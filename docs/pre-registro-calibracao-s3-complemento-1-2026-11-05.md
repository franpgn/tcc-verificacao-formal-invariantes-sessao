# Complemento 1 ao pré-registro da calibração S3: leituras das regras da seção 7 (2026-11-05)

> Senior, 2026-11-05, ~23:00Z, **antes de qualquer dado da calibração** (o X-18 está previsto para ~01:30Z de 06/11).
> Só documentos. Complementa a DT-020 (`docs/pre-registro-calibracao-s3-2026-11-05.md`, versões `1e4ff2c` e
> `2c8b7b8`) e é registrado como **DT-022**. Pela regra de `docs/decisoes-tecnicas.md`, o documento original não é
> editado além de uma nota de remissão; onde este complemento e a seção 7 divergirem, vale este.
>
> Origem: ambiguidades A-1 a A-17 que o W-9 achou ao implementar as regras (`wt-w9/docs/relatorio-w9-2026-11-05.md`
> §5 e §6; código `analysis/calibracao_s3.py`, branch `feature/w9-calibracao-s3`, ponta `4bcca11`). Semântica do
> controlador citada do código em `17f2bd4`: `Controlador.java:91-99` (alto: taxa ≥ θ_in; calmo: taxa ≤ θ_out ou
> indeterminada; entre os dois, nem alto nem calmo), `SondaDefasagem.java:215-216` (ALTA ≥ `lagInMs`, CALMA ≤
> `lagOutMs`, senão INTERMEDIARIA), `SondaDefasagem.java:559` (`atraso ≤ período − teto`) e a validação do W-2a que
> exige `hotrod_timeout_ms` < W.

> **Nota de remissão (2026-11-06, antes de qualquer dado):** o **complemento 3**
> (`docs/pre-registro-calibracao-s3-complemento-3-2026-11-06.md`, DT-024) corrige a viabilidade da §4.3 para
> `max(k, kLag_min)·W' + 13,3 < 39,8` (R-1), fixa a latência "sem cobertura" (R-2), corta no `end` o trecho do m da §3
> (R-3) e corrige a §4.1: com W < 5 s, pular janelas sem rodada **subestima** `kLag` e m (R-4). O texto abaixo não foi
> editado.

## 1. Ordem de cálculo

A ordem resolve o ciclo entre m e θ_out (A-4) e deixa W antes de tudo:

1. W e `kLag` (seção 4: 7.3, 7.13 e a regra de reserva), que só dependem de vazão, atraso das rodadas, defasagem e
   topologia;
2. referências de p99 (7.1) e excedência por janela (7.5);
3. θ_lat_in e θ_err_in (7.7), `lag_in` (7.11);
4. m (7.8, seção 3), que usa só θ_in e `lag_in`;
5. θ_out e `lag_out` (7.8 e 7.12, seção 2), que usam m;
6. `T_cooldown` = m·W, `T_estavel`, `T_hold_viol`, teto da convergência, `exec_teto_s`, volume, `T_exec`, q̂.

## 2. A-3: θ_out e `lag_out` (7.8 e 7.12)

**O defeito.** "θ_out = maior valor tal que nenhuma janela fique entre θ_out e θ_in por mais de m janelas seguidas"
não restringe nada: o maior θ_out abaixo de θ_in deixa a faixa intermediária vazia, a condição vale sempre e a
histerese desaparece.

**A intenção** (plano de alinhamento §3.6; histerese do plano de 22/10 §3.3): a **maior histerese** que, sem falha,
não prende o controlador. Em CONSERVADOR, uma janela intermediária (nem alta, nem calma) zera a contagem de janelas
calmas (`Controlador.java:127-129`), e a saída exige m calmas seguidas. A faixa intermediária deve ser a mais larga
possível desde que, nas execuções sem falha, nenhum trecho de janelas intermediárias passe de m.

**Regra emendada.**

- **θ_out** (latência e erro, cada um): o **menor** valor c no conjunto {0} ∪ {valores observados do sinal abaixo de
  θ_in} tal que, nas janelas das execuções ASYNC sem falha, nenhum trecho de janelas seguidas com c < sinal < θ_in tem
  mais de m janelas. Janela com sinal indeterminado conta como calma, como no código, e quebra o trecho. A condição é
  monótona em c (subir c só estreita a faixa), então o menor valor existe e está no conjunto finito acima. Igualdade
  com c conta como calma (`≤ θ_out`).
- **`lag_out`**: a mesma regra sobre o `lagMaxMs` da janela, com `lag_in` no lugar de θ_in.
- O valor literal antigo (maior valor) não é usado. O W-9 já calcula este como `alternativa_menor_valor`; ele passa a
  ser o `valor`.

## 3. A-5 e A-4: m (7.8)

