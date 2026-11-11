package br.unipampa.tcc.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica a conversão monotônico -> epoch-ns do {@link EpochClock} (T2)
 * e a preservação da relação end > start após a conversão.
 */
class EpochClockTest {

    @Test
    void converteMonotonicoParaEpochComOffsetConstante() {
        long epochBase = 1_700_000_000_000_000_000L; // ~2023 em epoch-ns
        long monoBase = 5_000_000_000L;
        EpochClock c = EpochClock.de(epochBase, monoBase);

        // No instante da âncora, epoch == epochBase.
        assertEquals(epochBase, c.paraEpochNanos(monoBase));

        // 1 ms depois (em mono) -> epochBase + 1e6.
        assertEquals(epochBase + 1_000_000L,
                c.paraEpochNanos(monoBase + 1_000_000L));

        // antes da âncora -> epoch menor (offset negativo aceito).
        assertEquals(epochBase - 2_000L,
                c.paraEpochNanos(monoBase - 2_000L));
    }

    @Test
    void conversaoPreservaRelacaoEndMaiorQueStart() {
        EpochClock c = EpochClock.de(1_700_000_000_000_000_000L, 0L);
        long startMono = 123_456L;
        long endMono = 123_456L + 4_321L;
        long startEpoch = c.paraEpochNanos(startMono);
        long endEpoch = c.paraEpochNanos(endMono);
        assertTrue(endEpoch > startEpoch);
        // A duração é preservada pela conversão (offset cancela).
        assertEquals(endMono - startMono, endEpoch - startEpoch);
    }

    @Test
    void capturarAgoraProduzAncorasPlausiveis() {
        EpochClock c = EpochClock.capturarAgora();
        // epochBase deve estar acima de 2020 (1.5e18 ns) — sanidade.
        assertTrue(c.epochBaseNanos() > 1_500_000_000_000_000_000L,
                "epochBaseNanos implausivelmente baixo: " + c.epochBaseNanos());
        // converter a própria âncora retorna o epochBase.
        assertEquals(c.epochBaseNanos(), c.paraEpochNanos(c.monoBaseNanos()));
    }
}
