# Validacao dos fixes A1-A4 de B-29 (V-B, correcoes) — 2026-10-16

Senior. Entrega do Worker-2: `fix/b29-contadores-por-sentido` (`bc4a16f` vermelho, `3f7b8c1` fix, `a8a6508` docs), `fix/b29-guarda-netns-particao` (`946a2f6` vermelho, `0f79256` fix, `3d1c0a9` docs), ambos sobre `daf4857`, e `dac9fa2` em `test/b29-gate-vm`. Nada foi executado na VM. Checkouts limpos via `git archive` (sem tocar nas branches).

## 1. Suites (checkout limpo, numeros medidos)

| Commit | Git Bash (PASS/FAIL/SKIP) | WSL Ubuntu, flock real |
|---|---|---|
| base `daf4857` | 99/0/2 | 101/0/1 |
| contadores `3f7b8c1` | 111/0/2 | 113/0/1 |
| netns `0f79256` | 113/0/2 | 115/0/1 |
| fusao das duas (resolvida a mao, temporaria) | 125/0/2 | nao medido (esperado 127/0/1) |

Coerente com o relatado (+12 e +14; baseline 99/101 igual ao registrado em `docs/testes.md` e ao 101 da VM da V-B). Todos os numeros do Worker conferem.

## 2. Vermelho e reversao do fix

| Teste | Contra codigo antigo | Git Bash | WSL |
|---|---|---|---|
| T-par-16/17 (`bc4a16f`) | FAIL | 7 | 7 |
| T-par-18 (`946a2f6`) | FAIL | **11** (Worker disse 9) | 11 |
| T-par-16/17 final (`3f7b8c1`) + script antigo | FAIL | 7 | n/a |
| mutacao: `soma_pkts` sem filtro de offset | FAIL | 6 (so T-par-16) | n/a |
| mutacao: guarda sem teste de netns ilegivel | FAIL | 2 (T-par-18d) | n/a |

- Divergencia: T-par-18 vermelho tem 11 asserts FAIL, nao 9. Nao afeta o veredito (numero de asserts, nao de testes; provavelmente contagem do relatorio estava defasada).
- **Ressalva de TDD:** o commit vermelho `bc4a16f` tinha um defeito no shim do `tc` (faltava `n="${n16}"` no laco `at 16`); o `3f7b8c1` corrigiu o shim junto com o fix. Os 7 FAIL do vermelho sao reais, mas o vermelho "puro" (teste final + codigo antigo) tambem foi medido: 7 FAIL. Sem impacto, apenas registrar.

## 3. Logica (A1, A3, A2)

**A1 `soma_pkts <pid> <hook> <offset>`.** Awk guarda o offset do ultimo `match ... at N` e soma `Sent .. pkt` ($4) so se `N == offset`. Alimentei a **funcao real** (extraida do script) com o formato real, extraido de `wt-tb/runs/gate-b29/g1-rep1/inj.log` (blocos `ingress:`/`egress:` do `tc -s filter show`): `soma_pkts ingress 12` = **33**, `soma_pkts egress 16` = **106**, identicos ao bruto (src 21+12, dst 53+53); cruzados (`ingress 16`, `egress 12`) tambem 106/33, confirmando que os dois hooks listam os dois filtros. Logica de saida (T-par-16): so um sentido 0 -> aviso "um so sentido" em stderr com exit 0; ambos 0 -> exit 6; ambos > 0 sem aviso. O "139/139" some. Cosmetico: a linha do `nstc ... | awk` perdeu a quebra e ficou com 9 espacos no meio (funciona).

**A3.** `TIMESTAMP` removido; `log()` chama `date -u` a cada linha. T-par-17 compara a 1a e a ultima linha de uma execucao de 2 s. Correto.

**A2.** `resolver_pid` agora le `sudo readlink /proc/<pid>/ns/net` e `/proc/1/ns/net`; ilegivel (vazio) ou igual -> retorno 2 + mensagem. Alvo, pares e `--heal` passam por ela; alvo/par -> exit 4 antes de qualquer `nsenter/tc`; `--heal` pula o no recusado (nao toca) mas **continua limpando os demais** e sai 4 no fim (T-par-18e). Cobertura: 18a-e (alvo, par, ilegivel, heal). Sem recusa para "container ausente" no heal (mantido exit 0). Sem ressalva de logica.

## 4. `g1.sh` (`dac9fa2`)

`bash -n` ok. Extracao `log-completo-*` (`--since t0-5`) + `log-vista-*` por grep movida para **depois** de `t_merge` e de `saude-depois`: correto. Ressalva menor: se o `until` estourar os 600 s (`exit 1`), os logs **nao** sao extraidos, justo no caso em que mais importam; sugestao: extrair tambem no caminho de abort (ou `trap`). Nao bloqueia REP=2. A5 (sonda Hot Rod) continua pendente.

## 5. Fusao

- Cada fix vs `test/b29-gate-vm`: **sem conflito** (`git merge-tree`).
- contadores x netns: `inject-partition.sh` funde sem conflito; **conflitam** `scripts/inject-partition.test.sh` (linha `unset` do `reset_shims` e o fim do arquivo, T-par-16/17 x T-par-18) e `docs/testes.md` (linhas de tabela). Resolucao trivial, "manter ambos": `unset ... SHIM_PKTS_SRC SHIM_PKTS_DST SHIM_HOSTNET SHIM_NO_NS ...`, blocos T-par-16/17 e depois T-par-18, linhas 16, 17, 18 da tabela. Resultado medido: **125/0/2** no Git Bash.
- Ordem proposta para o branch de integracao (orquestrador): base `test/b29-gate-vm` (`dac9fa2`) -> merge `fix/b29-contadores-por-sentido` -> merge `fix/b29-guarda-netns-particao` (resolve o conflito acima) -> ajustar a linha "Resultado desta rodada" no mesmo commit. Mensagem sugerida: `chore: integra fixes A1-A3 de B-29 (contadores por sentido, timestamp, guarda de netns) e g1.sh corrigido; 125 PASS Git Bash`.

## 6. `docs/testes.md`: exigencia

A linha "Resultado desta rodada: Git Bash 99 PASS / 0 FAIL / 2 SKIP; Ubuntu 101 PASS / 0 FAIL / 1 SKIP" **ficou desatualizada** em ambas as branches (nao foi tocada). **Exijo** atualizar: contadores 111/0/2 e 113/0/1; netns 113/0/2 e 115/0/1; no branch de integracao 125/0/2 (Git Bash) e o valor WSL medido (esperado 127/0/1, a confirmar). Fica como condicao antes do merge, nao do uso das branches.

## 7. Veredito: **APROVADA COM RESSALVAS**

Ressalvas (nenhuma bloqueia): (a) atualizar a linha de resultado de `docs/testes.md`; (b) corrigir "9" para 11 FAIL no relatorio do vermelho T-par-18 e registrar o shim defeituoso do vermelho A1; (c) extrair logs tambem no abort de `g1.sh`; (d) espacos soltos em `soma_pkts`.

**T-D / T-F alem do G-5:** sim. Depois dos fixes integrados e o injetor implantado na VM (verificacao real de `soma_pkts` ocorre na REP=2), faltam: G-5 (sinais; go do Gestor), captura Hot Rod/JSON integral e C-2 (so T-F), e G-6 apos G-5 (ciente de F3). A1-A4 estao cobertos por esta validacao; A5 (sonda Hot Rod no `g1.sh`) segue em aberto para a REP=2.
