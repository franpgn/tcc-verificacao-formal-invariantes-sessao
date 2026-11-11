package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.9, regra D (decisao do Gestor de 22-23/10): {@code block} em erro. O conflito esgotado na
 * cascata termina o {@code block} em ERROR_TRANSPORT com cascata parcial, e o marco fica com a cascata
 * PENDENTE. Leituras de sessao VALID sob a identidade cujo ULTIMO {@code block} terminou em
 * {@code ERROR_*} sao {@code I2_CASCATA_INCOMPLETA}, fora de {@code S_viol}; o rastro nunca as
 * classifica REAL (DT-002). Um {@code block} sobre identidade cujo periodo de bloqueio ja teve cascata
 * concluida (sem {@code unblock} depois) nao substitui o marco.
 */
class BlockEmErroTest {

    private static final String U = "u-be";

    @Test
    @DisplayName("Z16: conflito esgotado no block => ERROR_TRANSPORT, marco PENDENTE, validate posterior I2_CASCATA_INCOMPLETA, nunca REAL")
    void conflitoEsgotado() throws Exception {
        MundoMarcos w = new MundoMarcos();
        String s1 = w.login(0, U);
        String s2 = w.login(1, U);
        w.base.sempreConflita = true;

        OpResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> w.admin.block(U));
        w.base.sempreConflita = false;

        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        assertEquals(1, w.admin.conflitoEsgotado());
        List<String> csv = w.linhasCsv();
        assertTrue(csv.get(csv.size() - 1).contains(",Block,") && csv.get(csv.size() - 1).contains(",ERROR_TRANSPORT,"),
                csv.toString());
        MarcoBloqueio m = w.admin.marco(U);
        assertNotNull(m);
        assertFalse(m.cascataConcluida(), "cascata PENDENTE");
        assertTrue(m.identidadeConfirmada());
        assertEquals(SessionState.Estado.VALID, w.base.valor(s2).estado(), "cascata parcial");

        assertEquals(SessionState.Estado.VALID, w.apps.get(2).validate(s2).estado());

        List<InvariantAuditor.Violacao> v = w.auditor.drenar();
        assertEquals(List.of(SessionOps.I2_CASCATA_INCOMPLETA), v.stream().map(InvariantAuditor.Violacao::invariante).toList());
        assertEquals(1, w.apps.get(2).i2CascataIncompleta());
        assertEquals(0, w.soma(SessionOps::i2JanelaCascata));
        String classe = w.rastro.classificarViolacaoI2(s2, InvariantAuditor.epochNs(v.get(0).em()));
        assertEquals("INCERTA", classe);
        assertNotEquals("REAL", classe);
        assertEquals(SessionState.Estado.VALID, w.base.valor(s1).estado());
    }

    @Test
    @DisplayName("Z17: put(BLOCKED) que lanca (efeito aplicado, resposta perdida) => marco no catch; leitura posterior I2_CASCATA_INCOMPLETA")
    void putDaIdentidadeLanca() {
        MundoMarcos w = new MundoMarcos();
        String sid = w.login(0, U);
        boolean[] feito = {false};
        w.identidades.antes = (op, k) -> {
            if (!feito[0] && op.equals("put") && U.equals(k)) {
                feito[0] = true;
                w.identidades.dados.put(U, w.identidades.dados.get(U).bloquear(MundoMarcos.T));
                throw new TransportException("resposta perdida", (SocketAddress) null);
            }
        };

        assertEquals(OpResult.Code.ERROR_TRANSPORT, w.admin.block(U).code());
        MarcoBloqueio m = w.admin.marco(U);
        assertNotNull(m, "marco gravado no catch");
        assertTrue(m.identidadeConfirmada());
        assertFalse(m.cascataConcluida());

        assertEquals(SessionState.Estado.VALID, w.apps.get(1).validate(sid).estado());

        assertEquals(List.of(SessionOps.I2_CASCATA_INCOMPLETA + ":" + sid), w.invariantes());
        assertEquals(1, w.apps.get(1).i2CascataIncompleta());
        assertEquals(0, w.soma(SessionOps::i2SemMarco));
    }

    @Test
    @DisplayName("Z18: block repetido sobre cascata concluida, sem unblock, terminando em erro => marco antigo fica; leitura VALID depois e I2")
    void blockRepetidoNaoSubstitui() throws Exception {
        MundoMarcos w = new MundoMarcos();
        w.login(0, U);
        assertEquals(OpResult.Code.OK_NOOP, w.admin.block(U).code());
        MarcoBloqueio m1 = w.admin.marco(U);
        assertTrue(m1.cascataConcluida());
        // sessao VALID que reaparece depois da cascata concluida (replica velha); o segundo block esgota nela
        w.base.semear("sid-z18", SessionState.criar("sid-z18", U, MundoMarcos.T));
        w.base.sempreConflita = true;

        OpResult r2 = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> w.admin.block(U));
        w.base.sempreConflita = false;

        assertEquals(OpResult.Code.ERROR_TRANSPORT, r2.code());
        assertSame(m1, w.admin.marco(U), "o marco concluido nao e substituido");

        w.apps.get(1).validate("sid-z18");

        List<String> inv = w.invariantes().stream().filter(x -> x.startsWith("I2")).toList();
        assertEquals(List.of("I2:sid-z18"), inv);
        assertEquals(0, w.soma(SessionOps::i2CascataIncompleta));
    }
}
