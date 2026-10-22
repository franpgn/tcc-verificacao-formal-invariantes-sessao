package br.unipampa.tcc.session;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * B-43.8: duble em memoria de {@link ArmazemVersionado} (o {@link FakeCaches} nao muda). Cada escrita
 * aplicada ({@code put} ou {@code substituirSeVersao} com sucesso) da a chave uma versao nova, de um
 * contador global, como o servidor Hot Rod. A comparacao de versao e a escrita condicional sao
 * atomicas (sob o monitor do duble).
 *
 * <p>Ganchos (chamados FORA do monitor, para que a operacao concorrente injetada possa usar o mesmo
 * duble): {@link #antes} antes de cada operacao e {@link #depois} logo depois de uma escrita aplicada
 * ({@code op} = {@code "get"}, {@code "put"}, {@code "entrySet"}, {@code "lerComVersao"},
 * {@code "substituirSeVersao"}).</p>
 */
final class FakeVersionado<V> implements ArmazemVersionado<V> {

    private final Map<String, Versionado<V>> dados = new LinkedHashMap<>();
    private long proximaVersao = 1;

    volatile BiConsumer<String, String> antes = (op, chave) -> { };
    volatile BiConsumer<String, String> depois = (op, chave) -> { };

    /** W5: toda escrita condicional falha e muda a versao sem mudar o valor. */
    volatile boolean sempreConflita;

    final AtomicInteger tentativasCondicionais = new AtomicInteger();
    final AtomicInteger escritasCondicionaisAplicadas = new AtomicInteger();
    final AtomicInteger leiturasVersionadas = new AtomicInteger();

    /** Grava direto, sem gancho (montagem de cenario). */
    synchronized void semear(String chave, V valor) {
        dados.put(chave, new Versionado<>(valor, proximaVersao++));
    }

    /** Valor corrente, sem gancho (asserção). */
    synchronized V valor(String chave) {
        Versionado<V> v = dados.get(chave);
        return v == null ? null : v.valor();
    }

    synchronized Map<String, V> copia() {
        Map<String, V> m = new LinkedHashMap<>();
        dados.forEach((k, v) -> m.put(k, v.valor()));
        return m;
    }

    @Override
    public V get(String chave) {
        antes.accept("get", chave);
        return valor(chave);
    }

    @Override
    public V put(String chave, V valor) {
        antes.accept("put", chave);
        V anterior;
        synchronized (this) {
            Versionado<V> a = dados.put(chave, new Versionado<>(valor, proximaVersao++));
            anterior = a == null ? null : a.valor();
        }
        depois.accept("put", chave);
        return anterior;
    }

    @Override
    public Set<Map.Entry<String, V>> entrySet() {
        antes.accept("entrySet", "");
        Set<Map.Entry<String, V>> s = new LinkedHashSet<>();
        for (Map.Entry<String, V> e : copia().entrySet()) {
            s.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
        }
        return s;
    }

    @Override
    public Versionado<V> lerComVersao(String chave) {
        antes.accept("lerComVersao", chave);
        leiturasVersionadas.incrementAndGet();
        synchronized (this) {
            return dados.get(chave);
        }
    }

    @Override
    public boolean substituirSeVersao(String chave, V novo, long versao) {
        antes.accept("substituirSeVersao", chave);
        tentativasCondicionais.incrementAndGet();
        synchronized (this) {
            Versionado<V> a = dados.get(chave);
            if (a == null) {
                return false;
            }
            if (sempreConflita) {
                dados.put(chave, new Versionado<>(a.valor(), proximaVersao++));
                return false;
            }
            if (a.versao() != versao) {
                return false;
            }
            dados.put(chave, new Versionado<>(novo, proximaVersao++));
            escritasCondicionaisAplicadas.incrementAndGet();
        }
        depois.accept("substituirSeVersao", chave);
        return true;
    }
}
