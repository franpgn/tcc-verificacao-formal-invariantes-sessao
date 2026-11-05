package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (P-6 da V-W5) na fonte de {@code WorkloadMain} e de {@code LigacaoControlador}, no estilo de
 * {@link ConvergenciaResumoFimCargaTest#workloadMainPassaFimDaCarga}:
 *
 * <ol>
 *   <li>a linha de sinal sai depois de {@code executarCargaComControle} (fim da carga lido antes do encerramento do
 *       controlador, V-W3 R1) e antes do laco de convergencia, com {@code System.out.flush()} logo depois (o
 *       orquestrador le o stdout por um pipe);</li>
 *   <li>o alvo do AC-5 vem do controlador em memoria ({@code ac5AplicadaAlvo()}), sem ler a trilha do disco;</li>
 *   <li>a espera do religamento roda dentro de {@code executarConvergencia}, antes de {@code laco.executar};</li>
 *   <li>{@code LigacaoControlador.ac5AplicadaAlvo()} devolve o que o executor guardou.</li>
 * </ol>
 */
class WorkloadMainReligamentoFonteTest {

    static String fonte(String arquivo) throws Exception {
        String f = Files.readString(NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/" + arquivo), StandardCharsets.UTF_8).replace("\r\n", "\n");
        return DetectorFonte.semComentarios(f).replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("sinal depois da carga com controle e antes da convergencia, com flush")
    void ordemDoSinal() throws Exception {
        String s = fonte("WorkloadMain.java");
        int carga = s.indexOf("executarCargaComControle(() -> {");
        int alvo = s.indexOf("controle.ac5AplicadaAlvo()");
        int sinal = s.indexOf("System.out.println(Religamento.linhaSinal(rep, fimCargaNs, ac5Alvo));");
        int flush = s.indexOf("System.out.flush();", sinal);
        int conv = s.indexOf("marcadores = executarConvergencia(cli,");
        assertTrue(carga > 0, "executarCargaComControle(() -> {");
        assertTrue(alvo > carga, "alvo do AC-5 lido do controlador depois da carga");
        assertTrue(sinal > alvo, "linha de sinal depois do alvo");
        assertTrue(flush > sinal && flush < conv, "flush logo depois do sinal, antes da convergencia");
        assertTrue(conv > sinal, "convergencia depois do sinal");
    }

    @Test
    @DisplayName("espera do religamento dentro de executarConvergencia, antes do laco")
    void esperaAntesDoLaco() throws Exception {
        String s = fonte("WorkloadMain.java");
        int metodo = s.indexOf("private static Marcadores executarConvergencia(");
        int espera = s.indexOf("Religamento.esperar(", metodo);
        int laco = s.indexOf("laco.executar(tFimNs)", metodo);
        assertTrue(metodo > 0 && espera > metodo && laco > espera, "Religamento.esperar antes de laco.executar");
        assertTrue(s.indexOf("cli.religamentoPrazoSeg * 1_000_000_000L", metodo) > metodo, "prazo vem da CLI");
    }

    @Test
    @DisplayName("LigacaoControlador.ac5AplicadaAlvo delega ao executor")
    void delegaAoExecutor() throws Exception {
        String s = fonte("LigacaoControlador.java");
        assertTrue(s.contains("public String ac5AplicadaAlvo() { return executor.alvoDoAc5Aplicado(); }"), s.length() + "");
    }
}
