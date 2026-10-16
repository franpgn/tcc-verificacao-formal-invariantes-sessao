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

## 5. Roteiro do gate L-0..L-6 na VM (nada foi executado; revisado após V-C1, R2/R3/R4)

Pré: chave SSH do Gestor instalada (T0.2), `flock /var/lock/tcc-vm.lock`, `tmux`, cluster rootful `isn1..isn3` de pé e **ocioso**, e o IP da VM reconferido (em 2026-10-15: `192.168.137.150`, usuário `server1`; muda se o host reiniciar). O código chega à VM por `git bundle`/clone (blobs LF, ver `.gitattributes`), nunca por `scp` da árvore do Windows; portão antes de tudo: `grep -rlI $'\r' scripts/ | wc -l` deve dar 0. **Há duas paradas explícitas aguardando o "go" do Gestor (antes de L-0 e antes de L-1); registrar no log o IP da VM e a data/hora de cada "go".**

```bash
ssh server1@192.168.137.150          # depois: tmux new -s l-gate
cd ~/tcc-desenvolvimento             # ajustar
git checkout feature/b29-2-inject-netdegrade
export PODMAN="sudo podman"
mkdir -p runs/gate-netdegrade
grep -rlI $'\r' scripts/ | wc -l     # esperado 0

# dead-man's switch POR INJECAO (duracao + 90 s de margem), via systemd-run (sobrevive a queda do SSH).
# uso: armar <duracao-s> ; ... injecao ... ; desarmar
armar()    { DMS="tcc-heal-$$-$RANDOM"; sudo systemd-run --quiet --unit="$DMS" --on-active="$(( $1 + 90 ))s" \
               --setenv=PODMAN="sudo podman" --working-directory="$PWD" bash scripts/inject-netdegrade.sh --heal; }
desarmar() { sudo systemctl stop "$DMS.timer" 2>/dev/null || true; }

# guarda de netns (R4): obrigatoria antes de QUALQUER tc manual. Recusa PID vazio/0 ou netns igual ao do PID 1
# (--network=host atingiria a NIC da VM e derrubaria o SSH).
guarda() { [ -n "$1" ] && [ "$1" != 0 ] && [ "$(sudo readlink /proc/$1/ns/net)" != "$(sudo readlink /proc/1/ns/net)" ]; }
# contadores da banda 3 de um no: imprime "Sent dropped"
cont() { sudo nsenter -t "$(sudo podman inspect -f '{{.State.Pid}}' $1)" -n tc -s qdisc show dev eth0 \
           | awk '/^qdisc netem 30:/{f=1;next} f&&/Sent/{gsub(",","",$7); print $4, $7; exit}'; }

# pre-requisitos e suite na propria VM (flock real: 0 SKIP; contagem exata a registrar)
command -v nsenter tc flock sudo ping systemd-run
tc -V; ls /usr/lib/tc/normal.dist; uname -r          # registro do ambiente (P-11)
for m in sch_prio sch_netem cls_u32; do sudo modprobe $m; done      # um modulo por chamada
for m in sch_prio sch_netem cls_u32; do lsmod | grep -q "^$m " && echo "ok $m" || echo "FALTA $m"; done
bash scripts/inject-netdegrade.test.sh

# ===== PARADA 1: aguardar o "go" do Gestor antes de L-0 (o add manual troca a raiz do eth0 de isn3) =====

# L-0: o kernel aceita? (sem residuo; falha = exit 5, voltar ao Senior)
PID=$($PODMAN inspect -f '{{.State.Pid}}' isn3)
guarda $PID || { echo "ABORTAR: PID invalido ou netns do host"; false; }
armar 120                                                    # L-0 deve durar < 120 s
sudo nsenter -t $PID -n ip -4 addr show                      # interface e IPv4; confirmar ausencia de IPv6 no TCP do JGroups (ss -tn no netns)
sudo nsenter -t $PID -n tc qdisc show dev eth0               # raiz padrao (noqueue/fq_codel)
sudo nsenter -t $PID -n tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
sudo nsenter -t $PID -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20% 25% delay 20ms 5ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%
sudo nsenter -t $PID -n tc qdisc change dev eth0 parent 1:3 handle 30: netem loss gemodel 5% 25% 100% 0%
sudo nsenter -t $PID -n tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3
sudo nsenter -t $PID -n tc qdisc del dev eth0 root
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio/netem
desarmar
bash scripts/inject-netdegrade.sh --target isn3 --severity N3 --direction both --dry-run

# ===== PARADA 2: aguardar novo "go" do Gestor antes de L-1 =====

# L-1: efetividade, loss 20% por 60 s (cluster ocioso). SEM --loss-corr com verificacao por ping.
armar 60
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 60 --ping-count 300 --marker runs/gate-netdegrade/marcadores.txt 2>&1 | tee runs/gate-netdegrade/L1.log
desarmar
#  durante a janela, em outro pane:
#   sudo nsenter -t $PID -n tc -s qdisc show dev eth0 ; sudo nsenter -t $PID -n tc -s filter show dev eth0
#   curl -s -o /dev/null -w '%{time_total}\n' -u admin:infinispan http://127.0.0.1:11222/rest/v2/cache-managers/default/health   # comparar com a linha de base SEM F4
#   ping -c 50 -i 0.2 <ip-isn3-host>    # sem degradacao esperada (Hot Rod/SSH poupados)
#   registrar dropped/(Sent+dropped) dos contadores, alem do ping
# esperado: exit 0, perda observada dentro de 3 sigma de 20%, dropped > 0

# L-2: limpeza
sudo nsenter -t $PID -n tc qdisc show dev eth0 ; sudo nsenter -t $PID -n tc filter show dev eth0   # sem prio/netem

# L-3: sentido, por CONTADORES (ping mede ida+volta e NAO discrimina o sentido).
# out: dropped cresce so no alvo (isn3); isn1/isn2 nao tem prio.
armar 60
bash scripts/inject-netdegrade.sh --target isn3 --direction out --loss 30 --duration 60 --ping-count 0 &
P=$!; sleep 25                                               # esperar sondagem/aplicacao antes de medir
echo "isn3: $(cont isn3)"                                    # dropped > 0, ~30% de dropped/(Sent+dropped)
wait $P; desarmar
# in: dropped cresce so nos pares (isn1, isn2), nao em isn3
armar 60
bash scripts/inject-netdegrade.sh --target isn3 --direction in --loss-in 30 --duration 60 --ping-count 0 &
P=$!; sleep 25
echo "isn1: $(cont isn1)  isn2: $(cont isn2)"
wait $P; desarmar
# both assimetrico: ~20% no alvo (saida) e ~50% nos pares (entrada), por contadores
armar 60
bash scripts/inject-netdegrade.sh --target isn3 --direction both --loss 20 --loss-in 50 --duration 60 --ping-count 0 &
P=$!; sleep 25
echo "isn3: $(cont isn3)  isn1: $(cont isn1)  isn2: $(cont isn2)"
wait $P; desarmar
# (ping fim a fim com both: perda esperada 1-(0.8*0.5)=60%, so como checagem agregada)

# L-4: N1..N5, 120 s cada, >= 2 repeticoes, cluster ocioso (N5 depende do fix R3: Sent+dropped)
for rep in 1 2; do for sev in N1 N2 N3 N4 N5; do
  armar 120
  bash scripts/inject-netdegrade.sh --target isn3 --severity $sev --direction both --duration 120 \
       --marker runs/gate-netdegrade/marcadores.txt 2>&1 | tee runs/gate-netdegrade/L4-$sev-r$rep.log
  desarmar
  #   registrar: mudancas de vista por no ($PODMAN logs isn1 | grep -i -E 'view|ISPN000094|merge'),
  #   t_split, health, sudo nsenter -t $PID -n nstat -az TcpRetransSegs, RTT p50/p99 entre nos
  #   aguardar size=3 HEALTHY (podman restart se quebrar: resultado, nao falha do gate)
done; done

# L-5: sinais (esperado: sem prio apos TERM/INT/HUP; apos KILL a prio fica e o --heal remove)
armar 120
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 120 --ping-count 0 & P=$!; sleep 30; kill -TERM $P; wait $P; echo rc=$?   # esperado 143
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio
# L-5b: SIGINT em job de fundo e Ctrl-C em primeiro plano (fecha o desvio 4 de V-C1)
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 120 --ping-count 0 & P=$!; sleep 30; kill -INT $P; wait $P; echo rc=$?    # esperado 130
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio
#   repetir em PRIMEIRO PLANO: rodar o injetor e apertar Ctrl-C apos ~30 s; rc=130, sem prio residual
# L-5c: SIGHUP
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 120 --ping-count 0 & P=$!; sleep 30; kill -HUP $P; wait $P; echo rc=$?    # esperado 129
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio
# KILL: a prio AINDA fica (esperado); esperar os sleep/ping orfaos antes de reexecutar
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 120 --ping-count 0 & P=$!; sleep 30; kill -KILL $P
sudo nsenter -t $PID -n tc qdisc show dev eth0               # prio AINDA presente (esperado)
bash scripts/inject-netdegrade.sh --heal
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio
pgrep -f 'inject-netdegrade|ping -c' || true                 # esperar orfaos terminarem
desarmar

# L-6: coexistencia. F3 convive com F4 SO com --direction out (toca apenas isn3).
armar 100
bash scripts/inject-jitter.sh --node isn2 --duration 100 &
bash scripts/inject-netdegrade.sh --target isn3 --direction out --loss 20 --duration 90      # nos distintos, out: exit 0
wait; desarmar
# com in|both o injetor toca TODOS os pares (inclui isn2, com F3): exit 7 e nada alterado
armar 100
bash scripts/inject-jitter.sh --node isn2 --duration 100 &
sleep 10; bash scripts/inject-netdegrade.sh --target isn3 --direction both --loss 20 --duration 60 ; echo rc=$?   # esperado 7
wait; desarmar
# F3 no MESMO no do alvo: exit 7
armar 100
bash scripts/inject-jitter.sh --node isn3 --duration 100 &
sleep 10; bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 60 ; echo rc=$?   # esperado 7, nada alterado
wait; desarmar
# F2 (particao, clsact) + F4 no MESMO no: exit 7 por desenho experimental (convivem tecnicamente; o injetor exclui)
#   iniciar inject-partition.sh em isn3 (ver sua --help) e, durante a janela:
#   bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 60 ; echo rc=$?   # esperado 7, nada alterado
# F2 em outro no + F4 com --direction out: coexistem (exit 0)
```

