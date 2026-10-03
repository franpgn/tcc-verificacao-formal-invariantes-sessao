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
(*   - F1 (B-22, 2026-10-03 -- ver docs/plano-b22-falhas-explicitas-        *)
(*     2026-10-03.md §1): ações `Cair(r)`/`Reviver(r)` tornam uma réplica   *)
(*     temporariamente incapaz de agir (nem ações locais, nem               *)
(*     `EntregaMensagem`), via nova variável `vivo: [REPLICAS -> BOOLEAN]`. *)
(*     Mínima, SEM mecanismo de merge/snapshot: mensagens endereçadas à     *)
(*     réplica caída continuam se acumulando normalmente em `canal[r]`      *)
(*     (`Broadcast` não verifica `vivo`); ao reviver, a réplica volta a     *)
(*     drenar seu acúmulo via `EntregaMensagem`, uma mensagem por vez, pela *)
(*     mesma lógica de gate por `ts` que já existe -- convergindo ao mesmo  *)
(*     estado final que um *state-transfer* produziria (o gate por `ts` já *)
(*     garante que o valor mais recente vence, independente da ordem de    *)
(*     entrega). Não adicionada para capturar um risco que se espera        *)
(*     encontrar (o Achado E já prevê que não há violação aqui, já que F1   *)
(*     nunca ativa DEGRADED/merge-policy no Infinispan real) -- adicionada  *)
(*     para que o TLC de fato EXPLORE o cenário de indisponibilidade        *)
(*     temporária, transformando "F1 nunca viola" de presunção em resultado *)
(*     verificado (princípio de falsificabilidade, Wieringa CAND-019).      *)
(*     Nova variável auxiliar `quedas: Nat` conta o número total de         *)
(*     eventos `Cair` já ocorridos no comportamento (nunca decrementada por *)
(*     `Reviver`) -- usada só por `MCSession.tla` (`LimiteEventosFalha`)    *)
(*     para finitizar o espaço de estados, mesmo espírito de `relogio`/     *)
(*     `LimiteRelogio`. Não quebra `Simetria`: `Cair`/`Reviver` tratam toda  *)
(*     réplica igualmente, sem termo dependente de qual réplica é qual.     *)
(*                                                                         *)
(*   - F2 (B-22, 2026-10-03 -- ver docs/plano-b22-falhas-explicitas-        *)
(*     2026-10-03.md §2, Opção A): ações `Particionar`/`Mesclar` modelam    *)
(*     partição real. `Particionar` escolhe não-deterministicamente P1/P2   *)
(*     (não-vazios, disjuntos); novas variáveis `particaoAtiva: BOOLEAN` e  *)
(*     `ladoDe: [REPLICAS -> {1,2}]`. Enquanto particionada, `PodeAgir(r)`  *)
(*     (generaliza o gate de `vivo` de F1) bloqueia as 6 ações locais e      *)
(*     `EntregaMensagem` para o lado MINORITÁRIO (fiel a `DENY_READ_WRITES`,*)
(*     a configuração real do projeto) -- sem chamar `Cair`/`Reviver`       *)
(*     (evento logicamente distinto: F1 é o processo parar, F2 é a rede     *)
(*     cortar). `Vencedor` decide maioria/minoria por TAMANHO (nunca por    *)
(*     timestamp, Achado E A.1); em empate (só possível com 2 réplicas),    *)
(*     ambos os lados são explorados não-deterministicamente, preservando   *)
(*     `Simetria`. `Mesclar` copia o REGISTRO INTEIRO do lado vencedor para  *)
(*     o perdedor -- literalmente `PREFERRED_NON_NULL`/`PREFERRED_ALWAYS`   *)
(*     real, nunca uma comparação de `ts` (esse gate é exclusivo de         *)
(*     `EntregaMensagem`). F1 e F2 são mutuamente exclusivos nesta rodada,   *)
(*     nas DUAS direções (P-24, correção de 2026-10-03 rodada 2):           *)
(*     `Particionar` exige todas as réplicas vivas, E `Cair` exige          *)
(*     `~particaoAtiva` -- a combinação dos dois fica para rodada futura.   *)
(*     Sob Opção A (decidida pelo Gestor), o achado P-16 (empate de `ts`    *)
(*     entre escritas locais concorrentes no regime nominal) NÃO é          *)
(*     resolvido aqui -- `canal`/`Broadcast`/`EntregaMensagem` permanecem    *)
(*     exatamente como estavam.                                            *)
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
          relogio,       \* [REPLICAS -> Nat] -- contador de versão local (análogo a `versao`
                          \* de SessionState.java/IdentityState.java), avança só em escrita local
          vivo,          \* [REPLICAS -> BOOLEAN] -- F1 (B-22): FALSE enquanto a réplica está "caída"
          quedas,        \* Nat -- F1 (B-22): total de eventos Cair já ocorridos (nunca decrementa)
          particaoAtiva, \* BOOLEAN -- F2 (B-22): TRUE entre Particionar e Mesclar
          ladoDe         \* [REPLICAS -> {1, 2}] -- F2 (B-22): lado de cada réplica durante a partição
                          \* (valor irrelevante enquanto ~particaoAtiva)

