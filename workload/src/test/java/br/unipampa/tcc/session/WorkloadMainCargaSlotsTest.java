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
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T4 (parte do WorkloadMain) e T8: {@code prepararCargaSlots} so roda com v3 fora do S3,
 * imprime a linha {@code Carga:} e grava {@code carga-rep-NNN.json}; na falha, saida 5, JSON FALHA e
 * sem {@code rep-001.csv}; a chamada fica depois do reset e antes do warm-up ({@link DetectorCargaSlots}).
 */
class WorkloadMainCargaSlotsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int U = KeyGenerator.TAMANHO_PADRAO_UNIVERSO;

    private FakeCaches.Cache<SessionState> sessoes;
    private FakeCarregavel carregavel;
    private final long[] agoraNs = {0L};
    private final ResetEstado.Sono sono = d -> agoraNs[0] += d.toNanos();

    @BeforeEach
    void preparar() {
        sessoes = new FakeCaches.Cache<>();
        carregavel = new FakeCarregavel("sessions", sessoes);
    }

    private static Cli cli(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    private SlotsGeracao carga(Cli c, int rep) throws Exception {
        return WorkloadMain.prepararCargaSlots(c, carregavel, rep, U, Duration.ofSeconds(10), Duration.ofMillis(100),
                () -> agoraNs[0], sono);
    }

    private static <T> String capturar(Callable<T> acao) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            acao.call();
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static String fonte() throws Exception {
        return Files.readString(NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("T4: v3 => linha Carga, carga-rep-NNN.json OK com mix=v3, universo, tamanho_final, entradas_por_slot=2")
    void v3GravaJsonOk(@TempDir Path dir) throws Exception {
        Cli c = cli("--csv-dir", dir.toString(), "--threads", "4", "--mix", "v3", "--replication", "async");
        SlotsGeracao[] s = new SlotsGeracao[1];
        String out = capturar(() -> s[0] = carga(c, 2));
        assertNotNull(s[0]);
        assertEquals(1, s[0].geracao(1));
        assertEquals(U, s[0].universo());
        assertEquals(4, s[0].threads());
        assertEquals(2 * U, sessoes.dados.size());
        assertTrue(out.contains("Carga: mix=v3 cache=sessions slots=100000 entradas=200000 lotes=200 ms="), out);
        JsonNode j = JSON.readTree(dir.resolve("carga-rep-002.json").toFile());
        assertEquals("v3", j.get("mix").asText());
        assertEquals("async", j.get("replicacao").asText());
        assertEquals("sessions", j.get("cache").asText());
        assertEquals(U, j.get("universo").asInt());
        assertEquals(2 * U, j.get("tamanho_final").asInt());
        assertEquals(2, j.get("entradas_por_slot").asInt());
        assertEquals(200, j.get("lotes").asInt());
        assertEquals("OK", j.get("veredito").asText());
        assertTrue(j.get("motivo").isNull());
    }

    @Test
    @DisplayName("T8/V10: v1, v2 e S3 nao fazem a carga de slots (nem linha, nem JSON, nem acesso ao cache)")
    void soComV3ForaDoS3(@TempDir Path dir) throws Exception {
        for (Cli c : List.of(cli("--csv-dir", dir.toString(), "--mix", "v1"),
                cli("--csv-dir", dir.toString(), "--mix", "v2"),
                cli("--csv-dir", dir.toString()),
                cli("--csv-dir", dir.toString(), "--mix", "v3", "--scenario", "S3"))) {
            SlotsGeracao[] s = {null};
            String out = capturar(() -> s[0] = carga(c, 1));
            assertNull(s[0]);
            assertFalse(out.contains("Carga:"), out);
        }
        assertEquals(0, carregavel.putAlls + carregavel.sizes + carregavel.clears);
        assertFalse(Files.exists(dir.resolve("carga-rep-001.json")));
    }

    @Test
    @DisplayName("T8/V10: com v3 a carga v2 (CargaInicial) nao roda")
    void v3SemCargaV2() throws Exception {
        CargaInicial.Resultado r = WorkloadMain.prepararCarga(cli("--mix", "v3"), carregavel, 1, U,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> agoraNs[0], sono);
        assertNull(r);
        assertEquals(0, carregavel.putAlls);
    }

    @Test
    @DisplayName("T4: carga de slots que nunca chega => saida 5, JSON FALHA, sem rep-001.csv")
    void falhaGravaJson(@TempDir Path dir) throws Exception {
        carregavel.completaNaConsulta = Integer.MAX_VALUE;
        Cli c = cli("--csv-dir", dir.toString(), "--mix", "v3");
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carga(c, 1));
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
        JsonNode j = JSON.readTree(dir.resolve("carga-rep-001.json").toFile());
        assertEquals("FALHA", j.get("veredito").asText());
        assertEquals("v3", j.get("mix").asText());
        assertEquals(e.getMessage(), j.get("motivo").asText());
        assertEquals(2 * U - 1, j.get("tamanho_final").asInt());
        assertEquals(2, j.get("entradas_por_slot").asInt());
        assertFalse(Files.exists(dir.resolve("rep-001.csv")), "rep-001.csv nao pode existir");
    }

    @Test
    @DisplayName("T8: a carga de slots e statement unico, depois do reset e antes de inicioMs/WarmupPolicy")
    void posicao() throws Exception {
        String src = fonte();
        assertTrue(DetectorCargaSlots.problemas(src).isEmpty(), "problemas: " + DetectorCargaSlots.problemas(src));
        assertTrue(DetectorFonte.problemasDaCarga(src).isEmpty(), "a carga v2 continua correta");
        assertTrue(DetectorFonte.problemasDoReset(src).isEmpty(), "o reset continua correto");
    }

    @Test
    @DisplayName("T8: autoteste do detector (depois de inicioMs, antes do reset, comentada, if, try, cache errado, ausente)")
    void detector() throws Exception {
        String src = fonte();
        String st = "            SlotsGeracao slots = prepararCargaSlots(cli, "
                + "CacheCarregavel.de(rcm.getCache(cli.replicacao.cacheSessoes())), rep);\n";
        assertEquals(src.indexOf(st), src.lastIndexOf(st));
        assertTrue(src.contains(st));
        String sem = src.replace(st, "");
        String[] mutantes = {
            sem.replace("            WarmupPolicy politica = WarmupPolicy.padrao(",
                    st + "            WarmupPolicy politica = WarmupPolicy.padrao("),
            sem.replace("            prepararEstado(cli, List.of(", st + "            prepararEstado(cli, List.of("),
            src.replace(st, "            // " + st.trim() + "\n"),
            src.replace(st, "            if (false) " + st.trim() + "\n"),
            src.replace(st, "            if (cli.scenarioS3) {\n" + st + "            }\n"),
            src.replace(st, "            try {\n" + st + "            } catch (RuntimeException e) { }\n"),
            src.replace(st, st.replace("cacheSessoes()", "cacheContadores()")),
            sem,
        };
        for (int i = 0; i < mutantes.length; i++) {
            assertFalse(mutantes[i].equals(src), "mutante " + i + " nao alterou o fonte");
            assertFalse(DetectorCargaSlots.problemas(mutantes[i]).isEmpty(), "detector nao pegou o mutante " + i);
        }
    }
}
