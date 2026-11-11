package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B-43.7 (T8): detector da posicao da carga de slots da v3 em {@code executarRepeticao}, com as
 * mesmas ferramentas do {@link DetectorFonte} (que nao muda): statement unico, sem comentario, so
 * dentro do {@code try (RemoteCacheManager ...)}, depois de {@code prepararEstado(} e antes de
 * {@code long inicioMs} e de {@code WarmupPolicy.padrao(}, na forma literal
 * {@code rcm.getCache(cli.replicacao.cacheSessoes())}.
 */
final class DetectorCargaSlots {

    private DetectorCargaSlots() { }

    static final Pattern CHAMADA = Pattern.compile(
            "(?m)^\\s*SlotsGeracao slots = prepararCargaSlots\\(cli, CacheCarregavel\\.de\\(rcm\\.getCache\\("
                    + "cli\\.replicacao\\.cacheSessoes\\(\\)\\)\\), rep\\);");

    static List<String> problemas(String fonte) {
        List<String> p = new ArrayList<>();
        String s = DetectorFonte.semComentarios(fonte);
        String m = DetectorFonte.mascarado(fonte);
        int[] corpo = DetectorFonte.corpo(m, DetectorFonte.ASSINATURA_REPETICAO);
        if (corpo == null) {
            p.add("executarRepeticao nao encontrado");
            return p;
        }
        String corpoS = s.substring(corpo[0], corpo[1]);
        Matcher cm = CHAMADA.matcher(corpoS);
        List<Integer> chamadas = new ArrayList<>();
        while (cm.find()) {
            chamadas.add(corpo[0] + corpoS.indexOf("SlotsGeracao slots", cm.start()));
        }
        if (chamadas.size() != 1) {
            p.add("esperada exatamente 1 chamada da carga de slots como statement (sem comentario), achadas "
                    + chamadas.size());
            return p;
        }
        int pos = chamadas.get(0);
        String anterior = DetectorFonte.linhaNaoVaziaAnterior(s, pos);
        if (!(anterior.endsWith("{") || anterior.endsWith(";"))) {
            p.add("linha nao vazia anterior a carga de slots nao termina em '{' nem ';': " + anterior);
        }
        List<String> blocos = DetectorFonte.blocosEnvolventes(m, corpo[0], pos);
        if (blocos.size() != 1 || !DetectorFonte.BLOCO_PERMITIDO.matcher(blocos.get(0)).find()) {
            p.add("a carga de slots tem de estar so dentro do try (RemoteCacheManager ...); blocos: " + blocos);
        }
        Matcher rm = DetectorFonte.CHAMADA_RESET.matcher(corpoS);
        if (!rm.find() || corpo[0] + rm.end() > pos) {
            p.add("a carga de slots tem de vir depois de prepararEstado(");
        }
        for (String depois : new String[] {"long inicioMs", "WarmupPolicy.padrao("}) {
            int k = corpoS.indexOf(depois);
            if (k < 0 || corpo[0] + k < pos) {
                p.add("a carga de slots tem de vir antes de '" + depois + "'");
            }
        }
        return p;
    }
}
