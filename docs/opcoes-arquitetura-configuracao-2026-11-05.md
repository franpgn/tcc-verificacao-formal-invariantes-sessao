# Opções de configuração e arquitetura além do AC-5 e do AC-2 (2026-11-05)

Autor: Senior. Branch `docs/plano-implementacao-controlador`. Complementa
`docs/acoes-controlador-cluster-2026-11-05.md` (*commit* `cc08644`), que fica valendo no que não for dito aqui.
**Leitura apenas.** Nada foi executado em *cluster*: o piloto do *rebaseline* estava rodando na VM com o host em
silêncio estrito. Os achados vêm do código-fonte do Infinispan 15.0.21.Final (*tag*, *commit* `5bdc4de`) e do
JGroups 5.3.16.Final, a versão que essa *tag* declara (`build/configuration/pom.xml:137`, *tag*
`jgroups-5.3.16.Final`, *commit* `d87c9cf`), mais os guias 15.0 já baixados (`leituras/CAND-090`, `CAND-091`) e o
manual do JGroups no próprio repositório (`doc/manual/`). Os caminhos do Infinispan são relativos à raiz do repositório dele.
Os do JGroups são relativos a `src/org/jgroups/`. **Tudo depende da validação do Gestor (seção 7).**

| Termo | Significado |
|---|---|
| REST | API HTTP de administração do Infinispan (`/rest/v2/...`) |
| Hot Rod | protocolo binário do programa de carga com os *caches* |
| SYNC / ASYNC | replicação síncrona / assíncrona entre as réplicas de uma chave |
| JGroups | biblioteca de comunicação de grupo; decide a "vista" (quem é membro) |
| FD_ALL3 | detector de falhas por batimentos do JGroups (padrão: 40 s sem batimento) |
| VERIFY_SUSPECT2 | protocolo que confirma uma suspeita antes de repassá-la (1 s na nossa pilha) |
| MERGE3 | protocolo que junta de novo as vistas depois que a rede volta (fusão) |
| GMS | protocolo que instala as vistas; o coordenador é o primeiro membro da vista |
| JMX | interface Java de gerenciamento remoto (atributos e operações de objetos em execução) |
| *probe* | canal de diagnóstico do JGroups: lê e grava atributos e chama operações dos protocolos por texto via TCP ou UDP |
| `diag.enabled` | atributo estático da camada TCP do JGroups que liga o canal do *probe* |
| DEGRADED | modo de disponibilidade em que o *cache* recusa operações em chaves sem todas as réplicas na partição |
| M1..M5 | mecanismos de violação (plano de 04/11 §4.3; catálogo §4) |
| I1..I6 | invariantes de sessão (catálogo) |
| S_lag | sensor de defasagem de replicação do controlador (W-4) |
| T_det | tempo entre o corte da rede e a vista nova (39,8 a 46,4 s na VM, `V-S7r.md:45`) |
| k·W | atraso de decisão do controlador (persistência em k janelas de duração W) |

## 0. Resposta curta

1. **O conjunto de ações muda.** A leitura achou uma ação de servidor que AC-5 e AC-2 não cobrem. É a
   **AC-6, exclusão forçada do nó suspeito pelo JGroups**: a operação `GMS.suspect`, chamada pelo canal de
   diagnóstico (*probe*) nos dois lados do corte. Na maioria, ela tira o nó suspeito da vista na hora. No nó
   suspeito, ela tira os outros dois, e ele fica sozinho e em DEGRADED. Com `owners=2` e `DENY_READ_WRITES`,
   ele passa a recusar toda chave. A AC-6 ataca **as duas metades do M1**, e o AC-5 só ataca uma: a escrita
   aceita no nó isolado e, em ASYNC, a escrita feita na maioria para chave cujo dono primário está do lado
   cortado. A ação se desfaz sozinha pela fusão quando a rede volta, e um falso positivo vira uma fusão
   automática em 10 a 30 s, não um nó morto até o fim da execução. **Custo:** é preciso ligar o
   `diag.enabled` (configuração estática, antes da calibração) e publicar a porta de diagnóstico. **Proposta:**
   a AC-6 entra no lugar do AC-5 como ação de P1 se passar na verificação empírica (seção 8). O AC-5 fica
   de reserva.
2. **Mudar o detector de falhas em execução não funciona.** Os atributos do FD_ALL3 são graváveis pelo
   JMX e pelo *probe*, mas a mudança não vale para os membros que já existem. O mapa de batimentos de cada
   membro tem o tamanho fixado na criação, e a tarefa de verificação mantém o intervalo com que foi agendada
   (seção 2.3). Isso fecha a pergunta do FD_ALL3 adaptativo que o documento de `cc08644` deixou como trabalho
   futuro. A resposta é negativa, e agora tem fonte no código.
3. **A política de replicação continua sem troca em execução pela configuração.** Remover e recriar o
   `sessions` apaga os dados, inclusive os do armazenamento persistente (a remoção liga a limpeza do
   armazenamento). Os clientes recebem "*cache* não encontrado" no intervalo e depois um *cache* vazio, o que
   viola I2, I5 e I6 por construção. O caminho por cópia de segurança e restauração preserva os dados, mas
   exige parar o Hot Rod no *cluster* inteiro e o *cluster* saudável. Serve para troca planejada, não para
   reação. **Escolher entre dois *caches* no servidor não existe na 15.0.21:** o Hot Rod resolve o *cache*
   só pelo nome, os apelidos não entram nessa busca, e as tarefas de servidor só rodam quando alguém as chama.
4. **Existe um caminho de servidor para trocar a política sem mudar o cliente, mas ele exige implantar
   código no servidor.** O Infinispan honra, por operação, o indicador `FORCE_SYNCHRONOUS` num *cache*
   ASYNC. No modo servidor, SYNC e ASYNC sem transação usam o mesmo interceptador de distribuição e só
   diferem por esse teste. Uma extensão carregada na partida (um JAR em `server/lib`) pode inserir um
   interceptador que liga o indicador quando o controlador mandar. É a única opção encontrada que alcança
   **M2 e M3**. Fica como **exploratória E-1**, com decisão do Gestor, porque muda o sistema avaliado (passa
   a ser "Infinispan mais extensão") e usa uma interface marcada como experimental.
