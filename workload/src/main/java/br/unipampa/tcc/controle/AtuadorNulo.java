package br.unipampa.tcc.controle;

/**
 * W-3 (B-28.2): atuador que nao chama o cluster. Usado em {@code active} ate o W-2 (B-44.2) plugar o
 * adaptador REST: toda chamada devolve {@link ResultadoAtuacao.Estado#NAO_EXECUTADA} e a trilha sai com
 * {@code "executada": false}, como em shadow, mas passando pela fila e pela pilha.
 */
public final class AtuadorNulo implements AtuadorCluster {

    static final String MOTIVO = "atuador-nulo";

    @Override
    public String nome() {
        return "nulo";
    }

    @Override
    public ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) {
        return ResultadoAtuacao.naoExecutada(MOTIVO);
    }

    @Override
    public ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) {
        return ResultadoAtuacao.naoExecutada(MOTIVO);
    }
}
