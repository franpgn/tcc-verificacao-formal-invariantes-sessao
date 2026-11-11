package br.unipampa.tcc.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E2, aceite (c)): retentativa e backoff da aplicacao S3. No maximo 2 retentativas com
 * esperas de 500 ms e 1 s; depois disso a operacao conta como falha percebida pelo usuario;
 * nunca retentativa infinita.
 */
class AplicacaoS3Test {

    private RelogioFalso relogio;
    private S3Parametros p;
    private S3Registro registro;
    private AplicacaoS3 app;

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso(1_000_000_000L);
        p = new S3Parametros();
        registro = new S3Registro();
        app = new AplicacaoS3(relogio, p, registro);
    }

    /** Chamada que falha {@code falhas} vezes e depois devolve {@code depois}. */
    private static Supplier<OpResult> falhaDepoisDe(int falhas, OpResult erro, OpResult depois, AtomicInteger n) {
        return () -> n.incrementAndGet() <= falhas ? erro : depois;
    }

    @Test
    @DisplayName("sucesso na primeira tentativa nao espera nada")
    void semFalhaSemEspera() {
        AtomicInteger n = new AtomicInteger();
        OpResult r = app.executar("u", "Validate", "sid", p.appRetentativas,
                falhaDepoisDe(0, OpResult.errorTransport(), OpResult.okNoop(), n));
        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(1, n.get());
        assertTrue(relogio.esperas.isEmpty());
        assertEquals(0, app.falhasPercebidas());
    }

    @Test
    @DisplayName("duas falhas e sucesso na terceira: esperas de 500 ms e 1 s, sem falha percebida")
    void backoffDe500ms1s() {
        AtomicInteger n = new AtomicInteger();
        OpResult r = app.executar("u", "Validate", "sid", p.appRetentativas,
                falhaDepoisDe(2, OpResult.errorTransport(), OpResult.okNoop(), n));
        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(3, n.get());
        assertEquals(List.of(RelogioFalso.ms(500), RelogioFalso.ms(1000)), relogio.esperas);
        assertEquals(0, app.falhasPercebidas());
    }

    @Test
    @DisplayName("falha em todas as tentativas: exatamente 1 + 2 chamadas, 2 esperas, 1 falha percebida")
    void semRetentativaInfinita() {
        AtomicInteger n = new AtomicInteger();
        OpResult r = app.executar("u", "Logout", "sid", p.appRetentativas,
                falhaDepoisDe(1000, OpResult.errorTransport(), OpResult.okNoop(), n));
        assertEquals(OpResult.Code.ERROR_TRANSPORT, r.code());
        assertEquals(3, n.get(), "1 tentativa + 2 retentativas, nunca mais");
        assertEquals(List.of(RelogioFalso.ms(500), RelogioFalso.ms(1000)), relogio.esperas,
                "nao dorme depois da ultima tentativa");
        assertEquals(1, app.falhasPercebidas());
        assertEquals(1, app.falhasPercebidas("Logout"));
    }

    @Test
    @DisplayName("ERROR_UNAVAILABLE tambem e retentado; BLOCKED e NOT_FOUND nao sao erro e nao sao retentados")
    void somenteErrosSaoRetentados() {
        AtomicInteger n = new AtomicInteger();
        app.executar("u", "Login", "id", p.appRetentativas,
                falhaDepoisDe(1, OpResult.errorUnavailable(), OpResult.okLogin("s"), n));
        assertEquals(2, n.get());

        n.set(0);
        OpResult b = app.executar("u", "Login", "id", p.appRetentativas,
                () -> { n.incrementAndGet(); return OpResult.blocked(); });
        assertEquals(OpResult.Code.BLOCKED, b.code());
        assertEquals(1, n.get());

        n.set(0);
        app.executar("u", "Validate", "sid", p.appRetentativas,
                () -> { n.incrementAndGet(); return OpResult.notFound(); });
        assertEquals(1, n.get());
        assertEquals(0, app.falhasPercebidas());
    }

    @Test
    @DisplayName("numero de retentativas e backoff sao parametros; retentativas 0 desliga a repeticao")
    void parametrizavel() {
        p.appRetentativas = 0;
        AtomicInteger n = new AtomicInteger();
        app.executar("u", "Validate", "sid", p.appRetentativas,
                falhaDepoisDe(5, OpResult.errorTransport(), OpResult.okNoop(), n));
        assertEquals(1, n.get());
        assertTrue(relogio.esperas.isEmpty());

        p.backoffMs = 100;
        n.set(0);
        relogio.esperas.clear();
        app.executar("u", "Validate", "sid", 3,
                falhaDepoisDe(5, OpResult.errorTransport(), OpResult.okNoop(), n));
        assertEquals(4, n.get());
        assertEquals(List.of(RelogioFalso.ms(100), RelogioFalso.ms(200), RelogioFalso.ms(400)), relogio.esperas);
    }

    @Test
    @DisplayName("cada acao de aplicacao vira uma linha do registro com tentativas, resultado e falha percebida")
    void registraAcoes() {
        AtomicInteger n = new AtomicInteger();
        app.executar("u1", "Validate", "sid-1", 2,
                falhaDepoisDe(2, OpResult.errorTransport(), OpResult.okValidate(SessionState.Estado.INVALID), n));
        app.executar("u1", "Logout", "sid-1", 2, () -> OpResult.errorUnavailable());
        List<AcaoS3> acoes = registro.acoes();
        assertEquals(2, acoes.size());
        assertEquals("u1", acoes.get(0).ator());
        assertEquals("OK_VALIDATE:INVALID", acoes.get(0).resultado());
        assertEquals(3, acoes.get(0).tentativas());
        assertFalse(acoes.get(0).falhaPercebida());
        assertEquals("ERROR_UNAVAILABLE", acoes.get(1).resultado());
        assertTrue(acoes.get(1).falhaPercebida());
    }

    @Test
    @DisplayName("Block com erro: retentativa unica, marcado como tentativa incerta")
    void blockIncerto() {
        AtomicInteger n = new AtomicInteger();
        List<AcaoS3> antes = new ArrayList<>(registro.acoes());
        assertTrue(antes.isEmpty());
        OpResult r = app.executar("admin", SessionOps.OP_BLOCK, "u", 1,
                falhaDepoisDe(1, OpResult.errorTransport(), OpResult.okNoop(), n));
        assertEquals(OpResult.Code.OK_NOOP, r.code());
        assertEquals(2, n.get());
        AcaoS3 a = registro.acoes().get(0);
        assertEquals("tentativa-incerta", a.nota());
        assertEquals(2, a.tentativas());
    }
}
