package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.TransportException;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
 * W-2b (P-5, item 3; decisao 2 do coordenador de 05/11, P-46): contencao do {@code ISPN004007} e do
 * {@code ISPN004005} do cliente Hot Rod no stderr.
 *
 * <p><b>Origem.</b> No I-3b (05/11, {@code wt-w4/runs/i3b/}) os bracos ativos gravaram 9,89 MB e 12,12 MB de stderr
 * por execucao; 98,7 % eram linhas {@code ISPN004007} com a pilha inteira (6 450 e 6 348 ocorrencias, cerca de 1,5 KB
 * cada), quase todas de operacoes contra o no parado pelo AC-5. O {@code shadow} gravou 1,50 MB, quase tudo
 * {@code ISPN004005} (3 897 linhas, sem pilha), das leituras da sonda S_lag pelo no cortado em DEGRADED. O log do
 * cliente roda nas threads de E/S do Netty e vai ao stderr redirecionado para arquivo: e disco no caminho do cliente
 * Hot Rod (P-5).</p>
 *
 * <p><b>As mensagens.</b> No cliente 15.0.0, as duas vao ao logger {@code org.infinispan.HOTROD} ({@code Log.HOTROD}),
 * compartilhado por todas as mensagens do cliente, pelo {@code java.util.logging} (JBoss Logging sem logback nem log4j
 * no classpath; formato do stderr do I-3b). {@code ISPN004007} ({@code exceptionAndNoRetriesLeft}, ERROR, com a
 * excecao): uma operacao que esgotou as novas tentativas ("Retry 1 out of 1"; a nova tentativa em si so sai em TRACE).
 * {@code ISPN004005} ({@code errorFromServer}, WARN, sem excecao): um erro devolvido pelo servidor, com o texto dele;
 * o {@code Codec20} recebe o endereco do servidor, mas so grava o texto, entao o no sai {@value #NO_DESCONHECIDO}
 * salvo se o texto trouxer um {@code host/ip:porta}. Nos dois casos quem chamou recebe a excecao (carga, auditor ou
 * sonda, que ja contam suas falhas). Baixar o nivel nao isola essas mensagens: por isso a contencao e um
 * {@link Filter} que olha so a lista fechada {@link #CODIGOS}.</p>
 *
 * <p><b>Regra.</b> Por repeticao ({@link #reiniciar()}), por codigo e por chave (no, motivo): a primeira ocorrencia
 * sai inteira ({@value #IMPRESSAS_POR_CHAVE} por chave, para que cada tipo de falha distinto tenha um exemplo
 * completo no stderr); a segunda sai como um aviso de uma linha, sem pilha, que aponta para o resumo; as demais so
 * sao contadas, com o primeiro e o ultimo instante. O resumo vai ao stdout no fim da repeticao, uma linha por codigo
 * ({@link #linhasResumo()}: {@code Hot Rod ISPN004007:} e {@code Hot Rod ISPN004005:}). Qualquer outra mensagem do
 * cliente passa intacta. Nao ha limite no numero de chaves (sao no maximo nos x motivos).</p>
 *
 * <p><b>Chave do {@code ISPN004007}.</b> No: o endereco do servidor de uma {@link TransportException} da cadeia,
 * senao o endereco da conexao ({@code connection=/h:p}), senao o primeiro {@code host/ip:porta} das mensagens da
 * cadeia; senao {@value #NO_DESCONHECIDO}. Motivo: nome simples da excecao, mais o primeiro codigo {@code ISPN} das
 * mensagens da cadeia ou, sem codigo, o nome simples da causa raiz se for outra classe.</p>
 *
 * <p><b>Chave do {@code ISPN004005}.</b> No: o primeiro {@code host/ip:porta} do texto do servidor, senao
 * {@value #NO_DESCONHECIDO}. Motivo: o codigo do servidor (primeiro {@code ISPN} do texto, por exemplo
 * {@code ISPN000306}), senao o nome simples da excecao do servidor, senao {@value #SEM_CODIGO}.</p>
 */
public final class ContencaoLogHotRod implements Filter {

    /** Logger das mensagens do cliente Hot Rod 15.0.0 ({@code Log.HOTROD}). */
    static final String LOGGER = "org.infinispan.HOTROD";

    /** Operacao que esgotou as novas tentativas (ERROR, com a excecao). */
    static final String ISPN004007 = "ISPN004007";

    /** Erro devolvido pelo servidor (WARN, so o texto do servidor). */
    static final String ISPN004005 = "ISPN004005";

    /** Lista fechada de mensagens contidas, na ordem das linhas do stdout. */
    static final List<String> CODIGOS = List.of(ISPN004007, ISPN004005);

    /**
     * Ocorrencias impressas inteiras por (no, motivo), codigo e repeticao. Origem: W-2b item 3 (um contador por no e
     * motivo em vez de uma linha por ocorrencia, sem esconder informacao): um exemplo completo por tipo de falha.
     */
    static final int IMPRESSAS_POR_CHAVE = 1;

    static final String NO_DESCONHECIDO = "desconhecido";
    static final String SEM_EXCECAO = "sem-excecao";
    static final String SEM_CODIGO = "sem-codigo";

    /** Prefixo do texto do servidor no {@code ISPN004005}. */
    private static final String PREFIXO_4005 = ISPN004005 + ": Error received from the server:";

    /** Profundidade maxima da cadeia de causas examinada (protege de cadeias longas). */
    private static final int PROFUNDIDADE = 16;

    private static final Pattern CONEXAO = Pattern.compile(
            "connection=([\\w.\\-]*)/(<unresolved>|\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9A-Fa-f:.]+\\]):(\\d{1,5})\\b");
    private static final Pattern ENDERECO = Pattern.compile(
            "([\\w.\\-]*)/(<unresolved>|\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9A-Fa-f:.]+\\]):(\\d{1,5})\\b");
    private static final Pattern CODIGO_ISPN = Pattern.compile("ISPN\\d{6}");
    private static final Pattern EXCECAO = Pattern.compile("^\\s*([\\w.$]+(?:Exception|Error))\\b");

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

    /** Contagens por codigo (as chaves de cada codigo a parte). */
    private final Map<String, ConcurrentHashMap<Chave, Contagem>> contagens;
    private volatile Filter anterior;

    /** Contencao avulsa (testes); em producao use {@link #instalar()}. */
    ContencaoLogHotRod() {
        Map<String, ConcurrentHashMap<Chave, Contagem>> m = new java.util.LinkedHashMap<>();
        for (String c : CODIGOS) {
            m.put(c, new ConcurrentHashMap<>());
        }
        this.contagens = Collections.unmodifiableMap(m);
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
        if (m == null) {
            return true;
        }
        String codigo;
        Chave k;
        if (m.startsWith(ISPN004007)) {
            codigo = ISPN004007;
            Throwable t = r.getThrown();
            k = new Chave(noDe(t), motivoDe(t));
        } else if (m.startsWith(ISPN004005)) {
            codigo = ISPN004005;
            k = chave4005(m);
        } else {
            return true;
        }
        long n = contagens.get(codigo).computeIfAbsent(k, x -> new Contagem()).registrar(epochNs(r.getInstant()));
        if (n <= IMPRESSAS_POR_CHAVE) {
            return true;
        }
        if (n == IMPRESSAS_POR_CHAVE + 1) {
            r.setMessage(codigo + " [W-2b] as proximas ocorrencias de no=" + k.no() + " motivo=" + k.motivo()
                    + " so sao contadas (sem pilha); o total por no e motivo sai na linha '" + prefixo(codigo)
                    + "' do stdout no fim da repeticao");
            r.setThrown(null);
            r.setParameters(null);
            return true;
        }
        return false;
    }

    /** Zera as contagens (inicio de cada repeticao). */
    public void reiniciar() {
        contagens.values().forEach(Map::clear);
    }

    /** Ocorrencias de {@code codigo} contadas desde o ultimo {@link #reiniciar()}. */
    public long total(String codigo) {
        long s = 0;
        for (Contagem c : mapa(codigo).values()) {
            s += c.n.get();
        }
        return s;
    }

    /** Ocorrencias de {@code codigo} impressas inteiras. */
    public long inteiras(String codigo) {
        long s = 0;
        for (Contagem c : mapa(codigo).values()) {
            s += Math.min(c.n.get(), IMPRESSAS_POR_CHAVE);
        }
        return s;
    }

    /** Ocorrencias de {@code codigo} nao impressas inteiras (o aviso de uma linha conta aqui). */
    public long contidas(String codigo) {
        return total(codigo) - inteiras(codigo);
    }

    /** As linhas de {@link #linhaResumo(String)} de todos os {@link #CODIGOS}, separadas por fim de linha. */
    public String linhasResumo() {
        List<String> l = new ArrayList<>();
        for (String c : CODIGOS) {
            l.add(linhaResumo(c));
        }
        return String.join(System.lineSeparator(), l);
    }

    /**
     * {@code Hot Rod <codigo>: total=N inteiras=I contidas=C chaves=K} e, por chave, em ordem decrescente de
     * {@code n} (empate: no, motivo), {@code | no=... motivo=... n=... t_primeira_ns=... t_ultima_ns=...}. Instantes
     * em epoch-ns do relogio de parede do registro de log.
     */
    public String linhaResumo(String codigo) {
        List<Map.Entry<Chave, Contagem>> l = new ArrayList<>(mapa(codigo).entrySet());
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
        return prefixo(codigo) + " total=" + total + " inteiras=" + inteiras + " contidas=" + (total - inteiras)
                + " chaves=" + l.size() + partes;
    }

    private ConcurrentHashMap<Chave, Contagem> mapa(String codigo) {
        ConcurrentHashMap<Chave, Contagem> m = contagens.get(codigo);
        if (m == null) {
            throw new IllegalArgumentException("codigo fora da lista contida: " + codigo);
        }
        return m;
    }

    private static String prefixo(String codigo) {
        return "Hot Rod " + codigo + ":";
    }

    // ------------------------------------------------------------------ chaves

    /** Chave do {@code ISPN004005} a partir da mensagem (ver a classe). */
    static Chave chave4005(String mensagem) {
        String txt = mensagem.startsWith(PREFIXO_4005) ? mensagem.substring(PREFIXO_4005.length())
                : mensagem.substring(Math.min(mensagem.length(), ISPN004005.length()));
        String no = NO_DESCONHECIDO;
        Matcher e = ENDERECO.matcher(txt);
        if (e.find()) {
            String host = e.group(2).equals("<unresolved>") ? e.group(1) : e.group(2);
            if (!host.isEmpty()) {
                no = host + ":" + e.group(3);
            }
        }
        Matcher c = CODIGO_ISPN.matcher(txt);
        if (c.find()) {
            return new Chave(no, c.group());
        }
        Matcher x = EXCECAO.matcher(txt);
        if (x.find()) {
            String q = x.group(1);
            return new Chave(no, q.substring(Math.max(q.lastIndexOf('.'), q.lastIndexOf('$')) + 1));
        }
        return new Chave(no, SEM_CODIGO);
    }

    /** No da falha do {@code ISPN004007} (ver a classe). */
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

    /** Motivo da falha do {@code ISPN004007} (ver a classe). */
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
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
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
        return "ContencaoLogHotRod[" + CODIGOS + ", " + Objects.toString(anterior, "sem filtro anterior") + "]";
    }
}
