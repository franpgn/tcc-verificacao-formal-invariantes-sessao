# Complemento 3 ao pré-registro da calibração S3: viabilidade do AC-5 com a persistência do núcleo, latência sem cobertura e cortes no `end` (2026-11-06)

> Senior, 2026-11-06, ~00:05Z, **antes de qualquer dado da calibração** (X-18 ainda não começou; ninguém abriu brutos).
> Só documentos. Complementa a DT-020 e os complementos 1 (DT-022) e 2 (DT-023), e é registrado como **DT-024**. Os
> documentos anteriores não são editados além de notas de remissão; onde divergirem, vale este.
>
> Origem: achados R-1 a R-4 da validação independente do W-9 (`wt-w9/docs/validacao-w9-2026-11-05.md`, commit
> `8d6ebfc`, seções 3 e 6). Aprovados pelo coordenador pela regra permanente do Gestor, porque todos derivam do código
> do controlador em `17f2bd4` e de números que já têm origem.

## 1. R-1: viabilidade do AC-5 conta a persistência do núcleo

**O defeito.** A 7.4, a 7.13 e a regra de reserva do complemento 1 (§4.3) contavam `kLag·W + 13,3 < 39,8`. Mas o P1 só
age com o perfil em CONSERVADOR (`PoliticaPorAssinatura.java:152`), e no cenário do P1 (vista completa, S_lag no teto) o
núcleo só entra em CONSERVADOR depois de **k = 2** janelas seguidas com defasagem ALTA (R2, `Controlador.java:121-123`;
k pela 7.6). Logo o AC-5 age, no mínimo, ao fim da janela `max(k, kLag)`. Com `kLag` = 1 e W de 14 a 26 s, a regra antiga
dava "viável", mas o tempo real fica em 2W + 13,3 ≥ 41,3 s, depois do menor T_det (39,8 s). Depois da troca de vista a
vista deixa de ser completa e o P1 não age mais.

**Regra emendada** (vale na 7.4, na 7.13 e na regra de reserva do complemento 1 §4.3):

`viável em W'` ⟺ `max(k, kLag_min(W'))·W' + 13,3 < 39,8`, com k = 2,

ou seja, `max(2, kLag_min)·W' < 26,5 s` (39,8 s = menor T_det da VM, `V-S7r.md:45`; 13,3 s = parada sob corte, P-42).

| `kLag_min` | maior W' viável (inteiro) | conta |
|---|---|---|
| 1 ou 2 | **13 s** | 2 × 13 = 26 < 26,5; 2 × 14 = 28 |
| 3 | 8 s | 3 × 8 = 24; 3 × 9 = 27 |
| 4 | 6 s | 4 × 6 = 24; 4 × 7 = 28 |
| 5 | 5 s | 5 × 5 = 25; 5 × 6 = 30 |
| 6 | 4 s | 6 × 4 = 24; 6 × 5 = 30 |
| 7 ou 8 | 3 s | 7 × 3 = 21; 8 × 3 = 24; 8 × 4 = 32 |
| ≥ 9 | nenhum com W' ≥ 3 s | 9 × 3 = 27; abaixo de `W_min` (≥ 3 s, complemento 1 §4.2) não há W': AC-5 inviável |

Consequências: com a R-1, W da 7.3 acima de 13 s já leva à reserva, o que ocorre com λ₅ < 100/13 ≈ 7,7 eventos/s. O
*smoke* do I-3b local (λ₅ = 5,4 eventos/s, W = 19 s; não é dado da calibração) cairia na reserva com W' ≤ 13 s.

**Convenção declarada, anterior a este complemento:** a conta não inclui a fase do corte dentro da janela, que pode
acrescentar até um W ao tempo até a ação. Ela fica como está (o mesmo vale desde a DT-020) e é declarada no texto como
limite da conta.

## 2. R-2: latência sem cobertura

