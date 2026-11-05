package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.CanalCanario;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4, P-40: a linha {@code fim} de {@code observaveis} registra a chave de cada no e se ela tinha o proprio no como
 * dono primario, com o motivo da escolha ({@code defasagem_chaves}); sem a sonda, {@code null}. E o que o smoke
 * precisa para conferir o P-40.
 */
class LigacaoControladorChavesTest {

    static final long MS = 1_000_000L;

    @TempDir
    Path dir;

    Cli cli(String conteudo) throws IOException {
        Path p = Files.writeString(dir.resolve("controlador-chaves.json"), conteudo, StandardCharsets.UTF_8);
        Cli c = new Cli();
        new CommandLine(c).parseArgs("--controller", "shadow", "--controller-config", p.toString(),
                "--controller-admin", ControladorCliTest.SERVIDORES, "--servers", ControladorCliTest.SERVIDORES,
                "--csv-dir", dir.toString(), "--replication", "async", "--hotrod-timeout-ms", "100",
                "--scenario", "S3", "--marker", dir.resolve("marcadores.log").toString());
        c.validar();
        return c;
    }

    /** Canal particionado (sem corte) com as chaves escolhidas e o primario conhecido. */
    static final class CanalComChaves implements CanalCanario {
        final LigacaoControladorDefasagemTest.CanalParticionado base;

        CanalComChaves(List<String> rotulos) {
            this.base = new LigacaoControladorDefasagemTest.CanalParticionado(rotulos);
        }

        @Override
        public int nos() {
            return base.nos();
        }

        @Override
        public String rotulo(int indice) {
            return base.rotulo(indice);
        }

        @Override
        public void escrever(int indice, String chave, String valor) {
            base.escrever(indice, chave, valor);
        }

        @Override
        public String ler(int indice, String chave) {
            return base.ler(indice, chave);
        }

        @Override
        public String chave(int indice) {
            return "lag:" + base.rotulo(indice) + "#" + indice;
        }

        @Override
        public Boolean chaveNoPrimario(int indice) {
            return indice != 1;
        }

        @Override
        public String motivoDaChave(int indice) {
            return indice != 1 ? "primario" : "nao-encontrada";
        }

        @Override
        public void close() {
            base.close();
        }
    }

    static List<String> campos(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.fieldNames().forEachRemaining(r::add);
        return r;
    }

    @Test
    @DisplayName("fim de observaveis: defasagem_chaves com no, chave, primario e motivo de cada no")
    void chavesNoFim() throws Exception {
        Cli c = cli(LigacaoControladorDefasagemTest.parametros(true, true, 100));
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        List<String> rotulos = CanalCanarioHotRod.rotulos(cfg.urlsAdmin());
        CanalComChaves canal = new CanalComChaves(rotulos);
        LigacaoControlador.Pecas pecas = LigacaoControlador.pecas(cfg, c,
                LigacaoControladorDefasagemTest.saudeCompleta(rotulos), new LigacaoControladorTest.GanchoNulo(),
                10 * MS, 50 * MS, 40 * MS, canal);
        EpochClock relogio = EpochClock.capturarAgora();
        LigacaoControlador lig = LigacaoControlador.abrir(cfg, c, 1, relogio, relogio.paraEpochNanos(System.nanoTime()),
                null, pecas);
        Thread.sleep(700);
        lig.close();
        List<JsonNode> obs = LigacaoControladorTest.ler(dir.resolve("observaveis-rep-001.jsonl"));
        JsonNode fim = obs.get(obs.size() - 1);
        JsonNode chaves = fim.get("defasagem_chaves");
        assertEquals(3, chaves.size());
        for (int i = 0; i < 3; i++) {
            JsonNode k = chaves.get(i);
            assertEquals(List.of("no", "chave", "primario", "motivo"), campos(k));
            assertEquals(rotulos.get(i), k.get("no").asText());
            assertEquals("lag:" + rotulos.get(i) + "#" + i, k.get("chave").asText());
            assertEquals(i != 1, k.get("primario").asBoolean());
            assertEquals(i != 1 ? "primario" : "nao-encontrada", k.get("motivo").asText());
        }
        assertTrue(obs.stream().filter(n -> "defasagem".equals(n.get("tipo").asText())).count() >= 1);
    }

    @Test
    @DisplayName("sem o bloco defasagem: defasagem_chaves = null no fim")
    void semSonda() throws Exception {
        Cli c = cli(LigacaoControladorDefasagemTest.parametros(true, false, 100));
        LigacaoControlador.Configuracao cfg = LigacaoControlador.carregar(c);
        LigacaoControlador.Pecas pecas = LigacaoControlador.pecas(cfg, c, null, new LigacaoControladorTest.GanchoNulo(),
                10 * MS, 50 * MS, 40 * MS);
        EpochClock relogio = EpochClock.capturarAgora();
        LigacaoControlador lig = LigacaoControlador.abrir(cfg, c, 1, relogio, relogio.paraEpochNanos(System.nanoTime()),
                null, pecas);
        lig.close();
        List<JsonNode> obs = LigacaoControladorTest.ler(dir.resolve("observaveis-rep-001.jsonl"));
        JsonNode fim = obs.get(obs.size() - 1);
        assertTrue(fim.has("defasagem_chaves") && fim.get("defasagem_chaves").isNull());
    }
}
