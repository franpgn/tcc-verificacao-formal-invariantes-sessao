# Relatório de implementação de B-29 (`inject-partition.sh`) — 2026-10-09

> Worker, branch `feature/b29-inject-partition` (base `8e2e815` = `feature/b22-f2-particao`), worktree
> `../wt-b29`. Plano seguido: `docs/plano-b29-particao-real-e-contraexemplos-2026-10-09.md`
> (branch `docs/plano-b29-b30-2026-10-09`), seções 3.2 a 3.9. **Sem push.** O gate real na VM
> (G-0 a G-6) **não foi executado**.

## 1. O que foi feito

| Entregável | Arquivo |
|---|---|
| Injetor de partição de rede F2 | `scripts/inject-partition.sh` |
| Suíte sem VM (T-par-1 a T-par-15) com *shims* | `scripts/inject-partition.test.sh` |
| Texto de `DENY_READ_WRITES` deixa de ser "salvaguarda passiva"; seção F2 (mecanismo, flags, exit codes, Mermaid) | `docs/configuracao-infinispan.md` |
| Decisão técnica TD-008 | `docs/implementacao.md` |
| Tabela de testes B-29, resultado, gate pendente, comando de reprodução | `docs/testes.md` |

Mecanismo (a2) aprovado: `sudo nsenter -t <pid> -n tc` com `qdisc clsact` e, por IP de par do lado oposto,
um filtro `u32` em `ingress` (`match ip src`) e um em `egress` (`match ip dst`), ambos `action drop`,
**só no nó isolado**. IPs resolvidos por `podman inspect` (nunca fixos). Flags: `--minority` (aceita lista;
exige `2·|minoria| < |nós|`), `--nodes`, `--iface`, `--duration` (padrão 90; aviso abaixo de 45),
`--marker`, `--heal`, `--dry-run`, `--help`; `PODMAN` e `LOCK_DIR` por ambiente. Exit codes 0 a 6
(6 = partição não efetivada: conexão ainda possível ao par:7800, ou contadores zerados ao fim da janela).

Ordem real do script: validação, resolução de PID e IP, `trap`, `flock`, pré-limpeza, sonda
(`clsact` + filtros em 192.0.2.1 + `del`), aplicação, marcador `start`, verificação por conexão TCP,
espera, contadores, `del`, verificação de resíduo, marcador `end`.

## 2. Testes e saídas

Comandos e resultado (rodada final, esta branch):

```
# Git Bash (Windows), sem flock real
$ bash scripts/inject-partition.test.sh
...
SKIP: T-par-6 (run-baseline.sh ainda nao integra F2: escopo de B-29.1, nao de B-29)
SKIP: T-par-15 (flock real ausente neste host; roda na VM/WSL)
==== RESULTADO: 99 PASS, 0 FAIL, 2 SKIP ====

# Ubuntu (WSL), com flock real
$ wsl -e bash -c 'cd /mnt/c/.../wt-b29 && bash scripts/inject-partition.test.sh'
SKIP: T-par-6 (run-baseline.sh ainda nao integra F2: escopo de B-29.1, nao de B-29)
==== RESULTADO: 101 PASS, 0 FAIL, 1 SKIP ====
```

`bash -n scripts/inject-partition.sh` sem erros. `inject-jitter.test.sh` não foi tocado nem reexecutado
(nenhum arquivo dele mudou).

Cobertura por ID: T-par-1 (help, 9 asserções), T-par-2 (dry-run sem podman), T-par-3 e 3b (5 nós, minoria
de 2), T-par-4 (a a i, 14 casos de exit 2), T-par-5 (aviso), T-par-7 (sequência observada em ordem
verificada, só o PID de isn3 é tocado, marcadores), T-par-8 (sonda falha e `clsact` ausente, exit 5 e `del`
chamado), T-par-9 (`SIGTERM`, exit 143, `del` depois do sinal, marcador `end`), T-par-10 (`--heal` só chama
`del`, com e sem `--minority`, nó ausente, `--dry-run`), extras T-par-11 (exit 6 por conexão), T-par-12
(exit 6 por contador zerado), T-par-13 (exit 4), T-par-14 (`PODMAN="sudo podman"`), T-par-15 (`flock` real).

