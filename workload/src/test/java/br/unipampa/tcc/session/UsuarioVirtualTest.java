package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-33 (E2): ciclo de vida do usuario virtual (pool U) com relogio injetavel. */
class UsuarioVirtualTest {

    private static UsuarioVirtual um(MundoS3 m) {
        return new UsuarioVirtual(0, m.apps.get(0), m.ctx, m.relogio.agora);
    }

    /** Avanca o relogio ate o proximo instante do ator e executa um passo. */
    private static void passo(MundoS3 m, UsuarioVirtual u) {
        m.relogio.dormirAteNs(u.proximoNs());
        u.passo();
    }

    @Test
    @DisplayName("timeout de sessao no cliente: apos 30 s ociosa a sessao expira, novo Login, e a antiga fica VALID no grid")
    void timeoutDeSessaoPorInatividade() {
        MundoS3 m = new MundoS3(1, null);
        UsuarioVirtual u = um(m);
        passo(m, u); // Login
        String sidAntiga = u.sidAtual();
        assertEquals(UsuarioVirtual.Estado.PAGINA, u.estado());

        // 31 s sem nenhuma atividade: a proxima acao deve ser expirar e refazer o Login
        m.relogio.agora += RelogioFalso.seg(31);
        u.passo();
        assertEquals(UsuarioVirtual.Estado.LOGIN, u.estado());
        assertEquals(1, m.registro.valor("evento|SessaoExpiradaNoCliente"));
        long validates = m.registro.acoes().stream().filter(a -> a.operacao().equals("Validate")).count();
        assertEquals(0, validates, "nenhum Validate foi enviado com a sessao expirada");

        passo(m, u); // novo Login
        assertNotEquals(sidAntiga, u.sidAtual());
        assertEquals(SessionState.Estado.VALID, m.sessoes.dados.get(sidAntiga).estado(),
                "a sid abandonada continua VALID no servidor (o dominio nao tem expiracao)");
        assertEquals(2, m.sessoes.dados.size());
    }

    @Test
    @DisplayName("com 29 s de ociosidade a sessao ainda vale: Validate vai para a mesma sid")
    void limiteDoTimeoutDeSessao() {
        MundoS3 m = new MundoS3(1, p -> p.sessaoMedianaSeg = 3600);
        UsuarioVirtual u = um(m);
        passo(m, u);
        String sid = u.sidAtual();
        m.relogio.agora += RelogioFalso.seg(29);
        u.passo();
        assertEquals(sid, u.sidAtual());
        assertEquals(0, m.registro.valor("evento|SessaoExpiradaNoCliente"));
        assertEquals(1, m.registro.acoes().stream().filter(a -> a.operacao().equals("Validate")).count());
    }

    @Test
    @DisplayName("abandono silencioso: sessao fica VALID, nao ha Logout, e o usuario so volta depois do timeout de 30 s")
    void abandonoSilencioso() {
        MundoS3 m = new MundoS3(7, p -> { p.probLogout = 0.0; p.sessaoMedianaSeg = 4; p.sessaoSigma = 0; });
        UsuarioVirtual u = um(m);
        long ate = m.relogio.agora + RelogioFalso.seg(300);
        List<Long> loginsNs = new ArrayList<>();
        int vistos = 0;
        while (u.proximoNs() < ate) {
            passo(m, u);
            List<AcaoS3> a = m.registro.acoes();
            while (vistos < a.size()) {
                if (a.get(vistos).operacao().equals("Login") && a.get(vistos).resultado().equals("OK_LOGIN")) {
                    loginsNs.add(a.get(vistos).tNs());
                }
                vistos++;
            }
        }
        assertTrue(loginsNs.size() >= 3);
        assertEquals(0, m.registro.acoes().stream().filter(a -> a.operacao().equals("Logout")).count());
        assertEquals(loginsNs.size(), m.sessoes.dados.size(), "cada visita deixou uma sessao orfa");
        assertTrue(m.sessoes.dados.values().stream().allMatch(s -> s.estado() == SessionState.Estado.VALID));
        assertTrue(m.registro.valor("evento|AbandonoSilencioso") >= loginsNs.size() - 1);
        for (int i = 1; i < loginsNs.size(); i++) {
            assertTrue(loginsNs.get(i) - loginsNs.get(i - 1) >= RelogioFalso.seg(4 + 30) - RelogioFalso.seg(2),
                    "novo Login so depois de sessao (~4 s) + timeout (30 s)");
        }
    }

