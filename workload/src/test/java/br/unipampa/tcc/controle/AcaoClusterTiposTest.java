package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2): tipos de acao do braco B (documento de 05/11, secoes 3.1, 3.2 e 5.1). AC-2 global e
 * reversivel com prazo {@code T_cham}; AC-5 num no so, nao reversivel, com prazo {@code T_cham + 15 s} (P-42: a parada sob
 * corte medida no I-3 do B-44.2 veio 13,1 a 13,3 s depois do pedido).
 */
class AcaoClusterTiposTest {

    static final long S = 1_000_000_000L;
    static final List<String> CACHES = List.of("sessions", "counters");

    static final AcaoCluster AC2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, CACHES, null, null);
    static final AcaoCluster AC5_ISN3 = new AcaoCluster(AcaoCluster.Tipo.AC_5, CACHES, "isn3:11222", null);

    @Test
    @DisplayName("AC-2: global (alvo proibido), reversivel, prazo T_cham")
    void ac2() {
        assertTrue(AcaoCluster.Tipo.AC_2.reversivel());
        assertEquals(AcaoCluster.RegraAlvo.PROIBIDO, AcaoCluster.Tipo.AC_2.alvo());
        assertEquals(2 * S, AcaoCluster.Tipo.AC_2.prazoNs(2 * S));
        assertThrows(IllegalArgumentException.class,
                () -> new AcaoCluster(AcaoCluster.Tipo.AC_2, CACHES, "isn1:11222", null),
                "P-34: o AC-2 por no/por cache nao existe no braco B");
        assertEquals("AC-2|sessions,counters|*", AC2.chave());
    }

    @Test
    @DisplayName("AC-5: exatamente um no como alvo, nao reversivel, prazo T_cham + 15 s (P-42, parada sob corte no I-3)")
    void ac5() {
        assertFalse(AcaoCluster.Tipo.AC_5.reversivel());
        assertEquals(AcaoCluster.RegraAlvo.UM_NO, AcaoCluster.Tipo.AC_5.alvo());
        assertEquals("AC-5", AcaoCluster.Tipo.AC_5.rotulo());
        assertEquals(17 * S, AcaoCluster.Tipo.AC_5.prazoNs(2 * S));
        assertEquals(15 * S + 7, AcaoCluster.Tipo.AC_5.prazoNs(7));
        for (String ruim : new String[] {null, "", " ", "isn1,isn2", "isn1 isn2", " isn1", "isn1\t"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AcaoCluster(AcaoCluster.Tipo.AC_5, CACHES, ruim, null), "alvo '" + ruim + "'");
        }
        assertNotEquals(AC5_ISN3.chave(), new AcaoCluster(AcaoCluster.Tipo.AC_5, CACHES, "isn2:11222", null).chave());
        assertEquals("AC-5|sessions,counters|isn3:11222", AC5_ISN3.chave());
    }

    @Test
    @DisplayName("caches continuam obrigatorios em todo tipo")
    void cachesObrigatorios() {
        assertThrows(IllegalArgumentException.class, () -> new AcaoCluster(AcaoCluster.Tipo.AC_5, List.of(), "isn1", null));
        assertThrows(IllegalArgumentException.class, () -> new AcaoCluster(AcaoCluster.Tipo.AC_2, List.of(""), null, null));
    }

    @Test
    @DisplayName("formato JSONL (decisoes 1.2): reversao sempre presente, orquestrador no AC-5 e null no AC-2")
    void reversaoNoJsonl() {
        RegistroAcao r5 = new RegistroAcao(RegistroAcao.Operacao.APLICAR, AC5_ISN3, ModoAtuacao.ACTIVE, "falso", true,
                "APLICADA", RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 5 * S, 6 * S, 6 * S, S, 204,
                "isn3:11222=parou", null);
        var n = FormatoJsonl.acao(r5);
        List<String> campos = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(campos::add);
        assertEquals("reversao", campos.get(campos.size() - 1));
        assertEquals("orquestrador", n.get("reversao").asText());
        assertEquals("isn3:11222", n.get("acao").get("alvo").asText());

        RegistroAcao r2 = new RegistroAcao(RegistroAcao.Operacao.APLICAR, AC2, ModoAtuacao.ACTIVE, "falso", true,
                "APLICADA", RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 5 * S, 6 * S, 6 * S, S, 204, "x", null);
        var n2 = FormatoJsonl.acao(r2);
        assertTrue(n2.has("reversao"), "campo nunca omitido");
        assertTrue(n2.get("reversao").isNull());
        assertEquals("decisoes-controlador/1.2", FormatoJsonl.ESQUEMA_DECISOES);
    }

    @Test
    @DisplayName("formato JSONL: linha politica com P0/P1/P2, motivo e os tipos pedidos")
    void linhaPolitica() {
        Decisao d = PoliticaPorAssinaturaTest.dec(10 * S, Perfil.CONSERVADOR);
        PlanoDeAcao p = new PlanoDeAcao(List.of(AC2), List.of(AC5_ISN3), "P1", "isolar-suspeito", "isn3:11222");
        var n = FormatoJsonl.politica(d, p, ModoAtuacao.SHADOW, 11 * S);
        List<String> campos = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("tipo", "t_fim_janela_ns", "t_decisao_ns", "modo", "perfil", "regra", "politica", "motivo",
                "no", "reverter", "aplicar", "usadas_p1"), campos); // usadas_p1: P1-IC, aditivo na 1.2
        assertEquals("isn3:11222", n.get("no").asText(), "V-7: no da escolha (suspeito do P1)");
        var sem = FormatoJsonl.politica(d, new PlanoDeAcao(List.of(), List.of(), "P0", "sem-assinatura"),
                ModoAtuacao.SHADOW, 11 * S);
        assertTrue(sem.has("no") && sem.get("no").isNull(), "campo nunca omitido: null sem no");
        assertEquals("politica", n.get("tipo").asText());
        assertEquals("P1", n.get("politica").asText());
        assertEquals("isolar-suspeito", n.get("motivo").asText());
        assertEquals("AC-2", n.get("reverter").get(0).asText());
        assertEquals("AC-5", n.get("aplicar").get(0).asText());
        assertEquals("shadow", n.get("modo").asText());
    }

    @Test
    @DisplayName("PlanoDeAcao: o construtor do W-3 nao registra politica; motivo sem politica e recusado")
    void planoDeAcao() {
        PlanoDeAcao p = new PlanoDeAcao(List.of(), List.of(AC2));
        assertEquals(null, p.politica());
        assertEquals(PlanoDeAcao.VAZIO, new PlanoDeAcao(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlanoDeAcao(List.of(), List.of(), null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new PlanoDeAcao(List.of(), List.of(), null, null, "isn3"),
                "no sem politica");
        assertEquals(null, new PlanoDeAcao(List.of(), List.of(), "P0", "sem-assinatura").no());
    }

    @Test
    @DisplayName("AtuadorPorTipo: encaminha pelo tipo; tipo sem atuador e FALHOU sem chamada (AC-6 entra como entrada nova)")
    void atuadorPorTipo() throws Exception {
        AtuadorFalso rest = new AtuadorFalso();
        AtuadorPorTipo a = new AtuadorPorTipo(Map.of(AcaoCluster.Tipo.AC_2, rest));
        assertEquals("por-tipo(AC-2=falso)", a.nome());
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.aplicar(AC2, S).estado());
        assertEquals(ResultadoAtuacao.Estado.APLICADA, a.reverter(AC2, S).estado());
        ResultadoAtuacao r = a.aplicar(AC5_ISN3, S);
        assertEquals(ResultadoAtuacao.Estado.FALHOU, r.estado());
        assertEquals("tipo-sem-atuador:AC-5", r.erro());
        assertEquals(2, rest.chamadas().size());
        assertThrows(IllegalArgumentException.class, () -> new AtuadorPorTipo(Map.of()));
    }
}
