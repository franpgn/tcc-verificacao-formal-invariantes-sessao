# Plano — correção de broadcast + LWW em SessionStore.tla

> Elaborado pelo Senior em 2026-09-16, para execução pelo Worker. **Este documento é o
> plano completo** (substitui qualquer versão anterior truncada na conversa). Não foi
> implementado nem executado nada aqui — é especificação de trabalho.

## Contexto

A rodada de TLC de 09/07/2026 (`docs/relatorio-tlc-2026-07-09.md`, logs em `runs/tlc/`)
encontrou que **I5** e **I6** (convergência de estado/contador sob quiescência) violam
mesmo com o canal vazio. Causa-raiz confirmada por leitura direta de
`spec/SessionStore.tla` e `spec/MCSession.tla`:

1. `canal` é uma única `Seq(Mensagem)` **global**. `EntregaMensagem(r)` escolhe um
   índice arbitrário `i` no canal e entrega a mensagem a **uma única réplica** `r`,
   removendo-a para sempre — nunca há broadcast. Cada escrita é entregue a exatamente
   uma réplica, nunca a todas. Isso é um defeito estrutural de modelagem, não um
   artefato benigno de "mensagens em trânsito".
2. Mesmo corrigindo o broadcast, a precondição de `Login` (`sessoes[r][s] = "NONE"`)
   permite que duas identidades diferentes façam login concorrente na mesma `sid` em
   réplicas diferentes. Cada réplica aplica sua escrita local imediatamente; a ordem de
   entrega das mensagens assíncronas decide qual "vencedor" cada réplica observa, e
   réplicas diferentes podem ficar com vencedores diferentes mesmo após entrega
   completa (canal vazio, ainda divergente). Isso exige resolução de conflito
   last-writer-wins (LWW) via relógio lógico — mecanismo de Shapiro et al. 2011
   (`shapiro_crdt_2011`, já citado no artigo do usuário) para Strong Eventual
   Consistency.

Este plano cobre as duas mudanças estruturais necessárias na especificação, com
pseudocódigo TLA+ completo, critérios de aceite, plano de execução com timebox, e a
organização do estado pendente do repositório (ver "Passo -1" abaixo).

---

## Passo -1 — organizar estado pendente do repositório (fazer ANTES do Passo 0)

`git status` em `tcc-desenvolvimento` (verificado em 2026-09-16) mostra:

```
Em branch docs/vm-hyperv-netem
Mudancas nao commitadas:
  modified:   cluster/infinispan-cluster.xml
  modified:   cluster/podman-compose.yml
Arquivos nao rastreados:
  cluster/jgroups-tcp.xml
  docs/relatorio-tlc-2026-07-09.md
  runs/smoke/
  runs/tlc/
  spec/MCSession.tla
  spec/run-tlc-invariantes.sh
```

Ou seja: os próprios arquivos que esta correção precisa modificar (`spec/MCSession.tla`,
`spec/run-tlc-invariantes.sh`) **só existem sem commit**, soltos dentro da branch
`docs/vm-hyperv-netem` — `main` não os tem. Não dá para criar a branch de correção a
partir de `main` (perderia esses artefatos); e não dá para misturar cluster/VM/netem com
verificação formal no mesmo commit (viola "uma responsabilidade por branch").

Antes de tocar em qualquer arquivo TLA+, o Worker deve, ainda em `docs/vm-hyperv-netem`:

1. **Commit 1 — só o que é de VM/cluster/netem** (não relacionado a TLC):
   ```bash
   cd "C:/Users/Francesco/Development/tcc/tcc-desenvolvimento"
   git add cluster/infinispan-cluster.xml cluster/podman-compose.yml cluster/jgroups-tcp.xml runs/smoke/
   git commit -m "docs(f3): configuracao de cluster JGroups TCP e artefatos de smoke test da VM Hyper-V"
   ```
2. **Commit 2 — só os artefatos da rodada de TLC de 09/07** (preservar o trabalho já
   feito, sem perdê-lo):
   ```bash
   git add spec/MCSession.tla spec/run-tlc-invariantes.sh docs/relatorio-tlc-2026-07-09.md runs/tlc/
   git commit -m "docs: harness MCSession.tla, runner por invariante e relatorio da rodada TLC 2026-07-09"
   ```
3. Confirmar `git status` limpo (`nothing to commit, working tree clean`) antes de
   prosseguir.
4. **Só então** criar `fix/canal-broadcast-por-replica` **a partir da ponta atual de
   `docs/vm-hyperv-netem`** (não de `main`), para herdar `MCSession.tla` e
   `run-tlc-invariantes.sh` já existentes:
   ```bash
   git checkout -b fix/canal-broadcast-por-replica
   ```

Nenhum destes commits vai para `origin/*` sem aprovação explícita do Gestor — commits
locais são livres, push não.

---

## Passo 0 — ambiente (toda sessão de shell nova, a partir daqui)

O `java` padrão do PATH nesta máquina resolve para uma instalação Java 8 antiga. A JDK
22 (exigida pelos scripts, Java 11+) está em
`C:\Program Files\Java\jdk-22.0.2\bin\java.exe` mas não é a primeira no PATH.

```bash
export PATH="/c/Program Files/Java/jdk-22.0.2/bin:$PATH"
java -version   # deve mostrar "openjdk 22.0.2" — se aparecer 1.8.x, o export falhou
cd "C:/Users/Francesco/Development/tcc/tcc-desenvolvimento/spec"
```

