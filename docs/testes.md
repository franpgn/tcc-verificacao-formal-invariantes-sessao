# Testes

> Registro da suíte de testes do *workload* e dos critérios de aceite verificados na frente prática.

## Suíte unitária (JUnit 5)

Execução: `mvn -f workload/pom.xml test`. Estado atual: **49/49 verde** nos blocos da tabela abaixo; na branch de integracao `feature/etapa-formal-integracao` (2026-10-16) o Surefire completo mede **184 testes, 0 falhas, 0 erros, 2 skipped** (inclui B-33), e `python -m unittest discover -s analysis` (da raiz) mede 9/9.

| Classe de teste | n | Cobre |
|---|---|---|
| `CliTest` | 9 | parsing Picocli, *defaults*, validação de parâmetros |
| `ScenarioTest` | 3 | mistura S1 50/50, S2 95/5, soma de pesos = 100 |
| `KeyGeneratorTest` | 6 | distribuição Zipfian, reprodutibilidade por seed, cobertura |
| `WarmupPolicyTest` | 7 | fases warm-up/descarte/medição (T11) |
| `LatencyRegistryTest` | 4 | percentis HdrHistogram, dump CSV, supressão por *warm-up* |
| `InvariantAuditorTest` | 5 | violações I1–I6 imediatas e periódicas |
| `OpResultTest` | 8 | mapeamento `Code → return_code` e "só ERROR_TRANSPORT é erro" |
| `EventCsvWriterTest` | 4 | cabeçalho/ordem do CSV, *op_id* crescente, *warm-up* não vaza, `end_ns > start_ns`, sanitização |
| `EpochClockTest` | 3 | conversão monotônico → epoch-ns, preservação de duração |

### Testes do bloco de eventos (T5)

- **Mapeamento de códigos** (`OpResultTest`): cada `OpResult.Code` mapeia
  para o `return_code` canônico aprovado; varre todos os `Code` garantindo
  que apenas `ERROR_TRANSPORT` fique fora do conjunto de sucessos do
  `analysis/percentis.py`.
- **Cabeçalho e ordem** (`EventCsvWriterTest`): o CSV gravado começa
  exatamente com `op_id,operation,start_ns,end_ns,replica,return_code,key`
  e cada linha de dados tem 7 colunas na ordem do cabeçalho; `op_id` é
  estritamente crescente (contador global).
- **Warm-up não vaza**: com `WarmupPolicy.never()`, nenhum evento entra; o
  arquivo contém só o cabeçalho e `descartadosWarmup()` reflete o volume.
- **`end_ns > start_ns`**: validado em 200 linhas geradas; também após a
  conversão epoch-ns em `EpochClockTest`.

## Critérios de aceite integrados (cluster real)

Verificados contra o *cluster* Infinispan size=3 (Podman, imagem
`quay.io/infinispan/server:15.0.21.Final`).

### GATE 1 — formação do *cluster*

```bash
curl -s --digest -u admin:infinispan \
  http://localhost:11222/rest/v2/cache-managers/default/health
# {"cluster_health":{"health_status":"HEALTHY","number_of_nodes":3,...}}
```

Aceite: `health_status = HEALTHY` e `number_of_nodes = 3`, *caches*
`sessions` e `counters` HEALTHY.

### SMOKE (GATE 2) — carga curta + pipeline

Carga de 60s (rep 1, warm-up 10s) contra o *cluster* real; depois
`python percentis.py ../runs/smoke`:

- `runs/smoke/rep-001.csv`: ~540 700 linhas (1 cabeçalho + eventos).
- `return_code`: apenas códigos de sucesso (`BLOCKED`, `COUNTED`, `NONE`);
  **0 `ERROR_TRANSPORT`**.
- `summary.json`: 5 operações canônicas, `n` entre ~27 mil e ~270 mil,
  `taxa_erro = 0,00%` em todas, sem exceção.

Latências observadas (smoke, sem falha): Validate p50 ≈ 0,41 ms / p99 ≈
1,99 ms; Login p50 ≈ 0,46 ms / p99 ≈ 2,63 ms.

## Critérios de aceite do B-12 (falha F3 — jitter/netem)

`scripts/inject-jitter.sh` injeta a falha F3 (T18) aplicando uma
disciplina `netem` (atraso+jitter) sobre o `eth0` do nó alvo. O mecanismo
foi migrado de `podman exec <node> tc ...` (não funciona: a imagem oficial
Infinispan/UBI minimal não tem `tc` nem a capability `NET_ADMIN`) para
**aplicação host-side via `nsenter`** no namespace de rede do container:
`pid=$(podman inspect -f '{{.State.Pid}}' <node>)` e em seguida
`sudo nsenter -t <pid> -n tc qdisc add dev eth0 root netem ...`
(ver `docs/implementacao.md` TD-007). Calibração oficial TCC-I:
`delay 20ms 13ms distribution normal` → p99 ≈ MEAN + 2,326·JITTER ≈ +50 ms
(alvo T18). `--distribution lognormal` fica como rodada de sensibilidade.

**Pré-requisitos da execução real (no host da VM Ubuntu):** `podman`,
`nsenter` (util-linux), `tc` (iproute2), `sudo` e o módulo `sch_netem`. O
**container não precisa de `tc` nem `NET_ADMIN`**. Como cada célula da
bateria chama `inject-jitter.sh` (vários `sudo` por célula) e o cache do
`sudo` expira em ~15 min, **configurar `sudo` sem senha** para o usuário da
bateria na VM antes de rodar a bateria completa.

Testes sem-VM (rodam neste host antes do reboot da VM Hyper-V), em
`scripts/inject-jitter.test.sh` — **31 asserts, 0 falhas**:

| ID | Teste | Esperado | Estado |
|---|---|---|---|
| T-jit-1 | `--help` documenta todas as flags | exit 0, uso completo | PASS |
| T-jit-2 | `--dry-run` imprime plano `nsenter` add/sleep/del **sem podman no PATH**; resolve pid via `podman inspect`; não usa `podman exec` | exit 0 | PASS |
| T-jit-2b/2c | dry-run com `--node`/`--distribution lognormal`/decimais/`us` | refletido no plano (`nsenter`/`podman inspect`) | PASS |
| T-jit-3 | `--duration abc` | exit 2 | PASS |
| T-jit-4 | `--delay 20` (sem sufixo), `--jitter xyz`, `--distribution gauss`, `--duration 0`, flag desconhecida, flag sem valor | exit 2 | PASS |
| T-jit-7 | `run-baseline.sh --faults "none F3" --dry-run` | descreve ramo F3 (`S1 / F3`), exit 0 | PASS |

Exit codes verificados diretamente: 0 (`--help`, `--dry-run`), 2 (arg
inválido), 3 (`podman`/`nsenter`/`sudo` ausentes no modo real). Os exit 4
(nó ausente/parado ou PID não resolvido) e 5 (netem rejeitado /
`sch_netem` ausente) só são atingíveis com as dependências presentes e são
exercitados na VM.

**Pendente de VM (gate pós-reboot, ver `docs/plano-f3-vm-netem.md` §3.5/3.7):**

| ID | Teste | Ambiente |
|---|---|---|
| T-jit-4 (real) | sonda netem falha sem `sch_netem` → exit 3 com diagnóstico | WSL2/container sem módulo |
| T-jit-5 | Ctrl-C durante o `sleep` → qdisc removido (trap de cleanup) | VM com cluster |
| T-jit-6 | `--duration 5` real: durante há netem, depois interface limpa | VM com cluster |

A execução real exige a VM porque o kernel do WSL2 não traz `sch_netem`
(motivo da migração para Hyper-V/Ubuntu).

## Critérios de aceite do B-29 (falha F2 — partição de rede, `inject-partition.sh`)

Sem VM (rodam em qualquer host; `bash scripts/inject-partition.test.sh`). T-par-7 a T-par-15 usam *shims* de `podman`, `sudo`, `nsenter`, `tc` e `flock` no `PATH`, que só registram as chamadas e simulam o estado de um `clsact` por PID. Eles verificam a **lógica** (ordem, limpeza, códigos, sinais), **não** que o kernel aceita `clsact`/`u32`/`drop` (isso é o gate G-0).

| ID | Teste | Esperado |
|---|---|---|
| T-par-1 | `--help` | exit 0, documenta todas as flags e o exit 6 |
| T-par-2 | `--dry-run` com `PATH` sem podman | exit 0, plano com `nsenter`, `clsact`, filtros ingress/egress por par, `qdisc del clsact`, sem `podman exec` |
| T-par-3 | `--dry-run`, 5 nós, `--minority isn1`, `--duration`, `--marker` | 4 filtros ingress e 4 egress; minoria de 2 em 5 aceita |
| T-par-4 | argumentos inválidos (sem `--minority`, nó fora de `--nodes`, maioria, metade, `--duration 0`, flag desconhecida, flag sem valor, nome com `;`) | exit 2 |
| T-par-5 | `--duration 20` | aviso "atraso severo" e plano normal |
| T-par-6 | `run-baseline.sh --faults "none F2" --dry-run` | **SKIP até B-29.1** (o baseline ainda não integra F2) |
| T-par-7 | execução completa com *shims* (`--duration 1`) | sequência resolve, pré-limpeza, sonda, add, filtros por par, show, del; marcadores start/end; só toca o PID do nó isolado |
| T-par-8 | `tc` falha na sonda / `clsact` rejeitado | exit 5 e limpeza chamada |
| T-par-9 | `SIGTERM` durante a janela | exit 143, `qdisc del clsact` antes de sair, marcador `end` gravado |
| T-par-10 | `--heal` | só `qdisc del clsact` (sem add/filter); tolera nó ausente |
| T-par-11 | conexão ao par ainda possível | exit 6 e limpeza |
| T-par-12 | contadores zerados no fim da janela | exit 6 e limpeza |
| T-par-13 | nó isolado/par inexistente ou parado | exit 4, nada alterado |
| T-par-14 | `PODMAN="sudo podman"` | reflete no plano e funciona na execução |
| T-par-15 | `flock` real: segunda injeção no mesmo nó | exit 1 (SKIP onde não há `flock`, p.ex. Git Bash) |
| T-par-16 | Contadores por sentido: `at 12` = ingress, `at 16` = egress; um sentido 0 avisa "um so sentido" (exit 0); ambos 0 = exit 6 | A1 de V-B |
| T-par-17 | `log()` recalcula o timestamp a cada chamada | primeira e ultima linha diferem |
| T-par-18 | Guarda de netns: alvo/par/--heal com netns == PID 1 ou ilegivel -> exit 4 sem nsenter/tc (--heal limpa os demais) | A2 de V-B |

