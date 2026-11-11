# Validação do W-3 (B-28.2): laço do controlador, `--controller` e trilha JSONL (2026-11-04)

Senior, validação independente. Branch `feature/w3-laco-controlador` (worktree `wt-w3`) sobre `098900f`.
Commits validados: `12cca92`, `ae0e8fb`, `a011caa`, `24bef83`, `715c449`, `f820e73` (código) e `b883397`
(docs). Tarefa: prompt W-3 de `docs/plano-execucao-2026-11-04.md`, seções 4 e 9. Relatório do Worker:
`docs/relatorio-w3-2026-11-04.md`. Contrato: `docs/contrato-jsonl-controlador-v1.md`.

**Parecer: APROVADO COM RESSALVAS.** O código faz o que o prompt pede, e o que o Worker declarou bate com o
que medi. Antes de qualquer execução que gere dados, é preciso corrigir R1 (instante errado em
`t_fim_carga_ns`) e R2 (prazo de encerramento sem origem). O *smoke* local (R5) continua pendente: sem ele o
item 8 do aceite não fecha.

## 1. O que conferi e o resultado

| # | Verificação | Como | Resultado |
|---|---|---|---|
| 1 | Diff | `git diff --stat 098900f..HEAD`: 44 arquivos, +4461/−31. Li `ExecutorDeAcoes`, `LacoDeControle`, `AtuadorCluster`, `AcaoCluster`, `PoliticaDeAcao`, `TrilhaJsonl`, `LigacaoControlador` (abertura, ouvintes, classificação), o diff de `WorkloadMain`, `SessionOps`, `InvariantAuditor` e `ConvergenciaLoop` | coerente com as seções 4.1, 4.4.3 e 4.5 do plano; ressalvas R1 e R4 |
| 2 | Surefire | `mvn -q -T1 clean test` (exit 0) e soma dos `TEST-*.xml` | **894/0/0/1 em 123 arquivos**, igual ao declarado |
| 2b | Python | `python -m unittest discover -s analysis` | **187 OK, 1 *skip***, igual |
| 3 | `shadow` nunca chama o atuador | leitura: em `SHADOW`, `aplicar`, `reverter`, `reverterTudo` e `encerrarCom` só chamam `previsto(...)`, e `iniciar()` volta sem criar *thread* nem gancho. A única chamada ao atuador em shadow é `atuador.nome()`. `chamar()` só existe na *thread* executora. Mutante meu: chamar `atuador.aplicar` no ramo shadow de `aplicar` | **morto**: 3 falhas em `ExecutorDeAcoesTest` (`shadowNuncaChama`, `shadowIdempotente`, `shadowEncerrarPreveReversao`) e 2 em `LacoDeControleTest` |
| 4 | Reversão LIFO | `ArrayDeque.push` + iteração a partir do topo em `REVERTER_TUDO`; em shadow, `Collections.reverse` da ordem de inserção. Mutante meu: inverter a iteração da pilha | **morto**: `activeLifo`, `ganchoDeDesligamento`, `r4ReverteTudo` |
| 4b | LIFO na exceção da carga | `WorkloadMain`: `try { carga S1/S2 ou S3 } finally { auditor.definirOuvinte(null); controle.close(); }`. `close` → `LacoDeControle.encerrar` → `ExecutorDeAcoes.encerrar` (`REVERTER_TUDO` + `FIM`, com prazo) | correto por leitura. **Não há teste** que lance exceção na carga e confira a reversão (nota N1) |
| 4c | LIFO no gancho da JVM | gancho registrado só em `active` (`iniciar`), com `encerrarCom(GANCHO_JVM, false)`. O encerramento normal o remove, e a corrida com um desligamento em curso cai no `encerrado` (no-op). A executora é *daemon*, mas continua rodando enquanto os ganchos rodam. A trilha continua aberta nesse caminho, porque `LacoDeControle.encerrar` não rodou | correto; `ganchoDeDesligamento` cobre com dublê. `pecasPadrao` usa `Gancho.jvm()` |
| 5 | *Golden* e `S3SimulacaoTest` | `git diff 098900f..HEAD` em `GoldenS1S2Test`, `GoldenRodarClienteTest`, `S3SimulacaoTest` e `src/test/resources` | **sem diferença**; os três passam na suíte |
| 6 | As duas edições de teste | diff completo dos dois arquivos | ambas aceitas (seção 2) |
| — | Testes apagados | `git diff --name-status` em `src/test`: só `A`, mais os dois `M` | nenhum apagado |

