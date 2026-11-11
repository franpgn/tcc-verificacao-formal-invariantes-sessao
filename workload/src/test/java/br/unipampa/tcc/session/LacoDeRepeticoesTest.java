package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-B43.2, sobrevivente N1 (fechado em B-43.6): o detector de {@link DetectorFonte} so olha
 * {@code catch (EstadoNaoLimpoException ...)}. Um {@code try { executarRepeticao(cli, rep); } catch
 * (Exception ignorada) { }} no laco de repeticoes de {@code executar} engolia a saida 5 (estado nao
 * limpo, carga falha) e qualquer outra falha, e passava.
 *
 * <p>Regra travada aqui: no corpo de {@code executar(Cli cli)} a chamada
 * {@code executarRepeticao(cli, rep);} e um <i>statement</i> unico, no inicio da linha, sem
 * comentario, cuja linha nao vazia anterior termina em {@code {} ou {@code ;}, e cujo unico bloco
 * envolvente dentro do metodo e o {@code for (int rep ...)}: nem {@code try}, nem {@code if}. Alem
 * disso, o corpo de {@code executar} nao tem nenhum {@code catch}. Um autoteste prova que as formas
 * de evasao conhecidas sao pegas.</p>
 */
class LacoDeRepeticoesTest {

    static final String ASSINATURA_EXECUTAR = "static void executar\\(Cli cli\\)";

    static final Pattern CHAMADA = Pattern.compile("(?m)^\\s*executarRepeticao\\(cli, rep\\);");

    static final Pattern BLOCO_FOR = Pattern.compile("^for\\s*\\(\\s*int\\s+rep\\b");

    private static String fonteWorkloadMain() throws Exception {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain()
                .resolve("java/br/unipampa/tcc/session/WorkloadMain.java");
        return Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Problemas da forma do laco de repeticoes (vazia = forma correta). */
    static List<String> problemasDoLaco(String fonte) {
        List<String> p = new ArrayList<>();
        String s = DetectorFonte.semComentarios(fonte);
        String m = DetectorFonte.mascarado(fonte);
        int[] corpo = DetectorFonte.corpo(m, ASSINATURA_EXECUTAR);
        if (corpo == null) {
            p.add("executar(Cli cli) nao encontrado");
            return p;
        }
        String corpoS = s.substring(corpo[0], corpo[1]);
        Matcher cm = CHAMADA.matcher(corpoS);
        List<Integer> chamadas = new ArrayList<>();
        while (cm.find()) {
            chamadas.add(corpo[0] + corpoS.indexOf("executarRepeticao(", cm.start()));
        }
        if (chamadas.size() != 1) {
            p.add("esperada exatamente 1 chamada 'executarRepeticao(cli, rep);' como statement (sem comentario), "
                    + "achadas " + chamadas.size());
            return p;
        }
        int pos = chamadas.get(0);
        String anterior = DetectorFonte.linhaNaoVaziaAnterior(s, pos);
        if (!(anterior.endsWith("{") || anterior.endsWith(";"))) {
            p.add("linha nao vazia anterior a chamada nao termina em '{' nem ';': " + anterior);
        }
        List<String> blocos = DetectorFonte.blocosEnvolventes(m, corpo[0], pos);
        if (blocos.size() != 1 || !BLOCO_FOR.matcher(blocos.get(0)).find()) {
            p.add("a chamada tem de estar so dentro do for (int rep ...), sem try nem if; blocos: " + blocos);
        }
        if (Pattern.compile("\\bcatch\\s*\\(").matcher(m.substring(corpo[0], corpo[1])).find()) {
            p.add("executar(Cli cli) nao pode ter catch: a falha de uma repeticao tem de chegar a main");
        }
        return p;
    }

    /** Troca a unica ocorrencia de {@code de} por {@code para}; falha se nao houver exatamente uma. */
    private static String trocarUma(String src, String de, String para) {
        int i = src.indexOf(de);
        assertTrue(i >= 0, "trecho ausente: " + de);
        assertEquals(-1, src.indexOf(de, i + 1), "trecho nao e unico: " + de);
        return src.substring(0, i) + para + src.substring(i + de.length());
    }

    @Test
    @DisplayName("N1: executarRepeticao e statement executavel no for de executar, fora de try, sem catch")
    void lacoSemCaptura() throws Exception {
        List<String> p = problemasDoLaco(fonteWorkloadMain());
        assertTrue(p.isEmpty(), "problemas: " + p);
    }

    @Test
    @DisplayName("N1: o detector pega o catch generico, o catch de Throwable, if, comentario e chamada removida")
    void detectorPegaEvasoes() throws Exception {
        String src = fonteWorkloadMain();
        String chamada = "executarRepeticao(cli, rep);";
        Map<String, String> mutantes = new LinkedHashMap<>();
        mutantes.put("N1 catch (Exception)", trocarUma(src, chamada,
                "try { executarRepeticao(cli, rep); } catch (Exception ignorada) { }"));
        mutantes.put("try em linhas separadas", trocarUma(src, chamada,
                "try {\n                executarRepeticao(cli, rep);\n            } catch (Throwable t) {\n            }"));
        mutantes.put("if (false)", trocarUma(src, chamada, "if (false)\n            executarRepeticao(cli, rep);"));
        mutantes.put("if com bloco", trocarUma(src, chamada, "if (rep < 0) { executarRepeticao(cli, rep); }"));
        mutantes.put("comentada", trocarUma(src, chamada, "// executarRepeticao(cli, rep);"));
        mutantes.put("catch fora da chamada", trocarUma(src, "System.out.println(\"Configuracao: \" + cli);",
                "try { System.out.println(\"Configuracao: \" + cli); } catch (RuntimeException e) { }"));
        for (Map.Entry<String, String> e : mutantes.entrySet()) {
            assertFalse(problemasDoLaco(e.getValue()).isEmpty(), "detector nao pegou: " + e.getKey());
        }
    }
}
