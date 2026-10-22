package br.unipampa.tcc.controle;

import java.util.List;
import java.util.Objects;

/**
 * Evento de operacao visto pelo agregador (B-26.1). Mesmo contrato das colunas
 * {@code operation,start_ns,end_ns,return_code} do CSV de eventos; {@code op_id}, {@code replica}
 * e {@code key} nao entram no agregado.
 *
 * <p>Classificacao de {@code return_code} (plano de 2026-10-22, secao 3.2): erro e
 * {@code ERROR_TRANSPORT} ou {@code ERROR_UNAVAILABLE}; indisponivel e so {@code ERROR_UNAVAILABLE}.
 * Os demais codigos do contrato contam so no total. Codigo fora do contrato e rejeitado, para que
 * uma mudanca em {@code OpResult.returnCode()} nao passe calada como "nao erro".
 *
 * @param operacao      coluna {@code operation}
 * @param inicioNs      coluna {@code start_ns}
 * @param fimNs         coluna {@code end_ns}; indexa a janela
 * @param codigoRetorno coluna {@code return_code}
 */
public record EventoOperacao(String operacao, long inicioNs, long fimNs, String codigoRetorno) {

    public static final String ERROR_TRANSPORT = "ERROR_TRANSPORT";
    public static final String ERROR_UNAVAILABLE = "ERROR_UNAVAILABLE";

    /** Codigos gravados por {@code OpResult.returnCode()}. */
    public static final List<String> CODIGOS = List.of("OK", "VALID", "COUNTED", "NONE", "BLOCKED",
            ERROR_TRANSPORT, ERROR_UNAVAILABLE);

    public EventoOperacao {
        Objects.requireNonNull(operacao, "operacao");
        Objects.requireNonNull(codigoRetorno, "codigoRetorno");
        if (operacao.isBlank()) {
            throw new IllegalArgumentException("operacao vazia");
        }
        if (!CODIGOS.contains(codigoRetorno)) {
            throw new IllegalArgumentException("return_code fora do contrato: " + codigoRetorno);
        }
        if (fimNs < inicioNs) {
            throw new IllegalArgumentException("end_ns antes de start_ns: " + fimNs + " < " + inicioNs);
        }
    }

    public long latenciaNs() {
        return fimNs - inicioNs;
    }

    public boolean erro() {
        return ERROR_TRANSPORT.equals(codigoRetorno) || ERROR_UNAVAILABLE.equals(codigoRetorno);
    }

    public boolean indisponivel() {
        return ERROR_UNAVAILABLE.equals(codigoRetorno);
    }
}
