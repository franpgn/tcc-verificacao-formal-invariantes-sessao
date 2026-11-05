package br.unipampa.tcc.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2b (decisao 3 do coordenador de 05/11, P-45): a sonda do laco de convergencia ({@code HealthRest.consultar}) deixa
 * de criar um processo {@code curl} por rodada e passa a usar o {@link ClienteAdminRest} em memoria (Digest). O que o
 * laco ve nao muda: o {@link HealthRest.Estado} do corpo, e qualquer falha (rede, codigo fora de 2xx, prazo) como
 * {@link IOException}, que o laco grava como rodada invalida ("sonda de cluster falhou: ..."); corpo invalido continua
 * {@link IllegalArgumentException}. O prazo e o {@code --max-time 5} do curl antigo.
 */
class HealthRestHttpTest {

    static final long MS = 1_000_000L;
    static final String USUARIO = "admin";
    static final String SENHA = "s3cr3t\"x";

    final List<AutoCloseable> fechar = new ArrayList<>();

    @AfterEach
    void fecharTudo() throws Exception {
        for (AutoCloseable c : fechar) {
            c.close();
        }
    }

    ServidorAdminFalso servidor(ServidorAdminFalso.Resp r, List<String> erros) throws IOException {
        ServidorAdminFalso s = new ServidorAdminFalso(ConsultaSaudeRestTest.saudeComDigest(USUARIO, SENHA, r, erros));
        fechar.add(s);
        return s;
    }

    static ClienteAdminRest cliente() {
        return new ClienteAdminRest(USUARIO, SENHA);
    }

    @Test
    @DisplayName("P-45: GET com Digest no endpoint de saude; o Estado e o de HealthRest.interpretar; senha em nenhum cabecalho")
    void consultaDigest() throws Exception {
        List<String> erros = Collections.synchronizedList(new ArrayList<>());
        for (String nome : List.of("rep3-isn3-healthy-3nos.json", "rep3-isn3-degraded-1no.json")) {
            String corpo = ConsultaSaudeRestTest.capturaReal(nome);
            ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(200, corpo), erros);
            assertEquals(HealthRest.interpretar(corpo), HealthRest.consultar(cliente(), s.url(), 2_000 * MS), nome);
            for (ServidorAdminFalso.Pedido p : s.pedidos()) {
                assertEquals(ConsultaSaudeRestTest.ENDPOINT, p.caminho());
                assertFalse(p.autorizacao() != null && p.autorizacao().contains("s3cr3t"), p.autorizacao());
            }
            assertEquals(1, ConsultaSaudeRestTest.autenticados(s).size());
        }
        assertEquals(List.of(), erros);
    }

    @Test
    @DisplayName("P-45: codigo fora de 2xx vira IOException com o codigo (como o exit do curl --fail)")
    void codigoNaoSucesso() throws Exception {
        ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(503, "x"), new ArrayList<>());
        IOException e = assertThrows(IOException.class, () -> HealthRest.consultar(cliente(), s.url(), 2_000 * MS));
        assertTrue(e.getMessage().contains("503"), e.getMessage());
    }

    @Test
    @DisplayName("P-45: conexao recusada vira IOException")
    void semConexao() throws Exception {
        ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(200, "{}"), new ArrayList<>());
        String url = s.url();
        s.parar();
        assertThrows(IOException.class, () -> HealthRest.consultar(cliente(), url, 2_000 * MS));
    }

    @Test
    @DisplayName("P-45: prazo estourado vira IOException (nao TimeoutException: o laco grava a rodada invalida como antes), dentro do prazo")
    void prazo() throws Exception {
        ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(200, "{}").atrasada(5_000), new ArrayList<>());
        long t0 = System.nanoTime();
        IOException e = assertThrows(IOException.class, () -> HealthRest.consultar(cliente(), s.url(), 100 * MS));
        long ms = (System.nanoTime() - t0) / MS;
        assertTrue(ms < 1_000, "consulta levou " + ms + " ms com prazo de 100 ms");
        assertTrue(e.getMessage().contains("prazo"), e.getMessage());
    }

    @Test
    @DisplayName("P-45: corpo que nao e JSON de saude continua IllegalArgumentException")
    void corpoInvalido() throws Exception {
        ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(200, "<html>401</html>"), new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> HealthRest.consultar(cliente(), s.url(), 2_000 * MS));
    }

    @Test
    @DisplayName("P-45: o prazo da sonda da convergencia e o --max-time 5 do curl antigo (5 s, sem limite novo)")
    void prazoDaConvergencia() {
        assertEquals(5_000 * MS, HealthRest.PRAZO_CONSULTA_NS);
    }
}
