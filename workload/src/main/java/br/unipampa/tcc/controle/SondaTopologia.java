package br.unipampa.tcc.controle;

import java.util.List;
import java.util.function.Consumer;

/**
 * B-26.2: sonda de topologia (esqueleto; implementacao no commit feat).
 */
public final class SondaTopologia implements AutoCloseable {

    private final FonteDeSaude fonte;
    private final List<String> cachesDoPerfil;
    private final int nosEsperados;
    private final long periodoNs;
    private final long prazoRodadaNs;
    private final FonteDeTempo tempo;
    private final Consumer<AmostraTopologia> ouvinte;

    public SondaTopologia(FonteDeSaude fonte, List<String> cachesDoPerfil, int nosEsperados, long periodoNs,
                          long prazoRodadaNs, FonteDeTempo tempo, Consumer<AmostraTopologia> ouvinte) {
        this.fonte = fonte;
        this.cachesDoPerfil = cachesDoPerfil;
        this.nosEsperados = nosEsperados;
        this.periodoNs = periodoNs;
        this.prazoRodadaNs = prazoRodadaNs;
        this.tempo = tempo;
        this.ouvinte = ouvinte;
    }

    public static ResultadoNo classificar(String no, LeituraSaude leituraOuNull, String motivoFalha,
                                          List<String> caches, int nosEsperados) {
        return new ResultadoNo(no, true, 0, "ok", false);
    }

    public AmostraTopologia rodada() {
        return new AmostraTopologia(0L, false, 0, List.of());
    }

    public void iniciar() {
    }

    public synchronized EstadoTopologia resumirAte(long fimNs) {
        return new EstadoTopologia(true, 0, 0, 0);
    }

    @Override
    public void close() {
    }

    public long rodadas() {
        return 0;
    }

    public long rodadasPuladas() {
        return 0;
    }

    public long amostrasAtrasadas() {
        return 0;
    }

    public long errosOuvinte() {
        return 0;
    }
}
