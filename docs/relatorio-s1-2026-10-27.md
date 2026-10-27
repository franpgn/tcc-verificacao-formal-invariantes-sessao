# Relatório S1 (VM, só leitura), 2026-10-27

Executor: Worker. Gestor presente; autorizou o início do S1 nesta sessão. Referências: `decisoes-senior-2026-10-16.md` seções 5.1 e 5.3, bloco B0 de `relatorio-b29-2-implementacao-2026-10-16.md`, ressalvas 1 e 2 de `validacao-v-s0-2026-10-16.md`. Brutos (comando, saída literal e rc de cada passo) em `runs/vm-s1-2026-10-27/` (arquivos `00` a `14`). Este documento não se autoaprova; a V-S1 do Senior deve ler os brutos.

Nada de `tc` de escrita, netem, `nsenter`, `systemd-run`, start/stop/rm de contêiner, workload ou push. As únicas escritas na VM foram o bundle em `~`, o checkout em `~/tcc-gate` (seção 2), dois backups em `~`, o `modprobe` do B0 e o arquivo `/var/lock/tcc-vm.lock` criado pelo `flock`.

## 1. VM e precondições (`00-ambiente.log`)

| Item | Medido |
|---|---|
| Endereço | `server1@172.18.251.140`, MAC `00:15:5d:03:ac:00` (confere) |
| Uptime | boot 2026-10-20 16:31:44 UTC (7 dias) |
| `G_TESTE` | ausente em `env` (ssh), em `bash -lic`, em `sudo env` e nos arquivos de perfil (`grep` rc=1/2): ressalva 2 da V-S0 atendida |
| `/var/lock/tcc-vm.lock` | não existia no início; criado por `flock` em 17:59 (dono `server1`), livre no fim |
| timers/units `tcc-heal*` | 0 |
| tmux | nenhuma sessão |
| java | nenhum processo |

## 2. Bundle e checkout (`01`..`04`)

- sha256 na VM `39e9f100bbef535e138506010374ace447b49b2501882e46a80aefdca028f6c0` = `.sha256` (`SHA256_OK`); `git bundle verify` OK, ref única `refs/heads/test/b29-gate-v4` = `9eadbdbe30f0bc8635f88e273c775b43c615e147`.
- Antes: `~/tcc-gate` em `test/b29-gate-vm-v3` `507c21e`, sem modificações rastreadas; não versionados `runs/gate-b29/{g1-rep2,g1-rep3,recriacao}/` e vários ignorados.
- **O primeiro `git checkout` foi recusado pelo git** (rc=1, nada alterado): a v4 versiona 156 caminhos que existiam na VM como não versionados (146 de `g1-rep2/`, 3 de `g1-rep3/`, `g1b-rep2.log`, `g1-console-rep2.log`, `go.log`, 1 de `g5/`, 2 de `g6/`, 1 de `recriacao/`). Comparação `git hash-object` contra os blobs da v4: **156 comparados, 0 diferentes**.
- Procedimento (nada apagado): tar de `runs/gate-b29` inteiro em `~/backup-s1-2026-10-27-runs-gate-b29.tgz` (376 entradas = `find`), os 156 movidos para `~/backup-s1-2026-10-27-conflito/` (lista em `~/s1-conflito-lista.txt`), `git checkout test/b29-gate-v4` rc=0, `cmp` dos 156 restaurados contra o backup: 0 diferentes. Entradas de `runs/gate-b29` ausentes após o checkout: 0.
- Depois: HEAD `9eadbdb` (`HEAD_OK`), sobram 4 não versionados (`g1-rep3/hotrod/`, `recriacao/pre-recriacao-inspect-isn{1,2,3}.json`).
- 0 arquivos com `\r` em `scripts/`, `runs/gate-netdegrade/`, `runs/gate-b29/*.sh`.
- Os 9 hashes de script da seção 2 do `relatorio-gate-v4-2026-10-16.md` conferem na VM (l-lib, l0, l1, l0c, g5b, inject-netdegrade, inject-partition, g56-lib, g-lib). Modos git `100755`/`100644` como no relatório.
- `cluster/` idêntico entre `507c21e` e `9eadbdb` (`git diff --stat` vazio); `podman-compose.yml` sha256 `46d5ff7c...a67cd`.

## 3. B0 (`05-b0.log`)

