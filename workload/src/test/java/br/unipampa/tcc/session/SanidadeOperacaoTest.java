package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * B-43.3a (parte i), decisao do Gestor de 2026-10-22 (questao 1 da
 * V-B43.3a-i): uma {@code operation} fora da mistura do cenario declarado torna
 * o arquivo invalido para o cenario (saida 2, sem JSON), em vez de entrar nas
 * contagens e diluir as fracoes. {@code return_code} continua livre.
 *
 * <p>Mistura do cenario = as operacoes com peso &gt; 0 em {@link Scenario}, mais
 * Validate e Logout quando a triade Login esta na mistura. S1: as cinco. S2:
 * sem ResetFailures (peso 0).</p>
 */
class SanidadeOperacaoTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    private int rodar(Path csv, Scenario s, Path json) {
        return VerificadorSanidade.executar(
                new String[] {"--csv", csv.toString(), "--scenario", s.name(), "--saida", json.toString()},
                new PrintStream(buf, true, StandardCharsets.UTF_8));
    }

    private String saida() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** Fixture sa com {@code n} eventos {@code op} intercalados a cada 1 000. */
    private static List<FixtureSanidade.Ev> saCom(Scenario s, String op, String rc, String fase, int n) {
        List<FixtureSanidade.Ev> sa = FixtureSanidade.sa(s, 42L, 20_000);
        List<FixtureSanidade.Ev> r = new ArrayList<>();
        int postos = 0;
        for (int i = 0; i < sa.size(); i++) {
            r.add(fase == null ? sa.get(i) : sa.get(i).naFase("antes"));
            if (i % 1_000 == 999 && postos < n) {
                r.add(new FixtureSanidade.Ev(op, rc, fase));
                postos++;
            }
        }
        assertEquals(n, postos);
        return r;
    }

    @Test
    void operacoesDaMistura() {
        assertEquals(Set.of("Login", "Validate", "Logout", "IncrementFailure", "ResetFailures"),
                Sanidade.operacoesDe(Scenario.S1));
        assertEquals(Set.of("Login", "Validate", "Logout", "IncrementFailure"),
                Sanidade.operacoesDe(Scenario.S2));
    }

    /**
     * O caso da V-B43.3a-i: um CSV S3 com poucos Unblock (bem abaixo de 2 p.p.)
     * verificado como S1. Antes desta regra saia OK em silencio (diluicao
     * pequena, sem MISTURA_DESVIO).
     */
    @Test
    void unblockVerificadoComoS1Da2() throws Exception {
        Path csv = FixtureSanidade.gravar(tmp, "s3.csv",
                saCom(Scenario.S1, SessionOps.OP_UNBLOCK, "OK", null, 5), false);
        Path json = tmp.resolve("s3.json");

        assertEquals(2, rodar(csv, Scenario.S1, json));
        assertTrue(saida().contains("'Unblock'"), saida());
        assertTrue(saida().contains("S1"), saida());
        assertTrue(saida().matches("(?s).*linha \\d+.*"), saida());
        assertFalse(Files.exists(json), "arquivo invalido nao grava JSON");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Block", "Unblock", "login", "Validar", ""})
    void operacaoForaDaMisturaDeS1Da2(String op) throws Exception {
        Path csv = FixtureSanidade.gravar(tmp, "x.csv",
                saCom(Scenario.S1, op, "OK", null, 1), false);
        assertEquals(2, rodar(csv, Scenario.S1, tmp.resolve("x.json")));
        assertTrue(saida().contains("'" + op + "'"), saida());
    }

    @Test
    void resetFailuresEmS2Da2() throws Exception {
        // S2 tem peso 0 para ResetFailures: o evento nao e da mistura de S2.
        Path csv = FixtureSanidade.gravar(tmp, "s2.csv",
                saCom(Scenario.S2, "ResetFailures", "NONE", null, 1), false);
        assertEquals(2, rodar(csv, Scenario.S2, tmp.resolve("s2.json")));
        assertTrue(saida().contains("'ResetFailures'"), saida());
    }

    @Test
    void operacaoForaDaMisturaNaRegiaoDentroTambemDa2() throws Exception {
        List<FixtureSanidade.Ev> evs = saCom(Scenario.S1, "Login", "OK", "antes", 0);
        evs.add(new FixtureSanidade.Ev("Block", "ERROR_UNAVAILABLE", "durante"));
        Path csv = FixtureSanidade.gravar(tmp, "d.csv", evs, true);
        assertEquals(2, rodar(csv, Scenario.S1, tmp.resolve("d.json")));
        assertTrue(saida().contains("'Block'"), saida());
    }

    @Test
    void returnCodeContinuaLivre() throws Exception {
        // Um return_code nunca visto nao invalida o arquivo: sai 0 (fixture sa).
        Path csv = FixtureSanidade.gravar(tmp, "rc.csv",
                saCom(Scenario.S1, "IncrementFailure", "ERROR_CODIGO_NOVO", null, 3), false);
        assertEquals(0, rodar(csv, Scenario.S1, tmp.resolve("rc.json")), saida());
    }
}
