# Validação V-W2a: atuador REST do *cluster* e política por assinatura (2026-11-05)

> Senior, 2026-11-05. Branch `feature/b44-2-atuador-cluster`, worktree `wt-w2a`, ponta `7536c88` sobre `e869a31`
> (que contém `19d45b9`). Commits da primeira rodada `93238be`, `431f4a7`, `28a1409`, `68dc72e`, `0b153f5`; da segunda
> `db98466`, `4f74c26`, `18ab8b9`, `7ae4c17`, `2bf6cff`, `7536c88`. Especificação: seção 8 de
> `docs/acoes-controlador-cluster-2026-11-05.md` (`cc08644`), ajustes de `docs/opcoes-arquitetura-configuracao-2026-11-05.md`
> (`155aa95`) e as decisões do coordenador registradas no TD-011 (`docs/implementacao.md`), no P-36..P-38
> (`docs/registro-de-problemas.md`) e no `docs/testes.md`.
>
> **Validação feita só por leitura.** A VM rodava a noite 1 do *rebaseline* com o host em silêncio estrito. Não rodei
> Maven, Python, Podman nem *cluster*. Li o código, os *diffs* e os brutos. Os únicos cálculos foram `sha256sum -c` do
> `MANIFEST.sha256` (18 arquivos pequenos) e a soma dos XML do Surefire que já estavam em `workload/target/`. O que
> precisa ser reproduzido está na seção 12.

## Veredito

**APROVADO COM RESSALVAS.** O código faz o que a seção 8 pede, e as escolhas que a tabela 5 não fixa são defensáveis.
Não achei defeito que leve a ação errada nem violação do P-5. Nenhuma asserção de comportamento foi retirada nas edições
de teste. Os três desvios declarados são aceitos.

As ressalvas são de contrato, de medida e de procedimento. Duas delas (R2 e R3) são erros da minha especificação que o
código herdou ou que a leitura dos números expõe, e mexem no I-2 e na condição de viabilidade do AC-5. A integração
I-1..I-5 continua pendente. A liberação para a calibração depende do I-2 (D-B) e de uma execução do P1 de ponta a ponta
depois do W-4, que o `SmokeAtuador` não substitui (seção 11).

## 1. O que conferi

| Ponto | Resultado |
|---|---|
| Histórico | linear, 11 commits, sem *merge*, nenhum em `origin/*`; pares TDD na ordem `test:` → `feat:`/`refactor:` |
| Arquivos tocados | `controle/` (10), `session/` (3 de `main`), testes (15), `docs/` (4), `runs/b44-2/` (7 versionados). `analysis/`, `spec/`, `scripts/`, `cluster/` sem mudança |
| Surefire | XML de 02:53 (depois de `2bf6cff`, 02:52): **971 testes, 0 falhas, 0 erros, 1 pulado, 133 arquivos**. Por arquivo bate com o `testes.md` (`AtuadorRestInfinispanTest` 15, `PoliticaPorAssinaturaTest` 16, `LigacaoControladorAtuadorTest` 3, `RotulosDosNosTest` 2, `ParametrosPoliticaArquivoTest` 6, `GoldenS1S2Test` 3 verdes) |
| `analysis` 235 OK | não conferido (Python proibido na janela); `analysis/` não muda no *diff* |
| *grep* do item 10 (`ProcessBuilder\|podman\|sudo` em `controle/`, `AtuadorRestInfinispan`, `ClienteAdminRest`) | vazio |
| `set-mutable-attribute` em `workload/src/main` | ausente (R-ARQ-3) |
| `java.io` de arquivo em `controle/` e no adaptador | ausente |
| `MANIFEST.sha256` | 18/18 OK |

## 2. Edições em testes existentes

Contei `assert` e `@Test` antes (`e869a31`) e depois (`7536c88`):

| Arquivo | `assert` | `@Test` |
|---|---|---|
| `controle/ExecutorDeAcoesTest` | 117 → 117 | 18 → 18 |
| `controle/LacoDeControleTest` | 95 → 95 | 14 → 14 |
| `controle/ControleArquivosTest` | 38 → 39 | 6 → 6 |
| `session/ArquiteturaSemAcaoNoClienteTest` | 4 → 4 | 1 → 1 |
| `session/WorkloadMainCargaControleTest` | 30 → 30 | 5 → 5 |

