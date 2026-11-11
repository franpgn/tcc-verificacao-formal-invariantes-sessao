# Relatório: variante `MesclarJoin` no modelo TLA+ e retomada do B-45 (2026-11-06)

- Branch: `feature/tla-mesclar-join`, criada de `fix/b45-desempate-ts` em `0b6f41a` (herda o T2 do B-45). Worktree
  `wt-tla-join`. Sem push.
- Tarefa (Senior, 06/11): (1) variante do modelo em que a fusão combina as cópias das **sessões** pelo máximo de conteúdo,
  como a política de fusão proposta para o *cluster* (alternativa A1 de `docs/alternativas-tratamento-cluster-2026-11-06.md`,
  branch `docs/plano-implementacao-controlador`); TLC sob partição nas cotas dos traços de 4 estados
  (`runs/tlc/prop-particao-w1/`), com controle negativo; (2) retomar o B-45 (`wt-b45`).
- Restrições de host: no máximo 4 workers do TLC; outros dois Workers testam *clusters* locais (um mede tempos em ms); o B-45
  só volta depois que o Worker da A4 terminar.

## 1. O que foi modelado

`spec/MCSessionJoin.tla` estende `MCSession` (que estende `SessionStore`). **Nenhuma linha de `SessionStore.tla` ou
`MCSession.tla` mudou** (teste T1). A diferença de comportamento é só a ação de fusão:

| Componente | `Mesclar` (publicado, `SessionStore.tla:577-586`) | `MesclarJoin` (variante) |
|---|---|---|
| sessões | o perdedor recebe a cópia inteira de `rf` (uma réplica do lado vencedor) | para cada sid, o **máximo** das cópias de **todas** as réplicas, escrito em **todas** |
| identidades | o perdedor recebe a cópia de `rf` | igual (teste T3) |
| `relogio` | o perdedor recebe o de `rf` | o perdedor fica com `max(relogio[r], relogio[rf])`; o vencedor mantém o seu |
| demais variáveis | `particaoAtiva' = FALSE`; `canal`, `vivo`, `quedas`, `ladoDe`, `particoes` inalterados | igual (teste T4) |

Ordem da fusão: `ChaveFusao(x) = << estado, motivo, ts, identidade >>` em ordem lexicográfica, com os postos do T2
(`NONE < VALID < INVALID`; `NONE < LOGOUT < EXPIRADA < ADMIN_BLOCK`; identidade pela posição fixa em `IDS`). O conteúdo vem
antes da versão, como na regra da A1 (`(RankEstado, RankMotivo, versao, atualizadaEm)`). A tupla contém todos os campos de
`SessaoRec`, então a ordem é total e o máximo é único.

Parâmetro `OrdemFusao`: `"conteudo"` (a política) ou `"invertida"` (`NONE < INVALID < VALID`, controle negativo).

Propriedades: `I1F` e os corpos `CorpoI1F`, `CorpoI4F`, `CorpoI4Fp` são os de `MCSession`. As propriedades que restringem
só passos de fusão foram reescritas com `MesclarJoin` no antecedente (`I1MJ`, `I4MJ`, `I4MpJ`, `I2FaJ`,
`MesclarPerdeChaveJ`). Com `Mesclar` no antecedente, nenhum passo de `NextJoin` o satisfaria e a propriedade ficaria vazia.

Decisões de modelagem que pedem validação (seção 7):

- **D-J1, escopo da fusão.** As cópias de todas as réplicas entram, e o resultado vai para todas. É o que a resolução de
  conflitos do Infinispan faz: lê as cópias de todos os donos (o *hash* da CR é a união dos *hashes* das partições) e escreve o
  valor devolvido em todos os donos (`DefaultConflictManager.java:296-350` do 15.0.21, citado na A1). A alternativa mínima
  (juntar só a cópia de `rf` com a do perdedor e escrever só no perdedor) deixaria o vencedor sem o logout do perdedor.
