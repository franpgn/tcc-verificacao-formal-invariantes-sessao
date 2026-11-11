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
 * B-42.1 T0-D5 (validacao V-B42.1-T0, secao 4), complemento de {@link PapelDoCacheTest} e de
 * {@link PerfilFixoForaDoResolvedorTest}, sem edita-los:
 * <ul>
 *   <li><b>papel na varredura (X1):</b> uma declaracao generica {@code Tipo<String, T> x = ...} cuja
 *       linha contem {@code getCache(...)} depois do {@code =} (por exemplo
 *       {@code Map<String, SessionState> sessoes = materializarTudo(rcm.getCache(...))}) tem de usar
 *       {@code cacheSessoes()} quando {@code T = SessionState} e {@code cacheContadores()} quando
 *       {@code T = IdentityState}. A guarda (b) original so reconhecia {@code RemoteCache<String, T>};
 *       com o tipo do valor inferido pelo metodo generico, papeis trocados compilam e a varredura
 *       final falha em silencio (ClassCastException capturada, "VARREDURA NAO EXECUTADA");</li>
 *   <li><b>perfil por {@code values()} (X5):</b> {@code PerfilReplicacao.values()} (ou
 *       {@code PerfilReplicacao.class.getEnumConstants()}) fora do resolvedor e uma forma de fixar
 *       o perfil por posicao, equivalente a {@code PerfilReplicacao.SYNC}.</li>
 * </ul>
 */
class PapelDoCacheComplementarTest {

    /** Declaracao generica {@code Tipo<String, T> nome =} (qualquer tipo externo de um nivel). */
    static final Pattern DECLARACAO_GENERICA =
            Pattern.compile("\\b\\w+\\s*<\\s*String\\s*,\\s*(\\w+)\\s*>\\s+\\w+\\s*=");

    /** Perfil escolhido por posicao no enum. */
    static final Pattern PERFIL_POR_POSICAO = Pattern.compile(
            "\\bPerfilReplicacao\\s*\\.\\s*(values\\s*\\(|class\\s*\\.\\s*getEnumConstants\\s*\\()");

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

