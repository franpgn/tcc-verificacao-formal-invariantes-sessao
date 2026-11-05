package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (P-6 da V-W5, achado A-1, opcao a): o programa de carga diz ao orquestrador, na linha de fim da carga, qual
 * no o AC-5 parou. A regra e a mesma de {@code analysis/comparar_bracos.py} ({@code ac5_aplicada}): so a linha
 * {@code acao} APLICAR AC-5 com resultado APLICADA para um no. PREVISTA (shadow), NAO_EXECUTADA (atuador nulo) e
 * FALHOU nao contam. O executor guarda o alvo da primeira em memoria (sem ler a trilha do disco).
 */
class ExecutorAc5AplicadoTest {

    static final long S = 1_000_000_000L;
    static final AcaoCluster AC2 = AcaoClusterTiposTest.AC2;
    static final AcaoCluster AC5 = AcaoClusterTiposTest.AC5_ISN3;

    final List<RegistroAcao> trilha = Collections.synchronizedList(new ArrayList<>());
    final AtomicLong relogio = new AtomicLong(100 * S);
    final ExecutorDeAcoesTest.GanchoFalso gancho = new ExecutorDeAcoesTest.GanchoFalso();

    ExecutorDeAcoes executor(ModoAtuacao modo, AtuadorCluster a) {
        return new ExecutorDeAcoes(modo, a, 2 * S, relogio::get, trilha::add, gancho);
    }

    @Test
    @DisplayName("antes de qualquer acao e com so AC-2: nenhum alvo")
    void semAc5() throws Exception {
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, new AtuadorFalso());
        assertNull(e.alvoDoAc5Aplicado());
        e.iniciar();
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertNull(e.alvoDoAc5Aplicado());
    }

    @Test
    @DisplayName("AC-5 APLICADA: o alvo da acao, igual ao da linha acao da trilha")
    void ac5Aplicada() throws Exception {
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, new AtuadorFalso());
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertEquals("isn3:11222", e.alvoDoAc5Aplicado());
        RegistroAcao r = trilha.get(0);
        assertEquals("APLICADA", r.resultado());
        assertEquals(r.acao().alvo(), e.alvoDoAc5Aplicado());
    }

    @Test
    @DisplayName("AC-5 FALHOU: nenhum alvo (o orquestrador confere os nos parados por conta propria, O8)")
    void ac5Falhou() throws Exception {
        AtuadorFalso a = new AtuadorFalso().responder((op, x) -> ResultadoAtuacao.falhou(204, "isn3=respondendo",
                "alvo continuou respondendo"));
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertEquals("FALHOU", trilha.get(0).resultado());
        assertNull(e.alvoDoAc5Aplicado());
    }

    @Test
    @DisplayName("atuador que nao executa (NAO_EXECUTADA): nenhum alvo")
    void ac5NaoExecutada() throws Exception {
        AtuadorFalso a = new AtuadorFalso().responder((op, x) -> ResultadoAtuacao.naoExecutada("atuador nulo"));
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertNull(e.alvoDoAc5Aplicado());
    }

    @Test
    @DisplayName("shadow: AC-5 PREVISTA, atuador nunca chamado, nenhum alvo")
    void shadow() {
        ExecutorDeAcoes e = executor(ModoAtuacao.SHADOW, AtuadorFalso.proibido());
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertTrue(e.encerrar());
        assertEquals("PREVISTA", trilha.get(0).resultado());
        assertNull(e.alvoDoAc5Aplicado());
    }

    @Test
    @DisplayName("dois AC-5 em nos diferentes (a cota e da politica): fica o alvo do primeiro APLICADA")
    void primeiroAplicado() throws Exception {
        AcaoCluster outro = new AcaoCluster(AcaoCluster.Tipo.AC_5, AC5.caches(), "isn2:11222", null);
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, new AtuadorFalso());
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        e.aplicar(outro, Decisao.Regra.R3, 10 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertEquals("isn3:11222", e.alvoDoAc5Aplicado());
    }
}
