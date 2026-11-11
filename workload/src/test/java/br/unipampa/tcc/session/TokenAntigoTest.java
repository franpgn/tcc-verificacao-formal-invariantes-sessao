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
 * B-43.7, T2 (B-43.7b, DT-008): cada Validate isolado le a geracao anterior (token antigo) por um sorteio
 * de Bernoulli p = 0,05 com Random proprio por thread; a posicao nao depende do {@code Random} da mistura.
 */
class TokenAntigoTest {

    @Test
    @DisplayName("T2 (B-43.7b): um sorteio por chamada; mesma semente, mesma sequencia; sementes diferentes, outra")
    void sorteioPorThread() {
        MisturaV3 m = new MisturaV3(new SlotsGeracao(10, 1), WarmupPolicy.always());
        MisturaV3.Cliente a = m.cliente(42);
        MisturaV3.Cliente a2 = m.cliente(42);
        MisturaV3.Cliente b = m.cliente(43);
        assertEquals(0.05, MisturaV3.Q, 1e-12);
        assertEquals("0.05", MisturaV3.rotuloQ());
        int verdadeiros = 0;
        boolean diferem = false;
        for (int i = 0; i < 2000; i++) {
            boolean x = a.proximoEhTokenAntigo();
            assertEquals(x, a2.proximoEhTokenAntigo(), "mesma semente, posicao " + i);
            diferem |= x != b.proximoEhTokenAntigo();
            verdadeiros += x ? 1 : 0;
        }
        assertTrue(diferem, "sementes 42 e 43 deram a mesma sequencia");
        assertTrue(verdadeiros > 0 && verdadeiros < 2000, "um sorteio por chamada: " + verdadeiros);
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
    @DisplayName("T2 (B-43.7b): o mesmo cliente(42) com Random da mistura diferentes da as mesmas posicoes de token antigo")
    void naoDependeDoRandom() throws Exception {
        for (Scenario s : Scenario.values()) {
            List<Integer> a = posicoes(42, s, 4000);
            List<Integer> b = posicoesComRandom(new java.util.Random(4242), s, 4000);
            assertTrue(a.size() > 50, s + ": " + a.size());
            int n = Math.min(a.size(), b.size());
            assertEquals(a.subList(0, n), b.subList(0, n), s.name());
            MisturaV3.Cliente so = new MisturaV3(new SlotsGeracao(10, 1), WarmupPolicy.always()).cliente(42);
            List<Integer> esperado = new ArrayList<>();
            for (int j = 0; esperado.size() < a.size(); j++) {
                if (so.proximoEhTokenAntigo()) {
                    esperado.add(j);
                }
            }
            assertEquals(esperado, a, s + ": posicoes = sorteios verdadeiros de um cliente(42) sozinho");
        }
    }

    /** Posicoes de token antigo de uma thread com {@code cliente(42)} e o Random da mistura {@code r}. */
    private static List<Integer> posicoesComRandom(java.util.Random r, Scenario s, int n) throws Exception {
        MundoV3 w = new MundoV3(1, false);
        WorkloadMain.executarOperacoes(w.ops, s, r, new KeyGenerator(42),
                new IdentidadesMistura(0, VersaoMistura.V3), n, w.mistura.cliente(42));
        List<OpsGravadas.Chamada> val = w.isolados(SessionOps.OP_VALIDATE);
        List<Integer> pos = new ArrayList<>();
        for (int i = 0; i < val.size(); i++) {
            if (MundoV3.invalid(val.get(i).r())) {
                pos.add(i);
            }
        }
        assertEquals(pos.size(), w.mistura.tokenAntigo(), "sem concorrencia, INVALID = token antigo");
        return pos;
    }
}
