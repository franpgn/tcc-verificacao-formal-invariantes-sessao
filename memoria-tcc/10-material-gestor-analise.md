# Análise do Material Curado pelo Gestor (`referencias-gestor/`)

> Mantido pelo **Bibliotecário**. Relatório da análise realizada em 2026-05-12 sobre o material fornecido pelo Gestor via repositório `franpgn/repo` e disponível em `/workspace/referencias-gestor/`. **Este é um relatório de triagem** — nenhum item aqui está aprovado para citação. As **referências candidatas individuais** estão registradas em [`05-referencias-candidatas.md`](05-referencias-candidatas.md) (CAND-022..CAND-046, novas) e dependem de aprovação caso a caso do Gestor (ver [`06-decisoes-do-gestor.md`](06-decisoes-do-gestor.md), Decisão 005).

---

## 1. Inventário do material recebido

| Arquivo | Tipo | Tamanho | Função |
|---|---|---|---|
| `Impossibility of Distributed Consensus with One Faulty.pdf` | PDF — paper completo | 9 pp. | Texto integral do FLP (JACM 1985) |
| `Data-clustering-a-review.pdf` | PDF — paper completo | 60 pp. | Texto integral de Jain, Murty & Flynn (ACM CSUR 1999) |
| `Research Basis From File Analysis.pdf` | PDF — síntese interna do Gestor | 17 pp. | Documento gerado em Google Docs/Gemini consolidando o mapa temático do TCC |
| `Revisao-Sistematica-da-Literatura-em-Ciencia-da-Computacao--Um-Guia-Pratico.pdf` | PDF — guia metodológico | 7 pp. | Neiva & Silva (UFJF, 2016) — guia de revisão sistemática |
| `SearchResults.csv` | CSV — Springer (busca ampla) | 1.006 entradas | Pool principal |
| `SearchResultsDataGridFormal.csv` | CSV — Springer | 1.059 entradas | "data grid" + métodos formais |
| `SearchResultsDataGridLatency.csv` | CSV — Springer | 28 entradas | "data grid" + latência (foco) |
| `SearchResultsHeuristic.csv` | CSV — Springer | 495 entradas | Controle heurístico |
| `SearchResultsInfinispan.csv` | CSV — Springer | 80 entradas | Infinispan |
| `referencias-web.txt` | Lista de URLs | 17 linhas | 7× ACM, 3× Springer (chapters), 3× duplicatas Springer (artigos), 1× guia Neiva-Silva, 1× *sci-hub*, 2× links privados (Scholar/Gemini) |

Total único de entradas após deduplicação por DOI/URL nos CSVs: **2.298**.

---

## 2. Procedência e qualidade

### 2.1 O que é diretamente citável (sob aprovação)

- **PDFs 1, 2 e 4** são *papers* / relatórios técnicos de origem identificável, com metadados confirmados via leitura do conteúdo:
  - FLP 1985 — *Journal of the ACM*, v. 32, n. 2, p. 374–382.
  - Jain, Murty & Flynn 1999 — *ACM Computing Surveys*, v. 31, n. 3 (set. 1999).
  - Neiva & Silva 2016 — *Relatório Técnico RelaTeDCC 001/2016*, UFJF.
- **CSVs** trazem 2.298 candidatos com DOI verificado pela Springer/SpringerLink.
- **`referencias-web.txt`** linha 1 = duplicata do PDF 4; linhas 2–8 = ACM DOIs (metadados resolvidos via Crossref); linhas 9–11 = Springer LNCS; linhas 12–14 = duplicatas das CSVs.

### 2.2 O que **não é citável**

- **`Research Basis From File Analysis.pdf`** — documento de síntese do Gestor (produzido em Google Docs/Gemini; *Producer: Skia/PDF m149 Google Docs Renderer*). É um **insumo interno**, não uma fonte revisada. Será **usado como mapa temático** do TCC e como ponte para localizar as fontes primárias, **mas não citado**. *Útil para alimentar a delimitação do recorte e a justificativa, indiretamente.*
- **Link 15 (sci-hub)** — biblioteca-sombra; **não usada** por este sistema (questão ética/legal). Se houver paper específico atrás dela, busca-se a via legítima (DOI/biblioteca da Unipampa/CAPES).
- **Linhas 16–17** (Google Scholar session, Gemini chat) — links privados; sem valor bibliográfico.

### 2.3 Cobertura dos eixos teóricos

Os filtros temáticos aplicados sobre os 2.298 CSVs revelam a seguinte cobertura (top-3 hits e total de matches por eixo):

| Eixo (ver [`05-referencias-candidatas.md`](05-referencias-candidatas.md)) | Total de matches | Material forte? |
|---|---:|---|
| A — Replicação/consistência | 135 | Sim (CRDTs, quórum, geo-replicação) |
| B — IMDG/cache distribuído | 70 | Sim (KV stores, DHT, scheduling, latency) |
| C — Métodos formais | 83 | **Sim (model checking de protocolos distribuídos, TLA+, Amazon)** |
| D — SLO/latência/observabilidade | 29 | Sim (Tail latency em KV stores) |
| E — Autoadaptativo/controle | 30 | Sim (autoscaling, controle teórico, MAPE-K) |
| F — Falhas/partições/chaos | 21 | **Fraco** no CSV — mas FLP está no PDF |
| G — Metodologia | 31 | Médio (benchmarking, SLR) |
| H — Sessão (app) | 2 | **Lacuna** — não coberto pelo material |

