package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.1: N threads emitindo ao mesmo tempo (como os clientes do laco de carga). O total de
 * eventos e de violacoes se conserva: tudo o que entrou esta numa janela emitida, ou em
 * atrasados, ou no descarte do encerramento. As janelas saem contiguas e cada uma uma vez.
 */
class AgregadorDeJanelaConcorrenciaTest {

    private static final int THREADS = 8;
    private static final int EVENTOS_POR_THREAD = 20_000;
    private static final int VIOLACAO_A_CADA = 97;

    @RepeatedTest(3)
    @DisplayName("N threads: total conservado, janelas contiguas e unicas")
    void totalConservado() throws Exception {
        // W = 500 ns de relogio falso; L = 2 ns deixa passar so desordem curta, para haver atrasados.
        AgregadorDeJanela a = new AgregadorDeJanela(0, 500, 2,
                Referencia.de(Map.of("Login", 10L)));
        AtomicLong relogio = new AtomicLong();
        List<JanelaObservada> emitidas = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<long[]>> futuros = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int deslocamento = t;
                futuros.add(pool.submit(() -> {
                    largada.await();
                    long[] violacoes = new long[ClasseViolacao.values().length];
                    for (int i = 0; i < EVENTOS_POR_THREAD; i++) {
                        // end_ns lido antes da chamada, como em SessionOps.emitir: entre ler e
                        // registrar outra thread pode passar na frente (desordem real).
                        long fim = relogio.getAndIncrement();
                        String cod = (i % 10 == 0) ? "ERROR_TRANSPORT" : "OK";
                        emitidas.addAll(a.registrar(new EventoOperacao("Login", fim - (i % 20), fim, cod)));
                        if (i % VIOLACAO_A_CADA == 0) {
                            // as 4 classes se alternam (V-B26.1, T2)
                            ClasseViolacao c = ClasseViolacao.values()[(i / VIOLACAO_A_CADA + deslocamento) % 4];
                            emitidas.addAll(a.registrarViolacao(c, fim));
                            violacoes[c.ordinal()]++;
                        }
                    }
                    return violacoes;
                }));
            }
            largada.countDown();
            long[] enviadasPorClasse = new long[ClasseViolacao.values().length];
            for (Future<long[]> f : futuros) {
                long[] v = f.get(60, TimeUnit.SECONDS);
                for (int c = 0; c < v.length; c++) {
                    enviadasPorClasse[c] += v[c];
                }
            }
            long violacoesEnviadas = java.util.Arrays.stream(enviadasPorClasse).sum();
            for (long v : enviadasPorClasse) {
                assertTrue(v > 0, "todas as classes exercitadas");
            }
            emitidas.addAll(a.encerrar());

            List<JanelaObservada> ordenadas = new ArrayList<>(emitidas);
            ordenadas.sort((x, y) -> Long.compare(x.janela().inicioNs(), y.janela().inicioNs()));
            for (int i = 0; i < ordenadas.size(); i++) {
                assertEquals(500L * i, ordenadas.get(i).janela().inicioNs(), "janela " + i + " unica e sem buraco");
            }

            long n = (long) THREADS * EVENTOS_POR_THREAD;
            long nasJanelas = ordenadas.stream().mapToLong(o -> o.janela().total()).sum();
            long atrasadosNasJanelas = ordenadas.stream().mapToLong(JanelaObservada::atrasados).sum();
            assertEquals(n, nasJanelas + a.atrasadosTotal() + a.descartadosNoEncerramento(), "eventos conservados");
            assertTrue(atrasadosNasJanelas <= a.atrasadosTotal());

            long[] nasJanelasPorClasse = {
                    ordenadas.stream().mapToLong(o -> o.janela().violacoesSeguranca()).sum(),
                    ordenadas.stream().mapToLong(o -> o.janela().violacoesI2Esperadas()).sum(),
                    ordenadas.stream().mapToLong(JanelaObservada::violacoesI2ForaDoGatilho).sum(),
                    ordenadas.stream().mapToLong(JanelaObservada::violacoesForaDoGatilho).sum()};
            for (int c = 0; c < nasJanelasPorClasse.length; c++) {
                assertTrue(nasJanelasPorClasse[c] <= enviadasPorClasse[c], ClasseViolacao.values()[c].name());
            }
            long violNasJanelas = java.util.Arrays.stream(nasJanelasPorClasse).sum();
            assertEquals(violacoesEnviadas,
                    violNasJanelas + a.violacoesAtrasadasTotal() + a.violacoesDescartadasNoEncerramento(),
                    "violacoes conservadas");

            long erros = ordenadas.stream().mapToLong(o -> o.janela().erros()).sum();
            assertTrue(erros <= nasJanelas);
            assertEquals(nasJanelas, ordenadas.stream().mapToLong(o -> o.janela().elegiveisLat()).sum());
        } finally {
            pool.shutdownNow();
        }
    }
}
