# V-S3: validação do S3 (L-0c na VM), 2026-10-28

Validador: Senior. Só leitura na VM `172.18.251.140` (árvore `~/tcc-gate`, HEAD `902654b`, bundle gate-v4c) e no worktree `wt-gate-v4` (`test/b29-gate-v4`). Critérios: `decisoes-senior-2026-10-16.md` §5.4 (S3) e a seção "O que fica para o S3" da `runs/vm-s2-2026-10-27/V-S2.md` (itens 1 a 5). Nenhum `go`, nenhuma escrita na VM, nenhum push.

## Veredito

**APROVADO, com ressalvas.** O dead-man do `inject-netdegrade.sh` disparou sozinho 30,34 s depois de armado, executou o `--heal` real, removeu a `prio` de isn3 sem intervenção e terminou sem falha. Nenhuma ressalva invalida o bloco. A R-1 da V-S2 está fechada.

## Brutos

| Arquivo | Conteúdo |
|---|---|
| `vm/l0c.log` | console `bash -x` do L-0c (834 linhas) |
| `vm/l0c-inicio.txt` | `date -Is` antes do `l0c.sh` (04:58:52) |
| `vm/go.log` | `runs/gate-netdegrade/go.log` da VM (2 linhas: L-0 e L-0c) |
| `vm/portao51-pre.csv` | CSV do portão 5.1 pré, 2.ª execução, sob lock |
| `vm/l0c/*` | `runs/gate-netdegrade/l0c/` da VM (12 arquivos: `resultado.txt`, `tempos.txt`, `unit-armada.txt`, `journal-unit.txt`, `estado-systemd.txt`, `qdisc-{antes,durante,depois,diff}.txt`, `filter-depois.txt`, `{pre,pos}-sem-prio.txt`) |
| `00-portao51-pre-console.txt` | saída do portão 5.1 pré colada pelo Gestor (o `.log` não foi gravado na VM) |
| `01-copia-vm-host.log` | cópia por `tar`, sha256 VM = host nos 16 arquivos, 0 CR em todos |
| `02-pos-l0c-leitura.log` (+ `.sh`) | leitura pós-L-0c: hashes, mtimes, journal da unit com microssegundos, `sudo` de escrita desde 04:50, timers, tc nos 3 netns, vistas, health, lock, tmux, boot |
| `03-sudo-pre-armar-e-pos.log` | `sudo` de 04:59:00 a 04:59:26 (pré-armar) e journal de 04:59:57 a 05:01:00 |
| `04-s4-pre-hashes-e-cluster.log` | hashes de `inject-partition.sh`, `g1.sh`, `g1b.sh`, `g-lib.sh`; `runs/gate-b29/go.log`; idade dos contêineres (preparo do S4) |

O `02-pos-l0c-leitura.sh` foi lido antes de rodar e só faz leituras (`sha256sum`, `stat`, `journalctl`, `systemctl list-*`, `podman inspect/logs/ps`, `nsenter ... tc ... show`, `curl` GET de health, `flock -n ... true`, `tmux ls`).

## Critérios do L-0c (§5.4 e V-S2)

