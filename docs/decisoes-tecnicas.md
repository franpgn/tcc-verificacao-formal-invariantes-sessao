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
