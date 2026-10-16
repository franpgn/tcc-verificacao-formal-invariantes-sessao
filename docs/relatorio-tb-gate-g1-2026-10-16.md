# T-B: gate B-29, G-1 (1 particao real de 90 s em isn3) — 2026-10-16

Worker-2, branch `test/b29-gate-vm`, worktree `wt-tb`. Sem push. Autorizado pelo Gestor: G-1 (uma unica execucao, `REP=1`) e as observacoes de G-2/G-3/G-4 da mesma injecao. **G-5 (`kill -KILL`) e G-6 (coexistencia com F3) NAO foram executados; G-1 NAO foi repetido.** Brutos em `runs/gate-b29/g1-rep1/` (`inj.log`, `janela.log`, `marcadores.txt`, `log-completo-isn{1,2,3}.txt`, `saude-*.txt`, `t_merge.txt`), `go.log`, `g1-console-rep1.log`.

## 1. Preparo

- Bundle novo do worktree temporario `wt-tb-tmp` (`tmp/tb-vm-bundle`, nao integrar): merge de `test/b29-gate-vm` (`dd06d7a`, com `g1.sh`) sobre o tip com `.gitattributes` = **`4978e1c6ee634d59ba48927773f496d7bf51b3ee`**. sha256 do bundle `0ecc9621…d9d48` igual no host e na VM. Clone `~/tcc-gate` em `HEAD` = `4978e1c`; `grep -rlI $'\r' scripts cluster spec runs/gate-b29/*.sh | wc -l` = **0**. Os artefatos untracked da fase 1 foram movidos para `~/gate-b29-fase1-bkp` e os `.log` recopiados.
- Cluster antes (curl `--digest`): 11222/11223/11224 = `HEALTHY`, `number_of_nodes":3`; sem clsact nos tres; 0 timers; sem tmux.
- Execucao: `tmux new -d -s g-gate "flock /var/lock/tcc-vm.lock bash"` (o `flock` envolve o shell do tmux, equivalente a `flock … tmux` mas destacavel), `REP=1 bash runs/gate-b29/g1.sh` e o `GO` entregue por `tmux send-keys` (`go.log`: `go 2026-10-16T05:11:03+00:00 … sha=4978e1c…`). Guarda de netns ok (`pid_de`), dead-man `tcc-heal-11929-17523` armado para 180 s **antes** da injecao e desarmado ao fim limpo.

## 2. Linha do tempo (UTC; `marcadores.txt` em epoch ns)

| Evento | Hora | Delta |
|---|---|---|
| `event=start` (`1792127463784986725`) | 05:11:03.785 | 0 |
| isn3: ISPN000094 vista `[isn3-5889\|3] (1)` | 05:11:43.370 | **t_split(isn3) = 39,6 s** |
| isn1/isn2: ISPN000094 `[isn2-19096\|3] (2)` | 05:11:50.198 / .207 | **t_split(maioria) = 46,4 s** |
| `event=end` (`1792127559827315241`) | 05:12:39.827 | +96,04 s (90 s + sondas/verificacao) |
| isn1/2/3: ISPN000093 `MergeView::[isn2-19096\|4] (3)` | 05:13:26.210–.216 | **t_merge(vista) = 46,4 s** |
| 3 portas HEALTHY com 3 nos (`t_merge.txt`) | — | `t_merge_s=48` (granularidade de 2 s do polling) |

## 3. Resultado por criterio

**G-1 (a particao morde, bidirecional e so no par certo): PASS.**
- Contadores por filtro (offset 12 = src/ingresso, 16 = dst/saida), 2 filtros por sentido (isn1, isn2): t=+8 s `egress 15/22, ingress 5/5`; +25 s `29/30, 8/8`; +45 s `37/48, 9/9`; +65 s `42/52, 19/11`; +82 s `48/53, 20/11`. Final (`inj.log`): egress 53+53, ingress 21+12 (somados = 139 = o "ingress=139, egress=139" do injetor). **Ambos os sentidos > 0 em todas as 5 amostras.**
- `isn3 -> isn1:7800`: `rc=124` (timeout) nas 5 amostras; `isn1 -> isn2:7800`: `rc=0` nas 5. Conexoes TCP de isn3 em `ss`: 3 em t=+8 s (uma com `Send-Q 178` rumo a isn1:7800), 2 em +25 s, 1 depois (so a entrada `10.89.1.4:57800 <- 10.89.1.2:58565`, ocioso).
- Host -> isn3 `:11224` (REST): `http=200 t≈0,004 s` nas 5 amostras; o u32 nao atingiu o trafego do host (10.89.1.1) nem SSH.
- Achado confirmado: `tc filter show … ingress` e `… egress` listam os dois filtros; o resumo do injetor (`139/139`) e portanto cego a assimetria. O `cont_filtros` por offset funcionou como mitigacao.

**G-2 (limpeza, exit 0): PASS.** `injetor rc=0`; `OK: F2 (particao) injetada por 90s em isn3 e removida; sem clsact residual`; apos: `qdisc noqueue 0: root refcnt 2` em isn1, isn2, isn3 e `tc filter show dev eth0 ingress` = 0 linhas; 0 timers `tcc-heal-*`; tmux encerrado, lock liberado.

**G-3 (vistas/DEGRADED): PASS.** Nomes reais (ver sec. 4). Amostras de saude: ate +25 s `HEALTHY/3` nas tres portas; em +45, +65 e +82 s: **11222 e 11223 `HEALTHY`, `number_of_nodes":2`; 11224 (isn3) `DEGRADED`, `number_of_nodes":1`**. isn3 loga `ISPN100011: Entering availability mode DEGRADED_MODE` para `sessions` e `counters` (05:11:43.389); isn1/isn2 mantem `AVAILABLE`.

