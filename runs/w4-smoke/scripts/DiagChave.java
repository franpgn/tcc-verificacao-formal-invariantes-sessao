import org.infinispan.client.hotrod.DataFormat;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * W-4 smoke, diagnostico do A7 (P-40): para cada chave, o dono primario que o cliente Hot Rod topology-aware calcula
 * (o mesmo calculo de CanalCanarioHotRod.escolherPelaTopologia) e os dados para conferir pela REST
 * (?action=distribution). Uso: java -cp <jar> DiagChave.java <cache> <espera_s> <chave>...
 * Senha por -Dsenha (laboratorio); -Dgravar=true grava cada chave uma vez. Saida TSV: rodada, topologyId, chave, hex,
 * primario_cliente, segmento, donos_no_hash_do_cliente.
 */
public class DiagChave {
    public static void main(String[] a) throws Exception {
        String cache = a[0];
        int espera = Integer.parseInt(a[1]);
        ConfigurationBuilder cb = new ConfigurationBuilder();
        for (int p = 12611; p <= 12613; p++) {
            cb.addServer().host("127.0.0.1").port(p);
        }
        cb.security().authentication().enable().username("admin").password(System.getProperty("senha"));
        cb.marshaller(new JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        cb.socketTimeout(2000).connectionTimeout(2000).maxRetries(1);
        RemoteCacheManager rcm = new RemoteCacheManager(cb.build());
        try {
            RemoteCache<String, Object> c = rcm.getCache(cache);
            byte[] nome = cache.getBytes(StandardCharsets.UTF_8);
            ChannelFactory cf = rcm.getChannelFactory();
            DataFormat df = c.getDataFormat();
            for (int r = 0; r < 2; r++) {
                c.containsKey("lag:topologia");
                System.out.println("# rodada=" + r + " topologyId=" + cf.getTopologyId(nome) + " objectStorage="
                        + df.isObjectStorage() + " servidores=" + cf.getServers(nome) + " keyType=" + df.getKeyType());
                for (int i = 2; i < a.length; i++) {
                    String k = a[i];
                    byte[] b = df.keyToBytes(k);
                    Object rot = df.isObjectStorage() ? k : b;
                    SocketAddress s = cf.getHashAwareServer(rot, nome);
                    int seg = cf.getConsistentHash(nome) == null ? -1
                            : ((org.infinispan.client.hotrod.impl.consistenthash.SegmentConsistentHash)
                            cf.getConsistentHash(nome)).getSegment(rot);
                    String hp = s instanceof InetSocketAddress x ? x.getHostString() + ":" + x.getPort() : String.valueOf(s);
                    String donos = seg < 0 ? "?" : java.util.Arrays.toString(
                            ((org.infinispan.client.hotrod.impl.consistenthash.SegmentConsistentHash)
                                    cf.getConsistentHash(nome)).getSegmentOwners()[seg]);
                    if (r == 0 && Boolean.getBoolean("gravar")) {
                        c.put(k, "diag");
                    }
                    System.out.println(r + "\t" + cf.getTopologyId(nome) + "\t" + k + "\t" + HexFormat.of().formatHex(b)
                            + "\t" + hp + "\t" + seg + "\t" + donos);
                }
                if (r == 0 && espera > 0) {
                    Thread.sleep(espera * 1000L);
                }
            }
        } finally {
            rcm.stop();
        }
    }
}
