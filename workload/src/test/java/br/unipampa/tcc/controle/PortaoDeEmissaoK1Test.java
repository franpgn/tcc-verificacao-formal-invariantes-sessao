package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27, K1 (admissao): o limite de operacoes em voo cai para ceil(fracao * nominal) em CONSERVADOR,
 * volta ao nominal em NORMAL, acorda todos os que esperam na troca e nunca perde nem duplica
 * permissao. Threads reais, com prazos curtos.
 */
class PortaoDeEmissaoK1Test {

    static ParametrosKnobs knobs(double fracaoK1) {
        ParametrosKnobs p = ParametrosKnobs.padrao();
        return new ParametrosKnobs("teste", fracaoK1, p.k2PausaConservadorNs(), p.k2MultPensarConservador(),
                p.k3AdiaMaxNs(), p.k3SondagemNs(), p.k4FatorBaseConservador(), p.k4JitterConservador(), null);
    }

    static PortaoDeEmissao portao(PerfilAtual perfil, ParametrosKnobs k, int nominal) {
        return new PortaoDeEmissao(perfil, k, nominal, System::nanoTime, Dormidor.real(), e -> { }, 1L);
    }

    /** Espera ate a condicao valer ou o prazo passar. */
    static boolean ate(BooleanSupplier c, long prazoMs) throws InterruptedException {
        long fim = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(prazoMs);
        while (System.nanoTime() < fim) {
            if (c.getAsBoolean()) {
                return true;
            }
            Thread.sleep(2);
        }
        return c.getAsBoolean();
    }

