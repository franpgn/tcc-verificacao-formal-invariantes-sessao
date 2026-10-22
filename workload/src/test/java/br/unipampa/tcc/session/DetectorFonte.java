package br.unipampa.tcc.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V-B43.1 (R1) e B-43.2 (T8): leitura estrutural do fonte de {@link WorkloadMain} para travar a
 * posicao do reset (e da carga inicial) em {@code executarRepeticao}.
 *
 * <p>O teste textual anterior ({@code resetAntesDoWarmup}) procurava as <i>strings</i> no fonte bruto
 * e aceitava codigo morto: a chamada comentada, um {@code if (false)} antes dela, um
 * {@code try/catch} local que engole a excecao e o reset pulado em S3. Este detector:</p>
 * <ol>
 *   <li>remove os comentarios ({@code //...} e {@code /* ... *&#47;}) preservando as quebras de linha
 *       e os deslocamentos (cada caractere de comentario vira espaco);</li>
 *   <li>mascara o conteudo de literais de texto/caractere para a contagem de chaves e parenteses;</li>
 *   <li>exige a chamada como <i>statement</i> no inicio da linha, com a linha nao vazia anterior
 *       terminando em {@code {} ou {@code ;}, e com um unico bloco envolvente dentro do metodo (o
 *       {@code try (RemoteCacheManager ...)}): nem {@code try} local nem {@code if}.</li>
 * </ol>
 *
 * <p>Nao e um <i>parser</i> Java completo: cobre a forma do fonte deste projeto (sem blocos de
 * texto {@code """}). E so de teste.</p>
 */
final class DetectorFonte {

    private DetectorFonte() { }

    /** Regex da chamada do reset como statement (inicio de linha). */
    static final Pattern CHAMADA_RESET = Pattern.compile("(?m)^\\s*prepararEstado\\(cli, List\\.of\\(");

    static final String ARG_SESSOES = "CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheSessoes()))";
    static final String ARG_CONTADORES = "CacheLimpavel.de(rcm.getCache(cli.replicacao.cacheContadores()))";

    /** Assinatura de {@code executarRepeticao}. */
    static final String ASSINATURA_REPETICAO = "static void executarRepeticao\\(Cli cli, int rep\\)";

    /** Assinatura de {@code main}. */
    static final String ASSINATURA_MAIN = "public static void main\\(String\\[\\] args\\)";

    /** Cabecalho do unico bloco que pode envolver o reset dentro de {@code executarRepeticao}. */
    static final Pattern BLOCO_PERMITIDO = Pattern.compile("^try\\s*\\(\\s*RemoteCacheManager\\s+rcm\\b");

    /** Fonte com comentarios trocados por espacos (quebras de linha e deslocamentos preservados). */
    static String semComentarios(String src) {
        return varrer(src, false);
    }

    /** Como {@link #semComentarios}, e alem disso com o conteudo de literais trocado por espacos. */
    static String mascarado(String src) {
        return varrer(src, true);
    }

    private static String varrer(String src, boolean mascararLiterais) {
        StringBuilder sb = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            char d = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && d == '/') {
                while (i < n && src.charAt(i) != '\n' && src.charAt(i) != '\r') {
                    sb.append(' ');
                    i++;
                }
            } else if (c == '/' && d == '*') {
                sb.append("  ");
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    char x = src.charAt(i);
                    sb.append(x == '\n' || x == '\r' ? x : ' ');
                    i++;
                }
                if (i < n) {
                    sb.append("  ");
                    i += 2;
                }
            } else if (c == '"' || c == '\'') {
                char q = c;
                sb.append(c);
                i++;
                while (i < n && src.charAt(i) != q) {
                    char x = src.charAt(i);
                    if (x == '\\' && i + 1 < n) {
                        sb.append(mascararLiterais ? "  " : src.substring(i, i + 2));
                        i += 2;
                        continue;
                    }
                    sb.append(mascararLiterais && x != '\n' && x != '\r' ? ' ' : x);
                    i++;
                }
                if (i < n) {
                    sb.append(q);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Corpo do metodo cuja assinatura casa com {@code regexAssinatura} no texto mascarado:
     * {@code [indice da chave que abre, indice da chave que fecha]}, ou {@code null} se ausente.
     */
    static int[] corpo(String mascarado, String regexAssinatura) {
        Matcher m = Pattern.compile(regexAssinatura).matcher(mascarado);
        if (!m.find()) {
            return null;
        }
        int abre = mascarado.indexOf('{', m.end());
        int fecha = fechamento(mascarado, abre, '{', '}');
        return fecha < 0 ? null : new int[] {abre, fecha};
    }

    /** Indice do fechamento correspondente a {@code abre} (texto mascarado), ou -1. */
    static int fechamento(String mascarado, int abre, char a, char f) {
        int prof = 0;
        for (int i = abre; i < mascarado.length(); i++) {
            char c = mascarado.charAt(i);
            if (c == a) {
                prof++;
            } else if (c == f) {
                prof--;
                if (prof == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Cabecalhos (texto antes de cada {@code {}, sem espacos nas pontas) dos blocos abertos entre
     * {@code inicioCorpo} (a chave do metodo, exclusiva) e {@code pos}, do mais externo ao mais
     * interno.
     */
    static List<String> blocosEnvolventes(String mascarado, int inicioCorpo, int pos) {
        Deque<String> pilha = new ArrayDeque<>();
        int ultimoDelim = inicioCorpo;
        int parenteses = 0;
        for (int i = inicioCorpo + 1; i < pos; i++) {
            char c = mascarado.charAt(i);
            if (c == '(') {
                parenteses++;
            } else if (c == ')') {
                parenteses--;
            } else if (parenteses > 0) {
                continue; // ';' do try-with-resources nao separa o cabecalho
            }
            if (c == '{') {
                pilha.addLast(mascarado.substring(ultimoDelim + 1, i).trim().replaceAll("\\s+", " "));
                ultimoDelim = i;
            } else if (c == '}') {
                pilha.pollLast();
                ultimoDelim = i;
            } else if (c == ';') {
                ultimoDelim = i;
            }
        }
        return new ArrayList<>(pilha);
    }

    /** Ultima linha nao vazia de {@code texto} antes do indice {@code pos} (sem espacos nas pontas). */
    static String linhaNaoVaziaAnterior(String texto, int pos) {
        int ini = texto.lastIndexOf('\n', pos - 1);
        while (ini > 0) {
            int antes = texto.lastIndexOf('\n', ini - 1);
            String linha = texto.substring(antes + 1, ini).trim();
            if (!linha.isEmpty()) {
                return linha;
            }
            ini = antes;
        }
        return "";
    }

    /**
     * Verifica a chamada do reset em {@code executarRepeticao}. Devolve a lista de problemas (vazia
     * = posicao e forma corretas). Ver a classe.
     */
    static List<String> problemasDoReset(String fonte) {
        List<String> p = new ArrayList<>();
        String s = semComentarios(fonte);
        String m = mascarado(fonte);
        int[] corpo = corpo(m, ASSINATURA_REPETICAO);
        if (corpo == null) {
            p.add("executarRepeticao nao encontrado");
            return p;
        }
        String corpoS = s.substring(corpo[0], corpo[1]);
        Matcher cm = CHAMADA_RESET.matcher(corpoS);
        List<Integer> chamadas = new ArrayList<>();
        while (cm.find()) {
            chamadas.add(corpo[0] + cm.end() - "prepararEstado(cli, List.of(".length());
        }
        if (chamadas.size() != 1) {
            p.add("esperada exatamente 1 chamada 'prepararEstado(cli, List.of(' como statement no inicio da "
                    + "linha (sem comentario), achadas " + chamadas.size());
            return p;
        }
        int pos = chamadas.get(0);
        String anterior = linhaNaoVaziaAnterior(s, pos);
        if (!(anterior.endsWith("{") || anterior.endsWith(";"))) {
            p.add("linha nao vazia anterior a chamada nao termina em '{' nem ';': " + anterior);
        }
        List<String> blocos = blocosEnvolventes(m, corpo[0], pos);
        if (blocos.size() != 1 || !BLOCO_PERMITIDO.matcher(blocos.get(0)).find()) {
            p.add("a chamada tem de estar so dentro do try (RemoteCacheManager ...), sem try local nem if; "
                    + "blocos envolventes: " + blocos);
        }
        int abreArgs = m.indexOf('(', pos);
        int fechaArgs = fechamento(m, abreArgs, '(', ')');
        String args = fechaArgs < 0 ? "" : s.substring(abreArgs, fechaArgs + 1);
        if (!args.contains(ARG_SESSOES)) {
            p.add("argumento do cache de sessoes ausente (sem comentario) na chamada");
        }
        if (!args.contains(ARG_CONTADORES)) {
            p.add("argumento do cache de contadores ausente (sem comentario) na chamada");
        }
        for (String depois : new String[] {"long inicioMs", "WarmupPolicy.padrao(", "if (cli.scenarioS3)"}) {
            int k = corpoS.indexOf(depois);
            if (k < 0) {
                p.add("'" + depois + "' ausente em executarRepeticao");
            } else if (corpo[0] + k < pos) {
                p.add("a chamada do reset tem de vir antes de '" + depois + "'");
            }
        }
        p.addAll(problemasDeCatch(s, m));
        return p;
    }

    /** Regex da chamada da carga inicial (B-43.2) como statement, na forma literal exigida. */
    static final Pattern CHAMADA_CARGA = Pattern.compile(
            "(?m)^\\s*prepararCarga\\(cli, CacheCarregavel\\.de\\(rcm\\.getCache\\(cli\\.replicacao\\.cacheSessoes\\(\\)\\)\\), rep\\);");

    /**
     * B-43.2 (T8): a chamada da carga em {@code executarRepeticao} e statement unico, executavel (so
     * dentro do {@code try} do rcm, linha nao vazia anterior terminando em {@code ;} ou {@code {}),
     * depois da chamada do reset e antes de {@code long inicioMs} e de {@code WarmupPolicy.padrao(}.
     */
    static List<String> problemasDaCarga(String fonte) {
        List<String> p = new ArrayList<>();
        String s = semComentarios(fonte);
        String m = mascarado(fonte);
        int[] corpo = corpo(m, ASSINATURA_REPETICAO);
        if (corpo == null) {
            p.add("executarRepeticao nao encontrado");
            return p;
        }
        String corpoS = s.substring(corpo[0], corpo[1]);
        Matcher cm = CHAMADA_CARGA.matcher(corpoS);
        List<Integer> chamadas = new ArrayList<>();
        while (cm.find()) {
            chamadas.add(corpo[0] + corpoS.indexOf("prepararCarga(", cm.start()));
        }
        if (chamadas.size() != 1) {
            p.add("esperada exatamente 1 chamada da carga como statement (sem comentario), achadas "
                    + chamadas.size());
            return p;
        }
        int pos = chamadas.get(0);
        String anterior = linhaNaoVaziaAnterior(s, pos);
        if (!(anterior.endsWith("{") || anterior.endsWith(";"))) {
            p.add("linha nao vazia anterior a carga nao termina em '{' nem ';': " + anterior);
        }
        List<String> blocos = blocosEnvolventes(m, corpo[0], pos);
        if (blocos.size() != 1 || !BLOCO_PERMITIDO.matcher(blocos.get(0)).find()) {
            p.add("a carga tem de estar so dentro do try (RemoteCacheManager ...); blocos: " + blocos);
        }
        Matcher rm = CHAMADA_RESET.matcher(corpoS);
        if (!rm.find() || corpo[0] + rm.end() > pos) {
            p.add("a carga tem de vir depois de prepararEstado(");
        }
        for (String depois : new String[] {"long inicioMs", "WarmupPolicy.padrao("}) {
            int k = corpoS.indexOf(depois);
            if (k < 0 || corpo[0] + k < pos) {
                p.add("a carga tem de vir antes de '" + depois + "'");
            }
        }
        return p;
    }

    /**
     * {@code catch (EstadoNaoLimpoException ...)} so pode aparecer em {@code main} (onde vira a saida
     * 5) ou como captura que relanca a mesma excecao no fim do bloco ({@code throw <var>;}), como em
     * {@code prepararEstado}, que grava o JSON de FALHA e relanca. Qualquer outro {@code catch} engole
     * a falha do reset (mutante M7).
     */
    static List<String> problemasDeCatch(String s, String m) {
        List<String> p = new ArrayList<>();
        int[] main = corpo(m, ASSINATURA_MAIN);
        Matcher cc = Pattern.compile("catch\\s*\\(\\s*EstadoNaoLimpoException\\s+(\\w+)\\s*\\)").matcher(s);
        int achados = 0;
        while (cc.find()) {
            achados++;
            if (main != null && cc.start() > main[0] && cc.start() < main[1]) {
                continue;
            }
            int abre = m.indexOf('{', cc.end());
            int fecha = abre < 0 ? -1 : fechamento(m, abre, '{', '}');
            String bloco = fecha < 0 ? "" : s.substring(abre + 1, fecha).trim();
            if (!Pattern.compile("(^|[;{}])\\s*throw\\s+" + Pattern.quote(cc.group(1)) + "\\s*;$")
                    .matcher(bloco).find()) {
                p.add("'catch (EstadoNaoLimpoException " + cc.group(1) + ")' fora de main sem relancar, no indice "
                        + cc.start());
            }
        }
        Matcher qualquer = Pattern.compile("catch\\s*\\(\\s*EstadoNaoLimpoException").matcher(s);
        int total = 0;
        while (qualquer.find()) {
            total++;
        }
        if (total != achados) {
            p.add("'catch (EstadoNaoLimpoException' em forma nao reconhecida (multi-catch?)");
        }
        return p;
    }
}
