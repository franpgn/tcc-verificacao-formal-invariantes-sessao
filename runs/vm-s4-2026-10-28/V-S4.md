# V-S4: validação do S4 (REP=4R) na VM, 2026-10-28

Validador: Senior. Li só os brutos copiados para o host (`vm-20261028T070600Z/`, `vm-20261028T063554Z.abortada-host-crash/`, `g1-rep4-copia/`) no worktree `wt-gate-v4` (`test/b29-gate-v4`, ponta `1266c12`). Fiz também uma leitura do log System do host Windows (`Get-WinEvent`, só leitura), gravada em `host-eventos-desligamentos.log`. Não acessei a VM, não rodei `mvn`, não houve push e não dei `go`.

Critérios: `docs/decisoes-senior-2026-10-16.md` §5.5 (linhas 439-454, itens 1 a 9; o arquivo está na branch `docs/decisoes-senior-2026-10-16`, cópia idêntica em `wt-b42-1/docs/`). A REP=4R é a definida em `docs/validacao-v-s1-2026-10-27.md` §5.2 (linhas 75-82). As diferenças declaradas estão em `ROTEIRO-S4.md:27-29`. A REP=3 de comparação está em `docs/relatorio-tb-v3-execucao-2026-10-16.md:24-66` e `runs/gate-b29/g1-rep3/`.

Abreviações de caminho: `T2/` = `vm-20261028T070600Z/`, `T1/` = `vm-20261028T063554Z.abortada-host-crash/`, `G/` = `g1-rep4-copia/`.

## Veredito

**APROVADO, com ressalvas.** A janela F2 em isn3 durou 96,040 s, o split apareceu nos dois lados, o `MergeView` de 3 membros está nos 3 logs e o `t_merge` foi de 44 s, abaixo do limite de parada de 60 s. A REP=3 deu 46 s. A primeira partição real no kernel 6.8.0-147 se comportou como no 6.8.0-124. Nenhuma ressalva invalida a REP. A única que pede ação antes de fechar o S4 é a R-1 (conferir na VM o sha256 da cópia de `T2/`).

A estabilidade do host é o risco principal daqui para a frente. O log do Windows tem **17** reinícios sem desligamento limpo desde 01/09, não 3 (seção "Host"). Esse risco não afeta a validade da REP=4R, mas pesa nas campanhas noturnas.

## Brutos e onde ficam versionados

| Caminho | Conteúdo |
|---|---|
| `T2/` | saída completa do `s4.sh` da tentativa 2 (30 arquivos, mais o `.gz` do journal: `00-pre.log` … `07-portao51-pos.*`, `rc.txt`, `go.log`, `g1-rep4.sha256`, `s4-console.log`, `pre-recriacao/`, `recriacao/`) |
| `T2/pre-recriacao/journal-boot-anterior.txt.gz` | `journalctl -b -1` do passo 1, comprimido com `gzip -9n`. O original, de 50 409 360 bytes e sha256 `983aa2b5…1237d03` (em `copia-host.sha256`), fica fora do git, no disco do host (ver P-3). O `.gz` tem sha256 `8e54b4f9…06c2`, e `gzip -dc` dele reproduz o sha do original |
| `T1/` | saída parcial da tentativa 1 (5 arquivos, 3 deles vazios) |
| `G/` | cópia de `~/tcc-gate/runs/gate-b29/g1-rep4/` da VM (124 arquivos) |
| `copia-host.sha256` | sha256 no host dos 35 arquivos de `T2/` e `T1/`, para conferir na VM (R-1) |
| `host-eventos-desligamentos.log` | Kernel-Power 41, EventLog 6008, WER 1001 e WHEA do host desde 01/08, mais CPU, placa e BIOS |

**Onde fica a cópia do `g1-rep4`.** Mantive em `runs/vm-s4-2026-10-28/g1-rep4-copia/`, e não em `runs/gate-b29/g1-rep4/`, que espelharia o caminho da VM. Sigo a convenção do S3, em que o que veio da VM fica dentro do diretório do bloco (`runs/vm-s3-2026-10-28/vm/l0c/` é a cópia de `runs/gate-netdegrade/l0c/` da VM, e `runs/gate-netdegrade/` só tem scripts). Assim um bloco = um diretório, e o `V-S4.md` cita tudo com caminho relativo. Também fica separado o que é bruto da VM nova (S4) do que o Worker copiou da VM antiga em 16/10 (`runs/gate-b29/g1-rep2/`, `g1-rep3/`). O preço é que o manifesto da VM usa o caminho `runs/gate-b29/g1-rep4/`. A conferência no host é:

```bash
cd runs/vm-s4-2026-10-28
sed 's#runs/gate-b29/g1-rep4/#g1-rep4-copia/#' vm-20261028T070600Z/g1-rep4.sha256 | sha256sum -c -   # 124 OK
```

