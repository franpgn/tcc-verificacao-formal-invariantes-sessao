# Registro de problemas

> Diário técnico de problemas encontrados na frente prática e suas resoluções. Cada entrada registra sintoma, diagnóstico e correção, com referência ao *commit*/arquivo.

## 2026-06-22 — Formação do cluster e baseline F1

### P-01 — `Unknown security domain 'default'` ao subir o nó

- **Sintoma:** os três nós saíam com `FATAL ISPN080028 ... ISPN080014: Unknown security domain 'default'`.
- **Causa:** `cluster/infinispan-cluster.xml` substitui integralmente o `infinispan.xml` da imagem; o `<endpoints security-realm="default"/>` referenciava um *realm* que a imagem definia, mas que se perdeu na substituição.
- **Correção:** redeclarar `<security><security-realms><security-realm name="default"><properties-realm/></security-realm></security-realms></security>` no `<server>`. O usuário `admin` continua sendo criado pelo *entrypoint* (env `USER`/`PASS`). Commit `fix(cluster): forma cluster size=3`.

### P-02 — TCPPING: `num_initial_members` não reconhecido

- **Sintoma:** `JGRP000001: configuration error: the following properties in TCPPING are not recognized: {num_initial_members=3}`.
- **Causa:** o atributo `num_initial_members` foi **removido** de `TCPPING` no JGroups 5.x (a contagem passou a ser derivada de `initial_hosts`). A imagem usa JGroups 5.3.16.Final.
- **Correção:** remover `num_initial_members` do `cluster/jgroups-tcp.xml`; a formação determinística do *cluster* fica a cargo do GMS sobre os 3 hosts estáticos.

### P-03 — `bind_addr=0.0.0.0` inválido no TCP do JGroups

- **Sintoma:** `BindException: [TCP] /0.0.0.0 is not a valid address on any local network interface`.
- **Causa:** o protocolo `TCP` do JGroups não aceita o literal `0.0.0.0` como `bind_addr`.
- **Correção:** usar o símbolo `SITE_LOCAL`, resolvido pelo JGroups para o IP privado da interface do *container* na rede *bridge*. Após isso o *cluster* forma: `Received new cluster view ... (3) [isn3, isn1, isn2]`, `health HEALTHY`, `number_of_nodes=3`.

### P-04 — REST health retorna 403 com BASIC auth

- **Sintoma:** `curl -u admin:infinispan` → `403 ISPN080052: The request authentication mechanism 'null' is not supported`.
- **Causa:** o *endpoint* REST do Infinispan 15 exige autenticação **DIGEST**; `curl -u` usa BASIC por padrão.
- **Correção:** usar `curl --digest -u admin:infinispan` (e nos *healthchecks* do `podman-compose.yml`).

### P-05 — Hot Rod: `ISPN004015 Failed adding new server 10.89.x.x`

- **Sintoma:** ~35% das operações com `ERROR_TRANSPORT`; cliente registrava `Failed adding new server 10.89.1.4/<unresolved>:11222`.
- **Causa:** o cliente Hot Rod recebia a topologia com os **IPs internos** dos *containers* (`10.89.x.x`), inalcançáveis a partir do *host* Windows/WSL2. As chaves cujo *owner* não fosse o nó de conexão falhavam.
- **Correção:** `<endpoint><hotrod-connector external-host="127.0.0.1" external-port="..."/></endpoint>`. O `external-port` por nó (11222/11223/11224) é passado como **argumento de servidor** `-Dinfinispan.hotrod.external-port=...` no `command` do *compose* — `JAVA_OPTS` via *env* é sobrescrito por `server.conf` e não funciona. A topologia passou a anunciar `127.0.0.1:11222/11223/11224` e `ISPN004015` zerou.

### P-06 — Hot Rod: erro de *marshalling* de POJOs (ERROR_TRANSPORT residual)

- **Sintoma:** mesmo após P-05, ~35% `ERROR_TRANSPORT`; todo `put` de `SessionState`/`IdentityState` falhava.
- **Causa:** o *marshaller* padrão do Hot Rod é **ProtoStream**, que exige *schema* registrado; os valores são POJOs `Serializable` sem *schema*.
- **Correção:** configurar `JavaSerializationMarshaller` no `ConfigurationBuilder` do `WorkloadMain`, com *allow-list* `br.unipampa.tcc.session.*` e `java.time.*`. Smoke passou a `0 ERROR_TRANSPORT` / `taxa_erro 0%`. Commit `fix(workload): usa JavaSerializationMarshaller`.

### P-07 — Janela de medição vazia em execuções curtas

