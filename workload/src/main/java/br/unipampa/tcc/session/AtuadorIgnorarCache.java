package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.AtuadorCluster;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * P1-IC (06/11, G-1 a G-3 do coordenador): adaptador do {@link AcaoCluster.Tipo#AC_7}, o no suspeito passa a ignorar o
 * cache de sessoes. So pela REST de administracao do Infinispan 15.0.21, sobre {@link ClienteAdminRest} (nenhum
 * cliente Hot Rod, processo ou arquivo: R-ARQ-1). Regras do {@code docs/relatorio-a4-2026-11-06.md}, secao 8:
 *
 * <ul>
 *   <li><b>R1</b> um cache por pedido (o {@link AcaoCluster} do AC-7 ja exige um so);</li>
 *   <li><b>R2</b> um pedido de {@code ignored-caches} por vez: antes de qualquer POST ou DELETE, espera a resposta do
 *       anterior (sob corte, o POST so responde com o 500 do {@code remote-timeout} de 15 s do cache de estado). O
 *       pedido pendente nao e cancelado: o servidor continua a gravacao de qualquer forma;</li>
 *   <li><b>R3</b> efeito conferido pela lista ({@code GET /rest/v2/server/ignored-caches} do alvo, leitura local), nao
 *       pelo codigo HTTP: sob corte o 500 depois de 15 s e o resultado normal, e o 204 nao garante o efeito;</li>
 *   <li><b>R4</b> guarda de falso alarme: durante {@link Parametros#janelaGuardaNs()} depois do POST, le a lista dos
 *       outros nos; se algum mostrar o cache, a gravacao replicou (o alvo nao estava isolado) e o adaptador desfaz na
 *       hora e devolve {@code FALHOU} com {@value #FALSO_ALARME_DESFEITO};</li>
 *   <li><b>R5</b> nenhum pedido a {@code /rest/v2/container}: no no isolado ele prende uma thread da REST esperando o
 *       cluster (P-A4.3);</li>
 *   <li><b>R6</b> desfazer no proprio alvo quando ele esta em {@code DEGRADED_MODE} ({@code ?action=get-availability},
 *       leitura local): a gravacao nao alcanca ninguem e a lista dele esvazia;</li>
 *   <li><b>R7</b> nos outros casos, um DELETE a um no da maioria que respondeu na ultima janela: a gravacao da lista
 *       dele (sem o cache) chega ao alvo pelo ouvinte e zera a lista do alvo de uma vez. Se o alvo continuar com o cache
 *       depois de {@link Parametros#esperaOuvinteNs()} (corte ainda nao detectado, por exemplo no encerramento), vai um
 *       DELETE ao alvo tambem, em sequencia;</li>
 *   <li><b>R8</b> reverter e idempotente: sem o cache em nenhuma lista (e com a do alvo lida), nenhum pedido.</li>
 * </ul>
 *
 * <p>Chamado so pela thread executora do {@code ExecutorDeAcoes}; o estado do pedido pendente nao precisa de trava. O
 * prazo recebido limita a chamada inteira; o pedido pendente pode passar dele (ele tem o proprio prazo HTTP).</p>
 *
 * <p>{@code lido} da linha {@code acao} (separado por {@code ;}): {@code anterior=METODO:codigo@ms} (pedido pendente
 * que respondeu nesta chamada), {@code efeito_ms} (do envio do POST a lista do alvo com o cache), {@code guarda}
 * ({@code limpa_<leituras>x_<ms>} ou {@code falso-alarme@<no>_<ms>}), {@code post} (codigo ou {@code pendente}),
 * {@code rota} do desfazer ({@code alvo-sozinho}, {@code maioria@<no>}, {@code +alvo}) e as listas lidas
 * ({@code <no>=<caches>}, {@code -} vazia, {@code ?} sem leitura).</p>
 */
public final class AtuadorIgnorarCache implements AtuadorCluster {

    public static final String NOME = "rest-ignored-caches";

    static final String IGNORADOS = "/rest/v2/server/ignored-caches";
    static final String CACHES = "/rest/v2/caches/";
    static final String DISPONIBILIDADE = "?action=get-availability";
    static final String DEGRADED = "DEGRADED_MODE";

    static final String FALSO_ALARME_DESFEITO = "falso-alarme-desfeito";
    static final String EFEITO_NAO_CONFIRMADO = "efeito-nao-confirmado";
    static final String PENDENTE = "pedido-anterior-pendente";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MS = 1_000_000L;

    /**
     * Tempos do adaptador.
     *
     * @param janelaGuardaNs  quanto tempo, depois do POST, a lista dos outros nos e lida (R4)
     * @param passoNs         intervalo entre leituras
     * @param prazoEfeitoNs   prazo para ver o cache na lista do alvo depois do POST (R3)
     * @param prazoLeituraNs  prazo de cada GET
     * @param esperaOuvinteNs espera pela resposta do DELETE e, no R7, pela lista do alvo antes do DELETE no alvo
     */
    public record Parametros(long janelaGuardaNs, long passoNs, long prazoEfeitoNs, long prazoLeituraNs,
                             long esperaOuvinteNs) {

        /**
         * Producao. Guarda de 1 s: no A4, sem corte, a lista da maioria mostrou o cache entre 30 e 157 ms (vigia de
         * 100 ms) e a primeira recusa da maioria veio entre 22 e 60 ms; sob corte, nunca (8 execucoes). Leitura a cada
         * 20 ms (o efeito no alvo veio entre 16 e 70 ms). Efeito em ate 2 s, GET de 500 ms, ouvinte de 1 s (o maior
         * atraso do alvo depois do DELETE pela maioria foi ~1,08 s, com o alvo parado ~1 s antes do pedido).
         */
        public static final Parametros PADRAO = new Parametros(1_000 * MS, 20 * MS, 2_000 * MS, 500 * MS, 1_000 * MS);

        public Parametros {
            if (janelaGuardaNs <= 0 || passoNs <= 0 || prazoEfeitoNs <= 0 || prazoLeituraNs <= 0 || esperaOuvinteNs <= 0) {
                throw new IllegalArgumentException("tempos do adaptador devem ser > 0");
            }
        }
    }

    private final Map<String, URI> nos;
    private final ClienteAdminRest cliente;
    private final Supplier<Set<String>> nosQueResponderam;
    private final Parametros par;

    // R2: o ultimo pedido de ignored-caches, enquanto nao respondeu (so a thread executora mexe)
    private CompletableFuture<ClienteAdminRest.Resposta> pendente;
    private String pendenteMetodo;
    private long pendenteIniNs;
    private Integer ultimoCodigo;

    /**
     * @param urlsAdmin         {@code http://host:porta} de cada no, na ordem de {@code --controller-admin}
     * @param cliente           cliente HTTP autenticado
     * @param nosQueResponderam rotulos dos nos que responderam a REST na ultima janela (da sonda de topologia)
     */
    public AtuadorIgnorarCache(List<String> urlsAdmin, ClienteAdminRest cliente, Supplier<Set<String>> nosQueResponderam,
                               Parametros parametros) {
        if (urlsAdmin == null || urlsAdmin.isEmpty()) {
            throw new IllegalArgumentException("lista de URLs vazia");
        }
        Map<String, URI> m = new LinkedHashMap<>();
        for (String u : urlsAdmin) {
            String base = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
            if (m.put(AtuadorRestInfinispan.rotulo(base), URI.create(base)) != null) {
                throw new IllegalArgumentException("URL repetida: " + u);
            }
        }
        this.nos = m;
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.nosQueResponderam = Objects.requireNonNull(nosQueResponderam, "nosQueResponderam");
        this.par = Objects.requireNonNull(parametros, "parametros");
    }

    @Override
    public String nome() {
        return NOME;
    }

    // ------------------------------------------------------------------ aplicar

    @Override
    public ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) throws InterruptedException {
        if (acao.tipo() != AcaoCluster.Tipo.AC_7) {
            return ResultadoAtuacao.falhou(null, null, "tipo-sem-rota:" + acao.tipo().rotulo());
        }
        long limite = System.nanoTime() + prazoNs;
        String alvo = acao.alvo();
        String cache = acao.caches().get(0);
        if (!nos.containsKey(alvo)) {
            return ResultadoAtuacao.falhou(null, null, "alvo-desconhecido:" + alvo);
        }
        if (!nosQueResponderam.get().contains(alvo)) {
            // risco 3 da secao 3.1 de 05/11: pedido a no pausado pode ficar no buffer e valer quando ele voltar
            return ResultadoAtuacao.falhou(null, null, "alvo-sem-resposta-na-ultima-janela:" + alvo);
        }
        StringJoiner lido = new StringJoiner(";");
        if (!esperarPendente(limite, lido)) {
            return ResultadoAtuacao.falhou(null, lido.toString(), PENDENTE);
        }
        long t0 = System.nanoTime();
        enviar("POST", alvo, cache, limite);

        // R3 (efeito pela lista do alvo) e R4 (guarda: listas dos outros nos) no mesmo laco; a guarda le ao menos uma vez
        Long efeitoNs = null;
        String falso = null;
        int leituras = 0;
        long fimEfeito = Math.min(limite, t0 + par.prazoEfeitoNs());
        long fimGuarda = Math.min(limite, t0 + par.janelaGuardaNs());
        while (true) {
            long agora = System.nanoTime();
            boolean efeito = efeitoNs == null && agora < fimEfeito;
            boolean guarda = agora < fimGuarda || leituras == 0;
            if (!efeito && !guarda) {
                break;
            }
            if (efeito) {
                Set<String> l = lerLista(alvo, limite);
                if (l != null && l.contains(cache)) {
                    efeitoNs = System.nanoTime() - t0;
                }
                Integer cod = codigoSeRespondeu();
                if (cod != null && cod >= 400 && cod < 500) {
                    esperarPendente(limite, lido);
                    return ResultadoAtuacao.falhou(cod, lido.toString(), "http-" + cod + "@" + alvo);
                }
            }
            if (guarda) {
                Map<String, Set<String>> outros = lerListas(alvo, false, limite);
                leituras++;
                for (Map.Entry<String, Set<String>> e : outros.entrySet()) {
                    if (e.getValue() != null && e.getValue().contains(cache)) {
                        falso = e.getKey();
                        break;
                    }
                }
                if (falso != null) {
                    break;
                }
            }
            dormir(par.passoNs(), Math.max(fimEfeito, fimGuarda));
        }
        lido.add(efeitoNs == null ? "efeito=nao-visto" : "efeito_ms=" + efeitoNs / MS);
        if (falso != null) {
            lido.add("guarda=falso-alarme@" + falso + "_" + (System.nanoTime() - t0) / MS + "ms");
            // R2: o DELETE espera a resposta do POST (sem corte, o POST responde 204 em dezenas de ms)
            if (!esperarPendente(limite, lido)) {
                return ResultadoAtuacao.falhou(null, lido.toString(), "falso-alarme-nao-desfeito:" + PENDENTE);
            }
            Integer codPost = ultimoCodigo;
            ResultadoAtuacao d = desfazer(cache, alvo, limite, lido);
            return ResultadoAtuacao.falhou(codPost, d.lidoDeVolta(), d.estado() == ResultadoAtuacao.Estado.APLICADA
                    ? FALSO_ALARME_DESFEITO : "falso-alarme-nao-desfeito:" + d.erro());
        }
        lido.add("guarda=limpa_" + leituras + "x_" + (System.nanoTime() - t0) / MS + "ms");
        Integer cod = codigoSeRespondeu();
        lido.add(cod == null ? "post=pendente" : "post=" + cod);
        if (cod != null) {
            esperarPendente(limite, lido); // ja respondeu: so registra
        }
        if (efeitoNs == null) {
            return ResultadoAtuacao.falhou(cod, lido.toString(), EFEITO_NAO_CONFIRMADO);
        }
        return ResultadoAtuacao.aplicada(cod, lido.toString());
    }

    // ------------------------------------------------------------------ reverter

    @Override
    public ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) throws InterruptedException {
        if (acao.tipo() != AcaoCluster.Tipo.AC_7) {
            return ResultadoAtuacao.falhou(null, null, "tipo-sem-rota:" + acao.tipo().rotulo());
        }
        long limite = System.nanoTime() + prazoNs;
        String alvo = acao.alvo();
        if (!nos.containsKey(alvo)) {
            return ResultadoAtuacao.falhou(null, null, "alvo-desconhecido:" + alvo);
        }
        StringJoiner lido = new StringJoiner(";");
        if (!esperarPendente(limite, lido)) {
            return ResultadoAtuacao.falhou(null, lido.toString(), PENDENTE);
        }
        return desfazer(acao.caches().get(0), alvo, limite, lido);
    }

    /** R6/R7/R8; {@code lido} ja traz o que veio antes (pedido pendente, guarda). */
    private ResultadoAtuacao desfazer(String cache, String alvo, long limite, StringJoiner antes)
            throws InterruptedException {
        Map<String, Set<String>> listas = lerListas(alvo, true, limite);
        boolean alguem = listas.values().stream().anyMatch(l -> l != null && l.contains(cache));
        if (!alguem && listas.get(alvo) != null) {
            return ResultadoAtuacao.aplicada(null, juntar(antes, "nada-a-desfazer;" + formatar(listas)));
        }
        String destino;
        String rota;
        if (DEGRADED.equals(disponibilidade(alvo, cache, limite))) {
            destino = alvo; // R6: o alvo esta sozinho; a gravacao nao alcanca ninguem
            rota = "alvo-sozinho";
        } else {
            destino = noDaMaioria(alvo);
            rota = destino.equals(alvo) ? "alvo" : "maioria@" + destino;
        }
        enviar("DELETE", destino, cache, limite);
        Integer cod = esperarResposta(Math.min(limite, System.nanoTime() + par.esperaOuvinteNs()), antes);
        long tEnvio = System.nanoTime();
        boolean segundo = false;
        while (true) {
            listas = lerListas(alvo, true, limite);
            Set<String> doAlvo = listas.get(alvo);
            boolean ok = rota.equals("alvo-sozinho") || rota.equals("alvo")
                    ? doAlvo != null && !doAlvo.contains(cache)
                    : doAlvo != null && listas.values().stream().noneMatch(l -> l != null && l.contains(cache));
            if (ok) {
                return ResultadoAtuacao.aplicada(cod, juntar(antes, "rota=" + rota + ";" + formatar(listas)));
            }
            if (System.nanoTime() >= limite) {
                break;
            }
            if (!segundo && rota.startsWith("maioria@") && System.nanoTime() - tEnvio >= par.esperaOuvinteNs()
                    && doAlvo != null && doAlvo.contains(cache)) {
                // R7 sob corte nao detectado: a gravacao da maioria nao alcanca o alvo; DELETE no alvo, depois da
                // resposta do primeiro (R2)
                if (!esperarPendente(limite, antes)) {
                    break;
                }
                enviar("DELETE", alvo, cache, limite);
                esperarResposta(Math.min(limite, System.nanoTime() + par.esperaOuvinteNs()), antes);
                rota = rota + "+alvo";
                segundo = true;
                continue;
            }
            dormir(par.passoNs(), limite);
        }
        return ResultadoAtuacao.falhou(cod, juntar(antes, "rota=" + rota + ";" + formatar(listas)),
                "desfazer-nao-confirmado");
    }

    /** O primeiro no (na ordem de {@code --controller-admin}) diferente do alvo que respondeu na ultima janela. */
    private String noDaMaioria(String alvo) {
        Set<String> responderam = nosQueResponderam.get();
        String reserva = null;
        for (String no : nos.keySet()) {
            if (no.equals(alvo)) {
                continue;
            }
            if (responderam.contains(no)) {
                return no;
            }
            if (reserva == null) {
                reserva = no;
            }
        }
        return reserva == null ? alvo : reserva;
    }

    // ------------------------------------------------------------------ pedidos (R2)

    private void enviar(String metodo, String no, String cache, long limite) {
        pendenteMetodo = metodo + "@" + no;
        pendenteIniNs = System.nanoTime();
        ultimoCodigo = null;
        pendente = cliente.pedirAsync(metodo, uri(no, IGNORADOS + "/" + cache), Math.max(MS, limite - pendenteIniNs));
    }

    /** Codigo do pedido pendente se ele ja respondeu; {@code null} se nao respondeu ou falhou sem codigo. */
    private Integer codigoSeRespondeu() {
        CompletableFuture<ClienteAdminRest.Resposta> f = pendente;
        if (f == null || !f.isDone() || f.isCompletedExceptionally()) {
            return null;
        }
        return f.join().codigo();
    }

    /**
     * R2: espera o pedido pendente responder ate {@code limite}, sem cancelar. Registra {@code anterior=...} em
     * {@code lido}.
     *
     * @return {@code true} se nao ha pedido pendente
     */
    private boolean esperarPendente(long limite, StringJoiner lido) throws InterruptedException {
        if (pendente == null) {
            return true;
        }
        return esperarResposta(limite, lido) != null || pendente == null;
    }

    /** Espera o pendente ate {@code limite}; ao responder, registra e o limpa. Devolve o codigo, ou {@code null}. */
    private Integer esperarResposta(long limite, StringJoiner lido) throws InterruptedException {
        CompletableFuture<ClienteAdminRest.Resposta> f = pendente;
        if (f == null) {
            return ultimoCodigo;
        }
        String registro;
        Integer codigo = null;
        try {
            ClienteAdminRest.Resposta r = f.get(Math.max(1L, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
            codigo = r.codigo();
            registro = String.valueOf(codigo);
        } catch (TimeoutException e) {
            return null; // continua pendente; nao cancela (o servidor segue gravando de qualquer forma)
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            registro = c == null ? "falha" : c.getClass().getSimpleName();
        }
        lido.add("anterior=" + pendenteMetodo.substring(0, pendenteMetodo.indexOf('@')) + ":" + registro + "@"
                + (System.nanoTime() - pendenteIniNs) / MS + "ms");
        pendente = null;
        ultimoCodigo = codigo;
        return codigo == null ? -1 : codigo;
    }

    // ------------------------------------------------------------------ leituras (R5: so locais)

    private Set<String> lerLista(String no, long limite) throws InterruptedException {
        return lista(cliente.pedirAsync("GET", uri(no, IGNORADOS), prazoLeitura(limite)), limite);
    }

    /** Listas de todos os nos (com o alvo) ou so dos outros, em paralelo; {@code null} = sem leitura. */
    private Map<String, Set<String>> lerListas(String alvo, boolean comAlvo, long limite) throws InterruptedException {
        Map<String, CompletableFuture<ClienteAdminRest.Resposta>> f = new LinkedHashMap<>();
        long p = prazoLeitura(limite);
        for (String no : nos.keySet()) {
            if (comAlvo || !no.equals(alvo)) {
                f.put(no, cliente.pedirAsync("GET", uri(no, IGNORADOS), p));
            }
        }
        Map<String, Set<String>> m = new LinkedHashMap<>();
        for (Map.Entry<String, CompletableFuture<ClienteAdminRest.Resposta>> e : f.entrySet()) {
            m.put(e.getKey(), lista(e.getValue(), limite));
        }
        return m;
    }

    private Set<String> lista(CompletableFuture<ClienteAdminRest.Resposta> f, long limite) throws InterruptedException {
        try {
            ClienteAdminRest.Resposta r = ClienteAdminRest.esperar(f, Math.max(1L,
                    Math.min(par.prazoLeituraNs(), limite - System.nanoTime())));
            if (r.codigo() != 200 || r.corpo() == null) {
                return null;
            }
            JsonNode n = JSON.readTree(r.corpo());
            if (!n.isArray()) {
                return null;
            }
            Set<String> s = new TreeSet<>();
            n.forEach(x -> s.add(x.asText()));
            return s;
        } catch (IOException | TimeoutException | RuntimeException e) {
            return null;
        }
    }

    /** {@code ?action=get-availability} do cache no no (leitura local), sem aspas; {@code null} sem resposta 200. */
    private String disponibilidade(String no, String cache, long limite) throws InterruptedException {
        try {
            ClienteAdminRest.Resposta r = cliente.pedir("GET", uri(no, CACHES + cache + DISPONIBILIDADE),
                    prazoLeitura(limite));
            return r.codigo() == 200 && r.corpo() != null ? r.corpo().strip().replace("\"", "") : null;
        } catch (IOException | TimeoutException e) {
            return null;
        }
    }

    private long prazoLeitura(long limite) {
        return Math.max(MS, Math.min(par.prazoLeituraNs(), limite - System.nanoTime()));
    }

    private static void dormir(long ns, long limite) throws InterruptedException {
        long d = Math.min(ns, limite - System.nanoTime());
        if (d > 0) {
            Thread.sleep(d / MS, (int) (d % MS));
        }
    }

    private static String formatar(Map<String, Set<String>> listas) {
        StringJoiner j = new StringJoiner(";");
        listas.forEach((no, l) -> j.add(no + "=" + (l == null ? "?" : l.isEmpty() ? "-" : String.join("+", l))));
        return j.toString();
    }

    private static String juntar(StringJoiner antes, String depois) {
        String a = antes.toString();
        return a.isEmpty() ? depois : a + ";" + depois;
    }

    private URI uri(String no, String caminho) {
        return URI.create(nos.get(no).toString() + caminho);
    }
}
