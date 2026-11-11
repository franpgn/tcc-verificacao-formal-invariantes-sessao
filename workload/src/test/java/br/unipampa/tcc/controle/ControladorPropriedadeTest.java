package br.unipampa.tcc.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static br.unipampa.tcc.controle.Roteiro.S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * B-28.1: propriedade sobre 1000 sequencias aleatorias de semente fixa. Cada sequencia alterna
 * regimes calmo e de falha (cadeia de Markov) para gerar entradas e saidas com frequencia; os
 * parametros tambem variam por sequencia.
 */
class ControladorPropriedadeTest {

    private static final long SEMENTE = 20261022L;
    private static final int SEQUENCIAS = 1000;

    @Test
    @DisplayName("propriedade: transicoes separadas por >= cooldown (exceto entradas por R1), saida respeita hold e T_estavel")
    void transicoesEspacadas() {
        Random rnd = new Random(SEMENTE);
        Map<Decisao.Regra, Integer> porRegra = new EnumMap<>(Decisao.Regra.class);
        int r1ComCooldownAtivo = 0;
        for (int s = 0; s < SEQUENCIAS; s++) {
            ParametrosControlador p = parametrosAleatorios(rnd);
            List<Roteiro.Passo> passos = sequenciaAleatoria(rnd, p.wNs(), 60 + rnd.nextInt(240));
            List<Decisao> ds = Roteiro.rodar(p, passos);

            Long ultimaTransicao = null;
            Long ultimaViolacao = null;
            Long ultimaTopAnomala = null;
            Perfil anterior = Perfil.NORMAL;
            for (int i = 0; i < ds.size(); i++) {
                Decisao d = ds.get(i);
                Roteiro.Passo x = passos.get(i);
                long t = x.janela().fimNs();
                assertEquals(t, d.fimNs());
                if (x.janela().violacoesSeguranca() > 0) {
                    ultimaViolacao = t;
                }
                if (x.topologia().anomala()) {
                    ultimaTopAnomala = t;
                }
                assertEquals(d.transicao(), d.perfil() != anterior, ctx(s, i, d));
                if (d.transicao()) {
                    porRegra.merge(d.regra(), 1, Integer::sum);
                    boolean dentroDoCooldown = ultimaTransicao != null && t - ultimaTransicao < p.cooldownNs();
                    if (dentroDoCooldown) {
                        if (d.regra() != Decisao.Regra.R1 || d.perfil() != Perfil.CONSERVADOR) {
                            fail("transicao a menos de um cooldown sem ser entrada por R1: " + ctx(s, i, d));
                        }
                        r1ComCooldownAtivo++;
                    }
                    if (d.perfil() == Perfil.CONSERVADOR) {
                        assertTrue(d.regra() == Decisao.Regra.R1 || d.regra() == Decisao.Regra.R2, ctx(s, i, d));
                    } else {
                        assertEquals(Decisao.Regra.R4, d.regra(), ctx(s, i, d));
                        assertTrue(ultimaViolacao == null || t - ultimaViolacao >= p.holdViolNs(),
                                "saida dentro do hold de violacao: " + ctx(s, i, d));
                        assertTrue(ultimaTopAnomala == null || t - ultimaTopAnomala >= p.estavelNs(),
                                "saida com topologia instavel: " + ctx(s, i, d));
                        assertTrue(d.sinais().janelasCalmas() >= p.m(), ctx(s, i, d));
                    }
                    ultimaTransicao = t;
                }
                if (x.janela().violacoesSeguranca() > 0) {
                    assertEquals(Perfil.CONSERVADOR, d.perfil(), "violacao sem CONSERVADOR: " + ctx(s, i, d));
                }
                anterior = d.perfil();
            }
        }
        // Nao vacuidade: a geracao precisa exercitar todas as regras de transicao e o caso de excecao.
        assertTrue(porRegra.getOrDefault(Decisao.Regra.R1, 0) > 100, "R1: " + porRegra);
        assertTrue(porRegra.getOrDefault(Decisao.Regra.R2, 0) > 100, "R2: " + porRegra);
        assertTrue(porRegra.getOrDefault(Decisao.Regra.R4, 0) > 100, "R4: " + porRegra);
        assertTrue(r1ComCooldownAtivo > 10, "R1 dentro do cooldown: " + r1ComCooldownAtivo);
    }

    private static String ctx(int seq, int janela, Decisao d) {
        return "sequencia " + seq + " janela " + janela + " decisao " + d;
    }

    private static ParametrosControlador parametrosAleatorios(Random rnd) {
        long w = (1 + rnd.nextInt(10)) * S;
        double latIn = 0.03 + rnd.nextDouble() * 0.17;
        double errIn = 0.01 + rnd.nextDouble() * 0.09;
        return new ParametrosControlador("propriedade", w, 50 + rnd.nextInt(100), 20 + rnd.nextInt(60),
                latIn, latIn / 2.5, errIn, errIn / 4,
                1 + rnd.nextInt(3), 1 + rnd.nextInt(8),
                rnd.nextInt(90) * S, rnd.nextInt(90) * S, rnd.nextInt(180) * S, null);
    }

    /** Regime calmo ou de falha, com troca de regime de probabilidade 0,08 por janela. */
    private static List<Roteiro.Passo> sequenciaAleatoria(Random rnd, long w, int n) {
        List<Roteiro.Passo> r = new ArrayList<>(n);
        boolean falha = false;
        long t = rnd.nextInt(1000) * S;
        for (int i = 0; i < n; i++) {
            if (rnd.nextDouble() < 0.08) {
                falha = !falha;
            }
            long total = rnd.nextDouble() < 0.1 ? rnd.nextInt(60) : 200 + rnd.nextInt(2000);
            long eleg = (long) (total * (0.5 + rnd.nextDouble() * 0.5));
            double pExc = falha ? rnd.nextDouble() * 0.3 : rnd.nextDouble() * 0.03;
            double pErr = falha ? rnd.nextDouble() * 0.1 : rnd.nextDouble() * 0.006;
            long exc = Math.min(eleg, Math.round(eleg * pExc));
            long erros = Math.min(total, Math.round(total * pErr));
            long indisp = erros == 0 ? 0 : rnd.nextInt((int) erros + 1);
            long viol = rnd.nextDouble() < (falha ? 0.05 : 0.002) ? 1 + rnd.nextInt(3) : 0;
            long i2esp = rnd.nextDouble() < 0.1 ? rnd.nextInt(5) : 0;
            EstadoTopologia top;
            double u = rnd.nextDouble();
            if (u < 0.05) {
                top = EstadoTopologia.indisponivel();
            } else if (falha && u < 0.35) {
                top = new EstadoTopologia(true, 3, 1 + rnd.nextInt(3), 2);
            } else {
                top = new EstadoTopologia(true, 2 + rnd.nextInt(2), 0, 3);
            }
            r.add(new Roteiro.Passo(new Janela(t, t + w, total, erros, indisp, eleg, exc, viol, i2esp), top));
            t += w;
        }
        return r;
    }
}
