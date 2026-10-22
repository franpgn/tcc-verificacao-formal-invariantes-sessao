package br.unipampa.tcc.session;

/**
 * B-43.2 (diagnostico de 2026-10-22, causa C1): identidades usadas por uma thread de carga S1/S2.
 * Uma instancia por thread; sem I/O e sem {@code Random} (o rodizio NAO consome o gerador da
 * mistura nem o {@link KeyGenerator}, entao a sequencia de operacoes e de chaves por semente nao
 * muda).
 *
 * <ul>
 *   <li>{@link VersaoMistura#V2}: {@link #login()} devolve {@code user-<tid>-<k>} em rodizio,
 *       {@code k = 0..POOL_LOGIN_POR_THREAD-1}; essas identidades nunca recebem O4/O5, logo nunca
 *       bloqueiam. {@link #limite()} devolve {@code lim-<tid>}, que recebe O4/O5 (o bloqueio por
 *       limite continua exercitado).</li>
 *   <li>{@link VersaoMistura#V1}: {@link #login()} = {@link #limite()} = {@code user-<tid>}
 *       (comportamento anterior; bloqueio absorvente C1).</li>
 * </ul>
 */
final class IdentidadesMistura {

    /** Tamanho do pool de login por thread em v2 (decisao P-e do Gestor, 2026-10-22). */
    static final int POOL_LOGIN_POR_THREAD = 8;

    private final String[] logins;
    private final String limite;
    private int proximo;

    IdentidadesMistura(int tid, VersaoMistura versao) {
        if (tid < 0) {
            throw new IllegalArgumentException("tid deve ser >= 0: " + tid);
        }
        if (versao == VersaoMistura.V2) {
            logins = new String[POOL_LOGIN_POR_THREAD];
            for (int k = 0; k < POOL_LOGIN_POR_THREAD; k++) {
                logins[k] = nomeLogin(tid, k);
            }
            limite = "lim-" + tid;
        } else {
            limite = "user-" + tid;
            logins = new String[] {limite};
        }
    }

    /** Identidade do proximo Login (rodizio em v2). */
    String login() {
        String s = logins[proximo];
        proximo = (proximo + 1) % logins.length;
        return s;
    }

    /** Identidade de IncrementFailure (O4) e ResetFailures (O5). */
    String limite() {
        return limite;
    }

    /**
     * Identidade dona da sessao {@code sid-<i>} na carga inicial (i &ge; 1): {@code user-<(i-1) mod
     * threads>-<((i-1) / threads) mod POOL_LOGIN_POR_THREAD>}. Com {@code universo >= 8 x threads}
     * cobre exatamente as identidades de login de v2.
     */
    static String identidadeDaCarga(int i, int threads) {
        if (i < 1) {
            throw new IllegalArgumentException("i deve ser >= 1: " + i);
        }
        if (threads < 1) {
            throw new IllegalArgumentException("threads deve ser >= 1: " + threads);
        }
        return nomeLogin((i - 1) % threads, ((i - 1) / threads) % POOL_LOGIN_POR_THREAD);
    }

    private static String nomeLogin(int tid, int k) {
        return "user-" + tid + "-" + k;
    }
}
