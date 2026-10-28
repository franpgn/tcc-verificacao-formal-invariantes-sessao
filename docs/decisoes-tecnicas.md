# Decisões técnicas

> Registro das decisões técnicas aprovadas pelo Gestor que mudam como algo é medido, classificado ou
> aceito. Cada entrada tem data, origem e efeito. Entradas marcadas **PRÉ-REGISTRO** fixam um valor
> **antes** da execução que o usa, e não podem ser ajustadas depois de ver o resultado. Se for
> preciso mudar uma delas, a mudança vira uma nova entrada que cita a anterior e explica o motivo; a
> entrada original não é editada.
>
> Arquivo criado em 2026-10-22 na branch `docs/plano-implementacao-controlador` (worktree
> `wt-plano-ctrl`), onde estão o plano do controlador e as validações do bloco B-43. Ele vai para
> `main`/integração junto com esses documentos.

## DT-001: contadores `*_concorrentes` do auditor são só diagnóstico (2026-10-22)

- **Origem:** V-B43.6, seção 5.3 (achado A7-iii); decisão do Gestor de 2026-10-22.
- **Decisão:** `i1_concorrentes`, `i4_concorrentes` (B-43.6) e `i2_concorrentes` (B-43.9) medem o
  **observador**, não o *grid*. Eles **não entram em `S_viol` nem em nenhum gatilho** do controlador
  (R1..R4) e não contam como violação em nenhuma tabela de resultados.
- **Uso permitido:** canal de diagnóstico no JSON da janela do controlador (`viol.concorrentes`) e no
  relatório de cada execução, para que uma mudança anormal no próprio auditor fique visível.
- **Efeito:** o plano do controlador (`plano-implementacao-controlador-2026-10-22.md`, `S_viol`) deve
  ler `S_viol = Δ(I1 + I4 + I2_REAL)` sem nenhum termo `*_concorrentes`. A implementação do agregador
  (B-26.x/B-28.x) deve ter um teste que garanta isso.

## DT-002: `I2_REAL` em `S_viol` exige a cascata do bloqueio concluída (2026-10-22)

- **Origem:** V-B43.6, seção 5.2 (achado A7-ii); decisão do Gestor de 2026-10-22; implementação no
  B-43.9.
- **Decisão:** uma leitura de sessão VALID sob identidade BLOCKED é classificada pelo instante do
  início da leitura da sessão, em relação aos marcos do bloqueio. Antes de a identidade estar
  confirmada BLOCKED, ela é `i2_concorrente` (DT-001). Entre a confirmação da identidade e o fim da
  cascata, ela é `I2_JANELA_CASCATA`: métrica própria, fora de `S_viol`. Depois da cascata concluída,
  ela é I2, e só ela entra em `S_viol`, quando o rastro a classifica REAL.
- **Efeito:** `S_viol` usa só o I2 pós-cascata classificado REAL. `I2_JANELA_CASCATA` é reportada por
  fase como métrica secundária.

## DT-003: invalidação condicional por versão (2026-10-22)

- **Origem:** V-B43.6, seção 5.1 (achado A7-i); decisão do Gestor de 2026-10-22; implementação no
  B-43.8.
- **Decisão:** a atualização perdida `logout` × `block` é defeito do *workload*, não efeito a medir.
  `logout` e a cascata de `block` invalidam com escrita condicional por versão. A primeira invalidação
  vence, e o motivo de uma sessão invalidada é imutável do lado da aplicação.
- **Efeito:** o Logout passa a custar uma leitura com versão e uma escrita condicional em todos os
  cenários. O *rebaseline* S1/S2 e a T-F só rodam depois do B-43.8, para que meçam o mesmo Logout.

## DT-004: tolerâncias de estacionariedade da mistura v3, PRÉ-REGISTRO (2026-10-22)

> **Nota de remissão (2026-10-22):** a **Premissa** (`PERIODO_TOKEN_ANTIGO = 20`) e a tolerância de
> **C3** (5,00 % ± 0,10 p.p.) abaixo estão **SUBSTITUÍDAS por DT-008** a partir do B-43.7b. A margem
> de fronteira de C3/C5 está em DT-007. O texto original foi mantido sem edição.

- **Origem:** adendo do B-43.7 (`validacao-b43-2-adendo-b43-7-2026-10-22.md`, `ecc79cf`), seção 3,
  critérios C1..C3; aprovadas pelo Gestor em 2026-10-22, **antes** de qualquer execução de v3.
- **Premissa:** q = 5 % de Validate isolados lendo o *token* antigo (`PERIODO_TOKEN_ANTIGO = 20`),
  declarada como premissa de engenharia, não como medida de um serviço real.
- **Definições:** "Validate isolado" é um Validate cuja chave começa com `sid-`. "Fração INVALID" é
  INVALID / Validate isolados, contada no CSV de contrato v2 (B-43.4), na janela de medição, com os
  terços e décimos tomados por `start_ns`.
- **C1, estacionariedade:** a fração INVALID difere no máximo **1,0 p.p.** entre o 1º e o 3º terço da
  janela, em toda execução v3. Nas execuções de ≥ 600 s, também **máximo − mínimo ≤ 2,0 p.p.** entre
  os décimos.
- **C2, nível:** a fração INVALID fica em **[4,5 %; 6,5 %]**.
- **C3, premissa aplicada:** `token_antigo / validate_isolados` (stdout) = **5,00 % ± 0,10 p.p.**
- **Efeito:** valem para o *smoke* do B-43.7 e para toda execução v3 posterior (B-43.8, *rebaseline*),
  e são a base da regra `INVALID_FORA_DE_Q` proposta para a parte ii do B-43.3a. Execução fora dessas
  tolerâncias é FALHA de sanidade e não entra em resultado.

## DT-005: `--mix` com padrão `v2`; o *baseline* passa `--mix v3` (2026-10-22)

> **Nota de remissão (2026-10-22):** esclarecida por DT-006 (trava por faixa e reposição que falha).
> O texto original foi mantido sem edição.

- **Origem:** adendo do B-43.7, seção 6, ponto 2; decisão do Gestor de 2026-10-22.
- **Decisão:** o padrão do `--mix` continua `v2` (os testes existentes fixam isso). Toda execução de
  medição (B-42.3/B-43.3b, *rebaseline*, matriz) passa `--mix v3` explicitamente, e o *dry-run*/stdout
  registram a versão.
- **Também aprovado no mesmo dia:** a trava por faixa de *slot* no Logout isolado e a geração 0
  INVALID na carga de v3. As duas são artefatos declarados do *workload*.

## DT-006: esclarecimento de DT-005, trava por faixa e reposição que falha (2026-10-22)

- **Origem:** V-B43.7, observação O2 e ponto (iii) (`validacao-b43-7-2026-10-22.md`); decisão do
  Gestor de 2026-10-22. Esclarece DT-005 sem mudar nenhuma tolerância.
- **Trava:** a trava do Logout isolado da v3 é por **faixa** de *slot* (`k mod 1024`), não por *slot*.
  Logouts isolados de *slots* diferentes com o mesmo `k mod 1024` também se serializam no cliente. A
  espera pela trava fica fora da latência medida (`t0` é tomado dentro de `ops.logout`). A Metodologia
  deve dizer "faixa de *slot*".
- **Reposição que falha:** se a reposição VALID de `chave(k, g+1)` falhar, a geração **não** avança. Os
  Validate do *slot* continuam na geração `g`, já invalidada, até o próximo Logout isolado do *slot*,
  que vira no-op. É a única escolha que mantém Validate NOT_FOUND = 0 e não ressuscita chave.
- **Efeito:** em execução sem falha injetada, `reposicao_erro = 0` continua sendo condição de C5. Em
  execução com falha injetada, `reposicao_erro` e `logout_noop` são métricas reportadas, não critério.

## DT-007: esclarecimento de DT-004, margem de fronteira em C3/C5 (2026-10-22)

- **Origem:** V-B43.7, ponto (ii); decisão do Gestor de 2026-10-22. Esclarece DT-004 sem relaxar
  nenhuma tolerância.
- **Decisão:** nas comparações de contagem entre o stdout e o CSV de C3 (`validate_isolados`) e de C5
  (`logout_isolados`), admite-se uma diferença de **até 4 eventos, igual ao número de *threads*** da
  execução, na fronteira da janela de medição. Os contadores de janela e o evento do CSV são avaliados
  em instantes diferentes, e cada *thread* pode ter no máximo uma operação de cada lado da fronteira.
