# Pré-registro do benchmark refeito (recalibração e avaliação): APROVADO (2026-11-06)

> Senior, 2026-11-06, só documentos. Branch `docs/pre-registro-benchmark-refeito` (worktree `wt-bench`), a partir da
> ponta `b33aebf` de `docs/b40-pre-registro-avaliacao`. Rascunho em `a5ddac9`; preenchido com o resultado do piloto
> e as decisões P-1 a P-5 do coordenador, sob a autorização do Gestor para seguir o plano durante a noite (ele revisa
> ao acordar). **Aprovado** e registrado como **DT-026 (PRÉ-REGISTRO)** em `docs/decisoes-tecnicas.md`, antes de
> qualquer dado da recalibração. Sem push.
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
passagem 2. Os dados do piloto não entram na recalibração nem na avaliação.

Resultado do piloto (brutos em `dados-vm/piloto-2026-11-06/p-20261106T081543Z/piloto/`, tar `80a79265…` conferido pelo coordenador;
`resumo-geral.tsv`, `perdas-geral.tsv`, `diario.txt`, `runs/<cond>/`). V = I1 + I4 + I2 REAL; "chaves" = sids distintas
com detecção; todas as execuções ASYNC, braço A, `isn3` alvo, injeção a +49 s, `--sweep` em todas.

| Condição | O que é | Execuções | V *online* (execuções com V) | V-sonda | V-varredura | Alguma V | Fora de V |
|---|---|---|---|---|---|---|---|
| c50 | pausa de 50 s (zumbi), carga padrão | 3 | 0/3 | — | 0/3 | **0/3** | I5 em 3/3, I6 em 1/3, I2_JANELA_CASCATA |
| d | F2 DENY 90 s, carga L+ | 3 | 1/3 (I1 = 2, I2 = 2, 1 chave) | — | 2/3 (I1, 5 chaves) | **3/3** | I2_CASCATA_*, I2_JANELA_CASCATA |
| b1 | F2 com ARW + `PREFERRED_ALWAYS`, carga padrão | 2 (as duas DESCARTADA-INFRA) | 0/2 | — | 2/2 (I1, 3 chaves) | 2/2 | I5 em 1/2, I2_CASCATA_* |
| cd | pausa de 50 s com L+ | 2 | 0/2 | — | 0/2 | **0/2** | I5 em 2/2, I2_JANELA_CASCATA |
| **p2-d** | d com a sonda de *replay* (W-10) | 3 (1 DESCARTADA-INFRA) | 1/3 (I1, 2 chaves) | 3/3 (I1, 9 chaves) | 1/3 (I1, 3 chaves) | **3/3, 9 chaves distintas** | I2_JANELA_CASCATA |
| p2-b1 | b1 com a sonda | 3 (1 DESCARTADA-INFRA) | 1/3 | 3/3 (1 chave por execução) | 2/3 | 3/3 | I2_JANELA_CASCATA |
| manual-f2 | F2 DENY com `--sweep`, carga padrão, semente 310200 (coordenador; evidência, fora da contagem) | 1 | 0/1 | — | 1/1 (I1, 1 chave) | 1/1 | I2_CASCATA_* |

As opcionais c25, b2 e e não rodaram. Nas pausas (c50 e cd), **0 V em 5 execuções**: o dano do zumbi apareceu só como
divergência entre réplicas (I5/I6), fora de V. Na F4 da calibração não houve vista nova nem fusão, e o M1 não ocorre.

**Instantes das 11 perdas da forma 1 em DENY** (logouts confirmados pelo `isn3` e achados VALID depois: d 5, p2-d 5,
manual-f2 1; `perdas-geral.tsv`): de +2,4 a +20,1 s depois do marcador `start`, mediana +5,9 s. A detecção da partição
pelo *cluster* veio entre +41,9 e +47,3 s. As 4 detecções da forma 2 na p2-d são logouts confirmados pelo `isn2` e
relidos VALID pelo `isn3` 1,7 a 2,8 s depois (`080dcce8`, `0d7f4919`, `49f6cb15`, `44567bcb`).

