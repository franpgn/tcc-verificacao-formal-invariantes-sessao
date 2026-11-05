# Revisão: o que difere da versão aprovada pela banca de TCC I

Versão fundida de 01/11/2026 (`Main-Monografia.pdf` nesta pasta). Tudo o que não aparece aqui está igual à versão da banca e não precisa de revisão. O PDF `Main-Monografia-revisao.pdf` mostra os mesmos trechos em azul. As páginas são as impressas no canto superior da folha.

**Total de trechos:** 159 (ACRÉSCIMO 22, ATUALIZAÇÃO 30, CORREÇÃO 96, ESTRUTURA 11).

Tipos: **CORREÇÃO** troca texto da banca por versão corrigida; **ATUALIZAÇÃO** mantém o texto da banca e acrescenta ao lado o que mudou depois; **ACRÉSCIMO** é conteúdo novo que a banca não tinha; **ESTRUTURA** é um bloco novo sem número (fora do sumário); **DÚVIDA** é um ponto ainda em aberto.

## Fora do texto dos capítulos

| Onde | O que difere da banca |
|---|---|
| Folha de rosto | Data “Outubro de 2026” (banca: Julho de 2026). |
| Folha de aprovação | Data de defesa em branco (banca: 10 de julho de 2026). |
| Lista de siglas | Acrescentadas CPU, JVM e LWW. |
| Referências | Base do `.bib` da versão de 30/10 (metadados corrigidos pelo Bibliotecário), mais 12 entradas da banca restauradas porque o texto as cita: `redhat_datagrid_admin`, `lee_tla_tlc_2021` (créditos de figura), `hdrhistogram`, `jmx_oracle`, `junit5`, `maven_apache`, `numpy_harris_2020`, `openmetrics_spec`, `podman_docs`, `prometheus_docs`, `salhi_benchmarking_cache_2018`, `scipy_virtanen_2020`. Saiu NIST 800-63B (2017), substituído pelo 800-63B-4 (2025). |
| Apêndices | Não há Apêndice de protocolo do mapeamento (está na Seção 3.1, como na banca). |

Os trechos retirados dos Caps. 1 a 3 em 01/11 (prática e mudanças em relação ao plano) não estão no texto; ficaram anotados em `TRECHOS-PARA-DESENVOLVIMENTO.md` para entrar no Cap. 4 ou 5.

Em cada trecho abaixo, a citação aparece como [chave], e a referência cruzada como ⟨rótulo⟩.


## Cap. 1 Introdução

### p. 10 · INT-01 · CORREÇÃO · Contextualização, §1

Nota: Chave `nist_sp_800_63b_2017` trocada por `nist_sp_800_63b4_2025`; texto da banca mantido.

> Esses estados incluem sessões abertas, contadores de tentativas falhas, metadados de renovação e marcas de bloqueio, e existem para que cada nova requisição possa ser validada como continuação legítima de uma autenticação anterior [nist_sp_800_63b4_2025, owasp_session_mgmt_cheatsheet].

### p. 10 · INT-02 · CORREÇÃO · Contextualização, §2

Nota: "confirmação remota de um quórum de réplicas" trocado por "confirmação remota das demais cópias" (decisão do autor, rodada 2).

> Na replicação síncrona, a confirmação de uma escrita depende da confirmação remota das demais cópias, o que atrela cada operação ao Round-Trip Time (RTT) e à variabilidade da rede.

### p. 11 · INT-06 · CORREÇÃO · 1.1 Problema, §1

Nota: Chave NIST trocada para `nist_sp_800_63b4_2025`; frase da banca mantida.

> As três propriedades têm respaldo normativo, com tratamento explícito em [nist_sp_800_63b4_2025] e em [owasp_session_mgmt_cheatsheet], e podem ser violadas quando replicação assíncrona e failover interagem.

### p. 11–12 · INT-07 · ACRÉSCIMO · 1.1 Problema, §1

Nota: Exigências concretas de NIST (invalidação no *logout*, limitação de tentativas, sem prazos por AAL) e OWASP; forma replicada declarada como derivação do trabalho.

> As três propriedades derivam de exigências de [nist_sp_800_63b4_2025], como a invalidação dos segredos de sessão no logout e a limitação de tentativas falhas, e de [owasp_session_mgmt_cheatsheet], que exige a invalidação da sessão no servidor no logout. A forma replicada dessas propriedades é derivação deste trabalho.

### p. 13 · INT-09 · CORREÇÃO · 1.2 Justificativa, §3

Nota: Exemplo de 50 ms / 5% / vinte vezes atribuído ao SRE Book (`google_sre_slo`), não a Dean e Barroso; na rodada 2, redação trocada pela proposta do Bibliotecário.

> Um exemplo do livro de engenharia de confiabilidade do Google mostra o problema: a latência típica é da ordem de 50 milissegundos, mas 5% das requisições são vinte vezes mais lentas, e uma média não acusa a variação na cauda [google_sre_slo].

### p. 13 · INT-10 · CORREÇÃO · 1.2 Justificativa, §4

Nota: Chave NIST trocada para `nist_sp_800_63b4_2025` (marcador agora só na citação, por causa de INT-26).

> [nist_sp_800_63b4_2025]

### p. 14 · INT-11 · ACRÉSCIMO · 1.2 Justificativa, §4

Nota: Acrescentada a oração "a formulação delas para o estado replicado é derivação do trabalho".

> , e a formulação delas para o estado replicado é derivação do trabalho

### p. 11 · INT-18 · CORREÇÃO · Contextualização, §4

Nota: Frase atribuída a Navaux trocada: consumo de energia e resiliência como desafios de HPC; a relação com reprocessamento após falhas fica sem atribuição.

> Consumo de energia e resiliência estão entre os desafios centrais da computação de alto desempenho [navaux_hpc_challenges_2023], e falhas cobram recursos em reconstruções de estado, repetição de operações e reprocessamento.

### p. 11 · INT-19 · CORREÇÃO · Contextualização, §5

Nota: "decisões de cache e de armazenamento em memória" passou a "decisões de *cache*" (GreenCache trata de *cache*).

> decisões de cache têm impacto de carbono mensurável

### p. 12 · INT-21 · CORREÇÃO · 1.1 Problema, §3

Nota: Chave de "reprodução por testes é pouco confiável" trocada de `yu_lamport_tla_modelchecking_1999` para `newcombe_aws_formal_methods_2015`.

> [newcombe_aws_formal_methods_2015]

### p. 12 · INT-22 · CORREÇÃO · 1.2 Justificativa, §1

Nota: "O Dynamo adotou esse desenho porque a escrita é confirmada localmente, sem esperar a ida e volta" trocado por: a escrita pode ser confirmada antes de chegar a todas as réplicas, bastando parte delas.

> No Dynamo, uma escrita pode ser confirmada ao cliente antes de chegar a todas as réplicas, bastando a resposta de parte delas, e essa decisão tem como custo réplicas que observam estados defasados e exigem reconciliação posterior.

### p. 13 · INT-23 · CORREÇÃO · 1.2 Justificativa, §3

Nota: Acrescentado o exemplo de Dean e Barroso (10 ms, p99 de 1 s, 100 servidores, 63%), com a redação do Bibliotecário.

> [dean_barroso_tail_2013] mostram que o efeito cresce com o número de componentes: se cada servidor responde tipicamente em 10 milissegundos, mas tem p99 de 1 segundo, uma requisição que espera 100 desses servidores passa de 1 segundo em 63% dos casos.

### p. 13 · INT-24 · CORREÇÃO · 1.2 Justificativa, §3

Nota: Chave de "difícil de reproduzir apenas por testes" trocada de `yu_lamport_tla_modelchecking_1999` para `newcombe_aws_formal_methods_2015`.

> [newcombe_aws_formal_methods_2015]

### p. 13 · INT-25 · CORREÇÃO · 1.2 Justificativa, §3

Nota: Citação da aplicação industrial passou a `newcombe_aws_tla_2014, newcombe_aws_formal_methods_2015`.

> [newcombe_aws_tla_2014, newcombe_aws_formal_methods_2015]

### p. 13 · INT-26 · CORREÇÃO · 1.2 Justificativa, §4

Nota: Removido `\citep{armbrust_cloud_2010}` da frase sobre gerência de sessão (o artigo não trata de sessão); marcador de remoção no ponto.

> *(remoção: trecho da banca retirado neste ponto)*

### p. 11 · INT-27 · CORREÇÃO · Contextualização, §5 (fim)

Nota: A pedido do autor, saiu a oração final da banca sobre a mensuração e o reporte do carbono (editorial de Turner); a frase termina em "coerente com essa preocupação".

> .


## Cap. 2 Fundamentação Teórica

### p. 17 · FUN-01 · CORREÇÃO · 2.1

Nota: Removidas as orações "relido pelos próprios autores... segurança/vivacidade", que vêm de Gilbert e Lynch 2012 (fora do .bib). Fica só a atribuição a 2002.

> .

### p. 18 · FUN-02 · ACRÉSCIMO · 2.1

Nota: Duas frases sobre a taxonomia de falhas de Avizienis et al. (parada, silenciosa, temporização), no fim do parágrafo de Chandra-Toueg/Bailis.

> Para nomear essas falhas, adota-se a taxonomia de [avizienis_taxonomy_2004], que distingue falhas de conteúdo e de temporização e descreve a falha por parada, em que o serviço deixa de responder, e a falha silenciosa, em que nenhuma saída chega à interface do serviço. Nessa terminologia, a queda de nó estudada aqui é uma parada silenciosa, o atraso é uma falha de temporização tardia, e a perda parcial e a partição aparecem nos nós como falhas de temporização ou como silêncio do canal entre eles.

### p. 19 · FUN-03 · CORREÇÃO · 2.1

Nota: Na frase do Dynamo, R+W>N deixa de "caracterizar consistência forte" e passa a "sistema de quórum", com R e W abaixo de N; citação com seção 4.5.

> O Dynamo combina cinco mecanismos, e a replicação é regulada pela tripla $(N, R, W)$, em que $N$ é o número de réplicas, $R$ é o número mínimo de nós em uma leitura e $W$ o número mínimo em uma escrita. Com $R + W > N$, o sistema se comporta como um sistema de quórum; como a latência é ditada pela mais lenta das $R$ ou $W$ réplicas, $R$ e $W$ costumam ficar abaixo de $N$ [decandia_dynamo_2007, seção 4.5].

### p. 19 · FUN-04 · ACRÉSCIMO · 2.1

Nota: Termo original "(vector clocks)".

> (vector clocks)

### p. 19 · FUN-05 · ACRÉSCIMO · 2.1

Nota: Termo original "(sloppy quorum)".

> (sloppy quorum)

### p. 20 · FUN-06 · ACRÉSCIMO · 2.1

Nota: Termo original "(hinted handoff)".

> (hinted handoff)

### p. 20 · FUN-07 · CORREÇÃO · 2.1

Nota: "Quatro exemplos canônicos" vira "Dois exemplos do artigo" (só G-Counter e PN-Counter estão no SSS 2011).

> Dois exemplos do artigo ilustram o conceito.

### p. 20 · FUN-08 · CORREÇÃO · 2.1

Nota: Removidas as frases do OR-Set e do LWW-Register (não estão em shapiro_crdt_2011).

> *(remoção: trecho da banca retirado neste ponto)*

### p. 21 · FUN-09 · CORREÇÃO · 2.2

Nota: "Cinco modos" vira quatro modos (local, replicado, distribuído, invalidação), com SYNC/ASYNC como modo de confirmação; chave passa a `infinispan_configuring_15`, seção 1.3.

> O guia de configuração descreve quatro modos de cache: local, replicado, distribuído e invalidação [infinispan_configuring_15, seção 1.3]. O modo local (LOCAL) mantém o cache apenas no nó atual. O replicado mantém cada entrada em todos os nós do cluster, e o distribuído, em um número fixo de cópias, configurado pelo parâmetro numOwners; ambos admitem confirmação síncrona ou propagação assíncrona, o que dá os nomes REPL_SYNC, REPL_ASYNC, DIST_SYNC e DIST_ASYNC. O modo de invalidação (INVALIDATION) dispara apenas mensagens para que outros nós descartem cópias locais quando há atualização.

### p. 21–22 · FUN-10 · ACRÉSCIMO · 2.2

Nota: Dono primário e reservas, custo 2·numOwners, advertência do DIST_ASYNC (seções 2.2 e 2.4) e ligação com a comparação síncrono × assíncrono.

> Em modo distribuído, o dono primário coordena escritas e $numOwners - 1$ nós atuam como reservas, e uma escrita custa no máximo $2 \cdot numOwners$ mensagens. A documentação adverte que o modo distribuído assíncrono pode perder atualizações e admitir leituras desatualizadas mesmo pela thread que acabou de escrever a chave, e que o nó de origem não recebe confirmação dos demais nós sobre a operação [infinispan_configuring_15, seções 2.2 e 2.4]. Esse comportamento afeta a escolha do modo para aplicações que não toleram tais efeitos e é o objeto da comparação entre os modos síncrono e assíncrono neste trabalho.

### p. 24 · FUN-11 · ACRÉSCIMO · 2.2

Nota: Localizador `\cite[seção 2.2.1]{infinispan_configuring_15}` na frase "caches distribuídos não são linearizáveis".

> [infinispan_configuring_15, seção 2.2.1]

### p. 25 · FUN-12 · CORREÇÃO · 2.3

Nota: Os 35 passos do DynamoDB passam de `newcombe_aws_tla_2014` para `newcombe_aws_formal_methods_2015`.

> [newcombe_aws_formal_methods_2015]

### p. 26–27 · FUN-13 · ACRÉSCIMO · 2.3

Nota: Parágrafo novo sobre cardinalidades pequenas (Yuan et al. 2014), que apoia os modelos de 2 a 3 réplicas e o cluster de 3 nós.

> A verificação exaustiva só é tratável com cardinalidades pequenas, e a pertinência desse recorte tem apoio empírico. [yuan_simple_testing_2014], ao analisar falhas reportadas em sistemas distribuídos de produção, constatam que 98% delas se manifestam com três nós ou menos. A observação sustenta tanto os modelos com duas ou três réplicas verificados pelo TLC quanto o cluster experimental de três nós, embora não garanta que todo defeito do protocolo apareça nessas cardinalidades.

### p. 28–29 · FUN-14 · ESTRUTURA · 2.4

Nota: Seção "Avaliação experimental: estatística e estimativa de energia" da ATUAL absorvida em 2.4, sem título. 1º parágrafo: variabilidade, t de Student, Hoefler, bootstrap, Mann-Whitney, binomial. Label `sec-fund-avaliacao` mantido (aponta para 2.4; não é referenciado em outros arquivos da ATUAL).

> A avaliação experimental de sistemas computacionais precisa lidar com a variabilidade entre execuções. [georges_jvm_statistics_2007] mostram que o desempenho de programas Java não é determinístico, por efeito de fatores como a compilação dinâmica, a coleta de lixo e o escalonamento de threads, e que metodologias de avaliação sem rigor estatístico podem levar a conclusões enganosas. Para médias estimadas a partir de poucas execuções, o intervalo de confiança usa a distribuição $t$ de [student_probable_error_1908]. [hoefler_belli_benchmarking_2015] sistematizam regras para relatar resultados de desempenho, entre elas tratar a execução repetida como unidade de medida, reportar a variabilidade por intervalos de confiança e preferir procedimentos não paramétricos quando os dados não seguem distribuição normal. Para estatísticas sem distribuição amostral conhecida, como os percentis de latência, o bootstrap aproxima essa distribuição por reamostragem dos dados observados, e o intervalo percentil toma os quantis da distribuição reamostrada [efron_tibshirani_bootstrap_1993]. O teste de [mann_whitney_1947] compara duas amostras sem supor normalidade e verifica se uma das variáveis tende a assumir valores maiores que a outra. Para proporções, [brown_cai_dasgupta_binomial_2001] mostram que o intervalo padrão baseado na aproximação normal da binomial tem cobertura errática, que se afasta do nível nominal de modo irregular, o que recomenda cautela no uso dessa aproximação.

