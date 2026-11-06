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
 *   <li>{@code kLag} e {@code tetoLagMs}: sem valor padrao, vem da calibracao (D-12). Nos arquivos de teste, com
 *       {@code "origem": "teste"}. <b>Requisito da calibracao (P-42):</b> {@code kLag * W + t_parada < 39,8 s}, com
 *       {@code t_parada} sob corte = 13,3 s (I-3 do B-44.2) e 39,8 s o menor {@code T_det} da VM; com W = 5 s,
 *       {@code kLag <= 5};</li>
 *   <li>{@code p2Habilitada}: D-B do coordenador de 05/11, pela regra registrada antes do I-2: a P2 (AC-2) sai do
 *       braco B e fica exploratoria (P-39: sob falha silenciosa o AC-2 oscila, so entra depois da exclusao e deixa o
 *       <i>cluster</i> sem rebalanceamento depois da fusao, 3 de 3; P-40: na queda nao economiza rodada). No braco B
 *       de avaliacao e na calibracao, {@code false}; {@code true} so em execucao exploratoria.</li>
 * </ul>
 *
 * <p><b>{@code kLag} nao e o {@code k} do nucleo</b> (V-W2a R6, decisao V-4 de 05/11). O {@code k} de
 * {@link ParametrosControlador} conta janelas altas seguidas de latencia, erro ou defasagem para a entrada em
 * CONSERVADOR (R2). O {@code kLag} conta janelas seguidas, ja consultadas pela politica, com S_lag no teto e o mesmo
 * {@code noSuspeito}, para o P1 (AC-5). Sao numeros independentes, calibrados separadamente.</p>
 *
 * @param kLag      janelas seguidas com S_lag no teto e o mesmo {@code noSuspeito} para o AC-5 (&gt;= 1)
 * @param tetoLagMs o que conta como "nao viu" no S_lag: {@code lagMaxMs >= tetoLagMs} (&gt; 0)
 * @param tRebNs    teto {@code T_reb} do AC-2 (&gt; 0)
 * @param cotaAc5   AC-5 por execucao, 0 ou 1; 0 desliga o P1
 * @param p2Habilitada D-B: {@code false} = modo de avaliacao, a politica nunca pede o AC-2 (a assinatura do P2 vira
 *                  P0 com o motivo {@link PoliticaPorAssinatura#P2_DESABILITADA}); {@code true} = uso exploratorio
 * @param origem    origem declarada de cada parametro (chave = nome do campo); vazio quando construido no codigo
 * @param acaoP1    P1-IC (06/11): tipo da acao do P1, {@link AcaoCluster.Tipo#AC_7} (ignorar o cache de sessoes no
 *                  suspeito; G-1 do coordenador, a A4 substitui a parada) ou {@link AcaoCluster.Tipo#AC_5} (a parada,
 *                  mantida no codigo para comparacao). Campo <b>opcional</b> no arquivo, o unico com valor padrao: nao
 *                  e parametro calibrado, e a escolha do desenho (G-1); ausente, vale AC-7, e o arquivo da calibracao
 *                  (sem o campo) continua valido com o mesmo SHA-256. Presente, exige a sua origem. {@code cotaAc5}
 *                  passa a ser a cota do P1, qualquer que seja a acao
 */
public record ParametrosPolitica(int kLag, long tetoLagMs, long tRebNs, int cotaAc5, boolean p2Habilitada,
                                 Map<String, String> origem, AcaoCluster.Tipo acaoP1) {

    /** Campos do bloco, todos obrigatorios e sem valor padrao (os quatro numericos e o booleano da D-B). */
    public static final List<String> CAMPOS = List.of("kLag", "tetoLagMs", "tRebNs", "cotaAc5", "p2Habilitada");
    /** P1-IC: campo opcional com a acao do P1 ("AC-7" ou "AC-5"); com ele, a origem dele e obrigatoria. */
    public static final String ACAO_P1 = "acaoP1";
    /** Acao do P1 sem o campo {@value #ACAO_P1} (G-1 do coordenador, 06/11). */
    public static final AcaoCluster.Tipo ACAO_P1_PADRAO = AcaoCluster.Tipo.AC_7;
    /** Campo com a origem de cada parametro, obrigatorio no arquivo. */
    public static final String ORIGEM = "origem";

    public ParametrosPolitica {
        if (kLag < 1) {
            throw new IllegalArgumentException("kLag deve ser >= 1: " + kLag);
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
        Objects.requireNonNull(acaoP1, "acaoP1");
        if (acaoP1 != AcaoCluster.Tipo.AC_5 && acaoP1 != AcaoCluster.Tipo.AC_7) {
            throw new IllegalArgumentException("acaoP1 deve ser AC-5 ou AC-7: " + acaoP1.rotulo());
        }
        List<String> comAcao = new ArrayList<>(CAMPOS);
        comAcao.add(ACAO_P1);
        if (!origem.isEmpty() && !origem.keySet().equals(new LinkedHashSet<>(CAMPOS))
                && !origem.keySet().equals(new LinkedHashSet<>(comAcao))) {
            throw new IllegalArgumentException("origem deve ter exatamente " + CAMPOS + " (e " + ACAO_P1
                    + " com o campo): " + origem.keySet());
        }
        LinkedHashMap<String, String> copia = new LinkedHashMap<>();
        for (String c : comAcao) {
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

    /** Parametros montados no codigo (testes), sem origem declarada; a P2 e sempre explicita (D-B); P1 = AC-7. */
    public ParametrosPolitica(int kLag, long tetoLagMs, long tRebNs, int cotaAc5, boolean p2Habilitada) {
        this(kLag, tetoLagMs, tRebNs, cotaAc5, p2Habilitada, Map.of());
    }

    /** Com origem e a acao do P1 padrao (AC-7, G-1). */
    public ParametrosPolitica(int kLag, long tetoLagMs, long tRebNs, int cotaAc5, boolean p2Habilitada,
                              Map<String, String> origem) {
        this(kLag, tetoLagMs, tRebNs, cotaAc5, p2Habilitada, origem, ACAO_P1_PADRAO);
    }

    /**
     * Le o bloco {@code "politica"} ja lido (sem E/S). Exige exatamente {@link #CAMPOS} (inteiros, e
     * {@code p2Habilitada} booleano) e {@link #ORIGEM} (objeto com um texto nao vazio para cada campo).
     *
     * @throws IllegalArgumentException campo ausente, desconhecido, de tipo errado ou fora das regras
     */
    public static ParametrosPolitica deJson(JsonNode raiz) {
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException("parametros da politica: o bloco deve ser um objeto JSON");
        }
        List<String> esperados = new ArrayList<>(CAMPOS);
        esperados.add(ORIGEM);
        boolean comAcao = raiz.has(ACAO_P1);
        if (comAcao) {
            esperados.add(ACAO_P1);
        }
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
        List<String> origensEsperadas = new ArrayList<>(CAMPOS);
        if (comAcao) {
            origensEsperadas.add(ACAO_P1);
        }
        if (!origem.keySet().equals(new LinkedHashSet<>(origensEsperadas))) {
            throw new IllegalArgumentException("parametros da politica: origem deve ter exatamente " + origensEsperadas
                    + ": " + origem.keySet());
        }
        AcaoCluster.Tipo acao = ACAO_P1_PADRAO;
        if (comAcao) {
            JsonNode a = raiz.get(ACAO_P1);
            if (!a.isTextual()) {
                throw new IllegalArgumentException("parametros da politica: acaoP1 deve ser texto (AC-5 ou AC-7): " + a);
            }
            acao = switch (a.asText()) {
                case "AC-7" -> AcaoCluster.Tipo.AC_7;
                case "AC-5" -> AcaoCluster.Tipo.AC_5;
                default -> throw new IllegalArgumentException("parametros da politica: acaoP1 deve ser AC-5 ou AC-7: "
                        + a.asText());
            };
        }
        JsonNode p2 = raiz.get("p2Habilitada");
        if (!p2.isBoolean()) {
            throw new IllegalArgumentException("parametros da politica: p2Habilitada deve ser booleano (D-B): " + p2);
        }
        return new ParametrosPolitica((int) inteiro(raiz, "kLag", true), inteiro(raiz, "tetoLagMs", false),
                inteiro(raiz, "tRebNs", false), (int) inteiro(raiz, "cotaAc5", true), p2.booleanValue(), origem, acao);
    }

    private static long inteiro(JsonNode raiz, String campo, boolean cabeEmInt) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || (cabeEmInt ? !n.canConvertToInt() : !n.canConvertToLong())) {
            throw new IllegalArgumentException("parametros da politica: " + campo + " deve ser inteiro: " + n);
        }
        return n.longValue();
    }
}
