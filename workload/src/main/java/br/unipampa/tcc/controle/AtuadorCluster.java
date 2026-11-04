package br.unipampa.tcc.controle;

/**
 * W-3 (B-28.2): o que o controlador precisa de um atuador sobre o <i>cluster</i>. Uma chamada por
 * acao, sincrona, com prazo. Quem chama e sempre a thread unica do {@link ExecutorDeAcoes}, que cuida
 * da fila, da pilha de reversao, da idempotencia, do encerramento e do gancho de desligamento; o
 * atuador nao guarda estado de acoes.
 *
 * <p>Implementacoes: {@link AtuadorNulo} (ate o W-2 entrar) e o adaptador REST do W-2 (B-44.2,
 * {@code session.AtuadorRestInfinispan}), que faz a chamada e a leitura de volta e devolve o codigo
 * HTTP e o valor lido. Uma implementacao deve respeitar {@code prazoNs} e responder a interrupcao.</p>
 */
public interface AtuadorCluster {

    /** Nome curto gravado no JSONL ({@code nulo}, {@code rest-infinispan}, ...). */
    String nome();

    /**
     * Aplica a acao e confere por leitura de volta.
     *
     * @throws Exception qualquer falha; o executor a registra como {@link ResultadoAtuacao.Estado#FALHOU}
     *                   e tenta a reversao na hora
     */
    ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) throws Exception;

    /**
     * Desfaz a acao (volta ao valor do XML do cluster) e confere por leitura de volta. Deve ser
     * idempotente: reverter o que ja esta no valor do XML e sucesso.
     *
     * @throws Exception qualquer falha; o executor mantem a acao na pilha e tenta de novo no encerramento
     */
    ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) throws Exception;
}
