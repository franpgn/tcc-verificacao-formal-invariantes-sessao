package br.unipampa.tcc.controle;

import java.util.ArrayList;
import java.util.List;

/**
 * Apoio de teste: uma letra por janela tumbling de W, a partir de t = 0. A janela i cobre
 * {@code [i*W, (i+1)*W)}. Volumes escolhidos para cair exatamente nos limiares do
 * {@link ParametrosControlador#padrao()} (n_lat = 100, n_err = 50, 0,05/0,02 e 0,02/0,005).
 *
 * <pre>
 * c  calma (1000 eventos, 0 erro, 0 excedencia), topologia OK
 * L  latencia alta 0,10            l  latencia intermediaria 0,03 (entre theta_out e theta_in)
 * E  erro alto 0,05                e  erro intermediario 0,01
 * B  latencia 0,10 e erro 0,05     o  latencia 0,02 e erro 0,005 (exatamente theta_out)
 * p  latencia 0,021 (logo acima de theta_lat_out)
 * b  amostra minima (100) com latencia 0,05 e erro 0,02 (exatamente theta_in)
 * q  amostra minima (100) com latencia 0,05 (exatamente theta_lat_in) e 0 erro (so a latencia e alta)
 * a  amostra minima (100) com latencia 0,04 e erro 0,01 (logo abaixo de theta_in)
 * w  total = 50 (minimo de erro) com erro 0,02 e sem elegiveis de latencia
 * x  10 eventos, todos com erro e excedencia (amostra insuficiente nos dois sinais)
 * y  49 eventos, todos com erro e excedencia (um abaixo do minimo de erro)
 * z  99 elegiveis, todos excedendo; 0 erro (um abaixo do minimo de latencia)
 * n  janela vazia
 * V  calma + 1 violacao de seguranca     i  calma + 5 violacoes I2 esperadas (nao decidem)
 * T  calma + topologia anomala            U  calma + topologia indisponivel (com anomalas declaradas)
 * </pre>
 */
final class Roteiro {

    static final long S = 1_000_000_000L;

    static final EstadoTopologia TOP_OK = new EstadoTopologia(true, 3, 0, 3);
    static final EstadoTopologia TOP_ANOMALA = new EstadoTopologia(true, 3, 1, 2);
    static final EstadoTopologia TOP_INDISPONIVEL = new EstadoTopologia(false, 3, 3, 0);

    record Passo(Janela janela, EstadoTopologia topologia) {
    }

    private Roteiro() {
    }

    static Janela janela(int i, long wNs, long total, long erros, long indisp, long eleg, long exc,
                         long viol, long i2esp) {
        return new Janela(i * wNs, (i + 1) * wNs, total, erros, indisp, eleg, exc, viol, i2esp);
    }

    static Passo passo(char c, int i, long wNs) {
        return switch (c) {
            case 'c' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 0, 0, 0), TOP_OK);
            case 'L' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 100, 0, 0), TOP_OK);
            case 'l' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 30, 0, 0), TOP_OK);
            case 'E' -> new Passo(janela(i, wNs, 1000, 50, 20, 1000, 0, 0, 0), TOP_OK);
            case 'e' -> new Passo(janela(i, wNs, 1000, 10, 0, 1000, 0, 0, 0), TOP_OK);
            case 'B' -> new Passo(janela(i, wNs, 1000, 50, 0, 1000, 100, 0, 0), TOP_OK);
            case 'o' -> new Passo(janela(i, wNs, 1000, 5, 0, 1000, 20, 0, 0), TOP_OK);
            case 'p' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 21, 0, 0), TOP_OK);
            case 'b' -> new Passo(janela(i, wNs, 100, 2, 0, 100, 5, 0, 0), TOP_OK);
            case 'q' -> new Passo(janela(i, wNs, 100, 0, 0, 100, 5, 0, 0), TOP_OK);
            case 'a' -> new Passo(janela(i, wNs, 100, 1, 0, 100, 4, 0, 0), TOP_OK);
            case 'w' -> new Passo(janela(i, wNs, 50, 1, 0, 0, 0, 0, 0), TOP_OK);
            case 'x' -> new Passo(janela(i, wNs, 10, 10, 10, 10, 10, 0, 0), TOP_OK);
            case 'y' -> new Passo(janela(i, wNs, 49, 49, 0, 49, 49, 0, 0), TOP_OK);
            case 'z' -> new Passo(janela(i, wNs, 99, 0, 0, 99, 99, 0, 0), TOP_OK);
            case 'n' -> new Passo(janela(i, wNs, 0, 0, 0, 0, 0, 0, 0), TOP_OK);
            case 'V' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 0, 1, 0), TOP_OK);
            case 'i' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 0, 0, 5), TOP_OK);
            case 'T' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 0, 0, 0), TOP_ANOMALA);
            case 'U' -> new Passo(janela(i, wNs, 1000, 0, 0, 1000, 0, 0, 0), TOP_INDISPONIVEL);
            default -> throw new IllegalArgumentException("letra desconhecida: " + c);
        };
    }

    static List<Passo> passos(String roteiro, long wNs) {
        List<Passo> r = new ArrayList<>();
        String s = roteiro.replace(" ", "");
        for (int i = 0; i < s.length(); i++) {
            r.add(passo(s.charAt(i), i, wNs));
        }
        return r;
    }

    static List<Decisao> rodar(ParametrosControlador p, List<Passo> passos) {
        Controlador c = new Controlador(p);
        List<Decisao> r = new ArrayList<>();
        for (Passo x : passos) {
            r.add(c.avaliar(x.janela(), x.topologia()));
        }
        return r;
    }

    static List<Decisao> rodar(ParametrosControlador p, String roteiro) {
        return rodar(p, passos(roteiro, p.wNs()));
    }

    /** Perfil apos cada janela: N ou C. */
    static String perfis(List<Decisao> ds) {
        StringBuilder sb = new StringBuilder();
        for (Decisao d : ds) {
            sb.append(d.perfil() == Perfil.NORMAL ? 'N' : 'C');
        }
        return sb.toString();
    }

    static ParametrosControlador comTempos(long cooldownNs, long estavelNs, long holdViolNs) {
        ParametrosControlador d = ParametrosControlador.padrao();
        return new ParametrosControlador("teste", d.wNs(), d.nLat(), d.nErr(), d.thetaLatIn(), d.thetaLatOut(),
                d.thetaErrIn(), d.thetaErrOut(), d.k(), d.m(), cooldownNs, estavelNs, holdViolNs, null);
    }

    static ParametrosControlador comKm(int k, int m) {
        ParametrosControlador d = ParametrosControlador.padrao();
        return new ParametrosControlador("teste", d.wNs(), d.nLat(), d.nErr(), d.thetaLatIn(), d.thetaLatOut(),
                d.thetaErrIn(), d.thetaErrOut(), k, m, d.cooldownNs(), d.estavelNs(), d.holdViolNs(), null);
    }

    static String rep(char c, int n) {
        return String.valueOf(c).repeat(n);
    }
}