| Item | Medido |
|---|---|
| `command -v` | nsenter, tc (`/usr/sbin/tc`), flock, sudo, ping, systemd-run, curl, awk, tmux: todos presentes |
| `tc -V` | `tc utility, iproute2-6.1.0, libbpf 1.3.0`; `normal.dist` presente |
| `uname -r` | `6.8.0-147-generic` (o boot anterior, de 16 a 18/10, rodava `6.8.0-124-generic`) |
| outros | systemd 255, util-linux 2.39.3, tmux 3.4, podman 4.9.3 |
| Módulos antes | `sch_prio`, `sch_netem`, `cls_u32` ausentes de `lsmod` e de `/sys/module` (não carregados desde o boot) |
| `modprobe` | os 3 rc=0; depois presentes em `lsmod` e `/sys/module`, arquivos `.ko.zst` em `/lib/modules/6.8.0-147-generic/kernel/net/sched/` |

Isto só mostra que os módulos carregam no kernel 6.8.0-147. Se o kernel aceita os parâmetros reais, isso continua sendo o L-0 (S2).

## 4. Suítes (flock real, uma por vez)

Antes de rodar, li os cabeçalhos e os pontos de chamada. As execuções sem `--dry-run` usam um PATH em que os shims (`podman`, `sudo`, `nsenter`, `tc`, `ping`, `readlink`; `flock` só fora de T-nd-21/T-par-15) vêm antes de `/usr/bin:/bin`. O `tc` real (`/usr/sbin`) fica fora do PATH, e os `--dry-run` saem antes de qualquer chamada externa. Na VM `podman` e `sudo` reais estão em `/usr/bin`, mas ficam sempre atrás dos shims. Os dois casos chamados com o PATH real (`--help` e `--loss 101`) saem por validação de argumento (exit 2) antes de chamar qualquer coisa. `LOCK_DIR` aponta para o `mktemp` da suíte.

| Suíte | Comando | Resultado | rc | Duração |
|---|---|---|---|---|
| `inject-netdegrade.test.sh` | `flock -n /var/lock/tcc-vm.lock bash scripts/inject-netdegrade.test.sh` | **306 PASS, 0 FAIL, 0 SKIP** | 0 | 84 s |
| `inject-partition.test.sh` | `flock -n /var/lock/tcc-vm.lock bash scripts/inject-partition.test.sh` | **127 PASS, 0 FAIL, 1 SKIP** | 0 | 38 s |

- netdegrade: igual ao WSL da V-S0 (306/0/0). O T-nd-21 (flock real, 8 PASS) roda aqui; é o caso que o Git Bash pulava (298/0/1).
- partition: o SKIP é o T-par-6 (`run-baseline.sh` não integra F2, escopo de B-29.1). Ele é estrutural e não depende do host, então 0 SKIP não é alcançável nesta suíte. Em relação ao Git Bash (125/0/2), T-par-15 (flock real) roda e soma 2 PASS.

## 5. Estado do cluster (portão 5.1), só leitura (`08`, `10`, `11`, `14`)

| Item | isn1 | isn2 | isn3 |
|---|---|---|---|
| Status | created | created | created |
| Created | 2026-10-16 08:54:38.65 | 08:54:39.83 | 08:54:41.02 |
| StartedAt | 2026-10-16 08:54:38.76 | 08:54:39.96 | 08:54:41.14 |
| FinishedAt | 0001-01-01 (zero) | zero | zero |
| ExitCode / OOMKilled | 0 / false | 0 / false | 0 / false |
| RestartPolicy | (vazia) | (vazia) | (vazia) |
| Pid | 0 | 0 | 0 |

- Projeto compose `cluster`, `working_dir=/home/server1/tcc-gate/cluster`, podman-compose 1.0.6, rede `cluster_infinispan-net` existente. O rótulo `PODMAN_SYSTEMD_UNIT=podman-compose@cluster.service` não corresponde a nenhuma unit instalada ("could not be found").
- `podman logs --tail 50` (driver journald): as 50 linhas de cada nó são o WARN `ISPN012026` do healthcheck. A última é de 2026-10-18 07:49:49 (isn1), 07:49:45 (isn2), 07:49:47 (isn3).
- Fora do healthcheck, o journal dos contêineres tem 0 linhas depois de 2026-10-16 09:07. A última mudança de vista foi `MergeView::[isn2-15987|6] (3)` em 2026-10-16 09:06:06, seguida de `Finished rebalance` em `sessions`/`counters` às 09:06:07.
- `tc qdisc show` no netns do host: `noqueue` em `lo`, `mq` + `fq_codel` em `eth0` (padrão). **Nos netns dos contêineres a verificação não se aplica**: Pid=0, sem netns. Por isso o portão 5.1 (3 portas HEALTHY por >= 120 s, `sessions`/`counters` vazios, `noqueue` nos 3 netns) **não pode ser avaliado** com o cluster parado.
- Estado final igual ao inicial (`14-estado-final.log`): nada foi iniciado, parado, removido nem recriado.

## 6. Por que os contêineres estão em Created

