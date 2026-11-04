package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.AtuadorFalso;
import br.unipampa.tcc.controle.Perfil;
import br.unipampa.tcc.controle.PlanoDeAcao;
import br.unipampa.tcc.controle.PoliticaDeAcao;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-W3 (R1 e N1): o fim da carga em {@link WorkloadMain#executarCargaComControle}.
 *
 * <p>R1: {@code t_fim_carga_ns} e lido ANTES do {@code close} do controlador (que, em active, desfaz a pilha);
 * o instante depois do {@code close} vai a parte, em {@code t_fim_controle_ns}. Sem isso, o braco active
 * mediria a convergencia a partir de um instante mais tardio que o shadow. N1: uma excecao da carga passa
 * pelo {@code finally}, fecha o controlador (reversao de toda acao, linhas {@code fim}) e e relancada.</p>
 */
class WorkloadMainCargaControleTest {

    static final long MS = 1_000_000L;

    @TempDir
    Path dir;

    /** Controle falso: registra quando o close comeca e termina; o close demora {@code demoraMs}. */
    static final class ControleLento implements WorkloadMain.ControleDaCarga {
        final AtomicLong relogio;
        final long demoraMs;
        long inicioClose = -1;
        long fimClose = -1;
        int closes;
        final List<String> ordem;

        ControleLento(AtomicLong relogio, long demoraMs, List<String> ordem) {
            this.relogio = relogio;
            this.demoraMs = demoraMs;
            this.ordem = ordem;
        }

        @Override
        public void close() {
            ordem.add("close");
            closes++;
            inicioClose = relogio.get();
            relogio.addAndGet(demoraMs * MS); // o close "gasta" demoraMs no relogio falso
            fimClose = relogio.get();
        }

        @Override
        public String linhaResumo() {
            return "Controlador: falso";
        }
    }

    @Test
    @DisplayName("R1: t_fim_carga_ns antes do close do controlador lento; t_fim_controle_ns depois dele")
    void fimDaCargaAntesDoClose() throws Exception {
        AtomicLong relogio = new AtomicLong(1_000 * MS);
        List<String> ordem = new ArrayList<>();
        ControleLento c = new ControleLento(relogio, 8_000, ordem);
        WorkloadMain.CargaEncerrada<String> r = WorkloadMain.executarCargaComControle(() -> {
            relogio.addAndGet(300 * MS); // a carga roda 300 ms
            return "resultado";
        }, relogio::get, c, () -> ordem.add("ouvintes"));
        assertEquals("resultado", r.resultado());
        assertEquals(1_300 * MS, r.fimCargaNs(), "instante em que a carga parou");
        assertTrue(r.fimCargaNs() <= c.inicioClose, "t_fim_carga_ns nao inclui o close");
        assertEquals(c.fimClose, r.fimControleNs().longValue());
        assertEquals(8_000 * MS, r.fimControleNs() - r.fimCargaNs());
        assertEquals(List.of("ouvintes", "close"), ordem, "ouvintes desligados antes do close");
    }

    @Test
    @DisplayName("sem controlador (off): t_fim_controle_ns nulo e nada e chamado")
    void semControlador() throws Exception {
        AtomicLong relogio = new AtomicLong(5 * MS);
        AtomicBoolean ouvintes = new AtomicBoolean();
        WorkloadMain.CargaEncerrada<Integer> r = WorkloadMain.executarCargaComControle(() -> 7, relogio::get, null,
                () -> ouvintes.set(true));
        assertEquals(7, r.resultado());
        assertEquals(5 * MS, r.fimCargaNs());
        assertNull(r.fimControleNs());
        assertFalse(ouvintes.get());
    }

    @Test
    @DisplayName("N1: excecao da carga fecha o controlador (finally) e e relancada sem mudar")
    void excecaoDaCargaFechaERelanca() {
        AtomicLong relogio = new AtomicLong(0);
        List<String> ordem = new ArrayList<>();
        ControleLento c = new ControleLento(relogio, 10, ordem);
        IllegalStateException falha = new IllegalStateException("falha da carga");
        IllegalStateException lancada = assertThrows(IllegalStateException.class,
                () -> WorkloadMain.executarCargaComControle(() -> {
                    throw falha;
                }, relogio::get, c, () -> ordem.add("ouvintes")));
        assertSame(falha, lancada);
        assertEquals(1, c.closes);
        assertEquals(List.of("ouvintes", "close"), ordem);
    }

    @Test
    @DisplayName("N1 ponta a ponta: excecao da carga com acao aplicada em active -> reversao ENCERRAMENTO e linhas fim")
    void excecaoDaCargaReverteAcaoReal() throws Exception {
        Path par = dir.resolve("controlador.json");
        Files.writeString(par, LigacaoControladorTest.PARAMS_RAPIDOS, StandardCharsets.UTF_8);
        Path ref = Files.writeString(dir.resolve("ref.json"), ControladorCliTest.referencia("S1", "sync"));
        Cli cli = new Cli();
        new CommandLine(cli).parseArgs("--controller", "active", "--controller-config", par.toString(),
                "--controller-reference", ref.toString(), "--controller-admin", ControladorCliTest.SERVIDORES,
                "--servers", ControladorCliTest.SERVIDORES, "--csv-dir", dir.toString());
        cli.validar();
        AcaoCluster ac1 = new AcaoCluster(AcaoCluster.Tipo.AC_1, List.of("sessions", "counters"), null, "DEGRADED_MODE");
        AtomicBoolean pediu = new AtomicBoolean();
        PoliticaDeAcao umaVez = (d, t, l, ativas) -> pediu.compareAndSet(false, true)
                ? new PlanoDeAcao(List.of(), List.of(ac1)) : PlanoDeAcao.VAZIO;
        AtuadorFalso atuador = new AtuadorFalso();
        EpochClock relogio = EpochClock.capturarAgora();
        LigacaoControlador lig = LigacaoControlador.abrir(LigacaoControlador.carregar(cli), cli, 1, relogio,
                relogio.paraEpochNanos(System.nanoTime()), null,
                new LigacaoControlador.Pecas(null, atuador, umaVez, new LigacaoControladorTest.GanchoNulo(),
                        10 * MS, 2000 * MS, 1500 * MS));
        assertThrows(IllegalStateException.class, () -> WorkloadMain.executarCargaComControle(() -> {
            assertTrue(atuador.esperarPrimeira(), "a politica pediu AC-1 numa das primeiras janelas");
            throw new IllegalStateException("falha da carga");
        }, () -> relogio.paraEpochNanos(System.nanoTime()), lig, () -> { }));
        assertEquals(List.of("aplicar", "reverter"),
                atuador.chamadas().stream().map(AtuadorFalso.Chamada::operacao).toList());
        List<JsonNode> dec = LigacaoControladorTest.ler(dir.resolve("decisoes-rep-001.jsonl"));
        List<JsonNode> obs = LigacaoControladorTest.ler(dir.resolve("observaveis-rep-001.jsonl"));
        assertEquals("fim", dec.get(dec.size() - 1).get("tipo").asText());
        assertEquals("fim", obs.get(obs.size() - 1).get("tipo").asText());
        JsonNode rev = dec.stream().filter(n -> "acao".equals(n.get("tipo").asText())
                && "REVERTER".equals(n.get("operacao").asText())).findFirst().orElseThrow();
        assertEquals("ENCERRAMENTO", rev.get("origem").asText());
        assertTrue(rev.get("executada").asBoolean());
        assertEquals(Perfil.NORMAL.name(), dec.get(dec.size() - 1).get("perfil_final").asText());
    }

    @Test
    @DisplayName("R1 na fonte: executarRepeticao usa executarCargaComControle e nao le o fim da carga depois dele")
    void fonteDeWorkloadMain() throws Exception {
        String fonte = Files.readString(NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java"));
        String s = DetectorFonte.semComentarios(fonte);
        assertTrue(s.contains("executarCargaComControle("), "a carga passa pelo metodo testado");
        assertFalse(s.contains("long fimCargaNs = relogio.paraEpochNanos"),
                "o fim da carga nao e mais lido depois do close do controlador");
    }
}
