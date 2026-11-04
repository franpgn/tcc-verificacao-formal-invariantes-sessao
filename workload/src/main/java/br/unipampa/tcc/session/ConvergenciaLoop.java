package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * B-33 (E1, item 3): laco de convergencia (O-5 do plano de B-29).
 *
 * <p>Depois de {@code t_fim} a carga de escrita para; a cada {@code periodo}
 * (5 s) uma rodada de I5/I6 e avaliada. Uma rodada so <b>vale</b> se a sonda de
 * cluster lista os servidores esperados na topologia, os dois caches estao
 * HEALTHY e <b>todos</b> os leitores multi-replica responderam; excecao de
 * leitura (no em DEGRADED) torna a rodada invalida e nunca e lida como
 * "conjunto vazio = convergiu". {@code t_convergencia} e o instante da
 * <b>primeira</b> de {@code consecutivas} (3) rodadas validas consecutivas sem
 * divergencia, menos {@code t_fim}. Se isso nao ocorre em {@code teto} (600 s,
 * contado a partir do inicio do laco), o resultado e
 * {@code divergencia-persistente} e as divergencias da ultima rodada sao
 * registradas como violacoes I5/I6 (divergencia transitoria durante o laco
 * <b>nao</b> e violacao: e o dado {@code t_convergencia}).</p>
 */
public final class ConvergenciaLoop {

    /** Estado do cluster visto pela sonda (REST de saude + topologia do cliente). */
    public record EstadoCluster(int servidoresNaTopologia, boolean sessoesHealthy,
                                boolean contadoresHealthy) {}

    @FunctionalInterface
    public interface SondaCluster {
        EstadoCluster ler() throws Exception;
    }

    /** Relogio e espera injetaveis (testes nao dormem de verdade). */
    public interface Relogio {
        long agoraNs();

        void dormirNs(long ns) throws InterruptedException;

        static Relogio sistema() {
            return sistema(EpochClock.capturarAgora());
        }

        static Relogio sistema(EpochClock ancora) {
            return new Relogio() {
                @Override public long agoraNs() { return ancora.paraEpochNanos(System.nanoTime()); }
                @Override public void dormirNs(long ns) throws InterruptedException {
                    Thread.sleep(ns / 1_000_000L, (int) (ns % 1_000_000L));
                }
            };
        }
    }

    /** Uma rodada: {@code divergentes*} sao {@code null} quando a rodada e invalida. */
    public record Rodada(long instanteNs, boolean valida, Integer divergentesI5,
                         Integer divergentesI6, String motivoInvalida) {}

    public record Resultado(List<Rodada> rodadas, boolean convergiu, Long tConvergenciaAbsNs,
                            Long tConvergenciaRelFimNs, boolean divergenciaPersistente) {}

    private final InvariantAuditor auditor;
    private final List<Supplier<Map<String, SessionState>>> leitoresSessao;
    private final List<Supplier<Map<String, IdentityState>>> leitoresIdentidade;
    private final SondaCluster sonda;
    private final int servidoresEsperados;
    private final Relogio relogio;
    private final long periodoNs;
    private final long tetoNs;
    private final int consecutivas;

    public ConvergenciaLoop(InvariantAuditor auditor,
                            List<Supplier<Map<String, SessionState>>> leitoresSessao,
                            List<Supplier<Map<String, IdentityState>>> leitoresIdentidade,
                            SondaCluster sonda, int servidoresEsperados, Relogio relogio,
                            long periodoNs, long tetoNs, int consecutivas) {
        this.auditor = auditor;
        this.leitoresSessao = leitoresSessao;
        this.leitoresIdentidade = leitoresIdentidade;
        this.sonda = sonda;
        this.servidoresEsperados = servidoresEsperados;
        this.relogio = relogio;
        this.periodoNs = periodoNs;
        this.tetoNs = tetoNs;
        this.consecutivas = consecutivas;
    }

