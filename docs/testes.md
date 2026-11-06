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

## W-2b (P-5): caminho crítico sem disco e sem processo (2026-11-05)

Branch `fix/p5-caminho-critico` (worktree `wt-w2b`), a partir de `17f2bd4` (`feature/controlador-integracao`).
Surefire com `mvn -q -T1 clean test` em `workload/` (contado dos `TEST-*.xml`); Python com
`python -E -P -m unittest discover -s analysis` na raiz. JDK 22.0.2, Maven 3.9.16. Sem VM e sem *cluster* Podman (a VM
estava parada, janela H2). Vermelhos conferidos antes de cada `fix:`: os três primeiros `test:` não compilam
(`test-compile` exit 1, erros só nos arquivos de teste do item); o quarto (`db84d70`) falha por asserção.

| Passo | Commit | Surefire (testes/falhas/erros/pulados, arquivos) |
|---|---|---|
| base | `17f2bd4` | **1059/0/0/1**, 147 |
| trilha JSONL assíncrona (N-8) | `7376a1a` test → `c3ae70c` fix | **1074/0/0/1**, 149 |
| sonda de saúde sem `curl` (N-7) | `28c2fdf` test → `eca637b` fix | **1078/0/0/1**, 150 |
| contenção do `ISPN004007` | `0d42f82` test → `ed7276d` fix | **1086/0/0/1**, 151 |
| gravadora que nunca morre por uma linha | `db84d70` test (vermelho por asserção) → `07b174f` fix | **1087/0/0/1**, 151 |

`unittest discover -s analysis`: **370 OK, 1 *skip*** (igual à base).

Conta: 1087 = 1059 + 16 (trilha, com `gravadoraNaoMorre`) + 4 (sonda: 3 do `ArquiteturaSondaSaudeTest` e o `semConexao`; os outros testes do
`ConsultaSaudeRestTest` foram reescritos, não somados) + 8 (contenção).

Testes novos (28):

| Teste | Item | O que prende |
|---|---|---|
| `controle/TrilhaJsonlAssincronaTest` (11) | N-8 | `escrever` volta em < 200 ms com a gravadora travada e a ordem se mantém; fila cheia conta `transbordadas` sem bloquear e o `fim` entra fora da capacidade com o total; `linhas_perdidas` do `fim` conta o erro de uma linha que ainda estava na fila no pedido e é o último campo; nenhuma linha passa do `fim` (descartadas fora dele, contadas em `linhasPerdidas`); `close` espera a fila; `aguardarGravacao`; `capacidadePara` = 4 × linhas esperadas; gravadora daemon com o nome do arquivo; **o teste do plano**: trilha travada 5 s não atrasa a chamada seguinte do atuador além do prazo do tipo (`T_cham` = 500 ms); 30 *ticks* do laço em < 1 s com as duas gravadoras travadas; a gravadora não morre com `RuntimeException` da saída (conta como erro, a fila segue) |
| `session/LigacaoControladorTrilhaTest` (5) | N-8 | linhas esperadas por arquivo (600 s, W = 5 s, P = 2 s: 662/542/242 e 362; arredondamento para cima); capacidade das duas trilhas na abertura; gancho envolto com o mesmo nome que roda o do executor e depois espera a gravadora no que sobra do `T_enc`; sem sobra não espera; ligação `active` registra e remove o gancho envolto |
| `session/ArquiteturaSondaSaudeTest` (3) | N-7 | R-ARQ-4: `ConsultaSaudeRest` e `ClienteAdminRest` sem `ProcessBuilder`, `Process`, `Runtime`, `curl`, `java.nio.file`, e de `HealthRest` só `interpretar`/`Estado`; R-ARQ-5: `LigacaoControlador` sem processo; detectores |
| `session/ConsultaSaudeRestTest.semConexao` | N-7 | conexão recusada vira `IOException` de classe exata (motivo `falha:IOException`, o mesmo do I-3b) |
| `session/ContencaoLogHotRodTest` (8) | stderr | nó e motivo das mensagens reais do I-3b (`connection=`, `getsockopt: /h:p`, `h/<unresolved>:p`, `TransportException.getServerAddress`, a chave `lag:h:p` da sonda não conta); filtro (só o `ISPN004007`; 1ª inteira, 2ª aviso sem pilha, demais fora; chaves separadas); linha do stdout com `n` e o primeiro e o último instante; `reiniciar`; 8 × 1000 registros concorrentes = 8000 contados, uma inteira e um aviso; filtro anterior encadeado; **caminho real** `Log.HOTROD.exceptionAndNoRetriesLeft` → JBoss Logging → `java.util.logging` → filtro no logger `org.infinispan.HOTROD` (5 chamadas, 2 publicadas); ligação em `executarRepeticao` (instala e zera antes do `try` do `rcm`, imprime no `finally` dele) |

**Testes existentes editados** (números de linha na ponta `ed7276d`):

| Arquivo | Linhas | Mudança | Item |
|---|---|---|---|
| `controle/ControleArquivosTest` | 87 | linha nova `assertTrue(t.aguardarGravacao(5 * S), ...)` antes de `"flush por linha"` (a gravação passou para a gravadora); nenhuma asserção retirada | N-8 |
| `session/ConsultaSaudeRestTest` | 1-273 (o arquivo inteiro; 273 linhas antes e depois) | reescrito: saem `ProcessoFalso`/`LancadorFalso`, entra `saudeComDigest` sobre o `ServidorAdminFalso` do W-2a | N-7 |
| | `comandoSemSenha` → `pedidoDigestSemSenha` | as asserções do comando `curl` (`--digest`, `--fail`, `-K -`, `--max-time`, URL no fim, senha fora dos argumentos, sem `-u`, configuração no stdin e stdin fechado) viram: `GET` no endpoint de saúde, desafio + pedido Digest + pedido com o desafio guardado (3 pedidos), resumo Digest conferido pelo servidor, senha em nenhum cabeçalho, `lancamentos() = 0`, `pedidos() = 2` | N-7 |
| | `maxTime` → `urlDoIndice` | URL do índice pedido e um `GET` autenticado por consulta (`pedidos() = 3`); as asserções de `--max-time` (`1`, `1`, `3`) saem: o prazo é o da sonda no pedido inteiro, provado em `servidorLento` | N-7 |
| | `exitNaoZero` → `codigoNaoSucesso` | `exit 22` vira 503/404/500 → `IOException` de classe exata com o código na mensagem | N-7 |
| | `processoTravado` → `servidorLento` | processo que não termina vira servidor que responde em 5 s; `TimeoutException` em < 1 s com prazo de 100 ms (antes < 1 s também); a asserção `destroyForcibly` sai (não há processo; R-ARQ-4) | N-7 |
| | `interrupcao` | mesmo nome e mesmas asserções (`InterruptedException`, *thread* termina em 1 s); espera o pedido autenticado chegar em vez do processo; a asserção `destroyForcibly` sai | N-7 |
| | `corpoReal`, `corpoInvalido` | mesmas asserções, corpo servido pelo servidor falso | N-7 |
| | `nosERotulos` | construtor público (sem lançador); mesmas asserções | N-7 |
| | T14 (`urlsDoPadraoDaCli`, `espacos`, `invalidos`) | sem mudança | — |
| `session/ConsultaSaudeRestCliTest` | 11, 15-16, 23-24 | `import java.net.URI`; saem os `import static` de `LancadorFalso`/`ProcessoFalso`; Javadoc | N-7 |
| | 41, 43-44 | `semRede` passa uma fábrica de cliente que falha se chamada (antes, um lançador que falhava) | N-7 |
| | 118-129, 131-138 | `saidaJsonl`: três servidores falsos com Digest e um cliente aquecido (desafio guardado), `--servers` com os rótulos deles | N-7 |
| | 164 | rótulo do 1º nó = o do servidor falso (antes `127.0.0.1:12262`) | N-7 |
| | 170-180 | contagem de `GET` autenticados menos os 3 do aquecimento, mesma faixa `3·amostras .. 3·(amostras+1)` (antes, comandos `curl`); novas: nenhum resumo Digest errado, senha em nenhum cabeçalho | N-7 |

Não editados, verdes: os testes da `SondaTopologia` (contrato da `FonteDeSaude` inalterado), `LacoDeControleTest`,
`ExecutorDeAcoesTest`, `LacoDeRepeticoesTest` (o laço de `executar` não mudou) e os detectores de
`executarRepeticao` (`DetectorFonte`, `AuditorLinhaTest`, `InvalidacaoProducaoTest`, `ResetEstadoPosicaoTest`).

**Medições sem *cluster*:**

- *Smoke* do filtro com o cliente Hot Rod real (programa descartável no *scratchpad*, fora do git): 200 `getCache`
  contra a porta fechada `127.0.0.1:12709`, `maxRetries = 1`. Sem a contenção, 200 `ISPN004007` com pilha,
  stderr de 299 569 B; com ela, 1 inteira + 1 aviso, 2 018 B, e a linha
  `Hot Rod ISPN004007: total=200 inteiras=1 contidas=199 chaves=1 | no=127.0.0.1:12709 motivo=AnnotatedConnectException:ConnectException n=200 ...`.
  O chamador viu as 200 falhas nos dois casos.
- Regra reaplicada ao stderr bruto do I-3b (`wt-w4/runs/i3b/*/stderr.log.gz`, só leitura): `ativo-sem-religar`
  9 887 694 B → 18 553 B (6 450 `ISPN004007` em 4 chaves: 6 366 `AnnotatedConnectException`, 76
  `TransportException:ISPN004071`, 6 `SocketTimeoutException` no `127.0.0.1:12613`, 2 sem nó na mensagem);
  `ativo-religar` 12 123 100 B → 28 840 B (6 348 em 6 chaves); `controle-shadow` 1 498 576 B → 1 455 311 B (33
  `ISPN004007`; o resto é `ISPN004005`, P-46).

**O que os testes não provam (fica para o *smoke* local, se o Gestor pedir):** a trilha com disco travado de verdade
(os testes travam um `Writer` falso); a sonda REST nova contra o Infinispan 15.0.21 (o Digest do `ClienteAdminRest`
já passou na tarefa zero e no I-1..I-5 do W-2a, mas não pelo endpoint `/rest/v2/cache-managers/default/health`); o
volume real de stderr de uma execução com corte (só a reaplicação da regra aos brutos do I-3b).

### W-2b, decisões do coordenador em nome do Gestor (05/11, depois do primeiro relatório)

**Decisão 1, registrada como veio:** edições de teste aprovadas, inclusive a retirada das asserções próprias do `curl`
no `ConsultaSaudeRestTest`: o comportamento que elas testavam (argumentos e processo do curl) deixou de existir, e o
R-ARQ-4 cobre a garantia que importa.

| Passo | Commit | Surefire (testes/falhas/erros/pulados, arquivos) |
|---|---|---|
| ponta do primeiro relatório | `7d682dc` | **1087/0/0/1**, 151 |
| fechamento da trilha com prazo (decisão 5) | `1cdc7e5` test → `ff09766` fix | **1090/0/0/1**, 151 |
| contenção do `ISPN004005` (decisão 2, P-46) | `efdb0d5` test → `b30814a` fix | **1094/0/0/1**, 151 |
| `saidaJsonl` com prazo de 400 ms | `44658a7` test | (junto com a linha abaixo) |
| laço de convergência sem `curl` (decisão 3, P-45) | `ee9b563` test → `65165ea` fix | **1101/0/0/1**, 152 |

Vermelhos: `1cdc7e5`, `efdb0d5` e `ee9b563` não compilam (`test-compile` exit 1, só nos testes do item). Conta:
1101 = 1087 + 3 (`fecharComPrazoAbandona`, `fecharNoPrazo`, `encerramentoNaoEsperaODiscoTravado`) + 4
(`chaveDo4005`, `filtro4005`, `linhasResumo`, `caminhoReal4005`) + 6 (`HealthRestHttpTest`) + 2 (`rArq6`,
`convergenciaSemCurl`) − 1 (`HealthRestTest.configCurl`, retirado).

`unittest discover -s analysis`: **370 OK, 1 *skip***.

**Testes existentes editados nesta rodada** (números de linha na ponta `65165ea`):

| Arquivo | Linhas | Mudança | Item |
|---|---|---|---|
| `session/HealthRestTest` | 59-64 → 59-60 | sai o teste `configCurl` (configuração do `curl` no stdin, que deixa de existir com o P-45); fica um comentário apontando para `HealthRestHttpTest.consultaDigest`, que confere que a senha não vai a nenhum cabeçalho. Mesma razão da decisão 1 | P-45 |
| `session/ConsultaSaudeRestCliTest` | 125, 134-136 | `saidaJsonl`: `--periodo-ms 500 --prazo-ms 400 --duracao-s 2` (eram 200/100/1, calibrados para o lançador falso que respondia na hora); com HTTP real no *loopback* a suíte inteira estourou os 100 ms uma vez (`nos_min = 0`, linha 161). Asserções iguais | N-7 |

Testes desta branch ajustados (não existiam na base): `ContencaoLogHotRodTest` (as chamadas do `ISPN004007` passam a
dar o código, `total(Q7)`, `linhaResumo(Q7)`; a ligação confere `linhasResumo()`) e `ArquiteturaSondaSaudeTest`
(`HealthRest` no R-ARQ-4; `rArq6`; `convergenciaSemCurl`).

### W-2b, *smoke* local (decisão 7 do coordenador, janela H2, 05/11, 11:40Z a 11:49Z)

