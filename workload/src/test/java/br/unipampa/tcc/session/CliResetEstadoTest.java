package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.1, T8: opcao {@code --reset-state on|off} (padrao {@code on}); outro valor e erro de
 * parametro (saida 2 no {@code main}); {@code Cli.toString()} nao muda; {@code --dry-run} ganha a
 * linha {@code dry-run, reset_state=on|off} sem conectar.
 */
class CliResetEstadoTest {

    private static Cli parse(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    @Test
    @DisplayName("padrao on")
    void padraoOn() {
        assertTrue(parse().resetEstado);
        assertTrue(new Cli().resetEstado, "o campo tambem nasce ligado (Cli sem parse)");
    }

    @Test
    @DisplayName("on e off explicitos")
    void onEOff() {
        assertTrue(parse("--reset-state", "on").resetEstado);
        assertFalse(parse("--reset-state", "off").resetEstado);
        assertFalse(parse("--reset-state=off").resetEstado);
    }

    @Test
    @DisplayName("valores invalidos (ligado, vazio, true, ON) e opcao sem valor => ParameterException")
    void invalidos() {
        for (String v : new String[]{"ligado", "", "true", "ON", "false", "1"}) {
            CommandLine.ParameterException e = assertThrows(CommandLine.ParameterException.class,
                    () -> parse("--reset-state", v), "aceitou '" + v + "'");
            assertTrue(e.getMessage().contains("--reset-state"), e.getMessage());
        }
        assertThrows(CommandLine.ParameterException.class, () -> parse("--reset-state"));
    }

    @Test
    @DisplayName("Cli.toString() nao muda com --reset-state")
    void toStringInalterado() {
        assertEquals(parse().toString(), parse("--reset-state", "off").toString());
        assertFalse(parse().toString().contains("reset"), parse().toString());
    }

    @Test
    @DisplayName("textoDryRun ganha a linha reset_state, depois das linhas existentes")
    void textoDryRun() {
        String on = parse("--dry-run").textoDryRun();
        String off = parse("--dry-run", "--reset-state", "off").textoDryRun();
        String[] l = on.split("\\R");
        assertEquals(3, l.length, on);
        assertTrue(l[0].startsWith("dry-run, configuracao OK: Cli{"), on);
        assertTrue(l[1].startsWith("dry-run, replicacao=sync"), on);
        assertEquals("dry-run, reset_state=on", l[2]);
        assertTrue(off.endsWith("dry-run, reset_state=off"), off);
    }

    @Test
    @DisplayName("WorkloadMain --dry-run --reset-state off imprime sem conectar")
    void workloadMainDryRun() {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            WorkloadMain.main(new String[]{"--dry-run", "--reset-state", "off", "--servers", "nao-existe:1"});
        } finally {
            System.setOut(original);
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("dry-run, configuracao OK: "), out);
        assertTrue(out.contains("dry-run, reset_state=off"), out);
        assertFalse(out.contains("Reset: "), "dry-run nao deveria executar o reset: " + out);
    }
}
