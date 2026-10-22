# Validação V-B43.3a-i: verificador offline de sanidade da mistura, modo strict (2026-10-22)

> Senior, 2026-10-22. Validação independente da entrega do Worker na branch
> `fix/b43-3-guarda-sanidade-mistura` (worktree `wt-b43-3`), commits `b568f2c..6ce301a` sobre `8296270`,
> relatório `docs/relatorio-b43-3a-i-2026-10-22.md`. Especificação: Apêndice A de
> `validacao-b42-1-2026-10-22.md`, bloco "B-43.3a, PARTE i". Decisões do Gestor: modo strict, saídas
> 0 = OK, 4 = FALHA e 2 = uso inválido, limiares provisórios aceitos. Nada foi alterado em `wt-b43-3`.
> Todos os testes, execuções e mutantes rodaram em cópias `git archive` no *scratchpad*. Nenhum
> *cluster* subiu e a VM não foi usada. Commit local nesta branch, sem push.

## 1. Veredito

**APROVADA COM RESSALVAS.** O código faz o que a especificação pede, e todas as alegações medidas
conferem com o relatório. Conferi Surefire, golden, execução documental, manifesto e V1..V4. As
ressalvas são de cobertura de teste, não de comportamento:

1. **R1. Colunas por posição não são detectadas (M4 sobrevive, inclusive ao S2).** Todas as fixtures e
   todos os CSVs reais usam a ordem canônica `op_id,operation,start_ns,end_ns,replica,return_code,key[,phase]`.
   Se `operation`, `return_code` e `phase` fossem lidos pelas posições fixas 1, 5 e 7, nenhum teste
   perceberia. A especificação exige leitura "por NOME do cabeçalho". O código atual cumpre isso, mas
   o teste não trava a exigência. Correção: um teste com as colunas permutadas, por exemplo
   `return_code,key,operation,op_id,...`, que espere o mesmo veredito e as mesmas contagens da fixture
   canônica.
2. **R2. O SHA-256 só é verificado pelo S2, que é opt-in (M3 sobrevive à suíte padrão).** A suíte
   padrão só checa `length() == 64`. Sem o `DigestInputStream`, o JSON grava o hash do vazio
   (`e3b0c442…b855`) e a suíte continua verde. O S2 mata o mutante, mas só roda com
   `-Dsanidade.csvJunho`. Correção barata: o S1 (recorte de 5 000 linhas, versionado) pode comparar o
   `sha256` do JSON com `3fd92d2c…94f8`, que já está no `LEIAME.md`.
3. **R3 (observação, sem defeito).** `LOGIN_OK_BAIXO` usa OK/(OK+BLOCKED), como a especificação pede.
   Por isso, Login `ERROR_*` na região "fora" não aparece em nenhuma regra. Uma janela "fora" com
   1 Login OK e 10 000 Login `ERROR_TRANSPORT` passaria nessa regra. Na prática, `MISTURA_DESVIO` e
   `VALIDATE_*` tendem a pegar o caso, porque Login `ERROR_*` não gera a tríade. Mesmo assim, avaliar
   isso na parte ii (seção 6, item 2).

R1 e R2 cabem num único commit `test:` na própria branch, antes da integração. Não mexem em código de
produção.

## 2. Medido × alegado

