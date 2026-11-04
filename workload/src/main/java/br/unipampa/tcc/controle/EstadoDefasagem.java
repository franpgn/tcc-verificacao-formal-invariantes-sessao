package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * Resumo da sonda de defasagem de replicacao ({@code S_lag}) numa janela (W-3/B-28.2; plano de
 * execucao de 2026-11-04, secoes 4.2 e 4.5). A sonda ativa e o W-4 (B-26.3): ela compara o
 * {@code lag} medido com {@code lag_in}/{@code lag_out} (histerese) e entrega aqui so o nivel. O nucleo
 * trata o nivel como trata a latencia: {@link Nivel#ALTA} conta para a persistencia k (R2) e zera as
 * calmas (R3); {@link Nivel#INTERMEDIARIA} nao e alta nem calma; R4 exige {@link #calmaOuIndeterminada()}.
 *
 * <p>Ate o W-4 entrar nao ha sonda, e toda janela leva {@link #indeterminada()}.</p>
 *
 * @param nivel      nivel da defasagem na janela
 * @param noSuspeito rotulo do no de maior defasagem, ou {@code null} (so registro e politica de acao)
 * @param lagMaxMs   maior defasagem medida na janela, em ms, ou {@code null} sem amostra
 * @param amostras   amostras da sonda na janela
 */
public record EstadoDefasagem(Nivel nivel, String noSuspeito, Double lagMaxMs, int amostras) {

    /** Nivel da defasagem na janela. */
    public enum Nivel {
        /** Sem sonda ou amostra insuficiente: sinal neutro. */
        INDETERMINADA,
        /** {@code lag <= lag_out}. */
        CALMA,
        /** Entre {@code lag_out} e {@code lag_in}: nem alta nem calma (histerese). */
        INTERMEDIARIA,
        /** {@code lag >= lag_in}. */
        ALTA
    }

    private static final EstadoDefasagem INDETERMINADA = new EstadoDefasagem(Nivel.INDETERMINADA, null, null, 0);

    public EstadoDefasagem {
        Objects.requireNonNull(nivel, "nivel");
        if (amostras < 0) {
            throw new IllegalArgumentException("amostras negativo: " + amostras);
        }
        if (lagMaxMs != null && (!Double.isFinite(lagMaxMs) || lagMaxMs < 0)) {
            throw new IllegalArgumentException("lagMaxMs deve ser finito e nao negativo: " + lagMaxMs);
        }
    }

    /** Estado neutro: nenhuma informacao de defasagem. */
    public static EstadoDefasagem indeterminada() {
        return INDETERMINADA;
    }

    /** Sinal alto nesta janela. */
    public boolean alta() {
        return nivel == Nivel.ALTA;
    }

    /** Nao impede a janela de ser calma (R4). */
    public boolean calmaOuIndeterminada() {
        return nivel == Nivel.CALMA || nivel == Nivel.INDETERMINADA;
    }
}
