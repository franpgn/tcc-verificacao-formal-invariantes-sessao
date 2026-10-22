package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.6 (achado A1 da V-B43.2): o auditor de I1/I4 em {@link SessionOps} comparava a leitura com o
 * estado ATUAL do mapa de invalidacoes confirmadas, consultado DEPOIS de a leitura terminar. Uma
 * leitura concorrente com a primeira invalidacao (iniciada antes da confirmacao) virava I1 falsa.
 *
 * <p>Regra: uma leitura so pode ser acusada de I1/I4 contra uma confirmacao que ja existia ANTES de a
 * leitura comecar. A corrida (confirmacao surgida durante a leitura) e contada em
 * {@code i1Concorrentes}/{@code i4Concorrentes}, nao em violacao. A ordem dos eventos e forcada com
 * {@link CountDownLatch}, sem cluster e sem {@code sleep}.</p>
 */
class AuditorCorridaTest {

    private static final long PRAZO_S = 10;
    private static final String SID = "sid-000004";
    private static final String USUARIO = "u4";

    /**
     * {@link Armazem} sobre um {@link FakeCaches.Cache} que, para a thread {@link #alvo}, pausa logo
     * DEPOIS do primeiro {@code get} (o valor ja foi lido) ate {@link #segue} ser liberado.
     */
    static final class ArmazemPausavel<V> implements Armazem<V> {
        final FakeCaches.Cache<V> base;
        volatile Thread alvo;
        final CountDownLatch leu = new CountDownLatch(1);
        final CountDownLatch segue = new CountDownLatch(1);

        ArmazemPausavel(FakeCaches.Cache<V> base) {
            this.base = base;
        }

        @Override
        public V get(String chave) {
            V v = base.get(chave);
            if (Thread.currentThread() == alvo) {
                alvo = null;
                leu.countDown();
                aguardar(segue);
            }
            return v;
        }

        @Override
        public V put(String chave, V valor) {
            return base.put(chave, valor);
        }

        @Override
        public Set<Map.Entry<String, V>> entrySet() {
            return base.entrySet();
        }
    }

    /** {@link Armazem} "velho": enquanto {@link #versaoVelha} nao for nula, o {@code get} devolve ela. */
    static final class ArmazemVelho<V> implements Armazem<V> {
        final FakeCaches.Cache<V> base;
        volatile V versaoVelha;

        ArmazemVelho(FakeCaches.Cache<V> base) {
            this.base = base;
        }

        @Override
        public V get(String chave) {
            V v = versaoVelha;
            return v != null ? v : base.get(chave);
        }

        @Override
        public V put(String chave, V valor) {
            return base.put(chave, valor);
        }

        @Override
        public Set<Map.Entry<String, V>> entrySet() {
            return base.entrySet();
        }
    }

    /** {@link Armazem} que roda {@link #depoisDoEntrySet} logo depois de produzir a vista da varredura. */
    static final class ArmazemComVarredura<V> implements Armazem<V> {
        final FakeCaches.Cache<V> base;
        volatile Runnable depoisDoEntrySet = () -> { };

        ArmazemComVarredura(FakeCaches.Cache<V> base) {
            this.base = base;
        }

        @Override
        public V get(String chave) {
            return base.get(chave);
        }

        @Override
        public V put(String chave, V valor) {
            return base.put(chave, valor);
        }

        @Override
        public Set<Map.Entry<String, V>> entrySet() {
            // vista com os VALORES lidos agora (as entradas do LinkedHashMap do fake sao vivas)
            Set<Map.Entry<String, V>> vista = new java.util.LinkedHashSet<>();
            for (Map.Entry<String, V> e : base.entrySet()) {
                vista.add(new java.util.AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
            }
            Runnable r = depoisDoEntrySet;
            depoisDoEntrySet = () -> { };
            r.run();
            return vista;
        }
    }

