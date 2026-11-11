# Plano — Mudança 3: cascata de `Block` propagada (I5) + status órfão de `contador` (I6)

> Elaborado pelo Senior em 2026-09-16, para execução pelo Worker. **Este documento é o
> plano completo** — não foi implementado nem executado nada aqui, é especificação de
> trabalho. Dá continuidade a `docs/plano-fix-broadcast-lww-2026-09-16.md` (Mudanças
> 1/2) e ao estado real registrado em `docs/relatorio-tlc-2026-09-16.md` (Mudanças
> 2b/2c, causas residuais em §3.2 e §3.3). Ler os dois antes de executar este plano —
> este documento pressupõe o `spec/SessionStore.tla` tal como ele está hoje, na ponta
> de `fix/lww-gating-escrita-local` (lido integralmente antes de escrever este plano).

---

## 1. Contexto

Após Mudanças 1+2+2b+2c, `I5` e `I6` ainda violam sob quiescência, por duas causas
residuais **diferentes** entre si, cada uma com contraexemplo completo documentado:

- **Bug B (I5, relatório §3.2):** `Block(r,id)` invalida sessões em cascata
  **localmente** (`sessaoDe[r][s]=id ∧ sessoes[r][s]=VALID` → `INVALID`/`ADMIN_BLOCK`),
  mas a mensagem `ID_BLOQUEIA` propagada só carrega `chave=id`; o *case* `ID_BLOQUEIA`
  de `EntregaMensagem` só atualiza `status` na réplica remota, nunca reaplica o
  cascade sobre `sessoes`/`motivos`/`sessaoDeTS`/`sessoesTS`/`motivosTS`. Resultado:
  `status` converge, `sessoes`/`motivos` da(s) sessão(ões) afetada(s) não convergem.
- **I6 (relatório §3.3):** um `status=BLOCKED` disparado localmente (ou via
  `EntregaMensagem`, *case* `CONTADOR_INC`) quando `contador` cruza `LIMITE` fica
  "órfão" se esse valor específico de `contador` for depois superado por LWW
  (`CONTADOR_RESET` de prioridade maior vindo de outra réplica) — `status` nunca é
  revertido, porque nada além de `Unblock`/`ID_DESBLOQUEIA` toca `status` fora do
  próprio evento que cruzou o limite.

O pedido do Gestor é corrigir as duas causas de fato (não documentar como limitação) e
rodar o TLC até `I5`/`I6` fecharem sem contraexemplo. O Gestor também propôs duas rotas
concretas para cada causa e pediu avaliação/decisão fundamentada. Este plano faz essa
avaliação abaixo — a decisão para I5 confirma a proposta do Gestor; a decisão para I6
**diverge das duas rotas propostas**, com justificativa textual apoiada no Cap. 3 da
monografia (ver §3).

---

## 2. Decisão — Bug B / I5: mensagem `ID_BLOQUEIA` carrega o conjunto de sessões afetadas

**Decisão: aceita a proposta do Gestor, como está.** A mensagem `ID_BLOQUEIA` passa a
carregar um campo novo `sessoesAfetadas: SUBSET SIDS`, calculado na origem no momento
do `Block` exatamente como o `Afetado(s)` que `Block` já usa para sua própria escrita
local (`{s \in SIDS : sessaoDe[r][s] = id /\ sessoes[r][s] = "VALID"}`). Na entrega,
`EntregaMensagem` passa a reaplicar, POR SESSÃO em `m.sessoesAfetadas`, o mesmo padrão
de *gate* independente por campo que `SESSAO_INVALIDA` já usa (Mudança 2c) — um `IF`
para `sessoes`/`sessoesTS` gateado por `m.prio > sessoesTS[r][s]`, outro `IF`
independente para `motivos`/`motivosTS` gateado por `m.prio > motivosTS[r][s]`.

### 2.1 Viabilidade em TLA+ (confirmada por precedente já existente no próprio arquivo)

O padrão `[campo EXCEPT ![r] = [s \in SIDS |-> IF <condição por s> THEN <valor> ELSE
@[s]]]` **já é usado exatamente assim** na própria ação `Block` local, em
`spec/SessionStore.tla` linhas 277–288 (leitura direta confirmada nesta sessão):

```tla
/\ sessoes' = [sessoes EXCEPT ![r] =
                [s \in SIDS |-> IF Afetado(s) /\ p > sessoesTS[r][s]
                                THEN "INVALID" ELSE @[s]]]
```

