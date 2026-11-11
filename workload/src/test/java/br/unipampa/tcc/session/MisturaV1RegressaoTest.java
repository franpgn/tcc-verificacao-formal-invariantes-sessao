package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.2, T6: regressao de v1. Login, IncrementFailure e ResetFailures usam {@code user-<tid>}; em S1
 * com 10<sup>4</sup> operacoes a identidade chega a BLOCKED e os logins seguintes sao recusados (C1
 * reproduzido de proposito); sem carga, os Validate isolados caem em chave ausente (C2).
 */
class MisturaV1RegressaoTest {

    @Test
    @DisplayName("T6: v1 usa user-<tid> nas tres operacoes de identidade e reproduz o bloqueio absorvente")
    void v1ReproduzC1() {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
        OpsGravadas ops = new OpsGravadas(sessoes, identidades, new LatencyRegistry(),
                new EventCsvWriter(WarmupPolicy.always()));

        MisturaV2Test.rodar(ops, Scenario.S1, VersaoMistura.V1);

        for (OpsGravadas.Chamada c : ops.chamadas) {
            if (c.op().equals(SessionOps.OP_LOGIN) || c.op().equals(SessionOps.OP_INCREMENT_FAILURE)
                    || c.op().equals(SessionOps.OP_RESET_FAILURES)) {
                assertTrue(c.arg().matches("user-[0-3]"), c.op() + " " + c.arg());
            }
        }
        for (int t = 0; t < MisturaV2Test.THREADS; t++) {
            assertEquals(IdentityState.Status.BLOCKED, identidades.dados.get("user-" + t).status(), "user-" + t);
        }
        assertEquals(MisturaV2Test.THREADS, identidades.dados.size(), "so as identidades user-<tid>");
        long logins = ops.contar(SessionOps.OP_LOGIN);
        long bloqueados = ops.contar(SessionOps.OP_LOGIN, OpResult.Code.BLOCKED);
        assertTrue(bloqueados > 0.9 * logins, "bloqueio absorvente: " + bloqueados + " de " + logins);
        // o ultimo login de cada thread e BLOCKED (absorvente)
        assertEquals(OpResult.Code.BLOCKED, ops.chamadas.stream()
                .filter(c -> c.op().equals(SessionOps.OP_LOGIN)).reduce((a, b) -> b).orElseThrow().r().code());
        // sem carga: Validate isolado cai em chave ausente
        assertTrue(ops.contar(SessionOps.OP_VALIDATE, OpResult.Code.NOT_FOUND) > 0);
        assertTrue(sessoes.dados.keySet().stream().noneMatch(k -> k.startsWith(KeyGenerator.PREFIXO_SID)),
                "v1 nao carrega sids");
    }
}
