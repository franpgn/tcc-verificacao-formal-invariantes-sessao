package br.unipampa.tcc.session;

import br.unipampa.tcc.session.ConvergenciaLoop.EstadoCluster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-6 (B-46; P-6 da V-W5, achado A-1, opcao a; desenho do coordenador de 05/11): no fim da carga o programa
 * imprime uma linha de sinal no stdout (o orquestrador le pelo pipe, sem disco no caminho) com o alvo do AC-5
 * aplicado; o orquestrador faz o {@code podman start} do no parado, e o programa espera a topologia completa, com
 * prazo, antes do laco de convergencia. {@code t_religamento_ns} e o instante em que a carga viu os servidores
 * esperados de volta (depois da leitura que os viu). Nenhum teste dorme de verdade.
 */
class ReligamentoTest {

    static final long S = 1_000_000_000L;
    static final EstadoCluster TRES = new EstadoCluster(3, true, true);
    static final EstadoCluster DOIS = new EstadoCluster(2, true, true);

    /** Relogio falso: dormir so avanca; cada leitura da sonda gasta {@code custoLeitura}. */
    static final class RelogioDeTeste implements ConvergenciaLoop.Relogio {
        long agora;
        final List<Long> esperas = new ArrayList<>();

        RelogioDeTeste(long inicio) {
            agora = inicio;
        }

        @Override
        public long agoraNs() {
            return agora;
        }

        @Override
        public void dormirNs(long ns) {
            esperas.add(ns);
            agora += ns;
        }
    }

    /** Sonda que devolve {@code estados[k]} na k-esima leitura (o ultimo se repete) e gasta 100 ms por leitura. */
    static ConvergenciaLoop.SondaCluster sonda(RelogioDeTeste r, AtomicInteger leituras, Object... estados) {
        return () -> {
            int k = leituras.getAndIncrement();
            r.agora += 100_000_000L;
            Object e = estados[Math.min(k, estados.length - 1)];
            if (e instanceof Exception ex) {
                throw ex;
            }
            return (EstadoCluster) e;
        };
    }

    @Test
    @DisplayName("linha de sinal: prefixo fixo, rep, t_fim_carga_ns e ac5_alvo; '-' sem AC-5")
    void linhaDoSinal() {
        assertEquals("Fim da carga: rep=1 t_fim_carga_ns=123 ac5_alvo=127.0.0.1:11224",
                Religamento.linhaSinal(1, 123L, "127.0.0.1:11224"));
        assertEquals("Fim da carga: rep=2 t_fim_carga_ns=5 ac5_alvo=-", Religamento.linhaSinal(2, 5L, null));
        assertEquals("Fim da carga: rep=2 t_fim_carga_ns=5 ac5_alvo=-", Religamento.linhaSinal(2, 5L, " "));
        assertTrue(Religamento.linhaSinal(1, 1L, null).startsWith(Religamento.PREFIXO));
    }

    @Test
    @DisplayName("linha de sinal casa com a expressao que o orquestrador usa (run-campanha-s3.sh)")
    void expressaoDoOrquestrador() {
        // a mesma expressao de SINAL_RE em scripts/run-campanha-s3.sh
        Pattern re = Pattern.compile("^Fim da carga: rep=([0-9]+) t_fim_carga_ns=([0-9]+) ac5_alvo=([^ ]+)$");
        Matcher m = re.matcher(Religamento.linhaSinal(1, 1730000000000000000L, "127.0.0.1:11224"));
        assertTrue(m.matches());
        assertEquals("127.0.0.1:11224", m.group(3));
        assertTrue(re.matcher(Religamento.linhaSinal(1, 0L, null)).matches());
    }

    @Test
    @DisplayName("alvo com espaco e recusado (a linha tem de ser um token por campo)")
    void alvoComEspaco() {
        assertThrows(IllegalArgumentException.class, () -> Religamento.linhaSinal(1, 1L, "a b"));
    }

    @Test
    @DisplayName("espera: o instante gravado e o fim da leitura que viu os 3 servidores, e nao o inicio")
    void instanteDaLeituraQueViu() {
        RelogioDeTeste r = new RelogioDeTeste(1000 * S);
        AtomicInteger n = new AtomicInteger();
        Religamento.Resultado res = Religamento.esperar(sonda(r, n, DOIS, DOIS, TRES), 3, r,
                "127.0.0.1:11224", 999 * S, 180 * S, 2 * S);
        assertFalse(res.esgotado());
        assertEquals(3, res.leituras());
        assertEquals(3, n.get());
        // leituras em 1000,0 (fim 1000,1), 1002,1 (fim 1002,2), 1004,2 (fim 1004,3)
        assertEquals(1004 * S + 300_000_000L, res.tReligamentoNs());
        assertEquals(List.of(2 * S, 2 * S), r.esperas);
        assertEquals("127.0.0.1:11224", res.ac5Alvo());
        assertEquals(180 * S, res.prazoNs());
        assertEquals(999 * S, res.tSinalNs());
    }

