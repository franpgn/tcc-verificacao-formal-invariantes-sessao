# 6. Protocolo e instrumentação da avaliação

**Situação dos números.** Este capítulo descreve **o que foi fixado antes dos dados** e como cada coisa foi medida.
Os valores vêm dos pré-registros em `wt-bench/docs/decisoes-tecnicas.md` (DT-018 a DT-028) e dos documentos que eles
citam, dos arquivos de entrada da avaliação (`dados-vm/cs3-2026-11/fat-20261108T051402Z/entrada-fat/`) e do código da
*tag* `int/2026-11-06-aval` (`4b96798`). As contagens de execução foram conferidas por mim em
`fat-infra/execucoes-final.tsv` (SHA-256 `1430f00e…`) e na saída de `scripts/trilhas_controlador.py`. **Nenhum
resultado da comparação entre braços aparece aqui: ver o capítulo 5.**

## 6.1 Pré-registros: a cadeia que vale para a avaliação

Regra do projeto, já na Metodologia (`Metodologia.tex:318`): o protocolo é registrado antes de qualquer execução, não é
ajustado depois de ver dados, e uma mudança vira registro novo que cita o anterior. Os registros não são editados; o
mais novo prevalece onde divergir.

| Registro | Data e *commit* | O que fixou | Situação |
|---|---|---|---|
| DT-018 e DT-019 | 04 e 05/11 | critério h4 (travamento de I/O ou pausa da VM) e contagem de episódios | vigente, também nas campanhas S3 |
| DT-020 | 05/11, documento `1e4ff2c`, entrada `a98d597` | calibração do controlador: 26 execuções no braço que observa, regras de derivação 7.1 a 7.18, critério de falso acionamento "zero ocorrências" com 3/N | vigente pelas regras; a primeira calibração (X-18) ficou superada |
| DT-022, DT-023, DT-024 (e nota 3a) | 05 e 06/11, `744e6e8`, `8eb1240`, `45e7800`, `639966c` | leituras das regras de derivação (histerese, m, W, reserva), recriação por memória, viabilidade da P1 | vigentes |
| DT-021 | 05/11, documento `2c06890`, entrada `10b241b` | avaliação B-40 parte 1: dois braços, F2 e F4 de teste com 12 blocos, F1, F3 e sem falha descritivas, referências SYNC; co-principais V e p99; interseção-união; Holm sobre 2 células; sem expectativa numérica de efeito | superada no desenho pela DT-026 e pela DT-027; valem as regras estatísticas que elas não mudaram |
| DT-025 | 06/11, `b33aebf` | valores da calibração cega X-18 | **superada** pela DT-028 |
| DT-026 | 06/11, documento `37a1099`, entrada `d3d2988` | benchmark refeito: sonda de *replay* e varredura em todas as execuções; V por pares distintos; ponto de operação F2 com carga L+; recalibração com sementes 700 000 | vigente |
| DT-027 | 06/11, documento `4fa4546`, entrada `f6965ae`; notas `3a1db1d`, `3fcd8df`, `6bc28fd`, `2a97073` | fatorial 2×2 (regra de fusão × controlador) na F2; Holm m = 2 sobre os dois efeitos; perda permanente e exposição na janela como secundárias; células descritivas; teto h4 de 20 blocos; ordem `20261106` | **vigente: é o desenho executado** |
| DT-028 | 06/11, documento `8eadca7`, entrada `9ee94a6` | valores da recalibração X-19 (`b183527a…`), `s3_replay_late_max` 306, `fusao_teto_s` 72, `acaoP1` AC-7, ambiente de *log* (P-64) | **vigente** |
| ENTREGA-ANALISE | 08/11 ~05:30Z, `fat-20261108T051402Z/ENTREGA-ANALISE.txt` (SHA-256 `37a2770c…`) | parâmetros da análise e semente do *bootstrap* (20261108), fixados antes de rodar; desvios da execução | registro de execução |

**Quem aprovou.** O Gestor aprovou as decisões de desenho de 05/11 (12 blocos, F1 descritiva, critério de falso
acionamento, sem expectativa numérica) e as de 06/11 (A1, A4, "o nosso problema é gerar as falhas de invariante").
Vários itens técnicos foram aprovados pelo coordenador **sob a autorização do Gestor** de 05/11 ~22:30Z ("Faça isso e
depois teste na VM, não precisa do meu aval") e da regra permanente de 04/11 (aceitar recomendação só quando cada
número tiver origem no projeto). A DT-026 registra que foi aprovada pelo coordenador durante a noite, com revisão do
Gestor ao acordar; não encontrei registro escrito dessa revisão, só as decisões seguintes do Gestor que a pressupõem
(memória do coordenador, 06/11 ~16:00Z).

**Por que houve três versões do desenho, todas antes dos dados da avaliação.** A calibração X-18 não observou nenhuma
violação que conta em V (q̂ = 0 de 5 nas duas células de teste) porque a carga não relia a sessão por outro nó depois
do *logout* e a varredura estava desligada; o zero era de observação, não do sistema (DT-026 §1). Um piloto
exploratório escolheu o ponto de operação que gera violações (F2 com carga L+, regra 6.5 do roteiro do piloto), a
recalibração X-19 refez os limiares com os instrumentos, e a simulação do controlador recalibrado mostrou que a parada
do nó chegaria tarde (validação da derivação da recalibração, `ddc7700`). Daí a troca da ação (AC-7) e a entrada da
A1, registradas na DT-027 antes do primeiro dado da avaliação. O capítulo 8 trata isso como ameaça à validade.

## 6.2 Desenho: fatorial 2×2 por bloco e células descritivas

Fonte: DT-027 §1 e §3 (documento `4fa4546`).

**Célula de teste: ASYNC F2** (`isn3` isolado, `DENY_READ_WRITES`), carga L+ e os três instrumentos. Cada bloco tem
quatro braços com a mesma semente, em ordem sorteada:

