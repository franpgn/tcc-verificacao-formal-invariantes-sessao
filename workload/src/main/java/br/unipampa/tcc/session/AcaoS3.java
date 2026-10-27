package br.unipampa.tcc.session;

/**
 * B-33 (E2): uma acao de aplicacao do cenario S3 (o que o usuario ou o administrador fez e o
 * que percebeu), com todas as tentativas colapsadas. Complementa o CSV de eventos, que registra
 * cada tentativa individual. No contrato CSV 1 o {@code return_code} de um Validate bem-sucedido
 * era sempre {@code VALID} e so este registro distinguia o Validate que viu {@code INVALID}; desde
 * o contrato CSV 2 (B-43.4, {@code docs/contrato-csv-v2.md}) o CSV grava o estado real
 * ({@code VALID} ou {@code INVALID}).
 *
 * @param tNs            inicio da acao, na base do {@link RelogioS3} (nanoTime em producao)
 * @param ator           identificador do ator (usuario, conta de L ou admin)
 * @param operacao       nome canonico da operacao (Login, Validate, ..., Block, Unblock)
 * @param alvo           identidade ou sid
 * @param resultado      resultado final (ex.: OK_VALIDATE:INVALID, BLOCKED, ERROR_TRANSPORT)
 * @param tentativas     numero de tentativas feitas (1 + retentativas)
 * @param falhaPercebida {@code true} se todas as tentativas deram erro (o usuario viu a falha)
 * @param nota           observacao livre ({@code tentativa-incerta} para Block/Unblock com erro)
 * @param tFimNs         B-27: fim da acao (depois da ultima tentativa, inclui backoff e esperas do
 *                       portao), na mesma base de {@code tNs}
 * @param esperaPortaoNs B-27: ns que a acao passou presa no portao de emissao (K1 + K3)
 */
public record AcaoS3(long tNs, String ator, String operacao, String alvo, String resultado,
                     int tentativas, boolean falhaPercebida, String nota, long tFimNs, long esperaPortaoNs) {

    public AcaoS3 {
        if (tFimNs < tNs) {
            throw new IllegalArgumentException("tFimNs < tNs: " + tFimNs + " < " + tNs);
        }
        if (esperaPortaoNs < 0) {
            throw new IllegalArgumentException("esperaPortaoNs negativa: " + esperaPortaoNs);
        }
    }

    /** Contrato {@code docs/contrato-s3-acoes-v2.md}: as 8 primeiras colunas sao as do contrato 1. */
    public static final String HEADER =
            "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns";

    /** Linha de CSV sem vírgulas nos campos livres. */
    public String paraCsv(long tEpochNs, long tFimEpochNs) {
        return tEpochNs + "," + limpar(ator) + "," + limpar(operacao) + "," + limpar(alvo) + ","
                + limpar(resultado) + "," + tentativas + "," + falhaPercebida + "," + limpar(nota) + ","
                + tFimEpochNs + "," + esperaPortaoNs;
    }

    private static String limpar(String v) {
        return v == null ? "" : v.replace(',', '_').replace('\n', ' ').replace('\r', ' ');
    }
}
