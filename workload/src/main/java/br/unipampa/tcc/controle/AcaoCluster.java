package br.unipampa.tcc.controle;

import java.util.List;
import java.util.Objects;

/**
 * W-3 (B-28.2): uma acao do controlador sobre o <i>cluster</i> (plano de execucao de 2026-11-04,
 * secao 4.4). Os nomes de chamada de cada tipo sao do W-2 (B-44.2), que so usa o que o B-44.1 marcou
 * como verificado; aqui a acao e so um valor.
 *
 * @param tipo   AC-1, AC-2 ou AC-3
 * @param caches caches afetados (nao vazio)
 * @param alvo   rotulo do no de destino, ou {@code null} quando a acao vale para o <i>cluster</i>
 * @param valor  valor a aplicar (ex.: {@code DEGRADED_MODE}, um {@code remote-timeout}), ou {@code null}
 */
public record AcaoCluster(Tipo tipo, List<String> caches, String alvo, String valor) {

    /** Tipos de acao da secao 4.4 do plano de execucao. */
    public enum Tipo {
        /** DEGRADED antecipado (mecanismo M1). */
        AC_1("AC-1"),
        /** Rebalanceamento suspenso (M5). */
        AC_2("AC-2"),
        /** {@code remote-timeout} (latencia em SYNC). */
        AC_3("AC-3");

        private final String rotulo;

        Tipo(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    public AcaoCluster {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(caches, "caches");
        if (caches.isEmpty()) {
            throw new IllegalArgumentException("acao sem caches");
        }
        for (String c : caches) {
            if (c == null || c.isBlank()) {
                throw new IllegalArgumentException("nome de cache vazio em " + caches);
            }
        }
        caches = List.copyOf(caches);
    }

    /**
     * Identidade da acao para a idempotencia e a pilha de reversao: tipo, caches e alvo. O valor nao
     * entra (a mesma acao nao e aplicada duas vezes com valores diferentes).
     */
    public String chave() {
        return tipo.rotulo() + "|" + String.join(",", caches) + "|" + (alvo == null ? "*" : alvo);
    }
}
