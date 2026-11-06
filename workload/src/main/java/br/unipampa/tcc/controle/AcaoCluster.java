package br.unipampa.tcc.controle;

import java.util.List;
import java.util.Objects;

/**
 * W-3 (B-28.2), W-2a (B-44.2): uma acao do controlador sobre o <i>cluster</i>. Aqui a acao e so um valor;
 * a chamada REST de cada tipo fica no adaptador ({@code session.AtuadorRestInfinispan}). Especificacao:
 * {@code docs/acoes-controlador-cluster-2026-11-05.md} (branch {@code docs/plano-implementacao-controlador},
 * {@code cc08644}), secoes 3, 5 e 5.1. AC-1 (DEGRADED antecipado, B-44.1 secao 4.1) e AC-3 ({@code remote-timeout},
 * P-31) ficaram fora do braco B e foram retirados (P-36).
 *
 * <p>O que muda de um tipo para outro fica no proprio {@link Tipo}: se a acao entra na pilha de reversao
 * ({@link Tipo#reversivel()}), a regra do alvo ({@link Tipo#alvo()}) e o prazo da chamada inteira
 * ({@link Tipo#prazoNs(long)}). O {@link ExecutorDeAcoes} e a {@link PoliticaPorAssinatura} so leem essas
 * propriedades, de modo que um tipo novo (por exemplo a AC-6, exclusao forcada pelo JGroups,
 * {@code docs/opcoes-arquitetura-configuracao-2026-11-05.md} secao 2.4, se o B-44.3 aprovar) entra como uma
 * constante a mais e um adaptador a mais ({@link AtuadorPorTipo}), sem mudar o executor.</p>
 *
 * @param tipo   tipo da acao
 * @param caches caches do modo ativo (nao vazio); no AC-2 e no AC-5 servem so a leitura de volta e ao registro; no
 *               AC-7, exatamente um (o cache a ignorar, R1 do A4)
 * @param alvo   rotulo do no de destino, ou {@code null} quando a acao vale para o <i>cluster</i>; a regra
 *               de cada tipo esta em {@link Tipo#alvo()}
 * @param valor  valor a aplicar, ou {@code null} (nenhum tipo do braco B usa valor)
 */
public record AcaoCluster(Tipo tipo, List<String> caches, String alvo, String valor) {

    /** Como o tipo trata o {@code alvo}. */
    public enum RegraAlvo {
        /** Acao global: {@code alvo} tem de ser {@code null}. */
        PROIBIDO,
        /** Acao num unico no: {@code alvo} obrigatorio, um rotulo so (sem virgula nem espaco). */
        UM_NO
    }

    /** Tipos de acao. */
    public enum Tipo {
        /**
         * Rebalanceamento suspenso, <b>forma global</b>: {@code POST /rest/v2/container?action=disable-rebalancing}
         * (secao 3.2 do documento de 05/11). A forma por cache se perde quando o coordenador cai (P-34; B-44.1b
         * R2, o novo coordenador rebalanceou 4,6 s depois); a global sobreviveu (R3). Reversivel
         * ({@code enable-rebalancing}), entra na pilha LIFO. Prazo {@code T_cham}. A chave inclui os caches: dois
         * AC-2 com listas diferentes seriam acoes distintas no executor e o mesmo sinalizador no <i>cluster</i>; a
         * {@link PoliticaPorAssinatura} so monta uma instancia, entao isso nao acontece (V-W2a O12).
         */
        AC_2("AC-2", true, RegraAlvo.PROIBIDO, 0L),
        /**
         * Parada do no suspeito pela REST local: {@code POST /rest/v2/server/?action=stop} mandado ao proprio
         * no (secao 3.1 do documento de 05/11; achado N-1: o servidor agenda a propria saida para 3 s depois,
         * {@code Server.java:208} {@code SHUTDOWN_DELAY_SECONDS = 3}, sem falar com o coordenador). <b>Nao
         * reversivel</b> dentro da execucao: religar exige reiniciar o conteiner, o que o P-5 tira do caminho do
         * controlador; o orquestrador recria o <i>cluster</i> depois da execucao. Prazo
         * {@code T_cham + 15 s} (P-42, decisao do coordenador de 05/11; era {@code T_cham + 3 s} na secao 5.1): sob
         * corte, o servidor anuncia a parada 3 s depois do pedido, mas a saude do no isolado so deixa de responder
         * 13,1 s (SYNC) a 13,3 s (ASYNC) depois dele (I-3 do B-44.2, {@code docs/relatorio-b44-2-2026-11-05.md}
         * secao 7); com 3 s o atuador gravava {@code FALHOU} com o no parando. Sem corte, 3,06 a 3,24 s.
         */
        AC_5("AC-5", false, RegraAlvo.UM_NO, 15_000_000_000L),
        /**
         * P1-IC (06/11, G-1 e G-2 do coordenador sobre o A4, {@code docs/relatorio-a4-2026-11-06.md}): o no suspeito
         * passa a ignorar o cache de sessoes, {@code POST /rest/v2/server/ignored-caches/{cache}} mandado a REST dele.
         * O conjunto em memoria do no muda antes da gravacao replicada, e o Hot Rod dele recusa o cache em dezenas de
         * milissegundos (16 a 70 ms no A4, contra 13,1 s da parada sob o mesmo corte). Um no e <b>um cache</b> (R1:
         * a lista de ignorados e uma chave so, gravada inteira). <b>Reversivel</b> ({@code DELETE} no mesmo caminho):
         * entra na pilha, e o R4, o encerramento e o gancho a desfazem. Prazo {@code T_cham + 15 s}: a reversao espera
         * a resposta do POST anterior (R2, um pedido por vez), que sob corte so vem com o 500 do {@code remote-timeout}
         * de 15 s do cache de estado; com esse prazo o pior caso do {@code T_enc} do braco B nao muda.
         */
        AC_7("AC-7", true, RegraAlvo.UM_NO, 15_000_000_000L);

