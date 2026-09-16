------------------------------ MODULE SessionStore ------------------------------
(***************************************************************************)
(* Especificação formal do núcleo do protocolo de estado de sessão em      *)
(* data grids sob replicação assíncrona.                                   *)
(*                                                                         *)
(* Cobertura desta versão (B-14 + correção broadcast/LWW, TCC-I):          *)
(*   - Conjunto IDS de identidades e SIDS de sessões.                      *)
(*   - Estado por réplica: sessoes (função SIDS -> Estados),               *)
(*     motivos (SIDS -> MotivosT), sessaoDe (SIDS -> IDS),                 *)
(*     contador (IDS -> 0..LIMITE), status (IDS -> StatusT).               *)
(*   - Canal assíncrono por réplica com broadcast real: cada escrita local *)
(*     é enfileirada nas filas de TODAS as réplicas destino (exceto a de   *)
(*     origem, que já aplicou o efeito localmente) -- ver Broadcast.       *)
(*   - Resolução de conflito last-writer-wins (LWW) via relógio lógico de  *)
(*     Lamport, por réplica, com desempate determinístico por identidade   *)
(*     de réplica (ver RepIndex/Prio). Um registrador LWW por CAMPO        *)
(*     (sessoesTS, motivosTS, sessaoDeTS, contadorTS, statusTS), não um    *)
(*     único timestamp por registro de sessão composto -- ver              *)
(*     docs/plano-fix-broadcast-lww-2026-09-16.md, decisão D-1.            *)
(*   - Operações O1, O3, O4, O5, O6, O7 conforme Cap. 3 §3.3.1 da          *)
(*     monografia. O2 (Validate) é leitura pura e não altera o estado      *)
(*     do modelo, portanto não tem ação dedicada. Cada ação local também   *)
(*     aplica a comparação LWW sobre a própria escrita (ver decisão D-1    *)
(*     do plano e "Mudança 2b" no relatório da rodada), para que uma       *)
(*     escrita local nunca sobrescreva incondicionalmente um valor de      *)
(*     prioridade maior já aplicado por uma mensagem remota.               *)
(*   - Mudança 3a (docs/plano-fix-cascata-bloqueio-2026-09-16.md): a       *)
(*     mensagem ID_BLOQUEIA carrega sessoesAfetadas (o cascade de          *)
(*     invalidação que Block computa na origem), reaplicado por sessão em  *)
(*     EntregaMensagem, gateado independentemente por sessoesTS/motivosTS  *)
(*     -- corrige o "Bug B": antes, só status era propagado; sessoes/      *)
(*     motivos das sessões afetadas pelo bloqueio nunca convergiam.        *)
(*   - Mudança 3b / Rota (c) (mesmo plano, §3): em EntregaMensagem, o      *)
(*     case CONTADOR_INC desacopla o gate de status do gate de contador    *)
(*     (dois IF independentes, cada um comparando m.prio ao TS do PRÓPRIO  *)
(*     campo) -- corrige o "status órfão" de I6, sem alterar em nada       *)
(*     ResetFailures/CONTADOR_RESET (regra O5: "sem alterar a marca de     *)
(*     bloqueio", Cap. 3 de Metodologia.tex).                              *)
(*   - Invariantes I1..I6 declarados conforme Cap. 3 §3.3.3.               *)
(*                                                                         *)
(* Falhas explícitas (crash, partição, atraso/jitter) ficam para TCC-II,   *)
(* conforme Apêndice B, etapas 3 e 4.                                      *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS IDS,        \* conjunto de identidades modeladas
          SIDS,       \* conjunto de identificadores de sessão potenciais
          REPLICAS,   \* conjunto de réplicas
          LIMITE      \* limite de tentativas antes do bloqueio

ASSUME /\ LIMITE \in Nat /\ LIMITE > 0
       /\ IsFiniteSet(IDS) /\ IsFiniteSet(SIDS) /\ IsFiniteSet(REPLICAS)

VARIABLES sessoes,       \* [r \in REPLICAS |-> [s \in SIDS |-> "NONE" \/ "VALID" \/ "INVALID"]]
          motivos,       \* [r \in REPLICAS |-> [s \in SIDS |-> "NONE" \/ "LOGOUT" \/ "EXPIRADA" \/ "ADMIN_BLOCK"]]
          sessaoDe,      \* [r \in REPLICAS |-> [s \in SIDS |-> "NONE" \/ IDS]]
          contador,      \* [r \in REPLICAS |-> [id \in IDS |-> 0..LIMITE]]
          status,        \* [r \in REPLICAS |-> [id \in IDS |-> "OPEN" \/ "BLOCKED"]]
          canal,         \* [r \in REPLICAS |-> Seq(Mensagem)] -- fila de entrada por replica (broadcast)
          relogio,       \* [REPLICAS -> Nat] -- relogio logico local (Lamport, so avanca em op local)
          sessoesTS,     \* [REPLICAS -> [SIDS -> Nat]] -- prioridade da ultima escrita aplicada a sessoes[r][s]
          motivosTS,     \* [REPLICAS -> [SIDS -> Nat]] -- idem para motivos[r][s]
          sessaoDeTS,    \* [REPLICAS -> [SIDS -> Nat]] -- idem para sessaoDe[r][s]
          contadorTS,    \* [REPLICAS -> [IDS  -> Nat]] -- idem para contador[r][id]
          statusTS       \* [REPLICAS -> [IDS  -> Nat]] -- idem para status[r][id]

vars == << sessoes, motivos, sessaoDe, contador, status, canal,
           relogio, sessoesTS, motivosTS, sessaoDeTS, contadorTS, statusTS >>

Estados   == {"NONE", "VALID", "INVALID"}
MotivosT  == {"NONE", "LOGOUT", "EXPIRADA", "ADMIN_BLOCK"}
StatusT   == {"OPEN", "BLOCKED"}

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

(***************************************************************************)
(* Tipos de mensagens propagadas entre réplicas pelo canal assíncrono.      *)
(***************************************************************************)
(* Nota de tipagem: `payload` carrega uma STRING em todos os tipos exceto    *)
(* SESSAO_VALIDA, cujo payload e a identidade (IDS) que fez login -- por    *)
(* isso o tipo de payload e STRING \cup IDS, nao so STRING. Esta            *)
(* inconsistencia ja existia na especificacao original (o campo `payload`  *)
(* de Login sempre recebeu um valor de IDS), mas so foi exposta quando      *)
(* `canal` passou a fazer parte de TypeOK nesta versao -- o TLC violou      *)
(* TypeOK em 2 estados ate essa correcao ser aplicada (ver                  *)
(* docs/relatorio-tlc-<data>.md).                                           *)
Mensagem ==
  [tipo: {"SESSAO_VALIDA", "SESSAO_INVALIDA",
          "CONTADOR_INC", "CONTADOR_RESET",
          "ID_BLOQUEIA", "ID_DESBLOQUEIA"},
   chave: SIDS \cup IDS,
   payload: STRING \cup IDS,
   valor: 0..LIMITE,   \* usado so por CONTADOR_INC/CONTADOR_RESET; demais tipos usam 0
   ts: Nat,            \* tick logico da replica de origem no instante do envio
   origem: REPLICAS,   \* replica que produziu a mensagem (rastreabilidade/depuracao)
   prio: Nat,           \* Prio(ts, origem), pre-computado para comparacao O(1) na entrega
   sessoesAfetadas: SUBSET SIDS]  \* usado so por ID_BLOQUEIA: cascade do Block calculado
                                   \* na origem (Mudanca 3a); demais tipos usam {}.

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

(***************************************************************************)
(* Estado inicial.                                                          *)
(***************************************************************************)
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

(***************************************************************************)
(* Enfileira msg nas filas de TODAS as replicas exceto a de origem, que ja *)
(* aplicou o efeito localmente e nao precisa entregar a mensagem a si      *)
(* mesma.                                                                   *)
(***************************************************************************)
Broadcast(origem, msg) ==
  [r2 \in REPLICAS |-> IF r2 = origem THEN canal[r2] ELSE Append(canal[r2], msg)]

(***************************************************************************)
(* Operações executadas em uma réplica r.                                   *)
(***************************************************************************)

(* O1, login: cria sessão para id se a identidade está OPEN e a sid livre. *)
(* Mudanca 2b (achado real via TLC, fora do plano original): cada acao de  *)
(* escrita local aplica a MESMA regra de comparacao LWW que EntregaMensagem *)
(* usa para mensagens remotas -- e como se a replica aplicasse a propria    *)
(* mensagem a si mesma pela mesma funcao de merge. Sem isso, uma escrita    *)
(* local pode sobrescrever incondicionalmente um valor de prioridade maior  *)
(* ja aplicado por uma mensagem remota (contraexemplo real: EntregaMensagem *)
(* aplica SESSAO_VALIDA de prio alta vinda de outra replica; a replica      *)
(* entao dispara Logout localmente com prio PROPRIA mais baixa -- porque    *)
(* seu relogio nunca avancou -- e o Logout sobrescrevia sessoes/sessoesTS   *)
(* sem checar isso, "desfazendo" a escrita remota vencedora). O relogio     *)
(* SEMPRE avanca quando a acao local dispara, independente de o valor do    *)
(* campo ter sido efetivamente atualizado; a PRECONDICAO continua avaliada  *)
(* normalmente sobre o estado local atual.                                  *)
Login(r, id, s) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "SESSAO_VALIDA", chave |-> s, payload |-> id, valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
  IN
  /\ status[r][id] = "OPEN"
  /\ sessoes[r][s] = "NONE"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > sessoesTS[r][s]
       THEN /\ sessoes'   = [sessoes   EXCEPT ![r][s] = "VALID"]
            /\ sessoesTS' = [sessoesTS EXCEPT ![r][s] = p]
       ELSE /\ sessoes' = sessoes /\ sessoesTS' = sessoesTS
  /\ IF p > sessaoDeTS[r][s]
       THEN /\ sessaoDe'   = [sessaoDe   EXCEPT ![r][s] = id]
            /\ sessaoDeTS' = [sessaoDeTS EXCEPT ![r][s] = p]
       ELSE /\ sessaoDe' = sessaoDe /\ sessaoDeTS' = sessaoDeTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ contador'   = contador
  /\ contadorTS' = contadorTS
  /\ status'     = status
  /\ statusTS'   = statusTS
  /\ canal'      = Broadcast(r, msg)

(* O3, logout: marca sessão como INVALID com motivo LOGOUT. Idempotente. *)
Logout(r, s) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "SESSAO_INVALIDA", chave |-> s, payload |-> "LOGOUT", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
  IN
  /\ sessoes[r][s] = "VALID"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > sessoesTS[r][s]
       THEN /\ sessoes'   = [sessoes   EXCEPT ![r][s] = "INVALID"]
            /\ sessoesTS' = [sessoesTS EXCEPT ![r][s] = p]
       ELSE /\ sessoes' = sessoes /\ sessoesTS' = sessoesTS
  /\ IF p > motivosTS[r][s]
       THEN /\ motivos'   = [motivos   EXCEPT ![r][s] = "LOGOUT"]
            /\ motivosTS' = [motivosTS EXCEPT ![r][s] = p]
       ELSE /\ motivos' = motivos /\ motivosTS' = motivosTS
  /\ sessaoDe'    = sessaoDe
  /\ sessaoDeTS'  = sessaoDeTS
  /\ contador'    = contador
  /\ contadorTS'  = contadorTS
  /\ status'      = status
  /\ statusTS'    = statusTS
  /\ canal'       = Broadcast(r, msg)

(* O4, incrementFailure: incrementa contador, bloqueia se atingir LIMITE. *)
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

(* O5, resetFailures: zera o contador de id na réplica r.                  *)
(*    Precondição operacional: tipicamente após Login bem-sucedido em      *)
(*    janela, mas o modelo aceita a redefinição como ação livre.           *)
ResetFailures(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "CONTADOR_RESET", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
  IN
  /\ contador[r][id] # 0
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > contadorTS[r][id]
       THEN /\ contador'   = [contador   EXCEPT ![r][id] = 0]
            /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
       ELSE /\ contador' = contador /\ contadorTS' = contadorTS
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ status'     = status
  /\ statusTS'   = statusTS
  /\ canal'      = Broadcast(r, msg)

(* O6, block: marca identidade como BLOCKED e invalida sessões VALID. *)
Block(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      Afetado(s) == sessaoDe[r][s] = id /\ sessoes[r][s] = "VALID"
      msg == [tipo |-> "ID_BLOQUEIA", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p,
               sessoesAfetadas |-> {s \in SIDS : Afetado(s)}]
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

(* O7, unblock: remove a marca BLOCKED e zera o contador.                  *)
(*    Não recria sessões previamente invalidadas pelo bloqueio.            *)
Unblock(r, id) ==
  LET novoTS == relogio[r] + 1
      p == Prio(novoTS, r)
      msg == [tipo |-> "ID_DESBLOQUEIA", chave |-> id, payload |-> "", valor |-> 0,
               ts |-> novoTS, origem |-> r, prio |-> p, sessoesAfetadas |-> {}]
  IN
  /\ status[r][id] = "BLOCKED"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF p > statusTS[r][id]
       THEN /\ status'   = [status   EXCEPT ![r][id] = "OPEN"]
            /\ statusTS' = [statusTS EXCEPT ![r][id] = p]
       ELSE /\ status' = status /\ statusTS' = statusTS
  /\ IF p > contadorTS[r][id]
       THEN /\ contador'   = [contador   EXCEPT ![r][id] = 0]
            /\ contadorTS' = [contadorTS EXCEPT ![r][id] = p]
       ELSE /\ contador' = contador /\ contadorTS' = contadorTS
  /\ sessoes'    = sessoes
  /\ sessoesTS'  = sessoesTS
  /\ motivos'    = motivos
  /\ motivosTS'  = motivosTS
  /\ sessaoDe'   = sessaoDe
  /\ sessaoDeTS' = sessaoDeTS
  /\ canal'      = Broadcast(r, msg)

(***************************************************************************)
(* Entrega de uma mensagem qualquer do canal a uma réplica destino r, com  *)
(* resolucao de conflito last-writer-wins (LWW) por campo: se m.prio for   *)
(* MAIOR que o TS local do campo afetado, a escrita e aplicada e o TS      *)
(* local e atualizado para m.prio; caso contrario, a mensagem e            *)
(* descartada (o local ja tem uma escrita mais recente/vencedora) e NADA   *)
(* muda. `relogio` nunca muda em EntregaMensagem (decisao D-4: sem         *)
(* merge-on-receive).                                                      *)
(***************************************************************************)
EntregaMensagem(r) ==
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ UNCHANGED relogio
       /\ CASE m.tipo = "SESSAO_VALIDA" ->
                (* sessoes e sessaoDe sao registradores LWW INDEPENDENTES  *)
                (* (decisao D-1): cada um so e escrito se m.prio superar o *)
                (* TS do SEU PROPRIO campo, nao o do outro. Logout/Block   *)
                (* avancam sessoesTS sem tocar sessaoDeTS, entao os dois   *)
                (* podem divergir com o tempo -- gatear ambos por          *)
                (* sessoesTS (como numa versao anterior) quebra a          *)
                (* convergencia de sessaoDe quando isso acontece.          *)
                /\ IF m.prio > sessoesTS[r][m.chave]
                     THEN /\ sessoes'   = [sessoes   EXCEPT ![r][m.chave] = "VALID"]
                          /\ sessoesTS' = [sessoesTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ sessoes' = sessoes /\ sessoesTS' = sessoesTS
                /\ IF m.prio > sessaoDeTS[r][m.chave]
                     THEN /\ sessaoDe'   = [sessaoDe   EXCEPT ![r][m.chave] = m.payload]
                          /\ sessaoDeTS' = [sessaoDeTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ sessaoDe' = sessaoDe /\ sessaoDeTS' = sessaoDeTS
                /\ UNCHANGED << motivos, motivosTS, contador, contadorTS,
                                 status, statusTS >>
          [] m.tipo = "SESSAO_INVALIDA" ->
                (* sessoes e motivos sao registradores LWW INDEPENDENTES   *)
                (* pelo mesmo motivo acima -- gatear ambos por sessoesTS   *)
                (* apenas quebraria a convergencia de motivos quando os    *)
                (* dois TS divergirem.                                     *)
                /\ IF m.prio > sessoesTS[r][m.chave]
                     THEN /\ sessoes'   = [sessoes   EXCEPT ![r][m.chave] = "INVALID"]
                          /\ sessoesTS' = [sessoesTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ sessoes' = sessoes /\ sessoesTS' = sessoesTS
                /\ IF m.prio > motivosTS[r][m.chave]
                     THEN /\ motivos'   = [motivos   EXCEPT ![r][m.chave] = m.payload]
                          /\ motivosTS' = [motivosTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ motivos' = motivos /\ motivosTS' = motivosTS
                /\ UNCHANGED << sessaoDe, sessaoDeTS, contador,
                                 contadorTS, status, statusTS >>
          [] m.tipo = "CONTADOR_INC" ->
                (* contador e status sao registradores LWW INDEPENDENTES     *)
                (* (Mudanca 3b / Rota (c), docs/plano-fix-cascata-bloqueio-   *)
                (* 2026-09-16.md §3): o gate de status NAO fica mais aninhado *)
                (* dentro do gate de contador -- m.prio e comparado contra    *)
                (* statusTS por seus PROPRIOS termos, mesmo padrao ja usado   *)
                (* por ID_BLOQUEIA/ID_DESBLOQUEIA. Isto e seguro no caso      *)
                (* REMOTO (mas nao no caso local de IncrementFailure, que     *)
                (* continua acoplado de proposito) porque m.valor/m.prio sao  *)
                (* um fato historico ja consumado na origem, independente de  *)
                (* esta replica aceitar ou nao esse valor para o SEU campo    *)
                (* contador local. Corrige o "status orfao" de I6: antes, um  *)
                (* CONTADOR_INC descartado por LWW (contador de prioridade    *)
                (* menor) tambem descartava o efeito BLOCKED, mesmo quando     *)
                (* esse efeito, por seus proprios termos de statusTS, deveria  *)
                (* ser aceito. ResetFailures/CONTADOR_RESET continuam sem      *)
                (* tocar status, em nenhuma via (regra O5 da monografia: "sem  *)
                (* alterar a marca de bloqueio").                             *)
                /\ IF m.prio > contadorTS[r][m.chave]
                     THEN /\ contador'   = [contador   EXCEPT ![r][m.chave] = m.valor]
                          /\ contadorTS' = [contadorTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ contador' = contador /\ contadorTS' = contadorTS
                /\ IF m.valor >= LIMITE /\ m.prio > statusTS[r][m.chave]
                     THEN /\ status'   = [status   EXCEPT ![r][m.chave] = "BLOCKED"]
                          /\ statusTS' = [statusTS EXCEPT ![r][m.chave] = m.prio]
                     ELSE /\ status' = status /\ statusTS' = statusTS
                /\ UNCHANGED << sessoes, motivos, sessaoDe, sessoesTS, motivosTS, sessaoDeTS >>
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
                (* status e o cascade de sessoes/motivos sao registradores    *)
                (* LWW INDEPENDENTES (mesmo padrao D-1 de SESSAO_VALIDA/      *)
                (* SESSAO_INVALIDA, Mudanca 2c): o gate de status NAO         *)
                (* condiciona o gate de sessoes/motivos, e vice-versa.        *)
                (* m.sessoesAfetadas carrega o cascade BRUTO calculado na     *)
                (* origem (Afetado(s) no momento do Block) -- cabe a CADA     *)
                (* replica decidir, por sessao, se aceita esse fato,          *)
                (* comparando m.prio contra o proprio TS local de cada campo  *)
                (* (Mudanca 3a, corrige o "Bug B": antes, o cascade de        *)
                (* invalidacao do Block nunca era propagado a replicas        *)
                (* remotas, so o status). *)
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

(***************************************************************************)
(* Próximo passo: alguma das ações acima em qualquer réplica.              *)
(***************************************************************************)
Next ==
  \/ \E r \in REPLICAS, id \in IDS, s \in SIDS: Login(r, id, s)
  \/ \E r \in REPLICAS, s \in SIDS:             Logout(r, s)
  \/ \E r \in REPLICAS, id \in IDS:             IncrementFailure(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             ResetFailures(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Block(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Unblock(r, id)
  \/ \E r \in REPLICAS:                          EntregaMensagem(r)

Spec == Init /\ [][Next]_vars

(***************************************************************************)
(* Predicado de quiescência: canal vazio (todas as mensagens entregues).   *)
(* Invariantes I5 e I6 são propriedades condicionadas a este predicado.    *)
(***************************************************************************)
Quiescent == \A r \in REPLICAS: Len(canal[r]) = 0

(***************************************************************************)
(* Invariantes (Cap. 3 §3.3.3 da monografia).                               *)
(***************************************************************************)

(* I1, irreversibilidade da invalidação por logout.                         *)
(* Se em qualquer réplica r1 a sessão s está INVALID com motivo LOGOUT,    *)
(* então nenhuma réplica r2 tem essa sessão como VALID.                     *)
I1 ==
  \A r1 \in REPLICAS, s \in SIDS:
    (sessoes[r1][s] = "INVALID" /\ motivos[r1][s] = "LOGOUT")
      => (\A r2 \in REPLICAS: sessoes[r2][s] # "VALID")

(* I2, consistência de bloqueio.                                            *)
(* Se a identidade id está BLOCKED em alguma réplica r1, nenhuma réplica   *)
(* tem sessão de id como VALID.                                             *)
I2 ==
  \A r1 \in REPLICAS, id \in IDS:
    (status[r1][id] = "BLOCKED") =>
      (\A r2 \in REPLICAS, s \in SIDS:
        (sessaoDe[r2][s] = id) => (sessoes[r2][s] # "VALID"))

(* I3, monotonicidade local do contador. Declarada como invariante de       *)
(* domínio: o contador permanece em 0..LIMITE em toda réplica.              *)
(* A monotonicidade temporal é consequência das ações IncrementFailure,     *)
(* ResetFailures e Unblock, e é certificada por construção.                 *)
I3 ==
  \A r \in REPLICAS, id \in IDS: contador[r][id] \in 0..LIMITE

(* I4, unicidade do motivo de invalidação. Uma vez fixado o motivo \in     *)
(* {LOGOUT, EXPIRADA, ADMIN_BLOCK}, sessoes[r][s] = INVALID e motivos      *)
(* permanece consistente em todas as réplicas que observaram a invalidação.*)
(* Forma operacional: para toda réplica, motivos pertence ao conjunto      *)
(* MotivosT, sem deriva fora do tipo declarado.                             *)
I4 ==
  \A r \in REPLICAS, s \in SIDS: motivos[r][s] \in MotivosT

(* I5, convergência do estado lógico sob quiescência.                       *)
(* Quando o canal está vazio (todas as mensagens entregues), todas as      *)
(* réplicas observam o mesmo estado lógico para cada sessão.                *)
I5 ==
  Quiescent =>
    \A s \in SIDS, r1, r2 \in REPLICAS:
      /\ sessoes[r1][s]  = sessoes[r2][s]
      /\ motivos[r1][s]  = motivos[r2][s]
      /\ sessaoDe[r1][s] = sessaoDe[r2][s]

(* I6, convergência do contador e do status de identidade sob quiescência. *)
I6 ==
  Quiescent =>
    \A id \in IDS, r1, r2 \in REPLICAS:
      /\ contador[r1][id] = contador[r2][id]
      /\ status[r1][id]   = status[r2][id]

(***************************************************************************)
(* THEOREM: apos a correcao de broadcast por replica, a resolucao de       *)
(* conflito LWW via relogio logico de Lamport, e as Mudancas 3a/3b         *)
(* (propagacao do cascade de Block via sessoesAfetadas; gate de status     *)
(* desacoplado do gate de contador em CONTADOR_INC), espera-se que o TLC   *)
(* verifique TypeOK /\ I3 /\ I4 sem contraexemplo, e que I5/I6 fechem sem  *)
(* contraexemplo sob quiescencia nas cardinalidades em que a busca         *)
(* exaustiva for tratavel (ver docs/relatorio-tlc-<data>.md) -- ja nao     *)
(* mais como "causas residuais documentadas", e sim como correcoes         *)
(* verificadas pelo TLC. I1 e I2, por construcao, continuam violados no    *)
(* fluxo nominal: sao propriedades de seguranca *cross-replica* nao         *)
(* condicionadas a quiescencia, e o modelo captura atraso de propagacao    *)
(* real -- ficam para refinamento em TCC-II (Apendice B, etapas 3-4),      *)
(* junto com falhas explicitas (crash/particao/jitter).                    *)
(***************************************************************************)
=============================================================================
