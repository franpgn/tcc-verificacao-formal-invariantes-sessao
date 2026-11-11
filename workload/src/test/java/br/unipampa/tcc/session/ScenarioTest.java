package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes para o enum {@link Scenario}, com foco na proporção de operações
 * gerada por {@link Scenario#proximaOperacao(Random)}.
 *
 * <p>Os pesos declarados em cada cenário devem somar 100; a distribuição
 * empírica, em 1.000.000 de amostras com seed determinística, deve
 * aproximar os pesos com erro relativo abaixo de 3\% para cada categoria.
 * A tolerância de 3\% absorve a variância amostral de proporções pequenas
 * (com $p = 0{,}01$ e $n = 10^6$, o desvio padrão relativo é $\sim 1\%$;
 * a faixa de 3\% representa três desvios padrão, condição na qual o teste
 * é robusto a quaisquer JDKs que respeitem o contrato determinístico de
 * {@link Random} a partir de uma seed fixa).</p>
 */
class ScenarioTest {

    private static final int AMOSTRAS = 1_000_000;
    private static final long SEED = 20260614L;
    private static final double TOLERANCIA_RELATIVA = 0.03;

    @Test
    @DisplayName("S1 distribui 50% validate, 25% login-tríade, 10% logout, 10% inc, 5% reset")
    void s1DistribuiSegundoOsPesos() {
        Map<Scenario.Op, Double> esperado = new EnumMap<>(Scenario.Op.class);
        esperado.put(Scenario.Op.VALIDATE, 0.50);
        esperado.put(Scenario.Op.LOGIN_VALIDATE_LOGOUT, 0.25);
        esperado.put(Scenario.Op.LOGOUT, 0.10);
        esperado.put(Scenario.Op.INCREMENT_FAILURE, 0.10);
        esperado.put(Scenario.Op.RESET_FAILURES, 0.05);
        verificarDistribuicao(Scenario.S1, esperado);
    }

    @Test
    @DisplayName("S2 distribui 95% validate e o restante uniforme entre as escritas")
    void s2DistribuiSegundoOsPesos() {
        Map<Scenario.Op, Double> esperado = new EnumMap<>(Scenario.Op.class);
        esperado.put(Scenario.Op.VALIDATE, 0.95);
        esperado.put(Scenario.Op.LOGIN_VALIDATE_LOGOUT, 0.03);
        esperado.put(Scenario.Op.LOGOUT, 0.01);
        esperado.put(Scenario.Op.INCREMENT_FAILURE, 0.01);
        esperado.put(Scenario.Op.RESET_FAILURES, 0.00);
        verificarDistribuicao(Scenario.S2, esperado);
    }

    @Test
    @DisplayName("porNome resolve S1, S2 e cai para S1 em caso inválido")
    void porNomeResolveCorretamente() {
        assertEquals(Scenario.S1, Scenario.porNome("S1"));
        assertEquals(Scenario.S2, Scenario.porNome("s2"));
        assertEquals(Scenario.S1, Scenario.porNome(null));
        assertEquals(Scenario.S1, Scenario.porNome(""));
        assertEquals(Scenario.S1, Scenario.porNome("inexistente"));
    }

    private void verificarDistribuicao(Scenario cenario,
                                       Map<Scenario.Op, Double> esperado) {
        EnumMap<Scenario.Op, Long> contagem = new EnumMap<>(Scenario.Op.class);
        for (Scenario.Op op : Scenario.Op.values()) contagem.put(op, 0L);

        Random rng = new Random(SEED);
        for (int i = 0; i < AMOSTRAS; i++) {
            Scenario.Op op = cenario.proximaOperacao(rng);
            contagem.merge(op, 1L, Long::sum);
        }

        for (Scenario.Op op : Scenario.Op.values()) {
            double esp = esperado.getOrDefault(op, 0.0);
            double obs = ((double) contagem.get(op)) / AMOSTRAS;
            if (esp == 0.0) {
                assertEquals(0L, contagem.get(op).longValue(),
                    "Operacao " + op + " com peso zero teve " + contagem.get(op) + " ocorrencias");
            } else {
                double erro = Math.abs(obs - esp) / esp;
                assertTrue(erro < TOLERANCIA_RELATIVA,
                    "Cenario " + cenario + " op " + op
                        + " esperado " + esp + " observado " + obs
                        + " erro relativo " + erro);
            }
        }
    }
}