**Conclusão:** o material curado **resolve substancialmente** os eixos C, D, E e cobre bem A, B e G. **Mantém lacunas** em F (chaos engineering/Jepsen) e H (boas práticas de sessão — OWASP/NIST), que continuam a depender de busca complementar futura.

---

## 3. Mapeamento contra as candidatas existentes (CAND-001..021)

Confirmações encontradas no material:

- **CAND-002 (Gilbert & Lynch, CAP 2002)** — não está no material, mas *Research Basis* cita PACELC; eixo A pode ser ancorado nessa CAND quando o Gestor autorizar.
- **CAND-010 (Lamport, *Specifying Systems*, livro TLA+)** — não está no material, **mas** entram dois papers ACM sobre model checking de protocolos distribuídos (Gao 2025, Enea 2024) que reforçam o eixo C como tema vivo.
- **CAND-011 (Newcombe et al., CACM 2015, "How AWS uses formal methods")** — **encontrada uma variante**: Newcombe 2014, "Why Amazon Chose TLA+" (LNCS, ABZ conferência). Cobre o mesmo argumento de aplicação industrial de TLA+; pode ser **par** ou **substituta** da CACM 2015 a critério do Gestor.
- **CAND-014 (IBM MAPE-K)** — não encontrada no material, mas o tema de controle adaptativo está bem coberto por Ullah et al. 2018 ("control-theoretical view of cloud elasticity"), Wu et al. 2020 ("Autoscaling tiered cloud storage in Anna"), Liu et al. 2017 ("OnlineElastMan").
- **CAND-015 (LNCS "Software Engineering for Self-Adaptive Systems")** — **confirmada por proximidade**: a entrada do CSV "Self-management of Distributed Systems Using High-Level Goal Policies" (Rosa, Rodrigues, Lopes 2013) está justamente no volume *Software Engineering for Self-Adaptive Systems II*. Excelente match.
- **CAND-016 (Hellerstein et al., *Feedback Control of Computing Systems*)** — não está no material, mas o tópico está coberto modernamente por Gambi, Toffetti & Pezzè 2013 ("Assurance of Self-adaptive Controllers for the Cloud").

---

## 4. Novas candidatas extraídas do material

As candidatas individuais (com metadados completos verificados por DOI ou leitura do PDF) foram registradas em [`05-referencias-candidatas.md`](05-referencias-candidatas.md) como **CAND-022 a CAND-046**. Sumário por eixo:

### Eixo A — Replicação/consistência
- **CAND-022** — Ruiz-Fuertes, Pallardó-Lozoya, Muñoz-Escoí (2012) — "Consistency in Scalable Systems" (LNCS).
- **CAND-023** — Nair, Petri, Shapiro (2020) — "Proving the Safety of Highly-Available Distributed Objects" (LNCS).
- **CAND-024** — Preguiça, Baquero, Shapiro (2019/2022) — verbete CRDTs (*Encyclopedia of Big Data Technologies*) — complemento didático à REF-002.
- **CAND-025** — Liu, Nguyen, Ganhotra, Rahman, Gupta, Meseguer (2015) — "Quantitative Analysis of Consistency in NoSQL Key-Value Stores".

### Eixo B — IMDG/cache distribuído
- **CAND-026** — Salhi, Odeh, Nasser, Taweel (2018) — "Benchmarking and Performance Analysis for Distributed Cache Systems: A Comparative Case Study". *(Também relevante a G.)*
- **CAND-027** — Jaiman, Ben Mokhtar, Rivière (2020) — "TailX: Scheduling Heterogeneous Multiget Queries to Improve Tail Latencies in Key-Value Stores".

### Eixo C — Métodos formais (alta densidade no material)
- **CAND-028** — **Fischer, Lynch, Paterson (1985)** — "Impossibility of Distributed Consensus with One Faulty Process" — **JACM v. 32 n. 2, p. 374-382** (PDF integral fornecido). **Clássico fundamental do eixo A/F.**
- **CAND-029** — Newcombe (2014) — "Why Amazon Chose TLA+" (LNCS, ABZ 2014). Complemento/substituto de CAND-011.
- **CAND-030** — Gao, Wang, Dou, Feng, Liang, Feng, Wei (2025) — "Model Checking Guided Incremental Testing for Distributed Systems" (Proc. ACM SE, ISSTA).
- **CAND-031** — Enea, Giannakopoulou, Kokologiannakis, Majumdar (2024) — "Model Checking Distributed Protocols in Must" (Proc. ACM PL, OOPSLA2).
- **CAND-032** — Naskos, Gounaris, Katsaros (2017) — "Cost-aware horizontal scaling of NoSQL databases using probabilistic model checking" (*Cluster Computing*). *(Ponte C↔E: model checking aplicado ao próprio problema de escalabilidade adaptativa.)*
- **CAND-033** — Chen, Li, Gu, She, Zhu, Jiang (2026) — "Enhancing Context Awareness with Model Checking-based Uncertainty Representation in Decision Support Systems" (*Formal Aspects of Computing*). *(Eventual ponte C↔E.)*

