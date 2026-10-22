package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T3: com {@code new Random(42)} e {@code KeyGenerator(42)}, a mistura v3 executa as mesmas
 * 2000 primeiras operacoes de S1 e S2 do golden {@code golden-8e2e815/sequencia-s1-s2-seed42.txt}
 * (lido como recurso, sem edita-lo), e o slot da chave usada pelos Validate/Logout isolados das 500
 * primeiras iteracoes e o numero da chave do golden. Prova que o token antigo e a reposicao nao
 * consomem o {@code Random} nem o {@code KeyGenerator}.
 */
class SequenciaMisturaV3Test {

    private static List<String> golden() throws Exception {
        try (InputStream in = SequenciaMisturaV3Test.class.getResourceAsStream(
                "/golden-8e2e815/sequencia-s1-s2-seed42.txt")) {
            String t = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return List.of(t.split("\n"));
        }
    }

    private static List<String> secao(List<String> g, String nome) {
        List<String> out = new ArrayList<>();
        int i = g.indexOf("# " + nome);
        assertTrue(i >= 0, nome);
        for (int j = i + 1; j < g.size() && !g.get(j).startsWith("#"); j++) {
            out.add(g.get(j));
        }
        return out;
    }

    @Test
    @DisplayName("T3: v3 executa as 2000 primeiras operacoes de S1 e S2 e os slots das 500 primeiras chaves do golden")
    void sequenciaIntacta() throws Exception {
        List<String> g = golden();
        List<String> chaves = secao(g, "chaves");
        assertEquals(500, chaves.size());
        for (Scenario s : Scenario.values()) {
            List<String> esperadas = secao(g, s.name());
            assertEquals(2000, esperadas.size());
            MundoV3 w = new MundoV3(1, false);
            w.rodar(s, 2000, 42);
            List<Object[]> ops = w.ops.operacoes();
            assertEquals(2000, ops.size(), s.name());
            int conferidas = 0;
            int comGeracao = 0;
            for (int i = 0; i < 2000; i++) {
                assertEquals(esperadas.get(i), ops.get(i)[0].toString(), s + " op " + i);
                String chave = (String) ops.get(i)[1];
                if (i < 500 && chave != null) {
                    assertEquals(SlotsGeracao.slotDe(chaves.get(i)), SlotsGeracao.slotDe(chave), s + " slot " + i);
                    conferidas++;
                    if (SlotsGeracao.geracaoDe(chave) >= 1) {
                        comGeracao++;
                    }
                }
            }
            assertTrue(conferidas > 250, s + ": so " + conferidas + " slots conferidos");
            assertTrue(comGeracao > 0, s + ": a v3 usa chaves sid-NNNNNN~g");
        }
    }
}
