package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SondaHotrodTest {

    /** Duble em memoria com gancho de falha por operacao (padrao Armazem/FakeCaches). */
    static class AlvoFalso implements SondaHotrod.Alvo {
        final Map<String, Object> dados = new HashMap<>();
        final Map<String, RuntimeException> falhas = new HashMap<>();
        final List<String> chamadas = new ArrayList<>();

        private void antes(String op) {
            chamadas.add(op);
            RuntimeException f = falhas.get(op);
            if (f != null) {
                throw f;
            }
        }

        @Override public Object get(String chave) { antes("get"); return dados.get(chave); }
        @Override public void put(String chave) { antes("put"); dados.put(chave, "v"); }
        @Override public void remove(String chave) { antes("remove"); dados.remove(chave); }
        @Override public int amostra() { antes("entrySet"); return dados.size(); }
    }

    private static List<String> ciclo(SondaHotrod.Alvo alvo, String no) {
        AtomicLong parede = new AtomicLong(1_000_000);
        AtomicLong mono = new AtomicLong(0);
        List<String> saida = new ArrayList<>();
        SondaHotrod.executarCiclo(no, "sessions", alvo,
                () -> parede.addAndGet(10), () -> mono.addAndGet(5), saida::add);
        return saida;
    }

    @Test
    void cicloSemFalhaFazPutGetEntrySetRemoveNessaOrdemEmQuatroLinhasOk() {
        AlvoFalso alvo = new AlvoFalso();
        List<String> linhas = ciclo(alvo, "isn3");
        assertEquals(List.of("put", "get", "entrySet", "remove"), alvo.chamadas);
        assertEquals(4, linhas.size());
        for (String l : linhas) {
            assertTrue(l.contains(",isn3,sessions,"), l);
            assertTrue(l.contains(",OK,,,"), l);
        }
        assertTrue(alvo.dados.isEmpty(), "a chave de teste deve ser removida");
    }

    @Test
    void falhaEmUmaOperacaoNaoImpedeAsDemaisEViraLinhaDeErro() {
        AlvoFalso alvo = new AlvoFalso();
        alvo.falhas.put("put", new RuntimeException("recusado", new SocketTimeoutException("t")));
        List<String> linhas = ciclo(alvo, "isn3");
        assertEquals(List.of("put", "get", "entrySet", "remove"), alvo.chamadas);
        assertTrue(linhas.get(0).contains(",put,"), linhas.get(0));
        assertTrue(linhas.get(0).contains(
                ",ERRO,java.lang.RuntimeException,recusado,java.net.SocketTimeoutException: t"), linhas.get(0));
        assertTrue(linhas.get(1).contains(",OK,"));
    }

    @Test
    void duracaoVemDoRelogioMonotonicoETimestampDoDeParede() {
        List<String> linhas = ciclo(new AlvoFalso(), "isn1");
        String[] c = linhas.get(0).split(",");
        assertEquals("isn1", c[1]);
        assertEquals("put", c[3]);
        assertEquals("5", c[4], "mono avanca 5 por leitura; duracao = fim - inicio");
        assertEquals("1000010", c[0], "parede lida uma vez, antes da operacao");
    }

    @Test
    void errosDeTipoErrorNaoSaoEngolidos() {
        AlvoFalso quebrado = new AlvoFalso() {
            @Override public Object get(String chave) { throw new AssertionError("x"); }
        };
        assertThrows(AssertionError.class, () -> ciclo(quebrado, "isn3"));
    }

    @Test
    void falhaAoAbrirOClienteViraLinhaConnectEOCicloSeguinteTentaDeNovo() {
        List<String> saida = new ArrayList<>();
        AtomicLong r = new AtomicLong();
        int[] tentativas = {0};
        AlvoFalso alvo = new AlvoFalso();
        SondaHotrod.Abridor abridor = () -> {
            if (tentativas[0]++ == 0) {
                throw new IllegalStateException("sem conexao");
            }
            return alvo;
        };
        SondaHotrod.Sessao s = new SondaHotrod.Sessao("isn3", "sessions", abridor,
                () -> r.addAndGet(10), () -> r.addAndGet(5), saida::add);
        s.rodarUmCiclo();
        assertEquals(1, saida.size());
        assertTrue(saida.get(0).contains(",isn3,sessions,connect,"), saida.get(0));
        assertTrue(saida.get(0).contains(",ERRO,java.lang.IllegalStateException,sem conexao,"), saida.get(0));
        s.rodarUmCiclo();
        assertEquals(5, saida.size());
        assertTrue(saida.get(1).contains(",put,"));
    }

    @Test
    void chaveDeTesteTemPrefixoSondaENomeDoNo() {
        assertEquals("sonda-isn3", SondaHotrod.chaveDe("isn3"));
    }

    @Test
    void argumentosExigemNoNoFormatoRotuloHostPorta() {
        SondaHotrod.Args a = SondaHotrod.Args.parse(new String[]{"--no", "isn3=127.0.0.1:11224",
                "--caches", "sessions,counters", "--intervalo-s", "5", "--duracao-s", "100"});
        assertEquals("isn3", a.rotulo());
        assertEquals("127.0.0.1", a.host());
        assertEquals(11224, a.porta());
        assertEquals(List.of("sessions", "counters"), a.caches());
        assertEquals(5, a.intervaloS());
        assertEquals(100, a.duracaoS());
        assertThrows(IllegalArgumentException.class, () -> SondaHotrod.Args.parse(new String[]{}));
        assertThrows(IllegalArgumentException.class,
                () -> SondaHotrod.Args.parse(new String[]{"--no", "isn3=semporta"}));
        assertThrows(IllegalArgumentException.class,
                () -> SondaHotrod.Args.parse(new String[]{"--no", "isn3=h:1", "--desconhecido", "x"}));
    }

    @Test
    void valoresPadraoSaoSessionsACadaCincoSegundosComTimeoutsCurtos() {
        SondaHotrod.Args a = SondaHotrod.Args.parse(new String[]{"--no", "isn1=127.0.0.1:11222"});
        assertEquals(List.of("sessions"), a.caches());
        assertEquals(5, a.intervaloS());
        assertEquals(0, a.duracaoS(), "0 = ate ser interrompida");
        assertEquals(2000, SondaHotrod.TIMEOUT_MS);
        assertEquals(1, SondaHotrod.MAX_RETRIES);
    }

    @Test
    void configuracaoUsaBasicTimeoutsCurtosEUmUnicoServidor() {
        var c = SondaHotrod.configuracao("127.0.0.1", 11224, "admin", "x");
        assertEquals(ClientIntelligence.BASIC, c.clientIntelligence());
        assertEquals(2000, c.socketTimeout());
        assertEquals(2000, c.connectionTimeout());
        assertEquals(1, c.maxRetries());
        assertEquals(1, c.servers().size());
        assertEquals(11224, c.servers().get(0).port());
        assertTrue(c.marshaller() instanceof JavaSerializationMarshaller);
    }

    @Test
    void valorDeTesteEhDoTipoDoCacheParaNaoQuebrarAuditores() {
        assertTrue(SondaHotrod.valorDeTeste("sessions", "sonda-isn3") instanceof SessionState);
        assertTrue(SondaHotrod.valorDeTeste("counters", "sonda-isn3") instanceof IdentityState);
        assertThrows(IllegalArgumentException.class, () -> SondaHotrod.valorDeTeste("outro", "k"));
    }
    // ---- B-42.1: o padrao de --caches segue o perfil de --replication ----

    @Test
    void semCachesOPadraoSegueOPerfil() {
        SondaHotrod.Args s = SondaHotrod.Args.parse(new String[]{"--no", "isn1=127.0.0.1:11222"});
        assertEquals(PerfilReplicacao.SYNC, s.perfil());
        assertEquals(List.of("sessions"), s.caches());
        SondaHotrod.Args a = SondaHotrod.Args.parse(new String[]{"--replication", "async",
                "--no", "isn1=127.0.0.1:11222"});
        assertEquals(PerfilReplicacao.ASYNC, a.perfil());
        assertEquals(List.of("sessions-async"), a.caches());
        SondaHotrod.Args e = SondaHotrod.Args.parse(new String[]{"--no", "isn1=127.0.0.1:11222",
                "--replication", "async", "--caches", "sessions-async,counters-async"});
        assertEquals(List.of("sessions-async", "counters-async"), e.caches(), "--caches explicito prevalece");
        assertThrows(IllegalArgumentException.class, () -> SondaHotrod.Args.parse(
                new String[]{"--no", "isn1=127.0.0.1:11222", "--replication", "asinc"}));
    }

    @Test
    void valorDeTesteReconheceOsCachesAsync() {
        assertTrue(SondaHotrod.valorDeTeste("sessions-async", "sonda-isn3") instanceof SessionState);
        assertTrue(SondaHotrod.valorDeTeste("counters-async", "sonda-isn3") instanceof IdentityState);
        assertThrows(IllegalArgumentException.class, () -> SondaHotrod.valorDeTeste("sessions-repl", "k"));
    }
}
