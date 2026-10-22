package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T2: exatamente 1 de cada 20 Validate isolados por thread le a geracao anterior (token
 * antigo), na posicao {@code contador % 20 == 19}; a posicao nao depende do {@code Random} da mistura.
 */
class TokenAntigoTest {

    @Test
    @DisplayName("T2: o contador por thread marca o 20o, 40o, ... Validate isolado")
    void contadorPorThread() {
        MisturaV3 m = new MisturaV3(new SlotsGeracao(10, 1), WarmupPolicy.always());
        MisturaV3.Cliente a = m.cliente();
        MisturaV3.Cliente b = m.cliente();
        assertEquals(20, MisturaV3.PERIODO_TOKEN_ANTIGO);
        assertEquals(0.05, MisturaV3.Q, 1e-12);
        assertEquals("0.05", MisturaV3.rotuloQ());
        for (int i = 0; i < 200; i++) {
            assertEquals(i % 20 == 19, a.proximoEhTokenAntigo(), "posicao " + i);
            if (i < 19) {
                assertFalse(b.proximoEhTokenAntigo(), "o contador e por thread: b na posicao " + i);
            }
        }
    }

    /**
     * Posicoes (indice entre os Validate isolados da thread) dos que leram o token antigo, numa thread
     * so, reconstruindo a geracao de cada slot pela sequencia dos Logout isolados.
     */
    private static List<Integer> posicoes(long semente, Scenario cenario, int n) throws Exception {
        MundoV3 w = new MundoV3(1, false);
        w.rodar(cenario, n, semente);
        Map<Integer, Integer> geracao = new HashMap<>();
        List<Integer> pos = new ArrayList<>();
        int i = 0;
        for (OpsGravadas.Chamada c : w.ops.chamadas) {
            if (!c.arg().startsWith(KeyGenerator.PREFIXO_SID)) {
                continue;
            }
            int k = SlotsGeracao.slotDe(c.arg());
            int g = SlotsGeracao.geracaoDe(c.arg());
            int corrente = geracao.getOrDefault(k, 1);
            if (c.op().equals(SessionOps.OP_LOGOUT)) {
                assertEquals(corrente, g, "Logout isolado sempre na geracao corrente");
                geracao.put(k, corrente + 1);
            } else if (c.op().equals(SessionOps.OP_VALIDATE)) {
                if (g == corrente - 1) {
                    pos.add(i);
                } else {
                    assertEquals(corrente, g, "Validate isolado le g ou g-1");
                }
                i++;
            }
        }
        assertEquals(i, w.mistura.validateIsolados());
        assertEquals(pos.size(), w.mistura.tokenAntigo());
        return pos;
    }

    @Test
    @DisplayName("T2: dois Random diferentes dao as mesmas posicoes de token antigo (19, 39, 59, ...)")
    void naoDependeDoRandom() throws Exception {
        for (Scenario s : Scenario.values()) {
            List<Integer> a = posicoes(42, s, 4000);
            List<Integer> b = posicoes(4242, s, 4000);
            assertTrue(a.size() > 50, s + ": " + a.size());
            int n = Math.min(a.size(), b.size());
            assertEquals(a.subList(0, n), b.subList(0, n), s.name());
            for (int j = 0; j < a.size(); j++) {
                assertEquals(20 * j + 19, a.get(j), s + " token antigo " + j);
            }
            for (int j = 0; j < b.size(); j++) {
                assertEquals(20 * j + 19, b.get(j), s + " token antigo " + j + " (semente 4242)");
            }
        }
    }
}
