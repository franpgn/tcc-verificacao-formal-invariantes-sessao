package br.unipampa.tcc.session;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-43.8: utilitarios de teste para forcar ordens entre threads (sem dormir). */
final class Concorrencia {

    private Concorrencia() { }

    /** Roda {@code r} numa outra thread e espera ela terminar (10 s). */
    static void emOutraThread(Runnable r) {
        Thread t = new Thread(r, "concorrente");
        t.start();
        try {
            t.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertTrue(!t.isAlive(), "a operacao concorrente nao terminou");
    }

    /** Espera o latch (10 s) ou falha. */
    static void aguardar(CountDownLatch l) {
        try {
            assertTrue(l.await(10, TimeUnit.SECONDS), "latch nao liberado");
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Inicia {@code r} numa thread nova (sem esperar). */
    static Thread iniciar(Runnable r, String nome) {
        Thread t = new Thread(r, nome);
        t.start();
        return t;
    }

    static void juntar(Thread t) {
        try {
            t.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertTrue(!t.isAlive(), "thread " + t.getName() + " nao terminou");
    }
}
