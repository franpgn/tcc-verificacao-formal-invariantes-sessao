# Plano — Mudança 4: mensagem `CONTADOR_INC` "fantasma" (P-15)

> Elaborado pelo Senior em 2026-09-16, para execução pelo Worker. **Este documento é o
> plano completo** — nada foi implementado nem executado. Dá continuidade a
> `docs/plano-fix-cascata-bloqueio-2026-09-16.md` (Mudança 3) e ao estado real
> registrado em `docs/relatorio-tlc-2026-09-16-mudanca3.md` (achado P-15, §3.3 daquele
> relatório) e `docs/registro-de-problemas.md` (P-15). Ler os dois relatórios de TLC
> (`docs/relatorio-tlc-2026-09-16.md` e `docs/relatorio-tlc-2026-09-16-mudanca3.md`,
> principalmente o segundo) e `docs/registro-de-problemas.md` antes de executar este
> plano — este documento pressupõe o `spec/SessionStore.tla` tal como ele está hoje, na
> ponta de `fix/lww-gate-status-contador-inc` (commit `4e5d7ef`, `HEAD` confirmado via
> `git branch -vv` nesta sessão — lido integralmente antes de escrever este plano).

---

## 1. Contexto

Após Mudanças 1+2+2b+2c+3a+3b, `I5` fecha exaustivamente em `tiny`/`small`/`medium`,
mas `I6` continua violando em todas as cardinalidades, por uma causa nova (P-15),
diferente da que a Mudança 3b (Rota c) corrigiu (P-13):

- **P-13 (fechada pela Rota c):** um `status=BLOCKED` disparado por um valor de
  `contador` que **foi real** em algum momento (aplicado, cruzou `LIMITE`) e foi
  depois **superado** por LWW por um `CONTADOR_RESET` de prioridade maior — o
  bloqueio ficava "preso", órfão de um fato que já foi verdadeiro, mas passou.
- **P-15 (esta correção):** em `IncrementFailure(r,id)`, a mensagem `CONTADOR_INC`
  (`valor |-> novo, prio |-> p`) é construída e enviada via `Broadcast(r, msg)`
  **incondicionalmente** — mesmo quando a própria escrita LOCAL de `contador` é
  **rejeitada** pelo autofiltro LWW (`IF p > contadorTS[r][id]`, Mudança 2b). Quando
  isso acontece, `novo` **nunca se torna** `contador` real em réplica nenhuma — nem em
  `r`, nem em réplica alguma que aplique `EntregaMensagem` corretamente por LWW — mas a
  mensagem "fantasma" com esse `valor`/`prio` ainda é propagada. Antes da Rota (c), o
  gate único de `CONTADOR_INC` descartava a mensagem inteira (`status` incluído) sempre
  que `contador` perdia sua corrida — mascarando o problema por acidente de
  acoplamento, não por design. A Rota (c) — correta e necessária para fechar P-13 —
  remove esse acoplamento acidental, e com ele a proteção acidental que escondia P-15.

O pedido do Gestor é: (1) analisar rigorosamente se as outras 5 ações de escrita
(`Login`, `Logout`, `ResetFailures`, `Block`, `Unblock`) têm o mesmo risco estrutural, e
corrigi-las também se sim, com justificativa clara para as que ficarem sem mudança; (2)
tratar `Block` como caso especial (múltiplos gates locais independentes) e decidir entre
as três rotas que o Gestor esboçou; (3) escrever pseudocódigo completo de tudo que muda.
Este plano faz essa análise abaixo e conclui que **apenas `IncrementFailure` precisa da
correção estrutural** — uma conclusão mais restrita do que a hipótese de trabalho do
Gestor, com prova completa de por que as outras 5 ações estão seguras sem mudança
alguma (não apenas "não observadas pelo TLC ainda", mas estruturalmente impossíveis de
produzir o mesmo tipo de violação).

---

## 2. Princípio geral e por que ele NÃO se aplica igualmente às 6 ações

### 2.1 O lema de convergência LWW por campo (por que broadcast incondicional costuma ser inofensivo)

Considere um campo de estado `F` com registrador LWW `(F[r], F_TS[r])`, escrito por
tentativas (locais ou mensagens) que carregam `(valor, prio)`, aplicadas pela regra
`IF prio > F_TS[r] THEN F[r] := valor; F_TS[r] := prio` — e cujo **gate** e **valor
aplicado** dependem **somente** de `(valor, prio)` da própria tentativa e do `F_TS[r]`
do **mesmo campo**, nunca do valor/TS de **outro** campo.

