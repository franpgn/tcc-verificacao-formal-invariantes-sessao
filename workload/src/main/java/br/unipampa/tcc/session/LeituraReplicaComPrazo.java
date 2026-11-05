package br.unipampa.tcc.session;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * P-41 (B-44.2, I-3): leitura de uma replica pelos leitores multi-replica do B-24 (rodada final do
 * {@link InvariantAuditor} e laco de convergencia) com <b>prazo sem resposta</b>. Com um no parado ate o fim da
 * execucao (AC-5), o {@code entrySet} pelo cliente BASIC fixado nele ficava preso sem prazo
 * ({@code runs/b44-2/integracao/I3-corte-async-com-perdida/jstack-preso.txt}), e a carga nao gravava os CSV nem
 * terminava.
 *
 * <p>A leitura roda numa thread daemon propria; quem chama espera no maximo o prazo <i>desde a ultima entrada
 * recebida</i> (a leitura avisa cada entrada pelo {@link Runnable} que recebe) ou desde o inicio. Passado o prazo sem
 * resposta, a leitura e interrompida (o iterador do cliente Hot Rod cancela a assinatura na interrupcao) e a chamada
 * lanca {@link ReplicaSemResposta}: o {@link InvariantAuditor} ja exclui da comparacao a replica cuja leitura lanca
 * (B-33) e a conta em {@code leiturasFalhas}. O prazo e o da leitura da sonda S_lag, o {@code --hotrod-timeout-ms}
 * (decisao do coordenador de 05/11), contado sem resposta e nao sobre a iteracao inteira, para uma replica lenta mas
 * respondendo nao virar "nao lida".</p>
 *
 * <p>O resultado da ultima chamada fica em {@link #ultimoResultado()}: {@value #NAO_CHAMADA}, {@value #LIDA},
 * {@value #SEM_RESPOSTA}, {@value #INTERROMPIDA} ou {@value #ERRO} seguido do nome simples da excecao. Uma leitura que
 * lanca (DEGRADED, por exemplo) continua lancando a mesma excecao, como antes.</p>
 *
 * @param <T> tipo dos valores lidos
 */
final class LeituraReplicaComPrazo<T> implements Supplier<Map<String, T>> {

    static final String NAO_CHAMADA = "nao-chamada";
    static final String LIDA = "lida";
    static final String SEM_RESPOSTA = "nao-lida:sem-resposta";
    static final String INTERROMPIDA = "nao-lida:interrompida";
    static final String ERRO = "nao-lida:erro:";

    /** A replica nao respondeu no prazo (ou quem chamava foi interrompido): leitura nao feita. */
    static final class ReplicaSemResposta extends RuntimeException {
        ReplicaSemResposta(String mensagem) {
            super(mensagem);
        }
    }

    private final String rotulo;
    private final long prazoMs;
    private final long prazoNs;
    private final Function<Runnable, Map<String, T>> leitura;
    private volatile String ultimo = NAO_CHAMADA;

    /**
     * @param rotulo  {@code <cache>@<host:porta>} da replica (vai na linha da saida e na mensagem)
     * @param prazoMs prazo sem resposta, em ms ({@code --hotrod-timeout-ms}); &gt; 0
     * @param leitura le a replica inteira; chama o {@link Runnable} recebido a cada entrada que chega
     */
    LeituraReplicaComPrazo(String rotulo, long prazoMs, Function<Runnable, Map<String, T>> leitura) {
        this.rotulo = Objects.requireNonNull(rotulo, "rotulo");
        if (prazoMs <= 0) {
            throw new IllegalArgumentException("prazo sem resposta deve ser > 0 ms: " + prazoMs);
        }
        this.prazoMs = prazoMs;
        this.prazoNs = TimeUnit.MILLISECONDS.toNanos(prazoMs);
        this.leitura = Objects.requireNonNull(leitura, "leitura");
    }

    String rotulo() {
        return rotulo;
    }

    /** Resultado da ultima chamada de {@link #get()} (ou {@value #NAO_CHAMADA}). */
    String ultimoResultado() {
        return ultimo;
    }

    @Override
    public Map<String, T> get() {
        AtomicLong ultimaEntradaNs = new AtomicLong(System.nanoTime());
        FutureTask<Map<String, T>> tarefa = new FutureTask<>(
                () -> leitura.apply(() -> ultimaEntradaNs.set(System.nanoTime())));
        Thread t = new Thread(tarefa, "leitura-replica-" + rotulo);
        t.setDaemon(true);
        t.start();
        try {
            while (true) {
                long espera = ultimaEntradaNs.get() + prazoNs - System.nanoTime();
                if (espera <= 0 && tarefa.cancel(true)) {
                    ultimo = SEM_RESPOSTA;
                    throw new ReplicaSemResposta("replica " + rotulo + " sem resposta em " + prazoMs
                            + " ms (--hotrod-timeout-ms): nao lida (P-41)");
                }
                try {
                    Map<String, T> m = tarefa.get(Math.max(espera, 0L), TimeUnit.NANOSECONDS);
                    ultimo = LIDA;
                    return m;
                } catch (TimeoutException e) {
                    // confere o progresso e espera de novo
                }
            }
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            ultimo = ERRO + c.getClass().getSimpleName();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error er) {
                throw er;
            }
            throw new IllegalStateException(c);
        } catch (CancellationException e) {
            ultimo = SEM_RESPOSTA; // nao acontece: so este metodo cancela, e lanca logo depois
            throw new ReplicaSemResposta("replica " + rotulo + " cancelada");
        } catch (InterruptedException e) {
            tarefa.cancel(true);
            Thread.currentThread().interrupt();
            ultimo = INTERROMPIDA;
            throw new ReplicaSemResposta("leitura da replica " + rotulo + " interrompida");
        }
    }
}
