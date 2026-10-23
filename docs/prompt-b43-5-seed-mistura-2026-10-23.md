# Prompt de implementação do B-43.5: `Random` da mistura semeado por *thread* (2026-10-23)

> Senior, 2026-10-23. Pré-requisito do *rebaseline* (DT-009, decisão R4 do Gestor). Referências
> `arquivo:linha` lidas em `96c8045` (ponta do B-43.9 antes da correção F-1; a correção F-1 mexe em
> `SessionOps` e em testes novos, não nas linhas citadas aqui). Em `5e1084f`
> (`feature/controlador-integracao` antes do B-43.9) o `WorkloadMain.java` tem 10 linhas a menos antes
> desta região (`:743` lá corresponde a `:753` aqui), por isso a base é a ponta pós-integração do B-43.9. O Worker reconfere todas as linhas na
> base antes de começar. Sem push.

## 1. Objetivo e escopo

Hoje cada *thread* de carga de S1/S2 sorteia o tipo da operação com `ThreadLocalRandom.current()`
(`WorkloadMain.java:753`), que não depende de `--seed`. O `KeyGenerator` (`:754`) e o sorteio do
*token* antigo (`MisturaV3.Cliente`, DT-008) já são semeados por `sementeThread(seedBase, tid, rep)`
(`:739-741`). O B-43.5 troca o `ThreadLocalRandom` por um `java.util.Random` **próprio de cada
*thread***, semeado de forma determinística a partir de `sementeThread`, **com sal próprio**.

**O que passa a ser reprodutível** (mesmo jar, mesma `--seed`, mesmo `--rep`, mesmo `--threads`): em
cada *thread*, a sequência de tipos de operação, a sequência de chaves Zipf (logo de *slots* da v3), a
sequência de decisões de *token* antigo e o rodízio de identidades de Login. Em outras palavras, a
sequência de **intenções** de cada *thread*, como prefixo de uma sequência infinita fixa.

**O que continua não reprodutível:** a intercalação entre *threads* (escalonamento e latência); as
latências; as respostas do *grid* e tudo o que depende delas (a geração do *slot* lida por um Validate
isolado, que depende de Logouts de outras *threads*; a tríade, que só segue depois de Login OK; o
sucesso de reposições; erros sob falha); o número de operações por *thread* e a fronteira da janela
de medição, definidos por tempo; os `sid` da tríade (`UUID.randomUUID()`, `SessionOps.java:582`); S3
não muda (já tem geradores por ator, `ContextoS3.java:32-35`).

Fora do escopo: `KeyGenerator`, `MisturaV3` (salvo Javadoc), `SessionOps`, CSV, linhas do stdout,
*dry-run*, `scripts/`, `analysis/`, `spec/`, `cluster/`.

## 2. Por que um sal próprio (e não `new Random(sementeThread)`)

