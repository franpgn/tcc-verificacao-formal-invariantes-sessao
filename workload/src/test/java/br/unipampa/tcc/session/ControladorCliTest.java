package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3 (B-28.2, item 1 do prompt): opcoes {@code --controller off|shadow|active},
 * {@code --controller-config}, {@code --controller-reference} e {@code --controller-admin}; validacao
 * (codigo 2 em {@code main}); {@code --dry-run} com os parametros e o SHA-256 dos arquivos. Sem
 * {@code --controller-knobs} (K1..K4 sairam no W-1).
 */
class ControladorCliTest {

    static final String SERVIDORES = "127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224";

    @TempDir
    Path dir;

    static Cli parse(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    static final String PARAMS = "{\"versao\":\"teste-w3\",\"wNs\":5000000000,\"nLat\":100,\"nErr\":50,"
            + "\"thetaLatIn\":0.05,\"thetaLatOut\":0.02,\"thetaErrIn\":0.02,\"thetaErrOut\":0.005,"
            + "\"k\":2,\"m\":6,\"cooldownNs\":30000000000,\"estavelNs\":30000000000,\"holdViolNs\":120000000000}";

    static String referencia(String cenario, String perfil) {
        return "{\"esquema\":\"referencia-p99/1\",\"cenario\":\"" + cenario + "\",\"perfil\":\"" + perfil
                + "\",\"p99_ns\":{\"Login\":3000000,\"Validate\":1500000}}";
    }

    Path arquivo(String nome, String conteudo) throws IOException {
        Path p = dir.resolve(nome);
        Files.writeString(p, conteudo, StandardCharsets.UTF_8);
        return p;
    }

    /** Argumentos de um shadow valido em S1 (sem referencia). */
    List<String> shadowS1() throws IOException {
        return new ArrayList<>(List.of("--controller", "shadow",
                "--controller-config", arquivo("controlador.json", PARAMS).toString(),
                "--controller-admin", SERVIDORES, "--servers", SERVIDORES,
                "--csv-dir", dir.resolve("saida").toString()));
    }

    static Cli parse(List<String> args) {
        return parse(args.toArray(String[]::new));
    }

    static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    // -------------------------------------------------------------- opcao

    @Test
    @DisplayName("--controller: padrao off; aceita off, shadow, active; outro valor e erro de parametro")
    void opcao() {
        assertSame(ModoControlador.OFF, parse().controlador);
        assertSame(ModoControlador.SHADOW, parse("--controller", "shadow").controlador);
        assertSame(ModoControlador.ACTIVE, parse("--controller", "active").controlador);
        assertSame(ModoControlador.OFF, parse("--controller", "off").controlador);
        assertThrows(CommandLine.ParameterException.class, () -> parse("--controller", "on"));
        assertThrows(CommandLine.ParameterException.class, () -> parse("--controller-knobs", "x.json"));
    }

    @Test
    @DisplayName("off: nenhuma opcao do controlador e exigida (fluxo de sempre)")
    void offNaoExige() {
        Cli c = parse();
        c.validar();
        assertNull(c.controladorConfig);
        assertNull(c.controladorReferencia);
        assertNull(c.controladorAdmin);
    }

    // -------------------------------------------------------------- validacao

    @Test
    @DisplayName("shadow/active exigem --controller-config, --controller-admin e --csv-dir")
    void exigencias() throws IOException {
        for (String modo : List.of("shadow", "active")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> parse("--controller", modo, "--controller-admin", SERVIDORES, "--csv-dir", "x").validar());
            assertTrue(e.getMessage().contains("--controller-config"), e.getMessage());
            e = assertThrows(IllegalArgumentException.class,
                    () -> parse("--controller", modo, "--controller-config", "p.json", "--csv-dir", "x").validar());
            assertTrue(e.getMessage().contains("--controller-admin"), e.getMessage());
            e = assertThrows(IllegalArgumentException.class, () -> parse("--controller", modo,
                    "--controller-config", "p.json", "--controller-admin", SERVIDORES).validar());
            assertTrue(e.getMessage().contains("--csv-dir"), e.getMessage());
        }
        parse(shadowS1()).validar();
    }

