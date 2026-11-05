package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3): o bloco {@code "defasagem"} do {@code controlador-*.json} ({@link ParametrosDefasagem}): campos
 * obrigatorios sem valor padrao no codigo, origem obrigatoria, histerese, conferencia contra o prazo de leitura
 * ({@code --hotrod-timeout-ms}) e a janela W, SHA-256 do arquivo cobrindo o bloco, e o que ele muda nos JSONL
 * (observaveis 1.2: {@code parametros_defasagem} na meta e a linha {@code defasagem}).
 */
class ParametrosDefasagemTest {

    static final long MS = 1_000_000L;
    static final ObjectMapper JSON = new ObjectMapper();

    /** Bloco de teste (os valores reais vem da calibracao; o passo, de decisao declarada). */
    public static final String BLOCO_TESTE = "\"defasagem\":{\"lagInMs\":50,\"lagOutMs\":20.5,\"passoLeituraMs\":10,"
            + "\"origem\":{\"lagInMs\":\"teste\",\"lagOutMs\":\"teste\",\"passoLeituraMs\":\"teste\"}}";

    @TempDir
    Path dir;

    Path arquivo(String conteudo) throws IOException {
        Path f = Files.createTempFile(dir, "controlador-", ".json");
        Files.writeString(f, conteudo, StandardCharsets.UTF_8);
        return f;
    }

    static JsonNode no(String json) throws IOException {
        return JSON.readTree(json);
    }

    @Test
    @DisplayName("bloco valido: limiares inteiros ou reais, passo inteiro, origem na ordem dos campos")
    void blocoValido() throws IOException {
        ParametrosDefasagem p = ParametrosDefasagem.deJson(no("{" + BLOCO_TESTE + "}").get("defasagem"));
        assertEquals(50.0, p.lagInMs());
        assertEquals(20.5, p.lagOutMs());
        assertEquals(10, p.passoLeituraMs());
        assertEquals(List.of("lagInMs", "lagOutMs", "passoLeituraMs"), List.copyOf(p.origem().keySet()));
        assertEquals("teste", p.origem().get("passoLeituraMs"));
        assertEquals(List.of("lagInMs", "lagOutMs", "passoLeituraMs"), ParametrosDefasagem.CAMPOS);
    }