| Braço | Regra de fusão do `sessions-async` | Controlador |
|---|---|---|
| PO | `PREFERRED_NON_NULL` (a atual) | observando (`shadow`) |
| PL | `PREFERRED_NON_NULL` | ativo com a P1 pelo AC-7 (`active`) |
| AO | A1 (`PoliticaFusaoSessoes`) | observando |
| AL | A1 | ativo |

"Desligado" é `shadow`, para o custo de observação ser igual nos quatro. Contrastes por bloco b, com y o desfecho da
execução (diferença para V e taxas, logaritmo da razão para p99, vazão, CPU e energia):

- efeito da política de fusão: d_A1 = ½[(y_AO − y_PO) + (y_AL − y_PL)];
- efeito do controlador: d_A4 = ½[(y_PL − y_PO) + (y_AL − y_AO)];
- interação: d_int = (y_AL − y_AO) − (y_PL − y_PO).

Cada contraste é testado pela troca de sinal exata sobre os 12 blocos (2¹² = 4 096 arranjos; o menor valor-p bilateral
possível é 2/4 096 ≈ 0,0005). Os efeitos simples vão com IC, sem teste. **Por que fatorial:** com as mesmas 48
execuções, cada efeito usa os 12 blocos com o outro fator nos dois níveis, e só o fatorial mede se o controlador ainda
acrescenta algo quando a A1 já está ligada.

**Células descritivas**, com a regra de fusão atual (não há fusão nelas, salvo a volta do nó em F1, que entra por
JOIN) e dois braços por bloco, A (observando) e B (ativo):

| Célula | Blocos | Execuções | O que mede | Fase |
|---|---|---|---|---|
| ASYNC sem falha | 4 | 8 | P1 sem partição (falso alarme), p99 e vazão | núcleo |
| ASYNC F3 (atraso de 20 ± 13 ms em `isn2`) | 4 | 8 | o mesmo sob lentidão, sem corte; a defasagem sobe sem isolamento | núcleo |
| ASYNC F1 (queda silenciosa de `isn2`) | 4 | 8 | a P1 não pode agir (nó sem resposta REST); custo e ação depois da volta | extra |
| ASYNC F4 (20 % de perda na saída de `isn3`) | 4 | 8 | maior risco de falso alarme da P1 com a lista de ignorados replicando ao *cluster* | extra |

**Fora do desenho, com motivo registrado:** as células SYNC de referência da DT-026 (a A1 só muda o `sessions-async` e
o AC-7 só age nele, então elas não respondem a nenhuma das duas perguntas; DT-027 §3); F1 como célula de teste (sem
ação aplicável ao nó caído e escritas não replicadas perdidas no instante da queda; decisão do Gestor de 05/11); F4
como teste (sem mudança de vista nem fusão, o mecanismo M1 não existe; DT-026 §5); a pausa do nó (não gerou violação
de V em 5 execuções do piloto; DT-026, P-2).

Total planejado: 48 + 8 + 8 = 64 no núcleo e 16 nas extras, 80 execuções, ~8,8 h a ~395 s cada e ~11 h com a margem h4
(DT-027 §3). Executadas: 90, por causa das reposições (seção 6.10).

## 6.3 Sementes, ordem e preparação de cada execução

Fonte: `scripts/campanha_s3_matriz.py` da *tag* (linhas 19 a 39 e 105 a 149), DT-027 nota `3fcd8df`, matriz
`entrada-fat/matriz-fatorial.tsv` (SHA-256 `406a845c…`, 80 linhas).

- **Semente da carga:** 800 000 + 100 · bloco, a mesma nos braços do bloco (ex.: bloco 13, semente 801 300). As
  reposições usam os blocos novos 28 a 32 (sementes 802 800 a 803 200).
- **Ordem:** núcleo (blocos 0 a 19) antes das extras (20 a 27). Dentro de cada rodada, as células em ordem sorteada por
  `random.Random(20261106 · 1000 + r)`; dentro do bloco, os braços por `random.Random(20261106 · 10⁶ + bloco)`. A
  semente de ordem é a data do registro que fechou as notas [A4].
- **Preparação:** antes de cada execução o orquestrador recria o *cluster* do zero (`podman-compose` com o XML sem
  *caches*), cria os seis *caches* pela REST com o JSON do braço e lê de volta byte a byte nos três nós, confere a
  `merge-policy` e a linha `A1-POLITICA carregada`, aplica os níveis de *log* da P-64 e confere a camada 3
  (rebalanceamento ligado, *caches* `AVAILABLE`). O portão K1 lê o *swap* (seção 6.9). O GO de rede é dado por texto
  exato no terminal.
- **Piloto embutido:** a fase núcleo parou depois da i = 7 para conferência do protocolo (`fat-conferir piloto`,
  109 OK, `fat-infra/piloto-nucleo.txt`) e retomou; os dados entram na avaliação porque o protocolo não mudou.

## 6.4 Falhas injetadas

Fonte: `entrada-fat/campanha-s3-avaliacao.json` (`5959a349…`, campo `origem` de cada parâmetro) e `marcadores.txt`.

| Falha | Alvo | Forma | Disparo | Duração | Observado |
|---|---|---|---|---|---|
| F2, partição | `isn3` | `tc` com descarte nos dois sentidos para os pares do *cluster*; *cache* com `DENY_READ_WRITES` | +49 s da âncora (DT-011) | 90 s contados depois da confirmação do isolamento | 96,04 a 96,07 s entre os marcadores `start` e `end` nas 48 execuções |
| F1, queda silenciosa | `isn2` | interface desativada e `SIGKILL` do processo; volta por `podman start`, conferida pela REST | +49 s | 60 s | ver o capítulo 5 (60,5 s pelos marcadores) |
| F3, atraso | `isn2` | `tc-netem` 20 ms ± 13 ms, distribuição normal | +49 s | 251 s, até o fim da carga | netem efetivo ~2 s depois do disparo (pré-análise §2) |
| F4, degradação | `isn3` | 20 % de perda (severidade N3), direção de saída, só para os pares do *cluster* | +49 s | 90 s | ver o capítulo 5 |

A partição dura ~96 s, e não 90 s, porque o injetor só começa a contar depois de confirmar que o `isn3` não alcança
nenhum par (~6 s; pré-análise §2). O texto da monografia deve dizer "cerca de 96 s" ou "90 s depois da confirmação".

