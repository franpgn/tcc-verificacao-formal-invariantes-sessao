package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * W-4 (B-26.3): parametros da {@link SondaDefasagem} (S_lag). Todos obrigatorios, <b>sem valor padrao no
 * codigo</b>. No {@code controlador-*.json} ficam no bloco opcional {@code "defasagem"}
 * ({@link ParametrosControlador#defasagem()}), coberto pelo SHA-256 do arquivo, com a origem de cada um
 * ({@code "origem"}), como o bloco {@code "politica"} do W-2a.
 *
 * <p>O que <b>nao</b> esta aqui, e por que:</p>
 * <ul>
 *   <li>o <b>prazo de leitura</b> (o "nao viu", gravado como {@code lagMaxMs}) e o {@code --hotrod-timeout-ms} da
 *       campanha, o mesmo prazo dos clientes Hot Rod (prompt do W-4, item 2; decisao do coordenador de 05/11). O
 *       W-2a confere, em S3 com o bloco {@code politica}, que {@code politica.tetoLagMs} e igual a ele (R7): um teto
 *       maior desligaria o P1 sem aviso;</li>
 *   <li>o <b>periodo</b> da sonda e a janela W do nucleo (plano de alinhamento 4.2; plano de execucao 4.6);</li>
 *   <li>a persistencia {@code k} e a do nucleo (R2) e a da politica (P1), ja no arquivo.</li>
 * </ul>
 *
 * <p>Origem esperada (plano de alinhamento 3.6 e 4.2; plano de execucao 4.6):</p>
 * <ul>
 *   <li>{@code lagInMs}: quantil de 99 % do lag nas execucoes sem falha da calibracao (mesma logica do p99 de
 *       referencia, B-39.1);</li>
 *   <li>{@code lagOutMs}: regra de histerese de theta_out (plano de alinhamento 3.6), com os mesmos dados;</li>
 *   <li>{@code passoLeituraMs}: intervalo entre leituras de um par enquanto o valor novo nao aparece. Nao e
 *       limiar: fixa a resolucao do lag e o custo da sonda sob falha (no maximo {@code teto/passo} leituras por
 *       par e rodada). Sem fonte no projeto; a origem e declarada no arquivo (decisao do Gestor).</li>
 * </ul>
 *
 * @param lagInMs        entrada da defasagem alta: {@code lag >= lagInMs} (&gt; 0)
 * @param lagOutMs       defasagem calma: {@code lag <= lagOutMs} (&gt;= 0 e &lt; {@code lagInMs})
 * @param passoLeituraMs intervalo entre leituras do mesmo par (&gt;= 1)
 * @param origem         origem declarada de cada parametro (chave = nome do campo); vazio quando montado no codigo
 */
public record ParametrosDefasagem(double lagInMs, double lagOutMs, long passoLeituraMs, Map<String, String> origem) {

    /** Campos numericos do bloco, todos obrigatorios. */
    public static final List<String> CAMPOS = List.of("lagInMs", "lagOutMs", "passoLeituraMs");
    /** Campo com a origem de cada parametro, obrigatorio no arquivo. */
    public static final String ORIGEM = "origem";

    private static final long MS = 1_000_000L;

    public ParametrosDefasagem {
        if (!Double.isFinite(lagInMs) || lagInMs <= 0) {
            throw new IllegalArgumentException("lagInMs deve ser finito e > 0: " + lagInMs);
        }
        if (!Double.isFinite(lagOutMs) || lagOutMs < 0) {
            throw new IllegalArgumentException("lagOutMs deve ser finito e >= 0: " + lagOutMs);
        }
        if (lagOutMs >= lagInMs) {
            throw new IllegalArgumentException("histerese exige lagOutMs < lagInMs: " + lagOutMs + " >= " + lagInMs);
        }
        if (passoLeituraMs < 1) {
            throw new IllegalArgumentException("passoLeituraMs deve ser >= 1: " + passoLeituraMs);
        }
        Objects.requireNonNull(origem, "origem");
        if (!origem.isEmpty() && !origem.keySet().equals(new LinkedHashSet<>(CAMPOS))) {
            throw new IllegalArgumentException("origem deve ter exatamente " + CAMPOS + ": " + origem.keySet());
        }
        LinkedHashMap<String, String> copia = new LinkedHashMap<>();
        for (String c : CAMPOS) {
            if (origem.containsKey(c)) {
                String v = origem.get(c);
                if (v == null || v.isBlank()) {
                    throw new IllegalArgumentException("origem vazia para " + c);
                }
                copia.put(c, v);
            }
        }
        origem = Collections.unmodifiableMap(copia);
    }

    /** Parametros montados no codigo (testes), sem origem declarada. */
    public ParametrosDefasagem(double lagInMs, double lagOutMs, long passoLeituraMs) {
        this(lagInMs, lagOutMs, passoLeituraMs, Map.of());
    }

    /**
     * Confere os parametros contra o prazo de leitura da sonda e a janela, sem E/S. Chamado no carregamento com
     * {@code --hotrod-timeout-ms} e o {@code wNs} do nucleo.
     *
     * <ul>
     *   <li>prazo em ns &lt; {@code wNs}: a rodada comeca no inicio da janela e dura ate o prazo, entao precisa
     *       caber nela (uma rodada por janela);</li>
     *   <li>{@code passoLeituraMs} &lt; prazo: senao o par so seria lido uma vez;</li>
     *   <li>{@code lagInMs} &lt;= prazo: o lag nunca passa do prazo, e acima dele a defasagem alta nunca
     *       ocorreria.</li>
     * </ul>
     *
     * @param prazoLeituraMs {@code --hotrod-timeout-ms} (o "nao viu")
     * @throws IllegalArgumentException com o motivo
     */
    public void validarContra(long prazoLeituraMs, long wNs) {
        if (prazoLeituraMs <= 0) {
            throw new IllegalArgumentException("prazo de leitura deve ser > 0: " + prazoLeituraMs);
        }
        if (prazoLeituraMs > Long.MAX_VALUE / MS || prazoLeituraMs * MS >= wNs) {
            throw new IllegalArgumentException("a rodada da sonda de defasagem (prazo de leitura " + prazoLeituraMs
                    + " ms, --hotrod-timeout-ms) precisa caber na janela W (" + wNs + " ns)");
        }
        if (passoLeituraMs >= prazoLeituraMs) {
            throw new IllegalArgumentException("passoLeituraMs (" + passoLeituraMs + ") precisa ser menor que o prazo"
                    + " de leitura (" + prazoLeituraMs + " ms, --hotrod-timeout-ms)");
        }
        if (lagInMs > prazoLeituraMs) {
            throw new IllegalArgumentException("lagInMs (" + lagInMs + ") acima do prazo de leitura (" + prazoLeituraMs
                    + " ms, --hotrod-timeout-ms): a defasagem alta nunca ocorreria");
        }
    }

    /**
     * Le o bloco {@code "defasagem"} ja lido (sem E/S). Exige exatamente {@link #CAMPOS} (os dois limiares como
     * numero, o passo como inteiro) e {@link #ORIGEM} (objeto com um texto nao vazio para cada campo).
     *
     * @throws IllegalArgumentException campo ausente, desconhecido, de tipo errado ou fora das regras
     */
    public static ParametrosDefasagem deJson(JsonNode raiz) {
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException("parametros da defasagem: o bloco deve ser um objeto JSON");
        }
        List<String> esperados = new ArrayList<>(CAMPOS);
        esperados.add(ORIGEM);
        Set<String> presentes = new LinkedHashSet<>();
        for (Iterator<String> it = raiz.fieldNames(); it.hasNext(); ) {
            presentes.add(it.next());
        }
        List<String> desconhecidos = new ArrayList<>(presentes);
        desconhecidos.removeAll(esperados);
        if (!desconhecidos.isEmpty()) {
            throw new IllegalArgumentException("parametros da defasagem: campos desconhecidos " + desconhecidos);
        }
        List<String> ausentes = new ArrayList<>(esperados);
        ausentes.removeAll(presentes);
        if (!ausentes.isEmpty()) {
            throw new IllegalArgumentException("parametros da defasagem: campos ausentes " + ausentes);
        }
        JsonNode o = raiz.get(ORIGEM);
        if (!o.isObject()) {
            throw new IllegalArgumentException("parametros da defasagem: origem deve ser um objeto");
        }
        Map<String, String> origem = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!e.getValue().isTextual()) {
                throw new IllegalArgumentException("parametros da defasagem: origem de " + e.getKey() + " deve ser texto");
            }
            origem.put(e.getKey(), e.getValue().asText());
        }
        if (!origem.keySet().equals(new LinkedHashSet<>(CAMPOS))) {
            throw new IllegalArgumentException("parametros da defasagem: origem deve ter exatamente " + CAMPOS
                    + ": " + origem.keySet());
        }
        return new ParametrosDefasagem(numero(raiz, "lagInMs"), numero(raiz, "lagOutMs"),
                inteiro(raiz, "passoLeituraMs"), origem);
    }

    private static double numero(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isNumber()) {
            throw new IllegalArgumentException("parametros da defasagem: " + campo + " deve ser numero: " + n);
        }
        return n.doubleValue();
    }

    private static long inteiro(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || !n.canConvertToLong()) {
            throw new IllegalArgumentException("parametros da defasagem: " + campo + " deve ser inteiro: " + n);
        }
        return n.longValue();
    }
}
