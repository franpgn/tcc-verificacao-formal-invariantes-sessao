# V-C1b: revalidação das correções R1 a R4 da V-C1 (T-C1, B-29.2) — 2026-10-16

Validador: Senior. Base: `docs/validacao-tc1-2026-10-16.md` (branch `docs/validacao-tc1-2026-10-16`, `6b04333`). Entrega revalidada: `feature/b29-2-inject-netdegrade` (`e6d2967`, `3220e5d`, `5d43db0`, `b062aab`, `21bfbf1`, `7d2ae7f`), `chore/gitattributes-eol-lf` (`6dc7cbe`) e plano `7bb8fb9`. Tudo reexecutado em worktrees temporários (removidos ao fim). Nenhum script/spec alterado; nenhum push.

## Veredito: APROVADA COM RESSALVAS

R1, R3 e R4 estão corretas e verificadas por mutação. R2 (roteiro) melhorou muito, mas ainda tem 4 defeitos de execução (seção 4) que o Worker-3 deve corrigir no documento antes de T-C2; nenhum afeta T-B. Integração local aprovada (seção 7).

## 1. Suítes (checkout limpo do merge `chore/gitattributes-eol-lf` em `7d2ae7f`, commit de merge de teste `d66634e`, `autocrlf=true` do Windows)

| Suíte | Git Bash | WSL Ubuntu (`/mnt/c`) | Worker |
|---|---|---|---|
| inject-netdegrade | 298 PASS / 0 FAIL / 1 SKIP (T-nd-21, sem flock real) | 306 / 0 / 0 | 298/0/1 e 306/0/0 (confere) |
| inject-jitter | 35 / 0 | 35 / 0 | 35/0 (confere) |
| inject-partition | 99 / 0 / 2 SKIP | 101 / 0 / 1 SKIP | 99/0/2 e 101/0/1 (confere) |

LF: `git ls-files --eol scripts spec cluster` dá `i/lf w/lf attr/text eol=lf` em todos os 18 arquivos de texto (só `scripts/.gitkeep` é `none`); `grep -rlI $'\r' scripts spec cluster | wc -l` = 0. Arquivos fora do escopo do `.gitattributes` (`*.md`, `*.java`, `*.json`, `*.py`, `*.out`, `.gitignore`) continuam `w/crlf` no Windows, o que é inofensivo (não rodam na VM; ver 5). Observação: um worktree criado antes do merge permanece com CRLF; só um checkout posterior ao `.gitattributes` obedece.

## 2. R3 (falso exit 6 com N5)

- `e6d2967` (teste vermelho, script antigo): 285 PASS / **2 FAIL** / 1 SKIP; as falhas são T-nd-15l (N5, Sent 0, dropped 500) e T-nd-15m (perda 20%, Sent 0, dropped 500), ambas `esperado=0 obtido=6`.
- `3220e5d` (fix): 287 / 0 / 1. Fase vermelha genuína, confirmada.
- Mutação (HEAD com `git revert 3220e5d` no script): 296 PASS / **2 FAIL** (T-nd-15l e 15m) / 1 SKIP. A suíte detecta o defeito.
- Lógica: `SENT + DROP == 0` é o critério certo; T-nd-15g (`Sent=0, dropped=0` -> exit 6) e 15k (só atraso, Sent 0) seguem como o outro lado. Sem furo.

## 3. R4 (guarda de netns). O Worker disse não ter feito fase vermelha; fiz a mutação

Mutantes sobre `resolver_pid` (suíte inteira, Git Bash):

| Mutante | Resultado | Testes que matam |
|---|---|---|
| M1: `ns_alvo == ns_host` -> `false` | 292 PASS / **6 FAIL** | T-nd-15o (3), 15p (2), 15r |
| M2: checagem de leitura vazia -> `false` | 296 / **2 FAIL** | T-nd-15q (2) |
| M3: bloco inteiro removido | 288 / **10 FAIL** | T-nd-15n (2), 15o, 15p, 15q, 15r |

(Uma primeira tentativa com `sed` por número de linha quebrou a sintaxe e deu falhas espúrias; descartada, os três mutantes acima passam `bash -n`.) Os testes T-nd-15n..r são, portanto, não tautológicos. A falta de fase vermelha é uma falha de processo (o plano exige teste vermelho antes), compensada aqui pela mutação; registrar, não bloqueia.

Revisão da lógica (`scripts/inject-netdegrade.sh:533-555`):