| # | Critério | Evidência | Resultado |
|---|---|---|---|
| 1 | Bundle v4c com a correção da R-1 na VM | `02-pos-l0c-leitura.log:3-10` (HEAD `902654b`, 0 arquivos modificados; `l0c.sh` `ba38b912…1216`, `l-lib.sh` `9bdd1fae…73fd`, `inject-netdegrade.sh` `4ebdd8a3…abb1`, `g5b.sh` `6f50e3e3…0378`, iguais ao worktree) | ok |
| 2 | Portão 5.1 imediatamente antes, sob lock | `00-portao51-pre-console.txt:7` (27 amostras, 0 ruins, 131 s contínuos), `:8-13` (caches vazios), `:14` (0 timers/units), `:15-23` (`noqueue` e 0 filtros nos 3 netns), `:24` (`lock: OCUPADO`, coerente com rodar dentro do `flock`), `:25-30` (0 mudanças de vista em 5 min), `:31` fim 04:57:57; `vm/portao51-pre.csv` 81 linhas `200 HEALTHY 3 0`, 27 por porta | ok |
| 3 | Go do Gestor no terminal, `TCC_DIR=~/tcc-gate` | `vm/l0c.log:17-26`; `vm/go.log:2` (`go 2026-10-28T04:59:09+00:00 ... bloco=L-0c`); `vm/l0c.log:9`, `:138` (`--working-directory=/home/server1/tcc-gate`) | ok |
| 4 | Guarda de netns antes de qualquer `armar`/`tc` | `vm/l0c.log:31-47` (PID 33811, `net:[4026532291]` ≠ `net:[4026531840]`) | ok |
| 5 | Dead-man armado antes do primeiro `tc` de escrita | `systemd-run` às 04:59:25.341 e 1.º `tc qdisc add` às 04:59:25.405 (`03-sudo-pre-armar-e-pos.log:17,21`); timer iniciado às 04:59:25.347686 (`02-pos-l0c-leitura.log:32`) | ok |
| 6 | Corpo da unit igual ao da injeção real | `vm/l0c/unit-armada.txt:3-12` (`ExecStart="/usr/bin/bash" "scripts/inject-netdegrade.sh" "--heal"`, `WorkingDirectory`, `Environment="PODMAN=sudo podman"`). Os argumentos de `armar` em `l0c.sh:31-33` e `l-lib.sh:32-34` são iguais, exceto `--timer-property=AccuracySec=1s`, que só vai para o `.timer` (`diff` vazio após retirar essa opção). Ver R-4 | ok |
| 7 | Precisão do timer nos brutos (R-1 da V-S2) | `vm/l0c/unit-armada.txt:21` (`AccuracySec=1s` no `.timer`), `:25` (`AccuracyUSec=1s`, valor efetivo lido com o timer armado), `:27` (próximo disparo 04:59:55) | ok, R-1 fechada |
| 8 | Degradação inerte instalada | `vm/l0c/qdisc-durante.txt:3-4,7-10` (`prio 1:`, `netem 30: delay 1ms`, `u32 match c0000201/ffffffff at 16` = 192.0.2.1); `vm/l0c.log:186-202` | ok |
| 9 | O `systemd-run` real executou `inject-netdegrade.sh --heal` (início, fim, rc) | `02-pos-l0c-leitura.log:33-39`: service iniciado 04:59:55.689069, `Heal: removendo` (`:34`), isn1 e isn2 "nada a fazer" (`:35-36`), `isn3 ... prio 1: removida` (`:37`), `service: Deactivated successfully` 04:59:55.912205 (`:38`), timer desativado (`:39`); mesma sequência em `vm/l0c/journal-unit.txt:1-63`. Execução de 0,22 s. Ver R-3 sobre o rc | ok |
| 10 | Disparo entre 30 s e 40 s após armar | Pelo journal: 04:59:25.347686 → 04:59:55.689069 = **30,341 s** (`02-pos-l0c-leitura.log:32-33`). Pela sonda: `vm/l0c/tempos.txt:1` (31 338 ms desde antes de armar, 31 327 ms desde depois); `vm/l0c.log:605-617` (31.ª sonda ainda com `prio`, 32.ª com `noqueue`); `vm/l0c/resultado.txt:3-4` | ok |
| 11 | Unit sem `failed` | `vm/l0c/estado-systemd.txt:1` (`is-failed: inactive`); `vm/l0c/resultado.txt:7-8`; nenhum `Failed with result` no journal | ok |
| 12 | `tc qdisc show dev eth0` de isn3 volta a `noqueue` sem intervenção | `vm/l0c/qdisc-antes.txt:1` = `vm/l0c/qdisc-depois.txt:1` (`qdisc noqueue 0: root refcnt 2`, mesmo sha256 em `01-copia-vm-host.log`), `vm/l0c/qdisc-diff.txt` vazio, `vm/l0c/filter-depois.txt` sem filtros; a única escrita `tc` depois dos `add` foi o `qdisc del` do root da unit às 04:59:55.909748 (`02-pos-l0c-leitura.log:62-67`); nenhum `abort-heal.log` na VM; nenhum `--heal` manual no console (`vm/l0c.log:827-833`, `LIMPO=1` e `abortar` sai em `return 0`) | ok |
| 13 | isn1 e isn2 não tocados | `02-pos-l0c-leitura.log:62-67` (todas as escritas `tc` desde 04:50 são no PID 33811, de isn3); o heal só leu isn1/isn2 (`vm/l0c/journal-unit.txt:18,37`); `vm/l0c/pre-sem-prio.txt` = `vm/l0c/pos-sem-prio.txt` (mesmo sha256) | ok |
| 14 | 0 timers ao fim | `vm/l0c/estado-systemd.txt:3` (`(nenhum)`); `02-pos-l0c-leitura.log:69-70` (0 timers, 0 units às 05:03:54) | ok |
| 15 | Nenhuma mudança de vista causada pelo L-0c | `02-pos-l0c-leitura.log:76-78` (0 `ISPN000094/093/436/SUSPECT/MergeView/ISPN100000/100001` desde 04:55 nos 3 nós); última vista continua `[isn2-7049\|2] (3)` de 27/10 18:49:06 (`:80-82`) | ok |
| 16 | Estado final limpo | `02-pos-l0c-leitura.log:72-74` (`noqueue` em `lo` e `eth0` nos 3 netns, 0 filtros), `:84-86` (HEALTHY nas 3 portas), `:88-89` (lock `LIVRE`, sem lock do injetor em `/tmp`), `:91` (sem servidor tmux), `:93-94` (6.8.0-147, mesmo `boot_id` da V-S2) | ok |