**A sonda tardia bateu no teto de 200 nas três p2-d**: 195 a 198 sids relidas duas vezes, contra 212 a 238 logouts
confirmados desde o `start` (`runs/p2-d/exec-*/rep-001.csv`). O *replay* tardio foi amostrado ali, e é por isso que o
`--s3-replay-late-max` da campanha sai da regra da seção 2.1, não do padrão.

## 2. Método de observação

### 2.1 Instrumentos, iguais em todas as execuções das duas campanhas e dos dois braços

| Instrumento | O que vê | Opção do programa |
|---|---|---|
| auditor *online* (existente) | I1, I4, I2 nas leituras da própria carga | — |
| **sonda de *replay*** (W-10) | para cada Logout confirmado, um `Validate` da sid antiga pelo servidor de aplicação do nó (k+1) mod 3, de `min` a `max` s depois; no fim da carga, um *replay* tardio das sids com Logout confirmado na falha ou na recuperação, por um nó diferente do que confirmou. Violações com `origem=sonda` | `--s3-replay-probe --s3-replay-min-sec 1 --s3-replay-max-sec 3 --s3-replay-late-max 268` |
| **varredura final** | depois da convergência, todas as chaves dos dois *caches* contra as invalidações confirmadas: VALID ⇒ I1 REAL; motivo trocado ⇒ I4 REAL; VALID com identidade BLOCKED ⇒ I2 (REAL se administrativo); Block perdido ⇒ I2-PERDIDO (REAL, ou INCERTA com Unblock com erro depois) | `--sweep` |

Origem dos números da sonda: 1 a 3 s, o intervalo do "voltar" da carga (U(1,3) s, diagnóstico §4) e o padrão do W-10.
**`--s3-replay-late-max` = 268.** Regra: acima do maior número de logouts confirmados por execução no piloto, para o
*replay* tardio não truncar. Contagem nos brutos (`Logout` com `return_code` = `NONE` em `runs/<cond>/exec-*/rep-001.csv`,
todas as 16 execuções da VM): o maior é **267**, na `cd/exec-0001` (carga L+); na F2 com L+ (d e p2-d), o maior é 242
(`p2-d/exec-0001`). 267 + 1 = 268. O padrão do W-10 (200) foi atingido nas p2-d (seção 1). Uma execução da campanha que
bater no teto (`tardias_candidatas` ≥ 268 no `stdout`) é marcada e relatada (V-W5c §4, item 13).

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

A alternativa (as duas contagens como co-principais) não é recomendada, porque dobraria a família de V e o Holm.

**Decisão (P-5, coordenador, 06/11): V por par distinto (chave, invariante)**, somando as três origens, com a forma 2
contando e a sensibilidade `:sem_forma2`. A regra **substitui a contagem por linhas da DT-021** em toda execução, também
nas sem instrumentos (V-W5c §4, item 1); está implementada no W-5c e no W-5d (`wt-w5c`, validação
`docs/validacao-w5c-2026-11-06.md`, adendo `dba7320`) e no W-9 para a recalibração (V-W5c §7.2).

**Nota (V-W5c §4, item 14):** o mesmo `Block` perdido aparece como I2-PERDIDO na identidade e como I1 `ADMIN_BLOCK` em
cada sid da cascata. São pares diferentes pela regra acima, e isso não é dupla contagem escondida: um é o bloqueio que
sumiu, os outros são as sessões que ficaram válidas.

**Varredura com erro: tratamento simétrico** (V-W5c §5, W-5d). No bloco em que a varredura falhar em **qualquer** braço,
V e `:sem_forma2` dos **dois** braços usam só o auditor *online* e a sonda. O bloco fica no teste, os dois braços ficam
medidos com o mesmo instrumento, e, sob a hipótese nula exata, o teste por troca de sinal continua exato. O V desses
blocos fica subestimado nos dois braços (declarado). O relatório dá o número de blocos afetados por célula e por braço
da falha, os dois V (com e sem a varredura, onde ela existiu) e uma **sensibilidade sem esses blocos**, descritiva, sem p
e sem Holm. Excluir o bloco foi rejeitado, porque condicionaria a análise a uma variável posterior ao tratamento.

### 2.4 Atribuição às fases

