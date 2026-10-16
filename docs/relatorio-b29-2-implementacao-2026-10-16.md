# Relatório de implementação de B-29.2 (`inject-netdegrade.sh`, F4) — 2026-10-16

Branch `feature/b29-2-inject-netdegrade` (base `b56c6ea`), worktree `wt-tc`. Sem VM, sem push. Retomada: os commits `196e0f0` (suíte) e `ca4cc52` (script) já estavam completos; esta rodada reverificou tudo e corrigiu um desvio do plano (nome das severidades).

## 1. Estado encontrado e o que mudou

- Script e suíte completos: 827 e 813 linhas, docs (`degradacao-de-rede.md`, `testes.md`, `configuracao-infinispan.md`) e relatório curto de 2026-10-15 já existiam.
- `bash -n` limpo. Suíte reexecutada sem confiar no relato anterior: Git Bash 284 PASS / 0 FAIL / 1 SKIP; WSL Ubuntu 292 PASS / 0 FAIL / 0 SKIP.
- **Defeito encontrado**: severidades ainda chamadas `S1..S5`; o plano (Q-8) manda `N1..N5` (colisão com os cenários S1/S2/S3). Corrigido em script, suíte e três docs; `S1..S5` agora é rejeitado com exit 2 (teste novo T-nd-6g). Commit `1bbf237`.
- Resultado final: Git Bash 285 PASS / 0 FAIL / 1 SKIP (T-nd-21 pede `flock` real); WSL 293 PASS / 0 FAIL / 0 SKIP.

## 2. Conformidade com os critérios de aceite do plano (T-C1)

| Item | Situação |
|---|---|
| Suíte vermelha antes do script | registrado no commit `196e0f0` (sem script 0 PASS / 1 FAIL; contra stub `exit 0`, 93 PASS triviais / 191 FAIL) |
| `bash -n` | limpo |
| `--dry-run` com `<pid>`/`<ip-par>` | coberto (T-nd-20 e anteriores) |
| `tc` sempre via `nsenter` | assert em script e shims |
| Exit codes 0..7 | documentados no cabeçalho, em `--help` e em `degradacao-de-rede.md` |
| Mermaid | presente em `degradacao-de-rede.md` |
| Perda `random`/`gemodel`, atraso+jitter, duplicação, reordenação (exige delay), corrupção, assimetria (`--loss-in`), `--direction`, `--severity N1..N5`, `--heal`, marcador `fault=F4`, trap/flock/pré-limpeza, exit 5/6/7 | presentes e cobertos |
| Exclusão com F3 (raiz) e F2 (`clsact` + lock) | exit 7 |
| Arquivos tocados | script, suíte, docs; `run-baseline.sh` intacto |

## 3. Mutações (todas mortas)

Copias do script em diretório temporário, suíte completa contra cada uma (Git Bash):

| Mutante | Resultado |
|---|---|
| sem nenhum `trap` | falhas em T-nd-12a/c/e/f e T-nd-13a (resíduo, marcador `end` ausente) |
| checagem de raiz alheia desativada | 6 FAIL (T-nd-18a/b: exit 5 em vez de 7, `qdisc add` indevido) |
| checagem de `clsact` desativada | 3 FAIL (T-nd-18d) |
| marcador `fault=F4` -> `fault=F3` | 4 FAIL (T-nd-5n, T-nd-9) |
| `remover_degradacao` no cleanup neutralizada | falhas em T-nd-9/10 (resíduo, falta `qdisc del`) |

Obs.: tirar só `trap 'exit 143' TERM` é mutante equivalente (o bash roda o `trap EXIT` ao morrer por TERM), já registrado em `docs/testes.md`.

## 4. O que os shims NÃO provam

