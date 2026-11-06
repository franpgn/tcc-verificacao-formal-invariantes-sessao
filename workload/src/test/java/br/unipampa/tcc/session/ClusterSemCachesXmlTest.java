package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (B-46), requisito 1: {@code cluster/infinispan-sem-caches.xml} (promovido de
 * {@code runs/b44-1b/cluster/infinispan-sem-caches.xml}, B-44.1b, contorno b do P-30) e o XML do cluster da
 * calibracao e da avaliacao. Ele e o {@code cluster/infinispan-cluster.xml} sem nenhum cache: tirando os
 * {@code distributed-cache} do XML com caches, os comentarios e o texto so de espaco, os dois documentos sao
 * iguais no-a-no. O {@code cluster/podman-compose-sem-caches.yml} e o {@code podman-compose.yml} com o volume do
 * XML trocado e, desde o A1 (2026-11-06), o JAR da extensao do servidor ({@code extensao-servidor}, politica de fusao
 * do {@code sessions-async}) montado em {@code server/lib} nos tres servicos, e nada mais (mesmos nomes de conteiner,
 * portas, imagem e {@code external-port}).
 */
class ClusterSemCachesXmlTest {

    static final String NS = "urn:infinispan:config:15.0";
    /** Todos os elementos de cache do esquema 15.0 (nenhum pode aparecer no XML sem caches). */
    static final Set<String> CACHES = Set.of("distributed-cache", "replicated-cache", "local-cache",
            "invalidation-cache", "scattered-cache", "distributed-cache-configuration",
            "replicated-cache-configuration", "local-cache-configuration", "invalidation-cache-configuration");

    static Path dirCluster() {
        return ClusterXmlIrmaosTest.localizarXml().getParent();
    }

    static Document ler(Path p) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(p.toFile());
    }

    /** Tira comentarios, texto so de espaco e, com {@code semCaches}, os elementos de cache; recursivo. */
    static void limpar(Node n, boolean semCaches) {
        NodeList l = n.getChildNodes();
        for (int i = l.getLength() - 1; i >= 0; i--) {
            Node c = l.item(i);
            boolean tirar = c.getNodeType() == Node.COMMENT_NODE
                    || (c.getNodeType() == Node.TEXT_NODE && c.getNodeValue().isBlank())
                    || (semCaches && c.getNodeType() == Node.ELEMENT_NODE && CACHES.contains(c.getLocalName()));
            if (tirar) {
                n.removeChild(c);
            } else {
                limpar(c, semCaches);
            }
        }
    }

    @Test
    @DisplayName("o XML sem caches nao declara cache nenhum")
    void semNenhumCache() throws Exception {
        Document d = ler(dirCluster().resolve("infinispan-sem-caches.xml"));
        for (String tag : CACHES) {
            assertEquals(0, d.getElementsByTagNameNS(NS, tag).getLength(), tag);
        }
        assertEquals(1, d.getElementsByTagNameNS(NS, "cache-container").getLength());
        assertEquals(1, d.getElementsByTagNameNS(NS, "transport").getLength());
    }

    @Test
    @DisplayName("fora os caches, o XML sem caches e o XML versionado no-a-no (transporte, servidor, conectores)")
    void igualAoXmlSemOsCaches() throws Exception {
        Document base = ler(ClusterXmlIrmaosTest.localizarXml());
        Document sem = ler(dirCluster().resolve("infinispan-sem-caches.xml"));
        assertTrue(base.getElementsByTagNameNS(NS, "distributed-cache").getLength() > 0, "o XML base tem caches");
        limpar(base, true);
        limpar(sem, false);
        base.getDocumentElement().normalize();
        sem.getDocumentElement().normalize();
        assertTrue(base.getDocumentElement().isEqualNode(sem.getDocumentElement()),
                "infinispan-sem-caches.xml difere de infinispan-cluster.xml fora dos caches");
    }

    static List<String> semComentarios(Path p) throws Exception {
        return Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                .map(String::stripTrailing)
                .filter(l -> !l.strip().startsWith("#"))
                .collect(Collectors.toList());
    }

    /** A1: volume do JAR da extensao do servidor (relativo a {@code cluster/}), so leitura, em {@code server/lib}. */
    static final String VOLUME_JAR =
            "      - ../extensao-servidor/target/tcc-extensao-servidor.jar:/opt/infinispan/server/lib/tcc-extensao-servidor.jar:ro";

    @Test
    @DisplayName("compose sem caches = compose versionado com o volume do XML trocado e o JAR do A1, nos tres servicos")
    void composeSoTrocaOXml() throws Exception {
        List<String> base = semComentarios(dirCluster().resolve("podman-compose.yml"));
        List<String> sem = semComentarios(dirCluster().resolve("podman-compose-sem-caches.yml"));
        String velho = "./infinispan-cluster.xml:/opt/infinispan/server/conf/infinispan.xml:ro";
        String novo = "./infinispan-sem-caches.xml:/opt/infinispan/server/conf/infinispan.xml:ro";
        String jgroups = "./jgroups-tcp.xml:/opt/infinispan/server/conf/jgroups-tcp.xml:ro";
        assertEquals(3, base.stream().filter(l -> l.contains(velho)).count(), "tres volumes no compose base");
        assertEquals(3, base.stream().filter(l -> l.contains(jgroups)).count(), "tres volumes do jgroups no compose base");
        List<String> trocado = new java.util.ArrayList<>();
        for (String l : base) {
            trocado.add(l.replace(velho, novo));
            if (l.contains(jgroups)) {
                trocado.add(VOLUME_JAR);
            }
        }
        assertEquals(trocado, sem);
        assertEquals(3, sem.stream().filter(VOLUME_JAR::equals).count(), "o JAR nos tres servicos");
    }
}