vars == << sessoes, identidades, canal, relogio, vivo, quedas,
           particaoAtiva, ladoDe >>

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
  /\ vivo        \in [REPLICAS -> BOOLEAN]
  /\ quedas      \in Nat
  /\ particaoAtiva \in BOOLEAN
  /\ ladoDe        \in [REPLICAS -> {1, 2}]

(***************************************************************************)
(* Estado inicial.                                                          *)
(***************************************************************************)
Init ==
  /\ sessoes     = [r \in REPLICAS |-> [s \in SIDS |-> SessaoVazia]]
  /\ identidades = [r \in REPLICAS |-> [id \in IDS |-> IdentVazia]]
  /\ canal       = [r \in REPLICAS |-> << >>]
  /\ relogio     = [r \in REPLICAS |-> 0]
  /\ vivo        = [r \in REPLICAS |-> TRUE]
  /\ quedas      = 0
  /\ particaoAtiva = FALSE
  /\ ladoDe        = [r \in REPLICAS |-> 1]   \* irrelevante enquanto ~particaoAtiva

(***************************************************************************)
(* Enfileira msg nas filas de TODAS as réplicas exceto a de origem, que já *)
(* aplicou o efeito localmente e não precisa entregar a mensagem a si      *)
(* mesma. Preservado tal como na versão anterior (Achado E, correção #1,   *)
(* ortogonal à revisão).                                                    *)
(***************************************************************************)
Broadcast(origem, msg) ==
  [r2 \in REPLICAS |-> IF r2 = origem THEN canal[r2] ELSE Append(canal[r2], msg)]

(***************************************************************************)
(* F2 (B-22, 2026-10-03): maioria/minoria sob partição -- ver               *)
(* docs/plano-b22-falhas-explicitas-2026-10-03.md §2.2/§2.3. `Lado(n)` é o  *)
(* subconjunto de réplicas atualmente do lado `n`. `Vencedor(P1, P2)`       *)
(* devolve o(s) lado(s) vencedor(es) por TAMANHO -- sem noção de            *)
(* `topologyId`, fiel ao Achado E (A.1: a regra real é posicional, não por  *)
(* timestamp). Em empate (só possível com 2 réplicas, já que com 3 réplicas *)
(* todo split é 1-contra-2), devolve AMBOS os lados -- o TLC explora as     *)
(* duas resoluções possíveis, em vez de um desempate fixo que               *)
(* reintroduziria um mecanismo sem correspondência real e quebraria         *)
(* `Simetria` (mesma razão de design que já eliminou `RepIndex`/`Prio` na   *)
(* revisão do Achado E). `PodeAgir(r)` generaliza o gate de `vivo` de F1:    *)
(* uma réplica só age se estiver viva E (não há partição ativa OU seu lado  *)
(* está entre os vencedores) -- sob `DENY_READ_WRITES` (configuração real,  *)
(* `cluster/infinispan-cluster.xml`), o lado minoritário simplesmente não   *)
(* processa nada, mesma semântica de "não pode agir" que `Cair` já modela,  *)
(* mas por um motivo logicamente distinto (rede particionada, não processo  *)
(* parado) -- por isso não reutiliza `Cair`/`Reviver`, só o mesmo padrão de *)
(* gate.                                                                     *)
(***************************************************************************)
Lado(n) == {r \in REPLICAS : ladoDe[r] = n}

Vencedor(P1, P2) ==
  IF Cardinality(P1) > Cardinality(P2) THEN {P1}
  ELSE IF Cardinality(P2) > Cardinality(P1) THEN {P2}
  ELSE {P1, P2}   \* empate -- ambas as possibilidades exploradas, não uma fixa

PodeAgir(r) ==
  /\ vivo[r] = TRUE
  /\ \/ ~particaoAtiva
     \/ LET meuLado == Lado(ladoDe[r]) IN
          meuLado \in Vencedor(Lado(1), Lado(2))

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
  /\ PodeAgir(r)   \* F1+F2 (B-22): réplica caída ou do lado minoritário não executa nenhuma ação local
  /\ identidades[r][id].status = "OPEN"
  /\ sessoes[r][s].estado = "NONE"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > sessoes[r][s].ts
       THEN sessoes' = [sessoes EXCEPT ![r][s] = candidato]
       ELSE sessoes' = sessoes
  /\ canal' = Broadcast(r, msg)
  /\ identidades' = identidades
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe>>

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
  /\ PodeAgir(r)   \* F1+F2 (B-22)
  /\ sessoes[r][s].estado = "VALID"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > sessoes[r][s].ts
       THEN sessoes' = [sessoes EXCEPT ![r][s] = candidato]
       ELSE sessoes' = sessoes
  /\ canal' = Broadcast(r, msg)
  /\ identidades' = identidades
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe>>

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
  /\ PodeAgir(r)   \* F1+F2 (B-22)
  /\ identidades[r][id].contador < LIMITE
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN /\ identidades' = [identidades EXCEPT ![r][id] = candidato]
            /\ canal' = Broadcast(r, msg)
       ELSE /\ identidades' = identidades
            /\ canal' = canal
  /\ sessoes' = sessoes
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe>>

(* O5, resetFailures: zera o contador de id na réplica r. `status` é      *)
(* preservado do registro atual -- mesma semântica de                     *)
(* IdentityState.resetar() (regra O5: "sem alterar a marca de bloqueio"). *)
ResetFailures(r, id) ==
  LET novoTS == relogio[r] + 1
      candidato == [contador |-> 0, status |-> identidades[r][id].status, ts |-> novoTS]
      msg == [tipo |-> "CONTADOR_RESET", chave |-> id,
              sessao |-> SessaoVazia, ident |-> candidato, sessoesAfetadas |-> {}]
  IN
  /\ PodeAgir(r)   \* F1+F2 (B-22)
  /\ identidades[r][id].contador # 0
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidato]
       ELSE identidades' = identidades
  /\ canal' = Broadcast(r, msg)
  /\ sessoes' = sessoes
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe>>

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
  /\ PodeAgir(r)   \* F1+F2 (B-22)
  /\ identidades[r][id].status = "OPEN"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidatoId]
       ELSE identidades' = identidades
  /\ sessoes' = [sessoes EXCEPT ![r] =
                  [s \in SIDS |-> IF s \in afetadas /\ novoTS > @[s].ts
                                  THEN candidatoSessao ELSE @[s]]]
  /\ canal' = Broadcast(r, msg)
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe>>

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
  /\ PodeAgir(r)   \* F1+F2 (B-22)
  /\ identidades[r][id].status = "BLOCKED"
  /\ relogio' = [relogio EXCEPT ![r] = novoTS]
  /\ IF novoTS > identidades[r][id].ts
       THEN identidades' = [identidades EXCEPT ![r][id] = candidato]
       ELSE identidades' = identidades
  /\ canal' = Broadcast(r, msg)
  /\ sessoes' = sessoes
  /\ UNCHANGED <<vivo, quedas, particaoAtiva, ladoDe>>

