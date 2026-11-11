package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-33 (E2): aceite do cenario S3, itens (a), (d), (e) e consumo de RastroCliente/VarreduraFinal. */
class S3SimulacaoTest {

    /** Simulacao de 10 min do relogio falso, com um no de aplicacao caindo entre 100 s e 160 s. */
    private static MundoS3 corrida(long semente, double segundos, boolean comFalha) {
        MundoS3 m = new MundoS3(semente, null);
        if (comFalha) {
            m.foraDoAr(2, 100, 160);
        }
        m.rodar(segundos);
        return m;
    }

    @Test
    @DisplayName("(a) pools U e L sao disjuntos: U so Login/Validate/Logout e Block/Unblock; L so IncrementFailure/ResetFailures/Login e Unblock")
    void poolsDisjuntos() {
        MundoS3 m = corrida(42, 600, false);
        Set<String> opsU = new TreeSet<>();
        Set<String> opsL = new TreeSet<>();
        Set<String> alvosAdmin = new TreeSet<>();
        Set<String> alvosSuporte = new TreeSet<>();
        for (AcaoS3 a : m.registro.acoes()) {
            if (a.ator().startsWith("s3-u-")) opsU.add(a.operacao());
            else if (a.ator().startsWith("s3-l-")) opsL.add(a.operacao());
            else if (a.ator().equals("admin")) alvosAdmin.add(a.alvo());
            else if (a.ator().equals("suporte")) alvosSuporte.add(a.alvo());
            else throw new AssertionError("ator inesperado " + a.ator());
        }
        assertEquals(Set.of("Login", "Validate", "Logout"), opsU);
        assertEquals(Set.of("IncrementFailure", "ResetFailures", "Login"), opsL);
        assertFalse(alvosAdmin.isEmpty());
        assertTrue(alvosAdmin.stream().allMatch(x -> x.startsWith("s3-u-")), "admin so bloqueia identidades de U");
        assertFalse(alvosSuporte.isEmpty());
        assertTrue(alvosSuporte.stream().allMatch(x -> x.startsWith("s3-l-")), "suporte so desbloqueia identidades de L");
        // estado do grid: so contas de L acumulam falhas
        for (Map.Entry<String, IdentityState> e : m.identidades.dados.entrySet()) {
            if (e.getKey().startsWith("s3-u-")) {
                assertEquals(0, e.getValue().contadorFalhas(), e.getKey());
            }
        }
        Set<String> u = new HashSet<>(), l = new HashSet<>();
        m.identidades.dados.keySet().forEach(k -> (k.startsWith("s3-u-") ? u : l).add(k));
        assertTrue(Collections_disjoint(u, l));
        assertEquals(20, u.size());
        assertEquals(10, l.size());
    }

    private static boolean Collections_disjoint(Set<String> a, Set<String> b) {
        return java.util.Collections.disjoint(a, b);
    }

