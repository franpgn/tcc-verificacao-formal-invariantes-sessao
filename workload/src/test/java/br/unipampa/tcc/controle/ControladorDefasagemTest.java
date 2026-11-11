package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static br.unipampa.tcc.controle.Roteiro.perfis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-3 (B-28.2): terceiro sinal do nucleo, a defasagem de replicacao S_lag (plano de execucao de
 * 2026-11-04, secao 4.5). Entra como a latencia entra: R2 com k janelas altas seguidas, R3 zera as
 * calmas, R4 exige defasagem calma ou indeterminada. A assinatura antiga {@code avaliar(janela,
 * topologia)} delega com defasagem indeterminada.
 *
 * <p>Roteiro de duas letras por janela (janela e topologia de {@link Roteiro}, defasagem aqui):
 * {@code A} alta, {@code k} calma, {@code m} intermediaria (entre lag_out e lag_in), {@code -}
 * indeterminada. Ex.: {@code "cA cA"} = duas janelas calmas com defasagem alta.</p>
 */
class ControladorDefasagemTest {

    private static final ParametrosControlador P = ParametrosControlador.padrao();

    static final EstadoDefasagem ALTA = new EstadoDefasagem(EstadoDefasagem.Nivel.ALTA, "isn3", 2000.0, 6);
    static final EstadoDefasagem CALMA = new EstadoDefasagem(EstadoDefasagem.Nivel.CALMA, null, 3.0, 6);
    static final EstadoDefasagem MEIO = new EstadoDefasagem(EstadoDefasagem.Nivel.INTERMEDIARIA, "isn2", 40.0, 6);

    static EstadoDefasagem lag(char c) {
        return switch (c) {
            case 'A' -> ALTA;
            case 'k' -> CALMA;
            case 'm' -> MEIO;
            case '-' -> EstadoDefasagem.indeterminada();
            default -> throw new IllegalArgumentException("letra de defasagem desconhecida: " + c);
        };
    }

