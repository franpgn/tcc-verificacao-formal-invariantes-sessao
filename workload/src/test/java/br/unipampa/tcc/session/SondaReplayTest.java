package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-10: sonda de reuso de sessao depois do logout (replay), em unidade, sobre o duble de grid que
 * particiona ({@link MundoS3Particao}): replay imediato no no k+1 mod 3 com atraso U(1, 3) s do gerador
 * do usuario, replay tardio das sids com logout confirmado na falha, I1 pelo caminho do auditor com
 * origem {@code sonda}, evento {@code ReplayValidate} separado e nada no controlador, na latencia, nas
 * acoes S3 ou no mapa de invalidacoes.
 */
class SondaReplayTest {

    private static final LongUnaryOperator IDENTIDADE = t -> t;

    private static SondaReplay sonda(MundoS3Particao m, long sementeTardia) {
        return new SondaReplay(m.apps, m.relogio, m.p, m.registro, new Random(sementeTardia));
    }

    /** Roda so os atores da sonda ate {@code ateNs} (laco sequencial, como S3Simulacao.rodarSequencial). */
    static void rodarSonda(SondaReplay s, RelogioFalso r, long ateNs) {
        while (true) {
            AtorS3 prox = null;
            for (AtorS3 a : s.atores()) {
                if (prox == null || a.proximoNs() < prox.proximoNs()) {
                    prox = a;
                }
            }
            if (prox == null || prox.proximoNs() >= ateNs) {
                return;
            }
            r.dormirAteNs(prox.proximoNs());
            prox.passo();
        }
    }

    private static List<InvariantAuditor.Violacao> violacoes(MundoS3Particao m) {
        return new ArrayList<>(m.auditor.drenar());
    }

