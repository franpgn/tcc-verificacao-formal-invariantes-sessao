package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T5: mistura v3 sobre {@link FakeCaches} com a {@link CargaSlots}, 4 threads simuladas em
 * sequencia e 10<sup>4</sup> operacoes, em S1 e S2. Sem concorrencia nao ha corrida, entao a fracao
 * INVALID entre os Validate isolados e exatamente a do token antigo (q = 5 %), estacionaria; todo
 * Logout isolado invalida uma sessao VALID e e reposto; no fim, para todo slot tocado, so a geracao
 * corrente e VALID.
 */
class MisturaV3Test {

    static final int THREADS = 4;
    static final int OPERACOES = 10_000;

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void misturaV3(Scenario cenario) throws Exception {
        MundoV3 w = new MundoV3(THREADS, false);
        w.rodar(cenario, OPERACOES, 42);

        long logins = w.ops.contar(SessionOps.OP_LOGIN);
        assertTrue(logins > 0);
        assertEquals(logins, w.ops.contar(SessionOps.OP_LOGIN, OpResult.Code.OK_LOGIN), "Login OK / Login = 1");
        assertEquals(0, w.ops.contar(SessionOps.OP_VALIDATE, OpResult.Code.NOT_FOUND), "Validate NOT_FOUND = 0");

        // INVALID entre os Validate isolados = token antigo, exatamente; 5 % +- 0,1 p.p.
        List<OpsGravadas.Chamada> val = w.isolados(SessionOps.OP_VALIDATE);
        long invalid = val.stream().filter(c -> MundoV3.invalid(c.r())).count();
        assertEquals(val.size(), w.mistura.validateIsolados());
        assertEquals(w.mistura.tokenAntigo(), invalid, "INVALID = token_antigo (sem corrida)");
        double frac = (double) invalid / val.size();
        assertEquals(0.05, frac, 0.001, "fracao INVALID " + frac);

        // estacionaria: INVALID por terco das chamadas, diferenca <= 0,5 p.p.
        int terco = val.size() / 3;
        double min = 1;
        double max = 0;
        for (int t = 0; t < 3; t++) {
            List<OpsGravadas.Chamada> parte = val.subList(t * terco, (t + 1) * terco);
            double f = (double) parte.stream().filter(c -> MundoV3.invalid(c.r())).count() / parte.size();
            min = Math.min(min, f);
            max = Math.max(max, f);
        }
        assertTrue(max - min <= 0.005, "tercos: " + min + " .. " + max);

        // todo Logout isolado invalida uma sessao VALID (nenhum no-op, nem na triade); reposicoes = logouts
        List<OpsGravadas.Chamada> lo = w.isolados(SessionOps.OP_LOGOUT);
        assertTrue(lo.size() > 0);
        assertEquals(0, w.logoutNoop(), "logout_noop = 0");
        assertEquals(lo.size(), w.mistura.logoutIsolados());
        assertEquals(w.mistura.logoutIsolados(), w.ops.reposicoes(), "reposicoes = logout_isolados");
        assertEquals(0, w.mistura.reposicaoOmitida());
        assertEquals(0, w.ops.reposicaoErro());

        // fim: para todo slot tocado, geracao corrente VALID, anteriores INVALID, seguinte ausente
        Set<Integer> tocados = new TreeSet<>();
        for (OpsGravadas.Chamada c : w.ops.chamadas) {
            if (c.arg().startsWith(KeyGenerator.PREFIXO_SID)) {
                tocados.add(SlotsGeracao.slotDe(c.arg()));
            }
        }
        long avancos = 0;
        for (int k : tocados) {
            int g = w.slots.geracao(k);
            avancos += g - 1;
            assertEquals(SessionState.Estado.VALID, w.sessoes.dados.get(SlotsGeracao.chave(k, g)).estado(), "slot " + k);
            for (int j = 0; j < g; j++) {
                assertEquals(SessionState.Estado.INVALID, w.sessoes.dados.get(SlotsGeracao.chave(k, j)).estado(),
                        "slot " + k + " geracao " + j);
            }
            assertFalse(w.sessoes.dados.containsKey(SlotsGeracao.chave(k, g + 1)), "slot " + k);
        }
        assertEquals(lo.size(), avancos, "cada Logout isolado avanca a geracao de 1");
    }

    @Test
    @DisplayName("T5/V9: Logout isolado em ERROR_* => sem reposicao, sem avanco, reposicao_omitida = 1")
    void logoutComErroNaoRepoe() throws Exception {
        MundoV3 w = new MundoV3(1, false);
        String alvo = SlotsGeracao.chave(7, 1);
        w.sessoes.antes = (op, chave) -> {
            if (op.equals("get") && chave.equals(alvo)) {
                throw new IllegalStateException("transporte simulado");
            }
        };
        MisturaV3.Cliente c = w.mistura.cliente();
        WorkloadMain.logoutV3(w.ops, c, 7);
        assertEquals(OpResult.Code.ERROR_TRANSPORT, w.ops.chamadas.get(0).r().code());
        assertEquals(1, w.slots.geracao(7), "sem avanco");
        assertFalse(w.sessoes.dados.containsKey(SlotsGeracao.chave(7, 2)), "sem reposicao");
        assertEquals(1, w.mistura.logoutIsolados());
        assertEquals(1, w.mistura.reposicaoOmitida());
        assertEquals(0, w.ops.reposicoes());

        w.sessoes.antes = (op, chave) -> { };
        WorkloadMain.logoutV3(w.ops, c, 7);
        assertEquals(2, w.slots.geracao(7));
        assertEquals(SessionState.Estado.INVALID, w.sessoes.dados.get(alvo).estado());
        assertEquals(SessionState.Estado.VALID, w.sessoes.dados.get(SlotsGeracao.chave(7, 2)).estado());
        assertEquals(1, w.ops.reposicoes());
        assertEquals(2, w.mistura.logoutIsolados());
        assertTrue(w.mistura.linha(w.ops).equals("Mistura v3: validate_isolados=0 token_antigo=0 "
                + "logout_isolados=2 reposicoes=1 reposicao_omitida=1 reposicao_erro=0"), w.mistura.linha(w.ops));
    }

    @Test
    @DisplayName("T5: reposicao que falha => reposicao_erro = 1 e sem avanco")
    void reposicaoComErro() throws Exception {
        MundoV3 w = new MundoV3(1, false);
        w.sessoes.antes = (op, chave) -> {
            if (op.equals("put") && chave.equals(SlotsGeracao.chave(7, 2))) {
                throw new IllegalStateException("transporte simulado");
            }
        };
        WorkloadMain.logoutV3(w.ops, w.mistura.cliente(), 7);
        assertEquals(1, w.slots.geracao(7));
        assertEquals(1, w.ops.reposicaoErro());
        assertEquals(0, w.ops.reposicoes());
        assertEquals(0, w.mistura.reposicaoOmitida());
    }
}
