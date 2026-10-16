# Catálogo operação × falha × invariante (documento vivo)

> Criado em 2026-10-15 pela tarefa T-A (`test/b32-auditoria-controle`, base `48d1571`), por decisão do Gestor de **não editar a §3.2**
> do plano do controlador (`docs/plano-controlador-heuristico-2026-09-24.md`, branch `docs/plano-controlador-heuristico`). Este
> arquivo é a versão viva do catálogo: cada linha só entra com veredito que passou pelo protocolo de autenticidade
> (`docs/plano-fechamento-etapa-formal-2026-10-15.md` §4.1) e por A.1 a A.6 de T-A. Auditoria completa:
> `docs/relatorio-b32-auditoria-2026-10-15.md`. **Todos os status abaixo são provisórios até a validação V-A do Senior** (leitura de
> cada traço, P-7); onde aparece `CONFIRMADO-NO-MODELO`, falta apenas essa leitura humana.

## 1. Como ler

**Status (vocabulário fechado, plano §4.2):** `CONFIRMADO-NO-MODELO` (traço reproduzido sem simetria, aguardando leitura do Senior) ·
`PRESERVADA-ATE-COTAS` (fechou exaustivo, cotas na mesma frase) · `SEM-VIOLACAO-ATE-AQUI` (timeout; **não** vale como preservada) ·
`VACUA` (vacuidade verificada por cobertura) · `VIOLADA-POR-CONSTRUCAO` (viola sem falha; não discrimina a falha) ·
`ARTEFATO-DO-MODELO` · `OBSERVADO-NO-REAL` / `NAO-OBSERVADO-EM-n` · `PENDENTE` · `INVALIDADO`.

**Marca (pedida pelo Gestor):** `confirmado` (traço de violação reproduzido) · `não-fechado` (busca não terminou; nada a afirmar além do
"até aqui") · `vácuo-estrutural` (a propriedade só restringe passos `Mesclar`, que é inalcançável por construção na coluna; é
argumento, não verificação) · `vácuo-verificado` (idem, com contagem da ação `Mesclar` = 0 por `-coverage` em configuração que fecha). Também
aparecem `violada-por-construção` e `artefato-do-modelo`, que não são vereditos de falha.

**Cenário e cotas padrão (as do plano B-32, salvo quando a célula disser outra coisa):** cenário **semeado** (`SpecSemeada`, `IDS={u1}`,
`SIDS={s1,s2}`, todas as réplicas com `s1` e `s2` `VALID`, `ts=1`), 3 réplicas, `MaxCanal=3`, `MaxRelogio=5`, `MaxEventosFalha=2`,
`LIMITE=2`, redução por simetria (`Permutations(IDS) ∪ Permutations(SIDS) ∪ Permutations(REPLICAS)`), 4 workers para os números de
estados; tamanhos de traço de **1 worker**. "sem falha" = `quedas+particoes=0` (atraso do canal incluído); "queda" = `particoes=0`;
"partição" = `quedas=0`. "Traço de N estados" inclui o estado inicial (N-1 passos).

**O que o TLC aqui é:** verificação do **modelo** `spec/SessionStore.tla`. Nenhuma célula desta tabela é evidência do Infinispan real:
todas as colunas "real" ficam `PENDENTE` até B-34.

## 2. Linhas de origem (§3.2 do plano do controlador, sem edição)

| Falha | Invariante | Operações candidatas | Observável no real (proposto na §3.2) | Real (F1/F3 pendentes; F2 sem injetor validado) |
|---|---|---|---|---|
| F1 (queda) | I1 | O3 (Logout) seguido de O1/leitura em réplica que não recebeu `SESSAO_INVALIDA` | `Validate`/`Login` retorna `VALID` para sid com `Logout` confirmado na janela de queda | `PENDENTE` |
| F1 (queda) | I2 | O6 (Block) concorrente com O1/O2 em réplica que cai antes do `ID_BLOQUEIA` | `Login`/`Validate` bem-sucedido para identidade bloqueada em outra réplica | `PENDENTE` |
| F1 (queda) | I4 | O3/O6 concorrentes atravessando a queda | mesma sid com motivo diferente antes/depois da recuperação | `PENDENTE` |
| F2 (partição) | I1/I2/I4 | O1/O3/O6 na réplica que fica minoritária pouco antes da partição, leitura após o `Mesclar` | leitura diverge do que o cliente já havia recebido como confirmado | `PENDENTE` (B-34) |
| F3 (jitter) | I1/I2/I4 | qualquer operação cuja mensagem seja atrasada a ponto de inverter a ordem | correlação p99 elevado × evento pontual na mesma janela | `PENDENTE` (latência já computável, Achado C) |

