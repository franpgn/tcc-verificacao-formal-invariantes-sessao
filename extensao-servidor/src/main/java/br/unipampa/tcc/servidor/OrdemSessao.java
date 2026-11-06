package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;

import java.util.Comparator;
import java.util.Objects;

/**
 * A1: ordem de conteudo de {@link SessionState} usada pela politica de fusao. Os postos sao os do modelo TLA+ do
 * desempate T2 (B-45, {@code fix/b45-desempate-ts}, {@code spec/SessionStore.tla:233-235}):
 *
 * <ul>
 *   <li>estado: NONE 0 (ausente) &lt; VALID 1 &lt; INVALID 2;</li>
 *   <li>motivo: NONE 0 &lt; LOGOUT 1 &lt; EXPIRADA 2 &lt; ADMIN_BLOCK 3.</li>
 * </ul>
 *
 * <p>Diferenca em relacao ao T2: aqui o conteudo vem antes da versao, na ordem (estado, motivo, versao,
 * atualizadaEm). No programa de carga as duas ordens coincidem (so existem {@code criar}, versao 1, e
 * {@code invalidar}, versao + 1); com o conteudo primeiro, a invalidacao e absorvente mesmo com versao errada. O maximo
 * numa ordem total e associativo, comutativo e idempotente (semirreticulado): fundir em qualquer ordem da o mesmo
 * resultado, e um valor maior nunca e desfeito.</p>
 */
final class OrdemSessao {

    /** (posto do estado, posto do motivo, versao, atualizadaEm), crescente. */
    static final Comparator<SessionState> CONTEUDO = Comparator
            .comparingInt((SessionState s) -> rankEstado(s.estado()))
            .thenComparingInt(s -> rankMotivo(s.motivo()))
            .thenComparingLong(SessionState::versao)
            .thenComparing(SessionState::atualizadaEm);

    private OrdemSessao() { }

    /** {@code RankEstado} do modelo; o 0 (NONE) e a chave ausente, que a politica trata fora daqui. */
    static int rankEstado(SessionState.Estado e) {
        switch (e) {
            case VALID: return 1;
            case INVALID: return 2;
            default: throw new IllegalArgumentException("estado sem posto: " + e);
        }
    }

    /** {@code RankMotivo} do modelo. */
    static int rankMotivo(SessionState.Motivo m) {
        switch (m) {
            case NONE: return 0;
            case LOGOUT: return 1;
            case EXPIRADA: return 2;
            case ADMIN_BLOCK: return 3;
            default: throw new IllegalArgumentException("motivo sem posto: " + m);
        }
    }

    /** Todos os campos iguais ({@link SessionState} nao define {@code equals}). */
    static boolean mesmoConteudo(SessionState a, SessionState b) {
        return Objects.equals(a.id(), b.id()) && Objects.equals(a.identidade(), b.identidade())
                && a.estado() == b.estado() && a.motivo() == b.motivo() && a.versao() == b.versao()
                && Objects.equals(a.criadaEm(), b.criadaEm()) && Objects.equals(a.atualizadaEm(), b.atualizadaEm());
    }

    /** Texto curto para o log: {@code ESTADO/MOTIVO/vN@instante}. */
    static String resumo(SessionState s) {
        return s.estado() + "/" + s.motivo() + "/v" + s.versao() + "@" + s.atualizadaEm();
    }
}
