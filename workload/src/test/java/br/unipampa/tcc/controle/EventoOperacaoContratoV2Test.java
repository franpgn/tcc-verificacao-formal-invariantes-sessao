package br.unipampa.tcc.controle;

import br.unipampa.tcc.session.IdentityState;
import br.unipampa.tcc.session.OpResult;
import br.unipampa.tcc.session.SessionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integracao passo 6 (V-B43.4, achado A1 da secao 5.2): o leitor do controlador aceita o contrato
 * CSV v2 do B-43.4 ({@code INVALID} e {@code NOT_FOUND}).
 *
 * <p>Este teste fica no pacote de teste; a pureza do nucleo ({@link ArquiteturaControleTest}) vale so
 * para as fontes principais de {@code controle}, que continuam sem importar {@code session}.
 *
 * <p>Fixture {@code controle/amostra-b43-4-q1-rep001-200-v2.csv}: cabecalho + as 200 primeiras linhas
 * de {@code runs/b43-4-smoke/Q1/rep-001.csv} do B-43.4 (S1 sync v2, contrato 2; sha256 do arquivo
 * inteiro {@code 3680771b...cf20}, conferido contra o {@code MANIFEST.sha256} do smoke). O arquivo
 * completo nao tem {@code NOT_FOUND}; esse codigo entra por uma linha sintetica.
 */
class EventoOperacaoContratoV2Test {

    private static final String FIXTURE = "/controle/amostra-b43-4-q1-rep001-200-v2.csv";

    /**
     * Todos os {@link OpResult} distintos do ponto de vista de {@code returnCode()}: cada {@link OpResult.Code}
     * com todos os valores de payload que mudam o literal. O {@code switch} e exaustivo: um {@code Code}
     * novo nao compila sem uma linha aqui.
     */
    static List<OpResult> todosOsResultados() {
        List<OpResult> r = new ArrayList<>();
        for (OpResult.Code c : OpResult.Code.values()) {
            r.addAll(switch (c) {
                case OK_LOGIN -> List.of(OpResult.okLogin("sid"));
                case OK_VALIDATE -> Arrays.stream(SessionState.Estado.values()).map(OpResult::okValidate).toList();
                case OK_STATUS -> Arrays.stream(IdentityState.Status.values()).map(OpResult::okStatus).toList();
                case OK_NOOP -> List.of(OpResult.okNoop());
                case BLOCKED -> List.of(OpResult.blocked());
                case NOT_FOUND -> List.of(OpResult.notFound());
                case ERROR_TRANSPORT -> List.of(OpResult.errorTransport());
                case ERROR_UNAVAILABLE -> List.of(OpResult.errorUnavailable());
            });
        }
        return r;
    }

    @Test
    @DisplayName("paridade: todo literal de OpResult.returnCode(), para todo Code, esta em EventoOperacao.CODIGOS")
    void paridadeComOpResult() {
        List<OpResult> resultados = todosOsResultados();
        Set<OpResult.Code> cobertos = new TreeSet<>();
        Set<String> literais = new TreeSet<>();
        for (OpResult r : resultados) {
            cobertos.add(r.code());
            literais.add(r.returnCode());
            assertTrue(EventoOperacao.CODIGOS.contains(r.returnCode()),
                    r.code() + " -> " + r.returnCode() + " fora de EventoOperacao.CODIGOS");
            // o construtor aceita o literal, e so os dois ERROR_* contam como erro
            EventoOperacao e = new EventoOperacao("Op", 0, 1, r.returnCode());
            assertEquals(r.returnCode().startsWith("ERROR_"), e.erro(), r.returnCode());
            assertEquals("ERROR_UNAVAILABLE".equals(r.returnCode()), e.indisponivel(), r.returnCode());
        }
        // nao vacuidade: todos os Code foram exercitados, inclusive os dois estados de OK_VALIDATE
        assertEquals(Set.of(OpResult.Code.values()), cobertos);
        assertTrue(literais.containsAll(List.of("VALID", "INVALID", "NOT_FOUND")), literais.toString());
        // e o contrario: CODIGOS nao tem codigo que OpResult nao grava
        assertEquals(literais, new TreeSet<>(EventoOperacao.CODIGOS));
    }

    @Test
    @DisplayName("CSV v2 real (B-43.4 Q1, 200 linhas): le tudo, com INVALID, e nenhum evento e erro")
    void leAmostraRealV2() throws IOException {
        List<EventoOperacao> eventos = new ArrayList<>();
        InputStream in = EventoOperacaoContratoV2Test.class.getResourceAsStream(FIXTURE);
        assertNotNull(in, "fixture ausente: " + FIXTURE);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            assertEquals(200, LeitorCsvEventos.ler(r, eventos::add));
        }
        Map<String, Integer> porCodigo = new TreeMap<>();
        Map<String, Integer> porOp = new TreeMap<>();
        for (EventoOperacao e : eventos) {
            porCodigo.merge(e.codigoRetorno(), 1, Integer::sum);
            porOp.merge(e.operacao(), 1, Integer::sum);
            assertFalse(e.erro(), e.toString());
            assertFalse(e.indisponivel(), e.toString());
        }
        assertEquals(Map.of("COUNTED", 7, "INVALID", 33, "NONE", 59, "OK", 36, "VALID", 65), porCodigo);
        assertEquals(Map.of("IncrementFailure", 7, "Login", 36, "Logout", 52, "ResetFailures", 7,
                "Validate", 98), porOp);
        // a linha 2 do arquivo (a que derrubava o leitor antes da correcao)
        assertEquals(new EventoOperacao("Validate", 1792695183804332400L, 1792695183804714400L, "INVALID"),
                eventos.get(0));
    }

    @Test
    @DisplayName("NOT_FOUND (contrato v2) e lido e nao e erro")
    void leNotFound() throws IOException {
        String csv = "op_id,operation,start_ns,end_ns,replica,return_code,key\n"
                + "0,Logout,10,20,127.0.0.1:11222,NOT_FOUND,sid-x\n"
                + "1,Validate,15,30,127.0.0.1:11222,INVALID,sid-y\n";
        List<EventoOperacao> ev = new ArrayList<>();
        assertEquals(2, LeitorCsvEventos.ler(new BufferedReader(new StringReader(csv)), ev::add));
        assertEquals(new EventoOperacao("Logout", 10, 20, "NOT_FOUND"), ev.get(0));
        assertFalse(ev.get(0).erro());
        assertFalse(ev.get(0).indisponivel());
        assertFalse(ev.get(1).erro());
    }
}
