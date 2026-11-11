package br.unipampa.tcc.session;

import br.unipampa.tcc.session.CapturasReais.Captura;
import br.unipampa.tcc.session.OpResult.Code;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-2 da validacao V-E1 (item rastreavel RAST-G3-1): o {@link ClassificadorDeErro} e o
 * {@link HealthRest} contra o que o cliente Hot Rod e o REST do Infinispan 15.0.0.Final
 * realmente devolveram na captura da sonda ({@link SondaHotrod}) durante a particao do G-4
 * (REP=2 e REP=3, 2026-10-16). As fixtures estao em {@link CapturasReais} (CSV da sonda, copiado
 * sem alteracao) e em {@code src/test/resources/captura-real/} (JSON de saude).
 *
 * <p>Limites das capturas: sonda com {@code maxRetries=1} (o experimento usa 3) e chave unica
 * ({@code sonda-isn3}); a mensagem do ISPN000306 traz a chave em {@code WrappedByteArray[...]}.</p>
 */
class ClassificadorMensagemRealTest {

    // ---------------- reconstrucao da cadeia a partir das colunas do CSV da sonda ----------------

    /**
     * Reconstroi a excecao de uma linha do CSV: topo (coluna {@code excecao_classe}/{@code mensagem})
     * mais as causas na sintaxe de {@code SondaCsv.cadeiaDeCausas} ({@code classe: mensagem}
     * separadas por {@code " | "}; so a classe quando a mensagem e nula). Vazia = sem causas.
     */
    static RuntimeException reconstruirDoCsv(String classeTopo, String mensagemTopo, String causas) {
        List<String> classes = new ArrayList<>();
        List<String> mensagens = new ArrayList<>();
        classes.add(classeTopo);
        mensagens.add(mensagemTopo);
        if (!causas.isEmpty()) {
            for (String parte : causas.split(" \\| ")) {
                int i = parte.indexOf(": ");
                classes.add(i < 0 ? parte : parte.substring(0, i));
                mensagens.add(i < 0 ? null : parte.substring(i + 2));
            }
        }
        return reconstruir(classes, mensagens);
    }

    /**
     * Reconstroi uma cadeia de excecoes a partir de nomes de classe e mensagens (topo primeiro).
     * Cada classe precisa ser instanciavel por {@code (String,Throwable)}, {@code (String,Throwable,
     * SocketAddress)} ou {@code (String)}; se nao for, falha com mensagem clara.
     */
    static RuntimeException reconstruir(List<String> classes, List<String> mensagens) {
        assertEquals(classes.size(), mensagens.size(), "classes e mensagens devem ter o mesmo tamanho");
        Throwable causa = null;
        for (int i = classes.size() - 1; i >= 0; i--) {
            causa = instanciar(classes.get(i), mensagens.get(i), causa);
        }
        return causa instanceof RuntimeException r ? r : new RuntimeException(causa);
    }

    private static Throwable instanciar(String classe, String mensagem, Throwable causa) {
        try {
            Class<?> c = Class.forName(classe);
            if (causa != null) {
                try {
                    return (Throwable) c.getConstructor(String.class, Throwable.class).newInstance(mensagem, causa);
                } catch (NoSuchMethodException semDois) {
                    try {
                        return (Throwable) c.getConstructor(String.class, Throwable.class, SocketAddress.class)
                                .newInstance(mensagem, causa, null);
                    } catch (NoSuchMethodException semTres) {
                        Throwable t = (Throwable) c.getConstructor(String.class).newInstance(mensagem);
                        t.initCause(causa);
                        return t;
                    }
                }
            }
            return (Throwable) c.getConstructor(String.class).newInstance(mensagem);
        } catch (ReflectiveOperationException | IllegalStateException e) {
            throw new AssertionError("classe capturada nao reconstruivel: " + classe
                    + " (use construtor (String[,Throwable]) ou uma subclasse de teste)", e);
        }
    }

    // ------------------------------ o proprio reconstruir ------------------------------

