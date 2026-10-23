package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.5, W9: guardas de fonte (sem comentarios, {@link DetectorFonte}) do {@code Random} da mistura
 * semeado por thread. {@code rodarCliente} obtem o {@code Random} por {@code randomDaMistura(seed)} e
 * nao cria outro; nenhuma classe da mistura S1/S2 usa {@code ThreadLocalRandom} nem {@code Math.random};
 * {@code randomDaMistura} cria {@code new Random(sementeMistura(sementeThread))}.
 */
class MisturaSemeadaFonteTest {

    static final String LINHA = "Random r = randomDaMistura(seed);";

    private static String fonte(String arquivo) throws Exception {
        return ArmazemVersionadoTest.fonteSemComentarios(arquivo);
    }

    private static String mascarado(String arquivo) throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/" + arquivo);
        return DetectorFonte.mascarado(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private static String corpo(String arquivo, String regexAssinatura) throws Exception {
        int[] c = DetectorFonte.corpo(mascarado(arquivo), regexAssinatura);
        assertNotNull(c, "metodo nao encontrado: " + regexAssinatura);
        return fonte(arquivo).substring(c[0], c[1] + 1);
    }

    private static int ocorrencias(String s, String alvo) {
        int n = 0;
        for (int i = s.indexOf(alvo); i >= 0; i = s.indexOf(alvo, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("W9: rodarCliente obtem o Random por randomDaMistura(seed), uma vez, e nao cria outro")
    void rodarClienteUsaRandomDaMistura() throws Exception {
        String c = corpo("WorkloadMain.java", "private static void rodarCliente\\(");
        assertEquals(1, ocorrencias(c, LINHA), "esperada 1 linha '" + LINHA + "' em rodarCliente");
        assertFalse(c.contains("ThreadLocalRandom"), "rodarCliente referencia ThreadLocalRandom");
        assertFalse(c.contains("new Random("), "rodarCliente cria outro Random");
        assertFalse(c.contains("Math.random"), "rodarCliente usa Math.random");
    }

    @Test
    @DisplayName("W9: WorkloadMain, Scenario, KeyGenerator, IdentidadesMistura, MisturaV3 e SlotsGeracao sem ThreadLocalRandom nem Math.random")
    void semFonteNaoSemeada() throws Exception {
        List<String> achados = new ArrayList<>();
        for (String a : List.of("WorkloadMain.java", "Scenario.java", "KeyGenerator.java",
                "IdentidadesMistura.java", "MisturaV3.java", "SlotsGeracao.java")) {
            String s = fonte(a);
            if (s.contains("ThreadLocalRandom")) {
                achados.add(a + ": ThreadLocalRandom");
            }
            if (s.contains("Math.random")) {
                achados.add(a + ": Math.random");
            }
        }
        assertEquals(List.of(), achados, "fonte de aleatoriedade nao semeada");
    }

    @Test
    @DisplayName("W9: randomDaMistura cria new Random(sementeMistura(sementeThread))")
    void randomDaMisturaSemeado() throws Exception {
        String c = corpo("WorkloadMain.java", "static Random randomDaMistura\\(long sementeThread\\)");
        assertTrue(c.contains("new Random(sementeMistura(sementeThread))"),
                "randomDaMistura sem new Random(sementeMistura(sementeThread))");
    }
}