*Cluster* `w2b` (portas 12701-12703), jar da ponta `65165ea` (sha256 `f47870e7…dd3d59`), S3 ASYNC, `--duration 120`,
`--warmup-min-sec 30`, `--convergence` com teto de 120 s, arquivo da calibração, referência inerte e corte do `isn3` por
nftables como no I-3b; corte de origem + 30 s até o fim da carga (sem religar). Brutos em `runs/w2b-smoke/` com
`MANIFEST.sha256`; roteiro em `runs/w2b-smoke/comandos.txt`; harness, analisador (cópia do `analisar_i3b.py`) e
comparador em `runs/w2b-smoke/scripts/`. O I-3b (`wt-w4/runs/i3b/`, `17f2bd4`, `--duration 300`, corte de origem + 60 s
a + 150 s) foi só lido. As execuções do I-3b duram mais e cortam por mais tempo: as contagens não se comparam uma a
uma; o que se compara é o volume por ocorrência e o formato.

| | `ativo-sem-religar` W-2b | `ativo-sem-religar` I-3b | `controle-shadow` W-2b | `controle-shadow` I-3b |
|---|---|---|---|---|
| P1 (s depois do corte) | 13,28 | 13,26 | 13,36 | 13,23 |
| AC-5 | APLICADA, pedido 13,28 s, fim 16,43 s (3,15 s); lido `12703=parou`, os outros `HEALTHY` | APLICADA, 3,23 s | PREVISTA | PREVISTA |
| stderr (bytes) | **24 107** | 9 887 694 | **10 585** | 1 498 576 |
| stderr (linhas; de pilha) | 266; 150 | 124 534; 98 656 | 114; 24 | 8 392; 396 |
| `ISPN004007` contados (inteiras + avisos no stderr) | 1 243 (6 + 5) | 6 450 (todas inteiras) | 21 (2 + 2) | 33 (todas inteiras) |
| `ISPN004005` contados (inteiras + avisos no stderr) | 10 (1 + 1) | 0 | 556 (2 + 2) | 3 897 (todas inteiras) |
| observaveis: linhas, `fim`, `linhas_perdidas` | 95, sim, 0 | 256, sim, 0 | 98, sim, 0 | 255, sim, 0 |
| decisoes: linhas, `fim`, `linhas_perdidas` | 27, sim, 0 | 91, sim, 0 | 29, sim, 0 | 87, sim, 0 |
| `curl_lancados` | 0 | 450 | 0 | 450 |
| `linhas_jsonl_perdidas` (stdout) | 0 | 0 | 0 | 0 |
| fim da carga → fim do controlador | 11,8 ms | 14,9 ms | 13,1 ms | 16,3 ms |
| convergência | 24 rodadas, todas inválidas ("topologia com 2 de 3 servidores"), `persistente=true` | igual | convergiu, 11 rodadas (8 inválidas), 40,1 s | convergiu, 3 rodadas, 20 ms |
| `exit` | 0 | 0 | 0 | 0 |

Leitura:

- **stderr.** O `ISPN004007` com pilha (cerca de 1,5 KB por ocorrência no I-3b) vira uma linha inteira por (nó, motivo)
  e um aviso; o resto sai só na linha `Hot Rod ISPN004007:`. No ativo, 6 chaves (5 do `isn3`, uma do `isn2`), 1 243
  ocorrências, 24 107 B de stderr inteiro; a `TransportException:SocketException`, que na reaplicação aos brutos do
  I-3b saía sem nó, aqui sai com o `isn3` pelo `getServerAddress`. No `shadow`, o `ISPN004005` (P-46) cai de 3 897
  linhas no I-3b para 2 inteiras e 2 avisos (556 contados, `ISPN000306` e `ISPN000476`, nó `desconhecido`); o stderr
  inteiro tem 10 585 B. Os dois braços ficaram na mesma ordem de grandeza (10 a 24 KB), contra 1,5 MB e 9,9 MB no I-3b.
- **Trilha.** Os dois JSONL dos dois braços têm `fim`, `linhas_perdidas = 0` e `linhas_jsonl_perdidas=0`; nenhum
  campo novo; `curl_lancados = 0`. A ordem das linhas é a do contrato (meta, linhas intercaladas, fim).
- **Sonda de saúde HTTP (N-7) contra o 15.0.21:** `falha:IOException` para o `isn3` depois da parada (18 amostras no
  ativo), como com o `curl` no I-3b; nenhum `timeout`.
- **Convergência sem `curl` (P-45):** o laço consultou a saúde pelo HTTP em memória contra o 15.0.21 e gravou os
  mesmos motivos ("topologia com 2 de 3 servidores"); o resumo de convergência tem os mesmos campos. A diferença no
  `shadow` (40,1 s em vez de 20 ms) vem do protocolo: no *smoke* a cura é no fim da carga e a fusão cai dentro do laço;
  no I-3b a cura foi 150 s antes do fim.
- **Encerramento:** fim da carga → fim do controlador em 11,8 e 13,1 ms, com o fechamento das trilhas limitado pelo
  `T_enc` (decisão 5); o disco não travou e nada foi abandonado.

### W-2b, suíte e repetição r2 do *smoke* depois da campanha da VM (05/11, 22:05Z a 22:27Z)

A VM terminou a campanha às 21:56Z; até lá o host ficou sem Maven, testes e Podman (pedido do coordenador). Na ponta
`9ea2059` (o código é o de `65165ea`; só docs e brutos a mais):

- Surefire `mvn -q -T1 clean test`: **1101/0/0/1 em 152 classes**, 0 falhas (22:05Z a 22:09:50Z).
- `python -E -P -m unittest discover -s analysis`: **370 OK, 1 *skip*** (68,4 s).

Repetição r2 do *smoke* (sementes 63002 e 63004, mesmo jar `f47870e7…dd3d59`, mesmo roteiro de
`runs/w2b-smoke/comandos.txt`):

| | `ativo-sem-religar` r1 | `ativo-sem-religar-r2` | `controle-shadow` r1 | `controle-shadow-r2` |
|---|---|---|---|---|
| P1 (s depois do corte) | 13,28 | 12,70 | 13,36 | 12,82 |
| AC-5 | APLICADA, 3,15 s | APLICADA, 3,20 s; lido `12703=parou`, os outros `HEALTHY` | PREVISTA | PREVISTA |
| stderr (bytes; linhas de pilha) | 24 107; 150 | **17 893**; 93 | 10 585; 24 | **10 577**; 24 |
| `ISPN004007` contados (inteiras + avisos) | 1 243 (6 + 5) | 1 315 (4 + 4) | 21 (2 + 2) | 35 (2 + 2) |
| `ISPN004005` contados (inteiras + avisos) | 10 (1 + 1) | 4 (1 + 1) | 556 (2 + 2) | 453 (2 + 2) |
| observaveis / decisoes: linhas, `fim`, `linhas_perdidas` | 95 / 27, sim, 0 | 96 / 29, sim, 0 | 98 / 29, sim, 0 | 102 / 29, sim, 0 |
| `curl_lancados`; `linhas_jsonl_perdidas` | 0; 0 | 0; 0 | 0; 0 | 0; 0 |
| fim da carga → fim do controlador | 11,8 ms | 10,8 ms | 13,1 ms | 13,2 ms |
| convergência | 24 rodadas inválidas ("topologia com 2 de 3 servidores") | igual | convergiu em 40,1 s | convergiu em 40,1 s |
| violações; `exit` | 0; 0 | 0; 0 | 0; 0 | 0; 0 |

A r2 repete a r1: o stderr dos dois braços fica entre 10 e 24 KB, o P1 e o AC-5 saem nos mesmos instantes do I-3b
(12,7 a 13,4 s), as trilhas fecham com `fim` e sem perda, e a convergência pela sonda HTTP grava os mesmos motivos.

Incidente de infraestrutura: às 22:11Z uma primeira tentativa da r2 travou no `podman-compose down`, porque a máquina
Podman do WSL, recém-ligada, aceitava a conexão ssh mas as sessões ficavam presas; `podman machine stop` e `start`
resolveram e a tentativa (que não chegou a subir o *cluster*) foi apagada. Nada a ver com o código.

## W-6 (B-46): orquestrador das campanhas S3, religamento depois do AC-5 e marcadores do F1 (2026-11-05)

Branch `feature/w6-orquestrador-s3`, começada sobre a ponta do W-4 (`a180a6a`) e rebaseada sobre a integração `17f2bd4` (`int/2026-11-05-fix`; os *hashes* de commit abaixo são os de antes do rebase). A primeira parte foi escrita em H0 (noite 1 do
*rebaseline*), sem rodar nada; o coordenador abriu a janela H2 em 05/11 e tudo o que está abaixo foi rodado no *host*,
sem Podman nem VM. Cada bloco tem o commit `test:` (vermelho) antes do `feat:`/`fix:`.

| Suíte | Antes | Depois | Novos | Vermelho conferido |
|---|---|---|---|---|
| Surefire (`mvn -o -T1 test`) | 1036/0/0/1 (esperado do W-4) | **1072/0/0/1** sobre `a180a6a`; **1095/0/0/1** depois do rebase sobre `17f2bd4` (1059 da integração + 36); **1100/0/0/1** com o `convergencia_estado` | 36 (7 classes) | falha de compilação sem o código (`d498053`) |
| `scripts/inject-crash.test.sh` | 118 PASS | **141 PASS, 0 FAIL** | 23 asserts (C16..C21) | 23 FAIL antes do `--marker` (`af5283c`) |
| `scripts/run-rebaseline.test.sh R7 R13 R14` (injetor novo, runner congelado) | — | 19 PASS, 0 FAIL | — | — |
| `scripts/test_campanha_s3_matriz.py` | — | 15 OK | 15 | importação falha (`0617f17`) |
| `scripts/test_campanha_s3_conferir.py` | — | 21 OK | 21 (5 da D-B depois do rebase) | importação falha (`0617f17`) |
| `scripts/test_campanha_s3_h4.py` | — | 11 OK | 11 | importação falha; o caso sem âncora falhou antes do `fix` (`3ad8740`) |
| `python -E -P -m unittest discover -s analysis` | 235 OK, 1 *skip* | **235 OK, 1 *skip*** sobre `a180a6a`; **370 OK, 1 *skip*** sobre `17f2bd4` (o da integração; nada de `analysis` mudou) | 0 | — |
| `scripts/run-campanha-s3.test.sh` (S1..S21) | — | **193 PASS, 0 FAIL** (S1..S21, rodada completa sobre `17f2bd4` com todas as correções) | S1..S21 | S17 falhou antes do `fix` (P-51); S18 antes da D-B |

Classes Java novas: `ReligamentoTest` (11: linha de sinal e a expressão do orquestrador; espera com o instante do fim da
leitura que viu; sonda que falha conta como incompleta; prazo esgotado com a última leitura no prazo; parâmetros;
passo = `P_sonda`; `ControleDaCarga` sem AC-5), `ConvergenciaResumoReligamentoTest` (4: seis campos depois de
`t_fim_controle_ns` com e sem AC-5 e com prazo esgotado; a assinatura de cinco argumentos sem nenhum), `CliReligamentoTest`
(5: `--rejoin-timeout-sec` sem padrão, obrigatório só com `active` + `--convergence`, > 0, fora do `toString`),
`ExecutorAc5AplicadoTest` (6: só APLICADA conta; FALHOU, NAO_EXECUTADA e PREVISTA não; o primeiro fica),
`WorkloadMainReligamentoFonteTest` (3: ordem carga → alvo → sinal → *flush* → convergência; espera antes do laço;
delegação ao executor), `CachesCargaJsonTest` (4) e `ClusterSemCachesXmlTest` (3). Na primeira rodada nenhum teste
existente foi editado: o religamento só acrescenta campos pela assinatura nova do resumo, usada com o controlador.
Depois do I-3b, `ConvergenciaEstadoTest` (5: os três valores de `convergencia_estado` pelo laço real e a posição do
campo nos dois formatos) e as **edições autorizadas pelo coordenador** que só acompanham o campo novo:
`ConvergenciaResumoFimCargaTest` (o campo entra na lista do contrato e passa a ser o último da assinatura antiga, com
`t_fim_controle_ns` em penúltimo) e `ConvergenciaResumoReligamentoTest` (lista `ANTERIORES`). Surefire depois disso:
**1100/0/0/1**.

`run-campanha-s3.test.sh`: shims de `java`, `podman`, `podman-compose`, `sudo`, `nsenter`, `tc`, `ip`, `iptables`, `curl`
(saúde, `container`, disponibilidade, criação e `?action=config`), `systemd-run`, `systemctl`, `flock`, `pgrep`, `df`,
`sleep`, `date` e `timeout` (estes três com o relógio acelerado). O F1 roda o `inject-crash.sh` real; F2 e F4 usam dublês
(`CS_INJ_PART`, `CS_INJ_DEGR`, só no modo de teste). `cria-caches.sh`, `campanha_s3_conferir.py`, `campanha_s3_h4.py`
(sobre o `analysis/h4.py`) e `campanha_s3_matriz.py` rodam de verdade.

