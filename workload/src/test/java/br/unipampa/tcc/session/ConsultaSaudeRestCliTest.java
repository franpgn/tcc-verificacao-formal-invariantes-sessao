package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T16 e saida JSONL): CLI de smoke {@link SondaTopologiaCli}. Os erros de uso sao testados sem
 * rede (o parse falha antes de qualquer consulta); a saida e testada com tres {@link ServidorAdminFalso} com Digest
 * (W-2b: a consulta e HTTP em memoria, sem curl).
 */
class ConsultaSaudeRestCliTest {

    private static final Map<String, String> COM_SENHA = Map.of("SONDA_SENHA", "s3cr3t");
    private static final String SERVERS = "127.0.0.1:12262,127.0.0.1:12263,127.0.0.1:12264";

    private static final class Saida {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final PrintStream pOut = new PrintStream(out, true, StandardCharsets.UTF_8);
        final PrintStream pErr = new PrintStream(err, true, StandardCharsets.UTF_8);

        String out() { return out.toString(StandardCharsets.UTF_8); }
        String err() { return err.toString(StandardCharsets.UTF_8); }
    }

    /** Executa sem rede: a fabrica do cliente HTTP falha se for chamada. */
    private static int semRede(Saida s, Map<String, String> env, String... argv) {
        return SondaTopologiaCli.executar(argv, env, s.pOut, s.pErr, (usuario, senha) -> {
            throw new AssertionError("nenhuma consulta deveria ser feita: " + usuario);
        });
    }

    // ---------------------------------------------------------------- T16 erros de uso

    @Test
    @DisplayName("T16: sem --replication: exit 2 (sem perfil padrao)")
    void semReplication() {
        Saida s = new Saida();
        assertEquals(2, semRede(s, COM_SENHA, "--servers", SERVERS));
        assertTrue(s.err().contains("--replication"), s.err());
        assertThrows(IllegalArgumentException.class,
                () -> SondaTopologiaCli.parse(new String[] {"--servers", SERVERS}, COM_SENHA));
    }

    @Test
    @DisplayName("T16: --replication xyz: exit 2")
    void replicationInvalida() {
        Saida s = new Saida();
        assertEquals(2, semRede(s, COM_SENHA, "--servers", SERVERS, "--replication", "xyz"));
        assertThrows(IllegalArgumentException.class, () -> SondaTopologiaCli.parse(
                new String[] {"--servers", SERVERS, "--replication", "xyz"}, COM_SENHA));
    }

    @Test
    @DisplayName("T16: sem SONDA_SENHA (ou vazia): exit 2")
    void semSenha() {
        Saida s = new Saida();
        assertEquals(2, semRede(s, Map.of(), "--servers", SERVERS, "--replication", "sync"));
        assertTrue(s.err().contains("SONDA_SENHA"), s.err());
        assertEquals(2, semRede(new Saida(), Map.of("SONDA_SENHA", ""), "--servers", SERVERS, "--replication", "sync"));
    }

    @Test
    @DisplayName("T16: opcao desconhecida, opcao sem valor, sem --servers, prazo >= periodo: exit 2")
    void outrosErrosDeUso() {
        assertEquals(2, semRede(new Saida(), COM_SENHA, "--servers", SERVERS, "--replication", "sync", "--x", "1"));
        assertEquals(2, semRede(new Saida(), COM_SENHA, "--servers", SERVERS, "--replication"));
        assertEquals(2, semRede(new Saida(), COM_SENHA, "--replication", "sync"));
        assertEquals(2, semRede(new Saida(), COM_SENHA, "--servers", "h", "--replication", "sync"));
        assertEquals(2, semRede(new Saida(), COM_SENHA, "--servers", SERVERS, "--replication", "sync",
                "--periodo-ms", "1000", "--prazo-ms", "1000"));
        assertEquals(2, semRede(new Saida(), COM_SENHA, "--servers", SERVERS, "--replication", "sync",
                "--duracao-s", "0"));
    }

    @Test
    @DisplayName("parse: padroes (admin, 2000 ms, 1500 ms, 60 s), perfil pelo rotulo, URLs de --servers")
    void padroes() {
        SondaTopologiaCli.Args a = SondaTopologiaCli.parse(
                new String[] {"--servers", SERVERS, "--replication", "ASYNC"}, COM_SENHA);
        assertEquals(PerfilReplicacao.ASYNC, a.perfil());
        assertEquals("admin", a.usuario());
        assertEquals(2000, a.periodoMs());
        assertEquals(1500, a.prazoMs());
        assertEquals(60, a.duracaoS());
        assertEquals(List.of("http://127.0.0.1:12262", "http://127.0.0.1:12263", "http://127.0.0.1:12264"), a.urls());
        assertFalse(a.toString().contains("s3cr3t"), a.toString());
        SondaTopologiaCli.Args b = SondaTopologiaCli.parse(new String[] {"--servers", SERVERS, "--replication", "sync",
                "--usuario", "u", "--periodo-ms", "500", "--prazo-ms", "400", "--duracao-s", "5"}, COM_SENHA);
        assertEquals(PerfilReplicacao.SYNC, b.perfil());
        assertEquals("u", b.usuario());
        assertEquals(500, b.periodoMs());
        assertEquals(400, b.prazoMs());
        assertEquals(5, b.duracaoS());
    }