Refiz essa conferência e deu 124/124 OK. Os 159 arquivos copiados não têm nenhum CR.

## Pré-condições (§5.5, linha 439, e ROTEIRO-S4)

| Pré-condição | Evidência | Resultado |
|---|---|---|
| Kernel e boot esperados | `T2/00-pre.log:6` (6.8.0-147-generic), `:7` (`boot_id` `5d7119b8…10df`, o boot de 06:39:35) | ok |
| Código: HEAD, árvore limpa e hashes | `T2/00-pre.log:8-10` (HEAD `1266c12`, contém v4c, 0 modificados), `:11-19` (7 hashes iguais à referência v4c), `:20` (`s4.sh` `e9e46bf1…7367`, igual ao do host). No host, `git diff 902654b 1266c12` não toca nos 7 scripts nem em `cluster/`, e `inject-partition.sh` = `59dd2bee…94e1`, o hash do S0. O jar `3594c690…7bee` (`:23`) é o mesmo da REP=3 (`docs/relatorio-tb-v3-execucao-2026-10-16.md:9`) | ok, com o desvio aceito (R-4) |
| Lock, tmux, sudo, ambiente limpo, 0 timers, `g1-rep4` inexistente | `T2/00-pre.log:3-5,21,24-25` | ok |
| Partições anteriores neste cluster | O cluster foi recriado (`T2/02-recriacao.log:1-4`, `T2/03-pos-recriacao.log:2-4`, IDs novos). As 5 entradas anteriores do `go.log` são da VM antiga, de 16/10 (`T2/00-pre.log:26-31`). Neste cluster: 0 | ok |
| Nenhum `netem` desde a criação | Entre `up` (07:06:20) e GO (07:12:13) só rodaram leituras e o portão (`T2/rc.txt:4-8`). Portão pré: 0 `clsact/prio/netem/ingress` e `noqueue` nos 3 netns (`T2/04-portao51-pre.log:9-17`) | ok |
| Portão 5.1 pré, sob lock, com `.log` | `T2/04-portao51-pre.log:1` (64 amostras, 0 ruins, 317 s contínuos), `:2-7` (caches vazios), `:8` (0 timers), `:18` (lock OCUPADO pelo próprio `flock`), `:19-24` (0 mudanças de vista em 5 min; a última foi às 07:06:25, 5 min 41 s antes do fim às 07:12:06, `:25`); `T2/04-portao51-pre.avaliacao:1`. A R-1 da V-S3 (`.log` não gravado) não se repetiu | ok |
| Idade do cluster e topologia | `StartedAt` 07:06:17,38 a 07:06:19,82 (`T2/03-pos-recriacao.log:2-4`), `start` às 07:12:14,519: **354,7 a 357,1 s**. Topologia 6/9 depois da formação (`T2/03-pos-recriacao.log:6-31`), 10 na entrada em DEGRADED (`G/log-completo-isn3.txt:204`). REP=3: 120,8 s e topologia 10-11 | diferença declarada (R-2) |
| GO do Gestor com `REP=4` conferido | `T2/05-g1.log:1` (prompt `G-1 (REP=4, janela de 90 s em isn3)`, GO às 07:12:13, `sha=1266c12`), `T2/go.log:6` | ok |

## Critérios de validade da REP (§5.5, linha 441)

| # | Critério | Evidência (recalculada) | REP=4R | REP=3 (16/10) | Resultado |
|---|---|---|---|---|---|
| V1 | `injetor rc=0`, sem abort | `G/inj.log:92-93`; `T2/05-g1.log:8`; `T2/rc.txt:9` | rc 0 | rc 0 | ok |
| V2 | Dead-man armado e desarmado ao fim limpo | `T2/05-g1.log:7` (`tcc-heal-11014-26462` em 180 s), `:10` (desarmado); 0 timers e 0 units no portão pós (`T2/07-portao51-pos.log:8`) | ok | ok | ok |
| V3 | Sem `clsact` residual | `G/inj.log:91-92,94`; `T2/05-g1.log:9`; `T2/07-portao51-pos.log:9-17` (`noqueue` em `lo` e `eth0` nos 3 netns, 0 filtros) | ok | ok | ok |
| V4 | Janela real de 96,04 ± 0,2 s | `G/marcadores.txt:1-2`: 1793171630559796415 − 1793171534519348479 ns = **96,040 s** | 96,040 s | 96,046 s | ok |
| V5 | `event=start` e `event=end` uma vez cada | `G/marcadores.txt` tem 2 linhas, 1 `start` e 1 `end`; repetidas em `T2/06-pos-g1.log:2-3` | ok | ok | ok |
| V6 | Split na maioria (`ISPN000094` com 2 membros) | `G/log-completo-isn1.txt:560` (07:12:58,035, `[isn1-23890\|3] (2)`), `G/log-completo-isn2.txt:276` (07:12:58,040) | **+43,52 s** | +46,4 s | ok |
| V7 | Split em isn3 (1 membro e `DEGRADED_MODE` em `sessions` e `counters`) | `G/log-completo-isn3.txt:198` (07:12:58,077, `[isn3-54173\|3] (1)`), `:204` (`sessions` DEGRADED, topologia 10, 07:12:58,091), `:616` (`counters`, 07:12:58,135) | **+43,56 a +43,62 s** | +39,8 s | ok |
| V8 | `MergeView` de 3 membros nos 3 logs | `G/log-completo-isn1.txt:832` (07:14:34,086), `G/log-completo-isn2.txt:522` e `G/log-completo-isn3.txt:19002` (07:14:34,088); `MergeView::[isn3-54173\|4] (3)` | **+43,53 s** do `end` | +46,41 s | ok |

