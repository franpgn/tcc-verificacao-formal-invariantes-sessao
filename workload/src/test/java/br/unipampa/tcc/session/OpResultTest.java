package br.unipampa.tcc.session;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o mapeamento {@link OpResult.Code} -> {@code return_code}
 * gravado no CSV de eventos (T1 do bloco de eventos), conforme a tabela
 * aprovada pelo Gestor.
 */
class OpResultTest {

    /** Conjunto de sucessos reconhecido por analysis/percentis.py. */
    private static final Set<String> SUCESSOS = Set.of(
            "OK", "VALID", "INVALID", "COUNTED", "ALREADY_INVALID",
            "BLOCKED", "ALREADY_BLOCKED", "NOT_BLOCKED", "NONE", "NOT_FOUND");

    @Test
    void okLoginMapeiaParaOk() {
        assertEquals("OK", OpResult.okLogin("s1").returnCode());
    }

    @Test
    void okValidateMapeiaParaValid() {
        assertEquals("VALID",
                OpResult.okValidate(SessionState.Estado.VALID).returnCode());
    }

    @Test
    void okStatusMapeiaParaCounted() {
        assertEquals("COUNTED",
                OpResult.okStatus(IdentityState.Status.OPEN).returnCode());
    }

    @Test
    void okNoopMapeiaParaNone() {
        assertEquals("NONE", OpResult.okNoop().returnCode());
    }

    @Test
    void notFoundMapeiaParaNone() {
        assertEquals("NOT_FOUND", OpResult.notFound().returnCode());
    }

    @Test
    void blockedMapeiaParaBlocked() {
        assertEquals("BLOCKED", OpResult.blocked().returnCode());
    }

    @Test
    void errorTransportMapeiaParaErrorTransport() {
        assertEquals("ERROR_TRANSPORT", OpResult.errorTransport().returnCode());
    }

    @Test
    void errorUnavailableMapeiaParaErrorUnavailable() {
        assertEquals("ERROR_UNAVAILABLE", OpResult.errorUnavailable().returnCode());
        assertTrue(!OpResult.errorUnavailable().isOk());
    }

    /**
     * Todo código exceto os dois de erro (ERROR_TRANSPORT e, desde B-33/Q-9,
     * ERROR_UNAVAILABLE) deve cair no conjunto de sucessos do pipeline, de
     * modo que taxa_erro reflita apenas falhas. Cobre todos os valores de
     * {@link OpResult.Code}.
     *
     * <p>B-33: a premissa original ("apenas ERROR_TRANSPORT e erro") foi
     * ampliada, nao enfraquecida: o novo codigo ERROR_UNAVAILABLE tambem nao
     * pode ser sucesso.</p>
     */
    @Test
    void apenasCodigosDeErroSaoContabilizadosComoErro() {
        for (OpResult.Code c : EnumSet.allOf(OpResult.Code.class)) {
            OpResult r = construir(c);
            String rc = r.returnCode();
            if (c == OpResult.Code.ERROR_TRANSPORT || c == OpResult.Code.ERROR_UNAVAILABLE) {
                assertEquals(c.name(), rc);
                assertTrue(!SUCESSOS.contains(rc),
                        c + " nao pode ser sucesso");
            } else {
                assertTrue(SUCESSOS.contains(rc),
                        "Codigo " + c + " -> " + rc + " deveria ser sucesso");
            }
        }
    }

    private static OpResult construir(OpResult.Code c) {
        switch (c) {
            case OK_LOGIN:    return OpResult.okLogin("s");
            case OK_VALIDATE: return OpResult.okValidate(SessionState.Estado.VALID);
            case OK_STATUS:   return OpResult.okStatus(IdentityState.Status.OPEN);
            case OK_NOOP:     return OpResult.okNoop();
            case BLOCKED:     return OpResult.blocked();
            case NOT_FOUND:   return OpResult.notFound();
            case ERROR_UNAVAILABLE: return OpResult.errorUnavailable();
            case ERROR_TRANSPORT:
            default:          return OpResult.errorTransport();
        }
    }
}
