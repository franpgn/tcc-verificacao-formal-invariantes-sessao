package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.9, regra B: dupla checagem no {@code login}, em todos os cenarios. Depois do {@code put} da
 * sessao nova, o {@code login} rele a identidade. OPEN ou ausente: OK_LOGIN, como antes. BLOCKED: a
 * propria sessao e invalidada com ADMIN_BLOCK pelo mesmo caminho da cascata, conta
 * {@code login_revertido_por_bloqueio}, latencia {@code login_blocked}, evento Login/BLOCKED. O W8
 * reescrito ({@link ToctouLoginBlockTest}, Z9) cobre o roteiro do TOCTOU com o {@code block}.
 */
class LoginDuplaChecagemTest {

    private static final String U = "u-dc";
    private static final Instant T = Instant.parse("2026-10-23T00:00:00Z");

    private static TransportException transporte(String m) {
        return new TransportException(m, (SocketAddress) null);
    }

    private static List<String> linhasCsv(EventCsvWriter eventos) throws Exception {
        Path tmp = Files.createTempFile("ev-dc", ".csv");
        try {
            eventos.flush(tmp);
            List<String> l = Files.readAllLines(tmp);
            return l.subList(1, l.size());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("Z10: releitura OPEN => OK_LOGIN; 2 get de identidade por Login OK e 0 escrita condicional")
    void loginNormal() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ids.dados.put(U, IdentityState.criar(U, T));
        int[] gets = {0};
        ids.antes = (op, k) -> {
            if (op.equals("get") && U.equals(k)) {
                gets[0]++;
            }
        };
        LatencyRegistry lat = new LatencyRegistry();
        SessionOps o = new SessionOps(s, ids, lat, new InvariantAuditor(), null, null, "fake", null,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

        OpResult r = o.login(U, "cred");

        assertEquals(OpResult.Code.OK_LOGIN, r.code());
        assertEquals(2, gets[0], "leitura inicial e releitura depois do put");
        assertEquals(0, s.tentativasCondicionais.get());
        assertEquals(0, s.leiturasVersionadas.get());
        assertEquals(SessionState.Estado.VALID, s.valor(r.sessionId()).estado());
        assertEquals(0, o.loginRevertidoPorBloqueio());
        Map<String, Long> c = LogoutCondicionalTest.contagens(lat);
        assertEquals(1, LogoutCondicionalTest.conta(c, "login_ok"));
        assertEquals(0, LogoutCondicionalTest.conta(c, "login_blocked"));

        // identidade ausente: criada OPEN, releitura OPEN
        OpResult r2 = o.login("u-nova", "cred");
        assertEquals(OpResult.Code.OK_LOGIN, r2.code());
    }

    @Test
    @DisplayName("Z11: revertido sem armazem versionado => INVALID(ADMIN_BLOCK), confirmacao depois do put, login_blocked, CSV BLOCKED")
    void revertidoSemVersionado() throws Exception {
        FakeCaches.Cache<SessionState> s = new FakeCaches.Cache<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ids.dados.put(U, IdentityState.criar(U, T));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        List<String> mapaNoPut = new ArrayList<>();
        int[] puts = {0};
        s.antes = (op, k) -> {
            if (op.equals("put")) {
                puts[0]++;
                if (puts[0] == 1) {
                    // o block grava a identidade entre o put da sessao e a releitura
                    ids.dados.put(U, ids.dados.get(U).bloquear(T));
                } else {
                    mapaNoPut.add(k + "=" + mapa.get(k));
                }
            }
        };
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        LatencyRegistry lat = new LatencyRegistry();
        SessionOps o = new SessionOps(s, ids, lat, new InvariantAuditor(), eventos, EpochClock.capturarAgora(),
                "fake", null, mapa, new ConcurrentHashMap<>());

        OpResult r = o.login(U, "cred");

        assertEquals(OpResult.Code.BLOCKED, r.code());
        assertEquals(1, s.dados.size());
        String sid = s.dados.keySet().iterator().next();
        assertEquals(SessionState.Estado.INVALID, s.dados.get(sid).estado());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, s.dados.get(sid).motivo());
        assertEquals(List.of(sid + "=null"), mapaNoPut, "a confirmacao so entra no mapa depois do put");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, mapa.get(sid));
        assertEquals(1, o.loginRevertidoPorBloqueio());
        Map<String, Long> c = LogoutCondicionalTest.contagens(lat);
        assertEquals(1, LogoutCondicionalTest.conta(c, "login_blocked"));
        assertEquals(0, LogoutCondicionalTest.conta(c, "login_ok"));
        List<String> l = linhasCsv(eventos);
        assertEquals(1, l.size());
        String[] col = l.get(0).split(",");
        assertEquals(SessionOps.OP_LOGIN, col[1]);
        assertEquals("BLOCKED", col[5]);
        assertEquals(U, col[6]);
    }

