package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B-43.8, W8, reescrito no B-43.9 (regra B, dupla checagem no {@code login}): o TOCTOU
 * {@code login} x {@code block} documentado no B-43.8 fica fechado pela releitura da identidade
 * depois do {@code put} da sessao.
 *
 * <p>Mesmo roteiro com latch: o {@code login} le a identidade OPEN; antes do {@code put} da sessao
 * nova, um {@code block} completo roda (identidade BLOCKED; a cascata nao ve a sessao ainda nao
 * gravada); o {@code login} grava a sessao VALID e rele a identidade, que agora esta BLOCKED. O
 * proprio {@code login} invalida a sessao com ADMIN_BLOCK (escrita condicional, mesmo caminho da
 * cascata), conta {@code login_revertido_por_bloqueio} e devolve BLOCKED. Estado final: nenhuma
 * sessao VALID sob a identidade bloqueada, nenhuma I2 nos {@code validate} posteriores e nenhum
 * achado na varredura final.</p>
 */
class ToctouLoginBlockTest {

    private static final String U = "u-toctou";

    @Test
    @DisplayName("W8 (B-43.9): login le OPEN, block completo, releitura ve BLOCKED => login revertido, sem I2")
    void loginRevertidoPelaDuplaChecagem() {
        MundoS3Versionado w = new MundoS3Versionado();
        Instant t = Instant.parse("2026-10-22T00:00:00Z");
        w.sessoes.semear("sid-antiga", SessionState.criar("sid-antiga", U, t));
        w.identidades.dados.put(U, IdentityState.criar(U, t));
        Thread dona = Thread.currentThread();
        CountDownLatch blockFeito = new CountDownLatch(1);
        boolean[] feito = {false};
        w.sessoes.antes = (op, k) -> {
            // o put da sessao nova do login (a unica escrita incondicional de sessao neste roteiro)
            if (!feito[0] && Thread.currentThread() == dona && op.equals("put")) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> {
                    w.admin.block(U);
                    blockFeito.countDown();
                });
            }
        };

        OpResult login = w.apps.get(0).login(U, "cred");

        assertEquals(0, blockFeito.getCount(), "o block rodou inteiro dentro do login");
        assertEquals(OpResult.Code.BLOCKED, login.code(), "a dupla checagem reverte o login");
        String sid = w.sessoes.copia().keySet().stream().filter(k -> !k.equals("sid-antiga")).findFirst().orElseThrow();
        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(U).status());
        assertEquals(SessionState.Estado.INVALID, w.sessoes.valor(sid).estado(), "a sessao nova foi invalidada pelo login");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.valor(sid).motivo());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.valor("sid-antiga").motivo(),
                "a cascata invalidou o que ja estava gravado");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.invalidacoes.get(sid), "confirmada pelo proprio login");
        assertEquals(0, w.auditor.total(), "o login em si nao gera violacao");
        assertEquals(1, w.apps.get(0).loginRevertidoPorBloqueio());

        // leituras posteriores veem INVALID
        w.apps.get(1).validate(sid);
        w.apps.get(2).validate(sid);
        List<InvariantAuditor.Violacao> v = w.auditor.drenar();
        assertEquals(List.of(), v.stream().map(InvariantAuditor.Violacao::invariante).toList(),
                "nenhum validate posterior registra I2");

        VarreduraFinal.Resultado r = VarreduraFinal.varrer(w.sessoes.copia(), w.identidades.dados,
                w.invalidacoes, w.rastro, SessionOps.LIMITE_TENTATIVAS);
        assertEquals(List.of(), r.achados(), r.achados().toString());
    }
}
