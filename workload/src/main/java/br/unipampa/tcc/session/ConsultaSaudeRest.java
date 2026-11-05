package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.FonteDeSaude;
import br.unipampa.tcc.controle.LeituraSaude;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B-26.2: adaptador REST da sonda de topologia ({@link br.unipampa.tcc.controle.SondaTopologia}). Cada
 * consulta e um {@code GET} ao endpoint de saude de UM no ({@code GET /rest/v2/cache-managers/default/health}),
 * com o corpo interpretado por {@link HealthRest#interpretar}.
 *
 * <p><b>W-2b (P-5, achado N-7 do documento de 05/11).</b> Ate o W-2b cada consulta criava um processo
 * {@code curl --digest}; na VM, o travamento de disco travou junto a criacao de processos. Agora a consulta e
 * feita em memoria pelo {@link ClienteAdminRest} do W-2a ({@link java.net.http.HttpClient}, Digest RFC 7616 com o
 * desafio de cada no guardado e reaproveitado): nenhum processo, nenhum disco. O prazo e o que a sonda pede
 * ({@code prazoNs}, o mesmo em que a sonda antiga destruia o {@code curl}) e limita o pedido inteiro, inclusive a
 * segunda ida do desafio.</p>
 *
 * <p>Os motivos que a sonda grava nao mudam: prazo estourado = {@link java.util.concurrent.TimeoutException}
 * ({@code timeout}); conexao recusada ou resposta fora de 2xx = {@link IOException} exata ({@code falha:IOException},
 * como o {@code exit} do {@code curl --fail}); corpo que nao e JSON de saude = {@link IllegalArgumentException}.
 * Interrupcao propaga {@link InterruptedException} e cancela o pedido.</p>
 */
public final class ConsultaSaudeRest implements FonteDeSaude {

    static final String ENDPOINT = "/rest/v2/cache-managers/default/health";

    private final List<String> urls;
    private final ClienteAdminRest cliente;
    private final AtomicLong pedidos = new AtomicLong();

    /** Adaptador com um {@link ClienteAdminRest} proprio. */
    public ConsultaSaudeRest(List<String> urls, String usuario, String senha) {
        this(urls, new ClienteAdminRest(usuario, senha));
    }

    /** Adaptador sobre um cliente dado (CLI de smoke, testes). */
    ConsultaSaudeRest(List<String> urls, ClienteAdminRest cliente) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("lista de URLs vazia");
        }
        this.urls = List.copyOf(urls);
        this.cliente = Objects.requireNonNull(cliente, "cliente");
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
        URI uri = URI.create(urls.get(indice) + ENDPOINT);
        pedidos.incrementAndGet();
        ClienteAdminRest.Resposta r;
        try {
            r = cliente.pedir("GET", uri, prazoNs);
        } catch (IOException e) {
            // classe exata IOException: o motivo da sonda continua falha:IOException (P-5 sem mudar a amostra)
            String m = e.getMessage();
            throw new IOException("GET " + uri + " falhou: " + e.getClass().getSimpleName() + (m == null ? "" : ": " + m),
                    e);
        }
        if (!r.sucesso()) {
            throw new IOException("GET " + uri + " devolveu HTTP " + r.codigo());
        }
        HealthRest.Estado e = HealthRest.interpretar(r.corpo());
        return new LeituraSaude(e.numeroDeNos(), e.statusPorCache());
    }

    /** Pedidos {@code GET} feitos desde a criacao (um por consulta; o desafio Digest nao conta). */
    public long pedidos() {
        return pedidos.get();
    }

    /**
     * Processos lancados desde a criacao: sempre 0 desde o W-2b (P-5). Mantido porque o campo {@code curl_lancados}
     * do {@code fim} de {@code observaveis} (contrato 1.2) vem daqui e o esquema nao muda.
     */
    public long lancamentos() {
        return 0L;
    }
}
