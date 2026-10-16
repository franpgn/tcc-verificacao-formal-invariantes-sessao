---- MODULE MCRelogioSL ----
EXTENDS MCRelogio
\* Experimento de scratch (V-R3): Login so' se a sid esta livre GLOBALMENTE (nenhuma replica a conhece,
\* nenhuma mensagem em voo sobre ela). Elimina Login concorrente da mesma sid.
SidLivre(s) == /\ \A q \in REPLICAS : sessoes[q][s].estado = "NONE"
               /\ \A q \in REPLICAS : \A i \in 1..Len(canal[q]) : canal[q][i].chave # s
NextSL ==
  \/ \E r \in REPLICAS, id \in IDS, s \in SIDS: SidLivre(s) /\ Login(r, id, s)
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
SpecSL == Init /\ [][NextSL]_vars
====
