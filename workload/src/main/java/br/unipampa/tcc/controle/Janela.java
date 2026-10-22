package br.unipampa.tcc.controle;

/**
 * Agregado de uma janela tumbling {@code [inicioNs, fimNs)} (plano de 2026-10-22, secao 3.2).
 *
 * @param inicioNs             inicio da janela (ns, mesmo relogio de {@code end_ns} dos eventos)
 * @param fimNs                fim da janela; e o "agora" do controlador
 * @param total                eventos na janela
 * @param erros                eventos ERROR_TRANSPORT + ERROR_UNAVAILABLE
 * @param indisponiveis        eventos ERROR_UNAVAILABLE (subconjunto de {@code erros})
 * @param elegiveisLat         eventos cuja operacao tem p99 de referencia
 * @param excedencias          elegiveis com latencia acima do p99 de referencia
 * @param violacoesSeguranca   violacoes I1 + I4 + I2 real na janela
 * @param violacoesI2Esperadas violacoes I2 esperadas (so registro, nao decidem)
 */
public record Janela(long inicioNs, long fimNs, long total, long erros, long indisponiveis,
                     long elegiveisLat, long excedencias, long violacoesSeguranca,
                     long violacoesI2Esperadas) {

    public Janela {
        if (fimNs <= inicioNs) {
            throw new IllegalArgumentException("fimNs deve ser maior que inicioNs: " + inicioNs + " .. " + fimNs);
        }
        naoNegativo("total", total);
        naoNegativo("erros", erros);
        naoNegativo("indisponiveis", indisponiveis);
        naoNegativo("elegiveisLat", elegiveisLat);
        naoNegativo("excedencias", excedencias);
        naoNegativo("violacoesSeguranca", violacoesSeguranca);
        naoNegativo("violacoesI2Esperadas", violacoesI2Esperadas);
        if (erros > total) {
            throw new IllegalArgumentException("erros > total: " + erros + " > " + total);
        }
        if (indisponiveis > erros) {
            throw new IllegalArgumentException("indisponiveis > erros: " + indisponiveis + " > " + erros);
        }
        if (elegiveisLat > total) {
            throw new IllegalArgumentException("elegiveisLat > total: " + elegiveisLat + " > " + total);
        }
        if (excedencias > elegiveisLat) {
            throw new IllegalArgumentException("excedencias > elegiveisLat: " + excedencias + " > " + elegiveisLat);
        }
    }

    private static void naoNegativo(String nome, long v) {
        if (v < 0) {
            throw new IllegalArgumentException(nome + " negativo: " + v);
        }
    }
}
