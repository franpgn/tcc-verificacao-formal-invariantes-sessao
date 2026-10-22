package br.unipampa.tcc.controle;

import java.util.List;
import java.util.Objects;

/**
 * Resultado da avaliacao de uma janela (plano de 2026-10-22, secoes 3.3 e 3.6). Uma por janela,
 * com ou sem transicao.
 *
 * @param fimNs      fim da janela avaliada (o "agora" da decisao)
 * @param perfil     perfil depois da decisao
 * @param transicao  verdadeiro se o perfil mudou nesta janela
 * @param regra      regra que determinou a decisao (ver {@link Regra})
 * @param motivos    sinais altos na janela, em ordem decrescente de severidade; {@code [ESTAVEL]} se nenhum
 * @param severidade o motivo mais severo de {@code motivos}
 * @param sinais     valores de todos os sinais e contadores apos a janela
 * @param parametros limiares em vigor (com versao e SHA-256 do arquivo de origem)
 */
public record Decisao(long fimNs, Perfil perfil, boolean transicao, Regra regra, List<Motivo> motivos,
                      Motivo severidade, Sinais sinais, ParametrosControlador parametros) {

    /** Regra que determinou a decisao. */
    public enum Regra {
        /** Violacao de seguranca: entra em (ou mantem) CONSERVADOR, mesmo em cooldown. */
        R1,
        /** Entrada por topologia anomala ou latencia/erro persistente, com cooldown expirado. */
        R2,
        /** Em CONSERVADOR, sinal alto: zera o contador de janelas calmas. */
        R3,
        /** Saida para NORMAL. */
        R4,
        /** Nenhuma regra disparou (inclusive quando o cooldown bloqueou R2). */
        NENHUMA
    }

    /**
     * Sinais da janela. Taxas {@code null} significam indeterminado (amostra abaixo do minimo).
     *
     * @param taxaLatencia         excedencias / elegiveis, ou {@code null} se elegiveis &lt; n_lat
     * @param taxaErro             erros / total, ou {@code null} se total &lt; n_err
     * @param fracaoIndisponivel   S_unav (informativo): indisponiveis / erros, ou {@code null} sem erros
     * @param latenciaAlta         taxaLatencia &gt;= theta_lat_in
     * @param erroAlto             taxaErro &gt;= theta_err_in
     * @param topologiaAnomala     S_top ligado nesta janela
     * @param topologiaDisponivel  havia dado de topologia
     * @param topologiaAmostras    amostras de topologia na janela
     * @param topologiaAnomalas    amostras anomalas na janela
     * @param nosMinimo            menor numero de nos visto na janela
     * @param violacoesSeguranca   I1 + I4 + I2 real
     * @param violacoesI2Esperadas I2 esperadas (so registro)
     * @param persistenciaLatencia janelas seguidas com latencia alta, incluindo esta
     * @param persistenciaErro     janelas seguidas com erro alto, incluindo esta
     * @param janelasCalmas        janelas calmas seguidas em CONSERVADOR, incluindo esta
     * @param cooldownExpirado     o cooldown desde a ultima transicao tinha expirado nesta janela
     */
    public record Sinais(Double taxaLatencia, Double taxaErro, Double fracaoIndisponivel,
                         boolean latenciaAlta, boolean erroAlto, boolean topologiaAnomala,
                         boolean topologiaDisponivel, int topologiaAmostras, int topologiaAnomalas,
                         int nosMinimo, long violacoesSeguranca, long violacoesI2Esperadas,
                         int persistenciaLatencia, int persistenciaErro, int janelasCalmas,
                         boolean cooldownExpirado) {
    }

    public Decisao {
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(regra, "regra");
        Objects.requireNonNull(severidade, "severidade");
        Objects.requireNonNull(sinais, "sinais");
        Objects.requireNonNull(parametros, "parametros");
        motivos = List.copyOf(motivos);
        if (motivos.isEmpty()) {
            throw new IllegalArgumentException("motivos vazio: use [ESTAVEL]");
        }
        if (severidade != Motivo.maisSevero(motivos)) {
            throw new IllegalArgumentException("severidade " + severidade + " incoerente com " + motivos);
        }
    }
}
