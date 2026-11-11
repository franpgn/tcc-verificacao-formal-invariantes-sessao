package br.unipampa.tcc.controle;

/**
 * W-3 (B-28.2): S_top por janela. Ao vivo e a {@link SondaTopologia}; sem sonda,
 * {@code fim -> EstadoTopologia.indisponivel()}.
 */
@FunctionalInterface
public interface FonteDeTopologia {

    /** Resumo da janela so com as contagens (sem {@link EstadoTopologia#porNo()}). */
    EstadoTopologia resumirAte(long fimNs);

    /**
     * W-2a (B-44.2): resumo da janela com o estado de cada no ({@link EstadoTopologia#porNo()}), usado pela
     * {@link PoliticaPorAssinatura}. O laco chama este metodo, e nao {@link #resumirAte(long)}. Sem sonda
     * por no (o padrao), e {@link #resumirAte(long)}: {@code porNo} vazio, nenhuma assinatura dispara.
     */
    default EstadoTopologia resumirComNosAte(long fimNs) {
        return resumirAte(fimNs);
    }

    /** Sem sonda: topologia indisponivel (sinal neutro). */
    FonteDeTopologia INDISPONIVEL = fim -> EstadoTopologia.indisponivel();
}
