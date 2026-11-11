package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T1, T6, T7, T8, T9, T12 parte direta, instante de fim): uma rodada da sonda e o resumo por
 * janela, chamando {@link SondaTopologia#rodada()} direto, com relogio falso (exceto T7, que mede o
 * prazo no relogio real).
 */
class SondaTopologiaRodadaTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;
    static final List<String> CACHES = List.of("c1", "c2");

    /** Consulta de um no, por indice. */
    @FunctionalInterface
    interface Consulta {
        LeituraSaude consultar(int indice, long prazoNs) throws Exception;
    }

    static FonteDeSaude fonte(int n, Consulta c) {
        return new FonteDeSaude() {
            @Override public int nos() { return n; }
            @Override public String rotulo(int i) { return "no" + i; }
            @Override public LeituraSaude consultar(int i, long prazoNs) throws Exception {
                return c.consultar(i, prazoNs);
            }
        };
    }

    static LeituraSaude saudavel(int nos) {
        return new LeituraSaude(nos, Map.of("c1", "HEALTHY", "c2", "HEALTHY"));
    }

    static final FonteDeSaude TRES_SAUDAVEIS = fonte(3, (i, p) -> saudavel(3));

    static SondaTopologia sonda(FonteDeSaude f, AtomicLong relogio, Consumer<AmostraTopologia> ouvinte) {
        return new SondaTopologia(f, CACHES, 3, 2 * S, 1500 * MS, relogio::get, ouvinte);
    }

    static SondaTopologia sonda(FonteDeSaude f, AtomicLong relogio) {
        return sonda(f, relogio, a -> { });
    }

    static AmostraTopologia rodadaEm(SondaTopologia s, AtomicLong relogio, long tNs) {
        relogio.set(tNs);
        return s.rodada();
    }

    // ---------------------------------------------------------------- T1 contrato V-B28.1

    @Test
    @DisplayName("T1: falha de consulta e amostra anomala com disponivel = true")
    void falhaDeConsultaEAnomalaDisponivel() {
        AtomicLong t = new AtomicLong(S);
        FonteDeSaude f = fonte(3, (i, p) -> {
            if (i == 1) {
                throw new IOException("conexao recusada");
            }
            return saudavel(3);
        });
        try (SondaTopologia s = sonda(f, t)) {
            AmostraTopologia a = s.rodada();
            assertEquals(new ResultadoNo("no1", false, -1, "falha:IOException", true), a.porNo().get(1));
            assertFalse(a.porNo().get(0).anomalo());
            assertTrue(a.anomala());
            assertEquals(3, a.nosMinimo());
            EstadoTopologia e = s.resumirAte(5 * S);
            assertEquals(new EstadoTopologia(true, 1, 1, 3), e);
            assertTrue(e.anomala());
        }
    }

    @Test
    @DisplayName("T1: todos os nos falhando: nosMinimo = 0 e ainda disponivel = true")
    void todosFalhando() {
        AtomicLong t = new AtomicLong(S);
        FonteDeSaude f = fonte(3, (i, p) -> {
            throw new IllegalStateException("x");
        });
        try (SondaTopologia s = sonda(f, t)) {
            AmostraTopologia a = s.rodada();
            assertTrue(a.anomala());
            assertEquals(0, a.nosMinimo());
            assertTrue(a.porNo().stream().allMatch(r -> r.motivo().equals("falha:IllegalStateException")));
            EstadoTopologia e = s.resumirAte(5 * S);
            assertEquals(new EstadoTopologia(true, 1, 1, 0), e);
            assertTrue(e.anomala());
        }
    }

    // ---------------------------------------------------------------- T6 agregacao

    @Test
    @DisplayName("T6: so o 2o no anomalo torna a amostra anomala; nenhum anomalo, nao anomala")
    void agregacaoAnomala() {
        AtomicLong t = new AtomicLong(S);
        FonteDeSaude f = fonte(3, (i, p) -> i == 1
                ? new LeituraSaude(3, Map.of("c1", "DEGRADED", "c2", "HEALTHY")) : saudavel(3));
        try (SondaTopologia s = sonda(f, t); SondaTopologia ok = sonda(TRES_SAUDAVEIS, t)) {
            AmostraTopologia a = s.rodada();
            assertTrue(a.anomala());
            assertEquals(List.of(false, true, false), a.porNo().stream().map(ResultadoNo::anomalo).toList());
            assertEquals("cache:c1=DEGRADED", a.porNo().get(1).motivo());
            AmostraTopologia b = ok.rodada();
            assertFalse(b.anomala());
            assertEquals(3, b.nosMinimo());
            assertEquals(List.of("no0", "no1", "no2"), b.porNo().stream().map(ResultadoNo::no).toList());
        }
    }

    @Test
    @DisplayName("T6: nosMinimo e o minimo entre os que responderam (3,2,3 -> 2; 3,falha,3 -> 3)")
    void nosMinimo() {
        AtomicLong t = new AtomicLong(S);
        int[] nos = {3, 2, 3};
        try (SondaTopologia s = sonda(fonte(3, (i, p) -> saudavel(nos[i])), t)) {
            assertEquals(2, s.rodada().nosMinimo());
        }
        try (SondaTopologia s = sonda(fonte(3, (i, p) -> {
            if (i == 1) {
                throw new IOException("x");
            }
            return saudavel(3);
        }), t)) {
            assertEquals(3, s.rodada().nosMinimo());
        }
        int[] nos2 = {4, 3, 5};
        try (SondaTopologia s = sonda(fonte(3, (i, p) -> saudavel(nos2[i])), t)) {
            assertEquals(3, s.rodada().nosMinimo());
        }
    }

    // ---------------------------------------------------------------- T7 prazo

    @Test
    @DisplayName("T7: no que bloqueia vira timeout no prazo da rodada e e interrompido")
    void prazoDaRodada() throws InterruptedException {
        CountDownLatch interrompido = new CountDownLatch(1);
        FonteDeSaude f = fonte(3, (i, p) -> {
            if (i == 1) {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrompido.countDown();
                    throw e;
                }
            }
            return saudavel(3);
        });
        try (SondaTopologia s = new SondaTopologia(f, CACHES, 3, 1000 * MS, 150 * MS, System::nanoTime, a -> { })) {
            long t0 = System.nanoTime();
            AmostraTopologia a = assertTimeoutPreemptively(Duration.ofSeconds(3), s::rodada);
            long decorrido = System.nanoTime() - t0;
            assertTrue(decorrido <= 250 * MS, "rodada levou " + decorrido / MS + " ms (prazo 150 ms + 100 ms)");
            assertEquals(new ResultadoNo("no1", false, -1, "timeout", true), a.porNo().get(1));
            assertEquals("ok", a.porNo().get(0).motivo());
            assertEquals("ok", a.porNo().get(2).motivo());
            assertTrue(a.anomala());
            assertTrue(interrompido.await(1, TimeUnit.SECONDS), "a tarefa bloqueada nao recebeu interrupcao");
        }
    }

    @Test
    @DisplayName("os nos sao consultados em paralelo (3 nos de 100 ms terminam bem antes de 300 ms)")
    void paralelo() {
        CountDownLatch todos = new CountDownLatch(3);
        FonteDeSaude f = fonte(3, (i, p) -> {
            todos.countDown();
            // so responde quando os tres estiverem em voo ao mesmo tempo
            if (!todos.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("consultas nao simultaneas");
            }
            return saudavel(3);
        });
        try (SondaTopologia s = new SondaTopologia(f, CACHES, 3, 2 * S, 1500 * MS, System::nanoTime, a -> { })) {
            AmostraTopologia a = assertTimeoutPreemptively(Duration.ofSeconds(3), s::rodada);
            assertEquals(List.of("ok", "ok", "ok"), a.porNo().stream().map(ResultadoNo::motivo).toList());
            assertFalse(a.anomala(), a.toString());
        }
    }

    // ---------------------------------------------------------------- instante de fim (M16)

    @Test
    @DisplayName("tNs e o instante em que a rodada TERMINOU, nao o de inicio")
    void instanteDoFim() {
        AtomicLong t = new AtomicLong(5 * S);
        FonteDeSaude f = fonte(3, (i, p) -> {
            t.accumulateAndGet(7 * S, Math::max); // o tempo passa durante a consulta
            return saudavel(3);
        });
        try (SondaTopologia s = sonda(f, t)) {
            assertEquals(7 * S, s.rodada().tNs());
            assertEquals(0, s.resumirAte(7 * S).amostras(), "a amostra so existe a partir do fim da rodada");
            assertEquals(1, s.resumirAte(8 * S).amostras());
        }
    }

    // ---------------------------------------------------------------- T8 janelas

    @Test
    @DisplayName("T8: resumo por janela [., fim) com fronteira tNs == fimNs para a proxima")
    void janelas() {
        AtomicLong t = new AtomicLong();
        try (SondaTopologia s = sonda(TRES_SAUDAVEIS, t)) {
            for (long x : new long[] {1, 3, 5, 7}) {
                rodadaEm(s, t, x * S);
            }
            assertEquals(new EstadoTopologia(true, 2, 0, 3), s.resumirAte(5 * S));
            assertEquals(new EstadoTopologia(true, 2, 0, 3), s.resumirAte(10 * S));
            assertEquals(new EstadoTopologia(true, 0, 0, 0), s.resumirAte(15 * S));
            assertEquals(0, s.amostrasAtrasadas());
        }
    }

    @Test
    @DisplayName("T8: amostra atrasada (t = 4 s entregue depois do resumo de 5 s) entra no proximo resumo")
    void atrasadaPromovida() {
        AtomicLong t = new AtomicLong();
        try (SondaTopologia s = sonda(TRES_SAUDAVEIS, t)) {
            rodadaEm(s, t, 1 * S);
            rodadaEm(s, t, 3 * S);
            assertEquals(2, s.resumirAte(5 * S).amostras());
            rodadaEm(s, t, 4 * S); // chegou tarde
            rodadaEm(s, t, 7 * S);
            assertEquals(new EstadoTopologia(true, 2, 0, 3), s.resumirAte(10 * S));
            assertEquals(1, s.amostrasAtrasadas());
        }
    }

    @Test
    @DisplayName("T8: amostra anomala atrasada conta como anomala na janela em que e consumida")
    void atrasadaAnomala() {
        AtomicLong t = new AtomicLong();
        boolean[] ruim = {false};
        FonteDeSaude f = fonte(3, (i, p) -> ruim[0] ? saudavel(2) : saudavel(3));
        try (SondaTopologia s = sonda(f, t)) {
            rodadaEm(s, t, 1 * S);
            assertEquals(new EstadoTopologia(true, 1, 0, 3), s.resumirAte(5 * S));
            ruim[0] = true;
            rodadaEm(s, t, 4 * S);
            EstadoTopologia e = s.resumirAte(10 * S);
            assertEquals(new EstadoTopologia(true, 1, 1, 2), e);
            assertTrue(e.anomala());
        }
    }

    @Test
    @DisplayName("T8: fimNs que recua e rejeitado; repetir o mesmo fim e permitido")
    void fimQueRecua() {
        AtomicLong t = new AtomicLong();
        try (SondaTopologia s = sonda(TRES_SAUDAVEIS, t)) {
            s.resumirAte(10 * S);
            assertThrows(IllegalArgumentException.class, () -> s.resumirAte(9 * S));
            assertEquals(new EstadoTopologia(true, 0, 0, 0), s.resumirAte(10 * S));
        }
    }

    // ---------------------------------------------------------------- T9 janela vazia

    @Test
    @DisplayName("T9: janela sem amostra e neutra: disponivel, 0 amostras, nao anomala")
    void janelaVazia() {
        AtomicLong t = new AtomicLong();
        try (SondaTopologia s = sonda(TRES_SAUDAVEIS, t)) {
            EstadoTopologia e = s.resumirAte(5 * S);
            assertEquals(new EstadoTopologia(true, 0, 0, 0), e);
            assertFalse(e.anomala());
        }
    }

    // ---------------------------------------------------------------- T12 ouvinte (rodada direta)

    @Test
    @DisplayName("T12: o ouvinte recebe cada amostra uma vez, na ordem das rodadas")
    void ouvinteNaOrdem() {
        AtomicLong t = new AtomicLong();
        List<AmostraTopologia> recebidas = Collections.synchronizedList(new ArrayList<>());
        try (SondaTopologia s = sonda(TRES_SAUDAVEIS, t, recebidas::add)) {
            List<AmostraTopologia> devolvidas = new ArrayList<>();
            for (long x = 1; x <= 5; x++) {
                devolvidas.add(rodadaEm(s, t, x * S));
            }
            assertEquals(devolvidas, recebidas);
            assertEquals(List.of(S, 2 * S, 3 * S, 4 * S, 5 * S),
                    recebidas.stream().map(AmostraTopologia::tNs).toList());
            assertEquals(5, s.rodadas());
        }
    }

    @Test
    @DisplayName("T12: ouvinte que lanca nao derruba a rodada e e contado")
    void ouvinteQueLanca() {
        AtomicLong t = new AtomicLong();
        try (SondaTopologia s = sonda(TRES_SAUDAVEIS, t, a -> {
            throw new IllegalStateException("ouvinte quebrado");
        })) {
            rodadaEm(s, t, 1 * S);
            rodadaEm(s, t, 2 * S);
            assertEquals(2, s.errosOuvinte());
            assertEquals(2, s.resumirAte(5 * S).amostras(), "a amostra vai ao buffer mesmo com o ouvinte quebrado");
        }
    }

    // ---------------------------------------------------------------- construtor

    @Test
    @DisplayName("construtor rejeita caches vazios, nome vazio, nos < 1, periodo/prazo invalidos e ouvinte nulo")
    void construtorRejeita() {
        AtomicLong t = new AtomicLong();
        FonteDeTempo r = t::get;
        Consumer<AmostraTopologia> o = a -> { };
        FonteDeSaude f = TRES_SAUDAVEIS;
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, List.of(), 3, 2 * S, S, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, List.of("c1", ""), 3, 2 * S, S, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, CACHES, 0, 2 * S, S, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, CACHES, 3, 0, S, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, CACHES, 3, 2 * S, 0, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, CACHES, 3, 2 * S, 2 * S, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, CACHES, 3, 2 * S, 3 * S, r, o));
        assertThrows(IllegalArgumentException.class, () -> new SondaTopologia(f, CACHES, 3, 2 * S, S, r, null));
        new SondaTopologia(f, CACHES, 3, 2 * S, S, r, o).close();
    }

    @Test
    @DisplayName("rodada depois de close lanca IllegalStateException; resumirAte continua respondendo")
    void depoisDoClose() {
        AtomicLong t = new AtomicLong();
        SondaTopologia s = sonda(TRES_SAUDAVEIS, t);
        rodadaEm(s, t, S);
        s.close();
        assertThrows(IllegalStateException.class, s::rodada);
        assertEquals(new EstadoTopologia(true, 1, 0, 3), s.resumirAte(5 * S));
    }
}
