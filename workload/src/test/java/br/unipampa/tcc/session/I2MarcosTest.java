package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.9, regra A (DT-002): uma leitura de sessao VALID cuja identidade e lida BLOCKED e classificada
 * pelo instante {@code t0Sessao}, tomado imediatamente antes do {@code get} da sessao, contra os marcos
 * do bloqueio daquela identidade ({@link MarcoBloqueio}).
 *
 * <ul>
 *   <li>Z1: sessao lida antes de o {@code block} confirmar a identidade: {@code i2_concorrentes}, 0 I2
 *       (D4a do B-43.6 convertido; o teste do B-43.6 sobre o {@link MundoS3} nao muda).</li>
 *   <li>Z2: {@code validate} inteiro dentro da cascata: {@code I2_JANELA_CASCATA}, 0 I2 (D4b).</li>
 *   <li>Z3: leitura iniciada depois da cascata concluida, fake "velho": I2, REAL pelo rastro.</li>
 *   <li>Z4: LIMITE: I2 ESPERADA-POR-DESENHO. Z5: {@code unblock} remove o marco. Z6: sem marco.</li>
 *   <li>Z7: S3, marcos compartilhados entre servidores e admin. Z8: janela de medicao.</li>
 * </ul>
 */
class I2MarcosTest {

    private static final String U = "u-marco";

    /** Gancho que roda {@code acao} uma unica vez, na thread {@code dona}, para a chave dada. */
    private static java.util.function.Consumer<String> umaVezNaChave(Thread dona, String chave, Runnable acao) {
        boolean[] feito = {false};
        return k -> {
            if (!feito[0] && Thread.currentThread() == dona && chave.equals(k)) {
                feito[0] = true;
                acao.run();
            }
        };
    }

    @Test
    @DisplayName("Z1 (D4a convertido): sessao lida antes de o block confirmar a identidade => i2_concorrentes, 0 I2")
    void leituraAntesDaConfirmacao() {
        MundoMarcos w = new MundoMarcos();
        String sid = w.login(0, U);
        w.sessoes.depoisDoGet = umaVezNaChave(Thread.currentThread(), sid,
                () -> Concorrencia.emOutraThread(() -> w.admin.block(U)));

        OpResult r = w.apps.get(1).validate(sid);

        assertEquals(SessionState.Estado.VALID, r.estado());
        assertEquals(SessionState.Estado.INVALID, w.base.valor(sid).estado(), "a cascata terminou antes");
        assertEquals(List.of(), w.invariantes(), "corrida do observador, nao violacao (DT-001)");
        assertEquals(1, w.apps.get(1).i2Concorrentes());
        assertEquals(1, w.apps.get(1).i2ConcorrentesTotal());
        assertEquals(0, w.soma(SessionOps::i2SemMarco));
        assertEquals(0, w.soma(SessionOps::i2JanelaCascata));
    }

    @Test
    @DisplayName("Z1b: leitura iniciada durante o put(BLOCKED), antes de ele ser reconhecido => i2_concorrentes")
    void leituraDuranteOPutDaIdentidade() {
        MundoMarcos w = new MundoMarcos();
        String sid = w.login(0, U);
        CountDownLatch leu = new CountDownLatch(1);
        CountDownLatch segue = new CountDownLatch(1);
        Thread[] leitor = new Thread[1];
        OpResult[] res = new OpResult[1];
        w.sessoes.depoisDoGet = k -> {
            if (Thread.currentThread() == leitor[0]) {
                leu.countDown();
                Concorrencia.aguardar(segue);
            }
        };
        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        w.identidades.antes = (op, k) -> {
            // dentro do put(BLOCKED) do block: o validate comeca e le a sessao VALID, e so entao o put segue
            if (!feito[0] && Thread.currentThread() == dona && op.equals("put") && U.equals(k)) {
                feito[0] = true;
                leitor[0] = new Thread(() -> res[0] = w.apps.get(1).validate(sid), "leitor");
                leitor[0].start();
                Concorrencia.aguardar(leu);
            }
        };

        assertEquals(OpResult.Code.OK_NOOP, w.admin.block(U).code());
        segue.countDown();
        Concorrencia.juntar(leitor[0]);

        assertEquals(SessionState.Estado.VALID, res[0].estado());
        assertEquals(List.of(), w.invariantes(), "t0Sessao anterior ao put reconhecido: corrida");
        assertEquals(1, w.apps.get(1).i2Concorrentes());
        assertEquals(0, w.soma(SessionOps::i2JanelaCascata));
    }

