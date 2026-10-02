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

    private void verificarI5(List<Supplier<Map<String, SessionState>>> leitores) {
        if (leitores == null || leitores.size() < 2) return;
        List<Map<String, SessionState>> snaps = new ArrayList<>(leitores.size());
        for (Supplier<Map<String, SessionState>> s : leitores) snaps.add(s.get());
        Set<String> chaves = new HashSet<>();
        for (Map<String, SessionState> m : snaps) chaves.addAll(m.keySet());
        for (String k : chaves) {
            SessionState.Estado ref = null;
            boolean primeira = true;
            for (Map<String, SessionState> m : snaps) {
                SessionState s = m.get(k);
                SessionState.Estado e = s == null ? null : s.estado();
                if (primeira) { ref = e; primeira = false; continue; }
                if (!java.util.Objects.equals(ref, e)) {
                    registrarViolacao("I5", k,
                        "divergencia de estado entre replicas: " + ref + " vs " + e);
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
     */
    private void verificarI6(List<Supplier<Map<String, IdentityState>>> leitores) {
        if (leitores == null || leitores.size() < 2) return;
        List<Map<String, IdentityState>> snaps = new ArrayList<>(leitores.size());
        for (Supplier<Map<String, IdentityState>> s : leitores) snaps.add(s.get());
        Set<String> chaves = new HashSet<>();
        for (Map<String, IdentityState> m : snaps) chaves.addAll(m.keySet());
        for (String k : chaves) {
            Long refContador = null;
            IdentityState.Status refStatus = null;
            boolean primeira = true;
            for (Map<String, IdentityState> m : snaps) {
                IdentityState st = m.get(k);
                Long contador = st == null ? null : st.contadorFalhas();
                IdentityState.Status status = st == null ? null : st.status();
                if (primeira) { refContador = contador; refStatus = status; primeira = false; continue; }
                boolean divergeContador = !java.util.Objects.equals(refContador, contador);
                boolean divergeStatus = !java.util.Objects.equals(refStatus, status);
                if (divergeContador || divergeStatus) {
                    registrarViolacao("I6", k,
                        "divergencia entre replicas: contador " + refContador + " vs " + contador
                            + "; status " + refStatus + " vs " + status);
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
