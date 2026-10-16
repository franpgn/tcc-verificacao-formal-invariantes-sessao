package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.IntSet;
import org.infinispan.commons.util.IntSets;

import picocli.CommandLine;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
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
             LeitoresMultiReplica leitores = abrirLeitoresMultiReplica(cli, rcm)) {
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
            // B-33: marcadores de falha, rastro de cliente (Q-10) e parada da carga no fim da
            // falha; tudo desligado por padrao (sem as opcoes novas o fluxo e o de B-24).
            Marcadores marcadores = lerMarcadores(cli);
            RastroCliente rastro = (marcadores != null || cli.varredura) ? new RastroCliente() : null;
            SessionOps ops = new SessionOps(rcm, latency, auditor, eventos, relogio, rastro);
            BooleanSupplier parar = cli.paraNoFimDaFalha
                    ? new ParadaPorMarcador(Path.of(cli.marcadores))
                    : () -> false;

            System.out.printf(
                "Warm-up ate +%ds; medicao a partir de +%ds; total %ds%n",
                (politica.warmupAteMs() - inicioMs) / 1000L,
                (politica.medicaoInicioMs() - inicioMs) / 1000L,
                cli.duracaoSeg);

            AtomicLong totalOps = new AtomicLong();
            long fim = politica.medicaoFimMs();
            Supplier<Map<String, SessionState.Motivo>> invalidacoes = ops::invalidacoesConfirmadasSnapshot;

            if (cli.scenarioS3) {
                // B-33 (E2): cenario S3, tratado ANTES do enum Scenario (S1/S2 seguem o ramo abaixo).
                invalidacoes = executarS3(cli, rep, latency, auditor, eventos, relogio, rastro,
                        parar, fim, totalOps);
            } else {
            ExecutorService pool = Executors.newFixedThreadPool(cli.threads);
            long opsAlvoPorThread = cli.ops > 0 ? cli.ops / cli.threads : Long.MAX_VALUE;

            for (int t = 0; t < cli.threads; t++) {
                final int tid = t;
                final long seedThread = cli.seedBase + (long) tid + (long) rep * 1_000_003L;
                pool.submit(() -> rodarCliente(ops, tid, fim, cli.scenario,
                        seedThread, opsAlvoPorThread, totalOps, parar));
            }
            pool.shutdown();
            pool.awaitTermination(cli.duracaoSeg + 30, TimeUnit.SECONDS);
            }

            // B-24: checagem de I5/I6 (convergencia sob quiescencia) feita UMA VEZ,
            // logo apos as threads geradoras de carga pararem de emitir operacoes --
            // aproximacao mais direta de "quiescencia" disponivel sem W_quiesc
            // (janela deslizante), que fica para o controlador (B-26). Ver
            // docs/plano-consolidacao-b21-b23-b24-2026-10-02.md SS3.3.
            long fimCargaNs = relogio.paraEpochNanos(System.nanoTime());
            if (cli.convergencia) {
                // B-33: laco de convergencia substitui a rodada unica.
                marcadores = executarConvergencia(cli, rcm, leitores, auditor, relogio,
                        lerMarcadores(cli), fimCargaNs, rep);
            } else {
                auditor.rodadaUnica(leitores.leitoresSessao(), leitores.leitoresIdentidade());
                marcadores = lerMarcadores(cli);
            }
            if (cli.varredura) {
                executarVarredura(cli, rcm, invalidacoes, rastro, rep);
            }

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
                eventos.flush(destino, marcadores);
                System.out.printf(
                        "Eventos gravados em %s (registrados=%d, descartados_warmup=%d)%n",
                        destino, eventos.totalRegistrados(), eventos.descartadosWarmup());

                // B-23: persistencia das violacoes I1-I6 detectadas nesta repeticao.
                Path violacoesDestino = Path.of(cli.csvDir,
                        String.format("violacoes-rep-%03d.csv", rep));
                List<InvariantAuditor.Coluna> extras = rastro == null ? List.of()
                        : List.of(new InvariantAuditor.Coluna("classificacao",
                            v -> "I2".equals(v.invariante())
                                ? rastro.classificarViolacaoI2(v.chave(), InvariantAuditor.epochNs(v.em()))
                                : ""));
                auditor.dumpCsv(violacoesDestino, marcadores, extras);
                System.out.printf("Violacoes gravadas em %s (total=%d)%n",
                        violacoesDestino, auditor.total());
            }
        }
    }

    /**
     * B-33 (E2): executa o cenario S3 (simulacao de ciclo de vida de sessao) ate o fim da janela de
     * medicao: tres servidores de aplicacao pinados (um por no, BASIC) mais o console administrativo
     * (modo {@code pinned}), ou um cliente topology-aware (modo {@code topology}). Uma thread por
     * ator; {@code --threads} nao se aplica. Grava {@code s3-acoes-rep-NNN.csv} e
     * {@code s3-resumo-rep-NNN.json} em {@code --csv-dir}.
     *
     * @return o fornecedor das invalidacoes confirmadas por todos os atores (varredura I1/I4)
     */
    private static Supplier<Map<String, SessionState.Motivo>> executarS3(Cli cli, int rep,
            LatencyRegistry latency, InvariantAuditor auditor, EventCsvWriter eventos,
            EpochClock relogio, RastroCliente rastro, BooleanSupplier parar, long fimMs,
            AtomicLong totalOps) throws Exception {
        S3Registro registro = new S3Registro();
        RelogioS3 reloginho = RelogioS3.sistema();
        ContextoS3 ctx = new ContextoS3(reloginho, cli.s3, registro,
                cli.seedBase + (long) rep * 1_000_003L);
        try (S3Clientes clientes = S3Clientes.abrir(cli, latency, auditor, eventos, relogio, rastro)) {
            long inicioNs = reloginho.agoraNs();
            long fimNs = inicioNs + Math.max(0L, fimMs - System.currentTimeMillis()) * 1_000_000L;
            S3Simulacao sim = new S3Simulacao(ctx, clientes.servidoresApp(), clientes.admin(), inicioNs);
            System.out.printf("S3: %d usuarios (U), %d contas de limite (L), 1 admin; modo %s; admin-node %s%n",
                    cli.s3.usuarios, cli.s3.contasLimite, cli.s3.modoCliente, cli.s3.noAdmin);
            sim.rodarEmThreads(fimNs, parar);
            totalOps.set(registro.totalAcoes());
            if (cli.csvDir != null && !cli.csvDir.isBlank()) {
                registro.escreverCsv(Path.of(cli.csvDir, String.format("s3-acoes-rep-%03d.csv", rep)), relogio);
                java.util.Map<String, Object> par = new java.util.LinkedHashMap<>();
                par.put("modo_cliente", cli.s3.modoCliente.name());
                par.put("admin_node", cli.s3.noAdmin);
                par.put("usuarios", cli.s3.usuarios);
                par.put("contas_limite", cli.s3.contasLimite);
                par.put("semente", cli.seedBase + (long) rep * 1_000_003L);
                par.put("hotrod_timeout_ms", cli.s3.hotrodTimeoutMs);
                par.put("hotrod_retentativas", cli.s3.hotrodRetentativas);
                par.put("app_retentativas", cli.s3.appRetentativas);
                par.put("idle_timeout_sec", cli.s3.idleSeg);
                par.put("nota", "o resumo inclui o warm-up; acoes anteriores a medicao nao entram no CSV de eventos");
                registro.escreverResumo(Path.of(cli.csvDir, String.format("s3-resumo-rep-%03d.json", rep)), par);
            }
            registro.contadores().forEach((k, v) -> System.out.printf("S3 %s = %d%n", k, v));
            return clientes::invalidacoesSnapshot;
        }
    }

    /** B-33: le o arquivo de marcadores (ou {@code null} sem {@code --marker}) e fixa W_recovery. */
    private static Marcadores lerMarcadores(Cli cli) throws java.io.IOException {
        if (cli.marcadores == null || cli.marcadores.isBlank()) {
            return null;
        }
        return Marcadores.ler(Path.of(cli.marcadores))
                .comRecuperacao(cli.wRecoverySeg * 1_000_000_000L);
    }

    /**
     * B-33 (O-5): laco de convergencia. {@code t_fim} vem do marcador {@code end}; sem
     * ele usa o fim da carga (documentado no relatorio). Devolve os marcadores
     * completados com {@code t_convergencia} ou divergencia persistente.
     */
    private static Marcadores executarConvergencia(Cli cli, RemoteCacheManager rcm,
            LeitoresMultiReplica leitores, InvariantAuditor auditor, EpochClock relogio,
            Marcadores marcadores, long fimCargaNs, int rep) throws java.io.IOException {
        RemoteCache<String, SessionState> sessoesTopologia = rcm.getCache("sessions");
        int esperados = leitores.leitoresSessao().size();
        ConvergenciaLoop.SondaCluster sonda = () -> {
            HealthRest.Estado h = HealthRest.consultar(cli.restUrl, cli.usuario, cli.senha);
            int topologia = sessoesTopologia.getCacheTopologyInfo().getSegmentsPerServer().size();
            return new ConvergenciaLoop.EstadoCluster(Math.min(h.numeroDeNos(), topologia),
                    h.healthy("sessions"), h.healthy("counters"));
        };
        long tFimNs = marcadores != null && marcadores.fimNs() != null ? marcadores.fimNs() : fimCargaNs;
        ConvergenciaLoop laco = new ConvergenciaLoop(auditor,
                leitores.leitoresSessao(), leitores.leitoresIdentidade(), sonda, esperados,
                ConvergenciaLoop.Relogio.sistema(relogio),
                cli.convergenciaPeriodoSeg * 1_000_000_000L,
                cli.convergenciaTetoSeg * 1_000_000_000L, 3);
        ConvergenciaLoop.Resultado res = laco.executar(tFimNs);
        System.out.printf("Convergencia: convergiu=%s, persistente=%s, rodadas=%d, t_convergencia=%s ns%n",
                res.convergiu(), res.divergenciaPersistente(), res.rodadas().size(),
                res.tConvergenciaRelFimNs());
        if (cli.csvDir != null && !cli.csvDir.isBlank()) {
            ConvergenciaLoop.escreverCsv(
                    Path.of(cli.csvDir, String.format("convergencia-rep-%03d.csv", rep)), res);
            ConvergenciaLoop.escreverResumo(
                    Path.of(cli.csvDir, String.format("convergencia-resumo-rep-%03d.json", rep)),
                    res, tFimNs);
        }
        if (marcadores == null) {
            return null;
        }
        return res.convergiu() ? marcadores.comConvergencia(res.tConvergenciaAbsNs())
                               : marcadores.comDivergenciaPersistente();
    }

    /**
     * B-33: varredura final global (I1, I4, I2 global, bloqueio perdido). Le todas as chaves
     * dos dois caches pelo cliente topology-aware; se a leitura falhar (cache DEGRADED), a
     * varredura NAO e feita e o erro e registrado (nunca resultado parcial).
     */
    private static void executarVarredura(Cli cli, RemoteCacheManager rcm,
            Supplier<Map<String, SessionState.Motivo>> invalidacoes,
            RastroCliente rastro, int rep) throws java.io.IOException {
        try {
            Map<String, SessionState> sessoes = materializarTudo(rcm.getCache("sessions"));
            Map<String, IdentityState> identidades = materializarTudo(rcm.getCache("counters"));
            VarreduraFinal.Resultado r = VarreduraFinal.varrer(sessoes, identidades,
                    invalidacoes.get(), rastro, SessionOps.LIMITE_TENTATIVAS);
            System.out.println(r.resumo());
            if (cli.csvDir != null && !cli.csvDir.isBlank()) {
                VarreduraFinal.escreverAchados(
                        Path.of(cli.csvDir, String.format("varredura-rep-%03d.csv", rep)), r);
                VarreduraFinal.escreverIdentidades(
                        Path.of(cli.csvDir, String.format("varredura-identidades-rep-%03d.csv", rep)), r);
            }
        } catch (RuntimeException e) {
            System.err.println("VARREDURA NAO EXECUTADA (leitura global falhou): " + e);
            if (cli.csvDir != null && !cli.csvDir.isBlank()) {
                java.nio.file.Files.createDirectories(Path.of(cli.csvDir));
                java.nio.file.Files.writeString(
                        Path.of(cli.csvDir, String.format("varredura-rep-%03d.erro.txt", rep)),
                        String.valueOf(e) + System.lineSeparator());
            }
        }
    }

    private static <V> Map<String, V> materializarTudo(RemoteCache<String, V> cache) {
        Map<String, V> out = new HashMap<>();
        for (Map.Entry<String, V> e : cache.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * B-24 (ver docs/plano-consolidacao-b21-b23-b24-2026-10-02.md SS3.3, corrigido
     * conforme docs/validacao-b23-b24-2026-10-02.md SS2/SS4): abre um {@link
     * RemoteCacheManager} DEDICADO por servidor listado em {@code cli.servidores},
     * cada um com {@link ClientIntelligence#BASIC} -- "o cliente nao lida com
     * mudancas de topologia do servidor e portanto so usa a lista de servidores
     * fornecida na configuracao" (javadoc real de ClientIntelligence, confirmado
     * contra infinispan-client-hotrod-15.0.0.Final-sources.jar). Isso evita que o
     * cliente seja redirecionado pelo roteamento consciente de hash/topologia do
     * {@link RemoteCacheManager} padrao (usado por {@code ops}, que PRECISA desse
     * roteamento para desempenho).
     *
     * <p><b>P-19/correcao (ver docs/registro-de-problemas.md P-19 e P-21):</b>
     * {@code ClientIntelligence.BASIC} por si so NAO restringe
     * {@code RemoteCache#entrySet()} a dados locais do no conectado -- confirmado
     * empiricamente (200 chaves escritas, lidas via 3 managers BASIC distintos,
     * os tres devolvendo a contagem completa). A leitura correta usa
     * {@code RemoteCache#entrySet(IntSet)} RESTRITO aos segmentos que aquele
     * servidor efetivamente possui, obtidos via {@code getCacheTopologyInfo()
     * .getSegmentsPerServer()} de um cliente TOPOLOGY-AWARE (o {@code rcmPrincipal}
     * recebido aqui -- nunca de um cliente BASIC, que nao atualiza sua visao de
     * topologia, por definicao do proprio enum). Confirmado, contra o cluster real
     * sob uma falha F1 real, que essa combinacao reflete o estado LOCAL de cada no
     * (ver docs/validacao-b23-b24-2026-10-02.md SS2.3).</p>
     *
     * <p>A topologia e re-obtida A CADA CHAMADA do {@link Supplier} (dentro de
     * {@link #segmentosDoServidor}), nao capturada uma unica vez na abertura dos
     * managers -- a posse de segmentos muda apos um rebalance (ex.: apos um F1
     * real), entao um mapa capturado so no inicio da repeticao ficaria obsoleto
     * depois do primeiro evento de falha.</p>
     */
    private static LeitoresMultiReplica abrirLeitoresMultiReplica(Cli cli, RemoteCacheManager rcmPrincipal) {
        RemoteCache<String, SessionState> sessoesPrincipal = rcmPrincipal.getCache("sessions");
        RemoteCache<String, IdentityState> identidadesPrincipal = rcmPrincipal.getCache("counters");

        List<RemoteCacheManager> gerenciadores = new ArrayList<>();
        List<Supplier<Map<String, SessionState>>> leitoresSessao = new ArrayList<>();
        List<Supplier<Map<String, IdentityState>>> leitoresIdentidade = new ArrayList<>();
        for (String s : cli.servidores.split(",")) {
            String[] hp = s.split(":");
            int porta = Integer.parseInt(hp[1].trim());
            ConfigurationBuilder cb = new ConfigurationBuilder();
            cb.addServer().host(hp[0].trim()).port(porta);
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
            leitoresSessao.add(() ->
                    materializar(sessoesNo, segmentosDoServidor(sessoesPrincipal, porta)));
            leitoresIdentidade.add(() ->
                    materializar(identidadesNo, segmentosDoServidor(identidadesPrincipal, porta)));
        }
        return new LeitoresMultiReplica(gerenciadores, leitoresSessao, leitoresIdentidade);
    }

    /**
     * Resolve, a partir de um {@code cache} obtido de um cliente TOPOLOGY-AWARE, o
     * conjunto de segmentos que o servidor na {@code porta} dada efetivamente
     * possui agora. Casamento por porta (nao por {@link SocketAddress#toString()},
     * que inclui o host resolvido e pode nao bater byte-a-byte com a config) --
     * mesma abordagem validada em docs/validacao-b23-b24-2026-10-02.md SS2. Se a
     * porta nao aparecer no mapa (ex.: no momentaneamente fora do cluster, meio de
     * um F1), devolve um {@link IntSet} vazio -- a leitura correspondente naquele
     * ciclo fica vazia, o que e o comportamento correto (nao ha segmento para ler
     * de um no que a topologia atual nao reconhece).
     */
    private static IntSet segmentosDoServidor(RemoteCache<?, ?> cacheTopologyAware, int porta) {
        Map<SocketAddress, Set<Integer>> porServidor =
                cacheTopologyAware.getCacheTopologyInfo().getSegmentsPerServer();
        for (Map.Entry<SocketAddress, Set<Integer>> e : porServidor.entrySet()) {
            if (e.getKey() instanceof InetSocketAddress isa && isa.getPort() == porta) {
                return IntSets.from(e.getValue());
            }
        }
        return IntSets.immutableEmptySet();
    }

    private static <K, V> Map<K, V> materializar(RemoteCache<K, V> cache, IntSet segmentos) {
        Map<K, V> out = new HashMap<>();
        if (segmentos.isEmpty()) {
            return out;
        }
        for (Map.Entry<K, V> e : cache.entrySet(segmentos)) {
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
                                     long opsAlvo, AtomicLong totalOps,
                                     BooleanSupplier parar) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        KeyGenerator chaves = new KeyGenerator(seed);
        String identidade = "user-" + tid;
        long executadas = 0;
        while (System.currentTimeMillis() < deadline && executadas < opsAlvo
                && !parar.getAsBoolean()) {
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
