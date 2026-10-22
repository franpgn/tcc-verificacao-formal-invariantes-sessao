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
 * B-43.2, T7: opcao {@code --mix v1|v2} (padrao {@code v2}, P-a); outro valor e erro de parametro;
 * {@code Cli.toString()} nao muda; o {@code --dry-run} ganha a 4a linha {@code dry-run, mix=...};
 * {@code v2} exige {@code --reset-state on} em S1/S2 (P-b, saida 2 em {@code validar()}).
 */
class CliMisturaTest {

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
    @DisplayName("padrao v2 (com e sem parse)")
    void padraoV2() {
        assertEquals(VersaoMistura.V2, parse().mix);
        assertEquals(VersaoMistura.V2, new Cli().mix);
    }

    @Test
    @DisplayName("v1 e v2 explicitos")
    void explicitos() {
        assertEquals(VersaoMistura.V1, parse("--mix", "v1").mix);
        assertEquals(VersaoMistura.V1, parse("--mix=v1").mix);
        assertEquals(VersaoMistura.V2, parse("--mix", "v2").mix);
    }

    @Test
    @DisplayName("invalidos (V2, 2, vazio, v3) e opcao sem valor => ParameterException")
    void invalidos() {
        for (String v : new String[] {"V2", "2", "", "v3", "V1"}) {
            CommandLine.ParameterException e = assertThrows(CommandLine.ParameterException.class,
                    () -> parse("--mix", v), "aceitou '" + v + "'");
            assertTrue(e.getMessage().contains("--mix"), e.getMessage());
        }
        assertThrows(CommandLine.ParameterException.class, () -> parse("--mix"));
    }

    @Test
    @DisplayName("Cli.toString() nao muda com --mix")
    void toStringInalterado() {
        assertEquals(parse().toString(), parse("--mix", "v1").toString());
        assertFalse(parse().toString().contains("mix"), parse().toString());
    }

    @Test
    @DisplayName("dry-run: 4a linha 'dry-run, mix=v1|v2' depois das tres linhas existentes")
    void quartaLinhaDoDryRun() {
        Cli c = parse("--dry-run");
        String[] l = c.textoDryRunComMistura().split("\\R");
        assertEquals(4, l.length, c.textoDryRunComMistura());
        String[] tres = c.textoDryRun().split("\\R");
        for (int i = 0; i < 3; i++) {
            assertEquals(tres[i], l[i]);
        }
        assertEquals("dry-run, mix=v2", l[3]);
        String[] v1 = parse("--dry-run", "--mix", "v1").textoDryRunComMistura().split("\\R");
        assertEquals("dry-run, mix=v1", v1[3]);
    }

    @Test
    @DisplayName("P-b: v2 com --reset-state off falha em validar(); v1 com off e v2 com on passam")
    void v2ExigeReset() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("--reset-state", "off").validar());
        assertTrue(e.getMessage().contains("--mix v2") && e.getMessage().contains("--reset-state on"),
                e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> parse("--mix", "v2", "--reset-state", "off").validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse("--scenario", "S2", "--reset-state", "off").validar());
        assertDoesNotThrow(() -> parse("--mix", "v1", "--reset-state", "off").validar());
        assertDoesNotThrow(() -> parse("--mix", "v2", "--reset-state", "on").validar());
        assertDoesNotThrow(() -> parse().validar());
    }

    @Test
    @DisplayName("desvio: S3 (mistura nao se aplica) e --dry-run (nao executa) nao aplicam a regra de P-b")
    void excecoesDaRegra() {
        assertDoesNotThrow(() -> parse("--scenario", "S3", "--reset-state", "off").validar());
        assertDoesNotThrow(() -> parse("--dry-run", "--reset-state", "off").validar());
        String t = parse("--dry-run", "--reset-state", "off").textoDryRunComMistura();
        assertTrue(t.contains("dry-run, mix=v2 (invalido com reset_state=off"), t);
    }

    @Test
    @DisplayName("WorkloadMain --dry-run --mix v1 imprime a 4a linha sem conectar")
    void workloadMainDryRun() {
        String out = stdout(() -> WorkloadMain.main(
                new String[] {"--dry-run", "--mix", "v1", "--servers", "nao-existe:1"}));
        assertTrue(out.contains("dry-run, configuracao OK: "), out);
        assertTrue(out.contains("dry-run, reset_state=on"), out);
        assertTrue(out.contains("dry-run, mix=v1"), out);
        assertFalse(out.contains("Reset: "), out);
        assertFalse(out.contains("Carga: "), out);
        String padrao = stdout(() -> WorkloadMain.main(new String[] {"--dry-run", "--servers", "nao-existe:1"}));
        assertTrue(padrao.contains("dry-run, mix=v2"), padrao);
    }

    @Test
    @DisplayName("--help documenta --mix com padrao v2")
    void ajuda() {
        String uso = new CommandLine(new Cli()).getUsageMessage(CommandLine.Help.Ansi.OFF);
        assertTrue(uso.contains("--mix=v1|v2"), uso);
        assertTrue(uso.contains("Padrao v2"), uso);
    }
}