5. **O restante fica fora**, com o motivo na seção 5: tarefas de servidor, replicação entre *sites*,
   `when-split` recriado (já está `DENY_READ_WRITES`), transação e trava, quórum (não existe atributo),
   gerenciador de conflitos pela REST (não existe *endpoint*), ajustes de VERIFY_SUSPECT2, MERGE3 e
   `view_ack_collection_timeout`, e rebalanceamento por *cache* (já descartado pelo P-34).

## 1. O que limita a detecção hoje (por que 40 a 48 s)

A pilha da VM e do local é `cluster/jgroups-tcp.xml`: `MERGE3 min_interval=10000 max_interval=30000`, `FD_SOCK2`,
`FD_ALL3` sem atributos (padrão), `VERIFY_SUSPECT2 timeout=1000`, `GMS` sem `view_ack_collection_timeout`
(padrão) e `diag.enabled=false`.

| Peça | Valor | Fonte |
|---|---|---|
| FD_ALL3 `timeout` / `interval` | 40 000 / 8 000 ms | `protocols/FailureDetection.java:27-32` |
| Mapa de batimentos | `timeout/interval` bits por membro (5); suspeita quando todos zeram; a verificação avança 1 bit por `interval` | `protocols/FD_ALL3.java:14-17`, `:82-84`, `:86-96`, `:111-141` |
| Quem levanta a suspeita | só o primeiro membro elegível (o coordenador ou o próximo) | `protocols/FailureDetection.java:217-237` |
| VERIFY_SUSPECT2 | 1 000 ms | `protocols/VERIFY_SUSPECT2.java:34-36`; XML |
| GMS `view_ack_collection_timeout` | 2 000 ms (espera de confirmações da vista nova) | `protocols/pbcast/GMS.java:93-95` |
| FD_SOCK2 | detecta o fechamento da conexão; com o corte por `tc`, o pacote de fechamento não chega | `scripts/inject-partition.sh:12-19` (N-11) |

Sob o corte por `tc`, quem detecta é o FD_ALL3. A conta 40 s + até 8 s de granularidade da verificação + 1 s
de confirmação + instalação da vista bate com os 39,8 a 46,4 s medidos na VM. É a janela do M1.

## 2. Detecção de falhas do JGroups em tempo de execução

### 2.1 Por onde se chega aos protocolos do JGroups

| Meio | Existe no 15.0.21? | Estado na nossa configuração | Fonte |
|---|---|---|---|
| REST do Infinispan | **não**: nenhum recurso REST expõe atributo ou operação de protocolo do JGroups; a REST só mostra membros e endereços | — | `server/rest/.../resources/ServerResource.java:99-194`, `ClusterResource.java:54-71`, `ContainerResource.java:105-135` (lista de rotas) |
| CLI do Infinispan | não (fala com a REST) | — | `leituras/CAND-092` |
| JMX | sim: o Infinispan registra o canal e os protocolos do JGroups como MBeans **se** o JMX do *cache container* estiver ligado | desligado: `jmx enabled` tem padrão `false` e o XML não o liga; o acesso remoto ainda exige `server.sh --jmx` (RMI, porta 9999) | `core/.../transport/jgroups/JGroupsTransport.java:601-613`; `core/.../configuration/global/GlobalJmxConfiguration.java:22`; `server/runtime/src/main/server/bin/common.sh:10-15`, `:154-160` |
| *probe* do JGroups | sim: `jmx=PROT.attr=valor` grava atributo; `op=PROT.metodo[args]` chama qualquer método por reflexão; o Infinispan traz `bin/probe.sh` | desligado: `diag.enabled=false` em `cluster/jgroups-tcp.xml`; ligar é estático (o `TP.enableDiagnostics` é uma operação, e só se chama pelo próprio canal) | `JChannelProbeHandler.java:32-58`, `:245-284`, `:314-337`; `JChannel.java:71`, `:941`; `stack/DiagnosticsHandler.java:28-58`; `protocols/TP.java:866-875`; `server/runtime/src/main/server/bin/probe.sh`; `doc/manual/advanced.adoc:2018-2230` (seção *Probe*, `jgroups_manual_5`) |

O pedido do *probe* por TCP é uma linha de texto terminada em `\n` (`util/Util.java:4066-4080`, lido em
`stack/DiagnosticsHandler.java:226-233`). O controlador pode falar esse protocolo com um `Socket` Java, em
memória, sem criar processo, o que respeita o P-5. A porta padrão é 7500 (`DiagnosticsHandler.java:41-42`). Se
`diag.passcode` for definido, cada pedido leva um resumo criptográfico com instante e número aleatório
(`DiagnosticsHandler.java:57-58`, `:360-373`). Sem *passcode*, qualquer um que alcance a porta pode chamar
qualquer operação. Isso precisa ficar declarado como risco de laboratório.

### 2.2 Atributos graváveis

No JGroups, todo campo `@Property` é gravável por padrão (`annotations/Property.java:68`, `writable() default
true`). Um `@ManagedAttribute` só é gravável se declarar `writable=true` (`annotations/ManagedAttribute.java:29`).
O JMX respeita essa regra (`jmx/ResourceDMBean.java:357-364`, `:484-505`). O *probe* grava o **campo** por
reflexão quando ele existe, sem chamar o *setter* (`jmx/ReflectUtils.java:210-227`), e só cai no *setter* se não
achar o campo (`:230-241`).