| Caso | O que prende |
|---|---|
| S1 | `--dry-run` da calibração e da avaliação: plano, `timeout -k 23s 200s` (T_enc pior caso 21 s + folga 2 s), F2 com dead-man de 110 s, senha mascarada, nada executado; SHA-256 errado sai com 4 |
| S2 | parâmetro sem origem, `boot_id` diferente, *lock* ocupado e braço B sem bloco `politica`: exit 4 antes de qualquer carga |
| S3 | GO da fase e GO de rede com texto exato; GO de rede errado não deixa diretório nem partição |
| S4 | calibração none + F1 + F2 de ponta a ponta: recriação (`down`/`up`) por execução, seis POST de *cache*, camada 3, K1, janela h4 lida da linha do programa, marcadores do F1 (injetor real) e do F2, volta do F1 pela REST, dead-man antes da partição e desarmado, argumentos do programa, resumo, MANIFEST, h4 |
| S5 | bloco A/B com F2 e AC-5 no braço B: `podman start isn3` depois da linha de fim da carga e menos de 30 s (o prazo do religamento) depois dela, o programa vê o nó de volta, `convergencia_estado = convergiu`, a mesma semente em A e B, trilha e linha concordam, `manifesto-bracos.tsv` no formato do `comparar_bracos.py` |
| S6 | braço B com atuador `nulo`, política `nenhuma` ou `param_sha256` diferente: carga parada, `DESCARTADA-INFRA`, exit 6 |
| S7 | TERM no orquestrador com a JVM ignorando SIGTERM: o orquestrador sai com 143 (sem SIGSEGV, P-52) e o SIGKILL só vem depois de K (23 s virtuais); teto estourado vira RECUSADA |
| S8 | P-35: `inject-crash.sh` sai com 0 e o nó não volta pela REST: exit 10 |
| S9 | resto de partição que o `--heal` não tira: exit 6 com o dead-man armado; resto curado: segue |
| S10 | DT-014 antes da carga (abortada, mesmo i e semente) e depois (RECUSADA sem relançar) |
| S11 | arquivo PARAR; retomada com o *bundle* diferente do `progresso.json` recusada |
| S12 | leitura de volta divergente na criação, disponibilidade diferente de `AVAILABLE` e `StartedAt` anterior à recriação: exit 6 |
| S13 | h4: execuções h4a viram `DESCARTADA-INFRA`, 4 seguidas param a fase (exit 10); unit do amostrador inativa recusa no pré-voo |
| S14 | primeiro braço perdido: o segundo vira `PULADA` e o `repor` refaz o bloco inteiro |
| S15 | disco com menos de 1,5 × o volume de uma execução: exit 10; com 1,5 × exatos, segue |
| S16 | F4 com dead-man e marcadores; nenhum `\|\| true` nas linhas que lançam carga, injetor, conferência, h4 ou *caches* |
| S17 | carga que termina sem a âncora antes de 120 s: RECUSADA "sem âncora", sem injeção (P-51) |
| S18 | D-B: `p2Habilitada` diferente de `false` ou sem o bloco `politica` recusa no pré-voo (exit 4); `meta` com `p2_habilitada = true` para a carga (exit 6) |
| S19 | arquivo `NUL` (do `podman machine ssh` no Git Bash) na saída e no diretório da execução: removido, registrado no log, fora dos manifestos |
| S20 | pareamento: bloco com semente diferente, célula diferente ou braço faltando recusa no pré-voo (exit 4) |
| S21 | `convergencia_estado = sem_rodada_valida` vira aviso na checagem, com a execução ACEITA |

### W-6b: ressalvas da V-W6 (B1, B2, S1..S6, M1) (2026-11-05)

Mesma branch, sobre `a34aa36` (adenda da V-W6). Commits `test:` vermelhos antes da implementação: `98149ca` (B1,
S1..S6, M1), `4dec3df` (B2, M1 alinhada ao pré-registro, folga do S22) e `9c8a8b5` (S25). Nada em `workload/` mudou, então o Surefire
do W-6 (1100/0/0/1) vale para a árvore Java desta ponta.

| Suíte | Antes | Depois | Vermelho conferido |
|---|---|---|---|
| `scripts/test_campanha_s3_conferir.py` | 21 OK | **30 OK** (M1: 4, B2: 5) | 6 falhas antes da M1; 11 antes da M1 revista e do B2 |
| `scripts/run-campanha-s3.test.sh` | 193 PASS (S1..S21) | **269 PASS, 0 FAIL** (S1..S25, 2373 s) | S1 com 13 falhas antes da M1/S1; S1 com exit 2 antes do B2 |
| `scripts/test_campanha_s3_matriz.py`, `test_campanha_s3_h4.py` | 15 OK, 11 OK | 15 OK, 11 OK | — |
| `scripts/inject-crash.test.sh`; `analysis` | 141 PASS; 370 OK, 1 *skip* | iguais | — |

| Caso | O que prende |
|---|---|
| S1 (acréscimo) | o `--dry-run` confere o controlador: imprime `controlador conferido: …`; bloco `politica` ausente com braço B, `tetoLagMs` ≠ `hotrod_timeout_ms` e `p2Habilitada = true` saem com 4 sem chamar `java`, `podman`, `sudo`, `curl` nem `systemctl` |
| S2 (acréscimo) | `exec_teto_s` abaixo de duração + `T_enc` + religamento + convergência + período sai com 4 e cita `t_enc_pior_s (21)`; *timer* `tcc-heal-cs-*` sobrando sai com 4 no pré-voo |
| S4 (acréscimo) | `bundle.sha256` com o coletor; `MANIFEST.sha256` de cada execução confere com `sha256sum -c` (`stdout.log` completo) |
| S7 (mudança) | SIGKILL só depois de 2300 ms reais (K = 23 s virtuais na escala 10); teto do S7b no mínimo do M1 (176 s) |
| S22 | B1: F2 e F4 do braço B com o AC-5 no nó da falha (carga de 120 s): dublês saem com 1 e 6, a execução fica ACEITA com o aviso dos contadores não lidos e `injetor_alvo_parado = isn3`, o braço A segue com 0; F2 sem a linha OK do corte: RECUSADA; exit 1 com o nó de pé: RECUSADA; nó parado sem AC-5: recusa do injetor mais religamento do O8, `DESCARTADA-INFRA`, fase segue |
| S23 | S3: braço B sem a linha de fim depois de um AC-5: o orquestrador religa `isn3`, a execução vira `DESCARTADA-INFRA` com o motivo, e a fase sai com 0 (antes, a saúde falhava e a fase saía com 10) |
| S25 | F1 ainda em curso na linha de fim (F1 de +10 a +60 s numa carga de 60 s): o O8 não religa o nó do F1, a execução fica ACEITA, um só `podman start` de `isn2` (o do `inject-crash.sh`) e marcadores `start`/`end` completos. Achado na suíte completa do W-6b (S4 i=1 virou `DESCARTADA-INFRA` depois do P-55) |
| S24 | B2: fase com SYNC e ASYNC: cada carga com a referência do seu perfil, `--dry-run` do programa nos dois perfis, `referencia_sha256` no `execucao.json`, as duas no *bundle*; referências trocadas recusadas no pré-voo e no `--dry-run`; célula SYNC sem `--referencia-sync` recusada; fase só ASYNC aceita só a `--referencia-async`; `--referencia` antiga sai com 2 |

Dublês alterados para ficarem fiéis: o de F2 escreve a linha `OK: <no> nao conecta a nenhum par` e sai com 1, e o de
F4 sai com 6, quando o alvo não está `running` no fim da janela; o de `java` recusa referência de outro perfil
(`LigacaoControlador.java:204`), aceita `java-para-sem-ac5` (para um nó sem entrada na trilha) e
`java-sem-sinal-<controller>` (omite a linha de fim só num braço). Na primeira rodada do S22 com 60 s de carga, o
atraso do disparo no Git Bash (6 a 20 s virtuais) empurrou a janela da F2 para depois do religamento em duas
sub-rodadas; com 120 s a margem é de cerca de 30 s virtuais.

### W-6c: F3 na matriz S3, swap no portão e trilha completa (2026-11-05)

Numeração da integração (06/11): P-54..P-57 da branch do W-6c são P-57..P-60, e os números do W-6 citados aqui já
são os da integração (+2).

Branch `feature/w6c-f3-memoria`, worktree `wt-w6c`, sobre `6f366ac` (ponta de `feature/w6-orquestrador-s3`). Pedido:
F3 como falha da matriz para a célula ASYNC F3 descritiva do B-40 (seção 4.3), swap da VM no portão de cada execução e
trilha completa como condição (B-7 do B-40). Commits `test:` vermelhos antes da implementação: `7b43d48` (F3, swap,
trilha), `d030642` (S26h e ajustes da suíte) e `2b44245` (acréscimos da V-W6b: N1, R3, R4, R7, R8 e B1 no F3) `8fa508b`, `e388c8f`, `6ee7d51` e `16ac98f` (S5, S22, S25 e os casos do F3 sem relógio também com o *host* carregado) e `d2c7241` (P-59: exit 11). Nada em `workload/` mudou: o Surefire do W-6 (1100/0/0/1) vale para a
árvore Java desta ponta, e não rodei Maven, Podman nem *cluster*.

| Suíte | Antes (W-6b) | Depois | Vermelho conferido |
|---|---|---|---|
| `scripts/test_campanha_s3_conferir.py` | 30 OK | **44 OK** (F3: 6; trilha completa: 7; teto de 1010 s do C-6: 1) | 4 falhas e 11 erros antes da implementação; o teste dos 1010 s passa sem mudança de código (o mínimo é 986 s), é conferência pedida |
| `scripts/test_campanha_s3_matriz.py` | 15 OK | **16 OK** (célula ASYNC F3 nas extras; `F3` saiu da lista de inválidas, `F5`, `F3+F4` e `F2F3` entraram) | 1 falha |
| `scripts/test_campanha_s3_h4.py` | 11 OK | 11 OK | — |
| `scripts/run-campanha-s3.test.sh` | 269 PASS (S1..S25) | **suíte completa pendente (interrompida para H0)** na ponta `16ac98f` | S27/S28: 10 PASS e 40 FAIL contra o orquestrador do W-6b; S26h: 1 FAIL contra a primeira versão do W-6c; S1, S22 e S25 da V-W6b: 68 PASS e 16 FAIL (S1: 3; S22: 13; o S25 já passava, a mudança dele é de robustez) contra `f4bcf1f`; S1 e S27 do P-59: 52 PASS e 7 FAIL contra `d2c7241` |
| `scripts/inject-crash.test.sh` | 141 PASS | **141 PASS, 0 FAIL** (injetor sem mudança; rodado como carga da primeira rodada das mutações) (injetor sem mudança; rodado como carga das mutações) | — |

**Estado dos testes: suíte completa pendente (interrompida para H0).** O coordenador parou o *host* para o X-17 e a
calibração (06/11, ~01:20Z) com a suíte da ponta `16ac98f` em andamento (S1 a S16 sem falha até ali); o resultado
parcial foi descartado. A suíte completa roda antes da integração, com o *host* livre. Rodadas por caso que passaram
antes, em pontas anteriores (não valem como a suíte da ponta): S28 24 PASS e 0 FAIL e S26 + S26h + S27 87 PASS e 0 FAIL
(implementação de `f4bcf1f`); S1 25 PASS e 0 FAIL (`aea4bdb`). As rodadas completas de `f2dac17`, `e388c8f` e
`6ee7d51` foram interrompidas ou descartadas: duas por mudança de teste (S22 F4 e S5 ainda dependiam do relógio sob
carga, `6ee7d51` e `16ac98f`), uma pela parada do limite de uso.

**Mutações da V-W6b §2.1 com a suíte sob carga** (R3/R8: o aceite pede que continuem falhando com outra suíte em
paralelo). Rodadas as quatro ao mesmo tempo, cada uma numa cópia `git archive` com uma condição desfeita, mais o
`inject-crash.test.sh` como carga:

| Mutação (V-W6b §2.1) | Casos | Ponta | Em paralelo com | Resultado |
|---|---|---|---|---|
| sem o ramo do B1 em `concluir_injecao` | S22 | `6ee7d51` | m2 e a suíte completa | **32 PASS, 21 FAIL**: F2 e F4 positivos RECUSADA sem `injetor_alvo_parado`, braço A PULADA; nos negativos (`sem-ok`, `sem-start`, `sem-aplicada`) e no nó sem AC-5, `injetor_alvo_parado` vazio |
| sem o prefixo `infra: ` (P-55) | S22, S23 | `6ee7d51` | m1 e a suíte completa | **56 PASS, 3 FAIL**: nó parado sem AC-5 RECUSADA em vez de DESCARTADA-INFRA e sem o motivo `infra:`; S23 com o motivo sem `infra:` |
| sem o desvio do F1 em curso no O8 (S25) | S25 | `e388c8f` | as outras três e a suíte completa | **2 PASS, 4 FAIL**: DESCARTADA-INFRA, `outros_parados` = `isn2`, dois `podman start`, e o log sem "parado pelo F1 ainda em curso" |
| sem o religamento do S3 em `pos_execucao` | S23 | `e388c8f` | as outras três e a suíte completa | **1 PASS, 5 FAIL**: a fase sai com 10, `isn3` fica parado, sem `podman start` |

As mesmas duas últimas também falharam com os testes de `aea4bdb` e `8fa508b` nas rodadas anteriores (com até oito
suítes no *host*). Entre `e388c8f` e a ponta, o S23 e o S25 não mudaram. Uma suíte de controle sem mutação, rodada ao
lado na segunda rodada, falhou uma vez no N1 do S22 (corrigido em `e388c8f` e `6ee7d51`).

