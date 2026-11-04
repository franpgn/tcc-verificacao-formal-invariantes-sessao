package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * B-27.3 (W-1, R-C1): o controlador do TCC age no <i>cluster</i>, nao no cliente (decisao do autor de
 * 2026-11-04). Nenhuma classe de {@code session} usa um tipo de {@code controle} para agir sobre a
 * emissao de operacoes: o pacote {@code session} so pode usar, de {@code controle}, os tipos dos
 * sensores do B-26 (saude REST e sonda de topologia). A lista de classes de {@code session} que usam
 * {@code controle} para o laco de controle e vazia nesta branch; o W-3 a amplia, se precisar.
 *
 * <p>Os tipos do antigo portao de emissao (K1..K4) nao existem mais em {@code controle/}.</p>
 */
class ArquiteturaSemAcaoNoClienteTest {

    /** Tipos de {@code controle} que {@code session} pode usar: so sensores (B-26.1/B-26.2). */
    static final Set<String> SENSORES_PERMITIDOS = Set.of(
            "FonteDeSaude", "LeituraSaude",                                     // ConsultaSaudeRest
            "AmostraTopologia", "FonteDeTempo", "ResultadoNo", "SondaTopologia"); // SondaTopologiaCli

    /** Classes de {@code session} autorizadas a usar {@code controle} para o laco (W-3). Vazia aqui. */
    static final Set<String> LACO_AUTORIZADO = Set.of();

    /** Tipos do portao de emissao retirados no B-27.3. */
    static final List<String> TIPOS_RETIRADOS = List.of(
            "PortaoDeEmissao", "ParametrosKnobs", "EventoK3", "Dormidor", "PerfilAtual");

    private static final Pattern USO_CONTROLE = Pattern.compile("br\\.unipampa\\.tcc\\.controle\\.(\\*|[A-Za-z_]\\w*)");

    private static Path dir(String pacote) {
        return NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/" + pacote);
    }

    @Test
    @DisplayName("R-C1: session so usa de controle os tipos dos sensores (B-26); nenhum tipo do portao K1..K4 existe")
    void sessionNaoAgeNoCliente() throws IOException {
        TreeMap<String, Set<String>> indevidos = new TreeMap<>();
        try (Stream<Path> fontes = Files.list(dir("session"))) {
            for (Path f : (Iterable<Path>) fontes.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String classe = f.getFileName().toString().replace(".java", "");
                if (LACO_AUTORIZADO.contains(classe)) {
                    continue;
                }
                String limpo = DetectorFonte.semComentarios(
                        Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
                Matcher m = USO_CONTROLE.matcher(limpo);
                Set<String> fora = new TreeSet<>();
                while (m.find()) {
                    if (!SENSORES_PERMITIDOS.contains(m.group(1))) {
                        fora.add(m.group(1));
                    }
                }
                if (!fora.isEmpty()) {
                    indevidos.put(classe, fora);
                }
            }
        }
        assertEquals(new TreeMap<>(), indevidos, "classes de session usando controle fora dos sensores");
        for (String tipo : TIPOS_RETIRADOS) {
            assertFalse(Files.exists(dir("controle").resolve(tipo + ".java")), "tipo retirado ainda existe: " + tipo);
        }
    }
}
