package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.commons.util.IntSets;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Sonda Hot Rod minima (B-33, RAST-G3-1/C-2 e lacuna "Hot Rod em isn3 durante a particao").
 * Conecta a <b>um unico no</b> (cliente pinado: {@link ClientIntelligence#BASIC}, um servidor,
 * timeouts de {@value #TIMEOUT_MS} ms e {@value #MAX_RETRIES} retentativa, mesma marshaller do
 * workload) e, a cada intervalo, executa {@code put}, {@code get}, {@code entrySet(IntSet)}
 * (segmentos 0..3, ate {@value #MAX_AMOSTRA} entradas) e {@code remove} numa chave de teste,
 * imprimindo uma linha CSV por operacao ({@link SondaCsv}), com a excecao completa quando falha.
 *
 * <p>Nao injeta falha, nao toca no cluster alem dessas operacoes e nao decide nada: so mede e
 * registra. Para cobrir dois nos, o script de coleta (g1b.sh) roda duas JVMs, uma por no.</p>
 *
 * <p>Uso: {@code java -cp session-workload.jar br.unipampa.tcc.session.SondaHotrod
 * --no isn3=127.0.0.1:11224 [--caches sessions,counters] [--intervalo-s 5] [--duracao-s N]
 * [--usuario admin]}; a senha vem da variavel de ambiente {@code SONDA_SENHA} (nunca da linha de
 * comando).</p>
 */
public final class SondaHotrod {

    public static final int TIMEOUT_MS = 2000;
    public static final int MAX_RETRIES = 1;
    static final int MAX_AMOSTRA = 10;
    static final int SEGMENTOS = 4;

    private SondaHotrod() { /* utilitario */ }

    /** Fatia minima de um cache remoto usada pela sonda; costura de teste, como {@link Armazem}. */
    interface Alvo {
        Object get(String chave);

        void put(String chave);

        void remove(String chave);

        /** {@code entrySet(IntSet)} pequeno; devolve quantas entradas leu (ate {@link #MAX_AMOSTRA}). */
        int amostra();

        default void fechar() { }
    }

    /** Abre o cliente (pode lancar). */
    interface Abridor {
        Alvo abrir();
    }

    /** Argumentos da linha de comando. */
    record Args(String rotulo, String host, int porta, List<String> caches, int intervaloS,
                int duracaoS, String usuario) {

        static Args parse(String[] a) {
            String no = null;
            List<String> caches = List.of("sessions");
            int intervalo = 5;
            int duracao = 0;
            String usuario = "admin";
            for (int i = 0; i < a.length; i += 2) {
                if (i + 1 >= a.length) {
                    throw new IllegalArgumentException("opcao sem valor: " + a[i]);
                }
                String v = a[i + 1];
                switch (a[i]) {
                    case "--no" -> no = v;
                    case "--caches" -> caches = List.of(v.split(","));
                    case "--intervalo-s" -> intervalo = Integer.parseInt(v);
                    case "--duracao-s" -> duracao = Integer.parseInt(v);
                    case "--usuario" -> usuario = v;
                    default -> throw new IllegalArgumentException("opcao desconhecida: " + a[i]);
                }
            }
            if (no == null) {
                throw new IllegalArgumentException("--no rotulo=host:porta e obrigatorio");
            }
            int eq = no.indexOf('=');
            int dp = no.lastIndexOf(':');
            if (eq <= 0 || dp < eq + 2 || dp == no.length() - 1) {
                throw new IllegalArgumentException("--no deve ser rotulo=host:porta: " + no);
            }
            return new Args(no.substring(0, eq), no.substring(eq + 1, dp),
                    Integer.parseInt(no.substring(dp + 1)), caches, intervalo, duracao, usuario);
        }
    }

    static String chaveDe(String no) {
        return "sonda-" + no;
    }

    /** Valor do tipo que o cache guarda, para que auditores e varreduras nao tropecem na chave de teste. */
    static Object valorDeTeste(String cache, String chave) {
        Instant agora = Instant.now();
        return switch (cache) {
            case "sessions" -> SessionState.criar(chave, "sonda", agora);
            case "counters" -> IdentityState.criar("sonda", agora);
            default -> throw new IllegalArgumentException("cache desconhecido para a sonda: " + cache);
        };
    }

    /** Mesma marshaller e allow-list do workload; um unico servidor; sem aprender topologia. */
    static Configuration configuracao(String host, int porta, String usuario, String senha) {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.addServer().host(host).port(porta);
        cb.security().authentication().enable().username(usuario).password(senha);
        cb.marshaller(new JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");
        cb.clientIntelligence(ClientIntelligence.BASIC);
        cb.socketTimeout(TIMEOUT_MS);
        cb.connectionTimeout(TIMEOUT_MS);
        cb.maxRetries(MAX_RETRIES);
        return cb.build();
    }

    /** Executa put, get, entrySet e remove; cada um vira uma linha, com ou sem excecao. */
    static void executarCiclo(String no, String cache, Alvo alvo, LongSupplier epochNs,
                              LongSupplier monoNs, Consumer<String> saida) {
        String chave = chaveDe(no);
        medir(no, cache, "put", epochNs, monoNs, saida, () -> alvo.put(chave));
        medir(no, cache, "get", epochNs, monoNs, saida, () -> alvo.get(chave));
        medir(no, cache, "entrySet", epochNs, monoNs, saida, alvo::amostra);
        medir(no, cache, "remove", epochNs, monoNs, saida, () -> alvo.remove(chave));
    }

    private static void medir(String no, String cache, String op, LongSupplier epochNs,
                              LongSupplier monoNs, Consumer<String> saida, Runnable acao) {
        long t = epochNs.getAsLong();
        long ini = monoNs.getAsLong();
        RuntimeException erro = null;
        try {
            acao.run();
        } catch (RuntimeException e) {
            erro = e;
        }
        saida.accept(SondaCsv.linha(t, no, cache, op, monoNs.getAsLong() - ini, erro));
    }

    /** Um no, um cache: mantem o cliente aberto entre ciclos e reabre se a abertura falhou. */
    static final class Sessao {
        private final String no;
        private final String cache;
        private final Abridor abridor;
        private final LongSupplier epochNs;
        private final LongSupplier monoNs;
        private final Consumer<String> saida;
        private Alvo alvo;

        Sessao(String no, String cache, Abridor abridor, LongSupplier epochNs, LongSupplier monoNs,
               Consumer<String> saida) {
            this.no = no;
            this.cache = cache;
            this.abridor = abridor;
            this.epochNs = epochNs;
            this.monoNs = monoNs;
            this.saida = saida;
        }

        void rodarUmCiclo() {
            if (alvo == null) {
                long t = epochNs.getAsLong();
                long ini = monoNs.getAsLong();
                try {
                    alvo = abridor.abrir();
                } catch (RuntimeException e) {
                    saida.accept(SondaCsv.linha(t, no, cache, "connect", monoNs.getAsLong() - ini, e));
                    return;
                }
            }
            executarCiclo(no, cache, alvo, epochNs, monoNs, saida);
        }

        /** Remocao final da chave de teste (se o ultimo remove falhou) e fechamento do cliente. */
        void encerrar() {
            if (alvo == null) {
                return;
            }
            Alvo a = alvo;
            medir(no, cache, "remove-final", epochNs, monoNs, saida, () -> a.remove(chaveDe(no)));
            try {
                a.fechar();
            } catch (RuntimeException ignorado) {
                // encerramento: nada a registrar alem da linha acima
            }
        }
    }

    /** Adaptador de producao sobre o {@link RemoteCache}. */
    static Alvo de(RemoteCacheManager rcm, String nomeCache) {
        RemoteCache<String, Object> cache = rcm.getCache(nomeCache);
        if (cache == null) {
            throw new IllegalStateException("cache inexistente no servidor: " + nomeCache);
        }
        return new Alvo() {
            @Override public Object get(String chave) { return cache.get(chave); }
            @Override public void put(String chave) { cache.put(chave, valorDeTeste(nomeCache, chave)); }
            @Override public void remove(String chave) { cache.remove(chave); }
            @Override public int amostra() {
                int n = 0;
                var conjunto = cache.entrySet(IntSets.immutableRangeSet(SEGMENTOS));
                try (var it = conjunto.iterator()) {
                    while (n < MAX_AMOSTRA && it.hasNext()) {
                        Map.Entry<String, Object> ignorada = it.next();
                        n++;
                    }
                }
                return n;
            }
            @Override public void fechar() { rcm.stop(); }
        };
    }

    public static void main(String[] argv) throws Exception {
        Args a = Args.parse(argv);
        String senha = System.getenv("SONDA_SENHA");
        if (senha == null || senha.isEmpty()) {
            throw new IllegalArgumentException("defina a variavel de ambiente SONDA_SENHA");
        }
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        // pareia parede e monotonico uma vez (mesma tecnica do EpochClock) para o timestamp em epoch-ns
        EpochClock relogio = EpochClock.capturarAgora();
        LongSupplier epoch = () -> relogio.paraEpochNanos(System.nanoTime());
        LongSupplier mono = System::nanoTime;

        List<Sessao> sessoes = new ArrayList<>();
        for (String c : a.caches()) {
            sessoes.add(new Sessao(a.rotulo(), c, () -> {
                RemoteCacheManager rcm = new RemoteCacheManager(configuracao(a.host(), a.porta(), a.usuario(), senha));
                try {
                    return de(rcm, c);
                } catch (RuntimeException e) {
                    rcm.stop();
                    throw e;
                }
            }, epoch, mono, out::println));
        }

        Thread principal = Thread.currentThread();
        boolean[] parar = {false};
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            parar[0] = true;
            principal.interrupt();
            try {
                principal.join(8000);
            } catch (InterruptedException ignorado) {
                Thread.currentThread().interrupt();
            }
        }));

        out.println(SondaCsv.CABECALHO);
        long fim = a.duracaoS() > 0 ? System.nanoTime() + a.duracaoS() * 1_000_000_000L : Long.MAX_VALUE;
        long proximo = System.nanoTime();
        try {
            while (!parar[0] && System.nanoTime() < fim) {
                for (Sessao s : sessoes) {
                    s.rodarUmCiclo();
                }
                proximo += a.intervaloS() * 1_000_000_000L;
                long espera = proximo - System.nanoTime();
                if (espera > 0) {
                    Thread.sleep(espera / 1_000_000L, (int) (espera % 1_000_000L));
                }
            }
        } catch (InterruptedException e) {
            // parada pedida (SIGTERM): segue para o encerramento
        } finally {
            for (Sessao s : sessoes) {
                s.encerrar();
            }
        }
    }
}
