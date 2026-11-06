# Relatório do orquestrador da avaliação fatorial 2×2 (DT-027) (2026-11-06)

Branch `feature/orquestrador-fatorial` (worktree `wt-fat`), a partir de `edfa8b6` (ponta de
`feature/controlador-integracao`). Especificação: complemento 1 do pré-registro do *benchmark* refeito (DT-027,
`wt-bench`, `docs/pre-registro-benchmark-refeito`, `4fa4546` e `f6965ae`), seções 1 (desenho), 3 (células
descritivas) e 5 (o que o orquestrador precisa ter). Nomes alinhados com a análise do W-5e (`wt-w5e`,
`feature/w5e-fatorial`, `d1bb887`, TD-021), a pedido do coordenador. Sem *push*, sem integração. A A1 (`wt-a1`) e a A4
(`feature/p1-ignorar-cache`) ficam de fora e entram depois, na integração. Desenho em `docs/implementacao.md`
(TD-022, número provisório).

## O que foi feito

| Item do pedido | Onde | O quê |
|---|---|---|
| 1. Variante do *cache* por braço | `campanha_s3_matriz.py`, `run-campanha-s3.sh` | coluna `fusao` (`pnn`, `a1`) no fim da matriz fatorial; o `sessions-async` de cada execução sai do `sessions-async.json` ou do `sessions-async-a1.json`; `fusao` no `execucao.json` e no `checagem.json`; os dois JSON no *bundle* (`cluster/caches/*.json`) e o SHA-256 do arquivo do braço em `fusao_config`; a política lida de volta (`?action=config`) e a linha `A1-POLITICA carregada` conferidas nos três nós; se não ficou, `DESCARTADA-INFRA` sem carga, e a fase segue |
| 2. Matriz com 4 braços por bloco | `campanha_s3_matriz.py gerar --modo fatorial`, `campanha_s3_conferir.py pareamento` | PO, PL, AO e AL na célula de teste, A e B nas descritivas, ordem sorteada por bloco e reprodutível; o conferidor recusa bloco incompleto, braço repetido, mistura, semente, célula ou papel diferentes, controlador ou fusão incoerentes com o braço; o `repor` refaz o bloco fatorial com os quatro |
| 3. JAR da extensão no *bundle* | `run-campanha-s3.sh` e roteiro (seção "Roteiro do *bundle*") | o orquestrador lê o volume do *compose*, exige o JAR com `fusao=a1`, confere `--extensao-sha256` e o põe no *bundle* e no `execucao.json`; a construção (`mvn`) fica no roteiro |
| 4. Checagem de fusão antes da varredura | `EsperaFusao.java`, `Cli.java`, `WorkloadMain.java` | `--sweep-merge-timeout-sec`: antes de varrer, a saúde REST dos três nós até a mesma vista de 3 membros; sem fusão no teto, `varredura-rep-NNN.erro.txt`; o instante da checagem em `t_vista_checagem_ns` |

Com a matriz de 13 colunas (calibração, avaliação do B-40) **nada muda**: o formato da matriz, o *bundle*, a linha do
programa, o `execucao.json` e o `checagem.json` ficam como antes (S33: *dry-run* de calibração sem o JAR nem o JSON da
A1; a matriz da recalibração X-19 sai com o mesmo `3f20219d…`).

### Nomes combinados com o W-5e

| Onde | Campo | Valor |
|---|---|---|
| `execucao.json` | `fusao` | `pnn` ou `a1` |
| `execucao.json` | `controller`, `bloco` | os de sempre |
| `execucao.json` | `t_fusao_ns` | o instante real da fusão (P-61): a primeira amostra da sonda de topologia da trilha de observáveis depois do `end` com os três nós em 3; **ausente** sem fusão na trilha (ou sem `end`) |
| `manifesto-bracos.tsv` | `braco` | PO, PL, AO e AL no bloco fatorial (mesmo bloco, mesma semente); A e B nos de 2 braços; cabeçalho de sempre |

Detalhes que ficam só como registro, fora do contrato com o W-5e: `fusao_config` (arquivo, SHA-256, política esperada
e lida por nó, `counters-async` por nó, contagem de `A1-POLITICA carregada` por nó, falha do `cria-caches.sh`,
`ok`, `motivo`), `fusao_teto_s`, `fusao_checagem` (o registro do programa), `fusao_vista` (a primeira `ISPN000093`
depois do `end`, por nó, e o *driver* de log) e `extensao_sha256`. A coluna da matriz se chama `fusao`, e não `regra`
(o nome do complemento), para casar com o campo do `execucao.json`.