- **Sintoma:** *smoke* de 60s não gravava eventos; resumo parava em "Warm-up ate +10s".
- **Causa:** `WarmupPolicy.padrao` usava o piso fixo de 60s de warm-up (`max(60, 10%*dur)`), consumindo toda uma execução curta; `--warmup-min-sec` da CLI não era repassado.
- **Correção:** *overload* `WarmupPolicy.padrao(..., warmupMinimoSeg, clock)` e fiação de `cli.warmupMinimoSeg` no `WorkloadMain`. O *default* de 60s permanece para as baterias reais. Commit `feat(workload): wire --warmup-min-sec`.

## 2026-09-16 — Correção de broadcast + LWW em `SessionStore.tla`

### P-08 — `EntregaMensagem` entregava a uma única réplica, nunca *broadcast*

- **Sintoma:** `I5`/`I6` (convergência sob quiescência) violavam mesmo com o
  canal vazio (achado da rodada de 07/09).
- **Causa:** `canal` era uma `Seq(Mensagem)` **global**; `EntregaMensagem(r)`
  escolhia um índice arbitrário e entregava a mensagem a **uma única**
  réplica `r`, removendo-a do canal — nunca havia *broadcast* a todas as
  réplicas destino.
- **Correção:** `canal` passou a `[REPLICAS -> Seq(Mensagem)]`; novo operador
  `Broadcast(origem, msg)` enfileira a mensagem em todas as filas exceto a de
  origem. Commit `7ce78ca`, branch `fix/canal-broadcast-por-replica`.

### P-09 — `TypeOK` violava após incluir `canal` (tipo de `Mensagem.payload` incorreto)

- **Sintoma:** ao adicionar `canal \in [REPLICAS -> Seq(Mensagem)]` a `TypeOK`
  (necessário para a Mudança 2), o TLC violou `TypeOK` em 2 estados.
- **Causa:** `Mensagem` declarava `payload: STRING`, mas mensagens
  `SESSAO_VALIDA` sempre carregam uma identidade (`IDS`), não uma *string* —
  inconsistência já presente na spec original, nunca exposta porque `canal`
  nunca fizera parte de `TypeOK` antes.
- **Correção:** `payload: STRING \cup IDS`. Commit `aefe391`.

### P-10 — gate LWW compartilhado incorretamente entre dois registradores independentes

- **Sintoma:** após implementar LWW via relógio lógico (Mudança 2 completa),
  `I5` ainda violava em `tiny`.
- **Causa:** em `EntregaMensagem`, os *cases* `SESSAO_VALIDA`/`SESSAO_INVALIDA`
  escreviam dois registradores LWW independentes (`sessoes`+`sessaoDe`;
  `sessoes`+`motivos`) sob um único `IF m.prio > sessoesTS[...]`. Como
  `Logout`/`Block` avançam `sessoesTS` sem tocar `sessaoDeTS`, os dois podiam
  divergir, e o gate único descartava indevidamente escritas que deveriam
  valer para o campo com TS mais atrasado.
- **Correção:** gate independente por campo (`sessaoDe` por `sessaoDeTS`,
  `motivos` por `motivosTS`), mesmo padrão já usado em `CONTADOR_INC`/
  `ID_DESBLOQUEIA`. Commit `a5622f2`, branch
  `fix/lww-gate-por-campo-independente`.

### P-11 — escrita local sobrescrevia incondicionalmente valor remoto de prioridade maior

- **Sintoma:** mesmo após P-10, `I5` violava; um teste diagnóstico (`Block`/
  `Unblock` temporariamente removidos de `Next`) confirmou que o problema
  ocorria mesmo sem `Block` envolvido.
- **Causa:** as 6 ações de escrita local sobrescreviam campo+TS de forma
  incondicional, sem comparar com o TS local já armazenado — assimetria com
  `EntregaMensagem`, que é condicional. Uma réplica podia receber (via
  mensagem) um valor de prioridade alta, e depois "desfazê-lo" com uma
  escrita local de prioridade mais baixa (relógio local atrasado).
- **Correção:** cada ação local aplica a mesma regra de comparação LWW que
  `EntregaMensagem` usa (aplica a própria mensagem a si mesma pela mesma
  função de *merge*). Commit `f8e9e02`, branch `fix/lww-gating-escrita-local`.

### P-12 — cascata de `Block` não propagada a réplicas remotas (não corrigido nesta rodada)

- **Sintoma:** mesmo após P-08 a P-11, `I5` ainda viola em todas as
  cardinalidades testadas.
- **Causa:** `Block(r,id)` invalida localmente, na própria réplica, todas as
  sessões `s` com `sessaoDe[r][s]=id ∧ sessoes[r][s]=VALID` (efeito cascata).
  A mensagem `ID_BLOQUEIA` propagada só carrega `chave=id`; o *case*
  `ID_BLOQUEIA` de `EntregaMensagem` só atualiza `status` na réplica destino,
  nunca reaplica esse cascade sobre `sessoes`/`motivos`.
