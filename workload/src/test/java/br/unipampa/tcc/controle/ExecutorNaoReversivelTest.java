package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), item 2: acao nao reversivel (AC-5) fora da pilha, nunca desfeita pelo controlador, pedida
 * no maximo uma vez; prazo por tipo ({@code AC-2 = T_cham}, {@code AC-5 = T_cham + 3 s}); {@code T_enc} soma
 * os prazos da pilha e o da chamada em curso. Mutantes que estes testes matam: "AC-5 empilhada" e "pilha
 * invertida" (junto com os testes LIFO do {@link ExecutorDeAcoesTest}).
 */
class ExecutorNaoReversivelTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;
    static final AcaoCluster AC2 = AcaoClusterTiposTest.AC2;
    static final AcaoCluster AC5 = AcaoClusterTiposTest.AC5_ISN3;

    final List<RegistroAcao> trilha = Collections.synchronizedList(new ArrayList<>());
    final AtomicLong relogio = new AtomicLong(100 * S);
    final ExecutorDeAcoesTest.GanchoFalso gancho = new ExecutorDeAcoesTest.GanchoFalso();

    ExecutorDeAcoes executor(ModoAtuacao modo, AtuadorCluster a, long tCham) {
        return new ExecutorDeAcoes(modo, a, tCham, relogio::get, trilha::add, gancho);
    }

    List<RegistroAcao> trilha() {
        synchronized (trilha) {
            return List.copyOf(trilha);
        }
    }

    static List<String> ops(AtuadorFalso a) {
        return a.chamadas().stream().map(c -> c.operacao() + ":" + c.acao().tipo().rotulo()).toList();
    }

    @Test
    @DisplayName("active: AC-5 aplicada fica fora da pilha e de ativas(); o encerramento nao a desfaz")
    void ac5ForaDaPilha() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, 2 * S);
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertEquals(List.of(), e.ativas());
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertEquals(List.of("aplicar:AC-5"), ops(a));
        assertEquals("+AC-5:APLICADA", ExecutorDeAcoesTest.resumo(trilha()));
        assertEquals(1, e.contadores().executadas());
        assertEquals(0, e.contadores().reversoes());
        assertEquals(2 * S, e.prazoEncerramentoUsadoNs(), "pilha vazia: T_enc = T_cham");
    }

    @Test
    @DisplayName("prazo por tipo: AC-2 recebe T_cham e AC-5 recebe T_cham + 3 s")
    void prazoPorTipo() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, 2 * S);
        e.iniciar();
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        List<AtuadorFalso.Chamada> c = a.chamadas();
        assertEquals(List.of("aplicar:AC-2", "aplicar:AC-5", "reverter:AC-2"), ops(a));
        assertEquals(2 * S, c.get(0).prazoNs());
        assertEquals(5 * S, c.get(1).prazoNs());
        assertEquals(2 * S, c.get(2).prazoNs());
        assertEquals(5 * S, e.prazoNs(AcaoCluster.Tipo.AC_5));
    }

    @Test
    @DisplayName("R4 (reverterTudo): desfaz o AC-2 e nao toca no AC-5")
    void r4NaoDesfazAc5() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, 2 * S);
        e.iniciar();
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        e.aplicar(AC5, Decisao.Regra.R3, 10 * S);
        e.reverterTudo(RegistroAcao.Origem.R4, Decisao.Regra.R4, 15 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertEquals(List.of("aplicar:AC-2", "aplicar:AC-5", "reverter:AC-2"), ops(a));
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-2:APLICADA +AC-5:APLICADA -AC-2:APLICADA", ExecutorDeAcoesTest.resumo(t));
        assertEquals(RegistroAcao.Origem.R4, t.get(2).origem());
        assertTrue(e.encerrar());
        assertEquals(3, a.chamadas().size(), "o encerramento nao desfaz o AC-5");
    }

    @Test
    @DisplayName("AC-5 que falha nao e revertida (nem pelo executor), conta como falha e nao e pedida de novo")
    void ac5QueFalha() throws Exception {
        AtuadorFalso a = new AtuadorFalso().responder((op, x) ->
                ResultadoAtuacao.falhou(204, "isn3=respondendo", "alvo-continua-respondendo"));
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, 2 * S);
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        e.aplicar(AC5, Decisao.Regra.R3, 10 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        assertEquals(List.of("aplicar:AC-5"), ops(a));
        assertEquals("+AC-5:FALHOU", ExecutorDeAcoesTest.resumo(trilha()));
        assertEquals(1, e.contadores().falhas());
        assertEquals(1, e.contadores().ignoradas());
        assertEquals(1, e.contadores().pedidas());
        assertEquals(0, e.contadores().reversoesFalhas());
    }

    @Test
    @DisplayName("reverter(AC-5) e gancho da JVM nao chamam o atuador para o AC-5")
    void reverterEGanchoIgnoramAc5() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, 2 * S);
        e.iniciar();
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        e.reverter(AC5, RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 10 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        Thread g = gancho.registradas.get(0);
        g.start();
        g.join(5000);
        assertFalse(g.isAlive());
        assertEquals(List.of("aplicar:AC-5"), ops(a));
    }

    @Test
    @DisplayName("shadow: AC-5 vira PREVISTA, sem chamada; o encerramento nao gera REVERTER para ela")
    void shadowAc5() {
        ExecutorDeAcoes e = executor(ModoAtuacao.SHADOW, AtuadorFalso.proibido(), 2 * S);
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        e.aplicar(AC5, Decisao.Regra.R3, 5 * S);
        e.aplicar(AC5, Decisao.Regra.R3, 10 * S);
        assertTrue(e.encerrar());
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-2:PREVISTA +AC-5:PREVISTA -AC-2:PREVISTA", ExecutorDeAcoesTest.resumo(t));
        assertTrue(t.stream().noneMatch(RegistroAcao::executada));
        assertEquals(1, e.contadores().ignoradas());
    }

    @Test
    @DisplayName("T_enc: pilha com AC-2 + AC-5 em curso = T_cham + (T_cham + 3 s) + T_cham; A so conta a pilha")
    void prazoEncerramentoComAc5EmCurso() throws Exception {
        long tCham = 100 * MS;
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, tCham);
        e.iniciar();
        e.aplicar(AC2, Decisao.Regra.R3, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        a.bloquear();
        e.aplicar(AC5, Decisao.Regra.R3, 10 * S);
        assertTrue(a.esperarPrimeira());
        Thread soltar = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            a.liberar();
        });
        // a primeira chamada (AC-2) ja passou; espera a do AC-5 entrar antes de encerrar
        long ate = System.nanoTime() + 5 * S;
        while (a.chamadas().size() < 2 && System.nanoTime() < ate) {
            Thread.sleep(5);
        }
        soltar.start();
        assertTrue(e.encerrar(), "o AC-5 em curso cabe no prazo dele");
        soltar.join();
        assertEquals(tCham + (tCham + 3 * S) + tCham, e.prazoEncerramentoUsadoNs());
        assertEquals(List.of("aplicar:AC-2", "aplicar:AC-5", "reverter:AC-2"), ops(a));
    }
}