## 3. Matriz de vereditos do TLC (semeado, cotas do plano; célula = marca · status · traço)

| Propriedade (tipo) | Sem falha (atraso) | Só queda | Só partição |
|---|---|---|---|
| **I1F** (ação) | **não-fechado** · `SEM-VIOLACAO-ATE-AQUI` (601 s, 18.273.465 distintos, 12.793.143 na fila). Fechou só em cotas menores: ver 4.1 | **não-fechado** · `SEM-VIOLACAO-ATE-AQUI` (601 s, 22.377.840 distintos, 16.358.832 na fila). Fechou só em cotas menores: ver 4.1 | **confirmado** · `CONFIRMADO-NO-MODELO` · 4 estados |
| **I2Fa** (ação, só `Mesclar`) | **vácuo-verificado** · `VACUA` (`Mesclar` 0:0 em r2 e r3, `MaxCanal=2`, `MaxRelogio=3`; na cota do plano o argumento é estrutural, o TLC não fechou) | **vácuo-verificado** · `VACUA` (`Mesclar` 0:0 em r2, `MaxCanal=2`, `MaxRelogio=3`; na cota do plano, estrutural) | **confirmado** · `CONFIRMADO-NO-MODELO` · 4 estados |
| **I2Fb** (invariante) | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 3 estados (mecanismo a); `LIMITE=6`: 4 estados (mecanismo b) | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 3 (a); `LIMITE=6`: 4 (b) | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 3 (a); `LIMITE=6`: 4 (b) |
| **I4F** (ação) | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 5 estados | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 5 estados | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 4 estados (reversão do I1F; já violada sem falha) |
| **I4Fp** (ação, "pura") | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 5 estados | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 5 estados | **violada-por-construção** · `VIOLADA-POR-CONSTRUCAO` · 5 estados (o mesmo traço sem falha; não é efeito da partição) |
| **I1M** (ação, só `Mesclar`) | **vácuo-verificado** · `VACUA` (idem I2Fa) | **vácuo-verificado** · `VACUA` (idem I2Fa) | **confirmado** · `CONFIRMADO-NO-MODELO` · 4 estados |
| **I4M** (ação, só `Mesclar`) | **vácuo-verificado** · `VACUA` (idem I2Fa) | **vácuo-verificado** · `VACUA` (idem I2Fa) | **confirmado** · `CONFIRMADO-NO-MODELO` · 4 estados |
| **I4Mp** (ação, só `Mesclar`) | **vácuo-verificado** · `VACUA` (idem I2Fa) | **vácuo-verificado** · `VACUA` (idem I2Fa) | **confirmado** · `CONFIRMADO-NO-MODELO` · 5 estados; ressalva em 4.4: o passo violador é `Mesclar`, mas a pré-condição existe sem falha |
| **I1P** (ação, `Logout` efetivo) | **fechou nas cotas reduzidas** · `PRESERVADA-ATE-COTAS` (r3, `MaxCanal=2`, `MaxRelogio=3`, `MaxEventosFalha=2`: 1.627.731 distintos, fechou; sem simetria também fechou: 17.315.129 distintos) | **não-fechado** · `SEM-VIOLACAO-ATE-AQUI` (120 s, 1.972.331 distintos) | **não-fechado** · `SEM-VIOLACAO-ATE-AQUI` (r3, `MaxCanal=2`, `MaxRelogio=3`: 600 s, 33.038.740 distintos, 21.076.524 na fila) |

Fontes e comandos de cada célula: seção 4. Linhas que dependem do artefato do relógio (P-26) estão separadas na seção 5.