Ou seja: não é uma construção nova, é a mesma técnica já validada pelo TLC nesta
especificação, só reaplicada em `EntregaMensagem` sobre `m.sessoesAfetadas` em vez de
`Afetado(s)`. Não há necessidade de quebrar `ID_BLOQUEIA` em uma sequência de
mensagens — um único campo `SUBSET SIDS` basta.

### 2.2 Custo de espaço de estados — por que isto NÃO reabre o problema de explosão do §1 do relatório

`sessoesAfetadas` é um campo **totalmente determinado pelo estado corrente** no
momento da construção da mensagem (`{s \in SIDS : Afetado(s)}`), não uma escolha
não-determinística nova em `Next`. Diferente das 6 variáveis de estado novas da
Mudança 2 (que multiplicaram o espaço de estados combinatório porque cada uma podia
assumir muitos valores em muitos estados *alcançáveis*), este campo não adiciona
nenhum `\E` novo a nenhuma ação — só aumenta o "formato" de um valor já determinístico
dentro de cada mensagem `ID_BLOQUEIA` construída. O custo extra é desprezível
(comparação estrutural de um campo a mais em igualdade de registros), não
combinatório. `MaxCanal=1`/`MaxRelogio=3` (baseline já estabelecido em
2026-09-16) deve continuar suficiente; não se espera repetir a redução de parâmetros
que a Mudança 2 exigiu.

### 2.3 Diffs completos

**`Mensagem` (adiciona um campo):**

```tla
Mensagem ==
  [tipo: {"SESSAO_VALIDA", "SESSAO_INVALIDA",
          "CONTADOR_INC", "CONTADOR_RESET",
          "ID_BLOQUEIA", "ID_DESBLOQUEIA"},
   chave: SIDS \cup IDS,
   payload: STRING \cup IDS,
   valor: 0..LIMITE,
   ts: Nat,
   origem: REPLICAS,
   prio: Nat,
   sessoesAfetadas: SUBSET SIDS]  \* NOVO (Mudanca 3a) -- usado so por ID_BLOQUEIA,
                                   \* cascade do Block calculado na origem; demais
                                   \* tipos de mensagem usam {}.
```

`TypeOK` **não muda de corpo** — `canal \in [REPLICAS -> Seq(Mensagem)]` já cobre a
forma nova de `Mensagem` automaticamente.

**As 5 ações que NÃO são `Block` — adicionar `sessoesAfetadas |-> {}` ao `msg ==`
de cada uma** (`Login`, `Logout`, `IncrementFailure`, `ResetFailures`, `Unblock`; só
o campo novo muda, resto de cada ação permanece idêntico ao que está hoje em
`spec/SessionStore.tla`). Exemplo (`Login`):

```tla
msg == [tipo |-> "SESSAO_VALIDA", chave |-> s, payload |-> id, valor |-> 0,
         ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
```

(idem, mesmo acréscimo, para os `msg ==` de `Logout`, `IncrementFailure`,
`ResetFailures`, `Unblock` — nenhum outro trecho dessas 5 ações muda.)

**`Block` — só o `msg ==` do `LET` muda** (o resto do corpo de `Block`, incluindo o
próprio `Afetado(s)` e a escrita local já gateada por campo, permanece idêntico):

```tla
Block(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      Afetado(s) == sessaoDe[r][s] = id /\ sessoes[r][s] = "VALID"
      msg == [tipo |-> "ID_BLOQUEIA", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p,
               sessoesAfetadas |-> {s \in SIDS : Afetado(s)}]   \* NOVO
  IN
  /\ status[r][id] = "OPEN"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > statusTS[r][id]
       THEN /\ status'   = [status   EXCEPT ![r][id] = "BLOCKED"]
            /\ statusTS' = [statusTS EXCEPT ![r][id] = p]
       ELSE /\ status' = status /\ statusTS' = statusTS
  /\ sessoes' = [sessoes EXCEPT ![r] =
                  [s \in SIDS |-> IF Afetado(s) /\ p > sessoesTS[r][s]
                                  THEN "INVALID" ELSE @[s]]]
  /\ motivos' = [motivos EXCEPT ![r] =
                  [s \in SIDS |-> IF Afetado(s) /\ p > motivosTS[r][s]
                                  THEN "ADMIN_BLOCK" ELSE @[s]]]
  /\ sessoesTS' = [sessoesTS EXCEPT ![r] =
                    [s \in SIDS |-> IF Afetado(s) /\ p > sessoesTS[r][s]
                                    THEN p ELSE @[s]]]
  /\ motivosTS' = [motivosTS EXCEPT ![r] =
                    [s \in SIDS |-> IF Afetado(s) /\ p > motivosTS[r][s]
                                    THEN p ELSE @[s]]]
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ contador'   = contador
  /\ contadorTS' = contadorTS
  /\ canal'      = Broadcast(r, msg)
```

