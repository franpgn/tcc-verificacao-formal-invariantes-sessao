package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.infinispan.commons.marshall.WrappedByteArray;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.NullCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1: a politica de fusao do {@code sessions-async}. Escolhe, entre a preferida e as outras, a entrada maxima na ordem
 * de conteudo (INVALID &gt; VALID; ADMIN_BLOCK &gt; EXPIRADA &gt; LOGOUT; depois versao e instante), devolve a entrada
 * original (sem reserializar) e, quando nao da para decidir com seguranca, remove a chave (A2, falha fechada) e
 * registra a saida de seguranca. Valores com os bytes reais do cliente ({@code valores-cliente.tsv}).
 */
class PoliticaFusaoSessoesTest {

    /** Registro em memoria: guarda cada decisao e as linhas de formato. */
    static final class Captura implements RegistroFusao {
        final List<Decisao> decisoes = new ArrayList<>();
        final List<String> formatos = new ArrayList<>();

        @Override public void registrar(Decisao d) { decisoes.add(d); }
        @Override public void formato(String linha) { formatos.add(linha); }

        Decisao unica() {
            assertEquals(1, decisoes.size(), "uma decisao por chamada");
            return decisoes.get(0);
        }
    }

    private Captura registro;
    private PoliticaFusaoSessoes politica;

    @BeforeEach
    void nova() {
        registro = new Captura();
        politica = new PoliticaFusaoSessoes(registro);
    }

    private CacheEntry<Object, Object> fundir(CacheEntry<Object, Object> preferida, List<CacheEntry<Object, Object>> outras) {
        registro.decisoes.clear();
        return politica.merge(preferida, outras);
    }

    private static int posto(String rotulo) {
        int p = ValoresCliente.ORDEM.indexOf(rotulo);
        if (p < 0) {
            throw new IllegalArgumentException(rotulo);
        }
        return p;
    }

    @Test
    @DisplayName("o caso do I1: maioria VALID (preferida) e isn3 com LOGOUT confirmado (outra): vence o LOGOUT do isn3")
    void logoutDoIsn3Vence() {
        CacheEntry<Object, Object> maioria = ValoresCliente.entrada("valid-v1");
        CacheEntry<Object, Object> isn3 = ValoresCliente.entrada("logout-v2");
        assertSame(isn3, fundir(maioria, List.of(isn3)));
        Decisao d = registro.unica();
        assertEquals(Decisao.Resultado.ESCOLHIDA, d.resultado());
        assertFalse(d.igualPreferredNonNull(), "PREFERRED_NON_NULL devolveria a VALID da maioria");
        assertTrue(d.linha().contains("A1-FUSAO"), d.linha());
        assertTrue(d.linha().contains("chave=7b0c2f4e-1d3a-4e5b-9c6d-0a1b2c3d4e5f"), d.linha());
        assertTrue(d.linha().contains("igual_pnn=nao"), d.linha());
        assertTrue(d.linha().contains("vencedora=outra"), d.linha());
        assertTrue(d.linha().contains("pnn=VALID/NONE/v1"), d.linha());
        assertTrue(d.linha().contains("escolhida=INVALID/LOGOUT/v2"), d.linha());
    }

    @Test
    @DisplayName("pares do arquivo: a maxima vence, seja ela a preferida ou a outra, e e a mesma instancia recebida")
    void pares() {
        for (String a : ValoresCliente.ORDEM) {
            for (String b : ValoresCliente.ORDEM) {
                CacheEntry<Object, Object> ea = ValoresCliente.entrada(a);
                CacheEntry<Object, Object> eb = ValoresCliente.entrada(b);
                CacheEntry<Object, Object> r = fundir(ea, List.of(eb));
                CacheEntry<Object, Object> esperada = posto(b) > posto(a) ? eb : ea;
                assertSame(esperada, r, a + " x " + b);
                assertEquals(Decisao.Resultado.ESCOLHIDA, registro.unica().resultado(), a + " x " + b);
            }
        }
    }

    /** Todas as sequencias de comprimento {@code n} sobre {@code rotulos} (com repeticao e todas as ordens). */
    private static List<List<String>> sequencias(List<String> rotulos, int n) {
        List<List<String>> out = new ArrayList<>();
        if (n == 0) {
            out.add(List.of());
            return out;
        }
        for (List<String> pre : sequencias(rotulos, n - 1)) {
            for (String r : rotulos) {
                List<String> l = new ArrayList<>(pre);
                l.add(r);
                out.add(l);
            }
        }
        return out;
    }

