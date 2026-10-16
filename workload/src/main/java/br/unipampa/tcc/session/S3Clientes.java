package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B-33 (E2): conexoes do cenario S3. No modo {@code pinned} (padrao) ha um
 * {@link RemoteCacheManager} por no de aplicacao, cada um com <b>so</b> o seu no como servidor e
 * {@link ClientIntelligence#BASIC} (nao aprende a topologia, como um servidor de aplicacao regional
 * atras de balanceador), mais um console administrativo apontado a um no da maioria. O no isolado
 * recebe, assim, escrita de verdade (o campo {@code replica} do CSV e o no). No modo
 * {@code topology} ha um unico cliente topology-aware (contraste). Timeouts curtos de cliente
 * (padrao 2 s, 1 retentativa Hot Rod) em ambos os modos. Os defaults da biblioteca (60 s e 10
 * retentativas) tornariam a falha invisivel numa janela de partição.
 */
public final class S3Clientes implements AutoCloseable {

    /** host:porta de um servidor Hot Rod. */
    public record HostPort(String host, int port) {
        @Override public String toString() { return host + ":" + port; }

        static HostPort parse(String s) {
            String[] hp = s.trim().split(":");
            if (hp.length != 2) {
                throw new IllegalArgumentException("servidor invalido (esperado host:porta): " + s);
            }
            return new HostPort(hp[0].trim(), Integer.parseInt(hp[1].trim()));
        }
    }

    private final List<RemoteCacheManager> gerenciadores = new ArrayList<>();
    private final List<SessionOps> servidoresApp = new ArrayList<>();
    private SessionOps admin;
    private final ConcurrentHashMap<String, SessionState.Motivo> invalidacoes = new ConcurrentHashMap<>();

    public static List<HostPort> servidores(String csv) {
        List<HostPort> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            out.add(HostPort.parse(s));
        }
        return out;
    }

    /** {@code isnK} = K-esimo (1-based) de {@code --servers}; ou um {@code host:porta} literal. */
    public static HostPort resolverNoAdmin(String noAdmin, List<HostPort> servidores) {
        String v = noAdmin.trim();
        if (v.matches("isn\\d+")) {
            int k = Integer.parseInt(v.substring(3));
            if (k < 1 || k > servidores.size()) {
                throw new IllegalArgumentException(
                        "--admin-node " + v + " fora de --servers (" + servidores.size() + " servidores)");
            }
            return servidores.get(k - 1);
        }
        if (v.contains(":")) {
            return HostPort.parse(v);
        }
        throw new IllegalArgumentException("--admin-node deve ser isnK ou host:porta: " + noAdmin);
    }

    private static ConfigurationBuilder base(String usuario, String senha, S3Parametros p) {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.security().authentication().enable().username(usuario).password(senha);
        cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        cb.socketTimeout(p.hotrodTimeoutMs);
        cb.connectionTimeout(p.hotrodTimeoutMs);
        cb.maxRetries(p.hotrodRetentativas);
        return cb;
    }

    /** Servidor de aplicacao pinado: um unico no, sem aprender a topologia. */
    public static Configuration configuracaoPinned(HostPort no, String usuario, String senha, S3Parametros p) {
        ConfigurationBuilder cb = base(usuario, senha, p);
        cb.addServer().host(no.host()).port(no.port());
        cb.clientIntelligence(ClientIntelligence.BASIC);
        return cb.build();
    }

    /** Contraste: cliente topology-aware com todos os servidores. */
    public static Configuration configuracaoTopology(List<HostPort> todos, String usuario, String senha,
                                                     S3Parametros p) {
        ConfigurationBuilder cb = base(usuario, senha, p);
        for (HostPort s : todos) {
            cb.addServer().host(s.host()).port(s.port());
        }
        return cb.build();
    }

    /** Abre os clientes e os {@link SessionOps} do S3 conforme {@code cli}. */
    public static S3Clientes abrir(Cli cli, LatencyRegistry latency, InvariantAuditor auditor,
                                   EventCsvWriter eventos, EpochClock relogio, RastroCliente rastro) {
        S3Parametros p = cli.s3;
        List<HostPort> todos = servidores(cli.servidores);
        S3Clientes c = new S3Clientes();
        try {
            if (p.modoCliente == S3Parametros.ModoCliente.pinned) {
                for (HostPort no : todos) {
                    RemoteCacheManager rcm = new RemoteCacheManager(
                            configuracaoPinned(no, cli.usuario, cli.senha, p));
                    c.gerenciadores.add(rcm);
                    c.servidoresApp.add(new SessionOps(rcm, latency, auditor, eventos, relogio, rastro,
                            c.invalidacoes, no.toString()));
                }
                HostPort noAdmin = resolverNoAdmin(p.noAdmin, todos);
                RemoteCacheManager rcmAdmin = new RemoteCacheManager(
                        configuracaoPinned(noAdmin, cli.usuario, cli.senha, p));
                c.gerenciadores.add(rcmAdmin);
                c.admin = new SessionOps(rcmAdmin, latency, auditor, eventos, relogio, rastro,
                        c.invalidacoes, noAdmin.toString());
            } else {
                RemoteCacheManager rcm = new RemoteCacheManager(
                        configuracaoTopology(todos, cli.usuario, cli.senha, p));
                c.gerenciadores.add(rcm);
                // um unico cliente: o rotulo nao diz em que no a operacao caiu (decide o cliente)
                for (int i = 0; i < todos.size(); i++) {
                    c.servidoresApp.add(new SessionOps(rcm, latency, auditor, eventos, relogio, rastro,
                            c.invalidacoes, "topology"));
                }
                c.admin = new SessionOps(rcm, latency, auditor, eventos, relogio, rastro,
                        c.invalidacoes, "topology");
            }
        } catch (RuntimeException e) {
            c.close();
            throw e;
        }
        return c;
    }

    public List<SessionOps> servidoresApp() { return servidoresApp; }

    public SessionOps admin() { return admin; }

    /** Copia das invalidacoes confirmadas por qualquer ator (insumo da varredura I1/I4). */
    public java.util.Map<String, SessionState.Motivo> invalidacoesSnapshot() {
        return new java.util.HashMap<>(invalidacoes);
    }

    @Override
    public void close() {
        for (RemoteCacheManager g : gerenciadores) {
            try {
                g.close();
            } catch (RuntimeException ignored) {
                // encerramento best-effort
            }
        }
    }
}
