package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.4: contrato v2 da coluna {@code return_code} do CSV de eventos. {@code OK_VALIDATE} grava o
 * estado real da sessao ({@code VALID} ou {@code INVALID}) e {@code NOT_FOUND} grava
 * {@code NOT_FOUND} (no contrato 1 eram {@code VALID} e {@code NONE}, o mascaramento C4).
 * Sem cluster: {@link FakeCaches}.
 */
class ContratoCsvV2Test {

    // ---------------------------------------------------------------- R1

    @Test
    @DisplayName("R1: tabela completa do contrato v2 (OpResult.Code x payload -> return_code)")
    void tabelaCompleta() {
        Map<OpResult.Code, List<Object[]>> casos = new EnumMap<>(OpResult.Code.class);
        casos.put(OpResult.Code.OK_LOGIN, List.<Object[]>of(new Object[] {OpResult.okLogin("s"), "OK"}));
        casos.put(OpResult.Code.OK_VALIDATE, List.of(
                new Object[] {OpResult.okValidate(SessionState.Estado.VALID), "VALID"},
                new Object[] {OpResult.okValidate(SessionState.Estado.INVALID), "INVALID"}));
        casos.put(OpResult.Code.OK_STATUS, List.of(
                new Object[] {OpResult.okStatus(IdentityState.Status.OPEN), "COUNTED"},
                new Object[] {OpResult.okStatus(IdentityState.Status.BLOCKED), "COUNTED"}));
        casos.put(OpResult.Code.OK_NOOP, List.<Object[]>of(new Object[] {OpResult.okNoop(), "NONE"}));
        casos.put(OpResult.Code.BLOCKED, List.<Object[]>of(new Object[] {OpResult.blocked(), "BLOCKED"}));
        casos.put(OpResult.Code.NOT_FOUND, List.<Object[]>of(new Object[] {OpResult.notFound(), "NOT_FOUND"}));
        casos.put(OpResult.Code.ERROR_TRANSPORT,
                List.<Object[]>of(new Object[] {OpResult.errorTransport(), "ERROR_TRANSPORT"}));
        casos.put(OpResult.Code.ERROR_UNAVAILABLE,
                List.<Object[]>of(new Object[] {OpResult.errorUnavailable(), "ERROR_UNAVAILABLE"}));

        // a tabela cobre todos os codigos: um codigo novo sem linha aqui falha
        assertEquals(OpResult.Code.values().length, casos.size());
        casos.forEach((code, linhas) -> linhas.forEach(l -> {
            OpResult r = (OpResult) l[0];
            assertEquals(code, r.code());
            assertEquals(l[1], r.returnCode(), code + " / " + r.estado());
        }));
    }

    // ---------------------------------------------------------------- R2

