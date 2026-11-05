# Anotações por fonte e por tópico, ajuda para a escrita

Anotações livres, formato informal, para apoiar a redação e a defesa. Cada bloco resume o que cada fonte traz, com o tópico onde encaixa no texto e referência ao arquivo de texto integral em `/workspace/leituras/` quando há.

---

## Fontes lidas na íntegra (18)

### REF-001, Dynamo (DeCandia et al., SOSP 2007)

Arquivo: `REF-001-Dynamo-2007.txt` (94 KB).

O paper apresenta o Dynamo da Amazon como uma combinação de quatro mecanismos para construir um KV store altamente disponível em produção. O sistema escolhe disponibilidade sobre consistência forte, e essa escolha sustenta a defesa de SLAs do tipo 99.9% de respostas em menos de X ms (eles citam medidas em microssegundos para operações de read e write, com p99.9 sob 300 ms para serviços de larga escala).

Detalhes que valem citar diretamente:

- Vector clocks: cada versão de cada objeto carrega uma lista de pares (nó, contador). Comparação determina se uma versão é ancestral de outra, ou se há conflito. Se conflito, devolve todas as branches; aplicação resolve. Útil para Cap. 2.1 (replicação otimista) e para justificar reconciliação por aplicação.
- Sloppy quorum: requisições atendidas pelos W ou R nós saudáveis mais próximos (não necessariamente os N originais). Diferente do quórum estrito; preserva disponibilidade.
- Hinted handoff: quando um nó destino está fora, escrita vai para um vizinho com hint; quando volta, hint é entregue. Mecanismo prático para tolerar falhas transientes.
- Anti-entropia com árvores de Merkle: comparação eficiente de réplicas para reconciliar divergências de longo prazo em segundo plano.
- Gossip-based membership e failure detection: nada de coordenação central.

Onde encaixa: Cap. 2.1 (sistemas distribuídos), Cap. 2.6 não direto.

### REF-002, CRDTs (Shapiro et al., SSS 2011)

Arquivo: `REF-002-Shapiro-CRDTs-2011.txt` (44 KB).

Formaliza Strong Eventual Consistency (SEC). Tese principal: convergência sem coordenação é possível se as operações satisfizerem propriedades algébricas.

Pontos para citação direta:

- Definição de SEC: réplicas que aplicaram o mesmo conjunto de operações têm o mesmo estado (independente da ordem).
- Duas condições suficientes:
  - State-based (CvRDT): estado forma um join-semilattice; merge é o supremo. Monótono, idempotente.
  - Operation-based (CmRDT): operações concorrentes comutam; canal entrega cada operação pelo menos uma vez.
- Exemplo canônico: contador. G-Counter (só incremento, vetor por réplica). PN-Counter (incremento e decremento, dois vetores).
- Casos avançados no paper: sets, graphs.
- Solução para o "P" do CAP: SEC consegue A e P, dispensando consenso para C forte.

Onde encaixa: Cap. 2.1 (replicação assíncrona com convergência), Cap. 2.6 (sessão — discutir se contadores podem ser modelados como PN-Counter para garantir I3/I6).

### REF-003, Yu/Lamport TLC (CHARME 1999)

Arquivo: `REF-003-Yu-Lamport-TLC-1999.txt` (38 KB).

Descreve o TLC, model checker para TLA+. Aspectos relevantes:

- TLA+ combina TLA (Temporal Logic of Actions), lógica de 1ª ordem completa, ZF set theory.
- Especificação define estados iniciais + relação de transição.
- TLC aceita subconjunto de TLA+ adequado a sistemas reais.
- Trabalho usado para encontrar bugs em protocolo de coerência de cache de multiprocessador Compaq.
- Representação: estados como hashes (fingerprints) para economia de memória.
- Suporte a invariantes e propriedades temporais.
- Erro em invariante → TLC mostra sequência de transições.

Onde encaixa: Cap. 2.3 (model checking), Cap. 3 (justificativa de TLA+/TLC), Cap. 4 (especificação inicial).

### REF-004, Dean & Barroso Tail at Scale (CACM 2013)

Arquivo: `REF-004-Dean-Barroso-TailAtScale-2013.txt` (34 KB).

Argumento central: em sistemas de fan-out (muitos componentes por requisição), a cauda da distribuição de latência domina o tempo total. Exemplo numérico (citar): "5% das requisições são 20 vezes mais lentas que a típica".

Mecanismos de mitigação que eles propõem (todos relevantes para o controlador heurístico do TCC):