**Conferência com o `comparar_bracos.py` do W-5e** (`runs/orquestrador-fatorial/scripts/conferir_w5e.py`, saída em
`runs/orquestrador-fatorial/conferencia-w5e.out`). A saída do caso S36 (um bloco fatorial com F2, executado pelo
orquestrador contra os *shims*, com `t_fusao_ns` pela trilha) foi copiada; o `manifesto-bracos.tsv` e os `execucao.json` ficaram os do orquestrador, e os dados
de carga de cada braço foram trocados pelos geradores de teste do W-5c/W-5e, porque o dublê de `java` não grava carga.
A CLI do W-5e (`python -E -P analysis/comparar_bracos.py ... --celulas-teste ASYNC-F2`) leu o manifesto e saiu com
0: braços PO, PL, AO e AL, contrastes A1, A4 e interação, e perda permanente e exposição separadas pelo `t_fusao_ns`
de cada braço. Com `fusao` trocada no PO, ou com `t_fusao_ns` inválido, o W-5e recusou (exit 2): ele lê os campos do
orquestrador.

## Instante da fusão (P-61, decisão do coordenador de 06/11)

`t_fusao_ns` sai da trilha de observáveis do controlador (`campanha_s3_conferir.py fusao-trilha`): a primeira amostra
`topologia` em ou depois do `end` com os três nós respondendo e `numero_de_nos` = 3 em todos. A trilha não traz os nomes
dos membros; num *cluster* de 3, cada nó ver 3 é a mesma vista de 3 membros. A resolução é o período da sonda, 2,0 s nos
brutos. Também grava `fusao_trilha` (com o motivo, quando não há instante, e `dif_ispn000093_s`, a diferença para a
primeira `ISPN000093` depois do `end`) e, à parte, `t_vista_checagem_ns` (o instante da checagem antes da varredura).

Nos brutos completos do X-19, a regra dá **+42,2 a +46,2 s** nas nove F2: 44,1, 42,2, 44,2, 44,2, 44,3, 46,2, 44,2, 44,2
e 46,1 s, com mediana de 44,2 s nas seis ASYNC. No X-18, +44,1 a +47,8 s
(`runs/orquestrador-fatorial/fusao-trilha-brutos.out`). O teste usa uma fixture extraída desses brutos
(`scripts/testdata/fusao-x19/`, SHA-256 dos originais em `ORIGEM.txt`, iguais aos do MANIFEST da execução). Os brutos
não guardam o log do servidor, então a diferença para a `ISPN000093` só aparece nas execuções novas; no S36 (dublês),
a trilha dá +20,1 s e a `ISPN000093` +20,0 s, diferença de 0,1 s.

## Teto da checagem de fusão: 72 s, contados do `end`

| Parcela | Valor | Origem |
|---|---|---|
| tempo de fusão depois do `end` da F2, na VM | 42,2 a 47,8 s (máximo 47,8; o coordenador citou +44 a +48 s) | 18 execuções F2 (11 ASYNC, 7 SYNC) do X-18 e do X-19, primeira leitura da sonda de topologia do controlador (`observaveis-rep-001.jsonl`, `tipo` = `topologia`) com os três nós em `numero_de_nos` = 3, período de 2 s |
| arredondado | 48 s | |
| folga | × 1,5 | a do `exec_teto_s` sobre os tempos medidos (pré-registro da calibração §4.1: `1,5 × (t_pre + t_pos)`) |
| **teto** | **72 s** | vai ao arquivo de parâmetros como `fusao_teto_s`, com essa origem |

Por que não muda o `exec_teto_s`: o conferidor exige `inj_offset_s + f2_dur_s + fusao_teto_s <= duracao_s` (com os
números da recalibração, 49 + 90 + 72 = 211 ≤ 300), então o prazo vence antes do fim da carga. Nas nove F2 do X-19, o
fim da carga e a convergência vieram 153,9 a 154,1 s depois do `end`; a varredura, e com ela a checagem, começa depois
disso, com o prazo já vencido. A espera custa uma leitura (três GET em paralelo, prazo de 5 s cada, o do
`HealthRest`), que cabe na folga de 1,5 × (2,4 + 13,5) = 23,85 s do `exec_teto_s`. Sem `end` (célula sem falha), uma
leitura só.

Números conferidos nos brutos (`dados-vm/cs3-2026-11/rc-20261106T144744Z/` e `x18-20261106T050100Z/`), não em texto.

## Roteiro do *bundle*: o que muda em relação ao roteiro da recalibração (§2)

Referência: `wt-plano-ctrl/docs/roteiro-recalibracao-2026-11-06.md` §2. O `git archive` não leva `target/`, e o
*compose* da A1 monta `../extensao-servidor/target/tcc-extensao-servidor.jar` (relativo a `cluster/`) nos três nós. O
JAR tem de ser construído no *host* e posto no lugar na VM, como o do programa.

