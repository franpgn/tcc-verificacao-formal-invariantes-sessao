package br.unipampa.tcc.session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Auditor de invariantes do protocolo de sessao.
 *
 *   I1 irreversibilidade da invalidacao apos logout.
 *   I2 consistencia de bloqueio (sessoes ativas de identidade bloqueada).
 *   I3 monotonicidade local do contador de tentativas.
 *   I4 unicidade do motivo de invalidacao.
 *   I5 convergencia do estado logico sob quiescencia.
 *   I6 convergencia do contador sob quiescencia.
 *
 * I1-I4 sao detectados pelas chamadas a {@link #registrarViolacao(String, String, String)}
 * feitas a partir de {@link SessionOps}.
 *
 * I5 e I6 exigem leitura comparativa das replicas. Sao avaliados pela
 * auditoria periodica, iniciada com {@link #iniciarAuditoriaPeriodica}.
 * As leituras de cada replica sao fornecidas por {@link Supplier}s
 * injetados pelo chamador (em producao, conectando-se a cada no
 * via Hot Rod com endereco fixo; em teste, com Maps em memoria).
 */
public class InvariantAuditor implements AutoCloseable {

    /**
     * Uma violacao registrada. W-10: {@code origem} diz de onde veio a leitura que a revelou; {@code null} nos
     * caminhos existentes (carga, cascata, convergencia) e {@link #ORIGEM_SONDA} na sonda de replay.
     */
    public record Violacao(Instant em, String invariante, String chave, String mensagem, String origem) {
        /** Os caminhos existentes: sem origem. */
        public Violacao(Instant em, String invariante, String chave, String mensagem) {
            this(em, invariante, chave, mensagem, null);
        }
    }

    /** W-10: origem das violacoes reveladas pela sonda de replay ({@link SondaReplay}). */
    public static final String ORIGEM_SONDA = "sonda";

    /**
     * B-33: resultado de {@link #avaliarRodada}. {@code completa} = todos os
     * leitores responderam (rodada valida quanto as leituras); {@code leiturasFalhas}
     * = leitores que lancaram excecao (p.ex. no em DEGRADED).
     */
    public record RodadaAuditoria(boolean completa, int divergentesI5, int divergentesI6,
                                  int leiturasFalhas) {}

    private final ConcurrentLinkedQueue<Violacao> registros = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger leiturasFalhas =
            new java.util.concurrent.atomic.AtomicInteger();
    private ScheduledExecutorService scheduler;

    /**
     * W-3 (B-28.2): ouvinte de cada violacao registrada (o laco de controle). Recebe o mesmo rotulo de
     * invariante gravado no CSV de violacoes; a classificacao (seguranca ou fora do gatilho) e do laco.
     * Deve ser rapido e nunca lancar; uma excecao dele e engolida.
     */
    public interface OuvinteDeViolacao {
        void violacao(String invariante, String chave);
    }

    /** W-3: {@code null} (padrao) = desligado. */
    private volatile OuvinteDeViolacao ouvinte;

    /** W-3: liga (ou, com {@code null}, desliga) o ouvinte de violacoes. */
    void definirOuvinte(OuvinteDeViolacao o) {
        this.ouvinte = o;
    }

    public void registrarViolacao(String invariante, String chave, String mensagem) {
        registrarViolacao(invariante, chave, mensagem, null);
    }

    /**
     * W-10: como {@link #registrarViolacao(String, String, String)}, com a {@code origem} da leitura. Com origem
     * ({@link #ORIGEM_SONDA}) a violacao entra no registro e no CSV, mas o ouvinte (o laco de controle) NAO e
     * avisado: a sonda e instrumentacao e nao pode mudar a decisao do controlador.
     */
    public void registrarViolacao(String invariante, String chave, String mensagem, String origem) {
        registros.add(new Violacao(Instant.now(), invariante, chave, mensagem, origem));
        if (origem != null) {
            return;
        }
        OuvinteDeViolacao o = ouvinte;
        if (o != null) {
            try {
                o.violacao(invariante, chave);
            } catch (RuntimeException e) {
                // o controlador nunca derruba o auditor
            }
        }
    }

    public int total() { return registros.size(); }

    /** W-10: violacoes com a origem dada ({@code null} = os caminhos existentes). */
    public long total(String origem) {
        return registros.stream().filter(v -> java.util.Objects.equals(origem, v.origem())).count();
    }

    /** W-10: violacoes da invariante dada com a origem dada. */
    public long total(String invariante, String origem) {
        return registros.stream().filter(v -> invariante.equals(v.invariante())
                && java.util.Objects.equals(origem, v.origem())).count();
    }

    /** B-33: total acumulado de leituras de replica que lancaram excecao (toleradas). */
    public int leiturasFalhas() { return leiturasFalhas.get(); }

    public List<Violacao> drenar() {
        List<Violacao> out = new ArrayList<>(registros);
        registros.clear();
        return out;
    }

    /**
     * Inicia uma tarefa periodica que, a cada {@code periodoMs} milissegundos,
     * compara as leituras devolvidas por cada {@link Supplier} para detectar
     * I5 e I6 sob quiescencia.
     *
     * Caller deve garantir que a operacao de leitura ocorre apos um intervalo
     * sem escritas (janela de quiescencia); o auditor nao impoe a quiescencia,
     * apenas detecta divergencia.
     */
    public synchronized void iniciarAuditoriaPeriodica(
            long periodoMs,
            List<Supplier<Map<String, SessionState>>> leitoresSessao,
            List<Supplier<Map<String, IdentityState>>> leitoresIdentidade) {
        if (scheduler != null) {
            throw new IllegalStateException("auditoria periodica ja iniciada");
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "invariant-auditor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
            () -> rodadaUnica(leitoresSessao, leitoresIdentidade),
            periodoMs, periodoMs, TimeUnit.MILLISECONDS);
    }

    /** Executa uma rodada unica de checagem; util em testes deterministicos. */
    public void rodadaUnica(
            List<Supplier<Map<String, SessionState>>> leitoresSessao,
            List<Supplier<Map<String, IdentityState>>> leitoresIdentidade) {
        verificarI5(leitoresSessao);
        verificarI6(leitoresIdentidade);
    }

    /**
     * B-33: avalia uma rodada de I5/I6 <b>sem registrar violacao</b>, devolvendo
     * a contagem de chaves divergentes e se a rodada foi completa. Um leitor que
     * lanca excecao (no em DEGRADED recusa leitura) e <b>excluido</b> da
     * comparacao e torna a rodada incompleta: "excecao" nunca e lida como
     * "conjunto vazio = convergiu". Usada pelo laco de convergencia.
     */
    public RodadaAuditoria avaliarRodada(
            List<Supplier<Map<String, SessionState>>> leitoresSessao,
            List<Supplier<Map<String, IdentityState>>> leitoresIdentidade) {
        Leitura<SessionState> ls = ler(leitoresSessao);
        Leitura<IdentityState> li = ler(leitoresIdentidade);
        int falhas = ls.falhas() + li.falhas();
        int d5 = ls.lidos().size() < 2 ? 0 : divergenciasI5(ls.lidos()).size();
        int d6 = li.lidos().size() < 2 ? 0 : divergenciasI6(li.lidos()).size();
        return new RodadaAuditoria(falhas == 0, d5, d6, falhas);
    }

    private record Leitura<T>(List<Map<String, T>> lidos, int falhas) {}

    /**
     * Le todos os leitores; excecao de um leitor (DEGRADED) o exclui e e contada
     * em {@link #leiturasFalhas()}, em vez de derrubar a rodada (e, na auditoria
     * periodica, a tarefa agendada, que o ScheduledExecutor cancela em silencio
     * se a tarefa lancar).
     */
    private <T> Leitura<T> ler(List<Supplier<Map<String, T>>> leitores) {
        List<Map<String, T>> lidos = new ArrayList<>();
        int falhas = 0;
        if (leitores != null) {
            for (Supplier<Map<String, T>> s : leitores) {
                try {
                    lidos.add(s.get());
                } catch (RuntimeException e) {
                    falhas++;
                    leiturasFalhas.incrementAndGet();
                }
            }
        }
        return new Leitura<>(lidos, falhas);
    }

    /**
     * I5, per spec/SessionStore.tla: convergência de ESTADO, MOTIVO e IDENTIDADE
     * sob quiescência. (Correção de B-24, ver docs/registro-de-problemas.md P-21:
     * desde que os leitores passaram a ser RESTRITOS POR SEGMENTO -- cada réplica
     * só devolve as chaves cujo segmento ela possui, `owners=2` de 3 -- uma chave
     * ausente num leitor (`null`) deixou de significar "divergência"; passou a
     * significar, na maioria das vezes, "esta réplica não é owner desse
     * segmento", o que é normal e esperado, não um defeito. A comparação agora só
     * considera, para cada chave, os leitores que de fato a possuem (valor
     * não-nulo) -- um leitor que não possui a chave é simplesmente ignorado para
     * aquela chave, não comparado como se fosse um valor "ausente" divergente.)
     */
    private void verificarI5(List<Supplier<Map<String, SessionState>>> leitores) {
        if (leitores == null || leitores.size() < 2) return;
        List<Map<String, SessionState>> snaps = ler(leitores).lidos();
        divergenciasI5(snaps).forEach((k, msg) -> registrarViolacao("I5", k, msg));
    }

    /** Chaves divergentes de I5 (chave para mensagem); nao registra violacao. */
    private static Map<String, String> divergenciasI5(List<Map<String, SessionState>> snaps) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        Set<String> chaves = new HashSet<>();
        for (Map<String, SessionState> m : snaps) chaves.addAll(m.keySet());
        for (String k : chaves) {
            SessionState ref = null;
            for (Map<String, SessionState> m : snaps) {
                SessionState s = m.get(k);
                if (s == null) continue; // esta replica nao possui o segmento desta chave -- nao e divergencia
                if (ref == null) { ref = s; continue; }
                if (s.estado() != ref.estado()
                        || !java.util.Objects.equals(s.motivo(), ref.motivo())
                        || !java.util.Objects.equals(s.identidade(), ref.identidade())) {
                    out.put(k,
                        "divergencia entre replicas: estado " + ref.estado() + " vs " + s.estado()
                            + "; motivo " + ref.motivo() + " vs " + s.motivo()
                            + "; identidade " + ref.identidade() + " vs " + s.identidade());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * I6, per spec/SessionStore.tla: convergência do CONTADOR e do STATUS de
     * identidade sob quiescência -- ambos os campos, não só o contador. (Correção
     * de B-24: a versão anterior só comparava {@code contadorFalhas()}, deixando
     * de detectar divergência de {@code status()} sozinha -- lacuna encontrada ao
     * conectar leitores reais multi-réplica; nenhum teste existente dependia do
     * comportamento antigo, ver InvariantAuditorTest.)
     *
     * <p>Mesma correção de leitores restritos por segmento que {@link #verificarI5}
     * (ver P-21): uma chave ausente (`null`) num leitor significa "esta réplica
     * não possui este segmento", não divergência -- só compara leitores que de
     * fato possuem a chave.</p>
     */
    private void verificarI6(List<Supplier<Map<String, IdentityState>>> leitores) {
        if (leitores == null || leitores.size() < 2) return;
        List<Map<String, IdentityState>> snaps = ler(leitores).lidos();
        divergenciasI6(snaps).forEach((k, msg) -> registrarViolacao("I6", k, msg));
    }

    /** Chaves divergentes de I6 (chave para mensagem); nao registra violacao. */
    private static Map<String, String> divergenciasI6(List<Map<String, IdentityState>> snaps) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        Set<String> chaves = new HashSet<>();
        for (Map<String, IdentityState> m : snaps) chaves.addAll(m.keySet());
        for (String k : chaves) {
            IdentityState ref = null;
            for (Map<String, IdentityState> m : snaps) {
                IdentityState st = m.get(k);
                if (st == null) continue; // esta replica nao possui o segmento desta chave -- nao e divergencia
                if (ref == null) { ref = st; continue; }
                boolean divergeContador = ref.contadorFalhas() != st.contadorFalhas();
                boolean divergeStatus = ref.status() != st.status();
                if (divergeContador || divergeStatus) {
                    out.put(k,
                        "divergencia entre replicas: contador " + ref.contadorFalhas() + " vs " + st.contadorFalhas()
                            + "; status " + ref.status() + " vs " + st.status());
                    break;
                }
            }
        }
        return out;
    }

    /** Escreve as violacoes correntes em CSV: instante,invariante,chave,mensagem. */
    public void dumpCsv(Path destino) throws IOException {
        dumpCsv(destino, null, List.of());
    }

    /** B-33: coluna adicional derivada de cada violacao (p.ex. classificacao do I2). */
    public record Coluna(String nome, java.util.function.Function<Violacao, String> valor) {}

    /**
     * W-10: coluna {@code origem} do CSV de violacoes ({@code sonda} ou vazia). So entra no CSV com a sonda
     * ligada; sem ela o formato e o de antes.
     */
    public static Coluna colunaOrigem() {
        return new Coluna("origem", v -> v.origem() == null ? "" : v.origem());
    }

    /** B-33: igual a {@link #dumpCsv(Path)} mais a coluna {@code fase} (rotulo pelos marcadores). */
    public void dumpCsv(Path destino, Marcadores marcadores) throws IOException {
        dumpCsv(destino, marcadores, List.of());
    }

    /**
     * B-33: grava as violacoes; com {@code marcadores} nao nulo acrescenta a coluna
     * {@code fase}; {@code extras} acrescentam colunas depois dela. Sem ambos o
     * formato e o historico {@code instante,invariante,chave,mensagem}.
     */
    public void dumpCsv(Path destino, Marcadores marcadores, List<Coluna> extras) throws IOException {
        Files.createDirectories(destino.getParent() == null ? Path.of(".") : destino.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            StringBuilder cab = new StringBuilder("instante,invariante,chave,mensagem");
            if (marcadores != null) cab.append(",fase");
            for (Coluna c : extras) cab.append(',').append(c.nome());
            w.write(cab.toString());
            w.newLine();
            for (Violacao v : registros) {
                w.write(v.em().toString());
                w.write(",");
                w.write(v.invariante());
                w.write(",");
                w.write(escapar(v.chave()));
                w.write(",");
                w.write(escapar(v.mensagem()));
                if (marcadores != null) {
                    w.write(",");
                    w.write(marcadores.rotuloDe(epochNs(v.em())));
                }
                for (Coluna c : extras) {
                    w.write(",");
                    w.write(escapar(c.valor().apply(v)));
                }
                w.newLine();
            }
        }
    }

    /** Instant para epoch-ns (mesma base de {@link EpochClock}). */
    static long epochNs(Instant t) {
        return t.getEpochSecond() * 1_000_000_000L + t.getNano();
    }

    private static String escapar(String s) {
        if (s == null) return "";
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
