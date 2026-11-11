package br.unipampa.tcc.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * B-43.9: fixture dos testes de marcos do bloqueio (DT-002), dupla checagem e deduplicacao. Como o
 * {@link MundoS3Versionado} (que nao muda), mas com o mapa de marcos COMPARTILHADO entre os tres
 * servidores de aplicacao e o admin (como em {@link S3Clientes}) e com um gancho
 * {@link SessoesComGancho#depoisDoGet} que roda logo DEPOIS de o {@code get} da sessao ter lido o
 * valor (o {@link FakeVersionado} so tem gancho antes do {@code get}).
 */
final class MundoMarcos {

    static final Instant T = Instant.parse("2026-10-23T00:00:00Z");

    final FakeVersionado<SessionState> base = new FakeVersionado<>();
    final SessoesComGancho sessoes = new SessoesComGancho(base);
    final FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
    final EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
    final RastroCliente rastro = new RastroCliente();
    final InvariantAuditor auditor = new InvariantAuditor();
    final ConcurrentHashMap<String, SessionState.Motivo> invalidacoes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, MarcoBloqueio> marcos = new ConcurrentHashMap<>();
    final List<SessionOps> apps = new ArrayList<>();
    final List<LatencyRegistry> latencias = new ArrayList<>();
    final SessionOps admin;

    MundoMarcos() {
        EpochClock ancora = EpochClock.capturarAgora();
        for (int i = 0; i < 3; i++) {
            apps.add(ops(MundoS3.NOS[i], ancora));
        }
        admin = ops(MundoS3.NOS[0], ancora);
    }

    private SessionOps ops(String replica, EpochClock ancora) {
        LatencyRegistry lat = new LatencyRegistry();
        latencias.add(lat);
        return new SessionOps(sessoes, identidades, lat, auditor, eventos, ancora, replica, rastro,
                invalidacoes, marcos);
    }

    List<SessionOps> todas() {
        List<SessionOps> l = new ArrayList<>(apps);
        l.add(admin);
        return l;
    }

    /** Sessao VALID criada pelo {@code login} do servidor {@code app} (o rastro mapeia o sid). */
    String login(int app, String identidade) {
        OpResult r = apps.get(app).login(identidade, "cred");
        if (r.code() != OpResult.Code.OK_LOGIN) {
            throw new AssertionError("login nao OK: " + r);
        }
        return r.sessionId();
    }

    long soma(java.util.function.ToLongFunction<SessionOps> f) {
        long n = 0;
        for (SessionOps o : todas()) {
            n += f.applyAsLong(o);
        }
        return n;
    }

    /** Violacoes drenadas como "invariante:chave". */
    List<String> invariantes() {
        return auditor.drenar().stream().map(v -> v.invariante() + ":" + v.chave()).toList();
    }

    /** Linhas do CSV de eventos (sem cabecalho). */
    List<String> linhasCsv() throws Exception {
        Path tmp = Files.createTempFile("ev-b439", ".csv");
        try {
            eventos.flush(tmp);
            List<String> l = Files.readAllLines(tmp);
            return l.subList(1, l.size());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** {@link ArmazemVersionado} sobre o {@link FakeVersionado}, com gancho depois do {@code get}. */
    static final class SessoesComGancho implements ArmazemVersionado<SessionState> {
        final FakeVersionado<SessionState> base;
        volatile Consumer<String> depoisDoGet = k -> { };
        volatile Runnable depoisDoEntrySet = () -> { };

        SessoesComGancho(FakeVersionado<SessionState> base) {
            this.base = base;
        }

        @Override public SessionState get(String chave) {
            SessionState v = base.get(chave);
            depoisDoGet.accept(chave);
            return v;
        }

        @Override public SessionState put(String chave, SessionState valor) { return base.put(chave, valor); }

        @Override public Set<Map.Entry<String, SessionState>> entrySet() {
            Set<Map.Entry<String, SessionState>> e = base.entrySet();
            Runnable r = depoisDoEntrySet;
            depoisDoEntrySet = () -> { };
            r.run();
            return e;
        }

        @Override public Versionado<SessionState> lerComVersao(String chave) { return base.lerComVersao(chave); }

        @Override public boolean substituirSeVersao(String chave, SessionState novo, long versao) {
            return base.substituirSeVersao(chave, novo, versao);
        }
    }
}
