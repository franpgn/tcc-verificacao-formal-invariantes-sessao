package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Complemento 1 do benchmark refeito (DT-027, secao 5, item 3): checagem de fusao antes da varredura final.
 *
 * <p>O laco de convergencia nao espera a fusao (relatorio da A1, achado 2). Com {@code --sweep-merge-timeout-sec T},
 * antes de varrer o programa le a saude REST dos tres nos (os de {@code --controller-admin}) a cada 2 s (o periodo da
 * sonda de topologia) ate os tres mostrarem a mesma vista de 3 membros ({@code number_of_nodes} e
 * {@code node_names}), com prazo no marcador {@code end} mais T (sem {@code end}, uma leitura so). Sem fusao no prazo,
 * a varredura nao roda e {@code varredura-rep-NNN.erro.txt} traz o motivo (o tratamento simetrico do W-5d cobre esse
 * caso). Sem a opcao, nada muda.</p>
 */
class EsperaFusaoTest {

    private static final String U1 = "http://127.0.0.1:11222";
    private static final String U2 = "http://127.0.0.1:11223";
    private static final String U3 = "http://127.0.0.1:11224";
    private static final List<String> URLS = List.of(U1, U2, U3);
    private static final List<String> TRES = List.of("isn1-56338", "isn2-50911", "isn3-36001");
    private static final long S = 1_000_000_000L;
    private static final long END = 1_793_966_568_333_787_308L;

    /** Relogio falso: o tempo so anda quando o codigo dorme. */
    static final class RelogioFalso implements ConvergenciaLoop.Relogio {
        long agora;
        final List<Long> sonos = new ArrayList<>();

        RelogioFalso(long agora) {
            this.agora = agora;
        }

        @Override
        public long agoraNs() {
            return agora;
        }

        @Override
        public void dormirNs(long ns) {
            sonos.add(ns);
            agora += ns;
        }
    }

    private static EsperaFusao.Vista v(String url, int n, List<String> membros) {
        return new EsperaFusao.Vista(url, true, n, membros, null);
    }

    private static List<EsperaFusao.Vista> fundida() {
        return List.of(v(U1, 3, TRES), v(U2, 3, List.of(TRES.get(2), TRES.get(0), TRES.get(1))), v(U3, 3, TRES));
    }

    private static List<EsperaFusao.Vista> particionada() {
        List<String> maioria = List.of("isn1-56338", "isn2-50911");
        return List.of(v(U1, 2, maioria), v(U2, 2, maioria), v(U3, 1, List.of("isn3-36001")));
    }

    /** Fonte que devolve as leituras na ordem e repete a ultima. */
    private static EsperaFusao.FonteVistas fonte(AtomicInteger chamadas, List<List<EsperaFusao.Vista>> leituras) {
        return urls -> {
            int k = chamadas.getAndIncrement();
            return leituras.get(Math.min(k, leituras.size() - 1));
        };
    }

    // ------------------------------------------------------------------ interpretacao e criterio

    @Test
    @DisplayName("interpretar: JSON real do 15.0.21 (cluster_health.number_of_nodes e node_names)")
    void interpretarReal() {
        String corpo = "{\"cluster_health\":{\"cluster_name\":\"cluster-tcc\",\"health_status\":\"HEALTHY\","
                + "\"number_of_nodes\":3,\"node_names\":[\"isn2-50911\",\"isn1-56338\",\"isn3-36001\"]},"
                + "\"cache_health\":[{\"status\":\"HEALTHY\",\"cache_name\":\"___protobuf_metadata\"}]}";
        EsperaFusao.Vista x = EsperaFusao.interpretar(U1, corpo);
        assertTrue(x.respondeu());
        assertEquals(3, x.numeroDeNos());
        assertEquals(TRES, x.membros(), "membros ordenados");
        assertNull(x.erro());
    }

    @Test
    @DisplayName("interpretar: corpo que nao e a saude vira leitura sem resposta, com o erro")
    void interpretarInvalido() {
        EsperaFusao.Vista x = EsperaFusao.interpretar(U2, "<html>401</html>");
        assertFalse(x.respondeu());
        assertTrue(x.erro() != null && !x.erro().isBlank());
        EsperaFusao.Vista y = EsperaFusao.interpretar(U2, "{\"cluster_health\":{\"number_of_nodes\":3}}");
        assertTrue(y.respondeu());
        assertEquals(List.of(), y.membros(), "sem node_names: nenhum membro");
    }

