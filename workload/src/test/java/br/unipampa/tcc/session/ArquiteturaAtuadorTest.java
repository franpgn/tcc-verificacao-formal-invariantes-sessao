package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), item 6: testes de arquitetura do atuador do <i>cluster</i> (documento de 05/11, secao 8).
 *
 * <ul>
 *   <li><b>R-ARQ-1</b>: {@code AtuadorRestInfinispan} e {@code ClienteAdminRest} nao usam o cliente Hot Rod
 *       ({@code org.infinispan.client.hotrod}, {@code RemoteCache}, {@code S3Clientes}), {@code ProcessBuilder},
 *       {@code Runtime} nem {@code java.nio.file}.</li>
 *   <li><b>R-ARQ-2</b>: nenhuma classe de {@code controle} usa {@code ProcessBuilder}, {@code Runtime.exec} ou
 *       {@code java.nio.file}, salvo a lista fechada de leitores e gravadores de arquivo ja existentes
 *       ({@code TrilhaJsonl}, {@code ParametrosControlador}, {@code ArquivoReferencia}). O unico uso de
 *       {@code Runtime} aceito e o gancho de desligamento do {@code ExecutorDeAcoes}
 *       ({@code addShutdownHook}/{@code removeShutdownHook}), que nao cria processo.</li>
 *   <li><b>R-ARQ-3</b>: nenhum codigo de {@code main} contem {@code set-mutable-attribute} (o braco B nao muda
 *       atributo, logo nunca muda atributo com no ausente; N-9).</li>
 * </ul>
 *
 * Comentarios sao ignorados ({@link DetectorFonte#semComentarios}); literais de texto contam.
 */
class ArquiteturaAtuadorTest {

    static final List<String> ADAPTADORES = List.of("AtuadorRestInfinispan", "ClienteAdminRest");

    static final Pattern PROIBIDO_ADAPTADOR = Pattern.compile(
            "org\\.infinispan\\.client\\.hotrod|\\bRemoteCache\\w*\\b|\\bS3Clientes\\b|\\bProcessBuilder\\b"
                    + "|\\bRuntime\\b|java\\.nio\\.file");

    static final Pattern PROIBIDO_CONTROLE = Pattern.compile(
            "\\bProcessBuilder\\b|\\bRuntime\\b|\\.exec\\s*\\(|java\\.nio\\.file");

    /** Lista fechada de classes de controle que tocam em arquivo (W-2a, R-ARQ-2). */
    static final Set<String> ARQUIVO_PERMITIDO = Set.of("TrilhaJsonl", "ParametrosControlador", "ArquivoReferencia");

    /** Unico uso de Runtime aceito em controle: o gancho de desligamento da JVM. */
    static final Pattern GANCHO_JVM = Pattern.compile(
            "Runtime\\.getRuntime\\(\\)\\.(addShutdownHook|removeShutdownHook)\\(");

    static final String SET_MUTABLE = "set-mutable-attribute";

    private static Path raizMain() {
        return NomesDeCacheSoNoResolvedorTest.raizMain();
    }

    static String fonte(Path f) throws IOException {
        return DetectorFonte.semComentarios(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    static List<String> achados(Pattern p, String fonte) {
        List<String> r = new ArrayList<>();
        Matcher m = p.matcher(fonte);
        while (m.find()) {
            r.add(m.group());
        }
        return r;
    }

    /** Achados de R-ARQ-2 numa fonte de controle (funcao pura, testada abaixo). */
    static List<String> achadosControle(String classe, String fonte) {
        String semGancho = classe.equals("ExecutorDeAcoes") ? GANCHO_JVM.matcher(fonte).replaceAll("GANCHO(") : fonte;
        List<String> r = new ArrayList<>();
        for (String a : achados(PROIBIDO_CONTROLE, semGancho)) {
            if (a.equals("java.nio.file") && ARQUIVO_PERMITIDO.contains(classe)) {
                continue;
            }
            r.add(a);
        }
        return r;
    }

    @Test
    @DisplayName("R-ARQ-1: o atuador REST e o cliente admin nao usam Hot Rod, processo nem arquivo")
    void rArq1() throws IOException {
        TreeMap<String, List<String>> ruins = new TreeMap<>();
        for (String c : ADAPTADORES) {
            Path f = raizMain().resolve("java/br/unipampa/tcc/session/" + c + ".java");
            assertTrue(Files.exists(f), "fonte nao encontrada: " + f);
            List<String> a = achados(PROIBIDO_ADAPTADOR, fonte(f));
            if (!a.isEmpty()) {
                ruins.put(c, a);
            }
        }
        assertEquals(new TreeMap<>(), ruins);
    }

    @Test
    @DisplayName("R-ARQ-2: controle sem processo e sem arquivo, salvo a lista fechada")
    void rArq2() throws IOException {
        TreeMap<String, List<String>> ruins = new TreeMap<>();
        int lidas = 0;
        try (Stream<Path> s = Files.list(raizMain().resolve("java/br/unipampa/tcc/controle"))) {
            for (Path f : (Iterable<Path>) s.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String classe = f.getFileName().toString().replace(".java", "");
                lidas++;
                List<String> a = achadosControle(classe, fonte(f));
                if (!a.isEmpty()) {
                    ruins.put(classe, a);
                }
            }
        }
        assertTrue(lidas >= 40, "fontes de controle lidas: " + lidas);
        assertEquals(new TreeMap<>(), ruins);
    }

    @Test
    @DisplayName("R-ARQ-3: nenhum codigo de main contem set-mutable-attribute")
    void rArq3() throws IOException {
        List<String> ruins = new ArrayList<>();
        int lidas = 0;
        try (Stream<Path> s = Files.walk(raizMain().resolve("java"))) {
            for (Path f : (Iterable<Path>) s.filter(p -> p.toString().endsWith(".java"))::iterator) {
                lidas++;
                if (fonte(f).contains(SET_MUTABLE)) {
                    ruins.add(f.getFileName().toString());
                }
            }
        }
        assertTrue(lidas > 50, "fontes de main lidas: " + lidas);
        assertEquals(List.of(), ruins);
    }

    @Test
    @DisplayName("detectores: reconhecem os usos proibidos e aceitam o gancho da JVM e RuntimeException")
    void detectores() {
        assertEquals(List.of("ProcessBuilder"), achadosControle("X", "new ProcessBuilder(cmd).start();"));
        assertEquals(List.of("Runtime", ".exec("), achadosControle("X", "Runtime.getRuntime().exec(cmd);"));
        assertEquals(List.of("java.nio.file"), achadosControle("X", "import java.nio.file.Files;"));
        assertEquals(List.of(), achadosControle("TrilhaJsonl", "import java.nio.file.Files;"));
        assertEquals(List.of(), achadosControle("X", "} catch (RuntimeException e) {"));
        assertEquals(List.of(), achadosControle("ExecutorDeAcoes", "Runtime.getRuntime().addShutdownHook(t);"));
        assertEquals(List.of("Runtime"), achadosControle("OutraClasse", "Runtime.getRuntime().addShutdownHook(t);"));
        assertEquals(List.of("Runtime", ".exec("),
                achadosControle("ExecutorDeAcoes", "Runtime.getRuntime().exec(\"x\");"));
        assertEquals(List.of("org.infinispan.client.hotrod", "RemoteCacheManager", "S3Clientes"),
                achados(PROIBIDO_ADAPTADOR, "import org.infinispan.client.hotrod.X; RemoteCacheManager m; S3Clientes c;"));
        assertTrue(DetectorFonte.semComentarios("// set-mutable-attribute\nint x;").indexOf(SET_MUTABLE) < 0);
        assertTrue(DetectorFonte.semComentarios("String s = \"?action=set-mutable-attribute\";").contains(SET_MUTABLE));
    }
}
