# Relatório de B-32: propriedades operacionais por transição no TLC

> Worker, 2026-10-09. Branch `feature/b32-tlc-propriedades-fortes` (base `8e2e815`, `feature/b22-f2-particao`).
> Plano: `docs/plano-b29-particao-real-e-contraexemplos-2026-10-09.md` (branch `docs/plano-b29-b30-2026-10-09`), seções 5.2 a 5.6.
> `spec/SessionStore.tla` **não foi alterado** (nenhuma ação, guarda ou variável). Todo o acréscimo está em
> `spec/MCSession.tla` (harness), `spec/run-tlc-propriedades.sh` e `runs/tlc/prop-*/`.

## 1. Como foi executado

- **Verificador:** TLC2 2.19 (rev 5a47802, 08/08/2024), o mesmo de `docs/relatorio-tlc-2026-07-09.md`. O
  `tla2tools.jar` não está no repositório e **não foi encontrado na máquina**; foi baixado de
  `https://github.com/tlaplus/tlaplus/releases/download/v1.7.4/tla2tools.jar` para
  `C:\Users\Francesco\AppData\Local\Temp\claude\C--Users-Francesco-Development-tcc\5fa87c0b-e6a2-4e8e-82b3-1a099fd59db7\scratchpad\tla2tools.jar`
  (fora do repo; o script lê `TLA_TOOLS`). Java: `jdk-17.0.2` (o `java` do PATH puro é 1.8, então usei `JAVA=` explícito).
- **Cenário semeado** (`SpecSemeada`): `IDS={u1}`, `SIDS={s1,s2}`, todas as réplicas com `s1` e `s2` `VALID`, `ts=1`,
  identidade `OPEN` `ts=1`, `relogio=1`, canais vazios. `Login` fica desabilitado.
- **Limites da matriz (plano 5.3):** `MaxEventosFalha=2`, `MaxCanal=3`, `MaxRelogio=5`, `LIMITE=2`, 3 réplicas (`r3`), 4 workers,
  timebox 600 s por propriedade (60 s para as propriedades vacuamente verdadeiras, ver 3.2). Falhas isoladas por restrição
  de estado: `SemFalha` (`quedas+particoes=0`), `SoQueda` (`particoes=0`), `SoParticao` (`quedas=0`).
- **Atraso de rede** não tem ação própria; é a coluna "sem falha" com `MaxCanal=3` (entregas adiadas ao máximo).
- Uma propriedade por execução (o TLC para na primeira violação). Logs brutos, `.cfg` e `resumo-*.tsv` em
  `runs/tlc/prop-<falha>[-<tag>]/` (`prop-livre-*` = modelo não semeado; `-w1` = 1 worker, traço mínimo; `-lim6` = `LIMITE=6`;
  `-c2r3` = limites reduzidos que fecham; `-n2m-sim` = `-simulate`).
- **Tamanho do traço** = número de estados impressos pelo TLC, **incluindo o estado inicial** (N estados = N-1 passos). Com 4 workers
  o TLC nem sempre devolve o traço mínimo (variou ±1 a 2 estados); os tamanhos da tabela são os de **1 worker** (BFS determinístico),
  salvo indicação. Os valores de 4 workers estão nos `resumo-r3.tsv` de cada diretório.
- Todos os números abaixo foram conferidos contra as linhas finais dos `.out` (`N states generated, M distinct states found`,
  `Finished in`, `Model checking completed. No error has been found`); onde o TLC encerrou por timeout, o número é o do último `Progress(...)`.

## 2. Propriedades (em `spec/MCSession.tla`)

