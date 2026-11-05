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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3: arquivos e contratos do pacote {@code controle} usados pelo laco: leitura da referencia de p99
 * (esquema {@code referencia-p99/1} do B-39.1), trilha JSONL, formato das linhas de decisao e de acao e
 * o requisito H4 (a sonda normaliza {@code falha:TimeoutException} para {@code timeout}).
 */
class ControleArquivosTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final long S = 1_000_000_000L;

    @TempDir
    Path dir;

    Path escrever(String nome, String conteudo) throws IOException {
        Path p = dir.resolve(nome);
        Files.writeString(p, conteudo, StandardCharsets.UTF_8);
        return p;
    }

    static final String REF_OK = "{\n \"esquema\": \"referencia-p99/1\",\n \"cenario\": \"S3\",\n \"perfil\": \"async\",\n"
            + " \"metodo\": \"posto-mais-proximo\",\n \"n_min\": 1000,\n"
            + " \"p99_ns\": {\"Login\": 3000000, \"Validate\": 1500000},\n \"n\": {\"Login\": 5000, \"Validate\": 9000},\n"
            + " \"omitidas\": {},\n \"origem\": []\n}\n";

    // ------------------------------------------------------- referencia

    @Test
    @DisplayName("ArquivoReferencia le p99_ns, cenario, perfil e o SHA-256 dos bytes")
    void referenciaOk() throws Exception {
        Path p = escrever("referencia-S3-async.json", REF_OK);
        ArquivoReferencia r = ArquivoReferencia.ler(p);
        assertEquals("S3", r.cenario());
        assertEquals("async", r.perfil());
        assertEquals(Map.of("Login", 3_000_000L, "Validate", 1_500_000L), r.referencia().p99PorOperacao());
        assertEquals(64, r.sha256().length());
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(p))), r.sha256());
    }

    @Test
    @DisplayName("ArquivoReferencia rejeita esquema errado, p99 ausente, nao inteiro ou nao positivo e JSON malformado")
    void referenciaInvalida() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> ArquivoReferencia.ler(escrever("a.json", REF_OK.replace("referencia-p99/1", "referencia-p99/2"))));
        assertThrows(IllegalArgumentException.class,
                () -> ArquivoReferencia.ler(escrever("b.json", REF_OK.replace("\"p99_ns\"", "\"p99\""))));
        assertThrows(IllegalArgumentException.class,
                () -> ArquivoReferencia.ler(escrever("c.json", REF_OK.replace("3000000", "3.5"))));
        assertThrows(IllegalArgumentException.class,
                () -> ArquivoReferencia.ler(escrever("d.json", REF_OK.replace("3000000", "0"))));
        assertThrows(IllegalArgumentException.class,
                () -> ArquivoReferencia.ler(escrever("e.json", "{nao e json")));
        assertThrows(IllegalArgumentException.class,
                () -> ArquivoReferencia.ler(escrever("f.json", REF_OK.replace("\"cenario\": \"S3\",", ""))));
    }

    // ------------------------------------------------------- trilha

    @Test
    @DisplayName("TrilhaJsonl grava uma linha JSON por chamada, com flush; depois de fechada ignora e conta")
    void trilha() throws IOException {
        Path p = dir.resolve("sub/x.jsonl");
        TrilhaJsonl t = TrilhaJsonl.abrir(p);
        t.escrever(JSON.createObjectNode().put("tipo", "a").put("n", 1));
        assertEquals(1, Files.readAllLines(p).size(), "flush por linha");
        t.escrever(JSON.createObjectNode().put("tipo", "b"));
        t.close();
        t.escrever(JSON.createObjectNode().put("tipo", "c"));
        List<String> l = Files.readAllLines(p, StandardCharsets.UTF_8);
        assertEquals(List.of("{\"tipo\":\"a\",\"n\":1}", "{\"tipo\":\"b\"}"), l);
        assertEquals(2, t.linhas());
        assertEquals(1, t.descartadas());
        t.close();
    }

    // ------------------------------------------------------- formato

    @Test
    @DisplayName("linha de decisao: todos os sinais do nucleo, inclusive a defasagem, em snake_case")
    void formatoDecisao() {
        Controlador c = new Controlador(ParametrosControlador.padrao());
        Decisao d = c.avaliar(new Janela(0, 5 * S, 1000, 50, 20, 1000, 100, 1, 2),
                new EstadoTopologia(true, 2, 1, 2), ControladorDefasagemTest.ALTA);
        JsonNode n = FormatoJsonl.decisao(d, ModoAtuacao.ACTIVE, 6 * S);
        assertEquals(6 * S, n.get("t_decisao_ns").asLong());
        JsonNode s = n.get("sinais");
        List<String> campos = new java.util.ArrayList<>();
        s.fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("taxa_latencia", "taxa_erro", "fracao_indisponivel", "latencia_alta", "erro_alto",
                "topologia_anomala", "topologia_disponivel", "topologia_amostras", "topologia_anomalas", "nos_minimo",
                "violacoes_seguranca", "violacoes_i2_esperadas", "persistencia_latencia", "persistencia_erro",
                "janelas_calmas", "cooldown_expirado", "defasagem", "defasagem_alta", "persistencia_defasagem",
                "no_suspeito", "lag_max_ms", "defasagem_amostras"), campos);
        assertEquals("active", n.get("modo").asText());
        assertEquals("R1", n.get("regra").asText());
        assertEquals("ALTA", s.get("defasagem").asText());
        assertEquals("isn3", s.get("no_suspeito").asText());
        assertEquals(List.of("VIOLACAO", "TOPOLOGIA", "DEFASAGEM", "ERRO", "LATENCIA"),
                JSON.convertValue(n.get("motivos"), List.class));
    }

    @Test
    @DisplayName("linha de acao: operacao, executada, acao, origem, regra, tempos e resultado")
    void formatoAcao() {
        RegistroAcao r = new RegistroAcao(RegistroAcao.Operacao.APLICAR, ExecutorDeAcoesTest.AC1, ModoAtuacao.SHADOW,
                "falso", false, "PREVISTA", RegistroAcao.Origem.POLITICA, Decisao.Regra.R2, 5 * S, 6 * S,
                null, null, null, null, null);
        JsonNode n = FormatoJsonl.acao(r);
        List<String> campos = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("tipo", "operacao", "executada", "modo", "atuador", "acao", "origem", "regra",
                "t_fim_janela_ns", "t_pedido_ns", "t_inicio_ns", "duracao_ns", "resultado", "codigo_http",
                "lido", "erro", "reversao"), campos);
        assertTrue(n.get("reversao").isNull(), "acao reversivel: reversao pela pilha, campo null");
        assertEquals("acao", n.get("tipo").asText());
        assertFalse(n.get("executada").asBoolean());
        assertEquals("AC-2", n.get("acao").get("tipo").asText());
        assertEquals(List.of("sessions", "counters"), JSON.convertValue(n.get("acao").get("caches"), List.class));
        assertTrue(n.get("acao").get("alvo").isNull());
        assertTrue(n.get("acao").get("valor").isNull());
        assertTrue(n.get("codigo_http").isNull());
    }

    // ------------------------------------------------------- H4

    @Test
    @DisplayName("H4: falha de consulta por TimeoutException vira motivo 'timeout' (nao 'falha:TimeoutException')")
    void h4TimeoutNormalizado() {
        FonteDeSaude f = new FonteDeSaude() {
            @Override public int nos() { return 2; }
            @Override public String rotulo(int i) { return "no" + i; }
            @Override public LeituraSaude consultar(int i, long prazoNs) throws Exception {
                if (i == 0) {
                    throw new TimeoutException("curl excedeu o prazo de 1500 ms");
                }
                throw new java.io.IOException("curl falhou (exit 7)");
            }
        };
        AtomicLong rel = new AtomicLong(S);
        try (SondaTopologia s = new SondaTopologia(f, List.of("c1"), 2, 2 * S, 1500 * 1_000_000L, rel::get, a -> { })) {
            AmostraTopologia a = s.rodada();
            assertEquals("timeout", a.porNo().get(0).motivo());
            assertFalse(a.porNo().get(0).respondeu());
            assertTrue(a.porNo().get(0).anomalo());
            assertEquals("falha:IOException", a.porNo().get(1).motivo(), "outras falhas mantem o nome");
        }
    }
}
