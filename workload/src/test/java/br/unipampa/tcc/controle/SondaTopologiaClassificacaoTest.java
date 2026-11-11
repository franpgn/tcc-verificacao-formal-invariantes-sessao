package br.unipampa.tcc.controle;

import br.unipampa.tcc.session.HealthRest;
import br.unipampa.tcc.session.PerfilReplicacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T1 parte pura, T2..T5): classificacao de uma leitura de saude por
 * {@link SondaTopologia#classificar}. T2 le as capturas REAIS de {@code captura-real/} direto do
 * classpath (sem copia); T3 usa as capturas SINTETICAS de {@code captura-sintetica/}, derivadas de
 * {@code captura-real/rep3-isn3-healthy-3nos.json} (procedencia no relatorio do B-26.2).
 */
class SondaTopologiaClassificacaoTest {

    private static final List<String> SYNC = PerfilReplicacao.SYNC.caches();
    private static final List<String> ASYNC = PerfilReplicacao.ASYNC.caches();

    static LeituraSaude captura(String recurso) {
        try (InputStream in = SondaTopologiaClassificacaoTest.class.getResourceAsStream(recurso)) {
            assertNotNull(in, "recurso ausente: " + recurso);
            HealthRest.Estado e = HealthRest.interpretar(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return new LeituraSaude(e.numeroDeNos(), e.statusPorCache());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResultadoNo sync(LeituraSaude l) {
        return SondaTopologia.classificar("isn1", l, null, SYNC, 3);
    }

    private static ResultadoNo async(LeituraSaude l) {
        return SondaTopologia.classificar("isn1", l, null, ASYNC, 3);
    }

    private static Map<String, String> status(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }

    // ---------------------------------------------------------------- T1 (parte pura)

    @Test
    @DisplayName("T1: no que nao respondeu e anomalo, com o motivo da falha e numeroDeNos = -1")
    void falhaDeConsulta() {
        assertEquals(new ResultadoNo("isn2", false, -1, "falha:IOException", true),
                SondaTopologia.classificar("isn2", null, "falha:IOException", SYNC, 3));
        assertEquals(new ResultadoNo("isn3", false, -1, "timeout", true),
                SondaTopologia.classificar("isn3", null, "timeout", SYNC, 3));
    }

    // ---------------------------------------------------------------- T2 capturas reais (perfil sync)

    @Test
    @DisplayName("T2: captura real com 3 nos e caches HEALTHY nao e anomala")
    void realSaudavel() {
        assertEquals(new ResultadoNo("isn1", true, 3, "ok", false),
                sync(captura("/captura-real/rep3-isn3-healthy-3nos.json")));
    }

    @Test
    @DisplayName("T2: captura real com 2 nos e anomala por nos:2<3")
    void realDoisNos() {
        assertEquals(new ResultadoNo("isn1", true, 2, "nos:2<3", true),
                sync(captura("/captura-real/rep3-isn1-healthy-2nos.json")));
    }

    @Test
    @DisplayName("T2: captura real DEGRADED com 1 no: o primeiro motivo e nos:1<3")
    void realDegradadoUmNo() {
        assertEquals(new ResultadoNo("isn1", true, 1, "nos:1<3", true),
                sync(captura("/captura-real/rep3-isn3-degraded-1no.json")));
    }

    @Test
    @DisplayName("T2: captura real HEALTHY_REBALANCING com 3 nos e anomala pelo cache de sessoes")
    void realRebalanceando() {
        assertEquals(new ResultadoNo("isn1", true, 3, "cache:sessions=HEALTHY_REBALANCING", true),
                sync(captura("/captura-real/rep2-isn1-healthy-rebalancing-3nos.json")));
    }

    // ---------------------------------------------------------------- T3 perfil async (sinteticas)

    @Test
    @DisplayName("T3: perfil async com os quatro caches HEALTHY nao e anomalo")
    void asyncSaudavel() {
        assertEquals(new ResultadoNo("isn1", true, 3, "ok", false),
                async(captura("/captura-sintetica/sint-async-healthy-3nos.json")));
    }

    @Test
    @DisplayName("T3: so o cache sync de sessoes DEGRADED e perfil async: nao anomalo (cache fora da lista)")
    void asyncIgnoraIrmaoSync() {
        LeituraSaude l = captura("/captura-sintetica/sint-async-sessions-degraded-3nos.json");
        assertEquals("DEGRADED", l.statusPorCache().get(SYNC.get(0)));
        assertEquals(new ResultadoNo("isn1", true, 3, "ok", false), async(l));
        // o mesmo arquivo no perfil sync e anomalo: a captura nao e neutra por acidente
        assertEquals("cache:sessions=DEGRADED", sync(l).motivo());
    }

    @Test
    @DisplayName("T3: counters-async HEALTHY_REBALANCING e perfil async: anomalo")
    void asyncRebalanceando() {
        assertEquals(new ResultadoNo("isn1", true, 3, "cache:counters-async=HEALTHY_REBALANCING", true),
                async(captura("/captura-sintetica/sint-async-counters-async-rebalancing-3nos.json")));
    }

    @Test
    @DisplayName("T3: counters-async ausente da resposta: anomalo por cache-ausente")
    void asyncCacheAusente() {
        assertEquals(new ResultadoNo("isn1", true, 3, "cache-ausente:counters-async", true),
                async(captura("/captura-sintetica/sint-async-sem-counters-async-3nos.json")));
    }

    @Test
    @DisplayName("T3: ___protobuf_metadata HEALTHY_REBALANCING e o resto HEALTHY: nao anomalo")
    void asyncCacheInternoIgnorado() {
        LeituraSaude l = captura("/captura-sintetica/sint-async-protobuf-rebalancing-3nos.json");
        assertEquals("HEALTHY_REBALANCING", l.statusPorCache().get("___protobuf_metadata"));
        assertEquals(new ResultadoNo("isn1", true, 3, "ok", false), async(l));
        assertEquals(new ResultadoNo("isn1", true, 3, "ok", false), sync(l));
    }

    // ---------------------------------------------------------------- T4 fronteira de nos

    @Test
    @DisplayName("T4: numeroDeNos = esperados nao e anomalo; esperados - 1 e")
    void fronteiraDeNos() {
        Map<String, String> ok = status("sessions", "HEALTHY", "counters", "HEALTHY");
        assertEquals(new ResultadoNo("a", true, 3, "ok", false),
                SondaTopologia.classificar("a", new LeituraSaude(3, ok), null, SYNC, 3));
        assertEquals(new ResultadoNo("a", true, 2, "nos:2<3", true),
                SondaTopologia.classificar("a", new LeituraSaude(2, ok), null, SYNC, 3));
        assertEquals(new ResultadoNo("a", true, 5, "ok", false),
                SondaTopologia.classificar("a", new LeituraSaude(5, ok), null, SYNC, 5));
        assertEquals("nos:4<5", SondaTopologia.classificar("a", new LeituraSaude(4, ok), null, SYNC, 5).motivo());
        assertFalse(SondaTopologia.classificar("a", new LeituraSaude(4, ok), null, SYNC, 3).anomalo(),
                "mais nos que o esperado nao e anomalia de topologia");
    }

    // ---------------------------------------------------------------- T5 ordem dos motivos

    @Test
    @DisplayName("T5: nos < esperados vence cache DEGRADED")
    void nosAntesDeCache() {
        assertEquals("nos:2<3", sync(new LeituraSaude(2, status("sessions", "DEGRADED", "counters", "HEALTHY")))
                .motivo());
    }

    @Test
    @DisplayName("T5: dois caches problematicos: vale o primeiro da lista do perfil")
    void primeiroCacheDaLista() {
        LeituraSaude l = new LeituraSaude(3, status("counters", "HEALTHY_REBALANCING", "sessions", "DEGRADED"));
        assertEquals("cache:sessions=DEGRADED", sync(l).motivo());
        assertEquals("cache:counters=HEALTHY_REBALANCING",
                SondaTopologia.classificar("x", l, null, List.of("counters", "sessions"), 3).motivo());
    }

    @Test
    @DisplayName("T5: cache ausente vence cache com status ruim, mesmo que venha depois na lista")
    void ausenteAntesDeStatus() {
        assertEquals("cache-ausente:counters", sync(new LeituraSaude(3, status("sessions", "DEGRADED"))).motivo());
    }

    @Test
    @DisplayName("status diferente de exatamente HEALTHY e anomalo (minusculas, sufixos, vazio)")
    void soHealthyExato() {
        for (String s : List.of("healthy", "HEALTHY_REBALANCING", "DEGRADED", "FAILED", "", "HEALTHY ")) {
            ResultadoNo r = sync(new LeituraSaude(3, status("sessions", s, "counters", "HEALTHY")));
            assertTrue(r.anomalo(), "status '" + s + "'");
            assertEquals("cache:sessions=" + s, r.motivo());
        }
    }

    // ---------------------------------------------------------------- tipos de dados

    @Test
    @DisplayName("LeituraSaude: copia imutavel e ordenada; numeroDeNos negativo rejeitado")
    void leituraSaude() {
        Map<String, String> m = new LinkedHashMap<>(status("z", "HEALTHY", "a", "DEGRADED"));
        LeituraSaude l = new LeituraSaude(3, m);
        m.put("b", "HEALTHY");
        assertEquals(List.of("a", "z"), List.copyOf(l.statusPorCache().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> l.statusPorCache().put("c", "HEALTHY"));
        assertThrows(IllegalArgumentException.class, () -> new LeituraSaude(-1, Map.of()));
    }

    @Test
    @DisplayName("ResultadoNo: motivo nao vazio e sem virgula; numeroDeNos = -1 sse nao respondeu")
    void resultadoNo() {
        assertThrows(IllegalArgumentException.class, () -> new ResultadoNo("a", true, 3, "x,y", true));
        assertThrows(IllegalArgumentException.class, () -> new ResultadoNo("a", true, 3, "", true));
        assertThrows(IllegalArgumentException.class, () -> new ResultadoNo("a", false, 3, "timeout", true));
        assertThrows(IllegalArgumentException.class, () -> new ResultadoNo("a", true, -1, "ok", false));
    }

    @Test
    @DisplayName("AmostraTopologia: lista de nos imutavel; nosMinimo negativo rejeitado")
    void amostra() {
        List<ResultadoNo> l = new ArrayList<>(List.of(new ResultadoNo("a", true, 3, "ok", false)));
        AmostraTopologia a = new AmostraTopologia(1L, false, 3, l);
        l.clear();
        assertEquals(1, a.porNo().size());
        assertThrows(UnsupportedOperationException.class, () -> a.porNo().clear());
        assertThrows(IllegalArgumentException.class, () -> new AmostraTopologia(1L, false, -1, List.of()));
    }
}
