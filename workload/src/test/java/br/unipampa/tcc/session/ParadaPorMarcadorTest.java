package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-33 (E1, item 3): a carga de escrita para quando o marcador de fim da falha aparece. */
class ParadaPorMarcadorTest {

    @Test
    @DisplayName("so manda parar depois que o marcador 'end' existe; consulta o arquivo no maximo a cada 500 ms")
    void paraAoVerOFim(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("m.txt");
        AtomicLong agoraMs = new AtomicLong(0);
        ParadaPorMarcador p = new ParadaPorMarcador(f, agoraMs::get);
        assertFalse(p.deveParar(), "arquivo ainda nao existe");
        Files.writeString(f, "fault=F2 node=isn3 event=start t_epoch_ns=1\n");
        agoraMs.set(100);
        assertFalse(p.deveParar(), "dentro dos 500 ms nao relê");
        agoraMs.set(600);
        assertFalse(p.deveParar(), "so ha start");
        Files.writeString(f, "fault=F2 node=isn3 event=start t_epoch_ns=1\n"
                + "fault=F2 node=isn3 event=end t_epoch_ns=2\n");
        agoraMs.set(700);
        assertFalse(p.deveParar(), "ainda dentro da janela de cache");
        agoraMs.set(1200);
        assertTrue(p.deveParar());
        Files.delete(f);
        agoraMs.set(5000);
        assertTrue(p.deveParar(), "uma vez parado, permanece parado");
    }

    @Test
    @DisplayName("arquivo malformado nao derruba a thread de carga (nao para)")
    void malformadoNaoParaNemLanca(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("m.txt");
        Files.writeString(f, "lixo\n");
        ParadaPorMarcador p = new ParadaPorMarcador(f, () -> 0L);
        assertFalse(p.deveParar());
    }
}
