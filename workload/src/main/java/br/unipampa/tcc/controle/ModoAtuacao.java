package br.unipampa.tcc.controle;

import java.util.Locale;

/**
 * W-3 (B-28.2): modo do laco de controle quando ligado. {@code off} nao existe aqui: com
 * {@code --controller off} nenhum objeto deste pacote e criado (a opcao vive em {@code session}).
 */
public enum ModoAtuacao {
    /** Decide e registra; o atuador nunca e chamado ({@code "executada": false}). */
    SHADOW,
    /** Decide, registra e entrega as acoes ao atuador. */
    ACTIVE;

    /** Rotulo da CLI e do JSONL ({@code shadow} ou {@code active}). */
    public String rotulo() {
        return name().toLowerCase(Locale.ROOT);
    }
}
