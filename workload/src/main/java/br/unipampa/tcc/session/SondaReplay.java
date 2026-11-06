package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.LongUnaryOperator;

/**
 * W-10: sonda de reuso de sessao depois do logout (<i>replay</i>) na carga S3. Instrumentacao, nao carga:
 * mede se uma sid com logout confirmado ao cliente ainda e aceita por outro servidor de aplicacao, o ataque
 * de reuso de token depois do logout que a I1 existe para impedir (diagnostico de 06/11: a carga S3 so
 * revalida a sid por 10 s e sempre no mesmo no, e nunca observa o logout do no isolado que se perde na
 * fusao, mecanismo M1).
 *
 * <ul>
 *   <li><b>Replay imediato.</b> Para cada logout confirmado ao cliente pelo no {@code k}
 *       ({@link #logoutConfirmado}), um Validate da sid antiga pelo servidor de aplicacao do no
 *       {@code (k + 1) mod n}, entre {@code --s3-replay-min-sec} e {@code --s3-replay-max-sec} depois. O
 *       atraso vem do gerador proprio do usuario ({@link ContextoS3#rngSonda}), que nao consome o gerador
 *       da carga. Um ator por no alvo ({@link #atores()}): um no fora do ar so atrasa os replays dele.</li>
 *   <li><b>Replay tardio.</b> No fim da carga ({@link #replayTardio}), um novo Validate de cada sid com
 *       logout confirmado na falha ou na recuperacao (fases {@code durante*} e {@code recuperacao} dos
 *       marcadores), por um no diferente do que confirmou: {@code k + 1}, e {@code k + 2} se aquele der erro
 *       (um no que deu erro fica marcado e e pulado no resto do tardio). Acima de
 *       {@code --s3-replay-late-max} candidatas, amostra sem reposicao com o gerador
 *       {@link ContextoS3#rngSondaTardia}.</li>
 * </ul>
 *
 * <p>Cada leitura vai por {@link SessionOps#validarSonda}: VALID numa sid com invalidacao confirmada e I1
 * pelo caminho do auditor, com origem {@link InvariantAuditor#ORIGEM_SONDA}; o evento sai com o tipo
 * {@link SessionOps#OP_REPLAY_VALIDATE}. Nada vai ao {@link S3Registro} como acao (so os contadores
 * {@code sonda|...}), ao controlador ou a latencia. Os replays imediatos ainda pendentes no fim da carga sao
 * descartados e contados ({@code sonda|imediata|descartada-fim}). Thread-safe.</p>
 */
public final class SondaReplay {

    /** Tipo da leitura: replay imediato (logo depois do logout). */
    public static final String IMEDIATO = "imediato";
    /** Tipo da leitura: replay tardio (no fim da carga). */
    public static final String TARDIO = "tardio";

    /** Passo maximo de espera de um ator da sonda (fila vazia ou replay novo com prazo mais cedo). */
    static final long PASSO_NS = 50_000_000L;

    /**
     * Uma leitura feita pela sonda.
     *
     * @param tipo       {@link #IMEDIATO} ou {@link #TARDIO}
     * @param sid        a sid do logout
     * @param noLogout   indice do no que confirmou o logout
     * @param noAlvo     indice do no que fez a leitura
     * @param tLogoutNs  instante da confirmacao do logout (base do {@link RelogioS3})
     * @param tDevidoNs  instante agendado (imediato) ou o do inicio da leitura (tardio)
     * @param tInicioNs  instante em que a leitura comecou
     * @param resultado  {@link AplicacaoS3#descrever} do resultado
     */
    public record Leitura(String tipo, String sid, int noLogout, int noAlvo, long tLogoutNs, long tDevidoNs,
                          long tInicioNs, String resultado) { }

    private record Replay(String sid, int noLogout, int noAlvo, long tLogoutNs, long tDevidoNs, long seq) { }

    private record Logout(String sid, int no, long tNs, long seq) { }

    private static final Comparator<Replay> ORDEM = Comparator.comparingLong(Replay::tDevidoNs)
            .thenComparingLong(Replay::seq);

    private final List<SessionOps> nos;
    private final RelogioS3 relogio;
    private final S3Parametros p;
    private final S3Registro registro;
    private final Random rngTardia;
    private final List<AtorSonda> atores = new ArrayList<>();
    private final List<Logout> logouts = new ArrayList<>();
    private final List<Leitura> leituras = new ArrayList<>();
    private long seq;
    private long agendadas;
    private long descartadasFim;
    private long candidatasTardias;
    private long amostraTardia;
    private long semNoTardio;
    private boolean encerrada;

