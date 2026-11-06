package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;

import java.util.Comparator;

/** Esqueleto (fase vermelha do A1). */
final class OrdemSessao {
    static final Comparator<SessionState> CONTEUDO = (a, b) -> 0;

    private OrdemSessao() { }

    static int rankEstado(SessionState.Estado e) { return 0; }

    static int rankMotivo(SessionState.Motivo m) { return 0; }

    static boolean mesmoConteudo(SessionState a, SessionState b) { return false; }
}
