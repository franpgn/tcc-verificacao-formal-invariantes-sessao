package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.6, diagnostico A7 (V-B43.2, secao 5.4): documenta o comportamento ATUAL, sem corrigir, de
 * duas janelas que nao sao a corrida do observador corrigida em B-43.6. As decisoes sao do Gestor.
 *
 * <ul>
 *   <li><b>D1/D2/D3, {@code logout} x {@code block} na mesma sessao (reescritos no B-43.8, DT-003,
 *       com autorizacao do Gestor):</b> ate B-43.6 as duas operacoes faziam {@code get} + {@code put}
 *       sem condicao e quem escrevia por ultimo vencia (atualizacao perdida). Desde B-43.8 a producao
 *       usa leitura versionada e escrita condicional ({@link ArmazemVersionado}): a PRIMEIRA
 *       invalidacao vence, quem perde a versao rele INVALID e vira no-op ({@code invalidacaoPerdida}),
 *       e o motivo confirmado nao muda. Os casos rodam sobre o {@link MundoS3Versionado}.</li>
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

    private static MundoS3Versionado mundoVersionado() {
        MundoS3Versionado w = new MundoS3Versionado();
        w.sessoes.semear(SID, SessionState.criar(SID, U, Instant.parse("2026-10-22T00:00:00Z")));
        return w;
    }

    @Test
    @DisplayName("D1 (B-43.8): block le VALID, logout completo invalida, block perde a versao => LOGOUT mantido, no-op")
    void blockPerdeParaLogoutAnterior() {
        MundoS3Versionado w = mundoVersionado();
        w.sessoes.antes = umaVez(Thread.currentThread(), "substituirSeVersao", SID,
                () -> emOutraThread(() -> w.apps.get(0).logout(SID)));

        w.admin.block(U);
        w.apps.get(1).validate(SID);
        w.apps.get(2).logout(SID);

        assertEquals(SessionState.Motivo.LOGOUT, w.sessoes.valor(SID).motivo(), "o primeiro invalidador venceu");
        assertEquals(SessionState.Motivo.LOGOUT, w.invalidacoes.get(SID), "o perdedor nao escreveu no mapa");
        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(U).status());
        assertEquals(List.of(), invariantes(w.auditor));
        assertEquals(1, w.admin.invalidacaoPerdida(), "o block perdeu a versao e releu INVALID(LOGOUT)");
        assertEquals(1, w.invalidacaoPerdida());
        assertEquals(0, w.concorrentes());
        VarreduraFinal.Resultado r = VarreduraFinal.varrer(w.sessoes.copia(), w.identidades.dados,
                w.invalidacoes, w.rastro, SessionOps.LIMITE_TENTATIVAS);
        assertTrue(r.achados().stream().noneMatch(x -> x.invariante().equals("I4")), r.achados().toString());
    }

    @Test
    @DisplayName("D2 (B-43.8): logout le VALID, block completo invalida, logout perde a versao => ADMIN_BLOCK mantido")
    void logoutPerdeParaBlockAnterior() {
        MundoS3Versionado w = mundoVersionado();
        w.sessoes.antes = umaVez(Thread.currentThread(), "substituirSeVersao", SID,
                () -> emOutraThread(() -> w.admin.block(U)));

        OpResult r = w.apps.get(0).logout(SID);
        w.apps.get(1).validate(SID);

        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.valor(SID).motivo(), "o primeiro invalidador venceu");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.invalidacoes.get(SID), "o perdedor nao escreveu no mapa");
        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(U).status());
        assertEquals(List.of(), invariantes(w.auditor));
        assertEquals(1, w.apps.get(0).invalidacaoPerdida());
        assertEquals(1, w.invalidacaoPerdida());
        assertEquals(0, w.concorrentes());
        VarreduraFinal.Resultado v = VarreduraFinal.varrer(w.sessoes.copia(), w.identidades.dados,
                w.invalidacoes, w.rastro, SessionOps.LIMITE_TENTATIVAS);
        assertTrue(v.achados().stream().noneMatch(x -> x.invariante().equals("I4")), v.achados().toString());
    }

    @Test
    @DisplayName("D3 (B-43.8): leitura de outro servidor entre a escrita e a confirmacao do vencedor nao ve motivo divergente")
    void leituraNaJanelaDoVencedorNaoVeI4() {
        MundoS3Versionado w = mundoVersionado();
        Thread dona = Thread.currentThread();
        // O logout (dona) le VALID; antes da escrita dele, o block roda inteiro noutra thread e vence.
        // Entre a escrita condicional do block e a confirmacao dele no mapa, um terceiro servidor le.
        boolean[] lido = {false};
        w.sessoes.depois = (op, k) -> {
            if (!lido[0] && Thread.currentThread() != dona && op.equals("substituirSeVersao") && SID.equals(k)) {
                lido[0] = true;
                assertTrue(!w.invalidacoes.containsKey(SID), "a leitura cai antes da confirmacao do vencedor");
                emOutraThread(() -> w.apps.get(1).validate(SID));
            }
        };
        w.sessoes.antes = umaVez(dona, "substituirSeVersao", SID, () -> emOutraThread(() -> w.admin.block(U)));

        w.apps.get(0).logout(SID);

        assertTrue(lido[0], "a leitura na janela do vencedor aconteceu");
        assertEquals(List.of(), invariantes(w.auditor), "sem I4: o motivo lido e o do vencedor");
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.sessoes.valor(SID).motivo());
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, w.invalidacoes.get(SID));
        assertEquals(1, w.apps.get(0).invalidacaoPerdida());
        assertEquals(0, w.concorrentes());
        // uma leitura depois disso tambem nao ve nada
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
