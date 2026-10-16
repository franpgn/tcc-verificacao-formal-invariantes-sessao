package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.exceptions.HotRodTimeoutException;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (Q-9): distingue recusa por indisponibilidade (cache DEGRADED sob
 * DENY_READ_WRITES) de falha de transporte (timeout, conexao). A funcao
 * testada e a unica que classifica; as sete operacoes de SessionOps a usam.
 */
class ClassificadorDeErroTest {

    @Test
    @DisplayName("AvailabilityException vinda do servidor e indisponibilidade")
    void availabilityExceptionEhIndisponivel() {
        RuntimeException e = new HotRodClientException(
                "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: "
                        + "Key 'x' is not available. Not all owners are in this partition");
        assertTrue(ClassificadorDeErro.indisponivel(e));
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE,
                ClassificadorDeErro.classificar(e).code());
    }

    @Test
    @DisplayName("mensagem de cache DEGRADED na cadeia de causas e indisponibilidade")
    void causaAninhadaDegradedEhIndisponivel() {
        RuntimeException e = new RuntimeException("falha",
                new HotRodClientException("Cache 'sessions' is in DEGRADED mode"));
        assertTrue(ClassificadorDeErro.indisponivel(e));
    }

    @Test
    @DisplayName("TransportException e timeout sao ERROR_TRANSPORT")
    void transporteContinuaTransporte() {
        assertEquals(OpResult.Code.ERROR_TRANSPORT,
                ClassificadorDeErro.classificar(new TransportException("Connection refused", (java.net.SocketAddress) null)).code());
        assertEquals(OpResult.Code.ERROR_TRANSPORT,
                ClassificadorDeErro.classificar(new HotRodTimeoutException("timeout")).code());
        assertEquals(OpResult.Code.ERROR_TRANSPORT,
                ClassificadorDeErro.classificar(
                        new UncheckedIOException(new IOException("reset"))).code());
    }

    @Test
    @DisplayName("TransportException cuja mensagem cita 'not available' nao vira indisponibilidade")
    void mensagemEmTransporteNaoPromoveParaIndisponivel() {
        RuntimeException e = new TransportException("server not available: connect timed out", (java.net.SocketAddress) null);
        assertFalse(ClassificadorDeErro.indisponivel(e));
    }

    @Test
    @DisplayName("excecao desconhecida e ERROR_TRANSPORT (comportamento anterior)")
    void desconhecidaEhTransporte() {
        assertEquals(OpResult.Code.ERROR_TRANSPORT,
                ClassificadorDeErro.classificar(new IllegalStateException("x")).code());
        assertEquals(OpResult.Code.ERROR_TRANSPORT,
                ClassificadorDeErro.classificar(new RuntimeException((String) null)).code());
    }

    @Test
    @DisplayName("cadeia de causas ciclica nao entra em laco")
    void causaCiclicaNaoTrava() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertFalse(ClassificadorDeErro.indisponivel(a));
    }
}
