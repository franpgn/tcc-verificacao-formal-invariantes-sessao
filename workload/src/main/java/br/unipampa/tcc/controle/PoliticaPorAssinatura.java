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
 *       divergentes. <b>So com {@link ParametrosPolitica#p2Habilitada()}</b> (D-B de 05/11: a P2 saiu do braco B e
 *       ficou exploratoria, P-39 e P-40). No modo de avaliacao ({@code p2Habilitada = false}) a mesma assinatura
 *       vira P0 com o motivo {@value #P2_DESABILITADA} e o no ausente, e o AC-2 nunca e pedido.</li>
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
 * <p>O tipo da acao de P1 e parametro ({@link ParametrosPolitica#acaoP1()}). <b>P1-IC (06/11, G-1 a G-3 do
 * coordenador sobre o A4, {@code docs/relatorio-a4-2026-11-06.md} secao 8):</b> o padrao passa a ser o
 * {@link AcaoCluster.Tipo#AC_7}, ignorar so o cache de sessoes no suspeito; o AC-5 (parada) fica para comparacao.
 * A condicao de disparo do P1 nao muda. O AC-7 e reversivel, e esta politica o desfaz, em qualquer perfil, pela
 * primeira regra que valer na janela:</p>
 * <ul>
 *   <li>{@value #DESFAZER_ALVO_SOZINHO} (R6): o alvo respondeu e informou {@code number_of_nodes} de minoria
 *       ({@code <= n/2}) em alguma amostra da janela; dali ate a fusao o {@code DENY_READ_WRITES} recusa no alvo por
 *       conta propria, e o alvo volta a servir na fusao;</li>
 *   <li>{@value #DESFAZER_APOS_FUSAO} (R7, reserva): uma particao foi vista depois da acao (algum no que respondeu
 *       informou menos nos que os consultados, ou vistas divergentes) e a vista esta completa de novo;</li>
 *   <li>{@value #SUSPEITA_DESFEITA}: nenhuma particao foi vista desde a acao e o S_lag ficou determinado e abaixo do
 *       teto, com vista completa, por {@code kLag} janelas seguidas (corte curto que curou antes da deteccao). Sem
 *       esta regra o alvo ignoraria o cache ate o fim da execucao: o A4 mostrou que a fusao nao desfaz a acao, e sem
 *       deteccao nao ha fusao.</li>
 * </ul>
 * <p>A rota do desfazer (no proprio alvo ou por um no da maioria) e do adaptador, que le o estado do alvo na hora. O
 * no do AC-7 continua respondendo, entao ele nao entra em {@code paradosPelaPolitica} (que so vale para o AC-5).</p>
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
    /** D-B: assinatura do P2 com {@code p2Habilitada = false} (modo de avaliacao); nenhuma acao. */
    public static final String P2_DESABILITADA = "p2-desabilitada";
    /** P1-IC (R6): o alvo do AC-7 se viu em minoria; desfazer no proprio alvo. */
    public static final String DESFAZER_ALVO_SOZINHO = "desfazer-alvo-sozinho";
    /** P1-IC (R7): particao vista depois do AC-7 e vista completa de novo; desfazer depois da fusao. */
    public static final String DESFAZER_APOS_FUSAO = "desfazer-apos-fusao";
    /** P1-IC: sem particao vista, o S_lag voltou abaixo do teto por {@code kLag} janelas; desfazer. */
    public static final String SUSPEITA_DESFEITA = "suspeita-desfeita";

    private final ParametrosPolitica p;
    private final List<String> caches;
    private final AcaoCluster.Tipo tipoP1;
    /** Caches da acao de P1: so o de sessoes no AC-7 (R1, G-2); os do modo no AC-5. */
    private final List<String> cachesP1;
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
    // P1-IC: desfazer do AC-7
    private boolean particaoVistaDesdeP1;
    private int janelasAbaixoDoTeto;

    /**
     * P1 com a acao de {@link ParametrosPolitica#acaoP1()}; no AC-7, o cache de sessoes e o primeiro dos caches do
     * modo (a ordem de {@code PerfilReplicacao.caches()}).
     */
    public PoliticaPorAssinatura(ParametrosPolitica parametros, List<String> cachesDoModo) {
        this(parametros, cachesDoModo, acaoDe(parametros), primeiro(cachesDoModo), true);
    }

    /**
     * P1-IC: P1 com a acao de {@link ParametrosPolitica#acaoP1()} e o cache de sessoes dado (o unico que o AC-7 ignora).
     *
     * @param cacheSessoes um dos {@code cachesDoModo}
     */
    public PoliticaPorAssinatura(ParametrosPolitica parametros, List<String> cachesDoModo, String cacheSessoes) {
        this(parametros, cachesDoModo, acaoDe(parametros), cacheSessoes, true);
    }

    /**
     * @param parametros   {@code kLag}, {@code tetoLagMs}, {@code tRebNs}, {@code cotaAc5}, sem padrao
     * @param cachesDoModo os caches do modo ativo (leitura de volta do atuador)
     * @param tipoP1       tipo da acao de P1, explicito (ignora {@link ParametrosPolitica#acaoP1()}): AC-5 ou AC-7
     */
    public PoliticaPorAssinatura(ParametrosPolitica parametros, List<String> cachesDoModo, AcaoCluster.Tipo tipoP1) {
        this(parametros, cachesDoModo, tipoP1, primeiro(cachesDoModo), true);
    }

    private PoliticaPorAssinatura(ParametrosPolitica parametros, List<String> cachesDoModo, AcaoCluster.Tipo tipoP1,
                                  String cacheSessoes, boolean interno) {
        this.p = Objects.requireNonNull(parametros, "parametros");
        Objects.requireNonNull(tipoP1, "tipoP1");
        if (tipoP1 != AcaoCluster.Tipo.AC_5 && tipoP1 != AcaoCluster.Tipo.AC_7) {
            throw new IllegalArgumentException("acao de P1 tem de ser AC-5 ou AC-7 (num no): " + tipoP1.rotulo());
        }
        this.tipoP1 = tipoP1;
        this.ac2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, cachesDoModo, null, null);
        this.caches = this.ac2.caches();
        if (cacheSessoes == null || !caches.contains(cacheSessoes)) {
            throw new IllegalArgumentException("cache de sessoes '" + cacheSessoes + "' fora dos caches do modo " + caches);
        }
        this.cachesP1 = tipoP1 == AcaoCluster.Tipo.AC_7 ? List.of(cacheSessoes) : caches;
    }

    private static AcaoCluster.Tipo acaoDe(ParametrosPolitica p) {
        return Objects.requireNonNull(p, "parametros").acaoP1();
    }

    private static String primeiro(List<String> caches) {
        return caches == null || caches.isEmpty() ? null : caches.get(0);
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

        // ---- P1-IC: desfazer do AC-7 (qualquer perfil)
        AcaoCluster ac7 = null;
        for (AcaoCluster a : ativas) {
            if (a.tipo() == AcaoCluster.Tipo.AC_7) {
                ac7 = a;
                break;
            }
        }
        if (ac7 == null) {
            particaoVistaDesdeP1 = false;
            janelasAbaixoDoTeto = 0;
        } else {
            String motivo = motivoDesfazerAc7(top, lag, ac7.alvo());
            if (motivo != null) {
                particaoVistaDesdeP1 = false;
                janelasAbaixoDoTeto = 0;
                List<AcaoCluster> rev = new ArrayList<>(reverter);
                rev.add(ac7);
                return new PlanoDeAcao(rev, List.of(), P0, motivo, ac7.alvo()).comUsadasP1(usadasP1);
            }
        }

        if (d.perfil() != Perfil.CONSERVADOR) {
            return reverter.isEmpty() ? PlanoDeAcao.VAZIO
                    : new PlanoDeAcao(reverter, List.of(), P0, motivoReversao, noReversao).comUsadasP1(usadasP1);
        }
        return comUsadas(planejarConservador(top, reverter, motivoReversao, noReversao, ac2Ativa, d));
    }

    private PlanoDeAcao comUsadas(PlanoDeAcao plano) {
        return plano.politica() == null ? plano : plano.comUsadasP1(usadasP1);
    }

    /**
     * Regra de desfazer do AC-7 que vale nesta janela, ou {@code null} (ver a documentacao da classe). Atualiza o
     * registro de particao vista e a contagem de janelas com o S_lag abaixo do teto.
     */
    private String motivoDesfazerAc7(EstadoTopologia top, EstadoDefasagem lag, String alvo) {
        EstadoTopologia.No noAlvo = top.porNo().get(alvo);
        int n = top.porNo().size();
        if (noAlvo != null && noAlvo.respondeu() && n > 0 && noAlvo.menorNos() * 2 <= n) {
            return DESFAZER_ALVO_SOZINHO;
        }
        if (particaoVista(top)) {
            particaoVistaDesdeP1 = true;
        }
        if (particaoVistaDesdeP1) {
            return top.vistaCompleta() ? DESFAZER_APOS_FUSAO : null;
        }
        Double lagMax = lag.lagMaxMs();
        boolean abaixo = top.vistaCompleta() && lagMax != null && lagMax < p.tetoLagMs();
        janelasAbaixoDoTeto = abaixo ? janelasAbaixoDoTeto + 1 : 0;
        return janelasAbaixoDoTeto >= p.kLag() ? SUSPEITA_DESFEITA : null;
    }

    /** Algum no que respondeu informou menos membros que os nos consultados, ou ha vistas divergentes. */
    private static boolean particaoVista(EstadoTopologia top) {
        int n = top.porNo().size();
        for (EstadoTopologia.No x : top.porNo().values()) {
            if (x.respondeu() && x.menorNos() < n) {
                return true;
            }
        }
        return top.vistasDivergentes();
    }

    /** O resto da tabela em CONSERVADOR: P1, P2 e P0 (o que existia antes do P1-IC, sem mudanca). */
    private PlanoDeAcao planejarConservador(EstadoTopologia top, List<AcaoCluster> reverter, String motivoReversao,
                                            String noReversao, boolean ac2Ativa, Decisao d) {

        // ---- P1: vista completa e S_lag no teto com o mesmo suspeito em k janelas
        if (top.vistaCompleta() && suspeito != null && persistencia >= p.kLag() && top.respondeuEmTodas(suspeito)) {
            if (usadasP1 < p.cotaAc5()) {
                usadasP1++;
                if (tipoP1 == AcaoCluster.Tipo.AC_5) {
                    paradosPelaPolitica.add(suspeito); // o AC-7 nao para o no: ele continua respondendo
                }
                particaoVistaDesdeP1 = false;
                janelasAbaixoDoTeto = 0;
                return new PlanoDeAcao(reverter, List.of(new AcaoCluster(tipoP1, cachesP1, suspeito, null)), P1,
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
            if (!p.p2Habilitada()) {
                // D-B: nunca pede o AC-2. Uma reversao pendente (so possivel com um AC-2 vindo de fora) continua valendo
                return reverter.isEmpty() ? new PlanoDeAcao(List.of(), List.of(), P0, P2_DESABILITADA, no)
                        : new PlanoDeAcao(reverter, List.of(), P0, motivoReversao, noReversao);
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
