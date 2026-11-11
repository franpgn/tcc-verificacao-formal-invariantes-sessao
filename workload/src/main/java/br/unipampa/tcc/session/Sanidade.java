package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * B-43.3a (parte i): sanidade da mistura de uma execucao S1/S2, decidida so
 * pelas contagens {@code operation x return_code} do CSV de eventos. Logica
 * pura, sem I/O; o CSV e lido por {@link VerificadorSanidade}.
 *
 * <p>Motivacao (diagnostico de 2026-10-22, C1/C2): no baseline de junho todas
 * as identidades bloquearam ainda no warm-up, e a janela de medicao inteira
 * ficou com 0 Login OK e 0 Validate VALID. Os percentis dessa janela medem so
 * caminhos de recusa e de chave ausente. A guarda e <b>strict</b>: qualquer
 * motivo da FALHA, sem modo leniente (decisao do Gestor).</p>
 *
 * <p>Regioes: "fora" = sem coluna {@code phase}, ou {@code phase} em
 * {@code antes}/{@code pos-convergencia}; "dentro" = {@code durante},
 * {@code durante-pre-split}, {@code durante-pos-split}, {@code recuperacao}.
 * Outro rotulo de fase e erro de arquivo ({@link IllegalArgumentException}).</p>
 *
 * <p>Regras na regiao "fora": {@code JANELA_VAZIA}, {@code LOGIN_AUSENTE},
 * {@code LOGIN_OK_BAIXO}, {@code VALIDATE_VALID_ZERO},
 * {@code VALIDATE_AUSENTE_ALTO}, {@code MISTURA_DESVIO}. Na regiao "dentro",
 * se houver eventos: {@code DENTRO_SEM_VALID}.</p>
 *
 * <p>Nao e thread-safe; o verificador alimenta uma instancia por arquivo.</p>
 */
public final class Sanidade {

    /** Versao do criterio, gravada no JSON. */
    public static final String VERSAO = "b43-3a-i/1";

    public static final String OP_LOGIN = SessionOps.OP_LOGIN;
    public static final String OP_VALIDATE = SessionOps.OP_VALIDATE;
    public static final String OP_LOGOUT = SessionOps.OP_LOGOUT;
    public static final String OP_INCREMENT = SessionOps.OP_INCREMENT_FAILURE;
    public static final String OP_RESET = SessionOps.OP_RESET_FAILURES;

    /** Regiao temporal de um evento, pela coluna {@code phase}. */
    public enum Regiao {
        FORA("fora"),
        DENTRO("dentro");

        private final String rotulo;

