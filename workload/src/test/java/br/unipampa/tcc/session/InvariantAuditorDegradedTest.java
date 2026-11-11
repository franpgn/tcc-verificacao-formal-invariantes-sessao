package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 1b): leitores multi-replica toleram no em DEGRADED. Excecao de
 * leitura = rodada INVALIDA para aquela replica, nunca "conjunto vazio =
 * convergiu" e nunca excecao que derruba a thread da auditoria.
 */
class InvariantAuditorDegradedTest {

    private static final Instant T = Instant.parse("2026-10-15T12:00:00Z");

    private static Supplier<Map<String, SessionState>> sessoes(SessionState... ss) {
        Map<String, SessionState> m = new HashMap<>();
        for (SessionState s : ss) m.put(s.id(), s);
        return () -> m;
    }

    private static Supplier<Map<String, SessionState>> degradado() {
        return () -> { throw new RuntimeException("ISPN000306: cache DEGRADED"); };
    }

    private static Supplier<Map<String, IdentityState>> identidades(IdentityState... is) {
        Map<String, IdentityState> m = new HashMap<>();
        for (IdentityState i : is) m.put(i.identidade(), i);
        return () -> m;
    }

    @Test
    @DisplayName("avaliarRodada: leitor que lanca torna a rodada incompleta (invalida)")
    void leitorQueLancaInvalidaARodada() {
        InvariantAuditor a = new InvariantAuditor();
        SessionState s = SessionState.criar("s1", "u1", T);
        InvariantAuditor.RodadaAuditoria r = a.avaliarRodada(
                List.of(sessoes(s), degradado(), sessoes(s)), List.of());
        assertFalse(r.completa());
        assertEquals(1, r.leiturasFalhas());
    }

    @Test
    @DisplayName("avaliarRodada: todas as leituras ok e sem divergencia = completa, 0 e 0")
    void rodadaCompletaSemDivergencia() {
        InvariantAuditor a = new InvariantAuditor();
        SessionState s = SessionState.criar("s1", "u1", T);
        IdentityState i = IdentityState.criar("u1", T);
        InvariantAuditor.RodadaAuditoria r = a.avaliarRodada(
                List.of(sessoes(s), sessoes(s)), List.of(identidades(i), identidades(i)));
        assertTrue(r.completa());
        assertEquals(0, r.divergentesI5());
        assertEquals(0, r.divergentesI6());
    }

    @Test
    @DisplayName("avaliarRodada conta chaves divergentes de I5 e I6 sem registrar violacao")
    void contaDivergenciasSemRegistrar() {
        InvariantAuditor a = new InvariantAuditor();
        SessionState v = SessionState.criar("s1", "u1", T);
        SessionState inv = v.invalidar(SessionState.Motivo.LOGOUT, T);
        SessionState v2 = SessionState.criar("s2", "u1", T);
        SessionState inv2 = v2.invalidar(SessionState.Motivo.LOGOUT, T);
        IdentityState i0 = IdentityState.criar("u1", T);
        IdentityState i1 = i0.incrementar(5, T);
        InvariantAuditor.RodadaAuditoria r = a.avaliarRodada(
                List.of(sessoes(v, v2), sessoes(inv, inv2)),
                List.of(identidades(i0), identidades(i1)));
        assertTrue(r.completa());
        assertEquals(2, r.divergentesI5());
        assertEquals(1, r.divergentesI6());
        assertEquals(0, a.total(), "avaliarRodada nao registra violacoes");
    }

    @Test
    @DisplayName("rodadaUnica nao propaga excecao de leitor e ainda detecta divergencia dos demais")
    void rodadaUnicaTolerante() {
        InvariantAuditor a = new InvariantAuditor();
        SessionState v = SessionState.criar("s1", "u1", T);
        SessionState inv = v.invalidar(SessionState.Motivo.LOGOUT, T);
        a.rodadaUnica(List.of(sessoes(v), degradado(), sessoes(inv)), List.of());
        assertEquals(1, a.total());
        assertEquals("I5", a.drenar().get(0).invariante());
        assertEquals(1, a.leiturasFalhas());
    }

    @Test
    @DisplayName("auditoria periodica sobrevive a leitor em DEGRADED (a tarefa agendada nao morre)")
    void agendadorSobrevive() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        Supplier<Map<String, SessionState>> sempreLanca = () -> {
            chamadas.incrementAndGet();
            throw new RuntimeException("DEGRADED");
        };
        try (InvariantAuditor a = new InvariantAuditor()) {
            a.iniciarAuditoriaPeriodica(10,
                    List.of(sempreLanca, sempreLanca), List.of());
            Thread.sleep(300);
        }
        assertTrue(chamadas.get() > 4, "esperava varias rodadas, houve " + chamadas.get());
    }
}