**Lema:** sob essas condições, `F` converge para o mesmo valor em todas as réplicas sob
quiescência, **mesmo que tentativas localmente perdedoras na origem também sejam
propagadas**. Prova (esboço): `Broadcast(origem, msg)` envia a mesma mensagem a todas
as réplicas exceto a de origem; toda réplica aplica exatamente a mesma regra de
comparação de prioridade sobre o mesmo par `(valor, prio)`. Isso é comutativo, idempotente
e monótono (a regra "maior prioridade vence" não depende de ordem de entrega). Logo, sob
quiescência (todo o conjunto de mensagens geradas para `F` foi entregue a todas as
réplicas), o valor final de `F[r]` em qualquer réplica é o da tentativa de **prioridade
global máxima** — o mesmo em todas. Crucialmente: **toda tentativa que algum dia venceu
sua própria corrida em alguma réplica** (inclusive a de origem, aplicando-a
localmente) **já foi, ela mesma, transmitida a todas as outras réplicas no momento em
que venceu** (é exatamente essa tentativa vencedora que dispara o `Broadcast`, com
`prio` correspondente) — logo o "verdadeiro vencedor global" de `F` sempre chega a todo
lugar, **independentemente** de tentativas perdedoras adicionais também serem
propagadas ou suprimidas. Suprimir uma tentativa perdedora-na-origem não impede que
`F` convirja; propagá-la também não o impede (o pior caso é ela vencer temporariamente
em alguma réplica atrasada, e depois ser sobrescrita quando essa réplica também
receber a tentativa de prioridade ainda maior — LWW convergente por definição, Shapiro
et al. 2011, já citado no trabalho).

**Corolário — por que isto falha para `status` em `CONTADOR_INC`:** o lema exige que o
gate de `F` não dependa do valor de **outro** campo. O gate remoto de `status` em
`CONTADOR_INC` (Rota c, Mudança 3b) é `m.valor >= LIMITE /\ m.prio > statusTS[r][id]` —
ele lê `m.valor`, que é o **valor candidato de `contador`**, não de `status`. Isso por
si só não quebraria o lema (o valor de `m.valor` é fixo na mensagem, avaliado
identicamente em toda réplica) **se** o conjunto de "tentativas de escrita em `status`"
fosse o mesmo, gerado pela mesma regra, em toda réplica **incluindo a origem**. Mas não
é: a origem (`IncrementFailure`, local) só considera essa tentativa de `status` **se a
própria escrita de `contador` também venceu localmente** (`p > contadorTS[r][id]`,
acoplamento proposital e correto, ver comentário já existente na spec) — enquanto o
destino (`EntregaMensagem`, Rota c) considera a tentativa de `status`
**incondicionalmente** em relação a `contadorTS`. Ou seja: **o conjunto de tentativas de
`status` implícito por uma mensagem `CONTADOR_INC` não é o mesmo função de
`(valor,prio)` na origem e no destino** — quebra a premissa do lema especificamente
para `status`. É essa assimetria, não o "broadcast incondicional" em si, que é a causa
raiz.

### 2.2 Verificação campo a campo — nenhum outro tipo de mensagem tem esse acoplamento

Reli os 6 *cases* de `EntregaMensagem` e as 6 ações locais em `spec/SessionStore.tla`
(estado atual, ponta de `fix/lww-gate-status-contador-inc`) especificamente à procura de
qualquer gate remoto que leia o `valor`/`payload` de um campo para decidir a escrita de
**outro** campo (a assinatura exata do problema):

| Mensagem | Campos escritos | Gate de cada campo lê valor de outro campo? |
|---|---|---|
| `SESSAO_VALIDA` | `sessoes` (valor fixo `"VALID"`), `sessaoDe` (valor = `m.payload`, mas gate é só `m.prio > sessaoDeTS[...]`) | Não — cada gate compara `m.prio` só contra o TS do próprio campo |
| `SESSAO_INVALIDA` | `sessoes` (fixo `"INVALID"`), `motivos` (valor = `m.payload`, constante por evento) | Não — mesma estrutura |
| `CONTADOR_INC` | `contador` (valor = `m.valor`), `status` (fixo `"BLOCKED"`, **gate lê `m.valor`**) | **Sim — único caso** |
| `CONTADOR_RESET` | só `contador` (fixo `0`) | N/A (um único campo, sem O5 tocar `status`) |
| `ID_BLOQUEIA` | `status` (fixo `"BLOCKED"`), `sessoes`/`motivos` em cascata (fixos `"INVALID"`/`"ADMIN_BLOCK"`, gate por sessão é `s \in m.sessoesAfetadas /\ m.prio > TS[...]`) | Não — nenhum gate lê o valor de outro campo; `sessoesAfetadas` é um **conjunto de chaves**, não um valor de outro registrador LWW |
| `ID_DESBLOQUEIA` | `status` (fixo `"OPEN"`), `contador` (fixo `0`) | Não — ambos fixos, gates independentes por prioridade só |

