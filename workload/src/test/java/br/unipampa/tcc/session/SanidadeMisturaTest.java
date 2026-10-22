package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * B-43.3a (parte i), S3 e S4: a fixture sa sintetica passa (saida 0) e cada
 * regra dispara no seu caso.
 */
class SanidadeMisturaTest {

    static final long SEMENTE = 42L;
    static final int N_OPS = 20_000;

    @TempDir
    Path tmp;

    private int rodarCsv(Path csv, Scenario s, Path json) {
        return VerificadorSanidade.executar(
                new String[] {"--csv", csv.toString(), "--scenario", s.name(), "--saida", json.toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private static List<FixtureSanidade.Ev> sa(Scenario s) {
        return FixtureSanidade.sa(s, SEMENTE, N_OPS);
    }

    // ---------- S3: fixture sa ----------

    @ParameterizedTest
    @EnumSource(value = Scenario.class, names = {"S1", "S2"})
    void fixtureSaDaZeroOkSemMotivos(Scenario s) throws Exception {
        Path csv = FixtureSanidade.gravar(tmp, "sa-" + s + ".csv", sa(s), false);
        Path json = tmp.resolve("sa-" + s + ".json");

        assertEquals(0, rodarCsv(csv, s, json));
        JsonNode j = new ObjectMapper().readTree(json.toFile());
        assertEquals("OK", j.get("veredito").asText());
        assertEquals(0, j.get("motivos").size(), j.get("motivos").toString());
        assertEquals(s.name(), j.get("cenario").asText());
        assertEquals(0.95, j.get("limiares").get("login_ok_min").asDouble());
        assertEquals(0.05, j.get("limiares").get("validate_ausente_max").asDouble());
        assertEquals(2.0, j.get("limiares").get("mistura_tolerancia_pp").asDouble());
        assertEquals(0, j.get("limiares").get("validate_valid_min_exclusivo").asInt());
        assertEquals(64, j.get("sha256").asText().length());
        assertEquals(Sanidade.VERSAO, j.get("versao").asText());
    }

    @ParameterizedTest
    @EnumSource(value = Scenario.class, names = {"S1", "S2"})
    void fixtureSaOpsDescontaOsDoisEventosExtrasDoLoginOk(Scenario s) {
        Sanidade.Resultado r = FixtureSanidade.avaliar(s, sa(s));
        assertEquals((long) N_OPS, r.metricas().get("ops"));
    }

    // ---------- S4: um caso por regra ----------

    @Test
    void noventaPorCentoDeLoginOkDaLoginOkBaixo() {
        List<FixtureSanidade.Ev> evs =
                FixtureSanidade.comLoginBloqueado(Scenario.S1, SEMENTE, N_OPS, k -> k % 10 == 0);
        Sanidade.Resultado r = FixtureSanidade.avaliar(Scenario.S1, evs);
        assertEquals(Sanidade.Veredito.FALHA, r.veredito());
        assertTrue(r.temMotivo("LOGIN_OK_BAIXO"), r.motivos().toString());
        assertFalse(r.temMotivo("MISTURA_DESVIO"), "ops desconta so os Login OK: " + r.motivos());
    }

    @Test
    void loginOkNaFronteiraExataNaoDispara() {
        // 19/20 = 0,95 exatamente: "< loginOkMin" nao dispara (mata V1, "<" -> "<=").
        Sanidade san = new Sanidade();
        for (int i = 0; i < 19; i++) {
            san.registrar("Login", "OK", null);
        }
        san.registrar("Login", "BLOCKED", null);
        san.registrar("Validate", "VALID", null);
        Sanidade.Resultado r = san.avaliar(Scenario.S1, Sanidade.Limiares.provisorios());
        assertEquals(0.95, (double) r.metricas().get("login_ok_frac"));
        assertFalse(r.temMotivo("LOGIN_OK_BAIXO"), r.motivos().toString());
    }

    @Test
    void loginAusenteDaLoginAusente() {
        Sanidade san = new Sanidade();
        san.registrar("Validate", "VALID", null);
        Sanidade.Resultado r = san.avaliar(Scenario.S1, Sanidade.Limiares.provisorios());
        assertTrue(r.temMotivo("LOGIN_AUSENTE"), r.motivos().toString());
        assertFalse(r.temMotivo("LOGIN_OK_BAIXO"));
    }

    @Test
    void nenhumValidDaValidateValidZero() {
        List<FixtureSanidade.Ev> evs = new ArrayList<>();
        for (FixtureSanidade.Ev e : sa(Scenario.S1)) {
            evs.add(e.op().equals("Validate") ? e.com("NONE") : e);
        }
        Sanidade.Resultado r = FixtureSanidade.avaliar(Scenario.S1, evs);
        assertTrue(r.temMotivo("VALIDATE_VALID_ZERO"), r.motivos().toString());
        assertTrue(r.motivos().stream().anyMatch(m -> m.texto().equals("Validate VALID = 0")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "NOT_FOUND"})
    void seisPorCentoDeValidateAusenteDaValidateAusenteAlto(String grafia) {
        List<FixtureSanidade.Ev> evs = new ArrayList<>();
        int j = 0;
        for (FixtureSanidade.Ev e : sa(Scenario.S1)) {
            if (e.op().equals("Validate")) {
                evs.add(j++ % 50 < 3 ? e.com(grafia) : e); // 3 em 50 = 6 %
            } else {
                evs.add(e);
            }
        }
        Sanidade.Resultado r = FixtureSanidade.avaliar(Scenario.S1, evs);
        assertTrue(r.temMotivo("VALIDATE_AUSENTE_ALTO"), grafia + ": " + r.motivos());
        assertFalse(r.temMotivo("VALIDATE_VALID_ZERO"));
        assertEquals(0.06, (double) r.metricas().get("validate_ausente_frac"), 1e-3);
    }

    @Test
    void cincoPorCentoDeValidateAusenteNaFronteiraNaoDispara() {
        Sanidade san = new Sanidade();
        san.registrar("Login", "OK", null);
        for (int i = 0; i < 95; i++) {
            san.registrar("Validate", "VALID", null);
        }
        for (int i = 0; i < 5; i++) {
            san.registrar("Validate", "NOT_FOUND", null);
        }
        Sanidade.Resultado r = san.avaliar(Scenario.S1, Sanidade.Limiares.provisorios());
        assertFalse(r.temMotivo("VALIDATE_AUSENTE_ALTO"), r.motivos().toString());
    }

    @Test
    void validateDeslocadoTresPpDaMisturaDesvio() {
        List<FixtureSanidade.Ev> evs = new ArrayList<>(sa(Scenario.S1));
        int extras = (int) Math.round(0.065 * N_OPS); // cerca de +3 p.p. em Validate
        for (int i = 0; i < extras; i++) {
            evs.add(new FixtureSanidade.Ev("Validate", "VALID", null));
        }
        Sanidade.Resultado r = FixtureSanidade.avaliar(Scenario.S1, evs);
        assertEquals(Sanidade.Veredito.FALHA, r.veredito());
        assertTrue(r.motivos().stream().anyMatch(
                m -> m.codigo().equals("MISTURA_DESVIO") && m.texto().startsWith("Validate:")),
                r.motivos().toString());
        assertEquals(1, r.motivos().size(), "so o Validate passa da tolerancia: " + r.motivos());
    }

    @Test
    void csvSoComCabecalhoDaJanelaVazia() throws Exception {
        Path csv = FixtureSanidade.gravar(tmp, "vazio.csv", List.of(), false);
        Path json = tmp.resolve("vazio.json");
        assertEquals(4, rodarCsv(csv, Scenario.S1, json));
        JsonNode j = new ObjectMapper().readTree(json.toFile());
        assertEquals("FALHA", j.get("veredito").asText());
        assertEquals("JANELA_VAZIA", j.get("motivos").get(0).get("codigo").asText());
    }
}
