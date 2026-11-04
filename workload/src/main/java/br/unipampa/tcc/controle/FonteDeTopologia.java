package br.unipampa.tcc.controle;

/**
 * W-3 (B-28.2): S_top por janela. Ao vivo e {@link SondaTopologia#resumirAte(long)}; sem sonda,
 * {@code fim -> EstadoTopologia.indisponivel()}.
 */
@FunctionalInterface
public interface FonteDeTopologia {

    EstadoTopologia resumirAte(long fimNs);

    /** Sem sonda: topologia indisponivel (sinal neutro). */
    FonteDeTopologia INDISPONIVEL = fim -> EstadoTopologia.indisponivel();
}
