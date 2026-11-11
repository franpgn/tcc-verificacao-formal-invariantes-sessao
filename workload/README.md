# workload

Programa de carga em Java 17 + Hot Rod, que gera operações concorrentes sobre os *caches* `sessions` / `sessions-async` / `counters` do *cluster* Infinispan e registra latência e violações de invariantes.

## Status

Esqueleto migrado de `/workspace/prototipo/workload/` em B-04. As classes compilam, mas a lógica completa das operações O1-O7, do gerador Zipfian, do *warm-up* parametrizado e da CLI será materializada por B-05 a B-10 do *backlog*.

Componentes presentes:

| Classe | Responsabilidade |
|---|---|
| `WorkloadMain` | Ponto de entrada; analisa argumentos, instancia o `RemoteCacheManager` e dispara *threads* |
| `SessionOps` | Implementação das operações O1-O7 sobre Hot Rod |
| `SessionState`, `IdentityState` | Modelos do estado representado nos *caches* |
| `LatencyRegistry` | Registro de latência por operação (será evoluído para HdrHistogram em B-06) |
| `InvariantAuditor` | Checagem imediata de invariantes I1-I4 e auditoria periódica de I5/I6 (B-07) |

## Compilação

```bash
cd workload
mvn -q package
```

Produz `target/session-workload-0.1.0-SNAPSHOT-shaded.jar` (uberjar pelo *Maven Shade Plugin*).

## Execução (placeholder até B-10)

A CLI atual aceita argumentos posicionais:

```bash
java -jar target/session-workload-0.1.0-SNAPSHOT-shaded.jar \
     <duração_segundos> <threads> <host:port,host:port,...>
```

Exemplo:

```bash
java -jar target/session-workload-0.1.0-SNAPSHOT-shaded.jar \
     60 8 127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224
```

A CLI parametrizada conforme T7 (mistura 50/50 ou 95/5), T9 (10⁶ operações), T10 (30 repetições) e T11 (*warm-up* + descarte) será introduzida por B-10.

## Dependências relevantes

- `org.infinispan:infinispan-client-hotrod:15.0.0.Final`  — cliente Hot Rod alinhado ao servidor.
- `io.micrometer:micrometer-core:1.13.0`  — base para exportação de métricas (futura integração OpenMetrics em B-13).
- `com.fasterxml.jackson:jackson-databind:2.17.0`  — serialização dos `runs/<scenario>/summary.json`.
- `org.junit.jupiter:junit-jupiter:5.10.2`  — *smoke tests* a partir de B-05.

## Instrumentação de partição (B-33, parte E1)

Todas as opções abaixo vêm **desligadas**; sem elas a execução, o CSV de eventos e o de violações são os de antes (S1/S2 inalterados).

| Opção | Efeito |
|---|---|
| `--marker <arq>` | Lê os marcadores de falha (`fault=F2 node=isn3 event=start\|split\|end t_epoch_ns=<n>`). O CSV de eventos ganha a coluna `phase`; o de violações, `fase` e `classificacao` (I2 inline reclassificada por motivo do bloqueio). |
| `--w-recovery-sec` | Janela de recuperação após `t_fim` (padrão 60). |
| `--stop-at-fault-end` | Para a carga de escrita quando o marcador `end` aparece (exige `--marker`). |
| `--convergence` | Depois da carga, laço de convergência (período 5 s, teto 600 s, 3 rodadas válidas limpas); grava `convergencia-rep-NNN.csv` e `convergencia-resumo-rep-NNN.json`. Rodada só vale com a topologia completa, os dois caches `HEALTHY` (REST, `--rest-url`) e todos os leitores respondendo. |
| `--sweep` | Varredura final global (I1, I4, I2 global, bloqueio perdido); grava `varredura-rep-NNN.csv` e `varredura-identidades-rep-NNN.csv`. |

Códigos de retorno novos: operações `Block`/`Unblock` entram no CSV; `ERROR_UNAVAILABLE` distingue recusa por indisponibilidade de `ERROR_TRANSPORT`. A classificação do motivo do bloqueio (limite × administrativo) é feita por rastro no cliente (`RastroCliente`), sem alterar `IdentityState`. Esquemas e decisões em `docs/relatorio-b33-2026-10-15.md`.

## Cenário S3 (B-33, parte E2)

`--scenario S3` liga uma **simulação de ciclo de vida de sessão** (fora do enum `Scenario`; S1/S2 não mudam): usuários virtuais (pool U: `Login`, páginas com `Validate`, `Logout` ou abandono silencioso, "botão voltar"), contas com senha errada (pool L: `IncrementFailure`, `ResetFailures`, `Login`, `Unblock` de suporte) e um administrador (`Block`, `Unblock`). Os pools de identidades são disjuntos (`s3-u-NNN`, `s3-l-NNN`). Sem reparo, sem reconciliação e sem retentativa infinita (até 2 retentativas de aplicação, 500 ms e 1 s). Uma thread por ator; `--threads` não vale.

Ligação ao cluster (`--client-mode pinned`, padrão): um `RemoteCacheManager` por nó de `--servers`, cada um só com o seu nó e `ClientIntelligence.BASIC`; mais um console apontado a `--admin-node` (`isnK` = K-ésimo de `--servers`, ou `host:porta`; deve ser nó da maioria). `--client-mode topology` usa um cliente topology-aware (contraste). Timeouts: `--hotrod-timeout-ms` (2000; é também o padrão da biblioteca) e `--hotrod-retries` (1; o padrão da biblioteca é 3).

Parâmetros (todos por CLI, padrões do plano): `--s3-users 20`, `--s3-limit-accounts 10`, `--s3-think-ms 1000`, `--s3-session-median-sec 20`, `--s3-logout-prob 0.7`, `--s3-idle-timeout-sec 30`, `--s3-back-window-sec 10`, `--s3-admin-mean-sec 15`, `--s3-admin-hold-min-sec 20`, `--s3-l-support-delay-sec 60`, `--s3-app-retries 2`, `--s3-backoff-ms 500`, entre outros (`--help`). Determinismo pela semente `--seed` (e a repetição). Saídas extras em `--csv-dir`: `s3-acoes-rep-NNN.csv` (ação de aplicação colapsada: resultado, tentativas, falha percebida; distingue `Validate` que viu `INVALID`) e `s3-resumo-rep-NNN.json`. Detalhes e limites em `docs/relatorio-b33-2026-10-15.md`, parte E2.

## Próximas tarefas previstas

- **B-05** — Implementação efetiva de O1-O7 com Hot Rod, códigos de retorno conforme Cap. 3 §3.3.1.
- **B-06** — `LatencyRegistry` baseado em HdrHistogram, com *dump* CSV por operação.
- **B-07** — Auditor com checagem imediata para I1-I4 e auditoria periódica para I5/I6 sob quiescência.
- **B-08** — Gerador de chaves com distribuição Zipfian ρ = 0,99, 100 000 chaves, *payload* fixo de 1 KB.
- **B-09** — *Warm-up* de 60 s ou 10 % da execução; descarte das primeiras 5 % das amostras.
- **B-10** — CLI parametrizada (cenário S1/S2, duração, *target throughput*, repetições, *cache* alvo).