Li cada trecho alterado. As mudanças são de rótulo (`AC-1`/`AC-3` → `AC-2` em *caches* distintos, `AC-5` onde a chave
precisa de alvo), o auxiliar `resumo` passou a imprimir `tipo/primeiro cache`, o que deixa a asserção de ordem LIFO mais
estrita, e a linha 217 troca o teste por tipo por um teste por chave, com o mesmo efeito. As linhas batem com a tabela do
`testes.md`. Um único valor esperado mudou de sentido. `ControleArquivosTest:141` passou de `"DEGRADED_MODE"` para
`valor` nulo, o que tira a única conferência da serialização de `valor` não nulo. Nenhum tipo do braço B usa `valor`,
então isso não é asserção de comportamento retirada (O6). A lista do `ArquiteturaSemAcaoNoClienteTest` ganhou o par do
atuador (D-C) e três tipos na lista do `LigacaoControlador` (P-38). Isso afrouxa um teste de arquitetura e foi
autorizado pelo coordenador, não pelo Gestor diretamente (seção 13).

## 3. `PoliticaPorAssinatura` contra a tabela 5

| Linha da tabela 5 | Código | Teste |
|---|---|---|
| CONSERVADOR, vista completa, S_lag no teto com o mesmo `noSuspeito` em k janelas, suspeito respondeu, AC-5 não usada → AC-5 | `vistaCompleta() && persistencia >= k && respondeuEmTodas(suspeito)` e `usadasP1 < cotaAc5`; cota esgotada dá `P0:cota-p1-esgotada`, como o losango Q do fluxograma | `p1`, `ac5NuncaDuasVezes` |
| CONSERVADOR, um nó sem resposta, os outros respondendo, sem vistas divergentes → AC-2 se não ativa | `noSemResposta()` (um `NENHUMA`, os outros `TODAS`) e `!vistasDivergentes()`; ativa dá `P2:ac2-mantida` | `p2` |
| CONSERVADOR sem assinatura → nenhuma ação, motivo `sem-assinatura` | `P0:sem-assinatura` na linha `politica` (a tabela diz linha `acao`; a linha nova foi aprovada no P-37/P-38) | `p0SemAssinatura` |
| vistas divergentes → nenhuma ação; AC-2 ativa revertida | reversão em qualquer perfil; P2 exige `!vistasDivergentes()` | `divergentes`, `ac2NuncaComDivergencia` |
| AC-2 ativa e o nó volta, ou `T_reb` → reverter | `respondeuEmAlguma(ac2No)` → `no-voltou`; `fimNs − início ≥ tRebNs` → `teto-treb`, e o mesmo nó não recebe AC-2 até responder de novo | `reversaoNaVolta`, `reversaoNoTeto`, `normal` |
| R4 ou encerramento → reverter a pilha, AC-5 segue | no laço e no executor (a política não é consultada na janela do R4) | `ExecutorNaoReversivelTest.r4NaoDesfazAc5`, `LacoDeControleTest` |
| *shadow* → as mesmas linhas, `executada=false` | política igual; executor grava `PREVISTA` | `shadowAc5`, `shadowSemPedido` |

A persistência conta janelas seguidas consultadas com `lagMaxMs ≥ tetoLagMs` e o mesmo `noSuspeito`, e zera num salto de
`fimNs` maior que W (janela do R4). `EstadoDefasagem` indeterminado (`lagMaxMs` nulo) nunca dispara, como pede o item 4.
A classe é pura: não tem E/S nem relógio, e o tempo vem do `fimNs`. Os parâmetros não têm valor padrão no código.

Concordo com as seis escolhas do TD-011, com duas observações. A escolha (5), vistas divergentes por faixas disjuntas,
pode ler como divergência uma troca de vista 3→2 vista em instantes diferentes quando a janela tem uma amostra só por nó
(O2). O efeito é conservador, porque a AC-2 deixa de ser aplicada ou é revertida. A escolha (6), que conta como "parou"
a saúde que não responde em 1 s, aceita um nó só lento como parado. O risco é baixo porque o 204 já veio.

## 4. `ExecutorDeAcoes`: ação não reversível e `T_enc`

