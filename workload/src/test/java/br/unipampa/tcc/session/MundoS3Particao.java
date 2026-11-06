package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * W-10: fixture do S3 com um grid que <b>particiona</b> (o mecanismo M1 do diagnostico de 06/11). Tres
 * servidores de aplicacao e o admin, como no {@link MundoS3}, mas o armazem de sessoes tem duas copias
 * durante a janela de particao {@code [ini, fim)} do relogio falso:
 *
 * <ul>
 *   <li>antes da janela ha uma copia so (a da maioria), lida e escrita por todos;</li>
 *   <li>no inicio da janela a copia da minoria nasce igual a da maioria; dai em diante o no isolado le e
 *       escreve so a copia da minoria (a replica assincrona para o backup se perde no corte) e os outros
 *       nos leem e escrevem so a da maioria, que guarda o backup {@code VALID};</li>
 *   <li>na fusao (primeira chamada em {@code t >= fim}) a maioria vence nas chaves que as duas copias
 *       tem e as chaves que so a minoria tem entram (como {@code PREFERRED_NON_NULL} com a maioria
 *       preferida): um logout confirmado so pelo no isolado se perde e a sessao volta {@code VALID}.</li>
 * </ul>
 *
 * <p>O cache de identidades nao particiona (o S3 so precisa de sessoes aqui). Um no pode ficar fora do
 * ar ({@link #foraDoAr}): cada chamada dele custa 2 s do relogio falso e lanca, como o timeout do
 * cliente. Nada toca rede nem dorme.</p>
 */
final class MundoS3Particao {

    final RelogioFalso relogio = new RelogioFalso(1_000_000_000_000L);
    final S3Parametros p = new S3Parametros();
    final S3Registro registro = new S3Registro();
    final FakeCaches.Cache<IdentityState> identidades = new FakeCaches.Cache<>();
    final EventCsvWriter eventos;
    final RastroCliente rastro = new RastroCliente();
    final InvariantAuditor auditor = new InvariantAuditor();
    final ConcurrentHashMap<String, SessionState.Motivo> invalidacoes = new ConcurrentHashMap<>();
    final List<SessionOps> apps = new ArrayList<>();
    final List<LatencyRegistry> latencias = new ArrayList<>();
    final EpochClock ancora = EpochClock.capturarAgora();
    SessionOps admin;
    ContextoS3 ctx;

    /** Copia da maioria (a unica fora da janela). */
    final Map<String, SessionState> maioria = new LinkedHashMap<>();
    /** Copia da minoria (so dentro da janela). */
    final Map<String, SessionState> minoria = new LinkedHashMap<>();
    int isolado = -1;
    long iniNs = Long.MAX_VALUE;
    long fimNs = Long.MAX_VALUE;
    private boolean particionado;
    private boolean fundido;
    /** Janela [ini, fim) em que cada no (0..2) recusa tudo. */
    final long[][] foraDoAr = new long[3][2];

    MundoS3Particao(long semente, Consumer<S3Parametros> ajuste) {
        this(semente, ajuste, WarmupPolicy.always());
    }

    MundoS3Particao(long semente, Consumer<S3Parametros> ajuste, WarmupPolicy politica) {
        if (ajuste != null) {
            ajuste.accept(p);
        }
        eventos = new EventCsvWriter(politica);
        for (int i = 0; i < 3; i++) {
            LatencyRegistry l = new LatencyRegistry();
            latencias.add(l);
            apps.add(new SessionOps(visaoSessoes(i), visaoIdentidades(i), l, auditor, eventos, ancora,
                    MundoS3.NOS[i], rastro, invalidacoes));
        }
        admin = new SessionOps(visaoSessoes(0), visaoIdentidades(0), new LatencyRegistry(), auditor, eventos,
                ancora, MundoS3.NOS[0], rastro, invalidacoes);
        ctx = new ContextoS3(relogio, p, registro, semente);
    }

    /** Particiona {@code no} entre {@code iniSeg} e {@code fimSeg} (a partir do inicio do relogio). */
    void particionar(int no, double iniSeg, double fimSeg) {
        isolado = no;
        iniNs = relogio.agora + RelogioFalso.seg(iniSeg);
        fimNs = relogio.agora + RelogioFalso.seg(fimSeg);
    }

    void foraDoAr(int no, double iniSeg, double fimSeg) {
        foraDoAr[no][0] = relogio.agora + RelogioFalso.seg(iniSeg);
        foraDoAr[no][1] = relogio.agora + RelogioFalso.seg(fimSeg);
    }

    private void atualizar() {
        long t = relogio.agora;
        if (!particionado && !fundido && t >= iniNs && t < fimNs) {
            minoria.clear();
            minoria.putAll(maioria);
            particionado = true;
        }
        if (particionado && t >= fimNs) {
            for (Map.Entry<String, SessionState> e : minoria.entrySet()) {
                maioria.putIfAbsent(e.getKey(), e.getValue());
            }
            minoria.clear();
            particionado = false;
            fundido = true;
        }
    }

    private void checar(int no) {
        long t = relogio.agora;
        if (foraDoAr[no][1] > 0 && t >= foraDoAr[no][0] && t < foraDoAr[no][1]) {
            relogio.agora += RelogioFalso.seg(2);
            throw new TransportException("timeout", (SocketAddress) null);
        }
    }

    private Map<String, SessionState> copiaDe(int no) {
        atualizar();
        return particionado && no == isolado ? minoria : maioria;
    }

    private Armazem<SessionState> visaoSessoes(int no) {
        return new Armazem<>() {
            @Override public SessionState get(String chave) { checar(no); return copiaDe(no).get(chave); }

            @Override public SessionState put(String chave, SessionState valor) {
                checar(no);
                return copiaDe(no).put(chave, valor);
            }

            @Override public Set<Map.Entry<String, SessionState>> entrySet() {
                checar(no);
                return new LinkedHashSet<>(new ArrayList<>(new HashMap<>(copiaDe(no)).entrySet()));
            }
        };
    }

    private Armazem<IdentityState> visaoIdentidades(int no) {
        return new Armazem<>() {
            @Override public IdentityState get(String chave) { checar(no); return identidades.get(chave); }

            @Override public IdentityState put(String chave, IdentityState valor) {
                checar(no);
                return identidades.put(chave, valor);
            }

            @Override public Set<Map.Entry<String, IdentityState>> entrySet() {
                checar(no);
                return identidades.entrySet();
            }
        };
    }

    S3Simulacao simulacao() {
        return new S3Simulacao(ctx, apps, admin, relogio.agora);
    }

    /** Sessao VALID nova, gravada na copia vigente da maioria (fora da carga). */
    String sessaoValida(String sid, String identidade) {
        atualizar();
        SessionState s = SessionState.criar(sid, identidade, java.time.Instant.now());
        maioria.put(sid, s);
        if (particionado) {
            minoria.put(sid, s);
        }
        return sid;
    }
}
