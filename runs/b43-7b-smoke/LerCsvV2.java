import br.unipampa.tcc.controle.EventoOperacao;
import br.unipampa.tcc.controle.LeitorCsvEventos;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** B-43.7b (copia do LerCsvV2 de B-43.8, que copiou o de B-43.7): le cada CSV inteiro com LeitorCsvEventos (jar da propria base) e imprime JSON. */
public class LerCsvV2 {
    public static void main(String[] args) throws Exception {
        StringBuilder sb = new StringBuilder("{\n");
        int falhas = 0;
        for (int i = 0; i < args.length; i++) {
            Map<String, Long> porCodigo = new TreeMap<>();
            long[] erros = new long[1];
            String status;
            long n = -1;
            try (BufferedReader r = Files.newBufferedReader(Path.of(args[i]), StandardCharsets.UTF_8)) {
                n = LeitorCsvEventos.ler(r, (EventoOperacao e) -> {
                    porCodigo.merge(e.codigoRetorno(), 1L, Long::sum);
                    if (e.erro()) erros[0]++;
                });
                status = "OK";
            } catch (Exception ex) {
                status = "FALHA: " + ex.getMessage().replace("\"", "'");
                falhas++;
            }
            sb.append(" \"").append(args[i]).append("\": {\"status\": \"").append(status)
              .append("\", \"eventos\": ").append(n).append(", \"erros\": ").append(erros[0])
              .append(", \"por_codigo\": {");
            int k = 0;
            for (var en : porCodigo.entrySet()) {
                sb.append(k++ == 0 ? "" : ", ").append('"').append(en.getKey()).append("\": ").append(en.getValue());
            }
            sb.append("}}").append(i + 1 < args.length ? ",\n" : "\n");
        }
        System.out.print(sb.append("}\n"));
        System.exit(falhas == 0 ? 0 : 1);
    }
}
