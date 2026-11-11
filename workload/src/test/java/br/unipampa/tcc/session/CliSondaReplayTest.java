package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W-10: opcoes da sonda de replay do S3 (padrao desligado) e as regras de validacao. */
class CliSondaReplayTest {

    private static Cli parse(String... args) {
        Cli c = new Cli();
        new CommandLine(c).parseArgs(args);
        return c;
    }

    @Test
    @DisplayName("padrao: sonda desligada, atraso U(1, 3) s e ate 200 sids no replay tardio")
    void padroes() {
        S3Parametros p = parse("--scenario", "S3").s3;
        assertFalse(p.sondaReplay);
        assertEquals(1.0, p.sondaMinSeg);
        assertEquals(3.0, p.sondaMaxSeg);
        assertEquals(200, p.sondaTardiaMax);
        p.validar();
    }

    @Test
    @DisplayName("--s3-replay-probe liga a sonda; os parametros sao configuraveis")
    void ligaEConfigura() {
        S3Parametros p = parse("--scenario", "S3", "--s3-replay-probe", "--s3-replay-min-sec", "0.5",
                "--s3-replay-max-sec", "2", "--s3-replay-late-max", "50").s3;
        assertTrue(p.sondaReplay);
        assertEquals(0.5, p.sondaMinSeg);
        assertEquals(2.0, p.sondaMaxSeg);
        assertEquals(50, p.sondaTardiaMax);
        p.validar();
    }

    @Test
    @DisplayName("validacao: atraso minimo > 0, maximo >= minimo, limite do tardio >= 0, so no modo pinned")
    void validacao() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("--scenario", "S3", "--s3-replay-probe", "--s3-replay-min-sec", "0").s3.validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse("--scenario", "S3", "--s3-replay-probe", "--s3-replay-min-sec", "3",
                        "--s3-replay-max-sec", "2").s3.validar());
        assertThrows(IllegalArgumentException.class,
                () -> parse("--scenario", "S3", "--s3-replay-probe", "--s3-replay-late-max", "-1").s3.validar());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("--scenario", "S3", "--s3-replay-probe", "--client-mode", "topology").s3.validar());
        assertTrue(e.getMessage().contains("pinned"), e.getMessage());
        // desligada, os parametros da sonda nao sao exigidos (nada muda para quem nao a usa)
        parse("--scenario", "S3", "--client-mode", "topology").s3.validar();
    }

    @Test
    @DisplayName("semente da sonda: sal proprio pelo esquema SplitMix, fora das sementes existentes dos atores")
    void sementeDaSonda() {
        ContextoS3 ctx = new ContextoS3(new RelogioFalso(0), new S3Parametros(), new S3Registro(), 123L);
        assertEquals(0x534F4E4441524550L, ContextoS3.SAL_SONDA, "\"SONDAREP\" em ASCII");
        assertEquals(MisturaV3.mix64(ctx.sementeAtor("U", 4) ^ ContextoS3.SAL_SONDA), ctx.sementeSonda(4));
        assertEquals(new java.util.Random(ctx.sementeSonda(4)).nextLong(), ctx.rngSonda(4).nextLong());
        // o gerador do usuario nao muda: rng("U", i) continua semeado com a mesma semente de antes
        long antes = 123L ^ (0x9E3779B97F4A7C15L * 5L) ^ ((long) "U".hashCode() * 0xC2B2AE3D27D4EB4FL);
        assertEquals(antes, ctx.sementeAtor("U", 4));
        assertEquals(new java.util.Random(antes).nextLong(), ctx.rng("U", 4).nextLong());
        assertEquals(MisturaV3.mix64(123L ^ ContextoS3.SAL_SONDA_TARDIA), ctx.sementeSondaTardia());
    }
}
