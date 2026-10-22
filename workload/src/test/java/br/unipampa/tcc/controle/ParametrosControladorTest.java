package br.unipampa.tcc.controle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** B-28.1: parametros do controlador (plano de 2026-10-22, secao 3.5). */
class ParametrosControladorTest {

    private static final long S = 1_000_000_000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Conteudo fixo; SHA-256 calculado fora da JVM com {@code sha256sum}. */
    private static final String VALIDO = "{\"versao\":\"teste-1\",\"wNs\":5000000000,\"nLat\":100,\"nErr\":50,"
            + "\"thetaLatIn\":0.05,\"thetaLatOut\":0.02,\"thetaErrIn\":0.02,\"thetaErrOut\":0.005,"
            + "\"k\":2,\"m\":6,\"cooldownNs\":30000000000,\"estavelNs\":30000000000,\"holdViolNs\":120000000000}\n";
    private static final String SHA_VALIDO = "c1f111012a4853fc9f067d73958d93cbf91c5961e84fe1a7c573ec54e048ef19";

    @TempDir
    Path dir;

    @Test
    @DisplayName("padrao() traz os valores provisorios da secao 3.5")
    void padrao() {
        ParametrosControlador p = ParametrosControlador.padrao();
        assertEquals(5 * S, p.wNs());
        assertEquals(100, p.nLat());
        assertEquals(50, p.nErr());
        assertEquals(0.05, p.thetaLatIn());
        assertEquals(0.02, p.thetaLatOut());
        assertEquals(0.02, p.thetaErrIn());
        assertEquals(0.005, p.thetaErrOut());
        assertEquals(2, p.k());
        assertEquals(6, p.m());
        assertEquals(30 * S, p.cooldownNs());
        assertEquals(30 * S, p.estavelNs());
        assertEquals(120 * S, p.holdViolNs());
        assertNull(p.sha256(), "padrao nao vem de arquivo");
    }

    @Test
    @DisplayName("deJson le todos os campos e calcula o SHA-256 dos bytes do arquivo")
    void deJsonValido() throws IOException {
        Path f = dir.resolve("controlador-teste.json");
        Files.write(f, VALIDO.getBytes(StandardCharsets.UTF_8));
        ParametrosControlador p = ParametrosControlador.deJson(f);
        assertEquals("teste-1", p.versao());
        assertEquals(SHA_VALIDO, p.sha256());
        ParametrosControlador d = ParametrosControlador.padrao();
        assertEquals(d.wNs(), p.wNs());
        assertEquals(d.nLat(), p.nLat());
        assertEquals(d.nErr(), p.nErr());
        assertEquals(d.thetaLatIn(), p.thetaLatIn());
        assertEquals(d.thetaLatOut(), p.thetaLatOut());
        assertEquals(d.thetaErrIn(), p.thetaErrIn());
        assertEquals(d.thetaErrOut(), p.thetaErrOut());
        assertEquals(d.k(), p.k());
        assertEquals(d.m(), p.m());
        assertEquals(d.cooldownNs(), p.cooldownNs());
        assertEquals(d.estavelNs(), p.estavelNs());
        assertEquals(d.holdViolNs(), p.holdViolNs());
    }

    @Test
    @DisplayName("deJson aceita valores fora do padrao, inclusive k = m = 1 e tempos zero")
    void deJsonOutrosValores() throws IOException {
        ParametrosControlador p = ParametrosControlador.deJson(escrever(n -> {
            n.put("thetaLatIn", 0.12);
            n.put("thetaLatOut", 0.048);
            n.put("k", 1);
            n.put("m", 1);
            n.put("cooldownNs", 0L);
            n.put("estavelNs", 0L);
            n.put("holdViolNs", 0L);
            n.put("nLat", 0);
        }));
        assertEquals(0.12, p.thetaLatIn());
        assertEquals(0.048, p.thetaLatOut());
        assertEquals(1, p.k());
        assertEquals(1, p.m());
        assertEquals(0L, p.cooldownNs());
        assertEquals(0L, p.estavelNs());
        assertEquals(0L, p.holdViolNs());
        assertEquals(0, p.nLat());
    }

    @Test
    @DisplayName("SHA-256 muda quando o conteudo do arquivo muda")
    void shaDependeDoConteudo() throws IOException {
        String sa = ParametrosControlador.deJson(escrever(n -> n.put("versao", "a"))).sha256();
        String sb = ParametrosControlador.deJson(escrever(n -> n.put("versao", "b"))).sha256();
        assertEquals(64, sa.length());
        assertNotEquals(sa, sb);
    }

