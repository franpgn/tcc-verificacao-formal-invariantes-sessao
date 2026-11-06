package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * W-3 (B-28.2): formato das linhas dos dois JSONL do controlador. Contrato em
 * {@code docs/contrato-jsonl-controlador-v1.md}. Todo campo e {@code snake_case}; instantes em ns no
 * relogio dos {@code end_ns} do CSV de eventos; ausencia de valor e {@code null}, nunca campo omitido.
 *
 * <p>O p99 por janela nao vai ao JSONL ao vivo (decisao de 2026-10-22): so contagens.</p>
 */
public final class FormatoJsonl {

    /**
     * v1.1 (V-W3): {@code linhas_perdidas} na linha {@code fim}. v1.2 (W-4): {@code parametros_defasagem} na
     * {@code meta}, linha {@code defasagem} por rodada do S_lag e contadores {@code defasagem_*} no {@code fim}.
     */
    public static final String ESQUEMA_OBSERVAVEIS = "observaveis-controlador/1.2";
    /**
     * v1.1 (V-W3): {@code t_decisao_ns} na linha {@code decisao}; linha {@code fim} com prazos e perdas. v1.2 (W-2a):
     * {@code reversao} em toda linha {@code acao}, linha {@code politica}, tipos AC-2 e AC-5.
     */
    public static final String ESQUEMA_DECISOES = "decisoes-controlador/1.2";

    private static final ObjectMapper JSON = new ObjectMapper();

    private FormatoJsonl() { /* utilitario */ }

    /** Linha vazia com o campo {@code tipo}. */
    public static ObjectNode linha(String tipo) {
        ObjectNode o = JSON.createObjectNode();
        o.put("tipo", tipo);
        return o;
    }

    /** Primeira linha dos dois arquivos. */
    public static ObjectNode meta(String esquema, MetaExecucao m) {
        ObjectNode o = linha("meta");
        o.put("esquema", esquema);
        o.put("versao_programa", m.versaoPrograma());
        o.put("rep", m.rep());
        o.put("modo", m.modo().rotulo());
        o.put("cenario", m.cenario());
        o.put("replicacao", m.replicacao());
        o.put("nos_esperados", m.nosEsperados());
        ParametrosControlador p = m.parametros();
        o.put("param_versao", p.versao());
        o.put("param_sha256", p.sha256());
        ObjectNode par = o.putObject("parametros");
        par.put("w_ns", p.wNs());
        par.put("n_lat", p.nLat());
        par.put("n_err", p.nErr());
        par.put("theta_lat_in", p.thetaLatIn());
        par.put("theta_lat_out", p.thetaLatOut());
        par.put("theta_err_in", p.thetaErrIn());
        par.put("theta_err_out", p.thetaErrOut());
        par.put("k", p.k());
        par.put("m", p.m());
        par.put("cooldown_ns", p.cooldownNs());
        par.put("estavel_ns", p.estavelNs());
        par.put("hold_viol_ns", p.holdViolNs());
        o.put("referencia_arquivo", m.referenciaArquivo());
        o.put("referencia_sha256", m.referenciaSha256());
        o.put("tolerancia_ns", m.toleranciaNs());
        o.put("tick_ns", m.tickNs());
        o.put("origem_ns", m.origemNs());
        o.put("atuador", m.atuador());
        o.put("politica", m.politica());
        o.put("sonda_topologia", m.sondaTopologia());
        o.put("sonda_defasagem", m.sondaDefasagem());
        if (ESQUEMA_DECISOES.equals(esquema)) {
            // decisoes 1.2 (W-2a, P-38): parametros da politica de acao, null sem o bloco "politica"
            ParametrosPolitica pp = p.politica();
            if (pp == null) {
                o.putNull("parametros_politica");
            } else {
                ObjectNode q = o.putObject("parametros_politica");
                q.put("k_lag", pp.kLag());
                q.put("teto_lag_ms", pp.tetoLagMs());
                q.put("t_reb_ns", pp.tRebNs());
                q.put("cota_ac5", pp.cotaAc5());
                q.put("p2_habilitada", pp.p2Habilitada()); // D-B (aditivo na 1.2, antes de dado de campanha)
                q.put("acao_p1", pp.acaoP1().rotulo()); // P1-IC (aditivo na 1.2): AC-7 (G-1) ou AC-5 (comparacao)
                ObjectNode org = q.putObject("origem");
                pp.origem().forEach(org::put);
            }
        }
        if (ESQUEMA_OBSERVAVEIS.equals(esquema)) {
            // observaveis 1.2 (W-4): parametros do S_lag, null sem o bloco "defasagem". O prazo de leitura (o "nao
            // viu") e o --hotrod-timeout-ms e vai em teto_ms de cada linha defasagem
            ParametrosDefasagem pd = p.defasagem();
            if (pd == null) {
                o.putNull("parametros_defasagem");
            } else {
                ObjectNode q = o.putObject("parametros_defasagem");
                q.put("lag_in_ms", pd.lagInMs());
                q.put("lag_out_ms", pd.lagOutMs());
                q.put("passo_leitura_ms", pd.passoLeituraMs());
                q.put("periodo_ns", p.wNs());
                ObjectNode org = q.putObject("origem");
                pd.origem().forEach(org::put);
            }
        }
        return o;
    }

