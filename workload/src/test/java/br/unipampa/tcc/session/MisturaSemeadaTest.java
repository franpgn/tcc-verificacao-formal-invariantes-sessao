package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.5: o {@code Random} da mistura de S1/S2 (tipo de cada operacao) e proprio de cada thread,
 * {@code randomDaMistura(s) = new Random(sementeMistura(s))}, com
 * {@code sementeMistura(s) = mix64(s ^ SAL_MISTURA)} e {@code s = sementeThread(seedBase, tid, rep)}.
 * W1..W8 do prompt (W9, guardas de fonte, e {@link MisturaSemeadaFonteTest}).
 *
 * <p>As constantes de W1 foram calculadas por uma emulacao independente (Python) de
 * {@code java.util.Random} e do finalizador SplitMix64, nao copiadas da saida desta classe; a mesma
 * emulacao reproduz {@code SEMENTE_TOKEN_42} e {@code DECISOES_40} de {@link TokenAntigoSemeadoTest}.</p>
 */
class MisturaSemeadaTest {

    static final double Z = 3.2905;

    /** sementeMistura(42), congelado. */
    static final long SEMENTE_MISTURA_42 = -595519573436187269L;

    /** Os 40 primeiros randomDaMistura(42).nextInt(100), congelados. */
    static final int[] NEXT_INT_100_40 = {55, 54, 10, 39, 89, 66, 29, 23, 82, 18, 80, 94, 8, 53, 76, 3, 55, 38,
        63, 4, 17, 74, 1, 91, 62, 40, 1, 70, 26, 22, 62, 85, 61, 82, 42, 0, 17, 40, 74, 91};