(***************************************************************************)
(* F1 (B-22, 2026-10-03): Cair/Reviver -- ver docs/plano-b22-falhas-        *)
(* explicitas-2026-10-03.md §1.3. Ação mínima, sem mecanismo de merge: uma  *)
(* réplica "caída" simplesmente não executa nenhuma ação local nem          *)
(* EntregaMensagem (processo parado não roda nada); mensagens endereçadas a *)
(* ela continuam se acumulando normalmente em canal[r] (Broadcast não       *)
(* verifica vivo), e ao reviver ela as drena uma a uma pela mesma lógica de *)
(* gate por ts que já existe -- convergindo ao mesmo estado final que um    *)
(* state-transfer produziria, sem precisar modelar snapshot.                *)
(*                                                                         *)
(* P-24 (2026-10-03 rodada 2, correção pedida pelo Senior/Gestor após       *)
(* `docs/validacao-b22-f2-2026-10-03.md` §2): `Cair` agora também exige     *)
(* `~particaoAtiva` -- antes só `Particionar` garantia a exclusão mútua     *)
(* F1/F2 (exigindo todas vivas para COMEÇAR uma partição), mas nada          *)
(* impedia `Cair` de disparar DURANTE uma partição já ativa, contradizendo  *)
(* o texto que descreve a exclusão como mútua/bidirecional. A interação     *)
(* "um nó cai enquanto particionado" nunca foi analisada (ex.: `Mesclar`    *)
(* poderia copiar de um `rf` morto sem que isso fosse uma decisão           *)
(* deliberada) -- corrige o código em vez do texto, custo de uma linha.     *)
(* `Reviver` não precisa do mesmo gate: reverter um `Cair` já ocorrido não  *)
(* envolve nenhuma leitura nova de `rf`, não cria a mesma ambiguidade.      *)
(***************************************************************************)
Cair(r) ==
  /\ vivo[r] = TRUE
  /\ ~particaoAtiva   \* P-24: exclusão mútua F1/F2 também nesta direção
  /\ vivo' = [vivo EXCEPT ![r] = FALSE]
  /\ quedas' = quedas + 1
  /\ UNCHANGED <<sessoes, identidades, canal, relogio, particaoAtiva, ladoDe>>

