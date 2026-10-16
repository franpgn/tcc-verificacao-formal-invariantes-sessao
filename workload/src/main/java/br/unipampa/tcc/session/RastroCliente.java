package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B-33 (E1, item 4, decisao Q-10): rastro de operacoes confirmadas mantido no
 * <b>cliente</b>, que permite classificar por que uma identidade esta
 * {@code BLOCKED} sem alterar {@link IdentityState} nem o dominio.
 *
 * <p>Registra, a partir de cada evento emitido por {@link SessionOps}: o mapa
 * sid para identidade (de cada {@code Login} bem-sucedido, ja que o CSV de
 * {@code Login} tem a identidade como chave, nao a sid) e, por identidade, a
 * sequencia de {@code Block}/{@code Unblock} com seu resultado (confirmado =
 * codigo que nao e de erro). O motivo e conhecivel por construcao: so
 * {@code block()} produz bloqueio administrativo; {@code incrementar()} (limite)
 * nunca toca {@code sessions}. Vale tambem durante o warm-up (o estado do grid
 * nao distingue warm-up).</p>
 *
 * <p>Ordem temporal: eventos ordenados por {@code inicioNs} (instante em que o
 * efeito pode comecar; desempate pela ordem de registro). Todos os tempos sao
 * epoch-ns (ou, sem {@link EpochClock}, nanoTime monotono; nao misture).</p>
 */
public final class RastroCliente {

    /** Classificacao do motivo pelo qual uma identidade esta BLOCKED. */
    public enum Bloqueio {
        ADMINISTRATIVO("BLOQUEIO-ADMINISTRATIVO"),
        POR_LIMITE("BLOQUEIO-POR-LIMITE"),
        INCERTO("BLOQUEIO-INCERTO"),
        INEXPLICADO("BLOQUEIO-INEXPLICADO");

        private final String rotulo;

