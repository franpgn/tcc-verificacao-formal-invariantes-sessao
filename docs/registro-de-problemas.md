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

## 2026-10-02 (rodada 3) — B-24 corrigido (`entrySet(IntSet)`), I4 cobre `logout`/`block`, bateria F1 real com medição de convergência

> Contexto: `docs/validacao-b23-b24-2026-10-02.md` (Senior refuta a conclusão de
> impossibilidade estrutural de P-19 e valida `entrySet(IntSet)` +
> `CacheTopologyInfo.getSegmentsPerServer()`, inclusive sob F1 real, com *cache*
> ad-hoc). Esta rodada aplica a correção às caches reais do projeto.

### P-19 (resolução) — `entrySet(IntSet)` + segmentos reais substitui `entrySet()`

Ver commit da correção (`WorkloadMain.abrirLeitoresMultiReplica`/`materializar`/
`segmentosDoServidor`): a topologia é obtida de um cliente topology-aware (o
`RemoteCacheManager` principal, já existente) e re-obtida a cada chamada do
`Supplier` (não capturada uma única vez). API confirmada contra
`infinispan-client-hotrod-15.0.0.Final-sources.jar` e
`infinispan-commons-15.0.0.Final-sources.jar` antes de codificar.

### P-21 — leitores restritos por segmento expõem bug pré-existente em `verificarI5`/`verificarI6`: ausência por não-posse tratada como divergência (achado real, corrigido)

- **Sintoma:** ao rodar a primeira bateria F1 real pós-correção de P-19 (sem
  nenhuma falha chegar a ser injetada ainda, por um erro de orquestração do
  probe de medição — ver abaixo), `WorkloadMain` reportou **198 violações**
  (190 `I5` + 8 `I6`) numa execução **sem falha alguma**. 190 bate exatamente
  com o tamanho da *cache* `sessions` (190 chaves) e 8 com `counters` (8
  identidades) naquele momento — sinal claro de que TODA chave estava sendo
  contabilizada como violação, não um subconjunto real.
- **Causa:** `InvariantAuditor.verificarI5`/`verificarI6` foram escritos para
  a época do `entrySet()` sem filtro (P-19), quando todo leitor via o
  *dataset* completo e convergido — qualquer ausência de chave num leitor
  era, de fato, suspeita. Com os leitores agora restritos por segmento
  (`entrySet(IntSet)`, correção de P-19 acima), **cada réplica só devolve as
  chaves cujo segmento ela possui** — com `owners=2` de 3 réplicas, uma dada
  chave aparece em exatamente 2 dos 3 leitores e fica **ausente** (não
  presente no `Map`, não um valor `null` associado) no terceiro, **sempre,
  por design, mesmo sem nenhuma falha**. O código antigo tratava essa
  ausência esperada como `null` e comparava isso contra os valores dos
  outros leitores como se fosse uma divergência real.
- **Correção:** `verificarI5`/`verificarI6` agora só comparam, para cada
  chave, os leitores que **de fato a possuem** (valor não-nulo); um leitor
  sem a chave é ignorado para aquela chave, não tratado como divergência.
  Aproveitado para completar `I5` (passa a comparar `estado`+`motivo`+
  `identidade`, igual à definição de `spec/SessionStore.tla`; antes só
  comparava `estado`). 3 novos testes em `InvariantAuditorTest` cobrem o
  cenário que causava o bug.
- **Observação de processo:** este bug **não foi** encontrado pelo Senior —
  os probes dele (`SegmentProbe`/`CrashRaceProbe2`) validaram o mecanismo
  `entrySet(IntSet)` isoladamente, nunca o integraram com a lógica de
  comparação já existente em `InvariantAuditor`. Só apareceu ao rodar o
  sistema real, de ponta a ponta, contra o cluster real — reforça o valor de
  rodar a bateria real a cada mudança, não só validar o mecanismo em
  isolamento.

### P-22 — correção de I4 em `logout()`/`block()`; bateria F1 real pós-correção; medição de convergência (dado para `W_quiesc`)

- **Correção de I4 (achado do Senior, `docs/validacao-b23-b24-2026-10-02.md`
  §5):** `logout()` (ramo *no-op*) e `block()` (cascata) liam o estado atual
  da sessão e sobrescreviam `invalidacoesConfirmadas` incondicionalmente, sem
  checar divergência primeiro — uma violação real de I1/I4 cuja primeira
  observação pós-divergência passasse por um desses caminhos (em vez de
  `validate()`) escapava detecção silenciosamente. Corrigido chamando
  `verificarI1I4` antes de escrever, nos dois métodos.
- **Bateria F1 real (3 execuções, após as duas correções acima):** cluster
  real de 3 nós; jar reempacotado; workload real (`java -jar`, não
  `scripts/run-baseline.sh`) com 120s, *warm-up* 10s, 8 *threads*, cenário S1,
  escrevendo em `runs/S1-F1-b24fix-smoke{,2,3}/` (CSVs brutos, *gitignored*
  como sempre). Em paralelo, uma sonda Java dedicada (`ConvergenceProbe`,
  fora do repositório, em *scratch*, nunca *commitada* — mesmo padrão do
  Senior) disparou o crash real (`podman stop isn2`, +~16s, por 20s;
  `podman start isn2` depois) e mediu convergência via `entrySet(IntSet)`.
  Resultado das 3 execuções: **2.980.721 / 2.968.590 / 2.898.767 operações,
  0 violações em todas as três** (I1-I6) — consistente com o Achado E para
  I1-I4 (F1 nunca ativa `DEGRADED`/`merge-policy`) e, agora sim, confiável
  para I5/I6 (sem o artefato de P-21).
- **Medição de convergência (dado para `W_quiesc`, plano de 24/09 §5.2):**
  medido 2x (execuções 2 e 3), valores quase idênticos entre as duas
  (diferença <1%):
  - Do comando `podman start isn2` até a primeira conexão Hot Rod
    bem-sucedida: **~3,2s** (tempo de inicialização do container/servidor
    Infinispan, não de replicação).
  - Do restart até `sessions` convergir (leitura local de isn2, restrita aos
    segmentos que ele possui, igualar a leitura de referência do cluster,
    por 3 polls consecutivos a cada ~220ms): **4,1s** — ou seja, menos de 1s
    de catch-up de replicação depois que a porta volta a aceitar conexão.
  - Do restart até `counters` "convergir" pela mesma métrica: **~41s** em
    ambas as execuções — **não confiável, ver ressalva abaixo.**
