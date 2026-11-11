package br.unipampa.tcc.session;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de latências por nome de operação com HdrHistogram.
 *
 * Granularidade: precisão de 3 dígitos significativos no intervalo
 * 1 microssegundo a 60 segundos. Cobre p50, p95, p99 e p99.9
 * conforme T13/T14 da Tabela 1 do Capítulo 3.
 *
 * Cada operação tem um {@link Recorder} dedicado, thread-safe sem
 * locking explícito. A leitura para CSV usa {@code getIntervalHistogram}
 * que devolve um {@link Histogram} estável para inspeção.
 */
public class LatencyRegistry {

    private static final long LOWEST_DISCERNIBLE_NS = 1_000L;
    private static final long HIGHEST_TRACKABLE_NS = 60L * 1_000_000_000L;
    private static final int SIGNIFICANT_DIGITS = 3;

    private final ConcurrentHashMap<String, Recorder> recorders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> descartados
            = new ConcurrentHashMap<>();
    private volatile WarmupPolicy politica;

    /** Constrói o registro com a política indicada para warm-up e descarte. */
    public LatencyRegistry(WarmupPolicy politica) {
        this.politica = politica != null ? politica : WarmupPolicy.always();
    }

    /** Construtor sem warm-up; equivalente a {@link WarmupPolicy#always()}. */
    public LatencyRegistry() {
        this(WarmupPolicy.always());
    }

    /** Troca a política após a construção (apenas para integração WorkloadMain). */
    public void definirPolitica(WarmupPolicy politica) {
        this.politica = politica != null ? politica : WarmupPolicy.always();
    }

    /** Quantidade de eventos descartados por estar fora da janela de medição. */
    public long descartados(String op) {
        java.util.concurrent.atomic.AtomicLong c = descartados.get(op);
        return c == null ? 0L : c.get();
    }

    public void record(String op, long startNs) {
        recordRaw(op, System.nanoTime() - startNs);
    }

    /** Registra uma latência pré-computada (em nanossegundos). */
    public void recordRaw(String op, long deltaNs) {
        if (!politica.deveRegistrar()) {
            descartados
                .computeIfAbsent(op, k -> new java.util.concurrent.atomic.AtomicLong())
                .incrementAndGet();
            return;
        }
        long clamped = Math.min(Math.max(deltaNs, LOWEST_DISCERNIBLE_NS), HIGHEST_TRACKABLE_NS);
        recorders
            .computeIfAbsent(op, k -> new Recorder(LOWEST_DISCERNIBLE_NS, HIGHEST_TRACKABLE_NS, SIGNIFICANT_DIGITS))
            .recordValue(clamped);
    }

    /**
     * Tira um snapshot por operação. Cada chamada drena o intervalo
     * acumulado desde a chamada anterior; o resultado é independente.
     */
    public Map<String, Histogram> snapshot() {
        Map<String, Histogram> out = new HashMap<>();
        recorders.forEach((op, rec) -> out.put(op, rec.getIntervalHistogram()));
        return out;
    }

    /**
     * Escreve um CSV com uma linha por operação:
     * {@code op,count,mean_ns,p50_ns,p95_ns,p99_ns,p999_ns,max_ns}.
     */
    public void dumpCsv(Path destino) throws IOException {
        Map<String, Histogram> snap = snapshot();
        Files.createDirectories(destino.getParent() == null ? Path.of(".") : destino.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write("op,count,mean_ns,p50_ns,p95_ns,p99_ns,p999_ns,max_ns");
            w.newLine();
            for (Map.Entry<String, Histogram> e : new TreeMap<>(snap).entrySet()) {
                Histogram h = e.getValue();
                w.write(String.format(
                    java.util.Locale.ROOT,
                    "%s,%d,%.0f,%d,%d,%d,%d,%d",
                    e.getKey(),
                    h.getTotalCount(),
                    h.getMean(),
                    h.getValueAtPercentile(50.0),
                    h.getValueAtPercentile(95.0),
                    h.getValueAtPercentile(99.0),
                    h.getValueAtPercentile(99.9),
                    h.getMaxValue()
                ));
                w.newLine();
            }
        }
    }
}