Reviver(r) ==
  /\ vivo[r] = FALSE
  /\ vivo' = [vivo EXCEPT ![r] = TRUE]
  /\ UNCHANGED <<sessoes, identidades, canal, relogio, quedas, particaoAtiva, ladoDe>>

(***************************************************************************)
(* F2 (B-22, 2026-10-03): Particionar/Mesclar -- ver                       *)
(* docs/plano-b22-falhas-explicitas-2026-10-03.md §2. F1 e F2 são           *)
(* mutuamente exclusivos nesta rodada (§2.5): Particionar exige que TODAS   *)
(* as réplicas estejam vivas. `P1` é escolhido não-deterministicamente      *)
(* entre TODOS os subconjuntos não-vazios e não-totais de REPLICAS (o TLC   *)
(* explora todas as formas de dividir o cluster, não uma hipotética fixa);  *)
(* `P2 == REPLICAS \ P1` é o resto, disjunto e não-vazio por construção.    *)
(***************************************************************************)
Particionar ==
  /\ ~particaoAtiva
  /\ \A r \in REPLICAS: vivo[r] = TRUE
  /\ \E P1 \in SUBSET REPLICAS \ {{}, REPLICAS} :
       LET P2 == REPLICAS \ P1 IN
       /\ particaoAtiva' = TRUE
       /\ ladoDe' = [r \in REPLICAS |-> IF r \in P1 THEN 1 ELSE 2]
       /\ UNCHANGED <<sessoes, identidades, canal, relogio, vivo, quedas>>

