# 10. Glossário de siglas, códigos e termos internos

> Relatório técnico final da parte prática, capítulo 10. Senior, 11/11/2026, para o Escritor.
> O autor exige que nada fique só como código. Este glossário explica, em linguagem simples, cada sigla e cada código
> que aparece nos documentos técnicos, nos dados e nos capítulos deste relatório. **Na monografia, o Escritor deve usar
> o nome por extenso** e, quando o código for necessário (por exemplo F2 ou I1), defini-lo na primeira ocorrência.
> Onde um código interno não deve ir para o texto, isso está dito na coluna "No texto".

---

## 10.1 Como ler: códigos que significam coisas diferentes

Vários prefixos foram reutilizados em documentos diferentes. Antes de citar, confira o documento de origem.

| Código | Significados encontrados | Qual usar na monografia |
|---|---|---|
| S1, S2, S3 | (a) cenários de carga; (b) etapas S1 a S7 da verificação dos injetores na VM; (c) fatias de integração S0 a S7 de 16/10 | só (a); para (b), dizer "etapa da verificação dos injetores" |
| S_lat, S_err, S_top, S_lag, S_viol | sinais do controlador | escrever "sinal de latência", "sinal de erro" etc. |
| P0, P1, P2 | políticas de reação do controlador | pode usar, definindo |
| P-01 a P-65 | problemas do registro técnico (`docs/registro-de-problemas.md`) | não usar; descrever o problema |
| P-1 a P-5 | decisões do pré-registro do benchmark refeito | não usar |
| R1 a R4 | regras de decisão do controlador (máquina de estados) | pode usar, definindo (o texto já usa) |
| R1 a R8 | regras de uso da ação de ignorar o *cache* (relatório da A4) | não usar; descrever |
| R-1, R-2 ... | ressalvas de validações | não usar |
| A1, A4 | alternativas de tratamento do *cluster*: A1 é a regra de fusão própria; A4 é ignorar o *cache* no nó suspeito (a mesma ação que o controlador chama AC-7) | A1 pode ficar, com o nome "regra de fusão A1"; A4 só como "ação de ignorar o *cache*" |
| A-1 a A-17 | ambiguidades ou ressalvas de validações | não usar |
| C1 a C5, C8 | critérios de estacionariedade da mistura de operações | o texto já usa C1 a C3; manter |
| C-1 a C-10 | decisões do pré-registro da calibração | não usar |
| C3 (de "complemento 3") | terceiro complemento do pré-registro da nova medição | dizer "complemento ao pré-registro" |
| G-1, G-2 ... | decisões do Gestor numeradas por documento | não usar |
| G-0 a G-6 | passos do teste de partição sem carga (B-29) | não usar |
| D-1, D-8 ... | decisões numeradas por documento (D-8: desempate T2) | não usar |
| D1 a D8 | dimensões de classificação do mapeamento sistemático | pode usar (já está na Metodologia) |
| E-1 | interceptador que força replicação síncrona, descartado em 05/11 | dizer "interceptador no servidor" |
| E-1 a E-9, E-J1 a E-J8 | expectativas pré-registradas das rodadas do TLC | não usar |
| M1 a M5 | mecanismos de violação (seção 10.5) | descrever; M1 pode aparecer como "janela entre o corte e a detecção" |
| M-0 | medição de interferência do hospedeiro | não usar |
| T1 a T22 | parâmetros da `tab-parametros-baseline` | manter (já estão no texto) |
| T2 (do B-45) | variante de desempate de marcas de tempo pelo conteúdo | dizer "desempate pelo conteúdo do registro" |
| T-A a T-I | tarefas do plano de fechamento da etapa formal (15/10) | não usar |
| W | janela de observação do controlador | pode usar como símbolo, definindo |
| W-1 a W-10 | pacotes de trabalho de implementação (seção 10.9) | não usar |
| H0, H1, H2 | perfis de uso do hospedeiro | descrever ("hospedeiro dedicado à medição") |
| H_V, H_L | hipóteses do teste | podem virar $H_V$ e $H_L$, definidas |
| h4, h4a, h4b, h4c | critério de descarte por infraestrutura | o texto já usa h4; manter |
| K1 a K10 | critérios de recusa da nova medição de referência | não usar; descrever |
| K1 a K4 (do controlador) | as quatro ações sobre parâmetros do cliente do projeto de outubro (K1 admissão, K2 ritmo, K3 adiamento das escritas administrativas, K4 espera entre retentativas), retiradas em 04/11 | o texto já as descreve por extenso (`Desenvolvimento.tex:236`); não usar os códigos |
| N1 a N5 | severidades de perda da F4 | o texto já usa; manter |
| N-1 a N-6 | notas de um documento de ações | não usar |
| L | desfecho latência (p99 da ação S3) | símbolo $L$, definido |
| L+ | carga S3 mais intensa da avaliação | descrever os cinco parâmetros |
| L-0, L-0c, L-1, L-2 | etapas antigas do roteiro da VM | não usar |
| O1 a O7 | operações do protocolo | manter (já estão no texto) |
| O-3, O8 | operação de compressão dos CSV na VM; um veredito do orquestrador | não usar |
| I1 a I6 | invariantes | manter |
| I-1 a I-5, I-3b | testes de integração do atuador no *cluster* local | não usar |
| B-xx | itens do *backlog* técnico (seção 10.9) | não usar |
| B1, B2 | achados de uma validação do orquestrador | não usar |
| B-1 a B-9 | decisões do pré-registro da avaliação | não usar |
| V | taxa de violações (desfecho) | símbolo $V$, definido |
| V-xx | validação de uma entrega (por exemplo V-W6, V-S7) | não usar |
| F1 a F4 | classes de falha | manter |
| F-1 a F-10 | critérios do ensaio do F1; F-1 também foi um achado de validação | não usar |