## Ressalvas

- **R-1.** O `.log` do portão 5.1 pré não foi gravado (o `| tee` quebrou na colagem). A evidência é a saída colada pelo Gestor (`00-portao51-pre-console.txt`, sem par na VM) e o CSV, que tem par na VM e confere por sha256. A 1.ª execução, fora do lock, foi descartada. O CSV gravado é o da 2.ª: a 1.ª amostra é 04:55:44, que coincide com `ultima_sequencia_boa_desde` (`00-portao51-pre-console.txt:7`), e o `mtime` é 04:57:55 (`02-pos-l0c-leitura.log:14`). Aceito.
- **R-2.** Entre o go (04:59:09.62) e o fim da guarda passaram 15,5 s. Foi o 1.º `sudo podman inspect` de isn3 que demorou (`03-sudo-pre-armar-e-pos.log:1-2`); os seguintes levaram de 40 a 70 ms. Aconteceu antes de armar e de qualquer `tc`, então não afeta o L-0c. No S4, uma demora assim no `pid_de` atrasa o início da janela, mas não muda a medida, que vem dos marcadores do próprio injetor.
- **R-3.** O systemd 255 não registra o código de saída quando ele é 0. A unit transiente não fica carregada, então o `ExecMainStatus` não pode mais ser lido. O rc 0 se infere de `Deactivated successfully`, da ausência de `Failed with result` e de `is-failed: inactive`. O rc do próprio `l0c.sh` é 0 (`vm/l0c.log:830-833`, `abortar` com `rc=0` e `LIMPO=1`). A linha `rc=0 fim=05:00:54` (`vm/l0c.log:834`) é do invólucro do Gestor e veio 56 s depois do fim do script (último arquivo às 04:59:58.858, `02-pos-l0c-leitura.log:24`). A linha não altera nada, mas o Gestor deve dizer qual foi o comando, para saber se aquele `rc` é o `PIPESTATUS` do script ou de um comando posterior.
- **R-4 (limite da prova byte a byte).** Não existe `systemctl cat` de uma unit armada pelo `armar` verbatim para comparar (o L-0 não gravou isso). A igualdade do `.service` está provada pela construção: mesmos argumentos do `systemd-run`, exceto a propriedade do timer. O `.timer` da injeção real **difere**, porque usa `AccuracySec` padrão (1 min). Continua valendo a R-3 da V-S2: no L-1, a cura pelo dead-man pode chegar até 60 s depois do prazo.
- **R-5 (limite).** O L-0c prova que o dead-man do **netdegrade** dispara e cura sozinho com o cluster saudável e o `podman` respondendo. Não prova a cura com perda real no fio (L-1), nem o dead-man do `inject-partition.sh` (S7, G-5b-B), nem a queda da VM ou da sessão tmux.
- **R-6.** `vm/l0c/qdisc-durante.txt:1-2,5-6` contém linhas do `xtrace` (o `2>&1` dentro do `bash -x`). É cosmético e não muda o conteúdo.

