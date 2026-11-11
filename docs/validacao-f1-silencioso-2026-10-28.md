# Validação do F1 silencioso (opção B), branch `fix/f1-crash-silencioso` (2026-10-28)

> Senior, 2026-10-28. Entrega do Worker no *worktree* `wt-f1-kill`, base `c055399`, commits
> `2a82d42` (test), `0d955c0` (test), `0e83ec3` (fix), `db53600` (docs). Referências: prompt
> `docs/prompt-f1-kill-2026-10-27.md` §7, decisões §9.1, complemento 2 e DT-017; relatório do Worker
> `wt-f1-kill/docs/relatorio-f1-silencioso-2026-10-27.md`.
>
> Restrição da sessão: o Gestor rodava o S4 na VM. Não rodei `mvn`, podman, `run-rebaseline.test.sh`
> nem suítes shell longas. Fiz leitura, `git diff`/`show`, `git archive`, SHA-256 e testes Python
> curtos (< 10 s cada).

## 1. Veredito

**APROVADA COM RESSALVAS.** O código e os testes cumprem o prompt. Os *hashes* do *bundle* se
reproduzem. As edições em testes existentes ficaram dentro da Q2. Nenhum desvio pede mudança de
código. Antes da integração local faltam duas coisas: a re-medição das suítes shell pelo
coordenador (seção 5) e as respostas do Gestor às perguntas P1 a P3 (seção 4).

## 2. O que foi conferido

| Item | Resultado |
|---|---|
| Histórico | linear sobre `c055399` (pai a pai: `2a82d42` → `0d955c0` → `0e83ec3` → `db53600`), sem *merge*, *trailer* presente nos commits |
| `workload/` e `cluster/` | `git diff --name-status c055399 HEAD` vazio |
| `run-rebaseline.sh` | `d95305fe…7366` nos 5 commits (`c055399` a `db53600`), byte a byte igual |
| *Bundle* por `git archive` (`0e83ec3` e `db53600`) | `inject-crash.sh` = `5b4d5719…ddc0cf`; `rebaseline.py` = `3005f49a…c3c7cd`; `matriz.tsv`, `inject-jitter.sh`, `rebaseline_matriz.py` iguais a `c055399`; lista no formato `sha256sum` = `003814e9…2133a4`. O mesmo método em `c055399` dá `e304ba4b…`. O commit `docs:` não toca o *bundle*. O jar entrou na lista pelo *hash* declarado (G2, `5eced136…`); não foi re-hasheado nesta sessão |
| Edições em testes existentes (Q2) | o diff real de `test_rebaseline.py` e de `run-rebaseline.test.sh` é idêntico ao do relatório §3. Fora da classe nova `A9bK6F1Silencioso` (acréscimo, item 4) só mudam: a *fixture* de F1 (169-172), `test_f1_log_sem_start` (611), o *shim* do podman (`kill` e `{{.State.ExitCode}}`), o *shim* novo `ip`, e R3 (367, 396), R4 (420-428) e R8 (533, 537-538). Limites e tolerância de R4 iguais. Os dois comentários trocados (423-424) estão dentro do trecho 420-428 autorizado. **Dentro da Q2** |
| `rebaseline.py` | só o ramo F1 de `k6` muda: chaves `log_modo`, `log_isolamento`, `log_kill`, `log_exit137`, `log_start`; `log_stop` sai; texto do limite atualizado |
| Vermelho Python | reproduzido por `git archive` de `2a82d42`: 3 falhas e 2 erros em `A9bK6F1Silencioso`, como o relatório diz. Em `0d955c0`, `A9K6.test_offsets` também falha (é o efeito esperado da *fixture* nova contra o K6 antigo) |
| Verde Python em `HEAD` | `A9K6` e `A9bK6F1Silencioso`: 13 OK em 6 s |
| Mutante M7 | refeito: `\b(kill\|stop) isn2\b` é morto por `test_stop_no_lugar_de_kill_recusa` e `test_log_de_junho_recusa`. Um mutante extra (tirar `e137` da conjunção) é morto por `test_cada_linha_ausente_recusa` e `test_exitcode_143_recusa` |
| Mutantes shell M1..M6 e M8..M11 | **não refeitos** (a suíte leva 41 s e a sessão tinha limite). A tabela do relatório é coerente com os casos (cada mutante tem um caso que o mata pelo nome) |
| Injetor (leitura) | ordem guarda → `ip link show` → *trap* → `link set down` → confirmação → `kill` → *poll* → ExitCode → `sleep & wait` → `start`. Exit codes de acordo com o contrato C2. *Trap* por fase idempotente (`TRATADO`). `--dry-run` não exige dependências. Carimbo `+%3N` por linha |
| *Smoke* (amostra) | `MANIFEST.sha256` confere nos 154 arquivos. e6 recalculado a partir dos brutos: isolamento 05:05:57,682Z e primeira `ISPN000094` sem `isn2` 05:06:34,214Z (isn1), **Δdet = 36,53 s**, como no relatório. ExitCode no `inspect-parado.json`: 0 em e1/e4 (stop) e 137 em e6 |
| Credenciais nos brutos | `inspect-parado.json` traz `USER=admin`/`PASS=infinispan`. São os padrões de laboratório que já estão em `cluster/podman-compose.yml` no git. Nada novo exposto |

