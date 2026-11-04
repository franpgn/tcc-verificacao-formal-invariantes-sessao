package br.unipampa.tcc.controle;

import java.util.Objects;

/**
 * W-3 (B-28.2): conteudo da primeira linha ({@code {"tipo":"meta",...}}) dos dois JSONL do controlador.
 *
 * @param versaoPrograma    versao do programa de carga
 * @param rep               numero da repeticao
 * @param modo              shadow ou active
 * @param cenario           S1, S2 ou S3
 * @param replicacao        sync ou async
 * @param nosEsperados      itens de {@code --servers} (H6)
 * @param parametros        parametros do nucleo (versao e SHA-256 do arquivo)
 * @param referenciaArquivo arquivo da referencia de p99, ou {@code null}
 * @param referenciaSha256  SHA-256 da referencia, ou {@code null}
 * @param toleranciaNs      L do agregador
 * @param tickNs            periodo do tick
 * @param origemNs          inicio da primeira janela (inicio da medicao)
 * @param atuador           {@link AtuadorCluster#nome()}
 * @param politica          {@link PoliticaDeAcao#nome()}
 * @param sondaTopologia    ha sonda de topologia
 * @param sondaDefasagem    ha sonda de defasagem
 */
public record MetaExecucao(String versaoPrograma, int rep, ModoAtuacao modo, String cenario, String replicacao,
                           int nosEsperados, ParametrosControlador parametros, String referenciaArquivo,
                           String referenciaSha256, long toleranciaNs, long tickNs, long origemNs,
                           String atuador, String politica, boolean sondaTopologia, boolean sondaDefasagem) {

    public MetaExecucao {
        Objects.requireNonNull(versaoPrograma, "versaoPrograma");
        Objects.requireNonNull(modo, "modo");
        Objects.requireNonNull(parametros, "parametros");
        Objects.requireNonNull(atuador, "atuador");
        Objects.requireNonNull(politica, "politica");
        if (nosEsperados < 1) {
            throw new IllegalArgumentException("nosEsperados < 1: " + nosEsperados);
        }
    }
}
