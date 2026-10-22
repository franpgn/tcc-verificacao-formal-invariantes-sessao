package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.2, T4: o laco v2 com {@code new Random(42)} e {@code KeyGenerator(42)} executa as mesmas 2000
 * primeiras operacoes de S1 e de S2 e usa as mesmas 500 primeiras chaves do golden
 * {@code golden-8e2e815/sequencia-s1-s2-seed42.txt} (lido como recurso, sem edita-lo). Prova que o
 * rodizio de identidades nao consome o {@code Random} nem o {@code KeyGenerator}.
 */
class SequenciaMisturaV2Test {

    private static List<String> golden() throws Exception {
        try (InputStream in = SequenciaMisturaV2Test.class.getResourceAsStream(
                "/golden-8e2e815/sequencia-s1-s2-seed42.txt")) {
            String t = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return List.of(t.split("\n"));
        }
    }

    /** Linhas do golden entre o cabecalho {@code # <secao>} e o proximo {@code #}. */
    private static List<String> secao(List<String> g, String nome) {
        List<String> out = new ArrayList<>();
        int i = g.indexOf("# " + nome);
        assertTrue(i >= 0, nome);
        for (int j = i + 1; j < g.size() && !g.get(j).startsWith("#"); j++) {
            out.add(g.get(j));
        }
        return out;
    }

    private static List<Object[]> executar(Scenario cenario, VersaoMistura mix, int n) {
        OpsGravadas ops = new OpsGravadas(new FakeCaches.Cache<>(), new FakeCaches.Cache<>(),
                new LatencyRegistry(), null);
        WorkloadMain.executarOperacoes(ops, cenario, new Random(42), new KeyGenerator(42),
                new IdentidadesMistura(0, mix), n);
        return ops.operacoes();
    }

    @Test
    @DisplayName("T4: v2 executa as 2000 primeiras operacoes de S1 e S2 e as 500 primeiras chaves do golden")
    void sequenciaIntacta() throws Exception {
        List<String> g = golden();
        List<String> chaves = secao(g, "chaves");
        assertEquals(500, chaves.size());
        for (Scenario s : Scenario.values()) {
            List<String> esperadas = secao(g, s.name());
            assertEquals(2000, esperadas.size());
            for (VersaoMistura mix : VersaoMistura.values()) {
                List<Object[]> ops = executar(s, mix, 2000);
                assertEquals(2000, ops.size(), s + " " + mix);
                int chavesConferidas = 0;
                for (int i = 0; i < 2000; i++) {
                    assertEquals(esperadas.get(i), ops.get(i)[0].toString(), s + " " + mix + " op " + i);
                    if (i < 500 && ops.get(i)[1] != null) {
                        assertEquals(chaves.get(i), ops.get(i)[1], s + " " + mix + " chave " + i);
                        chavesConferidas++;
                    }
                }
                assertTrue(chavesConferidas > 250, s + " " + mix + ": so " + chavesConferidas + " chaves conferidas");
            }
        }
    }
}