## 3. Parecer sobre os desvios levantados pelo Worker

1. **`podman kill <nó> --signal KILL` em vez de `--signal KILL <nó>`.** **Aceitar.** A semântica é a
   mesma: o Podman aceita a *flag* depois do nome, e o *smoke* deu 137 em todos os `kill`. O *shim*
   autorizado (`kill) no="$1"`) e o padrão `podman|kill|isn2` de R3/R4/R8 dependem dessa ordem.
   Trocar a ordem obrigaria a mexer em testes fora da Q2 sem ganho nenhum. Não recomendo reescrever
   o texto aprovado do complemento 2 nem a DT-017. O caminho é uma **nota de execução** na adenda da
   DT-009 parte 2 (que já vai ser escrita com os *hashes*), dizendo que a forma literal é
   `podman kill isn2 --signal KILL`, com a mesma semântica. O ensaio da VM (§4.2) confirma o 137 no
   Podman da VM.
2. **Ordem test, test, fix, docs.** **Aceitar.** É TDD mais estrito que o pedido: os dois `test:`
   ficam vermelhos contra o injetor antigo e todo commit a partir do `fix:` fica verde. O
   `inject-crash.test.sh` mudou no segundo `test:` só em ordem do `kill` e casamento por prefixo, e
   o vermelho foi refeito com a versão final (56/57). O arquivo é novo, então a Q2 não se aplica.
3. **Comportamentos a mais no injetor.** **Aceitar os três.** O `link set up` antes do exit 5, quando
   o `kill` não derruba o contêiner, evita deixar `isn2` vivo e mudo, que era o risco 2 do plano. O
   exit 5 no erro do `podman stop` só afeta o modo `stop`, que fica fora do *rebaseline*, e fica
   coerente com o `start`. A *regex* no `--node` é endurecimento (o *runner* passa `isn2`). Nenhum
   deles muda o contrato C2.
4. **Brutos do *smoke* no commit `docs:`.** **Aceitar.** O ideal seria um commit `chore:` separado,
   mas são só os versionáveis (D-005: `*.log`, `*.txt`, `*.csv` ficam fora), não tocam o *bundle* e
   o `MANIFEST` confere. Reescrever o histórico para separar não vale o custo, já que a regra é não
   fazer *amend* nem *rebase*.
5. **`run-baseline.sh:11` ainda diz "(podman stop)".** **Corrigir antes da integração**, num commit
   próprio de uma linha (`docs:`) na mesma branch. O comentário passa a dizer "crash silencioso:
   isolamento do eth0 e podman kill (modo padrão; DT-017)". O arquivo está fora do *bundle*, então
   nenhum *hash* muda. A DT-017 já declara que `run-baseline.sh` usa o modo novo, e um comentário
   que diz o contrário contradiz isso.