### p. 29 · FUN-15 · ACRÉSCIMO · 2.4

Nota: Parágrafo do pré-registro (Nosek), com remissão às seções da nova medição e da avaliação do controlador. *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> Quando o mesmo pesquisador desenha, executa e analisa um experimento, decisões tomadas depois de ver os dados podem ser apresentadas, sem intenção, como previsões. [nosek_preregistration_2018] defendem o pré-registro como resposta: hipóteses, desfechos e plano de análise são fixados antes da coleta, o que separa a análise confirmatória da exploratória.

### p. 29 · FUN-16 · ACRÉSCIMO · 2.4

Nota: Parágrafo da estimativa de energia (Fan, Barroso e Hölzle, Kansal, cgroup v2).

> A estimativa de energia parte de modelos de potência por utilização. [fan_power_provisioning_2007] observam que a potência de um servidor varia de forma aproximadamente linear com a utilização da CPU, o que permite estimá-la por $P(u) = P_\text{ocioso} + (P_\text{máx} - P_\text{ocioso}) \cdot u$, em que $u$ é a utilização. [barroso_holzle_energy_proportional_2007] mostram que servidores consomem parcela elevada da potência de pico mesmo quando ociosos e passam a maior parte do tempo em utilização baixa ou moderada, de modo que a parcela ociosa pesa na energia por operação. Em máquinas virtuais, a potência não pode ser medida diretamente no hardware e precisa ser estimada por modelos alimentados por contadores de uso de recursos [kansal_vm_power_2010]. No Linux, a interface de grupos de controle versão 2 (cgroup v2) expõe o tempo de CPU acumulado por grupo de processos, o que permite atribuir o tempo de CPU a cada container [linux_cgroup_v2].

### p. 29–30 · FUN-17 · ACRÉSCIMO · 2.5

Nota: Frase do MAPE-K (Kephart e Chess).

> A referência conceitual comum a esses trabalhos é o laço monitorar-analisar-planejar-executar sobre uma base de conhecimento (MAPE-K), do gerente autônomo descrito por [kephart_chess_autonomic_2003].

### p. 30 · FUN-19 · CORREÇÃO · 2.6

Nota: O parágrafo do NIST 2017 (prazos de AAL1/2/3, vínculo criptográfico) foi trocado pelo texto da ATUAL com SP 800-63B-4 (2025), sem prazos por AAL; sai "quantitativas". Rodada 2: aplicada a reformulação CHK:160 (D-1), com citação única e sem parênteses duplos.

> A literatura normativa estabelece propriedades que esse ciclo deve satisfazer. A publicação SP 800-63B-4 do NIST, que substituiu a versão de 2017, define níveis de garantia do autenticador com prazos de reautenticação próprios de cada nível, trata da gerência de sessão em seção normativa, na qual exige que os segredos de sessão sejam invalidados no logout, e exige a limitação do número de tentativas de autenticação malsucedidas, o chamado rate limiting [nist_sp_800_63b4_2025, seções 2, 3.2.2 e 5]. Essas duas exigências fundamentam, respectivamente, a irreversibilidade da invalidação e o par formado pelo contador de tentativas e pelo bloqueio da identidade, objetos dos invariantes deste trabalho.

### p. 18 · FUN-20 · CORREÇÃO · 2.1

Nota: Raft: "presença em intervalo da ordem de dezenas de ms" vira AppendEntries vazias e tempo de difusão de 0,5 a 20 ms, uma ordem de grandeza abaixo do tempo-limite.

> o líder envia periodicamente mensagens de presença (AppendEntries sem entradas), e os autores recomendam que o tempo de difusão de uma mensagem, estimado entre 0,5 e 20 milissegundos, fique uma ordem de grandeza abaixo do tempo-limite de eleição

### p. 20 · FUN-21 · CORREÇÃO · 2.1

Nota: CvRDT: sem "elemento mínimo"; o supremo é comutativo, associativo e idempotente, e o estado cresce sob atualizações.

> o estado da réplica forma um semirreticulado, a fusão calcula o supremo $\sqcup$ de dois estados, que é comutativo, associativo e idempotente, e as atualizações só fazem o estado crescer na ordem parcial

### p. 20 · FUN-22 · CORREÇÃO · 2.1

Nota: CmRDT: "canais que entreguem pelo menos uma vez" vira difusão confiável em ordem causal, exatamente uma vez.

> Na abordagem por operação (CmRDT), as operações concorrentes comutam, e o artigo supõe uma difusão confiável em ordem causal, que entrega cada operação exatamente uma vez a cada réplica.

### p. 19 · FUN-23 · CORREÇÃO · 2.1

Nota: Localizador `\citep[seção 6]{decandia_dynamo_2007}` na configuração (3,2,2).

> [decandia_dynamo_2007, seção 6]

### p. 21 · FUN-24 · CORREÇÃO · 2.2

Nota: Armbrust: "externalização desacopla o estado..." vira separação entre computação sem estado e armazenamento com estado, com o IMDG nessa segunda camada.

> Em arquiteturas em nuvem, separar uma camada de computação sem estado de uma camada de armazenamento com estado é o que permite escalabilidade automática e alta disponibilidade [armbrust_cloud_2010], e um IMDG pode ocupar o papel dessa segunda camada.

### p. 21 · FUN-25 · CORREÇÃO · 2.2

Nota: Dentro de FUN-10: "custo típico" vira "uma escrita custa no máximo".

> uma escrita custa no máximo

### p. 22 · FUN-28 · CORREÇÃO · 2.2

Nota: Chave do consistent hashing: `infinispan_docs_stable` vira `\cite[seção 2.2.2]{infinispan_configuring_15}`.

> [infinispan_configuring_15, seção 2.2.2]

### p. 22 · FUN-29 · CORREÇÃO · 2.2

Nota: Esquema padrão de segmentos: "atribuições aproximadamente simétricas" vira mesmas chaves nos mesmos nós em todos os caches, com carga levemente desigual.

> O esquema padrão atribui as chaves aos mesmos nós em todos os caches, desde que todos os caches existam em todos os nós, ao custo de uma distribuição de carga levemente desigual [infinispan_configuring_15, seção 2.2.2]

### p. 24 · FUN-30 · CORREÇÃO · 2.3

Nota: "busca em largura ou profundidade" vira "busca em largura".

> por busca em largura

### p. 25 · FUN-31 · CORREÇÃO · 2.3

Nota: Caso Compaq: "erro sutil em caminho de poucas dezenas de passos..." vira especificação de 1.800 linhas, cerca de duas dezenas de erros, dois na RTL.

> registram o uso do TLC na especificação do protocolo de coerência de cache de um novo multiprocessador da Compaq, com cerca de 1.800 linhas, na qual o verificador encontrou perto de duas dezenas de erros, dois deles presentes também na implementação em RTL, que foi corrigida [yu_lamport_tla_modelchecking_1999, seção 7].

### p. 25 · FUN-32 · CORREÇÃO · 2.3

Nota: "64 ou 128 bits, probabilidade controlável de colisão" vira "64 bits, únicas com alta probabilidade".

> impressões digitais de 64 bits, únicas com alta probabilidade

### p. 25 · FUN-33 · CORREÇÃO · 2.3

Nota: `\citep{enea_must_2024}` após "redução parcial de ordem".

> [enea_must_2024]

### p. 26 · FUN-34 · CORREÇÃO · 2.3

Nota: Must: descrição restrita ao resumo (vários modelos de comunicação, DPOR, sistemas de produção).

> Must oferece uma interface de concorrência com vários modelos de comunicação, verifica os programas por redução parcial de ordem dinâmica e foi aplicada a sistemas de produção, como logs replicados e protocolos de transação distribuída [enea_must_2024].

### p. 26 · FUN-35 · CORREÇÃO · 2.3

Nota: Naskos: sai o formalismo (cadeias de Markov etc.); fica escalonamento horizontal ciente de custo em NoSQL.

> e aplica-se a decisões de escalonamento horizontal em bancos NoSQL ciente de custo [naskos_probabilistic_mc_2017]

### p. 27 · FUN-36 · CORREÇÃO · 2.3

Nota: Dentro de FUN-13: "quase todas" vira "98\% delas".

> 98% delas

### p. 27 · FUN-37 · CORREÇÃO · 2.4

Nota: Fontes de variabilidade de Dean e Barroso reescritas conforme o artigo (sem "falhas transitórias de hardware e topologia").

> As fontes de variabilidade apontadas pelos autores incluem disputa por recursos compartilhados na máquina e na rede, atividades de manutenção em segundo plano, como a coleta de lixo, filas em servidores e comutadores e mecanismos de economia de energia do hardware.

### p. 27 · FUN-38 · CORREÇÃO · 2.4

Nota: "2% a 5%" vira cerca de 5% (hedged, esperar o p95), exemplo de 2% para p99,9 de 1.800 a 74 ms, e menos de 1% (tied).

> Esperar até o p95 limita a carga adicional das requisições redundantes a cerca de 5%, e em um exemplo dos autores 2% de requisições a mais reduziram o p99,9 de 1.800 para 74 milissegundos; nas requisições atadas, a sobrecarga medida em uso de disco ficou abaixo de 1%.

### p. 28 · FUN-39 · CORREÇÃO · 2.4

Nota: Orçamento de erros: sai o exemplo 99,9%/30 dias e a taxa de consumo; fica a definição do capítulo SRE.

> O orçamento de erros é a taxa com que os SLOs podem deixar de ser cumpridos, acompanhada diariamente ou semanalmente, e seu consumo funciona como entrada para decisões operacionais.

### p. 30 · FUN-40 · CORREÇÃO · 2.5

Nota: "A literatura registra..." sem fonte vira atribuição a Ullah et al. (oscilação e acomodação).

> [ullah_control_elasticity_2018] apontam que controladores de elasticidade precisam evitar oscilação e se acomodar rapidamente após perturbações, e três fenômenos operacionais merecem atenção.

### p. 32 · FUN-41 · CORREÇÃO · 2.6

Nota: Armbrust: "prática consolidada em arquiteturas em nuvem" vira aplicação da separação computação sem estado / armazenamento com estado.

> Externalizar o estado de sessão em um IMDG ou armazenamento chave-valor aplica essa separação entre computação sem estado e armazenamento com estado [armbrust_cloud_2010]: múltiplas instâncias servem requisições de um mesmo cliente sob balanceamento de carga.


## Cap. 3 Metodologia

### p. 33 · MET-02 · ESTRUTURA · 3.1 e blocos `\section*` da 3.1

Nota: Labels da ATUAL definidos na 3.1 para manter refs externas: `ap-protocolo-ms` (após `sec-ms`, marcado), `ap-bases-strings` (parágrafo das bases), `sec-ms-execucao` (bloco Procedimento de seleção), `tab-ms-execucao` (na Tabela qd-prisma). Os três últimos não têm marcador (só label, sem texto).

> *(remoção: trecho da banca retirado neste ponto)*

### p. 38 · MET-04 · ACRÉSCIMO · Procedimento de seleção

Nota: Detalhe do que registra cada etapa (identificação, triagem, elegibilidade).

> A identificação registra, por base, a string aplicada, a data de execução e a quantidade de itens retornados. A triagem por título e resumo aplica os critérios de exclusão imediatamente verificáveis. A elegibilidade aplica a leitura completa, com registro do motivo de cada exclusão.

### p. 42 · MET-13 · CORREÇÃO · Operações modeladas (registro)

Nota: Frase da banca (nó atendente, diferença de estado antes/depois) trocada pela da ATUAL: servidor de conexão do cliente, código de retorno e chave (bate com o CSV real; decisão do autor, rodada 2).

> Cada operação registra marcas de tempo de início e fim com precisão de nanossegundos, o servidor de conexão do cliente, o código de retorno e a chave, e alimenta as métricas de latência e a detecção de violações descritas na Seção ⟨sec-metricas⟩.

### p. 42 · MET-14 · ACRÉSCIMO · Modelo de falhas (intro)

Nota: Enquadramento na taxonomia de Avizienis et al.; as 4 classes têm injetor; F1/F2 como ações do modelo e F3 como atraso. *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> As classes se enquadram na taxonomia de [avizienis_taxonomy_2004].

### p. 42 · MET-15 · ACRÉSCIMO · Modelo de falhas (F1)

Nota: Detectores FD_ALL3 e FD_SOCK2 e desativação da interface antes do encerramento do processo.

> A pilha de comunicação do cluster tem dois detectores de falha: o FD_ALL3, que suspeita de um nó depois de um intervalo sem mensagens de presença (T16), e o FD_SOCK2, que suspeita assim que a conexão TCP com o nó se fecha. Para que a queda seja silenciosa e detectada pelo FD_ALL3, a interface de rede do nó é desativada antes que o processo seja encerrado (Seção ⟨sec-protocolo-rebaseline⟩).

### p. 43 · MET-16 · ATUALIZAÇÃO · Modelo de falhas (F2)

Nota: Mantida a reserva ao TCC II; acrescentados isolamento de 1 nó e injetor implementado e verificado sem carga, ainda não exercido sob carga. *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> No experimento, um nó é isolado dos outros dois (minoria de um nó), e o fim do intervalo dispara a fusão das partições.

### p. 45–46 · MET-28 · CORREÇÃO · Métricas (critérios)

Nota: "sobre amostras pareadas por cenário" trocado por "sobre as amostras de cada cenário" (Mann-Whitney não é pareado).

> A primeira é redução estatisticamente significativa da taxa de violação, avaliada pelo teste não paramétrico de Mann-Whitney sobre as amostras de cada cenário ($\alpha = 0{,}05$).

### p. 46 · MET-30 · ACRÉSCIMO · Parâmetros, Tabela T2

Nota: Fonte acrescida de Yuan et al. (2014) e justificativa "falhas de produção reproduzíveis com até 3 nós" ("escopo TCC-I" mantido).

> [jaiman_tailx_2020, yuan_simple_testing_2014] & 3 & escopo TCC-I; tolerância mínima a 1 falha; falhas de produção reproduzíveis com até 3 nós

### p. 47 · MET-31 · CORREÇÃO · Parâmetros, Tabela T17

Nota: "dentro do timeout de T16" trocado por "maior que o timeout de T16" (60 s > 40 s), como na ATUAL (decisão do autor, rodada 2).

> maior que o timeout de T16; ativa convergência (I5, I6)

### p. 48 · MET-32 · ATUALIZAÇÃO · Parâmetros, Tabela T18 (valor adotado)

Nota: Valor da ATUAL: "+50 ms no p99 via tc-netem (lognormal no plano; normal na execução)". *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> $+50$ ms no p99 via tc-netem

### p. 48 · MET-33 · CORREÇÃO · Parâmetros, Tabela T18 (faixa a justificativa)

Nota: Linha T18 pela ATUAL: faixa "atraso com média, desvio e distribuição configuráveis", fonte netem_manpage (sai Ben Mokhtar), justificativa "valor de engenharia, sem fonte empírica; calibrável" (decisão do autor, rodada 2; P-032). MET-32 fica aninhado.

> atraso com média, desvio e distribuição configuráveis & [netem_manpage] & $+50$ ms no p99 via tc-netem & valor de engenharia, sem fonte empírica; calibrável

### p. 48 · MET-34 · ACRÉSCIMO · Parâmetros, Tabela T21

