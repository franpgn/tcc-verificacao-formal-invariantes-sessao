package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.EventoK3;
import br.unipampa.tcc.controle.ParametrosKnobs;
import br.unipampa.tcc.controle.Perfil;
import br.unipampa.tcc.controle.PerfilAtual;
import br.unipampa.tcc.controle.PortaoDeEmissao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-27: o portao na simulacao S3 com relogio falso. P1: portao real em NORMAL permanente produz a
 * mesma lista de {@link AcaoS3} (10 componentes) e as mesmas chamadas ao armazenamento que
 * {@link PortaoDeEmissao#livre()}. P3 (restricao do plano, secao 4): em CONSERVADOR nada e criado
 * nem suprimido; o K3 so atrasa. K2b: o multiplicador do pensar age depois do sorteio.
 */
class PortaoS3SimulacaoTest {

    static final long MS = 1_000_000L;

    private static PortaoDeEmissao portaoReal(MundoS3 m, PerfilAtual perfil, List<EventoK3> eventos) {
        return new PortaoDeEmissao(perfil, ParametrosKnobs.padrao(), 31, m.relogio::agoraNs, m.relogio::dormirNs,
                eventos::add, 99L);
    }

    private static String norm(String chave) {
        return chave.startsWith("s3-") || chave.isEmpty() ? chave : "sid";
    }

    /** Liga o registro das chamadas ao armazenamento (instante, mapa, operacao, chave normalizada). */
    private static List<String> gravarArmazem(MundoS3 m) {
        List<String> log = new ArrayList<>();
        m.sessoes.antes = (op, chave) -> log.add(m.relogio.agora + " S " + op + " " + norm(chave));
        m.identidades.antes = (op, chave) -> log.add(m.relogio.agora + " I " + op + " " + norm(chave));
        return log;
    }

    private static List<String> acoes(MundoS3 m) {
        List<String> out = new ArrayList<>();
        for (AcaoS3 a : m.registro.acoes()) {
            out.add(a.tNs() + "|" + a.ator() + "|" + a.operacao() + "|" + norm(a.alvo()) + "|" + a.resultado() + "|"
                    + a.tentativas() + "|" + a.falhaPercebida() + "|" + a.nota() + "|" + a.tFimNs() + "|"
                    + a.esperaPortaoNs());
        }
        return out;
    }

    @Test
    @DisplayName("P1: S3 com portao real em NORMAL permanente = S3 com livre(): mesmas acoes (10 componentes) e mesmas chamadas ao armazenamento")
    void normalIgualADesligado() {
        MundoS3 livre = new MundoS3(7, null);
        livre.foraDoAr(2, 100, 160);
        List<String> logLivre = gravarArmazem(livre);
        livre.rodar(400);

        MundoS3 normal = new MundoS3(7, null);
        normal.foraDoAr(2, 100, 160);
        PerfilAtual perfil = new PerfilAtual();
        List<EventoK3> eventos = new ArrayList<>();
        PortaoDeEmissao portao = portaoReal(normal, perfil, eventos);
        normal.ctx = new ContextoS3(normal.relogio, normal.p, normal.registro, 7, portao);
        List<String> logNormal = gravarArmazem(normal);
        normal.rodar(400);

        List<String> a = acoes(livre);
        assertTrue(a.size() > 300, "a simulacao produziu acoes: " + a.size());
        assertTrue(a.stream().anyMatch(x -> x.contains("ERROR_TRANSPORT") || x.contains("|2|") || x.contains("|3|")),
                "a janela fora do ar provocou retentativas");
        assertEquals(a, acoes(normal), "acoes");
        assertEquals(logLivre, logNormal, "chamadas ao armazenamento");
        assertEquals(List.of(), eventos, "NORMAL nao adia");
        assertEquals(0, portao.emVoo());
        assertEquals(0L, portao.esperaK1TotalNs());
    }

    // ------------------------------------------------------------------ P3 (restricao secao 4)

    /** Um passo de roteiro: ator, operacao, alvo. */
    private record Passo(String ator, String op, String alvo) { }

    private static final List<List<Passo>> ROTEIRO = List.of(
            List.of(new Passo("admin", "Block", "s3-u-001"), new Passo("admin", "Unblock", "s3-u-001"),
                    new Passo("admin", "Block", "s3-u-002"), new Passo("admin", "Unblock", "s3-u-002")),
            List.of(new Passo("suporte", "Unblock", "s3-l-000"), new Passo("suporte", "Unblock", "s3-l-001")),
            List.of(new Passo("s3-l-000", "IncrementFailure", "s3-l-000"),
                    new Passo("s3-l-000", "IncrementFailure", "s3-l-000"),
                    new Passo("s3-l-000", "ResetFailures", "s3-l-000"), new Passo("s3-l-000", "Login", "s3-l-000")),
            List.of(new Passo("s3-u-000", "Login", "s3-u-000"), new Passo("s3-u-000", "Validate", "sid-a"),
                    new Passo("s3-u-000", "Validate", "sid-a"), new Passo("s3-u-000", "Validate", "sid-a"),
                    new Passo("s3-u-000", "Logout", "sid-a")));

    /** Resultado de um roteiro: chamadas ao armazenamento e acoes registradas, por ator. */
    private record Saida(Map<String, List<String>> chamadas, Map<String, List<String>> acoes, long fimNs,
                         int eventosK3) { }

    /**
     * Executa o roteiro intercalado (round-robin pelos atores) com o perfil dado por passo
     * ({@code perfilNoPasso}); a i-esima tentativa da k-esima operacao de cada ator falha de forma
     * deterministica (independente do tempo).
     */
    private static Saida executarRoteiro(boolean comPortao, java.util.function.IntFunction<Perfil> perfilNoPasso) {
        RelogioFalso rel = new RelogioFalso(1_000_000_000_000L);
        S3Parametros p = new S3Parametros();
        S3Registro reg = new S3Registro();
        PerfilAtual perfil = new PerfilAtual();
        List<EventoK3> eventos = new ArrayList<>();
        PortaoDeEmissao portao = comPortao
                ? new PortaoDeEmissao(perfil, ParametrosKnobs.padrao(), 4, rel::agoraNs, rel::dormirNs, eventos::add, 3L)
                : PortaoDeEmissao.livre();
        AplicacaoS3 app = new AplicacaoS3(rel, p, reg, portao);
        Map<String, List<String>> chamadas = new LinkedHashMap<>();
        int[] proximo = new int[ROTEIRO.size()];
        int passo = 0;
        boolean resta = true;
        while (resta) {
            resta = false;
            for (int a = 0; a < ROTEIRO.size(); a++) {
                List<Passo> r = ROTEIRO.get(a);
                if (proximo[a] >= r.size()) {
                    continue;
                }
                resta = true;
                perfil.definir(perfilNoPasso.apply(passo++));
                final int k = proximo[a]++;
                Passo ps = r.get(k);
                int[] tentativa = {0};
                int retent = ps.op().equals("Block") ? p.adminRetentativasBlock : p.appRetentativas;
                app.executar(ps.ator(), ps.op(), ps.alvo(), retent, () -> {
                    int t = ++tentativa[0];
                    chamadas.computeIfAbsent(ps.ator(), x -> new ArrayList<>())
                            .add(ps.op() + " " + ps.alvo() + " #" + k + " t" + t);
                    rel.agora += 50 * MS;
                    boolean erro = t <= 2 && Math.floorMod((ps.ator() + "|" + k + "|" + t).hashCode(), 3) == 0;
                    return erro ? OpResult.errorTransport() : OpResult.okNoop();
                });
            }
        }
        Map<String, List<String>> acoes = new LinkedHashMap<>();
        for (AcaoS3 x : reg.acoes()) {
            acoes.computeIfAbsent(x.ator(), y -> new ArrayList<>()).add(x.operacao() + " " + x.alvo() + " "
                    + x.resultado() + " " + x.tentativas() + " " + x.falhaPercebida() + " " + x.nota());
        }
        return new Saida(chamadas, acoes, rel.agora, eventos.size());
    }

    private static List<String> multiconjunto(Map<String, List<String>> porAtor) {
        List<String> out = new ArrayList<>();
        porAtor.forEach((ator, l) -> l.forEach(x -> out.add(ator + " " + x)));
        Collections.sort(out);
        return out;
    }

    @Test
    @DisplayName("P3: em CONSERVADOR (permanente ou por trechos) as operacoes emitidas por ator sao as mesmas de NORMAL; K3 so atrasa")
    void restricaoSecao4() {
        Saida livre = executarRoteiro(false, i -> Perfil.NORMAL);
        Saida normal = executarRoteiro(true, i -> Perfil.NORMAL);
        Saida permanente = executarRoteiro(true, i -> Perfil.CONSERVADOR);
        Saida trechos = executarRoteiro(true, i -> i >= 3 && i < 11 ? Perfil.CONSERVADOR : Perfil.NORMAL);
        assertTrue(multiconjunto(livre.chamadas()).stream().anyMatch(x -> x.endsWith(" t2")),
                "o roteiro provoca retentativas");
        assertEquals(15, livre.acoes().values().stream().mapToInt(List::size).sum());
        for (Saida s : List.of(normal, permanente, trechos)) {
            assertEquals(livre.chamadas(), s.chamadas(), "chamadas por ator, na ordem de emissao");
            assertEquals(multiconjunto(livre.chamadas()), multiconjunto(s.chamadas()), "multiconjunto de chamadas");
            assertEquals(livre.acoes(), s.acoes(), "acoes registradas por ator");
        }
        assertEquals(livre.fimNs(), normal.fimNs(), "NORMAL nao muda o tempo");
        assertEquals(0, normal.eventosK3());
        assertEquals(9, permanente.eventosK3(), "as 9 administrativas adiadas e liberadas");
        assertTrue(trechos.eventosK3() > 0 && trechos.eventosK3() < 9, "adiadas so no trecho: " + trechos.eventosK3());
        assertTrue(permanente.fimNs() > livre.fimNs(), "K3 atrasa");
    }

    @Test
    @DisplayName("P3: simulacao S3 em CONSERVADOR permanente: cada administrativa registrada teve um EventoK3, na mesma ordem, e nenhum evento sem acao")
    void simulacaoConservador() {
        MundoS3 m = new MundoS3(11, null);
        m.foraDoAr(2, 100, 160);
        PerfilAtual perfil = new PerfilAtual();
        perfil.definir(Perfil.CONSERVADOR);
        List<EventoK3> eventos = new ArrayList<>();
        m.ctx = new ContextoS3(m.relogio, m.p, m.registro, 11, portaoReal(m, perfil, eventos));
        m.rodar(400);
        Set<String> adiaveis = Set.of("Block", "Unblock", "IncrementFailure", "ResetFailures");
        List<String> acoesAdm = new ArrayList<>();
        for (AcaoS3 a : m.registro.acoes()) {
            if (adiaveis.contains(a.operacao())) {
                acoesAdm.add(a.ator() + " " + a.operacao() + " " + a.alvo());
                assertTrue(a.esperaPortaoNs() > 0, "administrativa sem espera do K3: " + a);
            } else {
                assertEquals(0, a.esperaPortaoNs(), "Login/Validate/Logout nao esperam: " + a);
            }
            assertTrue(a.tFimNs() >= a.tNs() + a.esperaPortaoNs());
        }
        List<String> evs = new ArrayList<>();
        for (EventoK3 e : eventos) {
            evs.add(e.ator() + " " + e.operacao() + " " + e.alvo());
        }
        assertTrue(acoesAdm.size() > 10, "administrativas: " + acoesAdm.size());
        assertEquals(acoesAdm, evs);
    }

    // ------------------------------------------------------------------ K2b (S3)

    /** Intervalos de pensar (proximoNs - agora depois de cada passo) e as acoes de um usuario. */
    private static List<Long> intervalos(Perfil perfilFixo, List<String> acoes) {
        MundoS3 m = new MundoS3(5, p -> {
            p.sessaoMedianaSeg = 1e7;
            p.idleSeg = 1e7;
        });
        PerfilAtual perfil = new PerfilAtual();
        perfil.definir(perfilFixo);
        m.ctx = new ContextoS3(m.relogio, m.p, m.registro, 5, portaoReal(m, perfil, new ArrayList<>()));
        UsuarioVirtual u = new UsuarioVirtual(0, m.apps.get(0), m.ctx, m.relogio.agora);
        List<Long> out = new ArrayList<>();
        out.add(u.proximoNs() - m.relogio.agora);
        for (int i = 0; i < 60; i++) {
            m.relogio.dormirAteNs(u.proximoNs());
            u.passo();
            out.add(u.proximoNs() - m.relogio.agora);
        }
        for (AcaoS3 a : m.registro.acoes()) {
            acoes.add(a.operacao() + " " + a.resultado());
        }
        return out;
    }

    @Test
    @DisplayName("K2b: a sequencia de sorteios do usuario e a mesma com e sem CONSERVADOR; o pensar em CONSERVADOR e max(1 ms, round(1,5 x))")
    void pensarDepoisDoSorteio() {
        List<String> acoesN = new ArrayList<>();
        List<String> acoesC = new ArrayList<>();
        List<Long> n = intervalos(Perfil.NORMAL, acoesN);
        List<Long> c = intervalos(Perfil.CONSERVADOR, acoesC);
        assertEquals(acoesN, acoesC, "mesmas acoes (mesmos sorteios)");
        assertEquals(n.get(0), c.get(0), "chegada escalonada (antes do pensar) igual");
        for (int i = 1; i < n.size(); i++) {
            assertEquals(Math.max(MS, Math.round(n.get(i) * 1.5)), c.get(i), "intervalo " + i);
        }
    }
}
