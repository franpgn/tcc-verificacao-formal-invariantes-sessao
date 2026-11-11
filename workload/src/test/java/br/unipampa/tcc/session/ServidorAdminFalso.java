package br.unipampa.tcc.session;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * W-2a: servidor REST de administracao falso ({@code com.sun.net.httpserver}) para os testes do
 * {@link AtuadorRestInfinispan} e do {@link ClienteAdminRest}. Grava cada pedido (metodo, caminho com a
 * consulta, cabecalho {@code Authorization}) e responde pelo {@link Tratador}.
 */
final class ServidorAdminFalso implements AutoCloseable {

    /** Um pedido recebido. */
    record Pedido(String metodo, String caminho, String autorizacao) {
        @Override
        public String toString() {
            return metodo + " " + caminho;
        }
    }

    /** Resposta a devolver. */
    record Resp(int codigo, String corpo, Map<String, String> cabecalhos, long atrasoMs) {
        static Resp de(int codigo, String corpo) {
            return new Resp(codigo, corpo, Map.of(), 0);
        }

        Resp atrasada(long ms) {
            return new Resp(codigo, corpo, cabecalhos, ms);
        }
    }

    @FunctionalInterface
    interface Tratador {
        Resp tratar(ServidorAdminFalso srv, Pedido p) throws Exception;
    }

    private final HttpServer http;
    private final ExecutorService exec;
    private final List<Pedido> pedidos = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean parado = new AtomicBoolean();
    volatile Tratador tratador;

    ServidorAdminFalso(Tratador tratador) throws IOException {
        this.tratador = tratador;
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "servidor-admin-falso");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(exec);
        http.createContext("/", this::tratar);
        http.start();
    }

    /** {@code http://127.0.0.1:porta}. */
    String url() {
        return "http://" + http.getAddress().getAddress().getHostAddress() + ":" + http.getAddress().getPort();
    }

    /** Rotulo do no no atuador ({@code host:porta}). */
    String rotulo() {
        return AtuadorRestInfinispan.rotulo(url());
    }

    List<Pedido> pedidos() {
        synchronized (pedidos) {
            return List.copyOf(pedidos);
        }
    }

    /** Para de aceitar conexoes (como o servidor que saiu). Idempotente. */
    void parar() {
        if (parado.compareAndSet(false, true)) {
            http.stop(0);
        }
    }

    /** Para em outra thread depois de {@code ms}. */
    void pararDepoisDe(long ms) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            parar();
        }, "parar-servidor-falso");
        t.setDaemon(true);
        t.start();
    }

    private void tratar(HttpExchange ex) throws IOException {
        try {
            String q = ex.getRequestURI().getRawQuery();
            Pedido p = new Pedido(ex.getRequestMethod(), ex.getRequestURI().getRawPath() + (q == null ? "" : "?" + q),
                    ex.getRequestHeaders().getFirst("Authorization"));
            pedidos.add(p);
            ex.getRequestBody().readAllBytes();
            Resp r;
            try {
                r = tratador.tratar(this, p);
            } catch (Exception | AssertionError e) {
                r = Resp.de(599, "erro no tratador: " + e);
            }
            if (r.atrasoMs() > 0) {
                try {
                    Thread.sleep(r.atrasoMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            r.cabecalhos().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            if (r.corpo() == null || r.codigo() == 204) {
                ex.sendResponseHeaders(r.codigo(), -1);
            } else {
                byte[] b = r.corpo().getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(r.codigo(), b.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(b);
                }
            }
        } catch (IOException e) {
            // cliente desistiu (prazo): nada a fazer
        } finally {
            ex.close();
        }
    }

    @Override
    public void close() {
        parar();
        exec.shutdownNow();
    }
}
