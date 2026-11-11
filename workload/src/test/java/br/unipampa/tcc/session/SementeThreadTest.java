package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7b, U7: {@link WorkloadMain#sementeThread(long, int, int)} e a extracao, sem mudanca de valor,
 * da formula que a producao ja usava para a semente de cada thread em {@code executarRepeticao}
 * ({@code cli.seedBase + tid + rep * 1_000_003L}). O token antigo (DT-008) deriva a sua semente dela.
 */
class SementeThreadTest {

    @Test
    @DisplayName("U7: sementeThread reproduz a formula antiga numa grade de (seedBase, tid, rep)")
    void reproduzFormulaAntiga() {
        long[] bases = {0L, 1L, 42L, -1L, 1_000_003L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long b : bases) {
            for (int tid = 0; tid < 8; tid++) {
                for (int rep = 0; rep < 5; rep++) {
                    long antiga = b + (long) tid + (long) rep * 1_000_003L;
                    assertEquals(antiga, WorkloadMain.sementeThread(b, tid, rep), b + "/" + tid + "/" + rep);
                }
            }
        }
        assertEquals(42L + 3 + 2 * 1_000_003L, WorkloadMain.sementeThread(42, 3, 2));
    }

    @Test
    @DisplayName("U7: a repeticao muda a semente da thread")
    void repMudaSemente() {
        assertTrue(WorkloadMain.sementeThread(42, 0, 0) != WorkloadMain.sementeThread(42, 0, 1));
        assertEquals(1_000_003L, WorkloadMain.sementeThread(42, 0, 1) - WorkloadMain.sementeThread(42, 0, 0));
        assertEquals(1L, WorkloadMain.sementeThread(42, 1, 0) - WorkloadMain.sementeThread(42, 0, 0));
    }
}
