package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.Dormidor;
import br.unipampa.tcc.controle.EventoK3;
import br.unipampa.tcc.controle.ParametrosKnobs;
import br.unipampa.tcc.controle.Perfil;
import br.unipampa.tcc.controle.PerfilAtual;
import br.unipampa.tcc.controle.PortaoDeEmissao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27: o portao dentro de {@link AplicacaoS3}. K3 so atrasa (nunca descarta nem reordena por
 * ator), K4 nao muda o numero de tentativas, e a acao registra {@code tFimNs} (depois da ultima
 * tentativa) e {@code esperaPortaoNs} (K1 + K3). Relogio falso; {@link AplicacaoS3Test} e a
 * referencia do comportamento sem portao.
 */
class PortaoS3AplicacaoTest {

    static final long MS = 1_000_000L;
    static final long S = 1_000_000_000L;
    static final long SEMENTE = 271L;
    static final List<String> OPS = List.of("Login", "Validate", "Logout", "IncrementFailure", "ResetFailures",
            "Block", "Unblock");

    /** Mundo minimo: relogio falso, perfil, portao real e aplicacao com o portao. */
    static final class Mundo {
        final RelogioFalso relogio = new RelogioFalso(1_000 * S);
        final S3Parametros p = new S3Parametros();
        final S3Registro registro = new S3Registro();
        final PerfilAtual perfil = new PerfilAtual();
        final List<EventoK3> eventos = new ArrayList<>();
        /** Gancho chamado depois de cada sondagem do K3 (troca de perfil, encerramento). */
        Runnable aposSondagem = () -> { };
        final PortaoDeEmissao portao;
        final AplicacaoS3 app;

        Mundo(Perfil inicial) {
            this(inicial, ParametrosKnobs.padrao(), true);
        }

        Mundo(Perfil inicial, ParametrosKnobs k, boolean real) {
            perfil.definir(inicial);
            Dormidor d = ns -> {
                relogio.dormirNs(ns);
                aposSondagem.run();
            };
            portao = real ? new PortaoDeEmissao(perfil, k, 4, relogio::agoraNs, d, eventos::add, SEMENTE)
                    : PortaoDeEmissao.livre();
            app = new AplicacaoS3(relogio, p, registro, portao);
        }

        int retentativas(String op) {
            return op.equals("Block") ? p.adminRetentativasBlock : p.appRetentativas;
        }
    }

    /** Chamada que falha {@code falhas} vezes (avancando 2 s, como o timeout real) e depois devolve ok. */
    static Supplier<OpResult> falhas(RelogioFalso relogio, int falhas, AtomicInteger n, List<String> log, String rotulo) {
        return () -> {
            log.add(rotulo);
            if (n.incrementAndGet() <= falhas) {
                relogio.agora += 2 * S;
                return OpResult.errorTransport();
            }
            return OpResult.okNoop();
        };
    }

    /** Valor de K4 pela formula do prompt, com o {@link MisturaV3#mix64} do pacote session. */
    static long k4Esperado(ParametrosKnobs k, String ator, long base, int tentativa, long inicio) {
        long z = MisturaV3.mix64(SEMENTE ^ MisturaV3.mix64(ator.hashCode()) ^ MisturaV3.mix64(inicio) ^ tentativa);
        double u = (z >>> 11) * 0x1.0p-53;
        return Math.round(base * k.k4FatorBaseConservador() * (1 + k.k4JitterConservador() * (2 * u - 1)));
    }

    private static int[] rodar(Mundo m, String ator, String op, int nFalhas, List<String> log) {
        AtomicInteger n = new AtomicInteger();
        m.app.executar(ator, op, "alvo", m.retentativas(op), falhas(m.relogio, nFalhas, n, log, op));
        AcaoS3 a = m.registro.acoes().get(m.registro.acoes().size() - 1);
        return new int[] {n.get(), a.tentativas(), a.falhaPercebida() ? 1 : 0};
    }

