package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.COMPLETA;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.NENHUMA;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.PARTICAO_DETECTADA;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.todas;
import static br.unipampa.tcc.controle.EstadoTopologiaPorNoTest.top;
import static br.unipampa.tcc.controle.PoliticaPorAssinaturaTest.SEM;
import static br.unipampa.tcc.controle.PoliticaPorAssinaturaTest.TETO_ISN3;
import static br.unipampa.tcc.controle.PoliticaPorAssinaturaTest.dec;
import static br.unipampa.tcc.controle.PoliticaPorAssinaturaTest.resumo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-IC (06/11, G-1 a G-3 do coordenador; regras R6 e R7 de {@code docs/relatorio-a4-2026-11-06.md} secao 8): o P1
 * dispara pela mesma condicao de antes (CONSERVADOR, vista completa, S_lag no teto com o mesmo suspeito em
 * {@code kLag} janelas, suspeito respondendo a REST, cota) e pede o AC-7 (ignorar so o cache de sessoes no suspeito).
 * O que e novo e o desfazer, em qualquer perfil:
 *
 * <ul>
 *   <li><b>desfazer-alvo-sozinho</b> (R6): o alvo respondeu e se viu em minoria ({@code number_of_nodes} &lt;= n/2) em
 *       alguma amostra da janela; dali ate a fusao o {@code DENY_READ_WRITES} recusa no alvo por conta propria;</li>
 *   <li><b>desfazer-apos-fusao</b> (R7, reserva): uma particao foi vista depois da acao (algum no em minoria ou sem
 *       resposta, ou vistas divergentes) e agora a vista esta completa de novo, sem o alvo ter sido visto sozinho;</li>
 *   <li><b>suspeita-desfeita</b>: nenhuma particao foi vista e o S_lag do suspeito ficou abaixo do teto com vista
 *       completa por {@code kLag} janelas seguidas (corte curto que curou antes da deteccao). Sem esta regra, o alvo
 *       ficaria ignorando o cache ate o fim da execucao (achado do A4: a fusao nao desfaz a acao).</li>
 * </ul>
 */
class PoliticaIgnorarCacheTest {

    static final long S = 1_000_000_000L;
    static final List<String> CACHES = List.of("sessions-async", "counters-async");
    /** Modo de avaliacao (P2 desligada), acao de P1 = AC-7 (padrao do G-1). */
    static final ParametrosPolitica P = new ParametrosPolitica(2, 2000, 60 * S, 1, false);

    static final class Sim {
        final PoliticaPorAssinatura pol;
        final List<AcaoCluster> ativas = new ArrayList<>();
        long fim = 0;

        Sim(ParametrosPolitica p) {
            pol = new PoliticaPorAssinatura(p, CACHES, "sessions-async");
        }

        Sim() {
            this(P);
        }

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

        /** Duas janelas com o suspeito no teto: a segunda dispara o P1. */
        PlanoDeAcao disparar() {
            c(COMPLETA, TETO_ISN3);
            return c(COMPLETA, TETO_ISN3);
        }
    }

