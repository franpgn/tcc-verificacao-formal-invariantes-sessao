package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.CanalCanario;
import br.unipampa.tcc.controle.FonteDeSaude;
import br.unipampa.tcc.controle.LeituraSaude;
import br.unipampa.tcc.controle.SondaDefasagem;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3): a fonte que alimenta o controlador. Com o bloco {@code "defasagem"} no {@code controlador-*.json}
 * (so em S3), {@link LigacaoControlador} abre a {@link SondaDefasagem} sobre o canal canario, com prazo de leitura
 * = {@code --hotrod-timeout-ms} (o "nao viu", igual a {@code politica.tetoLagMs} pela R7) e periodo = W; o laco
 * le o S_lag dela; o {@code noSuspeito} usa o rotulo canonico ({@link RotulosDosNosTest#rotuloDoNo}) e por isso o
 * P1 escolhe o AC-5 para o no certo. Ponta a ponta em shadow, com canal falso (no 3 cortado) e saude falsa (vista
 * completa, como antes da deteccao): nenhuma chamada ao <i>cluster</i>.
 */
class LigacaoControladorDefasagemTest {

    static final long MS = 1_000_000L;
    static final String ADMIN = ControladorCliTest.SERVIDORES;

    /** Bloco de teste: limiares e passo com origem "teste" (os reais vem da calibracao e de decisao declarada). */
    static final String BLOCO_DEFASAGEM = "\"defasagem\":{\"lagInMs\":50,\"lagOutMs\":20,\"passoLeituraMs\":10,"
            + "\"origem\":{\"lagInMs\":\"teste\",\"lagOutMs\":\"teste\",\"passoLeituraMs\":\"teste\"}}";

    @TempDir
    Path dir;

    /** W = 500 ms; politica com tetoLagMs = 100 (= --hotrod-timeout-ms, R7); bloco defasagem opcional. */
    static String parametros(boolean politica, boolean defasagem, long tetoLagMs) {
        String base = LigacaoControladorTest.PARAMS_RAPIDOS.replace("\"wNs\":100000000", "\"wNs\":500000000");
        StringBuilder sb = new StringBuilder(base.substring(0, base.length() - 1));
        if (politica) {
            sb.append(',').append(LigacaoControladorAtuadorTest.BLOCO.replace("\"tetoLagMs\":2000",
                    "\"tetoLagMs\":" + tetoLagMs));
        }
        if (defasagem) {
            sb.append(',').append(BLOCO_DEFASAGEM);
        }
        return sb.append('}').toString();
    }

    Cli cli(String conteudo, String cenario, String hotrodMs, String servidores) throws IOException {
        Path p = Files.writeString(dir.resolve("controlador-w4.json"), conteudo, StandardCharsets.UTF_8);
        List<String> a = new ArrayList<>(List.of("--controller", "shadow", "--controller-config", p.toString(),
                "--controller-admin", ADMIN, "--servers", servidores, "--csv-dir", dir.toString(),
                "--replication", "async", "--hotrod-timeout-ms", hotrodMs));
        if (cenario.equals("S3")) {
            a.addAll(List.of("--scenario", "S3", "--marker", dir.resolve("marcadores.log").toString()));
        }
        Cli c = new Cli();
        new CommandLine(c).parseArgs(a.toArray(String[]::new));
        c.validar();
        return c;
    }

    Cli cliS3(String conteudo) throws IOException {
        return cli(conteudo, "S3", "100", ADMIN);
    }

    /** Canal falso: cada no ve as proprias escritas e as dos nos nao cortados; o no cortado so ve as dele. */
    static final class CanalParticionado implements CanalCanario {
        final List<String> rotulos;
        final Set<Integer> cortados = ConcurrentHashMap.newKeySet();
        final List<Map<String, String>> visao = new ArrayList<>();
        final AtomicInteger fechado = new AtomicInteger();

        CanalParticionado(List<String> rotulos) {
            this.rotulos = rotulos;
            for (int i = 0; i < rotulos.size(); i++) {
                visao.add(new ConcurrentHashMap<>());
            }
        }

        @Override
        public int nos() {
            return rotulos.size();
        }

        @Override
        public String rotulo(int indice) {
            return rotulos.get(indice);
        }

        @Override
        public void escrever(int indice, String chave, String valor) {
            visao.get(indice).put(chave, valor);
            if (!cortados.contains(indice)) {
                for (int j = 0; j < rotulos.size(); j++) {
                    if (!cortados.contains(j)) {
                        visao.get(j).put(chave, valor);
                    }
                }
            }
        }

        @Override
        public String ler(int indice, String chave) {
            return visao.get(indice).get(chave);
        }

        @Override
        public void close() {
            fechado.incrementAndGet();
        }
    }

    /** Saude falsa: tres nos respondendo com vista de 3 e os caches do perfil saudaveis (antes da deteccao). */
    static FonteDeSaude saudeCompleta(List<String> rotulos) {
        return new FonteDeSaude() {
            @Override
            public int nos() {
                return rotulos.size();
            }

            @Override
            public String rotulo(int i) {
                return rotulos.get(i);
            }

            @Override
            public LeituraSaude consultar(int i, long prazoNs) {
                return new LeituraSaude(3, Map.of("sessions-async", "HEALTHY", "counters-async", "HEALTHY"));
            }
        };
    }

    @Test
    @DisplayName("carregar: bloco defasagem lido; dry-run com prazo de leitura = --hotrod-timeout-ms e periodo = W")
    void carregarEDryRun() throws IOException {
        Cli c = cliS3(parametros(true, true, 100));
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        assertEquals(100, cfg.prazoHotRodMs());
        assertEquals(10, cfg.parametros().defasagem().passoLeituraMs());
        String t = LigacaoControlador.textoDryRun(cfg);
        assertTrue(t.contains("dry-run, defasagem lagInMs=50.0 lagOutMs=20.0 passoLeituraMs=10 prazo_leitura_ms=100"
                + " periodo_ns=500000000"), t);
        for (String linha : t.split("\\R")) {
            assertTrue(linha.startsWith("dry-run, "), linha);
        }
        assertFalse(LigacaoControlador.textoDryRun(LigacaoControlador.carregar(cliS3(parametros(true, false, 100))))
                .contains("defasagem"), "sem bloco, nenhuma linha");
    }

    @Test
    @DisplayName("carregar recusa: fora do S3, prazo que nao cabe em W, lagIn acima do prazo, --servers fora de ordem")
    void carregarRecusa() throws IOException {
        Cli s1 = cli(parametros(true, true, 100), "S1", "100", ADMIN);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.carregar(s1));
        assertTrue(e.getMessage().contains("S3"), e.getMessage());
        Cli naoCabe = cli(parametros(true, true, 600), "S3", "600", ADMIN);
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.carregar(naoCabe), "600 ms >= W de 500 ms");
        Cli lagAcima = cli(parametros(true, true, 40), "S3", "40", ADMIN);
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.carregar(lagAcima), "lagIn 50 > 40");
        String trocados = "127.0.0.1:11223,127.0.0.1:11222,127.0.0.1:11224";
        Cli foraDeOrdem = cli(parametros(true, true, 100), "S3", "100", trocados);
        IllegalArgumentException o = assertThrows(IllegalArgumentException.class,
                () -> LigacaoControlador.carregar(foraDeOrdem));
        assertTrue(o.getMessage().contains("host:porta"), o.getMessage());
        Cli r7 = cli(parametros(true, true, 2000), "S3", "100", ADMIN);
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.carregar(r7), "R7 do W-2a continua");
        // sem politica o S_lag so observa: o prazo continua sendo o --hotrod-timeout-ms
        assertEquals(100, LigacaoControlador.carregar(cliS3(parametros(false, true, 100))).prazoHotRodMs());
    }

    @Test
    @DisplayName("pecas: o bloco defasagem exige o canal, e um canal sem o bloco e recusado")
    void pecasCoerentes() throws IOException {
        LigacaoControlador.Configuracao com = LigacaoControlador.carregar(cliS3(parametros(true, true, 100)));
        Cli c = cliS3(parametros(true, true, 100));
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.pecas(com, c, null,
                new LigacaoControladorTest.GanchoNulo(), 10 * MS, 50 * MS, 40 * MS, null));
        LigacaoControlador.Configuracao sem = LigacaoControlador.carregar(cliS3(parametros(true, false, 100)));
        CanalParticionado canal = new CanalParticionado(CanalCanarioHotRod.rotulos(sem.urlsAdmin()));
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.pecas(sem, c, null,
                new LigacaoControladorTest.GanchoNulo(), 10 * MS, 50 * MS, 40 * MS, canal));
        LigacaoControlador.Pecas p = LigacaoControlador.pecas(sem, c, null, new LigacaoControladorTest.GanchoNulo(),
                10 * MS, 50 * MS, 40 * MS);
        assertEquals(null, p.canario(), "sem bloco, nenhum canal Hot Rod e aberto");
    }

    @Test
    @DisplayName("ponta a ponta (shadow): no 3 cortado com vista completa -> S_lag alto com noSuspeito canonico -> P1 AC-7 (so o cache de sessoes) previsto para ele")
    void pontaAPontaNoCortado() throws Exception {
        Cli c = cliS3(parametros(true, true, 100));
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        List<String> rotulos = CanalCanarioHotRod.rotulos(cfg.urlsAdmin());
        String suspeito = RotulosDosNosTest.rotuloDoNo(cfg.urlsAdmin().get(2));
        assertEquals(suspeito, rotulos.get(2));
        CanalParticionado canal = new CanalParticionado(rotulos);
        canal.cortados.add(2);
        LigacaoControlador.Pecas pecas = LigacaoControlador.pecas(cfg, c, saudeCompleta(rotulos),
                new LigacaoControladorTest.GanchoNulo(), 10 * MS, 50 * MS, 40 * MS, canal);
        EpochClock relogio = EpochClock.capturarAgora();
        LigacaoControlador lig = LigacaoControlador.abrir(cfg, c, 1, relogio, relogio.paraEpochNanos(System.nanoTime()),
                null, pecas);
        Thread.sleep(2_300); // ~4 janelas de 500 ms
        lig.close();
        assertEquals(1, canal.fechado.get(), "a ligacao fecha o canal");

        List<JsonNode> obs = LigacaoControladorTest.ler(dir.resolve("observaveis-rep-001.jsonl"));
        JsonNode meta = obs.get(0);
        assertEquals("observaveis-controlador/1.2", meta.get("esquema").asText());
        assertTrue(meta.get("sonda_defasagem").asBoolean());
        assertEquals(50.0, meta.get("parametros_defasagem").get("lag_in_ms").asDouble());
        assertEquals(500 * MS, meta.get("parametros_defasagem").get("periodo_ns").asLong());
        List<JsonNode> rodadas = obs.stream().filter(n -> "defasagem".equals(n.get("tipo").asText())).toList();
        assertTrue(rodadas.size() >= 3, "rodadas: " + rodadas.size());
        for (JsonNode r : rodadas) {
            assertEquals(100, r.get("teto_ms").asLong(), "nao visto = --hotrod-timeout-ms");
            assertEquals(100.0, r.get("lag_max_ms").asDouble());
            assertEquals(suspeito, r.get("no_suspeito").asText());
            assertEquals(6, r.get("pares").size());
            assertEquals(3, r.get("custo").get("escritas").asInt());
        }
        List<JsonNode> altas = obs.stream().filter(n -> "janela".equals(n.get("tipo").asText()))
                .filter(n -> "ALTA".equals(n.get("lag").get("nivel").asText())).toList();
        assertTrue(altas.size() >= 2, "janelas com S_lag alto: " + altas.size());
        for (JsonNode j : altas) {
            assertEquals(suspeito, j.get("lag").get("no_suspeito").asText());
            assertEquals(1, j.get("lag").get("amostras").asInt(), "uma rodada por janela");
        }
        JsonNode fim = obs.get(obs.size() - 1);
        assertEquals("fim", fim.get("tipo").asText());
        for (String nome : SondaDefasagem.nomesDosContadores()) {
            assertTrue(fim.has(nome) && !fim.get(nome).isNull(), nome);
        }
        assertEquals(3 * fim.get("defasagem_rodadas").asLong(), fim.get("defasagem_escritas").asLong());

        List<JsonNode> dec = LigacaoControladorTest.ler(dir.resolve("decisoes-rep-001.jsonl"));
        JsonNode p1 = dec.stream().filter(n -> "politica".equals(n.get("tipo").asText()))
                .filter(n -> "P1".equals(n.get("politica").asText())).findFirst().orElseThrow();
        assertEquals("isolar-suspeito", p1.get("motivo").asText());
        assertEquals(suspeito, p1.get("no").asText(), "V-7: no do P1 = noSuspeito canonico");
        // P1-IC (G-1): a acao do P1 e o AC-7 (ignorar o cache de sessoes no suspeito), nao mais a parada
        JsonNode ac7 = dec.stream().filter(n -> "acao".equals(n.get("tipo").asText()))
                .filter(n -> "AC-7".equals(n.get("acao").get("tipo").asText())).findFirst().orElseThrow();
        assertEquals(suspeito, ac7.get("acao").get("alvo").asText(), "o AC-7 acha o alvo pelo rotulo canonico");
        assertEquals("sessions-async", ac7.get("acao").get("caches").get(0).asText());
        assertEquals(1, ac7.get("acao").get("caches").size(), "R1: um cache so");
        assertFalse(ac7.get("executada").asBoolean());
        assertEquals("PREVISTA", ac7.get("resultado").asText());
        assertTrue(dec.stream().noneMatch(n -> "acao".equals(n.get("tipo").asText())
                && "AC-5".equals(n.get("acao").get("tipo").asText())), "a parada nao e pedida");
    }

    @Test
    @DisplayName("sem o bloco: nenhuma sonda, S_lag indeterminado, parametros_defasagem e contadores null, nenhuma linha defasagem")
    void semBloco() throws Exception {
        Cli c = cliS3(parametros(true, false, 100));
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        List<String> rotulos = CanalCanarioHotRod.rotulos(cfg.urlsAdmin());
        LigacaoControlador.Pecas pecas = LigacaoControlador.pecas(cfg, c, saudeCompleta(rotulos),
                new LigacaoControladorTest.GanchoNulo(), 10 * MS, 50 * MS, 40 * MS);
        EpochClock relogio = EpochClock.capturarAgora();
        LigacaoControlador lig = LigacaoControlador.abrir(cfg, c, 1, relogio, relogio.paraEpochNanos(System.nanoTime()),
                null, pecas);
        Thread.sleep(1_200);
        lig.close();
        List<JsonNode> obs = LigacaoControladorTest.ler(dir.resolve("observaveis-rep-001.jsonl"));
        assertFalse(obs.get(0).get("sonda_defasagem").asBoolean());
        assertTrue(obs.get(0).get("parametros_defasagem").isNull());
        assertTrue(obs.stream().noneMatch(n -> "defasagem".equals(n.get("tipo").asText())));
        assertTrue(obs.stream().filter(n -> "janela".equals(n.get("tipo").asText()))
                .allMatch(n -> "INDETERMINADA".equals(n.get("lag").get("nivel").asText())));
        JsonNode fim = obs.get(obs.size() - 1);
        Map<String, Boolean> nulos = new HashMap<>();
        for (String nome : SondaDefasagem.nomesDosContadores()) {
            nulos.put(nome, fim.has(nome) && fim.get(nome).isNull());
        }
        assertTrue(nulos.values().stream().allMatch(Boolean::booleanValue), nulos.toString());
    }
}