| Passo | Hoje (X-19) | Com a avaliação fatorial |
|---|---|---|
| 2.1 *host* | `git archive` da tag; `mvn package` do `workload/`; `bundle-$S7.sha256` com o *tar* e o JAR do programa | **mais:** `(cd extensao-servidor && mvn -o -q -T1 -DskipTests package)` (sem o plugin offline, o mesmo sem `-o`); `cp extensao-servidor/target/tcc-extensao-servidor.jar "$D/"`; o `bundle-$S7.sha256` com os três arquivos. A tag tem de conter a A1 (`extensao-servidor/`, `sessions-async-a1.json`, *compose* com o volume), a A4 e este orquestrador; conferir por ancestralidade, como os W-6c/W-10/P-48 hoje |
| 2.2 *host*, parâmetros | os 19 números, os cinco `s3_*` e o `s3_replay_late_max` | **mais** `fusao_teto_s` = 72, com a origem da tabela acima; o `rc-param.py` não sabe acrescentar esse campo (precisa de opção nova ou de outro auxiliar); o `exec_teto_s` e o `volume_exec_mb` seguem a regra da avaliação (DT-025), sem mudança por causa da checagem; conferir com `campanha_s3_conferir.py parametros --sonda-replay` |
| 2.3 transferência | `scp` do *tar*, do JAR do programa e dos SHA | **mais** o `tcc-extensao-servidor.jar`; na VM, `sha256sum -c bundle-$S7.sha256` (3 OK) e `mkdir -p $CS3/extensao-servidor/target && cp ~/tcc-extensao-servidor.jar $CS3/extensao-servidor/target/`; no `vars.sh`, `EXT_SHA=$(sha256sum $CS3/extensao-servidor/target/tcc-extensao-servidor.jar \| cut -c1-64)` e `--extensao-sha256 $EXT_SHA` no `ORC`; a lista `congelados.sha256` (18 arquivos) muda, porque o *compose* passa a montar o JAR |
| 2.4 matriz e *dry-run* | `gerar --modo calibracao` | `gerar --modo fatorial --nucleo "ASYNC-F2:teste:12,ASYNC-none:descritiva:4,ASYNC-F3:descritiva:4" --extras "ASYNC-F1:descritiva:4,ASYNC-F4:descritiva:4" --ordem-seed <data do commit das notas [A4]> --s0 800000 --passo-semente 100 --i0 0 --b0 0` (80 linhas) e `campanha_s3_conferir.py pareamento --matriz $MRC`; no *dry-run*, esperar `DRY-RUN fusao: pnn=sessions-async.json PREFERRED_NON_NULL … a1=sessions-async-a1.json br.unipampa.tcc.servidor.PoliticaFusaoSessoes …`, `DRY-RUN fusao antes da varredura: fusao_teto_s=72 …`, `DRY-RUN extensao: … montado nos 3 nos (SHA-256 confere)`, `fusao=` em cada `PLANO` e a linha do programa terminando em `--s3-replay-probe --sweep --sweep-merge-timeout-sec 72` |
| 2.5 recriação | `recriar-cs3.sh` com o *compose* do *bundle* | igual, mas o JAR tem de estar no lugar antes (o *compose* falha sem a fonte do volume); o `recriar-cs3.sh` cria os seis *caches* do `cluster/caches/` (regra atual), o que basta para o portão |
| 4.2 comando | `--sonda-replay` em `ORC` | **mais** `--extensao-sha256 $EXT_SHA`; fase `nucleo` (depois `extra` e `reposicoes`) |

O programa de carga também tem de vir da tag nova: o `--sweep-merge-timeout-sec` só existe a partir desta branch.

## Commits

| Commit | O quê |
|---|---|
| `c0e2603` | `test:` testes antes da implementação (vermelho): Python (matriz e conferidor), S33..S36 do orquestrador, `EsperaFusaoTest` |
| `04af5f6` | `test:` nomes combinados com o W-5e (`fusao`, `t_fusao_ns`, PO/PL/AO/AL no `manifesto-bracos.tsv`) e correção do auxiliar `vista` |
| `a36a6a0` | `feat:` matriz fatorial, pareamento, `fusao_teto_s` e `fusao-vista` no conferidor |
| `a570526` | `feat:` orquestrador (variante por braço, política conferida no *cluster*, JAR da extensão, registro da fusão) |
| `5f54796` | `feat:` checagem de fusão antes da varredura (`EsperaFusao`, `--sweep-merge-timeout-sec`) |
| (último) | `docs:` TD-022, P-61, `testes.md`, `configuracao-infinispan.md`, README, este relatório e as saídas em `runs/orquestrador-fatorial/` |

