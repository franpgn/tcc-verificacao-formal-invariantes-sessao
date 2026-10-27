# Relatório S2 (VM), 2026-10-27: preâmbulo, portão 5.1 e preparo do L-0

Executor: Worker. Gestor presente; aprovou em 27/10 P-1 (recriar o cluster com o XML atual da v4), P-2 (S4 vira REP=4R, fora desta tarefa) e P-6 (segurar o kernel e pausar o unattended-upgrades). Referências: `validacao-v-s1-2026-10-27.md`, `decisoes-senior-2026-10-16.md` §5.1, §5.2 e §5.4, roteiro L-0 em `relatorio-b29-2-implementacao-2026-10-16.md` §5. Brutos em `runs/vm-s2-2026-10-27/` (`00` a `09`, `recriacao/`, `vm/`). Este documento não se autoaprova.

**Estado: o S2 está parado antes do L-0.** O `l0.sh` exige `GO` digitado num terminal (`exigir_go`, `[ -t 0 ]`), e por isso o comando ficou para o Gestor (seção 6). O passo 1 (segurar o kernel) ficou **incompleto**: o `apt-mark hold` foi recusado porque há um unattended-upgrade travado desde 21/10 segurando o lock do dpkg (seção 2).

Registro (R-1/R-2): todo bloco remoto tem o carimbo do cliente no início e no fim, o comando literal, `remoto_inicio`/`remoto_fim` (`date -Is` na VM) e o rc do ssh. O relógio da VM está cerca de 1 s à frente do host (ex.: `07`, cliente_inicio 18:51:19.6Z, remoto_inicio 18:51:20).

## 0. Mitigação do ssh (`00`)

3× `ssh ... true`: 1058 ms, 272 ms, 261 ms. Todos < 2 s. Nenhum bloco desta sessão teve atraso de cliente fora do esperado: a maior diferença entre `duracao_cliente_ms` e a duração remota foi de cerca de 1 s.

## 1. Passo 0, sem VM: `recriar.sh` e R-4 (commit `12b9295`)

- `runs/gate-b29/recriar.sh`: cópia por `scp -p` de `~/recriar.sh`. sha256 na VM, no arquivo do host e no blob do índice: `5b1cca41a2d059acf76f0f1511053dde290a883f983cc266ad79ff9360480766` (`01`). Modo git 100755.
- `runs/gate-b29/recriar.test.sh` (novo, shims de sudo/podman-compose/podman/nsenter/curl/systemctl num HOME temporário): **15 PASS, 0 FAIL** (`02`). Ele prova a ordem `down` → `up`, sempre com `-f cluster/podman-compose.yml` a partir de `~/tcc-gate`, que o único `tc` é `tc qdisc show dev eth0` (3 chamadas, nenhuma de escrita) e o caminho ESTÁVEL. Não prova podman nem Infinispan reais.
- R-4: os `novos_apos_checkout=4` são `runs/gate-b29/g1-rep3/hotrod/` (diretório; 114 arquivos, dos quais 110 não ignorados e 4 ignorados por `*.csv`/`*.txt`) e `runs/gate-b29/recriacao/pre-recriacao-inspect-isn{1,2,3}.json`. Lista completa em `01` (`git status --porcelain --untracked-files=all`). Trouxe e versionei (com `add -f`) o `hotrod/` inteiro e o `recriacao/` inteiro. Este último inclui também os ignorados da R4 de 16/10: `pre-recriacao-isn{1,2,3}.log`, `idade-pre.txt`, `rec1/down.log`, `rec1/up.log`. sha256 conferidos um a um entre a VM e o host (`01`).
- Erro meu, sem efeito: o `find -newer /dev/null` do primeiro bloco de `01` listou só um arquivo. Os tamanhos e mtimes corretos estão no bloco seguinte.

## 2. Passo 1, segurar o kernel e pausar o unattended-upgrades (`03`, `04`)