Nota: Faixa acrescida de "falhas de produção com até 3 nós" e fonte de Yuan et al. (2014).

> cardinalidades pequenas (2--3 por sort) em verificações industriais; falhas de produção com até 3 nós & [yu_lamport_tla_modelchecking_1999, newcombe_aws_tla_2014, yuan_simple_testing_2014]

### p. 48 · MET-35 · CORREÇÃO · Parâmetros, Tabela T22 (fonte)

Nota: Dean e Barroso trocados por Mann-Whitney (1947) e Efron e Tibshirani (1993).

> [mann_whitney_1947, efron_tibshirani_bootstrap_1993]

### p. 48 · MET-36 · ACRÉSCIMO · Parâmetros, fonte da tabela

Nota: Acrescidas Yuan, netem_manpage, Mann-Whitney e Efron-Tibshirani à lista da fonte.

> , [newcombe_aws_tla_2014], [yuan_simple_testing_2014], [netem_manpage], [mann_whitney_1947] e [efron_tibshirani_bootstrap_1993].

### p. 48 · MET-37 · CORREÇÃO · Parâmetros (parágrafo após tabela)

Nota: IC bootstrap atribuído a Efron e Tibshirani (não Dean e Barroso), sem "estreito"; "comparações pareadas ... via Mann-Whitney" trocado por "comparações ... podem ser feitas pelo teste de Mann e Whitney".

> base para um intervalo de confiança bootstrap [efron_tibshirani_bootstrap_1993]. Com 30 repetições, comparações entre cenários por percentil podem ser feitas pelo teste de [mann_whitney_1947], sem hipótese de normalidade,

### p. 49–52 · MET-39 · ESTRUTURA · Novo `\section*{Carga sintética dos cenários S1 e S2}` na 3.2

Nota: Bloco novo, texto integral da ATUAL (label `sec-carga`). *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> Os cenários S1 e S2 da linha T7 da Tabela ⟨tab-parametros-baseline⟩ sorteiam, a cada iteração de cada thread do programa de carga, uma operação com pesos fixos. Em S1, os pesos são 50 % de validação (O2), 25 % de login (O1), 10 % de logout (O3), 10 % de incremento de tentativas falhas (O4) e 5 % de reposição de tentativas (O5). Em S2, os pesos na mesma ordem são 95 %, 3 %, 1 %, 1 % e 0 %. O login abre uma tríade: quando ele é aceito, a mesma thread valida e encerra a sessão recém-criada, e os três eventos entram no registro. As validações e os logouts sorteados fora da tríade, chamados aqui de isolados, recaem sobre chaves de sessão escolhidas pela distribuição Zipfian de T6, no universo de chaves de T8. Antes do aquecimento de cada execução, o programa esvazia os dois caches do modo de replicação em uso, confere que ambos ficaram vazios e encerra a execução com código de erro próprio se isso não ocorrer. Cada thread $t$ usa, no login, oito identidades próprias, user-$t$-$k$ com $k = 0, \dots, 7$, tomadas em rodízio, que nunca recebem O4 nem O5. As operações O4 e O5 atuam sobre uma identidade separada por thread, lim-$t$, de modo que o bloqueio por limite continua exercitado sem atingir as identidades de login. As chaves lidas pelas operações isoladas existem no cache, conforme o esquema de gerações descrito a seguir. O rodízio não consome os geradores da mistura nem o de chaves. A versão da mistura adotada nas medições organiza as chaves de sessão em slots. O sorteio Zipfian define o slot $k$, e um vetor compartilhado entre as threads guarda a geração corrente $g_k$ de cada slot. Antes do aquecimento, uma fase de carga grava, para cada um dos $10^5$ slots, a geração 0 como sessão já invalidada por logout e a geração 1 como sessão válida, o que soma $2 \times 10^5$ entradas e fixa $g_k = 1$. A sessão do slot $k$ pertence à identidade user-$((k-1) \bmod T)$-$(\lfloor (k-1)/T \rfloor \bmod 8)$, em que $T$ é o número de threads, a mesma em todas as gerações do slot. A validação isolada lê a geração corrente, exceto quando um sorteio de Bernoulli independente, com probabilidade $p = 0{,}05$, a desvia para a geração anterior. Esse desvio representa um cliente que ainda apresenta um token antigo. O valor de $p$ é uma premissa de engenharia, declarada como tal, e não a medida de um serviço real. O logout isolado toma uma trava da faixa do slot ($k \bmod 1024$), invalida a geração corrente, que é a operação medida e registrada, grava como válida a geração $g_k + 1$ e só então avança $g_k$. Essa gravação de reposição não emite evento, não entra na latência medida e não passa pelo auditor. Se o logout retornar erro, não há reposição nem avanço. Se a reposição falhar, a geração não avança, e as validações seguem sobre a sessão já invalidada até o próximo logout do slot. O desenho tem três consequências declaradas. A fração de respostas Invalid entre as validações isoladas fica estacionária desde o primeiro evento, próxima de $p$ acrescida da corrida entre uma validação e um logout do mesmo slot. A reposição acrescenta ao cluster uma escrita por logout isolado, ausente do registro de eventos, em número próximo de 10 % das operações em S1 e de 1 % em S2. A trava serializa no cliente os logouts isolados de uma mesma faixa de slot, que portanto não concorrem entre si, e a espera pela trava fica fora da latência medida. Os critérios de estacionariedade foram fixados antes de qualquer execução dessa versão da mistura. A fração Invalid entre as validações isoladas pode diferir no máximo 1,0 ponto percentual entre o primeiro e o terceiro terço da janela de medição (critério C1) e deve ficar no intervalo $[4{,}5 %;\ 6{,}5 %]$ (C2). Com $n$ validações isoladas e $k$ leituras de token antigo, a execução deve satisfazer $|k/n - 0{,}05| \le 3{,}2905 \sqrt{0{,}05 \cdot 0{,}95 / n}$ (C3), região de aceitação do teste de $H_0\colon p = 0{,}05$ ao nível de 0,1 % pela aproximação normal da binomial, aplicada somente quando $n \cdot 0{,}05 \cdot 0{,}95 \ge 100$, limite adotado por cautela diante da cobertura errática dessa aproximação [brown_cai_dasgupta_binomial_2001]. As contagens de C3 lidas na saída do programa e no registro de eventos podem diferir em até quatro eventos, o número de threads, por causa da fronteira da janela de medição. Execução fora dessas tolerâncias é recusada. O logout e a cascata de invalidação do bloqueio administrativo (O6) invalidam sessões por escrita condicional à versão lida. O programa lê a sessão com sua versão e a substitui pela versão invalidada somente se a versão não tiver mudado; em caso de conflito, relê a sessão e tenta de novo, até três tentativas. A primeira invalidação vence, e o motivo registrado não muda depois. Uma invalidação que perde a escrita condicional e relê a sessão já invalidada é contada como invalidação perdida e não altera o estado. Se a sessão continuar válida, com versão alterada, depois da terceira tentativa, a operação termina com o código ERROR_TRANSPORT, e um contador próprio de conflitos esgotados separa esse caso dos erros de transporte na análise. Os caches permanecem não transacionais. O servidor emite uma vez por processo o aviso ISPN006010, segundo o qual operações condicionais devem ser usadas com caches transacionais porque, de outro modo, ``data inconsistency issues could arise under failure situations". O guia do cliente Hot Rod faz a mesma recomendação, para que no failover as operações condicionais não fiquem parcialmente aplicadas, com entradas conflitantes em nós diferentes [infinispan_hotrod_java_15], e o guia de configuração adverte que a divisão do cluster em partições pode causar perda ou inconsistência de dados qualquer que seja a estratégia de tratamento ou a política de merge [infinispan_configuring_15]. A configuração não transacional foi mantida porque a adoção de caches transacionais mudaria o sistema medido. Por isso, uma violação de I4 ou uma invalidação perdida observada sob falha (queda de nó, partição ou fusão de partições) é tratada na análise como efeito do grid, e não da aplicação. O login relê a identidade depois de gravar a sessão. Se a releitura encontrar a identidade bloqueada, o próprio login invalida a sessão recém-gravada com o motivo de bloqueio administrativo, pelo mesmo caminho condicional da cascata, e retorna recusa por bloqueio. Essa dupla checagem fecha, em DIST_SYNC, a corrida entre um login e um bloqueio concorrente da mesma identidade: o login escreve a sessão e lê a identidade, o bloqueio escreve a identidade e lê as sessões, e, com operações linearizáveis, ao menos um dos dois observa a escrita do outro. Em DIST_ASYNC, a releitura pode encontrar uma réplica atrasada, e o que restar dessa corrida é medida do grid. A releitura custa uma leitura a mais por login aceito, incluída na latência medida, e vale em todos os cenários, para que o login meça a mesma operação em todos eles. O auditor aplica mais três regras à contagem de violações. Uma validação que lê sessão válida sob identidade bloqueada é classificada pelo instante de início da leitura da sessão, comparado com dois marcos do bloqueio (a confirmação da identidade bloqueada e o fim da cascata de invalidação). A leitura anterior à confirmação conta como concorrente, apenas para diagnóstico; a leitura entre a confirmação e o fim da cascata recebe o rótulo de janela de cascata; a leitura sob cascata interrompida por erro recebe o rótulo de cascata incompleta; e somente a leitura posterior ao fim da cascata é registrada como I2. Dentro de uma mesma operação, cada par formado por invariante e sessão é registrado no máximo uma vez para I1 e I4. Um bloqueio cuja cascata esgota as tentativas termina em ERROR_TRANSPORT com a cascata parcial, e a contagem de invalidações perdidas inclui apenas a invalidação que perdeu a escrita condicional e releu a sessão invalidada. As tabelas de violação contam I1, I4 e I2 a partir do arquivo de violações de cada execução, e uma I2 só entra como violação quando a análise do rastro do cliente a classifica como real. Os rótulos de janela e de cascata incompleta e os contadores de leituras concorrentes são reportados à parte. A leitura de sessão válida sob identidade bloqueada sem marco de bloqueio deve ser nula em DIST_SYNC. Em DIST_ASYNC, uma leitura atrasada da identidade depois do desbloqueio produz esse caso por efeito do grid, e a contagem é reportada sem servir de critério. O registro de eventos distingue, na validação, as respostas VALID, INVALID e NOT_FOUND, e, no logout, a chave ausente (NOT_FOUND) da invalidação concluída. Ao fim de cada execução, um verificador de sanidade lê esse registro, conta os desfechos por operação na janela de medição e emite veredito sobre a coerência entre os desfechos observados e a mistura declarada. O veredito é desfavorável se não houver login, se a fração de logins aceitos entre aceitos e recusados por bloqueio ficar abaixo de 0,95, se nenhuma validação retornar VALID, se mais de 5 % das validações encontrarem a chave ausente, ou se a fração de alguma operação sorteada se afastar mais de 2,0 pontos percentuais do peso nominal do cenário. Esses limiares são fixados no programa de carga antes do piloto e não são ajustados depois de observados os dados. Como a campanha não registra marcadores de fase, as regras se aplicam a todos os eventos da janela de medição, inclusive aos do intervalo sob falha. O tipo de cada operação, a chave sorteada e a decisão de ler o token antigo vêm de três geradores java.util.Random próprios de cada thread, derivados da semente da execução, do índice $t$ da thread e da repetição $r$ pela expressão $s = semente + t + 1 000 003 \cdot r$. O gerador de chaves usa $s$ diretamente. Os outros dois combinam $s$ com um sal próprio por ou exclusivo ($\oplus$) e aplicam a função de mistura de 64 bits do gerador SplitMix [steele_lea_flood_splitmix_2014], na variante usada por java.util.SplittableRandom ($mix64$), de modo que as três sequências sejam distintas: o gerador da mistura recebe a semente $mix64(s \oplus SAL_MISTURA)$, e o do token antigo, $mix64(s \oplus SAL_TOKEN)$. Duas execuções com a mesma semente produzem, em cada thread, a mesma sequência de intenções, isto é, os mesmos tipos de operação, slots, decisões de token antigo e identidades de login. A intercalação entre threads, as respostas do grid, as latências, o número de operações por thread e o trecho exato coberto pela janela de medição não são reprodutíveis, porque dependem do tempo. Com mais de uma thread, o registro de eventos de duas execuções com a mesma semente não coincide, e a reprodutibilidade vale por thread.

### p. 52–53 · MET-40 · ESTRUTURA · Novo `\section*{Cenário S3: ...}` na 3.2

Nota: Bloco novo, texto integral da ATUAL (label `sec-carga-s3`). *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> Os cenários S1 e S2 sorteiam operações com pesos fixos e não contêm bloqueio administrativo nem afinidade entre cliente e nó, de modo que não exercitam a consistência de bloqueio (I2) nem a escrita feita em um nó isolado por partição. O cenário S3 simula uma aplicação web com os defeitos de uma aplicação real: o programa não repara estado, não reconcilia réplicas e não repete operações indefinidamente, e o experimento registra o que o data grid entrega nessas condições. Três servidores de aplicação, cada um ligado a um único nó do cluster, atendem usuários virtuais fixados a eles, como sob um balanceador com afinidade; um quarto cliente faz o papel de console administrativo. Cada usuário de um primeiro conjunto de identidades faz login, valida a sessão a cada página com tempo de pensar aleatório e, ao fim, encerra a sessão ou a abandona, caso em que a aplicação a considera expirada por inatividade e a sessão permanece válida no grid. Depois de um logout confirmado, o usuário repete validações da sessão encerrada por alguns segundos, como quem usa o botão de voltar do navegador, o que exercita I1 e I4. Um ator administrativo bloqueia, em intervalos aleatórios, uma identidade com sessão ativa e a desbloqueia depois de um tempo mínimo, o que exercita I2 com cascata de invalidação. Um segundo conjunto de identidades recebe tentativas falhas até o limite, com reposições ocasionais, e mantém o bloqueio por limite separado do administrativo. O cliente tem tempos-limite curtos e no máximo duas retentativas de aplicação, depois das quais a operação conta como falha percebida pelo usuário. As ações de cada usuário são agregadas em ações S3, com a latência percebida, que inclui as retentativas. Os tempos de pensar, as durações de sessão, os intervalos do administrador e os tamanhos dos conjuntos de identidades são premissas de modelagem, sem fonte empírica, e serão fixados no pré-registro da campanha que usar S3. O cenário S3 é o objeto da campanha com F2 e F4 sob carga e da avaliação do controlador (Seção ⟨sec-avaliacao-controlador⟩), enquanto a nova medição de referência usa apenas S1 e S2.

### p. 53–55 · MET-41 · ESTRUTURA · Novo `\section*{Protocolo da nova medição de referência}` na 3.2

