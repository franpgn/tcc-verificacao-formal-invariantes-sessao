# Revalidação curta do *runner* do *rebaseline* depois das correções da V-RB (2026-10-23)

> Senior, 2026-10-23. Entrega revalidada: branch `feature/rebaseline-runner-analise` (worktree
> `wt-rebaseline`), ponta `988fd3c`, rebaseada sobre `642d81f` (`feature/controlador-integracao`).
> Parecer anterior: `docs/validacao-rebaseline-runner-2026-10-23.md` (`246c87c`). Decisões do Gestor:
> `4a8d461` (DT-013, DT-014, Q1..Q5). Relatório do Worker: `wt-rebaseline/docs/relatorio-rebaseline-runner-2026-10-23.md`,
> seção 10. Nada na VM, nenhum `tc`/`netem`, sem push, sem integração. Nenhum arquivo do `wt-rebaseline`
> foi editado: os vermelhos e o mutante rodaram em cópias `git archive` no *scratchpad*, apagadas no fim.
> O único efeito no `wt-rebaseline` é `workload/target/` (ignorado), refeito pelo `mvn test` e pelo
> `package`.

## 1. Veredito

**APROVADO COM RESSALVAS.** As cinco correções e a DT-014 fazem o que o Gestor aprovou, cada uma com teste
vermelho de verdade antes do `fix:`. As quatro suítes ficaram verdes na minha máquina, e o XC2 morre no
teste certo. Pode haver o *fast-forward* de `feature/controlador-integracao` para `988fd3c`. As ressalvas
não pedem mudança de código do *runner*, mas três delas precisam ser resolvidas **antes do piloto** (seção 6).

## 2. Números medidos por mim

| Verificação | Resultado |
|---|---|
| Arquivos `642d81f..988fd3c` | 7, todos `A`; `--diff-filter=MDRT` vazio. `git diff --quiet 642d81f 988fd3c -- scripts/run-baseline.sh scripts/inject-crash.sh scripts/inject-jitter.sh analysis/percentis.py analysis/compare_scenarios.py analysis/bootstrap_ic.py workload` passa |
| Linearidade | 23 commits (9 originais reaplicados, 13 de correção e estabilidade, 1 de adenda), cada um com um só pai, em cadeia sobre `642d81f`, sem *merge* |
| `--amend` nesta rodada | nenhum. O *reflog* mostra 13 commits novos antes do *rebase* (`2892092`..`52de708`), um único *rebase* (`onto 642d81f`) e o commit da adenda. A árvore dos 7 arquivos é idêntica antes (`52de708`) e depois (`53c1d04`) do *rebase* |
| Separação `test:`/`fix:` | cada `test:` só toca `test_rebaseline.py` e/ou `run-rebaseline.test.sh`; cada `fix:` só toca `rebaseline.py` e/ou `run-rebaseline.sh`. `53c1d04` só toca o `.test.sh` |
| Matriz | `python -I scripts/test_rebaseline_matriz.py`: **8/8 OK** |
| Análise | `python analysis/test_rebaseline.py` com `REBASELINE_ORACULO_DIR=.../wt-b43-9/runs/b43-9-smoke`: **64/64 OK**, A12 executado (Q3, Q4 e Q5 iguais ao `analise.json` selado, mesmos números da V-RB). Sem o oráculo: 63 OK, 1 *skip* |
| *Runner* | `bash scripts/run-rebaseline.test.sh`, uma rodada completa: **130 PASS / 0 FAIL**, 15 min 18 s, exit 0. R4: disparo do *runner* a +49,326 s, `podman stop` a +49,928 s; C5 com `isn2` parado a +49,82 s e reiniciado a +109,842 s. Sem aviso de pausa |
| Surefire | `mvn -q -o -f workload/pom.xml test` (JDK 22.0.2, Maven 3.9.16): **761 testes, 0 falhas, 0 erros, 1 *skip***, 106 relatórios novos |
| Mutante XC2 | `mkdir -p "${d}"` antes do gate `go f3` (cópia descartável): **129 PASS / 1 FAIL**, a falha é exatamente `R3 (C2) go f3 errado nao deixa exec-1001` |

### 2.1 Vermelhos reproduzidos em cada `test:` (cópia `git archive` do commit)

