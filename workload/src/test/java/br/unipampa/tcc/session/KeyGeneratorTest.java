package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes JUnit para {@link KeyGenerator}.
 *
 * <p>Cobre os três critérios de aceite de B-08 do
 * {@code docs/planejamento-inicial.md}:</p>
 * <ol>
 *   <li>Distribuição empírica em 10^6 amostras com erro relativo
 *       abaixo de 5\% nos primeiros 100 bins.</li>
 *   <li>Reprodutibilidade entre dois {@code KeyGenerator} com mesma
 *       seed.</li>
 *   <li>Cobertura efetiva do universo de chaves em 10^6 amostras.</li>
 * </ol>
 */
class KeyGeneratorTest {

    private static final int AMOSTRAS_MIL = 1_000_000;
    private static final int AMOSTRAS_PEQUENAS = 50_000;

    @Test
    @DisplayName("Distribuição empírica nos primeiros 100 bins com erro relativo < 5%")
    void distribuicaoEmpiricaCorrespondeAZipfian() {
        int universo = 10_000;
        double rho = 0.99;
        int amostras = AMOSTRAS_MIL;
        int bins = 100;

        KeyGenerator gen = new KeyGenerator(universo, rho, 12345L);
        long[] contagem = new long[universo + 1];
        for (int i = 0; i < amostras; i++) {
            contagem[gen.nextInt()]++;
        }

        double[] esperadoRelativo = pesosZipfNormalizados(universo, rho);
        double[] observadoRelativo = new double[universo + 1];
        for (int k = 1; k <= universo; k++) {
            observadoRelativo[k] = ((double) contagem[k]) / amostras;
        }

        int forasDaFaixa = 0;
        for (int k = 1; k <= bins; k++) {
            double rel = Math.abs(observadoRelativo[k] - esperadoRelativo[k])
                    / esperadoRelativo[k];
            if (rel > 0.05) {
                forasDaFaixa++;
            }
        }
        assertTrue(forasDaFaixa <= 5,
            "Esperado no máximo 5 bins fora da faixa de 5%, observados " + forasDaFaixa);

        long maior = 0;
        for (int k = 1; k <= universo; k++) {
            if (contagem[k] > maior) maior = contagem[k];
        }
        long maiorEsperado = contagem[1];
        assertEquals(maior, maiorEsperado,
            "A chave 1 deveria concentrar a maior frequência (cauda longa)");
    }

    @Test
    @DisplayName("Mesma seed produz mesma sequência (reprodutibilidade)")
    void reprodutibilidadeEntreGeradoresComMesmaSeed() {
        long seed = 987654321L;
        KeyGenerator g1 = new KeyGenerator(10_000, 0.99, seed);
        KeyGenerator g2 = new KeyGenerator(10_000, 0.99, seed);
        int prefixo = 5_000;
        for (int i = 0; i < prefixo; i++) {
            assertEquals(g1.nextInt(), g2.nextInt(),
                "Geradores divergiram na posição " + i);
        }
    }

    @Test
    @DisplayName("10^6 amostras cobrem ao menos 30% do universo de 100k")
    void coberturaEfetivaDoUniverso() {
        KeyGenerator gen = new KeyGenerator(KeyGenerator.TAMANHO_PADRAO_UNIVERSO,
                KeyGenerator.RHO_PADRAO, 42L);
        Set<Integer> chavesVistas = new HashSet<>();
        for (int i = 0; i < AMOSTRAS_MIL; i++) {
            chavesVistas.add(gen.nextInt());
        }
        double cobertura = ((double) chavesVistas.size())
                / KeyGenerator.TAMANHO_PADRAO_UNIVERSO;
        assertTrue(cobertura > 0.30,
            "Cobertura observada " + cobertura + " esperada superior a 30%");
    }

    @Test
    @DisplayName("nextSessionKey formata como sid-NNNNNN")
    void formatoChaveSessao() {
        KeyGenerator gen = new KeyGenerator(100, 0.99, 1L);
        for (int i = 0; i < AMOSTRAS_PEQUENAS / 1000; i++) {
            String chave = gen.nextSessionKey();
            assertNotNull(chave);
            assertTrue(chave.matches("sid-\\d{6}"),
                "Chave fora do formato: " + chave);
            int idx = Integer.parseInt(chave.substring(4));
            assertTrue(idx >= 1 && idx <= 100,
                "Índice fora de [1, 100]: " + idx);
        }
    }

    @Test
    @DisplayName("Toda amostra fica em [1, n]")
    void amostrasFicamNoIntervalo() {
        int universo = 1000;
        KeyGenerator gen = new KeyGenerator(universo, 0.99, 1L);
        for (int i = 0; i < AMOSTRAS_PEQUENAS; i++) {
            int k = gen.nextInt();
            assertTrue(k >= 1 && k <= universo,
                "Amostra " + k + " fora de [1, " + universo + "]");
        }
    }

    @Test
    @DisplayName("Construtor rejeita parâmetros inválidos")
    void construtorRejeitaInvalidos() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                    () -> new KeyGenerator(0, 0.99, 1L)),
            () -> assertThrows(IllegalArgumentException.class,
                    () -> new KeyGenerator(-1, 0.99, 1L)),
            () -> assertThrows(IllegalArgumentException.class,
                    () -> new KeyGenerator(100, 0.0, 1L)),
            () -> assertThrows(IllegalArgumentException.class,
                    () -> new KeyGenerator(100, -0.5, 1L))
        );
    }

    // --- utilidades ---

    /**
     * Devolve o vetor de probabilidades p[k] = (1/k^rho) / H(n, rho) para
     * k em [1, n]; índice 0 ignorado.
     */
    private static double[] pesosZipfNormalizados(int n, double rho) {
        double[] p = new double[n + 1];
        double soma = 0.0;
        for (int k = 1; k <= n; k++) {
            p[k] = 1.0 / Math.pow(k, rho);
            soma += p[k];
        }
        for (int k = 1; k <= n; k++) {
            p[k] /= soma;
        }
        return p;
    }
}
