# Revisão Dirigida — TCC-1 Semanas 3-4 (replicação assíncrona, partições, failover, reconciliação) ⛔ OBSOLETO

> ## ⛔ DOCUMENTO OBSOLETO (Decisão 010, 2026-05-12)
>
> Este documento foi produzido em 2026-05-12 com base numa **versão desatualizada** de `Main-PPEC.tex` que descrevia Sem 3-4 como "revisão dirigida narrativa". A **Proposta atualizada** (versão correta) declara Sem 3-4 do TCC-I como **"executar a busca e a triagem inicial e estabilizar critérios de inclusão/exclusão"** dentro do **mapeamento sistemático** (Decisão 010).
>
> **Status:** **NÃO USAR como entregável de Sem 3-4.** Conteúdo preservado como **eventual rascunho para sub-seções do Cap. 2** (Fundamentação Teórica) — pode ser reaproveitado quando o mapeamento sistemático tiver concluído e o Cap. 2 estiver sendo redigido, mas as escolhas de cobertura, ordem e ênfase precisarão ser revistas conforme os achados do mapeamento.
>
> **Próximo entregável real de Sem 3-4 de TCC-I:** **execução** do protocolo de mapeamento sistemático definido em Sem 1-2 — busca nas bases, triagem por título/resumo, estabilização dos critérios. Não há "documento de revisão dirigida" — há um **registro de execução do mapeamento** (a produzir no momento certo).
>
> **Conteúdo arquivado abaixo** (preservado para reuso seletivo no Cap. 2 quando aplicável):
>
> ---

---

# Revisão Dirigida — TCC-1 Semanas 3-4 (replicação assíncrona, partições, failover, reconciliação)

> Entregável das **Semanas 3-4** do cronograma (`Main-PPEC.tex`, linhas 78-80): *"Revisão dirigida: replicação assíncrona, partições, failover e reconciliação; consolidação das referências mínimas e do vocabulário técnico do texto."*
>
> Esta revisão é **narrativa** (Decisão 003 — não-sistemática) e usa apenas as referências aprovadas em [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md). Resulta em **insumo para o Cap. 2** (seções 2.1, 2.3, 2.4) e contribuições marginais ao Cap. 2.5.
>
> **Status:** draft do **Escritor** (2026-05-12), pendente de validação pelo Bibliotecário (consistência de citações), pelo Revisor (coerência) e aprovação do **Gestor**.

---

## 1. Replicação síncrona e replicação assíncrona

Em sistemas distribuídos com estado, a **replicação** dispõe cópias do estado em mais de um nó com o objetivo de aumentar disponibilidade e/ou desempenho de leitura. Há duas famílias principais. Na **replicação síncrona**, uma atualização só é confirmada ao cliente após ter sido aplicada (ou pelo menos registrada de modo durável) num quórum de réplicas; o protocolo é tipicamente coordenado e requer consenso entre as réplicas envolvidas. Na **replicação assíncrona**, uma atualização é confirmada após aplicação local no nó que a recebe e propagada às demais réplicas em segundo plano; trata-se de uma estratégia *otimista*, que troca garantias de consistência por menor latência percebida e maior disponibilidade sob falhas \citeonline{decandia_dynamo_2007}.

A replicação assíncrona introduz a possibilidade de **estados defasados** entre réplicas e de **reordenação efetiva** dos eventos observados por nós distintos. A reconciliação posterior, quando estados divergentes encontram-se, pode exigir resolução explícita de conflitos ou, alternativamente, ser organizada de modo a garantir **convergência sem coordenação**, conforme o esquema dos *Conflict-free Replicated Data Types* (CRDTs) \citeonline{shapiro_crdt_2011, preguica_crdt_encyclopedia_2019}.

