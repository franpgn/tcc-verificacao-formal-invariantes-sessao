# Glossário

> Termos técnicos do TCC, extraídos do arquivo base e da literatura inicial. Mantido pelo **Bibliotecário**; usado pelo **Escritor** para consistência terminológica e pelo **Revisor** para checagem. Termos em inglês de uso consagrado ficam em itálico no LaTeX (`\emph{...}`).

| Termo | Definição (no contexto do TCC) | Fonte/origem |
|---|---|---|
| **Data grid** / **In-Memory Data Grid (IMDG)** | Armazenamento chave-valor em memória, distribuído entre nós, com particionamento, replicação e realocação de dados quando a composição do cluster muda. No TCC, plataforma onde se externaliza o estado de sessão. | Arquivo base; REF-006, REF-009 |
| **Estado de sessão** | Conjunto de estados de curta duração associados a uma identidade autenticada: estado lógico da sessão (válida/inválida/bloqueada), metadados de renovação, contadores de tentativas, marcas de bloqueio. | Arquivo base |
| **Sticky session** (afinidade de sessão) | Roteamento que fixa um cliente a um nó específico para manter estado local; o TCC busca evitar essa rigidez externalizando o estado. | Arquivo base |
| **Replicação síncrona** | Esquema em que a confirmação de uma escrita depende de confirmação remota (réplica ou quórum), impondo dependência direta do RTT e da variabilidade de rede no tempo de resposta. | Arquivo base; cf. CAND-003, CAND-004 |
| **Replicação assíncrona** | Esquema em que a escrita é confirmada localmente e propagada depois; reduz dependência do RTT na escrita, mas admite estados defasados entre réplicas e reordenação efetiva de eventos observados por nós distintos. | Arquivo base; REF-001 |
| **RTT (Round-Trip Time)** | Tempo de ida e volta de uma mensagem entre dois nós; determinante do custo de confirmações remotas na replicação síncrona. | Arquivo base |
| **Failover** | Mudança do caminho de atendimento em resposta a falhas detectadas (ou a partições de rede), redirecionando requisições para nós/réplicas disponíveis. | Arquivo base |
| **Partição de rede** | Situação em que subconjuntos de nós deixam de trocar mensagens por um período, podendo processar operações com visões divergentes do estado. | Arquivo base; cf. CAND-002, CAND-007 |
| **Rebalancing** | Redistribuição de dados/responsabilidades entre nós quando a topologia do cluster muda (entrada/saída de nós). | Arquivo base; REF-006 |
| **State transfer** | Transferência de estado entre nós decorrente de *rebalancing* ou de recuperação; altera o mapeamento entre chaves e nós e pode coexistir com falhas. | Arquivo base; REF-006 |
| **Reconciliação** | Processo de combinar versões divergentes de um dado após partição/falha, possivelmente exigindo resolução de conflitos ou convergência eventual. | Arquivo base; REF-001, REF-002 |
| **Consistência eventual** | Garantia de que, na ausência de novas atualizações, todas as réplicas convergem para o mesmo valor. | REF-001, REF-002 |
| **CRDT (Conflict-free Replicated Data Type)** | Tipo de dado replicado projetado para convergir sem coordenação, dado que as operações satisfazem propriedades algébricas (comutatividade / *semilattice*). | REF-002 |
| **Invariante (de domínio)** | Propriedade que deve permanecer verdadeira em todas as execuções permitidas pelo modelo, apesar de concorrência e falhas. | Arquivo base; REF-003 |
| **Irreversibilidade de invalidação** | Invariante: após um *logout*, a sessão não deve retornar ao estado "válida". | Arquivo base |
| **Monotonicidade de contadores** | Invariante: contadores (p.ex. de tentativas falhas) não devem diminuir como efeito de reconciliações. | Arquivo base |
| **Consistência de bloqueio** | Invariante: sessão/identidade marcada como bloqueada não deve ser validada como ativa. | Arquivo base |
| **Model checking** (verificação de modelos) | Técnica de verificação automática que explora o espaço de estados de um modelo para checar invariantes e propriedades temporais, produzindo contraexemplos quando há violação. | REF-003; cf. CAND-012 |
| **Contraexemplo** | Execução concreta produzida pelo verificador que viola uma propriedade especificada; no TCC, usado para orientar o desenho do controlador e requisitos do protocolo. | Arquivo base; REF-003 |
| **TLA+ / TLC** | Linguagem de especificação formal (TLA+) e seu verificador de modelos (TLC), candidatos à trilha formal do TCC. | REF-003; CAND-010 |
| **Sistema de transições (discretas)** | Modelo formal composto de estados e transições; forma como o sistema será modelado na trilha formal. | Arquivo base |
| **Timestamp lógico** | Marcador de ordem (não físico) associado a atualizações, usado para versionamento/ordenação no modelo. | Arquivo base; cf. CAND-001 |
| **Propriedade de convergência** | Propriedade de que réplicas divergentes acabam por atingir um estado comum compatível com as atualizações aplicadas. | Arquivo base; REF-002 |
| **SLO (Service Level Objective)** | Objetivo de nível de serviço — meta quantitativa sobre um indicador (p.ex. p99 de latência ≤ X ms); base do "controle orientado a SLO". | REF-005 |
| **SLI / SLA** | *Service Level Indicator* (métrica medida) / *Service Level Agreement* (acordo contratual); relacionados ao SLO. | REF-005 |
| **Latência de cauda** | Parte superior da distribuição de latência (p95/p99/p99.9+), desproporcionalmente impactante em serviços com muitos componentes. | REF-004 |
| **Percentil p95 / p99** | Valores de latência abaixo dos quais ficam 95% / 99% das requisições; métricas primárias do TCC. | REF-004; arquivo base |
| **Controlador heurístico** | Componente externo ao *data grid* que lê observáveis em janelas temporais e seleciona políticas/ações de mitigação por meio de regras determinísticas. | Arquivo base |
| **Histerese** | Mecanismo de decisão com limiares de entrada/saída distintos para evitar oscilação rápida entre estados/decisões. | Arquivo base; cf. CAND-016 |
| **Flapping** | Alternância rápida e indesejada de decisões/estados; o controlador usa histerese e limites de taxa de mudança para evitá-lo. | Arquivo base |
| **Cross-site replication** | Replicação entre clusters em localidades distintas (no Infinispan), com *backups* síncronos/assíncronos entre sites; usada nos cenários multi-site do TCC. | REF-007 |
| **Quórum** | Subconjunto mínimo de nós cujo acordo é necessário para confirmar uma operação (na replicação síncrona). | Arquivo base; cf. CAND-004 |
| **Workload** (carga de trabalho) | Conjunto de operações geradas sobre o sistema durante um experimento (no TCC: operações tipo sessão — login/validate/renew/logout/contadores). | Arquivo base |
| **Injeção de falhas** | Provocação controlada de falhas (crash/restart, latência/*jitter*, partição, *state transfer*, indisponibilidade de site) para avaliar o sistema. | Arquivo base; cf. CAND-017 |

> **Observação:** novos termos devem ser adicionados aqui assim que surgirem nos capítulos. Termos cuja definição depende de uma referência ainda não aprovada ficam marcados com "cf. CAND-XXX" até a validação do Gestor.