- **"Alta" = algum sinal alto na janela**, isto é, `algumAlto` do `Controlador` (`:98`): violação, topologia anômala,
  latência ≥ θ_lat_in, erro ≥ θ_err_in ou defasagem ALTA. Não é o disparo de R1/R2. Motivo: em CONSERVADOR, qualquer
  janela alta zera as calmas (R3), e é isso que m precisa cobrir.
- **"Calma" para m = janela sem nenhum sinal alto** (não usa θ_out). Isso quebra o ciclo com θ_out (A-4) e é
  conservador: o trecho "sem alto" é igual ou mais longo que o trecho "calmo" do código, então m sai igual ou maior.
- **"Seguido de nova janela alta" = imediatamente seguido.** Com a definição acima, todo trecho máximo sem alto que não
  chega ao fim das janelas termina, por construção, numa janela alta.
- **Regra emendada:** m = 1 + L, com L = o maior comprimento de um trecho máximo de janelas sem sinal alto que começa
  em ou depois do marcador `end` e termina numa janela alta, nas execuções **ASYNC** F1, F2 e F4 aceitas. Trechos que
  chegam ao fim das janelas não contam. Sem nenhum trecho assim, L = 0 e m = 1. Sem nenhuma janela depois do `end`, m não
  é derivado (o W-9 já faz assim, `d329e21`), e isso é declarado.

## 4. A-1, A-17 e a observação sobre W e `kLag` (7.3, 7.12, 7.13)

### 4.1 Grade de janelas

Na calibração a sonda S_lag roda a cada 5 s (o W provisório); na avaliação, o período da sonda é o W derivado, uma
rodada por janela (plano de 04/11 §4.6). **Todas as regras usam a grade do W derivado**, alinhada em `origem_ns`, só com
janelas completas até `t_fim_carga_ns` (A-8). A defasagem de cada janela é o resumo das rodadas que começam nela, pela
mesma função do código (`SondaDefasagem.resumir`: `lagMaxMs`, `noSuspeito` único, nível). Com W > 5 s, a janela pode
ter mais de uma rodada e o máximo delas, o que torna a contagem de teto e de ALTA **mais conservadora** que na avaliação.
Com W < 5 s, algumas janelas ficam sem rodada; essas janelas são **puladas** (não quebram nem somam persistência e
trechos), porque na avaliação toda janela tem rodada. A topologia de cada janela usa as amostras de saúde que caem nela
(A-15, regra de consumo do código).

### 4.2 W (7.3, A-17)

`W = max(⌈n_lat/λ₅⌉, W_min)` em segundos inteiros, com **`W_min = ⌊tetoLag + atraso_max⌋ + 1`**, o menor inteiro
estritamente acima de `tetoLag + atraso_max`. Isso cumpre as duas condições do código: `hotrod_timeout_ms` < W (estrito)
e `atraso ≤ W − teto` (`SondaDefasagem.java:559`). `n_lat` = 100 (plano de 22/10 §3.2); `tetoLag` = 2 s;
`atraso_max` = maior `t_inicio_ns` − início da janela nas linhas `defasagem` de todas as execuções aceitas (A-6).

### 4.3 O AC-5 pode ficar inviável por construção? (observação 2 do W-9)

**Sim, sem a regra de reserva abaixo.** A 7.3 tira W da vazão do sinal de latência, e a viabilidade do AC-5 pede
`kLag·W + 13,3 < 39,8`, ou seja, `kLag·W < 26,5 s` (39,8 s = menor T_det da VM, `V-S7r.md:45`; 13,3 s = parada sob
corte, P-42). Conta, sem dado nenhum da calibração:

- λ₅ < 100/26 ≈ 3,85 eventos/s ⇒ W ≥ 27 s ⇒ nenhum `kLag` ≥ 1 cabe: AC-5 inviável pela 7.4, só por causa da vazão;
- 3,85 ≤ λ₅ < 100/13 ≈ 7,7 eventos/s ⇒ 14 s ≤ W ≤ 26 s ⇒ só `kLag` = 1 cabe, e qualquer janela isolada no teto com a
  condição da política, sem partição, já inviabiliza o AC-5;
- 7,7 ≤ λ₅ < 100/8 = 12,5 eventos/s ⇒ 9 s ≤ W ≤ 13 s ⇒ `kLag` ≤ 2.

O *smoke* do W-9 com o I-3b local (uma execução, só `Validate` com n ≥ 1000 na referência) deu λ₅ = 5,4 eventos/s e
W = 19 s. Não é dado da calibração, mas mostra que o caso de `kLag` = 1 é plausível.

**Por que isso é um acoplamento e não uma necessidade.** W serve a dois sinais. Para a latência, ⌈n_lat/λ₅⌉ faz 95 %
das janelas sem falha terem ao menos `n_lat` eventos (plano de alinhamento §3.5); janela com menos é indeterminada, e o
código já trata isso (`Controlador.java:93`). Pela política aprovada, latência e erro **não levam a ação nenhuma**: com
P2 fora (D-B), CONSERVADOR sem a assinatura do P1 é P0 (`acoes-controlador-cluster-2026-11-05.md` §5, terceira linha
da tabela). A única ação, o AC-5, depende de S_lag e da topologia, e é ela que precisa caber em 39,8 s.

