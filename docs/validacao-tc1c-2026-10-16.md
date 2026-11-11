# V-C1c: revalidacao do roteiro L-0..L-6 (T-C1, B-29.2), commit 2c9bf51 - 2026-10-16

Validador: Senior. Alvo: `docs/relatorio-b29-2-implementacao-2026-10-16.md`, secao 5, branch `feature/b29-2-inject-netdegrade` (`2c9bf51`). Base: V-C1b (`docs/validacao-tc1b-2026-10-16.md`). Nenhum script/spec alterado; nenhum push; VM nao tocada.

## Veredito: APROVADA COM RESSALVAS

Os 4 defeitos e os menores da V-C1b foram corrigidos de fato. Restam 2 defeitos novos, de uma linha cada, que o Worker-3 deve corrigir no documento; o primeiro quebra L-4 e o segundo tende a dar falso exit 6 em L-6e. Com as duas correcoes, T-C2 pode usar o roteiro; sem elas, nao.

## Metodo

Os 8 blocos bash (B0, `l-lib.sh`, `l0`, `l1`, `l3`, `l4`, `l5`, `l6`) foram extraidos do .md e passaram em `bash -n`. L-0, L-1, L-3, L-4, L-5 e L-6 foram executados em WSL Ubuntu, sob PTY (`script`), com shims para `sudo`, `podman`, `nsenter`, `tc` (com estado), `readlink` (/proc), `systemd-run`/`systemctl` (log), `curl`, `hostname`, `sleep` (/10) e injetores falsos que reproduzem os exit codes e o uso de `flock` do real. `flock`, `set -m` e `wait` sao reais. Limite: o kernel, o `tc` e o `systemd-run` reais nao foram exercitados; a sintaxe deles foi lida contra as man pages.

## Os defeitos da V-C1b

| V-C1b | Estado | Evidencia |
|---|---|---|
| 1 guarda de L-0 nao aborta | CORRIGIDO | `pid_de` faz `exit 1` e `PID=$(pid_de isn3)` sob `set -e` aborta o script. Shim com PID vazio e com netns do host (pid 1): "ABORTAR: PID invalido", rc=1, nenhum `systemd-run` registrado (nada armado, nenhum `tc`). |
| 2 paradas pelo go | CORRIGIDO | Sem terminal: rc=1; resposta diferente de GO: rc=1; GO digitado: segue e grava `go.log` (data, IP, bloco). |
| 3 L-4 sem espera, `tee` esconde rc | CORRIGIDO NA LOGICA, mas ver D1 | `rodar_inj` grava em arquivo e usa `$?`; rc errado (6) aborta com "dead-man's continua armado" e o `systemd-run` ja registrado. `espera_saudavel` roda antes de iniciar e apos cada severidade (10 injecoes, 10 logs). |
| 4 armar unico em L-5/L-6 | CORRIGIDO | L-5: `armar 120` por subteste e antes do KILL; `desarmar` so no fim limpo. L-6: `armar` e `armar_part` por subcaso. |
| 5 F2 em L-6 | CORRIGIDO | L-6d e L-6e com comandos; `armar_part` chama `inject-partition.sh --heal --minority <no>` (suportado). |
| 6 orfaos / lock | CORRIGIDO | `esperar_locks` com `flock -w 200` nos 3 locks (nome confere com o injetor: `inject-netdegrade-<no>.lock`); o KILL de L-5 passa por ele. |
| 7 flock com tmux | CORRIGIDO | `flock /var/lock/tcc-vm.lock tmux new -s l-gate`. |
| 8 FALTA | CORRIGIDO | Laco `command -v ... || echo FALTA $c`; modulos por laco com `/sys/module`. |

Resultados dos shims. L-0 normal: rc=0, sem resíduo. L-1: rc=0, `sem_prio` ok. L-3: tres subcasos, `cont` imprime "Sent dropped"; rc de cada um registrado. L-4 (com a correcao de D1): rc=0, 10 logs, espera entre severidades. L-5: TERM 143, INT 130 (`set -m` funciona, kill -INT em job de fundo entregue), HUP 129, KILL deixa prio, `--heal` remove, `esperar_locks` libera orfaos; rc=0 do bloco. L-6: 6a rc=0, 6b rc=7, 6c rc=7, 6d rc=7, 6e rc=0 (no shim), `bg rc=0` em todos.

