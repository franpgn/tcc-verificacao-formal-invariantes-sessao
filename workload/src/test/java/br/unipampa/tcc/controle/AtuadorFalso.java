package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Duble de {@link AtuadorCluster} para os testes (W-3). Grava cada chamada (operacao, acao, thread) e
 * responde pela funcao {@code resposta}; por padrao toda chamada da {@code APLICADA} com HTTP 204.
 * {@link #proibido()} falha o teste se receber qualquer chamada (modo shadow). {@link #bloquear()} faz
 * as chamadas esperarem {@link #liberar()}.
 */
public final class AtuadorFalso implements AtuadorCluster {

    /** Uma chamada recebida. */
    public record Chamada(String operacao, AcaoCluster acao, String thread, long prazoNs) {
    }

    private final List<Chamada> chamadas = new ArrayList<>();
    private volatile BiFunction<String, AcaoCluster, ResultadoAtuacao> resposta =
            (op, a) -> ResultadoAtuacao.aplicada(204, "lido-" + op);
    private volatile boolean proibido;
    private volatile CountDownLatch trava;
    private final CountDownLatch primeira = new CountDownLatch(1);

    public static AtuadorFalso proibido() {
        AtuadorFalso a = new AtuadorFalso();
        a.proibido = true;
        return a;
    }

    public AtuadorFalso responder(BiFunction<String, AcaoCluster, ResultadoAtuacao> r) {
        this.resposta = r;
        return this;
    }

    public AtuadorFalso bloquear() {
        this.trava = new CountDownLatch(1);
        return this;
    }

    public void liberar() {
        CountDownLatch t = trava;
        if (t != null) {
            t.countDown();
        }
    }

    /** Espera a primeira chamada chegar (ate 5 s). */
    public boolean esperarPrimeira() throws InterruptedException {
        return primeira.await(5, TimeUnit.SECONDS);
    }

    public synchronized List<Chamada> chamadas() {
        return List.copyOf(chamadas);
    }

    @Override
    public String nome() {
        return "falso";
    }

    @Override
    public ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) throws Exception {
        return chamar("aplicar", acao, prazoNs);
    }

    @Override
    public ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) throws Exception {
        return chamar("reverter", acao, prazoNs);
    }

    private ResultadoAtuacao chamar(String op, AcaoCluster acao, long prazoNs) throws Exception {
        synchronized (this) {
            chamadas.add(new Chamada(op, acao, Thread.currentThread().getName(), prazoNs));
        }
        primeira.countDown();
        if (proibido) {
            throw new AssertionError("atuador chamado em modo proibido: " + op + " " + acao);
        }
        CountDownLatch t = trava;
        if (t != null) {
            t.await();
        }
        return resposta.apply(op, acao);
    }
}
