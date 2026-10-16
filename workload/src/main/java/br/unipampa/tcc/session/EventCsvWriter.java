package br.unipampa.tcc.session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coletor de eventos crus de operação, gravados em CSV por repetição
 * (bloco de eventos, T3/T4).
 *
 * <p>Cada operação O1–O5 medida emite um evento cru com a forma
 * {@code {operation, start_ns, end_ns, replica, return_code, key}}. O
 * {@code op_id} é um {@link AtomicLong} <b>global ao processo</b>,
 * compartilhado entre todas as repetições, de modo que cada evento tenha
 * identidade única no experimento.</p>
 *
 * <p>Esta classe é <b>independente</b> do {@link LatencyRegistry}: o
 * registry continua agregando latências em histogramas para o resumo em
 * <i>stdout</i>, enquanto este escritor produz o CSV por evento consumido
 * pelo pipeline {@code analysis/}. As duas trilhas usam a mesma
 * {@link WarmupPolicy}, então um evento de warm-up não vaza para nenhuma
 * das duas.</p>
 *
 * <p>Cabeçalho exato do CSV (não alterar — contrato com
 * {@code analysis/percentis.py} e {@code analysis/compare_scenarios.py}):</p>
 * <pre>op_id,operation,start_ns,end_ns,replica,return_code,key</pre>
 *
 * <p>Os eventos são acumulados em memória durante a repetição e gravados
 * de uma só vez em {@link #flush(Path)} ao final, preservando a ordem de
 * inserção. {@code start_ns} e {@code end_ns} já vêm convertidos para
 * epoch-ns pelo chamador via {@link EpochClock}.</p>
 */
public final class EventCsvWriter {

    /** Cabeçalho canônico do CSV de eventos. */
    public static final String HEADER =
            "op_id,operation,start_ns,end_ns,replica,return_code,key";

    /** B-33: cabecalho com a coluna de fase, usado so quando ha marcadores de falha. */
    public static final String HEADER_COM_FASE = HEADER + ",phase";

    /** Contador global de op_id, compartilhado entre repetições. */
    private static final AtomicLong OP_ID_SEQ = new AtomicLong(0L);

    /** Evento cru de uma operação. */
    public static final class Event {
        final long opId;
        final String operation;
        final long startNs;
        final long endNs;
        final String replica;
        final String returnCode;
        final String key;

        Event(long opId, String operation, long startNs, long endNs,
              String replica, String returnCode, String key) {
            this.opId = opId;
            this.operation = operation;
            this.startNs = startNs;
            this.endNs = endNs;
            this.replica = replica;
            this.returnCode = returnCode;
            this.key = key;
        }
    }

    private final WarmupPolicy politica;
    private final List<Event> eventos = new ArrayList<>();
    private final AtomicLong descartadosWarmup = new AtomicLong(0L);

    /** Constrói o escritor com a política de warm-up/descarte. */
    public EventCsvWriter(WarmupPolicy politica) {
        this.politica = politica != null ? politica : WarmupPolicy.always();
    }

    /**
     * Registra um evento se e somente se o instante atual estiver na
     * janela de medição (warm-up e descarte são suprimidos). Os tempos
     * {@code startNs}/{@code endNs} são epoch-ns (já convertidos).
     *
     * @return {@code true} se o evento entrou; {@code false} se foi
     *         descartado por estar fora da janela de medição.
     */
    public boolean registrar(String operation, long startNs, long endNs,
                             String replica, String returnCode, String key) {
        if (!politica.deveRegistrar()) {
            descartadosWarmup.incrementAndGet();
            return false;
        }
        long id = OP_ID_SEQ.getAndIncrement();
        Event e = new Event(id, operation, startNs, endNs,
                sanitizar(replica), returnCode, sanitizar(key));
        synchronized (eventos) {
            eventos.add(e);
        }
        return true;
    }

    /** Quantidade de eventos suprimidos por warm-up/descarte. */
    public long descartadosWarmup() {
        return descartadosWarmup.get();
    }

    /** Quantidade de eventos efetivamente registrados nesta repetição. */
    public int totalRegistrados() {
        synchronized (eventos) {
            return eventos.size();
        }
    }

    /**
     * Grava todos os eventos acumulados no caminho indicado, criando os
     * diretórios pais se necessário. Mantém a ordem de inserção.
     */
    public void flush(Path destino) throws IOException {
        flush(destino, null);
    }

    /**
     * B-33: como {@link #flush(Path)}, mas com {@code marcadores} nao nulo acrescenta
     * a coluna {@code phase} (fase do {@code start_ns} do evento). Sem marcadores o
     * arquivo e identico ao formato historico consumido por {@code analysis/}.
     */
    public void flush(Path destino, Marcadores marcadores) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        List<Event> copia;
        synchronized (eventos) {
            copia = new ArrayList<>(eventos);
        }
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write(marcadores == null ? HEADER : HEADER_COM_FASE);
            w.newLine();
            for (Event e : copia) {
                w.write(Long.toString(e.opId));
                w.write(',');
                w.write(e.operation);
                w.write(',');
                w.write(Long.toString(e.startNs));
                w.write(',');
                w.write(Long.toString(e.endNs));
                w.write(',');
                w.write(e.replica);
                w.write(',');
                w.write(e.returnCode);
                w.write(',');
                w.write(e.key);
                if (marcadores != null) {
                    w.write(',');
                    w.write(marcadores.rotuloDe(e.startNs));
                }
                w.newLine();
            }
        }
    }

    /**
     * Remove vírgulas e quebras de linha de um campo livre (replica, key)
     * para não corromper o CSV. Os identificadores usados (UUID de sessão,
     * nome de identidade, host:port da replica) não contêm vírgula em
     * operação normal; esta é uma salvaguarda defensiva.
     */
    private static String sanitizar(String v) {
        if (v == null) {
            return "";
        }
        return v.replace(',', '_').replace('\n', ' ').replace('\r', ' ');
    }
}
