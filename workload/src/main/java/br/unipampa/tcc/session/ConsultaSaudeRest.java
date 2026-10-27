package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.FonteDeSaude;
import br.unipampa.tcc.controle.LeituraSaude;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B-26.2: adaptador REST da sonda de topologia ({@link br.unipampa.tcc.controle.SondaTopologia}). Cada
 * consulta e um {@code curl --digest} ao endpoint de saude de UM no
 * ({@code GET /rest/v2/cache-managers/default/health}), com o mesmo mecanismo de {@link HealthRest}:
 * credenciais SO por stdin ({@code -K -}, {@link HealthRest#configCurl}), corpo interpretado por
 * {@link HealthRest#interpretar}.
 *
 * <p>Diferente de {@link HealthRest#consultar}, respeita o prazo pedido pela sonda: {@code --max-time} e
 * o prazo arredondado para cima (minimo 1 s) e a espera do processo para no prazo; ao passar dele, ou
 * se a thread for interrompida, o curl e destruido a forca.</p>
 */
public final class ConsultaSaudeRest implements FonteDeSaude {

    /** Lanca o processo do curl; o padrao e {@link ProcessBuilder#start()}. */
    interface LancadorCurl {
        Process lancar(List<String> comando) throws IOException;
    }

    static final LancadorCurl PADRAO = comando -> new ProcessBuilder(comando).redirectErrorStream(false).start();

    static final String ENDPOINT = "/rest/v2/cache-managers/default/health";

    /** Passo de espera do processo enquanto drena o stdout. */
    private static final long PASSO_MS = 10;

    private final List<String> urls;
    private final String usuario;
    private final String senha;
    private final LancadorCurl lancador;
    private final AtomicLong lancamentos = new AtomicLong();

    /** Adaptador com o lancador padrao ({@link ProcessBuilder}). */
    public ConsultaSaudeRest(List<String> urls, String usuario, String senha) {
        this(urls, usuario, senha, PADRAO);
    }

    ConsultaSaudeRest(List<String> urls, String usuario, String senha, LancadorCurl lancador) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("lista de URLs vazia");
        }
        this.urls = List.copyOf(urls);
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.senha = Objects.requireNonNull(senha, "senha");
        this.lancador = Objects.requireNonNull(lancador, "lancador");
    }

    /**
     * Converte {@code --servers} ({@code h:p,h:p,...}) em URLs {@code http://h:p}, na mesma ordem. Espacos
     * nas pontas de cada item sao aceitos.
     *
     * @throws IllegalArgumentException com o item, se a lista for vazia ou algum item nao tiver host e
     *                                  porta numerica em 1..65535
     */
    public static List<String> urlsDe(String servidores) {
        if (servidores == null) {
            throw new IllegalArgumentException("--servers ausente");
        }
        List<String> r = new ArrayList<>();
        for (String bruto : servidores.split(",", -1)) {
            String item = bruto.trim();
            int dp = item.lastIndexOf(':');
            if (dp <= 0 || dp == item.length() - 1) {
                throw new IllegalArgumentException("servidor sem host:porta: '" + item + "'");
            }
            String host = item.substring(0, dp).trim();
            int porta;
            try {
                porta = Integer.parseInt(item.substring(dp + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("porta nao numerica em '" + item + "'");
            }
            if (host.isEmpty() || porta < 1 || porta > 65535) {
                throw new IllegalArgumentException("host vazio ou porta fora de 1..65535 em '" + item + "'");
            }
            r.add("http://" + host + ":" + porta);
        }
        return List.copyOf(r);
    }

    @Override
    public int nos() {
        return urls.size();
    }

    /** {@code host:porta} do no (a URL sem o esquema). */
    @Override
    public String rotulo(int indice) {
        String u = urls.get(indice);
        int i = u.indexOf("://");
        return i >= 0 ? u.substring(i + 3) : u;
    }

    @Override
    public LeituraSaude consultar(int indice, long prazoNs) throws Exception {
        long limite = System.nanoTime() + prazoNs;
        long maxTimeS = Math.max(1L, (prazoNs + 999_999_999L) / 1_000_000_000L);
        List<String> comando = List.of("curl", "-s", "--fail", "--digest", "--max-time", Long.toString(maxTimeS),
                "-K", "-", urls.get(indice) + ENDPOINT);
        Process p = lancador.lancar(comando);
        lancamentos.incrementAndGet();
        boolean terminou = false;
        try {
            try (OutputStream os = p.getOutputStream()) {
                os.write(HealthRest.configCurl(usuario, senha).getBytes(StandardCharsets.UTF_8));
            }
            InputStream in = p.getInputStream();
            ByteArrayOutputStream corpo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (true) {
                drenar(in, corpo, buf);
                long restanteNs = limite - System.nanoTime();
                if (restanteNs <= 0) {
                    throw new TimeoutException("curl excedeu o prazo de " + TimeUnit.NANOSECONDS.toMillis(prazoNs)
                            + " ms");
                }
                long passoNs = Math.min(restanteNs, TimeUnit.MILLISECONDS.toNanos(PASSO_MS));
                if (p.waitFor(passoNs, TimeUnit.NANOSECONDS)) {
                    terminou = true;
                    break;
                }
            }
            corpo.write(in.readAllBytes()); // processo terminou: EOF garantido
            int exit = p.exitValue();
            if (exit != 0) {
                throw new IOException("curl falhou (exit " + exit + ")");
            }
            HealthRest.Estado e = HealthRest.interpretar(corpo.toString(StandardCharsets.UTF_8));
            return new LeituraSaude(e.numeroDeNos(), e.statusPorCache());
        } finally {
            if (!terminou) {
                p.destroyForcibly(); // prazo, interrupcao ou erro de E/S
            }
        }
    }

    private static void drenar(InputStream in, ByteArrayOutputStream corpo, byte[] buf) throws IOException {
        int n;
        while (in.available() > 0 && (n = in.read(buf, 0, Math.min(buf.length, in.available()))) > 0) {
            corpo.write(buf, 0, n);
        }
    }

    /** Processos curl lancados desde a criacao (custo do smoke). */
    public long lancamentos() {
        return lancamentos.get();
    }
}