    @Test
    @DisplayName("active exige --controller-reference; shadow nao")
    void activeExigeReferencia() throws IOException {
        List<String> a = shadowS1();
        a.set(1, "active");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(a).validar());
        assertTrue(e.getMessage().contains("--controller-reference"), e.getMessage());
        a.addAll(List.of("--controller-reference", "r.json"));
        parse(a).validar();
    }

    @Test
    @DisplayName("shadow/active com --scenario S3 exigem --marker (sem marcador a I2 real nao e classificada)")
    void s3ExigeMarcador() throws IOException {
        List<String> a = shadowS1();
        a.addAll(List.of("--scenario", "S3"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(a).validar());
        assertTrue(e.getMessage().contains("--marker"), e.getMessage());
        assertTrue(e.getMessage().contains("I2"), "a mensagem cita o motivo: " + e.getMessage());
        a.addAll(List.of("--marker", dir.resolve("marcadores.log").toString()));
        parse(a).validar();
        // off em S3 continua sem exigir marcador
        parse("--scenario", "S3").validar();
    }

    @Test
    @DisplayName("H6: --controller-admin precisa ter tantas URLs quanto --servers")
    void adminDoMesmoTamanho() throws IOException {
        List<String> a = shadowS1();
        a.set(a.indexOf("--controller-admin") + 1, "127.0.0.1:11222,127.0.0.1:11223");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(a).validar());
        assertTrue(e.getMessage().contains("--controller-admin") && e.getMessage().contains("--servers"),
                e.getMessage());
    }

    // -------------------------------------------------------------- carga dos arquivos

    @Test
    @DisplayName("carregar: le parametros e referencia, nosEsperados = itens de --servers, URLs REST normalizadas")
    void carregar() throws Exception {
        List<String> a = shadowS1();
        Path ref = arquivo("referencia-S1-sync.json", referencia("S1", "sync"));
        a.addAll(List.of("--controller-reference", ref.toString()));
        a.set(a.indexOf("--controller-admin") + 1, "http://10.0.0.1:11222,10.0.0.2:11222,10.0.0.3:11222");
        a.set(a.indexOf("--servers") + 1, "a:1,b:2,c:3");
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(parse(a));
        assertEquals("teste-w3", cfg.parametros().versao());
        assertEquals(sha(dir.resolve("controlador.json")), cfg.parametros().sha256());
        assertEquals(sha(ref), cfg.referencia().sha256());
        assertEquals(3, cfg.nosEsperados());
        assertEquals(List.of("http://10.0.0.1:11222", "http://10.0.0.2:11222", "http://10.0.0.3:11222"),
                cfg.urlsAdmin());
    }

    @Test
    @DisplayName("carregar: referencia de outro cenario ou perfil e recusada; arquivo de parametros invalido tambem")
    void carregarRecusa() throws IOException {
        List<String> a = shadowS1();
        a.addAll(List.of("--controller-reference", arquivo("r1.json", referencia("S1", "async")).toString()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LigacaoControlador.carregar(parse(a)));
        assertTrue(e.getMessage().contains("async") && e.getMessage().contains("sync"), e.getMessage());

        List<String> b = shadowS1();
        b.addAll(List.of("--controller-reference", arquivo("r2.json", referencia("S3", "sync")).toString()));
        e = assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.carregar(parse(b)));
        assertTrue(e.getMessage().contains("S3"), e.getMessage());

        List<String> c = shadowS1();
        c.set(c.indexOf("--controller-config") + 1, arquivo("ruim.json", "{\"versao\":\"x\"}").toString());
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.carregar(parse(c)));
    }

    @Test
    @DisplayName("--dry-run: modo, parametros com versao e SHA-256, referencia com SHA-256 e URLs REST")
    void dryRun() throws Exception {
        List<String> a = shadowS1();
        Path ref = arquivo("referencia-S1-sync.json", referencia("S1", "sync"));
        a.addAll(List.of("--controller-reference", ref.toString()));
        String t = LigacaoControlador.textoDryRun(LigacaoControlador.carregar(parse(a)));
        assertTrue(t.contains("controller=shadow"), t);
        assertTrue(t.contains("versao=teste-w3"), t);
        assertTrue(t.contains(sha(dir.resolve("controlador.json"))), t);
        assertTrue(t.contains(sha(ref)), t);
        assertTrue(t.contains("wNs=5000000000") && t.contains("k=2") && t.contains("m=6"), t);
        assertTrue(t.contains("nos_esperados=3"), t);
        assertTrue(t.contains("http://127.0.0.1:11224"), t);
        assertTrue(t.contains("atuador=nulo") && t.contains("politica=nenhuma"), t);
        for (String linha : t.split("\\R")) {
            assertTrue(linha.startsWith("dry-run, "), linha);
        }
    }

    @Test
    @DisplayName("--dry-run sem controlador: o texto de sempre (nenhuma linha do controlador)")
    void dryRunOff() {
        Cli c = parse("--dry-run");
        assertTrue(!c.textoDryRunComMistura().contains("controller"));
    }
}
