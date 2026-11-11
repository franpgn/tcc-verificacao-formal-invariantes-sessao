package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7b (DT-008): o token antigo da mistura v3 e um sorteio de Bernoulli p = 0,05 por Validate
 * isolado, com um {@link Random} proprio de cada thread semeado por
 * {@code sementeToken(sementeThread) = mix64(sementeThread ^ SAL_TOKEN)}; o {@code Random} da mistura
 * nao e consumido. U1..U3, U5, U6 e U8 do prompt (U4 sao {@code SequenciaMisturaV3Test} e
 * {@code GoldenS1S2Test}, sem edicao; U7 e {@link SementeThreadTest}; U9 e {@link TokenAntigoFonteTest}).
 *
 * <p>As constantes de U1 foram calculadas por uma emulacao independente (Python) de
 * {@code java.util.Random} e do finalizador SplitMix64, nao copiadas da saida desta classe.</p>
 */
class TokenAntigoSemeadoTest {

    static final double P = 0.05;
    static final double Z = 3.2905;

    /** sementeToken(42), congelado. */
    static final long SEMENTE_TOKEN_42 = 2621948109880469469L;

    /** As 40 primeiras decisoes de cliente(42) ('1' = token antigo), congeladas. */
    static final String DECISOES_40 = "0000100000000000000000000000000000100000";

    /** Posicoes de token antigo entre as 1000 primeiras decisoes de cliente(42), congeladas. */
    static final List<Integer> POSICOES_1000 = List.of(4, 34, 56, 81, 100, 183, 207, 224, 262, 301, 322, 324,
            325, 340, 355, 370, 375, 383, 426, 507, 515, 540, 586, 610, 616, 627, 654, 716, 725, 747, 769, 779,
            794, 803, 814, 835, 845, 865, 866, 868, 887, 940, 942, 961);

    private static MisturaV3 mistura(WarmupPolicy p) {
        return new MisturaV3(new SlotsGeracao(10, 1), p);
    }

    private static boolean[] decisoes(MisturaV3.Cliente c, int n) {
        boolean[] d = new boolean[n];
        for (int i = 0; i < n; i++) {
            d[i] = c.proximoEhTokenAntigo();
        }
        return d;
    }

    private static int verdadeiros(boolean[] d) {
        int k = 0;
        for (boolean b : d) {
            k += b ? 1 : 0;
        }
        return k;
    }

    private static void noIc(long k, long n, double p, String msg) {
        double meia = Z * Math.sqrt(p * (1 - p) / n);
        double f = (double) k / n;
        assertTrue(Math.abs(f - p) <= meia, msg + ": k/n = " + f + ", p = " + p + ", meia-largura = " + meia);
    }

    // ---------------------------------------------------------------- U1

    @Test
    @DisplayName("U1: SAL_TOKEN e 'TOKENANT' em ASCII; mix64 e o finalizador SplitMix64; sementeToken = mix64(s ^ SAL)")
    void derivacaoCongelada() {
        assertEquals(0x544F4B454E414E54L, MisturaV3.SAL_TOKEN);
        assertEquals(ByteBuffer.wrap("TOKENANT".getBytes(StandardCharsets.US_ASCII)).getLong(), MisturaV3.SAL_TOKEN);
        // ancora externa: primeira saida do SplitMix64 com estado 0 (Steele, Lea, Flood 2014)
        assertEquals(0xE220A8397B1DCDAFL, MisturaV3.mix64(0x9E3779B97F4A7C15L));
        long[] s = {0L, 1L, 42L, -1L, Long.MIN_VALUE};
        long[] congelado = {-7724996358903509638L, 7880957947818844497L, SEMENTE_TOKEN_42,
                143759986413465447L, 7595910577557860251L};
        for (int i = 0; i < s.length; i++) {
            assertEquals(MisturaV3.mix64(s[i] ^ MisturaV3.SAL_TOKEN), MisturaV3.sementeToken(s[i]), "s = " + s[i]);
            assertEquals(congelado[i], MisturaV3.sementeToken(s[i]), "valor congelado, s = " + s[i]);
        }
    }

