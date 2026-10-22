package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;

/**
 * B-43.2: {@link SessionOps} sobre {@link FakeCaches} que grava cada chamada (metodo, argumento e
 * resultado), para os testes da mistura (T2, T4, T6, T9). Nao altera nenhuma regra: delega ao
 * {@link SessionOps} real.
 */
class OpsGravadas extends SessionOps {

    /** Uma chamada de operacao. */
    record Chamada(String op, String arg, OpResult r) { }

    final List<Chamada> chamadas = new ArrayList<>();

    OpsGravadas(FakeCaches.Cache<SessionState> sessoes, FakeCaches.Cache<IdentityState> identidades,
                LatencyRegistry latency, EventCsvWriter eventos) {
        super(sessoes, identidades, latency, new InvariantAuditor(), eventos,
                eventos == null ? null : EpochClock.capturarAgora(), "fake");
    }

    private OpResult gravar(String op, String arg, OpResult r) {
        chamadas.add(new Chamada(op, arg, r));
        return r;
    }

    @Override
    public OpResult login(String identidade, String credencial) {
        return gravar(OP_LOGIN, identidade, super.login(identidade, credencial));
    }

    @Override
    public OpResult validate(String sid) {
        return gravar(OP_VALIDATE, sid, super.validate(sid));
    }

    @Override
    public OpResult logout(String sid) {
        return gravar(OP_LOGOUT, sid, super.logout(sid));
    }

    @Override
    public OpResult incrementFailure(String identidade) {
        return gravar(OP_INCREMENT_FAILURE, identidade, super.incrementFailure(identidade));
    }

    @Override
    public OpResult resetFailures(String identidade) {
        return gravar(OP_RESET_FAILURES, identidade, super.resetFailures(identidade));
    }

    /**
     * Reconstroi a sequencia de operacoes da mistura ({@link Scenario.Op}) e a chave sorteada de cada
     * uma ({@code null} onde a operacao nao usa a chave Zipf). A triade conta como uma operacao: um
     * Login seguido, se {@code OK_LOGIN}, do Validate e do Logout do sid novo.
     */
    List<Object[]> operacoes() {
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < chamadas.size(); i++) {
            Chamada c = chamadas.get(i);
            switch (c.op()) {
                case OP_LOGIN -> {
                    out.add(new Object[] {Scenario.Op.LOGIN_VALIDATE_LOGOUT, null});
                    if (c.r().code() == OpResult.Code.OK_LOGIN) {
                        i += 2;
                    }
                }
                case OP_VALIDATE -> out.add(new Object[] {Scenario.Op.VALIDATE, c.arg()});
                case OP_LOGOUT -> out.add(new Object[] {Scenario.Op.LOGOUT, c.arg()});
                case OP_INCREMENT_FAILURE -> out.add(new Object[] {Scenario.Op.INCREMENT_FAILURE, null});
                case OP_RESET_FAILURES -> out.add(new Object[] {Scenario.Op.RESET_FAILURES, null});
                default -> throw new IllegalStateException(c.op());
            }
        }
        return out;
    }

    /** Numero de chamadas de {@code op} com codigo {@code code}. */
    long contar(String op, OpResult.Code code) {
        return chamadas.stream().filter(c -> c.op().equals(op) && c.r().code() == code).count();
    }

    /** Numero de chamadas de {@code op}. */
    long contar(String op) {
        return chamadas.stream().filter(c -> c.op().equals(op)).count();
    }
}
