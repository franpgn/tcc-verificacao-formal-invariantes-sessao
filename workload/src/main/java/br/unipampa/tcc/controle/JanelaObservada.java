package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * Janela fechada pelo {@link AgregadorDeJanela} com os contadores que nao decidem, mas vao para o
 * {@code observaveis-rep-NNN.jsonl} (B-26.1; plano de 2026-10-22, secao 3.6).
 *
 * @param janela                    entrada do {@link Controlador#avaliar}
 * @param atrasados                 eventos de operacao descartados por atraso (regra C+A) desde a
 *                                  emissao anterior; nao pertencem a {@code janela}
 * @param violacoesAtrasadas        violacoes (de qualquer classe) descartadas por atraso desde a
 *                                  emissao anterior
 * @param violacoesI2ForaDoGatilho  I2 incerta, inexplicada, sem login ou sem rastro, na janela
 * @param violacoesForaDoGatilho    I3, I5, I6 e outros invariantes, na janela
 */
public record JanelaObservada(Janela janela, long atrasados, long violacoesAtrasadas,
                              long violacoesI2ForaDoGatilho, long violacoesForaDoGatilho) {

    public JanelaObservada {
        Objects.requireNonNull(janela, "janela");
        naoNegativo("atrasados", atrasados);
        naoNegativo("violacoesAtrasadas", violacoesAtrasadas);
        naoNegativo("violacoesI2ForaDoGatilho", violacoesI2ForaDoGatilho);
        naoNegativo("violacoesForaDoGatilho", violacoesForaDoGatilho);
    }

    private static void naoNegativo(String nome, long v) {
        if (v < 0) {
            throw new IllegalArgumentException(nome + " negativo: " + v);
        }
    }
}