**O caso.** Na W final (da 7.3 ou da reserva), as janelas das execuções ASYNC sem falha podem não ter nenhuma janela
determinada para a latência (n < `n_lat` = 100 em todas). A 7.7 não tem então dado para θ_lat_in, e a 7.8 não fecha m nem
`T_cooldown`. O arquivo do controlador exige θ finitos e não negativos, `θ_out < θ_in`, m ≥ 1 e `cooldownNs`
(`ParametrosControlador.java:74-90`), e escolher valores depois de ver os dados quebraria o pré-registro. Com a R-1 o
caso fica provável: no *smoke* acima, W' ≤ 13 s daria cerca de 70 eventos elegíveis por janela (5,4 × 13).

**Regra** (vale com ou sem reserva, sempre que a 7.7 não tiver janela determinada para o sinal):

- **θ_lat_in = o menor *double* acima de 1** (`math.nextafter(1, +∞)`), inalcançável, porque a taxa de excedência é
  ≤ 1; é a mesma convenção do "menor *double* acima" do complemento 1 (A-2);
- **θ_lat_out = 1**, que cumpre `θ_out < θ_in` e deixa toda janela calma para a latência;
- m e `T_cooldown` saem da 7.8 normalmente, com esses valores. É o que o controlador fará na avaliação;
- **o mesmo vale para θ_err_in e θ_err_out** se a taxa de erro também ficar sem janela determinada;
- o JSON do W-9 marca o sinal como "sem cobertura" e dá a fração de janelas sem falha determinadas (zero).

**Efeito na avaliação** (declarado no B-40, DT-021): S_lat (e S_err, se for o caso) **não atua**; o CONSERVADOR é
alcançado só por defasagem, topologia ou violação (`Controlador.java:117-123`). O P1 não depende da latência: no cenário
do P1 o lag está ALTA desde a primeira janela com rodada no teto, porque `lag_in` ≤ teto (V-W9 §3(b)).

> **Nota de remissão (2026-11-06, antes de qualquer dado):** a condição desta seção vale sempre que a 7.7 tiver
> N = 0 (nenhuma posição de k janelas determinadas seguidas), o que inclui o caso sem janela determinada; o valor
> de θ_in é o menor *double* acima de 1, como acima. Ver o complemento 3a
> (`docs/pre-registro-calibracao-s3-complemento-3a-2026-11-06.md`). O texto acima não foi editado.

## 3. R-3: o trecho do m que atravessa o `end` é cortado no `end`

Na 7.8 (complemento 1 §3), os trechos de janelas sem sinal alto são tomados **dentro das janelas que começam em ou depois
do marcador `end`**. Um trecho que vinha de antes do `end` conta só a parte depois dele. É o mesmo tratamento da 7.14, que
corta o trecho saudável no `end` (A-9), e dá m igual ou maior que a leitura que descartava esses trechos. Contar o trecho
inteiro, desde antes do `end`, não serve, porque na F4 um trecho calmo pode vir do começo da carga e inflar m.

## 4. R-4: W final abaixo de 5 s

O complemento 1 §4.1 chama de conservador pular as janelas sem rodada quando W < 5 s. **Isso está errado e fica
corrigido:** com W final < 5 s, persistência e trechos passam a ser contados em rodadas de 5 s, não em janelas de W, o que
**subestima** `kLag`, m e o trecho do `lag_out` em cerca de 5/W. A regra de pular fica, porque na avaliação toda janela tem
rodada, mas a subestimação é declarada no relatório da calibração e no texto.

W final < 5 s acontece de dois jeitos: (a) a 7.3 já dá W < 5 s, sem reserva, o que pede λ₅ ≥ 25 eventos/s e `W_min`
≤ 4 s; (b) a reserva desce abaixo de 5 s, o que pela R-1 só acontece com `kLag_min` > 5 (tabela da seção 1).

## 5. Mudanças que o W-9 aplica

- `reserva_w` e a viabilidade com `max(K_NUCLEO, kLag)`, K_NUCLEO = 2 (R-1);
- θ_lat (e θ_err) "sem cobertura" com os valores da seção 2, em vez de "não derivado", para m e `T_cooldown` derivarem
  (R-2);
- `m_reentrada` com os trechos cortados no `end` (R-3);
- o JSON declara a subestimação da R-4 quando W final < 5 s.

Nenhuma regra olha efeito nem diferença entre braços.