    private static List<String[]> eventos(MundoS3Particao m) throws Exception {
        Path tmp = Files.createTempFile("w10-ev", ".csv");
        try {
            m.eventos.flush(tmp);
            return Files.readAllLines(tmp, StandardCharsets.UTF_8).stream().skip(1)
                    .map(l -> l.split(",", -1)).collect(Collectors.toList());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Marcadores marcadores(long iniNs, Long fimNs, double wRecSeg) {
        List<String> l = new ArrayList<>();
        l.add("fault=F2 node=isn3 event=start t_epoch_ns=" + iniNs);
        if (fimNs != null) {
            l.add("fault=F2 node=isn3 event=end t_epoch_ns=" + fimNs);
        }
        return Marcadores.parse(l).comRecuperacao(RelogioFalso.seg(wRecSeg));
    }

    @Test
    @DisplayName("replay imediato: logout confirmado pelo no isolado e relido no no k+1, que tem o backup VALID: I1 com origem sonda")
    void replayImediatoVeBackupValid() throws Exception {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        m.sessaoValida("sid-a", "s3-u-002");
        m.particionar(2, 10, 100);
        m.relogio.agora += RelogioFalso.seg(20);
        assertEquals(OpResult.Code.OK_NOOP, m.apps.get(2).logout("sid-a").code(), "logout confirmado pelo isolado");
        assertEquals(SessionState.Estado.INVALID, m.minoria.get("sid-a").estado());
        assertEquals(SessionState.Estado.VALID, m.maioria.get("sid-a").estado(), "backup VALID na maioria");
        int eventosAntes = eventos(m).size();

        SondaReplay s = sonda(m, 1);
        long tLogout = m.relogio.agora;
        s.logoutConfirmado("sid-a", 2, tLogout, new Random(5));
        rodarSonda(s, m.relogio, tLogout + RelogioFalso.seg(5));

        assertEquals(1, s.leituras().size());
        SondaReplay.Leitura l = s.leituras().get(0);
        assertEquals(SondaReplay.IMEDIATO, l.tipo());
        assertEquals(2, l.noLogout());
        assertEquals(0, l.noAlvo(), "outro servidor de aplicacao: (2 + 1) mod 3");
        assertEquals("OK_VALIDATE:VALID", l.resultado());
        long atraso = l.tDevidoNs() - tLogout;
        assertTrue(atraso >= RelogioFalso.seg(1) && atraso < RelogioFalso.seg(3), "atraso em [1, 3) s: " + atraso);

        List<InvariantAuditor.Violacao> v = violacoes(m);
        assertEquals(1, v.size(), v.toString());
        assertEquals("I1", v.get(0).invariante());
        assertEquals("sid-a", v.get(0).chave());
        assertEquals(InvariantAuditor.ORIGEM_SONDA, v.get(0).origem());
        assertEquals("invalidacao confirmada (LOGOUT) revertida para VALID", v.get(0).mensagem(),
                "mesma mensagem do caminho atual do auditor");

        List<String[]> ev = eventos(m);
        assertEquals(eventosAntes + 1, ev.size(), "um evento so da sonda");
        String[] e = ev.get(ev.size() - 1);
        assertEquals(SessionOps.OP_REPLAY_VALIDATE, e[1]);
        assertEquals(MundoS3.NOS[0], e[4]);
        assertEquals("VALID", e[5]);
        assertEquals("sid-a", e[6]);
        assertEquals("ReplayValidate", SessionOps.OP_REPLAY_VALIDATE, "nome do tipo de operacao da sonda");
    }

    @Test
    @DisplayName("o alvo do replay e sempre o no (k+1) mod 3; leitura INVALID nao e violacao")
    void alvoEOProximoNo() {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        SondaReplay s = sonda(m, 1);
        Random r = new Random(9);
        for (int k = 0; k < 3; k++) {
            String sid = m.sessaoValida("sid-" + k, "s3-u-00" + k);
            assertEquals(OpResult.Code.OK_NOOP, m.apps.get(k).logout(sid).code());
            s.logoutConfirmado(sid, k, m.relogio.agora, r);
        }
        rodarSonda(s, m.relogio, m.relogio.agora + RelogioFalso.seg(10));
        assertEquals(3, s.leituras().size());
        for (SondaReplay.Leitura l : s.leituras()) {
            assertEquals((l.noLogout() + 1) % 3, l.noAlvo(), l.toString());
            assertEquals("OK_VALIDATE:INVALID", l.resultado());
        }
        assertTrue(violacoes(m).isEmpty());
    }

    @Test
    @DisplayName("atraso sorteado do gerador dado (mesma semente, mesmos atrasos) e dentro de [min, max) configurados")
    void atrasoDoGeradorDadoENoIntervalo() {
        List<Long> a = atrasos(1.0, 3.0, 77);
        List<Long> b = atrasos(1.0, 3.0, 77);
        List<Long> c = atrasos(1.0, 3.0, 78);
        assertEquals(a, b);
        assertFalse(a.equals(c));
        for (long x : a) {
            assertTrue(x >= RelogioFalso.seg(1) && x < RelogioFalso.seg(3), "atraso " + x);
        }
        for (long x : atrasos(0.5, 0.7, 3)) {
            assertTrue(x >= RelogioFalso.ms(500) && x < RelogioFalso.ms(700), "atraso " + x);
        }
    }

    private static List<Long> atrasos(double min, double max, long semente) {
        MundoS3Particao m = new MundoS3Particao(1, p -> {
            p.sondaReplay = true;
            p.sondaMinSeg = min;
            p.sondaMaxSeg = max;
        });
        SondaReplay s = sonda(m, 1);
        Random r = new Random(semente);
        long t = m.relogio.agora;
        for (int i = 0; i < 20; i++) {
            s.logoutConfirmado("sid-" + i, i % 3, t, r);
        }
        rodarSonda(s, m.relogio, t + RelogioFalso.seg(10));
        List<Long> out = new ArrayList<>();
        for (SondaReplay.Leitura l : s.leituras().stream()
                .sorted(java.util.Comparator.comparing(SondaReplay.Leitura::sid)).collect(Collectors.toList())) {
            out.add(l.tDevidoNs() - t);
        }
        assertEquals(20, out.size());
        return out;
    }

    @Test
    @DisplayName("a sonda nao avisa o controlador (eventos e violacoes), nao registra latencia, acao S3 nem confirmacao no mapa")
    void sondaSoInstrumenta() {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        AtomicInteger eventosAoControlador = new AtomicInteger();
        AtomicInteger violacoesAoControlador = new AtomicInteger();
        for (SessionOps o : m.apps) {
            o.definirOuvinteDeEvento((op, i, f, rc) -> eventosAoControlador.incrementAndGet());
        }
        m.auditor.definirOuvinte((inv, chave) -> violacoesAoControlador.incrementAndGet());
        m.sessaoValida("sid-b", "s3-u-002");
        m.particionar(2, 10, 100);
        m.relogio.agora += RelogioFalso.seg(20);
        m.apps.get(2).logout("sid-b");
        int eventosDaCarga = eventosAoControlador.get();
        // sid nunca invalidada: a leitura da sonda nao pode criar confirmacao nova no mapa do observador
        m.sessaoValida("sid-nunca", "s3-u-005");
        m.maioria.put("sid-nunca", m.maioria.get("sid-nunca").invalidar(SessionState.Motivo.LOGOUT,
                java.time.Instant.now()));

        SondaReplay s = sonda(m, 1);
        s.logoutConfirmado("sid-b", 2, m.relogio.agora, new Random(1));
        s.logoutConfirmado("sid-nunca", 2, m.relogio.agora, new Random(2));   // alvo no 0: le INVALID
        rodarSonda(s, m.relogio, m.relogio.agora + RelogioFalso.seg(5));

        assertEquals(2, s.leituras().size());
        assertEquals(eventosDaCarga, eventosAoControlador.get(), "ouvinte de evento (controlador) nao recebe a sonda");
        assertEquals(0, violacoesAoControlador.get(), "ouvinte de violacao (controlador) nao recebe a I1 da sonda");
        assertEquals(1, m.auditor.total(), "a I1 da sonda esta no auditor");
        for (int i = 0; i < 3; i++) {
            assertFalse(m.latencias.get(i).snapshot().containsKey("validate"), "sem latencia de validate no no " + i);
            assertTrue(m.latencias.get(i).snapshot().keySet().stream().noneMatch(k -> k.contains("replay")),
                    "sem latencia da sonda");
        }
        assertEquals(0, m.registro.totalAcoes(), "a sonda nao e acao S3");
        assertNull(m.invalidacoes.get("sid-nunca"), "a leitura INVALID da sonda nao entra no mapa de confirmacoes");
    }

    @Test
    @DisplayName("CSV de violacoes: coluna origem = sonda na I1 da sonda e vazia nas demais; o ouvinte ve so a da carga")
    void colunaOrigemNoCsv() throws Exception {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        AtomicInteger aoControlador = new AtomicInteger();
        m.auditor.definirOuvinte((inv, chave) -> aoControlador.incrementAndGet());
        m.sessaoValida("sid-c", "s3-u-002");
        m.particionar(2, 10, 100);
        m.relogio.agora += RelogioFalso.seg(20);
        m.apps.get(2).logout("sid-c");
        SondaReplay s = sonda(m, 1);
        s.logoutConfirmado("sid-c", 2, m.relogio.agora, new Random(1));
        rodarSonda(s, m.relogio, m.relogio.agora + RelogioFalso.seg(5));
        // leitura da carga (Validate comum) no no 1, que tambem ve o backup VALID: I1 sem origem
        m.apps.get(1).validate("sid-c");
        assertEquals(1, aoControlador.get());

        Path tmp = Files.createTempFile("w10-vi", ".csv");
        try {
            m.auditor.dumpCsv(tmp, null, List.of(InvariantAuditor.colunaOrigem()));
            List<String> l = Files.readAllLines(tmp, StandardCharsets.UTF_8);
            assertEquals("instante,invariante,chave,mensagem,origem", l.get(0));
            assertEquals(3, l.size());
            assertTrue(l.get(1).contains(",I1,sid-c,") && l.get(1).endsWith(",sonda"), l.get(1));
            assertTrue(l.get(2).contains(",I1,sid-c,") && l.get(2).endsWith(","), l.get(2));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("replay tardio: depois da fusao, a sid com logout confirmado na falha e relida por outro no e vira I1 da sonda")
    void replayTardioDepoisDaFusao() {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        long origem = m.relogio.agora;
        m.sessaoValida("sid-d", "s3-u-002");
        m.particionar(2, 10, 100);
        m.foraDoAr(0, 15, 40);   // o replay imediato cai no no 0 fora do ar
        m.relogio.agora = origem + RelogioFalso.seg(20);
        m.apps.get(2).logout("sid-d");
        SondaReplay s = sonda(m, 1);
        s.logoutConfirmado("sid-d", 2, m.relogio.agora, new Random(1));
        rodarSonda(s, m.relogio, m.relogio.agora + RelogioFalso.seg(5));
        assertEquals("ERROR_TRANSPORT", s.leituras().get(0).resultado());
        assertTrue(violacoes(m).isEmpty());

        m.relogio.agora = origem + RelogioFalso.seg(200);   // depois da fusao (100 s)
        int n = s.replayTardio(marcadores(origem + RelogioFalso.seg(10), origem + RelogioFalso.seg(100), 60), IDENTIDADE);
        assertEquals(1, n);
        SondaReplay.Leitura t = s.leituras().get(1);
        assertEquals(SondaReplay.TARDIO, t.tipo());
        assertEquals(0, t.noAlvo(), "no diferente do que confirmou (k+1)");
        assertEquals("OK_VALIDATE:VALID", t.resultado(), "a fusao perdeu o logout do isolado");
        List<InvariantAuditor.Violacao> v = violacoes(m);
        assertEquals(1, v.size());
        assertEquals("I1", v.get(0).invariante());
        assertEquals(InvariantAuditor.ORIGEM_SONDA, v.get(0).origem());
    }

    @Test
    @DisplayName("replay tardio: so logouts confirmados na falha ou na recuperacao; sem marcador start, nenhum; amostra limitada e semeada")
    void tardioSoDaJanelaEAmostraLimitada() {
        List<String> a = sidsTardios(2, 11);
        List<String> b = sidsTardios(2, 11);
        assertEquals(a, b, "mesma semente, mesma amostra");
        assertEquals(2, a.size());
        assertTrue(a.stream().noneMatch(x -> x.equals("antes") || x.equals("depois")), a.toString());
        assertEquals(List.of("f1", "f2", "f3", "r1"), sidsTardios(200, 11), "sem limite efetivo: todos da janela");

        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        SondaReplay s = sonda(m, 1);
        s.logoutConfirmado(m.sessaoValida("x", "s3-u-000"), 0, m.relogio.agora, new Random(1));
        assertEquals(0, s.replayTardio(null, IDENTIDADE), "sem --marker: sem replay tardio");
        assertEquals(0, s.replayTardio(Marcadores.vazio(), IDENTIDADE), "sem start: sem replay tardio");
    }

    private static List<String> sidsTardios(int limite, long semente) {
        MundoS3Particao m = new MundoS3Particao(1, p -> {
            p.sondaReplay = true;
            p.sondaTardiaMax = limite;
        });
        long o = m.relogio.agora;
        SondaReplay s = sonda(m, semente);
        Random r = new Random(1);
        String[][] logouts = {{"antes", "5"}, {"f1", "20"}, {"f2", "30"}, {"f3", "40"}, {"r1", "80"}, {"depois", "200"}};
        for (String[] x : logouts) {
            m.sessaoValida(x[0], "s3-u-000");
            s.logoutConfirmado(x[0], 0, o + RelogioFalso.seg(Double.parseDouble(x[1])), r);
        }
        m.relogio.agora = o + RelogioFalso.seg(300);
        // falha [10, 50]; recuperacao ate 50 + 60 = 110 s
        s.replayTardio(marcadores(o + RelogioFalso.seg(10), o + RelogioFalso.seg(50), 60), IDENTIDADE);
        return s.leituras().stream().filter(l -> l.tipo().equals(SondaReplay.TARDIO))
                .map(SondaReplay.Leitura::sid).collect(Collectors.toList());
    }

    @Test
    @DisplayName("replay tardio: no alvo indisponivel e marcado e o proximo no (diferente do que confirmou) e usado")
    void tardioPulaNoIndisponivel() {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        long o = m.relogio.agora;
        SondaReplay s = sonda(m, 1);
        Random r = new Random(1);
        for (String sid : List.of("u1", "u2")) {
            m.sessaoValida(sid, "s3-u-001");
            m.relogio.agora = o + RelogioFalso.seg(20);
            m.apps.get(1).logout(sid);
            s.logoutConfirmado(sid, 1, m.relogio.agora, r);
        }
        m.relogio.agora = o + RelogioFalso.seg(200);
        m.foraDoAr(2, 0, 200);   // relativo ao agora: o no 2 fica fora do ar desde ja
        s.replayTardio(marcadores(o + RelogioFalso.seg(10), o + RelogioFalso.seg(50), 60), IDENTIDADE);
        List<SondaReplay.Leitura> t = s.leituras().stream().filter(l -> l.tipo().equals(SondaReplay.TARDIO))
                .collect(Collectors.toList());
        assertEquals(3, t.size(), t.toString());
        assertEquals(2, t.get(0).noAlvo());
        assertEquals("ERROR_TRANSPORT", t.get(0).resultado());
        assertEquals(0, t.get(1).noAlvo());
        assertEquals("OK_VALIDATE:INVALID", t.get(1).resultado());
        assertEquals(0, t.get(2).noAlvo(), "no 2 ja marcado indisponivel");
        assertTrue(t.stream().noneMatch(l -> l.noAlvo() == 1), "nunca o no que confirmou");
    }

    @Test
    @DisplayName("replay imediato pendente no fim da carga e descartado e contado; a linha de resumo traz os totais")
    void pendentesDescartadasNoFim() {
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true);
        SondaReplay s = sonda(m, 1);
        s.logoutConfirmado(m.sessaoValida("p1", "s3-u-000"), 0, m.relogio.agora, new Random(1));
        s.replayTardio(Marcadores.vazio(), IDENTIDADE);
        assertTrue(s.leituras().isEmpty());
        rodarSonda(s, m.relogio, m.relogio.agora + RelogioFalso.seg(10));
        assertTrue(s.leituras().isEmpty(), "fila esvaziada no fim da carga");
        String linha = s.linhaResumo();
        assertTrue(linha.startsWith("Sonda replay: "), linha);
        assertTrue(linha.contains("imediatas_agendadas=1"), linha);
        assertTrue(linha.contains("imediatas_descartadas_fim=1"), linha);
        assertTrue(linha.contains("tardias_candidatas=0"), linha);
        assertEquals(1, m.registro.valor("sonda|imediata|descartada-fim"));
    }

    @Test
    @DisplayName("evento do replay imediato respeita a janela de medicao; o do tardio (depois da carga) entra sempre")
    void janelaDeMedicaoDosEventos() throws Exception {
        // janela de medicao ja encerrada: inicio ha 1000 s, duracao de 10 s
        WarmupPolicy encerrada = WarmupPolicy.padrao(System.currentTimeMillis() - 1_000_000L, 10, 0,
                Clock.systemUTC());
        MundoS3Particao m = new MundoS3Particao(1, p -> p.sondaReplay = true, encerrada);
        long o = m.relogio.agora;
        SondaReplay s = sonda(m, 1);
        m.sessaoValida("j1", "s3-u-000");
        m.relogio.agora = o + RelogioFalso.seg(20);
        m.apps.get(0).logout("j1");
        s.logoutConfirmado("j1", 0, m.relogio.agora, new Random(1));
        rodarSonda(s, m.relogio, m.relogio.agora + RelogioFalso.seg(5));
        assertEquals(1, s.leituras().size());
        assertTrue(eventos(m).isEmpty(), "fora da janela: o evento do imediato e descartado como os da carga");
        m.relogio.agora = o + RelogioFalso.seg(200);
        s.replayTardio(marcadores(o + RelogioFalso.seg(10), o + RelogioFalso.seg(50), 60), IDENTIDADE);
        List<String[]> ev = eventos(m);
        assertEquals(1, ev.size(), "o tardio entra mesmo fora da janela");
        assertEquals(SessionOps.OP_REPLAY_VALIDATE, ev.get(0)[1]);
        assertEquals(MundoS3.NOS[1], ev.get(0)[4]);
    }
}