- Que o kernel da VM aceita `sch_prio`, `sch_netem`, `cls_u32` e os parâmetros `gemodel`, `reorder`, `corrupt`, `distribution normal`. O kernel do WSL2 testado não tem `sch_prio`/`sch_netem`; a sintaxe do `tc` **nunca** foi validada contra um kernel.
- Que o `u32` casa o tráfego JGroups (7800) e poupa Hot Rod (11222/11224) e SSH.
- Que o `netem` produz a taxa configurada; o formato real de `tc -s qdisc` e de `ping` (os *parsers* foram escritos contra o formato documentado de iproute2/iputils).
- `sudo nsenter` com podman rootful e netavark; nome real da interface (`eth0`?); IPv4-only no JGroups.
- Interação com TCP, JGroups (`FD_ALL3`) e Infinispan; as hipóteses H-L1..H-L7 não foram testadas.
- Final de linha: o repositório usa `autocrlf=true`; para rodar na VM a partir de um clone/bundle, o LF é garantido por `.gitattributes` (branch `chore/gitattributes-eol-lf`, V-C1 R1).

## 5. Roteiro do gate L-0..L-6 na VM (nada foi executado; revisado após V-C1 e V-C1b)

Pré: chave SSH do Gestor instalada (T0.2), `tmux`, cluster rootful `isn1..isn3` de pé e **ocioso**, e o IP da VM reconferido (em 2026-10-15: `192.168.137.150`, usuário `server1`; muda se o host reiniciar). O código chega à VM por `git bundle`/clone (blobs LF, ver `.gitattributes`), nunca por `scp` da árvore do Windows.

**Forma de execução (V-C1b).** O roteiro não é uma colagem contínua. São arquivos `.sh` (a biblioteca `l-lib.sh` e `l0.sh`, `l1.sh`, `l3.sh`, `l4.sh`, `l5.sh`, `l6.sh`, transcritos abaixo) que o operador grava em `~/tcc-desenvolvimento/runs/gate-netdegrade/` (fora do versionamento; fazem parte do roteiro) e executa com `bash`, cada um com `set -euo pipefail`: a guarda e qualquer falha **abortam de verdade**. Cada arquivo começa por `exigir_go`, que pede `GO` digitado num terminal (sem terminal, aborta), e só é iniciado depois que o **relatório do passo anterior** registra o "go" explícito do Gestor, com IP da VM e data/hora. Em execução automatizada o agente principal **para ao fim de cada bloco e pergunta ao Gestor**; não encadeia blocos, e o `exigir_go` não substitui o registro escrito.

| Bloco | Conteúdo | Pré-condição (go registrado) |
|---|---|---|
| B0 | preparo, registro do ambiente, módulos, suíte na VM | go de T-C2 |
| `l0.sh` | L-0: o kernel aceita? (o `add` manual troca a raiz do `eth0` de isn3) | relatório de B0 + go do Gestor |
| `l1.sh` | L-1 e L-2: primeira injeção real do injetor, 60 s | relatório de L-0 + novo go (Gestor disponível nos 60 s) |
| `l3.sh` | L-3: sentido, por contadores | relatório de L-1/L-2 |
| `l4.sh` | L-4: N1..N5 x 2 repetições | relatório de L-3 |
| `l5.sh` | L-5: sinais; inclui `kill -KILL` (deixa o nó cortado até o `--heal`) | relatório de L-4 + go (Gestor disponível, Q-12) |
| `l6.sh` | L-6: coexistência com F3 e F2 | relatório de L-5 |

Parada obrigatória e retorno ao Senior: exit 5 em L-0 (módulo ou sintaxe), IPv6 no TCP do JGroups, filtro `u32` degradando Hot Rod/SSH, cluster que não reforma `size=3`.

### B0: preparo (sem alterar a rede)

