# Complemento 1 ao pré-registro do benchmark refeito: avaliação da regra de fusão (A1) e do controlador com a ação nova (A4) (2026-11-06)

> Senior, 2026-11-06, só documentos, **antes de qualquer dado da avaliação**. Branch `docs/pre-registro-benchmark-refeito`
> (`wt-bench`). Complementa a DT-026 (`docs/pre-registro-benchmark-refeito-2026-11-06.md`) e é registrado como
> **DT-027**. A DT-026 não é editada; onde divergirem, vale este. Sem push.
>
> **Pontos marcados [A4]** dependem da implementação da ação nova do P1, em curso; eles fecham numa nota a este
> complemento, ainda antes dos dados, quando o código e a validação da A4 saírem.
>
> Decisão do Gestor (06/11, pelo coordenador): comparar **a regra de fusão atual × a regra nova (A1)** e **o
> controlador desligado × ligado com a ação nova (A4)**. Fontes:
>
> - A1: `wt-a1/docs/relatorio-a1-2026-11-06.md` (`288b01c`). `PoliticaFusaoSessoes`, ordem de conteúdo T2, só no
>   `sessions-async`, com a remoção da chave como saída de segurança. No teste dirigido, 26 de 26 *logouts* preservados
>   contra 21 a 23 de 23 desfeitos com `PREFERRED_NON_NULL`; sob carga, 0 desfeitos contra 3. A exposição na janela não
>   muda, e a varredura subconta a perda no controle (achado 1).
> - A4: `wt-a4/docs/relatorio-a4-2026-11-06.md` (`290ff40`). `ignored-caches/sessions-async` no suspeito: recusa no alvo
>   em 16 a 70 ms, contra 13,1 s da parada; regras R1 a R8; o falso alarme se espalha ao *cluster*.
> - Tempo do P1 com o controlador recalibrado: `wt-w9/docs/validacao-derivacao-recalibracao-2026-11-06.md` §4. O P1
>   dispara a ~+10 s do corte; as perdas se concentram nos primeiros ~15 s.
> - Recalibração: `dados-vm/cs3-2026-11/derivacao-recal-20261106T145106Z/calibracao-s3-derivacao.json`
>   (`b183527a4c88476a1feda51bfd6e7fedb832949344d04ad59c80606b25126121`), conferida na mesma validação. **Continua
>   válida**: a A1 só age na fusão e não muda nenhum sinal do controlador, e a A4 troca só a ação, não a decisão.

## 1. Desenho: fatorial 2×2 por bloco na célula de teste (recomendado)

**Célula de teste: ASYNC F2** (`isn3` isolado 90 s, `DENY_READ_WRITES`), carga L+ e os instrumentos da DT-026 (`--sweep
--s3-replay-probe --s3-replay-min-sec 1 --s3-replay-max-sec 3 --s3-replay-late-max 268`). Cada bloco tem **quatro
braços com a mesma semente**, em ordem sorteada dentro do bloco:

| Braço | Regra de fusão do `sessions-async` | Controlador |
|---|---|---|
| PO | `PREFERRED_NON_NULL` (atual) | desligado (`shadow`: observa, decide e registra, não age) |
| PL | `PREFERRED_NON_NULL` | ligado com a A4 (`active`) |
| AO | A1 (`PoliticaFusaoSessoes`) | desligado (`shadow`) |
| AL | A1 | ligado com a A4 |

"Desligado" é `shadow`, como o braço A da DT-021, para o custo de observação ser o mesmo nos quatro.

**Por que o fatorial e não duas comparações separadas.** Com o mesmo orçamento na célula de teste (48 execuções em 12
blocos), as duas comparações separadas (12 blocos de PO × AO e 12 de PO × PL) respondem só a "a A1 sozinha" e "a A4
sozinha". O fatorial responde às duas com **12 blocos cada, usando as 48 execuções em cada efeito** (o efeito da A1 é
medido com o controlador desligado e com ele ligado, e vice-versa), e responde também à pergunta que importa para a
recomendação final: **a A4 ainda acrescenta algo quando a A1 já está ligada?** (a interação). A A1 age só no que o nó
isolado confirmou antes da ação e que a fusão desfaria; a A4 age só depois do P1, sobre o que o nó isolado ainda
confirmaria e serviria. Os dois mecanismos se sobrepõem em parte, e só o fatorial mede a sobreposição. O custo é a
análise por bloco de quatro braços, que o `comparar_bracos.py` ainda não faz (seção 6).

**Contrastes por bloco b** (y = desfecho por execução, na escala da DT-021: diferença para V, log da razão para p99):

- efeito da A1: `d_A1 = ½[(y_AO − y_PO) + (y_AL − y_PL)]`;
- efeito da A4: `d_A4 = ½[(y_PL − y_PO) + (y_AL − y_AO)]`;
- interação: `d_int = (y_AL − y_AO) − (y_PL − y_PO)`.

