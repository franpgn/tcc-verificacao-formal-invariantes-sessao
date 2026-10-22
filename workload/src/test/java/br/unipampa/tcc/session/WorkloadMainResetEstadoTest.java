package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.1, T5 a T7 e a posicao do reset em {@code WorkloadMain.executarRepeticao}: codigo de saida,
 * {@code prepararEstado} com {@code --csv-dir} ({@code estado-rep-NNN.json}) e o modo {@code off}
 * (regressao do fluxo anterior: nenhuma operacao nos caches).
 */
class WorkloadMainResetEstadoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private FakeCaches.Cache<SessionState> sessoes;
    private FakeCaches.Cache<IdentityState> identidades;
    private FakeLimpavel limpSessoes;
    private FakeLimpavel limpIdentidades;
    private final long[] agoraNs = {0L};
    private final ResetEstado.Sono sono = d -> agoraNs[0] += d.toNanos();

    @BeforeEach
    void preparar() {
        sessoes = new FakeCaches.Cache<>();
        identidades = new FakeCaches.Cache<>();
        Instant t = Instant.parse("2026-10-22T00:00:00Z");
        for (int i = 0; i < 3; i++) {
            sessoes.dados.put("sid-" + i, SessionState.criar("sid-" + i, "user-" + i, t));
        }
        identidades.dados.put("user-0", new IdentityState("user-0", 5L, IdentityState.Status.BLOCKED, 7L, t));
        limpSessoes = new FakeLimpavel("sessions", sessoes);
        limpIdentidades = new FakeLimpavel("counters", identidades);
    }

    private static Cli cli(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    private ResetEstado.Resultado preparar(Cli c, int rep) throws EstadoNaoLimpoException, java.io.IOException {
        return WorkloadMain.prepararEstado(c, List.<CacheLimpavel>of(limpSessoes, limpIdentidades), rep,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> agoraNs[0], sono);
    }

    /** Executa {@code acao} capturando o stdout. */
    private static <T> String capturar(Supplier<T> acao) {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            acao.get();
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("T5: codigoDeSaida = 5 para EstadoNaoLimpoException (direta ou como causa), 3 para o resto")
    void codigoDeSaida() {
        EstadoNaoLimpoException e = new EstadoNaoLimpoException("x", null, null);
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
        assertEquals(5, WorkloadMain.codigoDeSaida(new RuntimeException("embrulho", e)));
        assertEquals(5, WorkloadMain.codigoDeSaida(
                new ExecutionException(new IllegalStateException("meio", e))));
        assertEquals(3, WorkloadMain.codigoDeSaida(new RuntimeException("transporte")));
        assertEquals(3, WorkloadMain.codigoDeSaida(new Exception("io", new IllegalStateException())));
        assertEquals(3, WorkloadMain.codigoDeSaida(null));
    }

    @Test
    @DisplayName("on: limpa, imprime a linha Reset e grava estado-rep-NNN.json com veredito OK")
    void onGravaJsonOk(@TempDir Path dir) throws Exception {
        Cli c = cli("--csv-dir", dir.toString(), "--replication", "sync");
        ResetEstado.Resultado[] r = new ResetEstado.Resultado[1];
        String out = capturar(() -> {
            try {
                r[0] = preparar(c, 7);
            } catch (EstadoNaoLimpoException | java.io.IOException e) {
                throw new AssertionError(e);
            }
            return null;
        });
        assertEquals(List.of(0, 0), r[0].tamanhosDepois());
        assertTrue(out.contains("Reset: reset_state=on caches=sessions,counters antes=3,1 depois=0,0 ms=0"), out);
        JsonNode j = JSON.readTree(dir.resolve("estado-rep-007.json").toFile());
        assertEquals("on", j.get("reset_state").asText());
        assertEquals("sync", j.get("replicacao").asText());
        assertEquals("[\"sessions\",\"counters\"]", j.get("caches").toString());
        assertEquals("[3,1]", j.get("tamanhos_antes").toString());
        assertEquals("[0,0]", j.get("tamanhos_depois").toString());
        assertEquals(0, j.get("duracao_ms").asLong());
        assertEquals("OK", j.get("veredito").asText());
        assertTrue(j.get("motivo").isNull());
    }

    @Test
    @DisplayName("T6: cache que nao esvazia => lanca; estado-rep-001.json com FALHA; sem rep-001.csv")
    void falhaGravaJsonEInterrompe(@TempDir Path dir) throws Exception {
        limpIdentidades.ignorarClear = true;
        Cli c = cli("--csv-dir", dir.toString(), "--replication", "async");

        EstadoNaoLimpoException e = assertThrows(EstadoNaoLimpoException.class, () -> preparar(c, 1));

        Path json = dir.resolve("estado-rep-001.json");
        assertTrue(Files.exists(json));
        String texto = Files.readString(json);
        assertTrue(texto.contains("\"veredito\":\"FALHA\""), texto);
        JsonNode j = JSON.readTree(texto);
        assertEquals("async", j.get("replicacao").asText());
        assertEquals("[0,1]", j.get("tamanhos_depois").toString());
        assertEquals(e.getMessage(), j.get("motivo").asText());
        assertTrue(j.get("motivo").asText().contains("counters=1"), texto);
        assertFalse(Files.exists(dir.resolve("rep-001.csv")), "rep-001.csv nao pode existir");
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
    }

    @Test
    @DisplayName("T7: off => nenhuma chamada a clear/size; linha Reset e JSON com reset_state=off e o resto nulo")
    void offNaoTocaNosCaches(@TempDir Path dir) throws Exception {
        Cli c = cli("--csv-dir", dir.toString(), "--reset-state", "off");
        ResetEstado.Resultado[] r = new ResetEstado.Resultado[1];
        String out = capturar(() -> {
            try {
                r[0] = preparar(c, 2);
            } catch (EstadoNaoLimpoException | java.io.IOException e) {
                throw new AssertionError(e);
            }
            return null;
        });
        assertEquals(0, limpSessoes.clears + limpSessoes.sizes + limpIdentidades.clears + limpIdentidades.sizes,
                "off nao pode tocar nos caches");
        assertEquals(3, sessoes.dados.size());
        assertEquals(1, identidades.dados.size());
        assertEquals(null, r[0]);
        assertTrue(out.contains("Reset: reset_state=off"), out);
        JsonNode j = JSON.readTree(dir.resolve("estado-rep-002.json").toFile());
        assertEquals("off", j.get("reset_state").asText());
        for (String campo : List.of("replicacao", "caches", "tamanhos_antes", "tamanhos_depois",
                "duracao_ms", "veredito", "motivo")) {
            assertTrue(j.has(campo) && j.get(campo).isNull(), campo + " deveria ser null: " + j);
        }
    }

    @Test
    @DisplayName("sem --csv-dir nao grava nada; a falha continua lancando a mesma excecao")
    void semCsvDir() {
        limpSessoes.falhaNoClear = new IllegalStateException("DEGRADED_MODE");
        Cli c = cli();
        EstadoNaoLimpoException e = assertThrows(EstadoNaoLimpoException.class, () -> preparar(c, 1));
        assertSame(limpSessoes.falhaNoClear, e.getCause());
    }

    @Test
    @DisplayName("executarRepeticao: reset com os dois caches do perfil, antes do warm-up")
    void resetAntesDoWarmup() throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java");
        String src = Files.readString(f, StandardCharsets.UTF_8);
        int corpo = src.indexOf("static void executarRepeticao(");
        int sess = src.indexOf("CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheSessoes()))", corpo);
        int cont = src.indexOf("CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))", corpo);
        int prep = src.indexOf("prepararEstado(cli,", corpo);
        int inicio = src.indexOf("long inicioMs", corpo);
        int warm = src.indexOf("WarmupPolicy.padrao(", corpo);
        assertTrue(corpo > 0 && sess > corpo && cont > corpo && prep > corpo, "reset ausente em executarRepeticao");
        assertTrue(prep < sess && sess < cont, "prepararEstado recebe sessoes e depois contadores");
        assertTrue(cont < inicio && cont < warm, "o reset tem de vir antes de inicioMs/WarmupPolicy.padrao");
    }
}
