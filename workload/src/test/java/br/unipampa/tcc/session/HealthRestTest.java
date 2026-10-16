package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 3): interpretacao do JSON de saude do REST do Infinispan
 * ({@code /rest/v2/cache-managers/default/health}). Os nomes de campo abaixo
 * sao os documentados pelo Infinispan 15 e serao confirmados no gate G-3 (T-B).
 */
class HealthRestTest {

    private static final String SAUDAVEL = """
            {"cluster_health":{"cluster_name":"cluster","health_status":"HEALTHY",
             "number_of_nodes":3,"node_names":["isn1","isn2","isn3"]},
             "cache_health":[{"status":"HEALTHY","cache_name":"sessions"},
                             {"status":"HEALTHY","cache_name":"counters"}]}
            """;

    @Test
    @DisplayName("cluster saudavel: 3 nos e os dois caches HEALTHY")
    void saudavel() {
        HealthRest.Estado e = HealthRest.interpretar(SAUDAVEL);
        assertEquals(3, e.numeroDeNos());
        assertTrue(e.healthy("sessions"));
        assertTrue(e.healthy("counters"));
    }

    @Test
    @DisplayName("cache DEGRADED nao e healthy; cache ausente tambem nao")
    void degradadoOuAusente() {
        String json = SAUDAVEL.replace("\"status\":\"HEALTHY\",\"cache_name\":\"sessions\"",
                "\"status\":\"DEGRADED\",\"cache_name\":\"sessions\"");
        HealthRest.Estado e = HealthRest.interpretar(json);
        assertFalse(e.healthy("sessions"));
        assertTrue(e.healthy("counters"));
        assertFalse(e.healthy("inexistente"));
    }

    @Test
    @DisplayName("no isolado: 1 no")
    void umNo() {
        String json = SAUDAVEL.replace("\"number_of_nodes\":3", "\"number_of_nodes\":1");
        assertEquals(1, HealthRest.interpretar(json).numeroDeNos());
    }

    @Test
    @DisplayName("JSON invalido ou sem cluster_health e erro (a rodada sera invalida)")
    void invalido() {
        assertThrows(IllegalArgumentException.class, () -> HealthRest.interpretar("<html>401</html>"));
        assertThrows(IllegalArgumentException.class, () -> HealthRest.interpretar("{}"));
    }

    @Test
    @DisplayName("configuracao do curl nao leva a senha na linha de comando e escapa aspas")
    void configCurl() {
        String cfg = HealthRest.configCurl("admin", "se\"nha\\x");
        assertTrue(cfg.contains("user = \"admin:se\\\"nha\\\\x\""), cfg);
    }
}