Nota: Bloco novo, texto integral da ATUAL (label `sec-protocolo-rebaseline`). *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> A nova medição de referência (rebaseline) mede os dois modos de replicação da linha T1 na mesma campanha e com o mesmo jar: DIST_SYNC como controle e DIST_ASYNC como alvo. O protocolo é pré-registrado [nosek_preregistration_2018] antes de qualquer execução e não admite ajuste depois de observados os dados. Uma mudança gera registro novo, que cita o anterior e explica o motivo. A matriz cruza os cenários S1 e S2, com a carga da Seção ⟨sec-carga⟩, os dois modos e três condições (sem falha, F1 e F3), em dez repetições de 300 s por célula, o que soma 120 execuções, sem o controlador. Cada execução usa quatro threads e um cluster de três nós com a imagem quay.io/infinispan/server:15.0.21.Final, recriado do zero antes da campanha, com quatro caches: um de sessões e um de contadores em cada modo. O cenário S3 e as falhas F2 e F4 ficam fora da campanha. A janela de medição de cada execução vai de +44 s a +300 s. O aquecimento ocupa os primeiros 30 s, valor de $\max(30 \text{s},\ 10 % \cdot 300 \text{s})$, e a política de aquecimento descarta ainda 5 % do tempo restante, ou seja, 14 s. As falhas são disparadas no nó isn2 a +49 s do início do aquecimento, cinco segundos depois do início da medição, e a execução cujo disparo caia fora do intervalo de +48 s a +51 s é recusada. F1 desativa a interface eth0 no network namespace de isn2 e, em seguida, encerra o processo do nó com o sinal SIGKILL (podman kill), sem saída ordenada. Após 60 s, o container é reiniciado com podman start. O nó fica fora de cerca de +49 s a cerca de +109 s, intervalo maior que o timeout de 40 s do detector FD_ALL3 (T16). A desativação da interface antes do encerramento impede que o FD_SOCK2 perceba o fechamento das conexões e detecte a queda em poucos segundos. F3 aplica tc-netem na interface eth0 de isn2, com atraso de 20 ms e desvio de 13 ms sob distribuição normal, por 251 s, até o fim da execução. A ordem de execução segue blocos completos aleatorizados. Cada uma das dez rodadas executa as doze células uma vez, em ordem sorteada por gerador com semente fixada no registro, de modo que a deriva do ambiente ao longo da noite se distribua entre as células e que uma interrupção preserve rodadas completas. Uma rodada piloto com as doze células precede a campanha e serve para verificar o orquestrador, os critérios e os tempos; seus dados não entram em resultado. A execução de índice $i$ usa a semente $50 000 + 100 \cdot i$, e nenhum par formado pela semente da thread e pela repetição se repete na campanha, no piloto ou nas reposições. As execuções rodam em máquina virtual Linux sob Hyper-V, sem coleta de uso de CPU durante a campanha. Cada execução passa por critérios de recusa fixados antes do piloto. Antes da carga, os três nós precisam estar saudáveis há pelo menos 60 s, sem rebalanceamento em curso e sem disciplina tc-netem residual, e os dois caches precisam começar vazios. Depois da execução, exigem-se término normal do programa de carga, veredito favorável do verificador de sanidade, carga inicial completa, os critérios C1 a C3 da mistura e a confirmação de que a falha ocorreu como previsto. Nas células com falha, C1 e C2 usam como denominador apenas as validações isoladas com resposta VALID ou INVALID, e os erros são reportados à parte. Nas células sem falha, exigem-se também ausência de validação com NOT_FOUND, ao menos 99 % de logins aceitos e ausência de reposições com erro, de invalidações perdidas e de conflitos esgotados. Nas células com falha, esses valores são apenas reportados. Em DIST_SYNC sem falha, qualquer registro de I1, I2 ou I4, leitura sem marco de bloqueio ou login revertido pela dupla checagem interrompe a campanha para investigação. Em DIST_ASYNC, essas contagens são objeto de medição e são reportadas. O aviso ISPN006010 pode aparecer no máximo uma vez por processo de nó, e o total por nó não pode exceder o número de vezes em que o processo foi iniciado, regra que acomoda o reinício de isn2 em F1. A campanha é interrompida automaticamente diante de duas recusas na mesma célula, de mais de seis recusas no total ou de falha de infraestrutura, e uma recusa isolada é reposta no fim da campanha com o próximo índice livre. A unidade estatística é a execução. Para cada execução e operação, a análise calcula p50, p95 e p99 das latências registradas na janela de medição, pelo método do posto mais próximo, sobre os desfechos válidos de cada operação: login aceito, validação com VALID ou INVALID, logout concluído, incremento contabilizado e reposição de tentativas concluída. Os demais desfechos entram nas taxas de erro por código. O p99 só é calculado com ao menos 1 000 eventos válidos da operação na execução, e o p99,9 só com ao menos 10 000, sem contraste. Os contrastes comparam F1 e F3 com a condição sem falha, por cenário, modo e operação, e DIST_ASYNC com DIST_SYNC, por cenário, condição de falha e operação. A estimativa de cada contraste é a diferença entre as médias dos p99 por execução das duas células, com intervalo de confiança de 95 % por bootstrap percentil [efron_tibshirani_bootstrap_1993] com 10 000 reamostragens de execuções, feitas com reposição e de forma independente em cada célula. O contraste só é estimado quando cada célula tem ao menos oito execuções aceitas. O teste de [mann_whitney_1947] bilateral sobre os p99 por execução, com $\alpha = 0{,}05$, é secundário. Os intervalos são reportados sem correção para comparações múltiplas, com a contagem de comparações declarada, e nenhuma conclusão depende de uma comparação isolada no limite da significância.

### p. 55–56 · MET-42 · ESTRUTURA · Novo `\section*{Estimativa de energia}` na 3.2

Nota: Bloco novo, texto integral da ATUAL (label `sec-energia`). *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> A máquina virtual Hyper-V que hospeda o cluster não expõe ao sistema convidado os contadores de energia do processador, de modo que a energia não pode ser medida, apenas estimada por modelo [kansal_vm_power_2010]. O método previsto lê, a cada segundo, o tempo de CPU acumulado e a memória de cada container Infinispan pela interface cgroup v2 [linux_cgroup_v2], o tempo de CPU do processo do programa de carga, que inclui o controlador, e o uso total de CPU da máquina virtual, com o tempo tomado pelo hipervisor (steal) registrado à parte. O relógio dessas leituras é o mesmo dos marcadores de falha, o que permite recortar o consumo por fase da execução. A métrica principal é medida, e não estimada: tempo de CPU por mil operações bem-sucedidas em S1 e S2 e por mil ações S3 sem falha percebida, acompanhado do retrabalho, contado em retentativas e operações com erro por ação útil. A conversão em energia usa o modelo linear de [fan_power_provisioning_2007], com a potência ociosa e a potência máxima tomadas da especificação do processador do hospedeiro. A estimativa é reportada em duas parcelas: a dinâmica, proporcional ao tempo de CPU, e a total, que inclui a parcela ociosa, cujo peso [barroso_holzle_energy_proportional_2007] destacam. A razão entre potência ociosa e máxima varia em $\{0{,}3;\ 0{,}5;\ 0{,}7\}$ como análise de sensibilidade. As conclusões são apenas relativas, entre os braços de uma comparação, e nunca em joules absolutos. O método tem limitações declaradas: o tempo de CPU virtual não equivale ao trabalho do núcleo físico; memória, disco e rede não entram na conta; e reduzir a carga reduz o consumo por definição, razão pela qual a métrica é normalizada por operação útil e a vazão útil tem margem de não inferioridade na avaliação do controlador. Uma execução com steal acima de 5 % do tempo fica fora da comparação de CPU.

### p. 56–57 · MET-43 · ESTRUTURA · Novo `\section*{Desenho da avaliação do controlador}` na 3.2

Nota: Bloco novo, texto integral da ATUAL (label `sec-avaliacao-controlador`). *(Parte deste trecho foi retirada em 01/11; ver `TRECHOS-PARA-DESENVOLVIMENTO.md`.)*

> O pré-registro da avaliação do controlador heurístico, com os valores finais, precede qualquer execução. A comparação ocorre em DIST_ASYNC, o modo alvo, com o cenário S3, e opõe dois braços que executam o mesmo código: no braço A, o controlador observa e decide sem atuar (modo shadow); no braço B, ele aplica as decisões (modo active). Assim, o custo de observação é o mesmo nos dois braços, e só a atuação difere. São quatro células: sem falha, para medir sobrecarga e acionamentos indevidos; F2 com o nó isn3 isolado por 90 s; F4 com perda de 20 % (severidade N3); e F1 sobre isn2. Cada célula recebe oito blocos pareados, e cada bloco contém uma execução de cada braço com a mesma semente de carga, em ordem sorteada e com o cluster reiniciado entre execuções. Os três primeiros blocos funcionam como piloto e são aproveitados se o protocolo não mudar. Duas células em DIST_SYNC (F2 e F4), com quatro blocos cada, servem de referência, sem pretensão de teste, para indicar se o efeito depende do modo. O total é de cerca de 80 execuções, estimadas em 16 a 27 horas de máquina. O desfecho primário de cada célula é o p99 da latência percebida das ações S3, que inclui as retentativas, nas fases durante a falha e de recuperação, calculado por execução. Para cada bloco $b$, calcula-se $d_b = \ln(p99_B / p99_A)$, e a estimativa é a média dos $d_b$, reportada como razão. O intervalo de confiança primário de 95 % é o intervalo $t$ sobre os $d_b$ [student_probable_error_1908], com o bootstrap sobre blocos como análise de sensibilidade [efron_tibshirani_bootstrap_1993]. O teste é de permutação exata por troca de sinal dos $d_b$ ([fisher_design_experiments_1935], [fisher_design_experiments_1935], seção 21; [good_permutation_tests_2005], [good_permutation_tests_2005]), com $\alpha = 0{,}05$ por célula; com oito blocos, há $2^8 = 256$ arranjos, e o menor valor-$p$ bilateral possível é $2/256 \approx 0{,}008$. Os desfechos secundários (p99 por operação, taxa de erro, falhas percebidas, violações de I1, I2 e I4, tempo de convergência e divergência persistente de I5 e I6, tempo de CPU e energia estimada) seguem o mesmo procedimento, com correção de [holm_sequential_1979] para múltiplas comparações dentro da célula. Como as contagens de violação podem ser nulas, a análise principal dessas contagens é a diferença de taxas entre os braços, e uma contagem nula é relatada como ``não observada em $N$ ações", com limite superior de 95 % próximo de $3/N$ [hanley_lippmanhand_zero_1983], e nunca como ausência do fenômeno. A vazão útil, em ações S3 sem falha percebida por segundo, entra como critério de não inferioridade: o limite inferior do intervalo de 95 % da razão entre os braços deve ficar acima de $1 - \delta$, com $\delta = 10 %$. Esse critério impede que o controlador melhore a cauda apenas por reduzir a carga. A rastreabilidade, terceiro critério da Proposta, permanece: cada decisão grava os sinais, os limiares em vigor, a regra aplicada e o perfil resultante. Os limiares do controlador serão calibrados com dados da campanha com o cenário S3, por regra fixada antes de ver esses dados, e as execuções de calibração não entram na avaliação.

### p. 58–59 · MET-46 · ACRÉSCIMO · Trabalhos correlatos (Nguyen et al.)

Nota: Detecção medida em cenário projetado para violações frequentes; mecanismos alternativos propostos pelos autores.

> A taxa de detecção acima foi medida em cenário projetado para provocar violações frequentes. Para o caso de reversões frequentes, os autores propõem mecanismos alternativos, como adiamento da resolução de violação ou avanço sob consistência mais restrita por janelas curtas.

### p. 59 · MET-48 · CORREÇÃO · Trabalhos correlatos (Nguyen et al.)

Nota: `nist_sp_800_63b_2017` trocada por `nist_sp_800_63b4_2025`.

> [nist_sp_800_63b4_2025, owasp_session_mgmt_cheatsheet]

### p. 48 · MET-49 · CORREÇÃO · Parâmetros, fonte da tabela

Nota: Removidas Ben Mokhtar (sai de T18, MET-33) e Liu et al. (sai de T11, MET-52) da lista da fonte.

> *(remoção: trecho da banca retirado neste ponto)*

### p. 47 · MET-50 · CORREÇÃO · Parâmetros, Tabela T6

Nota: "(padrão Cassandra)" trocado por "(YCSB)".

> Zipfian $\rho=0{,}99$ (YCSB); uniforme; latest

### p. 47 · MET-51 · CORREÇÃO · Parâmetros, Tabela T9

Nota: Faixa "10^5–10^7" trocada por "10^6–10^7".

> $10^6$--$10^7$

### p. 47 · MET-52 · CORREÇÃO · Parâmetros, Tabela T11

Nota: Retirado "sufficient warm-up" e Liu et al.; faixa só "fases de load e run separadas em YCSB", fonte só Cooper et al.

> fases de load e run separadas em YCSB & [cooper_ycsb_2010]

### p. 47 · MET-53 · CORREÇÃO · Parâmetros, Tabela T14

Nota: Faixa "10–30 min em estudos derivados de YCSB" trocada por "não reportada (estudos fixam número de operações)".

> não reportada (estudos fixam número de operações)

### p. 47 · MET-54 · CORREÇÃO · Parâmetros, Tabela T16

Nota: Faixa acrescida de "padrão da pilha JGroups do servidor (conferido na configuração do cluster)" e fonte trocada de infinispan_docs_stable por "O autor" (segunda opção do relatório).

> heartbeat 8000 ms, timeout 40000 ms, padrão da pilha JGroups do servidor (conferido na configuração do cluster) & O autor

### p. 33 · MET-55 · CORREÇÃO · 3.1 (introdução, Petersen)

Nota: "categoriza os trabalhos selecionados a partir de leitura completa" trocado pela etapa de palavras-chave dos resumos (keywording).

> classifica os trabalhos por palavras-chave extraídas dos resumos, recorrendo a outras partes do texto quando o resumo não basta, e, por fim, extrai e sintetiza os dados

### p. 36 · MET-56 · CORREÇÃO · Critérios de inclusão e exclusão (CE7)

Nota: Acrescentada `\citep{fischer_lynch_paterson_flp_1985}` na frase do CE7.

> CE7 elimina itens anteriores a 1985, ano do resultado de impossibilidade do consenso assíncrono [fischer_lynch_paterson_flp_1985], ressalvada

### p. 43 · MET-57 · CORREÇÃO · Modelo de falhas (bizantinas)

Nota: "os protocolos do Infinispan assumem canal honesto" trocado por "o modelo de falhas deste trabalho supõe canal honesto".

> dado que o modelo de falhas deste trabalho supõe canal honesto

### p. 46 · MET-58 · CORREÇÃO · Parâmetros (intro)

Nota: Citação tripla do Infinispan reescrita como `(\citeauthor, \citeyear, \citeyear, \citeyear)` na ordem docs_stable, metrics, tuning.

> ([infinispan_docs_stable], [infinispan_docs_stable], [infinispan_metrics], [infinispan_tuning])

### p. 58 · MET-59 · CORREÇÃO · Trabalhos correlatos (Newcombe)

Nota: Relato de defeitos atribuído também a Newcombe et al. (2015): "\citet{newcombe_aws_tla_2014} e \citet{newcombe_aws_formal_methods_2015} relatam".

> [newcombe_aws_tla_2014] e [newcombe_aws_formal_methods_2015]

### p. 58 · MET-60 · CORREÇÃO · Trabalhos correlatos (Must)

Nota: Descrição de Must reduzida ao que o resumo sustenta; removida também a frase seguinte ("Esse desenho sustenta a modelagem do canal ... e a representação dos eventos de falha"), que dependia do detalhe retirado.

> Must oferece uma interface de concorrência com vários modelos de comunicação, verifica os programas por redução parcial de ordem dinâmica e foi aplicada a sistemas de produção, como logs replicados e protocolos de transação distribuída [enea_must_2024].

### p. 59 · MET-61 · CORREÇÃO · Trabalhos correlatos (Liu et al.)

Nota: Retirado "e embasa o procedimento de warm-up registrado como parâmetro T11".

> O estudo representa a adaptação por realimentação sobre métricas observadas.


## Cap. 4 Desenvolvimento e Resultados Parciais

### p. 61 · DES-01 · ATUALIZAÇÃO · 4.1 Arquitetura experimental

Nota: Novo parágrafo após o 2º: Podman rootful/netavark em VM Ubuntu 24.04 (Hyper-V), WSL2 substituído por falta de sch_netem, JGroups TCP e pilha FD_ALL3 + FD_SOCK2.

