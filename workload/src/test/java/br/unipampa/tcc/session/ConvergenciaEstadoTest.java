package br.unipampa.tcc.session;

import br.unipampa.tcc.session.ConvergenciaLoop.EstadoCluster;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (decisao do coordenador de 05/11, depois do I-3b): o resumo de convergencia separa "nenhuma rodada valida" de
 * "divergencia persistente". Com AC-5 e o no nao religado, toda rodada sai invalida ("topologia com 2 de 3") e o laco
 * termina com {@code divergencia_persistente = true}, o que nao e divergencia medida. O campo aditivo
 * {@code convergencia_estado} vale {@code convergiu}, {@code divergente} (houve rodada valida e a divergencia ficou) ou
 * {@code sem_rodada_valida} (nenhuma rodada valida ate o teto); a analise W-5 trata o ultimo como nao avaliavel.
 * {@code divergencia_persistente} nao muda de significado. O campo vem logo depois de {@code t_fim_controle_ns}, nos
 * dois formatos do resumo (com e sem o controlador).
 */
class ConvergenciaEstadoTest {

    static final long S = 1_000_000_000L;
    static final Instant T = Instant.parse("2026-11-05T12:00:00Z");
    static final SessionState VALIDA = SessionState.criar("s1", "u1", T);
    static final SessionState INVALIDA = VALIDA.invalidar(SessionState.Motivo.LOGOUT, T);

    @TempDir
    Path dir;

    static final class RelogioDeTeste implements ConvergenciaLoop.Relogio {
        long agora;

        RelogioDeTeste(long inicio) {
            agora = inicio;
        }

        @Override
        public long agoraNs() {
            return agora;
        }

        @Override
        public void dormirNs(long ns) {
            agora += ns;
        }
    }

    static Map<String, SessionState> mapa(SessionState s) {
        Map<String, SessionState> m = new HashMap<>();
        m.put(s.id(), s);
        return m;
    }

    static ConvergenciaLoop.Resultado rodar(EstadoCluster estado, SessionState segunda) {
        ConvergenciaLoop l = new ConvergenciaLoop(new InvariantAuditor(),
                List.of(() -> mapa(VALIDA), () -> mapa(segunda)), List.of(), () -> estado,
                3, new RelogioDeTeste(1000 * S), 5 * S, 60 * S, 3);
        return l.executar(1000 * S);
    }

    static List<String> campos(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.fieldNames().forEachRemaining(r::add);
        return r;
    }

    @Test
    @DisplayName("convergiu: tres rodadas validas limpas")
    void convergiu() {
        ConvergenciaLoop.Resultado r = rodar(new EstadoCluster(3, true, true), VALIDA);
        assertTrue(r.convergiu());
        assertEquals("convergiu", r.estado());
    }

    @Test
    @DisplayName("divergente: rodadas validas, divergencia ate o teto")
    void divergente() {
        ConvergenciaLoop.Resultado r = rodar(new EstadoCluster(3, true, true), INVALIDA);
        assertTrue(r.divergenciaPersistente());
        assertTrue(r.rodadas().stream().anyMatch(ConvergenciaLoop.Rodada::valida));
        assertEquals("divergente", r.estado());
    }

    @Test
    @DisplayName("sem_rodada_valida: topologia com 2 de 3 ate o teto (no parado pelo AC-5 e nao religado, I-3b)")
    void semRodadaValida() {
        ConvergenciaLoop.Resultado r = rodar(new EstadoCluster(2, true, true), VALIDA);
        assertTrue(r.divergenciaPersistente(), "divergencia_persistente nao muda de significado");
        assertFalse(r.rodadas().isEmpty());
        assertTrue(r.rodadas().stream().noneMatch(ConvergenciaLoop.Rodada::valida));
        assertEquals("sem_rodada_valida", r.estado());
    }

    @Test
    @DisplayName("resumo sem controlador: convergencia_estado logo depois de t_fim_controle_ns")
    void resumoCincoArgumentos() throws Exception {
        Path p = dir.resolve("a.json");
        ConvergenciaLoop.escreverResumo(p, rodar(new EstadoCluster(2, true, true), VALIDA), 1000 * S, 1000 * S, null);
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        List<String> c = campos(n);
        assertEquals(c.indexOf("t_fim_controle_ns") + 1, c.indexOf("convergencia_estado"));
        assertEquals("sem_rodada_valida", n.get("convergencia_estado").asText());
        assertTrue(n.get("divergencia_persistente").asBoolean());
    }

    @Test
    @DisplayName("resumo com controlador: convergencia_estado antes dos campos do religamento")
    void resumoComReligamento() throws Exception {
        Path p = dir.resolve("b.json");
        ConvergenciaLoop.escreverResumo(p, rodar(new EstadoCluster(3, true, true), INVALIDA), 1000 * S, 1000 * S,
                1001 * S, Religamento.Resultado.semEspera(1002 * S));
        JsonNode n = new ObjectMapper().readTree(p.toFile());
        List<String> c = campos(n);
        assertEquals(c.indexOf("t_fim_controle_ns") + 1, c.indexOf("convergencia_estado"));
        assertEquals(c.indexOf("convergencia_estado") + 1, c.indexOf("t_sinal_fim_carga_ns"));
        assertEquals("divergente", n.get("convergencia_estado").asText());
    }
}
