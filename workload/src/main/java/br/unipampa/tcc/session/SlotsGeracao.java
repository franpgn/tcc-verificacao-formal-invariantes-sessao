package br.unipampa.tcc.session;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * B-43.7 (mistura v3, "slot-geracao"): estado compartilhado por todas as threads de uma repeticao
 * S1/S2 com {@code --mix v3}.
 *
 * <p>O {@link KeyGenerator} continua sorteando {@code sid-NNNNNN} (Zipf); em v3 esse numero e o
 * <i>slot</i> {@code k} (o usuario), e a sessao corrente do usuario e a da geracao
 * {@code geracao[k]}. A chave efetiva da sessao do slot {@code k} na geracao {@code g} e
 * {@link #chave(int, int)}: {@code sid-NNNNNN} na geracao 0 (o mesmo formato do
 * {@link KeyGenerator#nextSessionKey()}) e {@code sid-NNNNNN~g} da geracao 1 em diante.</p>
 *
 * <pre>
 * carga (CargaSlots):  chave(k,0) INVALID(LOGOUT), chave(k,1) VALID, geracao[k] = 1
 * Validate isolado:    le chave(k, geracao[k])  ou, por sorteio de Bernoulli p = 0,05 por thread (DT-008), chave(k, geracao[k]-1)
 * Logout isolado:      synchronized (trava(k)) { g = geracao[k]; logout chave(k,g);
 *                                                 reposicao chave(k,g+1) VALID; geracao[k] = g+1 }
 * </pre>
 *
 * <p>Decisoes do Gestor de 2026-10-22: mapa compartilhado entre threads (c4), trava por faixa de
 * slot no Logout isolado e geracao 0 INVALID na carga. A identidade dona da sessao do slot e a
 * mesma em todas as geracoes ({@link IdentidadesMistura#identidadeDaCarga(int, int)}).</p>
 */
final class SlotsGeracao {

    /** Numero de faixas de trava ({@code k mod FAIXAS}). */
    static final int FAIXAS = 1024;

    /** Separador entre o numero do slot e a geracao na chave ({@code sid-NNNNNN~g}). */
    static final char SEPARADOR = '~';

    private final int universo;
    private final int threads;
    private final AtomicIntegerArray geracao;
    private final Object[] travas = new Object[FAIXAS];

    /**
     * Estado de {@code universo} slots ({@code 1..universo}), todos na geracao 1 (o estado depois da
     * {@link CargaSlots}). {@code threads} define a identidade dona de cada slot.
     */
    SlotsGeracao(int universo, int threads) {
        if (universo < 1) {
            throw new IllegalArgumentException("universo deve ser >= 1: " + universo);
        }
        if (threads < 1) {
            throw new IllegalArgumentException("threads deve ser >= 1: " + threads);
        }
        this.universo = universo;
        this.threads = threads;
        this.geracao = new AtomicIntegerArray(universo + 1);
        for (int k = 1; k <= universo; k++) {
            geracao.set(k, 1);
        }
        for (int i = 0; i < FAIXAS; i++) {
            travas[i] = new Object();
        }
    }

    int universo() {
        return universo;
    }

    int threads() {
        return threads;
    }

    /** Geracao corrente do slot {@code k}. */
    int geracao(int k) {
        return geracao.get(k);
    }

    /** Fixa a geracao corrente do slot {@code k} (so o Logout isolado, sob {@link #trava(int)}). */
    void definirGeracao(int k, int g) {
        geracao.set(k, g);
    }

    /** Monitor da faixa do slot {@code k} ({@code k mod 1024}). */
    Object trava(int k) {
        return travas[Math.floorMod(k, FAIXAS)];
    }

    /** Identidade dona da sessao do slot {@code k}, em qualquer geracao. */
    String identidade(int k) {
        return IdentidadesMistura.identidadeDaCarga(k, threads);
    }

    /** Chave da sessao do slot {@code k} na geracao {@code g}. */
    static String chave(int k, int g) {
        if (k < 1) {
            throw new IllegalArgumentException("slot deve ser >= 1: " + k);
        }
        if (g < 0) {
            throw new IllegalArgumentException("geracao deve ser >= 0: " + g);
        }
        String base = String.format("%s%06d", KeyGenerator.PREFIXO_SID, k);
        return g == 0 ? base : base + SEPARADOR + g;
    }

    /** Slot de uma chave {@code sid-NNNNNN} ou {@code sid-NNNNNN~g} (inverso de {@link #chave}). */
    static int slotDe(String sid) {
        if (sid == null || !sid.startsWith(KeyGenerator.PREFIXO_SID)) {
            throw new IllegalArgumentException("chave fora do formato sid-NNNNNN[~g]: " + sid);
        }
        int fim = sid.indexOf(SEPARADOR);
        String num = sid.substring(KeyGenerator.PREFIXO_SID.length(), fim < 0 ? sid.length() : fim);
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("chave fora do formato sid-NNNNNN[~g]: " + sid, e);
        }
    }

    /** Geracao de uma chave ({@code 0} sem separador). */
    static int geracaoDe(String sid) {
        slotDe(sid);
        int fim = sid.indexOf(SEPARADOR);
        return fim < 0 ? 0 : Integer.parseInt(sid.substring(fim + 1));
    }
}
