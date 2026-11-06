package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-IC (06/11, decisoes G-1/G-2 do coordenador sobre o A4, {@code docs/relatorio-a4-2026-11-06.md}): o tipo
 * {@link AcaoCluster.Tipo#AC_7}, "ignorar o cache de sessoes no no suspeito" ({@code POST
 * /rest/v2/server/ignored-caches/{cache}} na REST dele), que substitui a parada (AC-5) como acao do P1.
 *
 * <ul>
 *   <li>Um no so como alvo e <b>um cache so</b> (R1 do relatorio: a lista de ignorados e uma chave so, gravada
 *       inteira, e o G-2 limita a acao ao {@code sessions-async}).</li>
 *   <li>Reversivel (o DELETE desfaz): entra na pilha do executor, e o R4, o encerramento e o gancho da JVM a
 *       desfazem.</li>
 *   <li>Prazo {@code T_cham + 15 s}: a reversao espera o POST anterior responder (R2, um pedido por vez), e sob
 *       corte o POST so responde depois do {@code remote-timeout} de 15 s do cache de estado (500, ISPN000476).</li>
 *   <li>O pior caso do {@code T_enc} do braco B nao muda (21 s com a P2, 19 s sem ela).</li>
 * </ul>
 */
class AcaoIgnorarCacheTest {

    static final long S = 1_000_000_000L;
    static final List<String> SESSOES = List.of("sessions-async");
    static final AcaoCluster AC7_ISN3 = new AcaoCluster(AcaoCluster.Tipo.AC_7, SESSOES, "isn3:11222", null);

    @Test
    @DisplayName("AC-7: um no, um cache, reversivel, prazo T_cham + 15 s, chave com o cache e o alvo")
    void tipo() {
        assertEquals("AC-7", AcaoCluster.Tipo.AC_7.rotulo());
        assertTrue(AcaoCluster.Tipo.AC_7.reversivel());
        assertEquals(AcaoCluster.RegraAlvo.UM_NO, AcaoCluster.Tipo.AC_7.alvo());
        assertEquals(17 * S, AcaoCluster.Tipo.AC_7.prazoNs(2 * S));
        assertEquals("AC-7|sessions-async|isn3:11222", AC7_ISN3.chave());
        assertThrows(IllegalArgumentException.class,
                () -> new AcaoCluster(AcaoCluster.Tipo.AC_7, List.of("sessions-async", "counters-async"), "isn3", null),
                "R1: um cache por acao");
        assertThrows(IllegalArgumentException.class, () -> new AcaoCluster(AcaoCluster.Tipo.AC_7, SESSOES, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AcaoCluster(AcaoCluster.Tipo.AC_7, SESSOES, "isn1,isn2", null));
        // os outros tipos continuam aceitando varios caches (leitura de volta e registro)
        new AcaoCluster(AcaoCluster.Tipo.AC_5, List.of("a", "b"), "isn3", null);
    }

    @Test
    @DisplayName("JSONL: AC-7 e reversivel, entao reversao = null; tipo AC-7 na linha acao")
    void jsonl() {
        RegistroAcao r = new RegistroAcao(RegistroAcao.Operacao.APLICAR, AC7_ISN3, ModoAtuacao.ACTIVE, "falso", true,
                "APLICADA", RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 5 * S, 6 * S, 6 * S, S, null,
                "alvo=sessions-async", null);
        var n = FormatoJsonl.acao(r);
        assertEquals("AC-7", n.get("acao").get("tipo").asText());
        assertTrue(n.get("reversao").isNull());
        assertEquals("sessions-async", n.get("acao").get("caches").get(0).asText());
    }

    @Test
    @DisplayName("AtuadorPorTipo.bracoB: AC-2 e AC-5 no REST, AC-7 no adaptador do ignored-caches; T_enc do pior caso igual")
    void bracoB() throws Exception {
        AtuadorFalso rest = new AtuadorFalso();
        AtuadorFalso ign = new AtuadorFalso();
        AtuadorPorTipo a = AtuadorPorTipo.bracoB(rest, ign);
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.aplicar(AC7_ISN3, S).estado());
        assertEquals(1, ign.chamadas().size());
        assertEquals(0, rest.chamadas().size());
        a.aplicar(AcaoClusterTiposTest.AC5_ISN3, S);
        assertEquals(1, rest.chamadas().size());
        assertEquals("por-tipo(AC-2=falso,AC-5=falso,AC-7=falso)", a.nome());
        assertEquals("por-tipo(AC-2=r,AC-5=r,AC-7=i)", AtuadorPorTipo.nomeBracoB("r", "i"));
        assertEquals(21 * S, AtuadorPorTipo.piorCasoEncerramentoBracoBNs(2 * S, true));
        assertEquals(19 * S, AtuadorPorTipo.piorCasoEncerramentoBracoBNs(2 * S, false));
    }

    // ------------------------------------------------------------------ executor

    final List<RegistroAcao> trilha = Collections.synchronizedList(new ArrayList<>());
    final AtomicLong relogio = new AtomicLong(100 * S);

    ExecutorDeAcoes executor(AtuadorCluster a) {
        return new ExecutorDeAcoes(ModoAtuacao.ACTIVE, a, 2 * S, relogio::get, trilha::add,
                new ExecutorDeAcoesTest.GanchoFalso());
    }

