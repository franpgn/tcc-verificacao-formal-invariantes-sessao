package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCacheManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Operações O1 a O7 do protocolo de estado de sessão, conforme Seção 4.1 da monografia.
 *
 * O1 login(id, cred): cria nova sessão para id se OPEN.
 * O2 validate(s): retorna estado lógico da sessão s.
 * O3 logout(s): marca s como Invalid com motivo logout (idempotente).
 * O4 incrementFailure(id): incrementa contador da id; bloqueia se >= limite.
 * O5 resetFailures(id): zera contador da id.
 * O6 block(id, motivo): marca id como Blocked e invalida sessões ativas associadas.
 * O7 unblock(id): remove a marca Blocked.
 *
 * Cada operação retorna um {@link OpResult} e registra timestamps de início e fim em
 * {@link LatencyRegistry}.
 *
 * <p><b>B-23 — detecção de I1/I4 (ver docs/plano-consolidacao-b21-b23-b24-2026-10-02.md
 * §3.2):</b> {@link #invalidacoesConfirmadas} rastreia, por {@code sid}, o último motivo
 * de invalidação que esta instância observou localmente (via {@link #logout}, {@link
 * #block} ou a primeira leitura de {@link #validate} que encontra a sessão já INVALID).
 * {@link #validate} compara cada leitura nova contra esse registro para detectar I1
 * (uma invalidação confirmada "ressuscitando" para VALID) e I4 (o motivo lido divergindo
 * do motivo confirmado). <b>Limitação de escopo, não é defeito:</b> este rastreamento só
 * vale <i>dentro de uma repetição</i> — {@code WorkloadMain.executarRepeticao} cria um
 * {@code SessionOps}/{@code InvariantAuditor} novos a cada repetição, então o mapa
 * começa vazio a cada uma; isso é consistente com o isolamento entre repetições já
 * existente no desenho atual.</p>
 *
 * <p><b>B-43.6, auditor sem corrida (achado A1 da V-B43.2):</b> uma leitura so pode ser acusada
 * de I1/I4 contra uma confirmacao de invalidacao que ja existia <i>antes</i> de a leitura comecar.
 * {@link #validate} e {@link #logout} tiram um instantaneo {@code confirmadoAntes} do mapa antes do
 * {@code sessions.get}, e {@link #block} copia o mapa inteiro antes de {@code sessions.entrySet()}.
 * Antes de B-43.6 a comparacao era feita contra o estado do mapa <i>depois</i> da leitura: uma
 * leitura VALID concorrente com a primeira invalidacao da chave (linearizavel nas duas ordens)
 * virava I1 falsa. Esse caso agora e contado em {@link #i1Concorrentes()} /
 * {@link #i4Concorrentes()}, impressos no stdout ({@link #linhaAuditor}), e nao em violacao. Uma
 * leitura velha real (iniciada depois da confirmacao) continua sendo acusada.</p>
 */
public class SessionOps {

    public static final int LIMITE_TENTATIVAS = 5;

    /** Nomes canônicos das operações O1–O5 na coluna {@code operation} do CSV. */
    public static final String OP_LOGIN = "Login";              // O1
    public static final String OP_VALIDATE = "Validate";        // O2
    public static final String OP_LOGOUT = "Logout";            // O3
    public static final String OP_INCREMENT_FAILURE = "IncrementFailure"; // O4
    public static final String OP_RESET_FAILURES = "ResetFailures";       // O5
    /** B-33: O6 e O7 passam a emitir evento no CSV (antes nao emitiam). */
    public static final String OP_BLOCK = "Block";              // O6
    public static final String OP_UNBLOCK = "Unblock";          // O7
    /**
     * W-10: leitura da sonda de replay ({@link SondaReplay}): um Validate da sid antiga depois do logout,
     * feito por outro servidor de aplicacao. Tipo proprio no CSV de eventos, fora da mistura, do verificador
     * de sanidade, do p99 e da vazao util; so existe com {@code --s3-replay-probe}.
     */
    public static final String OP_REPLAY_VALIDATE = "ReplayValidate";

    private final Armazem<SessionState> sessions;
    /**
     * B-43.8: o mesmo armazem de sessoes, quando ele e versionado (em producao, sempre: ver os
     * construtores com {@code RemoteCacheManager}); {@code null} no caminho comum dos testes antigos,
     * que mantem o comportamento de B-43.6.
     */
    private final ArmazemVersionado<SessionState> sessoesVersionadas;
    private final Armazem<IdentityState> identities;
    private final LatencyRegistry latency;
    private final InvariantAuditor auditor;
    private final EventCsvWriter eventos;
    private final EpochClock relogio;
    private final String replica;
    /** B-33 (Q-10): rastro de operacoes confirmadas no cliente; {@code null} = desligado. */
    private final RastroCliente rastro;

    /**
     * B-23: ver javadoc da classe. Chave = sid; valor = motivo confirmado. B-33 (E2): no S3 os
     * varios servidores de aplicacao e o console administrativo compartilham este mapa (e o
     * observador do experimento, nao estado da aplicacao): uma invalidacao confirmada por um
     * deles e comparada com as leituras de todos.
     */
    private final ConcurrentHashMap<String, SessionState.Motivo> invalidacoesConfirmadas;

    /**
     * B-43.6: leituras que encontraram, DEPOIS de lidas, uma confirmacao incompativel que nao existia
     * antes de comecarem (VALID lido; ou INVALID com motivo diferente). Corrida do observador, nao
     * violacao. Ver o javadoc da classe.
     */
    private final LongAdder i1Concorrentes = new LongAdder();
    private final LongAdder i4Concorrentes = new LongAdder();

    /**
     * B-43.8 (DT-003): invalidacoes ({@code logout} ou cascata de {@code block}) que perderam a escrita
     * condicional para outra invalidacao e viraram no-op ao reler INVALID; e invalidacoes que esgotaram
     * {@link #TENTATIVAS_CONDICIONAIS} com a sessao VALID e a versao mudando. So diagnostico (DT-001).
     */
    private final LongAdder invalidacaoPerdida = new LongAdder();
    private final LongAdder conflitoEsgotado = new LongAdder();

    /** B-43.8: tentativas de escrita condicional por invalidacao, no total. */
    static final int TENTATIVAS_CONDICIONAIS = 3;

    /**
     * B-43.9 (DT-002): rotulos das leituras de sessao VALID sob identidade BLOCKED que NAO sao I2 (fora
     * de {@code S_viol}): dentro da janela da cascata concluida, ou com a cascata do bloqueio pendente
     * ({@code block} em erro ou ainda em curso). Saem no CSV de violacoes com esse rotulo.
     */
    static final String I2_JANELA_CASCATA = "I2_JANELA_CASCATA";
    static final String I2_CASCATA_INCOMPLETA = "I2_CASCATA_INCOMPLETA";

    /**
     * B-43.9 (DT-002): marcos do bloqueio por identidade ({@link MarcoBloqueio}). Mesmo ciclo de vida do
     * mapa de invalidacoes confirmadas: um por repeticao em S1/S2 e um compartilhado por
     * {@link S3Clientes} entre os servidores de aplicacao e o admin.
     */
    private final ConcurrentHashMap<String, MarcoBloqueio> marcos;

    /**
     * B-43.9: contadores com janela ({@link #definirPoliticaAuditor}) e totais sem janela (conferencia,
     * D11 do B-43.6). So diagnostico (DT-001), fora de {@code S_viol}.
     */
    private final LongAdder i2Concorrentes = new LongAdder();
    private final LongAdder i2JanelaCascata = new LongAdder();
    private final LongAdder i2CascataIncompleta = new LongAdder();
    private final LongAdder i2SemMarco = new LongAdder();
    private final LongAdder loginRevertido = new LongAdder();
    private final LongAdder i1ConcorrentesTotal = new LongAdder();
    private final LongAdder i4ConcorrentesTotal = new LongAdder();
    private final LongAdder i2ConcorrentesTotal = new LongAdder();
    private volatile WarmupPolicy politicaAuditor = WarmupPolicy.always();

    /**
     * W-3 (B-28.2): ouvinte de cada evento emitido (o laco de controle). Recebe os instantes ja em
     * epoch-ns ({@link EpochClock}), os mesmos do CSV de eventos, inclusive no <i>warm-up</i> e sem
     * trilha de CSV. Deve ser rapido e nunca lancar; uma excecao dele e engolida.
     */
    public interface OuvinteDeEvento {
        void evento(String operacao, long inicioNs, long fimNs, String codigoRetorno);
    }

    /** W-3: {@code null} (padrao) = desligado, fluxo identico ao de antes. */
    private volatile OuvinteDeEvento ouvinteDeEvento;

    /** W-3: liga (ou, com {@code null}, desliga) o ouvinte de eventos. */
    void definirOuvinteDeEvento(OuvinteDeEvento ouvinte) {
        this.ouvinteDeEvento = ouvinte;
    }

    /** B-43.9: janela de medicao dos contadores do auditor (a mesma do CSV); sem politica, sempre. */
    void definirPoliticaAuditor(WarmupPolicy politica) {
        this.politicaAuditor = politica != null ? politica : WarmupPolicy.always();
    }

    /** B-43.9: leituras VALID sob BLOCKED iniciadas antes de a identidade ser confirmada (DT-001). */
    long i2Concorrentes() {
        return i2Concorrentes.sum();
    }

    /** B-43.9: leituras VALID sob BLOCKED dentro da janela da cascata (DT-002). */
    long i2JanelaCascata() {
        return i2JanelaCascata.sum();
    }

    /** B-43.9: leituras VALID sob BLOCKED com a cascata do bloqueio pendente. */
    long i2CascataIncompleta() {
        return i2CascataIncompleta.sum();
    }

    /** B-43.9: I2 sem marco do bloqueio (so testes que montam BLOCKED direto no duble). */
    long i2SemMarco() {
        return i2SemMarco.sum();
    }

    /** B-43.9: logins revertidos pela dupla checagem (releitura BLOCKED depois do put da sessao). */
    long loginRevertidoPorBloqueio() {
        return loginRevertido.sum();
    }

    long i1ConcorrentesTotal() {
        return i1ConcorrentesTotal.sum();
    }

    long i4ConcorrentesTotal() {
        return i4ConcorrentesTotal.sum();
    }

    long i2ConcorrentesTotal() {
        return i2ConcorrentesTotal.sum();
    }

    /**
     * B-43.9: conta um evento do auditor no contador com janela (so na janela de medicao, a mesma do
     * CSV) e, se dado, no total sem janela.
     */
    private void contar(LongAdder comJanela, LongAdder total) {
        if (total != null) {
            total.increment();
        }
        if (politicaAuditor.deveRegistrar()) {
            comJanela.increment();
        }
    }

    /**
     * B-43.9, regra A (DT-002): classifica a leitura de sessao VALID {@code sid} sob a identidade
     * {@code identidade} lida BLOCKED, pelo instante {@code t0Sessao} (tomado antes do {@code get} da
     * sessao) contra o marco do bloqueio.
     *
     * <ul>
     *   <li>sem marco: I2 como antes de B-43.9, e {@code i2_sem_marco} (so testes que montam BLOCKED
     *       direto no duble; em producao deve dar 0);</li>
     *   <li>identidade ainda nao confirmada, ou {@code t0Sessao < marcoIdentidadeNs}:
     *       {@code i2_concorrentes} (corrida do observador, DT-001; nao e violacao);</li>
     *   <li>{@code marcoIdentidadeNs <= t0Sessao} e o ultimo {@code block} terminou em erro (regra D):
     *       {@link #I2_CASCATA_INCOMPLETA} no auditor (fora de {@code S_viol});</li>
     *   <li>{@code marcoIdentidadeNs <= t0Sessao}, cascata em curso ou {@code t0Sessao < marcoCascataNs}:
     *       {@link #I2_JANELA_CASCATA} no auditor (fora de {@code S_viol});</li>
     *   <li>{@code t0Sessao >= marcoCascataNs} (cascata concluida): I2; o rastro classifica
     *       REAL/ESPERADA/INCERTA como antes.</li>
     * </ul>
     */
    private void classificarI2(String sid, String identidade, long t0Sessao) {
        MarcoBloqueio m = marcos.get(identidade);
        if (m == null) {
            auditor.registrarViolacao("I2", sid, "valida sob bloqueio");
            contar(i2SemMarco, null);
            return;
        }
        if (!m.identidadeConfirmada() || t0Sessao < m.marcoIdentidadeNs()) {
            contar(i2Concorrentes, i2ConcorrentesTotal);
            return;
        }
        if (m.cascataIncompleta()) {
            // B-43.9, regra D: o ultimo block desta identidade terminou em erro (cascata parcial).
            auditor.registrarViolacao(I2_CASCATA_INCOMPLETA, sid, "valida sob bloqueio, cascata do block em erro");
            contar(i2CascataIncompleta, null);
            return;
        }
        if (!m.cascataConcluida() || t0Sessao < m.marcoCascataNs()) {
            auditor.registrarViolacao(I2_JANELA_CASCATA, sid, "valida sob bloqueio, dentro da janela da cascata");
            contar(i2JanelaCascata, null);
            return;
        }
        auditor.registrarViolacao("I2", sid, "valida sob bloqueio");
    }

    /** B-43.9: marco corrente do bloqueio da identidade ({@code null} = sem marco). */
    MarcoBloqueio marco(String identidade) {
        return marcos.get(identidade);
    }

    /** B-43.9: linha NOVA do stdout, logo depois de {@code Invalidacao:}; contadores com janela. */
    static String linhaAuditorI2(Iterable<SessionOps> instancias) {
        long c = 0;
        long j = 0;
        long p = 0;
        long s = 0;
        for (SessionOps o : instancias) {
            c += o.i2Concorrentes();
            j += o.i2JanelaCascata();
            p += o.i2CascataIncompleta();
            s += o.i2SemMarco();
        }
        return "Auditor I2: concorrentes=" + c + " janela_cascata=" + j + " cascata_incompleta=" + p
                + " sem_marco=" + s;
    }

    /** B-43.9: linha NOVA do stdout, os contadores de corrida sem janela (conferencia). */
    static String linhaAuditorTotal(Iterable<SessionOps> instancias) {
        long i1 = 0;
        long i4 = 0;
        long i2 = 0;
        for (SessionOps o : instancias) {
            i1 += o.i1ConcorrentesTotal();
            i4 += o.i4ConcorrentesTotal();
            i2 += o.i2ConcorrentesTotal();
        }
        return "Auditor total: i1_concorrentes=" + i1 + " i4_concorrentes=" + i4 + " i2_concorrentes=" + i2;
    }

    /** B-43.9: linha NOVA do stdout com os logins revertidos pela dupla checagem. */
    static String linhaLogin(Iterable<SessionOps> instancias) {
        long r = 0;
        for (SessionOps o : instancias) {
            r += o.loginRevertidoPorBloqueio();
        }
        return "Login: revertido_por_bloqueio=" + r;
    }

    long invalidacaoPerdida() {
        return invalidacaoPerdida.sum();
    }

    long conflitoEsgotado() {
        return conflitoEsgotado.sum();
    }

    /** B-43.6: leituras VALID concorrentes com a primeira confirmacao da sessao (nao sao I1). */
    long i1Concorrentes() {
        return i1Concorrentes.sum();
    }

    /** B-43.6: leituras INVALID cujo motivo divergiu de uma confirmacao concorrente (nao sao I4). */
    long i4Concorrentes() {
        return i4Concorrentes.sum();
    }

    /**
     * B-43.6: linha do stdout no fim da repeticao, com a soma das instancias dadas (no S3, todas as
     * que compartilham o mapa de invalidacoes confirmadas).
     */
    static String linhaAuditor(Iterable<SessionOps> instancias) {
        long i1 = 0;
        long i4 = 0;
        for (SessionOps o : instancias) {
            i1 += o.i1Concorrentes();
            i4 += o.i4Concorrentes();
        }
        return "Auditor: i1_concorrentes=" + i1 + " i4_concorrentes=" + i4;
    }

    /**
     * B-43.8: linha NOVA do stdout no fim da repeticao ({@code Auditor:} e fixada inteira por teste),
     * com a soma das instancias dadas. So diagnostico (DT-001).
     */
    static String linhaInvalidacao(Iterable<SessionOps> instancias) {
        long perdida = 0;
        long esgotado = 0;
        for (SessionOps o : instancias) {
            perdida += o.invalidacaoPerdida();
            esgotado += o.conflitoEsgotado();
        }
        return "Invalidacao: perdida=" + perdida + " conflito_esgotado=" + esgotado;
    }

    /** B-43.2 (P-d): contador de desfechos de Validate; {@code null} = desligado. */
    private volatile DesfechosValidate desfechosValidate;

    /** B-43.2 (P-d): liga o contador de desfechos de Validate (so stdout; o CSV nao muda). */
    void definirDesfechosValidate(DesfechosValidate d) {
        this.desfechosValidate = d;
    }

    private OpResult contarValidate(OpResult r) {
        DesfechosValidate d = desfechosValidate;
        if (d != null) {
            d.registrar(r);
        }
        return r;
    }

    /**
     * Construtor sem trilha de eventos (mantém compatibilidade com testes
     * existentes e com execuções que só geram o resumo de latência).
     */
    public SessionOps(RemoteCacheManager rcm, PerfilReplicacao perfil, LatencyRegistry latency,
                      InvariantAuditor auditor) {
        this(rcm, perfil, latency, auditor, null, null);
    }

    /**
     * Construtor com trilha de eventos. Se {@code eventos} ou {@code relogio}
     * forem nulos, a instrumentação de eventos crus é desligada e apenas o
     * {@link LatencyRegistry} é alimentado.
     *
     * <p>A {@code replica} é resolvida como o servidor de conexão (primeiro
     * servidor configurado no {@link RemoteCacheManager}), conforme o
     * <i>default</i> aprovado: obter o <i>owner</i> real por Hot Rod seria
     * caro, então o servidor de conexão é o fallback aceito.</p>
     */
    public SessionOps(RemoteCacheManager rcm, PerfilReplicacao perfil, LatencyRegistry latency,
                      InvariantAuditor auditor, EventCsvWriter eventos,
                      EpochClock relogio) {
        this(rcm, perfil, latency, auditor, eventos, relogio, null);
    }

    /**
     * B-33: como o anterior, com o rastro de cliente (Q-10) alimentado a cada evento.
     * B-42.1: os nomes de cache vem do {@link PerfilReplicacao} (SYNC = nomes historicos).
     * B-43.8 (DT-003): o cache de sessoes vai SEMPRE pelo adaptador versionado
     * ({@link ArmazemVersionado#de}), para que logout e cascata usem escrita condicional.
     */
    public SessionOps(RemoteCacheManager rcm, PerfilReplicacao perfil, LatencyRegistry latency,
                      InvariantAuditor auditor, EventCsvWriter eventos,
                      EpochClock relogio, RastroCliente rastro) {
        this(ArmazemVersionado.de(rcm.<String, SessionState>getCache(perfil.cacheSessoes())),
                Armazem.de(rcm.<String, IdentityState>getCache(perfil.cacheContadores())),
                latency, auditor, eventos, relogio, resolverReplica(rcm), rastro);
    }

    /**
     * B-33: construtor de composicao (armazens ja resolvidos), usado pelo
     * construtor publico e por testes sem cluster (dubles de {@link Armazem}).
     * Nao altera nenhuma regra de dominio.
     */
    SessionOps(Armazem<SessionState> sessions,
               Armazem<IdentityState> identities,
               LatencyRegistry latency, InvariantAuditor auditor,
               EventCsvWriter eventos, EpochClock relogio, String replica) {
        this(sessions, identities, latency, auditor, eventos, relogio, replica, null);
    }

    SessionOps(Armazem<SessionState> sessions,
               Armazem<IdentityState> identities,
               LatencyRegistry latency, InvariantAuditor auditor,
               EventCsvWriter eventos, EpochClock relogio, String replica,
               RastroCliente rastro) {
        this(sessions, identities, latency, auditor, eventos, relogio, replica, rastro, null);
    }

    SessionOps(Armazem<SessionState> sessions,
               Armazem<IdentityState> identities,
               LatencyRegistry latency, InvariantAuditor auditor,
               EventCsvWriter eventos, EpochClock relogio, String replica,
               RastroCliente rastro,
               ConcurrentHashMap<String, SessionState.Motivo> invalidacoesCompartilhadas) {
        this(sessions, identities, latency, auditor, eventos, relogio, replica, rastro,
                invalidacoesCompartilhadas, null);
    }

    /**
     * B-43.9: como o anterior, com o mapa de marcos do bloqueio ({@link MarcoBloqueio}) compartilhado
     * ({@code null} = mapa proprio, como nos construtores existentes).
     */
    SessionOps(Armazem<SessionState> sessions,
               Armazem<IdentityState> identities,
               LatencyRegistry latency, InvariantAuditor auditor,
               EventCsvWriter eventos, EpochClock relogio, String replica,
               RastroCliente rastro,
               ConcurrentHashMap<String, SessionState.Motivo> invalidacoesCompartilhadas,
               ConcurrentHashMap<String, MarcoBloqueio> marcosCompartilhados) {
        this.invalidacoesConfirmadas = invalidacoesCompartilhadas != null
                ? invalidacoesCompartilhadas : new ConcurrentHashMap<>();
        this.marcos = marcosCompartilhados != null ? marcosCompartilhados : new ConcurrentHashMap<>();
        this.rastro = rastro;
        this.sessions = sessions;
        this.sessoesVersionadas = sessions instanceof ArmazemVersionado<SessionState> v ? v : null;
        this.identities = identities;
        this.latency = latency;
        this.auditor = auditor;
        this.eventos = eventos;
        this.relogio = relogio;
        this.replica = replica;
    }

    /**
     * B-33 (E2): como o construtor com rastro, mas com mapa de invalidacoes confirmadas
     * compartilhado entre instancias e rotulo de replica explicito ({@code null} = primeiro
     * servidor configurado). Usado pelo S3 (um {@code SessionOps} por servidor de aplicacao).
     */
    SessionOps(RemoteCacheManager rcm, PerfilReplicacao perfil, LatencyRegistry latency,
               InvariantAuditor auditor, EventCsvWriter eventos,
               EpochClock relogio, RastroCliente rastro,
               ConcurrentHashMap<String, SessionState.Motivo> invalidacoesCompartilhadas,
               String replica) {
        this(rcm, perfil, latency, auditor, eventos, relogio, rastro, invalidacoesCompartilhadas, null, replica);
    }

    /**
     * B-43.9: como o anterior, com o mapa de marcos do bloqueio compartilhado entre os servidores de
     * aplicacao e o admin do S3 ({@code null} = mapa proprio).
     */
    SessionOps(RemoteCacheManager rcm, PerfilReplicacao perfil, LatencyRegistry latency,
               InvariantAuditor auditor, EventCsvWriter eventos,
               EpochClock relogio, RastroCliente rastro,
               ConcurrentHashMap<String, SessionState.Motivo> invalidacoesCompartilhadas,
               ConcurrentHashMap<String, MarcoBloqueio> marcosCompartilhados,
               String replica) {
        this(ArmazemVersionado.de(rcm.<String, SessionState>getCache(perfil.cacheSessoes())),
                Armazem.de(rcm.<String, IdentityState>getCache(perfil.cacheContadores())),
                latency, auditor, eventos, relogio,
                replica != null ? replica : resolverReplica(rcm), rastro, invalidacoesCompartilhadas,
                marcosCompartilhados);
    }

    /**
     * Resolve o rótulo da replica a partir do primeiro servidor configurado
     * no cliente Hot Rod (fallback aceito ao owner real). Em caso de
     * qualquer falha de introspecção, devolve {@code "unknown"}.
     */
    private static String resolverReplica(RemoteCacheManager rcm) {
        try {
            return rcm.getConfiguration().servers().stream()
                    .findFirst()
                    .map(s -> s.host() + ":" + s.port())
                    .orElse("unknown");
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /**
     * Emite um evento cru para a trilha de CSV, convertendo os instantes
     * monotônicos em epoch-ns. Faz no-op se a trilha estiver desligada.
     */
    private void emitir(String operation, long startMono, long endMono,
                        OpResult result, String key) {
        if (rastro != null) {
            rastro.registrar(operation,
                    relogio != null ? relogio.paraEpochNanos(startMono) : startMono,
                    relogio != null ? relogio.paraEpochNanos(endMono) : endMono,
                    result, key);
        }
        OuvinteDeEvento ouvinte = ouvinteDeEvento;
        if (ouvinte != null) {
            try {
                ouvinte.evento(operation,
                        relogio != null ? relogio.paraEpochNanos(startMono) : startMono,
                        relogio != null ? relogio.paraEpochNanos(endMono) : endMono,
                        result.returnCode());
            } catch (RuntimeException e) {
                // o controlador nunca derruba a operacao da carga
            }
        }
        if (eventos == null || relogio == null) {
            return;
        }
        eventos.registrar(
                operation,
                relogio.paraEpochNanos(startMono),
                relogio.paraEpochNanos(endMono),
                replica,
                result.returnCode(),
                key);
    }

    /**
     * B-23: verifica I1 (irreversibilidade da invalidação) e I4 (unicidade do
     * motivo) da leitura {@code atual} de {@code sid}.
     *
     * <p>B-43.6: a comparacao e contra {@code confirmadoAntes}, o motivo que ja estava
     * em {@link #invalidacoesConfirmadas} ANTES de a leitura comecar (instantaneo tirado
     * pelo chamador antes do {@code get}), e nao contra o estado do mapa depois da
     * leitura.</p>
     *
     * <ul>
     *   <li>{@code confirmadoAntes != null}: I1 se a leitura deu VALID; I4 se deu
     *       INVALID com motivo diferente de {@code confirmadoAntes}.</li>
     *   <li>{@code confirmadoAntes == null}: nao ha violacao possivel. Se a leitura e
     *       INVALID, a primeira observacao e registrada com {@code putIfAbsent} (cobre a
     *       invalidacao feita por outra thread/replica). Se, depois da leitura, o mapa ja
     *       tem uma confirmacao incompativel (VALID lido, ou motivo diferente), a leitura
     *       foi concorrente com a invalidacao: conta em {@link #i1Concorrentes()} /
     *       {@link #i4Concorrentes()}.</li>
     * </ul>
     *
     * <p>B-43.9 (regra C): {@code registradas} e o conjunto local da operacao (criado no inicio dela) com
     * as violacoes ja registradas, por (invariante, {@code sid}); {@code null} = sem deduplicacao
     * ({@code validate} e {@code logout} nao versionado, que leem uma vez so). Os contadores de corrida
     * nao sao deduplicados.</p>
     */
    private void verificarI1I4(String sid, SessionState atual, SessionState.Motivo confirmadoAntes,
                               java.util.Set<String> registradas) {
        if (confirmadoAntes == null) {
            if (atual.estado() == SessionState.Estado.INVALID) {
                SessionState.Motivo depois = invalidacoesConfirmadas.putIfAbsent(sid, atual.motivo());
                if (depois != null && !depois.equals(atual.motivo())) {
                    contar(i4Concorrentes, i4ConcorrentesTotal);
                }
            } else if (invalidacoesConfirmadas.get(sid) != null) {
                contar(i1Concorrentes, i1ConcorrentesTotal);
            }
            return;
        }
        verificarContraConfirmado(sid, atual, confirmadoAntes, registradas, null);
    }

    /**
     * B-23/B-43.6: o ramo de {@link #verificarI1I4} com confirmacao anterior a leitura: I1 se a leitura deu
     * VALID; I4 se deu INVALID com motivo diferente. W-10: a sonda de replay usa este mesmo ramo, com
     * {@code origem} {@link InvariantAuditor#ORIGEM_SONDA} (com origem nula e o caminho de sempre).
     */
    private void verificarContraConfirmado(String sid, SessionState atual, SessionState.Motivo confirmado,
                                           java.util.Set<String> registradas, String origem) {
        if (atual.estado() == SessionState.Estado.VALID) {
            if (primeiraNaOperacao(registradas, "I1", sid)) {
                auditor.registrarViolacao("I1", sid,
                        "invalidacao confirmada (" + confirmado + ") revertida para VALID", origem);
            }
        } else if (atual.estado() == SessionState.Estado.INVALID
                && !confirmado.equals(atual.motivo())) {
            if (primeiraNaOperacao(registradas, "I4", sid)) {
                auditor.registrarViolacao("I4", sid,
                        "motivo inconsistente: confirmado " + confirmado + ", lido " + atual.motivo(), origem);
            }
        }
    }

    /**
     * W-10: leitura da sonda de replay (reuso da sid depois do logout, por outro servidor de aplicacao). Um
     * {@code get} da sessao, sem a leitura da identidade, e a checagem I1/I4 pelo ramo de
     * {@link #verificarContraConfirmado} contra o instantaneo {@code confirmadoAntes} tirado antes do
     * {@code get} (regra de B-43.6), com origem {@link InvariantAuditor#ORIGEM_SONDA}.
     *
     * <p>So instrumenta: emite o evento {@link #OP_REPLAY_VALIDATE} no CSV de eventos (e so nele: sem rastro,
     * sem ouvinte do controlador, sem {@link LatencyRegistry}, sem contador de desfechos de Validate), nao
     * escreve no mapa de invalidacoes confirmadas (uma leitura INVALID sem confirmacao anterior nao vira
     * confirmacao) e nao conta corrida do observador. {@code foraDaJanela}: o evento entra mesmo fora da
     * janela de medicao (replay tardio, depois do fim da carga).</p>
     */
    OpResult validarSonda(String sid, boolean foraDaJanela) {
        long t0 = System.nanoTime();
        OpResult r;
        try {
            SessionState.Motivo confirmadoAntes = invalidacoesConfirmadas.get(sid);
            SessionState s = sessions.get(sid);
            if (s == null) {
                r = OpResult.notFound();
            } else {
                if (confirmadoAntes != null) {
                    verificarContraConfirmado(sid, s, confirmadoAntes, null, InvariantAuditor.ORIGEM_SONDA);
                }
                r = OpResult.okValidate(s.estado());
            }
        } catch (RuntimeException e) {
            r = ClassificadorDeErro.classificar(e);
        }
        long t1 = System.nanoTime();
        if (eventos != null && relogio != null) {
            eventos.registrar(OP_REPLAY_VALIDATE, relogio.paraEpochNanos(t0), relogio.paraEpochNanos(t1), replica,
                    r.returnCode(), sid, foraDaJanela);
        }
        return r;
    }

    /** W-10: rotulo do servidor de aplicacao (coluna {@code replica} do CSV). */
    String replica() {
        return replica;
    }

    /**
     * B-43.9, regra C: {@code true} se (invariante, sid) ainda nao foi registrada nesta operacao (e a
     * marca como registrada). {@code registradas == null}: sem deduplicacao.
     */
    private static boolean primeiraNaOperacao(java.util.Set<String> registradas, String invariante, String sid) {
        return registradas == null || registradas.add(invariante + ":" + sid);
    }

    /**
     * B-33: copia das invalidacoes confirmadas (sid para motivo) por esta instancia,
     * usada pela varredura final global (I1/I4).
     */
    public java.util.Map<String, SessionState.Motivo> invalidacoesConfirmadasSnapshot() {
        return new java.util.HashMap<>(invalidacoesConfirmadas);
    }

    /** O1, login. */
    public OpResult login(String identidade, String credencial) {
        long t0 = System.nanoTime();
        try {
            IdentityState idSt = identities.get(identidade);
            if (idSt == null) {
                idSt = IdentityState.criar(identidade, Instant.now());
                identities.put(identidade, idSt);
            }
            if (idSt.status() == IdentityState.Status.BLOCKED) {
                latency.record("login_blocked", t0);
                OpResult r = OpResult.blocked();
                emitir(OP_LOGIN, t0, System.nanoTime(), r, identidade);
                return r;
            }
            String sid = UUID.randomUUID().toString();
            SessionState s = SessionState.criar(sid, identidade, Instant.now());
            sessions.put(sid, s);
            // B-43.9, regra B: dupla checagem contra o TOCTOU login x block (padrao de Dekker: o login
            // escreve a sessao e rele a identidade; o block escreve a identidade e varre as sessoes).
            IdentityState releitura = identities.get(identidade);
            if (releitura != null && releitura.status() == IdentityState.Status.BLOCKED) {
                reverterLogin(sid, s);
                contar(loginRevertido, null);
                latency.record("login_blocked", t0);
                OpResult r = OpResult.blocked();
                emitir(OP_LOGIN, t0, System.nanoTime(), r, identidade);
                return r;
            }
            latency.record("login_ok", t0);
            OpResult r = OpResult.okLogin(sid);
            emitir(OP_LOGIN, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
            latency.record("login_err", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_LOGIN, t0, System.nanoTime(), r, identidade);
            return r;
        }
    }

    /**
     * B-43.9, regra B: o login cuja releitura viu BLOCKED invalida a propria sessao com ADMIN_BLOCK pelo
     * MESMO caminho da cascata. Com armazem versionado, {@link #invalidarPorBloqueio} (leitura
     * versionada, checagem I1/I4 deduplicada na operacao, escrita condicional; conflito esgotado lanca
     * e o login termina em erro). Sem armazem versionado, {@code put} INVALID e so depois a confirmacao
     * no mapa (ordem de B-43.6).
     */
    private void reverterLogin(String sid, SessionState s) {
        if (sessoesVersionadas != null) {
            invalidarPorBloqueio(sid, new java.util.HashSet<>());
            return;
        }
        SessionState atual = s.invalidar(SessionState.Motivo.ADMIN_BLOCK, Instant.now());
        sessions.put(sid, atual);
        invalidacoesConfirmadas.put(sid, atual.motivo());
    }

    /** O2, validate. */
    public OpResult validate(String sid) {
        long t0 = System.nanoTime();
        try {
            // B-43.6: instantaneo ANTES da leitura (ver verificarI1I4).
            SessionState.Motivo confirmadoAntes = invalidacoesConfirmadas.get(sid);
            // B-43.9 (DT-002): instante do inicio da leitura da sessao, contra os marcos do bloqueio.
            long t0Sessao = System.nanoTime();
            SessionState s = sessions.get(sid);
            latency.record("validate", t0);
            if (s == null) {
                OpResult r = OpResult.notFound();
                emitir(OP_VALIDATE, t0, System.nanoTime(), r, sid);
                return contarValidate(r);
            }

            IdentityState idSt = identities.get(s.identidade());
            if (idSt != null && idSt.status() == IdentityState.Status.BLOCKED
                    && s.estado() == SessionState.Estado.VALID) {
                classificarI2(sid, s.identidade(), t0Sessao);
            }
            verificarI1I4(sid, s, confirmadoAntes, null);
            OpResult r = OpResult.okValidate(s.estado());
            emitir(OP_VALIDATE, t0, System.nanoTime(), r, sid);
            return contarValidate(r);
        } catch (RuntimeException e) {
            latency.record("validate", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_VALIDATE, t0, System.nanoTime(), r, sid);
            return contarValidate(r);
        }
    }

    /** O3, logout. Idempotente. B-43.8: com armazem versionado, ver {@link #logoutVersionado}. */
    public OpResult logout(String sid) {
        long t0 = System.nanoTime();
        if (sessoesVersionadas != null) {
            return logoutVersionado(sid, t0);
        }
        try {
            // B-43.6: instantaneo ANTES da leitura (ver verificarI1I4).
            SessionState.Motivo confirmadoAntes = invalidacoesConfirmadas.get(sid);
            SessionState s = sessions.get(sid);
            if (s == null) {
                latency.record("logout_noop", t0);
                OpResult r = OpResult.notFound();
                emitir(OP_LOGOUT, t0, System.nanoTime(), r, sid);
                return r;
            }
            if (s.estado() == SessionState.Estado.INVALID) {
                // B-23 (correcao, ver docs/validacao-b23-b24-2026-10-02.md SS5):
                // antes sobrescrevia invalidacoesConfirmadas incondicionalmente,
                // escapando a checagem de I4 quando a primeira leitura pos-
                // divergencia passava por este caminho em vez de validate().
                verificarI1I4(sid, s, confirmadoAntes, null);
                latency.record("logout_noop", t0);
                OpResult r = OpResult.okNoop();
                emitir(OP_LOGOUT, t0, System.nanoTime(), r, sid);
                return r;
            }
            // A sessao esta VALID nesta leitura -- se ja houver um motivo
            // confirmado para este sid (de uma invalidacao observada antes por
            // esta instancia), isso e uma violacao de I1 (irreversibilidade),
            // nao so um caso a sobrescrever. Checa ANTES de escrever a nova
            // invalidacao abaixo.
            verificarI1I4(sid, s, confirmadoAntes, null);
            SessionState atual = s.invalidar(SessionState.Motivo.LOGOUT, Instant.now());
            // B-43.6: a ordem importa. A confirmacao so entra no mapa DEPOIS de o put ter sido
            // reconhecido pelo cluster; assim, uma confirmacao visivel no mapa implica uma escrita
            // INVALID ja reconhecida, e uma leitura VALID iniciada depois dela e I1 real. O
            // ConcurrentHashMap da a relacao happens-before entre este put e o instantaneo do leitor.
            sessions.put(sid, atual);
            invalidacoesConfirmadas.put(sid, atual.motivo());
            latency.record("logout_ok", t0);
            OpResult r = OpResult.okNoop();
            emitir(OP_LOGOUT, t0, System.nanoTime(), r, sid);
            return r;
        } catch (RuntimeException e) {
            latency.record("logout_noop", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_LOGOUT, t0, System.nanoTime(), r, sid);
            return r;
        }
    }

    /** B-43.8: desfecho de {@link #invalidarCondicional}. */
    private enum Desfecho { ESCRITA, PERDIDA, AUSENTE }

    /**
     * B-43.8 (DT-003): {@code logout} com leitura versionada e escrita condicional. A leitura da
     * operacao e {@code lerComVersao} (no lugar do {@code get}), precedida do instantaneo
     * {@code confirmadoAntes} (regra de B-43.6). Ausente: NOT_FOUND. INVALID: checagem I1/I4 e no-op.
     * VALID: checagem I1/I4 e {@link #invalidarCondicional}. A latencia {@code logout_ok} so para o
     * vencedor; o perdedor (invalidacao perdida) e {@code logout_noop}, como um no-op.
     */
    private OpResult logoutVersionado(String sid, long t0) {
        try {
            // B-43.9 (regra C): no maximo uma I1 e uma I4 por sid nesta operacao.
            java.util.Set<String> registradas = new java.util.HashSet<>();
            SessionState.Motivo confirmadoAntes = invalidacoesConfirmadas.get(sid);
            ArmazemVersionado.Versionado<SessionState> v = sessoesVersionadas.lerComVersao(sid);
            if (v == null) {
                return fimLogout(t0, "logout_noop", OpResult.notFound(), sid);
            }
            verificarI1I4(sid, v.valor(), confirmadoAntes, registradas);
            if (v.valor().estado() == SessionState.Estado.INVALID) {
                return fimLogout(t0, "logout_noop", OpResult.okNoop(), sid);
            }
            Desfecho d = invalidarCondicional(sid, v, SessionState.Motivo.LOGOUT, registradas);
            if (d == Desfecho.ESCRITA) {
                return fimLogout(t0, "logout_ok", OpResult.okNoop(), sid);
            }
            return fimLogout(t0, "logout_noop", d == Desfecho.PERDIDA ? OpResult.okNoop() : OpResult.notFound(), sid);
        } catch (RuntimeException e) {
            return fimLogout(t0, "logout_noop", ClassificadorDeErro.classificar(e), sid);
        }
    }

    private OpResult fimLogout(long t0, String rotulo, OpResult r, String sid) {
        latency.record(rotulo, t0);
        emitir(OP_LOGOUT, t0, System.nanoTime(), r, sid);
        return r;
    }

    /**
     * B-43.8 (DT-003): invalida {@code sid} com {@code motivo} a partir da leitura VALID {@code v} (ja
     * checada por I1/I4 pelo chamador), com escrita condicional a versao lida. Sucesso: a confirmacao
     * entra no mapa DEPOIS da escrita reconhecida, so para o vencedor (ordem de B-43.6). Falha de
     * versao: novo instantaneo e nova {@code lerComVersao}, checada por I1/I4; INVALID (outra
     * invalidacao venceu) e no-op, conta em {@link #invalidacaoPerdida()} e nao escreve a confirmacao
     * deste motivo; VALID tenta de novo, ate {@link #TENTATIVAS_CONDICIONAIS} no total, e entao conta
     * em {@link #conflitoEsgotado()} e lanca {@code IllegalStateException} (o chamador classifica).
     */
    private Desfecho invalidarCondicional(String sid, ArmazemVersionado.Versionado<SessionState> lida,
                                          SessionState.Motivo motivo, java.util.Set<String> registradas) {
        ArmazemVersionado.Versionado<SessionState> v = lida;
        for (int tentativa = 1; ; tentativa++) {
            SessionState nova = v.valor().invalidar(motivo, Instant.now());
            if (sessoesVersionadas.substituirSeVersao(sid, nova, v.versao())) {
                invalidacoesConfirmadas.put(sid, nova.motivo());
                return Desfecho.ESCRITA;
            }
            SessionState.Motivo confirmadoAntes = invalidacoesConfirmadas.get(sid);
            v = sessoesVersionadas.lerComVersao(sid);
            if (v == null) {
                return Desfecho.AUSENTE;
            }
            verificarI1I4(sid, v.valor(), confirmadoAntes, registradas);
            if (v.valor().estado() == SessionState.Estado.INVALID) {
                invalidacaoPerdida.increment();
                return Desfecho.PERDIDA;
            }
            if (tentativa >= TENTATIVAS_CONDICIONAIS) {
                conflitoEsgotado.increment();
                throw new IllegalStateException("conflito de versao esgotado");
            }
        }
    }

    /**
     * B-43.7: reposicoes da mistura v3 ({@link #reporSessao}) e as que falharam, na janela de medicao
     * ({@link #definirPoliticaReposicao}; sem politica, sempre).
     */
    private final LongAdder reposicoes = new LongAdder();
    private final LongAdder reposicaoErro = new LongAdder();
    private volatile WarmupPolicy politicaReposicao = WarmupPolicy.always();

    /** B-43.7: politica de janela dos contadores de reposicao (a mesma do CSV e do LatencyRegistry). */
    void definirPoliticaReposicao(WarmupPolicy politica) {
        this.politicaReposicao = politica != null ? politica : WarmupPolicy.always();
    }

    long reposicoes() {
        return reposicoes.sum();
    }

    long reposicaoErro() {
        return reposicaoErro.sum();
    }

    /**
     * B-43.7 (decisao c2 do Gestor): reposicao do Logout isolado da mistura v3, isto e, o novo login do
     * mesmo usuario na geracao seguinte do slot. Um {@code put} de uma sessao VALID nova, SEM evento no
     * CSV (nao chama {@code emitir}), SEM latencia e SEM o auditor de I1/I4: nao e uma operacao da
     * mistura, e a chave e sempre nova (geracao {@code g+1}), entao nunca reescreve uma sessao ja
     * invalidada. Conta em {@link #reposicoes()} ou {@link #reposicaoErro()} na janela de medicao.
     *
     * @return {@code true} se o {@code put} foi reconhecido; {@code false} se lancou
     */
    boolean reporSessao(String sid, String identidade) {
        try {
            sessions.put(sid, SessionState.criar(sid, identidade, Instant.now()));
            if (politicaReposicao.deveRegistrar()) {
                reposicoes.increment();
            }
            return true;
        } catch (RuntimeException e) {
            if (politicaReposicao.deveRegistrar()) {
                reposicaoErro.increment();
            }
            return false;
        }
    }

    /** O4, incrementFailure. */
    public OpResult incrementFailure(String identidade) {
        long t0 = System.nanoTime();
        boolean marcaLimite = false;
        try {
            IdentityState before = identities.get(identidade);
            if (before == null) {
                before = IdentityState.criar(identidade, Instant.now());
            }
            IdentityState after = before.incrementar(LIMITE_TENTATIVAS, Instant.now());
            // B-43.9 (DT-002): o incremento que atinge o limite abre um periodo de bloqueio sem cascata.
            boolean atingeLimite = before.status() == IdentityState.Status.OPEN
                    && after.status() == IdentityState.Status.BLOCKED;
            // B-43.9 (D4, V-B43.9): o limite so e atingido a partir de OPEN; um marco que esteja no mapa e
            // de um periodo que ja acabou (por exemplo, unblock com resposta perdida). O marco novo substitui.
            if (atingeLimite) {
                marcos.put(identidade, MarcoBloqueio.emCurso(MarcoBloqueio.Tipo.LIMITE));
            }
            marcaLimite = atingeLimite;
            identities.put(identidade, after);
            marcaLimite = false;
            if (atingeLimite) {
                long agora = System.nanoTime();
                marcos.put(identidade, new MarcoBloqueio(MarcoBloqueio.Tipo.LIMITE, agora, agora));
            }
            latency.record("inc_failure", t0);

            if (after.contadorFalhas() < before.contadorFalhas()) {
                auditor.registrarViolacao("I3", identidade, "contador regrediu local");
            }
            OpResult r = OpResult.okStatus(after.status());
            emitir(OP_INCREMENT_FAILURE, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
            if (marcaLimite) {
                // B-43.9: put que lancou (efeito desconhecido); sem cascata, o marco LIMITE fecha aqui.
                long agora = System.nanoTime();
                marcos.put(identidade, new MarcoBloqueio(MarcoBloqueio.Tipo.LIMITE, agora, agora));
            }
            latency.record("inc_failure", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_INCREMENT_FAILURE, t0, System.nanoTime(), r, identidade);
            return r;
        }
    }

    /** O5, resetFailures. */
    public OpResult resetFailures(String identidade) {
        long t0 = System.nanoTime();
        try {
            IdentityState st = identities.get(identidade);
            if (st == null) {
                latency.record("reset_noop", t0);
                OpResult r = OpResult.notFound();
                emitir(OP_RESET_FAILURES, t0, System.nanoTime(), r, identidade);
                return r;
            }
            identities.put(identidade, st.resetar(Instant.now()));
            latency.record("reset_ok", t0);
            OpResult r = OpResult.okNoop();
            emitir(OP_RESET_FAILURES, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
            latency.record("reset_noop", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_RESET_FAILURES, t0, System.nanoTime(), r, identidade);
            return r;
        }
    }

    /**
     * O6, block. B-33: com try/catch e emitir, como as demais operacoes. A cascata
     * parcial de um block() interrompido NAO e reparada (e comportamento real a
     * observar); o resultado fica registrado como erro.
     */
    public OpResult block(String identidade) {
        long t0 = System.nanoTime();
        // B-43.9, regra D: um periodo de bloqueio que ja teve cascata concluida (sem unblock depois)
        // mantem o marco, mesmo que este block termine em erro. O marco de um block anterior em erro
        // (cascata incompleta) cede o marcoIdentidadeNs, que e anterior.
        MarcoBloqueio anterior = marcos.get(identidade);
        boolean preserva = anterior != null && anterior.identidadeConfirmada() && anterior.cascataConcluida();
        boolean putTentado = false;
        MarcoBloqueio meu = null;
        try {
            IdentityState st = identities.get(identidade);
            if (st == null) {
                st = IdentityState.criar(identidade, Instant.now());
            }
            // B-43.9 (F-1 da V-B43.9): identidade lida fora de BLOCKED => o periodo anterior terminou
            // (unblock aplicado, mesmo com resposta perdida); o marco que ficou no mapa nao vale.
            if (st.status() != IdentityState.Status.BLOCKED) {
                anterior = null;
                preserva = false;
            }
            // B-43.9 (DT-002): marco provisorio (identidade ainda nao confirmada) durante o put; depois
            // do put reconhecido, marcoIdentidadeNs = agora e a cascata PENDENTE.
            if (!preserva && (anterior == null || !anterior.identidadeConfirmada())) {
                marcos.put(identidade, MarcoBloqueio.emCurso(MarcoBloqueio.Tipo.ADMIN));
            }
            putTentado = true;
            identities.put(identidade, st.bloquear(Instant.now()));
            if (!preserva) {
                meu = new MarcoBloqueio(MarcoBloqueio.Tipo.ADMIN, identidadeDesde(anterior, System.nanoTime()),
                        MarcoBloqueio.PENDENTE);
                marcos.put(identidade, meu);
            }
            // B-43.6: copia das confirmacoes ANTES de iniciar a varredura; cada entrada e checada
            // contra a copia (uma confirmacao que surge durante a varredura e corrida, nao I1).
            java.util.Map<String, SessionState.Motivo> confirmadasAntes =
                    new java.util.HashMap<>(invalidacoesConfirmadas);
            // B-43.9 (regra C): no maximo uma I1 e uma I4 por sid nesta operacao (varredura e releituras).
            java.util.Set<String> registradas = new java.util.HashSet<>();
            if (sessoesVersionadas != null) {
                // B-43.8 (DT-003): a varredura so seleciona as candidatas; cada uma e invalidada com
                // leitura versionada e escrita condicional (invalidarPorBloqueio).
                sessions.entrySet().forEach(e -> {
                    if (e.getValue().identidade().equals(identidade)
                            && e.getValue().estado() == SessionState.Estado.VALID) {
                        verificarI1I4(e.getKey(), e.getValue(), confirmadasAntes.get(e.getKey()), registradas);
                        invalidarPorBloqueio(e.getKey(), registradas);
                    }
                });
            } else {
            sessions.entrySet().forEach(e -> {
                if (e.getValue().identidade().equals(identidade)
                        && e.getValue().estado() == SessionState.Estado.VALID) {
                    // B-23 (correcao, mesma razao de logout() acima): checa I1 contra
                    // um motivo ja confirmado para esta sid ANTES de escrever a nova
                    // invalidacao por cascata de bloqueio.
                    verificarI1I4(e.getKey(), e.getValue(), confirmadasAntes.get(e.getKey()), registradas);
                    SessionState atual = e.getValue().invalidar(SessionState.Motivo.ADMIN_BLOCK, Instant.now());
                    // B-43.6: mesma ordem de logout(): put reconhecido, depois a confirmacao.
                    sessions.put(e.getKey(), atual);
                    invalidacoesConfirmadas.put(e.getKey(), atual.motivo());
                }
            });
            }
            // B-43.9: a cascata terminou (depois da ultima invalidacao).
            if (meu != null) {
                marcos.replace(identidade, meu, new MarcoBloqueio(meu.tipo(), meu.marcoIdentidadeNs(), System.nanoTime()));
            }
            latency.record("block_ok", t0);
            OpResult r = OpResult.okNoop();
            emitir(OP_BLOCK, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
            // B-43.9, regra D: block em erro depois de tentar o put(BLOCKED). Put reconhecido (cascata
            // parcial, inclusive conflito esgotado): o marco fica com a cascata PENDENTE. Put que lancou
            // (efeito desconhecido): marcoIdentidadeNs = instante deste catch. Nos dois casos o marco e
            // marcado blockEmErro: leituras posteriores sao I2_CASCATA_INCOMPLETA, nunca "sem marco".
            if (putTentado && !preserva) {
                long ident = meu != null ? meu.marcoIdentidadeNs() : identidadeDesde(anterior, System.nanoTime());
                marcos.put(identidade, new MarcoBloqueio(MarcoBloqueio.Tipo.ADMIN, ident, MarcoBloqueio.PENDENTE, true));
            }
            latency.record("block_err", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_BLOCK, t0, System.nanoTime(), r, identidade);
            return r;
        }
    }

    /**
     * B-43.9: marcoIdentidadeNs de um block novo: {@code agora}, ou o de um marco anterior com a
     * identidade ja confirmada (block anterior em erro), se for mais antigo.
     */
    private static long identidadeDesde(MarcoBloqueio anterior, long agora) {
        if (anterior != null && anterior.identidadeConfirmada()) {
            return Math.min(anterior.marcoIdentidadeNs(), agora);
        }
        return agora;
    }

    /**
     * B-43.8 (DT-003): uma candidata da cascata de {@code block}. Instantaneo por entrada ANTES da
     * {@code lerComVersao} (regra de B-43.6); ausente: nada; INVALID: checagem I1/I4 e no-op; VALID:
     * checagem I1/I4 e {@link #invalidarCondicional} com ADMIN_BLOCK. Conflito esgotado propaga a
     * excecao, e o {@code block} termina em erro (cascata parcial, como qualquer erro na cascata).
     */
    private void invalidarPorBloqueio(String sid, java.util.Set<String> registradas) {
        SessionState.Motivo confirmadoAntes = invalidacoesConfirmadas.get(sid);
        ArmazemVersionado.Versionado<SessionState> v = sessoesVersionadas.lerComVersao(sid);
        if (v == null) {
            return;
        }
        verificarI1I4(sid, v.valor(), confirmadoAntes, registradas);
        if (v.valor().estado() == SessionState.Estado.INVALID) {
            return;
        }
        invalidarCondicional(sid, v, SessionState.Motivo.ADMIN_BLOCK, registradas);
    }

    /** O7, unblock. B-33: com try/catch e emitir. */
    public OpResult unblock(String identidade) {
        long t0 = System.nanoTime();
        try {
            IdentityState st = identities.get(identidade);
            if (st == null) {
                latency.record("unblock_noop", t0);
                OpResult r = OpResult.notFound();
                emitir(OP_UNBLOCK, t0, System.nanoTime(), r, identidade);
                return r;
            }
            identities.put(identidade, st.desbloquear(Instant.now()));
            // B-43.9: o periodo de bloqueio acabou (depois do put(OPEN) reconhecido).
            marcos.remove(identidade);
            latency.record("unblock_ok", t0);
            OpResult r = OpResult.okNoop();
            emitir(OP_UNBLOCK, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
            latency.record("unblock_err", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_UNBLOCK, t0, System.nanoTime(), r, identidade);
            return r;
        }
    }
}
