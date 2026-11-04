# Configuração do *cluster* Infinispan

> Documento técnico que descreve a configuração do *cluster* Infinispan empregada no protótipo do TCC. Mapeia cada decisão de configuração para a(s) linha(s) Tx da Tabela 1 do Cap. 3 §3.3.5 da monografia.

## Arquivos

| Arquivo | Função |
|---|---|
| `cluster/podman-compose.yml` | Orquestra três nós Infinispan em *containers* Podman, com rede *bridge* dedicada e *health check* via REST. |
| `cluster/infinispan-cluster.xml` | Define os *caches* `sessions`/`counters` (DIST_SYNC, controle) e os irmãos `sessions-async`/`counters-async` (DIST_ASYNC, alvo), com partição tratada por `DENY_READ_WRITES`; o *workload* escolhe o par por `--replication sync\|async`. |

A inicialização ocorre via `cd cluster && podman-compose up -d`. O *health check* é executado pelo Podman a cada 5 s; um nó é considerado saudável quando `GET /rest/v2/cache-managers/default/health/status` retorna 200 com credenciais `admin:infinispan`.

## Mapeamento Configuração ↔ Tabela 1

| Linha Tx | Onde realiza | Trecho relevante |
|---|---|---|
| **T1** Modo de replicação | `cluster/infinispan-cluster.xml` | `sessions`/`counters` em `mode="SYNC"` (controle) e os irmãos `sessions-async`/`counters-async` em `mode="ASYNC"` (alvo), implementado em B-42.1 (antigo B-03); o *workload* escolhe o par por `--replication sync\|async` (padrão `sync`), resolvido em `PerfilReplicacao.java` |
| **T2** Número de nós: 3 | `cluster/podman-compose.yml` | Serviços `isn1`, `isn2`, `isn3` |
| **T3** `numOwners` = 2 | `cluster/infinispan-cluster.xml` | `owners="2"` em ambos os *caches* |
| **T4** `numSegments` | `cluster/infinispan-cluster.xml` | `segments="64"` (valor herdado do esqueleto; T4 da tabela cita 256 como padrão Infinispan; o esqueleto adota 64 para reduzir tempo de *state transfer* em ambiente com três nós — desvio justificado a registrar em `docs/decisoes-tecnicas.md` em B-04 ou ajustar para 256 se o autor preferir aderência estrita à Tabela 1) |
| **T15** MTU | herdado da configuração de rede do Podman (*bridge* default 1500) | sem ajuste explícito; equivale ao parâmetro T15 |
| **T16** Detecção de falha FD\_ALL3 | herdada do *stack* JGroups padrão (`default-jgroups-tcp.xml`) | `<stack-file name="cluster-stack" path="default-jgroups-tcp.xml"/>` aplica os defaults (8000 ms / 40000 ms) |
| **T17** Falha F1 (*crash*) | `scripts/inject-crash.sh` (modo padrão `silencioso`, 2026-10-28): cala o `eth0` de `isn2` no *netns* dele e mata o *container* com SIGKILL logo em seguida; 60 s depois, `podman start`. A pilha (`cluster/jgroups-tcp.xml`) tem **FD\_SOCK2** além do FD\_ALL3: o FD\_SOCK2 suspeita do vizinho assim que a conexão TCP com ele fecha, e um processo morto por SIGKILL tem os *sockets* fechados pelo *kernel* (FIN/RST) enquanto o *netns* existe. Sem o isolamento prévio a queda seria detectada em ~1 s pelo FD\_SOCK2 (modo `kill`); com ele nenhum FIN sai e só o FD\_ALL3 (40 s + 1 s do VERIFY\_SUSPECT2) decide, como em T16. O F1 de junho (`podman stop`, saída ordenada com *leave*) continua disponível como `--modo stop` | *smoke* local de 2026-10-28: Δdet de 36,5 a 41,9 s no modo `silencioso` e de ~1 s nos modos `kill` e `stop` (`docs/relatorio-f1-silencioso-2026-10-27.md`); falta o ensaio da VM |
| **T19** Métricas coletadas | `cluster/infinispan-cluster.xml` | `statistics="true"` em `cache-container` e em cada *cache* |
| **T20** Endpoint OpenMetrics | nativo do Infinispan 15 | endpoint exposto por `endpoints socket-binding="default"` |

## Partition Handling

A configuração adota `<partition-handling when-split="DENY_READ_WRITES" merge-policy="PREFERRED_NON_NULL"/>` nos dois *caches* (`sessions` e `counters`). Sob partição, depois que o detector de falhas (`FD_ALL3`, até ~40 s, mais 1 s de `VERIFY_SUSPECT2`) muda a vista, a partição minoritária entra em modo DEGRADED e recusa leituras e escritas, enquanto a maioria segue AVAILABLE. Após a cura, o `MERGE3` (10 a 30 s) percebe a outra vista e a reconciliação segue `PREFERRED_NON_NULL` (regra posicional: vale o valor da partição preferida; só se for nulo, o primeiro não nulo das demais). Esses tempos são **estimativas derivadas dos parâmetros, não medições**; a medição é objeto dos gates reais G-3 e G-4.