- O AC-5 nunca entra em `ativas()` nem na pilha. Por isso `reverter`, `reverterTudo`, o encerramento e o gancho não o
  alcançam. É pedido uma vez por chave (`naoReversiveisPedidas`) e, quando falha, não tem reversão nem nova tentativa.
  A cota de 1 por execução é da política. O executor sozinho aceitaria AC-5 em dois nós diferentes, e a defesa contra
  isso é só a política (`ac5NuncaDuasVezes`), o que basta.
- A linha `acao` sai com `reversao: "orquestrador"` no AC-5 e `null` no AC-2 (`FormatoJsonl.acao`).
- O prazo vai por tipo: AC-2 com `T_cham` = 2 s e AC-5 com `T_cham` + 3 s = 5 s. O `chamar` passa o prazo do tipo ao
  atuador.
- `T_enc` = prazo em curso + soma dos prazos de reversão da pilha + `T_cham`. Com só AC-2 isso é o mesmo `(A + 1)·T_cham`
  da V-W3, contando a aplicação em curso. Com AC-5 em curso, ela entra com 5 s e não como mais um elemento de A. É a
  leitura certa de "A conta só a pilha". A fórmula literal da seção 5.1 cortaria a espera de um AC-5 em curso e daria
  `executor_no_prazo = false` sem motivo. A ordem de leitura (em curso antes da pilha) está correta pelo modelo de
  memória do Java. Só que o pior caso agora é **9 s** (AC-2 na pilha + AC-5 em curso + `T_cham`), e o contrato ainda diz
  ≤ 8 s com AC-1/AC-3 (R1).

## 5. `AtuadorRestInfinispan` e `ClienteAdminRest`

- **P-5 e R-ARQ-1.** Não usam `ProcessBuilder`, `Runtime`, `java.nio.file`, `podman`, `sudo`, Hot Rod, `RemoteCache` nem
  `S3Clientes`. Só `java.net.http.HttpClient`, HTTP/1.1, sem redirecionamento. As credenciais não vão a log.
- **AC-2.** O `POST /rest/v2/container?action=disable-rebalancing` é a forma global, nunca a por *cache*. Vai ao primeiro
  nó da última amostra que respondeu, com metade do prazo restante, e passa ao seguinte se ele não responder. A leitura de
  volta de `rebalancing_enabled` (container e os dois *caches* do modo) é feita **nos 3 nós** em paralelo. O resultado é
  `APLICADA` com ≥ 2 nós completos e nenhum valor divergente. Um `true` lido dá `FALHOU`, e o executor reverte. A
  reversão segue o mesmo caminho e é idempotente.
- **AC-5.** O pedido só sai se o alvo estiver entre os que responderam na última amostra. Sem isso dá `FALHOU` sem
  nenhum pedido (risco 3), e a política exige ainda `respondeuEmTodas` na janela. O pedido é
  `POST /rest/v2/server/?action=stop` ao próprio alvo, nunca `/rest/v2/cluster` (N-2). Exige 204, depois a saúde
  anônima do alvo tem de parar de responder dentro do prazo, e a dos outros dois tem de responder 200. A leitura de volta
  cobre os 3 nós. O reverter devolve `NAO_EXECUTADA`.
- **Prazo.** Todo pedido e toda leitura usam `limite − agora`. `esperar` cancela no estouro e propaga a interrupção.
- **Digest.** HA1 = H(usuário:realm:senha), HA2 = H(método:uri com *query*), `response` = H(HA1:nonce:nc:cnonce:auth:HA2),
  `algorithm` e `qop` como *token*, `cnonce` e `opaque` entre aspas, `nc` crescente por `host:porta`. Bate com a RFC 7616
  e com o desafio real (`a-autenticacao.txt`: MD5, `qop=auth`, `opaque`). Um 401 com desafio novo refaz o pedido uma vez.
  O teste usa o *parser* do próprio cliente para ler o cabeçalho, mas calcula o resumo de forma independente. A prova
  real é a tarefa zero (d). Fica um risco não exercido. Pedidos paralelos ao mesmo nó podem chegar com `nc` fora de
  ordem, e o servidor falso não confere a ordem (O4).

## 6. Sem o bloco `politica`

