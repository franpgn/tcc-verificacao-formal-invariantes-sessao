package br.unipampa.tcc.session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * B-43.3a (parte i): fixture SINTETICA de uma execucao sa, para os testes do
 * verificador de sanidade. Sorteia {@code nOps} operacoes com
 * {@link Scenario#proximaOperacao(Random)} e emite os eventos que o
 * {@code WorkloadMain} emitiria com a mistura corrigida: Login OK seguido de
 * Validate VALID e Logout NONE; Validate isolado VALID; Logout isolado NONE;
 * IncrementFailure COUNTED; ResetFailures NONE.
 */
final class FixtureSanidade {

    record Ev(String op, String rc, String fase) {
        Ev com(String novoRc) {
            return new Ev(op, novoRc, fase);
        }

        Ev naFase(String novaFase) {
            return new Ev(op, rc, novaFase);
        }
    }

    private FixtureSanidade() {
    }

    /** Fixture sa: todo Login OK. */
    static List<Ev> sa(Scenario s, long seed, int nOps) {
        return comLoginBloqueado(s, seed, nOps, k -> false);
    }

    /**
     * Como {@link #sa}, mas o k-esimo Login (k = 0, 1, ...) sai BLOCKED quando
     * {@code bloqueado.test(k)}; Login BLOCKED nao gera Validate/Logout.
     */
    static List<Ev> comLoginBloqueado(Scenario s, long seed, int nOps, IntPredicate bloqueado) {
        Random r = new Random(seed);
        List<Ev> evs = new ArrayList<>();
        int k = 0;
        for (int i = 0; i < nOps; i++) {
            switch (s.proximaOperacao(r)) {
                case VALIDATE -> evs.add(new Ev("Validate", "VALID", null));
                case LOGIN_VALIDATE_LOGOUT -> {
                    if (bloqueado.test(k++)) {
                        evs.add(new Ev("Login", "BLOCKED", null));
                    } else {
                        evs.add(new Ev("Login", "OK", null));
                        evs.add(new Ev("Validate", "VALID", null));
                        evs.add(new Ev("Logout", "NONE", null));
                    }
                }
                case LOGOUT -> evs.add(new Ev("Logout", "NONE", null));
                case INCREMENT_FAILURE -> evs.add(new Ev("IncrementFailure", "COUNTED", null));
                case RESET_FAILURES -> evs.add(new Ev("ResetFailures", "NONE", null));
                default -> throw new IllegalStateException();
            }
        }
        return evs;
    }

    /** Alimenta uma {@link Sanidade} nova e avalia com os limiares provisorios. */
    static Sanidade.Resultado avaliar(Scenario s, List<Ev> evs) {
        Sanidade san = new Sanidade();
        evs.forEach(e -> san.registrar(e.op(), e.rc(), e.fase()));
        return san.avaliar(s, Sanidade.Limiares.provisorios());
    }

    /**
     * Grava o CSV no formato do {@link EventCsvWriter}; com {@code comFase}, usa
     * {@link EventCsvWriter#HEADER_COM_FASE} e a fase de cada evento.
     */
    static Path gravar(Path dir, String nome, List<Ev> evs, boolean comFase) throws IOException {
        Path p = dir.resolve(nome);
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write(comFase ? EventCsvWriter.HEADER_COM_FASE : EventCsvWriter.HEADER);
            w.write('\n');
            long id = 0;
            for (Ev e : evs) {
                w.write(id + "," + e.op() + "," + (1000 + id) + "," + (2000 + id)
                        + ",127.0.0.1:11222," + e.rc() + ",k-" + id);
                if (comFase) {
                    w.write("," + e.fase());
                }
                w.write('\n');
                id++;
            }
        }
        return p;
    }
}