V conta as fases `durante` + `recuperacao` nas células com falha e `antes` na célula sem falha (DT-021). O auditor
*online* e o *replay* imediato são atribuídos pelo instante da detecção. **O *replay* tardio e a varredura** rodam
depois da carga, então são atribuídos pelo **instante da invalidação confirmada** da sid (o Logout ou a cascata, lido
do CSV de eventos pela chave). Uma detecção cuja invalidação não puder ser localizada no CSV **conta** na execução, sem
filtro de fase, e vai ao relatório (`sem_invalidacao_localizada`). Essa reserva é igual nos dois braços.

Leituras aceitas da V-W5c §4, por item: (3) *replay* tardio = detecção da sonda depois do último `end_ns` da carga; (4) o
instante da invalidação é o `end_ns` da confirmação, o instante em que o cliente soube dela (a fase dos eventos anômalos
continua pelo início); (5) a cascata (motivo `ADMIN_BLOCK`) não tem evento pela sid, porque a chave do `Block` é a
identidade, e cai na reserva "conta sem filtro"; (6) I2 e I2-PERDIDO são atribuídos pelo último `Block` confirmado
(`NONE`) da identidade. Os itens 2, 9, 11, 12 e 13 são de implementação e foram aceitos como o W-5c fez (o 11 é a
tolerância de casamento de relógio de 1 s, com origem medida de 7,4 ms no *smoke*).

### 2.5 As duas formas de I1

| Forma | O que é | Quem vê | Natureza |
|---|---|---|---|
| **1** | Logout confirmado pelo nó isolado, como dono primário, na janela não detectada; a réplica para a maioria se perde, e a fusão fica com o VALID da maioria | varredura, *replay* tardio | perda latente: o estado final está errado |
| **2** | Logout confirmado pela maioria, relido **VALID pelo nó isolado** durante a janela (ele ainda guarda a cópia antiga) | *replay* imediato cruzado, quando o nó (k+1) é o isolado; "voltar" de um usuário preso ao isolado | exposição transitória: o cliente aceita a sessão depois do logout; o estado final pode estar certo |

**Recomendação: as duas formas contam em V**, com o subtipo registrado. As duas são I1 pela definição que o auditor já
aplica (logout confirmado seguido de leitura VALID da mesma sid), e a forma 2 é exatamente o reuso de sessão depois do
logout que a literatura de segurança de sessão descreve (o cenário de NIST/OWASP citado no diagnóstico; a seção exata
fica com o Bibliotecário). O relatório dá V por forma e uma **sensibilidade sem a forma 2**. Regra de classificação:
forma 2 quando a leitura VALID foi servida pelo nó da falha **entre os marcadores `start` e `end`** e o Logout da sid
foi confirmado por outro nó; forma 1 nos demais achados de I1 depois de um Logout confirmado pelo nó da falha. A janela
usa os marcadores porque o instante da detecção pelo *cluster* não está nos CSV, e com `DENY_READ_WRITES` o isolado recusa
leitura depois dela (V-W5c §4, item 7). **Um par só é forma 2 se todas as suas detecções contadas forem forma 2**: se a
varredura o achou REAL, o estado final ficou errado e a exposição não foi só transitória (item 8).

**P-4 (coordenador, 06/11):** a V *online* da execução 2 da condição d do piloto é **I1 REAL legítima**, da classe
"leitura defasada na troca de vista". Ela conta pela regra acima. A coluna
`replica` do CSV dá o servidor de aplicação, que no modo `pinned` é o próprio nó.

### 2.6 `ReplayValidate`

Fora de latência (p99 da ação S3 e por operação), de vazão útil, da taxa de erro e do denominador de V. Eles entram só
na contagem de V pela origem `sonda` e num descritivo de custo da sonda (leituras extras por execução). A referência de
p99 (regra 7.1) não os inclui, porque o `referencia_p99.py` agrupa por `operation`, e `ReplayValidate` é uma operação
própria, que precisa ficar fora da lista do sinal de latência do controlador (conferir no W-9, seção 8).

## 3. Ponto de operação

