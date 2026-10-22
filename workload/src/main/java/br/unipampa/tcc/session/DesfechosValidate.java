package br.unipampa.tcc.session;

import java.util.concurrent.atomic.LongAdder;

/**
 * B-43.2 (P-d): contador, so em memoria, dos desfechos reais de Validate na janela de medicao (a
 * mesma {@link WarmupPolicy} do {@link LatencyRegistry} e do CSV de eventos).
 *
 * <p>Motivo: com o mascaramento C4 ainda ativo, o CSV grava {@code VALID} para toda
 * {@code OK_VALIDATE}, inclusive de sessao INVALID, e {@code NONE} para {@code NOT_FOUND}. Na mistura
 * v2 o Logout isolado invalida em segundos as chaves Zipf mais quentes, entao "Validate VALID" no CSV
 * nao distingue sessao valida de chave existente. Este contador separa os quatro casos e e impresso
 * no fim da repeticao ({@link #linha()}). Nao muda o CSV nem o {@link OpResult} (isso e B-43.4).</p>
 *
 * <p>A janela e avaliada no momento do registro, como no CSV; na fronteira da janela pode haver
 * diferenca de alguns eventos em relacao ao CSV, porque as duas consultas ao relogio nao sao
 * atomicas.</p>
 */
final class DesfechosValidate {

    private final WarmupPolicy politica;
    private final LongAdder valid = new LongAdder();
    private final LongAdder invalid = new LongAdder();
    private final LongAdder notFound = new LongAdder();
    private final LongAdder erro = new LongAdder();

    DesfechosValidate(WarmupPolicy politica) {
        this.politica = politica != null ? politica : WarmupPolicy.always();
    }

    /** Conta o desfecho de um Validate, se estiver na janela de medicao. */
    void registrar(OpResult r) {
        if (!politica.deveRegistrar()) {
            return;
        }
        switch (r.code()) {
            case OK_VALIDATE -> {
                if (r.estado() == SessionState.Estado.VALID) {
                    valid.increment();
                } else {
                    invalid.increment();
                }
            }
            case NOT_FOUND -> notFound.increment();
            case ERROR_TRANSPORT, ERROR_UNAVAILABLE -> erro.increment();
            default -> { /* Validate nao produz outros codigos */ }
        }
    }

    long valid() { return valid.sum(); }

    long invalid() { return invalid.sum(); }

    long notFound() { return notFound.sum(); }

    long erro() { return erro.sum(); }

    /** Linha do stdout no fim da repeticao. */
    String linha() {
        return String.format("Desfechos Validate: valid=%d invalid=%d not_found=%d erro=%d",
                valid(), invalid(), notFound(), erro());
    }
}
