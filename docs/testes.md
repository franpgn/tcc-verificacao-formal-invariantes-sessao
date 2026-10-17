# Testes

> Registro da suíte de testes do *workload* e dos critérios de aceite verificados na frente prática.

## Suíte unitária (JUnit 5)

Execução: `mvn -f workload/pom.xml test`. Estado atual: **49/49 verde** nos blocos da tabela abaixo; na branch de integracao `feature/etapa-formal-integracao` (2026-10-16) o Surefire completo mede **184 testes, 0 falhas, 0 erros, 2 skipped** (inclui B-33), e `python -m unittest` em `analysis/` mede 9/9.

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
| + B-32 (+auditoria) + B-33 (final) | 125/0/2 \| 127/0/1 | 298/0/1 \| 306/0/0 | 35/0 \| 35/0 | `mvn test` 184/0/0 (2 skipped); `unittest` analysis 9/9 |

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

## Gate v4 (S0): testes sem VM de L-0, L-0c, L-1 e G-5b (2026-10-16)

Branch `test/b29-gate-v4`; relatorio em `docs/relatorio-gate-v4-2026-10-16.md`. Nada aqui toca VM, rede ou privilegio: shims de `podman/nsenter/tc/systemd-run/systemctl/journalctl` e injetor falso em `runs/gate-b29/gate-test-lib-v4.sh`. Os testes provam a logica dos scripts, **nao** o kernel, o systemd real (AccuracySec) nem o formato real de `tc -s`/journal.

```bash
bash runs/gate-netdegrade/l-gate.test.sh   # l-lib, l0, l1 (verbatim) e l0c: guarda de netns, exigir_go, dead-man que dispara, 19 mutantes
bash runs/gate-b29/g5b.test.sh             # G-5b-A (cura apos split real) e G-5b-B (dead-man sozinho); ~330 s no Git Bash
bash runs/gate-netdegrade/verbatim.sh verificar   # diff doc x arquivo de l-lib.sh, l0.sh, l1.sh
bash runs/gate-b29/mutacao-gate-v4.sh l|g5b|todas [paralelismo]   # MORTO/SOBREVIVEU/TRAVOU por mutante (timeout por mutante 1200 s)
```

Contagens medidas (sem `java` ativo salvo indicacao):

| Suite | Git Bash | WSL (Ubuntu, gawk 5.1, `flock` real) |
|---|---|---|
| `l-gate.test.sh` | 162/0 (3 rodadas), e 162/0 (2 rodadas apos o cherry-pick final) | 157/0 (2 rodadas) |
| `g5b.test.sh` | 128/0 (2 rodadas finais, 330 s e 328 s) | 126/0 (2 rodadas) |
| `inject-netdegrade.test.sh` | 298/0/1 SKIP (`T-nd-21`, sem `flock` real) | 306/0/0 |
| `inject-partition.test.sh` | 125/0/2 SKIP | nao medido nesta rodada (127/0/1 na rodada anterior) |
| `inject-jitter.test.sh` | 35/0 | nao medido |
| `g5.test.sh` / `g6.test.sh` (isolados) | 50/0 / 70/0 | nao medido |
| `g1b.test.sh` | falhas=0 | nao medido |
| `g1.test.sh` (5 rodadas) | 4x 61/0 e 1x 60/1 (`T-g1-2`) | nao medido |
| `verbatim.sh verificar` | IDENTICO nos 3 (l-lib 80 linhas, l0 19, l1 17) | IDENTICO nos 3 |

Mutacao (`mutacao-gate-v4.sh`): bateria completa no Git Bash, 36 mutantes (19 de `l-gate`, 17 de `g5b`), **36 MORTO, 0 SOBREVIVEU, 0 TRAVOU**, os dois baselines passam; no WSL, `g5b` 17/17 MORTO, 0 SOBREVIVEU. Apos o conserto do `verbatim.sh`, os mutantes V1..V6 foram reexecutados no Git Bash: 6/6 MORTO. A bateria completa de `l` nao foi repetida depois do commit do `verbatim.sh` nem a de `g5b` completa no Git Bash depois do `ff18fe3` (so a do WSL).

### Ressalvas registradas da v4

- **SKIP/ausencia dos testes de modo git no WSL**: 5 casos em `l-gate` (162 contra 157) e 2 em `g5b` (128 contra 126). O `.git` do worktree aponta para um caminho Windows, entao `git rev-parse` falha no WSL e os casos `100755` sao pulados. Nao e falha. O mesmo efeito aparece no Git Bash se `MSYS_NO_PATHCONV=1` estiver exportado (126 e 157 em vez de 128 e 162).
- **`T-g1-4` e `T-g1-2` (`g1.test.sh`)**: corrida de `parar_log_saude` (`runs/gate-b29/g-lib.sh`, linhas 64 a 69): uma amostra de saude em voo depois do abort faz o arquivo crescer de 1 a 3 linhas apos a conferencia. Instavel no Git Bash (2 de 6 rodadas dessa sessao falharam, 1 em `T-g1-2` e 1 em `T-g1-4`; 2 de 5 na primeira sessao, em `T-g1-4`); preexistente a v4 (arquivo inalterado desde `4b680a2`). **Nao corrigido** por decisao do Gestor; fica como ressalva.
- **`T-g6-5` (`g6.test.sh`)**: falha apenas sob carga (execucao paralela de varias suites); isolado passa 70/0.
- **Rodar as suites em serie.** Em paralelo, `g1`, `g6` e `l-gate` falham por tempo. O `g5b.test.sh` leva ~330 s no Git Bash: com timeout externo abaixo de 330 s ele e morto (rc 124), o que nao e falha do teste.

### Defeitos de teste encontrados e corrigidos na S0

- `T-l-9c`: disparo "antecipado" medido com margem de 1 s, menor que o custo de fork do Git Bash; margem passou a `MIN=12 s` (commit `dd67af8`).
- `g5b.test.sh`: vigias em segundo plano sem limite penduravam a suite; agora `espera_cond` de 90 s, `G_CASO_TIMEOUT=240 s` por caso e `MUT_TIMEOUT=1200 s` por mutante (cherry-pick de `111e673`).
- `T-b-6d` e `T-b-8f` falhavam no WSL por corrida do teste (o vigia so escrevia `nodes2` depois de `heal.log`/`journal-unit.txt`); o gatilho passou a ser `rc_injetor=` em `estado.txt` (cherry-pick de `ff18fe3`).
- `verbatim.sh`: sob `pipefail` no Linux, o `awk` saia no fence de fechamento e o `tr` morria por SIGPIPE (141), falhando a extracao; o `awk` agora consome toda a entrada.
