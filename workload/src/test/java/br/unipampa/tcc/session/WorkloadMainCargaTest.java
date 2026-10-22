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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.2, T5 (parte do WorkloadMain), T6 (nenhuma carga em v1) e T8 (posicao da carga em
 * {@code executarRepeticao}, mesmo detector do commit 1).
 */
class WorkloadMainCargaTest {

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

    private CargaInicial.Resultado carga(Cli c, int rep) throws Exception {
        return WorkloadMain.prepararCarga(c, carregavel, rep, U, Duration.ofSeconds(10), Duration.ofMillis(100),
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
    @DisplayName("v2: linha Carga e carga-rep-NNN.json com OK")
    void v2GravaJsonOk(@TempDir Path dir) throws Exception {
        Cli c = cli("--csv-dir", dir.toString(), "--threads", "4", "--replication", "async");
        CargaInicial.Resultado[] r = new CargaInicial.Resultado[1];
        String out = capturar(() -> r[0] = carga(c, 3));
        assertEquals(U, sessoes.dados.size());
        assertTrue(out.contains("Carga: mix=v2 cache=sessions sids=100000 lotes=100 ms="), out);
        JsonNode j = JSON.readTree(dir.resolve("carga-rep-003.json").toFile());
        assertEquals("v2", j.get("mix").asText());
        assertEquals("async", j.get("replicacao").asText());
        assertEquals("sessions", j.get("cache").asText());
        assertEquals(U, j.get("universo").asInt());
        assertEquals(U, j.get("tamanho_final").asInt());
        assertEquals(100, j.get("lotes").asInt());
        assertTrue(j.get("duracao_ms").isNumber());
        assertEquals("OK", j.get("veredito").asText());
        assertTrue(j.get("motivo").isNull());
        assertEquals("user-0-0", sessoes.dados.get("sid-000001").identidade());
        assertEquals("user-3-0", sessoes.dados.get("sid-000004").identidade(), "threads da CLI");
    }

    @Test
    @DisplayName("T6/G8: v1 e S3 nao carregam, nao imprimem Carga e nao gravam JSON de carga")
    void semCargaEmV1NemS3(@TempDir Path dir) throws Exception {
        for (Cli c : List.of(cli("--csv-dir", dir.toString(), "--mix", "v1"),
                cli("--csv-dir", dir.toString(), "--scenario", "S3"))) {
            CargaInicial.Resultado[] r = {null};
            String out = capturar(() -> r[0] = carga(c, 1));
            assertNull(r[0]);
            assertFalse(out.contains("Carga:"), out);
        }
        assertEquals(0, carregavel.putAlls + carregavel.sizes + carregavel.clears);
        assertFalse(Files.exists(dir.resolve("carga-rep-001.json")));
    }

    @Test
    @DisplayName("T5: carga que nunca chega => saida 5, JSON com FALHA e motivo, sem rep-001.csv")
    void falhaGravaJson(@TempDir Path dir) throws Exception {
        carregavel.completaNaConsulta = Integer.MAX_VALUE;
        Cli c = cli("--csv-dir", dir.toString());
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carga(c, 1));
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
        JsonNode j = JSON.readTree(dir.resolve("carga-rep-001.json").toFile());
        assertEquals("FALHA", j.get("veredito").asText());
        assertEquals(e.getMessage(), j.get("motivo").asText());
        assertEquals(U - 1, j.get("tamanho_final").asInt());
        assertFalse(Files.exists(dir.resolve("rep-001.csv")), "rep-001.csv nao pode existir");
    }

    @Test
    @DisplayName("T5: putAll que lanca => JSON com FALHA e tamanho_final nulo; sem --csv-dir nada e gravado")
    void putAllLanca(@TempDir Path dir) throws Exception {
        carregavel.falhaNoPutAll = new IllegalStateException("x");
        carregavel.falhaNoLote = 1;
        CargaInicialException e = assertThrows(CargaInicialException.class,
                () -> carga(cli("--csv-dir", dir.toString()), 2));
        JsonNode j = JSON.readTree(dir.resolve("carga-rep-002.json").toFile());
        assertTrue(j.get("tamanho_final").isNull());
        assertEquals(0, j.get("lotes").asInt());
        assertTrue(e.getCause() == carregavel.falhaNoPutAll);
        carregavel.putAlls = 0; // sem --csv-dir: falha igual, nenhum arquivo
        assertThrows(CargaInicialException.class, () -> carga(cli(), 1));
    }

    @Test
    @DisplayName("T8: a carga e statement unico, depois do reset e antes de inicioMs/WarmupPolicy, no cache de sessoes")
    void posicaoDaCarga() throws Exception {
        List<String> p = DetectorFonte.problemasDaCarga(fonte());
        assertTrue(p.isEmpty(), "problemas: " + p);
        assertTrue(DetectorFonte.problemasDoReset(fonte()).isEmpty(), "o reset continua correto");
    }

    @Test
    @DisplayName("T8: autoteste do detector da carga (depois de inicioMs, antes do reset, comentada, if)")
    void detectorDaCarga() throws Exception {
        String src = fonte();
        String st = "            prepararCarga(cli, CacheCarregavel.de(rcm.getCache(cli.replicacao.cacheSessoes())), rep);\n";
        assertEquals(src.indexOf(st), src.lastIndexOf(st));
        String sem = src.replace(st, "");
        String[] mutantes = {
            sem.replace("            WarmupPolicy politica = WarmupPolicy.padrao(",
                    st + "            WarmupPolicy politica = WarmupPolicy.padrao("),
            sem.replace("            prepararEstado(cli, List.of(", st + "            prepararEstado(cli, List.of("),
            src.replace(st, "            // " + st.trim() + "\n"),
            src.replace(st, "            if (false) " + st.trim() + "\n"),
            src.replace(st, "            if (cli.scenarioS3) {\n" + st + "            }\n"),
            src.replace(st, st.replace("cacheSessoes()", "cacheContadores()")),
            sem,
        };
        for (int i = 0; i < mutantes.length; i++) {
            assertFalse(mutantes[i].equals(src), "mutante " + i + " nao alterou o fonte");
            assertFalse(DetectorFonte.problemasDaCarga(mutantes[i]).isEmpty(), "detector nao pegou o mutante " + i);
        }
    }
}