## Testes

Só os novos e os afetados (pedido do Senior). Detalhe em `docs/testes.md`, seção "Orquestrador da avaliação fatorial".

| Suíte | Vermelho (antes) | Verde |
|---|---|---|
| `test_campanha_s3_matriz_fatorial.py` (novo) | 12 testes: 8 falhas, 1 erro | 12 OK |
| `test_campanha_s3_conferir_fatorial.py` (novo) | 28 testes: 25 falhas, 2 erros | 28 OK |
| `test_campanha_s3_matriz.py`, `test_campanha_s3_conferir.py` (afetados) | — | 16 OK, 59 OK |
| `EsperaFusaoTest` (novo) | não compila | 13 OK |
| JUnit afetados: `Cli*Test`, `ControladorCliTest`, `Arquitetura*Test` (as do pacote `controle` também), `NomesDeCacheSoNoResolvedorTest`, `CanarioArquiteturaTest`, `ExigirPrimarioTest`, `HealthRest*Test`, `ConsultaSaudeRestTest` | — | todos verdes |
| `run-campanha-s3.test.sh` S33 (novo) | 7 PASS, 29 FAIL | 36 PASS |
| S34 (novo) | — | 74 PASS |
| S35, S36 (novos) | — | 67 PASS |
| afetados S1, S2, S4, S5, S6, S11, S12, S14, S16, S18, S20, S24, S27, S29, S30, S31, S32 | — | 294 PASS, 0 FAIL (2914 s) |
| P-61: conferidor do fatorial (classe `FusaoTrilha`, com a fixture do X-19) | 6 falhas | 34 OK |
| P-61: S33..S37 | — | 214 PASS e 8 FAIL, todos no S36 (defeito do dublê, corrigido); S36 de novo: 59 PASS |

Um teste novo foi corrigido depois do vermelho, sem mudar o que ele confere: o auxiliar `vista` do
`test_campanha_s3_conferir_fatorial.py` passava `--log-driver` no lugar dos marcadores.

## Problemas e riscos

1. **P-61, `RESOLVIDO`.** `t_fusao_ns` passou a ser o instante da fusão pela trilha (seção acima). Limite: se a fusão
   vier depois do fim da carga, o controlador já fechou a trilha, e o campo fica ausente (no X-18/X-19, a fusão veio
   ~110 s antes do fim da carga).
2. **Log do Podman no `journald`** (P-A4.2 da A4): as recusas Hot Rod da F2 podem suprimir as linhas `ISPN000093`. A
   linha `A1-POLITICA carregada` sai na criação dos *caches*, antes de qualquer carga, e não corre esse risco. O
   `execucao.json` registra o *driver* (`fusao_vista.log_driver`).
3. **Parada depois de 3 falhas seguidas da política** (decisão do coordenador): exit 6. Conto por variante: com o
   JAR quebrado só o `a1` falha, e os braços `pnn` que vêm antes dele no bloco zerariam uma contagem única. A contagem
   vale na invocação; a retomada recomeça do zero.
4. **Integração.** `sessions-async-a1.json`, o volume no *compose* e o `extensao-servidor/` vêm da A1; os testes desta
   branch usam cópias no diretório do caso (`CS_DIR_CACHES`, `CS_COMPOSE_ARQ`), e o `compose_fat` tira o volume que o
   *compose* do repositório já tiver. Conflitos prováveis: `docs/implementacao.md` (TD-020/021/022),
   `docs/testes.md`, `docs/registro-de-problemas.md` (P-61 provisório), `README.md`,
   `docs/configuracao-infinispan.md`; `Cli.java` e `WorkloadMain.java` se a A4 os mexer. Com o
   `sessions-async-a1.json` em `cluster/caches/`, o `CachesCargaJsonTest` ("o diretório tem exatamente os seis JSON")
   precisa aceitar o sétimo. Depois da integração, a suíte completa do orquestrador (S1..S36) roda com o *compose* da
   A1: os casos de 13 colunas não olham o JAR, e os fatoriais usam o *compose* do caso.

## Pontos que precisam de validação do Gestor

Aprovados pelo coordenador em 06/11: teto de 72 s e a regra `inj_offset + f2_dur + fusao_teto <= duracao`;
`DESCARTADA-INFRA` sem carga, com a parada depois de 3 falhas seguidas; coluna `fusao`; P-61 pela trilha. Em aberto:

- a contagem das 3 falhas por variante (e não única), pelo motivo do item 3;
- o critério da trilha sem `node_names` (três nós em 3);
- `logging: driver: k8s-file` no *compose* (o coordenador decide depois da proposta do outro Worker).
