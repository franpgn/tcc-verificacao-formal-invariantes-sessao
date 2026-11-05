package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-W4 R1 (decisao do coordenador em nome do Gestor, 05/11): o recuo para a chave simples e <b>fatal</b> em toda
 * execucao com o bloco {@code defasagem}, nos dois bracos, na calibracao e na avaliacao, na abertura. A funcao pura
 * {@link CanalCanarioHotRod#exigirPrimario} recusa a lista em que algum no nao ficou com uma chave de primario no
 * proprio no, com o rotulo e o motivo de cada um; {@link CanalCanarioHotRod#montar} fecha os clientes antes de
 * lancar; {@link CanalCanarioHotRod#abrir} passa por ela.
 */
class ExigirPrimarioTest {

    static final List<String> NOS = List.of("127.0.0.1:11222", "127.0.0.1:11223", "127.0.0.1:11224");

    static CanalCanarioHotRod.Chave primaria(int i) {
        return new CanalCanarioHotRod.Chave("lag:" + NOS.get(i) + "#" + i, true, i + 1, "primario");
    }

    @Test
    @DisplayName("todas com primario no proprio no: passa")
    void todasPrimarias() {
        CanalCanarioHotRod.exigirPrimario(NOS, List.of(primaria(0), primaria(1), primaria(2)));
    }

    @Test
    @DisplayName("um no sem primario (qualquer motivo) recusa, citando o rotulo e o motivo de cada no recusado")
    void recusaCadaMotivo() {
        for (String motivo : List.of("sem-topologia", "fora-da-topologia", "nao-encontrada", "nao-escolhida",
                "falha:TransportException")) {
            CanalCanarioHotRod.Chave ruim = new CanalCanarioHotRod.Chave("lag:" + NOS.get(1), false, 0, motivo);
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> CanalCanarioHotRod.exigirPrimario(NOS, List.of(primaria(0), ruim, primaria(2))));
            assertTrue(e.getMessage().contains(NOS.get(1)) && e.getMessage().contains(motivo), e.getMessage());
            assertTrue(!e.getMessage().contains(NOS.get(0)) && !e.getMessage().contains(NOS.get(2)), e.getMessage());
        }
        List<CanalCanarioHotRod.Chave> todas = CanalCanarioHotRod.chavesSimples(NOS, "fora-da-topologia");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CanalCanarioHotRod.exigirPrimario(NOS, todas));
        for (String no : NOS) {
            assertTrue(e.getMessage().contains(no), e.getMessage());
        }
    }

    @Test
    @DisplayName("listas de tamanhos diferentes sao recusadas")
    void tamanhos() {
        assertThrows(IllegalArgumentException.class,
                () -> CanalCanarioHotRod.exigirPrimario(NOS, List.of(primaria(0), primaria(1))));
    }

    @Test
    @DisplayName("montar: com recuo, fecha os tres clientes e lanca; sem recuo, devolve o canal aberto")
    void montarFechaOsClientes() {
        CanalCanarioHotRodTest.AlvoFalso a0 = new CanalCanarioHotRodTest.AlvoFalso();
        CanalCanarioHotRodTest.AlvoFalso a1 = new CanalCanarioHotRodTest.AlvoFalso();
        CanalCanarioHotRodTest.AlvoFalso a2 = new CanalCanarioHotRodTest.AlvoFalso();
        List<CanalCanarioHotRod.Alvo> alvos = List.of(a0, a1, a2);
        assertThrows(IllegalStateException.class, () -> CanalCanarioHotRod.montar("canario-async", NOS, alvos,
                CanalCanarioHotRod.chavesSimples(NOS, "sem-topologia")));
        assertEquals(1, a0.fechado.get());
        assertEquals(1, a1.fechado.get());
        assertEquals(1, a2.fechado.get());

        CanalCanarioHotRodTest.AlvoFalso b0 = new CanalCanarioHotRodTest.AlvoFalso();
        CanalCanarioHotRod c = CanalCanarioHotRod.montar("canario-async", NOS,
                List.of(b0, new CanalCanarioHotRodTest.AlvoFalso(), new CanalCanarioHotRodTest.AlvoFalso()),
                List.of(primaria(0), primaria(1), primaria(2)));
        assertNotNull(c);
        assertEquals(0, b0.fechado.get());
        assertEquals("lag:" + NOS.get(2) + "#2", c.chave(2));
        assertSame(Boolean.TRUE, c.chaveNoPrimario(0));
    }

    @Test
    @DisplayName("abrir passa por montar, e montar por exigirPrimario (varredura da fonte, sem comentarios)")
    void abrirExigePrimario() throws IOException {
        Path f = NomesDeCacheSoNoResolvedorTest.raizMain().resolve("java/br/unipampa/tcc/session/CanalCanarioHotRod.java");
        String m = DetectorFonte.mascarado(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        int[] abrir = DetectorFonte.corpo(m, "static CanalCanarioHotRod abrir\\(Cli cli, List<String> urlsAdmin\\)");
        assertNotNull(abrir, "abrir nao encontrado");
        assertTrue(m.substring(abrir[0], abrir[1]).contains("montar("), "abrir nao chama montar");
        int[] montar = DetectorFonte.corpo(m, "static CanalCanarioHotRod montar\\(");
        assertNotNull(montar, "montar nao encontrado");
        assertTrue(m.substring(montar[0], montar[1]).contains("exigirPrimario("), "montar nao chama exigirPrimario");
    }
}
