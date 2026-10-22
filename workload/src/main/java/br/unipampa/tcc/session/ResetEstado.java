package br.unipampa.tcc.session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * B-43.1 (diagnostico de 2026-10-22, causa C3): esvazia os caches do perfil em uso antes do
 * <i>warm-up</i> de cada repeticao e prova que ficaram vazios.
 *
 * <p>Passos: le {@code size()} de cada cache ({@code tamanhos_antes}), chama {@code clear()} em
 * cada um e consulta {@code size()} de todos ate todos darem 0 ou o prazo vencer. A espera e
 * necessaria porque, num cache ASYNC, o {@code clear} chega aos donos de forma assincrona. Qualquer
 * falha vira {@link EstadoNaoLimpoException} com o {@link Resultado} parcial.</p>
 *
 * <pre>{@code
 * antes = size(c) para cada c  ->  clear(c) para cada c  ->  depois = size(c) para cada c
 *   todos 0?                 -> Resultado
 *   prazo vencido?           -> EstadoNaoLimpoException("prazo ... restam: <cache>=<n>")
 *   senao sono(intervalo) e consulta de novo
 * }</pre>
 *
 * <p>Nao corrige a mistura S1/S2 (C1/C2 continuam ate B-43.2).</p>
 */
final class ResetEstado {

    /** Prazo padrao para os caches chegarem a 0. */
    static final Duration PRAZO_PADRAO = Duration.ofSeconds(10);

    /** Intervalo padrao entre consultas a {@code size()}. */
    static final Duration INTERVALO_PADRAO = Duration.ofMillis(100);

    private ResetEstado() { }

    /** Costura de teste para a espera entre consultas (producao: {@link Thread#sleep}). */
    @FunctionalInterface
    interface Sono {
        void dormir(Duration d) throws InterruptedException;

        /** Espera real. */
        static Sono real() {
            return d -> Thread.sleep(d.toMillis());
        }
    }

    /**
     * Resultado do reset. As listas seguem a ordem de {@code caches}; {@code null} numa posicao quer
     * dizer que aquela leitura nao aconteceu (so no resultado parcial de uma falha).
     */
    record Resultado(List<String> caches, List<Integer> tamanhosAntes, List<Integer> tamanhosDepois,
                     long duracaoMs) {
        Resultado {
            caches = List.copyOf(caches);
            tamanhosAntes = Collections.unmodifiableList(new ArrayList<>(tamanhosAntes));
            tamanhosDepois = Collections.unmodifiableList(new ArrayList<>(tamanhosDepois));
        }
    }

    /**
     * Executa o reset.
     *
     * @param caches    caches do perfil (sessoes e contadores)
     * @param prazo     tempo maximo, desde o inicio, para todos chegarem a 0 (&gt; 0)
     * @param intervalo espera entre consultas a {@code size()} (&gt; 0)
     * @param relogioNs relogio monotonico em nanossegundos
     * @param sono      espera entre consultas
     * @return tamanhos antes e depois (todos 0) e a duracao
     * @throws EstadoNaoLimpoException prazo vencido com algum cache nao vazio, ou excecao em
     *                                 {@code clear()}/{@code size()} (causa preservada)
     */
    static Resultado executar(List<CacheLimpavel> caches, Duration prazo, Duration intervalo,
                              LongSupplier relogioNs, Sono sono) throws EstadoNaoLimpoException {
        if (caches == null || caches.isEmpty()) {
            throw new IllegalArgumentException("nenhum cache para limpar");
        }
        if (prazo == null || prazo.isNegative() || prazo.isZero()) {
            throw new IllegalArgumentException("prazo deve ser > 0: " + prazo);
        }
        if (intervalo == null || intervalo.isNegative() || intervalo.isZero()) {
            throw new IllegalArgumentException("intervalo deve ser > 0: " + intervalo);
        }
        int n = caches.size();
        List<String> nomes = new ArrayList<>(n);
        for (CacheLimpavel c : caches) {
            nomes.add(c.nome());
        }
        List<Integer> antes = nulos(n);
        List<Integer> depois = nulos(n);
        long t0 = relogioNs.getAsLong();

        for (int i = 0; i < n; i++) {
            antes.set(i, tamanho(caches.get(i), "size() inicial", nomes, antes, depois, t0, relogioNs));
        }
        for (CacheLimpavel c : caches) {
            try {
                c.clear();
            } catch (RuntimeException e) {
                throw new EstadoNaoLimpoException("clear() falhou em " + c.nome() + ": " + e,
                        new Resultado(nomes, antes, depois, ms(relogioNs.getAsLong() - t0)), e);
            }
        }
        long prazoNs = prazo.toNanos();
        while (true) {
            boolean vazios = true;
            for (int i = 0; i < n; i++) {
                int s = tamanho(caches.get(i), "size() de verificacao", nomes, antes, depois, t0, relogioNs);
                depois.set(i, s);
                vazios &= s == 0;
            }
            long decorrido = relogioNs.getAsLong() - t0;
            if (vazios) {
                return new Resultado(nomes, antes, depois, ms(decorrido));
            }
            if (decorrido >= prazoNs) {
                throw new EstadoNaoLimpoException("caches nao esvaziaram no prazo de " + prazo.toMillis()
                        + " ms; restam: " + restantes(nomes, depois),
                        new Resultado(nomes, antes, depois, ms(decorrido)), null);
            }
            Duration espera = Duration.ofNanos(Math.min(intervalo.toNanos(), prazoNs - decorrido));
            try {
                sono.dormir(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EstadoNaoLimpoException("reset interrompido; restam: " + restantes(nomes, depois),
                        new Resultado(nomes, antes, depois, ms(relogioNs.getAsLong() - t0)), e);
            }
        }
    }

    private static int tamanho(CacheLimpavel c, String fase, List<String> nomes, List<Integer> antes,
                               List<Integer> depois, long t0, LongSupplier relogioNs)
            throws EstadoNaoLimpoException {
        try {
            return c.size();
        } catch (RuntimeException e) {
            throw new EstadoNaoLimpoException(fase + " falhou em " + c.nome() + ": " + e,
                    new Resultado(nomes, antes, depois, ms(relogioNs.getAsLong() - t0)), e);
        }
    }

    private static String restantes(List<String> nomes, List<Integer> depois) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nomes.size(); i++) {
            Integer s = depois.get(i);
            if (s == null || s != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(nomes.get(i)).append('=').append(s);
            }
        }
        return sb.toString();
    }

    private static List<Integer> nulos(int n) {
        List<Integer> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(null);
        }
        return l;
    }

    private static long ms(long ns) {
        return ns / 1_000_000L;
    }
}
