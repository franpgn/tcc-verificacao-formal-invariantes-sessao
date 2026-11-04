package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AtuadorFalso;
import br.unipampa.tcc.controle.ClasseViolacao;
import br.unipampa.tcc.controle.ExecutorDeAcoes;
import br.unipampa.tcc.controle.PoliticaDeAcao;
import br.unipampa.tcc.controle.ViolacaoObservada;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3 (B-28.2): ligacao do laco de controle a carga ({@link LigacaoControlador}). Cobre o item 7 do prompt
 * ({@code --controller off} nao cria nada do pacote {@code controle}), os requisitos herdados H6, H7,
 * DT-001 e DT-002 e o teste ponta a ponta com {@link FakeCaches} (erro forcado leva a R2; volta a calma
 * leva a R4 depois de m janelas; JSONL valido linha a linha).
 */
class LigacaoControladorTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final long MS = 1_000_000L;

    @TempDir
    Path dir;

    /** W = 100 ms, n_err = 5, theta_err 0,5/0,1, k = 2, m = 2, cooldown 200 ms; latencia sem referencia. */
    static final String PARAMS_RAPIDOS = "{\"versao\":\"e2e-w3\",\"wNs\":100000000,\"nLat\":100,\"nErr\":5,"
            + "\"thetaLatIn\":0.5,\"thetaLatOut\":0.1,\"thetaErrIn\":0.5,\"thetaErrOut\":0.1,"
            + "\"k\":2,\"m\":2,\"cooldownNs\":200000000,\"estavelNs\":0,\"holdViolNs\":0}";

    Cli cli(String modo, String... extras) throws IOException {
        Path p = dir.resolve("controlador-e2e.json");
        Files.writeString(p, PARAMS_RAPIDOS, StandardCharsets.UTF_8);
        List<String> a = new ArrayList<>(List.of("--controller", modo, "--controller-config", p.toString(),
                "--controller-admin", ControladorCliTest.SERVIDORES, "--servers", ControladorCliTest.SERVIDORES,
                "--csv-dir", dir.toString(), "--replication", "async"));
        a.addAll(List.of(extras));
        Cli c = new Cli();
        new CommandLine(c).parseArgs(a.toArray(String[]::new));
        c.validar();
        return c;
    }

    static List<JsonNode> ler(Path p) throws IOException {
        List<JsonNode> r = new ArrayList<>();
        for (String linha : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            r.add(JSON.readTree(linha));
        }
        return r;
    }

    /** Pecas sem sonda de topologia, com tick de 10 ms e gancho falso. */
    static LigacaoControlador.Pecas pecasDeTeste(AtuadorFalso atuador, long tickNs) {
        return new LigacaoControlador.Pecas(null, atuador, PoliticaDeAcao.NENHUMA, new GanchoNulo(), tickNs,
                2000 * MS, 1500 * MS);
    }

    static final class GanchoNulo implements ExecutorDeAcoes.Gancho {
        @Override public void registrar(Thread t) { }
        @Override public void remover(Thread t) { }
    }

    LigacaoControlador abrir(Cli c, EpochClock relogio, RastroCliente rastro, long tickNs) throws IOException {
        long agora = relogio.paraEpochNanos(System.nanoTime());
        return LigacaoControlador.abrir(LigacaoControlador.carregar(c), c, 1, relogio, agora, rastro,
                pecasDeTeste(AtuadorFalso.proibido(), tickNs));
    }

    // ------------------------------------------------------------ item 7

    @Test
    @DisplayName("--controller off: a abertura do controlador nunca e chamada (nenhum objeto de controle)")
    void offNaoCria() throws IOException {
        Cli c = new Cli();
        new CommandLine(c).parseArgs();
        AtomicInteger chamadas = new AtomicInteger();
        assertNull(WorkloadMain.ligarControlador(c, cfg -> {
            chamadas.incrementAndGet();
            throw new AssertionError("abertura chamada com --controller off");
        }));
        assertEquals(0, chamadas.get());
        Cli s = cli("shadow");
        assertNull(WorkloadMain.ligarControlador(s, cfg -> {
            chamadas.incrementAndGet();
            assertEquals("e2e-w3", cfg.parametros().versao());
            return null;
        }));
        assertEquals(1, chamadas.get());
    }

    // ------------------------------------------------------------ violacoes

    @Test
    @DisplayName("DT-001/DT-002: I1, I4 e I2 REAL sao seguranca; I2 sem rastro, I2_JANELA_CASCATA, I2_CASCATA_INCOMPLETA e I3/I5/I6 nao")
    void classificacao() throws IOException {
        RastroCliente rastro = new RastroCliente(0);
        long t = 1_000_000_000L;
        rastro.registrar(SessionOps.OP_LOGIN, t, t + 1, OpResult.okLogin("sid-adm"), "u-adm");
        rastro.registrar(SessionOps.OP_BLOCK, t + 2, t + 3, OpResult.okNoop(), "u-adm");
        rastro.registrar(SessionOps.OP_LOGIN, t, t + 1, OpResult.okLogin("sid-lim"), "u-lim");
        EpochClock relogio = EpochClock.capturarAgora();
        try (LigacaoControlador lig = abrir(cli("shadow"), relogio, rastro, 3_600_000 * MS)) {
            InvariantAuditor.OuvinteDeViolacao o = lig.ouvinteDeViolacao();
            o.violacao("I1", "s1");
            o.violacao("I4", "s2");
            o.violacao("I2", "sid-adm");
            o.violacao("I2", "sid-lim");
            o.violacao("I2", "sid-sem-login");
            o.violacao(SessionOps.I2_JANELA_CASCATA, "sid-adm");
            o.violacao(SessionOps.I2_CASCATA_INCOMPLETA, "sid-adm");
            o.violacao("I3", "u-lim");
            o.violacao("I5", "s3");
            o.violacao("I6", "u-lim");
            List<ClasseViolacao> classes = lig.fonteDeViolacoes().drenar().stream()
                    .map(ViolacaoObservada::classe).toList();
            assertEquals(List.of(ClasseViolacao.SEGURANCA, ClasseViolacao.SEGURANCA, ClasseViolacao.SEGURANCA,
                    ClasseViolacao.I2_ESPERADA, ClasseViolacao.I2_FORA_DO_GATILHO,
                    ClasseViolacao.FORA_DO_GATILHO, ClasseViolacao.FORA_DO_GATILHO,
                    ClasseViolacao.FORA_DO_GATILHO, ClasseViolacao.FORA_DO_GATILHO, ClasseViolacao.FORA_DO_GATILHO),
                    classes);
            assertEquals(List.of(), lig.fonteDeViolacoes().drenar(), "drenar remove");
        }
    }

    @Test
    @DisplayName("sem rastro (S1/S2 sem --marker), I2 fica fora do gatilho")
    void semRastro() throws IOException {
        try (LigacaoControlador lig = abrir(cli("shadow"), EpochClock.capturarAgora(), null, 3_600_000 * MS)) {
            lig.ouvinteDeViolacao().violacao("I2", "qualquer");
            assertEquals(ClasseViolacao.I2_FORA_DO_GATILHO, lig.fonteDeViolacoes().drenar().get(0).classe());
        }
    }

    @Test
    @DisplayName("H7/DT-001: leitura concorrente com a invalidacao conta em i1_concorrentes e nao chega ao controlador")
    void i1ConcorrenteNaoChega() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        String sid = "sid-000004";
        base.dados.put(sid, SessionState.criar(sid, "u4", Instant.parse("2026-10-22T00:00:00Z")));
        AuditorCorridaTest.ArmazemPausavel<SessionState> armazem = new AuditorCorridaTest.ArmazemPausavel<>(base);
        InvariantAuditor auditor = new InvariantAuditor();
        List<String> recebidas = new ArrayList<>();
        auditor.definirOuvinte((inv, chave) -> recebidas.add(inv + ":" + chave));
        SessionOps ops = new SessionOps(armazem, new FakeCaches.Cache<>(), new LatencyRegistry(), auditor, null, null,
                "fake", null, new ConcurrentHashMap<>());
        Thread b = new Thread(() -> ops.validate(sid), "thread-B");
        armazem.alvo = b;
        b.start();
        assertTrue(armazem.leu.await(10, TimeUnit.SECONDS));
        ops.logout(sid);
        armazem.segue.countDown();
        b.join(10_000);
        assertEquals(1, ops.i1Concorrentes());
        assertEquals(0, auditor.total(), "H7: I1 = I4 = 0 conta como zero violacoes");
        assertEquals(List.of(), recebidas);

        // a violacao real (leitura velha depois da confirmacao) chega, com o mesmo rotulo do auditor
        AuditorCorridaTest.ArmazemVelho<SessionState> velho = new AuditorCorridaTest.ArmazemVelho<>(base);
        SessionOps ops2 = new SessionOps(velho, new FakeCaches.Cache<>(), new LatencyRegistry(), auditor, null, null,
                "fake", null, new ConcurrentHashMap<>());
        ops2.logout(sid);
        velho.versaoVelha = SessionState.criar(sid, "u4", Instant.parse("2026-10-22T00:00:00Z"));
        ops2.validate(sid);
        assertEquals(List.of("I1:" + sid), recebidas);
    }

    // ------------------------------------------------------------ eventos

    @Test
    @DisplayName("ouvinte de evento recebe operacao, instantes em epoch-ns e return_code; sem ouvinte nada muda")
    void ouvinteDeEvento() {
        FakeCaches.Cache<SessionState> s = new FakeCaches.Cache<>();
        EpochClock relogio = EpochClock.de(1_000_000_000_000L, 0L);
        SessionOps ops = new SessionOps(s, new FakeCaches.Cache<>(), new LatencyRegistry(), new InvariantAuditor(),
                null, relogio, "fake");
        List<String> recebidos = new ArrayList<>();
        ops.validate("sid-x"); // sem ouvinte: nenhum efeito
        ops.definirOuvinteDeEvento((op, ini, fim, rc) -> {
            assertTrue(fim >= ini && ini > 1_000_000_000_000L, ini + " " + fim);
            recebidos.add(op + ":" + rc);
        });
        ops.validate("sid-x");
        s.antes = (op, k) -> {
            throw new IllegalStateException("particao");
        };
        ops.validate("sid-x");
        assertEquals(List.of("Validate:NOT_FOUND", "Validate:ERROR_TRANSPORT"), recebidos);
    }

    // ------------------------------------------------------------ ponta a ponta

    @Test
    @DisplayName("ponta a ponta (FakeCaches, relogio real): erro forcado -> R2; calma -> R4 depois de m janelas; JSONL valido; H6")
    void pontaAPonta() throws Exception {
        Cli c = cli("shadow");
        EpochClock relogio = EpochClock.capturarAgora();
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        InvariantAuditor auditor = new InvariantAuditor();
        SessionOps ops = new SessionOps(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), auditor, null,
                relogio, "fake");
        LigacaoControlador lig = abrir(c, relogio, null, 10 * MS);
        ops.definirOuvinteDeEvento(lig.ouvinteDeEvento());
        auditor.definirOuvinte(lig.ouvinteDeViolacao());
        sessoes.antes = (op, k) -> {
            throw new IllegalStateException("erro forcado");
        };
        carga(ops, 600);
        sessoes.antes = (op, k) -> { };
        carga(ops, 900);
        lig.close();
        List<JsonNode> dec = ler(dir.resolve("decisoes-rep-001.jsonl"));
        List<JsonNode> obs = ler(dir.resolve("observaveis-rep-001.jsonl"));
        assertEquals("meta", dec.get(0).get("tipo").asText());
        assertEquals(3, dec.get(0).get("nos_esperados").asInt(), "H6: itens de --servers");
        assertEquals(3, obs.get(0).get("nos_esperados").asInt());
        assertEquals("shadow", dec.get(0).get("modo").asText());
        assertEquals("async", dec.get(0).get("replicacao").asText());
        assertEquals("S1", dec.get(0).get("cenario").asText());
        assertEquals("e2e-w3", dec.get(0).get("param_versao").asText());
        List<JsonNode> transicoes = dec.stream()
                .filter(n -> "decisao".equals(n.get("tipo").asText()) && n.get("transicao").asBoolean()).toList();
        assertTrue(transicoes.size() >= 2, "transicoes: " + transicoes);
        assertEquals("R2", transicoes.get(0).get("regra").asText());
        assertEquals("ERRO", transicoes.get(0).get("severidade").asText());
        assertEquals("R4", transicoes.get(1).get("regra").asText());
        assertTrue(transicoes.get(1).get("sinais").get("janelas_calmas").asInt() >= 2);
        assertEquals("fim", dec.get(dec.size() - 1).get("tipo").asText());
        assertEquals("fim", obs.get(obs.size() - 1).get("tipo").asText());
        long fimAnterior = -1;
        for (JsonNode n : dec) {
            if ("decisao".equals(n.get("tipo").asText())) {
                long f = n.get("t_fim_ns").asLong();
                assertTrue(fimAnterior < 0 || f - fimAnterior == 100 * MS, "janelas contiguas de 100 ms");
                fimAnterior = f;
            }
        }
        assertFalse(lig.linhaResumo().isEmpty());
        assertTrue(lig.linhaResumo().startsWith("Controlador: modo=shadow"), lig.linhaResumo());
    }

    private static void carga(SessionOps ops, long ms) {
        long fim = System.nanoTime() + ms * MS;
        int i = 0;
        while (System.nanoTime() < fim) {
            ops.validate("sid-" + (i++ % 50));
            if (i % 50 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Test
    @DisplayName("encerrar e idempotente e depois dele o ouvinte de evento e ignorado (nao lanca na carga)")
    void encerrarIdempotente() throws Exception {
        LigacaoControlador lig = abrir(cli("active", "--controller-reference",
                Files.writeString(dir.resolve("ref.json"), ControladorCliTest.referencia("S1", "async")).toString()),
                EpochClock.capturarAgora(), null, 10 * MS);
        lig.close();
        lig.close();
        long t = System.currentTimeMillis() * 1_000_000L;
        lig.ouvinteDeEvento().evento("Validate", t, t + 1, "VALID");
        lig.ouvinteDeViolacao().violacao("I1", "x");
        JsonNode meta = ler(dir.resolve("decisoes-rep-001.jsonl")).get(0);
        assertEquals("active", meta.get("modo").asText());
        assertTrue(meta.get("referencia_sha256").asText().length() == 64);
    }
}
