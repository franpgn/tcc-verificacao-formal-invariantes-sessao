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
 * <p><b>Pendente (G-3, T-B):</b> as marcas abaixo vem do que o Infinispan documenta
 * ({@code AvailabilityException}, ISPN000306); as mensagens reais que o cliente
 * Hot Rod recebe de um no em DEGRADED serao confirmadas no gate G-3 da VM e esta
 * lista ajustada com um teste de regressao.</p>
 */
public final class ClassificadorDeErro {

    /** Trechos de nome de classe ou mensagem que indicam recusa por indisponibilidade. */
    static final List<String> MARCAS_DE_INDISPONIBILIDADE = List.of(
            "AvailabilityException",
            "ISPN000306",
            "DEGRADED",
            "degraded mode",
            "is not available");

    private ClassificadorDeErro() { /* utilitario */ }

    public static OpResult classificar(RuntimeException e) {
        return indisponivel(e) ? OpResult.errorUnavailable() : OpResult.errorTransport();
    }

    /** {@code true} se alguma causa (nao-transporte) indica indisponibilidade. */
    public static boolean indisponivel(Throwable t) {
        Set<Throwable> vistos = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable c = t; c != null && vistos.add(c); c = c.getCause()) {
            if (ehTransporte(c)) {
                continue;
            }
            if (casa(c.getClass().getName()) || casa(c.getMessage())) {
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
