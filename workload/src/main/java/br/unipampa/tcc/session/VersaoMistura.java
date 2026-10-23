package br.unipampa.tcc.session;

/**
 * B-43.2: versao da mistura S1/S2 ({@code --mix v1|v2|v3}; B-43.7 acrescenta {@link #V3}).
 *
 * <ul>
 *   <li>{@link #V1}: comportamento anterior, uma identidade {@code user-<tid>} por thread para Login,
 *       IncrementFailure e ResetFailures, sem carga inicial. Reproduz C1 e C2 do diagnostico de
 *       2026-10-22 (mantida para regressao e contraste).</li>
 *   <li>{@link #V2} (padrao): pools disjuntos ({@link IdentidadesMistura}) e carga inicial dos
 *       {@code sid-NNNNNN} como sessoes VALID ({@link CargaInicial}).</li>
 * </ul>
 * A sequencia de operacoes e de chaves por semente e a mesma nas duas.
 */
public enum VersaoMistura {
    V1("v1"),
    V2("v2"),
    /**
     * B-43.7: mistura "slot-geracao" ({@link SlotsGeracao}, {@link CargaSlots}, {@link MisturaV3}). Login,
     * O4 e O5 como v2; Validate/Logout isolados sobre a sessao corrente do slot Zipf, com cada Validate
     * isolado lendo o token antigo por sorteio de Bernoulli p = 0,05 (B-43.7b, DT-008) e reposicao
     * dentro do Logout, fora do CSV.
     */
    V3("v3");

    private final String rotulo;

    VersaoMistura(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rotulo da CLI e do stdout ({@code v1}/{@code v2}). */
    public String rotulo() {
        return rotulo;
    }

    /** Conversao estrita: so {@code v1}, {@code v2} e {@code v3}, exatamente. */
    public static VersaoMistura porRotulo(String valor) {
        for (VersaoMistura v : values()) {
            if (v.rotulo.equals(valor)) {
                return v;
            }
        }
        throw new IllegalArgumentException("valor invalido '" + valor + "' para --mix (aceitos: v1, v2, v3)");
    }
}
