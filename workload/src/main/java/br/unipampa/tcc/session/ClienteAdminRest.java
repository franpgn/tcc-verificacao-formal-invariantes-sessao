package br.unipampa.tcc.session;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * W-2a (B-44.2): cliente HTTP da REST de administracao do Infinispan, em memoria
 * ({@link java.net.http.HttpClient}): nenhum processo, nenhum disco (P-5, documento de 05/11 secao 8).
 *
 * <p><b>Autenticacao.</b> Responde ao desafio do servidor: Digest (RFC 7616, {@code MD5} ou {@code SHA-256},
 * {@code qop=auth}) ou Basic. O desafio de cada {@code host:porta} fica guardado e e reaproveitado nos pedidos
 * seguintes (Digest com {@code nc} crescente), de modo que, depois do primeiro, cada pedido custa uma ida e
 * volta. Um {@code 401} com o desafio guardado (por exemplo {@code stale=true}) refaz o pedido uma vez com o
 * desafio novo. A verificacao de qual esquema o servidor 15.0.21 aceita e a tarefa zero do W-2a (a, ainda nao
 * executada): o codigo cobre os dois.</p>
 *
 * <p><b>Prazo.</b> {@link #pedir} limita o pedido INTEIRO (inclusive a segunda ida do desafio) ao prazo dado;
 * ao estourar, cancela o pedido e lanca {@link TimeoutException}. Responde a interrupcao. Os metodos
 * {@code ...Async} servem para pedidos em paralelo; quem espera usa {@link #esperar}.</p>
 *
 * <p>So HTTP/1.1, sem redirecionamento. As credenciais nao vao a nenhum log.</p>
 */
public final class ClienteAdminRest {

    /** Resposta de um pedido. */
    public record Resposta(int codigo, String corpo) {
        public boolean sucesso() {
            return codigo >= 200 && codigo < 300;
        }
    }

    /** Desafio guardado de um {@code host:porta}. */
    record Desafio(String esquema, Map<String, String> campos, AtomicLong nc) {
        boolean mesmoDesafio(Desafio o) {
            return esquema.equals(o.esquema) && campos.equals(o.campos);
        }
    }

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final HttpClient http;
    private final String usuario;
    private final String senha;
    private final Map<String, Desafio> desafios = new ConcurrentHashMap<>();

    public ClienteAdminRest(String usuario, String senha) {
        this(usuario, senha, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    ClienteAdminRest(String usuario, String senha, HttpClient http) {
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.senha = Objects.requireNonNull(senha, "senha");
        this.http = Objects.requireNonNull(http, "http");
    }

    /** Pedido autenticado, sem corpo, com o prazo inteiro {@code prazoNs}. */
    public Resposta pedir(String metodo, URI uri, long prazoNs)
            throws IOException, InterruptedException, TimeoutException {
        return esperar(pedirAsync(metodo, uri, prazoNs), prazoNs);
    }

    /** Pedido anonimo (por exemplo {@code /rest/v2/container/health/status}). */
    public Resposta pedirAnonimo(String metodo, URI uri, long prazoNs)
            throws IOException, InterruptedException, TimeoutException {
        return esperar(pedirAnonimoAsync(metodo, uri, prazoNs), prazoNs);
    }

    /** Pedido autenticado assincrono; o {@code prazoNs} vai tambem ao {@link HttpRequest#timeout()}. */
    public CompletableFuture<Resposta> pedirAsync(String metodo, URI uri, long prazoNs) {
        String autoridade = uri.getRawAuthority();
        Desafio guardado = desafios.get(autoridade);
        HttpRequest req = pedido(metodo, uri, prazoNs, guardado == null ? null : autorizacao(guardado, metodo, uri));
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(r -> {
                    if (r.statusCode() != 401) {
                        return CompletableFuture.completedFuture(new Resposta(r.statusCode(), r.body()));
                    }
                    Desafio novo = escolher(r.headers().allValues("WWW-Authenticate"));
                    if (novo == null) {
                        return CompletableFuture.completedFuture(new Resposta(401, r.body()));
                    }
                    // pedidos em paralelo ao mesmo no recebem o mesmo desafio: compartilham o contador nc
                    Desafio usado = desafios.compute(autoridade,
                            (k, atual) -> atual != null && atual.mesmoDesafio(novo) ? atual : novo);
                    HttpRequest segundo = pedido(metodo, uri, prazoNs, autorizacao(usado, metodo, uri));
                    return http.sendAsync(segundo, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                            .thenApply(r2 -> new Resposta(r2.statusCode(), r2.body()));
                });
    }

    /** Pedido anonimo assincrono. */
    public CompletableFuture<Resposta> pedirAnonimoAsync(String metodo, URI uri, long prazoNs) {
        return http.sendAsync(pedido(metodo, uri, prazoNs, null), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(r -> new Resposta(r.statusCode(), r.body()));
    }

    /**
     * Espera o pedido ate {@code prazoNs}; ao estourar ou na interrupcao, cancela o pedido.
     *
     * @throws IOException          falha de rede (conexao recusada, fechada, ...)
     * @throws TimeoutException     prazo estourado
     * @throws InterruptedException a thread foi interrompida (o sinal fica no chamador)
     */
    public static <T> T esperar(CompletableFuture<T> f, long prazoNs)
            throws IOException, InterruptedException, TimeoutException {
        try {
            return f.get(Math.max(1L, prazoNs), TimeUnit.NANOSECONDS);
        } catch (TimeoutException | InterruptedException e) {
            f.cancel(true);
            throw e;
        } catch (CancellationException e) {
            throw new IOException("pedido cancelado", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof java.net.http.HttpTimeoutException t) {
                throw new TimeoutException(t.getMessage());
            }
            if (c instanceof IOException io) {
                throw io;
            }
            throw new IOException(c == null ? "falha" : c.getClass().getSimpleName() + ": " + c.getMessage(), c);
        }
    }

    private static HttpRequest pedido(String metodo, URI uri, long prazoNs, String autorizacao) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofNanos(Math.max(1_000_000L, prazoNs)));
        if (autorizacao != null) {
            b.header("Authorization", autorizacao);
        }
        return b.build();
    }

    // ------------------------------------------------------------------ desafio

    /** Escolhe, entre os desafios, Digest MD5, depois Digest SHA-256, depois Basic; {@code null} se nenhum. */
    static Desafio escolher(List<String> cabecalhos) {
        Desafio md5 = null;
        Desafio sha = null;
        Desafio basic = null;
        for (String h : cabecalhos) {
            String t = h.strip();
            int esp = t.indexOf(' ');
            String esquema = (esp < 0 ? t : t.substring(0, esp)).toLowerCase(Locale.ROOT);
            Map<String, String> campos = esp < 0 ? Map.of() : campos(t.substring(esp + 1));
            if (esquema.equals("digest")) {
                String alg = campos.getOrDefault("algorithm", "MD5").toUpperCase(Locale.ROOT);
                if (!campos.containsKey("nonce") || !campos.containsKey("realm")) {
                    continue;
                }
                if (alg.equals("MD5") && md5 == null) {
                    md5 = new Desafio("digest", campos, new AtomicLong());
                } else if (alg.equals("SHA-256") && sha == null) {
                    sha = new Desafio("digest", campos, new AtomicLong());
                }
            } else if (esquema.equals("basic") && basic == null) {
                basic = new Desafio("basic", campos, new AtomicLong());
            }
        }
        return md5 != null ? md5 : sha != null ? sha : basic;
    }

    /** {@code chave=valor} ou {@code chave="valor, com virgula"}, separados por virgula. */
    static Map<String, String> campos(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) {
                i++;
            }
            int ig = s.indexOf('=', i);
            if (ig < 0) {
                break;
            }
            String chave = s.substring(i, ig).strip().toLowerCase(Locale.ROOT);
            i = ig + 1;
            String valor;
            if (i < n && s.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        i++;
                    }
                    sb.append(s.charAt(i));
                    i++;
                }
                i++; // aspas de fechamento
                valor = sb.toString();
            } else {
                int fim = s.indexOf(',', i);
                if (fim < 0) {
                    fim = n;
                }
                valor = s.substring(i, fim).strip();
                i = fim;
            }
            m.put(chave, valor);
        }
        return m;
    }

    private String autorizacao(Desafio d, String metodo, URI uri) {
        if (d.esquema().equals("basic")) {
            return "Basic " + Base64.getEncoder().encodeToString(
                    (usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
        }
        Map<String, String> c = d.campos();
        String alg = c.getOrDefault("algorithm", "MD5");
        String realm = c.get("realm");
        String nonce = c.get("nonce");
        String uriDigest = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        String ha1 = hash(alg, usuario + ":" + realm + ":" + senha);
        String ha2 = hash(alg, metodo + ":" + uriDigest);
        boolean qopAuth = qopTemAuth(c.get("qop"));
        StringBuilder h = new StringBuilder("Digest username=\"").append(usuario)
                .append("\", realm=\"").append(realm)
                .append("\", nonce=\"").append(nonce)
                .append("\", uri=\"").append(uriDigest)
                .append("\", algorithm=").append(alg);
        if (qopAuth) {
            String nc = String.format("%08x", d.nc().incrementAndGet());
            byte[] b = new byte[8];
            ALEATORIO.nextBytes(b);
            String cnonce = HexFormat.of().formatHex(b);
            String resp = hash(alg, ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
            h.append(", response=\"").append(resp).append("\", qop=auth, nc=").append(nc)
                    .append(", cnonce=\"").append(cnonce).append('"');
        } else {
            h.append(", response=\"").append(hash(alg, ha1 + ":" + nonce + ":" + ha2)).append('"');
        }
        if (c.containsKey("opaque")) {
            h.append(", opaque=\"").append(c.get("opaque")).append('"');
        }
        return h.toString();
    }

    private static boolean qopTemAuth(String qop) {
        if (qop == null) {
            return false;
        }
        List<String> l = new ArrayList<>();
        for (String x : qop.split(",")) {
            l.add(x.strip().toLowerCase(Locale.ROOT));
        }
        return l.contains("auth");
    }

    /** Resumo hexadecimal minusculo ({@code MD5} ou {@code SHA-256}). */
    static String hash(String algoritmo, String s) {
        String a = algoritmo.toUpperCase(Locale.ROOT).equals("SHA-256") ? "SHA-256" : "MD5";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(a).digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(a + " indisponivel na JVM", e);
        }
    }
}