Antes (`03`):
- `uname -r` = `6.8.0-147-generic`; `apt-mark showhold` vazio; `20auto-upgrades` com `Unattended-Upgrade "1"`; `apt-daily-upgrade.timer` e `apt-daily.timer` enabled e active; `unattended-upgrades.service` enabled e active. Nenhum kernel mais novo: o candidato de `linux-image-generic` é o 147 instalado.
- **Achado: há um unattended-upgrade travado desde 2026-10-21 06:56:28.** `apt-daily-upgrade.service` está em `activating (start)` há 6 dias (job 13045). O PID 2714 segura `/var/lib/dpkg/lock-frontend` e `/var/cache/apt/archives/lock`. A cadeia é `unattended-upgrade` → hook do needrestart → `systemctl restart fwupd-refresh.service fwupd.service ... udisks2.service` (PID 2954), e esse `systemctl` espera o job 956 de `fwupd-refresh.service`, que está `activating` desde 06:56:32 com `fwupdmgr refresh` (PID 2956), `Type=oneshot` e `TimeoutStartUSec=infinity`. Os demais serviços da lista reiniciaram às 06:56:32 e estão `active`. O dpkg em si terminou: `liblzma5` e `xz-utils` foram instalados às 06:56:31, e `dpkg --audit` sai com rc 0 e sem pendências. Ainda faltam, na lista daquela execução, `ca-certificates git git-man libxtst6 python3-jwt tzdata`. `systemctl is-system-running` = `degraded` (`apt-daily.service` failed).

Ações (`04`), todas dentro da aprovação P-6:
- `systemctl disable --now apt-daily-upgrade.timer apt-daily.timer`: rc 0.
- `systemctl disable unattended-upgrades.service`: rc 0. Não fiz `stop`: o serviço só espera sinal para o desligamento (`TimeoutStopUSec=30min`), e pará-lo com um upgrade em curso poderia pendurar.
- `apt-mark hold linux-image-6.8.0-147-generic linux-modules-6.8.0-147-generic linux-modules-extra-6.8.0-147-generic linux-headers-6.8.0-147 linux-headers-6.8.0-147-generic linux-generic linux-image-generic linux-headers-generic`: **rc 100**, `dpkg frontend lock was locked by another process with pid 2714`. Nenhum hold foi aplicado. Incluí os três metapacotes porque são eles que trariam uma ABI nova; os `linux-tools-*` ficaram de fora.

Depois (`04`): timers `disabled`/`inactive`, `unattended-upgrades.service` `disabled` (ainda active até o próximo boot), `apt-daily-upgrade.service` ainda `activating` com o lock, 0 holds e `uname -r` inalterado. Não houve upgrade nem reboot.

Leitura: para o S2 o risco de troca de kernel é nulo. O u-u travado não instala nada enquanto estiver travado, os timers estão desligados, `Automatic-Reboot` está no padrão (false) e não há kernel novo candidato. Para as noites do rebaseline o problema continua aberto: se o job do `fwupd-refresh` terminar ou for cancelado, o u-u retoma e instala os 6 pacotes restantes (nenhum é kernel), e o hold continua sem ser aplicado. Destravar exige matar ou cancelar processos do apt/systemd, o que está fora do que foi aprovado (ver seção 7).

## 3. Passo 2, preâmbulo de recriação (`05`, `06`)

Backup em `~/backup-s2-2026-10-27-pre-recriacao/` na VM (27 MB): `podman inspect` e `podman diff` dos 3 contêineres, `podman cp` de `/opt/infinispan/server/data` e `/opt/infinispan/server/log`. Para o host vieram os inspect, os diffs e os `data-isn*` (`recriacao/`, sha256 conferidos em `05`). Os logs (27 MB) ficaram só na VM.

- `___global.lck`: 0 byte nos 3 (mtime 2026-10-16 08:54:44/45). `___global.state` com 126 bytes: `@version=15.0.21.Final`, `@timestamp` de 16/10 08:54:44-45Z e um `uuid` por nó (isn1 `b68a47d1…`, isn2 `a8f93ae1…`, isn3 `2a3f5f43…`).
- `podman diff`: 24 linhas por nó (data/, log/ com `server.log` rotacionado em 16 e 17/10, `conf/jgroups-tcp.xml`, `generated-identities.batch`).
- O `server.log` de isn1 vai até 2026-10-18 07:50:01, 12 s depois do último registro no journald (07:49:49). Os de isn2 e isn3 terminam numa linha em branco ou de espaços, o que é compatível com um arquivo cortado na queda do host. Isso é só observação: não li byte a byte.

Confirmação de que o XML é o da v4 (`06`): HEAD da VM `9eadbdb`, `git status -- cluster/` vazio, sha256 de `infinispan-cluster.xml` `8792dea3…15af`, de `jgroups-tcp.xml` `dd793430…cf14` e de `podman-compose.yml` `46d5ff7c…a67cd`, iguais aos blobs de `9eadbdb` no host. Há 2 caches (`sessions`, `counters`, SYNC, owners=2, 64 segmentos). Não é o XML do B-42.1.

