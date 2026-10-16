# Validacao V-R: integracao de docs (item 1) e variante MCRelogio R.1/R.2 (B-36), 2026-10-16

Senior. Base: relato do Worker B, tratado como alegacao. Tudo abaixo foi conferido por `git` e pelos `.out` brutos. Nada foi enviado a `origin`; a VM nao foi tocada.

## Veredito

| Item | Veredito |
|---|---|
| 1. Integracao de docs (22 cherry-picks, `ff-only` local) | **PASS com ressalva** (o criterio "merges vazio" de 3.3 passo 3 estava mal escrito; ver 1.3) |
| 2. R.1 e R.2 (`test/b36-relogio-max`, `498c2f8` e `e53752a`) | **PASS** |
| 3. Smoke nao commitado (I1F/I4F violadas com `max`; I1P nao fechou) | **Reproduzido por mim; leitura limitada** (secao 3) |
| 4. Liberar R.3 | **LIBERADO com cotas ajustadas** (secao 4) |

## 1. Integracao de docs

### 1.1 Conferido por git

| Alegacao | Resultado |
|---|---|
| Tags `int/2026-10-16-4b680a2` e `-6ba7e28` | existem; apontam para `4b680a2` e `6ba7e28` |
| 22 cherry-picks | `git rev-list --count int/...4b680a2..HEAD` = **22**; `8e2e815..4b680a2` = 76 (como 3.3 passo 1); `8e2e815..HEAD` = 98 |
| `-x` em todos | 22 de 22 com `(cherry picked from commit ...)`; cada um mapeado a um SHA de origem da tabela 3.1 |
| Conteudo igual ao da origem | `git patch-id --stable`: **21 de 22 IGUAL**. O unico que difere e `78b2e3f` -> `8c2e52b` (o conflito); as linhas `+/-` de `docs/testes.md` e `docs/integracao-etapa-formal-2026-10-16.md` sao **identicas** (diff vazio), so muda o contexto. Sem marcadores `<<<<<<<` nos dois arquivos |
| So `docs/` alterado | `git diff --name-only 4b680a2..HEAD`: 18 arquivos, todos em `docs/` |
| Sem CRLF | `git ls-files --eol | grep -c i/crlf` = 0 |
| Branch igual a ponta | `docs/integracao-docs-2026-10-16` e `feature/etapa-formal-integracao` ambas em `6ba7e28` (ff-only ja feito) |

### 1.2 `ff-only` ja feito: aceitavel?

Sim. E local, sem remoto, e o passo 4 do proprio 3.3; a reversao esta garantida pelas duas tags (`git reset --hard int/2026-10-16-4b680a2`). Como so `docs/` mudou, o passo 5 (regressao) nao tem como mudar contagem; **ressalva**: o passo 5 ainda e exigido antes de qualquer fatia ir para `main`, e eu nao o reexecutei aqui.

### 1.3 O merge `7ec14b2`

`git log --merges 8e2e815..HEAD` **nao e vazio**: lista `7ec14b2` (pais `e6e5985` e `5448791` de `tmp/vm-bundle-2`, 8 arquivos: sonda Hot Rod, `g1b.sh`, testes). Ele e **ancestral de `4b680a2`**, ou seja, veio da rodada anterior da integracao e **nao** dos 22 cherry-picks: `git log --merges int/2026-10-16-4b680a2..HEAD` e vazio. O Worker esta certo; o erro foi do meu passo 3 (o intervalo certo era a partir da tag, nao de `8e2e815`). **Corrijo o criterio para**: `git log --merges int/2026-10-16-4b680a2..HEAD` vazio.

O merge **nao invalida nada** do trabalho de docs. Consequencia para depois: a regra "historia linear, nenhum commit de merge" do 3.2 nao vale para a ponta como esta. Ao preparar fatias para `main` (3.4) elas devem ser feitas por `cherry-pick`/`rebase` de commits de conteudo, nunca publicando a integracao com esse merge. Isto vai para a lista de aprovacao do Gestor antes de qualquer push (nao decido sozinho).

## 2. R.1 e R.2 (wt-b36)

### 2.1 R.1

