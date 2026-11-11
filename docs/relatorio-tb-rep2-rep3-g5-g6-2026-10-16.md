# T-B2: REP=2 do G-4 com sonda Hot Rod; REP=3, G-5 e G-6 NAO executados - 2026-10-16

Worker-2, branch `test/b29-gate-vm`, worktree `wt-tb`. Sem push. Go do Gestor para quatro execucoes em ordem (REP=2, REP=3, G-5, G-6). **Executada apenas a REP=2.** Parei depois dela pela regra de parada (e): o cluster so voltou a size=3 HEALTHY 188 s depois do fim da janela, contra ~46-48 s da REP=1 (limite combinado: 3x t_merge, ~150 s). REP=3, G-5 e G-6 **nao foram executados**, nada foi repetido, L-0/L-1/netdegrade/perda de pacotes nao foram tocados. O Senior valida (V-B2); nada aqui se autoaprova.

## 1. O que foi feito na VM

- Bundle `tmp/vm-bundle-2`, HEAD da VM `544879150d70e146d4b3f5a4847c13d8f824f5b9` (confere com `go.log`); jar sombreado de sha256 `c58c8b5e...6dd61c` (`resumo.txt`); `java` 17.0.19.
- Pre-voo (somente leitura): 3 portas `HEALTHY`, `number_of_nodes":3`; `qdisc noqueue 0: root refcnt 2` nos tres netns (pids 4111/4247/4656, todos diferentes do netns do host `net:[4026531840]`); 0 timers `tcc-heal`; sem tmux; lock `/var/lock/tcc-vm.lock` livre.
- **Passo extra, declarado:** antes do Go da REP=2, um teste de fumaca da sonda (nao e injecao) contra isn3 no cluster saudavel: `SondaHotrod --no isn3=127.0.0.1:11224 --caches sessions,counters --duracao-s 6`; 18 linhas, todas `OK`, `remove-final` OK; depois `keys` REST de `sessions` e `counters` = `[]`. Motivo: era a primeira vez que a sonda falava com um Infinispan real e a REP nao podia ser repetida.
- Execucao: `tmux` sessao `g1b` (sem flock; `REP=2 bash runs/gate-b29/g1b.sh | tee g1b-rep2.log`) iniciada antes; sessao `g-gate` com `flock /var/lock/tcc-vm.lock bash` (so o `g1.sh` sob flock, como propos o preparo), `tmux pipe-pane` para `g1-console-rep2.log`; `REP=2 bash runs/gate-b29/g1.sh`; o `GO` foi entregue por `tmux send-keys` (autorizado pelo Gestor): `go 2026-10-16T06:48:16+00:00 ... sha=544879150d70...`. Guarda de netns (`pid_de`) e dead-man `tcc-heal-79752-18554` (180 s) armados antes da injecao e desarmados no fim limpo.
- Depois: `injetor rc=0`; `exit` do shell tmux (lock liberado); `tc qdisc show` = `noqueue` sem clsact nos tres nos; 0 timers; sem tmux, flock, sonda, injetor; `keys` de `sessions` e `counters` = `[]` (o `remove-final` falhou por `InterruptedException`/timeout no SIGTERM, mas nao deixou chaves `sonda-*`); 3 portas `HEALTHY` x3.

## 2. Linha do tempo REP=2 (UTC; marcadores em epoch ns)

| Evento | Hora | Delta |
|---|---|---|
| `event=start` (`1792133297309780433`) | 06:48:17.310 | 0 |
| isn1/isn2 ISPN000094 `[isn2-19096\|5] (2)` | 06:49:02.320 / .316 | **t_split(maioria) = 45,0 s** (REP1: 46,4) |
| isn3 ISPN000094 `[isn3-5889\|5] (1)` + `DEGRADED_MODE` (`sessions`, `counters`, topology 23) | 06:49:03.411-.414 | **t_split(isn3) = 46,1 s** (REP1: 39,6) |
| `event=end` (`1792133393351002900`) | 06:49:53.351 | +96,04 s |
| isn1/isn2/isn3 ISPN000093 `MergeView::[isn2-19096\|6] (3)` | 06:50:38.319-.320 | **t_merge(vista) = 45,0 s** (REP1: 46,4) |
| isn3: fim do rebalance `sessions` / `counters` | 06:52:17.949 / 06:52:29.996 | +144,6 s / +156,6 s |
| isn3: fim do rebalance dos caches internos (`CONFIG`, `COUNTER`, `___protobuf_metadata`, `___hotRodTopologyCache`, `CLIENT_SERVER_TX_TABLE`) | 06:53:02.097-.112 | +188,7 s |
| 3 portas `HEALTHY` com 3 nos (`t_merge.txt`) | ~06:53:01 | **`t_merge_s=188`** (REP1: 48) |

