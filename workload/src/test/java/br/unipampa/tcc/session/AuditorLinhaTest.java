package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.6, item 5: linha {@code Auditor: i1_concorrentes=<n> i4_concorrentes=<m>} no stdout do fim da
 * repeticao (S1/S2 e S3). No S3 e a soma das instancias que compartilham o mapa de invalidacoes
 * confirmadas (servidores de aplicacao e admin).
 */
class AuditorLinhaTest {

    private static SessionOps ops(Armazem<SessionState> s, ConcurrentHashMap<String, SessionState.Motivo> m,
                                  InvariantAuditor a) {
        return new SessionOps(s, new FakeCaches.Cache<>(), new LatencyRegistry(), a, null, null, "fake", null, m);
    }

    /** Leitor {@code b} pausado depois do get de {@code sid}; durante a pausa {@code a} faz logout. */
    private static void corrida(AuditorCorridaTest.ArmazemPausavel<SessionState> armazemB, SessionOps b,
                                SessionOps a, String sid) throws Exception {
        Thread t = new Thread(() -> b.validate(sid), "thread-B");
        armazemB.alvo = t;
        t.start();
        assertTrue(armazemB.leu.await(10, TimeUnit.SECONDS));
        a.logout(sid);
        armazemB.segue.countDown();
        t.join(10_000);
        assertTrue(!t.isAlive());
    }

    @Test
    @DisplayName("formato da linha sem corridas")
    void formatoZerado() {
        SessionOps o = ops(new FakeCaches.Cache<>(), new ConcurrentHashMap<>(), new InvariantAuditor());
        assertEquals("Auditor: i1_concorrentes=0 i4_concorrentes=0", SessionOps.linhaAuditor(List.of(o)));
    }

    @Test
    @DisplayName("S3: a linha soma as instancias que compartilham o mapa")
    void somaInstanciasDoMapaCompartilhado() throws Exception {
        FakeCaches.Cache<SessionState> base = new FakeCaches.Cache<>();
        Instant t = Instant.parse("2026-10-22T00:00:00Z");
        base.dados.put("s1", SessionState.criar("s1", "u1", t));
        base.dados.put("s2", SessionState.criar("s2", "u2", t));
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor auditor = new InvariantAuditor();
        AuditorCorridaTest.ArmazemPausavel<SessionState> vx = new AuditorCorridaTest.ArmazemPausavel<>(base);
        AuditorCorridaTest.ArmazemPausavel<SessionState> vy = new AuditorCorridaTest.ArmazemPausavel<>(base);
        SessionOps x = ops(vx, mapa, auditor);
        SessionOps y = ops(vy, mapa, auditor);

        corrida(vx, x, y, "s1");
        corrida(vy, y, x, "s2");

        assertEquals(0, auditor.total());
        assertEquals(1, x.i1Concorrentes());
        assertEquals(1, y.i1Concorrentes());
        assertEquals("Auditor: i1_concorrentes=2 i4_concorrentes=0", SessionOps.linhaAuditor(List.of(x, y)));
    }

    @Test
    @DisplayName("WorkloadMain imprime a linha uma vez por repeticao, e o S3 soma servidores e admin")
    void workloadMainImprime() throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java");
        String s = DetectorFonte.semComentarios(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        String m = DetectorFonte.mascarado(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        int[] corpo = DetectorFonte.corpo(m, DetectorFonte.ASSINATURA_REPETICAO);
        String rep = s.substring(corpo[0], corpo[1]);
        Matcher linha = Pattern.compile("(?m)^\\s*System\\.out\\.println\\(SessionOps\\.linhaAuditor\\(instanciasAuditor\\)\\);")
                .matcher(rep);
        assertTrue(linha.find(), "linha Auditor ausente em executarRepeticao");
        int pos = linha.start();
        assertTrue(!linha.find(), "linha Auditor duplicada");
        List<String> blocos = DetectorFonte.blocosEnvolventes(m, corpo[0], corpo[0] + pos + 1);
        assertEquals(1, blocos.size(), "a linha Auditor tem de valer para S1/S2 e S3 (fora de if): " + blocos);
        assertTrue(rep.contains("List<SessionOps> instanciasAuditor = new ArrayList<>(List.of(ops));"));
        assertTrue(s.contains("instanciasAuditor.addAll(clientes.instancias());"));
    }
}