    @Test
    @DisplayName("R2: logout e depois validate do mesmo sid grava INVALID; chave ausente grava NOT_FOUND")
    void sessionOpsGravaEstadoReal() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        sessoes.dados.put("sid-a", SessionState.criar("sid-a", "u1", Instant.now()));
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), eventos);

        ops.validate("sid-a");
        ops.logout("sid-a");
        ops.validate("sid-a");
        ops.validate("sid-ausente");
        ops.logout("sid-ausente");

        List<String[]> csv = linhas(eventos);
        assertEquals(5, csv.size());
        assertLinha(csv.get(0), SessionOps.OP_VALIDATE, "VALID", "sid-a");
        assertLinha(csv.get(1), SessionOps.OP_LOGOUT, "NONE", "sid-a");
        assertLinha(csv.get(2), SessionOps.OP_VALIDATE, "INVALID", "sid-a");
        assertLinha(csv.get(3), SessionOps.OP_VALIDATE, "NOT_FOUND", "sid-ausente");
        assertLinha(csv.get(4), SessionOps.OP_LOGOUT, "NOT_FOUND", "sid-ausente");
    }

    @Test
    @DisplayName("R2: ResetFailures e Unblock de identidade ausente tambem gravam NOT_FOUND (P2 = opcao (a))")
    void identidadeAusenteGravaNotFound() throws Exception {
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        SessionOps ops = new SessionOps(new FakeCaches.Cache<SessionState>().remoto(),
                new FakeCaches.Cache<IdentityState>().remoto(), new LatencyRegistry(),
                new InvariantAuditor(), eventos, EpochClock.capturarAgora(), "fake");
        assertEquals(OpResult.Code.NOT_FOUND, ops.resetFailures("ninguem").code());
        assertEquals(OpResult.Code.NOT_FOUND, ops.unblock("ninguem").code());
        List<String[]> csv = linhas(eventos);
        assertLinha(csv.get(0), SessionOps.OP_RESET_FAILURES, "NOT_FOUND", "ninguem");
        assertLinha(csv.get(1), SessionOps.OP_UNBLOCK, "NOT_FOUND", "ninguem");
    }

    // ---------------------------------------------------------------- R5

    @Test
    @DisplayName("R5: contador DesfechosValidate e CSV concordam linha a linha, inclusive ERROR_UNAVAILABLE em erro (N9)")
    void contadorECsvConcordam() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = carregado();
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), eventos);
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.always());
        ops.definirDesfechosValidate(d);

        ops.logout("sid-000002");
        ops.validate("sid-000001");
        ops.validate("sid-000002");
        ops.validate("sid-999999");
        sessoes.antes = (op, chave) -> {
            if ("sid-000003".equals(chave)) {
                throw new TransportException("caiu", (SocketAddress) null);
            }
            if ("sid-000004".equals(chave)) {
                throw new HotRodClientException(
                        "org.infinispan.partitionhandling.AvailabilityException: ISPN000306: degraded mode");
            }
        };
        ops.validate("sid-000003");
        ops.validate("sid-000004");

        assertEquals("Desfechos Validate: valid=1 invalid=1 not_found=1 erro=2", d.linha());
        List<String[]> csv = linhas(eventos);
        List<String> rcValidate = new ArrayList<>();
        for (String[] c : csv) {
            if (SessionOps.OP_VALIDATE.equals(c[1])) {
                rcValidate.add(c[5]);
            }
        }
        assertEquals(List.of("VALID", "INVALID", "NOT_FOUND", "ERROR_TRANSPORT", "ERROR_UNAVAILABLE"),
                rcValidate);
        // linha a linha: o codigo gravado e o do OpResult devolvido
        int i = 0;
        for (OpsGravadas.Chamada c : ops.chamadas) {
            if (SessionOps.OP_VALIDATE.equals(c.op())) {
                assertEquals(c.r().returnCode(), rcValidate.get(i++));
            }
        }
        assertConcorda(d, csv);
    }

    @Test
    @DisplayName("R5: numa mistura v2 sobre fakes, valid = Validate VALID e invalid = Validate INVALID no CSV")
    void misturaV2ContadorIgualAoCsv() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = carregado();
        EventCsvWriter eventos = new EventCsvWriter(WarmupPolicy.always());
        OpsGravadas ops = new OpsGravadas(sessoes, new FakeCaches.Cache<>(), new LatencyRegistry(), eventos);
        DesfechosValidate d = new DesfechosValidate(WarmupPolicy.always());
        ops.definirDesfechosValidate(d);
        WorkloadMain.executarOperacoes(ops, Scenario.S1, new Random(42), new KeyGenerator(10, 0.99, 42),
                new IdentidadesMistura(0, VersaoMistura.V2), 2000);
        assertTrue(d.invalid() > 0, "a mistura v2 sobre 10 chaves precisa produzir INVALID");
        assertTrue(d.valid() > 0);
        assertConcorda(d, linhas(eventos));
    }

    // ---------------------------------------------------------------- apoio

    private static void assertConcorda(DesfechosValidate d, List<String[]> csv) {
        long valid = 0, invalid = 0, notFound = 0, erro = 0;
        for (String[] c : csv) {
            if (!SessionOps.OP_VALIDATE.equals(c[1])) {
                continue;
            }
            switch (c[5]) {
                case "VALID" -> valid++;
                case "INVALID" -> invalid++;
                case "NOT_FOUND" -> notFound++;
                default -> {
                    if (c[5].startsWith("ERROR_")) {
                        erro++;
                    }
                }
            }
        }
        assertEquals(d.valid(), valid, "valid = Validate VALID");
        assertEquals(d.invalid(), invalid, "invalid = Validate INVALID");
        assertEquals(d.notFound(), notFound, "not_found = Validate NOT_FOUND");
        assertEquals(d.erro(), erro, "erro = Validate ERROR_*");
    }

    private static void assertLinha(String[] c, String op, String rc, String chave) {
        assertEquals(op, c[1]);
        assertEquals(rc, c[5]);
        assertEquals(chave, c[6]);
    }

    private static FakeCaches.Cache<SessionState> carregado() throws Exception {
        FakeCaches.Cache<SessionState> sessoes = new FakeCaches.Cache<>();
        CargaInicial.carregar(new FakeCarregavel("sessions", sessoes), 10, 2,
                Instant.parse("2026-10-22T00:00:00Z"), 5, Duration.ofSeconds(1), Duration.ofMillis(1),
                () -> 0L, x -> { });
        return sessoes;
    }

    private static List<String[]> linhas(EventCsvWriter eventos) throws Exception {
        Path tmp = Files.createTempFile("b434", ".csv");
        try {
            eventos.flush(tmp);
            List<String> l = Files.readAllLines(tmp);
            assertEquals(EventCsvWriter.HEADER, l.get(0));
            List<String[]> out = new ArrayList<>();
            for (String s : l.subList(1, l.size())) {
                out.add(s.split(",", -1));
            }
            return out;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
