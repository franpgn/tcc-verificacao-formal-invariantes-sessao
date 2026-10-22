package br.unipampa.tcc.session;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-42.1: o descritor {@code cluster/infinispan-cluster.xml} declara, para cada perfil de
 * {@link PerfilReplicacao}, os dois caches que o workload abre, e os pares irmaos (SYNC x ASYNC)
 * so diferem no atributo {@code mode}. Le o XML com parser DOM (com namespaces), nao com regex.
 */
class ClusterXmlIrmaosTest {

    private static final String NS = "urn:infinispan:config:15.0";
    private static Map<String, Element> caches;

    /** Sobe a partir do diretorio de trabalho do Surefire ({@code workload/}) ate achar o XML. */
    static Path localizarXml() {
        Path d = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && d != null; i++, d = d.getParent()) {
            Path p = d.resolve("cluster").resolve("infinispan-cluster.xml");
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("cluster/infinispan-cluster.xml nao encontrado a partir de "
                + System.getProperty("user.dir"));
    }

    static Map<String, Element> lerCaches(Path xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(xml.toFile());
        NodeList l = doc.getElementsByTagNameNS(NS, "distributed-cache");
        Map<String, Element> out = new LinkedHashMap<>();
        for (int i = 0; i < l.getLength(); i++) {
            Element e = (Element) l.item(i);
            out.put(e.getAttribute("name"), e);
        }
        return out;
    }

    @BeforeAll
    static void carregar() throws Exception {
        caches = lerCaches(localizarXml());
    }

    /** Atributos do elemento, sem {@code name} e {@code mode}. */
    private static Map<String, String> atributosSemNomeEModo(Element e) {
        Map<String, String> m = new TreeMap<>();
        NamedNodeMap at = e.getAttributes();
        for (int i = 0; i < at.getLength(); i++) {
            Node a = at.item(i);
            if (!"name".equals(a.getNodeName()) && !"mode".equals(a.getNodeName())) {
                m.put(a.getNodeName(), a.getNodeValue());
            }
        }
        return m;
    }

    /** Filhos elemento (locking, state-transfer, partition-handling...) com todos os atributos. */
    private static Map<String, Map<String, String>> filhos(Element e) {
        Map<String, Map<String, String>> m = new TreeMap<>();
        NodeList l = e.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            if (l.item(i) instanceof Element c) {
                Map<String, String> at = new TreeMap<>();
                NamedNodeMap nm = c.getAttributes();
                for (int j = 0; j < nm.getLength(); j++) {
                    at.put(nm.item(j).getNodeName(), nm.item(j).getNodeValue());
                }
                m.put(c.getLocalName(), at);
            }
        }
        return m;
    }

    private static Element cache(String nome) {
        Element e = caches.get(nome);
        assertNotNull(e, "cache ausente no XML: " + nome + " (presentes: " + caches.keySet() + ")");
        return e;
    }

    private static Element partitionHandling(Element cache) {
        NodeList l = cache.getElementsByTagNameNS(NS, "partition-handling");
        assertEquals(1, l.getLength(), "partition-handling em " + cache.getAttribute("name"));
        return (Element) l.item(0);
    }

    @Test
    @DisplayName("os quatro caches dos dois perfis existem e o modo segue o perfil")
    void cachesDosPerfisExistemComOModoCerto() {
        for (PerfilReplicacao p : PerfilReplicacao.values()) {
            for (String nome : p.caches()) {
                assertEquals(p.name(), cache(nome).getAttribute("mode"), "mode de " + nome);
            }
        }
        assertEquals(4, caches.size(), "so os quatro caches do workload: " + caches.keySet());
    }

    @Test
    @DisplayName("irmaos SYNC x ASYNC: owners, segments e partition-handling iguais")
    void irmaosTemOwnersSegmentsEPartitionHandlingIguais() {
        List<String[]> pares = List.of(
                new String[]{PerfilReplicacao.SYNC.cacheSessoes(), PerfilReplicacao.ASYNC.cacheSessoes()},
                new String[]{PerfilReplicacao.SYNC.cacheContadores(), PerfilReplicacao.ASYNC.cacheContadores()});
        for (String[] par : pares) {
            Element s = cache(par[0]);
            Element a = cache(par[1]);
            String rot = par[0] + " x " + par[1];
            assertEquals("SYNC", s.getAttribute("mode"), rot);
            assertEquals("ASYNC", a.getAttribute("mode"), rot);
            for (String attr : List.of("owners", "segments")) {
                assertTrue(s.hasAttribute(attr), attr + " ausente em " + par[0]);
                assertEquals(s.getAttribute(attr), a.getAttribute(attr), attr + ": " + rot);
            }
            Element ps = partitionHandling(s);
            Element pa = partitionHandling(a);
            for (String attr : List.of("when-split", "merge-policy")) {
                assertTrue(ps.hasAttribute(attr), attr + " ausente em " + par[0]);
                assertEquals(ps.getAttribute(attr), pa.getAttribute(attr), attr + ": " + rot);
            }
        }
    }

    @Test
    @DisplayName("irmaos so diferem em name/mode (atributos e filhos identicos)")
    void irmaosSoDiferemNoModo() {
        for (int i = 0; i < 2; i++) {
            Element s = cache(PerfilReplicacao.SYNC.caches().get(i));
            Element a = cache(PerfilReplicacao.ASYNC.caches().get(i));
            assertEquals(atributosSemNomeEModo(s), atributosSemNomeEModo(a), s.getAttribute("name"));
            assertEquals(filhos(s), filhos(a), s.getAttribute("name"));
        }
    }

    @Test
    @DisplayName("valores de referencia dos caches SYNC continuam os do baseline")
    void syncInalterado() {
        for (String nome : PerfilReplicacao.SYNC.caches()) {
            Element e = cache(nome);
            assertEquals("2", e.getAttribute("owners"), nome);
            assertEquals("64", e.getAttribute("segments"), nome);
            Element ph = partitionHandling(e);
            assertEquals("DENY_READ_WRITES", ph.getAttribute("when-split"), nome);
            assertEquals("PREFERRED_NON_NULL", ph.getAttribute("merge-policy"), nome);
        }
    }
}
