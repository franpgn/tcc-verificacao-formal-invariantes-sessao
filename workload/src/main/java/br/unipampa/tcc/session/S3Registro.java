package br.unipampa.tcc.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * B-33 (E2): registro em memoria das acoes de aplicacao do S3 e de contadores de eventos
 * (sessoes orfas, expiradas no cliente, bloqueios por tipo). Thread-safe. Saidas:
 * {@code s3-acoes-rep-NNN.csv} e {@code s3-resumo-rep-NNN.json}.
 */
public final class S3Registro implements Consumer<AcaoS3> {

    private final List<AcaoS3> acoes = new ArrayList<>();
    private final ConcurrentHashMap<String, LongAdder> contadores = new ConcurrentHashMap<>();

    @Override
    public void accept(AcaoS3 a) {
        synchronized (acoes) {
            acoes.add(a);
        }
        contar("acao|" + a.operacao() + "|" + a.resultado());
        if (a.falhaPercebida()) {
            contar("falha-percebida|" + a.operacao());
        }
    }

    /** Incrementa um contador nomeado (ex.: {@code evento|SessaoOrfa}). */
    public void contar(String nome) {
        contadores.computeIfAbsent(nome, k -> new LongAdder()).increment();
    }

    public long valor(String nome) {
        LongAdder a = contadores.get(nome);
        return a == null ? 0 : a.sum();
    }

    public List<AcaoS3> acoes() {
        synchronized (acoes) {
            return new ArrayList<>(acoes);
        }
    }

    /** Contadores ordenados por nome (para resumo e testes). */
    public Map<String, Long> contadores() {
        Map<String, Long> out = new TreeMap<>();
        contadores.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }

    /** Soma dos contadores {@code acao|*}: total de acoes de aplicacao registradas. */
    public long totalAcoes() {
        synchronized (acoes) {
            return acoes.size();
        }
    }

    public void escreverCsv(Path destino, EpochClock relogio) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        List<AcaoS3> copia = acoes();
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write(AcaoS3.HEADER);
            w.newLine();
            for (AcaoS3 a : copia) {
                w.write(relogio != null
                        ? a.paraCsv(relogio.paraEpochNanos(a.tNs()), relogio.paraEpochNanos(a.tFimNs()))
                        : a.paraCsv(a.tNs(), a.tFimNs()));
                w.newLine();
            }
        }
    }

    /** Resumo: mix efetivo de acoes por operacao/resultado, falhas percebidas e eventos. */
    public void escreverResumo(Path destino, Map<String, Object> parametros) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("parametros", parametros);
        raiz.put("total_acoes", totalAcoes());
        raiz.put("contadores", contadores());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(destino.toFile(), raiz);
    }
}
