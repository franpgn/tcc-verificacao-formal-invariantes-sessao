package br.unipampa.tcc.session;

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

    public AplicacaoS3(RelogioS3 relogio, S3Parametros p, S3Registro registro) {
        this.relogio = relogio;
        this.p = p;
        this.registro = registro;
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
     * <p>A espera entre tentativas comeca em {@code backoffMs} e dobra a cada tentativa. A acao
     * registra {@code tFimNs} (depois da ultima tentativa) e {@code esperaPortaoNs = 0}: a coluna
     * {@code espera_portao_ns} e reservada desde o B-27.3, que retirou o portao de emissao do
     * cliente (o controlador do TCC age no <i>cluster</i>). Se a espera do <i>backoff</i> for
     * interrompida, a flag e restaurada e a acao termina com o ultimo resultado.</p>
     *
     * @return o ultimo resultado (de erro se todas as tentativas falharam)
     */
    public OpResult executar(String ator, String operacao, String alvo, int retentativas,
                             Supplier<OpResult> chamada) {
        long inicio = relogio.agoraNs();
        OpResult r = chamada.get();
        int tentativas = 1;
        boolean houveErro = ehErro(r);
        long espera = S3Parametros.msParaNs(p.backoffMs);
        while (ehErro(r) && tentativas <= retentativas) {
            try {
                relogio.dormirNs(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            espera *= 2;
            r = chamada.get();
            tentativas++;
            houveErro |= ehErro(r);
        }
        long fim = relogio.agoraNs();
        boolean falha = ehErro(r);
        String nota = houveErro && (SessionOps.OP_BLOCK.equals(operacao) || SessionOps.OP_UNBLOCK.equals(operacao))
                ? "tentativa-incerta" : "";
        registro.accept(new AcaoS3(inicio, ator, operacao, alvo, descrever(r), tentativas, falha, nota,
                fim, 0L));
        return r;
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
