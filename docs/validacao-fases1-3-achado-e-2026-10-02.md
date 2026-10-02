# Validação das Fases 1-3 do Worker (branch `fix/revisao-mecanismo-conflito-real`)

> Elaborado pelo Senior em 2026-10-02, validando o trabalho do Worker na branch
> `fix/revisao-mecanismo-conflito-real` (3 commits: `e424c8a`, `aaaf11f`, `9a8afd1`,
> partindo de `ddf9dfc`, sem *push*) antes de reportar ao Gestor. Cada afirmação abaixo
> foi cruzada contra o artefato real (`git diff`, os `.out`/`.cfg` do TLC em
> `runs/tlc/inv-*/`, `spec/SessionStore.tla`, `docs/registro-de-problemas.md`,
> `tcc-escrita/Metodologia.tex`) — não contra o relato do Worker isoladamente. Onde algo
> foi re-verificado contra código-fonte externo (Infinispan), foi baixado de novo nesta
> sessão para confirmação independente, não reaproveitado de memória da sessão anterior.
> **Este documento é só validação — nenhum arquivo de `spec/*.tla` foi tocado.**

---

## 1. A regressão de I5/I6 é genuína e está corretamente diagnosticada

**Confirmado, com evidência direta, não só o relato do Worker.**

- `git diff ddf9dfc 9a8afd1 -- runs/tlc/inv-small/small-I6.out` e os equivalentes
  `tiny`/`medium`: o checkpoint **anterior** (`ddf9dfc`) fecha I5/I6 exaustivamente nas
  três cardinalidades (`"Model checking completed. No error has been found."`,
  `small` = 644.013 estados distintos, `medium` = 1.671.294). O checkpoint **novo**
  (`9a8afd1`), com os **mesmos parâmetros** `MaxCanal=1, MaxRelogio=3` (verificado nos
  `.cfg` de ambos os *commits* — comparação justa, confirmada) **viola** I5 e I6 nas
  três cardinalidades, com contraexemplos de 4-5 estados.
- Li o contraexemplo completo de `small-I6.out` linha a linha: Estado 1 (inicial, tudo
  zerado) → Estado 2, `r1` executa `Block(u1)` (`relogio[r1]: 0→1`,
  `identidades[r1][u1] = [ts:1, contador:0, status:BLOCKED]`, mensagem `ID_BLOQUEIA`
  enfileirada para `r2`) → Estado 3, **concorrentemente**, `r2` executa
  `IncrementFailure(u1)` (`relogio[r2]: 0→1`,
  `identidades[r2][u1] = [ts:1, contador:1, status:OPEN]`, mensagem `CONTADOR_INC`
  enfileirada para `r1`) → Estados 4-5, `EntregaMensagem` processa as duas mensagens: em
  ambos os sentidos o gate `m.ts > ts_local` é `1 > 1 = FALSO` — as duas mensagens são
  descartadas, os canais ficam vazios (quiescência), mas `identidades[r1][u1] ≠
  identidades[r2][u1]` permanentemente. **Bate exatamente** com a causa-raiz que o
  Worker reportou — não há erro de leitura do contraexemplo.
- Confirmei também, lendo o trecho correspondente do modelo **anterior**
  (`sessoesTS`/`motivosTS` vs. `contadorTS`/`statusTS` independentes em `ddf9dfc`), por
  que esse cenário específico **não** produzia divergência antes: `Block` só tocava
  `statusTS`, `IncrementFailure` só tocava `contadorTS` — cada mensagem entrante
  comparava contra um campo que começava em `0`, então `1 > 0` passava nos dois
  sentidos, sem empate possível. Ao colapsar os dois num único registro com um único
  `ts` (mais fiel ao `.put()` real — `SessionState.java`/`IdentityState.java` só têm um
  campo `versao`), as duas escritas concorrentes passam a **competir pelo mesmo `ts`**.
  Essa é a explicação correta e completa — não falta nenhum elemento.
- Parâmetros/execução: `.cfg` de `tiny`/`small`/`medium` confirmados com
  `MaxCanal=1, MaxRelogio=3` nos dois *commits* (não houve mudança de parâmetro
  mascarando a regressão). `TypeOK`/`I3`/`I4` fecham exaustivamente no modelo novo
  (mesma contagem de estados que `TypeOK`, confirmando checagem completa, não truncada);
  `I1`/`I2` continuam violando "por construção" como antes, com menos estados
  distintos (consistente com o espaço de estados ~5,7x menor, também confirmado:
  `small`/`TypeOK` 644.013→113.213, proporção bate com o que o Worker reportou).
