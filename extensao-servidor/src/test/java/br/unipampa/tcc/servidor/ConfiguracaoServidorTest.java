package br.unipampa.tcc.servidor;

import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.io.StringBuilderWriter;
import org.infinispan.commons.configuration.io.ConfigurationWriter;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.conflict.EntryMergePolicy;
import org.infinispan.conflict.MergePolicy;
import org.infinispan.partitionhandling.PartitionHandling;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1: empacotamento e configuracao, conferidos com o proprio parser do Infinispan 15.0.21 (o mesmo que a REST usa ao
 * criar o cache, {@code CacheResourceV2}: {@code ParserRegistry.parse(json, JSON).getCurrentConfigurationBuilder()};
 * e o mesmo serializador da leitura de volta {@code ?action=config} que o {@code scripts/cria-caches.sh} compara
 * byte a byte). Guia <i>Configuring Infinispan</i> 15.0, secao 7.6.
 */
class ConfiguracaoServidorTest {

    static final String CLASSE = "br.unipampa.tcc.servidor.PoliticaFusaoSessoes";

    private static Path caches() {
        return ValoresCliente.raizRepositorio().resolve("cluster").resolve("caches");
    }

    private static String json(String nome) throws Exception {
        return Files.readString(caches().resolve(nome + ".json"), StandardCharsets.UTF_8).strip();
    }

    private static Configuration comoARest(String json) {
        return new ParserRegistry().parse(json, MediaType.APPLICATION_JSON).getCurrentConfigurationBuilder().build();
    }

    private static String leituraDeVolta(String nome, Configuration c) {
        StringBuilderWriter sw = new StringBuilderWriter();
        try (ConfigurationWriter w = ConfigurationWriter.to(sw).withType(MediaType.APPLICATION_JSON).prettyPrint(false)
                .build()) {
            new ParserRegistry().serialize(w, nome, c);
        }
        return sw.toString();
    }

    @Test
    @DisplayName("META-INF/services/org.infinispan.conflict.EntryMergePolicy tem so o nome da classe")
    void arquivoDeServico() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/services/org.infinispan.conflict.EntryMergePolicy")) {
            assertNotNull(in, "arquivo de servico ausente");
            List<String> linhas = new ArrayList<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!l.isBlank() && !l.strip().startsWith("#")) {
                    linhas.add(l.strip());
                }
            }
            assertEquals(List.of(CLASSE), linhas);
        }
        assertEquals(CLASSE, PoliticaFusaoSessoes.class.getName());
    }

    @Test
    @DisplayName("o ServiceLoader acha a politica pelo arquivo de servico")
    void serviceLoader() {
        boolean achou = false;
        for (EntryMergePolicy<?, ?> p : ServiceLoader.load(EntryMergePolicy.class)) {
            achou |= p instanceof PoliticaFusaoSessoes;
        }
        assertTrue(achou);
    }

    @Test
    @DisplayName("nome da classe vira MergePolicy.CUSTOM no parser (nao colide com as cinco embutidas)")
    void custom() {
        assertSame(MergePolicy.CUSTOM, MergePolicy.fromString(CLASSE));
    }

    @Test
    @DisplayName("sessions-async.json: o parser da REST instancia a politica, com DENY_READ_WRITES")
    void sessionsAsync() throws Exception {
        Configuration c = comoARest(json("sessions-async"));
        assertTrue(c.clustering().partitionHandling().mergePolicy() instanceof PoliticaFusaoSessoes,
                String.valueOf(c.clustering().partitionHandling().mergePolicy()));
        assertEquals(PartitionHandling.DENY_READ_WRITES, c.clustering().partitionHandling().whenSplit());
    }

    @Test
    @DisplayName("sessions-async.json: a leitura de volta (?action=config) e o arquivo com o nome, byte a byte")
    void leituraDeVoltaIgual() throws Exception {
        String arquivo = json("sessions-async");
        assertEquals("{\"sessions-async\":" + arquivo + "}", leituraDeVolta("sessions-async", comoARest(arquivo)));
    }

    @Test
    @DisplayName("counters-async e canario-async continuam com PREFERRED_NON_NULL (desbloquear/resetar; sonda de defasagem)")
    void outrosCachesSemAPolitica() throws Exception {
        for (String nome : List.of("counters-async", "canario-async", "sessions", "counters", "canario")) {
            Configuration c = comoARest(json(nome));
            assertSame(MergePolicy.PREFERRED_NON_NULL, c.clustering().partitionHandling().mergePolicy(), nome);
            assertTrue(!json(nome).contains(CLASSE), nome);
        }
    }
}
