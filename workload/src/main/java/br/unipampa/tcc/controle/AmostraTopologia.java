package br.unipampa.tcc.controle;

import java.util.List;

/**
 * B-26.2: resultado de uma rodada da sonda de topologia.
 *
 * @param tNs       instante em que a rodada TERMINOU, na {@link FonteDeTempo} da sonda (o mesmo
 *                  relogio dos {@code end_ns}); a informacao so existe a partir dai
 * @param anomala   algum no anomalo
 * @param nosMinimo menor {@code numeroDeNos} entre os nos que responderam; 0 se nenhum respondeu
 * @param porNo     resultado de cada no, na ordem da fonte (copia imutavel)
 */
public record AmostraTopologia(long tNs, boolean anomala, int nosMinimo, List<ResultadoNo> porNo) {

    public AmostraTopologia {
        if (nosMinimo < 0) {
            throw new IllegalArgumentException("nosMinimo negativo: " + nosMinimo);
        }
        porNo = List.copyOf(porNo);
    }
}