- **Limites:** a margem vale só para C3 e C5. Não se aplica a C1 nem a C2. Com outro número de
  *threads*, a margem é esse número. O valor medido da diferença é **sempre** reportado, mesmo quando
  for 0.

## DT-008: *token* antigo por sorteio de Bernoulli semeado; C3 por IC binomial, PRÉ-REGISTRO (2026-10-22)

- **Origem:** V-B43.7, observação O3; decisão do Gestor de 2026-10-22; implementação no B-43.7b.
  **Substitui** a Premissa e a tolerância de C3 de DT-004 (que ficam no texto original, com nota de
  remissão).
- **Rejeitado:** manter o *token* antigo como contador determinístico ("1 em cada 20 Validates
  isolados, por *thread*") e descrevê-lo assim na Metodologia. Um contador periódico não é o modelo de
  q que a Metodologia quer declarar, e dá a C3 uma precisão artificial (erro < 10⁻⁴ p.p.).
- **Decisão:** cada Validate isolado da v3 lê o *token* antigo com probabilidade **p = 0,05**, por um
  sorteio de Bernoulli independente. O sorteio usa um `Random` **próprio de cada *thread***, separado
  do `Random` da mistura e do `KeyGenerator`, semeado de forma determinística a partir da semente da
  execução e do índice da *thread* (derivação fixada no prompt do B-43.7b). O `Random` da mistura não
  é consumido. Mesma semente, mesma sequência de *tokens* antigos, alinhado à reprodutibilidade por
  `--seed` do B-43.5.
- **C3, nova tolerância:** com `n = validate_isolados` (stdout) e `k = token_antigo` (stdout) da
  execução, C3 passa se `|k/n − 0,05| ≤ z · √(0,05 · 0,95 / n)`, com **z = 3,2905** (bilateral,
  99,9 %). É a região de aceitação do teste de H0: p = 0,05 ao nível de 0,1 %, pela aproximação normal
  da binomial, válida porque n · 0,05 · 0,95 ≥ 100 (o script de análise recusa a execução, como FALHA
  de sanidade, se não valer). O relatório de cada execução mostra k, n, a fração e a meia-largura.
- **Referência numérica (só ilustração, com os n do B-43.7):** n = 245 204 (S1 sync 120 s) dá
  ± 0,145 p.p.; n = 974 990 (S2 sync 120 s) dá ± 0,073 p.p.; n = 1 338 556 (S1 sync 600 s) dá
  ± 0,062 p.p. A banda é mais larga que os antigos ± 0,10 p.p. para n < ≈ 514 000 e mais estreita
  acima disso. Não é relaxamento: com o sorteio, uma banda fixa de ± 0,10 p.p. reprovaria por acaso
  cerca de 2 % das execuções de 120 s em S1 (2,3 % com n = 245 204), e o IC fixa essa chance em
  0,1 % por execução.
- **Inalterado:** C1 (terços ≤ 1,0 p.p.; décimos ≤ 2,0 p.p. em execuções ≥ 600 s), C2 (fração INVALID
  em [4,5 %; 6,5 %]), a parte de C3 que compara `validate_isolados` do stdout com o CSV (DT-007) e as
  definições de DT-004. Nenhuma outra tolerância muda.
- **Validade:** vale para toda execução v3 feita com o *jar* do B-43.7b ou posterior. Os *smokes* do
  B-43.7 e do B-43.8 (contador determinístico) continuam válidos como *smokes* sob a regra antiga e não
  são resultado. O *rebaseline* S1/S2 roda depois do B-43.7b.

## DT-009: *rebaseline* S1/S2 v3 em DIST_SYNC e DIST_ASYNC, PRÉ-REGISTRO (2026-10-23)

> **Nota de remissão (2026-10-23):** o instante de injeção (+35 s) e a duração de F3 abaixo estão
> **SUBSTITUÍDOS por DT-011** (+49 s da âncora, F3 de 251 s), que também fixa a tolerância de K6.
> O texto original foi mantido sem edição.

- **Origem:** rascunho `docs/pre-registro-rebaseline-2026-10-23.md` (`d7df5dd`); respostas do Gestor
  às perguntas R1..R10 em 2026-10-23. Texto completo e vinculante:
  `docs/pre-registro-rebaseline-aprovado-2026-10-23.md`. Registrado antes de qualquer execução do
  piloto.
- **Matriz:** S1 e S2, `--mix v3`; `--replication sync` e `async`; falhas `none`, F1 (`podman stop`
  de `isn2` por 60 s) e F3 (`netem delay 20ms 13ms distribution normal` em `isn2`, até o fim); 10
  repetições de 300 s por célula (120 execuções), 4 *threads*, `--warmup-min-sec 30`, `--reset-state
  on`, `--rep 1`. Injeção a +35 s; janela de medição de +44 a +300 s. S3 fora.
- **Ordem:** blocos completos aleatorizados; rodada r ordenada por
  `random.Random(231023 * 1000 + r).shuffle` da lista canônica; piloto = rodada 0.
- **Sementes:** `--seed = 50 000 + 100·i`; campanha i = 0..119, piloto i = 1 000..1 011, reposições
  i = 120, 121, ...; R-S3 e R-S4 como no documento.
- **Recusa e parada:** K1..K10 e paradas (a)..(g) da seção 6 do documento. Com falha, o denominador
  de C1/C2 é o número de Validate isolados com VALID ou INVALID (erros reportados à parte); C4/C5 são
  reportados, não critério (DT-006). Nenhuma análise usa `Violacoes detectadas:`; I2 conta como
  violação só com rótulo `I2` e `classificacao = REAL`.
- **Análise:** unidade = execução; p99 por posto mais próximo sobre desfechos válidos, com n ≥ 1 000;
  contraste = diferença das médias dos p99 por execução, IC 95 % por *bootstrap* percentil (10 000
  reamostragens de execuções, `BOOT_SEED = 231024`), só com ≥ 8 execuções aceitas por célula;
  Mann-Whitney bilateral sobre os p99 por execução como teste secundário.
- **Operação:** VM `tcc-f3`, depois de S1..S7 e com o *cluster* recriado; duas noites de 5 rodadas
  depois de uma noite de piloto validado de dia; `go` do Gestor presente no início de cada noite e
  antes da primeira execução F3 de cada noite; sem coleta de CPU.
- **Pré-requisitos:** B-43.9 com F-1 e B-43.5 integrados; *runner* e análise novos integrados;
  complemento deste registro (SHA da ponta, SHA-256 do jar, do *bundle* e de `matriz.tsv`, criação do
  *cluster*) commitado antes do piloto. Dados do piloto não entram em resultado.
- **Desvio declarado:** T10 (30 repetições) passa a 10 repetições por célula.

## DT-010: `sem_marco = 0` é critério só em DIST_SYNC (2026-10-23)

- **Origem:** V-B43.9, ponto (5) e pergunta V5 (`validacao-b43-9-2026-10-23.md`); decisão do Gestor
  de 2026-10-23.
- **Decisão:** a contagem `sem_marco` da linha `Auditor I2:` (leitura VALID sob identidade lida
  BLOCKED sem marco de bloqueio no mapa) deve ser 0 nas execuções em **DIST_SYNC** em que o
  pré-registro da campanha usa o auditor como critério. Em **DIST_ASYNC** ela é métrica reportada
  (por fase, na T-F), não critério: uma leitura velha da identidade depois do `unblock`, com o marco
  já removido, é efeito do *grid* (H-A0), não defeito.
- **Efeito no *rebaseline* (DT-009):** parte de K9 (SYNC sem falha, parada para investigar); em
  ASYNC, reportada (K10).
- **Efeito na T-F:** o pré-registro da T-F reporta `sem_marco` por fase em ASYNC. Se a T-F mostrar
  `sem_marco > 0` em ASYNC, abre-se um item para um marco de desbloqueio (lápide) que dê a essas
  leituras uma classe própria. A lápide não é feita agora.

## DT-011: injeção a +49 s, F3 de 251 s e tolerância de K6 no *rebaseline*, PRÉ-REGISTRO (2026-10-23)

- **Origem:** perguntas P1..P3 do Senior ao entregar a DT-009; decisão do Gestor de 2026-10-23,
  registrada antes de qualquer execução do piloto. **Substitui**, na DT-009 e no pré-registro
  aprovado, o instante de injeção e a duração de F3 (textos originais mantidos, com remissão).
- **Motivo (P1):** com `--duration 300` e `--warmup-min-sec 30`, a `WarmupPolicy` descarta mais
  14 s depois do aquecimento, e a janela de medição vai de +44 a +300 s. A +35 s, os primeiros ≈ 9 s
  do F1 (incluída a saída ordenada do nó por `podman stop`) cairiam fora da medição.
- **Decisão P1:** F1 e F3 são disparados a **+49 s** da âncora (início da medição + 5 s). A âncora é o
  instante em que a linha `Warm-up ate +` aparece no stdout do *workload* (início do aquecimento).
  F1 deixa `isn2` parado de ≈ +49 a ≈ +109 s. F3 dura **251 s** (`--duration 251`), de +49 a +300 s.
- **Decisão P3 (complemento operacional de K6):** o disparo do injetor tem de cair entre **−1 e +2 s**
  do instante previsto, isto é, entre **+48 e +51 s** da âncora. Fora disso, a execução é recusada
  (K6). O desvio medido é sempre registrado.
- **Decisão P2:** aprovado o desenho do B-43.5 com sal próprio: o `Random` da mistura de cada *thread*
  é `new Random(mix64(sementeThread ^ SAL_MISTURA))`, `SAL_MISTURA = 0x4D49535455524153L`
  ("MISTURAS" em ASCII), e não `new Random(sementeThread)`, que compartilharia o fluxo do
  `KeyGenerator`.
- **Inalterado:** todo o resto da DT-009 (matriz, sementes, K1..K10, paradas, análise, noites, gates).

## DT-012: semente do `Random` da mistura (`SAL_MISTURA`) e escopo da reprodutibilidade por `--seed` (2026-10-23)

- **Origem:** decisão P2 do Gestor registrada na DT-011 (desenho com sal próprio); implementação no
  B-43.5 (branch `fix/b43-5-mistura-semeada`, ponta `642d81f`); validação V-B43.5
  (`docs/validacao-b43-5-2026-10-23.md`). Esta entrada fixa a derivação e o que a Metodologia pode
  declarar. Vale para todo *jar* que contenha o B-43.5, inclusive o do *rebaseline* (DT-009).
- **Derivação (S1/S2).** Com `s = sementeThread(seedBase, tid, rep) = seedBase + tid + rep · 1 000 003`
  (inalterada desde o B-43.7b), cada *thread* de carga tem três geradores `java.util.Random` próprios,
  nunca compartilhados:
  - mistura (tipo de cada operação): `randomDaMistura(s) = new Random(sementeMistura(s))`, com
    `sementeMistura(s) = mix64(s ^ SAL_MISTURA)` e `SAL_MISTURA = 0x4D49535455524153L` ("MISTURAS" em
    ASCII);
  - *token* antigo da v3 (DT-008): `new Random(mix64(s ^ SAL_TOKEN))`, `SAL_TOKEN = 0x544F4B454E414E54L`
    ("TOKENANT");
  - chave Zipf (`KeyGenerator`): `new Random(s)`, **sem sal** (comportamento anterior, mantido para
    não mudar o golden nem as sequências já fixadas).
  `mix64` é o finalizador do SplitMix64 (`MisturaV3.mix64`). Valores congelados por emulação
  independente (Python) de `java.util.Random` e do SplitMix64: `sementeMistura(42) =
  −595 519 573 436 187 269`; os 40 primeiros `randomDaMistura(42).nextInt(100)` estão em
  `MisturaSemeadaTest.NEXT_INT_100_40`.
- **Rejeitado:** `new Random(s)` para a mistura (o mesmo fluxo do LCG do `KeyGenerator`, sorteio da
  operação n e chave de índice ≈ n/2 do mesmo estado); `ThreadLocalRandom` (não reprodutível);
  semente sem a *thread* (`seedBase` ou `seedBase + rep·1 000 003`, que daria a mesma mistura a todas
  as *threads*).
- **Limite conhecido da separação.** Os três geradores são posições do mesmo ciclo de 2⁴⁸ do LCG do
  `java.util.Random`; o sal com o misturador põe os da mistura e do *token* em posições
  pseudoaleatórias. A chance de duas dessas sequências se sobreporem em N = 10⁶ sorteios é da ordem
  de 2N/2⁴⁸ ≈ 7·10⁻⁹ por par. O `KeyGenerator`, sem misturador, recebe sementes vizinhas nas *threads*
  (s, s+1, ...), e por isso o **primeiro** `nextDouble` de cada *thread* é quase igual
  (≈ 0,608 nas quatro *threads* com `--seed 50000 --rep 1`, emulação da V-B43.5); do segundo sorteio em
  diante as sequências já divergem. O efeito é uma chave no início do aquecimento, que fica fora da
  janela de medição. Não muda nada agora; fica registrado.
- **O que é reprodutível** (mesmo *jar*, mesma `--seed`, mesmo `--rep`, mesmo `--threads`, mesmo
  `--scenario` e `--mix`): em cada *thread*, a sequência de **intenções**, isto é, os tipos de
  operação, as chaves Zipf (logo os *slots* da v3), as decisões de *token* antigo e o rodízio de
  identidades de Login, como prefixo de uma sequência infinita fixa. Evidência: W3, W6, W7 e W8 sobre o
  `rodarCliente` real; no *smoke* local com 1 *thread*, 179 159 eventos (operação, chave) iguais entre
  duas execuções com a mesma semente, e nenhuma janela de 256 eventos em comum com outra semente.
- **O que não é reprodutível:** a intercalação entre *threads*; as latências; as respostas do *grid*
  e o que depende delas (geração do *slot* lida por um Validate isolado, continuação da tríade depois
  de Login OK, sucesso das reposições, erros sob falha); o número de operações por *thread* e a
  fronteira da janela de medição, definidos por tempo (duas execuções iguais medem trechos
  deslocados da mesma sequência); os `sid` da tríade (`UUID.randomUUID()`). Com mais de uma *thread*,
  o CSV de duas execuções com a mesma semente **não** é igual, nem como trecho; a igualdade de
  `return_code` vista no *smoke* com 1 *thread* sem falha não é propriedade geral. S3 não muda (já
  tinha geradores por ator).
- **Efeito no texto:** a Metodologia descreve três geradores por *thread* derivados da semente da
  execução, do índice da *thread* e da repetição, **dois deles com sal próprio** (mistura e *token*),
  e declara só o escopo acima.

## Complemento da DT-009, parte 1 de 2: matriz e ordem congelada (2026-10-23)

- **Origem:** PR §3 e §9 (`docs/pre-registro-rebaseline-aprovado-2026-10-23.md`); entrega do *runner*
  (`feature/rebaseline-runner-analise`, ponta `599ea5e`); validação V-RB
  (`docs/validacao-rebaseline-runner-2026-10-23.md`). São identificadores, não escolhas (PR §9).
  Registrado antes de qualquer execução do piloto.
- **Comando:** `python3 -I scripts/rebaseline_matriz.py gerar --ordem-seed 231023 --s0 50000 --saida
  runs/rebaseline/matriz.tsv`. Cabeçalho mais 132 linhas (piloto, depois as rodadas 1..10), colunas
  `i fase noite rodada posicao celula cenario modo falha seed`, LF, ASCII.
- **SHA-256 de `matriz.tsv`:** `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35`.
  Conferido pelo Senior com um gerador independente escrito a partir do texto do PR §2..§4 (não do
  script do Worker), em CPython 3.12.6; o Worker gerou em 3.12.10; mesmos bytes. O arquivo entra no git
  como `runs/rebaseline/matriz.tsv` no commit de integração do *runner*; o *runner* recusa (exit 4) uma
  matriz com outro SHA-256. A VM nunca regenera a matriz, então a versão do Python da VM não importa.
- **Ordem congelada** (`random.Random(231023 * 1000 + r).shuffle` da lista canônica; posição:célula;
  igual à constante `ORDEM_CONGELADA` de `scripts/test_rebaseline_matriz.py`):

  | Rodada | Ordem (posição 0..11) |
  |---|---|
  | 0 (piloto, i = 1000..1011) | S2-sync-F1, S2-async-none, S1-sync-F1, S1-async-none, S2-async-F1, S1-async-F3, S2-async-F3, S2-sync-F3, S1-async-F1, S1-sync-F3, S2-sync-none, S1-sync-none |
  | 1 | S2-sync-F1, S1-sync-F1, S1-sync-none, S2-sync-none, S1-async-F3, S1-async-none, S2-async-none, S1-sync-F3, S2-async-F3, S1-async-F1, S2-sync-F3, S2-async-F1 |
  | 2 | S1-async-F3, S2-sync-F1, S1-async-none, S1-sync-none, S2-async-none, S2-sync-none, S1-sync-F1, S2-sync-F3, S2-async-F1, S2-async-F3, S1-async-F1, S1-sync-F3 |
  | 3 | S1-sync-F1, S2-sync-F3, S2-sync-none, S1-sync-F3, S2-async-none, S2-async-F1, S1-async-F1, S2-sync-F1, S1-async-none, S1-sync-none, S2-async-F3, S1-async-F3 |
  | 4 | S1-sync-F3, S2-async-none, S1-async-F1, S1-sync-none, S2-sync-F1, S2-async-F3, S1-async-F3, S1-async-none, S2-sync-none, S2-async-F1, S1-sync-F1, S2-sync-F3 |
  | 5 | S1-sync-none, S1-async-none, S2-sync-F1, S2-async-F3, S2-async-none, S1-async-F3, S2-sync-F3, S2-async-F1, S1-sync-F3, S1-async-F1, S2-sync-none, S1-sync-F1 |
  | 6 | S1-async-none, S1-sync-F3, S1-async-F1, S2-sync-none, S2-async-none, S2-async-F3, S2-async-F1, S2-sync-F1, S1-async-F3, S1-sync-F1, S2-sync-F3, S1-sync-none |
  | 7 | S1-async-none, S1-sync-none, S2-sync-F1, S1-async-F1, S2-async-none, S2-async-F3, S2-sync-F3, S1-async-F3, S2-sync-none, S2-async-F1, S1-sync-F3, S1-sync-F1 |
  | 8 | S2-sync-F1, S1-sync-F3, S2-sync-F3, S1-async-none, S2-async-F1, S1-async-F3, S1-sync-F1, S2-async-none, S2-sync-none, S1-sync-none, S2-async-F3, S1-async-F1 |
  | 9 | S1-sync-none, S1-async-none, S2-sync-F3, S2-async-none, S2-async-F1, S1-async-F3, S1-sync-F1, S2-sync-none, S1-async-F1, S2-async-F3, S2-sync-F1, S1-sync-F3 |
  | 10 | S1-sync-F1, S2-async-F1, S1-async-F3, S2-sync-F1, S2-sync-none, S1-sync-none, S1-sync-F3, S2-sync-F3, S2-async-F3, S2-async-none, S1-async-F1, S1-async-none |

- **Índices e sementes:** campanha i = 12·(r − 1) + posição (0..119), piloto i = 1000 + posição,
  `--seed = 50 000 + 100·i`; R-S2 conferido de forma independente (528 pares (semente da *thread*) com
  `tid` 0..3 e `rep` 1, nenhum repetido). Primeira F3: i = 1005 no piloto, i = 4 na noite 1, i = 60 + 1 = 61
  na noite 2 (rodada 6, posição 1).
- **Parte 2 (pendente, antes do piloto):** SHA da ponta integrada, SHA-256 do jar, SHA-256 do *bundle* da
  VM e instante de criação do *cluster* depois do S7.

## Complemento da DT-009, parte 2 de 2: ponta, jar e *bundle* (2026-10-23)

- **Origem:** PR §9; revalidação do *runner* (`docs/revalidacao-rebaseline-runner-2026-10-23.md`).
  São identificadores, não escolhas. Registrado antes de qualquer execução do piloto.
- **Ponta do *runner*** (alvo do *fast-forward* de `feature/controlador-integracao`):
  `988fd3c6bca5d3f201ca3b0366cdeca9e90f21d9`, base `642d81f`. Se o commit de integração que põe
  `matriz.tsv` no git criar outra ponta, o SHA dela entra como adenda deste item, antes do piloto.
- **Jar:** `workload/target/session-workload-0.1.0-SNAPSHOT.jar`, construído da ponta com
  `mvn -q -o -f workload/pom.xml -DskipTests clean package` (JDK 22.0.2, Maven 3.9.16), 18 824 990 bytes,
  SHA-256 `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf`. O *build* **não é
  reprodutível byte a byte** (o `pom.xml` não fixa `project.build.outputTimestamp`): o *hash* vale para
  este arquivo, que deve ser congelado e levado à VM sem novo `package`. Identificador reprodutível do
  conteúdo (SHA-256 da lista ordenada `nome<TAB>crc32<TAB>tamanho` das 12 018 entradas):
  `c9aa921222655cd4991b0c6ed13c46b88231ba9184aa35c76455205b69e2669d`, igual em dois *builds*.
- **`matriz.tsv`:** regenerada da ponta, `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35`,
  igual à parte 1.
- ***Bundle* da VM** (*blobs* do git, LF, montado por `git archive`; não do *worktree* Windows, que tem
  `core.autocrlf=true`):

  | Arquivo | SHA-256 |
  |---|---|
  | `analysis/rebaseline.py` | `fe5c7c74c9e740f6fba935caca888a03395ca7491bad22317deece857a559256` |
  | `runs/rebaseline/matriz.tsv` | `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35` |
  | `scripts/inject-crash.sh` | `1c9bf01b9a100e690f15f2fb4b923f2365c0d923572011cfde37eb0c36c92d55` |
  | `scripts/inject-jitter.sh` | `7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529` |
  | `scripts/rebaseline_matriz.py` | `3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a` |
  | `scripts/run-rebaseline.sh` | `d95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366` |
  | `workload/target/session-workload-0.1.0-SNAPSHOT.jar` | `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` |

  SHA-256 da lista acima no formato do `sha256sum` (`<hash> *<caminho>`, em ordem):
  `e304ba4b719820573fd3cd2bb70525e1160e7c76459b3ce16101766c34bdc34c`. Na VM, conferir arquivo a arquivo
  com `sha256sum -c`.
- **Pendente (só na VM):** instante UTC de criação do *cluster* depois do S7 (`--cluster-criado-apos`).

## DT-013: K8 do *rebaseline* conta `ISPN006010` por vida do processo do nó, PRÉ-REGISTRO (2026-10-23)

> **Aprovada pelo Gestor em 2026-10-23** (pergunta Q1 da V-RB; decisão transmitida ao Senior pelo
> coordenador). Registrada antes de qualquer execução do piloto; implementada antes do piloto.

- **Origem:** ponto P1 do relatório do *runner* (`docs/relatorio-rebaseline-runner-2026-10-23.md` da
  branch `feature/rebaseline-runner-analise`); validação V-RB.
- **Motivo:** `ISPN006010` (`HotRodHeader`: "Conditional operation 'REPLACE_IF_UNMODIFIED' should be
  used with transactional caches...") é um aviso de primeira ocorrência, emitido uma vez por JVM do
  servidor (1 por nó nos *smokes* do B-43.7b, B-43.8 e B-43.9, cada nó com uma única JVM). O critério
  nasceu na V-B43.8 com essa leitura ("aviso de primeira ocorrência; mais do que isso é investigado"). O
  F1 faz `podman stop`/`start` de `isn2`, o que sobe uma JVM nova, que emite o aviso de novo, e `podman
  logs` mostra todas as vidas do *container*. Com o texto da DT-009 ("no máximo 1 por nó por vida do
  *cluster*"), a primeira execução F1 (i = 1000 no piloto, i = 0 na noite 1) daria PARADA sem nenhuma
  anomalia. O texto da DT-009 foi escrito sem considerar que o F1 reinicia o processo.
- **Decisão proposta:** K8 continua com papel **parada**, com esta contagem, por nó `n` e depois de cada
  execução:
  1. no máximo **1** `ISPN006010` na vida atual do processo (`podman logs --since
     <.State.StartedAt.Unix> n`); e
  2. o total de `ISPN006010` em `podman logs n` (desde a criação do *container*) **não maior** que o
     número de vidas do processo, contado pelas linhas `ISPN080001` ("Infinispan Server ... started") no
     mesmo `podman logs n`.
  A regra 2 cobre o trecho que a regra 1 não vê: um segundo aviso na vida antiga de `isn2` emitido nos
  primeiros ≈ 49 s da execução F1 seguinte, antes do `stop`. Contagem ilegível (`podman logs` ou
  `podman inspect` com erro, ou campo ausente) não passa em silêncio: `checar` sai com 2 e o *runner* com
  6 (infraestrutura).
- **Rejeitado:** manter o texto literal e conferir na VM com um F1 fora do piloto (a regra literal pararia
  a primeira F1 de qualquer forma, e o ensaio exigiria uma injeção a mais, com `go` próprio); contar só a
  vida atual (perde o trecho descrito na regra 2); retirar K8 (perderia o sinal de uma segunda ocorrência
  na mesma JVM).
- **Efeito:** muda só a forma de contar de K8; o resto da DT-009, DT-010 e DT-011 fica igual. No
  *runner*: `ispn006010.json` passa a gravar `vidas_por_no` (contagem de `ISPN080001`) e trata `null` em
  `por_no_desde_ultimo_start` como erro. Na análise: K8 usa as duas regras, com testes (duas vidas com 1
  aviso cada: ACEITA; uma vida com 2: PARADA; total maior que as vidas: PARADA; contagem `null`: exit 2).

## DT-014: execução interrompida no *rebaseline*, PRÉ-REGISTRO (2026-10-23)

- **Origem:** ponto P3 do relatório do *runner* e pergunta Q3 da V-RB
  (`docs/validacao-rebaseline-runner-2026-10-23.md`); decisão do Gestor de 2026-10-23 (transmitida ao
  Senior pelo coordenador), registrada antes de qualquer execução do piloto. Complementa a DT-009
  (PR §6, recusas e reposições).
- **Contexto:** o *runner* nunca sobrescreve brutos; um `exec-<i>-<celula>` sem `checagem.json` impede a
  retomada (exit 4). Com a correção C2 da V-RB (gate `go f3` antes de criar o diretório e antes do K1),
  um `go f3` errado deixa de cair nesse caso; sobram interrupções reais (queda da VM, *tmux* morto,
  sinal ao *runner*).
- **Decisão:**
  1. **Interrompida antes do lançamento do *workload*** (sem `stdout.log` no diretório): o diretório é
     renomeado para `exec-<i>-<celula>.abortada-<UTC>`, mantido nos brutos, e o **mesmo i** roda de
     novo, com a mesma semente (ela nunca mediu nada).
  2. **Interrompida depois do lançamento** (há `stdout.log`): a execução conta como **RECUSADA**
     (motivo "interrompida"), entra nas paradas (a) e (b) do PR §6 e é reposta no fim da campanha com o
     próximo i livre, como qualquer recusa isolada.
- **Efeito:** o *runner* aplica a regra na retomada, registra o caso em `runner.log` e no resumo da fase;
  `estado` conta a recusa "interrompida". Nada mais da DT-009 muda.

## Decisões do Gestor sobre a V-RB (2026-10-23)

Registro das respostas às perguntas Q1..Q5 de `docs/validacao-rebaseline-runner-2026-10-23.md`
(transmitidas ao Senior pelo coordenador):

- **Q1:** DT-013 aprovada (acima).
- **Q2:** correções C2..C5 aprovadas: gate `go f3` antes de criar o `exec-*` e antes do K1; exits do
  injetor 1 e 4 dão recusa K6 e 2, 3, 5 ou outro dão exit 6; `null` em `por_no_desde_ultimo_start` é
  erro e `remocao_extra = true` falha K6; instante efetivo da falha registrado só para relatório.
- **Q3:** regra de execução interrompida aprovada (DT-014).
- **Q4:** *rebase* da branch do *runner* sobre `642d81f` e *fast-forward* local de
  `feature/controlador-integracao` depois das correções e da revalidação curta do Senior; sem push.
- **Q5:** ajustes da checklist do Gestor (seção 8 da V-RB) aprovados.

## Decisões do Gestor sobre a revalidação do *runner* (2026-10-23)

Respostas a G1..G5 de `docs/revalidacao-rebaseline-runner-2026-10-23.md` (transmitidas pelo coordenador):

- **G1 (R1):** aprovado. `*.py text eol=lf` e `*.tsv text eol=lf` entram no `.gitattributes` no mesmo
  commit que põe `runs/rebaseline/matriz.tsv` no git. O *bundle* é montado só por `git archive`.
- **G2 (R2):** aprovado. O jar `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf`
  (18 824 990 bytes, ponta `988fd3c`) foi copiado pelo coordenador para
  `dados-vm/rebaseline-2026-10/jar/session-workload-0.1.0-SNAPSHOT.jar` (somente leitura, com `.sha256`
  ao lado) e é o jar do piloto e das noites. Não se reconstrói o jar para a campanha.
- **G3 (D17):** cobertura manual. Em cada execução RECUSADA "interrompida" com CSV parcial, o Senior lê
  o CSV no parecer da noite e verifica K9; o código não muda. Uma violação encontrada assim tem o efeito
  de PARADA da fase.
- **G4:** passo 4 da checklist ajustado: `2>&1` antes do `grep -c ISPN080001` e leitura de
  `{{.State.FinishedAt.UnixMilli}}`.
- **G5 (D15):** aceito como está; mover `.scratch/` antes do exit 6 fica para depois do piloto.

Integração: *fast-forward* de `feature/controlador-integracao` `642d81f` → `988fd3c` feito pelo
coordenador; Surefire medido 761/0/0/1 (106 arquivos). Sem push.

## Decisões do Gestor sobre os prompts da trilha do host (2026-10-27)

Respostas a D1..D4 do Senior (prompts de `afa33c8`), transmitidas pelo coordenador:

- **D1 (B-27):** sobrecarga de `rodarCliente` com 11 parâmetros (a de 10 delega com `livre()`) e
  `ParametrosKnobs` em arquivo JSON próprio. Nenhum teste existente é editado.
- **D2 (B-39.1):** a referência de p99 do controlador usa todos os eventos da operação, com qualquer
  código de retorno, como o agregador conta, por posto mais próximo.
- **D3 (B-40):** IC primário = intervalo t sobre d_b com 8 blocos; *bootstrap* de blocos e hierárquico
  como sensibilidade; permutação exata mantida.
- **D4 (B-25.1):** entra depois da campanha do *rebaseline* e antes do *bundle* da T-F, mantendo
  `run-rebaseline.test.sh` verde sem edição (ou trazendo a adaptação do *runner* junto).

Os ajustes 1..9 ao plano de 22/10 listados pelo Senior ficam valendo como errata até a revisão do plano.

## DT-015: limiares do `VerificadorSanidade` (K3) no *rebaseline*, PRÉ-REGISTRO (2026-10-27)

> **Aprovada pelo Gestor em 2026-10-27** (decisão transmitida ao Senior pelo coordenador). Registrada
> antes de qualquer execução do piloto. Texto também no item (d) de
> `docs/pre-registro-rebaseline-complemento-1-2026-10-27.md`.

- **Origem:** limiares provisórios do diagnóstico de 22/10 aceitos na V-B43.3a-i
  (`docs/validacao-b43-3a-i-2026-10-22.md`, seção 5.2) e versão confirmada na revalidação das ressalvas
  (`docs/validacao-b43-3a-i-ressalvas-2026-10-22.md`, item 2). O PR §6 (K3) exige exit 0 e veredito OK
  do verificador, mas não fixava os limiares como pré-registro.
- **Decisão:** os limiares do *rebaseline* são os do jar congelado `5eced136…` (ponta `988fd3c`,
  `Sanidade.Limiares.provisorios()` e `Sanidade.VERSAO`):
  - Login OK/(OK + BLOCKED) ≥ 0,95;
  - Validate VALID > 0;
  - Validate ausente, (NONE + NOT_FOUND)/Validate, ≤ 5 %;
  - mistura: ± 2,0 p.p. por operação em relação ao peso nominal do cenário;
  - versão do critério no JSON: `b43-3a-i/1`.
- **Não muda:** o código e o jar. O nome `provisorios()` fica; para o *rebaseline* os valores são os
  definitivos. A calibração separada sugerida na V-B43.3a-i não foi feita como etapa própria; uma
  recusa sistemática no piloto por razão que não seja defeito segue o PR §8 (entrada nova antes da
  noite 1, com subida da versão do critério).
- **Efeito:** K3 passa a ter limiares pré-registrados. O parecer de cada fase confere, numa amostra de
  JSON do verificador, `versao = b43-3a-i/1` e os limiares 0,95, 0,05 e 2,0.

## Decisões do Gestor sobre a V-S1 (2026-10-27)

Respostas a P-1..P-6 de `docs/validacao-v-s1-2026-10-27.md` (branch `test/b29-gate-v4`, `012a621`),
transmitidas ao Senior pelo coordenador:

- **P-1:** aprovado. O *cluster* é recriado com o XML atual da v4 (`cluster/`, idêntico ao da v3 e ao da
  REP=3), por `podman-compose down` + `up -d`, como preâmbulo do S2, nas condições da V-S1 seção 5.1
  (`recriar.sh` versionado verbatim, `podman inspect` e cópia dos `___global.*` antes do `down`,
  portão 5.1 completo antes do L-0, instante de criação e `topologyId` registrados). S2..S7 com o XML
  atual; o XML do B-42.1 só depois da V-S7, como manda o PR §5.
- **P-2:** S4 redefinida como **REP=4R**, réplica da célula da REP=3: *cluster* novo, `COLETA=minima`,
  saúde a 2 s, `G1_LOG_POS_S=30`, janela de 90 s em `isn3`, `REP=4` explícito no `go`. A idade do
  *cluster* (≈ 1 h contra ≈ 10 min na REP=3) é registrada e declarada como diferença. Mais de 60 s
  para, com a leitura "a célula novo + mínima não é estável".
- **P-3:** a REP=2 (188 s) fica como **limitação declarada**, sem explicação. A REP=5 da seção 5.5 de
  `decisoes-senior-2026-10-16.md` é dispensada.
- **P-4:** P-28 aberto em `docs/registro-de-problemas.md` (desligamento abrupto do *host* em 18/10).
- **P-5:** aprovado com os itens (a) a (d) de `docs/pre-registro-rebaseline-complemento-1-2026-10-27.md`:
  (a) parada (h) por `boot_id` da VM, `StartedAt` de `isn1`/`isn3`, ou de `isn2` fora de uma execução
  F1, com descarte da execução em curso por infraestrutura e sem política de reinício nos contêineres;
  (b) `uname -r` e versão do podman registrados por noite; (c) T12 não aplicado, carga em laço fechado
  com 4 *threads* e vazão medida, declarado antes dos dados; (d) DT-015. O PR aprovado não é editado.
  A forma de verificar (h) (checagem no *runner* ou passo manual da checklist) está em aberto; o
  complemento fixa o procedimento manual, que vale em qualquer das duas formas.
- **P-6:** aprovado inteiro. *Host*: alimentação conferida, sem suspensão nem hibernação nas noites,
  Windows Update pausado na janela da campanha, `AutomaticStopAction=ShutDown` na VM `tcc-f3`. VM:
  `apt-mark hold` do kernel e `unattended-upgrades` pausado durante a campanha, feitos numa sessão de VM
  aprovada antes do piloto, com saídas nos brutos.

## DT-016: `numSegments` = 64, desvio declarado de T4, PRÉ-REGISTRO (2026-10-27)

> **Aprovada pelo Gestor em 2026-10-27** (decisão transmitida ao Senior pelo coordenador). Registrada
> antes de qualquer dado do *rebaseline*. Texto também no item (e) (seção 7.2) de
> `docs/pre-registro-rebaseline-complemento-1-2026-10-27.md`.

- **Origem:** T4 da tabela de parâmetros da metodologia fixa 256 segmentos (padrão do Infinispan, fonte
  `infinispan_tuning`). O *cluster* usa `segments="64"` desde o esqueleto de 25/05 (`20b80b1`), com a
  justificativa informal de "menos *state transfer* com 3 nós", sem DT e sem fonte.
  `docs/configuracao-infinispan.md` registrava o ponto como pendente.
- **Decisão:** 64 segmentos em todos os *caches* e em todas as execuções: bateria de junho, gate
  (REP=1..3, G-5, G-6, S1..S7) e *rebaseline*. O valor é mantido por **comparabilidade** com junho e com
  as partições REP=1..3, não por um ganho demonstrado. Nenhuma fonte justifica 64 contra 256, e o efeito
  da diferença não foi medido.
- **Efeito:** nenhum arquivo do *cluster* muda. T4 passa a ser afastamento declarado no texto
  acadêmico, sem afirmar vantagem de 64.

## Decisões do Gestor de 2026-10-27, complemento às decisões sobre a V-S1

- **Verificação de (h):** manual, pela checklist da seção 1.3 do complemento 1 (opção ii). O *runner*
  e a análise não mudam, e os *hashes* da DT-009 parte 2 continuam valendo. Registro na seção 7.1 do
  complemento 1.
- **P-28:** fica em `docs/registro-de-problemas.md` desta branch (`docs/plano-implementacao-controlador`),
  sem *cherry-pick* para `feature/controlador-integracao`.
- **T4:** DT-016.

## DT-017: F1 do *rebaseline* é *crash* silencioso (isolamento da interface e SIGKILL), PRÉ-REGISTRO (2026-10-28)

> **Aprovada pelo Gestor em 2026-10-28** (Q1 = B, Q2..Q6 = Sim; decisão transmitida ao Senior pelo
> coordenador). Registrada antes de qualquer execução do piloto. Texto completo em
> `docs/pre-registro-rebaseline-complemento-2-2026-10-27.md`.

- **Origem:** decisão do Gestor de 2026-10-27 (F1 deixa de ser `podman stop`); levantamento e opções
  em `docs/prompt-f1-kill-2026-10-27.md`; respostas Q1..Q6 de 2026-10-28.
- **Motivo:** `podman stop` é saída ordenada (SIGTERM, *leave*, `ISPN000390`, exit 143), não o
  "*crash* silencioso" de `Metodologia.tex` (F1, T16, T17). Com FD_SOCK2 na pilha, um SIGKILL
  sozinho é detectado pelo fechamento das conexões em segundos, não pelo FD_ALL3.
- **Decisão:** F1 = guarda de *netns*, `ip link set dev eth0 down` no *netns* de `isn2`,
  `podman kill --signal KILL isn2` (ExitCode 137), 60 s, `podman start isn2`, pelo modo padrão
  `silencioso` de `inject-crash.sh`. K6 de F1 exige no `injetor.log` `modo=silencioso`, `eth0 down`,
  `kill isn2`, `ExitCode=137` e `start isn2`. Parecer da fase confere `died` de `isn2` com 137.
- **Rejeitado:** manter `podman stop` (não é *crash*); `podman kill` sozinho (opção A da Q1, não é
  silencioso: detecção pelo FD_SOCK2); retirar o FD_SOCK2 da pilha (muda um fixo do PR §2);
  `podman pause` (não mata a JVM, a volta é *merge*, não *crash-recovery*).
- **Efeito:** muda `scripts/inject-crash.sh`, a função `k6` de `analysis/rebaseline.py` e os testes
  (edições autorizadas na Q2). B-25.1 entra agora só com o carimbo por linha e o exit 5 no `start` com
  erro; marcadores `fault=F1` e `inject-jitter.sh` ficam para depois da campanha (Q3, D4 mantida).
  `--modo stop` continua no injetor, fora do *rebaseline*, para reproduzir junho e para o *smoke*
  comparativo; `run-baseline.sh` passa a usar o modo padrão novo, o que é declarado (Q6). Ensaio na VM
  (complemento 2 / prompt §4.2) em 1 sessão, depois da V-S7 e antes da recriação final do *cluster* e
  do piloto (Q4). Jar, `run-rebaseline.sh`, matriz, sementes, K1..K5, K7..K10, paradas e análise não
  mudam. *Bundle* novo registrado na adenda abaixo. F1 de junho e dos *gates* passa a ser descrito
  como parada ordenada no texto acadêmico.

### Adenda da DT-009 parte 2: ponta e *bundle* depois da DT-017 (2026-10-28)

> Os campos que estavam "a preencher após a entrega do Worker" foram preenchidos numa edição só, em
> 2026-10-28 (tarde), depois da validação, da re-medição pelo coordenador e da integração de
> `fix/f1-crash-silencioso`, antes do piloto e antes de qualquer dado do *rebaseline*. O *bundle* da
> parte 2 (`e304ba4b…`) **não** serve para o piloto nem para as noites, porque traz o F1 antigo; vale
> o desta adenda (`003814e9…`).

- **Origem:** DT-017; integração de `fix/f1-crash-silencioso` em `feature/controlador-integracao`
  (validação `docs/validacao-f1-silencioso-2026-10-28.md`). São identificadores, não
  escolhas. Registrada antes de qualquer execução do piloto. Também registra que a ponta `988fd3c` da
  parte 2 foi seguida por `3c9db71` e `c055399` (matriz no git, G1, G3, G4). Nesses dois *commits* o
  único arquivo do *bundle* tocado foi `runs/rebaseline/matriz.tsv`, que entrou no git com o SHA-256
  já registrado; os demais *blobs* em `c055399` têm os SHA-256 da parte 2 (conferido pelo Senior em
  2026-10-27 para `inject-crash.sh`, `rebaseline.py` e `run-rebaseline.sh`).
- **Ponta:** `20805b092bd12231e1d744c2eb9f073214321207` (`feature/controlador-integracao` depois do
  *fast-forward* 2e8872a → 20805b0). A branch foi entregue com base `c055399`; o coordenador fez um
  *rebase* linear de `c055399..2e5c6ba` sobre `2e8872a`, sem conflito e sem arquivo em comum. *Ref* de
  *backup* `backup/f1-crash-silencioso-pre-rebase` = `2e5c6ba`. Mapa: `2a82d42`→`6bbf88c`,
  `0d955c0`→`c5a7af9`, `0e83ec3`→`1ac38a6`, `db53600`→`1f113bb`, `2e5c6ba`→`20805b0`. Os arquivos
  do F1 ficaram idênticos aos da original. O último commit (`20805b0`, P3) só muda o comentário de
  `scripts/run-baseline.sh`, fora do *bundle*.
- **Jar:** inalterado, `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` (cópia G2).
  Re-hasheado pelo coordenador em 2026-10-28 no arquivo congelado real
  (`dados-vm/rebaseline-2026-10/jar/session-workload-0.1.0-SNAPSHOT.jar`, só leitura; `sha256sum -c`
  do `.sha256` ao lado: OK). Isso fecha a lacuna da seção 1 da validação.
- ***Bundle* da VM** (`git archive` da ponta, LF):

  | Arquivo | SHA-256 |
  |---|---|
  | `analysis/rebaseline.py` | `3005f49ae5696dcd9ace324b08295187100fcde39c111115a89c517bc4e3c7cd` |
  | `runs/rebaseline/matriz.tsv` | `8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35` |
  | `scripts/inject-crash.sh` | `5b4d571986256d96c73405ac887eca55c783787ad0bc0a0c5dec558856ddc0cf` |
  | `scripts/inject-jitter.sh` | `7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529` |
  | `scripts/rebaseline_matriz.py` | `3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a` |
  | `scripts/run-rebaseline.sh` | `d95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366` |
  | `workload/target/session-workload-0.1.0-SNAPSHOT.jar` | `5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf` |

  SHA-256 da lista acima no formato do `sha256sum` (`<hash> *<caminho>`, em ordem):
  `003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4` (substitui `e304ba4b…` para o
  piloto e as noites). Na VM, `sha256sum -c` arquivo a arquivo.
- **Conferência dos *hashes*.** Três medições independentes dão os mesmos valores: o Worker
  (`git archive` de `0e83ec3` e `db53600`), o Senior na validação (mesmo método) e o coordenador
  (cópia `git archive` de `db53600` e de `20805b0`, jar congelado re-hasheado). O Senior conferiu
  também, nesta edição, os *blobs* de `20805b0` por `git show 20805b0:<caminho> | sha256sum` e a
  lista resultante (`003814e9…`). Os quatro arquivos fora do F1 (`matriz.tsv`, `inject-jitter.sh`,
  `rebaseline_matriz.py`, `run-rebaseline.sh`) e o jar têm os mesmos *hashes* da parte 2.
- **Re-medição na ponta** (coordenador, Worker independente, 2026-10-28): em `db53600`,
  `inject-crash.test.sh` 113/0 (contra o injetor de `c055399`: 56/57); mutantes M2 86/27, M3 110/3,
  M4 108/5, M5 108/5; `run-rebaseline.test.sh` 1 rodada 130/0 (1114 s, aviso R4 a +51 440 ms,
  esperado, acima do piso N + 48 s); `inject-jitter` 35/0, `inject-partition` 125/0/2 *skip*,
  `inject-netdegrade` 298/0/1 *skip*; `test_rebaseline.py` 69 OK, `test_rebaseline_matriz.py` 8 OK,
  `test_contrato_csv.py` 9 OK, `test_contrato_csv_v2.py` 6 OK. Em cópia `git archive` de `20805b0`:
  Surefire 863 testes / 0 falhas / 0 erros / 1 *skip*, 122 arquivos (igual a `2e8872a`; o F1 não tem
  Java); `unittest discover -s analysis` 187 OK, 1 *skip* (181 + 6 do F1); `inject-crash.test.sh`
  113/0; *hashes* do *bundle* iguais aos da tabela.
- **Nota de execução (P1 da validação, aprovada pelo Gestor em 2026-10-28):** a forma literal do
  comando no injetor é `podman kill isn2 --signal KILL` (a *flag* depois do nome), com a mesma
  semântica do `podman kill --signal KILL isn2` escrito no complemento 2 e na DT-017. O *smoke* deu
  ExitCode 137 em todos os `kill`; o ensaio da VM (complemento 2 / prompt §4.2) confirma no Podman da
  VM. O complemento 2 e a DT-017 não são editados.
- **Nota interpretativa (P2 da validação, aprovada pelo Gestor em 2026-10-28):** a conferência h3 do
  complemento 2 §4 lê-se "`died` de `isn2` dentro de uma execução F1 com código de saída **≠ 137** ⇒
  recusa K6 dessa execução", e não só "= 143". É mais estrita que o texto e coerente com a DT-017
  (o positivo é 137). Motivo: no *smoke* local uma parada ordenada saiu com ExitCode 0, que escaparia
  da regra literal. O K6 já recusa esse caso, porque exige `ExitCode=137` e `kill isn2` no *log*.
- **Data do *smoke* do complemento 2 §4** (*lock* `___global.lck` deixado pelo SIGKILL não impede a
  subida na 15.0.21): *smoke* local de **2026-10-27/28**, brutos em
  `runs/smoke-f1-silencioso-2026-10-27/` (o nome do diretório leva a data do plano; os eventos dos
  ensaios têm carimbo de 2026-10-28 UTC, o último às 05:26:32Z). Nos ensaios de `kill` e silencioso
  o `.lck` parado tinha 0 byte, `ISPN000693` não apareceu e `ISPN000389` ("Loaded global state")
  apareceu na vida nova (relatório do Worker `docs/relatorio-f1-silencioso-2026-10-27.md` §7). Faixa
  de detecção observada no modo silencioso: Δdet 36,5 a 41,9 s (FD_ALL3), registrada como observação,
  sem mínimo fixado.
- **Pendente (só na VM):** instante UTC de criação do *cluster* depois do S7 e do ensaio do F1
  (`--cluster-criado-apos`), como na parte 2.
- **Dependências na VM** para o F1 novo: `nsenter` (util-linux) e `ip` (iproute2), as mesmas do F3;
  conferidas pelo pré-voo do *runner* (`nsenter`, `tc`) e por `command -v ip` no bloco de abertura
  da fase.

## Decisões do Gestor de 2026-10-28

Todas pela recomendação do Senior; transmitidas pelo coordenador.

- **F1 (`docs/prompt-f1-kill-2026-10-27.md` §9):** Q1 = B, Q2 = Sim, Q3 = Sim, Q4 = Sim, Q5 = Sim,
  Q6 = Sim. Complemento 2 aprovado; DT-017 e adenda da DT-009 parte 2 acima. PR e complemento 1 não
  editados. Implementação em andamento (Worker, `fix/f1-crash-silencioso`).
- **V-B26.2/B27 (`docs/validacao-b26-2-b27-2026-10-27.md` §5):** H1..H8 aprovadas. H1..H3
  (rebase local de `72613cf` + `ce64576` antes de `7ad2fb8` com mapa de SHAs e *ref* de *backup*;
  `.gitattributes` `* -text` em `runs/b26-2-smoke/` com `--renormalize`; ordem B-26.2 → B-27 → B-37 →
  B-39) em execução por um Worker. **H4..H8 são requisitos dos prompts do B-28.2 e da leitura do P3**
  e devem ser copiados para eles:
  - **H4:** no B-28.2, a sonda normaliza `falha:TimeoutException` para `timeout`, com teste, sem
    reabrir o B-26.2.
  - **H5:** protocolo local do P3 fixo em 180 s (ou HEALTHY com 3 nós + 30 s).
  - **H6:** `nosEsperados` = número de itens de `--servers`, sem opção nova de CLI, gravado na linha de
    metadados do JSONL.
  - **H7:** "I1 = I4 = 0" conta como zero violações; `i1_concorrentes` fica fora do critério (DT-001).
  - **H8:** P3 lido em duas partes, e a cópia do `mix64` fica amarrada por `formulaMix64`.
- **V-B37/B39 (`docs/validacao-b37-b39-2026-10-27.md` §8):** G1..G5 aprovadas.
  - **G1:** integrar B-37 e depois B-39, na ordem da seção 7 (compatível com a H3).
  - **G2:** `comparar_bracos.py` roda com `python -E -P`, não `-I`.
  - **G3:** com taxas que têm zeros, a análise principal é a diferença de taxas; a razão com exclusão é
    sensibilidade. Fixar no pré-registro do B-40.
  - **G4:** `fix/b39-guarda-parametros-energia` (B39-R1: exit 2 quando `w_recovery_s` ou
    `marcadores` divergirem; B39-R2: marca própria `sem-amostra-valida`) entra **antes** do B-40.
  - **G5:** exceção no `.gitignore` para `recursos-rep-*.csv` e `eol=lf` para json/jsonl/csv/md ficam
    para depois da campanha do *rebaseline*, em `chore/` separadas.
- **V-S2 R-1 (`wt-gate-v4/runs/vm-s2-2026-10-27/V-S2.md`):** opção (a). O Worker corrige
  `l0c.sh:68` para `systemctl show <timer> -p AccuracyUSec`, ajusta o teste por *shims* e gera
  *bundle* novo com *hash*; a VM recebe esse *bundle* antes do S3. Em andamento.

## Decisões do Gestor de 2026-10-28 (tarde)

Transmitidas ao Senior pelo coordenador.

- **Estabilidade do *host* (resposta à P-2 da V-S4).** Decisão do Gestor, literal: o Gestor atribui
  os desligamentos abruptos do *host* a quedas de energia na região e decidiu que **não** serão
  feitas verificações de hardware (*dumps*, *microcode*, *memtest*). Os itens (i) a (iii) da
  recomendação da P-2 (abrir os *dumps* com `!analyze -v`, conferir o *microcode* e os limites de
  potência do BIOS 1.O0, teste de memória e de CPU) **não** são adotados.
  - **Fato registrado, sem contestar a decisão** (V-S4, `wt-gate-v4/runs/vm-s4-2026-10-28/V-S4.md`,
    seção "Host", e `host-eventos-desligamentos.log`, leitura só de eventos do Windows): dois dos
    eventos têm *bugcheck* registrado, 0x1CA em 2026-09-13 21:24:43 local (*dump* em
    `C:\WINDOWS\MEMORY.DMP`) e 0x154 (`UNEXPECTED_STORE_EXCEPTION`) em 2026-10-28 03:38:27 local
    (*minidump* `C:\WINDOWS\Minidumps\102826-88640-01.dmp`), ambos com WER 1001. Foram **17**
    `Kernel-Power 41` desde 01/09 (34 desde 01/08); os demais têm `BugcheckCode=0`.
  - **Consequência operacional** (nada novo é pedido): as salvaguardas das campanhas contra uma queda
    do *host* são a parada (h) por `boot_id` da VM e `StartedAt` dos contêineres (complemento 1,
    item (a), verificada pela checklist manual da seção 1.3, decisão de 27/10) e a DT-014 (execução
    interrompida descartada e refeita por infraestrutura). A mitigação da P-6 da V-S1 (alimentação,
    sem suspensão, Windows Update pausado, `apt-mark hold`) continua valendo.
- **V-S4 (`wt-gate-v4/runs/vm-s4-2026-10-28/V-S4.md`): P-1..P-7 aprovadas pela recomendação**, com
  a ressalva acima para a P-2.
  - **P-1:** V-S4 aprovada (APROVADO com ressalvas); preparação do S5 liberada.
  - **P-2:** ver o item anterior. Para S5 a S7 (curtos, com o Gestor presente) vale o resto da
    recomendação: registro de `boot_id` no início e no fim do invólucro e o critério de validade
    "intervalo máximo do `saude-log` ≤ 2 × intervalo".
  - **P-3:** só o `.gz` do `journal-boot-anterior.txt` no git; o original (50 409 360 bytes, SHA-256
    em `copia-host.sha256`) fica no disco do *host*.
  - **P-4:** `.gitattributes` com `-text` para os brutos de `runs/vm-s*/` (S2, S3 e S4), num commit
    `chore:` separado, feito pelo Worker em `wt-gate-v4` (`test/b29-gate-v4`).
  - **P-5:** R-1 fechada pelo coordenador: na VM, `sha256sum -c copia-host.sha256` deu 35/35 OK,
    rc = 0. O coordenador coletou também `journalctl -b -1 -k --since "2026-10-28 06:34"` e
    `journalctl -b -1 -p warning --since "2026-10-28 06:34"`; os dois deram "-- No entries --"
    (`wt-gate-v4/runs/vm-s4-2026-10-28/journal-b-1-kernel-warning-pos-queda.log`, a commitar pelo
    Worker de `wt-gate-v4`). *Lock* da VM livre; *cluster* HEALTHY.
  - **P-6:** GO novo para o L-1. O S5 roda com o Gestor presente, digitando o GO no *prompt*, como no
    S4; o GO de `docs/decisoes-senior-2026-10-16.md:458` não vale mais.
  - **P-7:** queda de 28/10 e histórico do *host* acrescentados ao P-28 de
    `docs/registro-de-problemas.md` (onde o P-28 mora, decisão de 27/10).
- **F1 silencioso (`docs/validacao-f1-silencioso-2026-10-28.md` §4): P1, P2 e P3 aprovadas pela
  recomendação.** P1 e P2 entram como notas na adenda da DT-009 parte 2, acima; o complemento 2 e a
  DT-017 não são editados. P3: o commit do comentário de `scripts/run-baseline.sh:11` (`2e5c6ba` na
  branch original, `20805b0` depois do *rebase*) **já existia quando a resposta chegou**: o Worker o
  fez antes da aprovação. O conteúdo é o recomendado (uma linha, fora do *bundle*, sem efeito em
  *hash*), e a aprovação vale para ele como está. Fica registrado que a ordem foi invertida.
- **Re-medição da §5 da validação (coordenador, Worker independente, cópia de `db53600`): tudo
  conforme.** Números e *hashes* na adenda da DT-009 parte 2. O jar congelado real foi re-hasheado
  (`5eced136…`), o que fecha a lacuna da §1 da validação. Relatório do coordenador no *scratchpad*
  da sessão (`remedicao-f1-e-surefire.md`), fora do git.
- **Integração (coordenador):** *rebase* linear de `c055399..2e5c6ba` sobre
  `feature/controlador-integracao` (`2e8872a`), sem conflito; *ref* de *backup*
  `backup/f1-crash-silencioso-pre-rebase` = `2e5c6ba`; mapa de SHAs na adenda; *fast-forward*
  local `2e8872a` → **`20805b0`**. Medido em cópia `git archive` de `20805b0`: Surefire 863/0/0/1 em
  122 arquivos, `unittest discover -s analysis` 187 OK (1 *skip*), `inject-crash.test.sh` 113/0.
  Nada foi para `origin`.
