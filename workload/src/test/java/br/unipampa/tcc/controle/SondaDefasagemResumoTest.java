package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3): partes puras do S_lag. {@link RodadaDefasagem#lagPorNo()} (menor lag dos pares de cada no),
 * {@code noSuspeito} unico ou {@code null}, {@link SondaDefasagem#resumir} (histerese, varias rodadas, janela sem
 * rodada = indeterminada), a linha {@code defasagem} do JSONL e as funcoes da agenda alinhada a grade.
 */
class SondaDefasagemResumoTest {

    static final long TETO = 2000;
    static final List<String> NOS = List.of("no0", "no1", "no2");
    /** Marca de par nao visto na matriz dos testes. */
    static final double NV = Double.NaN;

    /** Rodada a partir de uma matriz de lags (NaN = nao visto, lag = teto); diagonal ignorada. */
    static RodadaDefasagem rodada(long id, long tInicioNs, double[][] lag) {
        List<RodadaDefasagem.Par> pares = new ArrayList<>();
        for (int x = 0; x < NOS.size(); x++) {
            for (int y = 0; y < NOS.size(); y++) {
                if (x == y) {
                    continue;
                }
                boolean visto = !Double.isNaN(lag[x][y]);
                pares.add(new RodadaDefasagem.Par(NOS.get(x), NOS.get(y), visto, visto ? lag[x][y] : TETO,
                        visto ? 1 : 7, 0));
            }
        }
        List<RodadaDefasagem.Escrita> es = new ArrayList<>();
        for (String no : NOS) {
            es.add(new RodadaDefasagem.Escrita(no, true, 1.0, null));
        }
        return new RodadaDefasagem(id, tInicioNs, tInicioNs + 1, TETO, NOS, pares, es);
    }

    static final double[][] CALMA = {{0, 2, 3}, {2, 0, 4}, {3, 1, 0}};
    /** no2 cortado: nada que ele escreve chega, nada que ele le e novo. */
    static final double[][] NO2_CORTADO = {{0, 2, NV}, {3, 0, NV}, {NV, NV, 0}};

    @Test
    @DisplayName("lag do no = menor lag entre os pares em que ele escreve ou le")
    void lagDoNoEOMenorDosSeusPares() {
        RodadaDefasagem r = rodada(1, 0, CALMA);
        assertEquals(Map.of("no0", 2.0, "no1", 1.0, "no2", 1.0), r.lagPorNo());
        assertEquals(List.of("no0", "no1", "no2"), List.copyOf(r.lagPorNo().keySet()), "ordem do canal");
        assertEquals(2.0, r.lagMaxMs());
        assertEquals("no0", r.noSuspeito(), "maior lag de no, unico");
        assertEquals(4.0, r.lagMaxParMs());
        assertEquals(6, r.leituras());
    }

    @Test
    @DisplayName("no cortado: todos os seus pares no teto, suspeito = ele, lagMax = teto; os outros ficam baixos")
    void noCortado() {
        RodadaDefasagem r = rodada(1, 0, NO2_CORTADO);
        assertEquals((double) TETO, r.lagPorNo().get("no2"));
        assertEquals(2.0, r.lagPorNo().get("no0"));
        assertEquals(2.0, r.lagPorNo().get("no1"));
        assertEquals("no2", r.noSuspeito());
        assertEquals((double) TETO, r.lagMaxMs());
    }

    @Test
    @DisplayName("dono primario do outro lado: um par da maioria no teto nao muda o suspeito")
    void parDaMaioriaNoTetoNaoMudaOSuspeito() {
        double[][] m = {{0, NV, NV}, {3, 0, NV}, {NV, NV, 0}}; // no0 -> no1 nao viu (chave com dono no no2)
        RodadaDefasagem r = rodada(1, 0, m);
        assertEquals(3.0, r.lagPorNo().get("no0"), "no0 ainda viu o no1 escrever");
        assertEquals("no2", r.noSuspeito());
    }

    @Test
    @DisplayName("corte parcial entre no0 e no2: nenhum no no teto, nenhum suspeito no teto")
    void corteParcial() {
        double[][] m = {{0, 2, NV}, {2, 0, 3}, {NV, 3, 0}};
        RodadaDefasagem r = rodada(1, 0, m);
        assertTrue(r.lagMaxMs() < TETO, "lagMax " + r.lagMaxMs());
        assertEquals((double) TETO, r.lagMaxParMs(), "o par no teto fica so no registro");
    }

    @Test
    @DisplayName("todos os pares no teto (sonda sem contato, ou chaves com dono do outro lado): empate, suspeito null")
    void empateNoTeto() {
        double[][] m = {{0, NV, NV}, {NV, 0, NV}, {NV, NV, 0}};
        RodadaDefasagem r = rodada(1, 0, m);
        assertNull(r.noSuspeito());
        assertEquals((double) TETO, r.lagMaxMs());
        EstadoDefasagem e = SondaDefasagem.resumir(List.of(r), new ParametrosDefasagem(50, 20, 10));
        assertEquals(EstadoDefasagem.Nivel.ALTA, e.nivel());
        assertNull(e.noSuspeito(), "sem suspeito unico o P1 nao dispara");
    }

    @Test
    @DisplayName("resumir: histerese (>= lagIn alta, <= lagOut calma, entre os dois intermediaria), nas bordas")
    void histerese() {
        ParametrosDefasagem p = new ParametrosDefasagem(50, 20, 10);
        assertEquals(EstadoDefasagem.Nivel.ALTA, nivel(p, 50));
        assertEquals(EstadoDefasagem.Nivel.ALTA, nivel(p, 1999));
        assertEquals(EstadoDefasagem.Nivel.INTERMEDIARIA, nivel(p, 49.9));
        assertEquals(EstadoDefasagem.Nivel.INTERMEDIARIA, nivel(p, 20.1));
        assertEquals(EstadoDefasagem.Nivel.CALMA, nivel(p, 20));
        assertEquals(EstadoDefasagem.Nivel.CALMA, nivel(p, 0));
        EstadoDefasagem e = SondaDefasagem.resumir(List.of(rodada(1, 0, NO2_CORTADO)), p);
        assertEquals(new EstadoDefasagem(EstadoDefasagem.Nivel.ALTA, "no2", (double) TETO, 1), e);
        assertTrue(e.alta());
    }

    /** Nivel de uma rodada em que todo par tem o mesmo lag {@code v}. */
    static EstadoDefasagem.Nivel nivel(ParametrosDefasagem p, double v) {
        double[][] m = {{0, v, v}, {v, 0, v}, {v, v, 0}};
        return SondaDefasagem.resumir(List.of(rodada(1, 0, m)), p).nivel();
    }

    @Test
    @DisplayName("resumir: varias rodadas na janela = o maior lag de cada no; amostras = rodadas")
    void variasRodadas() {
        ParametrosDefasagem p = new ParametrosDefasagem(50, 20, 10);
        EstadoDefasagem e = SondaDefasagem.resumir(List.of(rodada(1, 0, CALMA), rodada(2, 10, NO2_CORTADO)), p);
        assertEquals("no2", e.noSuspeito());
        assertEquals((double) TETO, e.lagMaxMs());
        assertEquals(2, e.amostras());
    }

    @Test
    @DisplayName("resumir: janela sem rodada (amostra insuficiente) = indeterminada, amostras 0")
    void semRodadaIndeterminada() {
        ParametrosDefasagem p = new ParametrosDefasagem(50, 20, 10);
        assertSame(EstadoDefasagem.indeterminada(), SondaDefasagem.resumir(List.of(), p));
        assertSame(EstadoDefasagem.indeterminada(), SondaDefasagem.resumir(null, p));
        assertTrue(SondaDefasagem.resumir(List.of(), p).calmaOuIndeterminada());
        assertEquals(0, SondaDefasagem.resumir(List.of(), p).amostras());
    }

    @Test
    @DisplayName("RodadaDefasagem recusa pares faltando, repetidos, visto no teto, nao visto fora do teto, escritas fora de ordem")
    void rodadaInvalida() {
        RodadaDefasagem ok = rodada(1, 0, CALMA);
        List<RodadaDefasagem.Par> pares = new ArrayList<>(ok.pares());
        List<RodadaDefasagem.Par> faltando = pares.subList(0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> new RodadaDefasagem(1, 0, 1, TETO, NOS, faltando, ok.escritas()));
        List<RodadaDefasagem.Par> repetido = new ArrayList<>(pares.subList(0, 5));
        repetido.add(pares.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> new RodadaDefasagem(1, 0, 1, TETO, NOS, repetido, ok.escritas()));
        List<RodadaDefasagem.Par> vistoNoTeto = new ArrayList<>(pares);
        vistoNoTeto.set(0, new RodadaDefasagem.Par("no0", "no1", true, TETO, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RodadaDefasagem(1, 0, 1, TETO, NOS, vistoNoTeto, ok.escritas()));
        List<RodadaDefasagem.Par> naoVistoBaixo = new ArrayList<>(pares);
        naoVistoBaixo.set(0, new RodadaDefasagem.Par("no0", "no1", false, 3, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RodadaDefasagem(1, 0, 1, TETO, NOS, naoVistoBaixo, ok.escritas()));
        List<RodadaDefasagem.Escrita> fora = new ArrayList<>(ok.escritas());
        java.util.Collections.reverse(fora);
        assertThrows(IllegalArgumentException.class, () -> new RodadaDefasagem(1, 0, 1, TETO, NOS, pares, fora));
        assertThrows(IllegalArgumentException.class, () -> new RodadaDefasagem.Par("no0", "no0", true, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RodadaDefasagem.Par("no0", "no1", true, 1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new RodadaDefasagem.Escrita("no0", null, 1.0, null));
        assertThrows(IllegalArgumentException.class, () -> new RodadaDefasagem.Escrita("no0", true, 1.0, "X"));
    }

    @Test
    @DisplayName("linha defasagem (observaveis 1.2): campos em ordem, pares, escritas e custo")
    void linhaJsonl() {
        RodadaDefasagem r = rodada(7, 123, NO2_CORTADO);
        JsonNode o = FormatoJsonl.defasagem(r);
        assertEquals(List.of("tipo", "rodada", "t_inicio_ns", "t_ns", "teto_ms", "lag_max_ms", "no_suspeito",
                "lag_max_par_ms", "lag_por_no", "pares", "escritas", "custo"), LacoDeControleTest.campos(o));
        assertEquals("defasagem", o.get("tipo").asText());
        assertEquals(7, o.get("rodada").asLong());
        assertEquals(123, o.get("t_inicio_ns").asLong());
        assertEquals(TETO, o.get("teto_ms").asLong());
        assertEquals("no2", o.get("no_suspeito").asText());
        assertEquals(3, o.get("lag_por_no").size());
        assertEquals(6, o.get("pares").size());
        JsonNode p0 = o.get("pares").get(0);
        assertEquals(List.of("escritor", "leitor", "visto", "lag_ms", "leituras", "erros_leitura"),
                LacoDeControleTest.campos(p0));
        assertEquals(List.of("no", "ok", "duracao_ms", "erro"), LacoDeControleTest.campos(o.get("escritas").get(0)));
        JsonNode c = o.get("custo");
        assertEquals(3, c.get("escritas").asInt());
        assertEquals(r.leituras(), c.get("leituras").asInt());
        assertEquals(0, c.get("erros_escrita").asInt());
        JsonNode pendente = FormatoJsonl.defasagem(new RodadaDefasagem(1, 0, 1, TETO, NOS, r.pares(), List.of(
                new RodadaDefasagem.Escrita("no0", null, null, null), new RodadaDefasagem.Escrita("no1", false, 2.0, "X"),
                new RodadaDefasagem.Escrita("no2", true, 1.0, null)))).get("escritas");
        assertTrue(pendente.get(0).get("ok").isNull() && pendente.get(0).get("duracao_ms").isNull());
        assertFalse(pendente.get(1).get("ok").asBoolean());
    }

    @Test
    @DisplayName("agenda: indice da janela na grade (negativo antes da origem) e atraso maximo W - teto")
    void funcoesDaAgenda() {
        assertEquals(0, SondaDefasagem.indiceDaJanela(1000, 1000, 500));
        assertEquals(0, SondaDefasagem.indiceDaJanela(1499, 1000, 500));
        assertEquals(1, SondaDefasagem.indiceDaJanela(1500, 1000, 500));
        assertEquals(-1, SondaDefasagem.indiceDaJanela(999, 1000, 500));
        assertEquals(-1, SondaDefasagem.indiceDaJanela(500, 1000, 500));
        assertTrue(SondaDefasagem.cabeNaJanela(1000, 1000, 500, 200));
        assertTrue(SondaDefasagem.cabeNaJanela(1300, 1000, 500, 200));
        assertFalse(SondaDefasagem.cabeNaJanela(1301, 1000, 500, 200), "terminaria depois do fim da janela");
        assertFalse(SondaDefasagem.cabeNaJanela(999, 1000, 500, 200));
    }
}
