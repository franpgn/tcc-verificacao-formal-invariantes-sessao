package br.unipampa.tcc.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura direta de {@link IdentityState} (POJO de domínio, O4-O7),
 * criada como parte da estratégia de teste de B-23/B-24 (ver
 * docs/validacao-merge-b21-2026-10-02.md §3 — recomendação do Senior,
 * não-bloqueante, de criar esta classe junto com a decisão de teste de
 * B-23, já que nenhuma classe desta família tinha testes diretos).
 *
 * <p>Cobre em particular o fix do bug O7 (commit {@code d13b06b}):
 * {@link IdentityState#desbloquear} deve zerar {@code contadorFalhas},
 * conforme {@code Metodologia.tex} linha 78 ("β'(u).counter = 0") e
 * {@code spec/SessionStore.tla}, ação {@code Unblock}.</p>
 */
class IdentityStateTest {

    @Test
    void criarComecaAbertaComContadorZeroEVersaoUm() {
        Instant t = Instant.now();
        IdentityState s = IdentityState.criar("alice", t);
        assertEquals("alice", s.identidade());
        assertEquals(0L, s.contadorFalhas());
        assertEquals(IdentityState.Status.OPEN, s.status());
        assertEquals(1L, s.versao());
        assertEquals(t, s.atualizadaEm());
    }

    @Test
    void incrementarAumentaContadorEPreservaStatusAbaixoDoLimite() {
        Instant t = Instant.now();
        IdentityState s = IdentityState.criar("alice", t).incrementar(5, t);
        assertEquals(1L, s.contadorFalhas());
        assertEquals(IdentityState.Status.OPEN, s.status());
        assertEquals(2L, s.versao());
    }

    @Test
    void incrementarBloqueiaAoAtingirOLimite() {
        Instant t = Instant.now();
        IdentityState s = IdentityState.criar("alice", t);
        for (int i = 0; i < 5; i++) {
            s = s.incrementar(5, t);
        }
        assertEquals(5L, s.contadorFalhas());
        assertEquals(IdentityState.Status.BLOCKED, s.status());
    }

    @Test
    void resetarZeraContadorEPreservaStatus() {
        Instant t = Instant.now();
        IdentityState bloqueada = IdentityState.criar("alice", t).bloquear(t);
        IdentityState resetada = bloqueada.resetar(t);
        assertEquals(0L, resetada.contadorFalhas());
        // O5 nao altera a marca de bloqueio (regra da monografia).
        assertEquals(IdentityState.Status.BLOCKED, resetada.status());
        assertEquals(bloqueada.versao() + 1, resetada.versao());
    }

    @Test
    void bloquearMarcaBlockedEPreservaContador() {
        Instant t = Instant.now();
        IdentityState comFalhas = IdentityState.criar("alice", t).incrementar(5, t);
        IdentityState bloqueada = comFalhas.bloquear(t);
        assertEquals(IdentityState.Status.BLOCKED, bloqueada.status());
        assertEquals(comFalhas.contadorFalhas(), bloqueada.contadorFalhas());
    }

    /**
     * O7: desbloquear() deve ZERAR contadorFalhas, nao preserva-lo.
     * Antes do fix (commit d13b06b), esta asserção falhava --
     * {@code desbloquear()} preservava o contador, divergindo de
     * Metodologia.tex linha 78 e de spec/SessionStore.tla (Unblock).
     */
    @Test
    void desbloquearZeraContadorFalhas() {
        Instant t = Instant.now();
        IdentityState bloqueadaComFalhas = IdentityState.criar("alice", t)
                .incrementar(5, t)
                .incrementar(5, t)
                .incrementar(5, t)
                .incrementar(5, t)
                .incrementar(5, t); // 5 falhas, BLOCKED
        assertEquals(IdentityState.Status.BLOCKED, bloqueadaComFalhas.status());
        assertTrue(bloqueadaComFalhas.contadorFalhas() > 0);

        IdentityState desbloqueada = bloqueadaComFalhas.desbloquear(t);

        assertEquals(0L, desbloqueada.contadorFalhas(),
                "O7 deve zerar contadorFalhas (Metodologia.tex linha 78 / spec Unblock)");
        assertEquals(IdentityState.Status.OPEN, desbloqueada.status());
        assertEquals("alice", desbloqueada.identidade());
        assertEquals(bloqueadaComFalhas.versao() + 1, desbloqueada.versao());
    }
}
