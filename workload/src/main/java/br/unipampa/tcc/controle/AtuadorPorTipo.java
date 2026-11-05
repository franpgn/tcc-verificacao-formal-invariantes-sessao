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
