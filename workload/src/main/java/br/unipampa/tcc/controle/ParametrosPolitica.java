package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W-2a (B-44.2): parametros da {@link PoliticaPorAssinatura} (documento de 05/11, secao 5.1). Todos
 * obrigatorios, <b>sem valor padrao no codigo</b>: quem monta a politica tem de dizer de onde vem cada um.
 *
 * @param k         janelas seguidas com S_lag no teto e o mesmo {@code noSuspeito} para o AC-5 (&gt;= 1;
 *                  calibracao, plano de alinhamento secoes 3.5-3.6 e 4.2)
 * @param tetoLagMs o que conta como "nao viu" no S_lag: {@code lagMaxMs >= tetoLagMs} (&gt; 0); e o
 *                  {@code hotrod_timeout_ms} da campanha ({@code S3Parametros.java:117}, 2000 ms por padrao),
 *                  que o W-4 grava como {@code lagMaxMs} da leitura nao vista
 * @param tRebNs    teto {@code T_reb} do AC-2 (&gt; 0). Convencao da secao 3.2, nao um otimo medido: maior que o
 *                  maior {@code T_det} medido (46,4 s na VM, {@code V-S7r.md:45}; 46,8 s no local, B-44.1 secao
 *                  4.1) e igual ao primeiro valor ja declarado na configuracao acima disso, o
 *                  {@code state-transfer.timeout} de 60 000 ms ({@code cluster/infinispan-cluster.xml}); 60 s
 * @param cotaAc5   AC-5 por execucao, 0 ou 1: com {@code owners=2}, o <i>cluster</i> de 3 nos tolera um no fora
 *                  ({@code infinispan_configuring_15}, donos); 0 desliga o P1
 */
public record ParametrosPolitica(int k, long tetoLagMs, long tRebNs, int cotaAc5) {

    /** Campos do JSON, todos obrigatorios. */
    public static final List<String> CAMPOS = List.of("k", "tetoLagMs", "tRebNs", "cotaAc5");

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
    }

    /**
     * Le os parametros de um objeto JSON ja lido (sem E/S). Exige exatamente {@link #CAMPOS}, inteiros.
     *
     * @throws IllegalArgumentException campo ausente, desconhecido, de tipo errado ou fora das regras
     */
    public static ParametrosPolitica deJson(JsonNode raiz) {
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException("parametros da politica: a raiz deve ser um objeto JSON");
        }
        Set<String> presentes = new LinkedHashSet<>();
        for (Iterator<String> it = raiz.fieldNames(); it.hasNext(); ) {
            presentes.add(it.next());
        }
        List<String> desconhecidos = new ArrayList<>(presentes);
        desconhecidos.removeAll(CAMPOS);
        if (!desconhecidos.isEmpty()) {
            throw new IllegalArgumentException("parametros da politica: campos desconhecidos " + desconhecidos);
        }
        List<String> ausentes = new ArrayList<>(CAMPOS);
        ausentes.removeAll(presentes);
        if (!ausentes.isEmpty()) {
            throw new IllegalArgumentException("parametros da politica: campos ausentes " + ausentes);
        }
        return new ParametrosPolitica((int) inteiro(raiz, "k", true), inteiro(raiz, "tetoLagMs", false),
                inteiro(raiz, "tRebNs", false), (int) inteiro(raiz, "cotaAc5", true));
    }

    private static long inteiro(JsonNode raiz, String campo, boolean cabeEmInt) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || (cabeEmInt ? !n.canConvertToInt() : !n.canConvertToLong())) {
            throw new IllegalArgumentException("parametros da politica: " + campo + " deve ser inteiro: " + n);
        }
        return n.longValue();
    }
}
