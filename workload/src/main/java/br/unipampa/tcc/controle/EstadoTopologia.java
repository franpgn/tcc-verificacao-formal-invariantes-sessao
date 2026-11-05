package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resumo das amostras da sonda de topologia numa janela (plano de 2026-10-22, secao 3.2, S_top).
 * A classificacao de cada amostra (consulta REST falhou, menos de 3 nos, cache nao HEALTHY) e
 * feita pela sonda; aqui chega so a contagem.
 *
 * <p><b>W-2a (B-44.2):</b> {@code porNo} traz, por no consultado, se ele respondeu a REST em todas as
 * amostras da janela, em alguma ou em nenhuma, e o menor e o maior {@code number_of_nodes} que ele
 * informou. Dai saem as assinaturas da politica de acao (documento de 05/11, secao 5):
 * {@link #vistaCompleta()}, {@link #noSemResposta()} e {@link #vistasDivergentes()}. O construtor de quatro
 * campos (o de antes do W-2a) deixa {@code porNo} vazio, e com {@code porNo} vazio nenhuma assinatura
 * dispara.</p>
 *
 * @param disponivel {@code false} quando nao ha sonda ou dado de topologia: sinal neutro
 * @param amostras   amostras de topologia na janela
 * @param anomalas   amostras classificadas como anomalas
 * @param nosMinimo  menor {@code number_of_nodes} visto na janela (so registro)
 * @param porNo      estado de cada no na janela, na ordem da sonda (copia imutavel); vazio sem amostra
 */
public record EstadoTopologia(boolean disponivel, int amostras, int anomalas, int nosMinimo,
                              Map<String, No> porNo) {

    /** Em quantas amostras da janela o no respondeu a REST. */
    public enum Resposta {
        /** Em todas. */
        TODAS,
        /** Em pelo menos uma, nao em todas. */
        ALGUMA,
        /** Em nenhuma. */
        NENHUMA
    }

    /**
     * Estado de um no na janela.
     *
     * @param resposta  em quantas amostras respondeu
     * @param menorNos  menor {@code number_of_nodes} informado, ou {@code -1} se nao respondeu em nenhuma
     * @param maiorNos  maior {@code number_of_nodes} informado, ou {@code -1} se nao respondeu em nenhuma
     */
    public record No(Resposta resposta, int menorNos, int maiorNos) {

        public No {
            Objects.requireNonNull(resposta, "resposta");
            if (resposta == Resposta.NENHUMA) {
                if (menorNos != -1 || maiorNos != -1) {
                    throw new IllegalArgumentException("no sem resposta nao tem number_of_nodes: " + menorNos
                            + ".." + maiorNos);
                }
            } else if (menorNos < 0 || maiorNos < menorNos) {
                throw new IllegalArgumentException("faixa de number_of_nodes invalida: " + menorNos + ".." + maiorNos);
            }
        }

        /** Respondeu em pelo menos uma amostra. */
        public boolean respondeu() {
            return resposta != Resposta.NENHUMA;
        }
    }

    private static final EstadoTopologia INDISPONIVEL = new EstadoTopologia(false, 0, 0, 0);

    public EstadoTopologia {
        if (amostras < 0 || anomalas < 0 || nosMinimo < 0) {
            throw new IllegalArgumentException("contagem negativa: amostras=" + amostras
                    + " anomalas=" + anomalas + " nosMinimo=" + nosMinimo);
        }
        if (anomalas > amostras) {
            throw new IllegalArgumentException("anomalas > amostras: " + anomalas + " > " + amostras);
        }
        Objects.requireNonNull(porNo, "porNo");
        LinkedHashMap<String, No> copia = new LinkedHashMap<>();
        porNo.forEach((k, v) -> copia.put(Objects.requireNonNull(k, "rotulo nulo"),
                Objects.requireNonNull(v, "estado nulo para " + k)));
        if (amostras == 0 && !copia.isEmpty()) {
            throw new IllegalArgumentException("porNo sem amostras: " + copia.keySet());
        }
        porNo = Collections.unmodifiableMap(copia);
    }

    /** Construtor de antes do W-2a: sem estado por no. */
    public EstadoTopologia(boolean disponivel, int amostras, int anomalas, int nosMinimo) {
        this(disponivel, amostras, anomalas, nosMinimo, Map.of());
    }

    /** Estado neutro: nenhuma informacao de topologia. */
    public static EstadoTopologia indisponivel() {
        return INDISPONIVEL;
    }

    /** S_top ligado: disponivel e com ao menos uma amostra anomala na janela. */
    public boolean anomala() {
        return disponivel && anomalas > 0;
    }

    /** O mesmo resumo sem {@code porNo} (o que {@link FonteDeTopologia#resumirAte(long)} devolve). */
    public EstadoTopologia semPorNo() {
        return porNo.isEmpty() ? this : new EstadoTopologia(disponivel, amostras, anomalas, nosMinimo);
    }

    private boolean comDados() {
        return disponivel && amostras > 0 && !porNo.isEmpty();
    }

    /**
     * Vista completa em todas as amostras da janela: todo no consultado respondeu em todas e informou
     * sempre {@code number_of_nodes} igual ao numero de nos consultados (secao 3.1: "os tres nos respondem
     * a REST e informam 3 membros").
     */
    public boolean vistaCompleta() {
        if (!comDados()) {
            return false;
        }
        int n = porNo.size();
        for (No no : porNo.values()) {
            if (no.resposta() != Resposta.TODAS || no.menorNos() != n || no.maiorNos() != n) {
                return false;
            }
        }
        return true;
    }

    /**
     * Assinatura de queda (secao 3.2): exatamente um no sem resposta em nenhuma amostra da janela e todos os
     * outros respondendo em todas. Devolve esse no; vazio em qualquer outro caso (nenhum, dois ou mais nos
     * sem resposta, ou outro no que falhou em alguma amostra).
     */
    public Optional<String> noSemResposta() {
        if (!comDados()) {
            return Optional.empty();
        }
        String sem = null;
        for (Map.Entry<String, No> e : porNo.entrySet()) {
            switch (e.getValue().resposta()) {
                case NENHUMA -> {
                    if (sem != null) {
                        return Optional.empty();
                    }
                    sem = e.getKey();
                }
                case ALGUMA -> {
                    return Optional.empty();
                }
                case TODAS -> { }
            }
        }
        return Optional.ofNullable(sem);
    }

    /**
     * Partição detectada (secao 5): ha dois nos que responderam com faixas de {@code number_of_nodes}
     * disjuntas na janela (por exemplo, {@code [2,2]} na maioria e {@code [1,1]} no isolado). Faixas que se
     * cruzam (a troca de vista de 3 para 2, vista em instantes um pouco diferentes por cada no) nao contam.
     */
    public boolean vistasDivergentes() {
        if (!comDados()) {
            return false;
        }
        List<No> responderam = new ArrayList<>();
        for (No no : porNo.values()) {
            if (no.respondeu()) {
                responderam.add(no);
            }
        }
        for (int i = 0; i < responderam.size(); i++) {
            for (int j = i + 1; j < responderam.size(); j++) {
                No a = responderam.get(i);
                No b = responderam.get(j);
                if (a.maiorNos() < b.menorNos() || b.maiorNos() < a.menorNos()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** O no respondeu em todas as amostras da janela. */
    public boolean respondeuEmTodas(String no) {
        No x = porNo.get(no);
        return x != null && x.resposta() == Resposta.TODAS;
    }

    /** O no respondeu em pelo menos uma amostra da janela. */
    public boolean respondeuEmAlguma(String no) {
        No x = porNo.get(no);
        return x != null && x.respondeu();
    }
}
