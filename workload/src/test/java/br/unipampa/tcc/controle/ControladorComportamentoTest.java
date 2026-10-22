package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static br.unipampa.tcc.controle.Roteiro.S;
import static br.unipampa.tcc.controle.Roteiro.TOP_ANOMALA;
import static br.unipampa.tcc.controle.Roteiro.TOP_OK;
import static br.unipampa.tcc.controle.Roteiro.perfis;
import static br.unipampa.tcc.controle.Roteiro.rep;
import static br.unipampa.tcc.controle.Roteiro.rodar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-28.1: histerese, oscilacao, conteudo da Decisao, tempo e determinismo do Controlador. */
class ControladorComportamentoTest {

    private static final ParametrosControlador P = ParametrosControlador.padrao();

    // ---------------------------------------------------------------- histerese

    @Test
    @DisplayName("histerese: em NORMAL, valores entre theta_out e theta_in nunca entram")
    void histereseNormal() {
        assertEquals(rep('N', 40), perfis(rodar(P, rep('l', 40))));
        assertEquals(rep('N', 40), perfis(rodar(P, rep('e', 40))));
        assertEquals(rep('N', 40), perfis(rodar(P, "lepa".repeat(10))));
    }

    @Test
    @DisplayName("histerese: em CONSERVADOR, valores entre theta_out e theta_in nunca saem")
    void histereseConservador() {
        assertEquals(rep('C', 41), perfis(rodar(P, "T" + rep('l', 40))));
        assertEquals(rep('C', 41), perfis(rodar(P, "T" + rep('e', 40))));
    }

    @Test
    @DisplayName("histerese: apos a faixa intermediaria, m calmas saem")
    void histereseDepoisSai() {
        assertEquals(rep('C', 16) + "N", perfis(rodar(P, "T" + rep('l', 10) + rep('c', 6))));
    }

    // ---------------------------------------------------------------- oscilacao

    @ParameterizedTest(name = "alternando ''{0}'' e calma a cada janela")
    @ValueSource(chars = {'L', 'E', 'B', 'T', 'V'})
    @DisplayName("sinal alternando alto/baixo: no maximo uma transicao por cooldown")
    void alternanciaNaoOscila(char alto) {
        List<Decisao> ds = rodar(P, (alto + "c").repeat(100));
        List<Long> t = instantesDeTransicao(ds);
        for (int i = 1; i < t.size(); i++) {
            assertTrue(t.get(i) - t.get(i - 1) >= P.cooldownNs(),
                    "transicoes a menos de um cooldown: " + t);
        }
        // Valores exatos: latencia/erro alternando nunca persistem k = 2 janelas;
        // topologia/violacao entram uma vez e nunca saem (nao ha m calmas seguidas).
        long esperado = (alto == 'T' || alto == 'V') ? 1 : 0;
        assertEquals(esperado, t.size(), "transicoes: " + t);
    }

    @Test
    @DisplayName("topologia anomala a cada 7 janelas: entra e sai, mas nunca a menos de um cooldown")
    void alternanciaLenta() {
        List<Decisao> ds = rodar(P, ("T" + rep('c', 6)).repeat(20));
        List<Long> t = instantesDeTransicao(ds);
        assertTrue(t.size() >= 10, "esperava varias transicoes: " + t);
        for (int i = 1; i < t.size(); i++) {
            assertTrue(t.get(i) - t.get(i - 1) >= P.cooldownNs(), "transicoes: " + t);
        }
        // a anomalia logo apos a saida cai no cooldown e e ignorada
        assertEquals(List.of(5 * S, 35 * S, 75 * S, 105 * S), t.subList(0, 4));
    }

    private static List<Long> instantesDeTransicao(List<Decisao> ds) {
        List<Long> r = new ArrayList<>();
        for (Decisao d : ds) {
            if (d.transicao()) {
                r.add(d.fimNs());
            }
        }
        return r;
    }

    // ---------------------------------------------------------------- conteudo da Decisao