- **D-J2, relógio do perdedor.** Em `Mesclar` o perdedor copia o relógio de `rf` porque todos os registros dele passam a vir de
  `rf`. Com a junção ele pode manter um registro próprio com `ts` maior que `relogio[rf]`; copiar faria o relógio cair abaixo de
  um registro que a réplica ainda guarda, que é o que o comentário de `Mesclar` quer evitar. O vencedor mantém o seu relógio,
  como em `Mesclar`; o registro fundido pode ter `ts` acima dele, a mesma situação de `EntregaMensagem` (D-4, P-26).

## 2. Testes

`spec/test-mcsessionjoin.sh` (estrutural, sem TLC): T1 modelo publicado igual a `fix/b45-desempate-ts`; T2 `NextJoin` tem os
11 disjuntos de `Next` na mesma ordem, com `Mesclar` trocado por `MesclarJoin`; T3 linha de `identidades'` igual à de `Mesclar`;
T4 mesmo `UNCHANGED`; T5 as cinco propriedades de fusão têm versão J sobre `MesclarJoin` e nenhuma cita `Mesclar`; T6 SANY sem
erro. Resultado em `3c47ccf`: **10 PASS, 0 FAIL**. Mutações (aplicadas e desfeitas fora do git): retirar `Particionar` de
`NextJoin` (T2 falha), perdedor com as próprias identidades (T3), `I1MJ` com `Mesclar` (T5), `vivo` fora do `UNCHANGED` (T4),
erro de sintaxe (T6). As 5 foram detectadas.

Smoke manual antes deste pré-registro (fora do roteiro, saída descartada): `I1MJ` com a ordem invertida violou em 4 estados
(`Logout`, `Particionar`, `MesclarJoin`); com a ordem de conteúdo, 20 s sem violação. Serviu só para validar a `.cfg` com
constante *string*. A execução que vale é a do roteiro (seção 5).

## 3. Plano de execução

`spec/join-plano.tsv` + `spec/run-tlc-join.sh` (*snapshot* da revisão por `git show`, estado em `runs/tlc/join-estado.tsv`,
pausa por `runs/tlc/join-PAUSAR`, `java.io.tmpdir` e `-metadir` por execução). Brutos em `runs/tlc/join-<grupo>/`.

Cotas do catálogo (as de `runs/tlc/prop-particao-w1/` e `prop-livre-particao-w1/`): `IDS={u1}`, `SIDS={s1,s2}`, 3 réplicas,
`LIMITE=2`, `MaxCanal=3`, `MaxRelogio=5`, `MaxEventosFalha=2`, restrição `SoParticao`, simetria `Permutations(SIDS) ∪
Permutations(REPLICAS)`. Semeado (`SpecSemeada`/`SpecSemeadaJoin`) salvo `MesclarPerdeChave`, que o catálogo roda no livre.

| Grupo | O que | Workers, timebox |
|---|---|---|
| `ctrl-w1` | `Mesclar` publicado, mesma revisão: I1F, I1M, I4M, I4Mp, I2Fa, MesclarPerdeChave | 1, 600 s |
| `join-w1` | `MesclarJoin`, ordem de conteúdo: I1F, I1MJ, I4MJ, I4MpJ, I2FaJ, MesclarPerdeChaveJ | 1, 300 s |
| `inv-w1` | `MesclarJoin`, ordem invertida: I1F, I1MJ | 1, 300 s |
| `join` | as que não violarem em `join-w1` (previsto: I1F, I1MJ, MesclarPerdeChaveJ), contagem de estados; `-coverage 1` em I1MJ | 4, 600 s |
| `join-red` | 2 réplicas, `MaxCanal=2`, `MaxRelogio=3`, `MaxEventosFalha=2`, para fechar: I1MJ (`-coverage`), I1F, MesclarPerdeChaveJ (`-coverage`); controle invertido de I1MJ | 4 (o invertido 1), 1200 s |

## 4. Expectativas pré-registradas

Escritas em 06/11, depois do smoke da seção 2 e antes de qualquer execução do roteiro. Divergência é resultado, não erro a
corrigir.