- **Estado:** documentado com contraexemplo completo em
  `docs/relatorio-tlc-2026-09-16.md` §3.2; correção requer decisão de projeto
  nova (fora do escopo desta execução) — candidato a "Mudança 3".

### P-13 — bloqueio por limite de tentativas não revertido por reset de prioridade maior (não corrigido)

- **Sintoma:** `I6` viola em todas as cardinalidades testadas mesmo após
  P-08 a P-11.
- **Causa:** manifestação do trade-off já sinalizado pela decisão D-2 do
  plano (`contador` como LWW-register de valor absoluto, não CRDT
  *op-based*): um `status=BLOCKED` disparado localmente por um valor de
  `contador` que é depois superado por LWW por um `CONTADOR_RESET` de
  prioridade maior não é revertido, porque `CONTADOR_RESET` nunca toca
  `status` — mesma semântica local que já existia antes desta rodada
  (`ResetFailures` local também nunca desbloqueia).
- **Estado:** documentado com contraexemplo completo em
  `docs/relatorio-tlc-2026-09-16.md` §3.3; aceito como limitação do
  LWW-register escolhido para `contador`, ou candidato a "Mudança 3".

## 2026-09-16 (rodada 2) — Mudança 3: cascata de `Block` + gate de status em `CONTADOR_INC`

### P-14 — cascata de `Block` não propagada a réplicas remotas (resolve P-12)

- **Sintoma:** P-12 (`I5` viola: `status` converge sob quiescência, mas
  `sessoes`/`motivos` das sessões invalidadas por `Block` não convergem).
- **Causa:** confirmada como descrita em P-12 — a mensagem `ID_BLOQUEIA` só
  carregava `chave=id`, nunca o cascade de invalidação.
- **Correção:** `Mensagem` ganhou o campo `sessoesAfetadas: SUBSET SIDS`;
  `Block` passa a computar `{s \in SIDS : Afetado(s)}` (o mesmo `Afetado(s)`
  já usado para a escrita local) e embuti-lo na mensagem; `EntregaMensagem`,
  *case* `ID_BLOQUEIA`, reaplica o cascade por sessão, gateado
  independentemente por `sessoesTS`/`motivosTS` (mesmo padrão de P-10).
  Commit `b9c46e2`, branch `fix/cascata-bloqueio-sessoes-afetadas`. **`I5`
  fecha exaustivamente em `tiny`/`small`/`medium` após esta correção** (ver
  `docs/relatorio-tlc-2026-09-16-mudanca3.md`).

### P-13 (atualização) — causa original fechada pela Rota (c); ver P-15 para a causa nova exposta

- A causa original de P-13 (`ResetFailures`/`CONTADOR_RESET` de prioridade
  maior deixando um `status=BLOCKED` "preso") foi retraçada manualmente
  contra a Rota (c) (ver `docs/plano-fix-cascata-bloqueio-2026-09-16.md`
  §3.4) e confirmada fechada por essa correção. **Mas `I6` continua violando
  em todas as cardinalidades** — por uma causa nova e diferente, nunca
  antes observada porque ficava mascarada pelo gate acoplado que a própria
  Rota (c) removeu. Ver P-15.

### P-15 — mensagem `CONTADOR_INC` "fantasma": broadcast incondicional de um valor de `contador` cuja própria escrita local foi rejeitada por LWW (achado real, não corrigido)

- **Sintoma:** mesmo após P-14 e a Rota (c) (desacoplamento do gate de
  `status` em `CONTADOR_INC`), `I6` ainda viola em `tiny`/`small`/`medium`/
  `full` — agora com um contraexemplo **diferente** do de P-13.
- **Causa:** em `IncrementFailure(r,id)`, a mensagem `msg == [tipo |->
  "CONTADOR_INC", ..., valor |-> novo, ..., prio |-> p]` é **sempre**
  construída e enviada via `Broadcast(r, msg)`, **independentemente** de o
  `IF p > contadorTS[r][id]` que decide a escrita local ter aceitado ou
  rejeitado `novo` (spec/SessionStore.tla, ação `IncrementFailure`, linhas
  226–239). Quando essa escrita local perde sua própria corrida de LWW
  (réplica com relógio atrasado frente a um valor já recebido de outra
  réplica), `novo` nunca se torna `contador` real em réplica nenhuma — mas a
  mensagem "fantasma" com esse `valor`/`prio` ainda é propagada. Antes da
  Rota (c), essa mensagem fantasma era inofensiva: o gate único
  (`m.prio > contadorTS[...]`) descartava o `case` inteiro, `status`
  incluído. **Depois** da Rota (c), o gate de `status` passou a ser avaliado
  por seus próprios termos (`m.prio > statusTS[...]`, independente de
  `contadorTS`) — e pode vencer essa corrida **mesmo quando a corrida de
  `contador` foi perdida**, aplicando `BLOCKED` numa réplica remota a partir
  de um valor de `contador` que nunca existiu de fato em réplica alguma.
  `contador` continua convergindo corretamente (LWW funciona); é `status`
  que diverge, ficando preso em `BLOCKED` numa réplica que nunca teve
  `contador` real atingindo `LIMITE`.
