# Prompt de implementação do B-37: coletor de recursos para o *proxy* de energia (2026-10-27)

> Senior, 2026-10-27. Bloco do plano `docs/plano-implementacao-controlador-2026-10-22.md`, seção 5.1
> (o que coletar) e seção 6 (linha B-37), revisto em `c055399`. Roda em paralelo com B-26.2, B-27 e
> B-39. Não toca Java. O formato do CSV fixado aqui é o **contrato** que o B-39.2
> (`analysis/energia_proxy.py`) consome: os dois prompts carregam o mesmo texto da seção 3.1. Sem
> VM, sem push.

## 1. O que mudou desde o plano de 22/10 e afeta este bloco

1. **O *rebaseline* não coleta CPU** (DT-009, "Operação: ... sem coleta de CPU"), e o *bundle* da VM
   está congelado por SHA-256 (complemento da DT-009, parte 2). O coletor é um arquivo **novo** e
   não pode ser chamado por `run-rebaseline.sh`. Nenhum arquivo do *bundle* é tocado.
2. **Podman *rootful* nos dois ambientes.** A VM roda `sudo podman` com netavark, e os *scripts* usam
   `PODMAN="${PODMAN:-podman}"`. No *host* Windows o Podman 5.7 é *rootful*, dentro da máquina WSL
   `podman-machine-default`. Por isso o `/sys/fs/cgroup` dos contêineres só existe **dentro** dessa
   máquina, não no Git Bash. O *smoke* real roda lá dentro, por `podman machine ssh`. Lá o processo
   do *workload* (JVM no Windows) não é visível, e a fonte `pid:` só é exercitada pelos *shims*.
3. **Relógio:** os marcadores e os `end_ns` usam *epoch* ns. O coletor carimba cada amostra com
   `EPOCHREALTIME` (bash ≥ 5, sem *fork*), convertido para ns inteiros, e assim a CPU se recorta por
   fase no B-39.2.
4. **Custo de medir é desfecho.** O plano exige o custo do coletor. Leitura de arquivo por
   redirecionamento (`read < arquivo`, sem `cat`), um único `sleep` por ciclo, cadência por prazo
   absoluto (sem deriva acumulada) e uma linha final com a CPU do próprio coletor, filhos incluídos.

## 2. Escopo

**Entra:** `scripts/collect-resources.sh`, `scripts/collect-resources.test.sh` (*shims*),
`docs/contrato-recursos-v1.md`, *smoke* real curto dentro da máquina Podman.
**Não entra:** integração com `run-baseline.sh`/T-F (o *runner* da T-F decide), *metering* do
Hyper-V (`Enable-VMResourceMetering`, exige o Gestor como administrador; plano 5.1, opcional),
análise (B-39.2).

## 3. Prompt para o Worker

````text
Você é o WORKER. Uma entrega, B-37 (coletor de recursos). Commits só locais. NÃO faça push. NÃO use
a VM. NÃO use tc/netem.

BASE: c055399. Branch feature/b37-coletor-recursos, worktree ../wt-b37
(git worktree add ../wt-b37 -b feature/b37-coletor-recursos c055399).

PASSO 0:
 a) `mvn -q -o -f workload/pom.xml clean test` na base: esperado 106 arquivos, 761/0/0/1 (o bloco
    não toca Java; a medida serve de referência e é repetida no fim).
 b) `bash --version` no Git Bash (precisa de 5.x por causa de EPOCHREALTIME) e, dentro de
    `podman machine ssh`, `bash --version`, `uname -r`, `stat -fc %T /sys/fs/cgroup` (esperado
    cgroup2fs) e `getconf CLK_TCK`. Anote no relatório.
 c) `bash scripts/inject-jitter.test.sh` na base (anote PASS/FAIL): serve só para mostrar que as
    suítes de shell existentes não dependem deste bloco.

PAUSA OBRIGATÓRIA (regra 5.1 de docs/decisoes-senior-2026-10-16.md): quando o coordenador avisar uma
janela de injeção na VM ou uma noite de campanha, interrompa mvn, suítes e o smoke Podman, e só
retome com liberação expressa (inclui 5 min antes e depois).

