package br.unipampa.tcc.session;

import br.unipampa.tcc.session.RastroCliente.Bloqueio;
import br.unipampa.tcc.session.RastroCliente.EventoAdmin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * B-33 (E1, item 4): varredura final global, depois da convergencia. Percorre
 * <b>todas</b> as chaves lidas do grid (nao so as sids que o cliente conhece) e
 * registra:
 *
 * <ul>
 *   <li><b>I1</b>: sid com invalidacao confirmada (por esta instancia) hoje {@code VALID};</li>
 *   <li><b>I4</b>: sid invalidada cujo motivo lido difere do confirmado;</li>
 *   <li><b>I2 global</b>: sessao {@code VALID} de identidade {@code BLOCKED}, classificada
 *       pelo motivo do bloqueio (Q-10, {@link RastroCliente#classificarFinal}):
 *       {@code REAL} (administrativo), {@code ESPERADA-POR-DESENHO} (por limite, P-27),
 *       {@code INCERTA} (Block com erro), {@code INEXPLICADA} (anomalia);</li>
 *   <li><b>I2-PERDIDO</b> (bloqueio perdido): ultimo evento confirmado da identidade e um
 *       Block e a identidade esta {@code OPEN} (ou ausente), sem Unblock confirmado
 *       posterior: {@code REAL}; se houve Unblock com erro depois, {@code INCERTA}
 *       (efeito desconhecido; nunca somada como violacao).</li>
 * </ul>
 *
 * <p>Tudo e calculado a partir de mapas em memoria injetados, sem acesso ao cluster
 * (a leitura e feita por {@code WorkloadMain}).</p>
 */
public final class VarreduraFinal {

    public record Achado(String invariante, String chave, String classificacao, String detalhe) {}

    public record IdentidadeVarrida(String identidade, String status, long contador,
                                    String classificacao) {}

    public record Resultado(List<Achado> achados, List<IdentidadeVarrida> identidades,
                            Map<Bloqueio, Integer> bloqueiosPorMotivo, int identidadesComBlockConfirmado,
                            int sessoesVarridas, int identidadesVarridas) {

        /** Achados {@code REAL} (violacoes de fato) por invariante. */
        public Map<String, Integer> violacoesReaisPorInvariante() {
            Map<String, Integer> m = new TreeMap<>();
            for (Achado a : achados) {
                if (a.classificacao().equals("REAL")) {
                    m.merge(a.invariante(), 1, Integer::sum);
                }
            }
            return m;
        }

        /** Texto para o log: deixa explicito a origem dos bloqueios (S1/S2: todo BLOCKED e por limite). */
        public String resumo() {
            StringBuilder sb = new StringBuilder();
            sb.append("Varredura final: sessoes=").append(sessoesVarridas)
              .append(", identidades=").append(identidadesVarridas)
              .append(", achados=").append(achados.size()).append("; REAL=")
              .append(violacoesReaisPorInvariante()).append("; BLOCKED por motivo=")
              .append(bloqueiosPorMotivo);
            if (identidadesComBlockConfirmado == 0) {
                sb.append("; nenhum Block administrativo confirmado: todo BLOCKED observado e por limite");
            }
            return sb.toString();
        }
    }

    private VarreduraFinal() { /* utilitario */ }

    public static Resultado varrer(Map<String, SessionState> sessoes,
                                   Map<String, IdentityState> identidades,
                                   Map<String, SessionState.Motivo> invalidacoesConfirmadas,
                                   RastroCliente rastro, int limiteTentativas) {
        List<Achado> achados = new ArrayList<>();

        // I1 e I4: sobre as sids com invalidacao confirmada pelo cliente.
        for (Map.Entry<String, SessionState.Motivo> e : invalidacoesConfirmadas.entrySet()) {
            SessionState lido = sessoes.get(e.getKey());
            if (lido == null) {
                continue;
            }
            if (lido.estado() == SessionState.Estado.VALID) {
                achados.add(new Achado("I1", e.getKey(), "REAL",
                        "invalidacao confirmada (" + e.getValue() + ") e a sessao esta VALID"));
            } else if (!e.getValue().equals(lido.motivo())) {
                achados.add(new Achado("I4", e.getKey(), "REAL",
                        "motivo confirmado " + e.getValue() + ", lido " + lido.motivo()));
            }
        }

        // Classificacao de cada identidade BLOCKED.
        Map<Bloqueio, Integer> porMotivo = new EnumMap<>(Bloqueio.class);
        for (Bloqueio b : Bloqueio.values()) {
            porMotivo.put(b, 0);
        }
        Map<String, Bloqueio> classe = new TreeMap<>();
        List<IdentidadeVarrida> idsVarridas = new ArrayList<>();
        for (Map.Entry<String, IdentityState> e : new TreeMap<>(identidades).entrySet()) {
            IdentityState st = e.getValue();
            String rotulo = "";
            if (st.status() == IdentityState.Status.BLOCKED) {
                Bloqueio b = rastro.classificarFinal(e.getKey(), st.contadorFalhas(), limiteTentativas);
                classe.put(e.getKey(), b);
                porMotivo.merge(b, 1, Integer::sum);
                rotulo = b.rotulo();
            }
            idsVarridas.add(new IdentidadeVarrida(e.getKey(), st.status().name(),
                    st.contadorFalhas(), rotulo));
        }

        // I2 global: nao exige que o cliente conheca a sid.
        for (Map.Entry<String, SessionState> e : new TreeMap<>(sessoes).entrySet()) {
            SessionState s = e.getValue();
            if (s.estado() != SessionState.Estado.VALID) {
                continue;
            }
            Bloqueio b = classe.get(s.identidade());
            if (b != null) {
                achados.add(new Achado("I2", e.getKey(), RastroCliente.classeDeI2(b),
                        "sessao VALID de identidade " + s.identidade() + " BLOCKED (" + b.rotulo() + ")"));
            }
        }

        // I2 bloqueio perdido.
        int blocksAdmin = 0;
        for (String id : rastro.identidadesComBlock()) {
            if (rastro.eventos(id).stream().anyMatch(ev -> ev.ehBlock() && ev.confirmado())) {
                blocksAdmin++;
            }
            Optional<EventoAdmin> ult = rastro.ultimoConfirmado(id);
            if (ult.isEmpty() || !ult.get().ehBlock()) {
                continue;
            }
            IdentityState st = identidades.get(id);
            boolean bloqueada = st != null && st.status() == IdentityState.Status.BLOCKED;
            if (!bloqueada) {
                boolean incerto = rastro.unblockIncertoDepois(id, ult.get());
                achados.add(new Achado("I2-PERDIDO", id, incerto ? "INCERTA" : "REAL",
                        "Block confirmado e identidade " + (st == null ? "ausente" : "OPEN")
                                + (incerto ? "; Unblock posterior com erro (efeito desconhecido)"
                                           : " sem Unblock confirmado posterior")));
            }
        }

        achados.sort(Comparator.comparing(Achado::invariante).thenComparing(Achado::chave));
        return new Resultado(achados, idsVarridas, porMotivo, blocksAdmin,
                sessoes.size(), identidades.size());
    }

    /** {@code varredura-rep-NNN.csv}: {@code invariante,chave,classificacao,detalhe}. */
    public static void escreverAchados(Path destino, Resultado r) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write("invariante,chave,classificacao,detalhe");
            w.newLine();
            for (Achado a : r.achados()) {
                w.write(a.invariante() + "," + limpar(a.chave()) + "," + a.classificacao()
                        + "," + limpar(a.detalhe()));
                w.newLine();
            }
        }
    }

    /** {@code varredura-identidades-rep-NNN.csv}: {@code identidade,status,contador,classificacao_bloqueio}. */
    public static void escreverIdentidades(Path destino, Resultado r) throws IOException {
        Path pai = destino.getParent();
        if (pai != null) {
            Files.createDirectories(pai);
        }
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write("identidade,status,contador,classificacao_bloqueio");
            w.newLine();
            for (IdentidadeVarrida i : r.identidades()) {
                w.write(limpar(i.identidade()) + "," + i.status() + "," + i.contador() + ","
                        + i.classificacao());
                w.newLine();
            }
        }
    }

    private static String limpar(String s) {
        return s == null ? "" : s.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }
}
