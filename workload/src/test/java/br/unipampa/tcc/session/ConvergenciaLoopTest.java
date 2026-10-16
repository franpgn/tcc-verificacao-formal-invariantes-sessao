package br.unipampa.tcc.session;

import br.unipampa.tcc.session.ConvergenciaLoop.EstadoCluster;
import br.unipampa.tcc.session.ConvergenciaLoop.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 3): laco de convergencia (O-5). Relogio e sonda de topologia
 * injetaveis; nenhum teste dorme de verdade.
 */
class ConvergenciaLoopTest {

    private static final long S = 1_000_000_000L;
    private static final Instant T = Instant.parse("2026-10-15T12:00:00Z");
    private static final EstadoCluster OK = new EstadoCluster(3, true, true);

    /** Relogio falso: dormir apenas avanca o instante. */
    private static final class RelogioFalso implements ConvergenciaLoop.Relogio {
        long agora;
        RelogioFalso(long inicio) { agora = inicio; }
        @Override public long agoraNs() { return agora; }
        @Override public void dormirNs(long ns) { agora += ns; }
    }

    private static final SessionState VALIDA = SessionState.criar("s1", "u1", T);
    private static final SessionState INVALIDA = VALIDA.invalidar(SessionState.Motivo.LOGOUT, T);

    private static Map<String, SessionState> mapa(SessionState s) {
        Map<String, SessionState> m = new HashMap<>();
        m.put(s.id(), s);
        return m;
    }

    /** Monta o laco; rodada.incrementAndGet() acontece na sonda (1 sonda por rodada). */
    private static ConvergenciaLoop laco(InvariantAuditor a, RelogioFalso rel, AtomicInteger rodada,
            IntFunction<EstadoCluster> sonda,
            List<Supplier<Map<String, SessionState>>> leitores, long tetoNs) {
        return new ConvergenciaLoop(a, leitores, List.of(),
                () -> sonda.apply(rodada.getAndIncrement()),
                3, rel, 5 * S, tetoNs, 3);
    }

