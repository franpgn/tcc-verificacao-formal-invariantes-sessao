package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 1a): block() e unblock() passam a ter try/catch e emitir,
 * como as outras cinco operacoes. Sem cluster: caches em memoria (FakeCaches).
 */
class SessionOpsBlockTest {

    private FakeCaches.Cache<SessionState> sessoes;
    private FakeCaches.Cache<IdentityState> identidades;
    private EventCsvWriter eventos;
    private SessionOps ops;

    private static TransportException transporte(String m) {
        return new TransportException(m, (SocketAddress) null);
    }

    @BeforeEach
    void preparar() {
        sessoes = new FakeCaches.Cache<>();
        identidades = new FakeCaches.Cache<>();
        eventos = new EventCsvWriter(WarmupPolicy.always());
        ops = new SessionOps(sessoes.remoto(), identidades.remoto(),
                new LatencyRegistry(), new InvariantAuditor(), eventos,
                EpochClock.capturarAgora(), "127.0.0.1:11222");
    }

    private void semear(String sid, String identidade) {
        sessoes.dados.put(sid, SessionState.criar(sid, identidade, Instant.now()));
    }

    /** Linhas do CSV (sem cabecalho) gravado a partir do escritor de eventos. */
    private List<String> linhasCsv() throws Exception {
        Path tmp = Files.createTempFile("ev", ".csv");
        try {
            eventos.flush(tmp);
            List<String> l = Files.readAllLines(tmp);
            return l.subList(1, l.size());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("block() bem-sucedido invalida as sessoes e emite evento Block/NONE")
    void blockEmiteEvento() throws Exception {
        semear("s1", "u1");
        semear("s2", "u2");
        OpResult r = ops.block("u1");
        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(IdentityState.Status.BLOCKED, identidades.dados.get("u1").status());
        assertEquals(SessionState.Estado.INVALID, sessoes.dados.get("s1").estado());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, sessoes.dados.get("s1").motivo());
        assertEquals(SessionState.Estado.VALID, sessoes.dados.get("s2").estado());
        List<String> l = linhasCsv();
        assertEquals(1, l.size());
        String[] c = l.get(0).split(",");
        assertEquals(SessionOps.OP_BLOCK, c[1]);
        assertEquals("NONE", c[5]);
        assertEquals("u1", c[6]);
    }

    @Test
    @DisplayName("block() com excecao devolve erro, nao propaga e emite Block/ERROR_TRANSPORT")
    void blockComExcecaoNaoDerrubaAThread() throws Exception {
        identidades.antes = (op, k) -> {
            if (op.equals("put")) throw transporte("reset");
        };
        OpResult r = assertDoesNotThrow(() -> ops.block("u1"));
        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        List<String> l = linhasCsv();
        assertEquals(1, l.size());
        assertTrue(l.get(0).contains(",Block,"));
        assertTrue(l.get(0).contains(",ERROR_TRANSPORT,"));
    }

    @Test
    @DisplayName("block() recusado por indisponibilidade emite ERROR_UNAVAILABLE")
    void blockIndisponivel() throws Exception {
        identidades.antes = (op, k) -> {
            if (op.equals("get")) throw new HotRodClientException(
                    "AvailabilityException: ISPN000306: Key is not available");
        };
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ops.block("u1").code());
        assertTrue(linhasCsv().get(0).contains(",ERROR_UNAVAILABLE,"));
    }

    @Test
    @DisplayName("cascata interrompida: resultado e erro e a cascata parcial permanece (nao e reparada)")
    void cascataParcialFicaRegistradaComoErro() throws Exception {
        semear("s1", "u1");
        semear("s2", "u1");
        semear("s3", "u1");
        int[] putsSessao = {0};
        sessoes.antes = (op, k) -> {
            if (op.equals("put") && ++putsSessao[0] == 2) {
                throw transporte("corte");
            }
        };
        OpResult r = ops.block("u1");
        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        // identidade ficou BLOCKED (o put dela ocorreu antes da cascata)
        assertEquals(IdentityState.Status.BLOCKED, identidades.dados.get("u1").status());
        assertEquals(SessionState.Estado.INVALID, sessoes.dados.get("s1").estado());
        assertEquals(SessionState.Estado.VALID, sessoes.dados.get("s2").estado());
        assertEquals(SessionState.Estado.VALID, sessoes.dados.get("s3").estado());
        assertTrue(linhasCsv().get(0).contains(",ERROR_TRANSPORT,"));
    }

    @Test
    @DisplayName("unblock() emite Unblock/NONE; identidade ausente e NONE (no-op)")
    void unblockEmiteEvento() throws Exception {
        identidades.dados.put("u1", IdentityState.criar("u1", Instant.now()).bloquear(Instant.now()));
        assertEquals(OpResult.Code.OK_NOOP, ops.unblock("u1").code());
        assertEquals(IdentityState.Status.OPEN, identidades.dados.get("u1").status());
        assertEquals(OpResult.Code.NOT_FOUND, ops.unblock("ausente").code());
        List<String> l = linhasCsv();
        assertEquals(2, l.size());
        assertTrue(l.get(0).contains(",Unblock,") && l.get(0).contains(",NONE,"));
        assertTrue(l.get(1).contains(",Unblock,") && l.get(1).contains(",NONE,"));
    }

    @Test
    @DisplayName("unblock() com excecao devolve erro e emite Unblock/ERROR_*")
    void unblockComExcecao() throws Exception {
        identidades.antes = (op, k) -> {
            throw transporte("timeout");
        };
        OpResult r = assertDoesNotThrow(() -> ops.unblock("u1"));
        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        assertTrue(linhasCsv().get(0).contains(",Unblock,"));
    }

    @Test
    @DisplayName("as demais operacoes usam o classificador (indisponivel -> ERROR_UNAVAILABLE)")
    void demaisOperacoesClassificam() {
        sessoes.antes = (op, k) -> {
            throw new HotRodClientException("Cache 'sessions' is DEGRADED");
        };
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ops.validate("s1").code());
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ops.logout("s1").code());
        identidades.antes = (op, k) -> {
            throw new HotRodClientException("Cache 'counters' is DEGRADED");
        };
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ops.login("u1", "c").code());
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ops.incrementFailure("u1").code());
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ops.resetFailures("u1").code());
    }
}
