package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.infinispan.commons.marshall.WrappedByteArray;
import org.infinispan.commons.marshall.WrappedBytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1: leitura do valor pela politica. So {@link SessionState} e os enum dele, {@code java.lang.Enum} e
 * {@code java.time.Ser} entram; qualquer outra coisa vira falha classificada (a politica remove a entrada).
 */
class LeitorSessaoTest {

    private static void ok(LeitorSessao.Leitura l, String rotulo) {
        assertTrue(l.ok(), rotulo + ": " + l.falha() + " " + l.detalhe());
        assertTrue(OrdemSessao.mesmoConteudo(ValoresCliente.sessao(rotulo), l.sessao()), rotulo);
    }

    private static void falha(LeitorSessao.Leitura l, MotivoSaida motivo) {
        assertTrue(!l.ok(), "deveria falhar com " + motivo);
        assertNull(l.sessao());
        assertEquals(motivo, l.falha(), l.detalhe());
    }

    @Test
    @DisplayName("WrappedByteArray com os bytes do cliente: le todos os SessionState do arquivo")
    void leWrappedByteArray() {
        for (ValoresCliente.Linha l : ValoresCliente.LINHAS.values()) {
            if (l.tipo().equals("SessionState")) {
                ok(LeitorSessao.ler(new WrappedByteArray(l.bytes())), l.rotulo());
            }
        }
    }

    @Test
    @DisplayName("byte[] puro (encoding protostream/octet-stream) tambem e lido")
    void leByteArray() {
        ok(LeitorSessao.ler(ValoresCliente.bytes("logout-v2")), "logout-v2");
    }

    @Test
    @DisplayName("WrappedBytes com deslocamento no vetor de apoio: le so a faixa [offset, offset+length)")
    void leComDeslocamento() {
        byte[] v = ValoresCliente.bytes("admin-v2");
        byte[] apoio = new byte[v.length + 7];
        Arrays.fill(apoio, (byte) 0x55);
        System.arraycopy(v, 0, apoio, 3, v.length);
        WrappedBytes w = new WrappedBytes() {
            @Override public byte[] getBytes() { return apoio; }
            @Override public int backArrayOffset() { return 3; }
            @Override public int getLength() { return v.length; }
            @Override public byte getByte(int offset) { return apoio[3 + offset]; }
            @Override public boolean equalsWrappedBytes(WrappedBytes other) { return false; }
        };
        ok(LeitorSessao.ler(w), "admin-v2");
    }

    @Test
    @DisplayName("objeto SessionState ja desserializado (encoding x-java-object) passa direto")
    void objetoDireto() {
        SessionState s = ValoresCliente.sessao("expirada-v2");
        assertSame(s, LeitorSessao.ler(s).sessao());
    }

    @Test
    @DisplayName("IdentityState gravado pelo cliente: tipo inesperado (classe fora da lista)")
    void identityState() {
        falha(LeitorSessao.ler(new WrappedByteArray(ValoresCliente.bytes("identidade-bloqueada"))),
                MotivoSaida.TIPO_INESPERADO);
    }

    @Test
    @DisplayName("String serializada (a chave no lugar do valor): tipo inesperado")
    void stringSerializada() {
        falha(LeitorSessao.ler(new WrappedByteArray(ValoresCliente.bytes("chave-sid"))), MotivoSaida.TIPO_INESPERADO);
    }

    @Test
    @DisplayName("valor de outro tipo Java (Integer, String): tipo inesperado")
    void outroTipo() {
        falha(LeitorSessao.ler(42), MotivoSaida.TIPO_INESPERADO);
        falha(LeitorSessao.ler("VALID"), MotivoSaida.TIPO_INESPERADO);
    }

    @Test
    @DisplayName("valor nulo, bytes que nao sao serializacao Java, fluxo truncado e bytes sobrando: ilegivel")
    void ilegivel() {
        falha(LeitorSessao.ler(null), MotivoSaida.VALOR_ILEGIVEL);
        falha(LeitorSessao.ler(new WrappedByteArray(new byte[]{1, 2, 3, 4, 5})), MotivoSaida.VALOR_ILEGIVEL);
        falha(LeitorSessao.ler(new WrappedByteArray(new byte[0])), MotivoSaida.VALOR_ILEGIVEL);
        byte[] v = ValoresCliente.bytes("logout-v2");
        falha(LeitorSessao.ler(new WrappedByteArray(Arrays.copyOf(v, v.length - 9))), MotivoSaida.VALOR_ILEGIVEL);
        byte[] sobra = Arrays.copyOf(v, v.length + 1);
        sobra[v.length] = 0x70;
        falha(LeitorSessao.ler(new WrappedByteArray(sobra)), MotivoSaida.VALOR_ILEGIVEL);
    }

