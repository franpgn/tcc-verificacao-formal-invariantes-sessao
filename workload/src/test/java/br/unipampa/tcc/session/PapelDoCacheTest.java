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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-42.1, Tarefa 0 (b): o nome do cache vem do perfil, mas o papel tem de casar com o tipo. Em
 * {@code workload/src/main}:
 * <ul>
 *   <li>toda chamada {@code getCache(...)} tipada com {@code SessionState} (argumento de tipo
 *       explicito {@code <String, SessionState>getCache} ou declaracao
 *       {@code RemoteCache<String, SessionState> x =} na mesma linha) usa {@code cacheSessoes()}, e
 *       toda chamada tipada com {@code IdentityState} usa {@code cacheContadores()};</li>
 *   <li>as chamadas {@code HealthRest.Estado.healthy(...)} aparecem em par, sessoes antes de
 *       contadores.</li>
 * </ul>
 * Mata os mutantes N1 (papeis trocados em SessionOps) e N3 ({@code healthy} de sessoes duas vezes)
 * da validacao V-B42.1.
 */
class PapelDoCacheTest {

    /** Chamada {@code getCache(...)} e o seu argumento (aceita um nivel de parenteses). */
    static final Pattern GETCACHE = Pattern.compile("\\bgetCache\\s*\\(\\s*([^()]*(?:\\([^()]*\\))?[^()]*)\\)");

    /** Argumento de tipo explicito imediatamente antes de {@code getCache}. */
    static final Pattern TIPO_EXPLICITO = Pattern.compile("<\\s*String\\s*,\\s*(\\w+)\\s*>\\s*$");

    /** Declaracao {@code RemoteCache<String, T> x =} na linha. */
    static final Pattern DECLARACAO = Pattern.compile("\\bRemoteCache\\s*<\\s*String\\s*,\\s*(\\w+)\\s*>\\s+\\w+\\s*=");

    /** Chamada a {@code healthy(...)} com receptor (exclui a declaracao do metodo). */
    static final Pattern HEALTHY = Pattern.compile("\\.\\s*healthy\\s*\\(\\s*([^()]*(?:\\([^()]*\\))?[^()]*)\\)");

    static final Pattern ARG_SESSOES = Pattern.compile(".*\\.cacheSessoes\\(\\)\\s*");
    static final Pattern ARG_CONTADORES = Pattern.compile(".*\\.cacheContadores\\(\\)\\s*");

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

    private static boolean comentario(String l) {
        String t = l.trim();
        return t.startsWith("*") || t.startsWith("//") || t.startsWith("/*");
    }

    /** Chamada tipada: {@code Arquivo:linha}, tipo de valor e argumento. */
    record ChamadaTipada(String onde, String tipo, String arg) { }

