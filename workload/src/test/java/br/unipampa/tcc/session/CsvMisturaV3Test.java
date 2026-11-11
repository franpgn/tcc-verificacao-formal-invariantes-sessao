package br.unipampa.tcc.session;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T9: o CSV de eventos da v3 so contem as operacoes da mistura (a reposicao nao gera linha):
 * {@code registrados = operacoes + 2 x logins OK}, e o {@link VerificadorSanidade} da base da OK.
 */
class CsvMisturaV3Test {

    private static final int OPERACOES = 10_000;

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void csvSoComAMistura(Scenario cenario, @TempDir Path dir) throws Exception {
        MundoV3 w = new MundoV3(4, true);
        w.rodar(cenario, OPERACOES, 42);
        long loginsOk = w.ops.contar(SessionOps.OP_LOGIN, OpResult.Code.OK_LOGIN);
        assertTrue(w.ops.reposicoes() > 0, "houve reposicoes");
        assertEquals(OPERACOES + 2 * loginsOk, w.eventos.totalRegistrados(),
                "registrados = operacoes + 2 x logins OK (reposicao fora do CSV)");

        Path csv = dir.resolve("rep-001.csv");
        w.eventos.flush(csv, null);
        List<String> linhas = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(EventCsvWriter.HEADER, linhas.get(0));
        assertEquals(w.eventos.totalRegistrados(), linhas.size() - 1);
        Set<String> operacoes = new TreeSet<>();
        long sidLogin = 0;
        long logoutsSid = 0;
        for (String l : linhas.subList(1, linhas.size())) {
            String[] c = l.split(",", -1);
            operacoes.add(c[1]);
            if (c[1].equals(SessionOps.OP_LOGIN) && c[6].startsWith(KeyGenerator.PREFIXO_SID)) {
                sidLogin++;
            }
            if (c[1].equals(SessionOps.OP_LOGOUT) && c[6].startsWith(KeyGenerator.PREFIXO_SID)) {
                logoutsSid++;
            }
        }
        Set<String> esperadas = new TreeSet<>(Sanidade.operacoesDe(cenario));
        assertTrue(esperadas.containsAll(operacoes), "fora da mistura: " + operacoes);
        if (cenario == Scenario.S2) {
            assertFalse(operacoes.contains(SessionOps.OP_RESET_FAILURES));
        }
        assertEquals(0, sidLogin, "nenhum Login com chave de slot (reposicao)");
        assertEquals(w.mistura.logoutIsolados(), logoutsSid, "Logout isolado no CSV = logout_isolados");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int saida = VerificadorSanidade.executar(new String[] {"--csv", csv.toString(), "--scenario", cenario.name(),
                "--saida", dir.resolve("sanidade.json").toString()},
                new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertEquals(VerificadorSanidade.SAIDA_OK, saida, out);
        assertTrue(out.contains("veredito=OK"), out);
    }
}
