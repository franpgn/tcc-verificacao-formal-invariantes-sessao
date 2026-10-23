package br.unipampa.tcc.session;

/**
 * B-43.9 (DT-002): marcos de um periodo de bloqueio de uma identidade, usados por
 * {@link SessionOps#validate} para classificar uma leitura de sessao VALID sob identidade BLOCKED
 * pelo instante do inicio da leitura da sessao ({@code System.nanoTime()}, o mesmo relogio dos
 * marcos).
 *
 * <ul>
 *   <li>{@code marcoIdentidadeNs}: instante logo DEPOIS do {@code put(BLOCKED)} reconhecido (no
 *       {@code block}, tipo {@link Tipo#ADMIN}; no {@code incrementFailure} que atinge o limite, tipo
 *       {@link Tipo#LIMITE}). {@link #PENDENTE} enquanto o {@code put} ainda esta em curso
 *       ({@link #emCurso}).</li>
 *   <li>{@code marcoCascataNs}: instante logo DEPOIS da ultima invalidacao da cascata; {@link #PENDENTE}
 *       enquanto a cascata nao terminou (ou se ela terminou em erro). No LIMITE (sem cascata), igual a
 *       {@code marcoIdentidadeNs}.</li>
 * </ul>
 *
 * <p>E estado do observador do experimento (como o mapa de invalidacoes confirmadas), nao da
 * aplicacao: nenhum desfecho de operacao depende dele.</p>
 */
record MarcoBloqueio(Tipo tipo, long marcoIdentidadeNs, long marcoCascataNs) {

    /** Origem do bloqueio. */
    enum Tipo { ADMIN, LIMITE }

    /** Marco ainda nao atingido. */
    static final long PENDENTE = Long.MAX_VALUE;

    /** Marco provisorio, gravado ANTES do {@code put(BLOCKED)}: identidade ainda nao confirmada. */
    static MarcoBloqueio emCurso(Tipo tipo) {
        return new MarcoBloqueio(tipo, PENDENTE, PENDENTE);
    }

    /** A identidade ja foi confirmada BLOCKED ({@code put} reconhecido ou efeito desconhecido). */
    boolean identidadeConfirmada() {
        return marcoIdentidadeNs != PENDENTE;
    }

    /** A cascata do periodo de bloqueio terminou sem erro (ou nao existe, no LIMITE). */
    boolean cascataConcluida() {
        return marcoCascataNs != PENDENTE;
    }
}
