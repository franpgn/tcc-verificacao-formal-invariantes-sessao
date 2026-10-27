package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * B-26.2: classificacao da consulta a um no numa rodada da sonda.
 *
 * <p>{@code motivo} e um destes, sem virgula: {@code ok}, {@code timeout},
 * {@code falha:<NomeSimplesDaExcecao>}, {@code nos:<n><<esperados>}, {@code cache-ausente:<nome>},
 * {@code cache:<nome>=<status>}. Com mais de um problema vale o primeiro na ordem
 * timeout/falha &gt; nos &gt; cache-ausente &gt; cache.</p>
 *
 * @param no          rotulo do no
 * @param respondeu   {@code false} em timeout ou falha de consulta
 * @param numeroDeNos {@code number_of_nodes} lido, ou {@code -1} se nao respondeu
 * @param motivo      motivo da classificacao
 * @param anomalo     {@code true} se o no indica topologia anomala
 */
public record ResultadoNo(String no, boolean respondeu, int numeroDeNos, String motivo, boolean anomalo) {

    public ResultadoNo {
        Objects.requireNonNull(no, "no");
        Objects.requireNonNull(motivo, "motivo");
        if (motivo.isEmpty() || motivo.indexOf(',') >= 0) {
            throw new IllegalArgumentException("motivo vazio ou com virgula: '" + motivo + "'");
        }
        if (respondeu ? numeroDeNos < 0 : numeroDeNos != -1) {
            throw new IllegalArgumentException("numeroDeNos " + numeroDeNos + " incoerente com respondeu=" + respondeu);
        }
    }
}