## 2. As duas edições de teste

- **`TiposBasicosTest.ordemDeSeveridade`.** Mudou só a lista esperada de `Motivo.values()` (e o
  `DisplayName`). As asserções `maisSeveroQue` seguem iguais. O prompt pedia ao mesmo tempo
  `Motivo.DEFASAGEM` e nenhuma edição nos testes do núcleo, e os dois pedidos se contradizem neste teste, que
  fixa o enum inteiro. `maisSeveroQue` compara por `ordinal()`. Inserir `DEFASAGEM` entre `TOPOLOGIA` e
  `ERRO` não inverte nenhum par antigo, e nenhum ordinal é persistido (o JSONL grava nomes). A posição está
  certa: a defasagem é o sinal que antecede a detecção da partição (plano 4.2), então é mais severa que erro
  ou latência e menos que topologia já anômala. **Aceita.**
- **`ArquiteturaSemAcaoNoClienteTest` (R-C1).** A lista de tipos virou um mapa (classe de `session` → tipos
  permitidos de `controle`). A regra ficou **mais estrita**: antes, `LACO_AUTORIZADO` pulava a classe
  inteira. Agora uma classe não listada não pode usar nenhum tipo, e `LigacaoControlador` só pode usar os 23
  tipos listados, nenhum deles de ação sobre o cliente. É exatamente a ressalva 4 da validação do W-1.
  `WorkloadMain` não importa `controle` (conferi). **Aceita.**

## 3. Ressalvas

### R1 (corrigir antes de gerar dados): `t_fim_carga_ns` medido depois do encerramento do controlador

Em `WorkloadMain`, `long fimCargaNs = relogio.paraEpochNanos(System.nanoTime())` vem **depois** do
`finally` que chama `controle.close()`. Em `active`, esse `close` inclui a reversão de toda a pilha, até
`PRAZO_ENCERRAMENTO_NS`, mais o *join* do laço, até 5 s. O Javadoc de `escreverResumo` diz que
`t_fim_carga_ns` é o "instante em que as threads de carga pararam", e o valor gravado não é esse. Há um
efeito ainda maior: `fimCargaNs` também é o `tFimNs` de reserva da convergência quando falta o marcador
(S1/S2 com controlador). Com isso, o tempo de convergência do braço `active` passa a ser medido a partir de
um instante mais tardio que o do braço `shadow`, cujo `close` é imediato. É um viés **entre braços**.

Correção: ler `fimCargaNs` no início do `finally`, antes de `controle.close()`. Se for útil, gravar à parte
um `t_fim_controle_ns` (aditivo) depois do `close`. A convergência continua começando depois da reversão
(ponto 2 do Worker, correto); só muda o instante de referência. Teste: carga com um controlador falso cujo
`close` demora X, e conferir que `t_fim_carga_ns` fica antes do início do `close`.

### R2 (corrigir antes de gerar dados): prazo de encerramento de 10 s

Classificação no item 4 (ponto 6 do Worker).

### R3: período do *tick*

Classificação no item 4 (ponto 6 do Worker). Nada a corrigir no código. Falta a medida, e um campo aditivo a
torna barata.

### R4 (menor, pode ir com o W-2): pedido que some da trilha

Se a reversão de uma ação falha (por R4 ou pela política), a ação sai de `ativas` mas fica na `pilha`. Quando
a política volta a pedir a mesma ação, `aplicar` soma `pedidas++` e enfileira, e `processarAplicacao` vê
`naPilha(a)` e retorna **sem gravar linha**. O resultado é um pedido contado em `acoes_pedidas` sem linha
`acao` correspondente. Correção: gravar a linha (resultado `JA_ATIVA`, ou contar em `ignoradas`) e manter a
conservação "pedidas = linhas APLICAR". Com o atuador nulo isso não acontece, porque nada falha.

### R5: *smoke* local pendente (item 8 do aceite)

A justificativa é aceitável: o *cluster* `b441` está em uso pelo B-44.1b. Mas o W-3 só fecha o aceite com o
*smoke* S3 ASYNC de 120 s em `shadow` e `active` (zero transições em `shadow`) e com a partição local
forçada. Isso é pré-requisito do W-6 e da calibração N3, não da integração do código: com
`--controller off`, *golden* e S3 não mudaram.