    /** Chamadas {@code getCache} a direita de uma declaracao generica com tipo de valor conhecido. */
    static List<PapelDoCacheTest.ChamadaTipada> chamadasDeclaradas(Map<Path, String> fontes) {
        List<PapelDoCacheTest.ChamadaTipada> out = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                String l = linhas[i];
                if (comentario(l)) {
                    continue;
                }
                Matcher d = DECLARACAO_GENERICA.matcher(l);
                if (!d.find()) {
                    continue;
                }
                Matcher m = PapelDoCacheTest.GETCACHE.matcher(l);
                while (m.find()) {
                    if (m.start() >= d.end()) {
                        out.add(new PapelDoCacheTest.ChamadaTipada(
                                p.getFileName() + ":" + (i + 1), d.group(1), m.group(1).trim()));
                    }
                }
            }
        });
        return out;
    }

    /** Papeis trocados nas declaracoes genericas. */
    static List<String> papeisTrocados(Map<Path, String> fontes) {
        List<String> achados = new ArrayList<>();
        for (PapelDoCacheTest.ChamadaTipada c : chamadasDeclaradas(fontes)) {
            boolean ok = switch (c.tipo()) {
                case "SessionState" -> PapelDoCacheTest.ARG_SESSOES.matcher(c.arg()).matches();
                case "IdentityState" -> PapelDoCacheTest.ARG_CONTADORES.matcher(c.arg()).matches();
                default -> true;
            };
            if (!ok) {
                achados.add(c.onde() + ": " + c.tipo() + " com getCache(" + c.arg() + ")");
            }
        }
        return achados;
    }

    /** {@code PerfilReplicacao.values()}/{@code getEnumConstants()} fora do resolvedor. */
    static List<String> perfisPorPosicao(Map<Path, String> fontes) {
        List<String> achados = new ArrayList<>();
        fontes.forEach((p, txt) -> {
            String arquivo = p.getFileName().toString();
            if (arquivo.equals(PerfilFixoForaDoResolvedorTest.RESOLVEDOR)) {
                return;
            }
            String[] linhas = txt.split("\r?\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                if (!comentario(linhas[i]) && PERFIL_POR_POSICAO.matcher(linhas[i]).find()) {
                    achados.add(arquivo + ":" + (i + 1) + ": " + linhas[i].trim());
                }
            }
        });
        return achados;
    }

    @Test
    @DisplayName("a varredura ve as duas declaracoes materializarTudo da varredura final (nao e vacua)")
    void varreduraNaoEVacua() {
        assertTrue(fontes.size() >= 30, "poucas fontes lidas: " + fontes.size());
        List<PapelDoCacheTest.ChamadaTipada> cs = chamadasDeclaradas(fontes);
        List<PapelDoCacheTest.ChamadaTipada> varredura = cs.stream()
                .filter(c -> c.onde().startsWith("WorkloadMain.java:"))
                .filter(c -> linha(c.onde()).contains("materializarTudo("))
                .toList();
        assertTrue(varredura.stream().anyMatch(c -> c.tipo().equals("SessionState")),
                "declaracao Map<String, SessionState> = materializarTudo(getCache(...)) nao vista: " + cs);
        assertTrue(varredura.stream().anyMatch(c -> c.tipo().equals("IdentityState")),
                "declaracao Map<String, IdentityState> = materializarTudo(getCache(...)) nao vista: " + cs);
        // o resolvedor usa values() (e e isento); a varredura tem de enxerga-lo
        String resolvedor = fontes.entrySet().stream()
                .filter(e -> e.getKey().getFileName().toString().equals(PerfilFixoForaDoResolvedorTest.RESOLVEDOR))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertTrue(resolvedor.contains("values()"), "o resolvedor deveria iterar values()");
    }

    private static String linha(String onde) {
        int c = onde.indexOf(':');
        String arquivo = onde.substring(0, c);
        int n = Integer.parseInt(onde.substring(c + 1));
        String txt = fontes.entrySet().stream()
                .filter(e -> e.getKey().getFileName().toString().equals(arquivo))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        return txt.split("\r?\n", -1)[n - 1];
    }

    @Test
    @DisplayName("declaracao generica com SessionState usa cacheSessoes(); com IdentityState usa cacheContadores()")
    void papelCasaComOTipoNaDeclaracao() {
        List<String> a = papeisTrocados(fontes);
        assertEquals(List.of(), a, "papel do cache trocado:\n" + String.join("\n", a));
    }

    @Test
    @DisplayName("nenhum PerfilReplicacao.values()/getEnumConstants() fora do resolvedor")
    void semPerfilPorPosicao() {
        List<String> a = perfisPorPosicao(fontes);
        assertEquals(List.of(), a,
                "perfil escolhido por posicao fora de PerfilReplicacao (use cli.replicacao/perfil):\n"
                        + String.join("\n", a));
    }

    @Test
    @DisplayName("o detector pega X1 e X5 e ignora as formas corretas")
    void detectorPegaFormasConhecidas() {
        Map<Path, String> falsas = new TreeMap<>();
        falsas.put(Path.of("A.java"), String.join("\n",
                // X1: varredura com papeis trocados
                "Map<String, SessionState> sessoes = materializarTudo(rcm.getCache(cli.replicacao.cacheContadores()));",
                "Map<String, IdentityState> identidades = materializarTudo(rcm.getCache(cli.replicacao.cacheSessoes()));",
                // corretos
                "Map<String, SessionState> s = materializarTudo(rcm.getCache(cli.replicacao.cacheSessoes()));",
                "Map< String , IdentityState > i = materializarTudo(rcm.getCache(cli.replicacao.cacheContadores()));",
                // getCache a esquerda do '=' nao e da declaracao
                "x(rcm.getCache(cli.replicacao.cacheContadores())); Map<String, SessionState> y = z;",
                // tipo de valor desconhecido: fora do escopo
                "Map<String, Object> o = materializarTudo(rcm.getCache(cli.replicacao.cacheContadores()));",
                // comentario: ignorado
                "// Map<String, SessionState> s = materializarTudo(rcm.getCache(cli.replicacao.cacheContadores()));"));
        assertEquals(List.of(
                "A.java:1: SessionState com getCache(cli.replicacao.cacheContadores())",
                "A.java:2: IdentityState com getCache(cli.replicacao.cacheSessoes())"), papeisTrocados(falsas));
        assertEquals(5, chamadasDeclaradas(falsas).size(), chamadasDeclaradas(falsas).toString());

        Map<Path, String> perfis = new TreeMap<>();
        // X5: admin do S3 pelo primeiro perfil do enum
        perfis.put(Path.of("B.java"), "c.admin = new SessionOps(rcmAdmin, PerfilReplicacao.values()[0], latency);");
        perfis.put(Path.of("C.java"), "var p = PerfilReplicacao . values ( )[1];");
        perfis.put(Path.of("D.java"), "var p = PerfilReplicacao.class.getEnumConstants()[0];");
        // corretos: resolvedor isento, outro enum, comentario
        perfis.put(Path.of(PerfilFixoForaDoResolvedorTest.RESOLVEDOR), "for (PerfilReplicacao p : values()) {");
        perfis.put(Path.of("E.java"), "for (Bloqueio b : Bloqueio.values()) {\n"
                + "// PerfilReplicacao.values()[0]");
        assertEquals(List.of(
                "B.java:1: c.admin = new SessionOps(rcmAdmin, PerfilReplicacao.values()[0], latency);",
                "C.java:1: var p = PerfilReplicacao . values ( )[1];",
                "D.java:1: var p = PerfilReplicacao.class.getEnumConstants()[0];"), perfisPorPosicao(perfis));
    }
}