**Ponto de operação (P-1, coordenador, 06/11): p2-d.** F2 com o `isn3` isolado por 90 s, `DENY_READ_WRITES` +
`PREFERRED_NON_NULL` (a configuração de produção declarada), carga L+, e `--sweep --s3-replay-probe` em todas as execuções
dos dois braços. Origem: **seleção pela regra 6.5 do piloto** (abaixo). A p2-d e a p2-b1 foram as duas úteis da
passagem 2, as duas com V em 3 de 3; a p2-d tem mais chaves distintas por execução (9 em 3 contra 1 por execução) e não
muda a configuração do *cluster*. **A b1 (ARW + `PREFERRED_ALWAYS`) fica registrada como alternativa não adotada**: exige
trocar a configuração dos *caches*, e a regra prefere a menor mudança.

Regra de escolha, do roteiro do piloto §6.5, fixada antes da leitura da passagem 2:

1. **Útil** = V ≥ 1 em pelo menos 2 execuções da condição, com ao menos 2 chaves distintas no total (11 linhas na mesma
   sessão contam como uma ocorrência).
2. Entre as úteis, a de mais execuções com V ≥ 1; empate, a de mais chaves distintas por execução; empate, a que menos
   muda o sistema (pausa e carga antes de trocar a configuração do *cluster*: c50, d e cd antes de b1 e b2).
3. Sem nenhuma útil por V, a mesma ordem com V + V-varr, marcada como "só latente".
4. As duas primeiras pela regra vão para a passagem 2; a escolha final usa a passagem 2, com a sonda.

**Carga L+**, com os valores do piloto (roteiro do piloto §3; origem: seleção pela regra 6.5): `--s3-users 40` (13 usuários presos ao `isn3`
em vez de 6), `--s3-think-ms 500` (o dobro de páginas), `--s3-session-median-sec 10` (o dobro de sessões),
`--s3-logout-prob 0.9` (+29 % de logouts por sessão), `--s3-admin-mean-sec 5` (3 vezes mais Blocks). **A carga
escolhida vale para todas as células das duas campanhas**, para que as células continuem comparáveis entre si e a
calibração descreva o mesmo regime da avaliação.

A configuração do *cluster* não muda (XML sem *caches* e os seis JSON da DT-020).

## 4. Recalibração

- **Regras:** as mesmas da seção 7 do pré-registro da calibração, com os complementos 1, 3 e 3a, a regra de memória do
  complemento 2 e o critério C-8 (zero ocorrências, 3/N reportado). Nenhuma regra muda.
- **Células:** as mesmas da X-18 (ASYNC sem falha × 5, F1 × 5, F2 × 5, F4 × 5; SYNC F2 × 3; SYNC sem falha × 3), com a
  carga L+ da seção 3 e os instrumentos da seção 2 (`--sweep --s3-replay-probe --s3-replay-late-max 268`).
- **Pausa: fora** (P-2, coordenador, 06/11). No piloto deu 0 V em 5 execuções (c50 e cd), só I5/I6, e o P1 não age
  no nó congelado (sem resposta REST) nem na volta (vistas divergentes). Ela é relatada como resultado do piloto.
- **Total: 26 execuções**, as mesmas células da X-18, com a carga L+ e os instrumentos.
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
| ASYNC pausa (zumbi) | — | **fora** (P-2) | 0 V em 5 execuções no piloto, e o P1 não age no nó congelado nem na volta (diagnóstico §6); relatada como resultado do piloto |

- **Holm sobre 1** (só ASYNC F2): a célula rejeita com `p_IU` ≤ 0,05, o que exige k ≥ 6 blocos com d_b ≠ 0
  (2/2⁶ = 0,031; plano de alinhamento §3.3). A regra de "melhoria" (IU, vazão útil não inferior, rastreabilidade
  integral) não muda.
- **Execuções:** F2 24, F4 8, F1 8, sem falha 16, F3 8, SYNC F2 8, SYNC F4 8: **80**. Todas com a carga L+ e os
  instrumentos da seção 2, nos dois braços.
- **V** passa a ser contado pela regra da seção 2.3, com a sensibilidade sem a forma 2.
- **Sementes:** `--s0 800000`, passo 100; `--ordem-seed` = a data do commit da nova parte 2.
- O resto da DT-021 vale como está: braços, piloto embutido nos 3 primeiros blocos, secundários, declaração de que não
  há expectativa numérica de efeito, riscos, regras de perda (com as da DT-025 sobre o B1, N1, R4 e O1).