    @Test
    @DisplayName("(d) determinismo: mesma semente, mesma sequencia de acoes (com falha injetada); semente diferente muda")
    void determinismoPorSemente() {
        List<String> a = assinatura(corrida(7, 400, true));
        List<String> b = assinatura(corrida(7, 400, true));
        List<String> c = assinatura(corrida(8, 400, true));
        assertTrue(a.size() > 300, "a simulacao produziu acoes: " + a.size());
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    /** Sequencia de acoes sem os sid (UUID aleatorio nao faz parte da decisao dos atores). */
    private static List<String> assinatura(MundoS3 m) {
        List<String> out = new ArrayList<>();
        for (AcaoS3 a : m.registro.acoes()) {
            String alvo = a.alvo().startsWith("s3-") ? a.alvo() : "sid";
            out.add(a.tNs() + "|" + a.ator() + "|" + a.operacao() + "|" + alvo + "|" + a.resultado() + "|"
                    + a.tentativas() + "|" + a.falhaPercebida() + "|" + a.nota());
        }
        return out;
    }

    @Test
    @DisplayName("(e) escritas do S3 caem nos tres nos (campo replica do CSV) e o console usa o no da maioria")
    void escritasNosTresNos() throws Exception {
        MundoS3 m = corrida(5, 300, false);
        Path tmp = Files.createTempFile("s3-ev", ".csv");
        try {
            m.eventos.flush(tmp);
            List<String> l = Files.readAllLines(tmp);
            Map<String, Long> porNo = l.stream().skip(1).map(x -> x.split(",")[4])
                    .collect(Collectors.groupingBy(x -> x, Collectors.counting()));
            for (String no : MundoS3.NOS) {
                assertTrue(porNo.getOrDefault(no, 0L) > 0, "sem eventos no no " + no + ": " + porNo);
            }
            Set<String> escritas = l.stream().skip(1).map(x -> x.split(","))
                    .filter(c -> c[1].equals("Login") || c[1].equals("Logout") || c[1].equals("IncrementFailure"))
                    .map(c -> c[4]).collect(Collectors.toSet());
            assertEquals(new HashSet<>(List.of(MundoS3.NOS)), escritas, "escritas nos 3 nos");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("usuarios e contas sao sticky: cada identidade sempre no mesmo servidor de aplicacao")
    void stickyPorServidor() throws Exception {
        MundoS3 m = corrida(5, 200, false);
        Path tmp = Files.createTempFile("s3-ev", ".csv");
        try {
            m.eventos.flush(tmp);
            Map<String, Set<String>> nosPorIdentidade = new java.util.HashMap<>();
            for (String linha : Files.readAllLines(tmp).subList(1, Files.readAllLines(tmp).size())) {
                String[] c = linha.split(",");
                if (c[1].equals("Login") && c[6].startsWith("s3-u-")) {
                    nosPorIdentidade.computeIfAbsent(c[6], k -> new HashSet<>()).add(c[4]);
                }
            }
            assertFalse(nosPorIdentidade.isEmpty());
            nosPorIdentidade.forEach((id, nos) -> assertEquals(1, nos.size(), id + " " + nos));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("no de aplicacao fora do ar: usuarios dele veem falha percebida (2 retentativas), os dos outros nos seguem")
    void falhaIsolaUsuariosDoNo() {
        MundoS3 m = new MundoS3(9, p -> { p.contasLimite = 0; });
        m.foraDoAr(1, 60, 200);
        m.rodar(240);
        List<AcaoS3> falhas = m.registro.acoes().stream().filter(AcaoS3::falhaPercebida).collect(Collectors.toList());
        assertFalse(falhas.isEmpty());
        assertTrue(falhas.stream().allMatch(a -> a.tentativas() == 3 || a.operacao().equals("Block")),
                "exatamente 1 + 2 tentativas, sem retentativa infinita");
        // usuarios do no 1 (indices 1, 4, 7, ...) sao os unicos com falha percebida entre os usuarios
        for (AcaoS3 a : falhas) {
            if (a.ator().startsWith("s3-u-")) {
                assertEquals(1, Integer.parseInt(a.ator().substring(5)) % 3, a.ator());
            }
        }
        assertTrue(m.registro.acoes().stream().anyMatch(a -> a.ator().equals("s3-u-000")
                && a.tNs() > m.relogio.agora - RelogioFalso.seg(30)), "usuarios dos outros nos seguem ativos");
    }

    @Test
    @DisplayName("sem reparo: sessao orfa de Logout perdido continua VALID ate o fim e a varredura a ve")
    void semReparoEVarreduraConsomeRastro() {
        MundoS3 m = new MundoS3(21, p -> { p.contasLimite = 0; p.probLogout = 1.0; p.sessaoMedianaSeg = 5; p.sessaoSigma = 0.3; });
        m.foraDoAr(0, 40, 90);
        m.rodar(240);
        // sessoes VALID de identidades que sofreram falha de Logout permanecem VALID (nada repara)
        assertTrue(m.registro.valor("evento|LogoutFalhouSessaoOrfa") > 0);
        ConcurrentHashMap<String, SessionState> sess = new ConcurrentHashMap<>(m.sessoes.dados);
        ConcurrentHashMap<String, IdentityState> ids = new ConcurrentHashMap<>(m.identidades.dados);
        VarreduraFinal.Resultado r = VarreduraFinal.varrer(sess, ids, new java.util.HashMap<>(m.invalidacoes),
                m.rastro, SessionOps.LIMITE_TENTATIVAS);
        org.junit.jupiter.api.Assertions.assertNotNull(r.resumo());
        long orfas = sess.values().stream().filter(s -> s.estado() == SessionState.Estado.VALID).count();
        assertTrue(orfas > 0);
    }

    @Test
    @DisplayName("invalidacoes confirmadas compartilhadas: Logout num servidor e visto como I1 por outro que le a sessao VALID de novo")
    void invalidacoesCompartilhadas() {
        MundoS3 m = new MundoS3(1, null);
        String sid = "sid-x";
        m.sessoes.dados.put(sid, SessionState.criar(sid, "s3-u-000", Instant.now()));
        assertEquals(OpResult.Code.OK_NOOP, m.apps.get(0).logout(sid).code());
        // replica velha volta a mostrar VALID (ex.: apos fusao de particao)
        m.sessoes.dados.put(sid, SessionState.criar(sid, "s3-u-000", Instant.now()));
        m.apps.get(1).validate(sid);
        assertEquals(1, m.auditor.drenar().stream().filter(v -> v.invariante().equals("I1")).count());
        assertEquals(SessionState.Motivo.LOGOUT, m.invalidacoes.get(sid));
    }
}
