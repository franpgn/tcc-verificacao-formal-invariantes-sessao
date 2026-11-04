package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * B-27.3 (W-1, R-C3): golden do laco do cliente de S1/S2 ({@code WorkloadMain.rodarCliente}, assinatura
 * de 10 parametros). A referencia em {@code src/test/resources/golden-20805b0/} foi gerada com o codigo
 * da base 20805b0 (com o portao de emissao ainda presente, no modo {@code livre()}), antes da retirada
 * de K1..K4. Sem o portao, a sequencia de chamadas por semente tem de continuar identica byte a byte.
 * Nao regenerar a partir do codigo atual.
 */
class GoldenRodarClienteTest {

    private static final String CRLF = "" + (char) 13 + (char) 10;
    private static final String LF = "" + (char) 10;
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static Method rodarCliente10() throws Exception {
        Method m = WorkloadMain.class.getDeclaredMethod("rodarCliente", SessionOps.class, int.class, long.class,
                Scenario.class, long.class, long.class, AtomicLong.class, BooleanSupplier.class,
                VersaoMistura.class, MisturaV3.class);
        m.setAccessible(true);
        return m;
    }

    /** S1 e S2, v2 e v3, threads 0 e 3, 2000 operacoes cada; sids aleatorios (UUID) mascarados. */
    static String sequenciaAtual() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Scenario s : new Scenario[] {Scenario.S1, Scenario.S2}) {
            for (VersaoMistura mix : new VersaoMistura[] {VersaoMistura.V2, VersaoMistura.V3}) {
                for (int tid : new int[] {0, 3}) {
                    long seed = 27_300L + tid;
                    MundoV3 w = new MundoV3(1, false);
                    AtomicLong total = new AtomicLong();
                    rodarCliente10().invoke(null, w.ops, tid, Long.MAX_VALUE, s, seed, 2_000L, total,
                            (BooleanSupplier) () -> false, mix, mix == VersaoMistura.V3 ? w.mistura : null);
                    assertEquals(2_000L, total.get());
                    sb.append("# ").append(s).append(' ').append(mix).append(" tid=").append(tid)
                            .append(" seed=").append(seed).append('\n');
                    for (OpsGravadas.Chamada c : w.ops.chamadas) {
                        String arg = c.arg() != null && UUID.matcher(c.arg()).matches() ? "*" : c.arg();
                        sb.append(c.op()).append(' ').append(arg).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    @Test
    @DisplayName("R-C3: chamadas do laco do cliente de S1/S2 por semente identicas, byte a byte, as da base 20805b0")
    void lacoDoClienteIdenticoAoDaBase() throws Exception {
        String esperado;
        try (InputStream in = GoldenRodarClienteTest.class.getResourceAsStream(
                "/golden-20805b0/rodar-cliente-s1-s2.txt")) {
            assertNotNull(in, "referencia golden-20805b0 ausente");
            esperado = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace(CRLF, LF);
        }
        assertEquals(esperado, sequenciaAtual());
    }
}
