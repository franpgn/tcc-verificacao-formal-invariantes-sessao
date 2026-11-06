package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-48 na fonte de {@code WorkloadMain} (no estilo de {@link WorkloadMainReligamentoFonteTest}): a topologia do
 * {@code rcm} principal, que escolhe os segmentos lidos de cada replica ({@code segmentosDoServidor}), e atualizada
 * antes de cada leitura por replica e da varredura.
 *
 * <ol>
 *   <li>cada rodada do laco de convergencia atualiza a topologia antes da sonda e das leituras: a sonda do laco chama
 *       {@code atualizacao.atualizar()} e so depois a sonda de antes; uma falha da atualizacao sai pela sonda e a
 *       rodada fica invalida ({@code ConvergenciaLoop.rodada});</li>
 *   <li>a espera do religamento continua com a sonda de antes (REST decide; {@code t_religamento_ns}, origem do
 *       Delta_conv do braco B, nao muda de significado);</li>
 *   <li>a rodada unica (sem {@code --convergence}) e a varredura ({@code --sweep}) atualizam antes de ler; ali a
 *       falha vira aviso e a leitura segue.</li>
 * </ol>
 */
class WorkloadMainP48FonteTest {

    @Test
    @DisplayName("laco de convergencia: atualiza a topologia em cada rodada, antes da sonda de antes")
    void lacoAtualizaEmCadaRodada() throws Exception {
        String s = WorkloadMainReligamentoFonteTest.fonte("WorkloadMain.java");
        int metodo = s.indexOf("private static Marcadores executarConvergencia(");
        int fim = s.indexOf("private static void executarVarredura(", metodo);
        String m = s.substring(metodo, fim);
        assertTrue(m.contains("AtualizacaoTopologia atualizacao = AtualizacaoTopologia.doPrincipal(rcm, "
                + "List.of(cli.replicacao.cacheSessoes(), cli.replicacao.cacheContadores()), System.out::println);"), m);
        assertTrue(m.contains("ConvergenciaLoop.SondaCluster sondaLaco = () -> { atualizacao.atualizar(); return sonda.ler(); };"), m);
        assertTrue(m.contains("new ConvergenciaLoop(auditor, leitores.leitoresSessao(), leitores.leitoresIdentidade(), sondaLaco, esperados,"),
                "o laco usa a sonda que atualiza");
        assertTrue(m.contains("Religamento.esperar(sonda, esperados,"), "o religamento usa a sonda de antes");
        assertTrue(m.indexOf("sondaLaco") > m.indexOf("ConvergenciaLoop.SondaCluster sonda = () -> {"),
                "a sonda do laco embrulha a sonda de antes");
    }

    @Test
    @DisplayName("rodada unica e varredura: atualizam a topologia antes de ler")
    void rodadaUnicaEVarredura() throws Exception {
        String s = WorkloadMainReligamentoFonteTest.fonte("WorkloadMain.java");
        int unica = s.indexOf("auditor.rodadaUnica(leitores.leitoresSessao(), leitores.leitoresIdentidade());");
        int antesUnica = s.lastIndexOf("atualizarTopologiaOuAvisar(rcm, cli, \"rodada unica\");", unica);
        assertTrue(unica > 0 && antesUnica > 0 && unica - antesUnica < 200, "atualiza logo antes da rodada unica");
        int varredura = s.indexOf("private static void executarVarredura(");
        int atualiza = s.indexOf("atualizarTopologiaOuAvisar(rcm, cli, \"varredura\");", varredura);
        int le = s.indexOf("materializarTudo(rcm.getCache(cli.replicacao.cacheSessoes()))", varredura);
        assertTrue(varredura > 0 && atualiza > varredura && le > atualiza, "atualiza antes de materializar na varredura");
        int helper = s.indexOf("private static void atualizarTopologiaOuAvisar(RemoteCacheManager rcm, Cli cli, String onde)");
        assertTrue(helper > 0, "helper presente");
        String h = s.substring(helper, s.indexOf("}", s.indexOf("catch (IllegalStateException e)", helper)) + 1);
        assertTrue(h.contains(".atualizar();") && h.contains("catch (IllegalStateException e)")
                && h.contains("System.out.println(\"AVISO: \""), "falha vira aviso: " + h);
    }
}
