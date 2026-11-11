package br.unipampa.tcc.session;

import java.time.Instant;

/**
 * Âncora epoch-nanossegundos do workload (T2 do bloco de eventos).
 *
 * <p>O {@link System#nanoTime()} é monotônico mas não tem origem fixa:
 * dois processos (ou duas execuções) não compartilham o mesmo zero. Para
 * que os {@code start_ns}/{@code end_ns} do CSV de eventos sejam
 * comparáveis a um relógio de parede (e entre repetições), cada repetição
 * registra, no seu início, um par de âncoras:</p>
 *
 * <ul>
 *   <li>{@code epochBaseNanos} = {@link Instant#now()} convertido para
 *       nanossegundos desde a época Unix;</li>
 *   <li>{@code monoBaseNanos} = {@link System#nanoTime()} no mesmo
 *       instante.</li>
 * </ul>
 *
 * <p>Um instante monotônico {@code mono} é convertido para epoch-ns por
 * {@code epochBaseNanos + (mono - monoBaseNanos)}. A diferença é calculada
 * em {@code long}, segura para a duração de um experimento (a folga até
 * estouro de {@code long} em nanossegundos é de ~292 anos).</p>
 *
 * <p>Capturar as duas âncoras o mais próximo possível uma da outra
 * minimiza o desvio de fase entre o relógio monotônico e o de parede; o
 * desvio residual é constante para toda a repetição e não afeta latências
 * (diferenças {@code end - start}), apenas o alinhamento absoluto.</p>
 */
public final class EpochClock {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long epochBaseNanos;
    private final long monoBaseNanos;

    private EpochClock(long epochBaseNanos, long monoBaseNanos) {
        this.epochBaseNanos = epochBaseNanos;
        this.monoBaseNanos = monoBaseNanos;
    }

    /**
     * Captura as âncoras agora. Lê {@link System#nanoTime()} imediatamente
     * antes e depois de {@link Instant#now()} para reduzir o desvio.
     */
    public static EpochClock capturarAgora() {
        long mono1 = System.nanoTime();
        Instant agora = Instant.now();
        long mono2 = System.nanoTime();
        long epoch = agora.getEpochSecond() * NANOS_PER_SECOND + agora.getNano();
        long monoMeio = mono1 + (mono2 - mono1) / 2L;
        return new EpochClock(epoch, monoMeio);
    }

    /** Constrói com âncoras explícitas (para testes determinísticos). */
    public static EpochClock de(long epochBaseNanos, long monoBaseNanos) {
        return new EpochClock(epochBaseNanos, monoBaseNanos);
    }

    /** Âncora de parede em nanossegundos desde a época Unix. */
    public long epochBaseNanos() { return epochBaseNanos; }

    /** Âncora monotônica em nanossegundos ({@link System#nanoTime()}). */
    public long monoBaseNanos() { return monoBaseNanos; }

    /**
     * Converte um instante monotônico ({@link System#nanoTime()}) para
     * nanossegundos desde a época Unix.
     */
    public long paraEpochNanos(long monoNanos) {
        return epochBaseNanos + (monoNanos - monoBaseNanos);
    }
}
