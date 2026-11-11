package br.unipampa.tcc.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-33 (E2, condicao C-4 da validacao V-E1): custo de {@code block()}, que varre
 * {@code sessions.entrySet()} inteiro, frente ao timeout de 2 s do cliente no S3.
 *
 * <p>O que este teste MEDE: (1) o custo local da varredura de {@code block()} sobre N sessoes de
 * outras identidades em memoria (iteracao, copia e comparacao, sem rede); (2) o custo local de
 * serializar e desserializar as N sessoes com serializacao Java (aproximacao do trabalho do cliente
 * Hot Rod ao iterar o cache remoto com o {@code JavaSerializationMarshaller}). O que NAO mede:
 * iteracao no servidor, transferencia de rede e o efeito do cluster sob carga. Os numeros saem em
 * stdout ({@code CUSTO-BLOCK ...}) e vao para o relatorio; a unica asserção de tempo e um teto
 * frouxo, para pegar degeneracao e nao para validar desempenho.</p>
 */
class BlockCustoTest {

    private static MundoS3 mundoComSessoes(int n) {
        MundoS3 m = new MundoS3(1, null);
        for (int i = 0; i < n; i++) {
            String sid = String.format("sid-%07d", i);
            m.sessoes.dados.put(sid, SessionState.criar(sid, "outro-" + (i % 5000), Instant.now()));
        }
        for (int i = 0; i < 3; i++) {
            String sid = "alvo-" + i;
            m.sessoes.dados.put(sid, SessionState.criar(sid, "s3-u-000", Instant.now()));
        }
        return m;
    }

    @Test
    @DisplayName("block() sobre 100 mil e 500 mil sessoes de outras identidades invalida so as 3 do alvo (custo local medido)")
    void custoLocalDoBlock() throws Exception {
        for (int n : new int[] {100_000, 500_000}) {
            MundoS3 m = mundoComSessoes(n);
            long t0 = System.nanoTime();
            OpResult r = m.admin.block("s3-u-000");
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(OpResult.Code.OK_NOOP, r.code());
            long invalidas = m.sessoes.dados.values().stream()
                    .filter(s -> s.estado() == SessionState.Estado.INVALID).count();
            assertEquals(3, invalidas, "so as sessoes do alvo");
            System.out.println("CUSTO-BLOCK local sessoes=" + n + " block_ms=" + ms);
            assertTrue(ms < 20_000, "teto frouxo de degeneracao: " + ms + " ms");
        }
    }

    @Test
    @DisplayName("custo local de serializar e desserializar 100 mil sessoes (aproximacao do trabalho do cliente Hot Rod)")
    void custoLocalDeSerializacao() throws Exception {
        int n = 100_000;
        List<SessionState> todas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            todas.add(SessionState.criar(String.format("sid-%07d", i), "outro-" + (i % 5000), Instant.now()));
        }
        long t0 = System.nanoTime();
        List<byte[]> bytes = new ArrayList<>(n);
        long total = 0;
        for (SessionState s : todas) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (ObjectOutputStream o = new ObjectOutputStream(bo)) {
                o.writeObject(s);
            }
            bytes.add(bo.toByteArray());
            total += bo.size();
        }
        long serMs = (System.nanoTime() - t0) / 1_000_000L;
        long t1 = System.nanoTime();
        for (byte[] b : bytes) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b))) {
                in.readObject();
            }
        }
        long desMs = (System.nanoTime() - t1) / 1_000_000L;
        System.out.println("CUSTO-BLOCK serializacao sessoes=" + n + " bytes_por_sessao=" + (total / n)
                + " serializar_ms=" + serMs + " desserializar_ms=" + desMs);
        assertTrue(serMs + desMs < 60_000);
    }
}
