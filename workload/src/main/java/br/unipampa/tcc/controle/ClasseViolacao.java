package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * Classe de uma violacao do auditor para o controlador (B-26.1; plano de 2026-10-22, achados A2 e
 * A3). So {@link #SEGURANCA} (I1, I4 e I2 classificada {@code REAL}) dispara R1.
 *
 * <p>A classe de I2 e a string devolvida por {@code RastroCliente.classificarViolacaoI2}
 * ({@code REAL}, {@code ESPERADA-POR-DESENHO}, {@code INCERTA}, {@code INEXPLICADA},
 * {@code DESCONHECIDA-SEM-LOGIN}) ou {@code null} quando nao ha rastro. Esta classe so mapeia
 * strings; quem chama a classificacao e o adaptador do bloco posterior.
 */
public enum ClasseViolacao {
    /** I1, I4 e I2 REAL: entram em {@code Janela.violacoesSeguranca} e disparam R1. */
    SEGURANCA,
    /** I2 por limite (P-27): so registro, em {@code Janela.violacoesI2Esperadas}. */
    I2_ESPERADA,
    /** I2 incerta, inexplicada, sem login ou sem rastro: so registro. */
    I2_FORA_DO_GATILHO,
    /** I3, I5, I6 e qualquer outro invariante: so registro (I5/I6 sao metrica de avaliacao). */
    FORA_DO_GATILHO;

    public static final String I2_REAL = "REAL";
    public static final String I2_ESPERADA_POR_DESENHO = "ESPERADA-POR-DESENHO";

    public static ClasseViolacao de(String invariante, String classeI2) {
        Objects.requireNonNull(invariante, "invariante");
        switch (invariante) {
            case "I1":
            case "I4":
                return SEGURANCA;
            case "I2":
                if (I2_REAL.equals(classeI2)) {
                    return SEGURANCA;
                }
                if (I2_ESPERADA_POR_DESENHO.equals(classeI2)) {
                    return I2_ESPERADA;
                }
                return I2_FORA_DO_GATILHO;
            default:
                return FORA_DO_GATILHO;
        }
    }

    /** Verdadeiro so para {@link #SEGURANCA}. */
    public boolean gatilho() {
        return this == SEGURANCA;
    }
}
