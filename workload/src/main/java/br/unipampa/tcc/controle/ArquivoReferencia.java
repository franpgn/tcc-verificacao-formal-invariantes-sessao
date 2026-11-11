package br.unipampa.tcc.controle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * W-3 (B-28.2): arquivo {@code referencia-<cenario>-<perfil>.json} gerado por
 * {@code analysis/referencia_p99.py} (B-39.1, esquema {@value #ESQUEMA}). So os campos que o
 * controlador usa sao lidos ({@code esquema}, {@code cenario}, {@code perfil}, {@code p99_ns}); os demais
 * (origem, n, omitidas) ficam no arquivo, cujo SHA-256 vai ao JSONL.
 *
 * @param referencia p99 por operacao
 * @param cenario    {@code S1}, {@code S2} ou {@code S3}
 * @param perfil     {@code sync} ou {@code async}
 * @param sha256     SHA-256 (hex minusculo) dos bytes lidos
 */
public record ArquivoReferencia(Referencia referencia, String cenario, String perfil, String sha256) {

    /** Esquema aceito (constante {@code ESQUEMA} de {@code analysis/referencia_p99.py}). */
    public static final String ESQUEMA = "referencia-p99/1";

    public ArquivoReferencia {
        Objects.requireNonNull(referencia, "referencia");
        Objects.requireNonNull(cenario, "cenario");
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(sha256, "sha256");
    }

    /**
     * @throws IOException              falha de leitura
     * @throws IllegalArgumentException conteudo invalido
     */
    public static ArquivoReferencia ler(Path arquivo) throws IOException {
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
        String esquema = texto(raiz, "esquema", arquivo);
        if (!ESQUEMA.equals(esquema)) {
            throw new IllegalArgumentException("esquema " + esquema + " em " + arquivo + " (aceito: " + ESQUEMA + ")");
        }
        String cenario = texto(raiz, "cenario", arquivo);
        String perfil = texto(raiz, "perfil", arquivo);
        JsonNode p99 = raiz.get("p99_ns");
        if (p99 == null || !p99.isObject()) {
            throw new IllegalArgumentException("p99_ns ausente ou nao objeto em " + arquivo);
        }
        Map<String, Long> m = new TreeMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = p99.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (!v.isIntegralNumber() || !v.canConvertToLong()) {
                throw new IllegalArgumentException("p99_ns." + e.getKey() + " deve ser inteiro: " + v);
            }
            m.put(e.getKey(), v.longValue());
        }
        return new ArquivoReferencia(Referencia.de(m), cenario, perfil, sha256(bytes));
    }

    private static String texto(JsonNode raiz, String campo, Path arquivo) {
        JsonNode n = raiz.get(campo);
        if (n == null || !n.isTextual()) {
            throw new IllegalArgumentException(campo + " ausente ou nao texto em " + arquivo);
        }
        return n.asText();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
