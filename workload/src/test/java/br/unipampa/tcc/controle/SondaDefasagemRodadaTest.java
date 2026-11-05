package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3), item 4 do prompt: a rodada da {@link SondaDefasagem} com o {@link CanalFalso} (atraso conhecido,
 * no cortado, leitura que bloqueia, escrita que falha), o custo (3 escritas e 6 leituras sem falha), as chaves
 * usadas (so {@code lag:<no>}), o buffer por janela, a agenda alinhada a grade e a thread gravadora. O lag e
 * medido no relogio real; os instantes que decidem a janela vem da {@link FonteDeTempo}.
 */
class SondaDefasagemRodadaTest {

    static final long MS = 1_000_000L;
    static final ParametrosDefasagem P = new ParametrosDefasagem(50, 20, 10);

    static SondaDefasagem sonda(CanalFalso c, long tetoMs, long periodoNs, long origemNs, FonteDeTempo tempo,
                                java.util.function.Consumer<RodadaDefasagem> ouvinte) {
        return new SondaDefasagem(c, P, tetoMs, periodoNs, origemNs, tempo, ouvinte, "teste");
    }

    static SondaDefasagem sonda(CanalFalso c, long tetoMs, AtomicLong relogio) {
        return sonda(c, tetoMs, 10_000 * MS, 0, relogio::get, r -> { });
    }

    static RodadaDefasagem.Par par(RodadaDefasagem r, String escritor, String leitor) {
        return r.pares().stream().filter(p -> p.escritor().equals(escritor) && p.leitor().equals(leitor))
                .findFirst().orElseThrow();
    }