| Nome | Tipo | Definição resumida |
|---|---|---|
| `I1F` | ação | réplica com sessão `INVALID` nunca a tem em outro estado no passo seguinte |
| `I4F` | ação | sessão `INVALID`: o motivo do passo seguinte é igual ao atual |
| `I2Fa` | ação | em passos `Mesclar`, `BLOCKED` continua `BLOCKED` |
| `I2Fb` | invariante | `BLOCKED` na réplica implica nenhuma sessão `VALID` dessa identidade na mesma réplica |
| `I1M`, `I4M` | ação | `I1F`/`I4F` restritas a passos `Mesclar` |
| `I4Fp`, `I4Mp` | ação | **acréscimo meu** (ver 4.3): `I4F` só quando a sessão continua `INVALID` |
| `I1P` | ação | achado extra (a): `Logout(r,s)` deixa `sessoes'[r][s]` `INVALID` |
| `MesclarPerdeChave` | ação | achado extra (b): em `Mesclar`, chave com `estado # NONE` não passa a `NONE` |
| `InitSemeado`, `SpecSemeada`, `SemFalha`, `SoQueda`, `SoParticao` | cenário | plano 5.3 |

## 3. Resultados

### 3.1 Matriz principal (semeado, 3 réplicas, limites do plano; traço = estados com o inicial, 1 worker)

| Propriedade | Previsão do Senior (sem falha / queda / partição) | Sem falha (atraso) | Só queda | Só partição |
|---|---|---|---|---|
| **I1F** | preservada / preservada / violada | **sem violação** (limite de tempo 601 s, 18.273.465 estados distintos, **não fechou**) | **sem violação** (limite de tempo 601 s, 22.377.840 distintos, **não fechou**) | **VIOLADA, traço de 4 estados** |
| **I2Fa** | preservada / preservada / violada | vácuo (Mesclar inalcançável, ver 3.2) | vácuo | **VIOLADA, traço de 4 estados** |
| **I2Fb** | preservada / preservada / preservada | **VIOLADA, 3 estados** | **VIOLADA, 3 estados** | **VIOLADA, 3 estados** |
| **I4F** | pode violar / idem / violada | **VIOLADA, 5 estados** | **VIOLADA, 5 estados** | **VIOLADA, 4 estados** |
| **I4Fp** (pura) | n/a | **VIOLADA, 5 estados** | **VIOLADA, 5 estados** | **VIOLADA, 5 estados** |
| **I1M** | n/a / n/a / violada | vácuo | vácuo | **VIOLADA, 4 estados** |
| **I4M** | n/a / n/a / violada | vácuo | vácuo | **VIOLADA, 4 estados** |
| **I4Mp** (pura) | n/a | vácuo | vácuo | **VIOLADA, 5 estados** (4 workers; 1 worker não rodado) |

Fontes: `runs/tlc/prop-{sem,queda,particao}{,-w1}/`. Observação: `I4Mp` de partição foi só com 4 workers (5 estados).

### 3.2 O que significa "vácuo" e "não fechou"

- **Vácuo.** `I2Fa`, `I1M`, `I4M`, `I4Mp` só restringem passos `Mesclar`. `Mesclar` exige `particaoAtiva`, que só surge depois de
  `Particionar`, que incrementa `particoes`; `SemFalha` e `SoQueda` rejeitam qualquer estado com `particoes>0`. Logo o passo
  `Mesclar` é inalcançável e a propriedade vale por construção. Rodei-as com timebox curto (60 s a 300 s) e **o TLC não terminou**
  (por exemplo `I2Fa` sem falha: 94.862 distintos em 61 s); o argumento de vacuidade é estrutural, **não é um fechamento do TLC**.
- **`I1F` em sem falha e queda com 3 réplicas nos limites do plano não fechou** (≈18 a 22 milhões de estados distintos em 600 s, fila ainda
  com ~13 a 16 milhões). Para ter exaustão, reduzi os limites (tabela 3.3).

### 3.3 Onde `I1F` (e `I1P`) fecharam exaustivamente

