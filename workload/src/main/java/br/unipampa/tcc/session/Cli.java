package br.unipampa.tcc.session;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Configuração da linha de comando do workload, segregada do
 * {@link WorkloadMain} para simplificar testes e composição.
 *
 * <p>A CLI parametriza diretamente os elementos T7, T9, T10, T12 e T14
 * da Tabela 1 do Cap. 3 §3.3.5 da monografia. Os defaults seguem o
 * que está nessa Tabela quando aplicável (cenário S1, 30 repetições,
 * 1\,000\,000 de operações por execução).</p>
 *
 * <p>Esta classe é puramente um \emph{value object}: o trabalho efetivo
 * fica em {@link WorkloadMain} e é parametrizado pelos campos desta
 * classe após a chamada de {@code picocli.CommandLine.populateCommand}
 * (ou da execução do {@code Callable}).</p>
 */
@Command(
    name = "session-workload",
    mixinStandardHelpOptions = true,
    version = "session-workload 0.1.0-SNAPSHOT",
    description = "Workload Java para o cluster Infinispan; gera operacoes "
        + "O1-O7 do protocolo de estado de sessao, registra latencia por "
        + "operacao e detecta violacoes I1-I6. Cobertura T5/T6/T7/T8/T9/"
        + "T10/T11/T12/T13/T14 da Tabela 1 do Cap. 3 secao 3.3.5."
)
public final class Cli {

    /**
     * Cenario de mistura (T7). B-33 (E2): o valor e lido como texto para que {@code S3}
     * (simulacao de ciclo de vida, fora do enum) seja tratado ANTES de {@link Scenario}; S1 e
     * S2 continuam resolvidos pelo enum, como antes, e valor desconhecido continua sendo erro
     * de parametro (nao cai no fallback de {@link Scenario#porNome}).
     */
    public Scenario scenario = Scenario.S1;

    /** B-33 (E2): {@code true} com {@code --scenario S3}. */
    public boolean scenarioS3;

    @Option(
        names = {"--scenario", "-s"},
        description = "Cenario: S1, S2 (mistura de operacoes, T7) ou S3 (simulacao de ciclo de vida de sessao, B-33). Padrao ${DEFAULT-VALUE}.",
        defaultValue = "S1",
        converter = CenarioConverter.class
    )
    public void definirCenario(Escolha escolha) {
        this.scenarioS3 = escolha.s3();
        if (!escolha.s3()) {
            this.scenario = escolha.scenario();
        }
    }

    /** Resultado da conversao do texto de {@code --scenario}. */
    public record Escolha(boolean s3, Scenario scenario) { }

    /** Converte "S1"/"S2" (enum) ou "S3" (modo novo); outro valor e erro de parametro. */
    public static final class CenarioConverter implements CommandLine.ITypeConverter<Escolha> {
        @Override
        public Escolha convert(String valor) {
            if ("S3".equals(valor)) {
                return new Escolha(true, null);
            }
            try {
                return new Escolha(false, Scenario.valueOf(valor));
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(
                        "valor invalido '" + valor + "' para --scenario (aceitos: S1, S2, S3)");
            }
        }
    }

    /** B-33 (E2): parametros do cenario S3 (so valem com {@code --scenario S3}). */
    @Mixin
    public S3Parametros s3 = new S3Parametros();

    @Option(
        names = {"--duration", "-d"},
        description = "Duracao do workload em segundos (T14). Padrao ${DEFAULT-VALUE}.",
        defaultValue = "600"
    )
    public long duracaoSeg;

    @Option(
        names = {"--ops", "-o"},
        description = "Numero alvo de operacoes por execucao (T9). 0 = sem limite, usa apenas --duration. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "1000000"
    )
    public long ops;

    @Option(
        names = {"--rep", "-r"},
        description = "Numero de repeticoes do cenario (T10). Cada repeticao tem CSV separado se --csv-dir for fornecido. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "30"
    )
    public int repeticoes;

    @Option(
        names = {"--threads", "-t"},
        description = "Numero de threads concorrentes. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "8"
    )
    public int threads;

