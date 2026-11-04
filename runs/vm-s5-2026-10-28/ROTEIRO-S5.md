# Roteiro do S5 (L-1 e L-2), 2026-10-28

Preparado pelo Worker. Não foi executado na VM. Referências: `decisoes-senior-2026-10-16.md` §5.1 e §5.6 (na branch `docs/decisoes-senior-2026-10-16`), `runs/vm-s4-2026-10-28/V-S4.md` (seção "O que o S5 precisa", P-2, P-5 e P-6) e `runs/vm-s4-2026-10-28/ROTEIRO-S4.md`, que serve de modelo.

## O que muda em relação ao `l1.sh`

O `runs/gate-netdegrade/l1.sh` é verbatim do roteiro e **não foi editado**. O `s5.sh` o chama como está (`bash runs/gate-netdegrade/l1.sh`) e faz em volta as coletas que no `l1.sh` são só comentário (`l1.sh:8-13`).

**Divergência de porta, registrada e não corrigida no `l1.sh`.** O comentário de `l1.sh:10` mede `http://127.0.0.1:11222/...`, que é o REST de **isn1**. A §5.6 pede o REST **de isn3**, que é a porta **11224**. O `s5.sh` mede isn3 em 11224 e mantém 11222 como controle. O `s5.test.sh` (T-s5-0) confere que a linha 10 do `l1.sh` continua dizendo 11222, ou seja, que o arquivo não foi tocado.

## Antes do GO, o Gestor precisa saber

- **Dead-man do L-1.** O `rodar_inj 60` arma `--on-active=150 s` (`l-lib.sh:32-34`), mas o timer do `systemd-run` usa `AccuracySec` de 1 min (R-3 da V-S2, R-4 da V-S3). **A cura automática pode chegar só uns 210 s depois de armar.** A cura manual continua disponível, em `~/tcc-gate`: `PODMAN='sudo podman' bash scripts/inject-netdegrade.sh --heal`. O `s5.sh` imprime este aviso logo antes do prompt do GO.
- **O GO é digitado pelo Gestor** no prompt do próprio `l1.sh` (`exigir_go "L-1"`), que só aceita `GO` vindo de um terminal. O `s5.sh` não digita nada. A §5.6 diz que o go do L-1 já foi concedido, mas esse go é anterior à troca de VM e às quedas do host, e a V-S4 (P-6) recomenda um novo.
- **Host.** Nada de TLC, `mvn` ou carga pesada no host, do início da linha de base até o fim do portão pós (V-S4, item 7). O `s5.sh` registra o `boot_id` no início e no fim. Um intervalo do saude-log maior que 2× o nominal invalida o bloco.
- **Cluster recriado (04/11).** A queda de energia de 04/11 07:36 reiniciou host e VM (`boot_id` novo `b088606d…0546`, IP `172.27.127.98`). O coordenador recriou o cluster às 19:14:06Z com `runs/gate-b29/recriar.sh` (XML `8792dea3…`, o mesmo do gate v4): size=3 `HEALTHY`, vista `[isn1-54943|2] (3)`. O S5 não roda mais no cluster do S4. O passo 0 só **registra** IDs, `StartedAt`, IPs e a última vista atuais; nenhuma vista é exigida. O S6 compara os IDs e `StartedAt` com os deste `00-pre.log` (D-4).
- **`boot_id` por argumento (Q-11).** Nada de `boot_id` no código. O Gestor passa `--boot-esperado <uuid>`, o `boot_id` lido na abertura (`cat /proc/sys/kernel/random/boot_id`). O passo 0 aborta se o atual for diferente; a avaliação confere o do início e o do fim contra ele.
- **`go.log` versionado (Q-12).** A regra é a do S6/S7: a única modificação versionada aceita é `runs/gate-b29/go.log` com o conteúdo do `HEAD` como prefixo exato e cada linha a mais começando com `go ` (na VM, a linha do GO da REP=4 do S4). Qualquer outro arquivo modificado, linha antiga alterada, linha a mais sem `go ` ou o arquivo no índice é NÃO OK. Não é preciso restaurar o arquivo (a Q-01 deixa de bloquear).