    @Test
    @DisplayName("todas as combinacoes: preferida (ou nula) x ate 3 outras, em todas as ordens, com o empate incluido")
    void todasAsCombinacoes() {
        List<String> rotulos = new ArrayList<>(ValoresCliente.ORDEM);
        rotulos.add(ValoresCliente.EMPATE);
        List<String> preferidas = new ArrayList<>(rotulos);
        preferidas.add(null);
        int casos = 0;
        int saidas = 0;
        for (String p : preferidas) {
            for (int n = 0; n <= 3; n++) {
                for (List<String> outrasRot : sequencias(rotulos, n)) {
                    CacheEntry<Object, Object> ep = p == null ? null : ValoresCliente.entrada(p);
                    List<CacheEntry<Object, Object>> outras = new ArrayList<>();
                    for (String o : outrasRot) {
                        outras.add(ValoresCliente.entrada(o));
                    }
                    List<CacheEntry<Object, Object>> todas = new ArrayList<>();
                    List<String> todasRot = new ArrayList<>();
                    if (ep != null) {
                        todas.add(ep);
                        todasRot.add(p);
                    }
                    todas.addAll(outras);
                    todasRot.addAll(outrasRot);
                    CacheEntry<Object, Object> r = fundir(ep, outras);
                    Decisao d = registro.unica();
                    casos++;
                    String caso = "preferida=" + p + " outras=" + outrasRot;
                    if (todas.isEmpty()) {
                        assertNull(r, caso);
                        assertEquals(Decisao.Resultado.NENHUMA, d.resultado(), caso);
                        continue;
                    }
                    // oraculo: posto maximo; o EMPATE tem o posto de valid-v1
                    int max = -1;
                    for (String t : todasRot) {
                        max = Math.max(max, posto(t.equals(ValoresCliente.EMPATE) ? "valid-v1" : t));
                    }
                    final int m = max;
                    boolean ambiguo = max == posto("valid-v1") && todasRot.contains(ValoresCliente.EMPATE)
                            && todasRot.contains("valid-v1");
                    if (ambiguo) {
                        assertNull(r, caso);
                        assertEquals(Decisao.Resultado.SAIDA_SEGURANCA, d.resultado(), caso);
                        assertEquals(MotivoSaida.EMPATE, d.motivoSaida(), caso);
                        saidas++;
                        continue;
                    }
                    assertNotNull(r, caso);
                    assertEquals(Decisao.Resultado.ESCOLHIDA, d.resultado(), caso);
                    assertTrue(todas.stream().anyMatch(e -> e == r), "devolve uma das recebidas: " + caso);
                    String vencedor = todasRot.get(indiceDe(todas, r));
                    int pv = posto(vencedor.equals(ValoresCliente.EMPATE) ? "valid-v1" : vencedor);
                    assertEquals(m, pv, caso + " -> " + vencedor);
                    if (p != null && (p.equals(vencedor))) {
                        assertSame(ep, r, "com copias iguais, fica a preferida: " + caso);
                    }
                }
            }
        }
        assertEquals(10 * (1 + 9 + 81 + 729), casos);
        assertTrue(saidas > 0);
    }

    private static int indiceDe(List<CacheEntry<Object, Object>> l, CacheEntry<Object, Object> e) {
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == e) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("semirreticulado: comutativa, associativa e idempotente sobre os valores do arquivo")
    void semirreticulado() {
        for (String a : ValoresCliente.ORDEM) {
            assertTrue(OrdemSessao.mesmoConteudo(ValoresCliente.sessao(a), juntar(a, a)), "idempotente " + a);
            for (String b : ValoresCliente.ORDEM) {
                assertTrue(OrdemSessao.mesmoConteudo(juntar(a, b), juntar(b, a)), "comutativa " + a + "," + b);
                for (String c : ValoresCliente.ORDEM) {
                    SessionState esq = juntarSessoes(juntar(a, b), ValoresCliente.sessao(c));
                    SessionState dir = juntarSessoes(ValoresCliente.sessao(a), juntar(b, c));
                    assertTrue(OrdemSessao.mesmoConteudo(esq, dir), "associativa " + a + "," + b + "," + c);
                }
            }
        }
    }

    private SessionState juntar(String a, String b) {
        return juntarSessoes(ValoresCliente.sessao(a), ValoresCliente.sessao(b));
    }

    private SessionState juntarSessoes(SessionState a, SessionState b) {
        CacheEntry<Object, Object> r = fundir(ValoresCliente.entradaCom(a), List.of(ValoresCliente.entradaCom(b)));
        return (SessionState) r.getValue();
    }

