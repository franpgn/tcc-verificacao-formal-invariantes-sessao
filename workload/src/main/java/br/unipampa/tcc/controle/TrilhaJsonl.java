package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * W-3 (B-28.2): arquivo JSONL do controlador ({@code observaveis-rep-NNN.jsonl} ou
 * {@code decisoes-rep-NNN.jsonl}). Uma linha por chamada, terminada em {@code \n}, com flush a cada linha.
 * Thread-safe. Uma falha de escrita nunca chega a quem chamou (a carga nao pode morrer pela trilha): e contada
 * em {@link #errosEscrita()}.
 *
 * <p><b>W-2b (P-5, achado N-8 do documento de 05/11).</b> A gravacao sai das threads de quem chama (laco,
 * executor de acoes, sondas). {@link #escrever} so serializa a linha e a poe numa fila em memoria, sem nunca
 * bloquear (nenhuma E/S sob a trava); uma thread gravadora propria ({@value #PREFIXO_THREAD}{@code <arquivo>},
 * daemon) grava as linhas na ordem da fila, com flush a cada linha. Um travamento de disco atrasa so a
 * gravacao, nunca a chamada seguinte do atuador nem o tick do laco. Mesmo padrao da gravadora da
 * {@link SondaDefasagem} (W-4).</p>
 *
 * <ul>
 *   <li><b>Capacidade.</b> A fila guarda no maximo {@link #capacidade()} linhas, fixada na criacao
 *       ({@link #capacidadePara}: {@value #FATOR_CAPACIDADE} x as linhas esperadas na execucao, plano do W-2b,
 *       item 1). Com a fila cheia a linha e contada em {@link #transbordadas()} e a chamada volta na hora.</li>
 *   <li><b>Fim.</b> {@link #escreverFim} poe a linha {@code fim} fora da capacidade (ela nunca se perde por fila
 *       cheia), fecha a trilha para linhas novas e a gravadora preenche o campo de perdas na hora em que grava,
 *       quando ja conhece os erros de todas as linhas anteriores. Nenhuma linha passa do fim.</li>
 *   <li><b>Fechamento com prazo</b> ({@link #fechar(long)}, decisao 5 do coordenador em 05/11). Espera a gravadora
 *       ate o prazo dado (o {@link LacoDeControle} passa o {@code T_enc}); se ela nao terminar (disco travado),
 *       abandona a fila: o que ainda nao foi gravado (a linha em curso, as da fila e o {@code fim}) e contado em
 *       {@link #abandonadas()} e nada mais e gravado; a gravadora fecha o arquivo quando o disco voltar. A linha em
 *       curso no estouro conta como perdida mesmo se o disco a completar depois: o contador e um teto (no maximo uma
 *       linha a mais por arquivo). {@link #close()} espera sem prazo (testes e ferramentas).</li>
 *   <li><b>Perdas</b> ({@link #linhasPerdidas()}): erros de escrita + transbordadas + descartadas (chegaram
 *       depois do fim ou do fechamento) + abandonadas. Uma queda do processo perde as linhas ainda na fila e a
 *       linha em curso.</li>
 *   <li>A gravadora nunca morre por uma linha: {@link IOException} ou {@link RuntimeException} da saida contam como
 *       erro de escrita.</li>
 * </ul>
 */
public final class TrilhaJsonl implements Closeable {

    /**
     * Fator sobre as linhas esperadas por execucao (plano do W-2b, item 1, documento
     * {@code acoes-controlador-cluster-2026-11-05.md} secao 8: "Capacidade = 4 x linhas esperadas por execucao,
     * calculada na criacao").
     */
    public static final int FATOR_CAPACIDADE = 4;

    /** Prefixo do nome da thread gravadora; o resto e o nome do arquivo. */
    public static final String PREFIXO_THREAD = "trilha-jsonl-";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Item da fila: linha comum (ja serializada), linha {@code fim} (serializada na gravadora) ou parada. */
    private record Item(String json, ObjectNode fim, String campoPerdidas, long descartadasNoPedido) {
        static final Item PARAR = new Item(null, null, null, 0);
    }

    private final Path arquivo;
    private final Writer saida;
    private final int capacidade;
    private final LinkedBlockingQueue<Item> fila = new LinkedBlockingQueue<>();
    private final Thread gravadora;

    /** Protege o estado e todos os contadores. Nunca envolve E/S. */
    private final Object trava = new Object();
    private boolean aberta = true;
    private boolean fechada;
    private boolean abandonada;
    private boolean emCurso;
    private boolean emCursoFim;
    private int naFila;
    private long linhas;
    private long errosEscrita;
    private long descartadas;
    private long transbordadas;
    private long abandonadas;

    /** Itens postos na fila e ainda nao terminados pela gravadora (para {@link #aguardarGravacao}). */
    private final Object travaPendentes = new Object();
    private long pendentes;

    private TrilhaJsonl(Path arquivo, Writer saida, int capacidade) {
        if (capacidade < 1) {
            throw new IllegalArgumentException("capacidade da fila precisa ser >= 1: " + capacidade);
        }
        this.arquivo = Objects.requireNonNull(arquivo, "arquivo");
        this.saida = Objects.requireNonNull(saida, "saida");
        this.capacidade = capacidade;
        Path nome = arquivo.getFileName();
        this.gravadora = new Thread(this::gravar, PREFIXO_THREAD + (nome == null ? arquivo : nome));
        this.gravadora.setDaemon(true);
        this.gravadora.start();
    }

    /**
     * Cria (ou trunca) {@code arquivo}, criando os diretorios, com a fila de {@code capacidade} linhas (use
     * {@link #capacidadePara}).
     */
    public static TrilhaJsonl abrir(Path arquivo, int capacidade) throws IOException {
        if (capacidade < 1) {
            throw new IllegalArgumentException("capacidade da fila precisa ser >= 1: " + capacidade);
        }
        Path pai = arquivo.toAbsolutePath().getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        return new TrilhaJsonl(arquivo, Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8), capacidade);
    }

    /**
     * Como {@link #abrir(Path, int)}, com a fila sem limite pratico ({@link Integer#MAX_VALUE} linhas). Para testes
     * e ferramentas; o controlador ({@code LigacaoControlador}) usa a capacidade calculada.
     */
    public static TrilhaJsonl abrir(Path arquivo) throws IOException {
        return abrir(arquivo, Integer.MAX_VALUE);
    }

    /** Testes: trilha sobre uma saida dada ({@code arquivo} so da o nome da gravadora). */
    static TrilhaJsonl sobre(Path arquivo, Writer saida, int capacidade) {
        return new TrilhaJsonl(arquivo, saida, capacidade);
    }

    /**
     * Capacidade da fila para {@code linhasEsperadas} na execucao: {@value #FATOR_CAPACIDADE} x
     * {@code linhasEsperadas}.
     *
     * @throws IllegalArgumentException {@code linhasEsperadas <= 0} ou capacidade que nao cabe em {@code int}
     */
    public static int capacidadePara(long linhasEsperadas) {
        if (linhasEsperadas <= 0) {
            throw new IllegalArgumentException("linhas esperadas precisam ser > 0: " + linhasEsperadas);
        }
        long c = (long) FATOR_CAPACIDADE * linhasEsperadas;
        if (c > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("capacidade " + c + " nao cabe em int (linhas esperadas "
                    + linhasEsperadas + ")");
        }
        return (int) c;
    }

    /** Poe a linha na fila e volta; nunca espera o disco. Fila cheia: transbordada; depois do fim: descartada. */
    public void escrever(ObjectNode linha) {
        String json;
        try {
            json = JSON.writeValueAsString(linha);
        } catch (IOException | RuntimeException e) {
            synchronized (trava) {
                errosEscrita++;
            }
            return;
        }
        synchronized (trava) {
            if (!aberta) {
                descartadas++;
                return;
            }
            if (naFila >= capacidade) {
                transbordadas++;
                return;
            }
            naFila++;
            enfileirar(new Item(json, null, null, 0));
        }
    }

    /**
     * Ultima linha: entra fora da capacidade, fecha a trilha para linhas novas (as que chegarem depois sao
     * descartadas e nao entram nela) e, na hora da gravacao, recebe em {@code campoPerdidas} as linhas que nao
     * chegaram ao arquivo ate ali: erros de escrita das linhas anteriores + transbordadas + descartadas antes do
     * pedido. {@code linha} nao deve ser mexida por quem chama depois desta chamada.
     *
     * @return {@code false} (e a linha conta como descartada) se a trilha ja tinha fim ou estava fechada
     */
    public boolean escreverFim(ObjectNode linha, String campoPerdidas) {
        Objects.requireNonNull(linha, "linha");
        Objects.requireNonNull(campoPerdidas, "campoPerdidas");
        synchronized (trava) {
            if (!aberta) {
                descartadas++;
                return false;
            }
            aberta = false;
            enfileirar(new Item(null, linha, campoPerdidas, descartadas));
            return true;
        }
    }

    /** Chamado sob {@code trava}. */
    private void enfileirar(Item item) {
        synchronized (travaPendentes) {
            pendentes++;
        }
        fila.add(item);
    }

    /**
     * Espera a gravadora terminar tudo o que ja esta na fila (gancho da JVM, testes).
     *
     * @return {@code true} se terminou no prazo; {@code false} no prazo estourado ou na interrupcao (o sinal de
     *         interrupcao fica na thread)
     */
    public boolean aguardarGravacao(long prazoNs) {
        long limite = System.nanoTime() + prazoNs;
        synchronized (travaPendentes) {
            while (pendentes > 0) {
                long resta = limite - System.nanoTime();
                if (resta <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(travaPendentes, resta);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ gravadora

    private void gravar() {
        while (true) {
            Item it;
            try {
                it = fila.take();
            } catch (InterruptedException e) {
                continue; // ninguem interrompe a gravadora; a parada e pelo item PARAR ou pelo fim
            }
            if (it == Item.PARAR) {
                fecharSaida();
                concluir(1);
                return;
            }
            String json;
            synchronized (trava) {
                if (it.fim() == null) {
                    naFila--;
                }
                if (abandonada) {
                    abandonadas++; // tirado da fila depois do estouro e antes de comecar: nao grava, conta
                    concluir(1);
                    continue;
                }
                emCurso = true;
                emCursoFim = it.fim() != null;
                if (it.fim() != null) {
                    it.fim().put(it.campoPerdidas(), errosEscrita + transbordadas + it.descartadasNoPedido());
                }
            }
            boolean ok;
            try {
                json = it.fim() == null ? it.json() : JSON.writeValueAsString(it.fim());
                saida.write(json);
                saida.write('\n');
                saida.flush();
                ok = true;
            } catch (IOException | RuntimeException e) {
                ok = false;
            }
            synchronized (trava) {
                emCurso = false;
                emCursoFim = false;
                if (!abandonada) { // abandonada no meio: a linha ja foi contada em abandonadas
                    if (ok) {
                        linhas++;
                    } else {
                        errosEscrita++;
                    }
                }
            }
            if (it.fim() != null) {
                fecharSaida();
                concluir(1);
                return;
            }
            concluir(1);
        }
    }

    private void fecharSaida() {
        try {
            saida.close();
        } catch (IOException | RuntimeException e) {
            synchronized (trava) {
                if (!abandonada) {
                    errosEscrita++;
                }
            }
        }
    }

    private void concluir(int n) {
        synchronized (travaPendentes) {
            pendentes -= n;
            travaPendentes.notifyAll();
        }
    }

    // ------------------------------------------------------------------ leitura

    public Path arquivo() {
        return arquivo;
    }

    /** Linhas da fila (sem a {@code fim}). */
    public int capacidade() {
        return capacidade;
    }

    /** Linhas gravadas no arquivo. */
    public long linhas() {
        synchronized (trava) {
            return linhas;
        }
    }

    public long errosEscrita() {
        synchronized (trava) {
            return errosEscrita;
        }
    }

    /** Linhas que chegaram depois do fim ou do fechamento. */
    public long descartadas() {
        synchronized (trava) {
            return descartadas;
        }
    }

    /** Linhas recusadas com a fila cheia (W-2b). */
    public long transbordadas() {
        synchronized (trava) {
            return transbordadas;
        }
    }

    /** Linhas nao gravadas porque o fechamento com prazo estourou (decisao 5 do coordenador, W-2b). */
    public long abandonadas() {
        synchronized (trava) {
            return abandonadas;
        }
    }

    /** Linhas que nao chegaram ao arquivo: erros de escrita + transbordadas + descartadas + abandonadas. */
    public long linhasPerdidas() {
        synchronized (trava) {
            return errosEscrita + transbordadas + descartadas + abandonadas;
        }
    }

    /**
     * Fecha a trilha para linhas novas e espera a gravadora terminar a fila (inclusive o {@code fim}, se pedido) e
     * fechar o arquivo, ate {@code prazoNs}. Se o prazo estourar, abandona o que falta (ver a classe) e volta.
     * Idempotente. Interrompido, para de esperar, abandona e repoe o sinal.
     *
     * @return {@code true} se a gravadora terminou no prazo
     */
    public boolean fechar(long prazoNs) {
        pedirFechamento();
        try {
            long ns = Math.max(1L, prazoNs);
            gravadora.join(ns / 1_000_000L, (int) (ns % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean terminou = !gravadora.isAlive();
        if (!terminou) {
            abandonar();
        }
        return terminou;
    }

    /**
     * Como {@link #fechar(long)}, sem prazo: espera a gravadora terminar. Interrompido, para de esperar e repoe o
     * sinal.
     */
    @Override
    public void close() {
        pedirFechamento();
        try {
            gravadora.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pedirFechamento() {
        synchronized (trava) {
            if (!fechada) {
                fechada = true;
                if (aberta) {
                    aberta = false;
                    enfileirar(Item.PARAR);
                }
            }
        }
    }

    /** Estouro do prazo: conta o que nao foi gravado e deixa so a parada na fila. */
    private void abandonar() {
        synchronized (trava) {
            if (abandonada || !gravadora.isAlive()) {
                return;
            }
            abandonada = true;
            List<Item> resto = new ArrayList<>();
            fila.drainTo(resto);
            long n = 0;
            for (Item x : resto) {
                if (x != Item.PARAR) {
                    n++;
                }
            }
            naFila = 0;
            abandonadas += n + (emCurso ? 1 : 0);
            concluir(resto.size());
            if (!(emCurso && emCursoFim)) {
                enfileirar(Item.PARAR); // a gravadora fecha o arquivo quando voltar (com o fim em curso, fecha sozinha)
            }
        }
    }
}
