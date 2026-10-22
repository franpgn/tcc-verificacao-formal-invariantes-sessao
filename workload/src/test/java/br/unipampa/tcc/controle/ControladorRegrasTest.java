package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static br.unipampa.tcc.controle.Roteiro.S;
import static br.unipampa.tcc.controle.Roteiro.comKm;
import static br.unipampa.tcc.controle.Roteiro.comTempos;
import static br.unipampa.tcc.controle.Roteiro.perfis;
import static br.unipampa.tcc.controle.Roteiro.rep;
import static br.unipampa.tcc.controle.Roteiro.rodar;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B-28.1: casos em tabela para as regras R1..R4 (plano de 2026-10-22, secao 3.3). Cada caso e um
 * roteiro (uma letra por janela de 5 s, ver {@link Roteiro}) e o perfil esperado apos cada janela.
 * Com os parametros padrao: cooldown = 6 janelas, m = 6, T_estavel = 6 janelas, T_hold_viol = 24.
 */
class ControladorRegrasTest {

    private static final ParametrosControlador P = ParametrosControlador.padrao();

    static Stream<Arguments> r1() {
        return Stream.of(
                Arguments.of("violacao em NORMAL entra imediatamente", "cV", "NC"),
                Arguments.of("violacao na primeira janela entra", "V", "C"),
                Arguments.of("violacao durante o cooldown entra (R1 ignora cooldown)",
                        "Tcccccc V", "CCCCCCN C"),
                Arguments.of("I2 esperada nao e violacao e nao decide", "iiiiii", "NNNNNN"),
                // V em w0; V de novo em w20 renova ultimaViolacao; saida so em fim >= fim(w20) + 120 s = w44
                Arguments.of("violacao em CONSERVADOR mantem e renova o hold",
                        "V" + rep('c', 19) + "V" + rep('c', 24), rep('C', 44) + "N"));
    }

    static Stream<Arguments> r2() {
        return Stream.of(
                Arguments.of("topologia anomala entra sem persistencia", "cT", "NC"),
                Arguments.of("latencia alta em j e j-1 entra", "cLL", "NNC"),
                Arguments.of("erro alto em j e j-1 entra", "cEE", "NNC"),
                Arguments.of("latencia e erro altos juntos em j e j-1 entram", "BB", "NC"),
                Arguments.of("latencia alta isolada nao entra (k = 2)", "LcLcL", "NNNNN"),
                Arguments.of("erro alto isolado nao entra (k = 2)", "EcEcE", "NNNNN"),
                Arguments.of("latencia em j-1 e erro em j nao contam como persistencia", "LELE", "NNNN"),
                Arguments.of("exatamente theta_in com amostra exatamente minima entra", "bb", "NC"),
                // S1 (V-B28.1): so a latencia esta em theta_lat_in exato; o erro e 0 e nao dispara R2
                Arguments.of("latencia exatamente theta_lat_in, com erro calmo, entra sozinha", "qq", "NC"),
                Arguments.of("logo abaixo de theta_in nao entra", "aaaaaa", "NNNNNN"),
                Arguments.of("erro com total exatamente n_err = 50 entra", "ww", "NC"),
                Arguments.of("cooldown bloqueia topologia; entra quando expira",
                        "Tcccccc Tcccc T", "CCCCCCN NNNNN C"),
                Arguments.of("cooldown bloqueia persistencia; entra quando expira",
                        "Tcccccc LLLLLL", "CCCCCCN NNNNNC"),
                Arguments.of("depois da saida, uma janela alta isolada nao reentra", "Tcccccc Lc", "CCCCCCN NN"));
    }

    static Stream<Arguments> amostraInsuficiente() {
        return Stream.of(
                Arguments.of("10 eventos, todos com erro e excedencia: indeterminado", "xxxxxx", "NNNNNN"),
                Arguments.of("49 eventos (n_err - 1) e 49 elegiveis: indeterminado", "yyyyyy", "NNNNNN"),
                Arguments.of("99 elegiveis (n_lat - 1) todos excedendo: indeterminado", "zzzzzz", "NNNNNN"),
                Arguments.of("janelas vazias: indeterminado", "nnnnnn", "NNNNNN"),
                Arguments.of("topologia indisponivel e neutra, mesmo declarando anomalas", "UUUUUU", "NNNNNN"),
                Arguments.of("em CONSERVADOR, amostra insuficiente nao impede a saida", "Txxxxxx", "CCCCCCN"),
                Arguments.of("em CONSERVADOR, janela vazia nao impede a saida", "Tnnnnnn", "CCCCCCN"),
                Arguments.of("em CONSERVADOR, topologia indisponivel nao impede a saida", "TUUUUUU", "CCCCCCN"));
    }

    static Stream<Arguments> r3() {
        return Stream.of(
                Arguments.of("latencia alta isolada zera o contador de calmas",
                        "Tccccc L cccccc", "CCCCCC C CCCCCN"),
                Arguments.of("erro alto isolado zera o contador de calmas",
                        "Tccccc E cccccc", "CCCCCC C CCCCCN"),
                Arguments.of("topologia anomala zera o contador de calmas",
                        "Tccccc T cccccc", "CCCCCC C CCCCCN"),
                // Interpretacao registrada no relatorio: "m janelas calmas seguidas" exige que todas
                // sejam calmas; uma janela intermediaria (nem alta, nem calma) quebra a sequencia.
                Arguments.of("janela intermediaria quebra a sequencia de m calmas seguidas",
                        "Tccccc l cccccc", "CCCCCC C CCCCCN"));
    }