## Como rodar

```bash
cd ~/tcc-gate
sha256sum runs/vm-s5-2026-10-28/s5.sh   # esperado 287665752b648f3c1d2381f92ad5427444a26e07917ff16de027daef186addac
cat /proc/sys/kernel/random/boot_id    # conferir contra o lido na abertura (desde a queda de 04/11: b088606d-5d07-4424-b308-0a58fedf0546)
flock /var/lock/tcc-vm.lock tmux new -s s5
# dentro do tmux:
bash runs/vm-s5-2026-10-28/s5.sh --boot-esperado <uuid>
# ao fim: exit (libera o lock)
```

## O que o `s5.sh` faz

| Passo | Ação | Arquivo em `vm-<UTC>/` | rc em `rc.txt` | Se falhar |
|---|---|---|---|---|
| — | Argumentos: `--boot-esperado` obrigatório, UUID minúsculo; qualquer outro argumento é erro | nada (nem o diretório é criado) | — | rc 1 |
| 0 | Pré-condições, só leitura: `TCC_DIR=$HOME/tcc-gate` (exporta se vazio, aborta se diferente), tmux, terminal, lock OCUPADO, `sudo -n`, `uname -r` = 6.8.0-147-generic, `boot_id` atual = `--boot-esperado` (início, Q-11), HEAD, contém v4c, regra do `go.log` (Q-12: só `runs/gate-b29/go.log` com linhas `go ` acrescentadas), sha256 da tabela, sha256 do `s5.sh`, ambiente sem `LOCK_DIR`/`PODMAN`/`INJ`/`SONDA_*`/..., java, jar = o do S4 (`3594c690…7bee`), `runs/gate-netdegrade/L1.log` e `marcadores.txt` inexistentes, 0 timers e 0 units `tcc-heal`, `go.log` do netdegrade, `podman ps`, IP/ID/`StartedAt`/última vista dos 3 nós (só registro, nenhuma vista exigida), PID de isn3 e guarda de netns (diferente do host), JGroups de isn3 sem IPv6 (`ss -tn`, parada obrigatória da §5.6), NIC da VM sem `prio`/`netem`/`clsact` | `00-pre.log`, `parametros.txt` | `pre` | rc 1, nada tocado |
| 1 | Linha de base **sem F4**, 60 s: `/health` nas portas 11222/11223/11224 (`http_code`, `time_connect`, `time_total`, estado, nós) e conexão TCP ao `sshd` da VM a cada 2 s; `ping -c 300 -i 0.2` do host a isn3; sonda Hot Rod (`SondaHotrod`, a mesma do g1b) pinada em `isn3=127.0.0.1:11224`, `--duracao-s 60`, que remove a chave no fim | `01-base.log`, `base/saude.csv`, `base/ping.txt`, `base/sonda-isn3.{csv,err}` | `base` | rc 2 (falta amostra, resumo do ping ou `remove-final` OK nos 2 caches) |
| 2 | Portão 5.1 pré (`portao51.sh 130`) com `.log` e `.csv`, avaliação automática (a mesma do `s4.sh`). Confere também que a sonda da base não deixou chave (`size=0`) | `02-portao51-pre.{log,csv,avaliacao}` | `02-portao51-pre`, `02-portao51-pre-avaliacao` | rc 3: **S5 não roda** |
| 3 | `TcpRetransSegs` dos 3 netns e NIC da VM. Coletor em segundo plano. Aviso do dead-man. **`l1.sh` neste terminal, com o GO do Gestor** | `03-pre-l1.log`, `04-l1.log`, `04-coletor.log` | `pre-l1`, `l1`, `coletor` | rc 4: ver abaixo |
| 3a | Coletor, a partir do `event=start` do injetor: `tc -s qdisc` e `tc -s filter` de isn3 em +10, +30 e +50 s; `ping -c 250 -i 0.2` do host a isn3; `/health`, `time_connect` de 11224 e ssh a cada 2 s; sonda Hot Rod em isn3. Segue até 30 s depois do `event=end` | `janela/tc-{10,30,50}.txt`, `janela/ping.txt`, `janela/saude.csv`, `janela/sonda-isn3.{csv,err}` | `coletor` | (registro) |
| 4 | Cópias de `runs/gate-netdegrade/{L1.log,marcadores.txt,go.log}` com sha256; `TcpRetransSegs` depois; NIC; timers e units; linhas de vista e `DEGRADED_MODE` dos 3 nós desde o início do L-1 | `l1/`, `l1.sha256`, `05-pos-l1.log`, `05-vistas.log` | `pos-l1` | (registro) |
| 5 | Portão 5.1 pós, com fim ≥ fim do L-1 + 345 s (a regra de "0 mudanças de vista em 5 min" vale com folga) | `06-portao51-pos.{log,csv,avaliacao}` | `06-portao51-pos`, `06-portao51-pos-avaliacao` | rc 5 |
| 6 | `boot_id` do fim | `07-boot-fim.txt` | `boot-fim` | (entra na avaliação) |
| 7 | Avaliação automática (abaixo) | `08-avaliacao.txt` | `avaliacao` | rc 6: INVÁLIDO |