    @Test
    @DisplayName("K3c: em todo cenario (NORMAL, prazo, saida de CONSERVADOR, encerramento) cada operacao chama o armazenamento tantas vezes quanto sem portao")
    void nuncaDescarta() {
        int[] padroes = {0, 1, 1000};
        String[] cenarios = {"normal", "prazo", "saida", "encerramento"};
        for (String op : OPS) {
            for (int f : padroes) {
                Mundo livre = new Mundo(Perfil.NORMAL, ParametrosKnobs.padrao(), false);
                int[] ref = rodar(livre, "admin", op, f, new ArrayList<>());
                for (String c : cenarios) {
                    Mundo m = new Mundo(c.equals("normal") ? Perfil.NORMAL : Perfil.CONSERVADOR);
                    long t0 = m.relogio.agora;
                    if (c.equals("saida")) {
                        m.aposSondagem = () -> {
                            if (m.relogio.agora - t0 >= 2 * S) m.perfil.definir(Perfil.NORMAL);
                        };
                    } else if (c.equals("encerramento")) {
                        m.aposSondagem = m.portao::encerrar;
                    }
                    int[] r = rodar(m, "admin", op, f, new ArrayList<>());
                    String ctx = op + " falhas=" + f + " cenario=" + c;
                    assertEquals(ref[0], r[0], "chamadas ao armazenamento: " + ctx);
                    assertEquals(ref[1], r[1], "tentativas: " + ctx);
                    assertEquals(ref[2], r[2], "falha percebida: " + ctx);
                    assertEquals(1, m.registro.totalAcoes(), "uma acao registrada: " + ctx);
                    boolean adiavel = !List.of("Login", "Validate", "Logout").contains(op);
                    assertEquals(adiavel && !c.equals("normal") ? 1 : 0, m.eventos.size(), "eventos K3: " + ctx);
                }
            }
        }
    }

    @Test
    @DisplayName("K3d: duas operacoes administrativas seguidas do mesmo ator, ambas adiadas, chegam ao armazenamento na ordem de emissao")
    void ordemPorAtor() {
        Mundo m = new Mundo(Perfil.CONSERVADOR);
        List<String> log = new ArrayList<>();
        m.app.executar("admin", "Block", "s3-u-001", 1, falhas(m.relogio, 0, new AtomicInteger(), log, "Block s3-u-001"));
        m.app.executar("admin", "Unblock", "s3-u-001", 2,
                falhas(m.relogio, 0, new AtomicInteger(), log, "Unblock s3-u-001"));
        assertEquals(List.of("Block s3-u-001", "Unblock s3-u-001"), log);
        assertEquals(2, m.portao.adiamentosK3(), "as duas foram adiadas");
        List<AcaoS3> acoes = m.registro.acoes();
        assertEquals("Block", acoes.get(0).operacao());
        assertEquals("Unblock", acoes.get(1).operacao());
        assertTrue(acoes.get(0).tFimNs() <= acoes.get(1).tNs());
    }

    @Test
    @DisplayName("K3e (via aplicacao): Validate em CONSERVADOR nao espera; Unblock do suporte espera")
    void escopoNaAplicacao() {
        Mundo m = new Mundo(Perfil.CONSERVADOR);
        long t0 = m.relogio.agora;
        m.app.executar("s3-u-000", "Validate", "sid", 2, () -> OpResult.okNoop());
        assertEquals(t0, m.relogio.agora, "Validate nao espera");
        assertEquals(0, m.registro.acoes().get(0).esperaPortaoNs());
        m.app.executar("suporte", "Unblock", "s3-l-000", 2, () -> OpResult.okNoop());
        AcaoS3 u = m.registro.acoes().get(1);
        assertEquals(ParametrosKnobs.padrao().k3AdiaMaxNs(), u.esperaPortaoNs(), "Unblock do suporte espera o prazo");
        assertEquals(1, m.eventos.size());
        assertEquals("suporte", m.eventos.get(0).ator());
    }

