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
- Final de linha: o repositório usa `autocrlf=true`; para rodar na VM a partir de um clone/bundle, garantir LF (`dos2unix` ou `git config core.autocrlf input`).

## 5. Roteiro do gate L-0..L-6 na VM (nada foi executado)

Pré: chave SSH do Gestor instalada (T0.2), "go" do Gestor, `flock /var/lock/tcc-vm.lock`, `tmux`, cluster rootful `isn1..isn3` de pé e **ocioso**, dead-man's switch armado, e o IP da VM reconferido (em 2026-10-15: `192.168.137.150`, usuário `server1`; muda se o host reiniciar).

```bash
ssh server1@192.168.137.150          # depois: tmux new -s l-gate
cd ~/tcc-desenvolvimento             # ajustar; levar a branch por bundle (sem push)
git checkout feature/b29-2-inject-netdegrade
export PODMAN="sudo podman"
mkdir -p runs/gate-netdegrade
# dead-man's switch: em OUTRO pane, antes de cada injecao
#   ( sleep 300; bash scripts/inject-netdegrade.sh --heal ) &

# pre-requisitos e suite na propria VM (flock real: espera 293 PASS / 0 SKIP)
command -v nsenter tc flock sudo ping
lsmod | grep -E 'sch_prio|sch_netem|cls_u32' || sudo modprobe sch_prio sch_netem cls_u32
bash scripts/inject-netdegrade.test.sh

# L-0: o kernel aceita? (sem residuo; falha = exit 5, voltar ao Senior)
PID=$($PODMAN inspect -f '{{.State.Pid}}' isn3)
sudo nsenter -t $PID -n ip -4 addr show                      # interface e IPv4; confirmar ausencia de IPv6 no TCP do JGroups
sudo nsenter -t $PID -n tc qdisc show dev eth0               # raiz padrao (noqueue/fq_codel)
sudo nsenter -t $PID -n tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
sudo nsenter -t $PID -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20% 25% delay 20ms 5ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%
sudo nsenter -t $PID -n tc qdisc change dev eth0 parent 1:3 handle 30: netem loss gemodel 5% 25% 100% 0%
sudo nsenter -t $PID -n tc filter add dev eth0 parent 1: protocol ip prio 1 u32 match ip dst 192.0.2.1/32 flowid 1:3
sudo nsenter -t $PID -n tc qdisc del dev eth0 root
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio/netem
bash scripts/inject-netdegrade.sh --target isn3 --severity N3 --direction both --dry-run

# L-1: efetividade, loss 20% por 60 s (cluster ocioso)
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 60 --ping-count 300 --marker runs/gate-netdegrade/marcadores.txt 2>&1 | tee runs/gate-netdegrade/L1.log
#  durante a janela, em outro pane:
#   sudo nsenter -t $PID -n tc -s qdisc show dev eth0 ; sudo nsenter -t $PID -n tc -s filter show dev eth0
#   curl -s -u admin:infinispan http://127.0.0.1:11222/rest/v2/cache-managers/default/health
#   ping -c 50 -i 0.2 <ip-isn3-host>    # sem degradacao esperada (Hot Rod/SSH poupados)
# esperado: exit 0, perda observada dentro de 3 sigma de 20%, dropped > 0

# L-2: limpeza
sudo nsenter -t $PID -n tc qdisc show dev eth0 ; sudo nsenter -t $PID -n tc filter show dev eth0   # sem prio/netem

# L-3: direcoes (medir de dentro dos dois netns)
bash scripts/inject-netdegrade.sh --target isn3 --direction out --loss 30 --duration 60 &
#   ping de isn3 -> isn1 (perda), de isn1 -> isn3 (so a metade de ida/volta); comparar por sentido
wait
bash scripts/inject-netdegrade.sh --target isn3 --direction both --loss 20 --loss-in 50 --duration 60
#   taxas distintas por sentido (RTT-perda esperada 1-(0.8*0.5) = 60%)

# L-4: N1..N5, 120 s cada, >= 2 repeticoes, cluster ocioso
for rep in 1 2; do for sev in N1 N2 N3 N4 N5; do
  bash scripts/inject-netdegrade.sh --target isn3 --severity $sev --direction both --duration 120 \
       --marker runs/gate-netdegrade/marcadores.txt 2>&1 | tee runs/gate-netdegrade/L4-$sev-r$rep.log
  #   registrar: mudancas de vista por no ($PODMAN logs isn1 | grep -i -E 'view|ISPN000094|merge'),
  #   t_split, health, sudo nsenter -t $PID -n nstat -az TcpRetransSegs, RTT p50/p99 entre nos
  #   aguardar size=3 HEALTHY (podman restart se quebrar: resultado, nao falha do gate)
done; done

# L-5: sinais
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 120 & P=$!; sleep 60; kill -TERM $P; wait $P; echo rc=$?   # esperado 143
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 120 & P=$!; sleep 60; kill -KILL $P
sudo nsenter -t $PID -n tc qdisc show dev eth0               # prio AINDA presente (esperado)
bash scripts/inject-netdegrade.sh --heal
sudo nsenter -t $PID -n tc qdisc show dev eth0               # sem prio

# L-6: coexistencia
bash scripts/inject-jitter.sh --node isn2 --duration 100 &
bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 90      # nos distintos: exit 0
wait
bash scripts/inject-jitter.sh --node isn3 --duration 100 &
sleep 10; bash scripts/inject-netdegrade.sh --target isn3 --loss 20 --duration 60 ; echo rc=$?   # esperado 7, nada alterado
wait
# F4 isn3 + partição em outro par: so se o plano de T-C2 exigir (F2 no mesmo no da exit 7 por desenho)
```

Registrar saídas brutas em `runs/gate-netdegrade/` e o relatório em `docs/relatorio-netdegrade-gate-<data>.md`. Parada obrigatória e retorno ao Senior: exit 5 em L-0 (módulo ou sintaxe), IPv6 no TCP do JGroups, ou filtro `u32` degradando Hot Rod/SSH.

## 6. Desvios e pontos para validação (V-C1)

1. Docs `testes.md`/`configuracao-infinispan.md` editadas (aditivo) por ordem do Gestor, contra o prompt-resumo do plano.
2. F2 no mesmo nó: o plano diz que convive; o injetor recusa com exit 7 e toma o lock de F2 (pedido de exclusão mútua).
3. `--direction` padrão `out`; a matriz L exige `--direction both` explícito. `SIGINT` não testável em job assíncrono.
4. Verificação por ping dispensada com aviso para `gemodel`, `--corrupt` e sem perda no caminho.
5. As linhas de comando do roteiro (em especial `qdisc change ... gemodel`, `nstat` no netns e os pings por sentido no L-3) foram escritas sem execução; ajustar na primeira passagem.
