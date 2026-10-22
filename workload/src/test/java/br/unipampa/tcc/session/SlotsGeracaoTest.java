package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B-43.7, T1: formato e inverso de {@link SlotsGeracao#chave}, geracoes iniciais, faixas e identidade. */
class SlotsGeracaoTest {

    @Test
    @DisplayName("T1: chave(k,0) = sid-NNNNNN (formato do KeyGenerator); g >= 1 => sid-NNNNNN~g; slotDe inverte")
    void formatoEInverso() {
        assertEquals("sid-000001", SlotsGeracao.chave(1, 0));
        assertEquals(String.format("%s%06d", KeyGenerator.PREFIXO_SID, 42), SlotsGeracao.chave(42, 0));
        assertEquals("sid-000042~1", SlotsGeracao.chave(42, 1));
        assertEquals("sid-100000~12", SlotsGeracao.chave(100_000, 12));
        for (int k : new int[] {1, 7, 4096, 100_000}) {
            for (int g : new int[] {0, 1, 12}) {
                String c = SlotsGeracao.chave(k, g);
                assertEquals(k, SlotsGeracao.slotDe(c), c);
                assertEquals(g, SlotsGeracao.geracaoDe(c), c);
            }
        }
        KeyGenerator kg = new KeyGenerator(42);
        for (int i = 0; i < 1000; i++) {
            String sid = kg.nextSessionKey();
            assertEquals(sid, SlotsGeracao.chave(SlotsGeracao.slotDe(sid), 0));
        }
        assertThrows(IllegalArgumentException.class, () -> SlotsGeracao.chave(1, -1));
        assertThrows(IllegalArgumentException.class, () -> SlotsGeracao.chave(0, 1));
        assertThrows(IllegalArgumentException.class, () -> SlotsGeracao.slotDe("user-0-0"));
        assertThrows(IllegalArgumentException.class, () -> SlotsGeracao.slotDe("sid-abc"));
    }

    @Test
    @DisplayName("T1: todas as geracoes comecam em 1; definirGeracao muda so o slot dado")
    void geracoesIniciais() {
        SlotsGeracao s = new SlotsGeracao(1000, 4);
        for (int k = 1; k <= 1000; k++) {
            assertEquals(1, s.geracao(k), "slot " + k);
        }
        s.definirGeracao(7, 2);
        assertEquals(2, s.geracao(7));
        assertEquals(1, s.geracao(6));
        assertEquals(1, s.geracao(8));
        assertEquals(1000, s.universo());
        assertEquals(4, s.threads());
    }

    @Test
    @DisplayName("T1: 1024 faixas de trava (k mod 1024)")
    void faixas() {
        SlotsGeracao s = new SlotsGeracao(100_000, 4);
        assertEquals(1024, SlotsGeracao.FAIXAS);
        Map<Object, Boolean> distintas = new IdentityHashMap<>();
        for (int k = 1; k <= 100_000; k++) {
            distintas.put(s.trava(k), Boolean.TRUE);
            assertSame(s.trava(k), s.trava(k + 1024 <= 100_000 ? k + 1024 : k));
        }
        assertEquals(1024, distintas.size());
        assertNotSame(s.trava(1), s.trava(2));
        assertSame(s.trava(1), s.trava(1025));
    }

    @Test
    @DisplayName("T1: a identidade do slot e a da carga e e a mesma em todas as geracoes")
    void identidade() {
        SlotsGeracao s = new SlotsGeracao(1000, 4);
        for (int k = 1; k <= 1000; k++) {
            assertEquals(IdentidadesMistura.identidadeDaCarga(k, 4), s.identidade(k));
            int g0 = s.geracao(k);
            s.definirGeracao(k, g0 + 5);
            assertEquals(IdentidadesMistura.identidadeDaCarga(k, 4), s.identidade(k));
        }
        assertEquals("user-0-0", s.identidade(1));
        assertEquals("user-3-0", s.identidade(4));
    }
}
