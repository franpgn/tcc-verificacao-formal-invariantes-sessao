# Capítulo 1 — Introdução

> Controle de produção do capítulo. Fonte de partida: seções "Introdução" e "Objetivos" de `Main-PPEC.tex`.

## Objetivo do capítulo
Apresentar o contexto (sistemas distribuídos com estado de sessão externalizado em *data grids*; replicação síncrona vs. assíncrona; *failover*, partições, *rebalancing*/*state transfer*), delimitar o problema (violação de invariantes de sessão sob replicação assíncrona e falhas), justificar a relevância do estudo, enunciar os objetivos (geral e específicos) e descrever a organização do texto.

## Relação com o problema de pesquisa
Capítulo que **enuncia** o problema e a abordagem proposta (especificação formal + *model checking* + controlador heurístico + validação experimental em Infinispan). Define o escopo que os capítulos seguintes detalham e avaliam.

## Seções previstas
- 1.1 Contextualização — *(adaptar dos §1–§3 da Introdução do base)*
- 1.2 Problema de pesquisa — *(do §4 da Introdução do base; explicitar os invariantes-alvo)*
- 1.3 Justificativa — **NOVA** (não existe no base — P-002)
- 1.4 Objetivos (geral e específicos) — *(transcrever da seção "Objetivos" do base — já está pronta)*
- 1.5 Organização do texto — **NOVA**

## Referências necessárias
- Aprovadas (herdadas): REF-001 (Dynamo), REF-002 (CRDT), REF-003 (model checking TLA+), REF-004 (Tail at Scale), REF-005 (SLO/SRE), REF-007 (xsite) — todas já citadas na Introdução do base.
- A obter (candidatas): CAND-002 (CAP), CAND-007 (network is reliable) e CAND-011 (AWS formal methods) para reforçar a **justificativa**; possivelmente CAND-021 (gerência de sessão) para motivar os invariantes. — depende de P-015.

## Referências aprovadas
REF-001, REF-002, REF-003, REF-004, REF-005, REF-006, REF-007 *(herdadas — em uso; confirmação formal do Gestor pendente, P-014)*. Todas usadas na v0.1 são citações reais a chaves existentes em `bibliografia.bib`.

## Versão atual em LaTeX
**Versão 0.1** — escrita no arquivo `Main-Monografia.tex` (raiz do projeto), capítulo `\chapter{Introdução}` (`\label{cap:introducao}`), com as seções: 1.1 Contextualização; 1.2 Problema de pesquisa; 1.3 Justificativa; 1.4 Objetivos (geral + específicos); 1.5 Organização do texto. Adaptada da Introdução/Objetivos de `Main-PPEC.tex`, expandida com a Justificativa e a Organização do texto. **Restrição:** usa só as 9 referências herdadas (Decisão 004); 3 afirmações da Justificativa estão marcadas com `% [FONTE NECESSÁRIA: ...]` (dependem de CAND-007, CAND-011, CAND-018 — buscas suspensas).

| Versão | Data | Objetivo | Alterações | Refs usadas | Status |
|---|---|---|---|---|---|
| 0.1 | 2026-05-11 | 1ª redação do Cap. 1 da monografia | criação a partir do arquivo base; nova Justificativa (parcial) e Organização do texto; conversão para `abntex2`/`\citeonline` | REF-001, REF-002, REF-003, REF-004, REF-005, REF-006, REF-007 | revisado (1ª rodada); **aguardando aprovação do Gestor** |

## Revisões realizadas
1ª rodada — [Revisor], 2026-05-11. Registro em [`../revisoes/revisao-capitulo-01.md`](../revisoes/revisao-capitulo-01.md). Resumo: estrutura aprovada; 3 marcadores `% [FONTE NECESSÁRIA]` na Justificativa são pendências legítimas (não remover sem fonte); 1.1 deve ser **expandida** (não só transposta da Proposta); considerar parágrafo de delimitação em 1.2 (P-016); 1.5 a atualizar quando a estrutura final for aprovada (P-008); resumo/abstract a reescrever ao final (P-019).

## Pendências
P-002 (completar Justificativa — bloqueada por P-015/Decisão 004), P-003 (fora de escopo — incluir em 1.2 ou Cap. 3), P-008 (estrutura final → afeta 1.5), P-014 (confirmar biblioteca herdada), P-016 (delimitação dos invariantes), P-019 (resumo/abstract finais).

## Status
**v0.1 redigida e revisada (1ª rodada) — aguardando aprovação do Gestor.** Próxima versão (0.2) após: (a) decisão do Gestor sobre autorizar busca para fechar a Justificativa; (b) aprovação da estrutura de capítulos (P-008).
