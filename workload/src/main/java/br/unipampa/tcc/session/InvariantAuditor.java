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

    public record Violacao(Instant em, String invariante, String chave, String mensagem) {}

    private final ConcurrentLinkedQueue<Violacao> registros = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler;

    public void registrarViolacao(String invariante, String chave, String mensagem) {
        registros.add(new Violacao(Instant.now(), invariante, chave, mensagem));
    }

    public int total() { return registros.size(); }

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
        List<Map<String, SessionState>> snaps = new ArrayList<>(leitores.size());
        for (Supplier<Map<String, SessionState>> s : leitores) snaps.add(s.get());
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
                    registrarViolacao("I5", k,
                        "divergencia entre replicas: estado " + ref.estado() + " vs " + s.estado()
                            + "; motivo " + ref.motivo() + " vs " + s.motivo()
                            + "; identidade " + ref.identidade() + " vs " + s.identidade());
                    break;
                }
            }
        }
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
        List<Map<String, IdentityState>> snaps = new ArrayList<>(leitores.size());
        for (Supplier<Map<String, IdentityState>> s : leitores) snaps.add(s.get());
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
                    registrarViolacao("I6", k,
                        "divergencia entre replicas: contador " + ref.contadorFalhas() + " vs " + st.contadorFalhas()
                            + "; status " + ref.status() + " vs " + st.status());
                    break;
                }
            }
        }
    }

    /** Escreve as violacoes correntes em CSV: instante,invariante,chave,mensagem. */
    public void dumpCsv(Path destino) throws IOException {
        Files.createDirectories(destino.getParent() == null ? Path.of(".") : destino.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write("instante,invariante,chave,mensagem");
            w.newLine();
            for (Violacao v : registros) {
                w.write(v.em().toString());
                w.write(",");
                w.write(v.invariante());
                w.write(",");
                w.write(escapar(v.chave()));
                w.write(",");
                w.write(escapar(v.mensagem()));
                w.newLine();
            }
        }
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
