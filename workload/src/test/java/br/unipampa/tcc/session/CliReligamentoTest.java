package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (P-6 da V-W5): {@code --rejoin-timeout-sec}, o prazo para a carga ver de novo todos os servidores quando o
 * AC-5 parou um no. Regra do autor: o numero nao tem padrao no codigo; e obrigatorio onde pode ser usado
 * ({@code --controller active} com {@code --convergence}, o unico caso em que o AC-5 pode ficar APLICADA e o laco
 * de convergencia roda depois). Em shadow e com o controlador desligado ele e aceito e nao exigido, para o
 * orquestrador passar a mesma linha de comando aos dois bracos.
 */
class CliReligamentoTest {

    static final String SERVIDORES = "127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224";

    static Cli parse(List<String> args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args.toArray(String[]::new));
        return c;
    }

    static List<String> s3(String modo, String... extras) {
        List<String> a = new ArrayList<>(List.of("--scenario", "S3", "--marker", "m.txt",
                "--controller", modo, "--controller-config", "c.json", "--controller-admin", SERVIDORES,
                "--controller-reference", "r.json", "--servers", SERVIDORES, "--csv-dir", "saida"));
        a.addAll(List.of(extras));
        return a;
    }

    @Test
    @DisplayName("sem a opcao o campo fica nulo (nenhum padrao)")
    void semPadrao() {
        assertNull(parse(List.of()).religamentoPrazoSeg);
    }

    @Test
    @DisplayName("active com --convergence exige --rejoin-timeout-sec (exit 2 em main), com o motivo")
    void activeComConvergenciaExige() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse(s3("active", "--convergence")).validar());
        assertTrue(e.getMessage().contains("--rejoin-timeout-sec"), e.getMessage());
        Cli c = parse(s3("active", "--convergence", "--rejoin-timeout-sec", "180"));
        c.validar();
        assertEquals(180L, c.religamentoPrazoSeg);
    }

    @Test
    @DisplayName("active sem --convergence, shadow e off nao exigem; shadow aceita a mesma opcao")
    void outrosNaoExigem() {
        parse(s3("active")).validar();
        parse(s3("shadow", "--convergence")).validar();
        parse(s3("shadow", "--convergence", "--rejoin-timeout-sec", "180")).validar();
        parse(List.of("--convergence")).validar();
    }

    @Test
    @DisplayName("prazo nao positivo e invalido em qualquer modo")
    void naoPositivo() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(s3("active", "--convergence", "--rejoin-timeout-sec", "0")).validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse(s3("shadow", "--rejoin-timeout-sec", "-1")).validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse(List.of("--rejoin-timeout-sec", "0")).validar());
    }

    @Test
    @DisplayName("toString nao muda (a opcao nova fica fora, como --replication e --controller)")
    void toStringInalterado() {
        Cli c = parse(s3("active", "--convergence", "--rejoin-timeout-sec", "180"));
        assertFalse(c.toString().contains("rejoin"));
        assertFalse(c.toString().contains("religamento"));
    }
}
