package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.AcaoCluster;
import br.unipampa.tcc.controle.ModoAtuacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-IC (G-4 do coordenador, 06/11): o sensor do controlador desconta os erros vindos do no sob a acao AC-7. Com o
 * cache de sessoes ignorado, o servidor de aplicacao fixado no alvo erra quase toda operacao (A4: 147 de 147 do pedido
 * a deteccao), e esses erros sao efeito da propria acao, nao sinal do <i>cluster</i>. O no sob acao e o alvo de um
 * AC-7 em {@code ativas()} do executor; o rotulo do alvo e o da REST ({@code --controller-admin}) e o da carga e o do
 * Hot Rod ({@code --servers}), ligados pela posicao (a mesma correspondencia que o {@code carregar} confere). So em
 * active: em shadow nada e aplicado, e o braco A fica identico ao de antes.
 */
class LigacaoControladorDescontoTest {

    static final String ADMIN3 = "127.0.0.1:12903";
    static final String HOTROD3 = "127.0.0.1:11224";

    static final Map<String, String> MAPA = LigacaoControlador.servidorPorAdmin(
            List.of("http://127.0.0.1:12901", "http://127.0.0.1:12902", "http://" + ADMIN3),
            "127.0.0.1:11222, 127.0.0.1:11223,127.0.0.1:11224");

    static AcaoCluster ac7(String alvo) {
        return new AcaoCluster(AcaoCluster.Tipo.AC_7, List.of("sessions-async"), alvo, null);
    }

    @Test
    @DisplayName("mapa admin -> servidor pela posicao; tamanhos diferentes sao recusados")
    void mapa() {
        assertEquals(HOTROD3, MAPA.get(ADMIN3));
        assertEquals("127.0.0.1:11223", MAPA.get("127.0.0.1:12902"));
        assertThrows(IllegalArgumentException.class, () -> LigacaoControlador.servidorPorAdmin(
                List.of("http://a:1", "http://b:2"), "a:3"));
    }

    @Test
    @DisplayName("active com AC-7 no isn3: erros do servidor do isn3 descontados e contados; sucessos e outros nos nao")
    void descontaSoErrosDoAlvo() {
        List<AcaoCluster> ativas = new ArrayList<>(List.of(ac7(ADMIN3)));
        LigacaoControlador.DescontoSobAcao d = new LigacaoControlador.DescontoSobAcao(ModoAtuacao.ACTIVE, MAPA,
                () -> List.copyOf(ativas));
        assertTrue(d.descontar(HOTROD3, "ERROR_UNAVAILABLE"));
        assertTrue(d.descontar(HOTROD3, "ERROR_TRANSPORT"));
        assertFalse(d.descontar(HOTROD3, "VALID"), "sucesso do alvo continua contando");
        assertFalse(d.descontar("127.0.0.1:11222", "ERROR_UNAVAILABLE"), "erro de outro no continua contando");
        assertFalse(d.descontar(null, "ERROR_UNAVAILABLE"), "sem replica: nao desconta");
        assertEquals(2, d.descontados());
        ativas.clear(); // desfeita: os erros do alvo voltam a contar (DEGRADED depois da deteccao e sinal real)
        assertFalse(d.descontar(HOTROD3, "ERROR_UNAVAILABLE"));
        assertEquals(2, d.descontados());
    }

    @Test
    @DisplayName("shadow nunca desconta; AC-2 e AC-5 nao descontam")
    void shadowEOutrosTipos() {
        LigacaoControlador.DescontoSobAcao s = new LigacaoControlador.DescontoSobAcao(ModoAtuacao.SHADOW, MAPA,
                () -> List.of(ac7(ADMIN3)));
        assertFalse(s.descontar(HOTROD3, "ERROR_UNAVAILABLE"));
        LigacaoControlador.DescontoSobAcao o = new LigacaoControlador.DescontoSobAcao(ModoAtuacao.ACTIVE, MAPA,
                () -> List.of(new AcaoCluster(AcaoCluster.Tipo.AC_5, List.of("sessions-async"), ADMIN3, null),
                        new AcaoCluster(AcaoCluster.Tipo.AC_2, List.of("sessions-async"), null, null)));
        assertFalse(o.descontar(HOTROD3, "ERROR_UNAVAILABLE"));
        assertEquals(0, s.descontados() + o.descontados());
    }
}
