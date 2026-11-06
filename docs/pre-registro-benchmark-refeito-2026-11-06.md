# Pré-registro do benchmark refeito (recalibração e avaliação): RASCUNHO (2026-11-06)

> Senior, 2026-11-06, só documentos. Branch `docs/pre-registro-benchmark-refeito` (worktree `wt-bench`), a partir da
> ponta `b33aebf` de `docs/b40-pre-registro-avaliacao`. **Rascunho:** os pontos marcados **`<A PREENCHER PELO
> PILOTO>`** dependem do piloto de violações em curso (passagens 1 e 2). Fora deles, o texto pode ser aprovado já.
> Depois da aprovação, vira entrada `DT-0xx (PRÉ-REGISTRO)` em `docs/decisoes-tecnicas.md`. Sem push.
>
> Autorização: o Gestor mandou seguir o plano ("continue executando e siga o plano"), e o coordenador pediu este
> rascunho. Fontes: o diagnóstico de 06/11 (`scratchpad/diag-v/relatorio.md`, fora do repositório; resumido na seção 1
> do roteiro do piloto), o roteiro do piloto (`wt-plano-ctrl/docs/roteiro-piloto-violacoes-2026-11-06.md`, `40eda0e`),
> o B-40 parte 1 (DT-021) e parte 2 (DT-025) e o pré-registro da calibração com os complementos 1, 2, 3 e 3a
> (DT-020, DT-022, DT-023, DT-024).

## 0. Decisões aplicadas

| Decisão | Fonte |
|---|---|
| A sonda de *replay* e a varredura são **método** (instrumentação da carga S3 que torna as violações observáveis), não desfecho novo | Gestor, 06/11, pelo coordenador |
| V continua sendo (I1 + I4 + I2 REAL) por mil ações S3; contam as detecções do auditor *online*, da sonda (`origem=sonda`) e da varredura (REAL) | idem |
| Os eventos `ReplayValidate` ficam fora de latência e vazão | idem |
| 12 blocos nas células de teste | B-3 da DT-021 (Gestor, 05/11) |
| Todo o resto do B-40 parte 1 (braços, IU, Holm, "melhoria", secundários, riscos, sem expectativa numérica de efeito) | DT-021, salvo o que a seção 5 muda |
| Regras de derivação da calibração | DT-020 com os complementos 1, 2, 3 e 3a |

## 1. Motivo

**A calibração X-18 foi cega para violações.** Nenhuma das 26 execuções teve `--sweep`, e a carga não relê uma sessão
depois do logout por outro nó: o "voltar" revalida só por 10 s e sempre no mesmo servidor de aplicação. O diagnóstico de
06/11 achou 3 logouts confirmados pelo `isn3` isolado, como dono primário, na janela não detectada (42 a 46 s) das 5
ASYNC-F2, e ninguém os leu depois que a perda passa a existir. Com isso, o q̂ = 0/5 da parte 2 (DT-025, declaração 2) é
um zero de observação, não de ocorrência. Os valores da DT-025 ficam como registro da calibração cega e são
**substituídos**, para o benchmark refeito, pelos da recalibração (seção 4).

**O piloto de violações é a etapa de seleção do ponto de operação**, não teste de hipótese. Ele é relatado **com todos
os números**: todas as condições, todas as execuções, as que deram V e as que não deram, inclusive as opcionais e a
passagem 2. Os dados do piloto não entram na recalibração nem na avaliação. Até a escrita deste rascunho, o coordenador
informa:

- **F2** (`isn3` isolado 90 s): os logouts confirmados pelo `isn3` na janela não detectada (~44 s) se perdem na fusão,
  e a varredura os vê como I1 REAL: 1 de 1 execução com a carga padrão e 2 de 3 com a carga L+, 5 chaves;
- **pausa de 50 s** (nó zumbi): I5 em 3 de 3 e I6 em 1 de 3, fora de V;
- **F4**: sem vista nova e sem fusão, então o M1 não ocorre;
- ainda rodam b1 (ARW), cd (pausa com L+) e a passagem 2 com a sonda de *replay* (W-10).

