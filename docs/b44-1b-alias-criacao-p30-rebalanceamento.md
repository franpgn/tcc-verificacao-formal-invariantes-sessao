# B-44.1b — Aliases, criação de cache, contornos do P-30 e teto do rebalanceamento desligado

Data: 2026-11-04/05. Branch `docs/b44-1-reconfiguracao-infinispan` (continuação de `8e57ce7`). Pedido do
coordenador depois do B-44.1 (`docs/b44-1-reconfiguracao-infinispan.md`), porque AC-1 caiu e AC-3 diverge
réplicas. Verificação, não atuador. *Cluster* local Podman `b441` (portas 12441..12443, imagem
`quay.io/infinispan/server:15.0.21.Final`), sem VM, sem push. A fase foi interrompida às 22:18 UTC de
04/11 para o bloco S5 da VM (cluster e máquina Podman parados) e retomada às 01:40 UTC de 05/11 para o
item 4.

Brutos em `runs/b44-1b/` (`MANIFEST.sha256`). Funções e comandos em `runs/b44-1b/scripts/lib.sh`; fases e
instantes em `fases.tsv`; todas as chamadas administrativas em `chamadas.tsv` (corpos em `corpos/`);
latências resumidas em `latencias.tsv`; marcadores de reinício/rebalanceamento por fase em
`logs-por-fase.tsv` (mesmo método do B-44.1 §2.1).

Encarnações do *cluster*: INCA (XML da base; recriada porque os aliases ficaram inconsistentes), INCA2
(XML da base), INCB e INCB2 (XML **sem caches**, `cluster/infinispan-sem-caches.xml`, caches criados pela
REST com `scripts/cria-caches.sh`).

## 1. Resposta curta

| Item | Resultado |
|---|---|
| 1. Alias como atuador (trocar SYNC/ASYNC por alias) | **Não serve.** Hot Rod 15.0.21 não resolve alias; a REST resolve mas guarda a resolução e continua no cache antigo depois da troca; um alias não pode ter o nome de um cache existente; a troca exige duas chamadas não atômicas; uma rejeição deixa nós inconsistentes |
| 2. Criar/remover cache pela REST | **Funciona.** Criar 45 a 144 ms (uma criação levou 8,0 s, n = 1); remover 24 a 37 ms; rebalanceia só o cache novo (vazio, ~20 ms); caches criados pela REST reiniciam sem `ISPN000438` |
| 3. Contornos do P-30 | (a) limpar `caches.xml` sozinho: só funciona se o *cluster* estiver com os valores do XML. (b) XML sem caches + criação pela REST: preserva o F1 com um único `podman start` **se nenhum atributo mudou durante a queda**; se mudou, o 1º `start` falha (`ISPN000500`, 3 de 3) e o 2º funciona. (b)+(a): funciona com um único `start` mesmo com mudança durante a queda (1 de 1) |
| 4. Teto do rebalanceamento desligado | Com um nó fora, ~2/3 das chaves (1986 a 2053 de 3000) ficam com uma cópia só, durante todo o desligamento (87 s medidos); religar recupera em 35 a 130 ms. **O desligamento por cache se perde quando o coordenador cai** (o novo coordenador rebalanceou em 4,6 s); o global sobrevive. Teto proposto: 60 s (= `state-transfer.timeout`), usando a forma **global** |

## 2. Item 1: aliases

### 2.1 Fonte

- `Configuration.ALIASES` sem `.immutable()`, `since(15, 0)` (`core/.../configuration/cache/Configuration.java:27-30`);
  documentação *Configuring caches* 15.0, §3.3 *Cache aliases*: "The aliases attribute of a cache
  configuration is updatable at runtime. Reassign aliases to different caches to quickly switch cache
  contents." Não existe ação REST própria (`assign-alias` não está em `CacheResourceV2.java` da 15.0.21); a
  mudança é por `set-mutable-attribute&attribute-name=aliases` (ou PUT da configuração).
- Resolução: `ConfigurationManager.selectCache` (linhas 159-161) mapeia alias → cache; `addAliases`
  (134-151) recusa alias igual a nome de cache (`duplicateCacheName`, `ISPN000438`) ou alias já usado
  (`ISPN000975`); `AliasListener.attributeChanged` (163-180) adiciona os novos e **remove os antigos pelo
  nome, sem conferir a que cache pertencem** (`removeAliases`, 153-157).