O comportamento é o do W-3. `LigacaoControlador.pecas` monta o `AtuadorNulo` e `PoliticaDeAcao.NENHUMA` (teste
`LigacaoControladorAtuadorTest.semBloco`). A sonda e os prazos não mudam. O laço passa a chamar `resumirComNosAte`, que consome as mesmas
amostras que `resumirAte` e só acrescenta o mapa por nó. O núcleo não lê esse mapa, e o JSONL de observáveis não o
grava. As invariantes do `ResultadoNo` (respondeu ⇒ `numeroDeNos ≥ 0`) impedem que o construtor novo do
`EstadoTopologia.No` lance exceção no laço. O texto do `--dry-run` não muda.

O **formato** das decisões muda de forma aditiva: `esquema` passa a `decisoes-controlador/1.2` e a `meta` ganha
`parametros_politica: null`. Sem o bloco não há linha `acao` nem `politica`, porque a política `nenhuma` nunca pede nada.
Nenhum código de `analysis/` ou `scripts/` compara a cadeia do esquema. Então "idêntico ao W-3" vale para o
comportamento, e o formato é a versão 1.2 declarada.

## 7. Tarefa zero: números contra os brutos

| Declarado | Bruto | Confere |
|---|---|---|
| REST só com Digest | `a-autenticacao.txt`: um desafio Digest MD5 `qop=auth`; Basic 403; Digest 200; saúde anônima 200 | sim |
| `server?action=stop` 204 e parada em ~3,06 s | `b-parada-sem-barra.txt`: 204 em 13 ms; última saúde +3005 ms; primeira recusa +3059 ms (`RemoteDisconnected`) | sim, para os conectores. A saída do processo não tem instante absoluto do pedido no arquivo; pelo log (`ISPN080002` 05:33:14,997, que é pedido + 3 s pelo código) e pelo `exited` (05:33:17,164) fica em ≈ +5,2 s, por inferência |
| `rebalancing_enabled` por *cache* segue o global | `c-rebalancing-enabled.txt`: 4 *caches* × 3 nós, `false` depois do `disable` e `true` depois do `enable`, coordenador isn2 | sim |
| AC-2 de 18 a 182 ms | `d-atuador-ac2-*.txt`: 182,2; 30,0; 21,1; 19,9; 102,6; 19,8; 22,8; 18,0 ms, todos `APLICADA` 204 | sim |
| AC-5 em 3,19 s | `d-atuador-ac5.txt`: 3188,6 ms, `isn3=parou`, os outros `HEALTHY`, maioria com 2 nós logo depois | sim |

Autorização: o XML não tem `<authorization>` no `cache-container`, então é a "autorização desligada" da seção 3.1 com
autenticação Digest ligada. A leitura do Worker está certa.

## 8. Os três desvios

1. **Linhas fora da lista literal da D-C: aceito.** Sem trocar os rótulos das asserções de texto, a retirada de
   `AC_1`/`AC_3` reprovaria 13 asserções do `ExecutorDeAcoesTest` e outras do `LacoDeControleTest` e do
   `ControleArquivosTest` (P-36). As contagens da seção 2 mostram que nada foi retirado. Os testes LIFO continuam
   distinguindo a ordem com três chaves reversíveis. O artifício de "três AC-2 em *caches* diferentes" não é um cenário
   real, porque o AC-2 é global (O12).
2. **Bloco opcional aninhado: aceito.** Mantém `ParametrosControlador.CAMPOS`, mantém válidos os arquivos de hoje, entra
   no SHA-256 do arquivo (`param_sha256`) e é estrito por dentro: todo campo obrigatório, sem padrão, com `origem` não
   vazia para cada um, e `"politica": null` recusado. Ausência do bloco não é valor padrão, é o braço sem atuador. O
   risco é operacional. Uma execução do braço B sem o bloco vira W-3 sem aviso, então o W-6 tem de conferir a `meta`
   (O10).
3. **`parametros_politica` só nas decisões: aceito.** Os observáveis não mudam e ficam na 1.1. O `param_sha256` das duas
   `meta` cobre o arquivo inteiro.

## 9. Ressalvas

### R1 (antes do W-6): contrato do `T_enc` desatualizado

