// B-44.1b: cliente Hot Rod que escreve pelo ALIAS, um cliente por no (BASIC = fixado, sem topologia).
// Propriedades: -R-Dalias=... -R-Ddur=segundos -R-Dout=arquivo.tsv -R-Dalvos=cacheA,cacheB -R-Dtag=...
import org.infinispan.client.hotrod.*;
import org.infinispan.client.hotrod.configuration.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
String alias = System.getProperty("alias");
long dur = Long.parseLong(System.getProperty("dur"));
String out = System.getProperty("out");
String tag = System.getProperty("tag", "a");
String[] alvos = System.getProperty("alvos").split(",");
String[] portas = {"12441","12442","12443"};
RemoteCacheManager rcm(String porta) {
  var cb = new ConfigurationBuilder();
  cb.addServer().host("127.0.0.1").port(Integer.parseInt(porta));
  cb.clientIntelligence(ClientIntelligence.BASIC).socketTimeout(3000).connectionTimeout(3000).maxRetries(0);
  cb.security().authentication().username("admin").password("infinispan");
  return new RemoteCacheManager(cb.build());
}
var linhas = new ConcurrentLinkedQueue<String>();
var chaves = new ConcurrentLinkedQueue<String>();
var exec = Executors.newFixedThreadPool(3);
long fim = System.currentTimeMillis() + dur * 1000;
for (int n = 0; n < 3; n++) {
  final int no = n;
  exec.submit(() -> {
    RemoteCacheManager m = rcm(portas[no]);
    RemoteCache<String,String> c = null;
    int i = 0;
    while (System.currentTimeMillis() < fim) {
      String k = tag + "-isn" + (no+1) + "-" + (i++);
      long t0 = System.currentTimeMillis();
      String res;
      try {
        if (c == null) c = m.getCache(alias);
        c.put(k, "v");
        res = "OK"; chaves.add(k + "\t" + t0);
      } catch (Throwable e) { res = "ERRO " + String.valueOf(e).replace('\t',' ').replace('\n',' '); if (res.length() > 200) res = res.substring(0,200); }
      long t1 = System.currentTimeMillis();
      linhas.add("isn" + (no+1) + "\t" + k + "\t" + t0 + "\t" + (t1-t0) + "\t" + res);
      try { Thread.sleep(50); } catch (InterruptedException e) {}
    }
    m.stop();
    return null;
  });
}
exec.shutdown(); exec.awaitTermination(dur + 60, TimeUnit.SECONDS);
// verificacao: em que cache real ficou cada chave
RemoteCacheManager v = rcm("12441");
var onde = new StringBuilder();
for (String kt : chaves) {
  String k = kt.split("\t")[0];
  StringBuilder s = new StringBuilder();
  for (String a : alvos) { if (v.getCache(a).containsKey(k)) s.append(a).append(","); }
  onde.append(kt).append("\t").append(s).append("\n");
}
v.stop();
try (var w = new PrintWriter(out)) { for (String l : linhas) w.println(l); }
try (var w = new PrintWriter(out.replace(".tsv", "-onde.tsv"))) { w.print(onde); }
System.out.println("fim " + linhas.size() + " ops");
/exit
