package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T6: mistura v3 com threads reais sobre um {@link Armazem} de {@link ConcurrentHashMap}.
 * (a) e (b) sao deterministicos (ordem forcada com {@link CountDownLatch}, sem {@code sleep}); (c) e
 * estocastico (4 threads x 2,5 x 10<sup>4</sup> operacoes em S1).
 */
class MisturaV3ConcorrenciaTest {

    private static final long PRAZO_S = 10;

    /** Armazem concorrente com gancho opcional antes de cada get/put. */
    static final class ArmazemConcorrente<V> implements Armazem<V> {
        final ConcurrentHashMap<String, V> dados = new ConcurrentHashMap<>();
        volatile BiConsumer<String, String> antes = (op, chave) -> { };

        @Override
        public V get(String chave) {
            antes.accept("get", chave);
            return dados.get(chave);
        }

        @Override
        public V put(String chave, V valor) {
            antes.accept("put", chave);
            return dados.put(chave, valor);
        }

        @Override
        public Set<Map.Entry<String, V>> entrySet() {
            return dados.entrySet();
        }

        CacheCarregavel carregavel() {
            return new CacheCarregavel() {
                @Override public String nome() { return "sessions"; }
                @Override public void clear() { dados.clear(); }
                @Override public int size() { return dados.size(); }
                @Override public void putAll(Map<String, SessionState> lote) {
                    @SuppressWarnings("unchecked")
                    Map<String, V> m = (Map<String, V>) (Map<String, ?>) lote;
                    dados.putAll(m);
                }
            };
        }
    }

    /** Mundo concorrente: carga de slots, SessionOps real com auditor e desfechos de Validate. */
    static final class Mundo {
        final ArmazemConcorrente<SessionState> sessoes = new ArmazemConcorrente<>();
        final ArmazemConcorrente<IdentityState> identidades = new ArmazemConcorrente<>();
        final InvariantAuditor auditor = new InvariantAuditor();
        final LatencyRegistry latency = new LatencyRegistry();
        final DesfechosValidate desfechos = new DesfechosValidate(WarmupPolicy.always());
        final SessionOps ops;
        final SlotsGeracao slots;
        final MisturaV3 mistura;

        Mundo(int universo, int threads) throws CargaInicialException {
            CargaSlots.carregar(sessoes.carregavel(), universo, threads, Instant.parse("2026-10-22T00:00:00Z"),
                    CargaInicial.LOTE_PADRAO, Duration.ofSeconds(10), Duration.ofMillis(100), () -> 0L, d -> { });
            ops = new SessionOps(sessoes, identidades, latency, auditor, null, null, "fake");
            ops.definirDesfechosValidate(desfechos);
            slots = new SlotsGeracao(universo, threads);
            mistura = new MisturaV3(slots, WarmupPolicy.always());
        }

        long logoutNoop() {
            var h = latency.snapshot().get("logout_noop");
            return h == null ? 0 : h.getTotalCount();
        }
    }