| `test:` | O que rodei | Resultado | Bate com o relatório |
|---|---|---|---|
| `4224863` (C1) | análise | 3 FAIL em A14 (duas vidas com 1 aviso; contagem `null`; chave ausente) | sim |
| `4224863` (C1) | R13 | 4 FAIL (sem `vidas_por_no`; `--since` ilegível não dá exit 6; `checar` chamado; segunda execução lançada) | sim |
| `774e791` (C2) | R3 | 4 FAIL (gate depois do K1; `exec-1001` sobra; nova tentativa exit 4; F3 não roda) | sim |
| `98e095e` (C3) | R14 | 3 FAIL (injetor F3 com exit 5 segue a fase) | sim |
| `d3e5026` (C4) | análise | 1 FAIL, `test_f3_remocao_extra_recusa` | sim |
| `91cd0fe` (C5) | análise; R4 e R6 | ERROR em `test_piloto` (`falha_efetiva`); 3 FAIL em R4/R6 | sim |
| `64bc9e5` (DT-014) | análise; R15 | 2 FAIL em A15; 11 FAIL em R15 (exit 4 nos dois casos) | sim |

Não reapliquei o XC1. O XC2 basta para a amostra pedida, e o vermelho de A14 em `4224863` já mostra que a
regra "total ≤ vidas" tem teste que a separa da contagem só pela vida atual.

## 3. Conferência das correções no código

- **C1 / DT-013 (`95c0a5e`).** `k8_valores` exige os três inteiros por nó (`total`, `vida_atual`,
  `vidas`), recusa `bool` e levanta `ErroUso` (exit 2) se faltar algum. `k8_ok` é a regra da DT-013 ao pé
  da letra. No *runner*, `gravar_ispn` sai com 6 se qualquer das quatro leituras falhar ou não for
  inteiro (antes, `--since` ilegível virava `null` em silêncio). O papel de K8 continua "parada". Conforme.
- **C2 (`f6173c0`).** O bloco do gate só mudou de lugar: fica depois da checagem de diretório existente e
  antes do `mkdir` e do K1. A mensagem avisa da espera do K1. Conforme.
- **C3 (`1eba929`).** `INJ_EXITS_SEGUEM=" 0 1 4 "`; qualquer outro exit espera o *workload*, grava
  `execucao.json` e sai com 6. O desarme do dead-man e a remoção de netem acontecem **antes** desse teste,
  então a saída com 6 nunca deixa netem com o dead-man desarmado. Conforme.
- **C4 (`696a565`).** K6 de F3 exige `remocao_extra is False` (um `null` também falha, o que é o
  conservador). Conforme.
- **C5 (`66305e7`).** Só relatório: `falha_efetiva` vai para `execucao.json`, `metricas.falha_efetiva_informativo`
  e `piloto.json`; K6 não lê o campo. Falha de leitura vira `null` com aviso. Conforme.
- **DT-014 (`0923700`).** Sem `stdout.log`: `mv` para `.abortada-<UTC>` e o fluxo segue com o mesmo i (a
  semente vem da matriz, então é a mesma). Com `stdout.log`: `interrompida.json` (sem sobrescrever se já
  existir) e `checar`/`estado` pelo mesmo `checar_e_estado` das execuções normais, de modo que paradas (a)
  e (b) e reposições valem igual. `ler_checagens`, `reposicoes_pendentes`, o laço de reposições, o resumo
  e o manifesto tratam `.abortada-*`. Conforme o texto da DT-014.
- **Estabilidade (`53c1d04`).** Implementa a recomendação da V-RB 2.1. O disparo do *runner* continua com
  ±1 s; o que afrouxou foi só o teto do `podman stop` (agora aviso) e o teto "reinício ≤ 70 s" do C5.
  O afrouxamento é do *harness* e não esconde defeito do *runner*. O instante real do injetor passa a ser
  conferido no parecer do piloto pelo `falha_efetiva`.

## 4. Desvios D15..D19