- **E-J1 (controle, `Mesclar` publicado).** I1F, I1M, I4M, I2Fa e MesclarPerdeChave (livre) VIOLADAS com traço de 4 estados;
  I4Mp VIOLADA com 5. São os comprimentos do catálogo (§3, §4.4, §4.5) e da expectativa E-8 do B-45.
- **E-J2 (I1MJ).** Sem violação. Argumento: `INVALID` é o maior estado na ordem de conteúdo; se alguma cópia é `INVALID`, o
  máximo é `INVALID`, então nenhum passo `MesclarJoin` tira uma réplica de `INVALID`. TLC: `SEM_VIOLACAO_ATE_AQUI` nas cotas do
  catálogo (1 e 4 workers); `FECHOU` em `join-red`.
- **E-J3 (I1F, semeado).** Sem violação. No semeado `Login` fica desabilitado (toda sid começa `VALID` e a junção nunca produz
  `NONE`), então não há `SESSAO_VALIDA`; os outros passos mantêm ou põem `INVALID`, e `MesclarJoin` mantém (E-J2). Mesmas
  expectativas de TLC de E-J2.
- **E-J4 (MesclarPerdeChaveJ, livre).** Sem violação: `NONE` é o menor posto, e o máximo de cópias com alguma não `NONE` não é
  `NONE`. `SEM_VIOLACAO_ATE_AQUI` nas cotas do catálogo; `FECHOU` em `join-red`.
- **E-J5 (I4MJ, I4MpJ).** **Continuam VIOLADAS, com traço de 5 estados e outro mecanismo:** `Logout(r1,s1)`, `Block(r2,u1)`,
  `Particionar`, `MesclarJoin`; a junção troca o motivo de `s1` em r1 de `LOGOUT` para `ADMIN_BLOCK` (a sessão segue `INVALID`).
  O traço de 4 estados de I4M (a reversão para `VALID`) desaparece. É consequência da própria ordem (`ADMIN_BLOCK` vence
  `LOGOUT`), a mesma do T2 em `EntregaMensagem`; I4Fp já é `VIOLADA-POR-CONSTRUCAO` sem falha (catálogo §4.3).
- **E-J6 (I2FaJ).** VIOLADA, 4 estados, o mesmo traço do controle (`Block(r1,u1)`, `Particionar`, `MesclarJoin` com `rf` que
  ainda não recebeu o `ID_BLOQUEIA`): as identidades ficam com a fusão atual.
- **E-J7 (controle negativo, ordem invertida).** I1MJ e I1F VIOLADAS com 4 estados (`Logout`, `Particionar`, `MesclarJoin`), nas
  cotas do catálogo e em `join-red`.
- **E-J8 (não vacuidade).** Na contagem do `-coverage`, `MesclarJoin` tem estados distintos > 0 em `j-particao-r3-I1MJ` e
  `jr-particao-r2c2r3-I1MJ`.

## 5. Resultados

Rodada de 06/11, 13:36 a 15:00, revisões `58ecd38` (grupos `ctrl-w1`, `join-w1`, `inv-w1`, `join`, `join-red`) e `682bc4b`
(linhas `jx-`, acrescentadas depois da primeira passada; só o roteiro mudou, os `.tla` têm o mesmo SHA-256, ver
`runs/tlc/join-snapshots.tsv`). Todos os brutos em `runs/tlc/join-<grupo>/` (`.cfg`, `.out`, `.meta`, `resumo.tsv`); estado em
`runs/tlc/join-estado.tsv`. Nenhum `ERRO`. Os números de estados abaixo foram conferidos contra os `.out` (última linha
`Progress` ou o fecho do TLC), não contra o `resumo.tsv`.

### 5.1 Propriedade × resultado × estados × evidência

Salvo indicação, semeado, 3 réplicas, cotas do catálogo (`3/5/2`), `SoParticao`, simetria. "Distintos" e "gerados" são os do fim
da execução; "fila" é o que restou sem explorar (0 = fechou). Caminhos relativos a `runs/tlc/`.

