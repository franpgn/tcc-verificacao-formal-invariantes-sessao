package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AgregadorDeJanela;
import br.unipampa.tcc.controle.ArquivoReferencia;
import br.unipampa.tcc.controle.AtuadorCluster;
import br.unipampa.tcc.controle.AtuadorNulo;
import br.unipampa.tcc.controle.ClasseViolacao;
import br.unipampa.tcc.controle.Controlador;
import br.unipampa.tcc.controle.EventoOperacao;
import br.unipampa.tcc.controle.ExecutorDeAcoes;
import br.unipampa.tcc.controle.FonteDeDefasagem;
import br.unipampa.tcc.controle.FonteDeSaude;
import br.unipampa.tcc.controle.FonteDeTempo;
import br.unipampa.tcc.controle.FonteDeTopologia;
import br.unipampa.tcc.controle.FonteDeViolacoes;
import br.unipampa.tcc.controle.FormatoJsonl;
import br.unipampa.tcc.controle.LacoDeControle;
import br.unipampa.tcc.controle.MetaExecucao;
import br.unipampa.tcc.controle.ModoAtuacao;
import br.unipampa.tcc.controle.ParametrosControlador;
import br.unipampa.tcc.controle.PoliticaDeAcao;
import br.unipampa.tcc.controle.Referencia;
import br.unipampa.tcc.controle.SondaTopologia;
import br.unipampa.tcc.controle.TrilhaJsonl;
import br.unipampa.tcc.controle.ViolacaoObservada;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * W-3 (B-28.2): liga o laco de controle do <i>cluster</i> ({@link LacoDeControle}) ao programa de carga.
 * E a unica classe de {@code session} que usa os tipos do laco (R-C1, {@code ArquiteturaSemAcaoNoClienteTest}).
 *
 * <ul>
 *   <li>{@link #carregar(Cli)}: le e confere {@code --controller-config} e {@code --controller-reference},
 *       e normaliza {@code --controller-admin}. Erro de conteudo = {@link IllegalArgumentException} (exit 2).</li>
 *   <li>{@link #abrir}: monta o agregador (origem = inicio da medicao), o nucleo, a sonda de topologia
 *       (REST, {@code --controller-admin}, caches do perfil, nos esperados = itens de {@code --servers}),
 *       o executor de acoes (atuador nulo e politica {@code nenhuma} ate o W-2) e os dois JSONL em
 *       {@code --csv-dir}; inicia a sonda e a thread do laco.</li>
 *   <li>{@link #ouvinteDeEvento()} e {@link #ouvinteDeViolacao()}: os ganchos de {@link SessionOps} e
 *       {@link InvariantAuditor}. As violacoes sao enfileiradas com o instante em que foram vistas e so
 *       classificadas na thread do laco ({@link #fonteDeViolacoes()}), com o {@link RastroCliente}.</li>
 *   <li>{@link #close()}: no fim da carga, antes da convergencia; desfaz toda acao.</li>
 * </ul>
 */
final class LigacaoControlador implements AutoCloseable, WorkloadMain.ControleDaCarga {

    /** Versao do programa gravada nos metadados (a mesma do {@code @Command} de {@link Cli}). */
    static final String VERSAO_PROGRAMA = "session-workload 0.1.0-SNAPSHOT";

    /** Periodo e prazo da sonda de topologia: os do B-26.2 (log de saude do gate). */
    static final long PERIODO_SONDA_NS = 2_000_000_000L;
    static final long PRAZO_SONDA_NS = 1_500_000_000L;
    /** Prazo de cada chamada ao atuador: 2 s, o mesmo {@code curl -m} da sonda de saude (secao 4.4.2). */
    static final long PRAZO_CHAMADA_NS = 2_000_000_000L;
    /** Divisor de W no tick (V-B26.1 secao 5.9: 1 s com W = 5 s). */
    static final int DIVISOR_TICK = 5;

    /**
     * Regra do tick (contrato v1.1): {@code Δ = min(W/5, P_sonda/2)}. {@code W/5} e a decisao registrada
     * na V-B26.1 secao 5.9 (aprovada em 2026-10-22); {@code P_sonda/2} e a derivacao da V-W3 secao 4.2: o
     * tick nunca e o termo dominante do atraso de leitura de S_top. Com W = 5 s e P_sonda = 2 s, 1 s.
     */
    static long periodoTick(long wNs, long periodoSondaNs) {
        return Math.min(wNs / DIVISOR_TICK, periodoSondaNs / 2);
    }

    /**
     * Arquivos e opcoes do controlador, ja conferidos.
     *
     * @param referencia {@code null} sem {@code --controller-reference} (shadow): latencia indeterminada
     */
    record Configuracao(ModoAtuacao modo, Path arquivoParametros, ParametrosControlador parametros,
                        Path arquivoReferencia, ArquivoReferencia referencia, List<String> urlsAdmin,
                        int nosEsperados) {
    }

    /**
     * Pecas substituiveis (testes e W-2).
     *
     * @param saude {@code null} = sem sonda de topologia (topologia indisponivel, sinal neutro)
     */
    record Pecas(FonteDeSaude saude, AtuadorCluster atuador, PoliticaDeAcao politica, ExecutorDeAcoes.Gancho gancho,
                 long tickNs, long periodoSondaNs, long prazoSondaNs) {
        Pecas {
            Objects.requireNonNull(atuador, "atuador");
            Objects.requireNonNull(politica, "politica");
            Objects.requireNonNull(gancho, "gancho");
        }
    }

    private record Bruta(String invariante, String chave, long tNs) {
    }

    private final Configuracao cfg;
    private final LacoDeControle laco;
    private final SondaTopologia sonda;
    private final ConsultaSaudeRest rest;
    private final FonteDeTempo tempo;
    private final RastroCliente rastro;
    private final ConcurrentLinkedQueue<Bruta> violacoes = new ConcurrentLinkedQueue<>();
    private final AtomicLong eventosForaDoContrato = new AtomicLong();
    private volatile boolean fechada;
    private LacoDeControle.Resumo resumo;

    private LigacaoControlador(Configuracao cfg, LacoDeControle laco, SondaTopologia sonda, ConsultaSaudeRest rest,
                               FonteDeTempo tempo, RastroCliente rastro) {
        this.cfg = cfg;
        this.laco = laco;
        this.sonda = sonda;
        this.rest = rest;
        this.tempo = tempo;
        this.rastro = rastro;
    }

    // ------------------------------------------------------------------ configuracao

    static ModoAtuacao modoDe(ModoControlador m) {
        return switch (m) {
            case SHADOW -> ModoAtuacao.SHADOW;
            case ACTIVE -> ModoAtuacao.ACTIVE;
            case OFF -> throw new IllegalArgumentException("--controller off nao abre controlador");
        };
    }

    /**
     * Le os arquivos do controlador. Supoe {@link Cli#validar()} ja chamado.
     *
     * @throws IOException              falha de leitura
     * @throws IllegalArgumentException conteudo invalido ou incoerente com {@code --scenario}/{@code --replication}
     */
    static Configuracao carregar(Cli cli) throws IOException {
        ModoAtuacao modo = modoDe(cli.controlador);
        Path par = Path.of(cli.controladorConfig);
        ParametrosControlador p = ParametrosControlador.deJson(par);
        Path refPath = null;
        ArquivoReferencia ref = null;
        if (cli.controladorReferencia != null && !cli.controladorReferencia.isBlank()) {
            refPath = Path.of(cli.controladorReferencia);
            ref = ArquivoReferencia.ler(refPath);
            String cenario = cli.scenarioS3 ? "S3" : cli.scenario.name();
            if (!cenario.equals(ref.cenario())) {
                throw new IllegalArgumentException("referencia " + refPath + " e do cenario " + ref.cenario()
                        + ", mas --scenario e " + cenario);
            }
            if (!cli.replicacao.rotulo().equals(ref.perfil())) {
                throw new IllegalArgumentException("referencia " + refPath + " e do perfil " + ref.perfil()
                        + ", mas --replication e " + cli.replicacao.rotulo());
            }
        }
        List<String> urls = urlsAdmin(cli.controladorAdmin);
        int nos = ConsultaSaudeRest.urlsDe(cli.servidores).size();
        if (urls.size() != nos) {
            throw new IllegalArgumentException("--controller-admin tem " + urls.size() + " URLs e --servers tem " + nos);
        }
        return new Configuracao(modo, par, p, refPath, ref, urls, nos);
    }

    /** {@code http://h:p} ou {@code h:p}, separados por virgula; devolve sempre {@code http://h:p}. */
    static List<String> urlsAdmin(String valor) {
        List<String> semEsquema = new ArrayList<>();
        for (String item : valor.split(",", -1)) {
            String t = item.trim();
            if (t.startsWith("http://")) {
                t = t.substring("http://".length());
            } else if (t.contains("://")) {
                throw new IllegalArgumentException("--controller-admin aceita so http: '" + item.trim() + "'");
            }
            if (t.endsWith("/")) {
                t = t.substring(0, t.length() - 1);
            }
            semEsquema.add(t);
        }
        return ConsultaSaudeRest.urlsDe(String.join(",", semEsquema));
    }

    /** Linhas acrescentadas ao {@code --dry-run}: parametros, SHA-256 dos arquivos, URLs e pecas. */
    static String textoDryRun(Configuracao c) {
        ParametrosControlador p = c.parametros();
        String n = System.lineSeparator();
        return "dry-run, controller=" + c.modo().rotulo() + " nos_esperados=" + c.nosEsperados()
                + " admin=" + String.join(",", c.urlsAdmin()) + n
                + "dry-run, controller-config=" + c.arquivoParametros() + " sha256=" + p.sha256()
                + " versao=" + p.versao() + n
                + "dry-run, parametros wNs=" + p.wNs() + " nLat=" + p.nLat() + " nErr=" + p.nErr()
                + " thetaLatIn=" + p.thetaLatIn() + " thetaLatOut=" + p.thetaLatOut()
                + " thetaErrIn=" + p.thetaErrIn() + " thetaErrOut=" + p.thetaErrOut()
                + " k=" + p.k() + " m=" + p.m() + " cooldownNs=" + p.cooldownNs() + " estavelNs=" + p.estavelNs()
                + " holdViolNs=" + p.holdViolNs() + n
                + "dry-run, controller-reference="
                + (c.referencia() == null ? "nenhuma (latencia indeterminada)"
                        : c.arquivoReferencia() + " sha256=" + c.referencia().sha256()
                        + " operacoes=" + c.referencia().referencia().p99PorOperacao().keySet()) + n
                + "dry-run, atuador=" + new AtuadorNulo().nome() + " politica=" + PoliticaDeAcao.NENHUMA.nome()
                + " tick_ns=" + periodoTick(p.wNs(), PERIODO_SONDA_NS) + " sonda_periodo_ns=" + PERIODO_SONDA_NS
                + " prazo_chamada_ns=" + PRAZO_CHAMADA_NS + " prazo_encerramento=(A+1)x" + PRAZO_CHAMADA_NS;
    }

    /** Pecas de producao ate o W-2: sonda REST, atuador nulo, politica nenhuma, gancho da JVM. */
    static Pecas pecasPadrao(Configuracao c, Cli cli) {
        return new Pecas(new ConsultaSaudeRest(c.urlsAdmin(), cli.usuario, cli.senha), new AtuadorNulo(),
                PoliticaDeAcao.NENHUMA, ExecutorDeAcoes.Gancho.jvm(),
                periodoTick(c.parametros().wNs(), PERIODO_SONDA_NS),
                PERIODO_SONDA_NS, PRAZO_SONDA_NS);
    }

    // ------------------------------------------------------------------ abertura

    static LigacaoControlador abrir(Configuracao c, Cli cli, int rep, EpochClock relogio, long origemNs,
                                    RastroCliente rastro) throws IOException {
        return abrir(c, cli, rep, relogio, origemNs, rastro, pecasPadrao(c, cli));
    }

    /**
     * @param origemNs inicio da primeira janela, no relogio dos {@code end_ns} (inicio da medicao)
     * @param rastro   rastro de cliente para classificar I2, ou {@code null} (I2 fica fora do gatilho)
     */
    static LigacaoControlador abrir(Configuracao c, Cli cli, int rep, EpochClock relogio, long origemNs,
                                    RastroCliente rastro, Pecas pecas) throws IOException {
        FonteDeTempo tempo = () -> relogio.paraEpochNanos(System.nanoTime());
        Path dir = Path.of(cli.csvDir);
        TrilhaJsonl obs = TrilhaJsonl.abrir(dir.resolve(String.format("observaveis-rep-%03d.jsonl", rep)));
        TrilhaJsonl dec = TrilhaJsonl.abrir(dir.resolve(String.format("decisoes-rep-%03d.jsonl", rep)));
        SondaTopologia sonda = null;
        FonteDeTopologia topologia = FonteDeTopologia.INDISPONIVEL;
        if (pecas.saude() != null) {
            sonda = new SondaTopologia(pecas.saude(), cli.replicacao.caches(), c.nosEsperados(),
                    pecas.periodoSondaNs(), pecas.prazoSondaNs(), tempo,
                    a -> obs.escrever(FormatoJsonl.topologia(a)));
            topologia = sonda::resumirAte;
        }
        ExecutorDeAcoes executor = new ExecutorDeAcoes(c.modo(), pecas.atuador(), PRAZO_CHAMADA_NS,
                tempo, r -> dec.escrever(FormatoJsonl.acao(r)), pecas.gancho());
        Referencia ref = c.referencia() == null ? Referencia.vazia() : c.referencia().referencia();
        MetaExecucao meta = new MetaExecucao(VERSAO_PROGRAMA, rep, c.modo(), cli.scenarioS3 ? "S3" : cli.scenario.name(),
                cli.replicacao.rotulo(), c.nosEsperados(), c.parametros(),
                c.arquivoReferencia() == null ? null : c.arquivoReferencia().toString(),
                c.referencia() == null ? null : c.referencia().sha256(), AgregadorDeJanela.TOLERANCIA_PADRAO_NS,
                pecas.tickNs(), origemNs, pecas.atuador().nome(), pecas.politica().nome(), sonda != null, false);
        LigacaoControlador[] self = new LigacaoControlador[1];
        FonteDeViolacoes violacoes = () -> self[0].classificarPendentes();
        LacoDeControle laco = new LacoDeControle(new LacoDeControle.Componentes(meta,
                new AgregadorDeJanela(origemNs, c.parametros(), ref), new Controlador(c.parametros()),
                topologia, FonteDeDefasagem.INDETERMINADA, violacoes, pecas.politica(), executor, tempo, obs, dec));
        LigacaoControlador l = new LigacaoControlador(c, laco, sonda,
                pecas.saude() instanceof ConsultaSaudeRest r ? r : null, tempo, rastro);
        self[0] = l;
        if (sonda != null) {
            sonda.iniciar();
        }
        laco.iniciar(pecas.tickNs());
        return l;
    }

    // ------------------------------------------------------------------ ganchos

    /** Para {@link SessionOps#definirOuvinteDeEvento}; depois do {@link #close()}, ignora. */
    SessionOps.OuvinteDeEvento ouvinteDeEvento() {
        return (op, ini, fim, rc) -> {
            if (fechada) {
                return;
            }
            try {
                laco.registrarEvento(new EventoOperacao(op, ini, fim, rc));
            } catch (IllegalArgumentException e) {
                eventosForaDoContrato.incrementAndGet();
            }
        };
    }

    /** Para {@link InvariantAuditor#definirOuvinte}; carimba no relogio dos {@code end_ns}. */
    InvariantAuditor.OuvinteDeViolacao ouvinteDeViolacao() {
        return (inv, chave) -> {
            if (!fechada) {
                violacoes.add(new Bruta(inv, chave, tempo.agoraNs()));
            }
        };
    }

    /** A fonte que o laco drena a cada tick (exposta para os testes). */
    FonteDeViolacoes fonteDeViolacoes() {
        return this::classificarPendentes;
    }

    /**
     * Classifica as violacoes pendentes (DT-001/DT-002 por {@link ClasseViolacao#de}): I1, I4 e I2
     * {@code REAL} sao seguranca; a classe de I2 vem do {@link RastroCliente} no instante em que foi vista
     * ({@code null} sem rastro). Os rotulos {@code I2_JANELA_CASCATA}/{@code I2_CASCATA_INCOMPLETA} e
     * I3/I5/I6 ficam fora do gatilho.
     */
    private List<ViolacaoObservada> classificarPendentes() {
        List<ViolacaoObservada> l = new ArrayList<>();
        Bruta b;
        while ((b = violacoes.poll()) != null) {
            String classeI2 = "I2".equals(b.invariante()) && rastro != null
                    ? rastro.classificarViolacaoI2(b.chave(), b.tNs()) : null;
            l.add(new ViolacaoObservada(ClasseViolacao.de(b.invariante(), classeI2), b.tNs()));
        }
        return l;
    }

    // ------------------------------------------------------------------ encerramento

    /** Fim da carga: para a sonda, encerra o laco (desfaz toda acao) e fecha os JSONL. Idempotente. */
    @Override
    public synchronized void close() {
        if (resumo != null) {
            return;
        }
        fechada = true;
        if (sonda != null) {
            sonda.close();
        }
        resumo = laco.encerrar(o -> {
            o.put("eventos_fora_do_contrato", eventosForaDoContrato.get());
            o.put("sonda_rodadas", sonda == null ? null : sonda.rodadas());
            o.put("sonda_rodadas_puladas", sonda == null ? null : sonda.rodadasPuladas());
            o.put("sonda_amostras_atrasadas", sonda == null ? null : sonda.amostrasAtrasadas());
            o.put("sonda_erros_ouvinte", sonda == null ? null : sonda.errosOuvinte());
            o.put("curl_lancados", rest == null ? null : rest.lancamentos());
        });
    }

    /** Linha de stdout depois do {@link #close()}. */
    @Override
    public synchronized String linhaResumo() {
        LacoDeControle.Resumo r = resumo;
        if (r == null) {
            return "Controlador: modo=" + cfg.modo().rotulo() + " (aberto)";
        }
        return "Controlador: modo=" + cfg.modo().rotulo() + " janelas=" + r.janelas() + " transicoes="
                + r.transicoes() + " acoes_pedidas=" + r.acoes().pedidas() + " acoes_executadas="
                + r.acoes().executadas() + " reversoes=" + r.acoes().reversoes() + " reversoes_falhas="
                + r.acoes().reversoesFalhas() + " executor_no_prazo=" + r.executorNoPrazo()
                + " eventos_antes_da_origem=" + r.eventosAntesDaOrigem()
                + " linhas_jsonl_perdidas=" + laco.linhasPerdidas();
    }
}
