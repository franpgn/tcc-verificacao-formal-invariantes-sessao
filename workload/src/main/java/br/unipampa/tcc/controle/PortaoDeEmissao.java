package br.unipampa.tcc.controle;

import java.util.function.Consumer;

/**
 * B-27: portao de emissao do cliente, onde os knobs K1..K4 agem (plano de 2026-10-22, secao 3.4).
 * B-27 commit 1: assinaturas finais, corpos provisorios (comporta-se como {@link #livre()}).
 */
public final class PortaoDeEmissao {

    private static final PortaoDeEmissao LIVRE = new PortaoDeEmissao();

    private final PerfilAtual perfil;
    private final ParametrosKnobs k;
    private final int concorrenciaNominal;
    private final FonteDeTempo tempo;
    private final Dormidor dormidor;
    private final Consumer<EventoK3> ouvinteK3;
    private final long sementeJitter;

    private PortaoDeEmissao() {
        this.perfil = null;
        this.k = null;
        this.concorrenciaNominal = Integer.MAX_VALUE;
        this.tempo = null;
        this.dormidor = null;
        this.ouvinteK3 = null;
        this.sementeJitter = 0L;
    }

    public PortaoDeEmissao(PerfilAtual perfil, ParametrosKnobs k, int concorrenciaNominal, FonteDeTempo tempo,
                           Dormidor dormidor, Consumer<EventoK3> ouvinteK3, long sementeJitter) {
        this.perfil = perfil;
        this.k = k;
        this.concorrenciaNominal = concorrenciaNominal;
        this.tempo = tempo;
        this.dormidor = dormidor;
        this.ouvinteK3 = ouvinteK3;
        this.sementeJitter = sementeJitter;
    }

    /** Portao sem efeito (singleton). */
    public static PortaoDeEmissao livre() {
        return LIVRE;
    }

    public long entrar() throws InterruptedException {
        return 0L;
    }

    public void sair() {
    }

    public long pausaAposOperacaoNs() {
        return 0L;
    }

    public long aplicarPensar(long pensarNs) {
        return pensarNs;
    }

    public long aguardarAdministrativa(String ator, String operacao, String alvo) throws InterruptedException {
        return 0L;
    }

    public long esperaRetentativaNs(String ator, long baseNs, int tentativa, long inicioAcaoNs) {
        return baseNs;
    }

    public void encerrar() {
    }

    public int emVoo() {
        return 0;
    }

    public int limiteAtual() {
        return Integer.MAX_VALUE;
    }

    public long esperaK1TotalNs() {
        return 0L;
    }

    public long adiamentosK3() {
        return 0L;
    }
}
