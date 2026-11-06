import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import br.unipampa.tcc.session.AtuadorIgnorarCache;
import br.unipampa.tcc.session.ClienteAdminRest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * P1-IC (06/11): falso alarme provocado sem corte. Manda o AC-7 ao alvo pelo adaptador de producao
 * ({@link AtuadorIgnorarCache}, parametros {@code PADRAO}) com o cluster inteiro, como o executor do controlador
 * mandaria, e imprime o resultado do aplicar e do reverter (que o executor chama depois de um FALHOU). Copia de
 * trabalho do harness (nao entra no workload), no estilo do SmokeAtuador do W-2a.
 *
 * <p>Uso: java -cp session-workload.jar SmokeAc7.java URLS_ADMIN ALVO CACHE; senha so pela variavel SMOKE_SENHA.</p>
 */
public class SmokeAc7 {

    static long epochNs() {
        Instant i = Instant.now();
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }

    static void imprimir(String op, long t0, long t1, ResultadoAtuacao r) {
        System.out.println(op + "\tt_ini_epoch_ns=" + t0 + "\tt_fim_epoch_ns=" + t1 + "\tduracao_ms=" + (t1 - t0) / 1_000_000
                + "\testado=" + r.estado() + "\thttp=" + r.codigoHttp() + "\terro=" + r.erro() + "\tlido=" + r.lidoDeVolta());
    }

    public static void main(String[] a) throws Exception {
        List<String> urls = List.of(a[0].split(","));
        Set<String> rotulos = new LinkedHashSet<>();
        for (String u : urls) {
            rotulos.add(u.substring(u.indexOf("://") + 3));
        }
        String senha = System.getenv("SMOKE_SENHA");
        AtuadorIgnorarCache at = new AtuadorIgnorarCache(urls, new ClienteAdminRest("admin", senha), () -> rotulos,
                AtuadorIgnorarCache.Parametros.PADRAO);
        AcaoCluster acao = new AcaoCluster(AcaoCluster.Tipo.AC_7, List.of(a[2]), a[1], null);
        long prazo = AcaoCluster.Tipo.AC_7.prazoNs(2_000_000_000L);
        long t0 = epochNs();
        ResultadoAtuacao r = at.aplicar(acao, prazo);
        long t1 = epochNs();
        imprimir("APLICAR", t0, t1, r);
        long t2 = epochNs();
        ResultadoAtuacao v = at.reverter(acao, prazo);
        long t3 = epochNs();
        imprimir("REVERTER", t2, t3, v);
    }
}
