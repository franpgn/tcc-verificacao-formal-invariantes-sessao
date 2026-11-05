# Síntese do Mapeamento Sistemático e Recorte do Protótipo, TCC-I Sem 5-6

> Entregável da Etapa 3 (Sem 5-6) do Quadro 1 (TCC-I) do [`Main-PPEC.tex`](../Main-PPEC.tex), referente a: "Seleção por leitura completa, extração e classificação"; "Síntese do mapeamento e recorte do protótipo".
>
> Insumos: a seleção de 165 estudos primários produzida em Sem 3-4 ([`15-execucao-busca-tcc1.md`](15-execucao-busca-tcc1.md)), com resumos obtidos via OpenAlex em 14 de maio de 2026 ([`/workspace/protocolo-ms/abstracts-2026-05-14.json`](../protocolo-ms/abstracts-2026-05-14.json)) e classificação automática nas oito dimensões definidas no protocolo ([`/workspace/protocolo-ms/classificacao-2026-05-14.json`](../protocolo-ms/classificacao-2026-05-14.json)).
>
> Status: rascunho do Escritor (2026-05-14). Aplica as regras permanentes de redação ([`09-padrao-escrita-latex-abnt.md`](09-padrao-escrita-latex-abnt.md), seção "Regras permanentes de redação"). Pendente de validação do Bibliotecário, revisão do Revisor e aprovação do Gestor.

## 1. Caracterização do conjunto analisado

Do total de 165 estudos selecionados em Sem 3-4, 146 forneceram resumo textual recuperável pela API do OpenAlex. Os 19 itens restantes ficaram com apenas metadados estruturais (título, ano, autores, veículo, DOI), o que reduz a precisão da classificação automática para esses casos e exige verificação manual nas semanas subsequentes do trabalho. A classificação procedeu por casamento de padrões sobre título, resumo e conceitos atribuídos pelo OpenAlex, e admite múltiplas categorias por dimensão.

A distribuição quantitativa por dimensão consta da Tabela 1. Os números refletem ocorrências de termos característicos no corpo textual disponível para cada item e devem ser lidos como aproximação inicial, a ser refinada por leitura humana ao redigir o Capítulo 2.

| Dimensão | Categorias com maior incidência (n) |
|---|---|
| D1, consistência | eventual (15); forte (11); causal (7); SEC/CRDT (5); sequencial (3) |
| D2, modelo de falhas | não especificado (138); genérico ou misto (22); partição (4); bizantino (4) |
| D3, replicação e reconciliação | não classificado (132); consenso (26); SMR (6); CRDT (5) |
| D4, abordagem formal | sem método formal (132); MC genérico (14); Coq (7); prova de teorema (7); PMC (3) |
| D5, validação empírica | não-empírico ou abstract curto (117); experimental (41); simulação (8); IMDG ou KV identificado (4) |
| D6, domínio | outro (133); KV genérico (13); sessão e autenticação (13); BD distribuído (6) |
| D7, métricas | não especificadas (133); latência (18); vazão (11); latência de cauda (7); consumo de recursos (4); taxa de violação (1) |
| D8, mecanismo adaptativo | nenhum (146); autoadaptativo (10); aprendizado de máquina (5); autoescala (5) |

A alta incidência das categorias residuais ("não especificado", "outro") em D2, D3, D6 e D7 decorre de uma combinação de três fatores: resumos curtos sem terminologia padronizada; abstracts ausentes para parte do conjunto; e a heurística automática, conservadora por opção, que evita falsos positivos. A leitura humana nas próximas iterações deve reduzir esses resíduos. As constatações qualitativas a seguir consideram apenas os 32 itens classificáveis em pelo menos quatro dimensões com categoria substantiva.

## 2. Síntese descritiva por dimensão

### 2.1 Consistência e replicação (D1, D3)

