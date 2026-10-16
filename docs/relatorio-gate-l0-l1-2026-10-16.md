# Gate T-C2, blocos L-0 e L-1 do `inject-netdegrade.sh` na VM real (2026-10-16)

Estado: **L-0 executado e aprovado; L-1 NAO executado** (ação bloqueada pelo classificador de permissões; ver seção 4). Sem push.

Ambiente: VM Hyper-V `server1`, 192.168.137.150 (alcançável; chave `~/.ssh/id_ed25519_tccf3`, usada com `-i ... -o IdentitiesOnly=yes`, pois o ssh sem `-i` deu `Permission denied`), kernel `6.8.0-124-generic`, `tc iproute2-6.1.0`, cluster rootful isn1..isn3 de pé (healthy), 0 timers `tcc-heal` e raiz `noqueue` nos três contêineres antes de começar.
Código: bundle de `feature/etapa-formal-integracao` (HEAD `4b680a2`), sha256 `aee5ced1efff2224f0afe30a6d0ff650856143fcff478c84944337866b3a80b7` idêntico no Windows e na VM; clone em `~/tcc-netdeg` (separado de `~/tcc-gate` e `~/tcc-desenvolvimento`). Bundle: `_vb3/integ-4b680a2.bundle`.
Logs brutos: `runs/gate-netdegrade/` (este worktree, branch `feature/b29-2-inject-netdegrade`; cópia na VM em `~/tcc-netdeg/runs/gate-netdegrade/`).

## 1. B0 (preparo, sem alterar a rede)

| Item | Observado |
|---|---|
| CRs em `scripts/` | 0 |
| Binários (nsenter, tc, flock, sudo, ping, systemd-run, curl, awk, tmux) | todos presentes |
| Módulos `sch_prio`, `sch_netem`, `cls_u32` | `modprobe` sem erro e presentes (`ok` nos três) |
| Suíte `scripts/inject-netdegrade.test.sh` na VM (flock real) | **306 PASS, 0 FAIL, 0 SKIP** (`B0-suite.log`, 66 s) |
| `/usr/lib/tc/normal.dist` (citado no roteiro) | **não existe** neste caminho; o arquivo está em `/usr/lib/x86_64-linux-gnu/tc/normal.dist`. O roteiro deve ser corrigido; não afeta o injetor (usa `distribution normal` só com `--jitter`, e L-0 já exercitou `distribution normal` com sucesso) |

## 2. Guarda de netns (verificação somente leitura, `guardtest.out`)

netns do PID 1 = `net:[4026531840]`; do shell do host, o mesmo; de isn3 (pid 161250), `net:[4026532291]`.

| Entrada de `guarda` | rc | esperado |
|---|---|---|
| pid 1 (host) | 1 | 1 |
| pid do shell do host | 1 | 1 |
| pid vazio | 1 | 1 |
| pid 0 | 1 | 1 |
| pid inexistente 999999 | 1 | 1 |
| pid de isn3 | 0 | 0 |
| `pid_de nao-existe-xyz` | 1 | 1 |

Não foi criado contêiner `--network=host` para testar o caso real do guarda dentro de `pid_de`; a recusa de "netns igual ao do host" foi comprovada com o PID 1 e o shell do host, que têm exatamente o netns do host. O ramo equivalente de `resolver_pid` do injetor não foi exercitado na VM (só na suíte, com shims).

## 3. L-0 (`l0.sh`, executado com `bash -x` e PS4 com timestamp; `L0-bloco.log`)

go registrado em `go.log`: `go 2026-10-16T15:50:37+00:00 vm=192.168.137.150 bloco=L-0` (origem: autorização escrita do Gestor na tarefa; o `GO` foi digitado por um pty via `ssh -tt`, ver pontos de validação).

Sequência e horários (15:50:37, hora da VM):