- Hedged requests: envia mesma requisição para múltiplas réplicas e usa a primeira resposta.
- Tied requests: envia, dependendo do tempo, para uma segunda réplica e cancela na primeira.
- Cross-server cancellation: cooperação entre servidores para reduzir trabalho duplicado.
- Adaptive workload partitioning.

Onde encaixa: Cap. 2.4 (latência de cauda, SLO).

### REF-005, Google SRE SLO

Arquivo: `REF-005-SRE-SLO.txt` (HTML, 3 KB).

Definições canônicas que devem aparecer no Cap. 2.4:
- SLI = medida quantitativa.
- SLO = alvo do SLI.
- SLA = contrato com consequências.
- Error budget = tolerância de falha por unidade de tempo.

Citação direta útil: "média obscurece detalhes importantes; é possível que a maioria das requisições seja rápida e uma cauda longa seja muito mais lenta".

Onde encaixa: Cap. 2.4.

### REF-006, Infinispan Docs (configuring)

Arquivo: `REF-006-Infinispan-Docs-Configuring.txt` (5 KB).

Conteúdo prático para Cap. 2.2 e Cap. 4:
- 5 cache modes (LOCAL, REPL_SYNC, REPL_ASYNC, DIST_SYNC, DIST_ASYNC, INVALIDATION).
- numOwners controla número de cópias.
- Custo por escrita = 2 * numOwners mensagens.
- "DIST_ASYNC pode perder atualizações" (citar literal).
- Consistent hashing com numSegments fixo, hash factories diferentes.
- Rebalancing causa retries.
- Citação literal "Even with synchronous replication, distributed caches are not linearizable" — IMPORTANTE para justificar o modelo formal não assumir linearizabilidade.

### REF-007, Infinispan xsite

Arquivo: `REF-007-Infinispan-Xsite.txt` (5 KB).

Cross-site replication. Pontos:
- Sync backups: alta latência (RTT entre sites).
- Async backups: sem latência adicional ao cliente. Recomendado para Active/Active.
- Conflict resolution via vector clocks; default = lexicographic compareTo no nome do cluster.
- 3 políticas built-in: ALWAYS_REMOVE, PREFER_NON_NULL, PREFER_NULL.
- State transfer: "overwrites only data that exists on both cluster and does not delete data".
- Para optimal performance: configurar todos os nós como relay nodes.

Onde encaixa: Cap. 2.2 (cross-site), Cap. 4 (decisão de não usar cross-site em TCC-I).

### REF-008, Infinispan Metrics

Arquivo: `REF-008-Infinispan-Metrics.txt` (4 KB).

Conteúdo prático para Cap. 2.4 e Cap. 4:
- Cache manager metrics (cluster size, memory).
- Cache-level (hits/misses, hit ratio, avg latency ms+ns).
- RPC manager (avg replication time, fails).
- Cross-site stats (conflicts, local/remote wins, tombstones).
- JMX ObjectName: org.infinispan:type=Cache,name="${cache}",component=Statistics.
- OpenMetrics/Prometheus expose.
- Tags: ${cache_manager}, ${cache}, ${node}, ${cluster}.

### REF-009, Infinispan Intro

Arquivo: `REF-009-Infinispan-Intro.txt` (2 KB).

"In-memory key/value data store and cache written in Java. Distributes data across clusters for high availability."

Embedded vs Client/Server modes. Use cases: cache, NoSQL, AI vector store, HA state management (Keycloak usa para session clustering — interessante para o TCC!).

### REF-016, FLP 1985 (Fischer, Lynch, Paterson, JACM)

Arquivo: `REF-016-FLP-1985.txt` (20 KB).

Texto seminal de impossibilidade. Modelo: sistema asynchronous, processos com one-bit input register e output register, internal storage. Falha = parar silenciosamente (não-Byzantine).

Theorem 1: "No consensus protocol is totally correct in spite of one fault." Demonstração via lemas sobre configurações bivalentes/univalentes. Constrói execução infinita evitando decisão.

Insight para o texto: o FLP é a razão pela qual sistemas reais usam timeouts heurísticos (não dá para detectar parado vs lento em sistema asynchronous puro). Isso conecta com Chandra-Toueg.

Onde encaixa: Cap. 1.3 (justificativa), Cap. 2.1.

### REF-030, Neiva & Silva 2016 (RT UFJF)

Arquivo: `REF-030-Neiva-Silva-2016.txt` (12 KB).

