// B-44.1b: getCache por nome real e por alias, num no (BASIC); imprime resultado e excecao
import org.infinispan.client.hotrod.*;
import org.infinispan.client.hotrod.configuration.*;
var cb = new ConfigurationBuilder();
cb.addServer().host("127.0.0.1").port(Integer.parseInt(System.getProperty("porta")));
cb.clientIntelligence(ClientIntelligence.BASIC).maxRetries(0);
cb.security().authentication().username("admin").password("infinispan");
var m = new RemoteCacheManager(cb.build());
for (String n : System.getProperty("nomes").split(",")) {
  try { var c = m.getCache(n); System.out.println("SONDA " + n + " -> " + (c == null ? "null" : "ok")); if (c != null) { c.put("b441b-sonda-" + n, "v"); System.out.println("SONDA put " + n + " ok"); } }
  catch (Throwable e) { System.out.println("SONDA " + n + " -> " + e); }
}
System.out.println("SONDA nomes do servidor: " + m.getCacheNames());
m.stop();
/exit
