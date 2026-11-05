# Estrutura de Capítulos

> Mantido pelo **Bibliotecário**; mudanças exigem validação do **Gestor**.
> Estado: **estrutura de 5 capítulos aprovada** (Decisão 011, 2026-05-13 — ponto 8). Modelo LaTeX: classe **`abntex2`** (Decisão 002) — esqueleto em [`Main-Monografia.tex`](../Main-Monografia.tex).
> Metodologia: mapeamento sistemático cobrindo **todos os 8 eixos (A-H)** — Decisão 011 ponto 3.

## Situação atual (arquivo base — Proposta)

[`Main-PPEC.tex`](../Main-PPEC.tex) é a **Proposta de TCC** (não a monografia). Classe `article` + pacote `ppec.sty` (modelo da Unipampa). Seções:

| Seção (Proposta) | `\label` | Conteúdo | Reaproveitamento na monografia |
|---|---|---|---|
| Introdução | `sec-introducao` | Contexto, problema, abordagem proposta + sustentabilidade computacional (5 parágrafos) | → Cap. 1 (expandir; separar justificativa) |
| Objetivos | `sec-objetivos` | Pergunta de pesquisa + objetivo geral + 5 específicos | → Cap. 1 (seção de objetivos) |
| Metodologia | `sec-metodologia` | Mapeamento sistemático + Frente experimental + controlador heurístico | → Cap. 3 (expandir) |
| Cronograma | `sec-cronograma` | Quadros 1 (TCC-I) e 2 (TCC-II), 12 semanas cada | → Cap. 3 (cronograma TCC-I no corpo); Apêndice B (Quadro 2 + narrativa) |
| Referências | — | 14 entradas (9 originais + 5 acrescidas) | → expande durante o MS |
| Assinaturas | — | Orientando + Orientador | → sai na monografia |

## Estrutura aprovada da monografia — 5 capítulos (Decisão 011, ponto 8)

### Cap. 1 — Introdução  → `capitulos/capitulo-01-introducao.md`
- 1.1 Contextualização (sistemas distribuídos + IMDG + replicação + falhas)
- 1.2 Problema de pesquisa (transcreve pergunta do `Main-PPEC.tex` linha 49 — Decisão 011 ponto 2)
- 1.3 Justificativa (P-002 — incorporar ângulo de sustentabilidade computacional via REF-039 e REF-040)
- 1.4 Objetivos (geral e específicos)
- 1.5 Organização do texto

### Cap. 2 — Fundamentação Teórica  → `capitulos/capitulo-02-fundamentacao-teorica.md`
*Estrutura consolidada para 7 seções na v0.1 escrita em 2026-05-14, em conformidade com a regra de não fragmentação. Fusões aplicadas em relação à estrutura inicial de 10 subseções: 2.1+2.3+2.4 → nova 2.1; 2.2+2.8 → nova 2.2. As seções restantes mantêm escopo.*

- 2.1 Sistemas distribuídos: consistência, replicação e falhas (REF-001, 002, 010, 011, 012, 013, 016, 032, 033, 034)
- 2.2 \emph{In-Memory Data Grids} e a plataforma Infinispan (REF-006, 007, 008, 009, 014)
- 2.3 Especificação formal e \emph{model checking} (REF-003, 017, 018, 019, 020)
- 2.4 Latência de cauda, objetivos de nível de serviço e observabilidade (REF-004, 005, 015, 021, 022, 024)
- 2.5 Sistemas autoadaptativos e controle por realimentação (REF-025, 026, 027, 028, 029)
- 2.6 Gerência de sessão como domínio (REF-035, 036, 031)
- 2.7 Trabalhos relacionados (REF-023, + itens do mapeamento sob aprovação)

> Conteúdo arquivado em [`12-revisao-dirigida-tcc1-sem3-4.md`](12-revisao-dirigida-tcc1-sem3-4.md) é insumo seletivo (Decisão 011 ponto 5) e pode alimentar a futura versão 0.2, em particular na seção 2.7, quando os candidatos do mapeamento forem promovidos a REFs oficiais.

### Cap. 3 — Metodologia  → `capitulos/capitulo-03-metodologia.md`
- 3.1 Tipo e enquadramento da pesquisa
- 3.2 Frente 1 — Mapeamento sistemático (questões, bases, *strings*, critérios, esquema de classificação, extração) — base em REF-030 + REF-037 + REF-038
- 3.3 Frente 2 — Implementação e validação experimental
  - 3.3.1 Ambiente experimental (Infinispan + observabilidade)
  - 3.3.2 *Workload* e checagens de invariantes
  - 3.3.3 Modelo formal (especificação inicial, sem falhas — Decisão 011 ponto 10)
  - 3.3.4 Cenários de injeção de falhas em TCC-I: F1 *crash* e F3 latência/jitter (Decisão 011 ponto 11); F2 e F4 para TCC-II