**G-4 (fusao): PASS (1 janela; o plano pede >= 3 para a estatistica).** Com t_merge ≈ 46,4 s (vista) / 48 s (saude); depois `HEALTHY` x3 e `number_of_nodes":3` (`saude-depois.txt`). Nenhuma intervencao; dead-man nao disparou.

## 4. Mensagens reais (alimentam RAST-G3-1: ClassificadorDeErro / HealthRest)

| Codigo / logger | Texto (resumo) | Onde / uso |
|---|---|---|
| `ISPN000094` `o.i.CLUSTER` | `Received new cluster view for channel cluster-tcc: [isn3-5889\|3] (1) [isn3-5889]` | divisao: vista de 1 (isn3) e de 2 (`[isn2-19096\|3] (2)`) |
| `ISPN000093` | `Received new, MERGED cluster view … MergeView::[isn2-19096\|4] (3) …, 2 subgroups: …` | fusao (as 3 pontas) |
| `ISPN100011` | `Entering availability mode DEGRADED_MODE, topology id 11` (isn3); `… AVAILABLE, topology id 10` (isn1/2) | minoria entra em DEGRADED por cache |
| `ISPN000319` (**ERROR**) | `After merge (or coordinator change), cache still hasn't recovered all its data and must stay in degraded mode` | isn3, na **divisao** (nao na fusao), `sessions` e `counters` |
| `ISPN100007` | `After merge (or coordinator change), recovered members […3] with topology id 10` | isn3, **tambem na divisao** (rotulo "After merge" enganoso: e o handler de vista) |
| `ISPN100008` / `ISPN100002` / `ISPN100010` | `Updating cache members list`, `Starting rebalance … READ_OLD_WRITE_ALL`, `Finished rebalance` | reconfiguracao pos-vista e pos-fusao (topology 17/18) |
| `ISPN000517` (WARN) | `Ignoring cache topology from [isn3-5889] during merge: CacheTopology{id=11…}` | isn1/2, na fusao (topologia da minoria descartada) |
| `JGRP000011` (WARN) | `isn3-5889: dropped message from non-member isn2-19096 (view=[isn3-5889\|3] (1))` | isn3, no instante da fusao |
| `JGRP000006` (WARN) | `failed accepting connection from peer … java.io.EOFException` | **artefato da nossa sonda TCP** isn1->isn2:7800 (aparece em isn2 a cada amostra: 05:11:14, :31, :51, 05:12:11, :28); nao e falha do cluster |
| `ISPN012026` (WARN) | `REST invocation … /health … has been deprecated` | ruido: o healthcheck e o curl usam endpoint depreciado; filtrar |

Implicacoes: para o ClassificadorDeErro a assinatura da minoria e `DEGRADED` + `number_of_nodes":1` no REST e `ISPN100011 … DEGRADED_MODE` (nao ha erro de transporte do host ate isn3); o HealthRest deve ler `health_status` **e** `number_of_nodes`, pois a maioria segue `HEALTHY` com 2 nos. As portas Hot Rod/REST de isn3 respondem durante a particao.

## 5. Defeitos/observacoes sem impacto no resultado

1. `g1.sh` extrai `log-vista-*.txt` logo apos o injetor, antes da fusao; por isso nao contem `ISPN000093`. Os `log-completo-*.txt` (extraidos depois, sem cores ANSI) cobrem a fusao.
2. `inject-partition.sh` congela `TIMESTAMP` na linha 64: todos os `[…]` do `inj.log` mostram `05:11:03Z`, inclusive `del clsact` (~05:12:39). Os marcadores em epoch ns estao corretos.
3. `inject-partition.sh` continua sem guarda de netns propria (achado 6.2 do relatorio G-0) e o `soma_pkts` cego a sentido (6.1); correcoes em `fix/b29-contadores-por-sentido`, se o Senior quiser.

## 6. O que o gate NAO prova

Uma unica janela: nao da media nem variancia de t_split/t_merge (G-4 exige >= 3). Nao testa falha do proprio injetor (`kill -KILL`, G-5) nem coexistencia com netem de F3 (G-6). Nao exercita carga: o cluster estava ocioso (sem escrita em `sessions`), logo nada prova sobre divergencia de dados, reconciliacao ou perda de escrita no merge (ISPN000517 descarta a topologia da minoria; efeito sobre dados nao medido). Particao apenas de isn3 (minoria de 1); sem teste de particao 2x1 com outro isolado, sem split simetrico sem maioria. t_split e dominado pelos timeouts do FD/JGroups da configuracao atual, nao e propriedade do injetor.

## 7. Proposta para G-5 / G-6 (nao executados)

- **G-5:** `kill -TERM` e depois `kill -KILL` do injetor durante uma janela curta (duracao 30–40 s, dead-man armado antes, `--heal` manual), confirmando que o `trap` limpa no TERM e que apos KILL o clsact permanece ate `--heal`/dead-man (isn3 cortado); evidencias: `tc qdisc` antes/depois do `--heal`, rc, reforma size=3. Precisa de novo "go".
- **G-6:** so apos G-5 e com o Gestor ciente de F3: nao executar `inject-partition.sh` e `inject-netdegrade.sh` ao mesmo tempo no mesmo netns; verificar que o `flock /var/lock/tcc-vm.lock` serializa e que o `del clsact` da particao nao remove o prio/netem de F3.
- Antes: decidir se g1.sh ganha extracao de logs pos-fusao (sec. 5.1) e se G-4 faz `REP=2,3` (cada repeticao e outra particao real: precisa de go explicito).
