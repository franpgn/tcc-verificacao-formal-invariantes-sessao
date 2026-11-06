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

### P-39 — AC-2 sob falha silenciosa: o pedido global trava até a exclusão, o rebalanceamento oscila e o *cluster* termina com o rebalanceamento desligado depois da fusão

- **Sintoma (I-2, 3/3 repetições, `runs/b44-2/integracao/I2-pausa-async-active-r{1,2,3}/`):** com um nó pausado
  (`podman pause`, 52 s), o `POST /rest/v2/container?action=disable-rebalancing` mandado a um nó que responde não volta
  dentro do prazo (o atuador desiste em 1 s e tenta o seguinte, que também não volta). O AC-2 dá `FALHOU`, o executor
  manda o `enable`, que também dá `FALHOU`, e a política pede de novo na janela seguinte: 8 aplicações e 8 reversões
  falhas por repetição, de +5 s a +44 s. Mesmo sem resposta, os nós vivos aplicam cada pedido: o log mostra 22 a 27
  `ISPN000309` (suspenso) e 21 a 26 `ISPN000308` (ligado) por execução, ou seja, o rebalanceamento oscila a cada 5 s.
  O AC-2 só é aplicado depois da exclusão do nó pausado (vista de 2 em +44,7 a +47,0 s; `APLICADA` em +47,1 a +48,1 s).
  Na volta, a reversão vem 1,8 a 2,9 s depois de a REST do nó responder (motivo `vistas-divergentes`), bem antes da
  fusão (39 a 42 s depois). Mas, depois da fusão (`ISPN000093`, `MergeView`), o `rebalancing_enabled` lido nos três nós
  é **`false`** até o fim da execução, nas 3 repetições (no controle sem ação, `true`). O nó que voltou não recebe
  segmentos (`ISPN100008` só com os dois outros membros; nenhum `ISPN100002` depois da fusão) e a fusão não resolve
  conflitos (N-5). Na r2 apareceram 11 violações I2 nos 10 s seguintes à fusão (descritivo, n = 1).
- **Causa (leitura do log, não do código):** o pedido global é um comando para o *cluster* inteiro; os vivos aplicam e
  esperam a confirmação do nó pausado até a exclusão. Os comandos ficam enfileirados no nó pausado, que os processa ao
  voltar (rajada de `ISPN000309`/`ISPN000308` no instante da retomada) e fica com o último que recebeu antes da
  exclusão, um "suspenso". Na fusão, a recuperação da topologia adota o rebalanceamento desligado
  (`ClusterTopologyManagerImpl` agrega o sinalizador global, B-44.1b R3).
- **Impacto:** o P2 não alcança o que promete na falha silenciosa (o AC-2 entra depois da exclusão e, na r1, depois do
  rebalanceamento de saída) e deixa o *cluster* pior que sem ação: sem rebalanceamento e sem resolução de conflitos
  depois da fusão, sem que o controlador saiba (ele já reverteu e leu `true`). É a guarda da seção 3.2 (nenhuma fusão
  com o AC-2 ativo) violada por outro caminho: o AC-2 já foi revertido, mas o estado dele sobrevive no nó que estava
  pausado. Também confirma o O3 da V-W2a: sem limite de novas tentativas, a falha persistente vira oscilação.
- **Status:** `DECIDIDO` (05/11, D-B do coordenador, pela regra registrada antes do I-2): a P2/AC-2 sai do braço B e
  fica exploratória. Campo obrigatório `p2Habilitada` no bloco `politica` (`false` no braço B de avaliação e na
  calibração, com a origem citada); com `false`, a `PoliticaPorAssinatura` nunca pede o AC-2 (`P0`/`p2-desabilitada`).
  Commits `91c740d` (test) e `e7d4ed5` (feat), branch `fix/w2a-p41-p42`. O problema do AC-2 em si continua aberto para
  quem o usar fora da avaliação (itens (a) a (c) da seção 5 do relatório do B-44.2).

### P-40 — AC-2 depois de uma queda (F1, `podman kill`): o rebalanceamento de saída já aconteceu

- **Sintoma (I-1, `runs/b44-2/integracao/I1-*`):** com `podman kill`, a vista de 2 nós sai em 1,6 a 2,3 s (o FD_SOCK2
  vê a conexão cair), nas quatro execuções. O rebalanceamento de saída dos quatro *caches* de dados vem em 2,0 a 2,3 s
  quando o nó morto não é o coordenador e em 7,3 a 7,5 s quando é (o novo coordenador recupera a topologia antes). O AC-2 chega em +9,1 s (P2 na janela seguinte à da entrada em CONSERVADOR). Na
  volta (+56 s), o braço com ação adia o rebalanceamento de entrada até a reversão (+64,0 a +64,1 s, 4,9 a 5,2 s depois
  de a REST responder); o controle rebalanceia na hora (+58,6 a +58,8 s). Nos dois braços há dois rebalanceamentos por
  *cache* (saída e volta), sem violação e sem falha de ação, e o `rebalancing_enabled` volta a `true`.
- **Impacto:** na queda detectada pelo FD_SOCK2, o AC-2 não economiza nenhuma rodada; só atrasa em ~5 s a devolução de
  segmentos ao nó que voltou. A troca "uma rodada em vez de duas" da seção 3.2 não ocorre localmente. Com o coordenador
  derrubado, o AC-2 é pedido ao novo coordenador, depois da troca, e o sinalizador global vale nos vivos (o P-34 não
  reaparece, mas também não é exercitado: a política nunca tem o AC-2 ativo antes da queda).
