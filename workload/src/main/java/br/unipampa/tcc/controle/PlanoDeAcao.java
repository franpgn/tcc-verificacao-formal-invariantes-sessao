package br.unipampa.tcc.controle;

import java.util.List;
import java.util.Objects;

/**
 * W-3 (B-28.2): saida da {@link PoliticaDeAcao} para uma janela. As reversoes sao entregues ao
 * executor antes das aplicacoes.
 *
 * @param reverter acoes ativas a desfazer
 * @param aplicar  acoes a aplicar
 */
public record PlanoDeAcao(List<AcaoCluster> reverter, List<AcaoCluster> aplicar) {

    /** Nada a fazer. */
    public static final PlanoDeAcao VAZIO = new PlanoDeAcao(List.of(), List.of());

    public PlanoDeAcao {
        reverter = List.copyOf(Objects.requireNonNull(reverter, "reverter"));
        aplicar = List.copyOf(Objects.requireNonNull(aplicar, "aplicar"));
    }
}
