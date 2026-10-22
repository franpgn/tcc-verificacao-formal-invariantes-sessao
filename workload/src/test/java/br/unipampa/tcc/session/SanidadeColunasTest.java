package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-43.3a (parte i), ressalva R1 da V-B43.3a-i: as colunas sao localizadas pelo
 * NOME no cabecalho, nao pela posicao. O mesmo conjunto de eventos gravado com
 * as colunas permutadas tem de dar a mesma saida, o mesmo veredito, os mesmos
 * motivos e as mesmas contagens/metricas da ordem canonica do
 * {@link EventCsvWriter}. Mata o mutante M4 (leitura por posicao fixa).
 */
class SanidadeColunasTest {

    static final List<String> CANONICA =
            Arrays.asList(EventCsvWriter.HEADER.split(","));
    static final List<String> CANONICA_COM_FASE =
            Arrays.asList(EventCsvWriter.HEADER_COM_FASE.split(","));
    static final List<String> PERMUTADA =
            List.of("return_code", "key", "operation", "op_id", "start_ns", "end_ns", "replica");
    static final List<String> PERMUTADA_COM_FASE =
            List.of("phase", "end_ns", "return_code", "replica", "key", "op_id", "operation", "start_ns");

    @TempDir
    Path tmp;

    /** Grava os eventos com as colunas na ordem {@code cols}. */
    private Path gravar(String nome, List<FixtureSanidade.Ev> evs, List<String> cols) throws Exception {
        Path p = tmp.resolve(nome);
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write(String.join(",", cols));
            w.write('\n');
            long id = 0;
            for (FixtureSanidade.Ev e : evs) {
                List<String> v = new ArrayList<>();
                for (String c : cols) {
                    v.add(switch (c) {
                        case "op_id" -> Long.toString(id);
                        case "operation" -> e.op();
                        case "start_ns" -> Long.toString(1000 + id);
                        case "end_ns" -> Long.toString(2000 + id);
                        case "replica" -> "127.0.0.1:11222";
                        case "return_code" -> e.rc();
                        case "key" -> "k-" + id;
                        case "phase" -> e.fase();
                        default -> throw new IllegalArgumentException(c);
                    });
                }
                w.write(String.join(",", v));
                w.write('\n');
                id++;
            }
        }
        return p;
    }

    private record Rodada(int codigo, JsonNode json) {
    }

    private Rodada rodar(Path csv, Scenario s) throws Exception {
        Path json = tmp.resolve(csv.getFileName() + ".json");
        int codigo = VerificadorSanidade.executar(
                new String[] {"--csv", csv.toString(), "--scenario", s.name(), "--saida", json.toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        JsonNode j = Files.exists(json) ? new ObjectMapper().readTree(json.toFile()) : null;
        return new Rodada(codigo, j);
    }

    private void mesmoResultado(Scenario s, List<FixtureSanidade.Ev> evs, List<String> canonica,
                                List<String> permutada, int saidaEsperada) throws Exception {
        Rodada a = rodar(gravar("canonica.csv", evs, canonica), s);
        Rodada b = rodar(gravar("permutada.csv", evs, permutada), s);

        assertEquals(saidaEsperada, a.codigo(), "ordem canonica");
        assertEquals(a.codigo(), b.codigo(), "colunas permutadas mudaram a saida");
        assertEquals(a.json().get("veredito"), b.json().get("veredito"));
        assertEquals(a.json().get("motivos"), b.json().get("motivos"));
        assertEquals(a.json().get("contagens"), b.json().get("contagens"));
        assertEquals(a.json().get("metricas"), b.json().get("metricas"));
        // Os bytes diferem, entao o SHA-256 tambem.
        assertNotEquals(a.json().get("sha256"), b.json().get("sha256"));
    }

    @Test
    void permutadaSemFaseFixtureSaDaMesmoOk() throws Exception {
        mesmoResultado(Scenario.S1, FixtureSanidade.sa(Scenario.S1, 42L, 20_000),
                CANONICA, PERMUTADA, 0);
    }

    @Test
    void permutadaSemFaseComFalhaDaMesmaFalha() throws Exception {
        // 1 Login em 10 BLOCKED: LOGIN_OK_BAIXO.
        List<FixtureSanidade.Ev> evs =
                FixtureSanidade.comLoginBloqueado(Scenario.S2, 7L, 20_000, k -> k % 10 == 0);
        mesmoResultado(Scenario.S2, evs, CANONICA, PERMUTADA, 4);
    }

    @Test
    void permutadaComFaseNaPrimeiraColunaDaMesmaFalhaDentro() throws Exception {
        List<FixtureSanidade.Ev> evs = new ArrayList<>();
        List<FixtureSanidade.Ev> sa = FixtureSanidade.sa(Scenario.S1, 42L, 20_000);
        for (int i = 0; i < sa.size(); i++) {
            evs.add(sa.get(i).naFase(i < sa.size() / 2 ? "antes" : "pos-convergencia"));
        }
        for (int i = 0; i < 100; i++) {
            evs.add(new FixtureSanidade.Ev("Validate", "NONE", "recuperacao"));
            evs.add(new FixtureSanidade.Ev("Login", "BLOCKED", "durante"));
        }
        mesmoResultado(Scenario.S1, evs, CANONICA_COM_FASE, PERMUTADA_COM_FASE, 4);
        Rodada b = rodar(gravar("permutada2.csv", evs, PERMUTADA_COM_FASE), Scenario.S1);
        assertEquals(200, b.json().get("metricas").get("eventos_dentro").asLong());
        assertEquals("DENTRO_SEM_VALID", b.json().get("motivos").get(0).get("codigo").asText());
    }
}
