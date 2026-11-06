// Smoke da correcao do P-48, parte C (06/11/2026): sobre os MESMOS dados, quantas chaves divergentes entre replicas o
// laco de convergencia ve com a topologia velha (a que o rcm principal do programa tem depois de um F1 sem operacao)
// e com a atualizada. A comparacao e a do InvariantAuditor (I5: estado, motivo, identidade da SessionState; I6:
// contadorFalhas e status da IdentityState; chave ausente numa replica nao conta). A sonda abre o seu cliente
// topology-aware junto com o programa (antes do F1) e nao opera mais nele, como o rcm principal no S3.
//   "$JAVA_HOME/bin/java" -cp <jar> SondaDivergencia.java <servers> <usuario> <senha> <dir_gatilhos> <cache_sessoes> <cache_contadores>
import br.unipampa.tcc.session.IdentityState;
import br.unipampa.tcc.session.SessionState;
import org.infinispan.client.hotrod.CacheTopologyInfo;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.IntSets;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class SondaDivergencia {
    static String servers, usuario, senha;

    static ConfigurationBuilder base() {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.security().authentication().enable().username(usuario).password(senha);
        cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        return cb;
    }

    static RemoteCacheManager principal() {
        ConfigurationBuilder cb = base();
        for (String s : servers.split(",")) {
            String[] hp = s.split(":");
            cb.addServer().host(hp[0].trim()).port(Integer.parseInt(hp[1].trim()));
        }
        return new RemoteCacheManager(cb.build());
    }

    static Map<Integer, Set<Integer>> porPorta(CacheTopologyInfo t) {
        Map<Integer, Set<Integer>> m = new TreeMap<>();
        for (Map.Entry<SocketAddress, Set<Integer>> e : t.getSegmentsPerServer().entrySet()) {
            if (e.getKey() instanceof InetSocketAddress isa) {
                m.put(isa.getPort(), new TreeSet<>(e.getValue()));
            }
        }
        return m;
    }

    static void log(String s) {
        System.out.println(Instant.now() + " " + s);
        System.out.flush();
    }

    @SuppressWarnings("unchecked")
    static <V> List<Map<String, V>> lerReplicas(String cache, Map<Integer, Set<Integer>> segs) {
        List<Map<String, V>> out = new ArrayList<>();
        for (String s : servers.split(",")) {
            String[] hp = s.split(":");
            int porta = Integer.parseInt(hp[1].trim());
            ConfigurationBuilder cb = base();
            cb.addServer().host(hp[0].trim()).port(porta);
            cb.clientIntelligence(ClientIntelligence.BASIC);
            Map<String, V> m = new HashMap<>();
            try (RemoteCacheManager b = new RemoteCacheManager(cb.build())) {
                RemoteCache<String, V> c = b.getCache(cache);
                Set<Integer> sg = segs.getOrDefault(porta, Set.of());
                if (!sg.isEmpty()) {
                    try (var it = c.entrySet(IntSets.from(sg)).iterator()) {
                        while (it.hasNext()) {
                            var e = it.next();
                            m.put(e.getKey(), e.getValue());
                        }
                    }
                }
            }
            out.add(m);
        }
        return out;
    }

    static List<String> divergentesI5(List<Map<String, SessionState>> snaps) {
        List<String> out = new ArrayList<>();
        Set<String> chaves = new TreeSet<>();
        snaps.forEach(m -> chaves.addAll(m.keySet()));
        for (String k : chaves) {
            SessionState ref = null;
            for (Map<String, SessionState> m : snaps) {
                SessionState s = m.get(k);
                if (s == null) continue;
                if (ref == null) { ref = s; continue; }
                if (s.estado() != ref.estado() || !Objects.equals(s.motivo(), ref.motivo())
                        || !Objects.equals(s.identidade(), ref.identidade())) {
                    out.add(k + " (" + ref.estado() + "/" + ref.motivo() + " vs " + s.estado() + "/" + s.motivo() + ")");
                    break;
                }
            }
        }
        return out;
    }

    static List<String> divergentesI6(List<Map<String, IdentityState>> snaps) {
        List<String> out = new ArrayList<>();
        Set<String> chaves = new TreeSet<>();
        snaps.forEach(m -> chaves.addAll(m.keySet()));
        for (String k : chaves) {
            IdentityState ref = null;
            for (Map<String, IdentityState> m : snaps) {
                IdentityState s = m.get(k);
                if (s == null) continue;
                if (ref == null) { ref = s; continue; }
                if (ref.contadorFalhas() != s.contadorFalhas() || ref.status() != s.status()) {
                    out.add(k);
                    break;
                }
            }
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        servers = a[0]; usuario = a[1]; senha = a[2];
        Path g = Path.of(a[3]);
        String cs = a[4], cc = a[5];
        try (RemoteCacheManager velho = principal()) {
            velho.getCache(cs).containsKey("__sonda__");
            velho.getCache(cc).containsKey("__sonda__");
            log("cliente aberto antes do F1: sessoes topology_id=" + velho.getCache(cs).getCacheTopologyInfo().getTopologyId()
                    + " contadores topology_id=" + velho.getCache(cc).getCacheTopologyInfo().getTopologyId());
            Files.writeString(g.resolve("sonda-pronta"), Instant.now() + "\n");
            while (!Files.exists(g.resolve("programa-fim"))) {
                Thread.sleep(500);
            }
            try (RemoteCacheManager novo = principal()) {
                novo.getCache(cs).containsKey("__sonda__");
                novo.getCache(cc).containsKey("__sonda__");
                Map<String, Map<Integer, Set<Integer>>> vista = new LinkedHashMap<>();
                vista.put("VELHA", null);
                vista.put("ATUALIZADA", null);
                for (String nome : vista.keySet()) {
                    RemoteCacheManager r = nome.equals("VELHA") ? velho : novo;
                    CacheTopologyInfo ts = r.getCache(cs).getCacheTopologyInfo();
                    CacheTopologyInfo tc = r.getCache(cc).getCacheTopologyInfo();
                    List<Map<String, SessionState>> s5 = lerReplicas(cs, porPorta(ts));
                    List<Map<String, IdentityState>> s6 = lerReplicas(cc, porPorta(tc));
                    List<String> d5 = divergentesI5(s5);
                    List<String> d6 = divergentesI6(s6);
                    log(String.format("%s topology_id sessoes=%s contadores=%s | divergentes I5=%d I6=%d %s",
                            nome, ts.getTopologyId(), tc.getTopologyId(), d5.size(), d6.size(),
                            d5.isEmpty() ? "" : d5.toString()));
                }
            }
        }
        Files.writeString(g.resolve("sonda-fim"), Instant.now() + "\n");
    }
}