| Caso | O que prende |
|---|---|
| S26 | F3 com o `inject-jitter.sh` real (escala 5, amostra a +60 s pelo `CS_NETEM_EFETIVA_MS`, janela de 120 s): bloco A/B ACEITA; marcadores `fault=F3 node=isn2 event=start\|end` gravados pelo orquestrador, `end` depois do `start`; injetor com os números do `campanha-s3/2`; `netem` na raiz na amostra; *dead-man* de `f3_dur_s` + 90 s na unit `tcc-heal-cs-<i>`, com `AccuracySec=1s` e `nsenter -t 1002 -n tc qdisc del dev eth0 root`, armado antes da sonda e do `netem`; GO de rede antes da primeira F3; sem `netem` nem *timer* no fim; `inject-jitter.sh` no *bundle* |
| S26b | `netem` que não entra: RECUSADA "F3 sem netem em isn2 a +2 s do disparo", com `apos_disparo_s = 2` (o valor do protocolo, sem ajuste) |
| S26c | `netem` rejeitado (exit 5) com o nó de pé: fase sai com 6, DESCARTADA-INFRA, *dead-man* desarmado |
| S26d | `netem` que nem o injetor nem o orquestrador tiram: exit 6, `rede_pos` com remoção extra e resíduo, *dead-man* mantido |
| S26e | B1 no F3: AC-5 do braço B para `isn2` sob o `netem`; o injetor real sai com 0; `injetor_alvo_parado = isn2`, ACEITA com o aviso "falha efetiva ate a parada do no pelo AC-5", religamento de `isn2`; braço A ACEITA sem alvo parado (P-57) |
| S26f | TERM no orquestrador com o F3 em curso: exit 143, sem `netem`, sem *timer*, marcadores `start` e `end` (P-58) |
| S26g | pré-voo com `netem` na raiz de `isn1`: exit 4 sem `compose` nem carga; F3 com `campanha-s3/1`: exit 4 no pré-voo e no `--dry-run`; `--dry-run` com os números do *rebaseline* (49/251/300) imprime o plano do F3 com `+2 s` e *dead-man* de 341 s; GO de rede errado antes da primeira F3: exit 5, nenhum `qdisc add` |
| S26h | raiz de `isn2` ocupada por um `prio` de fora só depois da âncora (o K1 passa): exit 6 com "raiz de isn2 ocupada ou ilegivel antes da F3", sem `qdisc add` nem `systemd-run` |
| S27 | sem limite: `swap_usado_kb_k1` = SwapTotal − SwapFree, sem recriação a mais. Com `--swap-limite-kb 1048576` e swap = limite no portão: recriação (três `up` para duas execuções), leituras `[1048576, 256]`, `swap_usado_kb_k1` = 256, ACEITA, a semente da execução usada uma vez, brutos da primeira recriação em `antes-recriacao-swap/`, K1 de novo; swap que continua alto depois da recriação (P-59): exit 11, nenhuma carga, dois `up`, motivo `parada swap: …` no fim do log e em `swap_portao.parada`, sem `checagem.json`, e a retomada com o swap baixo guarda o diretório como `.abortada-<UTC>` e roda o mesmo i, ACEITA; `--swap-limite-kb` 0, −5, `abc` ou 1.5: exit 2; `meminfo` sem Swap com o limite: exit 4 no pré-voo; `--dry-run` mostra as opções |
| S1 (acréscimo) | R7: `--help` imprime o cabeçalho até o `set -euo pipefail`: "Variaveis", "Exit codes" (com o 11 do P-59) e a última linha ("NAO faz git push"), sem código |
| S22 (refeito) | R3: o dublê de `java` para o nó 2 s virtuais depois do `start` da falha (`ac5-apos-start`) e só escreve a linha de fim depois do `end`; F2 com janela de 80 s numa carga de 120 s, F4 com 300 s numa carga de 340 s e o *ping* de 40 s do dublê, que só termina depois da parada (`ac5-previsto`), teto de 900 s; F2 e F4 positivos ACEITA com aviso; N1: `fim_nominal_desde_ancora_s` = `inj_inicio_offset_s` + a duração, braço A sai depois dele, F2 do braço B também, F4 do braço B sai no fim do *ping*, mais de 20 s antes; R4: F2 sem `start`, F4 sem `start` e F4 sem a linha `degradacao aplicada: alvo isn3` são RECUSADA com o motivo; os negativos de antes (sem a linha OK, exit 1 com o nó de pé, nó parado sem AC-5) |
| S5 (mudança) | o AC-5 do dublê vem 2 s depois do `start` da F2 (`ac5-apos-start`), teto de 900 s: sob carga, a dur/2 do relógio do `java` caiu antes do disparo e a guarda de *netns* parou a fase com 6 (O1 da V-W6b) |
| S25 (acréscimo) | R8: `java-espera-parado-isn2` segura a linha de fim até `isn2` sair de `running`; teto de 900 s (com quatro suítes em paralelo o *kill* do F1 passou dos 200 s do teto padrão); confere no log que a linha chegou com o nó do F1 parado |
| S26e (acréscimo) | AC-5 90 s virtuais depois do `start` do F3 (sem relógio), janela de 180 s; N1 também no F3 |
| S28 | trilha completa com `--exigir-trilha-completa`: ACEITA, `trilha_incompleta = false`, `linhas_perdidas = 0`; retomada sem a opção: exit 4 (*bundle* mudou); `linhas_perdidas = 2` e trilha sem `fim`, com e sem a opção: `trilha_incompleta = true` com o motivo no `execucao.json`; sem a opção, ACEITA com aviso; com ela, DESCARTADA-INFRA com motivo `infra:`, fase segue |

Dublês e *shims* novos ou alterados: `tc` guarda o `netem` da raiz por PID (`netem-<pid>` no estado), recusa `add` com a
raiz ocupada, falha no netns de um nó parado (como o `nsenter` real) e tem as *flags* `tc-rejeita-netem`,
`tc-netem-some`, `tc-del-falha` e `tc-prio-apos-ancora`; `podman start` dá netns novo (apaga o `netem-<pid>`);
`podman-compose down` conta as recriações e pode trocar o `meminfo` (`meminfo-apos-down-<n>`); o `meminfo` dos *shims*
é o `/proc/meminfo` do modo de teste; o `fim` da trilha do dublê de `java` traz `linhas_perdidas` (padrão 0,
`linhas-perdidas`), e `java-sem-fim` o omite. Da V-W6b: o dublê de `java` lê o `--marker` e, com `ac5-apos-start`,
para o nó N s depois do `start` (ou do arquivo `falha-iniciada` que os dublês de F2/F4 tocam), e com
`java-espera-parado-<no>` segura a linha de fim; o dublê de F4 escreve a linha `degradacao aplicada: alvo <no>, pares
…, direcao both` (como `inject-netdegrade.sh:748`), faz a verificação por *ping* (`f4-ping-s`, 40 s por padrão) e sai
com 6 e o `end` no fim dela se o alvo parou; com `ac5-previsto` (gravado pelo dublê de `java` quando vai parar o nó),
os dublês de F2 e F4 só olham o alvo depois que ele parou; os dois aceitam `inj-sem-start`, e o de F4,
`inj-sem-aplicada`.

Na primeira rodada do S26 com a escala 20, a amostra do `netem` (+30 s virtuais) caiu antes do `netem` ou depois do fim
da janela: pelos *shims*, o injetor real leva ~3,6 s reais para aplicar o `netem` (dezenas de processos bash), mais de
70 s virtuais. As *flags* `netem-*` do dublê de `tc` eram apagadas pelo `podman-compose down` e contadas no glob
`netem-*`; foram renomeadas. O S26e também mostrou uma corrida da primeira versão: o resíduo dos três nós, conferido no
disparo do F3, demorava tanto pelos *shims* que o AC-5 do dublê parava `isn2` no meio; o disparo agora lê só a raiz do
nó do F3 (o K1 já conferiu os três), e o S26h prende a mensagem nova.

## Integração do W-6 e I-1 (2026-11-05)

Branch `feature/controlador-integracao` (worktree `wt-ctrl-integ`), a partir de `944a36d` (tag `int/2026-11-05-w2b`).
`merge --no-ff` de `feature/w6-orquestrador-s3` (`081e1c4`: W-6, W-6b, V-W6, adenda e V-W6b) em `49baddc`, com a
renumeração TD-014 → TD-015 e P-45..P-53 → P-47..P-55 (tabela no commit de merge e no registro de problemas). Depois,
o I-1 da V-W6 §5.2 (P-56) com TDD: `cb13cd5` test (vermelho), `70996e4` test, `fdcf0ae` fix. O caso do I-1 se
chamava S26 (e S26n) nesta integração; desde a integração do W-6c, que usa o S26 para o F3, é o S29 (e S29n). Surefire com
`mvn -q -o -T1 clean test` em `workload/` (contado dos `TEST-*.xml`), JDK 22.0.2, Maven 3.9.16; Python 3.12.6. Sem VM.

| Suíte | Comando | Resultado na ponta |
|---|---|---|
| Surefire | `mvn -q -o -T1 clean test` (em `workload/`) | **1142/0/0/1 em 160 classes** no merge e na ponta (1101 do W-2b + 41 do W-6; o I-1 não toca `workload/`) |
| `run-campanha-s3.test.sh` (S1..S26) | `bash scripts/run-campanha-s3.test.sh` | 283 asserções. Rodada completa (5803 s, com suítes de outras sessões no *host*): 266 PASS, 17 FAIL, só em S5 (11) e S22 (6). S5 e S22 de novo, sozinhos: **44 PASS, 0 FAIL** (998 s). Ver abaixo |
| `test_campanha_s3_conferir`, `_h4`, `_matriz`; `test_rebaseline_matriz` | `python3 -I -m unittest discover -s scripts -p "<nome>.py"` | 30 OK; 11 OK; 15 OK; 8 OK |
| `analysis` | `python -E -P -m unittest discover -s analysis` | **370 OK, 1 *skip*** (67 s). Com `-I` o Python não carrega o *site* do usuário, onde estão o `numpy` e o `scipy`, e 7 módulos não importam (ambiente, como na base) |
| `inject-crash.test.sh` | `bash scripts/inject-crash.test.sh` | 141 PASS, 0 FAIL (79 s) |
| `inject-partition.test.sh` | idem | 125 PASS, 0 FAIL, 2 SKIP (115 s) |
| `inject-netdegrade.test.sh` | idem | 298 PASS, 0 FAIL, 1 SKIP (605 s) |
| `inject-jitter.test.sh` | idem | 35 PASS, 0 FAIL (10 s) |
| `cria-caches.test.sh` | idem | 26 PASS, 0 FAIL |
| `run-rebaseline.test.sh R7 R13 R14` (runner congelado com o `inject-crash.sh` novo) | idem | 19 PASS, 0 FAIL (356 s) |

**Rodada completa sob carga.** A suíte completa rodou na ponta `fdcf0ae` das 23:28Z às 01:05Z (5803 s, contra 2373 s
no W-6b) com duas ou três suítes do orquestrador de outras sessões rodando no mesmo *host*
(`runs/w6-int-smoke/suite-s3-completa-sob-carga.out`). Os 17 FAIL caíram em dois casos que dependem do relógio do
Git Bash, a fragilidade que a V-W6b registrou como R3 e R8 e que o W-6c vai tirar. No S5, a âncora foi vista 43 s
virtuais depois do lançamento, o AC-5 do dublê parou o `isn3` antes do disparo da F2, e a guarda de *netns* parou
a fase com 6. No S22 (f2 e f4), a carga de 120 s do dublê estourou o teto de 300 s virtuais (exit 124), que o shim
de `timeout` converte em 15 s reais. Nenhum dos dois depende da releitura da `meta`: nos dois o dublê grava a `meta`
antes de imprimir a âncora. Rodados de novo sozinhos, na mesma ponta (01:05Z a 01:22Z), S5 e S22 deram 44 PASS e 0
FAIL (`runs/w6-int-smoke/s5-s22-isolado.out`). Os 14 do S26 passaram na rodada completa.

**S26 (I-1).** Vermelho sobre `49baddc`: 4 PASS, 10 FAIL (`runs/w6-int-smoke/i1-s26-vermelho.out`): a fase saía com 6
nos dois casos, com uma só leitura da `meta`. Com a correção, o primeiro S26 deu 34 PASS e 1 FAIL junto com S6 e S18
(`runs/w6-int-smoke/i1-s6-s18-s26-depois-do-fix.out`). A falha era da asserção: `conta 'podman|kill'` casava também a
linha do shim de `sudo` e contava o F1 de i=1 duas vezes. Ela passou a contar `' podman|kill|'`, como o S25 já fazia
(`70996e4`). O S6 e o S18 (recusa da `meta`, exit 6) continuam verdes.

| Caso | O que prende |
|---|---|
| S26 | o dublê de `java` grava a `meta` 1 s depois da âncora e só depois de uma leitura que saiu com 4 (um `python3` do caso anota o exit de cada `campanha_s3_conferir.py meta`); exit 0, ACEITA, primeira leitura 4 e última 0, carga viva, linha de log "meta da trilha lida na leitura N" |
| S26n | a `meta` não chega na primeira execução (F1 com `inj_offset_s = 3`, antes do fim do prazo de 5 s): carga parada, `DESCARTADA-INFRA` com "meta da trilha ausente ou ilegivel", mais de uma leitura com 4, marcadores vazios (nenhuma falha na carga parada), um só `podman kill` (o F1 de i=1), e a fase segue (exit 0, i=1 ACEITA) |

**Congelados.** SHA-256 na ponta: `run-rebaseline.sh` `d95305fe…`, `inject-netdegrade.sh` `4ebdd8a3…`,
`inject-partition.sh` `59dd2bee…`, `inject-jitter.sh` `7e68962b…`, `cria-caches.sh` `53de0b39…`,
`collect-resources.sh` `d1d3581b…`, `analysis/rebaseline.py` `3005f49a…`, `scripts/rebaseline_matriz.py` `3531916a…`,
`runs/rebaseline/matriz.tsv` `8654900c…`, iguais a `944a36d`; lista do h4 (`amostrador-infra.sh`, `vigia-h4.sh`,
`analysis/h4.py`) `e9c44819…`. O `inject-crash.sh` passa de `5b4d5719…` a `531a98a1…` (B-25.1, `--marker`, autorizado no
W-6). O *rebaseline* continua rodando do `rb-20805b0.tar`, e nenhum *bundle* dele pode ser regerado de `int/*` (V-W6 §2).

### Integração do W-6: *smoke* local de criação dos *caches* e `--dry-run` (05/11, 00:02Z a 00:12Z de 06/11)

Harness em `runs/w6-int-smoke/` (no estilo do `runs/w2b-smoke/`), máquina Podman do WSL, sem VM.

