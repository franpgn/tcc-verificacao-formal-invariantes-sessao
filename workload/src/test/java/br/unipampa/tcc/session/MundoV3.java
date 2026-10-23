package br.unipampa.tcc.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * B-43.7: mundo de teste da mistura v3 sobre {@link FakeCaches} (sem cluster): carga de slots
 * ({@link CargaSlots}), {@link SlotsGeracao} e {@link MisturaV3} compartilhados, {@link OpsGravadas}
 * e N threads simuladas intercaladas em sequencia (como {@code MisturaV2Test}).
 */
final class MundoV3 {

    static final int U = KeyGenerator.TAMANHO_PADRAO_UNIVERSO;

    final int threads;
    final FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
    final FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
    final LatencyRegistry latency = new LatencyRegistry();
    final EventCsvWriter eventos;
    final OpsGravadas ops;
    final SlotsGeracao slots;
    final MisturaV3 mistura;

    MundoV3(int threads, boolean comCsv) throws CargaInicialException {
        this.threads = threads;
        CargaSlots.carregar(new FakeCarregavel("sessions", sessoes), U, threads,
                Instant.parse("2026-10-22T00:00:00Z"), CargaInicial.LOTE_PADRAO,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> 0L, d -> { });
        this.eventos = comCsv ? new EventCsvWriter(WarmupPolicy.always()) : null;
        this.ops = new OpsGravadas(sessoes, identidades, latency, eventos);
        this.slots = new SlotsGeracao(U, threads);
        this.mistura = new MisturaV3(slots, WarmupPolicy.always());
    }

    /** {@code operacoes} operacoes intercaladas pelas threads simuladas, sementes {@code semente + t}. */
    void rodar(Scenario cenario, int operacoes, long semente) {
        Random[] r = new Random[threads];
        KeyGenerator[] k = new KeyGenerator[threads];
        IdentidadesMistura[] ids = new IdentidadesMistura[threads];
        MisturaV3.Cliente[] c = new MisturaV3.Cliente[threads];
        for (int t = 0; t < threads; t++) {
            r[t] = new Random(semente + t);
            k[t] = new KeyGenerator(semente + t);
            ids[t] = new IdentidadesMistura(t, VersaoMistura.V3);
            c[t] = mistura.cliente(semente + t);
        }
        for (int i = 0; i < operacoes; i++) {
            int t = i % threads;
            WorkloadMain.executarOperacoes(ops, cenario, r[t], k[t], ids[t], 1, c[t]);
        }
    }

    /** Chamadas de {@code op} sobre chaves de slot ({@code sid-...}), em ordem. */
    List<OpsGravadas.Chamada> isolados(String op) {
        List<OpsGravadas.Chamada> out = new ArrayList<>();
        for (OpsGravadas.Chamada c : ops.chamadas) {
            if (c.op().equals(op) && c.arg().startsWith(KeyGenerator.PREFIXO_SID)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Numero de Logout com latencia {@code logout_noop} registrada (isolados e triade; erros incluidos). */
    long logoutNoop() {
        var h = latency.snapshot().get("logout_noop");
        return h == null ? 0 : h.getTotalCount();
    }

    static boolean invalid(OpResult r) {
        return r.code() == OpResult.Code.OK_VALIDATE && r.estado() == SessionState.Estado.INVALID;
    }
}
