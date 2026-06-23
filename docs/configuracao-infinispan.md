# Configuração do *cluster* Infinispan

> Documento técnico que descreve a configuração do *cluster* Infinispan empregada no protótipo do TCC. Mapeia cada decisão de configuração para a(s) linha(s) Tx da Tabela 1 do Cap. 3 §3.3.5 da monografia.

## Arquivos

| Arquivo | Função |
|---|---|
| `cluster/podman-compose.yml` | Orquestra três nós Infinispan em *containers* Podman, com rede *bridge* dedicada e *health check* via REST. Imagem **pinada** em `quay.io/infinispan/server:15.0.21.Final`. |
| `cluster/infinispan-cluster.xml` | Define os *caches* `sessions` e `counters` em modo distribuído síncrono, com partição tratada por `DENY_READ_WRITES`; declara o *security-realm* `default` (properties-realm) e referencia o *stack* JGroups customizado. |
| `cluster/jgroups-tcp.xml` | *Stack* TCP JGroups com *discovery* estático **TCPPING** (substitui o MPING multicast do *stack* padrão). |

A inicialização ocorre via `cd cluster && podman-compose up -d`. O *health check* é executado pelo Podman a cada 5 s; um nó é considerado saudável quando `GET /rest/v2/cache-managers/default/health/status` retorna 200. A autenticação REST do Infinispan 15 usa **DIGEST**, portanto o `curl` precisa do *flag* `--digest -u admin:infinispan` (o `-u` simples, BASIC, retorna 403 `ISPN080052`).

## Formação do *cluster* (*discovery* TCPPING)

A rede *bridge* do Podman em WSL2 não propaga *multicast* de forma confiável entre *containers*, então o *discovery* por MPING do *stack* padrão (`default-jgroups-tcp.xml`) não forma o *cluster*. A solução adotada substitui MPING por **TCPPING** com lista estática de nós:

```xml
<TCP bind_addr="${jgroups.bind.address,jgroups.tcp.address:SITE_LOCAL}"
     bind_port="${jgroups.bind.port,jgroups.tcp.port:7800}" .../>
<TCPPING initial_hosts="isn1[7800],isn2[7800],isn3[7800]" port_range="0"/>
```

Notas de implementação verificadas no Infinispan 15.0.21.Final / JGroups 5.3.16.Final:

- `bind_addr` **não** pode ser o literal `0.0.0.0` (`BindException: not a valid address`); usa-se o símbolo `SITE_LOCAL`, que o JGroups resolve para o IP privado da interface do *container* na *bridge*.
- O atributo `num_initial_members` **foi removido** de TCPPING no JGroups 5.x; mantê-lo causa `JGRP000001: configuration error`. A contagem de membros iniciais passou a ser derivada de `initial_hosts`/GMS.
- `cluster/jgroups-tcp.xml` é montado por volume em `/opt/infinispan/server/conf/jgroups-tcp.xml` e referenciado em `infinispan-cluster.xml` por `<stack-file ... path="jgroups-tcp.xml"/>`.
- Como `infinispan-cluster.xml` substitui integralmente o `infinispan.xml` da imagem, foi necessário redeclarar o `<security-realm name="default"><properties-realm/></security-realm>`; sem ele a inicialização falha com `ISPN080014: Unknown security domain 'default'`. O usuário `admin` é criado pelo *entrypoint* da imagem (env `USER`/`PASS`) em `users.properties`.

Critério de aceite verificado: `GET /rest/v2/cache-managers/default/health` retorna `health_status: HEALTHY` e `number_of_nodes: 3`, com os *caches* `sessions` e `counters` HEALTHY.

## Mapeamento Configuração ↔ Tabela 1

| Linha Tx | Onde realiza | Trecho relevante |
|---|---|---|
| **T1** Modo de replicação | `cluster/infinispan-cluster.xml` | `<distributed-cache name="sessions" mode="SYNC" ...>` (cenário-alvo `ASYNC` será introduzido em B-03 como segundo *cache* ou perfil) |
| **T2** Número de nós: 3 | `cluster/podman-compose.yml` | Serviços `isn1`, `isn2`, `isn3` |
| **T3** `numOwners` = 2 | `cluster/infinispan-cluster.xml` | `owners="2"` em ambos os *caches* |
| **T4** `numSegments` | `cluster/infinispan-cluster.xml` | `segments="64"` (valor herdado do esqueleto; T4 da tabela cita 256 como padrão Infinispan; o esqueleto adota 64 para reduzir tempo de *state transfer* em ambiente com três nós — desvio justificado a registrar em `docs/decisoes-tecnicas.md` em B-04 ou ajustar para 256 se o autor preferir aderência estrita à Tabela 1) |
| **T15** MTU | herdado da configuração de rede do Podman (*bridge* default 1500) | sem ajuste explícito; equivale ao parâmetro T15 |
| **T16** Detecção de falha FD\_ALL3 | herdada do *stack* JGroups (`cluster/jgroups-tcp.xml`, base `default-jgroups-tcp.xml` com MPING→TCPPING) | `<stack-file name="cluster-stack" path="jgroups-tcp.xml"/>`; o protocolo `FD_ALL3` mantém os defaults do *stack* base |
| **T17** Falha F1 (*crash*) | `cluster/podman-compose.yml` define os *containers*; o *crash* será injetado por `scripts/inject-crash.sh` em B-11 | — |
| **T19** Métricas coletadas | `cluster/infinispan-cluster.xml` | `statistics="true"` em `cache-container` e em cada *cache* |
| **T20** Endpoint OpenMetrics | nativo do Infinispan 15 | endpoint exposto por `endpoints socket-binding="default"` |

## Partition Handling

A configuração adota `<partition-handling when-split="DENY_READ_WRITES" merge-policy="PREFERRED_NON_NULL"/>`. Sob partição, a partição minoritária recusa leituras e escritas, e a reconciliação após o merge prefere o valor não-nulo mais recente. Essa escolha alinha-se com o escopo de TCC-I: F2 (partição) está reservado para TCC-II (Decisão 011 ponto 11) e, portanto, esta política funciona como salvaguarda passiva caso o experimento gere partição involuntária.

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
| Perfil `DIST_ASYNC` | B-03 (próximo) | `feature/cluster-async` |
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
curl -s --digest -u admin:infinispan \
  http://localhost:11222/rest/v2/cache-managers/default/health/status
# esperado: HEALTHY

# Formação do cluster (cluster_size = 3):
curl -s --digest -u admin:infinispan \
  http://localhost:11222/rest/v2/cache-managers/default/health
# esperado: "health_status":"HEALTHY","number_of_nodes":3
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

## Decisões a registrar em `docs/decisoes-tecnicas.md`

- T4: aceitar `numSegments=64` do esqueleto ou ajustar para 256.
- Persistir credenciais `admin/infinispan` por enquanto; revisar para variável de ambiente em B-05.