Resultado desta rodada (integracao dos fixes A1-A3, resolvida a mao): Git Bash (Windows) 125 PASS / 0 FAIL / 2 SKIP; Ubuntu (WSL) com `flock` real 127 PASS / 0 FAIL / 1 SKIP. Isolados: contadores 111/0/2 e 113/0/1; netns 113/0/2 e 115/0/1; base 99/0/2 e 101/0/1.

Nota de TDD (A1/A3 de V-B): o vermelho `bc4a16f` (T-par-16/17) tinha defeito no shim de `tc`: faltava `n="${n16}"` no laco `at 16`; foi corrigido em `3f7b8c1` junto com o fix. Os 7 FAIL do vermelho sao reais e o vermelho puro (teste final + codigo antigo) tambem deu 7 FAIL (medido pelo Senior).

Nota de TDD (A2 de V-B): o vermelho `946a2f6` (T-par-18) tem 11 asserts FAIL (Git Bash e WSL), nao 9 como constava no relatorio de entrega (contagem defasada; numero de asserts, nao de testes).

Rodada de retomada (2026-10-16, Worker-3): severidades renomeadas S1..S5 -> N1..N5 (T-nd-6g: o nome antigo S3 e rejeitado com exit 2). Git Bash 285 PASS / 0 FAIL / 1 SKIP; WSL Ubuntu 293 PASS / 0 FAIL / 0 SKIP. Mutantes novos (Git Bash), todos mortos: sem nenhum `trap` -> falhas em T-nd-12/13 (residuo, sem marcador end); checagem de raiz alheia desativada -> 6 FAIL (T-nd-18a/b, exit 5 em vez de 7); checagem de `clsact` desativada -> 3 FAIL (T-nd-18d); marcador `fault=F4` trocado por F3 -> 4 FAIL (T-nd-5n, T-nd-9); `remover_degradacao` neutralizada no cleanup -> falhas em T-nd-9/10 (residuo).

**Gate real na VM (pendente)**: G-0 (kernel aceita `clsact` + `u32` + `drop`), G-1 (contadores > 0 nos dois sentidos, conexão a `IP_par:7800` falha, Hot Rod a isn3 responde com erro e não com *timeout*), G-2 (sem resíduo), G-3 (vistas 2 e 1; isn3 DEGRADED em `sessions` e `counters`), G-4 (fusão e tempo), G-5 (`SIGTERM` e `SIGKILL`+`--heal`), G-6 (coexistência com F3). Comandos no relatório `docs/relatorio-b29-implementacao-2026-10-09.md`. Resultados reais **ainda não registrados**.

## Critérios de aceite do B-29.2 (falha F4 — degradação parcial de rede, `inject-netdegrade.sh`)

Sem VM (rodam em qualquer host; `bash scripts/inject-netdegrade.test.sh`). Os testes T-nd-9 em diante usam *shims* de `podman`, `sudo`, `nsenter`, `tc`, `ping` e `flock` no `PATH`, que só registram as chamadas e simulam, por PID, a qdisc raiz (`prio` do injetor ou `netem` de F3), o `netem` da banda 3, os filtros `u32` e um `clsact` (F2). O shim de `tc` registra `BARE_TC` se for chamado fora de `nsenter` (critério: nenhum `tc` sem `nsenter`). Escritos **antes** do script (TDD): sem o script, 0 PASS / 1 FAIL; contra um *stub* que só faz `exit 0`, 93 PASS (asserções triviais, como `nao_contem` e `exit 0`) e 191 FAIL.

| ID | Teste | Esperado |
|---|---|---|
| T-nd-0 | `bash -n`; nenhuma linha do script chama `tc` sem `nsenter`; sem `podman exec` | limpo |
| T-nd-1 | `--help` | exit 0, documenta todas as flags, os exit codes 0 a 7 e `PODMAN` |
| T-nd-2 | `--dry-run` padrão (direção `out`) sem `podman` | plano com `nsenter`, `prio`, `netem` na banda 3, filtro `u32` por par, `qdisc del root`, contadores, locks (F4 e F2); 2 filtros; toda linha de `tc` passa por `nsenter` |
| T-nd-3 | `--direction in`/`both`, 5 nós, `--peers` | `in`: um `netem` por par com filtro apontando ao alvo; `both`: alvo + pares; `--peers` restringe |
| T-nd-4 | perda assimétrica (`--loss-in`) | 20% na saída do alvo e 50% nos pares; `--loss-in 0` deixa só a saída |
| T-nd-5 | parâmetros do `netem` | `delay`/`jitter`/`distribution normal`, `loss ... corr`, `duplicate`, `reorder r c`, `corrupt`, `gemodel` (p=6,25% r=25% para média 20% e rajada 4), perda fracionária, `--iface`, `--duration`, marcador |
| T-nd-6 | severidades N1 a N5 | 1%, 5%, 20%, 50%, 99,9%; aviso de equivalência com partição em N5 e `loss ≥ 99`; `--severity` com `--loss` → exit 2 |
| T-nd-7 | `--duration` curta | aviso; 60 s sem aviso |
| T-nd-8 | 38 argumentos inválidos (loss fora de 0..100, `--reorder` sem `--delay`, `--direction` inválida, nó fora da lista, `--peers` com o alvo, gemodel inviável, nome com `;`, ...) | exit 2, também sem `--dry-run` |
| T-nd-9 | execução completa com *shims* (`--duration 1`) | sequência resolve, inspeção da raiz, sonda, `del`, aplica (prio, netem, filtros por par), ping, contadores, `del`; nenhum `tc` sem `nsenter`; só toca o PID do alvo; marcadores `fault=F4`; sem resíduo |
| T-nd-10 | `--direction in`/`both`, assimétrica | `nsenter` nos PIDs dos **pares**; dois valores distintos de perda; perda esperada do ping composta (36% para 20%+20%) |
| T-nd-11 | parâmetros intactos ao `tc`; gemodel; só atraso; `--peers` com 5 nós | string completa do `netem`; verificação por ping "não se aplica" sem perda |
| T-nd-12 | kernel rejeita (filtro na sonda, `prio`, `netem`, gemodel, filtro na aplicação, filtro num par) | exit 5, regra parcial removida pelo `trap`, sem marcador quando a falha nem começou |
| T-nd-13 | `SIGTERM` durante a janela (3 nós), `SIGHUP`, `SIGTERM` durante o ping | exit 143/129, `del root` nos 3 nós após o sinal, marcador `end` uma só vez, sem resíduo. `SIGINT` **não** é testável aqui: o bash ignora INT em jobs assíncronos não interativos |
| T-nd-14 | `--heal` | só `del root` nos nós com `prio 1:` (alvo e pares); **não** apaga a raiz de F3; tolera nó ausente; idempotente; `--dry-run` |
| T-nd-15 | exit 6 | perda observada 0%/40%/50% contra a esperada; 25% e 100% (N5) dentro da tolerância; `Sent`+`dropped` zerados; N5 com `Sent` 0 e `dropped` > 0 passa (T-nd-15l/m); perda sem `dropped` (≥ 10 esperados → erro; amostra pequena → aviso); só atraso com `Sent` zerado |
| T-nd-15n..r | guarda de netns (R4) | alvo ou par com o mesmo netns do PID 1, ou netns ilegível → exit 4 sem nenhum `tc`; `--heal` não toca nó em rede do host |
| T-nd-16 | exit 4 | alvo ou par inexistente; contêiner não `running`; nada alterado |
| T-nd-17 | exit 3 | `PODMAN` inexistente |
| T-nd-18 | exit 7 | raiz de F3 no alvo ou num par (`in`); `clsact` de F2 no mesmo nó; nada alterado nem removido (raiz alheia intacta); F3/F2 em nó **não tocado** coexistem; `--heal` respeita a raiz alheia |
| T-nd-19 | pré-limpeza | sobra própria removida antes da sonda; duas execuções seguidas |
| T-nd-20 | `PODMAN="sudo podman"` | no plano e na execução |
| T-nd-21 | `flock` real | segunda injeção F4 no mesmo nó → exit 1; lock de F2 ocupado → exit 7; F4 em andamento segura o lock de F2 do nó (SKIP onde não há `flock`, p.ex. Git Bash) |

**O que os *shims* NÃO provam** (todos pendentes do gate L-0 a L-6 na VM, T-C2): que o kernel tem `sch_prio`/`sch_netem`/`cls_u32` e aceita `gemodel`, `reorder` e `corrupt`; que o filtro `u32` casa o tráfego JGroups e deixa Hot Rod e SSH intactos; que o `netem` produz a taxa configurada no fio; o formato real de `tc -s qdisc` e de `ping` (o *parser* foi escrito contra o formato documentado, não contra a VM); o `nsenter` com `sudo` rootful; qualquer efeito sobre TCP, JGroups ou Infinispan.

