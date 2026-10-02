# Validação do fix O7 e do merge B-21 (`feature/b21-consolidacao-base`)

> Elaborado pelo Senior em 2026-10-02, validando `d13b06b` (fix do bug O7) e o merge
> `f91f51b`/checkpoint `8836e95` (`feature/b21-consolidacao-base`) antes de liberar
> B-23/B-24. Toda verificação abaixo foi **executada de novo nesta sessão**, não só lida
> do relato — `mvn test`, `scripts/inject-jitter.test.sh` e validação de sintaxe de
> `cluster/*.xml`/`*.yml` foram rodados diretamente pelo Senior sobre a *working tree*
> real (branch `feature/b21-consolidacao-base`, HEAD `8836e95`), não presumidos do
> relato do Worker. **Só validação — nenhum arquivo de código/`spec/*.tla` foi
> alterado.**

---

## 1. Resolução do conflito — correta e completa, sem perda de conteúdo

`git diff --stat` do *merge commit* `f91f51b` contra **cada um** dos dois pais:

- Contra `d13b06b` (Cadeia 1 + Achado E + fix O7): aparecem só os arquivos
  **exclusivos** da Cadeia 2 (`scripts/inject-jitter.sh`, `scripts/run-baseline.sh`,
  `docs/implementacao.md`, `docs/testes.md`, `runs/S1-F3/*`, `runs/S2-F3/*`, etc.) — só
  `+` (inserções), nenhum `-` (remoção) em nenhum arquivo que não seja
  `docs/registro-de-problemas.md`.
- Contra `d1b82ae` (Cadeia 2, `origin/feature/f3-baseline`): aparecem só os arquivos
  **exclusivos** da Cadeia 1 (`spec/*.tla`, `runs/tlc/*`, `docs/plano-fix-*.md`,
  `docs/relatorio-tlc-*.md`, mais `IdentityState.java`, que só existe nesta cadeia) —
  de novo, só `+`.

Isso é exatamente o padrão esperado de um *merge* limpo e não-destrutivo: cada lado
preservou o que o outro não tinha, e nenhum conteúdo de nenhum dos dois pais
desapareceu. Busquei também por marcadores de conflito remanescentes
(`<<<<<<<`/`=======`/`>>>>>>>`) em toda a árvore de `f91f51b` — as únicas ocorrências de
`===...===` são separadores legítimos de cabeçalho em arquivos Python/TLA+ (nada a ver
com conflito de *merge*). **Confirmado: resolução correta e completa.**

Na numeração de `docs/registro-de-problemas.md`: `P-01`...`P-18`, sequencial, sem
duplicata (`git grep "^### P-" f91f51b`), e as seções P-08 a P-11 **originais** (sobre
*broadcast*/`EntregaMensagem`, 2026-09-16) seguem intactas e sem ambiguidade com o novo
P-18 (netem, antiga "P-08" da Cadeia 2) — nenhuma referência cruzada a "P-08" ficou
apontando para o item errado.

---

## 2. P-18 é a renumeração correta — não há alternativa melhor

Confirmado: no momento em que a seção "P-08" da Cadeia 2 precisou ser renumerada
(durante o *merge*), a faixa `P-16` (revisão do Achado E, criada na rodada anterior,
`docs/revisao-mecanismo-conflito-infinispan-2026-10-02.md`) e `P-17` (fix do bug O7,
`d13b06b`, criado **depois** do meu plano original mas **antes** do *merge*) já
estavam ocupadas na própria `fix/revisao-mecanismo-conflito-real`. **P-18 é o único
número livre e correto nesse ponto da sequência.**

Considerei a alternativa de renumerar **para trás** (ex.: mover o fix O7 para "P-18" e
devolver "P-17" à Cadeia 2) — **pior**: meu próprio documento de validação anterior
(`docs/validacao-fases1-3-achado-e-2026-10-02.md`, já comitado) e o próprio commit
`d13b06b` já citam "P-17" pelo nome para o fix do O7; mudar isso quebraria essas
referências já publicadas, exatamente o tipo de custo que a política do projeto (nunca
*rebase*/renumerar histórico já citado sem necessidade) pede para evitar. A escolha do
Worker — manter P-16/P-17 como estão e empurrar só a seção **sem nenhuma referência
cruzada existente** (netem, verificado) para P-18 — é a de menor custo e a correta.
**Aprovado: P-18, sem alteração.**

---

## 3. Fix do bug O7 — correto; suficiente para a correção, mas falta cobertura de teste (não-bloqueante)

