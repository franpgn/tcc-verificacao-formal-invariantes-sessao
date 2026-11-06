package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-10: a sonda de replay dentro da simulacao S3 (laco sequencial com relogio falso): desligada nao cria
 * nada; ligada cria um ator por no, nao muda a sequencia das acoes dos atores existentes, agenda um
 * replay por logout confirmado, e deterministica com a mesma semente e, numa particao, observa a I1 que o
 * botao voltar (no mesmo no) nao ve.
 */
class S3SondaSimulacaoTest {

    private static final LongUnaryOperator IDENTIDADE = t -> t;

    @Test
    @DisplayName("sonda desligada (padrao): nenhum ator a mais e sonda() nula")
    void desligadaNaoCriaAtores() {
        MundoS3 m = new MundoS3(3, null);
        assertFalse(m.p.sondaReplay, "padrao desligado");
        S3Simulacao s = m.simulacao();
        assertEquals(20 + 10 + 1, s.atores().size());
        assertNull(s.sonda());
    }

    @Test
    @DisplayName("sonda ligada: um ator por servidor de aplicacao, depois dos atores existentes")
    void ligadaCriaUmAtorPorNo() {
        MundoS3 m = new MundoS3(3, p -> p.sondaReplay = true);
        S3Simulacao s = m.simulacao();
        assertNotNull(s.sonda());
        assertEquals(20 + 10 + 1 + 3, s.atores().size());
        for (int i = 0; i < 31; i++) {
            assertFalse(s.atores().get(i).id().startsWith("sonda-"), s.atores().get(i).id());
        }
        assertEquals(List.of("sonda-0", "sonda-1", "sonda-2"),
                s.atores().subList(31, 34).stream().map(AtorS3::id).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("sonda ligada nao muda as acoes dos atores (mesma semente, sem perda de tempo no grid) nem os eventos da carga")
    void ligadaNaoMudaAsSequenciasExistentes() throws Exception {
        MundoS3 off = new MundoS3(11, null);
        off.rodar(240);
        MundoS3 on = new MundoS3(11, p -> p.sondaReplay = true);
        on.rodar(240);
        assertEquals(assinaturaAcoes(off), assinaturaAcoes(on));
        List<String> evOff = eventosDaCarga(off.eventos);
        List<String> evOn = eventosDaCarga(on.eventos);
        assertEquals(evOff, evOn, "eventos da carga (sem os ReplayValidate) identicos");
        assertTrue(contar(on.eventos, SessionOps.OP_REPLAY_VALIDATE) > 0, "a sonda emitiu eventos");
        assertEquals(0, contar(off.eventos, SessionOps.OP_REPLAY_VALIDATE));
    }

    @Test
    @DisplayName("cada logout confirmado ao cliente ganha um replay imediato no no (k+1) mod 3, entre 1 e 3 s depois")
    void umReplayPorLogoutConfirmado() {
        MundoS3 m = new MundoS3(5, p -> p.sondaReplay = true);
        S3Simulacao s = m.simulacao();
        MundoS3.rodar(s, m.relogio.agora + RelogioFalso.seg(240));
        List<AcaoS3> logouts = m.registro.acoes().stream()
                .filter(a -> a.operacao().equals("Logout") && !a.falhaPercebida()).collect(Collectors.toList());
        assertTrue(logouts.size() > 20, "logouts: " + logouts.size());
        SondaReplay sonda = s.sonda();
        s.sonda().replayTardio(Marcadores.vazio(), IDENTIDADE);
        long descartadas = m.registro.valor("sonda|imediata|descartada-fim");
        List<SondaReplay.Leitura> im = sonda.leituras().stream()
                .filter(l -> l.tipo().equals(SondaReplay.IMEDIATO)).collect(Collectors.toList());
        assertEquals(logouts.size(), im.size() + descartadas);
        Map<String, Integer> noDoUsuario = new HashMap<>();
        for (AcaoS3 a : logouts) {
            noDoUsuario.put(a.alvo(), Integer.parseInt(a.ator().substring(5)) % 3);
        }
        for (SondaReplay.Leitura l : im) {
            assertEquals(noDoUsuario.get(l.sid()), l.noLogout(), l.toString());
            assertEquals((l.noLogout() + 1) % 3, l.noAlvo());
            long atraso = l.tDevidoNs() - l.tLogoutNs();
            assertTrue(atraso >= RelogioFalso.seg(1) && atraso < RelogioFalso.seg(3), l.toString());
            assertTrue(l.tInicioNs() >= l.tDevidoNs());
        }
    }

    @Test
    @DisplayName("determinismo: mesma semente, mesma sequencia da sonda (com particao); semente diferente muda")
    void determinismoDaSonda() {
        List<String> a = sequenciaDaSonda(7);
        List<String> b = sequenciaDaSonda(7);
        List<String> c = sequenciaDaSonda(8);
        assertTrue(a.size() > 20, "replays: " + a.size());
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    private static List<String> sequenciaDaSonda(long semente) {
        MundoS3Particao m = new MundoS3Particao(semente, p -> p.sondaReplay = true);
        long o = m.relogio.agora;
        m.particionar(2, 60, 160);
        S3Simulacao s = m.simulacao();
        s.rodarSequencial(o + RelogioFalso.seg(300), () -> false);
        s.sonda().replayTardio(marcadores(o + RelogioFalso.seg(60), o + RelogioFalso.seg(160), 60), IDENTIDADE);
        Map<String, String> sids = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (SondaReplay.Leitura l : s.sonda().leituras()) {
            String sid = sids.computeIfAbsent(l.sid(), k -> "sid#" + sids.size());
            out.add(l.tipo() + "|" + (l.tLogoutNs() - o) + "|" + (l.tDevidoNs() - o) + "|" + sid + "|"
                    + l.noLogout() + "|" + l.noAlvo() + "|" + l.resultado());
        }
        return out;
    }

    @Test
    @DisplayName("particao: a sonda ve as duas formas de I1 (logout do isolado perdido; copia velha no isolado), mais sids que a carga sozinha")
    void particaoGeraI1DaSonda() {
        MundoS3Particao m = new MundoS3Particao(21, p -> {
            p.sondaReplay = true;
            p.contasLimite = 0;
            p.probLogout = 1.0;
            p.sessaoMedianaSeg = 5;
            p.sessaoSigma = 0.3;
        });
        long o = m.relogio.agora;
        long ini = o + RelogioFalso.seg(30);
        long fim = o + RelogioFalso.seg(150);
        m.particionar(2, 30, 150);
        S3Simulacao s = m.simulacao();
        s.rodarSequencial(o + RelogioFalso.seg(240), () -> false);
        s.sonda().replayTardio(marcadores(ini, fim, 60), IDENTIDADE);
        List<InvariantAuditor.Violacao> v = new ArrayList<>(m.auditor.drenar());
        List<InvariantAuditor.Violacao> daSonda = v.stream()
                .filter(x -> InvariantAuditor.ORIGEM_SONDA.equals(x.origem())).collect(Collectors.toList());
        assertFalse(daSonda.isEmpty(), "a sonda observou I1: " + v);
        assertTrue(daSonda.stream().allMatch(x -> x.invariante().equals("I1")), daSonda.toString());
        List<SondaReplay.Leitura> ls = s.sonda().leituras();
        // (M1) logout confirmado pelo isolado na janela, relido por outro no (que tem o backup VALID)
        Set<String> logoutDoIsolado = ls.stream()
                .filter(l -> l.noLogout() == 2 && l.tLogoutNs() >= ini && l.tLogoutNs() < fim)
                .map(SondaReplay.Leitura::sid).collect(Collectors.toSet());
        // (copia velha) logout confirmado pela maioria, relido pelo isolado na janela (copia VALID dele)
        Set<String> copiaVelhaNoIsolado = ls.stream()
                .filter(l -> l.noAlvo() == 2 && l.tInicioNs() >= ini && l.tInicioNs() < fim
                        && l.resultado().equals("OK_VALIDATE:VALID"))
                .map(SondaReplay.Leitura::sid).collect(Collectors.toSet());
        for (InvariantAuditor.Violacao x : daSonda) {
            assertTrue(logoutDoIsolado.contains(x.chave()) || copiaVelhaNoIsolado.contains(x.chave()),
                    "I1 da sonda so pelos dois mecanismos da particao: " + x);
        }
        assertTrue(daSonda.stream().anyMatch(x -> logoutDoIsolado.contains(x.chave())), "M1 observado");
        assertTrue(ls.stream().anyMatch(l -> l.tipo().equals(SondaReplay.TARDIO) && l.noLogout() == 2
                && l.resultado().equals("OK_VALIDATE:VALID")), "o replay tardio ve a perda depois da fusao");
        // a carga sozinha (botao voltar no mesmo no, cascata do admin) ve menos sids com I1 do que a sonda
        Set<String> daCarga = v.stream().filter(x -> x.origem() == null && x.invariante().equals("I1"))
                .map(InvariantAuditor.Violacao::chave).collect(Collectors.toSet());
        Set<String> sidsDaSonda = daSonda.stream().map(InvariantAuditor.Violacao::chave).collect(Collectors.toSet());
        assertTrue(sidsDaSonda.size() > daCarga.size(), "sonda " + sidsDaSonda.size() + " x carga " + daCarga.size());
    }

    // ------------------------------------------------------------------ utilitarios

    private static Marcadores marcadores(long iniNs, long fimNs, double wRecSeg) {
        return Marcadores.parse(List.of("fault=F2 node=isn3 event=start t_epoch_ns=" + iniNs,
                "fault=F2 node=isn3 event=end t_epoch_ns=" + fimNs)).comRecuperacao(RelogioFalso.seg(wRecSeg));
    }

    private static List<String> assinaturaAcoes(MundoS3 m) {
        List<String> out = new ArrayList<>();
        Map<String, String> sids = new HashMap<>();
        for (AcaoS3 a : m.registro.acoes()) {
            String alvo = a.alvo().startsWith("s3-") ? a.alvo() : sids.computeIfAbsent(a.alvo(), k -> "sid#" + sids.size());
            out.add(a.tNs() + "|" + a.ator() + "|" + a.operacao() + "|" + alvo + "|" + a.resultado() + "|"
                    + a.tentativas() + "|" + a.falhaPercebida() + "|" + a.nota() + "|" + a.tFimNs());
        }
        return out;
    }

    private static List<String> eventosDaCarga(EventCsvWriter ev) throws Exception {
        Map<String, String> sids = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String[] c : linhas(ev)) {
            if (c[1].equals(SessionOps.OP_REPLAY_VALIDATE)) {
                continue;
            }
            String k = c[6].startsWith("s3-") ? c[6] : sids.computeIfAbsent(c[6], x -> "sid#" + sids.size());
            out.add(c[1] + "|" + c[4] + "|" + c[5] + "|" + k);
        }
        return out;
    }

    private static long contar(EventCsvWriter ev, String op) throws Exception {
        return linhas(ev).stream().filter(c -> c[1].equals(op)).count();
    }

    private static List<String[]> linhas(EventCsvWriter ev) throws Exception {
        Path tmp = Files.createTempFile("w10-sim", ".csv");
        try {
            ev.flush(tmp);
            return Files.readAllLines(tmp, StandardCharsets.UTF_8).stream().skip(1)
                    .map(l -> l.split(",", -1)).collect(Collectors.toList());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
