package br.unipampa.tcc.servidor;

import org.infinispan.conflict.EntryMergePolicy;
import org.infinispan.container.entries.CacheEntry;

import java.util.List;

/** Esqueleto (fase vermelha do A1): devolve a preferida, como PREFERRED_ALWAYS. */
public final class PoliticaFusaoSessoes implements EntryMergePolicy<Object, Object> {
    private final RegistroFusao registro;

    public PoliticaFusaoSessoes() { this(d -> { }); }

    PoliticaFusaoSessoes(RegistroFusao registro) { this.registro = registro; }

    @Override
    public CacheEntry<Object, Object> merge(CacheEntry<Object, Object> preferredEntry,
                                            List<CacheEntry<Object, Object>> otherEntries) {
        return preferredEntry;
    }
}