| # | Alegação do relatório | Medido pelo Senior | Confere? |
|---|---|---|---|
| 1 | Surefire `8296270`: 305/0/0/0, 39 arquivos | `git archive 8296270`, `mvn clean test`: exit 0, **305/0/0/0, 39** | sim |
| 2 | Surefire HEAD: 337/0/0/1, 44 arquivos | `git archive 6ce301a`, `mvn clean test`: exit 0, **337/0/0/1, 44** | sim |
| 3 | O único *skip* é o S2 opt-in | O único `<skipped>` é `SanidadeRegressaoJunhoTest.s1F1Rep001CompletoContagensExatas`, mensagem "System property [sanidade.csvJunho] does not exist" | sim |
| 4 | S2 com `-Dsanidade.csvJunho` passa: 2 testes, 0 falhas | Rodei com `…/tcc-desenvolvimento/runs/S1-F1/rep-001.csv`: exit 0, `tests=2 failures=0 errors=0 skipped=0` (0,462 s e 0,012 s) | sim |
| 5 | `Scenario` só ganhou o acessor `peso` | `--name-status 8296270..HEAD`: o único `M` é `Scenario.java`, +15/−0, só `public int peso(Op op)` com `switch` exaustivo. Pesos e sorteio intocados | sim |
| 6 | Golden idêntico | `git diff --stat 8296270..HEAD -- GoldenS1S2Test.java workload/src/test/resources/golden-8e2e815`: vazio. `GoldenS1S2Test` verde na suíte | sim |
| 7 | Recorte = `head -n 5001` do original, SHA `3fd92d2c…94f8`. Original `5ea464ae…a198` | `sha256sum` do recurso e de `head -n 5001` do original: os dois dão `3fd92d2c…94f8`. Original: `5ea464ae…a198` | sim |
| 8 | Execução documental: 20/20 com saída 4 | Rodei o *jar* (OpenJDK 22) sobre **4** CSVs do baseline da VM: `S1-none/rep-002`, `S1-F3/rep-001`, `S2-F1/rep-003` e `S2-F3/rep-002`. Os quatro saem com **4**. O S2 cobre o de junho. Os 20 JSON versionados têm `veredito=FALHA` | sim |
| 9 | Login OK = 0 e Validate VALID = 0 nos 20 | Nos 20 JSON: `login_ok=0`, `validate_valid=0`, `eventos_dentro=0`. Nos meus 4: idem. Contagem independente em Python (`csv.DictReader`) = `contagens.fora` do meu JSON = `contagens.fora` do JSON versionado | sim |
| 10 | SHA-256 de cada CSV correto | Nos meus 4: `sha256` do JSON = `hashlib` = JSON versionado. `FONTES.sha256 -c` (a partir de `tcc/`): 20/20 OK | sim |
| 11 | Motivos `LOGIN_OK_BAIXO`, `VALIDATE_VALID_ZERO` e `VALIDATE_AUSENTE_ALTO`, sem `MISTURA_DESVIO` | Lista idêntica nos 4 que rodei e nos 20 JSON. Maior \|desvio\| dos meus 4: 0,169 p.p. (`S1-F3/rep-001`) | sim |
| 12 | `MANIFEST.sha256`: 21 linhas, 21 OK | `sha256sum -c MANIFEST.sha256`: **21 OK**, 0 falhas, 21 linhas | sim |
| 13 | V1 morto: 337/1/0/1, `loginOkNaFronteiraExataNaoDispara` | Reproduzido: 337/1/0/1, mesmo teste | sim |
| 14 | V2 morto: 337/10/0/1 | Reproduzido: 337/10/0/1 (`SanidadeFaseTest`, `fixtureSaDaZeroOkSemMotivos` etc.) | sim |
| 15 | V3 morto: 337/1/0/1, `[NOT_FOUND]` | Reproduzido: 337/1/0/1, `seisPorCento…[2]` (`NOT_FOUND`) | sim |
| 16 | V4 morto: 337/3/0/1 | Reproduzido: 337/3/0/1, os três testes citados | sim |
| 17 | D1: *amend* local, só doc do recurso | `git diff --stat 833ceef b568f2c`: só `.gitattributes` e `LEIAME.md` do recurso (+5/−4). Nenhum ref remoto contém `b568f2c`, e não há branch b43-3 no `origin` | sim |
| 18 | `git status` da worktree limpo | `wt-b43-3`: limpo | sim |

Não remedi o Surefire commit a commit (`b568f2c`, `728c85c`, `6e67807`). As pontas `8296270` e
`6ce301a` conferem, e o histórico TDD (seção 3 do relatório) é coerente com a ordem dos commits.