## 4. Detalhe por propriedade

### 4.1 I1F e a irreversibilidade (O3 `Logout`)

- **Propriedade operacional (o que o auditor real checa):** réplica com a sessão `INVALID` nunca a volta a ter em outro estado
  (`I1F == [][\A r,s : estado=INVALID => estado'=INVALID]_vars`).
- **Partição, `CONFIRMADO-NO-MODELO`, 4 estados** (`runs/tlc/prop-particao-w1/r3-I1F.out`; sem simetria
  `runs/tlc/audit-b32/ctrl-nosym-particao/r3-I1F.out`, 4 estados; traço anotado em
  `runs/tlc/audit-b32/tracos/traco-prop-particao-w1-r3-I1F.md`): `Logout(r1,s1)`, `Particionar {r1}|{r2,r3}`, `Mesclar` com `rf` do lado
  vencedor ainda `VALID`: o logout confirmado em r1 é revertido. **Independente do artefato D-4.** `I1M` e `I4M` têm o mesmo traço
  (a violação é do passo `Mesclar`).
- **Sem falha e queda: preservada só dentro das cotas abaixo; nas cotas do plano é `SEM-VIOLACAO-ATE-AQUI`.**

| Coluna | Cotas | Resultado | Distintos (gerados) | Evidência | Controle sem simetria (1 worker) |
|---|---|---|---|---|---|
| sem falha | r2, `MaxCanal=3`, `MaxRelogio=5`, ev=2 | `PRESERVADA-ATE-COTAS` | 2.968.799 (63.508.237) | `runs/tlc/prop-sem/r2-I1F.out` | fechou: 11.115.255 distintos (237.340.561 gerados), 2011 s, `runs/tlc/audit-b32/ctrl-nosym-sem-r2/r2-I1F.out`; razão 3,74 |
| sem falha | r3, `MaxCanal=2`, `MaxRelogio=3`, ev=2 | `PRESERVADA-ATE-COTAS` | 1.627.731 (53.541.786) | `runs/tlc/prop-sem-c2r3/r3-I1F.out` | fechou: 17.315.129 distintos (569.296.936 gerados), 3854 s, `runs/tlc/audit-b32/ctrl-nosym-sem-c2r3-I1F/r3-I1F.out`; razão 10,64 |
| sem falha | r3, `3/5/2` (plano) | `SEM-VIOLACAO-ATE-AQUI` (601 s) | 18.273.465 (222.726.728), fila 12.793.143 | `runs/tlc/prop-sem/r3-I1F.out` | não rodado (não fecha) |
| queda | r2, `MaxCanal=2`, `MaxRelogio=3`, ev=2 | `PRESERVADA-ATE-COTAS` | 15.690 (195.239) | `runs/tlc/prop-queda-c2r3/r2-I1F.out` | fechou: 51.840 distintos (641.661 gerados) sem simetria, `runs/tlc/audit-b32/ctrl-nosym-queda-c2r3/r2-I1F.out`; razão 3,30 |
| queda | r3, `MaxCanal=2`, `MaxRelogio=3`, ev=1 | `PRESERVADA-ATE-COTAS` (sob simetria) | 8.037.698 (197.610.219) | `runs/tlc/prop-queda-c2r3-ev1/r3-I1F.out` | sem controle sem simetria (até 12× os estados; não cabe no orçamento) |
| queda | r3, `MaxCanal=2`, `MaxRelogio=3`, ev=2 | `PRESERVADA-ATE-COTAS` (sob simetria) | 19.229.901 (394.154.264), 8 workers, 624 s | `runs/tlc/prop-queda-c2r3-ev2-longo/r3-I1F.out` | sem controle sem simetria (idem) |
| queda | r3, `MaxCanal=2`, `MaxRelogio=3`, ev=2, 4 workers, 600 s | `SEM-VIOLACAO-ATE-AQUI` (a execução curta que antecedeu a longa; não citada no relatório B-32) | 13.297.388 (208.052.900), fila 3.531.514 | `runs/tlc/prop-queda-c2r3/r3-I1F.out` | n/a |
| queda | r2, `3/5/2` (plano) | `SEM-VIOLACAO-ATE-AQUI` (604 s) | 21.725.102, fila 2.989.734 | `runs/tlc/prop-queda/r2-I1F.out` | n/a |
| queda | r3, `3/5/2` (plano) | `SEM-VIOLACAO-ATE-AQUI` (601 s) | 22.377.840, fila 16.358.832 | `runs/tlc/prop-queda/r3-I1F.out` | n/a |

