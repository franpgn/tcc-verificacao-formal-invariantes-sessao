# V-S2: validação do S2 (L-0 na VM), 2026-10-28

Validador: Senior. Só leitura na VM `172.18.251.140` (árvore `~/tcc-gate`, HEAD `9eadbdb`) e no worktree `wt-gate-v4` (`test/b29-gate-v4`). Critérios: `decisoes-senior-2026-10-16.md` §5.4 (S2), roteiro `relatorio-b29-2-implementacao-2026-10-16.md` §5, comando e checklist pós-L-0 de `docs/relatorio-s2-2026-10-27.md` §6. Nenhum `go`, nenhuma escrita de rede, nenhum push.

## Veredito

**APROVADO, com ressalvas.** Nenhuma ressalva invalida o L-0. A R-1 precisa de decisão do Gestor antes do S3.

## Brutos

| Arquivo | Conteúdo |
|---|---|
| `vm/l0-console.log` | console `bash -x` da execução válida (cópia por `tar`, sha256 `cfcf3f95…70a5` igual na VM e no host, 0 CR, 134 LF) |
| `vm/l0-inicio.txt` | `date -Is` antes do `l0.sh` |
| `vm/go.log` | `runs/gate-netdegrade/go.log` da VM (1 linha) |
| `11-copia-l0-vm-host.log` | cópia e hashes VM/host (a linha `CR em ...: 134` é falso positivo do `grep -c` do Git Bash, corrigida no fim do próprio log) |
| `12-pos-l0-leitura.log` | tc depois, ip/ss no netns, timers/units/journal do dead-man, podman logs, tmux, lock |
| `13-pos-l0-sudo-e-warn.log` | `sudo` de 03:10 a 03:16:01 (inclui a tentativa abortada) e classes de WARN/ERROR |
| `14-portao-51-pos-l0.log`, `vm/portao51-2.csv` | portão 5.1 depois do L-0 (CSV saiu por stderr, nada gravado na VM) |
| `15-s3-propriedade-accuracy.log` | nome da propriedade de precisão de timer no systemd 255 (preparo do S3) |
| `16-hashes-boot-pos-l0.log` | hashes dos scripts, `uname -r`, `boot_id` e módulos depois do L-0 |

O `portao51.sh` foi lido antes de rodar. Ele só faz `curl` GET de health e `size`, `systemctl list-*`, `podman inspect`/`logs`, `nsenter ... tc qdisc/filter show` e `flock -n ... true`, que não altera estado. Por isso foi rodado de novo (130 s).

## Critérios do L-0 (§5.4)

