package br.unipampa.tcc.session;

import picocli.CommandLine.Option;

/**
 * B-33 (E2): parametros do cenario S3 (simulacao de ciclo de vida de sessao). Todos
 * parametrizaveis por CLI (mixin de {@link Cli}); os valores usados sao fixados no
 * pre-registro de T-F. Os padroes seguem a secao T-E, item 5, do plano de fechamento.
 */
public final class S3Parametros {

    /** Como as aplicacoes se ligam ao cluster. */
    public enum ModoCliente { pinned, topology }

    @Option(names = "--client-mode",
            description = "S3: pinned (um servidor de aplicacao por no, sem aprender a topologia) ou "
                    + "topology (cliente topology-aware, contraste). Padrao ${DEFAULT-VALUE}.",
            defaultValue = "pinned")
    public ModoCliente modoCliente = ModoCliente.pinned;

    @Option(names = "--admin-node",
            description = "S3: no do console administrativo, isnK (K-esimo de --servers) ou host:port. "
                    + "Deve ser um no da maioria. Padrao ${DEFAULT-VALUE}.",
            defaultValue = "isn1")
    public String noAdmin = "isn1";

    @Option(names = "--s3-users", defaultValue = "20",
            description = "S3: identidades do pool U (usuarios). Padrao ${DEFAULT-VALUE}.")
    public int usuarios = 20;

    @Option(names = "--s3-limit-accounts", defaultValue = "10",
            description = "S3: identidades do pool L (senha errada). Padrao ${DEFAULT-VALUE}.")
    public int contasLimite = 10;

    @Option(names = "--s3-think-ms", defaultValue = "1000",
            description = "S3: media (exponencial) do tempo de pensar entre paginas, ms. Padrao ${DEFAULT-VALUE}.")
    public double pensarMs = 1000;

    @Option(names = "--s3-session-median-sec", defaultValue = "20",
            description = "S3: mediana (log-normal) da duracao da sessao, s. Padrao ${DEFAULT-VALUE}.")
    public double sessaoMedianaSeg = 20;

    @Option(names = "--s3-session-sigma", defaultValue = "0.6",
            description = "S3: sigma da log-normal da duracao da sessao. Padrao ${DEFAULT-VALUE}.")
    public double sessaoSigma = 0.6;

    @Option(names = "--s3-logout-prob", defaultValue = "0.7",
            description = "S3: probabilidade de Logout ao fim da sessao (o resto abandona em silencio). Padrao ${DEFAULT-VALUE}.")
    public double probLogout = 0.7;

    @Option(names = "--s3-idle-timeout-sec", defaultValue = "30",
            description = "S3: timeout de sessao (idle) no cliente, s. Padrao ${DEFAULT-VALUE}.")
    public double idleSeg = 30;

    @Option(names = "--s3-back-window-sec", defaultValue = "10",
            description = "S3: janela do botao voltar (Validate repetido apos Logout), s. Padrao ${DEFAULT-VALUE}.")
    public double voltarJanelaSeg = 10;

    @Option(names = "--s3-back-min-sec", defaultValue = "1",
            description = "S3: intervalo minimo entre Validates do botao voltar, s. Padrao ${DEFAULT-VALUE}.")
    public double voltarMinSeg = 1;

    @Option(names = "--s3-back-max-sec", defaultValue = "3",
            description = "S3: intervalo maximo entre Validates do botao voltar, s. Padrao ${DEFAULT-VALUE}.")
    public double voltarMaxSeg = 3;

    @Option(names = "--s3-visit-pause-sec", defaultValue = "5",
            description = "S3: media (exponencial) da pausa entre visitas, s. Padrao ${DEFAULT-VALUE}.")
    public double pausaVisitaSeg = 5;

    @Option(names = "--s3-blocked-wait-sec", defaultValue = "10",
            description = "S3: media (exponencial) da espera antes de tentar Login de novo apos BLOCKED/erro, s. Padrao ${DEFAULT-VALUE}.")
    public double esperaBloqueadoSeg = 10;