`CONTADOR_INC` é o único tipo de mensagem em que o gate de um campo (`status`) é
condicionado ao **valor** (não só à prioridade) de outro campo (`contador`). Esse é
exatamente o ingrediente que quebra o lema do §2.1. Como nenhum outro tipo de mensagem
tem esse ingrediente, **nenhuma outra ação precisa da correção estrutural** — não por
"o TLC ainda não achou", mas porque a condição estrutural que habilita esse tipo de bug
simplesmente não existe nas outras 5.

### 2.3 Confirmação adicional para `Login` (prova do Gestor, estendida a `sessaoDe`)

Independentemente do argumento do §2.2 (que já basta), verifiquei também a prova mais
forte que o Gestor esboçou para `Login`, estendendo-a ao segundo campo que `Login`
escreve (`sessaoDe`, que o Gestor não cobriu explicitamente):

- A precondição de `Login` é `sessoes[r][s] = "NONE"`. Nenhuma ação da especificação
  jamais escreve `"NONE"` de volta em `sessoes` (confirmado por leitura de todas as 6
  ações locais e dos 6 *cases* de `EntregaMensagem>` — `"NONE"` só aparece em `Init`).
  Logo, se `sessoes[r][s] = "NONE"`, nenhuma escrita jamais foi **aceita** para
  `sessoes[r][s]`, e portanto `sessoesTS[r][s] = 0`.
- `sessaoDe[r][s]` só é escrito em dois lugares: `Login` (local) e o *case*
  `SESSAO_VALIDA` de `EntregaMensagem` (remoto) — sempre no **mesmo evento** que também
  tenta escrever `sessoes[r][s]` para `"VALID"`, com o **mesmo** `prio`/`m.prio`. Como
  `sessoesTS[r][s] = 0` sempre que a precondição de `Login` vale, e `Prio(ts,r) > 0`
  para todo `ts \geq 1`, **todo** evento de escrita de `sessoes` a partir desse estado
  vence o gate de `sessoes` incondicionalmente (`p > 0`) — o que significa que
  `sessoes[r][s]` só pode estar em `"NONE"` se **nenhum** evento `SESSAO_VALIDA` jamais
  ocorreu para `s` em `r` (local ou remoto). Logo `sessaoDeTS[r][s] = 0` também, pela
  mesma razão (nunca houve evento que o tocasse).
- Portanto, sempre que a precondição de `Login` vale, `p > sessoesTS[r][s] = 0` **e**
  `p > sessaoDeTS[r][s] = 0` — as duas escritas locais de `Login` **sempre** vencem.
  `Login` nunca produz uma escrita local rejeitada; não há "corrida perdida" para
  propagar de forma fantasma.

Esta prova é mais forte que a necessária (o §2.2 já bastaria, pois `SESSAO_VALIDA` não
tem acoplamento de valor entre campos), mas confirma que `Login` está seguro por **duas
razões independentes**, reforçando a decisão de não tocá-lo.

### 2.4 Conclusão da análise

| Ação | Mensagem | Tem acoplamento de valor entre campos (§2.2)? | Escrita local pode ser rejeitada? | Precisa de correção estrutural? |
|---|---|---|---|---|
| `Login` | `SESSAO_VALIDA` | Não | Nunca (prova §2.3) | **Não** |
| `Logout` | `SESSAO_INVALIDA` | Não | Sim (`sessoesTS`/`motivosTS` podem já ser altos) | **Não** — sem acoplamento, o lema do §2.1 garante convergência mesmo assim |
| `IncrementFailure` | `CONTADOR_INC` | **Sim** (`status` lê `m.valor`) | Sim | **Sim — única correção necessária** |
| `ResetFailures` | `CONTADOR_RESET` | Não (só toca `contador`, nunca `status`, regra O5) | Sim | **Não** |
| `Block` | `ID_BLOQUEIA` | Não (ver §3 abaixo, caso especial) | Sim (múltiplos gates) | **Não** (ver decisão §3) |
| `Unblock` | `ID_DESBLOQUEIA` | Não | Sim | **Não** |

---

## 3. Caso especial — `Block`: decisão pela Rota (b)