Esta política deixou de ser uma "salvaguarda passiva": F2 (partição de rede) agora é **injetável** por `scripts/inject-partition.sh` (B-29), de modo que `DENY_READ_WRITES` e a fusão passam a ser exercitados deliberadamente. O XML não muda; a decisão registrada é manter `PREFERRED_NON_NULL` (configuração real do projeto) e não oferecer *flag* para simular `ALLOW_READ_WRITES` (fora de escopo).

### Falha F2 — partição de rede (`scripts/inject-partition.sh`)

Mecanismo (opção (a2) do plano de 2026-10-09): disciplina `clsact` do `tc` com um filtro `u32` por IP de par e ação `drop`, nos dois sentidos, aplicada **só no nó isolado** a partir do host via `sudo nsenter -t <pid> -n tc ...`. O tráfego Hot Rod do cliente não é afetado (origem no gateway da *bridge*, não em IP de par) e a fila raiz fica livre para o `netem` de F3. Plano B (só se o gate G-0 falhar): `iptables`/`nftables` via `nsenter`, sem implementar agora; `podman network disconnect` foi descartado (remove o nó também para o cliente e muda IP/interface).

```mermaid
%%{init: {'theme':'base','themeVariables':{'background':'#ffffff','primaryColor':'#ffffff','primaryTextColor':'#000000','primaryBorderColor':'#000000','lineColor':'#000000','secondaryColor':'#ffffff','tertiaryColor':'#ffffff','clusterBkg':'#ffffff','clusterBorder':'#000000','titleColor':'#000000','edgeLabelBackground':'#ffffff','fontFamily':'Arial'}}}%%
flowchart TB
    INJ["inject-partition.sh<br/>sudo nsenter -t PID -n tc ..."]
    subgraph MAJ["maioria (2 nos) - AVAILABLE"]
        N1["isn1"]
        N2["isn2"]
    end
    subgraph MIN["minoria (1 no) - DEGRADED apos deteccao"]
        N3["isn3<br/>clsact: drop src/dst isn1 e isn2"]
    end
    N1 <-->|"JGroups 7800: intacto"| N2
    N1 -. "descartado nos 2 sentidos" .- N3
    N2 -. "descartado nos 2 sentidos" .- N3
    WL["workload Hot Rod"] -->|"portas publicadas: nao afetadas"| N3
    INJ --> N3
```

| Opção | Semântica |
|---|---|
| `--minority <no[,no]>` | Nó(s) isolado(s); estritamente menos da metade de `--nodes` (3 nós: só 1), senão exit 2 |
| `--nodes` | padrão `isn1,isn2,isn3`; IPs dos pares resolvidos por `podman inspect`, nunca fixos |
| `--duration` | padrão 90 s; abaixo de 45 s avisa que é "atraso severo", não partição |
| `--marker` | acrescenta `fault=F2 node=<no> event=start` ou `event=end` com `t_epoch_ns=<n>` (formato de B-25) |
| `--heal` | só remove `clsact` remanescente (após `SIGKILL`); sem `--minority`, limpa todos os nós |
| `--dry-run` | imprime o plano; sem podman, sudo, nsenter ou tc |

Códigos de saída: 0 ok; 1 genérico (inclui lock ocupado e resíduo); 2 argumento; 3 dependência ausente; 4 nó ausente/parado; 5 kernel rejeitou `clsact`/`u32`/`drop`; **6 a partição não se efetivou** (conexão TCP ainda possível a um par, ou contadores de descarte zerados no fim da janela). Dependências no host da VM: `podman`, `nsenter`, `tc`, `sudo`, `flock`, `timeout` e os módulos `sch_clsact`, `cls_u32`, `act_gact`.

Os testes sem VM estão em `docs/testes.md`; o aceite real (G-0 a G-6) está **pendente** na VM.

### Falha F4 — degradação parcial de rede (`scripts/inject-netdegrade.sh`, B-29.2)

Perda (aleatória ou em rajada), atraso com jitter, duplicação, reordenação e corrupção **entre os nós do cluster**, simétricas ou assimétricas, sem derrubar o nó nem tocar o tráfego Hot Rod do cliente. Mecanismo: disciplina `prio` na **raiz** da interface do contêiner, `netem` na banda 3 e um filtro `u32` por IP de destino do par, sempre por `sudo nsenter -t <pid> -n tc ...` a partir do host (mesmo padrão de F2/F3). A direção de saída é aplicada no alvo; a de entrada, no egress de cada par com destino ao alvo (`--direction out|in|both`, `--loss` e `--loss-in`). Como a raiz fica ocupada, F4 e F3 (`netem` raiz sem filtro) são **mutuamente exclusivos no mesmo nó** (exit 7); F4 também recusa um nó com `clsact` ou com o lock de F2 ativo. Detalhes, diagramas, matriz de severidades N1 a N5 e limites de fidelidade em `docs/degradacao-de-rede.md`.

