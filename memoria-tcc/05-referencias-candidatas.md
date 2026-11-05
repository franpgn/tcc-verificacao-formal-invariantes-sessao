# Referências Candidatas

> Mantida pelo **Bibliotecário**. Referências **ainda não aprovadas** pelo Gestor — **o Escritor não pode citá-las**.
>
> **Aviso de procedência (protocolo de segurança acadêmica):** os itens abaixo são *indicações de obras conhecidas* que provavelmente cobrem as lacunas teóricas/metodológicas do TCC. **Dados bibliográficos completos (veículo exato, ano, páginas, DOI, BibTeX) ainda NÃO foram verificados por busca.** Nenhum item será promovido a [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md) sem (1) busca e verificação pelo Bibliotecário nas bases de [`02-metodologia.md`](02-metodologia.md) e (2) validação explícita do Gestor. Itens marcados *(a verificar)* têm ano/veículo apenas como **estimativa de boa-fé**, não como afirmação.

Convenção de IDs: `CAND-0XX`.

---

## Eixo A — Sistemas distribuídos, consistência e replicação

### Promovidas a REF oficial (Decisão 012, 2026-05-17)
- **CAND-001** → **REF-042** (Lamport 1978, *Time, Clocks*) — texto integral já em `/workspace/leituras/CAND-001-Lamport-Time-Clocks-1978.txt`.
- **CAND-004** → **REF-043** (Ongaro & Ousterhout 2014, Raft) — PDF OA baixado de `raft.github.io/raft.pdf`, texto em `/workspace/leituras/REF-043-Ongaro-Ousterhout-Raft-2014.txt`.
- **CAND-005** (Herlihy & Wing 1990, Linearizability) — **aprovada em princípio** na Decisão 012, aguardando aquisição manual via CAPES/ACM (DOI 10.1145/78969.78972).

### Dispensadas (Decisão 012, 2026-05-17)
- **CAND-024 → REF-012** (CRDTs Encyclopedia) — redundante com REF-002.
- **CAND-026 → REF-014** (Benchmarking caches Salhi 2018) — coberta por 4 REFs Infinispan + REF-013/015.

### [CAND-001] Time, Clocks, and the Ordering of Events in a Distributed System *(PROMOVIDA — REF-042)*
- **Autores:** Lamport, Leslie. **Ano (a verificar):** 1978. **Fonte (a verificar):** *Communications of the ACM*.
- **Resumo:** texto seminal sobre ordem parcial de eventos, relação *happened-before* e relógios lógicos.
- **Justificativa de relevância:** fundamenta a noção de "*timestamp* lógico", reordenação de eventos e ordem causal — centrais ao modelo formal do canal de replicação assíncrona.
- **Possível uso no TCC:** Cap. 2 (2.1), Cap. 4 (modelo formal).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-002] Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services
- **Autores:** Gilbert, Seth; Lynch, Nancy. **Ano (a verificar):** 2002. **Fonte (a verificar):** *ACM SIGACT News*.
- **Resumo:** formalização e prova do teorema CAP.
- **Justificativa de relevância:** enquadra teoricamente o *trade-off* entre consistência e disponibilidade sob partições — premissa do problema de pesquisa.
- **Possível uso no TCC:** Cap. 1, Cap. 2 (2.1, 2.4).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-003] Implementing Fault-Tolerant Services Using the State Machine Approach: A Tutorial
- **Autores:** Schneider, Fred B. **Ano (a verificar):** 1990. **Fonte (a verificar):** *ACM Computing Surveys*.
- **Resumo:** abordagem de replicação por máquina de estados (SMR) para tolerância a falhas.
- **Justificativa de relevância:** contraponto formal à replicação assíncrona/otimista; vocabulário de réplicas determinísticas e ordem total.
- **Possível uso no TCC:** Cap. 2 (2.3, 2.4).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-004] In Search of an Understandable Consensus Algorithm (Raft) *(PROMOVIDA — REF-043)*
- **Autores:** Ongaro, Diego; Ousterhout, John. **Ano (a verificar):** 2014. **Fonte (a verificar):** *USENIX ATC*.
- **Resumo:** algoritmo de consenso Raft (eleição de líder, replicação de *log*, segurança).
- **Justificativa de relevância:** caracteriza a alternativa "síncrona/quórum" mencionada na Introdução do base; útil para descrever *failover* coordenado.
- **Possível uso no TCC:** Cap. 2 (2.3, 2.4).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-005] Linearizability: A Correctness Condition for Concurrent Objects *(PROMOVIDA — REF-044, 2026-05-17 noite)*
- **Autores:** Herlihy, Maurice P.; Wing, Jeannette M. **Ano (a verificar):** 1990. **Fonte (a verificar):** *ACM TOPLAS*.
- **Resumo:** define linearizabilidade como critério de correção de objetos concorrentes.
- **Justificativa de relevância:** referência canônica de modelo de consistência forte, contraste com consistência eventual; vocabulário para enunciar invariantes.
- **Possível uso no TCC:** Cap. 2 (2.1).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-006] Unreliable Failure Detectors for Reliable Distributed Systems
- **Autores:** Chandra, Tushar Deepak; Toueg, Sam. **Ano (a verificar):** 1996. **Fonte (a verificar):** *Journal of the ACM*.
- **Resumo:** teoria de detectores de falha não confiáveis e suas classes.
- **Justificativa de relevância:** fundamenta a modelagem de detecção de falhas que dispara *failover*.
- **Possível uso no TCC:** Cap. 2 (2.4); Cap. 4 (modelo de falhas).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-007] The Network is Reliable: An Informal Survey of Real-World Communications Failures
- **Autores:** Bailis, Peter; Kingsbury, Kyle. **Ano (a verificar):** 2014. **Fonte (a verificar):** *Communications of the ACM* / *ACM Queue*.
- **Resumo:** levantamento de falhas de rede reais (partições, atrasos, perdas) em sistemas de produção.
- **Justificativa de relevância:** evidência empírica de que partições e *failover* são condições reais — reforça a justificativa do TCC.
- **Possível uso no TCC:** Cap. 1 (justificativa), Cap. 2 (2.4).
- **Status:** Aguardando busca/verificação e validação do Gestor.

---

## Eixo B — *In-Memory Data Grids* / caches distribuídos

