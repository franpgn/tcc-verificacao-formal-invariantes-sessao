# V-C1: validação da entrega T-C1 (B-29.2, `inject-netdegrade.sh`, falha F4) — 2026-10-16

Validador: Senior. Entrega: `feature/b29-2-inject-netdegrade` (`196e0f0`, `ca4cc52`, `1bbf237`, `7c33965`), worktree `wt-tc`. Plano: `docs/plano-fechamento-etapa-formal-2026-10-15.md` (T-C1, Q-7, Q-8, §4.1). Não confiei no relatório; tudo abaixo foi reexecutado. Nenhum script/spec foi alterado.

## Veredito: APROVADA COM RESSALVAS

Interface aprovada e congelada para T-D (ver §8). **T-C2 (gate L na VM) pode ser liberada quando houver SSH**, condicionada às ressalvas R1 (obrigatória antes de levar o código à VM), R2 (roteiro corrigido) e R3 (correção do `Sent==0` antes de L-4/N5). Nenhuma delas invalida o que foi entregue.

## 1. Suíte, nos dois ambientes (números reais)

Worktree temporário limpo em `7c33965` (`git status` vazio; removido ao fim).

| Verificação | Resultado |
|---|---|
| `bash -n scripts/inject-netdegrade.sh` | limpo |
| Git Bash (checkout limpo, CRLF) | **285 PASS, 0 FAIL, 1 SKIP** (T-nd-21: `flock` real ausente no host) — idêntico ao relatório |
| WSL Ubuntu, checkout limpo do Windows (CRLF) | **não roda**: `syntax error near unexpected token $'{\r'` (linha 79 do script, 31/35/65 da suíte) |
| WSL Ubuntu, árvore exportada com LF (`git -c core.autocrlf=false archive`) | `bash -n` ok; **293 PASS, 0 FAIL, 0 SKIP** — idêntico ao relatório |

Conclusão: os números do relatório estão corretos, mas só valem com LF. O relatório diz "garantir LF" sem exigir mecanismo; ver R1.

## 2. A suíte não é tautológica

- Sem o script (em `196e0f0`, e em `7c33965` com `git rm` do script): **0 PASS, 1 FAIL** (`T-nd-0 ... nao existe`). Vermelho verificado (fraco por ser um FAIL só; o relatório cita 93 PASS/191 FAIL contra um stub `exit 0`, que **não** reproduzi).
- Mutações no script (cópia isolada, suíte completa, Git Bash). Repeti 3 das 5:

| Mutante | Resultado | Relatório |
|---|---|---|
| sem nenhum `trap` | 273 PASS / **12 FAIL** (T-nd-12a/c/e/f, 13a: resíduo de `prio`, falta `qdisc del` após o sinal) | "falhas em 12/13" confere |
| checagem de raiz alheia (`alheia`) desativada | 279 PASS / **6 FAIL** | "6 FAIL" confere |
| marcador `fault=F4` -> `fault=F3` | 282 PASS / **3 FAIL** | relatório diz 4 FAIL; obtive 3 (diferença não relevante: mutante morto de qualquer forma) |

## 3. Script contra os critérios de aceite do plano

| Critério | Veredito |
|---|---|
| (1) suíte verde e vermelha antes | atendido (§1 e §2) |
| (2) `bash -n` limpo | atendido |
| (3) `--dry-run` imprime tudo com `<pid>`/`<ip-par>` | atendido (lido o bloco de dry-run; usado abaixo em §4) |
| (4) nenhum `tc` sem `nsenter` | atendido: a única chamada de `tc` é `nstc()` = `sudo nsenter -t <pid> -n tc`; `grep` confirma; 28 ocorrências de `nsenter` |
| (5) exit codes documentados | atendido: 0..7 no cabeçalho, `--help` e `degradacao-de-rede.md`; 2 (arg), 3 (dep), 4 (nó), 5 (kernel/sonda), 6 (efetividade), 7 (raiz ocupada) coerentes com o código |
| (6) `degradacao-de-rede.md` com Mermaid | atendido (2 blocos `mermaid`) |
| (7) nenhum arquivo fora da lista tocado | **não literal**: além de script, suíte e `degradacao-de-rede.md`, o diff contra `b56c6ea` traz `docs/testes.md` (+37), `docs/configuracao-infinispan.md` (+18) e dois relatórios. `run-baseline.sh` intacto. Ver §6 (desvio 1) |

