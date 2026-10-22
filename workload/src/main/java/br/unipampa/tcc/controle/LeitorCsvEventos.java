package br.unipampa.tcc.controle;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Leitura em streaming do CSV de eventos ({@code op_id,operation,start_ns,end_ns,replica,
 * return_code,key}, opcionalmente com {@code phase} ao fim) para {@link EventoOperacao} (B-26.1).
 * As colunas sao achadas pelo nome no cabecalho; colunas extras sao ignoradas. Nenhum campo do
 * contrato tem virgula, entao a separacao e por virgula simples. Linha em branco e ignorada; linha
 * com numero de campos diferente do cabecalho e rejeitada com o numero da linha.
 *
 * <p>Serve de base ao replay (B-38), que le os {@code rep-NNN.csv} de ~60 MB sem carrega-los.
 */
public final class LeitorCsvEventos {

    static final List<String> COLUNAS = List.of("operation", "start_ns", "end_ns", "return_code");

    private LeitorCsvEventos() {
    }

    /**
     * Le todas as linhas e entrega cada evento, na ordem do arquivo, a {@code destino}.
     *
     * @return numero de eventos entregues
     * @throws IllegalArgumentException cabecalho sem as colunas do contrato ou linha invalida
     */
    public static long ler(BufferedReader entrada, Consumer<EventoOperacao> destino) throws IOException {
        Objects.requireNonNull(entrada, "entrada");
        Objects.requireNonNull(destino, "destino");
        String cabecalho = entrada.readLine();
        if (cabecalho == null) {
            throw new IllegalArgumentException("CSV vazio: falta o cabecalho");
        }
        List<String> nomes = List.of(cabecalho.strip().split(",", -1));
        int[] idx = new int[COLUNAS.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = nomes.indexOf(COLUNAS.get(i));
            if (idx[i] < 0) {
                throw new IllegalArgumentException("cabecalho sem a coluna " + COLUNAS.get(i) + ": " + cabecalho);
            }
        }
        long n = 0;
        long linha = 1;
        String s;
        while ((s = entrada.readLine()) != null) {
            linha++;
            if (s.isBlank()) {
                continue;
            }
            String[] c = s.strip().split(",", -1);
            if (c.length != nomes.size()) {
                throw new IllegalArgumentException("linha " + linha + ": " + c.length + " campos, esperado "
                        + nomes.size());
            }
            EventoOperacao e;
            try {
                e = new EventoOperacao(c[idx[0]], Long.parseLong(c[idx[1]]), Long.parseLong(c[idx[2]]), c[idx[3]]);
            } catch (IllegalArgumentException ex) { // inclui NumberFormatException
                throw new IllegalArgumentException("linha " + linha + ": " + ex.getMessage(), ex);
            }
            destino.accept(e);
            n++;
        }
        return n;
    }
}
