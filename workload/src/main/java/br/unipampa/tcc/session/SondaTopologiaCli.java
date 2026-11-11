package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AmostraTopologia;
import br.unipampa.tcc.controle.FonteDeTempo;
import br.unipampa.tcc.controle.ResultadoNo;
import br.unipampa.tcc.controle.SondaTopologia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B-26.2: CLI de smoke da sonda de topologia (Podman local; nao e o laco do controlador, que e B-28.2).
 *
 * <pre>
 * java -cp &lt;jar&gt; br.unipampa.tcc.session.SondaTopologiaCli --servers h:p,... --replication sync|async
 *     [--usuario admin] [--periodo-ms 2000] [--prazo-ms 1500] [--duracao-s 60]
 * </pre>
 *
 * <p>Sem perfil padrao: {@code --replication} e obrigatorio e convertido por
 * {@link PerfilReplicacao#porRotulo}; os caches classificados sao os do perfil. A senha vem da variavel
 * de ambiente {@code SONDA_SENHA} (como {@link SondaHotrod}). Erro de uso: exit 2. O numero de nos
 * esperado e o numero de servidores de {@code --servers}.</p>
 *
 * <p>Saida: uma linha JSON por amostra em stdout, carimbada em epoch-ns no mesmo relogio dos
 * {@code end_ns} ({@link EpochClock}), e no fim uma linha {@code {"resumo":{...}}}. Em stderr, uma linha
 * de custo com o numero de pedidos HTTP e o tempo de CPU da JVM (W-2b: a consulta e HTTP em memoria pelo
 * {@link ClienteAdminRest}; ate o W-2b era um {@code curl} por consulta).</p>
 */
public final class SondaTopologiaCli {

    /** Codigo de saida de erro de uso (opcao ausente ou invalida, senha ausente). */
    static final int EXIT_USO = 2;

    static final String VAR_SENHA = "SONDA_SENHA";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Argumentos validados. {@code toString} nao mostra a senha. */
    record Args(List<String> urls, PerfilReplicacao perfil, String usuario, String senha, long periodoMs,
                long prazoMs, int duracaoS) {
        @Override
        public String toString() {
            return "Args[urls=" + urls + ", perfil=" + perfil + ", usuario=" + usuario + ", senha=***, periodoMs="
                    + periodoMs + ", prazoMs=" + prazoMs + ", duracaoS=" + duracaoS + "]";
        }
    }

    /** Cria o cliente HTTP da sonda (W-2b; injetavel nos testes). */
    @FunctionalInterface
    interface FabricaCliente {
        ClienteAdminRest criar(String usuario, String senha);
    }

    private SondaTopologiaCli() { /* utilitario */ }

    /**
     * Valida os argumentos e a senha do ambiente.
     *
     * @throws IllegalArgumentException em qualquer erro de uso
     */
    static Args parse(String[] argv, Map<String, String> env) {
        String servidores = null;
        String rotuloPerfil = null;
        String usuario = "admin";
        long periodoMs = 2000;
        long prazoMs = 1500;
        int duracaoS = 60;
        for (int i = 0; i < argv.length; i += 2) {
            if (i + 1 >= argv.length) {
                throw new IllegalArgumentException("opcao sem valor: " + argv[i]);
            }
            String v = argv[i + 1];
            switch (argv[i]) {
                case "--servers" -> servidores = v;
                case "--replication" -> rotuloPerfil = v;
                case "--usuario" -> usuario = v;
                case "--periodo-ms" -> periodoMs = Long.parseLong(v);
                case "--prazo-ms" -> prazoMs = Long.parseLong(v);
                case "--duracao-s" -> duracaoS = Integer.parseInt(v);
                default -> throw new IllegalArgumentException("opcao desconhecida: " + argv[i]);
            }
        }
        if (servidores == null) {
            throw new IllegalArgumentException("--servers e obrigatorio");
        }
        if (rotuloPerfil == null) {
            throw new IllegalArgumentException("--replication sync|async e obrigatorio (sem padrao)");
        }
        PerfilReplicacao perfil = PerfilReplicacao.porRotulo(rotuloPerfil);
        List<String> urls = ConsultaSaudeRest.urlsDe(servidores);
        if (periodoMs <= 0 || prazoMs <= 0 || prazoMs >= periodoMs) {
            throw new IllegalArgumentException("exige 0 < --prazo-ms < --periodo-ms: prazo=" + prazoMs
                    + " periodo=" + periodoMs);
        }
        if (duracaoS < 1) {
            throw new IllegalArgumentException("--duracao-s precisa ser >= 1: " + duracaoS);
        }
        String senha = env.get(VAR_SENHA);
        if (senha == null || senha.isEmpty()) {
            throw new IllegalArgumentException("defina a variavel de ambiente " + VAR_SENHA);
        }
        return new Args(urls, perfil, usuario, senha, periodoMs, prazoMs, duracaoS);
    }

    static String linhaAmostra(AmostraTopologia a) {
        ObjectNode o = JSON.createObjectNode();
        o.put("t_epoch_ns", a.tNs());
        o.put("anomala", a.anomala());
        o.put("nos_min", a.nosMinimo());
        ArrayNode porNo = o.putArray("por_no");
        for (ResultadoNo r : a.porNo()) {
            porNo.addObject()
                    .put("no", r.no())
                    .put("respondeu", r.respondeu())
                    .put("numero_de_nos", r.numeroDeNos())
                    .put("motivo", r.motivo())
                    .put("anomalo", r.anomalo());
        }
        return escrever(o);
    }

    static String linhaResumo(long rodadas, long puladas, long anomalas) {
        ObjectNode o = JSON.createObjectNode();
        o.putObject("resumo").put("rodadas", rodadas).put("puladas", puladas).put("anomalas", anomalas);
        return escrever(o);
    }

    private static String escrever(ObjectNode o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static int executar(String[] argv, Map<String, String> env, PrintStream out, PrintStream err,
                        FabricaCliente fabrica) {
        Args a;
        try {
            a = parse(argv, env);
        } catch (IllegalArgumentException e) {
            err.println("erro de uso: " + e.getMessage());
            err.println("uso: SondaTopologiaCli --servers h:p,... --replication sync|async [--usuario admin]"
                    + " [--periodo-ms 2000] [--prazo-ms 1500] [--duracao-s 60]  (senha em " + VAR_SENHA + ")");
            return EXIT_USO;
        }
        EpochClock relogio = EpochClock.capturarAgora();
        FonteDeTempo tempo = () -> relogio.paraEpochNanos(System.nanoTime());
        ConsultaSaudeRest fonte = new ConsultaSaudeRest(a.urls(), fabrica.criar(a.usuario(), a.senha()));
        AtomicLong anomalas = new AtomicLong();
        Object travaSaida = new Object();
        SondaTopologia sonda = new SondaTopologia(fonte, a.perfil().caches(), a.urls().size(),
                TimeUnit.MILLISECONDS.toNanos(a.periodoMs()), TimeUnit.MILLISECONDS.toNanos(a.prazoMs()), tempo,
                amostra -> {
                    if (amostra.anomala()) {
                        anomalas.incrementAndGet();
                    }
                    String linha = linhaAmostra(amostra);
                    synchronized (travaSaida) {
                        out.println(linha);
                        out.flush();
                    }
                });
        err.println("sonda-topologia " + a.perfil().descricao() + " nos=" + a.urls().size() + " periodo_ms="
                + a.periodoMs() + " prazo_ms=" + a.prazoMs() + " duracao_s=" + a.duracaoS());
        boolean interrompida = false;
        try {
            sonda.iniciar();
            Thread.sleep(TimeUnit.SECONDS.toMillis(a.duracaoS()));
        } catch (InterruptedException e) {
            interrompida = true;
        } finally {
            sonda.close();
        }
        synchronized (travaSaida) {
            out.println(linhaResumo(sonda.rodadas(), sonda.rodadasPuladas(), anomalas.get()));
            out.flush();
        }
        long cpuMs = ProcessHandle.current().info().totalCpuDuration().map(Duration::toMillis).orElse(-1L);
        err.println("custo pedidos_http=" + fonte.pedidos() + " rodadas=" + sonda.rodadas() + " cpu_jvm_ms="
                + cpuMs + " erros_ouvinte=" + sonda.errosOuvinte());
        if (interrompida) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    public static void main(String[] argv) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.exit(executar(argv, System.getenv(), out, System.err, ClienteAdminRest::new));
    }
}
