package br.unipampa.tcc.session;

import br.unipampa.tcc.session.Marcadores.Fase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B-33 (E1, item 2): leitura dos marcadores B-29 e rotulo de fase. */
class MarcadoresTest {

    private static final long S = 1_000_000_000L;

    private static Marcadores completo() {
        return Marcadores.parse(List.of(
                "fault=F2 node=isn3 event=start t_epoch_ns=1000000000000",
                "fault=F2 node=isn3 event=split t_epoch_ns=1040000000000",
                "fault=F2 node=isn3 event=end t_epoch_ns=1090000000000"))
                .comRecuperacao(60 * S);
    }

    @Test
    @DisplayName("parse le start, split e end no formato do injetor de particao")
    void parseLeTresEventos() {
        Marcadores m = completo();
        assertEquals(1_000_000_000_000L, m.inicioNs());
        assertEquals(1_040_000_000_000L, m.splitNs());
        assertEquals(1_090_000_000_000L, m.fimNs());
    }

    @Test
    @DisplayName("linhas vazias e comentarios sao ignorados; linha malformada e erro")
    void linhasInvalidas() {
        Marcadores m = Marcadores.parse(List.of("", "# comentario",
                "fault=F2 node=isn3 event=start t_epoch_ns=5"));
        assertEquals(5L, m.inicioNs());
        assertNull(m.fimNs());
        assertThrows(IllegalArgumentException.class,
                () -> Marcadores.parse(List.of("lixo sem pares")));
        assertThrows(IllegalArgumentException.class,
                () -> Marcadores.parse(List.of("fault=F2 event=start t_epoch_ns=abc")));
        assertThrows(IllegalArgumentException.class,
                () -> Marcadores.parse(List.of("fault=F2 event=explodiu t_epoch_ns=1")));
    }

    @Test
    @DisplayName("fases: antes, durante-pre-split, durante-pos-split, recuperacao, pos-convergencia")
    void fasesEmCadaIntervalo() {
        Marcadores m = completo();
        assertEquals(Fase.ANTES, m.faseDe(999_999_999_999L));
        assertEquals(Fase.DURANTE_PRE_SPLIT, m.faseDe(1_000_000_000_000L));
        assertEquals(Fase.DURANTE_PRE_SPLIT, m.faseDe(1_039_999_999_999L));
        assertEquals(Fase.DURANTE_POS_SPLIT, m.faseDe(1_040_000_000_000L));
        assertEquals(Fase.DURANTE_POS_SPLIT, m.faseDe(1_090_000_000_000L));
        assertEquals(Fase.RECUPERACAO, m.faseDe(1_090_000_000_001L));
        assertEquals(Fase.RECUPERACAO, m.faseDe(1_150_000_000_000L));
        assertEquals(Fase.POS_CONVERGENCIA, m.faseDe(1_150_000_000_001L));
    }

    @Test
    @DisplayName("rotulos textuais usados no CSV")
    void rotulos() {
        assertEquals("antes", Fase.ANTES.rotulo());
        assertEquals("durante-pre-split", Fase.DURANTE_PRE_SPLIT.rotulo());
        assertEquals("durante-pos-split", Fase.DURANTE_POS_SPLIT.rotulo());
        assertEquals("recuperacao", Fase.RECUPERACAO.rotulo());
        assertEquals("pos-convergencia", Fase.POS_CONVERGENCIA.rotulo());
    }

    @Test
    @DisplayName("sem marcador de split o intervalo inteiro e 'durante' (nao se afirma pre/pos split)")
    void semSplit() {
        Marcadores m = Marcadores.parse(List.of(
                "fault=F2 node=isn3 event=start t_epoch_ns=100",
                "fault=F2 node=isn3 event=end t_epoch_ns=200")).comRecuperacao(10);
        assertEquals(Fase.DURANTE, m.faseDe(150));
        assertEquals("durante", Fase.DURANTE.rotulo());
    }

    @Test
    @DisplayName("convergencia posterior a janela W estende a recuperacao ate t_convergencia")
    void recuperacaoVaiAteConvergencia() {
        Marcadores m = completo().comConvergencia(1_200_000_000_000L);
        assertEquals(Fase.RECUPERACAO, m.faseDe(1_199_000_000_000L));
        assertEquals(Fase.POS_CONVERGENCIA, m.faseDe(1_200_000_000_001L));
    }

    @Test
    @DisplayName("divergencia persistente: nada depois de t_fim vira pos-convergencia")
    void divergenciaPersistente() {
        Marcadores m = completo().comDivergenciaPersistente();
        assertEquals(Fase.RECUPERACAO, m.faseDe(9_000_000_000_000L));
    }

    @Test
    @DisplayName("sem marcador de inicio (execucao sem falha) tudo e 'antes'")
    void semFalha() {
        assertEquals(Fase.ANTES, Marcadores.vazio().faseDe(123));
    }

    @Test
    @DisplayName("ler(Path) le arquivo do disco")
    void lerArquivo(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("marcadores.txt");
        Files.writeString(f, "fault=F2 node=isn1 event=start t_epoch_ns=77\n"
                + "fault=F2 node=isn1 event=end t_epoch_ns=99\n");
        Marcadores m = Marcadores.ler(f);
        assertEquals(77L, m.inicioNs());
        assertEquals(99L, m.fimNs());
    }

    @Test
    @DisplayName("ler(Path) de arquivo ainda inexistente devolve marcadores vazios")
    void arquivoAusente(@TempDir Path dir) throws IOException {
        assertNull(Marcadores.ler(dir.resolve("nao-existe.txt")).inicioNs());
    }
}
