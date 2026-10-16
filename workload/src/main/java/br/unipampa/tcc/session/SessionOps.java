package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.RemoteCacheManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Armazem<SessionState> sessions;
    private final Armazem<IdentityState> identities;
    private final LatencyRegistry latency;
    private final InvariantAuditor auditor;
    private final EventCsvWriter eventos;
    private final EpochClock relogio;
    private final String replica;
    /** B-33 (Q-10): rastro de operacoes confirmadas no cliente; {@code null} = desligado. */
    private final RastroCliente rastro;

    /** B-23: ver javadoc da classe. Chave = sid; valor = motivo confirmado. */
    private final ConcurrentHashMap<String, SessionState.Motivo> invalidacoesConfirmadas =
            new ConcurrentHashMap<>();

    /**
     * Construtor sem trilha de eventos (mantém compatibilidade com testes
     * existentes e com execuções que só geram o resumo de latência).
     */
    public SessionOps(RemoteCacheManager rcm, LatencyRegistry latency, InvariantAuditor auditor) {
        this(rcm, latency, auditor, null, null);
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
    public SessionOps(RemoteCacheManager rcm, LatencyRegistry latency,
                      InvariantAuditor auditor, EventCsvWriter eventos,
                      EpochClock relogio) {
        this(rcm, latency, auditor, eventos, relogio, null);
    }

    /** B-33: como o anterior, com o rastro de cliente (Q-10) alimentado a cada evento. */
    public SessionOps(RemoteCacheManager rcm, LatencyRegistry latency,
                      InvariantAuditor auditor, EventCsvWriter eventos,
                      EpochClock relogio, RastroCliente rastro) {
        this(Armazem.de(rcm.<String, SessionState>getCache("sessions")),
                Armazem.de(rcm.<String, IdentityState>getCache("counters")),
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
        this.rastro = rastro;
        this.sessions = sessions;
        this.identities = identities;
        this.latency = latency;
        this.auditor = auditor;
        this.eventos = eventos;
        this.relogio = relogio;
        this.replica = replica;
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
     * motivo), comparando a leitura corrente contra {@link #invalidacoesConfirmadas}.
     * Chamado de {@link #validate(String)} após cada leitura bem-sucedida.
     *
     * <p>Se {@code sid} ainda não foi observado como inválido por esta instância,
     * e a leitura corrente já é INVALID, a primeira observação é registrada (cobre
     * o caso de uma invalidação originada por outra thread/réplica, não só pela
     * própria chamada local de {@link #logout}/{@link #block}). Caso já exista um
     * motivo confirmado: I1 dispara se a leitura corrente voltou a VALID; I4
     * dispara se a leitura corrente é INVALID com um motivo diferente do
     * confirmado.</p>
     */
    private void verificarI1I4(String sid, SessionState atual) {
        SessionState.Motivo confirmado = invalidacoesConfirmadas.get(sid);
        if (confirmado == null) {
            if (atual.estado() == SessionState.Estado.INVALID) {
                invalidacoesConfirmadas.putIfAbsent(sid, atual.motivo());
            }
            return;
        }
        if (atual.estado() == SessionState.Estado.VALID) {
            auditor.registrarViolacao("I1", sid,
                    "invalidacao confirmada (" + confirmado + ") revertida para VALID");
        } else if (atual.estado() == SessionState.Estado.INVALID
                && !confirmado.equals(atual.motivo())) {
            auditor.registrarViolacao("I4", sid,
                    "motivo inconsistente: confirmado " + confirmado + ", lido " + atual.motivo());
        }
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

    /** O2, validate. */
    public OpResult validate(String sid) {
        long t0 = System.nanoTime();
        try {
            SessionState s = sessions.get(sid);
            latency.record("validate", t0);
            if (s == null) {
                OpResult r = OpResult.notFound();
                emitir(OP_VALIDATE, t0, System.nanoTime(), r, sid);
                return r;
            }

            IdentityState idSt = identities.get(s.identidade());
            if (idSt != null && idSt.status() == IdentityState.Status.BLOCKED
                    && s.estado() == SessionState.Estado.VALID) {
                auditor.registrarViolacao("I2", sid, "valida sob bloqueio");
            }
            verificarI1I4(sid, s);
            OpResult r = OpResult.okValidate(s.estado());
            emitir(OP_VALIDATE, t0, System.nanoTime(), r, sid);
            return r;
        } catch (RuntimeException e) {
            latency.record("validate", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_VALIDATE, t0, System.nanoTime(), r, sid);
            return r;
        }
    }

    /** O3, logout. Idempotente. */
    public OpResult logout(String sid) {
        long t0 = System.nanoTime();
        try {
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
                verificarI1I4(sid, s);
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
            verificarI1I4(sid, s);
            SessionState atual = s.invalidar(SessionState.Motivo.LOGOUT, Instant.now());
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

    /** O4, incrementFailure. */
    public OpResult incrementFailure(String identidade) {
        long t0 = System.nanoTime();
        try {
            IdentityState before = identities.get(identidade);
            if (before == null) {
                before = IdentityState.criar(identidade, Instant.now());
            }
            IdentityState after = before.incrementar(LIMITE_TENTATIVAS, Instant.now());
            identities.put(identidade, after);
            latency.record("inc_failure", t0);

            if (after.contadorFalhas() < before.contadorFalhas()) {
                auditor.registrarViolacao("I3", identidade, "contador regrediu local");
            }
            OpResult r = OpResult.okStatus(after.status());
            emitir(OP_INCREMENT_FAILURE, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
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
        try {
            IdentityState st = identities.get(identidade);
            if (st == null) {
                st = IdentityState.criar(identidade, Instant.now());
            }
            identities.put(identidade, st.bloquear(Instant.now()));
            sessions.entrySet().forEach(e -> {
                if (e.getValue().identidade().equals(identidade)
                        && e.getValue().estado() == SessionState.Estado.VALID) {
                    // B-23 (correcao, mesma razao de logout() acima): checa I1 contra
                    // um motivo ja confirmado para esta sid ANTES de escrever a nova
                    // invalidacao por cascata de bloqueio.
                    verificarI1I4(e.getKey(), e.getValue());
                    SessionState atual = e.getValue().invalidar(SessionState.Motivo.ADMIN_BLOCK, Instant.now());
                    sessions.put(e.getKey(), atual);
                    invalidacoesConfirmadas.put(e.getKey(), atual.motivo());
                }
            });
            latency.record("block_ok", t0);
            OpResult r = OpResult.okNoop();
            emitir(OP_BLOCK, t0, System.nanoTime(), r, identidade);
            return r;
        } catch (RuntimeException e) {
            latency.record("block_err", t0);
            OpResult r = ClassificadorDeErro.classificar(e);
            emitir(OP_BLOCK, t0, System.nanoTime(), r, identidade);
            return r;
        }
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