6. **Achados do *smoke*.**
   - **`stop` local com ExitCode 0, não 143.** Não afeta o *rebaseline*, que não usa `stop`. Afeta
     a redação da conferência h3 do complemento 2 §4 ("um `died` com 143 ... é recusa"): fora da
     VM, uma parada ordenada sairia com 0 e escaparia da regra literal. A DT-017 já diz o positivo
     ("confere `died` de `isn2` com 137"). Na prática o K6 já recusa, porque exige `ExitCode=137` e
     `kill isn2` no *log*. Proponho registrar na adenda que o parecer h3 lê "código ≠ 137 ⇒ recusa
     K6" (pergunta P2).
   - **e6 com Δdet = 36,5 s, abaixo da faixa [38, 55].** A faixa era expectativa minha no prompt,
     não critério, e a condição de parada (< 30 s) não disparou. A explicação do Worker é coerente
     com o FD_ALL3: *timeout* de 40 s, verificação a cada 8 s, contando desde a última mensagem
     recebida, mais 1 s do VERIFY_SUSPECT2. Isso dá uma faixa compatível de cerca de [32, 49] s. A
     "linha do tempo esperada" do complemento 2 §2 (+89 a +98 s) é declarada como "esperado, não
     critério". Basta registrar a faixa observada (36,5 a 41,9 s) na adenda e no relatório do
     ensaio da VM, sem fixar mínimo. Nada a mudar.
   - **Aviso do R4 (+53,5 a +54,1 s nas três rodadas).** Aceitável. A asserção (nunca antes de
     N + 48 s) passou e o disparo do *runner* ficou em ±1 s. O atraso vem do custo de *fork* do Git
     Bash com o relógio ×2 nos seis processos a mais antes do `kill`. No *smoke* real, o `kill` saiu
     17 a 35 ms depois do isolamento. Quando o coordenador re-medir, o aviso é esperado e não é falha.
   - O `podman start` de 51 s (e1, WSL) e a troca de IP de `isn2` a cada volta entram no relatório do
     ensaio da VM como pontos a observar.
   - O ensaio c1 refeito (o encaminhamento de porta do Windows não volta depois do `kill`) é
     artefato do Podman no Windows. O bruto inválido foi guardado com nome explícito. Aceito.

## 4. Perguntas ao Gestor

| # | Pergunta | Recomendação |
|---|---|---|
| **P1** | Aceita a ordem `podman kill isn2 --signal KILL`, registrada como nota de execução na adenda da DT-009 parte 2, sem editar o complemento 2 nem a DT-017? Alternativa: autorizar a troca do padrão em R3/R4/R8 e do *shim* para manter a ordem literal. | **Aceitar a ordem e registrar a nota.** Mesma semântica, sem nova edição de testes |
| **P2** | A conferência h3 do complemento 2 §4 passa a ser lida como "`died` de `isn2` em execução F1 com código ≠ 137 ⇒ recusa K6", e não só "= 143"? O registro seria uma nota interpretativa na adenda, mais estrita e coerente com a DT-017. | **Sim.** O *smoke* mostrou que uma parada ordenada pode sair com 0 |
| **P3** | Autoriza o Worker a corrigir o comentário de `run-baseline.sh:11` num commit `docs:` próprio na mesma branch, antes da integração? | **Sim.** Uma linha, fora do *bundle*, sem efeito em *hash* |

## 5. Re-medição pelo coordenador, depois do S4

Em cópia limpa (`git archive db53600`, ou da ponta depois da P3, extraída num diretório novo), com o
*host* livre:

1. `bash scripts/inject-crash.test.sh` (113 PASS esperados, cerca de 40 s). O mesmo arquivo rodado
   contra o `inject-crash.sh` de `c055399` (`CRASH_SCRIPT=<cópia antiga>`) tem de dar 56 PASS /
   57 FAIL.
2. **Pelo menos M2, M3, M4 e M5** com `CRASH_SCRIPT=<cópia mutada>` (isolamento, ordem, guarda,
   *trap*: os que protegem a VM). Cada um tem de ter FAIL > 0.
3. `bash scripts/run-rebaseline.test.sh`, **1 rodada** (130 PASS, cerca de 20 min). As três rodadas
   do Worker cobrem a estabilidade, e o aviso do R4 é esperado.
4. `inject-jitter.test.sh`, `inject-partition.test.sh` e `inject-netdegrade.test.sh` (cerca de 7 min
   no total): o `inject-crash.sh` não é compartilhado, mas a mudança de ambiente justifica conferir.
5. Python: `analysis/test_rebaseline.py` completo (69 OK), `scripts/test_rebaseline_matriz.py`
   (8 OK), `analysis/test_contrato_csv.py` e `_v2.py` (sem `-I`, por causa do numpy).
6. SHA-256 de `inject-crash.sh`, `rebaseline.py`, `run-rebaseline.sh` e da lista, na cópia, e
   SHA-256 do jar congelado real contra `5eced136…`.

Depois disso: *fast-forward* local da integração e, em seguida, a adenda da DT-009 parte 2, com a
ponta final, a tabela, a lista `003814e9…`, as notas P1/P2 e o `<data>` do complemento 2 §4
(*smoke* de 2026-10-27/28). Nada vai para `origin` sem aprovação.
