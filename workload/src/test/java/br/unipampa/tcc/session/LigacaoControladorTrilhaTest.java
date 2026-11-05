package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AtuadorFalso;
import br.unipampa.tcc.controle.ExecutorDeAcoes;
import br.unipampa.tcc.controle.PoliticaDeAcao;
import br.unipampa.tcc.controle.TrilhaJsonl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2b (P-5, item 1): a ligacao abre as duas trilhas JSONL com a fila assincrona e a capacidade calculada na
 * criacao (4 x linhas esperadas na execucao de {@code --duration}), e o gancho de desligamento da JVM espera a
 * gravadora do {@code decisoes} terminar as linhas de reversao, dentro do mesmo {@code T_enc}.
 */
class LigacaoControladorTrilhaTest {

    static final long MS = 1_000_000L;
    static final long S = 1_000_000_000L;

    @TempDir
    Path dir;

    /** Gancho que guarda o que recebe. */
    static final class GanchoGravado implements ExecutorDeAcoes.Gancho {
        final List<Thread> registradas = Collections.synchronizedList(new ArrayList<>());
        final List<Thread> removidas = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void registrar(Thread t) {
            registradas.add(t);
        }

        @Override
        public void remover(Thread t) {
            removidas.add(t);
        }
    }

    Cli cli(String modo, String... extras) throws IOException {
        Path p = dir.resolve("controlador-e2e.json");
        Files.writeString(p, LigacaoControladorTest.PARAMS_RAPIDOS, StandardCharsets.UTF_8);
        List<String> a = new ArrayList<>(List.of("--controller", modo, "--controller-config", p.toString(),
                "--controller-admin", ControladorCliTest.SERVIDORES, "--servers", ControladorCliTest.SERVIDORES,
                "--csv-dir", dir.toString(), "--replication", "async"));
        a.addAll(List.of(extras));
        Cli c = new Cli();
        new CommandLine(c).parseArgs(a.toArray(String[]::new));
        c.validar();
        return c;
    }

    @Test
    @DisplayName("linhas esperadas: observaveis = meta + fim + (janela + tick) por W + topologia por P_sonda + defasagem por W; decisoes = meta + fim + (decisao + politica + acao) por W")
    void linhasEsperadas() {
        long d = 600 * S;
        long w = 5 * S;
        long p = 2 * S;
        assertEquals(2 + 2 * 120 + 300 + 120, LigacaoControlador.linhasEsperadasObservaveis(d, w, p, true, true));
        assertEquals(2 + 2 * 120 + 300, LigacaoControlador.linhasEsperadasObservaveis(d, w, p, true, false));
        assertEquals(2 + 2 * 120, LigacaoControlador.linhasEsperadasObservaveis(d, w, p, false, false));
        assertEquals(2 + 3 * 120, LigacaoControlador.linhasEsperadasDecisoes(d, w));
        // arredonda para cima: 301 s com W = 5 s sao 61 janelas e 151 rodadas da sonda
        assertEquals(2 + 2 * 61 + 151, LigacaoControlador.linhasEsperadasObservaveis(301 * S, w, p, true, false));
        assertEquals(2 + 3 * 61, LigacaoControlador.linhasEsperadasDecisoes(301 * S, w));
    }

    @Test
    @DisplayName("abrir: as duas trilhas com capacidade 4 x linhas esperadas de --duration (W do arquivo, sem sondas nas pecas de teste)")
    void capacidadeNaAbertura() throws Exception {
        Cli c = cli("shadow", "--duration", "60");
        EpochClock relogio = EpochClock.capturarAgora();
        long agora = relogio.paraEpochNanos(System.nanoTime());
        try (LigacaoControlador lig = LigacaoControlador.abrir(LigacaoControlador.carregar(c), c, 1, relogio, agora,
                null, LigacaoControladorTest.pecasDeTeste(AtuadorFalso.proibido(), 10 * MS))) {
            long w = 100 * MS; // PARAMS_RAPIDOS
            assertEquals(TrilhaJsonl.capacidadePara(LigacaoControlador.linhasEsperadasObservaveis(60 * S, w, 2000 * MS,
                    false, false)), lig.trilhaObservaveis().capacidade());
            assertEquals(TrilhaJsonl.capacidadePara(LigacaoControlador.linhasEsperadasDecisoes(60 * S, w)),
                    lig.trilhaDecisoes().capacidade());
            assertEquals(4 * (2 + 2 * 600), lig.trilhaObservaveis().capacidade());
            assertEquals(4 * (2 + 3 * 600), lig.trilhaDecisoes().capacidade());
        }
    }

