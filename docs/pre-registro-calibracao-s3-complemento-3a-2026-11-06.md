# Complemento 3a ao pré-registro da calibração S3: condição da latência sem cobertura (2026-11-06)

> Senior, 2026-11-06, **antes de qualquer dado da calibração** (X-18 ainda não começou; ninguém abriu brutos). Só
> documentos. Nota ao complemento 3 (DT-024, `45e7800`), registrada na própria DT-024. O complemento 3 não é editado
> além de uma nota de remissão; onde divergirem, vale esta nota.
>
> Origem: conferência do W-9b na V-W9 (adendo em `wt-w9/docs/validacao-w9-2026-11-05.md`).

## 1. O caso que o complemento 3 não cobria

O complemento 3 §2 aplica a regra de latência sem cobertura "sempre que a 7.7 não tiver janela determinada para o
sinal". Fica de fora o caso em que há janelas determinadas, mas nenhuma posição de k = 2 janelas determinadas seguidas.
Nele a 7.7 também não tem dado: o critério é "k janelas seguidas ≥ θ_in", e o N da regra conta posições de k janelas
seguidas determinadas (A-7, complemento 1 §5). θ_lat_in sai "não derivado", m e `T_cooldown` também, e o arquivo do
controlador não carrega (`ParametrosControlador.java:74-90`).

Com essa cobertura, o sinal nunca chega a `persistenciaLat ≥ k` sem falha (`Controlador.java:101` e `:122`), que é o
que a 7.7 calibra. Não há dado para fixar um limiar, e escolher um depois de ver os dados quebraria o pré-registro. É o
mesmo caso do complemento 3 §2.

## 2. Condição emendada

A regra do complemento 3 §2 (θ_in = o menor *double* acima de 1, θ_out = 1, m e `T_cooldown` derivados com eles,
latência e erro, cada um por si) vale **sempre que a 7.7 tiver N = 0**, isto é, nenhuma posição de k janelas
determinadas seguidas nas ASYNC sem falha no W final. Isso inclui o caso sem nenhuma janela determinada. O JSON do W-9
dá, ao lado, o número de janelas determinadas e a fração delas sobre o total, que neste caso pode ser maior que zero.

## 3. Valor de θ_in "sem cobertura"

O complemento 3 §2 fixa **o menor *double* acima de 1** (`math.nextafter(1, +∞)`), não só "acima de 1". Qualquer valor
acima de 1 dá o mesmo controlador, porque a taxa é ≤ 1, mas o valor que vai ao B-40 parte 2 é o do texto. O script segue
o texto.
