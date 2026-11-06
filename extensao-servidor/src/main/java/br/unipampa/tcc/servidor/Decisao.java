package br.unipampa.tcc.servidor;

import org.infinispan.container.entries.CacheEntry;

/**
 * A1: uma decisao da politica de fusao para uma chave em conflito, com o que vai ao log.
 *
 * @param resultado             escolheu uma entrada, nao recebeu nenhuma, ou saiu pela seguranca (remove a chave)
 * @param escolhida             entrada devolvida a resolucao de conflitos (uma das recebidas), ou {@code null}
 * @param motivoSaida           motivo da saida de seguranca; {@code null} nos outros resultados
 * @param chave                 a sid (ou {@code hex:...} se a chave nao for uma String serializada)
 * @param vencedora             {@code preferida}, {@code outra} ou {@code -}
 * @param resumoEscolhida       {@code ESTADO/MOTIVO/vN@instante} da escolhida, ou {@code -}
 * @param igualPreferredNonNull a escolha e a mesma entrada que {@code PREFERRED_NON_NULL} devolveria
 * @param resumoPnn             resumo do que {@code PREFERRED_NON_NULL} devolveria ({@code -} se nula, {@code ?} se ilegivel)
 * @param candidatos            {@code P=...} (preferida) e {@code O1=...}, {@code O2=...} (outras), em ordem
 * @param detalhe               detalhe da saida de seguranca, ou {@code -}
 */
record Decisao(Resultado resultado, CacheEntry<Object, Object> escolhida, MotivoSaida motivoSaida, String chave,
               String vencedora, String resumoEscolhida, boolean igualPreferredNonNull, String resumoPnn,
               String candidatos, String detalhe) {

    enum Resultado { ESCOLHIDA, NENHUMA, SAIDA_SEGURANCA }

    /** Prefixo das linhas de decisao normal (INFO). */
    static final String PREFIXO_FUSAO = "A1-FUSAO";
    /** Prefixo das linhas da saida de seguranca (WARN). */
    static final String PREFIXO_SAIDA = "A1-SAIDA-SEGURANCA";

    /** Uma linha {@code chave=valor}, facil de contar nos logs dos servidores. */
    String linha() {
        if (resultado == Resultado.SAIDA_SEGURANCA) {
            return PREFIXO_SAIDA + " motivo=" + motivoSaida + " chave=" + chave + " acao=remover igual_pnn="
                    + (igualPreferredNonNull ? "sim" : "nao") + " pnn=" + resumoPnn + " candidatos=[" + candidatos
                    + "] detalhe=" + detalhe;
        }
        return PREFIXO_FUSAO + " resultado=" + resultado + " chave=" + chave + " vencedora=" + vencedora
                + " escolhida=" + resumoEscolhida + " igual_pnn=" + (igualPreferredNonNull ? "sim" : "nao")
                + " pnn=" + resumoPnn + " candidatos=[" + candidatos + "]";
    }
}