## 10.2 Sistema, infraestrutura e ferramentas

| Termo | Significado | No texto |
|---|---|---|
| Infinispan | *data grid* em memória da Red Hat; versão do servidor 15.0.21.Final | sim |
| IMDG | *In-Memory Data Grid*, armazenamento chave-valor distribuído em memória | já na lista de siglas |
| *data grid* | o mesmo que IMDG | sim |
| `isn1`, `isn2`, `isn3` | os três nós do *cluster*, cada um num contêiner; `isn2` é o alvo de F1 e F3, `isn3` de F2 e F4 | sim, como nomes de nó |
| Hot Rod | protocolo binário de acesso do cliente ao Infinispan | sim |
| REST | interface HTTP do servidor, usada para saúde, vista, criação de *caches*, *logging* e ações administrativas | sim; acrescentar à lista de siglas |
| API administrativa | o conjunto de chamadas REST de gerenciamento | sim |
| JGroups | biblioteca de comunicação de grupo usada pelo Infinispan para formar o *cluster* | sim |
| vista (*view*) | a lista de membros que o JGroups considera vivos | sim |
| coordenador | o nó mais antigo da vista, que instala vistas novas e conduz a resolução de conflitos | sim |
| TCPPING | descoberta de membros por lista fixa de endereços | sim |
| FD_ALL3 | detector de falhas por falta de mensagens de presença (padrão: a cada 8 s, suspeita em 40 s) | sim |
| FD_SOCK2 | detector de falhas pela conexão TCP com o vizinho (suspeita quando a conexão fecha) | sim |
| VERIFY_SUSPECT2 | confirmação da suspeita antes de excluir o nó (1 s na configuração) | sim |
| MERGE3 | protocolo que descobre partições curadas e inicia a fusão (a cada 10 a 30 s) | sim |
| NAKACK2, UNICAST3 | protocolos de entrega confiável com retransmissão | se precisar |
| GMS | serviço de pertença do grupo, que instala as vistas | se precisar |
| `sessions`, `counters` | *caches* síncronos de sessões e de identidades/contadores | sim |
| `sessions-async`, `counters-async` | os mesmos, em modo assíncrono; usados na avaliação | sim |
| `canario`, `canario-async` | *caches* da sonda de defasagem do controlador, fora dos invariantes | sim, explicados |
| `DIST_SYNC`, `DIST_ASYNC` | modo distribuído síncrono e assíncrono do Infinispan | sim |
| SYNC, ASYNC | abreviação dos dois modos nos nomes de célula (ASYNC-F2 = célula da partição em modo assíncrono) | só por extenso |
| `owners` (`numOwners`) | número de cópias de cada chave (2) | sim |
| segmentos (`numSegments`) | divisões do espaço de chaves para distribuição (64) | sim |
| `DENY_READ_WRITES` | estratégia diante de partição: o lado minoritário recusa leituras e escritas depois da detecção | sim |
| `ALLOW_READ_WRITES` (ARW) | estratégia alternativa que deixa os dois lados atenderem; testada no piloto e não adotada | só como alternativa |
| `PREFERRED_NON_NULL` (PNN) | política de fusão padrão: fica a cópia preferida (da maioria), salvo se ela for nula | por extenso |
| `PREFERRED_ALWAYS` | outra política de fusão padrão, testada no piloto com ARW | só como alternativa |
| política de fusão (*merge policy*) | regra que decide a cópia que fica quando os lados de uma partição se reencontram | sim |
| resolução de conflitos (CR) | etapa da fusão em que o coordenador lê as cópias de todos os donos e grava a escolhida | por extenso |
| `DEGRADED_MODE` | estado de disponibilidade de um *cache* que recusa operações (o lado minoritário entra nele) | sim |
| `ignored-caches` | lista de *caches* que um servidor deixa de atender; é a base da ação AC-7 | sim, explicado |
| `merge-policy`, `when-split` | atributos da configuração do *cache* para política de fusão e estratégia diante de partição | sim |
| `remote-timeout`, `acquire-timeout` | tempo-limite das chamadas remotas síncronas (17,5 s) e da obtenção de trava (15 s) | sim |
| `state-transfer` | transferência de dados entre nós quando a topologia muda (tempo-limite de 60 s) | sim |
| rebalanceamento | redistribuição de segmentos depois de mudança de topologia | sim |
| `FORCE_SYNCHRONOUS` | indicador interno do Infinispan que força replicação síncrona numa operação | se precisar |
| `EntryMergePolicy` | interface Java que uma política de fusão própria implementa | se precisar |
| extensão do servidor | o JAR `tcc-extensao-servidor.jar`, com a classe da regra A1, instalado em `server/lib` dos três nós | sim |
| `ISPN000093` | mensagem do servidor que registra a instalação da vista de fusão | só nos apêndices |
| `ISPN006010` | aviso do servidor de que operações condicionais exigem *cache* transacional | o texto já cita |
| `ISPN005003`, `ISPN000136` | erros com pilha registrados quando o *cache* é ignorado ou o nó está em modo degradado | não |
| `ISPN000476`, `ISPN004015`, `ISPN004007` | tempo-limite do pedido REST sob corte; falha ao adicionar servidor no cliente; aviso do cliente sob corte | não |
| Podman, *rootful* | gerenciador de contêineres; *rootful* = executado como root, o que permite `nsenter` e `tc` | sim |
| `podman-compose` | ferramenta que sobe os três contêineres a partir do arquivo de composição | sim |
| `k8s-file` | modo de registro do Podman em arquivo do contêiner, com teto de tamanho | se precisar |
| netns (*network namespace*) | espaço de rede isolado de cada contêiner | sim |
| `nsenter` | comando que entra no *namespace* de rede do contêiner a partir da VM | sim |
| `tc`, `netem` | ferramenta de controle de tráfego do Linux e sua disciplina de emulação de rede (atraso, perda) | sim |
| `clsact`, `u32`, `prio` | disciplina com ganchos de entrada e saída, filtro por endereço e disciplina de prioridade, usados nos injetores de F2 e F4 | se precisar |
| `sch_netem` | módulo do *kernel* que implementa o `netem`; faltava no WSL2 | sim |
| WSL2 | subsistema Linux do Windows, usado no início | sim; acrescentar à lista de siglas |
| Hyper-V | hipervisor do Windows que roda a VM `tcc-f3` | sim |
| VM, vCPU | máquina virtual; processador virtual (4) | por extenso ("máquina virtual") |
| *Default Switch* | rede NAT padrão do Hyper-V; o IP da VM muda a cada reinício | se precisar |
| cgroup v2 | mecanismo do *kernel* que contabiliza CPU e memória por contêiner | sim |
| *steal* | tempo em que o hipervisor tirou a CPU virtual da VM | sim |
| PSI | *Pressure Stall Information*, contadores do *kernel* de tempo parado à espera de recurso (E/S, CPU, memória); base do h4a | sim, por extenso |
| RAPL | contadores de energia do processador Intel, não expostos à VM | se precisar |
| SSD NVMe | disco do hospedeiro (XrayDisk 512 GB), origem dos travamentos de E/S | sim |
| ASPM | economia de energia do barramento PCIe, desligada como mitigação | o texto já usa |
| TRIM | comando que devolve ao SSD os blocos livres, aplicado como mitigação | o texto já usa |
| JVM | máquina virtual Java (OpenJDK 17) | já na lista |
| JAR | arquivo de pacote Java (o programa de carga e a extensão) | sim |
| Maven, Surefire, JUnit | ferramenta de construção Java, executor de testes e biblioteca de testes | sim |
| *bundle* | pacote levado à VM: `git archive` da tag mais os JARs, cada um com SHA-256 | sim |
| SHA-256 | resumo criptográfico usado para conferir arquivos | sim |
| tag, *commit*, *worktree*, ramo | identificadores do repositório git; *worktree* é uma cópia de trabalho de um ramo (`wt-*`) | só tag e *commit*, se precisar |
| `int/2026-11-06-aval` (`4b96798`) | tag da versão integrada usada na avaliação | sim, em nota |
| TLA+, TLC | linguagem de especificação de Lamport e seu verificador de modelos | já na lista |
| SANY | analisador sintático do TLA+ | não |

