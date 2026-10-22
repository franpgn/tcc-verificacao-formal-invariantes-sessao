package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-43.8: W6 (guarda de producao) e a linha {@code Invalidacao:} do stdout.
 *
 * <ul>
 *   <li>W6: no fonte sem comentarios, todo construtor de {@link SessionOps} que resolve o cache de
 *       sessoes a partir do {@code RemoteCacheManager} usa {@code ArmazemVersionado.de(}; nenhum usa
 *       {@code Armazem.de(} para o cache de sessoes. O de identidades continua {@code Armazem.de(}.</li>
 *   <li>A linha {@code Auditor:} e fixada inteira por {@code AuditorLinhaTest}, entao os contadores
 *       novos vao numa linha NOVA {@code Invalidacao: perdida=<n> conflito_esgotado=<m>}, impressa uma
 *       vez por repeticao (S1/S2 e S3), logo depois da {@code Auditor:}. So diagnostico (DT-001).</li>
 * </ul>
 */
class InvalidacaoProducaoTest {

    static final String SESSOES_VERSIONADO =
            "ArmazemVersionado.de(rcm.<String, SessionState>getCache(perfil.cacheSessoes()))";

    @Test
    @DisplayName("W6: os construtores de producao de SessionOps usam ArmazemVersionado.de para as sessoes")
    void producaoUsaVersionado() throws Exception {
        String s = ArmazemVersionadoTest.fonteSemComentarios("SessionOps.java");
        int n = 0;
        for (int i = s.indexOf(SESSOES_VERSIONADO); i >= 0; i = s.indexOf(SESSOES_VERSIONADO, i + 1)) {
            n++;
        }
        assertEquals(2, n, "os dois construtores com RemoteCacheManager");
        Matcher comum = Pattern.compile("(?<![A-Za-z])Armazem\\.de\\(\\s*rcm\\.\\s*<\\s*String\\s*,\\s*SessionState\\s*>")
                .matcher(s);
        assertFalse(comum.find(), "cache de sessoes de producao com Armazem.de comum");
        assertTrue(s.contains("this.sessoesVersionadas = sessions instanceof ArmazemVersionado<SessionState> v ? v : null;"));
        // getCache(...) de sessoes so aparece dentro do adaptador versionado
        Matcher todos = Pattern.compile("getCache\\(perfil\\.cacheSessoes\\(\\)\\)").matcher(s);
        int g = 0;
        while (todos.find()) {
            g++;
        }
        assertEquals(2, g);
    }

    @Test
    @DisplayName("formato da linha Invalidacao e soma das instancias")
    void linhaInvalidacao() {
        ConcurrentHashMap<String, SessionState.Motivo> mapa = new ConcurrentHashMap<>();
        InvariantAuditor a = new InvariantAuditor();
        FakeVersionado<SessionState> s = new FakeVersionado<>();
        Instant t = Instant.parse("2026-10-22T00:00:00Z");
        s.semear("s1", SessionState.criar("s1", "u", t));
        s.semear("s2", SessionState.criar("s2", "u", t));
        SessionOps x = LogoutCondicionalTest.ops(s, mapa, a, new LatencyRegistry());
        SessionOps y = LogoutCondicionalTest.ops(s, mapa, a, new LatencyRegistry());
        assertEquals("Invalidacao: perdida=0 conflito_esgotado=0", SessionOps.linhaInvalidacao(List.of(x, y)));

        Thread dona = Thread.currentThread();
        boolean[] feito = {false};
        s.antes = (op, k) -> {
            if (!feito[0] && Thread.currentThread() == dona && op.equals("substituirSeVersao")) {
                feito[0] = true;
                Concorrencia.emOutraThread(() -> y.logout("s1"));
            }
        };
        x.logout("s1");
        s.antes = (op, k) -> { };
        s.sempreConflita = true;
        y.logout("s2");

        assertEquals("Invalidacao: perdida=1 conflito_esgotado=1", SessionOps.linhaInvalidacao(List.of(x, y)));
    }

    @Test
    @DisplayName("WorkloadMain imprime a linha Invalidacao uma vez, logo depois da Auditor, fora de if")
    void workloadMainImprime() throws Exception {
        String s = ArmazemVersionadoTest.fonteSemComentarios("WorkloadMain.java");
        String m = DetectorFonte.mascarado(
                java.nio.file.Files.readString(NomesDeCacheSoNoResolvedorTest.raizMain()
                        .resolve("java/br/unipampa/tcc/session/WorkloadMain.java")).replace("\r\n", "\n"));
        int[] corpo = DetectorFonte.corpo(m, DetectorFonte.ASSINATURA_REPETICAO);
        String rep = s.substring(corpo[0], corpo[1]);
        Matcher l = Pattern.compile(
                "(?m)^\\s*System\\.out\\.println\\(SessionOps\\.linhaAuditor\\(instanciasAuditor\\)\\);\\n"
                        + "\\s*System\\.out\\.println\\(SessionOps\\.linhaInvalidacao\\(instanciasAuditor\\)\\);")
                .matcher(rep);
        assertTrue(l.find(), "linha Invalidacao ausente ou fora do lugar");
        int pos = l.end();
        assertFalse(l.find(), "duplicada");
        assertEquals(1, rep.split("linhaInvalidacao\\(", -1).length - 1);
        List<String> blocos = DetectorFonte.blocosEnvolventes(m, corpo[0], corpo[0] + pos - 1);
        assertEquals(1, blocos.size(), "fora de if: " + blocos);
    }
}
