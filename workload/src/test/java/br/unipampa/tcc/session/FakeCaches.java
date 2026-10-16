package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Duble de {@link Armazem} em memoria para exercitar {@link SessionOps} sem
 * cluster. Permite injetar falha em get/put/entrySet para simular particao.
 */
final class FakeCaches {

    private FakeCaches() { }

    /** Armazem de mapa em memoria com gancho de falha opcional. */
    static final class Cache<V> implements Armazem<V> {
        final Map<String, V> dados = new LinkedHashMap<>();
        /** Chamado antes de cada get/put/entrySet; pode lancar. */
        BiConsumer<String, String> antes = (op, chave) -> { };
        int puts;

        Armazem<V> remoto() {
            return this;
        }

        @Override
        public V get(String chave) {
            antes.accept("get", chave);
            return dados.get(chave);
        }

        @Override
        public V put(String chave, V valor) {
            antes.accept("put", chave);
            puts++;
            return dados.put(chave, valor);
        }

        @Override
        public Set<Map.Entry<String, V>> entrySet() {
            antes.accept("entrySet", "");
            return new LinkedHashSet<>(new ArrayList<>(dados.entrySet()));
        }
    }
}
