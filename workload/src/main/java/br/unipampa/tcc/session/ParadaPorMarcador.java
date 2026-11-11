package br.unipampa.tcc.session;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * B-33 (E1, item 3): sinaliza as threads de carga que a escrita deve parar quando
 * o marcador {@code event=end} (t_fim) aparece no arquivo de marcadores. Le o
 * arquivo no maximo a cada {@link #INTERVALO_MS} e e idempotente: uma vez parado,
 * permanece parado. Arquivo ausente, malformado ou ilegivel conta como "ainda nao".
 */
public final class ParadaPorMarcador implements BooleanSupplier {

    public static final long INTERVALO_MS = 500L;

    private final Path arquivo;
    private final LongSupplier agoraMs;
    private volatile boolean parado;
    private volatile boolean consultou;
    private volatile long ultimaConsultaMs;

    public ParadaPorMarcador(Path arquivo, LongSupplier agoraMs) {
        this.arquivo = arquivo;
        this.agoraMs = agoraMs;
    }

    public ParadaPorMarcador(Path arquivo) {
        this(arquivo, System::currentTimeMillis);
    }

    public boolean deveParar() {
        if (parado) {
            return true;
        }
        long agora = agoraMs.getAsLong();
        if (consultou && agora - ultimaConsultaMs < INTERVALO_MS) {
            return false;
        }
        consultou = true;
        ultimaConsultaMs = agora;
        try {
            if (Marcadores.ler(arquivo).fimNs() != null) {
                parado = true;
            }
        } catch (IOException | IllegalArgumentException e) {
            // ainda nao ha marcador legivel: segue escrevendo
        }
        return parado;
    }

    @Override
    public boolean getAsBoolean() {
        return deveParar();
    }
}
