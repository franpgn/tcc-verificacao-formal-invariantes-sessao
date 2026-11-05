package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W-2b (P-5, item 3): contencao do {@code ISPN004007} do cliente Hot Rod no stderr.
 *
 * <p><b>Origem.</b> No I-3b (05/11, {@code wt-w4/runs/i3b/}) os bracos ativos gravaram 9,89 MB e 12,12 MB de stderr
 * por execucao; 98,7 % eram linhas {@code ISPN004007} com a pilha inteira (6 450 e 6 348 ocorrencias, cerca de 1,5 KB
 * cada), quase todas de operacoes contra o no parado pelo AC-5. O log do cliente roda nas threads de E/S do Netty e
 * vai ao stderr redirecionado para arquivo: e disco no caminho do cliente Hot Rod (P-5).</p>
 *
 * <p><b>O que e o {@code ISPN004007}.</b> No cliente 15.0.0, {@code RetryOnFailureOperation.logAndRetryOrFail} so o
 * grava quando a operacao esgotou as novas tentativas ({@code Log.HOTROD.exceptionAndNoRetriesLeft}, nivel ERROR,
 * "Retry 1 out of 1"); cada nova tentativa em si so sai em TRACE. Cada linha e, portanto, uma operacao que falhou e
 * cuja excecao chegou a quem chamou (carga, auditor ou sonda, que ja contam suas falhas). O logger e o
 * {@code org.infinispan.HOTROD}, compartilhado por todas as mensagens do cliente; JBoss Logging escreve nele pelo
 * {@code java.util.logging} (sem logback nem log4j no classpath; formato do stderr do I-3b). Baixar o nivel nao
 * isola o {@code ISPN004007}: por isso a contencao e um {@link Filter} que olha so essa mensagem.</p>
 *
 * <p><b>Regra.</b> Por repeticao ({@link #reiniciar()}) e por chave (no, motivo): a primeira ocorrencia sai inteira,
 * com a pilha ({@value #IMPRESSAS_POR_CHAVE} por chave, para que cada tipo de falha distinto tenha um exemplo completo
 * no stderr); a segunda sai como um aviso de uma linha, sem pilha, que aponta para o resumo; as demais so sao
 * contadas, com o primeiro e o ultimo instante. O resumo vai ao stdout no fim da repeticao ({@link #linhaResumo()},
 * linha {@code Hot Rod ISPN004007:}). Qualquer outra mensagem do cliente passa intacta. Nao ha limite no numero de
 * chaves (sao no maximo nos x motivos).</p>
 *
 * <p>No: o endereco do servidor de uma {@link TransportException} da cadeia, senao o endereco da conexao
 * ({@code connection=/h:p}), senao o primeiro {@code host/ip:porta} das mensagens da cadeia; senao
 * {@value #NO_DESCONHECIDO}. Motivo: nome simples da excecao, mais o primeiro codigo {@code ISPN} das mensagens da
 * cadeia ou, sem codigo, o nome simples da causa raiz se for outra classe.</p>
 */
public final class ContencaoLogHotRod implements Filter {

    /** Logger das mensagens do cliente Hot Rod 15.0.0 ({@code Log.HOTROD}). */
    static final String LOGGER = "org.infinispan.HOTROD";

    /** Mensagem contida: operacao que esgotou as novas tentativas. */
    static final String CODIGO = "ISPN004007";

    /**
     * Ocorrencias impressas inteiras por (no, motivo) em cada repeticao. Origem: W-2b item 3 (um contador por no e
     * motivo em vez de uma linha por ocorrencia, sem esconder informacao): uma pilha completa por tipo de falha.
     */
    static final int IMPRESSAS_POR_CHAVE = 1;

    static final String NO_DESCONHECIDO = "desconhecido";
    static final String SEM_EXCECAO = "sem-excecao";

    /** Prefixo da linha do stdout. */
    static final String PREFIXO_RESUMO = "Hot Rod " + CODIGO + ":";

    /** Profundidade maxima da cadeia de causas examinada (protege de cadeias longas). */
    private static final int PROFUNDIDADE = 16;

    private static final Pattern CONEXAO = Pattern.compile(
            "connection=([\\w.\\-]*)/(<unresolved>|\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9A-Fa-f:.]+\\]):(\\d{1,5})\\b");
    private static final Pattern ENDERECO = Pattern.compile(
            "([\\w.\\-]*)/(<unresolved>|\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9A-Fa-f:.]+\\]):(\\d{1,5})\\b");
    private static final Pattern CODIGO_ISPN = Pattern.compile("ISPN\\d{6}");

    /** Referencia forte ao logger: o {@code LogManager} so guarda referencias fracas e o filtro iria junto. */
    private static final Logger ALVO = Logger.getLogger(LOGGER);
    private static final ContencaoLogHotRod GLOBAL = new ContencaoLogHotRod();

    /** Chave da contagem. */
    record Chave(String no, String motivo) {
    }

    /** Contagem de uma chave. */
    static final class Contagem {
        private final AtomicLong n = new AtomicLong();
        private final AtomicLong primeiraNs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong ultimaNs = new AtomicLong(Long.MIN_VALUE);

        long registrar(long instanteNs) {
            primeiraNs.accumulateAndGet(instanteNs, Math::min);
            ultimaNs.accumulateAndGet(instanteNs, Math::max);
            return n.incrementAndGet();
        }
    }

    private final ConcurrentHashMap<Chave, Contagem> contagens = new ConcurrentHashMap<>();
    private volatile Filter anterior;

    /** Contencao avulsa (testes); em producao use {@link #instalar()}. */
    ContencaoLogHotRod() {
    }

    /**
     * Instala a contencao no logger {@value #LOGGER} (idempotente). Um filtro que ja estivesse nele continua valendo
     * antes desta.
     */
    public static synchronized ContencaoLogHotRod instalar() {
        Filter f = ALVO.getFilter();
        if (f != GLOBAL) {
            if (f != null) {
                GLOBAL.encadear(f);
            }
            ALVO.setFilter(GLOBAL);
        }
        return GLOBAL;
    }

    /** Filtro que vale antes desta contencao. */
    void encadear(Filter f) {
        if (f == this) {
            throw new IllegalArgumentException("filtro encadeado nele mesmo");
        }
        this.anterior = f;
    }

    @Override
    public boolean isLoggable(LogRecord r) {
        Filter a = anterior;
        if (a != null && !a.isLoggable(r)) {
            return false;
        }
        String m = r.getMessage();
        if (m == null || !m.startsWith(CODIGO)) {
            return true;
        }
        Throwable t = r.getThrown();
        Chave k = new Chave(noDe(t), motivoDe(t));
        long n = contagens.computeIfAbsent(k, x -> new Contagem()).registrar(epochNs(r.getInstant()));
        if (n <= IMPRESSAS_POR_CHAVE) {
            return true;
        }
        if (n == IMPRESSAS_POR_CHAVE + 1) {
            r.setMessage(CODIGO + " [W-2b] as proximas ocorrencias de no=" + k.no() + " motivo=" + k.motivo()
                    + " so sao contadas (sem pilha); o total por no e motivo sai na linha '" + PREFIXO_RESUMO
                    + "' do stdout no fim da repeticao");
            r.setThrown(null);
            r.setParameters(null);
            return true;
        }
        return false;
    }

    /** Zera as contagens (inicio de cada repeticao). */
    public void reiniciar() {
        contagens.clear();
    }

    /** Ocorrencias contadas desde o ultimo {@link #reiniciar()}. */
    public long total() {
        long s = 0;
        for (Contagem c : contagens.values()) {
            s += c.n.get();
        }
        return s;
    }

    /** Ocorrencias impressas inteiras (com a pilha). */
    public long inteiras() {
        long s = 0;
        for (Contagem c : contagens.values()) {
            s += Math.min(c.n.get(), IMPRESSAS_POR_CHAVE);
        }
        return s;
    }

    /** Ocorrencias nao impressas inteiras (o aviso de uma linha conta aqui). */
    public long contidas() {
        return total() - inteiras();
    }

    /**
     * {@code Hot Rod ISPN004007: total=N inteiras=I contidas=C chaves=K} e, por chave, em ordem decrescente de
     * {@code n} (empate: no, motivo), {@code | no=... motivo=... n=... t_primeira_ns=... t_ultima_ns=...}. Instantes
     * em epoch-ns do relogio de parede do registro de log.
     */
    public String linhaResumo() {
        List<Map.Entry<Chave, Contagem>> l = new ArrayList<>(contagens.entrySet());
        l.sort(Comparator.<Map.Entry<Chave, Contagem>>comparingLong(e -> -e.getValue().n.get())
                .thenComparing(e -> e.getKey().no()).thenComparing(e -> e.getKey().motivo()));
        long total = 0;
        long inteiras = 0;
        StringBuilder partes = new StringBuilder();
        for (Map.Entry<Chave, Contagem> e : l) {
            Contagem c = e.getValue();
            long n = c.n.get();
            total += n;
            inteiras += Math.min(n, IMPRESSAS_POR_CHAVE);
            partes.append(" | no=").append(e.getKey().no()).append(" motivo=").append(e.getKey().motivo())
                    .append(" n=").append(n).append(" t_primeira_ns=").append(c.primeiraNs.get())
                    .append(" t_ultima_ns=").append(c.ultimaNs.get());
        }
        return PREFIXO_RESUMO + " total=" + total + " inteiras=" + inteiras + " contidas=" + (total - inteiras)
                + " chaves=" + l.size() + partes;
    }

    // ------------------------------------------------------------------ chave

    /** No da falha (ver a classe). */
    static String noDe(Throwable t) {
        List<Throwable> cadeia = cadeia(t);
        for (Throwable x : cadeia) {
            if (x instanceof TransportException te) {
                String r = rotulo(te.getServerAddress());
                if (r != null) {
                    return r;
                }
            }
        }
        for (Pattern p : List.of(CONEXAO, ENDERECO)) {
            for (Throwable x : cadeia) {
                String m = x.getMessage();
                if (m == null) {
                    continue;
                }
                Matcher mm = p.matcher(m);
                if (mm.find()) {
                    String host = mm.group(2).equals("<unresolved>") ? mm.group(1) : mm.group(2);
                    if (!host.isEmpty()) {
                        return host + ":" + mm.group(3);
                    }
                }
            }
        }
        return NO_DESCONHECIDO;
    }

    /** Motivo da falha (ver a classe). */
    static String motivoDe(Throwable t) {
        if (t == null) {
            return SEM_EXCECAO;
        }
        List<Throwable> cadeia = cadeia(t);
        String nome = nome(t);
        for (Throwable x : cadeia) {
            String m = x.getMessage();
            if (m != null) {
                Matcher mm = CODIGO_ISPN.matcher(m);
                if (mm.find()) {
                    return nome + ":" + mm.group();
                }
            }
        }
        Throwable raiz = cadeia.get(cadeia.size() - 1);
        return raiz.getClass() == t.getClass() ? nome : nome + ":" + nome(raiz);
    }

    private static String rotulo(SocketAddress a) {
        if (!(a instanceof InetSocketAddress i)) {
            return null;
        }
        String host = i.isUnresolved() || i.getAddress() == null ? i.getHostString() : i.getAddress().getHostAddress();
        if (host == null || host.isEmpty()) {
            return null;
        }
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + i.getPort();
    }

    /** {@code t} e suas causas, sem repetir (ciclo) e ate {@value #PROFUNDIDADE}. */
    private static List<Throwable> cadeia(Throwable t) {
        List<Throwable> l = new ArrayList<>();
        Set<Throwable> vistos = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable x = t; x != null && l.size() < PROFUNDIDADE && vistos.add(x); x = x.getCause()) {
            l.add(x);
        }
        return l;
    }

    private static String nome(Throwable t) {
        String s = t.getClass().getSimpleName();
        return s.isEmpty() ? t.getClass().getName() : s;
    }

    private static long epochNs(Instant i) {
        return i == null ? 0L : i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }

    @Override
    public String toString() {
        return "ContencaoLogHotRod[" + Objects.toString(anterior, "sem filtro anterior") + "]";
    }
}