A decisão entre estratégias é restringida por um resultado teórico: nenhum protocolo de consenso *totalmente assíncrono* tolera a falha silenciosa de um único processo \citeonline{fischer_lynch_paterson_flp_1985}. Esse resultado de impossibilidade — conhecido como FLP — é uma das justificativas para a adoção de replicação assíncrona/otimista em sistemas que precisam permanecer disponíveis sob falhas, mesmo ao custo de consistência forte.

## 2. Modelos de consistência

Sobre a replicação, definem-se **modelos de consistência** que estabelecem garantias observáveis pelas aplicações. Modelos fortes — como a *linearizabilidade* — exigem que o sistema se comporte como se houvesse uma única cópia centralizada; modelos relaxados, como a *consistência eventual*, garantem apenas que, na ausência de novas atualizações, réplicas convergem para um mesmo estado \citeonline{ruizfuertes_consistency_2012}.

O **teorema CAP** sustenta formalmente o *trade-off* central: nenhum sistema distribuído pode oferecer simultaneamente *Consistency*, *Availability* e *Partition tolerance* — em presença de partição, o sistema escolhe entre as duas primeiras \citeonline{gilbert_lynch_cap_2002}. Análises quantitativas posteriores em *NoSQL key-value stores* mostraram que, mesmo sem partições, persiste um *trade-off* entre latência e consistência: este é o ponto de \textit{PACELC}, registrado empiricamente em diversos *stores* \citeonline{liu_quantitative_consistency_2015}.

A escolha por **consistência eventual** ou por variantes de consistência de sessão (*read-your-writes*, *monotonic reads*) é especialmente comum em *in-memory data grids* (IMDGs), nos quais o ganho de latência é objetivo de primeira ordem \citeonline{decandia_dynamo_2007, ruizfuertes_consistency_2012}.

## 3. Falhas e partições de rede

O modelo de falhas relevante a este trabalho considera três classes principais. (i) **Crash silencioso** de um nó: o processo deixa de responder, sem produzir mensagens incorretas. (ii) **Partição** de rede: o cluster fica fragmentado em sub-conjuntos que não se comunicam até a partição ser reparada. (iii) **Atrasos e jitter** sustentados no canal: as mensagens são entregues, mas com variabilidade que pode produzir efeitos similares a uma partição transitória.

Embora intuitivamente classificadas como anômalas, partições não são raras na prática: um levantamento extenso documenta partições, perdas e atrasos significativos em sistemas de produção de grande escala — incluindo provedores de nuvem maduros — sustentando que projetar para a presença de partições é requisito operacional, não exceção \citeonline{bailis_kingsbury_network_2014}.

Para que um sistema reaja a falhas, é preciso *detectá-las*. A teoria dos **detectores de falha não confiáveis** estabelece classes (completeness × accuracy) com diferentes garantias e prova que o consenso é solúvel num sistema assíncrono enriquecido com pelo menos um detector da classe ◇W (\textit{eventually weak}) \citeonline{chandra_toueg_failure_detectors_1996}. Em sistemas reais, detectores são aproximados por *timeouts* heurísticos, com riscos de falsos positivos (suspeitar de nó vivo) e falsos negativos (não suspeitar de nó morto) — distinção operacionalmente crítica para evitar *failovers* desnecessários e *split-brains*.

## 4. Failover, reconciliação e *state transfer*

Em sistemas como o Infinispan, o **failover** é tipicamente realizado pela redistribuição de \textit{owners} das chaves para réplicas vivas, por meio de **rebalanceamento** baseado em *consistent hashing*; quando novos nós entram, ocorre **state transfer** para popular o estado nas réplicas adicionadas \citeonline{infinispan_docs_stable}. Em cenários geo-distribuídos, há a **replicação entre sites** (*xsite*), com modos síncrono ou assíncrono entre clusters separados \citeonline{infinispan_xsite}; em TCC-1, o escopo limita-se a um único cluster (Decisão 008), e o xsite entra apenas como referência conceitual.

