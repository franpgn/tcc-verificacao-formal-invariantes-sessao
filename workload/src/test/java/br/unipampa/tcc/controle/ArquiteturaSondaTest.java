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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T15): a parte pura da sonda de topologia em {@code controle} nao importa
 * {@code org.infinispan} nem {@code br.unipampa.tcc.session}. Reaproveita o detector de
 * {@link ArquiteturaControleTest} (que ja varre todo o pacote) e exige que as cinco fontes existam,
 * para o teste nao passar por vacuidade.
 */
class ArquiteturaSondaTest {

    private static final List<String> TIPOS_DA_SONDA = List.of("SondaTopologia", "FonteDeSaude", "LeituraSaude",
            "ResultadoNo", "AmostraTopologia");

    @Test
    @DisplayName("T15: as cinco fontes da sonda existem em controle/ e nao tem import proibido")
    void sondaPura() throws IOException {
        String base = System.getProperty("basedir", System.getProperty("user.dir"));
        Path dir = Paths.get(base, "src", "main", "java", "br", "unipampa", "tcc", "controle");
        List<String> achados = new ArrayList<>();
        for (String tipo : TIPOS_DA_SONDA) {
            Path f = dir.resolve(tipo + ".java");
            assertTrue(Files.isRegularFile(f), "fonte ausente: " + f.toAbsolutePath());
            for (String imp : ArquiteturaControleTest.importsProibidos(Files.readString(f, StandardCharsets.UTF_8))) {
                achados.add(tipo + ": " + imp);
            }
        }
        assertEquals(List.of(), achados, "a sonda em controle/ deve ser pura");
    }
}
