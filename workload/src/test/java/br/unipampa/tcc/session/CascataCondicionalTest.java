package br.unipampa.tcc.session;

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
 * B-43.8 (DT-003): cascata de {@code block} no caminho versionado. A varredura so seleciona as
 * candidatas (identidade alvo e VALID) e checa I1 do valor da varredura contra a copia do mapa tirada
 * antes dela (B-43.6). Cada candidata: instantaneo por entrada, {@code lerComVersao}, checagem I1/I4
 * e a escrita condicional com ADMIN_BLOCK (mesma logica do {@code logout}).
 */
class CascataCondicionalTest {

    private static final String U = "u-cc";
    private static final Instant T = Instant.parse("2026-10-22T00:00:00Z");

    private static SessionOps ops(FakeVersionado<SessionState> s, FakeCaches.Cache<IdentityState> ids,
                                  ConcurrentHashMap<String, SessionState.Motivo> mapa, InvariantAuditor a) {
        return new SessionOps(s, ids, new LatencyRegistry(), a, null, null, "fake", null, mapa);
    }

    private static List<String> invariantes(InvariantAuditor a) {
        return a.drenar().stream().map(v -> v.invariante() + ":" + v.chave()).toList();
    }

    @Test
    @DisplayName("cascata: so as VALID da identidade, com escrita condicional; outras identidades intactas")
    void cascataSimples() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", SessionState.criar("s1", U, T));
        s.semear("s2", SessionState.criar("s2", U, T));
        s.semear("s3", SessionState.criar("s3", U, T).invalidar(SessionState.Motivo.LOGOUT, T));
        s.semear("x1", SessionState.criar("x1", "outro", T));
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        SessionOps admin = ops(s, ids, mapa, a);

        assertEquals(OpResult.Code.OK_NOOP, admin.block(U).code());

        assertEquals(IdentityState.Status.BLOCKED, ids.dados.get(U).status());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, s.valor("s1").motivo());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, s.valor("s2").motivo());
        assertEquals(SessionState.Motivo.LOGOUT, s.valor("s3").motivo());
        assertEquals(SessionState.Estado.VALID, s.valor("x1").estado());
        assertEquals(2, s.escritasCondicionaisAplicadas.get());
        assertEquals(2, s.leiturasVersionadas.get(), "uma lerComVersao por candidata");
        assertEquals(java.util.Map.of("s1", SessionState.Motivo.ADMIN_BLOCK, "s2", SessionState.Motivo.ADMIN_BLOCK),
                mapa);
        assertEquals(0, a.total());
        assertEquals(0, admin.invalidacaoPerdida());
    }

    @Test
    @DisplayName("Y8: logout completo entre a lerComVersao da cascata e a escrita => corrida, nao I1; block perde a versao")
    void corridaNaCascata() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", SessionState.criar("s1", U, T));
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        SessionOps admin = ops(s, ids, mapa, a);
        SessionOps app = ops(s, ids, mapa, a);
        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        s.depois = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("lerComVersao")) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> app.logout("s1"));
            }
        };

        admin.block(U);

        assertEquals(List.of(), invariantes(a), "a leitura comecou antes da confirmacao: corrida, nao I1");
        assertEquals(1, admin.i1Concorrentes());
        assertEquals(1, admin.invalidacaoPerdida());
        assertEquals(SessionState.Motivo.LOGOUT, s.valor("s1").motivo(), "o primeiro invalidador vence");
        assertEquals(SessionState.Motivo.LOGOUT, mapa.get("s1"));
    }

    @Test
    @DisplayName("W7 (cascata): confirmacao anterior ao block e sessao lida VALID => I1 (varredura e leitura versionada)")
    void controlePositivo() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", SessionState.criar("s1", U, T));
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.LOGOUT);
        InvariantAuditor a = new InvariantAuditor();
        SessionOps admin = ops(s, ids, mapa, a);

        admin.block(U);

        // Duas leituras distintas da sessao (o valor da varredura e a lerComVersao), as duas iniciadas
        // depois da confirmacao: cada uma e acusada.
        assertEquals(List.of("I1:s1", "I1:s1"), invariantes(a));
        assertEquals(0, admin.i1Concorrentes());
    }

    @Test
    @DisplayName("W7 (cascata): leitura versionada INVALID com motivo diferente do confirmado antes => I4, sem escrita")
    void controlePositivoI4() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", SessionState.criar("s1", U, T));
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        // A varredura ve VALID (mapa vazio). Logo depois dela, antes do instantaneo por entrada, o grid
        // passa a ter INVALID(LOGOUT) e a confirmacao registrada e ADMIN_BLOCK (divergencia real,
        // anterior a leitura versionada).
        Runnable divergir = () -> {
            s.semear("s1", SessionState.criar("s1", U, T).invalidar(SessionState.Motivo.LOGOUT, T));
            mapa.put("s1", SessionState.Motivo.ADMIN_BLOCK);
        };
        SessionOps admin = new SessionOps(new ArmazemComGancho(s, divergir), ids, new LatencyRegistry(), a,
                null, null, "fake", null, mapa);

        admin.block(U);

        assertEquals(List.of("I4:s1"), invariantes(a));
        assertEquals(SessionState.Motivo.LOGOUT, s.valor("s1").motivo(), "nao reescreve uma sessao INVALID");
        assertEquals(0, s.tentativasCondicionais.get());
        assertEquals(0, admin.invalidacaoPerdida(), "INVALID na primeira leitura versionada e no-op simples");
    }

    /** Versionado que roda {@code acao} uma vez, logo depois de devolver o {@code entrySet}. */
    private static final class ArmazemComGancho implements ArmazemVersionado<SessionState> {
        private final FakeVersionado<SessionState> base;
        private Runnable acao;

        ArmazemComGancho(FakeVersionado<SessionState> base, Runnable acao) {
            this.base = base;
            this.acao = acao;
        }

        @Override public SessionState get(String chave) { return base.get(chave); }
        @Override public SessionState put(String chave, SessionState valor) { return base.put(chave, valor); }

        @Override public java.util.Set<java.util.Map.Entry<String, SessionState>> entrySet() {
            java.util.Set<java.util.Map.Entry<String, SessionState>> e = base.entrySet();
            Runnable a = acao;
            acao = () -> { };
            a.run();
            return e;
        }

        @Override public Versionado<SessionState> lerComVersao(String chave) { return base.lerComVersao(chave); }

        @Override public boolean substituirSeVersao(String chave, SessionState novo, long versao) {
            return base.substituirSeVersao(chave, novo, versao);
        }
    }

    @Test
    @DisplayName("cascata com VALID persistente: 3 tentativas, conflitoEsgotado, block em erro, nada no mapa")
    void conflitoEsgotadoNaCascata() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", SessionState.criar("s1", U, T));
        s.sempreConflita = true;
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        SessionOps admin = ops(s, ids, mapa, a);

        OpResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> admin.block(U));

        assertEquals(ClassificadorDeErro.classificar(new IllegalStateException("conflito de versao esgotado")).code(),
                r.code());
        assertEquals(3, s.tentativasCondicionais.get());
        assertEquals(1, admin.conflitoEsgotado());
        assertFalse(mapa.containsKey("s1"));
        assertEquals(IdentityState.Status.BLOCKED, ids.dados.get(U).status(), "a identidade ja foi gravada");
    }
}