        private final String rotulo;
        private final boolean reversivel;
        private final RegraAlvo alvo;
        private final long acrescimoPrazoNs;

        Tipo(String rotulo, boolean reversivel, RegraAlvo alvo, long acrescimoPrazoNs) {
            this.rotulo = rotulo;
            this.reversivel = reversivel;
            this.alvo = alvo;
            this.acrescimoPrazoNs = acrescimoPrazoNs;
        }

        public String rotulo() {
            return rotulo;
        }

        /**
         * {@code true}: a acao entra na pilha LIFO do {@link ExecutorDeAcoes} e e desfeita no R4, no
         * encerramento e pelo gancho da JVM. {@code false}: fica fora da pilha, nunca e desfeita pelo
         * controlador e a linha {@code acao} sai com {@code "reversao": "orquestrador"}.
         */
        public boolean reversivel() {
            return reversivel;
        }

        public RegraAlvo alvo() {
            return alvo;
        }

        /**
         * Prazo da chamada INTEIRA deste tipo ao atuador (todos os pedidos e a leitura de volta).
         *
         * @param tChamNs {@code T_cham} (2 s, B-26.2)
         */
        public long prazoNs(long tChamNs) {
            return tChamNs + acrescimoPrazoNs;
        }
    }

    public AcaoCluster {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(caches, "caches");
        if (caches.isEmpty()) {
            throw new IllegalArgumentException("acao sem caches");
        }
        for (String c : caches) {
            if (c == null || c.isBlank()) {
                throw new IllegalArgumentException("nome de cache vazio em " + caches);
            }
        }
        caches = List.copyOf(caches);
        if (tipo == Tipo.AC_7 && caches.size() != 1) {
            throw new IllegalArgumentException("AC-7 ignora exatamente um cache (R1 do A4): " + caches);
        }
        switch (tipo.alvo()) {
            case PROIBIDO -> {
                if (alvo != null) {
                    throw new IllegalArgumentException(tipo.rotulo() + " e global e nao aceita alvo: '" + alvo + "'");
                }
            }
            case UM_NO -> {
                if (alvo == null || alvo.isBlank() || alvo.indexOf(',') >= 0 || !alvo.strip().equals(alvo)
                        || alvo.chars().anyMatch(Character::isWhitespace)) {
                    throw new IllegalArgumentException(tipo.rotulo() + " exige exatamente um no como alvo: '"
                            + alvo + "'");
                }
            }
        }
    }

    /**
     * Identidade da acao para a idempotencia e a pilha de reversao: tipo, caches e alvo. O valor nao
     * entra (a mesma acao nao e aplicada duas vezes com valores diferentes).
     */
    public String chave() {
        return tipo.rotulo() + "|" + String.join(",", caches) + "|" + (alvo == null ? "*" : alvo);
    }
}