        Regiao(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    public enum Veredito { OK, FALHA }

    /**
     * Limiares PROVISORIOS do diagnostico de 2026-10-22, aceitos pelo Gestor
     * como provisorios e gravados no JSON. A regra "Validate VALID &gt; 0" e
     * fixa e vai no JSON como {@code validate_valid_min_exclusivo = 0}.
     *
     * @param loginOkMin          minimo de OK/(OK+BLOCKED) entre os Login
     * @param validNotFoundMax    maximo de (NONE+NOT_FOUND)/Validate
     * @param misturaToleranciaPp tolerancia por operacao, em pontos percentuais
     */
    public record Limiares(double loginOkMin, double validNotFoundMax, double misturaToleranciaPp) {
        public static Limiares provisorios() {
            return new Limiares(0.95, 0.05, 2.0);
        }
    }

    /** Motivo de FALHA, com codigo estavel e texto legivel. */
    public record Motivo(String codigo, String texto) {
    }

    /**
     * Resultado da avaliacao.
     *
     * @param contagens regiao -&gt; operation -&gt; return_code -&gt; n (ordem estavel)
     * @param metricas  valores derivados usados pelas regras (ordem estavel)
     */
    public record Resultado(Veredito veredito, List<Motivo> motivos,
                            Map<String, Map<String, Map<String, Long>>> contagens,
                            Map<String, Object> metricas) {
        public boolean temMotivo(String codigo) {
            return motivos.stream().anyMatch(m -> m.codigo().equals(codigo));
        }
    }

    private static final Set<String> FASES_FORA = Set.of(
            Marcadores.Fase.ANTES.rotulo(), Marcadores.Fase.POS_CONVERGENCIA.rotulo());
    private static final Set<String> FASES_DENTRO = Set.of(
            Marcadores.Fase.DURANTE.rotulo(), Marcadores.Fase.DURANTE_PRE_SPLIT.rotulo(),
            Marcadores.Fase.DURANTE_POS_SPLIT.rotulo(), Marcadores.Fase.RECUPERACAO.rotulo());

    private final Map<Regiao, Map<String, Map<String, Long>>> contagens = new EnumMap<>(Regiao.class);

    public Sanidade() {
        for (Regiao r : Regiao.values()) {
            contagens.put(r, new TreeMap<>());
        }
    }

    /**
     * Regiao de um rotulo de fase. {@code null} ou vazio (CSV sem coluna
     * {@code phase}) e "fora".
     *
     * @throws IllegalArgumentException rotulo desconhecido
     */
    public static Regiao regiaoDe(String fase) {
        if (fase == null || fase.isEmpty() || FASES_FORA.contains(fase)) {
            return Regiao.FORA;
        }
        if (FASES_DENTRO.contains(fase)) {
            return Regiao.DENTRO;
        }
        throw new IllegalArgumentException("fase desconhecida: '" + fase + "'");
    }

    /**
     * Nomes de {@code operation} da mistura do cenario: as operacoes com peso
     * &gt; 0, mais Validate e Logout quando a triade Login esta na mistura (o
     * Login OK emite os dois). S1: as cinco; S2: sem ResetFailures (peso 0).
     * Uma operacao fora deste conjunto torna o arquivo invalido para o cenario
     * (decisao do Gestor, 2026-10-22).
     */
    public static Set<String> operacoesDe(Scenario cenario) {
        Set<String> r = new java.util.TreeSet<>();
        boolean triade = cenario.peso(Scenario.Op.LOGIN_VALIDATE_LOGOUT) > 0;
        if (triade) {
            r.add(OP_LOGIN);
        }
        if (triade || cenario.peso(Scenario.Op.VALIDATE) > 0) {
            r.add(OP_VALIDATE);
        }
        if (triade || cenario.peso(Scenario.Op.LOGOUT) > 0) {
            r.add(OP_LOGOUT);
        }
        if (cenario.peso(Scenario.Op.INCREMENT_FAILURE) > 0) {
            r.add(OP_INCREMENT);
        }
        if (cenario.peso(Scenario.Op.RESET_FAILURES) > 0) {
            r.add(OP_RESET);
        }
        return Collections.unmodifiableSet(r);
    }

    /** Acumula um evento. {@code fase} nulo = CSV sem coluna {@code phase}. */
    public void registrar(String operation, String returnCode, String fase) {
        contagens.get(regiaoDe(fase))
                .computeIfAbsent(operation, k -> new TreeMap<>())
                .merge(returnCode, 1L, Long::sum);
    }

    private long n(Regiao r, String op, String rc) {
        Map<String, Long> m = contagens.get(r).get(op);
        if (m == null) {
            return 0L;
        }
        return m.getOrDefault(rc, 0L);
    }

    private long n(Regiao r, String op) {
        Map<String, Long> m = contagens.get(r).get(op);
        if (m == null) {
            return 0L;
        }
        long s = 0L;
        for (long v : m.values()) {
            s += v;
        }
        return s;
    }

    private long total(Regiao r) {
        long s = 0L;
        for (String op : contagens.get(r).keySet()) {
            s += n(r, op);
        }
        return s;
    }

    private static String pct(double frac) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f %%", frac * 100.0);
    }

    private static String pp(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%+.2f p.p.", v);
    }

    /** Avalia as regras contra a mistura nominal do cenario. */
    public Resultado avaliar(Scenario cenario, Limiares lim) {
        List<Motivo> motivos = new ArrayList<>();
        Map<String, Object> met = new LinkedHashMap<>();
        Regiao f = Regiao.FORA;

        long eventosFora = total(f);
        long eventosDentro = total(Regiao.DENTRO);
        met.put("eventos_total", eventosFora + eventosDentro);
        met.put("eventos_fora", eventosFora);
        met.put("eventos_dentro", eventosDentro);

        if (eventosFora == 0) {
            motivos.add(new Motivo("JANELA_VAZIA", "nenhum evento na regiao fora"));
        } else {
            avaliarFora(cenario, lim, motivos, met);
        }
        if (eventosDentro > 0) {
            avaliarDentro(motivos, met);
        }

        Map<String, Map<String, Map<String, Long>>> cont = new LinkedHashMap<>();
        for (Regiao r : Regiao.values()) {
            Map<String, Map<String, Long>> copia = new TreeMap<>();
            contagens.get(r).forEach((op, m) -> copia.put(op, Collections.unmodifiableMap(new TreeMap<>(m))));
            cont.put(r.rotulo(), Collections.unmodifiableMap(copia));
        }
        Veredito v = motivos.isEmpty() ? Veredito.OK : Veredito.FALHA;
        return new Resultado(v, List.copyOf(motivos), Collections.unmodifiableMap(cont),
                Collections.unmodifiableMap(met));
    }

