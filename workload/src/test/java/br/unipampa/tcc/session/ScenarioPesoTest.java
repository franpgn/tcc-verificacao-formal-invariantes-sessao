package br.unipampa.tcc.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** B-43.3a (parte i): acessor de leitura {@link Scenario#peso(Scenario.Op)}. */
class ScenarioPesoTest {

    @Test
    void pesosDeS1() {
        assertEquals(50, Scenario.S1.peso(Scenario.Op.VALIDATE));
        assertEquals(25, Scenario.S1.peso(Scenario.Op.LOGIN_VALIDATE_LOGOUT));
        assertEquals(10, Scenario.S1.peso(Scenario.Op.LOGOUT));
        assertEquals(10, Scenario.S1.peso(Scenario.Op.INCREMENT_FAILURE));
        assertEquals(5, Scenario.S1.peso(Scenario.Op.RESET_FAILURES));
    }

    @Test
    void pesosDeS2() {
        assertEquals(95, Scenario.S2.peso(Scenario.Op.VALIDATE));
        assertEquals(3, Scenario.S2.peso(Scenario.Op.LOGIN_VALIDATE_LOGOUT));
        assertEquals(1, Scenario.S2.peso(Scenario.Op.LOGOUT));
        assertEquals(1, Scenario.S2.peso(Scenario.Op.INCREMENT_FAILURE));
        assertEquals(0, Scenario.S2.peso(Scenario.Op.RESET_FAILURES));
    }

    @Test
    void somaCem() {
        for (Scenario s : Scenario.values()) {
            int soma = 0;
            for (Scenario.Op op : Scenario.Op.values()) {
                soma += s.peso(op);
            }
            assertEquals(100, soma, s.name());
        }
    }
}