    @Test
    @DisplayName("devolve a entrada original: mesmo objeto de valor e mesmos metadados, sem reserializar")
    void entradaOriginal() {
        CacheEntry<Object, Object> isn3 = ValoresCliente.entrada("admin-v2");
        Object valor = isn3.getValue();
        CacheEntry<Object, Object> r = fundir(ValoresCliente.entrada("logout-v2"), List.of(isn3));
        assertSame(isn3, r);
        assertSame(valor, r.getValue());
        assertSame(isn3.getMetadata(), r.getMetadata());
    }

    @Test
    @DisplayName("nao depende da ordem das outras nem de qual copia e a preferida")
    void independeDaOrdem() {
        List<String> tres = List.of("valid-v1", "logout-v2", "valid-v5");
        for (String p : tres) {
            List<String> resto = new ArrayList<>(tres);
            resto.remove(p);
            for (int k = 0; k < 2; k++) {
                Collections.reverse(resto);
                List<CacheEntry<Object, Object>> outras = new ArrayList<>();
                for (String o : resto) {
                    outras.add(ValoresCliente.entrada(o));
                }
                CacheEntry<Object, Object> r = fundir(ValoresCliente.entrada(p), outras);
                assertTrue(OrdemSessao.mesmoConteudo(ValoresCliente.sessao("logout-v2"),
                        LeitorSessao.ler(r.getValue()).sessao()), p + " " + resto);
            }
        }
    }

    @Test
    @DisplayName("entrada nula: preferida nula fica com a maxima das outras; nada recebido devolve nulo sem saida de seguranca")
    void entradaNula() {
        CacheEntry<Object, Object> isn3 = ValoresCliente.entrada("logout-v2");
        assertSame(isn3, fundir(null, List.of(isn3)));
        assertEquals(Decisao.Resultado.ESCOLHIDA, registro.unica().resultado());
        assertTrue(registro.unica().igualPreferredNonNull(), "com a preferida nula, PREFERRED_NON_NULL pega a primeira outra");

        CacheEntry<Object, Object> soIsn3 = ValoresCliente.entrada("valid-v1");
        assertSame(soIsn3, fundir(null, List.of(soIsn3)), "sessao criada so de um lado continua (NONE < VALID)");

        assertNull(fundir(null, List.of()));
        assertEquals(Decisao.Resultado.NENHUMA, registro.unica().resultado());
        assertNull(fundir(null, null));
        assertEquals(Decisao.Resultado.NENHUMA, registro.unica().resultado());
    }

    @Test
    @DisplayName("NullCacheEntry na preferida e elementos nulos na lista contam como ausentes")
    void nullCacheEntry() {
        CacheEntry<Object, Object> isn3 = ValoresCliente.entrada("expirada-v2");
        CacheEntry<Object, Object> nula = NullCacheEntry.getInstance();
        assertSame(isn3, fundir(nula, List.of(isn3)));
        assertSame(isn3, fundir(null, Arrays.asList(null, isn3, nula)));
        assertNull(fundir(nula, List.of(nula)));
        assertEquals(Decisao.Resultado.NENHUMA, registro.unica().resultado());
    }

    private void saida(CacheEntry<Object, Object> preferida, List<CacheEntry<Object, Object>> outras, MotivoSaida motivo) {
        assertNull(fundir(preferida, outras), "remove a chave: " + motivo);
        Decisao d = registro.unica();
        assertEquals(Decisao.Resultado.SAIDA_SEGURANCA, d.resultado());
        assertEquals(motivo, d.motivoSaida(), d.linha());
        assertTrue(d.linha().startsWith("A1-SAIDA-SEGURANCA "), d.linha());
        assertTrue(d.linha().contains("motivo=" + motivo.name()), d.linha());
        assertTrue(d.linha().contains("chave=7b0c2f4e-1d3a-4e5b-9c6d-0a1b2c3d4e5f"), d.linha());
    }

    @Test
    @DisplayName("saida de seguranca: valor ilegivel em qualquer copia remove a chave, mesmo com um INVALID legivel")
    void saidaIlegivel() {
        CacheEntry<Object, Object> lixo = ValoresCliente.entradaCom(new WrappedByteArray(new byte[]{9, 9, 9}));
        saida(ValoresCliente.entrada("valid-v1"), List.of(lixo), MotivoSaida.VALOR_ILEGIVEL);
        saida(lixo, List.of(ValoresCliente.entrada("admin-v2")), MotivoSaida.VALOR_ILEGIVEL);
        saida(ValoresCliente.entradaCom(null), List.of(ValoresCliente.entrada("valid-v1")), MotivoSaida.VALOR_ILEGIVEL);
    }

