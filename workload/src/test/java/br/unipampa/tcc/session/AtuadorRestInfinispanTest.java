package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.Decisao;
import br.unipampa.tcc.controle.ExecutorDeAcoes;
import br.unipampa.tcc.controle.ModoAtuacao;
import br.unipampa.tcc.controle.RegistroAcao;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), itens 5 e 7: o atuador REST contra tres servidores falsos que imitam o que o B-44.1/B-44.1b
 * mediram (204 nos POST, {@code rebalancing_enabled} no corpo dos GET). Sucesso, 4xx, 5xx, prazo num no
 * lento, leitura de volta divergente (com o executor tentando a reversao), desafio Digest e Basic, AC-5 com
 * alvo que continua respondendo, shadow sem nenhum pedido. Mutantes que estes testes matam: "AC-2 por cache
 * em vez de global" (P-34), "chamada em shadow", "AC-5 mandada a no sem resposta".
 */
class AtuadorRestInfinispanTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;
    static final List<String> CACHES = List.of("sessions", "counters");
    static final AcaoCluster AC2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, CACHES, null, null);

    /** Tres nos falsos com um estado de rebalanceamento GLOBAL compartilhado (como o cluster real). */
    final class Cluster implements AutoCloseable {
        final AtomicBoolean rebalanceamento = new AtomicBoolean(true);
        final List<ServidorAdminFalso> nos = new ArrayList<>();

        Cluster() throws Exception {
            for (int i = 0; i < 3; i++) {
                nos.add(new ServidorAdminFalso(this::normal));
            }
        }

        ServidorAdminFalso.Resp normal(ServidorAdminFalso srv, ServidorAdminFalso.Pedido p) {
            String c = p.caminho();
            if (p.metodo().equals("POST") && c.equals("/rest/v2/container?action=disable-rebalancing")) {
                rebalanceamento.set(false);
                return ServidorAdminFalso.Resp.de(204, null);
            }
            if (p.metodo().equals("POST") && c.equals("/rest/v2/container?action=enable-rebalancing")) {
                rebalanceamento.set(true);
                return ServidorAdminFalso.Resp.de(204, null);
            }
            if (p.metodo().equals("GET") && (c.equals("/rest/v2/container") || c.startsWith("/rest/v2/caches/"))
                    && !c.contains("?")) {
                return ServidorAdminFalso.Resp.de(200, "{\"name\":\"x\",\"rebalancing_enabled\":"
                        + rebalanceamento.get() + "}");
            }
            if (p.metodo().equals("GET") && c.equals("/rest/v2/container/health/status")) {
                return ServidorAdminFalso.Resp.de(200, "HEALTHY");
            }
            if (p.metodo().equals("POST") && c.equals("/rest/v2/server/?action=stop")) {
                srv.pararDepoisDe(300);
                return ServidorAdminFalso.Resp.de(204, null);
            }
            return ServidorAdminFalso.Resp.de(404, "sem rota: " + p);
        }

        List<String> urls() {
            return nos.stream().map(ServidorAdminFalso::url).toList();
        }

        String rotulo(int i) {
            return nos.get(i).rotulo();
        }

        Set<String> todos() {
            return nos.stream().map(ServidorAdminFalso::rotulo).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        List<ServidorAdminFalso.Pedido> pedidos(int i) {
            return nos.get(i).pedidos();
        }

        List<ServidorAdminFalso.Pedido> todosPedidos() {
            List<ServidorAdminFalso.Pedido> l = new ArrayList<>();
            nos.forEach(n -> l.addAll(n.pedidos()));
            return l;
        }

        @Override
        public void close() {
            nos.forEach(ServidorAdminFalso::close);
        }
    }

    final List<AutoCloseable> fechar = new ArrayList<>();

    @AfterEach
    void fecharTudo() throws Exception {
        for (AutoCloseable c : fechar) {
            c.close();
        }
    }

    Cluster cluster() throws Exception {
        Cluster c = new Cluster();
        fechar.add(c);
        return c;
    }

    AtuadorRestInfinispan atuador(Cluster c, Set<String> responderam) {
        return new AtuadorRestInfinispan(c.urls(), new ClienteAdminRest("admin", "senha"), () -> responderam, 50 * MS);
    }

    static AcaoCluster ac5(String alvo) {
        return new AcaoCluster(AcaoCluster.Tipo.AC_5, CACHES, alvo, null);
    }

    /** Nenhum pedido de rebalanceamento por cache (P-34). */
    static void semRebalanceamentoPorCache(List<ServidorAdminFalso.Pedido> l) {
        for (ServidorAdminFalso.Pedido p : l) {
            assertFalse(p.caminho().startsWith("/rest/v2/caches/") && p.caminho().contains("rebalancing"),
                    "AC-2 por cache (P-34): " + p);
        }
    }

    // ------------------------------------------------------------------ AC-2

    @Test
    @DisplayName("AC-2: POST global num no que respondeu, leitura de volta nos 3 nos (container + 2 caches) -> APLICADA; reverter idem")
    void ac2Sucesso() throws Exception {
        Cluster c = cluster();
        AtuadorRestInfinispan a = atuador(c, c.todos());
        ResultadoAtuacao r = a.aplicar(AC2, 2 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, r.estado(), r.toString());
        assertEquals(204, r.codigoHttp());
        assertFalse(c.rebalanceamento.get());
        assertEquals(List.of("POST /rest/v2/container?action=disable-rebalancing"),
                c.pedidos(0).stream().filter(p -> p.metodo().equals("POST")).map(Object::toString).toList());
        for (int i = 0; i < 3; i++) {
            Set<String> gets = c.pedidos(i).stream().filter(p -> p.metodo().equals("GET"))
                    .map(ServidorAdminFalso.Pedido::caminho).collect(Collectors.toSet());
            assertEquals(Set.of("/rest/v2/container", "/rest/v2/caches/sessions", "/rest/v2/caches/counters"), gets,
                    "no " + i);
        }
        assertTrue(r.lidoDeVolta().contains("container=false"), r.lidoDeVolta());
        ResultadoAtuacao v = a.reverter(AC2, 2 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v.estado(), v.toString());
        assertTrue(c.rebalanceamento.get());
        ResultadoAtuacao v2 = a.reverter(AC2, 2 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v2.estado(), "reverter e idempotente");
        semRebalanceamentoPorCache(c.todosPedidos());
        assertEquals("rest-infinispan", a.nome());
    }

    @Test
    @DisplayName("AC-2: o POST vai ao primeiro no que respondeu na ultima janela, nunca a um que nao respondeu")
    void ac2SoNoQueRespondeu() throws Exception {
        Cluster c = cluster();
        AtuadorRestInfinispan a = atuador(c, Set.of(c.rotulo(1), c.rotulo(2)));
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.aplicar(AC2, 2 * S).estado());
        assertTrue(c.pedidos(0).stream().noneMatch(p -> p.metodo().equals("POST")));
        assertEquals(1, c.pedidos(1).stream().filter(p -> p.metodo().equals("POST")).count());
        AtuadorRestInfinispan nenhum = atuador(c, Set.of());
        ResultadoAtuacao r = nenhum.aplicar(AC2, 2 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertEquals("nenhum-no-respondeu-na-ultima-janela", r.erro());
    }

    @Test
    @DisplayName("AC-2: 4xx e 5xx no POST -> FALHOU com o codigo")
    void ac2Erros() throws Exception {
        for (int codigo : new int[] {403, 500}) {
            Cluster c = cluster();
            c.nos.get(0).tratador = (srv, p) -> p.metodo().equals("POST")
                    ? ServidorAdminFalso.Resp.de(codigo, "recusado") : c.normal(srv, p);
            ResultadoAtuacao r = atuador(c, c.todos()).aplicar(AC2, 2 * S);
            assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
            assertEquals(codigo, r.codigoHttp());
            assertTrue(c.rebalanceamento.get());
        }
    }

    @Test
    @DisplayName("AC-2: no lento na leitura de volta -> a chamada inteira termina no prazo; 2 nos bastam (maioria)")
    void ac2NoLento() throws Exception {
        Cluster c = cluster();
        c.nos.get(2).tratador = (srv, p) -> p.metodo().equals("GET")
                ? c.normal(srv, p).atrasada(5000) : c.normal(srv, p);
        AtuadorRestInfinispan a = atuador(c, c.todos());
        long t0 = System.nanoTime();
        ResultadoAtuacao r = a.aplicar(AC2, 1 * S);
        long dur = System.nanoTime() - t0;
        assertTrue(dur < 1500 * MS, "terminou no prazo: " + dur / MS + " ms");
        assertEquals(ResultadoAtuacao.Estado.APLICADA, r.estado(), r.toString());
        assertTrue(r.lidoDeVolta().contains(c.rotulo(2) + ":container=?"), r.lidoDeVolta());
    }

    @Test
    @DisplayName("AC-2: POST lento no primeiro no -> tenta o seguinte dentro do prazo")
    void ac2PostLento() throws Exception {
        Cluster c = cluster();
        c.nos.get(0).tratador = (srv, p) -> p.metodo().equals("POST")
                ? ServidorAdminFalso.Resp.de(204, null).atrasada(5000) : c.normal(srv, p);
        long t0 = System.nanoTime();
        ResultadoAtuacao r = atuador(c, c.todos()).aplicar(AC2, 2 * S);
        long dur = System.nanoTime() - t0;
        assertTrue(dur < 2500 * MS, "terminou no prazo: " + dur / MS + " ms");
        assertEquals(ResultadoAtuacao.Estado.APLICADA, r.estado(), r.toString());
        assertEquals(1, c.pedidos(1).stream().filter(p -> p.metodo().equals("POST")).count());
    }

    @Test
    @DisplayName("AC-2: menos de 2 nos na leitura de volta -> FALHOU")
    void ac2LeituraEmUmNo() throws Exception {
        Cluster c = cluster();
        c.nos.get(1).parar();
        c.nos.get(2).parar();
        ResultadoAtuacao r = atuador(c, Set.of(c.rotulo(0))).aplicar(AC2, 2 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertEquals("leitura-de-volta-em-1-nos", r.erro());
    }

    @Test
    @DisplayName("AC-2: leitura de volta divergente num no -> FALHOU, e o executor tenta a reversao (enable)")
    void ac2DivergenteComExecutor() throws Exception {
        Cluster c = cluster();
        c.nos.get(1).tratador = (srv, p) -> p.metodo().equals("GET") && p.caminho().equals("/rest/v2/container")
                ? ServidorAdminFalso.Resp.de(200, "{\"rebalancing_enabled\":true}") : c.normal(srv, p);
        List<RegistroAcao> trilha = Collections.synchronizedList(new ArrayList<>());
        ExecutorDeAcoes e = new ExecutorDeAcoes(ModoAtuacao.ACTIVE, atuador(c, c.todos()), 2 * S,
                System::nanoTime, trilha::add, new GanchoSemJvm());
        e.iniciar();
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(10 * S));
        assertTrue(e.encerrar());
        List<String> res = trilha.stream().map(x -> x.operacao() + ":" + x.resultado()).toList();
        assertEquals(List.of("APLICAR:FALHOU", "REVERTER:APLICADA"), res);
        assertEquals("leitura-de-volta-divergente", trilha.get(0).erro());
        assertTrue(c.rebalanceamento.get(), "reversao enviou enable-rebalancing");
        assertTrue(c.todosPedidos().stream().anyMatch(p -> p.caminho().equals("/rest/v2/container?action=enable-rebalancing")));
    }

    // ------------------------------------------------------------------ autenticacao

    static String md5(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Digest (RFC 7616, MD5, qop=auth): responde ao desafio com o resumo certo e reaproveita o nonce")
    void digest() throws Exception {
        Cluster c = cluster();
        List<String> erros = Collections.synchronizedList(new ArrayList<>());
        int[] desafios = {0};
        for (ServidorAdminFalso n : c.nos) {
            n.tratador = (srv, p) -> {
                String h = p.autorizacao();
                if (h == null || !h.startsWith("Digest ")) {
                    synchronized (desafios) {
                        desafios[0]++;
                    }
                    return new ServidorAdminFalso.Resp(401, null, Map.of("WWW-Authenticate",
                            "Digest realm=\"default\", domain=\"/\", nonce=\"n0nc3\", opaque=\"00ff\", algorithm=MD5, qop=\"auth\""), 0);
                }
                Map<String, String> f = ClienteAdminRest.campos(h.substring("Digest ".length()));
                String ha1 = md5("admin:default:senha");
                String ha2 = md5(p.metodo() + ":" + f.get("uri"));
                String esperado = md5(ha1 + ":n0nc3:" + f.get("nc") + ":" + f.get("cnonce") + ":auth:" + ha2);
                if (!f.get("uri").equals(p.caminho())) {
                    erros.add("uri " + f.get("uri") + " != " + p.caminho());
                }
                if (!esperado.equals(f.get("response")) || !"00ff".equals(f.get("opaque"))
                        || !"admin".equals(f.get("username"))) {
                    erros.add("resumo invalido: " + h);
                    return ServidorAdminFalso.Resp.de(401, null);
                }
                return c.normal(srv, p);
            };
        }
        AtuadorRestInfinispan a = atuador(c, c.todos());
        ResultadoAtuacao r = a.aplicar(AC2, 3 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, r.estado(), r + " " + erros);
        assertEquals(List.of(), erros);
        int depoisDoPrimeiro;
        synchronized (desafios) {
            depoisDoPrimeiro = desafios[0];
        }
        assertTrue(depoisDoPrimeiro >= 3 && depoisDoPrimeiro <= 7, "ao menos um desafio por no: " + depoisDoPrimeiro);
        ResultadoAtuacao v = a.reverter(AC2, 3 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v.estado(), v + " " + erros);
        synchronized (desafios) {
            assertEquals(depoisDoPrimeiro, desafios[0], "a segunda chamada reaproveita o nonce de cada no");
        }
        for (int i = 0; i < 3; i++) {
            List<String> ncs = c.pedidos(i).stream().map(ServidorAdminFalso.Pedido::autorizacao)
                    .filter(x -> x != null).map(x -> ClienteAdminRest.campos(x.substring(7)).get("nc")).toList();
            assertEquals(ncs.size(), new LinkedHashSet<>(ncs).size(), "nc nunca se repete no no " + i + ": " + ncs);
        }
    }

    @Test
    @DisplayName("Basic: se o desafio for Basic, manda usuario:senha em Base64")
    void basic() throws Exception {
        Cluster c = cluster();
        AtomicReference<String> visto = new AtomicReference<>();
        for (ServidorAdminFalso n : c.nos) {
            n.tratador = (srv, p) -> {
                if (p.autorizacao() == null) {
                    return new ServidorAdminFalso.Resp(401, null, Map.of("WWW-Authenticate", "Basic realm=\"default\""), 0);
                }
                visto.set(p.autorizacao());
                return c.normal(srv, p);
            };
        }
        assertEquals(ResultadoAtuacao.Estado.APLICADA, atuador(c, c.todos()).aplicar(AC2, 2 * S).estado());
        assertEquals("Basic YWRtaW46c2VuaGE=", visto.get());
    }

    // ------------------------------------------------------------------ AC-5

    @Test
    @DisplayName("AC-5: POST /rest/v2/server/?action=stop ao proprio alvo, 204, o alvo para e os outros seguem -> APLICADA")
    void ac5Sucesso() throws Exception {
        Cluster c = cluster();
        AtuadorRestInfinispan a = atuador(c, c.todos());
        long t0 = System.nanoTime();
        ResultadoAtuacao r = a.aplicar(ac5(c.rotulo(2)), 3 * S);
        long dur = System.nanoTime() - t0;
        assertEquals(ResultadoAtuacao.Estado.APLICADA, r.estado(), r.toString());
        assertEquals(204, r.codigoHttp());
        assertTrue(dur < 3500 * MS, "no prazo: " + dur / MS);
        assertTrue(r.lidoDeVolta().startsWith(c.rotulo(2) + "=parou"), r.lidoDeVolta());
        assertTrue(r.lidoDeVolta().contains(c.rotulo(0) + "=HEALTHY"), r.lidoDeVolta());
        assertEquals("POST /rest/v2/server/?action=stop", c.pedidos(2).get(0).toString());
        for (int i = 0; i < 2; i++) {
            assertTrue(c.pedidos(i).stream().noneMatch(p -> p.metodo().equals("POST")), "a ordem vai so ao alvo");
            assertTrue(c.pedidos(i).stream().allMatch(p -> p.autorizacao() == null), "saude e anonima");
        }
        assertTrue(c.todosPedidos().stream().noneMatch(p -> p.caminho().startsWith("/rest/v2/cluster")), "N-2");
        ResultadoAtuacao v = a.reverter(ac5(c.rotulo(2)), 3 * S);
        assertEquals(ResultadoAtuacao.Estado.NAO_EXECUTADA, v.estado());
        assertEquals("reversao-pelo-orquestrador", v.erro());
    }

    @Test
    @DisplayName("AC-5: alvo que continua respondendo depois do 204 -> FALHOU dentro do prazo do tipo")
    void ac5AlvoContinua() throws Exception {
        Cluster c = cluster();
        c.nos.get(2).tratador = (srv, p) -> p.caminho().equals("/rest/v2/server/?action=stop")
                ? ServidorAdminFalso.Resp.de(204, null) : c.normal(srv, p);
        long t0 = System.nanoTime();
        ResultadoAtuacao r = atuador(c, c.todos()).aplicar(ac5(c.rotulo(2)), 800 * MS);
        long dur = System.nanoTime() - t0;
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertEquals("alvo-continua-respondendo", r.erro());
        assertEquals(204, r.codigoHttp());
        assertTrue(dur < 1300 * MS, "no prazo: " + dur / MS);
    }

    @Test
    @DisplayName("AC-5: 403 (permissao LIFECYCLE) -> FALHOU com o codigo; nenhuma consulta de saude")
    void ac5Recusada() throws Exception {
        Cluster c = cluster();
        c.nos.get(2).tratador = (srv, p) -> p.metodo().equals("POST")
                ? ServidorAdminFalso.Resp.de(403, "sem LIFECYCLE") : c.normal(srv, p);
        ResultadoAtuacao r = atuador(c, c.todos()).aplicar(ac5(c.rotulo(2)), 2 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertEquals(403, r.codigoHttp());
        assertEquals(1, c.pedidos(2).size());
    }

    @Test
    @DisplayName("AC-5 a no sem resposta na ultima janela (mutante): FALHOU sem nenhum pedido")
    void ac5NoSemResposta() throws Exception {
        Cluster c = cluster();
        ResultadoAtuacao r = atuador(c, Set.of(c.rotulo(0), c.rotulo(1))).aplicar(ac5(c.rotulo(2)), 2 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertTrue(r.erro().startsWith("alvo-sem-resposta-na-ultima-janela"), r.erro());
        assertEquals(List.of(), c.todosPedidos());
        ResultadoAtuacao d = atuador(c, c.todos()).aplicar(ac5("isn9:1"), 2 * S);
        assertEquals("alvo-desconhecido:isn9:1", d.erro());
    }

    @Test
    @DisplayName("AC-5: outro no sem resposta depois da parada -> FALHOU (leitura de volta nos tres nos, P-29)")
    void ac5OutroSemResposta() throws Exception {
        Cluster c = cluster();
        c.nos.get(0).parar();
        ResultadoAtuacao r = atuador(c, c.todos()).aplicar(ac5(c.rotulo(2)), 3 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertEquals("outro-no-sem-resposta", r.erro());
        assertNotNull(r.lidoDeVolta());
    }

    // ------------------------------------------------------------------ shadow e tipos

    @Test
    @DisplayName("shadow: nenhum pedido HTTP chega a nenhum no (mutante: chamada em shadow)")
    void shadowSemPedido() throws Exception {
        Cluster c = cluster();
        for (ServidorAdminFalso n : c.nos) {
            n.tratador = (srv, p) -> {
                throw new AssertionError("pedido em shadow: " + p);
            };
        }
        List<RegistroAcao> trilha = Collections.synchronizedList(new ArrayList<>());
        ExecutorDeAcoes e = new ExecutorDeAcoes(ModoAtuacao.SHADOW, atuador(c, c.todos()), 2 * S,
                System::nanoTime, trilha::add, new GanchoSemJvm());
        e.iniciar();
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        e.aplicar(ac5(c.rotulo(2)), Decisao.Regra.R3, 10 * S);
        e.reverterTudo(RegistroAcao.Origem.R4, Decisao.Regra.R4, 15 * S);
        assertTrue(e.encerrar());
        assertEquals(List.of(), c.todosPedidos());
        assertEquals(3, trilha.size());
        assertTrue(trilha.stream().noneMatch(RegistroAcao::executada));
    }

    /** Gancho que nao toca na JVM. */
    static final class GanchoSemJvm implements ExecutorDeAcoes.Gancho {
        @Override
        public void registrar(Thread t) {
        }

        @Override
        public void remover(Thread t) {
        }
    }
}