## 6.5 Carga S3 da avaliação (L+)

Fonte: campo `comando` de `execucao.json` e `campanha-s3-avaliacao.json`; padrões em
`S3Parametros.java` da *tag*. Os cinco valores alterados vêm da regra 6.5 do piloto de 06/11 (condição d, passagem 2,
`runs/p2-d-java.log`), registrada na DT-026 (P-1).

| Parâmetro | Opção | Valor na avaliação | Padrão do programa |
|---|---|---|---|
| usuários virtuais | `--s3-users` | **40** | 20 |
| tempo de pensar médio | `--s3-think-ms` | **500 ms** | 1 000 ms |
| duração mediana da sessão (lognormal, σ 0,6) | `--s3-session-median-sec` | **10 s** | 20 s |
| probabilidade de *logout* ao fim | `--s3-logout-prob` | **0,9** | 0,7 |
| intervalo médio do administrador | `--s3-admin-mean-sec` | **5 s** | 15 s |
| inatividade até expirar | `--s3-idle-timeout-sec` | 30 s | 30 s |
| "voltar" depois do *logout* | `--s3-back-window-sec`, `-min`, `-max` | 10 s; 1 a 3 s | idem |
| bloqueio administrativo mantido | `--s3-admin-hold-min-sec`, `-extra-sec` | 20 + até 10 s | idem |
| contas do bloqueio por limite | `--s3-limit-accounts` | 10 | 10 |
| retentativas de aplicação e espera | `--s3-app-retries`, `--s3-backoff-ms` | 2; 500 ms | idem |
| tempo-limite e retentativas do Hot Rod | `--hotrod-timeout-ms`, `--hotrod-retries` | 2 000 ms; 1 | idem |
| duração e aquecimento | `--duration`, `--warmup-min-sec` | 300 s; 30 s | idem |
| clientes | `pinned` | um servidor de aplicação por nó, sem roteamento para outro nó; console do administrador no `isn1` | idem |

Consequências que o texto precisa saber: com retentativas de 500 ms e 1 s, uma ação que falha rápido três vezes leva
~1,5 s, o que prende o p99 (capítulo 5, §5.6; pré-análise §7.3); os usuários presos ao `isn3` concentram todo o custo
do AC-7. São 13 dos 40: o usuário i fica no servidor de aplicação i mod 3 (`S3Simulacao.java:42` da *tag*), e o `isn3`
é o índice 2.

## 6.6 Instrumentos e a definição de V

Fonte: DT-026 §2 (documento `37a1099`), DT-027 §7, `analysis/apuracao_v.py` e `comparar_bracos.py` da *tag*.

**Três instrumentos, iguais em todas as execuções e braços:**

| Instrumento | O que vê | Opção |
|---|---|---|
| auditor *online* | I1, I4 e I2 nas leituras da própria carga | sempre ligado |
| sonda de *replay* | para cada *logout* confirmado, um `Validate` da sessão antiga pelo servidor de aplicação do nó (k + 1) mod 3, de 1 a 3 s depois (imediata); no fim da carga, um *replay* tardio das sessões com *logout* confirmado na falha ou na recuperação, por outro nó, até 306 candidatas | `--s3-replay-probe` (com `--s3-replay-min-sec 1 --s3-replay-max-sec 3 --s3-replay-late-max 306`) |
| varredura final | depois da convergência e da fusão, todas as chaves dos dois *caches* contra as invalidações confirmadas: VALID ⇒ I1 REAL; motivo trocado ⇒ I4 REAL; VALID com identidade bloqueada ⇒ I2; bloqueio perdido ⇒ I2-PERDIDO | `--sweep`, com espera pela fusão de até 72 s (`--sweep-merge-timeout-sec`) |

O Gestor decidiu em 06/11 que sonda e varredura são **método**, isto é, instrumentação que torna as violações
observáveis, e não desfecho separado.

**V, co-principal.** Número de **pares distintos (chave, invariante)** de I1, I4 e I2 REAL com ao menos uma detecção
na execução, somando as três origens, × 1 000 / ações S3, nas fases durante a falha e recuperação (na célula sem falha,
a execução inteira). O denominador exclui os `ReplayValidate` da sonda. Regras:

- auditor *online* e sonda imediata atribuídos pelo instante da detecção; sonda tardia e varredura, que rodam depois da
  carga, pelo instante da invalidação confirmada da sessão; sem invalidação localizada, conta sem filtro de fase;
- **duas formas de I1**, ambas contam: forma 1, *logout* confirmado pelo nó isolado e perdido na fusão (perda latente);
  forma 2, *logout* confirmado pela maioria e relido VALID no nó isolado durante a janela (exposição); sensibilidade
  sem a forma 2;
- **fora de V**: `I2_CASCATA_*`, `I2_JANELA_CASCATA`, I2 de outra classe, os contadores `*_concorrentes`, I5 e I6;
- **varredura com erro** (ou sem fusão até o teto): no bloco inteiro, V dos braços usa só auditor e sonda (tratamento
  simétrico), e a perda permanente do bloco fica não avaliável. Na avaliação, a fusão veio nas 48 partições
  (ENTREGA item 6), então o tratamento não foi usado.

A troca da contagem por linhas (DT-021) pela contagem por pares foi decidida em 06/11 (P-5), antes dos dados. Motivo:
contar linhas mede quantas vezes alguém leu a sessão errada; o mesmo *logout* perdido seria contado de novo por cada
instrumento que o alcança; e uma ocorrência por chave corresponde ao que o controlador pode evitar.

**Por que V mede, na prática, a exposição.** Explicação aprovada pelo Gestor em 07/11 (pré-análise §10.1). Quando o
`isn3` isolado confirma um *logout*, a sonda imediata relê a sessão pela maioria 1 a 3 s depois, recebe VALID, e o par
(sessão, I1) já conta nesse momento. Se a fusão depois perde o *logout*, o mesmo par continua errado, mas não conta de
novo, porque V soma pares distintos. Por construção, V cresce com o número de sessões que o cliente viu inconsistentes
durante a partição, e não distingue se o estado final ficou certo. É por isso que a A1, que age só no estado final,
aparece na perda permanente e não em V (números no capítulo 5, §5.5). A ressalva obrigatória: o resultado depende do
instrumento que define V; só com auditor e varredura, V mediria quase só a perda permanente (capítulo 5, §5.9).

