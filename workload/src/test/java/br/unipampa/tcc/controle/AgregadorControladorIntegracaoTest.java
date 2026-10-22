package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.1: o agregador alimenta {@link Controlador#avaliar} num roteiro curto, com os parametros
 * padrao (W = 5 s, nErr = 50, theta_err_in = 0,02, k = 2) e topologia indisponivel. Cada janela
 * emitida vira uma decisao, na ordem, com {@code fimNs} estritamente crescente.
 */
class AgregadorControladorIntegracaoTest {

    private static final long S = 1_000_000_000L;
    private static final long ORIGEM = 1_000 * S;

    private final ParametrosControlador p = ParametrosControlador.padrao();
    private final AgregadorDeJanela agr = new AgregadorDeJanela(ORIGEM, p, Referencia.de(Map.of("Login", S)));
    private final Controlador ctrl = new Controlador(p);
    private final List<Decisao> decisoes = new ArrayList<>();
    private final List<JanelaObservada> observadas = new ArrayList<>();

    private void entregar(List<JanelaObservada> fechadas) {
        for (JanelaObservada o : fechadas) {
            observadas.add(o);
            decisoes.add(ctrl.avaliar(o.janela(), EstadoTopologia.indisponivel()));
        }
    }

    /** 100 eventos espalhados na janela {@code j}, {@code erros} deles ERROR_TRANSPORT. */
    private void janela(int j, int erros) {
        long ini = ORIGEM + j * p.wNs();
        for (int i = 0; i < 100; i++) {
            long fim = ini + 1 + i * (p.wNs() / 100);
            entregar(agr.registrar(new EventoOperacao("Logout", fim - 1_000, fim, i < erros ? "ERROR_TRANSPORT" : "OK")));
        }
    }

    private String perfis() {
        StringBuilder sb = new StringBuilder();
        for (Decisao d : decisoes) {
            sb.append(d.perfil() == Perfil.NORMAL ? 'N' : 'C');
        }
        return sb.toString();
    }

    /**
     * Um evento em {@code fimNs} da janela {@code ultima} completa-a (a marca d'agua chega ao fim
     * dela); {@code encerrar} a emite sem esperar L e descarta a parcial que comeca ali.
     */
    private void fechar(int ultima) {
        long fim = ORIGEM + (ultima + 1) * p.wNs();
        entregar(agr.registrar(new EventoOperacao("Logout", fim, fim, "OK")));
        entregar(agr.encerrar());
        assertEquals(1, agr.descartadosNoEncerramento());
    }

    @Test
    @DisplayName("erro alto em 2 janelas seguidas leva a CONSERVADOR por R2 na segunda")
    void erroPersistenteEntra() {
        janela(0, 0);
        janela(1, 10);
        janela(2, 10);
        janela(3, 0);
        fechar(3);
        assertEquals("NNCC", perfis());
        assertEquals(Decisao.Regra.R2, decisoes.get(2).regra());
        assertTrue(decisoes.get(2).transicao());
        assertEquals(ORIGEM + 3 * p.wNs(), decisoes.get(2).fimNs(), "tempo de decisao = fimNs da janela");
    }

    @Test
    @DisplayName("janela vazia entre duas janelas de erro alto quebra a persistencia (por isso toda janela e emitida)")
    void janelaVaziaQuebraPersistencia() {
        janela(0, 10);
        janela(2, 10); // a janela 1 fica sem evento e e emitida vazia
        fechar(2);
        assertEquals("NNN", perfis());
        assertEquals(3, decisoes.size());
        assertFalse(decisoes.stream().anyMatch(Decisao::transicao));
    }

    @Test
    @DisplayName("I2 esperada nao dispara; I1 dispara R1 na janela em que foi revelada")
    void violacoes() {
        janela(0, 0);
        entregar(agr.registrarViolacao(ClasseViolacao.de("I2", "ESPERADA-POR-DESENHO"), ORIGEM + p.wNs() + 7));
        janela(1, 0);
        entregar(agr.registrarViolacao(ClasseViolacao.de("I1", null), ORIGEM + 2 * p.wNs() + 7));
        janela(2, 0);
        fechar(2);
        assertEquals("NNC", perfis());
        assertEquals(Decisao.Regra.NENHUMA, decisoes.get(1).regra());
        assertEquals(Decisao.Regra.R1, decisoes.get(2).regra());
    }

    @Test
    @DisplayName("I1 atrasado (janela 0 ja emitida) e promovido e dispara R1 uma janela depois (Gestor 22/10)")
    void violacaoAtrasadaDisparaR1NaJanelaSeguinte() {
        janela(0, 0);
        janela(1, 0); // a janela 0 ja foi emitida e avaliada (NORMAL)
        assertEquals("N", perfis());
        entregar(agr.registrarViolacao(ClasseViolacao.de("I1", null), ORIGEM + p.wNs() - 1));
        janela(2, 0);
        fechar(2);
        assertEquals("NCC", perfis());
        assertEquals(Decisao.Regra.R1, decisoes.get(1).regra());
        assertEquals(ORIGEM + 2 * p.wNs(), decisoes.get(1).fimNs(), "R1 no fimNs da janela que recebeu a promovida");
        assertEquals(1, observadas.get(1).violacoesSegurancaPromovidas());
        assertEquals(1, observadas.get(1).janela().violacoesSeguranca());
        assertEquals(0, observadas.get(0).janela().violacoesSeguranca(), "a janela 0 emitida nao muda");
    }
}
