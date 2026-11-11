package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * B-33 (E1, item 3): saude do cluster pelo REST do Infinispan
 * ({@code GET /rest/v2/cache-managers/default/health}), usada para decidir se uma
 * rodada de convergencia e valida. Os nomes de campo ({@code cluster_health.number_of_nodes},
 * {@code cache_health[].cache_name/status}) sao os documentados; serao confirmados contra
 * o servidor real no gate G-3 (T-B).
 *
 * <p><b>W-2b (decisao 3 do coordenador de 05/11, P-45).</b> Ate o W-2b a consulta criava um processo
 * {@code curl --digest} por rodada do laco de convergencia; agora usa o {@link ClienteAdminRest} em memoria (Digest
 * com o desafio guardado), sem processo e sem disco. O prazo e o mesmo {@code --max-time 5} do curl
 * ({@link #PRAZO_CONSULTA_NS}). O que o laco ve nao muda: qualquer falha (rede, codigo fora de 2xx, prazo) e
 * {@link IOException}, que o {@code ConvergenciaLoop} grava como rodada invalida; corpo invalido e
 * {@link IllegalArgumentException}.</p>
 */
public final class HealthRest {

    /** Prazo de cada consulta: o {@code --max-time 5} do curl que a consulta usava ate o W-2b (B-33). */
    public static final long PRAZO_CONSULTA_NS = TimeUnit.SECONDS.toNanos(5);

    static final String ENDPOINT = "/rest/v2/cache-managers/default/health";

    /** Estado lido: numero de nos do cluster e status por cache. */
    public record Estado(int numeroDeNos, Map<String, String> statusPorCache) {
        public boolean healthy(String cache) {
            return "HEALTHY".equals(statusPorCache.get(cache));
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private HealthRest() { /* utilitario */ }

    /** Interpreta o JSON; lanca {@link IllegalArgumentException} se nao tiver o formato esperado. */
    public static Estado interpretar(String json) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("resposta de saude nao e JSON: " + e.getMessage(), e);
        }
        JsonNode cluster = raiz == null ? null : raiz.get("cluster_health");
        if (cluster == null || !cluster.has("number_of_nodes")) {
            throw new IllegalArgumentException("resposta de saude sem cluster_health.number_of_nodes");
        }
        Map<String, String> porCache = new HashMap<>();
        JsonNode caches = raiz.get("cache_health");
        if (caches != null && caches.isArray()) {
            for (JsonNode c : caches) {
                if (c.has("cache_name") && c.has("status")) {
                    porCache.put(c.get("cache_name").asText(), c.get("status").asText());
                }
            }
        }
        return new Estado(cluster.get("number_of_nodes").asInt(), porCache);
    }

    /**
     * Consulta a saude em {@code baseUrl} ({@code http://h:p}) pelo {@code cliente}, com o pedido inteiro limitado a
     * {@code prazoNs}.
     *
     * @throws IOException              falha de rede, codigo fora de 2xx ou prazo estourado
     * @throws InterruptedException     a thread foi interrompida (o pedido e cancelado)
     * @throws IllegalArgumentException corpo que nao e JSON de saude
     */
    public static Estado consultar(ClienteAdminRest cliente, String baseUrl, long prazoNs)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + ENDPOINT);
        ClienteAdminRest.Resposta r;
        try {
            r = cliente.pedir("GET", uri, prazoNs);
        } catch (TimeoutException e) {
            throw new IOException("GET " + uri + " excedeu o prazo de " + TimeUnit.NANOSECONDS.toMillis(prazoNs) + " ms",
                    e);
        }
        if (!r.sucesso()) {
            throw new IOException("GET " + uri + " devolveu HTTP " + r.codigo());
        }
        return interpretar(r.corpo());
    }
}
