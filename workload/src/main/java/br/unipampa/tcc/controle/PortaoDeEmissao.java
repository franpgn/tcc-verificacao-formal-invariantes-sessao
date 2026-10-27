package br.unipampa.tcc.controle;

import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * B-27: portao de emissao do cliente, onde os knobs K1..K4 agem (plano de 2026-10-22, secao 3.4).
 * O perfil vem de {@link PerfilAtual}; os parametros, de {@link ParametrosKnobs}. Em
 * {@link Perfil#NORMAL} o portao nao altera nada (nenhuma espera, nenhuma conta), e
 * {@link #livre()} e o portao que nunca age.
 *
 * <ul>
 *   <li><b>K1 (admissao):</b> {@link #entrar()}/{@link #sair()} limitam as operacoes em voo a
 *       {@code concorrenciaNominal} em NORMAL e a {@code max(1, ceil(k1 * nominal))} em CONSERVADOR.
 *       Reduzir o limite nao expulsa quem ja entrou; a troca de perfil acorda todos os que esperam.</li>
 *   <li><b>K2 (ritmo):</b> {@link #pausaAposOperacaoNs()} (S1/S2) e {@link #aplicarPensar(long)} (S3).</li>
 *   <li><b>K3 (adiamento):</b> {@link #aguardarAdministrativa} segura Block, Unblock,
 *       IncrementFailure e ResetFailures em CONSERVADOR ate a volta a NORMAL, o prazo
 *       {@code k3AdiaMaxNs} ou {@link #encerrar()}. Nunca descarta: a operacao sempre segue.</li>
 *   <li><b>K4 (backoff):</b> {@link #esperaRetentativaNs} multiplica a base por
 *       {@code k4Fator * (1 + k4Jitter * (2u - 1))}, com {@code u} funcao pura de
 *       (sementeJitter, ator, inicio da acao, tentativa). Nao muda o numero de tentativas.</li>
 * </ul>
 *
 * <p>Os nomes das operacoes sao os canonicos de {@code SessionOps} (o pacote {@code controle} nao
 * importa {@code session}).</p>
 */
public final class PortaoDeEmissao {

    /** K3: operacoes adiaveis, por nome (qualquer ator). Login, Validate e Logout nunca esperam. */
    static final Set<String> ADIAVEIS = Set.of("Block", "Unblock", "IncrementFailure", "ResetFailures");

    /** K2 (S3): piso do pensar em CONSERVADOR, o mesmo de {@code ContextoS3.exponencialNs}. */
    static final long PISO_PENSAR_NS = 1_000_000L;

    private static final PortaoDeEmissao LIVRE = new PortaoDeEmissao();

    private final boolean livre;
    private final PerfilAtual perfil;
    private final ParametrosKnobs k;
    private final int concorrenciaNominal;
    private final FonteDeTempo tempo;
    private final Dormidor dormidor;
    private final Consumer<EventoK3> ouvinteK3;
    private final long sementeJitter;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition mudou = lock.newCondition();
    /** Guardado por {@link #lock}. */
    private int emVoo;
    private volatile boolean encerrado;
    private final LongAdder esperaK1 = new LongAdder();
    private final LongAdder adiamentos = new LongAdder();

    private PortaoDeEmissao() {
        this.livre = true;
        this.perfil = null;
        this.k = null;
        this.concorrenciaNominal = Integer.MAX_VALUE;
        this.tempo = null;
        this.dormidor = null;
        this.ouvinteK3 = null;
        this.sementeJitter = 0L;
    }

    /**
     * @param perfil              perfil em vigor (o portao se registra como ouvinte)
     * @param k                   parametros dos knobs
     * @param concorrenciaNominal operacoes em voo em NORMAL (threads em S1/S2, atores em S3), &gt;= 1
     * @param tempo               relogio das esperas medidas (K1, K3) e dos {@link EventoK3}
     * @param dormidor            espera da sondagem do K3
     * @param ouvinteK3           recebe cada {@link EventoK3} (o JSONL e do B-28.2)
     * @param sementeJitter       semente do jitter do K4
     */
    public PortaoDeEmissao(PerfilAtual perfil, ParametrosKnobs k, int concorrenciaNominal, FonteDeTempo tempo,
                           Dormidor dormidor, Consumer<EventoK3> ouvinteK3, long sementeJitter) {
        if (perfil == null || k == null || tempo == null || dormidor == null || ouvinteK3 == null) {
            throw new IllegalArgumentException("perfil, knobs, tempo, dormidor e ouvinteK3 sao obrigatorios");
        }
        if (concorrenciaNominal < 1) {
            throw new IllegalArgumentException("concorrenciaNominal deve ser >= 1: " + concorrenciaNominal);
        }
        this.livre = false;
        this.perfil = perfil;
        this.k = k;
        this.concorrenciaNominal = concorrenciaNominal;
        this.tempo = tempo;
        this.dormidor = dormidor;
        this.ouvinteK3 = ouvinteK3;
        this.sementeJitter = sementeJitter;
        perfil.registrar(this::aoMudarPerfil);
    }

    /** Portao sem efeito (singleton; nenhuma alocacao por chamada). */
    public static PortaoDeEmissao livre() {
        return LIVRE;
    }

    private boolean conservador() {
        return perfil.atual() == Perfil.CONSERVADOR;
    }

    private int limite() {
        return conservador()
                ? Math.max(1, (int) Math.ceil(k.k1FracaoConservador() * concorrenciaNominal))
                : concorrenciaNominal;
    }

    /** Ouvinte do perfil: o limite e relido por quem espera, e todos sao acordados. */
    private void aoMudarPerfil() {
        lock.lock();
        try {
            mudou.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ K1

    /**
     * Pede permissao para emitir uma operacao; bloqueia enquanto {@code emVoo >= limite}.
     *
     * @return ns esperados (0 se entrou na hora)
     * @throws InterruptedException se interrompido enquanto esperava (sem permissao)
     */
    public long entrar() throws InterruptedException {
        if (livre) {
            return 0L;
        }
        lock.lock();
        try {
            if (encerrado || emVoo < limite()) {
                emVoo++;
                return 0L;
            }
            long t0 = tempo.agoraNs();
            while (!encerrado && emVoo >= limite()) {
                mudou.await();
            }
            emVoo++;
            long esperou = Math.max(0L, tempo.agoraNs() - t0);
            esperaK1.add(esperou);
            return esperou;
        } finally {
            lock.unlock();
        }
    }

    /** Devolve a permissao de um {@link #entrar()} anterior. */
    public void sair() {
        if (livre) {
            return;
        }
        lock.lock();
        try {
            if (emVoo <= 0) {
                throw new IllegalStateException("sair() sem entrar() correspondente");
            }
            emVoo--;
            mudou.signal();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ K2

    /** S1/S2: pausa depois de cada operacao (0 em NORMAL). */
    public long pausaAposOperacaoNs() {
        return livre || !conservador() ? 0L : k.k2PausaConservadorNs();
    }

    /** S3: tempo de pensar ja sorteado; em NORMAL devolve o argumento sem nenhuma conta. */
    public long aplicarPensar(long pensarNs) {
        if (livre || !conservador()) {
            return pensarNs;
        }
        return Math.max(PISO_PENSAR_NS, Math.round(pensarNs * k.k2MultPensarConservador()));
    }

    // ------------------------------------------------------------------ K3

    /**
     * Segura uma operacao administrativa em CONSERVADOR (ver {@link #ADIAVEIS}) ate a volta a
     * NORMAL, o prazo {@code k3AdiaMaxNs} ou {@link #encerrar()}, sondando a cada
     * {@code k3SondagemNs}; depois emite um {@link EventoK3}. A operacao sempre segue depois.
     *
     * @return ns esperados (0 se nao foi adiada)
     */
    public long aguardarAdministrativa(String ator, String operacao, String alvo) throws InterruptedException {
        if (livre || encerrado || !ADIAVEIS.contains(operacao) || !conservador()) {
            return 0L;
        }
        long inicio = tempo.agoraNs();
        String motivo;
        while (true) {
            if (encerrado) {
                motivo = EventoK3.ENCERRAMENTO;
                break;
            }
            if (!conservador()) {
                motivo = EventoK3.SAIDA_CONSERVADOR;
                break;
            }
            long decorrido = tempo.agoraNs() - inicio;
            if (decorrido >= k.k3AdiaMaxNs()) {
                motivo = EventoK3.PRAZO;
                break;
            }
            dormidor.dormirNs(Math.min(k.k3SondagemNs(), k.k3AdiaMaxNs() - decorrido));
        }
        long fim = tempo.agoraNs();
        adiamentos.increment();
        ouvinteK3.accept(new EventoK3(ator, operacao, alvo, inicio, fim, motivo));
        return fim - inicio;
    }

    // ------------------------------------------------------------------ K4

    /**
     * Espera antes da proxima tentativa. NORMAL: {@code baseNs} (o chamador continua dobrando a
     * base). CONSERVADOR: {@code round(base * k4Fator * (1 + k4Jitter * (2u - 1)))}.
     */
    public long esperaRetentativaNs(String ator, long baseNs, int tentativa, long inicioAcaoNs) {
        if (livre || !conservador()) {
            return baseNs;
        }
        long z = mix64(sementeJitter ^ mix64(ator.hashCode()) ^ mix64(inicioAcaoNs) ^ tentativa);
        double u = (z >>> 11) * 0x1.0p-53;
        return Math.round(baseNs * k.k4FatorBaseConservador() * (1 + k.k4JitterConservador() * (2 * u - 1)));
    }

    /** Finalizador do SplitMix64, identico a {@code MisturaV3.mix64} (que e do pacote session). */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ------------------------------------------------------------------ ciclo de vida e contadores

    /** Libera quem espera em K1 e K3; entrar() e aguardarAdministrativa() seguintes nao esperam. Idempotente. */
    public void encerrar() {
        if (livre) {
            return;
        }
        encerrado = true;
        lock.lock();
        try {
            mudou.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int emVoo() {
        if (livre) {
            return 0;
        }
        lock.lock();
        try {
            return emVoo;
        } finally {
            lock.unlock();
        }
    }

    public int limiteAtual() {
        return livre ? Integer.MAX_VALUE : limite();
    }

    public long esperaK1TotalNs() {
        return livre ? 0L : esperaK1.sum();
    }

    public long adiamentosK3() {
        return livre ? 0L : adiamentos.sum();
    }
}