    @Test
    @DisplayName("executor: AC-7 aplicada entra na pilha e em ativas(); o R4 a desfaz; T_enc conta o prazo dela")
    void executorPilha() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(a);
        e.iniciar();
        e.aplicar(AC7_ISN3, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertEquals(List.of(AC7_ISN3), e.ativas());
        e.aplicar(AC7_ISN3, Decisao.Regra.R3, 10 * S);
        assertEquals(1, e.contadores().ignoradas(), "repetida enquanto ativa: ignorada");
        e.reverterTudo(RegistroAcao.Origem.R4, Decisao.Regra.R4, 15 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertEquals(List.of(), e.ativas());
        assertTrue(e.encerrar());
        assertEquals(List.of("aplicar", "reverter"), a.chamadas().stream().map(AtuadorFalso.Chamada::operacao).toList());
        assertEquals(null, e.alvoDoAc5Aplicado(), "o sinal de religamento e so do AC-5");
        assertEquals(17 * S, a.chamadas().get(0).prazoNs());
    }

    @Test
    @DisplayName("executor: AC-7 FALHOU (falso alarme desfeito pelo atuador) sai de ativas() e e revertida na hora")
    void executorFalha() throws Exception {
        AtuadorFalso a = new AtuadorFalso().responder((op, x) -> op.equals("aplicar")
                ? ResultadoAtuacao.falhou(204, "isn1=sessions-async", "falso-alarme-desfeito")
                : ResultadoAtuacao.aplicada(null, "nada-a-desfazer"));
        ExecutorDeAcoes e = executor(a);
        e.iniciar();
        e.aplicar(AC7_ISN3, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertEquals(List.of(), e.ativas());
        assertTrue(e.encerrar());
        List<RegistroAcao> t = List.copyOf(trilha);
        assertEquals(2, t.size());
        assertEquals("FALHOU", t.get(0).resultado());
        assertEquals("falso-alarme-desfeito", t.get(0).erro());
        assertEquals(RegistroAcao.Origem.FALHA_NA_APLICACAO, t.get(1).origem());
        assertEquals(1, e.contadores().falhas());
    }

    // ------------------------------------------------------------------ parametros

    static final ObjectMapper JSON = new ObjectMapper();

    static String bloco(String extra, String origemExtra) {
        return "{\"kLag\":2,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"p2Habilitada\":false" + extra
                + ",\"origem\":{\"kLag\":\"t\",\"tetoLagMs\":\"t\",\"tRebNs\":\"t\",\"cotaAc5\":\"t\",\"p2Habilitada\":\"t\""
                + origemExtra + "}}";
    }

    @Test
    @DisplayName("parametros: acaoP1 opcional; ausente = AC-7 (G-1); \"AC-5\" religa a parada; outro valor e recusado")
    void acaoP1() throws Exception {
        ParametrosPolitica sem = ParametrosPolitica.deJson(JSON.readTree(bloco("", "")));
        assertEquals(AcaoCluster.Tipo.AC_7, sem.acaoP1());
        assertFalse(sem.origem().containsKey("acaoP1"));
        ParametrosPolitica ac5 = ParametrosPolitica.deJson(JSON.readTree(bloco(",\"acaoP1\":\"AC-5\"",
                ",\"acaoP1\":\"comparacao\"")));
        assertEquals(AcaoCluster.Tipo.AC_5, ac5.acaoP1());
        assertEquals("comparacao", ac5.origem().get("acaoP1"));
        assertEquals(AcaoCluster.Tipo.AC_7, ParametrosPolitica.deJson(JSON.readTree(bloco(",\"acaoP1\":\"AC-7\"",
                ",\"acaoP1\":\"G-1\""))).acaoP1());
        assertThrows(IllegalArgumentException.class, () -> ParametrosPolitica.deJson(JSON.readTree(
                bloco(",\"acaoP1\":\"AC-2\"", ",\"acaoP1\":\"x\""))), "AC-2 nao e acao de P1");
        assertThrows(IllegalArgumentException.class, () -> ParametrosPolitica.deJson(JSON.readTree(
                bloco(",\"acaoP1\":\"AC-7\"", ""))), "acaoP1 sem origem");
        assertThrows(IllegalArgumentException.class, () -> ParametrosPolitica.deJson(JSON.readTree(
                bloco("", ",\"acaoP1\":\"x\""))), "origem de acaoP1 sem o campo");
        assertThrows(IllegalArgumentException.class, () -> ParametrosPolitica.deJson(JSON.readTree(
                bloco(",\"acaoP1\":7", ",\"acaoP1\":\"x\""))), "acaoP1 deve ser texto");
        // meta do decisoes: acao_p1 no fim de parametros_politica
        assertEquals(AcaoCluster.Tipo.AC_7, new ParametrosPolitica(2, 2000, 60 * S, 1, false).acaoP1());
        assertEquals(AcaoCluster.Tipo.AC_5, new ParametrosPolitica(2, 2000, 60 * S, 1, false, Map.of(),
                AcaoCluster.Tipo.AC_5).acaoP1());
    }
}
