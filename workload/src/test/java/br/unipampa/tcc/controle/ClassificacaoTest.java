package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B-26.1: classificacao de {@code return_code} (contrato de {@code OpResult.returnCode()}) e das
 * violacoes do auditor (gatilho so com I1, I4 e I2 REAL; I2 por limite so registro; I3, I5, I6
 * fora do gatilho).
 */
class ClassificacaoTest {

    @ParameterizedTest(name = "{0}: erro={1}, indisponivel={2}")
    @CsvSource({
            "OK,                false, false",
            "VALID,             false, false",
            "COUNTED,           false, false",
            "NONE,              false, false",
            "BLOCKED,           false, false",
            "ERROR_TRANSPORT,   true,  false",
            "ERROR_UNAVAILABLE, true,  true",
    })
    @DisplayName("cada return_code do CSV e classificado")
    void codigos(String codigo, boolean erro, boolean indisponivel) {
        EventoOperacao e = new EventoOperacao("Login", 10, 20, codigo);
        assertEquals(erro, e.erro());
        assertEquals(indisponivel, e.indisponivel());
        assertEquals(10, e.latenciaNs());
    }

    @Test
    @DisplayName("o conjunto de codigos aceitos e exatamente o do contrato do CSV")
    void conjuntoDeCodigos() {
        assertEquals(List.of("OK", "VALID", "COUNTED", "NONE", "BLOCKED", "ERROR_TRANSPORT", "ERROR_UNAVAILABLE"),
                EventoOperacao.CODIGOS);
    }

    @Test
    @DisplayName("evento invalido e rejeitado: codigo desconhecido, fim antes do inicio, nulos")
    void eventoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new EventoOperacao("Login", 0, 1, "ERROR_TIMEOUT"));
        assertThrows(IllegalArgumentException.class, () -> new EventoOperacao("Login", 0, 1, "ok"));
        assertThrows(IllegalArgumentException.class, () -> new EventoOperacao("Login", 5, 4, "OK"));
        assertThrows(IllegalArgumentException.class, () -> new EventoOperacao(" ", 0, 1, "OK"));
        assertThrows(NullPointerException.class, () -> new EventoOperacao(null, 0, 1, "OK"));
        assertThrows(NullPointerException.class, () -> new EventoOperacao("Login", 0, 1, null));
        assertEquals(0, new EventoOperacao("Login", 7, 7, "OK").latenciaNs());
    }

    @ParameterizedTest(name = "{0}/{1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "I1, null,                   SEGURANCA",
            "I4, null,                   SEGURANCA",
            "I1, REAL,                   SEGURANCA",
            "I2, REAL,                   SEGURANCA",
            "I2, ESPERADA-POR-DESENHO,   I2_ESPERADA",
            "I2, INCERTA,                I2_FORA_DO_GATILHO",
            "I2, INEXPLICADA,            I2_FORA_DO_GATILHO",
            "I2, DESCONHECIDA-SEM-LOGIN, I2_FORA_DO_GATILHO",
            "I2, null,                   I2_FORA_DO_GATILHO",
            "I3, null,                   FORA_DO_GATILHO",
            "I5, null,                   FORA_DO_GATILHO",
            "I6, null,                   FORA_DO_GATILHO",
            "I2-PERDIDO, REAL,           FORA_DO_GATILHO",
    })
    @DisplayName("violacoes: so I1, I4 e I2 REAL entram no gatilho")
    void violacoes(String invariante, String classeI2, ClasseViolacao esperada) {
        assertEquals(esperada, ClasseViolacao.de(invariante, classeI2));
    }

    @Test
    @DisplayName("so SEGURANCA dispara")
    void soSegurancaDispara() {
        for (ClasseViolacao c : ClasseViolacao.values()) {
            assertEquals(c == ClasseViolacao.SEGURANCA, c.gatilho(), c.name());
        }
        assertThrows(NullPointerException.class, () -> ClasseViolacao.de(null, "REAL"));
    }
}
