package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), P-38: os parametros da politica entram no {@code controlador-*.json} como o bloco opcional
 * {@code "politica"} (decisao do Gestor de 05/11), cobertos pelo SHA-256 do arquivo; a linha {@code meta} do
 * JSONL de decisoes (esquema 1.2) os repete em {@code parametros_politica}; a sonda expoe os nos que
 * responderam na ultima amostra; o {@link AtuadorPorTipo#bracoB} encaminha AC-2 e AC-5.
 */
class ParametrosPoliticaArquivoTest {

    static final long S = 1_000_000_000L;
    static final ObjectMapper JSON = new ObjectMapper();

    /** Bloco de teste: k e tetoLagMs com origem "teste" (os reais vem da calibracao, D-12). */
    public static final String BLOCO_TESTE = "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,"
            + "\"cotaAc5\":1,\"origem\":{\"k\":\"teste\",\"tetoLagMs\":\"teste\","
            + "\"tRebNs\":\"state-transfer.timeout do XML (60000 ms), acima da deteccao medida de 46,8 s\","
            + "\"cotaAc5\":\"desenho: uma vez por execucao\"}}";

    static final String BASE = "{\"versao\":\"teste-w2a\",\"wNs\":5000000000,\"nLat\":100,\"nErr\":50,"
            + "\"thetaLatIn\":0.05,\"thetaLatOut\":0.02,\"thetaErrIn\":0.02,\"thetaErrOut\":0.005,"
            + "\"k\":2,\"m\":6,\"cooldownNs\":30000000000,\"estavelNs\":30000000000,\"holdViolNs\":120000000000";

    @TempDir
    Path dir;

    Path arquivo(String conteudo) throws IOException {
        Path f = Files.createTempFile(dir, "controlador-", ".json");
        Files.writeString(f, conteudo, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("bloco politica presente: lido com a origem de cada campo; ausente: politica() = null")
    void blocoPresenteEAusente() throws IOException {
        ParametrosControlador com = ParametrosControlador.deJson(arquivo(BASE + "," + BLOCO_TESTE + "}"));
        ParametrosPolitica p = com.politica();
        assertEquals(2, p.k());
        assertEquals(2000, p.tetoLagMs());
        assertEquals(60 * S, p.tRebNs());
        assertEquals(1, p.cotaAc5());
        assertEquals("teste", p.origem().get("k"));
        assertEquals("teste", p.origem().get("tetoLagMs"));
        assertEquals(List.of("k", "tetoLagMs", "tRebNs", "cotaAc5"), List.copyOf(p.origem().keySet()));
        ParametrosControlador sem = ParametrosControlador.deJson(arquivo(BASE + "}"));
        assertNull(sem.politica());
        assertNotEquals(com.sha256(), sem.sha256(), "o SHA-256 do arquivo cobre o bloco");
        assertNull(ParametrosControlador.padrao().politica());
        ParametrosControlador d = ParametrosControlador.padrao();
        assertNull(new ParametrosControlador("x", d.wNs(), d.nLat(), d.nErr(), d.thetaLatIn(), d.thetaLatOut(),
                d.thetaErrIn(), d.thetaErrOut(), d.k(), d.m(), d.cooldownNs(), d.estavelNs(), d.holdViolNs(), null)
                .politica(), "construtor de antes do W-2a: sem politica");
    }

    @Test
    @DisplayName("bloco politica invalido e recusado: sem valor padrao, origem obrigatoria, campos estritos")
    void blocoInvalido() throws IOException {
        String[] ruins = {
                "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1}",
                "\"politica\":{\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"origem\":{\"tetoLagMs\":\"t\",\"tRebNs\":\"t\",\"cotaAc5\":\"t\"}}",
                "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"origem\":{\"k\":\"t\",\"tetoLagMs\":\"t\",\"tRebNs\":\"t\"}}",
                "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"origem\":{\"k\":\" \",\"tetoLagMs\":\"t\",\"tRebNs\":\"t\",\"cotaAc5\":\"t\"}}",
                "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"origem\":{\"k\":3,\"tetoLagMs\":\"t\",\"tRebNs\":\"t\",\"cotaAc5\":\"t\"}}",
                "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":2,\"origem\":{\"k\":\"t\",\"tetoLagMs\":\"t\",\"tRebNs\":\"t\",\"cotaAc5\":\"t\"}}",
                "\"politica\":{\"k\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"extra\":1,\"origem\":{\"k\":\"t\",\"tetoLagMs\":\"t\",\"tRebNs\":\"t\",\"cotaAc5\":\"t\"}}",
                "\"politica\":[]",
                "\"politica\":null"};
        for (String r : ruins) {
            Path f = arquivo(BASE + "," + r + "}");
            assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(f), r);
        }
        Path desconhecido = arquivo(BASE + ",\"politicaa\":{}}");
        assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(desconhecido),
                "campo desconhecido continua recusado");
    }

    @Test
    @DisplayName("meta: decisoes 1.2 traz parametros_politica (null sem bloco); observaveis 1.1 nao muda")
    void metaComParametrosDaPolitica() throws IOException {
        ParametrosControlador com = ParametrosControlador.deJson(arquivo(BASE + "," + BLOCO_TESTE + "}"));
        MetaExecucao m = new MetaExecucao("v", 1, ModoAtuacao.SHADOW, "S3", "async", 3, com, null, null, 1, 1, 0,
                "por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)", "por-assinatura", true, false);
        JsonNode d = FormatoJsonl.meta(FormatoJsonl.ESQUEMA_DECISOES, m);
        JsonNode pp = d.get("parametros_politica");
        assertEquals(2, pp.get("k").asInt());
        assertEquals(2000, pp.get("teto_lag_ms").asLong());
        assertEquals(60 * S, pp.get("t_reb_ns").asLong());
        assertEquals(1, pp.get("cota_ac5").asInt());
        assertEquals("teste", pp.get("origem").get("k").asText());
        JsonNode o = FormatoJsonl.meta(FormatoJsonl.ESQUEMA_OBSERVAVEIS, m);
        assertFalse(o.has("parametros_politica"), "observaveis 1.1 sem campo novo");
        MetaExecucao semPol = new MetaExecucao("v", 1, ModoAtuacao.SHADOW, "S3", "async", 3,
                ParametrosControlador.padrao(), null, null, 1, 1, 0, "nulo", "nenhuma", true, false);
        JsonNode d2 = FormatoJsonl.meta(FormatoJsonl.ESQUEMA_DECISOES, semPol);
        assertTrue(d2.has("parametros_politica") && d2.get("parametros_politica").isNull(), "nunca omitido");
    }

    @Test
    @DisplayName("sonda: respondidosNaUltimaAmostra = rotulos que responderam na rodada mais recente (vazio antes)")
    void respondidosNaUltimaAmostra() {
        AtomicLong t = new AtomicLong(S);
        boolean[] cai = {false};
        FonteDeSaude f = SondaTopologiaRodadaTest.fonte(3, (i, p) -> {
            if (i == 2 && cai[0]) {
                throw new IOException("recusada");
            }
            return SondaTopologiaRodadaTest.saudavel(3);
        });
        try (SondaTopologia s = SondaTopologiaRodadaTest.sonda(f, t)) {
            assertEquals(Set.of(), s.respondidosNaUltimaAmostra());
            SondaTopologiaRodadaTest.rodadaEm(s, t, S);
            assertEquals(Set.of("no0", "no1", "no2"), s.respondidosNaUltimaAmostra());
            cai[0] = true;
            SondaTopologiaRodadaTest.rodadaEm(s, t, 2 * S);
            assertEquals(Set.of("no0", "no1"), s.respondidosNaUltimaAmostra());
            assertThrows(UnsupportedOperationException.class, () -> s.respondidosNaUltimaAmostra().add("x"));
        }
    }

    @Test
    @DisplayName("AtuadorPorTipo.bracoB: AC-2 e AC-5 vao ao mesmo adaptador; nome estavel para o dry-run")
    void bracoB() throws Exception {
        AtuadorFalso rest = new AtuadorFalso();
        AtuadorPorTipo a = AtuadorPorTipo.bracoB(rest);
        assertEquals("por-tipo(AC-2=falso,AC-5=falso)", a.nome());
        assertEquals(AtuadorPorTipo.nomeBracoB("falso"), a.nome());
        a.aplicar(AcaoClusterTiposTest.AC2, S);
        a.aplicar(AcaoClusterTiposTest.AC5_ISN3, 5 * S);
        assertEquals(List.of("AC-2", "AC-5"), rest.chamadas().stream().map(c -> c.acao().tipo().rotulo()).toList());
    }

    @Test
    @DisplayName("ParametrosPolitica.deJson exige a origem; construtor de codigo aceita origem vazia")
    void origemNoBloco() throws Exception {
        ObjectNode n = (ObjectNode) JSON.readTree("{" + BLOCO_TESTE + "}").get("politica");
        ParametrosPolitica p = ParametrosPolitica.deJson(n);
        assertEquals(Map.of("k", "teste", "tetoLagMs", "teste",
                "tRebNs", "state-transfer.timeout do XML (60000 ms), acima da deteccao medida de 46,8 s",
                "cotaAc5", "desenho: uma vez por execucao"), p.origem());
        assertEquals(Map.of(), new ParametrosPolitica(2, 2000, 60 * S, 1).origem());
        n.remove("origem");
        assertThrows(IllegalArgumentException.class, () -> ParametrosPolitica.deJson(n));
    }
}
