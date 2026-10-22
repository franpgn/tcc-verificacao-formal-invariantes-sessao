package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.4: versao do contrato do CSV de eventos ({@link OpResult#VERSAO_CONTRATO_CSV}), linha
 * {@code Contrato CSV: v2} no stdout e arquivo {@code meta-csv-rep-NNN.json} ao lado do
 * {@code rep-NNN.csv}. O {@code estado-rep-NNN.json} (B-43.1) e o {@code carga-rep-NNN.json} (B-43.2)
 * nao mudam.
 */
class MetaCsvTest {

    @Test
    @DisplayName("R1: versao do contrato e 2, com a linha de stdout correspondente")
    void versaoDoContrato() {
        assertEquals(2, OpResult.VERSAO_CONTRATO_CSV);
        assertEquals("Contrato CSV: v2", MetaCsv.linha());
    }

    @Test
    @DisplayName("meta-csv-rep-NNN.json ao lado do CSV, com {\"contrato_csv\": 2}")
    void gravaMeta(@TempDir Path dir) throws Exception {
        Path p = MetaCsv.gravar(dir.toString(), 7);
        assertEquals(dir.resolve("meta-csv-rep-007.json"), p);
        Map<?, ?> m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(p.toFile(), Map.class);
        assertEquals(Map.of("contrato_csv", 2), m);
    }

    @Test
    @DisplayName("sem --csv-dir nao grava meta")
    void semCsvDirNaoGrava() throws Exception {
        assertEquals(null, MetaCsv.gravar(null, 1));
        assertEquals(null, MetaCsv.gravar("  ", 1));
    }

    @Test
    @DisplayName("WorkloadMain imprime a linha do contrato e grava o meta logo depois do flush do rep-NNN.csv")
    void workloadMainUsaMeta() throws Exception {
        String fonte = Files.readString(Path.of("src/main/java/br/unipampa/tcc/session/WorkloadMain.java"),
                StandardCharsets.UTF_8);
        assertTrue(fonte.contains("System.out.println(MetaCsv.linha());"));
        int flush = fonte.indexOf("eventos.flush(destino, marcadores);");
        int meta = fonte.indexOf("MetaCsv.gravar(cli.csvDir, rep);");
        assertTrue(flush > 0 && meta > flush, "meta depois do flush do CSV");
        assertFalse(fonte.substring(flush, meta).contains("}"), "meta no mesmo bloco do flush");
    }
}