## Verificação do Senior (§5.5, linhas 443-452)

| Item | O que pede | Evidência | Resultado |
|---|---|---|---|
| 1 | Marcadores: ns e duração | V4 e V5 | ok |
| 2 | Instantes por nó e t_merge (vista e rebalance), incluindo os caches internos | t_split da maioria +43,52 s, de isn3 +43,56 s (V6, V7). t_merge pela vista: +43,53 s (V8). Fim do rebalance de **todos** os caches: os internos terminam entre 07:14:34,111 e ,212 (`G/log-completo-isn3.txt:19037-19070`). `sessions` e `counters` começam às 07:14:34,302-,305 (`G/log-completo-isn1.txt:847-848`) e o último `Finished rebalance` é `counters` em isn3, às 07:14:34,315 (`G/log-completo-isn3.txt:19084-19085`, topologia 21): **+43,76 s**. `t_merge.txt` = 44 s (`G/t_merge.txt:1`) | ok |
| 3 | ≥ 3 amostras `HEALTHY`/3 depois do `end` (g1fix), e `t_merge_saude` batendo com vista+rebalance em ~4 s | `G/saude-log.csv`: **16 amostras `HEALTHY`/3 por porta** depois do `end` (48 no total). A primeira é às 07:14:35,372, **+44,81 s** (`G/t_merge.txt:2-7`; `T2/05-g1.log:17-22`). A última amostra não saudável é de +42,80 s, então a transição cabe entre duas amostras de 2 s. A diferença para o fim do rebalance (+43,76 s) é de 1,06 s, dentro dos ~4 s. Não houve nenhuma amostra `HEALTHY_REBALANCING`. Intervalo máximo entre amostras: 2,03 s por porta (sem pausa do host na janela). **A ressalva da REP=3 (0 amostras `HEALTHY`/3 depois do `end`) está fechada: o g1fix funcionou na VM** | ok |
| 4 | Sem `ISPN000524`, `ISPN100014`, `ISPN000638` | 0 ocorrências em `G/log-completo-isn{1,2,3}.txt` | ok |
| 5 | Contadores por filtro > 0 nos dois sentidos em todas as amostras; `isn3→isn1:7800` com timeout e `isn1→isn2:7800` ok | `G/janela.log`, 5 amostras (+8, +25, +45, +65, +82 s): contadores em `:3-6, 26-29, 51-54, 80-83, 109-112`, todos > 0; `rc=124` em `:8,31,56,85,114`; `rc=0` em `:10,33,58,87,116`. Total no fim: ingress (src, `at 12`) 11 + 38 = **49 pkt**, egress (dst, `at 16`) 151 + 136 = **287 pkt** (`G/inj.log:12`, `:21,30,40,49`). REP=3: 33 e 439. O `show ingress` e o `show egress` listam os 4 filtros (`G/inj.log:13-90`), como na REP=2 com o kernel 124 (`runs/gate-b29/g1-rep2/inj.log:13-88`) e como diz o comentário em `runs/gate-b29/g1.sh:19`. Não há mudança de comportamento no kernel 147 | ok |
| 6 | REST de isn3 com HTTP 200 na janela; contagem de requisições compatível com a coleta mínima | `G/janela.log:12,35,60,89,118`: `http=200` em 5/5, de 2,5 a 3,5 ms. Em +45 s a saúde já mostra `HEALTHY/2, HEALTHY/2, DEGRADED/1` (`:62-64`). `G/hotrod/indice.csv`: 105 linhas, **todas** `health` com 200 (35 por porta, intervalo de 5 s, tempo máximo 8,8 ms); nenhum `/caches`, `?action=stats` ou `get-availability`; `G/hotrod/resumo.txt:2,5`. O `saude-log` tem 87 amostras por porta, todas com HTTP 200 | ok |
| 7 | Sonda: classes e nomes das exceções, `maxRetries` 1, chaves removidas (`size=0`) | `G/hotrod/sonda-isn3.csv`: 274 operações, 224 ERRO (`G/hotrod/resumo.txt:9`). Antes da detecção: 11 `TransportException`/`RuntimeException` por `SocketTimeoutException ... timed out after 2000 ms`, com 4,00 a 4,03 s (2 tentativas). Em DEGRADED e até o merge: 213 `HotRodClientException`, 159 `ISPN000306` e 54 `ISPN000305`, nos dois caches. 50 OK de +141,0 s em diante (a primeira na linha 226, 0,9 s depois da primeira amostra `HEALTHY`/3). `MAX_RETRIES = 1` em `workload/src/main/java/br/unipampa/tcc/session/SondaHotrod.java:42,128`, no jar da REP=3. `remove-final` OK nos dois caches e nas duas sondas (`sonda-isn3.csv:274-275`, `sonda-isn1.csv:274-275`); `size` de `sessions` e `counters` = 0 nas 3 portas (`T2/07-portao51-pos.log:2-7`). Na REP=3: 282/232/50. Ver R-7 sobre a sonda de isn1 | ok |
| 8 | Estado final: portão 5.1 e `go.log` com o HEAD/sha de referência | `T2/07-portao51-pos.log:1` (70 amostras, 0 ruins, 348 s), `:2-17`, `:19-24` (0 mudanças de vista em 5 min; última = `MergeView` às 07:14:34, fim às 07:20:54, `:25`); `T2/07-portao51-pos.avaliacao:1`. `T2/go.log:6` com `sha=1266c12`, e não o sha do S0 (R-4) | ok, com R-4 |
| 9 | Se o `t_merge` for longo, onde foi o tempo | Não se aplica: 44 s. Registro mesmo assim: entre o `MergeView` e o fim de `counters` passaram 0,23 s, e `sessions`/`counters` começam 0,21 s depois da vista. O 60 s exato da REP=2 entre `sessions` e `counters` não aparece | n/a |