- **Status:** `DECIDIDO` (05/11) junto com o P-39 (D-B): a P2/AC-2 fica fora do braço B.

### P-41 — com um nó parado até o fim, a leitura final por réplica da carga S3 trava sem prazo

- **Sintoma (I-3, `runs/b44-2/integracao/I3-corte-async-com-perdida/jstack-preso.txt`):** depois do AC-5, a carga
  imprime o resumo S3 e fica parada em `WorkloadMain.materializar` (iteração de `entrySet` por um cliente BASIC fixado na
  porta do nó parado, `abrirLeitoresMultiReplica`, chamado pela `rodadaUnica` do auditor), sem prazo. Os CSV de eventos
  e de violações só são gravados depois, e a execução não termina.
- **Impacto:** toda execução do braço B com AC-5 (o nó fica parado até o orquestrador recriar o *cluster*) travaria no
  fim. No I-3/I-4 o *harness* religa o nó depois da janela medida, só para a carga terminar (declarado nas fases).
- **Contorno a decidir:** prazo na leitura final por réplica e réplica sem resposta registrada como ausente, ou o
  orquestrador religar o nó antes do fim da carga. Tem de entrar antes da calibração.
- **Status:** `RESOLVIDO NO CÓDIGO, A CONFIRMAR NO SMOKE` (05/11, `d5c58ca` test, `b61c803` fix; decisão do
  coordenador). Em S3, a leitura de cada réplica (`LeituraReplicaComPrazo`, ligada por `WorkloadMain.leitorDaReplica`)
  tem prazo sem resposta igual ao `--hotrod-timeout-ms`, o mesmo prazo de leitura da sonda S_lag, contado desde a última
  entrada recebida; a leitura roda numa *thread* daemon e é interrompida no prazo. A réplica sem resposta fica fora da
  comparação de I5/I6 (como a leitura que lança, B-33) e a saída ganha a linha `Leitura por replica:` com o resultado
  de cada uma (`nao-lida:sem-resposta`). Declarado no contrato JSONL, seção "Leitura por réplica no S3". Fora do S3
  nada mudou. **A conferir no *smoke*:** que a interrupção solta o iterador do cliente Hot Rod 15.0 (o `jstack` mostra
  `BlockingFlowableIterable.hasNext`, que cancela a assinatura na interrupção) e que o `close` do `RemoteCacheManager`
  do nó parado, no fim da repetição, não trava (não medido; os testes usam leituras falsas).

### P-42 — AC-5 sob corte: o nó para 13 s depois do pedido, além do prazo de 5 s, e o pedido a um nó pausado vale na retomada

- **Sintoma (I-3, I-5):** sob corte, o 204 vem na hora, o `ISPN080002` (servidor parando) sai 3 s depois, mas a saúde
  do nó isolado continua respondendo até 13,1 a 13,3 s depois do pedido (SYNC e ASYNC) e o processo só sai em 42 a 44 s, quando o
  próprio nó isolado instala a vista de 1 (`ISPN000080` só então). O atuador grava `FALHOU`
  (`alvo-continua-respondendo`), embora a parada aconteça. Sem corte, 3,2 s (I-4) e 3,06 s (tarefa zero). No I-5, um
  pedido de parada mandado a um nó pausado com o desafio Digest guardado (cliente "aquecido") volta por prazo, e o nó
  **para 5 s depois de retomado**; com o cliente frio (o primeiro POST vai sem credencial), o nó volta e não para.
- **Impacto:** `t_parada` sob corte ≈ 13,1 s entra na condição de viabilidade (`k·W + t_parada < 39,8 s`); o prazo do
  AC-5 (`T_cham` + 3 s) é curto para o caso de uso previsto; a linha `acao` do AC-5 sai `FALHOU` com o nó parado (o O8
  da V-W2a acontece). O risco 3 da seção 3.1 existe: a guarda "respondeu na última janela" reduz, mas não elimina, a
  chance de mandar a ordem a um nó que pausa entre a última amostra e o pedido.
- **Status:** `PARCIAL` (05/11, decisões do coordenador). (1) **Prazo do AC-5 = `T_cham` + 15 s = 17 s**, acima da
  parada sob corte medida (13,1 a 13,3 s): `a47dca7` (test) e `3b3056c` (fix). O pior caso do `T_enc` passa de 9 s a
  **21 s** com a P2 habilitada e **19 s** no braço B de avaliação (D-B, sem AC-2 na pilha); o `--dry-run` mostra o
  valor da configuração. **Requisito para o W-6:** `timeout -k <K>` com `K` > 21 s mais a folga (cobre os dois casos);
  o W-6 (`wt-w6`, `f4eb04f`) ainda usa `T_ENC_PIOR_S=9` (`scripts/run-campanha-s3.sh:83`; o S1 de
  `run-campanha-s3.test.sh:495` espera `timeout -k 11s`) e tem de passar a 21 na integração dele.
  (2) **Requisito da calibração:** `kLag · W + t_parada < 39,8 s`; com W = 5 s e `t_parada` = 13,3 s, **`kLag` ≤ 5**
  (contrato, bloco `politica`; `ArquivoCalibracaoTest.kLagViavel` confere o arquivo da calibração). (3) **Aberto:** o
  risco 3 (pedido de parada a um nó pausado, executado na retomada, I-5), para o Gestor.

## 2026-11-05 — W-4 (B-26.3), sonda de defasagem S_lag