    @Test
    void reconstruirMontaACadeiaNaOrdemDoCsv() {
        RuntimeException e = reconstruir(
                List.of(HotRodClientException.class.getName(), IOException.class.getName(),
                        SocketTimeoutException.class.getName()),
                List.of("topo", "meio", "Read timed out"));
        assertEquals("topo", e.getMessage());
        assertTrue(e.getCause() instanceof IOException);
        assertEquals("meio", e.getCause().getMessage());
        assertTrue(e.getCause().getCause() instanceof SocketTimeoutException);
    }

    @Test
    void reconstruirRejeitaClasseInexistenteComMensagemClara() {
        AssertionError erro = assertThrows(AssertionError.class,
                () -> reconstruir(List.of("nao.Existe"), List.of("x")));
        assertTrue(erro.getMessage().contains("nao.Existe"), erro.getMessage());
    }

    // ------------------------------ capturas reais (parametrizado) ------------------------------

    static Stream<Arguments> capturas() {
        return CapturasReais.TODAS.stream().map(c -> Arguments.of(c));
    }

    static Stream<Arguments> capturasPorTipo(boolean indisponibilidade) {
        return CapturasReais.TODAS.stream()
                .filter(c -> (c.esperado() == Code.ERROR_UNAVAILABLE) == indisponibilidade)
                .map(c -> Arguments.of(c));
    }

    static Stream<Arguments> capturasDeIndisponibilidade() { return capturasPorTipo(true); }

    /** As de particao (AvailabilityException); a recusa do cache ignorado (P1-IC) tem teste proprio. */
    static Stream<Arguments> capturasDeParticao() {
        return CapturasReais.TODAS.stream()
                .filter(c -> c.esperado() == Code.ERROR_UNAVAILABLE && c.mensagem().contains("AvailabilityException"))
                .map(c -> Arguments.of(c));
    }

    static Stream<Arguments> capturasDeTransporte() { return capturasPorTipo(false); }

