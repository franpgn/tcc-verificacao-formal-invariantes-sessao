package br.unipampa.tcc.session;

import java.util.Random;

/**
 * B-33 (E2): conta do pool L (senha errada). Ciclo: cinco {@code IncrementFailure} espacados de
 * 1 s seguidos de {@code Login} (esperado {@code BLOCKED}); em metade dos casos, antes do limite,
 * {@code ResetFailures} (o usuario acertou a senha). Apos 60 s, {@code Unblock} de suporte (pelo
 * console administrativo). Recebe apenas {@code IncrementFailure/ResetFailures/Login} e o
 * {@code Unblock} de suporte; <b>nunca</b> {@code Block} (pools U e L disjuntos, Q-10): um
 * {@code BLOCKED} em L so pode vir do limite.
 */
public final class ContaLimiteS3 implements AtorS3 {

    enum Fase { INICIO, INCREMENTO, RESET, LOGIN, SUPORTE }

    private final String id;
    private final String identidade;
    private final SessionOps ops;
    private final SessionOps suporte;
    private final ContextoS3 ctx;
    private final Random rng;

    private Fase fase = Fase.INICIO;
    private long proximo;
    private boolean caminhoReset;
    private int alvoIncrementos;
    private int feitos;

    public ContaLimiteS3(int indice, SessionOps ops, SessionOps suporte, ContextoS3 ctx, long inicioNs) {
        this.identidade = ContextoS3.identidadeL(indice);
        this.id = identidade;
        this.ops = ops;
        this.suporte = suporte;
        this.ctx = ctx;
        this.rng = ctx.rng("L", indice);
        this.proximo = inicioNs + (long) (rng.nextDouble() * S3Parametros.segundosParaNs(ctx.p.lPausaSeg));
    }

    @Override public String id() { return id; }

    @Override public long proximoNs() { return proximo; }

    String identidade() { return identidade; }

    private long espacamentoNs() {
        return S3Parametros.msParaNs(ctx.p.lEspacamentoMs);
    }

    private long pausaNs() {
        return ContextoS3.exponencialNs(rng, S3Parametros.segundosParaNs(ctx.p.lPausaSeg));
    }

    @Override
    public void passo() {
        switch (fase) {
            case INICIO -> {
                caminhoReset = rng.nextDouble() < ctx.p.lProbReset;
                alvoIncrementos = caminhoReset ? 1 + rng.nextInt(SessionOps.LIMITE_TENTATIVAS - 1)
                        : SessionOps.LIMITE_TENTATIVAS;
                feitos = 0;
                fase = Fase.INCREMENTO;
                proximo = ctx.relogio.agoraNs();
            }
            case INCREMENTO -> {
                ctx.app.executar(id, SessionOps.OP_INCREMENT_FAILURE, identidade, ctx.p.appRetentativas,
                        () -> ops.incrementFailure(identidade));
                feitos++;
                if (feitos >= alvoIncrementos) {
                    fase = caminhoReset ? Fase.RESET : Fase.LOGIN;
                }
                proximo = ctx.relogio.agoraNs() + espacamentoNs();
            }
            case RESET -> {
                ctx.app.executar(id, SessionOps.OP_RESET_FAILURES, identidade, ctx.p.appRetentativas,
                        () -> ops.resetFailures(identidade));
                fase = Fase.INICIO;
                proximo = ctx.relogio.agoraNs() + pausaNs();
            }
            case LOGIN -> {
                ctx.app.executar(id, SessionOps.OP_LOGIN, identidade, ctx.p.appRetentativas,
                        () -> ops.login(identidade, "cred"));
                fase = Fase.SUPORTE;
                proximo = ctx.relogio.agoraNs() + S3Parametros.segundosParaNs(ctx.p.lSuporteSeg);
            }
            case SUPORTE -> {
                ctx.app.executar("suporte", SessionOps.OP_UNBLOCK, identidade, ctx.p.appRetentativas,
                        () -> suporte.unblock(identidade));
                fase = Fase.INICIO;
                proximo = ctx.relogio.agoraNs() + pausaNs();
            }
        }
    }
}
