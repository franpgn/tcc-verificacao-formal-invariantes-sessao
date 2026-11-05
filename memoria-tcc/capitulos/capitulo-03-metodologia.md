# Capítulo 3 — Metodologia

> Controle de produção do capítulo. Fonte de partida: seção "Metodologia" de `Main-PPEC.tex` (texto já razoavelmente desenvolvido). Detalhamento em [`../02-metodologia.md`](../02-metodologia.md).

## Objetivo do capítulo
Descrever o método de pesquisa: enquadramento; trilha de especificação e verificação formal; trilha de implementação e validação experimental; cenários de injeção de falhas; o controlador heurístico; métricas e critérios de avaliação; procedimento de análise; ameaças à validade.

## Relação com o problema de pesquisa
Capítulo que **operacionaliza** a investigação do problema: como os invariantes serão especificados e verificados, como as violações serão detectadas experimentalmente, e como o controlador heurístico será avaliado contra o *baseline* estático.

## Seções previstas
- 3.1 Tipo e enquadramento da pesquisa — **NOVA** (P-005)
- 3.2 Trilha de especificação e verificação formal — *(do base; expandir, formalizar)*
- 3.3 Trilha de implementação e validação experimental — *(do base; expandir)*
- 3.4 Cenários de injeção de falhas — *(do base: 5 cenários parametrizados)*
- 3.5 Métricas e critérios de avaliação — *(do base: taxa de violações; p95/p99; tempo de recuperação/convergência)*
- 3.6 Procedimento de análise (repetições, testes estatísticos, *outliers*) — **EXPANDIR** (P-011)
- 3.7 Ameaças à validade — **NOVA** (P-007)

## Referências necessárias
- Aprovadas (herdadas): REF-003 (model checking), REF-006 (Infinispan docs), REF-007 (xsite), REF-008 (Infinispan metrics), REF-004 (Tail at Scale — p95/p99) — todas já citadas na Metodologia do base.
- A obter: CAND-010 (Specifying Systems) ou CAND-017 (chaos engineering) para a injeção de falhas; CAND-019 (design science) e CAND-020 (experimentação em ES) para 3.1, 3.6, 3.7. — depende de P-015.

## Referências aprovadas
REF-003, REF-004, REF-006, REF-007, REF-008 *(herdadas — P-014)*.

## Versão atual em LaTeX
Conteúdo de origem disponível em `Main-PPEC.tex`, `\section{Metodologia}` (≈5 parágrafos: visão geral em duas trilhas; trilha formal; verificação por model checking; trilha experimental; injeção de falhas; controlador heurístico). **Reaproveitável com expansão** — é o capítulo mais maduro do base. Versão de monografia a ser produzida após Decisão 001 e P-008.

## Revisões realizadas
Nenhuma. ([`../revisoes/revisao-capitulo-03.md`](../revisoes/revisao-capitulo-03.md).)

## Pendências
P-005 (enquadramento + ref. metodológica), P-007 (ameaças à validade), P-008 (estrutura), P-011 (plano estatístico), P-017 (ferramenta de model checking), P-015 (busca).

## Status
**Não iniciado** (mas com base de partida sólida no arquivo base).
