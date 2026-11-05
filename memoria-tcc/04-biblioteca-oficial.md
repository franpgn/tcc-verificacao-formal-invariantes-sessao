# Biblioteca Oficial do TCC

> Mantida pelo **Bibliotecário**. **Somente referências aqui listadas e com status "Aprovada pelo Gestor" podem ser citadas pelo Escritor.**
>
> **Status global:** as 9 referências abaixo foram **herdadas do arquivo base** (`bibliografia.bib`) — presumivelmente já validadas pelo orientador no contexto da Proposta. Mantidas aqui como **pré-aprovadas**, porém **sujeitas à confirmação formal do Gestor** (ver decisão pendente em [`06-decisoes-do-gestor.md`](06-decisoes-do-gestor.md) e P-014). Entradas BibTeX reproduzidas de `bibliografia.bib` sem alteração.

Convenção de IDs: `REF-0XX` (oficiais). Candidatas em [`05-referencias-candidatas.md`](05-referencias-candidatas.md) usam `CAND-0XX`.

---

## [REF-001] Dynamo: Amazon's Highly Available Key-value Store

### Autores
DeCandia, Giuseppe; Hastorun, Deniz; Jampani, Madan; Kakulapati, Gunavardhan; Lakshman, Avinash; Pilchin, Alex; Sivasubramanian, Swaminathan; Vosshall, Peter; Vogels, Werner.

### Ano
2007.

