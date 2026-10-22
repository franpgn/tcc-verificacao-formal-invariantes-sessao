package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T7: {@code --mix v3} aceito ({@code V3}/{@code 3} rejeitados); o padrao continua {@code v2}
 * (DT-005); P-b vale para v3 (com mensagem propria); dry-run com {@code mix=v3 q=0.05};
 * {@code Cli.toString()} igual.
 */
class CliMisturaV3Test {

    private static Cli parse(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    private static String stdout(Runnable r) {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            r.run();
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("T7: --mix v3 aceito; V3, 3 e ' v3' rejeitados; padrao continua v2")
    void aceitaV3() {
        assertEquals(VersaoMistura.V3, parse("--mix", "v3").mix);
        assertEquals(VersaoMistura.V3, parse("--mix=v3").mix);
        assertEquals(VersaoMistura.V3, VersaoMistura.porRotulo("v3"));
        assertEquals("v3", VersaoMistura.V3.rotulo());
        for (String v : new String[] {"V3", "3", " v3", "v3 "}) {
            assertThrows(CommandLine.ParameterException.class, () -> parse("--mix", v), "aceitou '" + v + "'");
            assertThrows(IllegalArgumentException.class, () -> VersaoMistura.porRotulo(v), v);
        }
        assertEquals(VersaoMistura.V2, parse().mix);
        assertEquals(VersaoMistura.V2, new Cli().mix);
    }

    @Test
    @DisplayName("T7: P-b em v3 => validar() falha com mensagem propria; dispensado no dry-run e em S3")
    void v3ExigeReset() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("--mix", "v3", "--reset-state", "off").validar());
        assertTrue(e.getMessage().contains("--mix v3") && e.getMessage().contains("--reset-state on"), e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> parse("--mix", "v3", "--scenario", "S2", "--reset-state", "off").validar());
        assertDoesNotThrow(() -> parse("--mix", "v3").validar());
        assertDoesNotThrow(() -> parse("--mix", "v3", "--scenario", "S3", "--reset-state", "off").validar());
        assertDoesNotThrow(() -> parse("--mix", "v3", "--dry-run", "--reset-state", "off").validar());
        String t = parse("--mix", "v3", "--dry-run", "--reset-state", "off").textoDryRunComMistura();
        assertTrue(t.contains("dry-run, mix=v3 q=0.05 (invalido com reset_state=off"), t);
    }

    @Test
    @DisplayName("T7: dry-run com 'dry-run, mix=v3 q=0.05'; toString() igual ao de v2")
    void dryRunEToString() {
        Cli c = parse("--dry-run", "--mix", "v3");
        String[] l = c.textoDryRunComMistura().split("\\R");
        assertEquals(4, l.length);
        assertEquals("dry-run, mix=v3 q=0.05", l[3]);
        assertEquals("v3 q=0.05", c.rotuloMistura());
        assertEquals("v2", parse().rotuloMistura());
        assertEquals(parse().toString(), parse("--mix", "v3").toString());
        assertFalse(parse("--mix", "v3").toString().contains("mix"));
        String out = stdout(() -> WorkloadMain.main(
                new String[] {"--dry-run", "--mix", "v3", "--servers", "nao-existe:1"}));
        assertTrue(out.contains("dry-run, mix=v3 q=0.05"), out);
        assertFalse(out.contains("Carga: "), out);
    }

    @Test
    @DisplayName("T7: --help documenta v1|v2|v3 e o padrao v2")
    void ajuda() {
        String uso = new CommandLine(new Cli()).getUsageMessage(CommandLine.Help.Ansi.OFF);
        assertTrue(uso.contains("--mix=v1|v2|v3"), uso);
        assertTrue(uso.contains("Padrao v2"), uso);
    }
}
