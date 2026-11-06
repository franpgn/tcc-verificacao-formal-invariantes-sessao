package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-48 (smoke de 06/11, {@code runs/p48-smoke/}): no S3 o {@code rcm} principal so faz o reset e a carga inicial, e
 * depois de um no reiniciar (F1, religamento do AC-5) a topologia que ele conhece fica a da abertura; as leituras por
 * replica erravam 13 a 33 % das chaves de cada no. A topologia nova so chega na resposta de uma operacao (um
 * {@code get} bastou no smoke). Mitigacao da V-W6 secao 5.4: um {@code containsKey} de uma chave que nao existe em
 * cada cache lido, com prazo, antes da leitura.
 */
class AtualizacaoTopologiaTest {

    private static final List<String> CACHES = List.of("sessions-async", "counters-async");

    @Test
    @DisplayName("toca cada cache lido, na ordem, com a chave que nao existe no modelo")
    void tocaCadaCache() {
        List<String> toques = new ArrayList<>();
        AtualizacaoTopologia a = new AtualizacaoTopologia(CACHES,
                (cache, chave) -> { toques.add(cache + "/" + chave); return CompletableFuture.completedFuture(false); },
                cache -> 1, TimeUnit.SECONDS.toNanos(1), linha -> { });
        a.atualizar();
        assertEquals(List.of("sessions-async/" + AtualizacaoTopologia.CHAVE, "counters-async/" + AtualizacaoTopologia.CHAVE),
                toques);
    }

    @Test
    @DisplayName("prazo esgotado vira IllegalStateException com o cache e o P-48, sem esperar alem do prazo")
    void prazoEsgotado() {
        AtualizacaoTopologia a = new AtualizacaoTopologia(CACHES,
                (cache, chave) -> new CompletableFuture<>(), cache -> 1, TimeUnit.MILLISECONDS.toNanos(50), linha -> { });
        long t0 = System.nanoTime();
        IllegalStateException e = assertThrows(IllegalStateException.class, a::atualizar);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(e.getMessage().contains("sessions-async") && e.getMessage().contains("P-48"), e.getMessage());
        assertTrue(ms < 2000, "esperou " + ms + " ms");
    }

    @Test
    @DisplayName("falha da operacao vira IllegalStateException com a causa; os caches seguintes nao sao tocados")
    void falhaDoToque() {
        RuntimeException causa = new RuntimeException("servidor caiu");
        List<String> toques = new ArrayList<>();
        AtualizacaoTopologia a = new AtualizacaoTopologia(CACHES,
                (cache, chave) -> { toques.add(cache); return CompletableFuture.failedFuture(causa); },
                cache -> 1, TimeUnit.SECONDS.toNanos(1), linha -> { });
        IllegalStateException e = assertThrows(IllegalStateException.class, a::atualizar);
        assertSame(causa, e.getCause());
        assertTrue(e.getMessage().contains("sessions-async"), e.getMessage());
        assertEquals(List.of("sessions-async"), toques);
    }

    @Test
    @DisplayName("interrupcao: IllegalStateException e o sinal de interrupcao continua ligado")
    void interrupcao() {
        AtualizacaoTopologia a = new AtualizacaoTopologia(CACHES,
                (cache, chave) -> new CompletableFuture<>(), cache -> 1, TimeUnit.SECONDS.toNanos(5), linha -> { });
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, a::atualizar);
            assertTrue(Thread.currentThread().isInterrupted(), "interrupcao preservada");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("diagnostico de uma linha so quando o topology_id do cache muda")
    void diagnosticoSoQuandoMuda() {
        Map<String, Integer> ids = new HashMap<>(Map.of("sessions-async", 2135538279, "counters-async", 7));
        List<String> linhas = new ArrayList<>();
        AtualizacaoTopologia a = new AtualizacaoTopologia(CACHES,
                (cache, chave) -> {
                    if (cache.equals("sessions-async")) {
                        ids.put(cache, -1218010633);   // a resposta trouxe a topologia nova
                    }
                    return CompletableFuture.completedFuture(false);
                },
                ids::get, TimeUnit.SECONDS.toNanos(1), linhas::add);
        a.atualizar();
        assertEquals(List.of(AtualizacaoTopologia.linha("sessions-async", 2135538279, -1218010633)), linhas);
        a.atualizar();
        assertEquals(1, linhas.size(), "sem mudanca, sem linha");
    }

    @Test
    @DisplayName("formato da linha de diagnostico")
    void formatoDaLinha() {
        assertEquals("Topologia do principal: cache=sessions-async topology_id 2135538279 -> -1218010633 (P-48)",
                AtualizacaoTopologia.linha("sessions-async", 2135538279, -1218010633));
        assertEquals("Topologia do principal: cache=counters-async topology_id null -> 5 (P-48)",
                AtualizacaoTopologia.linha("counters-async", null, 5));
    }

    @Test
    @DisplayName("parametros: lista de caches nao vazia e prazo > 0")
    void parametros() {
        assertThrows(IllegalArgumentException.class, () -> new AtualizacaoTopologia(List.of(),
                (c, k) -> CompletableFuture.completedFuture(false), c -> 1, 1, l -> { }));
        assertThrows(IllegalArgumentException.class, () -> new AtualizacaoTopologia(CACHES,
                (c, k) -> CompletableFuture.completedFuture(false), c -> 1, 0, l -> { }));
    }

    @Test
    @DisplayName("a chave nao e do modelo (user-, lag:, slots) e o prazo e o da outra leitura da sonda (saude REST)")
    void chaveEPrazo() {
        String k = AtualizacaoTopologia.CHAVE;
        assertFalse(k.startsWith("user-") || k.startsWith("lag:"), k);
        assertTrue(k.contains("p48"), k);
        assertEquals(HealthRest.PRAZO_CONSULTA_NS, AtualizacaoTopologia.PRAZO_NS);
    }
}
