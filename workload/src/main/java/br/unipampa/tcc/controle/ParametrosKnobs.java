package br.unipampa.tcc.controle;

import java.io.IOException;
import java.nio.file.Path;

/**
 * B-27: parametros dos knobs K1..K4 do portao de emissao (plano de 2026-10-22, secao 3.4). Ficam
 * fora de {@link ParametrosControlador} (decisao D1 do Gestor, 2026-10-27): arquivo JSON proprio.
 * Nenhum valor e fixo no codigo do {@link PortaoDeEmissao}. Tempos em nanossegundos.
 *
 * @param versao                   rotulo do conjunto de parametros
 * @param k1FracaoConservador      K1: fracao da concorrencia nominal em CONSERVADOR, em (0, 1]
 * @param k2PausaConservadorNs     K2 (S1/S2): pausa depois de cada operacao em CONSERVADOR (&gt;= 0)
 * @param k2MultPensarConservador  K2 (S3): multiplicador do tempo de pensar em CONSERVADOR (&gt;= 1)
 * @param k3AdiaMaxNs              K3: adiamento maximo de uma operacao administrativa (&gt; 0)
 * @param k3SondagemNs             K3: periodo de sondagem do perfil durante o adiamento, em (0, k3AdiaMaxNs]
 * @param k4FatorBaseConservador   K4: fator sobre a base do backoff em CONSERVADOR (&gt;= 1)
 * @param k4JitterConservador      K4: amplitude relativa do jitter em CONSERVADOR, em [0, 1)
 * @param sha256                   SHA-256 (hex minusculo) do arquivo de origem; {@code null} para {@link #padrao()}
 */
public record ParametrosKnobs(String versao, double k1FracaoConservador, long k2PausaConservadorNs,
                              double k2MultPensarConservador, long k3AdiaMaxNs, long k3SondagemNs,
                              double k4FatorBaseConservador, double k4JitterConservador, String sha256) {

    private static final long MS = 1_000_000L;
    private static final long S = 1_000_000_000L;

    /** Versao dos valores provisorios de {@link #padrao()}. */
    public static final String VERSAO_PADRAO = "provisorio-2026-10-22";

    /** Valores provisorios da secao 3.4 do plano de 2026-10-22 (a calibrar). */
    public static ParametrosKnobs padrao() {
        return new ParametrosKnobs(VERSAO_PADRAO, 0.5, 5 * MS, 1.5, 30 * S, 100 * MS, 2.0, 0.5, null);
    }

    /** Le {@code knobs-<nome>.json}. B-27 commit 1: provisorio. */
    public static ParametrosKnobs deJson(Path arquivo) throws IOException {
        throw new UnsupportedOperationException("B-27: deJson ainda nao implementado");
    }
}