- **Ressalva metodológica importante sobre o número de `counters` (achado
  desta sessão, não do Senior):** inspecionando o log *verbose* da sonda,
  `counters` tem só 4-8 chaves (identidades), sob escrita CONTÍNUA e muito
  frequente (8 *threads* fazendo `incrementFailure`/`resetFailures` sem
  parar — `versao` de cada identidade sobe ~70-120 unidades por *poll* de
  220ms, centenas de incrementos/segundo para só 4-8 chaves). A sonda lê
  `local` (isn2) e depois `referencia` (cliente principal) **sequencialmente
  **, com um intervalo de milissegundos entre as duas chamadas de rede — sob
  essa taxa de escrita, a leitura `referencia` quase sempre captura uma
  `versao` 1-4 unidades **maior** que `local` só pelo tempo decorrido entre
  as duas chamadas, **não** por *replication lag* real de isn2. Isso explica
  tanto o valor alto (três *polls* iguais consecutivos por coincidência
  estatística, não por catch-up genuíno) quanto a notável consistência entre
  as duas execuções (~41s nas duas, plausivelmente um artefato estável da
  combinação taxa-de-escrita/intervalo-de-*poll*, não um sinal físico do
  cluster). **Não è usar os 41s para calibrar `W_quiesc`.** O número
  confiável desta rodada é o de `sessions` (~4,1s, dominado pelo tempo de
  reinicialização do container, com <1s de catch-up de replicação
  mensurável) — mas é um único cenário (identidades hot/pequena cardinalidade
  tendem a se comportar diferente de sessões, mais numerosas e com escrita
  mais distribuída). **Recomendação para calibração real de `W_quiesc`:**
  medir com leitura pareada/atômica (uma função administrativa, ou pausar a
  escrita por um instante antes de comparar) em vez de duas chamadas de rede
  sequenciais sob carga contínua — não implementado nesta rodada, registrado
  para decisão futura do Gestor/Senior.

## 2026-10-03 (rodada 2) — B-22 F2 (`Particionar`/`Mesclar`): primeira bateria TLC completa

> Continua a rodada de F1 (`feature/b22-f1-cair-reviver`, commits `0d997f5`/`f96a08a`).
> Implementação de `Particionar`/`Mesclar`/`particaoAtiva`/`ladoDe`/`Vencedor`/`PodeAgir`
> em `spec/SessionStore.tla` conforme `docs/plano-b22-falhas-explicitas-2026-10-03.md`
> §2 (commit `b9acd56`). Bateria: `tiny`/`small`/`medium`/`full` (`MaxCanal=1`,
> `MaxRelogio=3`, `MaxEventosFalha=1`) + baseline `full`/`MaxEventosFalha=0`, timeout de
> 300s por invariante (`spec/run-tlc-invariantes.sh`).

### P-23 — a maioria dos contraexemplos de I1/I2/I5/I6 "sob F2" é o achado pré-existente (P-16/"violado por construção"), **não** um achado novo; mas um subconjunto de I5/I6 expõe um mecanismo F2 genuíno e mais severo

- **Sintoma aparente:** nas 4 cardinalidades, I1 e I2 aparecem como `VIOLADO` com
  contraexemplos curtos (3-5 estados) logo após a implementação de F2 entrar em
  `Next` — fácil de ler como "primeiro contraexemplo real do catálogo
  operação×falha×invariante ligado à janela de partição" (expectativa do plano,
  `docs/plano-b22-falhas-explicitas-2026-10-03.md` §4.1).
- **Verificação contra os logs brutos (não presumir a leitura do resumo):**
  inspecionando `runs/tlc/inv-{full,tiny,small,medium}/{full,tiny,small,medium}-I1.out`
  e `-I2.out`, **nenhum** dos 8 contraexemplos contém `Particionar`, `Mesclar`, `Cair`
  ou `Reviver` no traço — `particaoAtiva` permanece `FALSE` do início ao fim, `vivo`
  permanece todo `TRUE`. É a mesma propriedade *cross-réplica* sem condição de
  quiescência que já violava I1/I2 "por construção" antes de B-22 existir
  (`docs/validacao-fases1-3-achado-e-2026-10-02.md` §2) — **confirmado** comparando
  com o *checkpoint* F1-only anterior (`git show f96a08a:runs/tlc/inv-full/full-I1.out`
  já violava, depth 6/38 estados distintos, sem nenhuma ação de F1/F2). Adicionar F2 a
  `Next` só deu ao TLC mais ações concorrentes por estado, então a busca em largura
  encontra o mesmo contraexemplo antigo por um caminho mais curto (depth 5 em vez de 6
  em `full`) — **não é um achado novo, é o mesmo P-16/pré-B-22 encontrado mais rápido.**
