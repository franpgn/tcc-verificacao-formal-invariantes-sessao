package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * B-26.2: sonda de topologia ({@code S_top}, plano de 2026-10-22, secao 3.2), parte pura.
 *
 * <p>Cada <b>rodada</b> consulta os {@link FonteDeSaude#nos()} nos em paralelo, com prazo total
 * {@code prazoRodadaNs}; no sem resposta no prazo vira {@code timeout} e a sua tarefa e interrompida.
 * Cada no e classificado por {@link #classificar} e a rodada vira uma {@link AmostraTopologia}, carimbada
 * na {@link FonteDeTempo} quando TERMINA. A amostra vai ao ouvinte (JSONL, no B-28.2) e a um buffer,
 * consumido por {@link #resumirAte(long)} a cada janela fechada do nucleo.</p>
 *
 * <p>Contrato herdado da V-B28.1: falha de consulta e amostra <b>anomala</b> com
 * {@code EstadoTopologia.disponivel = true}; esta classe nunca devolve {@code disponivel = false}, que
 * fica com o chamador ("sem sonda configurada", {@link EstadoTopologia#indisponivel()}).</p>
 *
 * <p>Concorrencia: um executor proprio de {@code nos()} threads daemon ({@code sonda-topologia-no-<i>})
 * faz as consultas, de modo que nunca ha mais de {@code nos()} consultas em voo; a agenda roda numa
 * thread daemon {@code sonda-topologia} a taxa fixa e PULA (e conta) o instante em que a rodada anterior
 * ainda nao terminou, inclusive quando uma consulta interrompida ainda nao cedeu.</p>
 */
public final class SondaTopologia implements AutoCloseable {

    static final String SAUDAVEL = "HEALTHY";
    static final String NOME_AGENDA = "sonda-topologia";
    static final String PREFIXO_CONSULTA = "sonda-topologia-no-";

    private final FonteDeSaude fonte;
    private final List<String> caches;
    private final int nosEsperados;
    private final long periodoNs;
    private final long prazoRodadaNs;
    private final FonteDeTempo tempo;
    private final Consumer<AmostraTopologia> ouvinte;
    private final int nos;

    private final ExecutorService consultas;
    /** Consultas submetidas que ainda nao terminaram (inclui as canceladas que nao cederam). */
    private final AtomicInteger emVoo = new AtomicInteger();
    /** Serializa as rodadas: nunca ha duas ao mesmo tempo, nem chamando {@link #rodada()} direto. */
    private final Object travaRodada = new Object();

    private final AtomicLong rodadas = new AtomicLong();
    private final AtomicLong puladas = new AtomicLong();
    private final AtomicLong errosOuvinte = new AtomicLong();

    /** Buffer de amostras ainda nao consumidas; protegido por {@code this}. */
    private final List<AmostraTopologia> pendentes = new ArrayList<>();
    private long ultimoFimNs = Long.MIN_VALUE;
    private boolean houveResumo;
    private long atrasadas;

    private final Object travaCiclo = new Object();
    private Thread agenda;
    private volatile boolean fechada;

    public SondaTopologia(FonteDeSaude fonte, List<String> cachesDoPerfil, int nosEsperados, long periodoNs,
                          long prazoRodadaNs, FonteDeTempo tempo, Consumer<AmostraTopologia> ouvinte) {
        this.fonte = Objects.requireNonNull(fonte, "fonte");
        this.tempo = Objects.requireNonNull(tempo, "tempo");
        if (cachesDoPerfil == null || cachesDoPerfil.isEmpty()) {
            throw new IllegalArgumentException("lista de caches do perfil vazia");
        }
        for (String c : cachesDoPerfil) {
            if (c == null || c.isBlank()) {
                throw new IllegalArgumentException("nome de cache vazio em " + cachesDoPerfil);
            }
        }
        if (nosEsperados < 1) {
            throw new IllegalArgumentException("nosEsperados < 1: " + nosEsperados);
        }
        if (periodoNs <= 0 || prazoRodadaNs <= 0) {
            throw new IllegalArgumentException("periodo e prazo precisam ser > 0: periodo=" + periodoNs
                    + " prazo=" + prazoRodadaNs);
        }
        if (prazoRodadaNs >= periodoNs) {
            throw new IllegalArgumentException("prazo da rodada (" + prazoRodadaNs + " ns) precisa ser menor que o"
                    + " periodo (" + periodoNs + " ns)");
        }
        if (ouvinte == null) {
            throw new IllegalArgumentException("ouvinte nulo (use x -> {} para nenhum)");
        }
        this.nos = fonte.nos();
        if (nos < 1) {
            throw new IllegalArgumentException("fonte sem nos: " + nos);
        }
        this.caches = List.copyOf(cachesDoPerfil);
        this.nosEsperados = nosEsperados;
        this.periodoNs = periodoNs;
        this.prazoRodadaNs = prazoRodadaNs;
        this.ouvinte = ouvinte;
        AtomicInteger seq = new AtomicInteger();
        this.consultas = Executors.newFixedThreadPool(nos, r -> {
            Thread t = new Thread(r, PREFIXO_CONSULTA + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Classifica a leitura de um no (funcao pura). Anomalo se nao respondeu; ou {@code numeroDeNos <
     * nosEsperados}; ou algum cache DA LISTA ausente da resposta; ou algum cache DA LISTA com status
     * diferente de exatamente {@code HEALTHY}. Caches fora da lista sao ignorados. Com varios motivos
     * vale o primeiro na ordem timeout/falha &gt; nos &gt; cache-ausente &gt; cache, e dentro de cada
     * classe a ordem da lista.
     *
     * @param leituraOuNull {@code null} se o no nao respondeu
     * @param motivoFalha   {@code timeout} ou {@code falha:<Excecao>}; usado so quando a leitura e nula
     */
    public static ResultadoNo classificar(String no, LeituraSaude leituraOuNull, String motivoFalha,
                                          List<String> caches, int nosEsperados) {
        if (leituraOuNull == null) {
            String motivo = motivoFalha == null || motivoFalha.isEmpty() ? "falha:desconhecida" : motivoFalha;
            return new ResultadoNo(no, false, -1, motivo, true);
        }
        int n = leituraOuNull.numeroDeNos();
        if (n < nosEsperados) {
            return new ResultadoNo(no, true, n, "nos:" + n + "<" + nosEsperados, true);
        }
        Map<String, String> status = leituraOuNull.statusPorCache();
        for (String c : caches) {
            if (!status.containsKey(c)) {
                return new ResultadoNo(no, true, n, "cache-ausente:" + c, true);
            }
        }
        for (String c : caches) {
            String s = status.get(c);
            if (!SAUDAVEL.equals(s)) {
                return new ResultadoNo(no, true, n, "cache:" + c + "=" + s, true);
            }
        }
        return new ResultadoNo(no, true, n, "ok", false);
    }

    /**
     * Executa uma rodada: consulta os nos em paralelo, espera no maximo {@code prazoRodadaNs} no total,
     * classifica, carimba no fim e entrega ao buffer e ao ouvinte.
     *
     * @throws IllegalStateException se a sonda estiver fechada ou a thread for interrompida durante a
     *                               rodada (nesse caso nada e entregue e o sinal de interrupcao e mantido)
     */
    public AmostraTopologia rodada() {
        synchronized (travaRodada) {
            if (fechada) {
                throw new IllegalStateException("sonda fechada");
            }
            AmostraTopologia a = executarRodada();
            if (a == null) {
                throw new IllegalStateException("rodada interrompida");
            }
            return a;
        }
    }

    /** Uma consulta submetida: o dono do "inicio" decrementa {@link #emVoo} exatamente uma vez. */
    private final class Consulta {
        final AtomicBoolean reivindicada = new AtomicBoolean();
        Future<LeituraSaude> futuro;

        LeituraSaude executar(int i) throws Exception {
            if (!reivindicada.compareAndSet(false, true)) {
                throw new CancellationException("cancelada antes de iniciar");
            }
            try {
                return fonte.consultar(i, prazoRodadaNs);
            } finally {
                emVoo.decrementAndGet();
            }
        }

        void cancelar() {
            if (reivindicada.compareAndSet(false, true)) {
                emVoo.decrementAndGet(); // nunca vai rodar
            }
            futuro.cancel(true);
        }
    }

    /** @return a amostra, ou {@code null} se a thread foi interrompida */
    private AmostraTopologia executarRodada() {
        long limite = System.nanoTime() + prazoRodadaNs;
        List<Consulta> lista = new ArrayList<>(nos);
        String[] falhaDeSubmissao = new String[nos];
        for (int i = 0; i < nos; i++) {
            Consulta c = new Consulta();
            int indice = i;
            emVoo.incrementAndGet();
            try {
                c.futuro = consultas.submit(() -> c.executar(indice));
                lista.add(c);
            } catch (RejectedExecutionException e) {
                emVoo.decrementAndGet();
                falhaDeSubmissao[i] = "falha:" + nomeDe(e);
                lista.add(null);
            }
        }
        List<ResultadoNo> porNo = new ArrayList<>(nos);
        boolean interrompida = false;
        for (int i = 0; i < nos; i++) {
            String rotulo = fonte.rotulo(i);
            Consulta c = lista.get(i);
            if (c == null) {
                porNo.add(classificar(rotulo, null, falhaDeSubmissao[i], caches, nosEsperados));
                continue;
            }
            if (interrompida) {
                c.cancelar();
                continue;
            }
            LeituraSaude leitura = null;
            String motivo = null;
            try {
                leitura = c.futuro.get(Math.max(0L, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                c.cancelar();
                motivo = "timeout";
            } catch (ExecutionException e) {
                motivo = "falha:" + nomeDe(e.getCause() == null ? e : e.getCause());
            } catch (CancellationException e) {
                motivo = "timeout";
            } catch (InterruptedException e) {
                c.cancelar();
                interrompida = true;
                continue;
            }
            porNo.add(classificar(rotulo, leitura, motivo, caches, nosEsperados));
        }
        if (interrompida) {
            Thread.currentThread().interrupt();
            return null;
        }
        long tNs = tempo.agoraNs(); // a informacao so existe a partir do fim da rodada
        boolean anomala = false;
        int nosMinimo = Integer.MAX_VALUE;
        for (ResultadoNo r : porNo) {
            anomala |= r.anomalo();
            if (r.respondeu()) {
                nosMinimo = Math.min(nosMinimo, r.numeroDeNos());
            }
        }
        AmostraTopologia a = new AmostraTopologia(tNs, anomala, nosMinimo == Integer.MAX_VALUE ? 0 : nosMinimo,
                porNo);
        entregar(a);
        return a;
    }

    private static String nomeDe(Throwable t) {
        String s = t.getClass().getSimpleName();
        return s.isEmpty() ? t.getClass().getName() : s;
    }

    private void entregar(AmostraTopologia a) {
        synchronized (this) {
            pendentes.add(a);
        }
        rodadas.incrementAndGet();
        try {
            ouvinte.accept(a);
        } catch (RuntimeException e) {
            errosOuvinte.incrementAndGet();
        }
    }

    /**
     * Agenda {@link #rodada()} a taxa fixa de {@code periodoNs} numa thread daemon {@code sonda-topologia}.
     * O instante em que a rodada anterior ainda nao terminou (alguma consulta dela ainda em voo, ou a
     * propria rodada passou do instante) e pulado e contado em {@link #rodadasPuladas()}.
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

    private void laco() {
        long proximo = System.nanoTime();
        try {
            while (!fechada && !Thread.currentThread().isInterrupted()) {
                long espera = proximo - System.nanoTime();
                if (espera > 0) {
                    TimeUnit.NANOSECONDS.sleep(espera);
                    continue;
                }
                if (emVoo.get() > 0) {
                    puladas.incrementAndGet(); // rodada anterior ainda em curso
                } else {
                    synchronized (travaRodada) {
                        if (fechada) {
                            return;
                        }
                        try {
                            if (executarRodada() == null) {
                                return; // interrompida pelo close
                            }
                        } catch (RuntimeException e) {
                            // a agenda nunca morre por uma rodada; a proxima tenta de novo
                        }
                    }
                }
                proximo += periodoNs;
                long agora = System.nanoTime();
                while (proximo <= agora) { // a rodada passou do instante seguinte
                    proximo += periodoNs;
                    puladas.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Consome todas as amostras com {@code tNs < fimNs} ainda nao consumidas, inclusive as de janelas
     * ja resumidas que chegaram tarde (contadas tambem em {@link #amostrasAtrasadas()}). Amostra com
     * {@code tNs >= fimNs} fica para a proxima chamada.
     *
     * @throws IllegalArgumentException se {@code fimNs} recuar em relacao a chamada anterior
     */
    public synchronized EstadoTopologia resumirAte(long fimNs) {
        if (houveResumo && fimNs < ultimoFimNs) {
            throw new IllegalArgumentException("fimNs recuou: " + fimNs + " < " + ultimoFimNs);
        }
        int consumidas = 0;
        int anomalas = 0;
        int nosMinimo = Integer.MAX_VALUE;
        for (Iterator<AmostraTopologia> it = pendentes.iterator(); it.hasNext(); ) {
            AmostraTopologia a = it.next();
            if (a.tNs() >= fimNs) {
                continue;
            }
            it.remove();
            consumidas++;
            if (a.anomala()) {
                anomalas++;
            }
            nosMinimo = Math.min(nosMinimo, a.nosMinimo());
            if (houveResumo && a.tNs() < ultimoFimNs) {
                atrasadas++;
            }
        }
        ultimoFimNs = fimNs;
        houveResumo = true;
        return new EstadoTopologia(true, consumidas, anomalas, consumidas == 0 ? 0 : nosMinimo);
    }

    /**
     * Para a agenda, interrompe as rodadas e consultas em curso e aguarda o termino das threads por ate
     * {@code 2 * prazoRodadaNs}. Idempotente. Depois dele {@link #resumirAte} continua respondendo com o
     * buffer; {@link #iniciar()} e {@link #rodada()} lancam {@link IllegalStateException}.
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
        consultas.shutdownNow();
        long limite = System.nanoTime() + 2 * prazoRodadaNs;
        boolean interrompido = false;
        try {
            if (t != null) {
                long restanteMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(limite - System.nanoTime()));
                t.join(restanteMs);
            }
            consultas.awaitTermination(Math.max(0L, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            interrompido = true;
        }
        if (interrompido) {
            Thread.currentThread().interrupt();
        }
    }

    /** Rodadas concluidas e entregues. */
    public long rodadas() {
        return rodadas.get();
    }

    /** Instantes da agenda pulados porque a rodada anterior ainda estava em curso. */
    public long rodadasPuladas() {
        return puladas.get();
    }

    /** Amostras consumidas numa janela posterior a de seu {@code tNs} (chegaram depois do resumo). */
    public synchronized long amostrasAtrasadas() {
        return atrasadas;
    }

    /** Excecoes lancadas pelo ouvinte (capturadas). */
    public long errosOuvinte() {
        return errosOuvinte.get();
    }
}
