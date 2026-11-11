package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.1: tick de relogio do agregador (V-B26.1 secao 5.9, criterios CA-T1, CA-T2 e CA-T3; Gestor,
 * 2026-10-22). {@code avancarRelogio} tem a semantica de um evento sem conteudo: so avanca a marca
 * d'agua e fecha janelas pela mesma regra {@code marca - L >= fimNs}. {@code seqRegistro} conta
 * {@code registrar} e {@code registrarViolacao}, para o replay reinjetar o tick na mesma posicao.
 */
class AgregadorDeJanelaRelogioTest {

    private static final long W = 10;
    private static final long L = 3;
    private static final Referencia REF = Referencia.de(Map.of("Login", 5L));

    private static AgregadorDeJanela agr() {
        return new AgregadorDeJanela(0, W, L, REF);
    }

    private static EventoOperacao ok(long fim) {
        return new EventoOperacao("Login", fim, fim, "OK");
    }

    private static Janela vazia(long ini) {
        return new Janela(ini, ini + W, 0, 0, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ CA-T1

    @Test
    @DisplayName("CA-T1: sem eventos, o tick em fim(j) + L emite j vazia e contigua; um ns antes nao emite")
    void tickSemEventosEmiteVazias() {
        AgregadorDeJanela a = agr();
        assertEquals(List.of(), a.avancarRelogio(W + L - 1));
        List<JanelaObservada> f = a.avancarRelogio(W + L);
        assertEquals(List.of(vazia(0)), f.stream().map(JanelaObservada::janela).toList());
        List<JanelaObservada> g = a.avancarRelogio(3 * W + L);
        assertEquals(List.of(vazia(10), vazia(20)), g.stream().map(JanelaObservada::janela).toList());
        assertEquals(3 * W + L, a.marcaDaguaNs());
    }

    @Test
    @DisplayName("CA-T1: tick com agoraNs <= marca nao emite nada nem recua a marca")
    void tickNaoRecua() {
        AgregadorDeJanela a = agr();
        a.registrar(ok(25)); // fecha [0,10) e [10,20)? 25 - 3 = 22 >= 20: sim
        assertEquals(25, a.marcaDaguaNs());
        assertEquals(List.of(), a.avancarRelogio(25));
        assertEquals(List.of(), a.avancarRelogio(7));
        assertEquals(25, a.marcaDaguaNs());
        assertEquals(1, a.registrar(ok(26)).size() + a.avancarRelogio(33).size(), "[20,30) fecha em 33");
    }

    @Test
    @DisplayName("CA-T1: depois de encerrar, o tick lanca IllegalStateException (valor e fonte)")
    void tickDepoisDeEncerrar() {
        AgregadorDeJanela a = agr();
        a.encerrar();
        assertThrows(IllegalStateException.class, () -> a.avancarRelogio(100));
        assertThrows(IllegalStateException.class, () -> a.avancarRelogio(() -> 100));
        assertThrows(NullPointerException.class, () -> agr().avancarRelogio((FonteDeTempo) null));
    }

    @Test
    @DisplayName("tick torna atrasado o evento de janela que ele fechou; a mistura continua contigua")
    void tickFechaEDepoisEventoAtrasado() {
        AgregadorDeJanela a = agr();
        a.registrar(ok(4));
        List<JanelaObservada> f = new ArrayList<>(a.avancarRelogio(13)); // fecha [0,10)
        assertEquals(1, f.get(0).janela().total());
        assertEquals(List.of(), a.registrar(ok(9)));
        assertEquals(1, a.atrasadosTotal());
        f.addAll(a.registrar(ok(23)));
        f.addAll(a.avancarRelogio(43));
        for (int i = 0; i < f.size(); i++) {
            assertEquals(W * i, f.get(i).janela().inicioNs());
        }
        assertEquals(1, f.get(1).atrasados());
        assertEquals(4, f.size());
    }

    @Test
    @DisplayName("FonteDeTempo injetada: o agregador usa o agora dela, sem relogio de parede")
    void fonteInjetada() {
        AtomicLong falso = new AtomicLong(5);
        FonteDeTempo fonte = falso::get;
        AgregadorDeJanela a = agr();
        assertEquals(List.of(), a.avancarRelogio(fonte));
        falso.set(2 * W + L);
        assertEquals(2, a.avancarRelogio(fonte).size());
        assertEquals(2 * W + L, a.marcaDaguaNs());
    }

    @Test
    @DisplayName("seqRegistro conta registrar e registrarViolacao (inclusive atrasados); tick nao conta")
    void seqRegistro() {
        AgregadorDeJanela a = agr();
        assertEquals(0, a.seqRegistro());
        a.registrar(ok(1));
        a.registrarViolacao(ClasseViolacao.SEGURANCA, 2);
        a.avancarRelogio(50);
        assertEquals(2, a.seqRegistro());
        a.registrar(ok(3)); // atrasado
        a.registrarViolacao(ClasseViolacao.FORA_DO_GATILHO, 3); // atrasada
        assertEquals(4, a.seqRegistro());
        a.avancarRelogio(60);
        assertEquals(4, a.seqRegistro());
    }

    // ------------------------------------------------------------------ CA-T2

    @Test
    @DisplayName("CA-T2: 200 sequencias de semente fixa, com e sem ticks: total(com) + atrasados_extra(com) = total(sem) por janela")
    void propriedadeComESemTicks() {
        long sequenciasComAtrasoExtra = 0;
        for (long semente = 1; semente <= 200; semente++) {
            Random r = new Random(semente);
            List<long[]> acoes = new ArrayList<>(); // {0, end} evento; {1, agora} tick
            long t = 0;
            long max = 0;
            for (int i = 0; i < 300; i++) {
                t += r.nextInt(4);
                if (r.nextInt(8) == 0) {
                    long agora = t + r.nextInt((int) W);
                    acoes.add(new long[]{1, agora});
                    max = Math.max(max, agora);
                } else {
                    long fim = Math.max(0, t - r.nextInt(12)); // desordem, parte alem de L
                    acoes.add(new long[]{0, fim});
                    max = Math.max(max, fim);
                }
            }
            acoes.add(new long[]{0, max + W}); // sentinela: mesma marca final nos dois
            Execucao com = executar(acoes, true);
            Execucao sem = executar(acoes, false);

            assertEquals(sem.janelas.size(), com.janelas.size(), "semente " + semente);
            for (int i = 0; i < com.janelas.size(); i++) {
                assertEquals(W * i, com.janelas.get(i).janela().inicioNs(), "contigua, semente " + semente);
                assertEquals(W * i, sem.janelas.get(i).janela().inicioNs());
                long extra = com.atrasadosPorJanela.getOrDefault((long) i, 0L)
                        - sem.atrasadosPorJanela.getOrDefault((long) i, 0L);
                assertTrue(extra >= 0, "o tick so acrescenta atrasados, semente " + semente);
                assertEquals(sem.janelas.get(i).janela().total(), com.janelas.get(i).janela().total() + extra,
                        "janela " + i + ", semente " + semente);
                if (extra > 0) {
                    sequenciasComAtrasoExtra++;
                }
            }
            assertEquals(com.seq, sem.seq, "o tick nao conta em seqRegistro");
        }
        assertTrue(sequenciasComAtrasoExtra > 0, "a propriedade foi exercitada com atraso causado por tick");
    }

    private record Execucao(List<JanelaObservada> janelas, Map<Long, Long> atrasadosPorJanela, long seq) {
    }

    private static Execucao executar(List<long[]> acoes, boolean comTicks) {
        AgregadorDeJanela a = agr();
        List<JanelaObservada> out = new ArrayList<>();
        Map<Long, Long> atrasados = new TreeMap<>();
        for (long[] acao : acoes) {
            if (acao[0] == 1) {
                if (comTicks) {
                    out.addAll(a.avancarRelogio(acao[1]));
                }
                continue;
            }
            long antes = a.atrasadosTotal();
            out.addAll(a.registrar(ok(acao[1])));
            if (a.atrasadosTotal() > antes) {
                atrasados.merge(Math.floorDiv(acao[1], W), 1L, Long::sum);
            }
        }
        out.addAll(a.encerrar());
        return new Execucao(out, atrasados, a.seqRegistro());
    }

    // ------------------------------------------------------------------ CA-T3

    @RepeatedTest(3)
    @DisplayName("CA-T3: 8 threads de eventos e 1 de tick: conservacao e indices unicos e contiguos")
    void concorrenciaComTick() throws Exception {
        final int threads = 8;
        final int porThread = 20_000;
        AgregadorDeJanela a = new AgregadorDeJanela(0, 500, 2, REF);
        AtomicLong relogio = new AtomicLong();
        AtomicBoolean fim = new AtomicBoolean();
        List<JanelaObservada> emitidas = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        try {
            Future<Long> tick = pool.submit(() -> {
                largada.await();
                long ticks = 0;
                FonteDeTempo fonte = relogio::get; // mesmo relogio dos end_ns
                while (!fim.get()) {
                    emitidas.addAll(a.avancarRelogio(fonte));
                    ticks++;
                }
                return ticks;
            });
            List<Future<?>> carga = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                carga.add(pool.submit(() -> {
                    largada.await();
                    for (int i = 0; i < porThread; i++) {
                        long e = relogio.getAndIncrement();
                        emitidas.addAll(a.registrar(new EventoOperacao("Login", e - (i % 9), e, "OK")));
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> f : carga) {
                f.get(60, TimeUnit.SECONDS);
            }
            fim.set(true);
            assertTrue(tick.get(60, TimeUnit.SECONDS) > 0);
            emitidas.addAll(a.encerrar());

            List<JanelaObservada> ordenadas = new ArrayList<>(emitidas);
            ordenadas.sort((x, y) -> Long.compare(x.janela().inicioNs(), y.janela().inicioNs()));
            for (int i = 0; i < ordenadas.size(); i++) {
                assertEquals(500L * i, ordenadas.get(i).janela().inicioNs(), "janela " + i + " unica e sem buraco");
            }
            long n = (long) threads * porThread;
            long nasJanelas = ordenadas.stream().mapToLong(o -> o.janela().total()).sum();
            assertEquals(n, nasJanelas + a.atrasadosTotal() + a.descartadosNoEncerramento(), "eventos conservados");
            assertEquals(n, a.seqRegistro());
        } finally {
            fim.set(true);
            pool.shutdownNow();
        }
    }
}
