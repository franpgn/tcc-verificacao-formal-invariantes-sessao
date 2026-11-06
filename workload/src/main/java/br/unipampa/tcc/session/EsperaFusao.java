package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * Complemento 1 do benchmark refeito (DT-027, secao 5, item 3): checagem de fusao antes da varredura final.
 *
 * <p><b>Por que.</b> O laco de convergencia confere I5/I6 e a topologia do cliente, nao a vista de cada no, e a
 * varredura podia rodar com o no da particao ainda fora (relatorio da A1, achado 2). Com
 * {@code --sweep-merge-timeout-sec T}, antes de varrer o programa le a saude REST
 * ({@code GET /rest/v2/cache-managers/default/health}) de cada no de {@code --controller-admin}, em paralelo, e so
 * deixa a varredura rodar quando os nos mostram <b>a mesma vista com todos os membros</b>: todos respondem,
 * {@code cluster_health.number_of_nodes} = numero de nos e o mesmo conjunto {@code node_names} com esse tamanho.</p>
 *
 * <p><b>Teto.</b> A espera vai ate o marcador {@code end} mais T, com uma leitura a cada {@link #PASSO_NS} (2 s, o
 * periodo da sonda de topologia do controlador: nenhum numero novo) e ao menos uma leitura; sem {@code end} (celula
 * sem falha), uma leitura so. O valor de T vem do arquivo de parametros da campanha ({@code fusao_teto_s}, com a
 * origem; DT-027): 72 s = 1,5 x 48 s, o maior tempo de fusao medido depois do {@code end} da F2 (+42,2 a +47,8 s nas
 * 18 execucoes F2 do X-18 e do X-19, sonda de topologia), com a folga de 1,5 do {@code exec_teto_s}. O conferidor do
 * orquestrador exige que o prazo venca antes do fim da carga, entao, quando a varredura comeca (na VM, ~154 s depois
 * do {@code end}), ele ja passou e a espera custa uma leitura.</p>
 *
 * <p><b>Saidas.</b> {@code fusao-rep-NNN.json} (o registro: fundiu, {@code t_vista_ns}, leituras, prazo e a ultima
 * leitura de cada no) e a linha {@code Fusao antes da varredura:} no stdout. Sem fusao no prazo, a varredura nao
 * roda e {@code varredura-rep-NNN.erro.txt} traz o motivo (o tratamento simetrico do W-5d cobre a varredura com erro).
 * O orquestrador copia {@code t_vista_ns} para o {@code execucao.json} como {@code t_vista_checagem_ns}, so como
 * registro: e o instante em que esta checagem <i>viu</i> a vista de fusao (na VM, ~154 s depois do {@code end}), e nao o
 * da fusao; o {@code t_fusao_ns} sai da trilha de observaveis do controlador (P-61). Sem a opcao, nada disso acontece e
 * a varredura roda como antes. So GET de saude: nenhuma acao no cluster, nenhum processo.</p>
 */
final class EsperaFusao {

    /** Intervalo entre as leituras: o periodo da sonda de topologia do controlador (2 s, B-26.2). */
    static final long PASSO_NS = LigacaoControlador.PERIODO_SONDA_NS;

    private static final ObjectMapper JSON = new ObjectMapper();

    private EsperaFusao() {
    }

    /** A vista de um no: respondeu, {@code number_of_nodes} e {@code node_names} (ordenados), ou o erro. */
    record Vista(String url, boolean respondeu, int numeroDeNos, List<String> membros, String erro) {
        Vista {
            membros = membros == null ? List.of() : List.copyOf(membros);
        }

        static Vista semResposta(String url, String erro) {
            return new Vista(url, false, 0, List.of(), erro);
        }

        String texto() {
            return respondeu ? url + " number_of_nodes=" + numeroDeNos + " membros=" + membros
                             : url + " sem resposta (" + erro + ")";
        }