Cada contraste é testado pela troca de sinal exata sobre os 12 blocos, como na DT-021. Os efeitos simples (A1 com o
controlador desligado, A4 com a regra atual, etc.) vão ao relatório com IC, sem teste.

## 2. Desfechos e regra de sucesso

### 2.1 Co-principais: V e o p99, como na DT-021 e na DT-026

**V** = (I1 + I4 + I2 REAL) por mil ações S3, por par distinto (chave, invariante), das três origens, com a forma 2
contando, a sensibilidade `:sem_forma2` e o tratamento simétrico da varredura com erro (DT-026 §2). **L** = p99 da ação
S3 com espera. Regra por efeito principal (A1 e A4): interseção-união como na DT-021 (`p_IU = max(p_V, p_L)`, δ_L =
10 %), e "melhoria" exige também vazão útil não inferior (δ = 10 %) e rastreabilidade integral.

**Família de Holm: os dois efeitos principais, m = 2** (A1 e A4). O efeito de menor `p_IU` precisa ficar abaixo de
0,025, o que exige k ≥ 7 blocos com d ≠ 0; o outro, abaixo de 0,05, k ≥ 6. A interação é reportada com IC e o p da troca
de sinal, **sem Holm e sem entrar na regra de sucesso**.

**Por que V continua co-principal e as medidas novas ficam secundárias.** V é o desfecho pré-registrado desde a G-1
(DT-021, DT-026). As duas medidas novas são decomposições de V no tempo (antes e depois da fusão) e foram propostas
depois de ver os *smokes* da A1 e da A4, que mostraram onde cada ação age. Trocar o desfecho principal pelo que o *smoke*
favoreceu seria escolher o critério olhando os dados. V já inclui as duas, porque soma as três origens por par.

### 2.2 Secundárias novas (família de Holm própria por efeito principal, com os secundários da DT-021)

| Medida | Definição, por execução | Instrumentos |
|---|---|---|
| **perda permanente** | número de sids distintas com I1 **depois da fusão**: a união, por sid, da varredura (I1 REAL), do I1 *online* com detecção depois do instante da fusão e do *replay* tardio (I1 da sonda tardia) | varredura, auditor *online*, sonda tardia. A união corrige a subcontagem da varredura sozinha (relatório da A1, achado 1: depois de uma fusão com `PREFERRED_NON_NULL`, as réplicas da mesma sessão podem ficar diferentes, e a varredura lê uma cópia por chave) |
| **exposição na janela** | número de sids distintas com I1 detectado **antes do instante da fusão** pela sonda imediata ou pelo auditor *online* | sonda imediata, auditor *online* |

O **instante da fusão** é o da primeira vista de fusão (`ISPN000093`) depois do marcador `end` em qualquer nó, gravado
pelo orquestrador (seção 5). Uma sid pode estar nas duas medidas. As duas usam a escala de diferença por bloco e entram
na família de secundários de cada efeito principal, com Holm dentro dela (DT-021 §5.3, família acrescida das duas).

Os demais secundários e descritivos da DT-021 e da DT-026 valem nos quatro braços. Descritivos novos: decisões da A1
(linhas `A1-FUSAO`, escolhas em que a A1 divergiu do `PREFERRED_NON_NULL`, usos da saída de segurança) e ações da A4
(seção 4).

### 2.3 Sem previsão de efeito

Como na DT-021 e na DT-026: nenhum tamanho de efeito é previsto. Ganhos e custos vão lado a lado, com IC, qualquer que
seja o sinal. Os números dos *smokes* da A1 e da A4 e a simulação do P1 recalibrado são registrados como contexto
medido, não como expectativa.

## 3. Células descritivas: falso alarme e custo da A4

A regra de fusão não importa onde não há fusão (sem falha, F3, F4; e no F1, em que o nó volta por JOIN, sem resolução de
conflito). Nessas células a regra fica a atual (`PREFERRED_NON_NULL`), e cada bloco tem **dois braços**, controlador
desligado × ligado com a A4, com a mesma semente:

| Célula | Blocos | Execuções | O que mede | Fase |
|---|---|---|---|---|
| ASYNC sem falha | 4 | 8 | P1 sem partição (falso alarme), indisponibilidade do *cache* que ele causa, p99 e vazão | núcleo |
| ASYNC F3 (*jitter* em `isn2`) | 4 | 8 | idem sob lentidão, sem corte | núcleo |
| ASYNC F1 (queda de `isn2`) | 4 | 8 | o P1 não pode agir com o nó morto (sem resposta REST); mede ação depois da volta e o custo | extra |
| ASYNC F4 (perda de 20 % em `isn3`) | 4 | 8 | o caso de maior risco de falso alarme da A4: o S_lag pode ir ao teto sem corte, e o *ignore* replica ao *cluster* | extra |

