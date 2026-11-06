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

(preenchido depois da rodada)

## 6. B-45: retomada

(preenchido depois)

## 7. Pontos para validação

(preenchido depois)