Nota: o campo `sessoesAfetadas` carrega o `Afetado(s)` **bruto** (não filtrado pelo
`p > sessoesTS[r][s]`/`p > motivosTS[r][s]` que gateia a escrita LOCAL). Isso é
proposital e necessário para a convergência: o que a mensagem propaga é o **fato**
"na prioridade `p`, a réplica `r` observou que a sessão `s` estava VALID para `id` e
a invalidou por bloqueio" — cabe a CADA réplica (inclusive a de origem, para a própria
escrita local) decidir independentemente, comparando `p` contra o próprio TS local de
cada campo, se aceita esse fato. É exatamente o mesmo argumento de LWW-register que já
vale para todo o resto da especificação (Mudança 2/2c) — não introduz um mecanismo
novo, só estende o padrão já demonstrado a um terceiro tipo de mensagem.

**`EntregaMensagem`, *case* `ID_BLOQUEIA` — reescrito por completo:**

```tla
[] m.tipo = "ID_BLOQUEIA" ->
      /\ IF m.prio > statusTS[r][m.chave]
           THEN /\ status'   = [status   EXCEPT ![r][m.chave] = "BLOCKED"]
                /\ statusTS' = [statusTS EXCEPT ![r][m.chave] = m.prio]
           ELSE /\ status' = status /\ statusTS' = statusTS
      /\ sessoes' = [sessoes EXCEPT ![r] =
                      [s \in SIDS |-> IF s \in m.sessoesAfetadas /\ m.prio > sessoesTS[r][s]
                                      THEN "INVALID" ELSE @[s]]]
      /\ motivos' = [motivos EXCEPT ![r] =
                      [s \in SIDS |-> IF s \in m.sessoesAfetadas /\ m.prio > motivosTS[r][s]
                                      THEN "ADMIN_BLOCK" ELSE @[s]]]
      /\ sessoesTS' = [sessoesTS EXCEPT ![r] =
                        [s \in SIDS |-> IF s \in m.sessoesAfetadas /\ m.prio > sessoesTS[r][s]
                                        THEN m.prio ELSE @[s]]]
      /\ motivosTS' = [motivosTS EXCEPT ![r] =
                        [s \in SIDS |-> IF s \in m.sessoesAfetadas /\ m.prio > motivosTS[r][s]
                                        THEN m.prio ELSE @[s]]]
      /\ UNCHANGED << sessaoDe, sessaoDeTS, contador, contadorTS >>
```

(Antes: `status'`/`statusTS'` gateados por `m.prio > statusTS[...]`, com
`UNCHANGED << sessoes, motivos, sessaoDe, contador, sessoesTS, motivosTS, sessaoDeTS,
contadorTS >>` no ramo positivo e tudo `UNCHANGED` no ramo negativo. Depois: `status`
continua gateado exatamente igual — isso não muda — mas `sessoes`/`motivos` deixam de
estar em `UNCHANGED` incondicional e passam a ser escritos por sessão, gateados
independentemente por `sessoesTS`/`motivosTS`, cada um comparado ao mesmo `m.prio` da
mensagem.)

Os demais 5 *cases* de `EntregaMensagem` (`SESSAO_VALIDA`, `SESSAO_INVALIDA`,
`CONTADOR_RESET`, `ID_DESBLOQUEIA`, `OTHER`) **não mudam** nesta parte do plano —
`CONTADOR_INC` muda, mas por outro motivo, coberto em §3.

---

## 3. Decisão — I6: rejeita as duas rotas propostas pelo Gestor, propõe Rota (c)

### 3.1 Por que a Rota (a) do Gestor (ResetFailures também desbloqueia) é **rejeitada**

A Rota (a) faria `ResetFailures`/`CONTADOR_RESET` também escrever `status="OPEN"`,
gateado por `statusTS`. O próprio Gestor já sinalizou o risco: "isso muda o protocolo
para 'resetar o contador sempre desbloqueia'". Fui checar contra a definição
**normativa** da operação O5 no Cap. 3 da monografia (`tcc-escrita/Metodologia.tex`,
linha 74, seção "Operações modeladas", lida integralmente nesta sessão — só leitura,
nenhuma edição):