    /** {@link SondaDefasagem#rodada()} assim que as tarefas interrompidas da rodada anterior cederem. */
    static RodadaDefasagem rodadaQuandoLivre(SondaDefasagem s) throws InterruptedException {
        long ate = System.nanoTime() + 3_000 * MS;
        while (true) {
            try {
                return s.rodada();
            } catch (IllegalStateException ainda) {
                if (System.nanoTime() > ate) {
                    throw ainda;
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }
    }

    @Test
    @DisplayName("sem falha: todos os pares vistos, lag < teto, 3 escritas e 6 leituras, so as chaves lag:<no>")
    void semFalha() {
        CanalFalso c = new CanalFalso(3);
        // passo de 100 ms: o leitor espera a escrita voltar ate um passo, folga para o agendamento das threads
        try (SondaDefasagem s = new SondaDefasagem(c, new ParametrosDefasagem(150, 20, 100), 400, 10_000 * MS, 0,
                () -> 1, r -> { }, "teste")) {
            RodadaDefasagem r = s.rodada();
            assertTrue(r.pares().stream().allMatch(RodadaDefasagem.Par::visto), r.toString());
            assertTrue(r.lagMaxMs() < 400, "lag " + r.lagMaxMs());
            assertEquals(3, c.escritas.get());
            assertEquals(6, c.leituras.get(), "o leitor espera a escrita voltar e le uma vez");
            assertEquals(6, r.leituras());
            assertEquals(3, s.escritas());
            assertEquals(6, s.leituras());
            assertEquals(Set.of("lag:no0", "lag:no1", "lag:no2"), new HashSet<>(c.chavesEscritas));
            assertEquals(Set.of("lag:no0", "lag:no1", "lag:no2"), new HashSet<>(c.chavesLidas));
            assertEquals(List.of("lag:no0", "lag:no1", "lag:no2"), s.chaves());
            assertEquals(Set.of("teste:1"), new HashSet<>(c.valoresEscritos), "nonce:rodada");
            assertTrue(r.escritas().stream().allMatch(e -> Boolean.TRUE.equals(e.ok())));
            RodadaDefasagem r2 = s.rodada();
            assertEquals(2, r2.rodada());
            assertEquals(6, r2.leituras(), "custo por rodada sem falha: 3 escritas e 6 leituras");
            assertTrue(c.valoresEscritos.contains("teste:2"));
        }
    }

    @Test
    @DisplayName("atraso conhecido num par: lag do par >= atraso; um enlace lento sozinho nao levanta o lag de no")
    void atrasoConhecido() {
        CanalFalso c = new CanalFalso(3);
        c.atrasoMs[0][1] = 80;
        try (SondaDefasagem s = sonda(c, 400, new AtomicLong(1))) {
            RodadaDefasagem r = s.rodada();
            RodadaDefasagem.Par p = par(r, "no0", "no1");
            assertTrue(p.visto(), p.toString());
            assertTrue(p.lagMs() >= 80 && p.lagMs() < 400, "lag " + p.lagMs());
            assertTrue(p.leituras() >= 2, "leu de novo a cada passo ate ver: " + p.leituras());
            assertTrue(r.lagMaxMs() < 80, "lag de no = menor dos pares: " + r.lagPorNo());
            assertTrue(r.lagMaxParMs() >= 80);
        }
    }

    @Test
    @DisplayName("no cortado (leitura cruzada nunca ve o valor): seus pares no teto, noSuspeito = ele, lagMax = teto")
    void noCortado() {
        CanalFalso c = new CanalFalso(3);
        c.cortados.add(2);
        c.preexistente("lag:no0", "outra-execucao:1"); // valor de uma execucao anterior: nao conta como visto
        long t0 = System.nanoTime();
        try (SondaDefasagem s = sonda(c, 200, new AtomicLong(1))) {
            RodadaDefasagem r = s.rodada();
            long durMs = (System.nanoTime() - t0) / MS;
            for (String outro : List.of("no0", "no1")) {
                assertFalse(par(r, "no2", outro).visto());
                assertFalse(par(r, outro, "no2").visto());
                assertEquals(200.0, par(r, outro, "no2").lagMs(), "nao visto = teto");
                assertTrue(par(r, outro, "no2").leituras() >= 5, "leu ate o teto, a cada passo");
            }
            assertTrue(par(r, "no0", "no1").visto() && par(r, "no1", "no0").visto());
            assertEquals("no2", r.noSuspeito());
            assertEquals(200.0, r.lagMaxMs());
            assertTrue(durMs < 1500, "a rodada termina no teto: " + durMs + " ms");
            EstadoDefasagem e = s.resumirAte(2);
            assertEquals(EstadoDefasagem.Nivel.ALTA, e.nivel());
            assertEquals("no2", e.noSuspeito());
            assertEquals(200.0, e.lagMaxMs());
            assertEquals(1, e.amostras());
        }
    }

    @Test
    @DisplayName("prazo: leitura que bloqueia alem do teto e interrompida; a rodada termina perto do teto e a seguinte roda")
    void leituraBloqueadaRespeitaOPrazo() throws Exception {
        CanalFalso c = new CanalFalso(3);
        c.leituraBloqueia = 1;
        c.bloqueioLeituraMs = 10_000;
        try (SondaDefasagem s = sonda(c, 200, new AtomicLong(1))) {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                long t0 = System.nanoTime();
                RodadaDefasagem r = s.rodada();
                assertTrue((System.nanoTime() - t0) / MS < 1500, "rodada presa na leitura bloqueada");
                assertFalse(par(r, "no0", "no1").visto());
                assertFalse(par(r, "no2", "no1").visto());
                assertTrue(par(r, "no1", "no0").visto(), "o que no1 escreve continua chegando");
                assertTrue(r.lagPorNo().get("no1") < 200, "no1 ainda troca com os outros como escritor");
            });
            c.leituraBloqueia = -1;
            RodadaDefasagem r2 = rodadaQuandoLivre(s); // as tarefas interrompidas cedem logo
            assertTrue(r2.pares().stream().allMatch(RodadaDefasagem.Par::visto));
        }
    }

    @Test
    @DisplayName("tarefa que nao cede a interrupcao: a rodada seguinte e recusada (nunca duas ao mesmo tempo)")
    void tarefaPresaImpedeNovaRodada() throws Exception {
        CanalFalso c = new CanalFalso(3);
        c.leituraPresa = 1;
        try (SondaDefasagem s = sonda(c, 100, new AtomicLong(1))) {
            RodadaDefasagem r = s.rodada();
            assertFalse(par(r, "no0", "no1").visto());
            assertThrows(IllegalStateException.class, s::rodada);
            c.leituraPresa = -1;
            c.liberar.countDown();
            RodadaDefasagem r2 = rodadaQuandoLivre(s);
            assertEquals(2, r2.rodada(), "a tarefa liberada terminou e a sonda voltou a rodar");
        }
    }

