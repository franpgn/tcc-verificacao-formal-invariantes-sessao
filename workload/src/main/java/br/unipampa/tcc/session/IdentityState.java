package br.unipampa.tcc.session;

import java.io.Serializable;
import java.time.Instant;

/**
 * Estado de uma identidade: contador de tentativas falhas e marca de bloqueio.
 */
public class IdentityState implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status { OPEN, BLOCKED }

    private final String identidade;
    private final long contadorFalhas;
    private final Status status;
    private final long versao;
    private final Instant atualizadaEm;

    public IdentityState(String identidade, long contadorFalhas, Status status, long versao, Instant atualizadaEm) {
        this.identidade = identidade;
        this.contadorFalhas = contadorFalhas;
        this.status = status;
        this.versao = versao;
        this.atualizadaEm = atualizadaEm;
    }

    public static IdentityState criar(String identidade, Instant agora) {
        return new IdentityState(identidade, 0L, Status.OPEN, 1L, agora);
    }

    public IdentityState incrementar(int limite, Instant agora) {
        long novo = contadorFalhas + 1L;
        Status s = novo >= limite ? Status.BLOCKED : status;
        return new IdentityState(identidade, novo, s, versao + 1, agora);
    }

    public IdentityState resetar(Instant agora) {
        return new IdentityState(identidade, 0L, status, versao + 1, agora);
    }

    public IdentityState bloquear(Instant agora) {
        return new IdentityState(identidade, contadorFalhas, Status.BLOCKED, versao + 1, agora);
    }

    /**
     * O7, desbloqueio: remove a marca de bloqueio e ZERA o contador de falhas.
     * Efeito exigido por Metodologia.tex (O7): "beta'(u).marca = Free,
     * beta'(u).counter = 0" -- mesmo efeito que {@code spec/SessionStore.tla},
     * ação {@code Unblock}, sempre implementou. Antes desta correção,
     * {@code desbloquear()} preservava {@code contadorFalhas}, divergindo da
     * especificação formal e da monografia (achado de validação registrado em
     * docs/registro-de-problemas.md, 2026-10-02, item 4.2 do documento de
     * validação do Senior).
     */
    public IdentityState desbloquear(Instant agora) {
        return new IdentityState(identidade, 0L, Status.OPEN, versao + 1, agora);
    }

    public String identidade() { return identidade; }
    public long contadorFalhas() { return contadorFalhas; }
    public Status status() { return status; }
    public long versao() { return versao; }
    public Instant atualizadaEm() { return atualizadaEm; }
}
