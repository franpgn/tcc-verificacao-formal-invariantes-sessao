package br.unipampa.tcc.session;

/**
 * B-43.1: o reset de estado antes do <i>warm-up</i> nao conseguiu provar que os caches do perfil
 * estao vazios (prazo vencido com {@code size() > 0}, ou excecao em {@code clear()}/{@code size()},
 * por exemplo {@code AvailabilityException} em particao degradada). A repeticao e abortada sem
 * gravar {@code rep-NNN.csv}; {@link WorkloadMain#main} sai com o codigo 5.
 */
public class EstadoNaoLimpoException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient ResetEstado.Resultado resultado;

    public EstadoNaoLimpoException(String motivo, ResetEstado.Resultado parcial, Throwable causa) {
        super(motivo, causa);
        this.resultado = parcial;
    }

    /** Resultado parcial (tamanhos conhecidos; {@code null} onde a leitura nao aconteceu). */
    public ResetEstado.Resultado resultado() {
        return resultado;
    }
}