    @Option(names = "--s3-admin-mean-sec", defaultValue = "15",
            description = "S3: media (exponencial) entre bloqueios administrativos, s. Padrao ${DEFAULT-VALUE}.")
    public double adminMediaSeg = 15;

    @Option(names = "--s3-admin-hold-min-sec", defaultValue = "20",
            description = "S3: tempo minimo de bloqueio administrativo antes do Unblock, s. Padrao ${DEFAULT-VALUE}.")
    public double adminHoldMinSeg = 20;

    @Option(names = "--s3-admin-hold-extra-sec", defaultValue = "10",
            description = "S3: media (exponencial) somada ao tempo minimo de bloqueio, s. Padrao ${DEFAULT-VALUE}.")
    public double adminHoldExtraSeg = 10;

    @Option(names = "--s3-admin-block-retries", defaultValue = "1",
            description = "S3: retentativas do Block administrativo com erro. Padrao ${DEFAULT-VALUE}.")
    public int adminRetentativasBlock = 1;

    @Option(names = "--s3-l-spacing-ms", defaultValue = "1000",
            description = "S3: espacamento entre IncrementFailure do pool L, ms. Padrao ${DEFAULT-VALUE}.")
    public double lEspacamentoMs = 1000;

    @Option(names = "--s3-l-reset-prob", defaultValue = "0.5",
            description = "S3: probabilidade de o ciclo de L acabar em ResetFailures antes do limite. Padrao ${DEFAULT-VALUE}.")
    public double lProbReset = 0.5;

    @Option(names = "--s3-l-support-delay-sec", defaultValue = "60",
            description = "S3: atraso do Unblock de suporte apos o bloqueio por limite, s. Padrao ${DEFAULT-VALUE}.")
    public double lSuporteSeg = 60;

    @Option(names = "--s3-l-pause-sec", defaultValue = "10",
            description = "S3: media (exponencial) da pausa entre ciclos de L, s. Padrao ${DEFAULT-VALUE}.")
    public double lPausaSeg = 10;

    @Option(names = "--s3-app-retries", defaultValue = "2",
            description = "S3: retentativas de aplicacao por operacao com erro (sem retentativa infinita). Padrao ${DEFAULT-VALUE}.")
    public int appRetentativas = 2;

    @Option(names = "--s3-backoff-ms", defaultValue = "500",
            description = "S3: espera antes da 1a retentativa de aplicacao, ms; dobra a cada retentativa (500 ms, 1 s). Padrao ${DEFAULT-VALUE}.")
    public double backoffMs = 500;

    @Option(names = "--hotrod-timeout-ms", defaultValue = "2000",
            description = "S3: socket_timeout e connect_timeout do cliente Hot Rod, ms. Padrao ${DEFAULT-VALUE}.")
    public int hotrodTimeoutMs = 2000;

    @Option(names = "--hotrod-retries", defaultValue = "1",
            description = "S3: max_retries do cliente Hot Rod. Padrao ${DEFAULT-VALUE}.")
    public int hotrodRetentativas = 1;

    // ---- W-10: sonda de reuso de sessao depois do logout (replay). Desligada por padrao. ----

    @Option(names = "--s3-replay-probe",
            description = "S3 (W-10): liga a sonda de replay: cada Logout confirmado ao cliente ganha um Validate da "
                    + "sid antiga pelo servidor de aplicacao do no (k+1) mod n, entre --s3-replay-min-sec e "
                    + "--s3-replay-max-sec depois; no fim da carga, um replay tardio das sids com Logout confirmado "
                    + "na falha ou na recuperacao (por um no diferente do que confirmou). Eventos ReplayValidate; "
                    + "violacoes com origem sonda. Exige --client-mode pinned. Padrao desligada.")
    public boolean sondaReplay;