**Criação dos *caches*** (`scripts/smoke-caches.sh`, saída em `caches/`, 00:11:05Z a 00:12:32Z, rc 0). *Cluster*
local `w6i` (portas 12801 a 12803), cópia do `cluster/podman-compose-sem-caches.yml` com nomes e portas próprios, o
`cluster/infinispan-sem-caches.xml` da ponta (`a7b18f2d…`) e o `jgroups-tcp.xml`. Três nós em 16 s. Antes da
criação, cada nó lista só os *caches* internos (`___protobuf_metadata`, `___script_cache`). O
`scripts/cria-caches.sh` da ponta (`53de0b39…`), com os seis JSON e `--conferir` nos três nós, saiu com 0: seis `POST`
com 200 (0,24 a 0,59 s, abaixo do aviso de 8,0 s) e dezoito leituras de volta iguais byte a byte. Uma releitura
independente, no formato da camada 3 do orquestrador (`camada3.tsv`), deu 0 divergências em 39 itens:
`rebalancing_enabled = true` nos três nós e, em cada nó, os seis *caches* `AVAILABLE` e com `?action=config` igual a
`{"<nome>":<arquivo>}`. Depois da criação, cada nó lista os seis (`canario`, `canario-async`, `counters`,
`counters-async`, `sessions`, `sessions-async`) mais os dois internos. Os SYNC voltam com `remote-timeout` 17500, os
ASYNC sem ele, todos com `owners` 2, 64 segmentos e `DENY_READ_WRITES`. Nos *logs* dos servidores não há `ERROR`. Os
`WARN` são `ISPN012026` (*endpoint* REST de saúde obsoleto), `ISPN000599` e os dois da subida do servidor.

**`--dry-run` do orquestrador** (`dry-run/`, rc 0, 3 s). Matriz de avaliação com um bloco `ASYNC-F2` (`matriz.tsv`,
`5ee4b770…`, gerada com `--nucleo "ASYNC-F2:teste:1" --ordem-seed 20261105 --s0 400000 --passo-semente 100 --i0 0 --b0 1`):
i=0 braço B `active` e i=1 braço A `shadow`, os dois com a semente 400100. Parâmetros `campanha-s3/1` com os 19
valores do pré-registro da calibração §4.1 (branch `docs/pre-registro-calibracao-s3`, `2c8b7b8`), provisórios para
este teste. Controlador `cluster/controlador/controlador-calibracao-s3.json` (`0ff8714b…`). Referências provisórias:
`runs/w2b-smoke/config/referencia-S3-async.json` (`05342de4…`, a do *smoke* do W-2b, cópia da do I-3b) e
`runs/b44-2/integracao/referencia-S3-sync.json` (`ec0f812b…`, da integração do W-2a; o *smoke* do W-2b e o I-3b só
tinham a ASYNC). As duas são conferidas (SHA-256 e perfil), mesmo com a fase só ASYNC. Saída: controlador conferido
(`politica` presente, `p2_habilitada = false`, `defasagem`), perfis da fase `async`, duas linhas `PLANO` com
`inject-partition.sh --minority isn3 --duration 90` a +49 s da âncora e *dead-man* de 180 s, `timeout -k 23s 1010s`,
`--controller-reference` com a referência ASYNC nas duas, `--rejoin-timeout-sec` presente, senha mascarada e "nada
executado (sem java, podman, sudo, tty)". O diretório `--saida` não foi criado. Comando em `dry-run/comando.out` e
SHA-256 das entradas em `dry-run/entradas.sha256`.

## Integração do W-6c (2026-11-06)

Branch `feature/controlador-integracao` (worktree `wt-ctrl-integ`), a partir de `1f65a02` (tag `int/2026-11-05-w6`).
`merge --no-ff` de `feature/w6c-f3-memoria` (`66cfca6`, base `6f366ac`) em `494ccce`, com a renumeração P-54..P-57 →
P-57..P-60, itens 21 a 24 do TD-014 → itens 22 a 25 do TD-015, números do W-6 citados pelo W-6c +2, e o caso do I-1
de S26 para S29 (o W-6c usa S26/S26h, S27 e S28). Tabela no commit de merge. Suítes rodadas depois que o *host* saiu do
silêncio (05:00:43Z, fim da calibração na VM), na ponta `494ccce`, uma vez cada. A suíte do orquestrador rodou sem
outra suíte em paralelo. JDK 22.0.2, Maven 3.9.16, Python 3.12.6. Sem VM.

| Suíte | Comando | Resultado |
|---|---|---|
| Surefire | `mvn -q -o -T1 clean test` (em `workload/`) | **1142/0/0/1 em 160 classes** (05:03Z a 05:04Z; o W-6c não toca `workload/`) |
| `run-campanha-s3.test.sh` (S1..S29) | `bash scripts/run-campanha-s3.test.sh` | **431 PASS, 0 FAIL** (05:12:42Z a 06:55:08Z, 6146 s; `runs/w6c-int-smoke/suite-s3.out`), sem nova rodada isolada. S5, S22 e S25, que o W-6c tirou do relógio, passaram de primeira |
| `test_campanha_s3_conferir`, `_h4`, `_matriz`; `test_rebaseline_matriz` | `python3 -I -m unittest discover -s scripts -p "<nome>.py"` | 44 OK; 11 OK; 16 OK; 8 OK |
| `analysis` | `python -E -P -m unittest discover -s analysis` | **370 OK, 1 *skip*** (54 s) |
| `inject-crash.test.sh` | `bash scripts/inject-crash.test.sh` | 141 PASS, 0 FAIL (46 s) |
| `inject-partition.test.sh` | idem | 125 PASS, 0 FAIL, 2 SKIP (105 s) |
| `inject-netdegrade.test.sh` | idem | 298 PASS, 0 FAIL, 1 SKIP (255 s) |
| `inject-jitter.test.sh` | idem | 35 PASS, 0 FAIL (2 s) |

**Congelados**, iguais a `1f65a02` e a `66cfca6`: `run-rebaseline.sh` `d95305fe…`, `inject-netdegrade.sh` `4ebdd8a3…`,
`inject-partition.sh` `59dd2bee…`, `inject-crash.sh` `531a98a1…`, `inject-jitter.sh` `7e68962b…`, lista do h4
(`amostrador-infra.sh` `e4cc5c79…`, `vigia-h4.sh` `1e7462f4…`, `analysis/h4.py` `53d7e7cb…`), `cria-caches.sh`,
`collect-resources.sh`, `analysis/rebaseline.py`, `scripts/rebaseline_matriz.py` e `runs/rebaseline/matriz.tsv`.

**`--dry-run` com F3 e F4 no braço B** (`runs/w6c-int-smoke/dry-run/`, rc 0, 2 s). Matriz de avaliação gerada com
`--nucleo "ASYNC-none:descritiva:1" --extras "ASYNC-F4:teste:1,ASYNC-F3:descritiva:1" --ordem-seed 20261106 --s0 400000
--passo-semente 100 --i0 0 --b0 1` (`ac9a745e…`). A fase `extra` tem quatro linhas, porque o pareamento exige A e B
por bloco: F4 A e B (semente 400200) e F3 A e B (semente 400300). Parâmetros no esquema `campanha-s3/2`, com 24
números: os 19 do pré-registro da calibração §4.1 e os cinco do F3 do B-40 §4.3 (`isn2`, 20 ms, 13 ms, `normal`,
251 s), todos provisórios para este teste. Controlador `controlador-calibracao-s3.json`. Referências provisórias, as
mesmas do *dry-run* da integração do W-6 (`runs/w2b-smoke/config/referencia-S3-async.json` e
`runs/b44-2/integracao/referencia-S3-sync.json`). Com `--swap-limite-kb 1048576`, a saída traz
`opcoes: swap_limite_kb=1048576 exigir_trilha_completa=nao`. As linhas `PLANO` do F4 têm
`inject-netdegrade.sh --target isn3 --severity N3 --direction out --duration 90` e *dead-man* de 180 s. As do F3 têm
`inject-jitter.sh --node isn2 --iface eth0 --delay 20ms --jitter 13ms --distribution normal --duration 251` a +49 s,
marcadores gravados pelo orquestrador, `netem` conferido a +2 s e *dead-man* de 341 s. Todas têm `timeout -k 23s 1010s`.
A saída termina com "nada executado (sem java, podman, sudo, tty)", e o diretório `--saida` não foi criado.

## W-10: sonda de reuso de sessão depois do *logout* (*replay*) na carga S3 (2026-11-06)

Branch `feature/s3-sonda-replay` (worktree `wt-w10`), a partir de `494ccce` (merge do W-6c em
`feature/controlador-integracao`). TDD: o golden da base foi gerado e commitado antes de qualquer mudança
(`c96981c`); os testes da sonda foram escritos antes do código (falha de compilação sem `SondaReplay`). JDK 22.0.2,
Maven 3.9.16, Python 3.12.6, no *host*; o *smoke* na máquina Podman do WSL, sem VM.

| Suíte | Comando | Resultado |
|---|---|---|
| Surefire | `mvn -o test` (em `workload/`) | **1165/0/0/1 em 165 classes** (base: 1142 em 160; +23 em 5 classes novas). O *skip* é o de sempre (`SanidadeRegressaoJunhoTest`) |
| `analysis` | `python -E -P -m unittest discover -s analysis` | **374 OK, 1 *skip*** (370 da base + 4) |
| `test_campanha_s3_*` | `python -I -m unittest discover -s scripts -p "test_campanha_s3_*.py"` | 71 OK (sem mudança) |
| `run-campanha-s3.test.sh` S1, S27, S30 | `bash scripts/run-campanha-s3.test.sh S30 S27 S1` | **70 PASS, 0 FAIL** (870 s, com a suíte de outra sessão rodando no *host*; `runs/w10-smoke/orquestrador-s1-s27-s30.out`). O S30 foi escrito junto com a opção e não foi visto vermelho |

Classes novas:

| Classe | Testes | O que prende |
|---|---|---|
| `S3SondaDesligadaRegressaoTest` | 1 | sonda desligada: ações S3 (instantes do relógio falso), eventos (operação, réplica, código, chave), violações, contadores e cabeçalhos dos CSV iguais ao golden `golden-w10-494ccce/s3-sem-sonda-semente7.txt`, gerado com as classes da base (240 s, nó 2 fora do ar de 100 a 160 s) |
| `SondaReplayTest` | 10 | replay imediato no nó `k+1` com atraso em [1, 3) s vê o backup `VALID` da maioria (dublê `MundoS3Particao`) e registra I1 com `origem = sonda` e a mensagem de sempre; um evento `ReplayValidate` e nenhum `Validate`; alvo `(k+1) mod 3`; atraso do gerador dado e dentro de [min, max); nada ao controlador (eventos e violações), à latência, às ações S3 nem ao mapa de confirmações; coluna `origem` no CSV; replay tardio depois da fusão vê a perda; só *logouts* das fases `durante*`/`recuperacao`, amostra semeada acima do limite, nada sem marcador `start`; nó indisponível pulado (nunca o `k`); imediatos pendentes descartados e contados; janela de medição (imediato respeita, tardio entra sempre) |
| `S3SondaSimulacaoTest` | 6 | desligada não cria ator; ligada cria um por nó, depois dos da carga; ligada não muda a sequência das ações nem os eventos da carga (mesma semente); um replay imediato por *logout* confirmado, no nó `k+1`, entre 1 e 3 s; determinismo da sequência da sonda com partição (mesma semente igual, outra semente diferente); na partição a sonda vê as duas formas de I1 (*logout* do isolado perdido na fusão; cópia velha no isolado) e mais sids que a carga sozinha |
| `WorkloadMainSondaFonteTest` | 2 | na fonte: replay tardio depois de `rodarEmThreads`, antes do CSV de ações e ainda dentro do *try* dos clientes S3; coluna `origem` só com `--scenario S3` e `--s3-replay-probe` (escrito depois do código) |
| `CliSondaReplayTest` | 4 | padrões (desligada, 1 s, 3 s, 200), opções, validação (mínimo > 0, máximo ≥ mínimo, limite ≥ 0, só `pinned`), semente `mix64(sementeAtor("U", i) ^ "SONDAREP")` e o gerador do usuário inalterado |

Dublê novo: `MundoS3Particao` (três servidores de aplicação sobre um *grid* com cópia da maioria e da minoria na
janela de partição; o nó isolado lê e escreve só a dele; na fusão a maioria vence nas chaves comuns e as só da minoria
entram, como `PREFERRED_NON_NULL` com a maioria preferida).

Python: `test_referencia_p99.R2bInstrumentacao` (2), `test_energia_proxy.EdbInstrumentacao` (1) e
`test_comparar_bracos.ExclusoesERegistro.test_replay_validate_fora_dos_desfechos` (1). Orquestrador: caso S30 do
`run-campanha-s3.test.sh`.

*Smoke* local: ver o relatório (`docs/relatorio-w10-2026-11-06.md`, seção 5) e `runs/w10-smoke/`.

## P-48: topologia do `rcm` principal antes das leituras por réplica (06/11)

Branch `fix/p48-topologia-convergencia`, sobre `2c44aa0` (o registro do smoke do P-48, *fast-forward* de `494ccce`).
Commits: `ce5583f` (`test:`, vermelho por compilação sem `AtualizacaoTopologia`) e `3c57447` (`fix:`).

| Suíte | Resultado |
|---|---|
| Surefire (`cd workload && mvn -o -q -T1 test`) | **1152 testes, 0 falhas, 0 erros, 1 *skip*** (162 classes), com 10 novos; a primeira rodada pegou o guarda `NomesDeCacheSoNoResolvedorTest.chamadasUsamOPerfil` (o parâmetro do adaptador passou a se chamar `nomeCache`, o nome aceito pelo guarda) |
| `AtualizacaoTopologiaTest` (8) | toca cada *cache* na ordem com a chave fora do modelo; prazo, falha e interrupção viram `IllegalStateException` (cache e P-48 na mensagem; os seguintes não são tocados; interrupção preservada); linha de diagnóstico só quando o `topology_id` muda; formato da linha; parâmetros; chave e prazo |
| `WorkloadMainP48FonteTest` (2) | a sonda do laço embrulha a de antes com `atualizacao.atualizar()`; o religamento usa a de antes; rodada única e varredura atualizam antes de ler, com a falha como aviso |

