import br.unipampa.tcc.session.IdentityState;
import br.unipampa.tcc.session.SessionState;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A4 (06/11/2026): sonda Hot Rod de alta frequencia para medir o efeito de
 * {@code POST /rest/v2/server/ignored-caches/{cache}} e de {@code POST /rest/v2/server?action=stop}.
 * Copia de trabalho do harness (nao entra no workload): mesma marshaller e allow-list do workload,
 * cliente pinado num unico no ({@link ClientIntelligence#BASIC}), sem retentativa, prazos de 1 s.
 *
 * <p>Para cada cache, duas threads: uma faz {@code put} e outra {@code get} numa de 8 chaves
 * {@code a4-ROTULO-CACHE-i}, a cada INTERVALO_MS (depois do fim da operacao anterior). Cada operacao vira
 * uma linha TSV: t_ini_epoch_ns, t_fim_epoch_ns, no, cache, operacao, resultado (OK ou nome simples da
 * excecao mais interna util), mensagem (300 caracteres, sem tab/quebra). Os valores sao do tipo que o
 * cache guarda (SessionState no de sessoes, IdentityState no de contadores), como a SondaHotrod.</p>
 *
 * <p>Uso: java -cp session-workload.jar SondaA4.java ROTULO HOST:PORTA CACHES INTERVALO_MS DURACAO_S SAIDA
 * [ARQ_FIM]; para quando DURACAO_S vence ou quando ARQ_FIM existe. Senha so pela variavel SONDA_SENHA.</p>
 */
public class SondaA4 {

    static final int PRAZO_MS = 1000;
    static final int CHAVES = 8;

    static long epochNs() {
        Instant i = Instant.now();
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }

    static String limpo(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    /** Classe e mensagem da causa mais interna que nao seja so um invólucro. */
    static String[] descrever(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String msg = e == c ? limpo(e.getMessage()) : limpo(e.getClass().getSimpleName() + ": " + e.getMessage())
                + " | causa " + limpo(c.getClass().getSimpleName() + ": " + c.getMessage());
        return new String[]{e.getClass().getSimpleName(), msg};
    }

    static synchronized void escrever(BufferedWriter w, String linha) {
        try {
            w.write(linha);
            w.write('\n');
            w.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] a) throws Exception {
        String rotulo = a[0];
        String[] hp = a[1].split(":");
        List<String> caches = List.of(a[2].split(","));
        int intervaloMs = Integer.parseInt(a[3]);
        long fimNs = System.nanoTime() + Long.parseLong(a[4]) * 1_000_000_000L;
        Path saida = Path.of(a[5]);
        Path arqFim = a.length > 6 ? Path.of(a[6]) : null;
        String senha = System.getenv("SONDA_SENHA");
        if (senha == null || senha.isEmpty()) {
            throw new IllegalArgumentException("SONDA_SENHA ausente");
        }
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.addServer().host(hp[0]).port(Integer.parseInt(hp[1]));
        cb.security().authentication().enable().username("admin").password(senha);
        cb.marshaller(new JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        cb.clientIntelligence(ClientIntelligence.BASIC);
        cb.socketTimeout(PRAZO_MS);
        cb.connectionTimeout(PRAZO_MS);
        cb.maxRetries(0);
        RemoteCacheManager rcm = new RemoteCacheManager(cb.build());
        BufferedWriter w = Files.newBufferedWriter(saida, StandardCharsets.UTF_8);
        escrever(w, "t_ini_epoch_ns\tt_fim_epoch_ns\tno\tcache\toperacao\tresultado\tmensagem");
        List<Thread> ts = new ArrayList<>();
        for (String cache : caches) {
            RemoteCache<String, Object> rc = rcm.getCache(cache);
            boolean sessoes = cache.startsWith("sessions");
            for (String op : List.of("put", "get")) {
                Thread t = new Thread(() -> {
                    long n = 0;
                    while (System.nanoTime() < fimNs && (arqFim == null || !Files.exists(arqFim))) {
                        String chave = "a4-" + rotulo + "-" + cache + "-" + (n % CHAVES);
                        long t0 = epochNs();
                        String res = "OK";
                        String msg = "";
                        try {
                            if (op.equals("put")) {
                                Object v = sessoes ? SessionState.criar(chave, "sonda", Instant.now())
                                        : IdentityState.criar("sonda", Instant.now());
                                rc.put(chave, v);
                            } else {
                                Object v = rc.get(chave);
                                msg = v == null ? "nulo" : "";
                            }
                        } catch (Throwable e) {
                            String[] d = descrever(e);
                            res = d[0];
                            msg = d[1];
                        }
                        long t1 = epochNs();
                        escrever(w, t0 + "\t" + t1 + "\t" + rotulo + "\t" + cache + "\t" + op + "\t" + res + "\t" + msg);
                        n++;
                        try {
                            Thread.sleep(intervaloMs);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }, "sonda-" + cache + "-" + op);
                ts.add(t);
                t.start();
            }
        }
        for (Thread t : ts) {
            t.join();
        }
        // limpeza das chaves de teste, melhor esforco (prazo curto)
        for (String cache : caches) {
            RemoteCache<String, Object> rc = rcm.getCache(cache);
            for (int i = 0; i < CHAVES; i++) {
                try {
                    rc.remove("a4-" + rotulo + "-" + cache + "-" + i);
                } catch (Throwable e) {
                    escrever(w, epochNs() + "\t" + epochNs() + "\t" + rotulo + "\t" + cache + "\tlimpeza\t"
                            + e.getClass().getSimpleName() + "\t" + limpo(e.getMessage()));
                    break;
                }
            }
        }
        w.close();
        rcm.stop();
    }
}
