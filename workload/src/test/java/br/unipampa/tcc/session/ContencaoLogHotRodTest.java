package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2b (P-5, item 3): contencao do {@code ISPN004007} do cliente Hot Rod no stderr. No I-3b (05/11) os bracos
 * ativos gravaram de 9,9 a 12,1 MB de stderr por execucao, 98,7 % em linhas {@code ISPN004007} com a pilha
 * (6 450 e 6 348 ocorrencias); o shadow gravou 1,5 MB. A primeira ocorrencia de cada (no, motivo) sai inteira, a
 * segunda vira um aviso de uma linha, as demais so sao contadas; a contagem por no e motivo vai a uma linha
 * {@code Hot Rod ISPN004007:} do stdout no fim de cada repeticao.
 *
 * <p>As mensagens das excecoes abaixo sao as do stderr do I-3b ({@code wt-w4/runs/i3b/*}/{@code stderr.log.gz}).</p>
 */
class ContencaoLogHotRodTest {

    static final String MSG_4007 = "ISPN004007: Exception encountered. Retry 1 out of 1";

    /** {@code io.netty.channel.AbstractChannel$AnnotatedConnectException} do I-3b (sem depender do Netty). */
    static final class AnnotatedConnectException extends ConnectException {
        AnnotatedConnectException(String m, Throwable causa) {
            super(m);
            initCause(causa);
        }
    }

    static Throwable recusada() {
        return new AnnotatedConnectException("Connection refused: getsockopt: /127.0.0.1:12613",
                new ConnectException("Connection refused"));
    }

    static Throwable prazo() {
        return new SocketTimeoutException("GetOperation{canario-async, key=lag:127.0.0.1:12611#4, flags=0,"
                + " connection=/127.0.0.1:12613} timed out after 2000 ms");
    }

    static Throwable fechada() {
        return new TransportException("org.infinispan.client.hotrod.exceptions.TransportException:: ISPN004071:"
                + " Connection to /127.0.0.1:12613 was closed while waiting for response.", null);
    }

    static Throwable naoResolvida() {
        return new TransportException("ISPN004071: Connection to 127.0.0.1/<unresolved>:12613 was closed while"
                + " waiting for response.", null);
    }

    static Throwable reset() {
        return new TransportException(new SocketException("Connection reset"), null);
    }

    static LogRecord registro(String msg, Throwable t) {
        LogRecord r = new LogRecord(Level.SEVERE, msg);
        r.setLoggerName(ContencaoLogHotRod.LOGGER);
        r.setThrown(t);
        return r;
    }

    // ------------------------------------------------------------------ chave

    @Test
    @DisplayName("no: o endereco da conexao (connection=), depois o primeiro host/ip:porta da cadeia; a chave da sonda (lag:h:p) nao conta")
    void noDasMensagensReais() {
        assertEquals("127.0.0.1:12613", ContencaoLogHotRod.noDe(recusada()));
        assertEquals("127.0.0.1:12613", ContencaoLogHotRod.noDe(prazo()));
        assertEquals("127.0.0.1:12613", ContencaoLogHotRod.noDe(fechada()));
        assertEquals("127.0.0.1:12613", ContencaoLogHotRod.noDe(naoResolvida()));
        assertEquals("isn3:11222", ContencaoLogHotRod.noDe(new IOException("x isn3/<unresolved>:11222 y")));
        assertEquals("10.0.0.3:11222", ContencaoLogHotRod.noDe(new IOException("ligacao isn3/10.0.0.3:11222")));
        assertEquals("[0:0:0:0:0:0:0:1]:11222", ContencaoLogHotRod.noDe(new IOException("a /[0:0:0:0:0:0:0:1]:11222")));
        assertEquals(ContencaoLogHotRod.NO_DESCONHECIDO, ContencaoLogHotRod.noDe(reset()));
        // TransportException com o endereco do servidor: vale o endereco, mesmo sem ele na mensagem
        assertEquals("127.0.0.1:12613", ContencaoLogHotRod.noDe(new TransportException(
                new SocketException("Connection reset"), InetSocketAddress.createUnresolved("127.0.0.1", 12613))));
        assertEquals("127.0.0.1:12612", ContencaoLogHotRod.noDe(new IllegalStateException("x", new TransportException(
                new SocketException("Connection reset"), new InetSocketAddress(InetAddress.getLoopbackAddress(), 12612)))));
        assertEquals(ContencaoLogHotRod.NO_DESCONHECIDO, ContencaoLogHotRod.noDe(null));
        assertEquals(ContencaoLogHotRod.NO_DESCONHECIDO,
                ContencaoLogHotRod.noDe(new IOException("key=lag:127.0.0.1:12611#4 sem conexao")));
    }

    @Test
    @DisplayName("motivo: classe da excecao + codigo ISPN da cadeia, ou + classe da causa raiz se for outra")
    void motivoDasMensagensReais() {
        assertEquals("AnnotatedConnectException:ConnectException", ContencaoLogHotRod.motivoDe(recusada()));
        assertEquals("SocketTimeoutException", ContencaoLogHotRod.motivoDe(prazo()));
        assertEquals("TransportException:ISPN004071", ContencaoLogHotRod.motivoDe(fechada()));
        assertEquals("TransportException:ISPN004071", ContencaoLogHotRod.motivoDe(naoResolvida()));
        assertEquals("TransportException:SocketException", ContencaoLogHotRod.motivoDe(reset()));
        assertEquals(ContencaoLogHotRod.SEM_EXCECAO, ContencaoLogHotRod.motivoDe(null));
        Throwable ciclo = new IOException("a");
        Throwable b = new IOException("b", ciclo);
        ciclo.initCause(b);
        assertEquals("IOException", ContencaoLogHotRod.motivoDe(ciclo), "ciclo de causas nao trava");
    }

    // ------------------------------------------------------------------ filtro

    @Test
    @DisplayName("filtro: so o ISPN004007 e contido; a 1a de cada (no, motivo) sai inteira, a 2a vira aviso sem pilha, as demais nao saem")
    void filtro() {
        ContencaoLogHotRod c = new ContencaoLogHotRod();
        LogRecord outra = registro("ISPN004005: Error received from the server: x", prazo());
        assertTrue(c.isLoggable(outra));
        assertSame(prazo().getClass(), outra.getThrown().getClass(), "outra mensagem passa intacta");
        assertEquals(0, c.total());

        LogRecord r1 = registro(MSG_4007, recusada());
        assertTrue(c.isLoggable(r1));
        assertEquals(MSG_4007, r1.getMessage());
        assertTrue(r1.getThrown() != null, "a primeira sai com a pilha");

        LogRecord r2 = registro(MSG_4007, recusada());
        assertTrue(c.isLoggable(r2), "a segunda sai como aviso");
        assertNull(r2.getThrown(), "o aviso nao tem pilha");
        assertTrue(r2.getMessage().startsWith("ISPN004007 [W-2b]"), r2.getMessage());
        assertTrue(r2.getMessage().contains("no=127.0.0.1:12613")
                && r2.getMessage().contains("motivo=AnnotatedConnectException:ConnectException")
                && r2.getMessage().contains("Hot Rod ISPN004007:"), r2.getMessage());

        for (int i = 0; i < 10; i++) {
            assertFalse(c.isLoggable(registro(MSG_4007, recusada())));
        }
        // outra chave: a primeira sai inteira de novo
        LogRecord p1 = registro(MSG_4007, prazo());
        assertTrue(c.isLoggable(p1));
        assertTrue(p1.getThrown() != null);
        assertEquals(13, c.total());
        assertEquals(2, c.inteiras());
        assertEquals(11, c.contidas());
    }

    @Test
    @DisplayName("linha do stdout: total, inteiras, contidas e uma parte por (no, motivo) com n e o primeiro e o ultimo instante, em ordem de n")
    void linhaResumo() {
        ContencaoLogHotRod c = new ContencaoLogHotRod();
        assertEquals("Hot Rod ISPN004007: total=0 inteiras=0 contidas=0 chaves=0", c.linhaResumo());
        Instant t0 = Instant.parse("2026-11-05T10:19:20.123456789Z");
        for (int i = 0; i < 3; i++) {
            LogRecord r = registro(MSG_4007, recusada());
            r.setInstant(t0.plusSeconds(i));
            c.isLoggable(r);
        }
        LogRecord r = registro(MSG_4007, prazo());
        r.setInstant(t0.plusMillis(500));
        c.isLoggable(r);
        long ns0 = t0.getEpochSecond() * 1_000_000_000L + t0.getNano();
        assertEquals("Hot Rod ISPN004007: total=4 inteiras=2 contidas=2 chaves=2"
                + " | no=127.0.0.1:12613 motivo=AnnotatedConnectException:ConnectException n=3 t_primeira_ns=" + ns0
                + " t_ultima_ns=" + (ns0 + 2_000_000_000L)
                + " | no=127.0.0.1:12613 motivo=SocketTimeoutException n=1 t_primeira_ns=" + (ns0 + 500_000_000L)
                + " t_ultima_ns=" + (ns0 + 500_000_000L), c.linhaResumo());
        c.reiniciar();
        assertEquals("Hot Rod ISPN004007: total=0 inteiras=0 contidas=0 chaves=0", c.linhaResumo());
        LogRecord depois = registro(MSG_4007, recusada());
        assertTrue(c.isLoggable(depois) && depois.getThrown() != null, "depois de reiniciar a primeira sai inteira de novo");
    }

    @Test
    @DisplayName("concorrencia: 8 threads x 1000 registros da mesma chave = 8000 contados, uma inteira e um aviso")
    void concorrencia() throws Exception {
        ContencaoLogHotRod c = new ContencaoLogHotRod();
        AtomicInteger passaram = new AtomicInteger();
        AtomicInteger inteiras = new AtomicInteger();
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(() -> {
                try {
                    largada.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int k = 0; k < 1000; k++) {
                    LogRecord r = registro(MSG_4007, recusada());
                    if (c.isLoggable(r)) {
                        passaram.incrementAndGet();
                        if (r.getThrown() != null) {
                            inteiras.incrementAndGet();
                        }
                    }
                }
            });
            ts.add(t);
            t.start();
        }
        largada.countDown();
        for (Thread t : ts) {
            t.join(10_000);
        }
        assertEquals(8000, c.total());
        assertEquals(2, passaram.get());
        assertEquals(1, inteiras.get());
    }

    @Test
    @DisplayName("filtro que ja estava no logger continua valendo antes da contencao")
    void filtroAnterior() {
        ContencaoLogHotRod c = new ContencaoLogHotRod();
        Filter nega = r -> false;
        c.encadear(nega);
        assertFalse(c.isLoggable(registro(MSG_4007, recusada())));
        assertEquals(0, c.total(), "o que o filtro anterior recusa nao e contado");
        assertThrows(IllegalArgumentException.class, () -> c.encadear(c));
    }

    // ------------------------------------------------------------------ caminho real

    @Test
    @DisplayName("caminho real: Log.HOTROD (JBoss Logging -> java.util.logging, como no stderr do I-3b) passa pelo filtro instalado no logger org.infinispan.HOTROD")
    void caminhoReal() {
        ContencaoLogHotRod c = ContencaoLogHotRod.instalar();
        assertSame(c, ContencaoLogHotRod.instalar(), "instalar e idempotente");
        Logger jul = Logger.getLogger(ContencaoLogHotRod.LOGGER);
        assertSame(c, jul.getFilter());
        List<LogRecord> publicados = Collections.synchronizedList(new ArrayList<>());
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                publicados.add(r);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        boolean paisAntes = jul.getUseParentHandlers();
        jul.addHandler(captura);
        jul.setUseParentHandlers(false); // nao suja o stdout do Surefire
        try {
            c.reiniciar();
            for (int i = 0; i < 5; i++) {
                org.infinispan.client.hotrod.logging.Log.HOTROD.exceptionAndNoRetriesLeft(1, 1, prazo());
            }
            assertEquals(5, c.total());
            assertEquals(2, publicados.size(), "uma inteira e um aviso: " + publicados.size());
            assertTrue(publicados.get(0).getMessage().startsWith(MSG_4007), publicados.get(0).getMessage());
            assertTrue(publicados.get(0).getThrown() instanceof SocketTimeoutException);
            assertNull(publicados.get(1).getThrown());
        } finally {
            jul.removeHandler(captura);
            jul.setUseParentHandlers(paisAntes);
            c.reiniciar();
        }
    }

    // ------------------------------------------------------------------ WorkloadMain

    /**
     * A repeticao precisa do cluster; a ligacao e conferida na fonte, como os outros detectores de
     * {@code executarRepeticao} ({@link DetectorFonte}). O laco de {@code executar} continua como o
     * {@code LacoDeRepeticoesTest} exige (chamada direta, sem try).
     */
    @Test
    @DisplayName("WorkloadMain.executarRepeticao instala e zera a contencao antes do try do rcm e imprime a linha Hot Rod ISPN004007 no finally dele")
    void ligacaoNaRepeticao() throws IOException {
        String fonte = DetectorFonte.semComentarios(Files.readString(NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n"));
        int[] corpo = DetectorFonte.corpo(DetectorFonte.mascarado(fonte), DetectorFonte.ASSINATURA_REPETICAO);
        assertTrue(corpo != null, "executarRepeticao nao encontrado");
        String c = fonte.substring(corpo[0], corpo[1]);
        int instala = c.indexOf("ContencaoLogHotRod contencao = ContencaoLogHotRod.instalar();");
        int zera = c.indexOf("contencao.reiniciar();");
        int tryRcm = c.indexOf("try (RemoteCacheManager rcm");
        assertTrue(instala >= 0 && zera > instala && tryRcm > zera, "instalar e reiniciar antes do try do rcm");
        // o finally fecha o try do rcm (indentacao de 8) e a linha de resumo e o ultimo statement do metodo
        int fin = c.lastIndexOf("\n        } finally {");
        String resumo = "System.out.println(contencao.linhaResumo());";
        int imp = c.indexOf(resumo, Math.max(fin, 0));
        assertTrue(fin > tryRcm && imp > fin, "finally do try do rcm com a linha de resumo");
        assertTrue(c.substring(imp + resumo.length()).replace("}", "").isBlank(),
                "a linha de resumo e o ultimo statement: " + c.substring(imp));
        assertEquals(1, c.split("linhaResumo\\(\\)", -1).length - 1, "uma linha de resumo por repeticao");
    }
}
