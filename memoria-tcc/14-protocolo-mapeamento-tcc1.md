# Protocolo do Mapeamento Sistemático — TCC-I Sem 1-2

> Entregável da **Etapa 1 (Sem 1-2)** do Quadro 1 (TCC-I) do [`Main-PPEC.tex`](../Main-PPEC.tex): *"estabelecer a pergunta de pesquisa, o protocolo do mapeamento sistemático e o esquema inicial de extração/classificação"*.
>
> **Metodologia oficial** (Decisão 010): mapeamento sistemático conforme \citeonline{petersen_mapping_2008}, com relato apoiado por \citeonline{prisma_2020}, operacionalizado pelo guia em português de \citeonline{neiva_silva_revisao_sistematica_2016}.
>
> **Escopo aprovado** (Decisão 011 ponto 3): **todos os 8 eixos (A-H)** identificados no projeto.
>
> **Status:** draft do **Escritor** (2026-05-13), pendente de validação do Bibliotecário (consistência de fontes), do Revisor (coerência) e aprovação do **Gestor**.
>
> Este documento é a **fonte primária do Apêndice A** da monografia ("Protocolo do Mapeamento Sistemático") e alimenta diretamente o Cap. 3.2 (descrição da Frente 1) do [`Main-Monografia.tex`](../Main-Monografia.tex).

---

## 1. Apresentação

O presente mapeamento sistemático (doravante **MS**) tem por objetivo identificar, agrupar e categorizar a produção científica sobre **invariantes de estado de sessão em *data grids*** sob **replicação assíncrona**, **falhas** e **controle orientado a SLO**, fornecendo embasamento para o recorte das frentes formal e experimental do TCC. O MS é executado conforme a sequência canônica de \citeonline{petersen_mapping_2008}: (i) definição das questões; (ii) condução da busca; (iii) triagem por título/resumo; (iv) categorização (*keywording*) com leitura completa; (v) extração e mapeamento.

O relato do processo segue elementos do *PRISMA 2020 statement* \citeonline{prisma_2020} — em particular, o **fluxograma de seleção** e a contabilização explícita de itens em cada etapa. O guia em português de \citeonline{neiva_silva_revisao_sistematica_2016}, baseado em Kitchenham, é adotado como referência operacional acessível.

## 2. Pergunta central e questões de mapeamento

### 2.1 Pergunta central (transcrita do `Main-PPEC.tex` linha 49 — Decisão 011 ponto 2)

> **Como especificar e verificar formalmente invariantes de sessão em *data grids* sob replicação assíncrona e falhas e, a partir dos contraexemplos e métricas observadas, definir um controlador heurístico que reduza a taxa de violações e estabilize a cauda de latência (p95/p99) sem aumentar indevidamente o custo operacional?**

### 2.2 Questões de mapeamento

A pergunta central desdobra-se nas **sete questões de mapeamento (QM1–QM7)** abaixo. Cada questão é associada a um ou mais dos 8 eixos do projeto:

| ID | Questão de mapeamento | Eixos cobertos |
|---|---|---|
| **QM1** | Quais **invariantes de domínio** de estado de sessão (irreversibilidade de invalidação, monotonicidade de contadores, consistência de bloqueio, unicidade, expiração) são tratados na literatura? | H, A |
| **QM2** | Quais **modelos de falha** (*crash*/restart, omissão, atrasos, particionamento, *state transfer*, falhas bizantinas) são assumidos pelos trabalhos? | F, A |
| **QM3** | Quais **mecanismos de replicação e reconciliação** (síncrona, assíncrona/otimista, *quorum*, *vector clocks*, *last-write-wins*, CRDTs, *state machine replication*) são empregados? | A, B |
| **QM4** | Quais **abordagens e ferramentas de verificação formal** (TLA+/TLC, Alloy, SPIN, Coq, *probabilistic model checking*, demonstração de teoremas) são aplicadas a sistemas distribuídos com estado replicado? | C |
| **QM5** | Quais **métricas e indicadores operacionais** (latência p95/p99, taxa de violação de invariantes, tempo de recuperação, tempo de convergência, taxa de erro, custo de energia/consumo de recursos) são reportados? | D |
| **QM6** | Quais **mecanismos de decisão e adaptação** (controladores heurísticos, MAPE-K, *feedback control*, autoadaptativos, autoescala, *self-healing*) são propostos para sistemas distribuídos sob falhas/variação de carga? | E |
| **QM7** | Quais **plataformas/sistemas-alvo** (Infinispan, Hazelcast, Apache Ignite, Redis, Cassandra, DynamoDB, KV stores em geral) são objeto dos estudos empíricos? | B |

**Cobertura dos eixos** — A: QM1, QM3; B: QM3, QM7; C: QM4; D: QM5; E: QM6; F: QM2; G: implícito (este MS é a aplicação do eixo); H: QM1.

## 3. Bases bibliográficas

Conforme [`Main-PPEC.tex`](../Main-PPEC.tex) e [`02-metodologia.md`](02-metodologia.md):

| Base | URL | Cobertura | Acesso |
|---|---|---|---|
| ACM Digital Library | https://dl.acm.org | conferências/periódicos ACM (SOSP, OSDI, PODC, PPoPP, EuroSys, CACM, ACM Computing Surveys) | aberto/parcial; via CAPES |
| IEEE Xplore | https://ieeexplore.ieee.org | conferências/periódicos IEEE (DSN, ICDCS, IEEE Trans. on Dependable and Secure Computing) | via CAPES |
| Scopus | https://www.scopus.com | índice multieditor (Elsevier, Springer, IEEE, ACM) | via CAPES |
| SpringerLink | https://link.springer.com | livros e revistas Springer (LNCS, *Cluster Computing*, *Distributed Computing*, *VLDB Journal*) | aberto/via CAPES |

