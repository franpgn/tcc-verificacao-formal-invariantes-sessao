------------------------------ MODULE SessionStore ------------------------------
(***************************************************************************)
(* Especificação formal do núcleo do protocolo de estado de sessão em      *)
(* data grids sob replicação assíncrona.                                   *)
(*                                                                         *)
(* Cobertura desta versão (B-14 + revisão do mecanismo de conflito,        *)
(* 2026-10-02, Achado E -- ver docs/revisao-mecanismo-conflito-            *)
(* infinispan-2026-10-02.md):                                              *)
(*   - Conjunto IDS de identidades e SIDS de sessões.                      *)
(*   - Estado por réplica como REGISTRO ÚNICO por chave, não mais 5+2      *)
(*     variáveis independentes por campo:                                  *)
(*       sessoes:     [REPLICAS -> [SIDS -> SessaoRec]]                    *)
(*       identidades: [REPLICAS -> [IDS  -> IdentRec]]                     *)
(*     onde SessaoRec = [estado, motivo, identidade, ts] e                 *)
(*     IdentRec = [contador, status, ts] -- cada um com um ÚNICO campo ts, *)
(*     espelhando exatamente o campo `versao` de                          *)
(*     SessionState.java/IdentityState.java (um objeto imutável por        *)
(*     chave, sempre lido/escrito como unidade atômica -- nunca um "put"   *)
(*     parcial de um campo). Esta é a mudança estrutural central da        *)
(*     revisão: as versões anteriores (TCC-I e correção broadcast/LWW de   *)
(*     2026-09-16) decompunham esse registro atômico real em 5 variáveis   *)
(*     de sessão e 2 de identidade, cada uma com seu próprio relógio LWW   *)
(*     independente -- divergência de granularidade identificada como      *)
(*     Achado E, item A.5 do documento acima.                              *)
(*   - Canal assíncrono por réplica com broadcast real: cada escrita local *)
(*     é enfileirada nas filas de TODAS as réplicas destino (exceto a de   *)
(*     origem, que já aplicou o efeito localmente) -- ver Broadcast. Este  *)
(*     mecanismo é ORTOGONAL à revisão (Achado E, item (b), correção #1)   *)
(*     e foi preservado tal como estava.                                   *)
(*   - IMPORTANTE -- o que este modelo NÃO afirma: o Achado E (itens       *)
(*     A.1-A.4) estabelece, a partir do código-fonte real do Infinispan    *)
(*     15.0.21.Final, que (i) em operação NOMINAL (sem partição), escritas *)
(*     concorrentes na mesma chave são serializadas por trava no owner     *)
(*     primário (NonTransactionalLockingInterceptor +                     *)
(*     NonTxDistributionInterceptor, replicação síncrona aos backup        *)
(*     owners) -- não há, de fato, nenhuma janela de reordenação a         *)
(*     resolver; e (ii) quando um conflito real é resolvido (sob partição, *)
(*     F2, fora do escopo desta versão), a regra é posicional              *)
(*     (`PREFERRED_NON_NULL`: partição com mais réplicas vence, valor      *)
(*     inteiro do registro, desempate por topologyId) -- NUNCA por         *)
(*     comparação de timestamp. Logo, o campo `ts` de SessaoRec/IdentRec   *)
(*     e o gate "aplica só se ts da mensagem > ts local" em EntregaMensagem*)
(*     NÃO modelam nenhum mecanismo real de resolução de conflito do       *)
(*     Infinispan -- são um DISPOSITIVO DE MODELAGEM cujo único propósito  *)
(*     é manter solidez (sem regressão permanente em I5/I6) na abstração,  *)
(*     já preservada por decisão do Gestor, de canal assíncrono com        *)
(*     reordenação sempre possível (EntregaMensagem permite entrega fora   *)
(*     de ordem -- ver `\E i \in 1..Len(canal[r])` abaixo). Essa abstração *)
(*     de canal, por si, já não corresponde ao caminho nominal real (item  *)
(*     A.4) -- o ponto foi mantido porque removê-lo é uma mudança maior,   *)
(*     de modelagem de concorrência, fora do escopo desta revisão (fica    *)
(*     para quando a ação de partição F2 for modelada, Fases 4-5 do plano, *)
(*     que SIM implementarão a regra real "maioria vence, registro         *)
(*     inteiro, sem timestamp"). `ts` aqui é melhor lido como o campo      *)
(*     `versao` do domínio (monotônico, local a cada registro), não como   *)
(*     um relógio lógico distribuído.                                      *)
(*   - Removido nesta revisão: o "relógio lógico de Lamport" com           *)
(*     desempate determinístico por identidade de réplica (RepIndex/Prio)  *)
(*     introduzido em 2026-09-16 -- era necessário para compensar a        *)
(*     decomposição por campo (5+2 relógios independentes podendo          *)
(*     divergir entre si) e para garantir uma ordem total determinística   *)
(*     entre réplicas. Sob o registro único, a razão de ser do desempate   *)
(*     por réplica desaparece: resta um único contador de versão local     *)
(*     por réplica (`relogio`), incrementado a cada escrita local,         *)
(*     análogo a `versao+1` em SessionState.java/IdentityState.java. Como  *)
(*     consequência, REPLICAS volta a ser um conjunto simétrico para fins  *)
(*     de SYMMETRY em MCSession.tla (ver Simetria lá).                     *)
(*   - Consequência estrutural (Achado E, item (b)): das 6 correções da    *)
(*     cadeia de 2026-09-16, a #1 (broadcast real por réplica) permanece   *)
(*     tal como estava; a regra de negócio da #4 (cascata de invalidação   *)
(*     do Block via sessoesAfetadas) permanece, com mecânica simplificada  *)
(*     para registro único; as #3, #5 e #6 (gates dessincronizados entre   *)
(*     campos do mesmo registro, broadcast fantasma de CONTADOR_INC)       *)
(*     deixam de ser possíveis POR CONSTRUÇÃO -- não há mais "dois campos  *)
(*     do mesmo registro com relógios próprios" para dessincronizar; um    *)
(*     único `ts` por registro resolve a escrita inteira de uma vez. A #2  *)
(*     (LWW por campo via Lamport) é substituída pelo gate único acima,    *)
(*     já descrito como dispositivo de modelagem, não mecanismo real.      *)
(*   - Operações O1, O3, O4, O5, O6, O7 conforme Cap. 3 §3.3.1 da          *)
(*     monografia. O2 (Validate) é leitura pura e não altera o estado      *)
(*     do modelo, portanto não tem ação dedicada.                          *)
(*   - Invariantes I1..I6 declarados conforme Cap. 3 §3.3.3 -- MESMAS      *)
(*     definições lógicas de antes; só a sintaxe de acesso aos campos      *)
(*     muda (sessoes[r][s].estado em vez de sessoes[r][s], etc.), porque   *)
(*     a revisão troca a representação do estado, não o que cada           *)
(*     invariante afirma.                                                  *)
(*                                                                         *)
(* Falhas explícitas (crash, partição, atraso/jitter) ficam para TCC-II,   *)
(* conforme Apêndice B, etapas 3 e 4 (Fases 4-5 do plano de revisão).      *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS IDS,        \* conjunto de identidades modeladas
          SIDS,       \* conjunto de identificadores de sessão potenciais
          REPLICAS,   \* conjunto de réplicas
          LIMITE      \* limite de tentativas antes do bloqueio

ASSUME /\ LIMITE \in Nat /\ LIMITE > 0
       /\ IsFiniteSet(IDS) /\ IsFiniteSet(SIDS) /\ IsFiniteSet(REPLICAS)

VARIABLES sessoes,       \* [REPLICAS -> [SIDS -> SessaoRec]] -- registro único por sessão
          identidades,   \* [REPLICAS -> [IDS  -> IdentRec]]  -- registro único por identidade
          canal,         \* [REPLICAS -> Seq(Mensagem)] -- fila de entrada por réplica (broadcast)
          relogio        \* [REPLICAS -> Nat] -- contador de versão local (análogo a `versao`
                          \* de SessionState.java/IdentityState.java), avança só em escrita local

vars == << sessoes, identidades, canal, relogio >>

Estados   == {"NONE", "VALID", "INVALID"}
MotivosT  == {"NONE", "LOGOUT", "EXPIRADA", "ADMIN_BLOCK"}
StatusT   == {"OPEN", "BLOCKED"}

(***************************************************************************)
(* Registros únicos por chave -- cada um com um ÚNICO campo `ts`,          *)
(* espelhando SessionState.java (estado, motivo, identidade, versão) e     *)
(* IdentityState.java (contador, status, versão). `identidade` usa         *)
(* {"NONE"} \cup IDS porque uma sessão ainda não criada (estado = "NONE")  *)
(* não tem identidade associada -- mesma convenção já usada pela           *)
(* sessaoDe original.                                                      *)
(***************************************************************************)
SessaoRec == [estado: Estados, motivo: MotivosT, identidade: {"NONE"} \cup IDS, ts: Nat]
IdentRec  == [contador: 0..LIMITE, status: StatusT, ts: Nat]

SessaoVazia == [estado |-> "NONE", motivo |-> "NONE", identidade |-> "NONE", ts |-> 0]
IdentVazia  == [contador |-> 0, status |-> "OPEN", ts |-> 0]

(***************************************************************************)
(* Tipos de mensagens propagadas entre réplicas pelo canal assíncrono.     *)
(* Cada mensagem carrega o REGISTRO INTEIRO candidato (não mais um valor   *)
(* de campo isolado) -- `sessao` é usado por SESSAO_VALIDA, SESSAO_INVALIDA*)
(* e ID_BLOQUEIA (como molde aplicado a cada sessão em sessoesAfetadas);   *)
(* `ident` é usado por CONTADOR_INC, CONTADOR_RESET, ID_BLOQUEIA e         *)
(* ID_DESBLOQUEIA. O campo não usado por um dado tipo carrega um valor     *)
(* vazio (SessaoVazia/IdentVazia), mesma convenção que `payload`/`valor`   *)
(* já usavam na versão anterior para campos só aplicáveis a alguns tipos.  *)
(***************************************************************************)
Mensagem ==
  [tipo: {"SESSAO_VALIDA", "SESSAO_INVALIDA",
          "CONTADOR_INC", "CONTADOR_RESET",
          "ID_BLOQUEIA", "ID_DESBLOQUEIA"},
   chave: SIDS \cup IDS,
   sessao: SessaoRec,
   ident: IdentRec,
   sessoesAfetadas: SUBSET SIDS]  \* usado só por ID_BLOQUEIA: cascata do Block calculada
                                   \* na origem (preservado da versão anterior); demais usam {}.

TypeOK ==
  /\ sessoes     \in [REPLICAS -> [SIDS -> SessaoRec]]
  /\ identidades \in [REPLICAS -> [IDS -> IdentRec]]
  /\ canal       \in [REPLICAS -> Seq(Mensagem)]
  /\ relogio     \in [REPLICAS -> Nat]

(***************************************************************************)
(* Estado inicial.                                                          *)
(***************************************************************************)
Init ==
  /\ sessoes     = [r \in REPLICAS |-> [s \in SIDS |-> SessaoVazia]]
  /\ identidades = [r \in REPLICAS |-> [id \in IDS |-> IdentVazia]]
  /\ canal       = [r \in REPLICAS |-> << >>]
  /\ relogio     = [r \in REPLICAS |-> 0]

(***************************************************************************)
(* Enfileira msg nas filas de TODAS as réplicas exceto a de origem, que já *)
(* aplicou o efeito localmente e não precisa entregar a mensagem a si      *)
(* mesma. Preservado tal como na versão anterior (Achado E, correção #1,   *)
(* ortogonal à revisão).                                                    *)
(***************************************************************************)
Broadcast(origem, msg) ==
  [r2 \in REPLICAS |-> IF r2 = origem THEN canal[r2] ELSE Append(canal[r2], msg)]

(***************************************************************************)
(* Operações executadas em uma réplica r. Cada ação local calcula um novo  *)
(* `ts` (relogio[r] + 1) e só efetiva a escrita LOCAL do registro inteiro  *)
(* se esse `ts` superar o `ts` já presente no registro local -- um único   *)
(* gate por registro, não mais um gate por campo (Achado E, item (b)).     *)
(* O broadcast é incondicional nas ações cujo registro candidato é um      *)
(* valor absoluto (não depende do valor anterior do PRÓPRIO campo que o    *)
(* gate protege); IncrementFailure é a única exceção -- ver comentário ali.*)
(***************************************************************************)

(* O1, login: cria sessão para id se a identidade está OPEN e a sid livre. *)
Login(r, id, s) ==
  LET novoTS == relogio[r] + 1
      candidato == [estado |-> "VALID", motivo |-> "NONE", identidade |-> id, ts |-> novoTS]
      msg == [tipo |-> "SESSAO_VALIDA", chave |-> s,
              sessao |-> candidato, ident |-> IdentVazia, sessoesAfetadas |-> {}]
  IN
  /\ identidades[r][id].status = "OPEN"
  /\ sessoes[r][s].estado = "NONE"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > sessoes[r][s].ts
       THEN sessoes' = [sessoes EXCEPT ![r][s] = candidato]
       ELSE sessoes' = sessoes
  /\ canal' = Broadcast(r, msg)
  /\ identidades' = identidades

(* O3, logout: marca sessão como INVALID com motivo LOGOUT. Idempotente   *)
(* (precondição exige VALID). `identidade` é preservada do registro atual *)
(* -- mesma semântica de SessionState.invalidar(), que reconstrói o       *)
(* objeto preservando identidade/id/criadaEm.                             *)
Logout(r, s) ==
  LET novoTS == relogio[r] + 1
      candidato == [estado |-> "INVALID", motivo |-> "LOGOUT",
                    identidade |-> sessoes[r][s].identidade, ts |-> novoTS]
      msg == [tipo |-> "SESSAO_INVALIDA", chave |-> s,
              sessao |-> candidato, ident |-> IdentVazia, sessoesAfetadas |-> {}]
  IN
  /\ sessoes[r][s].estado = "VALID"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > sessoes[r][s].ts
       THEN sessoes' = [sessoes EXCEPT ![r][s] = candidato]
       ELSE sessoes' = sessoes
  /\ canal' = Broadcast(r, msg)
  /\ identidades' = identidades

(* O4, incrementFailure: incrementa contador, bloqueia se atingir LIMITE. *)
(* Única ação cujo broadcast é CONDICIONAL ao próprio gate local: `novo`  *)
(* é calculado a partir do contador ATUAL (acumulador), então se a        *)
(* escrita local perde sua corrida de prioridade, `novo` nunca se torna   *)
(* contador real em réplica alguma (nem aqui) -- propagar mesmo assim     *)
(* seria anunciar um valor fantasma (mesma causa-raiz de P-15,            *)
(* 2026-09-16, preservada aqui como princípio geral: "não anunciar o que  *)
(* não foi de fato aplicado"). Sob o registro único, este é o único caso  *)
(* com esse ingrediente estrutural -- as demais ações escrevem valores     *)
(* absolutos, não acumulados.                                              *)
IncrementFailure(r, id) ==
  LET novoTS == relogio[r] + 1
      novoContador == identidades[r][id].contador + 1
      novoStatus == IF novoContador >= LIMITE THEN "BLOCKED" ELSE identidades[r][id].status
      candidato == [contador |-> novoContador, status |-> novoStatus, ts |-> novoTS]
      msg == [tipo |-> "CONTADOR_INC", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ identidades[r][id].contador < LIMITE
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN /\ identidades' = [identidades EXCEPT ![r][id] = candidato]
            /\ canal' = Broadcast(r, msg)
       ELSE /\ identidades' = identidades
            /\ canal' = canal
  /\ sessoes' = sessoes

(* O5, resetFailures: zera o contador de id na réplica r. `status` é      *)
(* preservado do registro atual -- mesma semântica de                     *)
(* IdentityState.resetar() (regra O5: "sem alterar a marca de bloqueio"). *)
ResetFailures(r, id) ==
  LET novoTS == relogio[r] + 1
      candidato == [contador |-> 0, status |-> identidades[r][id].status, ts |-> novoTS]
      msg == [tipo |-> "CONTADOR_RESET", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ identidades[r][id].contador # 0
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidato]
       ELSE identidades' = identidades
  /\ canal' = Broadcast(r, msg)
  /\ sessoes' = sessoes

(* O6, block: marca identidade como BLOCKED e invalida sessões VALID da   *)
(* identidade (cascata, mesma regra de negócio de SessionOps.block(), que *)
(* varre sessions.entrySet() invalidando as da identidade). O molde       *)
(* `candidatoSessao` vale para TODA sessão afetada (todas compartilham o  *)
(* mesmo id, logo o mesmo `identidade` preservado, o mesmo novo `ts`) --  *)
(* mecânica simplificada em relação à versão anterior, que precisava de   *)
(* dois gates por sessão afetada (sessoesTS e motivosTS independentes);   *)
(* agora é um único gate por sessão (sessoes[r][s].ts).                   *)
Block(r, id) ==
  LET novoTS == relogio[r] + 1
      Afetado(s) == sessoes[r][s].identidade = id /\ sessoes[r][s].estado = "VALID"
      afetadas == {s \in SIDS : Afetado(s)}
      candidatoId == [contador |-> identidades[r][id].contador, status |-> "BLOCKED", ts |-> novoTS]
      candidatoSessao == [estado |-> "INVALID", motivo |-> "ADMIN_BLOCK",
                          identidade |-> id, ts |-> novoTS]
      msg == [tipo |-> "ID_BLOQUEIA", chave |-> id,
              sessao |-> candidatoSessao, ident |-> candidatoId, sessoesAfetadas |-> afetadas]
  IN
  /\ identidades[r][id].status = "OPEN"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidatoId]
       ELSE identidades' = identidades
  /\ sessoes' = [sessoes EXCEPT ![r] =
                  [s \in SIDS |-> IF s \in afetadas /\ novoTS > @[s].ts
                                  THEN candidatoSessao ELSE @[s]]]
  /\ canal' = Broadcast(r, msg)

(* O7, unblock: remove a marca BLOCKED e zera o contador. Não recria      *)
(* sessões previamente invalidadas pelo bloqueio. NOTA (achado lateral,   *)
(* fora do escopo desta revisão): este zeramento do contador é um         *)
(* comportamento HERDADO da especificação original -- IdentityState.      *)
(* desbloquear() no código real NÃO zera contadorFalhas, só resetar()     *)
(* faz isso. Divergência modelo/código pré-existente, não introduzida     *)
(* nem corrigida por esta revisão; registrada aqui para rastreabilidade.  *)
Unblock(r, id) ==
  LET novoTS == relogio[r] + 1
      candidato == [contador |-> 0, status |-> "OPEN", ts |-> novoTS]
      msg == [tipo |-> "ID_DESBLOQUEIA", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ identidades[r][id].status = "BLOCKED"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidato]
       ELSE identidades' = identidades
  /\ canal' = Broadcast(r, msg)
  /\ sessoes' = sessoes

(***************************************************************************)
(* Entrega de uma mensagem qualquer do canal a uma réplica destino r.      *)
(* Captura atraso não determinístico e reordenação (mantido da versão      *)
(* anterior -- ver nota no cabeçalho do módulo sobre o que essa abstração  *)
(* representa e não representa). Resolução por UM gate por registro: se   *)
(* o `ts` do registro candidato da mensagem for MAIOR que o `ts` do        *)
(* registro local afetado, a escrita é aplicada por inteiro; caso          *)
(* contrário, a mensagem é descartada e nada muda. `relogio` nunca muda    *)
(* em EntregaMensagem (sem merge-on-receive, mesma decisão D-4 anterior).  *)
(***************************************************************************)
EntregaMensagem(r) ==
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ UNCHANGED relogio
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
(* Invariantes (Cap. 3 §3.3.3 da monografia). Mesmas definições lógicas da *)
(* versão anterior -- só o acesso aos campos muda (registro em vez de      *)
(* variável independente por campo).                                       *)
(***************************************************************************)

(* I1, irreversibilidade da invalidação por logout.                         *)
(* Se em qualquer réplica r1 a sessão s está INVALID com motivo LOGOUT,    *)
(* então nenhuma réplica r2 tem essa sessão como VALID.                     *)
I1 ==
  \A r1 \in REPLICAS, s \in SIDS:
    (sessoes[r1][s].estado = "INVALID" /\ sessoes[r1][s].motivo = "LOGOUT")
      => (\A r2 \in REPLICAS: sessoes[r2][s].estado # "VALID")

(* I2, consistência de bloqueio.                                            *)
(* Se a identidade id está BLOCKED em alguma réplica r1, nenhuma réplica   *)
(* tem sessão de id como VALID.                                             *)
I2 ==
  \A r1 \in REPLICAS, id \in IDS:
    (identidades[r1][id].status = "BLOCKED") =>
      (\A r2 \in REPLICAS, s \in SIDS:
        (sessoes[r2][s].identidade = id) => (sessoes[r2][s].estado # "VALID"))

(* I3, monotonicidade local do contador. Declarada como invariante de       *)
(* domínio: o contador permanece em 0..LIMITE em toda réplica.              *)
(* A monotonicidade temporal é consequência das ações IncrementFailure,     *)
(* ResetFailures e Unblock, e é certificada por construção.                 *)
I3 ==
  \A r \in REPLICAS, id \in IDS: identidades[r][id].contador \in 0..LIMITE

(* I4, unicidade do motivo de invalidação. Uma vez fixado o motivo \in     *)
(* {LOGOUT, EXPIRADA, ADMIN_BLOCK}, sessoes[r][s] = INVALID e motivos      *)
(* permanece consistente em todas as réplicas que observaram a invalidação.*)
(* Forma operacional: para toda réplica, motivo pertence ao conjunto       *)
(* MotivosT, sem deriva fora do tipo declarado.                             *)
I4 ==
  \A r \in REPLICAS, s \in SIDS: sessoes[r][s].motivo \in MotivosT

(* I5, convergência do estado lógico sob quiescência.                       *)
(* Quando o canal está vazio (todas as mensagens entregues), todas as      *)
(* réplicas observam o mesmo estado lógico para cada sessão.                *)
I5 ==
  Quiescent =>
    \A s \in SIDS, r1, r2 \in REPLICAS:
      /\ sessoes[r1][s].estado     = sessoes[r2][s].estado
      /\ sessoes[r1][s].motivo     = sessoes[r2][s].motivo
      /\ sessoes[r1][s].identidade = sessoes[r2][s].identidade

(* I6, convergência do contador e do status de identidade sob quiescência. *)
I6 ==
  Quiescent =>
    \A id \in IDS, r1, r2 \in REPLICAS:
      /\ identidades[r1][id].contador = identidades[r2][id].contador
      /\ identidades[r1][id].status   = identidades[r2][id].status

(***************************************************************************)
(* THEOREM: esta é uma revisão estrutural (Achado E), não uma correção de  *)
(* bug pontual -- o veredito do TLC (Fase 3 do plano de revisão) NÃO deve  *)
(* ser presumido igual ao da versão anterior (que fechava I5/I6 em         *)
(* tiny/small/medium/full via a máquina de relógio de Lamport removida     *)
(* aqui). Ver histórico de execução do TLC em runs/tlc/ e nas mensagens de *)
(* commit da branch fix/revisao-mecanismo-conflito-real para o resultado   *)
(* real desta versão. I1 e I2, por construção, continuam violados no       *)
(* fluxo nominal: são propriedades de segurança *cross-réplica* não        *)
(* condicionadas a quiescência, e o modelo captura atraso de propagação    *)
(* real -- ficam para refinamento em TCC-II (Apêndice B, etapas 3-4),      *)
(* junto com falhas explícitas (crash/partição/jitter) e a ação de         *)
(* partição real (F2, Fases 4-5 do plano de revisão), que substituirá o    *)
(* gate por `ts` em EntregaMensagem, no caminho de partição, pela regra    *)
(* real de maioria vence/registro inteiro (Achado E, itens A.1-A.2).       *)
(***************************************************************************)
=============================================================================
