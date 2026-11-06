package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1: a copia de {@link SessionState} que vai no JAR do servidor e a mesma classe do programa de carga. A politica
 * desserializa o valor gravado pelo cliente; se a copia divergir (campo, tipo ou {@code serialVersionUID}), a leitura
 * falha ou sai com campo nulo e a politica cai na saida de seguranca. Estes testes pegam a divergencia antes.
 */
class ValoresClienteTest {

    @Test
    @DisplayName("a copia serializa cada SessionState do arquivo nos mesmos bytes que o cliente gravou")
    void copiaSerializaIgualAoCliente() throws Exception {
        JavaSerializationMarshaller m = new JavaSerializationMarshaller();
        int n = 0;
        for (ValoresCliente.Linha l : ValoresCliente.LINHAS.values()) {
            if (!l.tipo().equals("SessionState")) {
                continue;
            }
            byte[] meus = m.objectToByteBuffer(ValoresCliente.sessao(l.rotulo()));
            assertArrayEquals(l.bytes(), meus, l.rotulo() + ": " + HexFormat.of().formatHex(meus));
            n++;
        }
        assertEquals(10, n, "linhas SessionState no arquivo");
    }

    @Test
    @DisplayName("os valores do arquivo comecam pelo cabecalho da serializacao Java (ACED 0005), sem prefixo")
    void cabecalhoJava() {
        for (ValoresCliente.Linha l : ValoresCliente.LINHAS.values()) {
            byte[] b = l.bytes();
            assertTrue(b.length > 4 && (b[0] & 0xff) == 0xac && (b[1] & 0xff) == 0xed && b[2] == 0 && b[3] == 5,
                    l.rotulo());
        }
    }

    @Test
    @DisplayName("serialVersionUID 1L e o mesmo nome de classe do programa de carga")
    void serialVersionUid() {
        ObjectStreamClass d = ObjectStreamClass.lookup(SessionState.class);
        assertEquals(1L, d.getSerialVersionUID());
        assertEquals("br.unipampa.tcc.session.SessionState", d.getName());
    }

    @Test
    @DisplayName("o fonte da copia e identico ao do programa de carga (fora o fim de linha)")
    void fonteIdentico() throws Exception {
        Path raiz = ValoresCliente.raizRepositorio();
        Path rel = Path.of("src", "main", "java", "br", "unipampa", "tcc", "session", "SessionState.java");
        String carga = Files.readString(raiz.resolve("workload").resolve(rel), StandardCharsets.UTF_8);
        String copia = Files.readString(raiz.resolve("extensao-servidor").resolve(rel), StandardCharsets.UTF_8);
        assertEquals(carga.replace("\r\n", "\n"), copia.replace("\r\n", "\n"));
    }
}
