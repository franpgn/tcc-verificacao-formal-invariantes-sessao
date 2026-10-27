package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.Dormidor;
import br.unipampa.tcc.controle.ParametrosKnobs;
import br.unipampa.tcc.controle.Perfil;
import br.unipampa.tcc.controle.PerfilAtual;
import br.unipampa.tcc.controle.PortaoDeEmissao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27: o portao no cliente de S1/S2 (sobrecarga de 11 parametros de {@code rodarCliente}). K1 com
 * {@code sair()} em {@code finally}, K2 (pausa so em CONSERVADOR), P2 (portao em NORMAL = livre) e
 * a forma da delegacao da assinatura de 10 parametros.
 */
class PortaoS1S2Test {

    private static final long MS = 1_000_000L;

    private static Method rodarCliente11() throws Exception {
        Method m = WorkloadMain.class.getDeclaredMethod("rodarCliente", SessionOps.class, int.class, long.class,
                Scenario.class, long.class, long.class, AtomicLong.class, BooleanSupplier.class,
                VersaoMistura.class, MisturaV3.class, PortaoDeEmissao.class);
        m.setAccessible(true);
        return m;
    }

    private static Method rodarCliente10() throws Exception {
        Method m = WorkloadMain.class.getDeclaredMethod("rodarCliente", SessionOps.class, int.class, long.class,
                Scenario.class, long.class, long.class, AtomicLong.class, BooleanSupplier.class,
                VersaoMistura.class, MisturaV3.class);
        m.setAccessible(true);
        return m;
    }

    private static PortaoDeEmissao portao(Perfil perfil, ParametrosKnobs k, int nominal) {
        PerfilAtual p = new PerfilAtual();
        p.definir(perfil);
        return new PortaoDeEmissao(p, k, nominal, System::nanoTime, Dormidor.real(), e -> { }, 1L);
    }

    private static ParametrosKnobs comPausa(long pausaNs) {
        ParametrosKnobs p = ParametrosKnobs.padrao();
        return new ParametrosKnobs("teste", p.k1FracaoConservador(), pausaNs, p.k2MultPensarConservador(),
                p.k3AdiaMaxNs(), p.k3SondagemNs(), p.k4FatorBaseConservador(), p.k4JitterConservador(), null);
    }

    private static List<String> chamadas(MundoV3 w) {
        Pattern uuid = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        List<String> out = new ArrayList<>();
        for (OpsGravadas.Chamada c : w.ops.chamadas) {
            String arg = c.arg() != null && uuid.matcher(c.arg()).matches() ? "*" : c.arg();
            out.add(c.op() + " " + arg);
        }
        return out;
    }

    private static long rodar(MundoV3 w, Scenario s, VersaoMistura mix, int tid, long seed, long ops,
                              PortaoDeEmissao portao) throws Exception {
        AtomicLong total = new AtomicLong();
        long t0 = System.nanoTime();
        MisturaV3 v3 = mix == VersaoMistura.V3 ? w.mistura : null;
        if (portao == null) {
            rodarCliente10().invoke(null, w.ops, tid, Long.MAX_VALUE, s, seed, ops, total, (BooleanSupplier) () -> false,
                    mix, v3);
        } else {
            rodarCliente11().invoke(null, w.ops, tid, Long.MAX_VALUE, s, seed, ops, total,
                    (BooleanSupplier) () -> false, mix, v3, portao);
        }
        assertEquals(ops, total.get(), "operacoes executadas");
        return System.nanoTime() - t0;
    }

    @Test
    @DisplayName("P2: S1 e S2, v2 e v3: a sobrecarga com portao em NORMAL e a de 10 parametros (livre) dao as mesmas chamadas")
    void normalIgualALivre() throws Exception {
        for (Scenario s : new Scenario[] {Scenario.S1, Scenario.S2}) {
            for (VersaoMistura mix : new VersaoMistura[] {VersaoMistura.V2, VersaoMistura.V3}) {
                for (int tid : new int[] {0, 3}) {
                    long seed = 27_200L + tid;
                    MundoV3 a = new MundoV3(1, false);
                    rodar(a, s, mix, tid, seed, 2_000, null);
                    MundoV3 b = new MundoV3(1, false);
                    PortaoDeEmissao p = portao(Perfil.NORMAL, ParametrosKnobs.padrao(), 4);
                    rodar(b, s, mix, tid, seed, 2_000, p);
                    String ctx = s + " " + mix + " tid=" + tid;
                    assertEquals(chamadas(a), chamadas(b), ctx);
                    assertEquals(0, p.emVoo(), "permissoes devolvidas: " + ctx);
                    assertEquals(0L, p.esperaK1TotalNs(), ctx);
                }
            }
        }
    }