    private static int[] ints(Random r, int n) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = r.nextInt(100);
        }
        return v;
    }

    private static Scenario.Op[] ops(Scenario s, Random r, int n) {
        Scenario.Op[] v = new Scenario.Op[n];
        for (int i = 0; i < n; i++) {
            v[i] = s.proximaOperacao(r);
        }
        return v;
    }

    private static void noIc(long k, long n, double p, String msg) {
        double meia = Z * Math.sqrt(p * (1 - p) / n);
        double f = (double) k / n;
        assertTrue(Math.abs(f - p) <= meia, msg + ": k/n = " + f + ", p = " + p + ", meia-largura = " + meia);
    }

    // ---------------------------------------------------------------- W1

    @Test
    @DisplayName("W1: SAL_MISTURA e 'MISTURAS' em ASCII; sementeMistura = mix64(s ^ SAL_MISTURA); sementeMistura(42) congelado")
    void derivacaoCongelada() {
        assertEquals(0x4D49535455524153L, WorkloadMain.SAL_MISTURA);
        assertEquals(ByteBuffer.wrap("MISTURAS".getBytes(StandardCharsets.US_ASCII)).getLong(),
                WorkloadMain.SAL_MISTURA);
        long[] s = {0L, 1L, 42L, -1L, Long.MIN_VALUE, WorkloadMain.sementeThread(42, 3, 1)};
        for (long x : s) {
            assertEquals(MisturaV3.mix64(x ^ WorkloadMain.SAL_MISTURA), WorkloadMain.sementeMistura(x), "s = " + x);
        }
        assertEquals(SEMENTE_MISTURA_42, WorkloadMain.sementeMistura(42));
    }

    @Test
    @DisplayName("W1: os 40 primeiros randomDaMistura(42).nextInt(100), congelados")
    void sequenciaCongelada() {
        assertArrayEquals(NEXT_INT_100_40, ints(WorkloadMain.randomDaMistura(42), 40));
    }

    // ---------------------------------------------------------------- W2

    @Test
    @DisplayName("W2: sementeMistura(s) != s, != sementeToken(s) e sem colisao para s em [0, 10^5)")
    void dominioSeparadoDasSementes() {
        Set<Long> vistos = new HashSet<>();
        for (long s = 0; s < 100_000; s++) {
            long m = WorkloadMain.sementeMistura(s);
            assertNotEquals(s, m, "sementeMistura(s) == s para s = " + s);
            assertNotEquals(MisturaV3.sementeToken(s), m, "sementeMistura(s) == sementeToken(s) para s = " + s);
            assertTrue(vistos.add(m), "colisao em s = " + s);
        }
    }

    @Test
    @DisplayName("W2: o fluxo da mistura difere do fluxo do KeyGenerator e do token (sementeThread(42, t, 1), t = 0..3)")
    void fluxoSeparadoDoKeyGeneratorEDoToken() {
        for (int t = 0; t < 4; t++) {
            long s = WorkloadMain.sementeThread(42, t, 1);
            int[] mis = ints(WorkloadMain.randomDaMistura(s), 1000);
            assertFalse(Arrays.equals(mis, ints(new Random(s), 1000)), "mistura = fluxo do KeyGenerator, t = " + t);
            assertFalse(Arrays.equals(mis, ints(new Random(MisturaV3.sementeToken(s)), 1000)),
                    "mistura = fluxo do token, t = " + t);
        }
    }

    // ---------------------------------------------------------------- W3

    @Test
    @DisplayName("W3: mesma semente, mesma sequencia (10^4); s e s+1 diferem nas 1000 primeiras")
    void determinismo() {
        long s = WorkloadMain.sementeThread(42, 1, 1);
        int[] a = ints(WorkloadMain.randomDaMistura(s), 10_000);
        int[] b = ints(WorkloadMain.randomDaMistura(s), 10_000);
        assertArrayEquals(a, b, "dois randomDaMistura(s)");
        assertFalse(Arrays.equals(Arrays.copyOf(a, 1000), ints(WorkloadMain.randomDaMistura(s + 1), 1000)),
                "s e s+1 iguais nas 1000 primeiras");
    }

    @Test
    @DisplayName("W3: dois randomDaMistura(42) consumidos intercalados tem, cada um, a sequencia de um sozinho")
    void intercaladosNaoCompartilham() {
        int n = 10_000;
        int[] so = ints(WorkloadMain.randomDaMistura(42), n);
        Random a = WorkloadMain.randomDaMistura(42);
        Random b = WorkloadMain.randomDaMistura(42);
        int[] ia = new int[n];
        int[] ib = new int[n];
        for (int i = 0; i < n; i++) {
            ia[i] = a.nextInt(100);
            ib[i] = b.nextInt(100);
        }
        assertArrayEquals(so, ia, "A intercalado com B");
        assertArrayEquals(so, ib, "B intercalado com A");
    }

    // ---------------------------------------------------------------- W4

    @Test
    @DisplayName("W4: threads independentes: S1 com 2,5e5 sorteios difere duas a duas; VALIDATE simultaneo no IC de 0,25")
    void threadsIndependentes() {
        int n = 250_000;
        Scenario.Op[][] d = new Scenario.Op[4][];
        for (int t = 0; t < 4; t++) {
            d[t] = ops(Scenario.S1, WorkloadMain.randomDaMistura(WorkloadMain.sementeThread(42, t, 1)), n);
        }
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertFalse(Arrays.equals(d[i], d[j]), "threads " + i + " e " + j + " com a mesma sequencia");
                long ambos = 0;
                for (int x = 0; x < n; x++) {
                    if (d[i][x] == Scenario.Op.VALIDATE && d[j][x] == Scenario.Op.VALIDATE) {
                        ambos++;
                    }
                }
                noIc(ambos, n, 0.25, "VALIDATE simultaneo, threads " + i + " e " + j);
            }
        }
    }

    // ---------------------------------------------------------------- W5

    @Test
    @DisplayName("W5: 10^6 sorteios de S1 e S2 com randomDaMistura(sementeThread(42, 0, 1)) no IC do peso; S2 sem RESET_FAILURES")
    void distribuicao() {
        int n = 1_000_000;
        for (Scenario s : new Scenario[] {Scenario.S1, Scenario.S2}) {
            Random r = WorkloadMain.randomDaMistura(WorkloadMain.sementeThread(42, 0, 1));
            Map<Scenario.Op, Long> k = new EnumMap<>(Scenario.Op.class);
            for (int i = 0; i < n; i++) {
                k.merge(s.proximaOperacao(r), 1L, Long::sum);
            }
            for (Scenario.Op op : Scenario.Op.values()) {
                long c = k.getOrDefault(op, 0L);
                double p = s.peso(op) / 100.0;
                if (p == 0) {
                    assertEquals(0L, c, s + ": " + op + " sorteado com peso 0");
                } else {
                    noIc(c, n, p, s + " " + op);
                }
            }
        }
        assertEquals(0, Scenario.S2.peso(Scenario.Op.RESET_FAILURES));
    }

    // ---------------------------------------------------------------- W6..W8: caminho de producao

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    static final int OPS = 5000;

    /** Lista de chamadas (op, arg) do mundo, com o arg trocado por "*" so quando for UUID (sid da triade). */
    private static List<String> chamadas(MundoV3 w) {
        List<String> out = new ArrayList<>();
        for (OpsGravadas.Chamada c : w.ops.chamadas) {
            String arg = c.arg() != null && UUID.matcher(c.arg()).matches() ? "*" : c.arg();
            out.add(c.op() + " " + arg);
        }
        return out;
    }

    /** O rodarCliente REAL (privado), por reflexao, num mundo novo de 1 thread. */
    private static List<String> rodarCliente(Scenario s, VersaoMistura mix, int tid, long seed) throws Exception {
        Method m = WorkloadMain.class.getDeclaredMethod("rodarCliente", SessionOps.class, int.class, long.class,
                Scenario.class, long.class, long.class, AtomicLong.class, BooleanSupplier.class,
                VersaoMistura.class, MisturaV3.class);
        m.setAccessible(true);
        MundoV3 w = new MundoV3(1, false);
        AtomicLong total = new AtomicLong();
        BooleanSupplier parar = () -> false;
        m.invoke(null, w.ops, tid, Long.MAX_VALUE, s, seed, (long) OPS, total, parar, mix,
                mix == VersaoMistura.V3 ? w.mistura : null);
        assertEquals(OPS, total.get(), "operacoes executadas");
        return chamadas(w);
    }

    /** A composicao declarada: executarOperacoes com randomDaMistura(s), KeyGenerator(s) e cliente(s). */
    private static List<String> composicao(Scenario s, VersaoMistura mix, int tid, long seed) throws Exception {
        MundoV3 w = new MundoV3(1, false);
        WorkloadMain.executarOperacoes(w.ops, s, WorkloadMain.randomDaMistura(seed), new KeyGenerator(seed),
                new IdentidadesMistura(tid, mix), OPS, mix == VersaoMistura.V3 ? w.mistura.cliente(seed) : null);
        return chamadas(w);
    }

    private static final Scenario[] CENARIOS = {Scenario.S1, Scenario.S2};
    private static final VersaoMistura[] MIX = {VersaoMistura.V2, VersaoMistura.V3};
    private static final int[] TIDS = {0, 3};

    @Test
    @DisplayName("W6: rodarCliente real (reflexao) e reprodutivel: dois mundos novos, mesma lista; seed + 1 difere")
    void caminhoDeProducaoReprodutivel() throws Exception {
        for (Scenario s : CENARIOS) {
            for (VersaoMistura mix : MIX) {
                for (int tid : TIDS) {
                    String rot = s + "/" + mix + "/tid " + tid;
                    long seed = WorkloadMain.sementeThread(42, tid, 1);
                    List<String> a = rodarCliente(s, mix, tid, seed);
                    List<String> b = rodarCliente(s, mix, tid, seed);
                    assertTrue(a.size() >= OPS, rot + ": " + a.size() + " chamadas");
                    assertEquals(a, b, rot + ": mesma semente, listas diferentes");
                    assertNotEquals(a, rodarCliente(s, mix, tid, seed + 1), rot + ": seed + 1 com a mesma lista");
                }
            }
        }
    }

    @Test
    @DisplayName("W7: rodarCliente = executarOperacoes(randomDaMistura(s), KeyGenerator(s), IdentidadesMistura(t), cliente(s))")
    void caminhoDeProducaoEhAComposicao() throws Exception {
        for (Scenario s : CENARIOS) {
            for (VersaoMistura mix : MIX) {
                for (int tid : TIDS) {
                    long seed = WorkloadMain.sementeThread(42, tid, 1);
                    assertEquals(composicao(s, mix, tid, seed), rodarCliente(s, mix, tid, seed),
                            s + "/" + mix + "/tid " + tid);
                }
            }
        }
    }

    @Test
    @DisplayName("W8: randomDaMistura de rep 0 e rep 1 reprodutivel em cada rep e diferente entre reps")
    void repeticaoRandom() {
        long s0 = WorkloadMain.sementeThread(42, 0, 0);
        long s1 = WorkloadMain.sementeThread(42, 0, 1);
        int[] a0 = ints(WorkloadMain.randomDaMistura(s0), 1000);
        int[] a1 = ints(WorkloadMain.randomDaMistura(s1), 1000);
        assertArrayEquals(a0, ints(WorkloadMain.randomDaMistura(s0), 1000), "rep 0 reprodutivel");
        assertArrayEquals(a1, ints(WorkloadMain.randomDaMistura(s1), 1000), "rep 1 reprodutivel");
        assertFalse(Arrays.equals(a0, a1), "rep 0 e rep 1 iguais nas 1000 primeiras");
    }

    @Test
    @DisplayName("W8: rodarCliente real com rep 0 e rep 1 da listas diferentes (S1, S2; v2, v3)")
    void repeticaoCaminhoDeProducao() throws Exception {
        for (Scenario s : CENARIOS) {
            for (VersaoMistura mix : MIX) {
                assertNotEquals(rodarCliente(s, mix, 0, WorkloadMain.sementeThread(42, 0, 0)),
                        rodarCliente(s, mix, 0, WorkloadMain.sementeThread(42, 0, 1)), s + "/" + mix);
            }
        }
    }
}
