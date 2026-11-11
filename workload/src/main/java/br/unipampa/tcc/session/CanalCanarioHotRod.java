package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.CanalCanario;
import org.infinispan.client.hotrod.DataFormat;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * W-4 (B-26.3): adaptador Hot Rod da sonda de defasagem ({@link br.unipampa.tcc.controle.SondaDefasagem}). Um
 * {@link RemoteCacheManager} por no, com a mesma configuracao dos servidores de aplicacao fixados do S3
 * ({@link S3Clientes#configuracaoPinned}: um so servidor, {@code BASIC}, prazo {@code --hotrod-timeout-ms}),
 * sobre o <i>cache</i> {@link PerfilReplicacao#cacheCanario()} do perfil ({@code canario} ou
 * {@code canario-async}).
 *
 * <p>Clientes proprios, e nao os do {@link S3Clientes} (aprovado pelo coordenador em 05/11): (1) o laco do
 * controlador abre antes da carga e fecha depois dela; (2) com {@code --s3-client-mode topology} o S3 nao tem cliente
 * fixado por no; (3) a sonda nao disputa a fila de conexoes dos servidores de aplicacao. O custo fica contado a parte
 * ({@code defasagem_escritas}, {@code defasagem_leituras}); nada passa por {@link SessionOps}, entao nada vai ao CSV
 * de eventos, ao auditor nem a varredura.</p>
 *
 * <p>Rotulos: o no {@code i} e o {@code i}-esimo de {@code --servers} (Hot Rod) e o {@code i}-esimo de
 * {@code --controller-admin} (REST); o rotulo e o {@code host:porta} da REST, o mesmo da sonda de topologia e do
 * atuador ({@code RotulosDosNosTest}). {@link #conferirCorrespondencia} recusa listas em que os dois nao sao o
 * mesmo {@code host:porta}: no Infinispan 15 Hot Rod e REST estao no mesmo <i>endpoint</i>
 * ({@code cluster/infinispan-cluster.xml}, {@code <endpoint socket-binding="default">}), entao um par diferente e
 * erro de ordem ou de digitacao, e o AC-5 pararia o no errado.</p>
 *
 * <p><b>Chave de cada no (P-44, decisao do coordenador de 05/11).</b> Na abertura, um cliente topology-aware
 * ({@link S3Clientes#configuracaoTopology}, todos os nos) recebe do servidor o hash consistente do cache canario, e
 * para cada no X a busca ({@link #escolherChave}) testa as candidatas {@code lag:<X>}, {@code lag:<X>#1},
 * {@code lag:<X>#2}... ate achar uma cujo dono primario seja o proprio X. O dono vem do mesmo calculo que o cliente
 * usa para rotear a operacao ({@code ChannelFactory.getHashAwareServer} sobre os bytes da chave do
 * {@code DataFormat} do cache). Por que: sob o corte de um no C, o par entre os dois nos da maioria so falha se o
 * primario da chave do escritor for C (a escrita nao chega a nenhuma replica); com o primario no proprio escritor, ele
 * grava localmente e a leitura do outro no a busca nele, e o empate no teto que tirava o suspeito deixa de existir. O
 * cliente topology-aware e fechado logo depois da escolha (sem custo durante a execucao). Sem topologia, com o no
 * fora da topologia anunciada ou sem achar no limite, a abertura <b>falha</b> ({@link #exigirPrimario}, V-W4 R1),
 * em toda execucao com o bloco {@code defasagem}; a escolha fica registrada em {@code defasagem_chaves}. A escolha vale
 * para a topologia da abertura: cada execucao comeca com o
 * <i>cluster</i> recriado, e a falha (um corte) parte dessa topologia.</p>
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

    /**
     * Resultado da escolha da chave de um no.
     *
     * @param chave      a chave usada
     * @param primario   a chave tem o proprio no como dono primario (na topologia da abertura)
     * @param tentativas candidatas testadas
     * @param motivo     {@code primario}, {@code sem-topologia}, {@code nao-encontrada}, {@code nao-escolhida} ou
     *                   {@code falha:<Excecao>}
     */
    record Chave(String chave, boolean primario, int tentativas, String motivo) {
        Chave {
            Objects.requireNonNull(chave, "chave");
            Objects.requireNonNull(motivo, "motivo");
            if (!chave.startsWith(CanalCanario.PREFIXO_CHAVE)) {
                throw new IllegalArgumentException("chave sem o prefixo " + CanalCanario.PREFIXO_CHAVE + ": " + chave);
            }
        }
    }

    /**
     * Limite da busca de candidatas por no. Seguranca contra laco sem fim (no fora da topologia), nao limiar: as
     * candidatas caem nos 64 segmentos (T4) de forma praticamente uniforme, entao um no que seja dono primario de ao
     * menos um segmento fica sem chave com chance de no maximo (63/64)^10000 &lt; 1e-60. Cada candidata custa uma
     * serializacao e um hash em memoria.
     */
    static final int LIMITE_BUSCA = 10_000;
    static final String SEPARADOR_CANDIDATA = "#";

    private final String nomeCache;
    private final List<String> rotulos;
    private final List<Alvo> alvos;
    private final List<Chave> chaves;
    private final AtomicBoolean fechado = new AtomicBoolean();

    /** Com a chave simples em todos os nos (sem escolha). */
    CanalCanarioHotRod(String nomeCache, List<String> rotulos, List<Alvo> alvos) {
        this(nomeCache, rotulos, alvos, chavesSimples(Objects.requireNonNull(rotulos, "rotulos"), "nao-escolhida"));
    }

    CanalCanarioHotRod(String nomeCache, List<String> rotulos, List<Alvo> alvos, List<Chave> chaves) {
        this.nomeCache = Objects.requireNonNull(nomeCache, "nomeCache");
        Objects.requireNonNull(rotulos, "rotulos");
        Objects.requireNonNull(alvos, "alvos");
        Objects.requireNonNull(chaves, "chaves");
        if (rotulos.size() < 2 || rotulos.size() != alvos.size() || rotulos.size() != chaves.size()) {
            throw new IllegalArgumentException("esperava >= 2 nos, um alvo e uma chave por rotulo: " + rotulos.size()
                    + " rotulos, " + alvos.size() + " alvos, " + chaves.size() + " chaves");
        }
        if (new HashSet<>(rotulos).size() != rotulos.size()) {
            throw new IllegalArgumentException("rotulos repetidos: " + rotulos);
        }
        this.rotulos = List.copyOf(rotulos);
        this.alvos = List.copyOf(alvos);
        this.chaves = List.copyOf(chaves);
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

    // ------------------------------------------------------------------ escolha da chave (P-44)

    /**
     * Busca, para o no {@code rotulo}, a primeira candidata ({@code lag:<rotulo>}, depois
     * {@code lag:<rotulo>#1}, {@code #2}...) cujo dono primario seja o proprio no (funcao pura).
     *
     * @param primarioDe {@code host:porta} do dono primario de uma chave, ou {@code null} sem topologia
     * @return a chave; sem topologia ou sem achar ate {@link #LIMITE_BUSCA}, a chave simples com {@code primario}
     *         falso
     */
    static Chave escolherChave(String rotulo, Function<String, String> primarioDe) {
        String simples = PREFIXO_CHAVE + rotulo;
        for (int j = 0; j < LIMITE_BUSCA; j++) {
            String candidata = j == 0 ? simples : simples + SEPARADOR_CANDIDATA + j;
            String dono = primarioDe.apply(candidata);
            if (dono == null) {
                return new Chave(simples, false, j + 1, "sem-topologia");
            }
            if (dono.equals(rotulo)) {
                return new Chave(candidata, true, j + 1, "primario");
            }
        }
        return new Chave(simples, false, LIMITE_BUSCA, "nao-encontrada");
    }

    /** {@link #escolherChave} para cada no, na ordem dos rotulos. */
    static List<Chave> escolherChaves(List<String> rotulos, Function<String, String> primarioDe) {
        List<Chave> l = new ArrayList<>(rotulos.size());
        for (String r : rotulos) {
            l.add(escolherChave(r, primarioDe));
        }
        return List.copyOf(l);
    }

    /**
     * Como {@link #escolherChaves(List, Function)}, sabendo quais {@code host:porta} a topologia anuncia: um no fora
     * dela (por exemplo, {@code --servers} com nome e a topologia com IP) fica com a chave simples e o motivo
     * {@code fora-da-topologia}, sem busca; topologia vazia da {@code sem-topologia} a todos (funcao pura).
     */
    static List<Chave> escolherChaves(List<String> rotulos, java.util.Set<String> naTopologia,
                                      Function<String, String> primarioDe) {
        if (naTopologia.isEmpty()) {
            return chavesSimples(rotulos, "sem-topologia");
        }
        List<Chave> l = new ArrayList<>(rotulos.size());
        for (String r : rotulos) {
            l.add(naTopologia.contains(r) ? escolherChave(r, primarioDe)
                    : new Chave(PREFIXO_CHAVE + r, false, 0, "fora-da-topologia"));
        }
        return List.copyOf(l);
    }

    /**
     * V-W4 R1 (decisao do coordenador em nome do Gestor, 05/11): toda execucao com o bloco {@code defasagem} exige
     * que cada no tenha uma chave com o proprio no como dono primario. O recuo para a chave simples devolveria o risco
     * do P-44 sem que ninguem visse, e na calibracao misturaria duas distribuicoes de lag no quantil do
     * {@code lagIn}. Vale nos dois bracos, para a exclusao nao depender do braco: a execucao vai como de
     * infraestrutura (DT-014) e e refeita (funcao pura).
     *
     * @throws IllegalStateException com o rotulo e o motivo de cada no sem primario
     * @throws IllegalArgumentException listas de tamanhos diferentes
     */
    static void exigirPrimario(List<String> rotulos, List<Chave> chaves) {
        if (rotulos.size() != chaves.size()) {
            throw new IllegalArgumentException("rotulos e chaves de tamanhos diferentes: " + rotulos.size() + " e "
                    + chaves.size());
        }
        List<String> sem = new ArrayList<>();
        for (int i = 0; i < chaves.size(); i++) {
            if (!chaves.get(i).primario()) {
                sem.add(rotulos.get(i) + " (" + chaves.get(i).motivo() + ")");
            }
        }
        if (!sem.isEmpty()) {
            throw new IllegalStateException("sonda de defasagem: sem chave com dono primario no proprio no em " + sem
                    + "; o recuo para a chave simples e fatal (V-W4 R1, P-44). Conferir se --servers e"
                    + " --controller-admin usam o host:porta que o servidor anuncia (external-host)");
        }
    }

    /**
     * Monta o canal com as chaves escolhidas e exige o primario em cada no ({@link #exigirPrimario}); se faltar,
     * fecha os clientes antes de lancar.
     */
    static CanalCanarioHotRod montar(String nomeCache, List<String> rotulos, List<Alvo> alvos, List<Chave> chaves) {
        try {
            exigirPrimario(rotulos, chaves);
        } catch (RuntimeException e) {
            alvos.forEach(CanalCanarioHotRod::fecharQuieto);
            throw e;
        }
        return new CanalCanarioHotRod(nomeCache, rotulos, alvos, chaves);
    }

    /** A chave simples em todos os nos, com o motivo dado. */
    static List<Chave> chavesSimples(List<String> rotulos, String motivo) {
        List<Chave> l = new ArrayList<>(rotulos.size());
        for (String r : rotulos) {
            l.add(new Chave(PREFIXO_CHAVE + r, false, 0, motivo));
        }
        return List.copyOf(l);
    }

    /** {@code host:porta} de um endereco do servidor; {@code null} se nulo ou de outro tipo. */
    static String hostPorta(SocketAddress a) {
        if (a instanceof InetSocketAddress i) {
            return i.getHostString() + ":" + i.getPort();
        }
        return null;
    }

    /**
     * Escolhe as chaves com o hash consistente de um cliente topology-aware sobre o cache canario, fechado logo
     * depois. Qualquer falha (cache sem topologia, servidor anunciado inalcancavel) deixa a chave simples com o
     * motivo {@code falha:<Excecao>}, e a abertura recusa ({@link #exigirPrimario}, V-W4 R1).
     */
    static List<Chave> escolherPelaTopologia(Cli cli, String nomeCache, List<S3Clientes.HostPort> nos,
                                             List<String> rotulos) {
        RemoteCacheManager rcm = null;
        try {
            rcm = new RemoteCacheManager(S3Clientes.configuracaoTopology(nos, cli.usuario, cli.senha, cli.s3));
            RemoteCache<String, Object> cache = abrirCache(rcm, nomeCache, nos.toString());
            cache.containsKey(PREFIXO_CHAVE + "topologia"); // a primeira resposta traz o hash consistente do cache
            DataFormat formato = cache.getDataFormat();
            ChannelFactory canais = rcm.getChannelFactory();
            byte[] nome = nomeCache.getBytes(StandardCharsets.UTF_8);
            java.util.Set<String> naTopologia = new HashSet<>();
            for (Object a : canais.getServers(nome)) {
                String hp = a instanceof SocketAddress s ? hostPorta(s) : null;
                if (hp != null) {
                    naTopologia.add(hp);
                }
            }
            // o mesmo objeto de roteamento das operacoes por chave do cliente: os bytes da chave (ou a chave, se o
            // servidor guardar objetos)
            return escolherChaves(rotulos, naTopologia, k -> hostPorta(canais.getHashAwareServer(
                    formato.isObjectStorage() ? k : formato.keyToBytes(k), nome)));
        } catch (RuntimeException e) {
            String s = e.getClass().getSimpleName();
            return chavesSimples(rotulos, "falha:" + (s.isEmpty() ? e.getClass().getName() : s));
        } finally {
            if (rcm != null) {
                try {
                    rcm.stop();
                } catch (RuntimeException ignorada) {
                    // encerramento: melhor esforco
                }
            }
        }
    }

    /** O cache canario do perfil num cliente; recusa cache inexistente. */
    private static RemoteCache<String, Object> abrirCache(RemoteCacheManager rcm, String nomeCache, String onde) {
        RemoteCache<String, Object> cache = rcm.getCache(nomeCache);
        if (cache == null) {
            throw new IllegalStateException("cache " + nomeCache + " inexistente em " + onde
                    + " (criar pela REST com scripts/cria-caches.sh a partir de cluster/caches/" + nomeCache + ".json)");
        }
        return cache;
    }

    // ------------------------------------------------------------------ abertura

    /**
     * Abre um cliente fixado por no, sobre o <i>cache</i> canario do perfil, confere que o <i>cache</i> existe e
     * escolhe a chave de cada no ({@link #escolherPelaTopologia}).
     *
     * @param urlsAdmin as URLs de {@code --controller-admin}, ja normalizadas
     * @throws IllegalArgumentException listas que nao correspondem ({@link #conferirCorrespondencia})
     * @throws IllegalStateException    o <i>cache</i> nao existe num no (criar com {@code scripts/cria-caches.sh})
     * @throws IllegalStateException    algum no sem chave com dono primario no proprio no ({@link #exigirPrimario});
     *                                  os clientes ja abertos sao fechados
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
                    cache = abrirCache(rcm, nomeCache, no.toString());
                } catch (RuntimeException e) {
                    rcm.stop();
                    throw e;
                }
                alvos.add(alvo(rcm, cache));
            }
        } catch (RuntimeException e) {
            alvos.forEach(CanalCanarioHotRod::fecharQuieto);
            throw e;
        }
        return montar(nomeCache, rot, alvos, escolherPelaTopologia(cli, nomeCache, nos, rot));
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

    // ------------------------------------------------------------------ canal

    /** O <i>cache</i> usado ({@code canario} ou {@code canario-async}). */
    public String nomeCache() {
        return nomeCache;
    }

    /** A escolha de cada no, na ordem dos rotulos. */
    public List<Chave> escolhas() {
        return chaves;
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
    public String chave(int indice) {
        return chaves.get(indice).chave();
    }

    @Override
    public Boolean chaveNoPrimario(int indice) {
        return chaves.get(indice).primario();
    }

    @Override
    public String motivoDaChave(int indice) {
        return chaves.get(indice).motivo();
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