*Smoke* no *cluster* local (`runs/p48-fix-smoke/`, mesmo *cluster* e mesma sonda do `runs/p48-smoke/`):

| Parte | O quê | Resultado |
|---|---|---|
| antes (`runs/p48-smoke/`, 1f65a02) | leituras por réplica com a topologia velha depois de um F1 | cada nó perde 174 a 441 das suas chaves (13 a 33 %) e acrescenta 245 a 399 alheias |
| A (jar da correção) | a mesma sonda: antes e depois de `AtualizacaoTopologia.atualizar()` | antes, 12,0 a 22,8 % perdidas e 12,3 a 23,2 % acrescentadas; **depois, 0** nos 3 nós e nos 2 *caches*, e a topologia do principal igual à de um cliente novo |
| B (programa com a correção) | S3 ASYNC 90 s, `--convergence`, F1 do `isn2` no meio da carga | as duas linhas `Topologia do principal` (ids trocados) na primeira rodada; o laço achou 1 chave divergente persistente (I5) em 24 rodadas: `divergente` |
| C (programa sem a correção, 1f65a02) | o mesmo cenário, com a sonda de divergência ao lado | o laço convergiu; 0 chaves divergentes nos mesmos dados com a topologia velha e com a atualizada (sem divergência nesse sorteio: a leitura velha não fabrica divergência) |

## W-9: derivação da calibração S3 (`analysis/calibracao_s3.py`) (2026-11-05)

Na integração (06/11, depois do W-10 e do P-48), o TD-016 do W-9 virou TD-017 (o W-10 entrou antes com o TD-016).

Branch `feature/w9-calibracao-s3` (worktree `wt-w9`) sobre `17f2bd4`. Só Python (sem Maven, sem Podman, sem VM).
Python 3.12.10. A suíte nova usa execuções sintéticas no formato do orquestrador S3 (`exec-NNNN-<célula>-A` com
`execucao.json`, `checagem.json`, `rep-001.csv`, JSONL 1.2 de observáveis e decisões, violações, ações S3, resumo de
convergência e marcadores), escritas pelo gerador do próprio teste.

| Suíte (nova) | Testes | O que cobre |
|---|---|---|
| `analysis/test_calibracao_s3.py`, `CampanhaBase` | 23 | uma derivação completa: execuções usadas e excluídas, 7.1 a 7.18 com os valores esperados calculados à mão (W = 5 s, θ_lat_in = nextafter(0,03), m = 5, kLag = 2 com N = 88 e 3/N, T_estavel = 34 s, T_hold_viol = 26,5 s, teto 600 s, exec_teto 1010 s, q̂ = 1/2 com Clopper-Pearson), só braço A |
| `Bordas` | 11 | `kLag` acima do limite (6 com W = 5: AC-5 inviável), `kLag` = 5 no limite, zero ocorrências (`kLag` = 1), nenhuma janela válida (`kLag` nulo, N = 0), F1 sem resposta longa (12 janelas, não conta), F1 com resposta (conta), λ₅ no posto (3 janelas baixas mudam W de 5 para 7), T_hold_viol = W, teto sem execução convergida, célula vazia, regra sem execução das suas células |
| `Funcoes` | 14 | funções puras de cada regra com as bordas (nenhuma janela válida, sem recaída, sem execução), `resumo_lag` com empate, `calcular_w` (termo de leitura dominante; W = prazo recusado pelo código), viabilidade, `exec_teto` (piso de 1010 s), Clopper-Pearson nos extremos |
| `Cli` | 5 | saída determinística (duas execuções, mesmos bytes), nunca sobrescreve, referências gravadas com o SHA-256 do JSON, braço B recusado (exit 2), F3 recusada, `teto_ms` diferente do prazo recusado |
| `Paridade` | 2 | Δ_conv e taxa de violações por mil iguais ao `comparar_bracos.desfechos_execucao`; Clopper-Pearson igual ao `scipy.stats.beta`. Pulados em `-I` (sem numpy) |

| Commit | Momento | `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"` |
|---|---|---|
| `d4c6f09` test | vermelho contra o esqueleto | caiu também por um defeito do *fixture* (`dict(**{10: ...})`, `TypeError` no `setUpClass`) |
| `7678318` test | *fixture* corrigido, vermelho limpo | 53 testes: 17 falhas, 34 erros (`KeyError` dos parâmetros ausentes), 2 pulados |
| `bd6446d` feat | implementação | 53: 51 OK, 2 pulados; em `python3 -E -P`, 53 OK |
| `8563ce2` test | regra sem execução das suas células | 55: 2 falhas |
| `d329e21` fix | correção | **55: 53 OK, 2 pulados** (8,1 s); em `python3 -E -P`, **55 OK** (8,4 s) |

Teste existente editado: `test_esquema_e_execucoes_usadas` passou a esperar também a execução abortada na lista de
excluídas (decisão da implementação, como o `resumo.json` do orquestrador; registrado no `feat:`). Nenhum módulo
existente de `analysis/` mudou; a suíte completa de `analysis/` não foi rodada para não pôr carga no *host* com a VM
medindo (os arquivos novos não são importados por nenhum outro).

**Mutantes** (cada um aplicado ao módulo e desfeito; `python3 -I`): sem o predicado do P1 na 7.13 (9 falhas); θ_in
igual ao maior mínimo, sem `nextafter` (5); trecho calmo seguido de intermediária contando para m (1); T_estavel sem
`P_sonda` (2); I2 de qualquer classe na 7.15 (2); persistência que não zera abaixo do teto (9); atraso de início
ignorado na 7.3 (2): os sete morrem. O `<` → `<=` no limiar de 400 s da 7.10 sobrevive e é equivalente (em 400 s as
duas leituras dão 600 s).

***Smoke* com brutos reais** (fora do git): os arquivos da execução `controle-shadow` do I-3b
(`wt-w4/runs/i3b/controle-shadow`, S3 ASYNC em shadow com S_lag) num diretório no formato do orquestrador, como
ASYNC sem falha. A leitura dos formatos reais passou (CSV com `phase`, JSONL 1.2, violações com mensagem entre aspas,
resumo sem `convergencia_estado`) e a CLI gravou o JSON e a referência. Os valores não servem para nada além disso:
a execução teve corte do isn3 e era local.

**O que os testes não provam:** que as leituras adotadas nas ambiguidades A-1 a A-17 são as pretendidas (decisão do
Senior/Gestor); o comportamento com os volumes reais da campanha (26 execuções de 300 s).

### W-9, complemento 1 da calibração (DT-022) (2026-11-05)

Par `d0dcce9` (test, vermelho: 61 testes, 8 falhas, 11 erros) → `a3c34d8` (fix). Depois do vermelho, dois ajustes de
teste registrados no `fix:`: `test_sem_execucao_nao_deriva` na assinatura nova de `m_reentrada`; e, na reserva em
que a latência perde toda a cobertura, θ_lat_in, m e T_cooldown não derivam (declarado). Um caso novo de reserva com
cobertura parcial (4 %) confere T_cooldown = m × W final.

| Comando (ponta `a3c34d8`) | Resultado |
|---|---|
| `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 62, 60 OK e 2 pulados (9,4 s) |
| `python3 -E -P -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 62 OK |

Testes novos ou mudados: W_min estrito (atraso de 1 s exato dá 4), reserva de W pura (viável, reserva, inviável,
W ≥ 27 s, `kLag` não derivado), reserva de ponta a ponta nos três casos (viável na base; `test_reserva_de_w_aplicada`,
de 5 para 4 s; `test_ac5_inviavel_sem_w_viavel`, com W' = 5, 4 e 3), janela sem rodada pulada no `kLag` (pura e com
W = 4 s), m pelo trecho máximo sem alto (o que começa antes do `end` não conta), θ_out pelo menor valor (indeterminada
quebra o trecho), ciclo completo, `n_fora`, comando do `poder.py` e as leituras com a situação no complemento.
Mutantes (cada um desfeito): W_min com teto, reserva desligada, m contando trecho que começa antes do `end`, `kLag`
sem pular a janela sem rodada e θ_out pelo maior candidato: os cinco morrem. O *smoke* com os brutos do I-3b roda a
reserva de W' = 19 a 3 s em 0,3 s.

### W-9b: ajustes da V-W9 (R-1, R-2, R-3, R-5) (2026-11-05)

Parecer `docs/validacao-w9-2026-11-05.md` (`8d6ebfc`), ajustes aprovados pelo coordenador; o Senior escreve o
complemento 3 (DT-024) com o mesmo conteúdo. Um `test:` vermelho antes de cada `fix:`.

| Par | Vermelho | O quê |
|---|---|---|
| `10d4d96` → `bbf1061` | 62 testes: 2 falhas, 1 erro | R-1: `viavel_em` = `max(k, kLag)·W + 13,3 < 39,8` na reserva, no limite da 7.13 e no `k_max_no_limite`; R-5: `viabilidade()` sem uso saiu |
| `e01de1f` → `690bcc6` | 67 testes: 6 erros | R-2: latência ou erro sem janela determinada → θ_in = 2,0, θ_out = 1, m e `T_cooldown` derivados; `valores_b40_parte2.carga` com o espelho dos guardas do Java (`GUARDAS_JAVA`) |
| `0f0c7c9` → `05ca007` | 67 testes: 1 falha | R-3: trecho do m que atravessa o `end` cortado no `end`; `trechos_cortados_no_end` |
| `6786244` (test) | — | o teste que lê os guardas no código Java é pulado quando `analysis/` roda fora da árvore (o *harness* de mutações copia só `analysis/`); no repositório ele roda |

| Comando (ponta `6786244`) | Resultado |
|---|---|
| `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 67, 65 OK e 2 pulados (paridade) |
| `python3 -E -P -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 67 OK |

Testes editados: `test_reserva_w` (kLag = 1 vai a W = 13, não 26; W = 14 inviável), `test_klag_puro` (W = 14 sem
kLag que caiba), `test_viabilidade` trocado por `test_viavel_em`, `test_reserva_de_w_aplicada` (θ_lat_in e θ_err_in
"sem cobertura", m e `T_cooldown` derivados no lugar de nulos), `test_m_reentrada` (`NNNNHNH` com o `end` em 2 dá
m = 3, não 2). Novos: carga dos valores do B-40 na base e na reserva sem cobertura (inclusive tipos inteiros do JSON
gravado), `test_problemas_de_carga` (17 valores ruins recusados), `test_guardas_lidos_do_codigo_java`,
`test_nem_sempre_sem_cobertura`. O *smoke* com os brutos do I-3b (sem célula com falha) dá `carga.carrega_no_controlador`
falso, com os campos que faltam (m e θ_out): a conferência acusa o que não carregaria.

Mutações (cada uma numa cópia de `analysis/` fora do repositório, `python3 -I`). Do validador
(`scratchpad/vw9/mutar.py`, 18 mutações): 16 aplicáveis, 14 mortas e as 2 equivalentes do parecer vivas (7.13-b, sem
exigir resposta do suspeito; R-c, limite com `<=`); as duas do m (7.8-a e 7.8-b) não acham mais o trecho, que a R-3
mudou, e entram com o trecho novo no conjunto do W-9b. Do W-9b (10 mutações): `viavel_em` sem o `max`, reserva e
limite da 7.13 com `_cabe(kLag, W)`, sem cobertura desligado, θ_out sem cobertura igual ao θ_in, carga sem a
histerese, carga aceitando inteiro como *float*, m sem o corte no `end`, m com o trecho inteiro e m contando trecho
que chega ao fim: as dez morrem. Antes do `6786244`, o teste dos guardas Java dava erro na cópia e matava todo
mutante; a contagem acima é a de depois.

### W-9c: adendo da V-W9 e complemento 3a (2026-11-06)

Adendo `docs/validacao-w9-2026-11-05.md` §8 (`330c321`) e complemento 3a (`639966c`). Um `test:` vermelho antes de cada
`fix:`:

| Par | Vermelho | O quê |
|---|---|---|
| `83b952b` → `6c0fa8e` | 68: 1 falha | θ_in sem cobertura = `math.nextafter(1.0, inf)` (complemento 3 §2, 3a §3) |
| `da91d1b` → `da4b7f2` | 69: 3 erros | sem cobertura sempre que a 7.7 tem N = 0 (3a §2), por `limiar_entrada_com_cobertura`; `janelas_determinadas` e `fracao_determinada` |
| `0629f62` → `0a65637` | 69: 3 erros | R-4: `W_s.dados.subestimacao_w_menor_que_5_s` com o fator 5/W, o caso e os parâmetros afetados |
| `b5ccc41` → `aa81e38` | 70: 1 falha | texto do m (cortado no `end`), `EMENDAS_VW9` com R-4 e 3a, `pre_registro` com os complementos 3 e 3a |

Ponta `aa81e38`: `python3 -I` **PASS** (70: 68 OK, 2 pulados); `python3 -E -P` **PASS** (70 OK). Três mutantes (θ sem
cobertura = 2,0, sem cobertura só com zero janela determinada, R-4 nunca declarada) são pegos.

### W-9 com a sonda de replay do W-10 (2026-11-06)

`--s3-replay-probe` (W-10, `feature/s3-sonda-replay`) grava eventos `ReplayValidate` no `rep-001.csv` e a coluna
`origem` no CSV de violações (contrato CSV v2 §7). Par `d48370a` (test, vermelho: 73 testes, 2 falhas) → `e561580` e
`e895313` (fix):

| Commit | O quê |
|---|---|
| `e561580` | traz de `feature/s3-sonda-replay` o filtro `OPERACOES_INSTRUMENTACAO` de `referencia_p99.ler_eventos` e `energia_proxy.ler_eventos_inicio`, com os testes deles (mudança idêntica à `6307230` do W-10, sem o teste do `comparar_bracos`). O W-9 lê eventos só por esses dois leitores: referência de p99, λ₅, excedências e denominadores de latência e erro |
| `e895313` | violação com `origem = sonda` fora da janela de violação (m) e do T_hold_viol, porque o `InvariantAuditor` não a entrega ao laço de controle; o q̂ continua contando, como o `comparar_bracos` (decisão do Gestor pendente no W-10) |