    @Test
    @DisplayName("K4a: em CONSERVADOR o numero de tentativas e de chamadas e o de NORMAL; as esperas seguem a formula do K4")
    void retentativas() {
        int[] padroes = {0, 1, 2, 3, 1000};
        for (String op : List.of("Validate", "Logout", "Block")) {
            for (int f : padroes) {
                Mundo normal = new Mundo(Perfil.NORMAL);
                int[] rn = rodar(normal, "s3-u-005", op, f, new ArrayList<>());
                Mundo cons = new Mundo(Perfil.CONSERVADOR); // o Block ainda espera o prazo do K3
                long inicio = cons.relogio.agora;
                int[] rc = rodar(cons, "s3-u-005", op, f, new ArrayList<>());
                String ctx = op + " falhas=" + f;
                assertEquals(rn[0], rc[0], "chamadas: " + ctx);
                assertEquals(rn[1], rc[1], "tentativas: " + ctx);
                assertEquals(rn[2], rc[2], "falha percebida: " + ctx);
                // esperas de backoff (as do K3, no Block, sao sondagens de 100 ms e vem antes)
                List<Long> backoffN = normal.relogio.esperas;
                List<Long> todas = cons.relogio.esperas;
                List<Long> backoffC = todas.subList(todas.size() - backoffN.size(), todas.size());
                long base = S3Parametros.msParaNs(normal.p.backoffMs);
                for (int i = 0; i < backoffN.size(); i++) {
                    assertEquals(base << i, backoffN.get(i), "NORMAL dobra a base: " + ctx);
                    assertEquals(k4Esperado(ParametrosKnobs.padrao(), "s3-u-005", base << i, i + 1, inicio),
                            backoffC.get(i), "K4 em CONSERVADOR: " + ctx + " tentativa " + (i + 1));
                }
            }
        }
    }

    @Test
    @DisplayName("F1: tFimNs = instante depois da ultima tentativa (inclui K3 e backoff); esperaPortaoNs = espera do K3")
    void fimComK3EBackoff() {
        Mundo m = new Mundo(Perfil.CONSERVADOR);
        long t0 = m.relogio.agora;
        AtomicInteger n = new AtomicInteger();
        m.app.executar("admin", "Block", "s3-u-002", 1, falhas(m.relogio, 1, n, new ArrayList<>(), "b"));
        AcaoS3 a = m.registro.acoes().get(0);
        long k3 = ParametrosKnobs.padrao().k3AdiaMaxNs();
        long backoff = k4Esperado(ParametrosKnobs.padrao(), "admin", 500 * MS, 1, t0);
        assertEquals(2, n.get());
        assertEquals(t0, a.tNs(), "t_ns e o inicio da acao, antes do K3");
        assertEquals(m.relogio.agora, a.tFimNs());
        assertEquals(k3 + 2 * S + backoff, a.tFimNs() - a.tNs());
        assertEquals(k3, a.esperaPortaoNs(), "esperaPortaoNs = K3 (K1 nao esperou)");
        assertEquals(m.eventos.get(0).liberadoEmNs() - m.eventos.get(0).adiadoEmNs(), a.esperaPortaoNs());

        // sem portao: tFim inclui o backoff 500 ms + 1 s e as duas falhas de 2 s
        Mundo livre = new Mundo(Perfil.NORMAL, ParametrosKnobs.padrao(), false);
        long l0 = livre.relogio.agora;
        livre.app.executar("u", "Validate", "sid", 2, falhas(livre.relogio, 2, new AtomicInteger(), new ArrayList<>(), "v"));
        AcaoS3 v = livre.registro.acoes().get(0);
        assertEquals(l0, v.tNs());
        assertEquals(2 * S + 500 * MS + 2 * S + 1_000 * MS, v.tFimNs() - v.tNs());
        assertEquals(0, v.esperaPortaoNs());
    }