Observação honesta sobre a ordem pedida (testes antes do script): escrevi a suíte completa primeiro e o
script depois, mas **não executei a suíte em vermelho** antes de existir o script; a primeira execução já
foi contra o script pronto (99 PASS). Os dois arquivos estão em commits separados, `test:` antes de `feat:`.

## 3. Desvios do plano e por quê

1. **T-par-6 (`run-baseline.sh --faults "none F2" --dry-run`) fica em SKIP.** O plano lista o teste na
   suíte de B-29, mas a integração de F2 no baseline é o item B-29.1 (seção 6 do plano). O teste detecta se
   `run-baseline.sh` já menciona F2 e `inject-partition`; quando B-29.1 entrar, ele passa a executar sozinho.
2. **Marcador `start` gravado logo após aplicar os filtros**, antes da verificação por conexão TCP (o
   diagrama da seção 3.4 mostra depois). Motivo: a falha já está ativa e a verificação pode levar até 3 s
   por par; assim `fault_start_ns` não é atrasado. Se a verificação falhar (exit 6), o `end` é gravado pelo `trap`.
3. **`--minority` aceita lista separada por vírgula** (o plano dizia "um nó" para 3 nós). A regra
   `2·|minoria| < |nós|` mantém o comportamento do plano (3 nós: só 1) e cobre clusters maiores.
4. **A espera usa `sleep N &` + `wait`**, não `sleep` em primeiro plano como no `inject-jitter.sh`: em bash o
   `trap` só roda depois que o comando em primeiro plano termina, o que adiaria a limpeza por até a janela
   inteira. Sinais viram `exit 130/143/129` para disparar o `trap` de EXIT. (Possível problema análogo existe
   em `inject-jitter.sh`; não alterado, fora de escopo. T-jit-5 do gate F3 vale conferir.)
5. **`flock` e `timeout` viraram dependências** (exit 3), checadas só no modo de execução real.
6. **Contador em um só sentido gera aviso, não exit 6**; exit 6 exige os dois zerados. O critério de G-1
   (ambos > 0) fica para o Gestor conferir no gate.
7. **`runbook-vm-hyperv.md` não foi alterado**: a seção de `--heal` é item de B-29.1 (seção 3.8, item 6, e
   backlog). O `--help` e `docs/configuracao-infinispan.md` já documentam `--heal`.
8. Arquivos de lock em `${LOCK_DIR:-/tmp}/inject-partition-<no>.lock`.

## 4. O que falta no gate real da VM (G-0 a G-6)

Nenhum deles foi executado. Os *shims* não provam que o kernel aceita `clsact`/`u32`/`drop`.

| Gate | Pendência | O que observar |
|---|---|---|
| G-0 | `clsact` + `u32` + `drop` aceitos no netns de um container | senão exit 5; plano B iptables volta ao Senior |
| G-1 | injeção de 90 s em isn3, bateria parada | contadores ingress e egress > 0; conexão isn3 a `IP_isn1:7800` falha; isn1 a isn2 continua; Hot Rod a isn3 devolve erro (não *timeout*); conferir se a rede é só IPv4 |
| G-2 | fim da janela | sem `clsact` residual; exit 0 |
| G-3 | vistas | vista de 2 e de 1 membros; isn3 DEGRADED em `sessions` e `counters`; mapear nomes reais de mensagens/campos; medir `t_split` |
| G-4 | fusão | vista única de 3 e HEALTHY; registrar o tempo (`t_merge`) |
| G-5 | sinais | `kill -TERM` na janela remove a regra; `kill -KILL` + `--heal` remove |
| G-6 | coexistência | F3 em isn2 e F2 em isn3 ao mesmo tempo; cada limpeza remove só a sua |

### Comandos exatos na VM (`server1@tcc-f3`, cluster rootful já de pé, repo na branch `feature/b29-inject-partition`)