    @Test
    @DisplayName("P1 com AC-7: mesma condicao de disparo; acao num no, so com o cache de sessoes; cota contada")
    void disparo() {
        Sim s = new Sim();
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, TETO_ISN3)));
        PlanoDeAcao p = s.c(COMPLETA, TETO_ISN3);
        assertEquals("P1:isolar-suspeito +AC-7@isn3", resumo(p));
        assertEquals(List.of("sessions-async"), p.aplicar().get(0).caches());
        assertEquals(1, s.pol.usadasP1());
        assertEquals(1, p.usadasP1(), "a linha politica leva a cota usada");
        assertEquals(List.of(p.aplicar().get(0)), s.ativas, "reversivel: fica ativa no executor");
    }

    @Test
    @DisplayName("antes da deteccao (vista completa, suspeito no teto) a acao fica: nenhum desfazer, cota esgotada")
    void ficaAteADeteccao() {
        Sim s = new Sim();
        s.disparar();
        for (int i = 0; i < 6; i++) {
            PlanoDeAcao p = s.c(COMPLETA, TETO_ISN3);
            assertEquals("P0:cota-p1-esgotada", resumo(p), "janela " + i);
        }
        assertEquals(1, s.ativas.size());
    }

    @Test
    @DisplayName("R6: o alvo se ve em minoria -> desfazer-alvo-sozinho, em CONSERVADOR e fora dele; depois, nada mais")
    void desfazerAlvoSozinho() {
        Sim s = new Sim();
        s.disparar();
        s.c(COMPLETA, TETO_ISN3);
        PlanoDeAcao p = s.c(PARTICAO_DETECTADA, TETO_ISN3);
        assertEquals("P0:desfazer-alvo-sozinho -AC-7", resumo(p));
        assertEquals("isn3", p.no());
        assertTrue(s.ativas.isEmpty());
        assertEquals("P0:sem-assinatura", resumo(s.c(PARTICAO_DETECTADA, TETO_ISN3)), "nao repete o desfazer");
        assertEquals("P0:cota-p1-esgotada", resumo(s.c(COMPLETA, TETO_ISN3)), "cota de 1 por execucao: nao volta");

        Sim n = new Sim();
        n.disparar();
        PlanoDeAcao q = n.janela(Perfil.NORMAL, top(todas(2, 2), todas(2, 2), todas(1, 3)), SEM);
        assertEquals("P0:desfazer-alvo-sozinho -AC-7", resumo(q), "fora de CONSERVADOR tambem; minoria vista numa amostra");
    }

    @Test
    @DisplayName("R7: particao vista sem o alvo responder (nunca visto sozinho) e depois vista completa -> desfazer-apos-fusao")
    void desfazerAposFusao() {
        Sim s = new Sim();
        s.disparar();
        assertEquals("P0:p2-desabilitada", resumo(s.c(top(todas(2, 2), todas(2, 2), NENHUMA), TETO_ISN3)),
                "particao vista, alvo sem resposta: espera (o AC-7 nao para o no, e a P2 esta desligada)");
        assertEquals("P0:p2-desabilitada", resumo(s.c(top(todas(2, 2), todas(2, 2), NENHUMA), TETO_ISN3)));
        PlanoDeAcao p = s.c(COMPLETA, SEM);
        assertEquals("P0:desfazer-apos-fusao -AC-7", resumo(p));
        assertEquals("isn3", p.no());
    }

    @Test
    @DisplayName("suspeita desfeita: sem particao vista, S_lag abaixo do teto com vista completa em kLag janelas -> desfazer")
    void suspeitaDesfeita() {
        Sim s = new Sim();
        s.disparar();
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, SEM)), "1 de kLag = 2");
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, TETO_ISN3)), "voltou ao teto: zera a contagem");
        assertEquals(1, s.ativas.size());
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, SEM)));
        PlanoDeAcao p = s.c(COMPLETA, SEM);
        assertEquals("P0:suspeita-desfeita -AC-7", resumo(p));
        assertEquals("isn3", p.no());
        assertTrue(s.ativas.isEmpty());
    }

    @Test
    @DisplayName("sem acao ativa (FALHOU ou ja desfeita pelo executor), nenhuma regra de desfazer dispara")
    void semAcaoAtiva() {
        Sim s = new Sim();
        s.disparar();
        s.ativas.clear(); // o atuador devolveu FALHOU (falso alarme desfeito na hora) e o executor a tirou de ativas()
        assertEquals("P0:sem-assinatura", resumo(s.c(PARTICAO_DETECTADA, TETO_ISN3)));
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, SEM)));
        assertEquals("P0:sem-assinatura", resumo(s.c(COMPLETA, SEM)));
    }

    @Test
    @DisplayName("acaoP1 = AC-5 (comparacao): a politica volta a pedir a parada com os caches do modo")
    void comparacaoComAc5() {
        Sim s = new Sim(new ParametrosPolitica(2, 2000, 60 * S, 1, false, java.util.Map.of(), AcaoCluster.Tipo.AC_5));
        PlanoDeAcao p = s.disparar();
        assertEquals("P1:isolar-suspeito +AC-5@isn3", resumo(p));
        assertEquals(CACHES, p.aplicar().get(0).caches());
        assertEquals("P0:sem-assinatura", resumo(s.c(PARTICAO_DETECTADA, TETO_ISN3)), "o AC-5 nunca e desfeito");
    }

    @Test
    @DisplayName("construtor: o cache de sessoes tem de ser um dos caches do modo; a acao de P1 tem de ser AC-5 ou AC-7")
    void construtor() {
        assertThrows(IllegalArgumentException.class, () -> new PoliticaPorAssinatura(P, CACHES, "outro"));
        assertThrows(IllegalArgumentException.class, () -> new PoliticaPorAssinatura(P, CACHES, AcaoCluster.Tipo.AC_2));
        assertEquals("P1:isolar-suspeito +AC-7@isn3", resumo(new SimDoisArgs().disparar()),
                "dois argumentos: o primeiro cache do modo e o de sessoes (PerfilReplicacao.caches())");
    }

    static final class SimDoisArgs {
        final PoliticaPorAssinatura pol = new PoliticaPorAssinatura(P, CACHES);
        long fim = 0;

        PlanoDeAcao disparar() {
            pol.planejar(dec(fim += 5 * S, Perfil.CONSERVADOR), COMPLETA, TETO_ISN3, List.of());
            return pol.planejar(dec(fim += 5 * S, Perfil.CONSERVADOR), COMPLETA, TETO_ISN3, List.of());
        }
    }

    @Test
    @DisplayName("JSONL: a linha politica traz usadas_p1 no fim (null quando a politica nao informa)")
    void linhaPolitica() {
        Sim s = new Sim();
        s.c(COMPLETA, TETO_ISN3);
        PlanoDeAcao p = s.c(COMPLETA, TETO_ISN3);
        var n = FormatoJsonl.politica(dec(10 * S, Perfil.CONSERVADOR), p, ModoAtuacao.ACTIVE, 11 * S);
        List<String> campos = new ArrayList<>();
        n.fieldNames().forEachRemaining(campos::add);
        assertEquals("usadas_p1", campos.get(campos.size() - 1));
        assertEquals(1, n.get("usadas_p1").asInt());
        assertEquals("AC-7", n.get("aplicar").get(0).asText());
        var w3 = FormatoJsonl.politica(dec(10 * S, Perfil.CONSERVADOR),
                new PlanoDeAcao(List.of(), List.of(), "P0", "x"), ModoAtuacao.ACTIVE, 11 * S);
        assertTrue(w3.get("usadas_p1").isNull());
    }
}