    static Stream<Arguments> r4() {
        return Stream.of(
                Arguments.of("sai apos m = 6 janelas calmas e cooldown expirado", "Tcccccc", "CCCCCCN"),
                Arguments.of("nao sai com m - 1 janelas calmas", "Tccccc", "CCCCCC"),
                Arguments.of("exatamente theta_out conta como calma", "Toooooo", "CCCCCCN"),
                Arguments.of("logo acima de theta_lat_out nao e calma", "Tpppppppppp", rep('C', 11)),
                Arguments.of("hold de violacao: so sai 120 s depois da violacao",
                        "V" + rep('c', 24), rep('C', 24) + "N"),
                Arguments.of("depois de sair, volta a NORMAL e fica", "Tcccccc cccccc", "CCCCCCN NNNNNN"));
    }

    @ParameterizedTest(name = "R1: {0}")
    @MethodSource("r1")
    void regraR1(String nome, String roteiro, String esperado) {
        verificar(P, roteiro, esperado);
    }

    @ParameterizedTest(name = "R2: {0}")
    @MethodSource("r2")
    void regraR2(String nome, String roteiro, String esperado) {
        verificar(P, roteiro, esperado);
    }

    @ParameterizedTest(name = "amostra insuficiente: {0}")
    @MethodSource("amostraInsuficiente")
    void amostraInsuficiente(String nome, String roteiro, String esperado) {
        verificar(P, roteiro, esperado);
    }

    @ParameterizedTest(name = "R3: {0}")
    @MethodSource("r3")
    void regraR3(String nome, String roteiro, String esperado) {
        verificar(P, roteiro, esperado);
    }

    @ParameterizedTest(name = "R4: {0}")
    @MethodSource("r4")
    void regraR4(String nome, String roteiro, String esperado) {
        verificar(P, roteiro, esperado);
    }

    static Stream<Arguments> parametrosAlternativos() {
        return Stream.of(
                // T_estavel = 60 s > m*W: m calmas nao bastam, a topologia segura a saida ate w12
                Arguments.of("saida bloqueada por topologia instavel (T_estavel = 60 s)",
                        comTempos(30 * S, 60 * S, 120 * S), "Tcccccc cccccc", "CCCCCCC CCCCCN"),
                // anomalia em w3 (dentro de CONSERVADOR): fim(w3) = 20 s; estavel quando fim >= 80 s, isto e, w15
                Arguments.of("anomalia mais recente define T_estavel",
                        comTempos(30 * S, 60 * S, 120 * S), "TccT" + rep('c', 13), rep('C', 15) + "NN"),
                Arguments.of("saida bloqueada pelo cooldown (T_cooldown = 60 s)",
                        comTempos(60 * S, 30 * S, 120 * S), "Tcccccc cccccc", "CCCCCCC CCCCCN"),
                Arguments.of("hold zero: violacao entra e sai como topologia",
                        comTempos(30 * S, 30 * S, 0), "Vcccccc", "CCCCCCN"),
                Arguments.of("k = 1: uma janela alta basta", comKm(1, 6), "cL", "NC"),
                Arguments.of("k = 3: duas janelas altas nao bastam, tres sim", comKm(3, 6), "LLcLLL", "NNNNNC"),
                // w5 intermediaria quebra a sequencia; com m = 1, so w6 calma ja basta (com m = 6 ficaria em C)
                Arguments.of("m = 1: uma janela calma basta (com cooldown expirado)",
                        comKm(2, 1), "Tccccl c", "CCCCCC N"),
                Arguments.of("m = 6 no mesmo roteiro nao sai", comKm(2, 6), "Tccccl c", "CCCCCC C"),
                // S12 (V-B28.1): com T_estavel = 0 so o contador de calmas segura a saida; a anomalia
                // em w6 zera o contador e a saida vem em w12 (se nao zerasse, sairia em w7)
                Arguments.of("topologia anomala zera o contador de calmas (T_estavel = 0)",
                        comTempos(30 * S, 0, 120 * S), "Tccccc T cccccc", "CCCCCC C CCCCCN"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parametrosAlternativos")
    @DisplayName("regras com parametros alternativos")
    void comParametros(String nome, ParametrosControlador p, String roteiro, String esperado) {
        verificar(p, roteiro, esperado);
    }

    @Test
    @DisplayName("S12: janela com topologia anomala em CONSERVADOR registra janelasCalmas = 0")
    void topologiaAnomalaZeraCalmasNosSinais() {
        List<Decisao> ds = rodar(comTempos(30 * S, 0, 120 * S), "Tccccc T cccccc");
        assertEquals(5, ds.get(5).sinais().janelasCalmas(), "w5: cinco calmas seguidas");
        assertEquals(Decisao.Regra.R3, ds.get(6).regra(), "w6: topologia anomala e R3");
        assertEquals(0, ds.get(6).sinais().janelasCalmas(), "w6: topologia anomala zera as calmas");
        assertEquals(1, ds.get(7).sinais().janelasCalmas(), "w7: recomeca do zero");
    }

    private static void verificar(ParametrosControlador p, String roteiro, String esperado) {
        List<Decisao> ds = rodar(p, roteiro);
        assertEquals(esperado.replace(" ", ""), perfis(ds), "roteiro " + roteiro);
    }
}
