// Smoke da correcao do P-48 (06/11/2026): o mesmo cenario do runs/p48-smoke (rcm principal com a configuracao do
// WorkloadMain, clear e carga inicial, nenhuma operacao durante o F1), agora com o jar da branch
// fix/p48-topologia-convergencia. Depois do F1:
//   ANTES  - leituras por replica com a topologia velha do principal contra a de um cliente novo (o erro do P-48);
//   CORRECAO - br.unipampa.tcc.session.AtualizacaoTopologia.doPrincipal(rcm, caches, System.out::println).atualizar(),
//              a mesma chamada que o laco de convergencia faz no inicio de cada rodada;
//   DEPOIS - as mesmas leituras com a topologia do principal atualizada: o erro tem de cair a 0.
// Harness: "$JAVA_HOME/bin/java" -cp <jar da correcao> SondaP48Fix.java <servers> <usuario> <senha> <n_chaves> <dir_gatilhos>
import br.unipampa.tcc.session.AtualizacaoTopologia;
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

public class SondaP48Fix {
    static final List<String> CACHES = List.of("sessions-async", "counters-async");
    static String servers, usuario, senha;
    static int erroTotal;

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

    static RemoteCacheManager basico(String host, int porta) {
        ConfigurationBuilder cb = base();
        cb.addServer().host(host).port(porta);
        cb.clientIntelligence(ClientIntelligence.BASIC);
        return new RemoteCacheManager(cb.build());
    }

    static Map<Integer, TreeSet<Integer>> porPorta(CacheTopologyInfo t) {
        Map<Integer, TreeSet<Integer>> m = new TreeMap<>();
        for (Map.Entry<SocketAddress, Set<Integer>> e : t.getSegmentsPerServer().entrySet()) {
            if (e.getKey() instanceof InetSocketAddress isa) {
                m.put(isa.getPort(), new TreeSet<>(e.getValue()));
            }
        }
        return m;
    }

    static String resumo(String rotulo, CacheTopologyInfo t) {
        StringBuilder sb = new StringBuilder(rotulo).append(" topology_id=").append(t.getTopologyId());
        for (Map.Entry<Integer, TreeSet<Integer>> e : porPorta(t).entrySet()) {
            sb.append(" ").append(e.getKey()).append("=").append(e.getValue().size());
        }
        return sb.toString();
    }

    static void log(String s) {
        System.out.println(Instant.now() + " " + s);
        System.out.flush();
    }

    static Set<String> chaves(RemoteCache<String, Object> c, Set<Integer> segs) {
        Set<String> out = new HashSet<>();
        if (segs.isEmpty()) {
            return out;
        }
        try (var it = c.entrySet(IntSets.from(segs)).iterator()) {
            while (it.hasNext()) {
                out.add(it.next().getKey());
            }
        }
        return out;
    }

    /** Leitura por replica (um cliente BASIC por no, entrySet dos segmentos do no) com a topologia usada e a real. */
    static void comparar(String rotulo, Map<String, CacheTopologyInfo> usada, Map<String, CacheTopologyInfo> real) {
        for (String cache : CACHES) {
            Map<Integer, TreeSet<Integer>> u = porPorta(usada.get(cache));
            Map<Integer, TreeSet<Integer>> r = porPorta(real.get(cache));
            log(rotulo + " cache=" + cache + " segmentos iguais por no: " + u.equals(r));
            for (String s : servers.split(",")) {
                String[] hp = s.split(":");
                int porta = Integer.parseInt(hp[1].trim());
                try (RemoteCacheManager b = basico(hp[0].trim(), porta)) {
                    RemoteCache<String, Object> c = b.getCache(cache);
                    Set<String> lu = chaves(c, u.getOrDefault(porta, new TreeSet<>()));
                    Set<String> lr = chaves(c, r.getOrDefault(porta, new TreeSet<>()));
                    Set<String> perde = new HashSet<>(lr); perde.removeAll(lu);
                    Set<String> acrescenta = new HashSet<>(lu); acrescenta.removeAll(lr);
                    int erro = perde.size() + acrescenta.size();
                    erroTotal += rotulo.startsWith("DEPOIS") ? erro : 0;
                    double base = lr.isEmpty() ? 1.0 : lr.size();
                    log(String.format("%s cache=%s no=%d chaves do no=%d perde=%d (%.1f%%) acrescenta=%d (%.1f%%)",
                            rotulo, cache, porta, lr.size(), perde.size(), 100.0 * perde.size() / base,
                            acrescenta.size(), 100.0 * acrescenta.size() / base));
                }
            }
        }
    }

    public static void main(String[] a) throws Exception {
        servers = a[0]; usuario = a[1]; senha = a[2];
        int n = Integer.parseInt(a[3]);
        Path g = Path.of(a[4]);
        Files.createDirectories(g);
        try (RemoteCacheManager rcm = principal()) {
            for (String cache : CACHES) {
                RemoteCache<String, Object> c = rcm.getCache(cache);
                c.clear();
                for (int i = 0; i < n; i++) {
                    c.put("p48:" + i, "v" + i);
                }
                log(resumo("T0 principal depois da carga inicial cache=" + cache, c.getCacheTopologyInfo()));
            }
            Files.writeString(g.resolve("pronto-para-f1"), Instant.now() + "\n");
            log("aguardando o F1 (gatilho f1-feito); nenhuma operacao no principal ate la");
            while (!Files.exists(g.resolve("f1-feito"))) {
                Thread.sleep(500);
            }
            Map<String, CacheTopologyInfo> velha = new LinkedHashMap<>();
            Map<String, CacheTopologyInfo> real = new LinkedHashMap<>();
            try (RemoteCacheManager novo = principal()) {
                for (String cache : CACHES) {
                    velha.put(cache, rcm.getCache(cache).getCacheTopologyInfo());
                    log(resumo("T1 principal depois do F1, sem operacao cache=" + cache, velha.get(cache)));
                    RemoteCache<String, Object> cn = novo.getCache(cache);
                    cn.get("p48:0");
                    real.put(cache, cn.getCacheTopologyInfo());
                    log(resumo("T2 cliente novo (a topologia de verdade) cache=" + cache, real.get(cache)));
                }
                comparar("ANTES", velha, real);
                log("CORRECAO: AtualizacaoTopologia.doPrincipal(rcm, caches, System.out::println).atualizar()");
                AtualizacaoTopologia.doPrincipal(rcm, CACHES, l -> log("  " + l)).atualizar();
                Map<String, CacheTopologyInfo> atualizada = new LinkedHashMap<>();
                for (String cache : CACHES) {
                    atualizada.put(cache, rcm.getCache(cache).getCacheTopologyInfo());
                    log(resumo("T3 principal depois da atualizacao cache=" + cache, atualizada.get(cache)));
                }
                comparar("DEPOIS", atualizada, real);
            }
            log("RESULTADO erro total depois da correcao (chaves perdidas + acrescentadas, 3 nos x 2 caches) = " + erroTotal);
            Files.writeString(g.resolve("sonda-fim"), Instant.now() + "\n");
        }
    }
}