O Gestor pediu atenção específica a `Block`, por ter múltiplos gates locais
independentes (`status`/`statusTS`; `sessoes`/`sessoesTS` e `motivos`/`motivosTS` por
sessão afetada) e por `sessoesAfetadas` carregar o cascade **bruto** (decisão explícita
da Mudança 3a). As três rotas propostas pelo Gestor:

- **(a) suprimir o broadcast inteiro só se TODOS os gates locais relevantes
  rejeitarem.**
- **(b) sempre fazer broadcast (como hoje), porque os valores de `Block` são
  constantes/absolutos, não uma computação relativa a um valor anterior.**
- **(c) outra abordagem.**

**Decisão: Rota (b) — nenhuma mudança em `Block`.** Justificativa:

1. **Pelo critério estrutural do §2.2, `Block` não tem o ingrediente do bug.** O gate
   remoto de `status` em `ID_BLOQUEIA` é `m.prio > statusTS[r][m.chave]` — não lê o
   valor de `sessoesAfetadas` nem de nenhum outro campo para decidir se aplica
   `"BLOCKED"`. Os gates de `sessoes`/`motivos` por sessão são
   `s \in m.sessoesAfetadas /\ m.prio > sessoesTS[r][s]` (idem para `motivosTS`) — a
   pertença a `m.sessoesAfetadas` é um teste de **associação a um conjunto de chaves
   fixo na mensagem**, não uma leitura do **valor** de outro registrador LWW. Nenhum
   desses três gates lê o valor ou o TS de um campo que **não seja o próprio**. Logo,
   pelo lema do §2.1, cada um dos três (status, e sessoes/motivos por sessão) converge
   de forma independente sob quiescência, **mesmo que a escrita local correspondente em
   `r` tenha perdido sua própria corrida** — exatamente como `Logout`/`Unblock`.
2. **O argumento adicional do Gestor também se confirma:** os valores que `Block`
   escreve são constantes/absolutos (`"BLOCKED"`, `"INVALID"`, `"ADMIN_BLOCK"`) — não
   computados a partir do valor anterior do próprio campo (diferente de
   `novo = contador[r][id] + 1`). Não há, portanto, o tipo específico de "fantasma"
   (um valor que só faz sentido semântico se a base de onde foi calculado também tiver
   vencido) que aflige `IncrementFailure`.
3. **Rota (a) é rejeitada explicitamente**, por dois motivos que o próprio Gestor já
   antecipou como risco: (i) é estruturalmente desnecessária, pois `Block` já está
   seguro sem ela (pontos 1–2 acima); (ii) implementá-la exigiria agregar o resultado de
   3–4 `IF`s independentes (1 de `status` + N por sessão em `SIDS`) numa única decisão
   booleana de "suprimir o broadcast inteiro", o que reintroduziria exatamente o tipo de
   acoplamento indevido entre campos independentes que as Mudanças 2c/3a corrigiram —
   se qualquer sessão tivesse seu gate aceito mas `status` rejeitado (ou vice-versa),
   suprimir o broadcast inteiro apagaria informação genuína que deveria propagar (o
   mesmo padrão de erro do "Bug B" original, só que por um caminho novo). Rejeitada.

`Block` permanece **exatamente como está hoje** — nenhuma linha de código muda. Só o
comentário será reforçado (ver §5).

---

## 4. Correção — `IncrementFailure`: broadcast gateado pela própria aceitação local

### 4.1 Diff completo (único trecho de código que muda)

**Antes** (`spec/SessionStore.tla`, linhas 226–250, ponta atual):

```tla
IncrementFailure(r, id) ==
  LET novo    == contador[r][id] + 1
      novoTS  == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "CONTADOR_INC", chave |-> id, payload |-> "", valor |-> novo,
               ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
  IN
  /\ contador[r][id] < LIMITE
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > contadorTS[r][id]
       THEN /\ contador'   = [contador   EXCEPT ![r][id] = novo]
            /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
            /\ IF novo >= LIMITE /\ p > statusTS[r][id]
                 THEN /\ status'   = [status   EXCEPT ![r][id] = "BLOCKED"]
                      /\ statusTS' = [statusTS EXCEPT ![r][id] = p]
                 ELSE /\ status' = status /\ statusTS' = statusTS
       ELSE /\ contador' = contador /\ contadorTS' = contadorTS
            /\ status' = status /\ statusTS' = statusTS
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ canal'      = Broadcast(r, msg)
```

**Depois (Mudança 4):**