    @Option(
        names = {"--servers"},
        description = "Lista host:port,host:port,... do cluster Infinispan. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224"
    )
    public String servidores;

    @Option(
        names = {"--username"},
        description = "Usuario para autenticacao no Hot Rod. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "admin"
    )
    public String usuario;

    @Option(
        names = {"--password"},
        description = "Senha para autenticacao no Hot Rod. Pode vir de TCC_PASSWORD se nao fornecida.",
        defaultValue = "infinispan"
    )
    public String senha;

    @Option(
        names = {"--seed"},
        description = "Semente base: gerador Zipf, mistura de operacoes e token antigo de cada thread, "
            + "por sementeThread(seed, tid, rep). Padrao ${DEFAULT-VALUE}.",
        defaultValue = "42"
    )
    public long seedBase;

    @Option(
        names = {"--csv-dir"},
        description = "Diretorio para dump CSV por repeticao. Cada repeticao escreve <dir>/rep-NNN.csv. Se omitido, nada e gravado."
    )
    public String csvDir;

    @Option(
        names = {"--warmup-min-sec"},
        description = "Tempo minimo de warm-up em segundos (T11). Padrao ${DEFAULT-VALUE}.",
        defaultValue = "60"
    )
    public long warmupMinimoSeg;

    @Option(
        names = {"--dry-run"},
        description = "Verifica configuracao e sai sem conectar ao cluster."
    )
    public boolean dryRun;

    // ---- B-33 (instrumentacao de particao): todas desligadas por padrao ----

    @Option(
        names = {"--marker"},
        description = "Arquivo de marcadores de falha (fault=F2 node=<no> event=start|split|end t_epoch_ns=<n>). "
            + "Com ele, o CSV de eventos ganha a coluna phase e o de violacoes a coluna fase."
    )
    public String marcadores;

    @Option(
        names = {"--w-recovery-sec"},
        description = "Janela de recuperacao W_recovery apos o fim da falha, em segundos. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "60"
    )
    public long wRecoverySeg;

    @Option(
        names = {"--stop-at-fault-end"},
        description = "Para a carga de escrita quando o marcador event=end aparece (exige --marker)."
    )
    public boolean paraNoFimDaFalha;

    @Option(
        names = {"--convergence"},
        description = "Apos a carga, executa o laco de convergencia (I5/I6, rodadas validas) e grava convergencia-rep-NNN.csv."
    )
    public boolean convergencia;

    @Option(
        names = {"--convergence-timeout-sec"},
        description = "Teto do laco de convergencia em segundos. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "600"
    )
    public long convergenciaTetoSeg;

    @Option(
        names = {"--convergence-period-sec"},
        description = "Periodo entre rodadas de convergencia em segundos. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "5"
    )
    public long convergenciaPeriodoSeg;

    /**
     * W-6 (B-46; P-6 da V-W5): prazo, em segundos, para a carga ver de novo todos os servidores depois do fim da
     * carga, quando o AC-5 parou um no (o orquestrador religa o no ao ler a linha {@code Fim da carga:}). Sem
     * valor padrao (regra do autor): obrigatorio com {@code --controller active} e {@code --convergence}; aceito e
     * sem efeito nos demais modos. Fica fora de {@link #toString()}.
     */
    @Option(
        names = {"--rejoin-timeout-sec"},
        description = "Prazo (s) para a carga ver de novo todos os servidores quando o AC-5 parou um no, antes do "
            + "laco de convergencia. Sem padrao; obrigatorio com --controller active e --convergence."
    )
    public Long religamentoPrazoSeg;

    @Option(
        names = {"--sweep"},
        description = "Executa a varredura final global (I1, I4, I2 global, bloqueio perdido) e grava varredura-rep-NNN.csv."
    )
    public boolean varredura;

