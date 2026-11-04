package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * W-3 (B-28.2): violacao ja classificada, com o instante em que foi vista (relogio dos {@code end_ns}),
 * entregue ao laco por uma {@link FonteDeViolacoes}.
 */
public record ViolacaoObservada(ClasseViolacao classe, long fimNs) {

    public ViolacaoObservada {
        Objects.requireNonNull(classe, "classe");
    }
}
