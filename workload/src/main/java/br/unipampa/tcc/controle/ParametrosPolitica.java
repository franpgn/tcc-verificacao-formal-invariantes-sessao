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
 * W-2a (B-44.2): parametros da {@link PoliticaPorAssinatura} (documento de 05/11, secao 5.1). Todos
 * obrigatorios, <b>sem valor padrao no codigo</b>. No arquivo {@code controlador-*.json} ficam no bloco
 * opcional {@code "politica"} ({@link ParametrosControlador#politica()}), de modo que o SHA-256 do arquivo
 * (linha {@code meta}) cobre tambem estes valores; o bloco traz a origem de cada um ({@code "origem"}).
 *
 * <p>Origem decidida pelo Gestor em 05/11 (contrato JSONL, bloco {@code politica}):</p>
 * <ul>
 *   <li>{@code cotaAc5} = 1, por desenho: uma vez por execucao (com {@code owners=2}, o <i>cluster</i> de 3 nos
 *       tolera um no fora);</li>
 *   <li>{@code tRebNs} = 60 s: o {@code state-transfer.timeout} de 60 000 ms do XML, acima da maior deteccao
 *       medida (46,8 s no local, B-44.1 secao 4.1; 46,4 s na VM, {@code V-S7r.md:45}); convencao, nao otimo;</li>
 *   <li>{@code k} e {@code tetoLagMs}: sem valor padrao, vem da calibracao (D-12). Nos arquivos de teste, com
 *       {@code "origem": "teste"}.</li>
 * </ul>
 *
 * @param k         janelas seguidas com S_lag no teto e o mesmo {@code noSuspeito} para o AC-5 (&gt;= 1)
 * @param tetoLagMs o que conta como "nao viu" no S_lag: {@code lagMaxMs >= tetoLagMs} (&gt; 0)
 * @param tRebNs    teto {@code T_reb} do AC-2 (&gt; 0)
 * @param cotaAc5   AC-5 por execucao, 0 ou 1; 0 desliga o P1
 * @param origem    origem declarada de cada parametro (chave = nome do campo); vazio quando construido no codigo
 */
public record ParametrosPolitica(int k, long tetoLagMs, long tRebNs, int cotaAc5, Map<String, String> origem) {

    /** Campos numericos do bloco, todos obrigatorios. */
    public static final List<String> CAMPOS = List.of("k", "tetoLagMs", "tRebNs", "cotaAc5");
    /** Campo com a origem de cada parametro, obrigatorio no arquivo. */
    public static final String ORIGEM = "origem";

    public ParametrosPolitica {
        if (k < 1) {
            throw new IllegalArgumentException("k deve ser >= 1: " + k);
        }
        if (tetoLagMs <= 0) {
            throw new IllegalArgumentException("tetoLagMs deve ser > 0: " + tetoLagMs);
        }
        if (tRebNs <= 0) {
            throw new IllegalArgumentException("tRebNs deve ser > 0: " + tRebNs);
        }
        if (cotaAc5 < 0 || cotaAc5 > 1) {
            throw new IllegalArgumentException("cotaAc5 deve ser 0 ou 1 (owners=2 tolera um no fora): " + cotaAc5);
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
    public ParametrosPolitica(int k, long tetoLagMs, long tRebNs, int cotaAc5) {
        this(k, tetoLagMs, tRebNs, cotaAc5, Map.of());
    }

    /**
     * Le o bloco {@code "politica"} ja lido (sem E/S). Exige exatamente {@link #CAMPOS} (inteiros) e
     * {@link #ORIGEM} (objeto com um texto nao vazio para cada campo).
     *
     * @throws IllegalArgumentException campo ausente, desconhecido, de tipo errado ou fora das regras
     */
    public static ParametrosPolitica deJson(JsonNode raiz) {
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException("parametros da politica: o bloco deve ser um objeto JSON");
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
            throw new IllegalArgumentException("parametros da politica: campos desconhecidos " + desconhecidos);
        }
        List<String> ausentes = new ArrayList<>(esperados);
        ausentes.removeAll(presentes);
        if (!ausentes.isEmpty()) {
            throw new IllegalArgumentException("parametros da politica: campos ausentes " + ausentes);
        }
        JsonNode o = raiz.get(ORIGEM);
        if (!o.isObject()) {
            throw new IllegalArgumentException("parametros da politica: origem deve ser um objeto");
        }
        Map<String, String> origem = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!e.getValue().isTextual()) {
                throw new IllegalArgumentException("parametros da politica: origem de " + e.getKey() + " deve ser texto");
            }
            origem.put(e.getKey(), e.getValue().asText());
        }
        if (!origem.keySet().equals(new LinkedHashSet<>(CAMPOS))) {
            throw new IllegalArgumentException("parametros da politica: origem deve ter exatamente " + CAMPOS
                    + ": " + origem.keySet());
        }
        return new ParametrosPolitica((int) inteiro(raiz, "k", true), inteiro(raiz, "tetoLagMs", false),
                inteiro(raiz, "tRebNs", false), (int) inteiro(raiz, "cotaAc5", true), origem);
    }

    private static long inteiro(JsonNode raiz, String campo, boolean cabeEmInt) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || (cabeEmInt ? !n.canConvertToInt() : !n.canConvertToLong())) {
            throw new IllegalArgumentException("parametros da politica: " + campo + " deve ser inteiro: " + n);
        }
        return n.longValue();
    }
}
