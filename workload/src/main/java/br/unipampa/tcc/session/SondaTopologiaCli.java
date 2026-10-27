package br.unipampa.tcc.session;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * B-26.2: CLI de smoke da sonda de topologia (esqueleto; implementacao no commit feat).
 */
public final class SondaTopologiaCli {

    /** Codigo de saida de erro de uso (opcao ausente ou invalida, senha ausente). */
    static final int EXIT_USO = 2;

    /** Argumentos validados. {@code toString} nao mostra a senha. */
    record Args(List<String> urls, PerfilReplicacao perfil, String usuario, String senha, long periodoMs,
                long prazoMs, int duracaoS) {
        @Override
        public String toString() {
            return "Args[urls=" + urls + ", perfil=" + perfil + ", usuario=" + usuario + ", senha=***, periodoMs="
                    + periodoMs + ", prazoMs=" + prazoMs + ", duracaoS=" + duracaoS + "]";
        }
    }

    private SondaTopologiaCli() { /* utilitario */ }

    static Args parse(String[] argv, Map<String, String> env) {
        return null;
    }

    static int executar(String[] argv, Map<String, String> env, PrintStream out, PrintStream err,
                        ConsultaSaudeRest.LancadorCurl lancador) {
        return 0;
    }

    public static void main(String[] argv) {
        System.exit(executar(argv, System.getenv(), System.out, System.err, ConsultaSaudeRest.PADRAO));
    }
}