Pontos do mecanismo (Q-7) e das condições:

- **prio+netem+u32 por par**: `prio bands 3 priomap 0 x16` na raiz, `netem` em `parent 1:3 handle 30:`, `u32 match ip dst <ip>/32 flowid 1:3` por par, `protocol ip prio 1`. É exatamente o exemplo da documentação oficial do netem (prio + netem na banda 3 + filtro u32 por IP). Hot Rod (origem no gateway) e SSH caem na banda 1 por `priomap` zerado.
- **Perda `random`/`gemodel`**: `loss X% [corr%]`; `gemodel`: `loss gemodel p% r% 100% 0%` com `r = 100/burst` e `p = r·X/(100−X)`. Conferi a álgebra: P(estado ruim) = p/(p+r) = X/100 e perda de 100% no ruim e 0% no bom dão perda média X e rajada média `burst`. Recusa (exit 2) quando `p > 100`.
- **Delay/jitter**: `delay D J distribution normal` (jitter exige delay, validado). **Duplicate/corrupt**: `duplicate d%`, `corrupt c%`. **Reorder**: `reorder r% [c%]`, recusado sem `--delay` (a mesma regra do `tc`; ver §4).
- **Assimetria**: `--direction in` aplica a regra no egress de cada par com destino ao alvo (um `nsenter` por par); `--loss` (saída) e `--loss-in` (entrada) distintos geram dois `netem` diferentes. Sem `ifb`, como no plano. A perda esperada no ping, `1−(1−po)(1−pi)`, está correta.
- **Trap idempotente ANTES de alterar**: `trap cleanup EXIT` e sinais `INT/TERM/HUP -> exit 130/143/129` são registrados antes do lock, da pré-limpeza, da sonda e de qualquer `add`; `CLEANED` evita dupla execução; remove só `prio 1:` (`eh_nosso`), só em nós sob lock (`BLOQUEADOS`); marcador `end` garantido se o `start` foi escrito.
- **Exclusão com F3 e F2**: raiz que não seja `noqueue|pfifo_fast|fq_codel|mq|fq|pfifo` -> exit 7 sem alterar nada; `clsact` -> exit 7; lock de F2 (`inject-partition-<no>.lock`, o mesmo nome que `inject-partition.sh` usa na linha 373) ocupado -> exit 7. F3 e F4 em nós diferentes: ver R2 (L-6).
- **Só IPv4**: `protocol ip`, resolução `IPV4_RE` e exit 4 sem IPv4; falta a confirmação no gate (L-0), que o roteiro prevê.
- **Marcador**: `fault=F4 node=<alvo> event=start|end t_epoch_ns=<n>`, mesmo formato do `fault=F2` do `inject-partition.sh`.
- **Guarda de netns**: **ausente**. O script confia em `podman inspect .State.Pid`; não compara `/proc/<pid>/ns/net` com `/proc/1/ns/net`. Com cluster em rede bridge o risco é baixo, mas um contêiner `--network=host` receberia `prio` na NIC da VM. Nenhum injetor anterior (B-29/F3) tem a guarda. Ver R4.

## 4. Sintaxe do `tc` sem a VM

O kernel do WSL2 não tem `sch_prio`/`sch_netem`/`cls_u32`, mas o `tc` (iproute2 5.15 no WSL Ubuntu) faz o *parse* dos parâmetros em espaço de usuário **antes** de falar com o kernel. Em `unshare -n` como root, rodei os comandos como o script os emite (extraídos de `--dry-run` para 7 combinações: N1, N5, perda+correlação+delay+jitter+duplicate+reorder+corrupt, gemodel, gemodel+delay, delay em segundos/microssegundos com assimetria, perda 0,1%):

- Todos chegaram ao estágio do kernel (`Error: Failed to find specified qdisc` / `Specified qdisc kind is unknown`), ou seja, **nenhum erro de sintaxe do `tc`**. `normal.dist` existe em `/usr/lib/tc`.
- Controles negativos: `reorder` sem `delay` -> `reordering not possible without specifying some delay` (confirma a guarda do script); `loss 20% 25% bogus` -> `What is "bogus"?` (o parser realmente valida; o teste tem poder de detecção).
- Isto prova a sintaxe do `netem` e de `loss gemodel` (o `u32` e o `prio` só ficam validados quando o kernel aceitar; a forma é a da documentação oficial). **Não prova** aceitação pelo kernel da VM nem efeito.

Riscos para L-0 (a verificar na VM):

