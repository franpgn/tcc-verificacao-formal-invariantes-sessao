package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2b (P-5, achado N-7 do documento de 05/11): nenhum processo no caminho da sonda de saude.
 *
 * <ul>
 *   <li><b>R-ARQ-4</b>: {@code ConsultaSaudeRest} (a fonte da sonda de topologia) e {@code ClienteAdminRest} nao
 *       usam {@code ProcessBuilder}, {@code Process}, {@code Runtime}, {@code curl} nem {@code java.nio.file}; de
 *       {@code HealthRest} (que ainda tem o {@code curl} da checagem de saude da partida), a sonda so usa o
 *       {@code interpretar}.</li>
 *   <li><b>R-ARQ-5</b>: {@code LigacaoControlador} (que monta a sonda, o atuador e as trilhas) nao cria processo.</li>
 *   <li><b>R-ARQ-6</b> (W-2b, P-45): nenhuma fonte de {@code main} cria processo; o laco de convergencia consulta a
 *       saude pelo {@code ClienteAdminRest}.</li>
 * </ul>
 *
 * Comentarios sao ignorados ({@link DetectorFonte#semComentarios}); literais de texto contam.
 */
class ArquiteturaSondaSaudeTest {

    /** W-2b, decisao 3 do coordenador (P-45): o HealthRest (sonda do laco de convergencia) entra na lista. */
    static final List<String> CAMINHO_DA_SONDA = List.of("ConsultaSaudeRest", "ClienteAdminRest", "HealthRest");

    /** Criacao de processo em qualquer fonte de main (o gancho de desligamento da JVM nao cria processo). */
    static final Pattern CRIA_PROCESSO = Pattern.compile("\\bProcessBuilder\\b|\\bProcess\\b|\\.exec\\s*\\(");

    static final Pattern PROCESSO_OU_ARQUIVO = Pattern.compile(
            "\\bProcessBuilder\\b|\\bProcess\\b|\\bRuntime\\b|\\.exec\\s*\\(|curl|java\\.nio\\.file");

    static final Pattern PROCESSO = Pattern.compile("\\bProcessBuilder\\b|\\bProcess\\b|\\bRuntime\\b|\\.exec\\s*\\(");

    /** Uso de {@code HealthRest.<membro>} numa fonte. */
    static final Pattern USO_HEALTH_REST = Pattern.compile("\\bHealthRest\\.(\\w+)");

    static Path fonteDe(String classe) {
        return NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/" + classe + ".java");
    }

    static List<String> achados(Pattern p, String fonte) {
        List<String> r = new ArrayList<>();
        Matcher m = p.matcher(fonte);
        while (m.find()) {
            r.add(m.group(m.groupCount() > 0 && m.group(1) != null ? 1 : 0));
        }
        return r;
    }

    @Test
    @DisplayName("R-ARQ-4: a sonda de saude e o cliente admin sem processo, sem curl e sem arquivo; de HealthRest so o interpretar")
    void rArq4() throws IOException {
        TreeMap<String, List<String>> ruins = new TreeMap<>();
        for (String c : CAMINHO_DA_SONDA) {
            Path f = fonteDe(c);
            assertTrue(Files.exists(f), "fonte nao encontrada: " + f);
            String fonte = ArquiteturaAtuadorTest.fonte(f);
            List<String> a = new ArrayList<>(achados(PROCESSO_OU_ARQUIVO, fonte));
            for (String membro : achados(USO_HEALTH_REST, fonte)) {
                if (!membro.equals("interpretar") && !membro.equals("Estado")) {
                    a.add("HealthRest." + membro);
                }
            }
            if (!a.isEmpty()) {
                ruins.put(c, a);
            }
        }
        assertEquals(new TreeMap<>(), ruins);
    }

    @Test
    @DisplayName("R-ARQ-5: a ligacao do controlador nao cria processo")
    void rArq5() throws IOException {
        assertEquals(List.of(), achados(PROCESSO, ArquiteturaAtuadorTest.fonte(fonteDe("LigacaoControlador"))));
    }

    @Test
    @DisplayName("detectores: reconhecem processo, curl e HealthRest.consultar/configCurl; aceitam ProcessHandle e interpretar")
    void detectores() {
        assertEquals(List.of("ProcessBuilder"), achados(PROCESSO_OU_ARQUIVO, "new ProcessBuilder(cmd).start();"));
        assertEquals(List.of("Process"), achados(PROCESSO_OU_ARQUIVO, "Process p = l.lancar(c);"));
        assertEquals(List.of("curl"), achados(PROCESSO_OU_ARQUIVO, "List.of(\"curl\", \"-s\")"));
        assertEquals(List.of("Runtime", ".exec("), achados(PROCESSO_OU_ARQUIVO, "Runtime.getRuntime().exec(c);"));
        assertEquals(List.of(), achados(PROCESSO_OU_ARQUIVO, "ProcessHandle.current(); } catch (RuntimeException e) {"));
        assertEquals(List.of("configCurl", "interpretar"),
                achados(USO_HEALTH_REST, "HealthRest.configCurl(u, s); HealthRest.interpretar(c);"));
    }

    @Test
    @DisplayName("R-ARQ-6 (P-45): nenhuma fonte de main cria processo (ProcessBuilder, Process, exec)")
    void rArq6() throws IOException {
        TreeMap<String, List<String>> ruins = new TreeMap<>();
        int lidas = 0;
        try (java.util.stream.Stream<Path> s = Files.walk(NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java"))) {
            for (Path f : (Iterable<Path>) s.filter(p -> p.toString().endsWith(".java"))::iterator) {
                lidas++;
                List<String> a = achados(CRIA_PROCESSO, ArquiteturaAtuadorTest.fonte(f));
                if (!a.isEmpty()) {
                    ruins.put(f.getFileName().toString(), a);
                }
            }
        }
        assertTrue(lidas > 50, "fontes de main lidas: " + lidas);
        assertEquals(new TreeMap<>(), ruins);
        assertEquals(List.of(), achados(CRIA_PROCESSO, "ProcessHandle.current(); executor.execute(r);"));
        assertEquals(List.of("ProcessBuilder"), achados(CRIA_PROCESSO, "new ProcessBuilder(c)"));
    }

    @Test
    @DisplayName("P-45: o laco de convergencia consulta a saude pelo ClienteAdminRest, com o prazo do HealthRest")
    void convergenciaSemCurl() throws IOException {
        String fonte = ArquiteturaAtuadorTest.fonte(fonteDe("WorkloadMain"));
        int i = fonte.indexOf("private static Marcadores executarConvergencia(");
        assertTrue(i >= 0, "executarConvergencia nao encontrado");
        String corpo = fonte.substring(i, fonte.indexOf("\n    }\n", i));
        assertTrue(corpo.contains("new ClienteAdminRest(cli.usuario, cli.senha)"), corpo);
        assertTrue(corpo.contains("HealthRest.consultar(clienteSaude, cli.restUrl, HealthRest.PRAZO_CONSULTA_NS)"), corpo);
    }
}