Entre a vista fundida e o `HEALTHY` ha ~143 s de `HEALTHY_REBALANCING` (lido em 11222/11223; em 11224 o REST so respondeu de forma intermitente ate ~06:52:30, ver 3.4). Os inicios de rebalance de `sessions` (06:50:38.397) e `counters` (06:51:38.331) em isn3 distam exatos 60 s um do outro. Nao ha como saber, com estes brutos, o que segurou o rebalance.

## 3. Criterios (PASS/FAIL com evidencia)

### 3.1 G-1 (a particao morde): PASS
`janela.log`, contadores por filtro (offset 12 = ingresso/src, 16 = saida/dst; 2 filtros por sentido, isn1 e isn2):

| t | egress dst (isn1/isn2) | ingress src (isn1/isn2) |
|---|---|---|
| +8 s | 21 / 60 | 8 / 5 |
| +25 s | 75 / 100 | 10 / 8 |
| +45 s | 113 / 133 | 11 / 9 |
| +65 s | 129 / 160 | 37 / 11 |
| +82 s | 134 / 160 | 38 / 12 |

Os dois sentidos > 0 em todas as amostras. Final (`inj.log`): `ingress=51 pkt, egress=301 pkt` (somas por sentido da correcao `fix/contadores`; antes da correcao o resumo do injetor era cego a assimetria). `isn3 -> isn1:7800` `rc=124` nas 5 amostras; `isn1 -> isn2:7800` `rc=0` nas 5.

### 3.2 G-2 (limpeza, exit 0): PASS
`injetor rc=0`; `OK: F2 (particao) injetada por 90s em isn3 e removida; sem clsact residual`; `ok sem clsact em isn3`; `noqueue` nos tres nos, 0 timers, tmux e lock livres.

### 3.3 G-3 (vistas/DEGRADED): PASS (com a ressalva de 3.4)
Vistas e DEGRADED como na tabela da secao 2. Saude (`janela.log`/`indice.csv`, 11222): `HEALTHY`, `number_of_nodes":2` desde +45 s (`saude-11222-...410215992871.json` completo: `cluster_health.health_status HEALTHY`, `node_names [isn2-19096, isn1-47759]`, 4 caches `HEALTHY`).

### 3.4 JSON de saude completo em isn3 durante a particao: **FAIL (nao capturado)**
Das 60 coletas de 11224, **so 8 devolveram HTTP 200** (todas ate +40,8 s, antes da vista); **52 deram `000` (timeout de 4 s)**, de +24,7 s ate o fim da coleta (o `curl` do proprio `g1.sh` em `janela.log` tambem deu `000` a +45 s e +65 s e `401` a +82 s). Nenhum JSON `DEGRADED`/`number_of_nodes":1` de isn3 foi gravado. Isto **diverge da REP=1**, em que o REST de isn3 respondeu `200` em ~4 ms em todas as amostras e mostrou `DEGRADED`/1. O primeiro 000 de 11224 e um `get-availability` de `counters` em +8,7 s. Ao fim, varias requisicoes REST pendentes em isn3 foram liberadas em bloco em 06:52:29.996 (`log`), o mesmo instante do `Finished rebalance` de `counters`. Hipotese (nao provada): a coleta extra do `g1b.sh` (`/caches`, `?action=stats`, `?action=get-availability`) ou as escritas da sonda seguraram o REST/rebalance de isn3; a REP=1, que so lia `/health`, nao teve isso. Falta uma REP sem sonda ou so com a sonda para separar as causas.

### 3.5 G-4 (fusao): **FAIL na medida de tempo (1 de 1 janela fora do limite)**
Vista fundida em 45,0 s (igual a REP1), mas `HEALTHY` x3 so em **188 s** (REP1: 48 s; limite de parada 150 s). Reformou sozinho, sem intervencao e sem tc residual, mas fora do tempo razoavel; por isso parei. Sem REP=3 nao ha 3 janelas comparaveis.

### 3.6 G-1 "Hot Rod a isn3 devolve erro, nao timeout": **FAIL parcial**
Sonda em isn3 (98 linhas, 82 `ERRO`): nas primeiras +46 s e em todo o `counters` e **timeout** (`TransportException` com causa `SocketTimeoutException ... timed out after 2000 ms`, duracao ~4,0 s = 2 tentativas, `maxRetries 1`; `entrySet`: `RuntimeException` ~2,0 s). Erro **imediato** (0,00 s) so em `sessions`, de +59,3 s ate +131,6 s, na forma abaixo. Isso vale para uma chave unica e `maxRetries 1`.

## 4. Excecoes reais do cliente Hot Rod 15 (para o C-2; `@Disabled` NAO ligado)