    /**
     * Executa o laco. Se {@code tFimNs} (epoch-ns) ainda esta no futuro, espera ate
     * ele antes da primeira rodada.
     */
    public Resultado executar(long tFimNs) {
        long agora = relogio.agoraNs();
        if (agora < tFimNs) {
            dormir(tFimNs - agora);
        }
        long inicio = relogio.agoraNs();
        List<Rodada> rodadas = new ArrayList<>();
        int limpasSeguidas = 0;
        long inicioSequencia = 0L;
        while (true) {
            Rodada r = rodada();
            rodadas.add(r);
            boolean limpa = r.valida() && r.divergentesI5() == 0 && r.divergentesI6() == 0;
            if (limpa) {
                if (limpasSeguidas == 0) {
                    inicioSequencia = r.instanteNs();
                }
                limpasSeguidas++;
                if (limpasSeguidas >= consecutivas) {
                    return new Resultado(rodadas, true, inicioSequencia,
                            inicioSequencia - tFimNs, false);
                }
            } else {
                limpasSeguidas = 0;
            }
            dormir(periodoNs);
            if (relogio.agoraNs() - inicio >= tetoNs) {
                break;
            }
        }
        // divergencia persistente: registra o estado da ultima leitura como violacao I5/I6
        auditor.rodadaUnica(leitoresSessao, leitoresIdentidade);
        return new Resultado(rodadas, false, null, null, true);
    }

    private Rodada rodada() {
        long t = relogio.agoraNs();
        EstadoCluster e;
        try {
            e = sonda.ler();
        } catch (Exception ex) {
            return new Rodada(t, false, null, null, "sonda de cluster falhou: " + ex.getMessage());
        }
        if (e.servidoresNaTopologia() < servidoresEsperados) {
            return new Rodada(t, false, null, null,
                    "topologia com " + e.servidoresNaTopologia() + " de " + servidoresEsperados + " servidores");
        }
        if (!e.sessoesHealthy() || !e.contadoresHealthy()) {
            return new Rodada(t, false, null, null, "cache nao HEALTHY");
        }
        InvariantAuditor.RodadaAuditoria a = auditor.avaliarRodada(leitoresSessao, leitoresIdentidade);
        if (!a.completa()) {
            return new Rodada(t, false, null, null,
                    "leitura de " + a.leiturasFalhas() + " replica(s) falhou (DEGRADED?)");
        }
        return new Rodada(t, true, a.divergentesI5(), a.divergentesI6(), "");
    }

    private void dormir(long ns) {
        try {
            relogio.dormirNs(ns);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** {@code convergencia.csv}: um registro por rodada; contagens vazias quando a rodada e invalida. */
    public static void escreverCsv(Path destino, Resultado r) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write("instante_ns,valida,chaves_divergentes_i5,chaves_divergentes_i6,motivo_invalida");
            w.newLine();
            for (Rodada x : r.rodadas()) {
                w.write(x.instanteNs() + "," + x.valida() + ","
                        + (x.divergentesI5() == null ? "" : x.divergentesI5()) + ","
                        + (x.divergentesI6() == null ? "" : x.divergentesI6()) + ","
                        + x.motivoInvalida().replace(',', ';'));
                w.newLine();
            }
        }
    }

    /** Resumo JSON: t_fim, convergiu, t_convergencia (absoluto e relativo a t_fim), contagens. */
    public static void escreverResumo(Path destino, Resultado r, long tFimNs) throws IOException {
        escreverResumo(destino, r, tFimNs, null);
    }

    /**
     * W-3 (B-28.2): como a anterior, com {@code t_fim_carga_ns} (instante em que as threads de carga
     * pararam, no relogio dos {@code end_ns}; {@code null} se desconhecido) acrescentado ao fim. Os campos
     * anteriores nao mudam.
     */
    public static void escreverResumo(Path destino, Resultado r, long tFimNs, Long tFimCargaNs) throws IOException {
        escreverResumo(destino, r, tFimNs, tFimCargaNs, null);
    }

    /**
     * V-W3 (R1): como a anterior, com {@code t_fim_controle_ns} (instante em que o controlador terminou de
     * encerrar, depois de desfazer as acoes; {@code null} sem controlador) acrescentado ao fim.
     * {@code t_fim_carga_ns} continua sendo o instante em que a carga parou, lido antes desse encerramento.
     */
    public static void escreverResumo(Path destino, Resultado r, long tFimNs, Long tFimCargaNs,
                                      Long tFimControleNs) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        long validas = r.rodadas().stream().filter(Rodada::valida).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t_fim_ns", tFimNs);
        m.put("convergiu", r.convergiu());
        m.put("divergencia_persistente", r.divergenciaPersistente());
        m.put("t_convergencia_ns", r.tConvergenciaAbsNs() == null ? null : r.tConvergenciaRelFimNs());
        m.put("t_convergencia_abs_ns", r.tConvergenciaAbsNs());
        m.put("rodadas", r.rodadas().size());
        m.put("rodadas_validas", validas);
        m.put("t_fim_carga_ns", tFimCargaNs);
        m.put("t_fim_controle_ns", tFimControleNs);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(destino.toFile(), m);
    }
}