        Bloqueio(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    /** Evento Block/Unblock; {@code confirmado} = resultado sem erro. */
    public record EventoAdmin(String operacao, long inicioNs, long fimNs, boolean confirmado, long seq) {
        public boolean ehBlock() { return SessionOps.OP_BLOCK.equals(operacao); }
        public boolean ehUnblock() { return SessionOps.OP_UNBLOCK.equals(operacao); }
    }

    private static final Comparator<EventoAdmin> ORDEM =
            Comparator.comparingLong(EventoAdmin::inicioNs).thenComparingLong(EventoAdmin::seq);

    /**
     * Tolerancia entre os dois relogios usados na reclassificacao inline: o instante da
     * violacao vem de {@code Instant.now()} (relogio de parede, granularidade grossa em
     * alguns SO) e o dos eventos vem do relogio monotonico convertido por
     * {@link EpochClock}. Um Block que comecou "agora" pode aparecer alguns ms depois
     * da violacao; {@link #classificarEm} soma esta folga ao instante consultado.
     */
    public static final long TOLERANCIA_RELOGIO_NS = 50_000_000L;

    private final long toleranciaNs;
    private final Map<String, String> identidadePorSid = new ConcurrentHashMap<>();
    private final Map<String, List<EventoAdmin>> adminPorIdentidade = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public RastroCliente() {
        this(TOLERANCIA_RELOGIO_NS);
    }

    /** {@code toleranciaNs} so afeta {@link #classificarEm}; use 0 em testes de ordem exata. */
    public RastroCliente(long toleranciaNs) {
        this.toleranciaNs = toleranciaNs;
    }

    /** Chamado por {@link SessionOps} a cada evento (mesmos argumentos de {@code emitir}). */
    public void registrar(String operacao, long inicioNs, long fimNs, OpResult resultado, String chave) {
        if (SessionOps.OP_LOGIN.equals(operacao)) {
            if (resultado.code() == OpResult.Code.OK_LOGIN) {
                identidadePorSid.put(resultado.sessionId(), chave);
            }
            return;
        }
        if (SessionOps.OP_BLOCK.equals(operacao) || SessionOps.OP_UNBLOCK.equals(operacao)) {
            boolean confirmado = !resultado.returnCode().startsWith("ERROR_");
            adminPorIdentidade
                    .computeIfAbsent(chave, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new EventoAdmin(operacao, inicioNs, fimNs, confirmado, seq.getAndIncrement()));
        }
    }

    public Optional<String> identidadeDe(String sid) {
        return Optional.ofNullable(identidadePorSid.get(sid));
    }

    /** Identidades que tiveram ao menos um Block (confirmado ou nao) tentado. */
    public Set<String> identidadesComBlock() {
        Set<String> out = new java.util.TreeSet<>();
        for (Map.Entry<String, List<EventoAdmin>> e : adminPorIdentidade.entrySet()) {
            if (eventos(e.getKey()).stream().anyMatch(EventoAdmin::ehBlock)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Eventos Block/Unblock da identidade em ordem temporal. */
    public List<EventoAdmin> eventos(String identidade) {
        List<EventoAdmin> l = adminPorIdentidade.get(identidade);
        if (l == null) {
            return List.of();
        }
        List<EventoAdmin> copia;
        synchronized (l) {
            copia = new ArrayList<>(l);
        }
        copia.sort(ORDEM);
        return copia;
    }

    /** Ultimo evento <b>confirmado</b> (Block ou Unblock) da identidade. */
    public Optional<EventoAdmin> ultimoConfirmado(String identidade) {
        return ultimoConfirmadoAte(identidade, Long.MAX_VALUE);
    }

    private Optional<EventoAdmin> ultimoConfirmadoAte(String identidade, long tNs) {
        EventoAdmin ultimo = null;
        for (EventoAdmin e : eventos(identidade)) {
            if (e.inicioNs() <= tNs && e.confirmado()) {
                ultimo = e;
            }
        }
        return Optional.ofNullable(ultimo);
    }

    /** Existe Block com erro (efeito desconhecido) depois do ultimo evento confirmado ate {@code tNs}? */
    private boolean blockIncerto(String identidade, long tNs) {
        Optional<EventoAdmin> ult = ultimoConfirmadoAte(identidade, tNs);
        for (EventoAdmin e : eventos(identidade)) {
            if (e.inicioNs() <= tNs && e.ehBlock() && !e.confirmado()
                    && (ult.isEmpty() || ORDEM.compare(e, ult.get()) > 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Classificacao na varredura final (Q-10): {@code ADMINISTRATIVO} se o ultimo
     * evento confirmado e Block; {@code POR_LIMITE} se {@code contadorFalhas >= limite}
     * e nao ha Block confirmado sem Unblock posterior; {@code INCERTO} se ha Block
     * com erro (efeito desconhecido) depois do ultimo evento confirmado;
     * {@code INEXPLICADO} nos demais casos (anomalia).
     */
    public Bloqueio classificarFinal(String identidade, long contadorFalhas, int limite) {
        Optional<EventoAdmin> ult = ultimoConfirmado(identidade);
        if (ult.isPresent() && ult.get().ehBlock()) {
            return Bloqueio.ADMINISTRATIVO;
        }
        if (contadorFalhas >= limite) {
            return Bloqueio.POR_LIMITE;
        }
        if (blockIncerto(identidade, Long.MAX_VALUE)) {
            return Bloqueio.INCERTO;
        }
        return Bloqueio.INEXPLICADO;
    }

    /**
     * Classificacao no instante {@code tNs} (reclassificacao da violacao inline de
     * I2 "valida sob bloqueio"): ha leitura BLOCKED, entao o bloqueio e administrativo
     * se o ultimo Block/Unblock confirmado ate ali e Block; incerto se ha Block com
     * erro pendente; senao e por limite <b>por exclusao</b> (so {@code incrementar()}
     * produz BLOCKED sem Block; o contador no instante nao e conhecido). Aplica
     * {@link #TOLERANCIA_RELOGIO_NS} ao instante consultado (limitacao documentada).
     */
    public Bloqueio classificarEm(String identidade, long tConsultaNs) {
        long tNs = tConsultaNs + toleranciaNs;
        Optional<EventoAdmin> ult = ultimoConfirmadoAte(identidade, tNs);
        if (ult.isPresent() && ult.get().ehBlock()) {
            return Bloqueio.ADMINISTRATIVO;
        }
        if (blockIncerto(identidade, tNs)) {
            return Bloqueio.INCERTO;
        }
        return Bloqueio.POR_LIMITE;
    }

    /** Houve Unblock com erro depois do Block confirmado {@code bloco} (efeito desconhecido)? */
    public boolean unblockIncertoDepois(String identidade, EventoAdmin bloco) {
        for (EventoAdmin e : eventos(identidade)) {
            if (e.ehUnblock() && !e.confirmado() && ORDEM.compare(e, bloco) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Rotulo de classificacao de uma violacao inline de I2 (coluna extra do CSV de violacoes). */
    public String classificarViolacaoI2(String sid, long tNs) {
        Optional<String> id = identidadeDe(sid);
        if (id.isEmpty()) {
            return "DESCONHECIDA-SEM-LOGIN";
        }
        return classeDeI2(classificarEm(id.get(), tNs));
    }

    /** Mapeia o motivo do bloqueio para a classe da violacao de I2. */
    public static String classeDeI2(Bloqueio b) {
        return switch (b) {
            case ADMINISTRATIVO -> "REAL";
            case POR_LIMITE -> "ESPERADA-POR-DESENHO";
            case INCERTO -> "INCERTA";
            case INEXPLICADO -> "INEXPLICADA";
        };
    }
}
