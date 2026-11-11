package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes para {@link WarmupPolicy}.
 *
 * <p>Cobre os cinco aspectos abaixo:</p>
 * <ol>
 *   <li>Aritmética da janela: warm-up = max(60s, 10\% * duração);
 *       descarte = 5\% do restante.</li>
 *   <li>Predicados de fase (warm-up, descarte, medição) avaliam
 *       corretamente o instante consultado.</li>
 *   <li>Em duração baixa (\(<\) 60\,s) o warm-up domina toda a janela.</li>
 *   <li>{@link WarmupPolicy#always()} sempre retorna {@code true} em
 *       {@code deveRegistrar()}.</li>
 *   <li>Validação de parâmetros inválidos.</li>
 * </ol>
 */
class WarmupPolicyTest {

    @Test
    @DisplayName("Em 600s, warm-up = 60s e descarte = 27s (5% de 540)")
    void aritmetica600s() {
        WarmupPolicy p = WarmupPolicy.padrao(0L, 600L);
        assertEquals(60_000L, p.warmupAteMs());
        // restante = 540s; descarte = ceil(540*0.05) = 27s
        assertEquals(60_000L + 27_000L, p.medicaoInicioMs());
        assertEquals(600_000L, p.medicaoFimMs());
    }

    @Test
    @DisplayName("Em 1800s, warm-up = 180s (10%) e descarte = 81s (5% de 1620)")
    void aritmetica1800s() {
        WarmupPolicy p = WarmupPolicy.padrao(0L, 1800L);
        assertEquals(180_000L, p.warmupAteMs());
        assertEquals(180_000L + 81_000L, p.medicaoInicioMs());
        assertEquals(1_800_000L, p.medicaoFimMs());
    }

    @Test
    @DisplayName("Em 60s, warm-up = 60s (mínimo); medição = zero")
    void aritmetica60s() {
        WarmupPolicy p = WarmupPolicy.padrao(0L, 60L);
        assertEquals(60_000L, p.warmupAteMs());
        assertEquals(60_000L, p.medicaoInicioMs());
        assertEquals(60_000L, p.medicaoFimMs());
    }

    @Test
    @DisplayName("Predicados emWarmup/emDescarte/emMedicao são mutuamente exclusivos")
    void predicadosMutuamenteExclusivos() {
        WarmupPolicy p = WarmupPolicy.padrao(0L, 600L);
        long[] amostras = {0L, 30_000L, 59_999L, 60_000L, 80_000L, 86_999L,
                87_000L, 300_000L, 599_999L, 600_000L};
        for (long t : amostras) {
            int categorias = (p.emWarmup(t) ? 1 : 0)
                    + (p.emDescarte(t) ? 1 : 0)
                    + (p.emMedicao(t) ? 1 : 0);
            if (t < p.medicaoFimMs()) {
                assertEquals(1, categorias,
                    "Em t=" + t + " esperado exatamente uma fase, observado " + categorias);
            } else {
                assertEquals(0, categorias,
                    "Em t=" + t + " esperado fora de todas as fases");
            }
        }
    }

    @Test
    @DisplayName("deveRegistrar respeita o Clock injetado")
    void deveRegistrarSegueClock() {
        Clock c30 = Clock.fixed(Instant.ofEpochMilli(30_000L), ZoneOffset.UTC);
        Clock c100 = Clock.fixed(Instant.ofEpochMilli(100_000L), ZoneOffset.UTC);
        assertFalse(WarmupPolicy.padrao(0L, 600L, c30).deveRegistrar());
        assertTrue(WarmupPolicy.padrao(0L, 600L, c100).deveRegistrar());
    }

    @Test
    @DisplayName("always() registra sempre; never() nunca")
    void atalhosAlwaysNever() {
        assertTrue(WarmupPolicy.always().deveRegistrar());
        assertFalse(WarmupPolicy.never().deveRegistrar());
    }

    @Test
    @DisplayName("Construtor rejeita duração negativa")
    void rejeitaDuracaoNegativa() {
        assertThrows(IllegalArgumentException.class,
                () -> WarmupPolicy.padrao(0L, -1L));
    }
}