    /**
     * W-4 (B-26.3): uma rodada da sonda de defasagem (observaveis 1.2), com os pares, as escritas e o custo.
     * {@code lag_por_no} e o menor lag dos pares de cada no; {@code lag_max_ms} e {@code no_suspeito} saem dele.
     */
    public static ObjectNode defasagem(RodadaDefasagem r) {
        ObjectNode o = linha("defasagem");
        o.put("rodada", r.rodada());
        o.put("t_inicio_ns", r.tInicioNs());
        o.put("t_ns", r.tNs());
        o.put("teto_ms", r.tetoMs());
        o.put("lag_max_ms", r.lagMaxMs());
        o.put("no_suspeito", r.noSuspeito());
        o.put("lag_max_par_ms", r.lagMaxParMs());
        ArrayNode porNo = o.putArray("lag_por_no");
        r.lagPorNo().forEach((no, lag) -> porNo.addObject().put("no", no).put("lag_ms", lag));
        ArrayNode pares = o.putArray("pares");
        for (RodadaDefasagem.Par p : r.pares()) {
            pares.addObject()
                    .put("escritor", p.escritor())
                    .put("leitor", p.leitor())
                    .put("visto", p.visto())
                    .put("lag_ms", p.lagMs())
                    .put("leituras", p.leituras())
                    .put("erros_leitura", p.errosLeitura());
        }
        ArrayNode es = o.putArray("escritas");
        for (RodadaDefasagem.Escrita e : r.escritas()) {
            es.addObject()
                    .put("no", e.no())
                    .put("ok", e.ok())
                    .put("duracao_ms", e.duracaoMs())
                    .put("erro", e.erro());
        }
        ObjectNode c = o.putObject("custo");
        c.put("escritas", r.escritas().size());
        c.put("leituras", r.leituras());
        c.put("erros_escrita", r.errosEscrita());
        c.put("erros_leitura", r.errosLeitura());
        return o;
    }

    /** Uma janela fechada, com S_top e S_lag (observaveis). */
    public static ObjectNode janela(JanelaObservada obs, EstadoTopologia top, EstadoDefasagem lag) {
        Janela j = obs.janela();
        ObjectNode o = linha("janela");
        o.put("t_ini_ns", j.inicioNs());
        o.put("t_fim_ns", j.fimNs());
        o.put("total", j.total());
        o.put("erros", j.erros());
        o.put("unavailable", j.indisponiveis());
        o.put("n_lat", j.elegiveisLat());
        o.put("exced", j.excedencias());
        o.put("atrasados", obs.atrasados());
        ObjectNode v = o.putObject("viol");
        v.put("seguranca", j.violacoesSeguranca());
        v.put("seguranca_promovidas", obs.violacoesSegurancaPromovidas());
        v.put("i2_esperada", j.violacoesI2Esperadas());
        v.put("i2_fora_do_gatilho", obs.violacoesI2ForaDoGatilho());
        v.put("fora_do_gatilho", obs.violacoesForaDoGatilho());
        v.put("atrasadas_fora_do_gatilho", obs.violacoesAtrasadas());
        ObjectNode t = o.putObject("top");
        t.put("disponivel", top.disponivel());
        t.put("amostras", top.amostras());
        t.put("anomalas", top.anomalas());
        t.put("nos_min", top.nosMinimo());
        ObjectNode l = o.putObject("lag");
        l.put("nivel", lag.nivel().name());
        l.put("no_suspeito", lag.noSuspeito());
        l.put("lag_max_ms", lag.lagMaxMs());
        l.put("amostras", lag.amostras());
        return o;
    }

    /** Tick que fechou ao menos uma janela (reinjecao no replay, B-38). */
    public static ObjectNode tick(long seqRegistro, long agoraNs, int janelasFechadas) {
        ObjectNode o = linha("tick");
        o.put("seq", seqRegistro);
        o.put("agora_ns", agoraNs);
        o.put("janelas", janelasFechadas);
        return o;
    }

    /** Uma rodada da sonda de topologia (mesmos campos de {@code SondaTopologiaCli}). */
    public static ObjectNode topologia(AmostraTopologia a) {
        ObjectNode o = linha("topologia");
        o.put("t_ns", a.tNs());
        o.put("anomala", a.anomala());
        o.put("nos_min", a.nosMinimo());
        ArrayNode porNo = o.putArray("por_no");
        for (ResultadoNo r : a.porNo()) {
            porNo.addObject()
                    .put("no", r.no())
                    .put("respondeu", r.respondeu())
                    .put("numero_de_nos", r.numeroDeNos())
                    .put("motivo", r.motivo())
                    .put("anomalo", r.anomalo());
        }
        return o;
    }

