package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-IC (06/11): o adaptador do AC-7 ({@link AtuadorIgnorarCache}) contra tres servidores falsos que imitam o que o A4
 * mediu no Infinispan 15.0.21 ({@code docs/relatorio-a4-2026-11-06.md}, secoes 1, 3, 5 e 6):
 *
 * <ul>
 *   <li>cada no tem a sua lista de caches ignorados em memoria; o POST/DELETE muda a lista do no que recebeu o pedido
 *       na hora, e grava a lista INTEIRA para todos ("replicacao"), salvo se o no estiver isolado;</li>
 *   <li>isolado, o pedido responde 500 so depois de um atraso (o {@code remote-timeout} de 15 s do cache de estado; aqui,
 *       {@value #ATRASO_ISOLADO_MS} ms), e a lista dos outros nos nao muda;</li>
 *   <li>{@code ?action=get-availability} devolve o modo do no ({@code AVAILABLE} ou {@code DEGRADED_MODE}).</li>
 * </ul>
 *
 * <p>Regras conferidas (secao 8 do relatorio): R1 um cache por pedido; R2 um pedido por vez (o DELETE espera a resposta
 * do POST pendente); R3 efeito confirmado pela lista do alvo, nao pelo HTTP (o 500 sob corte e sucesso); R4 guarda de
 * falso alarme (a lista da maioria mostrou o cache: desfaz na hora e devolve FALHOU); R5 nenhum pedido a
 * {@code /rest/v2/container}; R6 desfazer no alvo quando ele esta em {@code DEGRADED_MODE}; R7 desfazer por um no da
 * maioria, que zera a lista do alvo; R8 reverter idempotente.</p>
 */
class AtuadorIgnorarCacheTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;
    static final long ATRASO_ISOLADO_MS = 400;
    static final String CACHE = "sessions-async";
    static final String IGN = "/rest/v2/server/ignored-caches";

    /** Tres nos falsos com listas de ignorados e particao simulada. */
    final class Cluster implements AutoCloseable {
        final List<ServidorAdminFalso> nos = new ArrayList<>();
        final List<Set<String>> listas = new ArrayList<>();
        final boolean[] isolado = new boolean[3];
        final String[] disponibilidade = {"AVAILABLE", "AVAILABLE", "AVAILABLE"};
        /** Inicio e fim (nanoTime) de cada pedido que muda lista, na ordem de chegada. */
        final List<long[]> mudancas = Collections.synchronizedList(new ArrayList<>());

        Cluster() throws Exception {
            for (int i = 0; i < 3; i++) {
                listas.add(Collections.synchronizedSet(new TreeSet<>()));
                final int k = i;
                nos.add(new ServidorAdminFalso((srv, p) -> tratar(k, p)));
            }
        }

        ServidorAdminFalso.Resp tratar(int k, ServidorAdminFalso.Pedido p) throws Exception {
            String c = p.caminho();
            if (p.metodo().equals("GET") && c.equals(IGN)) {
                synchronized (this) {
                    return ServidorAdminFalso.Resp.de(200, json(listas.get(k)));
                }
            }
            if (p.metodo().equals("GET") && c.equals("/rest/v2/caches/" + CACHE + "?action=get-availability")) {
                return ServidorAdminFalso.Resp.de(200, disponibilidade[k]);
            }
            if ((p.metodo().equals("POST") || p.metodo().equals("DELETE")) && c.startsWith(IGN + "/")) {
                long ini = System.nanoTime();
                String cache = c.substring(IGN.length() + 1);
                synchronized (this) {
                    if (p.metodo().equals("POST")) {
                        listas.get(k).add(cache);
                    } else {
                        listas.get(k).remove(cache);
                    }
                    if (!isolado[k]) {
                        for (int i = 0; i < 3; i++) {
                            if (i != k && !isolado[i]) {
                                listas.get(i).clear();
                                listas.get(i).addAll(listas.get(k));
                            }
                        }
                    }
                }
                if (isolado[k]) {
                    Thread.sleep(ATRASO_ISOLADO_MS);
                    mudancas.add(new long[] {ini, System.nanoTime()});
                    return ServidorAdminFalso.Resp.de(500,
                            "ISPN000476: Timed out waiting for responses for request 1 from a,b after 15 seconds");
                }
                mudancas.add(new long[] {ini, System.nanoTime()});
                return ServidorAdminFalso.Resp.de(204, null);
            }
            return ServidorAdminFalso.Resp.de(404, "sem rota: " + p);
        }

        String json(Set<String> s) {
            return s.stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(",", "[", "]"));
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

        Set<String> lista(int i) {
            synchronized (this) {
                return Set.copyOf(listas.get(i));
            }
        }

        List<ServidorAdminFalso.Pedido> todosPedidos() {
            List<ServidorAdminFalso.Pedido> l = new ArrayList<>();
            nos.forEach(n -> l.addAll(n.pedidos()));
            return l;
        }

        List<String> mutacoes(int i) {
            return nos.get(i).pedidos().stream().filter(p -> !p.metodo().equals("GET"))
                    .map(Object::toString).toList();
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

    /** Guarda de 300 ms, leitura a cada 10 ms, espera do ouvinte de 200 ms (valores de producao: 1 s, 20 ms, 1 s). */
    static final AtuadorIgnorarCache.Parametros RAPIDOS = new AtuadorIgnorarCache.Parametros(300 * MS, 10 * MS,
            1 * S, 300 * MS, 200 * MS);

    AtuadorIgnorarCache atuador(Cluster c, Set<String> responderam) {
        return new AtuadorIgnorarCache(c.urls(), new ClienteAdminRest("admin", "senha"), () -> responderam, RAPIDOS);
    }

    static AcaoCluster ac7(String alvo) {
        return new AcaoCluster(AcaoCluster.Tipo.AC_7, List.of(CACHE), alvo, null);
    }

    /** R5: nenhum pedido a /rest/v2/container (no isolado ele prende a thread da REST, P-A4.3). */
    static void semContainer(Cluster c) {
        for (ServidorAdminFalso.Pedido p : c.todosPedidos()) {
            assertFalse(p.caminho().startsWith("/rest/v2/container"), "R5: " + p);
        }
    }

    /** R2: os pedidos que mudam lista nunca se sobrepoem no tempo. */
    static void semSobreposicao(Cluster c) {
        List<long[]> m;
        synchronized (c.mudancas) {
            m = new ArrayList<>(c.mudancas);
        }
        m.sort((a, b) -> Long.compare(a[0], b[0]));
        for (int i = 1; i < m.size(); i++) {
            assertTrue(m.get(i)[0] >= m.get(i - 1)[1], "R2: pedido " + i + " comecou antes de o anterior responder");
        }
    }

    // ------------------------------------------------------------------ corte verdadeiro

    @Test
    @DisplayName("corte: POST so ao alvo, efeito confirmado pela lista em ms, guarda limpa -> APLICADA com o POST pendente")
    void corteAplica() throws Exception {
        Cluster c = cluster();
        c.isolado[2] = true;
        AtuadorIgnorarCache a = atuador(c, c.todos());
        long t0 = System.nanoTime();
        ResultadoAtuacao r = a.aplicar(ac7(c.rotulo(2)), 17 * S);
        long dur = System.nanoTime() - t0;
        assertEquals(ResultadoAtuacao.Estado.APLICADA, r.estado(), r.toString());
        assertTrue(dur < ATRASO_ISOLADO_MS * MS, "nao espera o 500 do POST (R3): " + dur / MS + " ms");
        assertNull(r.codigoHttp(), "o POST ainda nao respondeu");
        assertTrue(r.lidoDeVolta().contains("post=pendente"), r.lidoDeVolta());
        assertTrue(r.lidoDeVolta().contains("efeito_ms="), r.lidoDeVolta());
        assertTrue(r.lidoDeVolta().contains("guarda=limpa"), r.lidoDeVolta());
        assertEquals(Set.of(CACHE), c.lista(2));
        assertEquals(Set.of(), c.lista(0));
        assertEquals(Set.of(), c.lista(1));
        assertEquals(List.of("POST " + IGN + "/" + CACHE), c.mutacoes(2));
        assertEquals(List.of(), c.mutacoes(0));
        semContainer(c);
    }

    @Test
    @DisplayName("R2 e R6: reverter logo depois espera a resposta do POST pendente; alvo DEGRADED -> DELETE no proprio alvo")
    void desfazerNoAlvoSozinho() throws Exception {
        Cluster c = cluster();
        c.isolado[2] = true;
        AtuadorIgnorarCache a = atuador(c, c.todos());
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.aplicar(ac7(c.rotulo(2)), 17 * S).estado());
        c.disponibilidade[2] = "DEGRADED_MODE"; // o alvo se viu sozinho
        ResultadoAtuacao v = a.reverter(ac7(c.rotulo(2)), 17 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v.estado(), v.toString());
        assertTrue(v.lidoDeVolta().contains("rota=alvo-sozinho"), v.lidoDeVolta());
        assertTrue(v.lidoDeVolta().contains("anterior=POST:500"), "o resultado do POST pendente vai a trilha: "
                + v.lidoDeVolta());
        assertEquals(Set.of(), c.lista(2));
        assertEquals(List.of("POST " + IGN + "/" + CACHE, "DELETE " + IGN + "/" + CACHE), c.mutacoes(2));
        assertEquals(List.of(), c.mutacoes(0), "nada na maioria");
        assertEquals(List.of(), c.mutacoes(1));
        semSobreposicao(c);
        semContainer(c);
    }

    @Test
    @DisplayName("R7: depois da fusao (alvo AVAILABLE, so a lista dele com o cache) -> um DELETE a um no da maioria zera a lista do alvo")
    void desfazerPelaMaioria() throws Exception {
        Cluster c = cluster();
        c.isolado[2] = true;
        AtuadorIgnorarCache a = atuador(c, c.todos());
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.aplicar(ac7(c.rotulo(2)), 17 * S).estado());
        Thread.sleep(ATRASO_ISOLADO_MS + 100); // o POST respondeu 500
        c.isolado[2] = false; // fusao: a gravacao de um no da maioria volta a alcancar o alvo
        ResultadoAtuacao v = a.reverter(ac7(c.rotulo(2)), 17 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v.estado(), v.toString());
        assertTrue(v.lidoDeVolta().contains("rota=maioria@" + c.rotulo(0)), v.lidoDeVolta());
        assertEquals(List.of("DELETE " + IGN + "/" + CACHE), c.mutacoes(0));
        assertEquals(List.of("POST " + IGN + "/" + CACHE), c.mutacoes(2), "nenhum DELETE no alvo");
        assertEquals(Set.of(), c.lista(2));
        semContainer(c);
    }

    @Test
    @DisplayName("R7 sob corte ainda nao detectado (R4/encerramento): o DELETE da maioria nao alcanca o alvo, entao vai o DELETE ao alvo, em sequencia")
    void desfazerSobCorte() throws Exception {
        Cluster c = cluster();
        c.isolado[2] = true;
        AtuadorIgnorarCache a = atuador(c, c.todos());
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.aplicar(ac7(c.rotulo(2)), 17 * S).estado());
        ResultadoAtuacao v = a.reverter(ac7(c.rotulo(2)), 17 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v.estado(), v.toString());
        assertTrue(v.lidoDeVolta().contains("rota=maioria@" + c.rotulo(0) + "+alvo"), v.lidoDeVolta());
        assertEquals(Set.of(), c.lista(2), "o alvo volta a servir");
        semSobreposicao(c);
        semContainer(c);
    }

    // ------------------------------------------------------------------ falso alarme

    @Test
    @DisplayName("R4 guarda: sem corte, a lista da maioria mostra o cache -> DELETE na hora, listas vazias, FALHOU falso-alarme-desfeito")
    void guardaDeFalsoAlarme() throws Exception {
        Cluster c = cluster();
        AtuadorIgnorarCache a = atuador(c, c.todos());
        long t0 = System.nanoTime();
        ResultadoAtuacao r = a.aplicar(ac7(c.rotulo(2)), 17 * S);
        long dur = System.nanoTime() - t0;
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado(), r.toString());
        assertEquals("falso-alarme-desfeito", r.erro());
        assertEquals(204, r.codigoHttp());
        assertTrue(r.lidoDeVolta().contains("guarda=falso-alarme@"), r.lidoDeVolta());
        assertTrue(dur < 2 * S, "desfaz dentro da janela da guarda, nao no fim do prazo: " + dur / MS + " ms");
        for (int i = 0; i < 3; i++) {
            assertEquals(Set.of(), c.lista(i), "no " + i);
        }
        // o executor chama reverter depois de um FALHOU: idempotente, sem pedido nenhum
        int antes = c.todosPedidos().stream().filter(p -> !p.metodo().equals("GET")).toList().size();
        ResultadoAtuacao v = a.reverter(ac7(c.rotulo(2)), 17 * S);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, v.estado(), v.toString());
        assertTrue(v.lidoDeVolta().startsWith("nada-a-desfazer"), v.lidoDeVolta());
        assertEquals(antes, c.todosPedidos().stream().filter(p -> !p.metodo().equals("GET")).toList().size());
        semSobreposicao(c);
        semContainer(c);
    }

    // ------------------------------------------------------------------ recusas

    @Test
    @DisplayName("alvo que nao respondeu na ultima janela, alvo desconhecido ou outro tipo: FALHOU sem nenhum pedido")
    void recusas() throws Exception {
        Cluster c = cluster();
        Set<String> semAlvo = new LinkedHashSet<>(c.todos());
        semAlvo.remove(c.rotulo(2));
        AtuadorIgnorarCache a = atuador(c, semAlvo);
        ResultadoAtuacao r = a.aplicar(ac7(c.rotulo(2)), 17 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertTrue(r.erro().startsWith("alvo-sem-resposta-na-ultima-janela"), r.erro());
        assertEquals(ResultadoAtuacao.Estado.FALHOU, atuador(c, c.todos()).aplicar(ac7("1.2.3.4:5"), 17 * S).estado());
        ResultadoAtuacao t = atuador(c, c.todos()).aplicar(
                new AcaoCluster(AcaoCluster.Tipo.AC_5, List.of(CACHE), c.rotulo(2), null), 17 * S);
        assertEquals("tipo-sem-rota:AC-5", t.erro());
        assertTrue(c.todosPedidos().isEmpty(), c.todosPedidos().toString());
        assertEquals("rest-ignored-caches", a.nome());
    }

    @Test
    @DisplayName("efeito nao visto no alvo dentro do prazo do efeito -> FALHOU efeito-nao-confirmado")
    void efeitoNaoConfirmado() throws Exception {
        Cluster c = cluster();
        c.nos.get(2).tratador = (srv, p) -> p.metodo().equals("GET") && p.caminho().equals(IGN)
                ? ServidorAdminFalso.Resp.de(200, "[]") : ServidorAdminFalso.Resp.de(204, null);
        c.isolado[2] = true;
        AtuadorIgnorarCache a = atuador(c, c.todos());
        ResultadoAtuacao r = a.aplicar(ac7(c.rotulo(2)), 17 * S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado(), r.toString());
        assertEquals("efeito-nao-confirmado", r.erro());
    }

    @Test
    @DisplayName("parametros de producao: guarda de 1 s (o maior tempo de replicacao medido no A4 foi 157 ms), leitura a cada 20 ms")
    void parametrosDeProducao() {
        assertEquals(1 * S, AtuadorIgnorarCache.Parametros.PADRAO.janelaGuardaNs());
        assertEquals(20 * MS, AtuadorIgnorarCache.Parametros.PADRAO.passoNs());
        assertTrue(AtuadorIgnorarCache.Parametros.PADRAO.prazoEfeitoNs() <= AcaoCluster.Tipo.AC_7.prazoNs(2 * S));
    }
}
