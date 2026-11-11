package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-42.1: nomes de cache resolvidos pelo perfil de replicacao. */
class PerfilReplicacaoTest {

    @Test
    @DisplayName("SYNC devolve os nomes historicos (baseline inalterado)")
    void syncUsaNomesHistoricos() {
        assertEquals("sessions", PerfilReplicacao.SYNC.cacheSessoes());
        assertEquals("counters", PerfilReplicacao.SYNC.cacheContadores());
        assertEquals(List.of("sessions", "counters"), PerfilReplicacao.SYNC.caches());
    }

    @Test
    @DisplayName("ASYNC devolve os caches irmaos com sufixo -async (sessions e counters)")
    void asyncUsaIrmaos() {
        assertEquals("sessions-async", PerfilReplicacao.ASYNC.cacheSessoes());
        assertEquals("counters-async", PerfilReplicacao.ASYNC.cacheContadores());
        assertEquals(List.of("sessions-async", "counters-async"), PerfilReplicacao.ASYNC.caches());
    }

    @Test
    @DisplayName("rotulo e conversao da CLI: sync/async aceitos, resto rejeitado")
    void conversao() {
        assertEquals("sync", PerfilReplicacao.SYNC.rotulo());
        assertEquals("async", PerfilReplicacao.ASYNC.rotulo());
        assertEquals(PerfilReplicacao.SYNC, PerfilReplicacao.porRotulo("sync"));
        assertEquals(PerfilReplicacao.ASYNC, PerfilReplicacao.porRotulo("async"));
        assertEquals(PerfilReplicacao.ASYNC, PerfilReplicacao.porRotulo("ASYNC"));
        for (String ruim : new String[]{"", " ", "asinc", "SYNC_ASYNC", "repl", "dist-async"}) {
            assertThrows(IllegalArgumentException.class, () -> PerfilReplicacao.porRotulo(ruim), ruim);
        }
        assertThrows(IllegalArgumentException.class, () -> PerfilReplicacao.porRotulo(null));
    }

    @Test
    @DisplayName("descricao mostra perfil e nomes")
    void descricao() {
        assertEquals("replicacao=sync caches=sessions,counters", PerfilReplicacao.SYNC.descricao());
        assertEquals("replicacao=async caches=sessions-async,counters-async",
                PerfilReplicacao.ASYNC.descricao());
    }

    @Test
    @DisplayName("classificacao do papel do cache pelo nome, nos dois perfis")
    void papelDoCache() {
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            assertTrue(PerfilReplicacao.ehCacheDeSessoes(p.cacheSessoes()));
            assertFalse(PerfilReplicacao.ehCacheDeSessoes(p.cacheContadores()));
            assertTrue(PerfilReplicacao.ehCacheDeContadores(p.cacheContadores()));
            assertFalse(PerfilReplicacao.ehCacheDeContadores(p.cacheSessoes()));
        }
        assertFalse(PerfilReplicacao.ehCacheDeSessoes("outro"));
        assertFalse(PerfilReplicacao.ehCacheDeContadores(null));
    }
}
