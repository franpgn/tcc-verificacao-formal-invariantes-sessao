package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.CACHES;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.MS;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.S;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.fonte;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.saudavel;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T10, T11, T12 com agenda): agenda periodica real da sonda. Usa relogio real e esperas
 * curtas (nenhum sleep acima de 200 ms; T10 observa 1 s de agenda em passos de 20 ms).
 */
class SondaTopologiaAgendaTest {

    private static final String PREFIXO = "sonda-topologia";

    /** Espera ate a condicao valer ou o prazo acabar, em passos de 20 ms. */
    static boolean esperar(long prazoMs, BooleanSupplier cond) throws InterruptedException {
        long limite = System.nanoTime() + prazoMs * MS;
        while (System.nanoTime() < limite) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return cond.getAsBoolean();
    }

    static List<Thread> threadsDaSonda() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(PREFIXO) && t.isAlive()).toList();
    }

    // ---------------------------------------------------------------- T10 agenda

    @Test
    @DisplayName("T10: periodo 100 ms com fonte instantanea: entre 8 e 12 rodadas em 1 s")
    void taxaFixa() throws InterruptedException {
        try (SondaTopologia s = new SondaTopologia(fonte(3, (i, p) -> saudavel(3)), CACHES, 3, 100 * MS, 50 * MS,
                System::nanoTime, a -> { })) {
            long t0 = System.nanoTime();
            s.iniciar();
            esperar(1000, () -> false);
            long r = s.rodadas();
            long decorrido = (System.nanoTime() - t0) / MS;
            assertTrue(r >= 8 && r <= 12, r + " rodadas em " + decorrido + " ms");
        }
    }

    @Test
    @DisplayName("T10: rodada cuja consulta passa do periodo faz a proxima ser pulada; nunca > nos() consultas em voo")
    void pulaSemSobrepor() throws InterruptedException {
        AtomicInteger emVoo = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        AtomicBoolean primeira = new AtomicBoolean(true);
        FonteDeSaude f = fonte(3, (i, p) -> {
            maximo.accumulateAndGet(emVoo.incrementAndGet(), Math::max);
            try {
                if (i == 0 && primeira.getAndSet(false)) {
                    // demora 150 ms ignorando a interrupcao (pior caso: consulta que nao cede)
                    long fim = System.nanoTime() + 150 * MS;
                    while (System.nanoTime() < fim) {
                        try {
                            Thread.sleep(Math.max(1, (fim - System.nanoTime()) / MS));
                        } catch (InterruptedException ignorada) {
                            // segue ate os 150 ms
                        }
                    }
                }
                return saudavel(3);
            } finally {
                emVoo.decrementAndGet();
            }
        });
        try (SondaTopologia s = new SondaTopologia(f, CACHES, 3, 100 * MS, 50 * MS, System::nanoTime, a -> { })) {
            s.iniciar();
            assertTrue(esperar(1500, () -> s.rodadas() >= 4), "rodadas: " + s.rodadas());
            assertTrue(s.rodadasPuladas() >= 1, "rodadasPuladas = " + s.rodadasPuladas());
            assertTrue(maximo.get() <= 3, "maximo de consultas simultaneas = " + maximo.get());
        }
    }

    // ---------------------------------------------------------------- T11 ciclo de vida

    @Test
    @DisplayName("T11: close interrompe a rodada em curso e nenhuma thread da sonda sobra; threads sao daemon")
    void closeLimpaThreads() throws InterruptedException {
        CountDownLatch emCurso = new CountDownLatch(1);
        FonteDeSaude f = fonte(3, (i, p) -> {
            if (i == 0) {
                emCurso.countDown();
                new CountDownLatch(1).await(); // bloqueia ate ser interrompida
            }
            return saudavel(3);
        });
        SondaTopologia s = new SondaTopologia(f, CACHES, 3, 10 * S, 5 * S, System::nanoTime, a -> { });
        s.iniciar();
        assertTrue(emCurso.await(1, TimeUnit.SECONDS), "a primeira rodada nao comecou");
        List<Thread> vivas = threadsDaSonda();
        assertTrue(vivas.stream().anyMatch(t -> t.getName().equals(PREFIXO)), "agenda: " + vivas);
        assertTrue(vivas.stream().anyMatch(t -> t.getName().startsWith(PREFIXO + "-no-")), "consultas: " + vivas);
        assertTrue(vivas.stream().allMatch(Thread::isDaemon), "threads nao daemon: " + vivas);

        s.close();
        assertTrue(esperar(1000, () -> threadsDaSonda().isEmpty()), "threads vivas: " + threadsDaSonda());
        assertDoesNotThrow(s::close);
        assertThrows(IllegalStateException.class, s::iniciar);
    }

    @Test
    @DisplayName("T11: iniciar duas vezes lanca IllegalStateException")
    void iniciarDuasVezes() {
        try (SondaTopologia s = new SondaTopologia(fonte(3, (i, p) -> saudavel(3)), CACHES, 3, 100 * MS, 50 * MS,
                System::nanoTime, a -> { })) {
            s.iniciar();
            assertThrows(IllegalStateException.class, s::iniciar);
        }
    }

    @Test
    @DisplayName("T11: close sem iniciar nao deixa thread e e idempotente")
    void closeSemIniciar() throws InterruptedException {
        SondaTopologia s = new SondaTopologia(fonte(3, (i, p) -> saudavel(3)), CACHES, 3, 100 * MS, 50 * MS,
                System::nanoTime, a -> { });
        s.rodada();
        s.close();
        s.close();
        assertTrue(esperar(1000, () -> threadsDaSonda().isEmpty()), "threads vivas: " + threadsDaSonda());
        assertThrows(IllegalStateException.class, s::iniciar);
    }

    // ---------------------------------------------------------------- T12 ouvinte com agenda

    @Test
    @DisplayName("T12: ouvinte que lanca nao para a agenda; errosOuvinte conta")
    void ouvinteQueLancaNaoParaAgenda() throws InterruptedException {
        try (SondaTopologia s = new SondaTopologia(fonte(3, (i, p) -> saudavel(3)), CACHES, 3, 50 * MS, 20 * MS,
                System::nanoTime, a -> {
                    throw new IllegalStateException("ouvinte quebrado");
                })) {
            s.iniciar();
            assertTrue(esperar(2000, () -> s.rodadas() >= 4), "rodadas: " + s.rodadas());
            assertTrue(s.errosOuvinte() >= 3, "errosOuvinte = " + s.errosOuvinte());
            assertFalse(s.resumirAte(Long.MAX_VALUE).amostras() < 3);
            assertEquals(0, s.amostrasAtrasadas());
        }
    }
}