Guia em PT da revisão sistemática baseado em Kitchenham. Etapas práticas:
1. Definir questões de pesquisa.
2. Definir palavras-chave (PICOC opcional).
3. Definir string de busca (OR para sinônimos, AND entre grupos).
4. Definir bases (Scopus, IEEE Xplore, ScienceDirect, Springer, Web of Science, ACM, Compendex).
5. Refinamento da string testando em base grande.
6. Execução da string nas bases (salvar BIB/CSV).
7. Critérios de inclusão/exclusão.

Onde encaixa: Cap. 3.1 e Apêndice A. Referência principal em PT.

### REF-032, CAP Perspectives (Gilbert & Lynch, 2012)

Arquivo: `REF-032-Gilbert-Lynch-CAP-Perspectives-2012.txt` (44 KB).

Retrospectiva do CAP escrita pelos mesmos autores. Pontos novos:

- CAP é caso particular do princípio geral "impossibility of safety + liveness in unreliable systems".
- Consistência (no sentido CAP) = safety. Disponibilidade = liveness.
- Eles discutem PACELC (Daniel Abadi) sem citar diretamente, mas a ideia "even without partitions, there's still a trade-off between latency and consistency" aparece.
- Estratégias para lidar com a trade-off:
  - Best-effort availability.
  - Best-effort consistency.
  - Trade-offs híbridos.
- Mencionam BASE como alternativa a ACID.

Cuidado: o paper original 2002 prova o teorema formalmente; este 2012 é discussão prática.

### REF-033, Chandra & Toueg 1996 (JACM)

Arquivo: `REF-033-Chandra-Toueg-1996.txt` (132 KB — texto extenso).

Failure detectors não confiáveis. Duas propriedades:
- Completeness: existe instante após o qual todo processo que falha é permanentemente suspeito por algum processo correto.
- Accuracy: existe instante após o qual algum processo correto nunca é suspeito por nenhum correto.

8 classes (2 completeness x 4 accuracy). A classe ◇W (Eventually Weak) é o detector mais fraco capaz de resolver consenso quando há maioria de processos corretos (resultado fundamental).

Implementação prática: incrementar timeout após cada engano. Eventualmente, processos corretos não são mais suspeitos (◇W).

Conexão: o FLP impossibilita consenso em assíncrono puro; Chandra-Toueg mostra que basta uma porção de sincronia eventual (encapsulada no detector) para tornar consenso solucionável.

Onde encaixa: Cap. 2.1 (justificativa do uso de heurísticas de timeout no Infinispan e em qualquer sistema distribuído real).

### REF-035, OWASP Session Mgmt

Arquivo: `REF-035-OWASP-Session-Mgmt-Cheatsheet.txt` (2 KB).

Detalhes operacionais (citáveis):
- 64 bits de entropia. CSPRNG. 16+ hex chars.
- Absolute timeout 4-8h workers, mais curto para high-security.
- Idle 2-5 min high-value, 15-30 min low-risk.
- Renewal timeout: rotaciona ID em intervalos.
- Server-side enforcement (cliente não confiável).
- Logout: invalidate() server + cookie clear client.
- Regenerar ID após qualquer mudança de privilégio (autenticação inclusa).
- Strict mode (só aceitar IDs gerados pela app, nunca os fornecidos).
- Cookie: Secure, HttpOnly, SameSite=Strict, __Host- prefix.
- Reauth após eventos de risco (mudança de senha, IP suspeito).

Onde encaixa: Cap. 2.6 e Cap. 1.3 (justificativa dos invariantes).

### REF-036, NIST SP 800-63B

Arquivo: `REF-036-NIST-SP-800-63B.txt` (2 KB).

Section 7 (gerência de sessão). Janelas de reautenticação por AAL:
- AAL1: 30 dias regardless of activity.
- AAL2: 12h + 30 min idle. Reauth pode ser single factor.
- AAL3: 12h + 15 min idle. Reauth com ambos fatores.

Citação literal nas regras (com SHOULD/SHALL). Bind criptográfico session ↔ authenticator. Canal autenticado e protegido.

Onde encaixa: Cap. 2.6 e Cap. 1.3.

### REF-017, Newcombe AWS TLA+ (CACM 2015)

Arquivo: `REF-017-Newcombe-AWS-TLA.txt` (2 KB).

Anúncio formal do uso de TLA+ na AWS. Pontos:
- Motivação: "testing the code is inadequate" — espaço de estados astronômico.
- Por que TLA+: "simple discrete math, basic set theory and predicates".
- DynamoDB: TLA+ encontrou bug "could lead to losing data", 35 steps shortest counterexample.
- Adoção: 14 equipes por volta de 2015, "useful results in two to three weeks" após aprender.
- Sistemas modelados: S3 (replication), DynamoDB, EBS, gerenciamento de rede.

