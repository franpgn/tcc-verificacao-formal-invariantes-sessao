package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * B-43.8, W1 (pedido do Gestor; mata E1/E2 da V-B43.6 sem depender do diagnostico A7): a confirmacao
 * de uma invalidacao so entra no mapa DEPOIS de a escrita ser reconhecida. O armazem pausa a escrita
 * ANTES de aplica-la (latch). Durante a pausa: (a) o mapa nao contem a confirmacao; (b) um
 * {@code validate} de outra thread le VALID e nao gera I1. Vale para {@code logout} e para a cascata
 * de {@code block}, no caminho versionado ({@link ArmazemVersionado}) e no comum ({@link Armazem}).
 */
class OrdemConfirmacaoTest {

    private static final String U = "u-ordem";
    private static final String SID = "sid-ordem";
    private static final Instant T = Instant.parse("2026-10-22T00:00:00Z");

    /** Pausa a thread {@code alvo[0]} na primeira {@code op} sobre SID, ate {@code libera}. */
    private static BiConsumer<String, String> pausa(Thread[] alvo, String op, CountDownLatch chegou,
                                                    CountDownLatch libera) {
        return (o, k) -> {
            if (Thread.currentThread() == alvo[0] && o.equals(op) && SID.equals(k)) {
                alvo[0] = null;
                chegou.countDown();
                Concorrencia.aguardar(libera);
            }
        };
    }

    private static SessionOps ops(Armazem<SessionState> s, FakeCaches.Cache<IdentityState> ids,
                                  ConcurrentHashMap<String, SessionState.Motivo> mapa, InvariantAuditor a) {
        return new SessionOps(s, ids, new LatencyRegistry(), a, null, null, "fake", null, mapa);
    }

    /**
     * Roda {@code escrita} numa thread; quando ela para antes de aplicar a escrita, confere (a) e (b);
     * libera; confere o estado final com {@code motivo}.
     */
    private static void conferir(Runnable escrita, Thread[] alvo, CountDownLatch chegou, CountDownLatch libera,
                                 SessionOps leitor, ConcurrentHashMap<String, SessionState.Motivo> mapa,
                                 InvariantAuditor a, java.util.function.Supplier<SessionState> finalNoGrid,
                                 SessionState.Motivo motivo) {
        Thread t = new Thread(escrita, "escritor");
        alvo[0] = t;
        t.start();
        Concorrencia.aguardar(chegou);

        assertFalse(mapa.containsKey(SID), "(a) confirmacao visivel antes de a escrita ser aplicada");
        OpResult r = leitor.validate(SID);
        assertEquals(SessionState.Estado.VALID, r.estado(), "(b) a escrita ainda nao foi aplicada");
        assertEquals(0, a.total(), "(b) leitura VALID durante a escrita nao e I1: " + a.drenar());

        libera.countDown();
        Concorrencia.juntar(t);
        assertEquals(motivo, finalNoGrid.get().motivo());
        assertEquals(motivo, mapa.get(SID));
        assertEquals(0, a.total());
        assertEquals(0, leitor.i1Concorrentes(), "o mapa estava vazio depois da leitura");
    }

    @Test
    @DisplayName("W1 logout, caminho versionado: pausa antes de substituirSeVersao")
    void logoutVersionado() {
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        s.semear(SID, SessionState.criar(SID, U, T));
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        Thread[] alvo = {null};
        CountDownLatch chegou = new CountDownLatch(1);
        CountDownLatch libera = new CountDownLatch(1);
        s.antes = pausa(alvo, "substituirSeVersao", chegou, libera);
        SessionOps escritor = ops(s, ids, mapa, a);

        conferir(() -> escritor.logout(SID), alvo, chegou, libera, ops(s, ids, mapa, a), mapa, a,
                () -> s.valor(SID), SessionState.Motivo.LOGOUT);
    }

    @Test
    @DisplayName("W1 logout, caminho comum (B-43.6): pausa antes do put")
    void logoutComum() {
        FakeCaches.Cache<SessionState> s = new FakeCaches.Cache<>();
        s.dados.put(SID, SessionState.criar(SID, U, T));
        FakeCaches.Cache<IdentityState> ids = new FakeCaches.Cache<>();
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        Thread[] alvo = {null};
        CountDownLatch chegou = new CountDownLatch(1);
        CountDownLatch libera = new CountDownLatch(1);
        s.antes = pausa(alvo, "put", chegou, libera);
        SessionOps escritor = ops(s, ids, mapa, a);

        conferir(() -> escritor.logout(SID), alvo, chegou, libera, ops(s, ids, mapa, a), mapa, a,
                () -> s.dados.get(SID), SessionState.Motivo.LOGOUT);
    }
}
