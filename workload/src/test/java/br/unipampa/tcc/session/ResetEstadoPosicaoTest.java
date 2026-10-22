package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-B43.1, ressalvas R1 e R2 (primeiro commit de B-43.2).
 *
 * <p><b>R1.</b> A posicao do reset em {@code executarRepeticao} era travada so por texto bruto, que
 * aceita codigo morto (mutantes M6 {@code if (false)}, M7 {@code try/catch} local, M8 reset pulado em
 * S3 e B2w-c linha do cache de contadores comentada). Aqui o fonte e lido sem comentarios e com a
 * estrutura de blocos ({@link DetectorFonte}); um autoteste prova que o detector pega as quatro
 * formas.</p>
 *
 * <p><b>R2.</b> Os padroes de producao do reset (prazo 10 s, intervalo 100 ms) nao eram testados; a
 * troca dos dois na sobrecarga de 3 argumentos (M2c) passava. Aqui: os valores das constantes, a
 * ordem no fonte e um teste de comportamento da sobrecarga de producao com espera real curta.</p>
 */
class ResetEstadoPosicaoTest {

    private static String fonteWorkloadMain() throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java");
        return Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Troca a unica ocorrencia de {@code de} por {@code para}; falha se nao houver exatamente uma. */
    private static String trocarUma(String src, String de, String para) {
        int i = src.indexOf(de);
        assertTrue(i >= 0, "trecho ausente: " + de);
        assertEquals(-1, src.indexOf(de, i + 1), "trecho nao e unico: " + de);
        return src.substring(0, i) + para + src.substring(i + de.length());
    }

    /** O statement do reset (de {@code prepararEstado(cli, List.of(} ate {@code ;}) no fonte real. */
    private static String statementDoReset(String src) {
        Matcher m = Pattern.compile("prepararEstado\\(cli, List\\.of\\([^;]*;").matcher(src);
        assertTrue(m.find(), "statement do reset ausente");
        String st = m.group();
        assertFalse(m.find(), "statement do reset nao e unico");
        return st;
    }

    // ---------------------------------------------------------------- R1

    @Test
    @DisplayName("R1: o reset e statement executavel em executarRepeticao, antes do warm-up, so no try do rcm")
    void resetExecutaAntesDoWarmup() throws Exception {
        List<String> p = DetectorFonte.problemasDoReset(fonteWorkloadMain());
        assertTrue(p.isEmpty(), "problemas: " + p);
    }

    @Test
    @DisplayName("R1: autoteste do detector (chamada comentada, if (false), try/catch local, if (!S3), "
            + "linha de contadores comentada)")
    void detectorPegaCodigoMorto() throws Exception {
        String src = fonteWorkloadMain();
        String st = statementDoReset(src);
        Map<String, String> mutantes = new LinkedHashMap<>();
        mutantes.put("chamada comentada (//)", trocarUma(src, st, "// " + st.replace("\n", "\n// ")));
        mutantes.put("chamada comentada (/* */)", trocarUma(src, st, "/* " + st + " */"));
        mutantes.put("M6 if (false) na mesma linha", trocarUma(src, st, "if (false) " + st));
        mutantes.put("M6 if (false) em linha propria", trocarUma(src, st, "if (false)\n            " + st));
        mutantes.put("M6 if (false) com bloco", trocarUma(src, st, "if (false) {\n            " + st + "\n            }"));
        mutantes.put("M7 try/catch local", trocarUma(src, st,
                "try { " + st + " } catch (EstadoNaoLimpoException ignorada) { }"));
        mutantes.put("M7 try/catch local em linhas", trocarUma(src, st,
                "try {\n            " + st + "\n            } catch (EstadoNaoLimpoException ignorada) {\n            }"));
        mutantes.put("M7 try/catch(Exception) local", trocarUma(src, st,
                "try {\n            " + st + "\n            } catch (Exception ignorada) {\n            }"));
        mutantes.put("M8 if (!cli.scenarioS3) na mesma linha", trocarUma(src, st, "if (!cli.scenarioS3) " + st));
        mutantes.put("M8 if (!cli.scenarioS3) em linha propria",
                trocarUma(src, st, "if (!cli.scenarioS3)\n                " + st));
        mutantes.put("B2w-c contadores comentado (//)", trocarUma(src,
                "CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheSessoes())),\n"
                        + "                    CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))), rep);",
                "CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheSessoes()))\n"
                        + "                    // , CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))\n"
                        + "                    ), rep);"));
        mutantes.put("B2w-c contadores comentado (/* */)", trocarUma(src,
                ",\n                    CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))), rep);",
                "/*,\n                    CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))*/), rep);"));
        mutantes.put("reset depois de inicioMs", trocarUma(trocarUma(src, st, ""),
                "            WarmupPolicy politica = WarmupPolicy.padrao(",
                "            " + st + "\n            WarmupPolicy politica = WarmupPolicy.padrao("));
        for (Map.Entry<String, String> e : mutantes.entrySet()) {
            List<String> p = DetectorFonte.problemasDoReset(e.getValue());
            assertFalse(p.isEmpty(), "o detector NAO pegou: " + e.getKey());
        }
    }