Recriação (`06`): `flock -n /var/lock/tcc-vm.lock bash ~/recriar.sh /home/server1/tcc-gate/runs/vm-s2-2026-10-27/rec-s2`:
- `down rc=0` (`podman stop -t 10` e `rm` dos 3), `up rc=0`. O `down.log` saiu byte a byte igual ao de 16/10 (mesmo sha256 `0b6e6fa7…`), porque não tem carimbo.
- `ESTAVEL size=3 HEALTHY: primeira leitura boa +14s, estavel por 21s (total 35s)`, `t0_up_epoch=1793126935` (18:48:55Z). timers tcc-heal 0, `noqueue` nos 3.
- **Instante de criação:** isn1 `2026-10-27 18:48:51.499Z` (StartedAt 18:48:51.840), isn2 `18:48:52.910Z` (53.061), isn3 `18:48:54.127Z` (54.277). IDs novos: `7a196f163609`, `1c89dacd64c2`, `5bf9476b5b97`. RestartPolicy vazia.
- **Vista:** `[isn2-7049|2] (3) [isn2-7049, isn1-64005, isn3-27776]` às 18:49:06,16-,20 nos 3 nós. Coordenador isn2.
- **topologyId:** `sessions` e `counters` terminam em **topology id 9** (`ISPN100010 Finished rebalance with members [isn2-7049, isn3-27776, isn1-64005], topology id 9`, coordenador, 18:49:07,90). Os caches internos seguem a mesma sequência (`07`).
- Avisos na subida (`07`): `ISPN080059` (sem script engines), `ISPN000599` (encoding), `ISPN080072` (JMX), `JOIN ... timed out (after 2000 ms)` (2 em isn1, 1 em isn3) e 1 `JGRP000006 ... EOFException` em isn2 durante a formação. Nenhuma menção a global state nos logs novos (0 linhas). São os avisos esperados de uma formação simultânea, mas não os comparei linha a linha com os da R4 de 16/10.
- A sessão inteira levou 91 s no cliente. Desse tempo, cerca de 51 s são das conferências que vêm depois do ESTÁVEL (`podman logs` completo pelo journald). Não é atraso de ssh: o `remoto_fim` é 18:50:21.

## 4. Passo 3, portão 5.1 (`07`, `vm/portao51-1.csv`, `portao51.sh`)

`runs/vm-s2-2026-10-27/portao51.sh` (novo, só leitura, sha256 `2598b37d…2338`, o mesmo na VM). Amostra as 3 portas a cada 5 s. Uma amostra conta como ruim se não for `cluster_health.health_status=HEALTHY` com `number_of_nodes=3` e http 200, ou se tiver `HEALTHY_REBALANCING|DEGRADED|FAILED|UNHEALTHY` em qualquer parte do corpo, inclusive em `cache_health`.

| Condição | Medido | OK |
|---|---|---|
| 3 portas HEALTHY, size=3, sem HEALTHY_REBALANCING, >= 120 s | 51 rodadas (153 amostras), 0 ruins, janela de 252 s (18:51:20 a 18:55:32) | sim |
| `sessions`/`counters` com tamanho 0 | 0 nas 3 portas, para os 2 caches | sim |
| 0 timers/units `tcc-heal*` | 0 / 0 | sim |
| `tc qdisc show` nos 3 netns | só `noqueue` em `lo` e `eth0`; 0 `clsact/prio/netem/ingress`; `tc filter show dev eth0` vazio | sim |
| lock livre | `LIVRE` | sim |
| nenhuma mudança de vista nos últimos 5 min | conferido às 18:56:3x: 0 linhas `ISPN000094/093/100000/100001/MergeView` em `--since 5m` nos 3; a última vista é de 18:49:06 (7,5 min antes) | sim |

`boot_id` `b7df4b03-6652-4808-9617-11096693e65b`, o mesmo do início da sessão.

## 5. Pré-L-0 (`08`), só leitura

- HEAD `9eadbdb`; sha256 de `l-lib.sh` `9bdd1fae…73fd`, de `l0.sh` `38325ff0…31f7` e de `inject-netdegrade.sh` `4ebdd8a3…7abb1`, iguais aos da §2 de `relatorio-gate-v4-2026-10-16.md`; 0 `\r`.
- `uname -r` 6.8.0-147-generic; `tc -V` iproute2-6.1.0, libbpf 1.3.0.
- R-5 (ponto de partida dos módulos): `sch_prio`, `sch_netem` e `cls_u32` carregados, refcount 0.
- isn3: PID 33811, netns `net:[4026532291]` ≠ `net:[4026531840]` do PID 1. `eth0` 10.89.1.4/24. IPv6 só `::1` e link-local `fe80::…/64`. Em `ss -tna`, todo o tráfego de 7800 e 57800 é IPv4, com 0 linhas IPv6 em 7800.
- `tc qdisc show dev eth0` antes: `qdisc noqueue 0: root refcnt 2` (gravado em `vm/l0-tc-antes.txt`); filtros vazios. 0 timers, lock livre, nenhuma sessão tmux.
- **Achado que afeta o comando do L-0:** `~/tcc-desenvolvimento` existe na VM. O `l-lib.sh` faz `cd "${TCC_DIR:-$HOME/tcc-desenvolvimento}"`. Sem `TCC_DIR=$HOME/tcc-gate`, o `l0.sh` carregaria a biblioteca de `~/tcc-gate` e depois iria rodar o `inject-netdegrade.sh`, e armar o dead-man, a partir da árvore antiga. O comando da seção 6 exporta `TCC_DIR`.

