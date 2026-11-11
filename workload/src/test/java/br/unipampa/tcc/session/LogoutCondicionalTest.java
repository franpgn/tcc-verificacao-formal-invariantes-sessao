package br.unipampa.tcc.session;

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * B-43.8 (DT-003): {@code SessionOps.logout} no caminho versionado (armazem de sessoes
 * {@link ArmazemVersionado}). Leitura com versao, escrita condicional; quem perde a versao rele e,
 * se a sessao ja esta INVALID, vira no-op ({@code invalidacaoPerdida}); com VALID persistente, no
 * maximo 3 tentativas e depois erro ({@code conflitoEsgotado}). A confirmacao no mapa so e escrita
 * pelo vencedor, depois da escrita reconhecida.
 */
class LogoutCondicionalTest {

    static final String U = "u-lc";
    static final String SID = "sid-lc";
    static final Instant T = Instant.parse("2026-10-22T00:00:00Z");

    /** Um servidor de aplicacao sobre o armazem dado, com mapa e auditor dados. */
    static SessionOps ops(Armazem<SessionState> s, ConcurrentHashMap<String, SessionState.Motivo> mapa,
                          InvariantAuditor a, LatencyRegistry lat) {
        return new SessionOps(s, new FakeCaches.Cache<>(), lat, a, null, null, "fake", null, mapa);
    }

