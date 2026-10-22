package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.8, W8 (item 6 do prompt): diagnostico do TOCTOU {@code login} x {@code block}, SEM corrigir.
 * Documenta o comportamento ATUAL (a correcao, por exemplo a dupla checagem depois do {@code put}, e
 * decisao do Gestor para o B-43.9).
 *
 * <p>Roteiro com latch: o {@code login} le a identidade OPEN; antes do {@code put} da sessao nova, um
 * {@code block} completo roda (identidade BLOCKED; a cascata nao ve a sessao ainda nao gravada); o
 * {@code login} grava a sessao VALID e devolve OK_LOGIN. Estado final: sessao VALID permanente sob
 * identidade BLOCKED. Um {@code validate} posterior registra I2, e o rastro classifica REAL (bloqueio
 * ADMINISTRATIVO). A varredura final tambem acusa I2 REAL. Nada disso e anomalia do grid.</p>
 */
class ToctouLoginBlockTest {

    private static final String U = "u-toctou";

    @Test
    @DisplayName("W8: login le OPEN, block completo, login grava VALID => sessao VALID sob BLOCKED; I2 REAL")
    void loginEscapaDaCascata() {
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
        assertEquals(OpResult.Code.OK_LOGIN, login.code(), "o login devolve OK_LOGIN com a identidade ja BLOCKED");
        String sid = login.sessionId();
        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(U).status());
        assertEquals(SessionState.Estado.VALID, w.sessoes.valor(sid).estado(), "sessao VALID sob BLOCKED");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.valor("sid-antiga").motivo(),
                "a cascata invalidou o que ja estava gravado");
        assertTrue(!w.invalidacoes.containsKey(sid), "a cascata nao viu a sessao nova");
        assertEquals(0, w.auditor.total(), "o login em si nao gera violacao");

        // permanente: nada no workload volta a invalidar essa sessao (so um logout explicito)
        w.apps.get(1).validate(sid);
        w.apps.get(2).validate(sid);
        List<InvariantAuditor.Violacao> v = w.auditor.drenar();
        assertEquals(List.of("I2", "I2"), v.stream().map(InvariantAuditor.Violacao::invariante).toList(),
                "cada validate posterior registra I2");
        for (InvariantAuditor.Violacao x : v) {
            assertEquals("REAL", w.rastro.classificarViolacaoI2(x.chave(), InvariantAuditor.epochNs(x.em())),
                    "o rastro classifica REAL (bloqueio ADMINISTRATIVO)");
        }

        VarreduraFinal.Resultado r = VarreduraFinal.varrer(w.sessoes.copia(), w.identidades.dados,
                w.invalidacoes, w.rastro, SessionOps.LIMITE_TENTATIVAS);
        List<VarreduraFinal.Achado> i2 = r.achados().stream().filter(a -> a.invariante().equals("I2")).toList();
        assertEquals(1, i2.size(), r.achados().toString());
        assertEquals(sid, i2.get(0).chave());
        assertEquals("REAL", i2.get(0).classificacao(), "a varredura final tambem conta como I2 REAL");
    }
}