| # | Critério | Evidência | Resultado |
|---|---|---|---|
| 1 | Go do Gestor digitado no terminal e registrado | `vm/l0-console.log:13-20`; `vm/go.log:1` (`go 2026-10-28T03:16:00+00:00 vm=172.18.251.140 bloco=L-0`) | ok |
| 2 | Scripts executados são os do bundle | `16-hashes-boot-pos-l0.log:3-7` iguais a `08-l0-pre.log:4-7` e ao worktree (`l0.sh` `38325ff0…31f7`, `l-lib.sh` `9bdd1fae…73fd`, `inject-netdegrade.sh` `4ebdd8a3…7abb1`) | ok |
| 3 | `TCC_DIR` aponta para `~/tcc-gate` (achado da §5 do relatório S2) | `vm/l0-console.log:4`, `:40` (`--working-directory=/home/server1/tcc-gate`) | ok |
| 4 | Guarda de netns antes de qualquer `armar`/`tc` | `vm/l0-console.log:21-37` (PID 33811, `net:[4026532291]` ≠ `net:[4026531840]`) | ok |
| 5 | Dead-man armado antes do primeiro `tc` de escrita | `vm/l0-console.log:38-40`; `12-pos-l0-leitura.log:86-87` (systemd-run 03:16:00.168, timer iniciado 03:16:00.192); 1.º `tc add` em `13-pos-l0-sudo-e-warn.log:18` (03:16:00.204) | ok |
| 6 | Cada `tc` com rc 0, incluindo `gemodel`, `reorder`, `corrupt`, `distribution normal` | `vm/l0-console.log:52-61` com `set -euo pipefail` (`:1`); o script só chega a `sem_prio` (`:62`) e a `rc_l0=0` (`:134`) se todos tiveram rc 0; `13-pos-l0-sudo-e-warn.log:18-22` mostra os 5 comandos executados como root | ok |
| 7 | `tc qdisc show dev eth0` antes e depois idênticos | antes: `vm/l0-console.log:51` e `vm/l0-tc-antes.txt:1`; depois: `vm/l0-console.log:82`, `12-pos-l0-leitura.log:5-10` (`diff` → `IGUAL`), filtros 0 (`:11-13`), todas as interfaces `noqueue` (`:14-17`) | ok |
| 8 | Sem resíduo `prio`/`netem` | `vm/l0-console.log:83-86` (`ok sem prio em isn3`); `14-portao-51-pos-l0.log:12-20` (0 proibidos e filtros vazios nos 3 nós) | ok |
| 9 | IPv4 só, sem IPv6 no TCP do JGroups | `12-pos-l0-leitura.log:19-26` (10.89.1.4/24), `:27-37` (só `::1` e `fe80::`, 0 global), `:38-47` (`ss -tn` só 10.89.1.x, 0 linhas IPv6 em 7800/57800) | ok |
| 10 | Dead-man desarmado e 0 timers/units | `vm/l0-console.log:87-89`; journal `12-pos-l0-leitura.log:80-84` (Started → Deactivated → Stopped, sem execução do `--heal`); `:70-79` (0 timers, 0 units); `14-portao-51-pos-l0.log:11` | ok |
| 11 | `--dry-run` do injetor ok | `vm/l0-console.log:90-131` (`dry-run: nada executado`) | ok |
| 12 | Duração < 120 s | `vm/l0-inicio.txt:1` 03:15:48, go 03:16:00, `vm/l0-console.log:134` fim 03:16:08 (20 s no total; armar → desarmar em 0,11 s, `12-pos-l0-leitura.log:86,88`) | ok |
| 13 | `uname -r`, `tc -V`, módulos | `08-l0-pre.log:18-19,22-24`; depois `16-hashes-boot-pos-l0.log:8,10-12` (6.8.0-147, mesmo `boot_id` de `08-l0-pre.log:20`, módulos com refcount 0, igual ao ponto de partida da R-5) | ok |
| 14 | Nenhuma mudança de vista causada pelo L-0 | `12-pos-l0-leitura.log:146,150,154` (0 `ISPN000094/093/436/SUSPECT` desde 03:00 nos 3 nós); última vista continua `[isn2-7049\|2] (3)` de 27/10 18:49:06 (`:148,152,156`) | ok |
| 15 | Cluster saudável e estado final limpo | portão 5.1 em `14-portao-51-pos-l0.log:4-28`: 27 amostras × 3 portas, 0 ruins, 131 s contínuos HEALTHY com 3 nós (`vm/portao51-2.csv`, 81 linhas `200 HEALTHY 3 0`), caches vazios, 0 timers, `noqueue` nos 3 netns, lock livre, 0 mudanças de vista nos 5 min finais | ok |
| 16 | Sessão e lock liberados | `12-pos-l0-leitura.log:158-167` (sem servidor tmux, lock `LIVRE`, nenhum lock do injetor em `/tmp`) | ok |

## Ressalvas