Registrar saídas brutas em `runs/gate-netdegrade/` e o relatório em `docs/relatorio-netdegrade-gate-<data>.md`. Parada obrigatória e retorno ao Senior: exit 5 em L-0 (módulo ou sintaxe), IPv6 no TCP do JGroups, ou filtro `u32` degradando Hot Rod/SSH. A credencial de laboratório do `curl` não vai para nenhum artefato publicado (P-11). O T-D deve passar `--direction both` **explicitamente** onde a matriz exigir simetria.

## 6. Desvios e pontos para validação (V-C1)

1. Docs `testes.md`/`configuracao-infinispan.md` editadas (aditivo) por ordem do Gestor, contra o prompt-resumo do plano.
2. F2 no mesmo nó: o plano dizia que convive (corrigido no plano após V-C1: convivem tecnicamente, o injetor exclui por desenho experimental, exit 7); o injetor recusa com exit 7 e toma o lock de F2 (pedido de exclusão mútua).
3. `--direction` padrão `out`; a matriz L exige `--direction both` explícito. `SIGINT` não testável em job assíncrono.
4. Verificação por ping dispensada com aviso para `gemodel`, `--corrupt` e sem perda no caminho.
5. As linhas de comando do roteiro (em especial `qdisc change ... gemodel`, `nstat` no netns e os contadores por sentido no L-3) foram escritas sem execução; ajustar na primeira passagem.
