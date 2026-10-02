# Implementação — visão técnica

> Documento técnico que descreve a estrutura do código do protótipo e o estado de implementação de cada componente. Mantido pelo agente de desenvolvimento e atualizado a cada bloco do *backlog* fechado.

## Estrutura de pacotes

```
workload/
└── src/main/java/br/unipampa/tcc/session/
    ├── WorkloadMain.java          # ponto de entrada
    ├── Scenario.java              # mistura de operações (S1 50/50, S2 95/5)
    ├── KeyGenerator.java          # gerador Zipfian de chaves de sessão
    ├── WarmupPolicy.java          # política de warm-up e descarte (T11)
    ├── Cli.java                   # configuração de linha de comando (Picocli)
    ├── SessionOps.java            # operações O1-O7 via Hot Rod
    ├── SessionState.java          # modelo do estado de sessão
    ├── IdentityState.java         # modelo do estado de identidade
    ├── OpResult.java              # tipo único de retorno das O1-O7 + map return_code
    ├── LatencyRegistry.java       # registro de latência por operação (resumo stdout)
    ├── EpochClock.java            # âncora epoch-ns por repetição (T2 eventos)
    ├── EventCsvWriter.java        # trilha de eventos crus em CSV (T3/T4 eventos)
    └── InvariantAuditor.java      # detector de violações I1-I6
```

## Trilha de eventos crus (CSV consumido por analysis/)

Além do resumo de latência por histograma (`LatencyRegistry`, impresso em
*stdout*), o *workload* grava uma **trilha de eventos crus** por repetição,
um evento por operação O1–O5, no formato exato esperado pelo pipeline:

```
op_id,operation,start_ns,end_ns,replica,return_code,key
```

- **`OpResult.returnCode()`** mapeia o `Code` interno para o código
  canônico: `OK_LOGIN→OK`, `OK_VALIDATE→VALID`, `OK_STATUS→COUNTED`,
  `OK_NOOP→NONE`, `NOT_FOUND→NONE`, `BLOCKED→BLOCKED`,
  `ERROR_TRANSPORT→ERROR_TRANSPORT`. Apenas `ERROR_TRANSPORT` fica fora do
  conjunto de sucessos de `analysis/percentis.py`, então `taxa_erro`
  reflete somente falhas de transporte.
- **`EpochClock`** captura, no início de cada repetição, o par
  (`epochBaseNanos = Instant.now()`, `monoBaseNanos = System.nanoTime()`)
  e converte cada instante monotônico das operações em epoch-ns,
  preservando durações.
- **`EventCsvWriter`** mantém um `op_id` global (`AtomicLong`), suprime
  eventos de warm-up/descarte (mesma `WarmupPolicy` do registry) e grava
  `<csv-dir>/rep-NNN.csv`.
- **`SessionOps`** emite, em O1–O5, o evento cru com nome canônico
  (`Login`, `Validate`, `Logout`, `IncrementFailure`, `ResetFailures`),
  `replica` = servidor de conexão (primeiro servidor Hot Rod; *fallback*
  aceito ao *owner* real) e `return_code`.

O *marshaller* do cliente Hot Rod é `JavaSerializationMarshaller` (os
valores `SessionState`/`IdentityState` são POJOs `Serializable`); sem isso
o ProtoStream padrão falha ao serializar e toda escrita vira
`ERROR_TRANSPORT`.

## Mapeamento operações ↔ Cap. 3 §3.3.1

| Operação (Cap. 3) | Método (Java) | Cache-alvo | Estado |
|---|---|---|---|
| O1 `Login` | `SessionOps.login(id, cred)` | `sessions` / `sessions-async` | esqueleto |
| O2 `Validate` | `SessionOps.validate(sid)` | leitura no mesmo *cache* | esqueleto |
| O3 `Logout` | `SessionOps.logout(sid)` | `sessions` / `sessions-async` | esqueleto |
| O4 `IncrementFailure` | `SessionOps.incrementFailure(id)` | `counters` | esqueleto |
| O5 `ResetFailures` | `SessionOps.resetFailures(id)` | `counters` | esqueleto |
| O6 `Block` | `SessionOps.block(id, motivo)` | `counters` + invalidação em `sessions*` | a implementar em B-05 |
| O7 `Unblock` | `SessionOps.unblock(id)` | `counters` | a implementar em B-05 |

## Mapeamento componente ↔ linha Tx da Tabela 1

