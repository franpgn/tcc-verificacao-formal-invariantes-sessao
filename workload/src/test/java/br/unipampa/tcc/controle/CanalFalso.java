package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * W-4: canal falso da {@link SondaDefasagem}, com relogio real. Cada chave guarda o valor atual (com o instante e
 * o no da escrita) e o anterior. O no Y ve o valor atual escrito por X quando nenhum dos dois esta cortado e ja
 * passou {@code atrasoMs[X][Y]}; senao ve o anterior. Permite tambem leitura que bloqueia (interrompivel ou nao),
 * e escrita que falha. Grava as chaves e os valores usados.
 */
class CanalFalso implements CanalCanario {

    private record Entrada(String atual, long tNs, int escritor, String anterior) {
    }

    final int n;
    final long[][] atrasoMs;
    final Set<Integer> cortados = Collections.synchronizedSet(new HashSet<>());
    /** No cujas leituras bloqueiam por {@code bloqueioLeituraMs}, respondendo a interrupcao. */
    volatile int leituraBloqueia = -1;
    volatile long bloqueioLeituraMs;
    /** No cujas leituras nao cedem a interrupcao ate {@link #liberar} contar. */
    volatile int leituraPresa = -1;
    final CountDownLatch liberar = new CountDownLatch(1);
    /** No cujas escritas lancam excecao. */
    volatile int escritaFalha = -1;

    private final Map<String, Entrada> dados = new HashMap<>();
    final List<String> chavesEscritas = Collections.synchronizedList(new ArrayList<>());
    final List<String> chavesLidas = Collections.synchronizedList(new ArrayList<>());
    final List<String> valoresEscritos = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger escritas = new AtomicInteger();
    final AtomicInteger leituras = new AtomicInteger();
    final AtomicInteger fechamentos = new AtomicInteger();

    CanalFalso(int n) {
        this.n = n;
        this.atrasoMs = new long[n][n];
    }

    /** Valor que existe antes da primeira rodada (por exemplo, de uma execucao anterior). */
    synchronized void preexistente(String chave, String valor) {
        dados.put(chave, new Entrada(valor, 0, -1, valor));
    }

    @Override
    public int nos() {
        return n;
    }

    @Override
    public String rotulo(int indice) {
        return "no" + indice;
    }

    @Override
    public void escrever(int indice, String chave, String valor) throws Exception {
        escritas.incrementAndGet();
        chavesEscritas.add(chave);
        valoresEscritos.add(valor);
        if (indice == escritaFalha) {
            throw new IllegalStateException("escrita falha no no " + indice);
        }
        synchronized (this) {
            Entrada e = dados.get(chave);
            dados.put(chave, new Entrada(valor, System.nanoTime(), indice, e == null ? null : e.atual()));
        }
    }

    @Override
    public String ler(int indice, String chave) throws Exception {
        leituras.incrementAndGet();
        chavesLidas.add(chave);
        if (indice == leituraBloqueia) {
            TimeUnit.MILLISECONDS.sleep(bloqueioLeituraMs); // InterruptedException sobe
        }
        if (indice == leituraPresa) {
            while (liberar.getCount() > 0) {
                Thread.onSpinWait(); // nao cede a interrupcao
            }
        }
        synchronized (this) {
            Entrada e = dados.get(chave);
            if (e == null) {
                return null;
            }
            if (e.escritor() < 0) {
                return e.atual();
            }
            boolean visivel = !cortados.contains(e.escritor()) && !cortados.contains(indice)
                    && (System.nanoTime() - e.tNs()) >= TimeUnit.MILLISECONDS.toNanos(atrasoMs[e.escritor()][indice]);
            return visivel || e.escritor() == indice ? e.atual() : e.anterior();
        }
    }

    @Override
    public void close() {
        fechamentos.incrementAndGet();
    }
}
