package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27, K2 (ritmo) e K4 (backoff com jitter) no portao: NORMAL nao faz conta nenhuma; CONSERVADOR
 * aplica os fatores de {@link ParametrosKnobs}. O jitter e uma funcao pura de
 * (sementeJitter, ator, inicio da acao, tentativa).
 */
class PortaoDeEmissaoK2K4Test {

    private static final long MS = 1_000_000L;

    private static PortaoDeEmissao portao(PerfilAtual perfil, long semente) {
        return new PortaoDeEmissao(perfil, ParametrosKnobs.padrao(), 8, () -> 0L, ns -> { }, e -> { }, semente);
    }

    private static PerfilAtual conservador() {
        PerfilAtual p = new PerfilAtual();
        p.definir(Perfil.CONSERVADOR);
        return p;
    }

    @Test
    @DisplayName("K2 (S1/S2): pausa 0 em NORMAL e k2PausaConservadorNs em CONSERVADOR")
    void pausa() {
        PerfilAtual perfil = new PerfilAtual();
        PortaoDeEmissao p = portao(perfil, 1L);
        assertEquals(0L, p.pausaAposOperacaoNs());
        perfil.definir(Perfil.CONSERVADOR);
        assertEquals(ParametrosKnobs.padrao().k2PausaConservadorNs(), p.pausaAposOperacaoNs());
        perfil.definir(Perfil.NORMAL);
        assertEquals(0L, p.pausaAposOperacaoNs());
    }

    @Test
    @DisplayName("K2b: aplicarPensar em NORMAL devolve o argumento identico; em CONSERVADOR x1,5 com piso de 1 ms")
    void pensar() {
        PerfilAtual perfil = new PerfilAtual();
        PortaoDeEmissao p = portao(perfil, 1L);
        long[] casos = {0L, 1L, 999_999L, MS, 1_234_567L, (1L << 53) + 1, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long x : casos) {
            assertEquals(x, p.aplicarPensar(x), "NORMAL altera " + x);
        }
        perfil.definir(Perfil.CONSERVADOR);
        assertEquals(3 * MS, p.aplicarPensar(2 * MS));
        assertEquals(1_500 * MS, p.aplicarPensar(1_000 * MS));
        assertEquals(MS, p.aplicarPensar(100_000L), "piso de 1 ms");
        assertEquals(MS, p.aplicarPensar(1L), "piso de 1 ms");
        assertEquals(Math.round(1_234_567L * 1.5), p.aplicarPensar(1_234_567L));
    }

    @Test
    @DisplayName("K4b: com jitter 0,5 e fator 2 a espera fica em [base*2*0,5; base*2*1,5] e a media no IC de 99,9 % de base*2; NORMAL devolve a base")
    void intervalo() {
        PortaoDeEmissao normal = portao(new PerfilAtual(), 77L);
        PortaoDeEmissao cons = portao(conservador(), 77L);
        long base = 500 * MS;
        int n = 10_000;
        double soma = 0;
        double soma2 = 0;
        for (int i = 0; i < n; i++) {
            String ator = "s3-u-" + (i % 37);
            long inicio = 1_000_000_000_000L + i * 7_919L;
            int tentativa = 1 + (i % 3);
            assertEquals(base, normal.esperaRetentativaNs(ator, base, tentativa, inicio));
            long v = cons.esperaRetentativaNs(ator, base, tentativa, inicio);
            assertTrue(v >= Math.round(base * 2 * 0.5) && v <= Math.round(base * 2 * 1.5),
                    "fora do intervalo: " + v);
            soma += v;
            soma2 += (double) v * v;
        }
        double media = soma / n;
        double dp = Math.sqrt((soma2 - n * media * media) / (n - 1));
        double meia = 3.2905 * dp / Math.sqrt(n);
        assertTrue(Math.abs(media - base * 2.0) <= meia,
                "media " + media + " fora de base*2 +- " + meia);
    }

    @Test
    @DisplayName("K4c: mesma (semente, ator, inicio, tentativa) => mesmo valor; mudar qualquer um muda em >= 99 % de 1 000 casos")
    void determinismo() {
        PerfilAtual perfil = conservador();
        PortaoDeEmissao a = portao(perfil, 123L);
        PortaoDeEmissao b = portao(perfil, 123L);
        PortaoDeEmissao outraSemente = portao(perfil, 124L);
        long base = 500 * MS;
        int mudouSemente = 0;
        int mudouAtor = 0;
        int mudouInicio = 0;
        int mudouTentativa = 0;
        Set<Long> valores = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String ator = "s3-l-" + i;
            long inicio = 5_000_000_000L + i * 1_000_003L;
            int t = 1 + (i % 2);
            long v = a.esperaRetentativaNs(ator, base, t, inicio);
            assertEquals(v, a.esperaRetentativaNs(ator, base, t, inicio), "repetir a chamada muda o valor");
            assertEquals(v, b.esperaRetentativaNs(ator, base, t, inicio), "outra instancia com a mesma semente");
            valores.add(v);
            if (outraSemente.esperaRetentativaNs(ator, base, t, inicio) != v) mudouSemente++;
            if (a.esperaRetentativaNs(ator + "x", base, t, inicio) != v) mudouAtor++;
            if (a.esperaRetentativaNs(ator, base, t, inicio + 1) != v) mudouInicio++;
            if (a.esperaRetentativaNs(ator, base, t + 1, inicio) != v) mudouTentativa++;
        }
        assertTrue(mudouSemente >= 990, "semente: " + mudouSemente);
        assertTrue(mudouAtor >= 990, "ator: " + mudouAtor);
        assertTrue(mudouInicio >= 990, "inicio: " + mudouInicio);
        assertTrue(mudouTentativa >= 990, "tentativa: " + mudouTentativa);
        assertTrue(valores.size() >= 990, "valores distintos: " + valores.size());
    }

    @Test
    @DisplayName("K4: jitter 0 e fator 1 em CONSERVADOR devolvem a base exata")
    void semJitter() {
        ParametrosKnobs k = new ParametrosKnobs("t", 0.5, 0, 1.0, 1_000 * MS, 100 * MS, 1.0, 0.0, null);
        PortaoDeEmissao p = new PortaoDeEmissao(conservador(), k, 1, () -> 0L, ns -> { }, e -> { }, 5L);
        for (int i = 0; i < 100; i++) {
            assertEquals(500 * MS, p.esperaRetentativaNs("a" + i, 500 * MS, 1, i));
        }
        assertEquals(2 * MS, p.aplicarPensar(2 * MS), "multiplicador 1,0 em CONSERVADOR");
    }
}