Alternativa sem export (mais verbosa, usar só se o export falhar): chamar
`"/c/Program Files/Java/jdk-22.0.2/bin/java.exe"` por caminho completo em cada
invocação do TLC.

---

## Mudança 1 — Broadcast por réplica (fix, obrigatória)

### O que muda

- `canal` muda de tipo: `Seq(Mensagem)` → `[REPLICAS -> Seq(Mensagem)]`.
- As 6 ações de escrita (`Login`, `Logout`, `IncrementFailure`, `ResetFailures`,
  `Block`, `Unblock`) passam a enfileirar a mensagem nas filas de TODAS as réplicas
  exceto a de origem `r` (que já aplicou o efeito localmente).
- `EntregaMensagem(r)` passa a desenfileirar de `canal[r]` especificamente, mantendo a
  escolha de índice arbitrário (reordenação não-determinística) que já existia — isso é
  proposital: estressa o pior caso de entrelaçamento e é o que vai validar que a
  correção da Mudança 2 é robusta a qualquer ordem de entrega.
- `Quiescent` passa a exigir fila vazia em TODAS as réplicas.
- `MCSession.tla`: `LimiteCanal` generalizado para todas as filas.

### Operador auxiliar novo (em `SessionStore.tla`)

```tla
(***************************************************************************)
(* Enfileira msg nas filas de TODAS as replicas exceto a de origem, que ja *)
(* aplicou o efeito localmente e nao precisa entregar a mensagem a si      *)
(* mesma.                                                                   *)
(***************************************************************************)
Broadcast(origem, msg) ==
  [r2 \in REPLICAS |-> IF r2 = origem THEN canal[r2] ELSE Append(canal[r2], msg)]
```

### Init — trecho que muda

```tla
/\ canal = [r \in REPLICAS |-> << >>]
```

### As 6 ações — trecho que muda em cada uma

Em cada uma das 6 ações, trocar a linha final `canal' = Append(canal, msg)` por
`canal' = Broadcast(r, msg)`. (O corpo completo e final de cada ação, já incorporando
também a Mudança 2, está na seção seguinte — não há necessidade de implementar a
Mudança 1 e depois reescrever de novo; mas se o Worker for implementar a Mudança 1
ISOLADAMENTE primeiro, como recomendado no plano de execução abaixo, o diff nesta
etapa é *só* essa troca de `Append(canal, msg)` por `Broadcast(r, msg)` em cada uma das
6 ações, mais o `Quiescent` e o `EntregaMensagem` abaixo — sem relógio lógico, sem
variáveis TS novas.)

### EntregaMensagem(r) — versão da Mudança 1 isolada (sem LWW ainda)

```tla
EntregaMensagem(r) ==
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ CASE m.tipo = "SESSAO_VALIDA" ->
                /\ sessoes'  = [sessoes  EXCEPT ![r][m.chave] = "VALID"]
                /\ sessaoDe' = [sessaoDe EXCEPT ![r][m.chave] = m.payload]
                /\ UNCHANGED << motivos, contador, status >>
            [] m.tipo = "SESSAO_INVALIDA" ->
                /\ sessoes' = [sessoes EXCEPT ![r][m.chave] = "INVALID"]
                /\ motivos' = [motivos EXCEPT ![r][m.chave] = m.payload]
                /\ UNCHANGED << sessaoDe, contador, status >>
            [] m.tipo = "CONTADOR_INC" ->
                /\ contador' = [contador EXCEPT ![r][m.chave] =
                                  IF @ < LIMITE THEN @ + 1 ELSE @]
                /\ UNCHANGED << sessoes, motivos, sessaoDe, status >>
            [] m.tipo = "CONTADOR_RESET" ->
                /\ contador' = [contador EXCEPT ![r][m.chave] = 0]
                /\ UNCHANGED << sessoes, motivos, sessaoDe, status >>
            [] m.tipo = "ID_BLOQUEIA" ->
                /\ status' = [status EXCEPT ![r][m.chave] = "BLOCKED"]
                /\ UNCHANGED << sessoes, motivos, sessaoDe, contador >>
            [] m.tipo = "ID_DESBLOQUEIA" ->
                /\ status'   = [status   EXCEPT ![r][m.chave] = "OPEN"]
                /\ contador' = [contador EXCEPT ![r][m.chave] = 0]
                /\ UNCHANGED << sessoes, motivos, sessaoDe >>
            [] OTHER -> UNCHANGED << sessoes, motivos, sessaoDe, contador, status >>
```

(Corpo idêntico ao original — só muda a fonte da mensagem, agora `canal[r][i]` em vez
de `canal[i]`, e o `canal'` só mexe na entrada `r` da função.)

### Quiescent

```tla
Quiescent == \A r \in REPLICAS: Len(canal[r]) = 0
```

### MCSession.tla — trecho que muda nesta etapa

```tla
LimiteCanal == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal
```

### Checkpoint empírico recomendado após a Mudança 1 isolada

Rodar `run-tlc-invariantes.sh tiny 2` e `small 2` **antes** de implementar a Mudança 2.
Resultado esperado e por quê:

- I1/I2 continuam violando (atraso de propagação, não é isso que estamos corrigindo).
- **`tiny`** (só 1 identidade, `IDS = {u1}`) pode já fechar I5/I6 mesmo só com
  broadcast — o cenário motivador (duas identidades DIFERENTES disputando a mesma
  sessão) é estruturalmente impossível com 1 única identidade. Duas Logins do mesmo
  `u1` na mesma `sid`, vindas de réplicas diferentes, não divergem (mesmo valor
  resultante em ambas).
