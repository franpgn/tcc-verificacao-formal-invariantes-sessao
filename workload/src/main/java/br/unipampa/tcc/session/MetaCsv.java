package br.unipampa.tcc.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * B-43.4: identifica a versao do contrato da coluna {@code return_code} do CSV de eventos.
 *
 * <p>Imprime {@link #linha()} no stdout e grava {@code meta-csv-rep-NNN.json} ao lado do
 * {@code rep-NNN.csv}, com {@code {"contrato_csv": 2}}. CSV sem esse arquivo e do contrato 1
 * ({@code OK_VALIDATE} sempre {@code VALID}, {@code NOT_FOUND} gravado como {@code NONE}). Ver
 * {@code docs/contrato-csv-v2.md}.</p>
 */
final class MetaCsv {

    private MetaCsv() { /* utilitario */ }

    /** Linha do stdout: {@code Contrato CSV: v2}. */
    static String linha() {
        return "Contrato CSV: v" + OpResult.VERSAO_CONTRATO_CSV;
    }

    /**
     * Grava {@code meta-csv-rep-NNN.json} em {@code csvDir}.
     *
     * @return o caminho gravado, ou {@code null} sem {@code --csv-dir}
     */
    static Path gravar(String csvDir, int rep) throws IOException {
        if (csvDir == null || csvDir.isBlank()) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contrato_csv", OpResult.VERSAO_CONTRATO_CSV);
        Path destino = Path.of(csvDir, String.format("meta-csv-rep-%03d.json", rep));
        Files.createDirectories(destino.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(destino.toFile(), m);
        return destino;
    }
}
