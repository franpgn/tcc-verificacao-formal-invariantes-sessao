package br.unipampa.tcc.session;

import java.util.Random;

/**
 * B-33 (E2): usuario virtual do pool U. Ciclo: {@code Login}; laco de "paginas" (tempo de pensar
 * exponencial, {@code Validate}); duracao de sessao log-normal; ao final, {@code Logout}
 * (probabilidade {@code probLogout}) ou abandono silencioso. Depois de um {@code Logout}
 * confirmado, o "botao voltar" repete {@code Validate} da mesma sid a cada 1 a 3 s por 10 s.
 *
 * <p>Defeitos de aplicacao real, de proposito: a sessao abandonada fica {@code VALID} no grid
 * (o dominio nao tem expiracao; o timeout de 30 s e so do cliente, que na proxima visita faz
 * novo {@code Login}); um {@code Logout} que falha apos as retentativas deixa a sessao orfa; nada
 * e reconciliado. Recebe apenas {@code Login/Validate/Logout} (pool U nunca recebe
 * {@code IncrementFailure}).</p>
 */
public final class UsuarioVirtual implements AtorS3 {

    enum Estado { LOGIN, PAGINA, LOGOUT, VOLTAR }

    private final String id;
    private final String identidade;
    private final SessionOps ops;
    private final ContextoS3 ctx;
    private final Random rng;

    private Estado estado = Estado.LOGIN;
    private long proximo;
    private String sid;
    private long ultimaAtividadeNs;
    private long fimSessaoNs;
    private String sidLogout;
    private long fimVoltarNs;
    /** W-10: sonda de replay ({@code null} = desligada, o comportamento de antes). */
    private final SondaReplay sonda;
    /** W-10: indice do no do servidor de aplicacao deste usuario (o {@code k} do replay em k+1). */
    private final int no;
    /** W-10: gerador proprio do atraso do replay (so com a sonda ligada); o {@link #rng} nao e consumido. */
    private final Random rngSonda;

    public UsuarioVirtual(int indice, SessionOps ops, ContextoS3 ctx, long inicioNs) {
        this(indice, ops, ctx, inicioNs, null, 0);
    }

    /**
     * W-10: como o anterior, com a sonda de replay e o indice {@code no} do servidor de aplicacao do usuario.
     * Com {@code sonda} nula e identico ao construtor sem sonda.
     */
    UsuarioVirtual(int indice, SessionOps ops, ContextoS3 ctx, long inicioNs, SondaReplay sonda, int no) {
        this.identidade = ContextoS3.identidadeU(indice);
        this.id = identidade;
        this.ops = ops;
        this.ctx = ctx;
        this.sonda = sonda;
        this.no = no;
        this.rngSonda = sonda != null ? ctx.rngSonda(indice) : null;
        this.rng = ctx.rng("U", indice);
        // chegadas escalonadas: nem todos os usuarios entram no mesmo instante
        this.proximo = inicioNs + (long) (rng.nextDouble() * S3Parametros.segundosParaNs(ctx.p.pausaVisitaSeg));
    }

    @Override public String id() { return id; }

    @Override public long proximoNs() { return proximo; }

    String identidade() { return identidade; }

    Estado estado() { return estado; }

    String sidAtual() { return sid; }

    private long pensarNs() {
        return ContextoS3.exponencialNs(rng, S3Parametros.msParaNs(ctx.p.pensarMs));
    }

    private long pausaVisitaNs() {
        return ContextoS3.exponencialNs(rng, S3Parametros.segundosParaNs(ctx.p.pausaVisitaSeg));
    }

    @Override
    public void passo() {
        long agora = ctx.relogio.agoraNs();
        switch (estado) {
            case LOGIN -> login();
            case PAGINA -> pagina(agora);
            case LOGOUT -> logout();
            case VOLTAR -> voltar(agora);
        }
    }