    @Test
    @DisplayName("o conjunto cobre os dois caches, as quatro operacoes e as tres fases da captura")
    void conjuntoDeFixturesCobreCachesOperacoesEFases() {
        for (String cache : List.of("sessions", "counters")) {
            for (String op : List.of("put", "get", "remove", "entrySet")) {
                for (Code code : List.of(Code.ERROR_TRANSPORT, Code.ERROR_UNAVAILABLE)) {
                    assertTrue(CapturasReais.TODAS.stream().anyMatch(c -> c.cache().equals(cache)
                                    && c.operacao().equals(op) && c.esperado() == code
                                    && c.rep().equals("REP3")),
                            "falta fixture REP3 de " + cache + "/" + op + " -> " + code);
                }
            }
        }
        assertTrue(CapturasReais.TODAS.stream().anyMatch(c -> c.fase().equals("encerramento")));
        assertTrue(CapturasReais.TODAS.stream().anyMatch(c -> c.rep().equals("REP2")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("capturas")
    @DisplayName("a excecao reconstruida e identica a capturada (classe, mensagem e cadeia de causas)")
    void reconstrucaoEhFielAoCsv(Captura c) {
        RuntimeException e = reconstruirDoCsv(c.classe(), c.mensagem(), c.causas());
        assertEquals(c.classe(), e.getClass().getName());
        assertEquals(c.mensagem(), e.getMessage());
        assertEquals(c.causas(), SondaCsv.cadeiaDeCausas(e), "cadeia de causas diferente da capturada");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("capturas")
    @DisplayName("cada captura real e classificada como decidido (por cache, operacao e fase)")
    void capturaRealEhClassificadaComoEsperado(Captura c) {
        RuntimeException e = reconstruirDoCsv(c.classe(), c.mensagem(), c.causas());
        assertEquals(c.esperado(), ClassificadorDeErro.classificar(e).code(), c.toString());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("capturasDeParticao")
    @DisplayName("AvailabilityException (ISPN000306/ISPN000305): classe do topo e HotRodClientException, sem causas")
    void availabilityExceptionVemSoNaMensagemDeUmHotRodClientException(Captura c) {
        assertEquals("org.infinispan.client.hotrod.exceptions.HotRodClientException", c.classe());
        assertTrue(c.mensagem().startsWith("org.infinispan.partitionhandling.AvailabilityException: ISPN00030"),
                c.mensagem());
        assertEquals("", c.causas(), "a captura nao tem cadeia de causas");
        RuntimeException e = reconstruirDoCsv(c.classe(), c.mensagem(), c.causas());
        assertNull(e.getCause(), "cadeia de causas vazia");
        assertTrue(ClassificadorDeErro.indisponivel(e));
    }

    @Test
    @DisplayName("P1-IC (G-4): a recusa real do cache ignorado (A4) e HotRodClientException com o nome da classe do servidor, sem causas")
    void cacheIgnoradoRealEhIndisponivel() {
        List<Captura> a4 = CapturasReais.TODAS.stream().filter(c -> c.rep().equals("A4")).toList();
        assertEquals(1, a4.size());
        Captura c = a4.get(0);
        assertEquals("org.infinispan.client.hotrod.exceptions.HotRodClientException", c.classe());
        assertEquals("org.infinispan.server.hotrod.CacheUnavailableException", c.mensagem());
        RuntimeException e = reconstruirDoCsv(c.classe(), c.mensagem(), c.causas());
        assertNull(e.getCause());
        assertEquals(Code.ERROR_UNAVAILABLE, ClassificadorDeErro.classificar(e).code());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("capturasDeTransporte")
    @DisplayName("timeout puro: o texto 'timed out after 2000 ms' nao casa com nenhuma marca e vira ERROR_TRANSPORT")
    void timeoutPuroNaoCasaComMarcaNenhuma(Captura c) {
        for (String marca : ClassificadorDeErro.MARCAS_DE_INDISPONIBILIDADE) {
            assertFalse(c.mensagem().contains(marca), "marca '" + marca + "' casa com " + c);
            assertFalse(c.classe().contains(marca), "marca '" + marca + "' casa com a classe de " + c);
        }
        assertEquals(Code.ERROR_TRANSPORT,
                ClassificadorDeErro.classificar(reconstruirDoCsv(c.classe(), c.mensagem(), c.causas())).code());
    }

    @Test
    @DisplayName("lado a lado: timeout e AvailabilityException da mesma operacao (sessions/put, REP=3)")
    void timeoutEIndisponibilidadeLadoALado() {
        Captura timeout = achar("REP3", "sessions", "put", Code.ERROR_TRANSPORT);
        Captura indisp = achar("REP3", "sessions", "put", Code.ERROR_UNAVAILABLE);
        assertEquals("org.infinispan.client.hotrod.exceptions.TransportException", timeout.classe());
        assertTrue(timeout.mensagem().contains("timed out after 2000 ms"));
        assertEquals(Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(
                reconstruirDoCsv(timeout.classe(), timeout.mensagem(), timeout.causas())).code());
        assertEquals(Code.ERROR_UNAVAILABLE, ClassificadorDeErro.classificar(
                reconstruirDoCsv(indisp.classe(), indisp.mensagem(), indisp.causas())).code());
    }

    @Test
    @DisplayName("HotRodClientException com InterruptedException (REP=2, encerramento) e ERROR_TRANSPORT")
    void interrupcaoNaoEhIndisponibilidade() {
        Captura c = CapturasReais.TODAS.stream().filter(x -> x.fase().equals("encerramento")).findFirst().orElseThrow();
        RuntimeException e = reconstruirDoCsv(c.classe(), c.mensagem(), c.causas());
        assertTrue(e.getCause() instanceof InterruptedException);
        assertNull(e.getCause().getMessage(), "na captura a causa vem so com o nome da classe");
        assertEquals(Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(e).code());
    }

    // --------------- o buraco do entrySet: RuntimeException com texto de indisponibilidade ---------------

    /** Texto que, se estivesse na mensagem de um timeout, casaria com as marcas reais. */
    private static final String TEXTO_DE_INDISPONIBILIDADE =
            " [Key 'k' is not available. Not all owners are in this partition, AvailabilityException ISPN000306,"
                    + " ISPN000305 degraded mode]";

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("entrySetComTimeout")
    @DisplayName("entrySet: RuntimeException de timeout com texto de indisponibilidade na mensagem continua ERROR_TRANSPORT")
    void entrySetRuntimeExceptionDeTimeoutComTextoDeIndisponibilidadeNaoEPromovida(Captura c) {
        // Mesma forma da captura (RuntimeException de topo + causa SocketTimeoutException), com o texto
        // de indisponibilidade acrescentado a mensagem: a causa-raiz e timeout, entao nao ha recusa.
        String mensagem = c.mensagem() + TEXTO_DE_INDISPONIBILIDADE;
        String causas = c.causas() + TEXTO_DE_INDISPONIBILIDADE;
        RuntimeException e = reconstruirDoCsv(c.classe(), mensagem, causas);
        assertEquals("java.lang.RuntimeException", e.getClass().getName());
        assertEquals(Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(e).code());
    }

    static Stream<Arguments> entrySetComTimeout() {
        return CapturasReais.TODAS.stream()
                .filter(c -> c.operacao().equals("entrySet") && c.classe().equals("java.lang.RuntimeException"))
                .map(c -> Arguments.of(c));
    }

    @Test
    @DisplayName("controle: a mesma mensagem de indisponibilidade SEM causa de timeout continua ERROR_UNAVAILABLE")
    void textoDeIndisponibilidadeSemCausaDeTransporteContinuaIndisponivel() {
        RuntimeException e = new RuntimeException("falha do entrySet" + TEXTO_DE_INDISPONIBILIDADE);
        assertEquals(Code.ERROR_UNAVAILABLE, ClassificadorDeErro.classificar(e).code());
    }

    @Test
    @DisplayName("TransportException e IOException com 'not available' seguem protegidas (caso negativo anterior)")
    void caso_negativo_timeoutRealDeTransporteNuncaViraErrorUnavailable() {
        TransportException e = new TransportException(new SocketTimeoutException("Read timed out"), null);
        assertEquals(Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(e).code());
        RuntimeException comTexto = new RuntimeException("falha",
                new IOException("Connection refused: cache is not available", new SocketTimeoutException("t")));
        assertEquals(Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(comTexto).code(),
                "texto 'not available' dentro de IOException nao promove para indisponibilidade");
    }

    // ------------------------------ marcas: cada uma com fonte real ------------------------------

    @Test
    @DisplayName("toda marca de MARCAS_DE_INDISPONIBILIDADE aparece em ao menos uma excecao real de indisponibilidade")
    void todaMarcaTemFonteNaCapturaReal() {
        for (String marca : ClassificadorDeErro.MARCAS_DE_INDISPONIBILIDADE) {
            assertTrue(CapturasReais.TODAS.stream().anyMatch(c -> c.esperado() == Code.ERROR_UNAVAILABLE
                            && (c.mensagem().contains(marca) || c.classe().contains(marca))),
                    "marca sem fonte na captura real: " + marca);
        }
    }

    @Test
    @DisplayName("toda captura de indisponibilidade casa com ao menos uma marca (nenhuma depende so de outra)")
    void todaIndisponibilidadeRealCasaComMarca() {
        for (Captura c : CapturasReais.TODAS) {
            if (c.esperado() != Code.ERROR_UNAVAILABLE) {
                continue;
            }
            assertTrue(ClassificadorDeErro.MARCAS_DE_INDISPONIBILIDADE.stream().anyMatch(c.mensagem()::contains),
                    c.toString());
        }
    }

    // ------------------- linhas de LOG do servidor (decisao, nao captura de cliente) -------------------

    record MensagemServidor(String codigo, String texto, boolean esperadoIndisponivel) { }

    /**
     * Linhas de log do servidor (relatorio T-B, secao 4; validacao V-B, secao 3). Nenhuma foi
     * observada como excecao do cliente na captura (as excecoes de indisponibilidade trazem so
     * ISPN000305/ISPN000306). Decisao: so e indisponibilidade o texto que a captura real mostra
     * ou que repete a mesma frase ("degraded mode", ISPN000319); visoes de cluster, fusao e
     * mensagens do JGroups nao sao.
     */
    static final List<MensagemServidor> POR_MENSAGEM_DE_SERVIDOR = List.of(
            new MensagemServidor("ISPN000094",
                    "Received new cluster view for channel cluster-tcc: [isn3-5889|3] (1) [isn3-5889]", false),
            new MensagemServidor("ISPN000093",
                    "Received new, MERGED cluster view for channel cluster-tcc: "
                            + "MergeView::[isn2-19096|4] (3) [isn2-19096, isn1, isn3-5889]", false),
            new MensagemServidor("ISPN100011",
                    "Entering availability mode DEGRADED_MODE, topology id 11", false),
            new MensagemServidor("ISPN000319",
                    "After merge (or coordinator change), cache still hasn't recovered all its data "
                            + "and must stay in degraded mode", true),
            new MensagemServidor("ISPN100007",
                    "After merge (or coordinator change), recovered members [isn2, isn1, isn3] "
                            + "with topology id 10", false),
            new MensagemServidor("ISPN000517",
                    "Ignoring cache topology from [isn3-5889] during merge", false),
            new MensagemServidor("JGRP000011",
                    "isn3-5889: dropped message from non-member isn2-19096 (view=[isn3-5889|3] (1))", false));

    @Test
    @DisplayName("as sete linhas de log conhecidas tem classificacao decidida")
    void mensagensDeServidorConhecidasTemClassificacaoDecidida() {
        assertEquals(7, POR_MENSAGEM_DE_SERVIDOR.size());
        for (MensagemServidor m : POR_MENSAGEM_DE_SERVIDOR) {
            RuntimeException e = new RuntimeException(m.codigo() + " " + m.texto());
            assertEquals(m.esperadoIndisponivel(), ClassificadorDeErro.indisponivel(e), m.codigo());
        }
    }

    // ------------------------------ HealthRest contra o JSON real ------------------------------

    private static String json(String arquivo) throws IOException {
        try (InputStream in = ClassificadorMensagemRealTest.class
                .getResourceAsStream("/captura-real/" + arquivo)) {
            assertNotNull(in, "fixture ausente: " + arquivo);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("REP=3, isn3 particionado: 1 no, sessions e counters DEGRADED (nao healthy)")
    void jsonRealDeNoDegradedEInterpretado() throws IOException {
        HealthRest.Estado e = HealthRest.interpretar(json("rep3-isn3-degraded-1no.json"));
        assertEquals(1, e.numeroDeNos());
        assertEquals("DEGRADED", e.statusPorCache().get("sessions"));
        assertEquals("DEGRADED", e.statusPorCache().get("counters"));
        assertFalse(e.healthy("sessions"));
        assertFalse(e.healthy("counters"));
        assertTrue(e.statusPorCache().containsKey("___protobuf_metadata"), "caches internos tambem listados");
    }

    @Test
    @DisplayName("REP=3, isn1 com a maioria: 2 nos, HEALTHY")
    void jsonRealDaMaioriaEInterpretado() throws IOException {
        HealthRest.Estado e = HealthRest.interpretar(json("rep3-isn1-healthy-2nos.json"));
        assertEquals(2, e.numeroDeNos());
        assertTrue(e.healthy("sessions"));
        assertTrue(e.healthy("counters"));
    }

    @Test
    @DisplayName("REP=3, pos-fusao: 3 nos, HEALTHY")
    void jsonRealPosFusaoEInterpretado() throws IOException {
        HealthRest.Estado e = HealthRest.interpretar(json("rep3-isn3-healthy-3nos.json"));
        assertEquals(3, e.numeroDeNos());
        assertTrue(e.healthy("sessions"));
        assertTrue(e.healthy("counters"));
    }

    @Test
    @DisplayName("REP=2, durante o rebalanceamento: 3 nos, sessions HEALTHY_REBALANCING (nao healthy), counters HEALTHY")
    void jsonRealDeRebalanceamentoNaoContaComoHealthy() throws IOException {
        HealthRest.Estado e = HealthRest.interpretar(json("rep2-isn1-healthy-rebalancing-3nos.json"));
        assertEquals(3, e.numeroDeNos());
        assertEquals("HEALTHY_REBALANCING", e.statusPorCache().get("sessions"));
        assertFalse(e.healthy("sessions"), "a rodada so e valida com sessions plenamente HEALTHY");
        assertTrue(e.healthy("counters"));
    }

    private static Captura achar(String rep, String cache, String op, Code code) {
        return CapturasReais.TODAS.stream()
                .filter(c -> c.rep().equals(rep) && c.cache().equals(cache) && c.operacao().equals(op)
                        && c.esperado() == code)
                .findFirst().orElseThrow();
    }
}
