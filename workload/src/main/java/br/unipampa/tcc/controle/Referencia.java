package br.unipampa.tcc.controle;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * p99 de referencia por operacao (B-26.1; plano de 2026-10-22, secao 3.2). Um evento e elegivel
 * para o sinal de latencia se a sua operacao esta aqui, e e uma excedencia se a latencia
 * ({@code end_ns - start_ns}) for estritamente maior que o p99 da operacao.
 *
 * <p>Imutavel; as operacoes ficam em ordem de nome para que toda saida derivada seja
 * deterministica. A leitura do arquivo {@code referencia-<cenario>-<sync|async>.json} fica para
 * quando B-39.1 fixar o seu esquema; aqui so ha a construcao em memoria.
 *
 * @param p99Ns p99 de referencia em ns por nome de operacao (coluna {@code operation} do CSV)
 */
public record Referencia(Map<String, Long> p99Ns) {

    public Referencia {
        Objects.requireNonNull(p99Ns, "p99Ns");
        TreeMap<String, Long> copia = new TreeMap<>();
        for (Map.Entry<String, Long> e : p99Ns.entrySet()) {
            String op = e.getKey();
            Long v = e.getValue();
            if (op == null || op.isBlank()) {
                throw new IllegalArgumentException("nome de operacao vazio na referencia");
            }
            if (v == null || v <= 0) {
                throw new IllegalArgumentException("p99 de referencia deve ser positivo: " + op + "=" + v);
            }
            copia.put(op, v);
        }
        p99Ns = Collections.unmodifiableSortedMap(copia);
    }

    public static Referencia de(Map<String, Long> p99Ns) {
        return new Referencia(p99Ns);
    }

    /** Referencia sem operacoes: nenhum evento e elegivel (sinal de latencia indeterminado). */
    public static Referencia vazia() {
        return new Referencia(Map.of());
    }

    public boolean contem(String operacao) {
        return p99Ns.containsKey(operacao);
    }

    /** p99 da operacao; lanca {@link IllegalArgumentException} se ela nao esta na referencia. */
    public long p99Ns(String operacao) {
        Long v = p99Ns.get(operacao);
        if (v == null) {
            throw new IllegalArgumentException("operacao fora da referencia: " + operacao);
        }
        return v;
    }

    /** Visao imutavel, em ordem de nome. */
    public Map<String, Long> p99PorOperacao() {
        return p99Ns;
    }
}
