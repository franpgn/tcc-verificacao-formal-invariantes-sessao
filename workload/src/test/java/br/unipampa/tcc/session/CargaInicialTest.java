package br.unipampa.tcc.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.2, T3 e T5 (parte da classe): carga inicial de {@code sid-000001..sid-<universo>} como
 * sessoes VALID, em lotes de 1000, com verificacao de {@code size() == universo} ate o prazo; falha
 * vira {@link CargaInicialException} (saida 5 pelo {@code codigoDeSaida} existente).
 */
class CargaInicialTest {

    private static final Instant T = Instant.parse("2026-10-22T00:00:00Z");
    private static final int U = KeyGenerator.TAMANHO_PADRAO_UNIVERSO;

    private FakeCaches.Cache<SessionState> sessoes;
    private FakeCarregavel carregavel;
    private final long[] agoraNs = {0L};
    private int sonos;
    private final ResetEstado.Sono sono = d -> {
        sonos++;
        agoraNs[0] += d.toNanos();
    };

    @BeforeEach
    void preparar() {
        sessoes = new FakeCaches.Cache<>();
        carregavel = new FakeCarregavel("sessions", sessoes);
    }

    private CargaInicial.Resultado carregar(int universo, int threads) throws CargaInicialException {
        return CargaInicial.carregar(carregavel, universo, threads, T, CargaInicial.LOTE_PADRAO,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> agoraNs[0], sono);
    }

    @Test
    @DisplayName("T3: carga do universo padrao; sid-000001 VALID; size == universo; lotes de 1000")
    void cargaPadrao() throws Exception {
        CargaInicial.Resultado r = carregar(U, 4);
        assertEquals(U, r.universo());
        assertEquals(100, r.lotes());
        assertEquals(Integer.valueOf(U), r.tamanhoFinal());
        assertEquals(U, sessoes.dados.size());
        assertEquals(100, carregavel.putAlls);
        assertTrue(carregavel.tamanhosDosLotes.stream().allMatch(n -> n == 1000), "lotes de 1000");
        assertEquals(1, carregavel.sizes, "SYNC: uma consulta basta");
        assertEquals(0, sonos);
        assertEquals(0, carregavel.clears, "a carga nao limpa (o reset de B-43.1 ja limpou)");

        SessionState s1 = sessoes.dados.get("sid-000001");
        assertEquals(SessionState.Estado.VALID, s1.estado());
        assertEquals("user-0-0", s1.identidade());
        assertEquals("sid-000001", s1.id());
        assertTrue(sessoes.dados.containsKey("sid-100000"));
        assertFalse(sessoes.dados.containsKey("sid-000000"));
        assertFalse(sessoes.dados.containsKey("sid-100001"));
        assertEquals("user-3-0", sessoes.dados.get("sid-000004").identidade());
        assertEquals("user-0-1", sessoes.dados.get("sid-000005").identidade());
    }

    @Test
    @DisplayName("T3: validate(sid-000001) depois da carga => OK_VALIDATE com VALID")
    void validateDepoisDaCarga() throws Exception {
        carregar(U, 4);
        SessionOps ops = new SessionOps(sessoes, new FakeCaches.Cache<IdentityState>(),
                new LatencyRegistry(), new InvariantAuditor(), null, null, "fake");
        OpResult r = ops.validate("sid-000001");
        assertEquals(OpResult.Code.OK_VALIDATE, r.code());
        assertEquals(SessionState.Estado.VALID, r.estado());
    }