```bash
ssh -i ~/.ssh/id_ed25519_tccf3 -o IdentitiesOnly=yes server1@192.168.137.150   # sem -i o ssh deu 'Permission denied' (relatorio L-0/L-1, 2026-10-16)
# ATENCAO: NAO colar o B0 inteiro de uma vez; o 'tmux new' abre outro shell e as linhas seguintes seriam perdidas/digitadas fora do lugar.
#   Colar so ate o 'tmux new' e o restante DENTRO da sessao tmux.
# exclusividade da VM: o flock envolve a sessao tmux; o servidor tmux herda o fd e o lock so e liberado quando a sessao encerra
sudo -n true && flock /var/lock/tcc-vm.lock tmux new -s l-gate      # sessao INTERATIVA (o Ctrl-C de L-5 precisa dela)
cd ~/tcc-desenvolvimento             # ajustar
git checkout test/b29-gate-v4        # branch do bundle de S0 (contem feature/b29-2-inject-netdegrade)
mkdir -p runs/gate-netdegrade
grep -rlI $'\r' scripts/ | wc -l     # esperado 0

for c in nsenter tc flock sudo ping systemd-run curl awk tmux; do command -v $c >/dev/null || echo "FALTA $c"; done
tc -V; ls /usr/lib/x86_64-linux-gnu/tc/normal.dist; uname -r          # registro do ambiente (P-11)
# um modulo por chamada; modulo embutido no kernel nao aparece em lsmod (conferir tambem /sys/module)
for m in sch_prio sch_netem cls_u32; do
  sudo modprobe $m || echo "FALHA modprobe $m"
  if lsmod | grep -q "^$m " || [ -d /sys/module/$m ]; then echo "ok $m"; else echo "FALTA $m"; fi
done
bash scripts/inject-netdegrade.test.sh               # flock real: 0 SKIP; contagem exata a registrar
```

Gravar o relatório de B0 e **parar: aguardar o "go" do Gestor para L-0**.

### Biblioteca `l-lib.sh` (carregada por `source` em cada bloco)