O `KeyGenerator` da *thread* já é `new Random(sementeThread)` (`KeyGenerator.java:62`, via `:754`).
Semear o `Random` da mistura com o mesmo valor daria às duas fontes **o mesmo fluxo do LCG**: o sorteio
da operação n e a chave Zipf de índice ≈ n/2 sairiam do mesmo estado interno (a chave consome dois
`next()` por `nextDouble`, a operação um `next(31)`). Não é possível garantir viés mensurável, mas as
duas sequências deixam de ser independentes por construção. É o mesmo princípio de DT-008 para o
*token* (`MisturaV3.java:19-23`): um domínio por sal, com o finalizador do SplitMix64 espalhando
sementes vizinhas. O comentário de `MisturaV3.java:21-22` ("que o B-43.5 vai semear com a mesma
semente da thread") fica impreciso e é corrigido no commit `docs:`.

## 3. Prompt para o Worker

````text
Você é o WORKER. Uma entrega, B-43.5, commits só locais. NÃO faça push. NÃO use a VM.

Base: <BASE> = ponta de feature/controlador-integracao DEPOIS da integração do B-43.9 com a correção
F-1 (o Senior informa o SHA no despacho). Branch fix/b43-5-mistura-semeada, worktree ../wt-b43-5.
Antes de tudo: `mvn -q -o -f workload/pom.xml clean test` na base e anote o Surefire (arquivos,
testes, falhas, erros, ignorados); confira que as linhas citadas abaixo existem como descritas
(WorkloadMain.java:26, :735-741, :743-766, :753; Scenario.java:56-62; MisturaV3.java:19-23;
Cli.java:129-134; TokenAntigoFonteTest.java:46-52, :89-102). Se alguma divergir, PARE e reporte.

DESENHO EXIGIDO (WorkloadMain, tudo static e package-private, documentado):
  static final long SAL_MISTURA = 0x4D49535455524153L;      // "MISTURAS" em ASCII
  static long sementeMistura(long sementeThread) { return MisturaV3.mix64(sementeThread ^ SAL_MISTURA); }
  static Random randomDaMistura(long sementeThread) { return new Random(sementeMistura(sementeThread)); }
Em rodarCliente, a primeira linha passa a ser EXATAMENTE
  Random r = randomDaMistura(seed);
com o MESMO nome de variável `r` (TokenAntigoFonteTest.java:51 exige
`executarOperacao(ops, cenario, r, chaves, ids, v3);` no corpo) e sem tocar em
`new KeyGenerator(seed)` (:50) nem em `misturaV3.cliente(seed)` (:49). O import de
java.util.concurrent.ThreadLocalRandom (WorkloadMain.java:26) sai. Nada mais muda no laço, em
executarOperacao(es), em sementeThread, no stdout, no CSV ou no dry-run. NÃO mude a visibilidade de
rodarCliente (TokenAntigoFonteTest.java:48 procura `private static void rodarCliente\(`).
mix64 continua em MisturaV3 (não mova: TokenAntigoSemeadoTest e TokenAntigoFonteTest o referenciam).

ORDEM DOS COMMITS (todo commit compila: `mvn -q -o -f workload/pom.xml test-compile`):
 1. refactor: introduz SAL_MISTURA, sementeMistura (implementação final, função pura) e
    randomDaMistura, que NESTE commit ainda devolve ThreadLocalRandom.current() (ignora o
    argumento); rodarCliente passa a usar `Random r = randomDaMistura(seed);`. Comportamento
    idêntico; Surefire = base, verde.
 2. test: testes novos W1..W9 (arquivos NOVOS MisturaSemeadaTest.java e MisturaSemeadaFonteTest.java).
    Vermelhos esperados neste commit: W3, W4, W5, W6, W7 e a parte de W8 sobre randomDaMistura.
    Registre no relatório quais ficaram vermelhos e com que mensagem.
 3. feat: randomDaMistura passa a `return new Random(sementeMistura(sementeThread));`; remove o
    import de ThreadLocalRandom. Surefire = base + novos, verde.
 4. docs: Javadoc de WorkloadMain.sementeThread (:735-738, que passa a citar também a mistura),
    rodarCliente (:743-748), Scenario.proximaOperacao (:56-62: a produção usa um Random semeado por
    thread, não ThreadLocalRandom), MisturaV3 (:19-23: "o Random da mistura, semeado por
    WorkloadMain.sementeMistura com o sal SAL_MISTURA") e a descrição de --seed em Cli.java:131
    ("Semente base: gerador Zipf, mistura de operacoes e token antigo de cada thread, por
    sementeThread(seed, tid, rep)"). Só texto; nenhum teste fixa esses textos (confira com grep e,
    se algum fixar, PARE).
 5. test: smoke local (seção SMOKE) e o script de análise do smoke.
 6. docs: relatório docs/relatorio-b43-5-<data>.md.

TESTES NOVOS (JUnit 5; constantes congeladas calculadas por emulação INDEPENDENTE em Python de
java.util.Random e do SplitMix64, como em TokenAntigoSemeadoTest:32-33, não copiadas da saída Java):
 W1 derivação congelada: SAL_MISTURA == ByteBuffer.wrap("MISTURAS" US-ASCII).getLong();
    sementeMistura(s) == mix64(s ^ SAL_MISTURA) para s em {0, 1, 42, -1, Long.MIN_VALUE,
    sementeThread(42, 3, 1)}; sementeMistura(42) congelado; os 40 primeiros
    randomDaMistura(42).nextInt(100) congelados. Valores de referência do Senior (emulação própria,
    que reproduz SEMENTE_TOKEN_42 e DECISOES_40 de TokenAntigoSemeadoTest):
      sementeMistura(42) = -595519573436187269
      nextInt(100) x 40 = 55 54 10 39 89 66 29 23 82 18 80 94 8 53 76 3 55 38 63 4
                          17 74 1 91 62 40 1 70 26 22 62 85 61 82 42 0 17 40 74 91
    Se a SUA emulação der outro valor, PARE e reporte (não ajuste para bater).
 W2 domínios separados: para s em [0, 10^5): sementeMistura(s) != s, != MisturaV3.sementeToken(s),
    e sem colisão entre si. Para s = sementeThread(42, t, 1), t = 0..3: as 1000 primeiras saídas de
    nextInt(100) de randomDaMistura(s) diferem das de new Random(s) (fluxo do KeyGenerator) e das de
    new Random(sementeToken(s)) (fluxo do token) em pelo menos uma posição.
 W3 determinismo: dois randomDaMistura(s) dão os mesmos 10^4 nextInt(100); s e s+1 diferem em ao
    menos uma das 1000 primeiras. Dois Random obtidos de randomDaMistura(42) consumidos intercalados
    (A, B, A, B, ...) dão, cada um, a sequência de um randomDaMistura(42) sozinho (mata Random
    compartilhado/estático).
 W4 threads independentes: para t = 0..3 e s_t = sementeThread(42, t, 1), as sequências de
    Scenario.S1.proximaOperacao(randomDaMistura(s_t)) com 2,5·10^5 sorteios diferem duas a duas, e a
    fração de posições em que duas threads sorteiam VALIDATE ao mesmo tempo fica no IC de 99,9 % de
    p = 0,5² = 0,25 (|f − 0,25| ≤ 3,2905·√(0,25·0,75/n)).
 W5 distribuição: 10^6 sorteios de S1 e de S2 com randomDaMistura(sementeThread(42, 0, 1)): a
    frequência de cada Op fica no IC de 99,9 % do peso (Scenario.peso); S2 nunca sorteia
    RESET_FAILURES.
 W6 caminho de produção, reprodutível: chame o rodarCliente REAL por reflexão
    (getDeclaredMethod("rodarCliente", SessionOps.class, int.class, long.class, Scenario.class,
    long.class, long.class, AtomicLong.class, BooleanSupplier.class, VersaoMistura.class,
    MisturaV3.class), setAccessible(true)) com um OpsGravadas sobre FakeCaches (o mundo de MundoV3,
    1 thread), deadline = Long.MAX_VALUE, opsAlvo = 5000, parar = () -> false. Para S1 e S2, v2 e
    v3, tid em {0, 3} e seed = sementeThread(42, tid, 1): dois mundos novos dão a MESMA lista de
    chamadas (op, arg), com o arg mascarado só quando for UUID (sid da tríade). Com seed + 1, a lista
    difere. (Este teste FALHA no commit 2, porque ThreadLocalRandom não é reprodutível: registre.)
 W7 caminho de produção = composição declarada: no mesmo mundo, a lista de chamadas de rodarCliente
    (reflexão, seed s, tid t) é IGUAL à de WorkloadMain.executarOperacoes(ops, cen,
    randomDaMistura(s), new KeyGenerator(s), new IdentidadesMistura(t, mix), 5000, cliente) com
    cliente = mistura.cliente(s) em v3 e null em v2. Fixa que a mistura usa a semente da thread
    (não seedBase, não tid sozinho) e o mesmo s do KeyGenerator e do token.
 W8 repetição: randomDaMistura(sementeThread(42, 0, 0)) e (42, 0, 1) diferem nas 1000 primeiras
    saídas; idem para W6 com rep 0 × rep 1.
 W9 guardas de fonte (MisturaSemeadaFonteTest, DetectorFonte, fonte sem comentários): o corpo de
    rodarCliente contém `Random r = randomDaMistura(seed);` exatamente uma vez e não contém
    `ThreadLocalRandom`, `new Random(` nem `Math.random`; WorkloadMain, Scenario, KeyGenerator,
    IdentidadesMistura, MisturaV3 e SlotsGeracao não referenciam ThreadLocalRandom nem Math.random;
    randomDaMistura contém `new Random(sementeMistura(sementeThread))`.

TESTES EXISTENTES: o desenho acima foi escolhido para NÃO exigir nenhuma edição de teste existente.
Têm de ficar verdes SEM EDIÇÃO, e o relatório mostra `git diff --stat <BASE> HEAD -- workload/src/test`
só com arquivos NOVOS (A):
  TokenAntigoFonteTest.java:48-51, :76-78, :85, :93-101; SementeThreadTest.java:24, :28, :34-36;
  TokenAntigoSemeadoTest.java:41, :44, :47-49 (constantes do token inalteradas: o token não depende
  da mistura); SequenciaMisturaV3Test, SequenciaMisturaV2Test, GoldenS1S2Test.java:83 e o recurso
  golden-8e2e815 (os testes passam Random(42)/KeyGenerator(42) diretamente a executarOperacoes,
  caminho que não muda); ScenarioTest; MisturaV3Test; MisturaV3ConcorrenciaTest; CliTest;
  CliMisturaTest; CliMisturaV3Test (dry-run inalterado).
Se QUALQUER teste existente precisar de edição, PARE antes do commit e reporte arquivo:linha, a
asserção e o motivo: cada edição exige autorização do Gestor.

MUTANTES (script no scratchpad, Surefire completo por mutante, fonte restaurado no fim; todos
precisam morrer; liste os testes que mataram cada um):
 N1 randomDaMistura volta a ThreadLocalRandom.current()            → W3, W6, W9
 N2 sem sal: new Random(sementeThread)                              → W1, W2
 N3 sal do token: mix64(s ^ MisturaV3.SAL_TOKEN)                    → W1, W2
 N4 sem misturador: new Random(s ^ SAL_MISTURA)                     → W1
 N5 semente sem a thread: rodarCliente usa randomDaMistura(seed - tid) (= seedBase + rep·1 000 003)
                                                                    → W7
 N6 Random estático compartilhado em WorkloadMain, criado uma vez   → W3 (intercalado), W6
 N7 rodarCliente cria randomDaMistura(seed) mas passa new Random() a executarOperacao → W6, W9
 N8 sementeMistura com + em vez de ^                                → W1

SMOKE LOCAL (Podman, cluster b435-isnN com portas que não colidam com outro cluster ativo,
`podman ps -a` e `podman network ls` antes e depois, máquina devolvida ao estado inicial, SEM VM, jar
construído da árvore limpa no commit 4, SHA-256 em jars.json; seeds pela R-S2 com S0 = 44 500, fora
dos smokes anteriores e do rebaseline):
 P1 S1 sync v3 --threads 1 --duration 120 --warmup-min-sec 10 --seed 44500
 P2 idêntica a P1 (MESMA semente, de propósito: é o teste de reprodutibilidade; a checagem R-S3 do
    script isenta só o par P1/P2, explicitamente)
 P3 idêntica a P1 com --seed 44600 (controle negativo)
 P4 S1 sync v3 --threads 4 --duration 120 --warmup-min-sec 10 --seed 44700
 P5 S2 async v3 --threads 4 --duration 120 --warmup-min-sec 10 --seed 44800
 Todas com --mix v3 --reset-state on --ops 0 --rep 1.
Análise (runs/b43-5-smoke/analise.py, novo; NÃO edite os analise.py selados de smokes anteriores):
 - Reprodutibilidade (P1, P2, P3): CSV ordenado por op_id; cada evento vira (operation, key), com
   key trocada por "*" quando for UUID. Como a janela de medição é definida por tempo, cada CSV é
   uma janela contígua da mesma sequência infinita. Ache a primeira ocorrência dos 256 primeiros
   eventos de P1 em P2 (ou de P2 em P1); critério: existe, a sobreposição a partir dali tem
   ≥ 50 000 eventos e é 100 % igual. Para P1 × P3: nenhuma janela de 256 eventos de P1 ocorre em P3.
   Reporte o deslocamento achado e o tamanho da sobreposição.
 - P4, P5: C1..C9 com as MESMAS definições de runs/b43-9-smoke/analise.py (copie a parte S1/S2 para
   o arquivo novo), C3 pelo IC de DT-008, margem de fronteira = threads (DT-007; 1 em P1..P3),
   invalidacao_perdida = conflito_esgotado = 0, `Auditor I2:` toda zero e sem_marco = 0 em P4
   (SYNC, DT-010), exit 0 em todas. Rode também VerificadorSanidade em P1..P5.
 - test_analise_reprodutibilidade.py (unittest): sobreposição igual, sobreposição com um evento
   trocado (falha), janelas sem sobreposição (falha), UUID mascarado, arg sid- não mascarado.
Brutos em runs/b43-5-smoke/ com MANIFEST.sha256. "Smoke local: as latências NÃO são evidência."

RELATÓRIO docs/relatorio-b43-5-<data>.md: Surefire por commit (com os vermelhos do commit 2),
arquivo:linha antes e depois, as constantes de W1 com a emulação usada, mutantes, smoke
(deslocamento e sobreposição de P1 × P2, controle P3, tabela C1..C9 de P4/P5), `git diff --stat`
dos testes (só A), desvios e pontos para o Gestor. Ao terminar, liste SHAs, worktree e
`git status`, e pare. Não integre nem faça push.
````

## 4. Critérios de aceite do B-43.5

1. Surefire = base + testes novos, 0 falhas, 0 erros, o mesmo ignorado; **nenhum teste existente
   editado** (`git diff --stat <BASE> HEAD -- workload/src/test` só com arquivos novos).
2. Cada um dos 6 commits compila; o commit `test:` tem os vermelhos esperados e só eles.
3. N1..N8 mortos; W1 com constantes congeladas iguais às de referência da seção 3.
4. `GoldenS1S2Test`, `SequenciaMisturaV2Test`, `SequenciaMisturaV3Test` e as constantes de
   `TokenAntigoSemeadoTest` verdes sem edição.
5. *Smoke*: P1 × P2 com sobreposição ≥ 50 000 eventos 100 % igual; P1 × P3 sem sobreposição;
   P4/P5 com C1..C9 atendidos; exit 0 em todas.
6. Nada de `SessionOps`, `KeyGenerator`, CSV, stdout ou *dry-run* alterado.

## 5. Edições de testes existentes exigidas

**Nenhuma.** Conferido em `96c8045`:

| Teste existente | Linha | O que exige | Por que continua verde |
|---|---|---|---|
| `TokenAntigoFonteTest.java` | `:48` | assinatura `private static void rodarCliente(` | visibilidade não muda |
| `TokenAntigoFonteTest.java` | `:49`, `:50` | `misturaV3.cliente(seed)` e `new KeyGenerator(seed)` no corpo | linhas não mudam |
| `TokenAntigoFonteTest.java` | `:51` | `executarOperacao(ops, cenario, r, chaves, ids, v3);` | a variável continua `r` |
| `TokenAntigoFonteTest.java` | `:76-78`, `:85` | `MisturaV3` sem `ThreadLocalRandom`; `executarOperacao` não sorteia de `r` além de `proximaOperacao(r)` | `MisturaV3` e `executarOperacao` não mudam (só Javadoc, removido pelo detector) |
| `TokenAntigoFonteTest.java` | `:93-101` | `final long seedThread = sementeThread(cli.seedBase, tid, rep);` uma vez | `executarRepeticao` não muda |
| `SementeThreadTest.java` | `:24`, `:28`, `:34-36` | fórmula de `sementeThread` | não muda |
| `TokenAntigoSemeadoTest.java` | `:41`, `:44`, `:47-49` | constantes do *token* | o *token* não depende do `Random` da mistura |
| `GoldenS1S2Test.java` | `:83` | sequência com `Random(42)` injetado | o caminho com `Random` injetado não muda |
| `CliTest`, `CliMisturaTest`, `CliMisturaV3Test` | — | *dry-run* e `Configuracao:` | stdout não muda |

Duas escolhas do desenho existem só para manter essa tabela vazia: o nome `r` e a visibilidade
`private` de `rodarCliente` (o W6 usa reflexão em vez de abrir o método). Se o Worker propuser
alterá-las, cada uma vira edição de teste existente e precisa de autorização do Gestor.

## 6. Efeito no *rebaseline* e no texto

- O jar do *rebaseline* (DT-009) inclui o B-43.5; a reprodutibilidade declarada é a da seção 1.
- Frase para a Metodologia (a adaptar pelo Escritor): "o tipo de cada operação, a chave sorteada e a
  decisão de ler o *token* antigo vêm de três geradores próprios de cada *thread*, derivados da
  semente da execução, do índice da *thread* e da repetição, com sais distintos; duas execuções com a
  mesma semente produzem, em cada *thread*, a mesma sequência de intenções. A intercalação entre
  *threads*, as respostas do *grid* e as latências não são reprodutíveis."
- Depois da validação, o Senior registra a derivação (`SAL_MISTURA`, `sementeMistura`) como DT nova.