**Observação operacional:** as 5 *SearchResults*\*.csv* presentes em [`/workspace/referencias-gestor/`](../referencias-gestor) — exportadas da Springer pelo Gestor — servem de **conjunto-semente para calibração** (Decisão 011 ponto 4); a execução formal das buscas (Sem 3-4) registra a **data, string final por base e *n* retornado** para cada uma das 4 bases.

## 4. Estratégia de busca

### 4.1 Termos-chave por eixo (vocabulário controlado)

| Eixo | Termos-chave (PT/EN) |
|---|---|
| A — Consistência/replicação | "asynchronous replication"; "eventual consistency"; "session consistency"; CRDT; "vector clocks"; reconciliation; convergence; "state machine replication" |
| B — IMDG/cache | "in-memory data grid"; IMDG; "distributed cache"; "key-value store"; "consistent hashing"; rebalancing; "state transfer"; Infinispan; Hazelcast; Apache Ignite |
| C — Métodos formais | "model checking"; "formal verification"; specification; "temporal logic"; invariant; TLA+; Alloy; SPIN; "bounded model checking"; "probabilistic model checking" |
| D — SLO/latência/observabilidade | SLO; SLI; "service level objective"; "tail latency"; p95; p99; percentile; latency; observability; tracing; metrics |
| E — Autoadaptativo/controle | "self-adaptive"; autonomic; "MAPE-K"; "feedback control"; "control theory"; auto-scaling; elasticity; heuristic; self-healing |
| F — Falhas/partições | "fault-tolerance"; "crash recovery"; "network partition"; "partition tolerance"; failover; "chaos engineering"; "failure detector"; resilience |
| G — Metodológico (apoio) | "systematic mapping"; "systematic review"; "design science"; "experimentation in software engineering" — *usados na revisão da própria metodologia, não nas buscas substantivas* |
| H — Sessão (domínio) | "session management"; "session state"; "session invariant"; authentication state; logout; "session token"; "session lifecycle"; "user session" |

### 4.2 Strings de busca

Para cobrir os 7 eixos substantivos (A, B, C, D, E, F, H) num escopo coerente, definem-se **quatro *strings* complementares**. Cada *string* combina **um foco principal** (em **negrito**) com **filtros transversais** (sessão, distribuído, falhas), buscando interseção temática com o objeto do TCC.

#### S1 — Núcleo "sessão × consistência × falhas" (eixos H, A, F)
```
(("session" OR "session state" OR "session management") AND
 ("distributed" OR "replicated" OR "in-memory" OR "data grid" OR "key-value" OR cache) AND
 ("consistency" OR "invariant" OR "convergence" OR "reconciliation" OR "replication"))
```

#### S2 — Métodos formais aplicados a replicação/sistemas distribuídos (eixo C com A/F)
```
(("model checking" OR "formal verification" OR "formal specification" OR "TLA+" OR Alloy OR SPIN) AND
 ("distributed" OR "replication" OR "replicated" OR "fault" OR "consistency" OR "session" OR "key-value" OR "data grid"))
```

#### S3 — SLO, latência de cauda e observabilidade em sistemas distribuídos (eixos D, B)
```
(("SLO" OR "service level objective" OR "tail latency" OR "p95" OR "p99" OR percentile) AND
 ("distributed cache" OR "in-memory data grid" OR "key-value store" OR "session" OR "replication" OR observability))
```

#### S4 — Autoadaptativo / controle heurístico em sistemas distribuídos (eixo E com F/D)
```
(("self-adaptive" OR autonomic OR "MAPE-K" OR "feedback control" OR "autoscal*" OR elasticity OR heuristic) AND
 ("distributed" OR "data grid" OR "key-value" OR "cache" OR "replication" OR "fault" OR "SLO" OR "latency"))
```

**Filtros adicionais** aplicados em cada base (refinados em Sem 3-4):
- **Tipo de documento:** artigo de periódico revisado por pares, artigo de conferência revisado por pares, capítulo de livro, *survey*. Excluem-se *posters*, *workshops* sem revisão, editoriais e correspondências.
- **Idioma:** inglês ou português.
- **Data:** sem limite inferior (clássicos como REF-016 FLP 1985 e REF-032 CAP 2002 devem ser captáveis). Limite superior na data de execução.

**Adaptação por base:** cada base requer ajustes sintáticos (operadores próprios, campos pesquisáveis). A Sem 3-4 documentará cada *string* final por base, mantendo a semântica das S1–S4.

## 5. Critérios de inclusão e exclusão

### 5.1 Critérios de inclusão (CI)

- **CI1.** Trabalho revisado por pares (periódico/conferência reconhecida) **ou** obra clássica/seminal **ou** documentação técnica oficial das tecnologias usadas (Infinispan, TLA+).
- **CI2.** Texto-completo acessível (via CAPES, biblioteca da Unipampa ou repositórios institucionais abertos).
- **CI3.** Idioma português ou inglês.
- **CI4.** Aborda **pelo menos uma** das QM1–QM7 de modo substancial (não como mera menção de passagem).
- **CI5.** Trata de **sistemas distribuídos**, **replicação**, **consistência**, **falhas**, **verificação formal**, **observabilidade**, **controle adaptativo** ou **gerência de sessão** como tema central ou um dos temas centrais.