| Hora | Evento |
|---|---|
| .214-.257 | `pid_de isn3` com guarda: PID 161250, netns diferente do host |
| .259 | `systemd-run --unit=tcc-heal-413584-2848 --on-active=210s ... --heal` (dead-man armado) |
| .283 | primeiro `tc qdisc add ... root handle 1: prio` (24 ms **depois** de armar) |
| .289 | `netem loss 20% 25% delay 20ms 5ms distribution normal duplicate 1% reorder 25% 50% corrupt 1%` aceito |
| .297 | `qdisc change ... netem loss gemodel 5% 25% 100% 0%` aceito |
| .303 | filtro `u32 match ip dst 192.0.2.1/32 flowid 1:3` aceito |
| .310 | `qdisc del dev eth0 root` |
| .368 | `sem_prio isn3`: `qdisc noqueue 0: root refcnt 2` (sem prio/netem) |
| .372 | `systemctl stop` do timer (dead-man desarmado) |
| .380-.396 | `--dry-run N3 both` imprimiu o plano e saiu 0 |

Resultado: rc do bloco = 0 (`L0-run.log`). O journal (`deadman-journal-L0.log`) confirma `Started tcc-heal-413584-2848.timer` às 15:50:37 e `Stopped` logo após. Nenhum comando `tc` falhou: **o kernel aceitou todos os parâmetros** (prio, netem com loss/delay/distribution normal/duplicate/reorder/corrupt, gemodel e u32).

Observado nos amostradores (`mon-L0-*.log`, ciclos de ~2 s; 6 ciclos): health `HEALTHY size=3` nas 3 portas em todos; ping host->isn3 0% de perda, rtt médio 0,033 a 0,058 ms; `time_total` do REST 2,2 a 4,1 ms. **Limite**: a janela de L-0 durou ~0,2 s de rede alterada, então nenhum amostrador caiu dentro dela (0 ocorrências de `prio`/`netem` nos três logs de monitor); a ausência de efeito no cluster não é demonstrada por L-0, só a aceitação de sintaxe e a limpeza.
Depois: isn1, isn2, isn3 com `qdisc noqueue 0: root refcnt 2`, 0 timers `tcc-heal`, cluster size=3 HEALTHY.

## 4. L-1: NAO executado

Eu pretendia, antes do L-1 de 20% do roteiro, rodar uma injeção preliminar de severidade baixa (N1 = 1%, 45 s, `--ping-count 0`) via um `l1pre.sh` novo, para seguir a regra "severidades baixas primeiro". O comando (envio do `l1pre.sh` por scp + execução remota) foi **negado pelo classificador de permissões** (motivo informado: "Remote Shell Writes"). Não contornei: o scp e a execução não rodaram (a VM não tem `l1pre.sh` nem logs L1; confirmado). Verificado depois: tc limpo em isn1, isn2, isn3, 0 timers.

Qualquer outra tentativa de L-1 (inclusive o `l1.sh` do roteiro, que usa o mesmo caminho de escrita remota) provavelmente cairia na mesma regra; por isso parei aqui.

## 5. Observado x inferido

- Observado: tudo o que está nas seções 1 a 3, com os logs citados.
- Inferido (não medido): que o injetor completo (com sonda, `nsenter` de pares, ping de verificação) funcionará da mesma forma; que o u32 casa o 7800; que a perda aparecerá em `tc -s qdisc`. Isso é exatamente o que L-1 mede e **ainda não foi medido**.

## 6. Pontos para o Gestor

1. Autorizar (regra de permissão para escrita remota na VM, ou executar você mesmo) a continuação: L-1 do roteiro, com a opção de antecedê-lo por N1 (1%) 45 s. O comando será `runblock.sh l1 5` com o `GO` digitado.
2. O `exigir_go` exige terminal; usei `ssh -tt` com `GO` por stdin, logado em `go.log` com a autorização textual do Gestor. Se preferir que o `GO` seja digitado por você, diga.
3. Corrigir o roteiro: `normal.dist` mora em `/usr/lib/x86_64-linux-gnu/tc/`; ssh exige `-i` explícito para a chave `id_ed25519_tccf3` desta máquina.
4. Artefatos auxiliares meus (`mon.sh`, `runblock.sh`, `guardtest.sh`) estão em `runs/gate-netdegrade/`; não alteram a rede (`mon.sh` só lê).