## 6. Riscos conhecidos antes dos dados (sem números de efeito)

1. **O AC-5 só tira o trecho final da janela de risco.** O P1 precisa de `max(k, kLag)` janelas com a defasagem no teto
   (k = 2) e depois da parada efetiva do nó, cerca de 13 s depois do pedido (P-42). Os logouts confirmados pelo nó isolado
   nos primeiros segundos da janela continuam perdidos (forma 1), e as releituras feitas antes da parada continuam
   expostas (forma 2). A redução possível é limitada por construção à parte da janela depois da parada.
2. **Só um AC-5 muito precoce corta perdas da forma 1.** Evidência medida no piloto, não previsão: das 11 perdas da
   forma 1 em DENY (seção 1), as que um nó parado em +t s depois do `start` teria evitado (logout confirmado depois de
   +t) são:

   | Parada efetiva do nó em | +2 s | +5 s | +10 s | +20 s | detecção (+41,9 a +47,3 s) |
   |---|---|---|---|---|---|
   | perdas da forma 1 evitáveis | 11/11 | 7/11 | 3/11 | 1/11 | 0/11 |

   O AC-5 chega, no melhor caso, em `max(k, kLag)` janelas mais ~13 s depois do corte (risco 1). Esta tabela não prevê
   o efeito da avaliação: ela mostra onde, na janela, as perdas medidas caíram.
3. A sonda soma leituras à carga (estimativa do diagnóstico: 2 a 4 % dos eventos), igual nos dois braços. O custo é
   medido e relatado.
4. A forma 1 depende de qual nó é o dono primário da sid, que o CSV não grava; a atribuição de forma usa o nó que
   confirmou o Logout, não o dono.
5. Parar um nó reduz a capacidade de atendimento até o fim da execução; a vazão útil é medida (DT-021).
6. Com a carga L+, os sinais do controlador mudam (λ₅, W, ruído da defasagem). Isso sai da recalibração, sem ajuste à mão.

## 7. Pontos que dependiam do piloto: decisões (coordenador, 06/11, sob a autorização do Gestor)

| # | Ponto | Decisão |
|---|---|---|
| P-1 | Ponto de operação | **p2-d**: F2 `isn3` DENY 90 s, carga L+, `--sweep --s3-replay-probe` em todas as execuções; b1 registrada como alternativa não adotada (seção 3) |
| P-2 | Pausa como célula | **fora** da recalibração e da avaliação; relatada como resultado do piloto, com I5/I6 |
| P-3 | `--s3-replay-late-max` | **268** (maior número de logouts confirmados por execução no piloto, 267, mais 1; seção 2.1) |
| P-4 | V *online* da d, execução 2 | **I1 REAL legítima**, da classe "leitura defasada na troca de vista" (seção 2.5) |
| P-5 | Regra de contagem e forma 2 | **V por par distinto (chave, invariante)**, três origens, forma 2 contando, sensibilidade `:sem_forma2`, leituras aceitas da V-W5c §4 e tratamento simétrico da varredura com erro (seções 2.3 a 2.5) |

## 8. O que precisa de código antes da recalibração

- **W-10 integrado** à ponta da campanha, com o SHA-256 do *jar* registrado.
- **Orquestrador:** passar `--sweep` e as opções da sonda na linha do programa pela própria campanha, gravadas no
  `execucao.json` e no arquivo de parâmetros com origem, em vez do *wrapper* de `JAVA` que o piloto usou.
- **`comparar_bracos.py`:** feito no W-5c e no W-5d e validado (V-W5c com adendo, `dba7320`): V pela regra 2.3,
  atribuição da 2.4, formas da 2.5, `ReplayValidate` fora, sensibilidade sem a forma 2 e tratamento simétrico da
  varredura com erro. Falta a integração na ponta da campanha.
- **`calibracao_s3.py`:** V por pares já entrou (V-W5c §7.2); conferir na integração que `ReplayValidate` fica fora de
  λ₅, da excedência e da referência de p99.