    @Test
    @DisplayName("escrita que falha: contada, o leitor continua tentando ate o teto; o lag do no fica pelos outros pares")
    void escritaFalha() {
        CanalFalso c = new CanalFalso(3);
        c.escritaFalha = 0;
        try (SondaDefasagem s = sonda(c, 150, new AtomicLong(1))) {
            RodadaDefasagem r = s.rodada();
            RodadaDefasagem.Escrita e0 = r.escritas().get(0);
            assertEquals(Boolean.FALSE, e0.ok());
            assertEquals("IllegalStateException", e0.erro());
            assertEquals(1, r.errosEscrita());
            assertEquals(1, s.errosEscrita());
            assertFalse(par(r, "no0", "no1").visto());
            assertTrue(par(r, "no1", "no0").visto());
            assertTrue(r.lagPorNo().get("no0") < 150);
        }
    }

    @Test
    @DisplayName("buffer: rodada pertence a janela do seu inicio; antes da origem fica fora; atrasada contada; fim nao recua")
    void bufferPorJanela() {
        CanalFalso c = new CanalFalso(3);
        AtomicLong t = new AtomicLong(-5);
        List<RodadaDefasagem> gravadas = Collections.synchronizedList(new ArrayList<>());
        SondaDefasagem s = sonda(c, 100, 1000 * MS, 0, t::get, gravadas::add);
        try {
            s.rodada(); // inicio -5 < origem 0: aquecimento
            assertEquals(1, s.rodadasAntesDaOrigem());
            assertEquals(EstadoDefasagem.indeterminada(), s.resumirAte(1000 * MS), "janela 0 sem rodada");
            t.set(500 * MS);
            s.rodada(); // pertence a [0, 1000 ms), ja resumida: entra atrasada na proxima
            t.set(1200 * MS);
            s.rodada();
            EstadoDefasagem e = s.resumirAte(2000 * MS);
            assertEquals(2, e.amostras());
            assertEquals(1, s.rodadasAtrasadas());
            assertThrows(IllegalArgumentException.class, () -> s.resumirAte(1999 * MS));
            assertEquals(3, s.rodadas());
        } finally {
            s.close();
        }
        assertEquals(2, gravadas.size(), "so as rodadas depois da origem vao ao ouvinte");
        assertTrue(gravadas.stream().allMatch(r -> r.tInicioNs() >= 0));
    }

    @Test
    @DisplayName("agenda: uma rodada por janela, comecando ate W - teto depois do inicio; aquecimento antes da origem")
    void agendaAlinhadaAGrade() throws Exception {
        CanalFalso c = new CanalFalso(3);
        long w = 300 * MS;
        long origem = System.nanoTime() + 200 * MS;
        List<RodadaDefasagem> gravadas = Collections.synchronizedList(new ArrayList<>());
        SondaDefasagem s = sonda(c, 100, w, origem, System::nanoTime, gravadas::add);
        s.iniciar();
        assertThrows(IllegalStateException.class, s::iniciar);
        TimeUnit.MILLISECONDS.sleep(200 + 4 * 300 + 150);
        s.close();
        assertTrue(s.rodadasAntesDaOrigem() >= 1, "aqueceu antes da origem");
        List<RodadaDefasagem> l = new ArrayList<>(gravadas);
        assertTrue(l.size() >= 3, "rodadas depois da origem: " + l.size() + ", puladas " + s.rodadasPuladas());
        Set<Long> janelas = new HashSet<>();
        for (RodadaDefasagem r : l) {
            long i = SondaDefasagem.indiceDaJanela(r.tInicioNs(), origem, w);
            assertTrue(i >= 0);
            assertTrue(SondaDefasagem.cabeNaJanela(r.tInicioNs(), origem + i * w, w, 100 * MS), "inicio fora: " + r);
            assertTrue(janelas.add(i), "duas rodadas na janela " + i);
            assertTrue(r.tNs() < origem + (i + 1) * w, "terminou depois do fim da janela: " + r);
        }
        assertThrows(IllegalStateException.class, s::rodada, "fechada");
        assertThrows(IllegalStateException.class, s::iniciar, "fechada");
        s.close(); // idempotente
    }

