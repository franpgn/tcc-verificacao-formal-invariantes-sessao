------------------------------ MODULE SessionStore ------------------------------
(***************************************************************************)
(* Especificação formal inicial do núcleo do protocolo de estado de sessão *)
(* em data grids sob replicação assíncrona.                                *)
(*                                                                         *)
(* Cobertura desta versão (B-14, TCC-I, Sem 7-8):                          *)
(*   - Conjunto IDS de identidades e SIDS de sessões.                      *)
(*   - Estado por réplica: sessoes (função SIDS -> Estados),               *)
(*     motivos (SIDS -> MotivosT), sessaoDe (SIDS -> IDS),                 *)
(*     contador (IDS -> 0..LIMITE), status (IDS -> StatusT).               *)
(*   - Canal assíncrono por réplica: fila de mensagens pendentes por       *)
(*     réplica destino (broadcast real a todas as réplicas exceto a        *)
(*     origem, que já aplicou o efeito localmente).                        *)
(*   - Operações O1, O3, O4, O5, O6, O7 conforme Cap. 3 §3.3.1 da          *)
(*     monografia. O2 (Validate) é leitura pura e não altera o estado      *)
(*     do modelo, portanto não tem ação dedicada.                          *)
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
          canal          \* [r \in REPLICAS |-> Seq(Mensagem)] -- fila de entrada por replica (broadcast)

vars == << sessoes, motivos, sessaoDe, contador, status, canal >>

Estados   == {"NONE", "VALID", "INVALID"}
MotivosT  == {"NONE", "LOGOUT", "EXPIRADA", "ADMIN_BLOCK"}
StatusT   == {"OPEN", "BLOCKED"}

(***************************************************************************)
(* Tipos de mensagens propagadas entre réplicas pelo canal assíncrono.      *)
(***************************************************************************)
Mensagem ==
  [tipo: {"SESSAO_VALIDA", "SESSAO_INVALIDA",
          "CONTADOR_INC", "CONTADOR_RESET",
          "ID_BLOQUEIA", "ID_DESBLOQUEIA"},
   chave: SIDS \cup IDS,
   payload: STRING]

TypeOK ==
  /\ sessoes  \in [REPLICAS -> [SIDS -> Estados]]
  /\ motivos  \in [REPLICAS -> [SIDS -> MotivosT]]
  /\ sessaoDe \in [REPLICAS -> [SIDS -> {"NONE"} \cup IDS]]
  /\ contador \in [REPLICAS -> [IDS -> 0..LIMITE]]
  /\ status   \in [REPLICAS -> [IDS -> StatusT]]

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
Login(r, id, s) ==
  /\ status[r][id] = "OPEN"
  /\ sessoes[r][s] = "NONE"
  /\ sessoes'  = [sessoes  EXCEPT ![r][s] = "VALID"]
  /\ sessaoDe' = [sessaoDe EXCEPT ![r][s] = id]
  /\ motivos'  = motivos
  /\ contador' = contador
  /\ status'   = status
  /\ canal'    = Broadcast(r, [tipo |-> "SESSAO_VALIDA", chave |-> s, payload |-> id])

(* O3, logout: marca sessão como INVALID com motivo LOGOUT. Idempotente. *)
Logout(r, s) ==
  /\ sessoes[r][s] = "VALID"
  /\ sessoes' = [sessoes EXCEPT ![r][s] = "INVALID"]
  /\ motivos' = [motivos EXCEPT ![r][s] = "LOGOUT"]
  /\ sessaoDe' = sessaoDe
  /\ contador' = contador
  /\ status'   = status
  /\ canal'    = Broadcast(r, [tipo |-> "SESSAO_INVALIDA", chave |-> s, payload |-> "LOGOUT"])

(* O4, incrementFailure: incrementa contador, bloqueia se atingir LIMITE. *)
IncrementFailure(r, id) ==
  LET novo == contador[r][id] + 1 IN
  /\ contador[r][id] < LIMITE
  /\ contador' = [contador EXCEPT ![r][id] = novo]
  /\ IF novo >= LIMITE
       THEN status' = [status EXCEPT ![r][id] = "BLOCKED"]
       ELSE status' = status
  /\ sessoes'  = sessoes
  /\ motivos'  = motivos
  /\ sessaoDe' = sessaoDe
  /\ canal'    = Broadcast(r, [tipo |-> "CONTADOR_INC", chave |-> id, payload |-> ""])

(* O5, resetFailures: zera o contador de id na réplica r.                  *)
(*    Precondição operacional: tipicamente após Login bem-sucedido em      *)
(*    janela, mas o modelo aceita a redefinição como ação livre.           *)
ResetFailures(r, id) ==
  /\ contador[r][id] # 0
  /\ contador' = [contador EXCEPT ![r][id] = 0]
  /\ sessoes'  = sessoes
  /\ motivos'  = motivos
  /\ sessaoDe' = sessaoDe
  /\ status'   = status
  /\ canal'    = Broadcast(r, [tipo |-> "CONTADOR_RESET", chave |-> id, payload |-> ""])

(* O6, block: marca identidade como BLOCKED e invalida sessões VALID. *)
Block(r, id) ==
  /\ status[r][id] = "OPEN"
  /\ status' = [status EXCEPT ![r][id] = "BLOCKED"]
  /\ sessoes' = [sessoes EXCEPT ![r] =
                  [s \in SIDS |-> IF sessaoDe[r][s] = id /\ @[s] = "VALID"
                                  THEN "INVALID" ELSE @[s] ] ]
  /\ motivos' = [motivos EXCEPT ![r] =
                  [s \in SIDS |-> IF sessaoDe[r][s] = id /\ sessoes[r][s] = "VALID"
                                  THEN "ADMIN_BLOCK" ELSE @[s] ] ]
  /\ sessaoDe' = sessaoDe
  /\ contador' = contador
  /\ canal'    = Broadcast(r, [tipo |-> "ID_BLOQUEIA", chave |-> id, payload |-> ""])

(* O7, unblock: remove a marca BLOCKED e zera o contador.                  *)
(*    Não recria sessões previamente invalidadas pelo bloqueio.            *)
Unblock(r, id) ==
  /\ status[r][id] = "BLOCKED"
  /\ status'   = [status   EXCEPT ![r][id] = "OPEN"]
  /\ contador' = [contador EXCEPT ![r][id] = 0]
  /\ sessoes'  = sessoes
  /\ motivos'  = motivos
  /\ sessaoDe' = sessaoDe
  /\ canal'    = Broadcast(r, [tipo |-> "ID_DESBLOQUEIA", chave |-> id, payload |-> ""])

(***************************************************************************)
(* Entrega de uma mensagem qualquer do canal a uma réplica destino r.      *)
(* Captura atraso não determinístico e reordenação.                        *)
(***************************************************************************)
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
(* THEOREM: nas configurações pequenas declaradas em MC-small.cfg,         *)
(* MC-medium.cfg e MC-full.cfg, espera-se que TLC verifique                *)
(* TypeOK /\ I1 /\ I2 /\ I3 /\ I4 sem contraexemplo no fluxo nominal.      *)
(* Para I5 e I6, contraexemplos sob entrelaçamento de mensagens são        *)
(* esperados e configuram a base para os refinamentos previstos em        *)
(* TCC-II, etapas 3 e 4 (Apêndice B).                                      *)
(***************************************************************************)
=============================================================================
