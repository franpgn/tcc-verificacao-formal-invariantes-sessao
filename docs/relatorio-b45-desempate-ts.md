# Relatório B-45: desempate de `ts` no modelo TLA+ (P-16) e nova rodada do TLC

- Branch: `fix/b45-desempate-ts`, criada de `feature/controlador-integracao` em `20805b0`. Sem push.
- Tarefa: plano `docs/plano-alinhamento-objetivos-2026-11-04.md` (branch `docs/plano-implementacao-controlador`), §9, Prompt 3.
- Decisão D-8 do autor (04/11): variante **T2**, desempate pelo conteúdo do registro.
- Restrições de host: no máximo 4 workers do TLC; `tiny` e `small` antes das maiores; `-checkpoint 10` e retomada por `-recover`;
  o TLC não roda durante janela de teste na VM (pausa por `runs/tlc/b45-PAUSAR`).

## 1. Leitura do defeito (Tarefa 0, sem execução)

Traço de referência: `wt-b35/runs/tlc/perda-base-controle/semeado-sem/r2-I5.out` (branch `feature/b35-spec-perda-canal`, `640614d`;
SHA-256 `91e70b3b7749162b15b183f0113f6c7b9b663885baf7cbe13c4cf7d9c2209588`). Cenário semeado, `REPLICAS={r1,r2}`, `IDS={u1}`,
`SIDS={s1,s2}`, `LIMITE=2`, `MaxCanal=2`, `MaxRelogio=3`, `MaxEventosFalha=2`, restrição `SemFalha`, 2 workers. As linhas citadas
abaixo são de `spec/SessionStore.tla` em `20805b0` (as mesmas do traço, que cita `Logout line 313` e `EntregaMensagem line 536`).

Notação: `V/-@1` = `VALID`, motivo `NONE`, `ts=1`; `I/LO@2` = `INVALID/LOGOUT`, `ts=2`; `I/AB@2` = `INVALID/ADMIN_BLOCK`, `ts=2`.

| Estado | Ação | r1 (`s1`, `s2`, `u1`, relógio) | r2 (`s1`, `s2`, `u1`, relógio) | Canais |
|---|---|---|---|---|
| 1 | inicial (semeado) | `V/-@1`, `V/-@1`, `OPEN@1`, 1 | `V/-@1`, `V/-@1`, `OPEN@1`, 1 | vazios |
| 2 | `Logout(r1,s1)`, `novoTS = relogio[r1]+1 = 2` (l. 307) | `I/LO@2`, `V/-@1`, `OPEN@1`, 2 | inalterado | r2: `SESSAO_INVALIDA s1 I/LO@2` |
| 3 | `Block(r2,u1)`, `novoTS = relogio[r2]+1 = 2` (l. 380), afetadas = {s1,s2} | inalterado | `I/AB@2`, `I/AB@2`, `BLOCKED@2`, 2 | r1: `ID_BLOQUEIA u1 I/AB@2 {s1,s2}` |
| 4 | `EntregaMensagem(r1)` do `ID_BLOQUEIA` | `I/LO@2`, `I/AB@2`, `BLOCKED@2` | inalterado | r1 vazio |
| 5 | `EntregaMensagem(r2)` do `SESSAO_INVALIDA` | inalterado | inalterado | vazios: `Quiescent`, I5 falsa em `s1` |

Onde o empate descarta a mensagem:

- **Estado 4, l. 571** (`m.sessao.ts > @[s].ts` na cascata do `ID_BLOQUEIA`): para `s2`, `2 > 1` aplica; para `s1`, `2 > 2` é falso e
  r1 mantém `I/LO@2`.
- **Estado 5, l. 548** (`m.sessao.ts > sessoes[r][m.chave].ts` em `SESSAO_INVALIDA`): `2 > 2` é falso e r2 mantém `I/AB@2`.

O `novoTS` das seis ações locais (l. 286, 307, 334, 356, 380, 409) é `relogio[r] + 1`, um contador por réplica que `EntregaMensagem`
não atualiza (l. 541). Duas réplicas com o mesmo relógio produzem o mesmo `ts` para escritas concorrentes na mesma chave. Com o gate
"estritamente maior" nos dois sentidos, nenhuma das duas troca o registro, e as filas esvaziam com as réplicas diferentes. Os gates
locais (l. 295, 316, 344, 364, 392, 396, 417) usam a mesma comparação, e o empate também pode acontecer ali (registro recebido com
`ts = relogio[r] + 1`).

Os 8 traços mínimos de I5/I6 citados em `Desenvolvimento.tex:60` são os de `runs/tlc/inv-{tiny,small,medium,full}/*-I5.out` e
`*-I6.out` (20 workers). Pela lista de ações de cada traço:

| Traço | Ações | Mecanismo |
|---|---|---|
| `tiny-I5` (5) | Login, Particionar, Mesclar, Entrega | fusão |
| `tiny-I6` (6) | Block, Particionar, IncrementFailure, Entrega, Entrega | empate (há partição, não há `Mesclar`) |
| `small-I5` (5) | Login, Login, Entrega, Entrega | empate (dois `Login` na mesma sid) |
| `small-I6` (7) | Login, Entrega, IncrementFailure, Particionar, Mesclar, Entrega | fusão |
| `medium-I5` (5) | Login, Login, Entrega, Entrega | empate |
| `medium-I6` (5) | IncrementFailure, Block, Entrega, Entrega | empate |
| `full-I5` (7) | Login, Entrega, Login, Entrega, Entrega, Entrega | empate |
| `full-I6` (6) | IncrementFailure, Particionar, Entrega, Mesclar, Entrega | fusão |

Ressalva: `runs/tlc/inv-full/` é de `dc64154`, anterior a `particoes` (P-25, `b7d8e0a`). Por isso o ANTES é rodado de novo aqui, na
especificação de `20805b0`, com as mesmas cotas e 4 workers.

## 2. Variante T2 implementada

Uma ordem total estrita sobre o registro, a mesma em todos os gates (6 ações locais e `EntregaMensagem`):

- `SessaoRec` vira a tupla `<< ts, estado, motivo, identidade >>`, com `NONE < VALID < INVALID`,
  `NONE < LOGOUT < EXPIRADA < ADMIN_BLOCK` e `identidade` pela posição fixa em `IDS` (`"NONE"` = 0).
- `IdentRec` vira `<< ts, status, contador >>`, com `OPEN < BLOCKED`.
- O candidato vence se a sua tupla é lexicograficamente maior que a do registro local (`VenceSessao`, `VenceIdent`).

Como `ts` é o primeiro componente, toda comparação com `ts` diferente dá o mesmo resultado de antes; só o empate muda. A tupla contém
todos os campos, então dois registros diferentes nunca empatam e o vencedor não depende da réplica de origem nem da ordem de entrega.
Os sentidos foram escolhidos de modo conservador: com `ts` igual, invalidação vence validação, `ADMIN_BLOCK` vence `LOGOUT` e bloqueio
vence desbloqueio.

**Desvio em relação ao texto do plano (para validação):** o plano diz que T2 "preserva a simetria sobre `REPLICAS`". Preserva. Mas
ordenar `identidade` exige ordenar os valores de modelo de `IDS` (dois `Login` concorrentes na mesma sid com ids diferentes e o mesmo
`ts` só se desempatam por essa ordem), e isso quebra a simetria sobre `IDS`. `Simetria` em `MCSession.tla` passa a ser
`Permutations(SIDS) ∪ Permutations(REPLICAS)`. Com `|IDS| = 1` (`tiny` e todo o cenário semeado) o grupo é o mesmo de antes; em
`small`, `medium` e `full` o grupo encolhe e os estados distintos do ANTES e do DEPOIS deixam de ser comparáveis diretamente.

## 3. Controle FIFO (N-4)

`spec/MCFifo.tla`: cópia mínima da especificação **sem** desempate (`20805b0`, comentários retirados por script), com `origem` em cada
mensagem e `EntregaMensagem(r)` restrita a mensagens sem outra anterior da mesma origem na fila de `r` (FIFO por par origem-destino;
com 2 réplicas, FIFO total da fila). Mais o harness de `MCSession.tla` (limites, simetria original, cenário semeado, colunas de falha).
Roda só I5/I6, em `tiny`, `small` e no semeado.

## 4. Plano de execução

`spec/b45-plano.tsv` (uma execução por linha, na ordem) e `spec/run-tlc-b45.sh` (retomável). Revisões: ANTES = `20805b0`,
DEPOIS = commit `fix:` desta branch, FIFO = commit `test:` que introduz `MCFifo.tla`. Brutos em `runs/tlc/b45-<grupo>/`.

| Grupo | O que | Cotas |
|---|---|---|
| `inv` | I1..I6 (e TypeOK em `tiny`) no modelo livre `Spec`, ANTES e DEPOIS; I5/I6 no FIFO | as de `runs/tlc/inv-*`: `MaxCanal=1`, `MaxRelogio=3`, `MaxEventosFalha=1`; 4 workers, 600 s |
| `inv-w1` | I1, I2, I5, I6 com 1 worker (tamanho mínimo do traço) | idem, 1 worker |
| `iso` | I5/I6 no `Spec` restrito a `SemFalha` e `SoQueda` (sem fusão possível) | idem, `tiny` e `small` |
| `semeado`, `semeado-w1` | I5/I6 no semeado, colunas sem falha, queda e partição | catálogo: `LIMITE=2`, `3/5/2`, r2 e r3 |
| `prop`, `prop-w1`, `prop41` | `I1F I2Fa I2Fb I4F I4Fp I1M I4M I4Mp I1P MesclarPerdeChave`, só DEPOIS (o ANTES é `runs/tlc/prop-*`, mesma especificação) | catálogo §3 (mesmos timeboxes) e §4.1 (cotas reduzidas) |