Resultado desta rodada: Git Bash (Windows, MSYS bash 3.6) 298 PASS / 0 FAIL / 1 SKIP (T-nd-21: sem `flock`); Ubuntu (WSL2, bash 5.1.16, `flock` real) 306 PASS / 0 FAIL / 0 SKIP (medido na integracao 2026-10-16; a rodada original da branch era 284/292, antes das correcoes R3/R4 e do shim de readlink). Mutação de sanidade (5 mutantes do script, WSL): removida a checagem de `clsact` → 3 FAIL; `eh_nosso` sempre verdadeiro → falhas em T-nd-9/10/11; `--direction in` tocando o alvo → falhas em T-nd-3/10; tolerância do ping ×10 → 3 FAIL (T-nd-15); sem o `trap` explícito de `TERM` → 0 FAIL (mutante equivalente: o bash executa o `trap EXIT` ao morrer por `SIGTERM`).

## Critérios de aceite do F1 como *crash* silencioso (`inject-crash.sh`, 2026-10-28)

Sem VM (`bash scripts/inject-crash.test.sh`, ~30 s no Git Bash). Os casos usam *shims* de `podman`, `sudo`, `readlink`, `nsenter`, `ip` e `sleep` no `PATH`, que registram as chamadas e simulam o estado do *container* (running/exited, ExitCode) e da interface (UP/DOWN) por PID. Eles verificam a **lógica** do injetor, **não** que o `link set down` real cala o JGroups sem FIN nem que o FD\_ALL3 detecta a queda em ~40 s (isso é o *smoke* local e o ensaio na VM, §4 de `docs/prompt-f1-kill-2026-10-27.md`). Escritos antes do código (TDD): contra o `inject-crash.sh` de `c055399`, 56 PASS / 57 FAIL.

| ID | Caso | Esperado |
|---|---|---|
| C1 | modo padrão | `silencioso`; ordem exata: `readlink` do alvo, `readlink` do PID 1, `ip link show`, `ip link set down`, `ip -o link show`, `podman kill <nó> --signal KILL`, ExitCode, `sleep`, `podman start` |
| C2 | modos `silencioso` e `kill` | `podman stop` nunca é chamado |
| C3 | guarda de *netns* e PID | *netns* do alvo = do PID 1, PID 0, vazio, não numérico ou 1: exit 4, nenhum `ip`/`nsenter`/`kill`; alvo ausente ou parado: exit 4 |
| C4 | interface inexistente | exit 4 sem `kill` nem `link set` |
| C5 | `link set down` que não pega | tentativa de `link set up`, exit 1, sem `kill` nem `start` |
| C6 | ExitCode 143 | `start` feito, linha `ExitCode=143`, exit 1 |
| C7 | `kill` que não derruba em ~10 s | exit 5, sem espera nem `start` |
| C8 | `podman start` com erro | exit 5 |
| C9 | TERM depois do isolamento e antes do `kill` | o *trap* dá `kill` e `start`; exit 143; sem espera |
| C10 | TERM durante a espera | `start` uma vez; exit 143 em < 10 s com `--duration 30` |
| C11 | carimbo por linha | formato `AAAA-MM-DDTHH:MM:SS.mmmZ`; carimbos diferentes antes e depois de uma espera real de 2 s |
| C12 | `--dry-run` e `--help` | sem `podman`/`sudo`/`nsenter`/`ip` (*shims* proibidos não chamados), plano com `link set dev eth0 down` e `kill`; `--help` documenta `--modo`, `--iface`, FD\_SOCK2 e FD\_ALL3 |
| C13 | modos `kill` e `stop` | `kill`: sem `readlink`/`ip`; `stop`: `stop`, `sleep`, `start` (junho) |
| C14 | argumentos | `--modo x`, `--duration 0`, `--iface` vazio ou inválido, `--modo` sem valor, *flag* desconhecida: exit 2 |
| C15 | linhas de K6 | o *log* casa `\bmodo=silencioso\b`, `\beth0 down\b`, `\bkill isn2\b`, `\bExitCode=137\b`, `\bstart isn2\b` |

K6 de F1 (`analysis/rebaseline.py`, classe `A9bK6F1Silencioso` de `analysis/test_rebaseline.py`): *log* completo ACEITA; cada um dos 5 sinais ausente RECUSA; `stop isn2` no lugar de `kill isn2` RECUSA; *log* de junho RECUSA; `ExitCode=143` RECUSA; modo `kill` RECUSA. O *shim* do `podman` de `scripts/run-rebaseline.test.sh` ganhou `kill` (exited, ExitCode 137) e `{{.State.ExitCode}}`, e há um *shim* novo de `ip`; R3, R4 e R8 medem `podman|kill|isn2` no lugar de `podman|stop|isn2`, com os mesmos limites. Resultados e mutantes em `docs/relatorio-f1-silencioso-2026-10-27.md`.

## Como reproduzir

```bash
# 1. cluster
cd cluster && podman-compose up -d        # aguardar HEALTHY size=3
# 2. build + suíte
mvn -f workload/pom.xml test              # 49/49
mvn -f workload/pom.xml package
# 3. smoke
java -jar workload/target/session-workload-0.1.0-SNAPSHOT.jar \
  --scenario S1 --duration 60 --warmup-min-sec 10 --rep 1 --csv-dir runs/smoke
cd analysis && python percentis.py ../runs/smoke
# 4. bateria baseline (S1/S2 x none/F1)
bash scripts/run-baseline.sh --reps 3 --duration 120 --warmup-min 20
# 5. bateria F3 (jitter) — APENAS na VM com sch_netem; netem via nsenter
#    host-side, exige sudo (recomendado: sudo sem senha p/ a bateria)
bash scripts/run-baseline.sh --faults "none F3" --reps 3 --duration 120 --warmup-min 20
# 6. testes sem-VM do inject-jitter (rodam em qualquer host)
bash scripts/inject-jitter.test.sh        # 31 PASS
# 7. testes sem-VM do inject-partition (F2; shims, qualquer host)
bash scripts/inject-partition.test.sh     # 99-101 PASS (T-par-6 e T-par-15 podem ser SKIP)
# 8. testes sem-VM do inject-netdegrade (F4; shims, qualquer host; ~4 min no Git Bash)
bash scripts/inject-netdegrade.test.sh    # 284-292 PASS (T-nd-21 e SKIP onde nao ha flock)
# 9. testes sem-VM do inject-crash (F1 silencioso; shims, qualquer host)
bash scripts/inject-crash.test.sh         # 113 PASS
```

## B-32: propriedades operacionais no TLC (2026-10-09)

`spec/run-tlc-propriedades.sh` roda `I1F`, `I4F`, `I2Fa`, `I2Fb`, `I1M`, `I4M` (mais `I4Fp`, `I4Mp`, `I1P`, `MesclarPerdeChave`) sobre o cenário
semeado (`SpecSemeada`) ou o modelo livre, isolando falha (`SemFalha`/`SoQueda`/`SoParticao`). Resultados e limites em
`docs/relatorio-b32-tlc-propriedades-2026-10-09.md`; logs brutos em `runs/tlc/prop-*/`. Requer `tla2tools.jar` (variável `TLA_TOOLS`) e Java 11+.

## Integracao local `feature/etapa-formal-integracao` (2026-10-16)

Contagens medidas apos cada fusao (Git Bash PASS/FAIL/SKIP | WSL Ubuntu com `flock` real). Detalhes em `docs/integracao-etapa-formal-2026-10-16.md`.

| Passo | inject-partition | inject-netdegrade | inject-jitter | Outras |
|---|---|---|---|---|
| eol + B-29 + gate | 99/0/2 \| 101/0/1 | n/a | 35/0 \| 35/0 | |
| + fix contadores (A1/A3) | 111/0/2 \| 113/0/1 | n/a | | |
| + fix netns (A2), conflito resolvido | 125/0/2 \| 127/0/1 | n/a | | |
| + B-29.2 | 125/0/2 \| 127/0/1 | 298/0/1 \| 306/0/0 | 35/0 \| 35/0 | |
| + B-32 (+auditoria) + B-33 (final) | 125/0/2 \| 127/0/1 | 298/0/1 \| 306/0/0 | 35/0 \| 35/0 | `mvn test` 184/0/0 (2 skipped); `python -m unittest discover -s analysis` 9/9 |

## Gate v3: testes sem VM de g1/g1b/g5/g6 (2026-10-16)

Branch `test/b29-gate-vm-v3`; detalhes e comandos da VM em `docs/relatorio-gate-v3-2026-10-16.md`. Shims e injetores falsos em `runs/gate-b29/gate-test-lib.sh`; nenhum toca em VM, rede ou privilegio.

```bash
bash runs/gate-b29/g5.test.sh     # T-g5-1..7: 50 PASS (TERM, KILL+--heal, sem start, sem clsact, rc!=137, ordem dead-man, sem GO)
bash runs/gate-b29/g6.test.sh     # T-g6-1..8: 70 PASS (ordem, netns, coexistencia, F3<F2, amostra tardia, PID2, corpo do dead-man, espera desde o fim da F2)
bash runs/gate-b29/g1.test.sh     # T-g1-1..7: 48 PASS (log de saude de 2 s, g1.sh completo/abort/sem GO, G1_LOG_SAUDE_S=0)
bash runs/gate-b29/g1b.test.sh    # 25 ok (completa, COLETA=minima, COLETA invalida)
```

Contagens medidas (Git Bash | WSL): g5 50/0 | 50/0; g6 70/0 | 70/0; g1 48/0 | 48/0; g1b falhas=0 | falhas=0; inject-partition 125/0/2 | 127/0/1; inject-netdegrade 298/0/1 | 306/0/0; inject-jitter 35/0 | 35/0; `mvn test` 205/0/0 (3 skipped); `unittest` analysis 9/9.