- Cobre alvo e pares (usa `resolver_pid` para todos de `ENVOLVIDOS`) e `--heal` (que também chama `resolver_pid`). Falha fecha: leitura ilegível de qualquer um dos dois namespaces recusa. Saída 4 (nó), documentada. A guarda roda antes de qualquer `tc`; T-nd-15o/p/q verificam que nenhum `tc` é executado.
- Sem furos de segurança. Notas menores (não bloqueiam): (a) com a guarda negada no fluxo principal, aparece também a mensagem genérica "nao encontrado, nao esta running ou sem PID", enganosa mas precedida do diagnóstico específico; (b) em `--heal` um nó com netns do host cai no ramo "container ausente/parado ... nada a fazer", também enganoso, porém seguro; (c) TOCTOU entre a checagem e o `nsenter` (PID reciclado) é desprezível; (d) não cobre pods (`--network=container:`/pod com netns compartilhado entre contêineres do cluster); não é o caso dos `isn1..3`.

## 4. R2 (roteiro L-0..L-6, `docs/relatorio-b29-2-implementacao-2026-10-16.md` linhas 49-186), linha a linha

Corrigidos e corretos: `modprobe` em laço e `lsmod` por módulo (77-78); `armar`/`desarmar` por injeção com `systemd-run --on-active=duração+90s`, `--setenv=PODMAN`, `--working-directory`, parada por `$DMS.timer` (63-65); `guarda` (69); `cont` (71-72: o `awk` lê `$4` e `$7` da linha `Sent ... (dropped N,`, depois de `qdisc netem 30:`; confere com o formato do `tc -s`); L-3 por contadores (114-133) e `--loss-in` com `--direction in|both` (aceito pelo script, linha 304); L-4 com N5 após R3; L-5b/L-5c (SIGINT 130, SIGHUP 129); L-6 só com `--direction out` para F3 em outro nó, exit 7 para `in|both`; `grep -rlI $'\r'` e bundle (51, 59). `kill -INT` em job de fundo só funciona em shell interativo com job control (tmux interativo; não colar em `bash -c`/script).

Defeitos que ainda precisam de correção (documento, não script), ordenados por gravidade:

1. **A guarda de L-0 não aborta nada** (85): `guarda $PID || { echo "ABORTAR..."; false; }` em colagem interativa só retorna 1 e o `armar` e os `tc add` seguintes **continuam**; exatamente o cenário que a guarda existe para evitar (PID vazio ou netns do host). Usar `guarda $PID || { echo ABORTAR; return 1 2>/dev/null || exit 1; }` dentro de um script/função, ou rodar L-0 como arquivo `set -euo pipefail`. Reforço: `guarda` aprova se `readlink /proc/$1/ns/net` falhar (vazio != netns do host); acrescentar `[ -n "$(sudo readlink /proc/$1/ns/net)" ]` (o script faz isso; o roteiro não).
2. **As paradas pelo "go" são comentários num bloco contínuo** (81, 98): quem cola o bloco inteiro as atravessa. Separar o roteiro em três blocos (preparo; L-0; L-1 a L-6), cada um iniciado só após o "go" registrado, ou trocar o comentário por `read -rp "GO do Gestor registrado? [digite GO] " r; [ "$r" = GO ] || return 1`.
3. **L-4 não espera o cluster reformar** (141-143): a recuperação para `size=3 HEALTHY` é um comentário; o laço `for rep in 1 2; do for sev ...` passa à próxima severidade com o cluster possivelmente partido por N4/N5, quebrando "cluster ocioso/size=3" e contaminando as repetições. Inserir uma função `espera_saudavel` (laço com `curl` de health dos três nós até `HEALTHY` e `number_of_nodes=3`, com limite e `podman restart` registrado como resultado). Também: `| tee` esconde o exit code do injetor (L-1 102, L-4 139) e o critério é "exit 0"; usar `${PIPESTATUS[0]}` ou `set -o pipefail` e gravar `rc=` no log; idem `wait $P` em L-3 (sem `echo rc=$?`).
4. **Dead-man's switch único em L-5** (147/163): `armar 120` dispara em 210 s e o L-5 completo (TERM, INT, HUP, KILL, mais `--heal` e esperas) dura cerca de 150 a 200 s; um disparo no meio de uma injeção remove a `prio` e produz exit 6 ou resultado enganoso. Armar por subteste (`armar 120` antes de cada) ou `armar 300`, e `desarmar` entre eles. O mesmo vale para L-6 (cada subcaso com seu `armar`; hoje `armar 100` cobre 190 s, folga curta).