Controle sem simetria com 1 worker (protocolo do plano de 15/10 §4.1): para todo traço de violação que não existia no ANTES,
acrescentado ao plano depois da primeira passada.

## 5. Expectativas pré-registradas

Escritas em 04/11, antes de qualquer execução do TLC desta branch (nem ANTES, nem DEPOIS, nem FIFO). Qualquer divergência é resultado,
não erro a corrigir.

- **E-1 (traços de empate, `inv`).** Em `small-I5`, `medium-I5`, `full-I5`, `tiny-I6` e `medium-I6`, o traço de empate do ANTES deixa de
  ser contraexemplo. I5/I6 continuam VIOLADAS nessas cinco configurações, porque o `Spec` admite uma partição
  (`MaxEventosFalha=1`), e todo traço DEPOIS passa por `Mesclar`. Com 1 worker, o traço DEPOIS tem comprimento maior ou igual ao do
  ANTES.
- **E-2 (traços de fusão, `inv`).** `tiny-I5`, `small-I6` e `full-I6` continuam VIOLADAS DEPOIS, com `Mesclar` no traço e o mesmo
  comprimento de traço do ANTES com 1 worker.
- **E-3 (isolamento, `iso`).** Sem fusão possível (`SemFalha`, `SoQueda`): ANTES, I5 e I6 VIOLADAS em `tiny` e `small`. DEPOIS, sem
  violação: `tiny` FECHOU; `small` FECHOU ou `SEM_VIOLACAO_ATE_AQUI`.
- **E-4 (semeado).** ANTES, I5/I6 VIOLADAS nas três colunas, em r2 e r3; em r2, I5 sem falha com 1 worker dá o traço de 5 estados da
  §1. DEPOIS, sem falha e queda sem violação (FECHOU ou `SEM_VIOLACAO_ATE_AQUI`); partição VIOLADA, com `Mesclar` no traço.
- **E-5 (FIFO, N-4).** O canal FIFO não elimina o empate: `f-iso-*-sem` e `f-sem-sem-*` VIOLADAS (sem fusão possível, logo pelo
  empate); em r2, I5 com 1 worker dá um traço de 5 estados `Logout`, `Block` e duas entregas. Se alguma dessas células não violar, N-4
  está refutada nela.
- **E-6 (I1, I2).** VIOLADAS DEPOIS nas quatro configurações, com o mesmo comprimento de traço do ANTES com 1 worker (os traços mínimos
  não passam por empate).
- **E-7 (TypeOK, I3, I4).** Sem violação DEPOIS. `tiny` FECHOU, com número de estados distintos diferente do ANTES (sentido não
  previsto: há estados novos, onde o empate agora aplica o registro, e estados que somem). `small`, `medium` e `full`:
  `SEM_VIOLACAO_ATE_AQUI` em 600 s, sem comparação de distintos por causa da simetria (§2).
- **E-8 (propriedades de ação, semeado).** Nenhuma propriedade antes preservada passa a violar: I1F sem falha r2 `3/5/2` e as cotas
  reduzidas da §4.1 FECHAM; I1P e I2Fa, I1M, I4M, I4Mp nas colunas sem falha e queda seguem sem violação. Na partição, I1F, I2Fa, I1M,
  I4M, I4Mp, `MesclarPerdeChave` seguem VIOLADAS com o mesmo comprimento com 1 worker (4, 4, 4, 4, 5 e 4 estados). I4F, I4Fp e I2Fb
  seguem VIOLADAS nas três colunas.
- **E-9 (`I4Fp` sem falha).** Continua violada. Expectativa específica: o traço com 1 worker **encurta** de 5 para 4 estados, porque
  o empate agora troca o motivo: `Logout(r1,s1)` e `Block(r2,u1)` com `ts=2`, e a entrega do `ID_BLOQUEIA` em r1 troca `I/LO@2` por
  `I/AB@2` (`ADMIN_BLOCK` vence). Toda resolução convergente de um empate entre motivos diferentes troca o motivo em alguma réplica,
  então I4Fp não pode ser corrigida pelo desempate; ela depende da ordem de chegada, não da convergência. Pelo mesmo traço, I4F sem
  falha e queda também encurta de 5 para 4 estados com 1 worker.

## 6. Resultados

(preenchido depois da rodada)
