package br.unipampa.tcc.session;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Estado de uma sessão no data grid.
 * Modelo: {@code (id, identidade, estado, motivo_invalid, versao, criadaEm, atualizadaEm)}.
 * As regras do ciclo de vida implementam os invariantes I1, I2 e I4 da Frente 2
 * conforme a Seção 4.1 da monografia.
 */
public class SessionState implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Estado { VALID, INVALID }

    public enum Motivo { NONE, LOGOUT, EXPIRADA, ADMIN_BLOCK }

    private final String id;
    private final String identidade;
    private final Estado estado;
    private final Motivo motivo;
    private final long versao;
    private final Instant criadaEm;
    private final Instant atualizadaEm;

    public SessionState(String id, String identidade, Estado estado, Motivo motivo,
                        long versao, Instant criadaEm, Instant atualizadaEm) {
        this.id = Objects.requireNonNull(id);
        this.identidade = Objects.requireNonNull(identidade);
        this.estado = Objects.requireNonNull(estado);
        this.motivo = Objects.requireNonNull(motivo);
        this.versao = versao;
        this.criadaEm = Objects.requireNonNull(criadaEm);
        this.atualizadaEm = Objects.requireNonNull(atualizadaEm);
    }

    public static SessionState criar(String id, String identidade, Instant agora) {
        return new SessionState(id, identidade, Estado.VALID, Motivo.NONE, 1L, agora, agora);
    }

    public SessionState invalidar(Motivo m, Instant agora) {
        if (estado == Estado.INVALID) {
            return this;
        }
        return new SessionState(id, identidade, Estado.INVALID, m, versao + 1, criadaEm, agora);
    }

    public String id() { return id; }
    public String identidade() { return identidade; }
    public Estado estado() { return estado; }
    public Motivo motivo() { return motivo; }
    public long versao() { return versao; }
    public Instant criadaEm() { return criadaEm; }
    public Instant atualizadaEm() { return atualizadaEm; }
}