> Numeração: na branch `feature/w4-sonda-defasagem` estas entradas eram P-39 e P-40, numeradas em paralelo com o W-2a
> (que tem P-39..P-42). Na integração de 05/11 (`feature/controlador-integracao`) passaram a P-43 e P-44; os commits
> do W-4 anteriores à integração citam os números antigos.

### P-43 (W-4; P-39 na branch do W-4, renumerado na integração) — ligar a sonda de defasagem muda dois testes existentes

- **Sintoma:** o adaptador `CanalCanarioHotRod` (session) implementa `controle.CanalCanario`, e o
  `LigacaoControlador` passa a usar `CanalCanario`, `ParametrosDefasagem` e `SondaDefasagem`: o R-C1
  (`ArquiteturaSemAcaoNoClienteTest.PERMITIDOS`) reprova os dois sem um par a mais. O custo da sonda em colunas
  próprias do `observaveis` (W-4, item 3) muda o esquema para `observaveis-controlador/1.2`, e
  `LacoDeControleTest.tDecisaoNs` (linha 426) compara o texto `1.1`.
- **Proposta:** acrescentar os pares (nenhuma asserção retirada) e trocar o literal para `1.2`. As duas edições estão
  no *working tree* da `wt-w4`, **não commitadas**, à espera da autorização do Gestor (como a D-C do W-2a).
- **Status:** `RESOLVIDO` (05/11, `bf59e6e`). Autorizado pelo coordenador em nome do Gestor; nenhuma asserção retirada.

### P-44 (W-4; P-40 na branch do W-4, renumerado na integração) — risco: suspeito ambíguo quando as chaves da maioria têm dono primário no nó cortado

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

## 2026-11-05 — W-2b (P-5), caminho crítico sem disco e sem processo

Branch `fix/p5-caminho-critico` (base `17f2bd4`). Numeração na branch do W-2b; renumerar na integração se colidir.
Os itens do plano (N-7, N-8 e o stderr sob corte) estão em `docs/implementacao.md` TD-014; aqui ficam os achados que
o W-2b não resolve.

### P-45 (W-2b) — o laço de convergência ainda cria um processo `curl` por rodada

- **Sintoma:** `WorkloadMain.executarConvergencia` usa `HealthRest.consultar` (`ProcessBuilder("curl", ...)`,
  `--max-time 5`, espera de até 10 s) como sonda do *cluster* em cada rodada do laço de convergência (B-33). O
  controlador já fechou nesse ponto, então não é caminho crítico do controlador; mas o tempo de convergência
  (`t_convergencia`) é desfecho medido, e na VM o travamento de disco travou junto a criação de processos (P-5,
  `V-S7r.md:61`): um travamento nessa fase atrasa a rodada e infla o `t_convergencia`.
- **Proposta:** trocar por `ConsultaSaudeRest` (mesmo endpoint, HTTP em memória, Digest), com um prazo próprio com
  origem declarada. Muda o laço de convergência, fora do escopo do W-2b.
- **Status:** `RESOLVIDO` (05/11, `ee9b563` test → `65165ea` fix). Decisão 3 do coordenador em nome do Gestor:
  `HealthRest.consultar(ClienteAdminRest, baseUrl, PRAZO_CONSULTA_NS)`, prazo de 5 s (o `--max-time 5` do curl), falhas
  como `IOException` (rodada inválida, como antes); nenhuma fonte de `main` cria processo (R-ARQ-6).

### P-46 (W-2b) — depois da contenção do `ISPN004007`, o stderr do `shadow` passa a ser o maior

- **Medido (I-3b, `wt-w4/runs/i3b/`, sem execução nova):** reaplicando a regra da `ContencaoLogHotRod` ao stderr bruto,
  os braços ativos vão de 9 887 694 B para 18 553 B e de 12 123 100 B para 28 840 B; o `shadow` vai de 1 498 576 B para
  1 455 311 B. O resto do `shadow` é `ISPN004005` (WARN, uma linha por erro devolvido pelo servidor, sem pilha;
  3 897 linhas, 1 440 008 B), quase todo `AvailabilityException`/`ISPN000306` das leituras da sonda S_lag pelo `isn3`
  em DEGRADED (1 767 linhas para cada uma das chaves `lag:*` do `isn1` e do `isn2`).
- **Efeito:** a escrita no stderr roda nas *threads* de E/S do cliente Hot Rod (as mesmas da sonda S_lag). Sem a
  contenção, os braços ativos escreviam 6,6 e 8,1 vezes o volume do `shadow`; com ela, escrevem 1/78 e 1/50 dele. Os dois braços
  continuam com escrita em disco no caminho do cliente sob corte, em volumes diferentes.
- **Proposta:** a mesma regra para o `ISPN004005`, com chave (código do servidor, por exemplo `ISPN000306`,
  `ISPN000476`), sem nó (a mensagem não traz o endereço). É uma linha na lista de mensagens contidas mais os testes.
- **Status:** `RESOLVIDO` (05/11, `efdb0d5` test → `b30814a` fix). Decisão 2 do coordenador em nome do Gestor:
  mesma regra, chave (nó, código do servidor), linha `Hot Rod ISPN004005:` no stdout. O nó sai `desconhecido`: o
  registro do cliente não traz o endereço do servidor. Medida no *smoke* do W-2b (`docs/testes.md`).

## 2026-11-05 — W-6 (B-46), orquestrador das campanhas S3

Branch `feature/w6-orquestrador-s3` (base `17f2bd4`). Na integração (merge em `feature/controlador-integracao`, 05/11)
os números do W-6 e do W-6b foram deslocados em +2, para depois do P-45 e do P-46 do W-2b: P-45..P-53 da branch do
W-6 são P-47..P-55 aqui. As mensagens de commit da branch ficam com a numeração antiga.