### Fonte
*Proceedings of the 21st ACM Symposium on Operating Systems Principles* (SOSP '07).

### Link
https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf

### Resumo
Apresenta o Dynamo, armazenamento chave-valor altamente disponível da Amazon, priorizando disponibilidade de escrita por meio de replicação otimista, *sloppy quorum*, *hinted handoff* e reconciliação por *vector clocks*; admite estados defasados e conflitos resolvidos na leitura ou pela aplicação.

### Principais conceitos
Replicação assíncrona/otimista; consistência eventual; *vector clocks*; reconciliação e resolução de conflitos; *sloppy quorum*; *anti-entropy*; *trade-off* disponibilidade × consistência sob partições.

### Trechos ou ideias úteis
Justifica, no base, a afirmação de que a replicação assíncrona "introduz a possibilidade de observação de estados defasados entre réplicas e reordenação efetiva de eventos observados por nós distintos" e que "a reconciliação posterior exija resolução de conflitos ou convergência eventual".

### Capítulos relacionados
Cap. 1 (Introdução); Cap. 2 (2.1, 2.3); Cap. 4 (modelo formal — canal assíncrono e reconciliação).

### Como citar no texto
`\cite{decandia_dynamo_2007}` (e, num modelo com `abntex2cite`, `\citeonline{decandia_dynamo_2007}` para "DeCandia *et al.* (2007)").

### Entrada BibTeX
```bibtex
@inproceedings{decandia_dynamo_2007,
  author    = {DeCandia, Giuseppe and Hastorun, Deniz and Jampani, Madan and Kakulapati, Gunavardhan and Lakshman, Avinash and Pilchin, Alex and Sivasubramanian, Swaminathan and Vosshall, Peter and Vogels, Werner},
  title     = {Dynamo: Amazon's Highly Available Key-value Store},
  booktitle = {Proceedings of the 21st ACM Symposium on Operating Systems Principles (SOSP '07)},
  year      = {2007},
  url       = {https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf},
  note      = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-002] Conflict-free Replicated Data Types

### Autores
Shapiro, Marc; Preguiça, Nuno; Baquero, Carlos; Zawirski, Marek.

### Ano
2011.

### Fonte
*Proceedings of the 13th International Symposium on Stabilization, Safety, and Security of Distributed Systems* (SSS 2011).

### Link
https://www.lip6.fr/Marc.Shapiro/papers/2011/CRDTs_SSS-2011.pdf

### Resumo
Define os CRDTs — tipos de dados replicados projetados para convergir sem coordenação, garantindo *Strong Eventual Consistency* desde que as operações satisfaçam propriedades algébricas (comutatividade, ou estados formando um *semilattice* com junção monótona).

### Principais conceitos
*Strong Eventual Consistency*; CvRDT (*state-based*) e CmRDT (*op-based*); *join-semilattice*; comutatividade; monotonicidade; convergência sem consenso.

### Trechos ou ideias úteis
Fundamenta a noção de "propriedades de convergência" entre os invariantes do TCC, e dá base teórica para discutir se/como o estado de sessão (especialmente contadores e *flags*) poderia ser modelado com estruturas convergentes.

### Capítulos relacionados
Cap. 1; Cap. 2 (2.3); Cap. 4 (invariantes de convergência; possível desenho do protocolo de atualização).

### Como citar no texto
`\cite{shapiro_crdt_2011}`.

### Entrada BibTeX
```bibtex
@inproceedings{shapiro_crdt_2011,
  author    = {Shapiro, Marc and Preguiça, Nuno and Baquero, Carlos and Zawirski, Marek},
  title     = {Conflict-free Replicated Data Types},
  booktitle = {Proceedings of the 13th International Symposium on Stabilization, Safety, and Security of Distributed Systems (SSS 2011)},
  year      = {2011},
  url       = {https://www.lip6.fr/Marc.Shapiro/papers/2011/CRDTs\_SSS-2011.pdf},
  note      = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-003] Model Checking TLA+ Specifications

> **Correção de metadados aplicada (2026-10-30, Decisão 018):** a ficha e o `bibliografia.bib` passam a registrar a versão publicada, Yu, Manolios e Lamport, CHARME '99, como `@inproceedings`, com a mesma chave. Até 2026-10-30 a entrada era um `@techreport` da DEC/Compaq com dois autores.

### Autores
Yu, Yuan; Manolios, Panagiotis; Lamport, Leslie.

### Ano
1999.

### Fonte
*Correct Hardware Design and Verification Methods* (CHARME '99), eds. Laurence Pierre e Thomas Kropf, Lecture Notes in Computer Science, v. 1703, Springer, 1999, p. 54-66. **DOI:** 10.1007/3-540-48153-2_6 (Crossref e página de publicações do Lamport).

### Link
https://doi.org/10.1007/3-540-48153-2_6 (cópia do autor: https://lamport.azurewebsites.net/pubs/yuanyu-model-checking.pdf)

### Resumo
Descreve o TLC, verificador de modelos para especificações em TLA+; trata da exploração do espaço de estados, verificação de invariantes e propriedades temporais e produção de contraexemplos.

### Principais conceitos
*Model checking*; TLA+ / TLC; espaço de estados; invariantes; contraexemplos; verificação de propriedades de segurança e *liveness*.

### Trechos ou ideias úteis
Sustenta as decisões metodológicas do base: usar *model checking* para "encontrar violações e produzir contraexemplos" e "uma ferramenta adequada a sistemas de transições e invariantes, como TLC ou alternativa equivalente".

### Capítulos relacionados
Cap. 1; Cap. 2 (2.5); Cap. 3 (trilha formal); Cap. 4 (resultados de verificação).

### Como citar no texto
`\cite{yu_lamport_tla_modelchecking_1999}`.

### Entrada BibTeX
```bibtex
@inproceedings{yu_lamport_tla_modelchecking_1999,
  author    = {Yu, Yuan and Manolios, Panagiotis and Lamport, Leslie},
  title     = {Model Checking {TLA+} Specifications},
  booktitle = {Correct Hardware Design and Verification Methods (CHARME '99)},
  editor    = {Pierre, Laurence and Kropf, Thomas},
  series    = {Lecture Notes in Computer Science},
  volume    = {1703},
  publisher = {Springer},
  address   = {Berlin, Heidelberg},
  year      = {1999},
  pages     = {54--66},
  doi       = {10.1007/3-540-48153-2_6},
  url       = {https://doi.org/10.1007/3-540-48153-2_6},
  note      = {Acesso em: 30 out. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-004] The Tail at Scale

### Autores
Dean, Jeffrey; Barroso, Luiz André.

### Ano
2013.

### Fonte
*Communications of the ACM*, v. 56, n. 2.

### Link
https://www.barroso.org/publications/TheTailAtScale.pdf

### Resumo
Analisa a variabilidade de latência em serviços de grande escala e o impacto desproporcional da cauda da distribuição (p99+) quando uma requisição depende de muitos componentes; propõe técnicas de mitigação (*hedged requests*, *tied requests* etc.).

### Principais conceitos
Latência de cauda; percentis (p95/p99/p99.9); amplificação de variabilidade em *fan-out*; técnicas de redução de latência de cauda.

### Trechos ou ideias úteis
Justifica o uso de p95/p99 como métricas primárias e a preocupação com latência sob falhas; pode embasar parte do desenho das ações do controlador heurístico.

### Capítulos relacionados
Cap. 1; Cap. 2 (2.6); Cap. 3 (métricas); Cap. 4 (controlador); Cap. 5 (análise).

### Como citar no texto
`\cite{dean_barroso_tail_2013}`.

### Entrada BibTeX
```bibtex
@article{dean_barroso_tail_2013,
  author  = {Dean, Jeffrey and Barroso, Luiz Andr{\'e}},
  title   = {The Tail at Scale},
  journal = {Communications of the ACM},
  year    = {2013},
  url     = {https://www.barroso.org/publications/TheTailAtScale.pdf},
  note    = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-005] Service Level Objectives (SRE Book)

### Autores
Google (Beyer, Betsy; Jones, Chris; Petoff, Jennifer; Murphy, Niall Richard — eds.). *Site Reliability Engineering*, cap. "Service Level Objectives".

### Ano
2016 (livro; capítulo disponível em página oficial).

### Fonte
*Website* — Google SRE Book.

### Link
https://sre.google/sre-book/service-level-objectives/

### Resumo
Define SLI, SLO e SLA; discute escolha de indicadores, agregação por percentis, *error budgets* e como objetivos de nível de serviço guiam decisões operacionais.

### Principais conceitos
SLI / SLO / SLA; *error budget*; escolha de indicadores; agregação por percentis; alvos de confiabilidade.

### Trechos ou ideias úteis
Base para o termo "controle orientado a SLO" no título do TCC e para definir formalmente as metas de latência que o controlador heurístico tenta manter.

### Capítulos relacionados
Cap. 1; Cap. 2 (2.6); Cap. 4 (definição de SLO no controlador); Cap. 5.

### Como citar no texto
`\cite{google_sre_slo}`.

### Entrada BibTeX
```bibtex
@misc{google_sre_slo,
  author       = {{Google}},
  title        = {Service Level Objectives (SRE Book)},
  howpublished = {Website},
  url          = {https://sre.google/sre-book/service-level-objectives/},
  note         = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-006] Infinispan Documentation (stable)

### Autores
Infinispan Project.

### Ano
s.d. (acesso em 19 mar. 2026).

### Fonte
*Website* — documentação oficial.

### Link
https://infinispan.org/docs/stable/

### Resumo
Documentação oficial do Infinispan: modos de cache (local, replicado, distribuído, *invalidation*), *clustering*, particionamento, *rebalancing*/*state transfer*, persistência, transações, segurança e configuração.

### Principais conceitos
Caches distribuídos vs. replicados; *consistent hashing*; *owners*; *rebalancing*; *state transfer*; *partition handling*.

### Trechos ou ideias úteis
Fonte primária para descrever a plataforma experimental e justificar as escolhas de configuração de cache no Cap. 4.

### Capítulos relacionados
Cap. 2 (2.8); Cap. 3 (trilha experimental); Cap. 4 (arquitetura experimental).

### Como citar no texto
`\cite{infinispan_docs_stable}`.

### Entrada BibTeX
```bibtex
@misc{infinispan_docs_stable,
  author       = {{Infinispan Project}},
  title        = {Infinispan Documentation (stable)},
  howpublished = {Website},
  url          = {https://infinispan.org/docs/stable/},
  note         = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-007] Cross-site replication (xsite)

### Autores
Infinispan Project.

### Ano
s.d. (acesso em 19 mar. 2026).

### Fonte
*Website* — documentação oficial (xsite).

### Link
https://infinispan.org/docs/stable/titles/xsite/xsite.html

### Resumo
Documenta a replicação entre sites no Infinispan: *backups* síncronos e assíncronos entre clusters geograficamente separados, estratégias de resolução de conflitos, *state transfer* entre sites e *takeover*/*takeback*.

### Principais conceitos
*Cross-site replication*; *backup* síncrono × assíncrono; *conflict resolution* entre sites; *site status* online/offline; *cross-site state transfer*.

### Trechos ou ideias úteis
Sustenta os cenários multi-site da metodologia ("simular comunicação entre domínios com latência e falhas controladas"; "indisponibilização temporária de um site e posterior retorno").

### Capítulos relacionados
Cap. 2 (2.3, 2.8); Cap. 3 (cenários de falha); Cap. 4 (arquitetura experimental multi-site).

### Como citar no texto
`\cite{infinispan_xsite}`.

### Entrada BibTeX
```bibtex
@misc{infinispan_xsite,
  author       = {{Infinispan Project}},
  title        = {Cross-site replication (xsite)},
  howpublished = {Website},
  url          = {https://infinispan.org/docs/stable/titles/xsite/xsite.html},
  note         = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-008] Infinispan Metrics

### Autores
Infinispan Project.

### Ano
s.d. (acesso em 19 mar. 2026).

### Fonte
*Website* — documentação oficial (metrics).

### Link
https://infinispan.org/docs/stable/titles/metrics/metrics.html

### Resumo
Documenta as métricas expostas pelo Infinispan (via Micrometer/Prometheus e JMX): métricas de cache, de *cluster*, de *cross-site*, latências e contadores operacionais.

### Principais conceitos
Métricas de cache e de *cluster*; integração Micrometer/Prometheus; JMX; observabilidade operacional.

### Trechos ou ideias úteis
Fonte primária para a coleta de "métricas do próprio cluster por APIs e logs, para correlacionar violações e mudanças de latência com eventos operacionais" e para os observáveis do controlador heurístico.

### Capítulos relacionados
Cap. 2 (2.6, 2.8); Cap. 3 (instrumentação); Cap. 4 (observáveis do controlador); Cap. 5.

### Como citar no texto
`\cite{infinispan_metrics}`.

### Entrada BibTeX
```bibtex
@misc{infinispan_metrics,
  author       = {{Infinispan Project}},
  title        = {Infinispan Metrics},
  howpublished = {Website},
  url          = {https://infinispan.org/docs/stable/titles/metrics/metrics.html},
  note         = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base — **confirmação do Gestor pendente** (P-014).

---

## [REF-009] Introduction: What is Infinispan?

### Autores
Infinispan Project.

### Ano
s.d. (acesso em 19 mar. 2026).

### Fonte
*Website* — página de introdução.

### Link
https://infinispan.org/introduction/

### Resumo
Apresentação de alto nível do Infinispan como *in-memory data grid* / *distributed cache*: casos de uso, modos de implantação (*embedded* e *remote*/servidor) e principais recursos.

### Principais conceitos
*In-memory data grid*; *distributed cache*; modo *embedded* × *remote*; casos de uso.

### Trechos ou ideias úteis
Útil para a definição introdutória de IMDG/data grid no Cap. 1 e 2. **Observação:** está em `bibliografia.bib` mas **não é citada no corpo** do arquivo base — decidir se será usada ou removida.

### Capítulos relacionados
Cap. 1; Cap. 2 (2.2, 2.8).

### Como citar no texto
`\cite{infinispan_intro}`.

### Entrada BibTeX
```bibtex
@misc{infinispan_intro,
  author       = {{Infinispan Project}},
  title        = {Introduction: What is Infinispan?},
  howpublished = {Website},
  url          = {https://infinispan.org/introduction/},
  note         = {Acesso em: 19 mar. 2026}
}
```

### Status
Herdada do arquivo base (não citada no corpo) — **confirmação do Gestor pendente** (P-014).

---

---

# Parte II — Entradas migradas em 2026-05-12 (Decisões 006 e 009)

> Adicionadas em lote a partir das candidatas aprovadas pelo Gestor: 22 itens vindos do material curado em `/workspace/referencias-gestor/` (CAND-022..045 aprovadas) + 5 itens das lacunas F e H (Decisão 009: Gilbert & Lynch CAP, Chandra & Toueg, Bailis & Kingsbury, OWASP, NIST SP 800-63B). Formato compactado.

---

## [REF-010] Consistency in Scalable Systems
- **Autores:** Ruiz-Fuertes, María Idoia; Pallardó-Lozoya, María Ruth; Muñoz-Escoí, Francesc D.
- **Ano:** 2012. **Fonte:** *On the Move to Meaningful Internet Systems* (OTM 2012), LNCS, p. 549-565.
- **DOI:** 10.1007/978-3-642-33615-7_7.
- **Resumo:** revisão de modelos de consistência em sistemas escaláveis (forte, sequencial, causal, eventual) e suas implicações para disponibilidade.
- **Principais conceitos:** modelos de consistência; trade-off escalabilidade × consistência.
- **Capítulos relacionados:** Cap. 2 (2.1, 2.3).
- **Citação:** `\citeonline{ruizfuertes_consistency_2012}` / `\cite{ruizfuertes_consistency_2012}`.
- **Origem:** CAND-022. **Status:** Aprovada (Decisão 006).

## [REF-011] Proving the Safety of Highly-Available Distributed Objects
- **Autores:** Nair, Sreeja S.; Petri, Gustavo; Shapiro, Marc.
- **Ano:** 2020. **Fonte:** *ESOP 2020 — Programming Languages and Systems*, LNCS, p. 544-571.
- **DOI:** 10.1007/978-3-030-44914-8_20.
- **Resumo:** método para provar formalmente *safety* de objetos distribuídos altamente disponíveis; generaliza CRDTs.
- **Principais conceitos:** verificação formal de objetos replicados; *safety*; composição de invariantes.
- **Capítulos relacionados:** Cap. 2 (2.3, 2.5); Cap. 4; trabalhos relacionados.
- **Citação:** `\citeonline{nair_safety_distributed_2020}` / `\cite{nair_safety_distributed_2020}`.
- **Origem:** CAND-023. **Status:** Aprovada (Decisão 006). *(Ponte A↔C.)*

## [REF-012] Conflict-Free Replicated Data Types (verbete) — **DISPENSADA (Decisão 012, 2026-05-17)**
> **2026-10-30 (Decisão 019):** entrada removida do `bibliografia.bib`.
- **Autores:** Preguiça, Nuno; Baquero, Carlos; Shapiro, Marc.
- **Ano:** 2019. **Fonte:** *Encyclopedia of Big Data Technologies*.
- **DOI:** 10.1007/978-3-319-77525-8_185.
- **Status:** **Dispensada** — redundante com REF-002 (Shapiro et al. 2011), que já cobre CRDTs no nível seminal e está lida na íntegra. Não citar.
- **Origem:** CAND-024. Aprovada na Decisão 006, dispensada na Decisão 012 (bloqueio SpringerLink + redundância).

## [REF-013] Quantitative Analysis of Consistency in NoSQL Key-Value Stores
- **Autores:** Liu, Si; Nguyen, Son; Ganhotra, Jatin; Rahman, Muntasir Raihan; Gupta, Indranil; Meseguer, José.
- **Ano:** 2015. **Fonte:** *QEST 2015 — Quantitative Evaluation of Systems*, LNCS.
- **DOI:** 10.1007/978-3-319-22264-6_15.
- **Resumo:** análise quantitativa/probabilística de modelos de consistência em KV stores reais.
- **Principais conceitos:** consistência KV; análise formal probabilística; medição.
- **Capítulos relacionados:** Cap. 2 (2.1, 2.3); Cap. 3.
- **Citação:** `\citeonline{liu_quantitative_consistency_2015}` / `\cite{liu_quantitative_consistency_2015}`.
- **Origem:** CAND-025. **Status:** Aprovada (Decisão 006).

## [REF-014] Benchmarking and Performance Analysis for Distributed Cache Systems — **DISPENSADA (Decisão 012, 2026-05-17)**
> **2026-10-30 (Decisão 019):** entrada removida do `bibliografia.bib`.
- **Autores:** Salhi, Haytham; Odeh, Feras; Nasser, Rabee; Taweel, Adel.
- **Ano:** 2018. **Fonte:** *Performance Evaluation and Benchmarking for the Analytics Era* (TPCTC 2017), LNCS.
- **DOI:** 10.1007/978-3-319-72401-0_11.
- **Status:** **Dispensada** — cobertura adequada pelas 4 REFs Infinispan + REF-013/015 (não dependentes desta). Bloqueio SpringerLink torna a recuperação custosa. Não citar.
- **Origem:** CAND-026. Aprovada na Decisão 006, dispensada na Decisão 012.

## [REF-015] TailX: Scheduling Heterogeneous Multiget Queries to Improve Tail Latencies in KV Stores
- **Autores:** Jaiman, Vikas; Ben Mokhtar, Sonia; Rivière, Etienne.
- **Ano:** 2020. **Fonte:** *DAIS 2020 — Distributed Applications and Interoperable Systems*, LNCS.
- **DOI:** 10.1007/978-3-030-50323-9_5.
- **Resumo:** *scheduling* para reduzir latência de cauda em KV stores com consultas heterogêneas.
- **Capítulos relacionados:** Cap. 2 (2.6, 2.8); Cap. 4 (comparação com controlador).
- **Citação:** `\citeonline{jaiman_tailx_2020}` / `\cite{jaiman_tailx_2020}`.
- **Origem:** CAND-027. **Status:** Aprovada (Decisão 006).

## [REF-016] Impossibility of Distributed Consensus with One Faulty Process (FLP)
- **Autores:** Fischer, Michael J.; Lynch, Nancy A.; Paterson, Michael S.
- **Ano:** 1985. **Fonte:** *Journal of the ACM*, v. 32, n. 2, p. 374-382.
- **DOI:** 10.1145/3149.214121.
- **Resumo:** **resultado clássico de impossibilidade** — nenhum protocolo de consenso totalmente assíncrono tolera a falha silenciosa de mesmo um processo.
- **Principais conceitos:** consenso assíncrono; impossibilidade; modelo de falha *crash*; fundamento teórico para escolha de replicação otimista vs. consenso síncrono.
- **Capítulos relacionados:** **Cap. 1 (justificativa)**; Cap. 2 (2.1, 2.4); Cap. 4.
- **Citação:** `\citeonline{fischer_lynch_paterson_flp_1985}` / `\cite{fischer_lynch_paterson_flp_1985}`.
- **Origem:** CAND-028 (PDF integral fornecido pelo Gestor). **Status:** Aprovada (Decisão 006). *(Recomendação forte do Bibliotecário cumprida.)*

## [REF-017] Why Amazon Chose TLA+
- **Autor:** Newcombe, Chris.
- **Ano:** 2014. **Fonte:** *Abstract State Machines, Alloy, B, TLA, VDM, and Z* (ABZ 2014), LNCS.
- **DOI:** 10.1007/978-3-662-43652-3_3.
- **Resumo:** relato do processo decisório que levou a AWS a adotar TLA+ para verificação de sistemas distribuídos.
- **Capítulos relacionados:** Cap. 1 (justificativa); Cap. 2 (2.5, trabalhos relacionados).
- **Citação:** `\citeonline{newcombe_aws_tla_2014}` / `\cite{newcombe_aws_tla_2014}`.
- **Origem:** CAND-029. **Status:** Aprovada (Decisão 006). *(Substitui CAND-011 — variante CACM 2015 — com mesmo argumento em venue formal.)*

## [REF-018] Model Checking Guided Incremental Testing for Distributed Systems
- **Autores:** Gao, Yu; Wang, Dong; Dou, Wensheng; Feng, Wei; Liang, Yu; Feng, Yang; Wei, Jun.
- **Ano:** 2025. **Fonte:** *Proceedings of the ACM on Software Engineering*, v. 2 (ISSTA), p. 297-319.
- **DOI:** 10.1145/3728883.
- **Resumo:** combinação de *model checking* com testes incrementais para sistemas distribuídos — abordagem híbrida formal/empírica.
- **Capítulos relacionados:** Cap. 2 (2.5, trabalhos relacionados); Cap. 3.
- **Citação:** `\citeonline{gao_mc_incremental_2025}` / `\cite{gao_mc_incremental_2025}`.
- **Origem:** CAND-030. **Status:** Aprovada (Decisão 006).

## [REF-019] Model Checking Distributed Protocols in Must
- **Autores:** Enea, Constantin; Giannakopoulou, Dimitra; Kokologiannakis, Michalis; Majumdar, Rupak.
- **Ano:** 2024. **Fonte:** *Proceedings of the ACM on Programming Languages*, v. 8 (OOPSLA2), p. 1900-1927.
- **DOI:** 10.1145/3689778.
- **Resumo:** apresenta a ferramenta *Must* para *model checking* de protocolos distribuídos com mensagens assíncronas e falhas.
- **Principais conceitos:** alternativa a TLA+/TLC; mensagens assíncronas; falhas.
- **Capítulos relacionados:** Cap. 2 (2.5); Cap. 3 (subsidia P-017 — escolha da ferramenta).
- **Citação:** `\citeonline{enea_must_2024}` / `\cite{enea_must_2024}`.
- **Origem:** CAND-031. **Status:** Aprovada (Decisão 006).

## [REF-020] Cost-aware horizontal scaling of NoSQL databases using probabilistic model checking
- **Autores:** Naskos, Athanasios; Gounaris, Anastasios; Katsaros, Panagiotis.
- **Ano:** 2017. **Fonte:** *Cluster Computing*.
- **DOI:** 10.1007/s10586-017-0816-5.
- **Resumo:** *probabilistic model checking* para decidir escalonamento horizontal de NoSQL com consciência de custo.
- **Principais conceitos:** ponte C↔E — métodos formais aplicados a controle adaptativo.
- **Capítulos relacionados:** Cap. 2 (2.5, 2.7, trabalhos relacionados); Cap. 4.
- **Citação:** `\citeonline{naskos_probabilistic_mc_2017}` / `\cite{naskos_probabilistic_mc_2017}`.
- **Origem:** CAND-032. **Status:** Aprovada (Decisão 006).

## [REF-021] Taming Tail Latency in Key-Value Stores: A Scheduling Perspective
- **Autores:** Ben Mokhtar, Sonia; Canon, Louis-Claude; Dugois, Anthony; Marchal, Loris; Rivière, Etienne.
- **Ano:** 2021. **Fonte:** *Euro-Par 2021: Parallel Processing*, LNCS.
- **DOI:** 10.1007/978-3-030-85665-6_9.
- **Resumo:** *scheduling* aplicado a redução de latência de cauda em KV stores.
- **Capítulos relacionados:** Cap. 2 (2.6); Cap. 4.
- **Citação:** `\citeonline{benmokhtar_taming_tail_2021}` / `\cite{benmokhtar_taming_tail_2021}`.
- **Origem:** CAND-034. **Status:** Aprovada (Decisão 006).

## [REF-022] A scheduling framework for distributed key-value stores and its application to tail latency minimization
- **Autores:** Ben Mokhtar, Sonia; Canon, Louis-Claude; Dugois, Anthony; Marchal, Loris; Rivière, Etienne.
- **Ano:** 2024. **Fonte:** *Journal of Scheduling*.
- **DOI:** 10.1007/s10951-023-00803-8.
- **Resumo:** versão estendida (periódico) de REF-021. Recomendada como citação preferencial.
- **Capítulos relacionados:** Cap. 2 (2.6); Cap. 4.
- **Citação:** `\citeonline{benmokhtar_scheduling_2024}` / `\cite{benmokhtar_scheduling_2024}`.
- **Origem:** CAND-035. **Status:** Aprovada (Decisão 006).

## [REF-023] Using weaker consistency models with monitoring and recovery for improving performance of KV stores
- **Autores:** Nguyen, Duong; Charapko, Aleksey; Kulkarni, Sandeep S.; Demirbas, Murat.
- **Ano:** 2019. **Fonte:** *Journal of the Brazilian Computer Society*.
- **DOI:** 10.1186/s13173-019-0091-9.
- **Resumo:** combina **consistência fraca + monitoramento + recovery** para melhorar desempenho de KV stores — quase espelha o escopo do TCC.
- **Principais conceitos:** consistência relaxada; *monitoring*; *recovery*.
- **Capítulos relacionados:** **Cap. 2 (trabalhos relacionados — central)**; Cap. 4.
- **Citação:** `\citeonline{nguyen_weaker_consistency_2019}` / `\cite{nguyen_weaker_consistency_2019}`.
- **Origem:** CAND-036. **Status:** Aprovada (Decisão 006). *(Recomendação forte do Bibliotecário cumprida.)*

## [REF-024] Enjoy your observability: an industrial survey of microservice tracing and analysis
- **Autores:** Li, Bowen; Peng, Xin; Xiang, Qilin; Wang, Hanzhang; Xie, Tao; Sun, Jun; Liu, Xuanzhe.
- **Ano:** 2021. **Fonte:** *Empirical Software Engineering*.
- **DOI:** 10.1007/s10664-021-10063-9.
- **Resumo:** *survey* industrial de práticas de observabilidade em microsserviços.
- **Capítulos relacionados:** Cap. 2 (2.6).
- **Citação:** `\citeonline{li_observability_2021}` / `\cite{li_observability_2021}`.
- **Origem:** CAND-037. **Status:** Aprovada (Decisão 006).

## [REF-025] A control theoretical view of cloud elasticity: taxonomy, survey and challenges
- **Autores:** Ullah, Amjad; Li, Jingpeng; Shen, Yindong; Hussain, Amir.
- **Ano:** 2018. **Fonte:** *Cluster Computing*.
- **DOI:** 10.1007/s10586-018-2807-6.
- **Resumo:** taxonomia e *survey* de elasticidade em nuvem sob a ótica de teoria de controle (estabilidade, histerese, *flapping*).
- **Capítulos relacionados:** Cap. 2 (2.7); Cap. 4 (controlador).
- **Citação:** `\citeonline{ullah_control_elasticity_2018}` / `\cite{ullah_control_elasticity_2018}`.
- **Origem:** CAND-038. **Status:** Aprovada (Decisão 006).

## [REF-026] Autoscaling tiered cloud storage in Anna
- **Autores:** Wu, Chenggang; Sreekanti, Vikram; Hellerstein, Joseph M.
- **Ano:** 2020. **Fonte:** *The VLDB Journal*.
- **DOI:** 10.1007/s00778-020-00632-7.
- **Resumo:** auto-escalonamento de storage hierárquico em "Anna" (KV store da pesquisa do Berkeley).
- **Capítulos relacionados:** Cap. 2 (2.7, trabalhos relacionados).
- **Citação:** `\citeonline{wu_anna_autoscale_2020}` / `\cite{wu_anna_autoscale_2020}`.
- **Origem:** CAND-039. **Status:** Aprovada (Decisão 006).

## [REF-027] OnlineElastMan: self-trained proactive elasticity manager for cloud-based storage services
- **Autores:** Liu, Ying; Gureya, Daharewa; Al-Shishtawy, Ahmad; Vlassov, Vladimir.
- **Ano:** 2017. **Fonte:** *Cluster Computing*.
- **DOI:** 10.1007/s10586-017-0899-z.
- **Resumo:** gestor proativo de elasticidade para storage em nuvem com auto-treinamento.
- **Capítulos relacionados:** Cap. 2 (2.7, trabalhos relacionados).
- **Citação:** `\citeonline{liu_onlineelastman_2017}` / `\cite{liu_onlineelastman_2017}`.
- **Origem:** CAND-040. **Status:** Aprovada (Decisão 006).

## [REF-028] Assurance of Self-adaptive Controllers for the Cloud
- **Autores:** Gambi, Alessio; Toffetti, Giovanni; Pezzè, Mauro.
- **Ano:** 2013. **Fonte:** *Assurances for Self-Adaptive Systems*, LNCS 7740.
- **DOI:** 10.1007/978-3-642-36249-1_12.
- **Resumo:** garantias (formais e empíricas) sobre controladores autoadaptativos para nuvem.
- **Principais conceitos:** ponte E↔C — validação de controlador adaptativo.
- **Capítulos relacionados:** Cap. 2 (2.5, 2.7); Cap. 4 (controlador).
- **Citação:** `\citeonline{gambi_assurance_selfadaptive_2013}` / `\cite{gambi_assurance_selfadaptive_2013}`.
- **Origem:** CAND-041. **Status:** Aprovada (Decisão 006). *(Recomendação forte do Bibliotecário cumprida.)*

## [REF-029] Self-management of Distributed Systems Using High-Level Goal Policies
- **Autores:** Rosa, Liliana; Rodrigues, Luís; Lopes, Antónia.
- **Ano:** 2013. **Fonte:** *Software Engineering for Self-Adaptive Systems II*, LNCS 7475.
- **DOI:** 10.1007/978-3-642-35813-5_7.
- **Resumo:** auto-gestão de sistemas distribuídos por políticas de objetivo de alto nível; do volume LNCS canônico de SEAMS.
- **Capítulos relacionados:** Cap. 2 (2.7).
- **Citação:** `\citeonline{rosa_selfmgmt_2013}` / `\cite{rosa_selfmgmt_2013}`.
- **Origem:** CAND-042. **Status:** Aprovada (Decisão 006).

## [REF-030] Revisão Sistemática da Literatura em Ciência da Computação: Um Guia Prático
- **Autores:** Neiva, Frâncila Weidt; Silva, Rodrigo Luís de Souza da.
- **Ano:** 2016. **Fonte:** *Relatório Técnico RelaTeDCC 001/2016*, Universidade Federal de Juiz de Fora.
- **Resumo:** guia em português para revisão sistemática em CC, baseado em Kitchenham 2007.
- **Capítulos relacionados:** **Cap. 3 (metodologia — mini-revisão estruturada)**.
- **Citação:** `\citeonline{neiva_silva_revisao_sistematica_2016}` / `\cite{neiva_silva_revisao_sistematica_2016}`.
- **Origem:** CAND-043 (PDF integral fornecido pelo Gestor). **Status:** Aprovada (Decisão 006). *(Recomendação forte do Bibliotecário cumprida.)*

## [REF-031] A view of cloud computing
- **Autores:** Armbrust, Michael; Fox, Armando; Griffith, Rean; Joseph, Anthony D.; Katz, Randy; Konwinski, Andy; Lee, Gunho; Patterson, David; Rabkin, Ariel; Stoica, Ion; Zaharia, Matei.
- **Ano:** 2010. **Fonte:** *Communications of the ACM*, v. 53, n. 4, p. 50-58.
- **DOI:** 10.1145/1721654.1721672.
- **Resumo:** texto seminal de definição de *cloud computing*; *trade-offs*, oportunidades, obstáculos.
- **Capítulos relacionados:** Cap. 1 (contexto); Cap. 2 (2.6).
- **Citação:** `\citeonline{armbrust_cloud_2010}` / `\cite{armbrust_cloud_2010}`.
- **Origem:** CAND-045. **Status:** Aprovada (Decisão 006).

---

## Lacuna F preenchida (Decisão 009)

## [REF-032] Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services (CAP)
- **Autores:** Gilbert, Seth; Lynch, Nancy.
- **Ano:** 2002. **Fonte:** *ACM SIGACT News*, v. 33, n. 2, p. 51-59.
- **DOI:** 10.1145/564585.564601.
- **Resumo:** formalização e prova do teorema CAP — não é possível garantir simultaneamente consistência, disponibilidade e tolerância a partições.
- **Capítulos relacionados:** **Cap. 1 (justificativa)**; Cap. 2 (2.1, 2.4).
- **Citação:** `\citeonline{gilbert_lynch_cap_2002}` / `\cite{gilbert_lynch_cap_2002}`.
- **Origem:** Decisão 009 (era CAND-002, agora aprovada com metadados verificados). **Status:** Aprovada.

## [REF-033] Unreliable Failure Detectors for Reliable Distributed Systems
- **Autores:** Chandra, Tushar Deepak; Toueg, Sam.
- **Ano:** 1996. **Fonte:** *Journal of the ACM*, v. 43, n. 2, p. 225-267.
- **DOI:** 10.1145/226643.226647.
- **Resumo:** teoria de detectores de falha não confiáveis e suas classes (W, S, ◇W, ◇S); resultado de equivalência consenso ↔ ◇W.
- **Capítulos relacionados:** Cap. 2 (2.4); Cap. 4 (modelo de falhas).
- **Citação:** `\citeonline{chandra_toueg_failure_detectors_1996}` / `\cite{chandra_toueg_failure_detectors_1996}`.
- **Origem:** Decisão 009 (era CAND-006, agora aprovada com metadados verificados). **Status:** Aprovada.

## [REF-034] The network is reliable
- **Autores:** Bailis, Peter; Kingsbury, Kyle.
- **Ano:** 2014. **Fonte:** *Communications of the ACM*, v. 57, n. 9, p. 48-55. *(Há versão pareada em ACM Queue v. 12 n. 7.)*
- **DOI:** 10.1145/2643130.
- **Resumo:** levantamento de falhas de rede reais (partições, atrasos, perdas) em sistemas de produção — evidência empírica de que partições não são raras.
- **Capítulos relacionados:** Cap. 1 (justificativa); Cap. 2 (2.4).
- **Citação:** `\citeonline{bailis_kingsbury_network_2014}` / `\cite{bailis_kingsbury_network_2014}`.
- **Origem:** Decisão 009 (era CAND-007, agora aprovada com metadados verificados). **Status:** Aprovada.

---

---

## Adições da Proposta atualizada (2026-05-12) — Petersen, PRISMA, Sustentabilidade

> Com a virada para **mapeamento sistemático** (Decisão 010) e a inclusão de um parágrafo sobre **computação sustentável** na Introdução, a Proposta atualizada introduz 5 novas referências citáveis. Mantidas como pré-aprovadas (parte da Proposta) — sujeitas à confirmação formal do Gestor (P-014, mesma lógica das 9 originais).

## [REF-037] Systematic Mapping Studies in Software Engineering
- **Autores:** Petersen, Kai; Feldt, Robert; Mujtaba, Shahid; Mattsson, Michael.
- **Ano:** 2008. **Fonte:** *Proceedings of the 12th International Conference on Evaluation and Assessment in Software Engineering* (EASE 2008).
- **DOI:** 10.14236/EWIC/EASE2008.8.
- **Resumo:** **referência metodológica canônica** para *systematic mapping studies* em engenharia de software — define etapas (questões, busca, triagem, mapeamento, classificação).
- **Principais conceitos:** *systematic mapping*; questões de mapeamento; esquema de classificação; *bubble plot*.
- **Capítulos relacionados:** **Cap. 3 (Metodologia — mapeamento sistemático)**; apêndice com protocolo.
- **Citação:** `\citeonline{petersen_mapping_2008}` / `\cite{petersen_mapping_2008}`.
- **Origem:** Proposta atualizada — citada em [`Main-PPEC.tex`](../Main-PPEC.tex) Sect. 3. **Status:** Pré-aprovada.

## [REF-038] The PRISMA 2020 statement: an updated guideline for reporting systematic reviews
- **Autores:** Page, Matthew J.; McKenzie, Joanne E.; Bossuyt, Patrick M.; e outros.
- **Ano:** 2021 (statement de 2020). **Fonte:** *Systematic Reviews*, v. 10, p. 89.
- **DOI:** 10.1186/s13643-021-01626-4.
- **Resumo:** *checklist* PRISMA 2020 para relato transparente de revisões sistemáticas (flow diagram, itens obrigatórios, registro).
- **Capítulos relacionados:** Cap. 3 (Metodologia — relato dos resultados de busca/seleção); apêndice (PRISMA diagram).
- **Citação:** `\citeonline{prisma_2020}` / `\cite{prisma_2020}`.
- **Origem:** Proposta atualizada. **Status:** Pré-aprovada.

## [REF-039] The carbon footprint of computational research
- **Autores:** Nature Computational Science (editorial).
- **Ano:** 2023. **Fonte:** *Nature Computational Science*, v. 3, p. 659.
- **DOI:** 10.1038/s43588-023-00506-2.
- **Resumo:** editorial sobre necessidade de mensurar/reportar impacto ambiental de pesquisa computacional.
- **Capítulos relacionados:** Cap. 1 (motivação — ângulo de sustentabilidade computacional).
- **Citação:** `\citeonline{turner_carbon_footprint_2023}` / `\cite{turner_carbon_footprint_2023}`.
- **Origem:** Proposta atualizada — citada em Sect. 1 (parágrafo de sustentabilidade). **Status:** Pré-aprovada.

## [REF-040] Exploring Energy Saving Opportunities in Fault Tolerant HPC Systems
- **Autores:** Morán, Marina; Balladini, Javier Aldo; Rexachs, Dolores; Rucci, Enzo.
- **Ano:** 2024. **Fonte:** *Journal of Parallel and Distributed Computing*, v. 185, p. 104797.
- **DOI:** 10.1016/j.jpdc.2023.104797.
- **Resumo:** estratégias de economia de energia em sistemas HPC tolerantes a falhas — relevante para o argumento de que reduzir reexecuções economiza energia.
- **Capítulos relacionados:** Cap. 1 (motivação — sustentabilidade × tolerância a falhas).
- **Citação:** `\citeonline{ft_hpc_energy_2024}` / `\cite{ft_hpc_energy_2024}`.
- **Origem:** Proposta atualizada — citada em Sect. 1. **Status:** Pré-aprovada.

## [REF-041] Instrumentação das Técnicas Árvore de Decisão e KerasTuner para Autoajuste de Redes Neurais Recorrentes GRU na Perspectiva de Computação Sustentável
- **Autores:** Lemos, Davi Lopes; Durgante, Bianca de Oliveira; Correia, Matheus Caneda; Perez, Naylor Bastiani; Pinho, Leonardo Bidese de.
- **Ano:** 2025. **Fonte:** *Anais da Escola Regional de Alto Desempenho da Região Sul* (ERAD-RS).
- **DOI:** 10.5753/eradrs.2025.6825.
- **Resumo:** trabalho do **próprio orientador** (Prof. Leonardo Bidese de Pinho) em computação sustentável — relevante como referência local/grupo de pesquisa.
- **Capítulos relacionados:** Cap. 1 (motivação — sustentabilidade); Cap. 2 (trabalhos relacionados do grupo).
- **Citação:** `\citeonline{lemos_eradrs_2025}` / `\cite{lemos_eradrs_2025}`.
- **Origem:** Proposta atualizada — em `bibliografia.bib` mas **ainda não citada no corpo** do `Main-PPEC.tex`. **Status:** Pré-aprovada (mesma lógica de `infinispan_intro` em REF-009).

---

## Lacuna H preenchida (Decisão 009)

## [REF-035] OWASP Session Management Cheat Sheet
- **Autores:** OWASP Foundation.
- **Ano:** s.d. (consultar versão atual). **Fonte:** *OWASP Cheat Sheet Series* — website.
- **URL:** https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- **Resumo:** guia de boas práticas para o ciclo de vida de sessão (geração de ID, expiração, invalidação, proteção contra *fixation*/*hijacking*).
- **Principais conceitos:** ciclo de vida de sessão; invalidação; bloqueio.
- **Capítulos relacionados:** **Cap. 1 (justificativa dos invariantes)**; Cap. 2 (motivação do estudo de caso).
- **Citação:** `\citeonline{owasp_session_mgmt_cheatsheet}` / `\cite{owasp_session_mgmt_cheatsheet}`.
- **Origem:** Decisão 009 (era CAND-021 — parte OWASP). **Status:** Aprovada. *(Fonte normativa/técnica, não acadêmica revisada por pares — usar como apoio para fundamentar invariantes I1, I2, I4.)*

## [REF-042] Time, Clocks, and the Ordering of Events in a Distributed System
- **Autores:** Lamport, Leslie.
- **Ano:** 1978. **Fonte:** *Communications of the ACM*, v. 21, n. 7, p. 558-565.
- **DOI:** 10.1145/359545.359563.
- **Resumo:** define a relação *happens-before* e o algoritmo de relógios lógicos; fundamento de ordenação causal em sistemas distribuídos sem relógio global.
- **Principais conceitos:** *happens-before*; relógios lógicos; ordenação parcial vs. total; relógios físicos sincronizáveis.
- **Capítulos relacionados:** **Cap. 2 (2.2 — modelo de tempo)**; Cap. 4 (modelo formal — alfabeto temporal).
- **Citação:** `\citeonline{lamport_time_clocks_1978}` / `\cite{lamport_time_clocks_1978}`.
- **Origem:** CAND-001, promovida a REF oficial na Decisão 012. Texto integral já em `/workspace/leituras/CAND-001-Lamport-Time-Clocks-1978.txt` desde 2026-05-14. **Status:** Aprovada.

## [REF-044] Linearizability: A Correctness Condition for Concurrent Objects
- **Autores:** Herlihy, Maurice P.; Wing, Jeannette M.
- **Ano:** 1990. **Fonte:** *ACM Transactions on Programming Languages and Systems*, v. 12, n. 3, p. 463-492.
- **DOI:** 10.1145/78969.78972.
- **Resumo:** define a linearizabilidade como condição de correção para objetos concorrentes: toda execução concorrente é equivalente a alguma ordenação sequencial que preserva a ordem temporal de operações não sobrepostas. Distingue linearizabilidade de consistência sequencial e demonstra propriedades de composição local.
- **Principais conceitos:** linearizabilidade; ordem temporal; composicionalidade; especificação sequencial.
- **Capítulos relacionados:** **Cap. 2 (2.1 — modelos de consistência)**; Cap. 4 (modelo formal — invariante I1 de linearizabilidade-de-sessão).
- **Citação:** `\citeonline{herlihy_wing_linearizability_1990}` / `\cite{herlihy_wing_linearizability_1990}`.
- **Origem:** CAND-005, promovida a REF oficial na Decisão 012 (PDF obtido via CAPES/ACM em 2026-05-17 — DOI 10.1145/78969.78972). **Status:** Aprovada. Texto integral em `/workspace/leituras/CAND-005-Herlihy-Wing-Linearizability-1990.txt` (30 pg).

## [REF-043] In Search of an Understandable Consensus Algorithm (Raft)
- **Autores:** Ongaro, Diego; Ousterhout, John.
- **Ano:** 2014. **Fonte:** *USENIX Annual Technical Conference 2014*, p. 305-319.
- **URL:** https://raft.github.io/raft.pdf (OA).
- **Resumo:** algoritmo de consenso projetado para inteligibilidade; decompõe consenso em eleição de líder, replicação de log e segurança; equivalente ao Paxos em garantias mas com formalização operacional.
- **Principais conceitos:** consenso de líder único; mandatos (*terms*); replicação de log; *commit index*; mudança de membros.
- **Capítulos relacionados:** **Cap. 2 (2.4 — consenso e ordenação)**; Cap. 4 (algoritmo de replicação no modelo formal); trabalhos relacionados.
- **Citação:** `\citeonline{ongaro_raft_2014}` / `\cite{ongaro_raft_2014}`.
- **Origem:** CAND-004, promovida a REF oficial na Decisão 012 (PDF OA baixado em 2026-05-17). **Status:** Aprovada.

## [REF-036] NIST Special Publication 800-63B: Digital Identity Guidelines — Authentication and Lifecycle Management
> **Aviso (2026-10-22, P-029):** o CSRC/NIST marca a SP 800-63B (2017) como *Withdrawn*. Ela foi substituída pela SP 800-63B-4 (jul. 2025), registrada como CAND-064.
> **SUBSTITUÍDA (2026-10-30, Decisão 018):** não citar mais. A chave a usar é `nist_sp_800_63b4_2025` (REF-062). A entrada `nist_sp_800_63b_2017` fica no `bibliografia.bib` só até o Escritor trocar as citações nos `.tex`; depois o Bibliotecário a remove.
- **Autores:** Grassi, Paul A.; Fenton, James L.; Newton, Elaine M.; Perlner, Ray A.; Regenscheid, Andrew R.; Burr, William E.; Richer, Justin P.
- **Ano:** 2017 (atualizado em 2020). **Fonte:** *NIST Special Publication 800-63B*, National Institute of Standards and Technology.
- **DOI:** 10.6028/NIST.SP.800-63b.
- **Resumo:** requisitos técnicos para autenticação digital — níveis de garantia (AAL1-3), tipos de autenticador, ciclo de vida e gerência de sessão.
- **Principais conceitos:** AAL; ciclo de vida do autenticador; *session management* normativo.
- **Capítulos relacionados:** Cap. 1 (motivação); Cap. 2 (fundamentação dos invariantes de bloqueio e expiração).
- **Citação:** `\citeonline{nist_sp_800_63b_2017}` / `\cite{nist_sp_800_63b_2017}`.
- **Origem:** Decisão 009 (era CAND-021 — parte NIST). **Status:** Substituída pela REF-062 (Decisão 018). **Entrada removida do `bibliografia.bib` em 2026-10-30**, depois de conferido que nenhum `.tex` ativo a cita (backup em `scratchpad/bibliografia-antes-remocao-nist2017-20261030.bib`, sessão 2b2a391e).

## [REF-045] Infinispan Performance Considerations and Tuning Guidelines
- **Autores:** Infinispan Project (documentação coletiva mantida pela comunidade).
- **Ano:** 2026 (versão *stable*). **Fonte:** documentação oficial do projeto Infinispan.
- **URL:** https://infinispan.org/docs/stable/titles/tuning/tuning.html (acesso em 25 mai. 2026).
- **Resumo:** guia oficial de dimensionamento e ajuste para uso do Infinispan em produção; descreve heap da JVM (50\% para *cache puro*, 33\% com processamento adicional), \emph{overhead} aproximado por *entry* (200 B *on-heap*, 60 B *off-heap*), janelas TCP recomendadas (`wmem_max` 640 KB, `rmem_max` 25 MB), MTU 1500 padrão e *jumbo frames* até 9 000, `numSegments` padrão 256 com fórmula 20×N para clusters acima de 20 nós, e dimensionamento de pools OOB/incoming conforme a concorrência esperada.
- **Principais conceitos:** *heap sizing*; MTU; *jumbo frames*; segmentação de chaves; pools OOB; *flow control*; *write-behind*; *garbage collection* (G1/ZGC/Shenandoah).
- **Capítulos relacionados:** **Cap. 3 (3.3.5 — parâmetros experimentais, linhas T3, T4, T15)**; Cap. 4 (configuração do `podman-compose.yml`).
- **Citação:** `\citeonline{infinispan_tuning}` / `\cite{infinispan_tuning}`.
- **Origem:** candidata da sessão 2026-05-25 (P-025), promovida pela Decisão 013. Segue o padrão de aprovação da família Infinispan (REF-006/007/008/009 — mesma docs oficiais). **Status:** Aprovada.

## [REF-046] Benchmarking Cloud Serving Systems with YCSB
- **Autores:** Cooper, Brian F.; Silberstein, Adam; Tam, Erwin; Ramakrishnan, Raghu; Sears, Russell.
- **Ano:** 2010. **Fonte:** *Proceedings of the 1st ACM Symposium on Cloud Computing (SoCC '10)*, p. 143-154.
- **DOI:** 10.1145/1807128.1807152.
- **Resumo:** apresenta o Yahoo! Cloud Serving Benchmark (YCSB), referência consolidada para avaliação de sistemas \emph{key-value} sob carga sintética; define seis *workloads* core (A 50/50, B 95/5, C leitura, D *latest*, E *short ranges*, F leitura-modifica-escrita), distribuições de chave (uniforme, Zipfian, *latest*), fases separadas de \emph{load} e \emph{run}, controle de \emph{throughput} alvo e formato de saída por percentis (avg, p95, p99) por tipo de operação. O *Workload A* é descrito explicitamente pelos autores como \emph{``session store recording recent actions''}.
- **Principais conceitos:** YCSB; *workloads* sintéticos; distribuições Zipfian/uniforme/*latest*; mistura leitura/escrita; fases *load*/*run*; percentis de latência reportados.
- **Capítulos relacionados:** **Cap. 3 (3.3.5 — parâmetros experimentais, linhas T6, T7, T11)**; Cap. 4 (operação do *workload*).
- **Citação:** `\citeonline{cooper_ycsb_2010}` / `\cite{cooper_ycsb_2010}`.
- **Origem:** candidata da sessão 2026-05-25 (P-025), promovida pela Decisão 013. Paper canônico (>6 000 citações), revisado por pares (ACM), citado por REF-001, REF-015, REF-021 e REF-027. **Status:** Aprovada.

---

## Promovidas em 2026-06-18 (Tarefa T-A — justificativa da escolha do TLA+)

> Quatro fontes promovidas para a subseção da Metodologia que justifica a escolha do TLA+ comparando-o com alternativas. Metadados verificados pelo Bibliotecário (InformIT/Amazon/Google Books, ACM DL/dblp/Semantic Scholar, MIT Press/Internet Archive, Pearson/AbeBooks/ACM DL). Entradas BibTeX já em `bibliografia.bib`. A redação da subseção é do Escritor (próximo passo).

## [REF-047] Specifying Systems: The TLA+ Language and Tools for Hardware and Software Engineers
- **Autores:** Lamport, Leslie.
- **Ano:** 2002. **Fonte:** Addison-Wesley, Boston, MA (livro). **ISBN:** 978-0-321-14306-8.
- **URL:** https://lamport.azurewebsites.net/tla/book.html (PDF da 1ª impressão disponível no site do autor).
- **Resumo:** obra de referência da linguagem TLA+ e do conjunto de ferramentas (incl. TLC); cobre especificação de comportamentos, *safety*/*liveness*, *fairness*, propriedades temporais e composição.
- **Principais conceitos:** TLA+; PlusCal; especificação de estados e transições; invariantes; *liveness*/*fairness*; verificação com TLC.
- **Capítulos relacionados:** **Cap. 3 (subseção — justificativa da escolha do TLA+, referência primária da linguagem)**; Cap. 2 (2.5); Cap. 4 (modelo formal).
- **Citação:** `\citeonline{lamport_specifying_systems_2002}` / `\cite{lamport_specifying_systems_2002}`.
- **Origem:** CAND-010, promovida na Tarefa T-A (2026-06-18). **Status:** Aprovada.

## [REF-048] How Amazon Web Services Uses Formal Methods
- **Autores:** Newcombe, Chris; Rath, Tim; Zhang, Fan; Munteanu, Bogdan; Brooker, Marc; Deardeuff, Michael.
- **Ano:** 2015. **Fonte:** *Communications of the ACM*, v. 58, n. 4, p. 66-73. **DOI:** 10.1145/2699417.
- **Resumo:** relato industrial do uso de TLA+ pela AWS para encontrar defeitos sutis em sistemas distribuídos de produção (armazenamento/replicação); discute custo-benefício e adoção pelos engenheiros.
- **Principais conceitos:** métodos formais na indústria; TLA+ em produção; defeitos sutis de concorrência; retorno do esforço de especificação.
- **Capítulos relacionados:** **Cap. 3 (subseção — justificativa da escolha do TLA+, evidência de adoção industrial)**; Cap. 1 (justificativa); Cap. 2 (2.5, trabalhos relacionados).
- **Citação:** `\citeonline{newcombe_aws_formal_methods_2015}` / `\cite{newcombe_aws_formal_methods_2015}`.
- **Origem:** CAND-011, promovida na Tarefa T-A (2026-06-18). **Status:** Aprovada. *(Distinta de REF-017 — Newcombe, "Why Amazon Chose TLA+", ABZ 2014: mesmo autor principal, obra/venue diferentes; ambas citáveis.)*

## [REF-049] Software Abstractions: Logic, Language, and Analysis
- **Autores:** Jackson, Daniel.
- **Ano:** 2012 (2. ed.). **Fonte:** MIT Press, Cambridge, MA (livro). **ISBN:** 978-0-262-01715-2.
- **Resumo:** obra de referência da linguagem/ferramenta **Alloy** e da abordagem de *lightweight formal methods* — modelagem em lógica relacional e análise automática por *bounded analysis* via SAT.
- **Principais conceitos:** Alloy; lógica relacional; *scope-bounded analysis*; SAT; *lightweight formal methods*.
- **Capítulos relacionados:** **Cap. 3 (subseção — justificativa da escolha do TLA+, alternativa Alloy considerada)**; Cap. 2 (2.5).
- **Citação:** `\citeonline{jackson_software_abstractions_2012}` / `\cite{jackson_software_abstractions_2012}`.
- **Origem:** CAND-047, promovida na Tarefa T-A (2026-06-18). **Status:** Aprovada. *(ISBN da 2. ed. capa dura 2012; não confundir com a ed. revisada 2016, ISBN 978-0-262-52890-0.)*

## [REF-050] The SPIN Model Checker: Primer and Reference Manual
- **Autores:** Holzmann, Gerard J.
- **Ano:** 2004. **Fonte:** Addison-Wesley, Boston, MA (livro). **ISBN:** 978-0-321-22862-8.
- **Resumo:** obra de referência do verificador **SPIN** e da linguagem **Promela** para modelos de sistemas concorrentes/distribuídos; cobre especificação, propriedades LTL, busca em espaço de estados e *partial-order reduction*.
- **Principais conceitos:** SPIN; Promela; *model checking* explícito; LTL; *partial-order reduction*.
- **Capítulos relacionados:** **Cap. 3 (subseção — justificativa da escolha do TLA+, alternativa SPIN/Promela considerada)**; Cap. 2 (2.5).
- **Citação:** `\citeonline{holzmann_spin_2004}` / `\cite{holzmann_spin_2004}`.
- **Origem:** CAND-048, promovida na Tarefa T-A (2026-06-18). **Status:** Aprovada.

---

## [REF-051] Challenges in High-Performance Computing
- **Autores:** Navaux, Philippe Olivier Alexandre; Lorenzon, Arthur Francisco; Serpa, Matheus da Silva.
- **Ano:** 2023. **Fonte:** *Journal of the Brazilian Computer Society* (JBCS/SBC), v. 29, n. 1, p. 51–62. **DOI:** 10.5753/jbcs.2023.2219.
- **Resumo:** survey sobre desafios de supercomputadores futuros (arquiteturas heterogêneas, exascale, paralelização), com consumo de energia e resiliência tratados como requisitos não-funcionais.
- **Enquadramento:** **survey de HPC / energia** — NÃO é carbon-aware caching. Usada como reforço da menção a consumo de recursos em ambientes de nuvem e HPC, ao lado de REF-039 (Turner) e REF-040 (Morán).
- **Principais conceitos:** HPC; exascale; arquiteturas heterogêneas; consumo de energia; resiliência.
- **Capítulos relacionados:** **Cap. 1 (justificativa, parágrafo de sustentabilidade)**.
- **Citação:** `\cite{navaux_hpc_challenges_2023}`.
- **Origem:** CAND-049, promovida na Decisão 016 (2026-06-23). **Status:** Aprovada.

---

## [REF-052] Cache Your Prompt When It's Green: Carbon-Aware Caching for Large Language Model Serving
- **Autores:** Tian, Yuyang; Sun, Desen; Ding, Yi; Liu, Sihang.
- **Ano:** 2026. **Fonte:** *Proceedings of the ACM on Measurement and Analysis of Computing Systems* (POMACS), v. 10, n. 1, p. 1–28 — apresentado na **ACM SIGMETRICS 2026** (peer-reviewed). **DOI:** 10.1145/3788087 (versão arXiv: 10.48550/arXiv.2505.23970).
- **Resumo:** framework GreenCache para caching ciente de carbono no serviço de LLMs; equilibra o carbono operacional evitado pelo cache contra o carbono incorporado (*embodied*) de SSDs de alta capacidade; ~15,1% de redução média de carbono (até 25,3%) respeitando restrições de latência.
- **Enquadramento:** **carbon-aware caching** (exemplo geral de impacto de carbono do caching). Sustenta o argumento de que decisões de cache em sistemas distribuídos têm impacto de carbono mensurável; usada ao lado de REF-039 (Turner) e REF-040 (Morán) e da REF-051 (Navaux).
- **Principais conceitos:** carbon-aware caching; carbono operacional × incorporado; *embodied carbon* de SSD; LLM serving; sustentabilidade computacional.
- **Capítulos relacionados:** **Cap. 1 (justificativa, parágrafo de sustentabilidade)**.
- **Citação:** `\cite{tian_greencache_2026}`.
- **Origem:** CAND-050, promovida na Decisão 016 (2026-06-23). **Status:** Aprovada.

## [REF-068] Using Hot Rod Java clients (Infinispan 15.0), Section 1.4: Client Failover
- **Autor:** Infinispan Project. **Ano:** 2026 (acesso em 27 out. 2026). **Fonte:** documentação oficial, https://infinispan.org/docs/15.0.x/titles/hotrod_java/hotrod_java.html#hotrod_failover. Bloco "Transactional Caches with Failover".
- **Trecho que sustenta o texto:** "Even though Hot Rod clients can failover, you should use transactional caches to ensure that operations do not partially complete and leave conflicting entries on different nodes."
- **Enquadramento:** sustenta só a afirmação de que, sem transação, uma operação condicional pode ficar parcialmente aplicada no failover. **Não** sustenta "não garante atomicidade" nem fala em perda ou troca de dono no rebalanceamento.
- **Capítulos relacionados:** Metodologia, parágrafo da escrita condicional (`replaceWithVersion`) e do aviso ISPN006010.
- **Citação:** `\cite{infinispan_hotrod_java_15}`.
- **Origem:** CAND-070, promovida na Decisão 017 (2026-10-27). **Status:** Aprovada.

## [REF-069] Configuring Infinispan caches (Infinispan 15.0)
- **Autor:** Infinispan Project. **Ano:** 2026 (acesso em 27 out. 2026). **Fonte:** documentação oficial, https://infinispan.org/docs/15.0.x/titles/configuring/configuring.html.
- **Trechos que sustentam o texto:** seção 7.1.1 "Data consistency in a split cluster": "Network outages or errors that cause Infinispan clusters to split into partitions can result in data loss or consistency issues regardless of any handling strategy or merge policy." Seção 10.1.1 "Clustered caches and locks": no cache não transacional, a escrita vai ao dono primário, e a condicional que falha nele não é repassada aos demais donos.
- **Enquadramento:** cobre partição e *merge* e descreve o caminho da escrita condicional no modo não transacional.
- **Trechos adicionais (conferidos em 2026-10-30), replicação SYNC × ASYNC:** seção 2.2 "Distributed caches": "Asynchronous replication is not recommended because it can lose updates" e "asynchronous distributed caches can also see a stale value". Seção 2.4 "Asynchronous replication": "the originator node does not receive any acknowledgement from the other nodes" e "We do not recommend asynchronous communications in general, as they can cause inconsistencies in the data." Sustenta o contraste `DIST_SYNC` × `DIST_ASYNC`. O campo `note` do `.bib` cita só as seções 7.1.1 e 10.1.1, então o Escritor deve indicar a seção no texto (`\cite[seção 2.2]{...}`) ou o Bibliotecário atualiza o `note`.
- **Conferido em 2026-10-30 (HTML baixado e lido localmente):** texto literal da 7.1.1 (subtítulo "Between the split and detection"): "If a write operation takes place on a node that is in a minor partition when a split occurs, and before Infinispan detects the split, that value is lost when Infinispan transfers state to that minor partition during the merge." e "In the event that all partitions are in the DEGRADED mode that value is not lost because no state transfer occurs but the entry can have an inconsistent value."
- **Conferido em 2026-10-30:** 2.2.1 "Read consistency": "Even with synchronous replication, distributed caches are not linearizable." 7.1.1: valor escrito numa partição menor entre a divisão e a detecção "is lost when Infinispan transfers state to that minor partition during the merge" (não se perde se todas as partições estiverem em DEGRADED). A frase do "originator node" é da 2.4, não da 2.2.
- **Capítulos relacionados:** Metodologia, mesmo parágrafo da REF-068; Fundamentação 2.2; Desenvolvimento (TLC sob partição).
- **Citação:** `\cite{infinispan_configuring_15}`.
- **Origem:** CAND-071, promovida na Decisão 017 (2026-10-27). **Status:** Aprovada.

## [REF-070] Fast Splittable Pseudorandom Number Generators
- **Autores:** Steele Jr., Guy L.; Lea, Doug; Flood, Christine H.
- **Ano:** 2014. **Fonte:** Proc. OOPSLA 2014 (SPLASH 2014, Portland), ACM, p. 453-472. **DOI:** 10.1145/2660193.2660195. Também em ACM SIGPLAN Notices 49(10), DOI 10.1145/2714064.2660195. Metadados conferidos no Crossref. O texto integral não foi lido (sem versão aberta).
- **Enquadramento e ressalva:** é a origem do gerador SplitMix. A função do código (`MisturaV3.mix64`) é o Mix13 de Stafford, igual a `java.util.SplittableRandom.mix64`. No artigo, a função `mix64` é o finalizador do MurmurHash3, segundo a implementação de referência em Haskell. A redação aprovada é "função de mistura do gerador SplitMix [cite], na variante usada por java.util.SplittableRandom", e o artigo não deve ser citado como fonte das constantes.
- **Capítulos relacionados:** Metodologia, derivação das sementes por *thread*.
- **Citação:** `\cite{steele_lea_flood_splitmix_2014}`.
- **Origem:** CAND-072, promovida na Decisão 017 (2026-10-27). **Status:** Aprovada.

---

## Promovidas em 2026-10-30 (Decisão 018, monografia "TCC-II parcial")

> Nove candidatas do Eixo J (P-031 a) e sete do Eixo K. Entradas no `bibliografia.bib` (bloco "Decisao 018", ao fim do arquivo). Testado com `abntex2-alf` numa cópia: 71 entradas, sem aviso nem duplicata. Numeração: CAND-0XX do Eixo J vira REF-0(XX-2), como reservado em P-027/P-028. REF-053, 054, 059, 063, 065 e 067 (CAND-055, 056, 061, 065, 067 e 069) continuam reservadas e fora da monografia.

## [REF-055] Basic Concepts and Taxonomy of Dependable and Secure Computing
- **Autores:** Avižienis, Algirdas; Laprie, Jean-Claude; Randell, Brian; Landwehr, Carl. **Ano:** 2004. **Fonte:** IEEE Transactions on Dependable and Secure Computing 1(1):11-33. **DOI:** 10.1109/TDSC.2004.2.
- **Enquadramento:** taxonomia de falhas (Sec. 3.3.1: parada, silêncio, temporização, falhas bizantinas). Sustenta que o modelo cobre parada/silêncio e atraso, não falha arbitrária.
- **Citação:** `\cite{avizienis_taxonomy_2004}`. **Origem:** CAND-057, Decisão 018. **Status:** Aprovada.

## [REF-056] Simple Testing Can Prevent Most Critical Failures
- **Autores:** Yuan, Ding et al. (8 autores). **Ano:** 2014. **Fonte:** Proc. 11th USENIX OSDI, p. 249-265 (sem DOI).
- **Enquadramento:** "almost all failures require only 3 or fewer nodes to reproduce". Apoio empírico a instâncias pequenas, ao lado da REF-049 (Jackson).
- **Citação:** `\cite{yuan_simple_testing_2014}`. **Origem:** CAND-058, Decisão 018. **Status:** Aprovada.

## [REF-057] Scientific Benchmarking of Parallel Computing Systems
- **Autores:** Hoefler, Torsten; Belli, Roberto. **Ano:** 2015. **Fonte:** Proc. SC '15, ACM, p. 1-12. **DOI:** 10.1145/2807591.2807644.
- **Enquadramento:** execução como unidade, percentis não paramétricos, "n > 5 measurements are needed to assess confidence intervals nonparametrically", Regra 8 (percentis para latência de pior caso).
- **Citação:** `\cite{hoefler_belli_benchmarking_2015}`. **Origem:** CAND-059, Decisão 018. **Status:** Aprovada.

## [REF-058] An Introduction to the Bootstrap
- **Autores:** Efron, Bradley; Tibshirani, Robert J. **Ano:** 1993. **Fonte:** Chapman & Hall, New York. **DOI:** 10.1007/978-1-4899-4541-9.
- **Enquadramento e ressalva:** só metadados e sumário conferidos (caps. 13-15: IC por percentis, IC melhores, testes de permutação). Citar **somente** se o texto usar *bootstrap* ou teste de permutação.
- **Conferência de 2026-10-30 (sumário):** cap. 15 "Permutation tests" = 15.1 Introduction, 15.2 The two-sample problem, 15.3 Other test statistics, 15.4 Relationship of hypothesis tests to confidence intervals and the bootstrap, 15.5 Bibliographic notes, 15.6 Problems; cap. 16 traz testes *bootstrap* (16.4, uma amostra). **Não cobre** o teste de permutação por troca de sinal de diferenças pareadas; não citar para ele (ver CAND-084). Cobre o *bootstrap* percentil (cap. 13) e o teste de permutação de duas amostras.
- **Citação:** `\cite{efron_tibshirani_bootstrap_1993}`. **Origem:** CAND-060, Decisão 018. **Status:** Aprovada.

## [REF-060] Power Provisioning for a Warehouse-Sized Computer
- **Autores:** Fan, Xiaobo; Weber, Wolf-Dietrich; Barroso, Luiz André. **Ano:** 2007. **Fonte:** Proc. 34th ISCA, ACM, p. 13-23. **DOI:** 10.1145/1250662.1250665.
- **Enquadramento:** modelo de potência linear na utilização de CPU, P = P_idle + (P_busy - P_idle)·u (com variante empírica de parâmetro r). Base do *proxy* de energia.
- **Citação:** `\cite{fan_power_provisioning_2007}`. **Origem:** CAND-062, Decisão 018. **Status:** Aprovada.

## [REF-061] Virtual Machine Power Metering and Provisioning (Joulemeter)
- **Autores:** Kansal, Aman; Zhao, Feng; Liu, Jie; Kothari, Nupur; Bhattacharya, Arka A. **Ano:** 2010. **Fonte:** Proc. 1st ACM SoCC, p. 39-50. **DOI:** 10.1145/1807128.1807136.
- **Enquadramento:** "VM power cannot be measured purely in hardware"; justifica estimar energia de VM/contêiner por modelo de uso de recursos. Verificado só pelo resumo.
- **Citação:** `\cite{kansal_vm_power_2010}`. **Origem:** CAND-063, Decisão 018. **Status:** Aprovada.

## [REF-062] NIST SP 800-63B-4: Digital Identity Guidelines: Authentication and Authenticator Management
- **Autores:** Temoshok, D.; Fenton, J. L.; Choong, Y.-Y.; Lefkovitz, N.; Regenscheid, A.; Galluzzo, R.; Richer, J. P. **Ano:** 2025. **DOI:** 10.6028/NIST.SP.800-63b-4.
- **Enquadramento:** substitui a REF-036 (retirada). Sec. 5 normativa sobre *session management* (reautenticação, *timeouts*) e seção de *rate limiting*. Base normativa de I1/I2.
- **Conferido em 2026-10-30 (texto HTML, pages.nist.gov/800-63-4/sp800-63b.html):** prazos por AAL ficam na Sec. 2, não na Sec. 5. AAL1 (2.1.3): *overall timeout* SHALL ser estabelecido e SHOULD ser de no máximo 30 dias; *inactivity timeout* MAY, não exigido. AAL2 (2.2.3): *overall* SHOULD ≤ 24 h; inatividade SHOULD ≤ 1 h. AAL3 (2.3.3): *overall* SHALL ≤ 12 h; inatividade SHOULD ≤ 15 min. Em todos, reautenticação periódica SHALL conforme Sec. 5.2. Sec. 5 "Session Management" (5.1 Session Bindings: segredos de sessão "erased or invalidated by the session subject when the subscriber logs out"; 5.2 Reauthentication: "When either timeout expires, the session SHALL be terminated"; 5.3 Session Monitoring). Sec. 3.2.2 Rate Limiting (Throttling): no máximo 100 tentativas falhas consecutivas por autenticador, com limites menores permitidos. Diferença para a REF-036: AAL2 passou de 12 h/30 min para 24 h/1 h, e os valores de AAL1 e AAL2 viraram SHOULD.
- **Citação:** `\cite{nist_sp_800_63b4_2025}`. **Origem:** CAND-064, Decisão 018. **Status:** Aprovada.

## [REF-064] The Case for Energy-Proportional Computing
- **Autores:** Barroso, Luiz André; Hölzle, Urs. **Ano:** 2007. **Fonte:** IEEE Computer 40(12):33-37. **DOI:** 10.1109/MC.2007.443.
- **Enquadramento:** proporcionalidade energética; consumo ocioso alto em servidores. Apoio ao argumento de energia do controlador.
- **Citação:** `\cite{barroso_holzle_energy_proportional_2007}`. **Origem:** CAND-066 (ratificação P-028), Decisão 018. **Status:** Aprovada.

## [REF-066] tc-netem(8): Network Emulator
- **Autores:** Ludovici, Fabio; Pfeifer, Hagen Paul. **Ano:** 2011 (data da página). **Fonte:** Linux manual page (iproute2), https://man7.org/linux/man-pages/man8/tc-netem.8.html.
- **Enquadramento:** definição de netem/tc para a injeção de atraso e perda (F3).
- **Citação:** `\cite{netem_manpage}`. **Origem:** CAND-068 (ratificação P-028), Decisão 018. **Status:** Aprovada.

## [REF-071] The Vision of Autonomic Computing
- **Autores:** Kephart, Jeffrey O.; Chess, David M. **Ano:** 2003. **Fonte:** IEEE Computer 36(1):41-50. **DOI:** 10.1109/MC.2003.1160055 (Crossref e OpenAlex).
- **Verificação do MAPE-K (2026-10-30, texto integral, cópia de curso do Inria):** a Figura 2 ("Structure of an autonomic element", p. 44) mostra o gerente autônomo com os blocos Monitor, Analyze, Plan e Execute em torno de Knowledge, acima do elemento gerenciado. O texto diz: "By monitoring the managed element and its external environment, and constructing and executing plans based on an analysis of this information, the autonomic manager will relieve humans of the responsibility of directly managing the managed element." A sigla "MAPE-K" **não aparece** no artigo (o nome é posterior, consagrado pelo *blueprint* da IBM). Redação segura: "o laço monitorar-analisar-planejar-executar sobre uma base de conhecimento (MAPE-K), do gerente autônomo descrito por \citeonline{kephart_chess_autonomic_2003}".
- **Citação:** `\cite{kephart_chess_autonomic_2003}`. **Origem:** CAND-074, Decisão 018. **Status:** Aprovada.

## [REF-072] Control Group v2 (documentação do kernel Linux)
- **Autor:** Heo, Tejun (documento da Linux Kernel documentation). **Fonte:** https://docs.kernel.org/admin-guide/cgroup-v2.html (acesso em 30 out. 2026).
- **Enquadramento:** `cpu.stat` (`usage_usec`, `user_usec`, `system_usec`, em microssegundos) como sinal de CPU por contêiner no *proxy* de energia. Se o *proxy* usar cgroup v1, trocar a fonte.
- **Citação:** `\cite{linux_cgroup_v2}`. **Origem:** CAND-076, Decisão 018 (chave definida pelo Gestor; a proposta era `linux_cgroup_v2_doc`). **Status:** Aprovada.

## [REF-073] The Preregistration Revolution
- **Autores:** Nosek, Brian A.; Ebersole, Charles R.; DeHaven, Alexander C.; Mellor, David T. **Ano:** 2018. **Fonte:** PNAS 115(11):2600-2606. **DOI:** 10.1073/pnas.1708274114.
- **Enquadramento:** pré-registro (perguntas e plano de análise fixados antes de ver os resultados). Sustenta o pré-registro do protocolo da medição.
- **Citação:** `\cite{nosek_preregistration_2018}`. **Origem:** CAND-077, Decisão 018. **Status:** Aprovada.

## [REF-074] The Probable Error of a Mean
- **Autor:** "Student" (Gosset, William Sealy). **Ano:** 1908. **Fonte:** Biometrika 6(1):1-25. **DOI:** 10.2307/2331554. A página final 25 vem da folha de rosto do JSTOR (o Crossref só traz a página inicial).
- **Enquadramento:** distribuição t para IC da média com poucas repetições. Citar só se o texto usar t de Student.
- **Citação:** `\cite{student_probable_error_1908}`. **Origem:** CAND-078, Decisão 018. **Status:** Aprovada.

## [REF-075] Interval Estimation for a Binomial Proportion
- **Autores:** Brown, Lawrence D.; Cai, T. Tony; DasGupta, Anirban. **Ano:** 2001. **Fonte:** Statistical Science 16(2):101-133. **DOI:** 10.1214/ss/1009213286.
- **Enquadramento:** mau comportamento do intervalo de Wald com proporções pequenas; recomenda Wilson ou Agresti-Coull. Só metadados conferidos (texto não lido); o enquadramento é o uso consagrado do artigo.
- **Citação:** `\cite{brown_cai_dasgupta_binomial_2001}`. **Origem:** CAND-079, Decisão 018. **Status:** Aprovada.

## [REF-076] On a Test of Whether One of Two Random Variables is Stochastically Larger than the Other
- **Autores:** Mann, Henry B.; Whitney, Donald R. **Ano:** 1947. **Fonte:** The Annals of Mathematical Statistics 18(1):50-60. **DOI:** 10.1214/aoms/1177730491.
- **Enquadramento:** origem do teste de Mann-Whitney, já usado em `Metodologia.tex` sem citação.
- **Citação:** `\cite{mann_whitney_1947}`. **Origem:** CAND-080, Decisão 018. **Status:** Aprovada.

## [REF-077] Statistically Rigorous Java Performance Evaluation
- **Autores:** Georges, Andy; Buytaert, Dries; Eeckhout, Lieven. **Ano:** 2007. **Fonte:** Proc. OOPSLA '07, ACM, p. 57-76. **DOI:** 10.1145/1297027.1297033.
- **Enquadramento e ressalva:** não determinismo entre execuções da JVM (JIT, GC, escalonamento) e várias invocações da VM como unidade. Verificado pelo resumo; a recomendação específica de t de Student com poucas execuções não foi conferida no texto, então não atribuí-la ao artigo sem leitura.
- **Citação:** `\cite{georges_jvm_statistics_2007}`. **Origem:** CAND-081, Decisão 018 (chave definida pelo Gestor; a proposta era `georges_java_performance_2007`). **Status:** Aprovada.

---

## Promovidas em 2026-10-30 (Decisão 019, estatística da avaliação do controlador)

## [REF-078] A Simple Sequentially Rejective Multiple Test Procedure
- **Autor:** Holm, Sture. **Ano:** 1979. **Fonte:** Scandinavian Journal of Statistics 6(2):65-70. JSTOR stable 4615733. **Sem DOI no `.bib`**: o DOI 10.2307/4615733 que o OpenAlex informa não resolve (Crossref 404, doi.org 404).
- **Enquadramento:** correção de Holm (Bonferroni sequencial), com controle forte do erro por família. Texto não lido; uso consagrado.
- **Citação:** `\cite{holm_sequential_1979}` (chave fixada pelo Gestor). **Origem:** CAND-082, Decisão 019. **Status:** Aprovada.

## [REF-079] If Nothing Goes Wrong, Is Everything All Right? Interpreting Zero Numerators
- **Autores:** Hanley, James A.; Lippman-Hand, Abby. **Ano:** 1983. **Fonte:** JAMA 249(13):1743-1745. **DOI:** 10.1001/jama.1983.03330370053031 (Crossref; autores e páginas pelo PubMed, PMID 6827763).
- **Enquadramento:** regra de três. Com zero eventos em N, o limite superior de 95 % da proporção fica próximo de 3/N. Texto não lido; uso consagrado.
- **Citação:** `\cite{hanley_lippmanhand_zero_1983}`. **Origem:** CAND-083, Decisão 019. **Status:** Aprovada.

## [REF-080] Permutation, Parametric and Bootstrap Tests of Hypotheses
- **Autor:** Good, Phillip I. **Ano:** 2005 (3. ed.). **Fonte:** Springer, New York (Springer Series in Statistics). **DOI:** 10.1007/b138696 (Crossref; autor pelo OpenAlex).
- **Enquadramento:** livro-texto de testes de permutação. O cap. 3 ("Testing Hypotheses") inclui testes de uma amostra para parâmetro de locação com teste de permutação, que é a forma do teste por troca de sinal das diferenças pareadas. Conferido só pelo sumário de catálogo; texto não lido.
- **Citação:** `\cite{good_permutation_tests_2005}`. **Origem:** CAND-084 (alternativa), Decisão 019. **Status:** Aprovada.

## [REF-081] The Design of Experiments
- **Autor:** Fisher, Ronald A. **Ano:** 1935 (1. ed.). **Fonte:** Edinburgh: Oliver and Boyd. Sem DOI.
- **Conferido em 2026-10-30** (digitalização da 1. ed., archive.org `in.ernet.dli.2015.502684`, folha de rosto "Oliver and Boyd, Edinburgh [...] 1935"): cap. III "A Historical Experiment on Growth Rate", **seção 21 "Test of a Wider Hypothesis"**, p. 50-53 da 1. ed. Com os 15 pares de *Zea mays* de Darwin: "the 15 differences of Table 3 would each have occurred with equal frequency with a positive or with a negative sign [...] how many of the 2^15 numbers, which may be formed by giving each component alternatively a positive and a negative sign, exceed this value". Resultado: 861 de 32.768 em cada cauda, 5,255 % no bilateral, próximo do t. É a origem do teste por troca de sinal.
- **Citação:** `\cite[seção 21]{fisher_design_experiments_1935}` (a paginação muda entre edições, por isso citar pela seção). **Origem:** CAND-084, Decisão 019. **Status:** Aprovada.


## [REF-082] Reliable group communication with JGroups (JGroups Manual 5.x)
- **Autor:** Ban, Bela (projeto JGroups, Red Hat). **Ano:** página sem data; `year = 2026` (ano de acesso, mesmo padrão das entradas Infinispan). **URL:** http://www.jgroups.org/manual5/index.html (certificado TLS do host inválido, por isso `http://`). **Acesso:** 3 nov. 2026.
- **Conferido em 2026-11-03** (manual baixado e lido): título da página "Reliable group communication with JGroups", autor "Bela Ban", "The installation refers to version 5.x of JGroups". Seções usadas: §3.10.13 "Disconnecting from a channel" (pedido de saída ao coordenador, que instala nova visão); §6.8 "Failure Detection"; §6.8.4 FD_ALL3; §6.8.6 FD_SOCK2 (`connectionClosed` emite SUSPECT; troca de visão não gera suspeita); §6.8.7, Tabela 35 ("Member N leaves: Since this is a graceful leave, none of the failure detection protocols kick in").
- **Capítulos relacionados:** Cap. 4 (DES-54); pode apoiar também MET-15 e a Metodologia, linha 320.
- **Citação:** `\cite[seções 3.10.13 e 6.8.7]{jgroups_manual_5}`. **Origem:** CAND-085, Decisão 020. **Status:** Aprovada.


## Promovidas em 2026-11-04 (Decisão 021)

Fichas detalhadas (trechos lidos, limites) no Eixo N e no Eixo O de `05-referencias-candidatas.md`. Textos em `tcc/leituras/`.

| REF | Chave | Origem | Onde será usada |
|---|---|---|---|
| REF-083 | `paxson_packet_dynamics_1997` | CAND-087 | DES-55, N1..N3 de F4 (perdas de 2,7 %, 5,2 % e ~17 % medidas, §5.1) e perda assimétrica (§5.2); citar por seção |
| REF-084 | `guo_pingmesh_2015` | CAND-088 | DES-55, N1 (perda normal 1e-4..1e-5, §4.2; incidente com 1 % a 2 %, §5.2) |
| REF-085 | `tan_netbouncer_2019` | CAND-089 | DES-55, N3 (falha cinzenta com ~15 %, p. 609) e N4/N5 (60 % a 100 %, p. 608) |
| REF-086 | `alfatafta_partial_partitioning_2020` | CAND-086 | F4 e partição parcial; perda de dados por partição parcial; neutralização do lado isolado (§5.4) |
| REF-087 | `infinispan_server_15` | CAND-090 | `Desenvolvimento.tex`:73/:97, TCPPING (§12.2.2) |
| REF-088 | `infinispan_rest_15` | CAND-091 | `Desenvolvimento.tex`:73/:143, saúde do cluster (§2.4.2) e `set-availability` (§2.1.32) |
| REF-089 | `infinispan_cli_15` | CAND-092 | comando `availability --mode` (§9.4) |
| REF-090 | `berger_iut_1982` | CAND-093 | `Metodologia.tex`:229, critérios conjuntos como teste de interseção-união |
| REF-091 | `intel_i5_14600kf` | CAND-094 | `Metodologia.tex`:335, potência do processador no modelo linear |
| REF-092 | `hayashibara_phi_accrual_2004` | CAND-095 | desenho do controlador: suspeita graduada antes da detecção definitiva |
| REF-093 | `huang_gray_failure_2017` | CAND-096 | falha cinzenta e observabilidade diferencial; motivação da sonda |
| REF-094 | `huang_panorama_2018` | CAND-097 | observações do cliente como sinal de falha |
| REF-095 | `bailis_pbs_2012` | CAND-098 | t-visibilidade/defasagem medida pela sonda |
| REF-096 | `wada_consistency_cloud_2011` | CAND-099 | precedente da sonda escreve-num-nó/lê-nos-outros |
| REF-097 | `gray_cheriton_leases_1989` | CAND-100 | princípio de autorização com prazo para a ação de modo restrito |

Status de todas: **Aprovada** (Decisão 021). Já presentes antes e reusadas: `barroso_holzle_energy_proportional_2007` (REF-064, potência ociosa) e `chandra_toueg_failure_detectors_1996` (REF-033).

---

## Lacunas de cobertura bibliográfica (atualizado 2026-05-12)

Após a migração das 22 candidatas (Decisão 006), o fechamento das lacunas F e H (Decisão 009), o ciclo de curadoria de 2026-05-17 (Decisão 012) e a promoção de REF-045/REF-046 pela Decisão 013 (2026-05-25), a biblioteca cobre **33 referências aprovadas em 8 eixos** (REF-012 e REF-014 dispensadas; REF-042 Lamport, REF-043 Raft, REF-044 Herlihy-Wing, REF-045 Infinispan Tuning e REF-046 YCSB promovidas: efetivo 33):

- **A — Replicação e consistência:** REF-001 (Dynamo), REF-002 (CRDTs Shapiro), REF-010 (Consistency in Scalable Systems), REF-011 (Safety of Distributed Objects), REF-013 (Quantitative NoSQL Consistency), REF-032 (CAP). *(REF-012 dispensada.)*
- **B — IMDG / cache:** REF-006/007/008/009 (Infinispan), **REF-045 (Infinispan Tuning Guide)**. *(REF-014 dispensada.)*
- **C — Métodos formais e consenso:** REF-003 (TLC), REF-016 (FLP 1985), REF-017 (Why Amazon Chose TLA+), REF-018 (MC Guided Testing), REF-019 (MC Distributed Protocols Must), REF-020 (Probabilistic MC NoSQL), **REF-043 (Raft)**.
- **D — SLO / latência / observabilidade:** REF-004 (Tail at Scale), REF-005 (SRE SLO), REF-015 (TailX), REF-021/022 (Taming/Scheduling Tail Latency), REF-023 (Weaker consistency + monitoring), REF-024 (Observability Survey), **REF-046 (YCSB — benchmark canônico)**.
- **E — Autoadaptativo / controle:** REF-025 (Control of Cloud Elasticity), REF-026 (Anna Autoscaling), REF-027 (OnlineElastMan), REF-028 (Assurance Self-adaptive), REF-029 (Self-mgmt Distributed).
- **F — Falhas / partições:** REF-016 (FLP — também aqui), REF-032 (CAP — também aqui), REF-033 (Failure Detectors), REF-034 (Network is Reliable).
- **G — Metodologia:** REF-030 (Neiva & Silva — guia SLR em PT), REF-037 (Petersen — Systematic Mapping), REF-038 (PRISMA 2020).
- **H — Sessão (normativo):** REF-035 (OWASP), REF-036 (NIST SP 800-63B).
- **Contexto geral / sustentabilidade:** REF-031 (Armbrust — *View of cloud computing*), REF-039 (Turner — Carbon Footprint), REF-040 (Morán — FT HPC Energy), REF-041 (Lemos — ERAD-RS 2025).
- **Tempo / ordenação:** **REF-042 (Lamport 1978 — *Time, Clocks*)**.

**Lacunas residuais** (não cobertas por nenhuma referência aprovada — atualizado 2026-05-17 após Decisão 012):
- ~~Lamport 1978 (*Time, Clocks*)~~ — **fechada (REF-042)**.
- ~~Ongaro & Ousterhout 2014 (Raft)~~ — **fechada (REF-043)**.
- Schneider 1990 (SMR tutorial) → CAND-003 — implícito em Raft (REF-043), baixa prioridade.
- Herlihy & Wing 1990 (Linearizabilidade) → CAND-005 — **aprovada em princípio na Decisão 012, aguardando aquisição via CAPES/ACM** (DOI 10.1145/78969.78972).
- ~~IBM MAPE-K (autonomic computing) → CAND-014~~ — **fechada pela REF-071 (Kephart & Chess 2003, Decisão 018)**.
- Hellerstein et al. 2004 (*Feedback Control of Computing Systems*) → CAND-016.
- *Chaos engineering* / Jepsen relatórios → CAND-017, CAND-018.
- Wieringa 2014 (*Design Science*) → CAND-019.
- Wohlin et al. 2012 (*Experimentation in SE*) → CAND-020.
- ~~Lamport 2002 (livro TLA+)~~ → CAND-010 — **fechada (REF-047, Tarefa T-A 2026-06-18)**.

Estas residuais permanecem em [`05-referencias-candidatas.md`](05-referencias-candidatas.md), Parte I. **Buscas em bases externas seguem suspensas** (Decisão 004) — exceto pelas frentes específicas autorizadas em Decisão 009 (concluídas).