> "**O5, reposição de tentativas.** Zera o contador da identidade $u$ após sucesso de
> *login* dentro da janela. Efeito: $\beta'(u).\textsf{counter} = 0$, **sem alterar a
> marca de bloqueio**."

Isso é uma regra de negócio explícita e normativa (não uma opção de modelagem em
aberto): O5 **nunca** deve alterar a marca de bloqueio, mesmo quando ela foi
provocada por O4 (limite de tentativas). A Rota (a) violaria essa regra diretamente
— não é só "arriscada", é **incorreta** frente à especificação de operações que a
própria monografia já formaliza. Rejeitada.

### 3.2 Por que a Rota (b) do Gestor (status derivado de `contador \/ adminBlocked`), como
### proposta, também é rejeitada — mesmo motivo, por um caminho diferente

A Rota (b) sugere separar `adminBlocked[r][id]` (só tocado por `Block`/`Unblock`) de
um `status` **derivado** por `IF contador[r][id] >= LIMITE \/ adminBlocked[r][id]
THEN "BLOCKED" ELSE "OPEN"`. À primeira vista isso parece resolver o problema (e
resolve, de fato, a parte de **misturar bloqueio administrativo com bloqueio por
tentativas** — essa parte da intuição do Gestor está certa). Mas ao analisar o efeito
completo: se `status` é **inteiramente derivado** de `contador`, então **qualquer**
operação que reduza `contador[r][id]` abaixo de `LIMITE` — inclusive `ResetFailures`,
que só toca `contador`, nunca `adminBlocked` — automaticamente "desbloqueia" a
identidade de fato (mesmo sem tocar `adminBlocked`), porque a fórmula derivada
recalcula `Bloqueado` sempre que `contador` muda. Isso é a **mesma violação da regra
de O5** ("sem alterar a marca de bloqueio"), só que produzida indiretamente pela
derivação, em vez de por uma escrita explícita. Rejeitada, com o mesmo fundamento do
§3.1 — mas o diagnóstico da Rota (b) (separar as duas causas de bloqueio) é o dado
que leva à Rota (c) abaixo.

### 3.3 Causa raiz real (revisitada) e Rota (c) proposta — decoupling do *gate* de `status`
### em `EntregaMensagem`, *case* `CONTADOR_INC`

Ao invés de mudar QUALQUER precondição de `Login`/`Block`/`Unblock`, QUALQUER
variável de estado, ou o efeito de `ResetFailures` (que já está certo e não deve
mudar), a causa raiz genuína de I6 é a MESMA classe de bug que a Mudança 2c já
corrigiu para `SESSAO_VALIDA`/`SESSAO_INVALIDA`: **um `IF` externo gateando
incorretamente dois campos LWW independentes sob um único teste**.

Em `EntregaMensagem`, o *case* `CONTADOR_INC` de hoje (`spec/SessionStore.tla`, linhas
369–382, lidas nesta sessão) tem esta forma:

```tla
[] m.tipo = "CONTADOR_INC" ->
      IF m.prio > contadorTS[r][m.chave]          \* gate EXTERNO, só do campo contador
        THEN /\ contador'   = ...
             /\ contadorTS' = ...
             /\ IF m.valor >= LIMITE /\ m.prio > statusTS[r][m.chave]
                  THEN status' BLOCKED, statusTS' m.prio
                  ELSE status'/statusTS' inalterados
             /\ UNCHANGED << sessoes, ... >>
        ELSE UNCHANGED << sessoes, motivos, sessaoDe, contador, status,
                           sessoesTS, motivosTS, sessaoDeTS, contadorTS, statusTS >>
```

O ramo `ELSE` do `IF` externo (`m.prio > contadorTS[r][m.chave]` falso) deixa **tudo**
inalterado — inclusive `status`, mesmo que `status` tenha seu **próprio** TS
(`statusTS`) que poderia justificar a aplicação da atualização por seus próprios
termos. É exatamente a mesma classe de acoplamento indevido entre dois registradores
independentes que a Mudança 2c corrigiu para `sessoes`+`sessaoDe`/`sessoes`+`motivos`.

**Por que isso É seguro desacoplar no caso remoto (`EntregaMensagem`), mas NÃO seria
seguro desacoplar no caso local (`IncrementFailure`)** — distinção importante,
verificada nesta sessão antes de propor a mudança:

