package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (B-46), requisito 1 (P-30 do B-44.1b; {@code docs/acoes-controlador-cluster-2026-11-05.md} secao 6, itens 1 a
 * 4): no cluster da avaliacao o XML nao declara caches, e os seis ({@code sessions*}, {@code counters*},
 * {@code canario*}) sao criados pela REST ({@code scripts/cria-caches.sh}) a partir de
 * {@code cluster/caches/<nome>.json}. Este teste estende o {@link CachesCanarioJsonTest} aos quatro da carga:
 *
 * <ul>
 *   <li>um arquivo por cache da carga, sem o envoltorio do nome, numa linha compacta (a leitura de volta
 *       {@code ?action=config} do {@code cria-caches.sh} e byte a byte);</li>
 *   <li>cada um igual ao cache de mesmo nome no XML versionado (owners, segments, statistics, locking,
 *       state-transfer, partition-handling), salvo {@code remote-timeout}: 17500 explicito nos SYNC (o valor
 *       efetivo do XML, B-44.1 secao 3; criado sem ele, seria 15000) e ausente nos ASYNC;</li>
 *   <li>o diretorio tem exatamente os seis JSON que o orquestrador cria (nenhum a mais, nenhum a menos).</li>
 * </ul>
 *
 * <p>A1 (2026-11-06): o {@code sessions-async} usa a politica de fusao propria
 * ({@link #POLITICA_A1}, JAR {@code extensao-servidor} em {@code server/lib}); e a unica diferenca em relacao ao XML e
 * ao {@code canario-async}. O {@code counters-async} fica com {@code PREFERRED_NON_NULL}: {@code desbloquear} e
 * {@code resetar} quebram o semirreticulado do {@code IdentityState}. O XML com caches nao muda (ele nao monta o
 * JAR).</p>
 */
class CachesCargaJsonTest {

    /** A1: classe da politica de fusao do {@code sessions-async} (modulo {@code extensao-servidor}). */
    static final String POLITICA_A1 = "br.unipampa.tcc.servidor.PoliticaFusaoSessoes";
    static final String PNN = "PREFERRED_NON_NULL";
    static final String MP = "merge-policy";
    static final String PH = "partition-handling";

    /** O {@code merge-policy} do mapa de um cache (para comparar o resto). */
    @SuppressWarnings("unchecked")
    static Object tirarMergePolicy(Map<String, Object> cache) {
        return ((Map<String, Object>) cache.get(PH)).remove(MP);
    }

    static Path dirCaches() {
        return ClusterXmlIrmaosTest.localizarXml().getParent().resolve("caches");
    }

    /** Os seis caches, na ordem em que o orquestrador os cria (carga SYNC, carga ASYNC, canarios). */
    static List<String> seis() {
        return List.of(PerfilReplicacao.SYNC.cacheSessoes(), PerfilReplicacao.SYNC.cacheContadores(),
                PerfilReplicacao.ASYNC.cacheSessoes(), PerfilReplicacao.ASYNC.cacheContadores(),
                PerfilReplicacao.SYNC.cacheCanario(), PerfilReplicacao.ASYNC.cacheCanario());
    }

    @Test
    @DisplayName("um JSON por cache da carga, sem envoltorio do nome, numa linha compacta, com o modo do perfil")
    void umArquivoPorCache() throws IOException {
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            for (String nome : p.caches()) {
                Path f = CachesCanarioJsonTest.arquivo(nome);
                assertTrue(Files.isRegularFile(f), "ausente: " + f);
                String bruto = Files.readString(f, StandardCharsets.UTF_8);
                JsonNode raiz = CachesCanarioJsonTest.JSON.readTree(bruto);
                assertEquals(List.of("distributed-cache"),
                        List.copyOf(CachesCanarioJsonTest.mapaJson(raiz).keySet()), "so distributed-cache: " + nome);
                assertEquals(CachesCanarioJsonTest.JSON.writeValueAsString(raiz), bruto.strip(), "linha compacta: " + nome);
                assertEquals(p.name(), raiz.get("distributed-cache").get("mode").asText(), "mode de " + nome);
                assertEquals(1, bruto.strip().split("\n").length, "uma linha: " + nome);
            }
        }
    }

    @Test
    @DisplayName("cada JSON igual ao cache de mesmo nome no XML, salvo remote-timeout (17500 so nos SYNC)")
    void iguaisAoXml() throws Exception {
        Map<String, Element> xml = ClusterXmlIrmaosTest.lerCaches(ClusterXmlIrmaosTest.localizarXml());
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            for (String nome : p.caches()) {
                Map<String, Object> json = CachesCanarioJsonTest.mapaJson(
                        CachesCanarioJsonTest.ler(nome).get("distributed-cache"));
                Map<String, Object> doXml = CachesCanarioJsonTest.mapaXml(xml.get(nome));
                Object rt = json.remove(CachesCanarioJsonTest.RT);
                Object mpJson = tirarMergePolicy(json);
                Object mpXml = tirarMergePolicy(doXml);
                assertEquals(PNN, mpXml, "o XML continua com PREFERRED_NON_NULL: " + nome);
                assertEquals(nome.equals(PerfilReplicacao.ASYNC.cacheSessoes()) ? POLITICA_A1 : PNN, mpJson,
                        "merge-policy de " + nome + " (A1 so no sessions-async)");
                assertEquals(doXml, json, nome + " (JSON x XML)");
                assertFalse(doXml.containsKey(CachesCanarioJsonTest.RT), "o XML nao declara remote-timeout");
                if (p == PerfilReplicacao.SYNC) {
                    assertEquals(CachesCanarioJsonTest.RT_SYNC, rt, "remote-timeout efetivo do XML no SYNC: " + nome);
                } else {
                    assertEquals(null, rt, "ASYNC sem remote-timeout: " + nome);
                }
            }
        }
    }

    @Test
    @DisplayName("canario igual a sessions do mesmo perfil, byte a byte, salvo a politica A1 do sessions-async")
    void canarioIgualASessoes() throws IOException {
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            String sessoes = Files.readString(CachesCanarioJsonTest.arquivo(p.cacheSessoes()), StandardCharsets.UTF_8).strip();
            if (p == PerfilReplicacao.ASYNC) {
                // A1: o canario mede defasagem e guarda String; a politica de sessoes removeria as chaves dele
                sessoes = sessoes.replace("\"" + MP + "\":\"" + POLITICA_A1 + "\"", "\"" + MP + "\":\"" + PNN + "\"");
            }
            assertEquals(sessoes,
                    Files.readString(CachesCanarioJsonTest.arquivo(p.cacheCanario()), StandardCharsets.UTF_8).strip(),
                    p.cacheCanario() + " x " + p.cacheSessoes());
        }
    }

    @Test
    @DisplayName("A1: so o sessions-async usa a politica propria; counters-async e os SYNC ficam com PREFERRED_NON_NULL")
    void politicaA1SoNoSessionsAsync() throws IOException {
        for (String nome : seis()) {
            Map<String, Object> json = CachesCanarioJsonTest.mapaJson(
                    CachesCanarioJsonTest.ler(nome).get("distributed-cache"));
            Object mp = tirarMergePolicy(json);
            assertEquals(nome.equals("sessions-async") ? POLITICA_A1 : PNN, mp, nome);
        }
        assertEquals("sessions-async", PerfilReplicacao.ASYNC.cacheSessoes());
        assertEquals("counters-async", PerfilReplicacao.ASYNC.cacheContadores());
    }

    @Test
    @DisplayName("cluster/caches tem exatamente os seis JSON que o orquestrador cria")
    void exatamenteSeis() throws IOException {
        try (Stream<Path> s = Files.list(dirCaches())) {
            TreeSet<String> achados = s.map(x -> x.getFileName().toString())
                    .filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(new TreeSet<>(seis()), achados);
        }
    }
}