Menores: (5) F2 em L-6 é só comentário (180-183): escrever o comando (`inject-partition.sh --minority isn3 --duration 90` em `&`, depois `inject-netdegrade.sh --target isn3 ...` esperando exit 7; e o caso F2 em isn2 + F4 `out` em isn3, exit 0) e o `--heal` do F2 como dead-man's próprio (`armar` só desfaz `prio`, não `clsact`); (6) o laço órfão de L-5 usa `pgrep -f 'inject-netdegrade|ping -c'`, mas o `sleep` filho também segura o lock herdado (fd do `flock`); incluir `sleep` ou esperar a liberação do lock antes da reexecução; (7) a pré-condição cita `flock /var/lock/tcc-vm.lock`, mas nenhum comando do roteiro o usa; envolver a sessão tmux (`flock /var/lock/tcc-vm.lock tmux new -s l-gate`) ou retirar a menção; (8) `command -v a b c` não sinaliza qual falta (usar laço com `|| echo FALTA $c`) e módulos embutidos no kernel não aparecem em `lsmod`; (9) o `curl` com `admin:infinispan` é credencial de laboratório, mantida fora de artefatos publicados (P-11).

## 5. R1 (`.gitattributes`, `chore/gitattributes-eol-lf`, `6dc7cbe`)

Um arquivo, 14 linhas. Cobre `*.sh`, `*.bash`, `*.xml` (`cluster/`, `pom.xml`), `*.yml`/`*.yaml` (podman-compose), `*.cfg`/`*.tla` (TLC), `*.properties`, `Containerfile`, `Dockerfile` e o próprio `.gitattributes`. Suficiente para tudo o que roda na VM neste momento. Sugestões opcionais (não bloqueiam): `*.py text eol=lf` (os `analysis/*.py` podem ganhar shebang; um shebang com `\r` quebra a execução direta), `*.csv`/`*.json` se algum dia forem processados por `awk`/`jq` na VM, e `*.conf`/`*.service` se surgirem. Partir de `main` é correto: a mudança é independente de B-29/B-32, o índice já está em LF (149 arquivos `i/lf`, nenhum precisa de renormalização; o merge de teste não gerou diff de fim de linha) e o `.gitattributes` não conflita com nada. Fica a primeira da fila de integração. O merge de teste também trouxe 88/63 linhas de `docs/planejamento-inicial.md`, que vêm da divergência de `main` (PR #20), não de R1.

## 6. Itens diversos

- `inject-partition.sh`, `inject-partition.test.sh`: **apenas** mudança de modo (`100644 -> 100755`, 0 linhas) em `21bfbf1`; conteúdo intacto. `spec/` e `scripts/inject-jitter.sh`, `inject-crash.sh`, `run-baseline.sh`: sem alteração contra `b56c6ea`. A mudança de modo toca um arquivo de B-29, mas é benigna e sem conflito (B-29 integra antes).
- `spec/run-tlc-invariantes.sh` está `100644` (os demais `.sh` do repositório estão `100755`). Para T-B/T-C2 não importa (os roteiros chamam `bash scripts/...` e o TLC não roda na VM). Corrigir em tarefa à parte, na branch dono do arquivo (`feature/b32-tlc-propriedades-fortes`/`test/b32-auditoria-controle`), com `git update-index --chmod=+x`; não nesta branch, para não misturar responsabilidades.
- Dois docs (`testes.md`, `configuracao-infinispan.md`) e o desvio R5 (ordem do Gestor) seguem pendentes de confirmação do Gestor.

## 7. Checklist do orquestrador antes de T-B (gate G-0..G-6) e T-C2 (gate L-0..L-6)

Ordem das integrações locais (sem push; cada merge com `--no-ff`, após "go" do Gestor para a integração):

1. `chore/gitattributes-eol-lf` -> base de integração (deve entrar primeiro, antes de qualquer checkout que vá à VM).
2. `feature/b29-inject-partition` (`b56c6ea`, B-29) já é ancestral de `feature/b29-2-inject-netdegrade`; T-B usa `b56c6ea` e T-C2 usa a ponta `7d2ae7f` (+ correções do roteiro).
3. Corrigir o roteiro (itens 1 a 5 da seção 4) em `wt-tc` (Worker-3), commit `docs:`. Revalidação leve por mim (leitura), sem nova suíte.
4. Só depois de V-B e V-C2: `feature/etapa-formal-integracao` (plano 5.3).

Como levar o código à VM sem push (a árvore do Windows nunca é copiada):

```bash
# no host (Git Bash), na ponta já integrada com o .gitattributes
git bundle create ../tcc-t-c2.bundle --all           # ou: <branch> ^main
git bundle verify ../tcc-t-c2.bundle
scp ../tcc-t-c2.bundle server1@192.168.137.150:~/    # chave SSH (T0.2)
# na VM
git clone ~/tcc-t-c2.bundle ~/tcc-desenvolvimento || (cd ~/tcc-desenvolvimento && git fetch ~/tcc-t-c2.bundle '+refs/heads/*:refs/remotes/bundle/*')
git -C ~/tcc-desenvolvimento checkout feature/b29-2-inject-netdegrade
git -C ~/tcc-desenvolvimento rev-parse HEAD           # deve bater com o SHA do host
grep -rlI $'\r' ~/tcc-desenvolvimento/scripts ~/tcc-desenvolvimento/cluster ~/tcc-desenvolvimento/spec | wc -l   # 0
```

O clone na VM (Linux, sem `autocrlf`) entrega os blobs como estão no índice (LF). T-B só precisa de `b56c6ea` (bundle de `feature/b29-inject-partition`); T-C2, da ponta de `feature/b29-2-inject-netdegrade`. Conferir o SHA nos dois lados.

Pré-requisitos na VM (Ubuntu 24.04, podman rootful, cluster `isn1..isn3` ocioso, `number_of_nodes=3`, HEALTHY):

- Pacotes/binários: `iproute2` (`tc`, `ip`), `util-linux` (`nsenter`, `flock`), `iputils-ping`, `curl`, `tmux`, `procps`, `sudo -n true`, `systemd-run`; `tc -V` e `ls /usr/lib/tc/normal.dist` registrados (P-11).
- Módulos: T-B (F2): `cls_u32`, `sch_ingress` (clsact), `act_gact`; T-C2 (F4): `sch_prio`, `sch_netem`, `cls_u32`. Carregar um por chamada e conferir com `lsmod`; falha de módulo = exit 5 e volta ao Senior (plano B iptables em T-B).
- Pré-condição de segurança: `guarda` (netns != PID 1) antes de qualquer `tc` manual e dead-man's switch armado por injeção. O roteiro de B-29 (`docs/relatorio-b29-implementacao-2026-10-09.md`) **não** tem nem `armar` nem `guarda` nem paradas; T-B deve usar o padrão corrigido do roteiro L (o prompt do T-B já exige dead-man's switch). `inject-partition.sh --heal --minority isn3` é o desfazer de F2.
- Suíte na VM com `flock` real: esperado `inject-partition` 101 PASS / 0 FAIL / 1 SKIP e `inject-netdegrade` 306 / 0 / 0 (o SKIP residual de partition é dependente do host; registrar o número real). Se divergir, parar.
- Sessão `tmux` e `flock /var/lock/tcc-vm.lock` para exclusividade da VM; T-B antes de T-C2 (a VM é um recurso único).

