package br.unipampa.tcc.session;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.LongAdder;

/**
 * B-43.7: estado da mistura v3 numa repeticao S1/S2: o {@link SlotsGeracao} compartilhado, a
 * premissa q do token antigo e os contadores da janela de medicao impressos no fim da repeticao
 * ({@link #linha(SessionOps)}). Cada thread de carga usa o seu {@link Cliente}
 * ({@link #cliente(long)}, com a semente da thread), que decide o token antigo por um sorteio de
 * Bernoulli p = 0,05 com Random proprio por thread (DT-008). Esse Random nao e o {@code Random} da
 * mistura nem o {@link KeyGenerator}, e nenhum dos dois e consumido pelo token.
 *
 * <p>Premissa do Gestor (DT-008, que substitui a de DT-004): cada Validate isolado le a geracao
 * anterior do slot (token antigo) com probabilidade {@value #P_TOKEN_ANTIGO}, num sorteio independente.
 * E premissa de engenharia, nao medida de servico real.</p>
 *
 * <p>Semente do sorteio: {@link #sementeToken(long)} da semente da thread
 * ({@link WorkloadMain#sementeThread(long, int, int)}), com o sal {@link #SAL_TOKEN} e o finalizador do
 * SplitMix64 ({@link #mix64(long)}). O sal separa o dominio do token do {@code Random} da mistura, que o
 * B-43.5 vai semear com a mesma semente da thread; o misturador espalha os bits de sementes vizinhas
 * ({@code s}, {@code s+1}, ...) antes do LCG do {@code java.util.Random}.</p>
 */
final class MisturaV3 {

    /** Probabilidade de um Validate isolado ler o token antigo (sorteio de Bernoulli, DT-008). */
    static final double P_TOKEN_ANTIGO = 0.05;

    /** q = {@link #P_TOKEN_ANTIGO} (dry-run e stdout). */
    static final double Q = P_TOKEN_ANTIGO;

    /** Sal da semente do token: "TOKENANT" em ASCII. */
    static final long SAL_TOKEN = 0x544F4B454E414E54L;

    /**
     * Semente do {@code Random} do token antigo de uma thread: {@code mix64(sementeThread ^ SAL_TOKEN)}.
     *
     * @param sementeThread a semente da thread ({@link WorkloadMain#sementeThread(long, int, int)})
     */
    static long sementeToken(long sementeThread) {
        return mix64(sementeThread ^ SAL_TOKEN);
    }

    /** Finalizador do SplitMix64 (Steele, Lea, Flood 2014), sem o incremento do gerador. */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private final SlotsGeracao slots;
    private final WarmupPolicy politica;
    private final LongAdder validateIsolados = new LongAdder();
    private final LongAdder tokenAntigo = new LongAdder();
    private final LongAdder logoutIsolados = new LongAdder();
    private final LongAdder reposicaoOmitida = new LongAdder();

    MisturaV3(SlotsGeracao slots, WarmupPolicy politica) {
        if (slots == null) {
            throw new IllegalArgumentException("slots nulo");
        }
        this.slots = slots;
        this.politica = politica != null ? politica : WarmupPolicy.always();
    }

    SlotsGeracao slots() {
        return slots;
    }

    /**
     * Estado de uma thread de carga (nao e thread-safe; uma instancia por thread), com o seu Random do
     * token antigo semeado por {@link #sementeToken(long)}.
     *
     * @param sementeThread a semente da thread ({@link WorkloadMain#sementeThread(long, int, int)})
     */
    Cliente cliente(long sementeThread) {
        return new Cliente(slots, sementeThread);
    }

    long validateIsolados() {
        return validateIsolados.sum();
    }

    long tokenAntigo() {
        return tokenAntigo.sum();
    }

    long logoutIsolados() {
        return logoutIsolados.sum();
    }

    long reposicaoOmitida() {
        return reposicaoOmitida.sum();
    }

    /** {@code q} formatado para o stdout e o dry-run ({@code 0.05}). */
    static String rotuloQ() {
        return String.format(Locale.ROOT, "%.2f", Q);
    }

    /** Linha do stdout no fim da repeticao (contadores da janela de medicao). */
    String linha(SessionOps ops) {
        return "Mistura v3: validate_isolados=" + validateIsolados() + " token_antigo=" + tokenAntigo()
                + " logout_isolados=" + logoutIsolados() + " reposicoes=" + ops.reposicoes()
                + " reposicao_omitida=" + reposicaoOmitida() + " reposicao_erro=" + ops.reposicaoErro();
    }

    /** Estado por thread: o Random proprio que sorteia o token antigo (nunca compartilhado). */
    final class Cliente {

        private final SlotsGeracao slotsDoCliente;
        private final Random tokens;

        Cliente(SlotsGeracao slotsDoCliente, long sementeThread) {
            this.slotsDoCliente = slotsDoCliente;
            this.tokens = new Random(sementeToken(sementeThread));
        }

        SlotsGeracao slots() {
            return slotsDoCliente;
        }

        /**
         * Um sorteio por Validate isolado: {@code true} (token antigo) com probabilidade
         * {@link #P_TOKEN_ANTIGO}. Sorteia SEMPRE, dentro e fora da janela de medicao (a politica de
         * warm-up so decide se {@link #contarValidate(boolean)} conta), para que a sequencia dependa so
         * da semente e da ordem dos Validate isolados da thread.
         */
        boolean proximoEhTokenAntigo() {
            return tokens.nextDouble() < P_TOKEN_ANTIGO;
        }

        /** Conta um Validate isolado (e o token antigo) se estiver na janela de medicao. */
        void contarValidate(boolean antigo) {
            if (politica.deveRegistrar()) {
                validateIsolados.increment();
                if (antigo) {
                    tokenAntigo.increment();
                }
            }
        }

        /** Conta um Logout isolado (e a reposicao omitida, se o logout deu ERROR_*), na janela. */
        void contarLogout(boolean omitida) {
            if (politica.deveRegistrar()) {
                logoutIsolados.increment();
                if (omitida) {
                    reposicaoOmitida.increment();
                }
            }
        }
    }
}