```bash
# runs/gate-netdegrade/l-lib.sh  (so definicoes; o set -euo pipefail fica no arquivo que a carrega)
cd "${TCC_DIR:-$HOME/tcc-desenvolvimento}"
export PODMAN="sudo podman"
LOG=runs/gate-netdegrade
INJ="bash scripts/inject-netdegrade.sh"
LOCK_DIR="${LOCK_DIR:-/tmp}"
mkdir -p "$LOG"

exigir_go() {   # parada real: so segue com o texto GO digitado num terminal
  [ -t 0 ] || { echo "ABORTAR: sem terminal; o go do Gestor deve ser dado interativamente" >&2; exit 1; }
  read -rp "$1 -- go do Gestor registrado no relatorio anterior? [digite GO] " r
  [ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
  echo "go $(date -Is) vm=$(hostname -I | awk '{print $1}') bloco=$1" | tee -a "$LOG/go.log"
}

# guarda de netns (R4): recusa PID vazio/0, readlink vazio ou netns igual ao do PID 1 (--network=host derrubaria o SSH)
guarda() {
  local pid="$1" ns1 nsn
  [ -n "$pid" ] && [ "$pid" != 0 ] || return 1
  ns1=$(sudo readlink /proc/1/ns/net) || return 1
  nsn=$(sudo readlink "/proc/$pid/ns/net") || return 1
  [ -n "$ns1" ] && [ -n "$nsn" ] && [ "$nsn" != "$ns1" ]
}
pid_de() {      # PID do no, ja validado pela guarda; aborta (exit 1) se invalido
  local p; p=$($PODMAN inspect -f '{{.State.Pid}}' "$1") || exit 1
  guarda "$p" || { echo "ABORTAR: PID invalido ou netns do host para $1 (pid='$p')" >&2; exit 1; }
  echo "$p"
}

# dead-man's switch POR INJECAO: duracao + 90 s, via systemd-run (sobrevive a queda do SSH).
# uso: armar <duracao-s> ; ... injecao ... ; desarmar   (desarmar so ao fim LIMPO da injecao)
armar()    { DMS="tcc-heal-$$-$RANDOM"
             sudo systemd-run --quiet --unit="$DMS" --on-active="$(( $1 + 90 ))s" \
               --setenv=PODMAN="sudo podman" --working-directory="$PWD" bash scripts/inject-netdegrade.sh --heal; }
desarmar() { sudo systemctl stop "$DMS.timer" 2>/dev/null || true; DMS=; }

# injecao em primeiro plano: arma, roda, grava log e rc (sem tee: o exit code nao se perde);
# desarma SO se rc == esperado; senao aborta o bloco deixando o switch armado.
# uso: rodar_inj <duracao-s> <rc-esperado> <arquivo-log> <args do injetor...>
rodar_inj() {
  local dur="$1" esp="$2" arq="$3" rc; shift 3
  armar "$dur"
  set +e; $INJ "$@" >"$arq" 2>&1; rc=$?; set -e
  cat "$arq"; echo "rc=$rc (esperado $esp)" | tee -a "$arq"
  [ "$rc" = "$esp" ] || { echo "ABORTAR: rc=$rc != $esp; dead-man's $DMS continua armado" >&2; exit 1; }
  desarmar
}
# contadores da banda 3 de um no: imprime "Sent dropped" (vazio se o no nao tem prio)
cont() { local p; p=$(pid_de "$1") || return 1
         sudo nsenter -t "$p" -n tc -s qdisc show dev eth0 \
           | awk '/^qdisc netem 30:/{f=1;next} f&&/Sent/{gsub(",","",$7); print $4, $7; exit}'; }
# falha (aborta) se ainda houver prio/netem no no
sem_prio() { local p out; p=$(pid_de "$1") || return 1
             out=$(sudo nsenter -t "$p" -n tc qdisc show dev eth0)
             if printf '%s\n' "$out" | grep -Eq 'prio|netem'; then echo "RESIDUO em $1" >&2; return 1; fi
             echo "ok sem prio em $1"; }
# espera os orfaos (sleep/ping herdam o fd do flock do injetor): so retorna com os locks livres
esperar_locks() { local n; for n in isn1 isn2 isn3; do
                    flock -w 200 "$LOCK_DIR/inject-netdegrade-$n.lock" true \
                      || { echo "ABORTAR: lock de $n preso (orfao?)" >&2; return 1; }
                  done; }
# cluster reformado: size=3 e HEALTHY nos tres nos, com limite (s)
espera_saudavel() {
  local lim="${1:-300}" t=0 n ip j ok
  while [ "$t" -lt "$lim" ]; do
    ok=1
    for n in isn1 isn2 isn3; do
      ip=$($PODMAN inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$n")
      j=$(curl -s -m 5 --digest -u admin:infinispan "http://$ip:11222/rest/v2/cache-managers/default/health" | tr -d ' \n' || true)
      case "$j" in
        *'"health_status":"HEALTHY"'*'"number_of_nodes":3'*|*'"number_of_nodes":3'*'"health_status":"HEALTHY"'*) ;;
        *) ok=0 ;;
      esac
    done
    [ "$ok" = 1 ] && { echo "saudavel apos ${t}s"; return 0; }
    sleep 5; t=$(( t + 5 ))
  done
  echo "RESULTADO: cluster nao reformou em ${lim}s (registrar; podman restart isn1 isn2 isn3)" | tee -a "$LOG/espera.log" >&2
  return 1
}
```

(`admin:infinispan` é credencial de laboratório e não vai para nenhum artefato publicado, P-11.)

### L-0: o kernel aceita? (`l0.sh`; sem resíduo; falha = exit 5, voltar ao Senior)

```bash
#!/usr/bin/env bash
# runs/gate-netdegrade/l0.sh
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-0"
PID=$(pid_de isn3)                 # a guarda ABORTA o script (exit 1) antes de qualquer armar/tc
armar 120                          # L-0 deve durar < 120 s
T() { sudo nsenter -t "$PID" -n "$@"; }
T ip -4 addr show                  # interface e IPv4; confirmar ausencia de IPv6 no TCP do JGroups (ss -tn no netns)
T tc qdisc show dev eth0           # raiz padrao (noqueue/fq_codel)
T tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
T tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20% 25% delay 20ms 5ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%
T tc qdisc change dev eth0 parent 1:3 handle 30: netem loss gemodel 5% 25% 100% 0%
T tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3
T tc qdisc del dev eth0 root
sem_prio isn3                      # sem prio/netem
desarmar
$INJ --target isn3 --severity N3 --direction both --dry-run
echo "L-0 concluido sem residuo"
```

