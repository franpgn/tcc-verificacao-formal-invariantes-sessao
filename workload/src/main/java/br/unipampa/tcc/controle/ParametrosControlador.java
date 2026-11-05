package br.unipampa.tcc.controle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parametros do controlador (plano de 2026-10-22, secao 3.5). Nenhum limiar e fixo no codigo do
 * {@link Controlador}: todos chegam por este registro. Tempos em nanossegundos.
 *
 * @param versao      rotulo do conjunto de parametros (gravado no JSONL)
 * @param wNs         largura W da janela tumbling (&gt; 0)
 * @param nLat        minimo de elegiveis para o sinal de latencia ser determinado
 * @param nErr        minimo de eventos para o sinal de erro ser determinado
 * @param thetaLatIn  limiar de entrada de S_lat (excedencias / elegiveis)
 * @param thetaLatOut limiar de saida de S_lat (estritamente menor que o de entrada)
 * @param thetaErrIn  limiar de entrada de S_err (erros / total)
 * @param thetaErrOut limiar de saida de S_err (estritamente menor que o de entrada)
 * @param k           janelas altas seguidas para R2 por latencia ou erro (&gt;= 1)
 * @param m           janelas calmas seguidas para R4 (&gt;= 1)
 * @param cooldownNs  T_cooldown entre transicoes (R1 o ignora)
 * @param estavelNs   T_estavel sem topologia anomala para R4
 * @param holdViolNs  T_hold_viol desde a ultima violacao para R4
 * @param sha256      SHA-256 (hex minusculo) do arquivo de origem; {@code null} para {@link #padrao()}
 * @param politica    W-2a (P-38): parametros da {@link PoliticaPorAssinatura}, do bloco opcional
 *                    {@code "politica"} do arquivo; {@code null} sem o bloco (politica {@code nenhuma}, atuador nulo)
 */
public record ParametrosControlador(String versao, long wNs, int nLat, int nErr,
                                    double thetaLatIn, double thetaLatOut,
                                    double thetaErrIn, double thetaErrOut,
                                    int k, int m, long cooldownNs, long estavelNs, long holdViolNs,
                                    String sha256, ParametrosPolitica politica) {

    private static final long S = 1_000_000_000L;

    /** Versao dos valores provisorios de {@link #padrao()}. */
    public static final String VERSAO_PADRAO = "provisorio-2026-10-22";

    private static final List<String> CAMPOS = List.of("versao", "wNs", "nLat", "nErr",
            "thetaLatIn", "thetaLatOut", "thetaErrIn", "thetaErrOut",
            "k", "m", "cooldownNs", "estavelNs", "holdViolNs");

    /** W-2a (P-38): bloco opcional com os parametros da politica de acao ({@link ParametrosPolitica#deJson}). */
    public static final String CAMPO_POLITICA = "politica";

    public ParametrosControlador {
        if (versao == null) {
            throw new IllegalArgumentException("versao obrigatoria");
        }
        if (wNs <= 0) {
            throw new IllegalArgumentException("wNs deve ser positivo: " + wNs);
        }
        naoNegativo("nLat", nLat);
        naoNegativo("nErr", nErr);
        limiar("thetaLatIn", thetaLatIn);
        limiar("thetaLatOut", thetaLatOut);
        limiar("thetaErrIn", thetaErrIn);
        limiar("thetaErrOut", thetaErrOut);
        if (thetaLatOut >= thetaLatIn) {
            throw new IllegalArgumentException("histerese exige thetaLatOut < thetaLatIn: "
                    + thetaLatOut + " >= " + thetaLatIn);
        }
        if (thetaErrOut >= thetaErrIn) {
            throw new IllegalArgumentException("histerese exige thetaErrOut < thetaErrIn: "
                    + thetaErrOut + " >= " + thetaErrIn);
        }
        if (k < 1) {
            throw new IllegalArgumentException("k deve ser >= 1: " + k);
        }
        if (m < 1) {
            throw new IllegalArgumentException("m deve ser >= 1: " + m);
        }
        naoNegativo("cooldownNs", cooldownNs);
        naoNegativo("estavelNs", estavelNs);
        naoNegativo("holdViolNs", holdViolNs);
    }

    /** Construtor de antes do W-2a: sem parametros de politica. */
    public ParametrosControlador(String versao, long wNs, int nLat, int nErr, double thetaLatIn, double thetaLatOut,
                                 double thetaErrIn, double thetaErrOut, int k, int m, long cooldownNs,
                                 long estavelNs, long holdViolNs, String sha256) {
        this(versao, wNs, nLat, nErr, thetaLatIn, thetaLatOut, thetaErrIn, thetaErrOut, k, m, cooldownNs, estavelNs,
                holdViolNs, sha256, null);
    }

    /** Valores provisorios da secao 3.5 do plano de 2026-10-22 (a calibrar com a T-F). */
    public static ParametrosControlador padrao() {
        return new ParametrosControlador(VERSAO_PADRAO, 5 * S, 100, 50,
                0.05, 0.02, 0.02, 0.005,
                2, 6, 30 * S, 30 * S, 120 * S, null);
    }

    /**
     * Le {@code controlador-<nome>.json}. Exige exatamente os campos de {@link #CAMPOS} (sem
     * desconhecidos, sem {@code sha256}), com tipos estritos, mais o bloco opcional {@value #CAMPO_POLITICA}
     * (W-2a); calcula o SHA-256 dos bytes lidos, que cobre tambem o bloco.
     *
     * @throws IOException              falha de leitura do arquivo
     * @throws IllegalArgumentException conteudo invalido (JSON malformado, campo ausente ou
     *                                  desconhecido, tipo errado, valor fora das regras)
     */
    public static ParametrosControlador deJson(Path arquivo) throws IOException {
        byte[] bytes = Files.readAllBytes(arquivo);
        JsonNode raiz;
        try {
            raiz = new ObjectMapper().readTree(bytes);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON malformado em " + arquivo + ": " + e.getOriginalMessage(), e);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException("a raiz de " + arquivo + " deve ser um objeto JSON");
        }
        Set<String> presentes = new LinkedHashSet<>();
        for (Iterator<String> it = raiz.fieldNames(); it.hasNext(); ) {
            presentes.add(it.next());
        }
        List<String> desconhecidos = new ArrayList<>(presentes);
        desconhecidos.removeAll(CAMPOS);
        desconhecidos.remove(CAMPO_POLITICA);
        if (!desconhecidos.isEmpty()) {
            throw new IllegalArgumentException("campos desconhecidos em " + arquivo + ": " + desconhecidos);
        }
        List<String> ausentes = new ArrayList<>(CAMPOS);
        ausentes.removeAll(presentes);
        if (!ausentes.isEmpty()) {
            throw new IllegalArgumentException("campos ausentes em " + arquivo + ": " + ausentes);
        }
        JsonNode v = raiz.get("versao");
        if (!v.isTextual()) {
            throw new IllegalArgumentException("versao deve ser texto");
        }
        return new ParametrosControlador(v.asText(),
                inteiroLongo(raiz, "wNs"), inteiro(raiz, "nLat"), inteiro(raiz, "nErr"),
                real(raiz, "thetaLatIn"), real(raiz, "thetaLatOut"),
                real(raiz, "thetaErrIn"), real(raiz, "thetaErrOut"),
                inteiro(raiz, "k"), inteiro(raiz, "m"),
                inteiroLongo(raiz, "cooldownNs"), inteiroLongo(raiz, "estavelNs"),
                inteiroLongo(raiz, "holdViolNs"),
                sha256(bytes),
                presentes.contains(CAMPO_POLITICA) ? ParametrosPolitica.deJson(raiz.get(CAMPO_POLITICA)) : null);
    }

    private static long inteiroLongo(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || !n.canConvertToLong()) {
            throw new IllegalArgumentException(campo + " deve ser inteiro (long): " + n);
        }
        return n.longValue();
    }

    private static int inteiro(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || !n.canConvertToInt()) {
            throw new IllegalArgumentException(campo + " deve ser inteiro (int): " + n);
        }
        return n.intValue();
    }

    private static double real(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isNumber()) {
            throw new IllegalArgumentException(campo + " deve ser numero: " + n);
        }
        return n.doubleValue();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    private static void naoNegativo(String nome, long v) {
        if (v < 0) {
            throw new IllegalArgumentException(nome + " negativo: " + v);
        }
    }

    private static void limiar(String nome, double v) {
        if (!Double.isFinite(v) || v < 0) {
            throw new IllegalArgumentException(nome + " deve ser finito e nao negativo: " + v);
        }
    }
}
