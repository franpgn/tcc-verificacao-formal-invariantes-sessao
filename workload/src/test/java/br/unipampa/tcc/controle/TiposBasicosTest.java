package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-28.1: tipos de valor do nucleo (Perfil, Motivo, Janela, EstadoTopologia). */
class TiposBasicosTest {

    private static final long S = 1_000_000_000L;

    @Test
    @DisplayName("Perfil tem exatamente NORMAL e CONSERVADOR")
    void perfis() {
        assertEquals(List.of(Perfil.NORMAL, Perfil.CONSERVADOR), List.of(Perfil.values()));
    }

    @Test
    @DisplayName("Motivo em ordem decrescente de severidade: VIOLACAO > TOPOLOGIA > DEFASAGEM > ERRO > LATENCIA > ESTAVEL")
    void ordemDeSeveridade() {
        assertEquals(List.of(Motivo.VIOLACAO, Motivo.TOPOLOGIA, Motivo.DEFASAGEM, Motivo.ERRO, Motivo.LATENCIA,
                        Motivo.ESTAVEL),
                List.of(Motivo.values()));
        assertTrue(Motivo.VIOLACAO.maisSeveroQue(Motivo.TOPOLOGIA));
        assertTrue(Motivo.ERRO.maisSeveroQue(Motivo.LATENCIA));
        assertFalse(Motivo.ESTAVEL.maisSeveroQue(Motivo.LATENCIA));
        assertFalse(Motivo.ERRO.maisSeveroQue(Motivo.ERRO));
    }

    @Test
    @DisplayName("Motivo.maisSevero escolhe o de maior severidade e devolve ESTAVEL para conjunto vazio")
    void maisSevero() {
        assertEquals(Motivo.TOPOLOGIA, Motivo.maisSevero(EnumSet.of(Motivo.LATENCIA, Motivo.TOPOLOGIA, Motivo.ERRO)));
        assertEquals(Motivo.VIOLACAO, Motivo.maisSevero(List.of(Motivo.LATENCIA, Motivo.VIOLACAO)));
        assertEquals(Motivo.ESTAVEL, Motivo.maisSevero(EnumSet.noneOf(Motivo.class)));
    }

    @Test
    @DisplayName("Janela valida guarda os campos")
    void janelaValida() {
        Janela j = new Janela(0, 5 * S, 1000, 30, 10, 900, 40, 1, 2);
        assertEquals(0, j.inicioNs());
        assertEquals(5 * S, j.fimNs());
        assertEquals(1000, j.total());
        assertEquals(30, j.erros());
        assertEquals(10, j.indisponiveis());
        assertEquals(900, j.elegiveisLat());
        assertEquals(40, j.excedencias());
        assertEquals(1, j.violacoesSeguranca());
        assertEquals(2, j.violacoesI2Esperadas());
    }

    @Test
    @DisplayName("Janela vazia (sem eventos) e valida")
    void janelaVazia() {
        Janela j = new Janela(0, 5 * S, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0, j.total());
    }

    @Test
    @DisplayName("Janela rejeita fim <= inicio, contagens negativas e contagens incoerentes")
    void janelaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new Janela(5 * S, 5 * S, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(6 * S, 5 * S, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, -1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, -1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 1, -1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 0, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 0, 0, 10, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 0, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 0, 0, 0, 0, 0, -1));
        // erros > total
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 11, 0, 0, 0, 0, 0));
        // indisponiveis (subconjunto de erros) > erros
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 2, 3, 0, 0, 0, 0));
        // elegiveis para latencia > total
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 0, 0, 11, 0, 0, 0));
        // excedencias > elegiveis
        assertThrows(IllegalArgumentException.class, () -> new Janela(0, 5 * S, 10, 0, 0, 5, 6, 0, 0));
    }

    @Test
    @DisplayName("EstadoTopologia: anomala so se disponivel e com pelo menos uma amostra anomala")
    void topologiaAnomala() {
        assertTrue(new EstadoTopologia(true, 3, 1, 2).anomala());
        assertFalse(new EstadoTopologia(true, 3, 0, 3).anomala());
        assertFalse(new EstadoTopologia(true, 0, 0, 0).anomala());
    }

    @Test
    @DisplayName("EstadoTopologia com disponivel=false e neutra, mesmo com amostras anomalas")
    void topologiaIndisponivelNeutra() {
        assertFalse(new EstadoTopologia(false, 3, 3, 0).anomala());
        assertFalse(EstadoTopologia.indisponivel().anomala());
        assertFalse(EstadoTopologia.indisponivel().disponivel());
    }

    @Test
    @DisplayName("EstadoTopologia rejeita contagens negativas e anomalas > amostras")
    void topologiaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia(true, -1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia(true, 3, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia(true, 3, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia(true, 2, 3, 3));
    }
}
