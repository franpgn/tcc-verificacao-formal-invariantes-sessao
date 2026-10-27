package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.PortaoDeEmissao;

import java.util.function.Supplier;

/**
 * B-33 (E2): a "aplicacao web" do cenario S3. Executa uma operacao do protocolo com a politica
 * de retentativa de uma aplicacao real <b>limitada</b>: no maximo {@code retentativas} novas
 * tentativas, com espera de {@code backoffMs} que dobra a cada tentativa (500 ms, 1 s); depois
 * disso a operacao conta como <i>falha percebida pelo usuario</i>. Nao reconcilia estado, nao
 * repara sessao e nao retenta indefinidamente: o experimento registra o que o sistema entrega
 * nessas condicoes. Cada tentativa individual ja foi para o CSV de eventos (via
 * {@link SessionOps}); aqui se registra a acao colapsada ({@link AcaoS3}).
 *
 * <p>So erro ({@code ERROR_TRANSPORT}/{@code ERROR_UNAVAILABLE}) e retentado; {@code BLOCKED} e
 * {@code NOT_FOUND} sao respostas validas do servidor.</p>
 */
public final class AplicacaoS3 {

    private final RelogioS3 relogio;
    private final S3Parametros p;
    private final S3Registro registro;
    private final PortaoDeEmissao portao;

    public AplicacaoS3(RelogioS3 relogio, S3Parametros p, S3Registro registro) {
        this(relogio, p, registro, PortaoDeEmissao.livre());
    }

    /** B-27: com o portao de emissao (K1, K3 e K4 agem aqui). */
    public AplicacaoS3(RelogioS3 relogio, S3Parametros p, S3Registro registro, PortaoDeEmissao portao) {
        if (portao == null) {
            throw new IllegalArgumentException("portao nulo (use PortaoDeEmissao.livre())");
        }
        this.relogio = relogio;
        this.p = p;
        this.registro = registro;
        this.portao = portao;
    }

    static boolean ehErro(OpResult r) {
        return r.code() == OpResult.Code.ERROR_TRANSPORT || r.code() == OpResult.Code.ERROR_UNAVAILABLE;
    }

    /** Descricao do resultado para o registro (distingue Validate que viu INVALID). */
    static String descrever(OpResult r) {
        if (r.code() == OpResult.Code.OK_VALIDATE && r.estado() != null) {
            return "OK_VALIDATE:" + r.estado();
        }
        return r.code().name();
    }

    /**
     * Executa {@code chamada} com ate {@code retentativas} retentativas de aplicacao.
     *
     * <p>B-27: antes da primeira tentativa o portao pode adiar a operacao (K3, so administrativas
     * em CONSERVADOR); cada tentativa passa por {@code entrar()}/{@code sair()} (K1); a espera entre
     * tentativas e {@code portao.esperaRetentativaNs} sobre a base que continua dobrando (K4). Com
     * {@link PortaoDeEmissao#livre()} ou em NORMAL o resultado e numericamente o de antes. A acao
     * registra {@code tFimNs} (depois da ultima tentativa) e {@code esperaPortaoNs} (K1 + K3). Se a
     * espera do portao for interrompida, a flag e restaurada e a operacao segue mesmo assim (o
     * portao nunca descarta).</p>
     *
     * @return o ultimo resultado (de erro se todas as tentativas falharam)
     */
    public OpResult executar(String ator, String operacao, String alvo, int retentativas,
                             Supplier<OpResult> chamada) {
        long inicio = relogio.agoraNs();
        long[] esperaPortao = {0L};
        try {
            esperaPortao[0] += portao.aguardarAdministrativa(ator, operacao, alvo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        OpResult r = tentar(chamada, esperaPortao);
        int tentativas = 1;
        boolean houveErro = ehErro(r);
        long espera = S3Parametros.msParaNs(p.backoffMs);
        while (ehErro(r) && tentativas <= retentativas) {
            try {
                relogio.dormirNs(portao.esperaRetentativaNs(ator, espera, tentativas, inicio));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            espera *= 2;
            r = tentar(chamada, esperaPortao);
            tentativas++;
            houveErro |= ehErro(r);
        }
        long fim = relogio.agoraNs();
        boolean falha = ehErro(r);
        String nota = houveErro && (SessionOps.OP_BLOCK.equals(operacao) || SessionOps.OP_UNBLOCK.equals(operacao))
                ? "tentativa-incerta" : "";
        registro.accept(new AcaoS3(inicio, ator, operacao, alvo, descrever(r), tentativas, falha, nota,
                fim, esperaPortao[0]));
        return r;
    }

    /** Uma tentativa com a permissao do K1 (devolvida em {@code finally}); soma a espera do K1. */
    private OpResult tentar(Supplier<OpResult> chamada, long[] esperaPortao) {
        boolean entrou = false;
        try {
            esperaPortao[0] += portao.entrar();
            entrou = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            return chamada.get();
        } finally {
            if (entrou) {
                portao.sair();
            }
        }
    }

    /** Total de falhas percebidas pelo usuario (todas as operacoes). */
    public long falhasPercebidas() {
        return registro.contadores().entrySet().stream()
                .filter(e -> e.getKey().startsWith("falha-percebida|"))
                .mapToLong(e -> e.getValue()).sum();
    }

    public long falhasPercebidas(String operacao) {
        return registro.valor("falha-percebida|" + operacao);
    }
}