Leitura honesta: "preservada **até** r3, `MaxCanal=2`, `MaxRelogio=3` e até 2 quedas (sob simetria)" e "até r3 com `MaxCanal=2`,
`MaxRelogio=3`, ou r2 com `3/5/2`, sem falha". Nunca prova geral. Não existe fechamento de I1F, sem falha ou queda, nas cotas do
plano com 3 réplicas.

### 4.2 I2 (bloqueio): I2Fa isola a partição; I2Fb é violada por construção

- **I2Fa** (`[][Mesclar => BLOCKED continua BLOCKED]_vars`): `CONFIRMADO-NO-MODELO` sob partição, 4 estados
  (`runs/tlc/prop-particao-w1/r3-I2Fa.out`; sem simetria `ctrl-nosym-particao/r3-I2Fa.out`, 4 estados): `Block(r1,u1)`,
  `Particionar`, `Mesclar` com `rf` que ainda não recebeu o `ID_BLOQUEIA`: r1 volta a `OPEN` e as sessões voltam a `VALID`. Independente de D-4.
  Sem falha e queda: ver seção 6 (vácuo).
- **I2Fb** (invariante: `BLOCKED` na réplica ⇒ nenhuma sessão `VALID` da identidade na mesma réplica): **`VIOLADA-POR-CONSTRUCAO`**
  nas três colunas (decisão D1 do Gestor, 2026-10-15; P-27). Não discrimina a coluna de falha. Dois mecanismos, ambos sem falha:
  - **(a) auto-bloqueio por limite sem cascata** (`comportamento do código`). `IncrementFailure` ×2 com `LIMITE=2`: 3 estados
    (`runs/tlc/prop-{sem,queda,particao}-w1/r3-I2Fb.out`; sem simetria `runs/tlc/audit-b32/ctrl-nosym-{sem,queda,particao}/r3-I2Fb.out`,
    3 estados). O código real faz o mesmo: `SessionOps.incrementFailure` só grava a identidade; só `block()` varre `sessions`.
  - **(b) cascata `sessoesAfetadas` calculada na origem** (`artefato do modelo`). `LIMITE=6`: `Logout(r1,s1)`, `Block(r1,u1)`
    (afetadas = {s2}), entrega do `ID_BLOQUEIA` a r2 que ainda vê `s1` `VALID`: 4 estados
    (`runs/tlc/prop-{sem,queda,particao}-lim6-w1/r3-I2Fb.out`; sem simetria `ctrl-nosym-lim6-*`, 4 estados).
- **Implicação para o auditor real (B-33):** uma varredura global de I2 acusará violação em toda execução com bloqueio por limite.
  Separar bloqueio por limite de bloqueio administrativo (`IdentityState` hoje não guarda o motivo do bloqueio). Pergunta Q-3 ao Gestor:
  o auto-bloqueio sem cascata é intencional?

### 4.3 I4 (motivo único) e a decisão sobre `I4Fp`/`I4Mp`

- **I4F** (`INVALID ⇒ motivo' = motivo`) dispara também quando a sessão **volta** a `VALID` (o motivo vira `NONE`), então seu traço mínimo
  sob partição (4 estados, `prop-particao-w1/r3-I4F.out`) é o mesmo de `I1F`. Já é violada sem falha (5 estados, abaixo). Status
  `VIOLADA-POR-CONSTRUCAO` nas três colunas.