As SYNC de referência da DT-026 saem: a A1 só muda o `sessions-async` e a A4 só age no `sessions-async`, então elas
não respondem a nenhuma das duas perguntas.

**Execuções e orçamento** (~395 s por execução, medida do coordenador; margem de 1,25 para reposições h4, a fração do
C3 §4.4):

| Parte | Execuções | Horas | Com margem |
|---|---|---|---|
| núcleo: F2 2×2 × 12 blocos + sem falha + F3 | 48 + 8 + 8 = **64** | 7,0 h | 8,8 h |
| extras: F1 + F4 | 16 | 1,8 h | 2,2 h |
| **total** | **80** | 8,8 h | **11,0 h** |

Cabe nas ~12 h. O núcleo roda primeiro; se o tempo acabar, as extras saem sem mudar nada do teste.

Sementes: `--s0 800000`, passo 100, como a DT-026 §5; `--ordem-seed` = a data do commit que fechar as notas [A4].

## 4. A4: falso alarme, desfazer e o que se mede

**Registro do risco.** Sem partição, o `POST ignored-caches/sessions-async` no suspeito **replica a lista aos três nós
em menos de 60 ms**, e o `sessions-async` fica indisponível no *cluster* inteiro até o `DELETE` (relatório da A4 §5: 96 a
100 % de erro nos três servidores de aplicação durante ~19,5 s). A guarda R4 existe para isso, e é parte da ação.

**Regras da A4** (relatório da A4 §8), que o controlador implementa [A4]:

| Regra | O quê |
|---|---|
| R1 | um *cache* só, o `sessions-async` |
| R2 | fila única no *cluster*: nunca dois pedidos de `ignored-caches` ao mesmo tempo |
| R3 | efeito conferido pela lista (`GET /rest/v2/server/ignored-caches`), não pelo HTTP (sob corte, o 500 depois de 15 s é o normal) |
| **R4** | **guarda de falso alarme:** logo depois do POST, ler a lista dos outros dois nós por até ~1 s; se algum mostrar o *cache*, `DELETE` imediato (medido: a lista replica em 30 a 157 ms quando não há corte; nunca replicou sob corte, n = 8) |
| R5 | nenhuma chamada ao alvo que dependa do *cluster* perto da ação |
| **R6** | **desfazer, corte verdadeiro:** no alvo, assim que `get-availability` der `DEGRADED_MODE` nele (o `DENY_READ_WRITES` recusa por conta própria até a fusão) |
| **R7** | **reserva:** se o momento da R6 passar, depois da fusão, um `DELETE` a um nó da maioria |
| R8 | conferência final das três listas; na subida do controlador, ler as listas e desfazer o que for dele |

R6 e R7 foram medidas uma vez cada (n = 1); o relatório da A4 pede mais duas de cada antes da avaliação (G-3 de lá)
[A4]. O teto de espera da R7 para corte assimétrico não foi medido e é fixado na implementação, com origem, antes dos
dados [A4].

**O que se mede, por execução, nos braços ligados:** número de P1 e de POST; tempo do pedido à recusa no alvo; guarda R4
disparada (sim/não) e **tempo de indisponibilidade do *cache* no *cluster*** (do POST ao `DELETE` conferido nas três
listas); operações com erro nesse intervalo, por servidor de aplicação; regra de desfazer usada (R6, R7) e listas
vazias no fim (R8). Todo P1 nas células sem falha, F3 e F1 é contado como falso alarme; na F4, é reportado à parte (há
falha real, mas não partição).

**Sensor da carga:** a recusa do *cache* ignorado aparece na carga como erro de transporte (P-A4.4 da A4); a decisão
sobre marcá-la à parte é da implementação [A4], igual nos quatro braços.

## 5. O que o orquestrador precisa ter antes de rodar

1. **Variante de configuração por braço.** O `sessions-async.json` do braço (A1 ou `PREFERRED_NON_NULL`) escolhido por
   uma coluna nova da matriz (`regra`), com os dois arquivos no *bundle* e os dois SHA-256 registrados; o `cria-caches.sh`
   cria o do braço, e a camada 3 compara com ele. O `counters-async` fica `PREFERRED_NON_NULL` nos quatro (TD-020 da A1).
2. **O JAR da extensão** (`tcc-extensao-servidor.jar`, construído com `mvn -f extensao-servidor/pom.xml package`, porque
   o `git archive` não leva `target/`) no *bundle*, montado em `server/lib` nos três nós, **em todos os braços** (só a
   `merge-policy` muda); SHA-256 registrado; o pré-voo confere `A1-POLITICA carregada` nos três nós quando o braço é A1.
