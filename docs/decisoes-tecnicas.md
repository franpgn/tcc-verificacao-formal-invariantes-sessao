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

- **Origem:** adendo do B-43.7, seção 6, ponto 2; decisão do Gestor de 2026-10-22.
- **Decisão:** o padrão do `--mix` continua `v2` (os testes existentes fixam isso). Toda execução de
  medição (B-42.3/B-43.3b, *rebaseline*, matriz) passa `--mix v3` explicitamente, e o *dry-run*/stdout
  registram a versão.
- **Também aprovado no mesmo dia:** a trava por faixa de *slot* no Logout isolado e a geração 0
  INVALID na carga de v3. As duas são artefatos declarados do *workload*.