### Eixo D — SLO/latência/observabilidade
- **CAND-034** — Ben Mokhtar, Canon, Dugois, Marchal, Rivière (2021) — "Taming Tail Latency in Key-Value Stores: A Scheduling Perspective" (Euro-Par).
- **CAND-035** — Ben Mokhtar, Canon, Dugois, Marchal, Rivière (2024) — versão estendida em *Journal of Scheduling*: "A scheduling framework for distributed key-value stores and its application to tail latency minimization".
- **CAND-036** — Nguyen, Charapko, Kulkarni, Demirbas (2019) — "Using weaker consistency models with monitoring and recovery for improving performance of key-value stores" (*J. Brazilian Computer Society*). *(Match raro: consistência fraca + monitoramento + KV — exatamente o eixo do TCC.)*
- **CAND-037** — Li, Peng, Xiang, Wang, Xie, Sun, Liu (2021) — "Enjoy your observability: an industrial survey of microservice tracing and analysis" (*Empirical Software Engineering*).

### Eixo E — Autoadaptativo/controle
- **CAND-038** — Ullah, Li, Shen, Hussain (2018) — "A control theoretical view of cloud elasticity: taxonomy, survey and challenges" (*Cluster Computing*).
- **CAND-039** — Wu, Sreekanti, Hellerstein (2020) — "Autoscaling tiered cloud storage in Anna" (*VLDB Journal*).
- **CAND-040** — Liu, Gureya, Al-Shishtawy, Vlassov (2017) — "OnlineElastMan: self-trained proactive elasticity manager for cloud-based storage services" (*Cluster Computing*).
- **CAND-041** — Gambi, Toffetti, Pezzè (2013) — "Assurance of Self-adaptive Controllers for the Cloud" (LNCS, *Assurances for Self-Adaptive Systems*). *(Ponte E↔C: garantias formais para controladores adaptativos.)*
- **CAND-042** — Rosa, Rodrigues, Lopes (2013) — "Self-management of Distributed Systems Using High-Level Goal Policies" (LNCS, *Software Engineering for Self-Adaptive Systems II*).

### Eixo G — Metodologia
- **CAND-043** — Neiva, Silva (2016) — "Revisão Sistemática da Literatura em Ciência da Computação: Um Guia Prático" — RT UFJF (PDF integral fornecido). **Referência metodológica direta da mini-revisão estruturada (Decisão 003).**
- **CAND-044** — Jain, Murty, Flynn (1999) — "Data Clustering: A Review" (ACM CSUR v. 31 n. 3). *(Uso candidato: referência metodológica caso o agrupamento temático da revisão dirigida seja explicado como "*thematic clustering*"; uso fraco — discutir com o Gestor.)*

### Material de contexto (ACM, geral)
- **CAND-045** — Armbrust *et al.* (2010) — "A view of cloud computing" (CACM v. 53 n. 4, p. 50-58). *(Cap. 1 / 2.6 — contexto cloud.)*

### Cobertura adicional
- **CAND-046** — Benítez-Pérez *et al.* (2018) — "Distributed Systems Modelling" (cap. de livro, *Modeling and Optimization in Science and Technologies*). *(Possível leitura geral de Cap. 2.1; relevância a confirmar.)*

---

## 5. Itens **descartados** na triagem

- **Sekiyama & Unno 2024** (Higher-order MC of effect handlers) — fora de escopo (linguagens de programação puras).
- **Guo, Truong, Demsky 2025** (CXLMC) — tangencial (memória CXL).
- **Hallal & Faizan 2024** (MC para teste em software corporativo) — tangencial.
- Centenas de hits dos CSVs em eixos como F (ex.: *deep RL para sistemas de energia*) — não relacionados.
- Sci-hub e links Gemini/Scholar — não citáveis.

---

## 6. Resumo: o que muda

1. **Decisão 004 não é mais bloqueio absoluto** — o Gestor entregou material curado, autorizando uma rodada de análise limitada a esse material (registrada como **Decisão 005** em [`06-decisoes-do-gestor.md`](06-decisoes-do-gestor.md)).
2. **Buscas autônomas em bases externas permanecem suspensas** até nova autorização (a lacuna F/H continua aberta).
3. **P-015** ([`07-pendencias.md`](07-pendencias.md)) passa a **parcialmente em andamento** — restrita ao corpus do Gestor.
4. As 25 novas candidatas (CAND-022..046) **NÃO são citáveis** ainda; o Gestor precisa aprová-las uma a uma (ver [`05-referencias-candidatas.md`](05-referencias-candidatas.md), seções marcadas como "(do material do Gestor — aguardando aprovação)").
5. **Próximo passo proposto:** seguir o cronograma de TCC-1 — Semanas 1-2 (recorte/critérios) — usando o *Research Basis* como ponte temática, **sem citar** o documento.
