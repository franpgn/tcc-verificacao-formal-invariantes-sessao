package br.unipampa.tcc.controle;

/**
 * W-4 (B-26.3): o canal da {@link SondaDefasagem} com o <i>cluster</i>. Cada no tem um cliente fixado nele; a
 * sonda escreve a chave de um no pelo cliente dele e le pelos clientes dos outros. Ao vivo e o adaptador Hot Rod
 * {@code session.CanalCanarioHotRod}, sobre o <i>cache</i> {@code canario} (SYNC) ou {@code canario-async}
 * (ASYNC), que nao entra em nenhum invariante; nos testes, um canal falso. Fica em {@code controle} para que a
 * sonda continue sem depender do cliente do <i>data grid</i> (mesmo padrao de {@link FonteDeSaude}).
 *
 * <p>P-5: as duas operacoes sao Hot Rod, em memoria; nenhuma cria processo nem toca em disco.</p>
 */
public interface CanalCanario extends AutoCloseable {

    /** Prefixo de toda chave escrita pela sonda no cache canario. */
    String PREFIXO_CHAVE = "lag:";

    /** Numero de nos ({@code >= 2}); a ordem e a de {@code --servers} e de {@code --controller-admin}. */
    int nos();

    /**
     * Rotulo canonico do no {@code indice}: {@code host:porta} da URL de {@code --controller-admin}, o mesmo da
     * sonda de topologia e do atuador (contrato JSONL, "rotulo dos nos"; {@code RotulosDosNosTest}).
     */
    String rotulo(int indice);

    /**
     * Chave que a sonda escreve para o no {@code indice}. Padrao: {@code lag:<rotulo>}. O adaptador Hot Rod escolhe,
     * na abertura, uma chave cujo dono primario e o proprio no (P-40), para que, sob o corte de outro no, os pares
     * entre os nos da maioria continuem vendo o valor. Comeca sempre por {@link #PREFIXO_CHAVE}.
     */
    default String chave(int indice) {
        return PREFIXO_CHAVE + rotulo(indice);
    }

    /**
     * P-40: a chave do no tinha o proprio no como dono primario na abertura ({@code true}), nao tinha
     * ({@code false}), ou o canal nao sabe ({@code null}, padrao). O adaptador Hot Rod recusa a abertura se algum no
     * ficar sem primario (V-W4 R1); o valor fica registrado em {@code defasagem_chaves} no
     * {@code fim}.
     */
    default Boolean chaveNoPrimario(int indice) {
        return null;
    }

    /**
     * P-40: motivo da escolha da chave do no ({@code primario}, {@code sem-topologia}, {@code fora-da-topologia},
     * {@code nao-encontrada}, {@code nao-escolhida}, {@code falha:<Excecao>}), ou {@code null} se o canal nao escolhe
     * (padrao). Registro de auditoria ({@code defasagem_chaves}); com o adaptador Hot Rod, so {@code primario} abre.
     */
    default String motivoDaChave(int indice) {
        return null;
    }

    /**
     * Escreve {@code valor} em {@code chave} pelo cliente fixado no no {@code indice}. Bloqueia ate a resposta
     * do servidor; deve responder a interrupcao (a sonda interrompe a tarefa no fim do prazo da rodada).
     *
     * @throws Exception qualquer falha; a sonda conta a escrita como falha e continua lendo
     */
    void escrever(int indice, String chave, String valor) throws Exception;

    /**
     * Le {@code chave} pelo cliente fixado no no {@code indice}.
     *
     * @return o valor lido, ou {@code null} se a chave nao existe
     * @throws Exception qualquer falha; a sonda conta a leitura como falha e tenta de novo ate o prazo
     */
    String ler(int indice, String chave) throws Exception;

    /** Fecha os clientes. Idempotente; nunca lanca. */
    @Override
    default void close() {
    }
}
