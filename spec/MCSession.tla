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
(*    permanecem intercambiaveis como antes. Cair/Reviver (B-22, F1) nao   *)
(*    alteram isso -- tratam toda replica igualmente.                      *)
(*                                                                         *)
(*  - LimiteEventosFalha (B-22, 2026-10-03, F1 -- ver                      *)
(*    docs/plano-b22-falhas-explicitas-2026-10-03.md SS4.2): quedas e um    *)
(*    Nat sem teto (incrementado a cada Cair), tornando o espaco de        *)
(*    estados infinito tambem por essa via (o TLC exploraria sequencias    *)
(*    arbitrarias de queda->recuperacao->queda->...). LimiteEventosFalha   *)
(*    impoe quedas =< MaxEventosFalha (state constraint). Valor inicial    *)
(*    recomendado: MaxEventosFalha = 1 (um unico evento de queda por       *)
(*    comportamento, suficiente para a primeira rodada de verificacao de   *)
(*    F1 isolado).                                                         *)
(*                                                                         *)
(*  - F2 (B-22, 2026-10-03, Particionar/Mesclar -- ver                     *)
(*    docs/plano-b22-falhas-explicitas-2026-10-03.md SS2): particaoAtiva    *)
(*    (BOOLEAN) e ladoDe ([REPLICAS -> {1,2}]) tem dominio FINITO por       *)
(*    construcao (diferente de quedas/relogio, que sao Nat sem teto) --    *)
(*    isso FINITIZA o espaco por essa via, mas nao o torna TRATAVEL: nada   *)
(*    limita quantos CICLOS Particionar->Mesclar o TLC explora por          *)
(*    comportamento, e cada ciclo pode levar sessoes/identidades/relogio a  *)
(*    uma combinacao nova. A primeira rodada (commit dc64154) decidiu nao   *)
(*    adicionar "+ particoes" por esse raciocinio de finitizacao -- decisao *)
(*    REVISADA numa segunda rodada (2026-10-03, apos                        *)
(*    docs/validacao-b22-f2-2026-10-03.md SS3, Senior): a pergunta certa    *)
(*    nao e "o espaco e finito", e "o espaco e TRATAVEL" -- confirmado       *)
(*    empiricamente que nao era (TypeOK/I3/I4 deixaram de fechar exaustivo  *)
(*    em tiny/small/medium sob F2, que antes fechavam em 0-18s; ver          *)
(*    docs/registro-de-problemas.md P-25). Nova variavel `particoes: Nat`   *)
(*    (incrementada em Particionar, nunca decrementada, mesmo espirito de   *)
(*    `quedas`) e LimiteEventosFalha estendida para                        *)
(*    `quedas + particoes =< MaxEventosFalha` abaixo -- exatamente a        *)
(*    formula que o plano original ja antecipava (SS4.2).                   *)
(*                                                                         *)
(*  - Simetria preservada sem alteracao tambem sob F2: Particionar explora *)
(*    TODOS os subconjuntos nao-triviais de REPLICAS simetricamente, e     *)
(*    Vencedor/Mesclar dependem so de CARDINALIDADE (nunca de identidade   *)
(*    de replica) -- nenhum termo novo depende de qual replica e qual.     *)
(*                                                                         *)
(* Uso (a partir de spec/):                                                *)
(*   java -cp tla2tools.jar tlc2.TLC -config <arquivo>.cfg \               *)
(*        -workers auto -cleanup MCSession.tla                             *)
(***************************************************************************)
EXTENDS SessionStore, TLC

CONSTANT MaxCanal,         \* teto para o comprimento de CADA fila canal[r]
         MaxRelogio,       \* teto para o valor de CADA contador de versao relogio[r]
         MaxEventosFalha   \* teto para o total de eventos de queda + particao (B-22, F1+F2)

LimiteCanal        == \A r \in REPLICAS: Len(canal[r]) =< MaxCanal
LimiteRelogio      == \A r \in REPLICAS: relogio[r] =< MaxRelogio
LimiteEventosFalha == quedas + particoes =< MaxEventosFalha

Simetria == Permutations(IDS) \cup Permutations(SIDS) \cup Permutations(REPLICAS)