    @Test
    @DisplayName("t_convergencia = instante da 1a de 3 rodadas validas sem divergencia menos t_fim")
    void convergeNaTerceiraRodadaLimpa() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(1000 * S);
        AtomicInteger r = new AtomicInteger();
        // rodadas 0 e 1 divergem; 2, 3, 4 sao limpas (a sonda incrementa r antes da leitura)
        ConvergenciaLoop l = new ConvergenciaLoop(a, List.of(
                () -> mapa(VALIDA),
                () -> mapa(r.get() <= 2 ? INVALIDA : VALIDA)), List.of(),
                () -> { r.incrementAndGet(); return OK; },
                3, rel, 5 * S, 600 * S, 3);
        Resultado res = l.executar(1000 * S);
        assertTrue(res.convergiu());
        assertFalse(res.divergenciaPersistente());
        assertEquals(5, res.rodadas().size());
        assertEquals(1000 * S + 2 * 5 * S, res.tConvergenciaAbsNs());
        assertEquals(10 * S, res.tConvergenciaRelFimNs());
        assertEquals(0, a.total(), "divergencia transitoria nao e violacao");
        assertEquals(2, res.rodadas().get(0).divergentesI5() + res.rodadas().get(1).divergentesI5());
    }

    @Test
    @DisplayName("rodada com topologia incompleta (2 de 3 servidores) e invalida e quebra a sequencia")
    void topologiaIncompletaInvalida() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(0);
        AtomicInteger r = new AtomicInteger();
        // limpa, limpa, INVALIDA (2 servidores), limpa, limpa, limpa => converge na 4a
        IntFunction<EstadoCluster> sonda = n -> n == 2 ? new EstadoCluster(2, true, true) : OK;
        ConvergenciaLoop l = laco(a, rel, r, sonda, List.of(() -> mapa(VALIDA), () -> mapa(VALIDA)), 600 * S);
        Resultado res = l.executar(0);
        assertTrue(res.convergiu());
        assertEquals(6, res.rodadas().size());
        assertFalse(res.rodadas().get(2).valida());
        assertNotNull(res.rodadas().get(2).motivoInvalida());
        assertEquals(3 * 5 * S, res.tConvergenciaAbsNs());
    }

    @Test
    @DisplayName("cache nao HEALTHY invalida a rodada")
    void cacheNaoHealthyInvalida() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(0);
        AtomicInteger r = new AtomicInteger();
        IntFunction<EstadoCluster> sonda = n -> n < 2 ? new EstadoCluster(3, true, false) : OK;
        Resultado res = laco(a, rel, r, sonda, List.of(() -> mapa(VALIDA), () -> mapa(VALIDA)), 600 * S).executar(0);
        assertFalse(res.rodadas().get(0).valida());
        assertFalse(res.rodadas().get(1).valida());
        assertEquals(2 * 5 * S, res.tConvergenciaAbsNs());
    }

    @Test
    @DisplayName("leitor em DEGRADED (excecao) torna a rodada invalida, nao 'convergiu'")
    void leitorDegradedInvalida() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(0);
        AtomicInteger r = new AtomicInteger();
        List<Supplier<Map<String, SessionState>>> leitores = List.of(
                () -> mapa(VALIDA),
                () -> mapa(VALIDA),
                () -> {
                    if (r.get() <= 3) throw new RuntimeException("ISPN000306 DEGRADED");
                    return mapa(VALIDA);
                });
        Resultado res = laco(a, rel, r, n -> OK, leitores, 600 * S).executar(0);
        assertTrue(res.convergiu());
        assertFalse(res.rodadas().get(0).valida());
        assertFalse(res.rodadas().get(2).valida());
        assertTrue(res.rodadas().get(3).valida());
        // rodadas 0..2 invalidas (r=1,2,3); validas a partir da 3 (r=4): convergencia na 3a valida
        assertEquals(3 * 5 * S, res.tConvergenciaAbsNs());
    }

    @Test
    @DisplayName("excecao da sonda de topologia invalida a rodada")
    void sondaQueLanca() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(0);
        AtomicInteger n = new AtomicInteger();
        ConvergenciaLoop l = new ConvergenciaLoop(a,
                List.of(() -> mapa(VALIDA), () -> mapa(VALIDA)), List.of(),
                () -> {
                    if (n.getAndIncrement() == 0) throw new java.io.IOException("REST fora do ar");
                    return OK;
                }, 3, rel, 5 * S, 600 * S, 3);
        Resultado res = l.executar(0);
        assertFalse(res.rodadas().get(0).valida());
        assertTrue(res.rodadas().get(0).motivoInvalida().contains("REST"));
        assertTrue(res.convergiu());
        assertEquals(5 * S, res.tConvergenciaAbsNs());
    }

    @Test
    @DisplayName("divergencia que nunca some em 600 s = divergencia-persistente, com violacoes registradas")
    void divergenciaPersistente() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(0);
        AtomicInteger r = new AtomicInteger();
        Resultado res = laco(a, rel, r, n -> OK,
                List.of(() -> mapa(VALIDA), () -> mapa(INVALIDA)), 600 * S).executar(0);
        assertFalse(res.convergiu());
        assertTrue(res.divergenciaPersistente());
        assertEquals(null, res.tConvergenciaAbsNs());
        assertTrue(res.rodadas().size() >= 120, "600 s / 5 s = 120 rodadas, houve " + res.rodadas().size());
        assertEquals(1, a.total());
        assertEquals("I5", a.drenar().get(0).invariante());
    }

    @Test
    @DisplayName("espera t_fim antes de comecar a contar rodadas")
    void esperaTFim() {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(100 * S);
        AtomicInteger r = new AtomicInteger();
        List<Long> instantes = new ArrayList<>();
        ConvergenciaLoop l = new ConvergenciaLoop(a,
                List.of(() -> mapa(VALIDA), () -> mapa(VALIDA)), List.of(),
                () -> { instantes.add(rel.agoraNs()); return OK; },
                3, rel, 5 * S, 600 * S, 3);
        Resultado res = l.executar(130 * S);
        assertEquals(130 * S, instantes.get(0));
        assertEquals(0L, res.tConvergenciaRelFimNs());
    }

    @Test
    @DisplayName("convergencia.csv e resumo JSON")
    void saidas(@TempDir Path dir) throws IOException {
        InvariantAuditor a = new InvariantAuditor();
        RelogioFalso rel = new RelogioFalso(0);
        AtomicInteger r = new AtomicInteger();
        IntFunction<EstadoCluster> sonda = n -> n == 0 ? new EstadoCluster(2, true, true) : OK;
        Resultado res = laco(a, rel, r, sonda, List.of(() -> mapa(VALIDA), () -> mapa(VALIDA)), 600 * S).executar(0);
        Path csv = dir.resolve("convergencia-rep-001.csv");
        ConvergenciaLoop.escreverCsv(csv, res);
        List<String> l = Files.readAllLines(csv);
        assertEquals("instante_ns,valida,chaves_divergentes_i5,chaves_divergentes_i6,motivo_invalida", l.get(0));
        assertEquals("0,false,,,topologia com 2 de 3 servidores", l.get(1));
        assertEquals("5000000000,true,0,0,", l.get(2));
        Path json = dir.resolve("convergencia-resumo-rep-001.json");
        ConvergenciaLoop.escreverResumo(json, res, 0L);
        String j = Files.readString(json);
        assertTrue(j.contains("\"convergiu\" : true"), j);
        assertTrue(j.contains("\"divergencia_persistente\" : false"), j);
        assertTrue(j.contains("\"t_convergencia_ns\" : 5000000000"), j);
        assertTrue(j.contains("\"rodadas\" : 4"), j);
    }
}
