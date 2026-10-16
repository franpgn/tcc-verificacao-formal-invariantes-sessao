package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E2): o modo {@code --scenario S3} e tratado fora do enum {@link Scenario}; S1/S2 e os
 * defaults existentes ficam como estavam. Parametros do S3 sao todos configuraveis por CLI.
 */
class CliS3Test {

    private static Cli parse(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    @Test
    @DisplayName("--scenario S3 liga o modo S3 e nao entra no enum Scenario")
    void s3FicaForaDoEnum() {
        Cli c = parse("--scenario", "S3");
        assertTrue(c.scenarioS3);
        assertEquals(2, Scenario.values().length, "enum so tem S1 e S2");
        assertTrue(parse("-s", "S3").scenarioS3);
    }

    @Test
    @DisplayName("S1/S2 e o padrao nao ligam o S3 e mantem o campo scenario")
    void s1s2Intocados() {
        assertFalse(parse().scenarioS3);
        assertEquals(Scenario.S1, parse().scenario);
        assertEquals(Scenario.S2, parse("--scenario", "S2").scenario);
        assertFalse(parse("--scenario", "S2").scenarioS3);
    }

    @Test
    @DisplayName("cenario desconhecido e rejeitado como erro de parametro (nao cai no fallback S1)")
    void cenarioDesconhecidoRejeitado() {
        assertThrows(CommandLine.ParameterException.class, () -> parse("--scenario", "S9"));
    }

    @Test
    @DisplayName("padroes do S3 seguem o plano (T-E item 5)")
    void padroesDoPlano() {
        S3Parametros p = parse("--scenario", "S3").s3;
        assertEquals(S3Parametros.ModoCliente.pinned, p.modoCliente);
        assertEquals("isn1", p.noAdmin);
        assertEquals(20, p.usuarios);
        assertEquals(10, p.contasLimite);
        assertEquals(1000.0, p.pensarMs);
        assertEquals(20.0, p.sessaoMedianaSeg);
        assertEquals(0.7, p.probLogout);
        assertEquals(30.0, p.idleSeg);
        assertEquals(10.0, p.voltarJanelaSeg);
        assertEquals(1.0, p.voltarMinSeg);
        assertEquals(3.0, p.voltarMaxSeg);
        assertEquals(15.0, p.adminMediaSeg);
        assertEquals(20.0, p.adminHoldMinSeg);
        assertEquals(1, p.adminRetentativasBlock);
        assertEquals(1000.0, p.lEspacamentoMs);
        assertEquals(0.5, p.lProbReset);
        assertEquals(60.0, p.lSuporteSeg);
        assertEquals(2, p.appRetentativas);
        assertEquals(500.0, p.backoffMs);
        assertEquals(2000, p.hotrodTimeoutMs);
        assertEquals(1, p.hotrodRetentativas);
        // o objeto criado sem CLI tem os mesmos padroes
        S3Parametros q = new S3Parametros();
        assertEquals(p.usuarios, q.usuarios);
        assertEquals(p.hotrodTimeoutMs, q.hotrodTimeoutMs);
        assertEquals(p.backoffMs, q.backoffMs);
    }

    @Test
    @DisplayName("pools, taxas e tempos do S3 sao parametrizaveis por CLI")
    void parametrizavelPorCli() {
        Cli c = parse("--scenario", "S3", "--client-mode", "topology", "--admin-node", "10.0.0.2:11222",
                "--s3-users", "50", "--s3-limit-accounts", "4", "--s3-think-ms", "250",
                "--s3-idle-timeout-sec", "12", "--s3-admin-hold-min-sec", "5",
                "--s3-app-retries", "3", "--hotrod-timeout-ms", "1500", "--hotrod-retries", "2");
        assertEquals(S3Parametros.ModoCliente.topology, c.s3.modoCliente);
        assertEquals("10.0.0.2:11222", c.s3.noAdmin);
        assertEquals(50, c.s3.usuarios);
        assertEquals(4, c.s3.contasLimite);
        assertEquals(250.0, c.s3.pensarMs);
        assertEquals(12.0, c.s3.idleSeg);
        assertEquals(5.0, c.s3.adminHoldMinSeg);
        assertEquals(3, c.s3.appRetentativas);
        assertEquals(1500, c.s3.hotrodTimeoutMs);
        assertEquals(2, c.s3.hotrodRetentativas);
    }

    @Test
    @DisplayName("validar rejeita parametros invalidos do S3 so quando S3 esta ligado")
    void validacaoDoS3() {
        parse("--s3-users", "0", "--s3-limit-accounts", "0").validar(); // S1: ignora parametros do S3
        Cli c = parse("--scenario", "S3", "--s3-users", "0", "--s3-limit-accounts", "0");
        assertThrows(IllegalArgumentException.class, c::validar);
        assertThrows(IllegalArgumentException.class,
                parse("--scenario", "S3", "--s3-logout-prob", "1.5")::validar);
        assertThrows(IllegalArgumentException.class,
                parse("--scenario", "S3", "--hotrod-timeout-ms", "0")::validar);
        parse("--scenario", "S3").validar();
    }

    @Test
    @DisplayName("toString mostra S3 e continua sem expor a senha")
    void toStringDoS3() {
        Cli c = parse("--scenario", "S3", "--password", "segredo-xyz");
        assertTrue(c.toString().contains("scenario=S3"));
        assertFalse(c.toString().contains("segredo-xyz"));
    }
}
