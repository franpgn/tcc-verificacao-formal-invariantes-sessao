package br.unipampa.tcc.servidor;

import org.infinispan.container.entries.CacheEntry;

/** Esqueleto (fase vermelha do A1). */
record Decisao(Resultado resultado, CacheEntry<Object, Object> escolhida, MotivoSaida motivoSaida, String chave,
               boolean igualPreferredNonNull) {
    enum Resultado { ESCOLHIDA, NENHUMA, SAIDA_SEGURANCA }

    String linha() { return ""; }
}