Se algum `tc` falhar, o `set -e` aborta com o dead-man's armado (desfaz em até 210 s; ou rodar `bash scripts/inject-netdegrade.sh --heal` na hora). Gravar o relatório de L-0 e **parar: aguardar novo "go" para L-1**.

### L-1 e L-2: efetividade e limpeza (`l1.sh`)

```bash
#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-1"
PID=$(pid_de isn3)
# loss 20% por 60 s (cluster ocioso), SEM --loss-corr com verificacao por ping; criterio: exit 0
rodar_inj 60 0 "$LOG/L1.log" --target isn3 --loss 20 --duration 60 --ping-count 300 --marker "$LOG/marcadores.txt"
# durante a janela, em outro pane (gravar em $LOG):
#   sudo nsenter -t $PID -n tc -s qdisc show dev eth0 ; sudo nsenter -t $PID -n tc -s filter show dev eth0
#   curl -s -o /dev/null -w '%{time_total}\n' --digest -u admin:infinispan http://127.0.0.1:11222/rest/v2/cache-managers/default/health   # comparar com a linha de base SEM F4
#   ping -c 50 -i 0.2 <ip-isn3-host>    # sem degradacao esperada (Hot Rod/SSH poupados)
#   registrar dropped/(Sent+dropped) dos contadores, alem do ping
# esperado: exit 0, perda observada dentro de 3 sigma de 20%, dropped > 0
# L-2: limpeza
sem_prio isn3
sudo nsenter -t "$PID" -n tc filter show dev eth0
esperar_locks
```

### L-3: sentido por contadores (`l3.sh`; o ping mede ida+volta e NÃO discrimina o sentido)

```bash
#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-3"
sub() {   # sub <rotulo> <nos a ler...> -- <args do injetor>
  local rotulo="$1" nos=() rc=0 P n c; shift
  while [ "$1" != -- ]; do nos+=("$1"); shift; done; shift
  armar 60
  $INJ "$@" --duration 60 --ping-count 0 >"$LOG/L3-$rotulo.log" 2>&1 & P=$!
  sleep 25                                   # esperar sondagem/aplicacao antes de medir
  for n in "${nos[@]}"; do c=$(cont "$n") || exit 1; echo "$rotulo $n: $c" | tee -a "$LOG/L3-$rotulo.cont"; done
  wait "$P" || rc=$?; echo "rc=$rc" | tee -a "$LOG/L3-$rotulo.log"
  [ "$rc" = 0 ] || { echo "ABORTAR: rc=$rc; dead-man's $DMS armado" >&2; exit 1; }
  desarmar; esperar_locks
}
# out: dropped cresce so no alvo (isn3); isn1/isn2 nao tem prio
sub out isn3 -- --target isn3 --direction out --loss 30
# in: dropped cresce so nos pares (isn1, isn2), nao em isn3
sub in isn1 isn2 -- --target isn3 --direction in --loss-in 30
# both assimetrico: ~20% no alvo (saida) e ~50% nos pares (entrada); ping fim a fim esperaria 1-(0.8*0.5)=60%, so como checagem agregada
sub both isn3 isn1 isn2 -- --target isn3 --direction both --loss 20 --loss-in 50
```

### L-4: N1..N5, 120 s cada, 2 repetições (`l4.sh`; cluster ocioso; N5 depende do fix R3: Sent+dropped)