## 6. L-0: comando para o Gestor digitar (não executado)

O `exigir_go` lê `GO` de um terminal. O Worker não simula o go. O Gestor digita, no próprio terminal:

```
ssh -i ~/.ssh/id_ed25519_tccf3 -o IdentitiesOnly=yes server1@172.18.251.140
sudo -n true && flock /var/lock/tcc-vm.lock tmux new -s l-gate
```

Dentro da sessão tmux `l-gate`, numa única linha:

```
cd ~/tcc-gate && export TCC_DIR=$HOME/tcc-gate && date -Is | tee runs/vm-s2-2026-10-27/l0-inicio.txt && bash -x runs/gate-netdegrade/l0.sh 2>&1 | tee runs/vm-s2-2026-10-27/l0-console.log; echo "rc_l0=${PIPESTATUS[0]} fim=$(date -Is)" | tee -a runs/vm-s2-2026-10-27/l0-console.log
```

No prompt `L-0 -- go do Gestor registrado no relatorio anterior? [digite GO]`, digitar `GO`. Ao terminar (`L-0 concluido sem residuo`, `rc_l0=0`), sair do tmux com `exit`, o que libera o lock.

- `bash -x` não muda o script. Só registra cada comando antes de executá-lo. Com o `set -e`, chegar à linha final prova rc 0 em cada `tc`, e a última linha `+ T tc ...` antes de um aborto mostra qual falhou.
- Se abortar com o dead-man armado: `bash scripts/inject-netdegrade.sh --heal` (com `TCC_DIR` exportado e dentro de `~/tcc-gate`), ou esperar 210 s.
- Depois do L-0, o Worker (nova tarefa) faz a parte só de leitura: `tc qdisc show dev eth0` depois contra `vm/l0-tc-antes.txt` (diff), `ss -tna` e `ip addr` no netns, 0 timers e units `tcc-heal*`, `journalctl` do `tcc-heal-*` (armado e parado), `podman logs --since <l0-inicio>` sem `ISPN000094/093` e o portão 5.1 de novo.

## 7. Pontos para o Gestor e o Senior

1. **Kernel hold (P-6) incompleto.** Para aplicar o hold é preciso liberar o lock do dpkg. Opções, todas escrita fora do aprovado:
   - (a) `sudo systemctl stop apt-daily-upgrade.service`, que mata o u-u travado (o dpkg já terminou e o audit está limpo) e libera o lock. Em seguida o `apt-mark hold` da seção 2. Recomendo esta.
   - (b) Cancelar o job 956 (`fwupd-refresh`). O u-u então retoma e **instala** os 6 pacotes restantes, o que contraria o "nada de upgrade".
   - (c) Deixar como está até o fim da campanha, porque o u-u travado já impede qualquer instalação. É frágil.

   Em qualquer caso, `fwupd-refresh.timer` também dispara um `fwupd-refresh` que pode travar de novo. Sugiro incluir o `disable` desse timer.
2. Registrar no P-28 (integração) o u-u travado desde 21/10, porque é estado da VM que estava escondido. O sistema fica `degraded` (`apt-daily.service` failed).
3. Aceitar o `portao51.sh` como instrumento do portão nas S3..S7. Ele é só leitura, mas não estava no roteiro.
4. Aceitar `bash -x` e `TCC_DIR` no comando do L-0 (seção 6).

## 8. Escritas na VM nesta sessão

`systemctl disable` (2 timers e o `unattended-upgrades.service`); backup em `~/backup-s2-2026-10-27-pre-recriacao/`; `down`/`up` do cluster (P-1); `~/tcc-gate/runs/vm-s2-2026-10-27/` (`rec-s2/`, `portao51.sh`, `portao51-1.csv`, `l0-tc-antes.txt`), não versionado na VM. Não houve `tc` de escrita, netem, `systemd-run`, upgrade, reboot nem push.
