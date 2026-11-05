# Capítulo 5 — Resultados e Discussão

> Controle de produção do capítulo. **Novo** — conteúdo de **TCC-2** (resultados parciais podem aparecer em TCC-1, conforme o cronograma do base).

## Objetivo do capítulo
Apresentar e discutir os resultados: *baseline* estático sob falhas; configuração com controlador heurístico; análise comparativa (taxa de violações; latência p95/p99; tempo de recuperação/convergência); relação entre os contraexemplos do modelo formal e os padrões observados experimentalmente; discussão das limitações e ameaças à validade.

## Relação com o problema de pesquisa
Capítulo que **responde empiricamente** à questão norteadora (P-001): o controlador heurístico reduz violações e controla latência sob falhas, comparado ao *baseline* estático?

## Seções previstas
- 5.1 *Baseline* estático sob falhas (por cenário: crash/restart; latência/*jitter*; partição; *state transfer*; multi-site)
- 5.2 Configuração com controlador heurístico (mesmos cenários; decisões registradas)
- 5.3 Análise comparativa (violações; p95/p99; recuperação/convergência; significância)
- 5.4 Relação com os contraexemplos do modelo formal
- 5.5 Discussão (limitações dos resultados; ameaças à validade)

## Referências necessárias
REF-004 (Tail at Scale — interpretação de p95/p99), REF-005 (SLO), REF-008 (métricas), REF-003 (contraexemplos) — aprovadas (herdadas). A obter: CAND-020 (análise estatística), CAND-018 (Jepsen — para a discussão de trabalhos próximos).

## Referências aprovadas
REF-003, REF-004, REF-005, REF-008 *(herdadas — P-014)*.

## Versão atual em LaTeX
Inexistente. (Sem dados experimentais ainda.)

## Revisões realizadas
Nenhuma. ([`../revisoes/revisao-capitulo-05.md`](../revisoes/revisao-capitulo-05.md).)

## Pendências
P-011 (plano de análise estatística), P-007 (ameaças à validade), P-008, P-013, P-015.

## Status
**Não iniciado** — depende da execução dos experimentos (TCC-2).
