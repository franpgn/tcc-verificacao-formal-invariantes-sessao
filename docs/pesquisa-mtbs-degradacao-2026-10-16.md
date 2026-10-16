# Pesquisa: MTBS e frequência de degradação, para o modelo TLA+ e para o experimento (2026-10-16)

Autor: Bibliotecário. Branch `docs/pesquisa-mtbs-degradacao-2026-10-16` (a partir de `feature/etapa-formal-integracao`). Sem push. Nenhuma alteração em `bibliografia.bib` nem em `tcc-escrita/memoria-tcc/`: as fontes novas abaixo são todas **CANDIDATAS** e só viram citáveis com aprovação do Gestor.

Convenção de verificação usada na coluna "Status":

- **verificado na fonte**: o texto integral foi baixado e lido nesta sessão (PDF, código-fonte ou página), e o trecho citado foi conferido literalmente.
- **secundária**: visto só em resumo de buscador, snippet ou página que cita a fonte; não usar como número em texto do TCC sem nova conferência.
- **local**: texto já arquivado em `leituras/` ou relatório do próprio projeto.

## 1. Resumo

1. **"MTBS" não é termo padrão.** Busca nesta sessão não achou "MTBS" como acrônimo estabelecido. O mais próximo é **MTBSI** (Mean Time Between Service Incidents), definido em glossário de contratos e em relatório BMC como o tempo médio entre o início de dois incidentes de serviço consecutivos (secundária, ver §3.6). Na literatura de redes de datacenter o termo usado é **MTBF** (Meza et al. 2018, fibras e nós de borda) e **MTBI** (Meza et al. 2018, incidentes por tipo de dispositivo); Schroeder & Gibson falam em "time between failures" (interchegada de falhas). Recomendação: adotar **MTBI** ou "tempo médio entre eventos de degradação" no TCC e explicar a escolha, em vez de "MTBS", que o leitor não vai achar em fonte nenhuma.
2. **A literatura dá faixas muito largas, nunca um número único.** O tempo médio entre falhas de enlace/nó de rede vai de minutos (balanceadores de carga, mediana 8,6 min) a meses (nós de borda do Facebook, mediana 1710 h), passando por cerca de uma semana por enlace (Gill et al. 2011) e 20,5 falhas por enlace-ano na mediana da rede de clientes do CENIC (Turner et al. 2010). Não existe "a taxa" de partição; depende da camada, do ambiente e da definição de falha.
3. **A interchegada de falhas não é exponencial nem periódica.** Schroeder & Gibson ajustam Weibull com forma 0,7 a 0,78 (taxa de risco decrescente, falhas em rajada); Turner et al. definem *flapping* (duas ou mais quedas com menos de 10 min entre si) e o chamam de caso comum. Isso tem consequência direta para o desenho: "a cada N passos" é uma simplificação, e a rajada é o cenário mais severo.
4. **Não há dado publicado de frequência de split/partição específico do Infinispan.** Nem a documentação do Infinispan, nem a do JGroups, nem a página do Keycloak sobre timeouts dizem com que frequência splits ocorrem. O que existe é a **configuração** de detecção e fusão (FD_ALL3 `timeout=40000`/`interval=8000`, `VERIFY_SUSPECT2 timeout=1000`, `MERGE3 min=10000/max=30000`), e as **medições do próprio projeto** (t_split de 39,6 a 46,4 s e t_merge de 45 a 48 s, com um valor atípico de 188 s).
5. **Precedentes de verificação com falhas limitadas.** A prática dominante em TLA+/TLC é impor um teto finito por *state constraint* sobre contadores (termo, tamanho de log, versão de configuração) e escolher instâncias pequenas; Cosmos DB verifica propriedade "dado qualquer um evento de falha". Não achei nenhum precedente publicado que **derive** o limite do modelo a partir de MTBF; o limite é escolhido por tratabilidade e por hipótese de escopo pequeno. Isso é uma **lacuna da literatura**, e o TCC pode apresentar a tradução MTBF para limite como contribuição de método, rotulada como tal.
6. **Argumento central (inferência)**: o modelo com teto k de eventos e posicionamento livre **contém** todo comportamento de um esquema periódico com no máximo k eventos na janela. Logo, introduzir periodicidade "a cada N passos" **não encontra violação nova**; só poda estados. Ver §6.

## 2. O que o projeto já tem (bibliografia e memória locais)