### Notas (sem bloqueio)

- **N1.** Falta um teste de `WorkloadMain`/`ligarControlador` com exceção na carga que confira
  `controle.close()` e a linha `fim` nos dois JSONL. O `finally` está certo por leitura.
- **N2.** `LacoDeControle.encerrar` usa `t.join(5 s)`, um número que o relatório não declara. Ele não decide
  comportamento: depois do *join*, o encerramento toma `travaTick` e de qualquer modo espera o *tick* em
  curso terminar. Basta um comentário dizendo isso, ou trocar por `join()` sem prazo.
- **N3.** Se `encerrar` do executor estourar o prazo, as linhas `acao` tardias caem em
  `TrilhaJsonl.descartadas`, mas esse contador não aparece em nenhuma linha `fim`. Ou se grava
  `trilha_descartadas` no stdout de `linhaResumo`, ou se aceita que `executor_no_prazo = false` já sinaliza o
  caso.

## 4. Ponto 6 do Worker: as duas convenções sem medida

Regra do autor: número que decide comportamento precisa de origem rastreável (bibliografia do projeto,
decisão registrada, derivação ou medida).

### 4.1 Prazo de encerramento = 10 s: **sem origem** (convenção). Substituir por derivação.

Esse número decide se a reversão termina antes da convergência e se o gancho da JVM chega a desfazer tudo. Não
há fonte para ele. Uma derivação está disponível:

```
T_enc = (A + 1) × T_cham
```

Aqui `T_cham` = 2 s, o prazo de cada chamada, que já tem origem (B-26.2, plano 4.4.2). `A` é o número de ações
que podem estar na pilha, e o `+1` cobre a chamada em curso quando o encerramento chega.

- **Forma recomendada (dinâmica):** o executor calcula o prazo no próprio encerramento, a partir do tamanho
  da pilha mais as aplicações na fila: `T_enc = (|pilha| + 1) × T_cham`. Assim ele espera só o necessário e
  não fica nenhum número solto.
- **Forma fixa (se o Gestor preferir):** pela tabela 4.5, as chaves possíveis são AC-1, AC-2 e AC-3
  (`A ≤ 3`), o que dá `T_enc = 4 × 2 s = 8 s`. Os 10 s atuais passam nesse limite, mas por acaso, e a conta
  muda se o B-44.1b acrescentar uma ação.

Há duas condições que vão para o contrato do W-2 e do W-6:

1. `T_cham` limita a chamada **inteira** do atuador (o pedido para todos os *caches* da ação e a leitura de
   volta), não cada pedido HTTP. Se o adaptador fizer um pedido por *cache* e por nó com 2 s cada, a
   derivação deixa de valer. Hoje o Javadoc de `AtuadorCluster` diz só "respeitar `prazoNs`". Falta deixar
   explícito que o prazo vale para a chamada toda.
2. O gancho da JVM só serve se o orquestrador (W-6) der ao processo, entre o SIGTERM e o SIGKILL, uma
   folga maior que `T_enc`. Isso vira requisito do W-6, em `timeout -k` ou equivalente.

### 4.2 *Tick* = W/5: **decisão registrada, com o fator 5 sem derivação**. Efeito limitado e mensurável.

Origem: `docs/validacao-b26-1-2026-10-22.md` §5.9 (`dc17f6a`, aprovado pelo Gestor em 22/10). O texto diz:
"`Δ = 1 s` (`W/5`). O atraso de fechamento fica limitado a `W + L + Δ`". A decisão registra o valor e a
cota, mas não explica por que o fator é 5. Pela regra do autor, a origem é "decisão registrada", e é
defensável. No texto do TCC, porém, ela precisa vir acompanhada da cota e da medida abaixo.

**O *tick* afeta a latência de decisão, e o efeito é limitado.** A janela `[ini, fim)` fecha no primeiro
*tick* com `agora ≥ fim + L` (regra do agregador). O atraso entre o fim da janela e a decisão é, portanto,

```
D = L + U,   U ∈ [0, Δ)   (mais o custo de processar o tick, desprezível)
```