    @Test
    @DisplayName("P-5: um ouvinte (JSONL) que trava nao atrasa as rodadas seguintes; close espera a gravadora")
    void ouvinteLentoNaoAtrasaRodada() throws Exception {
        CanalFalso c = new CanalFalso(3);
        long w = 200 * MS;
        CountDownLatch primeiro = new CountDownLatch(1);
        List<RodadaDefasagem> gravadas = Collections.synchronizedList(new ArrayList<>());
        SondaDefasagem s = sonda(c, 50, w, System.nanoTime(), System::nanoTime, r -> {
            if (primeiro.getCount() > 0) {
                primeiro.countDown();
                try {
                    TimeUnit.MILLISECONDS.sleep(1000); // "disco travado"
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            gravadas.add(r);
        });
        s.iniciar();
        assertTrue(primeiro.await(2, TimeUnit.SECONDS));
        TimeUnit.MILLISECONDS.sleep(700); // a gravadora ainda esta presa na primeira linha (1 s)
        assertEquals(0, gravadas.size(), "a primeira gravacao ainda nao terminou");
        assertTrue(s.rodadas() >= 3, "rodadas durante o travamento da gravacao: " + s.rodadas());
        assertEquals(0, s.rodadasPuladas(), "V-W4 R6: a rodada segue na grade com a gravacao travada");
        long ate = System.nanoTime() + 3_000 * MS;
        while (gravadas.size() < 3 && System.nanoTime() < ate) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        s.close();
        assertTrue(gravadas.size() >= 3, "a gravadora esvaziou a fila depois do travamento: " + gravadas.size());
        assertEquals(0, s.errosOuvinte());
    }

    @Test
    @DisplayName("ouvinte que lanca: contado, a sonda segue")
    void ouvinteQueLanca() {
        CanalFalso c = new CanalFalso(3);
        SondaDefasagem s = sonda(c, 100, 10_000 * MS, 0, () -> 1, r -> {
            throw new IllegalStateException("x");
        });
        s.rodada();
        s.rodada();
        s.close();
        assertEquals(2, s.errosOuvinte());
    }

    @Test
    @DisplayName("construtor: prazo >= W, passo >= prazo, lagIn > prazo, menos de 2 nos, rotulo repetido e nonce invalido sao recusados")
    void construtorRecusa() {
        CanalFalso c = new CanalFalso(3);
        FonteDeTempo t = () -> 0;
        assertThrows(IllegalArgumentException.class, () -> sonda(c, 500, 500 * MS, 0, t, r -> { }));
        assertThrows(IllegalArgumentException.class, () -> sonda(c, 10, 500 * MS, 0, t, r -> { }));
        assertThrows(IllegalArgumentException.class, () -> sonda(c, 40, 500 * MS, 0, t, r -> { }), "lagIn 50 > 40");
        assertThrows(IllegalArgumentException.class, () -> sonda(new CanalFalso(1), 100, 500 * MS, 0, t, r -> { }));
        CanalFalso repetido = new CanalFalso(3) {
            @Override
            public String rotulo(int indice) {
                return "igual";
            }
        };
        assertThrows(IllegalArgumentException.class, () -> sonda(repetido, 100, 500 * MS, 0, t, r -> { }));
        assertThrows(IllegalArgumentException.class, () -> new SondaDefasagem(c, P, 100, 500 * MS, 0, t, r -> { },
                "com:doispontos"));
        assertThrows(IllegalArgumentException.class, () -> sonda(c, 100, 500 * MS, 0, t, null));
        assertThrows(IllegalArgumentException.class, () -> sonda(c, 100, 0, 0, t, r -> { }));
        try (SondaDefasagem s = new SondaDefasagem(c, P, 100, 500 * MS, 0, t, r -> { })) {
            assertEquals(100, s.tetoMs());
            assertEquals(List.of("no0", "no1", "no2"), s.rotulos());
        }
    }

    @Test
    @DisplayName("contadores do fim: nomes fixos, na mesma ordem dos valores")
    void contadores() {
        CanalFalso c = new CanalFalso(3);
        try (SondaDefasagem s = sonda(c, 100, new AtomicLong(1))) {
            s.rodada();
            assertEquals(SondaDefasagem.nomesDosContadores(), List.copyOf(s.contadores().keySet()));
            assertEquals(1L, s.contadores().get("defasagem_rodadas"));
            assertEquals(3L, s.contadores().get("defasagem_escritas"));
            assertEquals(6L, s.contadores().get("defasagem_leituras"));
            assertEquals(0L, s.contadores().get("defasagem_rodadas_puladas"));
        }
    }
}