1. módulos `sch_prio`, `sch_netem`, `cls_u32` ausentes ou não carregáveis (exit 5, volta ao Senior);
2. `distribution normal` exige `normal.dist` no iproute2 **da VM**;
3. IPv6 no TCP do JGroups (`protocol ip` não o casa): confirmar com `ss -tn` no netns; o comentário de `cluster/jgroups-tcp.xml:19` fala em IPv4 privado, mas não é prova;
4. formato real de `tc -s qdisc show` (o parser lê `$4` e `$7` da linha `Sent`; está certo para `Sent N bytes P pkt (dropped D, ...)`) e de `ping` (`X% packet loss`);
5. `netem` com `jitter` reordena pacotes por construção; `limit` padrão de 1000 pacotes pode descartar com atraso alto (conta como `dropped`);
6. estritamente, `prio` serve a banda 3 só quando as bandas 1/2 estão vazias; sob saturação do tráfego não degradado, a degradação seria afinada em excesso (improvável em veth, registrar).

### Defeitos/riscos encontrados na leitura (nada corrigido aqui)

- **R3 — falso exit 6 em N5 (99,9%)**: o script falha com exit 6 se `Sent == 0` na banda 3. `Sent` conta pacotes que **passaram** pelo `netem`; com 99,9% de perda, o esperado é pouco mais de 0 pacotes em 120 s (cluster ocioso: dezenas a centenas de pacotes por par), logo `Sent` zerado é provável mesmo com a degradação funcionando. O teste correto é `Sent + dropped == 0` (nenhum pacote casou o filtro). Mesmo risco em `--loss-in` alto nos pares. Pedir correção ao Worker com teste, antes de L-4/N5.
- **Binomial do ping**: tolerância de 3 sigma dá ~0,3% de falso exit 6 por par por execução; com `--loss-corr` a variância é maior que a binomial e o falso positivo cresce. Em L-1, não usar `--loss-corr` com verificação por ping.
- **gemodel sem verificação de taxa**: o ping é dispensado (documentado). Compensar no gate lendo `dropped/(Sent+dropped)` dos contadores e comparando com X com tolerância larga.

## 5. Os quatro desvios herdados

1. **Docs editados por ordem prévia do Gestor** (`testes.md`, `configuracao-infinispan.md`): contraria o prompt-resumo do plano; o diff é puramente aditivo. Não consigo confirmar a ordem a partir do disco; o Gestor deve confirmar. Se confirmada, aceito.
2. **F2+F4 no mesmo nó = exit 7 (o plano dizia "convive")**: **corrigir o plano, não o script.** Tecnicamente `clsact` e `prio` convivem (o plano está certo no nível do kernel), mas combinar partição e degradação no mesmo nó confunde a atribuição causal e a matriz do T-D roda uma falha por vez (`--faults "none F1 F2 F3 F4"`). O script é mais estrito e mais seguro. Ajuste de texto no plano (T-C1 "Convivência" e L-6): "F2 e F4 convivem tecnicamente; o injetor os exclui no mesmo nó por desenho experimental (exit 7)".
3. **`--direction` padrão `out`**: aceito (é o egress "no nó onde roda" do plano). Consequência para T-D: passar `--direction both` **explicitamente** onde a matriz 8.1 exigir simetria. Consequência para L-6: com `out` só o alvo é tocado; com `in|both` **todos os pares** são tocados, então F3 em qualquer par vira exit 7.
4. **SIGINT não testável** em job assíncrono: aceito, com fechamento no gate (ver R2, L-5b: `kill -INT` em job de fundo e Ctrl-C em primeiro plano; esperado rc 130, sem `prio` residual).

## 6. Roteiro L-0..L-6 do relatório: completo? seguro?

Cobre o plano (módulos, efetividade, limpeza, direções, severidades, sinais, coexistência), usa `tmux`, `flock /var/lock/tcc-vm.lock`, `PODMAN="sudo podman"`, guarda de parada obrigatória e `--heal`. Erros e lacunas a corrigir **antes de T-C2** (R2):

