package br.unipampa.tcc.session;

import java.util.concurrent.locks.LockSupport;

/**
 * B-33 (E2): relogio e espera injetaveis do cenario S3 (testes usam relogio falso e nunca
 * dormem de verdade). Todos os instantes sao em ns de uma origem arbitraria monotonica.
 */
public interface RelogioS3 {

    long agoraNs();

    /** Espera ate {@code alvoNs} (retorna imediatamente se ja passou). */
    void dormirAteNs(long alvoNs) throws InterruptedException;

    default void dormirNs(long duracaoNs) throws InterruptedException {
        dormirAteNs(agoraNs() + duracaoNs);
    }

    /** Relogio de producao, baseado em {@link System#nanoTime()}. */
    static RelogioS3 sistema() {
        return new RelogioS3() {
            @Override public long agoraNs() { return System.nanoTime(); }

            @Override public void dormirAteNs(long alvoNs) throws InterruptedException {
                long falta;
                while ((falta = alvoNs - System.nanoTime()) > 0) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    LockSupport.parkNanos(falta);
                }
            }
        };
    }
}
