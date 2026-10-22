package br.unipampa.tcc.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.1, T1 a T4: {@link ResetEstado#executar} esvazia os dois caches do perfil e prova que estao
 * vazios, esperando {@code size()==0} ate o prazo. Sem cluster e sem dormir de verdade: relogio
 * falso em nanossegundos e {@link ResetEstado.Sono} que so avanca o relogio.
 */
class ResetEstadoTest {

    private static final Duration PRAZO = Duration.ofSeconds(10);
    private static final Duration INTERVALO = Duration.ofMillis(100);

    private FakeCaches.Cache<SessionState> sessoes;
    private FakeCaches.Cache<IdentityState> identidades;
    private FakeLimpavel limpSessoes;
    private FakeLimpavel limpIdentidades;

    /** Relogio falso (ns) e registro das esperas pedidas. */
    private final long[] agoraNs = {1_000_000_000L};
    private final List<Duration> esperas = new ArrayList<>();
    private final ResetEstado.Sono sono = d -> {
        esperas.add(d);
        agoraNs[0] += d.toNanos();
    };

    @BeforeEach
    void preparar() {
        sessoes = new FakeCaches.Cache<>();
        identidades = new FakeCaches.Cache<>();
        Instant t = Instant.parse("2026-10-22T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            String sid = "sid-" + i;
            sessoes.dados.put(sid, SessionState.criar(sid, "user-" + i, t));
        }
        identidades.dados.put("user-0",
                new IdentityState("user-0", 5L, IdentityState.Status.BLOCKED, 7L, t));
        limpSessoes = new FakeLimpavel("sessions", sessoes);
        limpIdentidades = new FakeLimpavel("counters", identidades);
    }

    private ResetEstado.Resultado executar() throws EstadoNaoLimpoException {
        return ResetEstado.executar(List.of(limpSessoes, limpIdentidades), PRAZO, INTERVALO,
                () -> agoraNs[0], sono);
    }

    @Test
    @DisplayName("T1: limpa os dois caches e a identidade bloqueada volta a fazer login")
    void limpaEDesbloqueia() throws Exception {
        SessionOps ops = new SessionOps(sessoes.remoto(), identidades.remoto(),
                new LatencyRegistry(), new InvariantAuditor(), null, null, "fake");
        assertEquals(OpResult.Code.BLOCKED, ops.login("user-0", "cred").code(), "pre-condicao: user-0 BLOCKED");

        ResetEstado.Resultado r = executar();

        assertEquals(List.of("sessions", "counters"), r.caches());
        assertEquals(List.of(10, 1), r.tamanhosAntes());
        assertEquals(List.of(0, 0), r.tamanhosDepois());
        assertEquals(0, sessoes.dados.size());
        assertEquals(0, identidades.dados.size());
        assertEquals(1, limpSessoes.clears);
        assertEquals(1, limpIdentidades.clears);
        assertEquals(List.of(), esperas, "caches sincronos: nenhuma espera");
        assertEquals(OpResult.Code.OK_LOGIN, ops.login("user-0", "cred").code());
    }

    @Test
    @DisplayName("T2: cache que ignora clear => EstadoNaoLimpoException no prazo, citando cache e tamanho")
    void cacheQueNaoEsvaziaEstouraOPrazo() {
        limpIdentidades.ignorarClear = true;
        long inicio = agoraNs[0];

        EstadoNaoLimpoException e = assertThrows(EstadoNaoLimpoException.class, this::executar);

        assertTrue(e.getMessage().contains("counters=1"), e.getMessage());
        assertTrue(e.getMessage().contains("prazo"), e.getMessage());
        assertTrue(agoraNs[0] - inicio >= PRAZO.toNanos(), "abortou antes do prazo");
        assertTrue(agoraNs[0] - inicio < PRAZO.toNanos() + INTERVALO.toNanos(), "passou do prazo");
        assertTrue(esperas.stream().allMatch(INTERVALO::equals), esperas.toString());
        assertEquals(PRAZO.toMillis() / INTERVALO.toMillis(), esperas.size());
        ResetEstado.Resultado parcial = e.resultado();
        assertEquals(List.of(10, 1), parcial.tamanhosAntes());
        assertEquals(List.of(0, 1), parcial.tamanhosDepois());
        assertEquals(PRAZO.toMillis(), parcial.duracaoMs());
        assertEquals(1, limpIdentidades.clears, "clear e chamado uma vez; depois so se consulta size()");
    }

    @Test
    @DisplayName("T3: cache ASYNC que so chega a 0 na k-esima consulta => OK com tamanhos_depois = 0")
    void cacheAsyncChegaAZeroDepoisDeEsperar() throws Exception {
        int k = 4;
        limpSessoes.aplicaNaConsulta = k;

        ResetEstado.Resultado r = executar();

        assertEquals(List.of(10, 1), r.tamanhosAntes());
        assertEquals(List.of(0, 0), r.tamanhosDepois());
        assertEquals(0, sessoes.dados.size());
        assertEquals(k - 1, esperas.size(), "uma espera entre consultas ate a k-esima");
        assertEquals((k - 1) * INTERVALO.toMillis(), r.duracaoMs());
        assertEquals(1 + k, limpSessoes.sizes, "1 leitura antes + k consultas depois do clear");
    }

    @Test
    @DisplayName("T4: clear() que lanca => EstadoNaoLimpoException com a causa preservada")
    void clearQueLancaPreservaCausa() {
        RuntimeException causa = new IllegalStateException("AvailabilityException simulada: DEGRADED_MODE");
        limpSessoes.falhaNoClear = causa;

        EstadoNaoLimpoException e = assertThrows(EstadoNaoLimpoException.class, this::executar);

        assertSame(causa, e.getCause());
        assertTrue(e.getMessage().contains("sessions"), e.getMessage());
        assertEquals(Arrays.asList(10, 1), e.resultado().tamanhosAntes());
        assertEquals(Arrays.asList(null, null), e.resultado().tamanhosDepois());
        assertEquals(List.of(), esperas);
    }

    @Test
    @DisplayName("T4b: size() que lanca ja na leitura inicial => EstadoNaoLimpoException com a causa")
    void sizeQueLancaPreservaCausa() {
        RuntimeException causa = new IllegalStateException("AvailabilityException simulada");
        limpIdentidades.falhaNoSize = causa;

        EstadoNaoLimpoException e = assertThrows(EstadoNaoLimpoException.class, this::executar);

        assertSame(causa, e.getCause());
        assertTrue(e.getMessage().contains("counters"), e.getMessage());
        assertEquals(Arrays.asList(10, null), e.resultado().tamanhosAntes());
        assertEquals(0, limpSessoes.clears, "sem tamanho inicial completo, nada e limpo");
    }

    @Test
    @DisplayName("prazo e intervalo invalidos sao rejeitados")
    void parametrosInvalidos() {
        List<CacheLimpavel> cs = List.of(limpSessoes);
        assertThrows(IllegalArgumentException.class,
                () -> ResetEstado.executar(cs, Duration.ZERO, INTERVALO, () -> 0L, sono));
        assertThrows(IllegalArgumentException.class,
                () -> ResetEstado.executar(cs, PRAZO, Duration.ZERO, () -> 0L, sono));
        assertThrows(IllegalArgumentException.class,
                () -> ResetEstado.executar(List.of(), PRAZO, INTERVALO, () -> 0L, sono));
    }
}