| Configuração | Falha | Réplicas | MaxCanal/MaxRelogio/MaxEventosFalha | Resultado | Estados distintos (gerados) | Tempo |
|---|---|---|---|---|---|---|
| `prop-sem/r2-I1F` | sem | 2 | 3 / 5 / 2 (plano) | **sem violação, exaustivo** | 2.968.799 (63.508.237) | 137 s |
| `prop-sem-c2r3/r3-I1F` | sem | 3 | 2 / 3 / 2 | **sem violação, exaustivo** | 1.627.731 (53.541.786) | 130 s |
| `prop-queda-c2r3/r2-I1F` | queda | 2 | 2 / 3 / 2 | **sem violação, exaustivo** | 15.690 (195.239) | 3 s |
| `prop-queda-c2r3-ev1/r3-I1F` | queda | 3 | 2 / 3 / 1 | **sem violação, exaustivo** | 8.037.698 (197.610.219) | 571 s |
| `prop-queda-c2r3-ev2-longo/r3-I1F` | queda | 3 | 2 / 3 / 2 | **sem violação, exaustivo** | 19.229.901 (394.154.264) | 624 s (8 workers, timebox 2400 s) |
| `prop-queda/r2-I1F` | queda | 2 | 3 / 5 / 2 (plano) | sem violação até aqui, **não fechou** | 21.725.102 | 604 s |
| `prop-sem-c2r3/r3-I1P` | sem | 3 | 2 / 3 / 2 | **sem violação, exaustivo** | 1.627.731 (53.541.786) | 84 s |
| `prop-particao-c2r3/r3-I1P` | partição | 3 | 2 / 3 / 2 | sem violação até aqui, **não fechou** | 33.038.740 | 600 s |

(O último diretório é `runs/tlc/prop-particao-c2r3/`.) Leitura honesta: "preservada **até** MaxCanal 2, MaxRelogio 3, duas quedas, 3 réplicas,
cenário semeado" para queda, e "até MaxCanal 2/MaxRelogio 3 (3 réplicas) e MaxCanal 3/MaxRelogio 5 (2 réplicas)" para sem falha. Em
nenhum caso é prova geral.

### 3.4 Traços de contraexemplo, em palavras (cenário semeado, 3 réplicas, 1 worker)

- **I1F sob partição (4 estados):** `Logout(r1,s1)` (r1 passa a `INVALID/LOGOUT`, mensagens em fila para r2 e r3); `Particionar` com
  lado 1 = {r1} e lado 2 = {r2,r3}; `Mesclar` com `rf` ainda `VALID` (r2/r3 não receberam o logout): r1 recebe o registro `VALID`
  `ts=1` e a invalidação confirmada é revertida. **É exatamente o traço que o Senior previu.** `I1M` e `I4M` dão o mesmo traço
  (a violação é do passo `Mesclar`); `I4F` também, porque a reversão muda o motivo de `LOGOUT` para `NONE` (ver 4.3).
- **I2Fa sob partição (4 estados):** `Block(r1,u1)` (r1: identidade `BLOCKED`, sessões `INVALID/ADMIN_BLOCK`); `Particionar` com {r1} isolada;
  `Mesclar` com `rf` que ainda não recebeu o `ID_BLOQUEIA`: r1 volta a `OPEN` e as sessões voltam a `VALID`. Previsão confirmada.
- **I2Fb em sem falha, queda e partição (3 estados): auto-bloqueio por limite.** `IncrementFailure` duas vezes na mesma réplica
  (com `LIMITE=2`) deixa a identidade `BLOCKED` pelo limite, **sem invalidar as sessões `VALID`** da mesma réplica. Ver 4.1.
- **I2Fb com `LIMITE=6` (auto-bloqueio inalcançável), 4 estados nas três colunas:** `Logout(r1,s1)`; `Block(r1,u1)` (a cascata
  `sessoesAfetadas` calculada na origem contém só `s2`, pois `s1` já estava `INVALID` em r1); entrega do `ID_BLOQUEIA` a r2, que
  ainda tem `s1` `VALID`: r2 fica com identidade `BLOCKED` e `s1` `VALID`. Mecanismo do canal assíncrono, sem falha nenhuma.
- **I4F e I4Fp sem falha e queda (5 estados):** `Logout(r1,s2)` ts 3 e `Block(r2,u1)` ts 2 acontecem concorrentemente em réplicas
  diferentes; r2 aplica o próprio `Block` (`ADMIN_BLOCK`, ts 2) e depois recebe o `Logout` de r1 (ts 3 > 2), trocando o motivo de
  `ADMIN_BLOCK` para `LOGOUT` sem passar por `VALID`. Corrida de canal, sem falha (previsão do Senior: "pode ser violada", **confirmada**).