    private void login() {
        OpResult r = ctx.app.executar(id, SessionOps.OP_LOGIN, identidade, ctx.p.appRetentativas,
                () -> ops.login(identidade, "cred"));
        long t = ctx.relogio.agoraNs();
        if (r.code() == OpResult.Code.OK_LOGIN) {
            sid = r.sessionId();
            ultimaAtividadeNs = t;
            fimSessaoNs = t + ContextoS3.logNormalNs(rng,
                    S3Parametros.segundosParaNs(ctx.p.sessaoMedianaSeg), ctx.p.sessaoSigma);
            estado = Estado.PAGINA;
            ctx.online.add(identidade);
            proximo = t + pensarNs();
        } else {
            // BLOCKED ou falha percebida: o usuario tenta de novo mais tarde (nao e retentativa
            // do harness: e o comportamento de quem volta a pagina de login)
            estado = Estado.LOGIN;
            proximo = t + ContextoS3.exponencialNs(rng, S3Parametros.segundosParaNs(ctx.p.esperaBloqueadoSeg));
        }
    }

    private void pagina(long agora) {
        if (agora - ultimaAtividadeNs > S3Parametros.segundosParaNs(ctx.p.idleSeg)) {
            // timeout de sessao no cliente: a sid antiga fica VALID no grid
            ctx.registro.contar("evento|SessaoExpiradaNoCliente");
            ctx.registro.contar("evento|SessaoOrfa");
            sid = null;
            estado = Estado.LOGIN;
            proximo = agora;
            return;
        }
        if (agora >= fimSessaoNs) {
            if (rng.nextDouble() < ctx.p.probLogout) {
                estado = Estado.LOGOUT;
                proximo = agora;
            } else {
                // abandono em silencio: volta depois do timeout de sessao, com sessao orfa
                ctx.registro.contar("evento|AbandonoSilencioso");
                ctx.registro.contar("evento|SessaoOrfa");
                sid = null;
                estado = Estado.LOGIN;
                proximo = agora + S3Parametros.segundosParaNs(ctx.p.idleSeg) + pausaVisitaNs();
            }
            return;
        }
        final String s = sid;
        OpResult r = ctx.app.executar(id, SessionOps.OP_VALIDATE, s, ctx.p.appRetentativas,
                () -> ops.validate(s));
        long t = ctx.relogio.agoraNs();
        ultimaAtividadeNs = t;
        boolean encerradaNoServidor = r.code() == OpResult.Code.NOT_FOUND
                || (r.code() == OpResult.Code.OK_VALIDATE && r.estado() == SessionState.Estado.INVALID);
        if (encerradaNoServidor) {
            ctx.registro.contar("evento|SessaoEncerradaNoServidor");
            ctx.online.remove(identidade);
            sid = null;
            estado = Estado.LOGIN;
        }
        proximo = t + pensarNs();
    }

    private void logout() {
        final String s = sid;
        OpResult r = ctx.app.executar(id, SessionOps.OP_LOGOUT, s, ctx.p.appRetentativas,
                () -> ops.logout(s));
        long t = ctx.relogio.agoraNs();
        if (!AplicacaoS3.ehErro(r)) {
            if (sonda != null) {
                // W-10: logout confirmado ao cliente; o replay da sid antiga vai a outro no
                sonda.logoutConfirmado(s, no, t, rngSonda);
            }
            ctx.online.remove(identidade);
            sidLogout = s;
            sid = null;
            estado = Estado.VOLTAR;
            fimVoltarNs = t + S3Parametros.segundosParaNs(ctx.p.voltarJanelaSeg);
            proximo = t + proximoVoltarNs();
        } else {
            // Logout falhou depois das retentativas: o usuario fecha a aba; sessao orfa
            ctx.registro.contar("evento|LogoutFalhouSessaoOrfa");
            ctx.registro.contar("evento|SessaoOrfa");
            sid = null;
            estado = Estado.LOGIN;
            proximo = t + pausaVisitaNs();
        }
    }

    private long proximoVoltarNs() {
        return ContextoS3.uniformeNs(rng, S3Parametros.segundosParaNs(ctx.p.voltarMinSeg),
                S3Parametros.segundosParaNs(ctx.p.voltarMaxSeg));
    }

    private void voltar(long agora) {
        if (agora >= fimVoltarNs) {
            estado = Estado.LOGIN;
            proximo = agora + pausaVisitaNs();
            return;
        }
        final String s = sidLogout;
        ctx.app.executar(id, SessionOps.OP_VALIDATE, s, ctx.p.appRetentativas, () -> ops.validate(s));
        proximo = ctx.relogio.agoraNs() + proximoVoltarNs();
    }
}