    private static void aguardar(CountDownLatch l) {
        try {
            if (!l.await(PRAZO_S, TimeUnit.SECONDS)) {
                throw new IllegalStateException("prazo do latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Pausa a primeira escrita de {@code chave} ate {@code segue}; sinaliza {@code entrou}. */
    private static BiConsumer<String, String> pausarPrimeiroPut(String chave, CountDownLatch entrou,
                                                                CountDownLatch segue) {
        AtomicBoolean primeiro = new AtomicBoolean(true);
        return (op, c) -> {
            if (op.equals("put") && c.equals(chave) && primeiro.compareAndSet(true, false)) {
                entrou.countDown();
                aguardar(segue);
            }
        };
    }

    private static Thread iniciar(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    @DisplayName("T6a: Validate durante a reposicao le g (INVALID); logo depois do avanco le g+1 (VALID); nunca NOT_FOUND")
    void validateDepoisDoAvanco() throws Exception {
        Mundo w = new Mundo(100, 1);
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch segue = new CountDownLatch(1);
        w.sessoes.antes = pausarPrimeiroPut(SlotsGeracao.chave(7, 2), entrou, segue);
        Thread logout = iniciar(() -> WorkloadMain.logoutV3(w.ops, w.mistura.cliente(42), 7));
        aguardar(entrou);
        // a reposicao de sid-000007~2 esta em curso: a geracao ainda nao pode ter avancado
        OpResult durante = WorkloadMain.validateV3(w.ops, w.mistura.cliente(42), 7, false);
        assertNotEquals(OpResult.Code.NOT_FOUND, durante.code(), "NOT_FOUND durante a reposicao");
        assertEquals(SessionState.Estado.INVALID, durante.estado());
        segue.countDown();
        logout.join(TimeUnit.SECONDS.toMillis(PRAZO_S));
        assertFalse(logout.isAlive());
        OpResult depois = WorkloadMain.validateV3(w.ops, w.mistura.cliente(42), 7, false);
        assertEquals(SessionState.Estado.VALID, depois.estado(), "depois do avanco le a sessao nova");
        assertEquals(0, w.desfechos.notFound());
    }

    @Test
    @DisplayName("T6b: dois Logouts no mesmo slot se serializam; o segundo invalida g+1, nao repete g")
    void logoutsSerializados() throws Exception {
        Mundo w = new Mundo(100, 2);
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch segue = new CountDownLatch(1);
        w.sessoes.antes = pausarPrimeiroPut(SlotsGeracao.chave(7, 2), entrou, segue);
        Thread a = iniciar(() -> WorkloadMain.logoutV3(w.ops, w.mistura.cliente(42), 7));
        aguardar(entrou);
        Thread b = iniciar(() -> WorkloadMain.logoutV3(w.ops, w.mistura.cliente(42), 7));
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(PRAZO_S);
        while (b.getState() != Thread.State.BLOCKED && b.getState() != Thread.State.TERMINATED
                && System.nanoTime() < limite) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.BLOCKED, b.getState(), "o segundo Logout espera a trava do slot");
        segue.countDown();
        a.join(TimeUnit.SECONDS.toMillis(PRAZO_S));
        b.join(TimeUnit.SECONDS.toMillis(PRAZO_S));
        assertEquals(3, w.slots.geracao(7));
        assertEquals(SessionState.Estado.INVALID, w.sessoes.dados.get(SlotsGeracao.chave(7, 1)).estado());
        assertEquals(SessionState.Estado.INVALID, w.sessoes.dados.get(SlotsGeracao.chave(7, 2)).estado());
        assertEquals(SessionState.Estado.VALID, w.sessoes.dados.get(SlotsGeracao.chave(7, 3)).estado());
        assertEquals(0, w.logoutNoop(), "nenhum Logout repetiu a geracao (no-op)");
        assertEquals(2, w.ops.reposicoes());
    }

    @Test
    @DisplayName("T6c: 4 threads x 2,5e4 operacoes em S1: NOT_FOUND = 0, I1 = I4 = 0, geracoes monotonicas, invariante do fim")
    void estocastico() throws Exception {
        int threads = 4;
        int porThread = 25_000;
        Mundo w = new Mundo(KeyGenerator.TAMANHO_PADRAO_UNIVERSO, threads);
        ConcurrentLinkedQueue<String> naoMonotonicas = new ConcurrentLinkedQueue<>();
        // toda escrita de sid-NNNNNN~g VALID (reposicao) acontece com geracao[k] == g - 1
        w.sessoes.antes = (op, c) -> {
            if (op.equals("put") && c.startsWith(KeyGenerator.PREFIXO_SID) && SlotsGeracao.geracaoDe(c) >= 2) {
                int k = SlotsGeracao.slotDe(c);
                int g = SlotsGeracao.geracaoDe(c);
                SessionState atual = w.sessoes.dados.get(c);
                if (atual == null && w.slots.geracao(k) != g - 1) {
                    naoMonotonicas.add(c + " com geracao[k]=" + w.slots.geracao(k));
                }
            }
        };
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        List<Throwable> erros = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            ts.add(iniciar(() -> {
                try {
                    aguardar(largada);
                    WorkloadMain.executarOperacoes(w.ops, Scenario.S1, new Random(42 + tid), new KeyGenerator(42 + tid),
                            new IdentidadesMistura(tid, VersaoMistura.V3), porThread, w.mistura.cliente(42 + tid));
                } catch (Throwable e) {
                    erros.add(e);
                }
            }));
        }
        largada.countDown();
        for (Thread t : ts) {
            t.join(TimeUnit.SECONDS.toMillis(120));
            assertFalse(t.isAlive());
        }
        assertTrue(erros.isEmpty(), erros.toString());
        assertEquals(0, w.desfechos.notFound(), "Validate NOT_FOUND = 0");
        long i1 = 0;
        long i4 = 0;
        for (InvariantAuditor.Violacao v : w.auditor.drenar()) {
            if (v.invariante().equals("I1")) {
                i1++;
            } else if (v.invariante().equals("I4")) {
                i4++;
            }
        }
        assertEquals(0, i1, "I1");
        assertEquals(0, i4, "I4");
        assertTrue(naoMonotonicas.isEmpty(), "geracoes nao monotonicas: " + naoMonotonicas);
        assertEquals(0, w.logoutNoop(), "nenhum Logout no-op");
        assertTrue(w.mistura.logoutIsolados() > 1000);
        assertEquals(w.mistura.logoutIsolados(), w.ops.reposicoes());
        long avancos = 0;
        for (int k = 1; k <= KeyGenerator.TAMANHO_PADRAO_UNIVERSO; k++) {
            int g = w.slots.geracao(k);
            avancos += g - 1;
            assertEquals(SessionState.Estado.VALID, w.sessoes.dados.get(SlotsGeracao.chave(k, g)).estado(), "slot " + k);
            for (int j = 0; j < g; j++) {
                assertEquals(SessionState.Estado.INVALID, w.sessoes.dados.get(SlotsGeracao.chave(k, j)).estado(),
                        "slot " + k + " geracao " + j);
            }
            assertFalse(w.sessoes.dados.containsKey(SlotsGeracao.chave(k, g + 1)), "slot " + k);
        }
        assertEquals(w.mistura.logoutIsolados(), avancos);
        // fracao INVALID entre os Validate isolados: q mais a corrida (pequena com 4 threads em memoria)
        double frac = (double) w.desfechos.invalid() / (w.desfechos.valid() + w.desfechos.invalid());
        assertTrue(frac > 0.0, "INVALID > 0: " + frac);
    }
}
