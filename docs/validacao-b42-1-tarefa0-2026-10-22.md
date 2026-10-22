# Validação V-B42.1-T0: endurecimento do B-42.1 (Tarefa 0) (2026-10-22)

> Senior, 2026-10-22. Branch `feature/b42-1-perfil-async` (worktree `wt-b42-1`), commits `85326f7`
> (`test:`), `be9091e` (`docs:`, linha 10) e `8296270` (`docs:`, adendo), sobre `cdfa989`. Mutantes
> executados numa cópia `git archive 8296270` no *scratchpad*, removida no fim. Não alterei nada em
> `wt-b42-1`. Sem *cluster*, sem VM. Commit local, sem push.

## 1. Veredito

**APROVADA.** Os cinco sobreviventes da V-B42.1 morrem. Dos oito mutantes novos que eu apliquei, seis
morrem. Os dois que sobrevivem estão fora do escopo pedido e são de baixo custo:

- X1 é o caso do T0-D5, papéis trocados em `WorkloadMain.java:320-321`;
- X5 é uma evasão por `PerfilReplicacao.values()[0]`.

Proponho fechar os dois no primeiro commit de B-43.1 (seção 5), sem bloquear o despacho.

**SHA de partida para B-43.1 e B-43.3a (parte i): `8296270`.**

## 2. Medido × alegado

| Alegação do Worker | Medido | Confere? |
|---|---|---|
| Surefire 296 → 305/0/0/0 | cópia `git archive 8296270`, `mvn -f workload/pom.xml clean test`: exit 0, 305/0/0/0, 39 arquivos | sim |
| `be9091e` e `8296270` não mudam o código | `git diff --stat 85326f7 HEAD -- workload cluster` vazio | sim |
| *golden* idêntico a `6ba7e28` | `git diff --quiet 6ba7e28 HEAD -- GoldenS1S2Test.java workload/src/test/resources` sai com 0 | sim |
| Nenhum teste existente editado | `cdfa989..HEAD`: só `A` nos três testes novos. Contra `6ba7e28`: `M` só em `CliTest` e `SondaHotrodTest`, que já tinham sido validados como acréscimo puro na V-B42.1 | sim |
| Nenhum código de produção nem XML alterado na Tarefa 0 | `git diff --name-status cdfa989 HEAD -- workload/src/main cluster` vazio | sim |
| N1 morto (305/1/0/0) | 305/1/0/0, `PapelDoCacheTest.papelCasaComOTipo:183` | sim |
| N2 morto | 305/1/0/0, `PerfilFixoForaDoResolvedorTest.semPerfilFixoForaDoResolvedor:122` | sim |
| N3 morto | 305/1/0/0, `PapelDoCacheTest.healthyEmParSessoesDepoisContadores:190` | sim |
| N7 morto | 305/1/0/0, `PerfilFixoForaDoResolvedorTest.semPerfilFixoForaDoResolvedor:122` | sim |
| N8 morto | 305/1/0/0, `ClusterXmlBaselineSyncTest.baselineSyncIdenticoA6ba7e28:104` | sim |
| Linha 10 da doc corrigida | é a célula da tabela "Arquivos", com o texto do apêndice | sim |

## 3. Mutantes novos do Senior (contra os detectores novos)

| ID | Mutação | Resultado | Teste que mata |
|---|---|---|---|
| X1 | `WorkloadMain.java:320-321`: varredura com sessões ↔ contadores trocados | **sobrevive** | nenhum (é o T0-D5) |
| X2 | `SessionOps.java:152` (construtor do S3): contadores abre `cacheSessoes()` | morta | `PapelDoCacheTest.papelCasaComOTipo` |
| X3 | `WorkloadMain.java:404` (leitor por nó): identidades abre `cacheSessoes()` | morta | `PapelDoCacheTest.papelCasaComOTipo` |
| X4 | `Cli.java:222`: o *default* passa a `PerfilReplicacao.porRotulo("sync")` | morta, 2 falhas | `semPerfilFixoForaDoResolvedor` e `varreduraNaoEVacua` (a *allow-list* deixou de casar) |
| X5 | `S3Clientes.java:115`: admin com `PerfilReplicacao.values()[0]` | **sobrevive** | nenhum |
| X6 | XML: `state-transfer timeout` 60000 → 30000 em `counters` e `counters-async` | morta | `ClusterXmlBaselineSyncTest` |
| X7 | XML: remove `statistics="true"` de `sessions` e `sessions-async` | morta | `ClusterXmlBaselineSyncTest` |
| X8 | `WorkloadMain.java:285`: par `healthy` em ordem invertida | morta | `PapelDoCacheTest.healthyEmParSessoesDepoisContadores` |

