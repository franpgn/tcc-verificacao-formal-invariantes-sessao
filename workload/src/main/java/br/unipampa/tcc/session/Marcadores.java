package br.unipampa.tcc.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * B-33 (E1, item 2): marcadores de falha (formato B-29/B-29.1) e rotulo de fase
 * de cada instante.
 *
 * <p>Cada linha do arquivo tem a forma
 * {@code fault=F2 node=isn3 event=start|split|end t_epoch_ns=<n>} (pares
 * {@code chave=valor} separados por espaco; linhas vazias e iniciadas por
 * {@code #} sao ignoradas). {@code start} e {@code end} sao escritos pelo
 * injetor; {@code split} (instante em que a vista se dividiu, medido em G-3/G-4)
 * e opcional.</p>
 *
 * <p>Fases ({@link Fase}): {@code antes} (antes do start), {@code durante-pre-split}
 * (do start ao split), {@code durante-pos-split} (do split ao end),
 * {@code recuperacao} (do end ate o fim da janela W_recovery ou ate a
 * convergencia, o que ocorrer depois), {@code pos-convergencia}. Sem marcador de
 * split, o intervalo todo e rotulado {@code durante}, sem afirmar pre/pos split.
 * Fronteiras: o intervalo "durante" e fechado em ambas as pontas; "recuperacao"
 * e aberto em t_fim. Execucao sem marcador de inicio (sem falha) e toda
 * {@code antes}. Imutavel.</p>
 */
public final class Marcadores {

    public enum Fase {
        ANTES("antes"),
        DURANTE("durante"),
        DURANTE_PRE_SPLIT("durante-pre-split"),
        DURANTE_POS_SPLIT("durante-pos-split"),
        RECUPERACAO("recuperacao"),
        POS_CONVERGENCIA("pos-convergencia");

        private final String rotulo;

        Fase(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    private final Long inicioNs;
    private final Long splitNs;
    private final Long fimNs;
    private final long wRecoveryNs;
    private final Long convergenciaNs;
    private final boolean divergenciaPersistente;

    private Marcadores(Long inicioNs, Long splitNs, Long fimNs, long wRecoveryNs,
                       Long convergenciaNs, boolean divergenciaPersistente) {
        this.inicioNs = inicioNs;
        this.splitNs = splitNs;
        this.fimNs = fimNs;
        this.wRecoveryNs = wRecoveryNs;
        this.convergenciaNs = convergenciaNs;
        this.divergenciaPersistente = divergenciaPersistente;
    }

    public static Marcadores vazio() {
        return new Marcadores(null, null, null, 0L, null, false);
    }

    /** Le o arquivo; arquivo inexistente equivale a marcadores vazios (falha ainda nao iniciada). */
    public static Marcadores ler(Path arquivo) throws IOException {
        if (!Files.exists(arquivo)) {
            return vazio();
        }
        return parse(Files.readAllLines(arquivo));
    }

    public static Marcadores parse(List<String> linhas) {
        Long ini = null;
        Long spl = null;
        Long fim = null;
        for (String bruta : linhas) {
            String linha = bruta.trim();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }
            String evento = null;
            Long t = null;
            for (String tok : linha.split("\\s+")) {
                int eq = tok.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("linha de marcador invalida: " + linha);
                }
                String k = tok.substring(0, eq);
                String v = tok.substring(eq + 1);
                if (k.equals("event")) {
                    evento = v;
                } else if (k.equals("t_epoch_ns")) {
                    try {
                        t = Long.parseLong(v);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("t_epoch_ns invalido: " + linha, e);
                    }
                }
            }
            if (evento == null || t == null) {
                throw new IllegalArgumentException("marcador sem event/t_epoch_ns: " + linha);
            }
            switch (evento) {
                case "start" -> { if (ini == null) ini = t; }
                case "split" -> { if (spl == null) spl = t; }
                case "end" -> fim = t;
                default -> throw new IllegalArgumentException("evento de marcador desconhecido: " + linha);
            }
        }
        return new Marcadores(ini, spl, fim, 0L, null, false);
    }

    public Long inicioNs() { return inicioNs; }
    public Long splitNs() { return splitNs; }
    public Long fimNs() { return fimNs; }
    public Long convergenciaNs() { return convergenciaNs; }

    public Marcadores comRecuperacao(long wRecoveryNs) {
        return new Marcadores(inicioNs, splitNs, fimNs, wRecoveryNs, convergenciaNs, divergenciaPersistente);
    }

    /** Instante absoluto (epoch-ns) em que a convergencia foi atingida. */
    public Marcadores comConvergencia(long tConvergenciaNs) {
        return new Marcadores(inicioNs, splitNs, fimNs, wRecoveryNs, tConvergenciaNs, false);
    }

    public Marcadores comDivergenciaPersistente() {
        return new Marcadores(inicioNs, splitNs, fimNs, wRecoveryNs, null, true);
    }

    public Fase faseDe(long tNs) {
        if (inicioNs == null || tNs < inicioNs) {
            return Fase.ANTES;
        }
        if (fimNs == null || tNs <= fimNs) {
            if (splitNs == null) {
                return Fase.DURANTE;
            }
            return tNs < splitNs ? Fase.DURANTE_PRE_SPLIT : Fase.DURANTE_POS_SPLIT;
        }
        if (divergenciaPersistente) {
            return Fase.RECUPERACAO;
        }
        long limite = fimNs + wRecoveryNs;
        if (convergenciaNs != null) {
            limite = Math.max(limite, convergenciaNs);
        }
        return tNs <= limite ? Fase.RECUPERACAO : Fase.POS_CONVERGENCIA;
    }

    public String rotuloDe(long tNs) {
        return faseDe(tNs).rotulo();
    }
}
