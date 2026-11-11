package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 2): rotulo de fase em cada evento do CSV e em cada violacao.
 * Sem marcador, o formato de S1/S2 e o mesmo de antes (byte-compativel).
 */
class FasesNoCsvTest {

    private static EventCsvWriter escritorComTresEventos() {
        EventCsvWriter w = new EventCsvWriter(WarmupPolicy.always());
        w.registrar("Validate", 50, 60, "isn1", "VALID", "k1");
        w.registrar("Validate", 150, 160, "isn3", "ERROR_UNAVAILABLE", "k2");
        w.registrar("Validate", 250, 260, "isn1", "VALID", "k3");
        return w;
    }

    @Test
    @DisplayName("flush sem marcadores mantem o cabecalho e as linhas de antes")
    void semMarcadoresFormatoAntigo(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("rep.csv");
        escritorComTresEventos().flush(f);
        List<String> l = Files.readAllLines(f);
        assertEquals("op_id,operation,start_ns,end_ns,replica,return_code,key", l.get(0));
        assertEquals(7, l.get(1).split(",").length);
    }

    @Test
    @DisplayName("flush com marcadores acrescenta a coluna phase pelo start_ns")
    void comMarcadoresAcrescentaFase(@TempDir Path dir) throws IOException {
        Marcadores m = Marcadores.parse(List.of(
                "fault=F2 node=isn3 event=start t_epoch_ns=100",
                "fault=F2 node=isn3 event=split t_epoch_ns=120",
                "fault=F2 node=isn3 event=end t_epoch_ns=200")).comRecuperacao(1000);
        Path f = dir.resolve("rep.csv");
        escritorComTresEventos().flush(f, m);
        List<String> l = Files.readAllLines(f);
        assertEquals("op_id,operation,start_ns,end_ns,replica,return_code,key,phase", l.get(0));
        assertTrue(l.get(1).endsWith(",k1,antes"), l.get(1));
        assertTrue(l.get(2).endsWith(",k2,durante-pos-split"), l.get(2));
        assertTrue(l.get(3).endsWith(",k3,recuperacao"), l.get(3));
    }

    @Test
    @DisplayName("dumpCsv sem marcadores mantem o cabecalho antigo")
    void violacoesSemMarcadores(@TempDir Path dir) throws IOException {
        InvariantAuditor a = new InvariantAuditor();
        a.registrarViolacao("I1", "s1", "x");
        Path f = dir.resolve("v.csv");
        a.dumpCsv(f);
        assertEquals("instante,invariante,chave,mensagem", Files.readAllLines(f).get(0));
    }

    @Test
    @DisplayName("dumpCsv com marcadores acrescenta a coluna fase")
    void violacoesComFase(@TempDir Path dir) throws IOException {
        InvariantAuditor a = new InvariantAuditor();
        a.registrarViolacao("I1", "s1", "x");
        long agoraNs = Instant.now().getEpochSecond() * 1_000_000_000L;
        Marcadores durante = Marcadores.parse(List.of(
                "fault=F2 node=isn3 event=start t_epoch_ns=0",
                "fault=F2 node=isn3 event=end t_epoch_ns=" + (agoraNs + 3_600_000_000_000L)));
        Marcadores antes = Marcadores.parse(List.of(
                "fault=F2 node=isn3 event=start t_epoch_ns=" + (agoraNs + 3_600_000_000_000L)));
        Path f1 = dir.resolve("v1.csv");
        Path f2 = dir.resolve("v2.csv");
        a.dumpCsv(f1, durante);
        a.dumpCsv(f2, antes);
        List<String> l1 = Files.readAllLines(f1);
        assertEquals("instante,invariante,chave,mensagem,fase", l1.get(0));
        assertTrue(l1.get(1).endsWith(",durante"), l1.get(1));
        assertTrue(Files.readAllLines(f2).get(1).endsWith(",antes"));
    }
}
