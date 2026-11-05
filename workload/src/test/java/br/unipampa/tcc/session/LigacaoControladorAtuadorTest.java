package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.FonteDeSaude;
import br.unipampa.tcc.controle.LeituraSaude;
import br.unipampa.tcc.controle.PoliticaDeAcao;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), P-38: ligacao do atuador REST e da {@link br.unipampa.tcc.controle.PoliticaPorAssinatura} ao
 * {@code --controller shadow|active} quando o {@code controlador-*.json} traz o bloco {@code politica}. Ponta a
 * ponta com tres servidores REST falsos e uma fonte de saude falsa com os mesmos rotulos: queda do no 3 leva a
 * R2, depois a P2 (AC-2 global aplicado de verdade no servidor falso, no no que respondeu na ultima amostra da
 * sonda) e, na volta do no, a reversao pela politica.
 */
class LigacaoControladorAtuadorTest {

    static final long MS = 1_000_000L;

    /** Bloco de teste (k e tetoLagMs com origem "teste"; os reais vem da calibracao, D-12). */
    static final String BLOCO = "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,"
            + "\"origem\":{\"k\":\"teste\",\"tetoLagMs\":\"teste\","
            + "\"tRebNs\":\"state-transfer.timeout do XML (60000 ms), acima da deteccao medida de 46,8 s\","
            + "\"cotaAc5\":\"desenho: uma vez por execucao\"}}";

    @TempDir
    Path dir;

    final List<ServidorAdminFalso> nos = new ArrayList<>();
    final AtomicBoolean rebalanceamento = new AtomicBoolean(true);

    @AfterEach
    void fechar() {
        nos.forEach(ServidorAdminFalso::close);
    }

    ServidorAdminFalso.Resp normal(ServidorAdminFalso srv, ServidorAdminFalso.Pedido p) {
        String c = p.caminho();
        if (p.metodo().equals("POST") && c.equals("/rest/v2/container?action=disable-rebalancing")) {
            rebalanceamento.set(false);
            return ServidorAdminFalso.Resp.de(204, null);
        }
        if (p.metodo().equals("POST") && c.equals("/rest/v2/container?action=enable-rebalancing")) {
            rebalanceamento.set(true);
            return ServidorAdminFalso.Resp.de(204, null);
        }
        if (p.metodo().equals("GET") && (c.equals("/rest/v2/container") || c.startsWith("/rest/v2/caches/"))) {
            return ServidorAdminFalso.Resp.de(200, "{\"rebalancing_enabled\":" + rebalanceamento.get() + "}");
        }
        return ServidorAdminFalso.Resp.de(404, "sem rota");
    }

    void subir() throws IOException {
        for (int i = 0; i < 3; i++) {
            nos.add(new ServidorAdminFalso(this::normal));
        }
    }

    String admin() {
        return nos.stream().map(ServidorAdminFalso::rotulo).collect(Collectors.joining(","));
    }

    Cli cli(String modo, boolean comPolitica) throws IOException {
        String base = LigacaoControladorTest.PARAMS_RAPIDOS;
        String conteudo = comPolitica ? base.substring(0, base.length() - 1) + "," + BLOCO + "}" : base;
        Path p = Files.writeString(dir.resolve("controlador-w2a.json"), conteudo, StandardCharsets.UTF_8);
        Path ref = Files.writeString(dir.resolve("ref.json"), ControladorCliTest.referencia("S1", "async"));
        Cli c = new Cli();
        new CommandLine(c).parseArgs("--controller", modo, "--controller-config", p.toString(),
                "--controller-reference", ref.toString(), "--controller-admin", admin(), "--servers", admin(),
                "--csv-dir", dir.toString(), "--replication", "async");
        c.validar();
        return c;
    }

    /** Saude falsa com os rotulos da sonda real ({@code host:porta} de {@code --controller-admin}). */
    FonteDeSaude saude(AtomicBoolean no3Caido) {
        return new FonteDeSaude() {
            @Override
            public int nos() {
                return 3;
            }

            @Override
            public String rotulo(int i) {
                return nos.get(i).rotulo();
            }

            @Override
            public LeituraSaude consultar(int i, long prazoNs) throws Exception {
                if (i == 2 && no3Caido.get()) {
                    throw new IOException("conexao recusada");
                }
                return new LeituraSaude(no3Caido.get() ? 2 : 3,
                        Map.of("sessions-async", "HEALTHY", "counters-async", "HEALTHY"));
            }
        };
    }

    @Test
    @DisplayName("sem bloco politica: pecasPadrao continua com atuador nulo e politica nenhuma; dry-run igual")
    void semBloco() throws IOException {
        subir();
        Cli c = cli("active", false);
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        LigacaoControlador.Pecas p = LigacaoControlador.pecasPadrao(cfg, c);
        assertEquals("nulo", p.atuador().nome());
        assertSame(PoliticaDeAcao.NENHUMA, p.politica());
        assertTrue(LigacaoControlador.textoDryRun(cfg).contains("atuador=nulo politica=nenhuma"));
        // V-W2a R1: sem acao possivel, o pior caso do encerramento e um T_cham
        assertTrue(LigacaoControlador.textoDryRun(cfg)
                .contains("prazo_encerramento=em_curso+pilha+T_cham pior_caso_ns=2000000000"));
        assertFalse(LigacaoControlador.textoDryRun(cfg).contains("(A+1)x"));
    }