- **`small`** (2 identidades) deve continuar violando, agora por uma causa-raiz
  DIFERENTE e mais específica: corrida de escrita concorrente entre duas identidades
  distintas na mesma sessão — não mais "entrega a uma réplica só".

Esse par de resultados (tiny fecha, small não) é o dado empírico que comprova que a
Mudança 2 é necessária, não cosmética. Registrar os vereditos brutos desta etapa (não
precisa escrever relatório ainda — o relatório final cobre as duas mudanças).

---

## Mudança 2 — LWW-register via relógio lógico de Lamport (necessária)

### Decisões de projeto (D-1 a D-4 — ver seção própria de validação do Gestor)

- **D-1**: um relógio (timestamp) POR CAMPO, não um relógio composto por "registro de
  sessão inteiro". `sessoes[r][s]` é escrito tanto por `Login` (junto com `sessaoDe`)
  quanto por `Logout`/`Block` (junto com `motivos`) — não existe um único "evento" que
  sempre escreve os três campos juntos, então um timestamp único por `sid` cobrindo os
  três quebraria a prova de convergência por campo. Registradores por campo, cada um
  com seu próprio timestamp, é o que GARANTE convergência (argumento clássico de
  LWW-register CRDT).
- **D-2** (a mais debatível): `contador` também tratado como LWW-register (valor
  absoluto — "última escrita vence"), não como contador op-based (soma). O emissor
  calcula o valor novo localmente (`contador[r][id]+1` ou `0`) e propaga esse valor
  absoluto; o receptor aplica LWW normal. Trade-off: incrementos concorrentes de
  réplicas diferentes podem não somar ambos no merge (só o de maior prioridade
  "conta"). Isso é diferente de um PN-Counter CRDT. Escolhido porque (a) é literalmente
  o que foi pedido (LWW-register, Shapiro et al. 2011), (b) garante convergência de
  forma direta e demonstrável, e (c) evita introduzir um segundo tipo de CRDT fora do
  escopo pedido.
- **D-3**: desempate determinístico por identidade de réplica, via uma bijeção fixa
  `REPLICAS -> 1..NR` (TLA+ não tem ordem nativa sobre valores de modelo opacos). Como
  consequência, a especificação DEIXA DE SER SIMÉTRICA em `REPLICAS` — o resultado da
  resolução de conflito passa a depender da identidade da réplica de origem. É
  **obrigatório** remover `Permutations(REPLICAS)` de `Simetria` em `MCSession.tla`,
  senão a redução por simetria do TLC deixa de ser válida (poderia podar estados que
  não são de fato equivalentes, mascarando bugs). `IDS` e `SIDS` continuam simétricos
  normalmente.
- **D-4**: o relógio lógico NÃO faz merge-on-receive (não avança em
  `EntregaMensagem`). O enunciado original pede explicitamente "incrementado a cada
  operação LOCAL por réplica" — logo `relogio[r]` só avança nas 6 ações de escrita.
  Mais simples, reduz o espaço de estados, e não compromete a corretude (a prova de
  convergência por campo não depende de merge de relógio, só de a prioridade `Prio`
  ser total e injetiva, o que já é garantido pela codificação abaixo).

### Novas variáveis

```tla
VARIABLES sessoes, motivos, sessaoDe, contador, status, canal,
          relogio,     \* [REPLICAS -> Nat] -- relogio logico local (Lamport, so avanca em op local)
          sessoesTS,   \* [REPLICAS -> [SIDS -> Nat]] -- prioridade da ultima escrita aplicada a sessoes[r][s]
          motivosTS,   \* [REPLICAS -> [SIDS -> Nat]] -- idem para motivos[r][s]
          sessaoDeTS,  \* [REPLICAS -> [SIDS -> Nat]] -- idem para sessaoDe[r][s]
          contadorTS,  \* [REPLICAS -> [IDS  -> Nat]] -- idem para contador[r][id]
          statusTS     \* [REPLICAS -> [IDS  -> Nat]] -- idem para status[r][id]

vars == << sessoes, motivos, sessaoDe, contador, status, canal,
           relogio, sessoesTS, motivosTS, sessaoDeTS, contadorTS, statusTS >>
```

### Ordem total fixa sobre REPLICAS + prioridade combinada (novo bloco, logo após as
### CONSTANTS/ASSUME, antes de TypeOK)

```tla
(***************************************************************************)
(* Ordem total arbitraria, porem fixa, sobre REPLICAS -- usada apenas para *)
(* desempate deterministico em conflitos de timestamp igual (LWW). E       *)
(* necessaria porque, a partir desta versao, a resolucao de conflito       *)
(* distingue replicas por identidade; logo REPLICAS deixa de ser um        *)
(* conjunto simetrico para fins de SYMMETRY em MCSession.tla (ver          *)
(* docs/plano-fix-broadcast-lww-2026-09-16.md, decisao D-3).               *)
(***************************************************************************)
NR == Cardinality(REPLICAS)
ReplicaSeq == CHOOSE seq \in [1..NR -> REPLICAS] :
                 \A i, j \in 1..NR : i # j => seq[i] # seq[j]
RepIndex(r) == CHOOSE i \in 1..NR : ReplicaSeq[i] = r

(* Prioridade combinada (relogio logico, indice de replica) para LWW com   *)
(* desempate deterministico: maior prioridade vence. Codifica o par        *)
(* (ts, RepIndex(r)) em um unico Nat preservando a ordem lexicografica.    *)
(* Como RepIndex e injetiva em 1..NR, Prio nunca produz empate real entre  *)
(* pares (ts,r) distintos.                                                  *)
Prio(ts, r) == ts * (NR + 1) + RepIndex(r)
```

