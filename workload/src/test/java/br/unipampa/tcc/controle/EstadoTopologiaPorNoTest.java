package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.S;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.fonte;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.rodadaEm;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.saudavel;
import static br.unipampa.tcc.controle.SondaTopologiaRodadaTest.sonda;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2a (B-44.2), item 3: estado de cada no na janela ({@link EstadoTopologia#porNo()}) e as tres
 * assinaturas da politica: vista completa, no sem resposta e vistas divergentes. O construtor antigo
 * continua valendo e {@link SondaTopologia#resumirAte(long)} continua sem {@code porNo}.
 */
class EstadoTopologiaPorNoTest {

    static EstadoTopologia.No todas(int min, int max) {
        return new EstadoTopologia.No(EstadoTopologia.Resposta.TODAS, min, max);
    }

    static EstadoTopologia.No alguma(int min, int max) {
        return new EstadoTopologia.No(EstadoTopologia.Resposta.ALGUMA, min, max);
    }

    static final EstadoTopologia.No NENHUMA = new EstadoTopologia.No(EstadoTopologia.Resposta.NENHUMA, -1, -1);

    static EstadoTopologia top(EstadoTopologia.No n1, EstadoTopologia.No n2, EstadoTopologia.No n3) {
        Map<String, EstadoTopologia.No> m = new LinkedHashMap<>();
        m.put("isn1", n1);
        m.put("isn2", n2);
        m.put("isn3", n3);
        boolean ok = n1.equals(todas(3, 3)) && n2.equals(todas(3, 3)) && n3.equals(todas(3, 3));
        return new EstadoTopologia(true, 3, ok ? 0 : 3, 1, m);
    }

    static final EstadoTopologia COMPLETA = top(todas(3, 3), todas(3, 3), todas(3, 3));
    static final EstadoTopologia QUEDA_ISN3 = top(todas(3, 3), todas(2, 3), NENHUMA);
    static final EstadoTopologia PARTICAO_DETECTADA = top(todas(2, 2), todas(2, 2), todas(1, 1));

    @Test
    @DisplayName("vista completa: todos responderam em todas as amostras e sempre com 3 membros")
    void vistaCompleta() {
        assertTrue(COMPLETA.vistaCompleta());
        assertFalse(top(todas(3, 3), todas(3, 3), alguma(3, 3)).vistaCompleta());
        assertFalse(top(todas(3, 3), todas(2, 3), todas(3, 3)).vistaCompleta());
        assertFalse(QUEDA_ISN3.vistaCompleta());
        assertFalse(new EstadoTopologia(true, 3, 0, 3).vistaCompleta(), "sem porNo nada dispara");
        assertFalse(EstadoTopologia.indisponivel().vistaCompleta());
    }

    @Test
    @DisplayName("no sem resposta: exatamente um sem resposta e os outros em todas as amostras")
    void noSemResposta() {
        assertEquals(Optional.of("isn3"), QUEDA_ISN3.noSemResposta());
        assertEquals(Optional.empty(), COMPLETA.noSemResposta());
        assertEquals(Optional.empty(), top(todas(3, 3), NENHUMA, NENHUMA).noSemResposta(), "dois fora");
        assertEquals(Optional.empty(), top(alguma(3, 3), todas(3, 3), NENHUMA).noSemResposta(), "outro oscilando");
        assertEquals(Optional.empty(), new EstadoTopologia(true, 3, 3, 0).noSemResposta());
    }

    @Test
    @DisplayName("vistas divergentes: faixas de number_of_nodes disjuntas entre nos que responderam")
    void vistasDivergentes() {
        assertTrue(PARTICAO_DETECTADA.vistasDivergentes());
        assertFalse(COMPLETA.vistasDivergentes());
        assertFalse(QUEDA_ISN3.vistasDivergentes(), "troca de vista 3->2 vista em instantes diferentes nao conta");
        assertFalse(top(todas(2, 3), todas(2, 3), todas(1, 3)).vistasDivergentes(), "faixas que se cruzam");
        assertTrue(top(todas(3, 3), todas(2, 2), NENHUMA).vistasDivergentes());
        assertFalse(top(todas(2, 2), NENHUMA, NENHUMA).vistasDivergentes(), "um so respondendo");
    }

    @Test
    @DisplayName("construtor antigo delega com porNo vazio; No e porNo sao validados")
    void construtores() {
        EstadoTopologia antigo = new EstadoTopologia(true, 2, 1, 2);
        assertEquals(Map.of(), antigo.porNo());
        assertEquals(new EstadoTopologia(true, 3, 0, 1), COMPLETA.semPorNo());
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia.No(EstadoTopologia.Resposta.NENHUMA, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia.No(EstadoTopologia.Resposta.TODAS, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> new EstadoTopologia.No(EstadoTopologia.Resposta.ALGUMA, -1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new EstadoTopologia(true, 0, 0, 0, Map.of("isn1", todas(3, 3))), "porNo sem amostras");
        assertThrows(UnsupportedOperationException.class, () -> COMPLETA.porNo().put("x", NENHUMA));
    }

    @Test
    @DisplayName("sonda: resumirComNosAte conta as respostas de cada no nas amostras consumidas; resumirAte nao traz porNo")
    void sondaPorNo() {
        AtomicLong t = new AtomicLong(S);
        AtomicInteger rodada = new AtomicInteger();
        // rodada 0: todos com 3; rodada 1: no1 falha, no2 ve 2; rodada 2: no1 falha, no2 ve 2
        FonteDeSaude f = fonte(3, (i, p) -> {
            int r = rodada.get();
            if (i == 1 && r >= 1) {
                throw new IOException("recusada");
            }
            if (i == 2 && r >= 1) {
                return saudavel(2);
            }
            return saudavel(3);
        });
        try (SondaTopologia s = sonda(f, t)) {
            rodadaEm(s, t, S);
            rodada.set(1);
            rodadaEm(s, t, 2 * S);
            rodada.set(2);
            rodadaEm(s, t, 3 * S);
            EstadoTopologia e = s.resumirComNosAte(5 * S);
            assertEquals(3, e.amostras());
            assertEquals(java.util.List.of("no0", "no1", "no2"), java.util.List.copyOf(e.porNo().keySet()));
            assertEquals(todas(3, 3), e.porNo().get("no0"));
            assertEquals(alguma(3, 3), e.porNo().get("no1"));
            assertEquals(todas(2, 3), e.porNo().get("no2"));
            assertFalse(e.vistaCompleta());

            rodadaEm(s, t, 6 * S);
            rodadaEm(s, t, 7 * S);
            EstadoTopologia e2 = s.resumirComNosAte(10 * S);
            assertEquals(NENHUMA, e2.porNo().get("no1"));
            assertEquals(Optional.of("no1"), e2.noSemResposta());

            rodadaEm(s, t, 11 * S);
            EstadoTopologia e3 = s.resumirAte(15 * S);
            assertEquals(Map.of(), e3.porNo(), "resumirAte continua so com as contagens");
            assertEquals(new EstadoTopologia(true, 1, 1, 2), e3);

            assertEquals(Map.of(), s.resumirComNosAte(20 * S).porNo(), "janela sem amostra: porNo vazio");
        }
    }

    @Test
    @DisplayName("FonteDeTopologia: o padrao de resumirComNosAte e resumirAte (lambda sem porNo)")
    void fontePadrao() {
        FonteDeTopologia f = fim -> COMPLETA.semPorNo();
        assertEquals(Map.of(), f.resumirComNosAte(5 * S).porNo());
        assertEquals(EstadoTopologia.indisponivel(), FonteDeTopologia.INDISPONIVEL.resumirComNosAte(5 * S));
    }
}