    @Test
    @DisplayName("Z12: revertido com armazem versionado => a auto-invalidacao e escrita condicional")
    void revertidoComVersionado() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ids.dados.put(U, IdentityState.criar(U, T));
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && op.equals("put")) {
                feito[0] = true;
                ids.dados.put(U, ids.dados.get(U).bloquear(T));
            }
        };
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        SessionOps o = new SessionOps(s, ids, new LatencyRegistry(), a, null, null, "fake", null, mapa,
                new ConcurrentHashMap<>());

        OpResult r = o.login(U, "cred");

        assertEquals(OpResult.Code.BLOCKED, r.code());
        String sid = s.copia().keySet().iterator().next();
        assertEquals(1, s.tentativasCondicionais.get(), "uma escrita condicional");
        assertEquals(1, s.escritasCondicionaisAplicadas.get());
        assertEquals(1, s.leiturasVersionadas.get());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, s.valor(sid).motivo());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, mapa.get(sid));
        assertEquals(0, a.total());
        assertEquals(1, o.loginRevertidoPorBloqueio());
    }

    @Test
    @DisplayName("Z12b: conflito esgotado na auto-invalidacao => ERROR_TRANSPORT, conflito_esgotado + 1, login_err")
    void conflitoEsgotadoNaAutoInvalidacao() throws Exception {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.sempreConflita = true;
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ids.dados.put(U, IdentityState.criar(U, T));
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && op.equals("put")) {
                feito[0] = true;
                ids.dados.put(U, ids.dados.get(U).bloquear(T));
            }
        };
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        LatencyRegistry lat = new LatencyRegistry();
        SessionOps o = new SessionOps(s, ids, lat, new InvariantAuditor(), eventos, EpochClock.capturarAgora(),
                "fake", null, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

        OpResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> o.login(U, "cred"));

        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        assertEquals(1, o.conflitoEsgotado());
        assertEquals(SessionOps.TENTATIVAS_CONDICIONAIS, s.tentativasCondicionais.get());
        assertEquals(0, o.loginRevertidoPorBloqueio(), "so conta o revertido que terminou");
        Map<String, Long> c = LogoutCondicionalTest.contagens(lat);
        assertEquals(1, LogoutCondicionalTest.conta(c, "login_err"));
        assertTrue(linhasCsv(eventos).get(0).contains(",ERROR_TRANSPORT,"));
    }

    @Test
    @DisplayName("Z13: releitura que lanca => classificador, login_err, sid fora do rastro, sessao VALID orfa no fake")
    void releituraQueLanca() throws Exception {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ids.dados.put(U, IdentityState.criar(U, T));
        int[] gets = {0};
        ids.antes = (op, k) -> {
            if (op.equals("get") && ++gets[0] == 2) {
                throw transporte("timeout na releitura");
            }
        };
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        LatencyRegistry lat = new LatencyRegistry();
        RastroCliente rastro = new RastroCliente();
        SessionOps o = new SessionOps(s, ids, lat, new InvariantAuditor(), eventos, EpochClock.capturarAgora(),
                "fake", rastro, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

        OpResult r = o.login(U, "cred");

        assertEquals(ClassificadorDeErro.classificar(transporte("x")).code(), r.code());
        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        Map<String, Long> c = LogoutCondicionalTest.contagens(lat);
        assertEquals(1, LogoutCondicionalTest.conta(c, "login_err"));
        assertEquals(0, LogoutCondicionalTest.conta(c, "login_ok"));
        assertEquals(1, s.copia().size());
        String sid = s.copia().keySet().iterator().next();
        assertEquals(SessionState.Estado.VALID, s.valor(sid).estado(), "sessao orfa: VALID e nunca devolvida");
        assertFalse(rastro.identidadeDe(sid).isPresent(), "o rastro nao mapeia o sid");
        assertTrue(linhasCsv(eventos).get(0).contains(",Login,"));
    }
}
