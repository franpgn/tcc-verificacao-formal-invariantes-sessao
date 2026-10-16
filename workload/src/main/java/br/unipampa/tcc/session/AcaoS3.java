package br.unipampa.tcc.session;

/**
 * B-33 (E2): uma acao de aplicacao do cenario S3 (o que o usuario ou o administrador fez e o
 * que percebeu), com todas as tentativas colapsadas. Complementa o CSV de eventos, que registra
 * cada tentativa individual mas nao distingue, por exemplo, Validate que viu {@code INVALID}
 * (o {@code return_code} de um Validate bem-sucedido e sempre {@code VALID}).
 *
 * @param tNs            inicio da acao, na base do {@link RelogioS3} (nanoTime em producao)
 * @param ator           identificador do ator (usuario, conta de L ou admin)
 * @param operacao       nome canonico da operacao (Login, Validate, ..., Block, Unblock)
 * @param alvo           identidade ou sid
 * @param resultado      resultado final (ex.: OK_VALIDATE:INVALID, BLOCKED, ERROR_TRANSPORT)
 * @param tentativas     numero de tentativas feitas (1 + retentativas)
 * @param falhaPercebida {@code true} se todas as tentativas deram erro (o usuario viu a falha)
 * @param nota           observacao livre ({@code tentativa-incerta} para Block/Unblock com erro)
 */
public record AcaoS3(long tNs, String ator, String operacao, String alvo, String resultado,
                     int tentativas, boolean falhaPercebida, String nota) {

    public static final String HEADER =
            "t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota";

    /** Linha de CSV sem vírgulas nos campos livres. */
    public String paraCsv(long tEpochNs) {
        return tEpochNs + "," + limpar(ator) + "," + limpar(operacao) + "," + limpar(alvo) + ","
                + limpar(resultado) + "," + tentativas + "," + falhaPercebida + "," + limpar(nota);
    }

    private static String limpar(String v) {
        return v == null ? "" : v.replace(',', '_').replace('\n', ' ').replace('\r', ' ');
    }
}