    @Test
    @DisplayName("SessionState com campo nulo no fluxo (identidade = TC_NULL): ilegivel, nunca NPE na ordem")
    void campoNulo() {
        String hex = HexFormat.of().formatHex(ValoresCliente.bytes("logout-v2"));
        String identidade = "7400087333" + "2d752d303031"; // TC_STRING, 8 bytes, "s3-u-001"
        assertEquals(hex.indexOf(identidade), hex.lastIndexOf(identidade), "uma ocorrencia so");
        assertTrue(hex.indexOf(identidade) > 0);
        byte[] semIdentidade = HexFormat.of().parseHex(hex.replace(identidade, "70"));
        falha(LeitorSessao.ler(new WrappedByteArray(semIdentidade)), MotivoSaida.VALOR_ILEGIVEL);
    }

    @Test
    @DisplayName("WrappedBytes que lanca ao ler os bytes: ilegivel, sem propagar a excecao")
    void leituraQueLanca() {
        WrappedBytes w = new WrappedBytes() {
            @Override public byte[] getBytes() { throw new IllegalStateException("falha simulada"); }
            @Override public int backArrayOffset() { return 0; }
            @Override public int getLength() { return 10; }
            @Override public byte getByte(int offset) { return 0; }
            @Override public boolean equalsWrappedBytes(WrappedBytes other) { return false; }
        };
        falha(LeitorSessao.ler(w), MotivoSaida.VALOR_ILEGIVEL);
    }

    @Test
    @DisplayName("chave: String serializada pelo cliente vira a sid; outra coisa vira texto curto, sem lancar")
    void chave() {
        assertEquals("7b0c2f4e-1d3a-4e5b-9c6d-0a1b2c3d4e5f", LeitorSessao.lerChave(ValoresCliente.chave()));
        assertEquals("abc", LeitorSessao.lerChave("abc"));
        assertEquals("null", LeitorSessao.lerChave(null));
        String bruta = LeitorSessao.lerChave(new WrappedByteArray(new byte[]{1, 2, 3}));
        assertTrue(bruta.startsWith("hex:010203"), bruta);
        assertTrue(LeitorSessao.lerChave(new WrappedByteArray(ValoresCliente.bytes("logout-v2"))).startsWith("hex:"),
                "objeto que nao e String nao vira sid");
    }

    /** {@link java.io.ObjectInputFilter.FilterInfo} de teste. */
    private static java.io.ObjectInputFilter.FilterInfo info(Class<?> c, long array, long depth, long refs) {
        return new java.io.ObjectInputFilter.FilterInfo() {
            @Override public Class<?> serialClass() { return c; }
            @Override public long arrayLength() { return array; }
            @Override public long depth() { return depth; }
            @Override public long references() { return refs; }
            @Override public long streamBytes() { return 0; }
        };
    }

    @Test
    @DisplayName("filtro: aceita as classes da lista e o Instant do readResolve; recusa outra classe, vetor e excesso")
    void filtro() {
        java.io.ObjectInputFilter.Status ok = java.io.ObjectInputFilter.Status.ALLOWED;
        java.io.ObjectInputFilter.Status nao = java.io.ObjectInputFilter.Status.REJECTED;
        assertEquals(ok, LeitorSessao.filtrar(info(SessionState.class, -1, 1, 1)));
        assertEquals(ok, LeitorSessao.filtrar(info(java.time.Instant.class, -1, 2, 4)));
        assertEquals(ok, LeitorSessao.filtrar(info(null, -1, 1, 1)));
        assertEquals(nao, LeitorSessao.filtrar(info(java.util.HashMap.class, -1, 1, 1)));
        assertEquals(nao, LeitorSessao.filtrar(info(byte[].class, 10, 1, 1)));
        assertEquals(nao, LeitorSessao.filtrar(info(null, -1, LeitorSessao.MAX_PROFUNDIDADE + 1, 1)));
        assertEquals(nao, LeitorSessao.filtrar(info(null, -1, 1, LeitorSessao.MAX_REFERENCIAS + 1)));
    }

    @Test
    @DisplayName("lista de classes aceitas: so SessionState, os dois enum, java.lang.Enum e java.time.Ser")
    void listaDeClasses() {
        assertEquals(java.util.Set.of("br.unipampa.tcc.session.SessionState",
                "br.unipampa.tcc.session.SessionState$Estado", "br.unipampa.tcc.session.SessionState$Motivo",
                "java.lang.Enum", "java.time.Ser"), LeitorSessao.CLASSES_PERMITIDAS);
    }
}