    /**
     * Complemento 1 do benchmark refeito (DT-027, secao 5, item 3): teto, em segundos contados do marcador {@code end},
     * da espera pela fusao antes da varredura final ({@link EsperaFusao}). Sem valor padrao (regra do autor): ausente,
     * a varredura roda como antes, sem espera. Exige {@code --sweep} e {@code --controller-admin} (as URLs REST dos
     * nos, uma por servidor). Fica fora de {@link #toString()}.
     */
    @Option(
        names = {"--sweep-merge-timeout-sec"},
        description = "Antes da varredura final, espera os nos de --controller-admin mostrarem a mesma vista com todos "
            + "os membros (saude REST), ate o marcador end mais este teto (s); sem fusao, a varredura nao roda e "
            + "varredura-rep-NNN.erro.txt traz o motivo. Sem padrao; exige --sweep e --controller-admin."
    )
    public Long varreduraFusaoTetoSeg;

    @Option(
        names = {"--rest-url"},
        description = "Base do REST do Infinispan para a saude do cluster no laco de convergencia. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "http://localhost:11222"
    )
    public String restUrl;

    /**
     * B-42.1: perfil de replicacao. {@code sync} (padrao) abre {@code sessions}/{@code counters}
     * (DIST_SYNC, controle; fluxo identico ao anterior a B-42.1); {@code async} abre os irmaos
     * {@code sessions-async}/{@code counters-async} (DIST_ASYNC). Outro valor e erro de parametro.
     */
    @Option(
        names = {"--replication"},
        description = "Perfil de replicacao: sync (caches sessions/counters, DIST_SYNC) ou async "
            + "(sessions-async/counters-async, DIST_ASYNC). Padrao ${DEFAULT-VALUE}.",
        defaultValue = "sync",
        converter = ReplicacaoConverter.class
    )
    public PerfilReplicacao replicacao = PerfilReplicacao.SYNC;

    /** Converte {@code sync}/{@code async}; outro valor e {@link CommandLine.TypeConversionException}. */
    public static final class ReplicacaoConverter implements CommandLine.ITypeConverter<PerfilReplicacao> {
        @Override
        public PerfilReplicacao convert(String valor) {
            try {
                return PerfilReplicacao.porRotulo(valor);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }
        }
    }

    /**
     * B-43.1: reset de estado antes do <i>warm-up</i> de cada repeticao ({@code true}, padrao
     * {@code on}) ou fluxo anterior, sem nenhuma operacao nos caches ({@code false}, {@code off},
     * so explicito). Ver {@link ResetEstado}. Fica fora de {@link #toString()} (mesmo criterio da
     * linha {@code Replicacao:} de B-42.1); aparece no {@code --dry-run} e na linha {@code Reset:}.
     */
    public boolean resetEstado = true;

    /**
     * Opcao {@code --reset-state on|off}. O valor passa por {@link LigaDesliga}, e nao direto para
     * o campo {@code boolean}, porque o Picocli reaplica o padrao de opcoes {@code boolean} como
     * {@code "true"}, que o conversor estrito rejeitaria.
     */
    @Option(
        names = {"--reset-state"},
        paramLabel = "on|off",
        description = "Esvazia os dois caches do perfil antes do warm-up de cada repeticao e prova "
            + "que ficaram vazios (on), ou nao toca nos caches (off). Se nao esvaziarem, a execucao "
            + "aborta com codigo de saida 5. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "on",
        arity = "1",
        converter = LigaDesligaConverter.class
    )
    public void definirResetEstado(LigaDesliga valor) {
        this.resetEstado = valor.ligado();
    }

    /** Resultado da conversao de {@code on}/{@code off}. */
    public record LigaDesliga(boolean ligado) { }

    /** Converte exatamente {@code on}/{@code off}; outro valor e {@link CommandLine.TypeConversionException}. */
    public static final class LigaDesligaConverter implements CommandLine.ITypeConverter<LigaDesliga> {
        @Override
        public LigaDesliga convert(String valor) {
            if ("on".equals(valor)) {
                return new LigaDesliga(true);
            }
            if ("off".equals(valor)) {
                return new LigaDesliga(false);
            }
            throw new CommandLine.TypeConversionException(
                    "valor invalido '" + valor + "' para --reset-state (aceitos: on, off)");
        }
    }

    /** Rotulo {@code on}/{@code off} de {@link #resetEstado}. */
    public String rotuloResetEstado() {
        return resetEstado ? "on" : "off";
    }

