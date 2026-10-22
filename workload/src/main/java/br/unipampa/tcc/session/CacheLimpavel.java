package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCache;

/**
 * B-43.1: fatia minima de um <i>cache</i> que o {@link ResetEstado} precisa (nome, {@code clear} e
 * {@code size}). Como {@link Armazem}, existe como costura de teste: o {@link RemoteCache} completo
 * nao pode ser dublado por {@code Proxy} (classes de consulta fora do classpath). {@link Armazem}
 * nao foi alterado.
 */
interface CacheLimpavel {

    /** Nome do cache (para mensagens, stdout e {@code estado-rep-NNN.json}). */
    String nome();

    /** Remove todas as entradas (no Hot Rod, {@code RemoteCache.clear()}). */
    void clear();

    /** Numero de entradas no cluster (no Hot Rod, {@code RemoteCache.size()}). */
    int size();

    /** Adaptador de producao: delega 1:1 ao {@link RemoteCache}. */
    static CacheLimpavel de(RemoteCache<?, ?> cache) {
        return new CacheLimpavel() {
            @Override public String nome() { return cache.getName(); }
            @Override public void clear() { cache.clear(); }
            @Override public int size() { return cache.size(); }
        };
    }
}