**Regra "t_merge > 60 s ⇒ PARAR"** (`docs/validacao-v-s1-2026-10-27.md:80`; `docs/decisoes-senior-2026-10-16.md:454`): `t_merge_s=44` (`T2/05-g1.log:11`), `t_merge_saude_s=44` (`:19`), vista+rebalance em +43,76 s. Não houve parada; o `s4.sh` saiu com rc 0 (`T2/rc.txt:9-13`).

## Diferenças declaradas em relação à REP=3 (ROTEIRO-S4, linhas 27-29)

| Diferença | Declarado | Medido | Efeito na leitura |
|---|---|---|---|
| Idade do cluster no `start` | 6 a 7 min | 354,7 a 357,1 s (5 min 55 s a 5 min 57 s); REP=3: 120,8 s | A REP=4R dá 44 s com um cluster ~3× mais velho. O resultado não sugere efeito de idade nessa faixa, mas n = 2 não exclui (R-2) |
| Kernel | 6.8.0-147 contra 6.8.0-124 | `T2/00-pre.log:6` | O `tc` se comportou igual: rc 0, clsact add/del, contadores nos dois sentidos, a mesma listagem dupla do `show` e nenhum resíduo. A pergunta (a) da V-S1 §5.2 está respondida |
| GO | digitado pelo Gestor (REP=3: `tmux send-keys` com autorização) | `T2/05-g1.log:1`, `T2/s4-console.log:133-134` | Mais estrito que a §5.1. Sem efeito na medida |

Não declaradas, sem efeito: o IP da VM mudou para 172.31.251.37 (Default Switch do Hyper-V depois do reboot do host); `podman` 4.9.3, `podman-compose` 1.0.6 e imagem `15.0.21.Final` (`T2/recriacao/down.log:1-3`, `T2/02-recriacao.log:2`).

## Coleta do passo 1 contra o que se perdeu na tentativa 1

Na tentativa 1, o passo 0 terminou às 06:36:24 (`T1/s4-console.log:38`) e o passo 1 abriu (`:39`) no mesmo segundo em que a VM parou. Ficaram vazios `T1/pre-recriacao/inspect-isn1.json`, `T1/01-pre-recriacao.log` e `T1/rc.txt`. Não chegaram a ser gravados o `inspect` de isn2/isn3, os `podman logs` e a última vista do cluster antigo. Nada foi injetado e o cluster não foi tocado.

