package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.infinispan.commons.marshall.WrappedBytes;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

/**
 * A1: leitura do valor que a resolucao de conflitos entrega a politica. Os caches nao declaram {@code encoding}
 * (armazenamento {@code application/unknown}), entao o valor chega como {@link WrappedBytes} com os bytes que o
 * cliente Hot Rod gravou: a serializacao Java de {@link SessionState} feita pelo {@code JavaSerializationMarshaller}
 * (um {@code ObjectOutputStream} comum). A leitura so serve para decidir; a politica devolve a entrada original.
 *
 * <p>Desserializacao restrita: {@link ObjectInputStream} que resolve as classes pelo carregador deste JAR e aceita so
 * {@link #CLASSES_PERMITIDAS} (o resto e recusado antes de qualquer objeto ser criado), com limite de tamanho,
 * profundidade e referencias, e sem vetores nem proxies. Nunca lanca: toda falha vira uma {@link Leitura} com o
 * {@link MotivoSaida}.</p>
 */
final class LeitorSessao {

    /** Unicas classes aceitas no fluxo: o registro de sessao, os dois enum dele e o que eles e o Instant puxam. */
    static final Set<String> CLASSES_PERMITIDAS = Set.of(
            SessionState.class.getName(),
            SessionState.Estado.class.getName(),
            SessionState.Motivo.class.getName(),
            "java.lang.Enum",
            "java.time.Ser");

    /**
     * Classes que o filtro da JVM tambem confere sem estarem no fluxo: o resultado do {@code readResolve} de
     * {@code java.time.Ser} (o filtro e chamado de novo com a classe do objeto devolvido).
     */
    static final Set<String> RESULTADOS_PERMITIDOS = Set.of("java.time.Instant");

    /** Um SessionState serializado tem ~550 bytes; nada legitimo passa de 16 KiB. */
    static final int MAX_BYTES = 16 * 1024;
    static final long MAX_PROFUNDIDADE = 8;
    static final long MAX_REFERENCIAS = 100;

    /** Resultado da leitura: a sessao, ou o motivo da falha com um detalhe para o log. */
    record Leitura(SessionState sessao, MotivoSaida falha, String detalhe) {
        boolean ok() {
            return sessao != null;
        }

        static Leitura de(SessionState s) {
            return new Leitura(s, null, null);
        }

        static Leitura falhou(MotivoSaida m, String detalhe) {
            return new Leitura(null, m, detalhe);
        }
    }

    private LeitorSessao() { }

    /** Le o valor de uma entrada; nunca lanca. */
    static Leitura ler(Object valor) {
        try {
            if (valor == null) {
                return Leitura.falhou(MotivoSaida.VALOR_ILEGIVEL, "valor nulo");
            }
            if (valor instanceof SessionState s) {
                return validar(s);
            }
            byte[] b = bytes(valor);
            if (b == null) {
                return Leitura.falhou(MotivoSaida.TIPO_INESPERADO, "valor do tipo " + valor.getClass().getName());
            }
            return desserializar(b);
        } catch (RuntimeException e) {
            return Leitura.falhou(MotivoSaida.VALOR_ILEGIVEL, e.toString());
        }
    }

    /** Os bytes de um {@link WrappedBytes} (so a faixa valida) ou de um {@code byte[]}; {@code null} se for outra coisa. */
    static byte[] bytes(Object valor) {
        if (valor instanceof WrappedBytes w) {
            int ini = w.backArrayOffset();
            return Arrays.copyOfRange(w.getBytes(), ini, ini + w.getLength());
        }
        if (valor instanceof byte[] a) {
            return a;
        }
        return null;
    }

