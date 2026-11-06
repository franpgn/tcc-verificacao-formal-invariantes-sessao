package br.unipampa.tcc.session;

import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1 (2026-11-06): os valores de {@code extensao-servidor/src/test/resources/br/unipampa/tcc/servidor/valores-cliente.tsv},
 * com que a politica de fusao do servidor e testada, sao os bytes que ESTE cliente grava: as classes reais
 * ({@link SessionState}, {@link IdentityState}) e o marshaller do cliente ({@link JavaSerializationMarshaller} do
 * infinispan-commons do Hot Rod, o mesmo de {@code S3Clientes.base}). Se a classe ou o marshaller mudarem, este teste
 * falha e o arquivo (e a copia de {@code SessionState} no JAR do servidor) tem de ser refeito.
 */
class ValoresServidorA1Test {

    static Path arquivo() {
        Path cluster = ClusterXmlIrmaosTest.localizarXml().getParent();
        return cluster.getParent().resolve("extensao-servidor").resolve("src").resolve("test").resolve("resources")
                .resolve("br").resolve("unipampa").resolve("tcc").resolve("servidor").resolve("valores-cliente.tsv");
    }

    private static Map<String, String> campos(String coluna) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String kv : coluna.split(",")) {
            String[] p = kv.split("=", 2);
            m.put(p[0], p[1]);
        }
        return m;
    }

    private static Object objeto(String tipo, Map<String, String> c) {
        switch (tipo) {
            case "SessionState": {
                Instant criada = Instant.parse(c.get("criadaEm"));
                // mesmo Instant nos dois campos quando iguais, como SessionState.criar
                Instant atualizada = c.get("atualizadaEm").equals(c.get("criadaEm")) ? criada
                        : Instant.parse(c.get("atualizadaEm"));
                return new SessionState(c.get("id"), c.get("identidade"), SessionState.Estado.valueOf(c.get("estado")),
                        SessionState.Motivo.valueOf(c.get("motivo")), Long.parseLong(c.get("versao")), criada, atualizada);
            }
            case "IdentityState":
                return new IdentityState(c.get("identidade"), Long.parseLong(c.get("contadorFalhas")),
                        IdentityState.Status.valueOf(c.get("status")), Long.parseLong(c.get("versao")),
                        Instant.parse(c.get("atualizadaEm")));
            case "String":
                return c.get("valor");
            default:
                throw new IllegalArgumentException("tipo desconhecido: " + tipo);
        }
    }

    @Test
    @DisplayName("cada linha do arquivo do servidor e o que o marshaller do cliente grava para o objeto descrito")
    void bytesDoCliente() throws Exception {
        Path f = arquivo();
        assertTrue(Files.isRegularFile(f), "ausente: " + f);
        JavaSerializationMarshaller m = new JavaSerializationMarshaller();
        int n = 0;
        for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (l.isBlank() || l.startsWith("#")) {
                continue;
            }
            String[] c = l.split("\t");
            assertEquals(4, c.length, l);
            String hex = HexFormat.of().formatHex(m.objectToByteBuffer(objeto(c[1], campos(c[2]))));
            assertEquals(c[3], hex, c[0]);
            n++;
        }
        assertEquals(12, n, "linhas do arquivo");
    }
}