    /**
     * B-43.2: versao da mistura S1/S2 ({@link VersaoMistura}); padrao {@code v2} (P-a). Fica fora de
     * {@link #toString()}, como {@code --replication} e {@code --reset-state}; aparece na 4a linha do
     * {@code --dry-run} e na linha {@code Mistura:} do stdout. B-43.7: {@code v3} aceito; o padrao
     * continua {@code v2} (DT-005: toda execucao de medicao passa {@code --mix v3} explicitamente).
     */
    public VersaoMistura mix = VersaoMistura.V2;

    @Option(
        names = {"--mix"},
        paramLabel = "v1|v2|v3",
        description = "Versao da mistura S1/S2: v2 (Login em pool proprio user-<tid>-<k>, O4/O5 em "
            + "lim-<tid> e carga inicial de sid-000001..sid-100000 como sessoes VALID antes do warm-up; "
            + "exige --reset-state on), v3 (slot-geracao: como v2 no Login e em O4/O5; o slot Zipf tem "
            + "uma sessao corrente sid-NNNNNN~g, cada Validate isolado le o token antigo com probabilidade "
            + "q = 0.05, por sorteio semeado por thread, e o Logout isolado repoe a geracao seguinte "
            + "fora do CSV; carga de 2 sessoes "
            + "por slot; exige --reset-state on) ou v1 (uma identidade user-<tid> por thread, sem carga; "
            + "comportamento anterior, para regressao). Sem efeito em S3. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "v2",
        arity = "1",
        converter = MisturaConverter.class
    )
    public void definirMistura(VersaoMistura valor) {
        this.mix = valor;
    }

    /** Converte exatamente {@code v1}/{@code v2}/{@code v3}; outro valor e {@link CommandLine.TypeConversionException}. */
    public static final class MisturaConverter implements CommandLine.ITypeConverter<VersaoMistura> {
        @Override
        public VersaoMistura convert(String valor) {
            try {
                return VersaoMistura.porRotulo(valor);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }
        }
    }

    // ---- W-3 (B-28.2): controlador do cluster; desligado por padrao ----

    /**
     * W-3: modo do controlador. {@code off} (padrao): nenhum objeto do pacote {@code controle} e criado.
     * {@code shadow}: o laco decide e grava os JSONL, sem chamar o atuador. {@code active}: tambem entrega
     * as acoes ao atuador. Fica fora de {@link #toString()}, como {@code --replication}.
     */
    public ModoControlador controlador = ModoControlador.OFF;

    @Option(
        names = {"--controller"},
        paramLabel = "off|shadow|active",
        description = "Controlador do cluster: off (nenhuma thread nem arquivo), shadow (decide e grava "
            + "observaveis-rep-NNN.jsonl e decisoes-rep-NNN.jsonl, sem agir) ou active (tambem age sobre o "
            + "cluster pela API administrativa). Padrao ${DEFAULT-VALUE}.",
        defaultValue = "off",
        arity = "1",
        converter = ControladorConverter.class
    )
    public void definirControlador(ModoControlador valor) {
        this.controlador = valor;
    }

    /** Converte exatamente {@code off}/{@code shadow}/{@code active}. */
    public static final class ControladorConverter implements CommandLine.ITypeConverter<ModoControlador> {
        @Override
        public ModoControlador convert(String valor) {
            try {
                return ModoControlador.porRotulo(valor);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }
        }
    }

    @Option(
        names = {"--controller-config"},
        description = "Arquivo JSON com os parametros do nucleo do controlador (controlador-<nome>.json). "
            + "Obrigatorio com --controller shadow|active."
    )
    public String controladorConfig;

    @Option(
        names = {"--controller-reference"},
        description = "Arquivo referencia-<cenario>-<sync|async>.json (p99 por operacao, B-39.1). "
            + "Obrigatorio com --controller active; opcional em shadow."
    )
    public String controladorReferencia;

    @Option(
        names = {"--controller-admin"},
        description = "URLs REST dos nos (http://h:p ou h:p, separadas por virgula, na ordem de --servers) para "
            + "a sonda de topologia e o atuador. Obrigatorio com --controller shadow|active."
    )
    public String controladorAdmin;