    @Test
    @DisplayName("Z2 (D4b convertido): validate inteiro dentro da cascata => 1 I2_JANELA_CASCATA, 0 I2")
    void leituraDentroDaCascata() {
        MundoMarcos w = new MundoMarcos();
        String sid = w.login(0, U);
        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        OpResult[] res = new OpResult[1];
        w.base.antes = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("substituirSeVersao") && sid.equals(k)) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> res[0] = w.apps.get(1).validate(sid));
            }
        };

        assertEquals(OpResult.Code.OK_NOOP, w.admin.block(U).code());

        assertEquals(SessionState.Estado.VALID, res[0].estado(), "estado que existiu no grid");
        assertEquals(List.of(SessionOps.I2_JANELA_CASCATA + ":" + sid), w.invariantes());
        assertEquals(1, w.apps.get(1).i2JanelaCascata());
        assertEquals(0, w.soma(SessionOps::i2Concorrentes));
        assertEquals(0, w.soma(SessionOps::i2CascataIncompleta));
        assertTrue(w.admin.marco(U).cascataConcluida());
    }

    @Test
    @DisplayName("Z3 (controle positivo): leitura depois da cascata concluida ve VALID (fake velho) => I2 REAL")
    void leituraDepoisDaCascata() {
        MundoMarcos w = new MundoMarcos();
        String sid = w.login(0, U);
        assertEquals(OpResult.Code.OK_NOOP, w.admin.block(U).code());
        assertTrue(w.admin.marco(U).cascataConcluida());
        w.base.semear(sid, SessionState.criar(sid, U, MundoMarcos.T)); // replica velha

        assertEquals(SessionState.Estado.VALID, w.apps.get(2).validate(sid).estado());

        List<InvariantAuditor.Violacao> v = w.auditor.drenar().stream()
                .filter(x -> x.invariante().startsWith("I2")).toList();
        assertEquals(1, v.size(), v.toString());
        assertEquals("I2", v.get(0).invariante());
        assertEquals("REAL", w.rastro.classificarViolacaoI2(sid, InvariantAuditor.epochNs(v.get(0).em())));
        assertEquals(0, w.soma(SessionOps::i2SemMarco));
        assertEquals(0, w.soma(SessionOps::i2Concorrentes) + w.soma(SessionOps::i2JanelaCascata));
    }

    @Test
    @DisplayName("Z4 (LIMITE): incrementFailure atinge o limite; sessao VALID lida depois => I2 ESPERADA-POR-DESENHO")
    void limite() {
        MundoMarcos w = new MundoMarcos();
        String id = "lim-z4";
        String sid = w.login(0, id);
        for (int i = 0; i < SessionOps.LIMITE_TENTATIVAS; i++) {
            w.apps.get(1).incrementFailure(id);
        }
        assertEquals(IdentityState.Status.BLOCKED, w.identidades.dados.get(id).status());
        MarcoBloqueio m = w.admin.marco(id);
        assertNotNull(m, "o incrementFailure que atinge o limite grava o marco");
        assertEquals(MarcoBloqueio.Tipo.LIMITE, m.tipo());
        assertEquals(m.marcoIdentidadeNs(), m.marcoCascataNs(), "sem cascata");

        assertEquals(SessionState.Estado.VALID, w.apps.get(2).validate(sid).estado());

        List<InvariantAuditor.Violacao> v = w.auditor.drenar();
        assertEquals(List.of("I2"), v.stream().map(InvariantAuditor.Violacao::invariante).toList());
        assertEquals("ESPERADA-POR-DESENHO", w.rastro.classificarViolacaoI2(sid, InvariantAuditor.epochNs(v.get(0).em())));
        assertEquals(0, w.soma(SessionOps::i2SemMarco));
    }

    @Test
    @DisplayName("Z5: unblock remove o marco; um block novo grava marcos novos")
    void unblockRemoveMarco() {
        MundoMarcos w = new MundoMarcos();
        w.login(0, U);
        w.admin.block(U);
        MarcoBloqueio m1 = w.admin.marco(U);
        assertNotNull(m1);
        assertTrue(m1.cascataConcluida());

        assertEquals(OpResult.Code.OK_NOOP, w.admin.unblock(U).code());
        assertNull(w.admin.marco(U), "unblock reconhecido remove o marco");

        w.admin.block(U);
        MarcoBloqueio m2 = w.admin.marco(U);
        assertNotNull(m2);
        assertTrue(m2.cascataConcluida());
        assertTrue(m2.marcoIdentidadeNs() > m1.marcoCascataNs(), "marcos do periodo novo");
    }

    @Test
    @DisplayName("Z6: BLOCKED montado direto no fake (sem marco) => I2 como antes, i2_sem_marco = 1")
    void semMarco() {
        MundoMarcos w = new MundoMarcos();
        w.identidades.dados.put(U, IdentityState.criar(U, MundoMarcos.T).bloquear(MundoMarcos.T));
        w.base.semear("sid-z6", SessionState.criar("sid-z6", U, MundoMarcos.T));

        w.apps.get(0).validate("sid-z6");

        assertEquals(List.of("I2:sid-z6"), w.invariantes());
        assertEquals(1, w.apps.get(0).i2SemMarco());
    }

    @Test
    @DisplayName("Z7 (S3): o admin bloqueia, outro servidor classifica pelos marcos compartilhados")
    void marcosCompartilhados() throws Exception {
        MundoMarcos w = new MundoMarcos();
        String sid = w.login(0, U);
        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        w.base.antes = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("substituirSeVersao") && sid.equals(k)) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> w.apps.get(2).validate(sid));
            }
        };

        w.admin.block(U);

        assertSame(w.admin.marco(U), w.apps.get(2).marco(U), "mesmo mapa");
        assertEquals(List.of(SessionOps.I2_JANELA_CASCATA + ":" + sid), w.invariantes());
        assertEquals(1, w.apps.get(2).i2JanelaCascata());
        assertEquals(0, w.apps.get(2).i2SemMarco());

        // producao: S3Clientes passa o MESMO mapa de marcos a todos os SessionOps (servidores e admin)
        String s = ArmazemVersionadoTest.fonteSemComentarios("S3Clientes.java");
        assertTrue(s.contains("private final ConcurrentHashMap<String, MarcoBloqueio> marcos = new ConcurrentHashMap<>();"));
        Matcher m = Pattern.compile("new SessionOps\\(([^;]*)\\);").matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(m.group(1).replaceAll("\\s+", " ").contains("c.invalidacoes, c.marcos,"), m.group(1));
        }
        assertEquals(4, n, "dois SessionOps no modo pinned e dois no topology");
        String o = ArmazemVersionadoTest.fonteSemComentarios("SessionOps.java").replaceAll("\\s+", " ");
        assertTrue(o.contains("rastro, invalidacoesCompartilhadas, marcosCompartilhados);"),
                "o construtor do S3 repassa o mapa compartilhado");
    }

    @Test
    @DisplayName("Z8: corridas fora da janela de medicao nao contam nos contadores com janela, so nos totais")
    void janelaDeMedicao() {
        MundoMarcos w = new MundoMarcos();
        for (SessionOps o : w.todas()) {
            o.definirPoliticaAuditor(WarmupPolicy.never());
        }
        corridas(w);

        assertEquals(0, w.apps.get(1).i2Concorrentes());
        assertEquals(1, w.apps.get(1).i2ConcorrentesTotal());
        assertEquals(0, w.apps.get(2).i1Concorrentes());
        assertEquals(1, w.apps.get(2).i1ConcorrentesTotal());
        assertEquals(1, w.apps.get(1).i1ConcorrentesTotal(), "o Z1 tambem e corrida de I1 (confirmacao depois da leitura)");
        assertEquals(0, w.soma(SessionOps::i2JanelaCascata));
        assertEquals(List.of(SessionOps.I2_JANELA_CASCATA), w.auditor.drenar().stream()
                .map(InvariantAuditor.Violacao::invariante).toList(), "o registro no auditor nao tem janela");
        assertEquals("Auditor: i1_concorrentes=0 i4_concorrentes=0", SessionOps.linhaAuditor(w.todas()));
        assertEquals("Auditor I2: concorrentes=0 janela_cascata=0 cascata_incompleta=0 sem_marco=0",
                SessionOps.linhaAuditorI2(w.todas()));
        assertEquals("Auditor total: i1_concorrentes=2 i4_concorrentes=0 i2_concorrentes=1",
                SessionOps.linhaAuditorTotal(w.todas()));
    }

    @Test
    @DisplayName("Z8b: dentro da janela (politica padrao) os contadores com janela igualam os totais")
    void dentroDaJanela() {
        MundoMarcos w = new MundoMarcos();
        corridas(w);

        assertEquals("Auditor: i1_concorrentes=2 i4_concorrentes=0", SessionOps.linhaAuditor(w.todas()));
        assertEquals("Auditor I2: concorrentes=1 janela_cascata=1 cascata_incompleta=0 sem_marco=0",
                SessionOps.linhaAuditorI2(w.todas()));
        assertEquals("Auditor total: i1_concorrentes=2 i4_concorrentes=0 i2_concorrentes=1",
                SessionOps.linhaAuditorTotal(w.todas()));
        assertEquals("Login: revertido_por_bloqueio=0", SessionOps.linhaLogin(w.todas()));
    }

    /**
     * Uma i2 concorrente (Z1, servidor 1, que tambem conta uma i1 concorrente: a confirmacao da cascata
     * surge depois da leitura), uma janela (Z2, servidor 0) e uma i1 concorrente (servidor 2).
     */
    private static void corridas(MundoMarcos w) {
        Thread dona = Thread.currentThread();
        String sid = w.login(0, U);
        w.sessoes.depoisDoGet = umaVezNaChave(dona, sid, () -> Concorrencia.emOutraThread(() -> w.admin.block(U)));
        w.apps.get(1).validate(sid);

        String u2 = "u-marco-2";
        String sid2 = w.login(0, u2);
        boolean[] feito = {false};
        w.base.antes = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("substituirSeVersao") && sid2.equals(k)) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> w.apps.get(0).validate(sid2));
            }
        };
        w.admin.block(u2);
        w.base.antes = (op, k) -> { };

        String sid3 = w.login(0, "u-marco-3");
        w.sessoes.depoisDoGet = umaVezNaChave(dona, sid3, () -> Concorrencia.emOutraThread(() -> w.apps.get(0).logout(sid3)));
        w.apps.get(2).validate(sid3);
        w.sessoes.depoisDoGet = k -> { };
    }
}
