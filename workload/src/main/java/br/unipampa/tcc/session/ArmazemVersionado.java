package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCache;

import java.util.Map;
import java.util.Set;

/**
 * B-43.8 (DT-003): {@link Armazem} com leitura versionada e escrita condicional por versao, usado por
 * {@link SessionOps} no cache de sessoes para que {@code logout} e a cascata de {@code block} nao
 * percam uma invalidacao concorrente (a primeira invalidacao vence e o motivo e imutavel do lado da
 * aplicacao). O {@link Armazem} nao muda: os testes antigos continuam usando o caminho nao versionado.
 *
 * <p>Em producao o adaptador {@link #de(RemoteCache)} usa {@code getWithMetadata} e
 * {@code replaceWithVersion} do Hot Rod.</p>
 */
interface ArmazemVersionado<V> extends Armazem<V> {

    /** Valor lido com a versao do servidor que o acompanhava. */
    record Versionado<V>(V valor, long versao) { }

    /** Le o valor e a versao; {@code null} se a chave nao existe. */
    Versionado<V> lerComVersao(String chave);

    /**
     * Grava {@code novo} so se a versao corrente da chave ainda for {@code versao}.
     *
     * @return {@code true} se a escrita foi aplicada; {@code false} se a versao mudou (ou a chave sumiu)
     */
    boolean substituirSeVersao(String chave, V novo, long versao);

    /** Adaptador de producao: delega 1:1 ao {@link RemoteCache}. */
    static <V> ArmazemVersionado<V> de(RemoteCache<String, V> cache) {
        return new ArmazemVersionado<>() {
            @Override public V get(String chave) { return cache.get(chave); }
            @Override public V put(String chave, V valor) { return cache.put(chave, valor); }
            @Override public Set<Map.Entry<String, V>> entrySet() { return cache.entrySet(); }

            @Override public Versionado<V> lerComVersao(String chave) {
                MetadataValue<V> m = cache.getWithMetadata(chave);
                return m == null ? null : new Versionado<>(m.getValue(), m.getVersion());
            }

            @Override public boolean substituirSeVersao(String chave, V novo, long versao) {
                return cache.replaceWithVersion(chave, novo, versao);
            }
        };
    }
}
