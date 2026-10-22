package br.unipampa.tcc.controle;

/**
 * Perfil de operacao do cliente decidido pelo controlador (B-28.1, plano de 2026-10-22, secao 3.4).
 * A severidade nao vira um terceiro perfil: vira {@link Motivo} e tempo de retencao.
 */
public enum Perfil {
    NORMAL,
    CONSERVADOR
}