    @Test
    @DisplayName("saida de seguranca: tipo inesperado (IdentityState do cliente, String, Integer)")
    void saidaTipo() {
        saida(ValoresCliente.entrada("valid-v1"), List.of(ValoresCliente.entrada("identidade-bloqueada")),
                MotivoSaida.TIPO_INESPERADO);
        saida(ValoresCliente.entrada("chave-sid"), List.of(), MotivoSaida.TIPO_INESPERADO);
        saida(ValoresCliente.entradaCom(7), List.of(ValoresCliente.entrada("logout-v2")), MotivoSaida.TIPO_INESPERADO);
    }

    @Test
    @DisplayName("saida de seguranca: empate que a ordem nao resolve (mesma ordem, identidade diferente)")
    void saidaEmpate() {
        saida(ValoresCliente.entrada("valid-v1"), List.of(ValoresCliente.entrada(ValoresCliente.EMPATE)),
                MotivoSaida.EMPATE);
    }

    @Test
    @DisplayName("empate abaixo da maxima nao importa: o LOGOUT vence e nao ha saida de seguranca")
    void empateAbaixoDaMaxima() {
        CacheEntry<Object, Object> isn3 = ValoresCliente.entrada("logout-v2");
        assertSame(isn3, fundir(ValoresCliente.entrada("valid-v1"),
                List.of(ValoresCliente.entrada(ValoresCliente.EMPATE), isn3)));
        assertEquals(Decisao.Resultado.ESCOLHIDA, registro.unica().resultado());
    }

    @Test
    @DisplayName("copias iguais (mesmos bytes) nao sao empate: fica a preferida")
    void copiasIguais() {
        CacheEntry<Object, Object> p = ValoresCliente.entrada("logout-v2");
        assertSame(p, fundir(p, List.of(ValoresCliente.entrada("logout-v2"), ValoresCliente.entrada("valid-v1"))));
        CacheEntry<Object, Object> o1 = ValoresCliente.entrada("admin-v2");
        assertSame(o1, fundir(null, List.of(o1, ValoresCliente.entrada("admin-v2"))), "sem preferida, a primeira");
    }

    @Test
    @DisplayName("saida de seguranca: sessoes de sids diferentes sob a mesma chave")
    void saidaSidsDiferentes() {
        saida(ValoresCliente.entrada("logout-v2"), List.of(ValoresCliente.entrada("outra-sid-logout-v2")),
                MotivoSaida.SESSOES_DIVERGENTES);
    }

    @Test
    @DisplayName("nunca lanca: erro dentro da decisao vira saida ERRO_INTERNO; erro no registro e engolido")
    void nuncaLanca() {
        Object chave = ValoresCliente.chave();
        @SuppressWarnings("unchecked")
        CacheEntry<Object, Object> quebrada = (CacheEntry<Object, Object>) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CacheEntry.class}, (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "getKey": return chave;
                        case "getValue": throw new IllegalStateException("falha simulada");
                        case "isNull": return false;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "entrada-quebrada";
                        default: return null;
                    }
                });
        saida(quebrada, List.of(ValoresCliente.entrada("logout-v2")), MotivoSaida.ERRO_INTERNO);

        PoliticaFusaoSessoes comRegistroQuebrado = new PoliticaFusaoSessoes(new RegistroFusao() {
            @Override public void registrar(Decisao d) { throw new IllegalStateException("log quebrado"); }
            @Override public void formato(String linha) { throw new IllegalStateException("log quebrado"); }
        });
        CacheEntry<Object, Object> isn3 = ValoresCliente.entrada("logout-v2");
        assertSame(isn3, assertDoesNotThrow(() -> comRegistroQuebrado.merge(ValoresCliente.entrada("valid-v1"), List.of(isn3))));
    }

    @Test
    @DisplayName("formato do primeiro valor recebido vai ao registro uma vez, com a classe e o hexadecimal")
    void formatoUmaVez() {
        fundir(ValoresCliente.entrada("valid-v1"), List.of(ValoresCliente.entrada("logout-v2")));
        fundir(ValoresCliente.entrada("valid-v1"), List.of(ValoresCliente.entrada("admin-v2")));
        assertEquals(1, registro.formatos.size(), registro.formatos.toString());
        String f = registro.formatos.get(0);
        assertTrue(f.startsWith("A1-FORMATO "), f);
        assertTrue(f.contains("classe=org.infinispan.commons.marshall.WrappedByteArray"), f);
        assertTrue(f.contains("hex=aced0005"), f);
    }

    @Test
    @DisplayName("construtor publico sem argumentos (o servidor instancia a classe pelo nome)")
    void construtorPublico() throws Exception {
        Object p = PoliticaFusaoSessoes.class.getConstructor().newInstance();
        assertTrue(p instanceof org.infinispan.conflict.EntryMergePolicy);
    }
}
