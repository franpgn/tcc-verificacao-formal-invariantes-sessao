package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * B-33 (E2): ator administrativo (console apontado a um no da maioria). A cada ~15 s
 * (exponencial) escolhe uma identidade de U com sessao ativa e faz {@code Block}; mantem por no
 * minimo 20 s (periodo em que {@code Login} deve dar {@code BLOCKED} e {@code Validate} deve ver
 * {@code INVALID} com motivo {@code ADMIN_BLOCK}) e entao {@code Unblock} (suporte). {@code Block}
 * com erro: registra tentativa incerta e retenta uma vez; se continuar falhando, o admin segue
 * em frente sem Unblock (nao sabe o que ficou feito: a cascata pode ter ficado pela metade, e isso
 * e comportamento real a observar, nao e reparado). So bloqueia identidades de U.
 */
public final class AtorAdministrativoS3 implements AtorS3 {

    enum Fase { ESPERA, SEGURANDO }

    private final SessionOps ops;
    private final ContextoS3 ctx;
    private final Random rng;

    private Fase fase = Fase.ESPERA;
    private long proximo;
    private String bloqueada;

    public AtorAdministrativoS3(SessionOps ops, ContextoS3 ctx, long inicioNs) {
        this.ops = ops;
        this.ctx = ctx;
        this.rng = ctx.rng("ADMIN", 0);
        this.proximo = inicioNs + ContextoS3.exponencialNs(rng, S3Parametros.segundosParaNs(ctx.p.adminMediaSeg));
    }

    @Override public String id() { return "admin"; }

    @Override public long proximoNs() { return proximo; }

    private long intervaloNs() {
        return ContextoS3.exponencialNs(rng, S3Parametros.segundosParaNs(ctx.p.adminMediaSeg));
    }

    @Override
    public void passo() {
        switch (fase) {
            case ESPERA -> bloquear();
            case SEGURANDO -> desbloquear();
        }
    }

    private void bloquear() {
        List<String> candidatos = new ArrayList<>(new TreeSet<>(ctx.online));
        if (candidatos.isEmpty()) {
            proximo = ctx.relogio.agoraNs() + intervaloNs();
            return;
        }
        final String alvo = candidatos.get(rng.nextInt(candidatos.size()));
        OpResult r = ctx.app.executar("admin", SessionOps.OP_BLOCK, alvo, ctx.p.adminRetentativasBlock,
                () -> ops.block(alvo));
        long t = ctx.relogio.agoraNs();
        if (!AplicacaoS3.ehErro(r)) {
            bloqueada = alvo;
            fase = Fase.SEGURANDO;
            long extra = ctx.p.adminHoldExtraSeg > 0
                    ? ContextoS3.exponencialNs(rng, S3Parametros.segundosParaNs(ctx.p.adminHoldExtraSeg)) : 0L;
            proximo = t + S3Parametros.segundosParaNs(ctx.p.adminHoldMinSeg) + extra;
        } else {
            ctx.registro.contar("evento|BlockAdminSemConfirmacao");
            proximo = t + intervaloNs();
        }
    }

    private void desbloquear() {
        final String alvo = bloqueada;
        ctx.app.executar("admin", SessionOps.OP_UNBLOCK, alvo, ctx.p.appRetentativas, () -> ops.unblock(alvo));
        bloqueada = null;
        fase = Fase.ESPERA;
        proximo = ctx.relogio.agoraNs() + intervaloNs();
    }
}
