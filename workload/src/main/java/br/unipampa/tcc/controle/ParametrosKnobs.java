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
 * B-27: parametros dos knobs K1..K4 do portao de emissao (plano de 2026-10-22, secao 3.4). Ficam
 * fora de {@link ParametrosControlador} (decisao D1 do Gestor, 2026-10-27): arquivo JSON proprio,
 * lido com o mesmo rigor. Nenhum valor e fixo no codigo do {@link PortaoDeEmissao}. Tempos em
 * nanossegundos.
 *
 * @param versao                   rotulo do conjunto de parametros
 * @param k1FracaoConservador      K1: fracao da concorrencia nominal em CONSERVADOR, em (0, 1]
 * @param k2PausaConservadorNs     K2 (S1/S2): pausa depois de cada operacao em CONSERVADOR (&gt;= 0)
 * @param k2MultPensarConservador  K2 (S3): multiplicador do tempo de pensar em CONSERVADOR (&gt;= 1)
 * @param k3AdiaMaxNs              K3: adiamento maximo de uma operacao administrativa (&gt; 0)
 * @param k3SondagemNs             K3: periodo de sondagem do perfil durante o adiamento, em (0, k3AdiaMaxNs]
 * @param k4FatorBaseConservador   K4: fator sobre a base do backoff em CONSERVADOR (&gt;= 1)
 * @param k4JitterConservador      K4: amplitude relativa do jitter em CONSERVADOR, em [0, 1)
 * @param sha256                   SHA-256 (hex minusculo) do arquivo de origem; {@code null} para {@link #padrao()}
 */
public record ParametrosKnobs(String versao, double k1FracaoConservador, long k2PausaConservadorNs,
                              double k2MultPensarConservador, long k3AdiaMaxNs, long k3SondagemNs,
                              double k4FatorBaseConservador, double k4JitterConservador, String sha256) {

    private static final long MS = 1_000_000L;
    private static final long S = 1_000_000_000L;

    /** Versao dos valores provisorios de {@link #padrao()}. */
    public static final String VERSAO_PADRAO = "provisorio-2026-10-22";

    private static final List<String> CAMPOS = List.of("versao", "k1FracaoConservador", "k2PausaConservadorNs",
            "k2MultPensarConservador", "k3AdiaMaxNs", "k3SondagemNs", "k4FatorBaseConservador",
            "k4JitterConservador");

    public ParametrosKnobs {
        if (versao == null) {
            throw new IllegalArgumentException("versao obrigatoria");
        }
        finito("k1FracaoConservador", k1FracaoConservador);
        if (k1FracaoConservador <= 0 || k1FracaoConservador > 1) {
            throw new IllegalArgumentException("k1FracaoConservador deve estar em (0, 1]: " + k1FracaoConservador);
        }
        if (k2PausaConservadorNs < 0) {
            throw new IllegalArgumentException("k2PausaConservadorNs negativo: " + k2PausaConservadorNs);
        }
        finito("k2MultPensarConservador", k2MultPensarConservador);
        if (k2MultPensarConservador < 1) {
            throw new IllegalArgumentException("k2MultPensarConservador deve ser >= 1: " + k2MultPensarConservador);
        }
        if (k3AdiaMaxNs <= 0) {
            throw new IllegalArgumentException("k3AdiaMaxNs deve ser positivo: " + k3AdiaMaxNs);
        }
        if (k3SondagemNs <= 0 || k3SondagemNs > k3AdiaMaxNs) {
            throw new IllegalArgumentException("k3SondagemNs deve estar em (0, k3AdiaMaxNs]: " + k3SondagemNs);
        }
        finito("k4FatorBaseConservador", k4FatorBaseConservador);
        if (k4FatorBaseConservador < 1) {
            throw new IllegalArgumentException("k4FatorBaseConservador deve ser >= 1: " + k4FatorBaseConservador);
        }
        finito("k4JitterConservador", k4JitterConservador);
        if (k4JitterConservador < 0 || k4JitterConservador >= 1) {
            throw new IllegalArgumentException("k4JitterConservador deve estar em [0, 1): " + k4JitterConservador);
        }
    }

    /** Valores provisorios da secao 3.4 do plano de 2026-10-22 (a calibrar). */
    public static ParametrosKnobs padrao() {
        return new ParametrosKnobs(VERSAO_PADRAO, 0.5, 5 * MS, 1.5, 30 * S, 100 * MS, 2.0, 0.5, null);
    }

    /**
     * Le {@code knobs-<nome>.json}. Exige exatamente os campos de {@link #CAMPOS} (sem
     * desconhecidos, sem {@code sha256}), com tipos estritos; calcula o SHA-256 dos bytes lidos.
     *
     * @throws IOException              falha de leitura do arquivo
     * @throws IllegalArgumentException conteudo invalido (JSON malformado, campo ausente ou
     *                                  desconhecido, tipo errado, valor fora das regras), citando o campo
     */
    public static ParametrosKnobs deJson(Path arquivo) throws IOException {
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
            throw new IllegalArgumentException("versao deve ser texto: " + v);
        }
        return new ParametrosKnobs(v.asText(),
                real(raiz, "k1FracaoConservador"), inteiroLongo(raiz, "k2PausaConservadorNs"),
                real(raiz, "k2MultPensarConservador"), inteiroLongo(raiz, "k3AdiaMaxNs"),
                inteiroLongo(raiz, "k3SondagemNs"), real(raiz, "k4FatorBaseConservador"),
                real(raiz, "k4JitterConservador"), sha256(bytes));
    }

    private static long inteiroLongo(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isIntegralNumber() || !n.canConvertToLong()) {
            throw new IllegalArgumentException(campo + " deve ser inteiro (long): " + n);
        }
        return n.longValue();
    }

    private static double real(JsonNode raiz, String campo) {
        JsonNode n = raiz.get(campo);
        if (!n.isNumber()) {
            throw new IllegalArgumentException(campo + " deve ser numero: " + n);
        }
        return n.doubleValue();
    }

    private static void finito(String nome, double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(nome + " deve ser finito: " + v);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
