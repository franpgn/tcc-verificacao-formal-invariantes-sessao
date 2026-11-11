package br.unipampa.tcc.session;

import org.infinispan.client.hotrod.exceptions.HotRodTimeoutException;
import org.infinispan.client.hotrod.exceptions.TransportException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * B-33 (Q-9): classifica uma excecao de operacao Hot Rod em
 * {@link OpResult#errorUnavailable()} (o servidor recusou por indisponibilidade
 * de particao, p.ex. cache DEGRADED sob {@code DENY_READ_WRITES}) ou
 * {@link OpResult#errorTransport()} (timeout, conexao, qualquer outra falha).
 *
 * <p>E a <b>unica</b> funcao de classificacao; as sete operacoes de
 * {@link SessionOps} a usam. Regra: percorre a cadeia de causas; excecoes de
 * transporte ({@link TransportException}, {@link HotRodTimeoutException},
 * {@link IOException}, {@link UncheckedIOException}, {@link TimeoutException})
 * <b>nunca</b> promovem para indisponibilidade, mesmo que a mensagem cite
 * "not available"; as demais sao indisponibilidade se o nome da classe ou a
 * mensagem casar com {@link #MARCAS_DE_INDISPONIBILIDADE}.</p>
 *
 * <p>Uma excecao cuja cadeia de causas (abaixo dela) contem uma excecao de transporte
 * e um involucro do timeout e <b>nao</b> e avaliada pela propria mensagem: na captura real o
 * {@code entrySet} devolve {@code RuntimeException("java.net.SocketTimeoutException: ...")} com
 * causa {@code SocketTimeoutException}, e o texto do involucro repete o da causa.</p>
 *
 * <p>As marcas abaixo vem da captura real do G-4 (sonda Hot Rod, REP=2 e REP=3, 2026-10-16,
 * Infinispan 15.0.0.Final; fixtures em {@code CapturasReais}, RAST-G3-1/C-2).</p>
 */
public final class ClassificadorDeErro {

    /**
     * Trechos de nome de classe ou mensagem que indicam recusa por indisponibilidade. Fontes, todas
     * em {@code sonda-isn3.csv} (REP=3: runs/gate-b29/g1-rep3/hotrod; REP=2: g1-rep2/hotrod), coluna
     * {@code mensagem} de {@code HotRodClientException} sem causas:
     * <ul>
     *   <li>{@code AvailabilityException}: prefixo {@code org.infinispan.partitionhandling.AvailabilityException:}
     *       das duas mensagens (REP=3 linhas 18 e 20);</li>
     *   <li>{@code ISPN000306}: "Key '...' is not available. Not all owners are in this partition"
     *       (put/get/remove, REP=3 linha 18);</li>
     *   <li>{@code ISPN000305}: "Cluster is operating in degraded mode because of node failures."
     *       (entrySet, REP=3 linha 20);</li>
     *   <li>{@code degraded mode}: a mesma frase do ISPN000305;</li>
     *   <li>{@code CacheUnavailableException} (P1-IC, G-4 do coordenador de 06/11; P-A4.4): a recusa do cache
     *       ignorado pelo no ({@code POST /rest/v2/server/ignored-caches/{cache}}); o servidor Hot Rod responde com o
     *       nome da classe ({@code org.infinispan.server.hotrod.CacheUnavailableException}, captura real do A4 em
     *       {@code runs/a4-teste/}) e o cliente 15.0.0 lanca {@code HotRodClientException} com esse texto.</li>
     * </ul>
     * Removidas por nao virem da captura como excecao de cliente: {@code DEGRADED} (so aparece no
     * JSON de saude e no log do servidor) e {@code is not available} (generica; todas as capturas em
     * que aparece ja casam por {@code AvailabilityException}/{@code ISPN000306}).
     */
    static final List<String> MARCAS_DE_INDISPONIBILIDADE = List.of(
            "AvailabilityException",
            "ISPN000306",
            "ISPN000305",
            "degraded mode",
            "CacheUnavailableException");

    private ClassificadorDeErro() { /* utilitario */ }

    public static OpResult classificar(RuntimeException e) {
        return indisponivel(e) ? OpResult.errorUnavailable() : OpResult.errorTransport();
    }

    /** {@code true} se alguma causa (nao-transporte) indica indisponibilidade. */
    public static boolean indisponivel(Throwable t) {
        Set<Throwable> vistos = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable c = t; c != null && vistos.add(c); c = c.getCause()) {
            if (ehTransporte(c) || temCausaDeTransporte(c)) {
                continue;
            }
            if (casa(c.getClass().getName()) || casa(c.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean temCausaDeTransporte(Throwable t) {
        Set<Throwable> vistos = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        vistos.add(t);
        for (Throwable c = t.getCause(); c != null && vistos.add(c); c = c.getCause()) {
            if (ehTransporte(c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ehTransporte(Throwable c) {
        return c instanceof TransportException
                || c instanceof HotRodTimeoutException
                || c instanceof IOException
                || c instanceof UncheckedIOException
                || c instanceof TimeoutException;
    }

    private static boolean casa(String texto) {
        if (texto == null) {
            return false;
        }
        for (String m : MARCAS_DE_INDISPONIBILIDADE) {
            if (texto.contains(m)) {
                return true;
            }
        }
        return false;
    }
}