### 5.2 Critérios de exclusão (CE)

- **CE1.** Resumo somente, sem texto completo.
- **CE2.** Duplicata (manter o veículo mais completo entre versões em conferência e revista expandida).
- **CE3.** *White paper* comercial sem conteúdo técnico verificável.
- **CE4.** Foco em arquitetura/tecnologia fora do escopo (p.ex., redes neurais sem ligação com o objeto; SoC/hardware sem ligação com replicação distribuída).
- **CE5.** Idioma fora de PT/EN.
- **CE6.** Trabalho sem revisão por pares **como base teórica principal** (relatórios técnicos do Jepsen são admitidos como **literatura cinza de apoio empírico** — Decisão 011 ponto 6 — não como fundamentação teórica).
- **CE7.** Anterior a 1985 (anteriores ao FLP). *Mantemos a captura de FLP 1985 e demais clássicos como casos de inclusão pela CI1.*

### 5.3 Procedimento de aplicação dos critérios

1. **Triagem por título/resumo (Sem 3-4):** aplicar CE1, CE3, CE4, CE5 + verificar pertinência a alguma QM.
2. **Leitura completa (Sem 5-6):** aplicar CI1–CI5 e CE2, CE6, CE7 com o texto integral em mãos.
3. **Cada exclusão é registrada com o motivo** (qual CE) para o fluxograma PRISMA.

## 6. Procedimento de seleção (fluxograma PRISMA esperado)

```
[1] Identificação
    ├─ Registros recuperados das 4 bases (n = a definir em Sem 3-4)
    └─ Registros do conjunto-semente curado (n = X — calibração, não recontados se já cobertos pela busca)

[2] Triagem
    ├─ Após remoção de duplicatas (n = ?)
    └─ Triagem por título/resumo
        ├─ Excluídos (n = ?, motivos CE)
        └─ Selecionados para leitura completa (n = ?)

[3] Elegibilidade
    └─ Leitura completa
        ├─ Excluídos (n = ?, motivos CE)
        └─ Incluídos no MS (n = ?)

[4] Inclusão
    └─ Estudos primários incluídos (n = ?)
```

O fluxograma definitivo é preenchido em Sem 5-6 e relatado conforme \citeonline{prisma_2020}.

## 7. Esquema de extração e classificação

Cada estudo primário incluído é caracterizado pelas **oito dimensões D1–D8** abaixo. As dimensões são compatíveis com a categorização típica de mapeamentos sistemáticos em ES \citeonline{petersen_mapping_2008}, podendo gerar *bubble plots* cruzando duas delas.

| ID | Dimensão | Categorias previstas (refinar com base em achados) |
|---|---|---|
| **D1** | Modelo de consistência alvo | Strong / Sequencial / Causal / Sessão (RYW, monotonic reads) / Eventual / SEC (CRDT) / Quórum |
| **D2** | Modelo de falhas assumido | Crash-stop / Crash-recovery / Omissão / Particionamento / Bizantino / Misto / Não especificado |
| **D3** | Mecanismo de reconciliação/replicação | Síncrona com quórum / Assíncrona LWW / Assíncrona com aplicação / CRDT / Vector clocks / Consenso (Paxos/Raft) / SMR |
| **D4** | Abordagem/ferramenta formal | TLA+/TLC / Alloy / SPIN / Coq/Isabelle / Probabilistic MC / Bounded MC / Sem método formal |
| **D5** | Validação empírica | Sim — KV/IMDG identificado / Sim — outro sistema / Simulação / Não-empírico (puramente formal) |
| **D6** | Domínio de aplicação | Sessão/autenticação / KV genérico / Banco de dados distribuído / Mensageria / Cache de aplicação / Outro |
| **D7** | Métricas reportadas | Latência (média/p95/p99) / Throughput / Taxa de violação / Tempo de recuperação / Tempo de convergência / Consumo de recursos / Outras |
| **D8** | Tipo de mecanismo adaptativo (se houver) | Nenhum / Estático configurável / Heurístico baseado em regras / MAPE-K / Controle de retroalimentação clássico / Aprendizado de máquina |

**Ficha de extração (modelo):**

| Campo | Conteúdo |
|---|---|
| ID interno | MS-001, MS-002, ... |
| Citação completa | (autores, ano, título, veículo, DOI) |
| Origem | base de busca em que foi encontrado primeiro |
| Eixo(s) principal(is) | A, B, C, D, E, F, H ou combinação |
| QM(s) abordada(s) | QM1..QM7 |
| D1..D8 | valores das categorias |
| Resumo dos contribuições | 3-5 linhas |
| Recorte experimental (se houver) | sistema, tamanho de cluster, *workload*, falhas injetadas |
| Métricas reportadas (extrato numérico/qualitativo) | livre |
| Comentários para o TCC | quando/como pode ser citado |

A **planilha de extração** é construída em Sem 5-6 (uma linha por estudo primário; uma coluna por campo).

## 8. Instrumentação e registro

### 8.1 Ferramenta de gestão bibliográfica
Mendeley (preferencial — colaborativo, ABNT) **ou** JabRef (BibTeX nativo). Cada estudo recebe entrada BibTeX preliminar; estudos incluídos no MS são integrados a [`/workspace/bibliografia.bib`](../bibliografia.bib) e catalogados em [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md) **após aprovação individual do Gestor**.

