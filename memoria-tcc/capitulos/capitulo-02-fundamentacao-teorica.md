# Capítulo 2 — Fundamentação Teórica

> Controle de produção do capítulo. **Capítulo inteiramente novo — não existe no arquivo base** (P-009).

## Objetivo do capítulo
Reunir e organizar a base conceitual necessária ao trabalho: modelos de consistência em sistemas distribuídos; IMDGs; replicação síncrona/assíncrona e reconciliação; falhas/*failover*/partições; métodos formais e *model checking*; SLO e latência de cauda; sistemas autoadaptativos e controle por realimentação; o Infinispan como plataforma; e os trabalhos relacionados.

## Relação com o problema de pesquisa
Fornece o vocabulário e os resultados teóricos que (i) fundamentam a formulação dos invariantes, (ii) justificam o uso de *model checking* e (iii) embasam o desenho do controlador heurístico (laço de observação→análise→decisão→ação, histerese, anti-*flapping*).

## Seções previstas
- 2.1 Sistemas distribuídos e modelos de consistência (causal, linearizabilidade, eventual)
- 2.2 *In-Memory Data Grids* (IMDG): particionamento, replicação, *rebalancing*, *state transfer*
- 2.3 Replicação síncrona × assíncrona; reconciliação e convergência (CRDTs)
- 2.4 Falhas, detectores de falha, *failover* e partições de rede
- 2.5 Métodos formais: especificação e *model checking* (TLA+/TLC)
- 2.6 SLO, SLI, latência de cauda (p95/p99) e observabilidade
- 2.7 Sistemas autoadaptativos e controle por realimentação (histerese, anti-*flapping*)
- 2.8 Infinispan: caches distribuídos, *cross-site replication*, métricas
- 2.9 Trabalhos relacionados (P-010)

## Referências necessárias
**Em grande parte ainda não disponíveis.** Mapeamento provisório (ver [`../05-referencias-candidatas.md`](../05-referencias-candidatas.md)):
- 2.1 → CAND-001 (Lamport clocks), CAND-002 (CAP), CAND-005 (linearizabilidade)
- 2.2 → REF-006, REF-009; CAND-008 (survey IMDG)
- 2.3 → REF-001 (Dynamo), REF-002 (CRDT); CAND-003 (SMR), CAND-004 (Raft)
- 2.4 → CAND-006 (failure detectors), CAND-007 (network is reliable); REF-007
- 2.5 → REF-003 (model checking TLA+); CAND-010 (Specifying Systems), CAND-011 (AWS), CAND-012 (livro Model Checking)
- 2.6 → REF-004 (Tail at Scale), REF-005 (SLO), REF-008 (Infinispan Metrics); CAND-013
- 2.7 → CAND-014 (MAPE-K), CAND-015 (self-adaptive roadmap), CAND-016 (Feedback Control)
- 2.8 → REF-006, REF-007, REF-008, REF-009; CAND-009
- 2.9 → CAND-011, CAND-018 (Jepsen) + resultados de busca

## Referências aprovadas
REF-001, REF-002, REF-003, REF-004, REF-005, REF-006, REF-007, REF-008, REF-009 *(herdadas — P-014)*. **Nenhuma candidata aprovada ainda.**

## Versão atual em LaTeX
Inexistente.

## Revisões realizadas
Nenhuma. ([`../revisoes/revisao-capitulo-02.md`](../revisoes/revisao-capitulo-02.md).)

## Pendências
P-008 (estrutura), P-009 (capítulo novo), P-010 (trabalhos relacionados), P-015 (busca bibliográfica — **bloqueante** para este capítulo), P-017 (ferramenta de model checking).

## Status
**Bloqueado** — depende de validação de estrutura (P-008) e de rodada de busca bibliográfica (P-015).
