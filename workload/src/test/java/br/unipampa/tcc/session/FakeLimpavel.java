package br.unipampa.tcc.session;

import java.util.Map;

/**
 * B-43.1: duble de {@link CacheLimpavel} sobre o mapa de um {@link FakeCaches.Cache} (sem editar
 * {@code FakeCaches}). Conta as chamadas a {@code clear()}/{@code size()} e simula os modos de
 * falha do reset: {@code clear} ignorado, {@code clear} que chega aos donos so na k-esima consulta
 * (ASYNC) e excecao em {@code clear}/{@code size}.
 */
final class FakeLimpavel implements CacheLimpavel {

    private final String nome;
    private final Map<String, ?> dados;

    int clears;
    int sizes;

    /** {@code true}: {@code clear()} nao tem efeito (cache que nao esvazia). */
    boolean ignorarClear;

    /**
     * {@code k > 0}: o {@code clear()} so e aplicado na k-esima consulta a {@code size()} depois
     * dele (ate la {@code size()} devolve o tamanho antigo), como num cache ASYNC.
     */
    int aplicaNaConsulta;

    RuntimeException falhaNoClear;
    RuntimeException falhaNoSize;

    private int pendentes;

    FakeLimpavel(String nome, FakeCaches.Cache<?> cache) {
        this.nome = nome;
        this.dados = cache.dados;
    }

    @Override
    public String nome() {
        return nome;
    }

    @Override
    public void clear() {
        clears++;
        if (falhaNoClear != null) {
            throw falhaNoClear;
        }
        if (ignorarClear) {
            return;
        }
        if (aplicaNaConsulta > 0) {
            pendentes = aplicaNaConsulta;
            return;
        }
        dados.clear();
    }

    @Override
    public int size() {
        sizes++;
        if (falhaNoSize != null) {
            throw falhaNoSize;
        }
        if (pendentes > 0 && --pendentes == 0) {
            dados.clear();
        }
        return dados.size();
    }
}
