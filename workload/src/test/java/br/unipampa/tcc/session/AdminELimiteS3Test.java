package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-33 (E2): ator administrativo (Block/Unblock) e pool L (bloqueio por limite). */
class AdminELimiteS3Test {

    private static List<AcaoS3> por(List<AcaoS3> l, String ator, String op) {
        return l.stream().filter(a -> a.ator().equals(ator) && a.operacao().equals(op)).collect(Collectors.toList());
    }

    @Test
    @DisplayName("admin: Block de uma identidade de U com sessao, mantido por >= 20 s, depois Unblock")
    void blockHoldUnblock() {
        MundoS3 m = new MundoS3(11, p -> { p.usuarios = 1; p.contasLimite = 0; p.adminMediaSeg = 5; });
        S3Simulacao s = m.simulacao();
        long fim = m.relogio.agora + RelogioFalso.seg(240);
        MundoS3.rodar(s, fim);

        List<AcaoS3> blocks = por(m.registro.acoes(), "admin", "Block");
        List<AcaoS3> unblocks = por(m.registro.acoes(), "admin", "Unblock");
        assertFalse(blocks.isEmpty(), "o admin bloqueou alguem");
        assertFalse(unblocks.isEmpty());
        for (AcaoS3 b : blocks) {
            assertEquals("s3-u-000", b.alvo());
            assertEquals("OK_NOOP", b.resultado());
            assertFalse(b.falhaPercebida());
            AcaoS3 u = unblocks.stream().filter(x -> x.tNs() > b.tNs()).findFirst().orElse(null);
            if (u != null) {
                assertTrue(u.tNs() - b.tNs() >= RelogioFalso.seg(20), "hold minimo de 20 s");
            }
        }
        // o rastro do cliente enxerga o Block confirmado (insumo da classificacao Q-10)
        assertFalse(m.rastro.eventos("s3-u-000").isEmpty());
    }

    @Test
    @DisplayName("durante o bloqueio administrativo: Login da identidade da BLOCKED e Validate ve INVALID/ADMIN_BLOCK")
    void efeitoDoBloqueio() {
        MundoS3 m = new MundoS3(1, p -> { p.usuarios = 1; p.contasLimite = 0; p.sessaoMedianaSeg = 3600; p.idleSeg = 3600; });
        UsuarioVirtual u = new UsuarioVirtual(0, m.apps.get(0), m.ctx, m.relogio.agora);
        m.relogio.dormirAteNs(u.proximoNs());
        u.passo(); // Login
        String sid = u.sidAtual();
        assertNotNull(sid);

        AtorAdministrativoS3 adm = new AtorAdministrativoS3(m.admin, m.ctx, m.relogio.agora);
        m.relogio.dormirAteNs(adm.proximoNs());
        adm.passo(); // Block
        assertEquals(IdentityState.Status.BLOCKED, m.identidades.dados.get("s3-u-000").status());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, m.sessoes.dados.get(sid).motivo());

        m.relogio.dormirAteNs(u.proximoNs());
        u.passo(); // Validate: a sessao foi encerrada no servidor
        AcaoS3 v = m.registro.acoes().stream().filter(a -> a.operacao().equals("Validate")).findFirst().orElseThrow();
        assertEquals("OK_VALIDATE:INVALID", v.resultado());
        assertEquals(UsuarioVirtual.Estado.LOGIN, u.estado());

        m.relogio.dormirAteNs(u.proximoNs());
        u.passo(); // Login durante o bloqueio
        AcaoS3 l = por(m.registro.acoes(), "s3-u-000", "Login").get(1);
        assertEquals("BLOCKED", l.resultado());
        assertFalse(l.falhaPercebida(), "BLOCKED e resposta do servidor, nao falha");