### Integracao local, segunda rodada (gate v3 + g1fix + C-2 + REP=2)

Medido em `feature/etapa-formal-integracao` apos `ff-only` de `test/b29-gate-v3-g1fix`, cherry-pick de C-2 (3 commits) e de `8394843` (Git Bash | WSL): `mvn test` 275/0/0 (0 skipped); g1 61/0; g1b falhas=0; g5 50/0; g6 70/0; inject-partition 125/0/2 | 127/0/1; inject-netdegrade 298/0/1 | 306/0/0; inject-jitter 35/0 | 35/0; `unittest` analysis 9/9. Estas contagens substituem as de g1 (48) e `mvn` (184 e 205) citadas acima. Detalhes em `docs/integracao-etapa-formal-2026-10-16.md`.

## B-27.3 (W-1): retirada do portão de emissão K1..K4 (2026-11-04)

Branch `refactor/w1-remover-acoes-cliente` sobre `20805b0`. Contagens medidas com `mvn -q clean test` em `workload/` (contadas dos `TEST-*.xml` do Surefire) e `python -m unittest discover -s analysis` na raiz.

| Momento | Surefire (testes/falhas/erros/pulados, arquivos) | `unittest` analysis |
|---|---|---|
| base `20805b0` | 863/0/0/1, 122 | 187 OK, 1 *skip* (`analysis/` não muda nesta branch) |
| depois do W-1 | 825/0/0/1, 116 | 187 OK, 1 *skip* |

825 = 863 − 42 apagados + 4 novos. Apagados com a funcionalidade (D-6): `PortaoDeEmissaoK1Test` (6), `PortaoDeEmissaoK2K4Test` (5), `PortaoDeEmissaoK3Test` (5), `ParametrosKnobsTest` (6), `ArquiteturaPortaoTest` (1), `PerfilAtualTest` (2), `PortaoS1S2Test` (5), `PortaoS3AplicacaoTest` (8), `PortaoS3SimulacaoTest` (4). Novos: `ArquiteturaSemAcaoNoClienteTest` (R-C1, 1), `S3SemPortaoTest` (R-C2, 2), `GoldenRodarClienteTest` (R-C3, 1; referência `golden-20805b0/rodar-cliente-s1-s2.txt` gerada na base). `S3SimulacaoTest`, `GoldenS1S2Test` e `golden-8e2e815/` inalterados.

## W-3 (B-28.2): laço de controle, atuador sobre interface e JSONL (2026-11-04)

Branch `feature/w3-laco-controlador` sobre `098900f` (W-1 integrado). Contagens com `mvn -q -T1 clean test` em
`workload/` (somadas dos `TEST-*.xml`) e `python -m unittest discover -s analysis` na raiz.

| Momento | Surefire (testes/falhas/erros/pulados, arquivos) | `unittest` analysis |
|---|---|---|
| base `098900f` (= `0f8b351` + docs) | 825/0/0/1, 116 | 187 OK, 1 *skip* |
| depois do W-3 | 894/0/0/1, 123 | 187 OK, 1 *skip* (`analysis/` não muda) |

894 = 825 + 69 novos, em 7 arquivos:

| Arquivo | Testes | O que cobre |
|---|---|---|
| `controle/ControladorDefasagemTest` | 16 | R2/R3/R4 com defasagem (alta, calma, intermediária, indeterminada), cooldown, ordem dos motivos, assinatura antiga = nova com defasagem indeterminada (1000 sequências) |
| `controle/ExecutorDeAcoesTest` | 15 | shadow nunca chama o atuador; *thread* única; LIFO; idempotência; falha na aplicação revertida; reversão que falha fica na pilha; encerramento com prazo e atuador travado; gancho da JVM; atuador nulo |
| `controle/LacoDeControleTest` | 12 | CA-T4 (tick fecha janela sem eventos, R2 em até W + L + 1 s; sem tick nada decide); G-1 (R1 em cooldown, active e shadow); JSONL linha a linha; R4 desfaz tudo; origem; fora do gatilho; *thread*; CA-T3 ao vivo; política que lança |
| `controle/ControleArquivosTest` | 6 | `ArquivoReferencia` (`referencia-p99/1`); `TrilhaJsonl`; formato de `decisao` e `acao`; H4 |
| `session/ControladorCliTest` | 10 | `--controller*`, exigências (config, admin, csv-dir, reference em active, marker em S3), H6 no admin, carga e recusa dos arquivos, `--dry-run` |
| `session/LigacaoControladorTest` | 7 | item 7 (off não abre nada); DT-001/DT-002; sem rastro; H7 (corrida não chega ao controlador); ouvinte de evento; ponta a ponta com `FakeCaches` (R2 por erro, R4 depois de m janelas, JSONL, H6); encerramento idempotente |
| `session/ConvergenciaResumoFimCargaTest` | 3 | `t_fim_carga_ns` aditivo no resumo da convergência |

Testes existentes editados (listados no relatório): `controle/TiposBasicosTest.ordemDeSeveridade` (novo valor
`Motivo.DEFASAGEM` no enum) e `session/ArquiteturaSemAcaoNoClienteTest` (R-C1 por pares, ressalva 4 da validação
do W-1, e `LigacaoControlador` autorizado). Mutantes conferidos à mão: pilha FIFO, shadow chamando o atuador, R2
sem defasagem, R4 sem reversão, violação do *warm-up* aceita; os cinco morrem.

### W-3, correções da V-W3 (R1, R2, R4, N1, N3, contrato v1.1)

Parecer `docs/validacao-w3-2026-11-04.md` (`df70c71`). Três pares `test:` → `fix:`; os três `test:` (`a877a56`,
`e15fcbc`, `9fa7250`) conferidos vermelhos com `mvn -q -o -T1 test-compile` numa worktree temporária (exit 1;
22, 20 e 10 linhas de erro de compilação), e os `fix:` intermediários (`f1a44d8`, `9126635`) compilam.

| Momento | Surefire (testes/falhas/erros/pulados, arquivos) | `unittest` analysis |
|---|---|---|
| W-3 (`f820e73`) | 894/0/0/1, 123 | 187 OK, 1 *skip* |
| V-W3 corrigida (`3988e32`) | 905/0/0/1, 124 | 187 OK, 1 *skip* |

905 = 894 + 11 novos: `session/WorkloadMainCargaControleTest` (5: R1 com controlador lento, off, N1 com exceção,
N1 ponta a ponta em active, fonte de `WorkloadMain`); `controle/ExecutorDeAcoesTest` +3 (`prazoDinamicoComTresAcoes`,
`prazoDinamicoSemAcoes`, `repedidaComReversaoFalhaGravaLinha`); `controle/LacoDeControleTest` +2 (`linhasPerdidas`,
`tDecisaoNs`); `session/LigacaoControladorTest` +1 (`regraDoTick`). Testes do W-3 editados só para acompanhar
assinatura ou campo novo: `ExecutorDeAcoesTest` (construtor sem prazo de encerramento; `encerrarComAtuadorTravado`
com `T_cham` = 150 ms e `T_enc` = 300 ms conferido; `parametros`), `LacoDeControleTest` (construtor da `Montagem`,
ordem dos campos de `decisao`), `ControleArquivosTest.formatoDecisao` (`t_decisao_ns`), `LigacaoControladorTest`
(`Pecas`), `ConvergenciaResumoFimCargaTest` (`t_fim_controle_ns`). Nenhum teste anterior ao W-3 mudou.

Testes de tempo real na rodada: `prazoDinamicoComTresAcoes` 0,921 s (3 × 300 ms dentro de 1,2 s),
`pontaAPonta` 1,515 s, `excecaoDaCargaReverteAcaoReal` 0,214 s, `linhasPerdidas` 0,219 s; nenhum oscilou.

## W-h4: amostrador de infraestrutura e critério h4 (DT-018, complemento 3) (2026-11-04)

Branch `feature/h4-amostrador-infra` sobre `19d45b9`. Suítes novas, todas no host, sem VM (relatório
`docs/relatorio-h4-amostrador-2026-11-04.md`):

| Suíte | Comando | Resultado |
|---|---|---|
| `analysis/test_h4.py` (48 testes: janela de 10 s, limiares, janela h4, segmento do F1, h4c, parada, recalibração com os brutos do S7, `repor`, `visao`, constantes cruzadas) | `python -I -m unittest discover -s analysis -p "test_h4.py"` | 48 OK |
| `scripts/amostrador-infra.test.sh` (A1..A7: `PATH` vazio, formato v1, cadência, SIGTERM, espelho, `iniciar`/`parar`/`estado` com *shims*, `--id`) | `bash scripts/amostrador-infra.test.sh` | 83 PASS, 0 FAIL |
| `scripts/vigia-h4.test.sh` (V1..V4) | `bash scripts/vigia-h4.test.sh` | 21 PASS, 0 FAIL |
| `scripts/h4-reposicoes.test.sh` (sequência do C3 §5 com o `run-rebaseline.sh` real) | `bash scripts/h4-reposicoes.test.sh` | 29 PASS, 0 FAIL |

| Momento | `unittest` analysis (sem `-I`) | `run-rebaseline.test.sh` |
|---|---|---|
| base `19d45b9` | 187 OK, 1 *skip* | 130 PASS, 0 FAIL (último registro, `relatorio-f1-silencioso-2026-10-27.md`) |
| depois do W-h4 | 235 OK, 1 *skip* (187 + 48 do `test_h4.py`) | 130 PASS, 0 FAIL (sem mudança) |

Com `-I`, a suíte inteira perde três módulos que importam `numpy` do `site-packages` do usuário. `test_h4.py` roda
com `-I`.

