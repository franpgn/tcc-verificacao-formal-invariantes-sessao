package br.unipampa.tcc.session;

import java.util.Random;

/**
 * Gerador de chaves Zipfian para o workload de TCC.
 *
 * <p>Realiza o sampling de inteiros em [1, n] segundo a distribuição
 * Zipfian com parâmetro ρ (rho). A frequência relativa esperada de
 * cada chave k é proporcional a 1/k^ρ, normalizada pelo número
 * harmônico generalizado H(n, ρ).</p>
 *
 * <p>Parâmetros padrão (T5, T6, T8 da Tabela 1 do Cap. 3 §3.3.5 da
 * monografia):</p>
 * <ul>
 *   <li>Universo: 100.000 chaves distintas</li>
 *   <li>ρ = 0,99 (cauda longa típica de session stores)</li>
 *   <li>Prefixo: "sid-" + zero-padded a 6 dígitos</li>
 * </ul>
 *
 * <p>Implementação: algoritmo Rejection-Inversion de Hörmann e
 * Derflinger (1996), com construção em O(1) e amostragem em O(1)
 * esperado. Sem dependências externas. Determinístico dada uma
 * seed: a sequência produzida é reprodutível entre execuções, o
 * que é condição necessária para repetições calibradas conforme
 * T10 da Tabela 1.</p>
 *
 * <p>Esta classe não é thread-safe. Cada thread do {@code WorkloadMain}
 * deve receber sua própria instância com uma seed distinta
 * (tipicamente {@code seedBase + threadId}).</p>
 */
public final class KeyGenerator {

    public static final int TAMANHO_PADRAO_UNIVERSO = 100_000;
    public static final double RHO_PADRAO = 0.99;
    public static final String PREFIXO_SID = "sid-";

    private final int n;
    private final double rho;
    private final Random rng;

    private final double hIntegralX1;
    private final double hIntegralN;
    private final double s;

    /**
     * Cria gerador com universo, expoente e seed explícitos.
     *
     * @param universo número de chaves distintas (> 0)
     * @param rho      parâmetro Zipfian (> 0; típico 0,9 a 1,1)
     * @param seed     seed do gerador pseudo-aleatório subjacente
     */
    public KeyGenerator(int universo, double rho, long seed) {
        if (universo <= 0) {
            throw new IllegalArgumentException("universo deve ser positivo");
        }
        if (rho <= 0.0) {
            throw new IllegalArgumentException("rho deve ser positivo");
        }
        this.n = universo;
        this.rho = rho;
        this.rng = new Random(seed);
        this.hIntegralX1 = hIntegral(1.5) - 1.0;
        this.hIntegralN = hIntegral(n + 0.5);
        this.s = 2.0 - hIntegralInverse(hIntegral(2.5) - h(2.0));
    }

    /** Constrói com parâmetros padrão da Tabela 1 (100.000, ρ=0,99). */
    public KeyGenerator(long seed) {
        this(TAMANHO_PADRAO_UNIVERSO, RHO_PADRAO, seed);
    }

    public int universo() { return n; }
    public double rho() { return rho; }

    /**
     * Próximo inteiro Zipfian em [1, n].
     *
     * <p>Algoritmo Rejection-Inversion: amostra uma variável uniforme
     * no intervalo de integral acumulada, inverte para obter a
     * pré-imagem real, arredonda para o inteiro mais próximo e
     * aplica filtro de rejeição quando necessário. Esperado de
     * iterações por chamada é menor do que dois para ρ próximo
     * de 1.</p>
     */
    public int nextInt() {
        while (true) {
            double u = hIntegralN + rng.nextDouble() * (hIntegralX1 - hIntegralN);
            double x = hIntegralInverse(u);
            int k = (int) (x + 0.5);
            if (k < 1) {
                k = 1;
            } else if (k > n) {
                k = n;
            }
            if (k - x <= s || u >= hIntegral(k + 0.5) - h(k)) {
                return k;
            }
        }
    }

    /** Próxima chave de sessão como "sid-NNNNNN" (seis dígitos). */
    public String nextSessionKey() {
        return String.format("%s%06d", PREFIXO_SID, nextInt());
    }

    // --- Núcleo numérico (Hörmann & Derflinger 1996) ---

    private double hIntegral(double x) {
        double logX = Math.log(x);
        return helper2((1.0 - rho) * logX) * logX;
    }

    private double h(double x) {
        return Math.exp(-rho * Math.log(x));
    }

    private double hIntegralInverse(double x) {
        double t = x * (1.0 - rho);
        if (t < -1.0) {
            t = -1.0;
        }
        return Math.exp(helper1(t) * x);
    }

    /** ln(1 + x) / x, com expansão em série para x próximo de zero. */
    private static double helper1(double x) {
        if (Math.abs(x) > 1e-8) {
            return Math.log1p(x) / x;
        }
        return 1.0 - x * (0.5 - x * ((1.0 / 3.0) - 0.25 * x));
    }

    /** (e^x - 1) / x, com expansão em série para x próximo de zero. */
    private static double helper2(double x) {
        if (Math.abs(x) > 1e-8) {
            return Math.expm1(x) / x;
        }
        return 1.0 + x * (0.5 + x * ((1.0 / 6.0) + x * (1.0 / 24.0)));
    }
}