    private static void aguardar(CountDownLatch l) {
        try {
            if (!l.await(PRAZO_S, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch nao liberado em " + PRAZO_S + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static SessionOps ops(Armazem<SessionState> sessoes, FakeCaches.Cache<IdentityState> identidades,
                                  InvariantAuditor auditor,
                                  ConcurrentHashMap<String, SessionState.Motivo> confirmadas) {
        return new SessionOps(sessoes, identidades, new LatencyRegistry(), auditor, null, null, "fake",
                null, confirmadas);
    }

    private static SessionState valida() {
        return SessionState.criar(SID, USUARIO, Instant.parse("2026-10-22T00:00:00Z"));
    }

    private static SessionState invalida(SessionState.Motivo m) {
        return valida().invalidar(m, Instant.parse("2026-10-22T00:00:01Z"));
    }

    /** Roda {@code acaoB} numa thread B pausada depois do get; durante a pausa roda {@code acaoA}. */
    private static OpResult corrida(ArmazemPausavel<SessionState> armazem, java.util.function.Supplier<OpResult> acaoB,
                                    Runnable acaoA) throws Exception {
        AtomicReference<OpResult> res = new AtomicReference<>();
        AtomicReference<Throwable> falha = new AtomicReference<>();
        Thread b = new Thread(() -> {
            try {
                res.set(acaoB.get());
            } catch (Throwable t) {
                falha.set(t);
            }
        }, "thread-B");
        armazem.alvo = b;
        b.start();
        aguardar(armazem.leu);
        acaoA.run();
        armazem.segue.countDown();
        b.join(TimeUnit.SECONDS.toMillis(PRAZO_S));
        assertTrue(!b.isAlive(), "thread B nao terminou");
        if (falha.get() != null) {
            throw new AssertionError("thread B falhou", falha.get());
        }
        return res.get();
    }

    private static List<String> invariantes(InvariantAuditor a) {
        return a.drenar().stream().map(v -> v.invariante() + ":" + v.chave()).toList();
    }

    // ---------------------------------------------------------------- A1, A2: a corrida nao e violacao

    @Test
    @DisplayName("A1: validate le VALID, logout concorrente confirma, validate retoma => 0 violacoes, i1_concorrentes=1")
    void validateConcorrenteComLogoutNaoEViolacao() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        ArmazemPausavel<SessionState> armazem = new ArmazemPausavel<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        SessionOps ops = ops(armazem, new FakeCaches.Cache<>(), auditor, new ConcurrentHashMap<>());

        OpResult rb = corrida(armazem, () -> ops.validate(SID), () -> ops.logout(SID));

        assertEquals(SessionState.Estado.VALID, rb.estado(), "a leitura concorrente e legitimamente VALID");
        assertEquals(List.of(), invariantes(auditor));
        assertEquals(1, ops.i1Concorrentes());
        assertEquals(0, ops.i4Concorrentes());
        // depois da corrida, uma leitura posterior e coerente com a confirmacao: sem violacao
        assertEquals(SessionState.Estado.INVALID, ops.validate(SID).estado());
        assertEquals(0, auditor.total());
    }

    @Test
    @DisplayName("A2: dois logouts concorrentes na mesma sessao => 0 violacoes, i1_concorrentes=1")
    void logoutsConcorrentesNaoSaoViolacao() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        ArmazemPausavel<SessionState> armazem = new ArmazemPausavel<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        SessionOps ops = ops(armazem, new FakeCaches.Cache<>(), auditor, confirmadas);

        corrida(armazem, () -> ops.logout(SID), () -> ops.logout(SID));

        assertEquals(List.of(), invariantes(auditor));
        assertEquals(1, ops.i1Concorrentes());
        assertEquals(SessionState.Motivo.LOGOUT, base.dados.get(SID).motivo());
        assertEquals(SessionState.Motivo.LOGOUT, confirmadas.get(SID));
    }

    @Test
    @DisplayName("A2b: INVALID lido com motivo X enquanto outra instancia confirma Y => 0 violacoes, i4_concorrentes=1")
    void motivoConcorrenteNaoEViolacao() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, invalida(SessionState.Motivo.LOGOUT));
        ArmazemPausavel<SessionState> armazem = new ArmazemPausavel<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        SessionOps ops = ops(armazem, new FakeCaches.Cache<>(), auditor, confirmadas);

        corrida(armazem, () -> ops.validate(SID), () -> confirmadas.put(SID, SessionState.Motivo.ADMIN_BLOCK));

