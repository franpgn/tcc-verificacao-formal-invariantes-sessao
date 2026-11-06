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
     * P1-IC: o AC-7 tem o mesmo prazo do AC-5 ({@code T_cham + 15 s}), na pilha ou em curso, e a cota do P1 e 1, entao
     * a conta abaixo vale com qualquer das duas acoes de P1.
     * V-W2a R1: pior caso do {@code T_enc} do {@link ExecutorDeAcoes} com o braco B: o AC-2 na pilha, o AC-5 em
     * curso e um {@code T_cham} (so ha uma chave de AC-2; a cota do AC-5 e 1). Com {@code T_cham} = 2 s e o AC-5 em
     * {@code T_cham + 15 s} (P-42), 2 + 17 + 2 = 21 s. Sem a P2 (D-B, modo de avaliacao), o AC-2 nunca entra na
     * pilha: 17 + 2 = 19 s.
     *
     * @param p2Habilitada a politica pode pedir o AC-2 ({@link ParametrosPolitica#p2Habilitada()})
     */
    public static long piorCasoEncerramentoBracoBNs(long tChamNs, boolean p2Habilitada) {
        return (p2Habilitada ? AcaoCluster.Tipo.AC_2.prazoNs(tChamNs) : 0L) + AcaoCluster.Tipo.AC_5.prazoNs(tChamNs)
                + tChamNs;
    }

    /**
     * P1-IC (06/11, G-1): braco B com o AC-7 (ignorar o cache de sessoes no suspeito) no seu adaptador
     * ({@code session.AtuadorIgnorarCache}); AC-2 e AC-5 continuam no REST (o AC-5 so e pedido com
     * {@code acaoP1 = AC-5}, para comparacao).
     */
    public static AtuadorPorTipo bracoB(AtuadorCluster rest, AtuadorCluster ignorarCache) {
        Map<AcaoCluster.Tipo, AtuadorCluster> m = new EnumMap<>(AcaoCluster.Tipo.class);
        m.put(AcaoCluster.Tipo.AC_2, rest);
        m.put(AcaoCluster.Tipo.AC_5, rest);
        m.put(AcaoCluster.Tipo.AC_7, ignorarCache);
        return new AtuadorPorTipo(m);
    }

    /** Nome de {@link #bracoB(AtuadorCluster, AtuadorCluster)} sem criar os adaptadores (o {@code --dry-run}). */
    public static String nomeBracoB(String nomeRest, String nomeIgnorarCache) {
        return "por-tipo(AC-2=" + nomeRest + ",AC-5=" + nomeRest + ",AC-7=" + nomeIgnorarCache + ")";
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
