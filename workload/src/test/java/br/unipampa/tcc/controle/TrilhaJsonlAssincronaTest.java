package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2b (P-5, achado N-8 do documento de 05/11): a trilha JSONL grava numa thread propria, com fila em memoria.
 * Quem chama ({@link LacoDeControle}, {@link ExecutorDeAcoes}, sondas) nunca espera o disco; a fila tem
 * capacidade calculada na criacao ({@code 4 x linhas esperadas}); a linha {@code fim} nunca se perde por fila
 * cheia e leva {@code linhas_perdidas} contadas na hora em que e gravada.
 */
class TrilhaJsonlAssincronaTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    /**
     * Saida falsa: guarda o texto; o {@code flush} da linha {@code travarNa} (1 = primeira) espera
     * {@link #liberar()} ou {@code travaMs}; o da linha {@code falharNa} lanca {@link IOException}.
     */
    static final class SaidaLenta extends Writer {
        private final StringBuilder gravado = new StringBuilder();
        private final StringBuilder emCurso = new StringBuilder();
        final CountDownLatch entrouNaTrava = new CountDownLatch(1);
        private final CountDownLatch libera = new CountDownLatch(1);
        private final int travarNa;
        private final long travaMs;
        private final int falharNa;
        private int flushes;
        volatile long liberadaEmNs = Long.MAX_VALUE;
        volatile boolean fechada;

        SaidaLenta(int travarNa, long travaMs, int falharNa) {
            this.travarNa = travarNa;
            this.travaMs = travaMs;
            this.falharNa = falharNa;
        }

        static SaidaLenta travandoNaPrimeira(long ms) {
            return new SaidaLenta(1, ms, -1);
        }

        void liberar() {
            libera.countDown();
        }

        synchronized String texto() {
            return gravado.toString();
        }

        List<String> linhas() {
            List<String> l = new ArrayList<>();
            for (String s : texto().split("\n")) {
                if (!s.isEmpty()) {
                    l.add(s);
                }
            }
            return l;
        }

        @Override
        public void write(char[] c, int off, int len) {
            synchronized (this) {
                emCurso.append(c, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            int n;
            synchronized (this) {
                n = ++flushes;
            }
            if (n == travarNa) {
                entrouNaTrava.countDown();
                try {
                    libera.await(travaMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                liberadaEmNs = System.nanoTime();
            }
            synchronized (this) {
                if (n == falharNa) {
                    emCurso.setLength(0);
                    throw new IOException("disco falhou na linha " + n);
                }
                gravado.append(emCurso);
                emCurso.setLength(0);
            }
        }

        @Override
        public void close() {
            fechada = true;
        }
    }

    static ObjectNode linha(String tipo, int n) {
        return JSON.createObjectNode().put("tipo", tipo).put("n", n);
    }

    static JsonNode ler(String s) {
        try {
            return JSON.readTree(s);
        } catch (IOException e) {
            throw new AssertionError(s, e);
        }
    }

    // ------------------------------------------------------------------ escrever

    @Test
    @DisplayName("escrever nao espera o disco: com a gravadora travada, cada chamada volta na hora e a ordem se mantem")
    void escreverNaoEsperaODisco() throws Exception {
        SaidaLenta s = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("x.jsonl"), s, 16);
        t.escrever(linha("a", 1));
        assertTrue(s.entrouNaTrava.await(2, TimeUnit.SECONDS), "a gravadora nao pegou a primeira linha");
        long t0 = System.nanoTime();
        for (int i = 2; i <= 5; i++) {
            t.escrever(linha("a", i));
        }
        long decorrido = System.nanoTime() - t0;
        assertTrue(decorrido < 200 * MS, "4 chamadas levaram " + decorrido / MS + " ms com o disco travado");
        assertEquals(0, t.linhas(), "nada gravado enquanto o disco esta travado");
        s.liberar();
        t.close();
        List<String> l = s.linhas();
        assertEquals(5, l.size(), s.texto());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, ler(l.get(i)).get("n").asInt(), "ordem da fila");
        }
        assertEquals(5, t.linhas());
        assertEquals(0, t.linhasPerdidas());
        assertTrue(s.fechada, "close fecha o arquivo");
    }

    @Test
    @DisplayName("fila cheia: a linha e contada como transbordada, a chamada nao bloqueia, e o fim entra fora da capacidade com o total")
    void filaCheiaContaENaoBloqueia() throws Exception {
        SaidaLenta s = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("x.jsonl"), s, 2);
        assertEquals(2, t.capacidade());
        t.escrever(linha("a", 1));
        assertTrue(s.entrouNaTrava.await(2, TimeUnit.SECONDS));
        // a linha 1 esta na gravadora (travada); cabem 2 na fila; 4 e 5 transbordam
        long t0 = System.nanoTime();
        for (int i = 2; i <= 5; i++) {
            t.escrever(linha("a", i));
        }
        assertTrue(System.nanoTime() - t0 < 200 * MS, "fila cheia bloqueou quem chama");
        assertEquals(2, t.transbordadas());
        ObjectNode fim = JSON.createObjectNode().put("tipo", "fim");
        assertTrue(t.escreverFim(fim, "linhas_perdidas"), "fim aceito com a fila cheia");
        s.liberar();
        t.close();
        List<String> l = s.linhas();
        assertEquals(List.of(1, 2, 3), l.subList(0, 3).stream().map(x -> ler(x).get("n").asInt()).toList());
        JsonNode f = ler(l.get(l.size() - 1));
        assertEquals("fim", f.get("tipo").asText());
        assertEquals(2, f.get("linhas_perdidas").asLong(), "o fim leva as transbordadas");
        assertEquals(4, l.size());
        assertEquals(2, t.linhasPerdidas());
    }

    @Test
    @DisplayName("fim: linhas_perdidas e contado na hora da gravacao (erro de escrita de uma linha ainda na fila entra), e e o ultimo campo")
    void fimContaErrosDaFila() throws Exception {
        // trava na primeira linha e falha a segunda: no pedido do fim ainda nao houve erro nenhum
        SaidaLenta s = new SaidaLenta(1, 5_000, 2);
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("x.jsonl"), s, 16);
        t.escrever(linha("a", 1));
        assertTrue(s.entrouNaTrava.await(2, TimeUnit.SECONDS));
        t.escrever(linha("a", 2));
        t.escrever(linha("a", 3));
        ObjectNode fim = JSON.createObjectNode().put("tipo", "fim").put("x", 7);
        assertTrue(t.escreverFim(fim, "linhas_perdidas"));
        assertEquals(0, t.errosEscrita(), "nenhum erro ainda no pedido do fim");
        s.liberar();
        t.close();
        List<String> l = s.linhas();
        assertEquals(3, l.size(), s.texto());
        assertEquals(List.of(1, 3), l.subList(0, 2).stream().map(x -> ler(x).get("n").asInt()).toList());
        JsonNode f = ler(l.get(2));
        assertEquals(1, f.get("linhas_perdidas").asLong());
        List<String> campos = new ArrayList<>();
        f.fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("tipo", "x", "linhas_perdidas"), campos);
        assertEquals(1, t.errosEscrita());
        assertEquals(1, t.linhasPerdidas());
    }

    @Test
    @DisplayName("depois do fim: nenhuma linha passa do fim; as que chegam sao descartadas e contadas fora do fim")
    void depoisDoFimDescarta() throws Exception {
        SaidaLenta s = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("x.jsonl"), s, 16);
        t.escrever(linha("a", 1));
        assertTrue(s.entrouNaTrava.await(2, TimeUnit.SECONDS));
        assertTrue(t.escreverFim(JSON.createObjectNode().put("tipo", "fim"), "linhas_perdidas"));
        t.escrever(linha("tarde", 2)); // executor fora do prazo, por exemplo
        assertFalse(t.escreverFim(JSON.createObjectNode().put("tipo", "fim"), "linhas_perdidas"),
                "um segundo fim e recusado");
        s.liberar();
        t.close();
        t.escrever(linha("tarde", 3));
        List<String> l = s.linhas();
        assertEquals(2, l.size(), s.texto());
        assertEquals("fim", ler(l.get(1)).get("tipo").asText());
        assertEquals(0, ler(l.get(1)).get("linhas_perdidas").asLong(), "o que chega depois do fim nao entra nele");
        assertEquals(3, t.descartadas());
        assertEquals(3, t.linhasPerdidas(), "aparecem em linhas_jsonl_perdidas do stdout");
    }

    @Test
    @DisplayName("close sem fim: espera a gravadora terminar a fila e fecha o arquivo; idempotente")
    void closeEsperaAFila() throws Exception {
        Path p = dir.resolve("sub/y.jsonl");
        TrilhaJsonl t = TrilhaJsonl.abrir(p, 64);
        for (int i = 1; i <= 50; i++) {
            t.escrever(linha("a", i));
        }
        t.close();
        List<String> l = Files.readAllLines(p, StandardCharsets.UTF_8);
        assertEquals(50, l.size());
        assertEquals(50, ler(l.get(49)).get("n").asInt());
        t.close();
        assertEquals(50, t.linhas());
    }

    @Test
    @DisplayName("aguardarGravacao: devolve false no prazo com o disco travado e true depois que a fila esvazia")
    void aguardarGravacao() throws Exception {
        SaidaLenta s = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("x.jsonl"), s, 16);
        t.escrever(linha("a", 1));
        t.escrever(linha("a", 2));
        assertTrue(s.entrouNaTrava.await(2, TimeUnit.SECONDS));
        long t0 = System.nanoTime();
        assertFalse(t.aguardarGravacao(100 * MS));
        assertTrue(System.nanoTime() - t0 < 2 * S, "aguardarGravacao passou do prazo");
        s.liberar();
        assertTrue(t.aguardarGravacao(5 * S));
        assertEquals(2, t.linhas());
        t.close();
    }

    @Test
    @DisplayName("capacidade: 4 x linhas esperadas (plano W-2b, item 1); linhas esperadas <= 0 ou capacidade que nao cabe em int sao recusadas")
    void capacidade() throws Exception {
        assertEquals(4, TrilhaJsonl.FATOR_CAPACIDADE);
        assertEquals(4 * 662, TrilhaJsonl.capacidadePara(662));
        assertEquals(4, TrilhaJsonl.capacidadePara(1));
        assertThrows(IllegalArgumentException.class, () -> TrilhaJsonl.capacidadePara(0));
        assertThrows(IllegalArgumentException.class, () -> TrilhaJsonl.capacidadePara(-3));
        assertThrows(IllegalArgumentException.class, () -> TrilhaJsonl.capacidadePara(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> TrilhaJsonl.abrir(dir.resolve("z.jsonl"), 0));
    }

    @Test
    @DisplayName("a gravadora e uma thread daemon propria, com o nome do arquivo")
    void threadGravadora() throws Exception {
        SaidaLenta s = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("decisoes-rep-001.jsonl"), s, 4);
        t.escrever(linha("a", 1));
        assertTrue(s.entrouNaTrava.await(2, TimeUnit.SECONDS));
        Thread g = null;
        for (Thread x : Thread.getAllStackTraces().keySet()) {
            if (x.getName().equals(TrilhaJsonl.PREFIXO_THREAD + "decisoes-rep-001.jsonl")) {
                g = x;
            }
        }
        assertTrue(g != null && g.isDaemon(), "gravadora " + g);
        assertFalse(g == Thread.currentThread());
        s.liberar();
        t.close();
    }

    @Test
    @DisplayName("a gravadora nunca morre por uma linha: RuntimeException da saida conta como erro e as linhas seguintes saem")
    void gravadoraNaoMorre() throws Exception {
        Writer saida = new Writer() {
            private final StringBuilder sb = new StringBuilder();
            private int flushes;

            @Override
            public void write(char[] c, int off, int len) {
                sb.append(c, off, len);
            }

            @Override
            public void flush() {
                if (++flushes == 1) {
                    sb.setLength(0);
                    throw new IllegalStateException("saida quebrada na primeira linha");
                }
            }

            @Override
            public void close() {
            }

            @Override
            public String toString() {
                return sb.toString();
            }
        };
        TrilhaJsonl t = TrilhaJsonl.sobre(dir.resolve("x.jsonl"), saida, 8);
        t.escrever(linha("a", 1));
        t.escrever(linha("a", 2));
        assertTrue(t.escreverFim(JSON.createObjectNode().put("tipo", "fim"), "linhas_perdidas"));
        assertTrue(t.aguardarGravacao(5 * S), "a gravadora parou");
        t.close();
        List<String> l = new ArrayList<>(List.of(saida.toString().split("\n")));
        assertEquals(2, l.size(), saida.toString());
        assertEquals(2, ler(l.get(0)).get("n").asInt());
        assertEquals(1, ler(l.get(1)).get("linhas_perdidas").asLong());
        assertEquals(1, t.errosEscrita());
    }

    // ------------------------------------------------------------------ quem chama

    /** Atuador que registra o instante (nanoTime) de entrada e de saida de cada chamada. */
    static final class AtuadorCronometrado implements AtuadorCluster {
        final List<long[]> chamadas = new ArrayList<>();

        @Override
        public String nome() {
            return "cronometrado";
        }

        @Override
        public ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) {
            return chamar();
        }

        @Override
        public ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) {
            return chamar();
        }

        private ResultadoAtuacao chamar() {
            long ini = System.nanoTime();
            ResultadoAtuacao r = ResultadoAtuacao.aplicada(204, "ok");
            synchronized (this) {
                chamadas.add(new long[] {ini, System.nanoTime()});
            }
            return r;
        }

        synchronized long[] chamada(int i) {
            return chamadas.get(i);
        }
    }

    @Test
    @DisplayName("plano W-2b: uma trilha que trava 5 s nao atrasa a chamada seguinte do atuador alem do prazo do tipo")
    void trilhaTravadaNaoAtrasaAtuador() throws Exception {
        SaidaLenta s = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl dec = TrilhaJsonl.sobre(dir.resolve("decisoes-rep-001.jsonl"), s, 64);
        AtuadorCronometrado a = new AtuadorCronometrado();
        long tCham = 500 * MS;
        AtomicLong relogio = new AtomicLong(100 * S);
        ExecutorDeAcoes ex = new ExecutorDeAcoes(ModoAtuacao.ACTIVE, a, tCham, relogio::get,
                r -> dec.escrever(FormatoJsonl.acao(r)), new ExecutorDeAcoesTest.GanchoFalso());
        ex.iniciar();
        ex.aplicar(ExecutorDeAcoesTest.AC1, Decisao.Regra.R2, 5 * S);
        ex.aplicar(ExecutorDeAcoesTest.AC2, Decisao.Regra.R2, 5 * S);
        assertTrue(ex.aguardarFilaVazia(10 * S));
        long atraso = a.chamada(1)[0] - a.chamada(0)[1];
        long prazoTipo = ex.prazoNs(ExecutorDeAcoesTest.AC2.tipo());
        assertTrue(atraso < prazoTipo, "a segunda chamada esperou " + atraso / MS + " ms (prazo do tipo "
                + prazoTipo / MS + " ms)");
        assertTrue(a.chamada(1)[0] < s.liberadaEmNs, "a segunda chamada saiu com a trilha ainda travada");
        ex.encerrar();
        s.liberar();
        dec.close();
        List<String> l = s.linhas();
        assertEquals(4, l.size(), "2 aplicacoes e 2 reversoes no encerramento: " + s.texto());
        assertEquals(List.of("APLICAR", "APLICAR", "REVERTER", "REVERTER"),
                l.stream().map(x -> ler(x).get("operacao").asText()).toList());
        assertEquals(0, dec.linhasPerdidas());
    }

    @Test
    @DisplayName("W-2b: o tick do laco grava janela e decisao sem esperar o disco travado")
    void tickNaoEsperaODisco() throws Exception {
        SaidaLenta so = SaidaLenta.travandoNaPrimeira(5_000);
        SaidaLenta sd = SaidaLenta.travandoNaPrimeira(5_000);
        TrilhaJsonl obs = TrilhaJsonl.sobre(dir.resolve("observaveis-rep-001.jsonl"), so, 256);
        TrilhaJsonl dec = TrilhaJsonl.sobre(dir.resolve("decisoes-rep-001.jsonl"), sd, 256);
        AtomicLong relogio = new AtomicLong(0);
        ParametrosControlador p = ParametrosControlador.padrao();
        ExecutorDeAcoes ex = new ExecutorDeAcoes(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), 2 * S, relogio::get,
                r -> dec.escrever(FormatoJsonl.acao(r)), new ExecutorDeAcoesTest.GanchoFalso());
        MetaExecucao meta = new MetaExecucao("teste-0", 1, ModoAtuacao.SHADOW, "S3", "async", 3, p, null, null,
                AgregadorDeJanela.TOLERANCIA_PADRAO_NS, S, 0L, "falso", PoliticaDeAcao.NENHUMA.nome(), true, false);
        LacoDeControle laco = new LacoDeControle(new LacoDeControle.Componentes(meta,
                new AgregadorDeJanela(0L, p, Referencia.vazia()), new Controlador(p),
                fim -> new EstadoTopologia(true, 2, 0, 3), fim -> EstadoDefasagem.indeterminada(), List::of,
                PoliticaDeAcao.NENHUMA, ex, relogio::get, obs, dec));
        assertTrue(so.entrouNaTrava.await(2, TimeUnit.SECONDS), "a linha meta deveria travar a gravadora");
        long t0 = System.nanoTime();
        for (int s = 1; s <= 30; s++) {
            relogio.set(s * S);
            laco.tick();
        }
        long decorrido = System.nanoTime() - t0;
        assertTrue(decorrido < 1_000 * MS, "30 ticks levaram " + decorrido / MS + " ms com o disco travado");
        assertTrue(laco.decisoes() >= 5, "decisoes: " + laco.decisoes());
        so.liberar();
        sd.liberar();
        LacoDeControle.Resumo r = laco.encerrar();
        List<String> lo = so.linhas();
        List<String> ld = sd.linhas();
        assertEquals("meta", ler(lo.get(0)).get("tipo").asText());
        assertEquals("fim", ler(lo.get(lo.size() - 1)).get("tipo").asText());
        assertEquals("fim", ler(ld.get(ld.size() - 1)).get("tipo").asText());
        assertEquals(r.janelas(), ld.stream().filter(x -> "decisao".equals(ler(x).get("tipo").asText())).count());
        assertEquals(0, ler(lo.get(lo.size() - 1)).get("linhas_perdidas").asLong());
        assertEquals(0, ler(ld.get(ld.size() - 1)).get("linhas_perdidas").asLong());
    }
}