(* Mesclar: o lado VENCEDOR (por tamanho, Vencedor/§2.2) tem seu registro   *)
(* INTEIRO copiado para cada réplica do lado perdedor -- nunca uma          *)
(* comparação por `ts` (esse gate é exclusivo de EntregaMensagem). É        *)
(* literalmente `PREFERRED_NON_NULL`/`PREFERRED_ALWAYS` real (Achado E,     *)
(* A.1-A.2): "o valor da partição com mais réplicas vence", mesmo que       *)
(* descarte uma escrita genuinamente mais nova que tenha ocorrido no lado   *)
(* minoritário antes da partição ser detectada. `relogio` também é          *)
(* copiado -- sem isso, a réplica perdedora poderia calcular, após o        *)
(* Mesclar, um `ts` novo menor que o já recebido por cópia, perdendo o      *)
(* gate contra si mesma (artefato de modelagem, não comportamento real a    *)
(* preservar). `rf` (o "coordenador" do merge) é escolhido não-             *)
(* deterministicamente dentro do lado vencedor -- se esse lado ainda não    *)
(* tiver convergido internamente via EntregaMensagem normal, o TLC explora  *)
(* os dois resultados possíveis, expondo essa como uma pergunta aberta, não *)
(* decidida a priori (ver §2.4 do plano). Em empate (2 réplicas),           *)
(* `Vencedor` devolve os dois lados possíveis -- ambos explorados aqui      *)
(* também, via o `\E ladoVencedor` externo.                                 *)
Mesclar ==
  /\ particaoAtiva
  /\ \E ladoVencedor \in Vencedor(Lado(1), Lado(2)) :
     \E rf \in ladoVencedor :
       LET perdedores == REPLICAS \ ladoVencedor IN
       /\ sessoes'     = [r \in REPLICAS |-> IF r \in perdedores THEN sessoes[rf]     ELSE sessoes[r]]
       /\ identidades' = [r \in REPLICAS |-> IF r \in perdedores THEN identidades[rf] ELSE identidades[r]]
       /\ relogio'      = [r \in REPLICAS |-> IF r \in perdedores THEN relogio[rf]     ELSE relogio[r]]
       /\ particaoAtiva' = FALSE
       /\ UNCHANGED <<canal, vivo, quedas, ladoDe>>

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
  /\ PodeAgir(r)   \* F1+F2 (B-22): réplica caída ou do lado minoritário não processa nem a propria fila
  /\ Len(canal[r]) > 0
  /\ \E i \in 1..Len(canal[r]):
       LET m == canal[r][i] IN
       /\ canal' = [canal EXCEPT ![r] = SubSeq(@, 1, i-1) \o SubSeq(@, i+1, Len(@))]
       /\ UNCHANGED <<relogio, vivo, quedas, particaoAtiva, ladoDe>>
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
  \/ \E r \in REPLICAS:                          Cair(r)       \* F1 (B-22)
  \/ \E r \in REPLICAS:                          Reviver(r)    \* F1 (B-22)
  \/ Particionar                                               \* F2 (B-22)
  \/ Mesclar                                                   \* F2 (B-22)

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
(* real -- ficam para refinamento em TCC-II (Apêndice B, etapas 3-4).      *)
(*                                                                         *)
(* F1 (B-22, 2026-10-03, Cair/Reviver/vivo/quedas): a expectativa teórica   *)
(* (Achado E, A.3 -- F1 nunca ativa DEGRADED/merge-policy no Infinispan     *)
(* real) é que I1-I6 NÃO mudem de veredito com F1 isolado. Isso foi         *)
(* TESTADO, não presumido, e confirmado -- ver                             *)
(* docs/plano-b22-falhas-explicitas-2026-10-03.md §1.4/§4.1 e o veredito    *)
(* real registrado nas mensagens de commit da branch                      *)
(* feature/b22-f1-cair-reviver (nenhuma violação nova ligada a Cair/        *)
(* Reviver).                                                                *)
(*                                                                         *)
(* F2 (B-22, 2026-10-03, Particionar/Mesclar/particaoAtiva/ladoDe, Opção A  *)
(* para P-16): ao contrário de F1, a expectativa teórica (mesmo documento,  *)
(* §4.1) é que F2 DEVE produzir os primeiros contraexemplos reais de       *)
(* I1/I2/I4 do catálogo operação×falha×invariante -- ligados à janela de   *)
(* partição (escrita no lado minoritário antes da detecção, depois         *)
(* Mesclar descarta essa escrita por posição, não por timestamp). I5/I6     *)
(* devem continuar fechando após Mesclar + convergência intra-lado via     *)
(* EntregaMensagem -- também a TESTAR, não presumir. Ver veredito real nas  *)
(* mensagens de commit da branch feature/b22-f2-particao. A combinação     *)
(* F1+F2 e o esboço de scripts/inject-partition.sh (B-29) ficam para        *)
(* rodada futura (mesmo documento, §2.5/§3).                                *)
(***************************************************************************)
=============================================================================