        // depois do hold, Unblock
        m.relogio.dormirAteNs(adm.proximoNs());
        adm.passo();
        assertEquals(IdentityState.Status.OPEN, m.identidades.dados.get("s3-u-000").status());
    }

    @Test
    @DisplayName("Block com erro: retenta uma vez, marca tentativa incerta, nao segura nem faz Unblock, nao repara nada")
    void blockComErro() {
        MundoS3 m = new MundoS3(1, p -> { p.usuarios = 1; p.contasLimite = 0; });
        m.ctx.online.add("s3-u-000");
        m.sessoes.dados.put("sid-x", SessionState.criar("sid-x", "s3-u-000", java.time.Instant.now()));
        m.foraDoAr(3, 0, 100000); // console fora do ar
        AtorAdministrativoS3 adm = new AtorAdministrativoS3(m.admin, m.ctx, m.relogio.agora);
        m.relogio.dormirAteNs(adm.proximoNs());
        adm.passo();
        AcaoS3 b = por(m.registro.acoes(), "admin", "Block").get(0);
        assertEquals(2, b.tentativas(), "1 + 1 retentativa");
        assertTrue(b.falhaPercebida());
        assertEquals("tentativa-incerta", b.nota());
        assertEquals(1, m.registro.valor("evento|BlockAdminSemConfirmacao"));
        // proxima acao e um novo Block (nao um Unblock): nao ha o que desbloquear
        m.relogio.dormirAteNs(adm.proximoNs());
        adm.passo();
        assertTrue(por(m.registro.acoes(), "admin", "Unblock").isEmpty());
    }

    @Test
    @DisplayName("sem usuario online o admin nao bloqueia ninguem")
    void semOnlineNaoBloqueia() {
        MundoS3 m = new MundoS3(1, null);
        AtorAdministrativoS3 adm = new AtorAdministrativoS3(m.admin, m.ctx, m.relogio.agora);
        for (int i = 0; i < 5; i++) {
            m.relogio.dormirAteNs(adm.proximoNs());
            adm.passo();
        }
        assertTrue(m.registro.acoes().isEmpty());
    }

    @Test
    @DisplayName("pool L (caminho do limite): 5 IncrementFailure espacados de 1 s, Login BLOCKED, Unblock de suporte 60 s depois")
    void contaLimite() {
        MundoS3 m = new MundoS3(2, p -> { p.usuarios = 0; p.contasLimite = 1; p.lProbReset = 0.0; });
        ContaLimiteS3 c = new ContaLimiteS3(0, m.apps.get(1), m.admin, m.ctx, m.relogio.agora);
        long ate = m.relogio.agora + RelogioFalso.seg(100);
        while (c.proximoNs() < ate && por(m.registro.acoes(), "suporte", "Unblock").isEmpty()) {
            m.relogio.dormirAteNs(c.proximoNs());
            c.passo();
            AcaoS3 ult = m.registro.acoes().isEmpty() ? null : m.registro.acoes().get(m.registro.acoes().size() - 1);
            if (ult != null && ult.operacao().equals("Login")) {
                assertEquals(IdentityState.Status.BLOCKED, m.identidades.dados.get("s3-l-000").status());
            }
        }
        List<AcaoS3> inc = por(m.registro.acoes(), "s3-l-000", "IncrementFailure");
        assertEquals(5, inc.size());
        for (int i = 1; i < inc.size(); i++) {
            assertEquals(RelogioFalso.seg(1), inc.get(i).tNs() - inc.get(i - 1).tNs());
        }
        AcaoS3 login = por(m.registro.acoes(), "s3-l-000", "Login").get(0);
        assertEquals("BLOCKED", login.resultado());
        AcaoS3 unblock = por(m.registro.acoes(), "suporte", "Unblock").get(0);
        assertEquals(RelogioFalso.seg(60), unblock.tNs() - login.tNs());
        assertEquals(IdentityState.Status.OPEN, m.identidades.dados.get("s3-l-000").status());
        assertEquals(0, m.identidades.dados.get("s3-l-000").contadorFalhas(), "Unblock zera o contador");
    }

    @Test
    @DisplayName("pool L (caminho do reset): menos de 5 falhas e ResetFailures; nunca bloqueia")
    void contaLimiteReset() {
        MundoS3 m = new MundoS3(2, p -> { p.usuarios = 0; p.contasLimite = 1; p.lProbReset = 1.0; });
        ContaLimiteS3 c = new ContaLimiteS3(0, m.apps.get(0), m.admin, m.ctx, m.relogio.agora);
        long ate = m.relogio.agora + RelogioFalso.seg(600);
        while (c.proximoNs() < ate) {
            m.relogio.dormirAteNs(c.proximoNs());
            c.passo();
            assertEquals(IdentityState.Status.OPEN, m.identidades.dados.get("s3-l-000") == null
                    ? IdentityState.Status.OPEN : m.identidades.dados.get("s3-l-000").status());
        }
        assertTrue(por(m.registro.acoes(), "s3-l-000", "ResetFailures").size() >= 3);
        assertTrue(por(m.registro.acoes(), "s3-l-000", "Login").isEmpty());
        assertTrue(por(m.registro.acoes(), "suporte", "Unblock").isEmpty());
    }
}