| Comando (ponta `e895313`) | Resultado |
|---|---|
| `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 73, 71 OK e 2 pulados |
| `python3 -E -P`, mesmo comando | **PASS**: 73 OK |
| `test_referencia_p99.py` e `test_energia_proxy.py`, em `-I` e `-E -P` | **PASS** |

`SondaReplay.test_replay_validate_nao_muda_nenhum_valor`: a campanha base com `ReplayValidate` em todas as execuções
(a cada 200 ms, lentos, com erro, e um replay tardio depois da carga) dá os mesmos `valor` e `incerteza` de todos os
parâmetros que sem eles, fora o SHA-256 dos CSV na referência e o volume. A derivação oficial da calibração
(`derivacao-20261106T050404Z`) refeita com este código dá os mesmos parâmetros e valores do B-40 parte 2; as
referências mudam só no `gerado_por`, porque o SHA-256 do `referencia_p99.py` mudou com o filtro.

### W-9 com V por pares distintos (regra do W-5c) (2026-11-06)

Par `5d21be3` (test, vermelho: 79 testes, 3 falhas, 1 erro) → `f799c52` (fix). O q̂ e as taxas da 7.18 saem de
`analysis/apuracao_v.py`, cópia literal sem numpy (extraída por `ast.get_source_segment`) das 13 funções e 26 constantes
de apuração de V do `comparar_bracos.py` do W-5c em `bc9cfdd` (com a W-5d). Testes: `python3 -I` **PASS** (79: 76 OK, 3
pulados); `python3 -E -P` **PASS** (79: 78 OK, 1 pulado: a identidade de fonte, que só roda quando o `comparar_bracos`
desta árvore tiver a apuração do W-5c). Conferência única contra o `comparar_bracos` do `wt-w5c` (`bc9cfdd`, `-E -P`):
fonte das 13 funções e valor das 26 constantes iguais; pares, linhas e taxa iguais em três execuções com sonda,
varredura e linhas repetidas. A derivação oficial da calibração refeita dá o mesmo q̂ (0/5 e 0/5), as mesmas taxas e os
mesmos valores do B-40.

## Carga S3 parametrizada no orquestrador (2026-11-06)

Na integração (06/11, depois do W-9), o TD-017 desta branch virou TD-018 (o W-9 entrou antes com o TD-017).

Branch `feature/s3-carga-parametrizada` (worktree `wt-carga`), a partir de `daa18a2`. TDD: testes do conferidor
vermelhos (`147aaec`: 8 testes, 18 falhas e 2 erros) antes do `feat:`; S31 vermelho (`runs/carga-s3-param/s31-vermelho.out`:
20 PASS, 10 FAIL, com o conferidor novo e o orquestrador da base, que conferia os campos e não os passava) antes do
`feat:` do orquestrador. Com a VM rodando o piloto, só os testes novos e os afetados rodaram no *host* (sem Surefire,
sem a suíte completa do orquestrador; o código Java não mudou).

| Suíte | Comando | Resultado |
|---|---|---|
| `test_campanha_s3_conferir.py` | `python -I scripts/test_campanha_s3_conferir.py` | **52 OK** (44 da base + 8 da classe `ParametrosCargaS3`) |
| `run-campanha-s3.test.sh` S31 | `bash scripts/run-campanha-s3.test.sh S31` | **30 PASS, 0 FAIL** (`runs/carga-s3-param/s31-verde.out`) |
| `run-campanha-s3.test.sh` afetados | `bash scripts/run-campanha-s3.test.sh S1 S4 S11 S24 S30` | **117 PASS, 0 FAIL** (492 s; `runs/carga-s3-param/afetados-s1-s4-s11-s24-s30.out`): o plano do `--dry-run` (S1), o *bundle* e o `ambiente.md` (S4), a retomada com *bundle* alterado (S11), o `--dry-run` por perfil (S24) e a linha da sonda (S30) |

`ParametrosCargaS3`: ausentes, nenhuma linha `s3_*` nos dois esquemas; presentes, impressos nos dois esquemas, depois
dos obrigatórios e na ordem da tabela; a tabela tem exatamente um campo por opção `--s3-*` de `S3Parametros.java`
(lida da fonte), menos as `--s3-replay-*`; limites aceitos (0 e 1 na probabilidade, 0 onde a faixa é ≥ 0); tipo e
faixa recusados (26 casos: negativo, zero onde é > 0, real em inteiro, texto, booleano, nulo, `NaN`, `Infinity`, acima
de 1); coerência com os padrões do programa (`s3_users` e `s3_limit_accounts` zerados; `s3_back_min_sec` acima do
máximo padrão; `s3_back_max_sec` abaixo do mínimo padrão); sem origem e nome desconhecido (`s3_replay_min_sec`,
`s3_foo`, `s3_client_mode`).

S31 (orquestrador): sem `s3_*`, a linha do jar termina em `--rejoin-timeout-sec 30`, sem `--s3-`, e o `--dry-run` diz
"padrao do programa"; com `s3_users=30 s3_think_ms=500 s3_logout_prob=0.9`, a linha é **exatamente** a de antes mais
` --s3-users 30 --s3-think-ms 500 --s3-logout-prob 0.9`; com `--sonda-replay`, a carga vem antes de
`--s3-replay-probe --sweep`; cinco valores ruins (`s3_logout_prob=1.5`, `s3_users=-1`, `s3_think_ms=0`, `s3_users=2.5`,
`s3_foo=1`) recusados no `--dry-run` (exit 4, com o nome no motivo); execução real ACEITA com as opções no pré-voo e na
carga, `carga_s3` no `execucao.json`, linha `carga_s3:` no *bundle*, os valores no `ambiente.md`; retomada com outra
carga recusada (bundle mudou). S31b: execução sem `s3_*` com `carga_s3 = {}`, nenhuma linha do jar com `--s3-` e
`carga_s3:-` no *bundle*. O auxiliar `parametros` do teste passou a gravar `0.9` como número real.

## W-5c: V com a sonda de *replay* e a varredura no `comparar_bracos.py` (2026-11-06)

Branch `feature/w5c-v-com-sonda` (worktree `wt-w5c`), a partir de `daa18a2` (`feature/controlador-integracao`, com
W-10 e P-48). Só Python (a VM estava medindo: nada de Maven nem Podman). Python 3.12.6, `python -E -P` (numpy e scipy
no *site* do usuário, decisão G2 de 28/10). TDD em três passos: o golden da base foi gerado e commitado antes de
qualquer mudança (`9093e6f`); os 20 testes da regra nova foram commitados vermelhos (`eb35290`; 12 erros por falta de
`violacoes_detalhe` e `DESFECHO_V_SEM_F2`, 8 falhas de asserção); depois o código. Um teste vermelho tinha uma conta
errada minha (2 achados da varredura fora de V no cenário de sobreposição, não 3) e foi corrigido no commit do código.

| Suíte | Comando | Resultado |
|---|---|---|
| `analysis` | `python -E -P -m unittest discover -s analysis` | **396 OK, 1 *skip*** (374 da base + 22) |
| `test_comparar_bracos*.py` | `python -E -P -m unittest discover -s analysis -p "test_comparar_bracos*.py"` | 60 OK (38 sem mudança + 22) |

Arquivo novo `analysis/test_comparar_bracos_w5c.py`:

| Classe | Testes | O que prende |
|---|---|---|
| `NaoRegressaoGolden` | 2 | sem sonda e sem varredura, cada chave do golden da base (`analysis/fixtures/w5c/golden-sem-sonda-daa18a2.json`: célula de teste com 6 blocos e trilha, descritiva com 2, violações de todas as classes com chaves distintas) existe no resultado atual com o mesmo valor; o golden exercita V nos dois braços, k > 0 e a regra IU |
| `ParesDistintos` | 5 | sobreposição online + sonda imediata + sonda tardia + varredura na mesma sid conta 1 par; 11 linhas de I2 `REAL` na mesma sessão contam 1; 5 pares em 19 linhas; linhas e pares por origem; pares em mais de uma origem; mesma chave com I1 e I4 conta 2; varredura só `REAL`; `contar_violacoes` por par distinto; `origem` desconhecida recusada |
| `FasesDoTardio` | 4 | tardio e varredura pelo `end_ns` do `Logout` `NONE` da sid (o `Logout` com erro anterior não vale); I2 e I2-PERDIDO pelo último `Block` `NONE` da identidade (o `Block` com erro não vale); I1 com motivo `ADMIN_BLOCK` e sid sem `Logout` no CSV contam sem filtro de fase; o tardio não usa o instante da própria leitura; com `--fases antes` as sem invalidação localizada também contam; a imediata vai pelo instante da detecção |
| `FormasDeI1` | 4 | forma 1 (`Logout` no nó da falha), forma 2 pela sonda imediata e pela leitura online (`Logout` por outro nó, leitura `VALID` no nó da falha entre `start` e `end`, com o instante da violação 7 ms depois do fim da leitura), outra (leitura depois do `end`; leitura por outro nó; par com detecção forma 2 e varredura); sensibilidade 4 de 6; sem `execucao.json`, formas indeterminadas e sensibilidade ausente; sem marcador, nenhuma forma 2; sem instrumentos, sem o desfecho de sensibilidade |
| `ReplayValidateFora` | 2 | com `ReplayValidate` (erro, latência de 4 s, tardio), sonda e varredura, o p99 da ação S3 (com e sem espera), a vazão útil, a taxa de erro, as falhas percebidas, o `evento_p99:Validate`, o Δ_rec e o denominador de V são idênticos aos da execução sem eles; custo da sonda; teto do tardio pelo `sonda_tardia_max` do `s3-resumo` |
| `VarreduraComErro` | 1 | `varredura-rep-NNN.erro.txt` deixa V e a sensibilidade ausentes, com motivo |
| `ComparacaoComSonda` | 3 | na célula de teste a sensibilidade é família sensibilidade, escala diferença, com p de permutação e sem Holm, fora da contagem de secundários; o co-principal V conta a forma 2; totais por braço; instrumentos diferentes entre A e B recusados; a tabela Markdown traz a regra e as formas |
| `SmokeW10` | 1 | formato real: recorte do *smoke* F2 do W-10 (`analysis/fixtures/w5c/smoke-w10-f2/`); 4 pares de I1 em 8 linhas (6 da sonda, 2 da varredura), 2 em mais de uma origem; forma 1 nas duas sids do M1 (`Logout` no `isn3`, `127.0.0.1:12903`), forma 2 nas duas com `Logout` no `isn2` relidas `VALID` pelo `isn3`; atribuição 4 pela detecção e 4 pela invalidação; o `I2` `INCERTA` da varredura fora |

Fixture `smoke-w10-f2`: cópia de `marcadores.txt`, `violacoes-rep-001.csv`, `varredura-rep-001.csv`, dos resumos e do
meta de `runs/w10-smoke/f2-sonda/` do worktree `wt-w10` (os CSV estão fora do Git em `runs/`); `rep-001.csv` com as
linhas das sids da sonda e da varredura, da identidade `s3-u-002` e as 3 últimas da carga; `s3-acoes-rep-001.csv` com
as 2 primeiras e as 3 últimas ações; `execucao.json` sintetizado do `comando.txt` (só o `--servers`). SHA-256 das
fontes: `violacoes-rep-001.csv` `119f1887…f42ef`, `varredura-rep-001.csv` `f9290904…64d71`, `rep-001.csv`
`70516510…9cbb`, `s3-acoes-rep-001.csv` `b51bce59…bb60`, `marcadores.txt` `6fac85c4…9136`.

Conferência da CLI no *smoke* inteiro (fora da suíte, no *scratchpad*; A = B = a execução do W-10): 258
`ReplayValidate` (139 imediatos na janela e 119 tardios, os números do relatório do W-10), 4 pares em 8 linhas, forma
1 = 2, forma 2 = 2, sem a forma 2 = 2, 2 240 ações S3 nas fases `durante` e `recuperacao`.

### W-5d: varredura com erro pelo tratamento simétrico (2026-11-06)

Mesma branch, depois da V-W5c (`aec81b0`). TDD: `3e50735` (`test:`, 10 vermelhos; a guarda de instrumentos já
passava, porque só prende o comportamento que não muda) e `bc9cfdd` (`feat:`).

| Suíte | Resultado |
|---|---|
| `python -E -P -m unittest discover -s analysis` | **407 OK, 1 *skip*** (396 + 11); golden da base intacto |

| Classe | Testes | O que prende |
|---|---|---|
| `TotaisSemVarredura` | 3 | totais sem a varredura na execução: pares por classe, pares, linhas; a forma do par recalculada sem a varredura (o par com leitura cruzada e I1 `REAL` da varredura, "outra" com ela, vira forma 2 sem ela); indeterminada sem `execucao.json` |
| `VarreduraSimetrica` | 8 | célula de 6 blocos com erro só em A (bloco 2), só em B (3) e nos dois (4): V e `:sem_forma2` dos dois braços sem a varredura, `d_b` de cada bloco, os 6 blocos no teste, `co_principal_v` e contagens por invariante trocadas; também na célula descritiva; `varredura_com_erro` com o braço da falha e os dois V; sensibilidade sem os blocos 2 a 4 (média dos blocos 1, 5 e 6, sem p e sem Holm); célula só com os blocos sem falha igual à de antes; linha na tabela Markdown; guarda de instrumentos com `erro` = varredura pedida |

## Integração do lote do *benchmark* (W-10, P-48, W-9, carga S3, W-5c/W-5d e fonte única da apuração de V) (2026-11-06)

Branch `feature/controlador-integracao` (worktree `wt-ctrl-integ`), a partir de `9a66e7e` (tag `int/2026-11-06-w6c`).
Merges `--no-ff` em ordem, com a numeração pela regra "depois do que existe" (tabela em cada commit de merge):

| Commit | O que entra | Numeração na integração |
|---|---|---|
| `c6fef4b` | W-10, sonda de *replay* (`7c94cfb`) | TD-016, caso S30 |
| `daa18a2` | correção do P-48 (`a97675f`) | TD-015 item 26 |
| `e2a220a` | W-9 em `b42741a` (derivação da calibração) | TD-016 da branch → TD-017 |
| `2d9d6b1` | carga S3 parametrizada (`a733282`) | TD-017 da branch → TD-018, caso S31 |
| `300a6e4` | W-5c e W-5d (`dba7320`) | TD-017 da branch → TD-019 |
| `ef0b06c` | W-9 em `6d154c6` (V por pares, `apuracao_v.py`) | sem troca |
| `0acfb44`, `ade376e` | `test:` (vermelho) e `refactor:`: o `comparar_bracos` importa do `apuracao_v` as 13 funções, as 26 constantes e o `ErroUso`, e apaga a cópia | — |
| `9df4594`, `acd079d` | `test:` (vermelho) e `feat:`: D-1 do roteiro da recalibração, `s3_replay_late_max`, `s3_replay_min_sec` e `s3_replay_max_sec` no `--parametros`, só com `--sonda-replay` | TD-018 (parágrafo novo), caso S32 |

Suítes rodadas depois que o piloto terminou na VM (~08:20Z), uma vez cada: Surefire, Python e injetores na ponta
`ade376e` (o D-1 não toca `workload/`, `analysis/` nem os injetores); o teste do conferidor e o subconjunto do
orquestrador na ponta `acd079d`, com o D-1. JDK 22.0.2, Maven
3.9.16, Python 3.12.6. Sem VM.

| Suíte | Comando | Resultado |
|---|---|---|
| Surefire | `mvn -q -o -T1 clean test` (em `workload/`) | **1175/0/0/1 em 167 classes** (1142 + 23 do W-10 + 10 do P-48) |
| `test_campanha_s3_conferir`, `_h4`, `_matriz`; `test_rebaseline_matriz` | `python3 -I -m unittest discover -s scripts -p "<nome>.py"` | 52 OK em `ade376e` (44 + 8 da carga) e **59 OK** em `acd079d` (+ 7 do D-1); 11 OK; 16 OK; 8 OK |
| `analysis` | `python -E -P -m unittest discover -s analysis` | **486 OK, 1 *skip*** (66 s). O teste de identidade do `apuracao_v` roda e passa (não é o *skip*) |
| `analysis/test_calibracao_s3.py` em `-I` | `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"` | 79 testes, OK, 3 pulados (os de paridade, que exigem numpy) |
| `inject-crash.test.sh` | `bash scripts/inject-crash.test.sh` | 141 PASS, 0 FAIL (43 s) |
| `inject-partition.test.sh` | idem | 125 PASS, 0 FAIL, 2 SKIP (111 s) |
| `inject-netdegrade.test.sh` | idem | 298 PASS, 0 FAIL, 1 SKIP (254 s) |
| `inject-jitter.test.sh` | idem | 35 PASS, 0 FAIL (2 s) |
| `run-campanha-s3.test.sh`, **subconjunto** | `bash scripts/run-campanha-s3.test.sh S1 S4 S11 S22 S24 S25 S26 S27 S29 S30 S31 S32` | **338 PASS, 0 FAIL** (10:23:04Z a 11:20:11Z, 3426 s, sozinho no *host*; `runs/bench-int/suite-s3-subconjunto.out`) |