## 10.3 Modelo formal

| Termo | Significado |
|---|---|
| `SessionStore.tla` | o modelo do protocolo de sessão |
| `MCSession.tla` | módulo de verificação: limites, simetria, propriedades por réplica, cenário semeado |
| `MCFifo.tla` | controle com canal FIFO e sem desempate, usado no B-45 |
| `MCSessionJoin.tla` | variante com fusão das sessões pelo máximo de conteúdo (a regra A1 no modelo) |
| `MCPerda.tla`, `MCRelogio.tla` | variantes com perda de mensagens e com relógio pelo máximo na entrega |
| `Login`, `Logout`, `IncrementFailure`, `ResetFailures`, `Block`, `Unblock` | ações locais que correspondem a O1, O3, O4, O5, O6 e O7 |
| `EntregaMensagem` | entrega de uma mensagem pendente a uma réplica |
| `Cair`, `Reviver` | queda e volta de uma réplica (F1 no modelo) |
| `Particionar`, `Mesclar` | início e fim de uma partição (F2 no modelo); `Mesclar` copia os registros de uma réplica do lado vencedor para o perdedor |
| `MesclarJoin` | fusão alternativa: máximo das cópias de todas as réplicas |
| `PodeAgir` | predicado que impede o lado perdedor de agir durante a partição |
| `ts` | marca de tempo do registro, tirada do relógio da réplica |
| desempate T2 | ordem total sobre o registro inteiro para decidir empates de `ts` (B-45) |
| `tiny`, `small`, `medium`, `full` | configurações de cardinalidade (seção 3.4 do capítulo 3) |
| semeado, livre | cenário com sessões já válidas e `Login` desabilitado; cenário com `Login` |
| cotas (`MaxCanal`, `MaxRelogio`, `MaxEventosFalha`) | limites de tamanho da fila, do relógio e do número de falhas, que tornam o espaço finito |
| `SemFalha`, `SoQueda`, `SoParticao` | restrições que isolam as colunas de falha |
| ANTES, DEPOIS, FIFO | revisões comparadas no B-45: sem desempate, com desempate, controle |
| `TypeOK` | invariante de tipos |
| `Quiescent` | canal vazio (todas as mensagens entregues) |
| I1F, I1M, I1P, I2Fa, I2Fb, I4F, I4Fp, I4M, I4Mp, MesclarPerdeChave | propriedades por réplica e por transição (capítulo 3, seção 3.3.2); o sufixo J indica a versão para `MesclarJoin` |
| `RelogioDomina` | propriedade da variante de relógio: o relógio nunca fica atrás de um registro guardado |
| estados distintos, gerados, fila, profundidade | estados únicos visitados; total de sucessores calculados; estados ainda não explorados; maior distância do estado inicial |
| traço (contraexemplo) | sequência de estados que leva a uma violação; "N estados" inclui o inicial |
| *worker* | linha de execução do TLC; com 1, o traço achado é o mais curto |
| simetria | redução que trata como iguais estados que diferem só por troca de nomes de réplicas ou sessões |
| exaustivo, fechou | a busca terminou sem estados na fila |
| `SEM_VIOLACAO_ATE_AQUI` | a busca parou no tempo-limite sem achar violação; não prova nada além do explorado |
| vácuo (vazia) | propriedade que só restringe passos de fusão, inalcançáveis na coluna |
| `CONFIRMADO-NO-MODELO`, `PRESERVADA-ATE-COTAS`, `VIOLADA-POR-CONSTRUCAO`, `ARTEFATO-DO-MODELO` | vocabulário de vereditos do catálogo |
| catálogo operação × falha × invariante | documento que liga operações, falhas e invariantes aos vereditos do TLC (`docs/catalogo-operacao-falha-invariante.md`) |
| Achado E | constatação de 02/10 de que o programa grava sessão e identidade como registro único, origem do modelo por registro |