    @Test
    @DisplayName("Decisao calma: motivo ESTAVEL, sem transicao, regra NENHUMA, sinais calculados")
    void decisaoCalma() {
        Decisao d = new Controlador(P).avaliar(Roteiro.janela(0, P.wNs(), 1000, 4, 1, 800, 8, 0, 3), TOP_OK);
        assertEquals(5 * S, d.fimNs());
        assertEquals(Perfil.NORMAL, d.perfil());
        assertFalse(d.transicao());
        assertEquals(Decisao.Regra.NENHUMA, d.regra());
        assertEquals(List.of(Motivo.ESTAVEL), d.motivos());
        assertEquals(Motivo.ESTAVEL, d.severidade());
        Decisao.Sinais s = d.sinais();
        assertEquals(0.01, s.taxaLatencia(), 1e-12);
        assertEquals(0.004, s.taxaErro(), 1e-12);
        assertEquals(0.25, s.fracaoIndisponivel(), 1e-12);
        assertFalse(s.latenciaAlta());
        assertFalse(s.erroAlto());
        assertFalse(s.topologiaAnomala());
        assertTrue(s.topologiaDisponivel());
        assertEquals(3, s.topologiaAmostras());
        assertEquals(0, s.topologiaAnomalas());
        assertEquals(3, s.nosMinimo());
        assertEquals(0, s.violacoesSeguranca());
        assertEquals(3, s.violacoesI2Esperadas());
        assertTrue(s.cooldownExpirado());
        assertSame(P, d.parametros(), "limiares em vigor vao junto com a decisao");
    }

    @Test
    @DisplayName("Decisao com todos os sinais: motivos em ordem de severidade, severidade VIOLACAO, regra R1")
    void decisaoTodosOsSinais() {
        Decisao d = new Controlador(P).avaliar(Roteiro.janela(0, P.wNs(), 1000, 50, 20, 1000, 100, 2, 0),
                TOP_ANOMALA);
        assertTrue(d.transicao());
        assertEquals(Perfil.CONSERVADOR, d.perfil());
        assertEquals(Decisao.Regra.R1, d.regra());
        assertEquals(List.of(Motivo.VIOLACAO, Motivo.TOPOLOGIA, Motivo.ERRO, Motivo.LATENCIA), d.motivos());
        assertEquals(Motivo.VIOLACAO, d.severidade());
        assertEquals(0.4, d.sinais().fracaoIndisponivel(), 1e-12);
        assertEquals(2, d.sinais().violacoesSeguranca());
        assertThrows(UnsupportedOperationException.class, () -> d.motivos().add(Motivo.ESTAVEL));
    }

    @Test
    @DisplayName("Decisao: amostra insuficiente deixa as taxas nulas (indeterminado), nao zero")
    void decisaoIndeterminada() {
        Decisao d = new Controlador(P).avaliar(Roteiro.janela(0, P.wNs(), 10, 0, 0, 10, 0, 0, 0), TOP_OK);
        assertNull(d.sinais().taxaLatencia());
        assertNull(d.sinais().taxaErro());
        assertNull(d.sinais().fracaoIndisponivel(), "sem erros nao ha fracao");
        assertEquals(List.of(Motivo.ESTAVEL), d.motivos());
    }

    @Test
    @DisplayName("Decisao registra a regra: R2 na entrada, R3 com sinal alto em C, R4 na saida, NENHUMA bloqueada")
    void decisaoRegras() {
        List<Decisao> ds = rodar(P, "cLL c L ccccccc T");
        assertEquals(Decisao.Regra.NENHUMA, ds.get(1).regra(), "uma janela alta: ainda nao persistiu");
        assertEquals(List.of(Motivo.LATENCIA), ds.get(1).motivos());
        assertEquals(1, ds.get(1).sinais().persistenciaLatencia());
        assertEquals(Decisao.Regra.R2, ds.get(2).regra());
        assertEquals(2, ds.get(2).sinais().persistenciaLatencia());
        assertEquals(Decisao.Regra.R3, ds.get(4).regra());
        assertEquals(0, ds.get(4).sinais().janelasCalmas());
        assertEquals(Decisao.Regra.R4, ds.get(10).regra());
        assertEquals(6, ds.get(10).sinais().janelasCalmas());
        assertTrue(ds.get(10).transicao());
        assertEquals(Perfil.NORMAL, ds.get(10).perfil());
        // w12: topologia anomala em NORMAL com cooldown ativo (saiu em w10)
        assertEquals(Decisao.Regra.NENHUMA, ds.get(12).regra());
        assertFalse(ds.get(12).sinais().cooldownExpirado());
        assertEquals(List.of(Motivo.TOPOLOGIA), ds.get(12).motivos());
        assertEquals(Perfil.NORMAL, ds.get(12).perfil());
    }