A tentativa 2 recuperou tudo isso:
- `inspect` dos 3 contêineres (`T2/pre-recriacao/inspect-isn{1,2,3}.json`, de 19,6 a 19,8 KB);
- `podman logs` completos (7282, 7381 e 7304 linhas, `T2/01-pre-recriacao.log:2,4,6`), que vão até o instante da queda (última linha de isn1 às 06:36:24,317, de isn2 às 06:36:15,792, de isn3 às 06:36:24,246). O `podman logs` funcionou com os contêineres em `Created` porque o driver de log é o journald;
- a última vista, `[isn2-7049|2] (3)` de 27/10 18:49:06 nos 3 nós (`T2/01-pre-recriacao.log:3,5,7`, confirmada no journal em `:9-11`). Portanto não houve mudança de vista no cluster antigo entre a V-S3 e a queda;
- `journalctl -b -1`: a lista de boots (`journal-boot-anterior.txt:1-4`; o boot −1, `b7df4b03`, vai de 20/10 16:31:46 a 28/10 06:36:24), as linhas dos 3 contêineres e 20 829 eventos `health_status` de podman/conmon. Comparei as linhas dos contêineres com os `logs-isn*.txt`, sem ANSI: são **iguais** em isn1 e isn3. Em isn2 só 6 linhas de stack trace diferem, por tab contra espaços. O journal acrescenta o carimbo do host em microssegundos e os eventos do podman.

**O que a coleta não cobre.** (1) O `inspect` com o contêiner ainda `running` (Pid, estado de saúde): o de agora já mostra `created`. Para a REP=4R isso não importa, porque o cluster foi recriado. (2) O journal do kernel e do sistema da VM no último minuto (`-k`, `-p warning`), já que o filtro do passo 1 pegou só `CONTAINER_NAME` e podman/conmon. Esse dado diria se a VM registrou uma pausa (RCU stall, salto de clocksource, `hv_*`) antes da queda.

**Achado sobre a queda.** Os brutos indicam que o host já estava degradado uns 50 s antes do BSOD:
- o healthcheck de cada contêiner rodava a cada 6 s e o último antes da queda é de 06:35:33-06:35:36 (`journal-boot-anterior.txt:7286`, `:14669`, `:21975`, `:42819`). O seguinte só aparece às 06:36:15-06:36:24 (`:7287`, `:14670`, `:21976`), então faltaram ~8 ciclos;
- o passo 0 da tentativa 1 levou **30 s** (`T1/00-pre.log:1` às 06:35:54, fim às 06:36:24). O da tentativa 2 levou 3 s (`T2/00-pre.log:1` às 07:06:00, `T2/rc.txt:1` às 07:06:03);
- o 6008 do host dá como último registro periódico 03:35:33 locais, ou seja, 06:35:33 UTC (`host-eventos-desligamentos.log:39`).

Isso é indício, não prova. Mas a consequência para as medidas é concreta. Uma pausa do host **sem** queda alongaria em silêncio um t_split ou um t_merge. Na REP=4R isso não ocorreu: os intervalos do `saude-log` ficaram ≤ 2,03 s (item 3).

## R-3 da V-S3 (pedido do `ROTEIRO-S4.md:9`)

Aceito a leitura do Worker. Num shell interativo, `${PIPESTATUS[0]}` na linha seguinte se refere ao último pipeline em primeiro plano, que foi o `bash -x ... l0c.sh 2>&1 | tee`. A condição é que nenhum `PROMPT_COMMAND` tenha rodado um pipeline entre as duas linhas. Isso não foi conferido na VM, mas o bash do Ubuntu numa sessão SSH/tmux não define `PROMPT_COMMAND` por padrão. Considero a R-3 da V-S3 fechada pela construção do S4: o `s4.sh` grava o rc de cada passo na mesma linha do pipeline (`T2/rc.txt:1-13`). Se o Gestor quiser fechar também a condição, basta `declare -p PROMPT_COMMAND` na sessão `s4`, só leitura.

## Ressalvas