    @Test
    @DisplayName("U1: as 40 primeiras decisoes de cliente(42) e as posicoes nas 1000 primeiras, congeladas")
    void decisoesCongeladas() {
        boolean[] d = decisoes(mistura(WarmupPolicy.always()).cliente(42), 1000);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append(d[i] ? '1' : '0');
        }
        assertEquals(DECISOES_40, sb.toString());
        List<Integer> pos = new ArrayList<>();
        for (int i = 0; i < d.length; i++) {
            if (d[i]) {
                pos.add(i);
            }
        }
        assertEquals(POSICOES_1000, pos);
    }

    @Test
    @DisplayName("U1: sementeToken(s) != s e sem colisao para s em [0, 10^5)")
    void semColisao() {
        Set<Long> vistos = new HashSet<>();
        for (long s = 0; s < 100_000; s++) {
            long t = MisturaV3.sementeToken(s);
            assertNotEquals(s, t, "sementeToken(s) == s para s = " + s);
            assertTrue(vistos.add(t), "colisao em s = " + s);
        }
    }

    // ---------------------------------------------------------------- U2

    @Test
    @DisplayName("U2: mesma semente, mesma sequencia (10^4); sementes 42 e 43 diferem nas 1000 primeiras")
    void mesmaSementeMesmaSequencia() {
        boolean[] a = decisoes(mistura(WarmupPolicy.always()).cliente(42), 10_000);
        boolean[] b = decisoes(mistura(WarmupPolicy.always()).cliente(42), 10_000);
        assertArrayEquals(a, b);
        boolean[] c = decisoes(mistura(WarmupPolicy.always()).cliente(43), 1000);
        assertFalse(Arrays.equals(Arrays.copyOf(a, 1000), c), "cliente(42) e cliente(43) iguais nas 1000 primeiras");
    }

    @Test
    @DisplayName("U2: dois clientes da mesma MisturaV3 intercalados tem cada um a sequencia de sozinho")
    void intercaladosNaoCompartilhamRandom() {
        int n = 10_000;
        boolean[] soA = decisoes(mistura(WarmupPolicy.always()).cliente(42), n);
        boolean[] soB = decisoes(mistura(WarmupPolicy.always()).cliente(43), n);
        MisturaV3 m = mistura(WarmupPolicy.always());
        MisturaV3.Cliente a = m.cliente(42);
        MisturaV3.Cliente b = m.cliente(43);
        boolean[] ia = new boolean[n];
        boolean[] ib = new boolean[n];
        for (int i = 0; i < n; i++) {
            ia[i] = a.proximoEhTokenAntigo();
            ib[i] = b.proximoEhTokenAntigo();
        }
        assertArrayEquals(soA, ia, "A intercalado com B");
        assertArrayEquals(soB, ib, "B intercalado com A");
    }

    // ---------------------------------------------------------------- U3

    /** Random que registra cada chamada de {@code next(bits)} (bits e valor devolvido). */
    static final class Gravador extends Random {
        final List<Long> chamadas = new ArrayList<>();

        Gravador(long semente) {
            super(semente);
        }

        @Override
        protected int next(int bits) {
            int v = super.next(bits);
            chamadas.add(((long) bits << 32) | (v & 0xFFFFFFFFL));
            return v;
        }
    }

    @Test
    @DisplayName("U3: o Random da mistura recebe as mesmas chamadas com v3 = cliente(42) e com v3 = null (S1, S2)")
    void randomDaMisturaNaoConsumido() throws Exception {
        for (Scenario s : new Scenario[] {Scenario.S1, Scenario.S2}) {
            MundoV3 w3 = new MundoV3(1, false);
            Gravador g3 = new Gravador(42);
            WorkloadMain.executarOperacoes(w3.ops, s, g3, new KeyGenerator(42),
                    new IdentidadesMistura(0, VersaoMistura.V3), 5000, w3.mistura.cliente(42));
            MundoV3 w0 = new MundoV3(1, false);
            Gravador g0 = new Gravador(42);
            WorkloadMain.executarOperacoes(w0.ops, s, g0, new KeyGenerator(42),
                    new IdentidadesMistura(0, VersaoMistura.V3), 5000, null);
            assertTrue(g0.chamadas.size() >= 5000, s + ": " + g0.chamadas.size());
            assertTrue(w3.mistura.tokenAntigo() > 0, s + ": o token antigo foi sorteado");
            assertEquals(g0.chamadas, g3.chamadas, s + ": sequencia de next(bits) do Random da mistura");
        }
    }

    /** Posicoes (indice entre os Validate isolados) de token antigo com cliente(42) e o Random r. */
    private static List<Integer> posicoes(Random r, Scenario s, int n) throws Exception {
        MundoV3 w = new MundoV3(1, false);
        WorkloadMain.executarOperacoes(w.ops, s, r, new KeyGenerator(42),
                new IdentidadesMistura(0, VersaoMistura.V3), n, w.mistura.cliente(42));
        List<OpsGravadas.Chamada> val = w.isolados(SessionOps.OP_VALIDATE);
        List<Integer> pos = new ArrayList<>();
        for (int i = 0; i < val.size(); i++) {
            if (MundoV3.invalid(val.get(i).r())) {
                pos.add(i);
            }
        }
        assertEquals(pos.size(), w.mistura.tokenAntigo(), "sem concorrencia, INVALID = token antigo");
        assertEquals(val.size(), w.mistura.validateIsolados());
        return pos;
    }

    @Test
    @DisplayName("U3: com o mesmo cliente(42), Random(1) e Random(2) da mistura dao as mesmas posicoes de token antigo")
    void posicoesIndependemDoRandomDaMistura() throws Exception {
        for (Scenario s : new Scenario[] {Scenario.S1, Scenario.S2}) {
            List<Integer> a = posicoes(new Random(1), s, 5000);
            List<Integer> b = posicoes(new Random(2), s, 5000);
            assertTrue(a.size() > 20 && b.size() > 20, s + ": " + a.size() + ", " + b.size());
            int n = Math.min(a.size(), b.size());
            assertEquals(a.subList(0, n), b.subList(0, n), s.name());
            assertEquals(POSICOES_1000.subList(0, Math.min(POSICOES_1000.size(), n)),
                    a.subList(0, Math.min(POSICOES_1000.size(), n)), s + ": posicoes congeladas de cliente(42)");
        }
    }

    // ---------------------------------------------------------------- U5

    @Test
    @DisplayName("U5: fracao no IC de 99,9 %: 10^6 decisoes de cliente(42) e 4 x 2,5e5 de cliente(sementeThread(42, t, 0))")
    void fracaoNoIc() {
        boolean[] d = decisoes(mistura(WarmupPolicy.always()).cliente(42), 1_000_000);
        noIc(verdadeiros(d), d.length, P, "cliente(42), n = 10^6");
        MisturaV3 m = mistura(WarmupPolicy.always());
        for (int t = 0; t < 4; t++) {
            boolean[] dt = decisoes(m.cliente(WorkloadMain.sementeThread(42, t, 0)), 250_000);
            noIc(verdadeiros(dt), dt.length, P, "thread " + t);
        }
    }

    // ---------------------------------------------------------------- U6

    @Test
    @DisplayName("U6: threads independentes: sequencias diferentes duas a duas; coincidencia de true no IC de p^2")
    void threadsIndependentes() {
        int n = 250_000;
        MisturaV3 m = mistura(WarmupPolicy.always());
        boolean[][] d = new boolean[4][];
        for (int t = 0; t < 4; t++) {
            d[t] = decisoes(m.cliente(WorkloadMain.sementeThread(42, t, 0)), n);
        }
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertFalse(Arrays.equals(d[i], d[j]), "threads " + i + " e " + j + " com a mesma sequencia");
                long ambos = 0;
                for (int x = 0; x < n; x++) {
                    if (d[i][x] && d[j][x]) {
                        ambos++;
                    }
                }
                noIc(ambos, n, P * P, "coincidencias threads " + i + " e " + j);
            }
        }
    }

    // ---------------------------------------------------------------- U8

    /** Relogio ajustavel para a politica de warm-up. */
    static final class RelogioManual extends Clock {
        volatile long agoraMs;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(agoraMs);
        }

        @Override
        public long millis() {
            return agoraMs;
        }
    }

    @Test
    @DisplayName("U8: o sorteio acontece dentro e fora da janela; a politica so muda os contadores")
    void sorteioIndependeDaJanela() {
        int n = 10_000;
        MisturaV3 nunca = mistura(WarmupPolicy.never());
        MisturaV3 sempre = mistura(WarmupPolicy.always());
        MisturaV3.Cliente cn = nunca.cliente(42);
        MisturaV3.Cliente cs = sempre.cliente(42);
        boolean[] dn = new boolean[n];
        boolean[] ds = new boolean[n];
        for (int i = 0; i < n; i++) {
            dn[i] = cn.proximoEhTokenAntigo();
            cn.contarValidate(dn[i]);
            ds[i] = cs.proximoEhTokenAntigo();
            cs.contarValidate(ds[i]);
        }
        assertArrayEquals(ds, dn, "never() e always() dao a mesma sequencia de decisoes");
        assertEquals(0, nunca.validateIsolados());
        assertEquals(0, nunca.tokenAntigo());
        assertEquals(n, sempre.validateIsolados());
        assertEquals(verdadeiros(ds), sempre.tokenAntigo());
        assertTrue(sempre.tokenAntigo() > 0);

        // warm-up -> medicao no meio da sequencia: as decisoes seguem as de um cliente(42) sozinho
        RelogioManual relogio = new RelogioManual();
        WarmupPolicy janela = WarmupPolicy.padrao(0L, 100L, 10L, relogio); // medicao em [15 s, 100 s)
        MisturaV3 mj = mistura(janela);
        MisturaV3.Cliente cj = mj.cliente(42);
        boolean[] dj = new boolean[n];
        long esperadoNaJanela = 0;
        for (int i = 0; i < n; i++) {
            relogio.agoraMs = i < n / 2 ? 0L : 20_000L;
            dj[i] = cj.proximoEhTokenAntigo();
            cj.contarValidate(dj[i]);
            if (i >= n / 2 && dj[i]) {
                esperadoNaJanela++;
            }
        }
        assertArrayEquals(ds, dj, "a sequencia nao depende da janela de medicao");
        assertEquals(n / 2, mj.validateIsolados());
        assertEquals(esperadoNaJanela, mj.tokenAntigo());
    }
}
