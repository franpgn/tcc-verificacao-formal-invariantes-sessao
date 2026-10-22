package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Nucleo de decisao puro do controlador (B-28.1; plano de 2026-10-22, secoes 3.2, 3.3 e 3.5).
 *
 * <p>Maquina de estados NORMAL/CONSERVADOR avaliada uma vez por janela fechada, na ordem R1..R4.
 * O tempo e sempre {@code janela.fimNs()}; nada aqui le relogio de parede, faz E/S ou depende do
 * cliente do data grid, de modo que a mesma classe serve ao vivo e no replay offline. Nao e
 * thread-safe: o laco de controle chama {@link #avaliar} de uma unica thread.
 *
 * <ul>
 *   <li>R1: violacao ({@code violacoesSeguranca > 0}) leva a CONSERVADOR mesmo em cooldown e
 *       registra {@code ultimaViolacao}.</li>
 *   <li>R2: em NORMAL com cooldown expirado, topologia anomala na janela, ou latencia alta em k
 *       janelas seguidas, ou erro alto em k janelas seguidas, leva a CONSERVADOR.</li>
 *   <li>R3: em CONSERVADOR, sinal alto zera o contador de janelas calmas.</li>
 *   <li>R4: volta a NORMAL com cooldown expirado, m janelas calmas seguidas, nenhuma topologia
 *       anomala ha T_estavel e T_hold_viol desde a ultima violacao.</li>
 * </ul>
 *
 * <p>Sinal alto: taxa &gt;= theta_in. Janela calma: sem violacao, sem topologia anomala, e
 * latencia e erro &lt;= theta_out ou indeterminados. Entre theta_out e theta_in a janela nao e
 * alta nem calma (histerese). Amostra abaixo do minimo deixa o sinal indeterminado.
 */
public final class Controlador {

    private final ParametrosControlador p;

    private Perfil perfil = Perfil.NORMAL;
    private boolean houveJanela;
    private long ultimoFimNs;
    private boolean houveTransicao;
    private long ultimaTransicaoNs;
    private boolean houveViolacao;
    private long ultimaViolacaoNs;
    private boolean houveTopologiaAnomala;
    private long ultimaTopologiaAnomalaNs;
    private int persistenciaLat;
    private int persistenciaErr;
    private int calmas;

    public Controlador(ParametrosControlador parametros) {
        this.p = Objects.requireNonNull(parametros, "parametros");
    }

    /** Perfil corrente (o da ultima decisao; NORMAL antes da primeira). */
    public Perfil perfil() {
        return perfil;
    }

    public ParametrosControlador parametros() {
        return p;
    }

    /**
     * Avalia uma janela fechada e devolve a decisao. {@code janela.fimNs()} deve crescer
     * estritamente entre chamadas; caso contrario lanca {@link IllegalArgumentException} sem
     * alterar o estado.
     */
    public Decisao avaliar(Janela janela, EstadoTopologia topologia) {
        Objects.requireNonNull(janela, "janela");
        Objects.requireNonNull(topologia, "topologia");
        final long agora = janela.fimNs();
        if (houveJanela && agora <= ultimoFimNs) {
            throw new IllegalArgumentException("fimNs nao crescente: " + agora + " <= " + ultimoFimNs);
        }
        houveJanela = true;
        ultimoFimNs = agora;

        // --- sinais da janela
        Double taxaLat = taxa(janela.excedencias(), janela.elegiveisLat(), p.nLat());
        Double taxaErr = taxa(janela.erros(), janela.total(), p.nErr());
        boolean latAlta = taxaLat != null && taxaLat >= p.thetaLatIn();
        boolean errAlto = taxaErr != null && taxaErr >= p.thetaErrIn();
        boolean latCalma = taxaLat == null || taxaLat <= p.thetaLatOut();
        boolean errCalmo = taxaErr == null || taxaErr <= p.thetaErrOut();
        boolean violacao = janela.violacoesSeguranca() > 0;
        boolean topAnomala = topologia.anomala();
        boolean algumAlto = violacao || topAnomala || latAlta || errAlto;
        boolean calma = !violacao && !topAnomala && latCalma && errCalmo;

        persistenciaLat = latAlta ? persistenciaLat + 1 : 0;
        persistenciaErr = errAlto ? persistenciaErr + 1 : 0;
        if (violacao) {
            houveViolacao = true;
            ultimaViolacaoNs = agora;
        }
        if (topAnomala) {
            houveTopologiaAnomala = true;
            ultimaTopologiaAnomalaNs = agora;
        }
        boolean cooldownExpirado = !houveTransicao || agora - ultimaTransicaoNs >= p.cooldownNs();

        // --- regras, na ordem R1..R4
        Perfil novo = perfil;
        Decisao.Regra regra = Decisao.Regra.NENHUMA;
        if (perfil == Perfil.NORMAL) {
            if (violacao) {
                novo = Perfil.CONSERVADOR;
                regra = Decisao.Regra.R1;
            } else if (cooldownExpirado
                    && (topAnomala || persistenciaLat >= p.k() || persistenciaErr >= p.k())) {
                novo = Perfil.CONSERVADOR;
                regra = Decisao.Regra.R2;
            }
            calmas = 0;
        } else {
            calmas = calma ? calmas + 1 : 0;
            if (violacao) {
                regra = Decisao.Regra.R1;
            } else if (algumAlto) {
                regra = Decisao.Regra.R3;
            } else if (cooldownExpirado
                    && calmas >= p.m()
                    && (!houveTopologiaAnomala || agora - ultimaTopologiaAnomalaNs >= p.estavelNs())
                    && (!houveViolacao || agora - ultimaViolacaoNs >= p.holdViolNs())) {
                novo = Perfil.NORMAL;
                regra = Decisao.Regra.R4;
            }
        }

        Decisao.Sinais sinais = new Decisao.Sinais(taxaLat, taxaErr,
                janela.erros() == 0 ? null : (double) janela.indisponiveis() / janela.erros(),
                latAlta, errAlto, topAnomala,
                topologia.disponivel(), topologia.amostras(), topologia.anomalas(), topologia.nosMinimo(),
                janela.violacoesSeguranca(), janela.violacoesI2Esperadas(),
                persistenciaLat, persistenciaErr, calmas, cooldownExpirado);

        boolean transicao = novo != perfil;
        if (transicao) {
            houveTransicao = true;
            ultimaTransicaoNs = agora;
            perfil = novo;
            calmas = 0;
        }

        List<Motivo> motivos = new ArrayList<>(4);
        if (violacao) {
            motivos.add(Motivo.VIOLACAO);
        }
        if (topAnomala) {
            motivos.add(Motivo.TOPOLOGIA);
        }
        if (errAlto) {
            motivos.add(Motivo.ERRO);
        }
        if (latAlta) {
            motivos.add(Motivo.LATENCIA);
        }
        if (motivos.isEmpty()) {
            motivos.add(Motivo.ESTAVEL);
        }
        return new Decisao(agora, perfil, transicao, regra, motivos, motivos.get(0), sinais, p);
    }

    /** {@code num/den}, ou {@code null} (indeterminado) se {@code den < minimo} ou {@code den == 0}. */
    private static Double taxa(long num, long den, int minimo) {
        if (den == 0 || den < minimo) {
            return null;
        }
        return (double) num / den;
    }
}
