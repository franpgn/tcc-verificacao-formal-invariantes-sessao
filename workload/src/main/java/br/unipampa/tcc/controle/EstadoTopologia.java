package br.unipampa.tcc.controle;

/**
 * Resumo das amostras da sonda de topologia numa janela (plano de 2026-10-22, secao 3.2, S_top).
 * A classificacao de cada amostra (consulta REST falhou, menos de 3 nos, cache nao HEALTHY) e
 * feita pela sonda; aqui chega so a contagem.
 *
 * @param disponivel {@code false} quando nao ha sonda ou dado de topologia: sinal neutro
 * @param amostras   amostras de topologia na janela
 * @param anomalas   amostras classificadas como anomalas
 * @param nosMinimo  menor {@code number_of_nodes} visto na janela (so registro)
 */
public record EstadoTopologia(boolean disponivel, int amostras, int anomalas, int nosMinimo) {

    private static final EstadoTopologia INDISPONIVEL = new EstadoTopologia(false, 0, 0, 0);

    public EstadoTopologia {
        if (amostras < 0 || anomalas < 0 || nosMinimo < 0) {
            throw new IllegalArgumentException("contagem negativa: amostras=" + amostras
                    + " anomalas=" + anomalas + " nosMinimo=" + nosMinimo);
        }
        if (anomalas > amostras) {
            throw new IllegalArgumentException("anomalas > amostras: " + anomalas + " > " + amostras);
        }
    }

    /** Estado neutro: nenhuma informacao de topologia. */
    public static EstadoTopologia indisponivel() {
        return INDISPONIVEL;
    }

    /** S_top ligado: disponivel e com ao menos uma amostra anomala na janela. */
    public boolean anomala() {
        return disponivel && anomalas > 0;
    }
}