| Propriedade | Variante | Cotas, workers, timebox | Resultado | Traço (1 worker) | Distintos / gerados / fila | Evidência |
|---|---|---|---|---|---|---|
| I1F | `Mesclar` (controle) | r3, 1 w | **VIOLADA** | 4: `Logout`, `Particionar`, `Mesclar` | 162 / 468 / 146 | `join-ctrl-w1/c1-particao-r3-I1F.out` |
| I1F | `MesclarJoin` | r3, 1 w, 300 s | sem violação até aqui | — | 3.667.251 / 18.839.424 / 2.960.845 | `join-join-w1/j1-particao-r3-I1F.out` |
| I1F | `MesclarJoin` | r3, 4 w, 600 s | sem violação até aqui | — | 20.458.469 / 117.309.608 / 15.790.328 | `join-join/j-particao-r3-I1F.out` |
| I1F | `MesclarJoin` | r2, `2/3/2`, 4 w | **FECHOU** | — | 79.224 / 1.308.515 / 0 (profundidade 13) | `join-join-red/jr-particao-r2c2r3-I1F.out` |
| I1F + I1MJ | `MesclarJoin` | r2, `3/5/2`, 4 w, 1200 s | sem violação até aqui | — | 25.801.035 / 205.708.784 / 15.100.133 | `join-join-red/jx-particao-r2-I1F-I1MJ.out` |
| I1F + I1MJ | `MesclarJoin` | r3, `2/3/2`, 4 w, 2700 s | sem violação até aqui (**pausado** aos 516 s, ver abaixo) | — | 8.856.002 / 78.591.225 / 5.242.635 (`Progress` de 15:00:00) | `join-join-red/jx-particao-r3c2r3-I1F-I1MJ.out` |
| I1F | `MesclarJoin` invertida | r3, 1 w | **VIOLADA** | 4: `Logout`, `Particionar`, `MesclarJoin` | 162 / 468 / 146 | `join-inv-w1/i1-particao-r3-I1F.out` |
| I1M | `Mesclar` (controle) | r3, 1 w | **VIOLADA** | 4: `Logout`, `Particionar`, `Mesclar` | 162 / 468 / 146 | `join-ctrl-w1/c1-particao-r3-I1M.out` |
| I1MJ | `MesclarJoin` | r3, 1 w, 300 s | sem violação até aqui | — | 2.126.772 / 10.647.963 / 1.717.314 | `join-join-w1/j1-particao-r3-I1MJ.out` |
| I1MJ | `MesclarJoin` | r3, 4 w, 600 s, `-coverage 1` | sem violação até aqui | — | 10.149.316 / 55.810.253 / 7.970.555 | `join-join/j-particao-r3-I1MJ.out` |
| I1MJ | `MesclarJoin` | r2, `2/3/2`, 4 w, `-coverage 1` | **FECHOU** | — | 79.224 / 1.308.515 / 0 (profundidade 13) | `join-join-red/jr-particao-r2c2r3-I1MJ.out` |
| I1MJ | `MesclarJoin` invertida | r3, 1 w | **VIOLADA** | 4: `Logout`, `Particionar`, `MesclarJoin` | 162 / 468 / 146 | `join-inv-w1/i1-particao-r3-I1MJ.out` |
| I1MJ | `MesclarJoin` invertida | r2, `2/3/2`, 1 w | **VIOLADA** | 4: `Logout`, `Particionar`, `MesclarJoin` | 78 / 264 / 63 | `join-join-red/jr-particao-r2c2r3-I1MJ-inv.out` |
| I4M | `Mesclar` (controle) | r3, 1 w | **VIOLADA** | 4: `Logout`, `Particionar`, `Mesclar` | 162 / 468 / 146 | `join-ctrl-w1/c1-particao-r3-I4M.out` |
| I4MJ | `MesclarJoin` | r3, 1 w | **VIOLADA** | 5: `Logout`, `Block`, `Particionar`, `MesclarJoin` | 1.416 / 4.562 / 1.267 | `join-join-w1/j1-particao-r3-I4MJ.out` |
| I4Mp | `Mesclar` (controle) | r3, 1 w | **VIOLADA** | 5: `Logout`, `Block`, `Particionar`, `Mesclar` | 1.422 / 4.562 / 1.273 | `join-ctrl-w1/c1-particao-r3-I4Mp.out` |
| I4MpJ | `MesclarJoin` | r3, 1 w | **VIOLADA** | 5: `Logout`, `Block`, `Particionar`, `MesclarJoin` | 1.416 / 4.562 / 1.267 | `join-join-w1/j1-particao-r3-I4MpJ.out` |
| I2Fa | `Mesclar` (controle) | r3, 1 w | **VIOLADA** | 4: `Block`, `Particionar`, `Mesclar` | 363 / 1.096 / 325 | `join-ctrl-w1/c1-particao-r3-I2Fa.out` |
| I2FaJ | `MesclarJoin` | r3, 1 w | **VIOLADA** | 4: `Block`, `Particionar`, `MesclarJoin` | 363 / 1.096 / 325 | `join-join-w1/j1-particao-r3-I2FaJ.out` |
| MesclarPerdeChave | `Mesclar` (controle), livre | r3, 1 w | **VIOLADA** | 4: `Login`, `Particionar`, `Mesclar` | 185 / 531 / 168 | `join-ctrl-w1/c1-livre-particao-r3-MesclarPerdeChave.out` |
| MesclarPerdeChaveJ | `MesclarJoin`, livre | r3, 1 w, 300 s | sem violação até aqui | — | 2.232.602 / 11.791.854 / 1.837.128 | `join-join-w1/j1-livre-particao-r3-MesclarPerdeChaveJ.out` |
| MesclarPerdeChaveJ | `MesclarJoin`, livre | r3, 4 w, 600 s | sem violação até aqui | — | 13.021.520 / 75.802.141 / 10.418.159 | `join-join/j-livre-particao-r3-MesclarPerdeChaveJ.out` |
| MesclarPerdeChaveJ | `MesclarJoin`, livre | r2, `2/3/2`, 4 w, `-coverage 1` | **FECHOU** (438 s) | — | 5.210.736 / 101.971.159 / 0 (profundidade 17) | `join-join-red/jr-livre-particao-r2c2r3-MesclarPerdeChaveJ.out` |