`docs/contrato-jsonl-controlador-v1.md`, seção "Prazo do encerramento", ainda diz `T_enc = (A + 1) × T_cham` e "com AC-1,
AC-2 e AC-3, `A_max` = 3 e `T_enc` ≤ 8 s". Com o W-2a, a fórmula é a da seção 4 e o pior caso é **9 s** (AC-2 na pilha +
AC-5 em curso + `T_cham`). O requisito do W-6 passa a `timeout -k K` com K > 9 s mais folga. O texto do `--dry-run`
(`prazo_encerramento=(A+1)x2000000000`) também ficou inexato.

### R2 (antes do I-2; erro meu na especificação): atraso da reversão do AC-2

A seção 3.2 e a tabela 5.1 dão `P_sonda + tick + T_cham` (≈ 5 s) para a reversão na volta do nó. A política, porém, só é
consultada no fechamento da janela (`LacoDeControle.processar`). O limite real é
`P_sonda + prazo_sonda + W + tick + T_cham` = 2 + 1,5 + W + 1 + 2 s, ou seja, **≈ 11,5 s com W = 5 s**. O `MERGE3` está
com `min_interval = 10000` e `max_interval = 30000`. A configuração não exclui uma fusão antes da reversão, e as fusões
medidas (18,9 s no local) só mostram folga típica. O D-B continua sendo o teste certo, mas precisa de três ajustes:

- a pausa do I-2 tem de ficar entre o T_det e o fim do teto. A REST do nó pausado para de responder na hora, e o P2
  exige uma janela inteira sem resposta, então o AC-2 entra entre W e 2W + tick depois do início da pausa (com o núcleo
  já em CONSERVADOR), e o teto vence 60 s depois disso. Com pausa maior que ~65 s, quem reverte é o teto, antes da
  volta, e o teste não exercita a corrida. Sugiro **50 a 55 s**, acima do maior T_det local (46,8 s);
- comparar com o limite de ≈ W + 6,5 s, não com 5 s;
- **três repetições**, não uma. Com n = 1 a corrida pode passar por sorte.

Alternativa de desenho, que exige decisão do Gestor: reverter o AC-2 fora da janela, no *tick*, quando a última amostra
mostrar o nó ausente de volta. O limite cairia para ≈ `P_sonda + prazo_sonda + tick + T_cham` (≈ 6,5 s).

### R3 (antes do relatório e do pré-registro): definição de `t_par`

O `testes.md` chama de `t_par` o intervalo do 204 até o fim dos conectores, ≈ 3,06 s, que inclui o atraso de 3 s do
`Server.java:208`. A condição de viabilidade da seção 5.1 é `k·W + 3 s + t_par < 39,8 s`, com `t_par` **sem** os 3 s.
Com o número do Worker na fórmula, os 3 s contam duas vezes. É preciso fixar uma das duas formas: `t_par ≈ 0,06 s` no
local, ou a fórmula reescrita como `k·W + t_parada < 39,8 s` com `t_parada ≈ 3,06 s`.

### R4 (procedimento): brutos fora do git e relatório ausente

11 das 18 entradas do `MANIFEST.sha256` (`verificacao/*.txt`, `*.log` e `mutantes.txt`) estão no `.gitignore`
(`runs/**/*.txt`, `*.log`). Só o *manifest* está versionado. Se a worktree for removida, os brutos se perdem. Fazer um `tar`
com SHA-256, como no h4, ou `git add -f`. Falta também o `docs/relatorio-b44-2-<data>.md` do item 10. Pode sair depois do
I-1..I-5, mas tem de existir, com a conta 905 + 56 − 1 + 11 = 971, os desvios e as medições.

### R5 (reproduzir): mutantes da primeira rodada

O `mutantes.txt` mostra rótulos `AC-1`/`AC-3`, ou seja, os sete mutantes rodaram sobre `68dc72e`. A segunda rodada mexeu
no enum, no executor (por meio dos testes) e no formato. Rodar de novo os sete sobre `2bf6cff` na próxima janela. O
custo é baixo e fecha o item 7 na ponta real.

### R6 (decisão antes da calibração): dois `k` no mesmo arquivo

O `controlador-*.json` passa a ter o `k` do núcleo (janelas altas para R2) e o `politica.k` (janelas de S_lag no teto
para o AC-5). A seção 5.1 da especificação fala em "`k`, `W`, `lag_in`, `lag_out` da calibração" sem separar os dois. O
pré-registro tem de dizer se são o mesmo número, e então o arquivo tem de impor isso, ou se são independentes. Nesse
caso, sugiro renomear para `kLag` enquanto a 1.2 não tem dado.

