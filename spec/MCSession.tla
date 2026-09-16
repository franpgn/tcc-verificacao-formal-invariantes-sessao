---------------------------- MODULE MCSession ----------------------------
(***************************************************************************)
(* Harness de model checking sobre SessionStore.tla. NAO altera a          *)
(* especificacao original: apenas a estende para tornar a verificacao      *)
(* exaustiva tratavel.                                                     *)
(*                                                                         *)
(*  - LimiteCanal: canal[r], para cada replica r, e uma sequencia          *)
(*    nao-limitada; operacoes como ResetFailures/IncrementFailure podem     *)
(*    ciclar e faze-la crescer sem limite, tornando o espaco de estados     *)
(*    infinito. LimiteCanal impoe Len(canal[r]) =< MaxCanal para toda r     *)
(*    (state constraint), o que finitiza o modelo. E parametro do          *)
(*    experimento.                                                         *)
(*                                                                         *)
(*  - Simetria: IDS, SIDS e REPLICAS sao intercambiaveis na especificacao. *)
(*    A reducao por permutacoes corta classes de estados equivalentes.     *)
(*    Valida para verificacao de invariantes (propriedades de seguranca).  *)
(*                                                                         *)
(* Uso (a partir de spec/):                                                *)
(*   java -cp tla2tools.jar tlc2.TLC -config <arquivo>.cfg \               *)
(*        -workers auto -cleanup MCSession.tla                             *)
(***************************************************************************)
EXTENDS SessionStore, TLC

CONSTANT MaxCanal          \* teto para o comprimento do canal assincrono

LimiteCanal == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal

Simetria == Permutations(IDS) \cup Permutations(SIDS) \cup Permutations(REPLICAS)
==========================================================================
