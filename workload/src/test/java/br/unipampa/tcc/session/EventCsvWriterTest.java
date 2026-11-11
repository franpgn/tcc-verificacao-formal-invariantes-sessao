package br.unipampa.tcc.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o {@link EventCsvWriter}: cabeçalho exato e ordem das colunas,
 * supressão de eventos de warm-up, e a invariante end_ns > start_ns nas
 * linhas gravadas (T4/T5 do bloco de eventos).
 */
class EventCsvWriterTest {

    private static final String HEADER_ESPERADO =
            "op_id,operation,start_ns,end_ns,replica,return_code,key";

    @Test
    void cabecalhoExatoEColunasNaOrdem(@TempDir Path tmp) throws IOException {
        EventCsvWriter w = new EventCsvWriter(WarmupPolicy.always());
        w.registrar("Login", 1_000L, 2_000L, "isn1:11222", "OK", "user-0");
        w.registrar("Validate", 3_000L, 3_500L, "isn1:11222", "VALID", "sid-1");

        Path csv = tmp.resolve("rep-001.csv");
        w.flush(csv);
        List<String> linhas = Files.readAllLines(csv);

        assertEquals(3, linhas.size(), "cabecalho + 2 eventos");
        assertEquals(HEADER_ESPERADO, linhas.get(0));

        // op_id global e monotônico crescente; colunas na ordem do header.
        String[] c1 = linhas.get(1).split(",", -1);
        assertEquals(7, c1.length, "7 colunas");
        assertEquals("Login", c1[1]);
        assertEquals("1000", c1[2]);
        assertEquals("2000", c1[3]);
        assertEquals("isn1:11222", c1[4]);
        assertEquals("OK", c1[5]);
        assertEquals("user-0", c1[6]);

        String[] c2 = linhas.get(2).split(",", -1);
        assertEquals("Validate", c2[1]);
        long id1 = Long.parseLong(c1[0]);
        long id2 = Long.parseLong(c2[0]);
        assertTrue(id2 > id1, "op_id deve ser estritamente crescente");
    }

    @Test
    void eventosDeWarmupNaoVazam(@TempDir Path tmp) throws IOException {
        // never() => fora da janela de medição => tudo descartado.
        EventCsvWriter w = new EventCsvWriter(WarmupPolicy.never());
        for (int i = 0; i < 50; i++) {
            boolean entrou = w.registrar("Login", i, i + 1, "isn1:11222", "OK", "k");
            assertFalse(entrou, "evento de warm-up nao deveria entrar");
        }
        assertEquals(0, w.totalRegistrados());
        assertEquals(50L, w.descartadosWarmup());

        Path csv = tmp.resolve("rep-002.csv");
        w.flush(csv);
        List<String> linhas = Files.readAllLines(csv);
        assertEquals(1, linhas.size(), "apenas o cabecalho, sem linhas de dados");
        assertEquals(HEADER_ESPERADO, linhas.get(0));
    }

    @Test
    void todasAsLinhasTemEndMaiorQueStart(@TempDir Path tmp) throws IOException {
        EventCsvWriter w = new EventCsvWriter(WarmupPolicy.always());
        for (int i = 0; i < 200; i++) {
            long start = 10_000L + i * 7L;
            long end = start + 1L + (i % 13);
            w.registrar("Validate", start, end, "isn1:11222", "VALID", "sid-" + i);
        }
        Path csv = tmp.resolve("rep-003.csv");
        w.flush(csv);

        List<String> linhas = Files.readAllLines(csv);
        assertEquals(201, linhas.size());
        for (int i = 1; i < linhas.size(); i++) {
            String[] cols = linhas.get(i).split(",", -1);
            long start = Long.parseLong(cols[2]);
            long end = Long.parseLong(cols[3]);
            assertTrue(end > start,
                    "end_ns deve ser > start_ns na linha " + i
                            + ": " + linhas.get(i));
        }
    }

    @Test
    void camposComVirgulaSaoSanitizados(@TempDir Path tmp) throws IOException {
        EventCsvWriter w = new EventCsvWriter(WarmupPolicy.always());
        w.registrar("Login", 1L, 2L, "host,com,virgula", "OK", "key,com,virgula");
        Path csv = tmp.resolve("rep-004.csv");
        w.flush(csv);
        List<String> linhas = Files.readAllLines(csv);
        // Cada linha de dados deve ter exatamente 7 colunas (sem virgula extra).
        String[] cols = linhas.get(1).split(",", -1);
        assertEquals(7, cols.length, "sanitizacao deve preservar 7 colunas");
        assertFalse(cols[4].contains(","));
        assertFalse(cols[6].contains(","));
    }
}