    @Test
    @DisplayName("com bloco politica: atuador por-tipo sobre o REST, politica por-assinatura; dry-run mostra os parametros")
    void comBloco() throws IOException {
        subir();
        Cli c = cli("shadow", true);
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        LigacaoControlador.Pecas p = LigacaoControlador.pecasPadrao(cfg, c);
        assertEquals("por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)", p.atuador().nome());
        assertEquals("por-assinatura", p.politica().nome());
        String t = LigacaoControlador.textoDryRun(cfg);
        assertTrue(t.contains("atuador=por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan) politica=por-assinatura"), t);
        assertTrue(t.contains("politica k=2 tetoLagMs=2000 tRebNs=60000000000 cotaAc5=1"), t);
        // V-W2a R1: AC-2 na pilha (2 s) + AC-5 em curso (5 s) + T_cham (2 s)
        assertTrue(t.contains("prazo_encerramento=em_curso+pilha+T_cham pior_caso_ns=9000000000"), t);
        for (String linha : t.split("\\R")) {
            assertTrue(linha.startsWith("dry-run, "), linha);
        }
    }

    @Test
    @DisplayName("ponta a ponta (active, servidor falso): queda do no 3 -> R2 -> P2 AC-2 global aplicado; volta -> reversao pela politica")
    void pontaAPonta() throws Exception {
        subir();
        Cli c = cli("active", true);
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        AtomicBoolean caido = new AtomicBoolean(false);
        LigacaoControlador.Pecas pecas = LigacaoControlador.pecas(cfg, c, saude(caido),
                new LigacaoControladorTest.GanchoNulo(), 10 * MS, 50 * MS, 40 * MS);
        EpochClock relogio = EpochClock.capturarAgora();
        LigacaoControlador lig = LigacaoControlador.abrir(cfg, c, 1, relogio, relogio.paraEpochNanos(System.nanoTime()),
                null, pecas);
        Thread.sleep(400);
        caido.set(true);
        long ate = System.nanoTime() + 5_000 * MS;
        while (rebalanceamento.get() && System.nanoTime() < ate) {
            Thread.sleep(10);
        }
        assertFalse(rebalanceamento.get(), "o AC-2 chegou ao servidor falso");
        Thread.sleep(300);
        caido.set(false);
        ate = System.nanoTime() + 5_000 * MS;
        while (!rebalanceamento.get() && System.nanoTime() < ate) {
            Thread.sleep(10);
        }
        assertTrue(rebalanceamento.get(), "a reversao chegou ao servidor falso");
        Thread.sleep(300);
        lig.close();

        // o POST foi ao primeiro no que respondeu na ultima amostra (no 1), nunca ao no 3
        List<String> posts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (ServidorAdminFalso.Pedido p : nos.get(i).pedidos()) {
                if (p.metodo().equals("POST")) {
                    posts.add(i + ":" + p.caminho());
                }
            }
        }
        assertEquals(List.of("0:/rest/v2/container?action=disable-rebalancing",
                "0:/rest/v2/container?action=enable-rebalancing"), posts);

        List<JsonNode> dec = LigacaoControladorTest.ler(dir.resolve("decisoes-rep-001.jsonl"));
        JsonNode meta = dec.get(0);
        assertEquals("decisoes-controlador/1.2", meta.get("esquema").asText());
        assertEquals("por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)", meta.get("atuador").asText());
        assertEquals("por-assinatura", meta.get("politica").asText());
        assertEquals(2, meta.get("parametros_politica").get("k").asInt());
        assertEquals("teste", meta.get("parametros_politica").get("origem").get("tetoLagMs").asText());
        List<String> politicas = dec.stream().filter(n -> "politica".equals(n.get("tipo").asText()))
                .map(n -> n.get("politica").asText() + ":" + n.get("motivo").asText()).toList();
        assertTrue(politicas.contains("P2:adiar-rebalanceamento"), politicas.toString());
        assertTrue(politicas.contains("P0:no-voltou"), politicas.toString());
        List<JsonNode> acoes = dec.stream().filter(n -> "acao".equals(n.get("tipo").asText())).toList();
        assertEquals(2, acoes.size(), acoes.toString());
        JsonNode apl = acoes.get(0);
        assertEquals("APLICAR", apl.get("operacao").asText());
        assertEquals("AC-2", apl.get("acao").get("tipo").asText());
        assertTrue(apl.get("acao").get("alvo").isNull());
        assertEquals(List.of("sessions-async", "counters-async"),
                LigacaoControladorTest.JSON.convertValue(apl.get("acao").get("caches"), List.class));
        assertTrue(apl.get("executada").asBoolean());
        assertEquals("APLICADA", apl.get("resultado").asText());
        assertEquals(204, apl.get("codigo_http").asInt());
        assertTrue(apl.get("reversao").isNull());
        JsonNode rev = acoes.get(1);
        assertEquals("REVERTER", rev.get("operacao").asText());
        assertEquals("POLITICA", rev.get("origem").asText());
        assertEquals("APLICADA", rev.get("resultado").asText());
        assertEquals("fim", dec.get(dec.size() - 1).get("tipo").asText());
    }
}
