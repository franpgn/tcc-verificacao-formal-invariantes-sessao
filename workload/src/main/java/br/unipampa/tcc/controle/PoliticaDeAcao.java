package br.unipampa.tcc.controle;

import java.util.List;

/**
 * W-3 (B-28.2): traduz a decisao de uma janela em acoes sobre o <i>cluster</i>. Pura: sem E/S e sem
 * relogio. A politica da tabela da secao 4.5 do plano de execucao de 2026-11-04 e do W-2 (B-44.2), que
 * depende do B-44.1; aqui ficam so a interface e {@link #NENHUMA}.
 *
 * <p>O laco garante, independente da politica, que a saida para NORMAL (R4) desfaz tudo pela pilha de
 * reversao (secao 4.4.3, camada 1); nessa janela a politica nao e consultada.</p>
 */
@FunctionalInterface
public interface PoliticaDeAcao {

    /**
     * @param decisao   decisao da janela
     * @param topologia S_top da janela
     * @param defasagem S_lag da janela
     * @param ativas    acoes ativas (pedidas e nao revertidas), em ordem de aplicacao
     */
    PlanoDeAcao planejar(Decisao decisao, EstadoTopologia topologia, EstadoDefasagem defasagem,
                         List<AcaoCluster> ativas);

    /** Nome gravado na linha de metadados do JSONL. */
    default String nome() {
        return getClass().getSimpleName();
    }

    /** Nenhuma acao, em nenhuma situacao (ate o W-2). */
    PoliticaDeAcao NENHUMA = new PoliticaDeAcao() {
        @Override
        public PlanoDeAcao planejar(Decisao d, EstadoTopologia t, EstadoDefasagem l, List<AcaoCluster> a) {
            return PlanoDeAcao.VAZIO;
        }

        @Override
        public String nome() {
            return "nenhuma";
        }
    };
}