## W-2a (B-44.2): atuador REST do *cluster* e política por assinatura (2026-11-05)

Branch `feature/b44-2-atuador-cluster` sobre `e869a31`. Código e testes escritos com o host em silêncio (piloto na
VM); rodados depois da liberação (H2). Vermelho conferido com `mvn -q -o -T1 test-compile` numa worktree temporária:
`93238be` exit 1 (375 linhas `ERROR`), `28a1409` exit 1 (símbolos do atuador ausentes); `431f4a7` compila.

| Momento | Surefire (testes/falhas/erros/pulados, arquivos) | `unittest` analysis |
|---|---|---|
| base `e869a31` | 905/0/0/1, 124 | 235 OK, 1 *skip* |
| W-2a (`68dc72e`) | 961/0/0/1, 130 | 235 OK, 1 *skip* (`analysis/` não muda) |

961 = 905 + 56 novos, em 6 arquivos (contagem por método `@Test`):

| Arquivo | Testes | O que cobre |
|---|---|---|
| `controle/AcaoClusterTiposTest` | 7 | AC-2 global e reversível; AC-5 num nó, não reversível, prazo `T_cham` + 3 s; `reversao` no JSONL; linha `politica`; `PlanoDeAcao`; `AtuadorPorTipo` |
| `controle/ExecutorNaoReversivelTest` | 7 | AC-5 fora da pilha e de `ativas()`; prazo por tipo; R4 desfaz AC-2 e não AC-5; AC-5 que falha sem reversão e sem repetição; `reverter`/gancho ignoram AC-5; shadow; `T_enc` com AC-5 em curso |
| `controle/EstadoTopologiaPorNoTest` | 6 | `vistaCompleta`, `noSemResposta`, `vistasDivergentes`; validações; sonda `resumirComNosAte` e `resumirAte` sem `porNo`; padrão da `FonteDeTopologia` |
| `controle/PoliticaPorAssinaturaTest` | 16 | linhas da tabela 5 (P1, P2, P0, divergentes, volta do nó, teto `T_reb`, NORMAL); AC-5 nunca com vista incompleta, para nó sem resposta, duas vezes, com suspeito trocando, abaixo do teto; persistência quebra no salto; AC-2 nunca com divergência; AC-2 não para o nó parado; parâmetros |
| `session/AtuadorRestInfinispanTest` | 16 | AC-2 sucesso, só no nó que respondeu, 4xx/5xx, nó lento, POST lento, leitura em 1 nó, divergência com o executor revertendo; Digest e Basic; AC-5 sucesso, alvo que continua, 403, alvo sem resposta, outro nó sem resposta; shadow sem pedido; AC-1/AC-3 recusados |
| `session/ArquiteturaAtuadorTest` | 4 | R-ARQ-1, R-ARQ-2 (lista fechada `TrilhaJsonl`, `ParametrosControlador`, `ArquivoReferencia`; gancho da JVM aceito), R-ARQ-3, autoteste dos detectores |

`ServidorAdminFalso` é apoio, sem `@Test`. `GoldenS1S2Test` passou na rodada (S1/S2 idênticos).

Testes existentes editados (D-C): `session/ArquiteturaSemAcaoNoClienteTest` (par `AtuadorRestInfinispan` →
`AcaoCluster`, `AtuadorCluster`, `ResultadoAtuacao`) e `session/WorkloadMainCargaControleTest:132` (`AC_1` com
`DEGRADED_MODE` → `AC_2` global sem valor; nenhuma asserção retirada). As edições autorizadas de
`ExecutorDeAcoesTest` (31-33, 217, 413-420) **não** foram feitas: sozinhas reprovam as asserções de rótulo listadas
no P-36.

Mutantes do item 7, conferidos à mão (`runs/b44-2/mutantes.txt`; os sete morrem). Chamada em shadow travou o
`prazoEncerramentoComAc5EmCurso` na primeira rodada (o atuador bloqueado passou a ser chamado na thread do laço) e
foi refeita só contra `shadowSemPedido`, que falhou com os pedidos recebidos. Pilha invertida
(`ExecutorDeAcoesTest` LIFO, `ExecutorNaoReversivelTest.r4NaoDesfazAc5`); AC-5 empilhada
(`ExecutorNaoReversivelTest.ac5ForaDaPilha`, `r4NaoDesfazAc5`); teto `T_reb` esquecido
(`PoliticaPorAssinaturaTest.reversaoNoTeto`); chamada em shadow (`AtuadorRestInfinispanTest.shadowSemPedido`,
`ExecutorNaoReversivelTest.shadowAc5`); AC-5 mandada a nó sem resposta (`PoliticaPorAssinaturaTest.ac5NuncaParaNoSemResposta`,
`AtuadorRestInfinispanTest.ac5NoSemResposta`); AC-2 por cache em vez de global (`AtuadorRestInfinispanTest.ac2Sucesso`,
`semRebalanceamentoPorCache`, `ac2LeituraEmUmNo`); e o AC-5 sem a guarda no atuador (`ac5NoSemResposta`).

Testes de tempo real (servidor falso), medidos: `ac2NoLento` 1,009 s (prazo 1 s, limite 1,5 s), `ac2PostLento`
1,026 s (2 s), `ac5Sucesso` 0,314 s, `ac5AlvoContinua` 0,816 s (800 ms, limite 1,3 s), `digest` 0,402 s,
`prazoEncerramentoComAc5EmCurso` 0,215 s.

Tarefa zero no *cluster* local `w2a` (3 nós, imagem 15.0.21.Final, XML sem *caches* + `cria-caches.sh`, portas
12501-12503, sem VM), brutos em `runs/b44-2/verificacao/`:

| Item | Resultado | Arquivo |
|---|---|---|
| (a) Basic × Digest | sem credencial: 401 com um único desafio `Digest realm="default", algorithm=MD5, qop=auth`; Basic: **403**; Digest: 200. Saúde `/rest/v2/container/health/status` anônima: 200 `HEALTHY` | `a-autenticacao.txt` |
| (b) parada | `POST /rest/v2/server?action=stop` (sem a barra) a isn3: 204 em 13 ms com o usuário `admin` padrão (LIFECYCLE passou); última saúde respondida +3005 ms, primeira recusa +3059 ms; os outros dois `HEALTHY`; contêiner `exited 0`. A recusa chega como desconexão (`RemoteDisconnected`), não como conexão recusada: o encaminhamento de porta do podman aceita o TCP | `b-parada-sem-barra.txt` |
| (c) `rebalancing_enabled` | depois do `disable-rebalancing` global (204, 14 ms), `GET /rest/v2/container` e `GET /rest/v2/caches/{c}` dos 4 caches leem `false` nos 3 nós (coordenador era isn2); depois do `enable`, `true` | `c-rebalancing-enabled.txt` |
| (d) atuador real | `AtuadorRestInfinispan` + `ClienteAdminRest` (Digest real): AC-2 aplicar/reaplicar/reverter/reverter em SYNC e ASYNC, todos `APLICADA` 204, 18 a 182 ms (o primeiro inclui o desafio); AC-5 com a barra em isn3: `APLICADA` 204, `isn3=parou`, os outros `HEALTHY`, 3188,6 ms (prazo 5 s); maioria com 2 nós `HEALTHY` logo depois (saída graciosa; sob corte a saída pelo JGroups não chega) | `d-atuador-ac2-*.txt`, `d-atuador-ac5.txt` |

`t_parada` (do 204 ao fim dos conectores, **com** o atraso de 3 s do `Server.java:208` dentro; V-W2a R3, decisão V-5)
≈ 3,06 s no local. A condição de viabilidade do AC-5 passa a ser `k·W + t_parada < 39,8 s` (menor T_det da VM); a
forma antiga `k·W + 3 s + t_par` contaria os 3 s duas vezes com este número. O
instante da primeira recusa Hot Rod não foi medido com cliente Hot Rod: o Hot Rod e a REST usam o mesmo conector da
porta 11222, e a recusa da saúde é a mesma. A integração I-1..I-5 (item 9) não rodou: depende da ligação do P-38.

### W-2a, segunda rodada: D-C ampliada, esquema 1.2 e ligação (P-36, P-37, P-38) (2026-11-05)

Decisões do coordenador em nome do Gestor (05/11). Pares na ordem do TDD: `db98466` (test) → `4f74c26` (refactor,
retira `AC_1`/`AC_3`) → `18ab8b9` (feat, `reversao` e 1.2); `7ae4c17` (test) → `2bf6cff` (feat, ligação). Vermelho
conferido: depois de `4f74c26`, exatamente os três testes de `reversao`/esquema falham
(`AcaoClusterTiposTest.reversaoNoJsonl`, `ControleArquivosTest.formatoAcao`, `LacoDeControleTest.tDecisaoNs`); o
`7ae4c17` não compila sem o `2bf6cff`.

| Momento | Surefire (testes/falhas/erros/pulados, arquivos) |
|---|---|
| W-2a, primeira rodada (`68dc72e`) | 961/0/0/1, 130 |
| W-2a, segunda rodada (`2bf6cff`) | 971/0/0/1, 133 |

971 = 961 − 1 (`AtuadorRestInfinispanTest.tiposForaDoBracoB`, os tipos não existem mais) + 6
(`controle/ParametrosPoliticaArquivoTest`) + 3 (`session/LigacaoControladorAtuadorTest`) + 2
(`session/RotulosDosNosTest`). `analysis/` não muda (235 OK, 1 *skip*, medido na primeira rodada).

Linhas de testes existentes editadas (D-C ampliada; só rótulo, nenhuma asserção de comportamento retirada):