A literatura selecionada distribui-se entre dois polos. De um lado, trabalhos que usam consistência eventual ou SEC com CRDT, e que tratam reconciliação por estruturas convergentes; do outro, trabalhos sobre consenso, com SMR como abstração dominante. A presença de 7 trabalhos categorizados em "causal" indica que consistência causal, e suas extensões em sessão, têm tração na produção atual; isso aparece, por exemplo, em verificação formal de KV stores causalmente consistentes. A categoria "por aplicação" foi detectada em apenas um item, o que sugere subnotificação no abstract; verificações pontuais no texto completo devem confirmar essa categoria em pelo menos cinco itens adicionais. O conjunto é compatível com o recorte do trabalho, que cobre replicação assíncrona, CRDT, vector clocks e consenso, como apresentado em [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md).

### 2.2 Modelo de falhas (D2)

A predominância da categoria "não especificado" (138 itens) é consistente com a prática de abstracts que assumem o modelo de falhas sem nomear classes formais. A leitura completa deve recuperar a classificação correta na maioria dos casos. A categoria "partição" aparece em 4 itens; "bizantino" em 4; "crash-recovery" em apenas 1. O número de itens declaradamente bizantinos é compatível com o escopo do TCC, que exclui falhas bizantinas (Decisão 007 e [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md), seção 5).

### 2.3 Métodos formais (D4)

Dos 33 itens classificados em alguma categoria formal, model checking genérico aparece em 14, Coq em 7, prova de teorema em 7, PMC em 3, Isabelle em 2, TLA+ ou TLC em 2 e BMC em 2. A pequena incidência explícita de TLA+ na classificação automática contrasta com a presença reconhecida das referências âncora do trabalho (REF-003, REF-017, REF-019) e indica que a ferramenta é frequentemente mencionada por sigla ou por nome de variantes que escapam ao classificador inicial. A leitura humana, ao construir a fundamentação do Capítulo 2, deve recuperar essas ocorrências e produzir um quadro comparativo entre TLA+/TLC, Alloy e a ferramenta Must.

### 2.4 Validação empírica e domínio (D5, D6)

Quarenta e um itens são experimentais, dos quais 4 declaram explicitamente uso de IMDG ou KV identificado. Esse número é baixo e reflete a generalidade do vocabulário em abstracts, não a ausência de uso real do KV ou IMDG; entradas como Cassandra, Redis, Spanner, CockroachDB, RAMCloud e Anna aparecem na seleção mas só foram pinçadas pelo classificador quando o nome consta do título ou do resumo. A leitura completa deve elevar o número de itens com plataforma identificada para a faixa de 30 a 50. O domínio "sessão e autenticação" foi classificado em 13 itens, com sobreposição parcial com a categoria "BD distribuído"; esse conjunto é o que mais diretamente alimenta as seções 2.4 e 2.9 do Capítulo 2.

### 2.5 Métricas (D7)

Dezoito itens reportam latência, sete dos quais a latência de cauda; onze reportam vazão; quatro reportam consumo de recursos, e apenas um reporta taxa de violação de invariantes. A baixa incidência de "taxa de violação" é o achado mais relevante para o recorte: a métrica M2, definida em [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md), permanece pertinente como contribuição do trabalho, dado que poucos estudos a quantificam de forma explícita. A combinação latência de cauda e taxa de violação, central neste TCC, aparece em poucos trabalhos da seleção, o que sustenta a tese de uma lacuna a ser ocupada.

### 2.6 Mecanismos adaptativos (D8)

Vinte e um itens apresentam algum mecanismo adaptativo: dez autoadaptativos, cinco baseados em aprendizado de máquina, cinco com autoescala, um heurístico baseado em regras. A escassez declarada de mecanismos heurísticos com regras determinísticas é compatível com o desenho do controlador previsto para TCC-II, que adota histerese e limites de taxa de mudança, mecanismos pouco representados na amostra. Essa categoria também terá ganho na leitura completa, dada a probabilidade de itens com termos correlatos não cobertos pela heurística.

## 3. Trabalhos-âncora e exemplares do conjunto

A seleção contém os clássicos teóricos e os trabalhos contemporâneos identificados como âncoras no protocolo, com cobertura em todas as dimensões relevantes. A Tabela 2 lista, por dimensão, os exemplares observados com presença em três ou mais dimensões substantivas.