- `git diff --stat 6ba7e28 HEAD -- spec/SessionStore.tla spec/MCSession.tla` vazio; `runs/tlc` preexistente inalterado (todo arquivo novo esta em `runs/tlc/relogio-*`); a unica alteracao em arquivo existente e `docs/testes.md` (+16 linhas, secao propria no fim, como previsto).
- `spec/MCRelogio.tla` lido inteiro: `EXTENDS MCSession`, `Max`, `TsMsg(m) == Max(m.sessao.ts, m.ident.ts)`, `EntregaMensagemMax` com `relogio' = [relogio EXCEPT ![r] = Max(@, TsMsg(m))]` e UNCHANGED sem `relogio`, `NextMax`, `SpecRelogio`, `SpecRelogioSemeada`, `RelogioDomina` identicos ao 1.4. Nao ha alteracao silenciosa do corpo.
- `bash spec/check-variante-relogio.sh`: rc 0, "corpo identico (orig=44 var=45; 1 linha -> 2 linhas); diff vazio".
- `bash spec/check-variante-relogio.test.sh`: **4 PASS, 0 FAIL** (variante real aceita; gate `>=`, relogio sem max e UNCHANGED incompleto recusados).
- SANY em `MCRelogio.tla`: sem erro.
- Arvore limpa, sem CRLF.

### 2.2 R.2: linha a linha contra a base

Cfgs: `diff` das cfgs mutadas contra as da base difere **somente** na linha `SPECIFICATION` (Spec -> SpecRelogio / SpecSemeada -> SpecRelogioSemeada); cotas, simetria e restricoes iguais. Os `.out` indicam `Semantic processing of module MCRelogio`, TLC 2.19 nos dois lados e `EntregaMensagemMax` nos tracos.

| Execucao (mutacao = `@`) | Base | Mutada | Confere |
|---|---|---|---|
| livre r3 `I1P` | VIOLADA, 4 estados, 476 / 122 | VIOLADA, 4 estados, 476 / 122 | sim |
| livre r3 `I1F` | VIOLADA, 7, 110551 / 13835, profundidade 7 | idem (`.out`: 110551 / 13835, profundidade 7) | sim |
| livre r3 `I4F` | 7, 110551 / 13835 | idem | sim |
| livre r3 `I2Fb` | 4, 318 / 76 | 4, 318 / 76 | sim |
| semeado r2 `I2Fa`, `I1M`, `I4M`, `I4Mp` | SEM_VIOLACAO, 38643 / **1988** | SEM_VIOLACAO, 38643 / **1988** (`.out` de `I4Mp` lido: profundidade 9) | sim |
| semeado r3 `I1F`, `I1P` (MaxCanal 2, MaxRelogio 3) | SEM_VIOLACAO, 53541786 / **1627731** | SEM_VIOLACAO, 53541786 / **1627731** (`.out` de `I1P`: profundidade 19, "No error has been found") | sim |
| livre r3 `RelogioDomina` (mutada) | n/a | **VIOLADA, 3 estados**, 59 / 13; no estado 3 `relogio[r2] = 0` e r2 tem sessao com `ts = 1` apos `EntregaMensagemMax` com `@` | sim, como previsto |

Tracos: o estado-a-estado dos 4 tracos mutados tem o mesmo conteudo da base (comparado como multiconjunto de caracteres; a ordem dos campos dos registros muda na impressao do TLC, o que e esperado). Tempos diferem (nao fazem parte do criterio).

Observacao: as execucoes livres usam `SYMMETRY Simetria` e cotas 3/5, como o `prop-livre-sem-w1` da base (nao e a variante "sem simetria" de 1.5; e a mesma configuracao do controle da V-A, portanto comparavel).

Controles 2, 3 e 4 do 1.4: **cumpridos** (4 exige a variante sem mutacao fechada; ver 4).

## 3. Smoke nao commitado

Os brutos do smoke do Worker **nao estao em disco** (nao achei na worktree nem no tmp). Reproduzi eu mesmo, 1 worker, em diretorio de scratch (nada commitado), `livre sem r3`, cotas 3/5/2, variante **sem** mutacao:

| Propriedade | Resultado meu |
|---|---|
| `I1F`, `I4F` | **VIOLADAS**, tracos de 8 estados, 394270 / 45774 em 5 s |
| `RelogioDomina` | sem violacao ate 60 s (294430 gerados / 35179 distintos); **nao fechou** |
| `I1P` | sem violacao ate 90 s (5161935 / 474435); **nao fechou** (a base violava em 122 estados) |

Isto concorda com o relato do Worker nos tres pontos (o 300 s dele nao foi reproduzido por mim; usei 90 s).

Mecanismo do traco de `I1F` (lido estado a estado): `Login(r1,s1)`, `Login(r2,s1)` concorrentes, `Login(r1,s2)`; r3 recebe `s2` (ts 2) e o `max` leva `relogio[r3]` a 2; `Login(r3,s1)` com ts 3; `Logout(r2,s1)` ts 2 (aceito; 2 > 1); a entrega a r2 do `SESSAO_VALIDA` de r3 (ts 3 > 2) reescreve INVALID por VALID. **Nenhum passo local teve o gate rejeitado**, todos os `ts` locais superam o do registro substituido. O que ocorre e conflito LWW entre um logout e um login concorrente da **mesma sessao** em replicas diferentes, com o login mais recente ganhando.