    /** Todas as chamadas {@code getCache} cujo tipo de valor e conhecido pela linha. */
    static List<ChamadaTipada> chamadasTipadas(Map<Path, String> fontes) {
        List<ChamadaTipada> out = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                String l = linhas[i];
                if (comentario(l)) {
                    continue;
                }
                Matcher m = GETCACHE.matcher(l);
                while (m.find()) {
                    String tipo = null;
                    Matcher te = TIPO_EXPLICITO.matcher(l.substring(0, m.start()));
                    if (te.find()) {
                        tipo = te.group(1);
                    } else {
                        Matcher d = DECLARACAO.matcher(l);
                        if (d.find() && d.end() <= m.start()) {
                            tipo = d.group(1);
                        }
                    }
                    if (tipo != null) {
                        out.add(new ChamadaTipada(p.getFileName() + ":" + (i + 1), tipo, m.group(1).trim()));
                    }
                }
            }
        });
        return out;
    }

    /** Chamadas tipadas cujo papel (tipo de valor) nao casa com o metodo do perfil usado. */
    static List<String> papeisTrocados(Map<Path, String> fontes) {
        List<String> achados = new ArrayList<>();
        for (ChamadaTipada c : chamadasTipadas(fontes)) {
            boolean ok = switch (c.tipo()) {
                case "SessionState" -> ARG_SESSOES.matcher(c.arg()).matches();
                case "IdentityState" -> ARG_CONTADORES.matcher(c.arg()).matches();
                default -> true;
            };
            if (!ok) {
                achados.add(c.onde() + ": " + c.tipo() + " com getCache(" + c.arg() + ")");
            }
        }
        return achados;
    }

    /** Argumentos das chamadas {@code .healthy(...)}, na ordem do arquivo, com {@code Arquivo:linha}. */
    static List<String[]> chamadasHealthy(Map<Path, String> fontes) {
        List<String[]> out = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                if (comentario(linhas[i])) {
                    continue;
                }
                Matcher m = HEALTHY.matcher(linhas[i]);
                while (m.find()) {
                    out.add(new String[]{p.getFileName() + ":" + (i + 1), m.group(1).trim()});
                }
            }
        });
        return out;
    }

    /** Problemas de pareamento das chamadas {@code healthy}: em par, sessoes e depois contadores. */
    static List<String> healthyForaDePar(Map<Path, String> fontes) {
        List<String[]> hs = chamadasHealthy(fontes);
        List<String> achados = new ArrayList<>();
        if (hs.size() % 2 != 0) {
            achados.add("numero impar de chamadas healthy: " + hs.size());
        }
        for (int i = 0; i + 1 < hs.size(); i += 2) {
            String[] a = hs.get(i);
            String[] b = hs.get(i + 1);
            if (!ARG_SESSOES.matcher(a[1]).matches() || !ARG_CONTADORES.matcher(b[1]).matches()) {
                achados.add("par " + a[0] + " / " + b[0] + ": healthy(" + a[1] + "), healthy(" + b[1]
                        + ") (esperado sessoes e depois contadores)");
            }
        }
        if (hs.size() % 2 != 0) {
            String[] u = hs.get(hs.size() - 1);
            achados.add("sem par " + u[0] + ": healthy(" + u[1] + ")");
        }
        return achados;
    }

    @Test
    @DisplayName("a varredura ve as chamadas tipadas e o par healthy (nao e vacua)")
    void varreduraNaoEVacua() {
        assertTrue(fontes.size() >= 30, "poucas fontes lidas: " + fontes.size());
        List<ChamadaTipada> cs = chamadasTipadas(fontes);
        long sess = cs.stream().filter(c -> c.tipo().equals("SessionState")).count();
        long ident = cs.stream().filter(c -> c.tipo().equals("IdentityState")).count();
        // SessionOps (2 construtores) e WorkloadMain (leitores multi-replica) tipam os dois papeis
        assertTrue(sess >= 4, "poucas chamadas tipadas com SessionState: " + cs);
        assertTrue(ident >= 4, "poucas chamadas tipadas com IdentityState: " + cs);
        assertTrue(cs.stream().anyMatch(c -> c.onde().startsWith("SessionOps.java:")), cs.toString());
        assertTrue(cs.stream().anyMatch(c -> c.onde().startsWith("WorkloadMain.java:")), cs.toString());
        List<String[]> hs = chamadasHealthy(fontes);
        assertTrue(hs.size() >= 2, "esperava ao menos um par healthy em WorkloadMain; achou " + hs.size());
        assertTrue(hs.stream().allMatch(h -> h[0].startsWith("WorkloadMain.java:")),
                "healthy fora de WorkloadMain: revisar a guarda");
    }

    @Test
    @DisplayName("getCache tipado com SessionState usa cacheSessoes(); com IdentityState usa cacheContadores()")
    void papelCasaComOTipo() {
        List<String> a = papeisTrocados(fontes);
        assertEquals(List.of(), a, "papel do cache trocado:\n" + String.join("\n", a));
    }

    @Test
    @DisplayName("healthy(...) aparece em par, sessoes antes de contadores")
    void healthyEmParSessoesDepoisContadores() {
        List<String> a = healthyForaDePar(fontes);
        assertEquals(List.of(), a, "healthy fora do par sessoes/contadores:\n" + String.join("\n", a));
    }

    @Test
    @DisplayName("o detector pega papeis trocados e pares healthy errados")
    void detectorPegaFormasConhecidas() {
        Map<Path, String> falsas = new TreeMap<>();
        falsas.put(Path.of("A.java"), String.join("\n",
                // N1: sessoes abre o cache de contadores
                "this(Armazem.de(rcm.<String, SessionState>getCache(perfil.cacheContadores())),",
                "        Armazem.de(rcm.<String, IdentityState>getCache(perfil.cacheContadores())),",
                // declaracao com papel trocado
                "RemoteCache<String, IdentityState> i = rcm.getCache(cli.replicacao.cacheSessoes());",
                // corretos
                "RemoteCache<String,SessionState> s = rcm.getCache(cli.replicacao.cacheSessoes());",
                "x = rcm.< String , IdentityState >getCache(perfil.cacheContadores());",
                // nao tipada: fora do escopo desta guarda
                "materializarTudo(rcm.getCache(cli.replicacao.cacheContadores()));",
                // comentario: ignorado
                "// RemoteCache<String, SessionState> s = rcm.getCache(cli.replicacao.cacheContadores());"));
        List<String> trocados = papeisTrocados(falsas);
        assertEquals(List.of(
                "A.java:1: SessionState com getCache(perfil.cacheContadores())",
                "A.java:3: IdentityState com getCache(cli.replicacao.cacheSessoes())"), trocados);
        assertEquals(5, chamadasTipadas(falsas).size(), chamadasTipadas(falsas).toString());

        // N3: sessoes duas vezes
        Map<Path, String> n3 = Map.of(Path.of("W.java"),
                "h.healthy(cli.replicacao.cacheSessoes()), h.healthy(cli.replicacao.cacheSessoes()));");
        assertEquals(1, healthyForaDePar(n3).size(), healthyForaDePar(n3).toString());
        // ordem invertida
        Map<Path, String> inv = Map.of(Path.of("W.java"),
                "h.healthy(cli.replicacao.cacheContadores()), h.healthy(cli.replicacao.cacheSessoes()));");
        assertEquals(1, healthyForaDePar(inv).size(), healthyForaDePar(inv).toString());
        // so um dos dois (contadores esquecido)
        Map<Path, String> so = Map.of(Path.of("W.java"), "h.healthy(cli.replicacao.cacheSessoes()), true);");
        assertEquals(2, healthyForaDePar(so).size(), healthyForaDePar(so).toString());
        // correto, inclusive quebrado em duas linhas; a declaracao do metodo nao conta
        Map<Path, String> ok = new TreeMap<>();
        ok.put(Path.of("H.java"), "public boolean healthy(String cache) {");
        ok.put(Path.of("W.java"), "h.healthy(cli.replicacao.cacheSessoes()),\n   h.healthy(cli.replicacao.cacheContadores()));");
        assertEquals(List.of(), healthyForaDePar(ok));
        assertEquals(2, chamadasHealthy(ok).size());
    }
}