- **I4Mp sob partição (5 estados):** r1 faz `Logout(s1)`, r2 faz `Block`; `Particionar` {r1}|{r2,r3}; `Mesclar` com `rf=r2` troca em r1 o
  motivo `LOGOUT` por `ADMIN_BLOCK` (a invalidação permanece, o motivo muda). Atribui a troca de motivo à fusão.

### 3.5 Modelo não semeado (`Spec`, `Login` habilitado), 3 réplicas, limites do plano

Fonte: `runs/tlc/prop-livre-{sem,queda,particao}{,-w1}/`. Traços mínimos (1 worker):

| Propriedade | Sem falha | Só queda | Só partição |
|---|---|---|---|
| `I1P` | **VIOLADA, 4 estados** | **VIOLADA, 4 estados** | **VIOLADA, 4 estados** |
| `I1F` | **VIOLADA, 7 estados** | **VIOLADA, 7 estados** | **VIOLADA, 5 estados** |
| `I4F` | **VIOLADA, 7 estados** | **VIOLADA, 7 estados** | **VIOLADA, 5 estados** |
| `I2Fb` | **VIOLADA, 4 estados** | **VIOLADA, 4 estados** | **VIOLADA, 4 estados** |
| `I2Fa` | vácuo | vácuo | **VIOLADA, 4 estados** |
| `I1M` / `I4M` | vácuo | vácuo | **VIOLADA, 5 estados** |
| `MesclarPerdeChave` | n/a | n/a | **VIOLADA, 4 estados** |

(Nas colunas sem falha e queda do modelo livre, `I2Fa`, `I1M`, `I4M` e `MesclarPerdeChave` não foram executadas; a vacuidade é a mesma de 3.2.)

### 3.6 `-simulate` (complemento, não prova ausência)

`-simulate num=2000000 -depth 80`, modelo livre, 3 réplicas, limites do plano: `I1F`, `I4F`, `I2Fb` e `I1P` foram **encontradas** nas três colunas,
com traços aleatórios de 4 a 17 estados (`runs/tlc/prop-livre-*-n2m-sim/`). Como os limites de canal e relógio encerram cada
trajetória cedo, a simulação aqui tem pouco alcance em profundidade e **não acrescenta evidência de ausência**. Não rodei a simulação
com mais profundidade.

## 4. Previsões do Senior (5.5) e achados

### 4.1 Status por previsão

| Previsão (Senior, 5.5) | Status | Observação |
|---|---|---|
| I1F sem falha: preservada (semeado) | **Confirmada até as cotas** | exaustiva a 2 réplicas (limites do plano) e a 3 réplicas com MaxCanal 2/MaxRelogio 3; a 3 réplicas nos limites do plano, sem violação em ~18 M estados, não fechou |
| I1F queda: preservada | **Confirmada até as cotas** | exaustiva com 3 réplicas, MaxCanal 2, MaxRelogio 3, até 2 quedas (19.229.901 distintos); nos limites do plano não fechou |
| I1F partição: violada, traço curto | **Confirmada** | 4 estados, traço idêntico ao descrito |
| I2Fa partição: violada | **Confirmada** | 4 estados |
| I2Fa sem falha / queda: preservada | **Verdadeira por vacuidade** (Mesclar inalcançável); sem fechamento pelo TLC |
| I2Fb: preservada nas três colunas | **REFUTADA** | violada em todas, com dois mecanismos que **não são falha** (ver abaixo) |
| I4F sem falha / queda: pode violar | **Confirmada** | corrida `Logout` x `Block` por canal assíncrono, 5 estados |
| I4F partição: violada | **Confirmada**, com ressalva | o traço mínimo de `I4F` é a própria reversão de I1F; `I4Fp` (troca de motivo pura) é violada em 5 estados por corrida de canal e, atribuída à fusão, `I4Mp` em 5 estados |
| I1M, I4M partição: violadas | **Confirmadas** | 4 estados cada |
| 5.6: sem `Mesclar` o estado só vai de `VALID` a `INVALID` | **Consistente** | `I1F` exaustiva em sem falha e queda (cotas de 3.3). O argumento vale para o cenário semeado |

