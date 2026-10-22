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
    }
}
