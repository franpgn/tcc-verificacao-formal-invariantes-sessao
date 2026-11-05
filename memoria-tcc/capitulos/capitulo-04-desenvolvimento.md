# Capítulo 4 — Desenvolvimento

> Controle de produção do capítulo. **Novo** — o arquivo base descreve a *intenção* (na Metodologia) mas não traz os artefatos. Conteúdo a ser produzido majoritariamente em **TCC-2** (parte do modelo formal pode entrar em TCC-1).

## Objetivo do capítulo
Apresentar os artefatos construídos: (i) o modelo formal do estado de sessão e das operações; (ii) o canal de replicação assíncrona e o modelo de falhas; (iii) os invariantes/propriedades verificados e os resultados do *model checking* (incl. contraexemplos); (iv) a arquitetura experimental (cluster Infinispan, configuração de caches, *cross-site*); (v) o *workload* e a instrumentação; (vi) o controlador heurístico (observáveis, regras, histerese, perfis).

## Relação com o problema de pesquisa
É a **resposta construtiva** ao problema: o modelo formal expõe as violações possíveis; a arquitetura experimental as reproduz e mede; o controlador heurístico as mitiga.

## Seções previstas
- 4.1 Modelo formal do estado de sessão e das operações (login, validação, renovação, invalidação, contadores, *flags*)
- 4.2 Canal de replicação assíncrona; modelo de falhas (crash/restart, partição) e abstração de *state transfer*
- 4.3 Invariantes e propriedades verificadas; configuração do *model checking*; análise de contraexemplos
- 4.4 Arquitetura experimental: cluster Infinispan, caches distribuídos, (multi-site) *cross-site replication*
- 4.5 *Workload* tipo sessão e instrumentação (latência, códigos de retorno, checagens de invariantes)
- 4.6 Controlador heurístico: observáveis, regras determinísticas, histerese, limites de taxa de mudança, perfis (normal × conservador)

## Referências necessárias
REF-003 (model checking), REF-006/007/008 (Infinispan), REF-004/005 (p95/p99, SLO), REF-001/002 (reconciliação/convergência) — já aprovadas (herdadas). A obter: CAND-010 (TLA+), CAND-014/016 (MAPE-K / feedback control) para 4.6.

## Referências aprovadas
REF-001, REF-002, REF-003, REF-004, REF-005, REF-006, REF-007, REF-008 *(herdadas — P-014)*.

## Versão atual em LaTeX
Inexistente. (Artefatos de código/especificação ainda não existem.)

## Revisões realizadas
Nenhuma. ([`../revisoes/revisao-capitulo-04.md`](../revisoes/revisao-capitulo-04.md).)

## Pendências
P-008 (estrutura), P-016 (conjunto de invariantes), P-017 (ferramenta de model checking), P-013 (TCC-1 × TCC-2 — define se algo deste capítulo já entra agora), P-015 (busca).

## Status
**Não iniciado** — depende de decisões de escopo e da produção dos artefatos.