    /** Relogio falso seguro entre threads (o K1 precisa de duas threads). */
    static final class RelogioAtomico implements RelogioS3 {
        final AtomicLong agora = new AtomicLong(5_000 * S);

        @Override public long agoraNs() { return agora.get(); }

        @Override public void dormirAteNs(long alvoNs) { agora.accumulateAndGet(alvoNs, Math::max); }
    }

    @Test
    @DisplayName("F1: esperaPortaoNs soma a espera do K1 (thread presa no portao enquanto o relogio anda)")
    void fimComK1() throws Exception {
        RelogioAtomico rel = new RelogioAtomico();
        PerfilAtual perfil = new PerfilAtual();
        S3Registro reg = new S3Registro();
        PortaoDeEmissao portao = new PortaoDeEmissao(perfil, ParametrosKnobs.padrao(), 1, rel::agoraNs,
                rel::dormirNs, e -> { }, SEMENTE);
        AplicacaoS3 app = new AplicacaoS3(rel, new S3Parametros(), reg, portao);
        portao.entrar(); // a unica permissao fica com o teste
        long t0 = rel.agoraNs();
        Thread t = new Thread(() -> app.executar("s3-u-000", "Validate", "sid", 2, () -> {
            rel.agora.addAndGet(3 * MS);
            return OpResult.okNoop();
        }));
        t.setDaemon(true);
        t.start();
        try {
            long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (t.getState() != Thread.State.WAITING && System.nanoTime() < fim) {
                Thread.sleep(2);
            }
            assertEquals(Thread.State.WAITING, t.getState(), "a acao fica presa no K1");
            rel.agora.addAndGet(7 * S);
            portao.sair();
            t.join(2000);
            assertFalse(t.isAlive());
        } finally {
            portao.encerrar();
        }
        AcaoS3 a = reg.acoes().get(0);
        assertEquals(t0, a.tNs());
        assertEquals(7 * S, a.esperaPortaoNs(), "espera do K1");
        assertEquals(7 * S + 3 * MS, a.tFimNs() - a.tNs());
        assertEquals(0, portao.emVoo(), "a aplicacao devolveu a permissao");
    }

    @Test
    @DisplayName("K1 na aplicacao: a permissao e devolvida mesmo se a chamada lanca")
    void permissaoDevolvidaComExcecao() {
        Mundo m = new Mundo(Perfil.NORMAL);
        AtomicInteger dentro = new AtomicInteger(-1);
        try {
            m.app.executar("s3-u-000", "Validate", "sid", 2, () -> {
                dentro.set(m.portao.emVoo());
                throw new IllegalStateException("falha da chamada");
            });
        } catch (IllegalStateException esperado) {
            // propagou
        }
        assertEquals(1, dentro.get(), "a chamada roda com a permissao");
        assertEquals(0, m.portao.emVoo());
    }

    @Test
    @DisplayName("K4: o portao implementa a formula com o mesmo mix64 do MisturaV3")
    void formulaMix64() {
        ParametrosKnobs k = ParametrosKnobs.padrao();
        PerfilAtual perfil = new PerfilAtual();
        perfil.definir(Perfil.CONSERVADOR);
        PortaoDeEmissao p = new PortaoDeEmissao(perfil, k, 1, () -> 0L, ns -> { }, e -> { }, SEMENTE);
        List<Long> difs = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            String ator = "s3-u-" + i;
            long inicio = 77L * i - 1_000_000L;
            long base = (i + 1) * 1_000L;
            int tent = i % 4;
            long v = p.esperaRetentativaNs(ator, base, tent, inicio);
            if (v != k4Esperado(k, ator, base, tent, inicio)) {
                difs.add((long) i);
            }
        }
        assertEquals(List.of(), difs);
    }
}