```tla
(* O4, incrementFailure: incrementa contador, bloqueia se atingir LIMITE.          *)
(* Mudanca 4 (fix, P-15): canal' so faz Broadcast quando a PROPRIA escrita local   *)
(* de contador venceu seu gate LWW (p > contadorTS[r][id]) -- mesma condicao que   *)
(* ja gateia contador'/contadorTS'/status'/statusTS' nesta acao. Racional: se a    *)
(* escrita local perde sua propria corrida de prioridade, "novo" nunca se torna    *)
(* contador real em replica nenhuma (nem aqui); propagar essa mensagem seria       *)
(* transmitir um valor fantasma que um gate remoto INDEPENDENTE (Rota c, Mudanca   *)
(* 3b, case CONTADOR_INC de EntregaMensagem) poderia aceitar para `status` mesmo   *)
(* sem `contador` jamais ter vencido em lugar nenhum -- exatamente o contraexemplo *)
(* de P-15 (docs/relatorio-tlc-2026-09-16-mudanca3.md SS3.3). Ver justificativa    *)
(* completa (por que so esta acao precisa desta guarda, entre as 6) em             *)
(* docs/plano-fix-broadcast-fantasma-2026-09-16.md.                                *)
IncrementFailure(r, id) ==
  LET novo    == contador[r][id] + 1
      novoTS  == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "CONTADOR_INC", chave |-> id, payload |-> "", valor |-> novo,
               ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
  IN
  /\ contador[r][id] < LIMITE
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > contadorTS[r][id]
       THEN /\ contador'   = [contador   EXCEPT ![r][id] = novo]
            /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
            /\ IF novo >= LIMITE /\ p > statusTS[r][id]
                 THEN /\ status'   = [status   EXCEPT ![r][id] = "BLOCKED"]
                      /\ statusTS' = [statusTS EXCEPT ![r][id] = p]
                 ELSE /\ status' = status /\ statusTS' = statusTS
            /\ canal' = Broadcast(r, msg)
       ELSE /\ contador' = contador /\ contadorTS' = contadorTS
            /\ status' = status /\ statusTS' = statusTS
            /\ canal' = canal
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
```

