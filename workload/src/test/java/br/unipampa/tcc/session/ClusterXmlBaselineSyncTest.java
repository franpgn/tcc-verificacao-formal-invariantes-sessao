package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-42.1, Tarefa 0 (c): os caches SYNC ({@code sessions}, {@code counters}) sao o controle de todo o
 * <i>baseline</i> F1/F3 e tem de continuar identicos ao descritor de {@code 6ba7e28}. Compara TODOS
 * os atributos do {@code distributed-cache} (exceto {@code name}) e TODOS os filhos, com todos os seus
 * atributos, com um instantaneo literal. {@code ClusterXmlIrmaosTest.syncInalterado} so fixa
 * owners/segments/partition-handling; esta guarda mata o mutante N8 da validacao V-B42.1
 * ({@code acquire-timeout} alterado nos dois irmaos ao mesmo tempo).
 */
class ClusterXmlBaselineSyncTest {

    /** Atributos do {@code distributed-cache} em {@code 6ba7e28}, sem {@code name}. */
    static final Map<String, String> ATRIBUTOS_6BA7E28 = Map.of(
            "mode", "SYNC", "owners", "2", "segments", "64", "statistics", "true");

    /** Filhos (nome local -> atributos) em {@code 6ba7e28}. */
    static final Map<String, Map<String, String>> FILHOS_6BA7E28 = Map.of(
            "locking", Map.of("concurrency-level", "1000", "acquire-timeout", "15000", "striping", "false"),
            "state-transfer", Map.of("enabled", "true", "timeout", "60000"),
            "partition-handling", Map.of("when-split", "DENY_READ_WRITES", "merge-policy", "PREFERRED_NON_NULL"));

    static final List<String> CACHES_BASELINE = List.of("sessions", "counters");

    private static Map<String, String> atributos(Element e, boolean semNome) {
        Map<String, String> m = new TreeMap<>();
        NamedNodeMap at = e.getAttributes();
        for (int i = 0; i < at.getLength(); i++) {
            String n = at.item(i).getNodeName();
            if (semNome && "name".equals(n)) {
                continue;
            }
            m.put(n, at.item(i).getNodeValue());
        }
        return m;
    }

    /** Divergencias de um {@code distributed-cache} em relacao ao instantaneo (vazio = identico). */
    static List<String> divergencias(String nome, Element e) {
        List<String> d = new ArrayList<>();
        Map<String, String> at = atributos(e, true);
        if (!at.equals(new TreeMap<>(ATRIBUTOS_6BA7E28))) {
            d.add(nome + ": atributos " + at + " != " + new TreeMap<>(ATRIBUTOS_6BA7E28));
        }
        Map<String, Map<String, String>> filhos = new TreeMap<>();
        NodeList l = e.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            if (l.item(i) instanceof Element c) {
                if (filhos.put(c.getLocalName(), atributos(c, false)) != null) {
                    d.add(nome + ": filho repetido <" + c.getLocalName() + ">");
                }
                if (c.getElementsByTagName("*").getLength() > 0) {
                    d.add(nome + ": <" + c.getLocalName() + "> ganhou filhos");
                }
            }
        }
        Map<String, Map<String, String>> esperado = new TreeMap<>();
        FILHOS_6BA7E28.forEach((k, v) -> esperado.put(k, new TreeMap<>(v)));
        if (!filhos.equals(esperado)) {
            d.add(nome + ": filhos " + filhos + " != " + esperado);
        }
        return d;
    }

    static List<String> divergencias(Map<String, Element> caches) {
        List<String> d = new ArrayList<>();
        for (String nome : CACHES_BASELINE) {
            Element e = caches.get(nome);
            if (e == null) {
                d.add(nome + ": ausente");
            } else {
                d.addAll(divergencias(nome, e));
            }
        }
        return d;
    }

    @Test
    @DisplayName("sessions e counters identicos ao instantaneo de 6ba7e28 (todos os atributos e filhos)")
    void baselineSyncIdenticoA6ba7e28() throws Exception {
        Map<String, Element> caches = ClusterXmlIrmaosTest.lerCaches(ClusterXmlIrmaosTest.localizarXml());
        for (String nome : CACHES_BASELINE) {
            assertNotNull(caches.get(nome), "cache do baseline ausente: " + nome);
        }
        List<String> d = divergencias(caches);
        assertEquals(List.of(), d, "baseline SYNC alterado:\n" + String.join("\n", d));
    }

    @Test
    @DisplayName("o comparador pega qualquer desvio de atributo ou filho (autoteste)")
    void comparadorPegaDesvios(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String base = "<distributed-cache name=\"%s\" mode=\"SYNC\" owners=\"2\" segments=\"64\" statistics=\"true\">"
                + "<locking concurrency-level=\"1000\" acquire-timeout=\"15000\" striping=\"false\"/>"
                + "<state-transfer enabled=\"true\" timeout=\"60000\"/>"
                + "<partition-handling when-split=\"DENY_READ_WRITES\" merge-policy=\"PREFERRED_NON_NULL\"/>"
                + "</distributed-cache>";
        Map<String, String> mutantes = new TreeMap<>();
        mutantes.put("0-identico", base);
        mutantes.put("n8-acquire-timeout", base.replace("acquire-timeout=\"15000\"", "acquire-timeout=\"5000\""));
        mutantes.put("striping", base.replace("striping=\"false\"", "striping=\"true\""));
        mutantes.put("atributo-novo", base.replace("statistics=\"true\"", "statistics=\"true\" l1-lifespan=\"0\""));
        mutantes.put("atributo-removido", base.replace(" statistics=\"true\"", ""));
        mutantes.put("filho-novo", base.replace("</distributed-cache>", "<expiration lifespan=\"1\"/></distributed-cache>"));
        mutantes.put("filho-removido", base.replace("<state-transfer enabled=\"true\" timeout=\"60000\"/>", ""));
        mutantes.put("timeout-st", base.replace("timeout=\"60000\"", "timeout=\"30000\""));
        mutantes.put("merge", base.replace("PREFERRED_NON_NULL", "REMOVE_ALL"));
        mutantes.put("modo", base.replace("mode=\"SYNC\"", "mode=\"ASYNC\""));
        for (Map.Entry<String, String> m : mutantes.entrySet()) {
            String xml = "<?xml version=\"1.0\"?><infinispan xmlns=\"urn:infinispan:config:15.0\"><cache-container>"
                    + String.format(m.getValue(), "sessions") + String.format(base, "counters")
                    + "</cache-container></infinispan>";
            Path f = tmp.resolve(m.getKey() + ".xml");
            Files.writeString(f, xml, StandardCharsets.UTF_8);
            List<String> d = divergencias(ClusterXmlIrmaosTest.lerCaches(f));
            if (m.getKey().equals("0-identico")) {
                assertEquals(List.of(), d, "o instantaneo literal deveria casar com ele mesmo");
            } else {
                assertTrue(!d.isEmpty() && d.stream().allMatch(s -> s.startsWith("sessions:")),
                        m.getKey() + " deveria divergir so em sessions: " + d);
            }
        }
    }
}
