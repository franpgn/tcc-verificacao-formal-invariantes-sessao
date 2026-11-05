import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.ResultadoAtuacao;
import br.unipampa.tcc.session.AtuadorRestInfinispan;
import br.unipampa.tcc.session.ClienteAdminRest;
import br.unipampa.tcc.session.EpochClock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W-2a tarefa zero (d) e AC-5 forcado do I-3..I-5: o AtuadorRestInfinispan real contra o cluster local w2a.
 *
 * <p>V-W2a secao 11, condicao 1: cada chamada grava os instantes do pedido e do retorno em epoch-ns pelo
 * {@link EpochClock} (ancora {@code Instant.now()} + {@code System.nanoTime()}), a mesma base de tempo dos
 * {@code end_ns} do CSV de eventos da carga, para contar a parte as violacoes em
 * {@code [t_pedido, t_pedido + prazo do tipo]}.</p>
 *
 * <p>Uso: {@code ac2 <cache1> <cache2>} (aplicar, reaplicar, reverter, reverter), {@code ac2-aplicar|ac2-reverter
 * <cache1> <cache2>} ou {@code ac5 <cache1> <cache2> <alvo host:porta>}. Propriedades opcionais:
 * {@code -Durls=http://127.0.0.1:12501,...} (padrao: portas 12501-12503), {@code -Dsenha=} (padrao
 * {@code infinispan}), {@code -Dprazo.ms=} (padrao: o do tipo, {@code AcaoCluster.Tipo.prazoNs(2 s)}: 2 s no AC-2 e
 * 5 s no AC-5).</p>
 *
 * <p>O conjunto "nos que responderam" e fixo (todos os nos): o SmokeAtuador pula de proposito a guarda da ultima
 * amostra da sonda. No I-5 isso mede o perigo que a guarda evita; o caminho de producao recusaria o pedido sem enviar
 * nada (AtuadorRestInfinispanTest.ac5NoSemResposta).</p>
 */
public class SmokeAtuador {

    static final long T_CHAM_NS = 2_000_000_000L;
    static final EpochClock RELOGIO = EpochClock.capturarAgora();

    public static void main(String[] a) throws Exception {
        List<String> urls = List.of(System.getProperty("urls",
                "http://127.0.0.1:12501,http://127.0.0.1:12502,http://127.0.0.1:12503").split(","));
        Set<String> todos = new LinkedHashSet<>();
        for (String u : urls) {
            todos.add(u.replaceFirst("^https?://", "").replaceAll("/$", ""));
        }
        AtuadorRestInfinispan at = new AtuadorRestInfinispan(urls,
                new ClienteAdminRest("admin", System.getProperty("senha", "infinispan")), () -> todos, 50_000_000L);
        List<String> caches = List.of(a[1], a[2]);
        System.out.println("relogio=EpochClock agora_epoch_ns=" + agora() + " urls=" + urls
                + " responderam=" + todos + " (guarda da ultima amostra pulada de proposito)");
        switch (a[0]) {
            case "ac2" -> {
                AcaoCluster ac2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, caches, null, null);
                long p = prazo(AcaoCluster.Tipo.AC_2);
                mostrar("AC-2 aplicar", p, () -> at.aplicar(ac2, p));
                mostrar("AC-2 aplicar de novo", p, () -> at.aplicar(ac2, p));
                mostrar("AC-2 reverter", p, () -> at.reverter(ac2, p));
                mostrar("AC-2 reverter de novo (idempotente)", p, () -> at.reverter(ac2, p));
            }
            case "ac2-aplicar", "ac2-reverter" -> {
                AcaoCluster ac2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, caches, null, null);
                long p = prazo(AcaoCluster.Tipo.AC_2);
                boolean aplicar = a[0].equals("ac2-aplicar");
                mostrar(aplicar ? "AC-2 aplicar" : "AC-2 reverter", p,
                        () -> aplicar ? at.aplicar(ac2, p) : at.reverter(ac2, p));
            }
            case "ac5" -> {
                AcaoCluster ac5 = new AcaoCluster(AcaoCluster.Tipo.AC_5, caches, a[3], null);
                long p = prazo(AcaoCluster.Tipo.AC_5);
                mostrar("AC-5 aplicar em " + a[3], p, () -> at.aplicar(ac5, p));
            }
            default -> throw new IllegalArgumentException("modo desconhecido: " + a[0]);
        }
    }

    static long agora() {
        return RELOGIO.paraEpochNanos(System.nanoTime());
    }

    static long prazo(AcaoCluster.Tipo t) {
        String ms = System.getProperty("prazo.ms");
        return ms == null ? t.prazoNs(T_CHAM_NS) : Long.parseLong(ms) * 1_000_000L;
    }

    interface Chamada {
        ResultadoAtuacao run() throws Exception;
    }

    static void mostrar(String r, long prazoNs, Chamada c) throws Exception {
        long t0 = agora();
        long m0 = System.nanoTime();
        ResultadoAtuacao res = c.run();
        long m1 = System.nanoTime();
        long t1 = agora();
        List<String> campos = new ArrayList<>();
        campos.add("t_pedido_epoch_ns=" + t0);
        campos.add("t_retorno_epoch_ns=" + t1);
        campos.add("prazo_ns=" + prazoNs);
        campos.add(String.format("duracao_ms=%.1f", (m1 - m0) / 1e6));
        System.out.printf("%s: %s %s%n", r, res, String.join(" ", campos));
    }
}