`jx-particao-r3c2r3-I1F-I1MJ` está `PAUSADO` em `runs/tlc/join-estado.tsv` (`runs/tlc/join-PAUSAR` criado às 15:00, quando a A4
terminou e o B-45 pôde voltar; o limite é de 4 workers no total). O roteiro não usa `-recover`: para retomar, apagar
`join-PAUSAR` e rodar `spec/run-tlc-join.sh '^jx-particao-r3c2r3'`, que recomeça do zero com o timebox inteiro (2700 s).

As oito expectativas pré-registradas (E-J1 a E-J8) se confirmaram, sem divergência. O controle reproduz na revisão desta branch os
comprimentos do catálogo (4, 4, 4, 5, 4, 4).

### 5.2 Traços

Notação: `V/-@1` = `VALID`, motivo `NONE`, `ts=1`; `I/LO@2` = `INVALID/LOGOUT`, `ts=2`; `I/AB@2` = `INVALID/ADMIN_BLOCK`; `O@1`/`B@2` =
identidade `OPEN`/`BLOCKED`. Nos traços abaixo (3 réplicas) a partição é `{r1} | {r2, r3}` (r1 perde).

**I1M com `Mesclar` (controle, 4 estados).** `Logout(r1,s1)` deixa r1 com `s1 = I/LO@2`; `Particionar`; `Mesclar` copia o registro de
`rf` (ainda `V/-@1`) para r1: o logout confirmado volta a `VALID`. É o traço do catálogo §4.1.

**I1MJ com `MesclarJoin` invertida (4 estados).** Os mesmos três passos; com `VALID > INVALID` o máximo de `{I/LO@2, V/-@1, V/-@1}` é
`V/-@1`, e r1 volta a `VALID`. Com a ordem de conteúdo o máximo é `I/LO@2` e as três réplicas ficam `INVALID`.

**I4MJ/I4MpJ com `MesclarJoin` (5 estados).**