### Mensagem (tipo atualizado)

```tla
Mensagem ==
  [tipo: {"SESSAO_VALIDA", "SESSAO_INVALIDA",
          "CONTADOR_INC", "CONTADOR_RESET",
          "ID_BLOQUEIA", "ID_DESBLOQUEIA"},
   chave: SIDS \cup IDS,
   payload: STRING,
   valor: 0..LIMITE,   \* usado so por CONTADOR_INC/CONTADOR_RESET; demais tipos usam 0
   ts: Nat,            \* tick logico da replica de origem no instante do envio
   origem: REPLICAS,   \* replica que produziu a mensagem (rastreabilidade/depuracao)
   prio: Nat]           \* Prio(ts, origem), pre-computado para comparacao O(1) na entrega
```

### TypeOK (versão final, com Mudança 1 + Mudança 2)

```tla
TypeOK ==
  /\ sessoes  \in [REPLICAS -> [SIDS -> Estados]]
  /\ motivos  \in [REPLICAS -> [SIDS -> MotivosT]]
  /\ sessaoDe \in [REPLICAS -> [SIDS -> {"NONE"} \cup IDS]]
  /\ contador \in [REPLICAS -> [IDS -> 0..LIMITE]]
  /\ status   \in [REPLICAS -> [IDS -> StatusT]]
  /\ canal    \in [REPLICAS -> Seq(Mensagem)]
  /\ relogio    \in [REPLICAS -> Nat]
  /\ sessoesTS  \in [REPLICAS -> [SIDS -> Nat]]
  /\ motivosTS  \in [REPLICAS -> [SIDS -> Nat]]
  /\ sessaoDeTS \in [REPLICAS -> [SIDS -> Nat]]
  /\ contadorTS \in [REPLICAS -> [IDS -> Nat]]
  /\ statusTS   \in [REPLICAS -> [IDS -> Nat]]
```

### Init (versão final)

```tla
Init ==
  /\ sessoes  = [r \in REPLICAS |-> [s \in SIDS |-> "NONE"]]
  /\ motivos  = [r \in REPLICAS |-> [s \in SIDS |-> "NONE"]]
  /\ sessaoDe = [r \in REPLICAS |-> [s \in SIDS |-> "NONE"]]
  /\ contador = [r \in REPLICAS |-> [id \in IDS |-> 0]]
  /\ status   = [r \in REPLICAS |-> [id \in IDS |-> "OPEN"]]
  /\ canal    = [r \in REPLICAS |-> << >>]
  /\ relogio    = [r \in REPLICAS |-> 0]
  /\ sessoesTS  = [r \in REPLICAS |-> [s \in SIDS |-> 0]]
  /\ motivosTS  = [r \in REPLICAS |-> [s \in SIDS |-> 0]]
  /\ sessaoDeTS = [r \in REPLICAS |-> [s \in SIDS |-> 0]]
  /\ contadorTS = [r \in REPLICAS |-> [id \in IDS |-> 0]]
  /\ statusTS   = [r \in REPLICAS |-> [id \in IDS |-> 0]]
```

### Broadcast (igual à Mudança 1, repetido aqui por completude)

```tla
Broadcast(origem, msg) ==
  [r2 \in REPLICAS |-> IF r2 = origem THEN canal[r2] ELSE Append(canal[r2], msg)]
```

### Login (versão final)

```tla
Login(r, id, s) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "SESSAO_VALIDA", chave |-> s, payload |-> id, valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p]
  IN
  /\ status[r][id] = "OPEN"
  /\ sessoes[r][s] = "NONE"
  /\ relogio'    = [relogio    EXCEPT ![r] = novoTS]
  /\ sessoes'    = [sessoes    EXCEPT ![r][s] = "VALID"]
  /\ sessaoDe'   = [sessaoDe   EXCEPT ![r][s] = id]
  /\ sessoesTS'  = [sessoesTS  EXCEPT ![r][s] = p]
  /\ sessaoDeTS' = [sessaoDeTS EXCEPT ![r][s] = p]
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ contador'   = contador
  /\ contadorTS' = contadorTS
  /\ status'     = status
  /\ statusTS'   = statusTS
  /\ canal'      = Broadcast(r, msg)
```

### Logout (versão final)

```tla
Logout(r, s) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "SESSAO_INVALIDA", chave |-> s, payload |-> "LOGOUT", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p]
  IN
  /\ sessoes[r][s] = "VALID"
  /\ relogio'   = [relogio   EXCEPT ![r] = novoTS]
  /\ sessoes'   = [sessoes   EXCEPT ![r][s] = "INVALID"]
  /\ motivos'   = [motivos   EXCEPT ![r][s] = "LOGOUT"]
  /\ sessoesTS' = [sessoesTS EXCEPT ![r][s] = p]
  /\ motivosTS' = [motivosTS EXCEPT ![r][s] = p]
  /\ sessaoDe'    = sessaoDe
  /\ sessaoDeTS'  = sessaoDeTS
  /\ contador'    = contador
  /\ contadorTS'  = contadorTS
  /\ status'      = status
  /\ statusTS'    = statusTS
  /\ canal'       = Broadcast(r, msg)
```