    /** Ops que lanca {@code erro} em toda operacao da mistura, anotando o emVoo do portao na chamada. */
    private static OpsGravadas lancando(PortaoDeEmissao p, AtomicInteger emVooNaChamada, AtomicInteger nChamadas,
                                        java.util.function.Supplier<? extends Throwable> erro) {
        return new OpsGravadas(new FakeCaches.Cache<>(), new FakeCaches.Cache<>(), new LatencyRegistry(), null) {
            private OpResult falhar() {
                emVooNaChamada.set(p.emVoo());
                nChamadas.incrementAndGet();
                Throwable t = erro.get();
                if (t instanceof RuntimeException re) {
                    throw re;
                }
                throw (Error) t;
            }

            @Override public OpResult login(String identidade, String credencial) { return falhar(); }

            @Override public OpResult validate(String sid) { return falhar(); }

            @Override public OpResult logout(String sid) { return falhar(); }

            @Override public OpResult incrementFailure(String identidade) { return falhar(); }

            @Override public OpResult resetFailures(String identidade) { return falhar(); }
        };
    }

    @Test
    @DisplayName("K1d: operacao que lanca dentro do laco da sobrecarga devolve a permissao (sair em finally)")
    void sairEmFinally() throws Exception {
        // RuntimeException: executarOperacao a engole (WorkloadMain, catch Exception); o laco segue e
        // cada operacao devolve a permissao.
        PortaoDeEmissao p = portao(Perfil.NORMAL, ParametrosKnobs.padrao(), 1);
        AtomicInteger emVooNaChamada = new AtomicInteger(-1);
        AtomicInteger chamadas = new AtomicInteger();
        AtomicLong total = new AtomicLong();
        rodarCliente11().invoke(null, lancando(p, emVooNaChamada, chamadas, () -> new IllegalStateException("x")),
                0, Long.MAX_VALUE, Scenario.S1, 5L, 10L, total, (BooleanSupplier) () -> false, VersaoMistura.V2,
                null, p);
        assertEquals(10, total.get(), "nominal 1: as 10 operacoes passaram (nenhuma permissao perdida)");
        assertEquals(10, chamadas.get());
        assertEquals(1, emVooNaChamada.get(), "a operacao roda com a permissao");
        assertEquals(0, p.emVoo());

        // Error (nao engolido): sai do laco pela excecao e a permissao volta pelo finally
        PortaoDeEmissao q = portao(Perfil.NORMAL, ParametrosKnobs.padrao(), 1);
        AtomicInteger emVooQ = new AtomicInteger(-1);
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
                () -> rodarCliente11().invoke(null, lancando(q, emVooQ, new AtomicInteger(),
                                () -> new AssertionError("erro que escapa")), 0, Long.MAX_VALUE, Scenario.S1, 5L, 10L,
                        new AtomicLong(), (BooleanSupplier) () -> false, VersaoMistura.V2, null, q));
        assertInstanceOf(AssertionError.class, e.getCause());
        assertEquals(1, emVooQ.get(), "a operacao roda com a permissao");
        assertEquals(0, q.emVoo(), "permissao devolvida apesar da excecao");
        p.encerrar();
        q.encerrar();
    }

    @Test
    @DisplayName("K2a: em NORMAL a sobrecarga nao pausa; em CONSERVADOR pausa k2PausaConservadorNs por operacao")
    void pausaSoEmConservador() throws Exception {
        MundoV3 w = new MundoV3(1, false);
        // NORMAL com pausa configurada de 50 ms: 200 operacoes levariam 10 s se pausasse
        long normal = rodar(w, Scenario.S1, VersaoMistura.V2, 0, 1L, 200,
                portao(Perfil.NORMAL, comPausa(50 * MS), 4));
        assertTrue(normal < TimeUnit.SECONDS.toNanos(3), "NORMAL pausou: " + normal / MS + " ms");
        long cons = rodar(new MundoV3(1, false), Scenario.S1, VersaoMistura.V2, 0, 1L, 20,
                portao(Perfil.CONSERVADOR, comPausa(20 * MS), 4));
        assertTrue(cons >= 20 * 20 * MS, "CONSERVADOR pausou so " + cons / MS + " ms");
        assertTrue(cons < TimeUnit.SECONDS.toNanos(5), "CONSERVADOR pausou demais: " + cons / MS + " ms");
    }

    @Test
    @DisplayName("K1: interrupcao presa em entrar() restaura a flag e sai do laco")
    void interrupcaoEmEntrar() throws Exception {
        PortaoDeEmissao p = portao(Perfil.NORMAL, ParametrosKnobs.padrao(), 1);
        p.entrar(); // a unica permissao fica com o teste
        MundoV3 w = new MundoV3(1, false);
        AtomicLong total = new AtomicLong();
        AtomicBoolean flag = new AtomicBoolean();
        AtomicBoolean terminou = new AtomicBoolean();
        Thread t = new Thread(() -> {
            try {
                rodarCliente11().invoke(null, w.ops, 0, Long.MAX_VALUE, Scenario.S1, 1L, 100L, total,
                        (BooleanSupplier) () -> false, VersaoMistura.V2, null, p);
                terminou.set(true);
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                flag.set(Thread.currentThread().isInterrupted());
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (t.getState() != Thread.State.WAITING && System.nanoTime() < fim) {
                Thread.sleep(2);
            }
            assertEquals(Thread.State.WAITING, t.getState(), "cliente preso no K1");
            t.interrupt();
            t.join(2000);
            assertFalse(t.isAlive(), "o cliente saiu do laco");
            assertTrue(terminou.get(), "saiu sem excecao");
            assertTrue(flag.get(), "flag de interrupcao restaurada");
            assertEquals(0, total.get(), "nenhuma operacao sem permissao");
            assertEquals(1, p.emVoo());
        } finally {
            p.encerrar();
        }
    }

    // ------------------------------------------------------------------ forma da delegacao (N16)

    private static final String ASSINATURA_10 = "private static void rodarCliente\\(SessionOps ops, int tid, long deadline,"
            + "\\s*Scenario cenario, long seed,\\s*long opsAlvo, AtomicLong totalOps,"
            + "\\s*BooleanSupplier parar, VersaoMistura mix, MisturaV3 misturaV3\\)";

    private static final String DELEGACAO = "rodarCliente(ops, tid, deadline, cenario, seed, opsAlvo, totalOps, parar, "
            + "mix, misturaV3, PortaoDeEmissao.livre());";

    @Test
    @DisplayName("N16: livre() e singleton; a de 10 parametros so delega com PortaoDeEmissao.livre(); a de 11 vem antes e tem o portao")
    void delegacaoComLivre() throws Exception {
        assertSame(PortaoDeEmissao.livre(), PortaoDeEmissao.livre());
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/WorkloadMain.java");
        String src = Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String masc = DetectorFonte.mascarado(src);
        String limpo = DetectorFonte.semComentarios(src);
        int[] c10 = DetectorFonte.corpo(masc, ASSINATURA_10);
        assertNotNull(c10, "assinatura de 10 parametros");
        String corpo10 = limpo.substring(c10[0] + 1, c10[1]).trim();
        assertEquals(DELEGACAO, corpo10.replaceAll("\\s+", " ").replace("( ", "("));
        int[] primeiro = DetectorFonte.corpo(masc, "private static void rodarCliente\\(");
        assertNotNull(primeiro);
        assertTrue(primeiro[0] < c10[0], "a de 11 parametros vem antes da de 10");
        String corpo11 = limpo.substring(primeiro[0], primeiro[1] + 1);
        assertTrue(corpo11.contains("portao.entrar()"), "a primeira rodarCliente usa o portao");
        assertTrue(corpo11.contains("portao.sair()"));
        Matcher m = Pattern.compile("private static void rodarCliente\\(").matcher(masc);
        int n = 0;
        while (m.find()) {
            n++;
        }
        assertEquals(2, n, "exatamente duas rodarCliente");
    }
}
