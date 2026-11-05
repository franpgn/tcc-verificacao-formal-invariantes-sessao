package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * W-2a (B-44.2): politica de acao por assinatura, a tabela da secao 5 de
 * {@code docs/acoes-controlador-cluster-2026-11-05.md} ({@code cc08644}). A cada janela escolhe uma de tres
 * politicas de reacao operacional (secao 4; decisao do autor de 05/11):
 *
 * <ul>
 *   <li><b>P1</b>, isolar o no suspeito ({@link AcaoCluster.Tipo#AC_5}): em CONSERVADOR, vista completa nos
 *       nos em todas as amostras da janela ({@link EstadoTopologia#vistaCompleta()}), S_lag no teto
 *       ({@code lagMaxMs >= tetoLagMs}) com o mesmo {@code noSuspeito} em {@code kLag} janelas seguidas, o
 *       suspeito respondeu a REST em todas as amostras e a cota ainda nao foi usada.</li>
 *   <li><b>P2</b>, adiar o rebalanceamento ({@link AcaoCluster.Tipo#AC_2}, forma global): em CONSERVADOR, um
 *       no sem resposta e os outros respondendo ({@link EstadoTopologia#noSemResposta()}), sem vistas
 *       divergentes.</li>
 *   <li><b>P0</b>, nenhuma acao, com o motivo registrado.</li>
 * </ul>
 *
 * <p>Reversao do AC-2 (secao 3.2), em qualquer perfil: vistas divergentes entre nos que respondem
 * (particao detectada); o no ausente voltou a responder; ou passou {@code T_reb} desde a aplicacao. Depois do
 * teto, o AC-2 nao volta para o mesmo no ate ele responder de novo (senao o teto nao limitaria nada). O R4 e
 * o encerramento sao do laco e do executor (pilha), nao desta classe. O AC-5 nunca e revertido.</p>
 *
 * <p>Duas escolhas que a tabela nao fixa, registradas no relatorio do W-2a: (1) o AC-2 nao e aplicado para
 * um no que a propria politica parou pelo AC-5 (ele nao volta na execucao, e o AC-2 so estenderia a copia
 * unica ate o teto); (2) a persistencia de {@code kLag} janelas zera quando a politica nao foi consultada numa
 * janela (janela do R4), detectado pelo salto de {@code fimNs} maior que {@code W}.</p>
 *
 * <p>Pura: sem E/S e sem relogio (o tempo e o {@code fimNs} da decisao). Guarda estado entre janelas
 * (persistencia, cota, inicio do AC-2), e por isso uma instancia serve a uma execucao so. Chamada sempre pela
 * thread do laco.</p>
 *
 * <p>O tipo da acao de P1 e parametro: hoje so o AC-5 se qualifica (nao reversivel, alvo num no). Se o B-44.3
 * aprovar a AC-6 (exclusao forcada pelo JGroups), ela entra como mais uma constante de
 * {@link AcaoCluster.Tipo} com as mesmas propriedades e e passada aqui, sem mudar a tabela.</p>
 */
public final class PoliticaPorAssinatura implements PoliticaDeAcao {

    public static final String P0 = "P0";
    public static final String P1 = "P1";
    public static final String P2 = "P2";

    /** Motivos gravados na linha {@code politica}. */
    public static final String SEM_ASSINATURA = "sem-assinatura";
    public static final String ISOLAR_SUSPEITO = "isolar-suspeito";
    public static final String COTA_P1_ESGOTADA = "cota-p1-esgotada";
    public static final String ADIAR_REBALANCEAMENTO = "adiar-rebalanceamento";
    public static final String AC2_MANTIDA = "ac2-mantida";
    public static final String TREB_ESGOTADO = "treb-esgotado";
    public static final String NO_PARADO_PELA_POLITICA = "no-parado-pela-politica";
    public static final String VISTAS_DIVERGENTES = "vistas-divergentes";
    public static final String NO_VOLTOU = "no-voltou";
    public static final String TETO_TREB = "teto-treb";

    private final ParametrosPolitica p;
    private final List<String> caches;
    private final AcaoCluster.Tipo tipoP1;
    private final AcaoCluster ac2;

    // estado entre janelas (so a thread do laco)
    private String suspeito;
    private int persistencia;
    private Long ultimoFimNs;
    private int usadasP1;
    private final Set<String> paradosPelaPolitica = new HashSet<>();
    private Long ac2InicioNs;
    private String ac2No;
    private String tRebEsgotadoNo;

    /** P1 com o AC-5. */
    public PoliticaPorAssinatura(ParametrosPolitica parametros, List<String> cachesDoModo) {
        this(parametros, cachesDoModo, AcaoCluster.Tipo.AC_5);
    }

    /**
     * @param parametros   {@code kLag}, {@code tetoLagMs}, {@code tRebNs}, {@code cotaAc5}, sem padrao
     * @param cachesDoModo os caches do modo ativo (leitura de volta do atuador)
     * @param tipoP1       tipo da acao de P1: nao reversivel e com alvo num no
     */
    public PoliticaPorAssinatura(ParametrosPolitica parametros, List<String> cachesDoModo, AcaoCluster.Tipo tipoP1) {
        this.p = Objects.requireNonNull(parametros, "parametros");
        Objects.requireNonNull(tipoP1, "tipoP1");
        if (tipoP1.reversivel() || tipoP1.alvo() != AcaoCluster.RegraAlvo.UM_NO) {
            throw new IllegalArgumentException("acao de P1 tem de ser nao reversivel e num no: " + tipoP1.rotulo());
        }
        this.tipoP1 = tipoP1;
        this.ac2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, cachesDoModo, null, null);
        this.caches = this.ac2.caches();
    }

    @Override
    public String nome() {
        return "por-assinatura";
    }

    /** Acoes de P1 ja pedidas nesta execucao. */
    public int usadasP1() {
        return usadasP1;
    }

    @Override
    public PlanoDeAcao planejar(Decisao d, EstadoTopologia top, EstadoDefasagem lag, List<AcaoCluster> ativas) {
        atualizarPersistencia(d, lag);
        if (tRebEsgotadoNo != null && top.respondeuEmAlguma(tRebEsgotadoNo)) {
            tRebEsgotadoNo = null;
        }

        // ---- reversao do AC-2 (qualquer perfil)
        boolean ac2Ativa = ativas.stream().anyMatch(a -> a.tipo() == AcaoCluster.Tipo.AC_2);
        List<AcaoCluster> reverter = new ArrayList<>();
        String motivoReversao = null;
        String noReversao = null; // V-7: no do AC-2 revertido
        if (!ac2Ativa) {
            ac2InicioNs = null;
            ac2No = null;
        } else {
            if (ac2InicioNs == null) {
                ac2InicioNs = d.fimNs(); // ativa sem registro de inicio: conta daqui (conservador)
            }
            if (top.vistasDivergentes()) {
                motivoReversao = VISTAS_DIVERGENTES;
            } else if (ac2No != null && top.respondeuEmAlguma(ac2No)) {
                motivoReversao = NO_VOLTOU;
            } else if (d.fimNs() - ac2InicioNs >= p.tRebNs()) {
                motivoReversao = TETO_TREB;
                tRebEsgotadoNo = ac2No;
            }
            if (motivoReversao != null) {
                noReversao = ac2No;
                for (AcaoCluster a : ativas) {
                    if (a.tipo() == AcaoCluster.Tipo.AC_2) {
                        reverter.add(a);
                    }
                }
                ac2InicioNs = null;
                ac2No = null;
                ac2Ativa = false;
            }
        }

        if (d.perfil() != Perfil.CONSERVADOR) {
            return reverter.isEmpty() ? PlanoDeAcao.VAZIO
                    : new PlanoDeAcao(reverter, List.of(), P0, motivoReversao, noReversao);
        }

        // ---- P1: vista completa e S_lag no teto com o mesmo suspeito em k janelas
        if (top.vistaCompleta() && suspeito != null && persistencia >= p.kLag() && top.respondeuEmTodas(suspeito)) {
            if (usadasP1 < p.cotaAc5()) {
                usadasP1++;
                paradosPelaPolitica.add(suspeito);
                return new PlanoDeAcao(reverter, List.of(new AcaoCluster(tipoP1, caches, suspeito, null)), P1,
                        ISOLAR_SUSPEITO, suspeito);
            }
            return new PlanoDeAcao(reverter, List.of(), P0, COTA_P1_ESGOTADA, suspeito);
        }

        // ---- P2: um no sem resposta, os outros respondendo, sem vistas divergentes
        Optional<String> sem = top.noSemResposta();
        if (sem.isPresent() && !top.vistasDivergentes()) {
            String no = sem.get();
            if (paradosPelaPolitica.contains(no)) {
                return new PlanoDeAcao(reverter, List.of(), P0, NO_PARADO_PELA_POLITICA, no);
            }
            if (ac2Ativa) {
                return new PlanoDeAcao(reverter, List.of(), P2, AC2_MANTIDA, no);
            }
            if (!reverter.isEmpty()) {
                return new PlanoDeAcao(reverter, List.of(), P0, motivoReversao, noReversao);
            }
            if (no.equals(tRebEsgotadoNo)) {
                return new PlanoDeAcao(List.of(), List.of(), P0, TREB_ESGOTADO, no);
            }
            ac2InicioNs = d.fimNs();
            ac2No = no;
            return new PlanoDeAcao(List.of(), List.of(ac2), P2, ADIAR_REBALANCEAMENTO, no);
        }

        return reverter.isEmpty() ? new PlanoDeAcao(List.of(), List.of(), P0, SEM_ASSINATURA)
                : new PlanoDeAcao(reverter, List.of(), P0, motivoReversao, noReversao);
    }

    private void atualizarPersistencia(Decisao d, EstadoDefasagem lag) {
        if (ultimoFimNs != null && d.fimNs() - ultimoFimNs > d.parametros().wNs()) {
            suspeito = null; // janela sem consulta (R4): a sequencia quebrou
            persistencia = 0;
        }
        ultimoFimNs = d.fimNs();
        Double lagMax = lag.lagMaxMs();
        String no = lag.noSuspeito();
        if (lagMax == null || no == null || lagMax < p.tetoLagMs()) {
            suspeito = null;
            persistencia = 0;
            return;
        }
        if (no.equals(suspeito)) {
            persistencia++;
        } else {
            suspeito = no;
            persistencia = 1;
        }
    }
}