## 3. Mutantes do Senior

Cada mutante foi aplicado numa cópia `git archive 6ce301a` e testado com o Surefire completo
(`mvn clean test`). M3 e M4 também rodaram com o S2 opt-in.

| ID | Mutação | Suíte padrão | Teste que mata | Com S2 |
|---|---|---|---|---|
| M1 | Região "fora" conta `durante` (sai de `FASES_DENTRO`, entra em `FASES_FORA`) | **morto**, 337/2/0/1 | `SanidadeFaseTest.regioes`, `foraRuimNaoEMascaradoPorDentroBom` | n/a |
| M2 | `MISTURA_DESVIO` compara \|obs − esp\| (fração) com 2,0 em vez de p.p. | **morto**, 337/1/0/1 | `validateDeslocadoTresPpDaMisturaDesvio` | n/a |
| M3 | SHA-256 não calculado (sem `DigestInputStream`, grava o hash do vazio) | **sobrevive**, 337/0/0/1 | nenhum (só `length()==64`) | **morto**: `expected <5ea464ae…> but was <e3b0c442…>` |
| M4 | Colunas por posição (`iOp=1`, `iRc=5`, `iFase=7`), mantendo a checagem de nomes | **sobrevive**, 337/0/0/1 | nenhum | **sobrevive** (o CSV real tem a ordem canônica) |
| M5 | `JANELA_VAZIA` ignorada | **morto**, 337/1/0/1 | `csvSoComCabecalhoDaJanelaVazia` | n/a |
| M6 | `DENTRO_SEM_VALID` conta `ERROR_*` como não-erro | **morto**, 337/1/0/1 | `dentroTodoErrorNaoDaDentroSemValid` | n/a |
| M7 | Região "dentro" nunca avaliada | **morto**, 337/1/0/1 | `dentroSemValidComEventosNaoErrorDaDentroSemValid` | n/a |
| M8 | `VALIDATE_AUSENTE_ALTO` com `>=` | **morto**, 337/1/0/1 | `cincoPorCentoDeValidateAusenteNaFronteiraNaoDispara` | n/a |

Seis de oito morrem na suíte padrão, e M3 morre no S2. M4 é a lacuna real (R1).

## 4. Desvios D1..D8

| ID | Avaliação |
|---|---|
| D1 (*amend* local) | **Aceito.** Confirmado que só mudou a documentação do recurso, antes de qualquer publicação. Não houve reescrita de histórico remoto |
| D2 (`skipped = 1`) | **Aceito.** É o S2, exigido como opt-in |
| D3 (dois `.gitattributes` locais) | **Aceito.** Necessário para os SHA do recorte (CRLF preservado) e do manifesto no Windows. Não toca o da raiz |
| D4 (campos extras no JSON) | **Aceito.** Todos os campos pedidos estão presentes. `modo` e `limiares.status` ajudam a auditoria. Na parte ii, `percentis.py` deve ler por chave, não por posição |
| D5 (saída 2 mais estrita) | **Aceito.** Coerente com strict. Uma ressalva menor: linhas vazias no meio do CSV são puladas em silêncio, e não dão 2. Inofensivo (o `EventCsvWriter` não as gera), mas pode ser registrado |
| D6 (Login só `ERROR_*` ⇒ fração 0 ⇒ `LOGIN_OK_BAIXO`) | **Aceito.** Escolheu o lado que falha. Ver R3 sobre `ERROR_*` misturado com OK |
| D7 (`FONTES.sha256`) | **Aceito.** Amarra o JSON à origem e confere 20/20 a partir de `tcc/` |
| D8 (`feat:` com teste e implementação juntos) | **Aceito.** É o par TDD por commit pedido, e os vermelhos estão documentados |

## 5. Recomendações para as três questões em aberto

