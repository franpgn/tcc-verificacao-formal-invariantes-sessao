package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3), item 4 do prompt (arquitetura) e P-5: a parte pura do S_lag em {@code controle} nao importa
 * {@code org.infinispan} nem {@code session} (detector de {@link ArquiteturaControleTest}), nao cria processo nem
 * toca em arquivo, e nao conhece nome de <i>cache</i>: nenhum literal {@code "sessions"}/{@code "counters"}/
 * {@code "canario"} (quem escolhe o <i>cache</i> e o adaptador, pelo perfil). Exige que as quatro fontes
 * existam, para nao passar por vacuidade.
 */
class ArquiteturaDefasagemTest {

    static final List<String> TIPOS = List.of("SondaDefasagem", "CanalCanario", "RodadaDefasagem",
            "ParametrosDefasagem");

    /** Processo, Runtime, exec e arquivo (mesmo criterio do R-ARQ-2 do W-2a). */
    static final Pattern PROIBIDO = Pattern.compile(
            "\\bProcessBuilder\\b|\\bRuntime\\b|\\.exec\\s*\\(|java\\.nio\\.file|java\\.io\\.File\\b");

    /** Literal de nome de cache. */
    static final Pattern NOME_DE_CACHE = Pattern.compile("\"(sessions|counters|canario)(-async)?\"");

    /** Remove comentarios de bloco e de linha (o bastante para estas fontes, sem barras dentro de literais). */
    static String semComentarios(String f) {
        return f.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    static List<String> achados(Pattern p, String fonte) {
        List<String> r = new ArrayList<>();
        Matcher m = p.matcher(fonte);
        while (m.find()) {
            r.add(m.group());
        }
        return r;
    }

    @Test
    @DisplayName("as fontes do S_lag existem em controle/, sem import proibido, sem processo, arquivo nem nome de cache")
    void sondaPura() throws IOException {
        String base = System.getProperty("basedir", System.getProperty("user.dir"));
        Path dir = Paths.get(base, "src", "main", "java", "br", "unipampa", "tcc", "controle");
        List<String> ruins = new ArrayList<>();
        for (String tipo : TIPOS) {
            Path f = dir.resolve(tipo + ".java");
            assertTrue(Files.isRegularFile(f), "fonte ausente: " + f.toAbsolutePath());
            String fonte = Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (String imp : ArquiteturaControleTest.importsProibidos(fonte)) {
                ruins.add(tipo + ": " + imp);
            }
            String codigo = semComentarios(fonte);
            for (String a : achados(PROIBIDO, codigo)) {
                ruins.add(tipo + ": " + a);
            }
            for (String a : achados(NOME_DE_CACHE, codigo)) {
                ruins.add(tipo + ": " + a);
            }
        }
        assertEquals(List.of(), ruins, "o S_lag em controle/ deve ser puro e nao conhecer nome de cache");
    }

    @Test
    @DisplayName("detectores: pegam processo, arquivo e nome de cache, e ignoram comentario e RuntimeException")
    void detectores() {
        assertEquals(List.of("ProcessBuilder"), achados(PROIBIDO, "new ProcessBuilder(x)"));
        assertEquals(List.of("java.nio.file"), achados(PROIBIDO, "import java.nio.file.Files;"));
        assertEquals(List.of(), achados(PROIBIDO, "catch (RuntimeException e) { executor.execute(r); }"));
        assertEquals(List.of("\"sessions\"", "\"canario-async\""),
                achados(NOME_DE_CACHE, "a(\"sessions\"); b(\"canario-async\"); c(\"lag:\");"));
        assertEquals("x  y", semComentarios("x /* \"sessions\" */ y// ProcessBuilder"));
    }
}
