package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B-43.2: duble de {@link CacheCarregavel} sobre o mapa de um {@link FakeCaches.Cache} (sem editar
 * {@code FakeCaches} nem {@link FakeLimpavel}). Conta {@code putAll}/{@code size}, guarda o tamanho
 * de cada lote e simula os modos de falha da carga: replicacao atrasada (ASYNC), excecao em
 * {@code putAll}/{@code size} e estado sujo (tamanho maior que o carregado).
 */
final class FakeCarregavel implements CacheCarregavel {

    private final String nome;
    private final FakeCaches.Cache<SessionState> cache;

    int putAlls;
    int sizes;
    int clears;
    final List<Integer> tamanhosDosLotes = new ArrayList<>();

    /**
     * {@code k > 0}: as primeiras {@code k - 1} consultas a {@code size()} devolvem o tamanho real
     * menos {@code faltando} (como num cache ASYNC em que os ultimos {@code put} ainda nao chegaram a
     * todos os donos); da k-esima em diante, o tamanho real. {@code Integer.MAX_VALUE}: nunca chega.
     */
    int completaNaConsulta;
    int faltando = 1;

    /** Somado ao tamanho real em toda consulta (estado sujo). */
    int extra;

    /** Lanca no {@code putAll} de numero {@code falhaNoLote} (1 = o primeiro). */
    int falhaNoLote;
    RuntimeException falhaNoPutAll;
    RuntimeException falhaNoSize;

    FakeCarregavel(String nome, FakeCaches.Cache<SessionState> cache) {
        this.nome = nome;
        this.cache = cache;
    }

    @Override
    public String nome() {
        return nome;
    }

    @Override
    public void clear() {
        clears++;
        cache.dados.clear();
    }

    @Override
    public int size() {
        sizes++;
        if (falhaNoSize != null) {
            throw falhaNoSize;
        }
        int real = cache.dados.size() + extra;
        if (completaNaConsulta > 0 && sizes < completaNaConsulta) {
            return real - faltando;
        }
        return real;
    }

    @Override
    public void putAll(Map<String, SessionState> lote) {
        putAlls++;
        if (falhaNoPutAll != null && putAlls == falhaNoLote) {
            throw falhaNoPutAll;
        }
        tamanhosDosLotes.add(lote.size());
        cache.dados.putAll(lote);
    }
}