> Na versão atual, os containers Podman executam no modo rootful, sobre a rede netavark, em uma máquina virtual Ubuntu Server 24.04 sob Hyper-V. O ambiente WSL2 usado no início não tinha o módulo de kernel sch_netem, necessário às falhas de rede, e foi substituído pela máquina virtual. A descoberta de membros usa JGroups com transporte TCP e lista explícita de nós, e a pilha de detecção de falhas inclui FD_ALL3 e FD_SOCK2 (Seção ⟨sec-falhas⟩).

### p. 61 · DES-02 · CORREÇÃO · 4.1 Arquitetura experimental

Nota: "nó atendente" vira "servidor de conexão do cliente" (decisão do autor, rodada 2).

> servidor de conexão do cliente

### p. 61 · DES-03 · ATUALIZAÇÃO · 4.1 Arquitetura experimental

Nota: Frase acrescentada: a auditoria de I5/I6 virou laço de convergência após o fim da carga (rodadas de 5 s), não usado na nova medição.

> Na versão atual, a verificação de I5 e I6 é um laço de convergência executado depois que a carga cessa, com rodadas a cada 5 s, laço que a nova medição de referência não usa.

### p. 62 · DES-04 · ATUALIZAÇÃO · 4.2 Especificação formal inicial

Nota: Parágrafo novo: módulo MCSession.tla, registro único com ts por réplica, filas por réplica (a banca diz multiset), regra de entrega por ts maior, que não é o mecanismo do Infinispan.

> Na versão atual, a especificação está acompanhada de um módulo de verificação, spec/MCSession.tla, que declara os cenários e as propriedades verificadas. Cada sessão e cada identidade é um registro único por réplica, com uma marca de tempo ($ts$), espelhando o objeto imutável que o programa de carga grava como unidade no data grid. As ações correspondem às operações O1 e O3 a O7 da Seção ⟨sec-operacoes⟩. Cada escrita local é difundida às demais réplicas por filas de mensagens pendentes, uma por réplica, e qualquer mensagem pendente pode ser entregue a qualquer momento, o que captura atraso e reordenação. Na entrega, a réplica aplica o registro recebido somente se a marca de tempo da mensagem for maior que a local. Essa regra é um dispositivo de modelagem para evitar regressões arbitrárias no canal abstrato, e não o mecanismo de resolução de conflitos do Infinispan, que sob partição segue a política de fusão configurada.

### p. 62–63 · DES-05 · ATUALIZAÇÃO · 4.2 Especificação formal inicial

Nota: Parágrafo novo (literal da ATUAL) com a evolução da especificação: set/out 2026, ações Cair/Reviver/Particionar/Mesclar, variantes com perda e com relógio pelo máximo.

> A especificação evoluiu em etapas. A versão do Trabalho de Conclusão de Curso I não modelava falhas. Em setembro de 2026, a entrega passou a difundir cada escrita a todas as réplicas, a comparação de marcas de tempo foi corrigida e a cascata de invalidação do bloqueio administrativo foi refeita. No início de outubro, uma revisão do mecanismo de conflito substituiu as variáveis independentes por campo pelo registro único por chave e retirou o desempate entre réplicas, que deixara de ter função. Em seguida, o modelo ganhou as ações de falha: Cair e Reviver, que representam a queda e a recuperação de uma réplica (F1), e Particionar e Mesclar, que representam a partição e a fusão (F2). Na fusão, o lado com mais réplicas (ou, em empate, qualquer dos lados) copia o registro inteiro de uma de suas réplicas para as do outro lado, sem comparação de marcas de tempo. Duas variantes posteriores, uma com perda de mensagens no canal e outra com relógio atualizado pelo máximo na entrega, foram verificadas em cópias separadas da especificação, ainda não incorporadas à versão principal. A variante com perda tem caráter ilustrativo, porque o modelo não tem retransmissão, e nenhuma das duas corrige o problema de I5 e I6 descrito adiante.

### p. 63 · DES-06 · ATUALIZAÇÃO · 4.3 (abertura)

Nota: Frase acrescentada: o diagnóstico de out/2026 mostrou que a bateria não mediu o ciclo de vida de sessões.

> Um diagnóstico dos registros brutos, feito em outubro de 2026, mostrou que essa bateria não mediu o ciclo de vida de sessões, como descrito adiante.

### p. 63–64 · DES-07 · CORREÇÃO · 4.3 / Verificação formal

Nota: Parágrafo novo: as quatro configurações atuais (tiny/small/medium/full), as cotas, runs/tlc/ e a nota de correção do TLC (o texto anterior trazia a expectativa pré-registrada).

> Com a inclusão das falhas no modelo, a verificação dos invariantes I1 a I6 passou a usar quatro configurações de cardinalidade, todas com no máximo uma mensagem pendente por fila, relógio limitado a três e no máximo um evento de falha por execução. A configuração tiny tem uma identidade, duas sessões, duas réplicas e limite de bloqueio igual a dois; small tem duas identidades, três sessões e duas réplicas; medium tem três identidades, três sessões e duas réplicas; e full tem três identidades, três sessões e três réplicas. As saídas brutas do TLC estão em runs/tlc/ no repositório. A versão anterior deste texto reproduziu uma expectativa registrada antes da execução, e não o resultado medido. Os números a seguir vêm dos registros de saída do verificador.

### p. 64–65 · DES-08 · CORREÇÃO · 4.3 / Verificação formal

Nota: Removidos o parágrafo de resultados da banca (I1 em 6 passos, I2 em 7, I5/I6 só por mensagens em trânsito, ~91 milhões de estados para I3/I4) e as duas primeiras frases do parágrafo seguinte ("achado legítimo do protocolo otimista"). Entram os resultados reais da ATUAL: I1/I2 em traços de 3 a 5 estados, propriedades por réplica, I5/I6 por empate de ts (defeito de modelagem) e por fusão, I3/I4 sem violação com 570.089 estados em tiny.

> Os invariantes I1 e I2 são violados em traços curtos. I1 é violado em traço de 4 estados nas configurações tiny, small e medium e de 5 estados em full; I2, em traço de 4 estados em tiny, small e medium e de 3 estados em full. Nenhum desses traços contém ação de falha. As violações decorrem da forma das fórmulas, avaliadas sobre o estado de todas as réplicas: basta que uma réplica tenha aplicado a invalidação ou o bloqueio e outra ainda não, situação normal enquanto há mensagens em trânsito. As violações de I1 e I2 nessa forma são, portanto, consequência direta da divergência transitória e não informam sobre o protocolo. Para separar esse efeito, o módulo de verificação acrescentou propriedades avaliadas réplica a réplica. Essas propriedades foram verificadas em um cenário semeado, com uma identidade, duas sessões já válidas, três réplicas, login desabilitado e cotas maiores (até três mensagens por fila, relógio até cinco e dois eventos de falha). A versão de I1 por réplica afirma que uma sessão inválida em uma réplica não passa a outro estado nessa réplica no passo seguinte. No cenário semeado, ela não foi violada sem falha nem com queda, de forma exaustiva com cotas reduzidas e até o tempo-limite com as cotas do plano, e foi violada sob partição, em traço de 4 estados. Esse resultado é coerente com a perda, na fusão, de escritas feitas na partição menor entre a divisão e sua detecção, descrita pela documentação do Infinispan [infinispan_configuring_15, seção 7.1.1], com duas ressalvas: no modelo, a escrita da réplica isolada é anterior à partição e ainda não propagada, aproximação daquela janela, e a fusão do modelo é uma abstração da política real. O comportamento real sob partição com carga ainda não foi medido. No modelo com login habilitado, a versão de I1 por réplica também é violada sem falha e com queda, por artefatos do modelo. O relógio de cada réplica não acompanha as marcas recebidas, e, na variante que corrige esse ponto, a violação vem de logins concorrentes da mesma sessão em réplicas diferentes. Nenhum dos dois ocorre no programa de carga, que usa a versão do registro lido e identificador único de sessão. A versão de I2 por réplica, segundo a qual uma identidade bloqueada não tem sessão válida na mesma réplica, é violada em traço de 3 estados em todos os cenários, porque o bloqueio por limite de tentativas, por decisão de domínio, não invalida as sessões existentes. Esse caso é tratado como violação esperada por desenho, e a análise experimental o separa do bloqueio administrativo. A versão de I4 por réplica, que exige que o motivo de invalidação não mude, também é violada. Os invariantes I5 e I6 são violados nas quatro configurações, em traços de 5 a 7 estados, por dois mecanismos. Em cinco dos oito traços mínimos, duas escritas concorrentes na mesma chave recebem a mesma marca de tempo, porque cada réplica atribui marcas pelo próprio contador, e a regra de entrega, que exige marca estritamente maior, faz cada réplica recusar o registro da outra. A divergência persiste depois que as filas se esvaziam. Esse é um defeito de modelagem, surgido com a retirada do desempate entre réplicas, e sua correção, por reintrodução de um desempate ou por canal com entrega em ordem, está pendente. Nos outros três traços (I5 em tiny e I6 em small e em full), a fusão copia o registro do lado vencedor sobre uma escrita local ainda não propagada, e as mensagens dessa escrita, entregues depois, alcançam só as outras réplicas, o que deixa divergência permanente. Esse segundo mecanismo é um resultado do modelo sob partição, coerente com a seção 7.1.1 da documentação citada acima e independente da correção do empate. Até essa correção, os contraexemplos de I5 e I6 que não passam pela fusão não informam sobre o protocolo. As formas de I3 e I4 declaradas no modelo, contador dentro da faixa de 0 ao limite e motivo dentro do conjunto declarado, não foram violadas. A verificação foi exaustiva apenas na configuração tiny, com 570 089 estados distintos, 11 243 221 estados gerados e profundidade 21. Nas configurações maiores, as execuções foram interrompidas pelo tempo-limite sem violação. Em small, por exemplo, a verificação de I3 percorreu 30 474 782 estados distintos em cerca de 9 minutos, e em full, 5 466 292 estados distintos em cerca de 4 minutos. Essas formas decorrem da tipagem e não verificam a monotonicidade do contador nem a imutabilidade do motivo. Esta última, avaliada réplica a réplica, é violada, como descrito acima.

### p. 65–66 · DES-09 · ATUALIZAÇÃO · 4.3 / Verificação formal

Nota: Frase após o plano de refinamento da banca: a pendência atual é a correção do empate de ts, e o catálogo relaciona operação, falha e invariante.

> Na versão atual, a pendência da especificação é a correção do empate de marcas de tempo descrita acima, e o catálogo passou a relacionar operação, falha e invariante, base do alinhamento entre o modelo e os experimentos previsto no Apêndice ⟨ap-plano-tcc2⟩.

### p. 66 · DES-10 · CORREÇÃO · 4.3 / Baseline experimental

Nota: A frase da banca "O crash de F1 seguiu por podman stop e podman start..." foi trocada pela da ATUAL: F1 usou podman stop/start, isto é, parada ordenada, e não queda silenciosa (decisão do autor, rodada 2; as demais menções ao F1 executado estão em DES-32 e DES-33).

> A falha F1 usou podman stop e podman start sobre o mesmo contêiner isn2, isto é, uma parada ordenada, e não a queda silenciosa definida na Seção ⟨sec-falhas⟩.

### p. 66 · DES-11 · CORREÇÃO · 4.3 / Baseline experimental

Nota: Removida a frase "Os números de F1 aqui reportados substituem os do piloto inicial executado no WSL2", que remetia às tabelas retiradas.

> *(remoção: trecho da banca retirado neste ponto)*

### p. 66 · DES-12 · CORREÇÃO · 4.3 / Baseline experimental

Nota: "perfil DIST_ASYNC" vira "em DIST_SYNC", com a explicação de que só havia caches síncronos e de que todas as execuções na VM até out/2026 rodaram nesse modo (decisão do autor, rodada 2).

> em DIST_SYNC e numOwners=2. A configuração do cluster só tinha caches síncronos, e todas as execuções na máquina virtual até outubro de 2026, incluindo as seis células desta bateria, ocorreram nesse modo.

### p. 67 · DES-13 · CORREÇÃO · 4.3 / Baseline experimental

Nota: Removidos os parágrafos de interpretação (erro 0%, F1 gracioso, F3 custoso, ressalva de IncrementFailure, leitura conjunta com dean_barroso_tail_2013) e as Tabelas F1/F3 de p99. Entram os dois parágrafos da ATUAL sobre o defeito (Login 100% BLOCKED, Validate 100% NONE, quatro causas) e a nova medição pré-registrada. A frase da banca sobre os artefatos em runs/ foi mantida.

> Um diagnóstico dos registros brutos, feito em outubro de 2026, mostrou que a bateria não mediu o ciclo de vida de sessões. Em todas as seis células, 100 % dos logins retornaram recusa por bloqueio, e 100 % das validações retornaram ausência de resultado (código NONE), que encobria a leitura de chave ausente; na célula S1 sem falha, por exemplo, os 1 291 332 logins registrados foram todos recusados. Nenhum evento registrou código de erro. Login e validação somaram 75 % dos eventos em S1 e 98 % em S2. Quatro causas, todas de desenho da carga e da análise, explicam o resultado. A primeira é a identidade única de cada thread, que atingia o limite de tentativas e permanecia bloqueada, já que a mistura não continha desbloqueio. A segunda é a leitura, pelas validações e logouts isolados, de chaves sintéticas que nenhuma operação gravava. A terceira é a ausência de limpeza do cluster entre execuções, de modo que as identidades já estavam bloqueadas no primeiro evento. A quarta é o mascaramento: o programa registrava a chave ausente com o mesmo código de ausência de resultado, e o script de percentis contava recusas e ausências como sucesso, o que ocultou o problema nos resumos. As latências medidas nessa bateria correspondem, portanto, à recusa de login, à leitura de chave ausente e às escritas do contador sobre identidades já bloqueadas, e não às operações que os rótulos indicam. Por isso, a versão anterior deste capítulo, que apresentava os percentis de latência dessa bateria por operação e condição de falha, foi revista: as tabelas foram retiradas, e nenhum número de latência de junho é usado como resultado ou será comparado com os da nova medição. A ausência de erros e de violações nessa bateria também não indica failover sem efeito, porque nenhuma sessão foi criada. A carga foi corrigida nos pontos descritos na Seção ⟨sec-carga⟩: limpeza e conferência dos caches antes de cada execução, identidades de login separadas das que recebem tentativas falhas, chaves de sessão gravadas antes da medição, códigos distintos para sessão válida, inválida e ausente, e um verificador de sanidade que recusa execuções incoerentes com a mistura declarada. A nova medição de referência, com essa carga, foi pré-registrada (Seção ⟨sec-protocolo-rebaseline⟩) e ainda não foi executada.

### p. 68 · DES-14 · ATUALIZAÇÃO · 4.3 / Baseline experimental

Nota: Após "permanece como protocolo desenhado": o protocolo deu lugar à nova medição pré-registrada (SYNC e ASYNC, 10×300 s, F1 silenciosa), ainda não executada.

> Esse protocolo deu lugar à nova medição de referência pré-registrada (Seção ⟨sec-protocolo-rebaseline⟩), que mede DIST_SYNC como controle e DIST_ASYNC como alvo na mesma campanha, em dez repetições de 300 s por célula, com F1 por queda silenciosa, e que ainda não foi executada.

### p. 68 · DES-15 · CORREÇÃO · 4.3 / Baseline experimental

Nota: Trecho de T18 "compatível com observações em redes inter-zona [benmokhtar_taming_tail_2021]" trocado pela redação da ATUAL: valor de engenharia, sem fonte empírica, calibrável, via tc-netem [netem_manpage] (decisão do autor, rodada 2).

