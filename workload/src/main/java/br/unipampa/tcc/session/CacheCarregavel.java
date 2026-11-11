package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCache;

import java.util.Map;

/**
 * B-43.2: {@link CacheLimpavel} com carga em lote ({@code putAll}), usado pela {@link CargaInicial}.
 * Mesma costura de teste de {@link CacheLimpavel}/{@link Armazem}, que nao mudam.
 */
interface CacheCarregavel extends CacheLimpavel {

    /** Grava todas as entradas (no Hot Rod, {@code RemoteCache.putAll}). */
    void putAll(Map<String, SessionState> lote);

    /** Adaptador de producao: delega 1:1 ao {@link RemoteCache}. */
    static CacheCarregavel de(RemoteCache<String, SessionState> cache) {
        return new CacheCarregavel() {
            @Override public String nome() { return cache.getName(); }
            @Override public void clear() { cache.clear(); }
            @Override public int size() { return cache.size(); }
            @Override public void putAll(Map<String, SessionState> lote) { cache.putAll(lote); }
        };
    }
}