## 10.4 Carga, falhas e observação

| Termo | Significado |
|---|---|
| O1 a O7 | operações: *login*, validação, *logout*, incremento de tentativas falhas, reposição de tentativas, bloqueio administrativo, desbloqueio |
| I1 a I6 | invariantes: irreversibilidade do *logout*, consistência de bloqueio, monotonicidade do contador, unicidade do motivo, convergência da sessão, convergência do contador |
| F1 | queda silenciosa de nó (interface desligada e `SIGKILL`, volta depois de 60 s) |
| F2 | partição de rede (um nó isolado dos outros dois) |
| F3 | atraso com *jitter* no canal de um nó |
| F4 | degradação parcial da rede (perda, atraso, duplicação ou reordenação, sem corte total) |
| N1 a N5 | severidades de perda da F4: 1 %, 5 %, 20 %, 50 % e 99,9 % |
| marcadores `start` e `end` | linhas com o instante de início e fim da falha, gravadas pelo injetor |
| *dead-man* | temporizador do sistema que limpa as regras de rede se o injetor morrer |
| S1, S2 | cenários de carga com mistura fixa de operações (50/50 e 95/5), usados na nova medição de referência |
| S3 | cenário de ciclo de vida de sessões: servidores de aplicação presos a nós, usuários, administrador, bloqueio por limite |
| mistura v1, v2, v3 | versões da carga de S1 e S2; a v3 (*slots* e gerações) é a usada nas medições |
| *slot*, geração | posição de uma sessão no universo Zipfian e a versão corrente dela |
| *token* antigo | validação desviada para a geração anterior com probabilidade 0,05 |
| tríade | *login*, validação e *logout* encadeados pela mesma *thread* em S1 e S2 |
| ação S3 | uma interação do usuário (página, *login*, *logout*), com as retentativas incluídas |
| falha percebida | ação S3 que falhou depois das retentativas da aplicação |
| carga L+ | carga S3 da avaliação: 40 usuários, tempo de pensar de 500 ms, sessão de 10 s, *logout* com probabilidade 0,9, bloqueio administrativo a cada 5 s em média |
| `pinned` | cada servidor de aplicação ligado a um único nó |
| auditor *online* | verificação de cada leitura da carga contra o que já foi confirmado |
| sonda de *replay* (imediato e tardio) | releitura da sessão depois do *logout*, por outro nó: 1 a 3 s depois (imediato) e no fim da carga (tardio) |
| `ReplayValidate` | a operação da sonda; fica fora de latência, vazão, erro e denominador |
| `late-max` | número máximo de sessões no *replay* tardio (306 na avaliação) |
| varredura final (`--sweep`) | leitura de todas as chaves depois da carga, confrontada com as invalidações confirmadas |
| checagem de fusão | espera da vista de três nós antes da varredura (até 72 s) |
| laço de convergência | comparação das réplicas depois da carga, a cada 4 s, até três rodadas seguidas sem divergência ou 600 s |
| divergência persistente | a execução chegou ao teto do laço com réplicas diferentes |
| forma 1 de I1 | *logout* confirmado pelo nó isolado e perdido na fusão (estado final errado) |
| forma 2 de I1 | *logout* confirmado pela maioria e relido válido pelo nó isolado durante a partição (exposição transitória) |
| perda permanente | sessões com I1 detectado depois da fusão (varredura, *replay* tardio, auditor depois da fusão) |
| exposição na janela | sessões com I1 detectado antes da fusão (*replay* imediato, auditor) |
| leitura defasada na troca de vista | I1 *online* por leitura de uma cópia de reserva atrasada logo depois da mudança de vista |
| I2 REAL | leitura de sessão válida sob identidade bloqueada depois do fim da cascata do bloqueio; é a única I2 que conta |
| `I2_JANELA_CASCATA` | a mesma leitura entre a confirmação do bloqueio e o fim da cascata; fora da contagem |
| `I2_CASCATA_INCOMPLETA` | a mesma leitura quando a cascata terminou com erro; fora da contagem |
| I2 INCERTA, I2 ESPERADA-POR-DESENHO, I2-PERDIDO | rótulos da varredura: caso sem classificação segura; bloqueio por limite (não invalida sessões); bloqueio administrativo que sumiu |
| `*_concorrentes` | leituras concorrentes com o bloqueio, só diagnóstico |
| `sem_marco` | leitura sob bloqueio sem marco de bloqueio registrado |
| invalidação perdida, conflito esgotado | invalidação que perdeu a escrita condicional e releu a sessão já invalidada; três conflitos seguidos na escrita condicional |
| códigos de retorno | `NONE` (sucesso sem dado extra), `VALID`, `INVALID`, `NOT_FOUND`, `BLOCKED`, `ERROR_TRANSPORT` (erro de comunicação ou tempo-limite), `ERROR_UNAVAILABLE` (recusa por indisponibilidade) |
| Δ_rec | tempo de recuperação: do fim da falha até o último evento anômalo |
| Δ_conv | tempo de convergência: do fim da carga até a primeira de três rodadas sem divergência |
| vazão útil | ações S3 sem falha percebida por segundo |
| fases antes, durante, recuperação | trechos da execução: antes da falha; entre `start` e `end`; os 191 s seguintes |
| âncora | linha "Warm-up até" do programa, a partir da qual se contam os instantes (falha a +49 s) |