## 6.7 Desfechos

Fonte: `comparar_bracos.py` da *tag* (docstring, linhas 60 a 100; famílias em `comparacao-principal.json`,
`celulas.ASYNC-F2.desfechos.*.familia`), DT-021 §5, DT-027 §2.

| Desfecho | Definição por execução | Escala no bloco | Papel |
|---|---|---|---|
| V | pares distintos de I1, I4 e I2 REAL × 1 000 / ações S3 (seção 6.6) | diferença | co-principal |
| p99 da ação S3 | p99 da latência percebida da ação S3, com as retentativas (mín. 1 000 ações) | log da razão | co-principal, margem de 10 % |
| vazão útil | ações sem falha percebida por segundo | log da razão | não inferioridade, δ = 10 % |
| rastreabilidade | toda ação dos braços ativos ligada à decisão e à regra na trilha | sim ou não | condição da regra |
| perda permanente | sessões distintas com I1 que conta em V vista depois do instante da fusão (varredura REAL, sonda tardia ou auditor *online* em ou depois de `t_fusao_ns`) | diferença | secundário |
| exposição na janela | sessões distintas com I1 que conta em V vista antes de `t_fusao_ns` (sonda imediata ou auditor *online*) | diferença | secundário |
| falhas percebidas | ações S3 que falharam depois das retentativas × 1 000 / ações S3 | diferença | secundário (custo) |
| taxa de erro | eventos com `ERROR_TRANSPORT` ou `ERROR_UNAVAILABLE` / eventos | diferença | secundário (custo) |
| Δ_rec | da última operação iniciada depois do fim da falha que terminou em anomalia até o fim da falha; censura nos últimos 4 s antes do fim da carga | diferença | secundário |
| Δ_conv | do fim da carga até a primeira de três rodadas seguidas sem divergência entre réplicas | diferença | secundário |
| divergência persistente | 1 se o laço não convergiu em 600 s | diferença | secundário |
| p99 sem espera, p99 por operação | o mesmo p99 sem a espera do portão (zero nesta campanha) e o p99 de cada operação com ao menos 1 000 eventos (só `Validate` passou) | log da razão | descritivos pelo pré-registro (o *script* os pôs na família de Holm; ver abaixo) |
| CPU e energia | CPU-s da VM por 1 000 ações úteis; energia estimada por 1 000 ações úteis com razão ociosa 0,3 (seção 6.8) | log da razão | secundários |
| cada invariante em separado (I1, I4, I2 real) | contagem por invariante | diferença | descritivos |
| sensibilidades | V sem a forma 2; taxas em razão sem zeros; energia com 0,5; Δ_rec pela linha de base binomial | | sem Holm |

**Regra de sucesso, por efeito principal** (DT-027 §2.1): interseção-união, `p_IU` = máx(p_V, p_L); H_V rejeitada com
valor-p da troca de sinal ≤ α e média negativa; H_L rejeitada com o limite superior do IC t de 95 % da razão do p99
≤ 1,10; "melhoria" exige também vazão não inferior e rastreabilidade integral dos braços PL e AL. Holm sobre os dois
efeitos (m = 2): o menor `p_IU` precisa ficar abaixo de 0,025, o que exige k ≥ 7 blocos com diferença não nula. H_V é
não avaliável num efeito quando os braços de referência dele (PO e PL para a A1; PO e AO para o controlador) somam zero
violação. A interação vai com IC e valor-p, sem Holm e fora da regra.

**Secundários: família de Holm de 9 hipóteses por efeito**, pelo pré-registro. São os 7 da DT-021 §5.3 (Δ_rec,
Δ_conv, divergência persistente, taxa de erro, falhas percebidas, CPU por mil ações úteis e energia com ociosidade
0,3; `wt-bench/docs/pre-registro-b40-avaliacao-2026-11-05.md` §5.3, "família fixa") mais perda permanente e exposição
na janela (DT-027 §2.2). O pré-registro deixa o p99 por operação e cada invariante como **descritivos**, "para a
família não crescer", e mandava ajustar o código se a família fosse outra. IC primário: t (decisão D3 de 27/10);
*bootstrap* por blocos como sensibilidade.

**Desvio da ferramenta (declarado no capítulo 8, D13).** O `comparar_bracos.py` oficial não foi ajustado: ele põe na
família todo desfecho rotulado "secundario" que tenha valor-p, e chegou a **m = 11** por efeito, com o p99 sem espera e
o p99 do `Validate` (os p99 das outras seis operações saíram sem bloco, com menos de 1 000 eventos; ENTREGA item 5 e
`n_secundarios_holm` = 11 no JSON oficial). O desvio é conservador e não muda nenhuma conclusão; o capítulo 5 reporta
o Holm da família de 9, com o valor do *script* ao lado (validação de 11/11, §4b).

**W = 191 s, a janela da recuperação.** A fase de recuperação vai do fim da falha até máx(fim + 191 s, convergência)
(`analysis/fases.py`). O valor 191 = 300 − 49 − 60 (duração − disparo − F1) e fica acima da janela de fusão de ~96 s
(C-4 da DT-020). Na F2, com o fim da falha a ~+145 s da âncora, a recuperação cobre todo o resto da carga.

**Convergência.** O laço roda depois do fim da carga, com período igual a W (4 s) e teto de 600 s (regra 7.10:
Δ_conv máximo de 11,09 s na recalibração, abaixo de 400 s). Uma execução no teto não é descartada nem imputada; entra
na divergência persistente. **`t_fusao_ns`** (P-61, P-65): primeira amostra da sonda de topologia do controlador depois
do `end` com os três nós respondendo e número de nós igual a 3 (resolução de 2 s), conferida contra a primeira linha
`ISPN000093` do *log* do servidor; o orquestrador espera a fusão até 72 s (48 s, a maior medida nas calibrações, × 1,5)
antes da varredura.

