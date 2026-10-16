package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Formato CSV da sonda Hot Rod ({@link SondaHotrod}): uma linha por operacao, sem quebra de
 * linha dentro do registro (as quebras de mensagem viram a sequencia literal {@code \n}), para
 * que a captura possa ser processada por linha. As colunas {@code mensagem} e {@code causas}
 * guardam o texto <b>integral</b> da excecao, sem truncar: e o insumo do ClassificadorDeErro
 * (RAST-G3-1/C-2).
 */
public final class SondaCsv {

    public static final String CABECALHO =
            "t_epoch_ns,no,cache,operacao,duracao_ns,resultado,excecao_classe,mensagem,causas";

    /** Limite defensivo contra cadeias patologicas; o normal e < 10. */
    private static final int MAX_CAUSAS = 32;

    private SondaCsv() { /* utilitario */ }

    /** Linha de uma operacao; {@code erro == null} significa sucesso. */
    public static String linha(long tEpochNs, String no, String cache, String operacao,
                               long duracaoNs, Throwable erro) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(tEpochNs).append(',')
          .append(campo(no)).append(',')
          .append(campo(cache)).append(',')
          .append(campo(operacao)).append(',')
          .append(duracaoNs).append(',');
        if (erro == null) {
            return sb.append("OK,,,").toString();
        }
        sb.append("ERRO,")
          .append(campo(erro.getClass().getName())).append(',')
          .append(campo(erro.getMessage())).append(',')
          .append(campo(cadeiaDeCausas(erro)));
        return sb.toString();
    }

    /** {@code classe: mensagem} de cada causa (exclui a propria excecao), separadas por {@code " | "}. */
    static String cadeiaDeCausas(Throwable topo) {
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        vistos.add(topo);
        List<String> partes = new ArrayList<>();
        for (Throwable c = topo.getCause(); c != null && vistos.add(c) && partes.size() < MAX_CAUSAS;
             c = c.getCause()) {
            String m = c.getMessage();
            partes.add(m == null ? c.getClass().getName() : c.getClass().getName() + ": " + m);
        }
        return String.join(" | ", partes);
    }

    /** RFC 4180 com quebras de linha neutralizadas. */
    static String campo(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String t = texto.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        if (t.indexOf(',') >= 0 || t.indexOf('"') >= 0) {
            return '"' + t.replace("\"", "\"\"") + '"';
        }
        return t;
    }
}