> O atraso de canal de F3 segue calibrado para deslocar o percentil 99 em $50$ ms (T18), via tc-netem [netem_manpage], valor de engenharia, sem fonte empírica, e calibrável.

### p. 68 · DES-16 · ATUALIZAÇÃO · 4.3 / Baseline experimental

Nota: Após "F2 e state transfer (F4) seguem reservados": F2 ganhou injetor, F4 virou degradação parcial da rede, verificação sem carga e uso sob carga na campanha S3.

> Esse plano foi modificado: F2 deixou de ser reservada e ganhou injetor, e F4 foi redefinida como degradação parcial da rede (Seção ⟨sec-falhas⟩). Os dois injetores passam pelas verificações sem carga de aplicação descritas a seguir, e a campanha com o cenário S3 os exercita sob carga.

### p. 68–69 · DES-17 · ESTRUTURA · 4.3 (novo \subsection*)

Nota: Bloco não numerado "Verificação dos injetores na máquina virtual" (label sec-verificacao-injetores), com os dois parágrafos da Seção 4.4 da ATUAL.

> Antes de usar os injetores de F2 e F4 sob carga, cada um passa por uma sequência de verificações na máquina virtual, sem carga de aplicação, em que cada etapa só começa depois que a anterior é validada. Para F2, o nó isn3 foi isolado dos outros dois por uma janela de cerca de 90 s em quatro repetições. O tempo entre o fim da janela e o retorno do cluster ao estado saudável com três nós foi medido por consultas a cada 2 s. Nas três repetições feitas em cluster recém-criado e com monitoramento mínimo (a primeira, a terceira e a quarta), esse tempo foi de 48 s, 46 s e 44 s. A segunda repetição, feita em um cluster que já sofrera duas partições, com uma sonda que escrevia durante a partição e com coleta de monitoramento mais intensa, levou 188 s. Os registros mostram que o atraso ocorreu depois da fusão da vista, na resolução de conflitos de um dos caches, que esgotou o tempo-limite de 60 s. A repetição seguinte, em cluster novo, enfraqueceu a hipótese da sonda, mas as duas hipóteses restantes, histórico do cluster e coleta intensa, não foram separadas. A causa não foi isolada, e a repetição é registrada como limitação e reportada à parte. Esses tempos dizem respeito apenas à recomposição da topologia: sem carga de aplicação, nada indicam sobre divergência de dados ou perda de escritas. Das oito etapas da sequência mais recente, cinco foram executadas. A primeira preparou, no hospedeiro, o pacote de código e seus resumos criptográficos. A segunda, na máquina virtual e sem alterar a rede, instalou esse pacote com conferência dos resumos, verificou as ferramentas e carregou os módulos do kernel usados por F4, executou na própria máquina virtual as suítes de teste dos injetores de F2 e F4 e leu o estado do cluster, que estava parado depois de uma queda do hospedeiro, com investigação da causa. A terceira verificou que o kernel aceita as disciplinas e o filtro de F4 sem afetar o tráfego, com filtro para um endereço reservado a testes. A quarta verificou que o temporizador do sistema armado pelo script de verificação dispara e aciona a limpeza do injetor de F4. A quinta repetiu a partição em cluster recém-criado. Essa repetição substituiu outra, planejada para o cluster com histórico, que perdeu o sentido quando uma queda do hospedeiro desfez o cluster, e seu tempo de recomposição está entre os citados acima. Faltam a primeira injeção real de F4, com perda de 20 % por 60 s e limpeza posterior, e duas verificações do injetor de F2, uma da recomposição manual depois de uma partição real e outra do temporizador de segurança, armado pelo script de verificação, disparando sozinho. O modo silencioso de F1 foi ensaiado apenas no cluster local, e seu ensaio na máquina virtual precede a recriação do cluster com os quatro caches.

### p. 69 · DES-18 · CORREÇÃO · 4.4 Estado dos artefatos

Nota: "mantém, em um único descritor, os perfis DIST_SYNC (controle) e DIST_ASYNC (alvo), com parametrização aderente às linhas T1 a T4..." vira: o XML descreve caches com numOwners=2 e 64 segmentos, parametrização baseada em T1–T4, T15, T16 (exceto segmentos: 256 em T4), e só os caches síncronos sessions/counters até out/2026 (decisão do autor, rodada 2).

> descreve os caches com numOwners=2 e 64 segmentos, com parametrização baseada nas linhas T1 a T4, T15 e T16 da Tabela ⟨tab-parametros-baseline⟩, exceto pelo número de segmentos, que em T4 é 256. Até outubro de 2026, a configuração tinha apenas os caches sessions e counters, ambos síncronos (DIST_SYNC).

### p. 70–71 · DES-19 · ACRÉSCIMO · 4.4 Estado dos artefatos

Nota: Depois da lista de sete componentes (literal da banca), nova lista com os acréscimos da ATUAL: escrita condicional e códigos de erro, slots/gerações, S3, fase de preparação, CSV por evento, auditor (I2 e laço de convergência), verificador de sanidade, pacote do controlador e CLI.

> Até outubro de 2026, o programa de carga recebeu os seguintes acréscimos: \begin{itemize} \item nas operações O1 a O7, a invalidação do logout e da cascata de bloqueio por escrita condicional à versão e códigos que distinguem erro de transporte (ERROR_TRANSPORT) de recusa por indisponibilidade (ERROR_UNAVAILABLE); \item nas misturas S1 e S2, a versão de slots e gerações, que é a usada nas medições (Seção ⟨sec-carga⟩); \item o cenário S3, com servidores de aplicação ligados a nós distintos, usuários virtuais, ator administrativo e conjunto de identidades para bloqueio por limite (Seção ⟨sec-carga-s3⟩); \item uma fase de preparação que esvazia e confere os caches e grava a carga inicial de slots; \item o registro de cada evento em CSV, com marcas de tempo de início e fim em nanossegundos, código de retorno, servidor de conexão do cliente e, quando há marcadores de falha, fase da execução; \item no auditor, a classificação de I2 por marcos de bloqueio e um laço de convergência para I5 e I6, executado depois que a carga cessa, com rodadas a cada 5 s, laço que a nova medição de referência não usa; \item um verificador de sanidade que lê o CSV de eventos e emite veredito sobre a mistura observada; \item o pacote do controlador heurístico, descrito adiante, e opções nomeadas de linha de comando para duração, repetições, aquecimento, modo de replicação, mistura, cenário e semente. \end{itemize}

### p. 71 · DES-20 · ATUALIZAÇÃO · 4.4 Estado dos artefatos

Nota: Frase acrescentada: a especificação inclui ações de falha, MCSession.tla e quatro configurações.

> Na versão atual, a especificação inclui as ações de falha e é acompanhada do módulo de verificação spec/MCSession.tla e de quatro configurações de cardinalidade, descritos nas Seções ⟨sec-spec-formal⟩ e ⟨sec-plano-execucao⟩.

### p. 72 · DES-21 · ACRÉSCIMO · 4.4 Estado dos artefatos

Nota: Parágrafo novo com os scripts atuais (quatro injetores, tc a partir do hospedeiro, limpeza, temporizador, collect-resources, run-rebaseline, scripts Python).

> Na versão atual, os scripts de injeção cobrem as quatro classes de falha: inject-crash.sh (F1, nos modos de parada ordenada e de queda silenciosa), inject-partition.sh (F2), inject-jitter.sh (F3) e inject-netdegrade.sh (F4, com disciplina de prioridade, netem por banda e filtro por endereço de destino). As falhas de rede usam tc-netem [netem_manpage], aplicado a partir do hospedeiro no network namespace do container, porque a imagem do Infinispan não traz a ferramenta tc. Os injetores de F2 e F4 gravam marcadores de início e fim, removem as disciplinas de rede ao terminar, por rotina de limpeza que roda também quando recebem sinal de término, e oferecem um modo de limpeza avulso. Nas verificações na máquina virtual, os scripts de verificação armam ainda um temporizador do sistema que aciona essa limpeza se o injetor for morto. Completam o conjunto o coletor de recursos collect-resources.sh (Seção ⟨sec-energia⟩), os orquestradores run-baseline.sh e run-rebaseline.sh, o gerador da matriz da nova medição e os scripts de análise em Python (rebaseline.py, fases.py, referencia_p99.py, energia_proxy.py e comparar_bracos.py).

### p. 72 · DES-22 · ATUALIZAÇÃO · 4.4 Estado dos artefatos

Nota: Frase após os 49 testes JUnit (mantidos): jar congelado para a nova medição; suítes em Java, Python e shell (a ATUAL não dá contagem).

> Na versão atual, o jar está congelado para a nova medição, e os componentes têm suítes de testes automatizados em Java, Python e shell, estas com simuladores dos comandos de sistema para rodar sem cluster.

### p. 72 · DES-23 · CORREÇÃO · 4.4 Estado dos artefatos

Nota: "teste de Mann-Whitney pareado por percentil" vira "teste de Mann-Whitney por percentil".

> pelo teste de Mann-Whitney por percentil

### p. 72 · DES-24 · ATUALIZAÇÃO · 4.4 Estado dos artefatos

Nota: Frase acrescentada: o diagnóstico mostrou que a execução contra o cluster vivo não mediu o ciclo de vida de sessões.

> O diagnóstico dessa execução, descrito na mesma seção, mostrou que ela não mediu o ciclo de vida de sessões.

### p. 73–75 · DES-25 · ESTRUTURA · 4.4 (novo \subsection*)

Nota: Bloco não numerado "Controlador heurístico: projeto e implementação" (labels sec-controlador, qd-sinais-controlador, sec-controlador-cluster), com todo o texto e o Quadro da Seção 4.5 da ATUAL; o comentário do Revisor foi descartado.

> O controlador executa como thread dentro do processo Java do programa de carga, externo ao data grid: não roda no servidor, não reconfigura o servidor, apenas lê a saúde dos nós pela API REST, e não grava estado replicado. O núcleo de decisão fica em um pacote que não depende do Infinispan nem do código de sessão, restrição conferida por teste, de modo que o mesmo código pode decidir ao vivo ou sobre registros gravados. O núcleo usa como tempo o fim de cada janela de observação, e não o relógio do sistema, o que o torna determinístico e testável sem espera real. No desenho aprovado, cada janela gerará uma linha em arquivo JSONL, com os observáveis, os limiares em vigor, a decisão e seus motivos, como trilha de auditoria, e o controlador terá três modos, selecionados por opção de linha de comando: desligado, em que nenhum objeto novo é criado; shadow, em que observa e decide sem atuar; e active, em que aplica o perfil decidido. A trilha e os modos dependem da ligação ao laço de carga, ainda não implementada. O monitoramento agrega os eventos do cliente em janelas contíguas de 5 s e consulta a saúde de cada nó a cada 2 s. O Quadro ⟨qd-sinais-controlador⟩ resume os sinais. O sinal de latência é a fração de eventos da janela com latência acima do p99 de referência da operação, obtido de execuções sem falha do mesmo cenário e modo de replicação. Para uma única operação, a fração ultrapassa 1 % exatamente quando o p99 da janela supera o de referência. Somada sobre operações, ela funciona com poucas amostras por operação. Janelas com amostra abaixo do mínimo tornam o sinal indeterminado. As violações de I5 e I6 ficam fora dos gatilhos, porque só são verificáveis sob quiescência, e entram na avaliação como desfecho. \begin{table}[htbp] \captionsetup{type=table, name=Quadro} \caption{Sinais observados pelo controlador heurístico} \centering \footnotesize \begin{tabular}{p{2.6cm}p{6.4cm}p{4.2cm}} \hline Sinal & Definição na janela & Papel \\ \hline Latência ($S_\text{lat}$) & fração de eventos com latência acima do p99 de referência da operação; mínimo de 100 eventos & primário; exige 2 janelas seguidas \\ Erro ($S_\text{err}$) & fração de eventos com ERROR_TRANSPORT ou ERROR_UNAVAILABLE; mínimo de 50 eventos & primário; exige 2 janelas seguidas \\ Topologia ($S_\text{top}$) & consulta de saúde falhou, menos de três nós ou algum cache fora do estado saudável & primário; basta 1 amostra \\ Violação ($S_\text{viol}$) & novas violações de I1, I4 ou I2 classificada como real & secundário; severidade máxima \\ \hline \end{tabular} \fonte{O autor} \end{table} A decisão é uma máquina de dois estados, NORMAL e CONSERVADOR, avaliada uma vez por janela por quatro regras. Pela regra R1, uma violação leva ao perfil conservador imediatamente, mesmo durante o intervalo mínimo entre transições (cooldown), porque, em segurança, reagir tarde custa mais que oscilar uma vez. Pela regra R2, fora do cooldown, uma anomalia de topologia, ou latência ou erro acima do limiar de entrada em duas janelas seguidas, leva ao perfil conservador. Pela regra R3, no perfil conservador, qualquer sinal alto zera a contagem de janelas calmas. Pela regra R4, o retorno ao perfil normal exige, ao mesmo tempo, cooldown expirado, seis janelas calmas seguidas, topologia estável há um intervalo mínimo e nenhuma violação recente. A histerese vem de limiares de saída menores que os de entrada. Os valores atuais são provisórios, fornecidos por arquivo de parâmetros ou pelo conjunto provisório padrão, sem valor fixo na lógica de decisão: limiar de latência de 0,05 na entrada e 0,02 na saída, de erro de 0,02 e 0,005, cooldown de 30 s, estabilidade de topologia de 30 s e retenção após violação de 120 s. A calibração final seguirá regra fixada antes de ver os dados de calibração. O perfil conservador atua por quatro parâmetros do cliente. O primeiro limita a admissão, reduzindo a 50 % o número de operações em curso. O segundo reduz o ritmo, com pausa de 5 ms entre operações em S1 e S2 e tempo de pensar multiplicado por 1,5 em S3. O terceiro adia as escritas administrativas de S3 (bloqueio, desbloqueio e alterações do contador) até a saída do perfil conservador ou até 30 s, sem descartá-las. O quarto dobra a espera base entre retentativas de S3, com variação aleatória de $\pm 50 %$, sem mudar o número de retentativas. Todos apenas escolhem quando o cliente emite uma operação que já emitiria: nenhuma operação é criada ou suprimida, nenhum estado replicado é tocado e a configuração do servidor não muda. Essa restrição mantém os comportamentos do sistema dentro dos admitidos pelo modelo formal e preserva o modo de replicação como variável do experimento. O modo de replicação e o tempo-limite do cliente Hot Rod ficaram fora desta primeira etapa porque alteram o sistema medido ou exigem reconstruir o cliente. Estão implementados e testados na versão local do código que integra esses componentes, com relógio simulado e capturas reais da saúde do cluster: o núcleo de decisão, com as regras, a histerese e o cooldown; o agregador de janelas, ligado ao registro de eventos e ao auditor por um ponto de escuta opcional que, desligado, deixa a saída do programa idêntica à anterior; a sonda de topologia; o controle de emissão de operações, com os quatro parâmetros; o coletor de recursos; e os scripts de análise por fase, de referência de p99, de estimativa de energia e de comparação pareada entre braços. Falta ligar o controlador ao laço de carga: a interface de linha de comando ainda não tem a opção que o ativa, e por isso o controlador não foi executado com o programa de carga e não há resultado sobre seu efeito. Também faltam a reprodução das decisões sobre registros gravados, os marcadores de início e fim de F1 e F3 nos registros, uma proteção contra parâmetros de energia inválidos, o pré-registro com execução curta de teste, a calibração e a avaliação descrita na Seção ⟨sec-avaliacao-controlador⟩. A atuação do controlador sobre a configuração do cluster, prevista no objetivo específico (4), é uma segunda etapa planejada, posterior à ligação do controlador ao laço de carga e à avaliação da atuação no cliente. Ela vem depois por três razões. Durante a nova medição de referência e a campanha com o cenário S3, a configuração do servidor é variável do experimento e não pode mudar. Só a avaliação da etapa cliente indica se atuar no servidor acrescenta algo. Por fim, parte das ações possíveis produz comportamentos que o modelo formal não admite. A etapa começa por uma verificação, na documentação e em cluster local, de quais atributos do Infinispan 15 podem ser alterados em tempo de execução sem reiniciar o cache. Os candidatos iniciais, hipótese de partida ainda não verificada, são o tempo-limite de chamadas remotas e o tempo-limite de aquisição de trava, que só alteram quando uma operação termina; a suspensão do rebalanceamento, que altera o momento da convergência; e a mudança forçada da disponibilidade de uma partição, que permitiria escrita na partição minoritária, comportamento fora do modelo. O modo de replicação, o número de cópias, o número de segmentos e a política de partição e de fusão devem exigir a recriação do cache, com perda dos dados, o que a mesma verificação vai confirmar, e ficam fora. A primeira etapa permanece restrita ao cliente e é avaliada como tal; a segunda terá pré-registro e avaliação próprios. A lista final de parâmetros, a admissão de ações que alteram a convergência ou a semântica sob partição, estas com extensão do modelo TLA+, e o tempo de máquina dependem de decisão posterior do autor.

