package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.6, diagnostico A7 (V-B43.2, secao 5.4): documenta o comportamento ATUAL, sem corrigir, de
 * duas janelas que nao sao a corrida do observador corrigida em B-43.6. As decisoes sao do Gestor.
 *
 * <ul>
 *   <li><b>D1/D2/D3, {@code logout} x {@code block} na mesma sessao:</b> as duas operacoes fazem
 *       {@code get} + {@code put} sem condicao, e quem escreve por ultimo vence (atualizacao perdida
 *       da aplicacao). O motivo de uma invalidacao ja confirmada muda no grid (I4 real nos dados), e
 *       o confirmador perdedor tem a sua confirmacao sobrescrita no mapa compartilhado
 *       ({@code put}, nao {@code putIfAbsent}). Resultado: o auditor so ve a I4 se uma leitura cair
 *       na janela entre o {@code sessions.put} e o {@code invalidacoesConfirmadas.put} do vencedor
 *       (D3); fora dela, nem as leituras seguintes nem a varredura final (que usa o mesmo mapa)
 *       acusam nada (D1, D2).</li>
 *   <li><b>D4, I2 ({@code SessionOps.validate}):</b> sessao e identidade sao lidas por dois
 *       {@code get} nao atomicos. Se a sessao e lida VALID antes de um {@code block} comecar e a
 *       identidade e lida BLOCKED depois de ele terminar, o auditor registra I2 (D4a), embora em
 *       nenhum instante o grid tenha tido a sessao VALID sob a identidade bloqueada <i>depois</i> da
 *       cascata. E a mesma forma da corrida de A1, aplicada a I2. Ja uma leitura inteira dentro da
 *       janela da cascata (identidade gravada BLOCKED, sessao ainda VALID) ve um estado que existiu
 *       no grid (D4b).</li>
 * </ul>
 *
 * <p>Tudo sobre o {@link MundoS3} (mapa compartilhado entre servidores e admin), com a ordem
 * forcada pelos ganchos {@code antes} do {@link FakeCaches.Cache} e por {@link Thread#join}.</p>
 */
class AuditorDiagnosticoS3Test {

    private static final String U = "u-diag";
    private static final String SID = "sid-diag";

    private static MundoS3 mundo() {
        MundoS3 w = new MundoS3(7L, null);
        w.sessoes.dados.put(SID, SessionState.criar(SID, U, Instant.parse("2026-10-22T00:00:00Z")));
        return w;
    }

    /** Roda {@code r} numa outra thread e espera ela terminar. */
    private static void emOutraThread(Runnable r) {
        Thread t = new Thread(r, "concorrente");
        t.start();
        try {
            t.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertTrue(!t.isAlive(), "a operacao concorrente nao terminou");
    }

    /** Gancho que dispara {@code acao} uma unica vez, na thread {@code dona}, para (op, chave) dados. */
    private static BiConsumer<String, String> umaVez(Thread dona, String op, String chave, Runnable acao) {
        boolean[] feito = {false};
        return (o, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && o.equals(op) && chave.equals(k)) {
                feito[0] = true;
                acao.run();
            }
        };
    }

    private static List<String> invariantes(InvariantAuditor a) {
        return a.drenar().stream().map(v -> v.invariante() + ":" + v.chave()).toList();
    }

    private static long concorrentesI1(MundoS3 w) {
        long n = w.admin.i1Concorrentes();
        for (SessionOps o : w.apps) {
            n += o.i1Concorrentes();
        }
        return n;
    }

    @Test
    @DisplayName("D1: block le VALID, logout confirma LOGOUT, block grava ADMIN_BLOCK => LOGOUT perdido, sem I4")
    void blockVenceLogout() {
        MundoS3 w = mundo();
        w.sessoes.antes = umaVez(Thread.currentThread(), "put", SID, () -> emOutraThread(() -> w.apps.get(0).logout(SID)));

        w.admin.block(U);
        w.apps.get(1).validate(SID);
        w.apps.get(2).logout(SID);

        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.dados.get(SID).motivo(), "o block venceu");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.invalidacoes.get(SID), "a confirmacao LOGOUT foi sobrescrita");
        assertEquals(List.of(), invariantes(w.auditor), "a mudanca de motivo nao aparece como I4");
        assertEquals(0, concorrentesI1(w));
        // a varredura final usa o mesmo mapa: tambem nao acusa
        VarreduraFinal.Resultado r = VarreduraFinal.varrer(w.sessoes.dados, w.identidades.dados,
                w.invalidacoes, w.rastro, SessionOps.LIMITE_TENTATIVAS);
        assertTrue(r.achados().stream().noneMatch(a -> a.invariante().equals("I4")), r.achados().toString());
    }

    @Test
    @DisplayName("D2: logout le VALID, block confirma ADMIN_BLOCK, logout grava LOGOUT => ADMIN_BLOCK perdido, sem I4")
    void logoutVenceBlock() {
        MundoS3 w = mundo();
        w.sessoes.antes = umaVez(Thread.currentThread(), "put", SID, () -> emOutraThread(() -> w.admin.block(U)));

        w.apps.get(0).logout(SID);
        w.apps.get(1).validate(SID);

        assertEquals(SessionState.Motivo.LOGOUT, w.sessoes.dados.get(SID).motivo(), "o logout venceu");
        assertEquals(SessionState.Motivo.LOGOUT, w.invalidacoes.get(SID), "a confirmacao ADMIN_BLOCK foi sobrescrita");
        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(U).status());
        assertEquals(List.of(), invariantes(w.auditor));
        assertEquals(0, concorrentesI1(w));
    }

    @Test
    @DisplayName("D3: leitura na janela entre o sessions.put e a confirmacao do vencedor => I4 (unico ponto em que aparece)")
    void leituraNaJanelaDoVencedorVeI4() {
        MundoS3 w = mundo();
        // Um servidor de aplicacao extra, no mesmo grid e no mesmo mapa, cujo Armazem roda o block
        // concorrente ANTES do put do logout e uma leitura de outro servidor logo DEPOIS dele (antes
        // de o logout gravar a sua confirmacao). O MundoS3 so tem gancho "antes"; o "depois" vem daqui.
        Runnable[] antesDoPut = {() -> emOutraThread(() -> w.admin.block(U))};
        Runnable[] depoisDoPut = {() -> emOutraThread(() -> w.apps.get(1).validate(SID))};
        Armazem<SessionState> visao = new Armazem<>() {
            @Override public SessionState get(String k) { return w.sessoes.get(k); }

            @Override public SessionState put(String k, SessionState v) {
                Runnable a = antesDoPut[0];
                antesDoPut[0] = () -> { };
                a.run();
                SessionState r = w.sessoes.put(k, v);
                Runnable d = depoisDoPut[0];
                depoisDoPut[0] = () -> { };
                d.run();
                return r;
            }

            @Override public Set<Map.Entry<String, SessionState>> entrySet() { return w.sessoes.entrySet(); }
        };
        SessionOps app = new SessionOps(visao, w.identidades, new LatencyRegistry(), w.auditor, null, null,
                "app-extra", null, w.invalidacoes);

        app.logout(SID);

        assertEquals(List.of("I4:" + SID), invariantes(w.auditor), "unico ponto em que a I4 aparece");
        assertEquals(SessionState.Motivo.LOGOUT, w.sessoes.dados.get(SID).motivo());
        assertEquals(SessionState.Motivo.LOGOUT, w.invalidacoes.get(SID));
        // uma leitura depois disso ja nao ve nada
        w.apps.get(2).validate(SID);
        assertEquals(0, w.auditor.total());
    }

    @Test
    @DisplayName("D4a: sessao lida VALID antes do block, identidade lida BLOCKED depois => I2 registrada (corrida)")
    void i2PorLeiturasNaoAtomicas() {
        MundoS3 w = mundo();
        w.identidades.antes = umaVez(Thread.currentThread(), "get", U, () -> emOutraThread(() -> w.admin.block(U)));

        OpResult r = w.apps.get(0).validate(SID);

        assertEquals(SessionState.Estado.VALID, r.estado());
        assertEquals(SessionState.Estado.INVALID, w.sessoes.dados.get(SID).estado(), "a cascata terminou antes");
        assertEquals(List.of("I2:" + SID), invariantes(w.auditor), "comportamento atual: I2 por corrida");
    }

    @Test
    @DisplayName("D4b: validate inteiro dentro da janela da cascata (identidade BLOCKED, sessao VALID) => I2")
    void i2DentroDaCascata() {
        MundoS3 w = mundo();
        w.sessoes.antes = umaVez(Thread.currentThread(), "put", SID, () -> emOutraThread(() -> w.apps.get(0).validate(SID)));

        w.admin.block(U);

        assertEquals(List.of("I2:" + SID), invariantes(w.auditor), "estado que existiu no grid");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.dados.get(SID).motivo());
    }
}