| Item | Onde | O que sustenta | Status |
|---|---|---|---|
| REF-034 Bailis & Kingsbury, *The network is reliable*, CACM 57(9), 2014 (`bailis_kingsbury_network_2014`) | `bibliografia.bib` linha 454; `leituras/REF-034-Bailis-Kingsbury-NetworkReliable-2014.txt` (texto integral, 8 p.) | Compilação de relatos de campo; ver §3.1 para os números | **local**, e conferido nesta sessão no texto integral. Nota: `memoria-tcc/18-anotacoes-refs.md` (linhas 274, 288, 301) registra "apenas abstract"; esta pesquisa leu o texto integral arquivado, o que supera aquela anotação |
| REF-033 Chandra & Toueg 1996 | bib e `leituras/` | Detectores de falha não confiáveis; base teórica de FD_ALL3 | local; não trata de frequência |
| REF-032 Gilbert & Lynch (CAP) | bib | Compromisso sob partição | local; não trata de frequência |
| REF-017/REF-016 Newcombe (AWS), `newcombe_aws_formal_methods_2015` | bib; `leituras/REF-017-Newcombe-AWS-TLA.txt` (apenas **extrato** de 1262 bytes) | TLA+ em produção | local; o extrato não tem nada sobre modelagem de falha (busca por "failure/liveness/fairness" deu zero acertos) |
| REF-020 Naskos et al. 2017 (*probabilistic model checking*) | `04-biblioteca-oficial.md` linha 519 | Ponte entre métodos formais e controle; **única** referência local que lida com taxa/probabilidade em verificação | local, resumo apenas |
| REF-003 Yu/Lamport TLC | bib | TLC | local |
| `keycloak_benchmark_timeouts` (FD_ALL3 8000/40000) | `memoria-tcc/19-metodologia-testes-sem7-9.md` linha 38 | Valores default de FD_ALL3 | **DISPENSADA pelo Gestor** ("não revisada por pares; coberta por REF-006"). Esta pesquisa usa o **código-fonte do JGroups** como fonte primária desses valores (§3.5) |
| Decisão 009 e Eixo F | `06-decisoes-do-gestor.md` linha 204 | Gilbert & Lynch, Bailis & Kingsbury, Jepsen, Chandra & Toueg como eixo de falhas | decisão registrada; Jepsen segue como CAND-018, sem arquivo local |

Em resumo: **o projeto não tem hoje nenhuma fonte com taxa de falha quantitativa fora do que Bailis & Kingsbury compilam.** Não há MTBF/MTTF/MTBI em `bibliografia.bib` nem em `memoria-tcc/` (busca por MTBF, MTTF, MTBS, Schroeder, Meza, Gill, Turner-rede: só Turner 2023 de sustentabilidade, REF-039, que é outra obra; **atenção para colisão de nome**: "Turner" no `.bib` é o editorial da Nature, enquanto "Turner et al., *California Fault Lines*" é outro trabalho, de redes).

## 3. Fontes externas, com números e trechos

Todos os trechos abaixo foram conferidos no texto. Aspas curtas são literais; o resto é paráfrase.

### 3.1 Bailis & Kingsbury 2014 (local, verificado no texto integral)

- Título: *The network is reliable: an informal survey of real-world communications failures*; Peter Bailis, Kyle Kingsbury; Communications of the ACM 57(9):48-55, setembro de 2014. DOI 10.1145/2643130 (já no `.bib`). Arquivo: `leituras/REF-034-Bailis-Kingsbury-NetworkReliable-2014.txt`.
- Sobre o estudo de Gill et al. (reportado por eles): "an average failure rate of 5.2 devices per day and 40.8 links per day, with a median time to repair of approximately five minutes (and a maximum of one week)"; "median packet loss of 59,000 packets per failure"; redundância melhora o tráfego mediano em apenas 43%.
- Sobre o CENIC (Turner et al.): "over 508 'isolating network partitions'" em cinco anos; duração média "from six minutes for software-related failures to over 8.2 hours for hardware-related failures (median 2.7 and 32 minutes; 95th percentile of 19.9 minutes and 3.7 days, respectively)".
- Sobre o Chubby (Google): 61 interrupções em 700 dias de operação; das nove com mais de 30 s, quatro por manutenção de rede e duas por "suspected network connectivity problems".
- Relato anedótico de EC2: perda total de conexão entre front-end e back-end "for a few seconds, several times a month"; depois "2 to 4 times a day". MongoDB: "network events causing failover on EC2 are common" (relato de usuários, **anedótico**, os autores marcam como tal).
- Limite: os próprios autores chamam o levantamento de informal; as taxas vêm de fontes heterogêneas e não são comparáveis entre si.

### 3.2 Gill, Jain, Nagappan, SIGCOMM 2011 (verificado na fonte; PDF baixado e lido)

- *Understanding Network Failures in Data Centers: Measurement, Analysis, and Implications*; Phillipa Gill, Navendu Jain, Nachiappan Nagappan; ACM SIGCOMM 2011, Toronto. PDF: https://people.cs.umass.edu/~phillipa/papers/comm235-gill.pdf (cópia da página da autora). DOI não verificado nesta sessão.
- Dados: um ano (outubro de 2009 a setembro de 2010), logs de SNMP/syslog de "tens of geographically distributed data centers" de uma grande provedora.
- Tabela 4 (falhas por dia): dispositivos média 5,2, mediana 3,0, p95 14,7; enlaces média 40,8, mediana 18,5, p95 136,0. COV 1,3 e 1,9 ("high variability").
- §4.3: balanceadores de carga têm "a 1 in 5 chance of experiencing failure" no ano; ToRs e AggS cerca de 5% e 10% (resumo na §1.1).
- §4.5.2: "LBs tend to have the shortest time between failures, with a median of 8.6 minutes and 16.4 minutes for LB-1 and LB-2"; ToR-1 e AggS-1 "multiple hours"; LB-3 "more than a day". Para enlaces: "On an average, link failures tend to be separated by a period of about one week."
- §5: mediana de pacotes perdidos por falha 59K, bytes 25 MB (tamanho médio perdido 423 B, que eles atribuem a keep-alives e ACKs); redundância dá 43% de melhora na mediana (93% contra 65% por enlace); 25% dos eventos sem impacto no grupo de redundância.
- §4.5.3: "more than four 9's of availability for about 80% of the links".
- Ressalvas dos autores: logs são "best-effort"; "our failure rates should not be interpreted as impacting applications". Falhas de enlace filtradas, **sem flapping**.