## 10.5 Mecanismos de violação

| Código | Mecanismo | Invariantes |
|---|---|---|
| M1 | escrita aceita pelo lado que vai perder, entre o corte da rede e a detecção (cerca de 40 s), e perdida na fusão; em modo assíncrono, também escrita na maioria cujo dono primário está do lado cortado | I1, I2, I4 |
| M2 | leitura defasada em modo assíncrono logo depois da escrita | I1, I4 aparentes |
| M3 | atualização perdida em modo assíncrono sob perda de pacotes | I5, I6 |
| M4 | *logout* e bloqueio concorrentes trocam o motivo; mitigado no programa pela escrita condicional | I4 |
| M5 | fusão a partir de réplica atrasada do lado vencedor; rebalanceamento durante oscilação de topologia | I5, I6 |

Fonte: `wt-plano-ctrl/docs/plano-execucao-2026-11-04.md:276-285`.

## 10.6 Controlador

| Termo | Significado |
|---|---|
| controlador do *cluster* | componente que observa o *cluster* e age nele pela API administrativa; roda como *thread* no programa de carga |
| modos `off`, `shadow`, `active` | desligado; observa e decide sem agir ("observando"); age ("ativo") |
| NORMAL, CONSERVADOR | os dois estados da máquina de decisão |
| R1 a R4 | regras: R1 violação leva ao conservador na hora; R2 sinais altos fora do intervalo mínimo levam ao conservador; R3 no conservador, sinal alto zera a contagem de janelas calmas; R4 volta ao normal com condições de calma |
| *cooldown* (T_cooldown) | intervalo mínimo entre transições |
| T_estavel, T_hold_viol | tempo de topologia estável e retenção depois de violação exigidos para voltar ao normal |
| W | duração da janela de observação (4 s na avaliação) |
| k, m | janelas seguidas exigidas para entrar no conservador; janelas calmas seguidas exigidas para sair |
| kLag | janelas seguidas de defasagem no teto exigidas para P1 |
| θ_lat_in, θ_lat_out, θ_err_in, θ_err_out | limiares de entrada e saída dos sinais de latência e de erro (a diferença é a histerese) |
| lag_in, lag_out | limiares de entrada e saída da defasagem |
| n_lat, n_err | mínimo de eventos por janela para o sinal valer |
| sinal de latência (S_lat) | fração de eventos acima do p99 de referência da operação |
| sinal de erro (S_err) | fração de eventos com erro de transporte ou indisponibilidade |
| sinal de topologia (S_top) | saúde falhou, menos de três nós ou *cache* fora do estado saudável |
| sinal de defasagem (S_lag) | tempo até um valor gravado por um nó ser lido pelos outros dois |
| sinal de violação (S_viol) | novas violações de I1, I4 ou I2 real |
| P0 | política de não agir |
| P1 | política de isolar o nó suspeito; na avaliação, executada pela AC-7 |
| P2 | política de suspender o rebalanceamento quando um nó some; retirada do braço ativo em 05/11 |
| AC-1 | forçar o modo degradado no nó suspeito; inviável (vale para o *cluster* inteiro) |
| AC-2 | suspender o rebalanceamento global; base da P2, retirada |
| AC-3 | encurtar o `remote-timeout`; descartada (escritas indeterminadas) |
| AC-4 | trocar o apelido do *cache*; inviável (o Hot Rod não resolve apelidos) |
| AC-5 | parar o servidor suspeito pela REST (`server?action=stop`); ação de P1 até 06/11 |
| AC-6 | excluir o suspeito da vista pelo canal de diagnóstico do JGroups; verificada e não adotada |
| AC-7 | mandar o nó suspeito ignorar o `sessions-async` (`ignored-caches`); ação de P1 na avaliação |
| guarda de falso alarme | se a lista de *caches* ignorados aparecer na maioria, desfazer na hora |
| `suspeita-desfeita`, guarda contínua | regras do controlador para desfazer a AC-7 quando a suspeita some |
| trilha JSONL | registro de cada janela e decisão do controlador, uma linha JSON por janela (`decisoes-rep-001.jsonl`) |
| `meta`, `fim` | primeira e última linhas da trilha (parâmetros e encerramento) |
| T_enc | tempo máximo de encerramento do controlador (21 s no pior caso) |
| MAPE-K | ciclo monitorar, analisar, planejar, executar sobre conhecimento (já na lista de siglas) |
| p99 de referência | p99 por operação medido nas execuções sem falha da calibração, usado pelo sinal de latência |
| calibração, recalibração | campanhas que fixaram os limiares por regra pré-registrada (X-18 e X-19) |
| derivação | cálculo dos limiares a partir dos brutos da calibração (`analysis/calibracao_s3.py`) |

## 10.7 Alternativas de tratamento do *cluster* (documento de 06/11)

| Código | Alternativa | Destino |
|---|---|---|
| A1 | política de fusão própria no `sessions-async`, pelo máximo numa ordem de conteúdo (semirreticulado) | adotada como fator do experimento |
| A2 | política que remove a chave na fusão | virou a saída de segurança dentro da A1 (`A1-SAIDA-SEGURANCA`) |
| A3 | detecção mais cedo pela configuração do FD_ALL3 | não adotada |
| A4 | ignorar o *cache* no nó suspeito (`ignored-caches`) | adotada como ação de P1 (AC-7) |
| A5 | tarefa de servidor que desliga o conector Hot Rod do nó | não adotada |
| A6 | filtro de IP ou parada de conector pela REST | não adotada |
| A7 | fusão sem resolução, comandada por tarefa | não resolve |
| A8 | outras opções de servidor | não resolvem |
| `A1-POLITICA`, `A1-FUSAO` | linhas de registro que a extensão grava ao carregar a política e a cada decisão | as linhas de decisão não foram preservadas (capítulo 5) |

