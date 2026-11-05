package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-4 (B-26.3): o adaptador Hot Rod do S_lag ({@link CanalCanarioHotRod}). O rotulo de cada no e o rotulo canonico
 * {@code host:porta} da sonda de topologia e do atuador ({@link RotulosDosNosTest#rotuloDoNo}, requisito do W-4 no
 * contrato JSONL), o no {@code i} de {@code --servers} tem de ser o de {@code --controller-admin}, e cada operacao
 * vai ao cliente do seu no. Sem <i>cluster</i>: os alvos sao falsos.
 */
class CanalCanarioHotRodTest {

    static final String[] FORMAS_ADMIN = {
            "127.0.0.1:12501,127.0.0.1:12502,127.0.0.1:12503",
            "http://127.0.0.1:12501/, http://127.0.0.1:12502 ,127.0.0.1:12503",
            "isn1:11222,isn2:11222,isn3:11222",
            "http://localhost:11222,http://localhost:11223,http://localhost:11224/"};

    /** Alvo falso: um mapa, com contagem de fechamentos. */
    static final class AlvoFalso implements CanalCanarioHotRod.Alvo {
        final Map<String, Object> dados = new HashMap<>();
        final List<String> operacoes = new ArrayList<>();
        final AtomicInteger fechado = new AtomicInteger();

        @Override
        public void put(String chave, String valor) {
            operacoes.add("put " + chave + "=" + valor);
            dados.put(chave, valor);
        }

        @Override
        public Object get(String chave) {
            operacoes.add("get " + chave);
            return dados.get(chave);
        }

        @Override
        public void fechar() {
            fechado.incrementAndGet();
        }
    }

    @Test
    @DisplayName("noSuspeito usa o rotulo canonico: canal = RotulosDosNosTest.rotuloDoNo = sonda de topologia = atuador")
    void rotuloCanonico() {
        for (String admin : FORMAS_ADMIN) {
            List<String> urls = LigacaoControlador.urlsAdmin(admin);
            List<String> doCanal = CanalCanarioHotRod.rotulos(urls);
            List<String> canonicos = new ArrayList<>();
            List<String> daSonda = new ArrayList<>();
            ConsultaSaudeRest sonda = new ConsultaSaudeRest(urls, "admin", "x");
            for (int i = 0; i < urls.size(); i++) {
                canonicos.add(RotulosDosNosTest.rotuloDoNo(urls.get(i)));
                daSonda.add(sonda.rotulo(i));
            }
            assertEquals(canonicos, doCanal, "canal x rotulo canonico para " + admin);
            assertEquals(daSonda, doCanal, "canal x sonda de topologia para " + admin);
            assertEquals(new AtuadorRestInfinispan(urls, new ClienteAdminRest("admin", "x"), Set::of, 1).rotulos(),
                    doCanal, "canal x atuador para " + admin);
            List<CanalCanarioHotRod.Alvo> alvos = List.of(new AlvoFalso(), new AlvoFalso(), new AlvoFalso());
            CanalCanarioHotRod c = new CanalCanarioHotRod("canario", doCanal, alvos);
            for (int i = 0; i < 3; i++) {
                assertEquals(canonicos.get(i), c.rotulo(i));
            }
        }
    }

    @Test
    @DisplayName("--servers e --controller-admin: mesmo host:porta no mesmo indice; trocado, outra porta ou outro tamanho e recusado")
    void correspondencia() {
        List<String> urls = LigacaoControlador.urlsAdmin("127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224");
        CanalCanarioHotRod.conferirCorrespondencia(urls, "127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224");
        CanalCanarioHotRod.conferirCorrespondencia(urls, " 127.0.0.1:11222 ,127.0.0.1:11223, 127.0.0.1:11224");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CanalCanarioHotRod.conferirCorrespondencia(urls, "127.0.0.1:11223,127.0.0.1:11222,127.0.0.1:11224"));
        assertTrue(e.getMessage().contains("no 1"), e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> CanalCanarioHotRod.conferirCorrespondencia(urls, "127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:12224"));
        assertThrows(IllegalArgumentException.class,
                () -> CanalCanarioHotRod.conferirCorrespondencia(urls, "127.0.0.1:11222,127.0.0.1:11223"));
    }

    @Test
    @DisplayName("cada operacao vai ao cliente do seu no; ler devolve o texto ou null; fechado recusa; close idempotente")
    void operacoesPorNo() {
        AlvoFalso a0 = new AlvoFalso();
        AlvoFalso a1 = new AlvoFalso();
        AlvoFalso a2 = new AlvoFalso();
        CanalCanarioHotRod c = new CanalCanarioHotRod("canario-async", List.of("h:1", "h:2", "h:3"),
                List.of(a0, a1, a2));
        assertEquals("canario-async", c.nomeCache());
        assertEquals(3, c.nos());
        c.escrever(1, "lag:h:2", "n:1");
        assertEquals(List.of("put lag:h:2=n:1"), a1.operacoes);
        assertTrue(a0.operacoes.isEmpty() && a2.operacoes.isEmpty());
        a2.dados.put("lag:h:2", "n:0");
        assertEquals("n:0", c.ler(2, "lag:h:2"));
        assertNull(c.ler(0, "lag:h:2"));
        a0.dados.put("x", 42);
        assertEquals("42", c.ler(0, "x"));
        c.close();
        c.close();
        assertEquals(1, a0.fechado.get());
        assertEquals(1, a1.fechado.get());
        assertEquals(1, a2.fechado.get());
        assertThrows(IllegalStateException.class, () -> c.escrever(0, "k", "v"));
        assertThrows(IllegalStateException.class, () -> c.ler(0, "k"));
    }

    @Test
    @DisplayName("construtor: menos de 2 nos, rotulo sem alvo e rotulo repetido sao recusados")
    void construtor() {
        assertThrows(IllegalArgumentException.class,
                () -> new CanalCanarioHotRod("canario", List.of("a"), List.of(new AlvoFalso())));
        assertThrows(IllegalArgumentException.class,
                () -> new CanalCanarioHotRod("canario", List.of("a", "b"), List.of(new AlvoFalso())));
        assertThrows(IllegalArgumentException.class,
                () -> new CanalCanarioHotRod("canario", List.of("a", "a"), List.of(new AlvoFalso(), new AlvoFalso())));
    }
}
