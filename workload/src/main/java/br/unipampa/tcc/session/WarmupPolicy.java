package br.unipampa.tcc.session;

import java.time.Clock;
import java.util.Objects;

/**
 * Política de aquecimento e descarte do workload (T11 da Tabela 1 do
 * Cap. 3 §3.3.5 da monografia).
 *
 * <p>O workload executa três fases consecutivas:</p>
 * <ol>
 *   <li><b>Warm-up:</b> operações executam mas não são registradas em
 *       {@link LatencyRegistry}. Duração: máximo entre 60\,s e 10\,\% do
 *       total, conforme {@code cooper_ycsb_2010} (T11). Esta fase
 *       estabiliza JIT, alocadores e caches.</li>
 *   <li><b>Descarte:</b> imediatamente após o warm-up, descartam-se as
 *       primeiras 5\,\% das amostras nominais como margem de segurança
 *       adicional para efeitos transitórios. Implementação por tempo
 *       proporcional: 5\,\% do intervalo de medição nominal.</li>
 *   <li><b>Medição:</b> as latências são registradas no {@link LatencyRegistry}
 *       e compõem o conjunto a ser sumarizado pelo pipeline de análise
 *       estatística.</li>
 * </ol>
 *
 * <p>A política é imutável após a construção. Para testes que precisam
 * registrar sem latência de warm-up, use {@link #always()}.</p>
 */
public final class WarmupPolicy {

    public static final long WARMUP_MINIMO_SEG = 60L;
    public static final double FRACAO_WARMUP_MINIMA = 0.10;
    public static final double FRACAO_DESCARTE = 0.05;

    private final long warmupAteMs;
    private final long medicaoInicioMs;
    private final long medicaoFimMs;
    private final Clock clock;

    private WarmupPolicy(long warmupAteMs, long medicaoInicioMs,
                         long medicaoFimMs, Clock clock) {
        this.warmupAteMs = warmupAteMs;
        this.medicaoInicioMs = medicaoInicioMs;
        this.medicaoFimMs = medicaoFimMs;
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Constrói a política padrão a partir do início e duração nominais.
     *
     * @param inicioMs       instante de início do workload, em milissegundos
     * @param duracaoSeg     duração total nominal do workload, em segundos
     * @return política com warm-up de {@code max(60, 10\% * duracaoSeg)}
     *         segundos seguido de descarte de {@code 5\%} do tempo restante.
     */
    public static WarmupPolicy padrao(long inicioMs, long duracaoSeg) {
        return padrao(inicioMs, duracaoSeg, WARMUP_MINIMO_SEG, Clock.systemUTC());
    }

    /** Versão de {@link #padrao(long, long)} com {@link Clock} injetável (testes). */
    public static WarmupPolicy padrao(long inicioMs, long duracaoSeg, Clock clock) {
        return padrao(inicioMs, duracaoSeg, WARMUP_MINIMO_SEG, clock);
    }

    /**
     * Versão com warm-up mínimo configurável. O warm-up efetivo é
     * {@code max(warmupMinimoSeg, 10% * duracaoSeg)}. Útil para execuções
     * curtas de validação/smoke, onde o mínimo de 60\,s consumiria toda a
     * execução; as baterias reais usam o default de 60\,s.
     *
     * @param warmupMinimoSeg piso de warm-up em segundos (>= 0)
     */
    public static WarmupPolicy padrao(long inicioMs, long duracaoSeg,
                                      long warmupMinimoSeg, Clock clock) {
        if (duracaoSeg < 0) {
            throw new IllegalArgumentException("duracaoSeg deve ser >= 0");
        }
        if (warmupMinimoSeg < 0) {
            throw new IllegalArgumentException("warmupMinimoSeg deve ser >= 0");
        }
        long warmupSeg = Math.max(warmupMinimoSeg,
                (long) Math.ceil(duracaoSeg * FRACAO_WARMUP_MINIMA));
        long restanteSeg = Math.max(0, duracaoSeg - warmupSeg);
        long descarteSeg = (long) Math.ceil(restanteSeg * FRACAO_DESCARTE);
        long warmupAteMs = inicioMs + warmupSeg * 1000L;
        long medicaoInicioMs = warmupAteMs + descarteSeg * 1000L;
        long medicaoFimMs = inicioMs + duracaoSeg * 1000L;
        return new WarmupPolicy(warmupAteMs, medicaoInicioMs, medicaoFimMs, clock);
    }

    /**
     * Política sem warm-up, registra sempre. Útil para tests unitários
     * de {@link LatencyRegistry} que não dependem de timing.
     */
    public static WarmupPolicy always() {
        return new WarmupPolicy(Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
                Clock.systemUTC());
    }

    /**
     * Política totalmente desabilitada: nunca registra. Útil para
     * smoke-tests sem efeito colateral no histograma.
     */
    public static WarmupPolicy never() {
        return new WarmupPolicy(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                Clock.systemUTC());
    }

    /** Marca temporal a partir da qual a medição registra latências. */
    public long medicaoInicioMs() { return medicaoInicioMs; }

    /** Marca temporal a partir da qual o warm-up termina. */
    public long warmupAteMs() { return warmupAteMs; }

    /** Marca temporal de encerramento nominal da medição. */
    public long medicaoFimMs() { return medicaoFimMs; }

    /** {@code true} sse o instante atual está na fase de warm-up. */
    public boolean emWarmup(long nowMs) {
        return nowMs < warmupAteMs;
    }

    /** {@code true} sse o instante atual está na fase de descarte. */
    public boolean emDescarte(long nowMs) {
        return nowMs >= warmupAteMs && nowMs < medicaoInicioMs;
    }

    /** {@code true} sse o instante atual está na janela de medição. */
    public boolean emMedicao(long nowMs) {
        return nowMs >= medicaoInicioMs && nowMs < medicaoFimMs;
    }

    /** Atalho usado pelo {@link LatencyRegistry} para decidir gravação. */
    public boolean deveRegistrar() {
        return emMedicao(clock.millis());
    }
}
