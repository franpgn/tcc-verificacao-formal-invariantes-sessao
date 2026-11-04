package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3 (B-28.2, item 6 do prompt): {@code convergencia-resumo-rep-NNN.json} ganha {@code t_fim_carga_ns}
 * (instante em que as threads de carga pararam, no relogio dos {@code end_ns}), aditivo: os campos de
 * antes continuam, na mesma ordem, e o novo vem por ultimo.
 */
class ConvergenciaResumoFimCargaTest {

    static final long S = 1_000_000_000L;

    @TempDir
    Path dir;

    static List<String> campos(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.fieldNames().forEachRemaining(r::add);
        return r;
    }

    @Test
    @DisplayName("contrato: campos anteriores na mesma ordem e t_fim_carga_ns ao fim, com o valor passado")
    void contrato() throws Exception {
        ConvergenciaLoop.Resultado r = new ConvergenciaLoop.Resultado(
                List.of(new ConvergenciaLoop.Rodada(10 * S, true, 0, 0, null)), true, 10 * S, 4 * S, false);
        Path p = dir.resolve("convergencia-resumo-rep-001.json");
        ConvergenciaLoop.escreverResumo(p, r, 6 * S, 5 * S);
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        assertEquals(List.of("t_fim_ns", "convergiu", "divergencia_persistente", "t_convergencia_ns",
                "t_convergencia_abs_ns", "rodadas", "rodadas_validas", "t_fim_carga_ns"), campos(n));
        assertEquals(6 * S, n.get("t_fim_ns").asLong());
        assertEquals(5 * S, n.get("t_fim_carga_ns").asLong());
        assertEquals(4 * S, n.get("t_convergencia_ns").asLong());
    }

    @Test
    @DisplayName("a assinatura antiga continua gravando os mesmos campos de antes, com t_fim_carga_ns nulo")
    void assinaturaAntiga() throws Exception {
        ConvergenciaLoop.Resultado r = new ConvergenciaLoop.Resultado(List.of(), false, null, null, true);
        Path p = dir.resolve("antigo.json");
        ConvergenciaLoop.escreverResumo(p, r, 6 * S);
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        assertEquals("t_fim_carga_ns", campos(n).get(campos(n).size() - 1));
        assertTrue(n.get("t_fim_carga_ns").isNull());
    }

    @Test
    @DisplayName("executarConvergencia passa o fim da carga ao resumo (fonte de WorkloadMain)")
    void workloadMainPassaFimDaCarga() throws Exception {
        String fonte = java.nio.file.Files.readString(NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java"));
        String s = DetectorFonte.semComentarios(fonte).replaceAll("\\s+", " ");
        assertTrue(s.contains("res, tFimNs, fimCargaNs);"), "escreverResumo(..., res, tFimNs, fimCargaNs)");
    }
}
