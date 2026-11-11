package br.unipampa.tcc.session;

import br.unipampa.tcc.session.RastroCliente.Bloqueio;
import br.unipampa.tcc.session.VarreduraFinal.Achado;
import br.unipampa.tcc.session.VarreduraFinal.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E1, item 4): varredura final global com mapas em memoria. Cada teste
 * falha se a regra correspondente for removida.
 */
class VarreduraFinalTest {

    private static final Instant T = Instant.parse("2026-10-15T12:00:00Z");
    private static final int LIMITE = SessionOps.LIMITE_TENTATIVAS;

    private final Map<String, SessionState> sessoes = new HashMap<>();
    private final Map<String, IdentityState> identidades = new HashMap<>();
    private final Map<String, SessionState.Motivo> confirmadas = new HashMap<>();
    private RastroCliente rastro;

    @BeforeEach
    void preparar() {
        rastro = new RastroCliente();
    }

    private void sessao(String sid, String id) {
        sessoes.put(sid, SessionState.criar(sid, id, T));
    }

    private void identidade(String id, IdentityState.Status st, long contador) {
        identidades.put(id, new IdentityState(id, contador, st, 1, T));
    }

    private Resultado varrer() {
        return VarreduraFinal.varrer(sessoes, identidades, confirmadas, rastro, LIMITE);
    }

    private static Achado achado(Resultado r, String inv, String chave) {
        return r.achados().stream()
                .filter(a -> a.invariante().equals(inv) && a.chave().equals(chave))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("I1: sid com invalidacao confirmada que esta VALID")
    void i1() {
        sessao("s1", "u1");
        confirmadas.put("s1", SessionState.Motivo.LOGOUT);
        Achado a = achado(varrer(), "I1", "s1");
        assertEquals("REAL", a.classificacao());
    }

    @Test
    @DisplayName("I1: sessao invalida como confirmado nao gera achado")
    void i1Limpa() {
        sessoes.put("s1", SessionState.criar("s1", "u1", T).invalidar(SessionState.Motivo.LOGOUT, T));
        confirmadas.put("s1", SessionState.Motivo.LOGOUT);
        assertTrue(varrer().achados().isEmpty());
    }

    @Test
    @DisplayName("I4: motivo lido diferente do confirmado")
    void i4() {
        sessoes.put("s1", SessionState.criar("s1", "u1", T).invalidar(SessionState.Motivo.ADMIN_BLOCK, T));
        confirmadas.put("s1", SessionState.Motivo.LOGOUT);
        Achado a = achado(varrer(), "I4", "s1");
        assertEquals("REAL", a.classificacao());
        assertTrue(a.detalhe().contains("LOGOUT") && a.detalhe().contains("ADMIN_BLOCK"));
    }

    @Test
    @DisplayName("I2 global: sessao VALID de identidade BLOCKED por Block confirmado e REAL, sem o cliente conhecer a sid")
    void i2GlobalAdministrativo() {
        sessao("sid-desconhecida", "u1");           // nenhum Login rastreado para esta sid
        identidade("u1", IdentityState.Status.BLOCKED, 0);
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.okNoop(), "u1");
        Resultado r = varrer();
        Achado a = achado(r, "I2", "sid-desconhecida");
        assertEquals("REAL", a.classificacao());
        assertEquals(1, r.bloqueiosPorMotivo().get(Bloqueio.ADMINISTRATIVO));
    }

    @Test
    @DisplayName("I2 global: bloqueio por limite e ESPERADA-POR-DESENHO (P-27), nao REAL")
    void i2GlobalPorLimite() {
        sessao("s1", "l1");
        identidade("l1", IdentityState.Status.BLOCKED, LIMITE);
        Resultado r = varrer();
        assertEquals("ESPERADA-POR-DESENHO", achado(r, "I2", "s1").classificacao());
        assertEquals(0, r.violacoesReaisPorInvariante().getOrDefault("I2", 0));
        assertEquals(1, r.bloqueiosPorMotivo().get(Bloqueio.POR_LIMITE));
    }