### P-47 (W-6; P-45 na branch do W-6, P-41 antes do rebase sobre `17f2bd4`; renumerado na integração) — o amostrador h4 só aceita identificadores do *rebaseline*

- **Sintoma:** `scripts/amostrador-infra.sh` recusa (exit 2) qualquer `--id` fora de
  `^(piloto|noite1|noite2|reposicoes|ensaio|diag)-<UTC>$`; `vigia-h4.sh` e `analysis/h4.py fase` só aceitam as fases do
  *rebaseline*, e o `h4.py` usa a janela fixa de +44 a +300 s da âncora. Os critérios h4 do C3 e da DT-019 valem para
  as campanhas S3 (requisito 6 do W-6), mas os três arquivos estão na lista `e9c44819…` da adenda da DT-018.
- **Contorno no W-6 (nada dos três foi editado):** `scripts/campanha_s3_h4.py` importa o `h4.py` e troca só a janela
  (lida da linha do programa) e o reinício padrão do F1; o orquestrador faz o papel do vigia (avalia depois de cada
  execução e para pela regra do C3 §4.3) e exige `--h4-id`, aceitando qualquer `<prefixo>-<UTC>`.
- **Pendente (decisão do Senior/Gestor):** com o `amostrador-infra.sh` como está, a coleta do S3 só pode ser iniciada
  com `--id diag-<UTC>`. Alternativa: uma linha a mais no `ID_RE` (`|calibracao|nucleo|extra|reposicoes-s3|validacao`),
  compatível com os testes atuais (o A7 só recusa IDs que continuariam recusados), num commit próprio e validado.
- **Decisão (coordenador, 05/11, sobre a V-W6, ponto 6):** o `amostrador-infra.sh` não muda enquanto o *rebaseline*
  não fechar. Nas campanhas S3 o amostrador é iniciado com `--id diag-<UTC>`, que o `ID_RE` atual aceita, e o
  orquestrador também (`--h4-id`, expressão `<prefixo>-<UTC>`); o pré-registro da calibração registra isso.
- **Status:** `RESOLVIDO POR DECISÃO` (contorno `diag-<UTC>`; nenhum dos três arquivos do h4 foi editado).

### P-48 (W-6; P-46 na branch do W-6, P-42 antes do rebase; renumerado na integração) — risco: a sonda do laço de convergência lê a topologia de um cliente que não opera no S3

- **Observação (código, não medida):** `WorkloadMain.executarConvergencia` usa
  `min(number_of_nodes da REST, servidores na topologia do rcm principal)`. No S3 a carga roda pelos clientes fixados a
  nós (`S3Clientes`); o `rcm` principal só faz o *reset* e a carga inicial, então a topologia dele fica a da abertura
  (três servidores) até alguma operação. Para o religamento (W-6) isso não atrapalha: o número vem da REST. Mas a
  leitura por segmento dos leitores multi-réplica (`segmentosDoServidor`) também usa essa topologia velha, inclusive
  depois de um F1.
- **Conferir no *smoke* local do controlador:** `getCacheTopologyInfo()` do `rcm` antes e depois de um F1 em S3.
- **Relação com o P-41 da integração** (leitura por réplica com prazo, `b61c803`): o P-41 resolve o travamento da
  leitura final no nó parado; este item é sobre qual topologia escolhe os segmentos lidos de cada nó, e continua aberto.
- **Smoke local (06/11, 05:11 a 05:15Z, ponta `1f65a02`, a da calibração; `runs/p48-smoke/`).** Cluster local de três
  nós com o XML sem *caches* e os seis *caches* da ponta; a sonda (`runs/p48-smoke/scripts/SondaP48.java`, *harness*,
  nenhum código do programa mudou) usa o `rcm` principal com a configuração do `WorkloadMain`, faz o *clear* e a carga
  inicial (2000 chaves em `sessions-async` e `counters-async`) e depois nenhuma operação nele, como no S3. F1 =
  `podman kill` do `isn2`, 60 s, `podman start` (o JGroups dá endereço novo ao nó: `isn2-2701` antes, `isn2-20801`
  depois). Resultado (`runs/p48-smoke/execucao/sonda.out`):
  - **A topologia fica velha.** Depois do F1, o `rcm` principal continua com a da abertura (`topology_id` 2135538279,
    43/42/43 segmentos por nó), e um cliente novo vê outra (−1218010633, 44/43/41). Um único `get` pelo principal traz a
    topologia nova (T3 igual a T2). Na calibração o `stdout.log` das execuções F1 tem o mesmo sinal: o cliente só
    registra `ISPN004006` (topologia nova) na abertura (3 linhas em cada uma das 5, nenhuma depois do F1).
  - **As leituras por réplica erram de 13 a 33 % das chaves de cada nó depois do F1.** Com a topologia velha, cada
    nó tem 8 a 13 segmentos a mais e 6 a 14 a menos do que os seus: a leitura dele perde 174 a 441 das ~1300 chaves que possui e
    acrescenta 245 a 399 chaves de segmentos que não são mais dele. O servidor devolve essas chaves (busca no dono
    atual), então, nessa parte, a "réplica" lida não é a cópia local do nó.
  - **Sem reinício a distribuição não muda.** Numa F2 sem reinício (`isn3` isolado por 40 s, fusão às 05:15:02Z), a
    distribuição depois da fusão é a mesma de antes (T5 igual a T2) e as leituras com a topologia velha e com a nova são
    idênticas nos três nós. Vale também para F4 (sem troca de membros). O erro só aparece quando um nó **reinicia**:
    F1 e, na avaliação, o religamento depois do AC-5.
  - **O erro só pode esconder divergência.** O laço conta como divergente a chave lida em duas réplicas com valores
    diferentes (`InvariantAuditor.divergenciasI5/I6`); chave que falta não conta. Uma leitura com segmentos errados ou
    traz a cópia do dono atual (igual à dele) ou deixa a chave de fora, nunca fabrica divergência. A convergência medida
    num F1 é, então, no máximo cedo demais, nunca tarde demais.