    // ---------------------------------------------------------------- saida JSONL

    @Test
    @DisplayName("saida: uma linha JSON por amostra e o resumo no fim; perfil async anomalo pela captura sync")
    void saidaJsonl() throws Exception {
        String corpo = ConsultaSaudeRestTest.capturaReal("rep3-isn3-healthy-3nos.json");
        List<ServidorAdminFalso> nos = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            nos.add(new ServidorAdminFalso(ConsultaSaudeRestTest.saudeComDigest("admin", "s3cr3t",
                    ServidorAdminFalso.Resp.de(200, corpo), erros)));
        }
        String servers = String.join(",", nos.stream().map(ServidorAdminFalso::rotulo).toList());
        // cliente aquecido (desafio Digest guardado e conexao aberta), para o prazo valer so para a consulta
        ClienteAdminRest cliente = new ClienteAdminRest("admin", "s3cr3t");
        for (ServidorAdminFalso n : nos) {
            cliente.pedir("GET", URI.create(n.url() + ConsultaSaudeRestTest.ENDPOINT), 2_000_000_000L);
        }
        Saida s = new Saida();
        int exit;
        try {
            exit = SondaTopologiaCli.executar(new String[] {"--servers", servers, "--replication", "async",
                    // HTTP real no loopback (W-2b): 400 ms de prazo, nao os 100 ms do lancador falso, que respondia
                    // na hora; com 100 ms a suite inteira estourou o prazo uma vez (nos_min = 0)
                    "--periodo-ms", "500", "--prazo-ms", "400", "--duracao-s", "2"}, COM_SENHA, s.pOut, s.pErr,
                    (usuario, senha) -> cliente);
        } finally {
            nos.forEach(ServidorAdminFalso::close);
        }
        assertEquals(0, exit, s.err());
        ObjectMapper json = new ObjectMapper();
        List<JsonNode> linhas = new ArrayList<>();
        for (String linha : s.out().split("\n")) {
            if (!linha.isBlank()) {
                linhas.add(json.readTree(linha));
            }
        }
        assertTrue(linhas.size() >= 3, s.out());
        JsonNode resumo = linhas.get(linhas.size() - 1).get("resumo");
        assertTrue(resumo != null, "ultima linha deveria ser o resumo: " + s.out());
        List<JsonNode> amostras = linhas.subList(0, linhas.size() - 1);
        assertEquals(amostras.size(), resumo.get("rodadas").asInt());
        assertEquals(0, resumo.get("puladas").asInt());
        // a captura real so tem os caches sync: no perfil async todos os nos ficam cache-ausente
        assertEquals(amostras.size(), resumo.get("anomalas").asInt());
        long anterior = 0;
        for (JsonNode a : amostras) {
            assertTrue(a.get("t_epoch_ns").asLong() > 1_600_000_000_000_000_000L, a.toString());
            assertTrue(a.get("t_epoch_ns").asLong() >= anterior);
            anterior = a.get("t_epoch_ns").asLong();
            assertTrue(a.get("anomala").asBoolean());
            assertEquals(3, a.get("nos_min").asInt());
            JsonNode porNo = a.get("por_no");
            assertEquals(3, porNo.size());
            assertEquals(nos.get(0).rotulo(), porNo.get(0).get("no").asText());
            assertTrue(porNo.get(0).get("respondeu").asBoolean());
            assertEquals(3, porNo.get(0).get("numero_de_nos").asInt());
            assertEquals("cache-ausente:sessions-async", porNo.get(0).get("motivo").asText());
            assertTrue(porNo.get(0).get("anomalo").asBoolean());
        }
        // 3 GET por rodada (fora os 3 do aquecimento); uma rodada interrompida pelo fim da duracao pode ter pedido
        // e nao entregue
        int n = nos.stream().mapToInt(x -> ConsultaSaudeRestTest.autenticados(x).size()).sum() - 3;
        assertTrue(n >= 3 * amostras.size() && n <= 3 * (amostras.size() + 1),
                n + " GET para " + amostras.size() + " amostras");
        assertEquals(List.of(), erros);
        for (ServidorAdminFalso x : nos) {
            for (ServidorAdminFalso.Pedido p : x.pedidos()) {
                assertFalse(p.autorizacao() != null && p.autorizacao().contains("s3cr3t"), p.autorizacao());
            }
        }
        assertFalse(s.out().contains("s3cr3t") || s.err().contains("s3cr3t"));
    }
}
