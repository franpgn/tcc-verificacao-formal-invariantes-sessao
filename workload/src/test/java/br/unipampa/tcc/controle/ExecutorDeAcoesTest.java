package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3: execucao das acoes do controlador sobre a interface {@link AtuadorCluster} (plano de execucao
 * de 2026-11-04, secao 4.4.3): fila com uma unica thread executora, pilha de reversao LIFO
 * idempotente, reversao no encerramento e gancho de desligamento da JVM. Em shadow o atuador nunca e
 * chamado e a trilha sai com {@code executada = false}.
 */
class ExecutorDeAcoesTest {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;

    static final AcaoCluster AC1 = new AcaoCluster(AcaoCluster.Tipo.AC_1, List.of("sessions", "counters"), null, "DEGRADED_MODE");
    static final AcaoCluster AC2 = new AcaoCluster(AcaoCluster.Tipo.AC_2, List.of("sessions", "counters"), null, null);
    static final AcaoCluster AC3 = new AcaoCluster(AcaoCluster.Tipo.AC_3, List.of("sessions"), "isn1", "1500");

    /** Gancho falso: guarda a thread registrada em vez de entrega-la a JVM. */
    static final class GanchoFalso implements ExecutorDeAcoes.Gancho {
        final List<Thread> registradas = new ArrayList<>();
        final List<Thread> removidas = new ArrayList<>();

        @Override
        public synchronized void registrar(Thread t) {
            registradas.add(t);
        }

        @Override
        public synchronized void remover(Thread t) {
            removidas.add(t);
        }
    }

    final List<RegistroAcao> trilha = Collections.synchronizedList(new ArrayList<>());
    final AtomicLong relogio = new AtomicLong(100 * S);
    final GanchoFalso gancho = new GanchoFalso();

    ExecutorDeAcoes executor(ModoAtuacao modo, AtuadorCluster a) {
        return executor(modo, a, 5 * S);
    }

    ExecutorDeAcoes executor(ModoAtuacao modo, AtuadorCluster a, long prazoEncerramentoNs) {
        return new ExecutorDeAcoes(modo, a, 2 * S, prazoEncerramentoNs, relogio::get, trilha::add, gancho);
    }

    List<RegistroAcao> trilha() {
        synchronized (trilha) {
            return List.copyOf(trilha);
        }
    }

