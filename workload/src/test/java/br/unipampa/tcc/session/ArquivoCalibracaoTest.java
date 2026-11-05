package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.ParametrosControlador;
import br.unipampa.tcc.controle.ParametrosDefasagem;
import br.unipampa.tcc.controle.ParametrosPolitica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4, decisoes do coordenador de 05/11 (itens 2 e 4): o arquivo de parametros provisorio da calibracao
 * ({@code cluster/controlador/controlador-calibracao-s3.json}) declara {@code passoLeituraMs} = 20 ms com origem em
 * {@code wada_consistency_cloud_2011} secao 2 (leitores a 50 leituras por segundo) e {@code lagInMs}/{@code lagOutMs}
 * provisorios com {@code "origem": "provisorio-calibracao"} (no braco A so mudam as decisoes registradas; a calibracao
 * usa o lag bruto). O prazo de leitura e o {@code --hotrod-timeout-ms} padrao (2000 ms), igual a
 * {@code politica.tetoLagMs} (R7), e o custo sob falha e de ate prazo/passo = 100 leituras por par e rodada.
 */
class ArquivoCalibracaoTest {

    static Path arquivo() {
        return ClusterXmlIrmaosTest.localizarXml().getParent().resolve("controlador")
                .resolve("controlador-calibracao-s3.json");
    }

    @Test
    @DisplayName("o arquivo de calibracao carrega e declara a origem de passo, lagIn e lagOut")
    void origemDeclarada() throws Exception {
        assertTrue(Files.isRegularFile(arquivo()), "ausente: " + arquivo());
        ParametrosControlador p = ParametrosControlador.deJson(arquivo());
        assertTrue(p.versao().startsWith("provisorio-calibracao"), p.versao());
        ParametrosDefasagem d = p.defasagem();
        assertEquals(20, d.passoLeituraMs());
        assertTrue(d.origem().get("passoLeituraMs").contains("wada_consistency_cloud_2011"), d.origem().toString());
        assertEquals("provisorio-calibracao", d.origem().get("lagInMs"));
        assertEquals("provisorio-calibracao", d.origem().get("lagOutMs"));
        ParametrosPolitica q = p.politica();
        assertEquals("provisorio-calibracao", q.origem().get("kLag"));
        assertEquals(new S3Parametros().hotrodTimeoutMs, q.tetoLagMs(), "R7 com o --hotrod-timeout-ms padrao");
    }

    @Test
    @DisplayName("os valores cabem no prazo de leitura e em W; custo sob falha = prazo/passo = 100 leituras por par")
    void coerencia() throws Exception {
        ParametrosControlador p = ParametrosControlador.deJson(arquivo());
        long prazo = new S3Parametros().hotrodTimeoutMs;
        p.defasagem().validarContra(prazo, p.wNs());
        assertEquals(100, prazo / p.defasagem().passoLeituraMs());
        assertTrue(p.defasagem().lagInMs() <= prazo);
    }
}