        Map<String, Object> mapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", url);
            m.put("respondeu", respondeu);
            m.put("number_of_nodes", respondeu ? numeroDeNos : null);
            m.put("node_names", membros);
            m.put("erro", erro);
            return m;
        }
    }

    /** Le a vista de cada URL (uma leitura de todos os nos); nunca lanca por falha de um no. */
    @FunctionalInterface
    interface FonteVistas {
        List<Vista> ler(List<String> urls) throws InterruptedException;
    }

    /**
     * Resultado da espera.
     *
     * @param tFimFalhaNs marcador {@code end} (epoch ns) ou {@code null}
     * @param prazoNs     {@code end + teto}; sem {@code end}, o inicio (uma leitura)
     * @param tVistaNs    instante (epoch ns, depois da leitura) em que a vista de fusao foi vista; {@code null} sem ela
     */
    record Resultado(boolean fundiu, Long tFimFalhaNs, long tetoNs, long inicioNs, long prazoNs, Long tVistaNs,
                     int leituras, List<Vista> ultima) {

        Double desdeEndS() {
            return tVistaNs == null || tFimFalhaNs == null ? null : (tVistaNs - tFimFalhaNs) / 1e9;
        }

        long tetoS() {
            return TimeUnit.NANOSECONDS.toSeconds(tetoNs);
        }

        String linha() {
            Double d = desdeEndS();
            return "Fusao antes da varredura: fundiu=" + (fundiu ? "sim" : "nao")
                    + " desde_end_s=" + (d == null ? "-" : String.format(Locale.ROOT, "%.3f", d))
                    + " leituras=" + leituras + " teto_s=" + tetoS()
                    + " prazo=" + (tFimFalhaNs == null ? "uma_leitura(sem_end)" : "end+" + tetoS() + "s")
                    + " vista=" + (ultima.isEmpty() ? "[]" : ultima.get(0).membros());
        }

        String motivo() {
            int n = ultima.size();
            String quando = tFimFalhaNs == null ? "na leitura unica (sem marcador end)"
                    : "ate end + " + tetoS() + " s";
            List<String> partes = new ArrayList<>();
            for (Vista v : ultima) {
                partes.add(v.texto());
            }
            return "sem fusao antes da varredura: os " + n + " nos nao mostraram a mesma vista de " + n + " membros "
                    + quando + " (" + leituras + " leitura(s); ultima: " + String.join("; ", partes) + ")";
        }

        String json() throws IOException {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fundiu", fundiu);
            m.put("teto_s", tetoS());
            m.put("t_fim_falha_ns", tFimFalhaNs);
            m.put("prazo_ns", prazoNs);
            m.put("inicio_ns", inicioNs);
            m.put("t_vista_ns", tVistaNs);
            m.put("desde_end_s", desdeEndS());
            m.put("leituras", leituras);
            m.put("passo_ns", PASSO_NS);
            List<Map<String, Object>> u = new ArrayList<>();
            for (Vista v : ultima) {
                u.add(v.mapa());
            }
            m.put("ultima_leitura", u);
            m.put("criterio", "todos respondem, number_of_nodes = numero de nos e o mesmo conjunto node_names desse "
                    + "tamanho (DT-027 secao 5, item 3)");
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(m) + System.lineSeparator();
        }
    }

    /** Interpreta a saude REST de um no; corpo que nao e a saude vira leitura sem resposta. */
    static Vista interpretar(String url, String corpo) {
        try {
            JsonNode raiz = JSON.readTree(corpo);
            JsonNode c = raiz == null ? null : raiz.get("cluster_health");
            if (c == null || !c.has("number_of_nodes")) {
                return Vista.semResposta(url, "resposta de saude sem cluster_health.number_of_nodes");
            }
            TreeSet<String> nomes = new TreeSet<>();
            JsonNode ns = c.get("node_names");
            if (ns != null && ns.isArray()) {
                for (JsonNode x : ns) {
                    nomes.add(x.asText());
                }
            }
            return new Vista(url, true, c.get("number_of_nodes").asInt(), new ArrayList<>(nomes), null);
        } catch (IOException | RuntimeException e) {
            return Vista.semResposta(url, "resposta de saude ilegivel: " + e.getMessage());
        }
    }

    /** Os {@code esperados} nos respondem, cada um com {@code esperados} nos e o mesmo conjunto de membros. */
    static boolean mesmaVistaCompleta(List<Vista> vistas, int esperados) {
        if (vistas.size() != esperados || esperados <= 0) {
            return false;
        }
        TreeSet<String> ref = null;
        for (Vista v : vistas) {
            TreeSet<String> m = new TreeSet<>(v.membros());
            if (!v.respondeu() || v.numeroDeNos() != esperados || m.size() != esperados) {
                return false;
            }
            if (ref == null) {
                ref = m;
            } else if (!ref.equals(m)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Le ate a fusao ou ate o prazo ({@code tFimFalhaNs + tetoNs}; sem {@code end}, o inicio), com ao menos uma leitura
     * e uma a cada {@code passoNs} (o ultimo sono e cortado no prazo).
     */
    static Resultado esperar(List<String> urls, FonteVistas fonte, ConvergenciaLoop.Relogio relogio, Long tFimFalhaNs,
                             long tetoNs, long passoNs) throws InterruptedException {
        long inicio = relogio.agoraNs();
        long prazo = tFimFalhaNs == null ? inicio : tFimFalhaNs + tetoNs;
        int leituras = 0;
        while (true) {
            List<Vista> ultima = fonte.ler(urls);
            leituras++;
            long agora = relogio.agoraNs();
            if (mesmaVistaCompleta(ultima, urls.size())) {
                return new Resultado(true, tFimFalhaNs, tetoNs, inicio, prazo, agora, leituras, ultima);
            }
            if (agora >= prazo) {
                return new Resultado(false, tFimFalhaNs, tetoNs, inicio, prazo, null, leituras, ultima);
            }
            relogio.dormirNs(Math.min(passoNs, prazo - agora));
        }
    }

    /** Fonte de producao: GET da saude em cada no, em paralelo, com o prazo de cada pedido (5 s, o do HealthRest). */
    static FonteVistas rest(ClienteAdminRest cliente, long prazoNs) {
        return urls -> {
            List<CompletableFuture<ClienteAdminRest.Resposta>> fs = new ArrayList<>();
            for (String u : urls) {
                fs.add(cliente.pedirAsync("GET", URI.create(u + HealthRest.ENDPOINT), prazoNs));
            }
            List<Vista> out = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                String u = urls.get(i);
                try {
                    ClienteAdminRest.Resposta r = ClienteAdminRest.esperar(fs.get(i), prazoNs);
                    out.add(r.sucesso() ? interpretar(u, r.corpo()) : Vista.semResposta(u, "HTTP " + r.codigo()));
                } catch (IOException e) {
                    out.add(Vista.semResposta(u, String.valueOf(e.getMessage())));
                } catch (TimeoutException e) {
                    out.add(Vista.semResposta(u, "prazo de " + TimeUnit.NANOSECONDS.toMillis(prazoNs) + " ms"));
                }
            }
            return out;
        };
    }

    /**
     * Antes da varredura: sem {@code --sweep-merge-timeout-sec}, {@code true} sem ler nada. Com ela, espera a fusao,
     * grava {@code fusao-rep-NNN.json} e a linha do stdout; sem fusao, grava {@code varredura-rep-NNN.erro.txt} e
     * devolve {@code false} (a varredura nao roda).
     */
    static boolean antesDaVarredura(Cli cli, int rep, Long tFimFalhaNs, FonteVistas fonte,
                                    ConvergenciaLoop.Relogio relogio) throws IOException {
        if (cli.varreduraFusaoTetoSeg == null) {
            return true;
        }
        Resultado r;
        try {
            r = esperar(LigacaoControlador.urlsAdmin(cli.controladorAdmin), fonte, relogio, tFimFalhaNs,
                    TimeUnit.SECONDS.toNanos(cli.varreduraFusaoTetoSeg), PASSO_NS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("checagem de fusao interrompida", e);
        }
        System.out.println(r.linha());
        boolean csv = cli.csvDir != null && !cli.csvDir.isBlank();
        if (csv) {
            Files.createDirectories(Path.of(cli.csvDir));
            Files.writeString(Path.of(cli.csvDir, String.format("fusao-rep-%03d.json", rep)), r.json(),
                    StandardCharsets.UTF_8);
        }
        if (r.fundiu()) {
            return true;
        }
        System.err.println("VARREDURA NAO EXECUTADA (" + r.motivo() + ")");
        if (csv) {
            Files.writeString(Path.of(cli.csvDir, String.format("varredura-rep-%03d.erro.txt", rep)),
                    r.motivo() + System.lineSeparator(), StandardCharsets.UTF_8);
        }
        return false;
    }

    /** Producao: as URLs de {@code --controller-admin}, o cliente REST em memoria e o relogio do programa. */
    static boolean antesDaVarredura(Cli cli, int rep, Long tFimFalhaNs, EpochClock ancora) throws IOException {
        if (cli.varreduraFusaoTetoSeg == null) {
            return true;
        }
        return antesDaVarredura(cli, rep, tFimFalhaNs,
                rest(new ClienteAdminRest(cli.usuario, cli.senha), HealthRest.PRAZO_CONSULTA_NS),
                ConvergenciaLoop.Relogio.sistema(ancora));
    }
}