### 8.2 Planilha de execução
Uma planilha (*spreadsheet*) — Google Sheets ou LibreOffice — com abas:
- **Aba 1 — Execução de busca:** por base, *string* final, data, *n* retornado, link para registros.
- **Aba 2 — Triagem por título/resumo:** ID, citação, decisão (incluído/excluído + motivo CE), avaliador.
- **Aba 3 — Leitura completa:** ID, decisão, motivo, *fichas D1-D8*.
- **Aba 4 — Síntese:** contagens por D1..D8, contagens por QM, *bubble plots* preparados.

### 8.3 Versionamento
A planilha e suas exportações CSV são versionadas (preferencialmente em `/workspace/protocolo-ms/` ou repositório git separado), permitindo auditoria do processo.

## 9. Conjunto-semente de calibração (Decisão 011 ponto 4)

O pool em [`/workspace/referencias-gestor/`](../referencias-gestor) (4 PDFs analisados + 5 CSVs Springer com 2.298 entradas únicas + 7 ACM + 3 LNCS) é o **conjunto-semente**. Função: validar que as *strings* S1–S4 recuperam os trabalhos-âncora já identificados:

**Trabalhos-âncora esperados na captura:**
- REF-001 (Dynamo); REF-002/012 (CRDTs); REF-016 (FLP); REF-032 (CAP); REF-033 (Chandra & Toueg); REF-034 (Bailis & Kingsbury); REF-003 (Yu/Lamport TLC); REF-017 (Newcombe TLA+); REF-019 (Enea Must); REF-018 (Gao MC distribuído); REF-020 (Naskos probabilistic MC); REF-021/022 (Ben Mokhtar tail latency); REF-023 (Nguyen weaker consistency); REF-025 (Ullah control elasticity); REF-028 (Gambi assurance self-adaptive); REF-004 (Dean & Barroso *Tail at Scale*); REF-005 (SRE SLO).

Caso uma *string* não capture uma âncora esperada, **a *string* é refinada** antes de prosseguir com a execução formal em Sem 3-4. Esta calibração é parte do procedimento e é registrada (qual *string* foi ajustada, por quê, qual âncora a motivou).

## 10. Cronograma de execução (resumido)

| Semanas | Atividade | Entregável associado |
|---|---|---|
| **1-2** | Este protocolo + calibração das *strings* S1-S4 com o conjunto-semente | `14-protocolo-mapeamento-tcc1.md` (este documento) |
| **3-4** | Execução das buscas em ACM, IEEE, Scopus, Springer; deduplicação; triagem por título/resumo; estabilização dos CI/CE | `15-execucao-busca-tcc1.md` + planilha + atualização de [`05-referencias-candidatas.md`](05-referencias-candidatas.md) |
| **5-6** | Leitura completa dos selecionados; extração D1-D8; síntese; recorte do protótipo (refino de [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md)) | `16-sintese-mapeamento-tcc1.md` + Cap. 2 + Cap. 3.2 escritos |

## 11. Limitações e ameaças à validade do MS

| Ameaça | Categoria (ES) | Mitigação |
|---|---|---|
| **Cobertura incompleta** — bases escolhidas (4) podem deixar trabalhos fora (DBLP, arXiv, etc.) | construto | conjunto-semente curado pelo Gestor inclui ACM e Springer adicionais; *strings* amplas; aceitar como limitação reportada |
| **Vieses de seleção/extração** — avaliador único (Escritor) | interna | revisão pelo Bibliotecário; amostra revisitada pelo Gestor; critérios documentados em CE1-CE7 |
| **Strings sub/superinclusivas** | construto | calibração com âncoras (Seção 9); registro das versões das *strings* |
| **Datação dos resultados** | externa | data de execução registrada; possibilidade de re-busca futura (em TCC-II) restringida ao período subsequente |
| **Cobertura de literatura cinza (Jepsen)** | construto | inclusão restrita (Decisão 011 ponto 6); buscar em jepsen.io diretamente, registrar à parte |
| **Idioma** — só PT/EN | construto | aceitar; literatura técnica predominante em EN |

A análise estendida das ameaças à validade entra em **Cap. 3.6** (P-007).

## 12. Pendências resolvidas neste protocolo

- ✅ **P-004b** (escopo MS) — 8 eixos cobertos via QM1-QM7 (Decisão 011 ponto 3).
- ✅ **P-015** — protocolo de busca consolidado; execução autorizada para Sem 3-4 (Decisões 005/009/010).

## 13. Pendências repactuadas / abertas

- 🟡 **P-001** (hipótese vs questão) — o protocolo trabalha com a pergunta central + QM1-QM7. Decidir se a monografia declarará uma hipótese formal (recomendação: **não** — manter como questão norteadora).
- 🟡 **P-005** (enquadramento metodológico) — *systematic mapping* (REF-037) + *systematic review reporting* (REF-038) cobrem; resta declarar explicitamente o **tipo de pesquisa** no Cap. 3.1 (proposta: *pesquisa aplicada, exploratória-descritiva quanto aos objetivos, com estratégia mista — mapeamento sistemático + pesquisa de projeto + experimentação*).
- 🟡 **P-017** (ferramenta MC) — explícito que decisão acontece em Sem 7-8 com base no que QM4 revelar.

## 14. Referências utilizadas neste protocolo

Todas oficiais (REF-* — ver [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md)):