Onde encaixa: Cap. 1.3 (justificativa) e Cap. 2.3.

### CAND-001, Lamport 1978 Time Clocks (CACM)

Arquivo: `CAND-001-Lamport-Time-Clocks-1978.txt` (42 KB).

Texto seminal. Define a relação "happened-before" (→) como menor ordem parcial irreflexiva satisfazendo:
1. Se a e b são no mesmo processo e a precede b localmente, então a → b.
2. Se a é envio de mensagem m e b é recebimento de m, então a → b.
3. Se a → b e b → c, então a → c (transitividade).

Eventos não relacionados por → são concorrentes (||).

Algoritmo de relógios lógicos para garantir clock condition: se a → b, então C(a) < C(b). Inverso não vale em geral (e por isso happened-before é menor que ordem total).

Algoritmo para totalizar a ordem parcial: timestamp + tiebreak por id de processo.

Onde encaixa: Cap. 2.1 (definição formal). Citação fundamental para qualquer texto que mencione ordens distribuídas.

### REF-041, Lemos et al. ERAD-RS 2025

Arquivo: `REF-041-Lemos-ERAD-RS-2025.txt` (1 KB).

Trabalho do grupo do orientador. GRU + Árvore de Decisão reduz 30% tempo execução vs KerasTuner. Computação sustentável.

Onde encaixa: Cap. 1.1 (parágrafo de sustentabilidade — referência local do grupo).

---

## Tópicos do texto onde cada fonte aparece (mapeamento reverso)

### Cap. 1.1 Contextualização
- NIST + OWASP (estado de sessão)
- Infinispan intro + docs (IMDG)
- Dynamo (replicação assíncrona)
- CRDT (reconciliação)
- Bailis & Kingsbury (rede não confiável) — apenas abstract
- Infinispan xsite (multi-site)
- Turner Nature 2023 (sustentabilidade) — apenas resumo público
- Morán et al. JPDC 2024 (FT HPC energy) — apenas abstract

### Cap. 1.2 Problema
- NIST + OWASP (invariantes do domínio)
- Yu/Lamport TLC (model checking)
- Dean/Barroso Tail (latência cauda)
- SRE SLO

### Cap. 1.3 Justificativa
- Dynamo, CRDT (replicação)
- FLP, CAP, Chandra-Toueg (teóricos)
- Bailis & Kingsbury (empírico) — abstract
- Dean/Barroso + SRE (SLO)
- Yu/Lamport + Newcombe (model checking industrial)
- Armbrust (cloud) — abstract (precisa busca)
- NIST + OWASP (normativo)

### Cap. 2.1 Sistemas distribuídos
- Lamport 1978 (happened-before)
- Ruiz-Fuertes 2012 (modelos de consistência) — abstract
- Liu et al. QEST 2015 (análise quantitativa) — abstract
- Gilbert/Lynch CAP — Perspectives (lido) + original 2002 (não lido)
- FLP 1985 (impossibilidade)
- Chandra-Toueg 1996 (failure detectors)
- Bailis & Kingsbury — abstract
- Dynamo 2007
- Shapiro CRDTs 2011
- Preguiça CRDT verbete 2019 — abstract
- Nair-Petri-Shapiro 2020 — abstract

### Cap. 2.2 IMDG / Infinispan
- Salhi Benchmarking 2018 — abstract
- Infinispan intro
- Infinispan docs (configuring)
- Infinispan xsite
- Infinispan metrics

### Cap. 2.3 Model checking
- Yu/Lamport TLC
- Newcombe AWS TLA+
- Gao MC Incremental 2025 — abstract
- Enea Must 2024 — abstract
- Naskos prob MC 2017 — abstract

### Cap. 2.4 Latência / SLO
- Dean/Barroso Tail 2013
- SRE SLO
- Jaiman TailX 2020 — abstract
- Ben Mokhtar Taming Tail 2021 — abstract
- Ben Mokhtar scheduling 2024 — abstract
- Li Observability 2021 — abstract
- Infinispan metrics

### Cap. 2.5 Autoadaptativos
- Ullah Control Elasticity 2018 — abstract
- Wu Anna Autoscale 2020 — abstract
- Liu OnlineElastMan 2017 — abstract
- Gambi Assurance 2013 — abstract
- Rosa Self-mgmt 2013 — abstract

