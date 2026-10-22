package br.unipampa.tcc.controle;

import java.util.Collection;

/**
 * Motivo de uma decisao, declarado em ordem decrescente de severidade
 * (plano de 2026-10-22, secao 3.3). {@code ESTAVEL} indica nenhum sinal alto na janela.
 */
public enum Motivo {
    VIOLACAO,
    TOPOLOGIA,
    ERRO,
    LATENCIA,
    ESTAVEL;

    /** Verdadeiro se este motivo e estritamente mais severo que {@code outro}. */
    public boolean maisSeveroQue(Motivo outro) {
        return ordinal() < outro.ordinal();
    }

    /** O motivo mais severo da colecao; {@link #ESTAVEL} se ela estiver vazia. */
    public static Motivo maisSevero(Collection<Motivo> motivos) {
        Motivo r = ESTAVEL;
        for (Motivo m : motivos) {
            if (m.maisSeveroQue(r)) {
                r = m;
            }
        }
        return r;
    }
}