Mudança mecânica: a linha `/\ canal' = Broadcast(r, msg)`, que hoje fica **fora** (abaixo)
do `IF p > contadorTS[r][id]`, passa para **dentro** dos dois ramos desse mesmo `IF` já
existente — `canal' = Broadcast(r, msg)` no ramo aceito, `canal' = canal` (nenhuma
mudança) no ramo rejeitado. Nenhum outro `IF`, variável, precondição ou tipo de mensagem
muda. `relogio'` continua avançando sempre, fora do `IF` — preserva o princípio já
documentado ("o relogio SEMPRE avanca quando a acao local dispara, independente de o
valor do campo ter sido efetivamente atualizado").

### 4.2 Por que isto fecha exatamente o contraexemplo de P-15

Retraçando o contraexemplo do relatório (`docs/relatorio-tlc-2026-09-16-mudanca3.md`
§3.3, `tiny`, `LIMITE=2`) com a correção aplicada:

| Estado | Ação | `r1.contador[u1]` | `r2.contador[u1]` | `r1.status[u1]` | `r2.status[u1]` |
|---|---|---|---|---|---|
| 1 | Init | 0 | 0 | OPEN | OPEN |
| 2 | `IncrementFailure(r2,u1)` (`novo=1`, `p=5`) | 0 | 1 | OPEN | OPEN |
| 3 | `EntregaMensagem(r1)` recebe `CONTADOR_INC,valor=1,prio=5` (aceito) | 1 | 1 | OPEN | OPEN |
| 4 | `IncrementFailure(r1,u1)` (`novo=2`, `p=4`) — `4 \leq contadorTS[r1]=5`: rejeitado. **Com a Mudança 4, `canal' = canal` — nenhuma mensagem é enviada.** | 1 | 1 | OPEN | OPEN |
| 5 | (nada a entregar — canal permanece vazio para esta mensagem) | 1 | 1 | OPEN | OPEN |

Estado final (quiescente): `contador[r1]=1=contador[r2]`, `status[r1]=OPEN=status[r2]` —
**converge em ambos os campos**. O traço específico de P-15 deixa de existir.

### 4.3 Por que isto generaliza (não é só o traço específico)

O argumento do §2.1/§2.2 é estrutural, não dependente do traço: **depois** da Mudança
4, toda mensagem `CONTADOR_INC` que chega a `EntregaMensagem` carrega um `(m.valor,
m.prio)` tal que `m.prio` **de fato venceu** o gate de `contador` na sua própria
origem no momento em que foi criada — ou seja, o "fato histórico já consumado na
réplica de origem" que a Rota (c) já presumia (comentário da spec, linha ~391: "isto e
seguro no caso REMOTO... porque m.valor/m.prio sao um fato historico ja consumado na
origem") passa a ser **verdadeiro por construção**, não apenas presumido. Isso restaura
exatamente a premissa que a Rota (c) precisava para ser correta, fechando a lacuna sem
reabrir P-13 (que continua fechado — `CONTADOR_RESET` continua sem tocar `status`,
regra O5 inalterada; a Rota (c) do gate independente de `status` continua intacta).

### 4.4 Impacto no espaço de estados (por que não deve piorar o tempo de TLC)

A mudança não introduz nenhuma variável, campo, tipo de mensagem ou `\E` novo — é uma
reordenação de uma atribuição já existente para dentro de um `IF` já existente. O único
efeito observável no espaço de estados é que, no ramo antes chamado de "rejeitado"
(`p \leq contadorTS[r][id]`), o número de sucessores distintos de `IncrementFailure`
**diminui** (antes: `canal` + mensagem fantasma enfileirada, um estado a mais por
combinação de `r`/`id`/conteúdo de canal; depois: `canal' = canal`, colapsando com
estados já alcançáveis por outros meios) — se houver qualquer efeito mensurável, deve
ser uma leve **redução**, não aumento, do espaço de estados a explorar. Não se espera
necessidade de ajustar `MaxCanal`/`MaxRelogio` (baseline 1/3 desde a rodada 1).

---

## 5. Sincronização de documentação dentro do próprio `SessionStore.tla`

Junto com o diff do §4.1 (que já inclui o comentário da própria ação), atualizar:

- **Comentário de cabeçalho do módulo** (linhas 1–43): acrescentar um item registrando
  a Mudança 4 — "`IncrementFailure` só faz `Broadcast` da mensagem `CONTADOR_INC`
  quando a própria escrita local de `contador` vence seu gate LWW (evita propagar um
  valor 'fantasma' que nunca se tornou `contador` real em réplica alguma — ver P-15,
  `docs/plano-fix-broadcast-fantasma-2026-09-16.md`)."
- **Comentário do *case* `CONTADOR_INC` de `EntregaMensagem`** (linhas ~384–401):
  ajustar a frase "isto e seguro no caso REMOTO... porque m.valor/m.prio sao um fato
  historico ja consumado na origem" para deixar explícito que essa premissa **agora é
  garantida por construção** pela Mudança 4 (antes, era presumida; a mensagem só chega
  a existir se a premissa for verdadeira).
- **Comentário do *case* `ID_BLOQUEIA`** (linhas ~420–431) e das ações `Logout`,
  `ResetFailures`, `Unblock`: acrescentar uma nota curta de uma linha em cada uma,
  citando o §2.2/§2.4 deste plano — por exemplo, em `Logout`: `(* Mudanca 4: canal'
  permanece incondicional aqui -- seguro por construcao, SESSAO_INVALIDA nao tem
  acoplamento de valor entre campos, ver docs/plano-fix-broadcast-fantasma-2026-09-16.md
  SS2.2/SS2.4. *)`. Idem para `ResetFailures`, `Unblock`, e para o `msg ==`/corpo de
  `Block`. Objetivo: deixar um rastro de que a ausência de mudança foi uma decisão
  analisada, não uma omissão — mesmo espírito já usado para justificar por que
  `ResetFailures`/`CONTADOR_RESET` não foram tocados nas Mudanças 3a/3b (regra O5).
- **Comentário `THEOREM` final** (linhas ~537–551): já afirma a expectativa de `I5`/`I6`
  fechando "sem mais como causas residuais documentadas, e sim como correções
  verificadas pelo TLC" — se `I6` fechar nesta rodada, esse comentário já está correto
  como está (não precisa mudar de tom); se não fechar, reportar com a mesma disciplina
  das rodadas anteriores (§7 abaixo).

`Mensagem`, `TypeOK`, `Init`, `Broadcast`, `MCSession.tla` e `run-tlc-invariantes.sh`
**não mudam** — este plano toca só `spec/SessionStore.tla`, e só a ação
`IncrementFailure` muda de comportamento (as demais só ganham comentário).

---

## 6. Plano de execução

### Passo 0 — ambiente (igual aos planos anteriores)

```bash
export PATH="/c/Program Files/Java/jdk-22.0.2/bin:$PATH"
java -version   # deve mostrar "openjdk 22.0.2"
cd "C:/Users/Francesco/Development/tcc/tcc-desenvolvimento/spec"
```

### Passo 1 — branch `fix/broadcast-fantasma-contador-inc`

A partir da ponta atual, confirmada nesta sessão via `git branch -vv`:
`fix/lww-gate-status-contador-inc` (commit `4e5d7ef`, contém Mudanças 3a+3b):

```bash
git checkout fix/lww-gate-status-contador-inc
git checkout -b fix/broadcast-fantasma-contador-inc
```

Aplicar **só** o §4.1 (diff de `IncrementFailure`) e o §5 (comentários — cabeçalho do
módulo, *case* `CONTADOR_INC`, e as 5 notas curtas nas demais ações/`Block`). Nenhum
outro trecho muda.

```bash
git add spec/SessionStore.tla
git commit -m "fix: broadcast de CONTADOR_INC so quando a propria escrita local de contador vence o gate LWW, evitando mensagem fantasma (P-15)"
```

### Passo 2 — matriz de cardinalidades

Mesma matriz e mesmos parâmetros já estabelecidos como baseline eficaz desde a rodada 1
(não se espera precisar reduzir, ver §4.4, mas manter a contingência já documentada nos
relatórios anteriores caso o TLC mostre o contrário):

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

Ordem sugerida: rodar `tiny`/`small` primeiro como checkpoint rápido (ambas sem
*timeout*, portanto vereditos definitivos); só prosseguir para `medium`/`full` se
`tiny`/`small` confirmarem `I6` fechando sem regressão em `TypeOK`/`I1`..`I5`.

### Passo 3 — diagnóstico se `I6` ainda violar

Se `I6` **ainda** violar em `tiny`/`small` após esta correção, tratar como achado real
(mesma disciplina das três rodadas anteriores): registrar o contraexemplo completo,
diagnosticar a causa, **parar e reportar ao Gestor antes de implementar uma quinta
correção por conta própria** — não encadear ajustes sem validação, mesmo que a causa
pareça mecanicamente análoga às anteriores. Prestar atenção especial a se o novo
contraexemplo (caso exista) envolve alguma combinação de `Block`/cascata ainda não
coberta — o padrão observado três vezes seguidas nesta sessão (cada correção expõe uma
causa nova antes mascarada) é um sinal de que o espaço de interação entre `status`,
`contador` e o cascade de `Block` pode ter mais uma camada; não presumir que esta é a
última correção necessária só porque a análise do §2 é rigorosa — a análise também
pareceu completa nas rodadas anteriores até o TLC mostrar o contrário.

Se `TypeOK`/`I1`..`I5` mudarem de veredito frente ao relatório de
`docs/relatorio-tlc-2026-09-16-mudanca3.md` §3.1, tratar como regressão e investigar
antes de prosseguir — `I1`/`I2` devem **continuar** violando (natureza inalterada,
propriedades *cross-réplica* fora do escopo desta correção); `I5` deve **continuar**
fechando exaustivamente em `tiny`/`small`/`medium` (esta correção não deveria afetar
`I5`, já que não toca `sessoes`/`motivos`/`sessaoDe` — qualquer mudança de veredito em
`I5` seria inesperada e precisa de explicação, não só de registro).

### Passo 4 — relatório final

Escrever `docs/relatorio-tlc-<data-real-da-execução>.md`, mesmo formato dos três
anteriores (entradas, processamento com diagrama Mermaid tema branco, matriz de
vereditos, contraexemplo completo de qualquer invariante que ainda viole, conclusão
honesta). Atualizar `docs/registro-de-problemas.md` com uma entrada P-16 (ou a resolução
de P-15, se fechar) seguindo o mesmo padrão das entradas existentes.

---

## 7. Critérios de aceite

1. **`I6` fecha sem contraexemplo** em `tiny` e `small` (critério mínimo,
   correspondente ao contraexemplo documentado em P-15); idealmente também em
   `medium`/`full` dentro do timebox de 20 min — reportar números parciais se não
   fechar dentro do timebox, sem inventar veredito, mesmo padrão já em uso.
2. **`I5` continua fechando sem regressão** em `tiny`/`small`/`medium` (esta correção
   não deveria alterar `I5` de forma alguma — qualquer mudança de veredito aqui deve ser
   tratada como achado a investigar, não uma consequência esperada).
3. **Sem regressão em `TypeOK`/`I3`/`I4`** — continuam fechando exaustivamente onde já
   fechavam (`tiny`/`small`/`medium`); **sem regressão em `I1`/`I2`** — continuam
   VIOLADOS (esperado, natureza inalterada).
4. Se `I6` não fechar em `tiny`/`small` após esta correção, isso deve ser reportado com
   contraexemplo completo e causa raiz diagnosticada — não escondido nem forçado, e
   **nenhuma quinta correção implementada sem reportar ao Gestor primeiro** (mesma
   disciplina exigida nas três rodadas anteriores).
5. `medium`/`full`: reportar veredito se fechar dentro do timebox; caso contrário,
   números parciais (estados gerados/distintos até o corte), sem inventar veredito.
6. A branch `fix/broadcast-fantasma-contador-inc` **não vai para `origin/*`** sem
   aprovação explícita do Gestor — commit local é livre, *push* não.
7. `docs/relatorio-tlc-<data>.md` novo, no mesmo padrão de rigor dos três anteriores;
   `docs/registro-de-problemas.md` atualizado com a entrada correspondente.

---

## 8. Pontos que precisam de validação do Gestor

- **Conclusão mais restrita do que a hipótese de trabalho.** O Gestor levantou as 5
  outras ações como "candidatas ao mesmo bug"; a análise deste plano (§2) conclui que
  **nenhuma delas** precisa de mudança estrutural — só `IncrementFailure`. A
  justificativa é estrutural (nenhuma outra mensagem tem um gate que leia o *valor* de
  um campo diferente do que está gateando, o ingrediente específico que quebra o lema
  de convergência LWW do §2.1), não apenas "o TLC não achou ainda". Peço confirmação de
  que este escopo reduzido (uma ação muda de comportamento, cinco ganham só comentário)
  é aceitável antes de acionar o Worker — é o tipo de decisão que a política vigente
  exige validar.
- **Decisão pela Rota (b) para `Block`** (nenhuma mudança de código), rejeitando
  explicitamente a Rota (a) (suprimir broadcast só se todos os gates locais
  rejeitarem) pelo risco de reintroduzir acoplamento indevido entre campos
  independentes — mesmo risco que o próprio Gestor sinalizou na pergunta original.
  Peço confirmação específica desta escolha, já que foi a parte mais aberta a
  interpretação na sua mensagem.
- **Risco residual a observar, nomeado explicitamente:** as três rodadas anteriores
  desta sessão mostraram um padrão — cada correção que remove um acoplamento acidental
  expõe uma causa nova, antes mascarada. Este plano tem uma prova estrutural de que
  isso não deveria se repetir (nenhuma outra mensagem tem o ingrediente do bug), mas
  registro esse padrão histórico como motivo adicional para não pular o Passo 2
  (matriz completa) nem presumir fechamento antes do TLC confirmar — coerente com a
  disciplina já seguida nas rodadas 1–3.
- **Uma branch nova, local, sem *push*** (`fix/broadcast-fantasma-contador-inc`, a
  partir de `fix/lww-gate-status-contador-inc`) — aguardando aprovação explícita do
  Gestor antes de qualquer `git push`, como sempre.
- Nome do único arquivo-alvo (`spec/SessionStore.tla`) — confirmar que este escopo
  (menor que os planos anteriores: um único diff funcional, mais comentários) está
  correto antes da execução.

---

## 9. Próxima tarefa sugerida para o Worker

1. Ler este plano por completo, mais `docs/relatorio-tlc-2026-09-16-mudanca3.md` §3.3
   (contraexemplo de P-15) e o `spec/SessionStore.tla` atual, antes de tocar em
   qualquer arquivo.
2. Confirmar via `git branch -vv` que a ponta atual é `fix/lww-gate-status-contador-inc`
   (commit `4e5d7ef`) antes de criar a branch nova — se a ponta tiver avançado desde a
   escrita deste plano, parar e reportar ao Senior antes de prosseguir.
3. Criar `fix/broadcast-fantasma-contador-inc` a partir de
   `fix/lww-gate-status-contador-inc`, aplicar o diff do §4.1 e os comentários do §5
   integralmente, commitar (Passo 1).
4. Rodar `tiny`/`small` primeiro (Passo 2) e registrar vereditos brutos; só prosseguir
   para `medium`/`full` se `I6` fechar em ambas sem regressão em `TypeOK`/`I1`..`I5`.
5. Se `I6` ainda violar em qualquer cardinalidade, documentar o contraexemplo completo
   e **parar para reportar ao Senior/Gestor antes de propor uma quinta correção** — não
   implementar por conta própria.
6. Escrever `docs/relatorio-tlc-<data>.md` (Passo 4) e atualizar
   `docs/registro-de-problemas.md`.
7. Reportar tudo ao Gestor e ao Senior antes de qualquer *push* para `origin/*`.
