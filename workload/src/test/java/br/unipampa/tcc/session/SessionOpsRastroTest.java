package br.unipampa.tcc.session;

import br.unipampa.tcc.session.RastroCliente.Bloqueio;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (Q-10): SessionOps alimenta o rastro de cliente e a varredura final
 * enxerga o resultado de ponta a ponta (caches em memoria).
 */
class SessionOpsRastroTest {

    private FakeCaches.Cache<SessionState> sessoes;
    private FakeCaches.Cache<IdentityState> identidades;
    private RastroCliente rastro;
    private InvariantAuditor auditor;
    private SessionOps ops;

    @BeforeEach
    void preparar() {
        sessoes = new FakeCaches.Cache<>();
        identidades = new FakeCaches.Cache<>();
        rastro = new RastroCliente();
        auditor = new InvariantAuditor();
        ops = new SessionOps(sessoes.remoto(), identidades.remoto(), new LatencyRegistry(),
                auditor, new EventCsvWriter(WarmupPolicy.always()),
                EpochClock.capturarAgora(), "127.0.0.1:11222", rastro);
    }

    @Test
    @DisplayName("Login, Block e Unblock chegam ao rastro, inclusive com erro")
    void rastroAlimentado() {
        String sid = ops.login("u1", "c").sessionId();
        assertEquals("u1", rastro.identidadeDe(sid).orElseThrow());
        ops.block("u1");
        assertEquals(Bloqueio.ADMINISTRATIVO, rastro.classificarFinal("u1", 0, SessionOps.LIMITE_TENTATIVAS));
        identidades.antes = (op, k) -> { throw new TransportException("x", (SocketAddress) null); };
        ops.unblock("u1");   // erro: efeito desconhecido
        assertTrue(rastro.unblockIncertoDepois("u1", rastro.ultimoConfirmado("u1").orElseThrow()));
    }

    @Test
    @DisplayName("invalidacoesConfirmadasSnapshot reflete logout e cascata de Block")
    void snapshotDeInvalidacoes() {
        String s1 = ops.login("u1", "c").sessionId();
        String s2 = ops.login("u2", "c").sessionId();
        ops.logout(s1);
        ops.block("u2");
        assertEquals(SessionState.Motivo.LOGOUT, ops.invalidacoesConfirmadasSnapshot().get(s1));
        assertEquals(SessionState.Motivo.ADMIN_BLOCK, ops.invalidacoesConfirmadasSnapshot().get(s2));
    }

    @Test
    @DisplayName("ponta a ponta: bloqueio administrativo perdido e I2 reclassificada na varredura")
    void pontaAPonta() {
        ops.login("u1", "c");
        ops.block("u1");
        // simula bloqueio perdido (ex.: perdido na fusao): a identidade volta a OPEN no grid
        identidades.dados.put("u1", IdentityState.criar("u1", Instant.now()));
        VarreduraFinal.Resultado r = VarreduraFinal.varrer(sessoes.dados, identidades.dados,
                ops.invalidacoesConfirmadasSnapshot(), rastro, SessionOps.LIMITE_TENTATIVAS);
        assertEquals(1, r.violacoesReaisPorInvariante().get("I2-PERDIDO"));
    }

    @Test
    @DisplayName("violacao inline 'valida sob bloqueio' ganha coluna classificacao pelo rastro")
    void classificacaoInlineNoCsv(@TempDir Path dir) throws IOException {
        String sidU = ops.login("u1", "c").sessionId();
        ops.block("u1");
        // desfaz so a cascata para reproduzir a leitura "VALID sob BLOCKED"
        sessoes.dados.put(sidU, SessionState.criar(sidU, "u1", Instant.now()));
        ops.validate(sidU);
        // identidade L: bloqueada por limite, sem Block
        String sidL = ops.login("l1", "c").sessionId();
        for (int i = 0; i < SessionOps.LIMITE_TENTATIVAS; i++) ops.incrementFailure("l1");
        ops.validate(sidL);

        Path f = dir.resolve("v.csv");
        auditor.dumpCsv(f, null, List.of(new InvariantAuditor.Coluna("classificacao",
                v -> v.invariante().equals("I2")
                        ? rastro.classificarViolacaoI2(v.chave(), InvariantAuditor.epochNs(v.em()))
                        : "")));
        List<String> l = Files.readAllLines(f);
        assertEquals("instante,invariante,chave,mensagem,classificacao", l.get(0));
        assertTrue(l.stream().anyMatch(x -> x.contains(sidU) && x.endsWith(",REAL")), l.toString());
        assertTrue(l.stream().anyMatch(x -> x.contains(sidL) && x.endsWith(",ESPERADA-POR-DESENHO")), l.toString());
    }
}