    @Test
    @DisplayName("gancho da JVM: o registrado e um envoltorio com o mesmo nome; roda o gancho do executor e depois espera a gravadora no que sobra do T_enc")
    void ganchoEsperaAGravadora() throws Exception {
        GanchoGravado base = new GanchoGravado();
        List<String> ordem = Collections.synchronizedList(new ArrayList<>());
        AtomicLong prazoDado = new AtomicLong(-1);
        ExecutorDeAcoes.Gancho g = LigacaoControlador.ganchoComDreno(base, () -> 3 * S, prazo -> {
            ordem.add("dreno");
            prazoDado.set(prazo);
            return true;
        });
        Thread interno = new Thread(() -> ordem.add("executor"), ExecutorDeAcoes.NOME_GANCHO);
        g.registrar(interno);
        assertEquals(1, base.registradas.size());
        Thread envolto = base.registradas.get(0);
        assertNotSame(interno, envolto);
        assertEquals(ExecutorDeAcoes.NOME_GANCHO, envolto.getName());
        envolto.run(); // como a JVM no desligamento
        assertEquals(List.of("executor", "dreno"), ordem);
        assertTrue(prazoDado.get() > 0 && prazoDado.get() <= 3 * S, "prazo do dreno: " + prazoDado.get());
        g.remover(interno);
        assertEquals(List.of(envolto), base.removidas);
    }

    @Test
    @DisplayName("gancho da JVM: se o executor gastou o T_enc inteiro, nao espera mais a gravadora (o gancho nunca passa do T_enc)")
    void ganchoSemSobraNaoEspera() {
        GanchoGravado base = new GanchoGravado();
        List<String> ordem = Collections.synchronizedList(new ArrayList<>());
        ExecutorDeAcoes.Gancho g = LigacaoControlador.ganchoComDreno(base, () -> 50 * MS, prazo -> {
            ordem.add("dreno");
            return true;
        });
        g.registrar(new Thread(() -> {
            ordem.add("executor");
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, ExecutorDeAcoes.NOME_GANCHO));
        base.registradas.get(0).run();
        assertEquals(List.of("executor"), ordem);
    }

    @Test
    @DisplayName("active: a ligacao registra o gancho envolto (nome do executor) e o remove no encerramento normal")
    void ligacaoUsaOGanchoEnvolto() throws Exception {
        Cli c = cli("active", "--controller-reference",
                Files.writeString(dir.resolve("ref.json"), ControladorCliTest.referencia("S1", "async")).toString());
        GanchoGravado base = new GanchoGravado();
        LigacaoControlador.Pecas p = new LigacaoControlador.Pecas(null, AtuadorFalso.proibido(), PoliticaDeAcao.NENHUMA,
                base, 10 * MS, 2000 * MS, 1500 * MS);
        EpochClock relogio = EpochClock.capturarAgora();
        long agora = relogio.paraEpochNanos(System.nanoTime());
        LigacaoControlador lig = LigacaoControlador.abrir(LigacaoControlador.carregar(c), c, 1, relogio, agora, null, p);
        assertEquals(1, base.registradas.size());
        assertEquals(ExecutorDeAcoes.NOME_GANCHO, base.registradas.get(0).getName());
        lig.close();
        assertEquals(base.registradas, base.removidas);
    }
}