### IncrementFailure (versão final)

```tla
IncrementFailure(r, id) ==
  LET novo    == contador[r][id] + 1
      novoTS  == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "CONTADOR_INC", chave |-> id, payload |-> "", valor |-> novo,
               ts |-> novoTS, origem |-> r, prio |-> p]
  IN
  /\ contador[r][id] < LIMITE
  /\ relogio'    = [relogio    EXCEPT ![r] = novoTS]
  /\ contador'   = [contador   EXCEPT ![r][id] = novo]
  /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
  /\ IF novo >= LIMITE
       THEN /\ status'   = [status   EXCEPT ![r][id] = "BLOCKED"]
            /\ statusTS' = [statusTS EXCEPT ![r][id] = p]
       ELSE /\ status'   = status
            /\ statusTS' = statusTS
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ canal'      = Broadcast(r, msg)
```

### ResetFailures (versão final)

```tla
ResetFailures(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "CONTADOR_RESET", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p]
  IN
  /\ contador[r][id] # 0
  /\ relogio'    = [relogio    EXCEPT ![r] = novoTS]
  /\ contador'   = [contador   EXCEPT ![r][id] = 0]
  /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ status'     = status
  /\ statusTS'   = statusTS
  /\ canal'      = Broadcast(r, msg)
```

### Block (versão final)

```tla
Block(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      Afetado(s) == sessaoDe[r][s] = id /\ sessoes[r][s] = "VALID"
      msg == [tipo |-> "ID_BLOQUEIA", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p]
  IN
  /\ status[r][id] = "OPEN"
  /\ relogio'  = [relogio EXCEPT ![r] = novoTS]
  /\ status'   = [status  EXCEPT ![r][id] = "BLOCKED"]
  /\ statusTS' = [statusTS EXCEPT ![r][id] = p]
  /\ sessoes' = [sessoes EXCEPT ![r] =
                  [s \in SIDS |-> IF Afetado(s) THEN "INVALID" ELSE @[s]]]
  /\ motivos' = [motivos EXCEPT ![r] =
                  [s \in SIDS |-> IF Afetado(s) THEN "ADMIN_BLOCK" ELSE @[s]]]
  /\ sessoesTS' = [sessoesTS EXCEPT ![r] =
                    [s \in SIDS |-> IF Afetado(s) THEN p ELSE @[s]]]
  /\ motivosTS' = [motivosTS EXCEPT ![r] =
                    [s \in SIDS |-> IF Afetado(s) THEN p ELSE @[s]]]
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ contador'   = contador
  /\ contadorTS' = contadorTS
  /\ canal'      = Broadcast(r, msg)
```

### Unblock (versão final)

```tla
Unblock(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "ID_DESBLOQUEIA", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p]
  IN
  /\ status[r][id] = "BLOCKED"
  /\ relogio'    = [relogio    EXCEPT ![r] = novoTS]
  /\ status'     = [status     EXCEPT ![r][id] = "OPEN"]
  /\ statusTS'   = [statusTS   EXCEPT ![r][id] = p]
  /\ contador'   = [contador   EXCEPT ![r][id] = 0]
  /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ canal'      = Broadcast(r, msg)
```

### EntregaMensagem(r) — versão final, com LWW por campo (todos os 6 cases + OTHER)

Regra geral por *case*: se `m.prio` (prioridade da mensagem) for MAIOR que o TS local
do campo afetado, a escrita é aplicada e o TS local é atualizado para `m.prio`; caso
contrário, a mensagem é descartada (o local já tem uma escrita mais recente/vencedora)
e NADA muda. `relogio` nunca muda em `EntregaMensagem` (D-4).

