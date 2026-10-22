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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-42.1, Tarefa 0 (a): a guarda {@link NomesDeCacheSoNoResolvedorTest} verifica que o nome do cache
 * vem de um perfil, mas nao qual perfil. Esta varredura falha se alguma fonte de
 * {@code workload/src/main} fixar um perfil ({@code PerfilReplicacao.SYNC}/{@code .ASYNC}, import
 * estatico das constantes, ou {@code valueOf}/{@code porRotulo} com literal) fora de
 * {@code PerfilReplicacao.java}. As unicas excecoes sao os dois <em>defaults</em> declarados, numa
 * allow-list por (arquivo, conteudo exato da linha). Mata os mutantes N2 (WorkloadMain com perfil
 * fixo) e N7 (admin do S3 com perfil fixo) da validacao V-B42.1 e, por tabela, a regressao N5 da sonda.
 */
class PerfilFixoForaDoResolvedorTest {

    static final String RESOLVEDOR = "PerfilReplicacao.java";

    /** Referencia qualificada a uma constante do enum. */
    static final Pattern CONSTANTE = Pattern.compile("\\bPerfilReplicacao\\s*\\.\\s*(SYNC|ASYNC)\\b");

    /** Import estatico das constantes (ou do enum inteiro), que permitiria escrever {@code SYNC} solto. */
    static final Pattern IMPORT_ESTATICO = Pattern.compile("^\\s*import\\s+static\\s+[\\w.]*\\bPerfilReplicacao\\s*\\.");

    /** Perfil fixado por rotulo literal. */
    static final Pattern ROTULO_LITERAL = Pattern.compile(
            "\\bPerfilReplicacao\\s*\\.\\s*(valueOf|porRotulo)\\s*\\(\\s*\"");

    /** Os dois defaults declarados: arquivo -> linha exata (sem espacos nas pontas). */
    static final Map<String, String> PERMITIDOS = Map.of(
            "Cli.java", "public PerfilReplicacao replicacao = PerfilReplicacao.SYNC;",
            "SondaHotrod.java", "PerfilReplicacao perfil = PerfilReplicacao.SYNC;");

    private static Map<Path, String> fontes;