O console inteiro vai para `s5-console.log`. O rc de cada passo é lido na mesma linha do pipeline (`... | tee "$OUT/NN-....log"; RC=${PIPESTATUS[0]}`), que é a lição da R-3 da V-S3. O `s5.test.sh` confere isso de forma estática em todas as linhas `| tee "$OUT/NN-...`.

**Falha do `l1.sh` (rc 4).** Sem o GO, o `l1.sh` sai antes de armar e o coletor é encerrado sem coletar. Se o injetor falhar depois de armar, o `rodar_inj` aborta com o dead-man **armado**: a cura automática vem em até ~210 s. Se o `l1.sh` sair no L-2 (`RESIDUO em isn3`), o dead-man **já foi desarmado** e a cura tem de ser manual. Nos dois casos o `s5.sh` grava `05-pos-falha.log` (timers e `tc qdisc` dos 3 netns), copia o `L1.log` e preserva as amostras da janela. O portão pós não roda.

## Avaliação automática (`08-avaliacao.txt`)

Cada linha começa com `OK:`, `FALHA:`, `RESULTADO:` ou `LEITURA HUMANA:`. A última é `S5: VALIDO (...)` ou `S5: INVALIDO (...)`. A avaliação pode ser refeita no host, sobre a cópia: `bash runs/vm-s5-2026-10-28/s5.sh --avaliar runs/vm-s5-2026-10-28/vm-<UTC>`.

Invalidam o bloco (`FALHA`):

