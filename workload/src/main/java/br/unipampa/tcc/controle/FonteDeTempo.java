package br.unipampa.tcc.controle;

/**
 * Fonte do "agora" usada pelo tick do agregador (V-B26.1 secao 5.9; Gestor, 2026-10-22).
 *
 * <p>Contrato: devolve ns <b>no mesmo relogio dos {@code end_ns}</b> dos eventos. Ao vivo (B-28.2)
 * a implementacao e {@code ancora.paraEpochNanos(System.nanoTime())}, o mesmo {@code EpochClock}
 * de {@code SessionOps.emitir} e o mesmo padrao de {@code ConvergenciaLoop.Relogio.sistema}. Nos
 * testes e no replay e um relogio falso. O pacote {@code controle} so declara a interface, para
 * continuar sem ler relogio de parede.
 */
@FunctionalInterface
public interface FonteDeTempo {

    /** Instante atual, em ns, no relogio dos {@code end_ns}. */
    long agoraNs();
}
