import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import br.unipampa.tcc.session.AtuadorRestInfinispan;
import br.unipampa.tcc.session.ClienteAdminRest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** W-2a tarefa zero (d): o AtuadorRestInfinispan real contra o cluster local w2a (portas 1250x), Digest admin. */
public class SmokeAtuador {
    public static void main(String[] a) throws Exception {
        List<String> urls = List.of("http://127.0.0.1:12501", "http://127.0.0.1:12502", "http://127.0.0.1:12503");
        Set<String> todos = new LinkedHashSet<>(List.of("127.0.0.1:12501", "127.0.0.1:12502", "127.0.0.1:12503"));
        AtuadorRestInfinispan at = new AtuadorRestInfinispan(urls, new ClienteAdminRest("admin", "infinispan"), () -> todos,
                50_000_000L);
        List<String> caches = List.of(a[1], a[2]);
        long tc = 2_000_000_000L;
        if (a[0].equals("ac2")) {
            AcaoCluster ac2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, caches, null, null);
            mostrar("AC-2 aplicar", ac2, () -> at.aplicar(ac2, AcaoCluster.Tipo.AC_2.prazoNs(tc)));
            mostrar("AC-2 aplicar de novo", ac2, () -> at.aplicar(ac2, AcaoCluster.Tipo.AC_2.prazoNs(tc)));
            mostrar("AC-2 reverter", ac2, () -> at.reverter(ac2, AcaoCluster.Tipo.AC_2.prazoNs(tc)));
            mostrar("AC-2 reverter de novo (idempotente)", ac2, () -> at.reverter(ac2, AcaoCluster.Tipo.AC_2.prazoNs(tc)));
        } else {
            AcaoCluster ac5 = new AcaoCluster(AcaoCluster.Tipo.AC_5, caches, a[3], null);
            mostrar("AC-5 aplicar em " + a[3], ac5, () -> at.aplicar(ac5, AcaoCluster.Tipo.AC_5.prazoNs(tc)));
        }
    }

    interface Chamada { ResultadoAtuacao run() throws Exception; }

    static void mostrar(String r, AcaoCluster x, Chamada c) throws Exception {
        long t0 = System.nanoTime();
        ResultadoAtuacao res = c.run();
        System.out.printf("%s: %s em %.1f ms%n", r, res, (System.nanoTime() - t0) / 1e6);
    }
}
