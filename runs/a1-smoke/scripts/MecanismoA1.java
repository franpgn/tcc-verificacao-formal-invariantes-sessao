import br.unipampa.tcc.session.SessionState;
import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A1 smoke, teste dirigido do mecanismo (06/11): o caso exato que a politica de fusao deve corrigir, sem depender do
 * acaso da carga. Roda com o jar do programa de carga no classpath (mesmo SessionState, mesmo cliente Hot Rod 15.0.0,
 * mesma configuracao de S3Clientes.base: JavaSerializationMarshaller, allow list, cliente BASIC preso a um no).
 *
 *   criar  host:porta arquivo N   cria N sessoes VALID (SessionState.criar) por um no da maioria; grava as sids
 *   logout host:porta arquivo     pelo no isolado, para cada sid, a mesma sequencia do logout do programa de carga
 *                                 (getWithMetadata + replaceWithVersion com SessionState.invalidar(LOGOUT)), em
 *                                 paralelo e com prazo curto; grava em arquivo.confirmados as sids cuja escrita foi
 *                                 reconhecida (o isn3 so confirma as de que e dono primario)
 *   ler    host:porta arquivo     le cada sid de arquivo e imprime estado/motivo/versao (ou AUSENTE)
 *
 * Senha so pela variavel A1_SENHA.
 */
public class MecanismoA1 {

    static RemoteCacheManager cliente(String hp, int timeoutMs) {
        String[] p = hp.split(":");
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.security().authentication().enable().username("admin").password(System.getenv("A1_SENHA"));
        cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        cb.socketTimeout(timeoutMs);
        cb.connectionTimeout(timeoutMs);
        cb.maxRetries(0);
        cb.addServer().host(p[0]).port(Integer.parseInt(p[1]));
        cb.clientIntelligence(ClientIntelligence.BASIC);
        return new RemoteCacheManager(cb.build());
    }

    public static void main(String[] a) throws Exception {
        String fase = a[0];
        Path arq = Path.of(a[2]);
        try (RemoteCacheManager rcm = cliente(a[1], fase.equals("logout") ? 1500 : 5000)) {
            RemoteCache<String, SessionState> c = rcm.getCache("sessions-async");
            switch (fase) {
                case "criar": {
                    int n = Integer.parseInt(a[3]);
                    List<String> sids = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        String sid = UUID.randomUUID().toString();
                        c.put(sid, SessionState.criar(sid, "a1-m-" + (i % 10), Instant.now()));
                        sids.add(sid);
                    }
                    Files.write(arq, sids, StandardCharsets.UTF_8);
                    System.out.println("criadas=" + n);
                    break;
                }
                case "logout": {
                    List<String> sids = Files.readAllLines(arq, StandardCharsets.UTF_8);
                    ExecutorService ex = Executors.newFixedThreadPool(32);
                    List<Future<String>> fs = new ArrayList<>();
                    for (String sid : sids) {
                        fs.add(ex.submit(() -> {
                            try {
                                MetadataValue<SessionState> m = c.getWithMetadata(sid);
                                if (m == null) {
                                    return sid + "\tAUSENTE";
                                }
                                SessionState nova = m.getValue().invalidar(SessionState.Motivo.LOGOUT, Instant.now());
                                boolean ok = c.replaceWithVersion(sid, nova, m.getVersion());
                                return sid + "\t" + (ok ? "CONFIRMADO" : "VERSAO") + "\t" + Instant.now();
                            } catch (RuntimeException e) {
                                return sid + "\tERRO\t" + e.getClass().getSimpleName();
                            }
                        }));
                    }
                    List<String> conf = new ArrayList<>();
                    int erros = 0;
                    for (Future<String> f : fs) {
                        String r = f.get();
                        if (r.contains("\tCONFIRMADO")) {
                            conf.add(r.split("\t")[0]);
                        } else {
                            erros++;
                        }
                    }
                    ex.shutdownNow();
                    Files.write(Path.of(a[2] + ".confirmados"), conf, StandardCharsets.UTF_8);
                    System.out.println("logout tentados=" + sids.size() + " confirmados=" + conf.size()
                            + " nao_confirmados=" + erros + " instante=" + Instant.now());
                    break;
                }
                case "ler": {
                    int valid = 0, invalid = 0, ausente = 0, erro = 0;
                    for (String sid : Files.readAllLines(arq, StandardCharsets.UTF_8)) {
                        try {
                            SessionState s = c.get(sid);
                            if (s == null) {
                                ausente++;
                                System.out.println(sid + "\tAUSENTE");
                            } else {
                                if (s.estado() == SessionState.Estado.VALID) valid++; else invalid++;
                                System.out.println(sid + "\t" + s.estado() + "/" + s.motivo() + "/v" + s.versao());
                            }
                        } catch (RuntimeException e) {
                            erro++;
                            System.out.println(sid + "\tERRO\t" + e.getClass().getSimpleName());
                        }
                    }
                    System.out.println("RESUMO servidor=" + a[1] + " VALID=" + valid + " INVALID=" + invalid
                            + " AUSENTE=" + ausente + " ERRO=" + erro);
                    break;
                }
                default:
                    throw new IllegalArgumentException(fase);
            }
        }
    }
}