- 3.4 Métricas e critérios de avaliação (M1-M7 — [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md))
- 3.5 Procedimento de análise estatística (P-011)
- 3.6 Ameaças à validade (P-007)
- 3.7 Cronograma de TCC-I (Quadro 1 da Proposta transcrito/comentado)

### Cap. 4 — Desenvolvimento e Resultados Parciais  → `capitulos/capitulo-04-desenvolvimento.md` *(FUNDIDO — Decisão 011 ponto 8)*
*Capítulo que mistura artefatos produzidos e seus primeiros resultados em TCC-I.*

- 4.1 Modelo formal inicial: estado de sessão, operações O1-O7, canal assíncrono abstrato, invariantes I1-I6 (sem falhas no modelo)
- 4.2 Propriedades verificadas e resultados do *model checking* inicial (Sem 9-10)
- 4.3 Arquitetura experimental implementada (cluster Infinispan + observabilidade; `/workspace/prototipo/` — Decisão 011 ponto 16)
- 4.4 *Workload* implementado e instrumentação (latência, checagens de invariantes)
- 4.5 *Baseline* experimental parcial: cenários F1 (*crash*) e F3 (latência/jitter)
- 4.6 Análise comparativa formal × experimental (preliminar): mapeamento de contraexemplos do MC para padrões observáveis no *baseline*
- 4.7 Discussão dos resultados parciais

### Cap. 5 — Conclusões Parciais  → `capitulos/capitulo-05-resultados.md` *(antigos Cap. 5 e 6 funcionalmente comprimidos aqui)*
- 5.1 Síntese das contribuições de TCC-I
- 5.2 Limitações conhecidas (do MS, do modelo formal inicial, do *baseline* parcial)
- 5.3 Plano para TCC-II (resumo — detalhamento completo no Apêndice B)
- 5.4 Considerações finais

> Arquivos `capitulos/capitulo-06-conclusao.md` continuam existindo, mas o Cap. 6 sai da estrutura final — conteúdo migra para o novo Cap. 5.

### Elementos pré-textuais / pós-textuais (classe `abntex2` — Decisão 002)
Capa; folha de rosto; folha de aprovação (TCC-I geralmente só com o orientador); resumo (PT) e *abstract* (EN) com palavras-chave; listas de figuras/tabelas/abreviaturas/símbolos (conforme houver); sumário; referências; apêndices.

**Apêndices previstos:**
- **Apêndice A — Protocolo do Mapeamento Sistemático** (Sem 1-2 produz; Sem 3-4 atualiza com fluxograma PRISMA preenchido; Sem 5-6 finaliza com tabela de extração).
- **Apêndice B — Plano de trabalho para TCC-II** (Quadro 2 da Proposta + narrativa expandida — Decisão 011 ponto 13).
- **Apêndice C — Especificação formal** (TLA+/Alloy/Must, conforme P-017 decidida em Sem 7-8).
- **Apêndice D — Configurações do cluster Infinispan e scripts** (do diretório `prototipo/`).

## Divisão TCC-I × TCC-II

- **TCC-I (12 semanas — Decisão 010/011):** Caps. 1-5 (na nova numeração) **completos** (com Caps. 4 e 5 contendo resultados *parciais*) + Apêndices A, B, C, D parcialmente preenchidos. Defesa de TCC-I é entregável de Sem 11-12 (Decisão 011 ponto 15).
- **TCC-II (12 semanas):** evolução do modelo formal (com falhas); execução completa do *baseline* (F2 + F4); controlador heurístico; execução comparativa; análise final. Caps. 4 e 5 da monografia são **reescritos/expandidos** em TCC-II; Apêndice B desaparece (vira corpo do trabalho); apêndices técnicos crescem.

## Validações

- ✅ Conjunto compatível com [`Main-Monografia.tex`](../Main-Monografia.tex) (revisar `\chapter`s ao iniciar Sem 5-6 / Sem 11-12).
- ✅ Cobre todas as REFs aprovadas (REF-001..041) — cada uma tem um capítulo/seção atribuído em [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md).
- ✅ Cronograma TCC-I (Sem 12: "Escrita ... metodologia, resultados parciais") compatível com a fusão Caps. 4+5.