| Atributo | Gravável? | Efeito de gravar com o *cluster* rodando | Fonte |
|---|---|---|---|
| FD_ALL3 `timeout` | sim (`@Property`) | **nenhum para os membros atuais.** O *setter* recalcula `num_bits` (`FD_ALL3.java:56-60`), mas o mapa de cada membro foi criado com o tamanho antigo e não muda (`:68-77`, `:115-119`). Na troca de vista, os membros que continuam mantêm o mapa (`FailureDetection.java:77`, `:197-205`). Pelo *probe*, nem o `num_bits` muda, porque o campo é gravado direto (`ReflectUtils.java:214-222`). Só um membro que entrar depois (por exemplo, depois de uma fusão) ganha o tamanho novo | idem |
| FD_ALL3 `interval` | sim | **nenhum até reagendar.** Remetente de batimentos e verificação foram agendados com o valor antigo (`FailureDetection.java:262-300`) e só são recriados se pararem. `stopFailureDetection`/`startFailureDetection` (`:107-115`) reagendam só a verificação, não o remetente | idem |
| VERIFY_SUSPECT2 `timeout` | sim | já é 1 s; baixar ganha no máximo 1 s dos 40 a 48 s | `VERIFY_SUSPECT2.java:34-36` |
| MERGE3 `min_interval`, `max_interval`, `check_interval` | sim | mudam o tempo até a **fusão** depois da cura, não a janela do M1. Encurtar a fusão aumenta a chance de fusão com o AC-2 ativo (N-5 de `cc08644`) | `MERGE3.java:46-58` |
| GMS `view_ack_collection_timeout` | sim | limita a espera por confirmações da vista nova (até 2 s); não muda quando a suspeita nasce | `pbcast/GMS.java:93-95`, `:180-181` |

**Conclusão do item 1 do pedido, parte "mudar atributos":** dá para gravar, mas **o efeito sobre o tempo de
detecção dos membros atuais é nulo**. Uma combinação que reagende a verificação com intervalo menor e mantenha
o mapa antigo encurtaria a suspeita. Ela depende de detalhe de implementação, deixa o remetente de batimentos
no ritmo antigo e cria suspeitas falsas de nós sãos. Fica **fora**.

**Mudar o detector de forma estática** (por exemplo, `FD_ALL3 timeout=10000` no XML) é configuração, não ação
do controlador. Encurta a janela do M1 para todos os braços, muda a linha de base já medida e, na VM, onde o
disco trava por segundos e leva a JVM junto (`V-S7r.md:61`), cria suspeitas falsas e fusões. É a troca entre
completude e acurácia de `chandra_toueg_failure_detectors_1996`. Fica **fora da campanha** e pode virar análise
de sensibilidade em trabalho futuro.

### 2.3 Forçar suspeita ou exclusão

| Operação | Existe? | O que faz | Fonte |
|---|---|---|---|
| `GMS.suspect(nome)` | **sim**, `@ManagedOperation` | procura o endereço pelo nome lógico e entrega um evento de suspeita **ao próprio GMS**. Como o GMS fica acima do VERIFY_SUSPECT2 na pilha, a suspeita **não é verificada**. No coordenador, gera a vista nova sem o suspeito na hora. Num participante, acumula a suspeita e, se ele passar a ser o próximo coordenador, assume e instala a vista | `pbcast/GMS.java:278-290`, `:816-828`; `pbcast/CoordGmsImpl.java:63-71`, `:88-161`; `pbcast/ParticipantGmsImpl.java:64-108` |
| `FD_ALL3.suspect` | não exposta (o `suspect(List)` é `protected`) | — | `FailureDetection.java:217` |
| MERGE3 "forçar fusão" | não; há `sendInfo` e `checkInconsistencies`, que só antecipam a próxima rodada | — | `MERGE3.java:125-131` |
| `INJECT_VIEW.injectView` | só se o protocolo `INJECT_VIEW` estiver na pilha (estático); instala vistas arbitrárias, feito para testes | — | `doc/manual/protocols.adoc:2384-2403` |
| `GMS.suspendViewHandler` / `resumeViewHandler` | sim | congela a instalação de vistas; não ajuda no M1 e é perigoso | `pbcast/GMS.java:267-268` |

O nome lógico de cada nó é `<hostname>-<número>` (por exemplo, `isn3-6672`), lido de `cluster_members` em
`GET /rest/v2/container` (`wt-b44-1/runs/b44-1/corpos/cm-isn1.txt`). O controlador já lê essa rota.

### 2.4 AC-6: exclusão forçada do nó suspeito pelos dois lados (nova, **entra condicionada**)

```mermaid
%%{init: {'theme':'base','themeVariables':{'background':'#ffffff','primaryColor':'#ffffff','primaryTextColor':'#000000','primaryBorderColor':'#000000','lineColor':'#000000','secondaryColor':'#ffffff','tertiaryColor':'#ffffff','fontFamily':'Arial'}}}%%
sequenceDiagram
    participant C as Controlador
    participant A as isn1 (maioria)
    participant B as isn2 (maioria)
    participant S as isn3 (suspeito, cortado)
    Note over A,S: corte em t0; FD_ALL3 só suspeitaria em t0 + 40..48 s
    C->>A: probe op=GMS.suspect[isn3-x]
    C->>B: probe op=GMS.suspect[isn3-x]
    Note over A,B: vista {isn1,isn2}; os caches seguem AVAILABLE e rebalanceiam
    C->>S: probe op=GMS.suspect[isn1-y]
    C->>S: probe op=GMS.suspect[isn2-z]
    Note over S: vista {isn3}; minoria, DEGRADED; recusa toda chave (owners=2)
    Note over A,S: cura: MERGE3 junta as vistas; a fusão resolve conflitos pela merge-policy
```

- **Chamadas.** No modo de diagnóstico TCP, `op=GMS.suspect[<nome do suspeito>]` para os dois nós da maioria (só
  o que for coordenador, ou o próximo, age; o outro registra um aviso e descarta, `ParticipantGmsImpl.java:90-106`)
  e `op=GMS.suspect[<nome de cada um dos outros>]` para o suspeito, duas chamadas. A suspeita acumula
  (`ParticipantGmsImpl.java:78-84`), e na segunda ele vira coordenador de si mesmo.
