package br.unipampa.tcc.session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * B-33 (E2): monta os atores do S3 (pool U, pool L e administrador) sobre os servidores de
 * aplicacao e executa a simulacao. Cada usuario/conta e <i>sticky</i> a um servidor de aplicacao
 * (indice modulo o numero de servidores), como num balanceador real.
 *
 * <p>Duas formas de rodar: {@link #rodarSequencial} (um laco que sempre executa o ator de menor
 * {@code proximoNs}; deterministico com relogio falso, usado nos testes) e {@link #rodarEmThreads}
 * (uma thread por ator, relogio real, usada no experimento).</p>
 */
public final class S3Simulacao {

    private final ContextoS3 ctx;
    private final List<AtorS3> atores = new ArrayList<>();
    /** W-10: sonda de replay ({@code null} = desligada, padrao). */
    private final SondaReplay sonda;

    /**
     * @param servidoresApp  um {@link SessionOps} por servidor de aplicacao (ancorado ao seu no)
     * @param admin          console administrativo (no da maioria)
     * @param inicioNs       instante de inicio na base do relogio (as chegadas sao escalonadas)
     */
    public S3Simulacao(ContextoS3 ctx, List<SessionOps> servidoresApp, SessionOps admin, long inicioNs) {
        if (servidoresApp.isEmpty()) {
            throw new IllegalArgumentException("S3 exige ao menos um servidor de aplicacao");
        }
        this.ctx = ctx;
        int n = servidoresApp.size();
        // W-10: com --s3-replay-probe, a sonda de replay e seus atores (um por no), depois dos existentes
        this.sonda = ctx.p.sondaReplay
                ? new SondaReplay(servidoresApp, ctx.relogio, ctx.p, ctx.registro, ctx.rngSondaTardia())
                : null;
        for (int i = 0; i < ctx.p.usuarios; i++) {
            atores.add(new UsuarioVirtual(i, servidoresApp.get(i % n), ctx, inicioNs, sonda, i % n));
        }
        for (int i = 0; i < ctx.p.contasLimite; i++) {
            atores.add(new ContaLimiteS3(i, servidoresApp.get(i % n), admin, ctx, inicioNs));
        }
        atores.add(new AtorAdministrativoS3(admin, ctx, inicioNs));
        if (sonda != null) {
            atores.addAll(sonda.atores());
        }
    }

    public List<AtorS3> atores() {
        return atores;
    }

    /** W-10: a sonda de replay; {@code null} com a sonda desligada. */
    public SondaReplay sonda() {
        return sonda;
    }

    /**
     * Executa em um unico laco ate {@code fimNs}; a cada passo avanca o relogio ate o instante do
     * ator mais proximo (desempate pela ordem de criacao). Com relogio falso nunca dorme.
     */
    public void rodarSequencial(long fimNs, BooleanSupplier parar) {
        while (!parar.getAsBoolean()) {
            AtorS3 proximo = null;
            for (AtorS3 a : atores) {
                if (proximo == null || a.proximoNs() < proximo.proximoNs()) {
                    proximo = a;
                }
            }
            if (proximo == null || proximo.proximoNs() >= fimNs) {
                return;
            }
            try {
                ctx.relogio.dormirAteNs(proximo.proximoNs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            proximo.passo();
        }
    }

    /** Uma thread por ator; retorna quando todos terminam (fim do prazo ou {@code parar}). */
    public void rodarEmThreads(long fimNs, BooleanSupplier parar) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(atores.size());
        for (AtorS3 a : atores) {
            pool.submit(() -> {
                try {
                    while (!parar.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
                        long alvo = a.proximoNs();
                        if (alvo >= fimNs) {
                            return;
                        }
                        ctx.relogio.dormirAteNs(alvo);
                        if (parar.getAsBoolean()) {
                            return;
                        }
                        a.passo();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    // um ator nunca derruba o experimento; o erro fica visivel no stderr
                    System.err.println("S3: ator " + a.id() + " encerrou por excecao: " + e);
                }
            });
        }
        pool.shutdown();
        long espera = Math.max(0L, fimNs - ctx.relogio.agoraNs()) + 30_000_000_000L;
        if (!pool.awaitTermination(espera, TimeUnit.NANOSECONDS)) {
            pool.shutdownNow();
        }
    }
}