Reli o *diff* de `d13b06b`: `IdentityState.desbloquear()` passou de
`return new IdentityState(identidade, contadorFalhas, Status.OPEN, versao + 1, agora);`
para `return new IdentityState(identidade, 0L, Status.OPEN, versao + 1, agora);` — muda
exatamente o campo que precisava mudar (`contadorFalhas` → `0L`), preserva `identidade`,
`status`, incrementa `versao`, mesmo padrão das outras operações. Bate exatamente com
`Metodologia.tex` linha 78 ("β'(u).counter = 0") e com `spec/SessionStore.tla`/`Unblock`
(que sempre zerou `contador`, antes e depois da revisão do Achado E) — **a correção é
exatamente a certa, nem mais nem menos.**

Executei `mvn -o test` eu mesmo em `workload/` (branch `feature/b21-consolidacao-base`,
HEAD `8836e95`, **não** confiei no número reportado): `Tests run: 49, Failures: 0,
Errors: 0, Skipped: 0`, `BUILD SUCCESS`. Confirma de forma independente o "49/49" do
relato.

**Falta, sim, cobertura de teste direta** — confirmei que não existe
`IdentityStateTest.java` em nenhum lugar da árvore (`find . -iname
"IdentityStateTest.java"` vazio). Isso **não invalida** a correção (ela está certa, e a
ausência de teste não é culpa desta correção — a classe nunca teve testes, nem antes
desta mudança). Mas é uma lacuna real de cobertura em uma classe de domínio simples
(POJO imutável, sem I/O, trivial de testar sem *cluster*) que acabou de receber uma
correção de comportamento. **Recomendação (não-bloqueante para B-23/B-24): criar
`IdentityStateTest.java`** com pelo menos um caso cobrindo `desbloquear()` (zera
`contadorFalhas`, preserva `identidade`, incrementa `versao`) antes de, ou junto com, o
início de B-23 — é um acréscimo pequeno e de baixo risco, e B-23 já vai precisar decidir
como testar `SessionOps`/classes de domínio relacionadas (achado já registrado em
`docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §3.2), então é natural resolver os
dois junto.

---

## 4. `feature/b21-consolidacao-base` está pronta como base para B-23/B-24

Revalidei, eu mesmo, os itens do plano de validação pós-*merge* que eu havia definido em
`docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §2.4:

| # | Item | Verificação do Senior nesta sessão | Resultado |
|---|---|---|---|
| 1 | Nenhum arquivo perdido | `git diff --stat` contra os dois pais (§1 acima) | **OK** |
| 2 | Suíte JUnit completa | `mvn -o test` executado por mim, não só lido do relato | **OK — 49/49, confirmado de forma independente** |
| 3 | TLC — não-regressão | Comparei `distinct states found`/veredito (violado ou não) de cada um dos 21 casos (`tiny`/`small`/`medium` × 7 invariantes) entre o checkpoint da Fase 3 (`9a8afd1`) e o pós-*merge* (`8836e95`) | **OK, com uma precisão a registrar — ver §4.1** |
| 4 | `inject-jitter.test.sh` | Executado por mim: `bash scripts/inject-jitter.test.sh` | **OK — 35 PASS, 0 FAIL, exit 0, confirmado de forma independente** |
| 5 | Sintaxe do *cluster* consolidado | `podman-compose.yml` parseado com `yaml.safe_load` (Python); `infinispan-cluster.xml`/`jgroups-tcp.xml` parseados com `xml.etree.ElementTree` (Python, `xmllint` não disponível no ambiente) | **OK — sintaticamente válidos; `mode="SYNC" owners="2"`, `when-split="DENY_READ_WRITES" merge-policy="PREFERRED_NON_NULL"` confirmados intactos por `grep` direto no arquivo** |
| 6 | *Smoke* de `run-baseline.sh` | Não executado (pelo Worker nem por mim) | Não-bloqueante, como o próprio plano já previa |
| 7 | Numeração sem duplicata | `git grep "^### P-"` | **OK — P-01...P-18** |

### 4.1 Precisão sobre o item 3 (TLC) — "idêntico estado-por-estado" é impreciso; o que é de fato verdade é mais forte e mais relevante

O relato do Worker descreve o resultado como "idêntico estado-por-estado" ao checkpoint
da Fase 3. **Comparei os 21 arquivos `.out` diretamente** (`tiny`/`small`/`medium` ×
`TypeOK`/`I1`-`I6`) e isso não é literalmente verdade: os `.out` **não são
byte-idênticos**, e mesmo as métricas numéricas de "estados gerados antes de achar a
violação" **diferem** entre os dois *runs* para `I1`, `I2`, `I5` e `I6` (ex.:
`small/I6`: 3.440 estados gerados no checkpoint da Fase 3, 5.730 no pós-*merge*). Isso
é **esperado e não é uma inconsistência**: o TLC roda com 20 *workers* em paralelo e
para no **primeiro** contraexemplo que qualquer *worker* encontrar — qual estado é
encontrado primeiro, e quantos estados foram visitados até lá, varia de execução para
execução por não-determinismo de escalonamento, não por o modelo ter mudado. **O que
de fato importa, e isso sim é idêntico nos dois *runs*, sem excecão:**