**Subconjunto do orquestrador, não a suíte completa.** Por decisão do coordenador (06/11), para ganhar cerca de 1,5 h
antes da recalibração, rodaram só os casos tocados neste lote e os básicos: S1 (pré-voo e `--dry-run`), S4
(calibração com F1 e F2), S11, S22 (B1), S24 (referência por perfil), S25 (F1 em curso na linha de fim), S26 (F3 do
W-6c), S27 (swap), S29 (I-1), S30 (sonda de *replay* do W-10), S31 (carga parametrizada) e S32 (D-1). **A suíte completa
(S1..S31) roda depois da recalibração e antes da avaliação**, quando o coordenador avisar.

**Fonte única da apuração de V.** O `comparar_bracos.py` deixou de ter a cópia: importa do `analysis/apuracao_v.py`
(que roda em `python3 -I`, sem numpy, para o `calibracao_s3`) as 13 funções de `COPIADAS`, os 26 nomes de `CONSTANTES` e
o `ErroUso`. O `ErroUso` é o mesmo objeto para que o erro de contrato dos leitores continue saindo com exit 2 por
`ERROS_USO`. O teste `test_apuracao_v_igual_a_do_comparar_bracos` passou a conferir identidade de objeto e que o
`comparar_bracos` não define nenhum desses nomes (vermelho em `0acfb44`: `_int_ou_none` era outra função). O
`comparar_bracos` continua importável em `-E -P`, e o golden do W-5c passa (`test_comparar_bracos*.py`, 71 OK).

**Congelados**, iguais a `9a66e7e`: `run-rebaseline.sh` `d95305fe…`, `inject-netdegrade.sh` `4ebdd8a3…`,
`inject-partition.sh` `59dd2bee…`, `inject-crash.sh` `531a98a1…`, `inject-jitter.sh` `7e68962b…`, lista do h4
(`e4cc5c79…`, `1e7462f4…`, `53d7e7cb…`), `cria-caches.sh`, `collect-resources.sh`, `analysis/rebaseline.py`,
`scripts/rebaseline_matriz.py` e `runs/rebaseline/matriz.tsv`.

**D-1 do roteiro da recalibração** (`9df4594` test, `acd079d` feat; TD-018). Vermelho: conferidor com 59 testes, 24
falhas e 2 erros, todos da classe nova `ParametrosSondaReplay`; S32 com 15 PASS e 11 FAIL
(`runs/bench-int/s32-vermelho.out`). Verde: conferidor 59 OK e S32 26 PASS, 0 FAIL (`s32-verde.out`), e de novo no
subconjunto. O `test_sem_origem_e_desconhecido` deixou de usar `s3_replay_min_sec` como exemplo de campo desconhecido,
porque o campo agora tem motivo próprio. *Dry-run* (`runs/bench-int/dry-run/`, matriz de calibração com uma linha
`ASYNC-F2`, semente 700000; parâmetros com os 19 números, os cinco `s3_*` da L+ do piloto e `s3_replay_late_max` = 268
com a origem da DT-026; `--swap-limite-kb 1048576`):
- com `--sonda-replay`, exit 0, `DRY-RUN opcoes: swap_limite_kb=1048576 exigir_trilha_completa=nao sonda_replay=sim`,
  `DRY-RUN carga S3: --s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9
  --s3-admin-mean-sec 5 --s3-replay-late-max 268` e a linha do programa terminando em `--rejoin-timeout-sec 60
  --s3-users 40 … --s3-admin-mean-sec 5 --s3-replay-late-max 268 --s3-replay-probe --sweep`;
- sem `--sonda-replay`, exit 4: "s3_replay_late_max so vale com a sonda de replay (--sonda-replay do orquestrador)";
- a linha do programa do plano, com o jar construído da ponta e `--dry-run` do próprio programa, sai com 0
  (`programa-dry-run.out`): o picocli e o `S3Parametros.validar` aceitam `--s3-replay-late-max 268` com
  `--s3-replay-probe` e `--sweep`.

## A1: política de fusão própria do `sessions-async` (2026-11-06)

Branch `feature/merge-policy-semirreticulado` (base `int/2026-11-06-bench`, `cc2b71c`); TD-020 em `implementacao.md`.
Módulo novo `extensao-servidor/`: `mvn -f extensao-servidor/pom.xml test` (Infinispan 15.0.21 `provided`, JUnit 5.10.2,
Surefire 3.5.4). TDD: vermelho em `f5da120` (esqueleto da API que devolve a preferida; 46 testes, 38 falhas), verde em
`04719ee` (47 testes, 0 falhas; o teste do filtro da JVM entrou no verde, quando o filtro recusou o `java.time.Instant`
devolvido pelo `readResolve` de `java.time.Ser`).

**Valores reais do cliente.** `extensao-servidor/src/test/resources/br/unipampa/tcc/servidor/valores-cliente.tsv`
tem 12 valores (10 `SessionState`, 1 `IdentityState`, a chave `String`) serializados pelo `JavaSerializationMarshaller`
do infinispan-commons 15.0.0.Final, que é o *marshaller* do cliente Hot Rod (`S3Clientes.base`), com as classes do
*workload*. Achado na geração: quando `criadaEm` = `atualizadaEm` (o `SessionState.criar`), o cliente usa o mesmo
`Instant` nos dois campos e a serialização grava o segundo como referência ao primeiro; os bytes só batem construindo
o objeto do mesmo jeito.

| Classe de teste | n | Cobre |
|---|---|---|
| `ValoresClienteTest` | 4 | a cópia de `SessionState` no JAR serializa nos mesmos bytes do cliente; cabeçalho `ACED 0005` sem prefixo; `serialVersionUID` 1L e o mesmo nome; fonte idêntico ao do *workload* |
| `OrdemSessaoTest` | 7 | postos iguais ao `RankEstado`/`RankMotivo` do T2 (B-45); ordem escrita à mão sobre os valores do arquivo, com 50 embaralhamentos; conteúdo antes da versão; motivo e versão antes do instante; empate com identidade diferente |
| `LeitorSessaoTest` | 13 | `WrappedByteArray`, `byte[]`, `WrappedBytes` com deslocamento e objeto já desserializado; `IdentityState` e `String` do cliente, `Integer`: tipo inesperado; nulo, lixo, vazio, truncado, bytes sobrando, campo nulo no fluxo (`TC_NULL` no lugar da identidade) e `getBytes` que lança: ilegível; chave como sid ou `hex:`; filtro da JVM; lista de classes |
| `PoliticaFusaoSessoesTest` | 17 | caso do I1 (maioria `VALID` preferida, isn3 `LOGOUT`: vence o isn3, `igual_pnn=nao`); pares; **todas as combinações** (preferida ou nula × até 3 outras em todas as ordens, 8 200 casos, com o empate); semirreticulado (comutativa, associativa, idempotente); entrada original (mesmo objeto, mesmos metadados); independência da ordem; entrada nula e `NullCacheEntry`; saída de segurança por motivo (ilegível, tipo, empate, sids diferentes, erro interno) com a linha `A1-SAIDA-SEGURANCA`; nunca lança (nem com o *log* quebrado); formato do primeiro valor uma vez; construtor público |
| `ConfiguracaoServidorTest` | 6 | `META-INF/services/org.infinispan.conflict.EntryMergePolicy`; `ServiceLoader`; `MergePolicy.CUSTOM`; `sessions-async.json` pelo parser da REST (`ParserRegistry.parse(json, JSON)`, como `CacheResourceV2`) instancia a política; **leitura de volta** com o serializador da REST igual ao arquivo com o nome, byte a byte (o que o `cria-caches.sh --conferir` compara); os outros cinco *caches* com `PREFERRED_NON_NULL` |

*Workload* (`mvn -f workload/pom.xml test`): `ValoresServidorA1Test` (1, novo: o arquivo de valores é o que as classes e
o *marshaller* reais do cliente gravam), `CachesCargaJsonTest` (5: o `sessions-async` com a classe da A1, o resto e o
XML com `PREFERRED_NON_NULL`; o `canario-async` igual ao `sessions-async` salvo a política) e `ClusterSemCachesXmlTest`
(3: o JAR montado nos três serviços do compose sem *caches*). Vermelho em `f5da120` (3 falhas nessas classes), verde
em `04719ee`. Suíte completa do *workload* na ponta: **1177 testes, 0 falhas, 0 erros, 1 ignorado** (o mesmo
ignorado de antes).

### A1 no *cluster* local (`runs/a1-smoke/`)

*Harness* próprio (projeto Podman `a1`, portas 12801 a 12803, corte do isn3 por `nftables` por 90 s), carga S3 ASYNC
L+ com `--s3-replay-probe --sweep`, 480 s, sementes 700400 e 701300, A1 × `PREFERRED_NON_NULL`, mais um teste dirigido
do mecanismo (sessões criadas antes do corte e *logout* pelo isn3 isolado). Comandos, desvios e manifesto em
`runs/a1-smoke/comandos.md`; análise `runs/a1-smoke/scripts/analisar_a1.py` → `analise.out`/`analise.json`.

| Critério | Resultado |
|---|---|
| `?action=config` do `sessions-async` com a classe nos três nós | sim, nas 4 execuções A1 (`config-sessions-async-isnN.json`; `cria-caches.sh --conferir` byte a byte) |
| Política carregada nos três nós | sim (`A1-POLITICA carregada` em isn1, isn2 e isn3) |
| Formato do valor no servidor | `WrappedByteArray` de 552 ou 575 bytes, desserializado pelas classes do cliente (`formato-decodificado.out`) |
| CR de fusão antes da varredura | sim nas 4 execuções de 480 s (38 a 49 s depois da cura); não na de preparo de 300 s (fora da comparação) |
| I1 REAL da varredura | A1: 0 e 0; controle: 0 e 1 |
| *Logouts* pré-existentes desfeitos na fusão (varredura, I1 online da recuperação ou sonda tardia) | A1: 0 de 2; controle: 3 de 3 |
| Teste dirigido: confirmadas lidas `VALID` depois da fusão (isn1/isn2/isn3) | A1: 0/0/0 de 26; controle: 21/23/21 de 23 |
| Saída de segurança no *cluster* | 0 em 182 decisões |
| `ISPN000466` ou erro da classe da política | 0 |

Detalhe e veredito em `docs/relatorio-a1-2026-11-06.md`.
