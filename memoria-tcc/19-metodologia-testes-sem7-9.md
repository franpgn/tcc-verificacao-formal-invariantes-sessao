# Metodologia de Testes e Checagem — Embasamento Bibliográfico (Sem 7-9 do TCC-I)

> Entregável da Etapa 4 (Sem 7-8) e início da Etapa 5 (Sem 9) do Quadro 1, conforme [`13-planejamento-tcc1.md`](13-planejamento-tcc1.md): "Ambiente experimental + workload + modelo formal inicial" e início de "MC inicial + baseline com falhas".
>
> Pedido do Gestor (2026-05-25): definir os parâmetros de teste e de checagem com base na bibliografia, do mesmo modo que nas etapas anteriores; consultar trabalhos que testam sistemas similares e a documentação oficial do Infinispan/Red Hat Data Grid; apresentar a análise em forma de tabela.
>
> Agente responsável: Bibliotecário (levantamento) → Escritor (LaTeX) → Revisor (coerência) → Gestor (aprovação).
>
> Status: rascunho do Bibliotecário (2026-05-25).

## 1. Fontes consultadas

### 1.1 Referências oficiais já aprovadas (biblioteca)

| Sigla | Trecho de interesse para a metodologia de teste |
|---|---|
| REF-001 (Dynamo, DeCandia et al. 2007) | Critério de SLO declarado: 99,9% das operações em poucas centenas de milissegundos; medição em produção por 24 h em intervalos de 30 min; razão de carga desbalanceada com tolerância de 15%; redução de cauda por buffer de escrita. |
| REF-004 (Dean & Barroso, 2013) | Fundamento canônico do uso de p95/p99/p99.9; demonstração da amplificação de cauda em fanout: com p99 de 1 s no servidor, 63% dos pedidos cliente em fanout 100 cruzam 1 s. Limiar perceptivo de 100 ms para fluidez. |
| REF-005 (Google SRE SLO) | Articulação SLI/SLO/SLA: SLO típico de latência expresso por percentil em janela móvel. |
| REF-006 (Infinispan Configuring) | Modos DIST_SYNC/DIST_ASYNC; numOwners; consistência limitada de DIST sync; particionamento por consistent hashing; partition handling policies. |
| REF-008 (Infinispan Metrics) | Métricas a coletar por nível (cache manager, cache, RPC, cross-site, transport, lock); endpoints JMX e OpenMetrics. |
| REF-015 (Jaiman, TailX, DAIS 2020) | Cassandra como alvo; YCSB modificado; Zipfian ρ=0,99; 16 nós; payload misto 1 KB (20 M) e 2 MB (100 K) ≈ 41 GB/nó; fator de replicação 3; 1 M-10 M operações por medição; 5 repetições por cenário; carga em 75 % do pico; percentis median, p90, p95, p99, p99.9. |
| REF-021 (Ben Mokhtar et al., 2021) | Formalização do problema de cauda em KV via max-flow e stretch; cita gap de ordens de magnitude entre p50 e p99. |
| REF-023 (Nguyen et al., 2019) | Combinação de consistência fraca com monitoramento e recuperação; reforça M2 (taxa de violação por invariante) como métrica primária pouco usada. |
| REF-027 (Liu, OnlineElastMan, 2017) | Necessidade de "warm-up sufficient" antes da janela de medição; controle baseado em percentil de latência e composição de workload. |
| REF-003 (Yu & Lamport, TLC, 1999) e REF-017 (Newcombe, AWS TLA, 2014) | Padrão de model checking com cardinalidades pequenas (2 a 3 instâncias por sort) suficientes para encontrar violações em protocolos industriais. |
| REF-018 (Gao et al., 2025) | Estratégia de model checking incremental: começar com a configuração mínima e expandir após a primeira passagem limpa. |
| REF-004 + REF-005 | Combinação de percentil e janela de SLO. |

### 1.2 Fontes externas consultadas (status pós-Decisão 013, 2026-05-25)

Cinco candidatas foram levantadas para ancorar a metodologia de teste; o Gestor aprovou a recomendação do Bibliotecário (duas promoções, três dispensas).