| Arquivo | Linhas (na base) | O quê |
|---|---|---|
| `controle/ExecutorDeAcoesTest` | 31-33 | AC1/AC2/AC3 = AC-2 em `sessions`, `sessions-async`, `canario` (chaves distintas, todas reversíveis) |
| | 77 | `resumo` imprime `tipo/primeiro cache`, para a ordem LIFO continuar distinguível com um tipo só |
| | 95, 114, 126, 168, 189, 204, 228, 251, 275, 295, 323, 364, 399 | rótulos nas asserções de texto |
| | 217 | `x.tipo() == AC_1` → `x.chave().equals(AC1.chave())` |
| | 274, 394 | comentários |
| | 411, 413-420 | `DisplayName`; `AC_1`/`AC_3` → `AC_2`/`AC_5` (a chave com alvo diferente passa a usar AC-5) |
| `controle/LacoDeControleTest` | 195-198, 293-294, 491-492 | lista de operações com `tipo/primeiro cache` |
| | 423 | `decisoes-controlador/1.2` |
| `controle/ControleArquivosTest` | 133-135 | `reversao` no fim da lista de campos e `null` no AC-2 |
| | 138, 141 | `AC-2`; `valor` `null` |
| `session/ArquiteturaSemAcaoNoClienteTest` | lista do `LigacaoControlador` | + `PoliticaPorAssinatura`, `ParametrosPolitica`, `AtuadorPorTipo` |

Testes do próprio W-2a ajustados: `ExecutorNaoReversivelTest` (resumo novo), `AcaoClusterTiposTest` (`reversao`
presente e `null`, esquema 1.2), `PoliticaPorAssinaturaTest.parametros` (origem obrigatória),
`AtuadorRestInfinispanTest` (sai `tiposForaDoBracoB`).

Ponta a ponta (`LigacaoControladorAtuadorTest.pontaAPonta`, 1,8 s): `--controller active` com o bloco `politica`,
três servidores REST falsos e uma saúde falsa com os mesmos rótulos. A queda do nó 3 leva a R2 e, na janela
seguinte (nó 3 sem resposta em todas as amostras), a P2: o `POST /rest/v2/container?action=disable-rebalancing` vai
ao nó 1 (o primeiro que respondeu na última amostra), a leitura de volta confere. Na volta do nó 3, a política
reverte (`P0:no-voltou`, `enable-rebalancing`). A `meta` traz o atuador `por-tipo(...)`, a política `por-assinatura`
e `parametros_politica`.

I-1..I-5 (item 9) não rodaram: a noite 1 do *rebaseline* começou e o host entrou em H0 (podman parado).

### W-2a, terceira rodada: ressalvas da V-W2a (R1, R3, R7, V-4, V-7, O7) (2026-11-05)

Parecer `docs/validacao-w2a-2026-11-05.md` (`27b50de`), APROVADO COM RESSALVAS; decisões do coordenador em nome do
Gestor (05/11). Escrito com a VM em H0: **nenhum Maven nem Surefire**. Só `javac` isolado sobre os arquivos mudados
(classpath montado à mão a partir do repositório Maven local, `target/classes` e `target/test-classes` da rodada
anterior), para conferir a sintaxe; todos compilam. Os vermelhos e as contagens ficam para a janela H2.

| Commit | O quê |
|---|---|
| `f686643` docs | R1 (`T_enc` por tipo, pior caso 9 s, `timeout -k` com K > 9 s no W-6), R3 (`t_parada` com os 3 s dentro), O7, O12 |
| `31ca8c5` test → `d2e9553` fix | texto do `--dry-run`: `prazo_encerramento=em_curso+pilha+T_cham pior_caso_ns=9000000000` (2 s sem a política) |
| `e5b992f` test → `e7df3dd` feat | `politica.k` → `kLag`; `k_lag` na `meta`; teste novo `kLagIndependenteDoK` |
| `9cb25b2` test → `32bc531` feat | V-7: campo `no` na linha `politica`; teste novo `noDaEscolha` |
| `9bf55f5` test → `883855d` feat | R7: em S3, `tetoLagMs` tem de ser igual a `--hotrod-timeout-ms`; teste novo `tetoLagIgualAoPrazoDaSonda` |
| `e349c68` test | `SmokeAtuador` com `t_pedido_epoch_ns`/`t_retorno_epoch_ns` pelo `EpochClock` |

Esperado na H2: 971 + 3 testes novos (`kLagIndependenteDoK`, `noDaEscolha`, `tetoLagIgualAoPrazoDaSonda`) = **974/0/0/1,
133 arquivos**. Testes do próprio W-2a editados só para acompanhar o nome `kLag`, o campo `no` e o texto do dry-run:
`ParametrosPoliticaArquivoTest`, `PoliticaPorAssinaturaTest.parametros`, `LigacaoControladorAtuadorTest`,
`AcaoClusterTiposTest.linhaPolitica` e `planoDeAcao`. Textos sem efeito em teste: mensagem de
`WorkloadMainCargaControleTest:143` e comentários de `LacoDeControleTest` (46, 437, 450).

### W-2a, janela H2: Surefire, vermelhos, mutantes, tar e integração I-1..I-5 (2026-11-05)

- Surefire na ponta `9695511`: **974/0/0/1, 133 arquivos** (o esperado). Vermelhos da terceira rodada conferidos numa
  worktree temporária (`runs/b44-2/vermelhos-rodada3.txt`): `31ca8c5` e `9bf55f5` falham em execução, `e5b992f` (4 erros)
  e `9cb25b2` (26 erros) não compilam.
- Sete mutantes do item 7 sobre a ponta nova (R5): os sete morrem (`runs/b44-2/mutantes-r5.txt`).
- R4: `runs/b44-2/brutos-tarefa-zero-2026-11-05.tar` (SHA-256 `86de025b…`) e `runs/b44-2/brutos-integracao-2026-11-05.tar.gz`
  (SHA-256 `f8e9795d…`), os dois determinísticos e reproduzidos.
- Integração I-1..I-5 no *cluster* local `w2a`: `docs/relatorio-b44-2-2026-11-05.md`. Problemas novos: P-39 (AC-2 sob
  falha silenciosa), P-40 (AC-2 depois da queda), P-41 (leitura final da carga trava com nó parado), P-42 (AC-5 sob corte
  e nó pausado).

## W-4 (B-26.3): sonda de defasagem S_lag, *caches* `canario*` e a fonte do controlador (2026-11-05)

> Integração (05/11, `feature/controlador-integracao`): os problemas do W-4 eram P-39 e P-40 na branch dele e
> passaram a P-43 e P-44 no registro (o W-2a já tinha P-39..P-42); o texto abaixo usa os números novos e os
> commits citados, os antigos.

Branch `feature/w4-sonda-defasagem` sobre a ponta do W-2a (`9695511`). **Escrito com a VM em H0 (noite 1 do
*rebaseline*): nenhum Maven, Surefire, `javac`, Python, Podman nem script de teste rodou.** Os testes foram
escritos antes do código (`603947d` test, vermelho; `133212d` feat) e só vão ser executados na janela H2.

| Arquivo de teste | Testes | O que prende |
|---|---|---|
| `controle/ParametrosDefasagemTest` | 5 | bloco `defasagem`: sem padrão, origem obrigatória, tipos estritos, histerese; `validarContra` (prazo em W, passo < prazo, `lagIn` ≤ prazo); SHA-256 cobre o bloco; `parametros_defasagem` na meta de observaveis 1.2 |
| `controle/SondaDefasagemResumoTest` | 11 | lag de nó = menor par; nó cortado = suspeito no teto; par da maioria no teto (dono primário do outro lado) não muda o suspeito; corte parcial não chega ao teto; empate → `null`; histerese nas bordas; várias rodadas; janela sem rodada = indeterminada; validação da rodada; linha `defasagem`; funções da agenda |
| `controle/SondaDefasagemRodadaTest` | 12 | canal falso com relógio real: 3 escritas e 6 leituras sem falha, só chaves `lag:<nó>`, `nonce:rodada`; atraso conhecido; nó cortado; leitura bloqueada interrompida no prazo; tarefa presa recusa a rodada seguinte; escrita que falha; buffer por janela (aquecimento, atrasada, fim não recua); agenda alinhada à grade; ouvinte travado não atrasa rodada (P-5); construtor |
| `controle/ArquiteturaDefasagemTest` | 2 | S_lag em `controle/` sem Infinispan, `session`, processo, arquivo nem nome de *cache* |
| `session/CanalCanarioHotRodTest` | 4 | rótulo = `RotulosDosNosTest.rotuloDoNo` = sonda de topologia = atuador; correspondência `--servers` × `--controller-admin`; operação vai ao cliente do nó |
| `session/CanarioArquiteturaTest` | 5 | literal `"canario"` só no resolvedor; `cacheCanario()` só no adaptador; adaptador sem `sessions*`/`counters*`, tipos da carga, processo ou arquivo |
| `session/CachesCanarioJsonTest` | 3 | `cluster/caches/canario*.json` compactos, sem envoltório, iguais aos irmãos do XML salvo `remote-timeout` (17500 só no SYNC); XML sem `canario` |
| `session/LigacaoControladorDefasagemTest` | 5 | carregamento (só S3, prazo em W, `lagIn`, correspondência, R7 continua), dry-run, `pecas` coerente com o bloco, ponta a ponta em shadow (nó 3 cortado → S_lag `ALTA` com `noSuspeito` canônico → P1 com `no` e AC-5 `PREVISTA` para ele) e sem o bloco |
| `scripts/cria-caches.test.sh` | 26 asserts | POST com o corpo do arquivo, leitura de volta nos três nós, divergência (exit 4), recusa (exit 3), arquivo ausente ou não compacto (exit 2), argumentos (exit 1), senha só pela entrada padrão |

