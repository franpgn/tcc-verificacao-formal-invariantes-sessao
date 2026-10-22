package br.unipampa.tcc.session;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * B-43.7: carga inicial da mistura v3 ("slot-geracao"), no padrao da {@link CargaInicial} (que e da
 * v2 e nao muda). Para cada slot {@code k = 1..universo} grava duas sessoes da mesma identidade
 * ({@link SlotsGeracao#identidade(int)}):
 * <ul>
 *   <li>{@code SlotsGeracao.chave(k, 0)} ({@code sid-NNNNNN}) INVALID com motivo LOGOUT: o
 *       <i>token</i> antigo ja existe e ja esta invalidado desde o primeiro evento (a fracao INVALID
 *       dos Validate isolados nasce estacionaria);</li>
 *   <li>{@code SlotsGeracao.chave(k, 1)} ({@code sid-NNNNNN~1}) VALID: a sessao corrente.</li>
 * </ul>
 *
 * <pre>{@code
 * putAll em lotes de `lote` entradas
 *   -> size() == 2 x universo?   -> Resultado
 *   -> size() >  2 x universo?   -> CargaInicialException (estado sujo; sem esperar)
 *   -> prazo vencido (desde o fim dos putAll)? -> CargaInicialException
 *   -> senao sono(intervalo) e consulta de novo (ASYNC)
 * }</pre>
 *
 * <p>O {@link CargaInicial.Resultado} devolvido tem {@code universo} = numero de slots; o numero de
 * entradas e {@link #ENTRADAS_POR_SLOT} x universo. Falhas viram {@link CargaInicialException}
 * (saida 5, sem {@code rep-NNN.csv}).</p>
 */
final class CargaSlots {

    /** Entradas gravadas por slot (geracao 0 INVALID e geracao 1 VALID). */
    static final int ENTRADAS_POR_SLOT = 2;

    private CargaSlots() { }

    /**
     * Executa a carga.
     *
     * @throws CargaInicialException ver a classe (causa preservada nas excecoes de {@code putAll}/{@code size})
     */
    static CargaInicial.Resultado carregar(CacheCarregavel sessoes, int universo, int threads, Instant agora,
                                           int lote, Duration prazo, Duration intervalo, LongSupplier relogioNs,
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
        long esperado = (long) ENTRADAS_POR_SLOT * universo;
        long t0 = relogioNs.getAsLong();
        int lotes = 0;
        Map<String, SessionState> buf = new HashMap<>(lote * 2);
        for (int k = 1; k <= universo; k++) {
            String dona = IdentidadesMistura.identidadeDaCarga(k, threads);
            String antiga = SlotsGeracao.chave(k, 0);
            String corrente = SlotsGeracao.chave(k, 1);
            buf.put(antiga, SessionState.criar(antiga, dona, agora).invalidar(SessionState.Motivo.LOGOUT, agora));
            if (buf.size() >= lote) {
                lotes = gravar(sessoes, buf, lotes, universo, relogioNs, t0);
                buf = new HashMap<>(lote * 2);
            }
            buf.put(corrente, SessionState.criar(corrente, dona, agora));
            if (buf.size() >= lote || k == universo) {
                lotes = gravar(sessoes, buf, lotes, universo, relogioNs, t0);
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
                        new CargaInicial.Resultado(universo, lotes, tamanho, ms(relogioNs.getAsLong() - t0)), e);
            }
            long agoraNs = relogioNs.getAsLong();
            if (tamanho == esperado) {
                return new CargaInicial.Resultado(universo, lotes, tamanho, ms(agoraNs - t0));
            }
            if (tamanho > esperado) {
                throw new CargaInicialException("tamanho diferente de 2 x slots em " + sessoes.nome()
                        + ": size()=" + tamanho + " > " + esperado + " (estado nao vazio antes da carga)",
                        new CargaInicial.Resultado(universo, lotes, tamanho, ms(agoraNs - t0)), null);
            }
            long decorrido = agoraNs - inicioEspera;
            if (decorrido >= prazoNs) {
                throw new CargaInicialException("carga de slots nao chegou a 2 x slots no prazo de "
                        + prazo.toMillis() + " ms em " + sessoes.nome() + ": size()=" + tamanho + " de " + esperado
                        + " (tamanho diferente de 2 x slots)",
                        new CargaInicial.Resultado(universo, lotes, tamanho, ms(agoraNs - t0)), null);
            }
            try {
                sono.dormir(Duration.ofNanos(Math.min(intervalo.toNanos(), prazoNs - decorrido)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CargaInicialException("carga de slots interrompida em " + sessoes.nome(),
                        new CargaInicial.Resultado(universo, lotes, tamanho, ms(relogioNs.getAsLong() - t0)), e);
            }
        }
    }

    private static int gravar(CacheCarregavel sessoes, Map<String, SessionState> buf, int lotes, int universo,
                              LongSupplier relogioNs, long t0) throws CargaInicialException {
        try {
            sessoes.putAll(buf);
        } catch (RuntimeException e) {
            throw new CargaInicialException("putAll falhou em " + sessoes.nome() + " no lote " + (lotes + 1)
                    + ": " + e,
                    new CargaInicial.Resultado(universo, lotes, null, ms(relogioNs.getAsLong() - t0)), e);
        }
        return lotes + 1;
    }

    private static long ms(long ns) {
        return ns / 1_000_000L;
    }
}