Fonte: `wt-plano-ctrl/docs/alternativas-tratamento-cluster-2026-11-06.md` §2 e §3.

## 10.8 Desenho e estatística

| Termo | Significado |
|---|---|
| célula | combinação de modo e falha (por exemplo, assíncrono com partição) |
| célula de teste, descritiva | célula com teste de hipótese; célula só com estimativas |
| braço | uma das condições comparadas dentro do bloco |
| braço A, braço B | nas células descritivas: controlador observando e controlador ativo |
| PO, PL, AO, AL | braços do fatorial na partição: regra de fusão atual (P de `PREFERRED_NON_NULL`) ou A1 (A), com o controlador observando (O) ou ativo (L de "ligado") |
| bloco | conjunto de execuções, uma por braço, com a mesma semente e ordem sorteada |
| efeito A1, efeito A4 | média dos contrastes do bloco entre os braços com e sem a regra A1, e com e sem o controlador ativo |
| interação | quanto o efeito do controlador muda quando a A1 está ligada |
| efeito simples | efeito de um fator com o outro fixo (por exemplo, controlador com a regra atual) |
| V | taxa de violações: pares distintos (chave, invariante) por mil ações S3 |
| L | p99 da ação S3 com espera |
| p99 com espera, sem espera | latência da ação incluindo ou não as esperas entre retentativas |
| posto mais próximo | método de percentil que toma o valor observado de posição ⌈p·n⌉ |
| H_V, H_L | hipóteses de redução de V e de não inferioridade do p99 |
| IU (interseção-união) | regra que só declara melhoria se as duas hipóteses forem rejeitadas; valor-p = o maior dos dois |
| Holm | correção sequencial para várias hipóteses que controla a chance de qualquer falso positivo (FWER) |
| FWER | probabilidade de pelo menos um falso positivo na família de testes |
| troca de sinal (permutação exata) | teste que compara a média dos contrastes com todas as trocas de sinal possíveis entre blocos |
| k | número de blocos com contraste diferente de zero; limita o menor valor-p (2/2^k) |
| IC *t*, *bootstrap*, IC hierárquico | intervalos de confiança pela distribuição *t*, por reamostragem de blocos e por reamostragem em dois níveis |
| não inferioridade, δ | o tratado não pode piorar mais que δ (10 %) |
| não avaliável | o braço de referência não teve violação em nenhum bloco |
| regra de três (3/N) | limite superior de 95 % para uma taxa com zero ocorrências em N tentativas |
| Clopper-Pearson | intervalo exato para proporção binomial |
| falso acionamento | ação do controlador sem falha que a justifique |
| `:sem_forma2` | sensibilidade de V sem a forma 2 de I1 |
| tratamento simétrico | se a varredura falha num braço do bloco, todos os braços do bloco ficam sem a varredura |
| família de secundários | desfechos secundários corrigidos juntos por Holm (11 por efeito na análise oficial) |
| pré-registro, complemento | documento com as regras fixadas antes dos dados; acréscimo posterior, também antes dos dados |
| sem previsão de efeito | nenhum tamanho de efeito foi fixado antes |

## 10.9 Protocolo, campanhas e vereditos

| Termo | Significado |
|---|---|
| nova medição de referência (*rebaseline*) | campanha S1/S2 em `DIST_SYNC` e `DIST_ASYNC`, sem falha, F1 e F3, sem controlador; rodou o piloto e a noite 1 |
| piloto | rodada de verificação antes da campanha, cujos dados não entram em resultado |
| noite 1, noite 2 (2a, 2b) | rodadas da nova medição de referência; só a noite 1 rodou |
| piloto de violações | campanha de 06/11 que escolheu o ponto de operação em que V aparece |
| condições do piloto `c50`, `d`, `b1`, `cd`, `p2-d` | pausa de 50 s; partição com carga L+; ARW com `PREFERRED_ALWAYS`; pausa com L+; segunda passagem de `d` com a sonda (a escolhida) |
| X-01 a X-19 | passos operacionais numerados do plano de execução contínua (por exemplo X-08 ensaio do F1, X-10 piloto, X-12 noite 1, X-17 ensaio do orquestrador, X-18 calibração, X-19 recalibração) |
| etapas S0 a S7 da VM | verificação dos injetores antes do uso sob carga: S0 pacote no hospedeiro; S1 leitura na VM; S2 aceitação das disciplinas de F4 pelo *kernel*; S3 temporizador de limpeza da F4; S4 nova partição em *cluster* novo; S5 primeira injeção real de F4 (20 % por 60 s); S6 recomposição depois de partição com o injetor encerrado; S7 temporizador de segurança da F2 disparando sozinho (`decisoes-senior-2026-10-16.md:375-391`) |
| REP=n | repetição n do teste de partição sem carga |
| ensaio do F1 | teste da queda silenciosa na VM antes do piloto |
| fase núcleo, extra, reposições | partes da avaliação fatorial: F2, sem falha e F3; F1 e F4; blocos refeitos |
| K1 a K10 | critérios de recusa da nova medição de referência (portão de saúde, término do programa, sanidade, mistura, falha como prevista, aviso `ISPN006010`, violações em SYNC) |
| portão (K1) | conferência antes de cada execução: três nós saudáveis há 60 s, sem rebalanceamento, sem `netem` residual |
| camada 3 | conferência da configuração dos *caches* e do rebalanceamento antes e depois da execução |
| h4, h4a, h4b, h4c | descarte por infraestrutura: pressão de E/S total ≥ 1,5 s em 10 s; intervalo entre amostras ≥ 3 s ou salto de relógio; amostras faltando ou arquivo ilegível |
| ACEITA | execução válida |
| RECUSADA | execução que falhou num critério pré-registrado (nova medição de referência) |
| DESCARTADA-INFRA | execução descartada por infraestrutura (h4 ou falha do ambiente); o bloco é refeito |
| PULADA | braço de um bloco já perdido, que não roda |
| PARADA | interrupção da fase por regra de segurança |
| reposição | refazer um bloco perdido, uma vez, com semente nova |
| GO | confirmação digitada no terminal para iniciar a fase ou a primeira falha de rede |
| supervisor, vigia | *script* na VM que acompanha a fase; agente que conferia cada execução |
| `PARAR` | arquivo que manda a fase parar entre execuções |
| MANIFEST, ENTREGA | lista de SHA-256 dos arquivos; documento de entrega dos dados à análise |
| H0, H1, H2 | hospedeiro em silêncio (só a medição), com carga limitada, livre |
| *swap* limite | 1 GiB de *swap* no portão leva a recriar o *cluster* antes da execução |
| `exec_teto_s` | teto de tempo do processo do programa de carga (1019 s) |
| `volume_exec_mb` | espaço em disco exigido por execução (14 MB) |
| semente de ordem | semente que sorteia a ordem das execuções (20261106) |