### 3.3 Turner, Levchenko, Snoeren, Savage, SIGCOMM 2010 (verificado na fonte; PDF baixado e lido)

- *California Fault Lines: Understanding the Causes and Impact of Network Failures*; ACM SIGCOMM 2010, New Delhi. PDF: https://people.cs.umass.edu/~phillipa/CSE390/calfault.pdf (cópia de curso; versão oficial na ACM DL). DOI não verificado nesta sessão.
- Dados: "over five years of failure events in a large regional network consisting of over 200 routers" (CENIC).
- §6.2.1: rede de clientes (CPE) com "median annual failure rate of 20.5 failures per link"; rede de núcleo (DC) com poucas falhas por enlace; rede HPR com "considerably more".
- §6.2.3: "70% of failures last less than 100 seconds, with a median duration of 13 seconds" na rede DC; "over 70% of the failures in the CPE and HPR networks last less than 10 seconds".
- §6.2.4: *flapping* definido como duas ou mais falhas no mesmo enlace separadas por menos de 10 min; "More than 50% of all flapping episodes ... consist of only two failures, but 5% of the episodes contain more than 19"; os autores sugerem que oscilação "may simply be 'normal' in large networks" e que protocolos devem tratar *flapping* "as a common case".
- Caso extremo: um enlace com mais de 33.000 falhas curtas em seis meses, 93% de todas as falhas de um subconjunto (§6.2).
- A comparação de Gill (§4.5.3) cita "annual downtime of 24-72 minutes reported by Turner et al." para uma WAN acadêmica.

### 3.4 Meza, Xu, Veeraraghavan, Mutlu, IMC 2018 (verificado na fonte; PDF baixado e lido)

- *A Large Scale Study of Data Center Network Reliability*; Justin Meza, Tianyin Xu, Kaushik Veeraraghavan, Onur Mutlu; ACM IMC 2018. PDF: https://conferences.sigcomm.org/imc/2018/papers/imc18-final179.pdf. DOI não verificado nesta sessão. Há versão em tese (arXiv 1901.03401).
- Dados: sete anos (2011-2018) de relatórios de incidente (SEVs) do Facebook e dezoito meses de reparos de fibra (out. 2016 a abr. 2018). **É o único estudo da lista que usa explicitamente os termos MTBI e MTBF.**
- MTBI (§5.4): variou "three orders of magnitude across switch types: from 39,495 device-hours for core devices to 9,958,828 device-hours for RSWs" em 2017.
- MTBF de nós de borda (§6.1): "50% of edge nodes fail less than once every 1710 h, or 2.3 months. And 90% ... less than once every 3521 h, or 4.8 months"; pior nó, uma falha a cada 253 h; melhor, a cada 8025 h; modelo ajustado MTBF_edge(p) = 462,88 e^(2,3408 p), R² ≈ 0,94.
- MTTR de nós de borda: modelo 1,513 e^(4,256 p); mais lento 608 h, mais rápido 1 h.
- Ressalva dos autores: SEVs dependem de relato humano; incidentes não relatados não aparecem.

### 3.5 Schroeder & Gibson, DSN 2006 (verificado na fonte; PDF baixado e lido)

- *A large-scale study of failures in high-performance computing systems*; Bianca Schroeder, Garth A. Gibson; IEEE/IFIP DSN 2006. PDF: https://www.cs.toronto.edu/~bianca/papers/dsn06.pdf. DOI não verificado.
- Dados: "23000 failures recorded on more than 20 different systems" do Los Alamos, nove anos. **São falhas de nó/HPC, não de rede**; só uma categoria de causa é "network".
- Resumo: taxa média de falha varia "ranging from 20–1000 failures per year" por sistema (no corpo, de 17 a 1159); "time between failures is modeled well by a Weibull distribution with decreasing hazard rate"; reparo bem modelado por lognormal; reparo médio de menos de uma hora a mais de um dia.
- §5.3: forma de Weibull **0,7** (visão por nó, 2000-2005) e **0,78** (visão do sistema); "The simpler exponential distribution is a poor fit"; interpretação dos autores: "not seeing a failure for a long time decreases the chance of seeing one in the near future".
- Uso aqui: base empírica para **não** assumir interchegada exponencial ou periódica; transferência para rede de datacenter é por analogia (inferência).

### 3.6 O termo MTBS (secundária)