- **Efeito no Infinispan, pelo código.** Na maioria, um membro perdido de três não é minoria
  (`perdidos >= ceil(3/2)` é falso, `partitionhandling/impl/PreferConsistencyStrategy.java:157-159`). Com
  `owners=2`, nenhum segmento perde todas as cópias, então os *caches* ficam AVAILABLE e rebalanceiam
  (`:118-150`). No nó isolado, dois perdidos de três é minoria, e ele vai a DEGRADED (`:146-148`). Com
  `DENY_READ_WRITES`, ele só aceita a chave que tiver todas as réplicas na partição, e nenhuma tem
  (`documentation/.../con_partition_handling_degraded_mode.adoc`, `ref_partition_handling_strategies.adoc`).
- **Mecanismo.** M1 inteiro, para as operações depois da ação. (a) Lado isolado: as escritas passam a ser
  recusadas com erro de disponibilidade, como no AC-5. (b) Maioria: a escrita para chave cujo dono primário era
  o nó isolado passa a ir para o novo dono, em vez de se perder (ASYNC) ou esperar até o `remote-timeout`
  (SYNC). O AC-5 não alcança (b). Parar o processo do nó isolado não avisa a maioria, porque a saída
  pelo JGroups não atravessa o corte e o FD_SOCK2 também não vê o fechamento. A maioria continua esperando o
  FD_ALL3, como já diz `cc08644` §3.1 ("do lado da maioria, nada muda até a detecção").
- **Efeito esperado em ASYNC.** Fração da janela coberta ≈ `1 − (k·W + t_probe + t_vista) / T_det`, com
  `t_vista` limitado por `view_ack_collection_timeout` (2 s) na maioria e imediato no nó sozinho. Comparado ao AC-5,
  não há o atraso de 3 s da saída do servidor e o efeito vale nos dois lados.
- **Efeito esperado em SYNC.** Maior que o do AC-5. Na maioria, as escritas que esperavam a cópia no nó cortado
  até o cliente desistir em 2 s (N-6) passam a terminar com o novo dono. No nó isolado, a recusa é
  imediata.
- **Falso positivo.** Se não houver corte, o resultado é uma divisão limpa: maioria AVAILABLE, nó sozinho
  DEGRADED recusando tudo. Como a rede funciona, o MERGE3 percebe as vistas diferentes e funde em 10 a 30 s
  (`min_interval`/`max_interval` do XML). A fusão roda a resolução de conflitos com a partição disponível como
  preferida (`PreferConsistencyStrategy.java:223-226`, `:251-270`). O nó sozinho não aceitou escrita depois da
  ação, então os conflitos possíveis são os de antes dela, os mesmos de uma fusão natural. Mesmo assim, uma
  fusão é onde nascem o M5 e o P-23. Por isso o falso positivo **cria** exposição ao M5 e custa vazão (o
  servidor de aplicação fixado no nó recebe recusas até a fusão). É menos grave que o falso positivo do AC-5,
  que deixa o nó morto até o fim da execução. Literatura: suspeitar com sinal mais lento e mais exigente (S_lag
  no teto em k janelas) é o degrau alto da escala de `hayashibara_phi_accrual_2004`. O preço de suspeitar cedo
  é a acurácia (`chandra_toueg_failure_detectors_1996`): cada suspeita falsa vira uma divisão.
- **Risco específico.** (1) Ação de um lado só. Se o pedido chegar à maioria e não ao isolado, o nó isolado
  continua aceitando até o próprio FD_ALL3, sem ninguém para replicar. É o M1 de antes, sem piora. Se chegar
  ao isolado e não à maioria, ele recusa e a maioria espera o FD_ALL3. Também sem piora em relação a nada
  fazer. O atuador manda os quatro pedidos em paralelo e registra quais responderam. (2) O nó suspeito é o
  coordenador: a operação nos dois da maioria faz o próximo assumir (`ParticipantGmsImpl.java:90-100`). Tem
  de ser verificado (V-4). (3) Mudança na mesma janela de um AC-2 ativo: não acontece, porque a AC-6 exige a
  vista completa e o AC-2 exige um nó sem resposta. Depois da AC-6, o nó sozinho responde à REST com vista de 1, e
  isso é "vistas divergentes" na tabela de `cc08644` §5, o que já proíbe e reverte o AC-2. (4) A porta de
  diagnóstico aberta sem *passcode* aceita qualquer operação.
- **Reversão.** Automática, pela fusão do MERGE3 quando a rede volta. Não entra na pilha LIFO do controlador e
  não há chamada de reversão. Não há o que o orquestrador religar, e o `podman start` do AC-5 sai do caminho.
  Se a fusão não vier depois da cura (não esperado), o orquestrador recria o *cluster* como hoje.
- **Cota e guarda.** 1 por execução; só com vista completa nos três nós em todas as amostras da janela e S_lag no
  teto com o mesmo `noSuspeito` em k janelas (a mesma assinatura do AC-5); o suspeito precisa ter respondido à REST
  na janela.
- **Modelo TLA+.** No nível 1, `Particionar` corta e detecta no mesmo passo, e a AC-6 **é** um `Particionar`
  escolhido pelo controlador. Logo, ela não cria comportamento novo, e um falso positivo é uma partição que
  o modelo já cobre, seguida do `Mesclar` de sempre. Não precisa de variável nova, ao contrário do `parado[r]`
  do AC-5. No nível 2 (W-7, `Cortar`/`Detectar`), a AC-6 é um `Detectar` antecipado. Os traços que nascem
  depois dela deixam de ser alcançáveis, e os de antes continuam. **Lacuna a declarar:** no real, as
  mensagens já em trânsito na hora da exclusão ficam pelo caminho. No modelo, depende de como o canal é
  esvaziado no `Particionar`.
- **Configuração necessária (estática, pré-registrar).** `diag.enabled=true`, `diag.enable_tcp=true`,
  `diag.enable_udp=false`, `diag.port` fixo e `diag.port_range=0` na camada TCP de `cluster/jgroups-tcp.xml`.
  A porta é publicada por nó no `podman-compose.yml` (por exemplo 7500, 7501 e 7502 no host), com `diag.passcode`
  opcional. O comportamento do *cluster* sem ação não deve mudar, e o V-1 confere isso.