## 10.10 Gestão do projeto (códigos que não devem ir para o texto)

| Código | Significado |
|---|---|
| B-xx | item do *backlog* técnico. Principais: B-22 falhas no modelo TLA+; B-25 marcadores de falha; B-26 agregador de janelas e sondas (B-26.3 sonda de defasagem); B-27 portão de emissão no cliente (retirado); B-28 núcleo de decisão e laço do controlador; B-29 injetor de partição e teste sem carga (B-29.2 injetor da F4); B-32 propriedades por réplica no TLC; B-33 instrumentação do S3; B-34 confronto do modelo com o real; B-35 variante de perda; B-36 variante de relógio; B-37 coletor de recursos; B-38 reprodução das decisões sobre registros gravados; B-39 análise pareada; B-40 pré-registro da avaliação; B-41 campanha de avaliação; B-42 perfil assíncrono; B-43 correções da carga (B-43.1 a B-43.9); B-44 atuação no *cluster* (B-44.1 o que muda em execução, B-44.2 atuador, B-44.3 exclusão pela vista); B-45 desempate de `ts`; B-46 orquestrador do S3 |
| W-1 a W-10 | pacotes de implementação de 04/11 a 06/11: W-1 retirar o portão de emissão; W-2 atuador do *cluster*; W-3 laço de controle; W-4 sonda de defasagem; W-5 desfechos na análise (W-5c a W-5f: V por pares e fatorial); W-6 orquestrador do S3 (W-6b, W-6c correções); W-7 modelo da janela; W-9 derivação da calibração; W-10 sonda de *replay* |
| DT-001 a DT-028 | decisões técnicas registradas em `docs/decisoes-tecnicas.md`. As que o texto pode citar pelo conteúdo: DT-003 escrita condicional por versão; DT-008 *token* antigo por sorteio; DT-009 pré-registro da nova medição; DT-016 64 segmentos; DT-017 F1 silenciosa; DT-018 critério h4; DT-020 a DT-025 calibração e avaliação de 05/11; DT-026 benchmark refeito; DT-027 fatorial com A1 e A4; DT-028 valores da recalibração |
| P-01 a P-65 | problemas registrados em `docs/registro-de-problemas.md` (por exemplo: P-16 empate de `ts`; P-24 exclusão entre queda e partição; P-25 limite de partições; P-26 relógio não atualizado na entrega; P-27 bloqueio por limite sem cascata; P-30 *cache* do XML impede reinício; P-41 leitura final sem prazo; P-42 parada leva 13 s; P-48 topologia velha na convergência; P-64 volume de log no alvo; P-65 instante da fusão) |
| TD-0xx | itens de dívida técnica e decisões de implementação em `docs/implementacao.md` |
| V-xx | parecer de validação de uma entrega (V-B43.1, V-W6, V-S7 etc.) |
| Q-xx, OP-xx, O-x | perguntas, opções e operações numeradas em planos |
| REF-xxx, CAND-xxx, Decisão 0xx | referência aceita, candidata e decisão do Bibliotecário |
| INT-, FUN-, MET-, DES-, CON-, APX- seguidos de número | marcadores de revisão no LaTeX (`\revini`/`\revfim`) por capítulo: Introdução, Fundamentação, Metodologia, Desenvolvimento, Conclusões, Apêndice; somem na versão final |
| QM1 a QM7, CE, CI, D1 a D8 | questões, critérios de exclusão e inclusão e dimensões do mapeamento sistemático (já no texto) |
| `exec-NNNN-<célula>-<braço>` | pasta de uma execução (por exemplo `exec-0037-ASYNC-F2-AL`) |

## 10.11 Tabelas para a monografia

### T10-01. Lista de abreviaturas e siglas (ATUALIZAR)

**Onde está:** `Main-Monografia.tex:118-144` (`listofabbrv`). Siglas que o texto atual já usa e não estão na lista
(contagem por `grep` nos seis capítulos): API (21 ocorrências), REST (16), TCP (5), CSV (5), JMX (4), YCSB (4), WSL (3),
RPC (2), IC (2), JSON (1), JSONL (1), SSD (1), PCIe (1), ASPM (1), MTU (1). Siglas que o texto novo vai introduzir: E/S
(se usada como sigla), NVMe, PSI, UUID, vCPU, FIFO, FWER. Linhas no formato atual:

