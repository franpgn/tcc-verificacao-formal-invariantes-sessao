# Testes

> Registro da suíte de testes do *workload* e dos critérios de aceite verificados na frente prática.

## Suíte unitária (JUnit 5)

Execução: `mvn -f workload/pom.xml test`. Estado atual: **49/49 verde**.

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
```