    /** Pares (janela, defasagem); espacos ignorados. */
    static List<Decisao> rodar(ParametrosControlador p, String roteiro) {
        String s = roteiro.replace(" ", "");
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("roteiro com numero impar de letras: " + roteiro);
        }
        Controlador c = new Controlador(p);
        List<Decisao> r = new ArrayList<>();
        for (int i = 0; i < s.length() / 2; i++) {
            Roteiro.Passo x = Roteiro.passo(s.charAt(2 * i), i, p.wNs());
            r.add(c.avaliar(x.janela(), x.topologia(), lag(s.charAt(2 * i + 1))));
        }
        return r;
    }

    static String calmas(int n, char lag) {
        return ("c" + lag).repeat(n);
    }

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of("R2: defasagem alta em j e j-1 entra", "c- cA cA", "NNC"),
                Arguments.of("R2: defasagem alta isolada nao entra (k = 2)", "cA ck cA ck cA", "NNNNN"),
                Arguments.of("R2: intermediaria nao conta como alta", "cm cm cm cm", "NNNN"),
                Arguments.of("R2: indeterminada nao conta como alta", "c- c- c- c-", "NNNN"),
                Arguments.of("R2: defasagem e latencia alternadas nao somam persistencia", "LA ck cA Lk", "NNNN"),
                Arguments.of("R2: cooldown bloqueia a defasagem; entra quando expira",
                        "T- " + calmas(6, '-') + calmas(6, 'A'),
                        "CCCCCCN NNNNNC"),
                Arguments.of("R4: defasagem indeterminada nao impede a saida", "T- " + calmas(6, '-'), "CCCCCCN"),
                Arguments.of("R4: defasagem calma nao impede a saida", "Tk " + calmas(6, 'k'), "CCCCCCN"),
                Arguments.of("R4: intermediaria nao e calma e adia a saida",
                        "Tk ck ck cm " + calmas(6, 'k'), "CCCCCCCCCN"),
                Arguments.of("R3: defasagem alta em CONSERVADOR zera as calmas e adia a saida",
                        "Tk ck ck cA " + calmas(6, 'k'), "CCCCCCCCCN"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void regrasComDefasagem(String nome, String roteiro, String esperado) {
        assertEquals(esperado.replace(" ", ""), perfis(rodar(P, roteiro)), nome);
    }

    @Test
    @DisplayName("R2 por defasagem: regra R2, motivo DEFASAGEM, persistencia e no suspeito nos sinais")
    void entradaPorDefasagem() {
        List<Decisao> ds = rodar(P, "cA cA");
        Decisao d0 = ds.get(0);
        assertEquals(Decisao.Regra.NENHUMA, d0.regra());
        assertEquals(List.of(Motivo.DEFASAGEM), d0.motivos());
        assertEquals(1, d0.sinais().persistenciaDefasagem());
        assertTrue(d0.sinais().defasagemAlta());
        Decisao d1 = ds.get(1);
        assertTrue(d1.transicao());
        assertEquals(Decisao.Regra.R2, d1.regra());
        assertEquals(Motivo.DEFASAGEM, d1.severidade());
        assertEquals(2, d1.sinais().persistenciaDefasagem());
        assertSame(ALTA, d1.sinais().defasagem());
        assertEquals("isn3", d1.sinais().defasagem().noSuspeito());
    }

    @Test
    @DisplayName("R3 por defasagem: regra R3 e janelas calmas zeradas")
    void r3PorDefasagem() {
        List<Decisao> ds = rodar(P, "Tk ck ck cA");
        assertEquals(2, ds.get(2).sinais().janelasCalmas());
        assertEquals(Decisao.Regra.R3, ds.get(3).regra());
        assertEquals(0, ds.get(3).sinais().janelasCalmas());
        assertEquals(List.of(Motivo.DEFASAGEM), ds.get(3).motivos());
    }

    @Test
    @DisplayName("motivos em ordem de severidade: VIOLACAO, TOPOLOGIA, DEFASAGEM, ERRO, LATENCIA")
    void ordemDosMotivos() {
        Controlador c = new Controlador(P);
        Janela j = new Janela(0, P.wNs(), 1000, 50, 20, 1000, 100, 1, 0);
        Decisao d = c.avaliar(j, Roteiro.TOP_ANOMALA, ALTA);
        assertEquals(List.of(Motivo.VIOLACAO, Motivo.TOPOLOGIA, Motivo.DEFASAGEM, Motivo.ERRO, Motivo.LATENCIA),
                d.motivos());
        assertTrue(Motivo.TOPOLOGIA.maisSeveroQue(Motivo.DEFASAGEM));
        assertTrue(Motivo.DEFASAGEM.maisSeveroQue(Motivo.ERRO));
    }

    @Test
    @DisplayName("assinatura antiga = nova com defasagem indeterminada (1000 sequencias de semente fixa)")
    void assinaturaAntigaDelega() {
        Random rnd = new Random(20261104L);
        String letras = "cLlEeBopbqawxyznViTU";
        for (int s = 0; s < 1000; s++) {
            Controlador antigo = new Controlador(P);
            Controlador novo = new Controlador(P);
            int n = 20 + rnd.nextInt(80);
            for (int i = 0; i < n; i++) {
                Roteiro.Passo x = Roteiro.passo(letras.charAt(rnd.nextInt(letras.length())), i, P.wNs());
                Decisao a = antigo.avaliar(x.janela(), x.topologia());
                Decisao b = novo.avaliar(x.janela(), x.topologia(), EstadoDefasagem.indeterminada());
                assertEquals(b, a, "sequencia " + s + " janela " + i);
                assertFalse(a.sinais().defasagemAlta());
                assertEquals(0, a.sinais().persistenciaDefasagem());
            }
        }
    }

    @Test
    @DisplayName("EstadoDefasagem: indeterminada e neutra; valida nivel, amostras e lag")
    void estadoDefasagem() {
        EstadoDefasagem i = EstadoDefasagem.indeterminada();
        assertEquals(EstadoDefasagem.Nivel.INDETERMINADA, i.nivel());
        assertSame(i, EstadoDefasagem.indeterminada());
        assertNull(i.noSuspeito());
        assertNull(i.lagMaxMs());
        assertEquals(0, i.amostras());
        assertFalse(i.alta());
        assertTrue(i.calmaOuIndeterminada());
        assertTrue(CALMA.calmaOuIndeterminada());
        assertFalse(MEIO.calmaOuIndeterminada());
        assertFalse(MEIO.alta());
        assertTrue(ALTA.alta());
        assertFalse(ALTA.calmaOuIndeterminada());
        assertThrows(NullPointerException.class, () -> new EstadoDefasagem(null, null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EstadoDefasagem(EstadoDefasagem.Nivel.CALMA, null, 1.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new EstadoDefasagem(EstadoDefasagem.Nivel.CALMA, null, -1.0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EstadoDefasagem(EstadoDefasagem.Nivel.CALMA, null, Double.NaN, 1));
    }

    @Test
    @DisplayName("avaliar com defasagem nula e rejeitado sem alterar o estado")
    void defasagemNula() {
        Controlador c = new Controlador(P);
        Roteiro.Passo x = Roteiro.passo('c', 0, P.wNs());
        assertThrows(NullPointerException.class, () -> c.avaliar(x.janela(), x.topologia(), null));
        assertEquals(Perfil.NORMAL, c.avaliar(x.janela(), x.topologia(), CALMA).perfil());
    }
}