**Testes existentes editados (P-43, autorizados pelo coordenador em nome do Gestor em 05/11; commit `bf59e6e`):**

| Teste | Linha | Mudança | Por quê |
|---|---|---|---|
| `session/ArquiteturaSemAcaoNoClienteTest` (R-C1) | `PERMITIDOS` | par novo `CanalCanarioHotRod` → `CanalCanario`; `LigacaoControlador` ganha `CanalCanario`, `ParametrosDefasagem`, `SondaDefasagem` | o adaptador implementa a porta da sonda e a ligação monta a sonda; nenhuma asserção retirada |
| `controle/LacoDeControleTest` | 426 | `"observaveis-controlador/1.1"` → `"1.2"` | o plano manda o custo da sonda em colunas próprias do `observaveis` (W-4 item 3), o que muda o esquema |

Esperado na H2 com as duas edições: 974 (esperado do W-2a, ainda não medido) + 47 = **1021/0/0/1**,
141 arquivos; `cria-caches.test.sh` 26/0. Depois: *smoke* local S3 ASYNC com e sem corte do `isn3` (item 5 do prompt).

### W-4, segunda rodada: decisões do coordenador de 05/11 (P-43, passo de 20 ms, provisórios, P-44) (2026-11-05)

Ainda sem execução (H0). Commits: `bf59e6e` test (as duas edições do P-43, autorizadas), `aadbaa3` e `4ed28a3` test
(vermelhos), `0db755f` feat; `13154c4` test → `5da6767` feat (motivo da escolha).

| Arquivo de teste | Testes | O que prende |
|---|---|---|
| `session/EscolhaDeChaveTest` | 8 | P-44: a chave de cada nó tem o próprio nó como dono primário (busca `lag:<nó>`, `lag:<nó>#<j>`); chaves distintas e determinísticas; sem topologia, nó fora da topologia anunciada ou sem achar até 10 000 candidatas, chave simples com o motivo; `hostPorta`; o canal expõe `chave(i)` e `chaveNoPrimario(i)` |
| `controle/SondaDefasagemChaveTest` | 3 | a sonda escreve e lê `canal.chave(i)`; padrão `lag:<rótulo>`; chave sem prefixo, repetida ou nula recusada |
| `session/LigacaoControladorChavesTest` | 2 | `defasagem_chaves` (`no`, `chave`, `primario`, `motivo`) no `fim` de `observaveis`; `null` sem a sonda |
| `session/ArquivoCalibracaoTest` | 2 | `cluster/controlador/controlador-calibracao-s3.json`: `passoLeituraMs` = 20 com origem `wada_consistency_cloud_2011` §2; `lagInMs`, `lagOutMs` e `kLag` com `provisorio-calibracao`; `tetoLagMs` = `--hotrod-timeout-ms` padrão (R7); custo sob falha prazo/passo = 100 leituras por par |

Esperado na H2: 974 (W-2a, ainda não medido) + 47 + 15 = **1036/0/0/1**, 145 arquivos; `cria-caches.test.sh` 26/0.
O que os testes **não** provam (fica para o *smoke* local): que o `ChannelFactory.getHashAwareServer` do cliente 15.0.0
devolve, para os bytes da chave, o mesmo primário que o servidor 15.0.21 usa; que a leitura de um nó que não é dono vai
primeiro ao primário; e que, sob o corte do `isn3`, os pares entre `isn1` e `isn2` deixam de empatar no teto.

### W-4, terceira rodada: ressalvas da V-W4 (R1, R5, R6) e a primeira execução na H2 (2026-11-05)

Parecer `docs/validacao-w4-2026-11-05.md` (`ff7ab3c`), APROVADO COM RESSALVAS. Commits: `5a69551` test (R1
`ExigirPrimarioTest`, 5 testes, e R6 em `ouvinteLentoNaoAtrasaRodada`), `68691ae` feat (R1 e Javadoc da R5). O vermelho
da R1 foi conferido antes do feat: com o `main` do `5a69551`, só o `ExigirPrimarioTest` deixa de compilar
(`exigirPrimario`/`montar` ausentes).

**Medido na H2** (`mvn -B -o test` em `workload/`, Maven 3.9.16, JDK 22.0.2 com alvo 17; contagens copiadas de
`target/surefire-reports/TEST-*.xml`): **1041/0/0/1, 146 classes**, `BUILD SUCCESS`. A conta: 974 (W-2a) + 62 (W-4,
primeira e segunda rodadas) + 5 (R1) = 1041; 133 + 12 + 1 = 146 classes. Tudo verde na primeira execução.
`bash scripts/cria-caches.test.sh`: **26/0**. Brutos em `runs/w4-h2/` (`surefire-por-classe.tsv`,
`surefire-total.txt`, `cria-caches.test.out`, `comandos.txt`, `MANIFEST.sha256`).

| Classe nova do W-4 | Testes (medido) |
|---|---|
| `ParametrosDefasagemTest` | 5 |
| `SondaDefasagemResumoTest` | 11 |
| `SondaDefasagemRodadaTest` | 12 (3,4 s) |
| `ArquiteturaDefasagemTest` | 2 |
| `SondaDefasagemChaveTest` | 3 |
| `CanalCanarioHotRodTest` | 4 |
| `CanarioArquiteturaTest` | 5 |
| `CachesCanarioJsonTest` | 3 |
| `LigacaoControladorDefasagemTest` | 5 (3,6 s) |
| `EscolhaDeChaveTest` | 8 |
| `LigacaoControladorChavesTest` | 2 |
| `ArquivoCalibracaoTest` | 2 |
| `ExigirPrimarioTest` | 5 |

Falta o *smoke* da seção 9 do parecer (cluster local), que espera o W-2a terminar o I-1..I-5.

## W-5 (B-39.4): `comparar-bracos/2`, guarda G4 e poder (2026-11-05)

Branch `feature/w5-desfechos-coprincipais` sobre `e869a31`. **Escrito sem executar**: o host estava em H0 estrito
(noite 1 do *rebaseline*); só `python -I -m py_compile` em cada arquivo. As contagens abaixo são as esperadas e
precisam ser medidas na janela liberada pelo coordenador (`python -E -P -m unittest discover -s analysis`).

| Suíte (nova) | Testes | O que cobre |
|---|---|---|
| `analysis/test_guarda_energia.py` | 12 | G4 de 28/10: B39-R1 (W, marcadores e fases da energia; convergência não passada ao `energia_proxy`; JSON incompleto) e B39-R2 (`sem-amostra-valida`) |
| `analysis/test_desfechos_coprincipais.py` | 78 | taxa de violações (diferença, não razão), Δ_rec principal (censura e fronteira, instante de observação, sem `end`, sem `t_fim_carga_ns`), Δ_rec da Metodologia, Δ_conv a partir de `t_fim_carga_ns`, divergência, p mínimo 2/2^k por enumeração (k = 5..8), p_L ⇔ limite do IC t, regra IU (quatro casos, direção, não avaliável), Holm entre células, tipos de célula, sensibilidades (a)(b)(c) e Wilson, trilha do controlador e rastreabilidade, energia (P_max 181 W, razão central 0,3, sensibilidade 0,5), CLI |
| `analysis/test_poder.py` | 13 | lote = `permutacao_exata`; poder ≈ 0 com redução 0; 1 com redução total e q = 1; P(k ≥ 6) binomial (0,96 e 0,14 do plano 3.3); α de Holm; CLI |
| `analysis/test_referencia_erro.py` | 9 | Clopper-Pearson unilateral; zero erros = regra de três; guarda de execução com falha; leitura; CLI |

Commits: `0ffb7a4` (test G4) → `87ff066` (fix G4) → `34f119a` (test W-5, com esqueletos de `poder.py` e
`referencia_erro.py` para o vermelho cair por asserção e não por `ImportError`) → `347670b` (feat W-5).

| Momento | `unittest` analysis (sem `-I`) |
|---|---|
| base `e869a31` | 235 OK, 1 *skip* (registro do W-h4) |
| esperado depois do W-5, com o patch autorizado | 347 = 235 + 112 novos, 1 *skip* |

**Testes existentes que dependem do `/1` (não editados; aguardam autorização do coordenador, Prompt 2 item 6).**
Sem o patch, em `analysis/test_comparar_bracos.py`: `C6Determinismo` (5, erro no `setUpClass`: `--primario` saiu e
`--delta-latencia`/`--w-analise-s` são obrigatórios) e `ExclusoesERegistro.test_n_menor_que_1000_e_steal`
(assinatura de `comparar` e `energia_min` sem `parametros`/`janela`, que a guarda B39-R1 recusa) falham;
`C5Pareamento.test_bloco_sem_b`, `C5Pareamento.test_blocos_trocados_de_celula` e os 3 de `Guardas` passam pelo
motivo errado (exit 2 do `argparse`). O patch troca só a chamada (argumentos do `/2`), o *fixture* `energia_min`
(campos `janela` e `parametros` coerentes) e, em `test_esquema_e_familias` e `test_boot_de_blocos_so_depende_de_d_b`,
o nome do esquema, a família `primario` → `co-principal-L`, o conjunto de desfechos e a escala da diferença. As
asserções sobre chaves que não mudaram ficam iguais.

### W-5, correções da V-W5 (R-1..R-11) (2026-11-05)

Parecer `docs/validacao-w5-2026-11-05.md` (`a2e14a8`), decisões P-1..P-7 e A-2..A-4 do coordenador em nome do
Gestor. Par `0380d26` (test) → `c7dfac4` (fix), **escrito sem executar** (H0 estrito). `test_desfechos_coprincipais.py`
passa de 78 para 96 testes e `test_poder.py` de 13 para 18; os testes do próprio W-5 que mudaram de assinatura
(`w_recovery_s`, fim da medição no descritivo do controlador, `--m-celulas`, esquema `poder-troca-de-sinal/2`) foram
ajustados no mesmo `test:`.

