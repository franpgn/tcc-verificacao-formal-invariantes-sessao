package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.CanalCanario;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * W-4 (B-26.3): adaptador Hot Rod da sonda de defasagem ({@link br.unipampa.tcc.controle.SondaDefasagem}). Um
 * {@link RemoteCacheManager} por no, com a mesma configuracao dos servidores de aplicacao fixados do S3
 * ({@link S3Clientes#configuracaoPinned}: um so servidor, {@code BASIC}, prazo {@code --hotrod-timeout-ms}),
 * sobre o <i>cache</i> {@link PerfilReplicacao#cacheCanario()} do perfil ({@code canario} ou
 * {@code canario-async}).
 *
 * <p>Clientes proprios, e nao os do {@link S3Clientes}: (1) o laco do controlador abre antes da carga e fecha
 * depois dela; (2) com {@code --s3-client-mode topology} o S3 nao tem cliente fixado por no; (3) a sonda nao
 * disputa a fila de conexoes dos servidores de aplicacao (nem atrasa a carga, nem e atrasada por ela). O custo
 * fica contado a parte ({@code defasagem_escritas}, {@code defasagem_leituras}); nada passa por
 * {@link SessionOps}, entao nada vai ao CSV de eventos, ao auditor nem a varredura.</p>
 *
 * <p>Rotulos: o no {@code i} e o {@code i}-esimo de {@code --servers} (Hot Rod) e o {@code i}-esimo de
 * {@code --controller-admin} (REST); o rotulo e o {@code host:porta} da REST, o mesmo da sonda de topologia e do
 * atuador ({@code RotulosDosNosTest}). {@link #conferirCorrespondencia} recusa listas em que os dois nao sao o
 * mesmo {@code host:porta}: no Infinispan 15 Hot Rod e REST estao no mesmo <i>endpoint</i>
 * ({@code cluster/infinispan-cluster.xml}, {@code <endpoint socket-binding="default">}), entao um par diferente e
 * erro de ordem ou de digitacao, e o AC-5 pararia o no errado.</p>
 *
 * <p>P-5: so Hot Rod, em memoria; nenhum processo, nenhum arquivo.</p>
 */
public final class CanalCanarioHotRod implements CanalCanario {

    /** Fatia do <i>cache</i> remoto usada pela sonda; costura de teste, como {@code SondaHotrod.Alvo}. */
    interface Alvo {
        void put(String chave, String valor);

        Object get(String chave);

        default void fechar() {
        }
    }

    private final String nomeCache;
    private final List<String> rotulos;
    private final List<Alvo> alvos;
    private final AtomicBoolean fechado = new AtomicBoolean();

    CanalCanarioHotRod(String nomeCache, List<String> rotulos, List<Alvo> alvos) {
        this.nomeCache = Objects.requireNonNull(nomeCache, "nomeCache");
        Objects.requireNonNull(rotulos, "rotulos");
        Objects.requireNonNull(alvos, "alvos");
        if (rotulos.size() < 2 || rotulos.size() != alvos.size()) {
            throw new IllegalArgumentException("esperava >= 2 nos e um alvo por rotulo: " + rotulos.size() + " rotulos, "
                    + alvos.size() + " alvos");
        }
        if (new HashSet<>(rotulos).size() != rotulos.size()) {
            throw new IllegalArgumentException("rotulos repetidos: " + rotulos);
        }
        this.rotulos = List.copyOf(rotulos);
        this.alvos = List.copyOf(alvos);
    }

    /** Rotulo canonico de cada no: a URL de {@code --controller-admin} sem o esquema ({@code host:porta}). */
    static List<String> rotulos(List<String> urlsAdmin) {
        List<String> r = new ArrayList<>(urlsAdmin.size());
        for (String u : urlsAdmin) {
            r.add(AtuadorRestInfinispan.rotulo(u.endsWith("/") ? u.substring(0, u.length() - 1) : u));
        }
        return List.copyOf(r);
    }

    /**
     * Confere que o {@code i}-esimo item de {@code --servers} e o {@code i}-esimo de {@code --controller-admin} sao
     * o mesmo {@code host:porta}.
     *
     * @param urlsAdmin  {@code http://host:porta}, ja normalizadas ({@code LigacaoControlador.urlsAdmin})
     * @param servidores o valor de {@code --servers}
     * @throws IllegalArgumentException com o primeiro par divergente
     */
    static void conferirCorrespondencia(List<String> urlsAdmin, String servidores) {
        List<String> rot = rotulos(urlsAdmin);
        List<S3Clientes.HostPort> hp = S3Clientes.servidores(servidores);
        if (rot.size() != hp.size()) {
            throw new IllegalArgumentException("--controller-admin tem " + rot.size() + " nos e --servers tem " + hp.size());
        }
        for (int i = 0; i < rot.size(); i++) {
            if (!rot.get(i).equals(hp.get(i).toString())) {
                throw new IllegalArgumentException("sonda de defasagem: o no " + (i + 1) + " de --servers ("
                        + hp.get(i) + ") e o de --controller-admin (" + rot.get(i) + ") nao sao o mesmo host:porta;"
                        + " o S_lag escreve pelo Hot Rod do no i e o rotula pela REST do no i (mesmo endpoint no"
                        + " Infinispan 15)");
            }
        }
    }

    /**
     * Abre um cliente fixado por no, sobre o <i>cache</i> canario do perfil, e confere que o <i>cache</i> existe.
     *
     * @param urlsAdmin as URLs de {@code --controller-admin}, ja normalizadas
     * @throws IllegalArgumentException listas que nao correspondem ({@link #conferirCorrespondencia})
     * @throws IllegalStateException    o <i>cache</i> nao existe num no (criar com {@code scripts/cria-caches.sh})
     */
    static CanalCanarioHotRod abrir(Cli cli, List<String> urlsAdmin) {
        conferirCorrespondencia(urlsAdmin, cli.servidores);
        String nomeCache = cli.replicacao.cacheCanario();
        List<String> rot = rotulos(urlsAdmin);
        List<S3Clientes.HostPort> nos = S3Clientes.servidores(cli.servidores);
        List<Alvo> alvos = new ArrayList<>(nos.size());
        try {
            for (S3Clientes.HostPort no : nos) {
                RemoteCacheManager rcm = new RemoteCacheManager(
                        S3Clientes.configuracaoPinned(no, cli.usuario, cli.senha, cli.s3));
                RemoteCache<String, Object> cache;
                try {
                    cache = rcm.getCache(nomeCache);
                } catch (RuntimeException e) {
                    rcm.stop();
                    throw e;
                }
                if (cache == null) {
                    rcm.stop();
                    throw new IllegalStateException("cache " + nomeCache + " inexistente em " + no
                            + " (criar pela REST com scripts/cria-caches.sh a partir de cluster/caches/" + nomeCache
                            + ".json)");
                }
                alvos.add(alvo(rcm, cache));
            }
        } catch (RuntimeException e) {
            alvos.forEach(CanalCanarioHotRod::fecharQuieto);
            throw e;
        }
        return new CanalCanarioHotRod(nomeCache, rot, alvos);
    }

    private static Alvo alvo(RemoteCacheManager rcm, RemoteCache<String, Object> cache) {
        return new Alvo() {
            @Override
            public void put(String chave, String valor) {
                cache.put(chave, valor);
            }

            @Override
            public Object get(String chave) {
                return cache.get(chave);
            }

            @Override
            public void fechar() {
                rcm.stop();
            }
        };
    }

    private static void fecharQuieto(Alvo a) {
        try {
            a.fechar();
        } catch (RuntimeException ignorada) {
            // encerramento: melhor esforco
        }
    }

    /** O <i>cache</i> usado ({@code canario} ou {@code canario-async}). */
    public String nomeCache() {
        return nomeCache;
    }

    @Override
    public int nos() {
        return rotulos.size();
    }

    @Override
    public String rotulo(int indice) {
        return rotulos.get(indice);
    }

    @Override
    public void escrever(int indice, String chave, String valor) {
        exigirAberto();
        alvos.get(indice).put(chave, valor);
    }

    @Override
    public String ler(int indice, String chave) {
        exigirAberto();
        Object v = alvos.get(indice).get(chave);
        return v == null ? null : v.toString();
    }

    private void exigirAberto() {
        if (fechado.get()) {
            throw new IllegalStateException("canal fechado");
        }
    }

    @Override
    public void close() {
        if (fechado.compareAndSet(false, true)) {
            alvos.forEach(CanalCanarioHotRod::fecharQuieto);
        }
    }
}