## O que vem no S4 (REP=4R)

Definição aprovada pelo Gestor em 27/10 (P-2, `docs/relatorio-s2-2026-10-27.md:3`; desenho em `docs/validacao-v-s1-2026-10-27.md` §5.2). É uma réplica da célula da REP=3: `REP=4` explícito no go, `g1.sh` sob `flock` com janela de 90 s em isn3 (`inject-partition.sh --minority isn3`), `g1b` fora do lock com `COLETA=minima`, log de saúde a 2 s e `G1_LOG_POS_S=30`. Critérios de validade e verificação conforme `decisoes-senior-2026-10-16.md` §5.5, itens 1 a 9: janela de 96,04 ± 0,2 s, split nos dois lados, `MergeView` de 3 nos 3 logs, ≥ 3 amostras `HEALTHY` com 3 nós depois do `end`, sem `ISPN000524/100014/638`, sem `clsact` residual. Se passar de 60 s, parar. A leitura nesse caso é "a célula novo + mínima não é estável", não "histórico".

Pré-requisitos, registrados antes do go:
1. **Decisão do Gestor sobre a idade do cluster** (ver abaixo).
2. Portão 5.1 imediatamente antes, sob lock, com o `.log` gravado (evitar a R-1: rodar o comando numa linha só ou num script).
3. Hashes conferidos: `inject-partition.sh` `59dd2bee…94e1`, igual ao S0 (`04-s4-pre-hashes-e-cluster.log:1`, `docs/relatorio-gate-v4-2026-10-16.md:25`); `g-lib.sh` `af3436d5…22dd`, igual ao S0; `g1.sh` `4645159b…8990` e `g1b.sh` `546aa6b0…6fd8`, iguais ao worktree. O bundle de referência agora é o v4c (`902654b`). Só `l0c.sh` e `g5b.sh` mudaram desde o S0 (R-1 da V-S2), e nenhum dos dois entra no S4. O Gestor precisa aceitar o v4c como referência no lugar do "hash do bundle igual ao de S0" da §5.5.
4. `uname -r` (6.8.0-147) e `boot_id`; `topologyId`/vista atual `[isn2-7049|2] (3)`; 0 partições neste cluster (`runs/gate-b29/go.log` só tem entradas da VM antiga, `04-s4-pre-hashes-e-cluster.log:5-9`); 0 `netem` que toque tráfego desde a criação (L-0 e L-0c usaram só o filtro TEST-NET).
5. Gestor presente durante a janela e o merge (cerca de 15 min no total).
6. Ordem mantida: S4 antes de L-1 (`docs/validacao-v-s1-2026-10-27.md:82`).

## Pontos para o Gestor antes do S4

- **Idade do cluster.** Os contêineres foram criados em 27/10 às 18:48:51 a 18:48:54 (`04-s4-pre-hashes-e-cluster.log:10-12`), então o cluster tem cerca de **10 h 15 min** agora. A REP=3 rodou com cerca de 10 min. A V-S1 aceitou declarar a diferença porque esperava cerca de 1 h. Com 10 h, "cluster novo" deixa de descrever bem a célula. Opções: (a) recriar com `recriar.sh` (versionado e testado, mesmo procedimento do S2) logo antes do S4, depois portão 5.1 e S4 em seguida (recomendo, porque é a única forma de a REP=4R replicar de fato a REP=3; L-0 e L-0c não precisam ser refeitos, já que não dependem das JVMs); (b) manter o cluster e declarar a idade de cerca de 10 h como diferença da REP=3.
- **Referência de hashes.** Aceitar o bundle v4c (`902654b`) como referência do S4 (pré-requisito 3).
- **R-3.** Dizer qual foi o comando que imprimiu `rc=0 fim=05:00:54`.