    @Test
    @DisplayName("T3: as 10^4 primeiras chaves de KeyGenerator(42) estao todas no conjunto carregado")
    void chavesZipfCarregadas() throws Exception {
        carregar(U, 4);
        KeyGenerator k = new KeyGenerator(42);
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String sid = k.nextSessionKey();
            assertTrue(sessoes.dados.containsKey(sid), "chave fora da carga: " + sid);
            vistas.add(sid);
        }
        assertTrue(vistas.contains("sid-000001"), "a chave mais quente (1) aparece");
        assertEquals(U, k.universo());
    }

    @Test
    @DisplayName("T3: universo vem de KeyGenerator.TAMANHO_PADRAO_UNIVERSO na sobrecarga de producao")
    void universoPadrao() throws Exception {
        CargaInicial.Resultado r = CargaInicial.carregar(carregavel, 2, T);
        assertEquals(KeyGenerator.TAMANHO_PADRAO_UNIVERSO, r.universo());
        assertEquals(KeyGenerator.TAMANHO_PADRAO_UNIVERSO, sessoes.dados.size());
        assertEquals(Duration.ofSeconds(10), CargaInicial.PRAZO_PADRAO);
        assertEquals(Duration.ofMillis(100), CargaInicial.INTERVALO_PADRAO);
        assertEquals(1000, CargaInicial.LOTE_PADRAO);
    }

    @Test
    @DisplayName("lote parcial no fim (universo nao multiplo do lote)")
    void loteParcial() throws Exception {
        CargaInicial.Resultado r = CargaInicial.carregar(carregavel, 2500, 2, T, 1000,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> agoraNs[0], sono);
        assertEquals(3, r.lotes());
        assertEquals(java.util.List.of(1000, 1000, 500), carregavel.tamanhosDosLotes);
        assertEquals("sid-002500", sessoes.dados.keySet().stream().max(String::compareTo).orElseThrow());
    }

    @Test
    @DisplayName("T5: ASYNC que so chega ao universo na 4a consulta => OK depois de 3 esperas")
    void asyncChegaNaKesima() throws Exception {
        carregavel.completaNaConsulta = 4;
        CargaInicial.Resultado r = carregar(U, 4);
        assertEquals(Integer.valueOf(U), r.tamanhoFinal());
        assertEquals(4, carregavel.sizes);
        assertEquals(3, sonos);
        assertEquals(300, r.duracaoMs());
    }

    @Test
    @DisplayName("T5: ASYNC que nunca chega => CargaInicialException no prazo do relogio falso, saida 5")
    void asyncNuncaChega() {
        carregavel.completaNaConsulta = Integer.MAX_VALUE;
        carregavel.faltando = 7;
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertTrue(e.getMessage().contains("prazo"), e.getMessage());
        assertTrue(e.getMessage().contains(String.valueOf(U - 7)), e.getMessage());
        assertEquals(Integer.valueOf(U - 7), e.carga().tamanhoFinal());
        assertEquals(100, e.carga().lotes());
        assertEquals(10_000, e.carga().duracaoMs());
        assertEquals(100, sonos, "10 s / 100 ms");
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
        assertTrue(e instanceof EstadoNaoLimpoException);
    }

    @Test
    @DisplayName("T5: tamanho maior que o universo (estado sujo) => falha imediata, sem esperar o prazo")
    void tamanhoMaior() {
        carregavel.extra = 3;
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertTrue(e.getMessage().contains("diferente"), e.getMessage());
        assertEquals(Integer.valueOf(U + 3), e.carga().tamanhoFinal());
        assertEquals(0, sonos);
    }

    @Test
    @DisplayName("T5: putAll que lanca => causa preservada, lotes parciais, saida 5")
    void putAllLanca() {
        carregavel.falhaNoPutAll = new IllegalStateException("DEGRADED_MODE");
        carregavel.falhaNoLote = 3;
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertSame(carregavel.falhaNoPutAll, e.getCause());
        assertEquals(2, e.carga().lotes());
        assertNull(e.carga().tamanhoFinal());
        assertTrue(e.getMessage().contains("putAll"), e.getMessage());
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
    }

    @Test
    @DisplayName("T5: size que lanca => causa preservada")
    void sizeLanca() {
        carregavel.falhaNoSize = new IllegalStateException("AvailabilityException");
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertSame(carregavel.falhaNoSize, e.getCause());
        assertEquals(100, e.carga().lotes());
        assertNull(e.carga().tamanhoFinal());
    }

    @Test
    @DisplayName("parametros invalidos => IllegalArgumentException")
    void parametros() {
        assertThrows(IllegalArgumentException.class, () -> carregar(0, 4));
        assertThrows(IllegalArgumentException.class, () -> carregar(10, 0));
        assertThrows(IllegalArgumentException.class, () -> CargaInicial.carregar(carregavel, 10, 1, T, 0,
                Duration.ofSeconds(1), Duration.ofMillis(1), () -> 0L, sono));
        assertThrows(IllegalArgumentException.class, () -> CargaInicial.carregar(carregavel, 10, 1, T, 5,
                Duration.ZERO, Duration.ofMillis(1), () -> 0L, sono));
        assertThrows(IllegalArgumentException.class, () -> CargaInicial.carregar(carregavel, 10, 1, T, 5,
                Duration.ofSeconds(1), Duration.ZERO, () -> 0L, sono));
    }
}
