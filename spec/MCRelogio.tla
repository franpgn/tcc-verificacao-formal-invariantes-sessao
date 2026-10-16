---------------------------- MODULE MCRelogio ----------------------------
(***************************************************************************)
(* B-36 / Q-5 (docs/decisoes-senior-2026-10-16.md SS1): VARIANTE do        *)
(* harness em que a entrega de mensagem atualiza o relogio local por       *)
(* max (Lamport), isto e, relogio[r]' = max(relogio[r], ts da mensagem).   *)
(*                                                                         *)
(* SessionStore.tla e MCSession.tla NAO foram alterados. Este modulo       *)
(* estende MCSession e redefine UMA acao: EntregaMensagemMax(r) e' copia   *)
(* LITERAL do corpo de EntregaMensagem(r) de SessionStore.tla, trocando    *)
(* apenas a linha "UNCHANGED <<relogio, ...>> por                          *)
(*   relogio' = [relogio EXCEPT ![r] = Max(@, TsMsg(m))]                    *)
(* e um UNCHANGED sem relogio. A fidelidade da copia e' verificada por     *)
(* spec/check-variante-relogio.sh (diff vazio apos normalizar essas linhas).*)
(*                                                                         *)
(* Finalidade: testar se as violacoes do modelo livre atribuidas a D-4     *)
(* (relogio que nao acompanha o ts recebido; P-26) desaparecem quando o    *)
(* relogio acompanha. RelogioDomina e' a propriedade que o codigo real tem *)
(* por construcao (SessionState.invalidar usa versao+1 do registro lido).  *)
(*                                                                         *)
(* Uso: ver spec/run-tlc-relogio.sh. Resultado da variante NUNCA entra no  *)
(* catalogo como evidencia do sistema; so' reclassifica o artefato D-4.    *)
(***************************************************************************)
EXTENDS MCSession

Max(a, b) == IF a > b THEN a ELSE b

\* ts da mensagem: o campo nao usado carrega o registro vazio (ts = 0),
\* entao o max cobre todos os tipos de mensagem.
TsMsg(m) == Max(m.sessao.ts, m.ident.ts)

\* Copia literal de EntregaMensagem(r) (SessionStore.tla), exceto as duas
\* linhas de relogio/UNCHANGED indicadas no cabecalho.
EntregaMensagemMax(r) ==
  /\ PodeAgir(r)   \* F1+F2 (B-22): réplica caída ou do lado minoritário não processa nem a propria fila
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ relogio' = [relogio EXCEPT ![r] = Max(@, TsMsg(m))]
       /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe, particoes>>
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
                (* status (via ident) e a cascata de sessoes (via sessao, molde único  *)
                (* aplicado a cada sessão em sessoesAfetadas) são gateados             *)
                (* INDEPENDENTEMENTE um do outro -- cada um compara m.*.ts contra o    *)
                (* ts do SEU PRÓPRIO registro local, não um gate aninhado no outro.    *)
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

NextMax ==
  \/ \E r \in REPLICAS, id \in IDS, s \in SIDS: Login(r, id, s)
  \/ \E r \in REPLICAS, s \in SIDS:             Logout(r, s)
  \/ \E r \in REPLICAS, id \in IDS:             IncrementFailure(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             ResetFailures(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Block(r, id)
  \/ \E r \in REPLICAS, id \in IDS:             Unblock(r, id)
  \/ \E r \in REPLICAS:                          EntregaMensagemMax(r)
  \/ \E r \in REPLICAS:                          Cair(r)
  \/ \E r \in REPLICAS:                          Reviver(r)
  \/ Particionar
  \/ Mesclar

SpecRelogio         == Init /\ [][NextMax]_vars          \* livre
SpecRelogioSemeada  == InitSemeado /\ [][NextMax]_vars   \* semeado

\* Invariante de sanidade da variante (propriedade que o codigo tem por construcao):
\* o relogio local nunca e' menor que o ts de qualquer registro local, logo o gate
\* de ts de uma escrita local nunca a rejeita.
RelogioDomina ==
  \A r \in REPLICAS :
    /\ \A s \in SIDS : relogio[r] >= sessoes[r][s].ts
    /\ \A id \in IDS : relogio[r] >= identidades[r][id].ts

==========================================================================
