package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ponto de entrada do workload.
 *
 * <p>Conecta ao cluster Infinispan, executa operacoes concorrentes do
 * protocolo de sessao por um intervalo configurado e registra latencias
 * e violacoes. A configuracao de execucao vem da CLI ({@link Cli}),
 * parseada por Picocli. Para ajuda completa, execute o jar com
 * {@code --help}.</p>
 *
 * <p>Cobertura na Tabela 1 do Cap. 3 secao 3.3.5: a CLI parametriza
 * T7 (cenarios), T9 (operacoes), T10 (repeticoes), T11 (warm-up),
 * T13 (CSV de latencia), T14 (duracao); KeyGenerator cobre T5, T6, T8;
 * Scenario cobre T7; WarmupPolicy cobre T11.</p>
 */
public final class WorkloadMain {

    private WorkloadMain() { /* utility */ }

    public static void main(String[] args) {
        Cli cli = new Cli();
        CommandLine cmd = new CommandLine(cli);
        try {
            CommandLine.ParseResult pr = cmd.parseArgs(args);
            if (CommandLine.printHelpIfRequested(pr)) {
                return;
            }
        } catch (CommandLine.ParameterException e) {
            System.err.println(e.getMessage());
            cmd.usage(System.err);
            System.exit(2);
        }
        try {
            cli.validar();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuracao invalida: " + e.getMessage());
            System.exit(2);
        }
        if (cli.dryRun) {
            System.out.println("dry-run, configuracao OK: " + cli);
            return;
        }
        try {
            executar(cli);
        } catch (Exception e) {
            System.err.println("Falha na execucao: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(3);
        }
    }

    /**
     * Executa o número de repetições configuradas; cada repetição abre
     * seu próprio {@link RemoteCacheManager}, sua {@link WarmupPolicy}
     * e seu {@link LatencyRegistry}.
     */
    static void executar(Cli cli) throws Exception {
        System.out.println("Configuracao: " + cli);

        for (int rep = 1; rep <= cli.repeticoes; rep++) {
            System.out.printf("== Repeticao %d / %d ==%n", rep, cli.repeticoes);
            executarRepeticao(cli, rep);
        }
    }

    static void executarRepeticao(Cli cli, int rep) throws Exception {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        for (String s : cli.servidores.split(",")) {
            String[] hp = s.split(":");
            cb.addServer().host(hp[0].trim()).port(Integer.parseInt(hp[1].trim()));
        }
        cb.security().authentication()
                .enable()
                .username(cli.usuario)
                .password(cli.senha);

        // Os valores armazenados (SessionState, IdentityState) sao POJOs
        // Serializable. O marshaller padrao do Hot Rod (ProtoStream) exige
        // schema registrado e, sem ele, todo put de POJO falha com erro de
        // marshalling (visto como ERROR_TRANSPORT). Usa-se o marshaller de
        // serializacao Java, com a allow-list restrita ao pacote do modelo.
        cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");

        try (RemoteCacheManager rcm = new RemoteCacheManager(cb.build());
             LeitoresMultiReplica leitores = abrirLeitoresMultiReplica(cli)) {
            long inicioMs = System.currentTimeMillis();
            WarmupPolicy politica = WarmupPolicy.padrao(
                    inicioMs, cli.duracaoSeg, cli.warmupMinimoSeg,
                    java.time.Clock.systemUTC());
            LatencyRegistry latency = new LatencyRegistry(politica);
            InvariantAuditor auditor = new InvariantAuditor();
            // Âncora epoch-ns capturada no início da repetição (T2): pareia
            // Instant.now() (parede) com System.nanoTime() (monotônico) para
            // converter cada evento monotônico em epoch-ns no CSV.
            EpochClock relogio = EpochClock.capturarAgora();
            EventCsvWriter eventos = new EventCsvWriter(politica);
            SessionOps ops = new SessionOps(rcm, latency, auditor, eventos, relogio);

            System.out.printf(
                "Warm-up ate +%ds; medicao a partir de +%ds; total %ds%n",
                (politica.warmupAteMs() - inicioMs) / 1000L,
                (politica.medicaoInicioMs() - inicioMs) / 1000L,
                cli.duracaoSeg);

            ExecutorService pool = Executors.newFixedThreadPool(cli.threads);
            AtomicLong totalOps = new AtomicLong();
            long opsAlvoPorThread = cli.ops > 0 ? cli.ops / cli.threads : Long.MAX_VALUE;
            long fim = politica.medicaoFimMs();

            for (int t = 0; t < cli.threads; t++) {
                final int tid = t;
                final long seedThread = cli.seedBase + (long) tid + (long) rep * 1_000_003L;
                pool.submit(() -> rodarCliente(ops, tid, fim, cli.scenario,
                        seedThread, opsAlvoPorThread, totalOps));
            }
            pool.shutdown();
            pool.awaitTermination(cli.duracaoSeg + 30, TimeUnit.SECONDS);

            // B-24: checagem de I5/I6 (convergencia sob quiescencia) feita UMA VEZ,
            // logo apos as threads geradoras de carga pararem de emitir operacoes --
            // aproximacao mais direta de "quiescencia" disponivel sem W_quiesc
            // (janela deslizante), que fica para o controlador (B-26). Ver
            // docs/plano-consolidacao-b21-b23-b24-2026-10-02.md SS3.3.
            auditor.rodadaUnica(leitores.leitoresSessao(), leitores.leitoresIdentidade());

            System.out.printf("Total de operacoes: %d%n", totalOps.get());
            System.out.println("Violacoes detectadas: " + auditor.total());
            latency.snapshot().forEach((op, h) ->
                System.out.printf(
                        "%-22s n=%d (descartados=%d) p50=%dns p95=%dns p99=%dns p999=%dns%n",
                        op,
                        h.getTotalCount(),
                        latency.descartados(op),
                        h.getValueAtPercentile(50.0),
                        h.getValueAtPercentile(95.0),
                        h.getValueAtPercentile(99.0),
                        h.getValueAtPercentile(99.9))
            );

            if (cli.csvDir != null && !cli.csvDir.isBlank()) {
                Path destino = Path.of(cli.csvDir,
                        String.format("rep-%03d.csv", rep));
                eventos.flush(destino);
                System.out.printf(
                        "Eventos gravados em %s (registrados=%d, descartados_warmup=%d)%n",
                        destino, eventos.totalRegistrados(), eventos.descartadosWarmup());

                // B-23: persistencia das violacoes I1-I6 detectadas nesta repeticao.
                Path violacoesDestino = Path.of(cli.csvDir,
                        String.format("violacoes-rep-%03d.csv", rep));
                auditor.dumpCsv(violacoesDestino);
                System.out.printf("Violacoes gravadas em %s (total=%d)%n",
                        violacoesDestino, auditor.total());
            }
        }
    }

    /**
     * B-24 (ver docs/plano-consolidacao-b21-b23-b24-2026-10-02.md SS3.3): abre um
     * {@link RemoteCacheManager} DEDICADO por servidor listado em {@code
     * cli.servidores}, cada um com {@link ClientIntelligence#BASIC} -- "o cliente
     * nao lida com mudancas de topologia do servidor e portanto so usa a lista de
     * servidores fornecida na configuracao" (javadoc real de ClientIntelligence,
     * confirmado contra infinispan-client-hotrod-15.0.0.Final-sources.jar nesta
     * sessao). Isso evita que o cliente seja redirecionado pelo roteamento
     * consciente de hash/topologia do {@link RemoteCacheManager} padrao (usado por
     * {@code ops}, que PRECISA desse roteamento para desempenho).
     *
     * <p><b>LIMITACAO CONFIRMADA EMPIRICAMENTE (nao implementar em torno dela sem
     * validacao do Senior/Gestor -- ver docs/registro-de-problemas.md P-19):</b>
     * {@code ClientIntelligence.BASIC} so afeta QUAL servidor recebe o comando
     * (impede redirecionamento automatico para o owner real em operacoes de
     * chave unica); NAO restringe {@code RemoteCache#entrySet()}/iteracao em
     * massa a dados LOCAIS do no conectado. Testado diretamente contra o
     * cluster real desta sessao (200 chaves escritas via RCM principal,
     * lidas via 3 RemoteCacheManager BASIC distintos, um por porta/no): os
     * TRES retornaram a mesma contagem completa (200/200), nao uma fracao
     * local por no. Ou seja, a iteracao em massa do Hot Rod/Infinispan 15
     * coordena o cluster inteiro no lado do SERVIDOR independentemente da
     * inteligencia do cliente -- {@code entrySet()} sempre devolve a visao
     * JA CONVERGIDA do cluster, nunca a visao local/potencialmente
     * divergente de um no especifico. Consequencia pratica: a leitura
     * abaixo esta corretamente conectada (nao lanca excecao, funciona contra
     * o cluster real de 3 nos) e permanece util como INFRAESTRUTURA para
     * I5/I6, mas NAO cumpre o objetivo original de "ler o que a replica X
     * enxerga agora" -- uma divergencia real entre replicas tende a nao ser
     * detectada por este mecanismo mesmo quando existe. Ver achado completo,
     * reproducao e alternativas candidatas (iteracao restrita a segmentos
     * locais, ou outra API) em docs/registro-de-problemas.md P-19 --
     * redesenho fica para rodada futura, fora do escopo desta.</p>
     *
     * <p>Cada {@link Supplier} materializa {@code RemoteCache#entrySet()} (mesmo
     * metodo ja usado por {@link SessionOps#block}) num {@link HashMap} simples a
     * cada chamada -- sem cache local, sempre uma leitura fresca no momento em que
     * {@link InvariantAuditor#rodadaUnica} for invocado.</p>
     */
    private static LeitoresMultiReplica abrirLeitoresMultiReplica(Cli cli) {
        List<RemoteCacheManager> gerenciadores = new ArrayList<>();
        List<Supplier<Map<String, SessionState>>> leitoresSessao = new ArrayList<>();
        List<Supplier<Map<String, IdentityState>>> leitoresIdentidade = new ArrayList<>();
        for (String s : cli.servidores.split(",")) {
            String[] hp = s.split(":");
            ConfigurationBuilder cb = new ConfigurationBuilder();
            cb.addServer().host(hp[0].trim()).port(Integer.parseInt(hp[1].trim()));
            cb.clientIntelligence(ClientIntelligence.BASIC);
            cb.security().authentication()
                    .enable()
                    .username(cli.usuario)
                    .password(cli.senha);
            cb.marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
            cb.addJavaSerialAllowList("br.unipampa.tcc.session.*", "java.time.*");

            RemoteCacheManager rcmNo = new RemoteCacheManager(cb.build());
            gerenciadores.add(rcmNo);
            RemoteCache<String, SessionState> sessoesNo = rcmNo.getCache("sessions");
            RemoteCache<String, IdentityState> identidadesNo = rcmNo.getCache("counters");
            leitoresSessao.add(() -> materializar(sessoesNo));
            leitoresIdentidade.add(() -> materializar(identidadesNo));
        }
        return new LeitoresMultiReplica(gerenciadores, leitoresSessao, leitoresIdentidade);
    }

    private static <K, V> Map<K, V> materializar(RemoteCache<K, V> cache) {
        Map<K, V> out = new HashMap<>();
        for (Map.Entry<K, V> e : cache.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Agrupa os {@link RemoteCacheManager} dedicados por no (B-24) com os
     * {@link Supplier}s que leem cada um, para fechamento conjunto via
     * try-with-resources em {@link #executarRepeticao}.
     */
    private record LeitoresMultiReplica(
            List<RemoteCacheManager> gerenciadores,
            List<Supplier<Map<String, SessionState>>> leitoresSessao,
            List<Supplier<Map<String, IdentityState>>> leitoresIdentidade)
            implements AutoCloseable {
        @Override
        public void close() {
            for (RemoteCacheManager g : gerenciadores) {
                try {
                    g.close();
                } catch (RuntimeException ignored) {
                    // encerramento best-effort; nao deve mascarar o resultado da repeticao
                }
            }
        }
    }

    /**
     * Loop de geração de carga para uma thread; termina quando o tempo
     * limite for atingido ou quando a thread tiver gerado seu alvo de
     * operações ({@link Cli#ops} dividido pelo número de threads).
     */
    private static void rodarCliente(SessionOps ops, int tid, long deadline,
                                     Scenario cenario, long seed,
                                     long opsAlvo, AtomicLong totalOps) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        KeyGenerator chaves = new KeyGenerator(seed);
        String identidade = "user-" + tid;
        long executadas = 0;
        while (System.currentTimeMillis() < deadline && executadas < opsAlvo) {
            String sid = chaves.nextSessionKey();
            try {
                switch (cenario.proximaOperacao(r)) {
                    case VALIDATE -> ops.validate(sid);
                    case LOGIN_VALIDATE_LOGOUT -> {
                        OpResult login = ops.login(identidade, "cred");
                        if (login.code() == OpResult.Code.OK_LOGIN) {
                            String novoSid = login.sessionId();
                            ops.validate(novoSid);
                            ops.logout(novoSid);
                        }
                    }
                    case LOGOUT -> ops.logout(sid);
                    case INCREMENT_FAILURE -> ops.incrementFailure(identidade);
                    case RESET_FAILURES -> ops.resetFailures(identidade);
                }
                executadas++;
                totalOps.incrementAndGet();
            } catch (Exception e) {
                // erro de transporte; conta tentativa mas não interrompe.
                executadas++;
                totalOps.incrementAndGet();
            }
        }
    }
}