## 6.8 Energia por *proxy*

Fonte: `analysis/energia_proxy.py` da *tag* (linhas 1 a 70), `Metodologia.tex:330-335`, ENTREGA item 5, validação de
11/11 §4.

- **Medido:** tempo de CPU por segundo de cada contêiner (cgroup v2), do processo da carga e da VM inteira, com o
  *steal* à parte, pelo coletor de recursos a 1 s. Métrica principal: CPU-s da VM por 1 000 ações úteis.
- **Estimado:** P(u) = P_ocioso + (P_max − P_ocioso) · u, com u = CPU-s / (duração × 4 vCPU) e P_ocioso = r · P_max.
  P_max = 181 W, a potência turbo máxima do i5-14600KF (`intel_i5_14600kf`); r = 0,3 central, porque processadores
  consomem menos de um terço do pico em atividade muito baixa (`barroso_holzle_energy_proportional_2007`, p. 36); r =
  0,5 de sensibilidade (p. 34, servidor inteiro). O valor 0,7 saiu por não ter fonte (decisão de 05/11). P_max se
  cancela nas razões entre braços; só os joules absolutos dependem dele.
- **Exclusão:** execução com *steal* acima de 5 % sai da comparação de CPU e energia (`Metodologia.tex:335`) e, pela
  regra conservadora do B-39 (item 8), também a execução **sem medida de *steal***, levando o bloco inteiro. Na F2, 8
  dos 12 blocos saíram com motivo `steal-indisponivel`: em 48 das 86 execuções com arquivo de energia (23 das 48 da F2),
  a primeira amostra da VM veio 0,01 a 0,13 s depois da primeira operação, e o cálculo não extrapola. O *steal* real,
  medido do primeiro ao último ponto válido, foi zero em todas (validação de 11/11 §4). O resultado oficial é o de 4
  blocos; a sensibilidade com 12, não pré-registrada, está no capítulo 5 (§5.7).
- **Sem falha:** a energia da ASYNC sem falha foi calculada sem marcadores e com `--w-recovery-s 191` (LEIAME da
  análise oficial), por exigência da checagem B39-R1 (mesmo recorte da comparação).

## 6.9 Critério h4, regras de parada e reposição

Fontes: DT-018 e DT-019 (`decisoes-tecnicas.md`), complemento 3 do pré-registro do *rebaseline*
(`docs/pre-registro-rebaseline-complemento-3-2026-11-04.md`), DT-023, DT-027 nota de 06/11, cabeçalho de
`scripts/run-campanha-s3.sh` da *tag*, roteiro da avaliação §9.

**h4: interferência do *host*.** Um amostrador de 1 s roda na VM como *root*, numa *unit* própria, e grava em
`/run/tccdiag/` (memória), fora do orquestrador. Na janela de medição de cada execução (+44 s a +300 s da âncora), a
execução fica DESCARTADA-INFRA quando:

| Ramo | Condição | Origem do limiar |
|---|---|---|
| h4a | alguma janela de 10 s com 1,5 s ou mais de pressão de E/S do tipo *full* (PSI) | pior janela do ruído limpo 0,69 s; episódios de 1,73 a 9,32 s (dados de 04 e 05/11) |
| h4b | intervalo entre amostras ≥ 3,0 s ou salto de relógio ≥ 1 s | maior intervalo limpo 1,014 s |
| h4c | cobertura incompleta da janela | amostrador ausente |

Na F1, o h4a não é avaliado entre o reinício − 2 s e o reinício + 60 s (só registrado). O descarte **não consulta
nenhum desfecho**.

| Regra | O que acontece |
|---|---|
| reposição | o bloco que perde um braço é refeito inteiro, com bloco e semente novos, **uma vez só**; o braço restante de um bloco já perdido vira PULADA |
| teto de reposições | 20 blocos (DT-027, nota de 06/11; parada de segurança contra ambiente doente) |
| parada h4 | 5 h4 entre as últimas 12 execuções, 4 seguidas, um h4c ou amostrador inativo: a fase para com código 10 e só retoma com Senior e Gestor |
| memória (DT-023, P-56) | *swap* ≥ 1 GiB no portão K1: recriar o *cluster* e rodar a mesma execução com a mesma semente, sem contar como recusa; se continuar alto depois da recriação, a fase para com código 11 |
| política de fusão | a do braço não ficou nos três nós: DESCARTADA-INFRA sem carga; três seguidas da mesma variante param a fase (código 6) |
| níveis de *log* | `ajusta-log.sh` falhou: a fase para (código 6) |
| teto de tempo | `timeout -k 23 s 1019 s` por execução (regra 7.17) |
| progresso | *log* parado por 600 s gera só alerta, sem ação |
| arquivo `PARAR` | para a fase entre execuções |

## 6.10 Execução por fase

Fontes: `fat-infra/execucoes-final.tsv`, ENTREGA itens 3, 8 e 9, `fat-infra/repor.txt`.

| Fase | Janela (UTC, 07/11) | Boot da VM e amostrador h4 | Execuções | Aceitas | Descartadas (h4a) | Puladas |
|---|---|---|---|---|---|---|
| núcleo (blocos 0 a 19) | GO 00:33:40; fim 07:41:17,988 | `f9e98b74…`; `diag-20261107T002832Z` | 64 (i 0 a 63) | 64 | 0 | 0 |
| extra (blocos 20 a 27) | 15:18 a 16:17; 16:20 a 16:40:51 (parada h4); 18:16:02 | `0c1b7856…`; `diag-20261107T151734Z` | 16 (i 64 a 79) | 7 | 5 (i 64, 70, 72, 77, 78) | 4 (i 65, 71, 73, 79) |
| reposições (blocos 28 a 32) | 18:17:10 a 19:33:47 | idem | 10 (i 80 a 89) | 9 | 1 (i 87) | 0 |
| **total** | | | **90** | **80** | **6** | **4** |

