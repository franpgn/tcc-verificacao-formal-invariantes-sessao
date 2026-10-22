package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.1: leitura do CSV de eventos e agregacao de uma amostra real.
 *
 * <p>Fixture {@code controle/amostra-s1-f1-rep001-200.csv}: cabecalho + as 200 primeiras linhas de
 * {@code runs/S1-F1/rep-001.csv} (fora do git). Serve <b>so como fixture de formato</b>: nessa
 * execucao 100% dos Login sao BLOCKED (achado de diagnostico) e nao ha OK, VALID nem ERROR_*.
 * Os valores esperados das janelas vieram de um calculo independente em Python sobre o mesmo
 * arquivo (relatorio B-26.1, secao 3).
 */
class LeitorCsvEventosTest {

    private static final String FIXTURE = "/controle/amostra-s1-f1-rep001-200.csv";

    private static BufferedReader fixture() {
        InputStream in = LeitorCsvEventosTest.class.getResourceAsStream(FIXTURE);
        assertNotNull(in, "fixture ausente: " + FIXTURE);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("amostra real: 200 eventos, operacoes e codigos esperados")
    void leAmostraReal() throws IOException {
        List<EventoOperacao> eventos = new ArrayList<>();
        try (BufferedReader r = fixture()) {
            assertEquals(200, LeitorCsvEventos.ler(r, eventos::add));
        }
        assertEquals(200, eventos.size());
        Map<String, Integer> porCodigo = new TreeMap<>();
        Map<String, Integer> porOp = new TreeMap<>();
        for (EventoOperacao e : eventos) {
            porCodigo.merge(e.codigoRetorno(), 1, Integer::sum);
            porOp.merge(e.operacao(), 1, Integer::sum);
        }
        assertEquals(Map.of("NONE", 135, "BLOCKED", 50, "COUNTED", 15), porCodigo);
        assertEquals(Map.of("Validate", 104, "Login", 50, "Logout", 24, "IncrementFailure", 15,
                "ResetFailures", 7), porOp);
        EventoOperacao primeiro = eventos.get(0);
        assertEquals("Validate", primeiro.operacao());
        assertEquals(1782175472158974500L, primeiro.inicioNs());
        assertEquals(1782175472167679000L, primeiro.fimNs());
        assertTrue(eventos.stream().allMatch(e -> e.latenciaNs() >= 296_500L && e.latenciaNs() <= 9_153_000L));
    }

    @Test
    @DisplayName("amostra real agregada em janelas de 5 ms com L = 10 ms bate com o calculo independente")
    void agregaAmostraReal() throws IOException {
        long origem = 1782175472165000000L;
        AgregadorDeJanela a = new AgregadorDeJanela(origem, 5_000_000L, AgregadorDeJanela.TOLERANCIA_PADRAO_NS,
                Referencia.de(Map.of("Validate", 1_000_000L, "Login", 2_000_000L)));
        List<JanelaObservada> out = new ArrayList<>();
        try (BufferedReader r = fixture()) {
            LeitorCsvEventos.ler(r, e -> out.addAll(a.registrar(e)));
        }
        assertEquals(2, out.size(), "antes de encerrar: so as janelas com fim + L <= marca");
        out.addAll(a.encerrar());
        long[][] esperado = { // total, elegiveis, excedencias
                {20, 18, 9}, {46, 32, 2}, {60, 46, 0}, {58, 45, 1}};
        assertEquals(esperado.length, out.size());
        for (int i = 0; i < esperado.length; i++) {
            Janela j = out.get(i).janela();
            assertEquals(origem + i * 5_000_000L, j.inicioNs());
            assertEquals(esperado[i][0], j.total(), "total w" + i);
            assertEquals(esperado[i][1], j.elegiveisLat(), "elegiveis w" + i);
            assertEquals(esperado[i][2], j.excedencias(), "excedencias w" + i);
            assertEquals(0, j.erros());
        }
        assertEquals(0, a.atrasadosTotal());
        assertEquals(16, a.descartadosNoEncerramento(), "janela parcial [185 ms, 190 ms)");
        assertEquals(200, out.stream().mapToLong(o -> o.janela().total()).sum() + a.descartadosNoEncerramento());
    }

    @Test
    @DisplayName("colunas achadas pelo nome; coluna phase extra e linha em branco sao toleradas")
    void colunasPorNome() throws IOException {
        String csv = "return_code,end_ns,operation,start_ns,phase\nOK,20,Login,10,antes\n\nERROR_UNAVAILABLE,30,Validate,25,durante\n";
        List<EventoOperacao> ev = new ArrayList<>();
        assertEquals(2, LeitorCsvEventos.ler(new BufferedReader(new StringReader(csv)), ev::add));
        assertEquals(new EventoOperacao("Login", 10, 20, "OK"), ev.get(0));
        assertEquals(new EventoOperacao("Validate", 25, 30, "ERROR_UNAVAILABLE"), ev.get(1));
    }

    @Test
    @DisplayName("rejeita CSV vazio, cabecalho incompleto, campos a menos, numero invalido e codigo fora do contrato")
    void rejeitaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> ler(""));
        assertThrows(IllegalArgumentException.class, () -> ler("operation,start_ns,end_ns\nLogin,1,2\n"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ler("operation,start_ns,end_ns,return_code\nLogin,1,2,OK\nLogin,1,2\n"));
        assertTrue(e.getMessage().startsWith("linha 3"), e.getMessage());
        // campos a mais tambem (V-B26.1, T5)
        IllegalArgumentException mais = assertThrows(IllegalArgumentException.class,
                () -> ler("operation,start_ns,end_ns,return_code\nLogin,1,2,OK\nLogin,1,2,OK,extra\n"));
        assertTrue(mais.getMessage().startsWith("linha 3: 5 campos"), mais.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ler("operation,start_ns,end_ns,return_code\nLogin,x,2,OK\n"));
        assertThrows(IllegalArgumentException.class, () -> ler("operation,start_ns,end_ns,return_code\nLogin,1,2,FOO\n"));
    }

    private static long ler(String csv) throws IOException {
        return LeitorCsvEventos.ler(new BufferedReader(new StringReader(csv)), e -> { });
    }
}