## Defeitos novos (documento, nao script)

D1 (bloqueia L-4). `espera_saudavel` chama `curl -s -m 5 -u admin:infinispan` sem `--digest`. A REST do Infinispan do cluster usa autenticacao digest (healthcheck em `cluster/podman-compose.yml` e `aguardar_healthy` de `run-baseline.sh` usam `--digest`). Com Basic o retorno e 401, o JSON nunca casa e `espera_saudavel 120` aborta no inicio de L-4 ("cluster nao reformou"). Reproduzido com um shim que exige `--digest`. Correcao: `curl -s -m 5 --digest -u ...`. O padrao de casamento (`health_status` antes de `number_of_nodes`, ou o inverso) esta correto segundo `docs/testes.md:46`. Observacao: `docs/relatorio-b29-2...` L-1 (comentario) tambem cita `curl -u` sem `--digest`; corrigir junto.

D2 (L-6e, falso exit 6). F4 com `--direction out` no isn3, com F2 isolando isn2: a verificacao por ping (padrao 200 pings, `-i 0.2`) vai do isn3 a TODOS os pares, inclusive isn2; o F2 descarta todo IP (incl. ICMP, `u32 match ip src|dst`), logo a perda observada sera ~100% contra 20% esperada e o injetor sai com exit 6, nao 0. Nao reproduzivel com shim (depende do kernel); deduzido de `inject-partition.sh:402-426` e `inject-netdegrade.sh:758-775`. Correcao: acrescentar `--ping-count 0` na linha de L-6e (o objetivo ali e coexistencia, nao a taxa). L-6a (F3 so atrasa) nao e afetado.

## Menores (nao bloqueiam)

- `cont` e `sem_prio` chamam `pid_de` dentro de `$(...)`; em `echo "...: $(cont "$n")"` uma falha de `pid_de` nao aborta (o `set -e` nao se propaga ao `echo`), apenas imprime vazio. Aceitavel porque `PID=$(pid_de isn3)` ja valida; opcional: `c=$(cont "$n") || exit 1`.
- Em L-3 os nos sem `prio` imprimem linha vazia por desenho; o operador deve ler o `.cont` e conferir dropped > 0 so onde esperado (nao e verificacao automatica).
- `PID` em `l4.sh` nao e usado; B0 nao tem `set -e` (aceitavel: nao altera a rede).
- B0: apos `tmux new` os comandos seguintes sao digitados dentro da sessao; nao colar o bloco inteiro de uma vez.
- `exigir_go` exige terminal real: dentro do tmux funciona; por `ssh host 'bash l0.sh'` sem `-t` aborta (correto).

## Sintaxe de tc, ping, nstat (leitura contra as man pages)

Corretos: `tc qdisc add dev eth0 root handle 1: prio bands 3 priomap` com 16 zeros; `parent 1:3 handle 30: netem loss 20% 25% delay 20ms 5ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%` (ordem livre; `reorder` exige `delay`, presente; `distribution normal` exige `/usr/lib/tc/normal.dist`, conferido em B0); `qdisc change ... netem loss gemodel 5% 25% 100% 0%`; `tc filter add ... protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3`; `tc qdisc del dev eth0 root`; `tc -s qdisc|filter show dev eth0`; `nstat -az TcpRetransSegs` (-a absoluto, -z zeros); `ping -c 50 -i 0.2` (0,2 s e o minimo sem root); `nsenter -t PID -n`. O `awk` de `cont` le `$4` (pacotes) e `$7` (dropped) da linha `Sent N bytes P pkt (dropped D, ...)` apos `qdisc netem 30:`. `systemd-run --unit=X --on-active=Ns --setenv=PODMAN="sudo podman"`: a atribuicao vai como um unico argumento; o `.timer` e parado por `$DMS.timer`. Nao testados em kernel real: devem ser confirmados no primeiro L-0.

## Condicao para T-C2

1. Worker-3 corrige D1 (`--digest`, em `espera_saudavel` e no comentario de L-1) e D2 (`--ping-count 0` em L-6e) no documento, commit `docs:`.
2. Eu releio apenas essas duas linhas (sem nova suite).
3. Seguem as paradas ja desenhadas: go do Gestor antes de L-0, L-1 e L-5 (KILL), e V-B aprovada antes de T-C2.
