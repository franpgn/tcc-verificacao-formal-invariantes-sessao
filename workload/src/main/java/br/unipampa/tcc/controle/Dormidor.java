package br.unipampa.tcc.controle;

import java.util.concurrent.locks.LockSupport;

/**
 * B-27: espera injetavel do portao de emissao (sondagem do K3). Em producao, {@link #real()}; nos
 * testes, um relogio falso que so avanca o tempo. Mesmo padrao de {@code RelogioS3.sistema()}.
 */
@FunctionalInterface
public interface Dormidor {

    /** Espera {@code ns} nanossegundos (retorna na hora se {@code ns <= 0}). */
    void dormirNs(long ns) throws InterruptedException;

    /** Espera real: {@link LockSupport#parkNanos(long)} em laco ate o prazo, sensivel a interrupcao. */
    static Dormidor real() {
        return ns -> {
            long alvo = System.nanoTime() + ns;
            long falta;
            while ((falta = alvo - System.nanoTime()) > 0) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                LockSupport.parkNanos(falta);
            }
        };
    }
}
