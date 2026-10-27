package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.FonteDeSaude;
import br.unipampa.tcc.controle.LeituraSaude;

import java.io.IOException;
import java.util.List;

/**
 * B-26.2: adaptador REST da sonda de topologia (esqueleto; implementacao no commit feat).
 */
public final class ConsultaSaudeRest implements FonteDeSaude {

    /** Lanca o processo do curl; o padrao e {@link ProcessBuilder#start()}. */
    interface LancadorCurl {
        Process lancar(List<String> comando) throws IOException;
    }

    static final LancadorCurl PADRAO = comando -> new ProcessBuilder(comando).start();

    private final List<String> urls;
    private final String usuario;
    private final String senha;
    private final LancadorCurl lancador;

    public ConsultaSaudeRest(List<String> urls, String usuario, String senha) {
        this(urls, usuario, senha, PADRAO);
    }

    ConsultaSaudeRest(List<String> urls, String usuario, String senha, LancadorCurl lancador) {
        this.urls = urls;
        this.usuario = usuario;
        this.senha = senha;
        this.lancador = lancador;
    }

    public static List<String> urlsDe(String servidores) {
        return List.of();
    }

    @Override
    public int nos() {
        return 0;
    }

    @Override
    public String rotulo(int indice) {
        return "";
    }

    @Override
    public LeituraSaude consultar(int indice, long prazoNs) throws Exception {
        throw new UnsupportedOperationException("B-26.2: esqueleto");
    }

    public long lancamentos() {
        return 0;
    }
}