- No caso **remoto**, `m.valor` e `m.prio` são um **fato histórico já consumado** na
  réplica de origem (`m.origem` realmente teve `contador = m.valor` em algum ponto do
  seu histórico causal, e realmente cruzou `LIMITE` naquele instante, com aquela
  prioridade). Isso é verdade **independentemente** de esta réplica receptora aceitar
  ou não esse valor para o SEU campo `contador` local (pode já ter algo de prioridade
  ainda maior). O fato "este identidade cruzou o limite na prioridade `m.prio`"
  merece ser avaliado pelos próprios termos de `statusTS`, exatamente como
  `ID_BLOQUEIA` já faz hoje (o bloqueio administrativo nunca dependeu de nenhum outro
  campo vencer sua própria corrida de LWW).
- No caso **local** (`IncrementFailure`), `novo = contador[r][id] + 1` só é um valor
  "real" **se a própria escrita em `contador` for aceita** (`p > contadorTS[r][id]`)
  — se essa escrita local perder a corrida de LWW contra um valor já recebido de
  outra réplica, `novo` nunca chega a ser o valor verdadeiro de `contador` em réplica
  NENHUMA (nem localmente): seria uma "leitura fantasma" que nunca se torna estado
  real. Aplicar `status` a partir de um `novo` descartado seria bloquear por um valor
  que nunca existiu de fato. Por isso o acoplamento ATUAL de `IncrementFailure`
  (local) entre a escrita de `contador` e o efeito em `status` está **correto** e
  **não deve mudar**.

Essa assimetria (mensagem remota = fato autocontido e já consumado; escrita local =
condicional à própria aceitação) é o mesmo raciocínio que já justifica, na
especificação atual, por que `ID_BLOQUEIA`/`ID_DESBLOQUEIA` gateiam `status` de forma
totalmente independente de qualquer outro campo. `CONTADOR_INC` é a única mensagem
que ainda não seguia esse padrão — a Rota (c) só estende o padrão já estabelecido.

### 3.4 Verificação do contraexemplo do relatório (§3.3) com a Rota (c) aplicada

Usando exatamente a sequência do contraexemplo documentado (`tiny`, `LIMITE=2`):

| Estado | Ação | `contadorTS[r1]` | `statusTS[r1]` | `status[r1]` | `contador[r2]` | `status[r2]` |
|---|---|---|---|---|---|---|
| 4 | `ResetFailures(r1,u1)`, `p=7` | 7 | 0 | OPEN | 1 | OPEN |
| 5 | `IncrementFailure(r2,u1)`, `novo=2≥LIMITE`, `p=5` | 7 | 0 | OPEN | **2** | **BLOCKED** (`statusTS[r2]=5`) |
| 6 | `EntregaMensagem(r1)` recebe `CONTADOR_INC,valor=2,prio=5` — **com a Rota (c)**: `contador` continua descartado (`5 ≤ contadorTS[r1]=7`), MAS `status` é avaliado **independentemente**: `2≥LIMITE ∧ 5 > statusTS[r1]=0` → **`status[r1] := BLOCKED`, `statusTS[r1] := 5`** | 7 | **5** | **BLOCKED** | 2 | BLOCKED |
| 7 | `EntregaMensagem(r2)` recebe `CONTADOR_RESET,prio=7` — `contador[r2] := 0` (`7>contadorTS[r2]=5`); `CONTADOR_RESET` não toca `status` (correto, O5) | 7 | 5 | BLOCKED | **0** | **BLOCKED** |

Estado final (quiescente): `contador[r1]=0`, `contador[r2]=0` (convergiu, como já
convergia antes), `status[r1]=BLOCKED`, `status[r2]=BLOCKED` (**convergiu** — antes
divergia: `r1=OPEN`, `r2=BLOCKED`). `I6` deixa de violar neste traço.

### 3.5 Diff completo — só o *case* `CONTADOR_INC` de `EntregaMensagem` muda

```tla
[] m.tipo = "CONTADOR_INC" ->
      /\ IF m.prio > contadorTS[r][m.chave]
           THEN /\ contador'   = [contador   EXCEPT ![r][m.chave] = m.valor]
                /\ contadorTS' = [contadorTS EXCEPT ![r][m.chave] = m.prio]
           ELSE /\ contador' = contador /\ contadorTS' = contadorTS
      /\ IF m.valor >= LIMITE /\ m.prio > statusTS[r][m.chave]
           THEN /\ status'   = [status   EXCEPT ![r][m.chave] = "BLOCKED"]
                /\ statusTS' = [statusTS EXCEPT ![r][m.chave] = m.prio]
           ELSE /\ status' = status /\ statusTS' = statusTS
      /\ UNCHANGED << sessoes, motivos, sessaoDe, sessoesTS, motivosTS, sessaoDeTS >>
```

