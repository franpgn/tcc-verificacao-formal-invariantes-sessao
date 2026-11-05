package br.unipampa.tcc.session;

/**
 * W-6 (B-46; P-6 da V-W5, achado A-1, opcao a; desenho do coordenador de 2026-11-05): fim da carga com o no parado
 * pelo AC-5.
 *
 * <p>O AC-5 para um no e nao e revertido dentro da execucao ({@code docs/acoes-controlador-cluster-2026-11-05.md}
 * secao 3.1). Sem religar o no, o laco de convergencia nunca ve todos os servidores
 * ({@link ConvergenciaLoop}, rodada invalida com topologia incompleta) e vai a {@code divergencia_persistente}
 * depois do teto. Por isso:</p>
 *
 * <ol>
 *   <li>no fim da carga (depois de o controlador encerrar), o programa imprime {@link #linhaSinal} no stdout,
 *       com o alvo do AC-5 com resultado APLICADA ({@code -} sem ele). O orquestrador ({@code
 *       scripts/run-campanha-s3.sh}) le o stdout por um pipe, sem disco no caminho, e faz o {@code podman start}
 *       do no parado;</li>
 *   <li>com AC-5 aplicado, o programa espera a topologia completa ({@link #esperar}), com o prazo de
 *       {@code --rejoin-timeout-sec}, lendo a mesma sonda do laco de convergencia a cada {@link #PASSO_NS};</li>
 *   <li>{@code t_religamento_ns} e o instante em que a leitura que viu os servidores esperados voltou (sempre
 *       antes da primeira rodada do laco, que comeca depois). O resumo de convergencia grava o campo, que
 *       {@code analysis/comparar_bracos.py} usa como origem do Delta_conv ({@code CAMPO_RELIGAMENTO}).</li>
 * </ol>
 *
 * <p>Sem AC-5 aplicado (braco A, braco B sem acao, AC-5 que falhou), nao ha espera e o laco comeca logo, como
 * antes. So texto e aritmetica aqui: a sonda e o relogio sao injetados (testes sem rede e sem dormir).</p>
 */
final class Religamento {

    /** Prefixo fixo da linha de sinal; o orquestrador casa a linha inteira por expressao regular. */
    static final String PREFIXO = "Fim da carga:";
    /** Valor de {@code ac5_alvo} quando nenhum AC-5 ficou APLICADA. */
    static final String SEM_AC5 = "-";
    /**
     * Intervalo entre as leituras da espera: o periodo da sonda de topologia do controlador (2 s, B-26.2). Nenhum
     * numero novo: a resolucao do instante fica a mesma da sonda que alimenta o controlador.
     */
    static final long PASSO_NS = LigacaoControlador.PERIODO_SONDA_NS;

    private Religamento() {
    }

    /**
     * Linha de sinal: {@code Fim da carga: rep=<n> t_fim_carga_ns=<n> ac5_alvo=<host:porta|->}.
     *
     * @param ac5Alvo rotulo do no parado pelo AC-5 ({@code host:porta} de {@code --controller-admin}), ou
     *                {@code null}/vazio sem AC-5 aplicada
     */
    static String linhaSinal(int rep, long tFimCargaNs, String ac5Alvo) {
        String alvo = (ac5Alvo == null || ac5Alvo.isBlank()) ? SEM_AC5 : ac5Alvo;
        for (int i = 0; i < alvo.length(); i++) {
            if (Character.isWhitespace(alvo.charAt(i))) {
                throw new IllegalArgumentException("alvo do AC-5 com espaco: '" + alvo + "'");
            }
        }
        return PREFIXO + " rep=" + rep + " t_fim_carga_ns=" + tFimCargaNs + " ac5_alvo=" + alvo;
    }

    /**
     * Resultado da espera, gravado no resumo de convergencia.
     *
     * @param ac5Alvo        alvo do AC-5 aplicado, ou {@code null} sem espera
     * @param tSinalNs       instante em que a linha de sinal saiu (relogio dos {@code end_ns})
     * @param prazoNs        prazo usado, ou {@code null} sem espera
     * @param tReligamentoNs instante em que a carga viu os servidores esperados; {@code null} sem espera ou com o
     *                       prazo esgotado
     * @param esgotado       houve espera e o prazo acabou sem a topologia completa
     * @param leituras       leituras da sonda feitas na espera
     */
    record Resultado(String ac5Alvo, long tSinalNs, Long prazoNs, Long tReligamentoNs, boolean esgotado,
                     int leituras) {

        static Resultado semEspera(long tSinalNs) {
            return new Resultado(null, tSinalNs, null, null, false, 0);
        }

        /** Linha de stdout depois da espera. */
        String linha() {
            return "Religamento: ac5_alvo=" + (ac5Alvo == null ? SEM_AC5 : ac5Alvo) + " esgotado=" + esgotado
                    + " leituras=" + leituras + " t_religamento_ns=" + tReligamentoNs + " prazo_ns=" + prazoNs;
        }
    }

    /**
     * Le a sonda ate ela informar pelo menos {@code esperados} servidores na topologia ou ate o prazo (contado do
     * inicio da espera). Uma leitura que falha (o no ainda subindo: a REST nao responde) conta como topologia
     * incompleta. A ultima leitura e feita no prazo; o instante devolvido e o do fim da leitura que viu a
     * topologia completa.
     */
    static Resultado esperar(ConvergenciaLoop.SondaCluster sonda, int esperados, ConvergenciaLoop.Relogio relogio,
                             String ac5Alvo, long tSinalNs, long prazoNs, long passoNs) {
        if (prazoNs <= 0 || passoNs <= 0 || esperados <= 0) {
            throw new IllegalArgumentException("prazo, passo e servidores esperados precisam ser > 0: prazo="
                    + prazoNs + " passo=" + passoNs + " esperados=" + esperados);
        }
        if (ac5Alvo == null || ac5Alvo.isBlank()) {
            throw new IllegalArgumentException("espera do religamento sem alvo do AC-5");
        }
        long inicio = relogio.agoraNs();
        int leituras = 0;
        while (true) {
            leituras++;
            boolean completa;
            try {
                completa = sonda.ler().servidoresNaTopologia() >= esperados;
            } catch (Exception e) {
                completa = false;
            }
            long agora = relogio.agoraNs();
            if (completa) {
                return new Resultado(ac5Alvo, tSinalNs, prazoNs, agora, false, leituras);
            }
            long decorrido = agora - inicio;
            if (decorrido >= prazoNs) {
                return new Resultado(ac5Alvo, tSinalNs, prazoNs, null, true, leituras);
            }
            try {
                relogio.dormirNs(Math.min(passoNs, prazoNs - decorrido));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new Resultado(ac5Alvo, tSinalNs, prazoNs, null, true, leituras);
            }
        }
    }
}
