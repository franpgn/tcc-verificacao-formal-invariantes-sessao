package br.unipampa.tcc.session;

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyRegistryTest {

    /**
     * Injeta 10 000 amostras com latência variando linearmente entre
     * 1 ms e 10 ms e verifica que os percentis caem dentro da
     * precisão esperada (3 dígitos, tolerância 1%).
     */
    @Test
    void percentisDentroDaPrecisao() {
        LatencyRegistry reg = new LatencyRegistry();
        long origem = System.nanoTime();
        for (int i = 1; i <= 10_000; i++) {
            long t0 = origem - i * 1_000L;
            long agora = t0 + i * 1_000L;
            long delta = i * 1_000L;
            reg.recordRaw("login_ok", delta);
            assertTrue(agora >= t0);
        }
        Map<String, Histogram> snap = reg.snapshot();
        Histogram h = snap.get("login_ok");
        assertNotNull(h);
        assertEquals(10_000L, h.getTotalCount());

        long p50 = h.getValueAtPercentile(50.0);
        long p99 = h.getValueAtPercentile(99.0);

        assertTrue(p50 >= 4_950_000L && p50 <= 5_050_000L, "p50 fora do esperado: " + p50);
        assertTrue(p99 >= 9_800_000L && p99 <= 10_050_000L, "p99 fora do esperado: " + p99);
    }

    @Test
    void dumpCsvProduzCabecalhoEUmaLinhaPorOperacao(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        LatencyRegistry reg = new LatencyRegistry();
        for (int i = 1; i <= 100; i++) {
            reg.recordRaw("login_ok", i * 1_000L);
            reg.recordRaw("validate", i * 500L);
        }
        Path csv = tmp.resolve("latencies.csv");
        reg.dumpCsv(csv);
        List<String> linhas = Files.readAllLines(csv);
        assertEquals(3, linhas.size());
        assertTrue(linhas.get(0).startsWith("op,count,mean_ns"));
        assertTrue(linhas.get(1).startsWith("login_ok,"));
        assertTrue(linhas.get(2).startsWith("validate,"));
    }

    /**
     * Com {@link WarmupPolicy#never()} o registro descarta todas as
     * amostras e o contador {@code descartados} reflete o volume.
     */
    @Test
    void warmupNeverDescartaTodasAsAmostras() {
        LatencyRegistry reg = new LatencyRegistry(WarmupPolicy.never());
        for (int i = 1; i <= 100; i++) {
            reg.recordRaw("login_ok", i * 1_000L);
        }
        assertEquals(0L, reg.snapshot().getOrDefault("login_ok",
                new Histogram(3)).getTotalCount(),
            "Nenhuma amostra deveria entrar no histograma");
        assertEquals(100L, reg.descartados("login_ok"),
            "Contador de descartados deveria refletir as 100 amostras");
    }

    /**
     * Mudança dinâmica de política simula a transição warm-up para
     * medição durante o workload.
     */
    @Test
    void transicaoNeverAlwaysEsvaziaApenasOsPosteriores() {
        LatencyRegistry reg = new LatencyRegistry(WarmupPolicy.never());
        for (int i = 1; i <= 50; i++) reg.recordRaw("login_ok", 1_000_000L);
        assertEquals(50L, reg.descartados("login_ok"));
        reg.definirPolitica(WarmupPolicy.always());
        for (int i = 1; i <= 30; i++) reg.recordRaw("login_ok", 1_000_000L);
        assertEquals(50L, reg.descartados("login_ok"),
            "Descartados nao deveria mudar apos transicao");
        assertEquals(30L, reg.snapshot().get("login_ok").getTotalCount(),
            "Histograma deveria conter as 30 amostras pos-transicao");
    }
}