- **Viabilidade numérica.** `k·W + t_probe + t_vista < 39,8 s` (menor T_det da VM). Medir `t_probe` e `t_vista` no V-3.
- **Medir.** Seção 8, V-1 a V-6.

### 2.5 Tabela do item 1

| Opção | Viável no servidor sem cliente? | Mecanismo | Custo | Reversão | Medir | Classe |
|---|---|---|---|---|---|---|
| AC-6 `GMS.suspect` pelos dois lados, via *probe* | sim, com `diag` ligado na configuração | M1 (as duas metades) | `diag` estático + porta; fusão na cura; vazão no nó excluído | automática pela fusão | V-1..V-6 | **entra, condicionada** |
| FD_ALL3 `timeout`/`interval` em execução | grava, mas sem efeito nos membros atuais | (M1) | — | — | V-2 (só confirmar o nulo) | **fora** (sem efeito, pelo código) |
| FD_ALL3 mais curto no XML | configuração, não ação | M1 em todos os braços | muda a linha de base; suspeitas falsas na VM | trocar o XML | — | **fora da campanha** |
| VERIFY_SUSPECT2 `timeout` | sim | M1, no máximo 1 s | — | regravar | — | **fora** (ganho ≤ 1 s) |
| MERGE3 intervalos, `sendInfo`, `checkInconsistencies` | sim (com `diag`) | nenhum (fusão, não janela) | aumenta o risco N-5 do AC-2 | regravar | — | **fora** |
| GMS `view_ack_collection_timeout` | sim | nenhum (≤ 2 s na instalação) | — | regravar | — | **fora** |
| `INJECT_VIEW` | exige o protocolo na pilha; ferramenta de teste | M1 | vistas arbitrárias, mais arriscado que `GMS.suspect` | fusão | — | **fora** (a AC-6 cobre com menos risco) |
| JMX remoto | exige `jmx enabled` + `--jmx` (RMI) | o mesmo da AC-6 | RMI em contêiner, porta extra, autenticação JAAS | — | — | **fora** (o *probe* faz o mesmo com menos peças) |

## 3. Troca da política de replicação

### 3.1 Remover e recriar `sessions` com outro modo

- **Os dados não são preservados.** A remoção pela REST chega a `removeCacheSync`, que liga a limpeza do
  armazenamento persistente na parada (`core/.../globalstate/impl/VolatileLocalConfigurationStorage.java:131-149`,
  `setClearOnStop(true)` na linha 136; a limpeza em `core/.../persistence/manager/PersistenceManagerImpl.java:421`).
  Um *file store* ou *soft-index store* com `preload` volta **vazio**. A memória de cada nó também se vai, porque o *cache* é
  parado em todos (`GlobalConfigurationManagerImpl.java:342-353`).
- **Clientes Hot Rod durante a troca.** Entre o `DELETE` (24 a 37 ms, B-44.1b) e o fim do `POST` (45 ms a 8 s), o
  servidor responde `CacheNotFoundException` ("*Cache with name 'sessions' not found*",
  `server/hotrod/.../HotRodServer.java:398-414`, `:429-432`). A entrada em memória do servidor é limpa quando o *cache* para
  (`:694-697`). Depois, o cliente encontra um *cache* vazio pelo mesmo nome.
- **Invariantes.** Violação por construção: o `Block` confirmado desaparece (I2) e os contadores recomeçam
  (I5, I6). Um `Logout` confirmado some, e a sessão passa a não existir. Se isso conta como I1 depende do
  auditor, mas o estado confirmado foi perdido de qualquer forma.
- **Classe: fora.**

### 3.2 Cópia de segurança, recriação e restauração

- **Existe pela REST:** `POST /rest/v2/cluster/backups/{nome}` e `POST /rest/v2/cluster/restores/{nome}`
  (`server/rest/.../ClusterResource.java:63-71`; `BackupManagerResource.java:53-55`, `:161-193`). A restauração usa
  `getOrCreateCache` (`server/core/.../backup/resources/CacheResource.java:141-158`). Se o *cache* já tiver sido
  recriado com outro modo, os dados entram nele e a configuração do arquivo é ignorada.
- **Por que não serve ao controlador.** (1) Escritas entre a cópia e a recriação se perdem, e escritas
  depois da recriação podem ser sobrescritas por valores mais velhos da cópia: um `Logout` vira `VALID` (I1).
  Isso só se evita parando o conector Hot Rod no *cluster* inteiro durante a troca (estado global, N-3 de
  `cc08644`), uma parada total de segundos. (2) Criar *cache*, copiar e restaurar exigem o *cluster* inteiro
  saudável. Sob partição, não funciona. A troca só pode ser **antecipada**, nunca **reativa**, e na campanha a
  falha é injetada sem aviso. (3) A cópia vai para o disco do servidor, que trava na VM (P-5).
- **Classe: fora** para o controlador. Pode ser citada no texto como o único caminho de servidor para trocar o
  modo **preservando dados**, desde que planejado e com parada total.

### 3.3 Dois *caches* sempre criados e o servidor escolhendo

| Mecanismo pedido | Existe na 15.0.21? | Fonte |
|---|---|---|
| Roteamento por nome no Hot Rod | não. O servidor procura o *cache* pelo nome pedido e exige que ele esteja em `getCacheNames()`. Apelidos não entram, como o B-44.1b mediu | `HotRodServer.java:398-433`; `runs/b44-1b/efeito/A2-sonda-hotrod.txt` |
| Interceptador declarado na configuração | não: `custom-interceptors` é lido e **ignorado** | `core/.../configuration/parsing/CacheParser.java:372-375` |
| `ServerTask` | roda só quando alguém a chama (REST `?action=exec` ou Hot Rod `execute`); não intercepta as operações da carga. Exige JAR em `server/lib` com o servidor parado ("*does not support runtime deployment of custom classes*") | `server/rest/.../TasksResource.java:47-50`; `leituras/CAND-090` §14.1 |
| *Script* pela REST | carregado em execução (`PUT /rest/v2/tasks/{nome}`), mas também só roda quando chamado; o motor JavaScript não foi confirmado na imagem (o Nashorn aparece só com escopo de teste no módulo de *scripts*) | `TasksResource.java:48`; `leituras/CAND-090` §14.2; `tasks/scripting/pom.xml` |
| Interceptador que copie para o outro *cache* | só com extensão (seção 3.4); escrita dupla sem atomicidade, a mesma objeção da seção 2 de `cc08644` | — |

