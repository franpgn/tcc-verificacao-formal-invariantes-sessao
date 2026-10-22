package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Agregador de janelas tumbling do controlador (B-26.1; plano de 2026-10-22, secao 3.2; contrato
 * da validacao V-B28.1, secao 6; regra C+A do Gestor, 2026-10-22, para evento atrasado).
 *
 * <p><b>Janelas.</b> A janela {@code j >= 0} e {@code [origem + j*W, origem + (j+1)*W)}. Um evento
 * entra na janela do seu {@code end_ns}; evento com {@code end_ns} exatamente em {@code fimNs}
 * pertence a janela seguinte. Toda janela e emitida, inclusive vazia, em ordem e sem buraco
 * ({@code inicioNs(j) = fimNs(j-1)}).
 *
 * <p><b>Tempo.</b> Nao ha relogio de parede: a marca d'agua e o maior {@code end_ns} ja visto
 * (eventos e violacoes), comecando em {@code origem}. A janela {@code j} fecha quando
 * {@code marca >= fimNs(j) + L}. O tempo de decisao continua sendo {@code fimNs(j)}; {@code L} so
 * atrasa a emissao.
 *
 * <p><b>Atrasados (regra C+A).</b> Um evento cuja janela ja foi emitida (ou anterior a origem) e
 * descartado e contado: em {@link JanelaObservada#atrasados()} da proxima janela emitida e em
 * {@link #atrasadosTotal()}. Violacoes atrasadas fora do gatilho sao descartadas e contadas a
 * parte ({@link JanelaObservada#violacoesAtrasadas()}). Uma janela emitida nunca muda.
 *
 * <p><b>Violacao de seguranca atrasada (V-B26.1 secao 5.5; Gestor, 2026-10-22).</b> Uma violacao
 * com {@link ClasseViolacao#gatilho()} cuja janela ja foi emitida nao e descartada: e
 * <em>promovida</em> a proxima janela emitida, somando em {@code violacoesSeguranca} dela e em
 * {@link JanelaObservada#violacoesSegurancaPromovidas()}. R1 dispara uma janela depois, a favor da
 * seguranca. Promovidas pendentes no encerramento vao para a primeira janela que {@code encerrar}
 * emitir; se nenhuma for emitida, contam em {@link #violacoesDescartadasNoEncerramento()}.
 *
 * <p><b>Encerramento.</b> {@link #encerrar()} emite, sem esperar {@code L}, todas as janelas
 * completas ({@code fimNs <= marca}); a janela parcial que contem a marca nao e emitida e o que
 * havia nela e contado em {@link #descartadosNoEncerramento()} e
 * {@link #violacoesDescartadasNoEncerramento()}. Depois disso o agregador recusa entrada.
 *
 * <p><b>Tick de relogio (V-B26.1 secao 5.9; Gestor, 2026-10-22).</b> {@link #avancarRelogio(long)}
 * tem a semantica de um evento sem conteudo: {@code marca = max(marca, agora)} e a mesma regra de
 * fechamento com {@code L}. Garante que janelas fechem mesmo sem eventos (partição, portao K1).
 * O agora vem de uma {@link FonteDeTempo} no mesmo relogio dos {@code end_ns}.
 * {@link #seqRegistro()} conta as chamadas a {@code registrar} e {@code registrarViolacao} (o tick
 * nao conta), para que o replay reinjete cada tick depois do mesmo registro.
 *
 * <p><b>Violacoes.</b> Chegam com o {@code end_ns} do evento que as revelou, informado pelo
 * chamador (decisao do Gestor, 2026-10-22), ja classificadas por {@link ClasseViolacao}.
 *
 * <p>Puro e thread-safe: os metodos publicos sao {@code synchronized} e devolvem as janelas que a
 * chamada fechou, em ordem. Nao depende de {@code LatencyRegistry} (cujo {@code snapshot()} drena o
 * intervalo, achado A1) nem do cliente do data grid.
 */
public final class AgregadorDeJanela {

    /**
     * Tolerancia padrao L para eventos fora de ordem: 100 ms, cerca de 10x o pior atraso medido nos
     * 12 CSVs brutos (9,43 ms em S2-none rep-001; V-B26.1 secao 6; Gestor, 2026-10-22).
     */
    public static final long TOLERANCIA_PADRAO_NS = 100_000_000L;

    private final long origemNs;
    private final long wNs;
    private final long toleranciaNs;
    private final Referencia referencia;

    /** Janelas abertas por indice. */
    private final TreeMap<Long, Acumulador> abertas = new TreeMap<>();
    /** Indice da proxima janela a emitir; indices menores ja foram emitidos. */
    private long proxima;
    private long marcaNs;
    private boolean encerrado;
    private long seqRegistro;

    private long atrasadosPendentes;
    private long violacoesAtrasadasPendentes;
    private long promovidasPendentes;
    private long promovidasTotal;
    private long atrasadosTotal;
    private long violacoesAtrasadasTotal;
    private long descartadosNoEncerramento;
    private long violacoesDescartadasNoEncerramento;

    /**
     * @param origemNs     inicio da primeira janela (mesmo relogio de {@code end_ns})
     * @param wNs          largura W (&gt; 0)
     * @param toleranciaNs tolerancia L para fora de ordem (&gt;= 0)
     * @param referencia   p99 de referencia por operacao
     */
    public AgregadorDeJanela(long origemNs, long wNs, long toleranciaNs, Referencia referencia) {
        if (wNs <= 0) {
            throw new IllegalArgumentException("wNs deve ser positivo: " + wNs);
        }
        if (toleranciaNs < 0) {
            throw new IllegalArgumentException("toleranciaNs negativa: " + toleranciaNs);
        }
        this.referencia = Objects.requireNonNull(referencia, "referencia");
        this.origemNs = origemNs;
        this.wNs = wNs;
        this.toleranciaNs = toleranciaNs;
        this.marcaNs = origemNs;
    }

    /** W de {@code parametros} e L = {@link #TOLERANCIA_PADRAO_NS}. */
    public AgregadorDeJanela(long origemNs, ParametrosControlador parametros, Referencia referencia) {
        this(origemNs, Objects.requireNonNull(parametros, "parametros").wNs(), TOLERANCIA_PADRAO_NS, referencia);
    }

    /** Registra um evento de operacao e devolve as janelas que ele fechou (possivelmente nenhuma). */
    public synchronized List<JanelaObservada> registrar(EventoOperacao evento) {
        Objects.requireNonNull(evento, "evento");
        exigirAberto();
        seqRegistro++;
        Acumulador acc = acumuladorDe(evento.fimNs());
        if (acc == null) {
            atrasadosPendentes++;
            atrasadosTotal++;
        } else {
            acc.total++;
            if (evento.erro()) {
                acc.erros++;
            }
            if (evento.indisponivel()) {
                acc.indisponiveis++;
            }
            if (referencia.contem(evento.operacao())) {
                acc.elegiveis++;
                if (evento.latenciaNs() > referencia.p99Ns(evento.operacao())) {
                    acc.excedencias++;
                }
            }
        }
        return avancar(evento.fimNs());
    }

    /**
     * Registra uma violacao ja classificada, no instante {@code fimNsDoEvento} (o {@code end_ns} do
     * evento que a revelou), e devolve as janelas que ela fechou.
     */
    public synchronized List<JanelaObservada> registrarViolacao(ClasseViolacao classe, long fimNsDoEvento) {
        Objects.requireNonNull(classe, "classe");
        exigirAberto();
        seqRegistro++;
        Acumulador acc = acumuladorDe(fimNsDoEvento);
        if (acc == null) {
            if (classe.gatilho()) {
                promovidasPendentes++;
            } else {
                violacoesAtrasadasPendentes++;
                violacoesAtrasadasTotal++;
            }
        } else {
            switch (classe) {
                case SEGURANCA -> acc.seguranca++;
                case I2_ESPERADA -> acc.i2Esperadas++;
                case I2_FORA_DO_GATILHO -> acc.i2Fora++;
                case FORA_DO_GATILHO -> acc.fora++;
            }
        }
        return avancar(fimNsDoEvento);
    }

    /**
     * Tick: avanca a marca d'agua ate {@code agoraNs} (sem recuar) e devolve as janelas que isso
     * fechou, em ordem. {@code agoraNs <= marca} nao emite nada.
     *
     * @throws IllegalStateException depois de {@link #encerrar()}
     */
    public synchronized List<JanelaObservada> avancarRelogio(long agoraNs) {
        exigirAberto();
        return avancar(agoraNs);
    }

    /** Tick com o agora lido de {@code fonte} (mesmo relogio dos {@code end_ns}), sob o lock. */
    public synchronized List<JanelaObservada> avancarRelogio(FonteDeTempo fonte) {
        Objects.requireNonNull(fonte, "fonte");
        exigirAberto();
        return avancar(fonte.agoraNs());
    }

    /** Numero de chamadas a {@code registrar} e {@code registrarViolacao} ate agora. */
    public synchronized long seqRegistro() {
        return seqRegistro;
    }

    /**
     * Fim da entrada: emite as janelas completas sem esperar L, descarta a parcial (contando) e
     * passa a recusar entrada. Chamadas seguintes devolvem lista vazia.
     */
    public synchronized List<JanelaObservada> encerrar() {
        if (encerrado) {
            return List.of();
        }
        encerrado = true;
        List<JanelaObservada> out = new ArrayList<>();
        while (fimDe(proxima) <= marcaNs) {
            out.add(emitir());
        }
        for (Acumulador acc : abertas.values()) {
            descartadosNoEncerramento += acc.total;
            violacoesDescartadasNoEncerramento += acc.seguranca + acc.i2Esperadas + acc.i2Fora + acc.fora;
        }
        abertas.clear();
        violacoesDescartadasNoEncerramento += promovidasPendentes;
        promovidasPendentes = 0;
        return out;
    }

    private void exigirAberto() {
        if (encerrado) {
            throw new IllegalStateException("agregador encerrado");
        }
    }

    /** Acumulador da janela de {@code fimNs}; {@code null} se ela ja foi emitida (atrasado). */
    private Acumulador acumuladorDe(long fimNs) {
        long j = Math.floorDiv(fimNs - origemNs, wNs);
        if (j < proxima) {
            return null;
        }
        return abertas.computeIfAbsent(j, x -> new Acumulador());
    }

    private List<JanelaObservada> avancar(long fimNs) {
        if (fimNs > marcaNs) {
            marcaNs = fimNs;
        }
        List<JanelaObservada> out = new ArrayList<>(1);
        while (marcaNs - toleranciaNs >= fimDe(proxima)) {
            out.add(emitir());
        }
        return out;
    }

    private JanelaObservada emitir() {
        Acumulador acc = abertas.remove(proxima);
        if (acc == null) {
            acc = new Acumulador();
        }
        long ini = origemNs + proxima * wNs;
        Janela janela = new Janela(ini, ini + wNs, acc.total, acc.erros, acc.indisponiveis,
                acc.elegiveis, acc.excedencias, acc.seguranca + promovidasPendentes, acc.i2Esperadas);
        JanelaObservada o = new JanelaObservada(janela, atrasadosPendentes, violacoesAtrasadasPendentes,
                promovidasPendentes, acc.i2Fora, acc.fora);
        promovidasTotal += promovidasPendentes;
        promovidasPendentes = 0;
        atrasadosPendentes = 0;
        violacoesAtrasadasPendentes = 0;
        proxima++;
        return o;
    }

    private long fimDe(long j) {
        return origemNs + (j + 1) * wNs;
    }

    public long origemNs() {
        return origemNs;
    }

    public long wNs() {
        return wNs;
    }

    public long toleranciaNs() {
        return toleranciaNs;
    }

    public synchronized long marcaDaguaNs() {
        return marcaNs;
    }

    public synchronized long atrasadosTotal() {
        return atrasadosTotal;
    }

    public synchronized long violacoesAtrasadasTotal() {
        return violacoesAtrasadasTotal;
    }

    /** Violacoes de seguranca atrasadas ja entregues em janelas emitidas (promovidas). */
    public synchronized long violacoesSegurancaPromovidasTotal() {
        return promovidasTotal;
    }

    public synchronized long descartadosNoEncerramento() {
        return descartadosNoEncerramento;
    }

    public synchronized long violacoesDescartadasNoEncerramento() {
        return violacoesDescartadasNoEncerramento;
    }

    /** Contadores mutaveis de uma janela aberta. */
    private static final class Acumulador {
        long total;
        long erros;
        long indisponiveis;
        long elegiveis;
        long excedencias;
        long seguranca;
        long i2Esperadas;
        long i2Fora;
        long fora;
    }
}
