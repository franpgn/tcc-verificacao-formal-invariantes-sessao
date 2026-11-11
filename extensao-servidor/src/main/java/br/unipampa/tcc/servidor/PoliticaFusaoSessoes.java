package br.unipampa.tcc.servidor;

import br.unipampa.tcc.session.SessionState;
import org.infinispan.conflict.EntryMergePolicy;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.NullCacheEntry;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A1 (2026-11-06): politica de fusao propria do cache {@code sessions-async} (Infinispan Server 15.0.21).
 *
 * <p><b>Problema.</b> Numa particao que isola o isn3 ({@code ASYNC}, {@code DENY_READ_WRITES}), o isn3 confirma
 * logouts antes da deteccao e grava a sessao INVALID. Na fusao, a resolucao de conflitos (CR) do coordenador le as
 * copias dos dois lados e chama {@link #merge}: {@code preferredEntry} e a copia da maioria (VALID) e a do isn3 chega
 * em {@code otherEntries}. Com {@code PREFERRED_NON_NULL}, a VALID vence e o logout se perde (I1).</p>
 *
 * <p><b>Regra.</b> Devolve, entre a preferida e as outras, a entrada <b>maxima na ordem de conteudo</b>
 * ({@link OrdemSessao}: INVALID &gt; VALID; ADMIN_BLOCK &gt; EXPIRADA &gt; LOGOUT; depois versao e instante), sem
 * olhar qual lado e a maioria. Devolve a entrada <b>original</b> recebida (valor e metadados intactos, sem
 * reserializar); com copias iguais, fica a preferida. Chave ausente e o menor valor: sessao criada so de um lado
 * continua.</p>
 *
 * <p><b>Saida de seguranca (A2, falha fechada).</b> Se nao da para decidir com seguranca (valor ilegivel, tipo
 * inesperado, empate que a ordem nao resolve, sids diferentes, erro interno), devolve {@code null}: a CR remove a
 * chave em todos os donos; a sessao some e o usuario entra de novo, mas uma sessao invalidada nunca volta a valer.
 * Cada uso vai ao log em WARN ({@code A1-SAIDA-SEGURANCA}).</p>
 *
 * <p><b>Nunca lanca.</b> Uma excecao aqui interromperia a CR da chave (ou do lote) no coordenador; toda falha vira
 * saida de seguranca ({@link MotivoSaida#ERRO_INTERNO}), e falhas do log sao engolidas.</p>
 *
 * <p><b>Escopo.</b> So o {@code sessions-async}. O {@code counters-async} continua com {@code PREFERRED_NON_NULL}:
 * {@code desbloquear} e {@code resetar} tornam o {@code IdentityState} nao monotono, e escolher um registro inteiro
 * pela ordem desfaria um desbloqueio legitimo (ou faria o contador descer). Guia <i>Configuring Infinispan</i> 15.0,
 * secao 7.6 "Configuring custom merge policies"; contrato em {@code org.infinispan.conflict.EntryMergePolicy}.</p>
 */
public final class PoliticaFusaoSessoes implements EntryMergePolicy<Object, Object> {

    private final RegistroFusao registro;
    private final AtomicBoolean formatoRegistrado = new AtomicBoolean();

    /** Construtor usado pelo servidor (o parser instancia a classe pelo nome em {@code merge-policy}). */
    public PoliticaFusaoSessoes() {
        this(RegistroFusao.log());
        try {
            Package p = PoliticaFusaoSessoes.class.getPackage();
            registro.carregada("A1-POLITICA carregada classe=" + PoliticaFusaoSessoes.class.getName()
                    + " versao=" + (p == null ? null : p.getImplementationVersion())
                    + " carregador=" + PoliticaFusaoSessoes.class.getClassLoader());
        } catch (RuntimeException e) {
            // so log
        }
    }

    PoliticaFusaoSessoes(RegistroFusao registro) {
        this.registro = registro;
    }

    @Override
    public CacheEntry<Object, Object> merge(CacheEntry<Object, Object> preferredEntry,
                                            List<CacheEntry<Object, Object>> otherEntries) {
        Decisao d;
        try {
            d = decidir(preferredEntry, otherEntries);
        } catch (RuntimeException | LinkageError e) {
            d = new Decisao(Decisao.Resultado.SAIDA_SEGURANCA, null, MotivoSaida.ERRO_INTERNO,
                    chaveSegura(preferredEntry, otherEntries), "-", "-", false, "?", "?", e.toString());
        }
        try {
            registro.registrar(d);
        } catch (RuntimeException e) {
            // o log nunca derruba a CR
        }
        return d.escolhida();
    }

    /** Uma copia recebida, com o rotulo do log ({@code P}, {@code O1}, ...) e a leitura do valor. */
    private record Candidato(String rotulo, CacheEntry<Object, Object> entrada, LeitorSessao.Leitura leitura) { }

    private static boolean ausente(CacheEntry<Object, Object> e) {
        return e == null || e instanceof NullCacheEntry;
    }

    Decisao decidir(CacheEntry<Object, Object> preferredEntry, List<CacheEntry<Object, Object>> otherEntries) {
        CacheEntry<Object, Object> preferida = ausente(preferredEntry) ? null : preferredEntry;
        List<CacheEntry<Object, Object>> outras = new ArrayList<>();
        if (otherEntries != null) {
            for (CacheEntry<Object, Object> e : otherEntries) {
                if (!ausente(e)) {
                    outras.add(e);
                }
            }
        }
        // o que PREFERRED_NON_NULL devolveria (MergePolicy.java:12-16), para comparar no log
        CacheEntry<Object, Object> pnn = preferida != null ? preferida : outras.isEmpty() ? null : outras.get(0);

        List<Candidato> cands = new ArrayList<>();
        if (preferida != null) {
            cands.add(candidato("P", preferida));
        }
        for (int i = 0; i < outras.size(); i++) {
            cands.add(candidato("O" + (i + 1), outras.get(i)));
        }
        String chave = cands.isEmpty() ? "-" : LeitorSessao.lerChave(cands.get(0).entrada().getKey());
        String lista = listar(cands);
        String resumoPnn = resumoDe(cands, pnn);

        if (cands.isEmpty()) {
            return new Decisao(Decisao.Resultado.NENHUMA, null, null, chave, "-", "-", true, resumoPnn, lista, "-");
        }
        for (Candidato c : cands) {
            if (!c.leitura().ok()) {
                return saida(c.leitura().falha(), chave, pnn, resumoPnn, lista, c.rotulo() + ": " + c.leitura().detalhe());
            }
        }
        String id = cands.get(0).leitura().sessao().id();
        for (Candidato c : cands) {
            if (!id.equals(c.leitura().sessao().id())) {
                return saida(MotivoSaida.SESSOES_DIVERGENTES, chave, pnn, resumoPnn, lista,
                        cands.get(0).rotulo() + " id=" + id + ", " + c.rotulo() + " id=" + c.leitura().sessao().id());
            }
        }
        Candidato max = cands.get(0);
        for (Candidato c : cands) {
            if (OrdemSessao.CONTEUDO.compare(c.leitura().sessao(), max.leitura().sessao()) > 0) {
                max = c;
            }
        }
        // todas as que empatam com a maxima na ordem precisam ter o mesmo conteudo; fica a preferida se for uma delas
        Candidato escolhido = null;
        for (Candidato c : cands) {
            if (OrdemSessao.CONTEUDO.compare(c.leitura().sessao(), max.leitura().sessao()) == 0) {
                if (!OrdemSessao.mesmoConteudo(c.leitura().sessao(), max.leitura().sessao())) {
                    return saida(MotivoSaida.EMPATE, chave, pnn, resumoPnn, lista,
                            max.rotulo() + " e " + c.rotulo() + " empatam na ordem com conteudo diferente");
                }
                if (escolhido == null) {
                    escolhido = c; // a primeira: P se estiver entre as maximas, senao a primeira outra
                }
            }
        }
        CacheEntry<Object, Object> e = escolhido.entrada();
        return new Decisao(Decisao.Resultado.ESCOLHIDA, e, null, chave, e == preferida ? "preferida" : "outra",
                OrdemSessao.resumo(escolhido.leitura().sessao()), e == pnn, resumoPnn, lista, "-");
    }

    private Candidato candidato(String rotulo, CacheEntry<Object, Object> e) {
        Object valor = e.getValue();
        registrarFormato(valor);
        return new Candidato(rotulo, e, LeitorSessao.ler(valor));
    }

    /** Classe e bytes do primeiro valor recebido: confirma no servidor o formato que a politica assume. */
    private void registrarFormato(Object valor) {
        if (valor == null || !formatoRegistrado.compareAndSet(false, true)) {
            return;
        }
        try {
            byte[] b = LeitorSessao.bytes(valor);
            registro.formato("A1-FORMATO primeiro valor recebido: classe=" + valor.getClass().getName()
                    + (b == null ? "" : " bytes=" + b.length + " hex=" + HexFormat.of().formatHex(b)));
        } catch (RuntimeException e) {
            // so log
        }
    }

    private static Decisao saida(MotivoSaida m, String chave, CacheEntry<Object, Object> pnn, String resumoPnn,
                                 String lista, String detalhe) {
        return new Decisao(Decisao.Resultado.SAIDA_SEGURANCA, null, m, chave, "-", "-", pnn == null, resumoPnn, lista,
                detalhe);
    }

    private static String listar(List<Candidato> cands) {
        StringBuilder sb = new StringBuilder();
        for (Candidato c : cands) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c.rotulo()).append('=').append(c.leitura().ok() ? OrdemSessao.resumo(c.leitura().sessao())
                    : c.leitura().falha());
        }
        return sb.toString();
    }

    private static String resumoDe(List<Candidato> cands, CacheEntry<Object, Object> e) {
        if (e == null) {
            return "-";
        }
        for (Candidato c : cands) {
            if (c.entrada() == e) {
                return c.leitura().ok() ? OrdemSessao.resumo(c.leitura().sessao()) : "?";
            }
        }
        return "?";
    }

    private static String chaveSegura(CacheEntry<Object, Object> p, List<CacheEntry<Object, Object>> outras) {
        try {
            if (!ausente(p)) {
                return LeitorSessao.lerChave(p.getKey());
            }
            if (outras != null) {
                for (CacheEntry<Object, Object> e : outras) {
                    if (!ausente(e)) {
                        return LeitorSessao.lerChave(e.getKey());
                    }
                }
            }
        } catch (RuntimeException e) {
            // cai no "?"
        }
        return "?";
    }
}
