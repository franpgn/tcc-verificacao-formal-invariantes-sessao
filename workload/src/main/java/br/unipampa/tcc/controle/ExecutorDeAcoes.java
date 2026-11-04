package br.unipampa.tcc.controle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * W-3 (B-28.2): executa as acoes pedidas pelo laco sobre um {@link AtuadorCluster} (plano de execucao
 * de 2026-11-04, secoes 4.1 e 4.4.3).
 *
 * <ul>
 *   <li><b>Fila com uma unica thread executora</b> ({@value #NOME_THREAD}, daemon): os pedidos
 *       ({@link #aplicar}, {@link #reverter}, {@link #reverterTudo}) so enfileiram e voltam na hora;
 *       uma chamada lenta ao cluster nunca bloqueia a carga nem o laco. Toda chamada leva o prazo
 *       {@code prazoChamadaNs}.</li>
 *   <li><b>Pilha de reversao LIFO, idempotente</b> (camada 1): cada aplicacao bem-sucedida (ou nao
 *       executada, com o atuador nulo) e empilhada; aplicar a mesma acao ({@link AcaoCluster#chave()})
 *       de novo e ignorado; reverter o que nao esta ativo nao faz nada. Aplicacao que falha e revertida
 *       na hora; reversao que falha fica na pilha e e tentada de novo no encerramento.</li>
 *   <li><b>Encerramento</b> ({@link #encerrar()}): descarta as aplicacoes ainda na fila, desfaz a pilha
 *       inteira e espera no maximo {@code prazoEncerramentoNs}.</li>
 *   <li><b>Gancho de desligamento da JVM</b> (camada 2): registrado no {@link #iniciar()} em
 *       {@code active}; se a JVM sair sem o encerramento normal (excecao, {@code System.exit}, sinal),
 *       ele desfaz a pilha com o mesmo prazo. O encerramento normal o remove.</li>
 *   <li><b>Shadow</b>: nenhuma thread, nenhum gancho, o atuador nunca e chamado. A pilha e virtual e
 *       cada pedido vira uma linha com {@code executada = false} e resultado {@code PREVISTA}, no mesmo
 *       instante e na mesma ordem em que active os executaria.</li>
 * </ul>
 *
 * <p>A camada 3 (o orquestrador confere e restaura a configuracao antes e depois de cada execucao) e do
 * W-6. O teto {@code T_cong} do AC-1 e da politica do W-2.</p>
 */
public final class ExecutorDeAcoes implements AutoCloseable {

    public static final String NOME_THREAD = "controlador-atuador";
    public static final String NOME_GANCHO = "controlador-gancho";

    static final String PREVISTA = "PREVISTA";
    static final String DESCARTADA = "DESCARTADA";

    /** Registro do gancho de desligamento (a JVM em producao; um duble nos testes). */
    public interface Gancho {
        void registrar(Thread t);

        void remover(Thread t);

        /** {@link Runtime#addShutdownHook}; remover durante o desligamento e ignorado. */
        static Gancho jvm() {
            return new Gancho() {
                @Override
                public void registrar(Thread t) {
                    Runtime.getRuntime().addShutdownHook(t);
                }

                @Override
                public void remover(Thread t) {
                    try {
                        Runtime.getRuntime().removeShutdownHook(t);
                    } catch (IllegalStateException e) {
                        // a JVM ja esta desligando
                    }
                }
            };
        }
    }

    /** Contadores para a linha de fim do JSONL. */
    public record Contadores(long pedidas, long ignoradas, long executadas, long falhas, long reversoes,
                             long reversoesFalhas, long descartadas) {
    }

    private enum TipoTarefa { APLICAR, REVERTER, REVERTER_TUDO, FIM }

    private record Tarefa(TipoTarefa tipo, AcaoCluster acao, RegistroAcao.Origem origem, Decisao.Regra regra,
                          Long fimJanelaNs, long pedidoNs) {
    }

    private record Chamada(ResultadoAtuacao resultado, long inicioNs, long duracaoNs) {
    }

    private final ModoAtuacao modo;
    private final AtuadorCluster atuador;
    private final long prazoChamadaNs;
    private final long prazoEncerramentoNs;
    private final FonteDeTempo tempo;
    private final Consumer<RegistroAcao> trilha;
    private final Gancho gancho;

    /** Acoes pedidas e nao revertidas (intencao), em ordem de aplicacao. Protegido por {@code trava}. */
    private final Map<String, AcaoCluster> ativas = new LinkedHashMap<>();
    private final Object trava = new Object();
    private long pedidas;
    private long ignoradas;
    private long executadas;
    private long falhas;
    private long reversoes;
    private long reversoesFalhas;
    private long descartadas;
    private boolean fechado;

    // --- so active
    private final LinkedBlockingQueue<Tarefa> fila = new LinkedBlockingQueue<>();
    /** Pilha das acoes aplicadas; so a thread executora mexe. Topo = primeiro. */
    private final Deque<AcaoCluster> pilha = new ArrayDeque<>();
    private final Object travaPendentes = new Object();
    private int pendentes;
    private volatile boolean encerrando;
    private Thread executora;
    private Thread threadGancho;

    private final Object travaEncerramento = new Object();
    private boolean encerrado;
    private boolean resultadoEncerramento;

    /**
     * @param prazoChamadaNs      prazo de cada chamada ao atuador (&gt; 0)
     * @param prazoEncerramentoNs espera maxima de {@link #encerrar()} e do gancho (&gt; 0)
     * @param tempo               relogio dos {@code end_ns} (instantes da trilha)
     * @param trilha              destino de cada {@link RegistroAcao} (thread do laco em shadow; thread
     *                            executora em active)
     */
    public ExecutorDeAcoes(ModoAtuacao modo, AtuadorCluster atuador, long prazoChamadaNs, long prazoEncerramentoNs,
                           FonteDeTempo tempo, Consumer<RegistroAcao> trilha, Gancho gancho) {
        this.modo = Objects.requireNonNull(modo, "modo");
        this.atuador = Objects.requireNonNull(atuador, "atuador");
        this.tempo = Objects.requireNonNull(tempo, "tempo");
        this.trilha = Objects.requireNonNull(trilha, "trilha");
        this.gancho = Objects.requireNonNull(gancho, "gancho");
        if (prazoChamadaNs <= 0 || prazoEncerramentoNs <= 0) {
            throw new IllegalArgumentException("prazos precisam ser > 0: chamada=" + prazoChamadaNs
                    + " encerramento=" + prazoEncerramentoNs);
        }
        this.prazoChamadaNs = prazoChamadaNs;
        this.prazoEncerramentoNs = prazoEncerramentoNs;
    }

    public ModoAtuacao modo() {
        return modo;
    }

    public String nomeAtuador() {
        return atuador.nome();
    }

    /** Em active, inicia a thread executora e registra o gancho de desligamento. Em shadow, nada. */
    public void iniciar() {
        if (modo == ModoAtuacao.SHADOW) {
            return;
        }
        synchronized (trava) {
            if (executora != null || fechado) {
                throw new IllegalStateException("executor ja iniciado ou fechado");
            }
            Thread t = new Thread(this::laco, NOME_THREAD);
            t.setDaemon(true);
            executora = t;
            threadGancho = new Thread(() -> encerrarCom(RegistroAcao.Origem.GANCHO_JVM, false), NOME_GANCHO);
        }
        executora.start();
        gancho.registrar(threadGancho);
    }

    /** Acoes ativas (pedidas e nao revertidas), em ordem de aplicacao. */
    public List<AcaoCluster> ativas() {
        synchronized (trava) {
            return List.copyOf(ativas.values());
        }
    }

    public Contadores contadores() {
        synchronized (trava) {
            return new Contadores(pedidas, ignoradas, executadas, falhas, reversoes, reversoesFalhas, descartadas);
        }
    }

    /** Pede a aplicacao (origem {@code POLITICA}); repetida enquanto ativa, e ignorada. */
    public void aplicar(AcaoCluster acao, Decisao.Regra regra, long fimJanelaNs) {
        Objects.requireNonNull(acao, "acao");
        synchronized (trava) {
            exigirAberto();
            if (ativas.containsKey(acao.chave())) {
                ignoradas++;
                return;
            }
            ativas.put(acao.chave(), acao);
            pedidas++;
        }
        long agora = tempo.agoraNs();
        if (modo == ModoAtuacao.SHADOW) {
            previsto(RegistroAcao.Operacao.APLICAR, acao, RegistroAcao.Origem.POLITICA, regra, fimJanelaNs, agora);
        } else {
            enfileirar(new Tarefa(TipoTarefa.APLICAR, acao, RegistroAcao.Origem.POLITICA, regra, fimJanelaNs, agora));
        }
    }

    /** Pede a reversao de uma acao ativa; inativa, nao faz nada. */
    public void reverter(AcaoCluster acao, RegistroAcao.Origem origem, Decisao.Regra regra, long fimJanelaNs) {
        Objects.requireNonNull(acao, "acao");
        Objects.requireNonNull(origem, "origem");
        synchronized (trava) {
            exigirAberto();
            if (ativas.remove(acao.chave()) == null) {
                return;
            }
        }
        long agora = tempo.agoraNs();
        if (modo == ModoAtuacao.SHADOW) {
            previsto(RegistroAcao.Operacao.REVERTER, acao, origem, regra, fimJanelaNs, agora);
        } else {
            enfileirar(new Tarefa(TipoTarefa.REVERTER, acao, origem, regra, fimJanelaNs, agora));
        }
    }

    /** Pede a reversao de tudo, do topo da pilha para a base. */
    public void reverterTudo(RegistroAcao.Origem origem, Decisao.Regra regra, Long fimJanelaNs) {
        Objects.requireNonNull(origem, "origem");
        List<AcaoCluster> lifo;
        synchronized (trava) {
            exigirAberto();
            lifo = new ArrayList<>(ativas.values());
            ativas.clear();
        }
        Collections.reverse(lifo);
        long agora = tempo.agoraNs();
        if (modo == ModoAtuacao.SHADOW) {
            for (AcaoCluster a : lifo) {
                previsto(RegistroAcao.Operacao.REVERTER, a, origem, regra, fimJanelaNs, agora);
            }
        } else {
            enfileirar(new Tarefa(TipoTarefa.REVERTER_TUDO, null, origem, regra, fimJanelaNs, agora));
        }
    }

    /**
     * Espera a fila esvaziar e a chamada em curso terminar (testes e encerramento do laco).
     *
     * @return {@code true} se esvaziou no prazo
     */
    public boolean aguardarFilaVazia(long prazoNs) throws InterruptedException {
        long limite = System.nanoTime() + prazoNs;
        synchronized (travaPendentes) {
            while (pendentes > 0) {
                long resta = limite - System.nanoTime();
                if (resta <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(travaPendentes, resta);
            }
            return true;
        }
    }

    /**
     * Encerramento normal: deixa de aceitar pedidos, descarta aplicacoes ainda na fila, desfaz a pilha
     * (origem {@code ENCERRAMENTO}), para a thread e remove o gancho. Idempotente.
     *
     * @return {@code true} se tudo terminou dentro de {@code prazoEncerramentoNs}
     */
    public boolean encerrar() {
        return encerrarCom(RegistroAcao.Origem.ENCERRAMENTO, true);
    }

    @Override
    public void close() {
        encerrar();
    }

    private boolean encerrarCom(RegistroAcao.Origem origem, boolean removerGancho) {
        synchronized (travaEncerramento) {
            if (encerrado) {
                return resultadoEncerramento;
            }
            encerrado = true;
            List<AcaoCluster> lifo;
            Thread t;
            synchronized (trava) {
                fechado = true;
                lifo = new ArrayList<>(ativas.values());
                ativas.clear();
                t = executora;
            }
            if (modo == ModoAtuacao.SHADOW || t == null) {
                Collections.reverse(lifo);
                long agora = tempo.agoraNs();
                for (AcaoCluster a : lifo) {
                    previsto(RegistroAcao.Operacao.REVERTER, a, origem, null, null, agora);
                }
                resultadoEncerramento = true;
                return true;
            }
            encerrando = true;
            long agora = tempo.agoraNs();
            enfileirar(new Tarefa(TipoTarefa.REVERTER_TUDO, null, origem, null, null, agora));
            enfileirar(new Tarefa(TipoTarefa.FIM, null, origem, null, null, agora));
            boolean ok;
            try {
                t.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(prazoEncerramentoNs)));
                ok = !t.isAlive();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ok = false;
            }
            if (removerGancho && threadGancho != null) {
                gancho.remover(threadGancho);
            }
            resultadoEncerramento = ok;
            return ok;
        }
    }

    private void exigirAberto() {
        if (fechado) {
            throw new IllegalStateException("executor de acoes encerrado");
        }
    }

    private void enfileirar(Tarefa t) {
        synchronized (travaPendentes) {
            pendentes++;
        }
        fila.add(t);
    }

    private void concluir() {
        synchronized (travaPendentes) {
            pendentes--;
            travaPendentes.notifyAll();
        }
    }

    private void previsto(RegistroAcao.Operacao op, AcaoCluster a, RegistroAcao.Origem origem, Decisao.Regra regra,
                          Long fimJanelaNs, long pedidoNs) {
        trilha.accept(new RegistroAcao(op, a, modo, atuador.nome(), false, PREVISTA, origem, regra, fimJanelaNs,
                pedidoNs, null, null, null, null, null));
    }

    // ------------------------------------------------------------ thread executora

    private void laco() {
        while (true) {
            Tarefa t;
            try {
                t = fila.take();
            } catch (InterruptedException e) {
                return;
            }
            try {
                processar(t);
            } catch (RuntimeException e) {
                // a thread executora nunca morre por uma tarefa
            } finally {
                concluir();
            }
            if (t.tipo() == TipoTarefa.FIM) {
                return;
            }
        }
    }

    private void processar(Tarefa t) {
        switch (t.tipo()) {
            case APLICAR -> processarAplicacao(t);
            case REVERTER -> {
                if (naPilha(t.acao())) {
                    desfazer(t.acao(), t.origem(), t.regra(), t.fimJanelaNs(), t.pedidoNs());
                }
            }
            case REVERTER_TUDO -> {
                for (AcaoCluster a : new ArrayList<>(pilha)) { // topo primeiro (LIFO)
                    desfazer(a, t.origem(), t.regra(), t.fimJanelaNs(), t.pedidoNs());
                }
            }
            case FIM -> { }
        }
    }

    private void processarAplicacao(Tarefa t) {
        AcaoCluster a = t.acao();
        if (encerrando) {
            synchronized (trava) {
                descartadas++;
                ativas.remove(a.chave());
            }
            trilha.accept(new RegistroAcao(RegistroAcao.Operacao.APLICAR, a, modo, atuador.nome(), false, DESCARTADA,
                    t.origem(), t.regra(), t.fimJanelaNs(), t.pedidoNs(), null, null, null, null, "encerramento"));
            return;
        }
        if (naPilha(a)) {
            return;
        }
        Chamada c = chamar(true, a);
        registrar(RegistroAcao.Operacao.APLICAR, a, t.origem(), t.regra(), t.fimJanelaNs(), t.pedidoNs(), c);
        ResultadoAtuacao r = c.resultado();
        if (r.estado() == ResultadoAtuacao.Estado.FALHOU) {
            synchronized (trava) {
                falhas++;
                ativas.remove(a.chave());
            }
            Chamada rv = chamar(false, a);
            registrar(RegistroAcao.Operacao.REVERTER, a, RegistroAcao.Origem.FALHA_NA_APLICACAO, t.regra(),
                    t.fimJanelaNs(), tempo.agoraNs(), rv);
            contarReversao(rv);
            return;
        }
        if (r.executada()) {
            synchronized (trava) {
                executadas++;
            }
        }
        pilha.push(a);
    }

    private void desfazer(AcaoCluster a, RegistroAcao.Origem origem, Decisao.Regra regra, Long fimJanelaNs,
                          long pedidoNs) {
        Chamada c = chamar(false, a);
        registrar(RegistroAcao.Operacao.REVERTER, a, origem, regra, fimJanelaNs, pedidoNs, c);
        contarReversao(c);
        if (c.resultado().estado() != ResultadoAtuacao.Estado.FALHOU) {
            for (Iterator<AcaoCluster> it = pilha.iterator(); it.hasNext(); ) {
                if (it.next().chave().equals(a.chave())) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private void contarReversao(Chamada c) {
        synchronized (trava) {
            if (c.resultado().estado() == ResultadoAtuacao.Estado.FALHOU) {
                reversoesFalhas++;
            } else {
                reversoes++;
            }
        }
    }

    private boolean naPilha(AcaoCluster a) {
        for (AcaoCluster x : pilha) {
            if (x.chave().equals(a.chave())) {
                return true;
            }
        }
        return false;
    }

    private Chamada chamar(boolean aplicar, AcaoCluster a) {
        long inicio = tempo.agoraNs();
        long t0 = System.nanoTime();
        ResultadoAtuacao r;
        try {
            r = aplicar ? atuador.aplicar(a, prazoChamadaNs) : atuador.reverter(a, prazoChamadaNs);
            if (r == null) {
                r = ResultadoAtuacao.falhou(null, null, "atuador devolveu null");
            }
        } catch (Exception | AssertionError e) {
            // inclusive InterruptedException: a thread executora nao repassa a interrupcao, para nao
            // abandonar a fila (o encerramento usa a tarefa FIM, nao interrupcao)
            String msg = e.getMessage();
            r = ResultadoAtuacao.falhou(null, null, e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg));
        }
        return new Chamada(r, inicio, System.nanoTime() - t0);
    }

    private void registrar(RegistroAcao.Operacao op, AcaoCluster a, RegistroAcao.Origem origem, Decisao.Regra regra,
                           Long fimJanelaNs, long pedidoNs, Chamada c) {
        ResultadoAtuacao r = c.resultado();
        trilha.accept(new RegistroAcao(op, a, modo, atuador.nome(), r.executada(), r.estado().name(), origem, regra,
                fimJanelaNs, pedidoNs, c.inicioNs(), c.duracaoNs(), r.codigoHttp(), r.lidoDeVolta(), r.erro()));
    }
}
