package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.COMPLETA;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.NENHUMA;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.PARTICAO_DETECTADA;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.QUEDA_ISN3;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.alguma;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.todas;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.top;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), itens 4 e 7: a tabela da secao 5 do documento de 05/11, linha a linha, e as proibicoes
 * (AC-5 nunca com vista incompleta, nunca para no sem resposta, nunca duas vezes, nunca com o suspeito
 * trocando, nunca abaixo do teto; AC-2 nunca com vistas divergentes; reversao do AC-2 na volta do no e no
 * {@code T_reb}). Mutantes que estes testes matam: "teto T_reb esquecido", "AC-5 mandada a no sem resposta".
 * As reversoes no R4 e no encerramento sao do executor ({@link ExecutorNaoReversivelTest#r4NaoDesfazAc5()}).
 */
class PoliticaPorAssinaturaTest {

    static final long S = 1_000_000_000L;
    static final List<String> CACHES = List.of("sessions", "counters");
    /** k = 2, teto 2000 ms, T_reb = 60 s, cota 1 (W = 5 s nos parametros padrao do nucleo). */
    static final ParametrosPolitica P = new ParametrosPolitica(2, 2000, 60 * S, 1);

    static Decisao dec(long fimNs, Perfil perfil) {
        Decisao.Sinais s = new Decisao.Sinais(null, null, null, false, false, false, true, 3, 0, 3, 0, 0, 0, 0, 0,
                true, EstadoDefasagem.indeterminada(), false, 0);
        Decisao.Regra r = perfil == Perfil.CONSERVADOR ? Decisao.Regra.R3 : Decisao.Regra.NENHUMA;
        return new Decisao(fimNs, perfil, false, r, List.of(Motivo.ESTAVEL), Motivo.ESTAVEL, s,
                ParametrosControlador.padrao());
    }

    static EstadoDefasagem lag(String no, double ms) {
        return new EstadoDefasagem(EstadoDefasagem.Nivel.ALTA, no, ms, 6);
    }

    static final EstadoDefasagem TETO_ISN3 = lag("isn3", 2000.0);
    static final EstadoDefasagem SEM = EstadoDefasagem.indeterminada();

    /** Executa janelas de 5 s em sequencia, mantendo as acoes ativas como o executor manteria. */
    static final class Sim {
        final PoliticaPorAssinatura pol = new PoliticaPorAssinatura(P, CACHES);
        final List<AcaoCluster> ativas = new ArrayList<>();
        long fim = 0;

        PlanoDeAcao janela(Perfil perfil, EstadoTopologia top, EstadoDefasagem lag) {
            fim += 5 * S;
            PlanoDeAcao p = pol.planejar(dec(fim, perfil), top, lag, List.copyOf(ativas));
            for (AcaoCluster a : p.reverter()) {
                ativas.removeIf(x -> x.chave().equals(a.chave()));
            }
            for (AcaoCluster a : p.aplicar()) {
                if (a.tipo().reversivel()) {
                    ativas.add(a);
                }
            }
            return p;
        }

        PlanoDeAcao c(EstadoTopologia top, EstadoDefasagem lag) {
            return janela(Perfil.CONSERVADOR, top, lag);
        }
    }

    static String resumo(PlanoDeAcao p) {
        StringBuilder sb = new StringBuilder(p.politica() == null ? "-" : p.politica());
        sb.append(':').append(p.motivo());
        p.reverter().forEach(a -> sb.append(" -").append(a.tipo().rotulo()));
        p.aplicar().forEach(a -> sb.append(" +").append(a.tipo().rotulo()).append(a.alvo() == null ? "" : "@" + a.alvo()));
        return sb.toString();
    }

    // ------------------------------------------------------------------ linhas da tabela 5

    @Test
    @DisplayName("linha 1 (P1): vista completa e S_lag no teto com o mesmo suspeito em k janelas -> AC-5 no suspeito")
    void p1() {
        Sim s = new Sim();
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, TETO_ISN3)));
        PlanoDeAcao p = s.c(COMPLETA, TETO_ISN3);
        assertEquals("P1:isolar-suspeito +AC-5@isn3", resumo(p));
        AcaoCluster a = p.aplicar().get(0);
        assertEquals(CACHES, a.caches());
        assertEquals(1, s.pol.usadasP1());
    }

    @Test
    @DisplayName("linha 2 (P2): um no sem resposta, os outros respondendo, sem divergencia -> AC-2 global")
    void p2() {
        Sim s = new Sim();
        PlanoDeAcao p = s.c(QUEDA_ISN3, SEM);
        assertEquals("P2:adiar-rebalanceamento +AC-2", resumo(p));
        assertNull(p.aplicar().get(0).alvo(), "forma global (P-34)");
        assertEquals("P2:ac2-mantida", resumo(s.c(QUEDA_ISN3, SEM)), "ja ativa: nao pede de novo");
    }

    @Test
    @DisplayName("linha 3 (P0): CONSERVADOR sem nenhuma assinatura -> nenhuma acao, motivo sem-assinatura")
    void p0SemAssinatura() {
        Sim s = new Sim();
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, SEM)));
        assertEquals("P0:sem-assinatura", resumo(s.c(top(todas(3, 3), alguma(3, 3), todas(3, 3)), TETO_ISN3)));
    }

    @Test
    @DisplayName("linha 4: vistas divergentes -> nenhuma acao; AC-2 ativa e revertida")
    void divergentes() {
        Sim s = new Sim();
        s.c(QUEDA_ISN3, SEM);
        assertEquals("P0:vistas-divergentes -AC-2", resumo(s.c(PARTICAO_DETECTADA, SEM)));
        assertEquals("P0:sem-assinatura", resumo(s.c(PARTICAO_DETECTADA, SEM)));
    }

    @Test
    @DisplayName("linha 5: AC-2 ativa e o no ausente volta a responder -> reverter")
    void reversaoNaVolta() {
        Sim s = new Sim();
        s.c(QUEDA_ISN3, SEM);
        s.c(QUEDA_ISN3, SEM);
        assertEquals("P0:no-voltou -AC-2", resumo(s.c(top(todas(3, 3), todas(3, 3), alguma(3, 3)), SEM)));
        assertEquals(List.of(), s.ativas);
    }

    @Test
    @DisplayName("linha 5: AC-2 ativa por T_reb -> reverter; nao volta para o mesmo no ate ele responder (mutante: teto esquecido)")
    void reversaoNoTeto() {
        Sim s = new Sim();
        assertEquals("P2:adiar-rebalanceamento +AC-2", resumo(s.c(QUEDA_ISN3, SEM))); // fim = 5 s
        for (int i = 0; i < 11; i++) { // 10 s .. 60 s: 55 s desde a aplicacao, abaixo do teto
            assertEquals("P2:ac2-mantida", resumo(s.c(QUEDA_ISN3, SEM)), "janela " + s.fim / S);
        }
        assertEquals("P0:teto-treb -AC-2", resumo(s.c(QUEDA_ISN3, SEM))); // fim = 65 s: 60 s
        assertEquals("P0:treb-esgotado", resumo(s.c(QUEDA_ISN3, SEM)));
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, SEM))); // isn3 voltou
        assertEquals("P2:adiar-rebalanceamento +AC-2", resumo(s.c(QUEDA_ISN3, SEM)), "nova queda: vale de novo");
    }

    @Test
    @DisplayName("reversao do AC-2 vale tambem fora de CONSERVADOR; P1/P2 nao (NORMAL so reverte)")
    void normal() {
        Sim s = new Sim();
        s.c(QUEDA_ISN3, SEM);
        assertSame(PlanoDeAcao.VAZIO, s.janela(Perfil.NORMAL, QUEDA_ISN3, SEM));
        assertEquals("P0:no-voltou -AC-2", resumo(s.janela(Perfil.NORMAL, COMPLETA, SEM)));
        Sim n = new Sim();
        for (int i = 0; i < 5; i++) {
            assertSame(PlanoDeAcao.VAZIO, n.janela(Perfil.NORMAL, COMPLETA, TETO_ISN3));
        }
        assertSame(PlanoDeAcao.VAZIO, n.janela(Perfil.NORMAL, QUEDA_ISN3, SEM));
    }

    // ------------------------------------------------------------------ proibicoes do AC-5

    @Test
    @DisplayName("AC-5 nunca com vista incompleta (um no com 2 membros, ou falhando em alguma amostra)")
    void ac5NuncaComVistaIncompleta() {
        Sim s = new Sim();
        for (int i = 0; i < 4; i++) {
            assertTrue(s.c(top(todas(3, 3), todas(2, 3), todas(3, 3)), TETO_ISN3).aplicar().isEmpty());
            assertTrue(s.c(top(alguma(3, 3), todas(3, 3), todas(3, 3)), TETO_ISN3).aplicar().isEmpty());
        }
    }

    @Test
    @DisplayName("AC-5 nunca para no sem resposta (mutante): suspeito falhou em alguma amostra ou nao e consultado")
    void ac5NuncaParaNoSemResposta() {
        Sim s = new Sim();
        for (int i = 0; i < 4; i++) {
            PlanoDeAcao p = s.c(top(todas(3, 3), todas(3, 3), alguma(3, 3)), TETO_ISN3);
            assertTrue(p.aplicar().stream().noneMatch(a -> a.tipo() == AcaoCluster.Tipo.AC_5), resumo(p));
        }
        Sim r = new Sim();
        for (int i = 0; i < 4; i++) { // rotulo do S_lag que nao e de no consultado
            assertTrue(r.c(COMPLETA, lag("isn9", 2000.0)).aplicar().isEmpty());
        }
        Sim q = new Sim();
        for (int i = 0; i < 4; i++) { // isn3 caido: P2, nunca P1
            PlanoDeAcao p = q.c(QUEDA_ISN3, TETO_ISN3);
            assertTrue(p.aplicar().stream().noneMatch(a -> a.tipo() == AcaoCluster.Tipo.AC_5), resumo(p));
        }
    }

    @Test
    @DisplayName("AC-5 nunca duas vezes na execucao (cota 1)")
    void ac5NuncaDuasVezes() {
        Sim s = new Sim();
        s.c(COMPLETA, TETO_ISN3);
        assertEquals("P1:isolar-suspeito +AC-5@isn3", resumo(s.c(COMPLETA, TETO_ISN3)));
        for (int i = 0; i < 5; i++) {
            assertEquals("P0:cota-p1-esgotada", resumo(s.c(COMPLETA, TETO_ISN3)));
        }
        PoliticaPorAssinatura semCota = new PoliticaPorAssinatura(new ParametrosPolitica(1, 2000, 60 * S, 0), CACHES);
        assertEquals("P0:cota-p1-esgotada", resumo(semCota.planejar(dec(5 * S, Perfil.CONSERVADOR), COMPLETA,
                TETO_ISN3, List.of())));
    }

    @Test
    @DisplayName("AC-5 nunca com o suspeito trocando entre janelas")
    void ac5NuncaComSuspeitoTrocando() {
        Sim s = new Sim();
        for (int i = 0; i < 6; i++) {
            PlanoDeAcao p = s.c(COMPLETA, lag(i % 2 == 0 ? "isn3" : "isn2", 2000.0));
            assertTrue(p.aplicar().isEmpty(), resumo(p));
        }
    }

    @Test
    @DisplayName("AC-5 nunca abaixo do teto, nem com S_lag indeterminado (W-4 nao integrado)")
    void ac5NuncaAbaixoDoTeto() {
        Sim s = new Sim();
        for (int i = 0; i < 6; i++) {
            assertTrue(s.c(COMPLETA, lag("isn3", 1999.0)).aplicar().isEmpty());
            assertTrue(s.c(COMPLETA, SEM).aplicar().isEmpty());
            assertTrue(s.c(COMPLETA, new EstadoDefasagem(EstadoDefasagem.Nivel.ALTA, null, 5000.0, 6)).aplicar().isEmpty());
        }
    }

    @Test
    @DisplayName("persistencia quebra quando uma janela nao foi consultada (R4): salto de fimNs maior que W")
    void persistenciaQuebraNoSalto() {
        PoliticaPorAssinatura pol = new PoliticaPorAssinatura(P, CACHES);
        assertTrue(pol.planejar(dec(5 * S, Perfil.CONSERVADOR), COMPLETA, TETO_ISN3, List.of()).aplicar().isEmpty());
        // janela de 10 s foi R4 (sem consulta)
        assertTrue(pol.planejar(dec(15 * S, Perfil.CONSERVADOR), COMPLETA, TETO_ISN3, List.of()).aplicar().isEmpty());
        assertEquals(1, pol.planejar(dec(20 * S, Perfil.CONSERVADOR), COMPLETA, TETO_ISN3, List.of()).aplicar().size());
    }

    // ------------------------------------------------------------------ proibicoes do AC-2

    @Test
    @DisplayName("AC-2 nunca com vistas divergentes, mesmo com um no sem resposta")
    void ac2NuncaComDivergencia() {
        Sim s = new Sim();
        for (int i = 0; i < 3; i++) {
            PlanoDeAcao p = s.c(top(todas(3, 3), todas(2, 2), NENHUMA), SEM);
            assertTrue(p.aplicar().isEmpty(), resumo(p));
        }
        assertTrue(s.c(top(todas(3, 3), NENHUMA, NENHUMA), SEM).aplicar().isEmpty(), "dois nos fora: nada");
    }

    @Test
    @DisplayName("AC-2 nao e aplicado ao no que a propria politica parou pelo AC-5")
    void ac2NaoParaNoParado() {
        Sim s = new Sim();
        s.c(COMPLETA, TETO_ISN3);
        s.c(COMPLETA, TETO_ISN3); // AC-5 em isn3
        assertEquals("P0:no-parado-pela-politica", resumo(s.c(QUEDA_ISN3, SEM)));
        Sim t = new Sim();
        t.c(COMPLETA, TETO_ISN3);
        t.c(COMPLETA, TETO_ISN3);
        assertEquals("P2:adiar-rebalanceamento +AC-2",
                resumo(t.c(top(NENHUMA, todas(3, 3), todas(3, 3)), SEM)), "outro no caindo: P2 vale");
    }

    // ------------------------------------------------------------------ parametros

    @Test
    @DisplayName("parametros: obrigatorios, sem padrao; ausente, desconhecido, tipo errado ou fora da regra sao recusados")
    void parametros() throws Exception {
        ObjectMapper j = new ObjectMapper();
        ParametrosPolitica ok = ParametrosPolitica.deJson(j.readTree(
                "{\"k\":3,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1}"));
        assertEquals(new ParametrosPolitica(3, 2000, 60 * S, 1), ok);
        for (String ruim : new String[] {
                "{\"k\":3,\"tetoLagMs\":2000,\"cotaAc5\":1}",
                "{\"k\":3,\"tetoLagMs\":2000,\"tRebNs\":60000000000}",
                "{\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1}",
                "{\"k\":3,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1,\"extra\":0}",
                "{\"k\":3.5,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1}",
                "{\"k\":\"3\",\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1}",
                "{\"k\":0,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":1}",
                "{\"k\":3,\"tetoLagMs\":0,\"tRebNs\":60000000000,\"cotaAc5\":1}",
                "{\"k\":3,\"tetoLagMs\":2000,\"tRebNs\":0,\"cotaAc5\":1}",
                "{\"k\":3,\"tetoLagMs\":2000,\"tRebNs\":60000000000,\"cotaAc5\":2}",
                "[]"}) {
            assertThrows(IllegalArgumentException.class, () -> ParametrosPolitica.deJson(j.readTree(ruim)), ruim);
        }
        assertThrows(NullPointerException.class, () -> new PoliticaPorAssinatura(null, CACHES));
        assertThrows(IllegalArgumentException.class, () -> new PoliticaPorAssinatura(P, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PoliticaPorAssinatura(P, CACHES, AcaoCluster.Tipo.AC_2),
                "acao de P1 tem de ser nao reversivel e num no");
        assertEquals("por-assinatura", new PoliticaPorAssinatura(P, CACHES).nome());
    }
}
