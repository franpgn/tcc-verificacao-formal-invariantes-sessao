package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (P-6 da V-W5): com o controlador ligado, {@code convergencia-resumo-rep-NNN.json} ganha, depois de
 * {@code t_fim_controle_ns}, os campos do religamento ({@code t_sinal_fim_carga_ns}, {@code religamento_ac5_alvo},
 * {@code religamento_prazo_ns}, {@code t_religamento_ns}, {@code religamento_esgotado}, {@code religamento_leituras}).
 * O nome {@code t_religamento_ns} e o que {@code analysis/comparar_bracos.py} le ({@code CAMPO_RELIGAMENTO}, W-5).
 * A assinatura de cinco argumentos (controlador desligado) continua gravando exatamente os campos de antes
 * ({@link ConvergenciaResumoFimCargaTest}).
 */
class ConvergenciaResumoReligamentoTest {

    static final long S = 1_000_000_000L;

    @TempDir
    Path dir;

    static List<String> campos(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.fieldNames().forEachRemaining(r::add);
        return r;
    }

    static ConvergenciaLoop.Resultado convergiu() {
        return new ConvergenciaLoop.Resultado(
                List.of(new ConvergenciaLoop.Rodada(310 * S, true, 0, 0, "")), true, 310 * S, 10 * S, false);
    }

    static final List<String> ANTERIORES = List.of("t_fim_ns", "convergiu", "divergencia_persistente",
            "t_convergencia_ns", "t_convergencia_abs_ns", "rodadas", "rodadas_validas", "t_fim_carga_ns",
            "t_fim_controle_ns", "convergencia_estado");

    @Test
    @DisplayName("com AC-5: campos anteriores na mesma ordem e os do religamento ao fim, com os valores")
    void comAc5() throws Exception {
        Religamento.Resultado rel = new Religamento.Resultado("127.0.0.1:11224", 301 * S, 180 * S, 305 * S, false, 3);
        Path p = dir.resolve("convergencia-resumo-rep-001.json");
        ConvergenciaLoop.escreverResumo(p, convergiu(), 300 * S, 300 * S, 301 * S, rel);
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        List<String> esperado = new ArrayList<>(ANTERIORES);
        esperado.addAll(List.of("t_sinal_fim_carga_ns", "religamento_ac5_alvo", "religamento_prazo_ns",
                "t_religamento_ns", "religamento_esgotado", "religamento_leituras"));
        assertEquals(esperado, campos(n));
        assertEquals(305 * S, n.get("t_religamento_ns").asLong());
        assertEquals("127.0.0.1:11224", n.get("religamento_ac5_alvo").asText());
        assertEquals(180 * S, n.get("religamento_prazo_ns").asLong());
        assertEquals(301 * S, n.get("t_sinal_fim_carga_ns").asLong());
        assertFalse(n.get("religamento_esgotado").asBoolean());
        assertEquals(3, n.get("religamento_leituras").asInt());
        assertEquals(300 * S, n.get("t_fim_carga_ns").asLong());
    }

    @Test
    @DisplayName("sem AC-5 (controlador ligado): os mesmos campos, com alvo, prazo e t_religamento_ns nulos")
    void semAc5() throws Exception {
        Path p = dir.resolve("r.json");
        ConvergenciaLoop.escreverResumo(p, convergiu(), 300 * S, 300 * S, 301 * S,
                Religamento.Resultado.semEspera(302 * S));
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        assertEquals(ANTERIORES.size() + 6, campos(n).size());
        assertTrue(n.get("t_religamento_ns").isNull(), "comparar_bracos le null como 'sem religamento'");
        assertTrue(n.get("religamento_ac5_alvo").isNull());
        assertTrue(n.get("religamento_prazo_ns").isNull());
        assertFalse(n.get("religamento_esgotado").asBoolean());
        assertEquals(0, n.get("religamento_leituras").asInt());
    }

    @Test
    @DisplayName("prazo esgotado: t_religamento_ns nulo e religamento_esgotado verdadeiro (Delta_conv nao avaliavel)")
    void esgotado() throws Exception {
        Religamento.Resultado rel = new Religamento.Resultado("127.0.0.1:11224", 301 * S, 180 * S, null, true, 91);
        Path p = dir.resolve("e.json");
        ConvergenciaLoop.escreverResumo(p, convergiu(), 300 * S, 300 * S, 301 * S, rel);
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        assertTrue(n.get("t_religamento_ns").isNull());
        assertTrue(n.get("religamento_esgotado").asBoolean());
        assertEquals(91, n.get("religamento_leituras").asInt());
    }

    @Test
    @DisplayName("a assinatura de cinco argumentos nao grava nenhum campo do religamento (controlador desligado)")
    void cincoArgumentosSemReligamento() throws Exception {
        Path p = dir.resolve("off.json");
        ConvergenciaLoop.escreverResumo(p, convergiu(), 300 * S, 300 * S, null);
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        assertEquals(ANTERIORES, campos(n));
        assertFalse(Files.readString(p).contains("religamento"));
    }
}