| Fase | Operacao | Classe | Mensagem (resumo) | Causas | Duracao |
|---|---|---|---|---|---|
| Janela antes da vista (isn1 e isn3, a partir de +1 s) | `put`/`get`/`remove` em `sessions`/`counters` | `org.infinispan.client.hotrod.exceptions.TransportException` | `java.net.SocketTimeoutException: PutOperation{sessions, key=sonda-isn3, ..., connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms` (`GetOperation`, `RemoveOperation` idem) | `java.net.SocketTimeoutException: <mesma mensagem>` | ~4,0 s |
| idem | `entrySet` | `java.lang.RuntimeException` | `java.net.SocketTimeoutException: IterationNextOperation{sessions, flags=0, connection=...:11224} timed out after 2000 ms` | `SocketTimeoutException` | ~2,0 s |
| isn3 DEGRADED, `sessions` | `put`/`get`/`remove` | `org.infinispan.client.hotrod.exceptions.HotRodClientException` | `org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[ACED0005...sonda-isn3 (17 bytes)]' is not available. Not all owners are in this partition` | vazio | 0,00 s |
| isn3 DEGRADED, `sessions` | `entrySet` | `HotRodClientException` | `org.infinispan.partitionhandling.AvailabilityException: ISPN000305: Cluster is operating in degraded mode because of node failures.` | vazio | 0,00 s |
| isn3 DEGRADED, `counters` | todas | `TransportException`/`RuntimeException` (timeout, como acima) | nao houve `AvailabilityException` em `counters` | | 4,0 s / 2,0 s |
| Pos-fusao (rebalance, isn1 **e** isn3, +145 s a +205 s) | `put`/`remove` | `TransportException` (timeout); `get` e `entrySet` OK | idem | | 4,0 s |
| SIGTERM da sonda (+205-209 s) | `remove`, `remove-final` | `HotRodClientException` com `java.lang.InterruptedException` | artefato do encerramento da sonda, **nao** e erro do cluster | | 3-4 s |

Contagem (isn3): 82 `ERRO` em 98 linhas; (isn1, o no majoritario): 27 `ERRO` em 266; os erros de isn1 estao em +1,1 a +37 s (escritas sobre chave cujo dono esta do outro lado, ate a vista nova) e em +145 a +209 s (rebalance). Tambem em isn1 a escrita falha enquanto o corte nao e detectado, e **nao** so no no isolado.

## 5. Divergencias declaradas da sonda

- `maxRetries` 1 na sonda contra 3 do workload: a duracao ~4,0 s da sonda subestima a do workload.
- Chave unica por no (`sonda-isn1`, `sonda-isn3`); `OK` nao prova ausencia de erro em outras chaves.
- Cliente `BASIC` pinado a um servidor, nao o de topologia dos clientes S1/S2.
- Dados falsos `sonda-*` nos caches `sessions` e `counters` (este com `IdentityState` falso): removidos pelo `remove-final`; confirmado `keys` = `[]` nos dois ao fim.
- Sonda e coleta (`/caches`, `?action=stats`, `?action=get-availability`) estavam **ligadas**; sao uma perturbacao possivel (3.4) e a REP=1 nao as tinha.
- `g1b.sh` rodou sem `flock`; so o `g1.sh` ficou sob o lock.

## 6. G-5 e G-6: nao executados; scripts escritos (nao testados na VM)

Escritos em `runs/gate-b29/`: `g56-lib.sh` (`exigir_go`, `preflight`, `espera_saudavel`, `armar_netem`, `espera_marcador`), `g5.sh` (TERM e depois KILL, janela de 40 s, sinal em +15 s, dead-man armado por fase, `--heal` manual na fase KILL, trap EXIT com `--heal` em falha) e `g6.sh` (F3 em isn2 130 s e F2 em isn3 90 s, dois dead-men, limpeza de cada uma verificada, trap EXIT). Verificados so com `bash -n` e `--dry-run`; **sem teste com shims e sem envio a VM** (nao havia go para continuar e portanto nao ha bundle/sha256). Observacoes de desenho: `inject-jitter.sh` nao tem `--direction` (o `netem` de raiz ja e de saida); `inject-jitter.sh` nao tem guarda de netns propria, entao o `g6.sh` aplica `pid_de` antes; `inject-netdegrade.sh` nao e usado.

## 7. O que a REP=2 NAO prova

Uma janela; nenhuma carga de aplicacao; nada sobre divergencia ou perda de dados no merge; nao separa o efeito da sonda do efeito da coleta REST; nao diz por que o rebalance durou ~143 s apos a vista fundida; o t_split(isn3) de 46,1 s contra 39,6 s da REP=1 nao permite estimar variancia com duas amostras e configuracoes diferentes; nada de G-5/G-6; nao mostra o JSON de saude de isn3 em DEGRADED (so o da REP=1).

## 8. Para o Senior (V-B2) e o Gestor

1. Decidir se a coleta de `g1b.sh` deve perder `?action=stats`/`get-availability`/`/caches` (so `/health` + sonda) antes de uma nova REP, ou se a REP=3 sai so sem sonda para separar as causas. Qualquer repeticao exige novo go.
2. G-5 e G-6 seguem pendentes e precisam de go novo; antes, o Senior revisa `g5.sh`/`g6.sh`.
3. Verificar se o rebalance de ~143 s e inerente (dados) ou consequencia das escritas pendentes da sonda.
