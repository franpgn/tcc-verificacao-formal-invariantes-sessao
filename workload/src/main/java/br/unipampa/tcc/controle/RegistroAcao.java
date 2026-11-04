package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * W-3 (B-28.2): uma linha {@code {"tipo":"acao",...}} do {@code decisoes-rep-NNN.jsonl}: um pedido de
 * aplicacao ou de reversao, executado ou nao (plano de execucao de 2026-11-04, secao 4.4.3).
 *
 * @param operacao    aplicar ou reverter
 * @param acao        a acao
 * @param modo        shadow ou active
 * @param atuador     {@link AtuadorCluster#nome()}
 * @param executada   houve chamada ao cluster
 * @param resultado   {@code APLICADA}, {@code FALHOU}, {@code NAO_EXECUTADA} (atuador nulo),
 *                    {@code PREVISTA} (shadow) ou {@code DESCARTADA} (pedido ainda na fila no encerramento)
 * @param origem      o que motivou o pedido
 * @param regra       regra da decisao que motivou o pedido, ou {@code null} (encerramento)
 * @param fimJanelaNs fim da janela da decisao, ou {@code null} (encerramento)
 * @param pedidoNs    instante do pedido (relogio dos {@code end_ns})
 * @param inicioNs    instante do inicio da chamada, ou {@code null} sem chamada
 * @param duracaoNs   duracao da chamada, ou {@code null} sem chamada
 * @param codigoHttp  codigo HTTP, ou {@code null}
 * @param lidoDeVolta valor lido de volta, ou {@code null}
 * @param erro        motivo da falha, ou {@code null}
 */
public record RegistroAcao(Operacao operacao, AcaoCluster acao, ModoAtuacao modo, String atuador,
                           boolean executada, String resultado, Origem origem, Decisao.Regra regra,
                           Long fimJanelaNs, long pedidoNs, Long inicioNs, Long duracaoNs,
                           Integer codigoHttp, String lidoDeVolta, String erro) {

    /** Aplicar ou reverter. */
    public enum Operacao { APLICAR, REVERTER }

    /** Origem do pedido. */
    public enum Origem {
        /** Plano da {@link PoliticaDeAcao}. */
        POLITICA,
        /** Saida para NORMAL: desfaz tudo (camada 1). */
        R4,
        /** Reversao automatica de uma aplicacao que falhou. */
        FALHA_NA_APLICACAO,
        /** Encerramento normal do laco (fim da carga). */
        ENCERRAMENTO,
        /** Gancho de desligamento da JVM (camada 2). */
        GANCHO_JVM
    }

    public RegistroAcao {
        Objects.requireNonNull(operacao, "operacao");
        Objects.requireNonNull(acao, "acao");
        Objects.requireNonNull(modo, "modo");
        Objects.requireNonNull(atuador, "atuador");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(origem, "origem");
    }
}