| Célula | Blocos planejados | Blocos completos | Blocos perdidos | Reposições |
|---|---|---|---|---|
| ASYNC F2 (PO, PL, AO, AL) | 12 | 12 | nenhum | nenhuma |
| ASYNC F3 | 4 | 4 | nenhum | nenhuma |
| ASYNC sem falha | 4 | 4 | nenhum | nenhuma |
| ASYNC F4 | 4 | 4 (21, 22, 25, 32) | 27 | 32 repõe 27 |
| ASYNC F1 | 4 | 3 (28, 29, 30) | 20, 23, 24, 26 e 31 | 28 a 31 repõem 20, 23, 24, 26; o 31 perdeu-se de novo e, pela regra, não foi reposto |

A queda de energia do *host* (~07:54Z a 14:06Z de 07/11) caiu entre o núcleo e a extra; nenhum bloco atravessa as
fases, e cada execução recria o *cluster* (ENTREGA item 8; capítulo 8). Reposições usadas: 5 de 20 blocos.

## 6.11 Análise

Fonte: `analise-oficial/LEIAME.txt`, ENTREGA item 5, validação de 11/11.

- Código: `analysis/comparar_bracos.py` e `analysis/energia_proxy.py` da árvore da *tag*, com `python -E -P`
  (Python 3.12.6, numpy 2.2.0, scipy 1.16.0).
- Parâmetros: `--delta-latencia 0.10 --delta 0.10 --w-analise-s 4 --w-recovery-s 191`, célula de teste ASYNC-F2,
  descritivas ASYNC-F3, ASYNC-F1 e ASYNC-F4; ASYNC sem falha em chamada própria com `--fases antes`; energia com
  `--p-max-w 181 --razao-ocioso-central 0.3`.
- Semente do *bootstrap*: 20261108, fixada na ENTREGA antes de rodar (a prévia de 07/11 usou 20261107 e não é oficial).
- Manifestos: só blocos completos (`manifesto-bracos.tsv`, 78 linhas, SHA-256 `843a2a6f…`).
- Validação (11/11): recálculo independente sem importar o código do projeto, idêntico ao oficial; reprodução byte a
  byte dos quatro arquivos oficiais (capítulo 9).

## 6.12 Tabelas para a monografia

### T6.1 Desenho da avaliação: células e braços (NOVA, em `sec-avaliacao-controlador`, `Metodologia.tex:344`)

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Células e braços do desenho da avaliação do controlador}
\label{qd-desenho-avaliacao}
\centering
\footnotesize
\begin{tabular}{p{3.0cm}p{1.6cm}p{4.6cm}p{1.4cm}p{3.0cm}}
\hline
\textbf{Célula} & \textbf{Papel} & \textbf{Braços} & \textbf{Blocos} & \textbf{O que mede} \\
\hline
Partição de \texttt{isn3} (F2) & teste & regra de fusão atual ou A1, cruzada com o controlador observando ou ativo (quatro braços) & 12 & efeito do controlador, efeito da regra de fusão e interação \\
Sem falha & descritiva & controlador observando ou ativo & 4 & acionamento indevido \\
Atraso em \texttt{isn2} (F3) & descritiva & idem & 4 & acionamento indevido sob lentidão \\
Queda de \texttt{isn2} (F1) & descritiva & idem & 4 & custo sem ação aplicável \\
Perda de 20\,\% em \texttt{isn3} (F4) & descritiva & idem & 4 & acionamento indevido sem partição \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### T6.2 Desfechos da avaliação (NOVA, em `sec-metricas` ou `sec-avaliacao-controlador`)

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Desfechos da avaliação do controlador}
\label{qd-desfechos-avaliacao}
\centering
\footnotesize
\begin{tabular}{p{3.0cm}p{7.6cm}p{3.2cm}}
\hline
\textbf{Desfecho} & \textbf{Definição por execução} & \textbf{Papel} \\
\hline
Taxa de violações & pares distintos de sessão ou identidade e invariante (I1, I4 e I2 real) detectados pelo auditor, pela sonda de releitura ou pela varredura final, por mil ações S3 & co-principal \\
p99 da ação S3 & percentil 99 da latência percebida, com as retentativas & co-principal, margem de 10\,\% \\
Vazão útil & ações S3 sem falha percebida por segundo & não inferioridade, margem de 10\,\% \\
Perda permanente & sessões com I1 vistas depois da fusão das partições & secundário \\
Exposição na janela & sessões com I1 vistas antes da fusão & secundário \\
Falhas percebidas & ações S3 que falharam depois das retentativas, por mil ações & secundário \\
Taxa de erro & fração de eventos com erro de transporte ou indisponibilidade & secundário \\
Recuperação e convergência & $\Delta_\textsf{rec}$, $\Delta_\textsf{conv}$ e divergência persistente & secundários \\
CPU e energia & tempo de CPU e energia estimada por mil ações úteis & secundários \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### T6.3 Parâmetros da campanha de avaliação (NOVA, depois de `tab-parametros-baseline`, `Metodologia.tex:236-285`)