    @Test
    @DisplayName("criterio: os tres respondem, 3 nos e os mesmos 3 membros em todos")
    void criterio() {
        assertTrue(EsperaFusao.mesmaVistaCompleta(fundida(), 3));
        assertFalse(EsperaFusao.mesmaVistaCompleta(particionada(), 3), "particao: 2 + 1");
        List<EsperaFusao.Vista> semResposta = new ArrayList<>(fundida());
        semResposta.set(2, new EsperaFusao.Vista(U3, false, 0, List.of(), "GET excedeu o prazo"));
        assertFalse(EsperaFusao.mesmaVistaCompleta(semResposta, 3), "um no sem resposta");
        List<EsperaFusao.Vista> outra = new ArrayList<>(fundida());
        outra.set(1, v(U2, 3, List.of("isn1-56338", "isn2-50911", "isn3-99999")));
        assertFalse(EsperaFusao.mesmaVistaCompleta(outra, 3), "3 membros, mas outra vista (isn3 de antes)");
        List<EsperaFusao.Vista> semNomes = List.of(v(U1, 3, List.of()), v(U2, 3, List.of()), v(U3, 3, List.of()));
        assertFalse(EsperaFusao.mesmaVistaCompleta(semNomes, 3), "sem node_names nao prova a mesma vista");
        assertFalse(EsperaFusao.mesmaVistaCompleta(fundida().subList(0, 2), 3), "faltou um no");
    }

    // ------------------------------------------------------------------ espera

    @Test
    @DisplayName("ja fundido na primeira leitura: uma leitura, sem dormir")
    void jaFundido() throws Exception {
        RelogioFalso r = new RelogioFalso(END + 160 * S);
        AtomicInteger n = new AtomicInteger();
        EsperaFusao.Resultado res = EsperaFusao.esperar(URLS, fonte(n, List.of(fundida())), r, END, 72 * S,
                EsperaFusao.PASSO_NS);
        assertTrue(res.fundiu());
        assertEquals(1, res.leituras());
        assertEquals(1, n.get());
        assertEquals(List.of(), r.sonos);
        assertEquals(END + 160 * S, res.tVistaNs());
        assertEquals(END + 72 * S, res.prazoNs());
    }

    @Test
    @DisplayName("funde na terceira leitura antes do prazo: espera com o passo de 2 s e grava o instante")
    void fundeDepois() throws Exception {
        RelogioFalso r = new RelogioFalso(END + 40 * S);
        AtomicInteger n = new AtomicInteger();
        EsperaFusao.Resultado res = EsperaFusao.esperar(URLS,
                fonte(n, List.of(particionada(), particionada(), fundida())), r, END, 72 * S, EsperaFusao.PASSO_NS);
        assertTrue(res.fundiu());
        assertEquals(3, res.leituras());
        assertEquals(List.of(2 * S, 2 * S), r.sonos);
        assertEquals(END + 44 * S, res.tVistaNs());
        assertTrue(res.linha().contains("fundiu=sim"), res.linha());
        assertTrue(res.linha().contains("desde_end_s=44.000"), res.linha());
    }

    @Test
    @DisplayName("sem fusao ate end + teto: le ate o prazo (ultimo sono cortado no prazo) e nao funde")
    void semFusaoNoPrazo() throws Exception {
        RelogioFalso r = new RelogioFalso(END + 67 * S);
        AtomicInteger n = new AtomicInteger();
        EsperaFusao.Resultado res = EsperaFusao.esperar(URLS, fonte(n, List.of(particionada())), r, END, 72 * S,
                EsperaFusao.PASSO_NS);
        assertFalse(res.fundiu());
        assertNull(res.tVistaNs());
        assertEquals(List.of(2 * S, 2 * S, 1 * S), r.sonos);
        assertEquals(4, res.leituras(), "67, 69, 71 e 72 s depois do end");
        assertEquals(END + 72 * S, r.agora);
        String m = res.motivo();
        assertTrue(m.contains("sem fusao"), m);
        assertTrue(m.contains("72"), m);
        assertTrue(m.contains("127.0.0.1:11224") && m.contains("number_of_nodes=1"), m);
    }

    @Test
    @DisplayName("prazo ja vencido quando a varredura comeca (caso da VM: end + ~160 s): uma leitura so")
    void prazoVencido() throws Exception {
        RelogioFalso r = new RelogioFalso(END + 160 * S);
        AtomicInteger n = new AtomicInteger();
        EsperaFusao.Resultado res = EsperaFusao.esperar(URLS, fonte(n, List.of(particionada())), r, END, 72 * S,
                EsperaFusao.PASSO_NS);
        assertFalse(res.fundiu());
        assertEquals(1, res.leituras());
        assertEquals(List.of(), r.sonos);
    }

    @Test
    @DisplayName("sem marcador end (celula sem falha): uma leitura so, com o mesmo criterio")
    void semEnd() throws Exception {
        RelogioFalso r = new RelogioFalso(END);
        AtomicInteger n = new AtomicInteger();
        EsperaFusao.Resultado ok = EsperaFusao.esperar(URLS, fonte(n, List.of(fundida())), r, null, 72 * S,
                EsperaFusao.PASSO_NS);
        assertTrue(ok.fundiu());
        assertNull(ok.tFimFalhaNs());
        assertTrue(ok.linha().contains("desde_end_s=-"), ok.linha());
        EsperaFusao.Resultado nao = EsperaFusao.esperar(URLS, fonte(new AtomicInteger(), List.of(particionada())), r,
                null, 72 * S, EsperaFusao.PASSO_NS);
        assertFalse(nao.fundiu());
        assertEquals(1, nao.leituras());
        assertEquals(List.of(), r.sonos);
    }

    // ------------------------------------------------------------------ antes da varredura