A **reconciliação** após uma partição ou crash com restart pode adotar políticas distintas:
- **Resolução por última-escrita-vence** (*last-write-wins*, LWW), baseada em *timestamps* (físicos ou lógicos): simples, mas perde atualizações em caso de relógios divergentes.
- **Resolução por aplicação**: o sistema entrega valores conflitantes e a aplicação decide, como em Dynamo \citeonline{decandia_dynamo_2007}.
- **Resolução por desenho convergente**: o estado é especificado de modo que toda fusão de réplicas converge, sem decisão arbitrária — caso dos CRDTs \citeonline{shapiro_crdt_2011, preguica_crdt_encyclopedia_2019}. Trabalhos recentes mostram técnicas para **provar formalmente o *safety*** de objetos distribuídos altamente disponíveis, generalizando o raciocínio que sustenta os CRDTs e oferecendo um caminho viável de verificação para estados de sessão \citeonline{nair_safety_distributed_2020}.

Pesquisa recente sobre KV stores mostra que **combinar consistência relaxada com monitoramento e *recovery* específicos por aplicação** pode reduzir custos de coordenação preservando metas operacionais — aproxima-se diretamente do desenho experimental do presente trabalho \citeonline{nguyen_weaker_consistency_2019}.

## 5. Implicações para o estudo de caso (sessão)