| Chave Bib | Fonte | Conteúdo extraído | Status pós-Decisão 013 |
|---|---|---|---|
| `infinispan_tuning` | Infinispan, "Performance considerations and tuning guidelines" (stable). https://infinispan.org/docs/stable/titles/tuning/tuning.html | JVM 50 % do heap para dados (cache puro) ou 33 % (cache + processamento); overhead aproximado de 200 B por entry on-heap, 60 B off-heap; janelas TCP recomendadas (wmem_max 640 KB, rmem_max 25 MB); MTU 1500 default, jumbo até 9000; numSegments default 256, recomendado 20×N para N > 20 nós; thread pools OOB/incoming dimensionados pelo número de atualizações concorrentes esperadas. | ✅ **PROMOVIDA — REF-045** |
| `cooper_ycsb_2010` | Cooper, B. F.; Silberstein, A.; Tam, E.; Ramakrishnan, R.; Sears, R. (2010). Benchmarking Cloud Serving Systems with YCSB. SoCC '10. DOI 10.1145/1807128.1807152. | Definição dos workloads core A-F: A (50/50 leitura/escrita, exemplo declarado "session store recording recent actions"), B (95/5), C (100 % leitura), D (read latest), E (range), F (read-modify-write); fases de load e run separadas; throughput-alvo por -target; reporte de avg/min/max/p95/p99 por tipo de operação. | ✅ **PROMOVIDA — REF-046** |
| `keycloak_benchmark_timeouts` | Keycloak Benchmark, "Failure detection timeouts" (acesso 2026-05-25). https://www.keycloak.org/keycloak-benchmark/kubernetes-guide/latest/running/timeout_tuning | Valores default em produção: FD_ALL3 heartbeat 8000 ms, timeout 40000 ms; remote timeout intra-cluster 15000 ms; lock acquisition 10000 ms; cross-site heartbeat 10000 ms, timeout 30000 ms. | ⛔ **DISPENSADA** — não revisada por pares; valores cobertos por `infinispan_docs_stable` (REF-006), citada em T16. |
| `ispnperftest` | Bela Ban, jgroups-extras/IspnPerfTest (GitHub). https://github.com/jgroups-extras/IspnPerfTest | Benchmark de referência para Infinispan/JGroups: 50 000 operações; mistura 80 % get / 20 % put; payload default de 1 000 B; configurável por número de chaves. | ⛔ **DISPENSADA** — repositório informal; parâmetros cobertos por REF-046 (YCSB) e REF-015 (TailX). |
| `aosa_infinispan` | Manik Surtani, "The Performance of Open Source Software: Infinispan". In: Bartholomew, A. (Org.), The Performance of Open Source Applications. https://aosabook.org/en/posa/infinispan.html | Discussão de bottlenecks em data grids: serialização ≈ 20 % do tempo de processamento; pausas de GC; tamanho de thread pool versus updates concorrentes; RadarGun como ferramenta de comparação. | ⛔ **DISPENSADA** — capítulo fora de venue acadêmico; conteúdo redundante com REF-045 e REF-024. |

## 2. Tabela analítica — parâmetros de teste e justificativa

A tabela abaixo confronta cada parâmetro de teste, a faixa de valores observada na literatura e na documentação, e o valor adotado no protótipo deste TCC-I. A última coluna explicita o motivo do recorte e remete às pendências quando há decisão pendente do Gestor.

