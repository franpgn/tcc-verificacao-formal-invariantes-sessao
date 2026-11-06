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
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    /**
     * Ponto de entrada. Codigos de saida:
     * <ul>
     *   <li>{@code 0}: execucao concluida (ou {@code --help}/{@code --dry-run});</li>
     *   <li>{@code 2}: parametro invalido (Picocli ou {@link Cli#validar()});</li>
     *   <li>{@code 3}: falha de execucao (qualquer outra excecao);</li>
     *   <li>{@code 4}: reservado para a guarda de sanidade da mistura (B-43.3a, parte ii; ainda
     *       nao emitido);</li>
     *   <li>{@code 5}: o reset de estado (B-43.1, {@code --reset-state on}) nao provou que os caches
     *       do perfil ficaram vazios ({@link EstadoNaoLimpoException}); a repeticao corrente nao
     *       grava {@code rep-NNN.csv} e as seguintes nao rodam. B-43.2 (P-c): o mesmo codigo quando a
     *       carga inicial da mistura v2 nao chega a {@code size() == universo}
     *       ({@link CargaInicialException}).</li>
     * </ul>
     */
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
        // W-3: com --controller shadow|active, os arquivos do controlador sao lidos e conferidos aqui
        // (conteudo invalido = exit 2), inclusive no --dry-run, que mostra parametros e SHA-256.
        String dryRunControlador = null;
        if (cli.controlador != ModoControlador.OFF) {
            try {
                dryRunControlador = LigacaoControlador.textoDryRun(LigacaoControlador.carregar(cli));
            } catch (IllegalArgumentException | java.io.IOException e) {
                System.err.println("Configuracao invalida do controlador: " + e.getMessage());
                System.exit(2);
            }
        }
        if (cli.dryRun) {
            System.out.println(cli.textoDryRunComMistura());
            if (dryRunControlador != null) {
                System.out.println(dryRunControlador);
            }
            return;
        }
        try {
            executar(cli);
        } catch (EstadoNaoLimpoException e) {
            System.err.println((e instanceof CargaInicialException
                    ? "Carga inicial falhou (mix=" + cli.mix.rotulo() + "): " : "Reset de estado falhou (reset_state=on): ")
                    + e.getMessage());
            System.exit(codigoDeSaida(e));
        } catch (Exception e) {
            System.err.println("Falha na execucao: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(codigoDeSaida(e));
        }
    }

    /** Codigo de saida da execucao para {@code t} (ver {@link #main}). */
    static int codigoDeSaida(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof EstadoNaoLimpoException) {
                return 5;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return 3;
    }

    /**
     * B-43.1: prepara o estado dos caches do perfil antes do <i>warm-up</i> da repeticao
     * {@code rep}. Com {@code --reset-state on}, executa o {@link ResetEstado} (prazo 10 s,
     * intervalo 100 ms), imprime a linha {@code Reset:} e, com {@code --csv-dir}, grava
     * {@code estado-rep-NNN.json}; na falha grava o JSON com {@code FALHA} e relanca. Com
     * {@code off} nao faz nenhuma operacao nos caches e devolve {@code null}.
     */
    static ResetEstado.Resultado prepararEstado(Cli cli, List<CacheLimpavel> caches, int rep)
            throws EstadoNaoLimpoException, java.io.IOException {
        return prepararEstado(cli, caches, rep, ResetEstado.PRAZO_PADRAO, ResetEstado.INTERVALO_PADRAO,
                System::nanoTime, ResetEstado.Sono.real());
    }

    /** Como {@link #prepararEstado(Cli, List, int)}, com prazo, relogio e espera injetaveis (testes). */
    static ResetEstado.Resultado prepararEstado(Cli cli, List<CacheLimpavel> caches, int rep,
            java.time.Duration prazo, java.time.Duration intervalo,
            java.util.function.LongSupplier relogioNs, ResetEstado.Sono sono)
            throws EstadoNaoLimpoException, java.io.IOException {
        if (!cli.resetEstado) {
            System.out.println("Reset: reset_state=off");
            gravarEstado(cli, rep, null, null, null);
            return null;
        }
        try {
            ResetEstado.Resultado r = ResetEstado.executar(caches, prazo, intervalo, relogioNs, sono);
            System.out.printf("Reset: reset_state=on caches=%s antes=%s depois=%s ms=%d%n",
                    String.join(",", r.caches()), juntar(r.tamanhosAntes()), juntar(r.tamanhosDepois()),
                    r.duracaoMs());
            gravarEstado(cli, rep, r, "OK", null);
            return r;
        } catch (EstadoNaoLimpoException e) {
            gravarEstado(cli, rep, e.resultado(), "FALHA", e.getMessage());
            throw e;
        }
    }

    /**
     * B-43.2: carga inicial da mistura v2 antes do <i>warm-up</i> da repeticao {@code rep}. Com
     * {@code --mix v2} em S1/S2 executa a {@link CargaInicial} (universo do {@link KeyGenerator},
     * lote 1000, prazo 10 s, intervalo 100 ms), imprime a linha {@code Carga:} e, com
     * {@code --csv-dir}, grava {@code carga-rep-NNN.json}; na falha grava o JSON com {@code FALHA} e
     * relanca. Com {@code v1} ou em S3 nao faz nada (nem linha, nem JSON) e devolve {@code null}.
     */
    static CargaInicial.Resultado prepararCarga(Cli cli, CacheCarregavel sessoes, int rep)
            throws CargaInicialException, java.io.IOException {
        return prepararCarga(cli, sessoes, rep, KeyGenerator.TAMANHO_PADRAO_UNIVERSO,
                CargaInicial.PRAZO_PADRAO, CargaInicial.INTERVALO_PADRAO,
                System::nanoTime, ResetEstado.Sono.real());
    }

    /** Como {@link #prepararCarga(Cli, CacheCarregavel, int)}, com universo, prazo, relogio e espera injetaveis. */
    static CargaInicial.Resultado prepararCarga(Cli cli, CacheCarregavel sessoes, int rep, int universo,
            java.time.Duration prazo, java.time.Duration intervalo,
            java.util.function.LongSupplier relogioNs, ResetEstado.Sono sono)
            throws CargaInicialException, java.io.IOException {
        if (cli.mix != VersaoMistura.V2 || cli.scenarioS3) {
            return null;
        }
        String nome = sessoes.nome();
        try {
            CargaInicial.Resultado r = CargaInicial.carregar(sessoes, universo, cli.threads,
                    java.time.Instant.now(), CargaInicial.LOTE_PADRAO, prazo, intervalo, relogioNs, sono);
            System.out.printf("Carga: mix=%s cache=%s sids=%d lotes=%d ms=%d%n",
                    cli.mix.rotulo(), nome, r.universo(), r.lotes(), r.duracaoMs());
            gravarCarga(cli, rep, nome, r, "OK", null);
            return r;
        } catch (CargaInicialException e) {
            gravarCarga(cli, rep, nome, e.carga(), "FALHA", e.getMessage());
            throw e;
        }
    }

    /**
     * B-43.7: carga dos slots da mistura v3 antes do <i>warm-up</i> da repeticao {@code rep}. Com
     * {@code --mix v3} em S1/S2 executa a {@link CargaSlots} (universo do {@link KeyGenerator}, lote
     * 1000, prazo 10 s, intervalo 100 ms), imprime a linha {@code Carga:}, grava
     * {@code carga-rep-NNN.json} (com {@code --csv-dir}) e devolve o {@link SlotsGeracao} da repeticao
     * (geracoes em 1). Na falha grava o JSON com {@code FALHA} e relanca (saida 5). Com v1/v2 ou em S3
     * nao faz nada e devolve {@code null}.
     */
    static SlotsGeracao prepararCargaSlots(Cli cli, CacheCarregavel sessoes, int rep)
            throws CargaInicialException, java.io.IOException {
        return prepararCargaSlots(cli, sessoes, rep, KeyGenerator.TAMANHO_PADRAO_UNIVERSO,
                CargaInicial.PRAZO_PADRAO, CargaInicial.INTERVALO_PADRAO,
                System::nanoTime, ResetEstado.Sono.real());
    }

    /** Como {@link #prepararCargaSlots(Cli, CacheCarregavel, int)}, com universo, prazo, relogio e espera injetaveis. */
    static SlotsGeracao prepararCargaSlots(Cli cli, CacheCarregavel sessoes, int rep, int universo,
            java.time.Duration prazo, java.time.Duration intervalo,
            java.util.function.LongSupplier relogioNs, ResetEstado.Sono sono)
            throws CargaInicialException, java.io.IOException {
        if (cli.mix != VersaoMistura.V3 || cli.scenarioS3) {
            return null;
        }
        String nome = sessoes.nome();
        try {
            CargaInicial.Resultado r = CargaSlots.carregar(sessoes, universo, cli.threads,
                    java.time.Instant.now(), CargaInicial.LOTE_PADRAO, prazo, intervalo, relogioNs, sono);
            System.out.printf("Carga: mix=%s cache=%s slots=%d entradas=%d lotes=%d ms=%d%n",
                    cli.mix.rotulo(), nome, r.universo(), CargaSlots.ENTRADAS_POR_SLOT * r.universo(),
                    r.lotes(), r.duracaoMs());
            gravarCargaSlots(cli, rep, nome, universo, r, "OK", null);
            return new SlotsGeracao(universo, cli.threads);
        } catch (CargaInicialException e) {
            gravarCargaSlots(cli, rep, nome, universo, e.carga(), "FALHA", e.getMessage());
            throw e;
        }
    }

    /** B-43.7: {@code carga-rep-NNN.json} da v3 (nada sem {@code --csv-dir}). */
    private static void gravarCargaSlots(Cli cli, int rep, String cache, int universo, CargaInicial.Resultado r,
                                         String veredito, String motivo) throws java.io.IOException {
        if (cli.csvDir == null || cli.csvDir.isBlank()) {
            return;
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("mix", cli.mix.rotulo());
        m.put("replicacao", cli.replicacao.rotulo());
        m.put("cache", cache);
        m.put("universo", universo);
        m.put("entradas_por_slot", CargaSlots.ENTRADAS_POR_SLOT);
        m.put("entradas", (long) CargaSlots.ENTRADAS_POR_SLOT * universo);
        m.put("tamanho_final", r != null ? r.tamanhoFinal() : null);
        m.put("lotes", r != null ? r.lotes() : null);
        m.put("duracao_ms", r != null ? r.duracaoMs() : null);
        m.put("q", MisturaV3.Q);
        m.put("veredito", veredito);
        m.put("motivo", motivo);
        Path destino = Path.of(cli.csvDir, String.format("carga-rep-%03d.json", rep));
        java.nio.file.Files.createDirectories(destino.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(destino.toFile(), m);
    }

    /** Grava {@code carga-rep-NNN.json} em {@code --csv-dir} (nada sem {@code --csv-dir}). */
    private static void gravarCarga(Cli cli, int rep, String cache, CargaInicial.Resultado r,
                                    String veredito, String motivo) throws java.io.IOException {
        if (cli.csvDir == null || cli.csvDir.isBlank()) {
            return;
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("mix", cli.mix.rotulo());
        m.put("replicacao", cli.replicacao.rotulo());
        m.put("cache", cache);
        m.put("universo", r != null ? r.universo() : null);
        m.put("tamanho_final", r != null ? r.tamanhoFinal() : null);
        m.put("lotes", r != null ? r.lotes() : null);
        m.put("duracao_ms", r != null ? r.duracaoMs() : null);
        m.put("veredito", veredito);
        m.put("motivo", motivo);
        Path destino = Path.of(cli.csvDir, String.format("carga-rep-%03d.json", rep));
        java.nio.file.Files.createDirectories(destino.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(destino.toFile(), m);
    }

    private static String juntar(List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (Integer v : l) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        return sb.toString();
    }

    /** Grava {@code estado-rep-NNN.json} em {@code --csv-dir} (nada sem {@code --csv-dir}). */
    private static void gravarEstado(Cli cli, int rep, ResetEstado.Resultado r, String veredito,
                                     String motivo) throws java.io.IOException {
        if (cli.csvDir == null || cli.csvDir.isBlank()) {
            return;
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("reset_state", cli.rotuloResetEstado());
        boolean on = cli.resetEstado;
        m.put("replicacao", on ? cli.replicacao.rotulo() : null);
        m.put("caches", on && r != null ? r.caches() : null);
        m.put("tamanhos_antes", on && r != null ? r.tamanhosAntes() : null);
        m.put("tamanhos_depois", on && r != null ? r.tamanhosDepois() : null);
        m.put("duracao_ms", on && r != null ? r.duracaoMs() : null);
        m.put("veredito", veredito);
        m.put("motivo", motivo);
        Path destino = Path.of(cli.csvDir, String.format("estado-rep-%03d.json", rep));
        java.nio.file.Files.createDirectories(destino.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(destino.toFile(), m);
    }

    /**
     * Executa o número de repetições configuradas; cada repetição abre
     * seu próprio {@link RemoteCacheManager}, sua {@link WarmupPolicy}
     * e seu {@link LatencyRegistry}.
     */
    static void executar(Cli cli) throws Exception {
        System.out.println("Configuracao: " + cli);
        System.out.println("Replicacao: " + cli.replicacao.descricao());
        System.out.println("Mistura: mix=" + cli.rotuloMistura() + (cli.scenarioS3 ? " (sem efeito em S3)" : ""));
        // B-43.4: versao do contrato da coluna return_code (tambem em meta-csv-rep-NNN.json).
        System.out.println(MetaCsv.linha());

        for (int rep = 1; rep <= cli.repeticoes; rep++) {
            System.out.printf("== Repeticao %d / %d ==%n", rep, cli.repeticoes);
            executarRepeticao(cli, rep);
        }
    }

    static void executarRepeticao(Cli cli, int rep) throws Exception {
        // W-2b (P-5, item 3; P-46): o ISPN004007 e o ISPN004005 do cliente Hot Rod saem inteiros uma vez por
        // (no, motivo) e sao contados; a contagem desta repeticao vai as linhas "Hot Rod ISPN004007:" e
        // "Hot Rod ISPN004005:" no finally do try do rcm, tambem na falha
        ContencaoLogHotRod contencao = ContencaoLogHotRod.instalar();
        contencao.reiniciar();
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
            // B-43.1: esvazia os caches do perfil ANTES do warm-up (nao consome warm-up). Na falha
            // lanca EstadoNaoLimpoException: sem rep-NNN.csv, saida 5, repeticoes seguintes nao rodam.
            prepararEstado(cli, List.of(
                    CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheSessoes())),
                    CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))), rep);
            // B-43.2: carga inicial dos sids VALID (so --mix v2 em S1/S2) depois do reset e ANTES do
            // warm-up: fora da janela de medicao e fora do CSV. Na falha, saida 5 (CargaInicialException).
            prepararCarga(cli, CacheCarregavel.de(rcm.getCache(cli.replicacao.cacheSessoes())), rep);
            // B-43.7: carga dos slots da v3 (geracao 0 INVALID, geracao 1 VALID; so --mix v3 em S1/S2),
            // no mesmo ponto: depois do reset e ANTES do warm-up. Na falha, saida 5 (CargaInicialException).
            SlotsGeracao slots = prepararCargaSlots(cli, CacheCarregavel.de(rcm.getCache(cli.replicacao.cacheSessoes())), rep);
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
            SessionOps ops = new SessionOps(rcm, cli.replicacao, latency, auditor, eventos, relogio, rastro);
            // B-43.2 (P-d): desfechos reais de Validate na janela de medicao (so stdout).
            DesfechosValidate desfechos = new DesfechosValidate(politica);
            ops.definirDesfechosValidate(desfechos);
            // B-43.7: contadores da v3 e das reposicoes na mesma janela de medicao.
            ops.definirPoliticaReposicao(politica);
            // B-43.9: contadores do auditor (Auditor:, Auditor I2:, Login:) na mesma janela de medicao.
            ops.definirPoliticaAuditor(politica);
            MisturaV3 misturaV3 = slots != null ? new MisturaV3(slots, politica) : null;
            BooleanSupplier parar = cli.paraNoFimDaFalha
                    ? new ParadaPorMarcador(Path.of(cli.marcadores))
                    : () -> false;
            // W-3 (B-28.2): laco de controle do cluster; com --controller off, null e nada mais muda.
            // Origem das janelas = inicio da medicao (o warm-up fica fora do controlador).
            long origemControleNs = relogio.paraEpochNanos(System.nanoTime())
                    + (politica.medicaoInicioMs() - System.currentTimeMillis()) * 1_000_000L;
            LigacaoControlador controle = ligarControlador(cli,
                    cfg -> LigacaoControlador.abrir(cfg, cli, rep, relogio, origemControleNs, rastro));
            if (controle != null) {
                ops.definirOuvinteDeEvento(controle.ouvinteDeEvento());
                auditor.definirOuvinte(controle.ouvinteDeViolacao());
            }

            System.out.printf(
                "Warm-up ate +%ds; medicao a partir de +%ds; total %ds%n",
                (politica.warmupAteMs() - inicioMs) / 1000L,
                (politica.medicaoInicioMs() - inicioMs) / 1000L,
                cli.duracaoSeg);

            AtomicLong totalOps = new AtomicLong();
            long fim = politica.medicaoFimMs();
            Supplier<Map<String, SessionState.Motivo>> invalidacoes = ops::invalidacoesConfirmadasSnapshot;
            // B-43.6: instancias cujos contadores de corrida do auditor entram na linha "Auditor:"
            // (S1/S2: o SessionOps unico; S3: todas as que compartilham o mapa, preenchidas abaixo).
            List<SessionOps> instanciasAuditor = new ArrayList<>(List.of(ops));

            // W-3: a carga roda dentro de executarCargaComControle: no fim (ou numa excecao), o instante do
            // fim da carga e lido ANTES de o controlador encerrar (V-W3, R1) e o controlador desfaz toda acao
            // e fecha os JSONL antes da convergencia.
            final Supplier<Map<String, SessionState.Motivo>> invalidacoesS1S2 = invalidacoes;
            CargaEncerrada<Supplier<Map<String, SessionState.Motivo>>> cargaEncerrada = executarCargaComControle(() -> {
            if (cli.scenarioS3) {
                // B-33 (E2): cenario S3, tratado ANTES do enum Scenario (S1/S2 seguem o ramo abaixo).
                return executarS3(cli, rep, latency, auditor, eventos, relogio, rastro,
                        parar, fim, totalOps, instanciasAuditor, politica, controle);
            }
            ExecutorService pool = Executors.newFixedThreadPool(cli.threads);
            long opsAlvoPorThread = cli.ops > 0 ? cli.ops / cli.threads : Long.MAX_VALUE;

            for (int t = 0; t < cli.threads; t++) {
                final int tid = t;
                final long seedThread = sementeThread(cli.seedBase, tid, rep);
                pool.submit(() -> rodarCliente(ops, tid, fim, cli.scenario,
                        seedThread, opsAlvoPorThread, totalOps, parar, cli.mix, misturaV3));
            }
            pool.shutdown();
            pool.awaitTermination(cli.duracaoSeg + 30, TimeUnit.SECONDS);
            return invalidacoesS1S2;
            }, () -> relogio.paraEpochNanos(System.nanoTime()), controle, () -> auditor.definirOuvinte(null));
            invalidacoes = cargaEncerrada.resultado();

            // B-24: checagem de I5/I6 (convergencia sob quiescencia) feita UMA VEZ,
            // logo apos as threads geradoras de carga pararem de emitir operacoes --
            // aproximacao mais direta de "quiescencia" disponivel sem W_quiesc
            // (janela deslizante), que fica para o controlador (B-26). Ver
            // docs/plano-consolidacao-b21-b23-b24-2026-10-02.md SS3.3.
            long fimCargaNs = cargaEncerrada.fimCargaNs();
            Long fimControleNs = cargaEncerrada.fimControleNs();
            // W-6 (P-6 da V-W5): com o controlador, a linha de fim da carga vai ao stdout com o alvo do AC-5 aplicado
            // (lido do executor, em memoria). O orquestrador le o stdout por um pipe e religa o no parado; o laco de
            // convergencia espera a topologia completa antes de comecar. Sem controlador, nada muda.
            String ac5Alvo = null;
            if (controle != null) {
                ac5Alvo = controle.ac5AplicadaAlvo();
                System.out.println(Religamento.linhaSinal(rep, fimCargaNs, ac5Alvo));
                System.out.flush();
            }
            Long tSinalNs = controle == null ? null : relogio.paraEpochNanos(System.nanoTime());
            if (cli.convergencia) {
                // B-33: laco de convergencia substitui a rodada unica.
                marcadores = executarConvergencia(cli, rcm, leitores, auditor, relogio,
                        lerMarcadores(cli), fimCargaNs, fimControleNs, rep, tSinalNs, ac5Alvo);
            } else {
                // P-48: os segmentos de cada replica saem da topologia do principal; atualiza antes de ler
                atualizarTopologiaOuAvisar(rcm, cli, "rodada unica");
                auditor.rodadaUnica(leitores.leitoresSessao(), leitores.leitoresIdentidade());
                marcadores = lerMarcadores(cli);
            }
            if (cli.scenarioS3) {
                // P-41: resultado da ultima leitura de cada replica (com prazo sem resposta = --hotrod-timeout-ms)
                System.out.println(linhaLeituraPorReplica(cli.s3.hotrodTimeoutMs, leitores.comPrazo()));
            }
            if (cli.varredura) {
                executarVarredura(cli, rcm, invalidacoes, rastro, rep);
            }

            System.out.printf("Total de operacoes: %d%n", totalOps.get());
            System.out.println("Violacoes detectadas: " + auditor.total());
            System.out.println(SessionOps.linhaAuditor(instanciasAuditor));
            System.out.println(SessionOps.linhaInvalidacao(instanciasAuditor));
            // B-43.9: linhas NOVAS (Auditor: e Invalidacao: sao fixadas inteiras por teste).
            System.out.println(SessionOps.linhaAuditorI2(instanciasAuditor));
            System.out.println(SessionOps.linhaAuditorTotal(instanciasAuditor));
            System.out.println(SessionOps.linhaLogin(instanciasAuditor));
            if (!cli.scenarioS3) {
                System.out.println(desfechos.linha());
            }
            if (misturaV3 != null) {
                System.out.println(misturaV3.linha(ops));
            }
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
                MetaCsv.gravar(cli.csvDir, rep);
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
        } finally {
            // W-2b: depois de fechados o rcm e os leitores (os clientes do S3 e da sonda fecham dentro do try)
            System.out.println(contencao.linhasResumo());
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
            AtomicLong totalOps, List<SessionOps> instanciasAuditor, WarmupPolicy politica,
            LigacaoControlador controle) throws Exception {
        S3Registro registro = new S3Registro();
        RelogioS3 reloginho = RelogioS3.sistema();
        ContextoS3 ctx = new ContextoS3(reloginho, cli.s3, registro,
                cli.seedBase + (long) rep * 1_000_003L);
        try (S3Clientes clientes = S3Clientes.abrir(cli, latency, auditor, eventos, relogio, rastro)) {
            long inicioNs = reloginho.agoraNs();
            long fimNs = inicioNs + Math.max(0L, fimMs - System.currentTimeMillis()) * 1_000_000L;
            // B-43.6: a linha "Auditor:" soma os servidores de aplicacao e o admin (mapa compartilhado).
            instanciasAuditor.clear();
            instanciasAuditor.addAll(clientes.instancias());
            // B-43.9: contadores do auditor na janela de medicao, em todas as instancias do S3.
            for (SessionOps o : clientes.instancias()) {
                o.definirPoliticaAuditor(politica);
                // W-3: eventos dos servidores de aplicacao e do admin vao ao laco de controle.
                if (controle != null) {
                    o.definirOuvinteDeEvento(controle.ouvinteDeEvento());
                }
            }
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

    /** W-3: a carga de uma repeticao (S1/S2 ou S3), devolvendo o fornecedor das invalidacoes confirmadas. */
    @FunctionalInterface
    interface Carga<T> {
        T executar() throws Exception;
    }

    /** W-3: o que o fim da carga precisa do controlador ({@link LigacaoControlador} em producao). */
    interface ControleDaCarga {
        /** Encerra o controlador: desfaz toda acao e fecha os JSONL. */
        void close();

        /** Linha {@code Controlador:} do stdout, depois do {@link #close()}. */
        String linhaResumo();

        /**
         * W-6 (P-6 da V-W5): alvo do AC-5 que ficou APLICADA na execucao, ou {@code null}; depois do
         * {@link #close()}. Sem controlador de verdade, nenhum.
         */
        default String ac5AplicadaAlvo() {
            return null;
        }
    }

    /**
     * Resultado da carga e os dois instantes do fim.
     *
     * @param fimCargaNs    instante em que a carga parou, lido antes do encerramento do controlador
     * @param fimControleNs instante em que o controlador terminou de encerrar; {@code null} sem controlador
     */
    record CargaEncerrada<T>(T resultado, long fimCargaNs, Long fimControleNs) {
    }

    /**
     * W-3 (V-W3, R1 e N1): roda a carga e, no {@code finally} (fim normal ou excecao), le o instante do fim
     * da carga ANTES de encerrar o controlador; depois desliga os ouvintes, encerra o controlador (em active
     * isso desfaz a pilha e pode levar ate {@code T_enc}) e le o instante do fim do controlador. Uma excecao
     * da carga e relancada sem mudar, depois do encerramento.
     *
     * @param controle         {@code null} com {@code --controller off}
     * @param desligarOuvintes chamado antes do {@code close} (so com controlador)
     */
    static <T> CargaEncerrada<T> executarCargaComControle(Carga<T> carga, java.util.function.LongSupplier agoraNs,
            ControleDaCarga controle, Runnable desligarOuvintes) throws Exception {
        T resultado;
        long fimCargaNs;
        Long fimControleNs = null;
        try {
            resultado = carga.executar();
        } finally {
            fimCargaNs = agoraNs.getAsLong();
            if (controle != null) {
                desligarOuvintes.run();
                controle.close();
                fimControleNs = agoraNs.getAsLong();
                System.out.println(controle.linhaResumo());
            }
        }
        return new CargaEncerrada<>(resultado, fimCargaNs, fimControleNs);
    }

    /** W-3: abre o controlador a partir da configuracao ja conferida (injetavel nos testes). */
    @FunctionalInterface
    interface AberturaControlador {
        LigacaoControlador abrir(LigacaoControlador.Configuracao cfg) throws java.io.IOException;
    }

    /**
     * W-3 (B-28.2, item 7): com {@code --controller off} devolve {@code null} sem ler arquivo nem chamar
     * {@code abertura} (nenhum objeto do pacote {@code controle} e criado); senao carrega a configuracao e
     * abre o controlador.
     */
    static LigacaoControlador ligarControlador(Cli cli, AberturaControlador abertura) throws java.io.IOException {
        if (cli.controlador == ModoControlador.OFF) {
            return null;
        }
        return abertura.abrir(LigacaoControlador.carregar(cli));
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
            Marcadores marcadores, long fimCargaNs, Long fimControleNs, int rep, Long tSinalNs, String ac5Alvo)
            throws java.io.IOException {
        RemoteCache<String, SessionState> sessoesTopologia = rcm.getCache(cli.replicacao.cacheSessoes());
        int esperados = leitores.leitoresSessao().size();
        // W-2b (P-45): a saude vem pelo HTTP em memoria (Digest), sem um processo curl por rodada
        ClienteAdminRest clienteSaude = new ClienteAdminRest(cli.usuario, cli.senha);
        ConvergenciaLoop.SondaCluster sonda = () -> {
            HealthRest.Estado h = HealthRest.consultar(clienteSaude, cli.restUrl, HealthRest.PRAZO_CONSULTA_NS);
            int topologia = sessoesTopologia.getCacheTopologyInfo().getSegmentsPerServer().size();
            return new ConvergenciaLoop.EstadoCluster(Math.min(h.numeroDeNos(), topologia),
                    h.healthy(cli.replicacao.cacheSessoes()), h.healthy(cli.replicacao.cacheContadores()));
        };
        // P-48 (smoke de 06/11): depois de um no reiniciar (F1, religamento do AC-5), a topologia do principal fica a da
        // abertura, e segmentosDoServidor escolhia por ela os segmentos lidos de cada replica (13 a 33 % das chaves de
        // cada no no lugar errado). Cada rodada do laco atualiza antes a topologia (V-W6 5.4: containsKey de uma chave
        // que nao existe em cada cache lido); uma falha sai pela sonda e a rodada fica invalida. A espera do
        // religamento continua com a sonda de antes: quem decide e a REST, e t_religamento_ns (origem do Delta_conv do
        // braco B) nao muda de significado.
        AtualizacaoTopologia atualizacao = AtualizacaoTopologia.doPrincipal(rcm,
                List.of(cli.replicacao.cacheSessoes(), cli.replicacao.cacheContadores()), System.out::println);
        ConvergenciaLoop.SondaCluster sondaLaco = () -> {
            atualizacao.atualizar();
            return sonda.ler();
        };
        long tFimNs = marcadores != null && marcadores.fimNs() != null ? marcadores.fimNs() : fimCargaNs;
        // W-6 (P-6 da V-W5): com AC-5 aplicado, espera a topologia completa (o orquestrador religou o no ao ler a
        // linha de fim da carga) antes do laco; sem ele, nenhuma espera. Sem controlador (tSinalNs nulo), nada.
        Religamento.Resultado religamento = null;
        if (tSinalNs != null) {
            religamento = ac5Alvo == null ? Religamento.Resultado.semEspera(tSinalNs)
                    : Religamento.esperar(sonda, esperados, ConvergenciaLoop.Relogio.sistema(relogio), ac5Alvo,
                            tSinalNs, cli.religamentoPrazoSeg * 1_000_000_000L, Religamento.PASSO_NS);
            if (ac5Alvo != null) {
                System.out.println(religamento.linha());
            }
        }
        ConvergenciaLoop laco = new ConvergenciaLoop(auditor,
                leitores.leitoresSessao(), leitores.leitoresIdentidade(), sondaLaco, esperados,
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
            Path resumo = Path.of(cli.csvDir, String.format("convergencia-resumo-rep-%03d.json", rep));
            if (religamento == null) {
                ConvergenciaLoop.escreverResumo(resumo,
                        res, tFimNs, fimCargaNs, fimControleNs);
            } else {
                ConvergenciaLoop.escreverResumo(resumo,
                        res, tFimNs, fimCargaNs, fimControleNs, religamento);
            }
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
        // P-48: a leitura global tambem vai pelos donos que a topologia do principal indica; atualiza antes
        atualizarTopologiaOuAvisar(rcm, cli, "varredura");
        try {
            Map<String, SessionState> sessoes = materializarTudo(rcm.getCache(cli.replicacao.cacheSessoes()));
            Map<String, IdentityState> identidades = materializarTudo(rcm.getCache(cli.replicacao.cacheContadores()));
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

    /**
     * P-48: atualiza a topologia do principal antes de uma leitura que nao e rodada do laco de convergencia (rodada
     * unica, varredura). Ali nao ha rodada para invalidar: a falha vira aviso no stdout e a leitura segue com a
     * topologia que o cliente tem (no S1/S2 o principal opera durante a carga e ja a tem atualizada).
     */
    private static void atualizarTopologiaOuAvisar(RemoteCacheManager rcm, Cli cli, String onde) {
        try {
            AtualizacaoTopologia.doPrincipal(rcm,
                    List.of(cli.replicacao.cacheSessoes(), cli.replicacao.cacheContadores()), System.out::println)
                    .atualizar();
        } catch (IllegalStateException e) {
            System.out.println("AVISO: " + e.getMessage() + " antes da " + onde
                    + "; leitura com a topologia que o cliente tem");
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
        RemoteCache<String, SessionState> sessoesPrincipal = rcmPrincipal.getCache(cli.replicacao.cacheSessoes());
        RemoteCache<String, IdentityState> identidadesPrincipal = rcmPrincipal.getCache(cli.replicacao.cacheContadores());

        List<RemoteCacheManager> gerenciadores = new ArrayList<>();
        List<Supplier<Map<String, SessionState>>> leitoresSessao = new ArrayList<>();
        List<Supplier<Map<String, IdentityState>>> leitoresIdentidade = new ArrayList<>();
        List<LeituraReplicaComPrazo<?>> comPrazo = new ArrayList<>();
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
            RemoteCache<String, SessionState> sessoesNo = rcmNo.getCache(cli.replicacao.cacheSessoes());
            RemoteCache<String, IdentityState> identidadesNo = rcmNo.getCache(cli.replicacao.cacheContadores());
            // P-41: em S3, prazo sem resposta = --hotrod-timeout-ms (o no parado pelo AC-5 nao trava a leitura final)
            String no = hp[0].trim() + ":" + porta;
            leitoresSessao.add(leitorDaReplica(cli.scenarioS3, cli.s3.hotrodTimeoutMs, "sessoes@" + no,
                    batida -> materializar(sessoesNo, segmentosDoServidor(sessoesPrincipal, porta), batida),
                    comPrazo));
            leitoresIdentidade.add(leitorDaReplica(cli.scenarioS3, cli.s3.hotrodTimeoutMs, "contadores@" + no,
                    batida -> materializar(identidadesNo, segmentosDoServidor(identidadesPrincipal, porta), batida),
                    comPrazo));
        }
        return new LeitoresMultiReplica(gerenciadores, leitoresSessao, leitoresIdentidade, comPrazo);
    }

    /** Leitura sem aviso de entrada (fora do S3 ninguem mede o prazo). */
    private static final Runnable SEM_BATIDA = () -> { };

    /**
     * P-41: o leitor de uma replica. Em S3 ({@code s3}), {@link LeituraReplicaComPrazo} com prazo sem resposta =
     * {@code prazoMs} ({@code --hotrod-timeout-ms}, o mesmo prazo de leitura da sonda S_lag), registrado em
     * {@code comPrazo} para a linha da saida; fora do S3, a leitura de antes, sem prazo e na thread de quem chama (as
     * campanhas S1/S2 nao mudam).
     */
    static <T> Supplier<Map<String, T>> leitorDaReplica(boolean s3, long prazoMs, String rotulo,
            Function<Runnable, Map<String, T>> leitura, List<LeituraReplicaComPrazo<?>> comPrazo) {
        if (!s3) {
            return () -> leitura.apply(SEM_BATIDA);
        }
        LeituraReplicaComPrazo<T> l = new LeituraReplicaComPrazo<>(rotulo, prazoMs, leitura);
        comPrazo.add(l);
        return l;
    }

    /**
     * P-41: linha da saida com a ultima leitura de cada replica, na ordem de {@code --servers} (sessoes e contadores de
     * cada no): {@code Leitura por replica: prazo_sem_resposta_ms=<ms> <cache>@<host:porta>=<resultado> ...}, com o
     * resultado de {@link LeituraReplicaComPrazo#ultimoResultado()}.
     */
    static String linhaLeituraPorReplica(long prazoMs, List<LeituraReplicaComPrazo<?>> leituras) {
        StringBuilder sb = new StringBuilder("Leitura por replica: prazo_sem_resposta_ms=").append(prazoMs);
        for (LeituraReplicaComPrazo<?> l : leituras) {
            sb.append(' ').append(l.rotulo()).append('=').append(l.ultimoResultado());
        }
        return sb.toString();
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

    /** Le as entradas dos {@code segmentos}; {@code batida} e chamada a cada entrada recebida (P-41). */
    private static <K, V> Map<K, V> materializar(RemoteCache<K, V> cache, IntSet segmentos, Runnable batida) {
        Map<K, V> out = new HashMap<>();
        if (segmentos.isEmpty()) {
            return out;
        }
        for (Map.Entry<K, V> e : cache.entrySet(segmentos)) {
            batida.run();
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
            List<Supplier<Map<String, IdentityState>>> leitoresIdentidade,
            List<LeituraReplicaComPrazo<?>> comPrazo)
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
     * B-43.7b: semente da thread {@code tid} na repeticao {@code rep} (S1/S2), a mesma formula de
     * sempre: {@code seedBase + tid + rep * 1_000_003}. Alimenta o {@link KeyGenerator} da thread e,
     * com sal e misturador, o sorteio do token antigo ({@code MisturaV3.sementeToken}, DT-008) e, desde o
     * B-43.5, o {@code Random} da mistura ({@link #sementeMistura(long)}, sal {@link #SAL_MISTURA}).
     */
    static long sementeThread(long seedBase, int tid, int rep) {
        return seedBase + (long) tid + (long) rep * 1_000_003L;
    }

    /** B-43.5: sal da semente do {@code Random} da mistura: "MISTURAS" em ASCII. */
    static final long SAL_MISTURA = 0x4D49535455524153L;

    /**
     * B-43.5: semente do {@code Random} da mistura de uma thread:
     * {@code mix64(sementeThread ^ SAL_MISTURA)} (funcao pura; dominio separado do
     * {@link KeyGenerator}, semeado com a propria {@code sementeThread}, e do token antigo,
     * {@code MisturaV3.sementeToken}).
     *
     * @param sementeThread a semente da thread ({@link #sementeThread(long, int, int)})
     */
    static long sementeMistura(long sementeThread) {
        return MisturaV3.mix64(sementeThread ^ SAL_MISTURA);
    }

    /**
     * B-43.5: {@code Random} da mistura de uma thread (sorteia o tipo de cada operacao).
     *
     * @param sementeThread a semente da thread ({@link #sementeThread(long, int, int)})
     */
    static Random randomDaMistura(long sementeThread) {
        return new Random(sementeMistura(sementeThread));
    }

    /**
     * Loop de geração de carga para uma thread; termina quando o tempo
     * limite for atingido ou quando a thread tiver gerado seu alvo de
     * operações ({@link Cli#ops} dividido pelo número de threads).
     * B-43.2: as identidades vêm de {@link IdentidadesMistura} conforme {@code --mix}.
     * B-43.5: {@code seed} e a semente da thread ({@link #sementeThread(long, int, int)}); dela saem, por
     * dominios separados, o {@code Random} da mistura ({@link #randomDaMistura(long)}), o
     * {@link KeyGenerator} e o sorteio do token antigo da v3. Mesma {@code seed}, mesma sequencia de
     * intencoes da thread (tipos de operacao, chaves, tokens antigos e identidades de Login).
     * B-27.3: sem portao de emissao (K1..K4 retirados; o controlador do TCC age no <i>cluster</i>):
     * o laco e o de antes do B-27.
     */
    private static void rodarCliente(SessionOps ops, int tid, long deadline,
                                     Scenario cenario, long seed,
                                     long opsAlvo, AtomicLong totalOps,
                                     BooleanSupplier parar, VersaoMistura mix, MisturaV3 misturaV3) {
        Random r = randomDaMistura(seed);
        KeyGenerator chaves = new KeyGenerator(seed);
        IdentidadesMistura ids = new IdentidadesMistura(tid, mix);
        // B-43.7/B-43.7b: estado por thread da v3 (Random proprio do token antigo, semeado pela
        // semente da thread, DT-008); null em v1/v2.
        MisturaV3.Cliente v3 = misturaV3 != null ? misturaV3.cliente(seed) : null;
        long executadas = 0;
        while (System.currentTimeMillis() < deadline && executadas < opsAlvo
                && !parar.getAsBoolean()) {
            executarOperacao(ops, cenario, r, chaves, ids, v3);
            executadas++;
            totalOps.incrementAndGet();
        }
    }

    /**
     * B-43.2: executa {@code n} operacoes da mistura (testes chamam com {@code new Random(42)} e
     * dubles; a producao usa {@link #executarOperacao} dentro do laco de {@link #rodarCliente}).
     */
    static void executarOperacoes(SessionOps ops, Scenario cenario, Random r, KeyGenerator chaves,
                                  IdentidadesMistura ids, long n) {
        executarOperacoes(ops, cenario, r, chaves, ids, n, null);
    }

    /** B-43.7: como a anterior; com {@code v3 != null}, Validate/Logout isolados da mistura v3. */
    static void executarOperacoes(SessionOps ops, Scenario cenario, Random r, KeyGenerator chaves,
                                  IdentidadesMistura ids, long n, MisturaV3.Cliente v3) {
        for (long i = 0; i < n; i++) {
            executarOperacao(ops, cenario, r, chaves, ids, v3);
        }
    }

    /**
     * Uma operacao da mistura: sorteia a chave Zipf (sempre, como antes) e a operacao; Login usa
     * {@code ids.login()}, IncrementFailure/ResetFailures usam {@code ids.limite()}. O rodizio de
     * identidades nao consome {@code r} nem {@code chaves}.
     */
    static void executarOperacao(SessionOps ops, Scenario cenario, Random r, KeyGenerator chaves,
                                 IdentidadesMistura ids) {
        executarOperacao(ops, cenario, r, chaves, ids, null);
    }

    /**
     * B-43.7: com {@code v3 == null}, a operacao de v1/v2 (chave Zipf crua). Com {@code v3}, o
     * {@code sid} Zipf da o slot {@code k}: o Validate isolado le a geracao corrente do slot (ou a
     * anterior, por sorteio de Bernoulli p = 0,05 com Random proprio por thread, DT-008) e o Logout
     * isolado segue {@link #logoutV3}. Triade, IncrementFailure e ResetFailures como v2. Nada disso
     * consome {@code r} nem {@code chaves} alem do sorteio de sempre.
     */
    static void executarOperacao(SessionOps ops, Scenario cenario, Random r, KeyGenerator chaves,
                                 IdentidadesMistura ids, MisturaV3.Cliente v3) {
        String sid = chaves.nextSessionKey();
        try {
            switch (cenario.proximaOperacao(r)) {
                case VALIDATE -> {
                    if (v3 == null) {
                        ops.validate(sid);
                    } else {
                        boolean antigo = v3.proximoEhTokenAntigo();
                        validateV3(ops, v3, SlotsGeracao.slotDe(sid), antigo);
                    }
                }
                case LOGIN_VALIDATE_LOGOUT -> {
                    OpResult login = ops.login(ids.login(), "cred");
                    if (login.code() == OpResult.Code.OK_LOGIN) {
                        String novoSid = login.sessionId();
                        ops.validate(novoSid);
                        ops.logout(novoSid);
                    }
                }
                case LOGOUT -> {
                    if (v3 == null) {
                        ops.logout(sid);
                    } else {
                        logoutV3(ops, v3, SlotsGeracao.slotDe(sid));
                    }
                }
                case INCREMENT_FAILURE -> ops.incrementFailure(ids.limite());
                case RESET_FAILURES -> ops.resetFailures(ids.limite());
            }
        } catch (Exception e) {
            // erro de transporte; conta tentativa (no chamador) mas não interrompe.
        }
    }

    /**
     * B-43.7: Validate isolado da mistura v3: le a geracao corrente do slot {@code k} ou, com
     * {@code antigo}, a anterior (token antigo, decidido por sorteio de Bernoulli p = 0,05 com Random
     * proprio por thread, DT-008; a geracao 0 ja nasce INVALID na carga). Le
     * {@code geracao[k]} sem trava: um Validate concorrente com o Logout do mesmo slot pode ler a
     * geracao que esta sendo invalidada (INVALID legitimo, contado nos desfechos).
     */
    static OpResult validateV3(SessionOps ops, MisturaV3.Cliente v3, int k, boolean antigo) {
        int g = v3.slots().geracao(k);
        OpResult r = ops.validate(SlotsGeracao.chave(k, antigo ? g - 1 : g));
        v3.contarValidate(antigo);
        return r;
    }

    /**
     * B-43.7: Logout isolado da mistura v3, sob a trava da faixa do slot (Logouts no mesmo slot nao
     * sao concorrentes entre si; artefato declarado do workload). Ordem: le a geracao {@code g};
     * invalida {@code chave(k,g)} (medido, CSV); reposicao VALID de {@code chave(k,g+1)} (fora do
     * CSV); so depois avanca a geracao, para que um Validate que veja {@code g+1} encontre a sessao ja
     * gravada. Logout em {@code ERROR_*}: sem reposicao nem avanco ({@code reposicao_omitida}).
     * Reposicao que falha: sem avanco (os Validate seguem na geracao {@code g}, ja invalidada, ate o
     * proximo Logout do slot).
     */
    static void logoutV3(SessionOps ops, MisturaV3.Cliente v3, int k) {
        SlotsGeracao slots = v3.slots();
        synchronized (slots.trava(k)) {
            int g = slots.geracao(k);
            OpResult r = ops.logout(SlotsGeracao.chave(k, g));
            boolean erro = r.code() == OpResult.Code.ERROR_TRANSPORT
                    || r.code() == OpResult.Code.ERROR_UNAVAILABLE;
            if (erro) {
                v3.contarLogout(true);
                return;
            }
            boolean reposta = ops.reporSessao(SlotsGeracao.chave(k, g + 1), slots.identidade(k));
            if (reposta) {
                slots.definirGeracao(k, g + 1);
            }
            v3.contarLogout(false);
        }
    }
}
