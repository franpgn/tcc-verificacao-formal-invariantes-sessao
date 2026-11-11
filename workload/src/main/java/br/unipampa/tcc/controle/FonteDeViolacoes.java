package br.unipampa.tcc.controle;

import java.util.List;

/**
 * W-3 (B-28.2): violacoes vistas desde a chamada anterior. O adaptador de {@code session} enfileira as
 * do auditor em linha e so as classifica aqui, na thread do laco, com o rastro de cliente mais completo
 * (a classe de I2 depende dos Block/Unblock ja confirmados).
 */
@FunctionalInterface
public interface FonteDeViolacoes {

    /** Remove e devolve as violacoes pendentes. */
    List<ViolacaoObservada> drenar();

    /** Nenhuma violacao. */
    FonteDeViolacoes NENHUMA = List::of;
}