`<A PREENCHER PELO PILOTO>`: a tabela completa do piloto (V, V-varr e V-sonda por execução e por condição, chaves
distintas por classe, o que fica fora de V), com os caminhos dos brutos e os SHA-256.

## 2. Método de observação

### 2.1 Instrumentos, iguais em todas as execuções das duas campanhas e dos dois braços

| Instrumento | O que vê | Opção do programa |
|---|---|---|
| auditor *online* (existente) | I1, I4, I2 nas leituras da própria carga | — |
| **sonda de *replay*** (W-10) | para cada Logout confirmado, um `Validate` da sid antiga pelo servidor de aplicação do nó (k+1) mod 3, de `min` a `max` s depois; no fim da carga, um *replay* tardio das sids com Logout confirmado na falha ou na recuperação, por um nó diferente do que confirmou. Violações com `origem=sonda` | `--s3-replay-probe --s3-replay-min-sec 1 --s3-replay-max-sec 3 --s3-replay-late-max <A PREENCHER PELO PILOTO>` |
| **varredura final** | depois da convergência, todas as chaves dos dois *caches* contra as invalidações confirmadas: VALID ⇒ I1 REAL; motivo trocado ⇒ I4 REAL; VALID com identidade BLOCKED ⇒ I2 (REAL se administrativo); Block perdido ⇒ I2-PERDIDO (REAL, ou INCERTA com Unblock com erro depois) | `--sweep` |

Origem dos números da sonda: 1 a 3 s, o intervalo do "voltar" da carga (U(1,3) s, diagnóstico §4) e o padrão do W-10.
**`--s3-replay-late-max`**: o padrão do W-10 é 200; a regra é que ele seja **maior que o maior número de logouts
confirmados por execução no piloto com o ponto de operação escolhido**, para o *replay* tardio nunca truncar. O valor
sai do piloto. Uma execução da campanha que bater no teto é marcada e relatada.

### 2.2 O que conta em V

- **I1 e I4**: das três origens.
- **I2**: só com classificação REAL. Da varredura, o I2 administrativo REAL e o I2-PERDIDO REAL; o INCERTA fica fora.
- **Fora de V**, como na DT-002 e na DT-021: `I2_CASCATA_*`, `I2_JANELA_CASCATA`, I2 de outra classe, os contadores
  `*_concorrentes`, I5 e I6 (divergência entre réplicas no laço de convergência, desfecho secundário à parte).

### 2.3 Regra de contagem (recomendação)

**Numerador de V = número de pares distintos (chave, invariante) com ao menos uma detecção na execução, somando as três
origens.** A contagem de linhas e a contagem por origem vão ao relatório como descritivas. Denominador: ações S3 da
execução, **sem** os `ReplayValidate`.

Razões:

1. **A contagem de linhas mede quantas vezes alguém leu a sessão errada, não quantas sessões ficaram erradas.** Na r2
   do `wt-w2a`, uma única sessão deu 11 linhas de I2 REAL, porque o próprio usuário continuou validando. A frequência de
   leitura é da carga, igual nos dois braços em expectativa, mas tem variância alta e faria uma chave pesar 11 vezes.
2. **Os instrumentos se sobrepõem de propósito.** O mesmo logout perdido pode ser visto pelo *replay* tardio e pela
   varredura, e o *replay* imediato pode vê-lo antes. Contar linhas somaria a mesma perda duas ou três vezes, e a soma
   dependeria de quantos instrumentos a alcançaram, não do sistema.