```latex
    \item[API]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Application Programming Interface}
    \item[ASPM]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Active State Power Management}
    \item[CSV]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Comma-Separated Values}
    \item[FIFO]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{First In, First Out}
    \item[FWER]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Family-Wise Error Rate}
    \item[IC]       \ \ \ \ \ \ \ \ \ \ \ \ \ Intervalo de confiança
    \item[JMX]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Java Management Extensions}
    \item[JSON]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{JavaScript Object Notation}
    \item[JSONL]    \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{JSON Lines}
    \item[MTU]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Maximum Transmission Unit}
    \item[NVMe]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Non-Volatile Memory Express}
    \item[PCIe]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Peripheral Component Interconnect Express}
    \item[PSI]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Pressure Stall Information}
    \item[REST]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Representational State Transfer}
    \item[RPC]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Remote Procedure Call}
    \item[SSD]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Solid-State Drive}
    \item[TCP]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Transmission Control Protocol}
    \item[UUID]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Universally Unique Identifier}
    \item[vCPU]     \ \ \ \ \ \ \ \ \ \ \ \ \ Processador virtual
    \item[WSL]      \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Windows Subsystem for Linux}
    \item[YCSB]     \ \ \ \ \ \ \ \ \ \ \ \ \ \emph{Yahoo! Cloud Serving Benchmark}
```

A lista atual está em ordem alfabética; inserir cada linha na posição correspondente. MAPE-K usa `\ \\` em vez dos
espaços; conferir o alinhamento das siglas longas (FWER, JSONL, NVMe, PCIe) no PDF.

### T10-02. Notação da avaliação (NOVA)

**Onde entra:** Metodologia, no início da seção "Desenho da avaliação do controlador" (`Metodologia.tex:344`), para que
os códigos F1 a F4, PO a AL, V e L fiquem definidos num só lugar antes das tabelas do capítulo de resultados.

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Notação usada na avaliação do controlador}
\label{qd-notacao-avaliacao}
\centering
\footnotesize
\begin{tabular}{p{2.6cm}p{11.2cm}}
\hline
\textbf{Símbolo} & \textbf{Significado} \\
\hline
F1, F2, F3, F4 & queda silenciosa de nó, partição de rede, atraso com \emph{jitter} e degradação parcial da rede \\
S3 & cenário de ciclo de vida de sessões, com servidores de aplicação ligados a nós, usuários e administrador \\
A1 & regra de fusão de partições própria, que mantém a cópia máxima numa ordem de conteúdo em que a sessão invalidada vence a válida \\
P0, P1 & políticas do controlador: não agir; isolar o nó suspeito, aqui pela ação de fazê-lo ignorar o \emph{cache} de sessões \\
PO, PL, AO, AL & braços da partição: regra de fusão atual (P) ou A1 (A), com o controlador observando (O) ou ativo (L) \\
$V$ & taxa de violações: pares distintos (chave, invariante) com detecção de I1, I4 ou I2 real, por mil ações S3 \\
$L$ & p99 da latência da ação S3, com as esperas entre retentativas \\
$d_b$ & contraste do bloco $b$ entre braços, em diferença para $V$ e em logaritmo da razão para $L$ \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

## 10.12 Já está na monografia × novo ou mudou

| Tema | Situação no texto | O que fazer |
|---|---|---|
| Siglas | lista em `Main-Monografia.tex:118-144` sem API, REST, TCP, CSV, JMX, YCSB, WSL, RPC, IC, JSON, SSD, PCIe, ASPM, MTU, já usadas | Tabela T10-01 |
| Códigos internos no texto | `Desenvolvimento.tex` e `Metodologia.tex` usam P0 a P2, R1 a R4, F1 a F4, N1 a N5, S1 a S3, C1 a C3, T1 a T22, h4, O1 a O7, I1 a I6, todos definidos no próprio texto | manter; acrescentar PO a AL, A1, AC-7 (se citado), $V$ e $L$ com o Quadro T10-02 |
| Códigos de gestão | não aparecem no texto, salvo "Decisão 011" (`Metodologia.tex:163`) | conferir se "Decisão 011, ponto 11" precisa ficar; é código interno do projeto |
| `ISPN006010` | citado e explicado (`Metodologia.tex:298`) | manter |
| Termos novos do método (sonda de *replay*, varredura final, forma 1 e forma 2, perda permanente, exposição na janela) | ausentes | definir na Metodologia (capítulo 6 deste relatório) e usar os nomes deste glossário |

## 10.13 Fontes bibliográficas necessárias

O glossário não faz afirmações novas; as fontes de cada conceito estão nos capítulos 1 a 3 (seções de fontes). Para a
lista de siglas, nenhuma citação é necessária. Se o Escritor quiser definir PSI e cgroup no texto, as chaves são
`linux_psi_6_8` e `linux_cgroup_v2`; para FWER e Holm, `holm_sequential_1979`; para interseção-união, `berger_iut_1982`.

## 10.14 Pendências e o que não pôde ser verificado

- **Significados de B-30, B-31 e B-38** foram tirados de menções em planos (`plano-implementacao-controlador-2026-10-22.md`,
  `plano-fechamento-etapa-formal-2026-10-15.md`); não há uma lista única de *backlog* versionada com todos os itens. B-30 e
  B-31 ficaram de fora por não ter achado a definição.
- **P-28 a P-35** não estão em `registro-de-problemas.md` (há um salto de P-27 para P-36); a P-28 está em
  `plano-implementacao-controlador`. Não listei esses números.
- **TD-0xx:** não listei um a um; o arquivo `docs/implementacao.md` da tag tem a lista.
- **W-7 e W-8:** W-7 aparece no plano de 04/11 como modelo da janela; W-8 não foi encontrado.
- **Significados das mensagens `ISPN`:** os significados vêm dos documentos do projeto que os citam, não do catálogo oficial de
  mensagens do Infinispan.
