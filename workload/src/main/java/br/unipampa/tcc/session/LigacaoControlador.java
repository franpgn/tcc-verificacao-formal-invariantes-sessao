package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.AgregadorDeJanela;
import br.unipampa.tcc.controle.ArquivoReferencia;
import br.unipampa.tcc.controle.AtuadorCluster;
import br.unipampa.tcc.controle.AtuadorNulo;
import br.unipampa.tcc.controle.AtuadorPorTipo;
import br.unipampa.tcc.controle.CanalCanario;
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
import br.unipampa.tcc.controle.ParametrosDefasagem;
import br.unipampa.tcc.controle.ParametrosPolitica;
import br.unipampa.tcc.controle.PoliticaDeAcao;
import br.unipampa.tcc.controle.PoliticaPorAssinatura;
import br.unipampa.tcc.controle.Referencia;
import br.unipampa.tcc.controle.SondaDefasagem;
import br.unipampa.tcc.controle.SondaTopologia;
import br.unipampa.tcc.controle.TrilhaJsonl;
import br.unipampa.tcc.controle.ViolacaoObservada;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * W-3 (B-28.2): liga o laco de controle do <i>cluster</i> ({@link LacoDeControle}) ao programa de carga.
 * E a unica classe de {@code session} que usa os tipos do laco (R-C1, {@code ArquiteturaSemAcaoNoClienteTest}).
 *
 * <ul>
 *   <li>{@link #carregar(Cli)}: le e confere {@code --controller-config} e {@code --controller-reference},
 *       e normaliza {@code --controller-admin}. Erro de conteudo = {@link IllegalArgumentException} (exit 2).</li>
 *   <li>{@link #abrir}: monta o agregador (origem = inicio da medicao), o nucleo, a sonda de topologia
 *       (REST, {@code --controller-admin}, caches do perfil, nos esperados = itens de {@code --servers}),
 *       o executor de acoes e os dois JSONL em
 *       {@code --csv-dir}; inicia a sonda e a thread do laco.</li>
 *   <li>{@link #ouvinteDeEvento()} e {@link #ouvinteDeViolacao()}: os ganchos de {@link SessionOps} e
 *       {@link InvariantAuditor}. As violacoes sao enfileiradas com o instante em que foram vistas e so
 *       classificadas na thread do laco ({@link #fonteDeViolacoes()}), com o {@link RastroCliente}.</li>
 *   <li>{@link #close()}: no fim da carga, antes da convergencia; desfaz toda acao.</li>
 * </ul>
 *
 * <p><b>W-2a (P-38).</b> Sem o bloco {@code "politica"} no {@code controlador-*.json}: atuador nulo e politica
 * {@code nenhuma}, como no W-3. Com o bloco: {@link PoliticaPorAssinatura} com esses parametros e os caches do
 * modo, e {@link AtuadorPorTipo#bracoB} sobre o {@link AtuadorRestInfinispan} (Digest, {@code --controller-admin});
 * os "nos que responderam na ultima janela" do atuador sao os da ultima amostra da sonda de topologia. Em shadow o
 * atuador nunca e chamado.</p>
 *
 * <p><b>W-4 (B-26.3).</b> Com o bloco {@code "defasagem"} (so em S3): {@link SondaDefasagem} sobre o
 * {@link CanalCanarioHotRod} (um cliente Hot Rod fixado por no, no cache canario do perfil), prazo de leitura =
 * {@code --hotrod-timeout-ms} (o "nao viu", igual a {@code politica.tetoLagMs} pela R7), periodo = W, alinhada a
 * origem das janelas; o laco le o S_lag
 * dela, cada rodada vai a uma linha {@code defasagem} de {@code observaveis} e o custo vai aos contadores
 * {@code defasagem_*} do {@code fim}. Sem o bloco: S_lag indeterminado, como no W-3.</p>
 *
 * <p><b>P1-IC (06/11, G-1 e G-4 do coordenador).</b> Com o bloco {@code politica}, o braco B ganha o adaptador do
 * AC-7 ({@link AtuadorIgnorarCache}, ignorar o cache de sessoes no suspeito), que passa a ser a acao do P1 (salvo
 * {@code acaoP1 = AC-5} no bloco, para comparacao). O sensor desconta os erros vindos do no sob o AC-7
 * ({@link DescontoSobAcao}, so em active): o rotulo do alvo e o da REST e o da carga e o do Hot Rod, ligados pela
 * posicao em {@code --controller-admin} e {@code --servers} ({@link #servidorPorAdmin}).</p>
 *
 * <p><b>W-2b (P-5).</b> Os dois JSONL gravam por uma thread propria ({@link TrilhaJsonl}), com fila de
 * {@link TrilhaJsonl#capacidadePara 4 x} as linhas esperadas na execucao de {@code --duration}
 * ({@link #linhasEsperadasObservaveis}, {@link #linhasEsperadasDecisoes}). O gancho de desligamento da JVM do
 * executor vai envolto ({@link #ganchoComDreno}): depois de desfazer a pilha, espera a gravadora do
 * {@code decisoes} no que sobra do {@code T_enc}.</p>
 */
final class LigacaoControlador implements AutoCloseable, WorkloadMain.ControleDaCarga {

    /** Versao do programa gravada nos metadados (a mesma do {@code @Command} de {@link Cli}). */
    static final String VERSAO_PROGRAMA = "session-workload 0.1.0-SNAPSHOT";

    /** Periodo e prazo da sonda de topologia: os do B-26.2 (log de saude do gate). */
    static final long PERIODO_SONDA_NS = 2_000_000_000L;
    static final long PRAZO_SONDA_NS = 1_500_000_000L;
    /**
     * Prazo de cada chamada ao atuador: 2 s, o mesmo {@code curl -m} da sonda de saude do B-26.2 (secao 4.4.2). Desde o
     * W-2b a sonda e HTTP em memoria; a origem do valor continua a mesma.
     */
    static final long PRAZO_CHAMADA_NS = 2_000_000_000L;
    /** Intervalo entre as consultas de saude do alvo do AC-5 (W-2a). */
    static final long PASSO_SAUDE_AC5_NS = 100_000_000L;
    /**
     * P1-IC: periodo da guarda continua do AC-7 (1 s: no smoke curto-r1 a lista da maioria passou a ter o cache 7 a 9 s
     * depois da cura e o cluster ficou sem o cache de sessoes ate o desfazer), carencia de 2 s (a guarda do adaptador
     * olha o primeiro segundo; o dobro cobre a chamada inteira de aplicar) e prazo de cada GET de 500 ms.
     */
    static final long PERIODO_GUARDA_NS = 1_000_000_000L;
    static final long CARENCIA_GUARDA_NS = 2_000_000_000L;
    static final long PRAZO_LEITURA_GUARDA_NS = 500_000_000L;
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
     * W-2b: linhas esperadas no {@code observaveis} numa execucao de {@code duracaoNs} ({@code --duration}, com o
     * <i>warm-up</i>, o que so superestima): {@code meta} e {@code fim}; por janela W uma {@code janela} e no maximo
     * um {@code tick} (so sai o que fecha janela); uma {@code topologia} por rodada da sonda ({@code P_sonda}), com
     * ela; uma {@code defasagem} por W, com a sonda S_lag (contrato 1.2). Arredonda para cima.
     */
    static long linhasEsperadasObservaveis(long duracaoNs, long wNs, long periodoSondaNs, boolean sondaTopologia,
                                           boolean sondaDefasagem) {
        long janelas = teto(duracaoNs, wNs);
        return 2 + 2 * janelas + (sondaTopologia ? teto(duracaoNs, periodoSondaNs) : 0)
                + (sondaDefasagem ? janelas : 0);
    }

    /**
     * W-2b: linhas esperadas no {@code decisoes}: {@code meta} e {@code fim}; por janela W uma {@code decisao}, no
     * maximo uma {@code politica} (so sai com plano da politica, contrato 1.2) e, em media, no maximo uma
     * {@code acao} (o braco B tem uma unica chave de AC-2 e cota de 1 AC-5; aplicar e reverter o AC-2 pedem
     * janelas diferentes). O plano do W-2b fala em "D/W decisoes + acoes"; a linha {@code politica} entrou no
     * W-2a e vai somada aqui. Arredonda para cima.
     */
    static long linhasEsperadasDecisoes(long duracaoNs, long wNs) {
        return 2 + 3 * teto(duracaoNs, wNs);
    }

    private static long teto(long a, long b) {
        if (a <= 0 || b <= 0) {
            throw new IllegalArgumentException("duracao e periodo precisam ser > 0: " + a + ", " + b);
        }
        return (a + b - 1) / b;
    }

    /**
     * W-2b: envolve o gancho de desligamento do {@link ExecutorDeAcoes}. A thread registrada em {@code base} tem o
     * mesmo nome; roda o gancho do executor (que desfaz a pilha e poe as linhas {@code acao} na fila da trilha) e
     * depois espera a gravadora ({@code aguardarGravacao}) pelo que sobra de {@code prazoNs} (o {@code T_enc} usado
     * pelo executor). Sem sobra, nao espera: o gancho nunca passa do {@code T_enc}, como antes do W-2b, quando as
     * linhas eram gravadas na propria thread do executor.
     */
    static ExecutorDeAcoes.Gancho ganchoComDreno(ExecutorDeAcoes.Gancho base, LongSupplier prazoNs,
                                                 LongPredicate aguardarGravacao) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(prazoNs, "prazoNs");
        Objects.requireNonNull(aguardarGravacao, "aguardarGravacao");
        Map<Thread, Thread> envoltos = new ConcurrentHashMap<>();
        return new ExecutorDeAcoes.Gancho() {
            @Override
            public void registrar(Thread t) {
                Thread w = new Thread(() -> {
                    long t0 = System.nanoTime();
                    t.run();
                    long resta = prazoNs.getAsLong() - (System.nanoTime() - t0);
                    if (resta > 0) {
                        aguardarGravacao.test(resta);
                    }
                }, t.getName());
                envoltos.put(t, w);
                base.registrar(w);
            }

            @Override
            public void remover(Thread t) {
                Thread w = envoltos.remove(t);
                if (w != null) {
                    base.remover(w);
                }
            }
        };
    }

    /**
     * Arquivos e opcoes do controlador, ja conferidos.
     *
     * @param referencia    {@code null} sem {@code --controller-reference} (shadow): latencia indeterminada
     * @param prazoHotRodMs W-4: {@code --hotrod-timeout-ms}, o prazo de leitura da sonda de defasagem (o "nao viu")
     */
    record Configuracao(ModoAtuacao modo, Path arquivoParametros, ParametrosControlador parametros,
                        Path arquivoReferencia, ArquivoReferencia referencia, List<String> urlsAdmin,
                        int nosEsperados, long prazoHotRodMs, String servidores) {
    }

    /**
     * Pecas substituiveis (testes e W-2).
     *
     * @param saude       {@code null} = sem sonda de topologia (topologia indisponivel, sinal neutro)
     * @param respondidos W-2a: onde {@link #abrir} pendura a ultima amostra da sonda para o atuador REST, ou
     *                    {@code null} (atuador que nao precisa dela)
     * @param canario     W-4: canal da sonda de defasagem, ou {@code null} sem o bloco {@code defasagem} (S_lag
     *                    indeterminado). A ligacao fecha o canal no {@link #close()}
     */
    record Pecas(FonteDeSaude saude, AtuadorCluster atuador, PoliticaDeAcao politica, ExecutorDeAcoes.Gancho gancho,
                 long tickNs, long periodoSondaNs, long prazoSondaNs,
                 AtomicReference<Supplier<Set<String>>> respondidos, CanalCanario canario) {
        Pecas {
            Objects.requireNonNull(atuador, "atuador");
            Objects.requireNonNull(politica, "politica");
            Objects.requireNonNull(gancho, "gancho");
        }

        /** Pecas do W-3 (sem fornecedor da ultima amostra, sem sonda de defasagem). */
        Pecas(FonteDeSaude saude, AtuadorCluster atuador, PoliticaDeAcao politica, ExecutorDeAcoes.Gancho gancho,
              long tickNs, long periodoSondaNs, long prazoSondaNs) {
            this(saude, atuador, politica, gancho, tickNs, periodoSondaNs, prazoSondaNs, null, null);
        }

        /** Pecas do W-2a (sem sonda de defasagem). */
        Pecas(FonteDeSaude saude, AtuadorCluster atuador, PoliticaDeAcao politica, ExecutorDeAcoes.Gancho gancho,
              long tickNs, long periodoSondaNs, long prazoSondaNs,
              AtomicReference<Supplier<Set<String>>> respondidos) {
            this(saude, atuador, politica, gancho, tickNs, periodoSondaNs, prazoSondaNs, respondidos, null);
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
    private final SondaDefasagem defasagem;
    private final CanalCanario canario;
    private final TrilhaJsonl trilhaObs;
    private final TrilhaJsonl trilhaDec;
    /** W-6 (P-6 da V-W5): o executor guarda o alvo do AC-5 APLICADA (linha de fim da carga). */
    private final ExecutorDeAcoes executor;
    private final ConcurrentLinkedQueue<Bruta> violacoes = new ConcurrentLinkedQueue<>();
    private final AtomicLong eventosForaDoContrato = new AtomicLong();
    /** P1-IC (G-4): erros do no sob o AC-7 fora do sensor (so em active). */
    private final DescontoSobAcao desconto;
    /** P1-IC: guarda continua do AC-7 (so em active com o bloco politica); {@code null} sem ela. */
    private GuardaContinuaIgnorados guarda;
    private volatile boolean fechada;
    private LacoDeControle.Resumo resumo;

    private LigacaoControlador(Configuracao cfg, LacoDeControle laco, SondaTopologia sonda, ConsultaSaudeRest rest,
                               FonteDeTempo tempo, RastroCliente rastro, SondaDefasagem defasagem,
                               CanalCanario canario, TrilhaJsonl trilhaObs, TrilhaJsonl trilhaDec,
                               ExecutorDeAcoes executor) {
        this.cfg = cfg;
        this.trilhaObs = trilhaObs;
        this.trilhaDec = trilhaDec;
        this.laco = laco;
        this.sonda = sonda;
        this.rest = rest;
        this.tempo = tempo;
        this.rastro = rastro;
        this.defasagem = defasagem;
        this.canario = canario;
        this.executor = executor;
        this.desconto = new DescontoSobAcao(cfg.modo(), servidorPorAdmin(cfg.urlsAdmin(), servidoresDe(cfg)),
                executor::ativas);
    }

    /** {@code --servers} guardado na configuracao (os nos esperados vem dele). */
    private static String servidoresDe(Configuracao c) {
        return c.servidores();
    }

    /**
     * P1-IC (G-4): rotulo do Hot Rod ({@code --servers}) de cada rotulo da REST ({@code --controller-admin}), pela
     * posicao. O {@code carregar} ja exige o mesmo numero de itens nas duas opcoes.
     *
     * @throws IllegalArgumentException numeros de itens diferentes
     */
    static Map<String, String> servidorPorAdmin(List<String> urlsAdmin, String servidores) {
        String[] s = servidores.split(",");
        if (s.length != urlsAdmin.size()) {
            throw new IllegalArgumentException("--controller-admin tem " + urlsAdmin.size() + " URLs e --servers tem "
                    + s.length);
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < s.length; i++) {
            m.put(AtuadorRestInfinispan.rotulo(urlsAdmin.get(i)), s[i].trim());
        }
        return Map.copyOf(m);
    }

    /**
     * P1-IC (G-4 do coordenador, 06/11; P-A4.4): o sensor desconta os erros vindos do no sob o AC-7. Com o cache de
     * sessoes ignorado, o servidor de aplicacao fixado no alvo erra quase toda operacao (A4: 147 de 147 do pedido a
     * deteccao), e isso e efeito da propria acao, nao sinal do <i>cluster</i>. "Sob acao" = um AC-7 em
     * {@code ExecutorDeAcoes#ativas()} com esse alvo (do pedido ate o pedido de reversao). So em active: em shadow
     * nada e aplicado, e o braco A fica igual ao de antes. Os sucessos do alvo continuam contando.
     */
    static final class DescontoSobAcao {
        private final ModoAtuacao modo;
        private final Map<String, String> servidorPorAdmin;
        private final Supplier<List<AcaoCluster>> ativas;
        private final AtomicLong descontados = new AtomicLong();

        DescontoSobAcao(ModoAtuacao modo, Map<String, String> servidorPorAdmin, Supplier<List<AcaoCluster>> ativas) {
            this.modo = Objects.requireNonNull(modo, "modo");
            this.servidorPorAdmin = Map.copyOf(servidorPorAdmin);
            this.ativas = Objects.requireNonNull(ativas, "ativas");
        }

        /** {@code true}: o evento e erro do servidor de um no sob o AC-7 e nao vai ao laco (e contado). */
        boolean descontar(String replica, String codigoRetorno) {
            if (modo != ModoAtuacao.ACTIVE || replica == null
                    || !(EventoOperacao.ERROR_TRANSPORT.equals(codigoRetorno)
                    || EventoOperacao.ERROR_UNAVAILABLE.equals(codigoRetorno))) {
                return false;
            }
            for (AcaoCluster a : ativas.get()) {
                if (a.tipo() == AcaoCluster.Tipo.AC_7 && replica.equals(servidorPorAdmin.get(a.alvo()))) {
                    descontados.incrementAndGet();
                    return true;
                }
            }
            return false;
        }

        long descontados() {
            return descontados.get();
        }
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
        conferirTetoDaSonda(p, cli);
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
        conferirSondaDefasagem(p, cli, urls);
        return new Configuracao(modo, par, p, refPath, ref, urls, nos, cli.s3.hotrodTimeoutMs, cli.servidores);
    }

    /**
     * W-4 (B-26.3): com o bloco {@code defasagem}, (1) so em S3 (a sonda usa os parametros Hot Rod do S3 e a
     * checagem R7 do W-2a so existe em S3); (2) os parametros cabem no prazo de leitura ({@code --hotrod-timeout-ms},
     * o "nao viu", decisao do coordenador de 05/11) e na janela W ({@link ParametrosDefasagem#validarContra});
     * (3) o no {@code i} de {@code --servers} e o de {@code --controller-admin} sao o mesmo {@code host:porta}
     * ({@link CanalCanarioHotRod#conferirCorrespondencia}). A igualdade {@code politica.tetoLagMs} =
     * {@code --hotrod-timeout-ms} ja e conferida por {@link #conferirTetoDaSonda} (R7).
     *
     * @throws IllegalArgumentException com o motivo
     */
    static void conferirSondaDefasagem(ParametrosControlador p, Cli cli, List<String> urlsAdmin) {
        if (p.defasagem() == null) {
            return;
        }
        if (!cli.scenarioS3) {
            throw new IllegalArgumentException("o bloco defasagem (sonda S_lag, W-4) so vale com --scenario S3");
        }
        p.defasagem().validarContra(cli.s3.hotrodTimeoutMs, p.wNs());
        CanalCanarioHotRod.conferirCorrespondencia(urlsAdmin, cli.servidores);
    }

    /**
     * V-W2a R7: o "nao viu" do S_lag (W-4) e o prazo de leitura da sonda, o {@code hotrod_timeout_ms} da campanha
     * (prompt do W-4, item 2), gravado como {@code lagMaxMs}. O P1 so dispara com {@code lagMaxMs >= tetoLagMs}: um
     * {@code tetoLagMs} maior desliga o P1 sem aviso, e um menor dispara antes de a leitura desistir. Por isso, em S3
     * com o bloco {@code politica}, os dois tem de ser iguais, e o arquivo e recusado se nao forem.
     *
     * @throws IllegalArgumentException {@code tetoLagMs} diferente de {@code --hotrod-timeout-ms}
     */
    static void conferirTetoDaSonda(ParametrosControlador p, Cli cli) {
        if (p.politica() == null || !cli.scenarioS3) {
            return; // sem politica nao ha P1; fora do S3 nao ha sonda S_lag (ela usa os clientes do S3)
        }
        long teto = p.politica().tetoLagMs();
        if (teto != cli.s3.hotrodTimeoutMs) {
            throw new IllegalArgumentException("politica.tetoLagMs (" + teto + ") difere de --hotrod-timeout-ms ("
                    + cli.s3.hotrodTimeoutMs + "): o \"nao viu\" do S_lag e o prazo Hot Rod da sonda e tem de ser o"
                    + " mesmo valor (V-W2a R7)");
        }
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
                + "dry-run, atuador=" + (p.politica() == null ? new AtuadorNulo().nome()
                        : AtuadorPorTipo.nomeBracoB(AtuadorRestInfinispan.NOME, AtuadorIgnorarCache.NOME))
                + " politica=" + (p.politica() == null ? PoliticaDeAcao.NENHUMA.nome() : "por-assinatura")
                + " tick_ns=" + periodoTick(p.wNs(), PERIODO_SONDA_NS) + " sonda_periodo_ns=" + PERIODO_SONDA_NS
                + " prazo_chamada_ns=" + PRAZO_CHAMADA_NS + " prazo_encerramento=em_curso+pilha+T_cham pior_caso_ns="
                + piorCasoEncerramentoNs(p.politica())
                + (p.politica() == null ? "" : n + "dry-run, " + textoPolitica(p.politica()))
                + (p.defasagem() == null ? "" : n + "dry-run, " + textoDefasagem(c));
    }

    /**
     * V-W2a R1: pior caso de {@code T_enc} (contrato 1.2). Sem politica nenhuma acao e pedida: {@code T_cham}. Com a
     * {@link PoliticaPorAssinatura}: AC-2 na pilha ({@code T_cham}) + AC-5 em curso ({@code T_cham + 15 s}, P-42) +
     * {@code T_cham} = 21 s, porque so ha uma chave de AC-2 e a cota do AC-5 e 1. Sem a P2 (D-B, modo de avaliacao),
     * o AC-2 nunca entra na pilha: 19 s.
     *
     * @param politica parametros do bloco {@code politica}; {@code null} sem o bloco
     */
    static long piorCasoEncerramentoNs(ParametrosPolitica politica) {
        if (politica == null) {
            return PRAZO_CHAMADA_NS;
        }
        return AtuadorPorTipo.piorCasoEncerramentoBracoBNs(PRAZO_CHAMADA_NS, politica.p2Habilitada());
    }

    /** Parametros da politica (W-2a), com a origem declarada de cada um. */
    static String textoPolitica(ParametrosPolitica q) {
        return "politica kLag=" + q.kLag() + " tetoLagMs=" + q.tetoLagMs() + " tRebNs=" + q.tRebNs()
                + " cotaAc5=" + q.cotaAc5() + " p2Habilitada=" + q.p2Habilitada() + " acaoP1=" + q.acaoP1().rotulo()
                + " origem=" + q.origem();
    }

    /**
     * Parametros da sonda de defasagem (W-4), com a origem declarada; prazo de leitura (o "nao viu") =
     * {@code --hotrod-timeout-ms}, periodo = W.
     */
    static String textoDefasagem(Configuracao c) {
        ParametrosControlador p = c.parametros();
        ParametrosDefasagem d = p.defasagem();
        return "defasagem lagInMs=" + d.lagInMs() + " lagOutMs=" + d.lagOutMs() + " passoLeituraMs="
                + d.passoLeituraMs() + " prazo_leitura_ms=" + c.prazoHotRodMs() + " periodo_ns=" + p.wNs()
                + " origem=" + d.origem();
    }

    /** Pecas de producao: sonda REST, gancho da JVM e, com o bloco {@code politica}, o atuador REST (W-2a). */
    static Pecas pecasPadrao(Configuracao c, Cli cli) {
        return pecas(c, cli, new ConsultaSaudeRest(c.urlsAdmin(), cli.usuario, cli.senha), ExecutorDeAcoes.Gancho.jvm(),
                periodoTick(c.parametros().wNs(), PERIODO_SONDA_NS), PERIODO_SONDA_NS, PRAZO_SONDA_NS);
    }

    /**
     * W-2a (P-38): monta as pecas para uma fonte de saude dada. Sem o bloco {@code politica}: atuador nulo e
     * politica {@code nenhuma}. Com o bloco: {@link PoliticaPorAssinatura} sobre os caches do modo e
     * {@link AtuadorPorTipo#bracoB} sobre o {@link AtuadorRestInfinispan}, cujos "nos que responderam" vem da
     * ultima amostra da sonda (ligada em {@link #abrir}). W-4: com o bloco {@code defasagem}, abre o
     * {@link CanalCanarioHotRod} (Hot Rod, um cliente por no); sem ele, nenhum canal.
     */
    static Pecas pecas(Configuracao c, Cli cli, FonteDeSaude saude, ExecutorDeAcoes.Gancho gancho, long tickNs,
                       long periodoSondaNs, long prazoSondaNs) {
        Pecas sem = pecas(c, cli, saude, gancho, tickNs, periodoSondaNs, prazoSondaNs, null, false);
        if (c.parametros().defasagem() == null) {
            return sem;
        }
        return comCanario(sem, CanalCanarioHotRod.abrir(cli, c.urlsAdmin()));
    }

    /**
     * W-4: como {@link #pecas(Configuracao, Cli, FonteDeSaude, ExecutorDeAcoes.Gancho, long, long, long)}, com o
     * canal da sonda de defasagem dado (testes). O canal e exigido com o bloco {@code defasagem} e recusado sem ele.
     */
    static Pecas pecas(Configuracao c, Cli cli, FonteDeSaude saude, ExecutorDeAcoes.Gancho gancho, long tickNs,
                       long periodoSondaNs, long prazoSondaNs, CanalCanario canario) {
        return pecas(c, cli, saude, gancho, tickNs, periodoSondaNs, prazoSondaNs, canario, true);
    }

    private static Pecas pecas(Configuracao c, Cli cli, FonteDeSaude saude, ExecutorDeAcoes.Gancho gancho,
                               long tickNs, long periodoSondaNs, long prazoSondaNs, CanalCanario canario,
                               boolean conferirCanario) {
        if (conferirCanario && (c.parametros().defasagem() == null) != (canario == null)) {
            throw new IllegalArgumentException(canario == null
                    ? "o bloco defasagem exige o canal da sonda de defasagem"
                    : "canal da sonda de defasagem sem o bloco defasagem no " + c.arquivoParametros());
        }
        ParametrosPolitica q = c.parametros().politica();
        Pecas p;
        if (q == null) {
            p = new Pecas(saude, new AtuadorNulo(), PoliticaDeAcao.NENHUMA, gancho, tickNs, periodoSondaNs,
                    prazoSondaNs);
        } else {
            AtomicReference<Supplier<Set<String>>> respondidos = new AtomicReference<>(Set::of);
            AtuadorRestInfinispan rest = new AtuadorRestInfinispan(c.urlsAdmin(),
                    new ClienteAdminRest(cli.usuario, cli.senha), () -> respondidos.get().get(), PASSO_SAUDE_AC5_NS);
            // P1-IC: o AC-7 tem o seu cliente (desafio Digest proprio) e os mesmos "nos que responderam"
            AtuadorIgnorarCache ignorar = new AtuadorIgnorarCache(c.urlsAdmin(),
                    new ClienteAdminRest(cli.usuario, cli.senha), () -> respondidos.get().get(),
                    AtuadorIgnorarCache.Parametros.PADRAO);
            p = new Pecas(saude, AtuadorPorTipo.bracoB(rest, ignorar),
                    new PoliticaPorAssinatura(q, cli.replicacao.caches(), cli.replicacao.cacheSessoes()),
                    gancho, tickNs, periodoSondaNs, prazoSondaNs, respondidos);
        }
        return canario == null ? p : comCanario(p, canario);
    }

    private static Pecas comCanario(Pecas p, CanalCanario canario) {
        return new Pecas(p.saude(), p.atuador(), p.politica(), p.gancho(), p.tickNs(), p.periodoSondaNs(),
                p.prazoSondaNs(), p.respondidos(), canario);
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
        try {
            return abrirComPecas(c, cli, rep, relogio, origemNs, rastro, pecas);
        } catch (IOException | RuntimeException e) {
            if (pecas.canario() != null) {
                pecas.canario().close(); // W-4: os clientes Hot Rod da sonda nao ficam abertos se a abertura falhar
            }
            throw e;
        }
    }

    private static LigacaoControlador abrirComPecas(Configuracao c, Cli cli, int rep, EpochClock relogio,
                                                    long origemNs, RastroCliente rastro, Pecas pecas)
            throws IOException {
        FonteDeTempo tempo = () -> relogio.paraEpochNanos(System.nanoTime());
        Path dir = Path.of(cli.csvDir);
        // W-2b: fila de cada trilha = 4 x as linhas esperadas na execucao de --duration
        long duracaoNs = TimeUnit.SECONDS.toNanos(cli.duracaoSeg);
        long wNs = c.parametros().wNs();
        TrilhaJsonl obs = TrilhaJsonl.abrir(dir.resolve(String.format("observaveis-rep-%03d.jsonl", rep)),
                TrilhaJsonl.capacidadePara(linhasEsperadasObservaveis(duracaoNs, wNs, pecas.periodoSondaNs(),
                        pecas.saude() != null, pecas.canario() != null)));
        TrilhaJsonl dec = TrilhaJsonl.abrir(dir.resolve(String.format("decisoes-rep-%03d.jsonl", rep)),
                TrilhaJsonl.capacidadePara(linhasEsperadasDecisoes(duracaoNs, wNs)));
        SondaTopologia sonda = null;
        FonteDeTopologia topologia = FonteDeTopologia.INDISPONIVEL;
        if (pecas.saude() != null) {
            sonda = new SondaTopologia(pecas.saude(), cli.replicacao.caches(), c.nosEsperados(),
                    pecas.periodoSondaNs(), pecas.prazoSondaNs(), tempo,
                    a -> obs.escrever(FormatoJsonl.topologia(a)));
            topologia = sonda; // W-2a: o laco usa resumirComNosAte (estado por no)
            if (pecas.respondidos() != null) {
                pecas.respondidos().set(sonda::respondidosNaUltimaAmostra);
            }
        }
        // W-4: sonda de defasagem; prazo de leitura = --hotrod-timeout-ms (= politica.tetoLagMs pela R7), periodo = W
        SondaDefasagem lag = null;
        FonteDeDefasagem defasagem = FonteDeDefasagem.INDETERMINADA;
        if (pecas.canario() != null) {
            ParametrosControlador p = c.parametros();
            lag = new SondaDefasagem(pecas.canario(), p.defasagem(), c.prazoHotRodMs(), p.wNs(), origemNs,
                    tempo, r -> obs.escrever(FormatoJsonl.defasagem(r)));
            defasagem = lag;
        }
        // W-2b: o gancho da JVM espera a gravadora do decisoes no que sobra do T_enc
        ExecutorDeAcoes[] exec = new ExecutorDeAcoes[1];
        ExecutorDeAcoes.Gancho gancho = ganchoComDreno(pecas.gancho(), () -> {
            Long t = exec[0] == null ? null : exec[0].prazoEncerramentoUsadoNs();
            return t == null ? 0L : t;
        }, dec::aguardarGravacao);
        ExecutorDeAcoes executor = new ExecutorDeAcoes(c.modo(), pecas.atuador(), PRAZO_CHAMADA_NS,
                tempo, r -> dec.escrever(FormatoJsonl.acao(r)), gancho);
        exec[0] = executor;
        Referencia ref = c.referencia() == null ? Referencia.vazia() : c.referencia().referencia();
        MetaExecucao meta = new MetaExecucao(VERSAO_PROGRAMA, rep, c.modo(), cli.scenarioS3 ? "S3" : cli.scenario.name(),
                cli.replicacao.rotulo(), c.nosEsperados(), c.parametros(),
                c.arquivoReferencia() == null ? null : c.arquivoReferencia().toString(),
                c.referencia() == null ? null : c.referencia().sha256(), AgregadorDeJanela.TOLERANCIA_PADRAO_NS,
                pecas.tickNs(), origemNs, pecas.atuador().nome(), pecas.politica().nome(), sonda != null,
                lag != null);
        LigacaoControlador[] self = new LigacaoControlador[1];
        FonteDeViolacoes violacoes = () -> self[0].classificarPendentes();
        LacoDeControle laco = new LacoDeControle(new LacoDeControle.Componentes(meta,
                new AgregadorDeJanela(origemNs, c.parametros(), ref), new Controlador(c.parametros()),
                topologia, defasagem, violacoes, pecas.politica(), executor, tempo, obs, dec));
        LigacaoControlador l = new LigacaoControlador(c, laco, sonda,
                pecas.saude() instanceof ConsultaSaudeRest r ? r : null, tempo, rastro, lag, pecas.canario(), obs, dec,
                executor);
        self[0] = l;
        if (c.modo() == ModoAtuacao.ACTIVE && c.parametros().politica() != null
                && c.parametros().politica().acaoP1() == AcaoCluster.Tipo.AC_7) {
            // P1-IC: guarda continua do AC-7; o desfazer vai pela fila unica do executor (origem GUARDA)
            l.guarda = new GuardaContinuaIgnorados(c.urlsAdmin(), new ClienteAdminRest(cli.usuario, cli.senha),
                    executor::ativas, executor::reverterPelaGuarda,
                    System::nanoTime, CARENCIA_GUARDA_NS, PRAZO_LEITURA_GUARDA_NS);
            l.guarda.iniciar(PERIODO_GUARDA_NS);
        }
        if (sonda != null) {
            sonda.iniciar();
        }
        if (lag != null) {
            lag.iniciar(); // depois da linha meta, gravada pelo construtor do laco
        }
        laco.iniciar(pecas.tickNs());
        return l;
    }

    // ------------------------------------------------------------------ ganchos

    /** Para {@link SessionOps#definirOuvinteDeEvento}; depois do {@link #close()}, ignora. Sem rotulo de servidor. */
    SessionOps.OuvinteDeEvento ouvinteDeEvento() {
        return ouvinteDeEvento(null);
    }

    /**
     * P1-IC (G-4): ouvinte de um servidor de aplicacao ({@code replica} = rotulo {@code host:porta} de
     * {@code --servers}, a coluna {@code replica} do CSV); os erros dele enquanto o no estiver sob o AC-7 nao vao ao
     * laco ({@link DescontoSobAcao}).
     */
    SessionOps.OuvinteDeEvento ouvinteDeEvento(String replica) {
        return (op, ini, fim, rc) -> {
            if (fechada) {
                return;
            }
            if (desconto.descontar(replica, rc)) {
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
        if (guarda != null) {
            guarda.close(); // antes do encerramento do executor: nenhum pedido novo de desfazer depois dele
        }
        if (sonda != null) {
            sonda.close();
        }
        if (defasagem != null) {
            defasagem.close(); // a gravadora termina as linhas na fila antes de o laco fechar o observaveis
        }
        if (canario != null) {
            canario.close();
        }
        resumo = laco.encerrar(o -> {
            o.put("eventos_fora_do_contrato", eventosForaDoContrato.get());
            // P1-IC (G-4, aditivo no observaveis 1.2): erros do no sob o AC-7 descontados do sensor
            o.put("eventos_descontados_sob_acao", desconto.descontados());
            // P1-IC (aditivo no observaveis 1.2): desfazeres pedidos pela guarda continua do AC-7; null sem ela
            o.put("guarda_ac7_desfazeres", guarda == null ? null : guarda.desfazeres());
            o.put("sonda_rodadas", sonda == null ? null : sonda.rodadas());
            o.put("sonda_rodadas_puladas", sonda == null ? null : sonda.rodadasPuladas());
            o.put("sonda_amostras_atrasadas", sonda == null ? null : sonda.amostrasAtrasadas());
            o.put("sonda_erros_ouvinte", sonda == null ? null : sonda.errosOuvinte());
            // W-2b (P-5): a sonda REST nao cria processo; o campo fica no esquema e vale 0 com ela
            o.put("curl_lancados", rest == null ? null : rest.lancamentos());
            // observaveis 1.2 (W-4): custo do S_lag, contado a parte da carga util; null sem a sonda
            if (defasagem == null) {
                SondaDefasagem.nomesDosContadores().forEach(o::putNull);
            } else {
                defasagem.contadores().forEach(o::put);
            }
            // P-44: a chave de cada no e se ela tinha o proprio no como dono primario na abertura
            if (canario == null) {
                o.putNull("defasagem_chaves");
            } else {
                var chaves = o.putArray("defasagem_chaves");
                for (int i = 0; i < canario.nos(); i++) {
                    chaves.addObject().put("no", canario.rotulo(i)).put("chave", canario.chave(i))
                            .put("primario", canario.chaveNoPrimario(i)).put("motivo", canario.motivoDaChave(i));
                }
            }
        });
    }

    /** W-2b: trilha do {@code observaveis} (testes). */
    TrilhaJsonl trilhaObservaveis() {
        return trilhaObs;
    }

    /** W-2b: trilha do {@code decisoes} (testes). */
    TrilhaJsonl trilhaDecisoes() {
        return trilhaDec;
    }

    /**
     * W-6 (P-6 da V-W5): alvo ({@code host:porta}) do AC-5 que ficou APLICADA nesta execucao, ou {@code null}. Vem
     * do executor, em memoria (a trilha no disco nao e lida). Chamado depois do {@link #close()}.
     */
    @Override
    public String ac5AplicadaAlvo() {
        return executor.alvoDoAc5Aplicado();
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
