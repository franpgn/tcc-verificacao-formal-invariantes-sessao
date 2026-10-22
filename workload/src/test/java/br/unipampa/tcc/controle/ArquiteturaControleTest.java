package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-28.1: o nucleo {@code br.unipampa.tcc.controle} nao depende do cliente do data grid nem do
 * pacote do workload (plano de 2026-10-22, secao 3.1). Le os {@code import} das fontes.
 */
class ArquiteturaControleTest {

    /** Import (inclusive {@code static}) de pacote proibido. */
    private static final Pattern PROIBIDO = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?(org\\.infinispan|br\\.unipampa\\.tcc\\.session)(?:\\.|\\s*;)",
            Pattern.MULTILINE);

    private static final List<String> TIPOS_DO_NUCLEO = List.of("Janela", "EstadoTopologia", "Perfil", "Motivo",
            "Decisao", "ParametrosControlador", "Controlador",
            // B-26.1: agregador e observador de janela
            "Referencia", "EventoOperacao", "ClasseViolacao", "JanelaObservada", "AgregadorDeJanela",
            "LeitorCsvEventos");

    static List<String> importsProibidos(String fonte) {
        List<String> r = new ArrayList<>();
        Matcher m = PROIBIDO.matcher(fonte);
        while (m.find()) {
            r.add(m.group().trim());
        }
        return r;
    }

    private static Path dirFontes() {
        String base = System.getProperty("basedir", System.getProperty("user.dir"));
        return Paths.get(base, "src", "main", "java", "br", "unipampa", "tcc", "controle");
    }

    @Test
    @DisplayName("nenhuma fonte de controle importa org.infinispan ou br.unipampa.tcc.session")
    void semDependenciaDeInfinispanNemSession() throws IOException {
        Path dir = dirFontes();
        assertTrue(Files.isDirectory(dir), "diretorio de fontes nao encontrado: " + dir.toAbsolutePath());
        List<Path> fontes;
        try (Stream<Path> s = Files.walk(dir)) {
            fontes = s.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        // Nao vacuidade: o teste precisa estar lendo as fontes reais do nucleo.
        List<String> nomes = fontes.stream()
                .map(f -> f.getFileName().toString().replace(".java", "")).collect(Collectors.toList());
        assertTrue(nomes.containsAll(TIPOS_DO_NUCLEO), "fontes lidas: " + nomes);

        List<String> achados = new ArrayList<>();
        for (Path f : fontes) {
            for (String imp : importsProibidos(Files.readString(f, StandardCharsets.UTF_8))) {
                achados.add(f.getFileName() + ": " + imp);
            }
        }
        assertEquals(List.of(), achados, "o nucleo do controlador deve ser puro");
    }

    @Test
    @DisplayName("o detector reconhece imports proibidos e ignora os permitidos")
    void detector() {
        String fonte = String.join("\n",
                "package br.unipampa.tcc.controle;",
                "import java.util.List;",
                "import org.infinispan.client.hotrod.RemoteCache;",
                "  import static org.infinispan.commons.util.Util.close;",
                "import br.unipampa.tcc.session.SessionOps;",
                "import br.unipampa.tcc.session.*;",
                "import br.unipampa.tcc.sessionizer.Outro;",
                "import com.fasterxml.jackson.databind.ObjectMapper;",
                "// import org.infinispan.Comentado; (comentario de linha nao comeca com import)",
                "class X {}");
        assertEquals(List.of(
                "import org.infinispan.",
                "import static org.infinispan.",
                "import br.unipampa.tcc.session.",
                "import br.unipampa.tcc.session."), importsProibidos(fonte));
        assertEquals(List.of(), importsProibidos("import java.util.List;\nclass Y {}"));
    }
}