- **Hot Rod:** `HotRodServer.checkCacheIsAvailable` (`server/hotrod/.../HotRodServer.java:429`) recusa todo
  nome que não está em `cacheManager.getCacheNames()`, que não inclui aliases (`CacheNotFoundException`).
- **REST:** `RestCacheManager.getCache` (`server/rest/.../RestCacheManager.java:96-101`) guarda o cache
  resolvido em `knownCaches` pelo nome pedido; só sai de lá quando o cache é removido (`resetCacheInfo`, 236-238).

### 2.2 Prova

| Passo (INCA/INCA2) | Resultado |
|---|---|
| `aliases=sessions` em `sessions-async` | 400 `ISPN000438 Cache with name 'sessions' is defined more than once`; o atributo ficou `[sessions]` em `isn2`/`isn3` e `[]` em `isn1` |
| `aliases=sess-ativo` em `sessions-async` | 200, `[sess-ativo]` nos 3; PUT REST pelo alias nos 3 nós → grava em `sessions-async` |
| `aliases=sess-ativo` também em `sessions` | 400 `ISPN000975`; atributo `[sess-ativo]` em `isn1`/`isn2` (parcial) |
| limpar `aliases` de `sessions` (valor vazio) | 200; **o alias deixou de resolver em `isn1` e `isn2`** (`ISPN012010 Cache with name 'sess-ativo' not found`), em `isn3` continuou em `sessions-async`. O valor vazio vira um alias `""` (`ISPN000975: The alias '' is already being used`). Irreparável pela API: *cluster* recriado |
| INCA2, troca limpa: `sessions-async aliases=sess-inativo` (200, 30 ms) e `sessions aliases=sess-ativo` (200, 32 ms), leitura de volta certa nos 3 | REST pelo alias **continua gravando em `sessions-async`** nos 3 nós (`efeito-rest-alias.txt`) |
| Cliente Hot Rod (`scripts/alias-cliente.jsh`, 3 clientes fixados, um por nó), 40 s com a troca no meio | 1842 de 1842 operações falharam: `getCache("sess-ativo")` devolve `null` antes e depois da troca. Cliente novo depois da troca: 192 de 192 falharam. `scripts/alias-sonda.jsh`: `sessions-async`/`sessions` → ok; `sess-ativo`/`sess-inativo` → `null` |

Nenhuma mudança de alias reiniciou ou rebalanceou cache (`logs-por-fase.tsv`, fases A1/A2 sem marcadores).
A carga S3 async de A2 (80 s) saiu com 0 e registrou 10 `I2_CASCATA_INCOMPLETA`; não liguei isso à troca
(o S3 usa `sessions-async` pelo nome, e no B-44.1 P6c o mesmo perfil deu 0), e fica sem explicação.

**Consequência:** a troca de política de replicação por alias não existe para o cliente Hot Rod e, pela
REST, não acontece depois da primeira resolução. Fora do espaço de ação.

## 3. Item 2: criação e remoção de cache pela REST

| Operação | Latência | Efeito |
|---|---|---|
| `POST /rest/v2/caches/sessions-sync2` (SYNC, mesma configuração do ASYNC, permanente), INCA2, sob S3 sync | **8,04 s** (n = 1; o cache só começou a subir 8 s depois do pedido; não investigado) | configuração idêntica lida nos 3 nós; rebalanceamento inicial só de `sessions-sync2` (vazio, ~20 ms); gravado em `data/caches.xml` |
| `POST .../sessions-sync3` com cabeçalho `flags: VOLATILE` | 45 ms | igual, mas **não** gravado em `caches.xml` |
| `DELETE .../sessions-sync2`, `.../sessions-sync3` | 37 ms, 24 ms | removidos dos 3 nós |
| INCB: os 4 caches da base pela REST | 144, 68, 79 ms; `counters` 67 ms (2ª forma) | — |

- A carga S3 sync de C1 (80 s) saiu com 0 e 0 violações; nenhum dos 4 caches existentes reiniciou ou
  rebalanceou.
