package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-41 (B-44.2, I-3): com um no parado ate o fim (AC-5), a leitura final por replica da carga S3 ficava presa sem prazo
 * em {@code WorkloadMain.materializar} ({@code entrySet} pelo cliente BASIC fixado no no parado, chamado pela
 * {@code rodadaUnica} do auditor; {@code jstack-preso.txt} do I-3). Em S3 a leitura de cada replica passa a ter o
 * prazo de leitura da sonda S_lag, o {@code --hotrod-timeout-ms}, contado sem resposta: a leitura desiste quando passa
 * o prazo sem chegar nenhuma entrada nova nem o fim da iteracao. A replica sem resposta vira "nao lida", com o motivo,
 * e o auditor a exclui da comparacao como ja fazia com a leitura que lanca (B-33). Fora do S3 nada muda.
 */
class LeituraReplicaComPrazoTest {

    static final long PRAZO_MS = 200;
    static final Instant T = Instant.parse("2026-11-05T12:00:00Z");

    /** Leitura que nunca responde; conta a interrupcao recebida. */
    static Function<Runnable, Map<String, SessionState>> presa(CountDownLatch interrompida) {
        return batida -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrompida.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrompida");
            }
            return Map.of();
        };
    }

    static Map<String, SessionState> mapa(SessionState... ss) {
        Map<String, SessionState> m = new HashMap<>();
        for (SessionState s : ss) {
            m.put(s.id(), s);
        }
        return m;
    }

    @Test
    @DisplayName("antes da primeira chamada: nao-chamada")
    void naoChamada() {
        LeituraReplicaComPrazo<SessionState> l = new LeituraReplicaComPrazo<>("sessoes@isn3:11422", PRAZO_MS,
                b -> Map.of());
        assertEquals("nao-chamada", l.ultimoResultado());
        assertEquals("sessoes@isn3:11422", l.rotulo());
        assertThrows(IllegalArgumentException.class, () -> new LeituraReplicaComPrazo<SessionState>("x", 0, b -> Map.of()));
    }

    @Test
    @DisplayName("replica sem resposta: desiste no prazo, interrompe a leitura e registra nao-lida:sem-resposta")
    void semRespostaVoltaNoPrazo() throws Exception {
        CountDownLatch interrompida = new CountDownLatch(1);
        LeituraReplicaComPrazo<SessionState> l = new LeituraReplicaComPrazo<>("sessoes@isn3:11422", PRAZO_MS,
                presa(interrompida));
        long t0 = System.nanoTime();
        RuntimeException e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(RuntimeException.class, l::get));
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertInstanceOf(LeituraReplicaComPrazo.ReplicaSemResposta.class, e);
        assertTrue(ms >= PRAZO_MS && ms < PRAZO_MS + 1500, ms + " ms");
        assertTrue(e.getMessage().contains("sessoes@isn3:11422") && e.getMessage().contains("200 ms"), e.getMessage());
        assertEquals("nao-lida:sem-resposta", l.ultimoResultado());
        assertTrue(interrompida.await(2, TimeUnit.SECONDS), "a leitura presa recebeu a interrupcao");
    }

    @Test
    @DisplayName("resposta lenta com progresso: entradas chegando a cada 60 ms por 600 ms nao estouram o prazo de 200 ms")
    void lentaComProgressoNaoEstoura() {
        SessionState s = SessionState.criar("s1", "u1", T);
        LeituraReplicaComPrazo<SessionState> l = new LeituraReplicaComPrazo<>("sessoes@isn1:11222", PRAZO_MS, b -> {
            Map<String, SessionState> m = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                dormir(60);
                b.run();
                m.put("s" + i, s);
            }
            return m;
        });
        Map<String, SessionState> m = assertTimeoutPreemptively(Duration.ofSeconds(5), l::get);
        assertEquals(10, m.size());
        assertEquals("lida", l.ultimoResultado());
    }

    @Test
    @DisplayName("progresso que para no meio: o prazo conta da ultima entrada recebida")
    void progressoQueParaEstoura() {
        CountDownLatch interrompida = new CountDownLatch(1);
        Function<Runnable, Map<String, SessionState>> presa = presa(interrompida);
        LeituraReplicaComPrazo<SessionState> l = new LeituraReplicaComPrazo<>("contadores@isn3:11422", PRAZO_MS, b -> {
            for (int i = 0; i < 3; i++) {
                dormir(100);
                b.run();
            }
            return presa.apply(b);
        });
        long t0 = System.nanoTime();
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(LeituraReplicaComPrazo.ReplicaSemResposta.class, l::get));
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ms >= 300 + PRAZO_MS, "o prazo recomeca a cada entrada: " + ms + " ms");
        assertEquals("nao-lida:sem-resposta", l.ultimoResultado());
    }

    @Test
    @DisplayName("leitura que lanca (DEGRADED): a mesma excecao sobe, como antes, e o motivo leva a classe")
    void erroPropaga() {
        LeituraReplicaComPrazo<SessionState> l = new LeituraReplicaComPrazo<>("sessoes@isn2:11322", PRAZO_MS, b -> {
            throw new IllegalStateException("ISPN000306: cache DEGRADED");
        });
        IllegalStateException e = assertThrows(IllegalStateException.class, l::get);
        assertEquals("ISPN000306: cache DEGRADED", e.getMessage());
        assertEquals("nao-lida:erro:IllegalStateException", l.ultimoResultado());
    }

    @Test
    @DisplayName("leitura que ignora a interrupcao: o chamador volta no prazo mesmo assim (thread daemon abandonada)")
    void ignoraInterrupcao() {
        AtomicBoolean parar = new AtomicBoolean(false);
        AtomicBoolean daemon = new AtomicBoolean(false);
        LeituraReplicaComPrazo<SessionState> l = new LeituraReplicaComPrazo<>("sessoes@isn3:11422", PRAZO_MS, b -> {
            daemon.set(Thread.currentThread().isDaemon());
            while (!parar.get()) {
                Thread.onSpinWait();
            }
            return Map.of();
        });
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(LeituraReplicaComPrazo.ReplicaSemResposta.class, l::get));
            assertTrue(daemon.get(), "a leitura roda numa thread daemon: nao segura a JVM no fim");
        } finally {
            parar.set(true);
        }
    }

    @Test
    @DisplayName("rodadaUnica do auditor com uma replica parada: nao trava, conta a falha e compara as outras")
    void auditorNaoTrava() {
        CountDownLatch i1 = new CountDownLatch(1);
        CountDownLatch i2 = new CountDownLatch(1);
        SessionState v = SessionState.criar("s1", "u1", T);
        SessionState inv = v.invalidar(SessionState.Motivo.LOGOUT, T);
        IdentityState id = IdentityState.criar("u1", T);
        Supplier<Map<String, SessionState>> a = () -> mapa(v);
        Supplier<Map<String, SessionState>> b = () -> mapa(inv);
        Supplier<Map<String, SessionState>> parada = new LeituraReplicaComPrazo<>("sessoes@isn3:11422", PRAZO_MS,
                presa(i1));
        Function<Runnable, Map<String, IdentityState>> presaId = batida -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                i2.countDown();
                Thread.currentThread().interrupt();
            }
            return Map.of();
        };
        Supplier<Map<String, IdentityState>> idParada = new LeituraReplicaComPrazo<>("contadores@isn3:11422", PRAZO_MS,
                presaId);
        InvariantAuditor aud = new InvariantAuditor();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> aud.rodadaUnica(List.of(a, b, parada),
                List.of(() -> Map.of("u1", id), () -> Map.of("u1", id), idParada)));
        assertEquals(2, aud.leiturasFalhas(), "sessoes e contadores do no parado");
        assertEquals(1, aud.total(), "a divergencia entre as replicas lidas continua detectada");
        assertEquals("I5", aud.drenar().get(0).invariante());
    }

    @Test
    @DisplayName("WorkloadMain.leitorDaReplica: em S3 com prazo (registrado para a linha), fora do S3 a leitura de antes")
    void leitorDaReplica() {
        List<LeituraReplicaComPrazo<?>> comPrazo = new ArrayList<>();
        CountDownLatch interrompida = new CountDownLatch(1);
        Supplier<Map<String, SessionState>> s3 = WorkloadMain.leitorDaReplica(true, PRAZO_MS, "sessoes@isn3:11422",
                presa(interrompida), comPrazo);
        assertEquals(1, comPrazo.size());
        assertSame(s3, comPrazo.get(0));
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(LeituraReplicaComPrazo.ReplicaSemResposta.class, s3::get));

        AtomicInteger chamadas = new AtomicInteger();
        SessionState s = SessionState.criar("s1", "u1", T);
        Supplier<Map<String, SessionState>> s1 = WorkloadMain.leitorDaReplica(false, PRAZO_MS, "sessoes@isn1:11222",
                b -> {
                    chamadas.incrementAndGet();
                    assertNotNull(b);
                    b.run();
                    assertFalse(Thread.currentThread().isDaemon() && Thread.currentThread().getName()
                            .startsWith("leitura-replica"), "fora do S3 a leitura roda na thread de quem chama");
                    return mapa(s);
                }, comPrazo);
        assertEquals(1, comPrazo.size(), "fora do S3 nada e registrado");
        assertEquals(1, s1.get().size());
        assertEquals(1, chamadas.get());
    }

    @Test
    @DisplayName("linha da saida: prazo e o resultado de cada replica, na ordem de --servers")
    void linha() throws Exception {
        LeituraReplicaComPrazo<SessionState> a = new LeituraReplicaComPrazo<>("sessoes@isn1:11222", PRAZO_MS,
                b -> Map.of());
        LeituraReplicaComPrazo<SessionState> c = new LeituraReplicaComPrazo<>("sessoes@isn3:11422", PRAZO_MS,
                presa(new CountDownLatch(1)));
        LeituraReplicaComPrazo<IdentityState> d = new LeituraReplicaComPrazo<>("contadores@isn3:11422", PRAZO_MS,
                b -> Map.of());
        a.get();
        assertThrows(LeituraReplicaComPrazo.ReplicaSemResposta.class, c::get);
        assertEquals("Leitura por replica: prazo_sem_resposta_ms=200 sessoes@isn1:11222=lida"
                        + " sessoes@isn3:11422=nao-lida:sem-resposta contadores@isn3:11422=nao-chamada",
                WorkloadMain.linhaLeituraPorReplica(PRAZO_MS, List.of(a, c, d)));
    }

    @Test
    @DisplayName("ligacao na fonte: abrirLeitoresMultiReplica usa leitorDaReplica com cli.scenarioS3 e cli.s3.hotrodTimeoutMs; executarRepeticao imprime a linha em S3")
    void ligacaoNaFonte() throws IOException {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/WorkloadMain.java");
        String m = DetectorFonte.mascarado(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        int[] abrir = DetectorFonte.corpo(m, "static LeitoresMultiReplica abrirLeitoresMultiReplica\\(");
        assertNotNull(abrir, "abrirLeitoresMultiReplica nao encontrado");
        String corpo = m.substring(abrir[0], abrir[1]).replaceAll("\\s+", " ");
        assertTrue(corpo.contains("leitorDaReplica(cli.scenarioS3, cli.s3.hotrodTimeoutMs,"), corpo);
        int[] rep = DetectorFonte.corpo(m, "static void executarRepeticao\\(Cli cli, int rep\\)");
        assertNotNull(rep, "executarRepeticao nao encontrado");
        String r = m.substring(rep[0], rep[1]).replaceAll("\\s+", " ");
        assertTrue(r.contains("linhaLeituraPorReplica(cli.s3.hotrodTimeoutMs,"), "a linha nao e impressa");
    }

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