- `MCSession.tla`: `Permutations(REPLICAS)` restaurada na `SYMMETRY`. Verifiquei que
  isso é correto, não um artifício que esconde a regressão — o gate de
  `EntregaMensagem`/ações locais no modelo novo compara só `ts` (sem termo dependente de
  qual réplica escreveu), então a especificação voltou a ser simétrica em `REPLICAS` de
  fato; redução por simetria é sólida para detecção de violação de invariante (se uma
  violação existe em alguma instância simétrica, existe na representante canônica
  também) — não é isso que faz a regressão aparecer, a regressão aparece com ou sem essa
  redução, só muda quantos estados o TLC precisa visitar até achá-la.

**Veredito do item 1: a regressão é real, o diagnóstico está certo, e a implementação
da Fase 3 (parâmetros, execução, leitura do resultado) não tem erro.**

---

## 2. Qual das 3 opções recomendar — e por quê

O Worker levantou: (i) aceitar/documentar e seguir para F2; (ii) desempate determinístico
a nível de registro; (iii) revisar a reordenação não-FIFO do canal.

**Recomendação do Senior: rejeitar (ii), e não tratar (i) e (iii) como alternativas
isoladas — sequenciá-las.** Justificativa:

### 2.1 Por que (ii) é a opção errada

Reintroduzir um desempate determinístico por réplica (mesmo "a nível de registro", não
mais por campo) resolveria I5/I6 numericamente, mas reintroduziria **exatamente** o tipo
de mecanismo que o Achado E mostrou não corresponder a nada real: a definição oficial de
`PREFERRED_NON_NULL` (Tabela 10 da documentação do Infinispan, citada no documento de
revisão) nunca compara duas escritas concorrentes por identidade/prioridade de réplica
individual — compara **tamanho da partição**. Um desempate por réplica individual (ex.:
"`r1` sempre vence empate contra `r2`") não é uma aproximação de "partição maior vence"
— é um mecanismo estruturalmente diferente, sem contrapartida real em nenhum dos dois
regimes (nominal ou sob partição). Adotar (ii) desfaria o ganho conceitual desta revisão
só para fazer o número de violações voltar a zero — **progresso aparente, não real**.

### 2.2 (i) e (iii) não são alternativas — (iii) é a forma correta de completar (i)

O próprio Achado E (item A.4, já no documento de 02/10, agora confirmado linha a linha
pelo *spike* da Fase 1 do Worker) estabelece que, em operação nominal, escritas
concorrentes na **mesma chave** são serializadas por trava no *owner* primário antes de
qualquer réplica considerar sua escrita "aplicada" — ou seja, o cenário que gera o
empate (`r1` e `r2` cada uma aplicando **sua própria** escrita local a `u1` **sem saber
da outra**, e só depois trocando mensagens) **não pode acontecer** sob o mecanismo real
em operação nominal. O canal assíncrono do modelo (`EntregaMensagem` sempre disponível,
reordenação livre, `\E i \in 1..Len(canal[r])`) é o que torna esse cenário alcançável —
e essa é precisamente a abstração que o Achado E (A.4) já tinha identificado como **não
correspondente a nada real no caminho nominal**, antes mesmo de qualquer regressão
aparecer. Logo, a forma certa de tratar a regressão não é "aceitar e seguir" nem
"corrigir com um mecanismo artificial", é **reconhecer que ela é um sintoma do mesmo
problema estrutural que já estava identificado**, e resolvê-lo fazendo exatamente o que
a Fase 4 (F2) já precisa fazer de qualquer forma: restringir a reordenação não-FIFO/
entrega assíncrona para só ser alcançável **quando o modelo estiver em estado
particionado** — no regime nominal, uma escrita local deveria se tornar visível a todas
as réplicas antes que outra escrita concorrente na mesma chave seja aceita em outro
lugar (espelhando a serialização real por trava).

### 2.3 Recomendação concreta e sequenciamento

1. **Agora:** manter a documentação como está (P-16, já registrada) — **não
   implementar nenhuma correção nesta rodada**, isso seria reabrir o escopo "Fases 1-3"
   que o Gestor já fechou.