```tla
EntregaMensagem(r) ==
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ UNCHANGED relogio
       /\ CASE m.tipo = "SESSAO_VALIDA" ->
                IF m.prio > sessoesTS[r][m.chave]
                  THEN /\ sessoes'    = [sessoes    EXCEPT ![r][m.chave] = "VALID"]
                       /\ sessaoDe'   = [sessaoDe   EXCEPT ![r][m.chave] = m.payload]
                       /\ sessoesTS'  = [sessoesTS  EXCEPT ![r][m.chave] = m.prio]
                       /\ sessaoDeTS' = [sessaoDeTS EXCEPT ![r][m.chave] = m.prio]
                       /\ UNCHANGED << motivos, motivosTS, contador, contadorTS,
                                        status, statusTS >>
                  ELSE UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                                     sessoesTS, motivosTS, sessaoDeTS,
                                     contadorTS, statusTS >>
          [] m.tipo = "SESSAO_INVALIDA" ->
                IF m.prio > sessoesTS[r][m.chave]
                  THEN /\ sessoes'   = [sessoes   EXCEPT ![r][m.chave] = "INVALID"]
                       /\ motivos'   = [motivos   EXCEPT ![r][m.chave] = m.payload]
                       /\ sessoesTS' = [sessoesTS EXCEPT ![r][m.chave] = m.prio]
                       /\ motivosTS' = [motivosTS EXCEPT ![r][m.chave] = m.prio]
                       /\ UNCHANGED << sessaoDe, sessaoDeTS, contador,
                                        contadorTS, status, statusTS >>
                  ELSE UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                                     sessoesTS, motivosTS, sessaoDeTS,
                                     contadorTS, statusTS >>
          [] m.tipo = "CONTADOR_INC" ->
                IF m.prio > contadorTS[r][m.chave]
                  THEN /\ contador'   = [contador   EXCEPT ![r][m.chave] = m.valor]
                       /\ contadorTS' = [contadorTS EXCEPT ![r][m.chave] = m.prio]
                       /\ IF m.valor >= LIMITE /\ m.prio > statusTS[r][m.chave]
                            THEN /\ status'   = [status   EXCEPT ![r][m.chave] = "BLOCKED"]
                                 /\ statusTS' = [statusTS EXCEPT ![r][m.chave] = m.prio]
                            ELSE /\ status'   = status
                                 /\ statusTS' = statusTS
                       /\ UNCHANGED << sessoes, motivos, sessaoDe,
                                        sessoesTS, motivosTS, sessaoDeTS >>
                  ELSE UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                                     sessoesTS, motivosTS, sessaoDeTS,
                                     contadorTS, statusTS >>
          [] m.tipo = "CONTADOR_RESET" ->
                IF m.prio > contadorTS[r][m.chave]
                  THEN /\ contador'   = [contador   EXCEPT ![r][m.chave] = 0]
                       /\ contadorTS' = [contadorTS EXCEPT ![r][m.chave] = m.prio]
                       /\ UNCHANGED << sessoes, motivos, sessaoDe, status,
                                        sessoesTS, motivosTS, sessaoDeTS, statusTS >>
                  ELSE UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                                     sessoesTS, motivosTS, sessaoDeTS,
                                     contadorTS, statusTS >>
          [] m.tipo = "ID_BLOQUEIA" ->
                IF m.prio > statusTS[r][m.chave]
                  THEN /\ status'   = [status   EXCEPT ![r][m.chave] = "BLOCKED"]
                       /\ statusTS' = [statusTS EXCEPT ![r][m.chave] = m.prio]
                       /\ UNCHANGED << sessoes, motivos, sessaoDe, contador,
                                        sessoesTS, motivosTS, sessaoDeTS, contadorTS >>
                  ELSE UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                                     sessoesTS, motivosTS, sessaoDeTS,
                                     contadorTS, statusTS >>
          [] m.tipo = "ID_DESBLOQUEIA" ->
                /\ IF m.prio > statusTS[r][m.chave]
                     THEN /\ status'   = [status   EXCEPT ![r][m.chave] = "OPEN"]
                          /\ statusTS' = [statusTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ status' = status /\ statusTS' = statusTS
                /\ IF m.prio > contadorTS[r][m.chave]
                     THEN /\ contador'   = [contador   EXCEPT ![r][m.chave] = 0]
                          /\ contadorTS' = [contadorTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ contador' = contador /\ contadorTS' = contadorTS
                /\ UNCHANGED << sessoes, motivos, sessaoDe,
                                 sessoesTS, motivosTS, sessaoDeTS >>
          [] OTHER -> UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                                     sessoesTS, motivosTS, sessaoDeTS,
                                     contadorTS, statusTS >>
```

Nota sobre `ID_DESBLOQUEIA`: `status` e `contador` são gateados de forma INDEPENDENTE
(cada um com seu próprio `IF`), mesmo os dois sendo produzidos por uma única mensagem
com um único `prio` — são registradores por campo distintos, coerente com D-1.

### Quiescent (igual à Mudança 1, repetido por completude)

```tla
Quiescent == \A r \in REPLICAS: Len(canal[r]) = 0
```

### Next, Spec, I1..I6 — NÃO MUDAM de corpo

`Next` continua a mesma disjunção de 7 ações (`Login`, `Logout`, `IncrementFailure`,
`ResetFailures`, `Block`, `Unblock`, `EntregaMensagem`), só que agora chamando as
versões novas dessas ações. `Spec == Init /\ [][Next]_vars` não muda. `I1`, `I2`, `I3`,
`I4`, `I5`, `I6` não mudam de corpo — só `Quiescent` mudou, e I5/I6 já chamavam
`Quiescent` como predicado.

### Documentação a sincronizar dentro do próprio `SessionStore.tla`