    /**
     * @param nos       um {@link SessionOps} por servidor de aplicacao, na ordem de {@code --servers}
     * @param relogio   relogio da simulacao (o mesmo dos atores)
     * @param p         parametros do S3 (atrasos e limite do tardio)
     * @param registro  registro do S3 (so os contadores {@code sonda|...})
     * @param rngTardia gerador da amostra do replay tardio
     */
    public SondaReplay(List<SessionOps> nos, RelogioS3 relogio, S3Parametros p, S3Registro registro,
                       Random rngTardia) {
        if (nos.size() < 2) {
            throw new IllegalArgumentException("a sonda de replay exige ao menos 2 servidores de aplicacao");
        }
        this.nos = List.copyOf(nos);
        this.relogio = relogio;
        this.p = p;
        this.registro = registro;
        this.rngTardia = rngTardia;
        for (int i = 0; i < nos.size(); i++) {
            atores.add(new AtorSonda(i));
        }
    }

    /** Um ator por no alvo, na ordem dos nos ({@code sonda-0}, {@code sonda-1}, ...). */
    public List<AtorS3> atores() {
        return Collections.unmodifiableList(atores);
    }

    /**
     * Logout de {@code sid} confirmado ao cliente pelo no {@code no} em {@code tNs}: agenda o replay imediato
     * no no {@code (no + 1) mod n} com atraso U(min, max) sorteado de {@code rng} (o gerador da sonda do
     * usuario) e guarda o logout para o replay tardio. Depois de {@link #replayTardio} nao agenda mais nada.
     */
    public void logoutConfirmado(String sid, int no, long tNs, Random rng) {
        long atraso = ContextoS3.uniformeNs(rng, S3Parametros.segundosParaNs(p.sondaMinSeg),
                S3Parametros.segundosParaNs(p.sondaMaxSeg));
        int alvo = (no + 1) % nos.size();
        Replay r;
        synchronized (this) {
            if (encerrada) {
                return;
            }
            long s = seq++;
            logouts.add(new Logout(sid, no, tNs, s));
            r = new Replay(sid, no, alvo, tNs, tNs + atraso, s);
            agendadas++;
        }
        registro.contar("sonda|imediata|agendada");
        atores.get(alvo).enfileirar(r);
    }

    /**
     * Replay tardio, no fim da carga (depois dos atores pararem): descarta os replays imediatos pendentes e
     * rele, por um no diferente do que confirmou, cada sid com logout confirmado nas fases {@code durante*}
     * ou {@code recuperacao} de {@code marcadores} (amostra se passar do limite). Sem marcadores ou sem
     * marcador {@code start}, nao ha falha e nao ha replay tardio.
     *
     * @param marcadores   marcadores da execucao, com W_recovery ({@code null} = sem {@code --marker})
     * @param paraEpochNs  converte um instante do {@link RelogioS3} em epoch-ns (a base dos marcadores)
     * @return o numero de sids relidas (com ou sem sucesso)
     */
    public int replayTardio(Marcadores marcadores, LongUnaryOperator paraEpochNs) {
        List<Logout> candidatas = new ArrayList<>();
        synchronized (this) {
            encerrada = true;
            for (AtorSonda a : atores) {
                int n = a.esvaziar();
                descartadasFim += n;
                for (int i = 0; i < n; i++) {
                    registro.contar("sonda|imediata|descartada-fim");
                }
            }
            if (marcadores == null || marcadores.inicioNs() == null) {
                return 0;
            }
            for (Logout l : logouts) {
                Marcadores.Fase f = marcadores.faseDe(paraEpochNs.applyAsLong(l.tNs()));
                if (f != Marcadores.Fase.ANTES && f != Marcadores.Fase.POS_CONVERGENCIA) {
                    candidatas.add(l);
                }
            }
            candidatasTardias = candidatas.size();
        }
        Comparator<Logout> ordem = Comparator.comparingLong(Logout::tNs).thenComparingLong(Logout::seq);
        candidatas.sort(ordem);
        if (candidatas.size() > p.sondaTardiaMax) {
            Collections.shuffle(candidatas, rngTardia);
            candidatas = new ArrayList<>(candidatas.subList(0, p.sondaTardiaMax));
            candidatas.sort(ordem);
        }
        synchronized (this) {
            amostraTardia = candidatas.size();
        }
        Set<Integer> indisponiveis = new HashSet<>();
        for (Logout l : candidatas) {
            boolean lida = false;
            for (int d = 1; d < nos.size(); d++) {
                int alvo = (l.no() + d) % nos.size();
                if (indisponiveis.contains(alvo)) {
                    continue;
                }
                long t = relogio.agoraNs();
                OpResult r = nos.get(alvo).validarSonda(l.sid(), true);
                registrar(new Leitura(TARDIO, l.sid(), l.no(), alvo, l.tNs(), t, t, AplicacaoS3.descrever(r)));
                lida = true;
                if (!AplicacaoS3.ehErro(r)) {
                    break;
                }
                indisponiveis.add(alvo);
            }
            if (!lida) {
                synchronized (this) {
                    semNoTardio++;
                }
                registro.contar("sonda|tardia|sem-no");
            }
        }
        return candidatas.size();
    }