    @BeforeAll
    static void ler() throws IOException {
        fontes = new TreeMap<>();
        try (Stream<Path> s = Files.walk(NomesDeCacheSoNoResolvedorTest.raizMain())) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                fontes.put(p, Files.readString(p, StandardCharsets.UTF_8));
            }
        }
    }

    /** Todas as ocorrencias de perfil fixo fora do resolvedor, antes da allow-list. */
    static List<String> perfisFixos(Map<Path, String> fontes) {
        List<String> achados = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            String arquivo = p.getFileName().toString();
            if (arquivo.equals(RESOLVEDOR)) {
                return;
            }
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                String l = linhas[i];
                if (CONSTANTE.matcher(l).find() || IMPORT_ESTATICO.matcher(l).find()
                        || ROTULO_LITERAL.matcher(l).find()) {
                    achados.add(arquivo + ":" + (i + 1) + ": " + l.trim());
                }
            }
        });
        return achados;
    }

    /** Verdadeiro se o achado ({@code Arquivo:linha: texto}) e um dos defaults declarados. */
    static boolean permitido(String achado) {
        int c1 = achado.indexOf(':');
        int c2 = achado.indexOf(": ", c1 + 1);
        String arquivo = achado.substring(0, c1);
        String texto = achado.substring(c2 + 2);
        return texto.equals(PERMITIDOS.get(arquivo));
    }

    static List<String> violacoes(Map<Path, String> fontes) {
        return perfisFixos(fontes).stream().filter(a -> !permitido(a)).toList();
    }

    @Test
    @DisplayName("a varredura ve as fontes reais e os dois defaults declarados (nao e vacua)")
    void varreduraNaoEVacua() {
        assertTrue(fontes.size() >= 30, "poucas fontes lidas: " + fontes.size());
        for (String f : List.of("WorkloadMain.java", "S3Clientes.java", "SessionOps.java", "Cli.java",
                "SondaHotrod.java", RESOLVEDOR)) {
            assertTrue(fontes.keySet().stream().anyMatch(p -> p.getFileName().toString().equals(f)),
                    "fonte nao lida: " + f);
        }
        String resolvedor = fontes.entrySet().stream()
                .filter(e -> e.getKey().getFileName().toString().equals(RESOLVEDOR))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertTrue(Pattern.compile("\\bSYNC\\s*\\(").matcher(resolvedor).find()
                && Pattern.compile("\\bASYNC\\s*\\(").matcher(resolvedor).find(),
                "o resolvedor deveria declarar SYNC e ASYNC");
        // cada default da allow-list existe exatamente uma vez (allow-list nao esta obsoleta)
        List<String> brutos = perfisFixos(fontes);
        for (Map.Entry<String, String> e : PERMITIDOS.entrySet()) {
            long n = brutos.stream().filter(a -> a.startsWith(e.getKey() + ":") && permitido(a)).count();
            assertEquals(1, n, "default declarado esperado uma vez em " + e.getKey() + ": " + brutos);
        }
    }

    @Test
    @DisplayName("nenhum PerfilReplicacao.SYNC/ASYNC fora do resolvedor, salvo os dois defaults")
    void semPerfilFixoForaDoResolvedor() {
        List<String> v = violacoes(fontes);
        assertEquals(List.of(), v,
                "perfil fixo fora de PerfilReplicacao (use cli.replicacao/perfil):\n" + String.join("\n", v));
    }

    @Test
    @DisplayName("o detector pega as formas conhecidas e respeita a allow-list por arquivo e linha")
    void detectorPegaFormasConhecidas() {
        Map<Path, String> falsas = new TreeMap<>();
        // N2: WorkloadMain com perfil fixo
        falsas.put(Path.of("A.java"), "SessionOps ops = new SessionOps(rcm, PerfilReplicacao.SYNC, latency);");
        // N7: admin do S3 com o outro perfil, espacos no meio
        falsas.put(Path.of("B.java"), "c.admin = new SessionOps(rcmAdmin, PerfilReplicacao . ASYNC, x);");
        falsas.put(Path.of("C.java"), "import static br.unipampa.tcc.session.PerfilReplicacao.SYNC;");
        falsas.put(Path.of("D.java"), "var p = PerfilReplicacao.valueOf(\"ASYNC\");");
        falsas.put(Path.of("E.java"), "var p = PerfilReplicacao.porRotulo(\"sync\");");
        // N5: sonda com padrao fixo em outra linha que nao a declarada
        falsas.put(Path.of("SondaHotrod.java"), "PerfilReplicacao perfil = PerfilReplicacao.SYNC;\n"
                + "String c = PerfilReplicacao.SYNC.cacheSessoes();");
        // default do Cli copiado para outro arquivo: nao e permitido
        falsas.put(Path.of("F.java"), "    public PerfilReplicacao replicacao = PerfilReplicacao.SYNC;");
        // default do Cli trocado para ASYNC: nao e permitido
        falsas.put(Path.of("Cli.java"), "    public PerfilReplicacao replicacao = PerfilReplicacao.ASYNC;");
        // formas corretas: nao sao achados
        falsas.put(Path.of("G.java"), "new SessionOps(rcm, cli.replicacao, latency);\n"
                + "case \"--replication\" -> perfil = PerfilReplicacao.porRotulo(v);\n"
                + "PerfilReplicacao.ehCacheDeSessoes(cache); PerfilReplicacao.SYNCRONO_NAO_EXISTE;");
        falsas.put(Path.of(RESOLVEDOR), "SYNC(\"\"), ASYNC(\"-async\"); x = PerfilReplicacao.SYNC;");

        List<String> v = violacoes(falsas);
        List<String> esperadas = List.of(
                "A.java:1: SessionOps ops = new SessionOps(rcm, PerfilReplicacao.SYNC, latency);",
                "B.java:1: c.admin = new SessionOps(rcmAdmin, PerfilReplicacao . ASYNC, x);",
                "C.java:1: import static br.unipampa.tcc.session.PerfilReplicacao.SYNC;",
                "Cli.java:1: public PerfilReplicacao replicacao = PerfilReplicacao.ASYNC;",
                "D.java:1: var p = PerfilReplicacao.valueOf(\"ASYNC\");",
                "E.java:1: var p = PerfilReplicacao.porRotulo(\"sync\");",
                "F.java:1: public PerfilReplicacao replicacao = PerfilReplicacao.SYNC;",
                "SondaHotrod.java:2: String c = PerfilReplicacao.SYNC.cacheSessoes();");
        assertEquals(esperadas, v);
        // a linha declarada da sonda foi vista, mas permitida
        assertTrue(perfisFixos(falsas).contains("SondaHotrod.java:1: PerfilReplicacao perfil = PerfilReplicacao.SYNC;"));
    }
}
