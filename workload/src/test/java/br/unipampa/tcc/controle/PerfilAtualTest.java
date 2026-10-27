package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B-27, J2: o perfil em vigor e os ouvintes de mudanca. */
class PerfilAtualTest {

    @Test
    @DisplayName("J2: comeca em NORMAL; ouvinte chamado uma vez por mudanca, depois da escrita; definir igual nao notifica")
    void ouvintes() {
        PerfilAtual p = new PerfilAtual();
        assertEquals(Perfil.NORMAL, p.atual());
        List<Perfil> vistos = new ArrayList<>();
        p.registrar(() -> vistos.add(p.atual()));
        p.definir(Perfil.NORMAL);
        assertEquals(List.of(), vistos, "definir o mesmo perfil nao notifica");
        p.definir(Perfil.CONSERVADOR);
        p.definir(Perfil.CONSERVADOR);
        p.definir(Perfil.NORMAL);
        assertEquals(List.of(Perfil.CONSERVADOR, Perfil.NORMAL), vistos, "um aviso por mudanca, apos a escrita");
        assertThrows(IllegalArgumentException.class, () -> p.definir(null));
        assertThrows(IllegalArgumentException.class, () -> p.registrar(null));
    }

    @Test
    @DisplayName("J2: o ouvinte roda fora do lock do portao (outra thread consegue entrar e sair durante o aviso)")
    void foraDoLock() throws Exception {
        PerfilAtual perfil = new PerfilAtual();
        PortaoDeEmissao portao = new PortaoDeEmissao(perfil, ParametrosKnobs.padrao(), 4, System::nanoTime,
                Dormidor.real(), e -> { }, 1L);
        List<Boolean> conseguiu = new ArrayList<>();
        perfil.registrar(() -> {
            Thread t = PortaoDeEmissaoK1Test.daemon(() -> {
                try {
                    portao.entrar();
                    portao.sair();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            conseguiu.add(!t.isAlive());
        });
        perfil.definir(Perfil.CONSERVADOR);
        assertEquals(List.of(true), conseguiu);
        assertEquals(0, portao.emVoo());
        assertFalse(Thread.currentThread().isInterrupted());
        portao.encerrar();
    }
}