- **Efeito na calibração e na regra 7.10.** Nas 27 execuções do X-18, toda rodada válida teve 0 chaves divergentes.
  Δ_conv: F1 196,9 a 197,1 s (5 execuções; 190 s disso são o intervalo entre o `end` do F1 (reinício do `isn2`)
  em +109,5 a +109,7 s, e o fim da carga, e o resto é a primeira rodada, inválida nas 5 por "leitura de 1 réplica
  falhou", mais um período), F2 153,5 a 154,0 s e F4 159,3 a 160,3 s, estes dois sem efeito do P-48. A 7.10 dá 600 s porque o maior Δ_conv (197,1 s) fica abaixo de
  400 s. Para o P-48 mudar essa decisão, uma leitura correta teria de mostrar, em alguma execução F1, divergência por
  mais ~203 s depois da primeira rodada limpa, toda dentro da parte mal lida (13 a 33 % das chaves de cada nó),
  enquanto o resto, lido certo, teve 0 chaves divergentes em três rodadas seguidas nas cinco execuções. **Veredito para a calibração: o teto
  de 600 s da 7.10 não muda.** O maior Δ_conv das F1 é limite inferior, não valor exato.
- **Para a avaliação (antes de qualquer dado do B-40):** o defeito vale para toda célula F1 e para toda execução do
  braço B com AC-5 e religamento (o nó volta com endereço novo), e aí pode encurtar o Δ_conv e esconder divergência só
  num braço. A mitigação da V-W6 §5.4 (uma operação barata pelo `rcm` principal no início de cada rodada do laço, por
  exemplo `containsKey` de uma chave inexistente em cada *cache* lido) basta, pelo smoke: um `get` atualizou a
  topologia. O I-3b `ativo-religar` (Δ_conv 35,3 s, citado na origem do `convergencia_teto_s`) foi medido com o mesmo
  laço de leitura e tem a mesma ressalva. Não alterei código (pedido do coordenador).
- **Observação à parte (não é o P-48):** nas 5 execuções F1 da calibração, e em nenhuma F2/F4, a primeira rodada do
  laço é inválida ("leitura de 1 réplica(s) falhou (DEGRADED?)"), provavelmente a conexão do leitor BASIC com o `isn2`
  derrubada pelo F1. Custa um período (~5 s) no Δ_conv das F1.
- **Status:** `CONFIRMADO` pelo smoke; sem efeito na decisão da 7.10 da calibração; correção pendente antes da avaliação
  (decisão do coordenador).

### P-49 (W-6; P-47 na branch do W-6, P-43 antes do rebase; renumerado na integração) — o `python3` do Windows escreve CRLF e o `\r` entrava nos parâmetros lidos pelo orquestrador

- **Sintoma (suíte do orquestrador no host):** `run-campanha-s3.sh: line 265: 200: arithmetic syntax error`, porque o
  `campanha_s3_conferir.py parametros` lido pelo *bash* do Git devolvia `200\r`.
- **Correção:** toda saída Python que o orquestrador interpreta passa pela função `py`, que tira o `\r` e mantém o exit.
  Na VM (Linux) não há `\r`; a correção só deixa o orquestrador indiferente ao sistema.
- **Status:** `RESOLVIDO` (05/11, branch `feature/w6-orquestrador-s3`).

### P-50 (W-6, só nos testes; P-48 na branch do W-6, P-44 antes do rebase; renumerado na integração) — `grep -q` com `pipefail` dá falso negativo em texto grande

- **Sintoma:** `contem` da suíte nova (`printf … | grep -qF …`) falhou com a linha presente: o `grep -q` sai no
  primeiro acerto, o `printf` de um log grande morre de SIGPIPE e, com `pipefail`, o *pipeline* falha.
- **Correção:** `grep -F … > /dev/null` (lê tudo). O `run-rebaseline.test.sh` tem o mesmo padrão com textos pequenos e
  não foi tocado.
- **Status:** `RESOLVIDO`.

### P-51 (W-6; P-49 na branch do W-6, P-45 antes do rebase; renumerado na integração) — evento do stdout perdido quando o `read -t` vence no meio da linha; carga sem âncora saía ACEITA

- **Sintoma (suíte do orquestrador no host, S4):** a execução F1 não viu a âncora (a linha estava no `stdout.log`),
  não injetou a falha e saiu ACEITA. Duas causas: (1) o laço de eventos lê o FIFO com `read -t 0,2`; quando o prazo
  vence no meio de uma linha, o bash deixa a parte lida na variável, e o laço a descartava; (2) a recusa "sem âncora"
  só existia para o prazo de 120 s, e uma carga que termina antes dele sem âncora passava.
- **Correção:** primeiro, a parte lida no `read -t` vencido ia a um *buffer*; depois do P-52, o laço deixou de usar
  `read -t` com prazo: testa com `read -t 0` (não lê) e lê a linha já disponível, que o leitor escreve inteira, então
  não há leitura parcial. Depois do laço, carga sem âncora é RECUSADA ("sem ancora (a carga terminou antes)"). Caso
  S17 da suíte.
- **Alcance:** no Linux da VM, o leitor escreve cada evento numa única escrita menor que `PIPE_BUF`, então a perda
  seria rara; no Git Bash (emulação de pipe do MSYS) apareceu. A correção vale nos dois.
- **Status:** `RESOLVIDO` (05/11).

### P-52 (W-6; P-50 na branch do W-6, renumerado na integração) — o bash do Git Bash cai com SIGSEGV quando o SIGTERM chega durante `read -t` num FIFO

- **Sintoma (S7, 2 de 3 rodadas completas e na rodada isolada):** o orquestrador recebe SIGTERM com o laço de eventos
  parado em `IFS= read -r -t 0.2 -u <fd>` sobre o FIFO de eventos e sai com 139 ("Segmentation fault" na linha do
  `read`), sem rodar o *trap* (sem a linha FIM no log). Na rodada anterior o S7 tinha passado.
- **Causa provável:** entrega de sinal durante o `read -t` no bash do MSYS (emulação de FIFO e de sinal). O runner do
  rebaseline espera com `sleep & wait` (`dormir`), e o R8 dele, que manda SIGTERM no meio da execução, passa nesse
  ambiente. Não foi reproduzido no Linux (não havia Linux disponível sem subir Podman).
- **Correção:** o laço de eventos espera com `dormir` (o SIGTERM chega no `wait` e o *trap* roda), testa eventos com
  `read -t 0` e lê só a linha disponível. A latência de leitura de um evento continua de até 0,2 s.
- **Status:** `RESOLVIDO` no código (05/11); a confirmar na rodada repetida do S7 e na validação X-17 na VM.

### P-53 (W-6b; P-51 na branch do W-6, renumerado na integração; achado B1 da V-W6) — F2/F4 do braço B recusadas sempre que o AC-5 para o nó da falha

- **Sintoma (leitura do Senior, V-W6 §5.1):** com o AC-5 parando o nó em que a F2 ou a F4 foi injetada, o
  `inject-partition.sh` real sai com 1 (a soma dos contadores pelo `nsenter` com o PID morto falha sob
  `set -euo pipefail`, `:495`) e o `inject-netdegrade.sh` com 6 (contadores zerados ou *ping* sem resposta). Os dois
  códigos estavam em `EXITS_REDE_SEGUEM` como recusa, então toda execução F2/F4 do braço B com AC-5 no nó da falha
  saía RECUSADA, e a reposição tinha o mesmo destino. A suíte não pegava: os dublês saíam sempre com o código pedido.
- **Correção (orquestrador; os injetores não mudam):** com exit 1 ou 6 e o nó-alvo fora de `running` (ou já religado
  pela linha de fim, pelo AC-5 ou pelo O8), `concluir_injecao` guarda o caso como pendente (`injetor_alvo_parado` no
  `execucao.json`). Na pós-checagem, se a linha de fim e a trilha concordam que o AC-5 APLICADA foi nesse nó (e, na F2,
  o `injetor.log` tem `OK: <no> nao conecta a nenhum par`), a execução segue com o aviso "falha efetiva ate a parada do
  no pelo AC-5; contadores do injetor nao lidos"; senão, a recusa de antes, com o que faltou. Dublês fiéis: o de F2
  escreve a linha OK e sai com 1, o de F4 sai com 6, quando o alvo não está `running` no fim da janela. Caso S22.
- **Status:** `RESOLVIDO` (05/11, W-6b); a confirmar no X-17 (1 F2 `shadow` + 1 F2 `active`).

### P-54 (W-6b; P-52 na branch do W-6, renumerado na integração; achado B2 da adenda da V-W6) — uma só referência de p99 para a fase inteira: toda execução SYNC falharia ao abrir

- **Sintoma (leitura do Senior, V-W6 §11):** o orquestrador passava o mesmo `--controller-reference` a todas as
  execuções, e o programa recusa referência de outro perfil (`LigacaoControlador.java:204`). Calibração e núcleo têm
  células SYNC e ASYNC; o pré-voo 4 só rodava o `--dry-run` do programa com `async`, então não via o erro.
- **Correção:** `--referencia-async` e `--referencia-sync` (cada uma com o seu SHA-256) no lugar de `--referencia`
  (que agora sai com 2 e uma mensagem); a referência de cada execução vem do modo da linha da matriz; a de cada perfil
  presente na fila é obrigatória e conferida no pré-voo e no `--dry-run` (`campanha_s3_conferir.py referencia`:
  esquema, cenário S3, perfil); as duas entram no *bundle*; o pré-voo 4 roda o `--dry-run` do programa em cada perfil
  da fase; o `execucao.json` registra o `referencia_sha256` usado. O dublê de `java` da suíte recusa referência de
  outro perfil, como o programa. Caso S24.
- **Status:** `RESOLVIDO` (05/11, W-6b).

### P-55 (W-6b; P-53 na branch do W-6, renumerado na integração) — nó religado pelo O8 não tornava a execução DESCARTADA-INFRA

- **Sintoma (achado ao escrever o S22):** o veredito `DESCARTADA-INFRA` olha os motivos que começam com `infra`, mas
  as mensagens de `E_INFRAS` (nó parado sem AC-5 religado pelo O8, `podman start` que falhou no religamento, alvo do
  AC-5 fora de `--admin`) entravam nos motivos sem esse prefixo. Uma execução com só um desses desvios terminava ACEITA
  com motivos. Nenhum caso da suíte exercitava o O8 sozinho (o arquivo `sinal-alvo` do dublê não era usado).
- **Correção:** os motivos de `E_INFRAS` entram com o prefixo `infra: `. O S22 (nó parado sem AC-5) e o S23 (sem a
  linha de fim) cobrem os dois caminhos. Os nós religados pelo O8 ou pela falta da linha não geram também o motivo de
  `StartedAt` (já têm o seu).
- **Efeito colateral achado na suíte completa (S4 i=1):** com o disparo atrasado no Git Bash, o F1 ainda estava em
  curso quando a linha de fim chegou, e o O8 religou o nó que o `inject-crash.sh` ia religar; com o prefixo, a execução
  virou `DESCARTADA-INFRA`. O O8 agora pula o nó de um F1 em curso (o injetor o religa e grava o `end`). Caso S25. Na
  VM o F1 acaba em +109 s numa carga de 300 s, então o caso só aparece se o injetor travar.
- **Status:** `RESOLVIDO` (05/11, W-6b).

Na integração, P-51..P-53 do W-6b deslocaram como os demais números do W-6 (seção 6 da V-W6, +2): são os P-53..P-55
acima.

## 2026-11-05 — Integração do W-6 em `feature/controlador-integracao`

Merge `49baddc` (W-6 e W-6b sobre a integração `944a36d`, tag `int/2026-11-05-w2b`), com a renumeração acima.

### P-56 (integração; achado I-1 da V-W6) — a `meta` da trilha pode chegar ao disco depois da âncora

- **Sintoma (previsto pela V-W6 §5.2, ainda não observado numa execução):** desde o W-2b a `TrilhaJsonl` grava por
  uma *thread* com fila, e a `meta` de `decisoes-rep-001.jsonl` deixa de estar no disco quando o construtor do laço
  volta. O orquestrador do W-6 lê a `meta` assim que vê a âncora. Com o disco lento (o caso h4), o
  `campanha_s3_conferir.py meta` sairia com 4 (trilha ausente ou primeira linha ilegível), e todo exit diferente de 0
  parava a fase com 6, como se fosse erro de configuração.
- **Correção:** `cb13cd5` (test, vermelho: 4 PASS, 10 FAIL), `70996e4` (test: a contagem do `podman kill` casava
  também a linha do shim de `sudo`) e `fdcf0ae` (fix). Com exit 4, nova leitura a cada 0,2 s até 5 s; lida, a execução
  segue. Ainda 4: carga parada, `DESCARTADA-INFRA` com o motivo "meta da trilha ausente ou ilegivel", e a fase
  segue. Exit 5 e 2 continuam parando a fase com 6. Nenhuma falha é disparada numa carga já parada pelo orquestrador.
- **Teste:** S29 do `scripts/run-campanha-s3.test.sh` (S26 até a integração do W-6c, que usa o S26 para o F3): a `meta` aparece 1 s depois da âncora e só depois de uma
  leitura que saiu com 4 (ACEITA, carga viva); e a `meta` que não chega, com o F1 marcado para +3 s, antes do fim do
  prazo (`DESCARTADA-INFRA`, sem injeção, e a execução seguinte ACEITA).
- **Status:** `RESOLVIDO` (05/11, na integração).

## 2026-11-05 — W-6c (F3 na matriz S3, swap no portão e trilha completa)

Branch `feature/w6c-f3-memoria` (base `6f366ac`, ponta do W-6b). Na integração do W-6c (06/11, merge em
`feature/controlador-integracao` sobre `int/2026-11-05-w6`), os números foram deslocados para depois do P-56 (I-1
da integração): P-54..P-57 da branch do W-6c são P-57..P-60 aqui. As referências aos números do W-6 (P-45..P-53 na
branch) foram trocadas pelos da integração (+2). As mensagens de commit da branch ficam com a numeração antiga.

### P-57 (W-6c; P-54 na branch do W-6c, renumerado na integração) — com o AC-5 parando o nó sob o `netem`, o `inject-jitter.sh` sai com 0 e o `injetor.log` diz "OK"

- **Sintoma (leitura do injetor congelado ao escrever o F3 do S3):** o rascunho do B-40 (seção 4.3) supõe que o AC-5
  que para o `isn2` durante o `netem` "faz o injetor falhar na remoção". Não faz: o `tc qdisc del` final é
  `|| true`, e a conferência `nstc qdisc show | grep -q netem`, com o netns sumido, cai no ramo `else` e escreve
  `OK: F3 (jitter) injetada por <n>s e removida; sem netem residual`. O *trap* de saída também é `|| true`. O injetor
  sai com **0**, e o `injetor.log` não mostra que o nó parou. Se o orquestrador tratasse o F3 como o F2/F4 do B1
  (exit 1 ou 6), a execução passaria como F3 completa sem aviso.
- **Correção (orquestrador; o injetor não muda):** com F3, exit 0 e o nó-alvo fora de `running` (ou já religado
  pela linha de fim), a execução fica pendente (`injetor_alvo_parado`), e a pós-checagem confere a linha de fim e a
  trilha como no B1: AC-5 APLICADA nesse nó vira o aviso "falha efetiva ate a parada do no pelo AC-5; o netem saiu com
  o netns de <no>", o resto vira recusa. Exit 5 com o nó já parado (o AC-5 entre a guarda e a sonda do injetor) é
  recusa, não parada da fase. Caso S26e; o dublê de `tc` falha no netns de um nó parado, como o `nsenter` real.
- **Para o Senior:** a frase do B-40 §4.3 sobre o B1 no F3 deve dizer "o injetor sai com 0 e não acusa a parada".
- **Status:** `RESOLVIDO` (05/11, W-6c); a confirmar na VM se houver AC-5 numa F3 (o B-40 espera zero).

### P-58 (W-6c; P-55 na branch do W-6c, renumerado na integração) — o `inject-jitter.sh` só trata o TERM depois do `sleep` da janela

- **Sintoma:** o injetor dorme a janela em primeiro plano (`sleep "${DURACAO}"`), e o bash só roda o *trap* quando o
  comando em primeiro plano termina. O `limpar` do orquestrador mandava TERM ao injetor e esperava: com o F3 em curso
  na VM, até 251 s com o `netem` aplicado antes de o orquestrador sair. O runner do *rebaseline* já contornava com o
  `matar` (`pkill -TERM -P` antes do `kill`).
- **Correção:** no `limpar`, com o F3 ativo, `pkill -TERM -P <injetor>` antes do `kill -TERM`, como o `matar`; o
  injetor então sai pelo `set -e` e o *trap* remove o `netem`. Depois, o orquestrador tira o resto da raiz, desarma o
  *dead-man* e grava o `end`. Sem `pkill` (o Git Bash dos testes), a espera é a do `sleep`, curta na escala da suíte.
  Caso S26f.
- **Status:** `RESOLVIDO` (05/11, W-6c).

### P-59 (W-6c; P-56 na branch do W-6c, renumerado na integração) — no S3 o cluster já é recriado antes de toda execução: o que fazer com swap alto depois da recriação

- **Fato:** o orquestrador do S3 faz `down`/`up` antes de cada execução (TD-015, escolha 1). O `down` mata as JVMs dos
  servidores e solta o swap delas. A leitura do portão (depois do K1) mede então o swap do que sobrevive à recriação
  (outros processos da VM) mais o que as JVMs novas empurraram no arranque e nos 60 s do K1. Os números de 05/11
  (+1 GiB a cada 7 a 10 execuções) vêm da campanha em curso, cujo runner não recria o *cluster* entre execuções.
- **Consequência:** a recriação extra pedida pelo W-6c só reduz o swap se ele for das JVMs dos servidores; se for de
  outro processo, a segunda leitura repete a primeira e a execução roda com o aviso "swap continua …". O que de fato
  esvazia o swap com RAM livre é `swapoff -a && swapon -a`, que não está no pedido.
- **Decisão (coordenador, sob autorização do Gestor, 05/11):** parar a fase. Nas 4 recriações medidas na noite 1 de
  05/11 o swap caiu para 39 a 51 MB; swap alto depois de recriar indica outra causa, desconhecida, e não deve
  contaminar dados.
- **Correção:** com o swap ainda ≥ `--swap-limite-kb` depois da recriação do portão, o orquestrador grava o
  `execucao.json` (com `swap_portao.parada`) e sai com **exit 11**, "parada swap: …" na última linha do log, sem rodar
  a execução e sem `checagem.json`; na retomada, a DT-014 guarda o diretório como `.abortada-<UTC>` e o mesmo i roda.
  Exit 11 no `--help` e no README. Caso S27c (vermelho em `d2c7241`, implementação em `f2dac17`).
- **Status:** `RESOLVIDO` (05/11, W-6c).

### P-60 (W-6c; P-57 na branch do W-6c, renumerado na integração; ressalvas N1, R3, R4, R7 e R8 da V-W6b) — dublês e evidências do B1 mais fracos que os injetores reais

- **Sintoma (V-W6b, seções 2.1, 3 e 8):** (N1) o dublê de F4 saía sempre no fim da janela, mas o `inject-netdegrade.sh`
  real sai no fim do *ping* (~40 s depois do `start`) quando o AC-5 para o alvo durante a verificação, e o `end` do
  braço B adianta ~50 s, o que desloca o Δ_rec só nesse braço; o `execucao.json` não guardava o fim nominal nem a saída
  do injetor. (R3/R8) o S22 e o S25 dependiam do relógio do Git Bash: sob carga, o AC-5 do dublê caía antes do disparo
  ou depois da janela, e a mutação do S25 passava. (R4) no F4, o B1 aceitava sem prova de que a degradação foi
  aplicada. (R7) o `--help` cortava o cabeçalho em 60 linhas e não mostrava "Variaveis" nem "Exit codes".
- **Correção:** N1 e R4 no orquestrador (TD-015 item 25); dublê de F4 com o *ping* de 40 s, a linha `degradacao
  aplicada` e a saída 6 no fim do *ping*; o dublê de `java` para o nó depois do `start` da falha (`ac5-apos-start`) e
  segura a linha de fim até o nó do F1 parar (`java-espera-parado-<no>`); `uso()` até o `set -euo pipefail`. As
  quatro mutações da V-W6b §2.1 foram rodadas de novo em paralelo, com carga (`docs/testes.md`, W-6c).
- **Status:** `RESOLVIDO` no W-6c, exceto a regra da análise do N1 (fim nominal ou `end` do injetor nas execuções com o
  aviso do B1), que é decisão do Gestor no B-40 §9 e uso no W-5b.

## Observação de modelagem (não é defeito)

Com 8 *threads* = 8 identidades fixas (`user-<tid>`), `incrementFailure`
atinge o limite (5) cedo e bloqueia a identidade; logins subsequentes
retornam `BLOCKED` e `validate` retorna `NONE` (sem sessão criada).
Isso é comportamento de domínio correto (códigos de sucesso, não erro) e
produz dados válidos com `taxa_erro ~0`. Para variar mais o estado em
baterias futuras, ampliar o universo de identidades.