1. **Operação desconhecida: sair com 2, sem diluir.** Hoje uma operação fora das cinco entra em `ops`
   e dilui as frações. Se a fração dela for pequena (< 2 p.p., por exemplo um CSV S3 com poucos
   `Unblock` passado como `--scenario S1`), o resultado é um **OK silencioso** sobre um arquivo que não
   é do cenário declarado. Em modo strict isso é exatamente o que não pode acontecer. Recomendo que
   uma `operation` fora do conjunto do cenário seja "arquivo inválido" (saída 2, mensagem com o nome e
   a linha), com um teste S6 novo. `return_code` continua livre (`ERROR_*` é esperado). Pode entrar no
   mesmo commit de endurecimento de R1/R2, porque é uma mudança de uma regra só no
   `VerificadorSanidade`. Se o Gestor preferir não tocar no código agora, o lugar é a parte ii, antes
   de ligar a guarda *online*.
2. **Limiares: manter os provisórios até a calibração, depois congelar antes da matriz.**
   - Mistura ±2 p.p.: manter. O ruído amostral observado nos CSVs reais fica entre 0,008 e 0,19 p.p.,
     uma ordem de grandeza abaixo da tolerância, inclusive nas execuções F3 curtas (cerca de 65 000
     eventos).
   - Validate VALID > 0 e ausente ≤ 5 %: manter como estão.
   - Login OK ≥ 0,95: **depende de B-43.2.** Em S1 a mistura nominal tem IncrementFailure 10 contra
     ResetFailures 5. Algum BLOCKED pode ser legítimo numa execução sã, conforme a política de
     identidades que B-43.2 fixar. Calibrar com a primeira execução pós-B-43.2 (uma repetição de
     `S1-none` e uma de `S2-none`). Fixar o limiar abaixo do mínimo observado, com margem documentada.
     Registrar o valor em `decisoes-tecnicas.md` **antes** de rodar a matriz, para que o limiar fique
     pré-registrado e não seja ajustado depois de ver os resultados.
   - R3: na calibração, medir também a fração de Login `ERROR_*` na região "fora". Se for relevante,
     acrescentar uma regra (por exemplo `LOGIN_ERRO_ALTO`) na parte ii.
3. **Região "fora" com marcadores: manter a definição atual como provisória e travá-la na parte ii,
   com dados reais.** A definição atual ("fora" = sem `phase`, `antes` ou `pos-convergencia`) segue o
   ponto 4 da seção 8 da V-B42.1, e M1 mostra que os testes a protegem. Os 20 CSVs não têm `phase`,
   então a parte "dentro" só tem cobertura sintética. Recomendo duas coisas:
   - um rótulo novo de fase continua dando saída 2, como já ocorre. Assim, uma mudança em
     `Marcadores.Fase` obriga a decidir a região conscientemente;
   - o primeiro F1/F3 pós-B-43.2 com `phase` deve rodar o verificador e registrar
     `eventos_fora`/`eventos_dentro`, para conferir que `recuperacao` em "dentro" não deixa a janela
     "fora" pequena demais nas execuções F3 curtas. Se deixar, reavaliar se `recuperacao` passa para
     "fora".

## 6. Pontos para o Gestor

1. Aprovar a entrega **com ressalvas**. Antes de integrar, um commit `test:` na própria branch com:
   (a) colunas permutadas (R1); (b) SHA exato do recorte no S1 (R2); e, se aprovado no item 2, (c)
   operação desconhecida ⇒ 2.
2. Decidir a questão 1: saída 2 agora (recomendado), ou na parte ii.
3. Aprovar o protocolo de calibração da questão 2: primeira execução pós-B-43.2, limiar congelado em
   `decisoes-tecnicas.md` antes da matriz.
4. Aprovar a questão 3: definição atual mantida, com conferência no primeiro F1/F3 com `phase`.
5. Integração e push continuam pendentes de aprovação explícita. Nada foi publicado.
