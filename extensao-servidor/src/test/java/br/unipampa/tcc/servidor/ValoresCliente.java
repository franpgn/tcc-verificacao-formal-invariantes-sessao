package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.infinispan.commons.marshall.WrappedByteArray;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.ImmortalCacheEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A1: os valores de {@code valores-cliente.tsv}, gravados como o cliente Hot Rod do programa de carga grava
 * (JavaSerializationMarshaller do infinispan-commons 15.0.0.Final), e as entradas de cache que a resolucao de
 * conflitos entregaria a politica: chave e valor em {@link WrappedByteArray} ({@code application/unknown}).
 */
final class ValoresCliente {

    /** Uma linha do arquivo. */
    record Linha(String rotulo, String tipo, Map<String, String> campos, byte[] bytes) { }

    static final Map<String, Linha> LINHAS = carregar();

    /**
     * Rotulos de mesma sid, do menor ao maior na ordem de conteudo do modelo, escrita a mao (oraculo dos testes):
     * (estado, motivo, versao, atualizadaEm), com VALID &lt; INVALID e NONE &lt; LOGOUT &lt; EXPIRADA &lt; ADMIN_BLOCK.
     * {@code valid-v1-outra-identidade} empata com {@code valid-v1} e fica fora desta lista.
     */
    static final List<String> ORDEM = List.of("valid-v1", "valid-v1-tardia", "valid-v5", "logout-v2",
            "logout-v2-tardia", "logout-v3", "expirada-v2", "admin-v2");

    /** Empata com {@code valid-v1} em (estado, motivo, versao, atualizadaEm), com outra identidade. */
    static final String EMPATE = "valid-v1-outra-identidade";

    private ValoresCliente() { }

    private static Map<String, Linha> carregar() {
        Map<String, Linha> m = new LinkedHashMap<>();
        try (InputStream in = ValoresCliente.class.getResourceAsStream("valores-cliente.tsv")) {
            if (in == null) {
                throw new IllegalStateException("valores-cliente.tsv ausente");
            }
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (l.isBlank() || l.startsWith("#")) {
                    continue;
                }
                String[] c = l.split("\t");
                if (c.length != 4) {
                    throw new IllegalStateException("linha com " + c.length + " colunas: " + l);
                }
                Map<String, String> campos = new LinkedHashMap<>();
                for (String kv : c[2].split(",")) {
                    String[] p = kv.split("=", 2);
                    campos.put(p[0], p[1]);
                }
                m.put(c[0], new Linha(c[0], c[1], campos, HexFormat.of().parseHex(c[3])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return m;
    }

    static Linha linha(String rotulo) {
        Linha l = LINHAS.get(rotulo);
        if (l == null) {
            throw new IllegalArgumentException("rotulo desconhecido: " + rotulo);
        }
        return l;
    }

    static byte[] bytes(String rotulo) {
        return linha(rotulo).bytes().clone();
    }

    /**
     * O objeto da linha, construido com a copia de {@link SessionState} deste modulo. Com {@code criadaEm} igual a
     * {@code atualizadaEm}, usa o MESMO {@link Instant} nos dois campos, como {@code SessionState.criar} do cliente:
     * a serializacao Java grava o segundo como referencia ao primeiro, e os bytes so batem assim.
     */
    static SessionState sessao(String rotulo) {
        Map<String, String> c = linha(rotulo).campos();
        Instant criada = Instant.parse(c.get("criadaEm"));
        Instant atualizada = c.get("atualizadaEm").equals(c.get("criadaEm")) ? criada : Instant.parse(c.get("atualizadaEm"));
        return new SessionState(c.get("id"), c.get("identidade"), SessionState.Estado.valueOf(c.get("estado")),
                SessionState.Motivo.valueOf(c.get("motivo")), Long.parseLong(c.get("versao")), criada, atualizada);
    }

    /** Chave da sid da linha, como o cliente a grava (String serializada). */
    static WrappedByteArray chave() {
        return new WrappedByteArray(bytes("chave-sid"));
    }

    /** Entrada nova (instancia propria) com o valor da linha, como a CR a entregaria. */
    static CacheEntry<Object, Object> entrada(String rotulo) {
        return new ImmortalCacheEntry(chave(), new WrappedByteArray(bytes(rotulo)));
    }

    /** Entrada com um valor qualquer (para os casos da saida de seguranca). */
    static CacheEntry<Object, Object> entradaCom(Object valor) {
        return new ImmortalCacheEntry(chave(), valor);
    }

    /** Raiz do repositorio (o diretorio que tem {@code workload/} e {@code extensao-servidor/}). */
    static Path raizRepositorio() {
        Path d = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && d != null; i++, d = d.getParent()) {
            if (Files.isDirectory(d.resolve("workload")) && Files.isDirectory(d.resolve("extensao-servidor"))) {
                return d;
            }
        }
        throw new IllegalStateException("raiz do repositorio nao encontrada a partir de " + System.getProperty("user.dir"));
    }
}
