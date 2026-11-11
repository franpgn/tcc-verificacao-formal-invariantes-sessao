package br.unipampa.tcc.session;

/**
 * B-33 (E2): ator do cenario S3 (usuario virtual, conta de L ou administrador), modelado como
 * maquina de estados: {@link #proximoNs()} diz quando o ator quer agir e {@link #passo()}
 * executa uma acao (que pode bloquear em retentativas) e agenda a seguinte.
 */
public interface AtorS3 {

    String id();

    /** Instante (base do {@link RelogioS3}) da proxima acao. */
    long proximoNs();

    /** Executa a acao devida e atualiza {@link #proximoNs()}. */
    void passo();
}