### Cap. 2.6 Sessão
- NIST SP 800-63B
- OWASP Cheat Sheet
- Armbrust 2010 (cloud externalização) — abstract

### Cap. 2.7 Trabalhos relacionados
- Nguyen Weaker Consistency 2019 — abstract

### Cap. 3 Metodologia
- Petersen Mapping 2008 — abstract (PRECISA ler)
- PRISMA 2020 — abstract (PRECISA ler)
- Neiva & Silva 2016
- FLP (para CE7 critério data)

### Cap. 4 Desenvolvimento
- Infinispan docs (DIST_SYNC, numOwners)
- Infinispan metrics
- Yu/Lamport TLC (justificativa da escolha de ferramenta — alternativa Must em REF-019)

---

## Lista de fontes que VOCÊ (Gestor) precisa baixar

Estas estão na bibliografia oficial mas não consegui texto integral. Sugeri uso restrito (afirmação suportada pelo abstract apenas). Cada uma traria mais profundidade ao Cap. 2 se lida na íntegra:

| Sigla | Autores e título | Acesso |
|---|---|---|
| REF-010 | Ruiz-Fuertes et al., Consistency in Scalable Systems, OTM 2012 LNCS | SpringerLink (CAPES) |
| REF-011 | Nair, Petri, Shapiro, Proving Safety of HA Distributed Objects, ESOP 2020 LNCS | SpringerLink (CAPES) |
| REF-012 | Preguiça, Baquero, Shapiro, CRDTs (Encyclopedia of Big Data Tech) | Springer (CAPES) |
| REF-013 | Liu et al., Quantitative Analysis of Consistency in NoSQL KV Stores, QEST 2015 LNCS | SpringerLink |
| REF-014 | Salhi et al., Benchmarking Cache Systems, TPCTC 2017 LNCS | SpringerLink |
| REF-015 | Jaiman, Ben Mokhtar, Rivière, TailX, DAIS 2020 LNCS | SpringerLink |
| REF-018 | Gao et al., Model Checking Guided Incremental Testing, Proc ACM SE 2025 ISSTA | ACM DL |
| REF-019 | Enea et al., Model Checking Distributed Protocols in Must, OOPSLA 2024 | ACM DL (provavelmente OA) |
| REF-020 | Naskos et al., Cost-aware Horizontal Scaling NoSQL with Probabilistic MC, Cluster Computing 2017 | SpringerLink |
| REF-021 | Ben Mokhtar et al., Taming Tail Latency, Euro-Par 2021 LNCS | SpringerLink |
| REF-022 | Ben Mokhtar et al., scheduling framework, J. Scheduling 2024 | SpringerLink |
| REF-023 | Nguyen et al., Weaker Consistency + Monitoring, JBCS 2019 | Open access (tentei, redirect — vale retentar) |
| REF-024 | Li et al., Observability industrial survey, Empirical SE 2021 | SpringerLink |
| REF-025 | Ullah et al., Control theoretical view of cloud elasticity, Cluster Computing 2018 | SpringerLink |
| REF-026 | Wu, Sreekanti, Hellerstein, Autoscaling Anna, VLDB Journal 2020 | SpringerLink |
| REF-027 | Liu et al., OnlineElastMan, Cluster Computing 2017 | SpringerLink |
| REF-028 | Gambi et al., Assurance of Self-adaptive Controllers, LNCS 7740 | SpringerLink |
| REF-029 | Rosa, Rodrigues, Lopes, Self-mgmt with High-Level Goals, LNCS 7475 | SpringerLink |
| REF-031 | Armbrust et al., A view of cloud computing, CACM 2010 | CACM (alguns abertos), Berkeley TR 2009 |
| REF-032 (original) | Gilbert, Lynch, Brewer's Conjecture, SIGACT 2002 | ACM DL |
| REF-034 | Bailis, Kingsbury, The network is reliable, CACM 2014 | CACM/ACM Queue |
| REF-037 | Petersen et al., Systematic Mapping Studies, EASE 2008 | BCS (open mas server fora) |
| REF-038 | Page et al., PRISMA 2020 statement, BMJ 2021 | BMJ open |
| REF-039 | Turner (Nature Comp Science editorial), Carbon footprint, Nature 2023 | Nature (frequentemente paywall) |
| REF-040 | Morán et al., Energy Saving Opportunities FT HPC, JPDC 2024 | Elsevier (CAPES) |

A leitura desses materiais é parte explícita das próximas semanas no plano para o TCC-II.
