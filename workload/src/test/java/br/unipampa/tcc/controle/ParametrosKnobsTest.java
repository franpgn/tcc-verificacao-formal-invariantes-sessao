package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-27, J1: parametros dos knobs K1..K4 (padrao, validacao e leitura estrita de JSON). */
class ParametrosKnobsTest {

    @TempDir
    Path dir;

    private static Map<String, String> validos() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("versao", "\"k-teste\"");
        m.put("k1FracaoConservador", "0.25");
        m.put("k2PausaConservadorNs", "7000000");
        m.put("k2MultPensarConservador", "2.0");
        m.put("k3AdiaMaxNs", "20000000000");
        m.put("k3SondagemNs", "50000000");
        m.put("k4FatorBaseConservador", "3.0");
        m.put("k4JitterConservador", "0.25");
        return m;
    }

    private Path escrever(Map<String, String> campos) throws Exception {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : campos.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": ").append(e.getValue());
            sb.append(++i < campos.size() ? ",\n" : "\n");
        }
        sb.append("}\n");
        Path f = Files.createTempFile(dir, "knobs-", ".json");
        Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        return f;
    }

    private void invalido(Map<String, String> campos, String campoCitado) throws Exception {
        Path f = escrever(campos);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ParametrosKnobs.deJson(f));
        assertTrue(e.getMessage().contains(campoCitado), "mensagem nao cita " + campoCitado + ": " + e.getMessage());
    }

    @Test
    @DisplayName("J1: padrao() tem os valores da secao 3.4 do plano")
    void padrao() {
        ParametrosKnobs p = ParametrosKnobs.padrao();
        assertEquals("provisorio-2026-10-22", p.versao());
        assertEquals(ParametrosKnobs.VERSAO_PADRAO, p.versao());
        assertEquals(0.5, p.k1FracaoConservador());
        assertEquals(5_000_000L, p.k2PausaConservadorNs());
        assertEquals(1.5, p.k2MultPensarConservador());
        assertEquals(30_000_000_000L, p.k3AdiaMaxNs());
        assertEquals(100_000_000L, p.k3SondagemNs());
        assertEquals(2.0, p.k4FatorBaseConservador());
        assertEquals(0.5, p.k4JitterConservador());
        assertNull(p.sha256());
    }

    @Test
    @DisplayName("J1: JSON valido e lido com o SHA-256 dos bytes do arquivo")
    void valido() throws Exception {
        Path f = escrever(validos());
        ParametrosKnobs p = ParametrosKnobs.deJson(f);
        assertEquals(new ParametrosKnobs("k-teste", 0.25, 7_000_000L, 2.0, 20_000_000_000L, 50_000_000L, 3.0, 0.25,
                p.sha256()), p);
        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)));
        assertEquals(esperado, p.sha256());
    }

    @Test
    @DisplayName("J1: campo desconhecido (inclusive sha256) e campo ausente sao rejeitados citando o campo")
    void desconhecidoEAusente() throws Exception {
        Map<String, String> m = validos();
        m.put("k5Qualquer", "1");
        invalido(m, "k5Qualquer");
        m = validos();
        m.put("sha256", "\"abc\"");
        invalido(m, "sha256");
        for (String campo : validos().keySet()) {
            Map<String, String> sem = validos();
            sem.remove(campo);
            invalido(sem, campo);
        }
    }

    @Test
    @DisplayName("J1: tipo errado e rejeitado citando o campo")
    void tipoErrado() throws Exception {
        String[][] casos = {
                {"versao", "3"},
                {"k1FracaoConservador", "\"0.5\""},
                {"k2PausaConservadorNs", "5.5"},
                {"k2PausaConservadorNs", "\"5\""},
                {"k2MultPensarConservador", "true"},
                {"k3AdiaMaxNs", "1e10"},
                {"k3SondagemNs", "null"},
                {"k4FatorBaseConservador", "[2]"},
                {"k4JitterConservador", "{}"},
        };
        for (String[] c : casos) {
            Map<String, String> m = validos();
            m.put(c[0], c[1]);
            invalido(m, c[0]);
        }
    }

    @Test
    @DisplayName("J1: cada regra de validacao, no construtor e no JSON, cita o campo")
    void validacoes() throws Exception {
        String[][] casos = {
                {"k1FracaoConservador", "0"},
                {"k1FracaoConservador", "-0.1"},
                {"k1FracaoConservador", "1.01"},
                {"k2PausaConservadorNs", "-1"},
                {"k2MultPensarConservador", "0.99"},
                {"k3AdiaMaxNs", "0"},
                {"k3SondagemNs", "0"},
                {"k3SondagemNs", "20000000001"},
                {"k4FatorBaseConservador", "0.5"},
                {"k4JitterConservador", "-0.01"},
                {"k4JitterConservador", "1.0"},
        };
        for (String[] c : casos) {
            Map<String, String> m = validos();
            m.put(c[0], c[1]);
            invalido(m, c[0]);
        }
        // limites aceitos
        Map<String, String> m = validos();
        m.put("k1FracaoConservador", "1.0");
        m.put("k2PausaConservadorNs", "0");
        m.put("k2MultPensarConservador", "1.0");
        m.put("k3SondagemNs", "20000000000");
        m.put("k4FatorBaseConservador", "1.0");
        m.put("k4JitterConservador", "0.0");
        ParametrosKnobs.deJson(escrever(m));
        assertThrows(IllegalArgumentException.class,
                () -> new ParametrosKnobs(null, 0.5, 0, 1.5, 10, 1, 2.0, 0.5, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ParametrosKnobs("v", Double.NaN, 0, 1.5, 10, 1, 2.0, 0.5, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ParametrosKnobs("v", 0.5, 0, Double.POSITIVE_INFINITY, 10, 1, 2.0, 0.5, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ParametrosKnobs("v", 0.5, 0, 1.5, 10, 1, Double.NaN, 0.5, null));
    }

    @Test
    @DisplayName("J1: JSON malformado ou raiz que nao e objeto")
    void malformado() throws Exception {
        Path f = Files.createTempFile(dir, "knobs-", ".json");
        Files.writeString(f, "{ \"versao\": ", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ParametrosKnobs.deJson(f));
        Files.writeString(f, "[1, 2]", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ParametrosKnobs.deJson(f));
    }
}