| Estado | Ação | r1 (`s1`, `s2`, `u1`) | r2 | r3 |
|---|---|---|---|---|
| 1 | inicial | `V/-@1`, `V/-@1`, `O@1` | igual | igual |
| 2 | `Logout(r1,s1)` | `I/LO@2`, `V/-@1`, `O@1` | igual a 1 | igual a 1 |
| 3 | `Block(r2,u1)` | inalterado | `I/AB@2`, `I/AB@2`, `B@2` | igual a 1 |
| 4 | `Particionar` | inalterado | inalterado | inalterado |
| 5 | `MesclarJoin` (`rf = r2`) | **`I/AB@2`**, `I/AB@2`, `B@2` | `I/AB@2`, `I/AB@2`, `B@2` | `I/AB@2`, `I/AB@2`, `O@1` |

`s1` em r1 continua `INVALID`, mas o motivo troca de `LOGOUT` para `ADMIN_BLOCK` (o máximo das cópias). As três réplicas convergem
para o mesmo registro de sessão; a troca é o preço de convergir num máximo.

**I2FaJ com `MesclarJoin` (4 estados).** `Block(r1,u1)` deixa r1 com `s1 = s2 = I/AB@2` e `u1 = B@2`; `Particionar`; `MesclarJoin`
com `rf` em `{r2, r3}` (ainda `O@1`): as sessões ficam `I/AB@2` nas três réplicas, mas a identidade de r1 volta a `O@1`. r2 e r3
ainda recebem o `ID_BLOQUEIA` do canal; r1 não recebe a própria mensagem e fica `OPEN`.

### 5.3 Não vacuidade

`-coverage` (formato do TLC `distintos:total` da ação):

| Execução | `MesclarJoin` | Evidência |
|---|---|---|
| `jr-particao-r2c2r3-I1MJ` (fechou) | 17.078 : 44.994 | linha 29 do `.out` |
| `j-particao-r3-I1MJ` (timebox; última estatística, 14:19:55) | 1.555.060 : 1.942.572 | linha 2113 do `.out` |
| `jr-livre-particao-r2c2r3-MesclarPerdeChaveJ` (fechou) | 931.453 : 2.667.430 | linha 2375 do `.out` |
| `jx-particao-r2-I1F-I1MJ` (timebox; última estatística, 14:50:44) | 3.707.341 : 6.020.872 | linha 4614 do `.out` |
| `jx-particao-r3c2r3-I1F-I1MJ` (pausado; estatística de 14:57:00) | 1.219.206 : 1.926.388 | linha 29 do `.out` |

O controle invertido viola em 4 estados nas duas cotas: a mesma propriedade, sobre a mesma ação, distingue as duas ordens.

### 5.4 Leitura

1. **A violação de I1 na fusão some com a ordem de conteúdo**, e o motivo é estrutural: `INVALID` é o maior estado, e o máximo de
   um conjunto que contém um `INVALID` é `INVALID`. O TLC confirma dentro das cotas: fechou com 2 réplicas, e com 3 réplicas não
   achou violação nem nas cotas do catálogo (20,5 M distintos em 600 s) nem com `2/3/2` (8,9 M distintos em 516 s, pausado). Na mesma revisão, o `Mesclar` publicado viola em 4 estados, e a ordem invertida também.
2. **`I1F` no semeado também fica sem violação**, porque no semeado nenhum outro passo tira uma sessão de `INVALID`. No modelo
   livre `I1F` continua violada sem falha pelo artefato do relógio (P-26, catálogo §5); isso não foi rodado aqui.
3. **`MesclarPerdeChave` deixa de ocorrer:** `NONE` é o menor posto, então uma cópia existente nunca perde para uma ausente.
4. **I4 na fusão continua violada, por outro mecanismo.** A reversão (4 estados) some; resta a troca de motivo `LOGOUT` →
   `ADMIN_BLOCK` quando os dois lados invalidaram a mesma sessão por motivos diferentes (5 estados). Toda fusão que converge para um
   único registro troca o motivo em alguma réplica nesse caso; a ordem só decide qual motivo fica.
