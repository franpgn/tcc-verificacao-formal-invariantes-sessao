package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-33 (E1): opcoes novas da CLI; todas desligadas por padrao (S1/S2 inalterados). */
class CliInstrumentacaoTest {

    private static Cli parse(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    @Test
    @DisplayName("defaults: toda a instrumentacao B-33 desligada")
    void defaultsDesligados() {
        Cli c = parse();
        assertNull(c.marcadores);
        assertFalse(c.convergencia);
        assertFalse(c.varredura);
        assertFalse(c.paraNoFimDaFalha);
        assertEquals(60L, c.wRecoverySeg);
        assertEquals(600L, c.convergenciaTetoSeg);
        assertEquals(5L, c.convergenciaPeriodoSeg);
        assertEquals("http://localhost:11222", c.restUrl);
    }

    @Test
    @DisplayName("opcoes novas sao parseadas")
    void parseNovas() {
        Cli c = parse("--marker", "m.txt", "--w-recovery-sec", "90", "--convergence",
                "--convergence-timeout-sec", "300", "--convergence-period-sec", "2",
                "--sweep", "--stop-at-fault-end", "--rest-url", "http://h:11222");
        assertEquals("m.txt", c.marcadores);
        assertEquals(90L, c.wRecoverySeg);
        assertTrue(c.convergencia && c.varredura && c.paraNoFimDaFalha);
        assertEquals(300L, c.convergenciaTetoSeg);
        assertEquals(2L, c.convergenciaPeriodoSeg);
        assertEquals("http://h:11222", c.restUrl);
        c.validar();
    }

    @Test
    @DisplayName("--stop-at-fault-end sem --marker e invalido")
    void paraNoFimExigeMarcador() {
        Cli c = parse("--stop-at-fault-end");
        assertThrows(IllegalArgumentException.class, c::validar);
    }

    @Test
    @DisplayName("tempos nao positivos sao invalidos")
    void temposInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("--convergence-period-sec", "0").validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse("--convergence-timeout-sec", "0").validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse("--w-recovery-sec", "-1").validar());
    }

    @Test
    @DisplayName("toString nao muda (nao expoe campos novos nem a senha)")
    void toStringInalterado() {
        Cli c = parse("--convergence", "--password", "segredo");
        assertFalse(c.toString().contains("segredo"));
        assertFalse(c.toString().contains("convergencia"));
    }
}