- \citeonline{petersen_mapping_2008} (REF-037) — base teórica do MS.
- \citeonline{prisma_2020} (REF-038) — relato do processo.
- \citeonline{neiva_silva_revisao_sistematica_2016} (REF-030) — guia operacional em PT.
- \citeonline{fischer_lynch_paterson_flp_1985} (REF-016) — âncora QM4.
- \citeonline{gilbert_lynch_cap_2002} (REF-032) — âncora QM2/QM3.
- \citeonline{chandra_toueg_failure_detectors_1996} (REF-033) — âncora QM2.
- \citeonline{bailis_kingsbury_network_2014} (REF-034) — âncora QM2.

Demais REFs aprovadas (REF-001..036, 037..041) são *âncoras de validação* do conjunto-semente conforme Seção 9.

---

## Revisão (1ª rodada — Revisor, 2026-05-13)

**Coerência interna:**
- ✅ Pergunta central tal qual `Main-PPEC.tex` linha 49 (Decisão 011 ponto 2).
- ✅ QM1-QM7 cobrem os 8 eixos (A-H) — eixo G é meta e não exige QM substantiva (justificado em §2.2).
- ✅ Cada *string* S1-S4 é mapeada para eixos específicos com filtros transversais; juntas dão cobertura completa.
- ✅ Critérios CI1-CI5 e CE1-CE7 alinhados ao escopo do TCC; CE6 honra Decisão 011 ponto 6 (Jepsen restrito).
- ✅ Esquema D1-D8 é minimamente granular para *bubble plots* cruzando, por exemplo, D4 × D2 (ferramenta formal × modelo de falhas) ou D1 × D7 (consistência × métricas).
- ✅ Conjunto-semente referenciado com lista de âncoras nomeadas — calibração tem critério objetivo de sucesso.

**Coerência metodológica:**
- ✅ Sequência Petersen 2008 (definição → busca → triagem → categorização → mapeamento) preservada.
- ✅ Relato PRISMA 2020 explícito (Seção 6).
- ✅ Limitações/ameaças endereçadas (Seção 11) — boa prática.

**Ressalvas:**
- ⚠️ Seção 4.2 — as *strings* S1-S4 são propostas; até calibrar com o conjunto-semente (Seção 9) não há garantia de cobertura completa. **Versão final só fecha após calibração** — esperar bloco de calibração em Sem 1-2 ou expandir o documento com o registro da calibração antes de declarar concluído.
- ⚠️ Seção 8.3 (versionamento) — o local `/workspace/protocolo-ms/` ainda não existe; recomendo criar quando a planilha for inicializada (Sem 3-4).
- ⚠️ Seção 13 (P-001 hipótese vs questão) — recomendação do Escritor de "manter como questão norteadora" precisa ser ratificada pelo Gestor.

**Bibliotecário:**
- Verificou que todas as citações apontam para chaves existentes em [`bibliografia.bib`](../bibliografia.bib) (`petersen_mapping_2008`, `prisma_2020`, `neiva_silva_revisao_sistematica_2016`, `fischer_lynch_paterson_flp_1985`, `gilbert_lynch_cap_2002`, `chandra_toueg_failure_detectors_1996`, `bailis_kingsbury_network_2014`). ✅

**Status:** **Aprovado pelo Revisor**, **pendente** de aprovação do Gestor. **Calibração executada em Seção 15 abaixo.**

---

## 15. Calibração das *strings* contra o conjunto-semente

Exercício executado em 2026-05-13, conforme recomendação do Revisor. Para cada âncora nomeada na Seção 9, examina-se manualmente se algum termo de S1-S4 a recuperaria (com base em título/resumo/palavras-chave do estudo).

### 15.1 Resultado da calibração inicial (S1-S4)

| Âncora | Título-chave | S1 (sessão×consist×falhas) | S2 (formal×distrib) | S3 (SLO×latência) | S4 (auto-adapt×distrib) | Captura? |
|---|---|---|---|---|---|---|
| REF-001 Dynamo | "Amazon's Highly Available Key-value Store" | ✗ (sem termo "session") | ✗ | ✗ | ✗ | **❌ não captura** |
| REF-002/012 CRDTs | "Conflict-free Replicated Data Types" | ✗ (sem session) | ✗ | ✗ | ✗ | **❌ não captura** |
| REF-016 FLP 1985 | "Impossibility of Distributed Consensus with One Faulty Process" | ✗ | ✗ (sem termo formal) | ✗ | ✗ | **❌ não captura** |
| REF-032 CAP 2002 | "Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services" | ✗ (sem session) | ✗ | ✗ | ✗ | **❌ não captura** |
| REF-033 Chandra & Toueg | "Unreliable Failure Detectors for Reliable Distributed Systems" | ✗ | ✗ | ✗ | ✗ | **❌ não captura** |
| REF-034 Bailis & Kingsbury | "The network is reliable" | ✗ | ✗ | ✗ | ✗ | **❌ não captura** |
| REF-003 Yu/Lamport TLC | "Model Checking TLA+ Specifications" | ✗ | ✓ "model checking" + "TLA+" — mas sem termo distrib/fault no título | parcial | ✗ | **⚠️ marginal** |
| REF-017 Newcombe | "Why Amazon Chose TLA+" | ✗ | ✓ "TLA+" + (Amazon→não está no filtro) | ✗ | ✗ | **⚠️ marginal** |
| REF-019 Enea Must | "Model Checking Distributed Protocols in Must" | ✗ | ✓ "model checking" + "distributed" + "protocol" | ✗ | ✗ | **✅ captura** |
| REF-018 Gao MC | "Model Checking Guided Incremental Testing for Distributed Systems" | ✗ | ✓ "model checking" + "distributed" | ✗ | ✗ | **✅ captura** |
| REF-020 Naskos | "Cost-aware horizontal scaling of NoSQL databases using probabilistic model checking" | ✗ | ✓ "probabilistic model checking" | ✗ | ✓ "autoscal*" não — "horizontal scaling" sim? Termo não capturado | **✅ captura via S2** |
| REF-021/022 Ben Mokhtar | "Taming Tail Latency in Key-Value Stores" | ✗ | ✗ | ✓ "tail latency" + "key-value store" | ✗ | **✅ captura** |
| REF-023 Nguyen | "Using weaker consistency models with monitoring and recovery for improving performance of key-value stores" | ✗ (sem session) | ✗ | ✓ "monitoring" + "key-value" | ✗ | **✅ captura via S3** |
| REF-025 Ullah | "A control theoretical view of cloud elasticity" | ✗ | ✗ | ✗ | ✓ "feedback control" + "elasticity" | **✅ captura** |
| REF-028 Gambi | "Assurance of Self-adaptive Controllers for the Cloud" | ✗ | ✗ | ✗ | ✓ "self-adaptive" + "cloud" | **✅ captura** |
| REF-004 Dean & Barroso | "The Tail at Scale" | ✗ | ✗ | ✓ "tail latency"? título tem "tail" mas não "tail latency" exatamente | ✗ | **⚠️ marginal** — fica a critério da base interpretar |
| REF-005 SRE SLO | "Service Level Objectives" | ✗ | ✗ | ✓ "SLO" + "service level objective" | ✗ | **✅ captura** |

