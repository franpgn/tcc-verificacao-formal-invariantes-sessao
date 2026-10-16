package br.unipampa.tcc.session;

import picocli.CommandLine.Command;
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

    @Option(
        names = {"--scenario", "-s"},
        description = "Cenario de mistura de operacoes (T7): ${COMPLETION-CANDIDATES}. Padrao ${DEFAULT-VALUE}.",
        defaultValue = "S1"
    )
    public Scenario scenario;

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
    }

    @Override
    public String toString() {
        return String.format(
            "Cli{scenario=%s, duracao=%ds, ops=%d, rep=%d, threads=%d, "
                + "seed=%d, csvDir=%s, warmupMin=%ds, dryRun=%s}",
            scenario, duracaoSeg, ops, repeticoes, threads,
            seedBase, csvDir, warmupMinimoSeg, dryRun);
    }
}