| # | Recomendação | Por quê |
|---|---|---|
| D15 (exit 2/3/5 espera o *workload* antes do exit 6) | **Aceitar**, com um ajuste opcional | Esperar o *workload* é o certo (não deixa JVM órfã). Mas `finalizar 6` acontece **antes** do passo que move `.scratch/` para o diretório, então os brutos ficam em `exec-<i>/.scratch/` e não há `ispn006010.json` nem `checagem.json`. Na retomada, como existe `stdout.log`, a DT-014 (2) trata a execução como RECUSADA "interrompida" e ela é reposta. O resultado é aceitável e nada se perde. O relatório diz "para os brutos ficarem completos", o que é verdade só no sentido de que o *workload* terminou. Ajuste opcional, não bloqueante: mover `.scratch/` antes do exit 6 ou registrar o motivo real (`injetor exit N`) em `interrompida.json` |
| D16 (`UnixMilli` no template) | **Aceitar**, com o passo 4 ampliado | `time.Time.UnixMilli` existe desde Go 1.17 e o podman 4.9.3 é compilado com Go bem mais novo; os campos `StartedAt`/`FinishedAt` do *inspect* são `time.Time`. O risco é baixo e, se falhar, vira `null` sem efeito em critério. A checklist confere só `StartedAt.UnixMilli`; acrescentar `{{.State.FinishedAt.UnixMilli}}` de `isn1` (num contêiner que nunca parou o valor é negativo, `-62135596800000`, o que só confirma que o template funciona; o *runner* lê `isn2` depois do F1, quando o valor é positivo) |
| D17 (interrompida sem avaliar K1..K10) | **Levar ao Gestor** | Para RECUSADA, não avaliar K1..K10 é coerente. Mas K9 é **parada** em SYNC sem falha (I1 = I4 = 0, nenhum I2 REAL). Numa execução interrompida depois do lançamento, uma violação de segurança registrada no CSV parcial não pararia a fase. K8 não se perde, porque a contagem de K8 é cumulativa por contêiner e a execução seguinte a vê. Recomendo **não mexer no código antes do piloto** e cobrir por procedimento: toda checagem "interrompida" é lida pelo Senior no parecer da noite, com contagem manual de `violacoes-rep-001.csv` (em `exec-<i>/` ou `.scratch/`). Alternativa, se o Gestor preferir automático: avaliar K9 *best-effort* quando o CSV existir e só elevar a PARADA (nunca aceitar), o que pede DT nova |
| D18 (escala ×20, suíte ≈ 15 min) | **Aceitar** | Medi 15 min 18 s, sem falha; é o preço da estabilidade |
| D19 (erro de sintaxe no script de mutantes) | **Aceitar** | Falhou antes de aplicar mutação, árvore limpa; reapliquei o XC2 e ele morre como descrito |

## 5. Passo 4 da checklist (10.6), só leitura na VM

Os comandos são só leitura e não injetam nada. Ajustes recomendados:

1. Na contagem de `ISPN080001`, usar `2>&1` antes do `grep`, igual ao *runner* (`contar_logs` faz
   `podman logs ... 2>&1`). Sem isso, se o servidor escrever o *log* em *stderr*, a conferência dá 0 e
   o Gestor para à toa:
   `sudo podman logs --since $(sudo podman inspect -f '{{.State.StartedAt.Unix}}' isn1) isn1 2>&1 | grep -c ISPN080001`.
2. Acrescentar a leitura de `FinishedAt.UnixMilli` (D16).
3. `systemctl list-timers --all --no-legend 'tcc-heal-rb-*' | wc -c` com resultado 0 está certo: com
   `--no-legend` e nenhuma unidade, o systemd não imprime nada. Manter "se não der 0, parar".
4. Acrescentar, no passo 2, que o *bundle* sai de `git archive` da ponta integrada (seção 6, R1), e
   conferir na VM `sha256sum -c` contra a lista do complemento da DT-009.

## 6. Ressalvas

| # | Ressalva | Quando | Recomendação |
|---|---|---|---|
| R1 | **Fim de linha.** O repositório usa `core.autocrlf=true` e o `.gitattributes` não cobre `*.py` nem `*.tsv`. No *worktree* Windows, `analysis/rebaseline.py` e `scripts/rebaseline_matriz.py` saem com CRLF (SHA diferente do *blob*). Quando `runs/rebaseline/matriz.tsv` entrar no git, o *checkout* no Windows também vai sair com CRLF e o *runner* recusará a matriz (exit 4). A recusa é segura, mas só apareceria no ensaio de pré-voo | antes de commitar `matriz.tsv` | No commit de integração, acrescentar `*.tsv text eol=lf` e `*.py text eol=lf` ao `.gitattributes` (ou `runs/rebaseline/matriz.tsv -text`), e montar o *bundle* só por `git archive`. Decisão do Gestor (mexe em arquivo de configuração do repositório) |
| R2 | **Jar não reprodutível.** Dois *builds* da mesma ponta deram jars com SHA-256 diferentes (o `pom.xml` não fixa `project.build.outputTimestamp`). O conteúdo é o mesmo: a lista ordenada (nome, CRC-32, tamanho) das 12 018 entradas tem o mesmo SHA-256 nos dois *builds* | antes do piloto | O SHA-256 do jar no complemento vale só para **o arquivo** registrado. Ele deve ser copiado agora para um lugar fixo e levado à VM tal como está; um novo `mvn package` invalida o *hash*. Fixar `outputTimestamp` tocaria `workload/` (protegido) e não vale o custo agora |
| R3 | D17 (K9 em execução interrompida) | antes do piloto | Seção 4 |
| R4 | Passo 4 da checklist (`2>&1`, `FinishedAt.UnixMilli`) | antes do piloto | Seção 5 |
| R5 | D15 (`.scratch/` não movido no exit 6) | opcional | Seção 4 |