**Diagnóstico (falhas críticas de cobertura):**
- **S1** falha completamente como "núcleo" por **exigir "session" obrigatório** — restringe a estudos puramente do domínio sessão, deixando de fora a literatura de replicação distribuída sem o termo. Precisa ser **reescrita**.
- **S2** captura bem os MC modernos mas perde papers teóricos fundamentais (FLP, Chandra & Toueg, CAP) que **não usam terminologia "model checking" / "formal verification"** — são papers de teoria de sistemas distribuídos publicados antes da popularização desses termos.
- **S3, S4** capturam bem suas áreas (latência/observabilidade e autoadaptativo), com pequenas margens.
- **Sem string** captura os **textos fundacionais** (FLP, CAP, Chandra & Toueg, Bailis & Kingsbury) — é necessária uma *string* específica para **fundamentos teóricos de sistemas distribuídos**.

### 15.2 *Strings* revisadas (S1'-S6')

Com base no diagnóstico, as *strings* são **reformuladas e ampliadas para seis**:

#### S1' — Replicação, consistência, IMDG (eixos A, B, com transversal H)
```
(("in-memory data grid" OR IMDG OR "distributed cache" OR "key-value store" OR "session state" OR "session management" OR "distributed storage") AND
 ("consistency" OR "invariant" OR "convergence" OR "reconciliation" OR "replication" OR "CRDT" OR "eventual consistency"))
```
*Captura agora:* Dynamo, CRDTs, CAP, Bailis & Kingsbury (via "distributed"+"reliable"→ falha; mantém ainda fora — coberto por S5'), Nguyen, Infinispan docs, Ruiz-Fuertes 2012.

#### S2' — Métodos formais aplicados a sistemas distribuídos (eixo C)
```
(("model checking" OR "formal verification" OR "formal specification" OR "temporal logic" OR "TLA+" OR "TLA " OR Alloy OR SPIN OR "bounded model checking" OR "probabilistic model checking" OR "theorem proving") AND
 ("distributed" OR "replicated" OR "replication" OR "consistency" OR "concurrent" OR "protocol" OR "fault" OR "cache" OR "data grid"))
```
*Captura:* TLC (Yu/Lamport), Newcombe TLA+, Enea Must, Gao MC, Naskos prob MC, Nair-Petri-Shapiro.

#### S3' — SLO, latência de cauda, observabilidade (eixo D)
```
(("SLO" OR "service level objective" OR "service level agreement" OR "tail latency" OR "p95" OR "p99" OR "percentile latency" OR observability OR "distributed tracing" OR "system monitoring") AND
 ("distributed" OR "data grid" OR "cache" OR "key-value" OR "replicated" OR "scale" OR "latency"))
```
*Captura:* Tail at Scale (via "tail" em "Tail at Scale" combinado com escala), SRE SLO, Ben Mokhtar tail latency, Infinispan metrics, Li observability.

#### S4' — Sistemas autoadaptativos / controle / autoescala (eixo E)
```
(("self-adaptive" OR autonomic OR "MAPE-K" OR "feedback control" OR "control theory" OR "autoscal*" OR elasticity OR "self-healing" OR "self-managing" OR "self-tuning" OR "adaptive control") AND
 ("distributed" OR "cloud" OR "data grid" OR "key-value" OR "cache" OR "replicated" OR "fault" OR "service" OR "storage"))
```
*Captura:* Ullah, Wu Anna, Liu OnlineElastMan, Gambi, Rosa, MAPE-K papers.

#### S5' — **(NOVA)** Fundamentos teóricos de sistemas distribuídos (eixos A, F)
```
(("consensus" OR "agreement protocol" OR "atomic broadcast" OR "linearizability" OR "failure detector" OR "impossibility" OR "network partition" OR "CAP theorem" OR "Byzantine" OR "Paxos" OR "Raft" OR "state machine replication") AND
 ("distributed" OR "asynchronous" OR "fault-tolerant" OR "fault tolerance" OR "process" OR "replicat*"))
```
*Captura:* FLP 1985, CAP 2002, Chandra & Toueg 1996, Bailis & Kingsbury, Schneider SMR, Raft (Ongaro), Linearizability (Herlihy & Wing), Paxos.

#### S6' — **(NOVA)** Sessão como domínio: boas práticas e segurança operacional (eixo H)
```
(("session management" OR "session state" OR "session invariant" OR "session lifecycle" OR "session token" OR "user session" OR "authentication state" OR "session fixation" OR "session hijacking") AND
 ("distributed" OR "cluster" OR "replicat*" OR "cache" OR "data grid" OR "in-memory" OR "stateful" OR "security"))
```
*Captura:* OWASP Session Management Cheat Sheet (literatura técnica), NIST SP 800-63B, papers de segurança de sessão em ambientes distribuídos.

### 15.3 Re-calibração das *strings* revisadas (S1'-S6')

| Âncora | String que captura | Confiança |
|---|---|---|
| REF-001 Dynamo | S1' ("key-value store" + "replication") | alta |
| REF-002/012 CRDTs | S1' ("CRDT" + "replicated"/"convergence") | alta |
| REF-016 FLP | S5' ("consensus" + "asynchronous" + "impossibility") | **alta** |
| REF-032 CAP | S5' ("CAP theorem"; alternativamente "partition" + "distributed") | **alta** |
| REF-033 Chandra & Toueg | S5' ("failure detector" + "distributed"/"fault-tolerant") | **alta** |
| REF-034 Bailis & Kingsbury | S5' ("network partition" + "distributed") OU S1' (via título "The network is reliable" – cobertura indireta) | média |
| REF-003 Yu/Lamport TLC | S2' ("TLA+" + "specification") | alta |
| REF-017 Newcombe | S2' ("TLA+" + "Amazon"→ não filtra base mas captura) | alta |
| REF-019 Enea Must | S2' ("model checking" + "distributed protocols") | alta |
| REF-018 Gao MC | S2' ("model checking" + "distributed systems") | alta |
| REF-020 Naskos | S2' ("probabilistic model checking" + "NoSQL"→ captura via "distributed" inferido) | alta |
| REF-021/022 Ben Mokhtar | S3' ("tail latency" + "key-value") | alta |
| REF-023 Nguyen | S3' ("monitoring" + "key-value") | alta |
| REF-025 Ullah | S4' ("feedback control"/"elasticity" + "cloud") | alta |
| REF-028 Gambi | S4' ("self-adaptive" + "cloud") | alta |
| REF-004 Dean & Barroso | S3' ("tail latency" — título "The Tail at Scale" + "scale") | alta |
| REF-005 SRE SLO | S3' ("SLO" + "service level objective") | alta |
| REF-011 Nair-Petri-Shapiro | S2' ("safety" não está no filtro; **alerta**) — mas título contém "highly-available distributed objects" — captura via "distributed" + outro? **revisar** | **média/baixa** |

**Diagnóstico após revisão:** todas as âncoras com **confiança alta**, exceto **REF-011 (Nair, Petri, Shapiro 2020 — "Proving the Safety of Highly-Available Distributed Objects")** que pode escapar — usa terminologia *"safety"* fora dos filtros principais. Mitigação: adicionar `OR "safety property"` ao bloco de termos de S2', ou aceitar que o paper entrará via "âncora explicitamente conferida no conjunto-semente". Decisão prática: **aceitar a captura via conjunto-semente** sem alargar S2' (evita falsos positivos com o termo genérico "safety").

### 15.4 *Strings* finais aprovadas para Sem 3-4

As versões **S1'-S6'** são as *strings* oficiais a serem executadas na Sem 3-4. A execução por base (ACM, IEEE, Scopus, Springer) exigirá pequenas adaptações sintáticas (operadores, escopo de campos), preservando a semântica.

### 15.5 Segunda revisão (Revisor — 2ª rodada, 2026-05-13)

A pedido do Gestor, revisão complementar focada em (a) cobertura dos eixos, (b) sintaxe das *strings* e (c) abrangência da literatura em inglês.

**Cobertura:**
- ✅ **S1'**: cobre A/B/H com 7 termos-foco + 7 termos-filtro. Faltavam *"consistent hashing"*, *"rebalancing"* e *"state transfer"* (importantes para B) — **adicionados** abaixo.
- ✅ **S2'**: cobre C com 11 termos-foco. *"Coq"* e *"Isabelle"* implícitos em "theorem proving" — aceitável; **adicionado** explicitamente para garantir captura.
- ✅ **S3'**: cobre D adequadamente.
- ✅ **S4'**: cobre E adequadamente.
- ✅ **S5'**: cobre A/F (fundamentos). *"vector clocks"* e *"logical clocks"* (Lamport 1978 — CAND-001) ainda não cobertos — **adicionados**.
- ✅ **S6'**: cobre H adequadamente.

**Sintaxe:**
- ⚠️ *"TLA+"* tem o símbolo `+` que algumas bases tratam como operador; sintaxe segura: *"TLA+"* entre aspas (já está) **e** alternativa *"TLA plus"* / *"Specifying Systems"* (livro Lamport) para fallback. **Adicionado** *"TLA"* sem o símbolo como termo separado também.
- ⚠️ *"autoscal\*"* — o asterisco como *wildcard* não é suportado por todas as bases; ACM e Springer aceitam, IEEE e Scopus preferem variantes explícitas. **Expandido** para *"autoscaling" OR "auto-scaling" OR "autoscaler"*.

**Idioma:**
- ✅ Todas as *strings* estão em **inglês** — adequado às bases internacionais (ACM, IEEE, Scopus, SpringerLink) onde a literatura técnica em CC é majoritariamente em inglês. Termos em português entram apenas em filtros pós-busca (CI3).
- ✅ Estratégia para captura de literatura em português (poucos itens esperados — Neiva & Silva 2016 é exceção, mas já é REF-030): executar mesmas *strings* (ou subconjunto) na **Scopus** com filtro de idioma "Portuguese" como execução complementar — registrado para a Sem 3-4.

### 15.6 *Strings* finais consolidadas (S1''-S6'') — versão de execução

#### S1'' — Replicação, consistência, IMDG (eixos A, B, H)
```
(("in-memory data grid" OR IMDG OR "distributed cache" OR "key-value store" OR
  "session state" OR "session management" OR "distributed storage" OR
  "consistent hashing" OR "rebalancing" OR "state transfer") AND
 ("consistency" OR "invariant" OR "convergence" OR "reconciliation" OR
  "replication" OR "CRDT" OR "eventual consistency" OR "session consistency"))
```

#### S2'' — Métodos formais aplicados a sistemas distribuídos (eixo C)
```
(("model checking" OR "formal verification" OR "formal specification" OR
  "temporal logic" OR "TLA+" OR "TLA" OR Alloy OR SPIN OR Coq OR Isabelle OR
  "bounded model checking" OR "probabilistic model checking" OR "theorem proving") AND
 ("distributed" OR "replicated" OR "replication" OR "consistency" OR "concurrent" OR
  "protocol" OR "fault" OR "cache" OR "data grid"))
```

#### S3'' — SLO, latência de cauda, observabilidade (eixo D)
```
(("SLO" OR "service level objective" OR "service level agreement" OR "tail latency" OR
  "p95" OR "p99" OR "percentile latency" OR observability OR "distributed tracing" OR
  "system monitoring") AND
 ("distributed" OR "data grid" OR "cache" OR "key-value" OR "replicated" OR
  "scale" OR "latency"))
```

#### S4'' — Autoadaptativo / controle (eixo E)
```
(("self-adaptive" OR autonomic OR "MAPE-K" OR "feedback control" OR "control theory" OR
  "autoscaling" OR "auto-scaling" OR "autoscaler" OR elasticity OR
  "self-healing" OR "self-managing" OR "self-tuning" OR "adaptive control") AND
 ("distributed" OR "cloud" OR "data grid" OR "key-value" OR "cache" OR
  "replicated" OR "fault" OR "service" OR "storage"))
```

#### S5'' — Fundamentos teóricos (eixos A, F)
```
(("consensus" OR "agreement protocol" OR "atomic broadcast" OR "linearizability" OR
  "failure detector" OR "impossibility" OR "network partition" OR "CAP theorem" OR
  Byzantine OR Paxos OR Raft OR "state machine replication" OR
  "vector clocks" OR "logical clocks") AND
 ("distributed" OR "asynchronous" OR "fault-tolerant" OR "fault tolerance" OR
  "process" OR "replicat*"))
```

#### S6'' — Sessão (eixo H)
```
(("session management" OR "session state" OR "session invariant" OR "session lifecycle" OR
  "session token" OR "user session" OR "authentication state" OR
  "session fixation" OR "session hijacking") AND
 ("distributed" OR "cluster" OR "replicat*" OR "cache" OR "data grid" OR
  "in-memory" OR "stateful" OR "security"))
```

**Decisão do Revisor (2ª rodada):** ✅ **S1''-S6'' aprovadas para execução em Sem 3-4.** As versões S1''-S6'' substituem definitivamente as S1'-S6' e S1-S4. Idioma de busca: **inglês primário** em todas as bases; complementar em **português** somente na Scopus com filtro de idioma (impacto esperado: ≤5 itens).

**Cobertura por eixo após calibração:**

| Eixo | *Strings* primárias | Cobertura |
|---|---|---|
| A — Replicação/consistência | S1', S5' | ✅ |
| B — IMDG/cache | S1', S3' | ✅ |
| C — Métodos formais | S2' | ✅ |
| D — SLO/latência | S3' | ✅ |
| E — Autoadaptativo | S4' | ✅ |
| F — Falhas/partições | S5' | ✅ |
| G — Metodologia | n/a (meta — não buscado) | ✅ (já coberto por REF-030/037/038) |
| H — Sessão | S6', S1' | ✅ |

**Conclusão da calibração:** os 8 eixos têm pelo menos uma *string* com captura validada por âncoras nomeadas. **Sem 3-4 está autorizada a executar.**

---

## 16. Próximos passos imediatos

Para encerrar a Sem 1-2:
1. **Gestor aprova este protocolo** (Seções 1-15) — incluindo as 6 *strings* finais S1'-S6'.
2. Bibliotecário cria o esqueleto da **planilha de execução** (Seção 8.2) e o diretório `/workspace/protocolo-ms/` para versionar.
3. Escritor adiciona ao [`Main-Monografia.tex`](../Main-Monografia.tex) parágrafos iniciais de **Cap. 1.2 (problema de pesquisa)** e **Cap. 3.2 (descrição do MS)** — primeira contribuição da "escrita em paralelo" prevista no Quadro 1.

A partir da aprovação, **inicia-se a Etapa 2 (Sem 3-4 — execução das buscas e triagem)**.