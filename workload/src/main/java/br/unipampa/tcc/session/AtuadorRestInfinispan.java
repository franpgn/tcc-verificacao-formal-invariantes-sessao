package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.AtuadorCluster;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * W-2a (B-44.2): atuador do controlador sobre o <i>cluster</i>, so pela REST de administracao do Infinispan
 * 15.0.21, sobre {@link ClienteAdminRest} (HTTP em memoria; nenhum processo, nenhum disco, nenhum cliente Hot
 * Rod: P-5 e R-ARQ-1). Especificacao: {@code docs/acoes-controlador-cluster-2026-11-05.md}, secoes 3.1, 3.2 e
 * 8 (item 5). Nunca muda atributo de cache (R-ARQ-3): AC-2 e AC-5 nao sao atributos, e por isso a escorva do
 * P-29 nao se aplica; a leitura de volta nos tres nos se aplica e e feita.
 *
 * <ul>
 *   <li><b>AC-2, aplicar</b>: {@code POST /rest/v2/container?action=disable-rebalancing} (forma GLOBAL,
 *       P-34) num no que respondeu na ultima janela (o primeiro, na ordem de {@code --controller-admin}; se
 *       ele nao responder ao pedido, o seguinte), com no maximo metade do prazo que resta; depois, em
 *       paralelo, {@code GET /rest/v2/container} e {@code GET /rest/v2/caches/{c}} dos caches do modo ativo
 *       nos tres nos, lendo {@code rebalancing_enabled}. {@code APLICADA} se ao menos 2 nos (maioria de 3)
 *       leram tudo e tudo {@code false}; qualquer {@code true} lido e {@code FALHOU} (divergencia).
 *       <b>Reverter</b>: o mesmo com {@code enable-rebalancing} e {@code true}; idempotente.</li>
 *   <li><b>AC-5, aplicar</b>: {@code POST /rest/v2/server/?action=stop} ao proprio alvo (nunca a
 *       {@code /rest/v2/cluster?action=stop&server=}, achado N-2), so se o alvo respondeu na ultima janela;
 *       {@code APLICADA} se o codigo for 204 e, dentro do prazo, a saude do alvo
 *       ({@code /rest/v2/container/health/status}, anonima) parou de responder e a dos outros dois seguiu
 *       respondendo. <b>Reverter</b>: {@code NAO_EXECUTADA} (a reversao e do orquestrador).</li>
 * </ul>
 *
 * <p>O prazo recebido limita a chamada inteira (todos os pedidos e a leitura de volta), como manda
 * {@link AtuadorCluster}. Interrupcao: a {@link InterruptedException} sobe ao executor.</p>
 *
 * <p>Os rotulos dos nos sao os da sonda de topologia: a URL de {@code --controller-admin} sem o esquema
 * ({@code host:porta}), os mesmos de {@code ConsultaSaudeRest#rotulo}. O {@code alvo} do AC-5 e o conjunto
 * {@code nosQueResponderam} usam esses rotulos.</p>
 */
public final class AtuadorRestInfinispan implements AtuadorCluster {

    public static final String NOME = "rest-infinispan";

    static final String CONTAINER = "/rest/v2/container";
    static final String DESLIGAR = CONTAINER + "?action=disable-rebalancing";
    static final String LIGAR = CONTAINER + "?action=enable-rebalancing";
    static final String CACHES = "/rest/v2/caches/";
    static final String PARAR = "/rest/v2/server/?action=stop";
    static final String SAUDE = "/rest/v2/container/health/status";
    static final String CAMPO = "rebalancing_enabled";
    static final String REVERSAO_ORQUESTRADOR = "reversao-pelo-orquestrador";

    /** Prazo de cada consulta de saude do AC-5 (limitado tambem pelo que resta do prazo da chamada). */
    static final long PRAZO_SAUDE_NS = 1_000_000_000L;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, URI> nos;
    private final ClienteAdminRest cliente;
    private final Supplier<Set<String>> nosQueResponderam;
    private final long passoSaudeNs;

    /**
     * @param urlsAdmin         {@code http://host:porta} de cada no, na ordem de {@code --controller-admin}
     * @param cliente           cliente HTTP autenticado
     * @param nosQueResponderam rotulos dos nos que responderam a REST na ultima janela (da sonda de topologia)
     * @param passoSaudeNs      intervalo entre as consultas de saude do alvo do AC-5 (&gt; 0)
     */
    public AtuadorRestInfinispan(List<String> urlsAdmin, ClienteAdminRest cliente,
                                 Supplier<Set<String>> nosQueResponderam, long passoSaudeNs) {
        if (urlsAdmin == null || urlsAdmin.isEmpty()) {
            throw new IllegalArgumentException("lista de URLs vazia");
        }
        Map<String, URI> m = new LinkedHashMap<>();
        for (String u : urlsAdmin) {
            String base = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
            URI uri = URI.create(base);
            if (m.put(rotulo(base), uri) != null) {
                throw new IllegalArgumentException("URL repetida: " + u);
            }
        }
        this.nos = m;
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.nosQueResponderam = Objects.requireNonNull(nosQueResponderam, "nosQueResponderam");
        if (passoSaudeNs <= 0) {
            throw new IllegalArgumentException("passoSaudeNs deve ser > 0: " + passoSaudeNs);
        }
        this.passoSaudeNs = passoSaudeNs;
    }

    /** {@code host:porta}: a URL sem o esquema (o rotulo da sonda). */
    static String rotulo(String url) {
        int i = url.indexOf("://");
        return i >= 0 ? url.substring(i + 3) : url;
    }

    /** Rotulos dos nos, na ordem de {@code --controller-admin}. */
    public List<String> rotulos() {
        return List.copyOf(nos.keySet());
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) throws Exception {
        return switch (acao.tipo()) {
            case AC_2 -> rebalanceamentoGlobal(acao, prazoNs, false);
            case AC_5 -> pararAlvo(acao, prazoNs);
            default -> ResultadoAtuacao.falhou(null, null, "tipo-fora-do-braco-b:" + acao.tipo().rotulo());
        };
    }

    @Override
    public ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) throws Exception {
        return switch (acao.tipo()) {
            case AC_2 -> rebalanceamentoGlobal(acao, prazoNs, true);
            case AC_5 -> ResultadoAtuacao.naoExecutada(REVERSAO_ORQUESTRADOR);
            default -> ResultadoAtuacao.falhou(null, null, "tipo-fora-do-braco-b:" + acao.tipo().rotulo());
        };
    }

    // ------------------------------------------------------------------ AC-2

    private ResultadoAtuacao rebalanceamentoGlobal(AcaoCluster acao, long prazoNs, boolean ligar)
            throws InterruptedException {
        long limite = System.nanoTime() + prazoNs;
        Set<String> responderam = nosQueResponderam.get();
        List<String> candidatos = new ArrayList<>();
        for (String no : nos.keySet()) {
            if (responderam.contains(no)) {
                candidatos.add(no);
            }
        }
        if (candidatos.isEmpty()) {
            return ResultadoAtuacao.falhou(null, null, "nenhum-no-respondeu-na-ultima-janela");
        }
        String caminho = ligar ? LIGAR : DESLIGAR;
        Integer codigo = null;
        String erros = null;
        boolean aceito = false;
        for (String no : candidatos) {
            long resta = limite - System.nanoTime();
            if (resta <= 0) {
                break;
            }
            try {
                ClienteAdminRest.Resposta r = cliente.pedir("POST", uri(no, caminho), Math.max(1L, resta / 2));
                codigo = r.codigo();
                if (!r.sucesso()) {
                    return ResultadoAtuacao.falhou(codigo, null, "http-" + codigo + "@" + no);
                }
                aceito = true;
                break;
            } catch (IOException | TimeoutException e) {
                erros = (erros == null ? "" : erros + ";") + no + ":" + e.getClass().getSimpleName();
            }
        }
        if (!aceito) {
            return ResultadoAtuacao.falhou(codigo, null, "pedido-sem-resposta:" + (erros == null ? "prazo" : erros));
        }
        return lerDeVolta(acao.caches(), ligar, codigo, limite);
    }

    /** Leitura de volta do {@code rebalancing_enabled} nos tres nos, em paralelo, ate {@code limite}. */
    private ResultadoAtuacao lerDeVolta(List<String> caches, boolean esperado, Integer codigo, long limite)
            throws InterruptedException {
        List<String> caminhos = new ArrayList<>();
        caminhos.add(CONTAINER);
        for (String c : caches) {
            caminhos.add(CACHES + c);
        }
        long resta = Math.max(1L, limite - System.nanoTime());
        Map<String, List<CompletableFuture<ClienteAdminRest.Resposta>>> pedidos = new LinkedHashMap<>();
        for (String no : nos.keySet()) {
            List<CompletableFuture<ClienteAdminRest.Resposta>> l = new ArrayList<>();
            for (String c : caminhos) {
                l.add(cliente.pedirAsync("GET", uri(no, c), resta));
            }
            pedidos.put(no, l);
        }
        int completos = 0;
        boolean divergente = false;
        StringJoiner lido = new StringJoiner(";");
        for (Map.Entry<String, List<CompletableFuture<ClienteAdminRest.Resposta>>> e : pedidos.entrySet()) {
            StringJoiner porNo = new StringJoiner(",", e.getKey() + ":", "");
            boolean completo = true;
            for (int i = 0; i < caminhos.size(); i++) {
                String nomeLido = i == 0 ? "container" : caches.get(i - 1);
                Boolean v = lerCampo(e.getValue().get(i), limite);
                if (v == null) {
                    completo = false;
                    porNo.add(nomeLido + "=?");
                } else {
                    porNo.add(nomeLido + "=" + v);
                    if (v != esperado) {
                        divergente = true;
                    }
                }
            }
            if (completo) {
                completos++;
            }
            lido.add(porNo.toString());
        }
        if (divergente) {
            return ResultadoAtuacao.falhou(codigo, lido.toString(), "leitura-de-volta-divergente");
        }
        if (completos < 2) {
            return ResultadoAtuacao.falhou(codigo, lido.toString(), "leitura-de-volta-em-" + completos + "-nos");
        }
        return ResultadoAtuacao.aplicada(codigo, lido.toString());
    }

    /** {@code rebalancing_enabled} da resposta, ou {@code null} sem resposta 200 legivel no prazo. */
    private static Boolean lerCampo(CompletableFuture<ClienteAdminRest.Resposta> f, long limite)
            throws InterruptedException {
        try {
            ClienteAdminRest.Resposta r = ClienteAdminRest.esperar(f, Math.max(1L, limite - System.nanoTime()));
            if (r.codigo() != 200 || r.corpo() == null) {
                return null;
            }
            JsonNode n = JSON.readTree(r.corpo()).get(CAMPO);
            return n != null && n.isBoolean() ? n.booleanValue() : null;
        } catch (IOException | TimeoutException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ AC-5

    private ResultadoAtuacao pararAlvo(AcaoCluster acao, long prazoNs) throws InterruptedException {
        long limite = System.nanoTime() + prazoNs;
        String alvo = acao.alvo();
        if (!nos.containsKey(alvo)) {
            return ResultadoAtuacao.falhou(null, null, "alvo-desconhecido:" + alvo);
        }
        if (!nosQueResponderam.get().contains(alvo)) {
            // risco 3 da secao 3.1: pedido a no pausado pode ficar no buffer e valer quando ele voltar
            return ResultadoAtuacao.falhou(null, null, "alvo-sem-resposta-na-ultima-janela:" + alvo);
        }
        int codigo;
        try {
            ClienteAdminRest.Resposta r = cliente.pedir("POST", uri(alvo, PARAR),
                    Math.max(1L, (limite - System.nanoTime()) / 2));
            codigo = r.codigo();
        } catch (IOException | TimeoutException e) {
            return ResultadoAtuacao.falhou(null, null, "pedido-sem-resposta:" + e.getClass().getSimpleName());
        }
        if (codigo != 204) {
            return ResultadoAtuacao.falhou(codigo, null, "http-" + codigo + "@" + alvo);
        }
        boolean parou = false;
        while (System.nanoTime() < limite) {
            long resta = limite - System.nanoTime();
            if (!saudeResponde(alvo, Math.min(resta, PRAZO_SAUDE_NS))) {
                parou = true;
                break;
            }
            long dorme = Math.min(passoSaudeNs, limite - System.nanoTime());
            if (dorme > 0) {
                Thread.sleep(dorme / 1_000_000L, (int) (dorme % 1_000_000L));
            }
        }
        if (!parou) {
            return ResultadoAtuacao.falhou(codigo, alvo + "=respondendo", "alvo-continua-respondendo");
        }
        StringJoiner lido = new StringJoiner(";");
        lido.add(alvo + "=parou");
        Map<String, CompletableFuture<ClienteAdminRest.Resposta>> outros = new LinkedHashMap<>();
        long resta = Math.max(1L, limite - System.nanoTime());
        for (String no : nos.keySet()) {
            if (!no.equals(alvo)) {
                outros.put(no, cliente.pedirAnonimoAsync("GET", uri(no, SAUDE), resta));
            }
        }
        boolean todos = true;
        for (Map.Entry<String, CompletableFuture<ClienteAdminRest.Resposta>> e : outros.entrySet()) {
            String status = statusDe(e.getValue(), limite);
            lido.add(e.getKey() + "=" + (status == null ? "?" : status));
            if (status == null) {
                todos = false;
            }
        }
        if (!todos) {
            return ResultadoAtuacao.falhou(codigo, lido.toString(), "outro-no-sem-resposta");
        }
        return ResultadoAtuacao.aplicada(codigo, lido.toString());
    }

    /** A saude do no respondeu (qualquer codigo HTTP) dentro de {@code prazoNs}. */
    private boolean saudeResponde(String no, long prazoNs) throws InterruptedException {
        try {
            cliente.pedirAnonimo("GET", uri(no, SAUDE), Math.max(1L, prazoNs));
            return true;
        } catch (IOException | TimeoutException e) {
            return false;
        }
    }

    /** Corpo da saude com 200, sem espacos; {@code null} sem resposta 200 no prazo. */
    private static String statusDe(CompletableFuture<ClienteAdminRest.Resposta> f, long limite)
            throws InterruptedException {
        try {
            ClienteAdminRest.Resposta r = ClienteAdminRest.esperar(f, Math.max(1L, limite - System.nanoTime()));
            if (r.codigo() != 200) {
                return null;
            }
            String s = r.corpo() == null ? "" : r.corpo().strip().replace("\"", "");
            return s.isEmpty() ? "200" : s.replaceAll("[\\s,;=]", "_");
        } catch (IOException | TimeoutException e) {
            return null;
        }
    }

    private URI uri(String no, String caminho) {
        return URI.create(nos.get(no).toString() + caminho);
    }
}