| # | Parâmetro de teste | Faixa observada (literatura ou documentação) | Fonte principal | Valor adotado (TCC-I) | Justificativa do recorte |
|---|---|---|---|---|---|
| T1 | Modo de replicação | DIST_SYNC, DIST_ASYNC, REPL_SYNC, REPL_ASYNC; sync recomendado quando consistência > vazão | REF-006 | DIST_ASYNC (cenário-alvo) e DIST_SYNC (controle) | objeto de estudo é replicação assíncrona; manter SYNC como controle para evidenciar contribuição |
| T2 | Número de nós no cluster | 3 (mínimo para tolerância a 1 falha), 16 em TailX, 30 em Dynamo strategy 3 | REF-015; REF-001 | 3 nós | escopo TCC-I (Decisão 011); reduz custo e mantém tolerância mínima |
| T3 | numOwners | default 2; > 2 melhora durabilidade às custas de desempenho | infinispan_tuning; REF-006 | 2 | default da documentação, suficiente para tolerar 1 falha em cluster de 3 |
| T4 | Particionamento (numSegments) | default 256; 20×N para N > 20 | infinispan_tuning | 256 | dentro do faixa default; 3 nós não justifica expansão |
| T5 | Tamanho do payload | 1 KB (IspnPerfTest e small TailX); até 2 MB (TailX large); REF-004 não fixa | ispnperftest; REF-015 | 1 KB fixo | representa tamanho típico de objeto de sessão (token, atributos, contador); reduz ruído de GC |
| T6 | Distribuição de chaves | Zipfian ρ=0,99 (Cassandra default; TailX); uniforme; latest (YCSB-D) | REF-015; ycsb_cooper_2010 | Zipfian ρ=0,99 | exposição da cauda; consistente com cargas reais de sessão; igual a TailX para comparação |
| T7 | Mistura de operações | A 50/50, B 95/5, C 100R, F leitura-modifica-escrita | ycsb_cooper_2010 | Cenário S1 (50/50, derivado do YCSB-A — declarado pelo autor de YCSB como "session store") + Cenário S2 (95/5, leitura predominante) | YCSB-A é declarado explicitamente como modelo de session store; S2 evidencia comportamento de Validate dominante |
| T8 | Cardinalidade do espaço de chaves | 50 000 (IspnPerfTest); 20 M small + 100 K large (TailX); 100 M (exemplo do YCSB) | ispnperftest; REF-015 | 100 000 chaves | suficiente para distribuição Zipfian formar cauda; cabe em heap modesto (3 nós × 4 GB) |
| T9 | Operações por execução | 50 000 (IspnPerfTest); 1 M-10 M (TailX) | REF-015; ispnperftest | 1 000 000 ops por execução | suficiente para p99.9 estável; alinha com lower bound de TailX |
| T10 | Repetições por cenário | 5 (TailX); ≥ 30 sugerido no recorte preliminar do TCC | REF-015 | 30 repetições | conservador em relação ao mínimo TailX; aceitável estatisticamente para Mann-Whitney/Wilcoxon (pendente CAND-020 — Wohlin, P-011) |
| T11 | Warm-up | "sufficient warm-up" (Liu 2017); fase de load separada da fase de run (YCSB) | REF-027; ycsb_cooper_2010 | 60 s ou 10 % do total da execução (o que for maior); descartar primeiros 5 % das amostras | tempo para JIT, allocators e cache do JVM estabilizarem; descarte alinhado à prática de TailBench |
| T12 | Carga (throughput-alvo) | 75 % do pico (TailX) | REF-015 | 75 % do throughput obtido em piloto sem falhas | evita saturação artificial; expõe variabilidade de cauda |
| T13 | Percentis reportados | p50, p95, p99 (YCSB e doc); p99.9 (Dynamo, TailX); p99.99 (Dean-Barroso) | ycsb_cooper_2010; REF-001; REF-004; REF-015 | p50, p95, p99 obrigatórios; p99.9 quando volume permitir (com 1 M ops, p99.9 = 1 000 amostras) | atende padrão da literatura; p99.9 é o limite estatístico fiável com o volume adotado |
| T14 | Duração total por cenário | 10 min a 30 min em estudos derivados de YCSB | ycsb_cooper_2010 | 10 min por execução; 30 min para cenários com falha (F1 e F3) | tempo suficiente para incluir janela de falha (40 s de FD3) e estabilização |
| T15 | MTU | 1500 default; jumbo até 9000 | infinispan_tuning | 1500 | infraestrutura de teste em containers Podman; jumbo não é controlado pelo experimento |
| T16 | Detecção de falha (FD) | FD_ALL3 heartbeat 8000 ms, timeout 40000 ms (Keycloak) | keycloak_benchmark_timeouts | manter defaults do Infinispan | reproduzir comportamento de produção; janela de 40 s define a duração mínima dos cenários com F1 |
| T17 | Falha F1 (crash silencioso) | Crash + reentrada com state transfer (Infinispan default) | REF-006 | 1 nó, duração 60 s, reentrada com state transfer | dentro do FD3 timeout; expõe convergência (I5, I6) |
| T18 | Falha F3 (atraso/jitter) | atraso de centenas de ms; lognormal | REF-021 | +50 ms p99 lognormal aplicado via `tc netem` | ordem de magnitude esperada em rede inter-AZ; valor calibrado em piloto |
| T19 | Métricas a coletar | latência, vazão, hit ratio, RPC replication time, state transfer, conflicts (xsite), thread pool saturation | REF-008 | M1 (latência), M2 (violação), M3 (recuperação), M4 (convergência), M5 (erro), M6 (CPU/heap), M7 (RPC/state transfer) | mapeia 1:1 com os medidores expostos por Infinispan Metrics |
| T20 | Ferramenta de coleta de métricas | JMX; OpenMetrics/Prometheus | REF-008 | OpenMetrics endpoint do servidor Infinispan; Prometheus opcional | padrão atual da documentação; reduz acoplamento ao JMX legacy |
| T21 | Espaço de estados (TLC) | Cardinalidades pequenas (2-3) em verificações de protocolos industriais | REF-003; REF-017 | Identidades 2-3; sessões 2-3; réplicas 2-3 | espaço suficiente para entrelaçar concorrência; padrão de model checking incremental (REF-018) |
| T22 | Análise estatística | Mann-Whitney/Wilcoxon + intervalo de confiança bootstrap | (pendente CAND-020 Wohlin) | Mann-Whitney por percentil; bootstrap 10 000 amostras para IC 95 % | testes não-paramétricos por causa de assimetria das distribuições de latência |

