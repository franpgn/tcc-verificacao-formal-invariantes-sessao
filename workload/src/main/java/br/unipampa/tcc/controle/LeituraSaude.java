package br.unipampa.tcc.controle;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * B-26.2: uma leitura de saude de UM no do cluster, ja sem formato de transporte (o adaptador REST
 * converte o JSON do Infinispan nisto). O nucleo {@code controle} nao conhece REST nem Infinispan.
 *
 * @param numeroDeNos    {@code cluster_health.number_of_nodes} visto pelo no consultado ({@code >= 0})
 * @param statusPorCache status por nome de cache, exatamente como o servidor devolveu; copia imutavel
 *                       e ordenada por nome
 */
public record LeituraSaude(int numeroDeNos, Map<String, String> statusPorCache) {

    public LeituraSaude {
        if (numeroDeNos < 0) {
            throw new IllegalArgumentException("numeroDeNos negativo: " + numeroDeNos);
        }
        Objects.requireNonNull(statusPorCache, "statusPorCache");
        SortedMap<String, String> copia = new TreeMap<>();
        statusPorCache.forEach((k, v) -> copia.put(Objects.requireNonNull(k, "nome de cache nulo"),
                Objects.requireNonNull(v, "status nulo para " + k)));
        statusPorCache = Collections.unmodifiableSortedMap(copia);
    }
}
