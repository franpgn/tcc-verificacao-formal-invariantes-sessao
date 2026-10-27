package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27, A1: os 5 tipos novos do portao existem em {@code controle/} e nao importam
 * {@code org.infinispan} nem {@code br.unipampa.tcc.session} (mesmo detector de
 * {@link ArquiteturaControleTest}).
 */
class ArquiteturaPortaoTest {

    static final List<String> TIPOS_DO_PORTAO = List.of("PerfilAtual", "ParametrosKnobs", "PortaoDeEmissao",
            "EventoK3", "Dormidor");

    @Test
    @DisplayName("A1: PerfilAtual, ParametrosKnobs, PortaoDeEmissao, EventoK3 e Dormidor existem e sao puros")
    void tiposDoPortaoPuros() throws Exception {
        String base = System.getProperty("basedir", System.getProperty("user.dir"));
        Path dir = Paths.get(base, "src", "main", "java", "br", "unipampa", "tcc", "controle");
        for (String tipo : TIPOS_DO_PORTAO) {
            Path f = dir.resolve(tipo + ".java");
            assertTrue(Files.isRegularFile(f), "ausente: " + f.toAbsolutePath());
            String fonte = Files.readString(f, StandardCharsets.UTF_8);
            assertTrue(fonte.contains("package br.unipampa.tcc.controle;"), tipo);
            assertEquals(List.of(), ArquiteturaControleTest.importsProibidos(fonte), tipo);
        }
    }
}
