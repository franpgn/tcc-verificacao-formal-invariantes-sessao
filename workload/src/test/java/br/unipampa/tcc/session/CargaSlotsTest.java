package br.unipampa.tcc.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.7, T4 (parte da classe): {@link CargaSlots} grava 2 entradas por slot (geracao 0 INVALID por
 * LOGOUT, geracao 1 VALID), em lotes de 1000, e espera {@code size() == 2U} ate o prazo.
 */
class CargaSlotsTest {

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
        return CargaSlots.carregar(carregavel, universo, threads, T, CargaInicial.LOTE_PADRAO,
                Duration.ofSeconds(10), Duration.ofMillis(100), () -> agoraNs[0], sono);
    }

    @Test
    @DisplayName("T4: 2U entradas; geracao 0 INVALID(LOGOUT) e geracao 1 VALID em todo slot; size == 2U; lotes de 1000")
    void cargaPadrao() throws Exception {
        CargaInicial.Resultado r = carregar(U, 4);
        assertEquals(U, r.universo());
        assertEquals(200, r.lotes());
        assertEquals(Integer.valueOf(2 * U), r.tamanhoFinal());
        assertEquals(2 * U, sessoes.dados.size());
        assertTrue(carregavel.tamanhosDosLotes.stream().allMatch(n -> n == 1000), "lotes de 1000");
        assertEquals(1, carregavel.sizes, "SYNC: uma consulta basta");
        assertEquals(0, sonos);
        assertEquals(0, carregavel.clears);
        for (int k = 1; k <= U; k++) {
            SessionState g0 = sessoes.dados.get(SlotsGeracao.chave(k, 0));
            SessionState g1 = sessoes.dados.get(SlotsGeracao.chave(k, 1));
            String dona = IdentidadesMistura.identidadeDaCarga(k, 4);
            assertEquals(SessionState.Estado.INVALID, g0.estado(), "geracao 0 do slot " + k);
            assertEquals(SessionState.Motivo.LOGOUT, g0.motivo(), "geracao 0 do slot " + k);
            assertEquals(SessionState.Estado.VALID, g1.estado(), "geracao 1 do slot " + k);
            assertEquals(dona, g0.identidade());
            assertEquals(dona, g1.identidade());
            assertEquals(SlotsGeracao.chave(k, 0), g0.id());
            assertEquals(SlotsGeracao.chave(k, 1), g1.id());
        }
        assertFalse(sessoes.dados.containsKey("sid-000001~2"));
    }

    @Test
    @DisplayName("T4: chega na k-esima consulta (ASYNC) => OK, com sono entre as consultas")
    void chegaNaKesima() throws Exception {
        carregavel.completaNaConsulta = 4;
        CargaInicial.Resultado r = carregar(U, 4);
        assertEquals(Integer.valueOf(2 * U), r.tamanhoFinal());
        assertEquals(4, carregavel.sizes);
        assertEquals(3, sonos);
    }

    @Test
    @DisplayName("T4: nunca chega => CargaInicialException no prazo, saida 5")
    void nuncaChega() {
        carregavel.completaNaConsulta = Integer.MAX_VALUE;
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
        assertEquals(Integer.valueOf(2 * U - 1), e.carga().tamanhoFinal());
        assertTrue(e.getMessage().contains("tamanho diferente"), e.getMessage());
        assertEquals(Duration.ofSeconds(10).toNanos(), agoraNs[0], "espera exatamente o prazo");
    }

    @Test
    @DisplayName("T4: tamanho maior que 2U => falha imediata, sem sono")
    void tamanhoMaior() {
        carregavel.extra = 1;
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertEquals(0, sonos);
        assertEquals(1, carregavel.sizes);
        assertEquals(Integer.valueOf(2 * U + 1), e.carga().tamanhoFinal());
    }

    @Test
    @DisplayName("T4: o ultimo lote parcial e gravado (U = 750 => 1000 + 500)")
    void ultimoLoteParcial() throws Exception {
        CargaInicial.Resultado r = carregar(750, 4);
        assertEquals(List.of(1000, 500), carregavel.tamanhosDosLotes);
        assertEquals(2, r.lotes());
        assertEquals(1500, sessoes.dados.size());
        assertEquals(SessionState.Estado.VALID, sessoes.dados.get("sid-000750~1").estado());
        assertEquals(SessionState.Estado.INVALID, sessoes.dados.get("sid-000750").estado());
    }

    @Test
    @DisplayName("T4: putAll que lanca => CargaInicialException com a causa e tamanho nulo")
    void putAllLanca() {
        carregavel.falhaNoPutAll = new IllegalStateException("x");
        carregavel.falhaNoLote = 2;
        CargaInicialException e = assertThrows(CargaInicialException.class, () -> carregar(U, 4));
        assertSame(carregavel.falhaNoPutAll, e.getCause());
        assertNull(e.carga().tamanhoFinal());
        assertEquals(1, e.carga().lotes());
        assertEquals(5, WorkloadMain.codigoDeSaida(e));
    }
}
