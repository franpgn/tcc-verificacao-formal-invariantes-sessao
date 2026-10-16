package br.unipampa.tcc.session;

import br.unipampa.tcc.session.RastroCliente.Bloqueio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 4, Q-10): regra de classificacao do motivo do bloqueio por
 * rastro no cliente. IdentityState nao e consultado nem alterado.
 */
class RastroClienteTest {

    private static final int LIMITE = SessionOps.LIMITE_TENTATIVAS;

    private static void block(RastroCliente r, String id, long t, boolean ok) {
        r.registrar(SessionOps.OP_BLOCK, t, t + 5,
                ok ? OpResult.okNoop() : OpResult.errorTransport(), id);
    }

    private static void unblock(RastroCliente r, String id, long t, boolean ok) {
        r.registrar(SessionOps.OP_UNBLOCK, t, t + 5,
                ok ? OpResult.okNoop() : OpResult.errorUnavailable(), id);
    }

    @Test
    @DisplayName("Login registra o mapa sid para identidade; falha de login nao")
    void mapaSidIdentidade() {
        RastroCliente r = new RastroCliente();
        r.registrar(SessionOps.OP_LOGIN, 1, 2, OpResult.okLogin("sid-1"), "u1");
        r.registrar(SessionOps.OP_LOGIN, 3, 4, OpResult.blocked(), "u2");
        r.registrar(SessionOps.OP_LOGIN, 5, 6, OpResult.errorTransport(), "u3");
        assertEquals("u1", r.identidadeDe("sid-1").orElseThrow());
        assertTrue(r.identidadeDe("sid-x").isEmpty());
    }

    @Test
    @DisplayName("ultimo evento confirmado e Block => BLOQUEIO-ADMINISTRATIVO (mesmo com contador >= limite)")
    void administrativo() {
        RastroCliente r = new RastroCliente();
        block(r, "u1", 100, true);
        assertEquals(Bloqueio.ADMINISTRATIVO, r.classificarFinal("u1", 0, LIMITE));
        assertEquals(Bloqueio.ADMINISTRATIVO, r.classificarFinal("u1", LIMITE + 3, LIMITE));
    }

    @Test
    @DisplayName("sem Block e contador >= limite => BLOQUEIO-POR-LIMITE")
    void porLimite() {
        RastroCliente r = new RastroCliente();
        assertEquals(Bloqueio.POR_LIMITE, r.classificarFinal("l1", LIMITE, LIMITE));
        assertEquals(Bloqueio.POR_LIMITE, r.classificarFinal("l1", LIMITE + 1, LIMITE));
    }

    @Test
    @DisplayName("Block seguido de Unblock confirmados + contador >= limite => por limite (Block ficou para tras)")
    void blockDesfeitoDepoisLimite() {
        RastroCliente r = new RastroCliente();
        block(r, "u1", 100, true);
        unblock(r, "u1", 200, true);
        assertEquals(Bloqueio.POR_LIMITE, r.classificarFinal("u1", LIMITE, LIMITE));
    }

    @Test
    @DisplayName("Block com erro (efeito desconhecido) e contador < limite => BLOQUEIO-INCERTO")
    void incerto() {
        RastroCliente r = new RastroCliente();
        block(r, "u1", 100, false);
        assertEquals(Bloqueio.INCERTO, r.classificarFinal("u1", 0, LIMITE));
    }

    @Test
    @DisplayName("Block com erro anterior a um Unblock confirmado nao deixa incerteza")
    void erroSuperadoPorUnblockConfirmado() {
        RastroCliente r = new RastroCliente();
        block(r, "u1", 100, false);
        unblock(r, "u1", 200, true);
        assertEquals(Bloqueio.INEXPLICADO, r.classificarFinal("u1", 0, LIMITE));
    }

    @Test
    @DisplayName("BLOCKED sem Block algum e sem contador no limite => BLOQUEIO-INEXPLICADO")
    void inexplicado() {
        RastroCliente r = new RastroCliente();
        assertEquals(Bloqueio.INEXPLICADO, r.classificarFinal("x", LIMITE - 1, LIMITE));
        block(r, "y", 100, true);
        unblock(r, "y", 200, true);
        assertEquals(Bloqueio.INEXPLICADO, r.classificarFinal("y", 0, LIMITE));
    }

    @Test
    @DisplayName("a ordem e a temporal, nao a de registro (eventos concorrentes chegam fora de ordem)")
    void ordemTemporal() {
        RastroCliente r = new RastroCliente();
        unblock(r, "u1", 200, true);  // registrado primeiro, mas ocorreu depois
        block(r, "u1", 100, true);
        assertEquals(Bloqueio.INEXPLICADO, r.classificarFinal("u1", 0, LIMITE));
        assertFalse(r.ultimoConfirmado("u1").orElseThrow().ehBlock());
    }

    @Test
    @DisplayName("classificarEm: considera so eventos que comecaram ate o instante")
    void classificarEmInstante() {
        RastroCliente r = new RastroCliente(0);
        block(r, "u1", 100, true);
        unblock(r, "u1", 200, true);
        assertEquals(Bloqueio.POR_LIMITE, r.classificarEm("u1", 50), "antes do Block: por exclusao, limite");
        assertEquals(Bloqueio.ADMINISTRATIVO, r.classificarEm("u1", 150));
        assertEquals(Bloqueio.POR_LIMITE, r.classificarEm("u1", 250));
        RastroCliente q = new RastroCliente(0);
        block(q, "u2", 100, false);
        assertEquals(Bloqueio.INCERTO, q.classificarEm("u2", 150));
    }

    @Test
    @DisplayName("tolerancia de relogio: violacao 'um pouco antes' do inicio do Block ainda e administrativa")
    void toleranciaDeRelogio() {
        RastroCliente r = new RastroCliente(RastroCliente.TOLERANCIA_RELOGIO_NS);
        block(r, "u1", 1_000_000_000L, true);
        assertEquals(Bloqueio.ADMINISTRATIVO, r.classificarEm("u1", 1_000_000_000L - 10_000_000L));
        assertEquals(Bloqueio.POR_LIMITE, r.classificarEm("u1", 1_000_000_000L - 200_000_000L));
    }

    @Test
    @DisplayName("classificarViolacaoI2 usa o mapa sid->identidade; sid sem login e DESCONHECIDA")
    void violacaoInlineReclassificada() {
        RastroCliente r = new RastroCliente(0);
        r.registrar(SessionOps.OP_LOGIN, 1, 2, OpResult.okLogin("sid-u"), "u1");
        r.registrar(SessionOps.OP_LOGIN, 1, 2, OpResult.okLogin("sid-l"), "l1");
        block(r, "u1", 100, true);
        assertEquals("REAL", r.classificarViolacaoI2("sid-u", 150));
        assertEquals("ESPERADA-POR-DESENHO", r.classificarViolacaoI2("sid-l", 150));
        assertEquals("DESCONHECIDA-SEM-LOGIN", r.classificarViolacaoI2("zzz", 150));
    }

    @Test
    @DisplayName("identidadesComBlock lista as que tiveram Block tentado (confirmado ou nao)")
    void identidadesComBlock() {
        RastroCliente r = new RastroCliente();
        block(r, "a", 1, true);
        block(r, "b", 1, false);
        unblock(r, "c", 1, true);
        assertEquals(java.util.Set.of("a", "b"), r.identidadesComBlock());
    }
}