    @Option(names = "--s3-replay-min-sec", defaultValue = "1",
            description = "S3 (W-10): atraso minimo do replay imediato depois do Logout, s. Padrao ${DEFAULT-VALUE}.")
    public double sondaMinSeg = 1;

    @Option(names = "--s3-replay-max-sec", defaultValue = "3",
            description = "S3 (W-10): atraso maximo do replay imediato depois do Logout, s. Padrao ${DEFAULT-VALUE}.")
    public double sondaMaxSeg = 3;

    @Option(names = "--s3-replay-late-max", defaultValue = "200",
            description = "S3 (W-10): maximo de sids no replay tardio; acima disso, amostra semeada sem reposicao. "
                    + "Padrao ${DEFAULT-VALUE}.")
    public int sondaTardiaMax = 200;

    public static long segundosParaNs(double seg) {
        return (long) (seg * 1_000_000_000.0);
    }

    public static long msParaNs(double ms) {
        return (long) (ms * 1_000_000.0);
    }

    /** Sanidade; lanca {@link IllegalArgumentException}. */
    public void validar() {
        exigir(usuarios >= 0, "--s3-users deve ser >= 0");
        exigir(contasLimite >= 0, "--s3-limit-accounts deve ser >= 0");
        exigir(usuarios + contasLimite > 0, "S3 sem nenhuma identidade (--s3-users e --s3-limit-accounts zerados)");
        exigir(pensarMs > 0, "--s3-think-ms deve ser > 0");
        exigir(sessaoMedianaSeg > 0, "--s3-session-median-sec deve ser > 0");
        exigir(sessaoSigma >= 0, "--s3-session-sigma deve ser >= 0");
        exigir(probLogout >= 0 && probLogout <= 1, "--s3-logout-prob deve estar em [0,1]");
        exigir(lProbReset >= 0 && lProbReset <= 1, "--s3-l-reset-prob deve estar em [0,1]");
        exigir(idleSeg > 0, "--s3-idle-timeout-sec deve ser > 0");
        exigir(voltarMinSeg > 0 && voltarMaxSeg >= voltarMinSeg, "--s3-back-min-sec/--s3-back-max-sec invalidos");
        exigir(voltarJanelaSeg >= 0, "--s3-back-window-sec deve ser >= 0");
        exigir(pausaVisitaSeg > 0 && esperaBloqueadoSeg > 0 && adminMediaSeg > 0 && lPausaSeg > 0,
                "medias exponenciais do S3 devem ser > 0");
        exigir(adminHoldMinSeg >= 0 && adminHoldExtraSeg >= 0 && lSuporteSeg >= 0 && lEspacamentoMs > 0,
                "tempos do ator administrativo/pool L invalidos");
        exigir(appRetentativas >= 0 && adminRetentativasBlock >= 0 && hotrodRetentativas >= 0,
                "retentativas devem ser >= 0");
        exigir(backoffMs >= 0, "--s3-backoff-ms deve ser >= 0");
        exigir(hotrodTimeoutMs > 0, "--hotrod-timeout-ms deve ser > 0");
        exigir(noAdmin != null && !noAdmin.isBlank(), "--admin-node nao pode ser vazio");
        if (sondaReplay) {
            exigir(sondaMinSeg > 0 && sondaMaxSeg >= sondaMinSeg,
                    "--s3-replay-min-sec deve ser > 0 e --s3-replay-max-sec >= --s3-replay-min-sec");
            exigir(sondaTardiaMax >= 0, "--s3-replay-late-max deve ser >= 0");
            exigir(modoCliente == ModoCliente.pinned,
                    "--s3-replay-probe exige --client-mode pinned (o replay e feito por outro servidor de aplicacao, "
                            + "um por no; no modo topology o cliente escolhe o no)");
        }
    }

    private static void exigir(boolean ok, String msg) {
        if (!ok) {
            throw new IllegalArgumentException(msg);
        }
    }
}
