package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.LeituraSaude;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T13, T14): adaptador REST da sonda de topologia. Desde o W-2b (P-5, achado N-7) a consulta e um
 * {@code GET} em memoria pelo {@link ClienteAdminRest} (Digest), sem processo {@code curl}; os testes T13 usam o
 * {@link ServidorAdminFalso} ({@code com.sun.net.httpserver}, so 127.0.0.1).
 */
class ConsultaSaudeRestTest {

    static final long MS = 1_000_000L;
    static final String USUARIO = "admin";
    static final String SENHA = "s3cr3t\"x";
    static final String ENDPOINT = "/rest/v2/cache-managers/default/health";
    static final String NONCE = "n0nc3";

    final List<AutoCloseable> fechar = new ArrayList<>();

    @AfterEach
    void fecharTudo() throws Exception {
        for (AutoCloseable c : fechar) {
            c.close();
        }
    }

    static String capturaReal(String nome) {
        try (InputStream in = ConsultaSaudeRestTest.class.getResourceAsStream("/captura-real/" + nome)) {
            assertNotNull(in, nome);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String md5(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Endpoint de saude falso com Digest (MD5, {@code qop=auth}, como o servidor 15.0.21 na tarefa zero do W-2a):
     * sem {@code Authorization} Digest responde 401 com o desafio; com resumo certo para {@code usuario}/{@code senha},
     * devolve {@code resposta}; resumo errado vai a {@code erros}.
     */
    static ServidorAdminFalso.Tratador saudeComDigest(String usuario, String senha, ServidorAdminFalso.Resp resposta,
                                                      List<String> erros) {
        return (srv, p) -> {
            String h = p.autorizacao();
            if (h == null || !h.startsWith("Digest ")) {
                return new ServidorAdminFalso.Resp(401, null, Map.of("WWW-Authenticate",
                        "Digest realm=\"default\", domain=\"/\", nonce=\"" + NONCE + "\", opaque=\"00ff\", algorithm=MD5,"
                                + " qop=\"auth\""), 0);
            }
            Map<String, String> f = ClienteAdminRest.campos(h.substring("Digest ".length()));
            String esperado = md5(md5(usuario + ":default:" + senha) + ":" + NONCE + ":" + f.get("nc") + ":"
                    + f.get("cnonce") + ":auth:" + md5(p.metodo() + ":" + f.get("uri")));
            if (!esperado.equals(f.get("response")) || !usuario.equals(f.get("username"))
                    || !p.caminho().equals(f.get("uri"))) {
                erros.add("resumo invalido: " + h);
                return ServidorAdminFalso.Resp.de(401, null);
            }
            if (!p.metodo().equals("GET") || !p.caminho().equals(ENDPOINT)) {
                return ServidorAdminFalso.Resp.de(404, "sem rota: " + p);
            }
            return resposta;
        };
    }

    ServidorAdminFalso servidor(ServidorAdminFalso.Resp resposta, List<String> erros) throws IOException {
        ServidorAdminFalso s = new ServidorAdminFalso(saudeComDigest(USUARIO, SENHA, resposta, erros));
        fechar.add(s);
        return s;
    }

    ServidorAdminFalso servidor(String corpo, List<String> erros) throws IOException {
        return servidor(ServidorAdminFalso.Resp.de(200, corpo), erros);
    }

    private static ConsultaSaudeRest adaptador(ServidorAdminFalso... nos) {
        List<String> urls = new ArrayList<>();
        for (ServidorAdminFalso n : nos) {
            urls.add(n.url());
        }
        return new ConsultaSaudeRest(urls, USUARIO, SENHA);
    }

    static List<ServidorAdminFalso.Pedido> autenticados(ServidorAdminFalso s) {
        return s.pedidos().stream().filter(p -> p.autorizacao() != null).toList();
    }

    // ---------------------------------------------------------------- T13 adaptador

    @Test
    @DisplayName("T13 (W-2b): GET no endpoint de saude com Digest; a senha nunca vai em nenhum pedido; nenhum processo")
    void pedidoDigestSemSenha() throws Exception {
        List<String> erros = Collections.synchronizedList(new ArrayList<>());
        ServidorAdminFalso s = servidor(capturaReal("rep3-isn3-healthy-3nos.json"), erros);
        ConsultaSaudeRest c = adaptador(s);
        c.consultar(0, 1500 * MS);
        c.consultar(0, 1500 * MS);
        assertEquals(List.of(), erros);
        List<ServidorAdminFalso.Pedido> l = s.pedidos();
        assertEquals(3, l.size(), "desafio + pedido + pedido com o desafio guardado: " + l);
        for (ServidorAdminFalso.Pedido p : l) {
            assertEquals("GET", p.metodo());
            assertEquals(ENDPOINT, p.caminho());
            String h = p.autorizacao();
            if (h != null) {
                assertTrue(h.startsWith("Digest "), h);
                assertFalse(h.contains(SENHA) || h.contains("s3cr3t"), "senha no cabecalho: " + h);
            }
        }
        assertEquals(2, autenticados(s).size());
        assertEquals(0, c.lancamentos(), "nenhum processo curl (P-5)");
        assertEquals(2, c.pedidos());
    }

    @Test
    @DisplayName("T13 (W-2b): URL do indice pedido; um pedido autenticado por consulta")
    void urlDoIndice() throws Exception {
        List<String> erros = Collections.synchronizedList(new ArrayList<>());
        String corpo = capturaReal("rep3-isn3-healthy-3nos.json");
        ServidorAdminFalso a = servidor(corpo, erros);
        ServidorAdminFalso b = servidor(corpo, erros);
        ConsultaSaudeRest c = adaptador(a, b);
        c.consultar(1, 1000 * MS);
        c.consultar(0, 1000 * MS);
        c.consultar(0, 2001 * MS);
        assertEquals(2, autenticados(a).size());
        assertEquals(1, autenticados(b).size());
        assertEquals(3, c.pedidos());
        assertEquals(List.of(), erros);
    }

    @Test
    @DisplayName("T13 (W-2b): resposta diferente de 2xx vira IOException com o codigo (motivo falha:IOException, como o exit do curl)")
    void codigoNaoSucesso() throws Exception {
        for (int codigo : new int[] {503, 404, 500}) {
            ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(codigo, "x"), new ArrayList<>());
            IOException e = assertThrows(IOException.class, () -> adaptador(s).consultar(0, 1500 * MS));
            assertEquals(IOException.class, e.getClass(), "classe exata (motivo da sonda)");
            assertTrue(e.getMessage().contains(Integer.toString(codigo)), e.getMessage());
        }
    }

    @Test
    @DisplayName("T13 (W-2b): no que recusa a conexao vira IOException (classe exata: motivo falha:IOException, como no I-3b)")
    void semConexao() throws Exception {
        ServidorAdminFalso s = servidor(capturaReal("rep3-isn3-healthy-3nos.json"), new ArrayList<>());
        ConsultaSaudeRest c = adaptador(s);
        s.parar();
        IOException e = assertThrows(IOException.class, () -> c.consultar(0, 1500 * MS));
        // SondaTopologia.motivoDaFalha usa o nome simples da classe: falha:IOException
        assertEquals(IOException.class, e.getClass(), "classe exata: " + e);
    }

    @Test
    @DisplayName("T13 (W-2b): no que nao responde no prazo: TimeoutException dentro do prazo (a sonda grava timeout)")
    void servidorLento() throws Exception {
        ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(200, "{}").atrasada(5_000), new ArrayList<>());
        long t0 = System.nanoTime();
        assertThrows(TimeoutException.class, () -> adaptador(s).consultar(0, 100 * MS));
        long decorrido = (System.nanoTime() - t0) / MS;
        assertTrue(decorrido < 1000, "consulta levou " + decorrido + " ms com prazo de 100 ms");
    }

    @Test
    @DisplayName("T13 (W-2b): interrupcao durante a espera propaga InterruptedException")
    void interrupcao() throws Exception {
        ServidorAdminFalso s = servidor(ServidorAdminFalso.Resp.de(200, "{}").atrasada(5_000), new ArrayList<>());
        ConsultaSaudeRest c = adaptador(s);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                c.consultar(0, 5_000 * MS);
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        t.start();
        long limite = System.nanoTime() + 2_000 * MS;
        while (autenticados(s).isEmpty() && System.nanoTime() < limite) {
            Thread.sleep(5);
        }
        assertFalse(autenticados(s).isEmpty(), "o pedido autenticado nao chegou");
        t.interrupt();
        t.join(1000);
        assertFalse(t.isAlive());
        assertTrue(erro.get() instanceof InterruptedException, String.valueOf(erro.get()));
    }

    @Test
    @DisplayName("T13: corpo da captura real vira a mesma LeituraSaude de HealthRest.interpretar")
    void corpoReal() throws Exception {
        for (String nome : List.of("rep3-isn3-healthy-3nos.json", "rep3-isn3-degraded-1no.json",
                "rep2-isn1-healthy-rebalancing-3nos.json")) {
            String corpo = capturaReal(nome);
            ServidorAdminFalso s = servidor(corpo, new ArrayList<>());
            HealthRest.Estado e = HealthRest.interpretar(corpo);
            assertEquals(new LeituraSaude(e.numeroDeNos(), e.statusPorCache()), adaptador(s).consultar(0, 1500 * MS),
                    nome);
        }
    }

    @Test
    @DisplayName("T13: corpo que nao e JSON de saude vira IllegalArgumentException (falha de consulta)")
    void corpoInvalido() throws Exception {
        ServidorAdminFalso s = servidor("<html>401</html>", new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> adaptador(s).consultar(0, 1500 * MS));
    }

    @Test
    @DisplayName("nos() e rotulo(i) vem das URLs, na ordem")
    void nosERotulos() {
        ConsultaSaudeRest c = new ConsultaSaudeRest(ConsultaSaudeRest.urlsDe("a:1,b:2,c:3"), USUARIO, SENHA);
        assertEquals(3, c.nos());
        assertEquals(List.of("a:1", "b:2", "c:3"), List.of(c.rotulo(0), c.rotulo(1), c.rotulo(2)));
    }

    // ---------------------------------------------------------------- T14 urlsDe

    @Test
    @DisplayName("T14: o padrao de --servers vira tres URLs http na mesma ordem")
    void urlsDoPadraoDaCli() {
        assertEquals(List.of("http://127.0.0.1:11222", "http://127.0.0.1:11223", "http://127.0.0.1:11224"),
                ConsultaSaudeRest.urlsDe("127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224"));
        assertEquals(List.of("http://127.0.0.1:12264", "http://127.0.0.1:12262"),
                ConsultaSaudeRest.urlsDe("127.0.0.1:12264,127.0.0.1:12262"));
    }

    @Test
    @DisplayName("T14: espacos nas pontas sao aceitos")
    void espacos() {
        assertEquals(List.of("http://h:1", "http://g:2"), ConsultaSaudeRest.urlsDe(" h:1 , g:2 "));
    }

    @Test
    @DisplayName("T14: item sem porta, porta invalida ou lista vazia: IllegalArgumentException citando o item")
    void invalidos() {
        for (String[] caso : new String[][] {{"h", "'h'"}, {"h:x", "'h:x'"}, {"h:0", "'h:0'"},
                {"h:70000", "'h:70000'"}, {"", "''"}, {"a:1,,b:2", "''"}, {":80", "':80'"}}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ConsultaSaudeRest.urlsDe(caso[0]), caso[0]);
            assertTrue(e.getMessage().contains(caso[1]), "'" + caso[0] + "' -> " + e.getMessage());
        }
        assertEquals(List.of("http://h:65535", "http://h:1"), ConsultaSaudeRest.urlsDe("h:65535,h:1"));
    }
}