- Pegadinha 1: `{"counters":{"distributed-cache":{...}}}` → 400 `ISPN029520 Invalid scope for tag
  <counter>` (o nome colide com o elemento `<counters>`). Funciona com o JSON sem o envoltório do nome
  (`{"distributed-cache":{...}}`), como faz `scripts/cria-caches.sh`.
- Pegadinha 2: SYNC criado pela REST sem `remote-timeout` fica com 15000; o mesmo cache vindo do XML fica
  com 17500. O script declara 17500 explicitamente.
- Reinício: em INCB, caches criados pela REST reiniciaram com `podman start`/`restart` sem `ISPN000438`
  (B2, B5, B8). Logo o P-30 vem só de caches **declarados no XML** e alterados depois.

## 4. Item 3: contornos do P-30

Injetor sem alteração: `scripts/inject-crash.sh --node b441-isnN --modo kill --duration D` (`podman kill`,
espera, `podman start` no mesmo container). O modo `silencioso` precisa de `sudo nsenter` no *host* e não roda
no Windows; o caminho de reentrada (`podman start`) é o mesmo.

| Caso | XML | Atributo mudado | Contorno | Resultado do `start` do injetor |
|---|---|---|---|---|
| F1a | base | sim (escorva/alias) | nenhum | `ISPN000438`, nó fora; **injetor sai com 0** |
| F1b | base | valores = XML | (a) `caches.xml` vazio copiado na janela | volta (`ok`) |
| F1c | base | `remote-timeout`=12000 | (a) | `ISPN000500`, nó fora |
| B2 | sem caches | `remote-timeout`=12000 antes da queda | nenhum | volta, 12000 nos 3 |
| B3, B6, B7 | sem caches | mudado **durante** a queda | nenhum | 1º `start`: `ISPN000500` (3 de 3); 2º `start`: volta com o valor novo (3 de 3) |
| B8 | sem caches | mudado durante a queda | (a) na janela | volta com um único `start`, valor novo nos 3 |

- Mecanismo do 2º `start` (B6): o *boot* que falha reescreve `caches.xml` sem o cache em conflito
  (`corpos/B6-caches-xml-isn3-apos-falha.xml` só tem `counters`); no boot seguinte não há definição local que
  conflite e o nó recebe a configuração do estado do *cluster*.
- Primeira mudança sem escorva em INCB (B1): 200 e uniforme nos 3 nós. O P-29 também some com (b).
- **Resposta:** só (b) preserva a recuperação do F1 sem mexer no injetor, e só se o controlador não mudar
  atributo enquanto um nó está fora. Para cobrir esse caso: ou o controlador congela mudanças de atributo
  enquanto houver membro ausente, ou o *runner* (não o injetor) limpa `caches.xml` na janela da queda (B8) ou
  dá um segundo `start` quando o nó sai. Em todos os casos o *runner* precisa conferir a saúde do nó depois
  do injetor, porque o injetor sai com 0 mesmo com o nó morto (F1a, F1c, B3).

## 5. Item 4: teto do rebalanceamento desligado

Método: INCB2, 3000 chaves em `sessions` e em `counters` (REST). `amostra` lê, a cada ~2 s por um nó vivo,
`size` (N), `?action=distribution` (cópias por nó) e calcula **cópias faltantes = 2N − Σ cópias** (com
`owners=2`, cada chave com uma cópia só conta 1). `counters` fica com rebalanceamento ligado, como
controle. Saída por `inject-crash.sh --modo kill --duration 60` (detecção pelo FD_SOCK2, ~3 s; no F1
silencioso seria pelo FD_ALL3, 46,8 s no B-44.1). Instantes de rebalanceamento pelos logs do coordenador.