(Antes: um único `IF m.prio > contadorTS[...]` externo decidia `contador` E `status`
juntos, com `ELSE UNCHANGED` cobrindo os dois. Depois: dois `IF` independentes ao
nível do `/\`, cada um comparando `m.prio` contra o TS do SEU PRÓPRIO campo — mesmo
padrão que `ID_DESBLOQUEIA` já usa para `status`/`contador`, e que `SESSAO_VALIDA`/
`SESSAO_INVALIDA` já usam para `sessoes`/`sessaoDe`/`motivos`, desde a Mudança 2c.)

**Nenhuma outra ação, precondição, variável de estado ou mensagem muda por causa da
Rota (c).** `IncrementFailure` (local), `ResetFailures`/`CONTADOR_RESET`,
`Login`/`Block`/`Unblock` e suas precondições ficam **exatamente como estão hoje**.
Não há variável nova, não há campo novo em `Mensagem`, não há mudança em `MCSession.tla`
(nem `Simetria`, nem `LimiteCanal`/`LimiteRelogio`) — o `MaxCanal`/`MaxRelogio` do
baseline de 2026-09-16 (1/3) não deve precisar de ajuste, já que Rota (c) não
acrescenta nenhuma dimensão nova ao espaço de estados (é reordenação de `IF`s dentro
de um *case* já existente).

### 3.6 Cobertura: isto fecha I6 em geral, ou só o contraexemplo específico?

O argumento do §3.3 acima generaliza porque `status[r][id]` passa a ter exatamente
três fontes de escrita, cada uma comparando a mesma prioridade `p` contra o mesmo
`statusTS[r][id]`, de forma totalmente desacoplada de qualquer outro campo — a
condição clássica de convergência de um LWW-register (Shapiro et al. 2011, já citado
no trabalho): (1) `IncrementFailure` local, quando a própria escrita de `contador`
vence (`p > contadorTS[r][id]`) E cruza `LIMITE`; (2) `EntregaMensagem` de
`CONTADOR_INC`, agora desacoplada (Rota (c)), quando `m.valor ≥ LIMITE`; (3)
`Block`/`Unblock` (local) e `ID_BLOQUEIA`/`ID_DESBLOQUEIA` (remoto), já corretamente
desacopladas desde sempre. `ResetFailures`/`CONTADOR_RESET` nunca escrevem `status`,
em nenhuma das quatro vias — coerente com O5. Isso não é uma prova formal (o TLC é
quem vai confirmar exaustivamente nas cardinalidades tratáveis), mas é o argumento de
por que se espera fechamento geral, não só do traço específico do relatório — a
mesma expectativa que o plano anterior já registrava para as correções 2b/2c antes de
serem confirmadas pelo TLC.

---

## 4. Sincronização de documentação dentro do próprio `SessionStore.tla`

O comentário de cabeçalho do módulo (linhas 1–31) e o comentário `THEOREM` final
(linhas 489–500) devem ser atualizados no mesmo espírito das mudanças anteriores,
registrando: (a) o cascade de `Block` agora é propagado via `sessoesAfetadas` na
mensagem `ID_BLOQUEIA`; (b) o efeito de bloqueio por `CONTADOR_INC` agora é gateado
independentemente do campo `contador` em `EntregaMensagem`, alinhado ao padrão já
usado por `ID_BLOQUEIA`/`ID_DESBLOQUEIA`; (c) a expectativa passa a ser `I5`/`I6`
fechando sem contraexemplo sob quiescência nas cardinalidades tratáveis, não mais
"contraexemplos residuais documentados".

---

## 5. Plano de execução

### Passo 0 — ambiente (igual ao plano anterior)

```bash
export PATH="/c/Program Files/Java/jdk-22.0.2/bin:$PATH"
java -version   # deve mostrar "openjdk 22.0.2"
cd "C:/Users/Francesco/Development/tcc/tcc-desenvolvimento/spec"
```

### Passo 1 — branch `fix/cascata-bloqueio-sessoes-afetadas` (só §2, Bug B/I5)

A partir da ponta atual de `fix/lww-gating-escrita-local` (HEAD conforme o relatório
de 2026-09-16):

```bash
git checkout fix/lww-gating-escrita-local
git checkout -b fix/cascata-bloqueio-sessoes-afetadas
```

Aplicar só o §2 (campo `sessoesAfetadas` em `Mensagem`, os 5 `msg ==` com
`sessoesAfetadas |-> {}`, o `msg ==` de `Block` com o conjunto calculado, o *case*
`ID_BLOQUEIA` de `EntregaMensagem` reescrito). Sincronizar comentários pertinentes.

```bash
git add spec/SessionStore.tla
git commit -m "fix: propaga cascata de invalidacao de sessoes do Block via campo sessoesAfetadas na mensagem ID_BLOQUEIA"
```

Checkpoint (esperado: `I5` fecha; `I6` **ainda viola**, causa independente ainda não
corrigida nesta branch — isso não é regressão, é o comportamento esperado até o
Passo 2):

```bash
./run-tlc-invariantes.sh tiny 1 3
./run-tlc-invariantes.sh small 1 3
```

### Passo 2 — branch `fix/lww-gate-status-contador-inc` (só §3, I6, Rota (c))

A partir da ponta de `fix/cascata-bloqueio-sessoes-afetadas` (incremental):

```bash
git checkout -b fix/lww-gate-status-contador-inc
```

Aplicar só o §3.5 (*case* `CONTADOR_INC` de `EntregaMensagem` reescrito com os dois
`IF` desacoplados). Sincronizar comentários pertinentes (§4).

```bash
git add spec/SessionStore.tla
git commit -m "fix: desacopla o gate LWW de status do gate de contador em CONTADOR_INC (EntregaMensagem), corrigindo bloqueio orfao em I6"
```

### Passo 3 — matriz de cardinalidades e checkpoint final

Mesma matriz e mesmos parâmetros já estabelecidos como baseline eficaz no relatório de
2026-09-16 (não se espera precisar reduzir mais, ver §2.2/§3.5, mas manter a
contingência):

| Config | IDS | SIDS | REPLICAS | LIMITE | MaxCanal | MaxRelogio | Timebox |
|---|---|---|---|---|---|---|---|
| `tiny`   | 1 | 2 | 2 | 2 | 1 | 3 | sem timeout |
| `small`  | 2 | 3 | 2 | 3 | 1 | 3 | sem timeout |
| `medium` | 3 | 3 | 2 | 3 | 1 | 3 | 20 min (`TIMEOUT_SECS=1200`) |
| `full`   | 3 | 3 | 3 | 3 | 1 | 3 | 20 min (`TIMEOUT_SECS=1200`) |

```bash
./run-tlc-invariantes.sh tiny 1 3
./run-tlc-invariantes.sh small 1 3
TIMEOUT_SECS=1200 ./run-tlc-invariantes.sh medium 1 3
TIMEOUT_SECS=1200 ./run-tlc-invariantes.sh full 1 3
```

Se `I5` ou `I6` **ainda** violarem em `small` após as duas correções, tratar como
achado real (mesma disciplina das rodadas anteriores): registrar o contraexemplo
completo, diagnosticar a causa, propor novo ajuste — **não** forçar fechamento nem
esconder o resultado. Se `TypeOK`/`I1`/`I2`/`I3`/`I4` mudarem de veredito em
`tiny`/`small`/`medium` frente ao relatório de 2026-09-16 (§3.1 daquele relatório),
tratar como regressão e investigar antes de prosseguir — `I1`/`I2` devem CONTINUAR
violando (não são afetados por nenhuma das duas correções deste plano).

### Passo 4 — relatório final

Escrever `docs/relatorio-tlc-<data-real-da-execução>.md`, mesmo formato dos anteriores
(entradas, processamento com diagrama Mermaid tema branco, matriz de vereditos,
contraexemplos completos de qualquer invariante que ainda viole, conclusão honesta).

---

## 6. Critérios de aceite

1. **I5 fecha sem contraexemplo** em `tiny` e `small` (e em `medium`/`full` se
   fecharem dentro do timebox de 20 min).
2. **I6 fecha sem contraexemplo** nas mesmas cardinalidades.
3. **Sem regressão:** `TypeOK`, `I3`, `I4` continuam fechando exaustivamente onde já
   fechavam (`tiny`/`small`/`medium`, por construção de 2026-09-16); `I1`/`I2`
   continuam VIOLADOS (esperado — propriedades de segurança *cross-réplica* não
   condicionadas a quiescência, natureza inalterada por este plano).
4. Se `I5`/`I6` não fecharem em `small` após as duas correções, isso deve ser
   reportado com contraexemplo completo e causa raiz — não escondido nem forçado.
5. `medium`/`full`: reportar veredito se fechar dentro do timebox; caso contrário,
   números parciais (estados gerados/distintos até o corte), sem inventar veredito —
   mesmo padrão já em uso.
6. Nenhuma das duas branches (`fix/cascata-bloqueio-sessoes-afetadas`,
   `fix/lww-gate-status-contador-inc`) vai para `origin/*` sem aprovação explícita do
   Gestor — commits locais são livres, *push* não.
7. `docs/relatorio-tlc-<data>.md` novo, no mesmo padrão de rigor dos anteriores.

---

## 7. Pontos que precisam de validação do Gestor

- **Divergência das duas rotas propostas pelo Gestor para I6.** Este plano rejeita
  tanto a Rota (a) quanto a Rota (b), como formuladas na mensagem original, e propõe
  uma terceira (Rota (c): desacoplar o *gate* de `status` do *gate* de `contador`
  dentro do *case* `CONTADOR_INC` de `EntregaMensagem`, sem tocar em nenhuma
  precondição, variável de estado ou mensagem nova). A justificativa central é a
  citação normativa de `tcc-escrita/Metodologia.tex` (O5: "sem alterar a marca de
  bloqueio") — **só leitura** dessa citação foi feita, nenhuma edição em
  `tcc-escrita/`, conforme escopo do Senior. Peço confirmação explícita de que essa
  divergência de rota é aceitável antes de acionar o Worker — é exatamente o tipo de
  decisão que a política vigente exige validar antes da execução.
- **Rota (c) é estritamente mais barata** que as duas rotas propostas (zero variáveis
  novas, zero campos novos em `Mensagem`, zero mudança em `MCSession.tla`, zero
  mudança de precondição) — mas também é uma mudança mais "cirúrgica"/sutil (dois
  `IF`s reordenados dentro de um *case* já denso). Se o Gestor preferir, por qualquer
  motivo, uma solução estruturalmente mais explícita mesmo custando mais (ex.: a Rota
  (b) do Gestor, aceitando a mudança de precondições de `Login`/`Block`/`Unblock` e a
  reavaliação formal de I2 que ela exigiria), posso reescrever o plano nessa direção —
  sinalizando desde já que, nesse caso, a parte de `ResetFailures` da Rota (b) também
  precisaria de um ajuste equivalente ao que está descrito em §3.2 para não violar O5.
- **Duas branches sequenciais** (`fix/cascata-bloqueio-sessoes-afetadas` depois
  `fix/lww-gate-status-contador-inc`), cada uma com checkpoint próprio em
  `tiny`/`small` antes de avançar — mesmo espírito das Mudanças 2b/2c, que provou
  valioso para isolar causa e efeito quando o TLC encontra algo inesperado. Confirmar
  que vale o tempo extra (2 rodadas de TLC em vez de 1) antes de acionar o Worker.
- Nome dos dois arquivos-alvo (`spec/SessionStore.tla` apenas — `MCSession.tla` e
  `run-tlc-invariantes.sh` não precisam de nenhuma mudança neste plano) — confirmar
  que este escopo reduzido (frente ao plano anterior, que tocou os três arquivos) está
  correto antes da execução.
- Como sempre: nada vai a `origin/*` sem aprovação explícita, a cada *push* — este
  plano não presume aprovação alguma, só prepara o trabalho local.

---

## 8. Próxima tarefa sugerida para o Worker

1. Ler este plano por completo, mais `docs/relatorio-tlc-2026-09-16.md` (causas
   residuais) e o `spec/SessionStore.tla` atual, antes de tocar em qualquer arquivo.
2. Criar `fix/cascata-bloqueio-sessoes-afetadas` a partir de
   `fix/lww-gating-escrita-local`, aplicar §2 integralmente, commitar, rodar
   `tiny`/`small` (Passo 1) e registrar vereditos brutos.
3. Criar `fix/lww-gate-status-contador-inc` a partir da branch anterior, aplicar §3.5
   integralmente, commitar.
4. Rodar a matriz completa (Passo 3: `tiny`, `small`, `medium` com timebox 20 min,
   `full` com timebox 20 min) e escrever `docs/relatorio-tlc-<data>.md` (Passo 4).
5. Se `I5`/`I6` ainda violarem em qualquer cardinalidade após as duas correções,
   documentar o contraexemplo completo e reportar ao Senior/Gestor antes de propor
   qualquer novo ajuste — não implementar uma quarta correção sem validação.
6. Reportar tudo ao Gestor e ao Senior antes de qualquer *push* para `origin/*`.
