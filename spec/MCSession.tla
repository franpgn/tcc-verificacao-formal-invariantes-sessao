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
(*  - LimiteRelogio: relogio[r], para cada replica r, e um Nat sem teto;    *)
(*    cada acao local o incrementa em 1, tornando o espaco de estados       *)
(*    infinito tambem por essa via. LimiteRelogio impoe                    *)
(*    relogio[r] =< MaxRelogio para toda r (state constraint).             *)
(*                                                                         *)
(*  - Simetria: IDS e SIDS permanecem intercambiaveis na especificacao.    *)
(*    REPLICAS NAO MAIS, pois a partir da resolucao de conflito LWW        *)
(*    (SessionStore.tla, RepIndex/Prio), a identidade da replica de        *)
(*    origem passa a influenciar o resultado do desempate -- a             *)
(*    especificacao deixou de ser simetrica em REPLICAS. Remover           *)
(*    Permutations(REPLICAS) preserva a SOUNDNESS da reducao (ver          *)
(*    docs/plano-fix-broadcast-lww-2026-09-16.md, decisao D-3).            *)
(*                                                                         *)
(* Uso (a partir de spec/):                                                *)
(*   java -cp tla2tools.jar tlc2.TLC -config <arquivo>.cfg \               *)
(*        -workers auto -cleanup MCSession.tla                             *)
(***************************************************************************)
EXTENDS SessionStore, TLC

CONSTANT MaxCanal,    \* teto para o comprimento de CADA fila canal[r]
         MaxRelogio   \* teto para o valor de CADA relogio logico relogio[r]

LimiteCanal   == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal
LimiteRelogio == \A r \in REPLICAS: relogio[r] =< MaxRelogio

(* IDS e SIDS permanecem intercambiaveis; REPLICAS NAO MAIS, pois a partir  *)
(* da resolucao de conflito LWW (SessionStore.tla, RepIndex/Prio), a       *)
(* identidade da replica de origem passa a influenciar o resultado do      *)
(* desempate -- a especificacao deixou de ser simetrica em REPLICAS.       *)
(* Remover Permutations(REPLICAS) preserva a SOUNDNESS da reducao (ver     *)
(* docs/plano-fix-broadcast-lww-2026-09-16.md, decisao D-3).               *)
Simetria == Permutations(IDS) \cup Permutations(SIDS)
==========================================================================