| Critério | Origem | Regra |
|---|---|---|
| `boot_id` | V-S4, P-2; Q-11 | igual ao `--boot-esperado` no início **e** no fim |
| rc do `l1.sh` e do injetor | §5.6 | `l1 rc=0` em `rc.txt` e `rc=0 (esperado 0)` no `L1.log` |
| Marcadores | §5.6 | 1 `start` e 1 `end` de `F4 isn3`, `end` > `start` |
| Perdas do ping do injetor | §5.6 | para isn1 e isn2, perdas = percentual × 300 / 100 (arredondado) entre **39 e 81** |
| Contadores do `netem` | §5.6, V-C1 | `dropped` > 0 e `dropped/(Sent+dropped)` a 3 σ binomiais de 0,20 (piso 2/n), a mesma regra do injetor |
| Amostras de `tc` | §5.6 | ≥ 2 amostras dentro da janela com `netem 30:` e Sent+dropped > 0, e `tc -s filter` com exatamente 2 filtros `flowid 1:3`, `match` = IPs de isn1 e isn2 |
| REST de isn3 (11224) | §5.6 | HTTP 200 em todas as amostras da janela e da base; mediana do `time_total` na janela ≤ p95 da base |
| Hot Rod de isn3 | §5.6 | mediana do `time_connect` à porta 11224 (a mesma porta do Hot Rod) na janela ≤ p95 da base |
| Controle isn1 (11222) | | mesma regra do `time_total` |
| ssh | §5.6 | todas as conexões TCP ao `sshd` aceitas; mesma regra de latência; NIC da VM sem `prio`/`netem`/`clsact` no início e depois do L-1 |
| Ping do host a isn3 | §5.6 | sem perda na base e na janela; mesma regra de latência (rtt) |
| Intervalo do saude-log | V-S4, P-2 | intervalo máximo entre amostras da mesma fonte ≤ 2 × 2 s, na base e na janela |
| L-2 e resíduos | §5.6 | `ok sem prio em isn3` no `04-l1.log`; 0 timers e 0 units depois do L-1; portão 5.1 pós OK (size=3 HEALTHY, caches vazios, 3 netns sem `prio`/`netem`/`clsact` e sem filtros) |

Não invalidam (`RESULTADO`), conforme a §5.6: mudança de vista ou `DEGRADED_MODE` nos logs desde o início do L-1, amostras do saude-log fora de `HEALTHY`/3 e o `TcpRetransSegs` antes e depois dos 3 nós (hipótese H-L: aumento em isn3). Uma mudança de vista é resultado do experimento, mas bloqueia o S6 até um portão 5.1 e muda a posição da REP=4B.

Ficam para leitura humana (`LEITURA HUMANA`):
- a latência das operações da sonda Hot Rod (put/get, base contra janela). Ela inclui a replicação para o outro dono (`SYNC`, `owners=2`), que passa de propósito pelo enlace degradado, então não serve de critério automático para "o filtro não pega o Hot Rod". O critério automático é a conexão TCP à porta 11224;
- o ssh do terminal do Gestor (Windows para a VM). O `s5.sh` só mede, de dentro da VM, a conexão ao `sshd` e a NIC sem `prio`/`netem`. Atraso percebido no terminal durante a janela deve ser anotado;
- o crescimento do `success` dos 2 filtros `u32` entre as amostras de `tc`;
- o `05-vistas.log` e o tempo de reforma, se houver mudança de vista;
- as perdas são calculadas do percentual que o injetor registra no `L1.log`. O injetor apaga a saída bruta do `ping` (`transmitted/received`) e não pode ser editado, então a contagem supõe 300 transmitidos (o `ping -c 300` só transmite menos se for interrompido, e aí o injetor sai com erro).

**Limiares da regra de latência (decisão do Gestor de 2026-11-04, com base em `docs/rastreabilidade-limiares-s5-s7-2026-11-04.md`, 4.1 e 4.2).** A regra é "mediana da janela ≤ p95 da base", sem folga (`FOLGA_LAT_S=0`): é a leitura literal de "latência dentro da dispersão da linha de base" (§5.6). A folga de 1 ms que havia antes não tinha fonte. O p95 só existe de fato com pelo menos 20 amostras (com n < 20 o p95 por posto mais próximo do `stats()` é o máximo), então a base exige **≥ 20 amostras por fonte** (`BASE_MIN_N=20`), no passo 1 (rc 2) e na avaliação. A base de 60 s com período de 2 s dá ~30 por fonte, e o ping da base, 300. As duas constantes vão para `parametros.txt` e a avaliação lê dali. A prova de que o filtro não pega o host continua nos critérios de `tc -s filter` (só os IPs de isn1 e isn2) e no ping sem perda, que não dependem de número sem fonte.