- **Por que não é a mesma causa de P-13:** P-13 envolvia um valor de
  `contador` que **foi real** em algum momento (aplicado localmente,
  cruzou `LIMITE`) e foi depois **superado** por um `CONTADOR_RESET` de
  prioridade maior. P-15 envolve um valor que **nunca foi real em réplica
  nenhuma** — a mensagem é fantasma desde a origem, não apenas superada
  depois.
- **Estado:** documentado com contraexemplo completo em
  `docs/relatorio-tlc-2026-09-16-mudanca3.md` (achado em `tiny`, confirmado
  em `small`/`medium`/`full`); **não corrigido nesta rodada** — reportado ao
  Gestor antes de propor uma quarta correção, por instrução explícita de não
  encadear correções sem validação.

## 2026-09-16 (rodada 3) — Mudança 4: broadcast fantasma de `CONTADOR_INC`

### P-15 (resolução) — mensagem `CONTADOR_INC` "fantasma" corrigida

- **Causa:** ver descrição completa acima (mesma seção P-15) — em
  `IncrementFailure(r,id)`, `canal' = Broadcast(r, msg)` executava
  incondicionalmente, fora do `IF p > contadorTS[r][id]` que decide a
  escrita local. Uma escrita local rejeitada por LWW ainda assim propagava
  a mensagem `CONTADOR_INC` com o valor rejeitado, que um gate remoto
  independente (`status`, Rota c/Mudança 3b) podia aceitar mesmo sem
  `contador` jamais ter vencido em réplica alguma.
- **Correção:** `canal' = Broadcast(r, msg)` movido para **dentro** dos
  dois ramos do `IF p > contadorTS[r][id]` já existente em
  `IncrementFailure` — `Broadcast` só no ramo aceito, `canal' = canal`
  (nenhuma mudança) no ramo rejeitado. Análise estrutural prévia (plano
  `docs/plano-fix-broadcast-fantasma-2026-09-16.md` §2) mostrou que essa é
  a **única** ação de escrita com o ingrediente que causa esse tipo de bug
  (um gate que lê o *valor*, não só a prioridade, de um campo diferente do
  que está protegendo); as outras 5 ações (`Login`, `Logout`,
  `ResetFailures`, `Block`, `Unblock`) não precisaram de mudança de
  comportamento, só de comentário documentando a análise. Commit
  `b6e0ec4`, branch `fix/broadcast-fantasma-contador-inc`.
- **Resultado:** `I6` fecha exaustivamente em `tiny` (32.129 estados),
  `small` (644.013 estados) e `medium` (1.671.294 estados) — sem
  contraexemplo em nenhuma das três. Em `full`, `I6` fica inconclusivo
  dentro do timebox de 20 min (mesma classe de intratabilidade que já
  afeta `TypeOK`/`I3`/`I4`/`I5` nessa cardinalidade), mas **sem nenhum
  contraexemplo** em 1,12 bilhão de estados gerados — antes desta correção,
  `I6` violava em `full` com apenas 10 estados. Sem regressão em
  `TypeOK`/`I1`..`I5`. Nenhuma quinta correção foi necessária. Detalhes
  completos em `docs/relatorio-tlc-2026-09-16-mudanca4.md`.

## 2026-10-02 — Revisão do mecanismo de conflito (Achado E): registro único por chave

> Contexto completo em `docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md`
> (plano, Fases 1–5) e nos comentários de `spec/SessionStore.tla`. Esta rodada
> executou as Fases 1–3 (spike de confirmação contra o código-fonte real do
> Infinispan 15.0.21.Final; redesenho de `SessionStore.tla`/`MCSession.tla`
> para registro único por chave; reverificação TLC em `tiny`/`small`/`medium`).
> As Fases 4–5 (ação de partição F2 real) ficam para rodada futura, por
> instrução explícita do Gestor.

### Fase 1 — confirmado contra código-fonte real (não suposição)