Não editar a `tab-parametros-baseline`, que documenta o plano. Em texto, dizer que na avaliação T9, T10, T12, T14 e T22
foram substituídos pelos valores abaixo e que T4 segue em 64 segmentos (DT-016).

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Tabela}
\caption{Parâmetros da campanha de avaliação do controlador}
\label{tab-parametros-avaliacao}
\centering
\footnotesize
\begin{tabular}{p{5.0cm}p{3.6cm}p{5.2cm}}
\hline
\textbf{Parâmetro} & \textbf{Valor} & \textbf{Origem} \\
\hline
Modo de replicação & \texttt{DIST\_ASYNC} & objeto do trabalho \\
Duração, aquecimento e disparo da falha & 300\,s; 30\,s; +49\,s & protocolo da nova medição \\
Partição (F2) & \texttt{isn3}, 90\,s depois da confirmação, cerca de 96\,s & injetor de partição \\
Queda (F1) & \texttt{isn2}, 60\,s & protocolo da nova medição \\
Atraso (F3) & \texttt{isn2}, 20\,ms $\pm$ 13\,ms, 251\,s & protocolo da nova medição \\
Degradação (F4) & \texttt{isn3}, 20\,\% de perda na saída, 90\,s & severidade N3 \\
Usuários virtuais e tempo de pensar & 40; 500\,ms & ponto de operação do piloto \\
Sessão mediana e \emph{logout} ao fim & 10\,s; 0,9 & ponto de operação do piloto \\
Intervalo médio do administrador & 5\,s & ponto de operação do piloto \\
Retentativas e espera & 2; 500\,ms & cenário S3 \\
Tempo-limite do cliente Hot Rod & 2\,s & cenário S3 \\
Sonda de releitura & 1 a 3\,s depois do \emph{logout}; até 306 sessões no fim & maior número de candidatas na recalibração \\
Espera pela fusão antes da varredura & até 72\,s & maior fusão medida, 48\,s, vezes 1,5 \\
Janela de recuperação & 191\,s & $300 - 49 - 60$, acima da fusão de cerca de 96\,s \\
Teto do laço de convergência & 600\,s & regra pré-registrada \\
Blocos da célula de teste & 12 & decisão do autor \\
Semente da carga & $800\,000 + 100 \cdot \textit{bloco}$ & pré-registro \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### T6.4 Critérios de descarte, reposição e parada (NOVA, em `sec-verificacao-injetores` ou `sec-avaliacao-controlador`; opcional, o texto DES-83 já descreve o h4)

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Critérios de descarte, reposição e parada das campanhas com o cenário S3}
\label{qd-criterios-campanha}
\centering
\footnotesize
\begin{tabular}{p{3.4cm}p{6.0cm}p{4.4cm}}
\hline
\textbf{Critério} & \textbf{Condição} & \textbf{Consequência} \\
\hline
h4a & janela de 10\,s com 1,5\,s ou mais de pressão de E/S do tipo \emph{full} & execução descartada e bloco refeito \\
h4b & intervalo entre amostras de 3\,s ou mais, ou salto de relógio de 1\,s ou mais & idem \\
h4c & cobertura incompleta da janela de medição & idem; a fase para \\
Reposição & bloco que perde um braço & refeito uma vez, com semente nova; teto de 20 blocos \\
Parada por interferência & 5 descartes h4 nas últimas 12 execuções ou 4 seguidos & a fase para até nova decisão \\
Memória & \emph{swap} de 1\,GiB ou mais antes da execução & \emph{cluster} recriado; a execução roda com a mesma semente \\
Política de fusão & a política do braço não fica nos três nós & execução descartada; três seguidas param a fase \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

### T6.5 Execução da avaliação por fase (NOVA, no capítulo de resultados, junto da `tab-aval-execucoes` do capítulo 5)

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Tabela}
\caption{Execuções da avaliação por fase}
\label{tab-aval-fases}
\centering
\footnotesize
\begin{tabular}{p{2.6cm}p{3.0cm}p{1.8cm}p{1.4cm}p{2.2cm}p{1.4cm}}
\hline
\textbf{Fase} & \textbf{Células} & \textbf{Execuções} & \textbf{Aceitas} & \textbf{Descartadas} & \textbf{Puladas} \\
\hline
Núcleo & F2, F3 e sem falha & 64 & 64 & 0 & 0 \\
Extra & F1 e F4 & 16 & 7 & 5 & 4 \\
Reposições & F1 e F4 & 10 & 9 & 1 & 0 \\
Total & & 90 & 80 & 6 & 4 \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

Nota para o texto: todos os descartes foram por h4a; o núcleo e a extra rodaram em dois boots da VM, separados pela
queda de energia de 07/11.

### T6.6 Pré-registros (NOVA, opcional, em apêndice)

| Registro | Data | O que fixou |
|---|---|---|
| calibração | 05/11 | regras de derivação dos limiares e critério de zero acionamento indevido |
| avaliação, parte 1 | 05/11 | dois braços, 12 blocos, co-principais, interseção-união, sem expectativa de efeito |
| benchmark refeito | 06/11 | sonda de releitura e varredura em todas as execuções; taxa por pares distintos; ponto de operação |
| fatorial | 06/11 | regra de fusão × controlador na partição; Holm sobre dois efeitos; perda e exposição |
| valores finais | 06/11 | limiares da recalibração e parâmetros novos da sonda e da fusão |
| análise | 08/11 | parâmetros e semente da análise, antes de rodá-la |

```latex
\begin{table}[htbp]
\captionsetup{type=table, name=Quadro}
\caption{Registros do protocolo da avaliação, todos anteriores aos dados}
\label{qd-prerregistros}
\centering
\footnotesize
\begin{tabular}{p{3.0cm}p{1.6cm}p{9.2cm}}
\hline
\textbf{Registro} & \textbf{Data} & \textbf{O que fixou} \\
\hline
Calibração & 05/11 & regras de derivação dos limiares e critério de nenhum acionamento indevido \\
Avaliação, parte 1 & 05/11 & dois braços, 12 blocos, desfechos co-principais, interseção-união e ausência de expectativa de efeito \\
\emph{Benchmark} refeito & 06/11 & sonda de releitura e varredura em todas as execuções, taxa por pares distintos e ponto de operação \\
Fatorial & 06/11 & regra de fusão cruzada com o controlador na partição, Holm sobre os dois efeitos, perda permanente e exposição \\
Valores finais & 06/11 & limiares da recalibração e parâmetros da sonda e da espera pela fusão \\
Análise & 08/11 & parâmetros e semente da análise, antes de rodá-la \\
\hline
\end{tabular}
\fonte{O autor}
\end{table}
```

## 6.13 Já está na monografia × novo ou mudou

