package br.unipampa.tcc.controle;

/**
 * B-27: uma operacao administrativa adiada pelo K3 e depois liberada (plano de 2026-10-22, secao
 * 3.4). O K3 nunca descarta: todo evento corresponde a uma operacao que seguiu depois da espera.
 * Instantes na base da {@link FonteDeTempo} do portao.
 *
 * @param ator         quem emitiu (admin, suporte, conta de L)
 * @param operacao     nome canonico (Block, Unblock, IncrementFailure, ResetFailures)
 * @param alvo         identidade alvo
 * @param adiadoEmNs   inicio da espera
 * @param liberadoEmNs fim da espera (a operacao segue logo depois)
 * @param motivo       {@link #SAIDA_CONSERVADOR}, {@link #PRAZO} ou {@link #ENCERRAMENTO}
 */
public record EventoK3(String ator, String operacao, String alvo, long adiadoEmNs,
                       long liberadoEmNs, String motivo) {

    /** O perfil voltou a NORMAL. */
    public static final String SAIDA_CONSERVADOR = "saida-conservador";
    /** Passou {@code k3AdiaMaxNs} desde o inicio da espera. */
    public static final String PRAZO = "prazo";
    /** O portao foi encerrado (fim da carga). */
    public static final String ENCERRAMENTO = "encerramento";
}