- **I4Fp** (troca de motivo mantendo a sessão `INVALID`): violada **sem falha** em 5 estados, nas três colunas
  (`prop-{sem,queda,particao}-w1/r3-I4Fp.out`; sem simetria `ctrl-nosym-{sem,queda,particao}/r3-I4Fp.out`, 5 estados): `Logout(s1)` e
  `Logout(s2)` em r1 (ts 2 e 3), `Block` em r2 (ts 2, `ADMIN_BLOCK`), r2 recebe o `Logout(s2)` ts 3 > 2 e o motivo vira `LOGOUT`.
  Corrida `Logout` × `Block` por canal assíncrono, sem falha e sem `Particionar` no traço da coluna partição. **Não é efeito da partição.**
  Independente de D-4.
- **Recomendação (A.7): manter `I4Fp`/`I4Mp`**, com a ressalva acima no catálogo: `I4Fp` isola a troca de motivo, que é o que o auditor
  real checa ("motivo lido diferente do confirmado"), e é `VIOLADA-POR-CONSTRUCAO` como `I2Fb`.

### 4.4 `I1M`, `I4M`, `I4Mp`

- **I1M, I4M** sob partição: `CONFIRMADO-NO-MODELO`, 4 estados (mesmo traço de `I1F`; `prop-particao-w1/r3-I1M.out`, `r3-I4M.out`; sem simetria
  `ctrl-nosym-particao/`, 4 estados).
- **I4Mp** sob partição: `CONFIRMADO-NO-MODELO`, 5 estados, 1 worker com simetria
  (`runs/tlc/audit-b32/ctrl-sym-particao-w1/r3-I4Mp.out`) e sem simetria (`ctrl-nosym-particao/r3-I4Mp.out`); o relatório B-32 só tinha 4 workers
  (`prop-particao/r3-I4Mp.out`, 5 estados). `Logout(s1)` em r1 e `Block` em r2, `Particionar`, `Mesclar` com `rf=r2` troca o motivo em
  r1 de `LOGOUT` para `ADMIN_BLOCK`. O passo violador é `Mesclar`, **mas a pré-condição (dois motivos concorrentes) existe sem falha**
  (4.3); não é prova de efeito da partição por si só.

### 4.5 `MesclarPerdeChave` e `I1P`

- **`MesclarPerdeChave`** (modelo livre, partição): `CONFIRMADO-NO-MODELO`, 4 estados (`prop-livre-particao-w1/r3-MesclarPerdeChave.out`; sem simetria
  `ctrl-nosym-livre-particao/`, 4 estados): `Login(r1,s1)` ainda não propagado, `Particionar`, `Mesclar` com `rf` sem a chave: r1 vai de
  `VALID` a `NONE`. Independente de D-4. **Evidência sobre o `Mesclar` do modelo (copia o registro inteiro do vencedor); nada sobre `PREFERRED_NON_NULL` do Infinispan real.**
- **`I1P`** (`Logout(r,s)` deixa `sessoes'[r][s]` `INVALID`): ver seção 5 (violada no modelo livre por D-4). No semeado, na tabela da seção 3.

## 5. Modelo livre e o artefato do relógio (P-26)

`Spec` (com `Login`), 3 réplicas, limites do plano, 1 worker; traços em `runs/tlc/prop-livre-*-w1/`; sem simetria `runs/tlc/audit-b32/ctrl-nosym-livre-*`.
Coluna "D-4": o traço tem um passo local rejeitado pelo gate de `ts` por `relogio` defasado (`runs/tlc/audit-b32/tracos/classificacao-d4.tsv`).

| Propriedade | Sem falha | Queda | Partição | D-4 | Status |
|---|---|---|---|---|---|
| `I1P` | violada, 4 estados | violada, 4 | violada, 4 | **sim** | `ARTEFATO-DO-MODELO` (defeito de modelagem, P-26) |
| `I1F` | violada, 7 | violada, 7 | violada, 5 | sim em sem falha e queda; **não** em partição | sem falha/queda: `ARTEFATO-DO-MODELO`; partição: `CONFIRMADO-NO-MODELO` (mesma reversão por `Mesclar` do semeado) |
| `I4F` | violada, 7 | violada, 7 | violada, 5 | sim em sem falha e queda; não em partição | idem `I1F` (e `I4F` já é violada por construção no semeado) |
| `I2Fb` | violada, 4 | violada, 4 | violada, 4 | não | `VIOLADA-POR-CONSTRUCAO` (P-27, mecanismo a) |
| `I2Fa`, `I1M`, `I4M` | não executadas | não executadas | violadas, 4, 5, 5 | não | partição: `CONFIRMADO-NO-MODELO`; sem falha e queda: ver seção 6 |
| `MesclarPerdeChave` | não executada | não executada | violada, 4 | não | `CONFIRMADO-NO-MODELO` |

