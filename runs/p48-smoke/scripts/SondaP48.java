// Smoke do P-48 (06/11/2026): a topologia do rcm principal do programa de carga fica velha depois de um F1 em S3?
// Harness (nao e codigo do programa): roda com o jar da ponta 1f65a02 no classpath, como fonte unica:
//   "$JAVA_HOME/bin/java" -cp <jar> SondaP48.java <servers> <usuario> <senha> <n_chaves> <dir_gatilhos> [f2]
// Reproduz o que o programa faz com o rcm principal no S3 (WorkloadMain.executarRepeticao, mesma configuracao: todos os
// servidores, autenticacao, JavaSerializationMarshaller, inteligencia padrao HASH_DISTRIBUTION_AWARE): clear e carga
// inicial pelo rcm principal, e depois nenhuma operacao nele durante a carga (a carga do S3 vai pelos clientes BASIC
// fixados a nos, S3Clientes). Le getCacheTopologyInfo() antes e depois do F1 feito pelo driver, compara com um cliente
// novo (a topologia de verdade) e faz as leituras por replica do laco de convergencia (abrirLeitoresMultiReplica:
// um cliente BASIC por no, entrySet(segmentos do no pela topologia do rcm principal)) com a topologia velha e a nova.
import org.infinispan.client.hotrod.CacheTopologyInfo;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.IntSet;
import org.infinispan.commons.util.IntSets;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class SondaP48 {
    static final String[] CACHES = {"sessions-async", "counters-async"};
    static String servers, usuario, senha;

    static RemoteCacheManager principal() {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        for (String s : servers.split(",")) {
            String[] hp = s.split(":");
            cb.addServer().host(hp[0].trim()).port(Integer.parseInt(hp[1].trim()));
        }
        cb.security().authentication().enable().username(usuario).password(senha);
        cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        return new RemoteCacheManager(cb.build());
    }

    static RemoteCacheManager basico(String host, int porta) {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.addServer().host(host).port(porta);
        cb.clientIntelligence(ClientIntelligence.BASIC);
        cb.security().authentication().enable().username(usuario).password(senha);
        cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
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
        StringBuilder sb = new StringBuilder(rotulo).append(" topology_id=").append(t.getTopologyId())
                .append(" num_segments=").append(t.getNumSegments()).append(" servidores=").append(t.getSegmentsPerServer().size());
        for (Map.Entry<Integer, TreeSet<Integer>> e : porPorta(t).entrySet()) {
            sb.append(" ").append(e.getKey()).append("=").append(e.getValue().size());
        }
        return sb.toString();
    }

    static void log(String s) {
        System.out.println(Instant.now() + " " + s);
        System.out.flush();
    }

    static Map<String, Object> ler(RemoteCache<String, Object> c, Set<Integer> segs) {
        Map<String, Object> out = new HashMap<>();
        if (segs.isEmpty()) {
            return out;
        }
        IntSet is = IntSets.from(segs);
        try (var it = c.entrySet(is).iterator()) {
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                out.put(e.getKey(), e.getValue());
            }
        } catch (Exception ex) {
            log("  leitura falhou: " + ex);
            return null;
        }
        return out;
    }

    static void esperar(Path dir, String nome) throws InterruptedException {
        Path p = dir.resolve(nome);
        while (!Files.exists(p)) {
            Thread.sleep(500);
        }
    }

    static void tocar(Path dir, String nome) throws Exception {
        Files.writeString(dir.resolve(nome), Instant.now().toString() + System.lineSeparator());
    }

    /** Leituras por replica com a topologia "usada" (a do rcm principal) e a "real" (a de um cliente novo). */
    static void leiturasPorReplica(String rotulo, Map<String, CacheTopologyInfo> usada, Map<String, CacheTopologyInfo> real,
                                   int nChaves) {
        for (String cache : CACHES) {
            Map<Integer, TreeSet<Integer>> u = porPorta(usada.get(cache));
            Map<Integer, TreeSet<Integer>> r = porPorta(real.get(cache));
            log(rotulo + " cache=" + cache + " segmentos iguais por no: " + u.equals(r));
            for (String s : servers.split(",")) {
                String[] hp = s.split(":");
                int porta = Integer.parseInt(hp[1].trim());
                Set<Integer> su = u.getOrDefault(porta, new TreeSet<>());
                Set<Integer> sr = r.getOrDefault(porta, new TreeSet<>());
                Set<Integer> soUsada = new TreeSet<>(su); soUsada.removeAll(sr);
                Set<Integer> soReal = new TreeSet<>(sr); soReal.removeAll(su);
                try (RemoteCacheManager b = basico(hp[0].trim(), porta)) {
                    RemoteCache<String, Object> c = b.getCache(cache);
                    Map<String, Object> lu = ler(c, su);
                    Map<String, Object> lr = ler(c, sr);
                    Map<String, Object> lsu = ler(c, soUsada);
                    Map<String, Object> lsr = ler(c, soReal);
                    log(String.format("%s cache=%s no=%d segs_usada=%d segs_real=%d so_usada=%d so_real=%d"
                                    + " | chaves lidas: usada=%s real=%s so_usada=%s so_real=%s",
                            rotulo, cache, porta, su.size(), sr.size(), soUsada.size(), soReal.size(),
                            lu == null ? "falhou" : lu.size(), lr == null ? "falhou" : lr.size(),
                            lsu == null ? "falhou" : lsu.size(), lsr == null ? "falhou" : lsr.size()));
                    if (lu != null && lr != null) {
                        Set<String> faltam = new HashSet<>(lr.keySet()); faltam.removeAll(lu.keySet());
                        Set<String> sobram = new HashSet<>(lu.keySet()); sobram.removeAll(lr.keySet());
                        log(String.format("%s cache=%s no=%d chaves que a leitura com a topologia usada perde=%d e acrescenta=%d",
                                rotulo, cache, porta, faltam.size(), sobram.size()));
                    }
                }
            }
        }
    }

    public static void main(String[] a) throws Exception {
        servers = a[0]; usuario = a[1]; senha = a[2];
        int nChaves = Integer.parseInt(a[3]);
        Path g = Path.of(a[4]);
        boolean comF2 = a.length > 5 && a[5].equals("f2");
        Files.createDirectories(g);
        try (RemoteCacheManager rcm = principal()) {
            Map<String, RemoteCache<String, Object>> cs = new LinkedHashMap<>();
            for (String cache : CACHES) {
                RemoteCache<String, Object> c = rcm.getCache(cache);
                c.clear();                                      // o reset (CacheLimpavel) pelo rcm principal
                for (int i = 0; i < nChaves; i++) {             // a carga inicial (prepararCarga) pelo rcm principal
                    c.put("p48:" + i, "v" + i);
                }
                cs.put(cache, c);
            }
            Map<String, CacheTopologyInfo> t0 = new LinkedHashMap<>();
            for (String cache : CACHES) {
                t0.put(cache, cs.get(cache).getCacheTopologyInfo());
                log(resumo("T0 rcm principal depois da carga inicial cache=" + cache, t0.get(cache)));
            }
            tocar(g, "pronto-para-f1");
            log("aguardando o F1 (gatilho f1-feito); nenhuma operacao no rcm principal ate la");
            esperar(g, "f1-feito");

            Map<String, CacheTopologyInfo> t1 = new LinkedHashMap<>();
            Map<String, CacheTopologyInfo> t2 = new LinkedHashMap<>();
            try (RemoteCacheManager novo = principal()) {
                for (String cache : CACHES) {
                    t1.put(cache, cs.get(cache).getCacheTopologyInfo());
                    log(resumo("T1 rcm principal depois do F1, sem operacao cache=" + cache, t1.get(cache)));
                    RemoteCache<String, Object> cn = novo.getCache(cache);
                    cn.get("p48:0");
                    t2.put(cache, cn.getCacheTopologyInfo());
                    log(resumo("T2 cliente novo depois do F1 (a topologia de verdade) cache=" + cache, t2.get(cache)));
                }
                leiturasPorReplica("LEITURA-F1", t1, t2, nChaves);
                // uma operacao no rcm principal atualiza a topologia dele?
                for (String cache : CACHES) {
                    cs.get(cache).get("p48:1");
                    CacheTopologyInfo t3 = cs.get(cache).getCacheTopologyInfo();
                    log(resumo("T3 rcm principal depois de um get cache=" + cache, t3));
                    log("T3 igual a T2 por no (cache=" + cache + "): " + porPorta(t3).equals(porPorta(t2.get(cache))));
                }
            }

            if (comF2) {
                tocar(g, "pronto-para-f2");
                log("aguardando a F2 (gatilho f2-feito); nenhuma operacao no rcm principal ate la");
                esperar(g, "f2-feito");
                try (RemoteCacheManager novo = principal()) {
                    Map<String, CacheTopologyInfo> t4 = new LinkedHashMap<>();
                    Map<String, CacheTopologyInfo> t5 = new LinkedHashMap<>();
                    for (String cache : CACHES) {
                        t4.put(cache, cs.get(cache).getCacheTopologyInfo());
                        log(resumo("T4 rcm principal depois da F2 e da fusao, sem operacao cache=" + cache, t4.get(cache)));
                        RemoteCache<String, Object> cn = novo.getCache(cache);
                        cn.get("p48:0");
                        t5.put(cache, cn.getCacheTopologyInfo());
                        log(resumo("T5 cliente novo depois da F2 cache=" + cache, t5.get(cache)));
                        log("T5 igual a T2 por no (a F2 sem reinicio volta a mesma distribuicao?) cache=" + cache + ": "
                                + porPorta(t5.get(cache)).equals(porPorta(t2.get(cache))));
                    }
                    leiturasPorReplica("LEITURA-F2", t4, t5, nChaves);
                }
            }
            tocar(g, "sonda-fim");
            log("fim");
        }
    }
}