    static Stream<Arguments> invalidos() {
        return Stream.of(
                caso("thetaLatOut igual a thetaLatIn", n -> n.put("thetaLatOut", 0.05)),
                caso("thetaLatOut maior que thetaLatIn", n -> n.put("thetaLatOut", 0.06)),
                caso("thetaErrOut igual a thetaErrIn", n -> n.put("thetaErrOut", 0.02)),
                caso("thetaErrOut maior que thetaErrIn", n -> n.put("thetaErrOut", 0.5)),
                caso("thetaLatIn negativo", n -> n.put("thetaLatIn", -0.05)),
                caso("thetaLatOut negativo", n -> n.put("thetaLatOut", -0.01)),
                caso("thetaErrIn negativo", n -> n.put("thetaErrIn", -0.02)),
                caso("thetaErrOut negativo", n -> n.put("thetaErrOut", -0.001)),
                caso("nLat negativo", n -> n.put("nLat", -1)),
                caso("nErr negativo", n -> n.put("nErr", -1)),
                caso("cooldownNs negativo", n -> n.put("cooldownNs", -1L)),
                caso("estavelNs negativo", n -> n.put("estavelNs", -1L)),
                caso("holdViolNs negativo", n -> n.put("holdViolNs", -1L)),
                caso("wNs negativo", n -> n.put("wNs", -5L)),
                caso("wNs zero", n -> n.put("wNs", 0L)),
                caso("k zero", n -> n.put("k", 0)),
                caso("k negativo", n -> n.put("k", -2)),
                caso("m zero", n -> n.put("m", 0)),
                caso("campo obrigatorio ausente", n -> n.remove("holdViolNs")),
                caso("versao ausente", n -> n.remove("versao")),
                caso("campo desconhecido (erro de digitacao)", n -> n.put("thetaLatInn", 0.05)),
                caso("sha256 nao pode vir do arquivo", n -> n.put("sha256", "abc")),
                caso("inteiro com parte fracionaria", n -> n.put("k", 2.5)),
                caso("inteiro fora da faixa de int", n -> n.put("m", 3_000_000_000L)),
                caso("numero como texto", n -> n.put("nLat", "100")),
                caso("theta como texto", n -> n.put("thetaLatIn", "0.05")),
                caso("versao nao textual", n -> n.put("versao", 3)));
    }

    private static Arguments caso(String nome, Consumer<ObjectNode> mutacao) {
        return Arguments.of(nome, mutacao);
    }

    @ParameterizedTest(name = "rejeita: {0}")
    @MethodSource("invalidos")
    void deJsonRejeita(String nome, Consumer<ObjectNode> mutacao) throws IOException {
        Path f = escrever(mutacao);
        assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(f), nome);
    }

    @Test
    @DisplayName("deJson rejeita JSON malformado e raiz que nao e objeto")
    void deJsonMalformado() throws IOException {
        Path a = dir.resolve("malformado.json");
        Files.writeString(a, "{\"versao\": \"x\", ", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(a));
        Path b = dir.resolve("lista.json");
        Files.writeString(b, "[1, 2, 3]", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ParametrosControlador.deJson(b));
    }

    @Test
    @DisplayName("deJson propaga falha de leitura de arquivo inexistente")
    void deJsonArquivoInexistente() {
        assertThrows(IOException.class, () -> ParametrosControlador.deJson(dir.resolve("nao-existe.json")));
    }

    @Test
    @DisplayName("o construtor aplica as mesmas regras (padrao() e deJson nao divergem)")
    void construtorValida() {
        ParametrosControlador d = ParametrosControlador.padrao();
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControlador("x", d.wNs(), d.nLat(),
                d.nErr(), 0.05, 0.05, d.thetaErrIn(), d.thetaErrOut(), d.k(), d.m(),
                d.cooldownNs(), d.estavelNs(), d.holdViolNs(), null));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControlador("x", d.wNs(), d.nLat(),
                d.nErr(), Double.NaN, d.thetaLatOut(), d.thetaErrIn(), d.thetaErrOut(), d.k(), d.m(),
                d.cooldownNs(), d.estavelNs(), d.holdViolNs(), null));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControlador("x", d.wNs(), d.nLat(),
                d.nErr(), d.thetaLatIn(), d.thetaLatOut(), d.thetaErrIn(), d.thetaErrOut(), d.k(), 0,
                d.cooldownNs(), d.estavelNs(), d.holdViolNs(), null));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControlador(null, d.wNs(), d.nLat(),
                d.nErr(), d.thetaLatIn(), d.thetaLatOut(), d.thetaErrIn(), d.thetaErrOut(), d.k(), d.m(),
                d.cooldownNs(), d.estavelNs(), d.holdViolNs(), null));
    }

    private Path escrever(Consumer<ObjectNode> mutacao) throws IOException {
        ObjectNode n = (ObjectNode) JSON.readTree(VALIDO);
        mutacao.accept(n);
        Path f = Files.createTempFile(dir, "controlador-", ".json");
        Files.writeString(f, JSON.writeValueAsString(n), StandardCharsets.UTF_8);
        return f;
    }
}