    static Thread daemon(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    @DisplayName("K1a: 8 threads, nominal 8; CONSERVADOR limita a 4 sem expulsar quem ja entrou; NORMAL volta a 8; entradas = saidas")
    void reduzEAumenta() throws Exception {
        PerfilAtual perfil = new PerfilAtual();
        PortaoDeEmissao p = portao(perfil, ParametrosKnobs.padrao(), 8);
        AtomicInteger entradas = new AtomicInteger();
        AtomicInteger saidas = new AtomicInteger();
        AtomicInteger maxFase2 = new AtomicInteger();
        CountDownLatch dentro = new CountDownLatch(8);
        CountDownLatch soltar1 = new CountDownLatch(1);
        CountDownLatch fase2Feita = new CountDownLatch(8);
        CountDownLatch fase3 = new CountDownLatch(1);
        CountDownLatch soltar3 = new CountDownLatch(1);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        List<Thread> ts = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                ts.add(daemon(() -> {
                    try {
                        p.entrar();
                        entradas.incrementAndGet();
                        dentro.countDown();
                        soltar1.await();
                        p.sair();
                        saidas.incrementAndGet();
                        for (int j = 0; j < 40; j++) {
                            p.entrar();
                            entradas.incrementAndGet();
                            maxFase2.accumulateAndGet(p.emVoo(), Math::max);
                            Thread.sleep(1);
                            p.sair();
                            saidas.incrementAndGet();
                        }
                        fase2Feita.countDown();
                        fase3.await();
                        p.entrar();
                        entradas.incrementAndGet();
                        soltar3.await();
                        p.sair();
                        saidas.incrementAndGet();
                    } catch (Throwable t) {
                        erro.compareAndSet(null, t);
                    }
                }));
            }
            assertTrue(dentro.await(5, TimeUnit.SECONDS), "as 8 threads entram em NORMAL");
            assertEquals(8, p.emVoo());
            assertEquals(8, p.limiteAtual());

            perfil.definir(Perfil.CONSERVADOR);
            assertEquals(4, p.limiteAtual(), "ceil(0,5 * 8)");
            assertEquals(8, p.emVoo(), "reduzir o limite nao expulsa quem ja entrou");

            soltar1.countDown();
            assertTrue(fase2Feita.await(10, TimeUnit.SECONDS), "fase CONSERVADOR terminou (nenhuma permissao perdida)");
            assertEquals(4, maxFase2.get(), "maximo em voo depois da troca para CONSERVADOR");

            perfil.definir(Perfil.NORMAL);
            assertEquals(8, p.limiteAtual());
            fase3.countDown();
            assertTrue(ate(() -> p.emVoo() == 8, 5000), "de volta a NORMAL as 8 entram juntas: " + p.emVoo());
            soltar3.countDown();
            for (Thread t : ts) {
                t.join(5000);
                assertFalse(t.isAlive(), "thread presa no fim");
            }
            assertNull(erro.get(), () -> "erro numa thread: " + erro.get());
            assertEquals(8 * 42, entradas.get());
            assertEquals(entradas.get(), saidas.get(), "entradas = saidas");
            assertEquals(0, p.emVoo(), "nenhuma permissao sobrando");
        } finally {
            soltar1.countDown();
            fase3.countDown();
            soltar3.countDown();
            p.encerrar();
        }
    }

    @Test
    @DisplayName("K1b: arredondamento para cima e piso 1 (nominal 1 => 1; nominal 3, fracao 0,5 => 2; 5 => 3)")
    void arredondamento() throws Exception {
        PerfilAtual perfil = new PerfilAtual();
        perfil.definir(Perfil.CONSERVADOR);
        assertEquals(1, portao(perfil, ParametrosKnobs.padrao(), 1).limiteAtual(), "nunca 0");
        assertEquals(2, portao(perfil, ParametrosKnobs.padrao(), 3).limiteAtual());
        assertEquals(3, portao(perfil, ParametrosKnobs.padrao(), 5).limiteAtual());
        assertEquals(1, portao(perfil, knobs(0.01), 8).limiteAtual(), "fracao pequena: piso 1");
        assertEquals(8, portao(perfil, knobs(1.0), 8).limiteAtual());
        // com nominal 1 em CONSERVADOR a primeira entrada passa na hora
        PortaoDeEmissao um = portao(perfil, ParametrosKnobs.padrao(), 1);
        Thread t = daemon(() -> {
            try {
                um.entrar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.join(2000);
        boolean vivo = t.isAlive();
        um.encerrar();
        assertFalse(vivo, "nominal 1 em CONSERVADOR nao pode travar");
        assertEquals(1, um.emVoo());
        um.sair();
    }

    @Test
    @DisplayName("K1c: 6 threads presas com limite 1; NORMAL acorda todas (signalAll) em ate 1 s")
    void acordaTodos() throws Exception {
        PerfilAtual perfil = new PerfilAtual();
        perfil.definir(Perfil.CONSERVADOR);
        PortaoDeEmissao p = portao(perfil, knobs(0.125), 8);
        assertEquals(1, p.limiteAtual());
        p.entrar(); // a unica permissao fica com o teste
        CountDownLatch entraram = new CountDownLatch(6);
        List<Thread> ts = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                ts.add(daemon(() -> {
                    try {
                        p.entrar();
                        entraram.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            assertTrue(ate(() -> ts.stream().allMatch(t -> t.getState() == Thread.State.WAITING), 5000),
                    "as 6 threads ficam presas no portao");
            assertEquals(1, p.emVoo());
            perfil.definir(Perfil.NORMAL);
            assertTrue(entraram.await(1, TimeUnit.SECONDS),
                    "faltaram " + entraram.getCount() + " threads depois da volta a NORMAL");
            assertEquals(7, p.emVoo());
        } finally {
            p.encerrar();
        }
    }

    @Test
    @DisplayName("K1: sair() sem entrar() correspondente e erro; livre() e singleton sem efeito")
    void sairSemEntrarELivre() throws Exception {
        PortaoDeEmissao p = portao(new PerfilAtual(), ParametrosKnobs.padrao(), 2);
        assertThrows(IllegalStateException.class, p::sair);
        assertSame(PortaoDeEmissao.livre(), PortaoDeEmissao.livre());
        PortaoDeEmissao l = PortaoDeEmissao.livre();
        assertEquals(0L, l.entrar());
        l.sair();
        l.sair(); // sem efeito, sem erro
        assertEquals(0, l.emVoo());
        assertEquals(0L, l.pausaAposOperacaoNs());
        assertEquals(1_234L, l.aplicarPensar(1_234L));
        assertEquals(0L, l.aguardarAdministrativa("admin", "Block", "x"));
        assertEquals(500L, l.esperaRetentativaNs("u", 500L, 1, 7L));
        assertEquals(0L, l.adiamentosK3());
    }

    @Test
    @DisplayName("K1: construtor rejeita concorrencia nominal < 1 e argumentos nulos")
    void construtor() {
        PerfilAtual perfil = new PerfilAtual();
        ParametrosKnobs k = ParametrosKnobs.padrao();
        assertThrows(IllegalArgumentException.class, () -> portao(perfil, k, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PortaoDeEmissao(null, k, 1, System::nanoTime, Dormidor.real(), e -> { }, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new PortaoDeEmissao(perfil, null, 1, System::nanoTime, Dormidor.real(), e -> { }, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new PortaoDeEmissao(perfil, k, 1, null, Dormidor.real(), e -> { }, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new PortaoDeEmissao(perfil, k, 1, System::nanoTime, null, e -> { }, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new PortaoDeEmissao(perfil, k, 1, System::nanoTime, Dormidor.real(), null, 1L));
    }

    @Test
    @DisplayName("K1: entrar() devolve a espera e esperaK1TotalNs a acumula")
    void esperaAcumulada() throws Exception {
        PerfilAtual perfil = new PerfilAtual();
        PortaoDeEmissao p = portao(perfil, ParametrosKnobs.padrao(), 1);
        p.entrar();
        AtomicReference<Long> esperou = new AtomicReference<>();
        Thread t = daemon(() -> {
            try {
                esperou.set(p.entrar());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(ate(() -> t.getState() == Thread.State.WAITING, 5000), "segunda entrada fica presa");
            Thread.sleep(30);
            p.sair();
            t.join(2000);
            assertFalse(t.isAlive());
            assertTrue(esperou.get() >= 25_000_000L, "esperou " + esperou.get());
            assertEquals(esperou.get(), p.esperaK1TotalNs());
            p.sair();
            assertEquals(0, p.emVoo());
        } finally {
            p.encerrar();
        }
    }
}