3. **Checagem de fusão antes da varredura.** O laço de convergência não espera a fusão (relatório da A1, achado 2). O
   orquestrador (ou o programa) grava o instante da fusão (seção 2.2) e só deixa a varredura rodar depois dela. Se a
   fusão não vier até o teto da convergência, a execução é tratada como **varredura com erro**, com o tratamento simétrico
   da DT-026 estendido aos quatro braços do bloco, e a perda permanente do bloco fica não avaliável.
4. **Matriz com quatro braços por bloco** na célula de teste (mesma semente, ordem sorteada) e dois nas descritivas;
   pareamento conferido no pré-voo para os dois formatos.
5. **Controlador com a A4** [A4]: o *jar* com a ação nova, o arquivo do controlador com os parâmetros da recalibração
   (`b183527a…`) e os da A4 com origem, e as linhas `acao` da A4 na trilha.
6. **Análise (W-5e):** o `comparar_bracos.py` com os contrastes da seção 1, a família de Holm m = 2, as duas medidas
   novas e os descritivos da seção 4.

## 6. Pontos [A4] e decisões abertas

| # | Ponto | Fecha com |
|---|---|---|
| A4-1 | regras R1 a R8 no código, com testes | implementação e validação da A4 |
| A4-2 | mais 2 execuções de R6 e de R7 no *cluster* local (G-3 da A4) | idem |
| A4-3 | teto da R7 para corte assimétrico, com origem | idem |
| A4-4 | marca da recusa do *cache* ignorado na carga (P-A4.4) | idem |
| A4-5 | `counters-async` fica fora da A4 (R1; G-2 da A4) | decisão do Gestor; o desenho supõe fora |

## 7. Nota (2026-11-06, antes de qualquer dado da avaliação): escolhas da análise do fatorial e a I4 com a A1

Escolhas da análise aceitas na validação independente do fatorial no `comparar_bracos.py`
(`wt-w5e/docs/validacao-w5e-2026-11-06.md`, `0583455`, seções 1, 2 e 5). Elas **completam** as seções 2 e 5 acima:

1. **§2.2, perda permanente e exposição na janela usam só detecções que contam em V.** São decomposições de V. A
   perda permanente soma a varredura (I1 REAL), o *replay* tardio e o I1 *online* com detecção em `t_fusao_ns` ou depois;
   a exposição soma o *replay* imediato e o I1 *online* antes de `t_fusao_ns`. O ***replay* imediato depois da fusão não
   entra em nenhuma das duas.** Sem o campo `t_fusao_ns`, as duas não são emitidas; com `t_fusao_ns = null` (a fusão não
   veio), ficam não avaliáveis; com a varredura com erro, a perda fica não avaliável em todos os braços do bloco, e a
   exposição, que não depende da varredura, fica.
2. **§2.1, H_V "não avaliável" por efeito.** É a regra da DT-021 levada a cada efeito: H_V do efeito da A1 é não
   avaliável quando os braços de referência dele (**PO e PL**) somam zero violação nos blocos usados; o da A4, quando
   **PO e AO** somam zero. **A rastreabilidade integral de PL e AL vale nos dois efeitos**, porque os dois braços ativos
   entram nos contrastes de ambos.
3. **§5, contrato de campos com o orquestrador fatorial.** No `execucao.json` de cada execução: `fusao` (`pnn` ou
   `a1`), `controller` (`shadow` ou `active`), `bloco` e `t_fusao_ns` (o instante da primeira vista de fusão depois do
   `end`, ou `null` se ela não veio até o teto). No manifesto de braços, os rótulos **PO, PL, AO e AL**. A matriz pode
   chamar a coluna de `regra`, desde que o `execucao.json` traga `fusao`. **Sem fusão até o teto, a execução vale como
   varredura com erro**, com o tratamento simétrico nos quatro braços do bloco (seção 5, item 3).
4. **Sem IC hierárquico do p99 no fatorial.** A IU usa o IC *t* sobre os contrastes por bloco, como na DT-021; um IC
   hierárquico seria só descritivo e não é calculado.

**I4 com a A1 (decisão do coordenador, 06/11).** O TLC do `MesclarJoin` (`wt-tla-join/docs/relatorio-tla-mesclar-join-2026-11-06.md`,
`4abd157`, E-J5) mostra que a fusão por ordem de conteúdo **troca o motivo de LOGOUT para ADMIN_BLOCK** quando o *logout* e
o bloqueio da mesma sessão acontecem em lados opostos da partição (traço de 5 estados: `Logout`, `Block`, `Particionar`,
`MesclarJoin`; a sessão segue INVALID). É consequência da própria ordem (ADMIN_BLOCK vence LOGOUT). **A definição da I4 não
muda.** Se isso acontecer na avaliação, a I4 conta em V como qualquer outra e é **relatada como custo da A1**, à parte,
nos descritivos da A1 (linhas `A1-FUSAO` em que a escolha trocou o motivo).
