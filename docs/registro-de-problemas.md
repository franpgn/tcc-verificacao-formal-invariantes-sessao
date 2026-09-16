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

## Observação de modelagem (não é defeito)

Com 8 *threads* = 8 identidades fixas (`user-<tid>`), `incrementFailure`
atinge o limite (5) cedo e bloqueia a identidade; logins subsequentes
retornam `BLOCKED` e `validate` retorna `NONE` (sem sessão criada).
Isso é comportamento de domínio correto (códigos de sucesso, não erro) e
produz dados válidos com `taxa_erro ~0`. Para variar mais o estado em
baterias futuras, ampliar o universo de identidades.
