package br.unipampa.tcc.controle;

import java.util.List;
import java.util.Objects;

/**
 * W-3 (B-28.2): saida da {@link PoliticaDeAcao} para uma janela. As reversoes sao entregues ao
 * executor antes das aplicacoes.
 *
 * <p>W-2a (B-44.2): a politica pode registrar qual politica de reacao escolheu na janela
 * ({@code P0}, {@code P1}, {@code P2}, documento de 05/11 secoes 4 e 5) e o motivo; o laco grava isso numa
 * linha {@code {"tipo":"politica",...}} do JSONL de decisoes. Com {@code politica = null} (as politicas do
 * W-3 e {@link #VAZIO}) nenhuma linha e gravada.</p>
 *
 * @param reverter acoes ativas a desfazer
 * @param aplicar  acoes a aplicar
 * @param politica politica de reacao escolhida ({@code P0}, {@code P1}, {@code P2}), ou {@code null}
 * @param motivo   motivo da escolha (sem espaco), ou {@code null}
 * @param no       V-7: rotulo do no da escolha (suspeito do P1, ausente do P2, no do AC-2 revertido), ou {@code null}
 */
public record PlanoDeAcao(List<AcaoCluster> reverter, List<AcaoCluster> aplicar, String politica, String motivo,
                          String no) {

    /** Nada a fazer. */
    public static final PlanoDeAcao VAZIO = new PlanoDeAcao(List.of(), List.of());

    public PlanoDeAcao {
        reverter = List.copyOf(Objects.requireNonNull(reverter, "reverter"));
        aplicar = List.copyOf(Objects.requireNonNull(aplicar, "aplicar"));
        if (politica == null && (motivo != null || no != null)) {
            throw new IllegalArgumentException("motivo ou no sem politica: " + motivo + " " + no);
        }
    }

    /** Plano com politica e motivo, sem no. */
    public PlanoDeAcao(List<AcaoCluster> reverter, List<AcaoCluster> aplicar, String politica, String motivo) {
        this(reverter, aplicar, politica, motivo, null);
    }

    /** Plano sem registro de politica (W-3). */
    public PlanoDeAcao(List<AcaoCluster> reverter, List<AcaoCluster> aplicar) {
        this(reverter, aplicar, null, null, null);
    }
}