    private static Cli cli(Path csv, String... extra) {
        List<String> a = new ArrayList<>(List.of("--scenario", "S3", "--csv-dir", csv.toString(), "--sweep",
                "--servers", "127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224",
                "--controller-admin", U1 + "," + U2 + "," + U3));
        a.addAll(List.of(extra));
        Cli c = new Cli();
        new CommandLine(c).parseArgs(a.toArray(new String[0]));
        return c;
    }

    @Test
    @DisplayName("sem --sweep-merge-timeout-sec: nao le nada, nao grava nada e a varredura roda (como antes)")
    void semOpcao(@TempDir Path d) throws IOException {
        AtomicInteger n = new AtomicInteger();
        assertTrue(EsperaFusao.antesDaVarredura(cli(d), 1, END, fonte(n, List.of(particionada())),
                new RelogioFalso(END + 160 * S)));
        assertEquals(0, n.get());
        try (var s = Files.list(d)) {
            assertEquals(0, s.count());
        }
    }

    @Test
    @DisplayName("fundido: a varredura roda e fusao-rep-NNN.json registra a leitura")
    void antesDaVarreduraFundido(@TempDir Path d) throws IOException {
        assertTrue(EsperaFusao.antesDaVarredura(cli(d, "--sweep-merge-timeout-sec", "72"), 1, END,
                fonte(new AtomicInteger(), List.of(fundida())), new RelogioFalso(END + 160 * S)));
        assertFalse(Files.exists(d.resolve("varredura-rep-001.erro.txt")));
        String j = Files.readString(d.resolve("fusao-rep-001.json"), StandardCharsets.UTF_8);
        assertTrue(j.contains("\"fundiu\" : true"), j);
        assertTrue(j.contains("\"teto_s\" : 72"), j);
        assertTrue(j.contains("\"desde_end_s\" : 160.0"), j);
        assertTrue(j.contains("isn3-36001"), j);
    }

    @Test
    @DisplayName("sem fusao: a varredura nao roda e varredura-rep-NNN.erro.txt traz o motivo")
    void antesDaVarreduraSemFusao(@TempDir Path d) throws IOException {
        assertFalse(EsperaFusao.antesDaVarredura(cli(d, "--sweep-merge-timeout-sec", "72"), 2, END,
                fonte(new AtomicInteger(), List.of(particionada())), new RelogioFalso(END + 160 * S)));
        String erro = Files.readString(d.resolve("varredura-rep-002.erro.txt"), StandardCharsets.UTF_8);
        assertTrue(erro.startsWith("sem fusao antes da varredura"), erro);
        assertTrue(erro.contains("end + 72 s"), erro);
        String j = Files.readString(d.resolve("fusao-rep-002.json"), StandardCharsets.UTF_8);
        assertTrue(j.contains("\"fundiu\" : false"), j);
        assertTrue(j.contains("\"t_vista_ns\" : null"), j);
    }

    // ------------------------------------------------------------------ opcao e ligacao

    @Test
    @DisplayName("opcao: ausente por padrao; > 0; exige --sweep e --controller-admin com um no por servidor")
    void opcao(@TempDir Path d) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs();
        assertNull(c.varreduraFusaoTetoSeg);
        cli(d, "--sweep-merge-timeout-sec", "72").validar();
        assertEquals(72L, cli(d, "--sweep-merge-timeout-sec", "72").varreduraFusaoTetoSeg);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> cli(d, "--sweep-merge-timeout-sec", "0").validar());
        Cli semSweep = new Cli();
        new CommandLine(semSweep).parseArgs("--scenario", "S3", "--sweep-merge-timeout-sec", "72",
                "--controller-admin", U1 + "," + U2 + "," + U3);
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                semSweep::validar);
        assertTrue(e.getMessage().contains("--sweep"), e.getMessage());
        Cli semAdmin = new Cli();
        new CommandLine(semAdmin).parseArgs("--scenario", "S3", "--sweep", "--sweep-merge-timeout-sec", "72");
        e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, semAdmin::validar);
        assertTrue(e.getMessage().contains("--controller-admin"), e.getMessage());
        Cli dois = new Cli();
        new CommandLine(dois).parseArgs("--scenario", "S3", "--sweep", "--sweep-merge-timeout-sec", "72",
                "--controller-admin", U1 + "," + U2);
        e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, dois::validar);
        assertTrue(e.getMessage().contains("--controller-admin"), e.getMessage());
    }

    @Test
    @DisplayName("WorkloadMain: a checagem de fusao vem antes da varredura, dentro do if (cli.varredura)")
    void ligacaoNoWorkloadMain() throws IOException {
        String fonte = ArquiteturaAtuadorTest.fonte(ArquiteturaSondaSaudeTest.fonteDe("WorkloadMain"));
        int i = fonte.indexOf("if (cli.varredura) {");
        assertTrue(i >= 0, "bloco da varredura nao encontrado");
        String bloco = fonte.substring(i, fonte.indexOf("\n            }\n", i));
        int espera = bloco.indexOf("EsperaFusao.antesDaVarredura(");
        int varre = bloco.indexOf("executarVarredura(");
        assertTrue(espera >= 0 && varre > espera, bloco);
    }
}