    @Test
    @DisplayName("apos Logout confirmado, o botao voltar repete Validate da mesma sid a cada 1 a 3 s por 10 s e para")
    void botaoVoltar() {
        MundoS3 m = new MundoS3(3, p -> { p.probLogout = 1.0; p.sessaoMedianaSeg = 3; p.sessaoSigma = 0; });
        UsuarioVirtual u = um(m);
        long ate = m.relogio.agora + RelogioFalso.seg(90);
        while (u.proximoNs() < ate) {
            passo(m, u);
        }
        List<AcaoS3> todas = m.registro.acoes();
        int iLogout = -1;
        for (int i = 0; i < todas.size(); i++) {
            if (todas.get(i).operacao().equals("Logout")) {
                iLogout = i;
                break;
            }
        }
        assertTrue(iLogout >= 0);
        AcaoS3 logout = todas.get(iLogout);
        List<AcaoS3> voltar = new ArrayList<>();
        for (int i = iLogout + 1; i < todas.size() && todas.get(i).operacao().equals("Validate"); i++) {
            voltar.add(todas.get(i));
        }
        assertTrue(voltar.size() >= 3 && voltar.size() <= 10, "10 s / (1 a 3 s): " + voltar.size());
        long prev = logout.tNs();
        for (AcaoS3 v : voltar) {
            assertEquals(logout.alvo(), v.alvo(), "sempre a sid do Logout");
            assertEquals("OK_VALIDATE:INVALID", v.resultado());
            long d = v.tNs() - prev;
            assertTrue(d >= RelogioFalso.seg(1) - 1 && d <= RelogioFalso.seg(3) + 1, "intervalo " + d);
            prev = v.tNs();
        }
        assertTrue(voltar.get(voltar.size() - 1).tNs() - logout.tNs() <= RelogioFalso.seg(10) + 1);
        assertEquals("Login", todas.get(iLogout + 1 + voltar.size()).operacao(), "depois da janela, nova visita");
    }

    @Test
    @DisplayName("Logout que falha apos as retentativas deixa sessao orfa VALID e nao dispara o botao voltar")
    void logoutFalhaDeixaOrfa() {
        MundoS3 m = new MundoS3(3, p -> { p.probLogout = 1.0; p.sessaoMedianaSeg = 3; p.sessaoSigma = 0; });
        UsuarioVirtual u = um(m);
        passo(m, u); // Login
        String sid = u.sidAtual();
        m.foraDoAr(0, 0, 100000); // no 0 cai para sempre
        long ate = m.relogio.agora + RelogioFalso.seg(60);
        while (u.proximoNs() < ate && m.registro.valor("evento|LogoutFalhouSessaoOrfa") == 0) {
            passo(m, u);
        }
        assertEquals(1, m.registro.valor("evento|LogoutFalhouSessaoOrfa"));
        AcaoS3 logout = m.acoesDe(u.id()).stream().filter(a -> a.operacao().equals("Logout")).findFirst().orElseThrow();
        assertTrue(logout.falhaPercebida());
        assertEquals(3, logout.tentativas());
        assertEquals(SessionState.Estado.VALID, m.sessoes.dados.get(sid).estado());
        assertEquals(UsuarioVirtual.Estado.LOGIN, u.estado());
        assertFalse(m.registro.acoes().stream().anyMatch(a -> a.operacao().equals("Validate")
                && a.alvo().equals(sid) && a.tNs() > logout.tNs()));
    }

    @Test
    @DisplayName("usuario nunca recebe IncrementFailure/ResetFailures/Block (pool U)")
    void usuarioSoLoginValidateLogout() {
        MundoS3 m = new MundoS3(5, null);
        UsuarioVirtual u = um(m);
        long ate = m.relogio.agora + RelogioFalso.seg(400);
        while (u.proximoNs() < ate) {
            passo(m, u);
        }
        for (AcaoS3 a : m.registro.acoes()) {
            assertTrue(List.of("Login", "Validate", "Logout").contains(a.operacao()), a.operacao());
        }
    }
}