**Mecanismos de I2Fb (refutação da previsão):**

1. **Auto-bloqueio por limite sem cascata** (`IncrementFailure` bloqueando ao atingir `LIMITE`): o modelo bloqueia a identidade e **não
   invalida as sessões**. Conferi o código: `SessionOps.incrementFailure` chama `IdentityState.incrementar(LIMITE_TENTATIVAS, ...)` e
   não invalida sessões; só `block()` varre `sessions`. Portanto **o modelo reproduz o comportamento do código**, e I2 (e I2Fb) é
   violada por esse caminho sem nenhuma falha. Isto é relevante para o auditor real: bloqueio por limite de tentativas deixa sessões `VALID` de identidade `BLOCKED` por desenho.
2. **Cascata calculada na origem** (`sessoesAfetadas`) aplicada a uma réplica que ainda vê uma das sessões `VALID` (traço do `LIMITE=6`). Artefato
   do modelo de réplicas com cópia local; no sistema real (um cache distribuído, uma única chave por sessão) é provável que não se manifeste do mesmo modo, mas isso é leitura, não evidência.

Para atribuir algo à partição, `I2Fb` **não discrimina** a coluna de falha. Uma versão de I2 que valha só para `Mesclar` seria `I2Fa`.

### 4.2 Achado extra (a): `EntregaMensagem` não atualiza `relogio` (5.4 ponto 1): **CONFIRMADO NO MODELO**

- `I1P` violada **sem falha** no modelo livre, em 4 estados: `Login(r1,s1)` (ts 1) -> `EntregaMensagem` leva o registro a r2 (cujo
  `relogio` continua 0) -> `Logout(r2,s1)` calcula `novoTS = 0+1 = 1`, não é maior que o `ts` local (1), então **não grava**, mas transmite
  mensagem que as outras réplicas também descartam. Logout aceito e perdido, sem falha.
- Consequência além do descarte: no modelo livre sem falha, `I1F` é violada em 7 estados por essa mesma raiz. `Login(s2)` em r1 (ts 2), entrega a r2;
  `Logout(s2)` em r2 recebe `ts=1`; r3 recebe o `Logout` (ts 1) **antes** do `Login` (ts 2) e em seguida o `Login`, que
  ressuscita a sessão. **Não depende de sid reutilizada** (uma só `Login` por sid), contrariando o que o plano supunha ser a causa nesse cenário.
- No cenário semeado **não ocorre**: `I1P` fechou exaustivamente sem falha (1.627.731 distintos, MaxCanal 2/MaxRelogio 3, 84 s); com queda e
  partição sem violação até 120 s a 600 s (não fechou).
- **Ressalva de fidelidade:** no código, `SessionState.invalidar` usa `versao + 1` do registro lido, então a versão do logout supera a do `Login`. O defeito é de **modelo
  (decisão D-4)**, não do sistema. Mesmo assim o Senior pediu parada e relato: **este é o artefato 5.4 (1), registrar como problema novo
  (sugestão P-26)** se o Gestor quiser; não alterei `registro-de-problemas.md`.

### 4.3 Observação sobre `I4F` como especificada

`I4F` (`INVALID => motivo' = motivo`) dispara também quando a sessão volta a `VALID` (motivo vira `NONE`), de modo que, sob partição, seu
traço mínimo é o mesmo de `I1F`. Por isso acrescentei `I4Fp`/`I4Mp` (troca de motivo mantendo a sessão `INVALID`), que isolam o efeito de
"motivo único". Não alterei `I4F`.

### 4.4 Achado extra (b): `Mesclar` do modelo descarta chave existente só do lado isolado: **EVIDÊNCIA NO MODELO; nada sobre o Infinispan real**

