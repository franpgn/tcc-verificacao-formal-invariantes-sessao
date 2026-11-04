package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * W-3 (B-28.2): laco de controle do <i>cluster</i>, numa thread do processo da carga (plano de execucao
 * de 2026-11-04, secoes 4.1 e 4.5).
 *
 * <p>A cada <b>tick</b> ({@link #tick()}, de uma thread propria a cada {@code periodoNs}, ou chamado
 * direto nos testes): drena as violacoes pendentes para o {@link AgregadorDeJanela}, avanca o relogio do
 * agregador ate o agora da {@link FonteDeTempo} (decisao de 2026-10-22: janelas fecham mesmo sem
 * eventos) e processa, em ordem, toda janela fechada, inclusive as que os eventos das threads de carga
 * fecharam. Para cada janela: le S_top e S_lag ate o fim dela, chama
 * {@link Controlador#avaliar(Janela, EstadoTopologia, EstadoDefasagem)} (o tempo do nucleo e sempre o fim
 * da janela), grava a linha {@code janela} em {@code observaveis} e a linha {@code decisao} em
 * {@code decisoes}, e entrega a decisao ao {@link ExecutorDeAcoes}: na saida para NORMAL (R4), desfaz
 * tudo; nas demais janelas, aplica o {@link PlanoDeAcao} da {@link PoliticaDeAcao}.</p>
 *
 * <p><b>Origem.</b> Eventos e violacoes com instante anterior a origem do agregador (o inicio da
 * medicao; antes dele esta o <i>warm-up</i>) sao descartados e contados. Com isso uma violacao do
 * <i>warm-up</i> nao vira promovida da janela 0 (ponto aberto da V-B26.1, ressalvas, secao 6).</p>
 *
 * <p><b>Concorrencia.</b> {@link #registrarEvento} vem das threads de carga: so registra no agregador e
 * enfileira as janelas que fechou, sob uma trava que preserva a ordem; nunca avalia nem grava. Tudo o
 * mais roda numa thread por vez ({@code tick} e {@code encerrar} sao serializados).</p>
 */
public final class LacoDeControle implements AutoCloseable {

    public static final String NOME_THREAD = "controlador-laco";

    /**
     * Pecas do laco.
     *
     * @param meta        primeira linha dos dois JSONL
     * @param observaveis {@code observaveis-rep-NNN.jsonl}
     * @param decisoes    {@code decisoes-rep-NNN.jsonl} (tambem recebe as linhas de acao do executor)
     */
    public record Componentes(MetaExecucao meta, AgregadorDeJanela agregador, Controlador controlador,
                              FonteDeTopologia topologia, FonteDeDefasagem defasagem, FonteDeViolacoes violacoes,
                              PoliticaDeAcao politica, ExecutorDeAcoes executor, FonteDeTempo tempo,
                              TrilhaJsonl observaveis, TrilhaJsonl decisoes) {
        public Componentes {
            Objects.requireNonNull(meta, "meta");
            Objects.requireNonNull(agregador, "agregador");
            Objects.requireNonNull(controlador, "controlador");
            Objects.requireNonNull(topologia, "topologia");
            Objects.requireNonNull(defasagem, "defasagem");
            Objects.requireNonNull(violacoes, "violacoes");
            Objects.requireNonNull(politica, "politica");
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(tempo, "tempo");
            Objects.requireNonNull(observaveis, "observaveis");
            Objects.requireNonNull(decisoes, "decisoes");
            if (executor.modo() != meta.modo()) {
                throw new IllegalArgumentException("modo do executor (" + executor.modo() + ") difere do meta ("
                        + meta.modo() + ")");
            }
        }
    }

    /** Contadores do encerramento (tambem nas linhas {@code fim}). */
    public record Resumo(long janelas, long transicoes, long ticks, long errosTick, long errosPolitica,
                         long eventosAntesDaOrigem, long violacoesAntesDaOrigem, long eventosAposEncerramento,
                         long atrasadosTotal, long violacoesAtrasadasTotal, long promovidasTotal,
                         long descartadosNoEncerramento, long violacoesDescartadasNoEncerramento,
                         boolean executorNoPrazo, ExecutorDeAcoes.Contadores acoes) {
    }

    private final Componentes c;
    private final ModoAtuacao modo;
    private final long origemNs;

    private final Object travaAgregador = new Object();
    private final ConcurrentLinkedQueue<JanelaObservada> fechadas = new ConcurrentLinkedQueue<>();
    private final Object travaTick = new Object();
    private final Object travaEncerramento = new Object();

    private final AtomicLong eventosAntes = new AtomicLong();
    private final AtomicLong eventosApos = new AtomicLong();
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong errosTick = new AtomicLong();
    private long violacoesAntes;
    private long errosPolitica;
    private long janelas;
    private long transicoes;
    private volatile Decisao ultima;

    private volatile boolean parar;
    private Thread thread;
    private boolean encerrado;
    private Resumo resumo;

    /** Grava a linha de metadados nos dois JSONL e inicia o executor de acoes. */
    public LacoDeControle(Componentes componentes) {
        this.c = Objects.requireNonNull(componentes, "componentes");
        this.modo = c.meta().modo();
        this.origemNs = c.agregador().origemNs();
        c.observaveis().escrever(FormatoJsonl.meta(FormatoJsonl.ESQUEMA_OBSERVAVEIS, c.meta()));
        c.decisoes().escrever(FormatoJsonl.meta(FormatoJsonl.ESQUEMA_DECISOES, c.meta()));
        c.executor().iniciar();
    }

    /** Evento de operacao, chamado pelas threads de carga. Nunca lanca por causa do controlador. */
    public void registrarEvento(EventoOperacao e) {
        if (e.fimNs() < origemNs) {
            eventosAntes.incrementAndGet();
            return;
        }
        synchronized (travaAgregador) {
            try {
                fechadas.addAll(c.agregador().registrar(e));
            } catch (IllegalStateException fechado) {
                eventosApos.incrementAndGet();
            }
        }
    }

    /** Um tick: violacoes pendentes, relogio do agregador, janelas fechadas. Sem efeito depois de encerrar. */
    public void tick() {
        synchronized (travaTick) {
            if (encerrado) {
                return;
            }
            ticks.incrementAndGet();
            drenarViolacoes();
            long agora = c.tempo().agoraNs();
            long seq;
            List<JanelaObservada> l;
            synchronized (travaAgregador) {
                seq = c.agregador().seqRegistro();
                l = c.agregador().avancarRelogio(agora);
                fechadas.addAll(l);
            }
            if (!l.isEmpty()) {
                c.observaveis().escrever(FormatoJsonl.tick(seq, agora, l.size()));
            }
            processarFechadas();
        }
    }

    /** Inicia a thread daemon {@value #NOME_THREAD}, que chama {@link #tick()} a taxa fixa. */
    public void iniciar(long periodoNs) {
        if (periodoNs <= 0) {
            throw new IllegalArgumentException("periodo do tick deve ser > 0: " + periodoNs);
        }
        synchronized (travaEncerramento) {
            if (thread != null || encerrado) {
                throw new IllegalStateException("laco ja iniciado ou encerrado");
            }
            Thread t = new Thread(() -> laco(periodoNs), NOME_THREAD);
            t.setDaemon(true);
            thread = t;
            t.start();
        }
    }

    private void laco(long periodoNs) {
        long proximo = System.nanoTime() + periodoNs;
        while (!parar) {
            long espera = proximo - System.nanoTime();
            if (espera > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(espera);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            try {
                tick();
            } catch (RuntimeException e) {
                errosTick.incrementAndGet(); // o laco nunca morre por um tick
            }
            proximo += periodoNs;
            long agora = System.nanoTime();
            while (proximo <= agora) {
                proximo += periodoNs;
            }
        }
    }

    /** {@link #encerrar(Consumer)} sem campos extras. */
    public Resumo encerrar() {
        return encerrar(o -> { });
    }

    /**
     * Fim da carga: para a thread, processa as violacoes pendentes e as janelas completas
     * ({@link AgregadorDeJanela#encerrar()}), encerra o executor (desfaz tudo), grava as linhas {@code fim}
     * e fecha os arquivos. Idempotente.
     *
     * @param extrasObservaveis acrescenta campos a linha {@code fim} de {@code observaveis} (ex.: custo da sonda)
     */
    public Resumo encerrar(Consumer<ObjectNode> extrasObservaveis) {
        synchronized (travaEncerramento) {
            if (resumo != null) {
                return resumo;
            }
            parar = true;
            Thread t = thread;
            if (t != null) {
                t.interrupt();
                try {
                    t.join(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (travaTick) {
                drenarViolacoes();
                synchronized (travaAgregador) {
                    fechadas.addAll(c.agregador().encerrar());
                }
                processarFechadas();
                encerrado = true;
            }
            boolean noPrazo = c.executor().encerrar();
            AgregadorDeJanela a = c.agregador();
            Resumo r;
            synchronized (travaTick) {
                r = new Resumo(janelas, transicoes, ticks.get(), errosTick.get(), errosPolitica, eventosAntes.get(),
                        violacoesAntes, eventosApos.get(), a.atrasadosTotal(), a.violacoesAtrasadasTotal(),
                        a.violacoesSegurancaPromovidasTotal(), a.descartadosNoEncerramento(),
                        a.violacoesDescartadasNoEncerramento(), noPrazo, c.executor().contadores());
            }
            ObjectNode fo = FormatoJsonl.linha("fim");
            fo.put("janelas", r.janelas());
            fo.put("ticks", r.ticks());
            fo.put("erros_tick", r.errosTick());
            fo.put("eventos_antes_da_origem", r.eventosAntesDaOrigem());
            fo.put("violacoes_antes_da_origem", r.violacoesAntesDaOrigem());
            fo.put("eventos_apos_encerramento", r.eventosAposEncerramento());
            fo.put("atrasados_total", r.atrasadosTotal());
            fo.put("violacoes_atrasadas_total", r.violacoesAtrasadasTotal());
            fo.put("promovidas_total", r.promovidasTotal());
            fo.put("descartados_no_encerramento", r.descartadosNoEncerramento());
            fo.put("violacoes_descartadas_no_encerramento", r.violacoesDescartadasNoEncerramento());
            extrasObservaveis.accept(fo);
            c.observaveis().escrever(fo);
            ExecutorDeAcoes.Contadores k = r.acoes();
            ObjectNode fd = FormatoJsonl.linha("fim");
            fd.put("decisoes", r.janelas());
            fd.put("transicoes", r.transicoes());
            fd.put("perfil_final", ultima == null ? null : ultima.perfil().name());
            fd.put("erros_politica", r.errosPolitica());
            fd.put("acoes_pedidas", k.pedidas());
            fd.put("acoes_ignoradas", k.ignoradas());
            fd.put("acoes_executadas", k.executadas());
            fd.put("acoes_falhas", k.falhas());
            fd.put("reversoes", k.reversoes());
            fd.put("reversoes_falhas", k.reversoesFalhas());
            fd.put("acoes_descartadas", k.descartadas());
            fd.put("executor_no_prazo", r.executorNoPrazo());
            c.decisoes().escrever(fd);
            c.observaveis().close();
            c.decisoes().close();
            resumo = r;
            return r;
        }
    }

    @Override
    public void close() {
        encerrar();
    }

    private void drenarViolacoes() {
        for (ViolacaoObservada v : c.violacoes().drenar()) {
            if (v.fimNs() < origemNs) {
                violacoesAntes++;
                continue;
            }
            synchronized (travaAgregador) {
                fechadas.addAll(c.agregador().registrarViolacao(v.classe(), v.fimNs()));
            }
        }
    }

    private void processarFechadas() {
        JanelaObservada o;
        while ((o = fechadas.poll()) != null) {
            processar(o);
        }
    }

    private void processar(JanelaObservada o) {
        Janela j = o.janela();
        EstadoTopologia top = c.topologia().resumirAte(j.fimNs());
        EstadoDefasagem lag = c.defasagem().resumirAte(j.fimNs());
        Decisao d = c.controlador().avaliar(j, top, lag);
        janelas++;
        if (d.transicao()) {
            transicoes++;
        }
        ultima = d;
        c.observaveis().escrever(FormatoJsonl.janela(o, top, lag));
        c.decisoes().escrever(FormatoJsonl.decisao(d, modo));
        if (d.transicao() && d.regra() == Decisao.Regra.R4) {
            c.executor().reverterTudo(RegistroAcao.Origem.R4, d.regra(), d.fimNs());
            return;
        }
        PlanoDeAcao plano;
        try {
            plano = c.politica().planejar(d, top, lag, c.executor().ativas());
        } catch (RuntimeException e) {
            errosPolitica++;
            return;
        }
        for (AcaoCluster a : plano.reverter()) {
            c.executor().reverter(a, RegistroAcao.Origem.POLITICA, d.regra(), d.fimNs());
        }
        for (AcaoCluster a : plano.aplicar()) {
            c.executor().aplicar(a, d.regra(), d.fimNs());
        }
    }

    /** Ultima decisao processada, ou {@code null}. */
    public Decisao ultimaDecisao() {
        return ultima;
    }

    /** Decisoes processadas ate agora. */
    public long decisoes() {
        synchronized (travaTick) {
            return janelas;
        }
    }

    /** Ticks executados. */
    public long ticks() {
        return ticks.get();
    }
}