    static String resumo(List<RegistroAcao> l) {
        StringBuilder sb = new StringBuilder();
        for (RegistroAcao r : l) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(r.operacao() == RegistroAcao.Operacao.APLICAR ? '+' : '-')
                    .append(r.acao().tipo().rotulo()).append(':').append(r.resultado());
        }
        return sb.toString();
    }

    // ----------------------------------------------------------------- shadow

    @Test
    @DisplayName("shadow: o atuador nunca e chamado; trilha PREVISTA com executada=false; sem thread nem gancho")
    void shadowNuncaChama() {
        AtuadorFalso a = AtuadorFalso.proibido();
        ExecutorDeAcoes e = executor(ModoAtuacao.SHADOW, a);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC2, Decisao.Regra.R2, 5 * S);
        e.reverterTudo(RegistroAcao.Origem.R4, Decisao.Regra.R4, 40 * S);
        assertTrue(e.encerrar());
        assertEquals(List.of(), a.chamadas());
        assertEquals("+AC-1:PREVISTA +AC-2:PREVISTA -AC-2:PREVISTA -AC-1:PREVISTA", resumo(trilha()));
        for (RegistroAcao r : trilha()) {
            assertFalse(r.executada());
            assertEquals(ModoAtuacao.SHADOW, r.modo());
            assertNull(r.codigoHttp());
        }
        assertEquals(List.of(), gancho.registradas, "shadow nao registra gancho");
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(t -> t.getName().equals(ExecutorDeAcoes.NOME_THREAD) && t.isAlive()));
    }

    @Test
    @DisplayName("shadow: aplicar duas vezes a mesma acao e idempotente; reverter acao inativa nao faz nada")
    void shadowIdempotente() {
        ExecutorDeAcoes e = executor(ModoAtuacao.SHADOW, AtuadorFalso.proibido());
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC1, Decisao.Regra.R3, 10 * S);
        e.reverter(AC2, RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 10 * S);
        assertEquals(List.of(AC1), e.ativas());
        assertEquals("+AC-1:PREVISTA", resumo(trilha()));
        assertEquals(1, e.contadores().ignoradas());
    }

    @Test
    @DisplayName("shadow: encerrar preve a reversao de tudo, LIFO, com origem ENCERRAMENTO")
    void shadowEncerrarPreveReversao() {
        ExecutorDeAcoes e = executor(ModoAtuacao.SHADOW, AtuadorFalso.proibido());
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC3, Decisao.Regra.R2, 5 * S);
        assertTrue(e.encerrar());
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-1:PREVISTA +AC-3:PREVISTA -AC-3:PREVISTA -AC-1:PREVISTA", resumo(t));
        assertEquals(RegistroAcao.Origem.ENCERRAMENTO, t.get(2).origem());
        assertEquals(List.of(), e.ativas());
    }

    // ----------------------------------------------------------------- active

    @Test
    @DisplayName("active: a chamada roda na thread controlador-atuador e nao bloqueia quem pede")
    void activeNaoBloqueia() throws Exception {
        AtuadorFalso a = new AtuadorFalso().bloquear();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> e.aplicar(AC1, Decisao.Regra.R2, 5 * S));
        assertTrue(a.esperarPrimeira());
        assertEquals(ExecutorDeAcoes.NOME_THREAD, a.chamadas().get(0).thread());
        assertEquals(2 * S, a.chamadas().get(0).prazoNs(), "prazo por chamada");
        assertEquals(List.of(AC1), e.ativas(), "a intencao ja conta antes da chamada terminar");
        a.liberar();
        assertTrue(e.aguardarFilaVazia(5 * S));
        RegistroAcao r = trilha().get(0);
        assertTrue(r.executada());
        assertEquals("APLICADA", r.resultado());
        assertEquals(204, r.codigoHttp());
        assertEquals("lido-aplicar", r.lidoDeVolta());
        assertEquals(Decisao.Regra.R2, r.regra());
        assertEquals(5 * S, r.fimJanelaNs());
        assertNotNull(r.duracaoNs());
        assertTrue(e.encerrar());
    }

    @Test
    @DisplayName("active: reverterTudo desfaz em ordem LIFO (mutante: pilha invertida)")
    void activeLifo() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC2, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC3, Decisao.Regra.R2, 5 * S);
        e.reverterTudo(RegistroAcao.Origem.R4, Decisao.Regra.R4, 60 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertEquals("+AC-1:APLICADA +AC-2:APLICADA +AC-3:APLICADA -AC-3:APLICADA -AC-2:APLICADA -AC-1:APLICADA",
                resumo(trilha()));
        assertEquals(6, a.chamadas().size());
        assertEquals(List.of(), e.ativas());
        assertTrue(e.encerrar());
        assertEquals(6, a.chamadas().size(), "nada a reverter no encerramento");
    }

    @Test
    @DisplayName("active: idempotencia (aplicar repetido chama uma vez; reverter acao inativa nao chama)")
    void activeIdempotente() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC1, Decisao.Regra.R3, 10 * S);
        e.reverter(AC2, RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 10 * S);
        e.reverter(AC1, RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 15 * S);
        e.reverter(AC1, RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 20 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertEquals(2, a.chamadas().size());
        assertEquals("+AC-1:APLICADA -AC-1:APLICADA", resumo(trilha()));
        assertTrue(e.encerrar());
    }

    @Test
    @DisplayName("active: aplicacao que falha e revertida na hora e sai das ativas")
    void falhaNaAplicacaoReverte() throws Exception {
        AtuadorFalso a = new AtuadorFalso().responder((op, x) -> op.equals("aplicar")
                ? ResultadoAtuacao.falhou(200, "AVAILABLE", "leitura de volta divergente")
                : ResultadoAtuacao.aplicada(204, "AVAILABLE"));
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-1:FALHOU -AC-1:APLICADA", resumo(t));
        assertEquals("leitura de volta divergente", t.get(0).erro());
        assertEquals(RegistroAcao.Origem.FALHA_NA_APLICACAO, t.get(1).origem());
        assertEquals(List.of(), e.ativas());
        assertEquals(1, e.contadores().falhas());
        assertTrue(e.encerrar());
        assertEquals(2, a.chamadas().size());
    }

    @Test
    @DisplayName("active: excecao do atuador vira FALHOU com o nome da excecao; o executor continua vivo")
    void excecaoDoAtuador() throws Exception {
        AtuadorFalso a = new AtuadorFalso().responder((op, x) -> {
            if (x.tipo() == AcaoCluster.Tipo.AC_1 && op.equals("aplicar")) {
                throw new IllegalStateException("conexao recusada");
            }
            return ResultadoAtuacao.aplicada(204, "ok");
        });
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC2, Decisao.Regra.R2, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-1:FALHOU -AC-1:APLICADA +AC-2:APLICADA", resumo(t));
        assertTrue(t.get(0).erro().startsWith("IllegalStateException"), t.get(0).erro());
        assertEquals(List.of(AC2), e.ativas());
        assertTrue(e.encerrar());
    }

    @Test
    @DisplayName("active: reversao que falha fica na pilha e e tentada de novo no encerramento")
    void reversaoQueFalhaFicaNaPilha() throws Exception {
        int[] reversoes = {0};
        AtuadorFalso a = new AtuadorFalso().responder((op, x) -> {
            if (op.equals("reverter") && reversoes[0]++ == 0) {
                return ResultadoAtuacao.falhou(503, null, "servidor indisponivel");
            }
            return ResultadoAtuacao.aplicada(204, "ok");
        });
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.reverter(AC1, RegistroAcao.Origem.POLITICA, Decisao.Regra.R3, 10 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-1:APLICADA -AC-1:FALHOU -AC-1:APLICADA", resumo(t));
        assertEquals(RegistroAcao.Origem.ENCERRAMENTO, t.get(2).origem());
        assertEquals(1, e.contadores().reversoesFalhas());
    }

    @Test
    @DisplayName("active: encerrar descarta aplicacoes ainda na fila e respeita o prazo com atuador travado")
    void encerrarComAtuadorTravado() throws Exception {
        AtuadorFalso a = new AtuadorFalso().bloquear();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a, 300 * MS);
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        assertTrue(a.esperarPrimeira());
        e.aplicar(AC2, Decisao.Regra.R2, 5 * S); // fica na fila atras da chamada travada
        long t0 = System.nanoTime();
        assertFalse(e.encerrar(), "nao terminou no prazo");
        long dur = System.nanoTime() - t0;
        assertTrue(dur < 3 * S, "encerrar respeitou o prazo: " + dur);
        a.liberar();
        assertTrue(e.aguardarFilaVazia(5 * S));
        List<RegistroAcao> t = trilha();
        // AC-1 termina depois do encerramento e e revertida; AC-2 nunca e chamada
        assertEquals("+AC-1:APLICADA +AC-2:DESCARTADA -AC-1:APLICADA", resumo(t));
        assertFalse(t.get(1).executada());
        assertEquals(1, a.chamadas().stream().filter(c -> c.operacao().equals("aplicar")).count());
    }

    @Test
    @DisplayName("active: gancho de desligamento registrado no iniciar; ao rodar, reverte tudo (origem GANCHO_JVM)")
    void ganchoDeDesligamento() throws Exception {
        AtuadorFalso a = new AtuadorFalso();
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, a);
        e.iniciar();
        assertEquals(1, gancho.registradas.size());
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        e.aplicar(AC2, Decisao.Regra.R2, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        Thread g = gancho.registradas.get(0);
        g.start(); // como a JVM faria
        g.join(5000);
        assertFalse(g.isAlive());
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-1:APLICADA +AC-2:APLICADA -AC-2:APLICADA -AC-1:APLICADA", resumo(t));
        assertEquals(RegistroAcao.Origem.GANCHO_JVM, t.get(2).origem());
        assertTrue(e.encerrar(), "encerrar depois do gancho e idempotente");
        assertEquals(4, a.chamadas().size());
        assertEquals(List.of(), gancho.removidas, "durante o desligamento nao se remove o gancho");
    }

    @Test
    @DisplayName("active: encerrar normal remove o gancho e para a thread")
    void encerrarRemoveGancho() throws Exception {
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, new AtuadorFalso());
        e.iniciar();
        assertTrue(e.encerrar());
        assertEquals(gancho.registradas, gancho.removidas);
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(t -> t.getName().equals(ExecutorDeAcoes.NOME_THREAD) && t.isAlive()));
        assertThrows(IllegalStateException.class, () -> e.aplicar(AC1, Decisao.Regra.R2, 5 * S));
    }

    @Test
    @DisplayName("active com atuador nulo: NAO_EXECUTADA, executada=false, mas a pilha e a reversao sao registradas")
    void atuadorNulo() throws Exception {
        ExecutorDeAcoes e = executor(ModoAtuacao.ACTIVE, new AtuadorNulo());
        e.iniciar();
        e.aplicar(AC1, Decisao.Regra.R2, 5 * S);
        assertTrue(e.aguardarFilaVazia(5 * S));
        assertTrue(e.encerrar());
        List<RegistroAcao> t = trilha();
        assertEquals("+AC-1:NAO_EXECUTADA -AC-1:NAO_EXECUTADA", resumo(t));
        assertFalse(t.get(0).executada());
        assertEquals("nulo", t.get(0).atuador());
    }

    @Test
    @DisplayName("parametros invalidos sao rejeitados")
    void parametros() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutorDeAcoes(ModoAtuacao.ACTIVE,
                new AtuadorFalso(), 0, S, relogio::get, trilha::add, gancho));
        assertThrows(IllegalArgumentException.class, () -> new ExecutorDeAcoes(ModoAtuacao.ACTIVE,
                new AtuadorFalso(), S, 0, relogio::get, trilha::add, gancho));
        assertThrows(NullPointerException.class, () -> new ExecutorDeAcoes(null,
                new AtuadorFalso(), S, S, relogio::get, trilha::add, gancho));
    }

    @Test
    @DisplayName("AcaoCluster: chave ignora o valor; caches obrigatorios; rotulos AC-1..AC-3")
    void acaoCluster() {
        AcaoCluster outro = new AcaoCluster(AcaoCluster.Tipo.AC_1, List.of("sessions", "counters"), null, "X");
        assertEquals(AC1.chave(), outro.chave());
        assertNotEquals(AC1.chave(), AC2.chave());
        assertNotEquals(AC3.chave(), new AcaoCluster(AcaoCluster.Tipo.AC_3, List.of("sessions"), "isn2", "1500").chave());
        assertEquals(List.of("AC-1", "AC-2", "AC-3"),
                List.of(AcaoCluster.Tipo.AC_1.rotulo(), AcaoCluster.Tipo.AC_2.rotulo(), AcaoCluster.Tipo.AC_3.rotulo()));
        assertThrows(IllegalArgumentException.class, () -> new AcaoCluster(AcaoCluster.Tipo.AC_1, List.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> new AcaoCluster(AcaoCluster.Tipo.AC_1, List.of(" "), null, null));
        assertThrows(NullPointerException.class, () -> new AcaoCluster(null, List.of("s"), null, null));
    }
}