(***************************************************************************)
(* B-32 (2026-10-09 -- ver docs/plano-b29-particao-real-e-contraexemplos-  *)
(* 2026-10-09.md SS5.2-5.3): propriedades OPERACIONAIS por transicao e      *)
(* cenario semeado. SessionStore.tla NAO foi alterado; tudo abaixo e'       *)
(* harness. I1/I2 do modulo principal sao violados por construcao (atraso   *)
(* do canal) e I4 e' vazio (TypeOK); estas formulas checam o que o auditor  *)
(* real de B-23 checa (por replica / por transicao).                        *)
(***************************************************************************)

\* I1F: irreversibilidade por replica. Replica com a sessao INVALID nunca a volta a ter
\* em outro estado.
CorpoI1F ==
  \A r \in REPLICAS, s \in SIDS :
     sessoes[r][s].estado = "INVALID" => sessoes'[r][s].estado = "INVALID"
I1F == [][CorpoI1F]_vars

\* I4F: motivo unico por replica. Fixado o motivo de uma sessao INVALID, ele nao muda.
CorpoI4F ==
  \A r \in REPLICAS, s \in SIDS :
     sessoes[r][s].estado = "INVALID" => sessoes'[r][s].motivo = sessoes[r][s].motivo
I4F == [][CorpoI4F]_vars

\* I2Fa: bloqueio nao se perde na fusao.
CorpoI2Fa ==
  Mesclar => \A r \in REPLICAS, id \in IDS :
               identidades[r][id].status = "BLOCKED" => identidades'[r][id].status = "BLOCKED"
I2Fa == [][CorpoI2Fa]_vars

\* I2Fb: consistencia local do bloqueio (invariante de estado, por replica).
I2Fb ==
  \A r \in REPLICAS, id \in IDS :
     identidades[r][id].status = "BLOCKED" =>
       \A s \in SIDS : sessoes[r][s].identidade = id => sessoes[r][s].estado # "VALID"

\* Variantes que atribuem a violacao a fusao (so' nos passos Mesclar).
I1M == [][Mesclar => CorpoI1F]_vars
I4M == [][Mesclar => CorpoI4F]_vars

\* I4Fp: variante "pura" de I4F. I4F, como especificada no plano, tambem dispara quando uma
\* sessao INVALID volta a VALID (o motivo vira NONE), ou seja, e' subsumida por I1F nesse caso.
\* I4Fp so' exige estabilidade do motivo enquanto a sessao PERMANECE INVALID, isolando a
\* troca de motivo (ex.: LOGOUT -> ADMIN_BLOCK) da reversao da invalidacao.
CorpoI4Fp ==
  \A r \in REPLICAS, s \in SIDS :
     (sessoes[r][s].estado = "INVALID" /\ sessoes'[r][s].estado = "INVALID")
        => sessoes'[r][s].motivo = sessoes[r][s].motivo
I4Fp == [][CorpoI4Fp]_vars
I4Mp == [][Mesclar => CorpoI4Fp]_vars

\* Achado extra (a), SS5.4(1): logout aceito que nao efetiva. Um passo Logout(r,s)
\* deve deixar a sessao INVALID na propria replica r.
I1P == [][\A r \in REPLICAS, s \in SIDS : Logout(r, s) => sessoes'[r][s].estado = "INVALID"]_vars

\* Achado extra (b), SS5.6: Mesclar descarta chave existente (estado # NONE) do lado perdedor.
\* Evidencia sobre o MODELO (copia o registro inteiro do vencedor); o comportamento do
\* Infinispan real (PREFERRED_NON_NULL) so' se confirma em B-34.
MesclarPerdeChave ==
  [][Mesclar => \A r \in REPLICAS, s \in SIDS :
        sessoes[r][s].estado # "NONE" => sessoes'[r][s].estado # "NONE"]_vars

\* Cenario semeado: Login ja ocorreu e foi totalmente propagado (todas as replicas com
\* todas as sessoes VALID da identidade semente, ts = 1, relogio = 1, canais vazios).
\* Com IDS = {u1}, Login fica desabilitado (exige estado = NONE local).
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

\* Isolamento do efeito de cada falha (state constraints nos .cfg).
SemFalha   == quedas + particoes = 0
SoQueda    == particoes = 0
SoParticao == quedas = 0

==========================================================================