- **R-1 (bloqueia o S3 até decisão).** `l0c.sh:68` lê `systemctl show <timer> -p AccuracySec`. No systemd 255 da VM essa propriedade não existe e a saída é vazia. O nome certo é `AccuracyUSec` (`15-s3-propriedade-accuracy.log:3-7`). A atribuição `--timer-property=AccuracySec=1s` de `l0c.sh:32` está correta, o problema é só a leitura. Sem correção, o bruto do S3 não mostraria o `AccuracyUSec=1s` que a V-S1 exige. Opções para o Gestor: (a) Worker corrige `l0c.sh:68` para `-p AccuracyUSec`, ajusta o teste por shims, gera novo bundle com hash, e a VM recebe o bundle antes do S3 (recomendo); (b) manter o script e aceitar como evidência a linha `AccuracySec=1s` do `systemctl cat` do timer (`l0c.sh:66`), que é a unidade transiente gravada, mas não o valor efetivo.
- **R-2.** A tentativa abortada de 03:12 não deixou console próprio (o `tee` da execução válida sobrescreveu `l0-console.log` e `l0-inicio.txt`). A prova de que ela não tocou a rede vem do journal: só dois `sudo true` (03:11:59 e 03:13:11, do `sudo -n true && flock ...`), nenhum `podman inspect`, `systemd-run` ou `tc` antes das leituras do coordenador às 03:13:59 (`13-pos-l0-sudo-e-warn.log:3-11`). O `go.log` tem uma linha só. Aceito.
- **R-3.** O dead-man do L-0 usa o `armar` verbatim, com `AccuracySec` padrão do `systemd-run` (1 min). No L-0 isso não importa (parado em 0,11 s). No L-1 o prazo real de cura é a duração + 90 s mais até 60 s de coalescência. Registrar no planejamento do S5.
- **R-4.** Os 185 a 188 WARN por nó desde 03:00 são todos `RestRequestHandler` do `GET .../health/status` sem autenticação (healthcheck do podman) (`13-pos-l0-sudo-e-warn.log:28-34`). A taxa é a mesma numa janela de controle de 27/10 23:00-23:19, 190 em isn3 (`:37-38`). Ruído preexistente, não causado pelo L-0.
- **R-5 (limite).** O L-0 prova que o kernel 6.8.0-147 aceita `prio`, `netem` (com `gemodel`, `reorder`, `corrupt`, `distribution normal`) e `u32` no netns de isn3, sem resíduo. Não prova que o filtro `u32` casa o tráfego JGroups, nem a taxa de perda no fio (L-1/L-2), nem nada em isn1/isn2 além do estado `noqueue`.
- **R-6.** Relógio do cliente Windows cerca de 1 a 2 s atrás da VM (`12-pos-l0-leitura.log:175-176`). Os carimbos que contam são os da VM.

## O que fica para o S3 (L-0c)

1. Decisão do Gestor sobre a R-1 e, se (a), correção de `l0c.sh:68`, teste por shims, novo bundle conferido por hash e checkout na VM.
2. Pré: portão 5.1 imediatamente antes; go do Gestor no terminal (`exigir_go "L-0c"`), sob `flock /var/lock/tcc-vm.lock`, `TCC_DIR=$HOME/tcc-gate`.
3. Critérios da §5.4 (S3) mais os da V-S1: o `systemd-run` real executa `scripts/inject-netdegrade.sh --heal` (journal com início, fim e rc da unit); `tc qdisc show dev eth0` de isn3 volta a `qdisc noqueue 0: root refcnt 2` sem intervenção; disparo entre 30 s e 40 s após armar; unit sem `failed`; isn1/isn2 não tocados; 0 timers ao fim.
4. Precisão do timer nos brutos: `systemctl show <timer> -p AccuracyUSec` = `1s` (enquanto armado), `systemctl cat` do timer e do service (o service byte a byte igual ao que `armar` gera na injeção real, só com o prazo diferente) e o carimbo do disparo no journal.
5. Nenhum comando de cura manual nos consoles. Se a rede de segurança (`abortar` → `--heal`) disparar, o bloco conta como FALHA e é refeito com novo go.
