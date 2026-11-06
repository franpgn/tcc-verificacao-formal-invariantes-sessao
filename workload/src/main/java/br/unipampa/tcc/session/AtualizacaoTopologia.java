package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCacheManager;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * P-48 (smoke de 06/11, {@code runs/p48-smoke/}): no S3 o {@code rcm} principal (topology-aware) so faz o reset e a
 * carga inicial; a carga vai pelos clientes BASIC do {@link S3Clientes}. Depois de um no reiniciar (F1, ou o
 * religamento do AC-5 no braco B), o no volta com endereco JGroups novo e a distribuicao dos segmentos muda, mas o
 * principal continua com a topologia da abertura, porque a nova so chega na resposta de uma operacao. Como
 * {@code WorkloadMain.segmentosDoServidor} escolhe por essa topologia os segmentos lidos de cada replica, as leituras
 * por replica do laco de convergencia erravam 13 a 33 % das chaves de cada no.
 *
 * <p>Mitigacao da V-W6 (secao 5.4): antes da leitura, um {@code containsKey} de {@link #CHAVE} (que nao existe no
 * modelo) em cada cache lido, pelo principal, com prazo {@link #PRAZO_NS}. A resposta traz a topologia nova, que o
 * cliente aplica antes de completar a operacao. Falha ou prazo esgotado viram {@link IllegalStateException}: no laco
 * de convergencia ela sai pela sonda e a rodada fica invalida, como uma sonda que falha. Quando o
 * {@code topology_id} de um cache muda, sai uma linha de diagnostico ({@link #linha}).</p>
 */
public final class AtualizacaoTopologia {

    /** Chave tocada: fora do modelo ({@code user-*}, {@code lag:*}, slots, ids de sessao); o containsKey nao a cria. */
    public static final String CHAVE = "__p48-topologia__";

    /** Prazo de cada toque: o mesmo da outra leitura da sonda do laco, a saude pela REST ({@link HealthRest}). */
    public static final long PRAZO_NS = HealthRest.PRAZO_CONSULTA_NS;

    /** A operacao que traz a topologia nova (no principal: {@code containsKeyAsync}). */
    @FunctionalInterface
    public interface Toque {
        CompletableFuture<?> tocar(String cache, String chave);
    }

    /** O {@code topology_id} que o cliente conhece para o cache (so para o diagnostico). */
    @FunctionalInterface
    public interface LeituraId {
        Integer topologia(String cache);
    }

    private final List<String> caches;
    private final Toque toque;
    private final LeituraId leituraId;
    private final long prazoNs;
    private final Consumer<String> saida;

    public AtualizacaoTopologia(List<String> caches, Toque toque, LeituraId leituraId, long prazoNs,
                                Consumer<String> saida) {
        if (caches == null || caches.isEmpty()) {
            throw new IllegalArgumentException("lista de caches vazia");
        }
        if (prazoNs <= 0) {
            throw new IllegalArgumentException("prazo da atualizacao da topologia deve ser > 0: " + prazoNs);
        }
        this.caches = List.copyOf(caches);
        this.toque = Objects.requireNonNull(toque, "toque");
        this.leituraId = Objects.requireNonNull(leituraId, "leituraId");
        this.prazoNs = prazoNs;
        this.saida = Objects.requireNonNull(saida, "saida");
    }

    /**
     * No {@code rcm} principal: {@code containsKeyAsync(CHAVE)} em cada cache, prazo {@link #PRAZO_NS}. Os nomes vem do
     * perfil de replicacao pelo chamador ({@code cli.replicacao.cacheSessoes()}, {@code cacheContadores()}); o
     * parametro {@code nomeCache} e o do adaptador, como na sonda ({@code NomesDeCacheSoNoResolvedorTest}).
     */
    public static AtualizacaoTopologia doPrincipal(RemoteCacheManager rcm, List<String> caches, Consumer<String> saida) {
        Objects.requireNonNull(rcm, "rcm");
        return new AtualizacaoTopologia(caches,
                (nomeCache, chave) -> rcm.getCache(nomeCache).containsKeyAsync(chave),
                nomeCache -> rcm.getCache(nomeCache).getCacheTopologyInfo().getTopologyId(),
                PRAZO_NS, saida);
    }

    /**
     * Toca cada cache, na ordem; para no primeiro que falha.
     *
     * @throws IllegalStateException se um toque falhar, esgotar o prazo ou for interrompido (com a interrupcao
     *                               preservada)
     */
    public void atualizar() {
        for (String cache : caches) {
            Integer antes = idOuNulo(cache);
            try {
                toque.tocar(cache, CHAVE).get(prazoNs, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                throw new IllegalStateException("topologia do principal nao atualizada em "
                        + TimeUnit.NANOSECONDS.toMillis(prazoNs) + " ms no cache " + cache + " (P-48)", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("topologia do principal nao atualizada no cache " + cache
                        + " (P-48): " + e.getCause(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("atualizacao da topologia interrompida no cache " + cache + " (P-48)", e);
            }
            Integer depois = idOuNulo(cache);
            if (!Objects.equals(antes, depois)) {
                saida.accept(linha(cache, antes, depois));
            }
        }
    }

    private Integer idOuNulo(String cache) {
        try {
            return leituraId.topologia(cache);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code Topologia do principal: cache=<c> topology_id <antes> -> <depois> (P-48)}. */
    public static String linha(String cache, Integer antes, Integer depois) {
        return "Topologia do principal: cache=" + cache + " topology_id " + antes + " -> " + depois + " (P-48)";
    }
}
