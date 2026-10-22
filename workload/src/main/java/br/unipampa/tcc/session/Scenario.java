package br.unipampa.tcc.session;

import java.util.Random;

/**
 * Cenários de mistura de operações reportados em T7 da Tabela 1 do
 * Cap. 3 §3.3.5 da monografia.
 *
 * <ul>
 *   <li>{@link #S1}: 50/50 leituras/escritas, modela um session store
 *       de escrita ativa (referência YCSB Workload A).</li>
 *   <li>{@link #S2}: 95/5 leituras/escritas, isola o comportamento de
 *       validação de sessão (referência YCSB Workload B).</li>
 * </ul>
 *
 * <p>Cada cenário decide a operação a executar consultando um
 * gerador uniforme em [0, 100). A divisão interna das escritas em
 * O1/O3/O4/O5 é proporcional aos pesos relativos típicos de uma
 * carga de gerência de sessão (login predomina sobre logout, que
 * predomina sobre operações administrativas).</p>
 */
public enum Scenario {

    S1(50, 25, 10, 10, 5),
    S2(95, 3, 1, 1, 0);

    /** Tipos de operação produzidas pelo seletor. */
    public enum Op {
        VALIDATE,           // O2
        LOGIN_VALIDATE_LOGOUT, // O1 + O2 + O3 (tríade de leitura-modifica)
        LOGOUT,             // O3 isolado (cleanup tardio)
        INCREMENT_FAILURE,  // O4
        RESET_FAILURES      // O5
    }

    private final int pesoValidate;
    private final int pesoLogin;
    private final int pesoLogout;
    private final int pesoIncrement;
    private final int pesoReset;

    Scenario(int pesoValidate, int pesoLogin, int pesoLogout,
             int pesoIncrement, int pesoReset) {
        int soma = pesoValidate + pesoLogin + pesoLogout + pesoIncrement + pesoReset;
        if (soma != 100) {
            throw new IllegalStateException(
                "Cenário " + name() + " soma " + soma + ", esperado 100");
        }
        this.pesoValidate = pesoValidate;
        this.pesoLogin = pesoLogin;
        this.pesoLogout = pesoLogout;
        this.pesoIncrement = pesoIncrement;
        this.pesoReset = pesoReset;
    }

    /**
     * Sorteia a próxima operação conforme a distribuição do cenário.
     *
     * <p>O parâmetro é declarado como {@link Random} para admitir tanto
     * a instância thread-safe {@code ThreadLocalRandom.current()}
     * usada em produção quanto a instância {@code new Random(seed)}
     * usada em testes para garantir determinismo.</p>
     *
     * @param r gerador uniforme em [0, 100)
     */
    public Op proximaOperacao(Random r) {
        int x = r.nextInt(100);
        int acc = pesoValidate;
        if (x < acc) return Op.VALIDATE;
        acc += pesoLogin;
        if (x < acc) return Op.LOGIN_VALIDATE_LOGOUT;
        acc += pesoLogout;
        if (x < acc) return Op.LOGOUT;
        acc += pesoIncrement;
        if (x < acc) return Op.INCREMENT_FAILURE;
        return Op.RESET_FAILURES;
    }

    /**
     * Peso nominal (em %) de uma operação neste cenário. Só leitura: não
     * altera os pesos nem o sorteio de {@link #proximaOperacao(Random)}.
     * Usado pela guarda de sanidade da mistura (B-43.3a).
     */
    public int peso(Op op) {
        return switch (op) {
            case VALIDATE -> pesoValidate;
            case LOGIN_VALIDATE_LOGOUT -> pesoLogin;
            case LOGOUT -> pesoLogout;
            case INCREMENT_FAILURE -> pesoIncrement;
            case RESET_FAILURES -> pesoReset;
        };
    }

    /** Resolve um nome de cenário (case-insensitive); fallback para S1. */
    public static Scenario porNome(String nome) {
        if (nome == null || nome.isBlank()) return S1;
        try {
            return valueOf(nome.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return S1;
        }
    }
}
