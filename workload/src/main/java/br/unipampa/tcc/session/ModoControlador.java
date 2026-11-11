package br.unipampa.tcc.session;

import java.util.Locale;

/**
 * W-3 (B-28.2): valor de {@code --controller}. Fica em {@code session}, e nao em {@code controle}, para que
 * {@code --controller off} (padrao) nao crie nenhum objeto do pacote {@code controle}.
 */
public enum ModoControlador {
    /** Sem controlador: nenhuma thread, nenhum arquivo, fluxo identico ao anterior ao W-3. */
    OFF,
    /** Decide e registra; nunca chama o atuador. */
    SHADOW,
    /** Decide, registra e entrega as acoes ao atuador. */
    ACTIVE;

    public String rotulo() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Aceita exatamente {@code off}, {@code shadow} ou {@code active}. */
    public static ModoControlador porRotulo(String valor) {
        for (ModoControlador m : values()) {
            if (m.rotulo().equals(valor)) {
                return m;
            }
        }
        throw new IllegalArgumentException("valor invalido '" + valor + "' para --controller (aceitos: off, shadow, active)");
    }
}