1. **`sudo modprobe sch_prio sch_netem cls_u32` está errado**: `modprobe` trata o 2º e 3º argumentos como **parâmetros do 1º módulo**. Usar `for m in sch_prio sch_netem cls_u32; do sudo modprobe $m; done`, e verificar cada um com `lsmod` (o `lsmod | grep -E 'a|b|c' ||` basta uma correspondência).
2. **Dead-man's switch com `sleep 300` fixo é inadequado**: L-4 roda 10 injeções de 120 s (mais pings e esperas); um relógio de 300 s dispara no meio de uma execução e remove a regra sob o injetor. Armar **por injeção** com `sleep <duration + 90>` e cancelar ao fim, ou `systemd-run --on-active=<s> --unit=tcc-heal ...`. O subshell precisa de `export PODMAN="sudo podman"` e do `cd` correto; `--heal` sozinho (sem `--nodes`) usa o padrão `isn1,isn2,isn3`, o que está certo. Sobreviver à queda do SSH: `setsid`/`systemd-run`, não um `&` de pane.
3. **Guarda de netns ausente no roteiro**: antes de qualquer `tc` manual, `[ "$(sudo readlink /proc/$PID/ns/net)" != "$(sudo readlink /proc/1/ns/net)" ]` e `[ -n "$PID" ] && [ "$PID" != 0 ]`. Sem isso um `PID` vazio ou de `network=host` pode atingir a NIC da VM e derrubar o SSH.
4. **"go" do Gestor**: o texto o exige como pré-condição, mas o roteiro é um bloco contínuo. Inserir parada explícita: go antes de L-0 (o `add` manual troca a raiz do `eth0` de isn3) e novo go antes de L-1. Registrar o IP da VM e a data/hora do go no log.
5. **L-0 manual** (`qdisc change ... netem loss gemodel 5% 25% 100% 0%`): sintaxe válida (confirmada no *parse*), mas precisa de `qdisc add` prévio do mesmo `handle 30:`; está. Adicionar `tc -V` e `ls /usr/lib/tc/normal.dist` ao registro do ambiente (P-11).
6. **L-1**: `ping -c 50 ... <ip-isn3-host>` testa "Hot Rod/SSH poupados" só por ICMP do host; usar também `curl` ao Hot Rod/REST (11222) com tempo medido (o `curl` do health está certo) e comparar com a linha de base sem F4. Registrar `dropped/(Sent+dropped)` além do ping. Sem `--loss-corr`.
7. **L-3 não discrimina o sentido por `ping`**: o RTT de um ping atravessa a ida e a volta, então com `--direction out --loss 30` a perda de ping é ~30% **nos dois** `ping` (isn3->isn1 e isn1->isn3); o comentário do roteiro ("só a metade") e o critério do plano ("medir perda em cada direção por ping") ficam equivocados. Discriminar pelos **contadores** `tc -s qdisc` (`dropped` aumenta só no alvo para `out`, só nos pares para `in`) ou por UDP/`tcpdump` unidirecional. Também esperar a aplicação (`sleep`) antes de medir: o injetor em `&` ainda está sondando/aplicando.
8. **L-4**: N5 produzirá o falso exit 6 de R3; tratar `Sent` e `dropped` conforme corrigido. `nstat -az TcpRetransSegs` dentro do netns (`nsenter -t $PID -n nstat ...`) é válido.
9. **L-5**: `kill -TERM $P`; `echo rc=$?` com `wait $P` está certo (esperado 143). Acrescentar **L-5b (SIGINT, rc 130)** para fechar o desvio 4 e **L-5c (SIGHUP, rc 129)**. Depois do `kill -KILL`, os `sleep`/`ping` filhos ficam órfãos: aguardá-los antes de reexecutar.
10. **L-6**: o plano e o roteiro dizem "F4 isn3 + F3 isn2 (nós distintos) -> exit 0". Isso só vale com `--direction out` (toca só isn3). Com `in|both` o injetor toca também isn2 e sai com exit 7. Testar os dois e registrar. O caso F2 (partição) usa `clsact` e deve ser incluído (exit 7 no mesmo nó; coexistência em nós distintos com `out`); o roteiro o deixa condicional ("se o plano de T-C2 exigir"), e o plano o exige.
11. Segredos: o `curl -u admin:infinispan` repete a credencial de laboratório já presente em `docs/configuracao-infinispan.md`; manter fora de qualquer artefato publicado do gate (P-11).

## 7. Final de linha (autocrlf)

Evidência: `git ls-files --eol`: índice `i/lf` em todos os scripts, mas `w/crlf` em `inject-crash`, `inject-jitter`, `inject-partition`, `run-baseline` e, em um checkout limpo, também nos dois arquivos novos; `core.autocrlf=true` vem de `C:/Program Files/Git/etc/gitconfig` e **não há `.gitattributes`** no repositório. Um checkout no Windows copiado para a VM quebra (§1). Os arquivos do `wt-tc` estão em LF só porque o Worker os escreveu assim; não é garantia.

