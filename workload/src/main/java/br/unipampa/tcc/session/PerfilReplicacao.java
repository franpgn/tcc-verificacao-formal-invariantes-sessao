package br.unipampa.tcc.session;

import java.util.List;
import java.util.Locale;

/**
 * B-42.1: perfil de replicacao do armazenamento e resolucao dos nomes de <i>cache</i>.
 *
 * <p>O descritor do <i>cluster</i> ({@code cluster/infinispan-cluster.xml}) declara dois pares de
 * <i>caches</i> irmaos com {@code owners}, {@code segments} e {@code partition-handling} iguais:
 * {@code sessions}/{@code counters} em {@code mode="SYNC"} (controle, todo o <i>baseline</i> F1/F3) e
 * {@code sessions-async}/{@code counters-async} em {@code mode="ASYNC"} (alvo T1). O <i>workload</i>
 * escolhe o par por {@code --replication sync|async} sem recompilar nem recriar o <i>cluster</i>
 * (plano de 2026-10-22, secao 2.4; o Gestor decidiu em 2026-10-22 que no perfil ASYNC os dois
 * <i>caches</i> ficam ASYNC).</p>
 *
 * <p>Este e o <b>unico</b> lugar do codigo de producao onde os nomes de <i>cache</i> aparecem como
 * literais; {@code NomesDeCacheSoNoResolvedorTest} falha se algum sobrar fora daqui.</p>
 */
public enum PerfilReplicacao {

    /** Controle: {@code mode="SYNC"}; nomes historicos, identicos ao <i>baseline</i>. */
    SYNC(""),

    /** Alvo: {@code mode="ASYNC"}; caches irmaos com sufixo {@code -async}. */
    ASYNC("-async");

    static final String BASE_SESSOES = "sessions";
    static final String BASE_CONTADORES = "counters";
    /** W-4 (B-26.3): base do <i>cache</i> da sonda de defasagem ({@code canario}, {@code canario-async}). */
    static final String BASE_CANARIO = "canario";

    private final String sufixo;

    PerfilReplicacao(String sufixo) {
        this.sufixo = sufixo;
    }

    /** Nome do <i>cache</i> de sessoes (I1-I3, I5) neste perfil. */
    public String cacheSessoes() {
        return BASE_SESSOES + sufixo;
    }

    /** Nome do <i>cache</i> de identidades/contadores (I4, I6) neste perfil. */
    public String cacheContadores() {
        return BASE_CONTADORES + sufixo;
    }

    /**
     * W-4 (B-26.3): <i>cache</i> da sonda de defasagem (S_lag) neste perfil, com o mesmo modo dos <i>caches</i>
     * da carga ({@code canario} SYNC, {@code canario-async} ASYNC). Criado pela REST a partir de
     * {@code cluster/caches/<nome>.json} ({@code scripts/cria-caches.sh}), nao pelo XML. Nao entra em
     * {@link #caches()}: nenhum invariante, auditor, varredura, reset ou sonda de saude o usa; so o adaptador
     * {@code CanalCanarioHotRod} ({@code CanarioArquiteturaTest}).
     */
    public String cacheCanario() {
        return BASE_CANARIO + sufixo;
    }

    /** Os dois nomes deste perfil, sessoes primeiro. */
    public List<String> caches() {
        return List.of(cacheSessoes(), cacheContadores());
    }

    /** Rotulo da CLI ({@code sync} ou {@code async}). */
    public String rotulo() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Descricao de uma linha para {@code --dry-run} e para o cabecalho da execucao. */
    public String descricao() {
        return "replicacao=" + rotulo() + " caches=" + String.join(",", caches());
    }

    /**
     * Converte o valor da CLI. Aceita exatamente {@code sync} ou {@code async} (sem distinguir
     * maiusculas); qualquer outro valor e rejeitado.
     *
     * @throws IllegalArgumentException se o valor for nulo ou desconhecido
     */
    public static PerfilReplicacao porRotulo(String valor) {
        if (valor != null) {
            for (PerfilReplicacao p : values()) {
                if (p.rotulo().equalsIgnoreCase(valor.trim())) {
                    return p;
                }
            }
        }
        throw new IllegalArgumentException(
                "valor invalido '" + valor + "' para --replication (aceitos: sync, async)");
    }

    /** {@code true} se {@code nome} e o <i>cache</i> de sessoes de algum perfil. */
    public static boolean ehCacheDeSessoes(String nome) {
        for (PerfilReplicacao p : values()) {
            if (p.cacheSessoes().equals(nome)) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} se {@code nome} e o <i>cache</i> de contadores de algum perfil. */
    public static boolean ehCacheDeContadores(String nome) {
        for (PerfilReplicacao p : values()) {
            if (p.cacheContadores().equals(nome)) {
                return true;
            }
        }
        return false;
    }
}
