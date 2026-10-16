package br.unipampa.tcc.session;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B-33 (E2): recursos compartilhados pelos atores do S3 (relogio, parametros, aplicacao,
 * registro e o conjunto de identidades de U "online" que o console administrativo enxerga).
 * Todo o aleatorio vem de geradores por ator derivados da semente, de modo que, com relogio e
 * armazenamento deterministicos, a sequencia de acoes e reprodutivel.
 */
public final class ContextoS3 {

    public final RelogioS3 relogio;
    public final S3Parametros p;
    public final AplicacaoS3 app;
    public final S3Registro registro;
    public final long semente;
    /** Identidades de U com sessao ativa ou abandonada ainda dentro do prazo (visao do admin). */
    public final Set<String> online = ConcurrentHashMap.newKeySet();

    public ContextoS3(RelogioS3 relogio, S3Parametros p, S3Registro registro, long semente) {
        this.relogio = relogio;
        this.p = p;
        this.registro = registro;
        this.semente = semente;
        this.app = new AplicacaoS3(relogio, p, registro);
    }

    /** Gerador independente por ator (tipo + indice), derivado da semente. */
    Random rng(String tipo, int indice) {
        long x = semente ^ (0x9E3779B97F4A7C15L * (indice + 1L)) ^ ((long) tipo.hashCode() * 0xC2B2AE3D27D4EB4FL);
        return new Random(x);
    }

    /** Exponencial de media {@code mediaNs}, em ns (minimo 1 ms para nao girar em vazio). */
    static long exponencialNs(Random r, long mediaNs) {
        double u = r.nextDouble();
        long v = (long) (-mediaNs * Math.log(1.0 - u));
        return Math.max(v, 1_000_000L);
    }

    /** Log-normal com a mediana dada e sigma do logaritmo, em ns (minimo 1 s). */
    static long logNormalNs(Random r, long medianaNs, double sigma) {
        double v = medianaNs * Math.exp(sigma * r.nextGaussian());
        return Math.max((long) v, 1_000_000_000L);
    }

    static long uniformeNs(Random r, long minNs, long maxNs) {
        return maxNs <= minNs ? minNs : minNs + (long) (r.nextDouble() * (maxNs - minNs));
    }

    static String identidadeU(int i) {
        return String.format("s3-u-%03d", i);
    }

    static String identidadeL(int i) {
        return String.format("s3-l-%03d", i);
    }
}
