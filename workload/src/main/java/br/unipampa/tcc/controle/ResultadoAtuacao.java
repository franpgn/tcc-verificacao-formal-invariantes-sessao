package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * W-3 (B-28.2): resultado de uma chamada ao {@link AtuadorCluster}. A duracao e medida pelo
 * {@link ExecutorDeAcoes}, nao pelo atuador.
 *
 * @param estado      {@link Estado}
 * @param codigoHttp  codigo HTTP da chamada, ou {@code null} sem chamada
 * @param lidoDeVolta valor lido de volta (S_disp) depois da chamada, ou {@code null}
 * @param erro        motivo da falha ou da nao execucao, ou {@code null}
 */
public record ResultadoAtuacao(Estado estado, Integer codigoHttp, String lidoDeVolta, String erro) {

    /** Desfecho da chamada. */
    public enum Estado {
        /** A chamada foi feita e a leitura de volta confere. */
        APLICADA,
        /** A chamada foi feita (ou tentada) e falhou, ou a leitura de volta divergiu. */
        FALHOU,
        /** Nenhuma chamada ao cluster (atuador nulo). */
        NAO_EXECUTADA
    }

    public ResultadoAtuacao {
        Objects.requireNonNull(estado, "estado");
    }

    public static ResultadoAtuacao aplicada(Integer codigoHttp, String lidoDeVolta) {
        return new ResultadoAtuacao(Estado.APLICADA, codigoHttp, lidoDeVolta, null);
    }

    public static ResultadoAtuacao falhou(Integer codigoHttp, String lidoDeVolta, String erro) {
        return new ResultadoAtuacao(Estado.FALHOU, codigoHttp, lidoDeVolta, erro);
    }

    public static ResultadoAtuacao naoExecutada(String motivo) {
        return new ResultadoAtuacao(Estado.NAO_EXECUTADA, null, null, motivo);
    }

    /** Houve chamada ao cluster (com sucesso ou nao). */
    public boolean executada() {
        return estado != Estado.NAO_EXECUTADA;
    }
}