    /** Contagem por rotulo de latencia. O {@code snapshot} drena: chamar uma vez por teste. */
    static java.util.Map<String, Long> contagens(LatencyRegistry lat) {
        java.util.Map<String, Long> m = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Histogram> e : lat.snapshot().entrySet()) {
            m.put(e.getKey(), e.getValue().getTotalCount());
        }
        return m;
    }

    static long conta(java.util.Map<String, Long> c, String rotulo) {
        return c.getOrDefault(rotulo, 0L);
    }

    static List<String> invariantes(InvariantAuditor a) {
        return a.drenar().stream().map(v -> v.invariante() + ":" + v.chave()).toList();
    }

    @Test
    @DisplayName("sem concorrencia: le com versao, grava condicional, confirma LOGOUT; logout_ok")
    void logoutSimples() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear(SID, SessionState.criar(SID, U, T));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        LatencyRegistry lat = new LatencyRegistry();
        SessionOps o = ops(s, mapa, a, lat);

        OpResult r = o.logout(SID);

        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(SessionState.Motivo.LOGOUT, s.valor(SID).motivo());
        assertEquals(SessionState.Motivo.LOGOUT, mapa.get(SID));
        assertEquals(1, s.escritasCondicionaisAplicadas.get(), "a invalidacao e escrita condicional");
        assertEquals(1, s.leiturasVersionadas.get(), "a leitura da operacao e lerComVersao");
        java.util.Map<String, Long> c = contagens(lat);
        assertEquals(1, conta(c, "logout_ok"));
        assertEquals(0, o.invalidacaoPerdida());
        assertEquals(0, o.conflitoEsgotado());
        assertEquals(0, a.total());
    }

    @Test
    @DisplayName("sessao ausente: NOT_FOUND como hoje; sessao ja INVALID: no-op sem tentativa de escrita")
    void ausenteEJaInvalida() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        LatencyRegistry lat = new LatencyRegistry();
        SessionOps o = ops(s, mapa, a, lat);

        assertEquals(OpResult.Code.NOT_FOUND, o.logout(SID).code());

        s.semear(SID, SessionState.criar(SID, U, T).invalidar(SessionState.Motivo.ADMIN_BLOCK, T));
        assertEquals(OpResult.Code.OK_NOOP, o.logout(SID).code());
        assertEquals(0, s.tentativasCondicionais.get());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, mapa.get(SID), "primeira observacao (putIfAbsent)");
        assertEquals(2, conta(contagens(lat), "logout_noop"));
        assertEquals(0, o.invalidacaoPerdida(), "no-op na primeira leitura nao e invalidacao perdida");
        assertEquals(0, a.total());
    }

    @Test
    @DisplayName("perdedor: outro logout vence entre a leitura e a escrita; rele INVALID, no-op, invalidacaoPerdida")
    void logoutPerdeParaOutroLogout() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear(SID, SessionState.criar(SID, U, T));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        LatencyRegistry latA = new LatencyRegistry();
        SessionOps perdedor = ops(s, mapa, a, latA);
        SessionOps vencedor = ops(s, mapa, a, new LatencyRegistry());
        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("substituirSeVersao")) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> vencedor.logout(SID));
            }
        };

        OpResult r = perdedor.logout(SID);

        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(1, perdedor.invalidacaoPerdida());
        assertEquals(0, vencedor.invalidacaoPerdida());
        java.util.Map<String, Long> c = contagens(latA);
        assertEquals(0, conta(c, "logout_ok"), "o perdedor nao devolve logout_ok");
        assertEquals(1, conta(c, "logout_noop"));
        assertEquals(2, s.tentativasCondicionais.get(), "uma do perdedor, uma do vencedor");
        assertEquals(1, s.escritasCondicionaisAplicadas.get());
        assertEquals(SessionState.Motivo.LOGOUT, mapa.get(SID));
        assertEquals(List.of(), invariantes(a));
    }

    @Test
    @DisplayName("W5: VALID persistente com versao sempre mudando: 3 tentativas, conflitoEsgotado, erro, nada no mapa")
    void conflitoEsgotado() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear(SID, SessionState.criar(SID, U, T));
        s.sempreConflita = true;
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        LatencyRegistry lat = new LatencyRegistry();
        SessionOps o = ops(s, mapa, a, lat);

        OpResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> o.logout(SID));

        OpResult.Code esperado = ClassificadorDeErro.classificar(
                new IllegalStateException("conflito de versao esgotado")).code();
        assertEquals(esperado, r.code());
        assertEquals(3, s.tentativasCondicionais.get(), "no maximo 3 tentativas no total");
        assertEquals(4, s.leiturasVersionadas.get(), "leitura inicial e uma releitura depois de cada falha");
        assertEquals(1, o.conflitoEsgotado());
        assertEquals(0, o.invalidacaoPerdida());
        assertFalse(mapa.containsKey(SID), "nada escrito no mapa");
        assertEquals(SessionState.Estado.VALID, s.valor(SID).estado());
        java.util.Map<String, Long> c = contagens(lat);
        assertEquals(0, conta(c, "logout_ok"));
        assertEquals(1, conta(c, "logout_noop"), "erro registrado como hoje");
        assertEquals(0, a.total());
    }

    @Test
    @DisplayName("W5: conflito resolvido na 2a tentativa (versao mudou, estado VALID): escreve e confirma")
    void conflitoUmaVez() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear(SID, SessionState.criar(SID, U, T));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        SessionOps o = ops(s, mapa, new InvariantAuditor(), new LatencyRegistry());
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && op.equals("substituirSeVersao")) {
                feito[0] = true;
                s.semear(SID, s.valor(SID)); // muda so a versao
            }
        };

        assertEquals(OpResult.Code.OK_NOOP, o.logout(SID).code());
        assertEquals(2, s.tentativasCondicionais.get());
        assertEquals(SessionState.Motivo.LOGOUT, s.valor(SID).motivo());
        assertEquals(SessionState.Motivo.LOGOUT, mapa.get(SID));
        assertEquals(0, o.conflitoEsgotado());
        assertEquals(0, o.invalidacaoPerdida());
    }

    @Test
    @DisplayName("W7 (logout): leitura velha iniciada depois da confirmacao continua dando I1 e I4")
    void controlePositivo() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        SessionOps o = ops(s, mapa, a, new LatencyRegistry());

        s.semear("s-i1", SessionState.criar("s-i1", U, T));
        mapa.put("s-i1", SessionState.Motivo.LOGOUT);
        o.logout("s-i1");

        s.semear("s-i4", SessionState.criar("s-i4", U, T).invalidar(SessionState.Motivo.LOGOUT, T));
        mapa.put("s-i4", SessionState.Motivo.ADMIN_BLOCK);
        o.logout("s-i4");

        assertEquals(List.of("I1:s-i1", "I4:s-i4"), invariantes(a));
    }
}
