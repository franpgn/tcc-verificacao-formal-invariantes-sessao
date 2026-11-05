package br.unipampa.tcc.controle;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * W-2a (B-44.2): encaminha cada acao ao atuador do seu tipo. Hoje o AC-2 e o AC-5 vao ao mesmo adaptador
 * REST ({@code session.AtuadorRestInfinispan}); se o B-44.3 aprovar a AC-6 (exclusao forcada pelo JGroups,
 * {@code docs/opcoes-arquitetura-configuracao-2026-11-05.md} secao 6 item 3), ela entra como mais uma
 * entrada deste mapa, com o adaptador dela, sem mudar o executor nem o adaptador REST.
 *
 * <p>Tipo sem atuador: {@link ResultadoAtuacao.Estado#FALHOU}, sem chamada.</p>
 */
public final class AtuadorPorTipo implements AtuadorCluster {

    static final String SEM_ATUADOR = "tipo-sem-atuador";

    private final Map<AcaoCluster.Tipo, AtuadorCluster> porTipo;
    private final String nome;

    public AtuadorPorTipo(Map<AcaoCluster.Tipo, AtuadorCluster> porTipo) {
        Objects.requireNonNull(porTipo, "porTipo");
        if (porTipo.isEmpty()) {
            throw new IllegalArgumentException("nenhum atuador");
        }
        EnumMap<AcaoCluster.Tipo, AtuadorCluster> m = new EnumMap<>(AcaoCluster.Tipo.class);
        porTipo.forEach((t, a) -> m.put(Objects.requireNonNull(t, "tipo"), Objects.requireNonNull(a, "atuador")));
        this.porTipo = m;
        StringJoiner j = new StringJoiner(",", "por-tipo(", ")");
        m.forEach((t, a) -> j.add(t.rotulo() + "=" + a.nome()));
        this.nome = j.toString();
    }

    /**
     * Braco B do W-2a: AC-2 e AC-5 no mesmo adaptador (o REST). Se a AC-6 entrar, ganha a sua entrada aqui.
     */
    public static AtuadorPorTipo bracoB(AtuadorCluster rest) {
        Map<AcaoCluster.Tipo, AtuadorCluster> m = new EnumMap<>(AcaoCluster.Tipo.class);
        m.put(AcaoCluster.Tipo.AC_2, rest);
        m.put(AcaoCluster.Tipo.AC_5, rest);
        return new AtuadorPorTipo(m);
    }

    /**
     * V-W2a R1: pior caso do {@code T_enc} do {@link ExecutorDeAcoes} com o braco B: o AC-2 na pilha, o AC-5 em
     * curso e um {@code T_cham} (so ha uma chave de AC-2; a cota do AC-5 e 1). Com {@code T_cham} = 2 s, 9 s.
     */
    public static long piorCasoEncerramentoBracoBNs(long tChamNs) {
        return AcaoCluster.Tipo.AC_2.prazoNs(tChamNs) + AcaoCluster.Tipo.AC_5.prazoNs(tChamNs) + tChamNs;
    }

    /** Nome de {@link #bracoB} para um adaptador com este nome (o {@code --dry-run} usa sem criar o adaptador). */
    public static String nomeBracoB(String nomeDoAdaptador) {
        return "por-tipo(AC-2=" + nomeDoAdaptador + ",AC-5=" + nomeDoAdaptador + ")";
    }

    @Override
    public String nome() {
        return nome;
    }

    @Override
    public ResultadoAtuacao aplicar(AcaoCluster acao, long prazoNs) throws Exception {
        AtuadorCluster a = porTipo.get(acao.tipo());
        return a == null ? ResultadoAtuacao.falhou(null, null, SEM_ATUADOR + ":" + acao.tipo().rotulo())
                : a.aplicar(acao, prazoNs);
    }

    @Override
    public ResultadoAtuacao reverter(AcaoCluster acao, long prazoNs) throws Exception {
        AtuadorCluster a = porTipo.get(acao.tipo());
        return a == null ? ResultadoAtuacao.falhou(null, null, SEM_ATUADOR + ":" + acao.tipo().rotulo())
                : a.reverter(acao, prazoNs);
    }
}