| Arquivo:linha | O que o texto diz | Situação | Evidência |
|---|---|---|---|
| `Metodologia.tex:219` (MET-62) | taxa = "soma das violações de I1, das de I4 e das de I2 classificadas como reais, por mil ações" | **corrigir**: pares distintos, três instrumentos (6.6) | DT-026 P-5 |
| `Metodologia.tex:221` (MET-63) | Δ_rec com censura e W derivado | **mantém**; acrescentar W = 4 s e censura nos últimos 4 s | `comparar_bracos.py` |
| `Metodologia.tex:223` (MET-64) | Δ_conv do fim da carga | **mantém**; acrescentar teto 600 s e período 4 s | DT-028 |
| `Metodologia.tex:229` (MET-65, MET-66) | troca de sinal α = 0,05; interseção-união; "baseline estático" × controlador | **ajustar** para o fatorial: dois efeitos, Holm m = 2 | DT-027 §2.1 |
| `Metodologia.tex:313` (MET-40) | parâmetros do S3 "serão fixados no pré-registro" | **corrigir**: fixados (T6.3), carga L+ escolhida no piloto | DT-026 P-1 |
| `Metodologia.tex:333-335` (MET-42, MET-69) | "O método previsto"; exclusão só por *steal* > 5 % | **atualizar**: método executado; acrescentar a exclusão por *steal* sem medida e o efeito (4 de 12 blocos) | 6.8 |
| `Metodologia.tex:347` (MET-43, MET-71) | dois braços, quatro células (sem falha, F2, F4, F1), oito blocos, SYNC de referência, ~80 execuções, 16 a 27 h | **errado**: fatorial na F2 com 12 blocos e quatro células descritivas de 4 blocos; sem SYNC; 90 execuções (~9,8 h de VM em três fases) | DT-027; 6.10 |
| `Metodologia.tex:349` (MET-72) | oito blocos, 2⁸ = 256, menor p a partir de k = 6 | **corrigir**: 12 blocos, 2¹² = 4 096; com Holm m = 2, o menor `p_IU` exige k ≥ 7 | DT-027 §2.1 |
| `Metodologia.tex:351` (MET-72) | Holm "das três células com falha"; braço A sem violação torna a célula não avaliável; lista de secundários com p99 por operação e cada invariante | **corrigir** para efeitos; família de 9 por efeito (os 7 da DT-021 mais perda permanente e exposição); p99 por operação e cada invariante como descritivos; declarar que a ferramenta usou 11 | DT-021 §5.3; DT-027 §2.2 e §7; validação de 11/11 §4b |
| `Metodologia.tex:353` (MET-43) | "Os limiares ... serão calibrados" | **corrigir**: calibrados duas vezes (X-18 e X-19), valores na DT-028 | capítulo 4 |
| `Desenvolvimento.tex:92` (DES-83) | h4 e reposição "até um teto fixado para a campanha" | **mantém**; acrescentar teto de 20 blocos e a parada por 5 em 12 | DT-027 nota |
| `Desenvolvimento.tex:101` (DES-35) | *caches* assíncronos "só será instalada na máquina virtual quando o *cluster* for recriado" | **atualizar**: nas campanhas S3 o *cluster* sobe sem *caches* e os seis são criados pela REST antes de cada execução, com o JSON do braço | TD-015; 6.3 |
| `Desenvolvimento.tex:128` (DES-45) | laço de convergência a cada 5 s | **atualizar**: período = W (4 s) na avaliação | DT-028 |
| `Desenvolvimento.tex:136` (DES-47) | "nenhum consumo foi medido em campanha" | **corrigir**: medido na calibração, na recalibração e na avaliação | 6.8 |
| `Desenvolvimento.tex:217` (DES-50) | avaliação "não foi pré-registrada nem executada" | **corrigir** | 6.1 e 6.10 |
| não existe | instrumentos (sonda e varredura), formas de I1, perda e exposição, `t_fusao_ns`, regras de parada, execução por fase | **novo** | 6.6 a 6.10 |

## 6.14 Fontes bibliográficas necessárias

| Afirmação | Chave | Situação |
|---|---|---|
| pré-registro | `nosek_preregistration_2018` | existe, já citada |
| interseção-união | `berger_iut_1982` | existe |
| Holm | `holm_sequential_1979` | existe |
| troca de sinal exata | `fisher_design_experiments_1935`, `good_permutation_tests_2005` | existem |
| intervalo t | `student_probable_error_1908` | existe |
| *bootstrap* | `efron_tibshirani_bootstrap_1993` | existe |
| zero eventos em N (≈ 3/N) e IC exato | `hanley_lippmanhand_zero_1983`, `clopper_pearson_binomial_1934` | existem |
| intervalo de Wilson (usado no JSON oficial para as contagens) | `brown_cai_dasgupta_binomial_2001` | existe |
| reuso de sessão depois do *logout* (forma 2 de I1) | `owasp_session_mgmt_cheatsheet`, `nist_sp_800_63b4_2025` | existem; **Bibliotecário** confere a seção exata (DT-026 §2.5 deixou para ele) |
| energia: modelo linear, P_max, razões | `fan_power_provisioning_2007`, `intel_i5_14600kf`, `barroso_holzle_energy_proportional_2007` (p. 34 e 36) | existem, já citadas |
| energia só por modelo em VM | `kansal_vm_power_2010` | existe |
| cgroup v2 e PSI | `linux_cgroup_v2`, `linux_psi_6_8` | existem |
| fatorial 2×2 em blocos aleatorizados, efeitos principais e interação | nenhuma | **Bibliotecário**: livro de planejamento de experimentos (por exemplo, Montgomery, *Design and Analysis of Experiments*, ou Box, Hunter e Hunter) |
| p99 preso no patamar das retentativas | nenhuma fonte necessária | medida do projeto |

## 6.15 Pendências e o que não pôde ser verificado

- **Revisão da DT-026 pelo Gestor:** registrada como "revisa ao acordar"; não achei registro escrito dela.
- **Duração efetiva de F1 e F4:** tirei do capítulo 5 (60,5 s e 89,6 s); não reconferi nos marcadores.
- **Contagem de 48 de 86 execuções sem *steal*:** vem da validação de 11/11; não refiz.
- **Tempo total de VM** (~9,8 h somando as três fases: núcleo 00:33Z a 07:41Z, extra 15:18Z a 16:40Z, reposições 18:17Z a 19:33Z): estimativa
  minha a partir das janelas da tabela 6.10, não medida.
