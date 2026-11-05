package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * W-4 (B-26.3): sonda ativa de defasagem de replicacao ({@code S_lag}; plano de alinhamento de 2026-11-04,
 * secao 4.2; plano de execucao, secoes 4.2 e 9, W-4; documento de 05/11, secao 3.1, assinatura do P1).
 *
 * <p><b>Rodada.</b> Para cada no X, uma tarefa escreve {@code lag:<rotulo de X>} = {@code <nonce>:<rodada>} pelo
 * cliente de X; para cada outro no Y, uma tarefa le essa chave pelo cliente de Y ate ver o valor novo ou ate o
 * prazo de leitura ({@code --hotrod-timeout-ms}, o mesmo dos clientes; igual a {@code politica.tetoLagMs} pela R7
 * do W-2a). O leitor espera a escrita de X terminar, mas no
 * maximo um passo ({@code passoLeituraMs}), e entao le; enquanto o valor nao aparece, le de novo a cada passo.
 * Assim, sem falha, a rodada custa {@code n} escritas e {@code n(n-1)} leituras (3 e 6 com tres nos), e a
 * leitura ainda pode ver o valor antes de a escrita voltar (no SYNC o dono primario grava antes da copia
 * responder). O lag do par e o tempo do inicio da rodada ate o fim da leitura que devolveu o valor novo; se nao
 * viu antes do teto, e o teto. O lag do no e o menor lag dos pares em que ele aparece
 * ({@link RodadaDefasagem#lagPorNo()}).</p>
 *
 * <p><b>Agenda.</b> Periodo = W, alinhado a grade das janelas do nucleo ({@code origem + i*W}): a rodada
 * {@code i} comeca no inicio da janela {@code i} e termina ate o teto, que e menor que W
 * ({@link ParametrosDefasagem#validarContra}); assim cada janela recebe uma rodada. Uma rodada que comecaria
 * tarde demais para terminar na janela (atraso &gt; W - teto) ou cuja anterior ainda tem tarefa em curso e
 * <b>pulada</b> e contada: a janela fica sem amostra e o S_lag dela e {@code INDETERMINADA}, nunca um lag
 * inventado. Rodadas antes da origem (aquecimento) aquecem os clientes, mas nao entram em janela nem no JSONL.</p>
 *
 * <p><b>Janela</b> ({@link #resumirAte(long)}): consome as rodadas com inicio antes do fim da janela; lag de cada
 * no = o maior entre as rodadas; {@code lagMaxMs} = o maior lag de no; {@code noSuspeito} = o no que o atinge, se
 * unico; nivel pela histerese ({@code >= lagInMs} alta, {@code <= lagOutMs} calma, entre os dois intermediaria).
 * Sem rodada na janela, indeterminada (amostra insuficiente: o lag de no precisa de todos os pares de uma
 * rodada). Ver {@link #resumir}.</p>
 *
 * <p><b>P-5.</b> Escrita e leitura sao do {@link CanalCanario} (Hot Rod, em memoria). Os instantes do lag sao
 * lidos antes de qualquer gravacao. A linha de cada rodada vai ao ouvinte (JSONL) por uma thread gravadora
 * propria, com fila em memoria: um travamento de disco atrasa so a gravacao, nunca a rodada seguinte.</p>
 *
 * <p><b>Concorrencia.</b> Um executor de {@code n*n} threads daemon ({@code sonda-defasagem-tarefa-<i>}) roda as
 * {@code n} escritas e {@code n(n-1)} leituras de uma rodada ao mesmo tempo; no fim do prazo, as tarefas que
 * ainda nao terminaram sao interrompidas. A agenda roda numa thread daemon {@code sonda-defasagem}. Nunca ha duas
 * rodadas ao mesmo tempo.</p>
 */
public final class SondaDefasagem implements FonteDeDefasagem, AutoCloseable {

    /** Prefixo da chave de cada no no cache canario. */
    public static final String PREFIXO_CHAVE = "lag:";

    static final String NOME_AGENDA = "sonda-defasagem";
    static final String PREFIXO_TAREFA = "sonda-defasagem-tarefa-";
    static final String NOME_GRAVADOR = "sonda-defasagem-gravador";

    private static final long MS = 1_000_000L;

    private final CanalCanario canal;
    private final ParametrosDefasagem parametros;
    private final long tetoMs;
    private final long tetoNs;
    private final long passoNs;
    private final long periodoNs;
    private final long origemNs;
    private final FonteDeTempo tempo;
    private final Consumer<RodadaDefasagem> ouvinte;
    private final int nos;
    private final List<String> rotulos;
    private final List<String> chaves;
    private final String nonce;

    private final ExecutorService tarefas;
    private final ExecutorService gravador;
    /** Tarefas submetidas que ainda nao terminaram (inclui as interrompidas que nao cederam). */
    private final AtomicInteger emVoo = new AtomicInteger();
    /** Serializa as rodadas. */
    private final Object travaRodada = new Object();
    private long ultimaRodada;

    private final AtomicLong rodadas = new AtomicLong();
    private final AtomicLong antesDaOrigem = new AtomicLong();
    private final AtomicLong puladas = new AtomicLong();
    private final AtomicLong escritas = new AtomicLong();
    private final AtomicLong leituras = new AtomicLong();
    private final AtomicLong errosEscrita = new AtomicLong();
    private final AtomicLong errosLeitura = new AtomicLong();
    private final AtomicLong errosOuvinte = new AtomicLong();
    private final AtomicLong naoGravadas = new AtomicLong();

    /** Rodadas ainda nao consumidas; protegido por {@code this}. */
    private final List<RodadaDefasagem> pendentes = new ArrayList<>();
    private long ultimoFimNs = Long.MIN_VALUE;
    private boolean houveResumo;
    private long atrasadas;

    private final Object travaCiclo = new Object();
    private Thread agenda;
    private volatile boolean fechada;

    /**
     * @param canal      clientes fixados nos nos (Hot Rod ao vivo)
     * @param parametros {@code lagInMs}, {@code lagOutMs}, {@code passoLeituraMs}, sem padrao
     * @param tetoMs     prazo de leitura = {@code --hotrod-timeout-ms}; o lag de um par nao visto (igual a
     *                   {@code politica.tetoLagMs}, conferido pela R7 do W-2a)
     * @param periodoNs  janela W do nucleo (periodo da sonda, plano de alinhamento 4.2)
     * @param origemNs   inicio da primeira janela, no relogio de {@code tempo}
     * @param tempo      relogio dos {@code end_ns} (so carimba e alinha; o lag e medido em {@code System.nanoTime})
     * @param ouvinte    recebe cada rodada depois da origem (JSONL), numa thread gravadora propria
     */
    public SondaDefasagem(CanalCanario canal, ParametrosDefasagem parametros, long tetoMs, long periodoNs,
                          long origemNs, FonteDeTempo tempo, Consumer<RodadaDefasagem> ouvinte) {
        this(canal, parametros, tetoMs, periodoNs, origemNs, tempo, ouvinte,
                Long.toHexString(ThreadLocalRandom.current().nextLong()) + Long.toHexString(System.nanoTime()));
    }

    /** Com o {@code nonce} dado (testes). O nonce separa os valores desta execucao dos de uma execucao anterior. */
    SondaDefasagem(CanalCanario canal, ParametrosDefasagem parametros, long tetoMs, long periodoNs, long origemNs,
                   FonteDeTempo tempo, Consumer<RodadaDefasagem> ouvinte, String nonce) {
        this.canal = Objects.requireNonNull(canal, "canal");
        this.parametros = Objects.requireNonNull(parametros, "parametros");
        this.tempo = Objects.requireNonNull(tempo, "tempo");
        if (ouvinte == null) {
            throw new IllegalArgumentException("ouvinte nulo (use r -> {} para nenhum)");
        }
        this.ouvinte = ouvinte;
        if (nonce == null || nonce.isBlank() || nonce.contains(":")) {
            throw new IllegalArgumentException("nonce vazio ou com ':': " + nonce);
        }
        this.nonce = nonce;
        if (periodoNs <= 0) {
            throw new IllegalArgumentException("periodoNs deve ser > 0: " + periodoNs);
        }
        parametros.validarContra(tetoMs, periodoNs);
        this.tetoMs = tetoMs;
        this.tetoNs = tetoMs * MS;
        this.passoNs = parametros.passoLeituraMs() * MS;
        this.periodoNs = periodoNs;
        this.origemNs = origemNs;
        this.nos = canal.nos();
        if (nos < 2) {
            throw new IllegalArgumentException("a sonda de defasagem precisa de >= 2 nos: " + nos);
        }
        List<String> r = new ArrayList<>(nos);
        List<String> c = new ArrayList<>(nos);
        for (int i = 0; i < nos; i++) {
            String rot = canal.rotulo(i);
            if (rot == null || rot.isBlank()) {
                throw new IllegalArgumentException("rotulo vazio no no " + i);
            }
            r.add(rot);
            c.add(PREFIXO_CHAVE + rot);
        }
        if (new HashSet<>(r).size() != nos) {
            throw new IllegalArgumentException("rotulos repetidos: " + r);
        }
        this.rotulos = List.copyOf(r);
        this.chaves = List.copyOf(c);
        AtomicInteger seq = new AtomicInteger();
        this.tarefas = Executors.newFixedThreadPool(nos * nos, x -> {
            Thread t = new Thread(x, PREFIXO_TAREFA + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.gravador = Executors.newSingleThreadExecutor(x -> {
            Thread t = new Thread(x, NOME_GRAVADOR);
            t.setDaemon(true);
            return t;
        });
    }

    // ------------------------------------------------------------------ janela (puro)

    /**
     * Resume as rodadas de uma janela (funcao pura). Sem rodada: {@link EstadoDefasagem#indeterminada()}. Com
     * rodadas: lag de cada no = o maior entre as rodadas; {@code lagMaxMs} = o maior lag de no;
     * {@code noSuspeito} = o no que o atinge, se unico; nivel {@code ALTA} se {@code lagMaxMs >= lagInMs},
     * {@code CALMA} se {@code lagMaxMs <= lagOutMs}, senao {@code INTERMEDIARIA}; {@code amostras} = rodadas.
     */
    public static EstadoDefasagem resumir(List<RodadaDefasagem> rodadasDaJanela, ParametrosDefasagem p) {
        Objects.requireNonNull(p, "parametros");
        if (rodadasDaJanela == null || rodadasDaJanela.isEmpty()) {
            return EstadoDefasagem.indeterminada();
        }
        Map<String, Double> porNo = new LinkedHashMap<>();
        for (RodadaDefasagem r : rodadasDaJanela) {
            r.lagPorNo().forEach((no, lag) -> porNo.merge(no, lag, Double::max));
        }
        double max = RodadaDefasagem.maximo(porNo);
        EstadoDefasagem.Nivel nivel = max >= p.lagInMs() ? EstadoDefasagem.Nivel.ALTA
                : max <= p.lagOutMs() ? EstadoDefasagem.Nivel.CALMA : EstadoDefasagem.Nivel.INTERMEDIARIA;
        return new EstadoDefasagem(nivel, RodadaDefasagem.unicoMaximo(porNo), max, rodadasDaJanela.size());
    }

    /**
     * Consome as rodadas com {@code tInicioNs < fimNs} ainda nao consumidas, inclusive as que terminaram depois
     * do resumo da janela delas (contadas em {@link #rodadasAtrasadas()}), e as resume ({@link #resumir}).
     *
     * @throws IllegalArgumentException se {@code fimNs} recuar em relacao a chamada anterior
     */
    @Override
    public synchronized EstadoDefasagem resumirAte(long fimNs) {
        if (houveResumo && fimNs < ultimoFimNs) {
            throw new IllegalArgumentException("fimNs recuou: " + fimNs + " < " + ultimoFimNs);
        }
        List<RodadaDefasagem> consumidas = new ArrayList<>();
        for (Iterator<RodadaDefasagem> it = pendentes.iterator(); it.hasNext(); ) {
            RodadaDefasagem r = it.next();
            if (r.tInicioNs() >= fimNs) {
                continue;
            }
            it.remove();
            consumidas.add(r);
            if (houveResumo && r.tInicioNs() < ultimoFimNs) {
                atrasadas++;
            }
        }
        ultimoFimNs = fimNs;
        houveResumo = true;
        return resumir(consumidas, parametros);
    }

    // ------------------------------------------------------------------ rodada

    /**
     * Executa uma rodada agora, fora da agenda (testes e diagnostico), e a entrega como a agenda entregaria.
     *
     * @throws IllegalStateException sonda fechada, tarefa da rodada anterior ainda em curso, ou thread
     *                               interrompida durante a rodada (o sinal de interrupcao e mantido)
     */
    public RodadaDefasagem rodada() {
        synchronized (travaRodada) {
            if (fechada) {
                throw new IllegalStateException("sonda fechada");
            }
            if (emVoo.get() > 0) {
                throw new IllegalStateException("tarefa da rodada anterior ainda em curso");
            }
            RodadaDefasagem r = executarRodada();
            if (r == null) {
                throw new IllegalStateException("rodada interrompida");
            }
            entregar(r);
            return r;
        }
    }

    /** Uma tarefa submetida: o dono do "inicio" decrementa {@link #emVoo} exatamente uma vez. */
    private final class Tarefa {
        final AtomicBoolean reivindicada = new AtomicBoolean();
        volatile boolean cancelada;
        Future<?> futuro;

        void executar(Runnable corpo) {
            if (!reivindicada.compareAndSet(false, true)) {
                return; // cancelada antes de iniciar
            }
            try {
                corpo.run();
            } finally {
                emVoo.decrementAndGet();
            }
        }

        void cancelar() {
            cancelada = true;
            if (reivindicada.compareAndSet(false, true)) {
                emVoo.decrementAndGet(); // nunca vai rodar
            }
            if (futuro != null) {
                futuro.cancel(true);
            }
        }
    }

    /** Submete {@code corpo}; {@code null} se o executor recusou (sonda fechando). */
    private Tarefa submeter(Runnable corpo) {
        Tarefa t = new Tarefa();
        emVoo.incrementAndGet();
        try {
            t.futuro = tarefas.submit(() -> t.executar(corpo));
            return t;
        } catch (RejectedExecutionException e) {
            emVoo.decrementAndGet();
            return null;
        }
    }

    /** @return a rodada, ou {@code null} se interrompida (ou com a sonda fechando) */
    private RodadaDefasagem executarRodada() {
        long id = ++ultimaRodada;
        String valor = nonce + ":" + id;
        int n = nos;
        long tInicioNs = tempo.agoraNs();
        long t0 = System.nanoTime();
        long prazo = t0 + tetoNs;

        CountDownLatch[] escrito = new CountDownLatch[n];
        AtomicLongArray duracaoEscrita = new AtomicLongArray(n);
        AtomicReferenceArray<String> erroEscrita = new AtomicReferenceArray<>(n);
        AtomicLongArray vistoEm = new AtomicLongArray(n * n);
        AtomicIntegerArray tentativas = new AtomicIntegerArray(n * n);
        AtomicIntegerArray falhas = new AtomicIntegerArray(n * n);
        for (int i = 0; i < n; i++) {
            escrito[i] = new CountDownLatch(1);
            duracaoEscrita.set(i, -1);
        }
        for (int i = 0; i < n * n; i++) {
            vistoEm.set(i, -1);
        }

        List<Tarefa> escritores = new ArrayList<>(n);
        List<Tarefa> leitores = new ArrayList<>(n * (n - 1));
        boolean recusada = false;
        for (int x = 0; x < n && !recusada; x++) {
            int ix = x;
            Tarefa[] eu = new Tarefa[1];
            Tarefa t = submeter(() -> escrever(ix, valor, escrito[ix], duracaoEscrita, erroEscrita, eu));
            eu[0] = t;
            if (t == null) {
                recusada = true;
            } else {
                escritores.add(t);
            }
        }
        for (int x = 0; x < n && !recusada; x++) {
            for (int y = 0; y < n && !recusada; y++) {
                if (x == y) {
                    continue;
                }
                int ix = x;
                int iy = y;
                Tarefa t = submeter(() -> ler(ix, iy, valor, escrito[ix], prazo, vistoEm, tentativas, falhas));
                if (t == null) {
                    recusada = true;
                } else {
                    leitores.add(t);
                }
            }
        }
        if (recusada) {
            escritores.forEach(Tarefa::cancelar);
            leitores.forEach(Tarefa::cancelar);
            return null;
        }

        boolean interrompida = esperarAtePrazo(leitores, prazo) || esperarAtePrazo(escritores, prazo);
        for (Tarefa t : leitores) {
            if (!t.futuro.isDone()) {
                t.cancelar();
            }
        }
        for (Tarefa t : escritores) {
            if (!t.futuro.isDone()) {
                t.cancelar();
            }
        }
        if (interrompida) {
            Thread.currentThread().interrupt();
            return null;
        }

        List<RodadaDefasagem.Par> pares = new ArrayList<>(n * (n - 1));
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                if (x == y) {
                    continue;
                }
                int idx = x * n + y;
                long visto = vistoEm.get(idx);
                boolean viu = visto >= 0 && visto < prazo;
                double lagMs = viu ? (visto - t0) / (double) MS : (double) tetoMs;
                int tent = tentativas.get(idx);
                pares.add(new RodadaDefasagem.Par(rotulos.get(x), rotulos.get(y), viu, lagMs, tent,
                        Math.min(falhas.get(idx), tent)));
            }
        }
        List<RodadaDefasagem.Escrita> es = new ArrayList<>(n);
        for (int x = 0; x < n; x++) {
            long d = duracaoEscrita.get(x);
            if (escritores.get(x).cancelada || d < 0) {
                es.add(new RodadaDefasagem.Escrita(rotulos.get(x), null, null, null));
            } else {
                String erro = erroEscrita.get(x);
                es.add(new RodadaDefasagem.Escrita(rotulos.get(x), erro == null, d / (double) MS, erro));
            }
        }
        RodadaDefasagem r = new RodadaDefasagem(id, tInicioNs, tempo.agoraNs(), tetoMs, rotulos, pares, es);
        rodadas.incrementAndGet();
        escritas.addAndGet(n);
        leituras.addAndGet(r.leituras());
        errosEscrita.addAndGet(r.errosEscrita());
        errosLeitura.addAndGet(r.errosLeitura());
        return r;
    }

    /** Espera cada tarefa ate o prazo. @return {@code true} se a thread foi interrompida */
    private static boolean esperarAtePrazo(List<Tarefa> lista, long prazo) {
        for (Tarefa t : lista) {
            try {
                t.futuro.get(Math.max(0L, prazo - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException | CancellationException | ExecutionException e) {
                // a tarefa e cancelada abaixo; o resultado ja esta nos vetores
            } catch (InterruptedException e) {
                return true;
            }
        }
        return false;
    }

    private void escrever(int x, String valor, CountDownLatch escrito, AtomicLongArray duracao,
                          AtomicReferenceArray<String> erro, Tarefa[] eu) {
        long ini = System.nanoTime();
        String falha = null;
        boolean interrompida = false;
        try {
            canal.escrever(x, chaves.get(x), valor);
        } catch (InterruptedException e) {
            interrompida = true;
        } catch (Exception e) {
            falha = nomeDe(e);
        } finally {
            escrito.countDown();
        }
        Tarefa t = eu[0];
        if (interrompida || Thread.currentThread().isInterrupted() || (t != null && t.cancelada)) {
            return; // cancelada no fim do prazo: fica pendente
        }
        erro.set(x, falha);
        duracao.set(x, System.nanoTime() - ini);
    }

    private void ler(int x, int y, String valor, CountDownLatch escrito, long prazo, AtomicLongArray vistoEm,
                     AtomicIntegerArray tentativas, AtomicIntegerArray falhas) {
        int idx = x * nos + y;
        try {
            // espera a escrita voltar, mas no maximo um passo: a leitura pode ver o valor antes da resposta
            escrito.await(Math.min(passoNs, Math.max(0L, prazo - System.nanoTime())), TimeUnit.NANOSECONDS);
            while (System.nanoTime() < prazo) {
                String lido = null;
                boolean falhou = false;
                try {
                    lido = canal.ler(y, chaves.get(x));
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    falhou = true;
                }
                long fim = System.nanoTime();
                tentativas.incrementAndGet(idx);
                if (falhou) {
                    falhas.incrementAndGet(idx);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                } else if (valor.equals(lido)) {
                    if (fim < prazo) {
                        vistoEm.set(idx, fim);
                    }
                    return;
                }
                long resta = prazo - System.nanoTime();
                if (resta <= 0) {
                    return;
                }
                TimeUnit.NANOSECONDS.sleep(Math.min(passoNs, resta));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String nomeDe(Throwable t) {
        String s = t.getClass().getSimpleName();
        return s.isEmpty() ? t.getClass().getName() : s;
    }

    /** Rodada depois da origem: buffer e ouvinte (pela thread gravadora). Antes: so contada. */
    private void entregar(RodadaDefasagem r) {
        if (r.tInicioNs() < origemNs) {
            antesDaOrigem.incrementAndGet();
            return;
        }
        synchronized (this) {
            pendentes.add(r);
        }
        try {
            gravador.execute(() -> {
                try {
                    ouvinte.accept(r);
                } catch (RuntimeException e) {
                    errosOuvinte.incrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            naoGravadas.incrementAndGet();
        }
    }

    // ------------------------------------------------------------------ agenda

    /**
     * Agenda as rodadas na grade {@code origem + i*W} numa thread daemon {@code sonda-defasagem}, a partir da
     * janela corrente (inclusive antes da origem, para aquecer os clientes).
     *
     * @throws IllegalStateException se ja iniciada ou fechada
     */
    public void iniciar() {
        synchronized (travaCiclo) {
            if (fechada) {
                throw new IllegalStateException("sonda fechada");
            }
            if (agenda != null) {
                throw new IllegalStateException("sonda ja iniciada");
            }
            Thread t = new Thread(this::laco, NOME_AGENDA);
            t.setDaemon(true);
            agenda = t;
            t.start();
        }
    }

    /** Indice da janela que contem {@code agoraNs} na grade {@code origem + i*W} (pode ser negativo). */
    static long indiceDaJanela(long agoraNs, long origemNs, long periodoNs) {
        return Math.floorDiv(agoraNs - origemNs, periodoNs);
    }

    /**
     * A rodada da janela que comecou em {@code inicioJanelaNs} ainda pode comecar em {@code agoraNs}: o atraso
     * nao passa de {@code W - teto}, de modo que ela termina dentro da janela.
     */
    static boolean cabeNaJanela(long agoraNs, long inicioJanelaNs, long periodoNs, long tetoNs) {
        long atraso = agoraNs - inicioJanelaNs;
        return atraso >= 0 && atraso <= periodoNs - tetoNs;
    }

    private void laco() {
        long i = indiceDaJanela(tempo.agoraNs(), origemNs, periodoNs);
        try {
            while (!fechada && !Thread.currentThread().isInterrupted()) {
                long inicio = origemNs + i * periodoNs;
                long espera = inicio - tempo.agoraNs();
                if (espera > 0) {
                    TimeUnit.NANOSECONDS.sleep(espera);
                    continue;
                }
                if (!cabeNaJanela(tempo.agoraNs(), inicio, periodoNs, tetoNs) || emVoo.get() > 0) {
                    puladas.incrementAndGet();
                } else {
                    synchronized (travaRodada) {
                        if (fechada) {
                            return;
                        }
                        try {
                            RodadaDefasagem r = executarRodada();
                            if (r == null) {
                                return; // interrompida pelo close
                            }
                            entregar(r);
                        } catch (RuntimeException e) {
                            // a agenda nunca morre por uma rodada; a proxima tenta de novo
                        }
                    }
                }
                i++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Para a agenda, interrompe as tarefas em curso, deixa a thread gravadora terminar as linhas na fila e espera
     * as threads por ate {@code 2 * teto} (a mesma regra do {@code close} da {@link SondaTopologia}). Idempotente.
     * Depois dele {@link #resumirAte} continua respondendo com o buffer; {@link #iniciar()} e {@link #rodada()}
     * lancam {@link IllegalStateException}. Nao fecha o {@link CanalCanario} (o dono dele fecha).
     */
    @Override
    public void close() {
        Thread t;
        synchronized (travaCiclo) {
            if (fechada) {
                return;
            }
            fechada = true;
            t = agenda;
        }
        if (t != null) {
            t.interrupt();
        }
        tarefas.shutdownNow();
        gravador.shutdown();
        long limite = System.nanoTime() + 2 * tetoNs;
        boolean interrompido = false;
        try {
            if (t != null) {
                t.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(limite - System.nanoTime())));
            }
            tarefas.awaitTermination(Math.max(0L, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
            gravador.awaitTermination(Math.max(0L, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            interrompido = true;
        }
        if (interrompido) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ leitura

    /** Rotulos dos nos, na ordem do canal. */
    public List<String> rotulos() {
        return rotulos;
    }

    /** Chaves escritas, uma por no ({@value #PREFIXO_CHAVE} + rotulo). */
    public List<String> chaves() {
        return chaves;
    }

    /** O prazo de leitura (o lag de um par nao visto), em ms ({@code --hotrod-timeout-ms}). */
    public long tetoMs() {
        return tetoMs;
    }

    /** Rodadas concluidas (inclusive as de antes da origem). */
    public long rodadas() {
        return rodadas.get();
    }

    /** Rodadas concluidas antes da origem (aquecimento; fora das janelas e do JSONL). */
    public long rodadasAntesDaOrigem() {
        return antesDaOrigem.get();
    }

    /** Instantes da agenda pulados (atraso maior que W - teto, ou tarefa da rodada anterior ainda em curso). */
    public long rodadasPuladas() {
        return puladas.get();
    }

    /** Rodadas consumidas numa janela posterior a do seu inicio. */
    public synchronized long rodadasAtrasadas() {
        return atrasadas;
    }

    /** Escritas pedidas (custo; nunca vao ao CSV de eventos). */
    public long escritas() {
        return escritas.get();
    }

    /** Tentativas de leitura (custo; nunca vao ao CSV de eventos). */
    public long leituras() {
        return leituras.get();
    }

    /** Escritas que lancaram excecao. */
    public long errosEscrita() {
        return errosEscrita.get();
    }

    /** Tentativas de leitura que lancaram excecao. */
    public long errosLeitura() {
        return errosLeitura.get();
    }

    /** Excecoes lancadas pelo ouvinte (capturadas). */
    public long errosOuvinte() {
        return errosOuvinte.get();
    }

    /** Rodadas que nao chegaram ao ouvinte porque a sonda ja fechava. */
    public long rodadasNaoGravadas() {
        return naoGravadas.get();
    }

    /** Contadores para a linha {@code fim} de {@code observaveis}, em ordem fixa. */
    public Map<String, Long> contadores() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("defasagem_rodadas", rodadas());
        m.put("defasagem_rodadas_antes_da_origem", rodadasAntesDaOrigem());
        m.put("defasagem_rodadas_puladas", rodadasPuladas());
        m.put("defasagem_rodadas_atrasadas", rodadasAtrasadas());
        m.put("defasagem_rodadas_nao_gravadas", rodadasNaoGravadas());
        m.put("defasagem_escritas", escritas());
        m.put("defasagem_leituras", leituras());
        m.put("defasagem_erros_escrita", errosEscrita());
        m.put("defasagem_erros_leitura", errosLeitura());
        m.put("defasagem_erros_ouvinte", errosOuvinte());
        return Collections.unmodifiableMap(m);
    }

    /** Nomes dos contadores de {@link #contadores()} (para a linha {@code fim} sem sonda, com {@code null}). */
    public static List<String> nomesDosContadores() {
        return List.of("defasagem_rodadas", "defasagem_rodadas_antes_da_origem", "defasagem_rodadas_puladas",
                "defasagem_rodadas_atrasadas", "defasagem_rodadas_nao_gravadas", "defasagem_escritas",
                "defasagem_leituras", "defasagem_erros_escrita", "defasagem_erros_leitura", "defasagem_erros_ouvinte");
    }
}