2. **Quando a Fase 4 for desenhada:** a ação de partição `Particionar`/`Mesclar` (já
   prevista no documento de 02/10, §C.2) deve vir acompanhada de uma restrição ao
   `EntregaMensagem`/escritas locais do regime **nominal**: enquanto não há partição
   ativa, a entrega deveria ser **efetivamente síncrona** por chave (ex.: uma escrita
   local só é aceita se não houver mensagem pendente não entregue para aquela mesma
   chave — ou, mais simplesmente, um escrita local satura um "bloqueio" lógico da chave
   até a entrega da sua mensagem completar) — fazendo o cenário do contraexemplo de
   P-16 deixar de ser alcançável no regime nominal **por construção**, sem precisar de
   nenhum desempate artificial. Sob partição (regime F2), a reordenação continua
   possível (é o que efetivamente acontece quando duas réplicas ficam incomunicáveis) e
   a resolução passa a ser a regra real (maioria vence, registro inteiro, Fase 4).
3. Isso não é um item novo de trabalho — é uma especificação mais precisa do que a Fase
   4 já precisava fazer. **Não adia nada, só evita que a Fase 4 herde cegamente a mesma
   abstração de canal que a Fase 1-3 já mostrou, duas vezes agora (A.4 e P-16), não
   corresponder ao caminho nominal real.**

**Se o Gestor preferir não aprofundar isso agora:** a alternativa aceitável é (i) pura —
manter P-16 documentada como limitação conhecida e reavaliar só quando F2 for desenhada.
Isso é defensável (é literalmente "aceitar e documentar", sem inventar um mecanismo
espúrio) — só não é, na minha avaliação, tão bom quanto entrar na Fase 4 já sabendo que
a restrição de §2.3 item 2 precisa estar no desenho desde o início.

---

## 3. A Fase 2 seguiu fielmente a Opção 2 — a causa da regressão é uma lacuna de especificação do PRÓPRIO plano do Senior, não um desvio do Worker

Revisei `spec/SessionStore.tla` (`9a8afd1`) linha a linha contra o que
`docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md` §C.1 especificava para a
Opção 2: `SessaoRec`/`IdentRec` com um único `ts` cada, `Mensagem` carregando o registro
inteiro, `TypeOK` atualizado, as 6 ações locais e `EntregaMensagem` reescritas para
gate único por registro. **Tudo isso bate exatamente** com o que foi implementado —
inclusive o comentário de cabeçalho do Worker lista, item a item, a mesma tabela de
"o que fica obsoleto por construção" que eu tinha produzido em §b do documento de
02/10, e vai além: já documenta explicitamente, no próprio código (não só em
`docs/`), que o gate `ts` continua sendo "um dispositivo de modelagem" e não uma
afirmação sobre o mecanismo real — exatamente a ressalva que meu documento fazia.

