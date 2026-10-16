package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ESQUELETO (C-2 da validacao V-E1; item rastreavel RAST-G3-1 no relatorio B-33, parte E2),
 * preparado para receber a captura da REP=2 do G-4 feita pela sonda ({@link SondaHotrod}, coletada
 * por {@code runs/gate-b29/g1b.sh}).
 *
 * <p>As marcas de {@link ClassificadorDeErro#MARCAS_DE_INDISPONIBILIDADE} e os nomes de campo
 * lidos por {@link HealthRest} vem da documentacao do Infinispan, nao de captura real; os testes
 * atuais usam mensagens fabricadas. Sem os testes abaixo preenchidos e habilitados, T-F nao pode
 * alegar H-L7 (distincao entre recusa por indisponibilidade e timeout).</p>
 *
 * <p><b>Como preencher (ver o checklist em docs/relatorio-sonda-hotrod-2026-10-16.md):</b>
 * (1) das linhas {@code ERRO} do CSV da sonda em isn3, copiar para {@link #CLASSES_CAPTURADAS} e
 * {@link #MENSAGENS_CAPTURADAS} a cadeia completa (da excecao de topo ate a causa-raiz, na ordem
 * do CSV: coluna {@code excecao_classe}/{@code mensagem}, depois cada item de {@code causas}); (2)
 * preencher {@link #ESPERADO_POR_MENSAGEM_DE_SERVIDOR}; (3) remover os {@code @Disabled}; (4) se o
 * teste falhar, ajustar {@code MARCAS_DE_INDISPONIBILIDADE} (TDD: o teste vermelho e a prova de que
 * a lista atual nao cobre o que o cliente recebe). Nunca remover o {@code @Disabled} antes da
 * captura real.</p>
 */
class ClassificadorMensagemRealTest {

    // ---- A PREENCHER COM A CAPTURA REAL (REP=2, sonda -> isn3, operacao com resultado ERRO) ----

    /** Classe e mensagem da excecao de topo (colunas excecao_classe e mensagem do CSV da sonda). */
    static final String CLASSE_CAPTURADA = "<preencher: classe da excecao no cliente>";
    static final String MENSAGEM_CAPTURADA = "<preencher: mensagem real>";

    /**
     * Cadeia completa, do topo a causa-raiz, uma entrada por excecao (inclui o topo na posicao 0).
     * As duas listas tem o mesmo tamanho; use {@code null} na mensagem de causa sem mensagem.
     */
    static final List<String> CLASSES_CAPTURADAS = List.of(CLASSE_CAPTURADA);
    static final List<String> MENSAGENS_CAPTURADAS = List.of(MENSAGEM_CAPTURADA);

    /** Operacao e no em que a excecao foi vista (documentacao do teste; nao entra na assercao). */
    static final String OPERACAO_CAPTURADA = "<preencher: put|get|remove|entrySet>";

    // ---- Mensagens de SERVIDOR ja conhecidas (relatorio T-B, secao 4; validacao V-B, secao 3) ----
    // Sao linhas de LOG do servidor, nao excecoes do cliente. O teste de mapa abaixo registra, para
    // cada uma, o que o ClassificadorDeErro deve fazer SE ela chegar ao cliente como texto de
    // excecao. O valor esperado e uma decisao a tomar apos a captura, por isso fica null.

    record MensagemServidor(String codigo, String texto, Boolean esperadoIndisponivel) { }

    static final List<MensagemServidor> ESPERADO_POR_MENSAGEM_DE_SERVIDOR = List.of(
            new MensagemServidor("ISPN000094",
                    "Received new cluster view for channel cluster-tcc: [isn3-5889|3] (1) [isn3-5889]", null),
            new MensagemServidor("ISPN000093",
                    "Received new, MERGED cluster view for channel cluster-tcc: "
                            + "MergeView::[isn2-19096|4] (3) [isn2-19096, isn1, isn3-5889]", null),
            new MensagemServidor("ISPN100011",
                    "Entering availability mode DEGRADED_MODE, topology id 11", null),
            new MensagemServidor("ISPN000319",
                    "After merge (or coordinator change), cache still hasn't recovered all its data "
                            + "and must stay in degraded mode", null),
            new MensagemServidor("ISPN100007",
                    "After merge (or coordinator change), recovered members [isn2, isn1, isn3] "
                            + "with topology id 10", null),
            new MensagemServidor("ISPN000517",
                    "Ignoring cache topology from [isn3-5889] during merge", null),
            new MensagemServidor("JGRP000011",
                    "isn3-5889: dropped message from non-member isn2-19096 (view=[isn3-5889|3] (1))", null));

    /**
     * Reconstroi uma cadeia de excecoes a partir de nomes de classe e mensagens (topo primeiro).
     * Cada classe precisa ser instanciavel por um construtor {@code (String)} ou {@code (String,
     * Throwable)}; se nao for, falha com mensagem clara (nesse caso guardar a captura como texto
     * e criar uma subclasse de teste equivalente).
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
            Throwable t;
            try {
                t = (Throwable) c.getConstructor(String.class, Throwable.class).newInstance(mensagem, causa);
            } catch (NoSuchMethodException semDois) {
                t = (Throwable) c.getConstructor(String.class).newInstance(mensagem);
                if (causa != null) {
                    t.initCause(causa);
                }
            }
            return t;
        } catch (ReflectiveOperationException | IllegalStateException e) {
            throw new AssertionError("classe capturada nao reconstruivel: " + classe
                    + " (use construtor (String[,Throwable]) ou uma subclasse de teste)", e);
        }
    }

    // ------------------------- testes ja ativos (nao dependem da captura) -------------------------

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

    @Test
    void caso_negativo_timeoutRealDeTransporteNuncaViraErrorUnavailable() {
        // Cadeia tipica de um get que estoura o socketTimeout (o que a sonda deve distinguir da recusa).
        TransportException e = new TransportException(new SocketTimeoutException("Read timed out"), null);
        assertEquals(OpResult.Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(e).code());
        RuntimeException comTexto = new RuntimeException("falha",
                new IOException("Connection refused: cache is not available", new SocketTimeoutException("t")));
        assertEquals(OpResult.Code.ERROR_TRANSPORT, ClassificadorDeErro.classificar(comTexto).code(),
                "texto 'not available' dentro de IOException nao promove para indisponibilidade");
    }

    // ------------------------- testes desativados ate a captura real -------------------------

    @Test
    @Disabled("RAST-G3-1: preencher com a excecao real capturada na REP=2 (sonda -> isn3) e habilitar")
    void excecaoRealDeNoDegradedViraErrorUnavailable() {
        assertTrue(!CLASSE_CAPTURADA.startsWith("<preencher"), "esqueleto nao preenchido: " + CLASSE_CAPTURADA);
        RuntimeException e = reconstruir(CLASSES_CAPTURADAS, MENSAGENS_CAPTURADAS);
        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, ClassificadorDeErro.classificar(e).code(),
                "operacao " + OPERACAO_CAPTURADA + ": se falhar, a lista de marcas nao cobre a captura");
    }

    @Test
    @Disabled("RAST-G3-1: decidir, apos a captura, o que cada mensagem de servidor conhecida deve produzir")
    void mensagensDeServidorConhecidasTemClassificacaoDecidida() {
        for (MensagemServidor m : ESPERADO_POR_MENSAGEM_DE_SERVIDOR) {
            assertNotNull(m.esperadoIndisponivel(),
                    m.codigo() + ": preencher esperadoIndisponivel (true/false) apos a captura");
            RuntimeException e = new RuntimeException(m.codigo() + " " + m.texto());
            assertEquals(m.esperadoIndisponivel(), ClassificadorDeErro.indisponivel(e), m.codigo());
        }
    }

    @Test
    @Disabled("RAST-G3-1: preencher com o JSON real de saude do REST capturado (g1b.sh, saude-*.json) e habilitar")
    void jsonRealDeSaudeEhInterpretadoPeloHealthRest() {
        org.junit.jupiter.api.Assertions.fail(
                "esqueleto nao preenchido: colar o JSON integral de isn3 (DEGRADED/1), isn2 (HEALTHY/2) e pos-fusao (HEALTHY/3)");
    }
}
