package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-43.3a (parte i), teste S1/S2: regressao de junho. A execucao
 * {@code runs/S1-F1/rep-001.csv} do baseline de 2026-06-22 tem 0 Login OK e
 * 0 Validate VALID (C1/C2 do diagnostico de 2026-10-22). O verificador strict
 * precisa reprova-la com saida 4.
 */
class SanidadeRegressaoJunhoTest {

    static final String RECORTE = "/sanidade/S1-F1-rep-001-head.csv";

    @TempDir
    Path tmp;

    static Path recurso(String nome) throws Exception {
        return Paths.get(SanidadeRegressaoJunhoTest.class.getResource(nome).toURI());
    }

    static List<String> codigos(JsonNode json) {
        List<String> r = new ArrayList<>();
        json.get("motivos").forEach(m -> r.add(m.get("codigo").asText()));
        return r;
    }

    static int rodar(Path saida, String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        String[] comSaida = new String[args.length + 2];
        System.arraycopy(args, 0, comSaida, 0, args.length);
        comSaida[args.length] = "--saida";
        comSaida[args.length + 1] = saida.toString();
        return VerificadorSanidade.executar(comSaida,
                new PrintStream(buf, true, StandardCharsets.UTF_8));
    }

    @Test
    void s1F1Rep001DaFalha() throws Exception {
        Path saida = tmp.resolve("sanidade.json");
        int codigo = rodar(saida, "--csv", recurso(RECORTE).toString(), "--scenario", "S1");

        assertEquals(4, codigo, "regressao de junho tem de sair com 4");
        JsonNode json = new ObjectMapper().readTree(saida.toFile());
        assertEquals("FALHA", json.get("veredito").asText());
        List<String> motivos = codigos(json);
        assertTrue(motivos.contains("LOGIN_OK_BAIXO"), motivos.toString());
        assertTrue(motivos.contains("VALIDATE_VALID_ZERO"), motivos.toString());
    }

    /**
     * S2: o arquivo completo (57 MB), fora do repositorio. Ativado por
     * {@code -Dsanidade.csvJunho=<caminho>}.
     */
    @Test
    @EnabledIfSystemProperty(named = "sanidade.csvJunho", matches = ".+")
    void s1F1Rep001CompletoContagensExatas() throws Exception {
        Path csv = Paths.get(System.getProperty("sanidade.csvJunho"));
        Path saida = tmp.resolve("sanidade-completo.json");
        int codigo = rodar(saida, "--csv", csv.toString(), "--scenario", "S1");

        assertEquals(4, codigo);
        JsonNode json = new ObjectMapper().readTree(saida.toFile());
        assertEquals("FALHA", json.get("veredito").asText());
        assertEquals("5ea464aeb2b0ffad738f63fb5cb30e79a0f447a9195825828659eea0023aa198",
                json.get("sha256").asText());
        List<String> motivos = codigos(json);
        assertTrue(motivos.contains("LOGIN_OK_BAIXO"), motivos.toString());
        assertTrue(motivos.contains("VALIDATE_VALID_ZERO"), motivos.toString());

        JsonNode fora = json.get("contagens").get("fora");
        assertEquals(161_064, fora.path("Login").path("BLOCKED").asLong());
        assertEquals(320_336, fora.path("Validate").path("NONE").asLong());
        assertEquals(64_922, fora.path("Logout").path("NONE").asLong());
        assertEquals(63_905, fora.path("IncrementFailure").path("COUNTED").asLong());
        assertEquals(32_402, fora.path("ResetFailures").path("NONE").asLong());
        assertEquals(0, fora.path("Login").path("OK").asLong());
        assertEquals(0, fora.path("Validate").path("VALID").asLong());
        assertEquals(642_629, json.get("metricas").get("eventos_fora").asLong());
        assertEquals(642_629, json.get("metricas").get("eventos_total").asLong());
    }
}
