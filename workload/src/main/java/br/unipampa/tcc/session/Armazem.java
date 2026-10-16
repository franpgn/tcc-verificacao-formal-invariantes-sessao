package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCache;

import java.util.Map;
import java.util.Set;

/**
 * B-33: fatia minima do {@link RemoteCache} usada por {@link SessionOps}
 * ({@code get}, {@code put}, {@code entrySet}). Existe apenas como costura de
 * teste: a interface completa do RemoteCache referencia classes de consulta
 * (infinispan-query-dsl) que nao estao no classpath, o que impede dubles por
 * {@code java.lang.reflect.Proxy}. Nao altera nenhuma regra de dominio.
 */
interface Armazem<V> {

    V get(String chave);

    V put(String chave, V valor);

    Set<Map.Entry<String, V>> entrySet();

    /** Adaptador de producao: delega 1:1 ao {@link RemoteCache}. */
    static <V> Armazem<V> de(RemoteCache<String, V> cache) {
        return new Armazem<>() {
            @Override public V get(String chave) { return cache.get(chave); }
            @Override public V put(String chave, V valor) { return cache.put(chave, valor); }
            @Override public Set<Map.Entry<String, V>> entrySet() { return cache.entrySet(); }
        };
    }
}