`MesclarPerdeChave` é violada em 4 estados (`runs/tlc/prop-livre-particao-w1/r3-MesclarPerdeChave.out`): `Login(r1,s1)` (mensagem ainda em fila para r2 e r3);
`Particionar` {r1}|{r2,r3}; `Mesclar` com `rf` que ainda não tem `s1`: r1 passa de `VALID` a `NONE` (a chave existente só do lado
isolado some). As mensagens pendentes ainda chegam depois a r2 e r3, mas r1 não as recebe de volta: divergência permanente (mesma
família de `I5`). Isso confirma que o `Mesclar` do modelo é o "vencedor sobrescreve inteiro", como na leitura do Senior (5.6). **Não criei `MesclarNaoNulo`** (decisão do Gestor) e não afirmo
nada sobre `PREFERRED_NON_NULL` real, que só o B-34 pode confirmar. Nota: no modelo o lado isolado não age durante a partição (`PodeAgir`), então o cenário "escrita atendida pelo nó
isolado na janela de detecção" é aproximado por uma escrita feita **antes** de `Particionar` e ainda não propagada.

## 5. Limitações

1. **Exaustão parcial.** Sem falha e queda com 3 réplicas nos limites do plano (3/5/2), `I1F` não fechou em 600 s; só fechou com limites reduzidos (3.3) ou 2 réplicas. "Preservada até as cotas X", nunca em geral.
2. **Propriedades vacuamente verdadeiras** (I2Fa, I1M, I4M, I4Mp sem falha e queda) não foram fechadas pelo TLC; a justificativa é estrutural (3.2).
3. **Tamanho do traço** com 4 workers não é garantidamente mínimo; os valores citados são de 1 worker. `I4Mp` de partição e `I1P` semeado com queda/partição não têm rodada de 1 worker/fechamento.
4. **Simetria** foi usada (`Simetria`) em propriedades de ação; é adequada porque as fórmulas não distinguem réplicas; nenhum aviso do TLC apareceu nos logs, mas a redução por simetria em propriedades de ação não foi validada com uma rodada sem `SYMMETRY`.
5. **Execução paralela.** Um `ERRO rc=150` em `prop-queda/resumo-r3.tsv` (`I4M`, "Parsing or semantic analysis failed") foi colisão de arquivos temporários do TLC em execuções simultâneas; repetida depois (última linha do mesmo arquivo: 60 s, sem violação até aqui). O script usa `-metadir` próprio por execução, mas os módulos padrão ainda são extraídos para o `TEMP`.
6. **`-simulate`** teve alcance raso (3.6).
7. O modelo continua com as ressalvas de 5.4 do plano (relógio local sem fusão na recepção; sid reutilizável no modelo livre) e `Mesclar` por registro inteiro.
8. Nada aqui mede o sistema real: resultado do TLC é sobre o modelo (B-34 é quem confronta o Infinispan).

## 6. Reproduzir

```bash
export TLA_TOOLS=<caminho>/tla2tools.jar JAVA=<jdk17>/bin/java
spec/run-tlc-propriedades.sh semeado particao r3 I1F I4F I2Fa I2Fb I1M I4M I4Fp I4Mp   # matriz, coluna partição
spec/run-tlc-propriedades.sh semeado sem r3 I1F                                          # coluna sem falha
TAG=c2r3 MAXCANAL=2 MAXRELOGIO=3 spec/run-tlc-propriedades.sh semeado sem r3 I1F         # fecha exaustivo
WORKERS=1 TAG=w1 spec/run-tlc-propriedades.sh livre sem r3 I1P I1F                       # traço mínimo
SIMULATE=2000000 TAG=n2m spec/run-tlc-propriedades.sh livre queda r3 I1F                 # -simulate
```

## 7. Pontos para o Gestor / Senior

1. Registrar o artefato 5.4 (1) como problema novo (sugestão P-26)? Ele confirmou-se no modelo livre, sem falha.
2. Aceitar I2Fb como "violada por construção" (auto-bloqueio por limite e cascata na origem), ou trocá-la por uma propriedade que isole a partição (I2Fa já faz isso)?
3. Manter `I4Fp`/`I4Mp` ao lado de `I4F`/`I4M`?
4. O catálogo 3.2 (operação x falha x invariante) deve receber estes vereditos; não o alterei por não estar em `spec/`.