5. **I2Fa continua violada como antes**, porque as identidades ficam com a cópia de `rf`. A junção das sessões preserva a cascata do
   bloqueio (as sessões ficam `INVALID`), mas não o `BLOCKED` da identidade no lado perdedor.

### 5.5 Limites

- Vale para o **modelo**. A ordem do *cluster* (A1) é a mesma; o caminho real (CR do Infinispan, `counters-async` com
  `PREFERRED_NON_NULL`) é o que a A1 vai medir.
- Com 3 réplicas nas cotas do catálogo nada fechou (`SEM_VIOLACAO_ATE_AQUI`): ausência de violação ali é "até aqui", não
  preservação. Os fechamentos são com 2 réplicas e `2/3/2`. Com 2 réplicas e `3/5/2` não fechou em 1200 s
  (25,8 M distintos, fila 15,1 M); com 3 réplicas e `2/3/2` foi pausado aos 516 s (8,9 M distintos, fila 5,2 M) para liberar os
  4 workers ao B-45, sem violação até ali. A fila crescia ~0,5 M por minuto; o fechamento ali passa de 45 min.
- `MesclarPerdeChaveJ` e o fecho do livre são com `IDS={u1}`, como no catálogo.
- O modelo guarda no canal as mensagens do lado minoritário durante a partição; o *cluster* não. Isso não muda os vereditos da fusão
  (a fusão não lê o canal), mas muda o que acontece depois dela.
- A A1 não reduz a exposição na janela (leitura de `VALID` na maioria antes da fusão); `MesclarJoin` também não a modela.

## 6. B-45: retomada

Worktree `wt-b45`, branch `fix/b45-desempate-ts`, roteiro `spec/run-tlc-b45.sh` (sem mudança), revisões de `runs/tlc/b45-revs.tsv`
(ANTES `20805b0`, DEPOIS `a2ce4c9`, FIFO `8f25803`).

- **Estado ao pegar a tarefa.** O commit `0b6f41a` tem 84 FEITO e 1 PAUSADO. No disco (não commitado) havia **141 FEITO e
  1 PAUSADO** (`d-sem-sem-r3-I6`, 540 s acumulados): a rodada seguiu depois daquele commit até 05/11 02:54, quando foi pausada.
  Faltavam 55 linhas e a pausada, com timebox somado de até 35.220 s (muitas violam em segundos).
- **Espera pela A4.** Às 13:30 a `wt-a4` não tinha relatório; às 14:37 apareceu `docs/relatorio-a4-2026-11-06.md`, ainda com
  marcadores `@@` e com a carga e as sondas da A4 rodando. Considerei a A4 terminada às 15:00 (relatório sem marcadores e nenhum
  processo java da `wt-a4` em duas checagens seguidas). Até lá só rodou a tarefa 1; o `jx-particao-r3c2r3` foi pausado nesse
  momento para não passar de 4 workers.
- **Retomada, 15:01:43.** `rm runs/tlc/b45-PAUSAR` e `CHECKPOINT_MIN=5 spec/run-tlc-b45.sh`, log em
  `runs/tlc/b45-retomada-2026-11-06.log` (ignorado pelo git). `d-sem-sem-r3-I6` voltou do *checkpoint* de 05/11 02:50 (10.628.511
  estados) com saldo de 60 s e terminou `SEM_VIOLACAO_ATE_AQUI` com 10.729.172 distintos. Antes da pausa de 05/11 ela tinha chegado
  a 15.808.266; o trabalho depois do último *checkpoint* se perde, como o roteiro documenta (o timebox efetivo fica menor).
- **Pausa de propósito e relançamento, 15:09 a 15:10.** Pausei logo depois do *checkpoint* de 15:08:14 de `d-sem-queda-r3-I5`
  (PAUSADO aos 366 s) para commitar os brutos com nada sendo escrito: **`ade41c7`** em `fix/b45-desempate-ts` (142 FEITO,
  1 PAUSADO, 54 pendentes; inclui os brutos de 04-05/11 que só estavam no disco). Em seguida relancei o roteiro fora desta sessão
  (`spec/states/b45/retomar-b45.sh`, ignorado pelo git, via `Start-Process`), com `CHECKPOINT_MIN=5` e o mesmo log. Às 15:10 ele
  retomou `d-sem-queda-r3-I5` do *checkpoint* (saldo 234 s) e segue sozinho, na ordem do plano, com 4 workers.
