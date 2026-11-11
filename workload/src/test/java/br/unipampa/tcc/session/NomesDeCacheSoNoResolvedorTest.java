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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-42.1: varre as fontes de {@code workload/src/main} e falha se algum nome de cache aparecer
 * como literal fora de {@link PerfilReplicacao} (o resolvedor), ou se alguma chamada
 * {@code getCache(...)}/{@code healthy(...)} nao receber o nome do perfil. Garante que os pontos da
 * secao 2.4 do plano de 2026-10-22 (SessionOps, WorkloadMain, S3Clientes, SondaHotrod) fiquem
 * parametrizados e que um literal reintroduzido seja pego.
 */
class NomesDeCacheSoNoResolvedorTest {

    /** Literal Java de nome de cache (com ou sem sufixo -async). */
    static final Pattern LITERAL = Pattern.compile("\"(sessions|counters)(-async)?\"");

    /** Qualquer {@code getCache(<literal>)}, inclusive com argumento de tipo explicito. */
    static final Pattern GETCACHE_LITERAL = Pattern.compile("getCache\\s*\\(\\s*\"");

    /** Argumento de {@code getCache(...)} e {@code healthy(...)}. */
    static final Pattern CHAMADA = Pattern.compile("\\b(getCache|healthy)\\s*\\(\\s*([^)]*\\)?)\\s*\\)");

    /** Argumentos aceitos: o nome vindo do perfil, ou o parametro do adaptador da sonda. */
    static final Pattern ARG_OK = Pattern.compile(
            "(perfil|cli\\.replicacao)\\.cache(Sessoes|Contadores)\\(\\)|nomeCache|String cache");

    static final String RESOLVEDOR = "PerfilReplicacao.java";

    private static Map<Path, String> fontes;

    static Path raizMain() {
        Path d = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && d != null; i++, d = d.getParent()) {
            Path p = d.resolve("src").resolve("main").resolve("java");
            if (Files.isDirectory(p) && Files.exists(p.resolve("br/unipampa/tcc/session/SessionOps.java"))) {
                return d.resolve("src").resolve("main");
            }
            Path w = d.resolve("workload").resolve("src").resolve("main");
            if (Files.isDirectory(w)) {
                return w;
            }
        }
        throw new IllegalStateException("workload/src/main nao encontrado a partir de " + System.getProperty("user.dir"));
    }

    @BeforeAll
    static void ler() throws IOException {
        fontes = new TreeMap<>();
        try (Stream<Path> s = Files.walk(raizMain())) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                fontes.put(p, Files.readString(p, StandardCharsets.UTF_8));
            }
        }
    }

    /** Achados de literal de nome de cache, como {@code Arquivo.java:linha: texto}. */
    static List<String> literaisForaDoResolvedor(Map<Path, String> fontes) {
        List<String> achados = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            if (p.getFileName().toString().equals(RESOLVEDOR)) {
                return;
            }
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                if (LITERAL.matcher(linhas[i]).find() || GETCACHE_LITERAL.matcher(linhas[i]).find()) {
                    achados.add(p.getFileName() + ":" + (i + 1) + ": " + linhas[i].trim());
                }
            }
        });
        return achados;
    }

    /** Chamadas {@code getCache}/{@code healthy} cujo argumento nao vem do perfil. */
    static List<String> chamadasSemPerfil(Map<Path, String> fontes) {
        List<String> achados = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                String l = linhas[i];
                if (l.trim().startsWith("*") || l.trim().startsWith("//")) {
                    continue; // javadoc/comentario
                }
                Matcher m = CHAMADA.matcher(l);
                while (m.find()) {
                    String arg = m.group(2).trim();
                    if (!ARG_OK.matcher(arg).matches()) {
                        achados.add(p.getFileName() + ":" + (i + 1) + ": " + l.trim());
                    }
                }
            }
        });
        return achados;
    }

    @Test
    @DisplayName("a varredura ve as fontes reais (nao e vacua)")
    void varreduraNaoEVacua() {
        assertTrue(fontes.size() >= 30, "poucas fontes lidas: " + fontes.size());
        assertTrue(fontes.keySet().stream().anyMatch(p -> p.endsWith("SessionOps.java")));
        assertTrue(fontes.keySet().stream().anyMatch(p -> p.endsWith("WorkloadMain.java")));
        String resolvedor = fontes.entrySet().stream()
                .filter(e -> e.getKey().getFileName().toString().equals(RESOLVEDOR))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertTrue(LITERAL.matcher(resolvedor).find(), "o resolvedor deveria conter os nomes base");
        long chamadas = fontes.values().stream()
                .mapToLong(t -> CHAMADA.matcher(t).results().count()).sum();
        assertTrue(chamadas >= 12, "esperava as chamadas getCache/healthy da secao 2.4; achou " + chamadas);
    }

    @Test
    @DisplayName("nenhum literal \"sessions\"/\"counters\" (nem getCache(\"...\")) fora do resolvedor")
    void semLiteralForaDoResolvedor() {
        List<String> achados = literaisForaDoResolvedor(fontes);
        assertEquals(List.of(), achados,
                "nome de cache literal fora de PerfilReplicacao (use o perfil):\n" + String.join("\n", achados));
    }

    @Test
    @DisplayName("toda chamada getCache/healthy recebe o nome do perfil")
    void chamadasUsamOPerfil() {
        List<String> achados = chamadasSemPerfil(fontes);
        assertEquals(List.of(), achados,
                "getCache/healthy sem o nome do perfil:\n" + String.join("\n", achados));
    }

    @Test
    @DisplayName("o detector pega as formas que a varredura precisa pegar")
    void detectorPegaFormasConhecidas() {
        Map<Path, String> falsas = new TreeMap<>();
        falsas.put(Path.of("A.java"), "x = rcm.<String, SessionState>getCache(\"sessions\");");
        falsas.put(Path.of("B.java"), "h.healthy(\"counters\")");
        falsas.put(Path.of("C.java"), "rcm.getCache(\"sessions-async\")");
        falsas.put(Path.of("D.java"), "rcm.getCache( \"outro\" )");
        falsas.put(Path.of("E.java"), "rcm.getCache(cli.replicacao.cacheSessoes())");
        falsas.put(Path.of(RESOLVEDOR), "static final String BASE = \"sessions\";");
        List<String> lit = literaisForaDoResolvedor(falsas);
        assertEquals(4, lit.size(), lit.toString());
        assertFalse(lit.stream().anyMatch(s -> s.startsWith("E.java") || s.startsWith(RESOLVEDOR)), lit.toString());
        List<String> ch = chamadasSemPerfil(falsas);
        assertEquals(4, ch.size(), ch.toString());
        assertFalse(ch.stream().anyMatch(s -> s.startsWith("E.java")), ch.toString());
    }
}