3. **É a regra que o piloto já usa** para escolher o ponto de operação (roteiro do piloto §6.5: "11 linhas na mesma
   sessão contam como uma ocorrência").
4. **Uma ocorrência por chave e invariante corresponde ao que o controlador pode evitar**: o AC-5 evita ou não que uma
   sessão fique válida depois do logout; ele não muda quantas vezes o cliente a lê.

A alternativa (as duas contagens como co-principais) não é recomendada, porque dobraria a família de V e o Holm. Esta
regra muda a operacionalização de `contar_violacoes` (`analysis/comparar_bracos.py`, que hoje conta linhas do
`violacoes-rep-NNN.csv`), e precisa entrar no código antes da avaliação (seção 8).

### 2.4 Atribuição às fases

V conta as fases `durante` + `recuperacao` nas células com falha e `antes` na célula sem falha (DT-021). O auditor
*online* e o *replay* imediato são atribuídos pelo instante da detecção. **O *replay* tardio e a varredura** rodam
depois da carga, então são atribuídos pelo **instante da invalidação confirmada** da sid (o Logout ou a cascata, lido
do CSV de eventos pela chave). Uma detecção cuja invalidação não puder ser localizada no CSV **conta** na execução, sem
filtro de fase, e vai ao relatório. Essa reserva é igual nos dois braços.

### 2.5 As duas formas de I1

| Forma | O que é | Quem vê | Natureza |
|---|---|---|---|
| **1** | Logout confirmado pelo nó isolado, como dono primário, na janela não detectada; a réplica para a maioria se perde, e a fusão fica com o VALID da maioria | varredura, *replay* tardio | perda latente: o estado final está errado |
| **2** | Logout confirmado pela maioria, relido **VALID pelo nó isolado** durante a janela (ele ainda guarda a cópia antiga) | *replay* imediato cruzado, quando o nó (k+1) é o isolado; "voltar" de um usuário preso ao isolado | exposição transitória: o cliente aceita a sessão depois do logout; o estado final pode estar certo |

**Recomendação: as duas formas contam em V**, com o subtipo registrado. As duas são I1 pela definição que o auditor já
aplica (logout confirmado seguido de leitura VALID da mesma sid), e a forma 2 é exatamente o reuso de sessão depois do
logout que a literatura de segurança de sessão descreve (o cenário de NIST/OWASP citado no diagnóstico; a seção exata
fica com o Bibliotecário). O relatório dá V por forma e uma **sensibilidade sem a forma 2**. Regra de classificação:
forma 2 quando a leitura VALID foi servida pelo nó da falha entre o marcador `start` e a detecção e o Logout da sid foi
confirmado por outro nó; forma 1 nos demais achados de I1 depois de um Logout confirmado pelo nó da falha. A coluna
`replica` do CSV dá o servidor de aplicação, que no modo `pinned` é o próprio nó.

### 2.6 `ReplayValidate`

Fora de latência (p99 da ação S3 e por operação), de vazão útil, da taxa de erro e do denominador de V. Eles entram só
na contagem de V pela origem `sonda` e num descritivo de custo da sonda (leituras extras por execução). A referência de
p99 (regra 7.1) não os inclui, porque o `referencia_p99.py` agrupa por `operation`, e `ReplayValidate` é uma operação
própria, que precisa ficar fora da lista do sinal de latência do controlador (conferir no W-9, seção 8).

## 3. Ponto de operação

**`<A PREENCHER PELO PILOTO>`**: a condição escolhida, com os parâmetros da carga e da falha.

Regra de escolha, do roteiro do piloto §6.5, fixada antes da leitura da passagem 2:

1. **Útil** = V ≥ 1 em pelo menos 2 execuções da condição, com ao menos 2 chaves distintas no total (11 linhas na mesma
   sessão contam como uma ocorrência).
2. Entre as úteis, a de mais execuções com V ≥ 1; empate, a de mais chaves distintas por execução; empate, a que menos
   muda o sistema (pausa e carga antes de trocar a configuração do *cluster*: c50, d e cd antes de b1 e b2).
3. Sem nenhuma útil por V, a mesma ordem com V + V-varr, marcada como "só latente".
4. As duas primeiras pela regra vão para a passagem 2; a escolha final usa a passagem 2, com a sonda.

**Favorita: F2 + L+** (condição d). Carga L+, do roteiro do piloto §3: `--s3-users 40` (13 usuários presos ao `isn3`
em vez de 6), `--s3-think-ms 500` (o dobro de páginas), `--s3-session-median-sec 10` (o dobro de sessões),
`--s3-logout-prob 0.9` (+29 % de logouts por sessão), `--s3-admin-mean-sec 5` (3 vezes mais Blocks). **A carga
escolhida vale para todas as células das duas campanhas**, para que as células continuem comparáveis entre si e a
calibração descreva o mesmo regime da avaliação.

Se o ponto escolhido trocar a configuração do *cluster* (b1 ou b2), isso muda o sistema medido e exige decisão do
Gestor antes da recalibração, porque a avaliação deixaria de medir a configuração de produção declarada.

## 4. Recalibração

- **Regras:** as mesmas da seção 7 do pré-registro da calibração, com os complementos 1, 3 e 3a, a regra de memória do
  complemento 2 e o critério C-8 (zero ocorrências, 3/N reportado). Nenhuma regra muda.
- **Células:** as mesmas da X-18 (ASYNC sem falha × 5, F1 × 5, F2 × 5, F4 × 5; SYNC F2 × 3; SYNC sem falha × 3), com o
  ponto de operação da seção 3 e os instrumentos da seção 2.
- **Pausa como célula:** **entra** (ASYNC pausa × 5) **se, e só se, a avaliação tiver a célula da pausa** (seção 5),
  `<A PREENCHER PELO PILOTO>`. Ela alimenta m, `T_estavel` e o maior Δ_conv com a dinâmica do zumbi; os limiares saem
  só das execuções sem falha, como antes.
- **λ₅ e W mudam com a carga.** Com L+ há mais eventos elegíveis por segundo, e a 7.3 dá outra W. Nenhum número é
  antecipado aqui: W, a viabilidade do AC-5 com `max(k, kLag)` (complemento 3) e o resto saem da derivação.
- **Sementes novas, disjuntas de todas as já usadas:** *rebaseline* até 151 100; X-18 a partir de 300 000; avaliação do
  B-40 a partir de 400 000; piloto de 610 000 a ~670 000. Recalibração: `--s0 700000`, passo 100; `--ordem-seed` = a
  data do commit que aprovar este pré-registro, em AAAAMMDD.
- **Saída:** uma nova parte 2 (valores recalibrados), pela mesma conferência independente contra os brutos, antes da
  primeira noite da avaliação refeita. A DT-025 não é editada; a nova parte 2 a substitui para o benchmark refeito.

## 5. Avaliação: o que muda em relação ao B-40 parte 1 (DT-021)

| Célula | Na DT-021 | Benchmark refeito | Razão |
|---|---|---|---|
| ASYNC F2 | teste, 12 blocos | **teste, 12 blocos**, no ponto de operação | o mecanismo M1 ocorre e é observado (seção 1) |
| ASYNC F4 | teste, 12 blocos | **descritiva, 4 blocos** | sem vista nova e sem fusão, o M1 não ocorre; não há hipótese de redução a testar, pela mesma lógica que tirou a F1 do teste |
| ASYNC F1 | descritiva, 4 | descritiva, 4 | sem mudança |
| ASYNC sem falha | descritiva, 8 | descritiva, 8 | sem mudança |
| ASYNC F3 | descritiva, 4 | descritiva, 4 | sem mudança |
| SYNC F2, SYNC F4 | referência, 4 cada | referência, 4 cada | sem mudança |
| **ASYNC pausa** (zumbi) | — | **descritiva, 4 blocos**, `<A PREENCHER PELO PILOTO>` | o P1 não age na pausa: durante o congelamento o suspeito não responde à REST, e na volta as vistas divergem (diagnóstico §6); não há hipótese de redução, mas a célula mede o que o controlador faz e o dano do zumbi |

- **Holm sobre 1** (só ASYNC F2): a célula rejeita com `p_IU` ≤ 0,05, o que exige k ≥ 6 blocos com d_b ≠ 0
  (2/2⁶ = 0,031; plano de alinhamento §3.3). A regra de "melhoria" (IU, vazão útil não inferior, rastreabilidade
  integral) não muda.
- **Execuções:** F2 24, F4 8, F1 8, sem falha 16, F3 8, SYNC F2 8, SYNC F4 8: **80**; com a pausa, **88**.
- **V** passa a ser contado pela regra da seção 2.3, com a sensibilidade sem a forma 2.
- **Sementes:** `--s0 800000`, passo 100; `--ordem-seed` = a data do commit da nova parte 2.
- O resto da DT-021 vale como está: braços, piloto embutido nos 3 primeiros blocos, secundários, declaração de que não
  há expectativa numérica de efeito, riscos, regras de perda (com as da DT-025 sobre o B1, N1, R4 e O1).

## 6. Riscos conhecidos antes dos dados (sem números de efeito)

1. **O AC-5 só tira o trecho final da janela de risco.** O P1 precisa de `max(k, kLag)` janelas com a defasagem no teto
   (k = 2) e depois da parada efetiva do nó, cerca de 13 s depois do pedido (P-42). Os logouts confirmados pelo nó isolado
   nos primeiros segundos da janela continuam perdidos (forma 1), e as releituras feitas antes da parada continuam
   expostas (forma 2). A redução possível é limitada por construção à parte da janela depois da parada.
2. **V *online* no instante da detecção (condição d, execução 2 do piloto), em diagnóstico.** Se for leitura defasada
   servida pelo nó isolado na troca de vista, é forma 2 e conta. Se for artefato da instrumentação, a regra de contagem
   precisa excluí-la **antes** da recalibração, por emenda a este pré-registro. `<A PREENCHER PELO PILOTO>`.
3. A sonda soma leituras à carga (estimativa do diagnóstico: 2 a 4 % dos eventos), igual nos dois braços. O custo é
   medido e relatado.
4. A forma 1 depende de qual nó é o dono primário da sid, que o CSV não grava; a atribuição de forma usa o nó que
   confirmou o Logout, não o dono.
5. Parar um nó reduz a capacidade de atendimento até o fim da execução; a vazão útil é medida (DT-021).
6. Com a carga L+, os sinais do controlador mudam (λ₅, W, ruído da defasagem). Isso sai da recalibração, sem ajuste à mão.

## 7. Pontos pendentes

| # | Ponto | Quando |
|---|---|---|
| P-1 | Ponto de operação (seção 3) e tabela completa do piloto (seção 1) | fim do piloto |
| P-2 | Pausa como célula, na recalibração e na avaliação | fim do piloto |
| P-3 | `--s3-replay-late-max` | fim do piloto |
| P-4 | Diagnóstico da V *online* da condição d, execução 2 (risco 2) | antes da recalibração |
| P-5 | Confirmação do Gestor para a regra de contagem (2.3) e para a forma 2 em V (2.5) | antes da recalibração |

## 8. O que precisa de código antes da recalibração

- **W-10 integrado** à ponta da campanha, com o SHA-256 do *jar* registrado.
- **Orquestrador:** passar `--sweep` e as opções da sonda na linha do programa pela própria campanha, gravadas no
  `execucao.json` e no arquivo de parâmetros com origem, em vez do *wrapper* de `JAVA` que o piloto usou.
- **`comparar_bracos.py` (W-5c):** V pela regra 2.3 (pares distintos das três origens, varredura incluída, atribuição
  da 2.4); forma 1 e forma 2 (2.5); `ReplayValidate` fora de latência, vazão, erro e denominador; sensibilidade sem a
  forma 2.
- **`calibracao_s3.py`:** conferir que `ReplayValidate` fica fora de λ₅, da excedência e da referência de p99.
