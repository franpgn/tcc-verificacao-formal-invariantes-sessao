package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B-43.8: como o {@link MundoS3} (que nao muda), mas com o cache de sessoes versionado
 * ({@link FakeVersionado}), que e o que a producao usa desde B-43.8. Tres servidores de aplicacao e o
 * admin sobre o mesmo grid, o mesmo mapa de invalidacoes confirmadas, o mesmo auditor e o mesmo
 * rastro. Sem relogio falso nem janelas fora do ar: so para forcar ordens com os ganchos do duble.
 */
final class MundoS3Versionado {

    final FakeVersionado<SessionState> sessoes = new FakeVersionado<>();
    final FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
    final EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
    final RastroCliente rastro = new RastroCliente();
    final InvariantAuditor auditor = new InvariantAuditor();
    final ConcurrentHashMap<String, SessionState.Motivo> invalidacoes = new ConcurrentHashMap<>();
    final List<SessionOps> apps = new ArrayList<>();
    final SessionOps admin;

    MundoS3Versionado() {
        EpochClock ancora = EpochClock.capturarAgora();
        for (int i = 0; i < 3; i++) {
            apps.add(ops(MundoS3.NOS[i], ancora));
        }
        admin = ops(MundoS3.NOS[0], ancora);
    }

    private SessionOps ops(String replica, EpochClock ancora) {
        return new SessionOps(sessoes, identidades, new LatencyRegistry(), auditor, eventos, ancora, replica,
                rastro, invalidacoes);
    }

    long invalidacaoPerdida() {
        long n = admin.invalidacaoPerdida();
        for (SessionOps o : apps) {
            n += o.invalidacaoPerdida();
        }
        return n;
    }

    long concorrentes() {
        long n = admin.i1Concorrentes() + admin.i4Concorrentes();
        for (SessionOps o : apps) {
            n += o.i1Concorrentes() + o.i4Concorrentes();
        }
        return n;
    }
}