| Execução | Quem sai | Desligamento | Durante a ausência | Quando o nó volta (rebal. ainda desligado) | Ao religar |
|---|---|---|---|---|---|
| R1 | `isn3` (não coord.) | por cache (`sessions`) | `sessions`: **1986/3000 faltantes**, `AVAILABLE`; `counters`: rebalanceou em 50 ms, 0 faltantes | `isn3` não recebe segmentos de `sessions`; faltantes continuam 1986 | `enable` 01:45:34,39 → rebalanceamento 34,595 → 34,630 (**35 ms**); 0 faltantes |
| R2 | `isn1` (**coordenador**) | por cache (`sessions`) | **o flag se perdeu**: o novo coordenador (`isn2`) recuperou a topologia (`ISPN100007`, 01:47:42,2) e `sessions` rebalanceou às 01:47:46,76; `rebalancing_enabled=True`; 2053 faltantes por ~5 s, depois 0 | normal (rebalanceamento ligado) | sem efeito |
| R3 | `isn2` (**coordenador**) | **global** | flag preservado (`rebalancing_enabled=False` nos nós vivos); `sessions` e `counters` com **2007/3000 faltantes** de 01:50:13,1 a 01:51:39,9 (**86,8 s**) | idem, sem segmentos novos | `enable` global 01:51:39,7 → `sessions` 39,730 → 39,860 (**130 ms**), `counters` idem; 0 faltantes |

- Fonte do R2: a recuperação de estado depois da troca de coordenador agrega só o flag **global** de cada
  nó (`ClusterTopologyManagerImpl.java:369`, `rebalancingEnabled &= nodeStatus.isRebalancingEnabled()`);
  `CacheStatusResponse` não leva o flag por cache (o flag fica no `ClusterCacheStatus` do coordenador,
  `ClusterCacheStatus.java:1094-1100`), então ele não é recuperado pelo novo coordenador. Vi isso uma vez (n = 1); o código explica.
- Nenhuma execução perdeu dados nem saiu de `AVAILABLE`: com `owners=2`, só uma segunda saída durante o
  desligamento deixaria segmentos sem dono. Esse é o risco que o teto limita.
- Custo da recuperação: 35 a 130 ms para 3000 entradas pequenas no *cluster* local. Não vale para a VM nem
  para o tamanho dos dados da T-F.

**Teto proposto (para o Senior):** desligar só pela forma **global**
(`POST /rest/v2/container?action=disable-rebalancing`), porque a forma por cache não sobrevive à queda do
coordenador. Tempo máximo desligado de **60 s = `state-transfer.timeout`** do XML. É um valor já
declarado, rastreável, e cobre um ciclo inteiro de detecção (46,8 s medidos no B-44.1 para F2; FD_ALL3 40 s
+ VERIFY_SUSPECT2 1 s pela configuração). Religar quando a topologia ficar estável ou ao fim dos 60 s, o
que vier antes. A exposição medida é ~2/3 das chaves com uma cópia só por todo o tempo desligado com um nó
fora. Com o teto, ela fica limitada a 60 s mais o rebalanceamento (< 1 s aqui). Proposta derivada de n = 1
por condição no *cluster* local, não de medição na VM.

## 6. Latência e autenticação

`latencias.tsv`. Todas as chamadas usam Digest `admin` (`AuthorizationPermission.ADMIN`, como no B-44.1 §7).
`set-mutable-attribute` (aliases) 30 a 37 ms; `disable-/enable-rebalancing` por cache 9 a 15 ms, global 50 a
60 ms; criação 45 a 144 ms (8,0 s uma vez); remoção 24 a 37 ms.

## 7. Limitações

- Uma execução por condição (exceto B3/B6/B7, 3 de 3). *Cluster* local; injetor no modo `kill`, não no
  `silencioso`.
- Não testado: alias com cliente REST novo em nó que nunca resolveu o alias (o cache de resolução é por nó);
  a causa dos 8 s da primeira criação; a causa das 10 `I2_CASCATA_INCOMPLETA` da carga A2.
- Ficaram processos `java.exe` (JDK 17, `timeout 600s`) de outra sessão no *host* durante o item 4; não são
  desta tarefa.

## 8. Pontos para validação do Gestor

1. Retirar a troca de política por alias do espaço de ação.
2. Adotar o contorno (b) (XML sem caches, criação pela REST com `scripts/cria-caches.sh`) na VM, mais uma
   destas regras: o controlador não muda atributo com membro ausente, ou o *runner* limpa `caches.xml` / dá
   um 2º `start` e confere a saúde depois do injetor.
3. AC-2 só na forma global, com teto de 60 s.