**Regra de reserva (nesta emenda).** Seja `kLag_min(W')` o menor k ≥ 1 com zero ocorrências na contagem da 7.13 (com a
condição da política, grade W'), e "viável em W'" = `kLag_min(W')·W' + 13,3 < 39,8`.

1. Se é viável em W da 4.2: W e `kLag = kLag_min(W)`, sem mudança.
2. Senão: **W passa a ser o maior inteiro W' entre W_min e W − 1 viável**, e `kLag = kLag_min(W')`. Todas as regras
   seguintes (θ, m, `lag_out` etc.) usam esse W'. O relatório declara a fração de janelas sem falha com latência
   indeterminada (n < `n_lat`) em W', que passa de 5 %, e o texto diz que o sinal de latência perdeu cobertura para o
   AC-5 caber no tempo de detecção.
3. Se nenhum W' em [W_min, W] é viável: o **AC-5 é declarado inviável antes da avaliação** (7.4), com a conta e as
   ocorrências que o impediram, e a DT-021 volta ao Gestor antes da N4. Não há reserva automática para além disso.

Todos os números da regra já têm origem: 39,8 s (V-S7r), 13,3 s (P-42), `n_lat` = 100 (plano de 22/10), o 95 % de λ₅
(plano de alinhamento §3.5), W_min (4.2) e `kLag_min` (contagem da 7.13). A regra não olha efeito nem diferença entre
braços. Com ela, a inviabilidade só acontece se as execuções sem falha e F1 mostrarem tetos persistentes com a condição
da política, isto é, por dado, não por construção.

## 5. Demais itens (uma linha cada)

| Id | Decisão |
|---|---|
| A-2 | **Confirmada**: θ_in = o menor *double* acima do maior mínimo de k janelas seguidas (`math.nextafter`), o limiar exato de `taxa ≥ θ`. Com zero erro sem falha, θ_err_in = 5 × 10⁻³²⁴ e qualquer erro em k janelas seguidas é alto; é a regra aplicada e é declarado |
| A-4 | **Resolvida** pela seção 3 (m sem θ_out) e pela ordem da seção 1 |
| A-6 | **Confirmada**: W, θ, k, m, `lag_in`, `lag_out` e `kLag` só de execuções ASYNC; `atraso_max` (7.3), 7.10, 7.14 e 7.15 de todas as aceitas, inclusive SYNC, porque o máximo é conservador |
| A-7 | **Confirmada**: N = janelas (ou posições de k janelas seguidas) em que o critério pode ocorrer; o total de janelas vai ao lado |
| A-8 | **Confirmada**: janelas completas de `origem_ns` até `t_fim_carga_ns`; D = 256 s só no alvo a |
| A-9 | **Confirmada**: duração do trecho saudável = da primeira à última amostra saudável depois do `end`, quando a seguinte é anômala; mais `P_sonda`, como na 7.14 |
| A-10 | **Confirmada**: I1 + I4 + I2 REAL, todas as do CSV da execução (as que o R1 vê) |
| A-11 | **Confirmada**: período = W (fechamento `2c8b7b8`); `t_pre` = âncora − lançamento; `t_pos` = `fim_utc` − convergência, só nas que convergiram (superestima, conservador); MiB arredondado para cima; teto para cima |
| A-12 | **Confirmada com acréscimo**: `T_exec` = `fim_utc` − `recriacao_ms`; o relatório dá também o ciclo completo (intervalo entre `recriacao_ms` de execuções seguidas da mesma invocação), que inclui pós-checagem e h4. Com n = 12 fixo, `T_exec` só informa o calendário |
| A-13 | **Confirmada com acréscimo**: q̂, Clopper-Pearson e taxas no W-9; a curva do `poder.py` roda depois, com `python -E -P` (G2), `--semente 20261105` (a mesma regra da `--ordem-seed`), `--n-sim 10000` (as 10 000 reamostragens da análise do *rebaseline*, DT-009), `--m-celulas 2`, `--blocos 12`. Só informativa |
| A-14 | **Confirmada**: quantil por posto mais próximo (`referencia_p99.percentil`) |
| A-15 | **Confirmada**: atribuição de amostras e rodadas às janelas pela regra de consumo do código, como na seção 4.1 |
| A-16 | **Confirmada**: Δ_conv do `comparar_bracos` (desde `t_fim_carga_ns` no braço A); fora do máximo da 7.10 quem não tem `convergencia_estado = convergiu`, mas a contagem desses vai ao relatório |
| A-17 | **Corrigida** na seção 4.2 (W_min estrito) |
| Escopo | O W-9 implementa 7.1 a 7.18. A 7.18 é só informativa (A-13); a 7.16 e a 7.17 só informam o calendário e o B-40 parte 2 |
