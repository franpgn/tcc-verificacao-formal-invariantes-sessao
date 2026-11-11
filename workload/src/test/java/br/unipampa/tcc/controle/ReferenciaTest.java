package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-26.1: p99 de referencia por operacao (plano de 2026-10-22, secao 3.2). */
class ReferenciaTest {

    @Test
    @DisplayName("guarda o p99 por operacao e diz quais operacoes sao elegiveis")
    void guardaP99() {
        Referencia r = Referencia.de(Map.of("Login", 2_000_000L, "Validate", 1_000_000L));
        assertTrue(r.contem("Login"));
        assertTrue(r.contem("Validate"));
        assertFalse(r.contem("Logout"));
        assertEquals(2_000_000L, r.p99Ns("Login"));
        assertEquals(1_000_000L, r.p99Ns("Validate"));
    }

    @Test
    @DisplayName("operacao fora da referencia: p99Ns lanca, contem e falso")
    void foraDaReferencia() {
        Referencia r = Referencia.de(Map.of("Login", 1L));
        assertThrows(IllegalArgumentException.class, () -> r.p99Ns("Validate"));
        assertFalse(Referencia.vazia().contem("Login"));
        assertEquals(Map.of(), Referencia.vazia().p99PorOperacao());
    }

    @Test
    @DisplayName("copia defensiva, imutavel e em ordem de nome (saida deterministica)")
    void copiaImutavelOrdenada() {
        Map<String, Long> m = new HashMap<>();
        m.put("Validate", 5L);
        m.put("Login", 7L);
        m.put("IncrementFailure", 9L);
        Referencia r = Referencia.de(m);
        m.put("Logout", 1L);
        assertFalse(r.contem("Logout"));
        assertEquals(List.of("IncrementFailure", "Login", "Validate"), List.copyOf(r.p99PorOperacao().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> r.p99PorOperacao().put("X", 1L));
        assertEquals(Referencia.de(Map.of("Login", 7L, "Validate", 5L, "IncrementFailure", 9L)), r);
    }

    @Test
    @DisplayName("rejeita p99 nao positivo, nome vazio e mapa nulo")
    void rejeitaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> Referencia.de(Map.of("Login", 0L)));
        assertThrows(IllegalArgumentException.class, () -> Referencia.de(Map.of("Login", -1L)));
        assertThrows(IllegalArgumentException.class, () -> Referencia.de(Map.of(" ", 1L)));
        assertThrows(NullPointerException.class, () -> Referencia.de(null));
        Map<String, Long> comNulo = new HashMap<>();
        comNulo.put("Login", null);
        assertThrows(IllegalArgumentException.class, () -> Referencia.de(comNulo));
    }
}