| Componente | Tx coberta(s) | Estado |
|---|---|---|
| `cluster/podman-compose.yml` + `cluster/infinispan-cluster.xml` | T1, T2, T3, T4, T15, T16, T19, T20 | implementado (B-02, B-03) |
| `spec/SessionStore.tla` + `MC-*.cfg` + `run-tlc.sh` | T21 | implementado (B-14) |
| `workload/.../WorkloadMain.java` | T7, T9, T11, T14 | integração de `Scenario` + `KeyGenerator` (B-08); CLI completa em B-10 |
| `workload/.../SessionOps.java` | T5, T7 | implementado com Hot Rod e `OpResult` (B-05) |
| `workload/.../LatencyRegistry.java` | T13 | HdrHistogram + dump CSV (B-06) |
| `workload/.../InvariantAuditor.java` | M2 (Cap. 3 §3.3.4) | checagens I1-I4 imediato + auditoria I5/I6 periódica (B-07) |
| `workload/.../KeyGenerator.java` | T5, T6, T8 | Zipfian Rejection-Inversion, universo 100k, ρ=0,99 (B-08) |
| `workload/.../Scenario.java` | T7 | enum S1 50/50 e S2 95/5 (B-08) |
| `workload/.../WarmupPolicy.java` | T11 | warm-up max(60s, 10%) + descarte 5% restante (B-09) |
| `workload/.../Cli.java` | T7, T9, T10, T11, T14 | CLI Picocli com --scenario, --duration, --ops, --rep, --threads, --seed, --warmup-min-sec, --csv-dir, --dry-run (B-10) |
| `workload/.../EpochClock.java` | T2 (eventos) | âncora epoch-ns por repetição |
| `workload/.../EventCsvWriter.java` | T3/T4 (eventos) | CSV `op_id,operation,start_ns,end_ns,replica,return_code,key` |
| `scripts/inject-crash.sh` | T17 | `podman stop`/`start` por 60s (B-11) |
| `scripts/run-baseline.sh` | bateria F1/F3 | S1/S2 × {none, F1, F3}, popula `runs/` e aciona `analysis/` |
| `scripts/inject-jitter.sh` | T18 | F3: netem aplicado **host-side via nsenter** no netns do container — `sudo nsenter -t <pid> -n tc qdisc ... netem delay 20ms 13ms distribution normal` (p99 ≈ +50 ms); `<pid>` resolvido por `podman inspect`; trap de cleanup garante remoção; exit 5 = netem rejeitado (B-12, mecanismo migrado de `podman exec` em F3) |
| `scripts/inject-jitter.test.sh` | T-jit-1..4,7 | suite shell sem-VM (31 asserts) de `inject-jitter.sh` (dry-run valida o plano `nsenter`) + dry-run F3 do `run-baseline.sh` (B-12) |
| `scripts/collect-metrics.sh` | T19 | a implementar em B-13 |
| `analysis/percentis.py` | T13 | a implementar em B-16 |
| `analysis/bootstrap_ic.py` | T22 | a implementar em B-17 |
| `analysis/compare_scenarios.py` | T22 | a implementar em B-18 |

## Decisões técnicas a registrar

### TD-007 — F3 (netem) aplicado host-side via `nsenter`, não `podman exec`

A injeção da falha F3 (jitter/atraso) precisa configurar uma disciplina `netem` do `tc` (iproute2) sobre o `eth0` do nó alvo. A implementação inicial (B-12) usava `podman exec <node> tc qdisc ...`, executando o `tc` **dentro** do container. Ao validar o baseline F3 na VM Ubuntu descobriu-se que esse caminho não funciona com a imagem oficial Infinispan (UBI minimal): a imagem não traz `tc` no `PATH` e o container não recebe a capability `NET_ADMIN`, então o `add` é rejeitado.

A correção aplica o `netem` **a partir do host da VM**, entrando no *namespace de rede* do container via `nsenter`. O fluxo é: (1) resolver o PID do container com `podman inspect -f '{{.State.Pid}}' <node>`; (2) `sudo nsenter -t <pid> -n tc qdisc add dev eth0 root netem delay 20ms 13ms distribution normal`; (3) ao fim da janela (ou em qualquer sinal/erro, via `trap`), `sudo nsenter -t <pid> -n tc qdisc del dev eth0 root`. O efeito sobre o tráfego do nó é idêntico — o `eth0` afetado é o do container —, mas as ferramentas (`tc`, módulo `sch_netem`) e o privilégio ficam no host, que é onde existem. Vantagem colateral: o container permanece sem `tc` nem `NET_ADMIN`, mantendo a imagem oficial intocada. Validado manualmente na VM (`qdisc netem ... delay 20ms 13ms` aplicado e removido limpo, exit 0).