- **R-1 (cópia sem conferência VM = host para `T2/`).** Só o `g1-rep4` tem manifesto gerado na VM (124/124 OK). Para os 30 arquivos de `T2/` e os 5 de `T1/` não há um par como o `01-copia-vm-host.log` do S3. Gerei o lado do host (`copia-host.sha256`). Falta rodar na VM, só leitura, `cd ~/tcc-gate/runs/vm-s4-2026-10-28 && sha256sum -c` desse arquivo (P-5). Os brutos são coerentes entre si (marcadores de `T2/06-pos-g1.log:2-3` = `G/marcadores.txt:1-2`; `T2/05-g1b.log:4-13` = `G/hotrod/resumo.txt:1-10`), então não espero divergência.
- **R-2 (idade do cluster, n = 2).** 355 s contra 121 s na REP=3. Declarada e aceita pelo Gestor, que optou por recriar (`ROTEIRO-S4.md:7`). A estimativa do roteiro (6 a 7 min) ficou um pouco acima do medido (5,9 min). Duas janelas não estimam variância e não separam idade de acaso.
- **R-3 (detecção em isn3).** Na REP=3, isn3 detectou o split 6,6 s antes da maioria (+39,8 contra +46,4 s). Na REP=4R detectou junto (+43,56 contra +43,52 s). É uma observação, e o protocolo de detecção (FD_ALL/VERIFY_SUSPECT) não muda. Com n = 2 não dá para dizer qual comportamento é o típico.
- **R-4 (referência de hashes ≠ S0, item 8).** O `go.log` registra `sha=1266c12`, e não o sha do S0. O desvio foi aceito pelo Gestor em 28/10 (`ROTEIRO-S4.md:8`). Conferi no host que nenhum dos 7 scripts da tabela nem `cluster/` mudou entre `902654b` e `1266c12`, e que `inject-partition.sh` e `g-lib.sh` têm o hash do S0.
- **R-5 (cosmético).** `T2/05-g1.log:24` diz "261 linhas", mas `G/saude-log.csv` tem 262 (cabeçalho + 261 amostras = 87 × 3). Provavelmente a última amostra em segundo plano foi escrita depois da contagem. O `Terminated` em `T2/05-g1.log:15` é o aviso do bash quando o `parar_log_saude` mata o laço (`runs/gate-b29/g-lib.sh:64-67`). A última linha do CSV está completa. Nada a corrigir agora; pode entrar num `wait` depois do `pkill` em outra branch.
- **R-6 (journal de 50 MB).** Versionei o `journal-boot-anterior.txt` comprimido (2,6 MB, sem perda; o sha256 do original está em `copia-host.sha256`) e deixei o original só no disco. Motivo: o maior arquivo versionado na branch tem 1 MB e o pack tem 3 MB. O original fica perto do aviso de 50 MiB do GitHub, e o conteúdo dos contêineres repete os `logs-isn*.txt`. Se o Gestor quiser o original no git, basta um commit aditivo, sem reescrever histórico (P-3).
- **R-7 (sonda de isn1 com 10 erros; na REP=3, 0).** `G/hotrod/sonda-isn1.csv`: 10 timeouts de 4,0 s entre +1,6 e +37,7 s, todos antes da detecção. Do lado do servidor aparecem `ISPN000136`/`ISPN000217` em isn1 (`G/log-completo-isn1.txt:38,93`). Hipótese, não verificada: `sessions` e `counters` são `distributed`, `owners="2"`, 64 segmentos e `SYNC` (`cluster/infinispan-cluster.xml:22,28`). A chave `sonda-isn1` tem isn3 entre os donos neste cluster e não tinha no cluster de 16/10, já que o hash consistente muda com os endereços dos membros a cada recriação. Isso não afeta nenhum critério. Mas "sonda de isn1 sem erro" não é propriedade estável entre REPs e não deve ser usado como critério.
- **R-8 (`.gitattributes`).** Segui o S3: brutos com `git add -f` e sem atributo `-text`. Com `core.autocrlf=true` (do `C:/Program Files/Git/etc/gitconfig`), um checkout novo no Windows converte os brutos para CRLF. Isso já acontece com `runs/gate-b29/g1-rep2/log-completo-isn3.txt` neste worktree (`git ls-files --eol`: `i/lf w/crlf`), e aí os manifestos sha256 deixam de conferir. O conteúdo no git está certo, porque os arquivos entraram com LF. Proposta em P-4.

## O que a REP=4R mede e o que não mede

Mede:
1. O `inject-partition.sh` no kernel 6.8.0-147 reproduz o comportamento conhecido: janela de 96,040 s, rc 0, `clsact` instalado e removido, descarte nos dois sentidos, split nos dois lados, `MergeView`, nenhum resíduo. É a pergunta (a) da V-S1 §5.2, e a resposta é sim.
2. Um segundo ponto na célula "cluster novo + coleta mínima": t_merge de **44 s**, contra 46 s da REP=3 (pela vista: 43,53 contra 46,41 s; pela saúde: 44,81 s, que a REP=3 não conseguiu medir). A REP=3 ganha uma réplica. Junto com a REP=1 (48 s), há agora três janelas entre 44 e 48 s.
3. A correção do g1fix funciona na VM: 16 amostras `HEALTHY`/3 por porta depois do `end`, e `t_merge_saude` coerente com vista e rebalance em 1,06 s.
4. O invólucro `s4.sh` funcionou de ponta a ponta na VM, com GO manual, recriação, portões automáticos com `.log` e parada condicionada.