        assertEquals(List.of(), invariantes(auditor));
        assertEquals(0, ops.i1Concorrentes());
        assertEquals(1, ops.i4Concorrentes());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, confirmadas.get(SID), "putIfAbsent nao sobrescreve");
    }

    // ---------------------------------------------------------------- A3: controle positivo

    @Test
    @DisplayName("A3: leitura INICIADA depois da confirmacao devolve a versao VALID antiga => I1 (validate e logout)")
    void leituraVelhaPosteriorEI1() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        ArmazemVelho<SessionState> replicaVelha = new ArmazemVelho<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        SessionOps opsA = ops(base, ids, auditor, confirmadas);
        SessionOps opsB = ops(replicaVelha, ids, auditor, confirmadas);

        // A confirma numa thread propria; B so comeca depois (join = happens-before)
        Thread a = new Thread(() -> opsA.logout(SID), "thread-A");
        a.start();
        a.join(TimeUnit.SECONDS.toMillis(PRAZO_S));
        assertEquals(SessionState.Motivo.LOGOUT, confirmadas.get(SID));
        replicaVelha.versaoVelha = valida();

        assertEquals(SessionState.Estado.VALID, opsB.validate(SID).estado());
        opsB.logout(SID);

        assertEquals(List.of("I1:" + SID, "I1:" + SID), invariantes(auditor));
        assertEquals(0, opsB.i1Concorrentes());
    }

    @Test
    @DisplayName("A3: leitura INICIADA depois da confirmacao com motivo diferente => I4 (validate e logout)")
    void leituraVelhaPosteriorEI4() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        ArmazemVelho<SessionState> replicaVelha = new ArmazemVelho<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        SessionOps opsA = ops(base, ids, auditor, confirmadas);
        SessionOps opsB = ops(replicaVelha, ids, auditor, confirmadas);

        opsA.logout(SID);
        replicaVelha.versaoVelha = invalida(SessionState.Motivo.ADMIN_BLOCK);
        opsB.validate(SID);
        opsB.logout(SID);

        assertEquals(List.of("I4:" + SID, "I4:" + SID), invariantes(auditor));
        assertEquals(0, opsB.i4Concorrentes());
    }

    // ---------------------------------------------------------------- A4: block

    @Test
    @DisplayName("A4: block com logout concorrente durante a varredura => sem I1 espuria, i1_concorrentes=1")
    void blockComLeituraConcorrenteNaVarredura() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        ArmazemComVarredura<SessionState> armazem = new ArmazemComVarredura<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        SessionOps admin = ops(armazem, ids, auditor, confirmadas);
        SessionOps app = ops(base, ids, auditor, confirmadas);
        armazem.depoisDoEntrySet = () -> {
            Thread a = new Thread(() -> app.logout(SID), "thread-A");
            a.start();
            try {
                a.join(TimeUnit.SECONDS.toMillis(PRAZO_S));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };

        assertEquals(OpResult.Code.OK_NOOP, admin.block(USUARIO).code());

        assertEquals(List.of(), invariantes(auditor));
        assertEquals(1, admin.i1Concorrentes());
    }

    @Test
    @DisplayName("A4: confirmacao anterior a varredura e entrada VALID => I1")
    void blockComConfirmacaoAnteriorEI1() {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        SessionOps ops = ops(base, new FakeCaches.Cache<>(), auditor, confirmadas);

        ops.logout(SID);
        base.dados.put(SID, valida()); // a varredura enxerga a versao VALID antiga
        ops.block(USUARIO);

        assertEquals(List.of("I1:" + SID), invariantes(auditor));
        assertEquals(0, ops.i1Concorrentes());
    }

    // ---------------------------------------------------------------- A5: sequencial (B-23)

    @Test
    @DisplayName("A5: logout e depois validate/logout que leem VALID por um fake velho, na mesma thread => I1")
    void sequencialB23() {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, valida());
        InvariantAuditor auditor = new InvariantAuditor();
        SessionOps ops = ops(base, new FakeCaches.Cache<>(), auditor, new ConcurrentHashMap<>());

        ops.logout(SID);
        base.dados.put(SID, valida());
        ops.validate(SID);
        base.dados.put(SID, valida());
        ops.logout(SID);

        assertEquals(List.of("I1:" + SID, "I1:" + SID), invariantes(auditor));
        assertEquals(0, ops.i1Concorrentes());
        assertEquals(0, ops.i4Concorrentes());
    }

    @Test
    @DisplayName("primeira observacao INVALID sem confirmacao previa registra o motivo (putIfAbsent), sem contar corrida")
    void primeiraObservacaoRegistra() {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        base.dados.put(SID, invalida(SessionState.Motivo.EXPIRADA));
        InvariantAuditor auditor = new InvariantAuditor();
        ConcurrentHashMap<String, SessionState.Motivo> confirmadas = new ConcurrentHashMap<>();
        SessionOps ops = ops(base, new FakeCaches.Cache<>(), auditor, confirmadas);

        ops.validate(SID);

        assertEquals(SessionState.Motivo.EXPIRADA, confirmadas.get(SID));
        assertEquals(0, auditor.total());
        assertEquals(0, ops.i1Concorrentes() + ops.i4Concorrentes());
    }
}
