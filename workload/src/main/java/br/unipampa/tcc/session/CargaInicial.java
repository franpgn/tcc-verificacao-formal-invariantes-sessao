package br.unipampa.tcc.session;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * B-43.2 (diagnostico de 2026-10-22, causa C2): carga inicial da mistura v2. Grava
 * {@code sid-000001..sid-<universo>} como sessoes VALID antes do <i>warm-up</i>, para que os
 * {@code Validate}/{@code Logout} isolados de S1/S2 (chaves Zipf do {@link KeyGenerator}) leiam
 * sessoes que existem.
 *
 * <pre>{@code
 * putAll em lotes de `lote` (chave KeyGenerator.PREFIXO_SID + %06d, dona identidadeDaCarga(i, threads))
 *   -> size() == universo?            -> Resultado
 *   -> size() >  universo?            -> CargaInicialException (estado sujo; sem esperar)
 *   -> prazo vencido (desde o fim dos putAll)? -> CargaInicialException
 *   -> senao sono(intervalo) e consulta de novo (ASYNC)
 * }</pre>
 *
 * <p>O prazo conta a partir do fim dos {@code putAll} (a fase de espera), nao do inicio: o tempo de
 * gravar 10<sup>5</sup> entradas depende do <i>host</i> e nao deve consumir a espera da replicacao.
 * A duracao relatada cobre a carga inteira. Pressupoe os caches vazios (o reset de B-43.1 roda
 * antes; {@code --mix v2} exige {@code --reset-state on}).</p>
 */
final class CargaInicial {

    /** Entradas por {@code putAll}. */
    static final int LOTE_PADRAO = 1000;

    /** Mesmos padroes do {@link ResetEstado}. */
    static final Duration PRAZO_PADRAO = ResetEstado.PRAZO_PADRAO;
    static final Duration INTERVALO_PADRAO = ResetEstado.INTERVALO_PADRAO;

    private CargaInicial() { }

    /**
     * Resultado da carga. {@code tamanhoFinal} e o ultimo {@code size()} lido ({@code null} se nenhum
     * terminou); {@code duracaoMs} cobre {@code putAll} e espera.
     */
    record Resultado(int universo, int lotes, Integer tamanhoFinal, long duracaoMs) { }

    /** Sobrecarga de producao: universo do {@link KeyGenerator}, lote de 1000, 10 s / 100 ms. */
    static Resultado carregar(CacheCarregavel sessoes, int threads, Instant agora) throws CargaInicialException {
        return carregar(sessoes, KeyGenerator.TAMANHO_PADRAO_UNIVERSO, threads, agora, LOTE_PADRAO,
                PRAZO_PADRAO, INTERVALO_PADRAO, System::nanoTime, ResetEstado.Sono.real());
    }

    /**
     * Executa a carga.
     *
     * @throws CargaInicialException ver a classe (causa preservada nas excecoes de {@code putAll}/{@code size})
     */
    static Resultado carregar(CacheCarregavel sessoes, int universo, int threads, Instant agora, int lote,
                              Duration prazo, Duration intervalo, LongSupplier relogioNs,
                              ResetEstado.Sono sono) throws CargaInicialException {
        if (universo < 1) {
            throw new IllegalArgumentException("universo deve ser >= 1: " + universo);
        }
        if (threads < 1) {
            throw new IllegalArgumentException("threads deve ser >= 1: " + threads);
        }
        if (lote < 1) {
            throw new IllegalArgumentException("lote deve ser >= 1: " + lote);
        }
        if (prazo == null || prazo.isNegative() || prazo.isZero()) {
            throw new IllegalArgumentException("prazo deve ser > 0: " + prazo);
        }
        if (intervalo == null || intervalo.isNegative() || intervalo.isZero()) {
            throw new IllegalArgumentException("intervalo deve ser > 0: " + intervalo);
        }
        long t0 = relogioNs.getAsLong();
        int lotes = 0;
        Map<String, SessionState> buf = new HashMap<>(lote * 2);
        for (int i = 1; i <= universo; i++) {
            String sid = String.format("%s%06d", KeyGenerator.PREFIXO_SID, i);
            buf.put(sid, SessionState.criar(sid, IdentidadesMistura.identidadeDaCarga(i, threads), agora));
            if (buf.size() == lote || i == universo) {
                try {
                    sessoes.putAll(buf);
                } catch (RuntimeException e) {
                    throw new CargaInicialException("putAll falhou em " + sessoes.nome() + " no lote "
                            + (lotes + 1) + ": " + e,
                            new Resultado(universo, lotes, null, ms(relogioNs.getAsLong() - t0)), e);
                }
                lotes++;
                buf = new HashMap<>(lote * 2);
            }
        }
        long inicioEspera = relogioNs.getAsLong();
        long prazoNs = prazo.toNanos();
        Integer tamanho = null;
        while (true) {
            try {
                tamanho = sessoes.size();
            } catch (RuntimeException e) {
                throw new CargaInicialException("size() falhou em " + sessoes.nome() + ": " + e,
                        new Resultado(universo, lotes, tamanho, ms(relogioNs.getAsLong() - t0)), e);
            }
            long agoraNs = relogioNs.getAsLong();
            if (tamanho == universo) {
                return new Resultado(universo, lotes, tamanho, ms(agoraNs - t0));
            }
            if (tamanho > universo) {
                throw new CargaInicialException("tamanho diferente do universo em " + sessoes.nome()
                        + ": size()=" + tamanho + " > " + universo + " (estado nao vazio antes da carga)",
                        new Resultado(universo, lotes, tamanho, ms(agoraNs - t0)), null);
            }
            long decorrido = agoraNs - inicioEspera;
            if (decorrido >= prazoNs) {
                throw new CargaInicialException("carga nao chegou ao universo no prazo de " + prazo.toMillis()
                        + " ms em " + sessoes.nome() + ": size()=" + tamanho + " de " + universo
                        + " (tamanho diferente do universo)",
                        new Resultado(universo, lotes, tamanho, ms(agoraNs - t0)), null);
            }
            try {
                sono.dormir(Duration.ofNanos(Math.min(intervalo.toNanos(), prazoNs - decorrido)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CargaInicialException("carga interrompida em " + sessoes.nome(),
                        new Resultado(universo, lotes, tamanho, ms(relogioNs.getAsLong() - t0)), e);
            }
        }
    }

    private static long ms(long ns) {
        return ns / 1_000_000L;
    }
}
