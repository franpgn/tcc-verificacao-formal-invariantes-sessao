package br.unipampa.tcc.controle;

/**
 * W-3 (B-28.2): S_lag por janela. A sonda e o W-4 (B-26.3); ate la, {@link #INDETERMINADA}.
 */
@FunctionalInterface
public interface FonteDeDefasagem {

    EstadoDefasagem resumirAte(long fimNs);

    /** Sem sonda de defasagem. */
    FonteDeDefasagem INDETERMINADA = fim -> EstadoDefasagem.indeterminada();
}
