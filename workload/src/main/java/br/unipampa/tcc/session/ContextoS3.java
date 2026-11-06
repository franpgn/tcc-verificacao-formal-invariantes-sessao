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
        return new Random(sementeAtor(tipo, indice));
    }

    /**
     * W-10: a semente do gerador de um ator ({@link #rng}), a mesma formula de antes:
     * {@code semente ^ (0x9E3779B97F4A7C15 * (indice + 1)) ^ (hash(tipo) * 0xC2B2AE3D27D4EB4F)}.
     */
    long sementeAtor(String tipo, int indice) {
        return semente ^ (0x9E3779B97F4A7C15L * (indice + 1L)) ^ ((long) tipo.hashCode() * 0xC2B2AE3D27D4EB4FL);
    }

    /** W-10: sal da semente do atraso do replay imediato: "SONDAREP" em ASCII. */
    static final long SAL_SONDA = 0x534F4E4441524550L;

    /** W-10: sal da semente da amostra do replay tardio: "SONDATAR" em ASCII. */
    static final long SAL_SONDA_TARDIA = 0x534F4E4441544152L;

    /**
     * W-10: semente do gerador proprio do atraso do replay do usuario {@code indice} do pool U:
     * {@code mix64(sementeAtor("U", indice) ^ SAL_SONDA)}, pelo mesmo esquema de {@code MisturaV3.sementeToken}
     * e {@code WorkloadMain.sementeMistura} (sal por ou exclusivo e o finalizador do SplitMix64). Dominio
     * separado do gerador do usuario ({@link #rng}), que nao e consumido pela sonda.
     */
    long sementeSonda(int indice) {
        return MisturaV3.mix64(sementeAtor("U", indice) ^ SAL_SONDA);
    }

    /** W-10: gerador do atraso do replay do usuario {@code indice} (so existe com a sonda ligada). */
    Random rngSonda(int indice) {
        return new Random(sementeSonda(indice));
    }

    /** W-10: semente da amostra do replay tardio: {@code mix64(semente ^ SAL_SONDA_TARDIA)}. */
    long sementeSondaTardia() {
        return MisturaV3.mix64(semente ^ SAL_SONDA_TARDIA);
    }

    /** W-10: gerador da amostra do replay tardio (so e consumido se houver mais candidatas que o limite). */
    Random rngSondaTardia() {
        return new Random(sementeSondaTardia());
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