**Resposta honesta:** a escolha entre dois *caches* sem mudar o cliente **não existe** no servidor 15.0.21
pela configuração. Com código implantado, a forma menos invasiva não é escolher entre dois *caches*. É mudar,
por operação, o modo do próprio *cache* (3.4).

### 3.4 E-1: extensão no servidor que força SYNC por operação (nova, **exploratória**)

- **Base no código.** O indicador `FORCE_SYNCHRONOUS` "força chamadas de rede síncronas mesmo se configurado
  para assíncronas" (`core/.../context/Flag.java:59-68`). O teste está em `BaseRpcInterceptor.isSynchronous`
  (`interceptors/impl/BaseRpcInterceptor.java:35-46`). No modo servidor, `DIST_SYNC` (sem versão) e
  `DIST_ASYNC` sem transação usam **o mesmo** `NonTxDistributionInterceptor`
  (`factories/InterceptorChainFactory.java:225-241`; `configuration/cache/Configurations.java:42-45`), que decide
  entre esperar a cópia ou só mandar por esse teste (`interceptors/distribution/NonTxDistributionInterceptor.java:495-518`;
  repasse ao primário em `BaseDistributionInterceptor.java:689`). Com o indicador, a escrita também volta a
  aceitar nova tentativa por mudança de topologia, como no SYNC (`EntryWrappingInterceptor.java:614-621`). O lado
  que recebe o comando também lê o indicador (`remoting/inboundhandler/TrianglePerCacheInboundInvocationHandler.java:229-230`).
- **Como seria.** Um JAR em `server/lib` com um `ModuleMetadataBuilder` registrado por `ServiceLoader`
  (`core/.../manager/ModuleRepository.java:74`; interface em `factories/impl/ModuleMetadataBuilder.java:26-34`), cujo
  `ModuleLifecycle.cacheStarting` (`lifecycle/ModuleLifecycle.java:32`) insere, no *cache* `sessions-async`
  (e `counters-async`), um interceptador antes do `EntryWrappingInterceptor`
  (`interceptors/AsyncInterceptorChain.java:56-64`). Ele chama `command.addFlags(FORCE_SYNCHRONOUS)` quando uma
  chave de controle estiver ligada. A chave fica num *cache* **local** de cada nó, gravado pelo controlador
  pela REST em cada nó, para funcionar também sob partição.
- **Mecanismo.** É a única opção que alcança **M2** (leitura defasada em ASYNC: com SYNC forçado, a escrita só
  volta depois da cópia) e **M3** (atualização perdida em ASYNC). Alcança também o M1 em ASYNC, porque a
  escrita sob corte passa de "confirmada e perdida" para "indeterminada ou falha", como no SYNC. É uma seleção
  de política de replicação no sentido forte do objetivo (4): ASYNC no normal e SYNC sob suspeita.
- **Por que só exploratória.** (1) Muda o sistema avaliado: passa a ser Infinispan mais extensão, e a linha
  de base ASYNC precisa ser refeita com a extensão instalada e desligada (ou provar a equivalência). (2) A
  interface de cadeia de interceptadores é marcada `@Experimental` ("*may be removed in future versions*",
  `AsyncInterceptorChain.java:12-19`). O Infinispan tirou os interceptadores da configuração declarativa, o que
  mostra que não quer esse uso. (3) Exige compilação (Maven) e mais um item na imagem da VM. (4) Precisa de
  argumento novo no TLA+ (escrita síncrona por operação no modelo ASYNC). (5) Esforço estimado de 1 a 2 dias de
  Worker só para a prova de conceito, antes de qualquer campanha. (6) O SYNC forçado sob corte produz
  escrita indeterminada (P-31), que já existe no braço SYNC e precisa de leitura própria no auditor.
- **Custo e reversão.** Desligar a chave de controle (uma gravação por nó). Latência do SYNC enquanto ligada.
- **Medir (só se o Gestor aprovar, seção 8, W-E1).** Equivalência com a chave desligada (latência e violações
  iguais ao ASYNC puro). Com a chave ligada, comportamento igual ao `sessions` SYNC na sonda de defasagem (S_lag
  zero) e sob corte.
- **Classe: exploratória**, fora do braço B desta campanha, salvo decisão do Gestor (D-2).

## 4. Outros recursos do servidor

| Recurso | Viável no servidor sem cliente? | Mecanismo | Por que | Fonte | Classe |
|---|---|---|---|---|---|
| `ServerTask` / *scripts* | só sob chamada; não intercepta | — | seção 3.3 | `CAND-090` §14 | **fora** |
| Replicação entre *sites* (*backup*, `take-offline`) | as rotas existem, mas exigem RELAY2 na pilha e um segundo *cluster* | — | não há segundo *site*. Criar um muda a arquitetura (dois *clusters*, replicação assíncrona entre eles com resolução própria de conflitos) e não ataca a janela do M1 dentro de um *site* | `server/rest/.../XSiteResource.java:82-94`; `REF-007` | **fora** |
| `when-split=DENY_READ_WRITES` em *cache* recriado | já é a configuração dos dois modos; o atributo é imutável | — | recriar só repete o que existe e perde dados (3.1) | `cluster/infinispan-cluster.xml`; `PartitionHandlingConfiguration.java:20-23` | **fora** (já vigente) |
| `transaction` | modo de transação imutável; transação Hot Rod exige gerenciador de transações no cliente | — | cliente | `TransactionConfiguration.java:36` | **fora** |
| `locking` | só `acquire-timeout` muda em execução (B-44.1) | latência | já tratado | `cc08644` §3.3 | **exploratória** (sem mudança) |
| Quórum / `minimum-nodes` | não existe atributo; a maioria é regra fixa (`perdidos >= ceil(n/2)`); `initial-cluster-size` só vale na partida | — | não há o que mudar | `PreferConsistencyStrategy.java:157-159`; `TransportConfiguration.java:26-29` | **fora** (não existe) |
| Gerenciador de conflitos pela REST | não há rota; o `ConflictManager` é API embutida; `merge-policy` é imutável | M5 | exigiria extensão | `server/rest/.../resources/*` (sem rota); `documentation/.../code_examples/EmbeddedConflictManager.java`; `PartitionHandlingConfiguration.java:22-23` | **fora** |
| Rebalanceamento por *cache* × global | os dois existem | custo de transferência | por *cache* se perde na queda do coordenador (P-34); global fica (AC-2) | `cc08644` §3.2 | AC-2 global **entra**; por *cache* **fora** |