    /**
     * B-43.2: {@link #textoDryRun()} (tres linhas, inalteradas) seguido da 4a linha
     * {@code dry-run, mix=v1|v2}. Com {@code v2} e {@code --reset-state off} em S1/S2, a linha avisa
     * que a execucao real sairia com 2 ({@link #validar()} so dispensa a regra no {@code --dry-run}).
     */
    public String textoDryRunComMistura() {
        String aviso = misturaExigeResetAusente()
                ? " (invalido com reset_state=off: a execucao sai com 2; use --reset-state on ou --mix v1)"
                : "";
        return textoDryRun() + System.lineSeparator() + "dry-run, mix=" + rotuloMistura() + aviso;
    }

    /**
     * B-43.7: rotulo da mistura no dry-run e na linha {@code Mistura:} ({@code v1}, {@code v2} ou
     * {@code v3 q=0.05}).
     */
    public String rotuloMistura() {
        return mix == VersaoMistura.V3 ? mix.rotulo() + " q=" + MisturaV3.rotuloQ() : mix.rotulo();
    }

    /**
     * {@code true} se a combinacao {@code --mix v2|v3} + {@code --reset-state off} vale (S1/S2). B-43.7:
     * P-b vale para v3 (a carga de slots verifica {@code size() == 2 x slots}).
     */
    private boolean misturaExigeResetAusente() {
        return (mix == VersaoMistura.V2 || mix == VersaoMistura.V3) && !resetEstado && !scenarioS3;
    }

    /**
     * Texto impresso por {@code --dry-run}: a linha historica (inalterada) seguida do perfil de
     * replicacao e dos nomes de cache que a execucao abriria e do estado do reset (B-43.1).
     */
    public String textoDryRun() {
        return "dry-run, configuracao OK: " + this + System.lineSeparator()
                + "dry-run, " + replicacao.descricao() + System.lineSeparator()
                + "dry-run, reset_state=" + rotuloResetEstado();
    }

    /** Sanidade dos parâmetros; lança IllegalArgumentException com motivo. */
    public void validar() {
        if (duracaoSeg <= 0) {
            throw new IllegalArgumentException("--duration deve ser > 0");
        }
        if (repeticoes <= 0) {
            throw new IllegalArgumentException("--rep deve ser > 0");
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("--threads deve ser > 0");
        }
        if (ops < 0) {
            throw new IllegalArgumentException("--ops deve ser >= 0");
        }
        if (warmupMinimoSeg < 0) {
            throw new IllegalArgumentException("--warmup-min-sec deve ser >= 0");
        }
        if (servidores == null || servidores.isBlank()) {
            throw new IllegalArgumentException("--servers nao pode estar vazio");
        }
        if (wRecoverySeg < 0) {
            throw new IllegalArgumentException("--w-recovery-sec deve ser >= 0");
        }
        if (convergenciaTetoSeg <= 0) {
            throw new IllegalArgumentException("--convergence-timeout-sec deve ser > 0");
        }
        if (convergenciaPeriodoSeg <= 0) {
            throw new IllegalArgumentException("--convergence-period-sec deve ser > 0");
        }
        if (religamentoPrazoSeg != null && religamentoPrazoSeg <= 0) {
            throw new IllegalArgumentException("--rejoin-timeout-sec deve ser > 0");
        }
        if (paraNoFimDaFalha && (marcadores == null || marcadores.isBlank())) {
            throw new IllegalArgumentException("--stop-at-fault-end exige --marker");
        }
        validarFusaoAntesDaVarredura();
        if (scenarioS3) {
            s3.validar();
        }
        validarControlador();
        // B-43.2 (P-b): a carga inicial de v2 verifica size()==universo, que so vale sobre caches
        // vazios. Dispensado no --dry-run (nao executa; a 4a linha avisa) e em S3 (mistura sem efeito).
        if (misturaExigeResetAusente() && !dryRun) {
            if (mix == VersaoMistura.V3) {
                throw new IllegalArgumentException("--mix v3 exige --reset-state on (a carga de slots precisa "
                        + "dos caches vazios); para --reset-state off use --mix v1");
            }
            throw new IllegalArgumentException("--mix v2 exige --reset-state on (a carga inicial precisa "
                    + "dos caches vazios); para --reset-state off use --mix v1");
        }
    }

