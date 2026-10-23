package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * B-43.9, regra C: dentro de UMA operacao ({@code block}, {@code logout} versionado, auto-invalidacao
 * do {@code login}), a mesma (invariante, {@code sid}) e registrada no auditor no maximo uma vez. As
 * duas checagens da cascata continuam (a da varredura e a da leitura versionada). Operacoes distintas
 * continuam registrando cada uma a sua.
 */
class DeduplicacaoI1I4Test {

    private static final String U = "u-dd";
    private static final Instant T = Instant.parse("2026-10-23T00:00:00Z");

    private static SessionState valida(String sid) {
        return SessionState.criar(sid, U, T);
    }

    private static SessionState invalida(String sid, SessionState.Motivo m) {
        return SessionState.criar(sid, U, T).invalidar(m, T);
    }

    private static SessionOps ops(ArmazemVersionado<SessionState> s, FakeCaches.Cache<IdentityState> ids,
                                  ConcurrentHashMap<String, SessionState.Motivo> mapa, InvariantAuditor a) {
        return new SessionOps(s, ids, new LatencyRegistry(), a, null, null, "fake", null, mapa,
                new ConcurrentHashMap<>());
    }

    private static List<String> invariantes(InvariantAuditor a) {
        return a.drenar().stream().map(v -> v.invariante() + ":" + v.chave()).toList();
    }

    @Test
    @DisplayName("Z14a: duas leituras VALID do mesmo sid depois da confirmacao, no block => uma I1")
    void duasLeiturasUmaI1() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", valida("s1"));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.LOGOUT);
        InvariantAuditor a = new InvariantAuditor();

        ops(s, new FakeCaches.Cache<>(), mapa, a).block(U);

        assertEquals(List.of("I1:s1"), invariantes(a));
        assertEquals(1, s.leiturasVersionadas.get(), "as duas checagens rodaram (varredura e lerComVersao)");
    }

    @Test
    @DisplayName("Z14b: I1 na varredura e I4 na leitura versionada do mesmo sid => as duas (chave = invariante + sid)")
    void i1EI4DoMesmoSid() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", valida("s1"));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.LOGOUT);
        InvariantAuditor a = new InvariantAuditor();
        MundoMarcos.SessoesComGancho g = new MundoMarcos.SessoesComGancho(s);
        g.depoisDoEntrySet = () -> s.semear("s1", invalida("s1", SessionState.Motivo.ADMIN_BLOCK));

        ops(g, new FakeCaches.Cache<>(), mapa, a).block(U);

        assertEquals(List.of("I1:s1", "I4:s1"), invariantes(a));
    }

    @Test
    @DisplayName("Z14c: varredura VALID e lerComVersao INVALID com o motivo confirmado => 1 I1, da varredura")
    void checagemDaVarreduraContinua() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", valida("s1"));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.LOGOUT);
        InvariantAuditor a = new InvariantAuditor();
        MundoMarcos.SessoesComGancho g = new MundoMarcos.SessoesComGancho(s);
        g.depoisDoEntrySet = () -> s.semear("s1", invalida("s1", SessionState.Motivo.LOGOUT));

        ops(g, new FakeCaches.Cache<>(), mapa, a).block(U);

        assertEquals(List.of("I1:s1"), invariantes(a), "a reversao vista no entrySet continua acusada");
        assertEquals(0, s.tentativasCondicionais.get());
    }

    @Test
    @DisplayName("Z14d: dois sids da mesma identidade, os dois confirmados antes => uma I1 para cada")
    void doisSids() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", valida("s1"));
        s.semear("s2", valida("s2"));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.LOGOUT);
        mapa.put("s2", SessionState.Motivo.LOGOUT);
        InvariantAuditor a = new InvariantAuditor();

        ops(s, new FakeCaches.Cache<>(), mapa, a).block(U);

        assertEquals(List.of("I1:s1", "I1:s2"), invariantes(a));
    }

    @Test
    @DisplayName("Z14e: auto-invalidacao do login com 4 leituras VALID do mesmo sid (conflito esgotado) => uma I1")
    void autoInvalidacaoDoLogin() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.sempreConflita = true;
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ids.dados.put(U, IdentityState.criar(U, T));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && op.equals("put")) {
                feito[0] = true;
                // confirmacao anterior a releitura (forcada) e o block entre o put e a releitura
                mapa.put(k, SessionState.Motivo.LOGOUT);
                ids.dados.put(U, ids.dados.get(U).bloquear(T));
            }
        };
        InvariantAuditor a = new InvariantAuditor();
        SessionOps o = ops(s, ids, mapa, a);

        OpResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> o.login(U, "cred"));

        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        String sid = s.copia().keySet().iterator().next();
        assertEquals(1 + SessionOps.TENTATIVAS_CONDICIONAIS, s.leiturasVersionadas.get(), "lerComVersao e 3 releituras");
        assertEquals(List.of("I1:" + sid), invariantes(a));
    }

    @Test
    @DisplayName("Z15a: logout versionado, primeira leitura VALID com confirmacao anterior, escrita falha, releitura VALID => uma I1")
    void logoutVersionadoUmaI1() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", valida("s1"));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.ADMIN_BLOCK);
        InvariantAuditor a = new InvariantAuditor();
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && op.equals("substituirSeVersao")) {
                feito[0] = true;
                s.semear("s1", valida("s1")); // versao nova, mesmo valor: a escrita condicional falha
            }
        };
        SessionOps o = ops(s, new FakeCaches.Cache<>(), mapa, a);

        assertEquals(OpResult.Code.OK_NOOP, o.logout("s1").code());

        assertEquals(List.of("I1:s1"), invariantes(a));
        assertEquals(2, s.tentativasCondicionais.get());
        assertEquals(2, s.leiturasVersionadas.get());
    }

    @Test
    @DisplayName("Z15b: validate, logout e logout do mesmo sid (cada um ve VALID velho) => uma I1 por operacao")
    void operacoesDistintas() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear("s1", valida("s1"));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        mapa.put("s1", SessionState.Motivo.LOGOUT);
        InvariantAuditor a = new InvariantAuditor();
        SessionOps o = ops(s, new FakeCaches.Cache<>(), mapa, a);

        o.validate("s1");
        o.logout("s1");
        s.semear("s1", valida("s1")); // replica velha de novo
        o.logout("s1");

        assertEquals(List.of("I1:s1", "I1:s1", "I1:s1"), invariantes(a));
    }
}
