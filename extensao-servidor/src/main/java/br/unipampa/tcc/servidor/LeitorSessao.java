package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;

import java.util.Set;

/** Esqueleto (fase vermelha do A1). */
final class LeitorSessao {
    static final Set<String> CLASSES_PERMITIDAS = Set.of();

    record Leitura(SessionState sessao, MotivoSaida falha, String detalhe) {
        boolean ok() { return sessao != null; }
    }

    private LeitorSessao() { }

    static Leitura ler(Object valor) { return new Leitura(null, MotivoSaida.ERRO_INTERNO, "esqueleto"); }

    static String lerChave(Object chave) { return ""; }
}