Os dois sobreviventes, em detalhe:

- **X1.** `executarVarredura` captura a `RuntimeException`. Na prática, um `ClassCastException` nos
  valores faria a varredura **não ser executada**: sairia "VARREDURA NAO EXECUTADA" no stderr, sem
  derrubar a execução. O efeito é perder silenciosamente os achados I1/I4/bloqueio perdido. Não
  produz um número falso, mas apaga evidência. Vale um teste.
- **X5.** É uma forma adversarial e improvável. Fechar custa uma alternativa a mais no regex
  (`PerfilReplicacao.values()`).

## 4. Desvios

| Desvio | Avaliação |
|---|---|
| **T0-D1/D2**, linha 10 dentro da célula e `sync\|async` escapado | **Aceito.** É obrigatório em GFM: sem o escape, o `\|` abre uma terceira coluna mesmo dentro de *code span*. O `--amend` de `7b1739c` para `be9091e` foi local, antes de qualquer publicação, então não houve reescrita de histórico publicado nem *force-push* |
| **T0-D3**, terceiro commit `docs:` com o adendo | **Aceito.** É só documentação e tem responsabilidade única. Também faz a ponta de partida de B-43 já conter o registro. O apêndice falava em "dois commits" de trabalho, e o relatório em arquivo novo era exigido |
| **T0-D4**, a guarda (a) também pega `import static` e `valueOf`/`porRotulo` com literal | **Aceito e desejável.** São formas equivalentes de fixar o perfil. O X4 mostra que funciona. O autoteste cobre as formas corretas (`porRotulo(v)` sem literal) |
| **T0-D5**, a guarda (b) não cobre `WorkloadMain.java:320-321` | **Lacuna real, não bloqueante.** O X1 sobrevive, e o efeito é perder a varredura em silêncio. Precisa de teste, mas é barato e não muda nada em B-43.3a. Recomendo fechar no **primeiro commit `test:` de B-43.1**, num arquivo de teste novo `PapelDoCacheComplementarTest`, para não reabrir a branch de B-42.1 nem mudar o SHA de partida. O teste reconhece `Map<String, T> x = materializarTudo(...getCache(arg))`, com T = `SessionState` ⇒ `cacheSessoes()` e T = `IdentityState` ⇒ `cacheContadores()`, e acrescenta `PerfilReplicacao.values()` como perfil fixo (X5). Os dois, X1 e X5, têm de morrer |

## 5. Ajustes no prompt do Apêndice A (`validacao-b42-1-2026-10-22.md`)

O prompt continua válido, com quatro ajustes:

1. **Tarefa 0**: concluída. O Worker não deve refazê-la.
2. **Base**: B-43.1 e B-43.3a (parte i) partem de `8296270`, não de "ponta da Tarefa 0" genérica.
3. **B-43.1, commit 1 (novo, antes de T1)**: `test: guarda complementar de papel na varredura e
   perfil por values() (B-42.1 T0-D5)`, com o `PapelDoCacheComplementarTest` descrito na seção 4. Os
   mutantes X1 e X5 desta validação são reaplicados e têm de morrer. Nenhum teste existente é editado.
4. **Surefire de referência**: o "antes" de B-43.1 e de B-43.3a é **305/0/0/0, 39 arquivos**.

## 6. Decisão nova do Gestor (L = 100 ms, violação de segurança atrasada promovida)

**Sem impacto em B-42.1 nem em B-43.** Conferi o seguinte:

- B-42.1 e a Tarefa 0 não tocam a janela, a tolerância L nem a classificação de violações. Só tratam
  de nomes de *cache*, perfil e XML.
- B-43.1 (*reset*) atua antes do *warm-up* e não lê violações.
- B-43.3a parte i decide só por contagens `operation × return_code` do CSV de eventos. Não lê
  `violacoes-rep-*.csv` nem usa L.
- A parte ii (guarda *online* e `percentis.py`) também não usa L no desenho atual.
- O que muda é B-26.x, o agregador e o controlador. Se no futuro a guarda de sanidade passar a incluir
  violações promovidas, a decisão terá de ser revista nesse ponto. Hoje não inclui.
