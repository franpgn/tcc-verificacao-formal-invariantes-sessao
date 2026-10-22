package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V-B43.2, sobrevivente N9 (fechado em B-43.6): o T9 de B-43.2 so exercitava
 * {@code ERROR_TRANSPORT}; tirar {@code ERROR_UNAVAILABLE} do contador de erro passava. E justamente o
 * desfecho de particao (F3), entao o caso e travado aqui, pelo caminho real de {@link SessionOps}
 * (excecao de indisponibilidade classificada) e pelo contador isolado.
 */
class DesfechosValidateIndisponivelTest {

    private static HotRodClientException indisponivel() {
        return new HotRodClientException("org.infinispan.partitionhandling.AvailabilityException: "
                + "ISPN000306: Key 'sid-000001' is not available. Not all owners are in this partition");
    }

    @Test
    @DisplayName("N9: Validate recusado por indisponibilidade (ERROR_UNAVAILABLE) conta em erro, e so em erro")
    void indisponivelContaComoErro() {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        sessoes.dados.put("sid-000001", SessionState.criar("sid-000001", "u1", Instant.now()));
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), null);
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.always());
        ops.definirDesfechosValidate(d);

        sessoes.antes = (op, chave) -> {
            throw indisponivel();
        };
        OpResult r = ops.validate("sid-000001");

        assertEquals(OpResult.Code.ERROR_UNAVAILABLE, r.code());
        assertEquals(1, d.erro());
        assertEquals(0, d.valid() + d.invalid() + d.notFound());
        assertEquals("Desfechos Validate: valid=0 invalid=0 not_found=0 erro=1", d.linha());
    }

    @Test
    @DisplayName("N9: os dois codigos de erro contam em erro no contador isolado")
    void doisCodigosDeErro() {
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.always());
        d.registrar(OpResult.errorUnavailable());
        d.registrar(OpResult.errorTransport());
        d.registrar(OpResult.errorUnavailable());
        assertEquals(3, d.erro());
        assertEquals(0, d.valid() + d.invalid() + d.notFound());
    }
}