- Busca nesta sessão ("MTBS" com "mean time between" service/split/software) não encontrou "MTBS" como acrônimo padrão. Apareceu **MTBSI**, "Mean Time Between Service Incidents": "the mean time from when a System or IT Service fails, until it next fails", igual a MTBF + MTRS (glossário de contratos, https://www.lawinsider.com/dictionary/mtbsi) e, no relatório BMC ProactiveNet, MTBSI = (uptime + downtime) / número de incidentes no período (https://docs.helixops.ai/bin/BMC-Helix-Archive/BMC%20ProactiveNet/bppmreporting90/Using/Generating-Event-and-Impact-reports/Impact-report-templates/Mean-Time-Between-Service-Incidents-MTBSI-report/). Ambas **secundárias, não normativas** (ITIL/ITSM, não revisão por pares).
- Interpretação do Gestor ("tempo médio entre eventos de degradação de serviço") é consistente com MTBSI/MTBI. Se o termo entrar no texto, definir explicitamente e citar Meza et al. (MTBI) como origem acadêmica.

### 3.7 JGroups e Infinispan: detecção, suspeita e fusão (verificado no código-fonte; sem dado de frequência)

Fonte primária: repositório JGroups, branch `master`, arquivos lidos em 2026-10-16: `src/org/jgroups/protocols/FailureDetection.java` (base de FD_ALL3), `MERGE3.java`, `VERIFY_SUSPECT2.java` e `doc/manual/protocols-template.adoc`, https://github.com/belaban/JGroups. **Ressalva**: é o `master`, não a tag do JGroups embutida no Infinispan 15.0.21.Final (imagem usada no cluster); conferir a versão exata antes de citar no texto.

| Parâmetro | Valor default no código | Valor no `cluster/jgroups-tcp.xml` do projeto | Observação |
|---|---|---|---|
| FD_ALL3 `timeout` | 40000 ms | default (não sobrescrito) | suspeita quando todos os bits de uma janela `timeout/interval` ficam 0 (manual: bitmap de `timeout`/`interval` bits) |
| FD_ALL3 `interval` | 8000 ms | default | intervalo do heartbeat |
| VERIFY_SUSPECT2 `timeout` | 1000 ms | 1000 ms | "approximation" do tempo de espera por verificação |
| MERGE3 `min_interval` / `max_interval` | 1000 / 10000 ms (default de código) | 10000 / 30000 ms | INFO enviado em instante aleatório no intervalo |
| MERGE3 `check_interval` | `max_interval * 1.6` quando não definido | 48000 ms (derivado) | o coordenador checa vistas divergentes a cada `check_interval` |

- O manual do JGroups (MERGE3) diz que o protocolo "merges the subclusters back into one cluster" depois que a partição cura, e que aumentar `check_interval` "decreases the chance of partial merges ... but doesn't entirely eliminate them". **Não diz nada sobre frequência de splits.**
- Documentação do Infinispan (partition handling): descreve estratégias `when-split`; afirma que redes redundantes reduzem a janela de split sem eliminá-la (snippet de busca sobre o guia 10.0, **secundária**). **Não encontrei nenhum número de frequência.**
- Página do Keycloak sobre timeouts (https://www.keycloak.org/keycloak-benchmark/kubernetes-guide/latest/running/timeout_tuning, **dispensada como referência pelo Gestor**; citada aqui só como conferência): FD_ALL3 `interval=8000`, `timeout=40000`, "The defaults should work for 99% of the use cases"; cita a documentação do kernel Linux: valor 15 de retries "yields a hypothetical timeout of 924.6 seconds" para conexão TCP quebrada. Não diz nada sobre frequência de falhas.
- **Conclusão explícita: não existe, nas fontes que consegui ler, dado publicado de frequência esperada de splits, suspeitas ou views instáveis do Infinispan/JGroups.** Há apenas configuração e, no projeto, medições próprias.

### 3.8 Medições do próprio projeto (local; lidas nos relatórios, **não conferidas por mim contra todo o bruto**)

Fontes: `docs/relatorio-tb-gate-g1-2026-10-16.md`, `docs/relatorio-tb-v3-execucao-2026-10-16.md`, `docs/relatorio-tb-rep2-rep3-g5-g6-2026-10-16.md`; brutos em `runs/gate-b29/`. Conferi apenas que `runs/gate-b29/g1-rep2/t_merge.txt` registra `t_merge_s=188` e `g1-rep3/t_merge.txt` registra `t_merge_s=46`. Valores (cluster size=3, F2 injetada em isn3, cluster ocioso, uma janela por repetição):

| Repetição | t_split(maioria) | t_split(isn3) | t_merge (vista) | t_merge (saúde) |
|---|---|---|---|---|
| REP1 | 46,4 s | 39,6 s | 46,4 s | 48 s |
| REP2 | 45,0 s | 46,1 s | 45,0 s | 188 s (atípico; rebalance) |
| REP3 | 46,4 s | 39,8 s | ≈46 s | 46 s |

Isso é o único dado real de "tempo de degradação" que o TCC tem. É **de uma só configuração e com n=3**; não dá variância.

### 3.9 Precedentes de verificação formal com falhas limitadas (verificado na fonte quando indicado)

| Fonte | O que faz com falhas | Trecho/número | Status |
|---|---|---|---|
| Schultz, Zhou, Dardik, Tripakis, *Design and Analysis of a Logless Dynamic Reconfiguration Protocol* (MongoDB/Northeastern), arXiv:2102.11960v3, nov. 2021; https://arxiv.org/pdf/2102.11960 | Instância finita por *state constraint* | `StateConstraint` limita `currentTerm[s] <= MaxTerm`, `Len(log[s]) <= MaxLogLen`, `configVersion[s] <= MaxConfigVersion`; "sufficient to make the reachable state space of this model finite". Verificações com 4 servidores, logs de até 2, versões até 3, termos até 3 (≈345 milhões de estados distintos, ≈8 h, 20 workers) e 5 servidores, versões e termos até 4 (≈812 milhões, ≈19,5 h). Admitem que a verificação é "necessarily, incomplete" para parâmetros ilimitados | verificado na fonte |
| Hackett, Rowe, Kuppe, *Understanding Inconsistency in Azure Cosmos DB with TLA+*, arXiv:2210.13661, out. 2022; https://arxiv.org/pdf/2210.13661 | Falha como ação não determinística, com contagem pequena | "model-checked a uniqueness property for all session tokens given up to 6 writes and any one failure event"; "exhaustive state space exploration of logs up to length 6, and depth-first random simulation of execution traces exploring up to 100 steps"; escrita em andamento "may also non-deterministically fail at any time, due to timeouts, spurious network failures"; `epoch` é "a monotonically increasing counter of fail-overs" | verificado na fonte (venue de publicação não verificado nesta sessão) |
| Ongaro, `raft.tla`, https://github.com/ongardie/raft.tla | Falhas como ações livres no `Next`: `Restart(i)`, `DuplicateMessage(m)`, `DropMessage(m)` | O `Next` inclui as três como disjuntos sem contador nem fairness; o limite é imposto no modelo (cfg do usuário) | verificado na fonte (arquivo lido); o fato de **não haver contador no spec** é leitura do arquivo |
| Fórum TLA+ (Merz, 13 e 15/out/2012), https://groups.google.com/g/tlaplus/c/sHGqYSGzfN8/m/nimjciqd61UJ | Armadilha de *state constraint* com liveness | "checking liveness properties in the presence of state constraints can be really tricky"; se todas as execuções violam a restrição, "you haven't really verified anything"; a restrição pode truncar comportamentos e fazer a propriedade valer "vacuously" | verificado na página (mensagem de lista, não revisão por pares). O livro *Specifying Systems* (Lamport 2002, §14.3 sobre restrições; REF no `.bib` como `lamport_specifying_systems_2002`) é o tratamento canônico, **não relido por mim**; o número de seção vem do snippet de busca (**secundária**) |
| Davis, Hirschhorn, Schvimer, *eXtreme Modelling in Practice*, PVLDB 13(9):1346-1358, 2020, DOI 10.14778/3397230.3397233 | Conformidade modelo-implementação (MongoDB) | só vi o resumo na busca (**secundária**); não verifiquei como tratam falhas | secundária |
| Newcombe et al., AWS (já no `.bib`) | Não verificado: o extrato local não traz nada sobre modelagem de falha | — | local, insuficiente |

**O que esses precedentes mostram**: (a) contadores de eventos com teto como *state constraint* é a técnica corrente (MongoDB: termo, versão, tamanho de log); (b) Cosmos DB usa "no máximo um evento de falha" como cota; (c) nenhum dos três **deriva** o teto de MTBF; o teto é escolhido por tratabilidade. Não achei nenhum artigo que faça a tradução MTBF para teto de eventos. Não pesquisei de forma exaustiva (só busca na web e leitura dos artigos acima), então "não achei" não é "não existe".

**Jepsen**: a busca desta sessão não retornou uma fonte que declare a cadência de partições do Jepsen; só a documentação de `jepsen.nemesis` (partição por metades aleatórias disparada por `:start`/`:stop` de um gerador, com exemplo de `stagger 1` e `time-limit 15`, **exemplo ilustrativo**, não padrão de um relatório específico). Ou seja, em Jepsen a cadência é parâmetro do teste, não derivada de taxa de campo. Pendente: ler um relatório específico (por exemplo, de uma análise cujo sistema o Gestor queira citar) antes de afirmar algo.

## 4. Tabela-resumo de números (para consulta rápida)

| Fenômeno | Número | Fonte | Escopo/ressalva |
|---|---|---|---|
| Falhas de dispositivo por dia, rede inteira | média 5,2; mediana 3,0 | Gill 2011, Tab. 4 | rede de um provedor, muitos datacenters; não é por cluster |
| Falhas de enlace por dia, rede inteira | média 40,8; mediana 18,5 | Gill 2011, Tab. 4 | idem |
| Tempo médio entre falhas de enlace | ≈ 1 semana (por enlace) | Gill 2011, §4.5.2 | só eventos "acionáveis"; sem *flapping* |
| Mediana entre falhas de balanceador de carga | 8,6 min (LB-1), 16,4 min (LB-2) | Gill 2011, §4.5.2 | dominado por poucos dispositivos defeituosos |
| Mediana de reparo (enlace) | ≈ 5 min | Gill 2011 via Bailis & Kingsbury | passo de coleta SNMP de 4 min limita resolução |
| Falhas por enlace-ano, mediana (CPE) | 20,5 | Turner 2010, §6.2.1 | rede de clientes do CENIC |
| Duração mediana de falha, rede DC | 13 s; 70% abaixo de 100 s | Turner 2010, §6.2.3 | enlace, não partição |
| Partições isoladoras, 5 anos | 508 | Turner 2010 via Bailis & Kingsbury | duração mediana 2,7 min (software) e 32 min (hardware) |
| MTBF mediano de nó de borda | 1710 h (2,3 meses) | Meza 2018, §6.1 | WAN do Facebook |
| MTBI por tipo de switch, 2017 | 39.495 a 9.958.828 device-horas | Meza 2018, §5.4 | por dispositivo, não por cluster |
| Interchegada de falhas de nó (HPC) | Weibull, forma 0,7 a 0,78 | Schroeder & Gibson 2006, §5.3 | falhas de nó, não de rede |
| Interrupções do Chubby | 61 em 700 dias (uma a cada ≈ 11,5 dias) | via Bailis & Kingsbury | quociente é cálculo meu; inclui causas não-rede |
| Detecção FD_ALL3 | 40 s (`timeout`), heartbeat 8 s | código JGroups | default de código |
| t_split medido no projeto | 39,6 a 46,4 s | relatórios TB | n=3, uma configuração |
| t_merge medido no projeto | 45 a 48 s (um atípico de 188 s) | relatórios TB | n=3 |

## 5. Lacunas

1. **Nenhuma taxa de split/partição do Infinispan/JGroups.** Nem em documentação, nem em artigo. Qualquer número de frequência para o Infinispan sai de analogia com redes de datacenter (que são de outra camada) ou de medição própria.
2. **Nenhuma fonte converte taxa de falha de campo em cota de eventos para model checking.** É lacuna de literatura, e o texto do TCC deve declarar a tradução como decisão de método.
3. **Sem conferência da versão do JGroups embutida no Infinispan 15.0.21.Final**; os defaults citados são do `master` de hoje. Baixar o JAR da imagem (`jgroups-*.jar`) e conferir antes de citar. O `cluster/jgroups-tcp.xml` do projeto fixa `MERGE3` e `VERIFY_SUSPECT2`, mas deixa FD_ALL3 no default.
4. **Jepsen**: cadência de falhas por relatório não verificada (ver §3.9).
5. **Não achei dado de perda de pacote em regime estacionário em datacenters** (taxa de perda "de fundo"). Gill reporta perda **durante falhas** (mediana 59 mil pacotes/falha) e Bailis & Kingsbury relatam eventos de 50% a 100% de perda localizada, mas não uma taxa percentual contínua. Isso importa porque o TCC injeta perda de 1% a 50% (F4); a faixa de severidade não tem apoio quantitativo de campo nas fontes lidas.
6. **Meza et al. 2018 e Gill et al. 2011 medem incidentes de dispositivo, não de cluster Infinispan.** Converter para "por cluster de 3 nós" exige premissas sobre topologia que o TCC não tem.
7. As medições do projeto são de uma configuração, cluster ocioso, n=3, sem variância; o atípico de 188 s ainda não tem causa estabelecida (o relatório levanta hipóteses, sem prova).
8. Fontes secundárias a promover a lido-integralmente se forem citadas: Davis et al. 2020 (só resumo), *Specifying Systems* §14.3 (só snippet), documentação do Infinispan sobre partition handling (só snippet de busca).

## 6. Recomendação: como mapear taxa de degradação para o TLA+ e para o experimento

Tudo nesta seção é **inferência** apoiada nas fontes acima, e vem rotulada assim. A parte do modelo é a que o Gestor pediu ("a cada N execuções/passos, em vez de tempo").

### 6.1 Princípio: o teto com posicionamento livre cobre o esquema periódico

**Inferência (argumento de inclusão).** O modelo atual (`spec/MCSession.tla`) impõe `LimiteEventosFalha == quedas + particoes =< MaxEventosFalha` e deixa o TLC escolher livremente **quando** cada evento ocorre. Um esquema "um evento a cada N passos" é um **subconjunto** desses comportamentos (os que têm eventos espaçados por pelo menos N passos). Portanto:

- para **invariantes de segurança** (I1 a I6 e as propriedades por transição), verificar com teto k e posicionamento livre é **mais forte** que verificar com periodicidade: se não há violação no conjunto maior, não há no subconjunto;
- acrescentar periodicidade **não descobre violação nova**; só reduz o espaço de estados. Serve como otimização de tratabilidade ou como delimitação de escopo, não como realismo adicional.

Isto responde à pergunta do Gestor: a periodicidade em "passos" é **dispensável para a correção** e só pode ser útil para custo. Se o objetivo é mostrar aderência à taxa de falha real, o argumento acima (o modelo cobre qualquer calendário com no máximo k eventos) é mais defensável perante a banca do que um período arbitrário em passos, porque passo do TLC não é unidade de tempo nem de operação (a ordem de intercalação é livre).

### 6.2 (a) Parâmetro `MaxEventosFalha` no TLA+

1. **Como definir k a partir da taxa (inferência, aritmética minha).** Seja λ = 1/MTBI e W a janela de exposição (duração de uma execução ou de uma sessão). Sob modelo de Poisson, P(≥2 eventos em W) ≈ (λW)²/2. Com os MTBF da literatura, para um componente isolado:

   | Referência de MTBF | MTBF usado | W = 120 s: P(≥1) / P(≥2) | W = 1 h: P(≥1) / P(≥2) |
   |---|---|---|---|
   | Gill, enlace, ≈ 1 semana | 168 h | 2,0e-4 / 2,0e-8 | 5,9e-3 / 1,8e-5 |
   | Meza, nó de borda, mediana | 1710 h | 2,0e-5 / 1,9e-10 | 5,9e-4 / 1,7e-7 |
   | Turner, CPE, 20,5 falhas/ano | 427 h | 7,8e-5 / 3,0e-9 | 2,3e-3 / 2,7e-6 |
   | Chubby, 61 em 700 dias | 275 h | 1,2e-4 / 7,3e-9 | 3,6e-3 / 6,6e-6 |

   Leitura: para uma sessão curta (timeout de 30 s no workload) contra um componente com MTBF de dias, **um evento por sessão já é o caso raro**, e dois é desprezível. Isso sustenta `MaxEventosFalha = 1` como cota principal e `= 2` como cota de estresse (queda e partição na mesma sessão, ou duas partições). **Ressalva forte**: o modelo de Poisson é contrariado pelas fontes (Schroeder & Gibson: Weibull com forma < 1; Turner: *flapping* é comum; Gill: balanceadores com mediana de 8,6 min). Falhas vêm em rajada, e a probabilidade de segundo evento condicional ao primeiro é bem maior que a incondicional. Por isso k = 2 e k = 3 devem ser rodados quando o custo permitir, e a tabela acima deve ser lida como limite inferior da probabilidade de múltiplos eventos.
2. **Cota atual é conservadora e coerente.** O comentário de `MCSession.tla` já recomenda `MaxEventosFalha = 1` como primeira rodada (os `spec/MC-*.cfg` não definem a constante; ela é fixada nos cfgs de `runs/tlc/`, que não conferi aqui), com a justificativa de tratabilidade (P-25 no `registro-de-problemas.md`: sem o teto, TypeOK/I3/I4 deixaram de fechar em tiny/small/medium sob F2). A literatura confirma que é prática comum (MongoRaftReconfig, Cosmos DB com "any one failure event"). Registrar no texto: **o teto é uma hipótese de escopo pequeno, justificada por tratabilidade e apoiada, a posteriori, pela baixa probabilidade de múltiplos eventos na janela.**
3. **Periodicidade opcional (só se o Gestor quiser).** Se quiser expressar "degradação periódica" no modelo, a forma mais simples e honesta é um contador de passos desde o último evento de falha, por exemplo uma variável `desdeUltima` (incrementada em cada ação que não seja `Cair`/`Particionar`, zerada nelas) e a guarda `desdeUltima >= PeriodoMin` em `Cair` e `Particionar`. Isto é modelagem de **espaçamento mínimo**, não de periodicidade exata, e (pelo §6.1) só restringe. Recomendo **não** tornar isso o padrão. Se for usado, rodar a mesma bateria com e sem a guarda e relatar que os veredictos coincidem.
4. **Cuidado com liveness (verificado na fonte, Merz 2012).** Com *state constraint* sobre contadores, propriedades de vivacidade (por exemplo "o cluster converge depois da cura") podem passar vacuamente ou falhar por truncamento. O que o TCC verifica hoje são invariantes de segurança por traço, então o risco é baixo; se entrar convergência (I5/I6) como propriedade temporal, usar fairness fraca sobre as ações de reparo (`Reviver`, `Mesclar`) e verificar que não há comportamento que termina em *stuttering* só por causa da restrição.
5. **Rajada.** Como a literatura aponta rajadas, a cota k deve contar **eventos** (já faz: `quedas + particoes`) sem exigir espaçamento. Se o Gestor quiser modelar *flapping* explicitamente, é `Particionar`/`Mesclar` repetidos dentro da cota, que é o que o modelo já permite com k ≥ 2.

### 6.3 (b) Cadência de injeção no experimento real

1. **Unidade da cadência: operações de cliente ou tempo, não "passos".** No experimento real o equivalente natural de "a cada N execuções" é "a cada N operações de sessão confirmadas" (o cliente já registra isso) ou, de forma equivalente, um intervalo de tempo T = N / taxa_de_operações. Passo de TLC não tem contraparte direta; não prometer equivalência entre "N passos" e "N operações".
2. **Espaçamento mínimo imposto pelo cluster (dado do projeto, §3.8).** Após uma F2, o cluster precisa de t_split ≈ 40 a 46 s para detectar, mais t_merge ≈ 46 a 48 s para fundir (e, no atípico, 188 s até HEALTHY). Duas injeções seguidas com intervalo menor que cerca de 100 s medem **a mesma degradação**, não dois eventos. Recomendação: **intervalo entre injeções ≥ janela de injeção + t_merge + folga de convergência**, por exemplo ≥ 300 s, valor que já cobre o atípico de 188 s. Para o intervalo curto (rajada/*flapping*), fazer um cenário **separado e rotulado como estresse**, não misturar.
3. **Escolha de uma taxa de referência declarada.** Como não há taxa do Infinispan, declarar a taxa como **parâmetro do experimento, ancorado em faixa da literatura**, não como medida do ambiente. Sugestão: três regimes, cada um rotulado com a fonte da faixa e com o aviso de que a frequência é acelerada em relação ao campo (um experimento de minutos não reproduz MTBF de dias):
   - **Raro**: um evento por execução (corresponde a k = 1 do modelo);
   - **Moderado**: um evento a cada 300 s (espaçamento mínimo de §6.3.2), ordem de grandeza de dezenas de eventos por hora, **muito acima** de qualquer taxa de campo citada, o que é aceitável como teste de estresse mas deve ser dito;
   - **Rajada**: eventos com intervalo curto, só para exercitar *flapping*, apoiado em Turner (§6.2.4: ">19 falhas em 5% dos episódios").
4. **Taxa acelerada é decisão do Gestor.** A aceleração (experimento de minutos contra MTBF de dias) é prática de teste de injeção de falhas, mas não achei fonte nesta sessão que a legitime formalmente; apresentar como **escolha de projeto** e pedir aprovação, em vez de apresentar como derivada da literatura.
5. **Severidade de perda (F4)**: as lacunas (§5.5) valem: a faixa 1% a 50% não tem apoio quantitativo de campo nas fontes lidas; documentar como decorrente das hipóteses H-L1..H-L7 do plano, e não como "taxa observada em datacenters".
6. **Contagem de repetições.** Com n=3 e variância desconhecida, manter a regra de três para zeros (já prevista no plano de fechamento) ao relatar ausência de violações.

### 6.4 Texto-modelo curto para a seção de ameaças/limites (rascunho, sem citar nada ainda não aprovado)

> A taxa de eventos de degradação não é derivada de medição do ambiente. O modelo limita o número de eventos de falha por comportamento (MaxEventosFalha), e o conjunto de comportamentos com no máximo k eventos e posicionamento livre contém qualquer calendário periódico com até k eventos. A escolha de k = 1 e k = 2 fundamenta-se em tratabilidade e na baixa probabilidade de múltiplos eventos numa janela curta quando o tempo entre eventos é da ordem de dias a meses [CAND-Gill, CAND-Meza, CAND-Turner], com a ressalva de que a literatura descreve falhas em rajada [CAND-Schroeder].

Os rótulos CAND acima são marcadores, não chaves de bibliografia.

## 7. Candidatas a bibliografia (para decisão do Gestor; nada foi gravado em `bibliografia.bib` nem em `05-referencias-candidatas.md`)

| Proposta | Referência | Status de verificação | Uso sugerido |
|---|---|---|---|
| CAND-A | Gill, Jain, Nagappan. *Understanding Network Failures in Data Centers: Measurement, Analysis, and Implications*. ACM SIGCOMM 2011 | texto lido; DOI a verificar | taxa de falhas de enlace/dispositivo, 43% de redundância, perda por falha |
| CAND-B | Turner, Levchenko, Snoeren, Savage. *California Fault Lines*. ACM SIGCOMM 2010 | texto lido; DOI a verificar. **Cuidado**: chave distinta de `turner...` de sustentabilidade (REF-039) | *flapping*, falhas por enlace-ano, duração |
| CAND-C | Meza, Xu, Veeraraghavan, Mutlu. *A Large Scale Study of Data Center Network Reliability*. ACM IMC 2018 | texto lido; DOI a verificar | **origem acadêmica dos termos MTBF/MTBI** |
| CAND-D | Schroeder, Gibson. *A large-scale study of failures in high-performance computing systems*. DSN 2006 | texto lido; DOI a verificar | Weibull e interchegada não exponencial |
| CAND-E | Schultz, Zhou, Dardik, Tripakis. *Design and Analysis of a Logless Dynamic Reconfiguration Protocol*. arXiv:2102.11960 (2021) | texto lido; venue de publicação a verificar | precedente de cotas finitas por *state constraint* |
| CAND-F | Hackett, Rowe, Kuppe. *Understanding Inconsistency in Azure Cosmos DB with TLA+*. arXiv:2210.13661 (2022) | texto lido; venue a verificar | precedente de "any one failure event" |
| CAND-G | JGroups (repositório, manual do protocolo MERGE3/FD_ALL3), https://github.com/belaban/JGroups | código lido (`master`); conferir versão do JAR do Infinispan 15.0.21 | valores default de detecção e fusão (substitui a fonte dispensada do Keycloak) |
| CAND-H | Davis, Hirschhorn, Schvimer. *eXtreme Modelling in Practice*. PVLDB 13(9), 2020. DOI 10.14778/3397230.3397233 | só resumo | opcional |

Observação de política: as páginas de MTBSI (§3.6) são glossários de contrato/ITSM e **não** devem entrar como referência acadêmica.

## 8. Perguntas ao Gestor

1. Aprovar CAND-A a CAND-G como referências a promover (e quais)? Posso então ler as seções relevantes de novo, fechar DOI/venue e só depois gravar no `.bib` e em `04-biblioteca-oficial.md`.
2. Adotar **MTBI** (ou "tempo médio entre eventos de degradação") no lugar de "MTBS" no texto?
3. Concordo em registrar a decisão "periodicidade em passos é opcional; padrão é teto k com posicionamento livre" como decisão do Gestor em `06-decisoes-do-gestor.md`?
4. Autoriza a verificação do JAR do JGroups da imagem Infinispan 15.0.21 (download de um artefato Maven) para fechar a lacuna §5.3?
5. Cadência real: aceita a taxa acelerada e os três regimes (§6.3.3) como decisão de projeto?