    @Test
    @DisplayName("I2 global: Block com erro e INCERTA; BLOCKED sem explicacao e INEXPLICADA")
    void i2GlobalIncertaEInexplicada() {
        sessao("s1", "u1");
        sessao("s2", "x1");
        identidade("u1", IdentityState.Status.BLOCKED, 0);
        identidade("x1", IdentityState.Status.BLOCKED, 1);
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.errorUnavailable(), "u1");
        Resultado r = varrer();
        assertEquals("INCERTA", achado(r, "I2", "s1").classificacao());
        assertEquals("INEXPLICADA", achado(r, "I2", "s2").classificacao());
        assertTrue(r.violacoesReaisPorInvariante().isEmpty(), "incerta e inexplicada nunca somam como violacao real");
    }

    @Test
    @DisplayName("I2 global: sessao INVALID ou identidade OPEN nao geram achado")
    void i2SemAchado() {
        sessoes.put("s1", SessionState.criar("s1", "u1", T).invalidar(SessionState.Motivo.ADMIN_BLOCK, T));
        identidade("u1", IdentityState.Status.BLOCKED, 0);
        sessao("s2", "u2");
        identidade("u2", IdentityState.Status.OPEN, 0);
        assertTrue(varrer().achados().stream().noneMatch(a -> a.invariante().equals("I2")));
    }

    @Test
    @DisplayName("bloqueio perdido: Block confirmado, identidade OPEN, sem Unblock confirmado posterior")
    void bloqueioPerdido() {
        identidade("u1", IdentityState.Status.OPEN, 0);
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.okNoop(), "u1");
        Achado a = achado(varrer(), "I2-PERDIDO", "u1");
        assertEquals("REAL", a.classificacao());
    }

    @Test
    @DisplayName("bloqueio perdido: identidade ausente do grid tambem conta")
    void bloqueioPerdidoIdentidadeAusente() {
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.okNoop(), "u1");
        assertEquals("REAL", achado(varrer(), "I2-PERDIDO", "u1").classificacao());
    }

    @Test
    @DisplayName("sem achado: Block seguido de Unblock confirmado; identidade segue BLOCKED")
    void semBloqueioPerdido() {
        identidade("u1", IdentityState.Status.OPEN, 0);
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.okNoop(), "u1");
        rastro.registrar(SessionOps.OP_UNBLOCK, 200, 205, OpResult.okNoop(), "u1");
        identidade("u2", IdentityState.Status.BLOCKED, 0);
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.okNoop(), "u2");
        assertTrue(varrer().achados().stream().noneMatch(a -> a.invariante().equals("I2-PERDIDO")));
    }

    @Test
    @DisplayName("bloqueio perdido com Unblock posterior com erro e INCERTA (nunca violacao)")
    void bloqueioPerdidoIncerto() {
        identidade("u1", IdentityState.Status.OPEN, 0);
        rastro.registrar(SessionOps.OP_BLOCK, 100, 105, OpResult.okNoop(), "u1");
        rastro.registrar(SessionOps.OP_UNBLOCK, 200, 205, OpResult.errorTransport(), "u1");
        Resultado r = varrer();
        assertEquals("INCERTA", achado(r, "I2-PERDIDO", "u1").classificacao());
        assertFalse(r.violacoesReaisPorInvariante().containsKey("I2-PERDIDO"));
    }

    @Test
    @DisplayName("S1/S2 (sem Block): o resumo diz explicitamente que todo BLOCKED e por limite")
    void resumoSemBlock() {
        identidade("user-1", IdentityState.Status.BLOCKED, LIMITE);
        identidade("user-2", IdentityState.Status.OPEN, 1);
        Resultado r = varrer();
        assertEquals(0, r.identidadesComBlockConfirmado());
        assertTrue(r.resumo().contains("todo BLOCKED observado e por limite"), r.resumo());
        assertEquals(Bloqueio.POR_LIMITE.rotulo(), r.identidades().get(0).classificacao());
        assertEquals("", r.identidades().get(1).classificacao());
    }

    @Test
    @DisplayName("esquema dos CSVs de varredura")
    void esquemaCsv(@TempDir Path dir) throws IOException {
        sessao("s1", "u1");
        confirmadas.put("s1", SessionState.Motivo.LOGOUT);
        identidade("u1", IdentityState.Status.OPEN, 0);
        Resultado r = varrer();
        Path a = dir.resolve("varredura-rep-001.csv");
        Path i = dir.resolve("varredura-identidades-rep-001.csv");
        VarreduraFinal.escreverAchados(a, r);
        VarreduraFinal.escreverIdentidades(i, r);
        List<String> la = Files.readAllLines(a);
        assertEquals("invariante,chave,classificacao,detalhe", la.get(0));
        assertTrue(la.get(1).startsWith("I1,s1,REAL,"));
        List<String> li = Files.readAllLines(i);
        assertEquals("identidade,status,contador,classificacao_bloqueio", li.get(0));
        assertEquals("u1,OPEN,0,", li.get(1));
    }
}