O cabeçalho do módulo (comentário nas primeiras linhas) e o comentário `THEOREM` no
final do arquivo (que hoje diz "para I5 e I6, contraexemplos sob entrelaçamento de
mensagens são esperados... base para os refinamentos previstos em TCC-II") ficam
**desatualizados** após esta correção e devem ser reescritos no mesmo commit da
Mudança 2, refletindo que:
- o canal agora é por réplica com broadcast real;
- há resolução de conflito LWW via relógio lógico (Lamport) com desempate por réplica;
- I5/I6 são esperados fechar sob quiescência nas cardinalidades onde o TLC conseguir
  fechar exaustivamente (ver critérios de aceite).

### MCSession.tla — versão final completa

```tla
---------------------------- MODULE MCSession ----------------------------
EXTENDS SessionStore, TLC

CONSTANT MaxCanal,    \* teto para o comprimento de CADA fila canal[r]
         MaxRelogio   \* teto para o valor de CADA relogio logico relogio[r]

LimiteCanal   == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal
LimiteRelogio == \A r \in REPLICAS: relogio[r] =< MaxRelogio

(* IDS e SIDS permanecem intercambiaveis; REPLICAS NAO MAIS, pois a partir  *)
(* da resolucao de conflito LWW (SessionStore.tla, RepIndex/Prio), a       *)
(* identidade da replica de origem passa a influenciar o resultado do      *)
(* desempate -- a especificacao deixou de ser simetrica em REPLICAS.       *)
(* Remover Permutations(REPLICAS) preserva a SOUNDNESS da reducao (ver     *)
(* docs/plano-fix-broadcast-lww-2026-09-16.md, decisao D-3).               *)
Simetria == Permutations(IDS) \cup Permutations(SIDS)
==========================================================================
```

---

## Plano de execução

### Passo 1 — branch `fix/canal-broadcast-por-replica` (só Mudança 1)

A partir do ponto organizado no Passo -1 (`docs/vm-hyperv-netem` já com os dois commits
de organização feitos):

```bash
git checkout -b fix/canal-broadcast-por-replica
```

Aplicar só a Mudança 1 em `spec/SessionStore.tla` (canal por réplica, `Broadcast`,
`EntregaMensagem` sem LWW, `Quiescent`) e `spec/MCSession.tla` (`LimiteCanal`
generalizado, sem tocar em `Simetria` ainda — não há LWW nesta etapa).

```bash
git add spec/SessionStore.tla spec/MCSession.tla
git commit -m "fix: broadcast de mensagens para todas as replicas destino no canal assincrono"
```

Rodar (com JDK 22 no PATH, Passo 0):
```bash
./run-tlc-invariantes.sh tiny 2
./run-tlc-invariantes.sh small 2
```

Registrar os vereditos brutos. Esperado: I1/I2 continuam violando; I5/I6 podem fechar
em `tiny` mas devem continuar violando em `small` (ver "Checkpoint empírico" acima).

### Passo 2 — branch `feature/lww-relogio-logico` (Mudança 2 completa)

A partir da ponta de `fix/canal-broadcast-por-replica` (incremental, não de `main` nem
de `docs/vm-hyperv-netem` diretamente):

```bash
git checkout -b feature/lww-relogio-logico
```

Aplicar a Mudança 2 completa em `spec/SessionStore.tla` (variáveis novas,
`RepIndex`/`Prio`, `Mensagem` com `ts`/`origem`/`prio`/`valor`, `TypeOK`, `Init`, as 6
ações reescritas, `EntregaMensagem` com LWW, atualização de comentários) e
`spec/MCSession.tla` (`MaxRelogio`, `LimiteRelogio`, `Simetria` sem `REPLICAS`).

Também atualizar `spec/run-tlc-invariantes.sh` com dois parâmetros novos:
- `MaxRelogio` como 3º argumento posicional (default `4`).
- `TIMEOUT_SECS` como variável de ambiente opcional (default vazio = sem timeout),
  envolvendo a chamada do `java` com `timeout "${TIMEOUT_SECS}s" ...` quando definida.
  Se o utilitário `timeout` não existir no Git Bash local, alternativa: rodar em
  background e encerrar manualmente o processo `java` após o prazo.

O heredoc do `.cfg` gerado pelo script passa a incluir:
```
CONSTANTS
    IDS      = $IDS
    SIDS     = $SIDS
    REPLICAS = $REPS
    LIMITE   = $LIM
    MaxCanal   = $MAXCANAL
    MaxRelogio = $MAXRELOGIO
SYMMETRY Simetria
CONSTRAINT
    LimiteCanal
    LimiteRelogio
INVARIANTS
    $INV
```

```bash
git add spec/SessionStore.tla spec/MCSession.tla spec/run-tlc-invariantes.sh
git commit -m "feat: resolucao de conflito last-writer-wins via relogio logico de Lamport"
```

### Passo 3 — matriz de cardinalidades e timebox

| Config | IDS | SIDS | REPLICAS | LIMITE | MaxCanal | MaxRelogio | Timebox |
|---|---|---|---|---|---|---|---|
| `tiny`   | 1 | 2 | 2 | 2 | 2 | 4 | sem timeout (deve fechar exaustivo) |
| `small`  | 2 | 3 | 2 | 3 | 2 | 4 | sem timeout, mas se passar de ~15 min, aplicar timebox e reportar parcial |
| `medium` | 3 | 3 | 2 | 3 | 2 | 4 | 20 min (`TIMEOUT_SECS=1200`) |
| `full`   | 3 | 3 | 3 | 3 | 2 | 4 | 20 min (`TIMEOUT_SECS=1200`) |

```bash
./run-tlc-invariantes.sh tiny 2 4
./run-tlc-invariantes.sh small 2 4
TIMEOUT_SECS=1200 ./run-tlc-invariantes.sh medium 2 4
TIMEOUT_SECS=1200 ./run-tlc-invariantes.sh full 2 4
```

Se `tiny`/`small` não fecharem em tempo razoável (o espaço de estados cresce bastante
com as 6 variáveis novas), reduzir `MaxRelogio` para 3 e/ou `MaxCanal` para 1,
documentando os valores efetivamente usados — mesmo espírito de transparência do
relatório de 09/07 sobre `MaxCanal`.

Para os casos com timeout atingido, o classificador do script já cai no ramo
"inconclusivo" (não bate nem `is violated` nem `No error has been found`). Extrair do
`.out` a última linha `Progress(...)` (estados gerados/distintos até o corte) para
reportar o número parcial no relatório, sem inventar veredito.

### Passo 4 — relatório final

Escrever `docs/relatorio-tlc-<data-real-da-execucao>.md` (formato AAAA-MM-DD, data em
que o Worker efetivamente rodar — não necessariamente 2026-09-16), no mesmo espírito de
`docs/relatorio-tlc-2026-07-09.md`:
- Entradas (specs/configs/versão do TLC).
- Processamento (diagrama Mermaid, tema branco, atualizado para incluir
  broadcast/LWW).
- Matriz de vereditos por invariante e cardinalidade.
- Contraexemplos completos de qualquer invariante que ainda viole.
- Conclusão honesta comparando com a expectativa.

---

## Critérios de aceite

1. **I1 e I2 continuam VIOLADOS** em `tiny` e `small`, com contraexemplo curto (mesma
   natureza do relatório anterior — atraso de propagação, não é isso que estamos
   corrigindo). Se passarem a fechar, é sinal de regressão indevida e deve ser
   investigado antes de aceitar o resultado.
2. **I5 e I6 fecham sem contraexemplo** em `small` (2 identidades — a cardinalidade que
   realmente exercita a corrida entre identidades concorrentes) após a Mudança 2. Se
   ainda violarem em `small` após a Mudança 2, isso deve ser investigado e reportado
   com o contraexemplo completo, não escondido.
3. **Nuance esperada, não é bug:** é plausível que `tiny` já feche I5/I6 logo após a
   Mudança 1 isolada (Passo 1), porque `tiny` só tem 1 identidade e não representa o
   cenário de duas identidades disputando a mesma sessão. Isso não invalida a
   necessidade da Mudança 2 — o teste decisivo é `small`.
4. **TypeOK, I3, I4 continuam OK** (nenhuma regressão de domínio) em todas as
   cardinalidades que fecharem.
5. `medium`/`full`: reportar veredito se fechar dentro do timebox de 20 min; caso
   contrário, reportar estados gerados/distintos até o corte, sem alegar veredito.
6. O relatório final deve conter, no mínimo: entradas, processamento, matriz de
   vereditos por invariante e cardinalidade, contraexemplos completos de qualquer
   invariante que ainda viole, e conclusão honesta.
7. Nome do arquivo do relatório:
   `tcc-desenvolvimento/docs/relatorio-tlc-<data-real-da-execucao>.md`.

---

## Pontos que precisam de validação do Gestor

- **D-1 (granularidade dos registradores LWW):** um relógio por campo (`sessoesTS`,
  `motivosTS`, `sessaoDeTS`, `contadorTS`, `statusTS`) em vez de um único relógio por
  chave/registro composto. É a escolha que garante prova de convergência por campo;
  adiciona 6 variáveis de estado novas ao todo (incluindo `relogio`).
- **D-2 (a mais debatível):** `contador` tratado como LWW-register (valor absoluto,
  "última escrita vence") em vez de contador CRDT op-based. Sob concorrência,
  incrementos concorrentes podem não somar ambos — converge, mas não necessariamente
  "conta certo". Alternativa mais fiel (PN-Counter CRDT) é mais complexa e fica fora do
  escopo pedido; sugiro deixá-la como nota de trabalho futuro (TCC-II).
- **D-3 (soundness):** remover `Permutations(REPLICAS)` de `Simetria` em
  `MCSession.tla` é **obrigatório** para manter a verificação válida (não é opcional) —
  mas reduz o corte por simetria, especialmente em `full` (REPLICAS=3, fator 6
  perdido), podendo tornar `full` ainda mais difícil de fechar no timebox.
- **D-4:** relógio lógico não faz merge-on-receive (só avança em operação local,
  conforme pedido literalmente) — decisão de simplicidade, documentar a diferença
  frente a um Lamport clock clássico completo.
- **Passo -1 (organização do repositório):** os dois commits propostos em
  `docs/vm-hyperv-netem` (cluster/VM separado dos artefatos de TLC de 09/07) precisam
  de aceite antes de criar as branches novas — confirmar que essa é a forma desejada de
  preservar o trabalho pendente, em vez de, por exemplo, descartar ou espremer tudo num
  commit só.
- Não incluí atualização de `docs/spec-formal.md` (que o relatório de 09/07 já
  sinalizou como desatualizado quanto às expectativas de I1-I4) neste plano — mantive o
  escopo estritamente na spec TLA+ e no novo relatório, por ser território que pode
  interessar aos agentes acadêmicos. Aviso, não decido.
- Estratégia de duas branches sequenciais com checkpoint intermediário em `tiny`/`small`
  entre Mudança 1 e Mudança 2 — adiciona uma rodada de TLC extra, mas produz um dado
  empírico valioso (prova que broadcast sozinho não basta). Confirmar que vale o tempo
  extra antes de acionar o Worker.

---

## Próxima tarefa sugerida para o Worker

1. Executar o Passo -1 completo (dois commits de organização em
   `docs/vm-hyperv-netem`, depois criar `fix/canal-broadcast-por-replica` a partir
   dali).
2. Aplicar a Mudança 1 completa, commitar, rodar `run-tlc-invariantes.sh tiny 2` e
   `small 2` (com JDK 22 no PATH — Passo 0) e registrar os vereditos brutos.
3. Criar `feature/lww-relogio-logico` a partir da branch anterior, aplicar a Mudança 2
   completa, atualizar `run-tlc-invariantes.sh` (`MaxRelogio`, `TIMEOUT_SECS`),
   commitar.
4. Rodar a matriz completa (`tiny`, `small`, `medium` com timebox 20 min, `full` com
   timebox 20 min) e escrever `docs/relatorio-tlc-<data>.md`.
5. Reportar tudo ao Gestor e ao Senior antes de qualquer push para `origin/*` —
   nenhuma das branches deve ir ao GitHub sem aprovação explícita.