    /**
     * Uma decisao do nucleo (uma por janela).
     *
     * @param tDecisaoNs instante em que o laco processou a janela (v1.1, V-W3 4.2): {@code t_decisao_ns -
     *                   t_fim_ns} mede o atraso do fechamento, esperado em {@code [L, L + tick]}
     */
    public static ObjectNode decisao(Decisao d, ModoAtuacao modo, long tDecisaoNs) {
        ObjectNode o = linha("decisao");
        o.put("t_fim_ns", d.fimNs());
        o.put("t_decisao_ns", tDecisaoNs);
        o.put("modo", modo.rotulo());
        o.put("perfil", d.perfil().name());
        o.put("transicao", d.transicao());
        o.put("regra", d.regra().name());
        ArrayNode mot = o.putArray("motivos");
        d.motivos().forEach(m -> mot.add(m.name()));
        o.put("severidade", d.severidade().name());
        Decisao.Sinais s = d.sinais();
        ObjectNode n = o.putObject("sinais");
        n.put("taxa_latencia", s.taxaLatencia());
        n.put("taxa_erro", s.taxaErro());
        n.put("fracao_indisponivel", s.fracaoIndisponivel());
        n.put("latencia_alta", s.latenciaAlta());
        n.put("erro_alto", s.erroAlto());
        n.put("topologia_anomala", s.topologiaAnomala());
        n.put("topologia_disponivel", s.topologiaDisponivel());
        n.put("topologia_amostras", s.topologiaAmostras());
        n.put("topologia_anomalas", s.topologiaAnomalas());
        n.put("nos_minimo", s.nosMinimo());
        n.put("violacoes_seguranca", s.violacoesSeguranca());
        n.put("violacoes_i2_esperadas", s.violacoesI2Esperadas());
        n.put("persistencia_latencia", s.persistenciaLatencia());
        n.put("persistencia_erro", s.persistenciaErro());
        n.put("janelas_calmas", s.janelasCalmas());
        n.put("cooldown_expirado", s.cooldownExpirado());
        n.put("defasagem", s.defasagem().nivel().name());
        n.put("defasagem_alta", s.defasagemAlta());
        n.put("persistencia_defasagem", s.persistenciaDefasagem());
        n.put("no_suspeito", s.defasagem().noSuspeito());
        n.put("lag_max_ms", s.defasagem().lagMaxMs());
        n.put("defasagem_amostras", s.defasagem().amostras());
        o.put("param_sha256", d.parametros().sha256());
        return o;
    }

    /** Um pedido de aplicacao ou reversao (executado ou nao). */
    public static ObjectNode acao(RegistroAcao r) {
        ObjectNode o = linha("acao");
        o.put("operacao", r.operacao().name());
        o.put("executada", r.executada());
        o.put("modo", r.modo().rotulo());
        o.put("atuador", r.atuador());
        ObjectNode a = o.putObject("acao");
        a.put("tipo", r.acao().tipo().rotulo());
        ArrayNode c = a.putArray("caches");
        r.acao().caches().forEach(c::add);
        a.put("alvo", r.acao().alvo());
        a.put("valor", r.acao().valor());
        o.put("origem", r.origem().name());
        o.put("regra", r.regra() == null ? null : r.regra().name());
        o.put("t_fim_janela_ns", r.fimJanelaNs());
        o.put("t_pedido_ns", r.pedidoNs());
        o.put("t_inicio_ns", r.inicioNs());
        o.put("duracao_ns", r.duracaoNs());
        o.put("resultado", r.resultado());
        o.put("codigo_http", r.codigoHttp());
        o.put("lido", r.lidoDeVolta());
        o.put("erro", r.erro());
        // decisoes 1.2 (W-2a, P-37): orquestrador na acao fora da pilha (AC-5); null na reversivel (pilha)
        o.put("reversao", r.acao().tipo().reversivel() ? null : REVERSAO_ORQUESTRADOR);
        return o;
    }

    /** Valor de {@code reversao} na linha {@code acao} de uma acao nao reversivel. */
    public static final String REVERSAO_ORQUESTRADOR = "orquestrador";

    /**
     * W-2a (B-44.2): politica de reacao escolhida numa janela ({@code P0}, {@code P1}, {@code P2}) e o
     * motivo, quando a politica de acao registra a escolha ({@link PlanoDeAcao#politica()} nao nulo).
     */
    public static ObjectNode politica(Decisao d, PlanoDeAcao p, ModoAtuacao modo, long tNs) {
        ObjectNode o = linha("politica");
        o.put("t_fim_janela_ns", d.fimNs());
        o.put("t_decisao_ns", tNs);
        o.put("modo", modo.rotulo());
        o.put("perfil", d.perfil().name());
        o.put("regra", d.regra().name());
        o.put("politica", p.politica());
        o.put("motivo", p.motivo());
        o.put("no", p.no()); // V-7 (aditivo na 1.2): no da escolha, ou null
        ArrayNode rev = o.putArray("reverter");
        p.reverter().forEach(x -> rev.add(x.tipo().rotulo()));
        ArrayNode apl = o.putArray("aplicar");
        p.aplicar().forEach(x -> apl.add(x.tipo().rotulo()));
        // P1-IC (aditivo na 1.2): acoes de P1 ja pedidas depois desta janela (a cota fica em parametros_politica)
        o.put("usadas_p1", p.usadasP1());
        return o;
    }
}
