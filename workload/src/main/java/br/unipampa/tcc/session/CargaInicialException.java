package br.unipampa.tcc.session;

/**
 * B-43.2 (P-c): a carga inicial da mistura v2 nao provou {@code size() == universo} (prazo vencido,
 * tamanho diferente ou excecao em {@code putAll}/{@code size}). E um caso de "estado inicial nao
 * preparado": estende {@link EstadoNaoLimpoException}, entao {@link WorkloadMain#codigoDeSaida} da 5
 * sem codigo novo, a repeticao nao grava {@code rep-NNN.csv} e as seguintes nao rodam.
 */
public class CargaInicialException extends EstadoNaoLimpoException {

    private static final long serialVersionUID = 1L;

    private final transient CargaInicial.Resultado carga;

    public CargaInicialException(String motivo, CargaInicial.Resultado parcial, Throwable causa) {
        super(motivo, null, causa);
        this.carga = parcial;
    }

    /** Resultado parcial da carga ({@code tamanhoFinal} nulo se nenhuma consulta terminou). */
    public CargaInicial.Resultado carga() {
        return carga;
    }
}
