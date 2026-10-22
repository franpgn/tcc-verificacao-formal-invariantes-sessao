package br.unipampa.tcc.session;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * B-43.7: estado da mistura v3 numa repeticao S1/S2: o {@link SlotsGeracao} compartilhado, a
 * premissa q do token antigo e os contadores da janela de medicao impressos no fim da repeticao
 * ({@link #linha(SessionOps)}). Cada thread de carga usa o seu {@link Cliente} ({@link #cliente()}),
 * que guarda o contador POR THREAD de Validate isolados (nao consome o {@code Random} da mistura nem o
 * {@link KeyGenerator}).
 *
 * <p>Premissa c3 do Gestor (DT-004): q = 1/{@value #PERIODO_TOKEN_ANTIGO} = 5 % dos Validate isolados
 * leem a geracao anterior do slot (token antigo). E premissa de engenharia, nao medida de servico
 * real.</p>
 */
final class MisturaV3 {

    /** Um em cada {@value} Validate isolados (por thread) le o token antigo. */
    static final int PERIODO_TOKEN_ANTIGO = 20;

    /** q = 1 / {@link #PERIODO_TOKEN_ANTIGO}. */
    static final double Q = 1.0 / PERIODO_TOKEN_ANTIGO;

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

    /** Estado de uma thread de carga (nao e thread-safe; uma instancia por thread). */
    Cliente cliente() {
        return new Cliente(slots);
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

    /** Estado por thread: o contador de Validate isolados que decide o token antigo. */
    final class Cliente {

        private final SlotsGeracao slotsDoCliente;
        private long posicao;

        Cliente(SlotsGeracao slotsDoCliente) {
            this.slotsDoCliente = slotsDoCliente;
        }

        SlotsGeracao slots() {
            return slotsDoCliente;
        }

        /**
         * Avanca o contador de Validate isolados desta thread e diz se este le o token antigo: o
         * {@code PERIODO_TOKEN_ANTIGO}-esimo de cada bloco ({@code contador % 20 == 19}).
         */
        boolean proximoEhTokenAntigo() {
            long c = posicao++;
            return c % PERIODO_TOKEN_ANTIGO == PERIODO_TOKEN_ANTIGO - 1;
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