Evidência (`09`, `10`, `12`):
1. `journalctl --list-boots`: boot -1 de 2026-10-16 01:45:35 a **2026-10-18 07:49:49**; boot 0 em 2026-10-20 16:31:46. `last -x` registra o boot de 16/10 como "still running", sem linha de shutdown. O mesmo vale para o journal do boot -1: nenhum `Reached target Shutdown/Power-Off`, nenhum `Journal stopped`. As últimas linhas são healthchecks normais (`healthy`) dos 3 nós.
2. `podman events` (desde 15/10): o último evento dos contêineres antes do fim do boot -1 é `health_status` em 07:49:49. Não há `died`, `stop` nem `cleanup` depois de 08:54 de 16/10. O evento seguinte é `system refresh` em 2026-10-20 16:31:48, emitido pelo `podman.service` no boot.
3. O Infinispan não registrou `ISPN080002: Infinispan Server stopping` depois de 2026-10-16 08:54:3x, que é o `down` da recriação. Em 24/06, por comparação, o desligamento ordenado da VM gerou essa linha nos 3 nós.
4. Sem restart policy, o `podman-restart.service` do boot não reinicia nada.

Conclusão: a VM foi encerrada de forma abrupta (desligamento forçado ou perda do host) em algum momento logo depois de 2026-10-18 07:49:49, com o cluster ocioso e saudável, e ficou desligada até 20/10 16:31. No boot, o `podman system refresh` restaurou os contêineres ao estado configurado (`created`), mantendo o `StartedAt` antigo e `FinishedAt` zero. Ninguém os parou nem reiniciou desde então. O build do host Hyper-V é igual nos dois boots (`10.0.26100.9711`), o que não indica atualização do host. A causa do desligamento fica fora do que a VM registra: não aparece no journal nem no apt. O `unattended-upgrade` de 17/10 só reiniciou serviços, sem reboot. O kernel 6.8.0-147 entrou neste boot.

**`recriar.sh` criou os contêineres atuais: sim.** `~/recriar.sh` (mtime 2026-10-16 08:54:30, sha256 `5b1cca41...0766`) faz `cd ~/tcc-gate; sudo podman-compose -f cluster/podman-compose.yml down` e depois `up -d`, valida HEALTHY size=3 e grava o resultado. O `~/rec1.out` (08:55:06) registra `down rc=0`, `up rc=0`, `ESTAVEL ... total 24s`, StartedAt 08:54:38.758899275 / 08:54:39.959685667 / 08:54:41.138260104, idênticos ao `inspect` de hoje. `podman events` mostra `remove` às 08:54:38 e `create`/`start` às 08:54:38 a 41. É a recriação R4 do gate v3 (`relatorio-tb-v3-execucao-2026-10-16.md` seção 2). O `recriar.sh` não está versionado.

Implicação para S4 (decisão do Senior/Gestor, não do Worker): o histórico do cluster que a REP=4B queria preservar (contêineres de 16/10 08:54, uma partição REP=3 e um merge) já se perdeu no processo. Qualquer `start` cria JVMs novas, com vista nova e caches vazios, nos mesmos contêineres. Na prática isso equivale a uma recriação sem `rm`.

## 7. Observações

- Dois comandos remotos triviais levaram cerca de 2 min para retornar (17:55:48 a 17:57:50, o `lsmod` antes do `modprobe`; 18:05:14 a 18:07:29, o primeiro `sudo podman ps`). O ssh medido depois (3 vezes) responde em 205 a 237 ms. A causa não foi investigada; a carga ficou baixa (loadavg < 0,8, 7,3 GB disponíveis).
- O `modprobe` do B0 deixa `sch_prio`, `sch_netem` e `cls_u32` carregados (refcount 0, sem uso).
- Ficam em `~` na VM: `gate-v4.bundle`(+`.sha256`), `backup-s1-2026-10-27-runs-gate-b29.tgz`, `backup-s1-2026-10-27-conflito/`, `s1-conflito-lista.txt`. Se alguém voltar `~/tcc-gate` para a v3, o git **apaga** os 156 arquivos que agora são rastreados (o backup cobre esse caso).

## 8. Para validação (V-S1)

1. Aceitar o procedimento de checkout da seção 2: mover para backup arquivos idênticos aos blobs, em vez de usar `git worktree` em outro diretório.
2. Decidir como subir o cluster antes de S2, que precisa de PID/netns de isn3: `start` dos contêineres existentes ou recriação. Decidir também o que isso significa para a REP=4B (seção 6).
3. Ressalva 1 da V-S0 (`AccuracySec=1s` contra systemd real) continua aberta. Isso fica para S3 (L-0c), não para S1.