- **O que é, de fato, novo e genuíno:** em 3 das 8 execuções de I5/I6
  (`full-I6.out`, `tiny-I5.out`, `small-I6.out`), o traço **contém**
  `Particionar`/`Mesclar`. Traço completo de `full-I6` (o mais informativo, 6
  estados): `r1` faz `IncrementFailure(r1,u1)` localmente (`identidades[r1][u1]`:
  ts=1,contador=1) e enfileira `CONTADOR_INC` para `r2`/`r3` (ainda não entregue) →
  `Particionar` escolhe `P1={r1}`/`P2={r2,r3}` (todas vivas, pré-condição ok) →
  `Mesclar` resolve `r1` como perdedor (1 contra 2) e copia o registro de um membro de
  `P2` (ainda em ts=0,contador=0, pois a mensagem de `r1` segue na fila) **para `r1`**
  — a própria escrita local de `r1` é apagada, sem nunca ter sido comparada por `ts`
  (fiel ao desenho, §2.4) → as mensagens `CONTADOR_INC` que já estavam na fila de
  `r2`/`r3` **antes da partição são entregues normalmente depois do merge** (§2.3, "não
  removidas retroativamente") e, pelo gate `m.ts > ts local`, `r2`/`r3` **aplicam** o
  valor original de `r1` (ts=1,contador=1) — estado final (quiescente, `canal` vazio,
  sem partição ativa): `r1` com `contador=0`, `r2`/`r3` com `contador=1`,
  **divergência permanente** (nada mais na fila para reconciliar `r1`). É exatamente a
  janela que a documentação do Infinispan descreve ("escrita no nó isolado pode ser
  perdida"), mas demonstrada aqui de forma mais severa do que o texto sugere: o nó que
  perde sua própria escrita **também não recebe de volta** a versão que seus pares
  aceitaram a partir do próprio *broadcast* dele — o modelo captura uma divergência
  *pós-quiescência* detectável por I6, não só uma perda transitória.
- **Por que isso não invalida nenhum resultado já obtido:** os 5 contraexemplos de
  I5/I6 restantes (`full-I5`, `tiny-I6`, `small-I5`, `medium-I5`, `medium-I6`) **não**
  envolvem F1/F2 — mesmo padrão P-16 de sempre. O TLC relata só o primeiro
  contraexemplo encontrado por invariante; qual mecanismo "ganha a corrida" depende da
  cardinalidade/ordem de exploração, não é determinístico entre mecanismo antigo e
  novo.
- **Ação, nenhuma correção de código aqui** (é comportamento correto, fiel ao
  `PREFERRED_NON_NULL`/`PREFERRED_ALWAYS` real) — registro para corrigir a leitura do
  catálogo operação×falha×invariante antes de reportar ao Cap. 5: a entrada real de
  F2 no catálogo é o mecanismo de `full-I6`/`tiny-I5`/`small-I6` acima (I5/I6, não
  I1/I2), e deve ser descrita com o traço completo, não só "VIOLADO".

### P-24 — `Cair`/`Reviver` não verificam `~particaoAtiva`: a exclusão mútua F1↔F2 é unidirecional, não bidirecional como o texto do plano/comentários descreve

- **Sintoma:** `docs/plano-b22-falhas-explicitas-2026-10-03.md` §2.5 e o comentário em
  `spec/SessionStore.tla` (bloco F2) afirmam que "F1 e F2 são mutuamente exclusivos
  nesta rodada" e que "a interação 'um nó cai durante uma partição' não é modelada".
  Isso só é verdade em uma direção: `Particionar` de fato exige `\A r \in REPLICAS:
  vivo[r] = TRUE` (confirmado, linha correspondente em `SessionStore.tla`), então uma
  partição não pode **começar** enquanto alguém está caído. Mas `Cair(r)` e
  `Reviver(r)` (ambos ainda com a pré-condição original de F1, sem `~particaoAtiva`)
  **não** são bloqueados enquanto `particaoAtiva = TRUE` — nada no código impede
  `Cair(r)` de disparar **durante** uma partição já ativa, contradizendo o texto que
  diz que esse cenário "não é modelado".
- **Por que não invalida os resultados desta rodada:** nenhum dos contraexemplos
  obtidos (I1/I2/I5/I6, nas 4 cardinalidades) contém `Cair`/`Reviver` combinado com
  `Particionar`/`Mesclar` no mesmo traço (verificado via *grep* em todos os `.out`) —
  a lacuna existe no espaço de estados explorado (tornando a explosão de P-25 ainda
  maior do que o necessário), mas não contaminou nenhum veredito já reportado.
- **Não corrigido nesta rodada** (mudança de desenho, não bug pontual de
  implementação — decisão sobre se `Cair`/`Reviver` devem ganhar a pré-condição
  `~particaoAtiva`, ou se o texto do plano/comentário deve ser ajustado para descrever
  o comportamento real, **pertence ao Senior/Gestor**, não ao Worker unilateralmente).

### P-25 — `TypeOK`/I3/I4 deixam de fechar exaustivo em `tiny`/`small`/`medium` sob F2 (antes fechavam em 0-18s); `full`/`MaxEventosFalha=0` confirma que a explosão é dominada por F2, não por F1

- **Sintoma:** no *checkpoint* F1-only anterior (commit `f96a08a`), `TypeOK`/I3/I4
  fechavam exaustivo e rápido em `tiny` (25.517 estados distintos, 0s), `small`
  (444.826 estados, 4-5s) e `medium` (1.125.864 estados, 16-18s) — só `full` já era
  inconclusivo (padrão histórico, documentado antes de B-22). Com F2 em `Next`, **as
  3 cardinalidades menores também passam a ser inconclusivas** dentro do *timebox* de
  300s usado nesta rodada — `tiny`/`TypeOK` chega a 340 milhões de estados gerados/36,6
  milhões de estados distintos em 300s sem terminar; `small` e `medium` na mesma
  ordem de grandeza. Confirma, empiricamente, o risco que o próprio plano já havia
  sinalizado (§4.2: "o produto combinado `vivo × particaoAtiva × ladoDe` ... pode
  multiplicar o tempo de verificação significativamente").
- **Baseline `full`/`MaxEventosFalha=0` (`runs/tlc/inv-full-f0/full-f0-TypeOK.out`),
  pedido pelo Gestor para isolar a contribuição de F1:** também inconclusivo em 300s
  (87,7 milhões de estados gerados/4,8 milhões distintos) — na **mesma ordem de
  grandeza** do `full`/`MaxEventosFalha=1` (81,4 milhões gerados/5,6 milhões
  distintos). Como `MaxEventosFalha` só limita `quedas` (eventos `Cair`), não
  `Particionar`/`Mesclar` (decisão de escopo documentada em `MCSession.tla`), este
  baseline não desliga F2 — mas o fato de zerar a contribuição de F1 não reduzir a
  ordem de grandeza da explosão **indica que a explosão é dominada por F2
  (`particaoAtiva`/`ladoDe`/o não-determinismo de `Particionar` sobre `SUBSET
  REPLICAS`), não pela dimensão de F1.**
- **Não é regressão de bug, é característica do espaço de estados** — nenhuma ação
  corretiva de código aqui; registrado para o Senior/Gestor decidirem se vale a pena
  (a) aumentar o *timebox* além de 300s para `tiny`/`small`/`medium`, (b) reduzir mais
  alguma constante (`MaxCanal`/`MaxRelogio`), ou (c) aceitar `TypeOK`/I3/I4
  inconclusivos em todas as cardinalidades como limitação documentada desta rodada de
  F2 (mesmo padrão já aceito para `full` desde antes de B-22).

## 2026-10-03 (rodada 3) — B-22 F2: validação do Senior + correção de P-24 + experimento de P-25

> Continua a rodada 2 acima. O Senior validou o trabalho (`docs/validacao-b22-f2-
> 2026-10-03.md`, commit `81d2071`, branch `docs/validacao-b22-f2`): confirmou a
> recaracterização de P-23 lendo os 16 traços brutos, e refinou o mecanismo exato do
> achado de I5/I6 (ver nota abaixo). O Gestor aprovou as duas recomendações do Senior:
> corrigir P-24 no código, e testar a hipótese de P-25 com um experimento concreto
> antes de aceitar qualquer limitação.

### P-23 (precisão do Senior) — o mecanismo exato de `full-I6` é `Mesclar` escolher `rf` não-convergido, não "mensagem entregue depois do merge" em geral

Reconstruindo os 6 estados do traço de `full-I6` valor a valor (não só a lista de
ações), o Senior identificou que `EntregaMensagem(r2)` (Estado 4) acontece **antes**
de `Mesclar` (Estado 5) — nesse ponto `r2` **já estava correto**. O fator decisivo da
violação não é "a entrega sempre acontece depois do merge" — é que `Mesclar` escolheu
`rf = r3` (membro do lado vencedor que **ainda não tinha convergido** internamente,
sua própria mensagem ainda na fila) em vez de `rf = r2` (que já tinha o valor certo).
Se tivesse escolhido `rf = r2`, não haveria violação por este caminho. É a resposta
empírica à "pergunta aberta" que o plano original já previa (§2.4). Comentário
correspondente adicionado em `Mesclar` (`spec/SessionStore.tla`, commit `b7d8e0a`).

### P-24 (resolução) — `Cair` agora exige `~particaoAtiva`

- **Correção:** adicionada a pré-condição `/\ ~particaoAtiva` em `Cair(r)`
  (`spec/SessionStore.tla`, commit `96fcf3c`) — mesmo padrão que `Particionar` já usava
  na direção oposta (`\A r: vivo[r] = TRUE`). `Reviver` não precisou da mesma correção
  (reverter um `Cair` já ocorrido não envolve leitura nova de `rf`, não cria a mesma
  ambiguidade que motivou a correção).
- **Decisão (Senior, aprovada pelo Gestor): corrigir o código, não o texto.** A
  interação "um nó cai durante uma partição" nunca tinha sido analisada (`Mesclar`
  poderia copiar de um `rf` morto sem que isso fosse uma decisão deliberada); custo da
  correção mínimo (uma linha); declarar "modelado" sem essa análise protegeria a lacuna
  em vez de corrigi-la.
- **Confirmado, antes e depois da correção:** nenhum dos 16 traços de I1/I2/I5/I6 já
  obtidos (rodada 2) combinava `Cair`/`Reviver` com `Particionar`/`Mesclar` — nenhum
  veredito já reportado fica invalidado.

### P-25 (resultado do experimento) — `particoes`/`LimiteEventosFalha` estendida recupera fechamento exaustivo em `tiny`, mas **não** em `small`/`medium` dentro de 300s

- **Experimento:** nova variável `particoes: Nat` (incrementada em `Particionar`,
  nunca decrementada) e `LimiteEventosFalha` estendida para
  `quedas + particoes =< MaxEventosFalha` em `MCSession.tla`
  (`spec/SessionStore.tla`/`spec/MCSession.tla`, commit `b7d8e0a`) — exatamente a
  fórmula que o plano original já antecipava (§4.2). Re-rodado `tiny`/`small`/`medium`
  (`MaxCanal=1, MaxRelogio=3, MaxEventosFalha=1`, timeout 300s/invariante).
- **Resultado real, não presumido — misto, não confirma nem refuta por completo:**
  - **`tiny`: RECUPERA fechamento exaustivo rápido** — `TypeOK`/I3/I4 fecham em
    570.089 estados distintos (11.243.221 gerados), 6-7s cada, "0 estados na fila"
    (antes da correção: inconclusivo em 300s, 340+ milhões gerados/36,6 milhões
    distintos). Maior que o *baseline* F1-only pré-F2 (25.517 estados, 0s), mas
    plenamente tratável.
  - **`small`/`medium`: AJUDA mas NÃO recupera fechamento em 300s.** Comparando o
    estado da busca exatamente no corte de 300s, antes vs. depois da correção:
    `small`/`TypeOK` antes = 241.672.302 gerados/22.250.191 distintos/13.327.354 na
    fila; depois = 323.039.430 gerados/**14.634.296 distintos**/**5.759.450 na
    fila** — ~34% menos estados distintos encontrados e ~57% menos na fila no mesmo
    tempo, mas ainda inconclusivo. `medium`/`TypeOK` antes = 173.918.677
    gerados/13.894.296 distintos/9.270.857 na fila; depois = 214.433.349
    gerados/**10.467.191 distintos**/**5.831.269 na fila** — mesma redução
    proporcional (~25% menos distintos, ~37% menos na fila), ainda inconclusivo.
  - **Leitura honesta:** a hipótese do Senior (ciclos repetidos de
    `Particionar`→`Mesclar` são causa dominante) é **parcialmente confirmada** —
    reduz substancialmente o espaço alcançável (evidenciado pela fila menor e menos
    estados distintos no mesmo tempo), mas para `small`/`medium` o espaço restante
    (`MaxCanal=1, MaxRelogio=3`, mais o não-determinismo de escolher `P1` e `rf` já
    sem repetição) ainda é grande demais para fechar em 300s. Não é dado suficiente
    para decidir entre "aumentar o *timebox*" e "reduzir mais alguma constante" —
    fica para o Senior/Gestor decidirem com este dado real em mãos.
  - I1/I2/I5/I6 continuam `VIOLADO` nas 3 cardinalidades, sem mudança de veredito
    (contraexemplos rasos, não dependem de busca exaustiva).
- **JUnit:** 58/58 verde, não afetado (confirmado de novo após as duas correções).

## 2026-10-03 (rodada 4) — B-22 F2: teste de 600s em `small`/`medium`, encerramento da rodada

> O Senior, lendo os `.out` reais, confirmou de forma independente os números de P-24/
> P-25 (rodada 3) sem ressalva, e notou que a fila (*states left on queue*) reduz
> sistematicamente mais que os estados distintos em `small`/`medium` — consistente com
> `particoes` eliminando especificamente os ramos de busca mais profundos (vários
> ciclos `Particionar`→`Mesclar` em sequência), não só reduzindo o tamanho geral da
> busca. O Gestor rejeitou reduzir `MaxCanal`/`MaxRelogio` (sacrificaria a fidelidade
> que `tiny` já demonstra não precisar sacrificar) e, observando que a taxa de
> descoberta de estados novos por minuto em `small` caía monotonicamente nos 4
> intervalos registrados até então, pediu um teste final com `TIMEOUT_SECS=600`
> (dobro de 300s) antes de decidir se B-22 fecha nesta rodada.

### P-25 (fechamento) — `TypeOK`/I3/I4 continuam inconclusivos em `small`/`medium` mesmo a 600s; sinal de convergência não se confirma ao olhar a série completa

- **Resultado real, sem presumir:** com `TIMEOUT_SECS=600`, `TypeOK`/I3/I4 de `small` e
  `medium` **continuam inconclusivos** — nenhum fecha exaustivo dentro do dobro do
  tempo. `tiny` não foi re-testado (já fechava em 6-7s desde a rodada 3, não havia
  razão para re-rodar).
- **Comparando 300s (rodada 3, commit `32af7f8`) com 600s (esta rodada) no mesmo
  invariante — crescimento aproximadamente LINEAR com o tempo, não desacelerando:**

  | Invariante | Estados distintos a 300s | Estados distintos a 600s | Razão | Fila a 300s | Fila a 600s | Razão |
  |---|---|---|---|---|---|---|
  | small/TypeOK | 14.634.296 | 28.836.454 | 1,97× | 5.759.450 | 9.510.590 | 1,65× |
  | small/I3 | 15.258.857 | 30.474.782 | 2,00× | 6.110.688 | 10.142.480 | 1,66× |
  | small/I4 | 15.100.624 | 31.134.111 | 2,06× | 6.322.224 | 10.469.154 | 1,66× |
  | medium/TypeOK | 10.467.191 | 21.056.435 | 2,01× | 5.831.269 | 10.589.528 | 1,82× |
  | medium/I3 | 11.489.140 | 23.710.038 | 2,06× | 6.779.509 | 12.949.002 | 1,91× |
  | medium/I4 | 11.079.515 | 23.446.569 | 2,12× | 6.312.927 | 12.305.697 | 1,95× |
- **Olhando a série completa de `Progress` dentro desta própria execução de 600s (10
  pontos, não só os 4 citados na decisão do Gestor), a fila (*states left on queue*)
  **cresce monotonicamente do início ao fim, sem nenhum sinal de platô**, tanto em
  `small`/TypeOK (147K → 2,28M → 3,74M → 4,95M → 5,89M → 6,82M → 7,30M → 7,68M → 8,71M
  → 9,51M) quanto em `medium`/TypeOK (111K → 1,79M → 3,21M → 4,71M → 6,04M → 7,01M →
  7,75M → 8,67M → 9,76M → 10,59M). A taxa de descoberta (`ds/min`) de `small`/TypeOK,
  olhando os 10 intervalos completos (não só os 4 da decisão do Gestor), não é
  monotonicamente decrescente — oscila (4,32M → 3,66M → 3,34M → 3,16M → 3,21M → 2,09M
  → 2,68M → 3,26M → 2,88M): desacelera no começo, mas depois oscila em torno de
  2,5-3,5M/min sem tendência clara de queda contínua até o corte. **Correção honesta à
  leitura inicial do Gestor:** com a janela maior, o sinal de "convergindo" não se
  confirma com a mesma força — nem se refuta (o crescimento é levemente sublinear em
  `small`, não claramente linear nem claramente convergente). **Dado real, não
  interpretação forçada em nenhuma direção.**
- **Decisão (regra pré-acordada com o Gestor antes deste teste, válida para qualquer
  resultado): isso FECHA B-22 nesta rodada.** `TypeOK`/I3/I4 não fechar exaustivo em
  `small`/`medium` nunca foi critério de aceite isolado do plano original
  (`docs/plano-b22-falhas-explicitas-2026-10-03.md` §5) — é limitação documentada,
  mesmo padrão já aceito para `full` desde antes de B-22. Os critérios de aceite reais
  (F1 mínimo testado não presumido, F2 com `Particionar`/`Mesclar` fiéis ao Achado E,
  exclusão mútua F1/F2 bidirecional corrigida — P-24 —, catálogo operação×falha×
  invariante atualizado com o achado real de I5/I6) estão todos satisfeitos.
- **B-22 ENCERRADO nesta rodada.** Trabalho futuro (não aprovado aqui, decisão do
  Gestor): combinação F1+F2 no mesmo comportamento, `scripts/inject-partition.sh`
  (B-29), ou aumentar ainda mais o *timebox*/reduzir cardinalidade de `small`/`medium`
  se o catálogo precisar de TypeOK/I3/I4 fechados nessas cardinalidades no futuro.
- **JUnit:** 58/58 verde, confirmado uma última vez nesta rodada.

## 2026-10-15 — T-A: auditoria do B-32 (`test/b32-auditoria-controle`, base `48d1571`)

> Auditoria dos vereditos de `docs/relatorio-b32-tlc-propriedades-2026-10-09.md` contra os `.out` brutos
> (protocolo de autenticidade, `docs/plano-fechamento-etapa-formal-2026-10-15.md` §4.1). Relatório:
> `docs/relatorio-b32-auditoria-2026-10-15.md`. `spec/SessionStore.tla` e `spec/MCSession.tla` não foram alterados.

### P-26 — `EntregaMensagem` não atualiza `relogio`: no modelo livre, `Logout` aceito e perdido sem falha (`I1P`, 4 estados) e ressurreição por reordenação (`I1F`, 7 estados)

- **Contexto.** Decisão de modelagem D-4 (`spec/SessionStore.tla`, comentário de `EntregaMensagem`: "`relogio` nunca muda em
  `EntregaMensagem`, sem *merge-on-receive*"). Cada ação local calcula `novoTS = relogio[r] + 1` e só escreve se `novoTS` superar o
  `ts` do registro local. Uma réplica que recebeu um registro com `ts` maior continua com `relogio` baixo, e sua próxima escrita
  local sobre aquele registro é rejeitada pelo gate, embora a mensagem correspondente seja transmitida às outras réplicas.
- **Evidência bruta (todas reproduzidas com 1 worker, com e sem `SYMMETRY`, mesmos tamanhos de traço).**
  - `I1P` violada sem falha em 4 estados: `Login(r1,s1)` (ts 1), `EntregaMensagem` leva o registro a r2 (`relogio[r2]` fica 0),
    `Logout(r2,s1)` calcula `novoTS = 1`, não supera o `ts` local (1): a escrita local é rejeitada e a mensagem emitida (ts 1) é
    descartada por r1 e r3. Arquivos: `runs/tlc/prop-livre-sem-w1/r3-I1P.out` (com simetria) e
    `runs/tlc/audit-b32/ctrl-nosym-livre-sem/r3-I1P.out` (sem simetria); mesmo traço em `queda` e `particao`.
  - `I1F` violada sem falha em 7 estados (modelo livre): `Login(s1)`, `Login(s2)`, entrega, `Logout` rejeitado localmente,
    r3 recebe o `Logout` antes do `Login` e este o ressuscita. `runs/tlc/prop-livre-sem-w1/r3-I1F.out` e
    `runs/tlc/audit-b32/ctrl-nosym-livre-sem/r3-I1F.out`.
  - Classificação mecânica de todos os traços de violação: `runs/tlc/audit-b32/tracos/classificacao-d4.tsv` (coluna `depende_D4`).
    Dos 77 traços de `prop-*`, 18 têm a assinatura (passo local que incrementa `relogio` e não altera nenhum registro); todos
    pertencem ao modelo **livre** (`I1P`, `I1F`, `I4F` em `sem`/`queda`, mais traços de `-simulate`). **Nenhum** traço do cenário
    **semeado** tem a assinatura.
- **Classificação.** **Defeito de modelagem (decisão D-4), não do sistema.** No código real, `SessionState.invalidar` usa
  `versao + 1` do registro lido (`workload/.../SessionState.java`), então a versão do logout sempre supera a do registro lido.
- **Consequência para as conclusões.** Os vereditos do modelo **livre** (`prop-livre-*`) que dependem do artefato (`I1P`, `I1F`,
  `I4F` em `sem`/`queda`) **não servem como evidência do sistema** e entram no catálogo como `ARTEFATO-DO-MODELO`. Os vereditos
  do cenário **semeado** são independentes do artefato nos traços de violação (A.6), mas a ausência de violação do semeado em
  `queda`/`partição` não exclui que o artefato apareça além das cotas verificadas.
- **Status:** `ABERTO-ACEITO` (sem alterar `SessionStore.tla`). Pergunta ao Gestor (Q-5): corrigir em variante do modelo
  (`relogio' = max(relogio, ts recebido)` em `EntregaMensagem`) e reverificar, ou manter o artefato documentado?

### P-27 — `I2Fb` é violada por construção (sem falha): auto-bloqueio por limite sem cascata (comportamento do código) e cascata `sessoesAfetadas` calculada na origem (artefato do modelo)

- **Contexto.** B-32 previu `I2Fb` (`BLOCKED` na réplica implica nenhuma sessão `VALID` da identidade na mesma réplica) preservada
  nas três colunas de falha; o TLC a violou em todas. Decisão do Gestor (D1, 2026-10-15): aceitar como "violada por construção";
  `I2Fa` (escopo `Mesclar`) é a propriedade que isola a partição.
- **Mecanismo (a), `comportamento do código`.** Auto-bloqueio por limite: `IncrementFailure` leva `contador >= LIMITE` e a
  identidade a `BLOCKED` **sem invalidar** as sessões `VALID` da mesma réplica. Traço de 3 estados (`LIMITE=2`):
  `IncrementFailure`, `IncrementFailure`. O código real faz o mesmo: `SessionOps.incrementFailure` chama
  `IdentityState.incrementar(LIMITE_TENTATIVAS, ...)` e grava só a identidade (`workload/.../SessionOps.java`, linhas 256 a 276);
  apenas `block()` varre `sessions` (linhas 306 a 325; conferido nesta auditoria).
  Evidência: `runs/tlc/prop-{sem,queda,particao}-w1/r3-I2Fb.out` (3 estados) e
  `runs/tlc/audit-b32/ctrl-nosym-{sem,queda,particao}/r3-I2Fb.out` (sem simetria, 3 estados).
- **Mecanismo (b), `artefato do modelo`.** Com `LIMITE=6` o auto-bloqueio é inalcançável e a violação cai para 4 estados:
  `Logout(r1,s1)`; `Block(r1,u1)` (a cascata `sessoesAfetadas` calculada na origem contém só `s2`, pois `s1` já estava `INVALID` em
  r1); entrega do `ID_BLOQUEIA` a r2, que ainda vê `s1` `VALID`: r2 fica com identidade `BLOCKED` e `s1` `VALID`. Efeito do canal
  assíncrono e do modelo de cópias locais; o sistema real tem um único registro por chave num cache distribuído e provavelmente
  não o reproduz do mesmo modo (leitura, não evidência). Evidência: `runs/tlc/prop-{sem,queda,particao}-lim6-w1/r3-I2Fb.out` e
  `runs/tlc/audit-b32/ctrl-nosym-lim6-{sem,queda,particao}/r3-I2Fb.out`.
- **Consequências.** (1) `I2Fb` **não discrimina** a coluna de falha; status `VIOLADA-POR-CONSTRUCAO` nas três colunas.
  (2) O mecanismo (a) é uma **propriedade do sistema real**: uma varredura global de I2 no auditor real acusará violação em toda
  execução com bloqueio por limite, com ou sem falha; B-33 precisa separar bloqueio por limite de bloqueio administrativo.
  (3) Pergunta aberta Q-3: isso é intencional no domínio?
- **Status:** `ACEITO` (D1); registrado no catálogo `docs/catalogo-operacao-falha-invariante.md`.

## 2026-11-05 — W-2a (B-44.2), atuador REST do *cluster*

> Numeração: P-29..P-35 estão na branch `docs/b44-1-reconfiguracao-infinispan`; estas entradas começam em P-36.

### P-36 — a retirada de `AC_1`/`AC_3` quebra asserções fora da lista D-C autorizada

- **Sintoma:** o item 1 do W-2a manda retirar `AC_1` e `AC_3`. A lista autorizada (D-C) cobre
  `ExecutorDeAcoesTest.java:31-33`, `:217`, `:413-420` e `WorkloadMainCargaControleTest.java:132`, mas os rótulos
  `"AC-1"`/`"AC-3"` também estão nas asserções de texto de `ExecutorDeAcoesTest` (linhas 95, 114, 126, 168, 189, 204,
  228, 251, 275, 295, 323, 364, 399), de `LacoDeControleTest` (197-198, 294, 491) e de `ControleArquivosTest`
  (138 `"AC-1"`, 141 `"DEGRADED_MODE"`). Trocar só as constantes das linhas 31-33 muda o rótulo e reprova essas
  asserções; e os testes LIFO precisam de três ações reversíveis distintas, que com o braço B só existem como AC-2
  com caches diferentes.
- **Contorno aplicado:** `AC_1` e `AC_3` continuam no enum, `@Deprecated`, reversíveis e sem regra de alvo; nenhum
  atuador nem a política os usa (`AtuadorRestInfinispan` devolve `FALHOU tipo-fora-do-braco-b`). Feita só a edição
  autorizada que não depende do rótulo (`WorkloadMainCargaControleTest:132`, AC_1 → AC_2 global).
- **Status:** `RESOLVIDO` (05/11). D-C ampliada pelo coordenador; rótulos trocados em `db98466` (AC1/AC2/AC3 =
  AC-2 em caches distintos e o resumo com tipo/primeiro cache, para a ordem LIFO continuar distinguível); `AC_1` e
  `AC_3` retirados em `4f74c26`.

### P-37 — campo `reversao` só nas linhas de ação não reversível

- **Sintoma:** o contrato JSONL v1 diz "ausência de valor é `null`, nunca campo omitido", mas
  `ControleArquivosTest.formatoAcao` (133-135) fixa a lista de campos da linha `acao`. Gravar `reversao` em todas as
  linhas reprova esse teste.
- **Contorno aplicado:** o campo vai só nas linhas de AC-5 (`"orquestrador"`), no fim da linha.
- **Status:** `RESOLVIDO` (05/11, `18ab8b9`). `reversao` em toda linha `acao` (`"orquestrador"` ou `null`) e
  esquema `decisoes-controlador/1.2`; testes em `db98466`.

### P-38 — o atuador real ainda não está ligado ao `--controller active`

- **Sintoma:** `LigacaoControlador.pecasPadrao` monta o atuador nulo e a política `nenhuma`. Ligar o
  `AtuadorRestInfinispan` e a `PoliticaPorAssinatura` faz o `LigacaoControlador` usar tipos de `controle` fora da
  lista dele no `ArquiteturaSemAcaoNoClienteTest` (a D-C autoriza um par a mais, o do atuador, não a mudança da lista
  do `LigacaoControlador`).
- **Falta decidir:** de onde vêm `k`, `tRebNs` (60 s) e `cotaAc5` (1): arquivo próprio da política, ou campos novos
  no `controlador-*.json` (muda `ParametrosControlador.CAMPOS`, os arquivos de parâmetros e os testes dele); e o
  fornecedor de "nós que responderam na última janela" (última amostra da sonda). Sem isso o item 9 (integração com
  `--controller active`) não roda.
- **Status:** `RESOLVIDO` (05/11, `2bf6cff`). Bloco opcional `"politica"` no `controlador-*.json` (com a origem de
  cada parâmetro), `LigacaoControlador.pecas` liga `PoliticaPorAssinatura` e `AtuadorPorTipo.bracoB(AtuadorRestInfinispan)`,
  e os nós que responderam vêm de `SondaTopologia.respondidosNaUltimaAmostra`. Ponta a ponta com servidor falso em
  `LigacaoControladorAtuadorTest`. A integração I-1..I-5 no *cluster* local espera uma janela H2.

### P-39 (W-4; número a conferir na integração) — ligar a sonda de defasagem muda dois testes existentes

- **Sintoma:** o adaptador `CanalCanarioHotRod` (session) implementa `controle.CanalCanario`, e o
  `LigacaoControlador` passa a usar `CanalCanario`, `ParametrosDefasagem` e `SondaDefasagem`: o R-C1
  (`ArquiteturaSemAcaoNoClienteTest.PERMITIDOS`) reprova os dois sem um par a mais. O custo da sonda em colunas
  próprias do `observaveis` (W-4, item 3) muda o esquema para `observaveis-controlador/1.2`, e
  `LacoDeControleTest.tDecisaoNs` (linha 426) compara o texto `1.1`.
- **Proposta:** acrescentar os pares (nenhuma asserção retirada) e trocar o literal para `1.2`. As duas edições estão
  no *working tree* da `wt-w4`, **não commitadas**, à espera da autorização do Gestor (como a D-C do W-2a).
- **Status:** `RESOLVIDO` (05/11, `bf59e6e`). Autorizado pelo coordenador em nome do Gestor; nenhuma asserção retirada.

### P-40 (W-4; número a conferir na integração) — risco: suspeito ambíguo quando as chaves da maioria têm dono primário no nó cortado

- **Hipótese (não medida):** sob corte do `isn3`, o par `isn1 → isn2` só falha se o dono primário de `lag:<isn1>` for
  o `isn3` (a escrita não chega a nenhuma réplica, e a leitura pelo `isn2` busca no primário inacessível). Se as duas
  chaves da maioria tiverem o primário no `isn3`, os seis pares ficam no teto, os três nós empatam, o `noSuspeito` sai
  `null` e o P1 não dispara naquela execução. Com 64 segmentos e dono primário mais ou menos uniforme entre 3 nós, a
  chance por execução seria da ordem de (1/3)² ≈ 11 %; as chaves são fixas por execução.
- **Efeito:** falha segura (sem AC-5, CONSERVADOR sem ação), mas reduz a sensibilidade do P1.
- **Medir:** no *smoke* local do W-4, registrar os pares no teto por rodada (linha `defasagem`) e onde ficam as
  chaves `lag:*`, por `GET /rest/v2/caches/{cache}/{chave}?action=distribution` (REST 15.0 §2.1.18,
  `leituras/CAND-091`), conferindo o `Key-Content-Type` (as chaves vão pelo `JavaSerializationMarshaller` do
  cliente, não como texto). Se a
  ambiguidade aparecer, as opções são duas chaves por escritor (custo dobra) ou escolher chaves com primário no
  próprio escritor (exige topologia no cliente). Decisão do Senior/Gestor.
- **Status:** `MITIGADO NO CÓDIGO, A CONFIRMAR NO SMOKE` (05/11, `0db755f`). Decisão do coordenador: escolher, para cada
  nó X, uma chave cujo dono primário seja X. Na abertura, `CanalCanarioHotRod` abre um cliente *topology-aware*
  (`S3Clientes.configuracaoTopology`), faz uma operação para receber o hash consistente do cache canario e testa as
  candidatas `lag:<X>`, `lag:<X>#1`, ... até `ChannelFactory.getHashAwareServer` (o mesmo cálculo com que o cliente
  roteia a operação, sobre os bytes da chave do `DataFormat`) devolver X; limite de 10 000 candidatas
  ((63/64)^10000 < 1e-60 para um nó dono primário de ao menos 1 dos 64 segmentos). O cliente é fechado logo depois:
  nada de custo durante a execução, só Hot Rod em memória (P-5). A chave, o primário e o motivo vão a `defasagem_chaves` no `fim`.
- **Limites, a declarar:** (1) `ChannelFactory` é do pacote `impl` do cliente Hot Rod 15.0.0 (métodos públicos,
  conferidos nas *flags* do `.class`, mas fora da API estável); (2) a escolha vale para a topologia da abertura: cada
  execução começa com o *cluster* recriado e o corte parte dela; uma mudança de topologia durante a execução (F1,
  fusão) pode tirar o primário do nó, o que só importaria para uma segunda falha, fora do desenho; (3) supõe que a
  leitura de um nó que não é dono vai primeiro ao primário (leitura escalonada do Infinispan), a conferir; (4) sem
  topologia, nó fora da topologia anunciada (`--servers` com nome e topologia com IP) ou falha, a chave simples volta,
  com o motivo registrado; desde a V-W4 R1 isso faz a abertura falhar, em vez de devolver o risco original.
- **V-W4 (`ff7ab3c`), decisões do coordenador em nome do Gestor:** R1, o recuo para a chave simples é fatal na abertura
  em toda execução com o bloco `defasagem` (`exigirPrimario`, `68691ae`); o `ChannelFactory` fica como limitação
  declarada (cliente fixo em 15.0.0, uso só na abertura, falha nunca silenciosa, prova REST obrigatória no *smoke*,
  com os bytes da chave Hot Rod em hexadecimal e `contains_key = true`, R2); R3, risco novo só em SYNC (a chave de X
  com cópia no nó cortado pode deixar o valor invisível em X até a confirmação), decidido depois do *smoke* SYNC, com
  preferência por declarar a limitação na célula SYNC F2, que é de referência.
- **Conferir no smoke:** `defasagem_chaves` com `primario = true` nos três nós; com o `isn3` cortado, os pares
  `isn1`↔`isn2` vistos e `no_suspeito = isn3` em todas as rodadas antes da mudança de vista; e o dono primário de cada
  chave por `GET /rest/v2/caches/{cache}/{chave}?action=distribution`, como prova independente.
- **Smoke local (05/11, `docs/relatorio-w4-2026-11-05.md`; P-44 no integrador):** em ASYNC a escolha funcionou pelo
  comportamento: com o `isn3` cortado, 9 de 9 rodadas antes da vista com os pares da maioria vistos e o suspeito
  `isn3` no teto, e P1 aos 13,2 s. A prova REST `?action=distribution` não serve de oráculo: com uma chave por *cache*
  novo, o hash do cliente acertou os três conjuntos de donos (entradas em memória por nó) e a REST errou dois. Em SYNC,
  o risco R3 ocorreu: as duas chaves da maioria tinham cópia no `isn3`, a escrita ficou presa (`ISPN000476`,
  `ISPN000299`) e as 8 rodadas antes da vista empataram no teto, sem P1. Decisão pendente: declarar a limitação na
  célula SYNC F2 (preferência do coordenador) ou chave por par ordenado.
- **Decisões do coordenador em nome do Gestor (05/11, depois do smoke):** (1) a prova REST obrigatória sai; valem a
  prova de colocação (uma chave por *cache* novo e entradas por nó, como no diagnóstico D) e a de comportamento sob corte
  em ASYNC (como no A10), registradas no contrato; (2) R3: limitação declarada na célula SYNC F2, que é de referência,
  sem chave por par ordenado; (3) o volume de stderr do cliente Hot Rod sob corte fica como pendência do W-2b, ainda não
  feito.
- **Status:** `MITIGADO EM ASYNC; LIMITAÇÃO DECLARADA EM SYNC F2` (05/11).

## Observação de modelagem (não é defeito)

Com 8 *threads* = 8 identidades fixas (`user-<tid>`), `incrementFailure`
atinge o limite (5) cedo e bloqueia a identidade; logins subsequentes
retornam `BLOCKED` e `validate` retorna `NONE` (sem sessão criada).
Isso é comportamento de domínio correto (códigos de sucesso, não erro) e
produz dados válidos com `taxa_erro ~0`. Para variar mais o estado em
baterias futuras, ampliar o universo de identidades.