### p. 75–76 · DES-26 · CORREÇÃO · 4.5 Resultados parciais

Nota: "Entre as trinta e uma referências oficiais lidas integralmente" vira "Nos cerca de trinta trabalhos lidos na íntegra", e entra a ressalva de que a classificação dos 165 estudos ainda precisa confirmar a constatação (rodada 2: redação do Bibliotecário).

> Nos cerca de trinta trabalhos lidos na íntegra até esta etapa, a taxa de violação de invariantes não aparece como métrica primária de avaliação experimental, pois a literatura se concentra em métricas de desempenho agregadas, como latência média, vazão e percentis de cauda, e em métricas de disponibilidade do sistema; a classificação dos 165 estudos do mapeamento ainda precisa confirmar essa constatação.

### p. 76 · DES-27 · CORREÇÃO · 4.5 Resultados parciais

Nota: Frase acrescentada: na forma global, as violações de I1/I2 decorrem da divergência transitória e não informam sobre o protocolo.

> Na forma global, porém, as violações de I1 e I2 são consequência direta da divergência transitória entre réplicas e não informam sobre o protocolo; as propriedades avaliadas réplica a réplica, descritas na mesma seção, separam esse efeito.

### p. 76 · DES-28 · ATUALIZAÇÃO · 4.5 Resultados parciais

Nota: Frase acrescentada: o controlador está projetado e implementado em parte, sem ligação ao laço de carga.

> O controlador está projetado e implementado em parte, ainda sem ligação ao laço de carga (Seção ⟨sec-controlador⟩).

### p. 76 · DES-29 · CORREÇÃO · 4.5 Resultados parciais

Nota: "com resultados reais reportados na Seção" vira "cujo diagnóstico, na Seção, mostrou que a bateria não mediu o ciclo de vida de sessões".

> cujo diagnóstico, na Seção ⟨sec-baseline-estado⟩, mostrou que a bateria não mediu o ciclo de vida de sessões

### p. 76 · DES-30 · ATUALIZAÇÃO · 4.5 Resultados parciais

Nota: Acrescentado: a nova medição que substitui a bateria plena foi pré-registrada e ainda não foi executada.

> , e a nova medição de referência que a substitui foi pré-registrada e ainda não foi executada

### p. 76 · DES-31 · ATUALIZAÇÃO · 4.5 Resultados parciais

Nota: Após a 2ª limitação (rede sem perdas nem partições): o modelo atual inclui queda, recuperação, partição e fusão; a perda só existe em cópia separada, sem retransmissão.

> Na versão atual, o modelo inclui queda, recuperação, partição e fusão como ações, e a perda de mensagens foi verificada apenas em cópia separada da especificação, sem retransmissão (Seção ⟨sec-spec-formal⟩).

### p. 66 · DES-32 · CORREÇÃO · 4.3 / Baseline experimental

Nota: Na abertura da subseção, "crash silencioso (F1)" vira "falha de nó (F1), executada como parada ordenada" (decisão do autor sobre DES-10).

> falha de nó (F1), executada como parada ordenada,

### p. 76 · DES-33 · CORREÇÃO · 4.5 Resultados parciais

Nota: Na 1ª limitação, "crash silencioso (F1)" vira "parada ordenada de nó (F1)" (decisão do autor sobre DES-10).

> parada ordenada de nó (F1)

### p. 69 · DES-34 · CORREÇÃO · 4.4 Estado dos artefatos

Nota: Acrescentado \cite[seção 7]{infinispan_configuring_15} depois de PREFERRED_NON_NULL.

> [infinispan_configuring_15, seção 7]

### p. 70 · DES-35 · ATUALIZAÇÃO · 4.4 Estado dos artefatos

Nota: Parágrafo com o restante do antigo DES-18: acréscimo dos caches sessions-async/counters-async, não transacionais, ainda não instalados na VM.

> A versão atual acrescenta sessions-async e counters-async em modo assíncrono, com os mesmos parâmetros, selecionados pela opção \texttt{-{}-replication} do programa de carga. Os quatro caches são não transacionais. Essa versão foi testada no cluster local e só será instalada na máquina virtual quando o cluster for recriado para a nova medição de referência.

### p. 75 · DES-36 · CORREÇÃO · 4.4 / Controlador (dentro de DES-25)

Nota: "só mudam recriando o cache" vira "devem exigir a recriação do cache [...], o que a mesma verificação vai confirmar".

> devem exigir a recriação do cache, com perda dos dados, o que a mesma verificação vai confirmar, e ficam fora.

### p. 61 · DES-37 · CORREÇÃO · 4.2 Especificação formal inicial

Nota: No registro de aplicação industrial, \citep{newcombe_aws_tla_2014} vira \citep{newcombe_aws_tla_2014, newcombe_aws_formal_methods_2015}, como pede A8 para a frase equivalente da Introdução.

> [newcombe_aws_tla_2014, newcombe_aws_formal_methods_2015]

### p. 61 · DES-38 · CORREÇÃO · 4.2 Especificação formal inicial

Nota: "Must, com semântica explícita de mensagens assíncronas e falhas" vira "Must, que oferece uma interface de concorrência com vários modelos de comunicação e verifica os programas por redução parcial de ordem dinâmica" (redação de A16, restrita ao resumo).

> que oferece uma interface de concorrência com vários modelos de comunicação e verifica os programas por redução parcial de ordem dinâmica [enea_must_2024],


## Cap. 5 Considerações Parciais

### p. 77 · CON-01 · CORREÇÃO · Cap. 5, texto inicial (síntese), 1º parágrafo

Nota: Removido "ambos sem erros de transporte: F1 com *failover* gracioso e impacto de cauda mínimo, e F3 com degradação graciosa porém custosa..." (resultado de junho inválido); no lugar, a frase de que a bateria de junho não mediu o ciclo de vida de sessões.

> . Essa primeira bateria de medição, de junho, não mediu o ciclo de vida de sessões, por defeitos da carga e da análise identificados em outubro (Seção ⟨sec-baseline-estado⟩).

### p. 77–78 · CON-02 · ATUALIZAÇÃO · Cap. 5, texto inicial (síntese)

Nota: Três parágrafos novos após o 1º parágrafo da banca: estado de 30/10 da vertente formal (modelo com falhas, I1/I2 réplica a réplica, I3/I4 só faixa/tipo, I5/I6 por empate de *timestamp* e fusão), da experimental e do controlador. Conector "Na versão parcial do TCC II"; "frente formal/experimental" trocado por "vertente" (nome da banca); frase sobre a bateria de junho retirada por repetir CON-01.

> Na versão parcial do Trabalho de Conclusão de Curso II, o trabalho chega com a vertente formal em estágio mais avançado que a experimental. Na vertente formal, o modelo TLA+ do protocolo de sessão inclui quedas, recuperações e partições. Na verificação pelo TLC, as fórmulas globais de I1 e I2 são violadas por construção em um modelo com várias réplicas, o que levou a propriedades avaliadas réplica a réplica. No cenário semeado, a irreversibilidade da invalidação por réplica só foi violada sob partição, coerente com a perda, na fusão, de escritas feitas na partição menor. Com login habilitado, ela também é violada sem falha, por artefatos do modelo. As formas de I3 e I4 declaradas no modelo, de faixa e de tipo, não foram violadas, com verificação exaustiva apenas na menor configuração, e a imutabilidade do motivo, avaliada réplica a réplica, é violada. I5 e I6 são violados por dois mecanismos: o empate de marcas de tempo, defeito do modelo cuja correção está pendente, e, sob partição, a perda, na fusão, de uma escrita ainda não propagada, com divergência permanente (Seção ⟨sec-mc-resultados⟩). Na vertente experimental, o ambiente está montado e documentado (Seção ⟨sec-arquitetura⟩): o cluster de três nós, o programa de carga com os cenários S1, S2 e S3, os injetores das quatro classes de falha e os scripts de análise. Os defeitos da primeira bateria foram corrigidos, e uma nova medição de referência, com os modos síncrono e assíncrono, foi pré-registrada, mas ainda não executada. Todas as execuções na máquina virtual até aqui ocorreram em DIST_SYNC, de modo que o comportamento do modo assíncrono, alvo do trabalho, ainda não foi medido. O injetor de partição foi verificado sem carga de aplicação, e o de degradação parcial passou apenas pelas verificações que não afetam o tráfego, sem injeção real até aqui. O controlador heurístico está projetado e implementado em parte (Seção ⟨sec-controlador⟩). O gatilho primário combina latência de cauda relativa, taxa de erro e topologia, e as violações de I1, I2 e I4 formam o gatilho secundário. A convergência (I5 e I6) entra apenas como desfecho. A primeira etapa atua apenas sobre parâmetros do cliente, e a atuação sobre a configuração do cluster é uma segunda etapa planejada, cujos parâmetros candidatos ainda não foram verificados. O controlador ainda não foi ligado ao laço de carga, e sua avaliação, desenhada em blocos pareados (Seção ⟨sec-avaliacao-controlador⟩), depende da nova medição de referência e da campanha com S3.

### p. 78 · CON-03 · CORREÇÃO · Cap. 5, texto inicial, parágrafo dos invariantes

Nota: Chave `nist_sp_800_63b_2017` trocada por `nist_sp_800_63b4_2025` (sem prazos por AAL no trecho).

> [nist_sp_800_63b4_2025]

### p. 78 · CON-04 · ACRÉSCIMO · Cap. 5, texto inicial, parágrafo dos invariantes

Nota: Frase sobre Nguyen et al. (2019) como ponto comparativo mais próximo.

> O ponto comparativo mais próximo, [nguyen_weaker_consistency_2019], combina consistência relaxada com monitoramento e recuperação reativos, sem verificação formal a montante.

### p. 78 · CON-05 · CORREÇÃO · Cap. 5, texto inicial, parágrafo dos invariantes

Nota: Depois de "Os contraexemplos do TLC fornecem o catálogo inicial...", acrescentada a ressalva de que os contraexemplos de I5/I6 fora da fusão não informam sobre o protocolo até a correção do empate (defeito de modelagem, não "benigno").

> Até a correção do empate de marcas de tempo, os contraexemplos de I5 e I6 que não passam pela fusão não informam sobre o protocolo.

### p. 79 · CON-06 · CORREÇÃO · Cap. 5, texto inicial, parágrafo das três limitações

Nota: Remoção de "com resultados reais reportados na Seção~\ref{sec-baseline-estado}," (marcador vazio no ponto).

> *(remoção: trecho da banca retirado neste ponto)*

### p. 79 · CON-07 · CORREÇÃO · Cap. 5, texto inicial, parágrafo das três limitações

Nota: Frase acrescentada: a bateria não mediu o ciclo de vida de sessões e nenhum número de latência de junho é usado como resultado.

> Essa bateria não mediu o ciclo de vida de sessões, e nenhum número de latência de junho é usado como resultado (Seção ⟨sec-baseline-estado⟩).

### p. 79 · CON-08 · ATUALIZAÇÃO · Cap. 5, texto inicial, parágrafo do canal do modelo

Nota: Após o plano da banca (falhas nas etapas 3 e 4): o modelo ganhou queda, recuperação, partição e fusão no TCC II; o canal integrado não perde mensagens, e a variante com perda é ilustrativa.

> No Trabalho de Conclusão de Curso II, o modelo ganhou as ações de queda, recuperação, partição e fusão (Seção ⟨sec-mc-resultados⟩). O canal do modelo não perde mensagens na versão integrada, e a variante com perda tem caráter ilustrativo.

### p. 79–80 · CON-09 · ESTRUTURA · Cap. 5, após as limitações da banca, antes de 5.1

Nota: Bloco `\section*{Limitações e ameaças à validade}` (fora do sumário) com `\label{sec-limitacoes}` (o da banca está comentado) e 3 parágrafos da ATUAL: limitações formais (tempo-limite, cardinalidade, Yuan 2014, empate), experimentais (nenhum dado utilizável, F1 ordenado, 188 s, 10 repetições, 64 segmentos, normal em F3, S3, energia) e ambiente (17 desligamentos, quedas de 18 e 28/10). "frente" trocado por "vertente".

> Limitações e ameaças à validade} Na vertente formal, os resultados de I3 e I4 nas configurações maiores valem só até o tempo-limite das execuções, e as cardinalidades verificadas são pequenas, recorte apoiado pela observação de que 98 % das falhas de produção analisadas se manifestam com três nós ou menos [yuan_simple_testing_2014], mas que não exclui defeitos em escalas maiores. Os contraexemplos de I5 e I6 que não passam pela fusão decorrem do empate de marcas de tempo e não terão interpretação enquanto ele não for corrigido. Na vertente experimental, nenhum dado de latência ou de violação é ainda utilizável: a bateria de junho não criou sessões, e a nova medição não foi executada. Os F1 já executados foram paradas ordenadas, e não quedas silenciosas, e o modo silencioso não foi ensaiado na máquina virtual. A partição foi exercida apenas sem carga de aplicação, e o tempo de recomposição da segunda repetição, de 188 s, obtido em condições diferentes das demais (cluster com histórico e coleta de monitoramento intensa), tem causa não isolada entre duas hipóteses registradas. A nova medição usa dez repetições por célula, e não as trinta do plano, o que reduz o poder estatístico. Ela mantém ainda 64 segmentos, sem fonte que justifique o valor, e usa distribuição normal em F3, no lugar da lognormal. Os parâmetros do cenário S3 são premissas de modelagem, e a estimativa de energia depende de um modelo de potência e do tempo de CPU virtual, com as limitações da Seção ⟨sec-energia⟩. O ambiente de execução é uma ameaça à validade de outra natureza. O computador hospedeiro registrou dezessete desligamentos não ordenados desde setembro de 2026, dois deles com parada do sistema por erro registrada. O autor atribui os desligamentos a quedas no fornecimento de energia elétrica na região. Uma dessas quedas, em 18 de outubro, desfez o cluster entre duas etapas da sequência de verificações. O histórico que uma das repetições de partição pretendia preservar se perdeu, e a repetição foi redefinida. Outra queda, em 28 de outubro, interrompeu a primeira tentativa dessa repetição. As regras pré-registradas da nova medição tratam execuções interrompidas, que são descartadas e repostas, mas não compensam o atraso que novas quedas imporiam ao cronograma.

### p. 82 · CON-10 · CORREÇÃO · 5.1, último parágrafo