```bash
cd ~/tcc-desenvolvimento            # ajustar ao caminho do clone
git fetch && git checkout feature/b29-inject-partition   # branch local; levar via bundle/cópia, pois nao ha push
export PODMAN="sudo podman"

# pré-requisitos e testes sem VM na própria VM (com flock real: espera 101 PASS)
command -v nsenter tc flock timeout sudo
bash scripts/inject-partition.test.sh

# G-0: o kernel aceita? (nao deve deixar residuo)
PID=$($PODMAN inspect -f '{{.State.Pid}}' isn3)
sudo nsenter -t $PID -n tc qdisc add dev eth0 clsact
sudo nsenter -t $PID -n tc filter add dev eth0 ingress protocol ip prio 1 u32 match ip src 192.0.2.1/32 action drop
sudo nsenter -t $PID -n tc filter add dev eth0 egress  protocol ip prio 1 u32 match ip dst 192.0.2.1/32 action drop
sudo nsenter -t $PID -n tc qdisc del dev eth0 clsact

# conferir interface e IPs dentro do container (nao assumir eth0 cegamente)
sudo nsenter -t $PID -n ip -4 addr show
curl -s http://127.0.0.1:11222/rest/v2/cache-managers/default/health -u admin:infinispan

# plano
bash scripts/inject-partition.sh --minority isn3 --dry-run

# G-1/G-2/G-3: partição real de 90 s, com marcadores (em outro terminal: acompanhar logs/saúde)
bash scripts/inject-partition.sh --minority isn3 --duration 90 --marker runs/gate-b29/marcadores.txt
#   outro terminal, durante a janela:
#   sudo nsenter -t $PID -n tc -s filter show dev eth0 ingress ; ... egress
#   sudo nsenter -t $PID -n timeout 3 bash -c 'exec 3<>/dev/tcp/<ip-isn1>/7800' ; echo $?   # 124 esperado
#   $PODMAN logs isn1 | grep -i -E 'view|ISPN000094|merge'   # mapear mensagens
#   curl -s -u admin:infinispan http://127.0.0.1:11222/rest/v2/cache-managers/default/health
#   curl -s -u admin:infinispan http://127.0.0.1:11224/rest/v2/cache-managers/default/health
sudo nsenter -t $PID -n tc qdisc show dev eth0      # G-2: sem clsact

# G-4: tempo até vista única de 3 e HEALTHY depois do fim (cronometrar a partir de t_end no marcador)

# G-5: sinais
bash scripts/inject-partition.sh --minority isn3 --duration 120 & P=$!; sleep 60; kill -TERM $P; wait $P; echo rc=$?
sudo nsenter -t $PID -n tc qdisc show dev eth0      # sem clsact
bash scripts/inject-partition.sh --minority isn3 --duration 120 & P=$!; sleep 60; kill -KILL $P
sudo nsenter -t $PID -n tc qdisc show dev eth0      # clsact AINDA presente (esperado)
bash scripts/inject-partition.sh --heal --minority isn3
sudo nsenter -t $PID -n tc qdisc show dev eth0      # sem clsact

# G-6: coexistência com F3
bash scripts/inject-jitter.sh --node isn2 --duration 100 & 
bash scripts/inject-partition.sh --minority isn3 --duration 90
wait   # conferir que o netem de isn2 foi removido pelo seu proprio trap e o clsact de isn3 pelo seu
```

Registrar o resultado real (saídas de `tc -s filter show`, logs de vista, `t_split`, `t_merge`) em
`docs/testes.md`. Se G-0 falhar, voltar ao Senior antes do plano B.

## 5. Riscos e pontos para o Gestor

- Em G-1, a conexão de verificação do script parte do netns do nó isolado com `bash /dev/tcp` do **host**
  (não do container, que não tem esses utilitários); confirmar que o `timeout`/`bash` do host estão no PATH do `sudo`.
- A pré-limpeza remove qualquer `clsact` pré-existente no nó isolado (não só os do próprio injetor).
- `inject-jitter.sh` pode ter o mesmo atraso de `trap` descrito no desvio 4; sugestão de corrigir em item à parte.
- Decisão do Gestor: o primeiro gate pode ser com Gestor presente, como previsto no plano (seção 7, passo 3).

## 6. Próximo passo sugerido

Gate real G-0 a G-6 na VM (Gestor presente); depois B-29.1 (F2 no `run-baseline.sh`, `--partition-node`,
`--partition-duration`, seção de `--heal` no runbook, T-par-6 passando).
