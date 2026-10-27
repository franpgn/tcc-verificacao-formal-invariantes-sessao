package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B-27, F1/F2: {@link AcaoS3} com {@code tFimNs} e {@code esperaPortaoNs} no fim; validacao no
 * construtor canonico e CSV com as duas colunas novas (contrato {@code docs/contrato-s3-acoes-v2.md}).
 */
class AcaoS3FimTest {

    @TempDir
    Path dir;

    private static AcaoS3 acao(long t, long fim, long espera) {
        return new AcaoS3(t, "admin", "Block", "s3-u-001", "OK_STATUS", 2, false, "tentativa-incerta", fim, espera);
    }

    @Test
    @DisplayName("F1: tFimNs < tNs ou esperaPortaoNs < 0 => IllegalArgumentException; tFim = tNs e espera 0 valem")
    void validacao() {
        acao(10, 10, 0);
        acao(10, 25, 15);
        assertThrows(IllegalArgumentException.class, () -> acao(10, 9, 0));
        assertThrows(IllegalArgumentException.class, () -> acao(10, 20, -1));
    }

    @Test
    @DisplayName("F2: escreverCsv grava o HEADER novo e as duas colunas convertidas pelo mesmo EpochClock; as 8 primeiras como antes")
    void csv() throws Exception {
        assertEquals("t_ns,ator,operacao,alvo,resultado,tentativas,falha_percebida,nota,t_fim_ns,espera_portao_ns",
                AcaoS3.HEADER);
        S3Registro reg = new S3Registro();
        reg.accept(acao(5_000L, 7_500L, 1_000L));
        reg.accept(new AcaoS3(6_000L, "s3-u-000", "Validate", "a,b", "OK_VALIDATE:VALID", 1, false, "", 6_100L, 0L));
        EpochClock ec = EpochClock.de(1_800_000_000_000_000_000L, 1_000L);
        Path f = dir.resolve("s3-acoes-rep-001.csv");
        reg.escreverCsv(f, ec);
        List<String> l = Files.readAllLines(f, StandardCharsets.UTF_8);
        assertEquals(AcaoS3.HEADER, l.get(0));
        // as 8 primeiras colunas: o mesmo texto do contrato 1 (t_ns convertido)
        assertEquals("1800000000000004000,admin,Block,s3-u-001,OK_STATUS,2,false,tentativa-incerta,"
                + "1800000000000006500,1000", l.get(1));
        assertEquals("1800000000000005000,s3-u-000,Validate,a_b,OK_VALIDATE:VALID,1,false,,1800000000000005100,0",
                l.get(2));
        assertEquals(3, l.size());
        for (String linha : l) {
            assertEquals(10, linha.split(",", -1).length, linha);
        }
        // sem relogio: valores crus
        Path g = dir.resolve("crus.csv");
        reg.escreverCsv(g, null);
        assertEquals("5000,admin,Block,s3-u-001,OK_STATUS,2,false,tentativa-incerta,7500,1000",
                Files.readAllLines(g, StandardCharsets.UTF_8).get(1));
    }
}
