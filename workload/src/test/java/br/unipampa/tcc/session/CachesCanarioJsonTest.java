package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3), item 1 do prompt, com a decisao do coordenador de 05/11: os <i>caches</i> {@code canario}
 * (SYNC) e {@code canario-async} (ASYNC) <b>nao</b> estao no XML; sao criados pela REST a partir de
 * {@code cluster/caches/<nome>.json} ({@code scripts/cria-caches.sh}), como no contorno do P-30 do B-44.1b. Este
 * teste e a extensao do {@link ClusterXmlIrmaosTest} para eles (que continua lendo so o XML):
 *
 * <ul>
 *   <li>um arquivo por perfil, com o nome de {@link PerfilReplicacao#cacheCanario()}, sem o envoltorio do nome
 *       (com o nome, {@code counters} colide com o elemento {@code <counters>} do parser, ISPN029520), numa linha
 *       compacta (o {@code cria-caches.sh} compara a leitura de volta {@code ?action=config} byte a byte);</li>
 *   <li>cada um igual ao irmao da carga no XML (owners, segments, statistics, locking, state-transfer,
 *       partition-handling), salvo {@code remote-timeout};</li>
 *   <li>{@code remote-timeout} = 17500 explicito no SYNC, o valor efetivo do XML (B-44.1 secao 3: criado pela REST
 *       sem o atributo, o padrao seria 15000), e ausente no ASYNC, como na leitura de volta do XML;</li>
 *   <li>os dois so diferem em {@code mode} e {@code remote-timeout}; o XML continua com os quatro da carga.</li>
 * </ul>
 */
class CachesCanarioJsonTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String NS = "urn:infinispan:config:15.0";
    static final String RT = "remote-timeout";
    /** Valor efetivo do XML nos SYNC (B-44.1 secao 3; runs/b44-1/corpos/config-sessions-isn1.txt). */
    static final String RT_SYNC = "17500";

    static Path arquivo(String nome) {
        return ClusterXmlIrmaosTest.localizarXml().getParent().resolve("caches").resolve(nome + ".json");
    }

    static JsonNode ler(String nome) throws IOException {
        return JSON.readTree(Files.readString(arquivo(nome), StandardCharsets.UTF_8));
    }

    /** O objeto {@code distributed-cache} como mapa de texto (filhos objeto viram mapas). */
    static Map<String, Object> mapaJson(JsonNode cache) {
        Map<String, Object> m = new TreeMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = cache.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            m.put(e.getKey(), e.getValue().isObject() ? mapaJson(e.getValue()) : e.getValue().asText());
        }
        return m;
    }

    /** O elemento {@code distributed-cache} como mapa de texto: atributos (sem {@code name}) e filhos elemento. */
    static Map<String, Object> mapaXml(Element e) {
        Map<String, Object> m = new TreeMap<>();
        NamedNodeMap at = e.getAttributes();
        for (int i = 0; i < at.getLength(); i++) {
            if (!"name".equals(at.item(i).getNodeName())) {
                m.put(at.item(i).getNodeName(), at.item(i).getNodeValue());
            }
        }
        NodeList l = e.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            if (l.item(i) instanceof Element c) {
                m.put(c.getLocalName(), mapaXml(c));
            }
        }
        return m;
    }

    @Test
    @DisplayName("um JSON por perfil, sem envoltorio do nome, numa linha compacta, com o modo do perfil")
    void umArquivoPorPerfil() throws IOException {
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            String nome = p.cacheCanario();
            assertTrue(Files.isRegularFile(arquivo(nome)), "ausente: " + arquivo(nome));
            String bruto = Files.readString(arquivo(nome), StandardCharsets.UTF_8);
            JsonNode raiz = JSON.readTree(bruto);
            assertEquals(List.of("distributed-cache"), List.copyOf(mapaJson(raiz).keySet()),
                    "so o objeto distributed-cache, sem o nome: " + nome);
            assertEquals(JSON.writeValueAsString(raiz), bruto.strip(), "linha compacta: " + nome);
            assertEquals(p.name(), raiz.get("distributed-cache").get("mode").asText(), "mode de " + nome);
        }
    }

    @Test
    @DisplayName("canario* iguais aos irmaos da carga no XML, salvo remote-timeout (17500 explicito so no SYNC)")
    void iguaisAosIrmaosDoXml() throws Exception {
        Map<String, Element> xml = ClusterXmlIrmaosTest.lerCaches(ClusterXmlIrmaosTest.localizarXml());
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            Map<String, Object> canario = mapaJson(ler(p.cacheCanario()).get("distributed-cache"));
            Map<String, Object> irmao = mapaXml(xml.get(p.cacheSessoes()));
            Object rt = canario.remove(RT);
            assertEquals(irmao, canario, p.cacheCanario() + " x " + p.cacheSessoes());
            assertFalse(irmao.containsKey(RT), "o XML nao declara remote-timeout");
            if (p == PerfilReplicacao.SYNC) {
                assertEquals(RT_SYNC, rt, "remote-timeout efetivo do XML no SYNC");
            } else {
                assertEquals(null, rt, "ASYNC sem remote-timeout, como na leitura de volta do XML");
            }
        }
    }

    @Test
    @DisplayName("canario x canario-async: so mode e remote-timeout diferem; o XML continua sem canario")
    void irmaosEntreSi() throws Exception {
        Map<String, Object> s = mapaJson(ler(PerfilReplicacao.SYNC.cacheCanario()).get("distributed-cache"));
        Map<String, Object> a = mapaJson(ler(PerfilReplicacao.ASYNC.cacheCanario()).get("distributed-cache"));
        for (Map<String, Object> m : List.of(s, a)) {
            m.remove("mode");
            m.remove(RT);
        }
        assertEquals(s, a);
        Map<String, Element> xml = ClusterXmlIrmaosTest.lerCaches(ClusterXmlIrmaosTest.localizarXml());
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            assertFalse(xml.containsKey(p.cacheCanario()), "canario no XML: " + xml.keySet());
        }
        NodeList ph = xml.get(PerfilReplicacao.SYNC.cacheSessoes()).getElementsByTagNameNS(NS, "partition-handling");
        assertEquals(1, ph.getLength());
    }
}