## 5. Classificação final

| Classe | Opções |
|---|---|
| **Entra** | AC-5 (já desenhada; passa a reserva se a AC-6 passar); AC-2 global (sem mudança); **AC-6 exclusão forçada pelo JGroups, condicionada a V-1..V-6** |
| **Exploratória** | **E-1** extensão com `FORCE_SYNCHRONOUS` (decisão do Gestor); `acquire-timeout` (sem mudança) |
| **Fora** | FD_ALL3, VERIFY_SUSPECT2, MERGE3 e GMS em execução; FD_ALL3 estático (fora da campanha); `INJECT_VIEW`; JMX remoto; recriação do *cache*; cópia mais restauração; dois *caches* com escolha no servidor; tarefas de servidor e *scripts*; entre *sites*; `when-split` recriado; transação; quórum; gerenciador de conflitos; rebalanceamento por *cache* |

**A política de reação muda assim, se a AC-6 passar:** P1 (consistência sob suspeita de partição) usa a AC-6 em
vez do AC-5. Os mecanismos alcançados passam de "M1 do lado isolado" para "M1 dos dois lados". M2 e M3
continuam sem ação **a menos que** o Gestor aprove a E-1. A frase de `cc08644` §4, "a política de replicação não é
selecionável em tempo de execução no Infinispan 15.0.21 sem mudar o cliente", continua verdadeira **para a
configuração**. Com a E-1, ganha a ressalva "sem implantar código no servidor".

## 6. O que muda em outros documentos, se o Gestor aprovar

1. `cc08644` §3.4: a linha "FD_ALL3 adaptativo" passa a dizer "sem efeito em execução (código, seção 2.2 deste
   documento)". A linha "Nada de diagnóstico do JGroups" (§6 item 10) é revogada se a AC-6 entrar.
2. `cc08644` §5: P1 passa a ser a AC-6. O AC-5 só roda se o atuador não conseguir falar com a porta de
   diagnóstico de nenhum dos três nós na janela (registrado como `motivo: diag-indisponivel`).
3. W-2a: novo tipo `AC_6`, não reversível pelo controlador (a reversão é a fusão), prazo `T_cham`, cota 1. O
   adaptador `ClienteProbeJGroups` só usa `java.net.Socket` (P-5). O teste de arquitetura R-ARQ-1 vale igual.
   Detalhes de implementação ficam para o prompt do W-2a revisado, depois do V-*.
4. VM: `diag.*` na camada TCP de `cluster/jgroups-tcp.xml` e as portas no `podman-compose.yml` da VM, declarados
   no pré-registro (B-40) antes da calibração, com o V-1 provando que nada muda sem ação.
5. TLA+: a AC-6 não ganha ação no nível 1 (é `Particionar`). O AC-5 só ganha `parado[r]` se continuar no braço B.

## 7. Pontos que precisam de validação do Gestor

| # | Pergunta | Recomendação do Senior |
|---|---|---|
| D-1 | Aceitar a AC-6 como candidata a ação de P1, condicionada a V-1..V-6, com o AC-5 de reserva | aceitar; cobre as duas metades do M1, desfaz-se sozinha e o falso positivo custa menos |
| D-2 | E-1 (extensão `FORCE_SYNCHRONOUS`): (a) só registrar como trabalho futuro; (b) prova de conceito local depois do V-*, sem compromisso de campanha; (c) entrar no braço B | (b) se houver folga de 2 dias de Worker depois da calibração ficar pronta; senão (a). (c) só com linha de base refeita |
| D-3 | Ligar o `diag` do JGroups na VM (configuração estática nova, porta sem *passcode* ou com *passcode*) | ligar com *passcode*, se o V-6 mostrar que o cliente Java do *probe* com resumo funciona; senão sem, declarado como risco de laboratório |
| D-4 | Texto: "a política de replicação não é selecionável em execução pela configuração; só com código implantado no servidor (E-1)" | aceitar; encaminhar ao Escritor pelo coordenador |
| D-5 | Ordem: V-* antes do W-2a revisado; W-2a não começa a parte de cluster antes do resultado do V-3 | aceitar |

## 8. Prompt do Worker: B-44.3, verificação empírica das opções que passaram pela leitura