    private void avaliarFora(Scenario cenario, Limiares lim, List<Motivo> motivos, Map<String, Object> met) {
        Regiao f = Regiao.FORA;
        long login = n(f, OP_LOGIN);
        long loginOk = n(f, OP_LOGIN, "OK");
        long loginBlocked = n(f, OP_LOGIN, "BLOCKED");
        long validate = n(f, OP_VALIDATE);
        long valid = n(f, OP_VALIDATE, "VALID");
        // NONE (mapeamento atual de NOT_FOUND) e NOT_FOUND (grafia apos B-43.4).
        long ausente = n(f, OP_VALIDATE, "NONE") + n(f, OP_VALIDATE, "NOT_FOUND");

        double fracLoginOk = (loginOk + loginBlocked) > 0
                ? (double) loginOk / (double) (loginOk + loginBlocked) : 0.0;
        double fracAusente = validate > 0 ? (double) ausente / (double) validate : 0.0;
        met.put("login", login);
        met.put("login_ok", loginOk);
        met.put("login_blocked", loginBlocked);
        met.put("login_ok_frac", fracLoginOk);
        met.put("validate", validate);
        met.put("validate_valid", valid);
        met.put("validate_ausente", ausente);
        met.put("validate_ausente_frac", fracAusente);

        if (login == 0) {
            motivos.add(new Motivo("LOGIN_AUSENTE", "Login = 0"));
        } else if (fracLoginOk < lim.loginOkMin()) {
            motivos.add(new Motivo("LOGIN_OK_BAIXO",
                    "Login OK = " + pct(fracLoginOk) + " (minimo " + pct(lim.loginOkMin()) + ")"));
        }
        if (valid == 0) {
            motivos.add(new Motivo("VALIDATE_VALID_ZERO", "Validate VALID = 0"));
        }
        if (validate > 0 && fracAusente > lim.validNotFoundMax()) {
            motivos.add(new Motivo("VALIDATE_AUSENTE_ALTO",
                    "Validate NONE+NOT_FOUND = " + pct(fracAusente)
                            + " (maximo " + pct(lim.validNotFoundMax()) + ")"));
        }
        avaliarMistura(cenario, lim, loginOk, validate, motivos, met);
    }

    /**
     * DENTRO_SEM_VALID: na regiao "dentro" (com eventos), Validate VALID = 0 e
     * nem todos os eventos sao {@code ERROR_*}. Uma regiao "dentro" toda
     * recusada por indisponibilidade (DENY_READ_WRITES) e esperada e nao falha.
     */
    private void avaliarDentro(List<Motivo> motivos, Map<String, Object> met) {
        Regiao d = Regiao.DENTRO;
        long valid = n(d, OP_VALIDATE, "VALID");
        long naoErro = 0L;
        for (Map<String, Long> porRc : contagens.get(d).values()) {
            for (Map.Entry<String, Long> e : porRc.entrySet()) {
                if (!e.getKey().startsWith("ERROR_")) {
                    naoErro += e.getValue();
                }
            }
        }
        met.put("dentro_validate_valid", valid);
        met.put("dentro_nao_error", naoErro);
        if (valid == 0 && naoErro > 0) {
            motivos.add(new Motivo("DENTRO_SEM_VALID",
                    "regiao dentro: Validate VALID = 0 com " + naoErro + " eventos nao ERROR_*"));
        }
    }

    /**
     * MISTURA_DESVIO. Cada Login OK gera dois eventos extras (o Validate e o
     * Logout da triade), entao o numero de operacoes sorteadas e
     * {@code ops = eventos - 2*loginOk}. Fracoes por operacao sorteada:
     * Validate = (V - loginOk)/ops, Logout = (L - loginOk)/ops, Login = Login/ops,
     * IncrementFailure/ops, ResetFailures/ops; comparadas a {@code peso/100}.
     * |desvio| &gt; tolerancia (p.p.) da um motivo por operacao.
     */
    private void avaliarMistura(Scenario cenario, Limiares lim, long loginOk, long validate,
                                List<Motivo> motivos, Map<String, Object> met) {
        Regiao f = Regiao.FORA;
        long ops = total(f) - 2L * loginOk;
        met.put("ops", ops);
        if (ops <= 0) {
            return;
        }
        Map<String, Long> observados = new LinkedHashMap<>();
        observados.put(OP_VALIDATE, validate - loginOk);
        observados.put(OP_LOGIN, n(f, OP_LOGIN));
        observados.put(OP_LOGOUT, n(f, OP_LOGOUT) - loginOk);
        observados.put(OP_INCREMENT, n(f, OP_INCREMENT));
        observados.put(OP_RESET, n(f, OP_RESET));
        Map<String, Scenario.Op> sorteada = Map.of(
                OP_VALIDATE, Scenario.Op.VALIDATE,
                OP_LOGIN, Scenario.Op.LOGIN_VALIDATE_LOGOUT,
                OP_LOGOUT, Scenario.Op.LOGOUT,
                OP_INCREMENT, Scenario.Op.INCREMENT_FAILURE,
                OP_RESET, Scenario.Op.RESET_FAILURES);

        Map<String, Object> mistura = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : observados.entrySet()) {
            double obs = (double) e.getValue() / (double) ops;
            double esp = cenario.peso(sorteada.get(e.getKey())) / 100.0;
            double desvioPp = (obs - esp) * 100.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("observado", obs);
            m.put("esperado", esp);
            m.put("desvio_pp", desvioPp);
            mistura.put(e.getKey(), m);
            if (Math.abs(desvioPp) > lim.misturaToleranciaPp()) {
                motivos.add(new Motivo("MISTURA_DESVIO",
                        e.getKey() + ": observado " + pct(obs) + ", esperado " + pct(esp)
                                + ", desvio " + pp(desvioPp)
                                + " (tolerancia " + lim.misturaToleranciaPp() + " p.p.)"));
            }
        }
        met.put("mistura", mistura);
    }
}