Não mede:
1. Variância. Com n = 2 na célula (3 com a REP=1, que não é par exato), não há intervalo de confiança nem estimativa de dispersão.
2. A causa dos 188 s da REP=2. A pergunta "histórico × coleta pesada" foi extinta na V-S1 §5.2 (`docs/validacao-v-s1-2026-10-27.md:73,78`), e a REP=4R não a reabre.
3. Nada sob carga de aplicação: o cluster estava ocioso, com só as chaves `sonda-*`. Também nada sobre divergência ou conteúdo no merge (isso é T-F) nem sobre `DIST_ASYNC`, porque os caches são `SYNC`.
4. Só uma configuração de partição: isn3 em minoria, 90 s de injeção, cluster de 3 nós.
5. Nem o dead-man do `inject-partition.sh` disparando (S7), nem a cura depois de split com o injetor morto (S6).
6. Robustez a uma pausa ou queda do host durante a janela.

## Host: estabilidade e risco para as campanhas noturnas

O coordenador listou 3 desligamentos abruptos (13/09 0x1CA, 18/10 Kernel-Power, 28/10 0x154). O log System do host mostra mais (`host-eventos-desligamentos.log:3-36`):
- **34** Kernel-Power 41 desde 01/08, sendo **17 desde 01/09**. Só 2 deles, ambos em agosto, têm `PowerButtonTimestamp` (`:20`, `:27`). Os outros 32 não têm botão de energia registrado, e só 2 têm bugcheck: 0x1CA em 13/09 (`:19`, WER em `:55`) e 0x154 em 28/10 (`:3`, WER em `:38`). Os demais têm `BugcheckCode=0`, ou seja, travamento duro ou corte de energia sem tela azul.
- Há rajadas: 27/09 teve 8 em ~10 h, 06/08 teve 8 em ~2 h.
- Pelo 6008, ao menos **4 quedas foram de madrugada**: 12/09 01:15, 08/10 02:21, 18/10 04:49 e 28/10 03:35, horário local (`:39-41`, `:56`).
- 0 eventos WHEA desde 01/08 (`:76`). Hardware: i5-14600KF numa MSI PRO H610M-G DDR4, BIOS 1.O0 de 31/07/2025 (`:78-80`).

Não diagnostico a causa daqui. Para a decisão, pesa o seguinte:
- 0x154 (`UNEXPECTED_STORE_EXCEPTION`) e as quedas sem bugcheck são compatíveis com instabilidade de hardware: CPU Raptor Lake Refresh com o problema de Vmin conhecido, RAM, VRM de uma placa H610 com uma CPU K, ou armazenamento. Também são compatíveis com drivers de kernel de terceiros. O boot de 28/10 carrega os filtros `vgk` e `FACEIT`. Nenhuma dessas hipóteses está verificada.
- Em escala grosseira, 4 quedas noturnas em ~47 noites dão ~1 a cada 12 noites. Numa campanha de 10 noites, a chance de pelo menos uma queda fica na casa de 50% (Poisson, λ ≈ 0,083/noite; supõe taxa constante, o que as rajadas desmentem). Se uma parte dos 41 foi reset intencional do Gestor, a taxa real é menor.
- Os blocos S5 a S7 são curtos (~10 a 30 min cada) e acompanhados. Neles uma queda custa repetir o bloco com novo GO, e o dead-man na VM morre junto com ela. Isso não deixa a rede degradada: depois do boot, os contêineres ficam em `Created` e o netns é recriado, como em 18/10 e 28/10. Nas campanhas noturnas o custo é perder noites e, sem a parada (h) por `boot_id` (P-5 da V-S1), misturar uma execução interrompida com as válidas.

## Perguntas ao Gestor (cada uma com a recomendação do Senior)

- **P-1. Aprovar a V-S4 (APROVADO com ressalvas) e liberar a preparação do S5.** Recomendo aprovar. A liberação da execução do S5 depende de P-5 e P-6.
- **P-2. Estabilidade do host.** (a) Quais dos 17 Kernel-Power 41 desde 01/09 foram resets ou desligamentos intencionais seus? Isso muda a taxa. (b) O senhor aceita rodar campanhas noturnas com essa taxa? Recomendo **não** começar campanha noturna (rebaseline/T-F) antes de: (i) abrir os dumps de 13/09 e 28/10 (`!analyze -v` no WinDbg, em `C:\Windows\MEMORY.DMP` ou `C:\Windows\Minidump`); (ii) conferir se o BIOS 1.O0 traz o microcode da Intel com a correção de Vmin e usa os limites de potência padrão da Intel; (iii) um teste de memória e de estabilidade da CPU fora do horário de experimento; (iv) tornar obrigatória no runner a parada (h) por `boot_id` e `StartedAt` (V-S1, P-5) e o descarte da execução interrompida. Para S5 a S7, curtos e com o senhor presente, recomendo seguir, mantendo no invólucro o registro de `boot_id` no início e no fim (o `s4.sh` já faz no início) e acrescentando como critério de validade "intervalo máximo do `saude-log` ≤ 2 × intervalo".
- **P-3. Journal de 50 MB.** Manter só o `.gz` no git (feito) ou versionar também o original? Recomendo manter só o `.gz`.
- **P-4. `.gitattributes` com `-text` para os brutos de `runs/vm-s*/`.** Recomendo, num commit `chore:` separado nesta branch, cobrindo S2, S3 e S4 (R-8). Não fiz porque não é a convenção atual do S3.
- **P-5. Conferir a cópia na VM (R-1) e liberar o lock.** Na sessão tmux `s4`, ainda aberta: `cd ~/tcc-gate/runs/vm-s4-2026-10-28 && sha256sum -c` do `copia-host.sha256` (levado à VM pelo próximo bundle ou colado), depois `exit` para liberar o lock. Opcional, também só leitura: `journalctl -b -1 -k --since "2026-10-28 06:34" --no-pager` e `journalctl -b -1 -p warning --since "2026-10-28 06:34"`, para ver se a VM registrou a pausa. Recomendo fazer as duas coisas antes do S5.
- **P-6. GO do L-1.** A §5.6 diz "Go já concedido pelo Gestor para L-1" (`docs/decisoes-senior-2026-10-16.md:458`), mas esse go é de antes da troca de VM e de kernel e das duas quedas. Recomendo um GO novo, digitado pelo senhor no prompt, como no S4.
- **P-7. Registrar o incidente.** Acrescentar ao P-28 do `registro-de-problemas.md` (na integração, quando liberar) a queda de 28/10 e o histórico do host acima. Recomendo.