"Go" do Gestor exigidos:

| Gate | Parada | Quem |
|---|---|---|
| T0.2 | chave SSH instalada e `sudo -n true` (uma vez) | Gestor |
| T-B | depois de G-0 e do dry-run, antes de G-1 (primeira injeção real; Gestor disponível durante os 90 s) | Gestor |
| T-B | antes do ensaio `kill -KILL` de G-5 (deixa o nó cortado até o `--heal`) | Gestor |
| T-B / T-C2 | qualquer exit 5 ou 6, cluster que não reforma size=3, filtro `u32` degradando Hot Rod/SSH | parar e chamar Gestor/Senior |
| T-C2 | antes de L-0 (o `add` manual troca a raiz do `eth0` de isn3) | Gestor |
| T-C2 | antes de L-1 (primeira injeção real do injetor) | Gestor |
| T-C2 | Gestor disponível nas janelas de L-1 e do `kill -KILL` de L-5 (Q-12); IP da VM e data/hora de cada "go" registrados no log | Gestor |

Liberação formal: V-B aprovada (Senior) antes de T-C2 (a VM e o método SSH provados). R5 (ordem do Gestor sobre os dois docs) pode ser confirmada em paralelo.

## 8. Resumo das ressalvas restantes

| # | Ressalva | Quem | Bloqueia |
|---|---|---|---|
| a | Roteiro: guarda que não aborta, paradas não impostas, espera de saúde em L-4, `armar` em L-5/L-6 e `rc` perdido por `tee` (seção 4, itens 1 a 4) | Worker-3 | T-C2 |
| b | Roteiro: F2 em L-6 sem comando; `sleep` órfão; `flock` do tmux (itens 5 a 8) | Worker-3 | não |
| c | `chmod +x` de `spec/run-tlc-invariantes.sh` | Worker na branch de B-32 | não |
| d | Gestor: confirmar a ordem sobre os dois docs e as integrações locais | Gestor | integração |