### [CAND-008] *Survey* / artigo de referência sobre *In-Memory Data Grids* (a identificar)
- **Resumo:** visão comparativa de IMDGs (arquitetura, particionamento, replicação, casos de uso). Candidatos a buscar: artigos de *survey* em periódicos IEEE/ACM/Elsevier; capítulos de livro sobre *NoSQL*/*in-memory computing*.
- **Justificativa de relevância:** o base usa o termo IMDG sem uma referência conceitual acadêmica (apenas a documentação do Infinispan) — lacuna a preencher no Cap. 2 (2.2).
- **Possível uso no TCC:** Cap. 2 (2.2).
- **Status:** **Busca pendente** — sugiro autorização do Gestor para localizar 1–2 referências acadêmicas de IMDG.

### [CAND-009] Documentação técnica complementar de Infinispan (*partition handling*, *transactions*, *expiration*)
- **Resumo:** páginas específicas da doc. oficial (já temos a doc. geral em REF-006) que detalham comportamento sob partições, transações e expiração — relevantes ao modelo de falhas e ao estado de sessão.
- **Justificativa de relevância:** precisão técnica do Cap. 4.
- **Possível uso no TCC:** Cap. 4.
- **Status:** A consolidar (mesma fonte que REF-006; decidir granularidade de citação com o Gestor).

---

## Eixo C — Métodos formais

### [CAND-010] Specifying Systems: The TLA+ Language and Tools for Hardware and Software Engineers *(PROMOVIDA — REF-047, 2026-06-18, Tarefa T-A)*
- **Autores:** Lamport, Leslie. **Ano (verificado):** 2002. **Fonte (verificada):** Addison-Wesley, Boston, MA (livro). **ISBN:** 978-0-321-14306-8.
- **Resumo:** livro de referência de TLA+/PlusCal e do uso prático de especificação e *model checking*.
- **Justificativa de relevância:** referência primária da linguagem de especificação a ser usada (complementa REF-003, que é sobre o verificador TLC). Na subseção T-A, fundamenta a apresentação do TLA+ como linguagem escolhida.
- **Possível uso no TCC:** Cap. 2 (2.5), Cap. 3 (subseção de justificativa da escolha do TLA+), Cap. 4.
- **Status:** **Promovida a REF-047** (Tarefa T-A aprovada pelo Gestor, 2026-06-18). Metadados verificados via InformIT/Amazon/Google Books. BibTeX `lamport_specifying_systems_2002` em `bibliografia.bib`.

### [CAND-011] How Amazon Web Services Uses Formal Methods *(PROMOVIDA — REF-048, 2026-06-18, Tarefa T-A)*
- **Autores:** Newcombe, Chris; Rath, Tim; Zhang, Fan; Munteanu, Bogdan; Brooker, Marc; Deardeuff, Michael. **Ano (verificado):** 2015. **Fonte (verificada):** *Communications of the ACM*, v. 58, n. 4, p. 66-73. **DOI:** 10.1145/2699417.
- **Resumo:** relato industrial do uso de TLA+ para encontrar defeitos sutis em sistemas distribuídos de produção (incl. de armazenamento/replicação).
- **Justificativa de relevância:** evidência prática que justifica a escolha metodológica do TCC (verificação formal aplicada a replicação/consistência). Na subseção T-A, ancora o argumento de adoção industrial do TLA+.
- **Possível uso no TCC:** Cap. 1 (justificativa), Cap. 2 (2.5, trabalhos relacionados), Cap. 3 (subseção de justificativa da escolha do TLA+).
- **Nota:** distinta de REF-017 (Newcombe, "Why Amazon Chose TLA+", ABZ 2014) — mesmo autor principal e tema, mas obra/venue diferentes. Ambas citáveis; T-A pode usar as duas em conjunto.
- **Status:** **Promovida a REF-048** (Tarefa T-A aprovada pelo Gestor, 2026-06-18). Metadados verificados via ACM DL/dblp/Semantic Scholar. BibTeX `newcombe_aws_formal_methods_2015` em `bibliografia.bib`.

### [CAND-012] Model Checking (livro de referência)
- **Autores (a verificar):** Clarke, Edmund M.; Grumberg, Orna; Peled, Doron A. (e/ou edição com Kroening, Veith). **Ano (a verificar):** 1999 (1ª ed.) / 2018 (2ª ed.). **Fonte:** MIT Press (livro).
- **Resumo:** texto canônico sobre verificação de modelos (espaço de estados, BDDs, *bounded model checking*, LTL/CTL).
- **Justificativa de relevância:** fundamentação geral da técnica no Cap. 2 (2.5).
- **Possível uso no TCC:** Cap. 2 (2.5).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-047] Software Abstractions: Logic, Language, and Analysis *(PROMOVIDA — REF-049, 2026-06-18, Tarefa T-A)*
- **Autores:** Jackson, Daniel. **Ano (verificado):** 2012 (2. ed.). **Fonte (verificada):** MIT Press, Cambridge, MA (livro). **ISBN:** 978-0-262-01715-2.
- **Resumo:** livro de referência da linguagem/ferramenta **Alloy** e da abordagem de *lightweight formal methods* (análise por modelos baseada em lógica relacional e *bounded analysis* via SAT).
- **Justificativa de relevância:** referência primária da **alternativa Alloy** ao TLA+ — necessária para a subseção T-A comparar a escolha do TLA+ com abordagens baseadas em modelagem relacional/SAT.
- **Possível uso no TCC:** Cap. 3 (subseção de justificativa da escolha do TLA+ — alternativa considerada e descartada); Cap. 2 (2.5).
- **Origem:** indicada na avaliação anterior do Bibliotecário para a Tarefa T-A; metadados verificados via MIT Press/Amazon UK/Internet Archive. *(Atenção: ISBN 978-0-262-01715-2 = 2. ed. capa dura 2012; a ed. revisada 2016 tem ISBN 978-0-262-52890-0 — não confundir.)*
- **Status:** **Promovida a REF-049** (Tarefa T-A aprovada pelo Gestor, 2026-06-18). BibTeX `jackson_software_abstractions_2012` em `bibliografia.bib`.

### [CAND-048] The SPIN Model Checker: Primer and Reference Manual *(PROMOVIDA — REF-050, 2026-06-18, Tarefa T-A)*
- **Autores:** Holzmann, Gerard J. **Ano (verificado):** 2004. **Fonte (verificada):** Addison-Wesley, Boston, MA (livro). **ISBN:** 978-0-321-22862-8.
- **Resumo:** livro de referência do **SPIN** e da linguagem **Promela** para verificação de modelos de sistemas concorrentes/distribuídos (LTL, busca em espaço de estados, *partial-order reduction*).
- **Justificativa de relevância:** referência primária da **alternativa SPIN/Promela** ao TLA+ — necessária para a subseção T-A comparar a escolha do TLA+ com outro verificador de modelos consolidado e amplamente usado.
- **Possível uso no TCC:** Cap. 3 (subseção de justificativa da escolha do TLA+ — alternativa considerada e descartada); Cap. 2 (2.5).
- **Origem:** indicada na avaliação anterior do Bibliotecário para a Tarefa T-A; metadados verificados via Pearson/AbeBooks/ACM DL.
- **Status:** **Promovida a REF-050** (Tarefa T-A aprovada pelo Gestor, 2026-06-18). BibTeX `holzmann_spin_2004` em `bibliografia.bib`.

---

## Eixo D — SLO, latência e observabilidade

*(Cobertura inicial já existe: REF-004 "The Tail at Scale", REF-005 "SRE Book — SLOs", REF-008 "Infinispan Metrics".)*

### [CAND-013] Site Reliability Engineering / The Site Reliability Workbook (capítulos sobre SLO/SLI/error budget)
- **Autores:** Beyer, Betsy *et al.* (eds.). **Ano (a verificar):** 2016 / 2018. **Fonte:** O'Reilly / sre.google.
- **Justificativa de relevância:** complementa REF-005 com tratamento mais operacional de SLO; útil ao definir as metas do controlador.
- **Possível uso no TCC:** Cap. 2 (2.6), Cap. 4.
- **Status:** Aguardando validação do Gestor (decidir se é necessário além de REF-005).

---

## Eixo E — Sistemas autoadaptativos / controle por realimentação

### [CAND-014] An Architectural Blueprint for Autonomic Computing (MAPE-K)
- **Autores:** IBM. **Ano (a verificar):** 2005. **Fonte (a verificar):** *IBM White Paper* (4th ed.).
- **Resumo:** modelo de referência *autonomic computing* — laço MAPE-K (*Monitor–Analyze–Plan–Execute over Knowledge*).
- **Justificativa de relevância:** enquadra conceitualmente o "mecanismo de decisão heurístico" como laço de controle observação→análise→decisão→ação.
- **Possível uso no TCC:** Cap. 2 (2.7), Cap. 4 (controlador).
- **Status:** Aguardando busca/verificação e validação do Gestor. *(Atenção: é fonte industrial — usar como apoio; buscar também referência acadêmica de* self-adaptive systems*.)*

### [CAND-015] Software Engineering for Self-Adaptive Systems (*roadmap*)
- **Autores (a verificar):** Cheng, B. H. C. *et al.* (e edições seguintes — de Lemos *et al.*). **Ano (a verificar):** 2009 / 2013. **Fonte:** Springer LNCS.
- **Resumo:** *roadmap* de pesquisa em sistemas autoadaptativos (modelagem, garantias, avaliação).
- **Justificativa de relevância:** base acadêmica para o capítulo 2.7, complementando CAND-014.
- **Possível uso no TCC:** Cap. 2 (2.7).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-016] Feedback Control of Computing Systems
- **Autores:** Hellerstein, Joseph L.; Diao, Yixin; Parekh, Sujay; Tilbury, Dawn M. **Ano (a verificar):** 2004. **Fonte:** Wiley (livro).
- **Resumo:** aplicação de teoria de controle a sistemas computacionais (estabilidade, *settling time*, evitar oscilação/*flapping*).
- **Justificativa de relevância:** embasa o uso de **histerese** e **limites de taxa de mudança** para evitar *flapping* no controlador.
- **Possível uso no TCC:** Cap. 2 (2.7), Cap. 4.
- **Status:** Aguardando busca/verificação e validação do Gestor.

---

## Eixo F — Tolerância a falhas aplicada / *chaos engineering*

### [CAND-017] Principles of Chaos Engineering / Chaos Engineering (livro O'Reilly)
- **Autores (a verificar):** Basiri, Ali *et al.* (artigo IEEE Software, 2016) e/ou Rosenthal, Casey *et al.* (livro, 2020). **Fonte:** IEEE Software / O'Reilly.
- **Resumo:** princípios e práticas de injeção controlada de falhas para validar resiliência.
- **Justificativa de relevância:** fundamenta a metodologia de injeção de falhas parametrizada do TCC.
- **Possível uso no TCC:** Cap. 3 (cenários de falha).
- **Status:** Aguardando busca/verificação e validação do Gestor.

### [CAND-018] Trabalhos do projeto *Jepsen* (Kyle Kingsbury) sobre testes de consistência sob partição
- **Resumo:** análises empíricas de violações de consistência em sistemas distribuídos sob partição (relatórios técnicos; alguns publicados em *venues* acadêmicos).
- **Justificativa de relevância:** metodologia próxima à trilha experimental do TCC (gerar carga, particionar, checar invariantes).
- **Possível uso no TCC:** Cap. 2 (trabalhos relacionados), Cap. 3.
- **Status:** **Busca pendente** — verificar quais resultados *Jepsen* têm publicação revisada por pares antes de usar como base.

---

## Eixo G — Metodologia de pesquisa

### [CAND-019] Design Science Methodology for Information Systems and Software Engineering
- **Autores (a verificar):** Wieringa, Roel J. **Ano (a verificar):** 2014. **Fonte:** Springer (livro).
- **Justificativa de relevância:** enquadra a parte de "pesquisa de projeto" (construção e avaliação de artefatos).
- **Possível uso no TCC:** Cap. 3 (3.1).
- **Status:** Aguardando validação do Gestor (também verificar a metodologia preferida pelo curso/orientador).

### [CAND-020] Experimentation in Software Engineering
- **Autores (a verificar):** Wohlin, Claes; Runeson, Per; Höst, Martin; Ohlsson, Magnus C.; Regnell, Björn; Wesslén, Anders. **Ano (a verificar):** 2012 (2ª ed.). **Fonte:** Springer (livro).
- **Justificativa de relevância:** base para desenho experimental, repetições, análise estatística e ameaças à validade (P-007, P-011).
- **Possível uso no TCC:** Cap. 3 (3.6, 3.7).
- **Status:** Aguardando validação do Gestor.

> **Nota (2026-09-24):** CAND-019/020 cobrem metodologia de *pesquisa* em ES (desenho científico, artefato + avaliação). Não cobrem especificamente a metodologia de **desenvolvimento/engenharia com métodos formais** (processo de especificar, verificar e refinar). CAND-051..054 abaixo fecham essa lacuna, a pedido do Gestor.

### [CAND-051] Using Z: Specification, Refinement, and Proof
- **Autores:** Woodcock, Jim; Davies, Jim. **Ano:** 1996. **Fonte:** Prentice Hall International Series in Computer Science (livro).
- **Resumo:** manual clássico do método formal Z — cobre o *processo* de especificação em Z, técnicas de refinamento de dados/operações e condução de provas, com estudos de caso completos.
- **Justificativa de relevância:** referência canônica de **como conduzir, passo a passo, o ciclo especificar→refinar→provar** com um método formal — exatamente a lacuna metodológica do eixo G que CAND-019/020 (metodologia de pesquisa em ES genérica) não cobrem. Ancora metodologicamente a condução da frente formal do TCC (especificação do modelo, refinamento até o controlador, verificação de invariantes).
- **Possível uso no TCC:** Cap. 3 (3.2 — descrição do processo de modelagem/verificação formal).
- **Status:** Aguardando validação do Gestor.

### [CAND-052] Modeling in Event-B: System and Software Engineering
- **Autores:** Abrial, Jean-Raymond. **Ano:** 2010. **Fonte:** Cambridge University Press (livro).
- **Resumo:** método Event-B para modelagem e desenvolvimento por refinamento sucessivo de sistemas (de uma especificação abstrata a um modelo detalhado, com prova de invariantes a cada passo), com estudos de caso de sistemas reativos/distribuídos.
- **Justificativa de relevância:** metodologia de desenvolvimento formal por **refinamento incremental com preservação de invariantes**, o paralelo mais próximo (entre os candidatos considerados) do processo real seguido no TCC ao evoluir o modelo TLA+ do núcleo de sessão até incorporar réplicas, falhas e controlador. Complementa Woodcock & Davies (Z) com uma tradição de refinamento orientada a sistemas reativos/distribuídos.
- **Nota (2026-09-24, leitura do livro completo):** o Cap. 4 ("A simple file transfer protocol") é um estudo de caso quase diretamente transponível para a frente formal do TCC — um protocolo distribuído sender/receiver é desenvolvido em 3 refinamentos sucessivos (abstração de cópia "mágica" em um passo → simulação com acesso direto → canais explícitos com mensagens/ACK → otimização por paridade), cada estágio preservando os invariantes de correspondência entre estado abstrato e concreto; o padrão "anticipated event" (§4.7) evita introduzir variáveis fantasma entre refinamentos, um problema de engenharia análogo ao de manter invariantes de sessão estáveis conforme o modelo evolui de single-node para replicado. O Prólogo também fornece o vocabulário "problem validation vs. solution validation" — útil para argumentar por que a verificação formal do TCC ataca a corretude do protocolo (problema), não apenas a implementação (solução), sendo complementada pela validação empírica.
- **Possível uso no TCC:** Cap. 3 (3.2, 3.3 — refinamento do modelo formal).
- **Status:** Aguardando validação do Gestor.

### [CAND-053] Principles of Model Checking
- **Autores:** Baier, Christel; Katoen, Joost-Pieter. **Ano:** 2008. **Fonte:** MIT Press (livro).
- **Resumo:** tratamento sistemático da teoria e prática de *model checking* — lógica temporal, algoritmos de verificação, redução de espaço de estados — com orientação metodológica sobre como modelar um sistema e formular propriedades verificáveis.
- **Justificativa de relevância:** o TCC usa TLC (*model checker* do TLA+) como ferramenta central de verificação; esta obra é a referência textbook canônica sobre a **metodologia de aplicar model checking** (modelagem, escolha de propriedades, interpretação de contraexemplos) — distinta de CAND-019/020 (metodologia de pesquisa genérica) e complementar a REF-003 (Yu & Lamport, já oficial, sobre TLC especificamente).
- **Nota (2026-09-24, leitura do livro completo, Cap. 1 e Cap. 3 §3.1-3.5):** o Cap. 1 formaliza o processo de model checking em 3 fases (modeling → running → analysis, §1.2.1) que corresponde exatamente ao ciclo especificar/rodar TLC/interpretar contraexemplo seguido no TCC, e explicita a distinção verificação ("estamos construindo a coisa certo?") vs. validação ("estamos verificando a coisa certa?") — útil para justificar metodologicamente por que a verificação TLA+/TLC precisa ser complementada por validação empírica (chaos engineering) no TCC. O Cap. 3 fornece a definição formal precisa de invariante (condição válida em todo estado alcançável, checável por busca em profundidade) e o teorema de decomposição de qualquer propriedade linear-time em uma parte de segurança (safety) e uma de vivacidade (liveness), além das três formas de fairness (unconditional/strong/weak) — vocabulário formal que sustenta diretamente a definição dos invariantes de sessão e das propriedades de progresso (ex.: failover eventualmente completa) usadas na frente TLA+ do TCC.
- **Possível uso no TCC:** Cap. 3 (3.4 — verificação formal, interpretação de contraexemplos).
- **Status:** Aguardando validação do Gestor.

### [CAND-054] Ten Commandments of Formal Methods (+ revisões "Ten Commandments Revisited" 2006 e "...Ten Years On" 2009)
- **Autores:** Bowen, Jonathan P.; Hinchey, Michael G. **Ano:** 1995 (original, *IEEE Computer*); 2006 e 2009 (revisões). **Fonte:** IEEE Computer / capítulos de livro (FMICS workshops).
- **Resumo:** conjunto de diretrizes práticas curtas (dez "mandamentos") para conduzir projetos de engenharia com métodos formais na indústria, revisitadas duas vezes com uma década de experiência adicional cada.
- **Justificativa de relevância:** complementa os três candidatos acima (que são metodologias/manuais extensos de métodos específicos) com **lições práticas e diretrizes de condução de projeto** ao aplicar métodos formais — útil para justificar decisões de escopo/parcimônia na frente formal do TCC (por que nem tudo foi formalizado, por que o controlador é heurístico e não formalmente sintetizado).
- **Nota (2026-09-24, artigo completo lido — só a capa era legível na tentativa anterior):** dois mandamentos, lidos agora na íntegra, mapeiam diretamente para decisões já tomadas no TCC: o Mandamento II ("thou shalt formalize but not over-formalize", com os três níveis de rigor — especificação formal / desenvolvimento-verificação formal / prova mecanicamente checada — como escala explícita de quanto formalizar) justifica a escolha de aplicar TLA+/TLC em nível de especificação+verificação por model checking, sem buscar prova mecanizada completa; o Mandamento IX ("thou shalt test, test, and test again") argumenta explicitamente que métodos formais nunca eliminam a necessidade de teste porque a especificação em si pode não capturar a intenção real e porque o refinamento até código sempre admite erro humano residual — sustentando diretamente a decisão metodológica do TCC de combinar verificação formal (TLA+/TLC) com validação empírica (testes de failover/chaos engineering no protótipo).
- **Possível uso no TCC:** Cap. 3 (3.1, justificativa metodológica), Cap. 6 (limitações/decisões de escopo).
- **Status:** Aguardando validação do Gestor.

---

## Eixo H — Gerência de sessão (domínio de aplicação)

### [CAND-021] Referência de boas práticas de gerência de sessão (p.ex. *OWASP Session Management Cheat Sheet* / *NIST SP 800-63B*)
- **Resumo:** boas práticas para ciclo de vida de sessão (criação, validação, expiração, invalidação, bloqueio por tentativas).
- **Justificativa de relevância:** ancora o *porquê* dos invariantes escolhidos (irreversibilidade de *logout*, contadores de tentativas, bloqueio).
- **Possível uso no TCC:** Cap. 1 (justificativa), Cap. 2 (motivação do estudo de caso).
- **Status:** Aguardando busca/verificação e validação do Gestor. *(Atenção: fontes técnicas/normativas — adequadas, mas confirmar pertinência acadêmica com o Gestor.)*

---

## Pendência geral de busca

> **P-015:** O Bibliotecário recomenda ao Gestor autorizar uma rodada de busca e verificação bibliográfica, priorizando os eixos A, C e E (núcleo conceitual do TCC), seguidos de F, G e B. Cada item retornará no formato "Referência candidata" completo (com link e BibTeX verificados) para validação individual.

> **Atualização 2026-05-12 (Decisão 005):** o Gestor forneceu material curado em `/workspace/referencias-gestor/` (4 PDFs + 5 CSVs + lista de URLs). O Bibliotecário extraiu **25 novas candidatas (CAND-022..046)** registradas abaixo, com metadados verificados via leitura direta dos PDFs ou via Crossref/API. Análise consolidada em [`10-material-gestor-analise.md`](10-material-gestor-analise.md). **Estas candidatas ainda dependem de aprovação individual do Gestor para se tornarem citáveis.**

---

# Parte II — Candidatas extraídas do material curado pelo Gestor (CAND-022..046)

> Material em `/workspace/referencias-gestor/`. Metadados confirmados a partir do PDF ou via Crossref.
>
> **Status (atualizado 2026-05-12 — Decisão 006):** **22 itens aprovados em lote** e migrados para [`04-biblioteca-oficial.md`](04-biblioteca-oficial.md) (Parte II) como **REF-010..REF-031**. **2 itens descartados:** CAND-044 (Jain et al. — clustering) e CAND-046 (Benítez-Pérez — modelagem genérica). **1 item pendente:** CAND-033 (Chen 2026 — context awareness + MC).
>
> Mapeamento aprovação → biblioteca oficial:
> - CAND-022 → REF-010; CAND-023 → REF-011; CAND-024 → REF-012; CAND-025 → REF-013;
> - CAND-026 → REF-014; CAND-027 → REF-015;
> - CAND-028 (FLP) → REF-016; CAND-029 → REF-017; CAND-030 → REF-018; CAND-031 → REF-019; CAND-032 → REF-020;
> - CAND-034 → REF-021; CAND-035 → REF-022; CAND-036 → REF-023; CAND-037 → REF-024;
> - CAND-038 → REF-025; CAND-039 → REF-026; CAND-040 → REF-027; CAND-041 → REF-028; CAND-042 → REF-029;
> - CAND-043 → REF-030; CAND-045 → REF-031.
>
> **Decisão 009 — itens nomeados pelo Gestor para fechar lacunas F e H, migrados diretamente:**
> - CAND-002 (Gilbert & Lynch CAP 2002) → REF-032;
> - CAND-006 (Chandra & Toueg 1996) → REF-033;
> - CAND-007 (Bailis & Kingsbury 2014) → REF-034;
> - CAND-021 (parte OWASP) → REF-035; CAND-021 (parte NIST SP 800-63B) → REF-036.
>
> **Jepsen (CAND-018):** mantida como candidata em Parte I — uso depende de decisão caso a caso devido à natureza não-revisada por pares dos relatórios técnicos.

---

## Eixo A (continuação) — Replicação/consistência

### [CAND-022] Consistency in Scalable Systems
- **Autores:** Ruiz-Fuertes, María Idoia; Pallardó-Lozoya, María Ruth; Muñoz-Escoí, Francesc D.
- **Ano:** 2012. **Fonte:** *Lecture Notes in Computer Science* (OTM 2012 — *On the Move to Meaningful Internet Systems*), p. 549-565.
- **DOI:** 10.1007/978-3-642-33615-7_7. **URL:** https://link.springer.com/chapter/10.1007/978-3-642-33615-7_7
- **Resumo:** discute modelos de consistência em sistemas escaláveis (forte, sequencial, causal, eventual), com foco no *trade-off* entre disponibilidade e consistência sob escalabilidade horizontal.
- **Justificativa de relevância:** referência acadêmica acessória ao Cap. 2.1 (modelos de consistência) — complementa Lamport e CAP/PACELC.
- **Possível uso no TCC:** Cap. 2 (2.1, 2.3).
- **Origem no material:** `referencias-web.txt`, linha 9.
- **Status:** Aguardando validação do Gestor.

### [CAND-023] Proving the Safety of Highly-Available Distributed Objects
- **Autores:** Nair, Sreeja S.; Petri, Gustavo; Shapiro, Marc.
- **Ano:** 2020. **Fonte:** *Lecture Notes in Computer Science* (ESOP 2020 — *European Symposium on Programming*), p. 544-571.
- **DOI:** 10.1007/978-3-030-44914-8_20. **URL:** https://link.springer.com/chapter/10.1007/978-3-030-44914-8_20
- **Resumo:** método para provar *safety* de objetos distribuídos altamente disponíveis (gen. de CRDTs); composição entre invariantes locais e propriedades globais.
- **Justificativa de relevância:** **ponte direta entre eixo A (replicação otimista) e eixo C (métodos formais)** — exatamente o tipo de raciocínio do TCC.
- **Possível uso no TCC:** Cap. 2 (2.3, 2.5); Cap. 4 (modelo formal); trabalhos relacionados.
- **Origem no material:** `referencias-web.txt`, linha 10.
- **Status:** Aguardando validação do Gestor.

### [CAND-024] Conflict-Free Replicated Data Types (verbete enciclopédico) *(DISPENSADA Decisão 012 — era REF-012)*
- **Autores:** Preguiça, Nuno; Baquero, Carlos; Shapiro, Marc.
- **Ano:** 2019 (1ª ed.) / 2022 (atualizado). **Fonte:** *Encyclopedia of Big Data Technologies*.
- **DOI:** 10.1007/978-3-319-77525-8_185 (2019) / 10.1007/978-3-319-63962-8_185-2 (2022).
- **Resumo:** verbete acessível sobre CRDTs (CvRDT/CmRDT, *join-semilattice*, casos de uso) escrito por três dos quatro autores do paper seminal de 2011 (REF-002).
- **Justificativa de relevância:** complemento didático à REF-002; útil quando se quiser citar uma fonte mais recente e enciclopédica.
- **Possível uso no TCC:** Cap. 2 (2.3) como apoio.
- **Origem no material:** `SearchResults.csv` / `SearchResultsDataGridFormal.csv`.
- **Status:** Aguardando validação do Gestor. *(Atenção: REF-002 já cobre o conceito; o Gestor pode optar por dispensar.)*

### [CAND-025] Quantitative Analysis of Consistency in NoSQL Key-Value Stores
- **Autores:** Liu, Si; Nguyen, Son; Ganhotra, Jatin; Rahman, Muntasir Raihan; Gupta, Indranil; Meseguer, José.
- **Ano:** 2015. **Fonte:** *Quantitative Evaluation of Systems* (QEST 2015), LNCS.
- **DOI:** 10.1007/978-3-319-22264-6_15.
- **Resumo:** análise quantitativa de modelos de consistência em KV stores reais, com formalização probabilística.
- **Justificativa de relevância:** une eixos A (consistência KV) e C (análise formal probabilística); útil para a metodologia experimental.
- **Possível uso no TCC:** Cap. 2 (2.1, 2.3); Cap. 3 (metodologia).
- **Origem no material:** `SearchResultsDataGridFormal.csv`.
- **Status:** Aguardando validação do Gestor.

---

## Eixo B (continuação) — IMDG/cache distribuído

### [CAND-026] Benchmarking and Performance Analysis for Distributed Cache Systems: A Comparative Case Study *(DISPENSADA Decisão 012 — era REF-014)*
- **Autores:** Salhi, Haytham; Odeh, Feras; Nasser, Rabee; Taweel, Adel.
- **Ano:** 2018. **Fonte:** *Performance Evaluation and Benchmarking for the Analytics Era* — LNCS (TPCTC 2017).
- **DOI:** 10.1007/978-3-319-72401-0_11.
- **Resumo:** estudo comparativo de performance entre sistemas de cache distribuído (inclui Infinispan, Hazelcast, Apache Ignite), com metodologia de *benchmarking*.
- **Justificativa de relevância:** referência metodológica direta para a trilha experimental do TCC; ajuda a justificar a escolha do Infinispan e o desenho do *workload*.
- **Possível uso no TCC:** Cap. 2 (2.2, 2.8); **Cap. 3 (metodologia experimental)**.
- **Origem no material:** `SearchResultsInfinispan.csv` (alto rank em vários eixos).
- **Status:** Aguardando validação do Gestor.

### [CAND-027] TailX: Scheduling Heterogeneous Multiget Queries to Improve Tail Latencies in Key-Value Stores
- **Autores:** Jaiman, Vikas; Ben Mokhtar, Sonia; Rivière, Etienne.
- **Ano:** 2020. **Fonte:** *Distributed Applications and Interoperable Systems* (DAIS 2020) — LNCS.
- **DOI:** 10.1007/978-3-030-50323-9_5.
- **Resumo:** algoritmo de *scheduling* para reduzir latência de cauda em KV stores com consultas heterogêneas.
- **Justificativa de relevância:** evidência empírica para o problema central da trilha experimental (p95/p99 sob carga).
- **Possível uso no TCC:** Cap. 2 (2.6, 2.8); Cap. 4 (controlador heurístico — comparação).
- **Origem no material:** `SearchResultsDataGridLatency.csv`.
- **Status:** Aguardando validação do Gestor.

---

## Eixo C (continuação) — Métodos formais

### [CAND-028] Impossibility of Distributed Consensus with One Faulty Process **(FLP 1985)**
- **Autores:** Fischer, Michael J.; Lynch, Nancy A.; Paterson, Michael S.
- **Ano:** 1985. **Fonte:** *Journal of the ACM*, v. 32, n. 2 (abril), p. 374-382.
- **DOI:** 10.1145/3149.214121 (a confirmar — número padrão JACM).
- **Resumo:** **resultado clássico de impossibilidade**: nenhum protocolo de consenso totalmente assíncrono tolera a morte (silenciosa) de mesmo um processo. Pedra angular da teoria de sistemas distribuídos.
- **Justificativa de relevância:** **base teórica fundamental** para discutir por que sistemas como Infinispan adotam replicação assíncrona/otimista em vez de consenso síncrono; ancora também o argumento sobre detectores de falha (CAND-006).
- **Possível uso no TCC:** **Cap. 1 (justificativa)**, Cap. 2 (2.1, 2.4), Cap. 4 (modelo formal).
- **Origem no material:** `Impossibility of Distributed Consensus with One Faulty.pdf` (texto integral lido).
- **Status:** Aguardando validação do Gestor. *(Recomendação forte do Bibliotecário: aprovar.)*

### [CAND-029] Why Amazon Chose TLA+
- **Autor:** Newcombe, Chris.
- **Ano:** 2014. **Fonte:** *Abstract State Machines, Alloy, B, TLA, VDM, and Z* (ABZ 2014) — LNCS.
- **DOI:** 10.1007/978-3-662-43652-3_3.
- **Resumo:** relato detalhado do processo decisório que levou a AWS a adotar TLA+ para verificação de sistemas distribuídos de produção.
- **Justificativa de relevância:** **complemento ou substituto** de CAND-011 (Newcombe et al., CACM 2015). Mesmo autor, mesmo argumento, *venue* formal de métodos formais.
- **Possível uso no TCC:** Cap. 1 (justificativa); Cap. 2 (2.5, trabalhos relacionados).
- **Origem no material:** `SearchResultsDataGridFormal.csv`.
- **Status:** Aguardando validação do Gestor. *(Decisão acessória: usar CAND-011, CAND-029, ou ambos.)*

### [CAND-030] Model Checking Guided Incremental Testing for Distributed Systems
- **Autores:** Gao, Yu; Wang, Dong; Dou, Wensheng; Feng, Wei; Liang, Yu; Feng, Yang; Wei, Jun.
- **Ano:** 2025. **Fonte:** *Proceedings of the ACM on Software Engineering*, v. 2 (ISSTA), p. 297-319.
- **DOI:** 10.1145/3728883.
- **Resumo:** combina *model checking* com testes incrementais para sistemas distribuídos — abordagem híbrida formal/empírica.
- **Justificativa de relevância:** **estado-da-arte (2025)** alinhado à abordagem dual do TCC (formal + experimental).
- **Possível uso no TCC:** Cap. 2 (2.5, trabalhos relacionados); Cap. 3 (metodologia).
- **Origem no material:** `referencias-web.txt`, linha 3.
- **Status:** Aguardando validação do Gestor.

### [CAND-031] Model Checking Distributed Protocols in Must
- **Autores:** Enea, Constantin; Giannakopoulou, Dimitra; Kokologiannakis, Michalis; Majumdar, Rupak.
- **Ano:** 2024. **Fonte:** *Proceedings of the ACM on Programming Languages*, v. 8 (OOPSLA2), p. 1900-1927.
- **DOI:** 10.1145/3689778.
- **Resumo:** apresenta a ferramenta *Must* para *model checking* de protocolos distribuídos com semântica de mensagens assíncronas e falhas.
- **Justificativa de relevância:** **alternativa concreta** ao TLA+/TLC (P-017); pode informar a decisão de ferramenta.
- **Possível uso no TCC:** Cap. 2 (2.5); Cap. 3 (P-017 — escolha da ferramenta).
- **Origem no material:** `referencias-web.txt`, linha 5.
- **Status:** Aguardando validação do Gestor.

### [CAND-032] Cost-aware horizontal scaling of NoSQL databases using probabilistic model checking
- **Autores:** Naskos, Athanasios; Gounaris, Anastasios; Katsaros, Panagiotis.
- **Ano:** 2017. **Fonte:** *Cluster Computing*.
- **DOI:** 10.1007/s10586-017-0816-5.
- **Resumo:** uso de *probabilistic model checking* para decisões de escalonamento horizontal de NoSQL com consciência de custo.
- **Justificativa de relevância:** **ponte rara** entre métodos formais e controle adaptativo — pode embasar o desenho conceitual do controlador heurístico do TCC.
- **Possível uso no TCC:** Cap. 2 (2.5, 2.7, trabalhos relacionados); Cap. 4 (controlador).
- **Origem no material:** `SearchResultsDataGridFormal.csv`.
- **Status:** Aguardando validação do Gestor.

### [CAND-033] Enhancing Context Awareness with Model Checking-based Uncertainty Representation in Decision Support Systems
- **Autores:** Chen, Guanyu; Li, Zhixin; Gu, Jin; She, Yu; Zhu, Chen; Jiang, Zhe.
- **Ano:** 2026. **Fonte:** *Formal Aspects of Computing*, v. 38, n. 1, p. 1-31.
- **DOI:** 10.1145/3785354.
- **Resumo:** model checking aplicado a sistemas de apoio à decisão com representação de incerteza.
- **Justificativa de relevância:** relevância **especulativa** — pode informar o tratamento de incerteza no controlador heurístico do TCC. Avaliar se o ganho vale o custo.
- **Possível uso no TCC:** Cap. 4 (controlador — opcional).
- **Origem no material:** `referencias-web.txt`, linha 7.
- **Status:** Aguardando validação do Gestor. *(Recomendação fraca; pode ser descartada.)*

---

## Eixo D (continuação) — SLO/latência/observabilidade

### [CAND-034] Taming Tail Latency in Key-Value Stores: A Scheduling Perspective
- **Autores:** Ben Mokhtar, Sonia; Canon, Louis-Claude; Dugois, Anthony; Marchal, Loris; Rivière, Etienne.
- **Ano:** 2021. **Fonte:** *Euro-Par 2021: Parallel Processing* — LNCS.
- **DOI:** 10.1007/978-3-030-85665-6_9.
- **Resumo:** *scheduling* para conter latência de cauda em KV stores.
- **Justificativa de relevância:** complemento empírico ao REF-004 (Dean & Barroso, *The Tail at Scale*) aplicado a KV stores — exatamente o domínio do TCC.
- **Possível uso no TCC:** Cap. 2 (2.6); Cap. 4 (controlador — comparação).
- **Origem no material:** `SearchResultsDataGridLatency.csv`.
- **Status:** Aguardando validação do Gestor.

### [CAND-035] A scheduling framework for distributed key-value stores and its application to tail latency minimization
- **Autores:** Ben Mokhtar, Sonia; Canon, Louis-Claude; Dugois, Anthony; Marchal, Loris; Rivière, Etienne.
- **Ano:** 2024. **Fonte:** *Journal of Scheduling*.
- **DOI:** 10.1007/s10951-023-00803-8.
- **Resumo:** **versão estendida** de CAND-034 em periódico. Mesma agenda de pesquisa.
- **Justificativa de relevância:** versão definitiva/estendida do trabalho; preferível à CAND-034 se for citar só uma.
- **Possível uso no TCC:** Cap. 2 (2.6); Cap. 4.
- **Origem no material:** múltiplos CSVs.
- **Status:** Aguardando validação do Gestor.

### [CAND-036] Using weaker consistency models with monitoring and recovery for improving performance of key-value stores
- **Autores:** Nguyen, Duong; Charapko, Aleksey; Kulkarni, Sandeep S.; Demirbas, Murat.
- **Ano:** 2019. **Fonte:** *Journal of the Brazilian Computer Society*.
- **DOI:** 10.1186/s13173-019-0091-9.
- **Resumo:** combina **consistência fraca + monitoramento + recovery** para melhorar desempenho de KV stores — **descrição quase literal do escopo do TCC**.
- **Justificativa de relevância:** **trabalho relacionado de primeira linha** — leitura obrigatória para a seção de trabalhos relacionados.
- **Possível uso no TCC:** **Cap. 2 (trabalhos relacionados — central)**; Cap. 4.
- **Origem no material:** `SearchResultsDataGridFormal.csv`.
- **Status:** Aguardando validação do Gestor. *(Recomendação forte do Bibliotecário: aprovar.)*

### [CAND-037] Enjoy your observability: an industrial survey of microservice tracing and analysis
- **Autores:** Li, Bowen; Peng, Xin; Xiang, Qilin; Wang, Hanzhang; Xie, Tao; Sun, Jun; Liu, Xuanzhe.
- **Ano:** 2021. **Fonte:** *Empirical Software Engineering*.
- **DOI:** 10.1007/s10664-021-10063-9.
- **Resumo:** *survey* industrial de observabilidade em microsserviços.
- **Justificativa de relevância:** apoio ao Cap. 2.6 (observabilidade) — perspectiva industrial atual.
- **Possível uso no TCC:** Cap. 2 (2.6).
- **Origem no material:** múltiplos CSVs.
- **Status:** Aguardando validação do Gestor.

---

## Eixo E (continuação) — Autoadaptativo/controle

### [CAND-038] A control theoretical view of cloud elasticity: taxonomy, survey and challenges
- **Autores:** Ullah, Amjad; Li, Jingpeng; Shen, Yindong; Hussain, Amir.
- **Ano:** 2018. **Fonte:** *Cluster Computing*.
- **DOI:** 10.1007/s10586-018-2807-6.
- **Resumo:** taxonomia e *survey* de elasticidade em nuvem sob a ótica de teoria de controle.
- **Justificativa de relevância:** apoio direto ao Cap. 2.7 e ao desenho do controlador heurístico do TCC (histerese, *flapping*).
- **Possível uso no TCC:** Cap. 2 (2.7); Cap. 4.
- **Origem no material:** `SearchResultsHeuristic.csv`.
- **Status:** Aguardando validação do Gestor.

### [CAND-039] Autoscaling tiered cloud storage in Anna
- **Autores:** Wu, Chenggang; Sreekanti, Vikram; Hellerstein, Joseph M.
- **Ano:** 2020. **Fonte:** *The VLDB Journal*.
- **DOI:** 10.1007/s00778-020-00632-7.
- **Resumo:** auto-escalonamento de storage hierárquico em "Anna" (KV store da pesquisa do Berkeley). Joseph M. Hellerstein é um dos autores de CAND-016.
- **Justificativa de relevância:** trabalho relacionado de auto-adaptação em KV stores.
- **Possível uso no TCC:** Cap. 2 (2.7, trabalhos relacionados).
- **Origem no material:** `SearchResultsHeuristic.csv`.
- **Status:** Aguardando validação do Gestor.

### [CAND-040] OnlineElastMan: self-trained proactive elasticity manager for cloud-based storage services
- **Autores:** Liu, Ying; Gureya, Daharewa; Al-Shishtawy, Ahmad; Vlassov, Vladimir.
- **Ano:** 2017. **Fonte:** *Cluster Computing*.
- **DOI:** 10.1007/s10586-017-0899-z.
- **Resumo:** gestor proativo e autoaprendido de elasticidade para serviços de storage em nuvem.
- **Justificativa de relevância:** mais um exemplar de trabalhos relacionados em controle adaptativo de storage.
- **Possível uso no TCC:** Cap. 2 (2.7, trabalhos relacionados).
- **Origem no material:** `SearchResultsHeuristic.csv`.
- **Status:** Aguardando validação do Gestor.

### [CAND-041] Assurance of Self-adaptive Controllers for the Cloud
- **Autores:** Gambi, Alessio; Toffetti, Giovanni; Pezzè, Mauro.
- **Ano:** 2013. **Fonte:** *Assurances for Self-Adaptive Systems* — LNCS 7740.
- **DOI:** 10.1007/978-3-642-36249-1_12.
- **Resumo:** garantias (formais e empíricas) sobre controladores autoadaptativos para a nuvem.
- **Justificativa de relevância:** **ponte E↔C** — exatamente o tema de "validar formalmente um controlador heurístico", o ponto mais delicado do TCC.
- **Possível uso no TCC:** Cap. 2 (2.5, 2.7); Cap. 4 (controlador).
- **Origem no material:** `SearchResultsHeuristic.csv`.
- **Status:** Aguardando validação do Gestor. *(Recomendação forte do Bibliotecário.)*

### [CAND-042] Self-management of Distributed Systems Using High-Level Goal Policies
- **Autores:** Rosa, Liliana; Rodrigues, Luís; Lopes, Antónia.
- **Ano:** 2013. **Fonte:** *Software Engineering for Self-Adaptive Systems II* — LNCS 7475.
- **DOI:** 10.1007/978-3-642-35813-5_7.
- **Resumo:** auto-gestão de sistemas distribuídos por políticas de objetivo de alto nível.
- **Justificativa de relevância:** publicado **no exato volume** referenciado em CAND-015 (LNCS *SEAMS II*) — confirma o eixo E como vivo e fornece um capítulo concreto.
- **Possível uso no TCC:** Cap. 2 (2.7).
- **Origem no material:** `SearchResultsInfinispan.csv`.
- **Status:** Aguardando validação do Gestor.

---

## Eixo G (continuação) — Metodologia

### [CAND-043] Revisão Sistemática da Literatura em Ciência da Computação: Um Guia Prático
- **Autores:** Neiva, Frâncila Weidt; Silva, Rodrigo Luís de Souza da.
- **Ano:** 2016. **Fonte:** *Relatório Técnico do Departamento de Ciência da Computação* (RelaTeDCC 001/2016), Universidade Federal de Juiz de Fora.
- **URL:** https://www.researchgate.net/publication/304215400 (também distribuído como PDF pelo Gestor).
- **Resumo:** guia prático para condução de revisão sistemática em CC, baseado em Kitchenham 2007, em português, formato sucinto e didático.
- **Justificativa de relevância:** **referência metodológica direta** para a mini-revisão estruturada (Decisão 003 / [`02-metodologia.md`](02-metodologia.md)) — fornece protocolo concreto, em português, validável pelo orientador.
- **Possível uso no TCC:** **Cap. 3 (metodologia — mini-revisão estruturada)**.
- **Origem no material:** PDF integral + `referencias-web.txt` linha 1.
- **Status:** Aguardando validação do Gestor. *(Recomendação forte do Bibliotecário.)*

### [CAND-044] Data Clustering: A Review
- **Autores:** Jain, A. K.; Murty, M. N.; Flynn, P. J.
- **Ano:** 1999. **Fonte:** *ACM Computing Surveys*, v. 31, n. 3 (setembro), p. 264-323.
- **DOI:** 10.1145/331499.331504.
- **Resumo:** *survey* clássico de algoritmos de agrupamento (*clustering*) em reconhecimento de padrões.
- **Justificativa de relevância:** **incerta**. Possível uso: se o trabalho for descrever o agrupamento temático das referências como "*thematic clustering*", esta seria a referência canônica. **Risco:** uso periférico que pode parecer forçado. **Recomendação do Bibliotecário: descartar a menos que o Gestor identifique aplicação concreta.**
- **Possível uso no TCC:** —.
- **Origem no material:** PDF integral fornecido pelo Gestor.
- **Status:** Aguardando validação do Gestor. *(Recomendação: descartar.)*

---

## Material de contexto geral (ACM)

### [CAND-045] A view of cloud computing
- **Autores:** Armbrust, Michael; Fox, Armando; Griffith, Rean; Joseph, Anthony D.; Katz, Randy; Konwinski, Andy; Lee, Gunho; Patterson, David; Rabkin, Ariel; Stoica, Ion; Zaharia, Matei.
- **Ano:** 2010. **Fonte:** *Communications of the ACM*, v. 53, n. 4, p. 50-58.
- **DOI:** 10.1145/1721654.1721672.
- **Resumo:** texto seminal de definição de *cloud computing* — modelos de serviço, *trade-offs*, oportunidades e obstáculos.
- **Justificativa de relevância:** ancora a contextualização geral em Cap. 1 (cloud + serviços de larga escala) e Cap. 2.6 (motivação para SLO).
- **Possível uso no TCC:** Cap. 1 (contexto); Cap. 2 (2.6).
- **Origem no material:** `referencias-web.txt`, linha 2.
- **Status:** Aguardando validação do Gestor.

---

## Cobertura adicional (uso a confirmar)

### [CAND-046] Distributed Systems Modelling
- **Autores:** Benítez-Pérez, Héctor; Ortega-Arjona, Jorge L.; Méndez-Monroy, Paul E.; Rubio-Acosta, Esaú; Esquivel-Flores, Oscar A.
- **Ano:** 2018. **Fonte:** *Modeling and Optimization in Science and Technologies* (livro, cap. 3), p. 41-82.
- **DOI:** 10.1007/978-3-319-97044-8_3.
- **Resumo:** capítulo introdutório sobre modelagem de sistemas distribuídos.
- **Justificativa de relevância:** **fraca** — texto genérico; pode servir de leitura geral mas não acrescenta sobre o tema-foco do TCC.
- **Possível uso no TCC:** Cap. 2 (2.1, eventual).
- **Origem no material:** `referencias-web.txt`, linha 11.
- **Status:** Aguardando validação do Gestor. *(Recomendação: descartar a menos que o Gestor sinalize uso.)*

---

## Eixo I — Computação sustentável / energia / carbono (adicionado 2026-06-23)

> Metadados VERIFICADOS na fonte (WebFetch + Crossref). Citação no `.tex` pendente de inserção pelo Escritor.

### [CAND-049] Challenges in High-Performance Computing *(PROMOVIDA — REF-051, Decisão 016, 2026-06-23)*
- **Autores:** Navaux, Philippe Olivier Alexandre; Lorenzon, Arthur Francisco; Serpa, Matheus da Silva.
- **Ano:** 2023. **Fonte:** *Journal of the Brazilian Computer Society* (JBCS/SBC), vol. 29, n. 1, p. 51-62.
- **DOI:** 10.5753/jbcs.2023.2219. **chave .bib:** `navaux_hpc_challenges_2023`.
- **Resumo:** survey sobre desafios de supercomputadores futuros (arquiteturas heterogêneas, exascale, paralelização); trata consumo de energia e resiliência como requisitos não-funcionais.
- **ATENÇÃO (procedência):** a URL fornecida originalmente como "carbon-aware caching" (`view/2219/2306`) resolve, de fato, para ESTE survey de HPC — **NÃO** é artigo de carbon-aware caching. Mantido por decisão relatada do Gestor como reforço de "energia em HPC".
- **Possível uso no TCC:** Cap. 1 (justificativa, parágrafo de sustentabilidade), ao lado de `turner_carbon_footprint_2023` e `ft_hpc_energy_2024`.
- **Status:** **PROMOVIDA a REF-051** (Decisão 016, 2026-06-23).

### [CAND-050] Cache Your Prompt When It's Green: Carbon-Aware Caching for LLM Serving *(PROMOVIDA — REF-052, Decisão 016, 2026-06-23)*
- **Autores:** Tian, Yuyang; Sun, Desen; Ding, Yi; Liu, Sihang.
- **Ano:** 2026. **Fonte:** *Proceedings of the ACM on Measurement and Analysis of Computing Systems* (POMACS), vol. 10, n. 1, p. 1-28 — apresentado na **ACM SIGMETRICS 2026** (peer-reviewed).
- **DOI:** 10.1145/3788087 (POMACS). DOI da versão arXiv: 10.48550/arXiv.2505.23970. **chave .bib:** `tian_greencache_2026`.
- **Resumo:** framework GreenCache para caching ciente de carbono em servição de LLMs; equilibra o carbono operacional evitado pelo cache contra o carbono incorporado (embodied) de SSDs de alta capacidade; ~15,1% de redução média de carbono (até 25,3%) respeitando restrições de latência.
- **Justificativa de relevância:** artigo REAL e revisado por pares especificamente de *carbon-aware caching* — alinhado ao argumento de sustentabilidade do TCC (reduzir retrabalho/reexecução em cache distribuído tem impacto de carbono).
- **Possível uso no TCC:** Cap. 1 (justificativa, parágrafo de sustentabilidade), ao lado de `turner_carbon_footprint_2023` e `ft_hpc_energy_2024`.
- **Status:** **PROMOVIDA a REF-052** (Decisão 016, 2026-06-23).


---

## Eixo J — Workshop UCC 2026, submissão 6539, versão de 6 páginas (adicionado 2026-10-22)

> Curadoria pedida pelo coordenador para responder aos pareceres R1/R2/R3 (`ucc26/workshop-paper/pareceres-ucc26.txt`). **Metadados VERIFICADOS** na fonte primária: Crossref/OpenAlex mais a página do editor ou a cópia do autor/repositório institucional. **Nenhuma está aprovada.** O Escritor não pode citá-las até decisão explícita do Gestor (P-027). **Atualização (2026-10-30, Decisão 018):** CAND-057..060, 062..064, 066 e 068 aprovadas para a monografia (REF-055..058, 060..062, 064, 066). O BibTeX está pronto em `ucc26/workshop-paper/refs-relatorio.md`. Nenhuma foi adicionada ao `references.bib` do workshop nem ao `bibliografia.bib` do TCC.

### [CAND-055] The Byzantine Generals Problem
- **Autores:** Lamport, Leslie; Shostak, Robert; Pease, Marshall. **Ano:** 1982. **Fonte:** ACM TOPLAS 4(3):382-401. **DOI:** 10.1145/357172.357176. **Chave:** `lamport_byzantine_1982`.
- **Verificação:** Crossref e resumo (Semantic Scholar). Trata de componentes que dão informação conflitante; com mensagens orais, o acordo só é possível se mais de 2/3 dos generais forem leais.
- **Uso:** R2(a). Define a falha bizantina e sustenta o limite de 3f+1 que o texto do Escritor menciona.

### [CAND-056] Practical Byzantine Fault Tolerance (PBFT)
- **Autores:** Castro, Miguel; Liskov, Barbara. **Ano:** 1999. **Fonte:** Proc. 3rd USENIX OSDI, New Orleans, p. 173-186 (sem DOI). **Chave:** `castro_liskov_pbft_1999`.
- **Verificação:** página da USENIX (título, autores e páginas 173-186). O resumo fala de nós faltosos com comportamento arbitrário e de funcionamento em ambientes assíncronos. A versão TOCS 2002 (DOI 10.1145/571637.571640) também foi conferida: o algoritmo tolera falhas desde que menos de 1/3 das réplicas falhe.
- **Uso:** R2(a). Mostra que tolerar falha bizantina exige outro protocolo e mais réplicas. É opcional se a CAND-055 entrar.

### [CAND-057] Basic Concepts and Taxonomy of Dependable and Secure Computing — promovida a REF-055 (Decisão 018)
- **Autores:** Avižienis, Algirdas; Laprie, Jean-Claude; Randell, Brian; Landwehr, Carl. **Ano:** 2004. **Fonte:** IEEE TDSC 1(1):11-33. **DOI:** 10.1109/TDSC.2004.2. **Chave:** `avizienis_taxonomy_2004`.
- **Verificação:** texto integral da versão submetida, no repositório da Univ. de Maryland (ISR TR 2004-47, via Unpaywall). A Sec. 3.3.1 define halt failure, silent failure, fail-stop/fail-silent, falhas de temporização e "inconsistent failures ... usually called, after [Lamport et al. 1982], Byzantine failures".
- **Uso:** R2(a). Fornece a taxonomia de falhas para dizer que o modelo cobre parada/silêncio e atraso, mas não falhas arbitrárias. Cristian 1991 (CACM 34(2):56-78) foi considerado como alternativa e **não foi verificado**: o texto integral não abriu (ACM 403), e só os metadados e o resumo foram conferidos.

### [CAND-058] Simple Testing Can Prevent Most Critical Failures — promovida a REF-056 (Decisão 018)
- **Autores:** Yuan, Ding; Luo, Yu; Zhuang, Xin; Rodrigues, Guilherme Renna; Zhao, Xu; Zhang, Yongle; Jain, Pranay U.; Stumm, Michael. **Ano:** 2014. **Fonte:** Proc. 11th USENIX OSDI, p. 249-265 (sem DOI). **Chave:** `yuan_simple_testing_2014`.
- **Verificação:** página da USENIX. O resumo diz que "almost all failures require only 3 or fewer nodes to reproduce".
- **Uso:** R2(b). Dá apoio empírico, revisado por pares, a instâncias pequenas (small scope) em sistemas distribuídos. Complementa a REF-049 (Jackson), que formula a small scope hypothesis. Essa formulação foi conferida em slides do próprio autor (people.csail.mit.edu/dnj); o capítulo do livro não foi lido. Andoni et al. 2003 **não** é recomendado, por ser relatório não revisado.

### [CAND-059] Scientific Benchmarking of Parallel Computing Systems: Twelve Ways to Tell the Masses when Reporting Performance Results — promovida a REF-057 (Decisão 018)
- **Autores:** Hoefler, Torsten; Belli, Roberto. **Ano:** 2015. **Fonte:** Proc. SC'15, p. 1-12. **DOI:** 10.1145/2807591.2807644. **Chave:** `hoefler_belli_benchmarking_2015`.
- **Verificação:** PDF do autor (htor.inf.ethz.ch). Pontos conferidos: medianas e percentis não paramétricos exigem medições i.i.d.; há ICs não paramétricos; "n > 5 measurements are needed to assess confidence intervals nonparametrically"; a Regra 8 trata de percentis para latência de pior caso.
- **Uso:** R1(e). Justifica tratar a execução como unidade, reportar o p99 por execução e não alegar IC nem significância com n=3.

### [CAND-060] An Introduction to the Bootstrap — promovida a REF-058 (Decisão 018)
- **Autores:** Efron, Bradley; Tibshirani, Robert J. **Ano:** 1993. **Fonte:** Chapman & Hall, New York. DOI Springer 10.1007/978-1-4899-4541-9; edição CRC 10.1201/9780429246593. **Chave:** `efron_tibshirani_bootstrap_1993`.
- **Verificação:** metadados do Crossref e títulos de capítulo do editor: cap. 13 "Confidence intervals based on bootstrap percentiles", cap. 14 "Better bootstrap confidence intervals" e cap. 15 "Permutation tests". O texto é pago, e nada além disso foi lido.
- **Uso:** R1(e), **somente** se o texto usar bootstrap ou o teste de permutação exato. No rascunho de 22/10 aparece o teste de permutação, mas não o bootstrap.

### [CAND-061] The Maintenance of Duplicate Databases (RFC 677)
- **Autores:** Johnson, Paul R.; Thomas, Robert H. **Ano:** 1975. **Fonte:** RFC 677 (RFC Editor). **DOI:** 10.17487/RFC0677. **Chave:** `johnson_thomas_rfc677_1975`.
- **Verificação:** texto integral em rfc-editor.org. Cada modificação recebe um timestamp (T, D), e a réplica guarda a versão "more recent". Empates de T são desempatados pelo identificador do nó: (T1,D1)>(T2,D2) sse T1>T2 ou (T1=T2 e D1>D2).
- **Uso:** item (d), LWW. É a origem do last-writer-wins por timestamp com desempate por réplica, o que importa para o mecanismo de I5/I6 (um empate de stamp sem desempate).
- **Alternativas:** Thomas 1979 (TODS 4(2):180-209, DOI 10.1145/320071.320076) tem metadados e resumo verificados, mas o resumo não menciona timestamps; o Dynamo o cita para LWW. A **REF-001 (Dynamo), já aprovada**, também serve: diz textualmente "last write wins ... the object with the largest physical timestamp value is chosen" e dá como exemplo o serviço de sessão de clientes.

### [CAND-062] Power Provisioning for a Warehouse-Sized Computer — promovida a REF-060 (Decisão 018)
- **Autores:** Fan, Xiaobo; Weber, Wolf-Dietrich; Barroso, Luiz André. **Ano:** 2007. **Fonte:** Proc. 34th ISCA, p. 13-23. **DOI:** 10.1145/1250662.1250665. **Chave:** `fan_power_provisioning_2007`.
- **Verificação:** PDF do Google Research (cópia dos autores). O modelo de potência usa a utilização de CPU como sinal, P = P_idle + (P_busy − P_idle)·u, e tem uma variante empírica não linear com parâmetro de calibração r. Basta uma calibração por classe de máquina.
- **Uso:** R1(g) e item (e). É o modelo linear de potência por utilização usado no proxy de energia.

### [CAND-063] Virtual Machine Power Metering and Provisioning (Joulemeter) — promovida a REF-061 (Decisão 018)
- **Autores:** Kansal, Aman; Zhao, Feng; Liu, Jie; Kothari, Nupur; Bhattacharya, Arka A. **Ano:** 2010. **Fonte:** Proc. 1st ACM SoCC, p. 39-50. **DOI:** 10.1145/1807128.1807136. **Chave:** `kansal_vm_power_2010`.
- **Verificação:** resumo (OpenAlex): "VM power cannot be measured purely in hardware". Os modelos de potência inferem o consumo a partir do uso de recursos.
- **Uso:** item (e). Sustenta a limitação de medir energia em VM ou contêiner e justifica o proxy.

### [CAND-064] NIST SP 800-63B-4: Digital Identity Guidelines: Authentication and Authenticator Management — promovida a REF-062 (Decisão 018)
- **Autores:** Temoshok, D.; Fenton, J. L.; Choong, Y.-Y.; Lefkovitz, N.; Regenscheid, A.; Galluzzo, R.; Richer, J. P. **Ano:** 2025 (jul.). **DOI:** 10.6028/NIST.SP.800-63b-4. **Chave:** `nist_sp_800_63b4_2025`.
- **Verificação:** csrc.nist.gov informa que ela "supersedes SP 800-63B". No texto HTML em pages.nist.gov, a Sec. 5 é normativa sobre session management (reautenticação e timeouts), e há uma seção Rate Limiting (Throttling).
- **Uso:** substitui a REF-036 (SP 800-63B de 2017, hoje marcada como **Withdrawn** no CSRC) como base normativa de I1/I2.

### Regularização: chaves usadas na versão submetida da UCC sem REF oficial
> Estas chaves entraram no `ucc26/paper/references.bib` sem registro nesta biblioteca. Os metadados foram verificados agora, e falta a ratificação do Gestor (P-028).
- **[CAND-065]** Taufer et al., "Balancing Sustainable Computing and Computing for Sustainability", CACM 69(9):33-36, 2026, DOI 10.1145/3786358 (Crossref). Chave `taufer_balancing_sustainable_2026`. Era `@misc` sem DOI e foi corrigida para `@article`.
- **[CAND-066]** (promovida a REF-064, Decisão 018) Barroso & Hölzle, "The Case for Energy-Proportional Computing", IEEE Computer 40(12):33-37, 2007, DOI 10.1109/MC.2007.443 (Crossref). Chave `barroso_holzle_energy_proportional_2007`.
- **[CAND-067]** Lee, T., "TLA+ / TLC TL;DR", blog, 2021 (URL ativa). Chave `lee_tla_tlc_2021`. **Não é revisada por pares.** Só se justifica como crédito da figura adaptada; a recomendação é cortá-la junto com a figura.
- **[CAND-068]** (promovida a REF-066, Decisão 018) Página de manual tc-netem(8) do iproute2 (man7.org; página datada de 25/11/2011; manual de Ludovici e Pfeifer). Chave `netem_manpage`. Serve para R3(c), na definição de netem/tc.
- **[CAND-069]** Site do projeto OpenMetrics (openmetrics.io). Chave `openmetrics_spec`. Não é citada no rascunho de 22/10; a recomendação é cortar.

### Fontes para os `% [FONTE NECESSÁRIA]` de `Metodologia.tex` (2026-10-27)
> Pedido do Escritor, repassado pelo coordenador. As fontes foram verificadas na origem (HTML oficial 15.0.x, código-fonte na tag `15.0.21.Final`, Crossref). Nada entrou no `bibliografia.bib` nem no `.tex`. Aguarda o Gestor (P-030).

### [CAND-070] Using Hot Rod Java clients (Infinispan 15.0) — promovida a REF-068 (Decisão 017)
- **Autor:** Infinispan Project. **Fonte:** https://infinispan.org/docs/15.0.x/titles/hotrod_java/hotrod_java.html#hotrod_failover. Seção "1.4. Client Failover", bloco "Transactional Caches with Failover". **Chave:** `infinispan_hotrod_java_15`.
- **Trecho literal:** "Conditional operations, such as putIfAbsent(), replace(), remove(), have strict method return guarantees. Likewise, some operations can require previous values to be returned. Even though Hot Rod clients can failover, you should use transactional caches to ensure that operations do not partially complete and leave conflicting entries on different nodes."
- **Uso:** Metodologia, parágrafo da escrita condicional (`replaceWithVersion`). Sustenta a afirmação de que, sem transação, uma operação condicional pode ficar parcialmente aplicada no failover. **Não** diz "não garante atomicidade" com essas palavras, e não menciona rebalanceamento nem troca de dono.

### [CAND-071] Configuring Infinispan caches (Infinispan 15.0) — promovida a REF-069 (Decisão 017)
- **Autor:** Infinispan Project. **Fonte:** https://infinispan.org/docs/15.0.x/titles/configuring/configuring.html. **Chave:** `infinispan_configuring_15`.
- **Trechos literais:** (a) seção 7.1.1 "Data consistency in a split cluster" (`#partition-handling-data-consistency_partition-handling`): "Network outages or errors that cause Infinispan clusters to split into partitions can result in data loss or consistency issues regardless of any handling strategy or merge policy." (b) seção 10.1.1 "Clustered caches and locks" (`#clustered_caches_and_locks`): "For non-transactional caches, Infinispan forwards the write operation to the primary owner of the key so it can attempt to lock it. [...] If the operation is conditional and fails on the primary owner, Infinispan does not forward it to the other owners."
- **Uso:** (a) cobre partição e merge; (b) descreve o caminho da escrita condicional no modo não transacional, em que a condição é avaliada só no dono primário.

### [CAND-072] Fast splittable pseudorandom number generators — promovida a REF-070 (Decisão 017)
- **Autores:** Steele Jr., Guy L.; Lea, Doug; Flood, Christine H. **Ano:** 2014. **Fonte:** Proc. 2014 ACM International Conference on Object Oriented Programming Systems Languages & Applications (OOPSLA '14, SPLASH '14, Portland), ACM, p. 453-472. **DOI:** 10.1145/2660193.2660195. A versão no ACM SIGPLAN Notices 49(10):453-472 tem DOI 10.1145/2714064.2660195. **Chave:** `steele_lea_flood_splitmix_2014`.
- **Verificação:** os metadados vêm do Crossref, conferidos em 2026-10-27, e batem com o pedido. Pelo Unpaywall, não há versão OA, e o texto integral **não foi lido**.
- **Ressalva técnica:** o `mix64` do projeto (`MisturaV3.mix64`, deslocamentos 30/27/31, constantes 0xBF58476D1CE4E5B9 e 0x94D049BB133111EB) é a variante "Mix13" de Stafford. É idêntico a `java.util.SplittableRandom.mix64` (OpenJDK: "Computes Stafford variant 13 of 64bit mix function") e à saída do `splitmix64.c` de Vigna (2015), que deu o nome "SplitMix64". Segundo a implementação de referência em Haskell (pacote `splitmix`, fonte secundária), no artigo a função chamada `mix64` é o finalizador do MurmurHash3 (33/0xff51afd7ed558ccd/0xc4ceb9fe1a85ec53), e o Mix13 aparece só como `mix64variant13`, usado na escolha do gamma. Citar o artigo como origem do SplitMix é correto. Atribuir ao artigo a função exata do código é impreciso.
- **Uso:** Metodologia, derivação das sementes por *thread*. A citação é opcional, já que serve só para dar crédito ao nome do algoritmo.

### [CAND-073] Código-fonte do servidor Hot Rod, mensagem ISPN006010 (Infinispan 15.0.21.Final) — dispensada (Decisão 017)
- **Fonte:** https://github.com/infinispan/infinispan/blob/15.0.21.Final/server/hotrod/src/main/java/org/infinispan/server/hotrod/logging/Log.java (linha 55). A condição de emissão está em `HotRodHeader.java`, linhas 129-130: `if (clustered && !transactional && op.isConditional())`. `REPLACE_IF_UNMODIFIED`, a operação do `replaceWithVersion`, está marcada `IS_CONDITIONAL` em `HotRodOperation.java`.
- **Trecho literal:** "Conditional operation '%s' should be used with transactional caches, otherwise data inconsistency issues could arise under failure situations" (id 6010, `@Once`, nível WARN).
- **Uso:** só se o Gestor quiser citar o texto do aviso. A mensagem aparece nos logs do próprio *cluster*, então pode ser transcrita como observação, sem citação. Recomendação: não promover.

## Eixo K — Monografia "TCC-II parcial" (adicionado 2026-10-30)

> Pedido do coordenador para a versão entregável de 30/10 (controlador, energia, estatística, pré-registro). Metadados conferidos no Crossref e no OpenAlex **sem `mailto`** (R-EMAIL). O Unpaywall não foi chamado. **Nenhuma está aprovada** e nenhuma entrou no `bibliografia.bib`. Pendência P-031.
> **Atualização (2026-10-30, Decisão 018):** CAND-074 e CAND-076..081 promovidas a REF-071..REF-077 e incluídas no `bibliografia.bib`. Continuam candidatas, sem decisão: CAND-075 (Lorido-Botran), CAND-014..017 e a alternativa Agresti-Coull.
> Atualizações de candidatas antigas, verificadas hoje: **CAND-014** (IBM *blueprint*, sem DOI): preferir a CAND-074, que é revisada por pares. **CAND-015**: a 2.ª edição do roadmap está confirmada, de Lemos et al., LNCS 7475, p. 1-32, 2013, DOI 10.1007/978-3-642-35813-5_1. **CAND-016**: Hellerstein, Diao, Parekh e Tilbury, *Feedback Control of Computing Systems*, Wiley, 2004, DOI 10.1002/047166880X (Crossref); só os metadados foram conferidos. **CAND-017**: Basiri et al., "Chaos Engineering", IEEE Software 33(3):35-41, 2016, DOI 10.1109/MS.2016.60 (Crossref).

### [CAND-074] The Vision of Autonomic Computing — promovida a REF-071 (Decisão 018)
- **Autores:** Kephart, Jeffrey O.; Chess, David M. **Ano:** 2003. **Fonte:** IEEE Computer 36(1):41-50. **DOI:** 10.1109/MC.2003.1160055. **Chave proposta:** `kephart_chess_autonomic_2003`.
- **Verificação:** Crossref e resumo no OpenAlex ("systems that can manage themselves when given high-level objectives"). O texto integral não foi lido. A figura do elemento autônomo (monitorar, analisar, planejar, executar, sobre conhecimento) é a origem usual do MAPE-K e precisa ser conferida antes de citar. **Conferido em 2026-10-30** no texto integral: Figura 2, p. 44, blocos Monitor/Analyze/Plan/Execute em torno de Knowledge. A sigla "MAPE-K" não aparece no artigo (ver REF-071).
- **Uso:** laço MAPE-K do controlador autoadaptativo. Versão acadêmica da CAND-014.

### [CAND-075] A Review of Auto-scaling Techniques for Elastic Applications in Cloud Environments
- **Autores:** Lorido-Botran, Tania; Miguel-Alonso, Jose; Lozano, Jose A. **Ano:** 2014. **Fonte:** Journal of Grid Computing 12(4):559-592. **DOI:** 10.1007/s10723-014-9314-7. **Chave proposta:** `lorido_botran_autoscaling_2014`.
- **Verificação:** Crossref. O resumo não veio no OpenAlex e o texto não foi lido. A cobertura de regras por limiar com período de inércia (*cooldown*) precisa ser conferida antes de citar.
- **Uso:** regras por limiar com histerese e *cooldown*. Complementa a REF-025 (Ullah) e a CAND-016.

### [CAND-076] Control Group v2 (documentação do kernel Linux) — promovida a REF-072 (Decisão 018)
- **Autor:** The Linux Kernel documentation (Heo, Tejun, autor do documento). **Fonte:** https://docs.kernel.org/admin-guide/cgroup-v2.html (HTTP 200 em 30/10/2026). **Chave adotada:** `linux_cgroup_v2` (definida pelo Gestor; a proposta era `linux_cgroup_v2_doc`).
- **Trecho conferido:** "cpu.stat: A read-only flat-keyed file. This file exists whether the controller is enabled or not. It always reports the following three stats [...] usage_usec, user_usec, system_usec"; "All time durations are in microseconds."
- **Uso:** origem do sinal de CPU por contêiner no proxy de energia. Se o proxy usar cgroup v1 (`cpuacct.usage`), trocar pela página da v1.

### [CAND-077] The Preregistration Revolution — promovida a REF-073 (Decisão 018)
- **Autores:** Nosek, Brian A.; Ebersole, Charles R.; DeHaven, Alexander C.; Mellor, David T. **Ano:** 2018. **Fonte:** PNAS 115(11):2600-2606. **DOI:** 10.1073/pnas.1708274114. **Chave proposta:** `nosek_preregistration_2018`.
- **Verificação:** Crossref e resumo no OpenAlex ("define the research questions and analysis plan before observing the research outcomes — a process called preregistration"). Há versão aberta no PMC.
- **Uso:** pré-registro do protocolo da medição (critérios de recusa e análise fixados antes da coleta).

### [CAND-078] The Probable Error of a Mean — promovida a REF-074 (Decisão 018)
- **Autor:** "Student" (Gosset, William Sealy). **Ano:** 1908. **Fonte:** Biometrika 6(1):1-25. **DOI:** 10.2307/2331554 (JSTOR, Crossref). **Chave proposta:** `student_probable_error_1908`.
- **Verificação:** Crossref (vol. 6, n. 1, página inicial 1, mar. 1908). A página final, 25, vem da paginação usual e não do Crossref, então precisa ser conferida.
- **Uso:** IC da média pela distribuição t com poucas repetições. Só entra se o texto usar t de Student. Um livro-texto de estatística pode ser preferível, a decidir.

### [CAND-079] Interval Estimation for a Binomial Proportion — promovida a REF-075 (Decisão 018)
- **Autores:** Brown, Lawrence D.; Cai, T. Tony; DasGupta, Anirban. **Ano:** 2001. **Fonte:** Statistical Science 16(2):101-133. **DOI:** 10.1214/ss/1009213286. **Chave proposta:** `brown_cai_dasgupta_binomial_2001`.
- **Verificação:** Crossref. O texto não foi lido nesta sessão. É a referência-padrão para o mau comportamento do intervalo normal (Wald) com proporções pequenas e para a recomendação de Wilson ou Agresti-Coull.
- **Alternativa:** Agresti, Alan; Coull, Brent A., "Approximate is Better than 'Exact' for Interval Estimation of Binomial Proportions", The American Statistician 52(2):119-126, 1998, DOI 10.1080/00031305.1998.10480550 (Crossref).
- **Uso:** IC de taxas de erro e de violação (binomial/normal). Importante se houver taxas próximas de 0.

### [CAND-080] On a Test of Whether One of Two Random Variables is Stochastically Larger than the Other — promovida a REF-076 (Decisão 018)
- **Autores:** Mann, Henry B.; Whitney, Donald R. **Ano:** 1947. **Fonte:** The Annals of Mathematical Statistics 18(1):50-60. **DOI:** 10.1214/aoms/1177730491. **Chave proposta:** `mann_whitney_1947`.
- **Verificação:** Crossref.
- **Uso:** a `Metodologia.tex` (análise da nova medição) já usa o teste de Mann-Whitney **sem citação**.

### [CAND-081] Statistically Rigorous Java Performance Evaluation — promovida a REF-077 (Decisão 018)
- **Autores:** Georges, Andy; Buytaert, Dries; Eeckhout, Lieven. **Ano:** 2007. **Fonte:** Proc. OOPSLA '07, ACM, p. 57-76. **DOI:** 10.1145/1297027.1297033. **Chave adotada:** `georges_jvm_statistics_2007` (definida pelo Gestor; a proposta era `georges_java_performance_2007`).
- **Verificação:** Crossref e resumo no OpenAlex (não determinismo entre execuções da JVM: JIT, GC, escalonamento). Não foi possível ler a cópia aberta (UGent), então a recomendação da t de Student com poucas execuções e de ICs entre invocações da VM precisa ser conferida.
- **Uso:** a execução como unidade estatística em carga Java, com aquecimento e várias invocações da JVM. É a fonte mais próxima do protocolo do projeto.

---

## Eixo L — Estatística da avaliação do controlador (2026-10-30, conferência do texto reescrito)

> Pedidas pelo Escritor para os dois `% [FONTE NECESSÁRIA]` da Seção 3.3.11 (`Metodologia.tex`, linhas 256 e 258) e para o teste de permutação por troca de sinal (linha 255). Metadados conferidos no Crossref e no OpenAlex (sem `mailto`, R-EMAIL) e no PubMed. Textos não lidos. **Aprovadas na Decisão 019 (2026-10-30). Chaves fixadas pelo Gestor: `holm_sequential_1979`, `hanley_lippmanhand_zero_1983`, `good_permutation_tests_2005`, `fisher_design_experiments_1935`.**

### [CAND-082] A Simple Sequentially Rejective Multiple Test Procedure — promovida a REF-078 (Decisão 019)
- **Autor:** Holm, Sture. **Ano:** 1979. **Fonte:** Scandinavian Journal of Statistics 6(2):65-70. JSTOR stable 4615733 (https://www.jstor.org/stable/4615733). **Chave proposta:** `holm_sequentially_rejective_1979`.
- **Verificação:** OpenAlex (W2121044470: título, autor, vol. 6, p. 65-70, 1979). O OpenAlex informa o DOI 10.2307/4615733, mas ele **não está registrado** (Crossref 404, doi.org 404), então não vai para o `.bib`. Número 2 e editora (Wiley, pela Board of the Foundation of the Scandinavian Journal of Statistics) vêm do registro do JSTOR, visto em resultado de busca.
- **Uso:** correção de Holm para comparações múltiplas dos desfechos secundários dentro de cada célula (controle do erro do tipo I por família, versão sequencial do Bonferroni).

### [CAND-083] If Nothing Goes Wrong, Is Everything All Right? Interpreting Zero Numerators — promovida a REF-079 (Decisão 019)
- **Autores:** Hanley, James A.; Lippman-Hand, Abby. **Ano:** 1983. **Fonte:** JAMA 249(13):1743-1745. **DOI:** 10.1001/jama.1983.03330370053031. **Chave proposta:** `hanley_lippmanhand_zero_1983`.
- **Verificação:** Crossref (DOI, vol., n., página inicial 1743; o Crossref lista só Hanley e omite o subtítulo). PubMed PMID 6827763 traz os dois autores, o subtítulo "Interpreting zero numerators" e p. 1743-5.
- **Uso:** "regra de três": com zero eventos em N observações, o limite superior unilateral de 95 % da proporção é aproximadamente 3/N.

### [CAND-084] The Design of Experiments — promovida a REF-081; alternativa Good promovida a REF-080 (Decisão 019)
- **Autor:** Fisher, Ronald A. **Ano:** 1935. **Fonte:** Edinburgh: Oliver and Boyd (1. ed.). Sem DOI. **Chave proposta:** `fisher_design_experiments_1935`.
- **Verificação:** só a existência e a edição (resenhas de 1935 indexadas no OpenAlex, p. ex. DOI 10.1086/334571). Conteúdo não conferido: o teste por troca de sinal das diferenças pareadas é o exemplo dos dados de Darwin (*Zea mays*), no cap. III; capítulo e seção precisam ser conferidos na edição usada.
- **Alternativa (livro-texto):** Good, Phillip. *Permutation, Parametric and Bootstrap Tests of Hypotheses*. 3. ed. New York: Springer, 2005 (Springer Series in Statistics). DOI 10.1007/b138696 (Crossref; autor pelo OpenAlex). Cobertura do teste pareado não conferida.
- **Uso:** teste de permutação exata por troca de sinal dos $d_b$ pareados. Motivo: a REF-058 (Efron & Tibshirani) trata no cap. 15 o teste de permutação de **duas amostras** (seções 15.1-15.6) e, no cap. 16, o teste *bootstrap* de uma amostra; não há seção sobre o caso pareado por troca de sinal.

---

## Eixo M — Rodada 2 de fontes do Cap. 4 da versão oficial (2026-11-03)

> Levantadas na conferência de DES-54 e DES-55 de `tcc/monografia-atual/Desenvolvimento.tex`. Relatório: `tcc/monografia-atual/FONTES-RODADA2-2026-11-03.md`. Consultas sem e-mail (R-EMAIL). **CAND-085 aprovada na Decisão 020 (2026-11-03); CAND-086 fica de fora.**

### [CAND-085] JGroups Manual 5.x (Red Hat / projeto JGroups) — promovida a REF-082 (Decisão 020)
- **Autor:** Ban, Bela (projeto JGroups). **Ano:** sem data na página (manual da série 5.x, que inclui a 5.3 usada pelo Infinispan 15.0). **URL:** http://www.jgroups.org/manual5/index.html (certificado TLS do host inválido; baixado por HTTP em 03/11/2026). **Chave proposta:** `jgroups_manual_5`.
- **Lido:** §3.10.13 "Disconnecting from a channel" (o `disconnect()` envia pedido de saída ao coordenador, que instala nova visão); §6.8 "Failure Detection"; §6.8.4 FD_ALL3 (suspeita quando não há *heartbeat* nem dados no *timeout*); §6.8.6 FD_SOCK2 (`connectionClosed(ping-dest)` emite SUSPECT; troca de visão não gera suspeita); §6.8.7, Tabela 35 "Failure detection behavior" ("Member N leaves: Since this is a graceful leave, none of the failure detection protocols kick in"; *crash* com o socket fechado pelo SO é detectado de imediato).
- **Uso:** DES-54 (por que `podman stop` não exercita a detecção) e MET-15/Metodologia 320 (por que a interface é derrubada antes do SIGKILL).

### [CAND-086] Toward a Generic Fault Tolerance Technique for Partial Network Partitioning — promovida a REF-086 (Decisão 021, 2026-11-04; texto integral lido)
- **Autores:** Alfatafta, Mohammed; Alkhatib, Basil; Alquraan, Ahmed; Al-Kiswany, Samer. **Ano:** 2020. **Fonte:** Proc. 14th USENIX OSDI, p. 351-368. Sem DOI (OpenAlex). **URL:** https://www.usenix.org/conference/osdi20/presentation/alfatafta. **Chave proposta:** `alfatafta_partial_partitioning_2020`.
- **Lido:** só o resumo ("Partial network partitions disrupt the communication between some but not all nodes in a cluster"). O resumo não dá frequência relativa de partições parciais.
- **Alternativa:** Alquraan, Takruri, Alfatafta, Al-Kiswany, "An Analysis of Network-Partitioning Failures in Cloud Systems", OSDI 2018, p. 51-68 (OpenAlex; resumo sem dado sobre parciais).
- **Uso:** só se o Gestor quiser fonte específica para a definição de degradação/partição parcial (F4, DES-55). Não sustenta "raramente".

---

## Eixo N — Escala de severidade da perda em F4, N1 a N5 (2026-11-04)

> Levantadas para `monografia-atual/Desenvolvimento.tex:197` (DES-55). A auditoria `wt-gate-v4/docs/rastreabilidade-limiares-s5-s7-2026-11-04.md` (itens 48 a 51) mostrou que N1 = 1 %, N2 = 5 % e N3 = 20 % não têm fonte. N4 e N5 já se apoiam em REF-034 (Bailis e Kingsbury, p. 50 e 52, conferido). Consultas sem e-mail (R-EMAIL): Crossref e OpenAlex sem `mailto`. Textos extraídos em `tcc/leituras/CAND-087..089-*.txt`. **Aprovadas na Decisão 021 (2026-11-04) e incluídas no `.bib` (REF-083..085).**

### [CAND-087] End-to-End Internet Packet Dynamics — promovida a REF-083 (Decisão 021)
- **Autor:** Paxson, Vern. **Ano:** 1997. **Fonte:** Proc. ACM SIGCOMM '97, p. 139-152. **DOI:** 10.1145/263105.263155 (Crossref). Versão de periódico: IEEE/ACM Transactions on Networking 7(3):277-292, 1999, DOI 10.1109/90.779192 (Crossref). **Chave proposta:** `paxson_packet_dynamics_1997`.
- **Lido:** §5.1 "Loss rates" e §5.2 no preprint LBNL-40488 (mesmo texto da versão SIGCOMM; a paginação do preprint não é a dos anais, então citar por seção). Trechos: "in N1, 2.7% of the packets were lost, while in N2, 5.2%"; "For 'busy' connections, the loss rates jump to 5.7% in N1 and 9.2% in N2"; Tabela 1, "Into Europe" com 16,9 % em períodos de carga ("The 17% loss rate going into Europe is particularly glum"); §5.2, extremos de 47 %, 65 % e 68 % e "For all of these extremes, no packets were lost in the reverse direction!" (perda assimétrica).
- **Limite:** caminhos de longa distância na Internet de 1994-95, não rede interna de *cluster*. Serve para "perdas de 1 % a 20 % já foram medidas em caminhos reais", não para afirmar que são típicas de centro de dados.
- **Uso:** N1, N2 (ordem de grandeza 2,7 % e 5,2 %) e N3 (ordem de 17 %); também apoia a variante assimétrica de F4.

### [CAND-088] Pingmesh: A Large-Scale System for Data Center Network Latency Measurement and Analysis — promovida a REF-084 (Decisão 021)
- **Autores:** Guo, Chuanxiong; Yuan, Lihua; Xiang, Dong; Dang, Yingnong; Huang, Ray; Maltz, Dave; Liu, Zhaoyi; Wang, Vin; Pang, Bin; Chen, Hua; Lin, Zhi-Wei; Kurien, Varugis. **Ano:** 2015. **Fonte:** Proc. ACM SIGCOMM 2015, p. 139-152. **DOI:** 10.1145/2785956.2787496 (Crossref). **Chave proposta:** `guo_pingmesh_2015`.
- **Lido:** §4.2 "Packet drop rate", §4.3 (SLA) e §5.2 "Silent random packet drops" (PDF da Microsoft Research; citar por seção). Trechos: "the packet drop rates are in the range of 10-4 - 10-5 ... unless network incidents happen" (§4.2); alerta quando "the packet drop rate is greater than 10-3" (§4.3); num incidente com *switch spine* defeituoso, "several source and destination pairs that experienced around 1%-2% random packet drops" (§5.2).
- **Uso:** N1. Mostra que 1 % já é falha num centro de dados (duas a três ordens acima do normal) e que perdas aleatórias de 1 % a 2 % ocorreram em produção.

### [CAND-089] NetBouncer: Active Device and Link Failure Localization in Data Center Networks — promovida a REF-085 (Decisão 021)
- **Autores:** Tan, Cheng; Jin, Ze; Guo, Chuanxiong; Zhang, Tianrong; Wu, Haitao; Deng, Karl; Bi, Dongming; Xiang, Dong. **Ano:** 2019. **Fonte:** Proc. 16th USENIX NSDI, p. 599-614. Sem DOI (OpenAlex W2900985109). **URL:** https://www.usenix.org/conference/nsdi19/presentation/tan. **Chave proposta:** `tan_netbouncer_2019`.
- **Lido:** §7.1 e §8 "Deployment experiences" (Azure). Trechos: p. 608, "the packet drop rate of the link changed from 60% to 100%" (até o enlace ser desligado); p. 609, Caso 1 (falha cinzenta de *spine*), "this link was constantly dropping packets with around 15% packet drop probability"; Caso 2, tráfego Scavenger com cerca de 35 %; switch alternando entre normal e "100% packet drops"; critério de alarme de "1% packet drops".
- **Uso:** N3 (ordem de 15 % a 35 % em falhas cinzentas de produção) e reforço de N1 (1 % como limiar de enlace defeituoso) e de N4/N5 (60 % a 100 %).

---

## Eixo O — Guias Infinispan 15.0, IUT, energia e detecção antecipada de partição/falha cinzenta/defasagem (2026-11-04)

> Pedido do autor repassado pelo coordenador em 04/11/2026, com aval geral "entra tudo que for relevante de verdade" (Decisão 021). Consultas a Crossref, OpenAlex e páginas públicas, sem e-mail (R-EMAIL). Textos em `tcc/leituras/CAND-086` e `CAND-090..101-*.txt`. Todas, exceto CAND-101, foram promovidas na Decisão 021.

### [CAND-090] Guide to Infinispan Server (Infinispan 15.0) — promovida a REF-087
- URL https://infinispan.org/docs/15.0.x/titles/server/server.html. A página da série 15.0.x exibia 15.0.22.Final em 04/11; não existe guia fixado em 15.0.21 (a imagem usada). Lido: §12.2.2 TCPPING (lista estática de membros).
- **Uso:** `Desenvolvimento.tex`:73 e :97 (descoberta por TCPPING sobre lista explícita de hosts).

### [CAND-091] Using the Infinispan REST API (Infinispan 15.0) — promovida a REF-088
- URL https://infinispan.org/docs/15.0.x/titles/rest/rest.html. Lido: §2.4.2 `GET /rest/v2/container/health` (HEALTHY, HEALTHY_REBALANCING, DEGRADED, FAILED; `number_of_nodes`, `node_names`); §2.4.3 status sem autenticação; §2.1.31 `?action=get-availability`; §2.1.32 `POST ...?action=set-availability&availability={AVAILABLE|DEGRADED_MODE}`, válido com DENY_READ_WRITES ou ALLOW_READS ("DEGRADED_MODE prevents read and write operations on caches when network partitions occur").
- **Uso:** `Desenvolvimento.tex`:73 (validação do tamanho do cluster pelo endpoint de saúde) e :143 (controlador lê a saúde pela API REST); atuador possível para forçar DEGRADED_MODE.

### [CAND-092] Using the Infinispan Command Line Interface (Infinispan 15.0) — promovida a REF-089
- URL https://infinispan.org/docs/15.0.x/titles/cli/cli.html. Lido: §9.4 AVAILABILITY(1), `availability --mode=[AVAILABLE|DEGRADED_MODE] CACHE_NAME`.
- **Uso:** alternativa ao REST para a ação de modo degradado; registro dos comandos de operação.

### [CAND-093] Berger (1982), Multiparameter Hypothesis Testing and Acceptance Sampling — promovida a REF-090
- Technometrics 24(4):295-300, DOI 10.1080/00401706.1982.10487790 (Crossref). Texto integral NÃO obtido (pago; scan DTIC ADA091607 com HTTP 403). Conteúdo conferido por fonte secundária do próprio autor: Casella e Berger, verbete "Hypothesis Testing" (1999), seção 3: "Ho can be rejected if and only if each of the individual hypotheses Ho-y can be rejected", com o exemplo de amostragem de aceitação atribuído a Berger 1982.
- **Uso:** `Metodologia.tex`:229, os três critérios exigidos "conjuntamente" formam um teste de interseção-união; citar sem página ou teorema.

### [CAND-094] Intel ARK, Core i5-14600KF — promovida a REF-091
- Processor Base Power 125 W; Maximum Turbo Power 181 W; 14 núcleos (6P+8E), 20 threads, lançamento Q4'23. A ficha não dá potência ociosa. Hospedeiro documentado em `wt-gate-v4/runs/vm-s4-2026-10-28/V-S4.md`.
- **Uso:** `Metodologia.tex`:335 (potência máxima do modelo linear); a razão ociosa/máxima continua como sensibilidade {0,3; 0,5; 0,7}.

### [CAND-095] Hayashibara et al. (2004), The φ Accrual Failure Detector — promovida a REF-092
- SRDS 2004, p. 66-78, DOI 10.1109/RELDIS.2004.1353004. Lido no relatório técnico JAIST IS-RR-2004-010 (mesmo conteúdo; citar por conceito/seção). Sinal: chegadas de heartbeat; φ = −log10(P_later(t_now − T_last)) com distribuição normal estimada numa janela; φ = 1, 2, 3 ↔ probabilidade de engano ≈ 10 %, 1 %, 0,1 %. Exemplo da seção 2: limiar baixo → parar de enviar trabalho ao nó; moderado → realocar; alto → remover. Tempo de detecção cresce com o limiar (enlace Japão–Suíça, heartbeat de ~100 ms).
- **Uso:** base para ações graduadas do controlador antes da detecção definitiva (ação b).

### [CAND-096] Huang et al. (2017), Gray Failure — promovida a REF-093
- HotOS '17, p. 150-155, DOI 10.1145/3102980.3103005. Sinal: "observabilidade diferencial" (o detector não vê o que a aplicação vê). Propõe medir o que a aplicação observa, por exemplo sondas servidor-a-servidor como Pingmesh (§4.2), e monitoração multidimensional (§4.1). Sem tempo de detecção medido.
- **Uso:** justificativa conceitual da sonda de defasagem (a) e do controlador ver o que o JGroups não vê.

### [CAND-097] Huang et al. (2018), Panorama — promovida a REF-094
- OSDI '18, p. 1-16. Sinal: erros observados pelos componentes requisitantes (observação in situ). Detecta 15 falhas cinzentas reais em menos de 7 s cada; os detectores existentes detectaram só uma (86 s / menos de 300 s); falhas fail-stop em menos de 10 s (ZooKeeper em menos de 10 ms).
- **Uso:** apoia usar as observações do cliente (programa de carga) como sinal de falha.

### [CAND-098] Bailis et al. (2012), Probabilistically Bounded Staleness — promovida a REF-095
- PVLDB 5(8):776-787. Define t-visibilidade e k-staleness; modelo WARS; validação no Cassandra inserindo versões crescentes de uma chave e lendo concorrentemente (§5.2). Considera só operação estável, sem falhas (§6, "Failure modes").
- **Uso:** vocabulário e método para a medida de defasagem da sonda (a); não trata partição.

### [CAND-099] Wada et al. (2011), consistência observada em armazenamentos de nuvem — promovida a REF-096
- CIDR 2011, p. 134-143. Escritor grava o timestamp atual a cada 3 s e leitores leem 50 vezes por segundo, em threads, processos, VMs ou regiões diferentes; mede a probabilidade de ler o valor mais recente em função do tempo desde a escrita.
- **Uso:** precedente direto da sonda que escreve por um nó e lê pelos outros (a).

### [CAND-100] Gray e Cheriton (1989), Leases — promovida a REF-097
- SOSP '89, p. 202-210, DOI 10.1145/74850.74870. Autorização com prazo; se o detentor fica inalcançável, o servidor espera a expiração; prazo curto limita o efeito de falhas de comunicação e partições; exige margem para defasagem de relógio.
- **Uso:** princípio para a ação (b): um nó só aceita escritas enquanto renovar contato com a maioria; sem renovação, entra em modo restrito antes da exclusão formal.

### [CAND-101] Alquraan et al. (2018), An Analysis of Network-Partitioning Failures in Cloud Systems — NÃO promovida
- OSDI '18, p. 51-68. Lida parcialmente; redundante com REF-086 para o desenho. Fica como candidata.
