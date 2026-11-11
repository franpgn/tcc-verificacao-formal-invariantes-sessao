package br.unipampa.tcc.controle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * B-43.9 (V-B43.9, ponto (2); DT-002): os rotulos que o auditor passou a registrar para leituras VALID
 * sob BLOCKED fora de {@code S_viol} ({@code I2_JANELA_CASCATA}, {@code I2_CASCATA_INCOMPLETA}) vao para
 * {@link ClasseViolacao#FORA_DO_GATILHO} qualquer que seja a classe do rastro, e nao disparam R1. Fixa o
 * comportamento do {@code default} de {@link ClasseViolacao#de}: trocar o {@code switch} por um prefixo
 * "I2" quebraria o DT-002 sem este teste.
 */
class RotulosI2ForaDoGatilhoTest {

    @ParameterizedTest(name = "{0} com classe {1}")
    @CsvSource(nullValues = "NULO", value = {
        "I2_JANELA_CASCATA, NULO",
        "I2_JANELA_CASCATA, REAL",
        "I2_JANELA_CASCATA, ESPERADA-POR-DESENHO",
        "I2_CASCATA_INCOMPLETA, NULO",
        "I2_CASCATA_INCOMPLETA, REAL",
        "I2_CASCATA_INCOMPLETA, ESPERADA-POR-DESENHO",
    })
    void foraDoGatilho(String invariante, String classeI2) {
        ClasseViolacao c = ClasseViolacao.de(invariante, classeI2);
        assertEquals(ClasseViolacao.FORA_DO_GATILHO, c);
        assertFalse(c.gatilho());
    }
}
