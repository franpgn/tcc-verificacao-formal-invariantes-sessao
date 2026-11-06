package br.unipampa.tcc.servidor;

/** Esqueleto (fase vermelha do A1). */
interface RegistroFusao {
    void registrar(Decisao d);

    default void formato(String linha) { }
}
