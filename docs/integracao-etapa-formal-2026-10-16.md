# Integracao local da etapa formal (2026-10-16)

Branch `feature/etapa-formal-integracao`, worktree `wt-integracao`, **somente local** (sem push). Historico linear, 51 commits sobre `8e2e815` (`feature/b22-f2-particao`), nenhum merge commit. As branches de origem nao foram alteradas; tudo foi feito por `git cherry-pick` no worktree de integracao, o que preserva a linearidade exigida pelo plano (5.3 e T-H).

## Base

`8e2e815`. Conferido com `git merge-base`: B-29, B-29.2, B-32 (e auditoria) e B-33 nascem em `8e2e815` (B-29.2 e os fixes/gate nascem em `b56c6ea`, ponta de B-29). A excecao e `chore/gitattributes-eol-lf`, cujo merge-base com `8e2e815` e `0df5383` (parte de `main`, com 3 commits de `main`); por isso so o commit `6dc7cbe` foi aplicado (cherry-pick), sem trazer a historia de `main`. Os fixes A1-A3 foram feitos sobre `daf4857` (= o mesmo cherry-pick do gitattributes sobre `b56c6ea`), entao o range usado foi `daf4857..<fix>`.

## Ordem aplicada

| # | Origem | Range | Commits | Conflito |
|---|---|---|---|---|
| 1 | `chore/gitattributes-eol-lf` | `6dc7cbe` | 1 | nao |
| 2 | `feature/b29-inject-partition` | `8e2e815..b56c6ea` | 4 | nao |
| 3 | `test/b29-gate-vm` (com `g1.sh` extraindo logs tambem no abort) | `b56c6ea..bbd6e42` | 4 | nao |
| 4 | `fix/b29-contadores-por-sentido` | `daf4857..` | 4 | nao |
| 5 | `fix/b29-guarda-netns-particao` | `daf4857..` | 4 | sim (abaixo) |
| 6 | `feature/b29-2-inject-netdegrade` | `b56c6ea..4f09c3f` | 12 | nao |
| 7 | `test/b32-auditoria-controle` (inclui `feature/b32-tlc-propriedades-fortes`) | `8e2e815..5f7e0c1` | 6 | nao |
| 8 | `feature/b33-instrumentacao-particao` | `8e2e815..7ca5dfa` | 16 | nao |

Auditoria x B-32: o merge-base de `test/b32-auditoria-controle` com `feature/b32-tlc-propriedades-fortes` e a ponta de B-32 (`48d1571`); a auditoria e seus 5 commits assentam sobre B-32 (runner `run-tlc-propriedades.sh`), entao B-32 entra pelo mesmo range (6 commits). Nao foi necessario cherry-pick separado.

Divergencia em relacao ao plano 5.3: o plano propoe ordem b29 -> b29.2 -> b32 -> b33; a ordem pedida pelo Senior intercala os fixes entre B-29 e B-29.2, o que foi seguido. A base indicada por 5.3 (`feature/b22-f2-particao`) e `8e2e815`.

## Conflitos e resolucao

Somente no passo 5 (netns x contadores), ambos em arquivos esperados:

1. `scripts/inject-partition.test.sh`, `reset_shims`: as duas linhas `unset` foram unidas (`SHIM_PKTS_SRC SHIM_PKTS_DST` dos contadores e `SHIM_HOSTNET SHIM_NO_NS` do netns).
2. `scripts/inject-partition.test.sh`, fim do arquivo: mantidos os blocos T-par-16/17 e depois T-par-18.
3. `docs/testes.md`, tabela: linhas T-par-16, 17 e 18 juntas.
4. `docs/testes.md`, linha "Resultado desta rodada" (commit de docs do netns): substituida por uma unica linha com a medicao da fusao (125/0/2 Git Bash, 127/0/1 WSL) e as duas notas de TDD (shim de `tc` do vermelho `bc4a16f`; 11, nao 9, FAIL no vermelho de T-par-18).

`docs/testes.md` tambem foi tocado por B-29.2 e B-29; a fusao automatica funcionou. B-32/B-33 nao conflitaram. `inject-partition.sh` fundiu sem conflito.

## Contagens por passo

Ver tabela em `docs/testes.md` (secao "Integracao local"). Final: inject-partition 125/0/2 (Git Bash) e 127/0/1 (WSL); inject-netdegrade 298/0/1 e 306/0/0; inject-jitter 35/0 e 35/0; `mvn -f workload/pom.xml test` 184 testes, 0 falhas, 0 erros, 2 skipped; `python -m unittest discover -s analysis` (da raiz) 9 OK.

## Ocorrencias

- `.gitattributes` (eol=lf) foi aplicado depois de os arquivos ja estarem no worktree; 44 arquivos ficaram CRLF no disco (indice LF), o que quebrava `inject-jitter.test.sh` no WSL (`$'\r': command not found`). Corrigido restaurando esses arquivos a partir do indice (`git checkout`); sem alteracao de conteudo versionado.
- A linha "Estado atual: 49/49" de `docs/testes.md` foi qualificada com a medicao da integracao; a linha de resultado de `inject-netdegrade` (284/292) foi atualizada para 298/306.
- `spec/` nao foi tocado por esta tarefa (os arquivos de B-32 vieram intactos da origem). Nenhum push.