Os resultados do modelo livre **não servem como evidência do comportamento do sistema** quando dependem de D-4: no código real
`SessionState.invalidar` usa `versao + 1` do registro lido. Os vereditos do **semeado** têm todos traços independentes de D-4 (A.6),
mas a ausência de violação do semeado em queda e partição vale só até as cotas verificadas.

## 6. Vacuidade das propriedades de escopo `Mesclar` (`I2Fa`, `I1M`, `I4M`, `I4Mp`)

Argumento estrutural: `Mesclar` exige `particaoAtiva`, que só surge depois de `Particionar`, que incrementa `particoes`; `SemFalha` e
`SoQueda` rejeitam qualquer estado com `particoes>0`; logo `Mesclar` é inalcançável e a propriedade vale por construção. Nas cotas do plano
o TLC **não fechou** (ex.: `I2Fa` sem falha, 94.862 distintos em 61 s), então ali o status é `vácuo-estrutural`.

Prova empírica (`-coverage 1`, `runs/tlc/audit-b32/cobertura-mesclar.tsv`; formato do TLC `distintos:total`):

| Configuração (semeado, `MaxEventosFalha=2`) | Coluna | Resultado | `Particionar` | `Mesclar` | Marca |
|---|---|---|---|---|---|
| r2, `MaxCanal=2`, `MaxRelogio=3` | sem falha | fechou (1.988 distintos) | 0:3.976 (gerou sucessores, todos fora do modelo) | **0:0** | `vácuo-verificado` |
| r2, `MaxCanal=2`, `MaxRelogio=3` | queda | fechou (15.690 distintos) | 0:11.928 | **0:0** | `vácuo-verificado` |
| r3, `MaxCanal=2`, `MaxRelogio=3` | sem falha | fechou (1.627.731 distintos) | 0:9.766.386 | **0:0** | `vácuo-verificado` |
| r3, `MaxCanal=2`, `MaxRelogio=3` | queda (só `I2Fa` medida; as outras três têm o mesmo grafo de estados) | fechou (19.229.901 distintos, 394.154.264 gerados, 8 workers, 1001 s) | 0:29.299.158 | **0:0** | `vácuo-verificado` |
| r2, `MaxCanal=2`, `MaxRelogio=3` | partição (controle positivo) | violada | 21:26 a 104:112 | **> 0** (3:6 a 33:37) | (confirma que a contagem enxerga `Mesclar`) |

As quatro propriedades têm a mesma contagem (`Mesclar 0:0`) em cada configuração de sem falha e queda (arquivos `cov-*/r?-I2Fa|I1M|I4M|I4Mp.out`); exceção: em r3 queda só `I2Fa` foi rodada (o grafo de estados é independente da propriedade: 19.229.901 distintos, o mesmo número de `prop-queda-c2r3-ev2-longo/r3-I1F.out`).
Status: `VACUA` nas configurações da tabela; **na cota do plano (r3, `3/5/2`) continua `vácuo-estrutural`**.

## 7. Pendências e histórico

- Colunas "real" e a ligação com B-34: `PENDENTE`. F1 e F3 permanecem `PENDENTE` quanto ao real.
- `I2Fb`: pergunta Q-3 (auto-bloqueio sem cascata é intencional no domínio?).
- P-26: pergunta Q-5 (corrigir o relógio em variante do modelo e reverificar?).
- Controle sem simetria **não** cobre `ev1` e `ev2-longo` de `I1F` em queda (≈ 12× os estados): ficam `PRESERVADA-ATE-COTAS (sob simetria; sem controle sem simetria)`.
- 2026-10-15: criação (T-A). Próxima revisão: após V-A.