    @Test
    @DisplayName("bloco invalido e recusado: sem padrao, origem obrigatoria, campos e tipos estritos, histerese")
    void blocoInvalido() {
        String o3 = "\"origem\":{\"lagInMs\":\"t\",\"lagOutMs\":\"t\",\"passoLeituraMs\":\"t\"}";
        List<String> ruins = new ArrayList<>(List.of(
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10}",
                "{\"lagOutMs\":20,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":50,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":50,\"lagOutMs\":20," + o3 + "}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10,\"extra\":1," + o3 + "}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10,\"origem\":{\"lagInMs\":\"t\",\"lagOutMs\":\"t\"}}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10,\"origem\":{\"lagInMs\":\" \",\"lagOutMs\":\"t\",\"passoLeituraMs\":\"t\"}}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10,\"origem\":{\"lagInMs\":1,\"lagOutMs\":\"t\",\"passoLeituraMs\":\"t\"}}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10,\"origem\":\"teste\"}",
                "{\"lagInMs\":\"50\",\"lagOutMs\":20,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":1.5," + o3 + "}",
                "{\"lagInMs\":20,\"lagOutMs\":20,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":20,\"lagOutMs\":30,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":0,\"lagOutMs\":0,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":50,\"lagOutMs\":-1,\"passoLeituraMs\":10," + o3 + "}",
                "{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":0," + o3 + "}",
                "[1,2,3]"));
        for (String r : ruins) {
            assertThrows(IllegalArgumentException.class, () -> ParametrosDefasagem.deJson(no(r)), r);
        }
        assertThrows(IllegalArgumentException.class, () -> ParametrosDefasagem.deJson(null));
    }

    @Test
    @DisplayName("validarContra: o prazo de leitura cabe em W, o passo e menor que o prazo e lagIn nao passa do prazo")
    void validarContraPrazoEJanela() {
        ParametrosDefasagem p = new ParametrosDefasagem(50, 20, 10);
        p.validarContra(100, 500 * MS); // ok
        new ParametrosDefasagem(100, 20, 10).validarContra(100, 500 * MS); // lagIn = prazo: ok
        assertThrows(IllegalArgumentException.class, () -> p.validarContra(500, 500 * MS), "prazo = W nao cabe");
        assertThrows(IllegalArgumentException.class, () -> p.validarContra(600, 500 * MS), "prazo > W");
        assertThrows(IllegalArgumentException.class, () -> p.validarContra(10, 500 * MS), "passo = prazo");
        assertThrows(IllegalArgumentException.class, () -> new ParametrosDefasagem(101, 20, 10)
                .validarContra(100, 500 * MS), "lagIn acima do prazo: alta nunca ocorreria");
        assertThrows(IllegalArgumentException.class, () -> p.validarContra(0, 500 * MS));
        assertThrows(IllegalArgumentException.class, () -> p.validarContra(Long.MAX_VALUE, Long.MAX_VALUE));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> p.validarContra(600, 500 * MS));
        assertTrue(e.getMessage().contains("--hotrod-timeout-ms"), e.getMessage());
    }

    @Test
    @DisplayName("controlador-*.json: bloco defasagem lido; ausente = null; SHA-256 cobre o bloco; nome errado recusado")
    void noArquivoDoControlador() throws IOException {
        String base = ParametrosPoliticaArquivoTest.BASE;
        ParametrosControlador com = ParametrosControlador.deJson(arquivo(base + "," + BLOCO_TESTE + "}"));
        assertEquals(50.0, com.defasagem().lagInMs());
        assertNull(com.politica(), "o bloco defasagem nao exige o bloco politica (o prazo vem da linha de comando)");
        ParametrosControlador comPol = ParametrosControlador.deJson(arquivo(base + ","
                + ParametrosPoliticaArquivoTest.BLOCO_TESTE + "," + BLOCO_TESTE + "}"));
        assertEquals(10, comPol.defasagem().passoLeituraMs());
        ParametrosControlador sem = ParametrosControlador.deJson(arquivo(base + "}"));
        assertNull(sem.defasagem());
        assertNotEquals(com.sha256(), sem.sha256(), "o SHA-256 do arquivo cobre o bloco");
        assertNull(ParametrosControlador.padrao().defasagem());
        Path errado = arquivo(base + ",\"defasagemm\":{}}");
        assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(errado));
        Path invalido = arquivo(base + ",\"defasagem\":{\"lagInMs\":50}}");
        assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(invalido));
    }

    @Test
    @DisplayName("meta: observaveis 1.2 traz parametros_defasagem (null sem bloco); decisoes nao muda")
    void metaComParametrosDaDefasagem() throws IOException {
        assertEquals("observaveis-controlador/1.2", FormatoJsonl.ESQUEMA_OBSERVAVEIS);
        String base = ParametrosPoliticaArquivoTest.BASE;
        ParametrosControlador com = ParametrosControlador.deJson(arquivo(base + "," + BLOCO_TESTE + "}"));
        MetaExecucao m = new MetaExecucao("v", 1, ModoAtuacao.SHADOW, "S3", "async", 3, com, null, null, 1, 1, 0,
                "nulo", "nenhuma", true, true);
        JsonNode o = FormatoJsonl.meta(FormatoJsonl.ESQUEMA_OBSERVAVEIS, m);
        assertTrue(o.get("sonda_defasagem").asBoolean());
        JsonNode pd = o.get("parametros_defasagem");
        assertEquals(List.of("lag_in_ms", "lag_out_ms", "passo_leitura_ms", "periodo_ns", "origem"),
                LacoDeControleTest.campos(pd));
        assertEquals(50.0, pd.get("lag_in_ms").asDouble());
        assertEquals(20.5, pd.get("lag_out_ms").asDouble());
        assertEquals(10, pd.get("passo_leitura_ms").asLong());
        assertEquals(com.wNs(), pd.get("periodo_ns").asLong());
        assertEquals("teste", pd.get("origem").get("lagOutMs").asText());
        assertFalse(FormatoJsonl.meta(FormatoJsonl.ESQUEMA_DECISOES, m).has("parametros_defasagem"),
                "decisoes 1.2 nao muda");
        MetaExecucao sem = new MetaExecucao("v", 1, ModoAtuacao.SHADOW, "S3", "async", 3,
                ParametrosControlador.padrao(), null, null, 1, 1, 0, "nulo", "nenhuma", true, false);
        JsonNode o2 = FormatoJsonl.meta(FormatoJsonl.ESQUEMA_OBSERVAVEIS, sem);
        assertTrue(o2.has("parametros_defasagem") && o2.get("parametros_defasagem").isNull(), "nunca omitido");
    }
}
