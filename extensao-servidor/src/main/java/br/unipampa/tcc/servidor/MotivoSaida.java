package br.unipampa.tcc.servidor;

/**
 * A1/A2: por que a politica de fusao nao decidiu e removeu a chave (saida de seguranca, falha fechada). Com a chave
 * removida, a sessao some e o usuario entra de novo; uma sessao invalidada nunca volta a valer.
 */
public enum MotivoSaida {
    /** Valor nulo, bytes que nao sao serializacao Java, fluxo truncado, bytes sobrando ou campo nulo. */
    VALOR_ILEGIVEL,
    /** Valor de outra classe (fora da lista: {@code SessionState}, os enum dele, {@code Enum}, {@code java.time.Ser}). */
    TIPO_INESPERADO,
    /** Duas copias empatam na ordem de conteudo e diferem em outro campo (identidade, criacao). */
    EMPATE,
    /** Copias da mesma chave com {@code id} de sessao diferentes. */
    SESSOES_DIVERGENTES,
    /** Excecao inesperada dentro da decisao (a politica nunca a propaga a resolucao de conflitos). */
    ERRO_INTERNO
}
