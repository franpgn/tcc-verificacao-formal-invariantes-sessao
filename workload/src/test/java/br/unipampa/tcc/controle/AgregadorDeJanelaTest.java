package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.1: agregador de janelas tumbling por {@code end_ns} (plano de 2026-10-22, secao 3.2;
 * validacao V-B28.1, secao 6; regra C+A do Gestor para evento atrasado). Relogio falso: os tempos
 * sao os proprios {@code end_ns} dos eventos, em ns pequenos.
 */
class AgregadorDeJanelaTest {

    private static final Referencia REF = Referencia.de(Map.of("Login", 100L, "Validate", 50L));

    private static EventoOperacao ok(long fim) {
        return new EventoOperacao("Logout", fim, fim, "OK");
    }

    private static EventoOperacao ev(String op, long lat, long fim, String codigo) {
        return new EventoOperacao(op, fim - lat, fim, codigo);
    }

    /** Agregador com origem 0, W = 10 ns e tolerancia L. */
    private static AgregadorDeJanela agr(long l) {
        return new AgregadorDeJanela(0, 10, l, REF);
    }

    private static Janela vazia(long ini, long fim) {
        return new Janela(ini, fim, 0, 0, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ contiguidade e vazias

    @Test
    @DisplayName("intervalo sem eventos produz janelas vazias contiguas (contrato V-B28.1 secao 6)")
    void janelasVaziasContiguas() {
        AgregadorDeJanela a = agr(0);
        assertEquals(List.of(), a.registrar(ok(1)));
        List<JanelaObservada> fechadas = a.registrar(ok(35));
        assertEquals(3, fechadas.size());
        assertEquals(new Janela(0, 10, 1, 0, 0, 0, 0, 0, 0), fechadas.get(0).janela());
        assertEquals(vazia(10, 20), fechadas.get(1).janela());
        assertEquals(vazia(20, 30), fechadas.get(2).janela());
        for (int i = 1; i < fechadas.size(); i++) {
            assertEquals(fechadas.get(i - 1).janela().fimNs(), fechadas.get(i).janela().inicioNs());
        }
    }

    @Test
    @DisplayName("a primeira janela comeca na origem, mesmo que o primeiro evento venha depois")
    void primeiraJanelaNaOrigem() {
        AgregadorDeJanela a = new AgregadorDeJanela(1_000, 10, 0, REF);
        List<JanelaObservada> f = a.registrar(ok(1_025));
        assertEquals(List.of(vazia(1_000, 1_010), vazia(1_010, 1_020)),
                f.stream().map(JanelaObservada::janela).toList());
    }

    @Test
    @DisplayName("sequencia longa: janelas sempre contiguas, indice sem buraco nem repeticao")
    void contiguidadeEmSequenciaLonga() {
        AgregadorDeJanela a = agr(3);
        List<JanelaObservada> todas = new ArrayList<>();
        Random r = new Random(42);
        long t = 0;
        for (int i = 0; i < 2_000; i++) {
            t += r.nextInt(25); // saltos de ate 2,5 janelas
            todas.addAll(a.registrar(ok(t)));
        }
        todas.addAll(a.encerrar());
        assertTrue(todas.size() > 100);
        for (int i = 0; i < todas.size(); i++) {
            assertEquals(10L * i, todas.get(i).janela().inicioNs(), "janela " + i);
            assertEquals(10L * (i + 1), todas.get(i).janela().fimNs(), "janela " + i);
        }
    }

    // ------------------------------------------------------------------ fronteiras

    @Test
    @DisplayName("evento exatamente em fimNs pertence a janela seguinte e, com L = 0, fecha a anterior")
    void eventoNaBorda() {
        AgregadorDeJanela a = agr(0);
        a.registrar(ok(9));
        List<JanelaObservada> f = a.registrar(ok(10));
        assertEquals(1, f.size());
        assertEquals(new Janela(0, 10, 1, 0, 0, 0, 0, 0, 0), f.get(0).janela());
        assertEquals(List.of(), a.encerrar(), "[10,20) ainda parcial: nao e emitida");
        assertEquals(1, a.descartadosNoEncerramento());
    }

    @Test
    @DisplayName("evento em inicioNs pertence a janela; em fimNs - 1 tambem")
    void bordasInclusivaExclusiva() {
        AgregadorDeJanela a = agr(0);
        a.registrar(ok(0));
        a.registrar(ok(9));
        List<JanelaObservada> f = a.registrar(ok(20));
        assertEquals(2, f.get(0).janela().total());
        assertEquals(0, f.get(1).janela().total());
    }

    @Test
    @DisplayName("com L > 0 a janela so fecha quando a marca d'agua chega a fimNs + L")
    void toleranciaAdiaFechamento() {
        AgregadorDeJanela a = agr(5);
        a.registrar(ok(3));
        assertEquals(List.of(), a.registrar(ok(14)), "14 < 10 + 5: [0,10) segue aberta");
        assertEquals(List.of(), a.registrar(ok(8)), "dentro da tolerancia: conta em [0,10)");
        List<JanelaObservada> f = a.registrar(ok(15));
        assertEquals(1, f.size());
        assertEquals(new Janela(0, 10, 2, 0, 0, 0, 0, 0, 0), f.get(0).janela());
        assertEquals(0, f.get(0).atrasados());
        assertEquals(10, f.get(0).janela().fimNs(), "o tempo de decisao continua sendo fimNs, nao fimNs + L");
    }

    // ------------------------------------------------------------------ evento atrasado (regra C+A)

    @Test
    @DisplayName("evento atrasado alem da tolerancia: descartado, contado em atrasados, janela fechada intacta")
    void eventoAtrasado() {
        AgregadorDeJanela a = agr(2);
        a.registrar(ok(5));
        List<JanelaObservada> f1 = a.registrar(ok(12));
        assertEquals(1, f1.size());
        Janela fechada = f1.get(0).janela();
        assertEquals(1, fechada.total());

        assertEquals(List.of(), a.registrar(ok(9)), "atrasado nao reabre nem emite nada");
        assertEquals(1, a.atrasadosTotal());

        List<JanelaObservada> f2 = a.registrar(ok(22));
        assertEquals(1, f2.size());
        assertEquals(new Janela(10, 20, 1, 0, 0, 0, 0, 0, 0), f2.get(0).janela(), "o atrasado nao migra de janela");
        assertEquals(1, f2.get(0).atrasados(), "atrasados vai na proxima janela emitida (para o JSONL)");
        assertEquals(new Janela(0, 10, 1, 0, 0, 0, 0, 0, 0), fechada, "a janela fechada nunca muda");

        List<JanelaObservada> f3 = a.registrar(ok(32));
        assertEquals(0, f3.get(0).atrasados(), "o contador por janela zera a cada emissao");
        assertEquals(1, a.atrasadosTotal());
    }

    @Test
    @DisplayName("evento antes da origem conta como atrasado")
    void antesDaOrigem() {
        AgregadorDeJanela a = new AgregadorDeJanela(100, 10, 0, REF);
        assertEquals(List.of(), a.registrar(ok(99)));
        assertEquals(1, a.atrasadosTotal());
        assertEquals(1, a.registrar(ok(110)).get(0).atrasados());
    }

    @Test
    @DisplayName("violacao atrasada: descartada e contada a parte, sem sumir em silencio")
    void violacaoAtrasada() {
        AgregadorDeJanela a = agr(0);
        a.registrar(ok(15));
        assertEquals(List.of(), a.registrarViolacao(ClasseViolacao.SEGURANCA, 4));
        assertEquals(1, a.violacoesAtrasadasTotal());
        JanelaObservada j = a.registrar(ok(20)).get(0);
        assertEquals(0, j.janela().violacoesSeguranca());
        assertEquals(1, j.violacoesAtrasadas());
        assertEquals(0, j.atrasados());
    }

    // ------------------------------------------------------------------ excedencia e codigos

    @Test
    @DisplayName("excedencia estrita: latencia igual ao p99 nao excede, p99 + 1 excede; fora da referencia nao e elegivel")
    void excedenciaNoLimite() {
        AgregadorDeJanela a = agr(0);
        a.registrar(ev("Login", 100, 1, "OK"));        // = p99: elegivel, nao excede
        a.registrar(ev("Login", 101, 2, "OK"));        // > p99: excede
        a.registrar(ev("Validate", 50, 3, "VALID"));   // = p99
        a.registrar(ev("Validate", 51, 4, "VALID"));   // > p99
        a.registrar(ev("Validate", 0, 5, "NONE"));
        a.registrar(ev("Logout", 1_000_000, 6, "OK")); // fora da referencia
        Janela j = a.registrar(ok(10)).get(0).janela();
        assertEquals(6, j.total());
        assertEquals(5, j.elegiveisLat());
        assertEquals(2, j.excedencias());
    }

    @Test
    @DisplayName("erros = TRANSPORT + UNAVAILABLE; indisponiveis = UNAVAILABLE; demais codigos so no total")
    void contagemPorCodigo() {
        AgregadorDeJanela a = agr(0);
        for (String c : EventoOperacao.CODIGOS) {
            a.registrar(ev("Logout", 1, 5, c));
        }
        a.registrar(ev("Logout", 1, 5, "ERROR_UNAVAILABLE"));
        Janela j = a.registrar(ok(10)).get(0).janela();
        assertEquals(8, j.total());
        assertEquals(3, j.erros());
        assertEquals(2, j.indisponiveis());
    }

    // ------------------------------------------------------------------ violacoes

    @Test
    @DisplayName("I2 esperada nao entra em violacoesSeguranca; I2 fora do gatilho e I3/I5/I6 so registro")
    void violacoesSeparadas() {
        AgregadorDeJanela a = agr(0);
        a.registrarViolacao(ClasseViolacao.de("I1", null), 1);
        a.registrarViolacao(ClasseViolacao.de("I4", null), 2);
        a.registrarViolacao(ClasseViolacao.de("I2", "REAL"), 3);
        a.registrarViolacao(ClasseViolacao.de("I2", "ESPERADA-POR-DESENHO"), 4);
        a.registrarViolacao(ClasseViolacao.de("I2", "ESPERADA-POR-DESENHO"), 5);
        a.registrarViolacao(ClasseViolacao.de("I2", "INCERTA"), 6);
        a.registrarViolacao(ClasseViolacao.de("I2", null), 6);
        a.registrarViolacao(ClasseViolacao.de("I5", null), 7);
        a.registrarViolacao(ClasseViolacao.de("I3", null), 8);
        a.registrarViolacao(ClasseViolacao.de("I6", null), 8);
        a.registrarViolacao(ClasseViolacao.de("I2-PERDIDO", "REAL"), 9);
        JanelaObservada o = a.registrar(ok(10)).get(0);
        assertEquals(3, o.janela().violacoesSeguranca());
        assertEquals(2, o.janela().violacoesI2Esperadas());
        // contagens assimetricas (2 x 4): uma troca entre os dois contadores nao passa (V-B26.1, S12)
        assertEquals(2, o.violacoesI2ForaDoGatilho());
        assertEquals(4, o.violacoesForaDoGatilho());
        assertEquals(0, o.janela().total(), "violacao nao e evento de operacao (ok(10) e da janela seguinte)");
    }

    @Test
    @DisplayName("so I2 esperada na janela: violacoesSeguranca = 0")
    void soI2Esperada() {
        AgregadorDeJanela a = agr(0);
        a.registrarViolacao(ClasseViolacao.I2_ESPERADA, 3);
        Janela j = a.registrar(ok(10)).get(0).janela();
        assertEquals(0, j.violacoesSeguranca());
        assertEquals(1, j.violacoesI2Esperadas());
    }

    @Test
    @DisplayName("o end_ns da violacao avanca a marca d'agua como o de um evento")
    void violacaoAvancaMarca() {
        AgregadorDeJanela a = agr(0);
        a.registrar(ok(1));
        List<JanelaObservada> f = a.registrarViolacao(ClasseViolacao.SEGURANCA, 12);
        assertEquals(1, f.size());
        assertEquals(12, a.marcaDaguaNs());
        assertEquals(1, a.registrar(ok(20)).get(0).janela().violacoesSeguranca());
    }

    // ------------------------------------------------------------------ encerramento

    @Test
    @DisplayName("encerrar emite as janelas completas sem esperar L e descarta a parcial, contando")
    void encerrar() {
        AgregadorDeJanela a = agr(100);
        a.registrar(ok(2));
        a.registrar(ok(13));
        a.registrar(ok(25));
        a.registrarViolacao(ClasseViolacao.SEGURANCA, 26);
        List<JanelaObservada> f = a.encerrar();
        assertEquals(2, f.size(), "[0,10) e [10,20) estao completas (marca = 26)");
        assertEquals(1, f.get(0).janela().total());
        assertEquals(1, f.get(1).janela().total());
        assertEquals(1, a.descartadosNoEncerramento());
        assertEquals(1, a.violacoesDescartadasNoEncerramento());
        assertThrows(IllegalStateException.class, () -> a.registrar(ok(30)));
        assertThrows(IllegalStateException.class, () -> a.registrarViolacao(ClasseViolacao.SEGURANCA, 30));
        assertEquals(List.of(), a.encerrar(), "encerrar de novo nao emite nada");
    }

    @Test
    @DisplayName("encerrar conta no descarte as violacoes das 4 classes que estavam na parcial (V-B26.1, T2)")
    void encerrarDescartaTodasAsClasses() {
        AgregadorDeJanela a = agr(100);
        a.registrar(ok(12));
        for (ClasseViolacao c : ClasseViolacao.values()) {
            a.registrarViolacao(c, 15);
        }
        List<JanelaObservada> f = a.encerrar();
        assertEquals(1, f.size(), "so [0,10) esta completa (marca = 15)");
        assertEquals(1, a.descartadosNoEncerramento());
        assertEquals(4, a.violacoesDescartadasNoEncerramento());
    }

    @Test
    @DisplayName("a marca d'agua nunca decresce; encerrar apos evento fora de ordem usa a maior marca (V-B26.1, T4)")
    void marcaNaoDecresce() {
        AgregadorDeJanela a = agr(100);
        a.registrar(ok(30));
        a.registrar(ok(25));
        assertEquals(30, a.marcaDaguaNs());
        a.registrarViolacao(ClasseViolacao.FORA_DO_GATILHO, 21);
        assertEquals(30, a.marcaDaguaNs());
        List<JanelaObservada> f = a.encerrar();
        assertEquals(3, f.size(), "[0,10), [10,20) e [20,30) completas pela marca 30");
        assertEquals(30, f.get(2).janela().fimNs());
        assertEquals(1, f.get(2).janela().total(), "o 25 entra em [20,30); o 30 fica na parcial");
        assertEquals(1, a.descartadosNoEncerramento());
    }

    @Test
    @DisplayName("o contador de violacoes atrasadas zera na emissao seguinte; o total continua (V-B26.1, T1)")
    void violacoesAtrasadasZeramPorJanela() {
        AgregadorDeJanela a = agr(0);
        a.registrar(ok(15));
        a.registrarViolacao(ClasseViolacao.I2_ESPERADA, 4);
        assertEquals(1, a.registrar(ok(20)).get(0).violacoesAtrasadas());
        JanelaObservada segunda = a.registrar(ok(30)).get(0);
        assertEquals(0, segunda.violacoesAtrasadas());
        assertEquals(1, a.violacoesAtrasadasTotal());
    }

    // ------------------------------------------------------------------ determinismo e parametros

    @Test
    @DisplayName("determinismo: a mesma sequencia produz as mesmas janelas")
    void determinismo() {
        List<JanelaObservada> a = rodar(7);
        assertEquals(a, rodar(7));
        assertTrue(a.size() > 50);
        assertTrue(a.stream().mapToLong(JanelaObservada::atrasados).sum() > 0, "o roteiro exercita atrasados");
    }

    private static List<JanelaObservada> rodar(long semente) {
        Random r = new Random(semente);
        AgregadorDeJanela a = agr(4);
        List<JanelaObservada> out = new ArrayList<>();
        long t = 0;
        String[] ops = {"Login", "Validate", "Logout"};
        for (int i = 0; i < 5_000; i++) {
            t += r.nextInt(3);
            long fim = Math.max(0, t - r.nextInt(30)); // desordem de ate 30 ns, parte alem de L
            if (r.nextInt(50) == 0) {
                out.addAll(a.registrarViolacao(ClasseViolacao.values()[r.nextInt(4)], fim));
            } else {
                String cod = EventoOperacao.CODIGOS.get(r.nextInt(EventoOperacao.CODIGOS.size()));
                long lat = r.nextInt(150);
                out.addAll(a.registrar(new EventoOperacao(ops[r.nextInt(3)], fim - lat, fim, cod)));
            }
        }
        out.addAll(a.encerrar());
        return out;
    }

    @Test
    @DisplayName("construtor com ParametrosControlador usa wNs dos parametros e L padrao de 10 ms")
    void construtorComParametros() {
        ParametrosControlador p = ParametrosControlador.padrao();
        AgregadorDeJanela a = new AgregadorDeJanela(1_000, p, REF);
        assertEquals(p.wNs(), a.wNs());
        assertEquals(10_000_000L, AgregadorDeJanela.TOLERANCIA_PADRAO_NS);
        assertEquals(AgregadorDeJanela.TOLERANCIA_PADRAO_NS, a.toleranciaNs());
        assertEquals(1_000, a.origemNs());
    }

    @Test
    @DisplayName("rejeita W <= 0, L < 0 e argumentos nulos")
    void rejeitaParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new AgregadorDeJanela(0, 0, 0, REF));
        assertThrows(IllegalArgumentException.class, () -> new AgregadorDeJanela(0, -1, 0, REF));
        assertThrows(IllegalArgumentException.class, () -> new AgregadorDeJanela(0, 10, -1, REF));
        assertThrows(NullPointerException.class, () -> new AgregadorDeJanela(0, 10, 0, null));
        assertThrows(NullPointerException.class, () -> agr(0).registrar(null));
        assertThrows(NullPointerException.class, () -> agr(0).registrarViolacao(null, 1));
    }

    @Test
    @DisplayName("JanelaObservada rejeita contadores negativos e janela nula")
    void janelaObservadaValida() {
        assertThrows(NullPointerException.class, () -> new JanelaObservada(null, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new JanelaObservada(vazia(0, 1), -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new JanelaObservada(vazia(0, 1), 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new JanelaObservada(vazia(0, 1), 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new JanelaObservada(vazia(0, 1), 0, 0, 0, -1));
    }
}
