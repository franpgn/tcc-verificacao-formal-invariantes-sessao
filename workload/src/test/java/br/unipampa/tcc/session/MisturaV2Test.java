package br.unipampa.tcc.session;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.2, T2 (criterio 2 do diagnostico): mistura v2 com {@link FakeCaches} carregado pela
 * {@link CargaInicial}, 4 threads simuladas intercaladas e 10<sup>4</sup> operacoes em S1 e em S2.
 * Nenhuma identidade de login bloqueia, todo Login da OK, nenhum Validate cai em chave ausente, a
 * triade expande (eventos/operacoes &gt; 1) e, em S1, {@code lim-<tid>} chega a BLOCKED (O4
 * continua exercitado).
 */
class MisturaV2Test {

    static final int THREADS = 4;
    static final int OPERACOES = 10_000;

    /** Executa {@code OPERACOES} operacoes intercaladas por {@code THREADS} threads simuladas. */
    static void rodar(OpsGravadas ops, Scenario cenario, VersaoMistura mix) {
        Random[] r = new Random[THREADS];
        KeyGenerator[] k = new KeyGenerator[THREADS];
        IdentidadesMistura[] ids = new IdentidadesMistura[THREADS];
        for (int t = 0; t < THREADS; t++) {
            r[t] = new Random(42 + t);
            k[t] = new KeyGenerator(42 + t);
            ids[t] = new IdentidadesMistura(t, mix);
        }
        for (int i = 0; i < OPERACOES; i++) {
            int t = i % THREADS;
            WorkloadMain.executarOperacoes(ops, cenario, r[t], k[t], ids[t], 1);
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void misturaV2(Scenario cenario) throws Exception {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
        CargaInicial.carregar(new FakeCarregavel("sessions", sessoes), KeyGenerator.TAMANHO_PADRAO_UNIVERSO,
                THREADS, Instant.parse("2026-10-22T00:00:00Z"), CargaInicial.LOTE_PADRAO,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> 0L, d -> { });
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        OpsGravadas ops = new OpsGravadas(sessoes, identidades, new LatencyRegistry(), eventos);

        rodar(ops, cenario, VersaoMistura.V2);

        // nenhuma identidade de login fica BLOCKED; nenhuma recebeu O4/O5
        long identidadesDeLogin = 0;
        for (Map.Entry<String, IdentityState> e : identidades.dados.entrySet()) {
            if (e.getKey().matches("user-\\d+-\\d+")) {
                identidadesDeLogin++;
                assertEquals(IdentityState.Status.OPEN, e.getValue().status(), e.getKey());
                assertEquals(0L, e.getValue().contadorFalhas(), e.getKey());
            }
        }
        assertEquals(THREADS * IdentidadesMistura.POOL_LOGIN_POR_THREAD, identidadesDeLogin);

        long logins = ops.contar(SessionOps.OP_LOGIN);
        long loginsOk = ops.contar(SessionOps.OP_LOGIN, OpResult.Code.OK_LOGIN);
        assertTrue(logins > 0);
        assertEquals(logins, loginsOk, "Login OK / Login = 1,0");
        assertEquals(0, ops.contar(SessionOps.OP_VALIDATE, OpResult.Code.NOT_FOUND), "Validate NOT_FOUND = 0");
        assertTrue(ops.contar(SessionOps.OP_VALIDATE, OpResult.Code.OK_VALIDATE) > 0);

        // O4/O5 so em lim-<tid>
        ops.chamadas.stream()
                .filter(c -> c.op().equals(SessionOps.OP_INCREMENT_FAILURE) || c.op().equals(SessionOps.OP_RESET_FAILURES))
                .forEach(c -> assertTrue(c.arg().matches("lim-[0-3]"), c.arg()));
        if (cenario == Scenario.S1) {
            for (int t = 0; t < THREADS; t++) {
                IdentityState lim = identidades.dados.get("lim-" + t);
                assertNotNull(lim, "lim-" + t);
                assertEquals(IdentityState.Status.BLOCKED, lim.status(), "lim-" + t + " deveria bloquear em S1");
            }
        }

        // a triade expande: eventos - operacoes = 2 x logins OK, e eventos/operacoes > 1
        long eventosEmitidos = eventos.totalRegistrados();
        assertEquals(OPERACOES + 2 * loginsOk, eventosEmitidos);
        assertTrue((double) eventosEmitidos / OPERACOES > 1.0);
    }
}
