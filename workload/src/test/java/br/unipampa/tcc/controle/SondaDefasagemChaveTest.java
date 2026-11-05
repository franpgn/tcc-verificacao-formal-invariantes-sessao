package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4, P-40: a {@link SondaDefasagem} escreve e le a chave que o canal escolheu para cada no
 * ({@link CanalCanario#chave(int)}; no adaptador Hot Rod, uma chave com o proprio no como dono primario). Sem
 * escolha, a chave simples {@code lag:<rotulo>}. A sonda recusa chave sem o prefixo, repetida ou nula.
 */
class SondaDefasagemChaveTest {

    static final long MS = 1_000_000L;
    static final ParametrosDefasagem P = new ParametrosDefasagem(150, 20, 100);

    /** Canal falso com chaves escolhidas. */
    static CanalFalso comChaves(String... chaves) {
        return new CanalFalso(3) {
            @Override
            public String chave(int indice) {
                return chaves[indice];
            }

            @Override
            public Boolean chaveNoPrimario(int indice) {
                return Boolean.TRUE;
            }
        };
    }

    @Test
    @DisplayName("a sonda usa as chaves do canal (lag:<no>#<j>) para escrever e ler, e mais nenhuma")
    void usaAsChavesDoCanal() {
        CanalFalso c = comChaves("lag:no0#3", "lag:no1", "lag:no2#17");
        try (SondaDefasagem s = new SondaDefasagem(c, P, 400, 10_000 * MS, 0, () -> 1, r -> { }, "teste")) {
            assertEquals(List.of("lag:no0#3", "lag:no1", "lag:no2#17"), s.chaves());
            RodadaDefasagem r = s.rodada();
            assertTrue(r.pares().stream().allMatch(RodadaDefasagem.Par::visto));
            Set<String> esperadas = Set.of("lag:no0#3", "lag:no1", "lag:no2#17");
            assertEquals(esperadas, new HashSet<>(c.chavesEscritas));
            assertEquals(esperadas, new HashSet<>(c.chavesLidas));
        }
    }

    @Test
    @DisplayName("sem escolha: a chave simples lag:<rotulo> e primario desconhecido (null)")
    void padraoDoCanal() {
        CanalFalso c = new CanalFalso(3);
        assertEquals("lag:no1", c.chave(1));
        assertNull(c.chaveNoPrimario(1));
        assertNull(c.motivoDaChave(1));
        assertEquals(CanalCanario.PREFIXO_CHAVE, SondaDefasagem.PREFIXO_CHAVE);
    }

    @Test
    @DisplayName("chave sem o prefixo lag:, repetida ou nula e recusada no construtor")
    void chaveInvalida() {
        FonteDeTempo t = () -> 1;
        assertThrows(IllegalArgumentException.class, () -> new SondaDefasagem(comChaves("x:no0", "lag:no1", "lag:no2"),
                P, 400, 10_000 * MS, 0, t, r -> { }, "teste"));
        assertThrows(IllegalArgumentException.class, () -> new SondaDefasagem(comChaves("lag:a", "lag:a", "lag:no2"),
                P, 400, 10_000 * MS, 0, t, r -> { }, "teste"));
        assertThrows(IllegalArgumentException.class, () -> new SondaDefasagem(comChaves(null, "lag:no1", "lag:no2"),
                P, 400, 10_000 * MS, 0, t, r -> { }, "teste"));
    }
}