    /**
     * W-3: regras das opcoes do controlador (exit 2 em {@code main}). Os arquivos so sao lidos depois, por
     * {@code LigacaoControlador.carregar}.
     */
    private void validarControlador() {
        if (controlador == ModoControlador.OFF) {
            return;
        }
        String modo = "--controller " + controlador.rotulo();
        if (vazio(controladorConfig)) {
            throw new IllegalArgumentException(modo + " exige --controller-config (parametros do nucleo; "
                    + "nenhum limiar e fixo no codigo)");
        }
        if (vazio(controladorAdmin)) {
            throw new IllegalArgumentException(modo + " exige --controller-admin (URLs REST dos nos)");
        }
        if (vazio(csvDir)) {
            throw new IllegalArgumentException(modo + " exige --csv-dir (onde ficam observaveis-rep-NNN.jsonl "
                    + "e decisoes-rep-NNN.jsonl)");
        }
        if (controlador == ModoControlador.ACTIVE && vazio(controladorReferencia)) {
            throw new IllegalArgumentException(modo + " exige --controller-reference (p99 de referencia por "
                    + "operacao, sinal de latencia)");
        }
        if (controlador == ModoControlador.ACTIVE && convergencia && religamentoPrazoSeg == null) {
            throw new IllegalArgumentException(modo + " com --convergence exige --rejoin-timeout-sec (prazo para ver "
                    + "de novo todos os servidores quando o AC-5 parou um no; sem valor padrao)");
        }
        if (scenarioS3 && vazio(marcadores)) {
            throw new IllegalArgumentException(modo + " com --scenario S3 exige --marker: sem marcador nao ha "
                    + "rastro de cliente, a I2 nao e classificada como REAL e a violacao I2 nao dispara R1");
        }
        int admin = controladorAdmin.split(",", -1).length;
        int servidoresN = servidores.split(",", -1).length;
        if (admin != servidoresN) {
            throw new IllegalArgumentException("--controller-admin tem " + admin + " URLs e --servers tem "
                    + servidoresN + " servidores (H6: nos esperados = itens de --servers, um no REST por servidor)");
        }
    }

    /** DT-027 (secao 5, item 3): regras de {@code --sweep-merge-timeout-sec} (exit 2 em {@code main}). */
    private void validarFusaoAntesDaVarredura() {
        if (varreduraFusaoTetoSeg == null) {
            return;
        }
        if (varreduraFusaoTetoSeg <= 0) {
            throw new IllegalArgumentException("--sweep-merge-timeout-sec deve ser > 0");
        }
        if (!varredura) {
            throw new IllegalArgumentException("--sweep-merge-timeout-sec exige --sweep (a checagem de fusao antecede "
                    + "a varredura final)");
        }
        if (vazio(controladorAdmin)) {
            throw new IllegalArgumentException("--sweep-merge-timeout-sec exige --controller-admin (URLs REST dos nos, "
                    + "para ler a vista de cada um)");
        }
        int admin = controladorAdmin.split(",", -1).length;
        int servidoresN = servidores.split(",", -1).length;
        if (admin != servidoresN) {
            throw new IllegalArgumentException("--controller-admin tem " + admin + " URLs e --servers tem "
                    + servidoresN + " servidores (--sweep-merge-timeout-sec: a vista de cada no, um no REST por "
                    + "servidor)");
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return String.format(
            "Cli{scenario=%s, duracao=%ds, ops=%d, rep=%d, threads=%d, "
                + "seed=%d, csvDir=%s, warmupMin=%ds, dryRun=%s}",
            scenarioS3 ? "S3" : scenario, duracaoSeg, ops, repeticoes, threads,
            seedBase, csvDir, warmupMinimoSeg, dryRun);
    }
}
