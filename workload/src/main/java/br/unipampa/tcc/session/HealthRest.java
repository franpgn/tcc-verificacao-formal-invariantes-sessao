package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * B-33 (E1, item 3): saude do cluster pelo REST do Infinispan
 * ({@code GET /rest/v2/cache-managers/default/health}), usada para decidir se uma
 * rodada de convergencia e valida. Os nomes de campo ({@code cluster_health.number_of_nodes},
 * {@code cache_health[].cache_name/status}) sao os documentados; serao confirmados contra
 * o servidor real no gate G-3 (T-B).
 *
 * <p>A consulta usa o {@code curl --digest} (mesmo mecanismo de {@code run-baseline.sh}) com as
 * credenciais passadas por configuracao em stdin ({@code -K -}), de modo que a senha nao aparece
 * na linha de comando nem em listagem de processos.</p>
 */
public final class HealthRest {

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

    /** Configuracao do curl (lida de stdin): usuario e senha com aspas e barras escapadas. */
    static String configCurl(String usuario, String senha) {
        String cred = usuario + ":" + senha;
        return "user = \"" + cred.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n";
    }

    /** Consulta o REST via curl; lanca {@link IOException} se o curl falhar ou nao responder. */
    public static Estado consultar(String baseUrl, String usuario, String senha)
            throws IOException, InterruptedException {
        Process p = new ProcessBuilder("curl", "-s", "--fail", "--digest", "--max-time", "5",
                "-K", "-", baseUrl + "/rest/v2/cache-managers/default/health")
                .redirectErrorStream(false).start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(configCurl(usuario, senha).getBytes(StandardCharsets.UTF_8));
        }
        String corpo = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("curl excedeu o tempo");
        }
        if (p.exitValue() != 0) {
            throw new IOException("curl falhou (exit " + p.exitValue() + ")");
        }
        return interpretar(corpo);
    }
}
