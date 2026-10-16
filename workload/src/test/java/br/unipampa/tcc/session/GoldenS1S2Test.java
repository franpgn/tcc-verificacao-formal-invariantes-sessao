package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B-33 (E2, condicao C-3 da validacao V-E1): golden de S1/S2. As referencias em
 * {@code src/test/resources/golden-8e2e815/} foram geradas com as classes compiladas da base
 * 8e2e815 (antes do B-33), nao com o codigo atual. O CSV de eventos sem marcadores e a
 * sequencia de operacoes por semente de S1/S2 devem continuar identicos byte a byte.
 */
class GoldenS1S2Test {

    private static String recurso(String nome) throws Exception {
        try (InputStream in = GoldenS1S2Test.class.getResourceAsStream("/golden-8e2e815/" + nome)) {
            // separador de linha da plataforma (newLine) nao faz parte do contrato: normaliza CRLF
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("
", "
");
        }
    }

    /** Reescreve o op_id (contador global do processo) relativo ao primeiro evento do arquivo. */
    private static String normalizarOpId(String csv) {
        String[] linhas = csv.split("\n", -1);
        StringBuilder sb = new StringBuilder(linhas[0]).append('\n');
        long base = Long.parseLong(linhas[1].substring(0, linhas[1].indexOf(',')));
        for (int i = 1; i < linhas.length; i++) {
            if (linhas[i].isEmpty()) continue;
            int v = linhas[i].indexOf(',');
            long id = Long.parseLong(linhas[i].substring(0, v)) - base;
            sb.append(id).append(linhas[i].substring(v)).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("CSV de eventos sem marcadores e identico, byte a byte, ao gerado pela base 8e2e815")
    void csvDeEventosIdenticoAoDaBase() throws Exception {
        EventCsvWriter w = new EventCsvWriter(WarmupPolicy.always());
        w.registrar("Login", 1000000000L, 1000000450L, "127.0.0.1:11222", "OK_LOGIN", "user-0");
        w.registrar("Validate", 1000001000L, 1000001200L, "127.0.0.1:11223", "OK_VALIDATE_VALID", "sid-000001");
        w.registrar("Validate", 1000002000L, 1000002100L, "127.0.0.1:11224", "NOT_FOUND", "sid-000002");
        w.registrar("Logout", 1000003000L, 1000003900L, "127.0.0.1:11222", "NONE", "sid-000001");
        w.registrar("Login", 1000004000L, 1000004050L, "127.0.0.1:11222", "BLOCKED", "user-3");
        w.registrar("IncrementFailure", 1000005000L, 1000005300L, "127.0.0.1:11223", "OK_STATUS", "user-3");
        w.registrar("ResetFailures", 1000006000L, 1000006100L, "127.0.0.1:11224", "NONE", "user-3");
        w.registrar("Validate", 1000007000L, 1000007100L, "127.0.0.1:11222", "ERROR_TRANSPORT", "sid-000009");
        w.registrar("Login", 1000008000L, 1000008100L, "a,b", "NONE", "chave,com\nquebra");
        Path tmp = Files.createTempFile("golden-ev", ".csv");
        try {
            w.flush(tmp);
            String atual = normalizarOpId(Files.readString(tmp, StandardCharsets.UTF_8).replace("
", "
"));
            assertEquals(recurso("eventos-sem-marcadores.csv"), atual);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("sequencia de operacoes de S1/S2 e de chaves por semente identica a da base")
    void sequenciaPorSementeIdenticaADaBase() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Scenario s : new Scenario[] {Scenario.S1, Scenario.S2}) {
            Random r = new Random(42);
            sb.append("# ").append(s).append('\n');
            for (int i = 0; i < 2000; i++) sb.append(s.proximaOperacao(r)).append('\n');
        }
        KeyGenerator k = new KeyGenerator(42);
        sb.append("# chaves\n");
        for (int i = 0; i < 500; i++) sb.append(k.nextSessionKey()).append('\n');
        assertEquals(recurso("sequencia-s1-s2-seed42.txt"), sb.toString());
    }

    @Test
    @DisplayName("CSV de violacoes sem marcadores mantem cabecalho e formato da base")
    void csvDeViolacoesIdenticoAoDaBase() throws Exception {
        InvariantAuditor au = new InvariantAuditor();
        au.registrarViolacao("I2", "sid-1", "valida sob bloqueio");
        Path tmp = Files.createTempFile("golden-viol", ".csv");
        try {
            au.dumpCsv(tmp);
            List<String> l = new ArrayList<>(Files.readAllLines(tmp, StandardCharsets.UTF_8));
            assertEquals(recurso("violacoes-cabecalho.txt"), l.get(0) + "\n");
            String linha = l.get(1);
            assertEquals(recurso("violacoes-linha-sem-timestamp.txt"),
                    linha.substring(linha.indexOf(',')) + "\n");
            assertEquals(2, l.size());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
