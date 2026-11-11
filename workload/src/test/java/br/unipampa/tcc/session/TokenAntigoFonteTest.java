package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7b, U9: guardas de fonte (sem comentarios, {@link DetectorFonte}) do token antigo semeado
 * (DT-008). A producao da a cada thread o seu {@code Cliente} com a semente da thread
 * ({@code misturaV3.cliente(seed)} em {@code rodarCliente}); nao ha {@code cliente()} sem argumento
 * em producao (seria a mesma semente para todas as threads); {@code MisturaV3} nao usa
 * {@code ThreadLocalRandom}, {@code Math.random} nem recebe o {@code Random} da mistura; e a semente
 * da thread em {@code executarRepeticao} vem de {@code sementeThread(}.
 */
class TokenAntigoFonteTest {

    private static String fonte(String arquivo) throws Exception {
        return ArmazemVersionadoTest.fonteSemComentarios(arquivo);
    }

    private static String mascarado(String arquivo) throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/" + arquivo);
        return DetectorFonte.mascarado(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private static String corpo(String arquivo, String regexAssinatura) throws Exception {
        int[] c = DetectorFonte.corpo(mascarado(arquivo), regexAssinatura);
        assertNotNull(c, "metodo nao encontrado: " + regexAssinatura);
        return fonte(arquivo).substring(c[0], c[1] + 1);
    }

    @Test
    @DisplayName("U9: rodarCliente cria o Cliente com a semente da thread: misturaV3.cliente(seed)")
    void rodarClienteUsaSemente() throws Exception {
        String c = corpo("WorkloadMain.java", "private static void rodarCliente\\(");
        assertTrue(c.contains("misturaV3.cliente(seed)"), "rodarCliente sem misturaV3.cliente(seed)");
        assertTrue(c.contains("new KeyGenerator(seed)"), "o KeyGenerator continua com a mesma semente");
        assertTrue(c.contains("executarOperacao(ops, cenario, r, chaves, ids, v3);"), "laco inalterado");
    }

    @Test
    @DisplayName("U9: nenhum cliente() sem argumento no codigo de producao")
    void semClienteSemArgumento() throws Exception {
        Path raiz = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java");
        Pattern semArg = Pattern.compile("\\bcliente\\s*\\(\\s*\\)");
        List<String> achados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path p : arquivos.filter(x -> x.toString().endsWith(".java")).toList()) {
                String s = DetectorFonte.semComentarios(Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"));
                Matcher m = semArg.matcher(s);
                while (m.find()) {
                    achados.add(raiz.relativize(p) + "@" + m.start());
                }
            }
        }
        assertEquals(List.of(), achados, "cliente() sem argumento em producao");
    }

    @Test
    @DisplayName("U9: MisturaV3 nao usa ThreadLocalRandom, Math.random nem o Random da mistura")
    void misturaV3SemOutroRandom() throws Exception {
        String s = fonte("MisturaV3.java");
        assertFalse(s.contains("ThreadLocalRandom"), "MisturaV3 referencia ThreadLocalRandom");
        assertFalse(s.contains("Math.random"), "MisturaV3 referencia Math.random");
        assertTrue(s.contains("new Random(sementeToken(sementeThread))"), "Random proprio semeado por sementeToken");
        assertTrue(Pattern.compile("boolean proximoEhTokenAntigo\\(\\)").matcher(s).find(),
                "proximoEhTokenAntigo nao pode receber argumento (Random da mistura)");
        String op = corpo("WorkloadMain.java",
                "static void executarOperacao\\(SessionOps ops, Scenario cenario, Random r, KeyGenerator chaves,\\s*"
                        + "IdentidadesMistura ids, MisturaV3\\.Cliente v3\\)");
        assertTrue(op.contains("boolean antigo = v3.proximoEhTokenAntigo();"), "sorteio do token sem argumento");
        assertFalse(Pattern.compile("\\br\\.next\\w*\\(").matcher(op).find(),
                "executarOperacao nao pode sortear nada de r alem de proximaOperacao(r)");
    }

    @Test
    @DisplayName("U9: a semente da thread em executarRepeticao vem de sementeThread(cli.seedBase, tid, rep)")
    void sementeDaThreadPorMetodo() throws Exception {
        String c = corpo("WorkloadMain.java", DetectorFonte.ASSINATURA_REPETICAO);
        Matcher m = Pattern.compile("final long seedThread = sementeThread\\(cli\\.seedBase, tid, rep\\);").matcher(c);
        int n = 0;
        while (m.find()) {
            n++;
        }
        assertEquals(1, n, "esperada 1 linha 'final long seedThread = sementeThread(cli.seedBase, tid, rep);'");
        assertFalse(c.contains("(long) tid + (long) rep * 1_000_003L"), "formula da semente da thread em linha");
        assertTrue(c.contains("seedThread, opsAlvoPorThread, totalOps, parar, cli.mix, misturaV3"),
                "rodarCliente recebe seedThread");
    }
}