| Dimensão de entrada | Exemplares observados |
|---|---|
| Verificação formal de KV consistente | Chapar (Lesani, Bell, Chlipala, 2016); Verdi (Wilcox et al., 2015); Hermes (Katsarakis et al., 2020); Paxos Deconstructed (García-Pérez et al., 2018) |
| Verificação formal aplicada a protocolos distribuídos | Newcombe, "Why Amazon Chose TLA+" (REF-017); Enea, "Model Checking Distributed Protocols in Must" (REF-019); Gao, "MC Guided Incremental Testing" (REF-018); IVy (Padon et al., 2018) |
| Inferência e teste de invariantes | "Inferring and Asserting Distributed System Invariants" (Grant, Cech, Beschastnikh, 2018); "Greybox Fuzzing of Distributed Systems" (Meng et al., 2023) |
| KV stores reais e suas garantias | Spanner (Corbett et al., 2013); CockroachDB (Taft et al., 2020); RAMCloud (Ousterhout et al., 2015); Cure (Akkoorath et al., 2016); Anna (Wu et al., REF-026) |
| Latência de cauda em KV | TailX (Jaiman, Ben Mokhtar, Rivière, REF-015); Taming Tail Latency (Ben Mokhtar et al., REF-021, REF-022) |
| Controle adaptativo em nuvem | Ullah et al., "Control theoretical view of cloud elasticity" (REF-025); Gambi, Toffetti, Pezzè, "Assurance of Self-adaptive Controllers" (REF-028); ControCity (Ghobaei-Arani et al., 2019); Anna autoescala (Wu et al., REF-026) |
| Consistência fraca com monitoramento | Nguyen et al., REF-023; "Eventual Consistency Today" (Bailis, Ghodsi, 2013) |

Os itens em negrito por sigla REF-XXX são oficiais (biblioteca aprovada). Os itens sem REF integram a seleção de candidatos para aprovação individual, conforme detalhamento no Bibliotecário, abaixo.

## 4. Implicações para o recorte do protótipo

A classificação confirma o conjunto de operações O1 a O7, de falhas F1 a F4, e de invariantes I1 a I6 aprovados pela Decisão 007. Os refinamentos sugeridos para a próxima iteração de [`11-recorte-criterios-tcc1.md`](11-recorte-criterios-tcc1.md), no escopo do TCC-I, são os seguintes.

Primeiro, a literatura recente sobre KV stores causalmente consistentes (Chapar, Cure) sugere considerar, como variante experimental opcional em TCC-II, leituras com garantia de sessão (read-your-writes, monotonic reads) como configuração alternativa do cliente; em TCC-I esta variante permanece fora do escopo. Segundo, a métrica M2 (taxa de violação por invariante) mantém-se como contribuição: a literatura raramente a reporta diretamente, o que reforça sua justificativa no Capítulo 5. Terceiro, a categoria D4 mostra que ferramentas alternativas a TLA+/TLC, em particular Must e abordagens baseadas em Coq ou Isabelle, têm presença suficiente para justificar a discussão da pendência P-017 (escolha da ferramenta) com base na cobertura observada no mapeamento, decisão a ser tomada em Sem 7-8.

## 5. Lacunas remanescentes para leitura completa

A leitura humana das próximas duas semanas deve priorizar três frentes. A primeira é a recuperação dos 19 itens sem abstract textual, cuja classificação atual está limitada ao título. A segunda é a re-classificação dos itens que caíram em D2 "não especificado", D3 "não classificado", D6 "outro" e D7 "não especificadas" — alvo de cerca de 130 itens, do qual estima-se promover a categorias substantivas entre 60 e 90. A terceira é o registro, em [`05-referencias-candidatas.md`](05-referencias-candidatas.md), dos itens com presença em três ou mais dimensões substantivas e com indicação para citação no Capítulo 2, faixa estimada em 25 a 35 itens, a serem submetidos ao Gestor para aprovação individual.

## Próximas atualizações esperadas neste arquivo

Quando a leitura completa avançar, a Tabela 1 receberá uma coluna adicional com a contagem revisada, a Tabela 2 será preenchida com a numeração definitiva (MS-001, MS-002, ...) atribuída pela planilha de execução, e a seção 3 incorporará as referências individuais com a sigla CAND-XXX para os itens promovidos a candidatos.