## 3. Resumo da racional metodológica

A escolha do conjunto de parâmetros segue três princípios.

**Compatibilidade com a literatura comparável**: tamanho de payload, distribuição de chaves e percentis reportados reproduzem o setup de REF-015 (TailX, Cassandra) e do guia YCSB. Essa compatibilidade permite contextualizar os resultados deste TCC dentro da faixa observada em estudos análogos, ainda que o objeto (data grid Infinispan, ênfase em invariantes de sessão) seja distinto.

**Aderência à documentação oficial do Infinispan**: número de proprietários, número de segmentos, modos de cache, MTU e detecção de falhas seguem os valores recomendados pelo guia de configuração e pelo guia de performance tuning. O experimento mede o comportamento do Infinispan tal como é configurado em produção típica, não em uma configuração afinada para o experimento.

**Escopo proporcional ao TCC-I**: cluster de três nós, dois cenários de falha (F1 e F3), Decisão 011 ponto 11; volume de 1 M operações por execução e 30 repetições, suficientes para estabilizar p99 e p99.9 e para análise não-paramétrica entre cenários, sem inflar o tempo total além do disponível em Sem 9-10.

## 4. Ligação com o texto da monografia

| Local da monografia | Conteúdo derivado desta análise |
|---|---|
| Capítulo 3 (Metodologia) — frente experimental | Tabela com a totalidade dos parâmetros T1-T22 e a coluna de justificativa |
| Capítulo 4 (Desenvolvimento) §4.3 (plano de execução) | Adoção dos valores T9-T14 e T17-T18 na descrição do plano de Sem 9-10 |
| Capítulo 4 §4.4 (estado dos artefatos) | Configuração concreta do `podman-compose.yml` (T1-T4, T15-T16) |
| Apêndice A (Protocolo do MS) | A própria tabela analítica entra como anexo metodológico após aprovação |

## 5. Pendências derivadas

- ~~**P-025**~~: ✅ **RESOLVIDA** (Decisão 013, 2026-05-25) — 2 promoções (REF-045 `infinispan_tuning`, REF-046 `cooper_ycsb_2010`) e 3 dispensas (Keycloak, IspnPerfTest, AOSA).
- **P-011** (em andamento): plano estatístico depende da aprovação de CAND-020 (Wohlin, *Experimentation in SE*). Enquanto não há resolução, mantém-se Mann-Whitney/Wilcoxon + bootstrap como ferramenta default.
- **P-024** (em andamento): caçada final de REF-019 (Enea/Must) e REF-026 (Anna) mantém-se aberta; a inclusão eventual de REF-019 reabre a justificativa de TLA+ versus Must em §4.2 (já tratada parcialmente em Decisão 012).