## O que o S5 precisa (`docs/decisoes-senior-2026-10-16.md` §5.2 e §5.6, V-S1 §5.2, V-S2 e V-S3)

1. **Ordem.** O S5 vem depois da V-S4 aprovada (`docs/validacao-v-s1-2026-10-27.md:82`). O cluster atual tem 1 partição e 1 merge (a REP=4R) e nenhum `netem`. O L-1 não depende do histórico. Não recomendo recriar, mas o estado precisa ser registrado (IDs, `StartedAt`, vista `[isn3-54173|4] (3)`).
2. **Pré-condições** (§5.6, linha 458): V-S2 e V-S3 aprovadas (feito), portão 5.1 imediatamente antes com `.log`, o Gestor presente nos 60 s e um GO novo (P-6).
3. **Um invólucro `s5.sh`, com TDD, no padrão do `s4.sh`.** O `l1.sh` é verbatim e deixa as coletas paralelas como comentário (`runs/gate-netdegrade/l1.sh:8-13`), mas a §5.6 (linhas 460 e 470) exige:
   - a linha de base **sem** F4 da latência REST de isn3, antes;
   - ≥ 2 amostras de `tc -s qdisc` e `tc -s filter` no netns de isn3 durante a janela;
   - `ping` do host a isn3 e latência de Hot Rod e SSH, na base e durante;
   - `TcpRetransSegs` do netns de isn3 antes e depois (hipótese H-L);
   - `transmitted/received` do `ping -c 300` do injetor, para o intervalo de 39 a 81 perdas;
   - logs de vista dos 3 nós, marcadores, L-2 (`sem_prio`, filtro vazio, `esperar_locks`) e o portão 5.1 pós.
4. **Correção de porta.** O comentário em `runs/gate-netdegrade/l1.sh:10` mede `127.0.0.1:11222`, que é isn1. A §5.6 pede o REST **de isn3**, que é a porta 11224. O `s5.sh` deve usar 11224 e manter 11222 como controle. O `l1.sh` não se edita, porque é verbatim.
5. **Dead-man do L-1.** `armar 60` dá disparo em 150 s (`runs/gate-netdegrade/l-lib.sh:32-34`), mas o `.timer` usa `AccuracySec` padrão de 1 min (R-3 da V-S2, R-4 da V-S3). A cura automática pode chegar até ~210 s depois de armar. O Gestor precisa saber disso antes do GO. O `--heal` manual (`scripts/inject-netdegrade.sh --heal`) continua disponível.
6. **Hashes.** Acrescentar à tabela do invólucro o `l1.sh`, o `l-lib.sh` e o `inject-netdegrade.sh`, com o v4c como referência.
7. **Host.** Nada de TLC, `mvn` ou carga pesada de 5 min antes do portão pré até o fim do portão pós. Registrar o `boot_id` no início e no fim. Se houver intervalo do log de saúde > 2 × o nominal, o bloco é inválido.
8. **Critérios** (§5.6, linhas 462-468): exit 0; 39 a 81 perdas em 300; `dropped/(Sent+dropped)` perto de 20%; Hot Rod/SSH sem degradação; nenhum `prio`/`netem`/`u32` residual; `size=3 HEALTHY` no fim. Mudança de vista ou `DEGRADED` é resultado do experimento e bloqueia o S6 até o portão 5.1.
9. **Push.** O `s5.sh` só chega à VM por bundle depois da aprovação de push da branch (política de 14/06).
