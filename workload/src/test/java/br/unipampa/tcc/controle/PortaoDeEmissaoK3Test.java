package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27, K3 (adiamento de operacoes administrativas): em CONSERVADOR, Block, Unblock,
 * IncrementFailure e ResetFailures esperam a volta a NORMAL, o prazo k3AdiaMaxNs ou o encerramento;
 * Login, Validate e Logout nunca esperam. Relogio falso (a sondagem avanca o tempo).
 */
class PortaoDeEmissaoK3Test {

    static final long MS = 1_000_000L;
    static final long S = 1_000_000_000L;

    /** Relogio falso: dormir so avanca o tempo e dispara as acoes agendadas (com limite de passos). */
    static final class TempoFalso implements FonteDeTempo, Dormidor {
        long agora = 1_000 * S;
        int sonos;
        final TreeMap<Long, Runnable> agenda = new TreeMap<>();

        @Override
        public long agoraNs() {
            return agora;
        }

        @Override
        public void dormirNs(long ns) {
            if (++sonos > 100_000) {
                throw new AssertionError("espera sem fim no relogio falso (" + sonos + " sondagens)");
            }
            agora += Math.max(0, ns);
            while (!agenda.isEmpty() && agenda.firstKey() <= agora) {
                agenda.pollFirstEntry().getValue().run();
            }
        }

        void em(long instante, Runnable r) {
            agenda.put(instante, r);
        }
    }

    private final PerfilAtual perfil = new PerfilAtual();
    private final TempoFalso tempo = new TempoFalso();
    private final List<EventoK3> eventos = new CopyOnWriteArrayList<>();

    private PortaoDeEmissao portao(ParametrosKnobs k) {
        return new PortaoDeEmissao(perfil, k, 4, tempo, tempo, eventos::add, 9L);
    }

    @Test
    @DisplayName("K3a: Block do admin espera; a volta a NORMAL no instante t o libera ate k3SondagemNs depois de t")
    void liberaNaSaida() throws Exception {
        ParametrosKnobs k = ParametrosKnobs.padrao();
        PortaoDeEmissao p = portao(k);
        perfil.definir(Perfil.CONSERVADOR);
        long t0 = tempo.agora;
        long t = t0 + 1_250 * MS;
        tempo.em(t, () -> perfil.definir(Perfil.NORMAL));
        long esperou = p.aguardarAdministrativa("admin", "Block", "s3-u-001");
        assertTrue(tempo.agora >= t, "liberado antes da volta a NORMAL");
        assertTrue(tempo.agora - t <= k.k3SondagemNs(), "liberado " + (tempo.agora - t) + " ns depois de t");
        assertEquals(tempo.agora - t0, esperou);
        assertEquals(List.of(new EventoK3("admin", "Block", "s3-u-001", t0, tempo.agora, EventoK3.SAIDA_CONSERVADOR)),
                eventos);
        assertEquals(1, p.adiamentosK3());
    }

    @Test
    @DisplayName("K3b: CONSERVADOR permanente => libera em k3AdiaMaxNs (+- k3SondagemNs), motivo prazo")
    void prazo() throws Exception {
        ParametrosKnobs k = ParametrosKnobs.padrao();
        PortaoDeEmissao p = portao(k);
        perfil.definir(Perfil.CONSERVADOR);
        long t0 = tempo.agora;
        long esperou = p.aguardarAdministrativa("s3-l-003", "IncrementFailure", "s3-l-003");
        assertTrue(Math.abs(esperou - k.k3AdiaMaxNs()) <= k.k3SondagemNs(), "esperou " + esperou);
        assertEquals(1, eventos.size());
        EventoK3 e = eventos.get(0);
        assertEquals(EventoK3.PRAZO, e.motivo());
        assertEquals(t0, e.adiadoEmNs());
        assertEquals(t0 + esperou, e.liberadoEmNs());
        // prazo curto e sondagem que nao divide o prazo
        eventos.clear();
        ParametrosKnobs curto = new ParametrosKnobs("t", 0.5, 0, 1.5, 250 * MS, 100 * MS, 2.0, 0.5, null);
        long esperouCurto = portao(curto).aguardarAdministrativa("admin", "Unblock", "x");
        assertTrue(esperouCurto >= 150 * MS && esperouCurto <= 350 * MS, "esperou " + esperouCurto);
        assertEquals(EventoK3.PRAZO, eventos.get(0).motivo());
    }

