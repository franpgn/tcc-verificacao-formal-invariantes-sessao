package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-10 na fonte de {@code WorkloadMain}, no estilo de {@link WorkloadMainReligamentoFonteTest}: o replay tardio roda
 * em {@code executarS3} depois dos atores pararem e antes de gravar as acoes e fechar os clientes S3 (ainda dentro do
 * try-with-resources); a coluna {@code origem} so entra no CSV de violacoes com a sonda ligada.
 */
class WorkloadMainSondaFonteTest {

    @Test
    @DisplayName("replay tardio depois de rodarEmThreads, antes do CSV de acoes e do fim do try dos clientes")
    void ordemDoTardio() throws Exception {
        String s = WorkloadMainReligamentoFonteTest.fonte("WorkloadMain.java");
        int abrir = s.indexOf("try (S3Clientes clientes = S3Clientes.abrir(");
        int rodar = s.indexOf("sim.rodarEmThreads(fimNs, parar);");
        int tardio = s.indexOf("sonda.replayTardio(lerMarcadores(cli), relogio::paraEpochNanos);");
        int resumo = s.indexOf("System.out.println(sonda.linhaResumo());");
        int acoes = s.indexOf("registro.escreverCsv(Path.of(cli.csvDir, String.format(\"s3-acoes-rep-%03d.csv\", rep)), relogio);");
        int retorno = s.indexOf("return clientes::invalidacoesSnapshot;");
        assertTrue(abrir > 0 && rodar > abrir, "atores rodam com os clientes abertos");
        assertTrue(tardio > rodar, "tardio depois dos atores pararem");
        assertTrue(resumo > tardio && acoes > resumo, "linha de resumo e depois o CSV de acoes");
        assertTrue(retorno > acoes, "ainda dentro do try dos clientes");
        assertTrue(s.indexOf("if (sonda != null) {", rodar) < tardio, "so com a sonda ligada");
    }

    @Test
    @DisplayName("coluna origem so com --scenario S3 e --s3-replay-probe")
    void colunaOrigemGuardada() throws Exception {
        String s = WorkloadMainReligamentoFonteTest.fonte("WorkloadMain.java");
        int guarda = s.indexOf("if (cli.scenarioS3 && cli.s3.sondaReplay) {");
        int coluna = s.indexOf("extras.add(InvariantAuditor.colunaOrigem());");
        int dump = s.indexOf("auditor.dumpCsv(violacoesDestino, marcadores, extras);");
        assertTrue(guarda > 0 && coluna > guarda && dump > coluna, "guarda, coluna e dump nessa ordem");
        assertTrue(s.indexOf("colunaOrigem()") == coluna + "extras.add(InvariantAuditor.".length(),
                "uma unica chamada de colunaOrigem()");
    }
}
