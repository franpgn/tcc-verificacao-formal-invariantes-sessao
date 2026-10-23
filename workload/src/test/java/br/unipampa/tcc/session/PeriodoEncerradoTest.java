package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.9, ressalva F-1 da V-B43.9: um marco concluido so vale enquanto o periodo de bloqueio dura. Se o
 * {@code put(OPEN)} do {@code unblock} lanca com o efeito aplicado (resposta perdida), a identidade fica
 * OPEN e o marco concluido fica no mapa. Um {@code block} (ou um limite atingido) que le a identidade
 * OPEN abre um periodo NOVO e grava marcos novos; o marco velho nao pode ser preservado.
 */
class PeriodoEncerradoTest {

    private static final String U = "u-f1";

    /** {@code unblock} cujo {@code put(OPEN)} e aplicado no duble e depois lanca (resposta perdida). */
    private static void unblockComRespostaPerdida(MundoMarcos w, String id) {
        boolean[] feito = {false};
        w.identidades.antes = (op, k) -> {
            if (!feito[0] && op.equals("put") && id.equals(k)) {
                feito[0] = true;
                w.identidades.dados.put(id, w.identidades.dados.get(id).desbloquear(MundoMarcos.T));
                throw new TransportException("resposta perdida", (SocketAddress) null);
            }
        };
        assertEquals(OpResult.Code.ERROR_TRANSPORT, w.admin.unblock(id).code());
        w.identidades.antes = (op, k) -> { };
        assertEquals(IdentityState.Status.OPEN, w.identidades.dados.get(id).status(), "o efeito foi aplicado");
    }

    @Test
    @DisplayName("Z20 (F-1): unblock com resposta perdida, block novo, leitura VALID dentro da cascata => I2_JANELA_CASCATA, nao I2 REAL")
    void blockDepoisDeUnblockComRespostaPerdida() {
        MundoMarcos w = new MundoMarcos();
        w.login(0, U);
        assertEquals(OpResult.Code.OK_NOOP, w.admin.block(U).code());
        MarcoBloqueio m1 = w.admin.marco(U);
        assertTrue(m1.cascataConcluida());
        unblockComRespostaPerdida(w, U);
        assertSame(m1, w.admin.marco(U), "o marco do periodo anterior ficou no mapa");

        String sid = w.login(1, U); // a identidade esta OPEN: login aceito
        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        OpResult[] res = new OpResult[1];
        w.base.antes = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("substituirSeVersao") && sid.equals(k)) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> res[0] = w.apps.get(2).validate(sid));
            }
        };

        assertEquals(OpResult.Code.OK_NOOP, w.admin.block(U).code());

        assertEquals(SessionState.Estado.VALID, res[0].estado(), "leitura dentro da cascata do block novo");
        List<InvariantAuditor.Violacao> v = w.auditor.drenar();
        assertEquals(List.of(SessionOps.I2_JANELA_CASCATA),
                v.stream().map(InvariantAuditor.Violacao::invariante).toList(), "nao e I2 (REAL) do periodo anterior");
        MarcoBloqueio m2 = w.admin.marco(U);
        assertNotNull(m2);
        assertNotEquals(m1, m2, "periodo novo, marcos novos");
        assertTrue(m2.marcoIdentidadeNs() > m1.marcoCascataNs());
        assertTrue(m2.cascataConcluida());
    }

    @Test
    @DisplayName("Z21 (D4): limite atingido de novo depois de unblock com resposta perdida => marco LIMITE novo")
    void limiteDepoisDeUnblockComRespostaPerdida() {
        MundoMarcos w = new MundoMarcos();
        String id = "lim-f1";
        for (int i = 0; i < SessionOps.LIMITE_TENTATIVAS; i++) {
            w.apps.get(0).incrementFailure(id);
        }
        MarcoBloqueio m1 = w.admin.marco(id);
        assertEquals(MarcoBloqueio.Tipo.LIMITE, m1.tipo());
        unblockComRespostaPerdida(w, id);
        assertSame(m1, w.admin.marco(id));

        for (int i = 0; i < SessionOps.LIMITE_TENTATIVAS; i++) {
            w.apps.get(0).incrementFailure(id);
        }

        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(id).status());
        MarcoBloqueio m2 = w.admin.marco(id);
        assertNotEquals(m1, m2, "o limite atingido a partir de OPEN abre um periodo novo");
        assertTrue(m2.marcoIdentidadeNs() > m1.marcoCascataNs());
        assertEquals(MarcoBloqueio.Tipo.LIMITE, m2.tipo());
    }
}
