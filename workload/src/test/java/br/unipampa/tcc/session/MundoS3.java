package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Fixture de teste do S3: um grid em memoria (um mapa de sessoes e um de identidades),
 * tres servidores de aplicacao (cada um com uma "visao" que pode ficar fora do ar numa janela
 * de tempo do relogio falso) e um console administrativo. Nada aqui toca rede nem dorme.
 */
final class MundoS3 {

    static final String[] NOS = {"127.0.0.1:11222", "127.0.0.1:11223", "127.0.0.1:11224"};

    final RelogioFalso relogio = new RelogioFalso(1_000_000_000_000L);
    final S3Parametros p = new S3Parametros();
    final S3Registro registro = new S3Registro();
    final FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
    final FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
    final EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
    final RastroCliente rastro = new RastroCliente();
    final InvariantAuditor auditor = new InvariantAuditor();
    final ConcurrentHashMap<String, SessionState.Motivo> invalidacoes = new ConcurrentHashMap<>();
    final List<SessionOps> apps = new ArrayList<>();
    SessionOps admin;
    ContextoS3 ctx;
    /** Janela [iniNs, fimNs) em que cada no (0..2) e o admin (3) recusa tudo. */
    final long[][] foraDoAr = new long[4][2];

    MundoS3(long semente, Consumer<S3Parametros> ajuste) {
        if (ajuste != null) {
            ajuste.accept(p);
        }
        EpochClock ancora = EpochClock.capturarAgora();
        for (int i = 0; i < 3; i++) {
            apps.add(ops(i, ancora));
        }
        admin = ops(3, ancora);
        ctx = new ContextoS3(relogio, p, registro, semente);
    }

    /** Fica fora do ar entre {@code iniSeg} e {@code fimSeg} (a partir do inicio do relogio). */
    void foraDoAr(int no, double iniSeg, double fimSeg) {
        foraDoAr[no][0] = relogio.agora + RelogioFalso.seg(iniSeg);
        foraDoAr[no][1] = relogio.agora + RelogioFalso.seg(fimSeg);
    }

    private SessionOps ops(int no, EpochClock ancora) {
        String replica = no < 3 ? NOS[no] : NOS[0];
        return new SessionOps(visao(sessoes, no), visao(identidades, no), new LatencyRegistry(), auditor,
                eventos, ancora, replica, rastro, invalidacoes);
    }

    private <V> Armazem<V> visao(Armazem<V> base, int no) {
        return new Armazem<>() {
            private void checar() {
                long t = relogio.agora;
                if (t >= foraDoAr[no][0] && t < foraDoAr[no][1] && foraDoAr[no][1] > 0) {
                    // a chamada real so devolve erro depois do timeout do cliente (2 s)
                    relogio.agora += RelogioFalso.seg(2);
                    throw new TransportException("timeout", (SocketAddress) null);
                }
            }

            @Override public V get(String chave) { checar(); return base.get(chave); }

            @Override public V put(String chave, V valor) { checar(); return base.put(chave, valor); }

            @Override public Set<Map.Entry<String, V>> entrySet() { checar(); return base.entrySet(); }
        };
    }

    S3Simulacao simulacao() {
        return new S3Simulacao(ctx, apps, admin, relogio.agora);
    }

    /** Roda a simulacao sequencial por {@code seg} segundos do relogio falso. */
    void rodar(double seg) {
        simulacao().rodarSequencial(relogio.agora + RelogioFalso.seg(seg), () -> false);
    }

    /** Roda os atores ja criados ate {@code fimNs}. */
    static void rodar(S3Simulacao s, long fimNs) {
        s.rodarSequencial(fimNs, () -> false);
    }

    List<AcaoS3> acoesDe(String ator) {
        List<AcaoS3> out = new ArrayList<>();
        for (AcaoS3 a : registro.acoes()) {
            if (a.ator().equals(ator)) {
                out.add(a);
            }
        }
        return out;
    }
}