- `org.infinispan.conflict.MergePolicy.PREFERRED_NON_NULL` (fonte real,
  `infinispan-core-15.0.21.Final-sources.jar`, Maven Central) é regra
  **posicional** (`preferredEntry` = réplica primária da partição com mais
  nós; empate por maior `topologyId`), **sem nenhuma comparação de
  timestamp** — confirma A.1 do documento de revisão.
- `org.infinispan.interceptors.locking.NonTransactionalLockingInterceptor` +
  `org.infinispan.interceptors.distribution.{NonTxDistributionInterceptor,
  BaseDistributionInterceptor}` (fontes reais): escrita não-transacional
  adquire *lock* no *owner* primário da chave (`lockAndRecord` em
  `AbstractLockingInterceptor.visitNonTxDataWriteCommand`), aplica
  localmente, e replica **sincronamente** (`rpcManager.getSyncRpcOptions()`,
  aguardando `invokeCommand(...)`) aos *backup owners* antes de liberar o
  *lock* e retornar — confirma A.4 (sem janela de reordenação a resolver em
  operação nominal).
- `org.infinispan.partitionhandling.impl.PreferConsistencyStrategy.
  onClusterViewChange` (fonte real): `isMinorityPartition(stable, lost) ==
  lost.size() >= Math.ceil(stable.size()/2d)`. Para F1 (3 réplicas estáveis,
  1 perdida): `1 >= Math.ceil(1.5) = 2` é **falso** — não é partição
  minoritária; com `owners=2 < 3` réplicas, nenhum segmento perde todos os
  seus *owners* (`lostDataCheck`), então o modo DEGRADED **não ativa** e
  `merge-policy` **não é invocado** para F1. Confirma A.3 com leitura direta
  do código (não só do javadoc) — a hipótese do Gestor estava certa.
- **Achado adicional (vai além do documento de 02/10):**
  `PartitionHandlingConfiguration.resolveConflictsOnMerge()` depende só de
  `mergePolicy() != NONE`, **não** de `whenSplit()` — ou seja,
  `merge-policy` *pode* disparar sob `DENY_READ_WRITES` (configuração deste
  projeto) também, não só sob `ALLOW_READ_WRITES` como o javadoc de
  `EntryMergePolicy` sugere à primeira leitura; a condição real é "houve um
  evento de *merge* de partição E o estado pós-merge voltou a ser
  AVAILABLE" (`PreferConsistencyStrategy.onPartitionMerge`), independente de
  `whenSplit`. Não muda a conclusão sobre F1 (que nunca chega a
  `onPartitionMerge`, por não haver partição), mas é relevante para o
  desenho da Fase 4 (F2) futura.

### P-16 — colapso em registro único remove by-construction P-10/P-15-like bugs, mas expõe um NOVO empate de `ts` sem desempate por réplica (I5/I6 regridem em `tiny`/`small`/`medium`)

- **Mudança:** `spec/SessionStore.tla` passou de 5 variáveis de sessão + 2 de
  identidade (cada uma com relógio LWW por campo + `RepIndex`/`Prio` para
  desempate determinístico por réplica) para 2 variáveis compostas
  (`sessoes: SessaoRec`, `identidades: IdentRec`), cada uma com um único
  campo `ts`, espelhando `SessionState.java`/`IdentityState.java`. O
  desempate por identidade de réplica (`RepIndex`/`Prio`) foi removido junto
  com a decomposição por campo — `relogio[r]` voltou a ser um contador de
  versão local simples, sem termo dependente de qual réplica escreveu.
- **Efeito OK (esperado, por construção):** `TypeOK`/`I3`/`I4` continuam
  fechando exaustivamente em `tiny` (6.472 estados, `MaxCanal=1,
  MaxRelogio=3`), `small` (113.213 estados) e `medium` (287.114 estados);
  `I1`/`I2` continuam violando por construção (propriedades *cross-réplica*
  não condicionadas a quiescência, mesmo padrão de sempre). O espaço de
  estados ficou **menor**: `small`/`TypeOK` tinha 644.013 estados distintos
  no modelo anterior (mesmos parâmetros `MaxCanal=1,MaxRelogio=3`,
  `git diff runs/tlc/inv-small/small-TypeOK.out`) e passou a 113.213 — cerca
  de 5,7× menos, consistente com menos dimensões de relógio independentes.
- **Sintoma NOVO:** `I5` e `I6`, que fechavam exaustivamente sob os mesmos
  parâmetros no modelo anterior (commit `ddf9dfc`: `small`/`I6` = 644.013
  estados, "No error has been found"), agora **violam** em `tiny` (5
  estados), `small` (5 estados) e `medium` (5 estados) — mesmo padrão nas
  três cardinalidades.
