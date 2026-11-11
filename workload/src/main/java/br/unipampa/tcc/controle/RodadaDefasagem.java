package br.unipampa.tcc.controle;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * W-4 (B-26.3): uma rodada da {@link SondaDefasagem}. Cada no X escreveu a sua chave pelo cliente dele, e cada
 * outro no Y leu essa chave pelo cliente dele ate ver o valor novo ou ate o teto. Um <b>par</b> (X, Y) e o lag
 * de X para Y.
 *
 * <p><b>Lag do no</b> ({@link #lagPorNo()}): o <b>menor</b> lag entre os pares em que o no e escritor ou leitor.
 * E alto so quando <i>todas</i> as trocas do no com os outros foram lentas ou nao viram o valor: o problema e do
 * no (cortado, pausado), nao de um enlace nem do dono de uma chave. Um corte parcial entre dois nos, ou uma
 * chave cujo dono primario esta do outro lado, nao basta para levantar o lag de nenhum no. O
 * {@link #lagMaxMs()} e o maior lag de no, e o {@link #noSuspeito()} e o no que o atinge, se for unico (empate,
 * por exemplo todos no teto, da {@code null}).</p>
 *
 * <p>Base: medir a defasagem do ponto de vista do cliente, escrevendo um valor marcado e lendo ate ve-lo, como em
 * {@code wada_consistency_cloud_2011} (secao 2: tempo desde o inicio da escrita ate a leitura devolver o valor
 * novo); a defasagem como distribuicao medida, de onde sai o {@code lagIn} da calibracao, como em
 * {@code bailis_pbs_2012}; e o no visto como falho por quem o usa antes do detector de falhas, como em
 * {@code huang_gray_failure_2017}.</p>
 *
 * @param rodada    numero da rodada na execucao (a partir de 1)
 * @param tInicioNs inicio da rodada, no relogio dos {@code end_ns}; decide a janela a que a rodada pertence
 * @param tNs       fim da rodada (quando o resultado ficou pronto), no mesmo relogio
 * @param tetoMs    o prazo de leitura ({@code --hotrod-timeout-ms}): o lag de um par nao visto
 * @param nos       rotulos canonicos dos nos, na ordem do canal
 * @param pares     um resultado por par ordenado (X, Y) com X != Y, exatamente uma vez cada
 * @param escritas  um resultado por no, na ordem de {@code nos}
 */
public record RodadaDefasagem(long rodada, long tInicioNs, long tNs, long tetoMs, List<String> nos, List<Par> pares,
                              List<Escrita> escritas) {

    /**
     * Resultado de um par.
     *
     * @param escritor     rotulo de quem escreveu
     * @param leitor       rotulo de quem leu
     * @param visto        a leitura devolveu o valor novo antes do teto
     * @param lagMs        do inicio da rodada ate o fim da leitura que devolveu o valor novo; o teto se nao visto
     * @param leituras     tentativas de leitura do par (inclui as que falharam)
     * @param errosLeitura tentativas que lancaram excecao
     */
    public record Par(String escritor, String leitor, boolean visto, double lagMs, int leituras, int errosLeitura) {
        public Par {
            Objects.requireNonNull(escritor, "escritor");
            Objects.requireNonNull(leitor, "leitor");
            if (escritor.equals(leitor)) {
                throw new IllegalArgumentException("par com o mesmo no: " + escritor);
            }
            if (!Double.isFinite(lagMs) || lagMs < 0) {
                throw new IllegalArgumentException("lagMs deve ser finito e >= 0: " + lagMs);
            }
            if (leituras < 0 || errosLeitura < 0 || errosLeitura > leituras) {
                throw new IllegalArgumentException("contagens invalidas: leituras=" + leituras + " erros=" + errosLeitura);
            }
        }
    }

    /**
     * Resultado da escrita de um no.
     *
     * @param no        rotulo do no
     * @param ok        {@code true} sucesso, {@code false} excecao, {@code null} ainda pendente no fim da rodada
     * @param duracaoMs duracao da escrita, ou {@code null} se pendente
     * @param erro      nome simples da excecao, ou {@code null}
     */
    public record Escrita(String no, Boolean ok, Double duracaoMs, String erro) {
        public Escrita {
            Objects.requireNonNull(no, "no");
            if ((ok == null) != (duracaoMs == null)) {
                throw new IllegalArgumentException("escrita pendente sem duracao, e so ela: ok=" + ok + " duracao=" + duracaoMs);
            }
            if (Boolean.TRUE.equals(ok) && erro != null) {
                throw new IllegalArgumentException("escrita ok com erro: " + erro);
            }
        }
    }

    public RodadaDefasagem {
        Objects.requireNonNull(nos, "nos");
        Objects.requireNonNull(pares, "pares");
        Objects.requireNonNull(escritas, "escritas");
        if (tetoMs <= 0) {
            throw new IllegalArgumentException("tetoMs deve ser > 0: " + tetoMs);
        }
        nos = List.copyOf(nos);
        pares = List.copyOf(pares);
        escritas = List.copyOf(escritas);
        int n = nos.size();
        if (n < 2 || new HashSet<>(nos).size() != n) {
            throw new IllegalArgumentException("nos deve ter >= 2 rotulos distintos: " + nos);
        }
        if (pares.size() != n * (n - 1)) {
            throw new IllegalArgumentException("esperava " + n * (n - 1) + " pares, veio " + pares.size());
        }
        Set<String> vistos = new HashSet<>();
        for (Par p : pares) {
            if (!nos.contains(p.escritor()) || !nos.contains(p.leitor())) {
                throw new IllegalArgumentException("par com no desconhecido: " + p);
            }
            if (!vistos.add(p.escritor() + "\n" + p.leitor())) {
                throw new IllegalArgumentException("par repetido: " + p);
            }
            if (p.visto() ? p.lagMs() >= tetoMs : p.lagMs() != tetoMs) {
                throw new IllegalArgumentException("par visto tem lag < teto, e nao visto tem lag = teto (" + tetoMs
                        + "): " + p);
            }
        }
        if (escritas.size() != n) {
            throw new IllegalArgumentException("esperava " + n + " escritas, veio " + escritas.size());
        }
        for (int i = 0; i < n; i++) {
            if (!escritas.get(i).no().equals(nos.get(i))) {
                throw new IllegalArgumentException("escritas fora da ordem dos nos: " + escritas);
            }
        }
    }

    /** Lag de cada no (o menor lag entre os pares em que ele aparece), na ordem de {@link #nos()}. */
    public Map<String, Double> lagPorNo() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String no : nos) {
            double menor = Double.POSITIVE_INFINITY;
            for (Par p : pares) {
                if (p.escritor().equals(no) || p.leitor().equals(no)) {
                    menor = Math.min(menor, p.lagMs());
                }
            }
            m.put(no, menor);
        }
        return Collections.unmodifiableMap(m);
    }

    /** Maior lag de no da rodada. */
    public double lagMaxMs() {
        return maximo(lagPorNo());
    }

    /** Rotulo do no com o maior lag, se unico; {@code null} em empate. */
    public String noSuspeito() {
        return unicoMaximo(lagPorNo());
    }

    /** Maior lag de par (so registro: um par alto sozinho nao levanta o lag de nenhum no). */
    public double lagMaxParMs() {
        double m = 0;
        for (Par p : pares) {
            m = Math.max(m, p.lagMs());
        }
        return m;
    }

    /** Tentativas de leitura da rodada. */
    public int leituras() {
        return pares.stream().mapToInt(Par::leituras).sum();
    }

    /** Tentativas de leitura que falharam. */
    public int errosLeitura() {
        return pares.stream().mapToInt(Par::errosLeitura).sum();
    }

    /** Escritas que lancaram excecao. */
    public int errosEscrita() {
        return (int) escritas.stream().filter(e -> Boolean.FALSE.equals(e.ok())).count();
    }

    /** Maior valor do mapa (0 se vazio). */
    static double maximo(Map<String, Double> lagPorNo) {
        double m = 0;
        for (double v : lagPorNo.values()) {
            m = Math.max(m, v);
        }
        return m;
    }

    /** Chave do maior valor, se unico; {@code null} em empate ou mapa vazio. */
    static String unicoMaximo(Map<String, Double> lagPorNo) {
        String melhor = null;
        double max = Double.NEGATIVE_INFINITY;
        boolean empate = false;
        for (Map.Entry<String, Double> e : lagPorNo.entrySet()) {
            double v = e.getValue();
            if (v > max) {
                max = v;
                melhor = e.getKey();
                empate = false;
            } else if (v == max) {
                empate = true;
            }
        }
        return empate ? null : melhor;
    }
}