## 7. DT-009, complemento parte 2

Registrado em `docs/decisoes-tecnicas.md`, seção "Complemento da DT-009, parte 2 de 2". Valores:

| Item | Valor |
|---|---|
| Ponta do *runner* (alvo do *fast-forward*) | `988fd3c6bca5d3f201ca3b0366cdeca9e90f21d9` |
| Jar (`workload/target/session-workload-0.1.0-SNAPSHOT.jar`, `mvn -q -o -f workload/pom.xml -DskipTests clean package`, JDK 22.0.2, Maven 3.9.16) | `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` (18 824 990 bytes) |
| Conteúdo do jar (SHA-256 da lista ordenada nome/CRC-32/tamanho, 12 018 entradas; reprodutível) | `c9aa921222655cd4991b0c6ed13c46b88231ba9184aa35c76455205b69e2669d` |
| `runs/rebaseline/matriz.tsv` (regenerada da ponta, CPython 3.12.6) | `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35` (não mudou) |
| `scripts/run-rebaseline.sh` | `d95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366` |
| `scripts/rebaseline_matriz.py` | `3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a` |
| `scripts/inject-crash.sh` | `1c9bf01b9a100e690f15f2fb4b923f2365c0d923572011cfde37eb0c36c92d55` |
| `scripts/inject-jitter.sh` | `7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529` |
| `analysis/rebaseline.py` | `fe5c7c74c9e740f6fba935caca888a03395ca7491bad22317deece857a559256` |
| *Bundle* (SHA-256 da lista `sha256sum` dos 7 arquivos acima, caminhos relativos em ordem, formato do `sha256sum` do Git Bash) | `e304ba4b719820573fd3cd2bb70525e1160e7c76459b3ce16101766c34bdc34c` |

Os *hashes* dos scripts e da análise são dos *blobs* do git (LF), não do *worktree* Windows (R1). O
*runner* não gera *bundle* próprio; o *bundle* é o conjunto do passo 2 da checklist. Fica pendente o
instante de criação do *cluster* depois do S7, que só existe na VM.

## 8. Perguntas ao Gestor

| # | Pergunta | Opção recomendada |
|---|---|---|
| G1 | No commit de integração que põe `matriz.tsv` no git, acrescentar `*.tsv text eol=lf` e `*.py text eol=lf` ao `.gitattributes` (R1)? | **Sim** |
| G2 | Congelar o jar `5eced136...` (copiar agora para um lugar fixo fora de `target/`) como o jar do piloto e das noites (R2)? | **Sim** |
| G3 | D17: cobrir K9 de execução interrompida por leitura manual do Senior no parecer da noite, sem mudar código? | **Sim** (alternativa: DT nova com K9 *best-effort* só para PARADA) |
| G4 | Ajustar o passo 4 da checklist (`2>&1` na contagem de `ISPN080001`, leitura de `FinishedAt.UnixMilli`)? | **Sim** |
| G5 | D15: aceitar como está, deixando o ajuste de `.scratch/` para depois do piloto? | **Sim** |

## 9. SHAs

| Item | SHA |
|---|---|
| Base | `642d81f` |
| Ponta revalidada | `988fd3c` |
| Correções | `4224863`→`95c0a5e`, `774e791`→`f6173c0`, `98e095e`→`1eba929`, `d3e5026`→`696a565`, `91cd0fe`→`66305e7`, `64bc9e5`→`0923700`; estabilidade `53c1d04`; adenda `988fd3c` |
| Parecer anterior | `246c87c` |
| Decisões do Gestor | `4a8d461` |
