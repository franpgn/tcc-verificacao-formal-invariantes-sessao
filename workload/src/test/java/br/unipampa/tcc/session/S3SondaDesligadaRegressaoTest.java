package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * W-10 (nao regressao): com a sonda de replay desligada (o padrao), a simulacao S3 produz exatamente a
 * mesma saida da base {@code 494ccce} (ponta de {@code feature/controlador-integracao} antes do W-10).
 *
 * <p>O golden {@code src/test/resources/golden-w10-494ccce/s3-sem-sonda-semente7.txt} foi gerado por
 * este mesmo teste com as classes da base ({@code -Dw10.gravarGolden=true}) e nao e editado a mao. Ele
 * fixa, numa simulacao sequencial de 240 s do relogio falso com o no 2 fora do ar entre 100 s e 160 s:
 * a sequencia das acoes S3 (instantes do relogio falso), a sequencia dos eventos do CSV (operacao,
 * replica, codigo e chave; os instantes vem do nanoTime e ficam de fora), as violacoes (invariante,
 * chave e mensagem; o instante e de parede), os contadores do registro e os cabecalhos dos CSV de
 * eventos e de violacoes. Os sid (UUID aleatorio) viram {@code sid#n} pela ordem da primeira aparicao.</p>
 */
class S3SondaDesligadaRegressaoTest {

    static final String GOLDEN = "/golden-w10-494ccce/s3-sem-sonda-semente7.txt";

    @Test
    @DisplayName("W-10: sonda desligada = saida identica a da base 494ccce (acoes, eventos, violacoes, contadores, cabecalhos)")
    void sondaDesligadaIgualABase() throws IOException {
        String atual = assinaturaCompleta(corrida(7));
        if (Boolean.getBoolean("w10.gravarGolden")) {
            Path destino = Path.of("src/test/resources" + GOLDEN);
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, atual, StandardCharsets.UTF_8);
            return;
        }
        String golden;
        try (InputStream in = S3SondaDesligadaRegressaoTest.class.getResourceAsStream(GOLDEN)) {
            assertNotNull(in, "golden ausente: " + GOLDEN);
            golden = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(golden.replace("\r\n", "\n"), atual.replace("\r\n", "\n"));
    }

    static MundoS3 corrida(long semente) {
        MundoS3 m = new MundoS3(semente, null);
        m.foraDoAr(2, 100, 160);
        m.rodar(240);
        return m;
    }

    /** Texto canonico da corrida (ver o javadoc da classe). */
    static String assinaturaCompleta(MundoS3 m) throws IOException {
        Map<String, String> sids = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        long origem = 1_000_000_000_000L;
        sb.append("# acoes\n");
        for (AcaoS3 a : m.registro.acoes()) {
            sb.append(a.tNs() - origem).append('|').append(a.ator()).append('|').append(a.operacao()).append('|')
                    .append(rotulo(a.alvo(), sids)).append('|').append(a.resultado()).append('|')
                    .append(a.tentativas()).append('|').append(a.falhaPercebida()).append('|').append(a.nota())
                    .append('|').append(a.tFimNs() - origem).append('\n');
        }
        Path ev = Files.createTempFile("w10-ev", ".csv");
        Path vi = Files.createTempFile("w10-vi", ".csv");
        try {
            m.eventos.flush(ev);
            List<String> linhas = Files.readAllLines(ev, StandardCharsets.UTF_8);
            sb.append("# eventos\n").append(linhas.get(0)).append('\n');
            for (String l : linhas.subList(1, linhas.size())) {
                String[] c = l.split(",", -1);
                sb.append(c[1]).append('|').append(c[4]).append('|').append(c[5]).append('|')
                        .append(rotulo(c[6], sids)).append('\n');
            }
            m.auditor.dumpCsv(vi, null, List.of());
            List<String> lv = Files.readAllLines(vi, StandardCharsets.UTF_8);
            sb.append("# violacoes\n").append(lv.get(0)).append('\n');
        } finally {
            Files.deleteIfExists(ev);
            Files.deleteIfExists(vi);
        }
        List<InvariantAuditor.Violacao> vs = new ArrayList<>(m.auditor.drenar());
        for (InvariantAuditor.Violacao v : vs) {
            sb.append(v.invariante()).append('|').append(rotulo(v.chave(), sids)).append('|')
                    .append(v.mensagem()).append('\n');
        }
        sb.append("# contadores\n");
        m.registro.contadores().forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private static String rotulo(String alvo, Map<String, String> sids) {
        if (alvo == null || alvo.startsWith("s3-") || alvo.isEmpty()) {
            return alvo;
        }
        return sids.computeIfAbsent(alvo, k -> "sid#" + sids.size());
    }
}
