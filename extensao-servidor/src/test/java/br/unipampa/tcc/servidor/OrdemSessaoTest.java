package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1: a ordem de conteudo da politica e a do modelo TLA+ do desempate T2 (B-45), com o conteudo antes da versao.
 * Postos do modelo: {@code fix/b45-desempate-ts}, {@code spec/SessionStore.tla:233-235} ({@code RankEstado}:
 * NONE 0 &lt; VALID 1 &lt; INVALID 2; {@code RankMotivo}: NONE 0 &lt; LOGOUT 1 &lt; EXPIRADA 2 &lt; ADMIN_BLOCK 3).
 */
class OrdemSessaoTest {

    @Test
    @DisplayName("postos do estado iguais ao RankEstado do modelo (NONE = ausente = 0)")
    void postosEstado() {
        assertEquals(1, OrdemSessao.rankEstado(SessionState.Estado.VALID));
        assertEquals(2, OrdemSessao.rankEstado(SessionState.Estado.INVALID));
        assertEquals(2, SessionState.Estado.values().length, "estado novo exige posto novo no modelo e aqui");
    }

    @Test
    @DisplayName("postos do motivo iguais ao RankMotivo do modelo")
    void postosMotivo() {
        assertEquals(0, OrdemSessao.rankMotivo(SessionState.Motivo.NONE));
        assertEquals(1, OrdemSessao.rankMotivo(SessionState.Motivo.LOGOUT));
        assertEquals(2, OrdemSessao.rankMotivo(SessionState.Motivo.EXPIRADA));
        assertEquals(3, OrdemSessao.rankMotivo(SessionState.Motivo.ADMIN_BLOCK));
        assertEquals(4, SessionState.Motivo.values().length, "motivo novo exige posto novo no modelo e aqui");
    }

    @Test
    @DisplayName("ordenar os valores do arquivo, em qualquer ordem de entrada, da a ordem escrita a mao")
    void ordemDoArquivo() {
        List<SessionState> esperado = new ArrayList<>();
        for (String r : ValoresCliente.ORDEM) {
            esperado.add(ValoresCliente.sessao(r));
        }
        Random rnd = new Random(20261106L);
        for (int i = 0; i < 50; i++) {
            List<SessionState> l = new ArrayList<>(esperado);
            Collections.shuffle(l, rnd);
            l.sort(OrdemSessao.CONTEUDO);
            for (int k = 0; k < l.size(); k++) {
                assertTrue(OrdemSessao.mesmoConteudo(esperado.get(k), l.get(k)), "posicao " + k + " na rodada " + i);
            }
        }
    }

    @Test
    @DisplayName("conteudo antes da versao: INVALID v2 vence VALID v5")
    void conteudoAntesDaVersao() {
        assertTrue(OrdemSessao.CONTEUDO.compare(ValoresCliente.sessao("logout-v2"), ValoresCliente.sessao("valid-v5")) > 0);
    }

    @Test
    @DisplayName("motivo antes do instante: ADMIN_BLOCK de 5 s vence EXPIRADA de 30 s e LOGOUT de 12 s")
    void motivoAntesDoInstante() {
        SessionState admin = ValoresCliente.sessao("admin-v2");
        assertTrue(OrdemSessao.CONTEUDO.compare(admin, ValoresCliente.sessao("expirada-v2")) > 0);
        assertTrue(OrdemSessao.CONTEUDO.compare(admin, ValoresCliente.sessao("logout-v2-tardia")) > 0);
    }

    @Test
    @DisplayName("versao antes do instante: LOGOUT v3 de 11 s vence LOGOUT v2 de 12 s")
    void versaoAntesDoInstante() {
        assertTrue(OrdemSessao.CONTEUDO.compare(ValoresCliente.sessao("logout-v3"),
                ValoresCliente.sessao("logout-v2-tardia")) > 0);
    }

    @Test
    @DisplayName("empate na ordem com identidade diferente: compara 0, mas nao e o mesmo conteudo")
    void empateSemMesmoConteudo() {
        SessionState a = ValoresCliente.sessao("valid-v1");
        SessionState b = ValoresCliente.sessao(ValoresCliente.EMPATE);
        assertEquals(0, OrdemSessao.CONTEUDO.compare(a, b));
        assertFalse(OrdemSessao.mesmoConteudo(a, b));
        assertTrue(OrdemSessao.mesmoConteudo(a, ValoresCliente.sessao("valid-v1")));
    }
}