- **Causa:** sem `RepIndex`/`Prio`, dois replicas podem alcançar o **mesmo**
  valor de `ts` escrevendo **localmente e concorrentemente** em campos
  diferentes do mesmo registro (ex.: `r1` faz `Block(u1)` e `r2` faz
  `IncrementFailure(u1)`, ambos partindo de `relogio=0`, ambos chegando a
  `ts=1`). Cada réplica aceita sua própria escrita local (passa seu próprio
  gate `novoTS > ts local`), e quando a mensagem da outra réplica chega, o
  gate `m.ts > ts local` falha por **empate** (`1 > 1` é falso) — a mensagem
  remota é descartada nos dois sentidos, e as réplicas divergem
  **permanentemente** mesmo sob quiescência. No modelo anterior, esse
  cenário específico não produzia divergência porque `status`/`contador`
  eram campos com relógios (`statusTS`/`contadorTS`) **independentes**: as
  duas escritas não competiam pelo mesmo `ts`. Ao colapsar em um único
  registro atômico (fidelidade ao `put()` real do Java, que substitui o
  objeto inteiro), as duas escritas concorrentes **passam a competir pelo
  mesmo `ts`** — e sem desempate por réplica, o empate é possível e não é
  resolvido.
- **Estado:** **não corrigido nesta rodada** — reportado ao Gestor/Senior
  antes de decidir. Trade-off explícito: reintroduzir um desempate
  determinístico (ex.: o próprio `RepIndex`/`Prio` anterior, agora a nível
  de registro, não de campo) resolveria o empate e provavelmente restauraria
  I5/I6, mas reintroduz exatamente o tipo de mecanismo ("resolução por
  timestamp com desempate por identidade de réplica") que o Achado E (A.1,
  A.4) mostrou não corresponder a nenhum mecanismo real do Infinispan —
  mesmo como dispositivo de modelagem interno, não como afirmação sobre o
  sistema real. Decisão sobre se/como fechar isso (aceitar a violação
  documentada, reintroduzir desempate, ou remover a reordenação não-FIFO do
  canal) cabe ao Gestor/Senior. Evidência completa (contraexemplos de 4–9
  estados, `TypeOK`/`I1`-`I6` nas três cardinalidades) em
  `runs/tlc/inv-{tiny,small,medium}/`.

### P-17 — `IdentityState.desbloquear()` não zerava `contadorFalhas` (O7), divergindo de `Metodologia.tex` e do modelo TLA+

> **Nota de numeração:** o plano de consolidação B-21
> (`docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §2.3) já havia reservado
> "P-16" para a seção P-08 da Cadeia 2 (`netem`/`nsenter`), a renumerar no
> momento do merge. Como a entrada acima (revisão do mecanismo de conflito)
> também ficou "P-16" por ter sido feita primeiro nesta mesma branch, e esta
> aqui é sequencialmente a próxima, a seção da Cadeia 2 passa a virar **P-18**
> no merge (não P-17 como o Gestor havia instruído antes desta entrada
> existir) — ajuste necessário, não um desvio da instrução original.

- **Sintoma:** não é um achado de execução/TLC — é uma leitura de código
  confirmada pelo Senior em `docs/validacao-fases1-3-achado-e-2026-10-02.md`
  §4.2 contra `tcc-escrita/Metodologia.tex` linha 78 ("O7, desbloqueio... efeito:
  β'(u).marca = Free, **β'(u).counter = 0**").
- **Causa:** `IdentityState.desbloquear()` (`workload/src/main/java/.../
  IdentityState.java`) reconstruía o registro preservando `contadorFalhas`
  (só `resetar()`, usado por O5, zerava) — divergindo da especificação oficial
  já aprovada e de `spec/SessionStore.tla`, ação `Unblock`, que **sempre**
  zerou `contador` (tanto antes quanto depois da revisão do Achado E). O
  modelo formal estava certo; o código Java tinha o bug.
- **Correção:** `desbloquear()` passa a zerar `contadorFalhas` (`0L` em vez de
  preservar o valor atual), mesma assinatura e mesmo padrão de incremento de
  `versao`. Mudança de 1 linha de efeito (mais javadoc). Nenhum teste
  existente exercitava `desbloquear()` diretamente (não há
  `IdentityStateTest.java`/`SessionOpsTest.java` hoje — achado já registrado
  em `docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §3.2), então a suíte
  JUnit completa (49 testes) permanece verde antes e depois da correção, sem
  necessidade de ajustar nenhum teste. **Relevante para a credibilidade de
  dado real de O7** gerado por B-23/B-24 (ainda não implementados) — corrigido
  antes de qualquer execução real que gere esse dado.