O atraso total de entrada do AC-1, contado a partir do início da anomalia, soma
`atraso_sonda (≤ P_sonda + prazo_sonda = 3,5 s) + alinhamento e persistência (≤ k·W) + L (0,1 s) + U (≤ Δ) +
chamada (≤ T_cham = 2 s)`. A fração da janela do M1 coberta (plano 4.4.2) passa a ser
`≈ 1 − (k·W + L + Δ + 3,5 s + 2 s) / T_det`. O *tick* responde por no máximo `Δ / T_det`. Com `W = 5 s`
(Δ = 1 s) e `T_det ≈ 40 s` (medido: 43,5 a 46,4 s), isso dá **cerca de 2,5 % da janela do M1**, menos que o
custo da sonda (3,5 s) e o da chamada (2 s).

**Por que reduzir Δ não custa nada em atraso de eventos, e por que não reduzi-lo mesmo assim.** Um Δ menor
fecha a janela mais perto de `fim + L`, e com isso um evento registrado com atraso maior que `L` passaria a
ser contado como atrasado. Mas o pior atraso de registro medido foi de 9,43 ms, contra `L` = 100 ms (V-B26.1,
memória de 22/10), de modo que Δ não muda a contagem de `atrasados`. O único custo de um Δ menor é CPU e
linhas `tick` no JSONL. O ganho máximo, de 1 s para 0, é de 2,5 % da janela do M1, abaixo da resolução da
sonda (período de 2 s). Uma regra derivável que justifica o valor atual seria `Δ ≤ P_sonda / 2`: o *tick*
nunca é o termo dominante do atraso de leitura de S_top. Com `P_sonda` = 2 s, isso dá `Δ ≤ 1 s`, e `W/5` com
`W = 5 s` cumpre a regra. **Atenção:** se a calibração (N3) escolher `W > 10 s`, `W/5` passa de `P_sonda/2`.
Nesse caso o Senior recomenda `Δ = min(W/5, P_sonda/2)`, e a mudança vai ao Gestor antes do pré-registro.

**Como medir (calibração N3, `shadow`, sem custo extra):**

1. Acrescentar à linha `decisao` o campo `t_decisao_ns`, o instante `tempo.agoraNs()` em que `processar`
   rodou. É aditivo (contrato v1 → v1.1, antes de qualquer dado). Sem esse campo, a medida só existe para as
   janelas fechadas por *tick*, pelo `agora_ns` da linha `tick`, e para as que geraram `acao`, pelo
   `t_pedido_ns`. As janelas fechadas por evento ficariam sem medida.
2. Na análise, calcular a distribuição de `D = t_decisao_ns − t_fim_ns` por execução. O esperado é
   `D ∈ [L, L + Δ]`, com média perto de `L + Δ/2` quando a carga é baixa. Valor fora disso é defeito.
3. Relatar o máximo de `D` e a sua parcela no atraso de entrada do AC-1 medido de ponta a ponta (do marcador
   do corte até `t_pedido_ns` da primeira `acao` AC-1). Esse número substitui a convenção no texto.

### 4.3 Sonda com período de 2 s e prazo de 1,5 s

Decisão validada no B-26.2. Tem origem.

## 5. Os outros pontos do Worker (1 a 5 e 7)

O coordenador aceita esses pontos, e não encontrei erro técnico neles, com uma exceção: o ponto 2
(encerramento no fim da carga, antes da convergência) é correto como desenho, mas a implementação desloca
`t_fim_carga_ns` (R1). O ponto 4 (violação classificada no *tick*) usa o instante em que a violação foi vista
(`b.tNs()` em `classificarPendentes`), como o relatório afirma. O H8 (seção 4.3 do relatório) não se aplica,
e concordo.

## 6. A interface do atuador acomoda "trocar alias" (B-44.1b)?

**Sim, sem refatoração grande.** O executor, o laço, a pilha e a trilha não mudam. Os pontos de mudança são
estes:

| Peça | Mudança |
|---|---|
| `AcaoCluster.Tipo` | uma constante nova. **Usar `AC-4`**, porque `AC-5` já está reservado no plano 4.4 para "parar o servidor suspeito". Um cache criado em tempo de execução seria outra constante (`AC-6`) |
| forma da ação | `new AcaoCluster(AC_4, List.of("sessions"), null, "sessions-sync")`: `caches` = o alias, `alvo` = `null` (vale para o *cluster*), `valor` = o cache de destino. A `chave()` ignora o valor, o que basta para a idempotência |
| reversão | `reverter(acao)` volta ao valor do XML. O adaptador do W-2 resolve qual era o cache original (de `lerConfiguracao()` ou do XML), o que já é o contrato atual de `AtuadorCluster.reverter` |
| ação composta | "criar `sessions-sync` e depois apontar o alias" vira duas ações em ordem. A pilha LIFO dá a ordem certa de reversão: primeiro o alias volta, depois o cache é apagado |
| testes e contrato | `ExecutorDeAcoesTest` ("rótulos AC-1..AC-3") e a lista de valores de `acao.tipo` em `contrato-jsonl-controlador-v1.md` ganham o novo rótulo (mudança aditiva) |

Há limites que **não são de interface** e que o Senior precisa analisar antes de o W-2 usar a ação:

1. **P-30 vale para o alias.** A tabela do B-44.1 (linha `aliases`, `ctr-alias`) mostra que `aliases` é um
   atributo mutável. Pelo achado 3 (§4.3), qualquer mudança de atributo vai para `data/caches.xml`, e o nó
   não reinicia mais (`ISPN000438`), mesmo depois de voltar ao valor do XML (P8). Um cache criado em tempo de
   execução também entra em `persistentCaches`. Nas células com `Reviver`/reinício de nó, a ação quebra a
   própria injeção de falha. A reversão pela API não basta: é preciso a camada 3 (W-6) recriar o *cluster*.
2. **Os dados não acompanham o alias.** As sessões gravadas em `sessions-async` não estão em
   `sessions-sync`. Depois da troca, leituras falham ou ficam defasadas. Na reversão, as escritas feitas no
   cache SYNC desaparecem para o cliente. A ação pode **gerar** violações I1, I2 e I4 por conta própria, e o
   argumento de refinamento do plano 4.7 ("só remove passos habilitados") deixa de valer para ela. Seria
   preciso modelá-la no TLA+, ou copiar o estado, o que não cabe em uma chamada de 2 s.
3. **O cliente Hot Rod.** O B-44.1b precisa mostrar se um `RemoteCache("sessions")` já aberto passa a usar o
   novo destino sem reconectar e sem cache de topologia velho do lado do cliente. Se não passar, a ação só
   tem efeito numa execução nova.
4. **Duração.** Se trocar o alias exigir mais de uma chamada ou transferência de estado, o prazo
   `T_cham = 2 s` por chamada e a derivação de `T_enc` (4.1) têm de ser refeitos para essa ação.

## 7. Pontos que precisam de decisão do Gestor

1. R1 e R2 vão como correção do Worker nesta branch, antes da integração? O Senior recomenda que sim: são
   pequenas e mudam número gravado.
2. Prazo de encerramento: forma dinâmica `(|pilha| + 1) × T_cham` (recomendada) ou fixa de 8 s?
3. Campo aditivo `t_decisao_ns` na linha `decisao` (contrato v1.1), para medir o efeito do *tick* na
   calibração.
4. Regra `Δ = min(W/5, P_sonda/2)`, a confirmar se a calibração escolher `W > 10 s`.
5. Ação "trocar alias": só entra no W-2 depois da análise da seção 6, itens 1 e 2. Ela gera violações
   próprias e esbarra em P-30.

## 8. Próxima tarefa sugerida para o Worker

Na mesma branch (`feature/w3-laco-controlador`), em commits `test:` e depois `fix:`:

1. R1: `fimCargaNs` lido antes de `controle.close()`, com teste de controlador lento.
2. R2: prazo de encerramento derivado (forma escolhida pelo Gestor), com teste de pilha com 3 ações e
   atuador que gasta o prazo inteiro em cada chamada. Deixar explícito no Javadoc de `AtuadorCluster` que
   `prazoNs` limita a chamada inteira.
3. R4: linha `acao` (ou contagem `ignoradas`) na re-aplicação de ação cuja reversão falhou, com teste.
4. Se o Gestor aprovar: `t_decisao_ns` na linha `decisao`, contrato v1.1 e teste de contrato.
5. N1: teste de exceção na carga com `controle.close()` e linhas `fim`.
6. Surefire completo com a contagem, e o *smoke* (R5) quando houver *cluster* livre.

Sem push.
