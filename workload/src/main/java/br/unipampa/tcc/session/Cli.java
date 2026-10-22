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
        description = "Seed base do gerador Zipfian (T6). Cada thread usa seed+tid. Padrao ${DEFAULT-VALUE}.",
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

    @Option(
        names = {"--sweep"},
        description = "Executa a varredura final global (I1, I4, I2 global, bloqueio perdido) e grava varredura-rep-NNN.csv."
    )
    public boolean varredura;

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
        if (paraNoFimDaFalha && (marcadores == null || marcadores.isBlank())) {
            throw new IllegalArgumentException("--stop-at-fault-end exige --marker");
        }
        if (scenarioS3) {
            s3.validar();
        }
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