Mecanismo exigido (R1): `.gitattributes` na **raiz** do repositório, em branch própria `chore/gitattributes-eol-lf` (uma responsabilidade por branch), a partir da base de integração e antes de T-C2/T-D:

```
*.sh   text eol=lf
*.bash text eol=lf
*.xml  text eol=lf
```

(`*.xml` por `cluster/jgroups-tcp.xml`/compose; `*.yml`/`*.tla`/`*.cfg` opcionais.) Depois `git add --renormalize .` e commit. Efeito: o `checkout` no Windows passa a produzir LF, e o Git Bash continua rodando a suíte. Na VM, levar o código por `git bundle`/clone (blobs com LF) e proibir `scp` da árvore de trabalho do Windows. Portão extra no roteiro, antes de qualquer execução: `grep -rlI $'\r' scripts/ | wc -l` deve dar 0.

## 8. Interface congelada para T-D (aprovada)

`--target`, `--nodes`, `--peers`, `--direction out|in|both` (padrão `out`), `--iface`, `--duration`, `--severity N1..N5` (1%, 5%, 20%, 50%, 99,9%) ou `--loss`, `--loss-in`, `--loss-corr`, `--loss-model random|gemodel`, `--burst`, `--delay`, `--jitter`, `--duplicate`, `--reorder p[,c]`, `--corrupt`, `--ping-count`, `--marker` (`fault=F4`), `--heal`, `--dry-run`, `--help`; env `PODMAN`, `LOCK_DIR`; exit 0..7. O T-D deve passar `--direction` explicitamente. Mudanças de R3 não alteram a interface.

## 9. Ressalvas e condições para liberar T-C2

| # | Ressalva | Quem | Bloqueia |
|---|---|---|---|
| R1 | `.gitattributes` com `eol=lf` para `*.sh` (e `*.xml`) em branch `chore/gitattributes-eol-lf`, renormalizar; bundle/clone na VM, nunca cópia da árvore do Windows | Worker (infra), Gestor aprova push | levar o código à VM |
| R2 | corrigir o roteiro (itens 1 a 10 de §6) antes de executá-lo; incorporar ao `relatorio-netdegrade-gate` | Worker-3 | T-C2 |
| R3 | `Sent + dropped == 0` em vez de `Sent == 0`, com teste novo (commit `fix:`) | Worker-3 | L-4/N5 (não L-0..L-3) |
| R4 | guarda de netns (script opcional em T-D; no mínimo no roteiro, R2.3) | Worker-3 | L-0 |
| R5 | Gestor confirmar a ordem prévia que autorizou editar `testes.md`/`configuracao-infinispan.md`; ajustar o plano quanto a F2+F4 (exit 7) e quanto ao critério de ping do L-3 | Gestor/Senior | não |

**T-C2 pode ser liberada quando houver SSH**, com go do Gestor antes de L-0 e antes de L-1, e com R1 e R2 cumpridas; L-4 só após R3.

## 10. Observação V-A pendente (catálogo do B-32)

O Worker do catálogo (`eb4e79c` em `test/b32-auditoria-controle`) escreveu a seção "Limites da evidência" sem ler `docs/validacao-ta-2026-10-15.md`. Conferido linha a linha: o cabeçalho, a marca `vácuo-verificado (cotas reduzidas)` em 8 células e a ressalva 2 conferem; divergências corrigidas por mim, **somente docs**, em `5f7e0c1` (`wt-ta`):

- cabeçalho remetia a "(§7)"; a seção é a **§8**;
- ressalva 4 chamava `rc=150` de "0/100 e 0/120 **violações**": `rc=150` é erro de extração do TLC, não violação; agora traz 2/100 (script antigo, V-A), 5/80 e 4/80 (Worker) e 0/100 e 0/120 (corrigido);
- ressalva 3 sem as propriedades/tempos exatos (60 s para `I2Fa`/`I1M`/`I4M`, 120 s para `I1P` em queda): completada;
- faltavam a ressalva 1, a nota de leitura (r1 não recebe a própria mensagem) e a recomendação Q-5 (não alterar `SessionStore.tla`; variante `max(relogio, ts)` opcional): acrescentadas.

Nenhum número do catálogo foi alterado; os vereditos continuam como validados.
