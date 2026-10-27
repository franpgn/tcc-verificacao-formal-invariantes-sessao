package br.unipampa.tcc.session;

import br.unipampa.tcc.controle.LeituraSaude;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-26.2 (T13, T14): adaptador REST da sonda de topologia, com um {@code LancadorCurl} falso (nenhum
 * curl real, nenhuma rede).
 */
class ConsultaSaudeRestTest {

    static final long MS = 1_000_000L;
    static final String USUARIO = "admin";
    static final String SENHA = "s3cr3t\"x";
    static final String URL = "http://127.0.0.1:11222";
    static final String ENDPOINT = "/rest/v2/cache-managers/default/health";

    /** Processo falso: corpo e exit fixos, ou trava ate ser destruido. Registra stdin e destruicao. */
    static final class ProcessoFalso extends Process {
        final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        volatile boolean stdinFechado;
        volatile boolean destruido;
        private final InputStream stdout;
        private final int exit;
        private final boolean trava;

        ProcessoFalso(String corpo, int exit, boolean trava) {
            this.stdout = new ByteArrayInputStream(corpo.getBytes(StandardCharsets.UTF_8));
            this.exit = exit;
            this.trava = trava;
        }

        static ProcessoFalso responde(String corpo) {
            return new ProcessoFalso(corpo, 0, false);
        }

        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int b) { stdin.write(b); }
                @Override public void write(byte[] b, int off, int len) { stdin.write(b, off, len); }
                @Override public void close() { stdinFechado = true; }
            };
        }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() throws InterruptedException {
            while (!waitFor(50, TimeUnit.MILLISECONDS)) {
                // espera
            }
            return exitValue();
        }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (!trava || destruido) {
                return true;
            }
            Thread.sleep(Math.max(1, unit.toMillis(timeout)));
            return destruido;
        }
        @Override public int exitValue() {
            if (trava && !destruido) {
                throw new IllegalThreadStateException("ainda vivo");
            }
            return trava ? 137 : exit;
        }
        @Override public boolean isAlive() { return trava && !destruido; }
        @Override public void destroy() { destruido = true; }
        @Override public Process destroyForcibly() {
            destruido = true;
            return this;
        }
    }

    /** Lancador falso: registra cada comando e devolve o processo do fornecedor. */
    static final class LancadorFalso implements ConsultaSaudeRest.LancadorCurl {
        final List<List<String>> comandos = Collections.synchronizedList(new ArrayList<>());
        final List<ProcessoFalso> processos = Collections.synchronizedList(new ArrayList<>());
        private final Supplier<ProcessoFalso> fornecedor;

        LancadorFalso(Supplier<ProcessoFalso> fornecedor) {
            this.fornecedor = fornecedor;
        }

        @Override public Process lancar(List<String> comando) {
            comandos.add(List.copyOf(comando));
            ProcessoFalso p = fornecedor.get();
            processos.add(p);
            return p;
        }
    }

    static String capturaReal(String nome) {
        try (InputStream in = ConsultaSaudeRestTest.class.getResourceAsStream("/captura-real/" + nome)) {
            assertNotNull(in, nome);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ConsultaSaudeRest adaptador(LancadorFalso l) {
        return new ConsultaSaudeRest(List.of(URL, "http://127.0.0.1:11223"), USUARIO, SENHA, l);
    }

    // ---------------------------------------------------------------- T13 adaptador

    @Test
    @DisplayName("T13: comando com --digest, -K -, --max-time e a URL de saude; senha nunca no comando")
    void comandoSemSenha() throws Exception {
        String corpo = capturaReal("rep3-isn3-healthy-3nos.json");
        LancadorFalso l = new LancadorFalso(() -> ProcessoFalso.responde(corpo));
        adaptador(l).consultar(0, 1500 * MS);
        assertEquals(1, l.comandos.size());
        List<String> cmd = l.comandos.get(0);
        assertEquals("curl", cmd.get(0));
        assertTrue(cmd.contains("--digest"), cmd.toString());
        assertTrue(cmd.contains("--fail"), cmd.toString());
        int k = cmd.indexOf("-K");
        assertTrue(k >= 0 && "-".equals(cmd.get(k + 1)), cmd.toString());
        int mt = cmd.indexOf("--max-time");
        assertTrue(mt >= 0, cmd.toString());
        assertEquals("2", cmd.get(mt + 1), "ceil(1,5 s) = 2");
        assertEquals(URL + ENDPOINT, cmd.get(cmd.size() - 1));
        for (String arg : cmd) {
            assertFalse(arg.contains(SENHA), "senha no argumento: " + arg);
            assertFalse(arg.contains("s3cr3t"), "senha no argumento: " + arg);
            assertFalse(arg.contains(USUARIO + ":"), "usuario:senha no argumento: " + arg);
        }
        assertFalse(cmd.contains("-u") || cmd.contains("--user"), cmd.toString());
        ProcessoFalso p = l.processos.get(0);
        assertEquals(HealthRest.configCurl(USUARIO, SENHA), p.stdin.toString(StandardCharsets.UTF_8));
        assertTrue(p.stdinFechado, "stdin precisa ser fechado para o curl ler a configuracao");
    }

    @Test
    @DisplayName("T13: --max-time = ceil(prazo em s), minimo 1; URL do indice pedido")
    void maxTime() throws Exception {
        String corpo = capturaReal("rep3-isn3-healthy-3nos.json");
        LancadorFalso l = new LancadorFalso(() -> ProcessoFalso.responde(corpo));
        ConsultaSaudeRest c = adaptador(l);
        c.consultar(1, 100 * MS);
        c.consultar(0, 1000 * MS);
        c.consultar(0, 2001 * MS);
        List<String> valores = new ArrayList<>();
        for (List<String> cmd : l.comandos) {
            valores.add(cmd.get(cmd.indexOf("--max-time") + 1));
        }
        assertEquals(List.of("1", "1", "3"), valores);
        assertEquals("http://127.0.0.1:11223" + ENDPOINT, l.comandos.get(0).get(l.comandos.get(0).size() - 1));
        assertEquals(3, c.lancamentos());
    }

    @Test
    @DisplayName("T13: exit diferente de 0 vira IOException com o exit")
    void exitNaoZero() {
        LancadorFalso l = new LancadorFalso(() -> new ProcessoFalso("", 22, false));
        IOException e = assertThrows(IOException.class, () -> adaptador(l).consultar(0, 1500 * MS));
        assertTrue(e.getMessage().contains("22"), e.getMessage());
    }

    @Test
    @DisplayName("T13: processo que nao termina no prazo e destruido e a consulta lanca timeout")
    void processoTravado() {
        LancadorFalso l = new LancadorFalso(() -> new ProcessoFalso("", 0, true));
        long t0 = System.nanoTime();
        assertThrows(TimeoutException.class, () -> adaptador(l).consultar(0, 100 * MS));
        long decorrido = (System.nanoTime() - t0) / MS;
        assertTrue(decorrido < 1000, "consulta levou " + decorrido + " ms com prazo de 100 ms");
        assertTrue(l.processos.get(0).destruido, "destroyForcibly nao foi chamado");
    }

    @Test
    @DisplayName("T13: interrupcao durante a espera destroi o processo e propaga InterruptedException")
    void interrupcao() throws InterruptedException {
        LancadorFalso l = new LancadorFalso(() -> new ProcessoFalso("", 0, true));
        AtomicReference<Throwable> erro = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                adaptador(l).consultar(0, 5_000 * MS);
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        t.start();
        long limite = System.nanoTime() + 1000 * MS;
        while (l.processos.isEmpty() && System.nanoTime() < limite) {
            Thread.sleep(5);
        }
        t.interrupt();
        t.join(1000);
        assertFalse(t.isAlive());
        assertTrue(erro.get() instanceof InterruptedException, String.valueOf(erro.get()));
        assertTrue(l.processos.get(0).destruido, "destroyForcibly nao foi chamado");
    }

    @Test
    @DisplayName("T13: corpo da captura real vira a mesma LeituraSaude de HealthRest.interpretar")
    void corpoReal() throws Exception {
        for (String nome : List.of("rep3-isn3-healthy-3nos.json", "rep3-isn3-degraded-1no.json",
                "rep2-isn1-healthy-rebalancing-3nos.json")) {
            String corpo = capturaReal(nome);
            LancadorFalso l = new LancadorFalso(() -> ProcessoFalso.responde(corpo));
            HealthRest.Estado e = HealthRest.interpretar(corpo);
            assertEquals(new LeituraSaude(e.numeroDeNos(), e.statusPorCache()), adaptador(l).consultar(0, 1500 * MS),
                    nome);
        }
    }

    @Test
    @DisplayName("T13: corpo que nao e JSON de saude vira IllegalArgumentException (falha de consulta)")
    void corpoInvalido() {
        LancadorFalso l = new LancadorFalso(() -> ProcessoFalso.responde("<html>401</html>"));
        assertThrows(IllegalArgumentException.class, () -> adaptador(l).consultar(0, 1500 * MS));
    }

    @Test
    @DisplayName("nos() e rotulo(i) vem das URLs, na ordem")
    void nosERotulos() {
        ConsultaSaudeRest c = new ConsultaSaudeRest(ConsultaSaudeRest.urlsDe("a:1,b:2,c:3"), USUARIO, SENHA,
                new LancadorFalso(() -> ProcessoFalso.responde("")));
        assertEquals(3, c.nos());
        assertEquals(List.of("a:1", "b:2", "c:3"), List.of(c.rotulo(0), c.rotulo(1), c.rotulo(2)));
    }

    // ---------------------------------------------------------------- T14 urlsDe

    @Test
    @DisplayName("T14: o padrao de --servers vira tres URLs http na mesma ordem")
    void urlsDoPadraoDaCli() {
        assertEquals(List.of("http://127.0.0.1:11222", "http://127.0.0.1:11223", "http://127.0.0.1:11224"),
                ConsultaSaudeRest.urlsDe("127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224"));
        assertEquals(List.of("http://127.0.0.1:12264", "http://127.0.0.1:12262"),
                ConsultaSaudeRest.urlsDe("127.0.0.1:12264,127.0.0.1:12262"));
    }

    @Test
    @DisplayName("T14: espacos nas pontas sao aceitos")
    void espacos() {
        assertEquals(List.of("http://h:1", "http://g:2"), ConsultaSaudeRest.urlsDe(" h:1 , g:2 "));
    }

    @Test
    @DisplayName("T14: item sem porta, porta invalida ou lista vazia: IllegalArgumentException citando o item")
    void invalidos() {
        for (String[] caso : new String[][] {{"h", "'h'"}, {"h:x", "'h:x'"}, {"h:0", "'h:0'"},
                {"h:70000", "'h:70000'"}, {"", "''"}, {"a:1,,b:2", "''"}, {":80", "':80'"}}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ConsultaSaudeRest.urlsDe(caso[0]), caso[0]);
            assertTrue(e.getMessage().contains(caso[1]), "'" + caso[0] + "' -> " + e.getMessage());
        }
        assertEquals(List.of("http://h:65535", "http://h:1"), ConsultaSaudeRest.urlsDe("h:65535,h:1"));
    }
}