## 2026-06-23 — Baseline F3 na VM Ubuntu

### P-18 — `podman exec <node> tc ...` não aplica netem (F3)

> **Nota de numeração (merge B-21):** esta seção era "P-08" na Cadeia 2
> (`feature/f3-baseline`). Renumerada para **P-18** ao consolidar com a
> Cadeia 1 (TLA+/Achado E) em `docs/registro-de-problemas.md`, porque "P-08"
> já estava ocupado pela Cadeia 1 (`EntregaMensagem`/*broadcast*, 2026-09-16)
> e "P-16"/"P-17" já foram usados nesta mesma branch para a revisão do
> Achado E e o fix do bug O7, respectivamente — ver nota em P-17 acima. Não
> há nenhuma referência cruzada a "P-08" apontando para este item em nenhum
> outro documento (verificado via busca textual antes do merge), então a
> renumeração não quebra nenhuma citação existente.

- **Sintoma:** ao rodar o baseline F3 na VM, a injeção falhava: a imagem oficial Infinispan (UBI minimal) não tem `tc` no `PATH` e o container não recebe a capability `NET_ADMIN`, então `podman exec <node> tc qdisc add ... netem` é rejeitado.
- **Diagnóstico/validação:** aplicar o `netem` **a partir do host**, entrando no *namespace de rede* do container via `nsenter`, funciona (exit 0; `qdisc netem ... delay 20ms 13ms` aplicado no `eth0` do container e removido limpo). Comandos validados manualmente: `PID=$(podman inspect -f '{{.State.Pid}}' isn2)`; `sudo nsenter -t $PID -n tc qdisc add dev eth0 root netem delay 20ms 13ms distribution normal`; `... qdisc show`; `... qdisc del dev eth0 root`.
- **Correção:** `scripts/inject-jitter.sh` e `verificar_sem_netem()` em `scripts/run-baseline.sh` migrados de `podman exec` para `sudo nsenter -t <pid> -n tc ...`, com o PID resolvido por `podman inspect` antes de registrar o `trap` de cleanup (cleanup idempotente em EXIT/INT/TERM mesmo se o `add` falhar). Dependências da execução real agora incluem `nsenter` (util-linux) e `sudo` no host (container não precisa de `tc`/`NET_ADMIN`); `--dry-run` segue sem dependências. Exit codes preservados (3 dependência ausente, 4 container/PID, 5 netem rejeitado). Recomenda-se `sudo` sem senha para a bateria (cache do sudo expira em ~15 min). Detalhe em `docs/implementacao.md` TD-007. Commit `fix(f3): aplica netem via nsenter host-side`.

## 2026-10-02 (rodada 2) — B-23/B-24: auditoria real de I1/I4 e leitores multi-réplica de I5/I6

> Contexto: `docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §3.2/§3.3. Branch
> `feature/b23-auditoria-i1-i4`, a partir de `feature/b21-consolidacao-base`
> (`8836e95`).

### P-19 — `ClientIntelligence.BASIC` não restringe `RemoteCache#entrySet()` a dados locais do nó (achado confirmado contra a API real, impacta B-24)

- **Contexto:** B-24 propunha abrir um `RemoteCacheManager` dedicado por nó,
  com `ClientIntelligence.BASIC`, para que cada leitor enxergasse "o que
  aquele nó vê localmente agora" — necessário para I5/I6 detectarem
  divergência real entre réplicas. O próprio plano do Senior marcava isso
  como "candidato a confirmar contra a lib real", não como certeza.
- **Verificação feita (não suposição):** com o cluster real de 3 nós
  (`cluster/podman-compose.yml`) rodando, escrevi 200 chaves via um
  `RemoteCacheManager` principal (topology-aware), depois abri 3
  `RemoteCacheManager` **distintos**, um por porta (11222/11223/11224),
  cada um com `clientIntelligence(ClientIntelligence.BASIC)` e um único
  servidor configurado, e chamei `entrySet()` em cada um.
- **Resultado (reproduzido 2x):** os **três** retornaram a contagem
  **completa** (200/200) das chaves escritas — não uma fração local por nó,
  como se esperava. `ClientIntelligence.BASIC` afeta **apenas** o roteamento
  de comandos de **chave única** (impede o cliente de descobrir/redirecionar
  para o *owner* real via topologia) — **não** afeta operações de leitura em
  massa (`entrySet()`/iteração), que o Infinispan coordena no lado do
  **servidor**, cluster inteiro, independentemente do que o cliente sabe
  sobre a topologia. Ou seja, `entrySet()` sempre devolve a visão **já
  convergida** do cluster, nunca a visão local/potencialmente divergente de
  um nó específico.
- **Impacto:** a infraestrutura de leitores multi-réplica de B-24
  (`WorkloadMain.abrirLeitoresMultiReplica`) está corretamente conectada
  (sem exceção, testada contra o cluster real de 3 nós) e serve como base
  útil, mas **não cumpre o objetivo original** de ler o estado local por nó
  — uma divergência real entre réplicas tende a **não ser detectada** por
  este mecanismo, mesmo quando existe, porque a leitura em si já reflete o
  valor convergido. Isso também significa que o resultado "I5/I6 = 0
  violações" da bateria F1 real desta rodada (ver P-20) **não é evidência
  forte de convergência real** — pode ser convergência de fato, ou pode ser
  um artefato deste mecanismo de leitura, que não tem como diferenciar os
  dois casos.
- **Estado:** **não corrigido nesta rodada** — reportado ao Gestor/Senior,
  por instrução explícita de não redesenhar sem validação. Candidatos para
  rodada futura (não implementados, não avaliados em profundidade):
  iteração restrita a segmentos localmente possuídos por cada nó
  (`RemoteCache#entrySet(IntSet segments)`, exigiria saber quais segmentos
  cada nó possui, o que por sua vez exige alguma forma de consciência de
  topologia — tensão a resolver); ou uma API diferente (REST/JMX por nó)
  para inspeção de estado local, fora do protocolo Hot Rod de aplicação.

### P-20 — primeira bateria F1 real pós-B-23/B-24: 0 violações detectadas, consistente com o Achado E (não é ausência de teste, é o resultado real)

- **O que foi executado:** cluster real de 3 nós subido
  (`podman-compose up -d`, `size=3 HEALTHY` confirmado via REST); jar
  empacotado (`mvn package`); duas execuções curtas via invocação direta do
  workload (não via `scripts/run-baseline.sh`, para não sobrescrever os
  dados históricos de `runs/S1-{none,F1}/` já committados pela Cadeia 2 —
  saída redirecionada para `runs/S1-{none,F1}-b23b24-smoke/`):
  - **`none`** (sem falha): cenário S1, 90s, warm-up 10s, 8 threads, seed
    2001 — 2.170.083 operações, **0 violações** (I1-I6).
  - **`F1`** (crash real): mesma configuração, seed 3001, com
    `scripts/inject-crash.sh --node isn2 --duration 30` disparado em +15s
    (dentro da janela de medição). O log do cliente Hot Rod confirma a
    falha real: `ISPN004006: Server sent new topology view ... containing 2
    addresses` (isn2/porta 11223 caiu), seguido ~37s depois por `containing
    3 addresses` (isn2 reentrou, `New server added`). Cluster voltou a
    `size=3 HEALTHY` ao final. 2.103.454 operações, **0 violações** (I1-I6).
- **Leitura correta deste resultado (nuance importante, ver P-19):** para
  **I1-I4** (detectados via instrumentação direta em `SessionOps`, não via
  `entrySet()`), 0 violações é um resultado forte e significativo — bate
  exatamente com o que as Fases 1-3 (Achado E) já haviam estabelecido a
  partir do código-fonte real do Infinispan: F1 (1 de 3 réplicas cai,
  `owners=2`) nunca ativa modo `DEGRADED`/`merge-policy`, é só
  `state-transfer`/rebalanceamento — não há mecanismo algum, nesta falha,
  que deveria produzir uma invalidação revertida (I1) ou um motivo
  inconsistente (I4). Para **I5/I6** (via `entrySet()` multi-réplica), 0
  violações **não pode ser lido com a mesma confiança** por causa de P-19 —
  o mecanismo de leitura não teria detectado uma divergência real mesmo que
  ela tivesse ocorrido brevemente durante a janela de `state-transfer`.
- **Estado:** primeiro dado real para o catálogo operação×falha×invariante
  do plano de 24/09 (§3.2, hoje PENDENTE) — registra o resultado real,
  **sem forçar nenhuma expectativa**, incluindo a ressalva de P-19. CSVs
  brutos em `runs/S1-none-b23b24-smoke/` e `runs/S1-F1-b23b24-smoke/`
  (`rep-001.csv`, `violacoes-rep-001.csv`, `inject-crash.log`).

## Observação de modelagem (não é defeito)

Com 8 *threads* = 8 identidades fixas (`user-<tid>`), `incrementFailure`
atinge o limite (5) cedo e bloqueia a identidade; logins subsequentes
retornam `BLOCKED` e `validate` retorna `NONE` (sem sessão criada).
Isso é comportamento de domínio correto (códigos de sucesso, não erro) e
produz dados válidos com `taxa_erro ~0`. Para variar mais o estado em
baterias futuras, ampliar o universo de identidades.
