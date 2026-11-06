package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * sensores do B-26 (saude REST e sonda de topologia) e, na ligacao do laco de controle (W-3), os tipos
 * do laco. Desde o W-3 a lista e por pares (classe de {@code session}, tipo de {@code controle}), ressalva
 * 4 da validacao do W-1: uma classe nova de {@code session} que use qualquer tipo de {@code controle} e
 * reprovada, e uma classe listada so pode usar os seus tipos.
 *
 * <p>Os tipos do antigo portao de emissao (K1..K4) nao existem mais em {@code controle/}.</p>
 */
class ArquiteturaSemAcaoNoClienteTest {

    /**
     * Pares permitidos: classe de {@code session} e os tipos de {@code controle} que ela pode usar. Os
     * sensores do B-26 ({@code ConsultaSaudeRest}, {@code SondaTopologiaCli}) e a ligacao do laco
     * ({@code LigacaoControlador}, W-3). Nenhum tipo de acao sobre o cliente existe em {@code controle}.
     */
    static final Map<String, Set<String>> PERMITIDOS = Map.of(
            "ConsultaSaudeRest", Set.of("FonteDeSaude", "LeituraSaude"),
            // W-2a (B-44.2, D-C): o atuador REST do cluster so usa os tipos da interface do atuador
            "AtuadorRestInfinispan", Set.of("AcaoCluster", "AtuadorCluster", "ResultadoAtuacao"),
            // P1-IC (06/11, G-1): o adaptador do AC-7 (ignored-caches) usa so os mesmos tipos do atuador
            "AtuadorIgnorarCache", Set.of("AcaoCluster", "AtuadorCluster", "ResultadoAtuacao"),
            "SondaTopologiaCli", Set.of("AmostraTopologia", "FonteDeTempo", "ResultadoNo", "SondaTopologia"),
            // W-4 (B-26.3): o adaptador Hot Rod do S_lag so implementa o canal da sonda
            "CanalCanarioHotRod", Set.of("CanalCanario"),
            "LigacaoControlador", Set.of("AgregadorDeJanela", "ArquivoReferencia", "AtuadorCluster", "AtuadorNulo",
                    "ClasseViolacao", "Controlador", "EventoOperacao", "ExecutorDeAcoes", "FonteDeDefasagem",
                    "FonteDeSaude", "FonteDeTempo", "FonteDeTopologia", "FonteDeViolacoes", "FormatoJsonl",
                    "LacoDeControle", "MetaExecucao", "ModoAtuacao", "ParametrosControlador", "PoliticaDeAcao",
                    "Referencia", "SondaTopologia", "TrilhaJsonl", "ViolacaoObservada",
                    // W-2a (P-38, autorizado em 05/11): ligacao do atuador REST e da politica por assinatura
                    "PoliticaPorAssinatura", "ParametrosPolitica", "AtuadorPorTipo",
                    // W-4 (B-26.3): ligacao da sonda de defasagem (S_lag) ao laco
                    "CanalCanario", "ParametrosDefasagem", "SondaDefasagem",
                    // P1-IC (G-4): o desconto do no sob o AC-7 le o tipo e o alvo das acoes ativas
                    "AcaoCluster"));

    /** Tipos do portao de emissao retirados no B-27.3. */
    static final List<String> TIPOS_RETIRADOS = List.of(
            "PortaoDeEmissao", "ParametrosKnobs", "EventoK3", "Dormidor", "PerfilAtual");

    private static final Pattern USO_CONTROLE = Pattern.compile("br\\.unipampa\\.tcc\\.controle\\.(\\*|[A-Za-z_]\\w*)");

    private static Path dir(String pacote) {
        return NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/" + pacote);
    }

    @Test
    @DisplayName("R-C1: session so usa de controle os pares (classe, tipo) permitidos; nenhum tipo do portao K1..K4 existe")
    void sessionNaoAgeNoCliente() throws IOException {
        TreeMap<String, Set<String>> indevidos = new TreeMap<>();
        try (Stream<Path> fontes = Files.list(dir("session"))) {
            for (Path f : (Iterable<Path>) fontes.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String classe = f.getFileName().toString().replace(".java", "");
                Set<String> permitidos = PERMITIDOS.getOrDefault(classe, Set.of());
                String limpo = DetectorFonte.semComentarios(
                        Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
                Matcher m = USO_CONTROLE.matcher(limpo);
                Set<String> fora = new TreeSet<>();
                while (m.find()) {
                    if (!permitidos.contains(m.group(1))) {
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
