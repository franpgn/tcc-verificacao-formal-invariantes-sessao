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