    @Test
    @DisplayName("K3e: Login/Validate/Logout nunca esperam; Block/Unblock (inclusive do suporte)/IncrementFailure/ResetFailures esperam so em CONSERVADOR")
    void escopo() throws Exception {
        ParametrosKnobs k = ParametrosKnobs.padrao();
        PortaoDeEmissao p = portao(k);
        for (String op : List.of("Block", "Unblock", "IncrementFailure", "ResetFailures", "Login", "Validate", "Logout")) {
            assertEquals(0L, p.aguardarAdministrativa("admin", op, "x"), "NORMAL nao adia " + op);
        }
        assertTrue(eventos.isEmpty());
        perfil.definir(Perfil.CONSERVADOR);
        long antes = tempo.agora;
        for (String op : List.of("Login", "Validate", "Logout")) {
            assertEquals(0L, p.aguardarAdministrativa("s3-u-000", op, "x"), op + " nao pode esperar");
            assertEquals(0L, p.aguardarAdministrativa("s3-l-000", op, "x"), op + " nao pode esperar");
        }
        assertEquals(antes, tempo.agora, "nenhuma sondagem para Login/Validate/Logout");
        assertTrue(eventos.isEmpty());
        assertTrue(p.aguardarAdministrativa("suporte", "Unblock", "s3-l-002") > 0, "Unblock do suporte espera");
        assertTrue(p.aguardarAdministrativa("admin", "Block", "s3-u-000") > 0);
        assertTrue(p.aguardarAdministrativa("s3-l-001", "IncrementFailure", "s3-l-001") > 0);
        assertTrue(p.aguardarAdministrativa("s3-l-001", "ResetFailures", "s3-l-001") > 0);
        assertEquals(4, eventos.size());
        assertEquals(4, p.adiamentosK3());
    }

    @Test
    @DisplayName("K3f: encerrar() libera os adiados (motivo encerramento), e as chamadas seguintes nao esperam; idempotente")
    void encerrar() throws Exception {
        ParametrosKnobs k = new ParametrosKnobs("t", 0.5, 0, 1.5, 60 * S, 5 * MS, 2.0, 0.5, null);
        List<EventoK3> evs = new CopyOnWriteArrayList<>();
        PortaoDeEmissao p = new PortaoDeEmissao(perfil, k, 1, System::nanoTime, Dormidor.real(), evs::add, 1L);
        perfil.definir(Perfil.CONSERVADOR);
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final String alvo = "s3-u-00" + i;
            ts.add(PortaoDeEmissaoK1Test.daemon(() -> {
                try {
                    p.aguardarAdministrativa("admin", "Block", alvo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        // K1 tambem: uma permissao ocupada e uma thread presa
        p.entrar();
        Thread presaK1 = PortaoDeEmissaoK1Test.daemon(() -> {
            try {
                p.entrar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            Thread.sleep(100);
            assertTrue(ts.stream().allMatch(Thread::isAlive), "os 3 Block ficam adiados");
            assertTrue(PortaoDeEmissaoK1Test.ate(() -> presaK1.getState() == Thread.State.WAITING, 5000));
            p.encerrar();
            p.encerrar();
            for (Thread t : ts) {
                t.join(1000);
                assertFalse(t.isAlive(), "adiado nao foi liberado pelo encerramento");
            }
            presaK1.join(1000);
            assertFalse(presaK1.isAlive(), "K1 nao foi liberado pelo encerramento");
            assertEquals(3, evs.size());
            assertTrue(evs.stream().allMatch(e -> e.motivo().equals(EventoK3.ENCERRAMENTO)), evs.toString());
            long t0 = System.nanoTime();
            assertEquals(0L, p.aguardarAdministrativa("admin", "Block", "s3-u-009"));
            assertEquals(0L, p.entrar());
            assertTrue(System.nanoTime() - t0 < TimeUnit.MILLISECONDS.toNanos(500));
            assertEquals(3, evs.size(), "depois de encerrar nao ha adiamento");
        } finally {
            perfil.definir(Perfil.NORMAL);
            p.encerrar();
        }
    }

    @Test
    @DisplayName("K3: a espera e sensivel a interrupcao do Dormidor real")
    void interrupcao() throws Exception {
        ParametrosKnobs k = new ParametrosKnobs("t", 0.5, 0, 1.5, 60 * S, 20 * MS, 2.0, 0.5, null);
        PortaoDeEmissao p = new PortaoDeEmissao(perfil, k, 1, System::nanoTime, Dormidor.real(), e -> { }, 1L);
        perfil.definir(Perfil.CONSERVADOR);
        boolean[] interrompida = {false};
        Thread t = PortaoDeEmissaoK1Test.daemon(() -> {
            try {
                p.aguardarAdministrativa("admin", "Block", "x");
            } catch (InterruptedException e) {
                interrompida[0] = true;
            }
        });
        try {
            Thread.sleep(50);
            t.interrupt();
            t.join(1000);
            assertFalse(t.isAlive());
            assertTrue(interrompida[0], "InterruptedException propagada");
        } finally {
            p.encerrar();
        }
    }
}
