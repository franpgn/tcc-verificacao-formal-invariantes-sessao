package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B-43.2, T9 (P-d): contador em memoria dos desfechos reais de Validate na janela de medicao. Separa
 * VALID, INVALID, NOT_FOUND e ERROR_*, que o CSV mistura (C4: OK_VALIDATE vira sempre VALID e
 * NOT_FOUND vira NONE). O CSV e o {@code OpResult} nao mudam.
 */
class DesfechosValidateTest {

    private FakeCaches.Cache<SessionState> carregado() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        CargaInicial.carregar(new FakeCarregavel("sessions", sessoes), 10, 2,
                Instant.parse("2026-10-22T00:00:00Z"), 5, Duration.ofSeconds(1), Duration.ofMillis(1),
                () -> 0L, d -> { });
        return sessoes;
    }

    @Test
    @DisplayName("T9: Logout isolado invalida um sid carregado; o contador separa VALID, INVALID, NOT_FOUND e erro")
    void separaDesfechos() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = carregado();
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), eventos);
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.always());
        ops.definirDesfechosValidate(d);

        ops.logout("sid-000002");
        ops.validate("sid-000001");
        ops.validate("sid-000002");
        ops.validate("sid-000002");
        ops.validate("sid-999999");
        sessoes.antes = (op, chave) -> {
            if ("sid-000003".equals(chave)) {
                throw new org.infinispan.client.hotrod.exceptions.TransportException("caiu", (java.net.SocketAddress) null);
            }
        };
        ops.validate("sid-000003");

        assertEquals(1, d.valid());
        assertEquals(2, d.invalid());
        assertEquals(1, d.notFound());
        assertEquals(1, d.erro());
        assertEquals("Desfechos Validate: valid=1 invalid=2 not_found=1 erro=1", d.linha());

        // o contrato do CSV nao muda (C4 continua ate B-43.4): INVALID sai como VALID, NOT_FOUND como NONE
        OpsGravadas.Chamada invalida = ops.chamadas.get(2);
        assertEquals(SessionState.Estado.INVALID, invalida.r().estado());
        assertEquals("VALID", invalida.r().returnCode());
        assertEquals("NONE", ops.chamadas.get(4).r().returnCode());
    }

    @Test
    @DisplayName("T9: fora da janela de medicao (warm-up) nada e contado; sem contador o SessionOps segue igual")
    void soNaJanela() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = carregado();
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), null);
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.never());
        ops.definirDesfechosValidate(d);
        ops.validate("sid-000001");
        ops.validate("sid-999999");
        assertEquals(0, d.valid() + d.invalid() + d.notFound() + d.erro());

        OpsGravadas semContador = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), null);
        assertEquals(OpResult.Code.OK_VALIDATE, semContador.validate("sid-000001").code());
    }

    @Test
    @DisplayName("T9: a triade tambem conta (Validate do sid novo e VALID)")
    void triadeConta() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = carregado();
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), null);
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.always());
        ops.definirDesfechosValidate(d);
        WorkloadMain.executarOperacoes(ops, Scenario.S1, new java.util.Random(42), new KeyGenerator(10, 0.99, 42),
                new IdentidadesMistura(0, VersaoMistura.V2), 1000);
        long validates = ops.contar(SessionOps.OP_VALIDATE);
        assertEquals(validates, d.valid() + d.invalid() + d.notFound() + d.erro());
        assertEquals(ops.contar(SessionOps.OP_VALIDATE, OpResult.Code.NOT_FOUND), d.notFound());
    }
}