### R7 (W-4 e calibração): `tetoLagMs` amarrado ao valor que o W-4 grava

O P1 só dispara se `lagMaxMs ≥ tetoLagMs`, e o W-4 grava a leitura não vista com `lagMaxMs` igual ao prazo Hot Rod
(seção 5.1). Nada no código liga os dois. Um `tetoLagMs` maior que esse valor desliga o P1 sem aviso. Pôr um teste no W-4,
ou uma conferência no orquestrador, que exija `tetoLagMs ≤` o valor de "não viu".

## 10. Observações sem bloqueio

- **O1.** A linha `politica` não traz o nó (suspeito do P1, ausente do P2). Acrescentar `no` antes de qualquer dado,
  porque é aditivo e facilita auditar `no-voltou` e `teto-treb`.
- **O2.** Vistas divergentes com uma amostra por nó na janela (escolha 5): contar os motivos `vistas-divergentes` nas
  execuções de queda do I-1. Se aparecerem, a regra alternativa é "algum nó que responde informa menos membros do que o
  número de nós que respondem".
- **O3.** Falha do AC-2 não tem limite de novas tentativas. Uma falha persistente refaz `disable` → `FALHOU` → `enable` a
  cada janela, e o rebalanceamento fica ligando e desligando. Contar `acoes_falhas` no I-1.
- **O4.** Digest com `nc` fora de ordem em pedidos paralelos ao mesmo nó: contar 401 por fase no I-1. O código refaz uma
  vez com o desafio novo.
- **O5.** Corrida curta, que já existia na V-W3: uma aplicação que passa no teste de `encerrando` logo antes do
  encerramento pode ficar fora do `T_enc` lido. A janela é de nanossegundos.
- **O6.** A serialização de `valor` não nulo ficou sem conferência (seção 2). É opcional repor com um AC-2 de valor
  sintético.
- **O7.** Textos velhos: TD-011 com "961/0/0/1"; Javadoc do `ClienteAdminRest` com "tarefa zero (a, ainda não
  executada)"; mensagem "a política pediu AC-1" em `WorkloadMainCargaControleTest:143`.
- **O8.** A cota e o conjunto de parados são marcados no planejamento, mesmo se o AC-5 falhar. Isso é coerente com "uma vez
  por execução", e um `FALHOU` com o nó parado mesmo assim (POST sem resposta mas executado) tem de ser lido pelo
  orquestrador.
- **O9.** O detector do R-ARQ-2 não cobre `java.io.File*`. Nenhuma classe usa hoje. Endurecer é opcional.
- **O10.** O W-6 deve abortar uma execução do braço B cuja `meta` não traga `politica = por-assinatura` e
  `parametros_politica` não nulo.
- **O11.** O teto efetivo é `⌈T_reb/W⌉·W` mais o *tick*. Com W = 5 s dá os 60 s exatos. Declarar no pré-registro se W
  calibrado não dividir 60 s.
- **O12.** A chave do AC-2 inclui os *caches*. Dois AC-2 com listas diferentes seriam ações distintas no executor e o
  mesmo sinalizador no *cluster*. A política só monta uma instância, então isso não acontece. Registrar no Javadoc.
- **O13.** `feature/w4-sonda-defasagem` aponta hoje para `7536c88`. Correções do W-2a devem entrar como commits nesta
  branch antes de o W-4 divergir, ou o W-4 faz *rebase*.

## 11. Plano I-1..I-5 e o AC-5 forçado pelo `SmokeAtuador`

**I-1 e I-2 pelo caminho real** (`--controller active` com o bloco `politica`): certo. O P2 dispara de ponta a ponta
(`LigacaoControladorAtuadorTest.pontaAPonta`). No arquivo de integração, `k` e `tetoLagMs` vão com
`"origem": "integracao"`, porque o P1 não dispara sem W-4. O I-2 segue os ajustes da R2: pausa de 50 a 55 s, limite
≈ W + 6,5 s e três repetições. O I-1 conta também os motivos de O2, as falhas de O3 e os 401 de O4, e na execução com o
coordenador derrubado lê `rebalancing_enabled` nos vivos (P-34).