- O **veredito** (viola ou não viola) é o mesmo nas 21 combinações, sem exceção.
- Onde não há violação (`TypeOK`, `I3`, `I4`, todas as cardinalidades), a contagem
  **final** de `distinct states found` é **exatamente igual** nos dois *runs*
  (`tiny`=6.472, `small`=113.213, `medium`=287.114) — e essa, sim, é uma igualdade forte,
  porque numa busca exaustiva sem violação o grafo de estados alcançáveis é
  determinístico; só a *ordem* de visita (refletida nas linhas de `Progress`
  intermediárias, que diferem) depende do escalonamento.

Ou seja: a conclusão do Worker ("sem regressão nova desta rodada") está certa, mas a
formulação "idêntico estado-por-estado" deveria ser "mesmo veredito em todas as 21
combinações, com contagem exaustiva idêntica onde aplicável; variação esperada de
contagem só nos casos que violam, por não-determinismo de busca paralela, não por
mudança de modelo". Correção de precisão textual, não de mérito — **não muda o
resultado da validação.**

---

## 5. Outras inconsistências encontradas

Nenhuma inconsistência nova de peso, além da precisão de linguagem do item 4.1. Dois
registros menores:

- A nota de numeração em P-17/P-18 (`docs/registro-de-problemas.md`) é clara e
  rastreável — referencia corretamente `docs/plano-consolidacao-b21-b23-b24-2026-10-02.md`
  §2.3 e explica o motivo exato do desvio (P-16/P-17 já ocupados nesta mesma branch).
  Nenhuma ação necessária.
- O título de "P-16" continua um pouco denso (observação já registrada em
  `docs/validacao-fases1-3-achado-e-2026-10-02.md` §5.2) — permanece válida, não
  bloqueante, estilo apenas.

---

## 6. Veredito final

| Pergunta | Resposta |
|---|---|
| 1. Conflito resolvido correta e completamente? | **Sim** — confirmado contra os dois pais, sem marcador de conflito remanescente, sem perda de conteúdo. |
| 2. P-18 é a renumeração certa? | **Sim** — único número livre no momento do *merge*; qualquer alternativa quebraria referências já publicadas (`d13b06b`, documento de validação anterior). |
| 3. Fix do O7 correto e suficiente? | **Correto.** Suficiente para a correção em si; recomendo (não-bloqueante) criar `IdentityStateTest.java` antes de/junto com o início de B-23. |
| 4. Branch pronta para B-23/B-24? | **Sim.** Todos os 7 itens do plano de validação pós-*merge* confirmados, a maioria por execução direta do Senior nesta sessão (não só leitura do relato) — `mvn test` (49/49), `inject-jitter.test.sh` (35/0), sintaxe de *cluster* válida, nenhum arquivo perdido, numeração sem duplicata. Única ressalva é de precisão textual (§4.1), não de mérito. |
| 5. Outras inconsistências? | Nenhuma de peso. |

**Aprovado para seguir para B-23/B-24.** Recomendação de acompanhamento, não-bloqueante:
incluir a criação de `IdentityStateTest.java` (cobrindo ao menos `desbloquear()`) como
parte do mesmo esforço de decidir a estratégia de teste de `SessionOps`/classes de
domínio para B-23 (§3.2 do plano de 02/10), já que ambas as decisões são do mesmo tipo
("como testar classes que hoje só são validadas por execução real contra o *cluster*").

---

## Pontos que precisam de validação do Gestor

1. Confirmar prosseguir para B-23/B-24 a partir de `feature/b21-consolidacao-base`
   (`8836e95`) — aprovado pelo Senior, sem bloqueio.
2. Confirmar se `IdentityStateTest.java` deve ser criado **antes** de B-23 começar, ou
   **junto** com a decisão de estratégia de teste de B-23 (ambas são aceitáveis na
   avaliação do Senior — só precisa decidir a ordem).
3. Como sempre: nada vai a `origin/*` sem aprovação explícita.

## Próxima tarefa sugerida para o Worker

Prosseguir para B-23 (conectar I1/I4 em `SessionOps.registrarViolacao`, persistir
`InvariantAuditor.dumpCsv`), conforme já detalhado em
`docs/plano-consolidacao-b21-b23-b24-2026-10-02.md` §3.2, a partir de
`feature/b21-consolidacao-base`. Se o Gestor confirmar o ponto 2 acima a favor de
"antes", incluir `IdentityStateTest.java` como primeiro passo da mesma *branch*/esforço.
