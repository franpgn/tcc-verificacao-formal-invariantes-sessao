package br.unipampa.tcc.session;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3), item 1 e item 4 do prompt: os <i>caches</i> {@code canario*} nao entram em nenhum invariante,
 * auditor, varredura ou reset, e a sonda nao toca {@code sessions*}/{@code counters*}.
 *
 * <ul>
 *   <li>o literal {@code "canario"} so existe no resolvedor ({@link PerfilReplicacao});</li>
 *   <li>{@code cacheCanario()} so e chamado pelo adaptador da sonda ({@link CanalCanarioHotRod});</li>
 *   <li>o adaptador nao usa os nomes nem os tipos da carga (sessoes, contadores, {@code SessionOps}, auditor,
 *       CSV de eventos, varredura, rastro), nem processo, {@code Runtime} ou arquivo (P-5);</li>
 *   <li>o unico {@code getCache} do adaptador recebe o nome do perfil {@code cacheCanario()}.</li>
 * </ul>
 * Comentarios sao ignorados ({@link DetectorFonte#semComentarios}).
 */
class CanarioArquiteturaTest {

    static final Pattern LITERAL_CANARIO = Pattern.compile("\"canario(-async)?\"");
    static final Pattern USO_CACHE_CANARIO = Pattern.compile("\\bcacheCanario\\s*\\(");
    static final Pattern PROIBIDO_NO_ADAPTADOR = Pattern.compile(
            "\\bcacheSessoes\\b|\\bcacheContadores\\b|\\.caches\\s*\\(|\\bSessionOps\\b|\\bInvariantAuditor\\b"
                    + "|\\bEventCsvWriter\\b|\\bLatencyRegistry\\b|\\bRastroCliente\\b|\\bVarreduraFinal\\b"
                    + "|\\bResetEstado\\b|\\bProcessBuilder\\b|\\bRuntime\\b|java\\.nio\\.file|java\\.io\\.File\\b");

    static final String ADAPTADOR = "CanalCanarioHotRod.java";
    static final String RESOLVEDOR = "PerfilReplicacao.java";

    private static Map<String, String> codigo;

    @BeforeAll
    static void ler() throws IOException {
        codigo = new TreeMap<>();
        try (Stream<Path> s = Files.walk(NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java"))) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".java"))::iterator) {
                codigo.put(p.getFileName().toString(), DetectorFonte.semComentarios(
                        Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")));
            }
        }
    }

    static TreeSet<String> arquivosCom(Pattern p, Map<String, String> fontes) {
        TreeSet<String> r = new TreeSet<>();
        fontes.forEach((nome, txt) -> {
            if (p.matcher(txt).find()) {
                r.add(nome);
            }
        });
        return r;
    }

    static List<String> achados(Pattern p, String txt) {
        List<String> r = new ArrayList<>();
        Matcher m = p.matcher(txt);
        while (m.find()) {
            r.add(m.group());
        }
        return r;
    }

    @Test
    @DisplayName("a varredura le as fontes reais: o resolvedor, o adaptador e a ligacao")
    void naoVacua() {
        assertTrue(codigo.size() > 50, "fontes: " + codigo.size());
        assertTrue(codigo.containsKey(ADAPTADOR) && codigo.containsKey(RESOLVEDOR)
                && codigo.containsKey("LigacaoControlador.java"));
    }

    @Test
    @DisplayName("literal \"canario\" so no resolvedor; cacheCanario() so no resolvedor e no adaptador da sonda")
    void canarioSoNoResolvedorENoAdaptador() {
        assertEquals(new TreeSet<>(List.of(RESOLVEDOR)), arquivosCom(LITERAL_CANARIO, codigo));
        assertEquals(new TreeSet<>(List.of(ADAPTADOR, RESOLVEDOR)), arquivosCom(USO_CACHE_CANARIO, codigo));
    }

    @Test
    @DisplayName("o adaptador nao toca sessions*/counters* nem os tipos da carga, e nao usa processo nem arquivo")
    void adaptadorIsolado() {
        String a = codigo.get(ADAPTADOR);
        assertEquals(List.of(), achados(PROIBIDO_NO_ADAPTADOR, a));
        List<String> getCache = achados(Pattern.compile("getCache\\s*\\(([^)]*)\\)"), a);
        assertEquals(List.of("getCache(nomeCache)"), getCache);
        assertTrue(a.contains("String nomeCache = cli.replicacao.cacheCanario();"), "o nome vem do perfil");
    }

    @Test
    @DisplayName("perfis: canario (SYNC) e canario-async (ASYNC), fora de caches()")
    void perfis() {
        assertEquals("canario", PerfilReplicacao.SYNC.cacheCanario());
        assertEquals("canario-async", PerfilReplicacao.ASYNC.cacheCanario());
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            assertFalse(p.caches().contains(p.cacheCanario()), "caches() e so a carga: " + p.caches());
            assertFalse(PerfilReplicacao.ehCacheDeSessoes(p.cacheCanario()));
            assertFalse(PerfilReplicacao.ehCacheDeContadores(p.cacheCanario()));
        }
    }

    @Test
    @DisplayName("detectores: pegam os usos proibidos e ignoram o que nao e uso")
    void detectores() {
        assertEquals(List.of("cacheSessoes", ".caches(", "SessionOps", "ProcessBuilder"),
                achados(PROIBIDO_NO_ADAPTADOR, "cli.replicacao.cacheSessoes(); p.caches(); SessionOps o; new ProcessBuilder();"));
        assertEquals(List.of(), achados(PROIBIDO_NO_ADAPTADOR, "catch (RuntimeException e) { canal.ler(i, k); }"));
        Map<String, String> falsas = new TreeMap<>(Map.of("A.java", "x(\"canario\")", "B.java", "y(\"canarios\")",
                "C.java", "p.cacheCanario ()"));
        assertEquals(new TreeSet<>(List.of("A.java")), arquivosCom(LITERAL_CANARIO, falsas));
        assertEquals(new TreeSet<>(List.of("C.java")), arquivosCom(USO_CACHE_CANARIO, falsas));
    }
}
