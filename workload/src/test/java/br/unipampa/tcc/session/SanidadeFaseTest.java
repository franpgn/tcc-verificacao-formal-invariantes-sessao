package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-43.3a (parte i), S5: coluna {@code phase}. As regras de mistura valem so na
 * regiao "fora" ({@code antes}, {@code pos-convergencia}); na regiao "dentro"
 * ({@code durante*}, {@code recuperacao}) so DENTRO_SEM_VALID.
 */
class SanidadeFaseTest {

    @TempDir
    Path tmp;

    /** Fixture sa dividida: primeira metade "antes", segunda "pos-convergencia". */
    private static List<FixtureSanidade.Ev> foraSa() {
        List<FixtureSanidade.Ev> sa = FixtureSanidade.sa(Scenario.S1, 42L, 20_000);
        List<FixtureSanidade.Ev> r = new ArrayList<>();
        for (int i = 0; i < sa.size(); i++) {
            r.add(sa.get(i).naFase(i < sa.size() / 2 ? "antes" : "pos-convergencia"));
        }
        return r;
    }

    private static List<FixtureSanidade.Ev> dentro(String fase, String[][] pares, int vezes) {
        List<FixtureSanidade.Ev> r = new ArrayList<>();
        for (int i = 0; i < vezes; i++) {
            for (String[] p : pares) {
                r.add(new FixtureSanidade.Ev(p[0], p[1], fase));
            }
        }
        return r;
    }

    private JsonNode rodar(List<FixtureSanidade.Ev> evs, int saidaEsperada) throws Exception {
        Path csv = FixtureSanidade.gravar(tmp, "f.csv", evs, true);
        Path json = tmp.resolve("f.json");
        int codigo = VerificadorSanidade.executar(
                new String[] {"--csv", csv.toString(), "--scenario", "S1", "--saida", json.toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertEquals(saidaEsperada, codigo);
        return new ObjectMapper().readTree(json.toFile());
    }

    @Test
    void regioes() {
        assertEquals(Sanidade.Regiao.FORA, Sanidade.regiaoDe(null));
        assertEquals(Sanidade.Regiao.FORA, Sanidade.regiaoDe("antes"));
        assertEquals(Sanidade.Regiao.FORA, Sanidade.regiaoDe("pos-convergencia"));
        assertEquals(Sanidade.Regiao.DENTRO, Sanidade.regiaoDe("durante"));
        assertEquals(Sanidade.Regiao.DENTRO, Sanidade.regiaoDe("durante-pre-split"));
        assertEquals(Sanidade.Regiao.DENTRO, Sanidade.regiaoDe("durante-pos-split"));
        assertEquals(Sanidade.Regiao.DENTRO, Sanidade.regiaoDe("recuperacao"));
        assertThrows(IllegalArgumentException.class, () -> Sanidade.regiaoDe("DURANTE"));
    }

    @Test
    void falhaSoEmDuranteNaoDerrubaFora() throws Exception {
        List<FixtureSanidade.Ev> evs = new ArrayList<>(foraSa());
        // Dentro: muito BLOCKED e NONE, mas com algum VALID. Nao entra nas regras de fora.
        evs.addAll(dentro("durante-pos-split", new String[][] {
                {"Login", "BLOCKED"}, {"Validate", "NONE"}, {"Validate", "NONE"},
                {"Validate", "VALID"}, {"Login", "ERROR_UNAVAILABLE"}}, 3_000));
        JsonNode j = rodar(evs, 0);
        assertEquals("OK", j.get("veredito").asText(), j.get("motivos").toString());
        assertEquals(15_000, j.get("metricas").get("eventos_dentro").asLong());
        assertEquals(3_000, j.get("contagens").get("dentro").get("Validate").get("VALID").asLong());
    }

    @Test
    void dentroSemValidComEventosNaoErrorDaDentroSemValid() throws Exception {
        List<FixtureSanidade.Ev> evs = new ArrayList<>(foraSa());
        evs.addAll(dentro("recuperacao", new String[][] {
                {"Validate", "NONE"}, {"Login", "ERROR_TRANSPORT"}}, 100));
        JsonNode j = rodar(evs, 4);
        List<String> codigos = new ArrayList<>();
        j.get("motivos").forEach(m -> codigos.add(m.get("codigo").asText()));
        assertEquals(List.of("DENTRO_SEM_VALID"), codigos);
    }

    @Test
    void dentroTodoErrorNaoDaDentroSemValid() throws Exception {
        List<FixtureSanidade.Ev> evs = new ArrayList<>(foraSa());
        evs.addAll(dentro("durante", new String[][] {
                {"Validate", "ERROR_UNAVAILABLE"}, {"Login", "ERROR_TRANSPORT"},
                {"IncrementFailure", "ERROR_UNAVAILABLE"}}, 100));
        JsonNode j = rodar(evs, 0);
        assertEquals("OK", j.get("veredito").asText(), j.get("motivos").toString());
    }

    @Test
    void semEventosDentroNaoAvaliaDentro() {
        Sanidade.Resultado r = FixtureSanidade.avaliar(Scenario.S1, foraSa());
        assertEquals(Sanidade.Veredito.OK, r.veredito());
        assertFalse(r.temMotivo("DENTRO_SEM_VALID"));
    }

    @Test
    void foraRuimNaoEMascaradoPorDentroBom() {
        // Fora com 0 VALID; dentro com VALID: a falha de fora continua.
        List<FixtureSanidade.Ev> evs = new ArrayList<>();
        for (FixtureSanidade.Ev e : foraSa()) {
            evs.add(e.op().equals("Validate") ? e.com("NONE") : e);
        }
        evs.addAll(dentro("durante", new String[][] {{"Validate", "VALID"}}, 1_000));
        Sanidade.Resultado r = FixtureSanidade.avaliar(Scenario.S1, evs);
        assertTrue(r.temMotivo("VALIDATE_VALID_ZERO"), r.motivos().toString());
        assertFalse(r.temMotivo("DENTRO_SEM_VALID"));
    }
}
