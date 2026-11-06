package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-IC (06/11): guarda continua do AC-7. O smoke {@code curto-r1} (corte de 24 s, curado antes da deteccao) mostrou que
 * a gravacao da lista do alvo, que respondeu 500 sob o corte, ainda chegou a maioria 7 a 9 s depois da cura, e o
 * {@code sessions-async} ficou recusado no cluster inteiro por 11 a 13 s, ate a regra {@code suspeita-desfeita}. A guarda
 * do adaptador so olha o primeiro segundo. Enquanto houver um AC-7 ativo, esta guarda le, a cada periodo, a lista dos
 * nos diferentes do alvo (GET local, R5) e, se alguma mostrar o cache, pede o desfazer ao executor (uma vez por acao).
 * Nos primeiros {@code carenciaNs} depois de a acao aparecer ela nao age (a guarda do adaptador cobre essa janela e o
 * pedido correria com o POST).
 */
class GuardaContinuaIgnoradosTest {

    static final long MS = 1_000_000L;
    static final String CACHE = "sessions-async";

    final List<ServidorAdminFalso> nos = new ArrayList<>();
    final List<List<String>> listas = new ArrayList<>();

    @AfterEach
    void fechar() {
        nos.forEach(ServidorAdminFalso::close);
    }

    void cluster() throws Exception {
        for (int i = 0; i < 3; i++) {
            List<String> l = Collections.synchronizedList(new ArrayList<>());
            listas.add(l);
            nos.add(new ServidorAdminFalso((srv, p) -> p.metodo().equals("GET")
                    && p.caminho().equals("/rest/v2/server/ignored-caches")
                    ? ServidorAdminFalso.Resp.de(200, "[" + String.join(",", l.stream().map(x -> "\"" + x + "\"").toList()) + "]")
                    : ServidorAdminFalso.Resp.de(404, "sem rota")));
        }
    }

    List<String> urls() {
        return nos.stream().map(ServidorAdminFalso::url).toList();
    }

    AcaoCluster ac7() {
        return new AcaoCluster(AcaoCluster.Tipo.AC_7, List.of(CACHE), nos.get(2).rotulo(), null);
    }

    @Test
    @DisplayName("AC-7 ativo e a lista de um no da maioria com o cache (depois da carencia) -> pede o desfazer uma vez; so le os outros nos")
    void desfazQuandoAMaioriaMostra() throws Exception {
        cluster();
        AtomicLong relogio = new AtomicLong(0);
        List<AcaoCluster> ativas = new ArrayList<>(List.of(ac7()));
        List<AcaoCluster> desfeitas = new ArrayList<>();
        GuardaContinuaIgnorados g = new GuardaContinuaIgnorados(urls(), new ClienteAdminRest("admin", "x"),
                () -> List.copyOf(ativas), desfeitas::add, relogio::get, 2_000 * MS, 500 * MS);
        listas.get(2).add(CACHE); // so o alvo: corte verdadeiro
        assertEquals(0, g.rodada());
        relogio.set(3_000 * MS);
        assertEquals(0, g.rodada(), "so o alvo com o cache: nada");
        listas.get(1).add(CACHE); // a gravacao chegou a maioria (corte curto curado)
        relogio.set(4_000 * MS);
        assertEquals(1, g.rodada());
        assertEquals(List.of(ac7()), desfeitas);
        assertEquals(0, g.rodada(), "uma vez por acao");
        assertEquals(1, g.desfazeres());
        assertTrue(nos.get(2).pedidos().isEmpty(), "nunca le o alvo (pode estar isolado)");
        for (ServidorAdminFalso n : nos) {
            for (ServidorAdminFalso.Pedido p : n.pedidos()) {
                assertEquals("GET /rest/v2/server/ignored-caches", p.toString(), "R5: so a lista, nada de /container");
            }
        }
    }

    @Test
    @DisplayName("na carencia (guarda do adaptador em curso) nao age; sem AC-7 ativo nao le nada")
    void carenciaESemAcao() throws Exception {
        cluster();
        AtomicLong relogio = new AtomicLong(0);
        List<AcaoCluster> ativas = new ArrayList<>(List.of(ac7()));
        List<AcaoCluster> desfeitas = new ArrayList<>();
        GuardaContinuaIgnorados g = new GuardaContinuaIgnorados(urls(), new ClienteAdminRest("admin", "x"),
                () -> List.copyOf(ativas), desfeitas::add, relogio::get, 2_000 * MS, 500 * MS);
        listas.get(0).add(CACHE);
        assertEquals(0, g.rodada(), "acao recem-vista");
        relogio.set(1_900 * MS);
        assertEquals(0, g.rodada(), "ainda na carencia");
        ativas.clear();
        relogio.set(5_000 * MS);
        assertEquals(0, g.rodada());
        assertTrue(desfeitas.isEmpty());
        int pedidos = nos.stream().mapToInt(n -> n.pedidos().size()).sum();
        assertEquals(0, g.rodada());
        assertEquals(pedidos, nos.stream().mapToInt(n -> n.pedidos().size()).sum(), "sem AC-7, nenhum GET");
        ativas.add(new AcaoCluster(AcaoCluster.Tipo.AC_2, List.of(CACHE), null, null));
        assertEquals(0, g.rodada(), "AC-2 nao interessa");
    }
}
