package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.2, T1: identidades da mistura por thread. Em v2 o Login roda num pool proprio de 8
 * identidades por thread ({@code user-<tid>-<k>}, nunca recebe O4/O5) e O4/O5 vao para
 * {@code lim-<tid>}; em v1 tudo continua em {@code user-<tid>} (C1 do diagnostico).
 */
class IdentidadesMisturaTest {

    @Test
    @DisplayName("v2: login em rodizio user-<tid>-0..7")
    void rodizioV2() {
        IdentidadesMistura ids = new IdentidadesMistura(3, VersaoMistura.V2);
        assertEquals(8, IdentidadesMistura.POOL_LOGIN_POR_THREAD);
        for (int volta = 0; volta < 3; volta++) {
            for (int k = 0; k < 8; k++) {
                assertEquals("user-3-" + k, ids.login());
            }
        }
    }

    @Test
    @DisplayName("v2: limite em lim-<tid>, sempre o mesmo, e nao avanca o rodizio")
    void limiteV2() {
        IdentidadesMistura ids = new IdentidadesMistura(5, VersaoMistura.V2);
        assertEquals("user-5-0", ids.login());
        assertEquals("lim-5", ids.limite());
        assertEquals("lim-5", ids.limite());
        assertEquals("user-5-1", ids.login());
    }

    @Test
    @DisplayName("v2: disjuncao para tid < 64 (login nunca e limite nem user-<tid>; pools de threads distintas nao se cruzam)")
    void disjuncao() {
        Set<String> logins = new HashSet<>();
        Set<String> limites = new HashSet<>();
        Set<String> legados = new HashSet<>();
        for (int tid = 0; tid < 64; tid++) {
            IdentidadesMistura ids = new IdentidadesMistura(tid, VersaoMistura.V2);
            for (int k = 0; k < 8; k++) {
                assertTrue(logins.add(ids.login()), "login repetido entre threads");
            }
            limites.add(ids.limite());
            legados.add("user-" + tid);
        }
        assertEquals(64 * 8, logins.size());
        assertEquals(64, limites.size());
        for (String l : logins) {
            assertFalse(limites.contains(l), l);
            assertFalse(legados.contains(l), l);
        }
    }

    @Test
    @DisplayName("v1: login = limite = user-<tid> (comportamento anterior)")
    void v1() {
        IdentidadesMistura ids = new IdentidadesMistura(2, VersaoMistura.V1);
        for (int i = 0; i < 20; i++) {
            assertEquals("user-2", ids.login());
            assertEquals("user-2", ids.limite());
        }
    }

    @Test
    @DisplayName("identidadeDaCarga cobre exatamente as 8 x threads identidades de login")
    void identidadeDaCarga() {
        for (int threads : List.of(1, 4, 8)) {
            Set<String> esperadas = new HashSet<>();
            for (int tid = 0; tid < threads; tid++) {
                IdentidadesMistura ids = new IdentidadesMistura(tid, VersaoMistura.V2);
                for (int k = 0; k < 8; k++) {
                    esperadas.add(ids.login());
                }
            }
            Set<String> carga = new HashSet<>();
            for (int i = 1; i <= KeyGenerator.TAMANHO_PADRAO_UNIVERSO; i++) {
                carga.add(IdentidadesMistura.identidadeDaCarga(i, threads));
            }
            assertEquals(esperadas, carga, "threads=" + threads);
        }
        assertEquals("user-0-0", IdentidadesMistura.identidadeDaCarga(1, 4));
        assertEquals("user-3-0", IdentidadesMistura.identidadeDaCarga(4, 4));
        assertEquals("user-0-1", IdentidadesMistura.identidadeDaCarga(5, 4));
        assertEquals("user-0-0", IdentidadesMistura.identidadeDaCarga(33, 4));
        assertThrows(IllegalArgumentException.class, () -> IdentidadesMistura.identidadeDaCarga(0, 4));
        assertThrows(IllegalArgumentException.class, () -> IdentidadesMistura.identidadeDaCarga(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new IdentidadesMistura(-1, VersaoMistura.V2));
    }

    @Test
    @DisplayName("rotulos v1/v2 e conversao estrita")
    void rotulos() {
        assertEquals("v1", VersaoMistura.V1.rotulo());
        assertEquals("v2", VersaoMistura.V2.rotulo());
        assertEquals(VersaoMistura.V2, VersaoMistura.porRotulo("v2"));
        assertEquals(VersaoMistura.V1, VersaoMistura.porRotulo("v1"));
        for (String v : new String[] {"V2", "2", "", " v2", "v4"}) {
            assertThrows(IllegalArgumentException.class, () -> VersaoMistura.porRotulo(v), v);
        }
        assertThrows(IllegalArgumentException.class, () -> VersaoMistura.porRotulo(null));
    }
}