- **Na entrega deste relatório:** 142 de 197 FEITO; em curso `d-sem-queda-r3-I5`; faltam 54. Teto dos timeboxes restantes: 34.254 s
  (~9,5 h). Pelas expectativas E-4, E-5 e E-8 do B-45, a maior parte das linhas de partição e do FIFO viola em segundos; estimo 2 a
  3 h, dominadas pelas `prop41` (até 9.600 s de timebox) e pelas colunas sem falha e queda (600 s cada). Os brutos que vierem depois
  de `ade41c7` ficam no disco do `wt-b45` até alguém commitar no fim da rodada.

Para pausar: `touch wt-b45/runs/tlc/b45-PAUSAR` (a execução corrente vira PAUSADO e retoma do último *checkpoint*, de no máximo
5 min antes). Para retomar: apagar o arquivo e rodar de novo `CHECKPOINT_MIN=5 spec/run-tlc-b45.sh` em `wt-b45`.

## 7. Pontos para validação

1. **D-J1 (escopo da fusão).** Juntar as cópias de todas as réplicas e escrever em todas, como a CR. A alternativa mínima (só `rf`
   com o perdedor) não muda I1MJ (o máximo continua `INVALID`), mas deixaria o vencedor sem o logout do perdedor.
2. **D-J2 (relógio do perdedor = maior entre o seu e o de `rf`).** Copiar o de `rf`, como em `Mesclar`, faria o relógio cair abaixo
   de um registro que o perdedor ainda guarda. No semeado a escolha não muda nenhum veredito desta rodada (as sessões que o perdedor
   mantém são `INVALID`, sem escrita local depois).
3. **Leitura de I4 com a A1.** A troca `LOGOUT` → `ADMIN_BLOCK` (5 estados) é inevitável em qualquer fusão que converge para um
   registro: a ordem só escolhe qual motivo fica. Proposta: no experimento da A1, ler I4 como "motivo único depois da fusão" (as
   réplicas concordam), e registrar a troca como efeito esperado da política, não como violação. A leitura por réplica ("motivo
   confirmado nunca muda") segue `VIOLADA-POR-CONSTRUCAO`, como I4Fp sem falha.
4. **Identidades (D-2).** `I2FaJ` mostra que a junção das sessões preserva a cascata do bloqueio, mas a identidade volta a `OPEN`
   no lado perdedor. Uma segunda variante com fusão própria das identidades (por exemplo `BLOCKED > OPEN` e contador pelo máximo)
   ficou fora desta tarefa ("identidades e contadores ficam com a fusão atual"). Modelar só se a D-2 for nessa direção.
5. **Fechamento com 3 réplicas.** `jx-particao-r3c2r3` foi pausado aos 516 s; fechar ali passa de 45 min com 4 workers. Rodar
   depois do B-45, se o texto precisar de "preservada até as cotas" com 3 réplicas (hoje: fechou com 2, "até aqui" com 3).
6. **Números provisórios** nos documentos: P-61 em `docs/registro-de-problemas.md` e TD-020 em `docs/implementacao.md` (há
   P-60 e TD-019 em outras branches). **Na integração ao `main` (11/11) viraram P-66 e TD-024.**
7. **Frase possível para o texto (conferir contra os `.out` antes de publicar):** "Numa variante do modelo em que a fusão das
   sessões é o máximo na ordem de conteúdo, o TLC não encontrou violação de I1 nos passos de fusão nem de I1F sob partição: a
   busca fechou com 2 réplicas (79.224 estados distintos) e, com 3 réplicas, passou de 20 milhões de estados sem violação. Na
   mesma revisão, o modelo publicado e a variante com a ordem invertida violam em 4 estados."