```bash
#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-4"
PID=$(pid_de isn3)
espera_saudavel 120                          # pre-condicao: size=3 HEALTHY antes de comecar
for rep in 1 2; do for sev in N1 N2 N3 N4 N5; do
  rodar_inj 120 0 "$LOG/L4-$sev-r$rep.log" --target isn3 --severity "$sev" --direction both --duration 120 --marker "$LOG/marcadores.txt"
  # registrar: mudancas de vista por no ($PODMAN logs isn1 | grep -i -E 'view|ISPN000094|merge'),
  # t_split, health, sudo nsenter -t $PID -n nstat -az TcpRetransSegs, RTT p50/p99 entre nos
  esperar_locks
  # N4/N5 podem partir o cluster: a proxima severidade so comeca com size=3 HEALTHY (limite 300 s). Se estourar,
  # o set -e aborta o bloco depois de gravar o RESULTADO em espera.log (podman restart e resultado, nao falha do gate).
  espera_saudavel 300
done; done
```

### L-5: sinais (`l5.sh`; esperado: sem prio após TERM/INT/HUP; após KILL a prio fica e o `--heal` remove)

`set -m` liga o controle de jobs: sem ele, `kill -INT` num job de fundo de shell não interativo é ignorado (SIGINT herdado como ignorado). Cada subteste tem o seu `armar` (duração + 90 s) e o `desarmar` ao fim limpo; no KILL, o `--heal` é parte do teste.

```bash
#!/usr/bin/env bash
set -euo pipefail
set -m
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-5 (inclui kill -KILL)"
PID=$(pid_de isn3)
sinal() {   # sinal <TERM|INT|HUP> <rc-esperado>
  local P rc=0
  armar 120
  $INJ --target isn3 --loss 20 --duration 120 --ping-count 0 >"$LOG/L5-$1.log" 2>&1 & P=$!
  sleep 30; kill -"$1" "$P"
  wait "$P" || rc=$?; echo "L-5 $1 rc=$rc (esperado $2)" | tee -a "$LOG/L5.log"
  [ "$rc" = "$2" ] || { echo "ABORTAR: rc=$rc; dead-man's $DMS armado" >&2; exit 1; }
  sem_prio isn3; desarmar; esperar_locks
}
sinal TERM 143
sinal INT 130        # SIGINT em job de fundo (set -m); o Ctrl-C em primeiro plano e repetido a mao (abaixo)
sinal HUP 129
# KILL: a prio AINDA fica (esperado); o --heal a remove
armar 120
$INJ --target isn3 --loss 20 --duration 120 --ping-count 0 >"$LOG/L5-KILL.log" 2>&1 & P=$!
sleep 30; kill -KILL "$P"; wait "$P" || true
out=$(sudo nsenter -t "$PID" -n tc qdisc show dev eth0)
printf '%s\n' "$out" | grep -q prio || { echo "ABORTAR: prio nao ficou (inesperado)" >&2; exit 1; }
echo "prio AINDA presente (esperado)"
$INJ --heal
sem_prio isn3
desarmar
esperar_locks        # sleep/ping orfaos seguram o fd do flock herdado; so segue com os locks livres
echo "L-5 concluido"
```

Ctrl-C em primeiro plano (a mão, no pane interativo do tmux: `source runs/gate-netdegrade/l-lib.sh; armar 120`, rodar o injetor sem `&`, Ctrl-C após ~30 s, depois `echo rc=$?; sem_prio isn3; desarmar`): esperado rc=130 e nenhum resíduo.

### L-6: coexistência (`l6.sh`; F3 e F2 com F4)

F3 convive com F4 SÓ com `--direction out` (toca apenas isn3). Cada subcaso tem o seu `armar` (duração do mais longo + 90 s) e o seu `desarmar`. O `armar` só desfaz `prio`, não o `clsact` de F2; por isso a partição F2 tem dead-man's próprio (`armar_part`), que chama `inject-partition.sh --heal`.

