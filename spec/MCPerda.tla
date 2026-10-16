---------------------------- MODULE MCPerda ----------------------------
(***************************************************************************)
(* B-35 / T-I (docs/decisoes-senior-2026-10-16.md SS2): VARIANTE do        *)
(* harness com perda de mensagens no canal. Modulo aditivo: estende        *)
(* MCSession e NAO altera SessionStore.tla nem MCSession.tla. A variavel   *)
(* `perdas` (orcamento de perdas) e' declarada AQUI, de modo que `vars`    *)
(* do modulo base fica intacto; as propriedades de MCSession, que sao      *)
(* [][...]_vars, continuam validas porque a perda altera `canal`, que esta *)
(* em `vars`.                                                              *)
(*                                                                         *)
(* Dois sabores de perda:                                                  *)
(*  - P0 (PerderQualquer): limite superior, ilustrativo; qualquer          *)
(*    mensagem de qualquer canal, a qualquer momento.                      *)
(*  - P1 (PerderNaVista): so' mensagem endereçada a replica que NAO pode   *)
(*    agir (caida ou do lado minoritario), isto e, perda ligada a mudanca  *)
(*    de vista. Sem falha, PerderNaVista nunca habilita (vacuo; demonstrar *)
(*    com -coverage 1, nao supor).                                         *)
(*                                                                         *)
(* Nota: os dois quantificadores sao aninhados (\E r : \E i), nao em lista *)
(* "r, i": em TLA+ o limite de i nao pode depender de r na mesma lista.     *)
(*                                                                         *)
(* RemoverMsg e' a MESMA expressao de canal' de EntregaMensagem(r)         *)
(* (SessionStore.tla). A fidelidade e as listas UNCHANGED sao verificadas  *)
(* por spec/check-variante-perda.sh. MaxPerdas = 0 deve ser equivalente a  *)
(* base (controle de regressao, B-35.2).                                   *)
(*                                                                         *)
(* Sem retransmissao, transferencia de estado nem resolucao de conflito:   *)
(* violacao de I5/I6 aqui e' POR CONSTRUCAO e nao diz nada sobre perda de  *)
(* dados no Infinispan. Resultado ILUSTRATIVO; nunca entra no catalogo     *)
(* como evidencia do sistema.                                              *)
(***************************************************************************)
EXTENDS MCSession

CONSTANT MaxPerdas   \* teto de perdas por comportamento (0 = base)
VARIABLE perdas      \* orcamento consumido

varsP == << vars, perdas >>

\* Remove a i-esima mensagem do canal de r (mesma expressao de EntregaMensagem(r)).
RemoverMsg(r, i) ==
  canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]

\* P0 (limite superior, ilustrativo): qualquer mensagem, em qualquer momento.
PerderQualquer ==
  /\ perdas < MaxPerdas
  /\ \E r \in REPLICAS : \E i \in 1..Len(canal[r]) : RemoverMsg(r, i)
  /\ perdas' = perdas + 1
  /\ UNCHANGED <<sessoes, identidades, relogio, vivo, quedas, particaoAtiva, ladoDe, particoes>>

\* P1 (defensavel): so' perde mensagem destinada a replica que NAO pode agir
\* (caida ou do lado minoritario), isto e, ligada a mudanca de vista.
PerderNaVista ==
  /\ perdas < MaxPerdas
  /\ \E r \in REPLICAS : \E i \in 1..Len(canal[r]) : ~PodeAgir(r) /\ RemoverMsg(r, i)
  /\ perdas' = perdas + 1
  /\ UNCHANGED <<sessoes, identidades, relogio, vivo, quedas, particaoAtiva, ladoDe, particoes>>

PerdasOK == perdas \in 0..MaxPerdas

InitP        == Init /\ perdas = 0
InitSemeadoP == InitSemeado /\ perdas = 0
NextP0 == (Next /\ UNCHANGED perdas) \/ PerderQualquer
NextP1 == (Next /\ UNCHANGED perdas) \/ PerderNaVista

SpecPerda0Semeada == InitSemeadoP /\ [][NextP0]_varsP
SpecPerda1Semeada == InitSemeadoP /\ [][NextP1]_varsP
SpecPerda0Livre   == InitP        /\ [][NextP0]_varsP
SpecPerda1Livre   == InitP        /\ [][NextP1]_varsP

==========================================================================
