import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * B-44.3: cliente do canal de diagnostico (probe) do JGroups 5.3.16 por TCP, sem criar processo.
 *
 * Protocolo (lido do codigo-fonte do JGroups 5.3.16.Final):
 *  - DiagnosticsHandler.runTCP: aceita a conexao, le com Util.readBytes ate '\n' ou EOF, responde e fecha.
 *  - Sem passcode: o pedido e a linha de texto (ex.: "op=GMS.suspect[isn3-123]").
 *  - Com passcode: prefixo binario = writeLong(t1) + writeDouble(q1) + writeInt(len) + SHA-1(passcode, t1, q1)
 *    (Util.createAuthenticationDigest / Util.createDigest; conferido em authorizeProbeRequest).
 *    Achado: Util.createDigest chama md.update(bb) sem bb.flip(), entao t1 e q1 nao entram no resumo; o
 *    resumo efetivo e SHA-1(passcode), fixo, e o servidor nao confere t1 (sem protecao contra repeticao).
 *    Como Util.readBytes para no primeiro byte 0x0A, o prefixo nao pode conter 0x0A: o cliente sorteia
 *    (t1, q1) de novo ate o prefixo sair sem 0x0A (o Probe.java do JGroups nao faz isso).
 *
 * Uso: java ProbeTcp [--passcode X] [--passcode-errado] [--timeout-ms N] alvo=pedido [alvo=pedido ...]
 *   alvo = host:porta; os pedidos sao enviados em paralelo (uma thread por alvo, liberadas juntas).
 * Saida (TSV, uma linha por pedido): rotulo t_envio_ms t_resposta_ms dur_ms bytes status resposta_escapada
 */
public class ProbeTcp {
    static String passcode = null;
    static boolean passcodeErrado = false;
    static int timeoutMs = 10000;

    public static void main(String[] args) throws Exception {
        List<String[]> pedidos = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--passcode": passcode = args[++i]; break;
                case "--passcode-errado": passcodeErrado = true; break;
                case "--timeout-ms": timeoutMs = Integer.parseInt(args[++i]); break;
                default:
                    int k = args[i].indexOf('=');
                    pedidos.add(new String[]{args[i].substring(0, k), args[i].substring(k + 1)});
            }
        }
        String[] linhas = new String[pedidos.size()];
        CountDownLatch largada = new CountDownLatch(1);
        Thread[] ts = new Thread[pedidos.size()];
        for (int i = 0; i < pedidos.size(); i++) {
            final int idx = i;
            final String alvo = pedidos.get(i)[0], pedido = pedidos.get(i)[1];
            ts[i] = new Thread(() -> {
                try { largada.await(); } catch (InterruptedException e) { return; }
                linhas[idx] = envia(alvo, pedido);
            });
            ts[i].start();
        }
        largada.countDown();
        for (Thread t : ts) t.join();
        for (String l : linhas) System.out.println(l);
    }

    static byte[] prefixo(String pc) throws Exception {
        SecureRandom rnd = new SecureRandom();
        for (int tent = 0; tent < 1000; tent++) {
            long t1 = System.currentTimeMillis() + tent;
            double q1 = rnd.nextDouble();
            MessageDigest md = MessageDigest.getInstance("SHA");
            md.update(pc.getBytes());
            ByteBuffer bb = ByteBuffer.allocate(16);
            bb.putLong(t1);
            bb.putDouble(q1);
            md.update(bb); // igual ao JGroups: sem flip(), entao nenhum byte de bb entra no resumo
            //                (MessageDigest.update(ByteBuffer) consome remaining() = 0)
            byte[] dig = md.digest();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeLong(t1);
            out.writeDouble(q1);
            out.writeInt(dig.length);
            out.write(dig);
            out.flush();
            byte[] p = baos.toByteArray();
            boolean temLf = false;
            for (byte b : p) if (b == '\n') { temLf = true; break; }
            if (!temLf) return p;
        }
        throw new IllegalStateException("nao achei prefixo sem 0x0A");
    }

    static String envia(String alvo, String pedido) {
        String host = alvo.substring(0, alvo.lastIndexOf(':'));
        int porta = Integer.parseInt(alvo.substring(alvo.lastIndexOf(':') + 1));
        long t0 = System.currentTimeMillis();
        long t0n = System.nanoTime();
        String status;
        String resp = "";
        int bytes = 0;
        try (Socket s = new Socket()) {
            s.setSoTimeout(timeoutMs);
            s.connect(new InetSocketAddress(host, porta), timeoutMs);
            OutputStream out = s.getOutputStream();
            byte[] corpo = pedido.getBytes(StandardCharsets.US_ASCII);
            if (passcode != null) {
                byte[] p = prefixo(passcodeErrado ? passcode + "-errado" : passcode);
                byte[] tudo = new byte[p.length + corpo.length];
                System.arraycopy(p, 0, tudo, 0, p.length);
                System.arraycopy(corpo, 0, tudo, p.length, corpo.length);
                corpo = tudo;
            }
            out.write(corpo);
            out.write('\n');
            out.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) buf.write(b, 0, n);
            bytes = buf.size();
            resp = buf.toString(StandardCharsets.UTF_8);
            status = bytes > 0 ? "ok" : "vazia";
        } catch (Exception e) {
            status = "erro:" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
        long t1 = t0 + (System.nanoTime() - t0n) / 1_000_000L;
        String esc = resp.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        return alvo + "|" + pedido + "\t" + t0 + "\t" + t1 + "\t" + (t1 - t0) + "\t" + bytes + "\t" + status + "\t" + esc;
    }
}
