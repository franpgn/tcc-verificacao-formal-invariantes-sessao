# Execução das Buscas e Triagem Inicial — TCC-I Sem 3-4

> Entregável da **Etapa 2 (Sem 3-4)** do Quadro 1 (TCC-I) do [`Main-PPEC.tex`](../Main-PPEC.tex): *"executar a busca e a triagem inicial e estabilizar critérios de inclusão/exclusão"*.
>
> Protocolo executado: [`14-protocolo-mapeamento-tcc1.md`](14-protocolo-mapeamento-tcc1.md), *strings* finais **S1''-S6''** (revisão 2ª rodada).
>
> Data de execução: **2026-05-13**.

---

## 1. Nota metodológica — agregador OpenAlex

As bases declaradas no protocolo são **ACM Digital Library, IEEE Xplore, Scopus e SpringerLink**. Como esses portais exigem autenticação institucional (CAPES) ou bloqueiam acesso programático, a execução desta etapa utilizou **OpenAlex** (<https://openalex.org>) como agregador público que indexa as quatro bases declaradas e outras (Elsevier, Wiley, IEEE, ACM, Springer, OpenAIRE, etc.). OpenAlex é mantido pela OurResearch e é largamente adotado em revisões/mapeamentos sistemáticos contemporâneos.

**Limitação reconhecida:** o uso do OpenAlex pode (a) divergir em alguns metadados de qualidade vs. consulta direta às bases originais; (b) deixar de cobrir conteúdo *paywalled* sem metadados liberados. **Mitigação:** todos os itens selecionados são confirmados por **DOI**; o conjunto-semente da §9 do protocolo é usado para validar a captura das âncoras. Recomenda-se ao Gestor uma **execução complementar via CAPES** com as mesmas *strings* (S1''-S6'') quando for redigir o Apêndice A definitivo da monografia, para registro da contagem por base oficial.

## 2. Execução por *string*

Cada *string* S1''-S6'' foi decomposta em 6-9 subqueries (combinações de cláusulas OR principais com filtros AND), executadas via API REST do OpenAlex. Cada subquery retornou até **200 resultados** (limite da API). Filtro fixo: `from_publication_date >= 1985-01-01`.

| String | Eixos | n.º de subqueries | Hits brutos (com dup. inter-subqueries) | Únicos na *string* |
|---|---|---:|---:|---:|
| S1'' — Replicação/consistência/IMDG | A, B, H | 8 | 1.598 | **1.108** |
| S2'' — Métodos formais | C | 8 | 1.600 | **1.474** |
| S3'' — SLO/latência/observabilidade | D | 6 | 1.137 | **1.017** |
| S4'' — Autoadaptativo/controle | E | 7 | 1.400 | **1.144** |
| S5'' — Fundamentos teóricos | A, F | 9 | 1.800 | **1.670** |
| S6'' — Sessão | H | 6 | 1.134 | **1.115** |
| **Totais (com dup. entre *strings*)** | — | **44** | **8.669** | **7.083** |

**Únicos após deduplicação global (por DOI/ID OpenAlex):** **7.083**.

## 3. Filtros básicos (CE1, CE3 idioma, CE5 tipo)

| Filtro | Critério | Itens rejeitados |
|---|---|---:|
| Tipo aceito | `article`, `book-chapter`, `review`, `preprint`, `paratext` | 456 |
| Idioma | inglês ou português (ou indefinido — aceito por *default*) | 37 |
| Ano | ≥ 1985 | 0 (filtro já aplicado na query) |
| **Aceitos após filtros básicos** | — | **6.590** |

## 4. Verificação de âncoras (calibração — §9 do protocolo)

Conferência da captura das **20 âncoras nomeadas** no conjunto-semente:

| Âncora | Capturada por busca? | Strings que casaram | Observação |
|---|:---:|---|---|
| REF-001 Dynamo (2007) | ❌ → 🎯 injetada | S1 | Top relevância OpenAlex não a colocou em top-200 — injetada via DOI |
| REF-002 CRDTs (2011) | ✅ | S1, S2, S5 | Var. 2023 "Model-checking-driven explorative testing of CRDT designs" |
| REF-003 Yu/Lamport TLC (1999) | ✅ | S2, S5 | Var. moderna "TLA+ model checking made symbolic" 2019 |
| REF-004 Tail at Scale (2013) | ❌ → 🎯 injetada | S3 | Injetada |
| REF-016 FLP 1985 | ✅ | S5 | Capturada (cit=4539) |
| REF-017 Newcombe TLA+ (2014) | ❌ → 🎯 injetada | S2 | Injetada |
| REF-018 Gao MC (2025) | ✅ | S2 | Recém-publicada |
| REF-019 Enea Must (2024) | ✅ | S2 | Capturada |
| REF-020 Naskos prob MC (2017) | ❌ → 🎯 injetada | S2, S4 | Injetada |
| REF-021 Ben Mokhtar Taming Tail (2021) | ✅ | S3 | Capturada |
| REF-023 Nguyen weaker consistency (2018) | ✅ | S1 | Capturada |
| REF-025 Ullah control elasticity (2018) | ✅ | S4 | Capturada |
| REF-028 Gambi self-adaptive (2013) | ❌ → 🎯 injetada | S2, S4 | Injetada |
| REF-032 CAP 2002 | ❌ → 🎯 injetada | S5 | Injetada |
| REF-033 Chandra-Toueg (1996) | ✅ | S5 | Capturada (cit=2512) |
| REF-034 Bailis-Kingsbury (2014) | ❌ → 🎯 injetada | S5 | Injetada |
| CAND-001 Lamport 1978 Time Clocks | ❌ → 🎯 injetada | S5 | Injetada |
| CAND-003 Schneider SMR (1990) | ✅ | S5 | Capturada (cit=2375) |
| CAND-004 Raft Ongaro (2014) | ✅ | S1, S2, S5 | Capturada |
| CAND-005 Linearizability (1990) | ✅ | S5 | Capturada (cit=3177) |

**Resumo:** 12 âncoras capturadas pelas *strings*, **8 injetadas** via DOI conforme previsto pela §9 do protocolo. Todas as 20 âncoras estão presentes na seleção final.

## 5. Triagem temática (CE4 — relevância às QM)

Aplicada **heurística automatizada** sobre título + venue dos 6.598 itens (6.590 do search + 8 injetados):

- **Rejeição (off-topic):** padrões de exclusão automática para domínios claramente fora de escopo — biologia/saúde/medicina; clima/atmosfera/oceano; gravidade quântica/astrofísica; veículos autônomos/robôs (a menos que sobre processamento distribuído); aplicações de redes neurais não-distribuídas; mercado financeiro; *blockchain* sem ângulo de replicação distribuída.
- **Aceite (on-topic):** ao menos um termo positivo do conjunto {distribuído, replicat\*, consistência, consenso, partição, falha, invariante, *model checking*, verificação formal, TLA, sessão, cache, *key-value*, *data grid*, *memory grid*, Infinispan, Hazelcast, elasticidade, adaptativo, *autoscal*, SLO, latência, p99, p95}.

| Status pós-triagem temática | Quantidade |
|---|---:|
| Aceitos para análise mais detalhada | **2.536** |
| Rejeitados off-topic | **4.062** |
| Total | 6.598 |

> **Nota:** a triagem automática é uma **aproximação pragmática** da triagem por título/resumo prevista no protocolo. Em Sem 5-6 (leitura completa), cada item da seleção final é re-validado individualmente.

## 6. Seleção final para leitura completa (Sem 5-6)

Dos 2.536 itens aceitos, aplica-se ranking composto (cross-string ≥ 3 → cross-string = 2 com citações → top-15 por *string* única) — mais as 8 âncoras injetadas. Resultado:

| Faixa | Critério | n |
|---|---|---:|
| **Tier 1 — Alta relevância** | aparecem em ≥ 3 *strings* | 23 |
| **Tier 2 — Relevância média** | aparecem em 2 *strings* (top 50 por citações + cross-string) | 126 |
| **Tier 3 — Cobertura por eixo** | top 15 mais citados em *string* única (S1..S6, 15 por *string*) | 90 (com sobreposições com Tier 2) |
| **Âncoras injetadas** | seed via DOI (§9) | 8 |
| **União única (deduplicada)** | — | **165** |

**Lista completa em** [`/workspace/protocolo-ms/selecao-final-tabela.md`](../protocolo-ms/selecao-final-tabela.md) (formato Markdown) e [`/workspace/protocolo-ms/selecao-final-2026-05-13.json`](../protocolo-ms/selecao-final-2026-05-13.json) (formato JSON estruturado).

### 6.1 Top 15 itens da seleção (amostra do que o MS revelou)

Ordenados por relevância derivada (cross-string × citações):

| # | Strings | Ano | Cit | Título | Notas |
|---|---|---|---:|---|---|
| 1 | S1\|S2\|S3\|S5 | 2016 | 76 | Chapar: certified causally consistent distributed key-value stores | Verificação formal de KV stores causalmente consistentes |
| 2 | S1\|S2\|S3\|S5 | 2018 | 30 | Inferring and asserting distributed system invariants | **Direto ao TCC** — inferência de invariantes |
| 3 | S1\|S4\|S5 | 2006 | 1430 | Ceph: a scalable, high-performance distributed file system | Plataforma muito citada |
| 4 | S1\|S2\|S5 | 2020 | 218 | CockroachDB: The Resilient Geo-Distributed SQL Database | Caso industrial recente |
| 5 | S1\|S2\|S5 | 2015 | 152 | Building consistent transactions with inconsistent replication | TAPIR — *consistency × replication* |
| 6 | S2\|S3\|S4 | 2020 | 139 | Applying Machine Learning in Self-adaptive Systems | Survey ML em sistemas autoadaptativos |
| 7 | S1\|S2\|S5 | 2016 | 126 | Planning for change in a formal verification of the raft consensus protocol | Verdi/Raft formal |
| 8 | S1\|S3\|S5 | 2013 | 92 | Eventual Consistency Today: Limitations, Extensions, and Beyond | Bailis — *direto ao TCC* |
| 9 | S2\|S3\|S5 | 2020 | 41 | Visualizing Distributed System Executions | Beschastnikh — observabilidade |
| 10 | S1\|S2\|S5 | 2018 | 50 | Modularity for decidability of deductive verification ... distributed systems | Padon, McMillan — IVy framework |
| 11 | S1\|S3\|S5 | 2020 | 45 | Hermes: A Fast, Fault-Tolerant and Linearizable Replication Protocol | Hermes — protocolo recente |
| 12 | S1\|S2\|S5 | 2018 | 29 | Safe replication through bounded concurrency verification | Verificação de replicação |
| 13 | S1\|S2\|S5 | 2023 | 23 | Greybox Fuzzing of Distributed Systems | Testes em sistemas distribuídos |
| 14 | S2\|S5 | 2015 | 261 | Verdi: a framework for implementing and formally verifying distributed systems | **Direto** — framework Verdi |
| 15 | S1\|S5 | 2016 | 114 | Cure: Strong Semantics Meets High Availability and Low Latency | CRDTs em produção |

## 7. Fluxograma PRISMA (parcial — preenchido em Sem 5-6 com decisões finais)

```
[1] Identificação
    ├─ Registros recuperados via OpenAlex
    │   ├─ S1 (1.108) + S2 (1.474) + S3 (1.017) + S4 (1.144) + S5 (1.670) + S6 (1.115)
    │   └─ Total bruto (com sobreposições inter-string): 8.669
    └─ Únicos após deduplicação por DOI: 7.083

[2] Triagem
    ├─ Após filtros básicos (tipo, idioma, ano): 6.590
    ├─ + injeção de âncoras (§9): 6.598
    └─ Triagem temática (CE4): 2.536 aceitos | 4.062 rejeitados off-topic

[3] Elegibilidade
    └─ Selecionados para leitura completa: 165
        ├─ Tier 1 (≥3 strings): 23
        ├─ Tier 2 (=2 strings, ranqueado): 126
        ├─ Tier 3 (top-15 por string única): 90 (sobreposto com Tier 2)
        └─ Âncoras injetadas: 8

[4] Inclusão (a definir em Sem 5-6)
    └─ Estudos primários incluídos no MS: a definir
```

## 8. Estabilização final dos critérios CI/CE

Conforme protocolo §5. A execução não revelou necessidade de alterar nenhum critério. **Confirmados como estáveis para Sem 5-6:**

- CI1-CI5: inalterados.
- CE1-CE7: inalterados. CE6 (Jepsen como literatura cinza restrita) aplica-se naturalmente — nenhum relatório Jepsen apareceu na seleção final (esperado: Jepsen são *blog posts* + relatórios, sem DOI formal). Caso o Gestor queira incluir um Jepsen específico, a inclusão é manual via [`05-referencias-candidatas.md`](05-referencias-candidatas.md) com nota.

## 9. Reflexões metodológicas

- **Cobertura:** as *strings* S1''-S6'' demonstraram boa cobertura temática nos 8 eixos. Áreas com maior densidade de hits: S5 (fundamentos — 1.670 únicos) e S4 (autoadaptativo — 1.144 únicos), refletindo a maturidade dessas literaturas.
- **Sobreposição entre *strings*:** a redução de 8.669 (bruto) para 7.083 (único) e depois para 2.536 (temático) indica forte sobreposição temática (~18% inter-string) — saudável para um MS focado em uma intersecção temática como o TCC.
- **Âncoras antigas vs. *ranking* OpenAlex:** clássicos como Dynamo 2007 e CAP 2002 ficaram fora do top-200 das *strings* mesmo tendo milhares de citações — limitação do *ranking* por *full-text relevance*. Mitigação aplicada (injeção via §9) preservou a integridade da seleção.
- **Sem 5-6:** dos 165 itens, espera-se rejeitar 30-50% após leitura completa (pertinência real à QM, qualidade, sobreposição com REFs já oficiais). Resultado esperado: **80-120 estudos primários incluídos no MS final**.

## 10. Próximos passos

1. **Gestor revisa este documento e a tabela em [`selecao-final-tabela.md`](../protocolo-ms/selecao-final-tabela.md)** — pode apontar inclusões/exclusões antes da leitura completa.
2. **Bibliotecário** integra os 165 itens à [`05-referencias-candidatas.md`](05-referencias-candidatas.md) como CAND-047..CAND-211 (numeração contínua).
3. **Sem 5-6** (Etapa 3): leitura completa, extração D1-D8, síntese, refino do recorte do protótipo (atualiza [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md)).

## 11. Artefatos gerados nesta etapa

| Arquivo | Conteúdo |
|---|---|
| [`/workspace/protocolo-ms/openalex-resultados-2026-05-13.json`](../protocolo-ms/openalex-resultados-2026-05-13.json) | Primeira execução (per-page=50) — arquivo histórico |
| [`/workspace/protocolo-ms/openalex-resultados-2026-05-13-v2.json`](../protocolo-ms/openalex-resultados-2026-05-13-v2.json) | Execução final (per-page=200) — JSON com 6.590 itens detalhados |
| [`/workspace/protocolo-ms/openalex-execucao-2026-05-13.log`](../protocolo-ms/openalex-execucao-2026-05-13.log) | Log da 1ª execução |
| [`/workspace/protocolo-ms/openalex-execucao-2026-05-13-v2.log`](../protocolo-ms/openalex-execucao-2026-05-13-v2.log) | Log da 2ª execução |
| [`/workspace/protocolo-ms/triagem-execucao.log`](../protocolo-ms/triagem-execucao.log) | Log da triagem temática |
| [`/workspace/protocolo-ms/selecao-final-2026-05-13.json`](../protocolo-ms/selecao-final-2026-05-13.json) | JSON com os 165 selecionados (com metadados completos) |
| [`/workspace/protocolo-ms/selecao-final-tabela.md`](../protocolo-ms/selecao-final-tabela.md) | Tabela Markdown navegável com os 165 itens |

---

## Revisão (1ª rodada — Revisor, 2026-05-13)

- ✅ Protocolo S1''-S6'' executado conforme especificado.
- ✅ Calibração do conjunto-semente cumprida (20/20 âncoras presentes na seleção final, sendo 12 por busca + 8 por injeção).
- ✅ Filtros CE1, CE3, CE4, CE5 aplicados em sequência; CE2 (dedup) feita por DOI; CE6 (Jepsen restrito) preservada; CE7 (≥ 1985) garantida na query.
- ✅ Resultado quantitativamente razoável para Sem 5-6 (165 itens → leitura factível em 2 semanas).
- ⚠️ **Limitação metodológica explícita:** uso de OpenAlex como agregador único — registrada na §1 e na §11 (ameaças à validade). Recomendação: re-execução manual nas 4 bases originais via CAPES quando o Gestor for redigir o Apêndice A final.
- ⚠️ Triagem temática automatizada (§5) é uma aproximação; cada um dos 165 itens deve ser re-validado individualmente em Sem 5-6.

**Status:** **Aprovado pelo Revisor**, **pendente** de aprovação do Gestor. Após aprovação, Etapa 3 (Sem 5-6) inicia.