    // ---------------------------------------------------------------- tempo e determinismo

    @Test
    @DisplayName("o tempo e o fimNs da janela: um salto de fimNs expira cooldown e T_estavel sem relogio de parede")
    void tempoVemDaJanela() {
        Controlador c = new Controlador(Roteiro.comKm(2, 1));
        assertEquals(Perfil.CONSERVADOR, c.avaliar(new Janela(0, 5 * S, 1000, 0, 0, 1000, 0, 0, 0), TOP_ANOMALA).perfil());
        assertEquals(Perfil.CONSERVADOR, c.avaliar(new Janela(5 * S, 10 * S, 1000, 0, 0, 1000, 0, 0, 0), TOP_OK).perfil());
        // janela seguinte termina uma hora depois (lacuna nos dados): cooldown e estabilidade expiraram
        Decisao d = c.avaliar(new Janela(3595 * S, 3600 * S, 1000, 0, 0, 1000, 0, 0, 0), TOP_OK);
        assertEquals(Perfil.NORMAL, d.perfil());
        assertEquals(Decisao.Regra.R4, d.regra());
    }

    @Test
    @DisplayName("fimNs precisa crescer estritamente entre chamadas")
    void fimNaoCrescenteRejeitado() {
        Controlador c = new Controlador(P);
        c.avaliar(new Janela(0, 10 * S, 0, 0, 0, 0, 0, 0, 0), TOP_OK);
        assertThrows(IllegalArgumentException.class,
                () -> c.avaliar(new Janela(5 * S, 10 * S, 0, 0, 0, 0, 0, 0, 0), TOP_OK));
        assertThrows(IllegalArgumentException.class,
                () -> c.avaliar(new Janela(0, 9 * S, 0, 0, 0, 0, 0, 0, 0), TOP_OK));
    }

    @Test
    @DisplayName("argumentos nulos sao rejeitados")
    void nulos() {
        assertThrows(NullPointerException.class, () -> new Controlador(null));
        Controlador c = new Controlador(P);
        assertThrows(NullPointerException.class, () -> c.avaliar(null, TOP_OK));
        assertThrows(NullPointerException.class,
                () -> c.avaliar(new Janela(0, 5 * S, 0, 0, 0, 0, 0, 0, 0), null));
    }

    @Test
    @DisplayName("perfil inicial e NORMAL e o controlador expoe o perfil corrente")
    void perfilInicial() {
        Controlador c = new Controlador(P);
        assertEquals(Perfil.NORMAL, c.perfil());
        c.avaliar(Roteiro.janela(0, P.wNs(), 1000, 0, 0, 1000, 0, 1, 0), TOP_OK);
        assertEquals(Perfil.CONSERVADOR, c.perfil());
    }

    @Test
    @DisplayName("determinismo: a mesma entrada produz a mesma sequencia de decisoes")
    void determinismo() {
        String roteiro = "cLLccTcccccccVcc" + rep('c', 30) + "EEcBlpexyzUnicLc".repeat(5);
        List<Decisao> a = rodar(P, roteiro);
        List<Decisao> b = rodar(P, roteiro);
        assertEquals(a, b);
        assertTrue(a.stream().filter(Decisao::transicao).count() >= 3, "roteiro precisa exercitar transicoes");
    }

    @Test
    @DisplayName("determinismo: duas instancias intercaladas (uma com outro roteiro) nao interferem")
    void semEstadoCompartilhado() {
        String r1 = "cLLccTcccccccVcc" + rep('c', 30);
        String r2 = "TTTTEEEEVVVV" + rep('L', 34);
        List<Decisao> sozinho = rodar(P, r1);
        List<Roteiro.Passo> p1 = Roteiro.passos(r1, P.wNs());
        List<Roteiro.Passo> p2 = Roteiro.passos(r2, P.wNs());
        Controlador c1 = new Controlador(P);
        Controlador c2 = new Controlador(P);
        List<Decisao> intercalado = new ArrayList<>();
        for (int i = 0; i < p1.size(); i++) {
            c2.avaliar(p2.get(i).janela(), p2.get(i).topologia());
            intercalado.add(c1.avaliar(p1.get(i).janela(), p1.get(i).topologia()));
        }
        assertEquals(sozinho, intercalado);
    }
}