```bash
#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/l-lib.sh"
exigir_go "L-6"
IPART="bash scripts/inject-partition.sh"
armar_part()    { PDMS="tcc-heal-part-$$-$RANDOM"
                  sudo systemd-run --quiet --unit="$PDMS" --on-active="$(( $1 + 90 ))s" \
                    --setenv=PODMAN="sudo podman" --working-directory="$PWD" bash scripts/inject-partition.sh --heal --minority "$2"; }
desarmar_part() { sudo systemctl stop "$PDMS.timer" 2>/dev/null || true; }
ret() {   # ret <rc-esperado> <comando...>: confere o rc sem deixar o set -e matar antes do registro
  local esp="$1" rc=0; shift; "$@" || rc=$?; echo "rc=$rc (esperado $esp): $*" | tee -a "$LOG/L6.log"
  [ "$rc" = "$esp" ] || { echo "ABORTAR: rc=$rc != $esp" >&2; exit 1; }
}
bg_wait() { local rc=0; wait "$1" || rc=$?; echo "bg rc=$rc" | tee -a "$LOG/L6.log"; [ "$rc" = 0 ] || exit 1; }

# L-6a: F3 em isn2 + F4 em isn3, out: nos distintos, exit 0
armar 100
bash scripts/inject-jitter.sh --node isn2 --duration 100 & J=$!
ret 0 $INJ --target isn3 --direction out --loss 20 --duration 90
bg_wait "$J"; desarmar; esperar_locks

# L-6b: com in|both o injetor toca TODOS os pares (inclui isn2, com F3): exit 7 e nada alterado
armar 100
bash scripts/inject-jitter.sh --node isn2 --duration 100 & J=$!
sleep 10; ret 7 $INJ --target isn3 --direction both --loss 20 --duration 60
bg_wait "$J"; desarmar; esperar_locks

# L-6c: F3 no MESMO no do alvo: exit 7, nada alterado
armar 100
bash scripts/inject-jitter.sh --node isn3 --duration 100 & J=$!
sleep 10; ret 7 $INJ --target isn3 --loss 20 --duration 60
bg_wait "$J"; desarmar; esperar_locks

# L-6d: F2 (particao, clsact) + F4 no MESMO no: exit 7 (convivem tecnicamente; o injetor exclui por desenho experimental)
armar 100; armar_part 90 isn3
$IPART --minority isn3 --duration 90 & F=$!
sleep 10; ret 7 $INJ --target isn3 --loss 20 --duration 60
bg_wait "$F"; desarmar; desarmar_part; esperar_locks

# L-6e (flag conferida em inject-netdegrade.sh: --ping-count <n>, 0 desliga): F2 em isn2 + F4 em isn3 com --direction out: coexistem, exit 0
armar 100; armar_part 90 isn2
$IPART --minority isn2 --duration 90 & F=$!
sleep 10; ret 0 $INJ --target isn3 --direction out --loss 20 --duration 60 --ping-count 0   # F2 descarta todo IP (inclui ICMP): ping padrao daria ~100% de perda e exit 6 falso
bg_wait "$F"; desarmar; desarmar_part; esperar_locks
echo "L-6 concluido"
```

Os subcasos são sequenciais, então cada `armar` (que reatribui `$DMS`) vale só até o `desarmar` do mesmo subcaso.

Registrar saídas brutas em `runs/gate-netdegrade/` e o relatório em `docs/relatorio-netdegrade-gate-<data>.md`. O T-D deve passar `--direction both` **explicitamente** onde a matriz exigir simetria.

## 6. Desvios e pontos para validação (V-C1)

1. Docs `testes.md`/`configuracao-infinispan.md` editadas (aditivo) por ordem do Gestor, contra o prompt-resumo do plano.
2. F2 no mesmo nó: o plano dizia que convive (corrigido no plano após V-C1: convivem tecnicamente, o injetor exclui por desenho experimental, exit 7); o injetor recusa com exit 7 e toma o lock de F2 (pedido de exclusão mútua).
3. `--direction` padrão `out`; a matriz L exige `--direction both` explícito. `SIGINT` não testável em job assíncrono.
4. Verificação por ping dispensada com aviso para `gemodel`, `--corrupt` e sem perda no caminho.
5. As linhas de comando do roteiro (em especial `qdisc change ... gemodel`, `nstat` no netns e os contadores por sentido no L-3) foram escritas sem execução; ajustar na primeira passagem.
