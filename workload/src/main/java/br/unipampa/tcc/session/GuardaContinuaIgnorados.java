package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * P1-IC (06/11): guarda continua do AC-7. A guarda do adaptador ({@link AtuadorIgnorarCache}) so olha o primeiro segundo
 * depois do POST. No smoke {@code curto-r1} (corte de 24 s, curado antes da deteccao), a gravacao da lista do alvo, que
 * tinha respondido 500 sob o corte, ainda chegou a maioria 7 a 9 s depois da cura, e o cache de sessoes ficou recusado no
 * cluster inteiro ate a regra {@code suspeita-desfeita} da politica (11 a 13 s). Enquanto houver um AC-7 ativo no
 * executor, esta guarda le a cada periodo a lista de caches ignorados dos nos diferentes do alvo (so
 * {@code GET /rest/v2/server/ignored-caches}, leitura local, R5) e, se alguma mostrar o cache, pede o desfazer ao
 * executor, uma vez por acao. O desfazer passa pela fila unica do executor (R2), e o adaptador escolhe a rota (R6/R7).
 *
 * <p>Carencia: nos primeiros {@code carenciaNs} depois de a acao aparecer em {@code ativas()} a guarda nao age (a guarda
 * do adaptador cobre essa janela, e o pedido correria com o POST). Nunca le o alvo (ele pode estar isolado). So e ligada
 * em active.</p>
 */
final class GuardaContinuaIgnorados implements AutoCloseable {

    static final String NOME_THREAD = "controlador-guarda-ac7";
    static final String IGNORADOS = "/rest/v2/server/ignored-caches";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, URI> nos = new LinkedHashMap<>();
    private final ClienteAdminRest cliente;
    private final Supplier<List<AcaoCluster>> ativas;
    private final Consumer<AcaoCluster> desfazer;
    private final LongSupplier relogioNs;
    private final long carenciaNs;
    private final long prazoLeituraNs;

    /** Primeira vez que cada acao (chave) foi vista ativa; so a thread da guarda mexe. */
    private final Map<String, Long> vistaEm = new HashMap<>();
    private final Set<String> pedidas = new HashSet<>();
    private final AtomicLong desfazeres = new AtomicLong();
    private final AtomicLong rodadas = new AtomicLong();
    private volatile boolean parar;
    private Thread thread;

    /**
     * @param ativas    acoes ativas do executor ({@code ExecutorDeAcoes#ativas()})
     * @param desfazer  pede a reversao ao executor (origem {@code GUARDA})
     * @param relogioNs relogio da carencia
     */
    GuardaContinuaIgnorados(List<String> urlsAdmin, ClienteAdminRest cliente, Supplier<List<AcaoCluster>> ativas,
                            Consumer<AcaoCluster> desfazer, LongSupplier relogioNs, long carenciaNs, long prazoLeituraNs) {
        for (String u : urlsAdmin) {
            String base = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
            nos.put(AtuadorRestInfinispan.rotulo(base), URI.create(base));
        }
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.ativas = Objects.requireNonNull(ativas, "ativas");
        this.desfazer = Objects.requireNonNull(desfazer, "desfazer");
        this.relogioNs = Objects.requireNonNull(relogioNs, "relogioNs");
        if (carenciaNs < 0 || prazoLeituraNs <= 0) {
            throw new IllegalArgumentException("carencia >= 0 e prazo de leitura > 0");
        }
        this.carenciaNs = carenciaNs;
        this.prazoLeituraNs = prazoLeituraNs;
    }

    /**
     * Uma rodada: para cada AC-7 ativo fora da carencia e ainda nao pedido, le as listas dos outros nos; se alguma tiver o
     * cache, pede o desfazer.
     *
     * @return quantos desfazeres pediu
     */
    int rodada() throws InterruptedException {
        rodadas.incrementAndGet();
        long agora = relogioNs.getAsLong();
        Set<String> presentes = new HashSet<>();
        int pedidosAgora = 0;
        for (AcaoCluster a : ativas.get()) {
            if (a.tipo() != AcaoCluster.Tipo.AC_7) {
                continue;
            }
            String chave = a.chave();
            presentes.add(chave);
            long desde = vistaEm.computeIfAbsent(chave, k -> agora);
            if (pedidas.contains(chave) || agora - desde < carenciaNs) {
                continue;
            }
            String cache = a.caches().get(0);
            Map<String, CompletableFuture<ClienteAdminRest.Resposta>> f = new LinkedHashMap<>();
            for (Map.Entry<String, URI> e : nos.entrySet()) {
                if (!e.getKey().equals(a.alvo())) {
                    f.put(e.getKey(), cliente.pedirAsync("GET", URI.create(e.getValue() + IGNORADOS), prazoLeituraNs));
                }
            }
            for (CompletableFuture<ClienteAdminRest.Resposta> x : f.values()) {
                if (contem(x, cache)) {
                    pedidas.add(chave);
                    desfazeres.incrementAndGet();
                    pedidosAgora++;
                    desfazer.accept(a);
                    break;
                }
            }
        }
        // acao que saiu de ativas (desfeita): esquece, para uma nova acao com a mesma chave valer de novo
        vistaEm.keySet().retainAll(presentes);
        pedidas.retainAll(presentes);
        return pedidosAgora;
    }

    private boolean contem(CompletableFuture<ClienteAdminRest.Resposta> f, String cache) throws InterruptedException {
        try {
            ClienteAdminRest.Resposta r = ClienteAdminRest.esperar(f, prazoLeituraNs);
            if (r.codigo() != 200 || r.corpo() == null) {
                return false;
            }
            JsonNode n = JSON.readTree(r.corpo());
            if (!n.isArray()) {
                return false;
            }
            for (JsonNode x : n) {
                if (cache.equals(x.asText())) {
                    return true;
                }
            }
            return false;
        } catch (IOException | TimeoutException | RuntimeException e) {
            return false;
        }
    }

    /** Desfazeres pedidos ate agora. */
    long desfazeres() {
        return desfazeres.get();
    }

    long rodadas() {
        return rodadas.get();
    }

    /** Inicia a thread daemon {@value #NOME_THREAD}, que roda {@link #rodada()} a cada {@code periodoNs}. */
    synchronized void iniciar(long periodoNs) {
        if (thread != null) {
            throw new IllegalStateException("guarda ja iniciada");
        }
        Thread t = new Thread(() -> {
            while (!parar) {
                try {
                    rodada();
                    TimeUnit.NANOSECONDS.sleep(periodoNs);
                } catch (InterruptedException e) {
                    return;
                } catch (RuntimeException e) {
                    // a guarda nunca derruba a carga
                }
            }
        }, NOME_THREAD);
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    @Override
    public synchronized void close() {
        parar = true;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