| Momento | `unittest` analysis (sem `-I`) |
|---|---|
| base `e869a31` | 235 OK, 1 *skip* |
| esperado com o patch R-1 aplicado | 370 = 235 + 135 novos (12 + 96 + 18 + 9), 1 *skip* |
| **medido** na base `e869a31` (worktree temporária completa, 05/11 08:42Z, janela H2) | **235 OK, 1 *skip*** (46,6 s) |
| **medido** na ponta `6ec46c8` (patch R-1 aplicado, 05/11 08:32Z, janela H2) | **370 OK, 1 *skip*** (255,9 s) |

Comando: `python -E -P -m unittest discover -s analysis`. Mutantes do Prompt 2 e da V-W5 (diferença trocada por
razão, censura esquecida, `t_fim_ns` no Δ_conv, fim em vez de início da operação): os quatro morrem. Detalhes em
`docs/relatorio-b39-4-2026-11-05.md`.

R-1 (patch autorizado de `test_comparar_bracos.py`, a aplicar na janela H2): além das duas correções do Senior
(escala esperada por nome numa lista explícita; secundários sem p exatamente `{delta_rec_s, delta_conv_s,
divergencia_persistente}`), o patch leva `--w-recovery-s 60` em `ARGS_V2` e `w_recovery_s=60` em `ExclusoesERegistro`
(P-5), os dois desfechos `:razao` no conjunto esperado (P-4) e um `escrever_trilha` mínimo nas execuções das células
de teste de `C6Determinismo` e `ExclusoesERegistro` (A-3). Nenhuma linha de asserção sai.

## Integração do controlador (W-2a + W-4 + W-5) e correções P-41, P-42 e D-B (2026-11-05)

Branch `feature/controlador-integracao` (worktree `wt-ctrl-integ`), a partir de `e869a31` (tag `int/2026-11-05-h4`).
Surefire com `mvn -q -T1 clean test` em `workload/` (contado dos `TEST-*.xml`); Python com
`python -E -P -m unittest discover -s analysis` na raiz (Python 3.12.6). JDK 22.0.2, Maven 3.9.16. Sem VM e sem
*cluster* (a VM estava parada, janela H2).

| Passo | Ponta | Tag | Surefire (testes/falhas/erros/pulados, arquivos) | `unittest` analysis |
|---|---|---|---|---|
| W-2a, *fast-forward* | `3930972` | `int/2026-11-05-w2a` | **974/0/0/1**, 133 | — |
| W-4, `merge --no-ff` (P-39/P-40 do W-4 renumerados P-43/P-44) | `c10e7ba` | `int/2026-11-05-w4` | **1041/0/0/1**, 146 | — |
| W-5, `merge --no-ff` | `9b32695` | `int/2026-11-05-w5` | **1041/0/0/1**, 146 | **370 OK, 1 *skip*** |
| correções (`fix/w2a-p41-p42`) | ponta da branch | `int/2026-11-05-fix` | **1059/0/0/1**, 147 | **370 OK, 1 *skip*** |

Conflitos: `docs/registro-de-problemas.md` e `docs/testes.md` no W-4 (numeração paralela de problemas; tabela de
renumeração no commit de merge `c10e7ba`) e `docs/testes.md` no W-5 (seções acrescentadas no mesmo ponto).

### Correções P-41, P-42 e D-B (branch `fix/w2a-p41-p42`)

Decisões do coordenador de 05/11 sobre o relatório do B-44.2. Vermelhos conferidos antes de cada `fix:`/`feat:`
(`runs/w2a-fix/vermelhos.txt`, fora do git): `a47dca7` falha por asserção (21 testes, 4 falhas: `ac5`, `prazoPorTipo`,
`prazoEncerramentoComAc5EmCurso`, `comBloco`); `91c740d` e `d5c58ca` não compilam (`test-compile` exit 1, só nos testes).

| Commit | O quê |
|---|---|
| `a47dca7` test → `3b3056c` fix | P-42: prazo do AC-5 `T_cham` + 15 s (17 s); pior caso do `T_enc` 21 s com o AC-2 na pilha |
| `91c740d` test → `e7d4ed5` feat | D-B: `p2Habilitada` obrigatório; sem P2, nunca AC-2 (`P0`/`p2-desabilitada`); `p2_habilitada` na meta; pior caso 19 s sem P2; arquivo da calibração com `false` |
| `d5c58ca` test → `b61c803` fix | P-41: leitura por réplica do S3 com prazo sem resposta = `--hotrod-timeout-ms`; linha `Leitura por replica:` |

Testes novos (18 = 1059 − 1041):

| Teste | Item | O que prende |
|---|---|---|
| `session/ArquivoCalibracaoTest.kLagViavel` | P-42 | `kLag · W + 13,3 s < 39,8 s` no arquivo da calibração (guarda, verde desde o início: `kLag` = 2) |
| `controle/PoliticaPorAssinaturaTest.p2Desabilitada` | D-B | 15 janelas de queda do isn3 (passa do `T_reb`) e outro nó sem resposta: sempre `P0:p2-desabilitada` com o nó; o P1 e `no-parado-pela-politica` não mudam |
| `controle/PoliticaPorAssinaturaTest.p2DesabilitadaNuncaEmiteAc2` | D-B | 20 sementes × 200 janelas aleatórias (7 topologias, 4 defasagens, NORMAL/CONSERVADOR, janelas sem consulta): nenhum AC-2, nenhum `P2` |
| `controle/ParametrosPoliticaArquivoTest.p2HabilitadaObrigatoria` | D-B | ausente, sem origem, texto, número ou `null` recusados; `true`/`false` lidos |
| `controle/ParametrosPoliticaArquivoTest.metaComP2Habilitada` | D-B | `parametros_politica.p2_habilitada` booleano na meta de `decisoes` |
| `session/LigacaoControladorAtuadorTest.dryRunAvaliacao` | D-B, P-42 | dry-run com `p2Habilitada=false` e `pior_caso_ns=19000000000`; com `true`, 21 s |
| `session/LigacaoControladorAtuadorTest.avaliacaoSemP2PontaAPonta` | D-B | `active` com servidores REST falsos: queda do nó 3 sem nenhum POST, sem linha `acao`, linhas `politica` `P0:p2-desabilitada` com o rótulo do nó 3 |
| `session/ArquivoCalibracaoTest.p2ForaDoBracoB` | D-B | arquivo da calibração com `p2Habilitada = false` e origem com D-B, P-39 e P-40 |
| `session/LeituraReplicaComPrazoTest` (10) | P-41 | prazo a partir da última entrada; interrupção da leitura presa; leitura lenta com progresso não estoura; erro sobe como antes; *thread* daemon; `rodadaUnica` não trava, conta 2 falhas e ainda acha a divergência das lidas; `leitorDaReplica` só em S3; formato da linha; ligação na fonte (`cli.s3.hotrodTimeoutMs`) |

**Testes existentes editados** (números de linha na ponta `9b32695`; só acompanham o prazo novo do P-42 ou o campo
novo da D-B, nenhuma asserção retirada):

| Arquivo | Linhas | Mudança | Item |
|---|---|---|---|
| `controle/AcaoClusterTiposTest` | 17, 40 | Javadoc e `@DisplayName`: `T_cham + 3 s` → `T_cham + 15 s` | P-42 |
| | 45, 46 | `5 * S` → `17 * S`; `3 * S + 7` → `15 * S + 7` | P-42 |
| `controle/ExecutorNaoReversivelTest` | 17, 64, 150 | Javadoc e `@DisplayName`: `+ 3 s` → `+ 15 s` | P-42 |
| | 76, 78 | `5 * S` → `17 * S` | P-42 |
| | 177 | `tCham + (tCham + 3 * S) + tCham` → `... 15 * S ...` | P-42 |
| `session/LigacaoControladorAtuadorTest` | 147, 148 | comentário e `pior_caso_ns=9000000000` → `21000000000` (bloco com P2) | P-42 |
| | 39-43 | `BLOCO` com `"p2Habilitada":true` e a origem (o `pontaAPonta` exercita o AC-2); `BLOCO_AVALIACAO` e o *helper* `cliCom` novos | D-B |
| `controle/ParametrosPoliticaArquivoTest` | 37-41 | `BLOCO_TESTE` com `"p2Habilitada":false` e a origem (`ORIGEM_P2`) | D-B |
| | 67 | lista de chaves da origem com `p2Habilitada`; `assertFalse(p.p2Habilitada())` | D-B |
| | 94-100 | os 7 blocos de `blocoInvalido` com o campo, para cada um falhar pelo motivo dele | D-B |
| | 175, 176 | mapa de origem esperado com `p2Habilitada`; construtor `(2, 2000, 60 * S, 1, false)` | D-B |
| `controle/PoliticaPorAssinaturaTest` | 34, 35 | `P` com `p2Habilitada = true` (Javadoc: o código do AC-2, uso exploratório) | D-B |
| | 204 | `semCota` com `true` | D-B |
| | 316, 319, 332 | `parametros()`: campo em `o`, construtor de 6 campos, bloco sem origem com o campo | D-B |

Usam os *fixtures* editados sem mudança própria: `ParametrosDefasagemTest` (`BLOCO_TESTE`) e
`LigacaoControladorDefasagemTest` (`BLOCO`, P1 de ponta a ponta), os dois verdes.

**O que os testes não provam (fica para o *smoke* local):** que a interrupção solta o iterador real do cliente Hot Rod
15.0 preso no nó parado e que o `close` do `RemoteCacheManager` desse nó não trava (P-41); que, sob corte, o AC-5 sai
`APLICADA` dentro dos 17 s (P-42).