Combinando os pontos anteriores, o estudo de caso "estado de sessão" herda riscos previsíveis:
- Sob partição, uma reconciliação por LWW pode trazer de volta o estado `Valid` após um `Logout` confirmado se relógios entre nós discordarem, violando **I1** ([`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md)).
- A propagação assíncrona pode levar um nó a observar `IncrementFailure` aplicado fora de ordem, abrindo janela para `Validate` retornar `VALID` sob bloqueio em outra réplica — risco direto a **I2**.
- A reconciliação por aplicação requer um critério de fusão coerente para os contadores; CRDTs do tipo G-Counter / PN-Counter (ou variantes acoplados a `Reset`) são candidatos naturais a um refinamento conceitual do protocolo, mesmo que o TCC não chegue a implementá-los.

Especificações formais — discutidas em maior profundidade na revisão de Semanas 5-6 — atacam esses riscos antecipadamente, escrutinando invariantes via *model checking* \citeonline{yu_lamport_tla_modelchecking_1999, newcombe_aws_tla_2014, enea_must_2024, gao_mc_incremental_2025}; bons sinais empíricos vêm de trabalhos que aplicaram *probabilistic model checking* a decisões de escalonamento de *stores* NoSQL \citeonline{naskos_probabilistic_mc_2017}.

## 6. Referências mínimas consolidadas (escopo Semanas 3-4)

| Tema | Referência aprovada | Papel |
|---|---|---|
| Replicação assíncrona / otimista | REF-001 (Dynamo) | base do *trade-off* disponibilidade × consistência |
| Convergência sem coordenação | REF-002 (CRDT seminal), REF-012 (verbete enciclopédico) | *Strong Eventual Consistency* |
| Impossibilidade FLP | REF-016 (Fischer et al. 1985) | fundamento teórico do consenso assíncrono |
| Modelos de consistência | REF-010 (Ruiz-Fuertes 2012), REF-013 (Liu et al. 2015) | revisão e análise quantitativa |
| Teorema CAP | REF-032 (Gilbert & Lynch 2002) | *trade-off* sob partição |
| Detectores de falha | REF-033 (Chandra & Toueg 1996) | classes ◇W, ◇S; falsos positivos/negativos |
| Partições na prática | REF-034 (Bailis & Kingsbury 2014) | evidência empírica |
| Safety de objetos distribuídos | REF-011 (Nair, Petri, Shapiro 2020) | método de prova para objetos HA |
| Cluster / Rebalancing / xsite | REF-006, REF-007 (Infinispan docs) | plataforma experimental |
| Trabalho relacionado direto | REF-023 (Nguyen et al. 2019) | consistência fraca + monitoramento + recovery |

## 7. Vocabulário técnico consolidado (a incorporar em [`08-glossario.md`](08-glossario.md))

- **Replicação assíncrona / otimista** — propagação de atualizações em segundo plano, sem confirmação prévia entre réplicas.
- **Consistência eventual** — garantia de que, na ausência de novas atualizações, réplicas convergem.
- **Strong Eventual Consistency (SEC)** — variante em que a convergência é determinística pelo conjunto de operações aplicadas (CRDTs).
- **Quorum / sloppy quorum / hinted handoff** — termos do espaço de Dynamo \citeonline{decandia_dynamo_2007}.
- **Partição** — fragmentação do *cluster* em sub-conjuntos sem comunicação; *split-brain* é o efeito de operação concorrente nos dois lados.
- **Failover** — redistribuição de responsabilidades quando um nó cai ou parte do cluster fica isolada.
- **Rebalancing / State transfer** — redistribuição de chaves e transferência de estado quando a topologia muda \citeonline{infinispan_docs_stable}.
- **Detector de falha (◇W, ◇S, P, S)** — classes definidas por completeness × accuracy \citeonline{chandra_toueg_failure_detectors_1996}.
- **CRDT (CvRDT, CmRDT, join-semilattice)** — tipos de dados convergentes \citeonline{shapiro_crdt_2011}.
- **PACELC** — extensão de CAP que considera latência sob operação normal.
- **CAP** — *Consistency, Availability, Partition tolerance* \citeonline{gilbert_lynch_cap_2002}.

## 8. Lacunas a fechar nas Semanas 5-6

Esta revisão **não cobriu** (por escopo da semana):
- IMDG e particionamento (REF-006, REF-009, CAND-008) — eixo B.
- *Rebalancing* / *state transfer* em profundidade (REF-006, REF-007) — eixo B.
- Observabilidade e SLO (REF-004, REF-005, REF-008, REF-024) — eixo D.
- Mini-revisão estruturada (Decisão 003) — protocolo formal, definir temas com o Gestor (sub-pendência P-004a).

Estes pontos compõem o entregável das Semanas 5-6.

---

## Revisão (1ª rodada — Revisor, 2026-05-12)

- ✅ Todas as citações apontam para chaves existentes em `bibliografia.bib` (`decandia_dynamo_2007`, `shapiro_crdt_2011`, `preguica_crdt_encyclopedia_2019`, `fischer_lynch_paterson_flp_1985`, `ruizfuertes_consistency_2012`, `liu_quantitative_consistency_2015`, `gilbert_lynch_cap_2002`, `chandra_toueg_failure_detectors_1996`, `bailis_kingsbury_network_2014`, `infinispan_docs_stable`, `infinispan_xsite`, `nair_safety_distributed_2020`, `nguyen_weaker_consistency_2019`, `yu_lamport_tla_modelchecking_1999`, `newcombe_aws_tla_2014`, `enea_must_2024`, `gao_mc_incremental_2025`, `naskos_probabilistic_mc_2017`).
- ✅ A revisão é narrativa (não-sistemática), consistente com Decisão 003; menciona explicitamente que o protocolo estruturado leve (mini-revisão) virá nas Semanas 5-6.
- ✅ Liga texto aos invariantes (Seção 5) — bom para fundamentação da Justificativa (P-002) do Cap. 1.
- ⚠️ Seção 4 menciona "G-Counter / PN-Counter" mas o trabalho não as cita formalmente — sugiro inserir uma referência via REF-002/012, ou rebaixar o nível de detalhe (deixar como "estruturas comutativas para contadores").
- ⚠️ Seção 2 menciona PACELC como *extensão* de CAP, mas a literatura atribui a Daniel Abadi (2012, *Computer* / IEEE) — atualmente não temos PACELC como REF aprovada; considerar manter como "extensão prática do CAP" sem citar autoria específica, **ou** abrir candidata futura.

**Status:** **Aprovado pelo Revisor**, pendente de aprovação do Gestor.