ARQUIVOS PERMITIDOS (todos NOVOS): scripts/collect-resources.sh, scripts/collect-resources.test.sh,
  docs/contrato-recursos-v1.md, runs/b37-smoke/**, docs/relatorio-b37-<data>.md.
ARQUIVOS PROIBIDOS: todo arquivo existente, em especial scripts/run-rebaseline.sh,
  scripts/run-rebaseline.test.sh, scripts/inject-*.sh, scripts/rebaseline_matriz.py,
  scripts/run-baseline.sh, analysis/*, workload/*, cluster/*, runs/rebaseline/*, .gitattributes
  (*.sh já sai com LF? confira com `git ls-files --eol scripts/collect-resources.sh` e, se não sair
  LF no índice, PARE e reporte: mudar .gitattributes exige o Gestor).
`git diff --name-status c055399 HEAD` só com A.

CONTRATO (copie literalmente para docs/contrato-recursos-v1.md; o B-39.2 usa o mesmo texto):
 runs/<celula>/recursos-rep-NNN.csv, UTF-8, LF, cabeçalho exato
   t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado
 - Uma linha por fonte por amostra; todas as fontes de uma amostra com o MESMO t_epoch_ns (o
   instante do início do ciclo).
 - fonte: "cg:<conteiner>" (cgroup v2 do contêiner), "pid:<rotulo>" (processo dado por
   --pid rotulo=PID), "vm" (a máquina inteira, /proc/stat) e "coletor" (só na última linha).
 - cpu_usec_acum (inteiro, acumulado desde o boot ou desde a criação do cgroup):
     cg:  usage_usec de <cgroup>/cpu.stat
     pid: (utime + stime) de /proc/<pid>/stat × 1 000 000 / CLK_TCK, em aritmética inteira
     vm:  (user + nice + system + irq + softirq) da linha "cpu " de /proc/stat × 1 000 000 / CLK_TCK
          (guest e guest_nice já estão dentro de user/nice e NÃO são somados de novo; idle e iowait
          fora; steal fora, na coluna própria)
     coletor: (utime + stime + cutime + cstime) do próprio coletor, × 1 000 000 / CLK_TCK
 - mem_bytes (inteiro): cg: memory.current; pid: VmRSS de /proc/<pid>/status × 1024;
   vm: (MemTotal − MemAvailable) de /proc/meminfo × 1024; coletor: VmRSS do coletor × 1024.
 - steal_usec_acum: só em "vm" (campo steal × 1 000 000 / CLK_TCK); vazio nas demais.
 - estado: "ok", ou "erro:<motivo>" (motivo sem vírgula nem espaço, ex.: erro:cgroup-ausente,
   erro:pid-ausente, erro:leitura). Com erro, as colunas numéricas daquela linha ficam VAZIAS
   (nunca 0).
 runs/<celula>/recursos-meta-rep-NNN.json: {"contrato":"recursos/1","versao_coletor","sha256_script",
   "uname_r","clk_tck","nproc","intervalo_ms","podman_stats_s","fontes":[{"fonte","cgroup"|"pid"}],
   "inicio_epoch_ns","fim_epoch_ns","amostras","motivo_fim":"sinal"|"max-amostras"|"duracao",
   "atraso_max_ns"} (atraso_max_ns = maior atraso de um ciclo em relação ao seu prazo).
 runs/<celula>/recursos-podman-rep-NNN.jsonl: a cada --podman-stats-s segundos uma linha
   {"t_epoch_ns":..,"ok":true,"stats":<saída crua de `$PODMAN stats --no-stream --format json`>} ou
   {"t_epoch_ns":..,"ok":false,"erro":"..."}; só verificação cruzada (o B-39.2 não depende dela).

INTERFACE DO SCRIPT:
 scripts/collect-resources.sh --saida DIR --rep N --containers "isn1 isn2 isn3"
     [--pid rotulo=PID]... [--intervalo-ms 1000] [--podman-stats-s 10]
     [--duracao-s S | --max-amostras N] [--dry-run] [--help]
 - PODMAN="${PODMAN:-podman}" (na VM: PODMAN="sudo podman"); raízes injetáveis para teste:
   COLETOR_RAIZ_CGROUP (padrão /sys/fs/cgroup), COLETOR_RAIZ_PROC (padrão /proc); relógio e
   espera injetáveis: COLETOR_AGORA_NS_CMD e COLETOR_SLEEP_CMD (sem eles: EPOCHREALTIME e sleep).
 - Início: resolve o cgroup de cada contêiner por
   `$PODMAN inspect --format '{{.State.CgroupPath}}' <c>`; contêiner inexistente ou caminho que não
   existe sob a raiz ⇒ exit 3 ANTES da primeira amostra (nada de medir parcialmente sem aviso).
   PID inexistente no início ⇒ exit 3. Uso inválido ⇒ exit 2. Saída já existente
   (recursos-rep-NNN.csv) ⇒ exit 4 (nunca sobrescreve brutos).
 - Laço: prazo_k = início + k × intervalo; lê todas as fontes, grava, dorme (prazo_{k+1} − agora)
   se positivo; se negativo, não dorme e registra o atraso (atraso_max_ns). Fonte que some durante
   a coleta ⇒ linha "erro:..." e o laço continua.
 - Fim: SIGTERM/SIGINT, --duracao-s ou --max-amostras. Em todos: mata o sleep em curso, escreve a
   linha "coletor", fecha o meta e sai com 0. Nenhum processo filho sobra.
 - --dry-run: mostra contêineres, cgroups resolvidos, PIDs, raízes, intervalo e arquivos que seriam
   escritos; não lê contadores nem escreve nada; exit 0.
 - Escrita: o CSV é gravado em modo append a cada ciclo, sem arquivo temporário (uma queda da VM
   preserva os dados até ali); o meta é escrito só no fim, e a ausência dele indica coleta
   interrompida.

ORDEM DOS COMMITS (todo commit deixa o repositório utilizável: `bash -n` limpo nos dois scripts;
sem --amend; histórico linear):
 1. refactor: esqueleto de collect-resources.sh com parse de argumentos, --help, --dry-run e códigos
    de saída de uso (2), e as funções de leitura declaradas, devolvendo "erro:nao-implementado".
    `bash -n` limpo.
 2. test: collect-resources.test.sh completo (seção TESTES), no padrão de
    scripts/inject-jitter.test.sh (ok/contem, PASS/FAIL, exit 1 se alguma falhar). REGISTRE os
    FAIL esperados e as mensagens.
 3. feat: leituras, laço, sinais e meta. Suíte verde no Git Bash.
 4. docs: docs/contrato-recursos-v1.md.
 5. test: smoke real (seção SMOKE).
 6. docs: relatório.

TESTES (collect-resources.test.sh; shims num diretório temporário; nenhum podman real; a suíte
inteira em menos de 60 s no Git Bash):
 R1 dry-run: lista os 3 cgroups resolvidos pelo shim de podman e não cria nenhum arquivo. Dois
    formatos de CgroupPath no shim: o da máquina WSL (anote o real no passo 0b) e
    /machine.slice/libpod-<id>.scope (formato rootful típico de Ubuntu); os dois resolvem.
 R2 uso: sem --saida, --rep não numérico, --intervalo-ms 0, --duracao-s e --max-amostras juntos ⇒
    exit 2 com mensagem.
 R3 contêiner ausente (shim de inspect com exit 125) ⇒ exit 3, nenhum CSV criado.
 R4 saída existente ⇒ exit 4, arquivo intacto (sha256 antes e depois).
 R5 formato: --max-amostras 3 com relógio e sleep injetados ⇒ cabeçalho exato; 3 × (3 cg + 1 pid +
    1 vm) linhas + 1 "coletor"; mesmo t_epoch_ns dentro de uma amostra; t crescente entre amostras.
 R6 conversões: cpu.stat falso com usage_usec 123456 ⇒ 123456; /proc/<pid>/stat falso com utime 250,
    stime 150 e CLK_TCK 100 ⇒ 4 000 000; /proc/stat falso com user 100 nice 0 system 50 idle 9000
    iowait 7 irq 3 softirq 2 steal 40 guest 30 guest_nice 0 ⇒ vm cpu = (100+0+50+3+2)×10 000 =
    1 550 000 e steal = 400 000 (CLK_TCK 100); memory.current e VmRSS conferidos. CLK_TCK lido de
    `getconf CLK_TCK` (shim) e gravado no meta.
 R7 erro em execução: o cgroup de isn2 some depois da 1ª amostra ⇒ linhas de isn2 com
    "erro:cgroup-ausente" e colunas numéricas vazias; as demais fontes seguem "ok"; exit 0.
 R8 cadência: relógio injetado em que cada leitura "custa" 300 ms ⇒ o shim de sleep recebe
    durações de 0,7 s (prazo absoluto, sem deriva); custo de 1,2 s ⇒ nenhum sleep naquele ciclo e
    atraso_max_ns ≈ 200 ms no meta.
 R9 sinal: coletor real (sem relógio injetado, intervalo 200 ms) em segundo plano; SIGTERM depois de
    ~1 s ⇒ exit 0, linha "coletor" presente, meta com motivo_fim "sinal", e nenhum processo filho do
    coletor vivo 1 s depois (procure pelo PID-pai salvo; no Git Bash use `ps` do MSYS).
 R10 podman stats: --podman-stats-s 1 com shim de stats ⇒ linhas JSON válidas (python -c "json.loads"
    por linha); shim de stats falhando ⇒ {"ok":false,...} e o coletor segue.
 R11 meta: JSON válido; sha256_script = sha256 do próprio arquivo; amostras = número de ciclos.
 R12 sem fork no caminho quente: o laço não chama cat, awk, sed, cut, grep nem date (inspeção do
    corpo da função do ciclo, no padrão das guardas de fonte; só o sleep e o podman stats periódico
    podem criar processo).

MUTANTES (cópia do script no scratchpad, um por vez, suíte completa; todos precisam morrer):
 X1 pid sem a conversão CLK_TCK (ticks gravados como µs)        → R6
 X2 steal somado no cpu da vm                                    → R6
 X3 guest somado de novo no cpu da vm                            → R6
 X4 sleep fixo de intervalo (sem prazo absoluto)                 → R8
 X5 sem trap (sinal mata sem linha "coletor")                    → R9
 X6 erro de leitura gravado como 0                               → R7
 X7 cgroup por caminho fixo, sem podman inspect                  → R1, R3
 X8 sobrescreve saída existente                                  → R4
 X9 contêiner ausente vira linha de erro em vez de exit 3        → R3
 X10 t_epoch_ns lido por fonte (instantes diferentes na amostra) → R5

SMOKE REAL (dentro de podman-machine-default; NÃO a VM; depois do commit 4):
 - Peça a vez no Podman ao coordenador (B-26.2 e B-27 também usam a máquina). NÃO pare nem
   reinicie a máquina Podman.
 - podman-compose -p b37 (contêineres b37-isn1..3, portas 12537..12539); ps/network antes e depois
   iguais. Copie o script para a máquina (podman machine ssh + cat > arquivo) e confira o sha256 dos
   dois lados.
 - S1: 60 s ocioso (cluster sem carga), intervalo 1000 ms, --podman-stats-s 10.
 - S2: 60 s com carga leve: o workload do jar da base (sem rebuild obrigatório) S1 sync v3
   --threads 2 --duration 60 --warmup-min-sec 10 contra as portas do b37, rodando no Windows.
 - Critérios: exit 0; 60 ± 2 amostras; contadores monotônicos por fonte; CPU dos 3 cg maior em S2
   que em S1; soma dos 3 cg ≤ cpu da vm + 5 % (a VM WSL inclui outros processos; registre a razão);
   atraso_max_ns < 200 ms; CPU do próprio coletor em % de um núcleo (linha "coletor" ÷ duração),
   registrada; comparação, por contêiner, da CPU do cgroup no intervalo com a média do
   podman stats (só registro, sem critério).
 - Brutos em runs/b37-smoke/ com MANIFEST.sha256. "Smoke local: não é medida de energia."

RELATÓRIO docs/relatorio-b37-<data>.md: versões (passo 0), suíte por commit (FAIL esperados no
commit 2), X1..X10, smoke (tabela S1/S2, custo do coletor, razão cg/vm, atraso), `git diff
--name-status c055399 HEAD`, Surefire final igual a 761/0/0/1, desvios e pontos para o Gestor.
Liste SHAs, worktree e `git status`, e pare. Não integre nem faça push.
````

## 4. Critérios de aceite

1. Suíte verde no Git Bash; X1..X10 mortos; `bash -n` limpo em todo commit.
2. Contrato `recursos/1` documentado, igual ao da seção 3 do prompt do B-39.
3. Encerramento por sinal sem processo órfão e com linha de custo; nunca sobrescreve brutos.
4. *Smoke* real dentro da máquina Podman com custo do coletor medido; nenhum arquivo existente
   alterado; Surefire inalterado.

## 5. Pendências que não são deste bloco

- Validação na VM (Ubuntu 24.04, `sudo podman`, `cgroup2fs`) só num intervalo liberado pelo Gestor,
  no *smoke* do B-40. O caminho de cgroup do Podman *rootful* na VM pode ter outro formato
  (`machine.slice/libpod-<id>.scope`); o teste R1 cobre os dois formatos com *shims*.
- Decisão do Gestor sobre `Enable-VMResourceMetering` (plano 10, ponto 8): fora deste bloco.
