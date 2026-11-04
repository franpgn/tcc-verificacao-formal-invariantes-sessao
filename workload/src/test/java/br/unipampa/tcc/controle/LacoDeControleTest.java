package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3 (B-28.2): laco de controle com relogio falso. Fecha as janelas do agregador no tick
 * (CA-T4 da V-B26.1), avalia o nucleo com topologia e defasagem, grava os dois JSONL e entrega a
 * decisao a politica e ao executor de acoes. Violacao e gatilho de primeira classe (G-1): R1 na janela
 * em que e vista, mesmo em cooldown.
 */
class LacoDeControleTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;
    static final ObjectMapper JSON = new ObjectMapper();

    static final AcaoCluster AC1 = ExecutorDeAcoesTest.AC1;
    static final AcaoCluster AC2 = ExecutorDeAcoesTest.AC2;

    @TempDir
    Path dir;

    /** Politica de teste: ao entrar em CONSERVADOR pede AC-1 e AC-2 (sem regra da tabela 4.5). */
    static final PoliticaDeAcao ENTRA_E_CONGELA = new PoliticaDeAcao() {
        @Override
        public PlanoDeAcao planejar(Decisao d, EstadoTopologia t, EstadoDefasagem l, List<AcaoCluster> ativas) {
            if (d.transicao() && d.perfil() == Perfil.CONSERVADOR) {
                return new PlanoDeAcao(List.of(), List.of(AC1, AC2));
            }
            return PlanoDeAcao.VAZIO;
        }

        @Override
        public String nome() {
            return "entra-e-congela";
        }
    };

    /** Montagem do laco com relogio falso, fontes falsas e JSONL num diretorio temporario. */
    final class Montagem {
        final AtomicLong relogio = new AtomicLong(0);
        final ConcurrentLinkedQueue<ViolacaoObservada> violacoes = new ConcurrentLinkedQueue<>();
        final List<RegistroAcao> acoes = Collections.synchronizedList(new ArrayList<>());
        final ExecutorDeAcoesTest.GanchoFalso gancho = new ExecutorDeAcoesTest.GanchoFalso();
        final AtuadorFalso atuador;
        final ExecutorDeAcoes executor;
        final LacoDeControle laco;
        final Path obs = dir.resolve("observaveis-rep-001.jsonl");
        final Path dec = dir.resolve("decisoes-rep-001.jsonl");

        Montagem(ModoAtuacao modo, AtuadorFalso atuador, PoliticaDeAcao politica,
                 LongFunction<EstadoTopologia> topologia) throws IOException {
            this(modo, atuador, politica, topologia, 0L);
        }

        Montagem(ModoAtuacao modo, AtuadorFalso atuador, PoliticaDeAcao politica,
                 LongFunction<EstadoTopologia> topologia, long origemNs) throws IOException {
            this(modo, atuador, politica, topologia, origemNs, 2 * S);
        }

        Montagem(ModoAtuacao modo, AtuadorFalso atuador, PoliticaDeAcao politica,
                 LongFunction<EstadoTopologia> topologia, long origemNs, long prazoChamadaNs) throws IOException {
            this.atuador = atuador;
            ParametrosControlador p = ParametrosControlador.padrao();
            TrilhaJsonl tObs = TrilhaJsonl.abrir(obs);
            TrilhaJsonl tDec = TrilhaJsonl.abrir(dec);
            this.executor = new ExecutorDeAcoes(modo, atuador, prazoChamadaNs, relogio::get,
                    r -> tDec.escrever(FormatoJsonl.acao(r)), gancho);
            MetaExecucao meta = new MetaExecucao("teste-0", 1, modo, "S3", "async", 3, p, null, null,
                    AgregadorDeJanela.TOLERANCIA_PADRAO_NS, S, origemNs, atuador.nome(), politica.nome(),
                    true, false);
            this.laco = new LacoDeControle(new LacoDeControle.Componentes(meta,
                    new AgregadorDeJanela(origemNs, p, Referencia.vazia()), new Controlador(p),
                    topologia::apply, fim -> EstadoDefasagem.indeterminada(),
                    () -> drenar(violacoes), politica, executor, relogio::get, tObs, tDec));
        }

        void tickEm(long tNs) {
            relogio.set(tNs);
            laco.tick();
        }

        /** Ticks a cada segundo de {@code de} ate {@code ate} (inclusive), em segundos. */
        void ticks(int de, int ate) {
            for (int s = de; s <= ate; s++) {
                tickEm(s * S);
            }
        }
    }

    static List<ViolacaoObservada> drenar(ConcurrentLinkedQueue<ViolacaoObservada> q) {
        List<ViolacaoObservada> l = new ArrayList<>();
        ViolacaoObservada v;
        while ((v = q.poll()) != null) {
            l.add(v);
        }
        return l;
    }

    static final EstadoTopologia OK = new EstadoTopologia(true, 2, 0, 3);
    static final EstadoTopologia ANOMALA = new EstadoTopologia(true, 2, 1, 2);

    static List<JsonNode> ler(Path p) throws IOException {
        List<JsonNode> r = new ArrayList<>();
        for (String linha : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            r.add(JSON.readTree(linha)); // falha se a linha nao for JSON valido
        }
        return r;
    }

    static List<JsonNode> doTipo(List<JsonNode> l, String tipo) {
        return l.stream().filter(n -> tipo.equals(n.path("tipo").asText())).toList();
    }

    // ------------------------------------------------------------------ CA-T4

    @Test
    @DisplayName("CA-T4: sem eventos e com topologia anomala desde 2 s, o tick leva a R2 em ate W + L + 1 s")
    void ticksFechamJanelasSemEventos() throws IOException {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA,
                fim -> fim > 2 * S ? ANOMALA : OK);
        long decididoEm = -1;
        for (int s = 1; s <= 30 && decididoEm < 0; s++) {
            m.tickEm(s * S);
            Decisao d = m.laco.ultimaDecisao();
            if (d != null && d.perfil() == Perfil.CONSERVADOR) {
                decididoEm = s * S;
                assertEquals(Decisao.Regra.R2, d.regra());
                assertEquals(5 * S, d.fimNs(), "tempo do nucleo = fim da janela");
            }
        }
        long limite = 2 * S + ParametrosControlador.padrao().wNs() + AgregadorDeJanela.TOLERANCIA_PADRAO_NS + S;
        assertTrue(decididoEm > 0 && decididoEm <= limite, "decidido em " + decididoEm + ", limite " + limite);
        m.laco.encerrar();
    }

    @Test
    @DisplayName("CA-T4 regressao: sem tick e sem eventos nenhuma janela fecha e nada e decidido")
    void semTickNaoDecide() throws IOException {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA,
                fim -> ANOMALA);
        m.relogio.set(30 * S); // o tempo passa, mas ninguem chama tick()
        assertNull(m.laco.ultimaDecisao());
        assertEquals(0, m.laco.decisoes());
    }

    // ------------------------------------------------------------- G-1 / R1

    /** Topologia anomala so na janela 0; violacao I1 em 37 s (janela 7, no cooldown da saida em 35 s). */
    Montagem violacaoNoCooldown(ModoAtuacao modo, AtuadorFalso atuador) throws IOException {
        Montagem m = new Montagem(modo, atuador, ENTRA_E_CONGELA, fim -> fim == 5 * S ? ANOMALA : OK);
        m.ticks(1, 36);
        assertEquals(Perfil.NORMAL, m.laco.ultimaDecisao().perfil(), "saiu por R4 em 35 s");
        assertEquals(Decisao.Regra.R4, m.laco.ultimaDecisao().regra());
        m.violacoes.add(new ViolacaoObservada(ClasseViolacao.SEGURANCA, 37 * S));
        m.ticks(37, 41);
        return m;
    }

    @Test
    @DisplayName("G-1 active: violacao I1 forcada leva a CONSERVADOR (R1) na janela em que e vista, em cooldown, e o atuador age")
    void violacaoEmCooldownActive() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        Montagem m = violacaoNoCooldown(ModoAtuacao.ACTIVE, a);
        Decisao d = m.laco.ultimaDecisao();
        assertEquals(40 * S, d.fimNs(), "janela [35 s, 40 s) contem a violacao");
        assertEquals(Decisao.Regra.R1, d.regra());
        assertTrue(d.transicao());
        assertFalse(d.sinais().cooldownExpirado(), "o cooldown da saida em 35 s ainda valia");
        assertTrue(m.executor.aguardarFilaVazia(5 * S));
        m.laco.encerrar();
        // entrada em 5 s (AC-1, AC-2), R4 em 35 s (reverte), R1 em 40 s (AC-1, AC-2), encerramento (reverte)
        List<String> ops = a.chamadas().stream().map(c -> c.operacao() + ":" + c.acao().tipo().rotulo()).toList();
        assertEquals(List.of("aplicar:AC-1", "aplicar:AC-2", "reverter:AC-2", "reverter:AC-1",
                "aplicar:AC-1", "aplicar:AC-2", "reverter:AC-2", "reverter:AC-1"), ops);
        List<JsonNode> acoes = doTipo(ler(m.dec), "acao");
        assertEquals(8, acoes.size());
        assertTrue(acoes.stream().allMatch(n -> n.get("executada").asBoolean()));
        assertEquals("R4", acoes.get(2).get("origem").asText());
        assertEquals("ENCERRAMENTO", acoes.get(7).get("origem").asText());
    }

    @Test
    @DisplayName("G-1 shadow: a mesma decisao R1 e gravada, as acoes saem previstas e o atuador nunca e chamado")
    void violacaoEmCooldownShadow() throws Exception {
        AtuadorFalso a = AtuadorFalso.proibido();
        Montagem m = violacaoNoCooldown(ModoAtuacao.SHADOW, a);
        assertEquals(Decisao.Regra.R1, m.laco.ultimaDecisao().regra());
        m.laco.encerrar();
        assertEquals(List.of(), a.chamadas());
        List<JsonNode> acoes = doTipo(ler(m.dec), "acao");
        assertEquals(8, acoes.size());
        assertTrue(acoes.stream().noneMatch(n -> n.get("executada").asBoolean()));
        assertTrue(acoes.stream().allMatch(n -> "PREVISTA".equals(n.get("resultado").asText())));
        List<JsonNode> ds = doTipo(ler(m.dec), "decisao");
        JsonNode r1 = ds.stream().filter(n -> "R1".equals(n.get("regra").asText())).findFirst().orElseThrow();
        assertEquals("shadow", r1.get("modo").asText());
        assertEquals("CONSERVADOR", r1.get("perfil").asText());
    }

    // --------------------------------------------------------- JSONL contrato

    @Test
    @DisplayName("JSONL: meta na 1a linha dos dois arquivos; uma decisao e uma janela por janela, contiguas; fim no encerramento")
    void jsonlLinhaALinha() throws IOException {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA, fim -> OK);
        for (int i = 0; i < 40; i++) {
            long fim = i * 250 * MS + 10 * MS;
            m.laco.registrarEvento(new EventoOperacao("Validate", fim - 3 * MS, fim, i % 10 == 0 ? "ERROR_TRANSPORT" : "VALID"));
        }
        m.ticks(1, 22);
        LacoDeControle.Resumo r = m.laco.encerrar();
        List<JsonNode> obs = ler(m.obs);
        List<JsonNode> dec = ler(m.dec);
        for (List<JsonNode> l : List.of(obs, dec)) {
            JsonNode meta = l.get(0);
            assertEquals("meta", meta.get("tipo").asText());
            assertEquals(3, meta.get("nos_esperados").asInt());
            assertEquals("shadow", meta.get("modo").asText());
            assertEquals("async", meta.get("replicacao").asText());
            assertEquals(ParametrosControlador.VERSAO_PADRAO, meta.get("param_versao").asText());
            assertTrue(meta.get("param_sha256").isNull());
            assertEquals("fim", l.get(l.size() - 1).get("tipo").asText());
        }
        assertEquals(FormatoJsonl.ESQUEMA_OBSERVAVEIS, obs.get(0).get("esquema").asText());
        assertEquals(FormatoJsonl.ESQUEMA_DECISOES, dec.get(0).get("esquema").asText());
        List<JsonNode> janelas = doTipo(obs, "janela");
        List<JsonNode> decisoes = doTipo(dec, "decisao");
        assertEquals(4, janelas.size(), "janelas de 5 s fechadas ate 22 s: [0,5) .. [15,20)");
        assertEquals(janelas.size(), decisoes.size());
        assertEquals(r.janelas(), janelas.size());
        for (int i = 0; i < janelas.size(); i++) {
            assertEquals(i * 5 * S, janelas.get(i).get("t_ini_ns").asLong());
            assertEquals((i + 1) * 5 * S, janelas.get(i).get("t_fim_ns").asLong());
            assertEquals(janelas.get(i).get("t_fim_ns"), decisoes.get(i).get("t_fim_ns"));
        }
        assertEquals(20, janelas.get(0).get("total").asLong());
        assertEquals(2, janelas.get(0).get("erros").asLong());
        long soma = janelas.stream().mapToLong(n -> n.get("total").asLong()).sum();
        assertEquals(40, soma);
        JsonNode d0 = decisoes.get(0);
        assertEquals(List.of("tipo", "t_fim_ns", "modo", "perfil", "transicao", "regra", "motivos", "severidade",
                "sinais", "param_sha256"), campos(d0));
        assertEquals("INDETERMINADA", d0.get("sinais").get("defasagem").asText());
        assertTrue(janelas.get(0).has("top") && janelas.get(0).has("lag") && janelas.get(0).has("viol"));
        List<JsonNode> ticks = doTipo(obs, "tick");
        assertFalse(ticks.isEmpty(), "ticks que fecham janela sao gravados para o replay");
        for (JsonNode t : ticks) {
            assertTrue(t.has("seq") && t.has("agora_ns"));
        }
    }

    static List<String> campos(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.fieldNames().forEachRemaining(r::add);
        return r;
    }

    // ------------------------------------------------------------ R4 e pilha

    @Test
    @DisplayName("R4 reverte tudo pela pilha (LIFO) e o encerramento nao tem mais nada a reverter")
    void r4ReverteTudo() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        Montagem m = new Montagem(ModoAtuacao.ACTIVE, a, ENTRA_E_CONGELA, fim -> fim == 5 * S ? ANOMALA : OK);
        m.ticks(1, 36);
        assertTrue(m.executor.aguardarFilaVazia(5 * S));
        assertEquals(List.of(), m.executor.ativas());
        m.laco.encerrar();
        List<String> ops = a.chamadas().stream().map(c -> c.operacao() + ":" + c.acao().tipo().rotulo()).toList();
        assertEquals(List.of("aplicar:AC-1", "aplicar:AC-2", "reverter:AC-2", "reverter:AC-1"), ops);
    }

    // ---------------------------------------------------------- origem

    @Test
    @DisplayName("antes da origem (warm-up): eventos e violacoes sao descartados e contados, sem R1 na janela 0")
    void antesDaOrigem() throws IOException {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA,
                fim -> OK, 10 * S);
        m.laco.registrarEvento(new EventoOperacao("Validate", 9 * S, 9 * S + MS, "VALID"));
        m.violacoes.add(new ViolacaoObservada(ClasseViolacao.SEGURANCA, 9 * S));
        m.ticks(9, 16);
        Decisao d = m.laco.ultimaDecisao();
        assertNotNull(d);
        assertEquals(15 * S, d.fimNs());
        assertEquals(Perfil.NORMAL, d.perfil(), "violacao do warm-up nao dispara R1");
        LacoDeControle.Resumo r = m.laco.encerrar();
        assertEquals(1, r.eventosAntesDaOrigem());
        assertEquals(1, r.violacoesAntesDaOrigem());
        JsonNode fim = ler(m.obs).get(ler(m.obs).size() - 1);
        assertEquals(1, fim.get("eventos_antes_da_origem").asLong());
        assertEquals(1, fim.get("violacoes_antes_da_origem").asLong());
    }

    @Test
    @DisplayName("violacao fora do gatilho (I2 esperada, I2 fora, I3/I5/I6) nao dispara R1")
    void foraDoGatilho() throws IOException {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA, fim -> OK);
        m.violacoes.add(new ViolacaoObservada(ClasseViolacao.I2_ESPERADA, 1 * S));
        m.violacoes.add(new ViolacaoObservada(ClasseViolacao.I2_FORA_DO_GATILHO, 2 * S));
        m.violacoes.add(new ViolacaoObservada(ClasseViolacao.FORA_DO_GATILHO, 3 * S));
        m.ticks(4, 6);
        Decisao d = m.laco.ultimaDecisao();
        assertEquals(Perfil.NORMAL, d.perfil());
        assertEquals(0, d.sinais().violacoesSeguranca());
        assertEquals(1, d.sinais().violacoesI2Esperadas());
        m.laco.encerrar();
        JsonNode j = doTipo(ler(m.obs), "janela").get(0);
        assertEquals(1, j.get("viol").get("i2_fora_do_gatilho").asLong());
        assertEquals(1, j.get("viol").get("fora_do_gatilho").asLong());
    }

    // ---------------------------------------------------------- thread

    @Test
    @DisplayName("iniciar cria a thread daemon controlador-laco; encerrar a para e fecha os arquivos (idempotente)")
    void threadDoLaco() throws Exception {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA, fim -> OK);
        m.laco.iniciar(10 * MS);
        Thread t = Thread.getAllStackTraces().keySet().stream()
                .filter(x -> x.getName().equals(LacoDeControle.NOME_THREAD)).findFirst().orElseThrow();
        assertTrue(t.isDaemon());
        Thread.sleep(50);
        assertTrue(m.laco.ticks() > 0, "a thread chamou tick()");
        m.laco.encerrar();
        t.join(2000);
        assertFalse(t.isAlive());
        m.laco.encerrar();
        assertEquals("fim", ler(m.dec).get(ler(m.dec).size() - 1).get("tipo").asText());
    }

    @Test
    @DisplayName("CA-T3 ao vivo: 8 threads de eventos e a thread de tick conservam o total")
    void concorrenciaComTick() throws Exception {
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA, fim -> OK);
        AtomicLong agora = new AtomicLong(0);
        int threads = 8;
        int porThread = 5000;
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        for (int k = 0; k < threads; k++) {
            int kk = k;
            Thread t = new Thread(() -> {
                try {
                    largada.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < porThread; i++) {
                    long fim = agora.addAndGet(MS) + kk;
                    m.laco.registrarEvento(new EventoOperacao("Validate", fim - 1, fim, "VALID"));
                }
            });
            ts.add(t);
            t.start();
        }
        Thread tick = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                m.relogio.set(agora.get());
                m.laco.tick();
            }
        });
        largada.countDown();
        tick.start();
        for (Thread t : ts) {
            t.join();
        }
        tick.interrupt();
        tick.join();
        LacoDeControle.Resumo r = m.laco.encerrar();
        List<JsonNode> janelas = doTipo(ler(m.obs), "janela");
        long nasJanelas = janelas.stream().mapToLong(n -> n.get("total").asLong()).sum();
        assertEquals((long) threads * porThread, nasJanelas + r.atrasadosTotal() + r.descartadosNoEncerramento());
        for (int i = 0; i < janelas.size(); i++) {
            assertEquals(i * 5 * S, janelas.get(i).get("t_ini_ns").asLong(), "janelas contiguas");
        }
    }

    // ---------------------------------------------------------- N3 (V-W3)

    @Test
    @DisplayName("N3: linhas_perdidas na linha fim dos dois arquivos; linhas que chegam depois do fim sao contadas")
    void linhasPerdidas() throws Exception {
        AtuadorFalso a = new AtuadorFalso().bloquear();
        Montagem m = new Montagem(ModoAtuacao.ACTIVE, a, ENTRA_E_CONGELA, fim -> fim == 5 * S ? ANOMALA : OK,
                0L, 100 * MS);
        m.ticks(1, 6); // R2 em 5 s: AC-1 (travada no atuador) e AC-2 (na fila)
        assertTrue(a.esperarPrimeira());
        LacoDeControle.Resumo r = m.laco.encerrar();
        assertFalse(r.executorNoPrazo(), "atuador travado: o encerramento estoura T_enc");
        List<JsonNode> dec = ler(m.dec);
        JsonNode fimDec = dec.get(dec.size() - 1);
        assertEquals("fim", fimDec.get("tipo").asText());
        assertEquals(0, fimDec.get("linhas_perdidas").asLong());
        assertEquals(200 * MS, fimDec.get("prazo_encerramento_ns").asLong(), "(0 + 1 em curso + 1) x 100 ms");
        JsonNode fimObs = ler(m.obs).get(ler(m.obs).size() - 1);
        assertEquals(0, fimObs.get("linhas_perdidas").asLong());
        a.liberar();
        assertTrue(m.executor.aguardarFilaVazia(5 * S));
        // AC-1 APLICADA, AC-2 DESCARTADA e a reversao de AC-1 chegam com a trilha ja fechada
        assertEquals(3, m.laco.linhasPerdidas());
        assertEquals(dec.size(), ler(m.dec).size(), "nada foi escrito depois do fim");
    }

    // ---------------------------------------------------------- politica

    @Test
    @DisplayName("politica que lanca nao derruba o laco: erro contado no fim")
    void politicaQueFalha() throws IOException {
        PoliticaDeAcao quebrada = (d, t, l, a) -> {
            throw new IllegalStateException("bug");
        };
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), quebrada, fim -> OK);
        m.ticks(1, 11);
        LacoDeControle.Resumo r = m.laco.encerrar();
        assertEquals(2, r.janelas());
        assertEquals(2, r.errosPolitica());
        JsonNode fim = ler(m.dec).get(ler(m.dec).size() - 1);
        assertEquals(2, fim.get("erros_politica").asLong());
    }

    @Test
    @DisplayName("politica recebe as acoes ativas e pode pedir reversao especifica")
    void politicaReverteEspecifica() throws Exception {
        List<List<AcaoCluster>> vistas = Collections.synchronizedList(new ArrayList<>());
        PoliticaDeAcao p = (d, t, l, ativas) -> {
            vistas.add(ativas);
            if (d.fimNs() == 5 * S) {
                return new PlanoDeAcao(List.of(), List.of(AC1, AC2));
            }
            if (d.fimNs() == 10 * S) {
                return new PlanoDeAcao(List.of(AC1), List.of());
            }
            return PlanoDeAcao.VAZIO;
        };
        Montagem m = new Montagem(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), p, fim -> OK);
        m.ticks(1, 16);
        assertEquals(List.of(), vistas.get(0));
        assertEquals(List.of(AC1, AC2), vistas.get(1));
        assertEquals(List.of(AC2), vistas.get(2));
        m.laco.encerrar();
        List<JsonNode> acoes = doTipo(ler(m.dec), "acao");
        assertEquals(List.of("APLICAR:AC-1", "APLICAR:AC-2", "REVERTER:AC-1", "REVERTER:AC-2"),
                acoes.stream().map(n -> n.get("operacao").asText() + ":" + n.get("acao").get("tipo").asText()).toList());
        assertEquals(Map.of("origem", "POLITICA"), Map.of("origem", acoes.get(2).get("origem").asText()));
    }
}