Consequências operacionais: a execução real do F3 passa a exigir, **no host**, `podman`, `nsenter` (util-linux), `tc` (iproute2), `sudo` e o módulo `sch_netem` (o container não exige nada disso). Como cada célula da bateria invoca `inject-jitter.sh` e o cache de credencial do `sudo` expira (~15 min), recomenda-se configurar `sudo` sem senha para o usuário da bateria na VM. O `--dry-run` continua sem dependências (imprime o plano com os comandos `nsenter` e sai 0). Os exit codes preservam a semântica: `3` = dependência ausente (`podman`/`nsenter`/`sudo`), `4` = container não encontrado/não-running ou PID não resolvido, `5` = `netem` rejeitado pelo kernel (`sch_netem` ausente).

### TD-006 — Escolha de Picocli como framework de CLI

A camada de linha de comando do `WorkloadMain` foi migrada de parsing posicional para Picocli (`info.picocli:picocli:4.7.6`). A escolha contrasta com parsing manual e com a biblioteca `args4j`. Picocli é o padrão de fato em CLIs Java contemporâneas: suporta opções longas e curtas, gera mensagem de ajuda automaticamente conforme as anotações, valida tipos primitivos e enums sem código adicional, e tem ~250 KiB com zero dependências transitivas. O custo é uma dependência adicional, justificada pela manutenção que economiza em validação e documentação. A classe `Cli` é puramente um *value object* anotado, sem regra de negócio; a execução fica em `WorkloadMain.executar()`, o que permite testes diretos via `CommandLine.parseArgs()`.

### TD-005 — Implementação do descarte de 5% como tempo proporcional

T11 da Tabela 1 prescreve descarte das "primeiras 5\,\% das amostras" após o warm-up. Como o {@link LatencyRegistry} usa HdrHistogram, que não preserva ordem temporal das amostras, a implementação adotada em {@link WarmupPolicy} traduz a fração de amostras em fração de tempo: descarta 5\,\% do tempo nominal de medição posterior ao warm-up. A aproximação é exata se a taxa de operações for estacionária, hipótese válida porque o objetivo do descarte é justamente eliminar a região onde a taxa ainda não estabilizou. O contador {@code descartados} no {@link LatencyRegistry} permite auditar quantas amostras caíram nessa janela após a execução.

### TD-004 — Algoritmo de amostragem Zipfian

O `KeyGenerator` implementa o algoritmo **Rejection-Inversion** de Hörmann e Derflinger (1996), autocontido. A escolha contrasta com duas alternativas consideradas: (a) reusar a classe `ZipfianGenerator` do YCSB, que adiciona dependência pesada para uma única função; (b) usar `org.apache.commons:commons-math3:ZipfDistribution`, que adiciona dependência por uma feature isolada. A implementação atual tem cerca de 100 linhas de código, é determinística dada uma seed e tem custo O(1) por amostra. Validada por três critérios em `KeyGeneratorTest`: distribuição empírica nos primeiros 100 bins com erro relativo abaixo de 5\%, reprodutibilidade entre instâncias com mesma seed, e cobertura superior a 30\% do universo de 100 000 chaves em 1 000 000 amostras.

### TD-001 — `numSegments` = 64

A configuração herdada do esqueleto usa `segments="64"`, enquanto a Tabela 1 T4 cita 256 como padrão do Infinispan. A escolha de 64 está alinhada à recomendação do guia de tuning (REF-045) para *clusters* de até 20 nós, em que um número menor de segmentos reduz o custo de roteamento e o tempo de *state transfer*. **Pendência:** decisão do autor entre manter 64 ou ajustar para 256 (alinhamento estrito à Tabela 1).

### TD-002 — Credenciais `admin:infinispan` no XML

A configuração atual mantém credenciais fixas no XML e nas variáveis de ambiente do `podman-compose.yml`. **Pendência:** parametrizar via variável de ambiente em B-05, com valores fora de versionamento.

### TD-003 — Modo Hot Rod single-server vs. multi-server

`WorkloadMain` atualmente alimenta o `ConfigurationBuilder` com a lista completa de nós (`isn1:11222, isn2:11223, isn3:11224`). Isso permite ao cliente descobrir a topologia dinamicamente, mas pressupõe que pelo menos um nó está acessível. Documentado como hipótese implícita.

## Como contribuir com este código

O protocolo está descrito em [`CONTRIBUTING.md`](../CONTRIBUTING.md). Cada incremento corresponde a um item do *backlog* em `docs/planejamento-inicial.md`, com critérios de aceite explícitos. Os passos típicos:

1. Criar *branch* `feature/<nome>` a partir de `main` (ou da *branch* anterior, quando há dependência).
2. Implementar conforme critérios de aceite e tabela de cobertura T1-T22 acima.
3. Adicionar testes unitários em `workload/src/test/java/.../`.
4. Atualizar este documento com o componente que mudou de estado.
5. Reportar a entrega no formato definido (objetivo, *branch*, arquivos alterados, testes, problemas, próximo passo).
6. Aguardar validação do autor antes do *merge*.
