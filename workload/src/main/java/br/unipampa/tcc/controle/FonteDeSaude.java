package br.unipampa.tcc.controle;

/**
 * B-26.2: origem das leituras de saude usadas pela {@link SondaTopologia}. Ao vivo e o adaptador REST
 * ({@code session.ConsultaSaudeRest}); nos testes, uma fonte falsa. Fica em {@code controle} para que a
 * sonda continue sem depender de processo, rede ou cliente do data grid.
 */
public interface FonteDeSaude {

    /** Numero de nos consultados por rodada ({@code >= 1}). */
    int nos();

    /** Rotulo do no {@code indice} (aparece em {@link ResultadoNo#no()}). */
    String rotulo(int indice);

    /**
     * Consulta UM no. Deve respeitar {@code prazoNs} (e responder a interrupcao): a sonda considera
     * o no em {@code timeout} quando o prazo da rodada acaba e interrompe a tarefa.
     *
     * @throws Exception qualquer falha de consulta; a sonda a classifica como amostra anomala
     */
    LeituraSaude consultar(int indice, long prazoNs) throws Exception;
}