    @Test
    @DisplayName("espera: sonda que falha (no subindo, REST sem resposta) conta como topologia incompleta")
    void falhaDaSondaNaoConta() {
        RelogioDeTeste r = new RelogioDeTeste(0);
        AtomicInteger n = new AtomicInteger();
        Religamento.Resultado res = Religamento.esperar(
                sonda(r, n, new java.io.IOException("curl excedeu o tempo"), TRES), 3, r, "x:1", 0, 180 * S, 2 * S);
        assertFalse(res.esgotado());
        assertEquals(2, res.leituras());
        assertEquals(2 * S + 200_000_000L, res.tReligamentoNs());
    }

    @Test
    @DisplayName("espera: prazo esgotado sem ver os 3 servidores -> t_religamento nulo e esgotado; ultima leitura no prazo")
    void prazoEsgotado() {
        RelogioDeTeste r = new RelogioDeTeste(0);
        AtomicInteger n = new AtomicInteger();
        Religamento.Resultado res = Religamento.esperar(sonda(r, n, DOIS), 3, r, "x:1", 0, 5 * S, 2 * S);
        assertTrue(res.esgotado());
        assertNull(res.tReligamentoNs());
        // leituras: 0 (fim 0,1); 2,1 (fim 2,2); 4,2 (fim 4,3); dorme so 0,7 s ate o prazo; 5,0 (fim 5,1) e para
        assertEquals(4, res.leituras());
        assertEquals(List.of(2 * S, 2 * S, 700_000_000L), r.esperas);
        assertTrue(r.agora - 0 >= 5 * S, "nao para antes do prazo");
    }

    @Test
    @DisplayName("espera: topologia ja completa na primeira leitura -> uma leitura, sem dormir")
    void jaCompleta() {
        RelogioDeTeste r = new RelogioDeTeste(10 * S);
        AtomicInteger n = new AtomicInteger();
        Religamento.Resultado res = Religamento.esperar(sonda(r, n, TRES), 3, r, "x:1", 10 * S, 180 * S, 2 * S);
        assertEquals(1, res.leituras());
        assertEquals(List.of(), r.esperas);
        assertEquals(10 * S + 100_000_000L, res.tReligamentoNs());
    }

    @Test
    @DisplayName("sem AC-5: nenhum campo de espera (prazo e instante nulos), so o instante do sinal")
    void semEspera() {
        Religamento.Resultado res = Religamento.Resultado.semEspera(77L);
        assertNull(res.ac5Alvo());
        assertNull(res.prazoNs());
        assertNull(res.tReligamentoNs());
        assertFalse(res.esgotado());
        assertEquals(0, res.leituras());
        assertEquals(77L, res.tSinalNs());
    }

    @Test
    @DisplayName("parametros invalidos: prazo, passo e esperados > 0; alvo obrigatorio na espera")
    void parametrosInvalidos() {
        RelogioDeTeste r = new RelogioDeTeste(0);
        ConvergenciaLoop.SondaCluster s = () -> TRES;
        assertThrows(IllegalArgumentException.class, () -> Religamento.esperar(s, 3, r, "x:1", 0, 0, 2 * S));
        assertThrows(IllegalArgumentException.class, () -> Religamento.esperar(s, 3, r, "x:1", 0, 5 * S, 0));
        assertThrows(IllegalArgumentException.class, () -> Religamento.esperar(s, 0, r, "x:1", 0, 5 * S, 2 * S));
        assertThrows(IllegalArgumentException.class, () -> Religamento.esperar(s, 3, r, null, 0, 5 * S, 2 * S));
    }

    @Test
    @DisplayName("passo da espera = periodo da sonda de topologia (B-26.2), sem numero novo")
    void passoDaSonda() {
        assertEquals(LigacaoControlador.PERIODO_SONDA_NS, Religamento.PASSO_NS);
    }

    @Test
    @DisplayName("ControleDaCarga sem controlador de verdade: nenhum AC-5 aplicado (default nulo)")
    void controleSemAc5() {
        WorkloadMain.ControleDaCarga c = new WorkloadMain.ControleDaCarga() {
            @Override
            public void close() {
            }

            @Override
            public String linhaResumo() {
                return "";
            }
        };
        assertNull(c.ac5AplicadaAlvo());
    }
}