**O único ponto não especificado explicitamente pelo meu plano** era: ao colapsar em um
registro único, **mantém-se ou remove-se** o desempate determinístico por réplica
(`RepIndex`/`Prio`)? Meu documento (§B, tabela) classificava a correção #3 (gate por
campo independente) como "obsoleta sob Opção 2" mas não tratava **separadamente** a
questão de manter algum desempate por réplica a nível de registro — uma lacuna da minha
especificação, não um desvio do Worker. A escolha do Worker (remover) é uma leitura
razoável e defensável do espírito da Opção 2 ("elimina a decomposição por campo e a
maquinaria que ela exigia") — e, crucialmente, o Worker **não tentou esconder ou
presumir** a consequência: implementou, rodou o TLC imediatamente (Fase 3, como o
próprio plano exigia — "não presumir o resultado"), e reportou a regressão com causa-raiz
completa antes de prosseguir. **Isso é o processo funcionando como deveria**, não uma
falha a corrigir. Não haveria benefício em o Worker ter parado no meio da Fase 2 para
perguntar "mantenho o desempate?" sem ainda ter dado nenhum — a pergunta só ficou
precisa **depois** de rodar o TLC, que é exatamente quando ela foi trazida.

**Veredito do item 3: Fase 2 fiel à Opção 2. Nenhuma correção de processo necessária.**

---

## 4. Os dois achados laterais merecem registro adicional — um deles é mais importante do que parece

### 4.1 `resolveConflictsOnMerge()` independente de `whenSplit()` — confirmado, registrar como emenda ao desenho da Fase 4

Rebaixei o `.jar` de fontes do Infinispan 15.0.21.Final de novo nesta sessão (download
independente, não reaproveitado) e confirmei exatamente o método citado pelo Worker:

```java
// org.infinispan.configuration.cache.PartitionHandlingConfiguration
public boolean resolveConflictsOnMerge() {
   EntryMergePolicy policy = mergePolicy();
   if (policy == MergePolicy.NONE) return false;
   return policy != null;
}
```

Confirmado também `PreferConsistencyStrategy.isMinorityPartition(stable, lost) ==
lost.size() >= Math.ceil(stable.size()/2d)` — para F1 (`stable=3, lost=1`):
`1 >= Math.ceil(1.5)=2` é falso, F1 não é partição minoritária, DEGRADED não ativa.
**Ambas as citações do Worker são exatas**, não aproximadas.

**Isso é genuinamente um achado novo** (meu documento de 02/10 se apoiou na prosa da
documentação oficial sobre "modo DEGRADED", não nesse método específico) e **deve
emendar `docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md` §C.2, Fase 4**: a
condição de disparo do `merge-policy` no desenho da ação `Mesclar` não deve ser
condicionada a qual `when-split` está configurado — deve disparar sempre que
`mergePolicy ≠ NONE` **e** tiver ocorrido um evento de *merge* de partição que volte o
cluster a `AVAILABLE`, independente de `DENY_READ_WRITES`/`ALLOW_READ_WRITES`. Registro
recomendado: nota de emenda no próprio documento de revisão (não precisa de arquivo
novo), a ser feita quando a Fase 4 for de fato desenhada.

### 4.2 `Unblock`/`contadorFalhas` — NÃO é uma divergência ambígua: o código Java tem um bug real, confirmado contra `Metodologia.tex`

O Worker registrou isso como "achado lateral, fora de escopo, divergência pré-existente"
sem decidir qual lado (modelo ou código) está certo. **Eu fui ler `tcc-escrita/Metodologia.tex`
(leitura permitida, sem edição) e a resposta está lá, não é ambígua:**

> "**O7, desbloqueio.** Remove a marca de bloqueio e zera o contador. Efeito:
> β'(u).marca = Free, **β'(u).counter = 0**. Não recria sessões previamente
> invalidadas." (`Metodologia.tex`, linha 78)

A especificação oficial, já aprovada, **exige** que O7 zere o contador.
`spec/SessionStore.tla` (modelo, tanto antes quanto depois desta revisão) **está
certo** — `Unblock` zera `contador`. `IdentityState.desbloquear()`
(`workload/src/main/java/.../IdentityState.java`, código real, lido em `ddf9dfc`) **tem
um bug**: `desbloquear()` preserva `contadorFalhas` (só `resetar()`, usado por O5, zera).
**Isso não é uma divergência modelo-vs-código para "decidir qual lado muda" — é um
defeito real no código do *workload*, a corrigir.** Recomendo registrar como um novo
item de *backlog* (ex. `B-32` ou o próximo número livre em
`docs/planejamento-inicial.md`/`docs/plano-controlador-heuristico-2026-09-24.md`):
corrigir `IdentityState.desbloquear(...)` para zerar `contadorFalhas`, coerente com
`Metodologia.tex` linha 78 e com o modelo formal (que sempre esteve certo nesse ponto).
Baixo risco, mudança de 1 linha, mas **relevante para a credibilidade de qualquer dado
real de baseline que envolva O7** — enquanto não corrigido, qualquer execução real que
exerça `Unblock` produz um estado que diverge do que a especificação (e o modelo)
definem como correto, o que contaminaria silenciosamente o catálogo operação×falha×
invariante do plano de 24/09 se não for corrigido antes de B-23/B-24 gerarem dado real
envolvendo O7.

---

## 5. Outras inconsistências encontradas nesta validação

### 5.1 Colisão de numeração com o plano de consolidação B-21 (ainda não executado, mas já planejado)

`docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §2.3 já recomendava renumerar a
seção "P-08" da Cadeia 2 (`netem`/`nsenter`, F3) para **"P-16"** ao executar o merge da
B-21. Mas a nova entrada do Worker nesta rodada **também** se chama **"P-16"**
(`fix/revisao-mecanismo-conflito-real`, commit `aaaf11f`) — e como a revisão do modelo
foi sequenciada **antes** do merge da B-21 (conforme minha própria recomendação no
documento de 02/10, §D, já aprovada pelo Gestor), quando a B-21 for de fato executada a
partir desta branch, **"P-16" já estará ocupado**. **Correção necessária, a aplicar no
momento da execução da B-21 (não agora):** a seção da Cadeia 2 (`netem`/`nsenter`) deve
ser renumerada para **"P-17"**, não "P-16". Registrado aqui para não se perder até lá.

### 5.2 Nomenclatura do título de P-16 é um pouco confusa

O título "P-16 — colapso em registro único remove *by-construction* P-10/P-15-like bugs,
mas expõe um NOVO empate..." mistura o que foi **resolvido** com o que foi
**introduzido** num único item — tecnicamente correto, mas um pouco denso para quem for
ler o histórico de problemas depois sem o contexto desta sessão. Sugestão (baixa
prioridade, não bloqueante): ao integrar na B-21, considerar separar em duas entradas
(uma de resultado positivo, outra do novo achado) — ou manter como está, é uma questão
de estilo, não de correção.

### 5.3 Nenhuma inconsistência encontrada em `full` (não executado)

O Worker não rodou a cardinalidade `full` nesta rodada ("fora do mínimo pedido") — isso
é consistente com o que o Gestor pediu (Fases 1-3, TLC em `tiny`/`small`/`medium`) e não
é uma omissão a corrigir. Só registro que, se a decisão final for a abordagem de §2.3
(restringir reordenação nominal na Fase 4), `full` deve ser incluída na bateria de
reverificação daquele momento, não precisa ser rodada agora.

### 5.4 Verifiquei que o `.jar` usado pelo Worker é a mesma versão pinada no cluster real

`infinispan-core-15.0.21.Final-sources.jar` — mesma versão de
`quay.io/infinispan/server:15.0.21.Final` em `cluster/podman-compose.yml`. Não é uma
versão genérica/aproximada; a fonte consultada é exatamente a do servidor que o projeto
usa.

---

## 6. Resumo do veredito

| Pergunta | Resposta |
|---|---|
| 1. Regressão genuína e bem diagnosticada? | **Sim**, confirmada contra os `.out` reais, contraexemplo lido linha a linha, parâmetros idênticos ao checkpoint anterior. |
| 2. Qual opção recomendar? | **Rejeitar (ii).** Tratar (i) como estado atual aceitável e (iii) como a forma correta de resolver de vez — a incorporar no desenho da Fase 4 (restringir reordenação ao regime particionado), não como correção isolada agora. |
| 3. Fase 2 fiel à Opção 2? | **Sim.** O único ponto não coberto pelo meu plano original (manter ou não desempate por réplica) foi decidido de forma razoável pelo Worker, e a consequência foi corretamente medida e reportada — processo funcionando como deveria, não uma falha. |
| 4. Achados laterais merecem registro? | **Sim, os dois** — `resolveConflictsOnMerge()` emenda o desenho da Fase 4 (achado novo, confirmado); `Unblock`/`contadorFalhas` é um **bug real no código**, confirmado contra `Metodologia.tex` linha 78 (não ambíguo), recomendo novo item de *backlog* para corrigir antes de B-23/B-24 gerarem dado real envolvendo O7. |
| 5. Outras inconsistências? | Colisão de numeração P-16 com o plano de B-21 (corrigir para P-17 na hora do merge); observação de estilo no título de P-16 (não bloqueante). |

**O trabalho do Worker nas Fases 1-3 está correto, bem documentado e pronto para ser
levado ao Gestor como concluído** — nenhuma correção é necessária na branch
`fix/revisao-mecanismo-conflito-real` antes da próxima decisão. As duas ações
pendentes (restrição de reordenação nominal a incorporar no desenho da Fase 4; correção
de `IdentityState.desbloquear()`) são itens de *backlog* para rodadas futuras, não
bloqueios do que já foi feito.

---

## Pontos que precisam de validação do Gestor

1. Recomendação do item 2 (§2.3): incorporar a restrição de reordenação-só-sob-partição
   ao desenho da Fase 4, em vez de tratar como correção isolada agora ou aceitar (i) sem
   mais nada.
2. Novo item de *backlog* para corrigir `IdentityState.desbloquear()` (zerar
   `contadorFalhas`, conforme `Metodologia.tex` linha 78) — confirmar prioridade e se
   deve entrar antes ou junto de B-23/B-24.
3. Emenda ao documento de 02/10 §C.2 Fase 4 sobre `resolveConflictsOnMerge()` — aceitar
   o texto proposto em §4.1 acima.
4. Correção de numeração P-16→P-17 da Cadeia 2 no momento da execução da B-21 (§5.1) —
   só uma confirmação de que isso será lembrado, não uma decisão de mérito.
5. Como sempre: nada vai a `origin/*` sem aprovação explícita.

## Próxima tarefa sugerida para o Worker

Nenhuma correção pendente na branch `fix/revisao-mecanismo-conflito-real` — está pronta
para a próxima decisão do Gestor sobre o que vem a seguir (prosseguir para B-21 já a
partir desta ponta revisada, como planejado em
`docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md` §D, ou endereçar primeiro os
dois itens de *backlog* de §4 — decisão do Gestor, não decidida aqui).
