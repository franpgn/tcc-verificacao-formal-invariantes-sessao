------------------------------ MODULE MCFifo ------------------------------
(***************************************************************************)
(* B-45, controle FIFO (refuta ou confirma N-4 do plano de 04/11, §9,      *)
(* Prompt 3, item 3). NAO e' a especificacao do protocolo: e' uma copia    *)
(* minima de spec/SessionStore.tla na versao SEM desempate (git 20805b0),  *)
(* gerada mecanicamente (comentarios retirados), com duas mudancas:        *)
(*   (1) toda mensagem carrega `origem` (a replica que a difundiu);        *)
(*   (2) EntregaMensagem(r) so' entrega canal[r][i] se nenhuma mensagem    *)
(*       anterior na fila de r tem a mesma origem: canal FIFO por par      *)
(*       (origem, destino), com intercalacao livre entre origens.          *)
(*       Com 2 replicas isso e' FIFO total da fila de destino.             *)
(* Os gates continuam "ts estritamente maior" (sem desempate). Mais o      *)
(* harness de MCSession.tla (limites, simetria original, cenario semeado). *)
(* Pergunta: canal com entrega em ordem elimina os tracos de empate de     *)
(* I5/I6? Expectativa pre-registrada (docs/relatorio-b45-desempate-ts.md): *)
(* nao, porque as duas escritas empatadas nascem em replicas diferentes.   *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS IDS,
          SIDS,
          REPLICAS,
          LIMITE,
          MaxCanal, MaxRelogio, MaxEventosFalha

ASSUME /\ LIMITE \in Nat /\ LIMITE > 0
       /\ IsFiniteSet(IDS) /\ IsFiniteSet(SIDS) /\ IsFiniteSet(REPLICAS)

VARIABLES sessoes,
          identidades,
          canal,
          relogio,

          vivo,
          quedas,
          particaoAtiva,
          ladoDe,

          particoes

vars == << sessoes, identidades, canal, relogio, vivo, quedas,
           particaoAtiva, ladoDe, particoes >>

Estados   == {"NONE", "VALID", "INVALID"}
MotivosT  == {"NONE", "LOGOUT", "EXPIRADA", "ADMIN_BLOCK"}
StatusT   == {"OPEN", "BLOCKED"}

SessaoRec == [estado: Estados, motivo: MotivosT, identidade: {"NONE"} \cup IDS, ts: Nat]
IdentRec  == [contador: 0..LIMITE, status: StatusT, ts: Nat]

SessaoVazia == [estado |-> "NONE", motivo |-> "NONE", identidade |-> "NONE", ts |-> 0]
IdentVazia  == [contador |-> 0, status |-> "OPEN", ts |-> 0]

Mensagem ==
  [tipo: {"SESSAO_VALIDA", "SESSAO_INVALIDA",
          "CONTADOR_INC", "CONTADOR_RESET",
          "ID_BLOQUEIA", "ID_DESBLOQUEIA"},
   chave: SIDS \cup IDS,
   sessao: SessaoRec,
   ident: IdentRec,
   sessoesAfetadas: SUBSET SIDS,
   origem: REPLICAS]

TypeOK ==
  /\ sessoes     \in [REPLICAS -> [SIDS -> SessaoRec]]
  /\ identidades \in [REPLICAS -> [IDS -> IdentRec]]
  /\ canal       \in [REPLICAS -> Seq(Mensagem)]
  /\ relogio     \in [REPLICAS -> Nat]
  /\ vivo        \in [REPLICAS -> BOOLEAN]
  /\ quedas      \in Nat
  /\ particaoAtiva \in BOOLEAN
  /\ ladoDe        \in [REPLICAS -> {1, 2}]
  /\ particoes     \in Nat

Init ==
  /\ sessoes     = [r \in REPLICAS |-> [s \in SIDS |-> SessaoVazia]]
  /\ identidades = [r \in REPLICAS |-> [id \in IDS |-> IdentVazia]]
  /\ canal       = [r \in REPLICAS |-> << >>]
  /\ relogio     = [r \in REPLICAS |-> 0]
  /\ vivo        = [r \in REPLICAS |-> TRUE]
  /\ quedas      = 0
  /\ particaoAtiva = FALSE
  /\ ladoDe        = [r \in REPLICAS |-> 1]
  /\ particoes     = 0

Broadcast(origem, msg) ==
  [r2 \in REPLICAS |-> IF r2 = origem THEN canal[r2] ELSE Append(canal[r2], msg)]

Lado(n) == {r \in REPLICAS : ladoDe[r] = n}

Vencedor(P1, P2) ==
  IF Cardinality(P1) > Cardinality(P2) THEN {P1}
  ELSE IF Cardinality(P2) > Cardinality(P1) THEN {P2}
  ELSE {P1, P2}

PodeAgir(r) ==
  /\ vivo[r] = TRUE
  /\ \/ ~particaoAtiva
     \/ LET meuLado == Lado(ladoDe[r]) IN
          meuLado \in Vencedor(Lado(1), Lado(2))

Login(r, id, s) ==
  LET novoTS == relogio[r] + 1
      candidato == [estado |-> "VALID", motivo |-> "NONE", identidade |-> id, ts |-> novoTS]
      msg == [origem |-> r, tipo |-> "SESSAO_VALIDA", chave |-> s,
              sessao |-> candidato, ident |-> IdentVazia, sessoesAfetadas |-> {}]
  IN
  /\ PodeAgir(r)
  /\ identidades[r][id].status = "OPEN"
  /\ sessoes[r][s].estado = "NONE"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > sessoes[r][s].ts
       THEN sessoes' = [sessoes EXCEPT ![r][s] = candidato]
       ELSE sessoes' = sessoes
  /\ canal' = Broadcast(r, msg)
  /\ identidades' = identidades
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>

Logout(r, s) ==
  LET novoTS == relogio[r] + 1
      candidato == [estado |-> "INVALID", motivo |-> "LOGOUT",
                    identidade |-> sessoes[r][s].identidade, ts |-> novoTS]
      msg == [origem |-> r, tipo |-> "SESSAO_INVALIDA", chave |-> s,
              sessao |-> candidato, ident |-> IdentVazia, sessoesAfetadas |-> {}]
  IN
  /\ PodeAgir(r)
  /\ sessoes[r][s].estado = "VALID"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > sessoes[r][s].ts
       THEN sessoes' = [sessoes EXCEPT ![r][s] = candidato]
       ELSE sessoes' = sessoes
  /\ canal' = Broadcast(r, msg)
  /\ identidades' = identidades
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>

IncrementFailure(r, id) ==
  LET novoTS == relogio[r] + 1
      novoContador == identidades[r][id].contador + 1
      novoStatus == IF novoContador >= LIMITE THEN "BLOCKED" ELSE identidades[r][id].status
      candidato == [contador |-> novoContador, status |-> novoStatus, ts |-> novoTS]
      msg == [origem |-> r, tipo |-> "CONTADOR_INC", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ PodeAgir(r)
  /\ identidades[r][id].contador < LIMITE
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN /\ identidades' = [identidades EXCEPT ![r][id] = candidato]
            /\ canal' = Broadcast(r, msg)
       ELSE /\ identidades' = identidades
            /\ canal' = canal
  /\ sessoes' = sessoes
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>

ResetFailures(r, id) ==
  LET novoTS == relogio[r] + 1
      candidato == [contador |-> 0, status |-> identidades[r][id].status, ts |-> novoTS]
      msg == [origem |-> r, tipo |-> "CONTADOR_RESET", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ PodeAgir(r)
  /\ identidades[r][id].contador # 0
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidato]
       ELSE identidades' = identidades
  /\ canal' = Broadcast(r, msg)
  /\ sessoes' = sessoes
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>

Block(r, id) ==
  LET novoTS == relogio[r] + 1
      Afetado(s) == sessoes[r][s].identidade = id /\ sessoes[r][s].estado = "VALID"
      afetadas == {s \in SIDS : Afetado(s)}
      candidatoId == [contador |-> identidades[r][id].contador, status |-> "BLOCKED", ts |-> novoTS]
      candidatoSessao == [estado |-> "INVALID", motivo |-> "ADMIN_BLOCK",
                          identidade |-> id, ts |-> novoTS]
      msg == [origem |-> r, tipo |-> "ID_BLOQUEIA", chave |-> id,
              sessao |-> candidatoSessao, ident |-> candidatoId, sessoesAfetadas |-> afetadas]
  IN
  /\ PodeAgir(r)
  /\ identidades[r][id].status = "OPEN"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidatoId]
       ELSE identidades' = identidades
  /\ sessoes' = [sessoes EXCEPT ![r] =
                  [s \in SIDS |-> IF s \in afetadas /\ novoTS > @[s].ts
                                  THEN candidatoSessao ELSE @[s]]]
  /\ canal' = Broadcast(r, msg)
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>

Unblock(r, id) ==
  LET novoTS == relogio[r] + 1
      candidato == [contador |-> 0, status |-> "OPEN", ts |-> novoTS]
      msg == [origem |-> r, tipo |-> "ID_DESBLOQUEIA", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ PodeAgir(r)
  /\ identidades[r][id].status = "BLOCKED"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidato]
       ELSE identidades' = identidades
  /\ canal' = Broadcast(r, msg)
  /\ sessoes' = sessoes
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>

Cair(r) ==
  /\ vivo[r] = TRUE
  /\ ~particaoAtiva
  /\ vivo' = [vivo EXCEPT ![r] = FALSE]
  /\ quedas' = quedas + 1
  /\ UNCHANGED <<sessoes, identidades, canal, relogio, particaoAtiva, ladoDe, particoes>>

Reviver(r) ==
  /\ vivo[r] = FALSE
  /\ vivo' = [vivo EXCEPT ![r] = TRUE]
  /\ UNCHANGED <<sessoes, identidades, canal, relogio, quedas, particaoAtiva, ladoDe, particoes>>

Particionar ==
  /\ ~particaoAtiva
  /\ \A r \in REPLICAS: vivo[r] = TRUE
  /\ \E P1 \in SUBSET REPLICAS \ {{}, REPLICAS} :
       LET P2 == REPLICAS \ P1 IN
       /\ particaoAtiva' = TRUE
       /\ ladoDe' = [r \in REPLICAS |-> IF r \in P1 THEN 1 ELSE 2]
       /\ particoes' = particoes + 1
       /\ UNCHANGED <<sessoes, identidades, canal, relogio, vivo, quedas>>

Mesclar ==
  /\ particaoAtiva
  /\ \E ladoVencedor \in Vencedor(Lado(1), Lado(2)) :
     \E rf \in ladoVencedor :
       LET perdedores == REPLICAS \ ladoVencedor IN
       /\ sessoes'     = [r \in REPLICAS |-> IF r \in perdedores THEN sessoes[rf]     ELSE sessoes[r]]
       /\ identidades' = [r \in REPLICAS |-> IF r \in perdedores THEN identidades[rf] ELSE identidades[r]]
       /\ relogio'      = [r \in REPLICAS |-> IF r \in perdedores THEN relogio[rf]     ELSE relogio[r]]
       /\ particaoAtiva' = FALSE
       /\ UNCHANGED <<canal, vivo, quedas, ladoDe, particoes>>

EntregaMensagem(r) ==
  /\ PodeAgir(r)
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ \A j \in 1..(i - 1) : canal[r][j].origem # m.origem
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ UNCHANGED <<relogio, vivo, quedas, particaoAtiva, ladoDe, particoes>>
       /\ CASE m.tipo = "SESSAO_VALIDA" ->
                /\ IF m.sessao.ts > sessoes[r][m.chave].ts
                     THEN sessoes' = [sessoes EXCEPT ![r][m.chave] = m.sessao]
                     ELSE sessoes' = sessoes
                /\ UNCHANGED identidades
            [] m.tipo = "SESSAO_INVALIDA" ->
                /\ IF m.sessao.ts > sessoes[r][m.chave].ts
                     THEN sessoes' = [sessoes EXCEPT ![r][m.chave] = m.sessao]
                     ELSE sessoes' = sessoes
                /\ UNCHANGED identidades
            [] m.tipo = "CONTADOR_INC" ->
                /\ IF m.ident.ts > identidades[r][m.chave].ts
                     THEN identidades' = [identidades EXCEPT ![r][m.chave] = m.ident]
                     ELSE identidades' = identidades
                /\ UNCHANGED sessoes
            [] m.tipo = "CONTADOR_RESET" ->
                /\ IF m.ident.ts > identidades[r][m.chave].ts
                     THEN identidades' = [identidades EXCEPT ![r][m.chave] = m.ident]
                     ELSE identidades' = identidades
                /\ UNCHANGED sessoes
            [] m.tipo = "ID_BLOQUEIA" ->
                /\ IF m.ident.ts > identidades[r][m.chave].ts
                     THEN identidades' = [identidades EXCEPT ![r][m.chave] = m.ident]
                     ELSE identidades' = identidades
                /\ sessoes' = [sessoes EXCEPT ![r] =
                                [s \in SIDS |-> IF s \in m.sessoesAfetadas /\ m.sessao.ts > @[s].ts
                                                THEN m.sessao ELSE @[s]]]
            [] m.tipo = "ID_DESBLOQUEIA" ->
                /\ IF m.ident.ts > identidades[r][m.chave].ts
                     THEN identidades' = [identidades EXCEPT ![r][m.chave] = m.ident]
                     ELSE identidades' = identidades
                /\ UNCHANGED sessoes
            [] OTHER -> UNCHANGED << sessoes, identidades >>

Next ==
  \/ \E r \in REPLICAS, id \in IDS, s \in SIDS: Login(r, id, s)
  \/ \E r \in REPLICAS, s \in SIDS:             Logout(r, s)
  \/ \E r \in REPLICAS, id \in IDS:             IncrementFailure(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             ResetFailures(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Block(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Unblock(r, id)
  \/ \E r \in REPLICAS:                          EntregaMensagem(r)
  \/ \E r \in REPLICAS:                          Cair(r)
  \/ \E r \in REPLICAS:                          Reviver(r)
  \/ Particionar
  \/ Mesclar

Spec == Init /\ [][Next]_vars

Quiescent == \A r \in REPLICAS: Len(canal[r]) = 0

I1 ==
  \A r1 \in REPLICAS, s \in SIDS:
    (sessoes[r1][s].estado = "INVALID" /\ sessoes[r1][s].motivo = "LOGOUT")
      => (\A r2 \in REPLICAS: sessoes[r2][s].estado # "VALID")

I2 ==
  \A r1 \in REPLICAS, id \in IDS:
    (identidades[r1][id].status = "BLOCKED") =>
      (\A r2 \in REPLICAS, s \in SIDS:
        (sessoes[r2][s].identidade = id) => (sessoes[r2][s].estado # "VALID"))

I3 ==
  \A r \in REPLICAS, id \in IDS: identidades[r][id].contador \in 0..LIMITE

I4 ==
  \A r \in REPLICAS, s \in SIDS: sessoes[r][s].motivo \in MotivosT

I5 ==
  Quiescent =>
    \A s \in SIDS, r1, r2 \in REPLICAS:
      /\ sessoes[r1][s].estado     = sessoes[r2][s].estado
      /\ sessoes[r1][s].motivo     = sessoes[r2][s].motivo
      /\ sessoes[r1][s].identidade = sessoes[r2][s].identidade

I6 ==
  Quiescent =>
    \A id \in IDS, r1, r2 \in REPLICAS:
      /\ identidades[r1][id].contador = identidades[r2][id].contador
      /\ identidades[r1][id].status   = identidades[r2][id].status


(***************************************************************************)
(* Harness copiado de MCSession.tla (git 20805b0), sem as propriedades de  *)
(* acao: limites, simetria original (inclui IDS: aqui nao ha desempate),   *)
(* cenario semeado e restricoes de coluna de falha.                        *)
(***************************************************************************)
LimiteCanal        == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal
LimiteRelogio      == \A r \in REPLICAS: relogio[r] =< MaxRelogio
LimiteEventosFalha == quedas + particoes =< MaxEventosFalha

Simetria == Permutations(IDS) \cup Permutations(SIDS) \cup Permutations(REPLICAS)

IdSemente == CHOOSE id \in IDS : TRUE
InitSemeado ==
  /\ sessoes = [r \in REPLICAS |-> [s \in SIDS |->
        [estado |-> "VALID", motivo |-> "NONE", identidade |-> IdSemente, ts |-> 1]]]
  /\ identidades = [r \in REPLICAS |-> [id \in IDS |->
        [contador |-> 0, status |-> "OPEN", ts |-> 1]]]
  /\ canal = [r \in REPLICAS |-> << >>]
  /\ relogio = [r \in REPLICAS |-> 1]
  /\ vivo = [r \in REPLICAS |-> TRUE]
  /\ quedas = 0
  /\ particaoAtiva = FALSE
  /\ ladoDe = [r \in REPLICAS |-> 1]
  /\ particoes = 0
SpecSemeada == InitSemeado /\ [][Next]_vars

SemFalha   == quedas + particoes = 0
SoQueda    == particoes = 0
SoParticao == quedas = 0

=============================================================================