**I-3, I-4 e I-5 com o AC-5 forçado pelo `SmokeAtuador`: aceito, com condições.** O Worker leu certo. Sem o W-4 o
`EstadoDefasagem` é indeterminado e o item 4 proíbe o P1, então a única forma de medir agora o mecanismo da seção 3.1
(`t_par`, recusa, saída, vista da maioria, riscos 1 e 3) é forçar a ação. Isso mede o Infinispan e o adaptador. Não mede a
decisão (política, executor, guarda da janela e linha `acao`). As condições são estas.

1. O `SmokeAtuador` hoje imprime só a duração. Ele tem de gravar `Instant.now()` em epoch-ns no pedido e no retorno, no
   mesmo relógio do `EpochClock` da carga, para a contagem à parte das violações em `[t_pedido, t_pedido + 5 s]`.
2. Um *poller* em paralelo, no estilo do `parada.py` e com relógio de parede, registra o 204, a primeira recusa da saúde,
   a saída do contêiner (do lado do *harness*) e a linha `ISPN000094` da maioria.
3. No I-3, conferir antes da ação que a REST do isn3 cortado continua respondendo (N-11).
4. Cada cenário tem uma execução de controle sem AC-5.
5. No I-5, o `() -> todos` do `SmokeAtuador` pula de propósito a guarda "respondeu na última janela". O relatório tem de
   dizer que o caminho de produção recusaria o pedido sem enviar nada (`ac5NoSemResposta`) e que o I-5 mede o perigo que
   a guarda evita. Declarar o prazo usado: o de produção (5 s no total, 2,5 s no POST) ou os 2 s da seção 8.
6. **Depois da integração do W-4, uma execução do P1 de ponta a ponta (I-3b) é obrigatória antes da calibração**, com o
   controlador decidindo e as linhas `politica` P1 e `acao` AC-5 com `reversao: "orquestrador"`.

## 12. O que fica para a janela seguinte (H2)

1. `mvn test` na ponta, com 971/0/0/1 em 133 arquivos esperado, e `unittest discover -s analysis` com 235 OK e 1 *skip*.
2. Os sete mutantes sobre `2bf6cff` (R5).
3. `tar` dos brutos de `runs/b44-2/` com SHA-256 (R4).
4. I-1..I-5 com os ajustes das seções 9 e 11.

## 13. Pontos que precisam de validação do Gestor

| ID | Decisão | Recomendação do Senior |
|---|---|---|
| V-1 | Ratificar a D-C ampliada (P-36) e a mudança na lista do `LigacaoControlador` no `ArquiteturaSemAcaoNoClienteTest` (P-38), autorizadas pelo coordenador em nome do Gestor | ratificar; as contagens da seção 2 mostram que nenhuma asserção saiu |
| V-2 | I-2 com pausa de 50 a 55 s, limite ≈ W + 6,5 s e 3 repetições (R2) | aceitar |
| V-3 | Reversão do AC-2 no *tick*, fora da janela (R2, alternativa) | decidir depois do I-2; se uma das 3 repetições der fusão antes da reversão, implementar antes de tirar o AC-2 do braço B |
| V-4 | `politica.k` igual ao `k` do núcleo ou independente; renomear para `kLag` (R6) | independente e renomeado, decidido antes da calibração |
| V-5 | Forma de `t_par` no relatório e no pré-registro (R3) | `t_parada` (204 → fim dos conectores) com os 3 s dentro, e a fórmula reescrita como `k·W + t_parada < 39,8 s` |
| V-6 | I-3b, P1 de ponta a ponta depois do W-4, como condição da calibração (seção 11) | aceitar |
| V-7 | Campo `no` na linha `politica` (O1), aditivo na 1.2 antes de qualquer dado | aceitar, junto com o W-4 |

## 14. Próxima tarefa sugerida para o Worker

Na branch `feature/b44-2-atuador-cluster`, commits locais, sem *push*:

1. `docs:` contrato do `T_enc` (R1), `t_par`/`t_parada` (R3, depois da V-5), textos de O7; `fix:` texto do `--dry-run`.
2. `test:` + `feat:` do campo `no` na linha `politica` (O1), se a V-7 for aprovada.
3. Na janela H2, a seção 12 e o `docs/relatorio-b44-2-<data>.md`.
4. `SmokeAtuador` com epoch-ns (seção 11, condição 1) antes do I-3.