    private static Leitura desserializar(byte[] b) {
        if (b.length > MAX_BYTES) {
            return Leitura.falhou(MotivoSaida.VALOR_ILEGIVEL, "valor com " + b.length + " bytes");
        }
        Object o;
        try (Entrada in = new Entrada(new ByteArrayInputStream(b))) {
            o = in.readObject();
            if (!fimDoFluxo(in)) {
                return Leitura.falhou(MotivoSaida.VALOR_ILEGIVEL, "bytes sobrando depois do objeto");
            }
        } catch (InvalidClassException | ClassNotFoundException e) {
            return Leitura.falhou(MotivoSaida.TIPO_INESPERADO, e.toString());
        } catch (IOException | RuntimeException e) {
            return Leitura.falhou(MotivoSaida.VALOR_ILEGIVEL, e.toString());
        }
        if (!(o instanceof SessionState s)) {
            return Leitura.falhou(MotivoSaida.TIPO_INESPERADO, o == null ? "objeto nulo" : o.getClass().getName());
        }
        return validar(s);
    }

    /** Depois do objeto, o fluxo tem de acabar (um segundo {@code readObject} da EOF). */
    private static boolean fimDoFluxo(ObjectInputStream in) {
        try {
            in.readObject();
            return false;
        } catch (EOFException fim) {
            return true;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /** A desserializacao nao passa pelo construtor: campos nulos sao conferidos aqui. */
    private static Leitura validar(SessionState s) {
        String nulo = s.id() == null ? "id" : s.identidade() == null ? "identidade" : s.estado() == null ? "estado"
                : s.motivo() == null ? "motivo" : s.criadaEm() == null ? "criadaEm"
                : s.atualizadaEm() == null ? "atualizadaEm" : null;
        return nulo == null ? Leitura.de(s) : Leitura.falhou(MotivoSaida.VALOR_ILEGIVEL, "campo nulo: " + nulo);
    }

    /**
     * A chave como texto para o log: a sid quando e uma {@code String} serializada (como o cliente grava), senao
     * {@code hex:} com os primeiros bytes. Nunca lanca.
     */
    static String lerChave(Object chave) {
        try {
            if (chave == null) {
                return "null";
            }
            if (chave instanceof String s) {
                return s;
            }
            byte[] b = bytes(chave);
            if (b == null) {
                return String.valueOf(chave);
            }
            if (b.length <= MAX_BYTES) {
                try (Entrada in = new Entrada(new ByteArrayInputStream(b))) {
                    Object o = in.readObject();
                    if (o instanceof String s) {
                        return s;
                    }
                } catch (IOException | ClassNotFoundException | RuntimeException e) {
                    // cai no hexadecimal
                }
            }
            int n = Math.min(b.length, 32);
            return "hex:" + HexFormat.of().formatHex(b, 0, n) + (b.length > n ? "..." : "");
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** Filtro de limites (o {@link Entrada#resolveClass} ja barra as classes fora da lista). */
    static ObjectInputFilter.Status filtrar(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > MAX_PROFUNDIDADE || info.references() > MAX_REFERENCIAS || info.arrayLength() >= 0) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> c = info.serialClass();
        if (c == null) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return CLASSES_PERMITIDAS.contains(c.getName()) || RESULTADOS_PERMITIDOS.contains(c.getName())
                ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
    }

    /** {@link ObjectInputStream} restrito, com as classes resolvidas pelo carregador deste JAR. */
    private static final class Entrada extends ObjectInputStream {
        Entrada(InputStream in) throws IOException {
            super(in);
            try {
                setObjectInputFilter(LeitorSessao::filtrar);
            } catch (IllegalStateException | SecurityException e) {
                // filtro global da JVM ja fixado: o resolveClass abaixo continua aceitando so a lista
            }
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String nome = desc.getName();
            if (!CLASSES_PERMITIDAS.contains(nome)) {
                throw new InvalidClassException(nome, "classe fora da lista da politica de fusao A1");
            }
            return Class.forName(nome, false, LeitorSessao.class.getClassLoader());
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
            throw new InvalidClassException("proxy", "proxy recusado pela politica de fusao A1");
        }
    }
}