| Opção | Semântica |
|---|---|
| `--target`, `--nodes`, `--peers` | alvo, cluster (padrão `isn1,isn2,isn3`), pares afetados (padrão: todos menos o alvo) |
| `--direction out\|in\|both` | padrão `out`; `in` e `both` tocam também os **pares** |
| `--loss`, `--loss-in`, `--loss-corr`, `--loss-model random\|gemodel`, `--burst` | perda por sentido, correlação, modelo (Gilbert-Elliott) e rajada média |
| `--delay`, `--jitter`, `--duplicate`, `--reorder`, `--corrupt` | demais degradações (`--reorder` e `--jitter` exigem `--delay`) |
| `--severity N1..N5` | perda 1%, 5%, 20%, 50%, 99,9% |
| `--duration` | padrão 120 s; abaixo de 45 s avisa |
| `--marker` | `fault=F4 node=<alvo> event=start\|end t_epoch_ns=<n>` (formato de B-25) |
| `--heal` | remove a `prio 1:` do injetor em todos os nós; nunca toca raiz alheia |
| `--dry-run`, `--ping-count` | plano sem executar; pings por par para medir a perda no fio (0 desliga) |

Códigos de saída: 0 ok; 1 genérico (lock, resíduo); 2 argumento; 3 dependência ausente; 4 nó ausente/parado; 5 kernel rejeitou `prio`/`netem`/`u32`; **6 a degradação não se efetivou** (perda no fio fora de 3 σ binomiais, `Sent`+`dropped` zerados (nenhum pacote casou o filtro) ou perda sem descarte); **7 raiz ocupada** (F3, `clsact`/lock de F2). Dependências no host da VM: `podman`, `nsenter`, `tc`, `sudo`, `flock`, `ping` e os módulos `sch_prio`, `sch_netem`, `cls_u32`. Os testes sem VM estão em `docs/testes.md`; o aceite real (gate L-0 a L-6, T-C2) está **pendente** e nada foi executado na VM.

## Locking e *state transfer*

```
locking: concurrency-level=1000, acquire-timeout=15000, striping=false
state-transfer: enabled=true, timeout=60000
```

- `acquire-timeout=15000` ms está acima do RTT esperado dentro de uma rede *bridge* local e abaixo do *timeout* de detecção de falhas (T16), o que evita que a aquisição de *lock* seja abortada por evento de FD.
- `state-transfer.timeout=60000` ms está alinhado à duração mínima do cenário F1 (T17), que prevê 60 s de indisponibilidade de um nó.

## Variantes pendentes

| Variante | Quando | Branch sugerida |
|---|---|---|
| Perfil `DIST_ASYNC` | implementado em B-42.1 (antigo B-03): *caches* `sessions-async` e `counters-async` (`mode="ASYNC"`, mesmos `owners`, `segments`, `partition-handling` e `merge-policy` de `sessions`/`counters`, conferidos por `ClusterXmlIrmaosTest`); seleção por `--replication async`. Na VM só depois de S7 | `feature/b42-1-perfil-async` |
| Stack JGroups customizado (parâmetros explícitos de FD\_ALL3) | a decidir | — |
| Pacote `numSegments=256` alinhado ao default Infinispan | a decidir (vide T4) | — |

## Como subir o *cluster* localmente

```bash
cd cluster
podman-compose up -d
podman-compose ps
```

Para validar o estado:

```bash
curl -s -u admin:infinispan \
  http://localhost:11222/rest/v2/cache-managers/default/health/status
# esperado: HEALTHY
```

Para encerrar:

```bash
podman-compose down
```

## Como verificar conectividade dos nós

```bash
for port in 11222 11223 11224; do
  echo "isn em $port:"
  curl -s -u admin:infinispan \
    http://localhost:$port/rest/v2/cluster/distribution \
    | head -c 200
  echo
done
```

## Reconfiguração em tempo de execução (B-44.1)

O que o servidor 15.0.21 deixa mudar sem recriar o cache, com prova no *cluster* local, está em
`docs/b44-1-reconfiguracao-infinispan.md`. Em resumo: `remote-timeout`, `acquire-timeout`, expiração,
rebalanceamento (por cache e global) e disponibilidade são mutáveis; `mode`, `owners`, `segments`,
`when-split` e `merge-policy` não são. Antes de usar: toda mudança de atributo num cache deste XML exige
escorva e impede o nó de reiniciar enquanto `data/caches.xml` não for limpo (P-29, P-30 em
`docs/registro-de-problemas.md`).

## Decisões a registrar em `docs/decisoes-tecnicas.md`

- T4: aceitar `numSegments=64` do esqueleto ou ajustar para 256.
- Persistir credenciais `admin/infinispan` por enquanto; revisar para variável de ambiente em B-05.
