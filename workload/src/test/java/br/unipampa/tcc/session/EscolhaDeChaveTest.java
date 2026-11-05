package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.CanalCanario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4, P-40 (decisao do coordenador de 05/11): a chave de cada no X e escolhida para ter o proprio X como dono
 * primario, pelo hash consistente que o cliente Hot Rod topology-aware recebe do servidor. Com isso, sob o corte de
 * um no C, os pares entre os dois nos da maioria continuam vendo o valor (o primario de {@code lag:<X>} e X, que
 * grava localmente e responde a leitura do outro), e o empate que tirava o suspeito deixa de existir. Aqui so a
 * parte pura: a busca de candidatas com uma funcao de dono primario falsa, o limite da busca e o recuo para a chave
 * simples sem topologia.
 */
class EscolhaDeChaveTest {

    static final List<String> NOS = List.of("127.0.0.1:11222", "127.0.0.1:11223", "127.0.0.1:11224");

    /** Dono primario falso: um no por chave, espalhado pelo hash do texto (como os 64 segmentos espalham). */
    static String donoFalso(String chave) {
        return NOS.get(Math.floorMod(chave.hashCode() * 31 + 7, NOS.size()));
    }

    @Test
    @DisplayName("a chave escolhida tem o proprio no como dono primario; prefixo lag:, candidatas lag:<no>#<j>")
    void escolheChaveComPrimarioNoProprioNo() {
        for (String no : NOS) {
            CanalCanarioHotRod.Chave c = CanalCanarioHotRod.escolherChave(no, EscolhaDeChaveTest::donoFalso);
            assertTrue(c.primario(), c.toString());
            assertEquals(no, donoFalso(c.chave()));
            assertTrue(c.chave().startsWith(CanalCanario.PREFIXO_CHAVE + no), c.chave());
            assertEquals("primario", c.motivo());
            if (c.tentativas() == 1) {
                assertEquals(CanalCanario.PREFIXO_CHAVE + no, c.chave(), "a chave simples, se ja servir");
            } else {
                assertEquals(CanalCanario.PREFIXO_CHAVE + no + "#" + (c.tentativas() - 1), c.chave());
            }
        }
    }

    @Test
    @DisplayName("os tres nos ganham chaves distintas, todas com primario no proprio no; a busca e deterministica")
    void tresNos() {
        List<CanalCanarioHotRod.Chave> l = CanalCanarioHotRod.escolherChaves(NOS, EscolhaDeChaveTest::donoFalso);
        assertEquals(3, l.size());
        assertEquals(3, new HashSet<>(l.stream().map(CanalCanarioHotRod.Chave::chave).toList()).size());
        assertTrue(l.stream().allMatch(CanalCanarioHotRod.Chave::primario));
        assertEquals(l, CanalCanarioHotRod.escolherChaves(NOS, EscolhaDeChaveTest::donoFalso));
    }

    @Test
    @DisplayName("sem topologia (dono desconhecido): chave simples, primario false, motivo sem-topologia, uma consulta")
    void semTopologia() {
        AtomicInteger consultas = new AtomicInteger();
        CanalCanarioHotRod.Chave c = CanalCanarioHotRod.escolherChave(NOS.get(0), k -> {
            consultas.incrementAndGet();
            return null;
        });
        assertEquals(CanalCanario.PREFIXO_CHAVE + NOS.get(0), c.chave());
        assertFalse(c.primario());
        assertEquals("sem-topologia", c.motivo());
        assertEquals(1, consultas.get());
    }

    @Test
    @DisplayName("no que nunca e primario: a busca para no limite e recua para a chave simples")
    void nuncaPrimario() {
        Function<String, String> outro = k -> NOS.get(1);
        CanalCanarioHotRod.Chave c = CanalCanarioHotRod.escolherChave(NOS.get(0), outro);
        assertEquals(CanalCanario.PREFIXO_CHAVE + NOS.get(0), c.chave());
        assertFalse(c.primario());
        assertEquals("nao-encontrada", c.motivo());
        assertEquals(CanalCanarioHotRod.LIMITE_BUSCA, c.tentativas());
    }

    @Test
    @DisplayName("limite da busca: com o no primario de ao menos 1 de 64 segmentos, a chance de nao achar e desprezivel")
    void limiteDaBusca() {
        assertEquals(10_000, CanalCanarioHotRod.LIMITE_BUSCA);
        assertTrue(Math.pow(63.0 / 64.0, CanalCanarioHotRod.LIMITE_BUSCA) < 1e-60);
    }

    @Test
    @DisplayName("endereco do dono: host:porta do InetSocketAddress (resolvido ou nao); outro tipo ou null da null")
    void hostPorta() {
        assertEquals("127.0.0.1:11222", CanalCanarioHotRod.hostPorta(InetSocketAddress.createUnresolved("127.0.0.1", 11222)));
        assertEquals("127.0.0.1:11223", CanalCanarioHotRod.hostPorta(new InetSocketAddress("127.0.0.1", 11223)));
        assertNull(CanalCanarioHotRod.hostPorta(null));
        assertNull(CanalCanarioHotRod.hostPorta(new SocketAddress() { }));
    }

    @Test
    @DisplayName("o canal expoe a chave escolhida e se ela tem primario no no; sem escolha, a chave simples e false")
    void canalExpoeAsChaves() {
        List<CanalCanarioHotRod.Alvo> alvos = List.of(new CanalCanarioHotRodTest.AlvoFalso(),
                new CanalCanarioHotRodTest.AlvoFalso(), new CanalCanarioHotRodTest.AlvoFalso());
        List<CanalCanarioHotRod.Chave> chaves = CanalCanarioHotRod.escolherChaves(NOS, EscolhaDeChaveTest::donoFalso);
        CanalCanarioHotRod c = new CanalCanarioHotRod("canario", NOS, alvos, chaves);
        for (int i = 0; i < 3; i++) {
            assertEquals(chaves.get(i).chave(), c.chave(i));
            assertEquals(Boolean.TRUE, c.chaveNoPrimario(i));
        }
        assertEquals(chaves, c.escolhas());
        CanalCanarioHotRod simples = new CanalCanarioHotRod("canario", NOS, alvos);
        for (int i = 0; i < 3; i++) {
            assertEquals(CanalCanario.PREFIXO_CHAVE + NOS.get(i), simples.chave(i));
            assertEquals(Boolean.FALSE, simples.chaveNoPrimario(i));
            assertEquals("nao-escolhida", simples.escolhas().get(i).motivo());
        }
    }
}
