package br.unipampa.tcc.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SondaCsvTest {

    @Test
    void cabecalhoTemAsNoveColunasEsperadas() {
        assertEquals("t_epoch_ns,no,cache,operacao,duracao_ns,resultado,excecao_classe,mensagem,causas",
                SondaCsv.CABECALHO);
    }

    @Test
    void linhaDeSucessoTemCamposDeExcecaoVazios() {
        String l = SondaCsv.linha(1792127500000000000L, "isn3", "sessions", "get", 1234L, null);
        assertEquals("1792127500000000000,isn3,sessions,get,1234,OK,,,", l);
    }

    @Test
    void linhaDeErroTrazClasseMensagemECadeiaDeCausas() {
        Throwable raiz = new SocketTimeoutException("Read timed out");
        Throwable meio = new IOException("io falhou", raiz);
        RuntimeException topo = new IllegalStateException("topo", meio);
        String l = SondaCsv.linha(5L, "isn1", "sessions", "put", 77L, topo);
        assertEquals("5,isn1,sessions,put,77,ERRO,java.lang.IllegalStateException,topo,"
                + "java.io.IOException: io falhou | java.net.SocketTimeoutException: Read timed out", l);
    }

    @Test
    void camposComVirgulaAspasOuQuebraDeLinhaSaoEscapadosEmUmaLinhaSo() {
        RuntimeException e = new RuntimeException("a,b \"c\"\nd\r\ne");
        String l = SondaCsv.linha(1L, "isn3", "sessions", "get", 1L, e);
        assertTrue(l.contains("\"a,b \"\"c\"\"\\nd\\ne\""), l);
        assertEquals(-1, l.indexOf('\n'));
        assertEquals(-1, l.indexOf('\r'));
    }

    @Test
    void mensagemNulaViraVazioECausaSemMensagemSoTemAClasse() {
        RuntimeException e = new RuntimeException(null, new IllegalArgumentException());
        String l = SondaCsv.linha(1L, "isn3", "sessions", "get", 1L, e);
        assertEquals("1,isn3,sessions,get,1,ERRO,java.lang.RuntimeException,,java.lang.IllegalArgumentException", l);
    }

    @Test
    void cadeiaCiclicaDeCausasNaoTrava() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        String l = SondaCsv.linha(1L, "isn3", "sessions", "get", 1L, a);
        assertTrue(l.endsWith("java.lang.RuntimeException: b"), l);
    }

    @Test
    void suprimidosNaoEntramNaCadeia() {
        RuntimeException e = new RuntimeException("x");
        e.addSuppressed(new IOException("suprimida"));
        assertEquals("1,n,c,o,1,ERRO,java.lang.RuntimeException,x,",
                SondaCsv.linha(1L, "n", "c", "o", 1L, e));
    }
}