    @Test
    @DisplayName("R1: semComentarios preserva deslocamentos e literais; mascarado esconde chaves em literais")
    void detectorBasico() {
        String s = "a(); // x { \n/* { */ b(\"{//\"); c('{');";
        String sc = DetectorFonte.semComentarios(s);
        String m = DetectorFonte.mascarado(s);
        assertEquals(s.length(), sc.length());
        assertEquals(s.length(), m.length());
        assertTrue(sc.contains("b(\"{//\")"), sc);
        assertFalse(sc.contains("x {"), sc);
        assertEquals(0, m.chars().filter(c -> c == '{').count(), m);
    }

    // ---------------------------------------------------------------- R2

    @Test
    @DisplayName("R2: padroes de producao do reset sao prazo 10 s e intervalo 100 ms")
    void padroesDoReset() {
        assertEquals(Duration.ofSeconds(10), ResetEstado.PRAZO_PADRAO);
        assertEquals(Duration.ofMillis(100), ResetEstado.INTERVALO_PADRAO);
    }

    @Test
    @DisplayName("R2: a sobrecarga de 3 argumentos passa PRAZO_PADRAO e depois INTERVALO_PADRAO (fonte sem comentarios)")
    void sobrecargaDeProducaoNaOrdem() throws Exception {
        String src = fonteWorkloadMain();
        String s = DetectorFonte.semComentarios(src);
        String m = DetectorFonte.mascarado(src);
        int[] corpo = DetectorFonte.corpo(m,
                "static ResetEstado\\.Resultado prepararEstado\\(Cli cli, List<CacheLimpavel> caches, int rep\\)");
        assertNotNull(corpo, "sobrecarga de 3 argumentos ausente");
        String c = s.substring(corpo[0], corpo[1]);
        assertTrue(Pattern.compile("prepararEstado\\(\\s*cli\\s*,\\s*caches\\s*,\\s*rep\\s*,\\s*"
                + "ResetEstado\\.PRAZO_PADRAO\\s*,\\s*ResetEstado\\.INTERVALO_PADRAO\\s*,").matcher(c).find(), c);
    }

    @Test
    @DisplayName("R2: a sobrecarga de producao espera em passos de 100 ms ate o prazo (cache ASYNC na 3a consulta)")
    void sobrecargaDeProducaoComportamento() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        sessoes.dados.put("sid-1", SessionState.criar("sid-1", "user-0", Instant.parse("2026-10-22T00:00:00Z")));
        FakeLimpavel limp = new FakeLimpavel("sessions", sessoes);
        limp.aplicaNaConsulta = 3;
        FakeLimpavel vazio = new FakeLimpavel("counters", new FakeCaches.Cache<IdentityState>());
        Cli cli = new Cli();
        long t0 = System.nanoTime();
        ResetEstado.Resultado r = WorkloadMain.prepararEstado(cli, List.<CacheLimpavel>of(limp, vazio), 1);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(List.of(1, 0), r.tamanhosAntes());
        assertEquals(List.of(0, 0), r.tamanhosDepois());
        // duas esperas de 100 ms; com os padroes trocados (prazo 100 ms) a 2a consulta ja estouraria o prazo
        assertTrue(ms >= 190 && ms < 5_000, "duracao fora do esperado para 2 x 100 ms: " + ms + " ms");
    }

    @Test
    @DisplayName("R2: com um cache que nunca esvazia, a sobrecarga de producao NAO desiste antes de 1 s")
    void sobrecargaDeProducaoNaoDesisteCedo() {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        sessoes.dados.put("sid-1", SessionState.criar("sid-1", "user-0", Instant.parse("2026-10-22T00:00:00Z")));
        FakeLimpavel limp = new FakeLimpavel("sessions", sessoes);
        limp.aplicaNaConsulta = 12; // ~1,1 s com intervalo de 100 ms; aborta se o prazo fosse < 1,1 s
        FakeLimpavel vazio = new FakeLimpavel("counters", new FakeCaches.Cache<IdentityState>());
        Cli cli = new Cli();
        ResetEstado.Resultado r = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> WorkloadMain.prepararEstado(cli, List.<CacheLimpavel>of(limp, vazio), 1));
        assertTrue(r.duracaoMs() >= 1_000, "duracao " + r.duracaoMs());
        assertThrows(IllegalArgumentException.class, () -> ResetEstado.executar(List.of(limp),
                Duration.ZERO, ResetEstado.INTERVALO_PADRAO, System::nanoTime, ResetEstado.Sono.real()));
    }
}
