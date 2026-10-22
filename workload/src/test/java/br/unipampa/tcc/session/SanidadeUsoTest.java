package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** B-43.3a (parte i), S6: uso e arquivo invalidos saem com 2. */
class SanidadeUsoTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    private int rodar(String... args) {
        return VerificadorSanidade.executar(args, new PrintStream(buf, true, StandardCharsets.UTF_8));
    }

    private String saida() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    private Path csv(String conteudo) throws Exception {
        Path p = tmp.resolve("x.csv");
        Files.writeString(p, conteudo, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void cabecalhoSemReturnCodeDa2() throws Exception {
        Path p = csv("op_id,operation,start_ns,end_ns,replica,key\n0,Login,1,2,r,user-0\n");
        Path json = tmp.resolve("s.json");
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S1", "--saida", json.toString()));
        assertTrue(saida().contains("return_code"), saida());
        assertFalse(Files.exists(json), "uso invalido nao grava JSON");
    }

    @Test
    void cabecalhoSemOperationDa2() throws Exception {
        Path p = csv("op_id,start_ns,return_code\n0,1,OK\n");
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S1"));
    }

    @Test
    void scenarioS3Da2ComMensagem() throws Exception {
        Path p = csv(EventCsvWriter.HEADER + "\n");
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S3"));
        assertTrue(saida().contains("S3: guarda propria na parte ii"), saida());
    }

    @Test
    void scenarioInvalidoOuMinusculoDa2() throws Exception {
        Path p = csv(EventCsvWriter.HEADER + "\n");
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S9"));
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "s1"));
    }

    @Test
    void argumentosAusentesOuDesconhecidosDa2() throws Exception {
        Path p = csv(EventCsvWriter.HEADER + "\n");
        assertEquals(2, rodar());
        assertEquals(2, rodar("--csv", p.toString()));
        assertEquals(2, rodar("--scenario", "S1"));
        assertEquals(2, rodar("--csv", p.toString(), "--scenario"));
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S1", "--lenient"));
    }

    @Test
    void arquivoInexistenteOuVazioDa2() throws Exception {
        assertEquals(2, rodar("--csv", tmp.resolve("nao-existe.csv").toString(), "--scenario", "S1"));
        Path vazio = csv("");
        assertEquals(2, rodar("--csv", vazio.toString(), "--scenario", "S1"));
    }

    @Test
    void linhaComColunasErradasDa2() throws Exception {
        Path p = csv(EventCsvWriter.HEADER + "\n0,Login,1,2,r,OK\n");
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S1"));
        assertTrue(saida().contains("linha 2"), saida());
    }

    @Test
    void faseDesconhecidaDa2() throws Exception {
        Path p = csv(EventCsvWriter.HEADER_COM_FASE + "\n0,Login,1,2,r,OK,user-0,talvez\n");
        assertEquals(2, rodar("--csv", p.toString(), "--scenario", "S1"));
        assertTrue(saida().contains("talvez"), saida());
    }
}