Nota: "cujos resultados constam da Seção~\ref{sec-baseline-estado}" trocado por "cujo diagnóstico consta da Seção~\ref{sec-baseline-estado}".

> cujo diagnóstico consta da Seção ⟨sec-baseline-estado⟩

### p. 82 · CON-11 · ATUALIZAÇÃO · 5.1, último parágrafo

Nota: Acrescentado: o cronograma do TCC I foi cumprido com duas ressalvas (mapeamento não classificado; *baseline* das semanas 9 e 10 inválido como medida); no TCC II, F2 ganhou injetor, F3 usa distribuição normal e F4 virou degradação parcial da rede.

> O cronograma do Trabalho de Conclusão de Curso I foi cumprido com duas ressalvas: a leitura completa e a classificação dos estudos do mapeamento não foram concluídas, e o baseline das semanas 9 e 10 mostrou-se inválido como medida (Seção ⟨sec-baseline-estado⟩). No Trabalho de Conclusão de Curso II, F2 deixou de ser reservada e ganhou injetor, F3 passou a usar a distribuição normal, e F4 foi redefinida como degradação parcial da rede (Seção ⟨sec-falhas⟩).

### p. 83 · CON-12 · ATUALIZAÇÃO · 5.2, após o parágrafo do cronograma do TCC II

Nota: Parágrafo novo: o cronograma da Tabela tab-cronograma-tcc2 foi modificado (semanas 1 a 4 cumpridas; 5 e 6 viraram correção da carga e pré-registro; controlador adiantado; etapas restantes sem datas no Apêndice). Conector de 1 frase.

> O cronograma da Tabela ⟨tab-cronograma-tcc2⟩ foi modificado durante a execução. No Trabalho de Conclusão de Curso II, as etapas das semanas 1 a 4 foram cumpridas, com a inclusão de falhas no modelo. A etapa das semanas 5 e 6 deu lugar à correção da carga e ao pré-registro da nova medição de referência, ainda não executada. O controlador, previsto para as semanas 7 e 8, foi projetado e implementado em parte antes da medição de referência, para que seu desenvolvimento não dependesse da máquina virtual. As etapas restantes, sem datas fixas, estão no Apêndice ⟨ap-plano-tcc2⟩.

### p. 84 · CON-13 · ATUALIZAÇÃO · 5.2, após o parágrafo "Em síntese"

Nota: Parágrafo "Próximas etapas" da ATUAL (ordem de dependência das etapas restantes). "frente formal" trocado por "vertente formal".

> As etapas restantes, detalhadas no Apêndice ⟨ap-plano-tcc2⟩, seguem uma ordem de dependência. Na máquina virtual, falta concluir as verificações dos injetores, estabilizar o sistema operacional contra atualizações automáticas, ensaiar o F1 silencioso e recriar o cluster com os quatro caches. Em seguida, vêm o piloto e a campanha da nova medição de referência, que produzirão as primeiras tabelas de latência e de violação válidas, nos dois modos. Depois, a campanha com o cenário S3 exerce F2 e F4 sob carga. Em paralelo, o controlador precisa ser ligado ao laço de carga, calibrado e avaliado conforme o desenho da Seção ⟨sec-avaliacao-controlador⟩, e a segunda etapa, de atuação sobre a configuração do cluster, começa pela verificação de quais parâmetros podem mudar em tempo de execução. Na vertente formal, faltam a correção de I5 e I6 e o alinhamento entre os contraexemplos e os padrões observados nos experimentos. No mapeamento, faltam a classificação dos estudos e o fluxograma definitivo.

### p. 77 · CON-14 · CORREÇÃO · Cap. 5, texto inicial (síntese), 1º parágrafo

Nota: O F1 executado era chamado de "*crash* silencioso (F1)"; passou a "parada ordenada de nó (F1, por `podman stop` e `podman start`)". As outras menções ao F1 executado estão em CON-21 e CON-22; as menções ao F1 *planejado* (Etapas 3 e 4, Etapa A, 5.2) não mudaram. A banca não dizia `DIST_ASYNC` para a bateria de junho, e nada mudou nesse ponto.

> parada ordenada de nó (F1, por podman stop e podman start)

### p. 79 · CON-15 · CORREÇÃO · Cap. 5, limitações (2ª limitação)

Nota: Edição do coordenador (rodada 2): "a leitura na íntegra de trinta e uma das referências" passou a "a leitura na íntegra das referências". A 2ª ocorrência, no 5.1, está em CON-19.

> A segunda é a leitura na íntegra das referências

### p. 79 · CON-16 · CORREÇÃO · Cap. 5, limitações (3ª limitação)

Nota: Saiu ", com suíte de 49 testes JUnit aprovada em verificação determinística sobre dez rodadas consecutivas" (marcador vazio no ponto), como na ATUAL, que não dá contagem de testes.

> *(remoção: trecho da banca retirado neste ponto)*

### p. 78 · CON-17 · CORREÇÃO · Cap. 5, texto inicial, parágrafo dos invariantes

Nota: "entre as referências oficiais lidas integralmente até esta etapa" passou a "nos cerca de trinta trabalhos lidos na íntegra até esta etapa, constatação que a classificação dos 165 estudos do mapeamento ainda precisa confirmar".

> nos cerca de trinta trabalhos lidos na íntegra até esta etapa, constatação que a classificação dos 165 estudos do mapeamento ainda precisa confirmar

### p. 79 · CON-18 · CORREÇÃO · Cap. 5, limitações (2ª limitação)

Nota: O trecho "oficiais aprovadas até esta etapa; as exceções correspondem a fontes hospedadas em bases (SpringerLink Tier 2 e repositório institucional francês)..." foi substituído pela redação do Bibliotecário: oito fontes consultadas só por resumo ou trechos, citadas por nome (Newcombe 2014, Liu 2015, Naskos, Wu, Gambi, Rosa, Enea, Ben Mokhtar 2024). A frase da banca sobre a curadoria do TCC II foi mantida com conector.

> citadas, que não alcançou todas. Por restrição de acesso no período, oito das fontes citadas foram consultadas apenas pelo resumo ou por trechos: [newcombe_aws_tla_2014], [liu_quantitative_consistency_2015], [naskos_probabilistic_mc_2017], [wu_anna_autoscale_2020], [gambi_assurance_selfadaptive_2013], [rosa_selfmgmt_2013], [enea_must_2024] e [benmokhtar_scheduling_2024]; as afirmações que dependem delas se limitam ao que os resumos sustentam. A resolução final dessas exceções compõe

### p. 81 · CON-19 · CORREÇÃO · 5.1, 1º parágrafo de desvios

Nota: "trinta e uma referências oficiais" passou a "cerca de trinta trabalhos" (recontagem: 30 lidos na íntegra).

> cerca de trinta trabalhos

### p. 79 · CON-20 · CORREÇÃO · Bloco "Limitações e ameaças à validade" (dentro de CON-09)

Nota: "quase todas as falhas de produção" passou a "98 % das falhas de produção analisadas" (Yuan et al. 2014).

> 98 % das falhas de produção analisadas

### p. 79 · CON-21 · CORREÇÃO · Cap. 5, limitações (3ª limitação)

Nota: "*crash* silencioso (F1)" passou a "parada ordenada de nó (F1)" (F1 executado).

> parada ordenada de nó (F1)

### p. 82 · CON-22 · CORREÇÃO · 5.1, 2º parágrafo de desvios

Nota: "*crash* silencioso (F1)" passou a "parada ordenada de nó (F1)" (F1 executado).

> parada ordenada de nó (F1)


## Apêndice A Plano de trabalho

### p. 93 · APX-01 · ATUALIZAÇÃO · Apêndice, parágrafo inicial

Nota: Frase conectora: o plano foi revisado, e as atividades restantes estão nas Etapas A a F, ao final do apêndice.

> O plano foi revisado durante a execução, e as atividades que restam para concluir o trabalho constam, ao final deste apêndice, nas Etapas A a F.

### p. 94 · APX-02 · ATUALIZAÇÃO · Apêndice, Etapas 7 e 8

Nota: Após "perfil conservador, ativado quando a taxa de violação aumenta acima de um limiar": o projeto foi revisado; gatilho primário = latência de cauda relativa, erro e topologia; violações de I1, I2 e I4 = gatilho secundário.

> O projeto do controlador foi revisado no Trabalho de Conclusão de Curso II: o gatilho primário combina latência de cauda relativa, taxa de erro e topologia, e as violações de I1, I2 e I4 formam o gatilho secundário (Seção ⟨sec-controlador⟩).

### p. 95 · APX-03 · ESTRUTURA · Apêndice, após Etapas 11 e 12

Nota: Bloco `\section*{Plano de trabalho restante}` com conector de 1 frase e parágrafo introdutório da ATUAL ("Este apêndice" virou "Esta parte do apêndice").

> Plano de trabalho restante} O plano das Etapas 1 a 12 foi modificado durante a execução do Trabalho de Conclusão de Curso II. Esta parte do apêndice detalha as atividades que restam para concluir o trabalho, na ordem imposta pelas dependências entre elas. Não há datas fixas: a duração de várias etapas depende de tempo de máquina, e a estimativa para as três campanhas de medição (nova medição de referência, campanha com o cenário S3 e avaliação do controlador) é de ao menos cinco a sete noites de execução sem falha do hospedeiro, além de sessões diurnas de preparação. As etapas que exigem a máquina virtual são executadas uma de cada vez, com validação antes da seguinte. As demais podem correr em paralelo.

### p. 95 · APX-04 · ATUALIZAÇÃO · Apêndice, após Etapas 11 e 12

Nota: Etapa A (preparação da VM), de `\section` numerada para `\section*`; label `ap-tcc2-etapa-a` mantido.

> Etapa A: preparação da máquina virtual} A etapa conclui a sequência de verificações dos injetores descrita na Seção ⟨sec-verificacao-injetores⟩: a primeira injeção real de degradação parcial, com perda de 20 % por 60 s sem carga de aplicação, e as duas verificações restantes do injetor de partição, a recomposição manual depois de uma partição real e o disparo do temporizador de segurança armado pelo script de verificação. Em seguida, o sistema operacional da máquina virtual é protegido contra atualizações automáticas de kernel e reinícios não planejados, porque uma troca de kernel no meio de uma campanha alteraria o ambiente medido. A etapa termina com o ensaio do F1 silencioso na máquina virtual e com a recriação do cluster com os quatro caches, dois síncronos e dois assíncronos.

### p. 95–96 · APX-05 · ATUALIZAÇÃO · idem

Nota: Etapa B (nova medição de referência), `\section*`; label `ap-tcc2-etapa-b`.

> Etapa B: nova medição de referência} A etapa executa o protocolo pré-registrado da Seção ⟨sec-protocolo-rebaseline⟩. Uma rodada piloto com as doze células, de dia, verifica o orquestrador, os critérios de recusa e os tempos; seus dados não entram em resultado. A campanha de 120 execuções roda em duas noites, com autorização do autor antes de cada uma. A análise pré-registrada produz os percentis por execução, os contrastes entre condições de falha e entre os modos síncrono e assíncrono e as contagens de violação, que formarão as primeiras tabelas de resultados válidas do trabalho. Os números da bateria de junho não serão comparados com os novos.

### p. 96 · APX-06 · ATUALIZAÇÃO · idem

Nota: Etapa C (falhas sob carga e S3), `\section*`; label `ap-tcc2-etapa-c`.

> Etapa C: falhas sob carga e cenário S3} O orquestrador de experimentos passa a disparar F2 e F4, e o comportamento do modo assíncrono é verificado na documentação e empiricamente, com escrita em um nó e leitura imediata em outro, nos dois modos. Com isso, prepara-se o pré-registro da campanha com o cenário S3, em que DIST_ASYNC é o alvo, com todas as condições de falha, e DIST_SYNC entra em células de referência. A estimativa atual é de cerca de 120 execuções. Essa campanha mede as hipóteses sobre o modo assíncrono, como violações de I1 e I4 mesmo sem falha e perda de escritas confirmadas sob partição, e fornece os dados de calibração do controlador.

### p. 96–97 · APX-07 · ATUALIZAÇÃO · idem

Nota: Etapa D (controlador, 2 parágrafos), `\section*`; label `ap-tcc2-etapa-d`.

> Etapa D: controlador heurístico} O desenvolvimento do controlador não depende da máquina virtual até a avaliação. Falta ligá-lo ao laço de carga, com as opções de linha de comando e a gravação das trilhas de decisão; implementar a reprodução das decisões sobre registros gravados, que confere a equivalência entre a decisão ao vivo e a reprodução; acrescentar marcadores de início e fim de F1 e F3 aos registros; e proteger a análise de energia contra parâmetros inválidos. Com os dados da etapa C, os limiares são calibrados pela regra fixada antes, e a avaliação é pré-registrada, testada em execução curta e executada conforme a Seção ⟨sec-avaliacao-controlador⟩, com cerca de 80 execuções. A segunda etapa do controlador trata da atuação sobre a configuração do cluster (Seção ⟨sec-controlador-cluster⟩). A verificação de quais atributos do Infinispan 15 podem ser alterados em tempo de execução sem reiniciar o cache parte da lista de candidatos, que é hipótese de partida, e é feita na documentação e em cluster local. Ela pode começar depois da ligação do controlador ao laço de carga, enquanto a implementação, o pré-registro e a execução vêm depois da avaliação da etapa cliente. Antes da implementação, definem-se a lista final de parâmetros, a admissão ou não de ações que alteram a convergência ou a semântica sob partição, estas com extensão do modelo TLA+, e o tempo de máquina, e a etapa recebe pré-registro e avaliação próprios.

### p. 97 · APX-08 · ATUALIZAÇÃO · idem

Nota: Etapa E, título "frente formal e alinhamento" trocado por "vertente formal e alinhamento"; `\section*`; label `ap-tcc2-etapa-e`.

> Etapa E: vertente formal e alinhamento} A correção do empate de marcas de tempo, por desempate entre réplicas ou por canal com entrega em ordem, depende de decisão do autor e é seguida de nova verificação de I5 e I6. O mecanismo da fusão de partições não depende dessa correção, e as violações correspondentes devem continuar. As variantes com perda de mensagens e com relógio atualizado pelo máximo são integradas ou descartadas com registro do motivo. O catálogo que relaciona operação, falha e invariante é então confrontado com os resultados experimentais das etapas B e C, para indicar quais contraexemplos do modelo têm correspondente observado e quais não têm.

### p. 97 · APX-09 · ATUALIZAÇÃO · idem

Nota: Etapa F (mapeamento e consolidação), "frentes" trocado por "vertentes"; `\section*`; label `ap-tcc2-etapa-f`.

> Etapa F: mapeamento sistemático e consolidação} O mapeamento sistemático é fechado com a complementação da busca nas bases originais, a classificação dos 165 estudos selecionados nas dimensões D1 a D8 e o fluxograma PRISMA definitivo, o que permite posicionar o trabalho frente a cada agrupamento de estudos. O código desenvolvido, hoje em versões locais, é integrado à versão principal do repositório. A escrita final integra os resultados das campanhas, a discussão e as conclusões, revisa a coerência entre as vertentes formal e experimental e conclui os elementos pré-textuais e pós-textuais. A escrita ocorre em paralelo a todas as etapas.

### p. 94 · APX-10 · CORREÇÃO · Apêndice, Etapas 5 e 6

Nota: A frase da banca sobre F5 (*cross-site*) foi mantida, e uma frase acrescentada diz que a replicação entre localidades saiu do recorte e o F5 não será executado.

> A replicação entre localidades (cross-site replication) saiu do recorte durante o Trabalho de Conclusão de Curso II, e o cenário F5 não será executado.

