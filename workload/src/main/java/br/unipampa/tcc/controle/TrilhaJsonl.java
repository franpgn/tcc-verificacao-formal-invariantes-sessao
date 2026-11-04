package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * W-3 (B-28.2): arquivo JSONL do controlador ({@code observaveis-rep-NNN.jsonl} ou
 * {@code decisoes-rep-NNN.jsonl}). Uma linha por chamada, terminada em {@code \n}, com flush a cada
 * linha (uma queda do processo perde no maximo a linha em curso). Thread-safe. Uma falha de escrita
 * nunca chega a quem chamou (a carga nao pode morrer pela trilha): e contada em {@link #errosEscrita()}.
 * Depois de {@link #close()} as linhas sao descartadas e contadas em {@link #descartadas()}.
 */
public final class TrilhaJsonl implements Closeable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path arquivo;
    private BufferedWriter saida;
    private long linhas;
    private long errosEscrita;
    private long descartadas;

    private TrilhaJsonl(Path arquivo, BufferedWriter saida) {
        this.arquivo = arquivo;
        this.saida = saida;
    }

    /** Cria (ou trunca) {@code arquivo}, criando os diretorios. */
    public static TrilhaJsonl abrir(Path arquivo) throws IOException {
        Path pai = arquivo.toAbsolutePath().getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        return new TrilhaJsonl(arquivo, Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8));
    }

    public synchronized void escrever(ObjectNode linha) {
        if (saida == null) {
            descartadas++;
            return;
        }
        try {
            saida.write(JSON.writeValueAsString(linha));
            saida.write('\n');
            saida.flush();
            linhas++;
        } catch (IOException e) {
            errosEscrita++;
        }
    }

    public Path arquivo() {
        return arquivo;
    }

    public synchronized long linhas() {
        return linhas;
    }

    public synchronized long errosEscrita() {
        return errosEscrita;
    }

    public synchronized long descartadas() {
        return descartadas;
    }

    @Override
    public synchronized void close() {
        if (saida == null) {
            return;
        }
        try {
            saida.close();
        } catch (IOException e) {
            errosEscrita++;
        }
        saida = null;
    }
}
