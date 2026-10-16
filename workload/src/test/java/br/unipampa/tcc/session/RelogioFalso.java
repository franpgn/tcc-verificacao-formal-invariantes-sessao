package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;

/** Relogio de teste do S3: nunca dorme de verdade; "dormir" avanca o tempo e fica registrado. */
final class RelogioFalso implements RelogioS3 {

    long agora;
    final List<Long> esperas = new ArrayList<>();

    RelogioFalso(long inicioNs) {
        this.agora = inicioNs;
    }

    @Override
    public long agoraNs() {
        return agora;
    }

    @Override
    public void dormirAteNs(long alvoNs) {
        if (alvoNs > agora) {
            esperas.add(alvoNs - agora);
            agora = alvoNs;
        }
    }

    static long seg(double s) {
        return (long) (s * 1_000_000_000.0);
    }

    static long ms(double m) {
        return (long) (m * 1_000_000.0);
    }
}