    private void registrar(Leitura l) {
        synchronized (this) {
            leituras.add(l);
        }
        registro.contar("sonda|" + (IMEDIATO.equals(l.tipo()) ? "imediata" : "tardia") + "|" + l.resultado());
    }

    /** Copia das leituras feitas, na ordem em que terminaram. */
    public List<Leitura> leituras() {
        synchronized (this) {
            return new ArrayList<>(leituras);
        }
    }

    /**
     * Linha do stdout (so com a sonda ligada): agendadas, lidas por desfecho, descartadas no fim e os totais
     * do tardio. As violacoes da sonda saem na linha {@code Sonda violacoes:} de {@link #linhaViolacoes}.
     */
    public String linhaResumo() {
        List<Leitura> ls = leituras();
        StringBuilder sb = new StringBuilder("Sonda replay:");
        synchronized (this) {
            sb.append(" imediatas_agendadas=").append(agendadas);
            sb.append(' ').append(desfechos(ls, IMEDIATO, "imediatas"));
            sb.append(" imediatas_descartadas_fim=").append(descartadasFim);
            sb.append(" tardias_candidatas=").append(candidatasTardias);
            sb.append(" tardias_amostra=").append(amostraTardia);
            sb.append(' ').append(desfechos(ls, TARDIO, "tardias"));
            sb.append(" tardias_sem_no=").append(semNoTardio);
        }
        return sb.toString();
    }

    /** Linha do stdout com as violacoes reveladas pela sonda (origem {@code sonda}). */
    public static String linhaViolacoes(InvariantAuditor auditor) {
        return "Sonda violacoes: I1=" + auditor.total("I1", InvariantAuditor.ORIGEM_SONDA)
                + " I4=" + auditor.total("I4", InvariantAuditor.ORIGEM_SONDA)
                + " total=" + auditor.total(InvariantAuditor.ORIGEM_SONDA);
    }

    private static String desfechos(List<Leitura> ls, String tipo, String prefixo) {
        long lidas = 0;
        long valid = 0;
        long invalid = 0;
        long ausente = 0;
        long erro = 0;
        for (Leitura l : ls) {
            if (!tipo.equals(l.tipo())) {
                continue;
            }
            lidas++;
            switch (l.resultado()) {
                case "OK_VALIDATE:VALID" -> valid++;
                case "OK_VALIDATE:INVALID" -> invalid++;
                case "NOT_FOUND" -> ausente++;
                default -> erro++;
            }
        }
        return prefixo + "_lidas=" + lidas + " " + prefixo + "_valid=" + valid + " " + prefixo + "_invalid=" + invalid
                + " " + prefixo + "_not_found=" + ausente + " " + prefixo + "_erro=" + erro;
    }

    /** Ator da sonda de um no alvo: executa, um por vez, os replays imediatos devidos a esse no. */
    private final class AtorSonda implements AtorS3 {

        private final int alvo;
        private final PriorityQueue<Replay> fila = new PriorityQueue<>(ORDEM);

        AtorSonda(int alvo) {
            this.alvo = alvo;
        }

        @Override
        public String id() {
            return "sonda-" + alvo;
        }

        void enfileirar(Replay r) {
            synchronized (fila) {
                fila.add(r);
            }
        }

        int esvaziar() {
            synchronized (fila) {
                int n = fila.size();
                fila.clear();
                return n;
            }
        }

        /**
         * O prazo do primeiro replay da fila, limitado a {@link #PASSO_NS} a partir de agora: na execucao em
         * threads a thread do ator acorda para ver um replay novo com prazo mais cedo.
         */
        @Override
        public long proximoNs() {
            long teto = relogio.agoraNs() + PASSO_NS;
            synchronized (fila) {
                Replay r = fila.peek();
                return r == null ? teto : Math.min(r.tDevidoNs(), teto);
            }
        }

        @Override
        public void passo() {
            Replay r;
            long agora = relogio.agoraNs();
            synchronized (fila) {
                r = fila.peek();
                if (r == null || r.tDevidoNs() > agora) {
                    return;
                }
                fila.poll();
            }
            OpResult res = nos.get(alvo).validarSonda(r.sid(), false);
            registrar(new Leitura(IMEDIATO, r.sid(), r.noLogout(), alvo, r.tLogoutNs(), r.tDevidoNs(), agora,
                    AplicacaoS3.descrever(res)));
        }
    }
}