> **B-44.3: exclusão forçada pelo JGroups (AC-6) e confirmações do detector de falhas, no *cluster* local.**
> **Só começar depois do fim do piloto na VM e com o host liberado pelo coordenador (política H0/H1/H2 do plano de
> execução contínua). Não usar a VM.** Branch `test/b44-3-opcoes-jgroups`, a partir de
> `docs/b44-1-reconfiguracao-infinispan` em `12c1786` (worktree novo `wt-b44-3`). Mesmo *cluster* local do B-44.1
> (três nós, imagem `quay.io/infinispan/server:15.0.21.Final`, pilha `cluster/jgroups-tcp.xml`), mesmo corte por
> `scripts/inject-partition.sh` e mesma carga S3 curta do B-44.1b. Especificação:
> `docs/opcoes-arquitetura-configuracao-2026-11-05.md` (branch `docs/plano-implementacao-controlador`), seções 2.3 e
> 2.4. Brutos em `runs/b44-3/`; relatório em `docs/b44-3-opcoes-jgroups.md`. Commits locais `test:` ou `docs:`;
> **nada vai para `origin`.** Não mexer em `controle/` nem no atuador: isto é verificação, não implementação.
>
> **Regras.** Cada passo registra, com relógio de parede em milissegundos, os instantes de pedido e resposta e as
> linhas `ISPN000093`/`ISPN000094` (vista recebida, com e sem fusão) e de disponibilidade (`DEGRADED`/`AVAILABLE`)
> de cada nó. Toda execução com ação tem uma execução de controle igual, sem ação, logo antes ou logo depois.
> Violações contadas pelo auditor como no B-44.1b, separando as operações que se sobrepõem a
> `[t_pedido, t_pedido + 5 s]`.
>
> 0. **Configuração.** Cópia de `cluster/jgroups-tcp.xml` em `runs/b44-3/cluster/` com `diag.enabled=true`,
>    `diag.enable_tcp=true`, `diag.enable_udp=false`, `diag.port=7500`, `diag.port_range=0` e, se preciso,
>    `diag.bind_addr` igual ao `bind_addr`. Uma cópia do `podman-compose.yml` publica 7500 de cada nó em 7500, 7501 e 7502
>    do host. Registrar os nomes lógicos (`cluster_members` de `GET /rest/v2/container`) e qual nó é o coordenador
>    (`coordinator_address`).
> 1. **V-1, nada muda sem ação.** Duas cargas S3 curtas (SYNC e ASYNC), uma com o `diag` desligado e outra ligado,
>    sem falha: latência p50/p99 e violações. Resultado esperado: iguais dentro da variação do B-44.1b.
> 2. **V-1b, cliente do *probe* sem processo.** Um `jshell` ou classe de teste em
>    `runs/b44-3/scripts/ProbeTcp.java` abre `java.net.Socket` para `127.0.0.1:7500`, manda a linha `jmx=GMS.view\n` e
>    lê a resposta. Repetir com `jmx=FD_ALL3` nos três nós. Comparar com `podman exec isn1 /opt/infinispan/bin/probe.sh`
>    só como referência. Registrar o formato exato da resposta.
> 3. **V-2, detector em execução não muda nada (confirmação do nulo).** Gravar `jmx=FD_ALL3.timeout=10000` nos três
>    nós, ler de volta (`jmx=FD_ALL3.timeout,num_bits`) e cortar o isn3 por 70 s. Medir T_det. Resultado esperado:
>    de 40 a 48 s, como sem a gravação, com `num_bits` ainda 5 (ver `FD_ALL3.java:56-77`, `ReflectUtils.java:214-222`).
>    Uma repetição basta. Se T_det cair, parar e reportar, porque contradiz a leitura.
> 4. **V-3, AC-6 sob corte (SYNC e ASYNC, 2 execuções de cada + controles).** Com carga, cortar o isn3 em t0. Em
>    t0 + 5 s, mandar em paralelo `op=GMS.suspect[<isn3>]` a isn1 e isn2 e `op=GMS.suspect[<isn1>]` e
>    `op=GMS.suspect[<isn2>]` ao isn3 (o cliente do V-1b). Curar em t0 + 60 s. Medir: tempo de cada resposta do
>    *probe* (`t_probe`); vista nova na maioria e no isn3 (`t_vista`); disponibilidade dos dois *caches* nos três
>    nós; primeira recusa Hot Rod no isn3 (tipo da exceção); fim do rebalanceamento da maioria; vista de fusão depois
>    da cura e linhas de resolução de conflito; escritas confirmadas pelo isn3 entre t0 e o pedido e onde elas foram
>    parar depois da fusão; violações contra o controle. Resultado esperado: maioria com vista de 2 em menos de 2 s,
>    isn3 DEGRADED e recusando, fusão automática depois da cura.
> 5. **V-4, suspeito é o coordenador.** Repetir uma execução do V-3 (ASYNC) com o isn3 como coordenador (subir os nós
>    na ordem isn3, isn1, isn2, ou derrubar e religar até ele ser o primeiro da vista). Confirmar que o próximo da
>    maioria assume e instala a vista de 2 (`ParticipantGmsImpl.java:90-100`).
> 6. **V-5, falso positivo.** Sem corte, com carga ASYNC e SYNC, mandar os quatro pedidos. Medir: tempo até a fusão
>    automática; recusas no isn3 até a fusão; violações contra o controle; estado das chaves do isn3 depois da fusão.
>    Resultado esperado: fusão em 10 a 30 s (MERGE3 do XML).
> 7. **V-5b, um lado só (curto).** Uma execução ASYNC sob corte com o pedido só na maioria e outra só no isn3.
>    Confirmar que nenhuma das duas piora as violações em relação ao controle (seção 2.4, risco 1).
> 8. **V-6, *passcode* (opcional).** Com `diag.passcode` definido, implementar o resumo de
>    `DiagnosticsHandler.authorizeProbeRequest` (`:360-373`; ver `Util.createDigest`) no `ProbeTcp` e confirmar que um
>    pedido sem resumo é recusado e um com resumo é aceito.
>
> **Saída.** Tabela por passo com os instantes, as medianas e os extremos de `t_probe` e `t_vista`, a condição de
> viabilidade `k·W + t_probe + t_vista < 39,8 s` com os valores medidos (deixar `k·W` como símbolo) e as violações com
> e sem ação. Responder sim ou não para: (a) a AC-6 funciona nos dois lados sob corte; (b) com o coordenador como
> suspeito; (c) o falso positivo funde sozinho; (d) o detector em execução ficou sem efeito; (e) o `diag` ligado não
> mudou a carga sem falha. Qualquer resposta "não" em (a), (b), (c) ou (e) para o passo seguinte e vai ao Senior.
>
> **W-E1 (só se o Gestor escolher D-2 (b); prompt separado depois do B-44.3).** Prova de conceito da extensão
> `FORCE_SYNCHRONOUS` da seção 3.4: módulo Maven isolado, JAR montado em `server/lib`, chave de controle em
> *cache* local, verificação de equivalência com a chave desligada e de comportamento SYNC com a chave ligada.
