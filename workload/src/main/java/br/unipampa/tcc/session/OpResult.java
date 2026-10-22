package br.unipampa.tcc.session;

/**
 * Resultado de uma operação O1 a O7 do protocolo de sessão.
 *
 * Cada operação devolve um {@code OpResult} com:
 *   {@link Code} estado lógico do retorno (sucesso, idempotência, bloqueio, ausência, erro);
 *   {@code payload} opcional, semanticamente válido para alguns códigos:
 *     OK_LOGIN traz o identificador da nova sessão;
 *     OK_VALIDATE traz o estado lógico da sessão consultada;
 *     OK_STATUS traz o status da identidade após mutação.
 *
 * Substitui os retornos heterogêneos anteriores (String, Estado, Status, void)
 * e fixa o contrato exigido pelo bloco B-05 do backlog.
 */
public final class OpResult {

    public enum Code {
        OK_LOGIN,
        OK_VALIDATE,
        OK_STATUS,
        OK_NOOP,
        BLOCKED,
        NOT_FOUND,
        ERROR_TRANSPORT,
        /** B-33 (Q-9): recusa por indisponibilidade (ex.: cache DEGRADED sob DENY_READ_WRITES). */
        ERROR_UNAVAILABLE
    }

    /**
     * B-43.4: versao do contrato da coluna {@code return_code} do CSV de eventos. Contrato 1:
     * {@code OK_VALIDATE} sempre {@code VALID} e {@code NOT_FOUND} como {@code NONE} (mascaramento
     * C4). Contrato 2: estado real ({@code VALID}/{@code INVALID}) e {@code NOT_FOUND}. Gravada em
     * {@code meta-csv-rep-NNN.json} ({@link MetaCsv}). Ver {@code docs/contrato-csv-v2.md}.
     */
    public static final int VERSAO_CONTRATO_CSV = 2;

    private final Code code;
    private final Object payload;

    private OpResult(Code code, Object payload) {
        this.code = code;
        this.payload = payload;
    }

    public static OpResult okLogin(String sid) { return new OpResult(Code.OK_LOGIN, sid); }
    public static OpResult okValidate(SessionState.Estado estado) { return new OpResult(Code.OK_VALIDATE, estado); }
    public static OpResult okStatus(IdentityState.Status status) { return new OpResult(Code.OK_STATUS, status); }
    public static OpResult okNoop() { return new OpResult(Code.OK_NOOP, null); }
    public static OpResult blocked() { return new OpResult(Code.BLOCKED, null); }
    public static OpResult notFound() { return new OpResult(Code.NOT_FOUND, null); }
    public static OpResult errorTransport() { return new OpResult(Code.ERROR_TRANSPORT, null); }
    public static OpResult errorUnavailable() { return new OpResult(Code.ERROR_UNAVAILABLE, null); }

    public Code code() { return code; }
    public boolean isOk() { return code == Code.OK_LOGIN || code == Code.OK_VALIDATE
            || code == Code.OK_STATUS || code == Code.OK_NOOP; }

    /**
     * Mapeia o {@link Code} interno para o código de retorno canônico
     * gravado na coluna {@code return_code} do CSV de eventos, consumido
     * pelo pipeline {@code analysis/}.
     *
     * <p>Tabela de mapeamento (aprovada pelo Gestor):</p>
     * <pre>
     *   OK_LOGIN        -> OK
     *   OK_VALIDATE     -> VALID
     *   OK_STATUS       -> COUNTED
     *   OK_NOOP         -> NONE
     *   NOT_FOUND       -> NONE
     *   BLOCKED         -> BLOCKED
     *   ERROR_TRANSPORT -> ERROR_TRANSPORT
     *   ERROR_UNAVAILABLE -> ERROR_UNAVAILABLE   (B-33, Q-9)
     * </pre>
     *
     * <p>Todos os códigos exceto {@code ERROR_TRANSPORT} e
     * {@code ERROR_UNAVAILABLE} pertencem ao conjunto {@code SUCESSOS} de
     * {@code analysis/percentis.py}, de modo que a {@code taxa_erro} reflete
     * apenas falhas (de transporte ou de indisponibilidade). Execuções sem
     * falha de partição nunca emitem {@code ERROR_UNAVAILABLE}, então os CSV
     * de S1/S2 sem falha seguem byte-compatíveis.</p>
     */
    public String returnCode() {
        switch (code) {
            case OK_LOGIN:        return "OK";
            case OK_VALIDATE:     return "VALID";
            case OK_STATUS:       return "COUNTED";
            case OK_NOOP:         return "NONE";
            case NOT_FOUND:       return "NONE";
            case BLOCKED:         return "BLOCKED";
            case ERROR_TRANSPORT: return "ERROR_TRANSPORT";
            case ERROR_UNAVAILABLE: return "ERROR_UNAVAILABLE";
            default:              return "ERROR_TRANSPORT";
        }
    }
    public String sessionId() { return code == Code.OK_LOGIN ? (String) payload : null; }
    public SessionState.Estado estado() { return code == Code.OK_VALIDATE ? (SessionState.Estado) payload : null; }
    public IdentityState.Status status() { return code == Code.OK_STATUS ? (IdentityState.Status) payload : null; }
}
