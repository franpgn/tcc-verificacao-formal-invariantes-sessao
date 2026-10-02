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
(*  - LimiteRelogio: relogio[r], para cada replica r, e um Nat sem teto,    *)
(*    analogo ao campo `versao` de SessionState.java/IdentityState.java;   *)
(*    cada acao local o incrementa em 1, tornando o espaco de estados      *)
(*    infinito tambem por essa via. LimiteRelogio impoe                    *)
(*    relogio[r] =< MaxRelogio para toda r (state constraint).             *)
(*                                                                         *)
(*  - Simetria: a partir da revisao de 2026-10-02 (Achado E -- colapso de  *)
(*    sessoes/identidades em registro unico por chave, ver                *)
(*    docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md), REPLICAS  *)
(*    volta a ser um conjunto simetrico. O "relogio logico de Lamport com  *)
(*    desempate deterministico por identidade de replica" (RepIndex/Prio) *)
(*    que quebrava essa simetria na versao anterior (2026-09-16) foi       *)
(*    removido: o gate de EntregaMensagem agora compara apenas `ts`        *)
(*    (contador de versao local, sem nenhum termo dependente de QUAL       *)
(*    replica originou a escrita), entao permutar REPLICAS permuta um      *)
(*    comportamento valido em outro comportamento valido. IDS e SIDS       *)
(*    permanecem intercambiaveis como antes.                               *)
(*                                                                         *)
(* Uso (a partir de spec/):                                                *)
(*   java -cp tla2tools.jar tlc2.TLC -config <arquivo>.cfg \               *)
(*        -workers auto -cleanup MCSession.tla                             *)
(***************************************************************************)
EXTENDS SessionStore, TLC

CONSTANT MaxCanal,    \* teto para o comprimento de CADA fila canal[r]
         MaxRelogio   \* teto para o valor de CADA contador de versao relogio[r]

LimiteCanal   == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal
LimiteRelogio == \A r \in REPLICAS: relogio[r] =< MaxRelogio

Simetria == Permutations(IDS) \cup Permutations(SIDS) \cup Permutations(REPLICAS)
==========================================================================