## Duração estimada

Cerca de **11 min** do comando ao fim, mais o tempo até o GO. O prompt do GO aparece por volta de **3,5 min** (pré-condições em segundos, linha de base 60 s, portão pré 130 s mais as leituras finais). Depois do GO: cerca de 65 s de janela (300 pings a 0,2 s), alguns segundos de L-2, 30 s de coleta pós-janela e o portão pós até o fim do L-1 + 345 s, uns 6 min. O Gestor precisa ficar presente do GO até o fim da janela (§5.6, "Gestor presente nos 60 s") e, se o injetor falhar, até a cura (até ~210 s depois de armar).

A sonda ao `sshd` é um `curl http://<ip-da-VM>:22/`: o `sshd` responde com o banner SSH, o `curl` recusa a resposta e sai na hora, e o tempo de conexão (`time_connect`) é o medido pelo próprio `curl`. Cronometrar um processo (`timeout`/`bash`) mediria o fork, que cresce com a carga da VM durante a janela e daria falso positivo. Em troca, o `sshd` registra uma troca de banner inválida a cada 2 s, cerca de 100 linhas no `auth.log` ao longo do bloco.

## Tabela de hashes (referência v4c, `902654b`)

| Arquivo | sha256 | Também conferido na VM em |
|---|---|---|
| `scripts/inject-netdegrade.sh` | `4ebdd8a34a7d5c1e6981138272a4f0b90a67c44dd8626e9521884e185297abb1` | S2 (`runs/vm-s2-2026-10-27/16-hashes-boot-pos-l0.log:6`) |
| `runs/gate-netdegrade/l-lib.sh` | `9bdd1faeca0399d11886ee5c77189825c18f5bf69d78797c579c1fd71e9d73fd` | S2 (`16-hashes-boot-pos-l0.log:3`) |
| `runs/gate-netdegrade/l1.sh` | `9f3deacc6c408d2e70125ede52d409d87b876202cd33b370277b6330775e1253` | S1 (`runs/vm-s1-2026-10-27/04-hashes-scripts.log`) |
| `runs/vm-s2-2026-10-27/portao51.sh` | `2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338` | S2, S3, S4 |

Os 4 arquivos não mudaram entre `902654b` e a ponta desta branch (T-s5-0). O jar da sonda é conferido à parte: `3594c690cfacd2be1f88e58d2a65a4cd944a06688abcb9ae5b4d3df2f7dc7bee`, o mesmo da REP=3 e do S4.

## Teste

`bash runs/vm-s5-2026-10-28/s5.test.sh`, sozinho: **460 PASS, 0 FAIL em 730 s** (2026-11-04, depois do `--boot-esperado`, da regra do `go.log` e dos limiares rastreáveis; Git Bash, a maior parte do tempo em criar processos de shim no Windows). Vermelho visto antes de cada mudança: 284/154 (Q-11/Q-12) e 442/18 (limiares). A versão original, 356 PASS, foi vista vermelha antes do `s5.sh` existir. O `s4.test.sh` segue com 216 PASS, 0 FAIL. O teste não prova podman, tc, netem, systemd, Infinispan, a sonda Java nem os scripts reais, que têm testes próprios (`l-gate.test.sh`, `inject-netdegrade.test.sh`).

## Como o `s5.sh` chega à VM

Só pelo bundle **v4h** (`C:\Users\Francesco\Development\tcc\gate-v4h.bundle`, incremental de `1266c12`, HEAD da VM, até a ponta desta branch, com `.sha256`), conferido por sha256 e com autorização do Gestor (Q-02). Antes do S5, na VM, `sha256sum runs/vm-s5-2026-10-28/s5.sh` deve dar `287665752b648f3c1d2381f92ad5427444a26e07917ff16de027daef186addac` (o `8bce87a5…0da08` anterior, com `boot_id` fixo, não serve mais).