### O que significa e o que nao

- Sustentado: com o relogio acompanhando o `ts` recebido, a classe de violacao `I1P` (logout aceito e perdido por gate) **some ate onde olhei, sem fechar**, enquanto `I1F` e `I4F` livres **continuam violadas por um mecanismo diferente do D-4**. Em termos da tabela 1.6, a segunda linha (violada em traco sem passo de gate rejeitado) e a que esta ativa para `I1F`/`I4F`.
- **Nao sustentado**: (a) que `I1P` esta "fechada"; nao fechou. (b) que `I1F`/`I4F` existam no sistema real: a pergunta e se duas replicas podem executar `Login` da mesma `sid` concorrentemente (o `Login` do modelo so exige `estado = NONE` local). Isso e decisao do Gestor sobre o sistema (a tabela 1.6 ja preve a pergunta). (c) que P-26 possa mudar de status; (d) nada disto entra na evidencia do sistema.
- Efeito formal: **P-26 continua `ABERTO-ACEITO`**. **P-28 so deve ser aberta** depois de R.3 fechar a linha `I1F`/`I4F` (o traco ja basta como violacao, mas o aceite exige o `.out` commitado e a coluna D-4 refeita por `scripts/audit_tracos.py`) e a P-26 so e reclassificada para `FECHADO-POR-VARIANTE` se `I1P` fechar sem violacao e `RelogioDomina` fechar verde.
- A tabela 1.6 era pre-registrada; o smoke nao a altera. Registro apenas que, para as linhas `I1F`/`I4F`, o desfecho provavel e a linha 2, e que isto nao reduz o valor de R.3 (a evidencia que falta e o fechamento de `I1P` e `RelogioDomina`).

## 4. R.3: liberado, com cotas

Condicao: **sem janela de VM aberta e sem Worker A em TLC/suite pesada** (confirmacao abaixo). Sem janela para confirmar por mim: no momento da conferencia nenhum processo `java` rodava, mas ha atividade recente de g5b em `/tmp` (13:28), entao a confirmacao final de que o Worker A terminou e do Worker, antes de disparar o nivel 2.

| Nivel | Cotas e execucao | Propriedades | Timebox | Notas |
|---|---|---|---|---|
| 1 | livre, **r2**, `MAXCANAL=2`, `MAXRELOGIO=3`, `MAXEVENTOSFALHA=2`, `SEM_SIMETRIA=1`, `WORKERS=1`, colunas sem/queda/particao | `I1P I1F I4F I2Fb RelogioDomina` | 10 min por coluna | concorda com a sugestao do Worker e com 1.5. `OUTDIR` novo `runs/tlc/relogio-livre-<falha>-...` (sem sufixo `mut`) |
| 1b | idem, cotas `3/5/2` | idem | 10 min | se nao fechar, registrar "nao-fechado" |
| 2 | livre, **r3**, **sem falha**, `MAXCANAL=2`, `MAXRELOGIO=3`, `SEM_SIMETRIA=1` | `I1P I1F I4F RelogioDomina` | 30 min | `WORKERS=1` ate o Worker A terminar; depois `WORKERS<=4`. `I1F`/`I4F` ja violam em segundos (basta o traco); a execucao existe para tentar fechar `I1P` e `RelogioDomina` |
| 3 | opcional, semeado r2 queda/particao | `I1F I4F I2Fa I1M I4M` | | veredito deve ser identico ao da base |

Exigencias de R.3 acima das do 1.7:
1. Commitar todos os `.out` e `.cfg` (inclusive as execucoes que violam e as que atingem o limite); o resumo marca "limite de tempo" como `SEM_VIOLACAO_ATE_AQUI`, nunca como verde.
2. Rodar `scripts/audit_tracos.py` sobre os tracos de `I1F`/`I4F` da variante e anotar quais passos tem gate rejeitado.
3. R.4 relata a tabela 1.6 com a linha ativa por propriedade, sem tom de conclusao sobre o sistema real.
4. Nenhum TLC nas janelas de injecao da VM e nos 5 min antes e depois.

## 5. Ressalvas e pendencias

1. Passo 5 de 3.3 (regressao) nao reexecutado por mim.
2. O merge `7ec14b2` ja esta na ponta; resolver no momento das fatias para `main` (Gestor).
3. Brutos do smoke do Worker nao verificaveis; reproduzidos.
4. Nao toquei `origin`, VM, nem arquivos das branches validadas. Esta validacao fica na branch `docs/validacao-vr-2026-10-16`, so commit local.
