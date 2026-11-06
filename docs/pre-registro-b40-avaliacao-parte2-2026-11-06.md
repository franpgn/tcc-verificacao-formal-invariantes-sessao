# B-40 parte 2: valores calibrados da avaliação do controlador na campanha S3 (2026-11-06)

> Senior, 2026-11-06, depois da calibração (X-18) e **antes da primeira noite da avaliação (N4)**. Branch
> `docs/b40-pre-registro-avaliacao`. Preenche a seção 11 do B-40 parte 1 (`docs/pre-registro-b40-avaliacao-2026-11-05.md`,
> DT-021) com os valores que as regras da calibração produziram, **sem nenhuma comparação entre braços** (a calibração
> só tem braço A). Registrada como **DT-025 (PRÉ-REGISTRO)**. Sem push.
>
> - **Derivação:** `dados-vm/cs3-2026-11/derivacao-20261106T050404Z/calibracao-s3-derivacao.json`, SHA-256
>   `e904b0149a779ae01cbb4506e05f5789935229d4cec0ab344cbf10ddc79f8eed`, gerada pelo `analysis/calibracao_s3.py` do W-9
>   liberado pela V-W9 (adendo 2, `6956d81`), com `MANIFEST.sha256` na mesma pasta.
> - **Regras:** seção 7 do pré-registro da calibração (DT-020) com os complementos **1** (DT-022, `744e6e8`), **3**
>   (DT-024, `45e7800`) e **3a** (nota à DT-024, `639966c`). O campo `leitura` de `valores_b40_parte2` cita só o
>   complemento 1; o campo `pre_registro` do mesmo JSON cita os três, que são os que valeram (ressalva 2 da conferência).
> - **Conferência independente:** `wt-w9/docs/validacao-derivacao-calibracao-2026-11-06.md` (`0c6b7c0`): valores
>   confirmados por recálculo amostral contra os brutos, com três ressalvas e duas declarações (seções 3 e 4 abaixo).
> - Nenhum valor da derivação foi mudado. A única troca é a guarda de disco (`volume_exec_mb`), seção 2.

## 1. Valores

`a` = alvo de falso acionamento por par de janelas = 0,05 × W/D = 0,05 × 12/256 = 0,00234375. Critério C-8: zero
ocorrências, com o limite de 95 % (3/N) reportado ao lado (DT-020).

| Parâmetro | Valor | Regra | N | Ocorrências | 3/N (limite de 95 %) | Dados |
|---|---|---|---|---|---|---|
| λ₅ | 8,4 eventos/s | 7.2 | 255 janelas de 5 s | — | — | ASYNC sem falha × 5 (menor 6,2; maior 21,2) |
| **W** (`wNs`) | **12 s** | 7.3, C1 §4.2 | — | — | — | ⌈100/8,4⌉ = 12; W_min = 3 s (atraso máximo 2,817 ms, `exec-0008-ASYNC-F1-A`); 1 352 rodadas; reserva **não** aplicada |
| viabilidade do AC-5 | **viável** | 7.4, C3 R-1 | — | — | — | `max(2, 1)·12 + 13,3` = 37,3 s < 39,8 s |
| **`kLag`** | **1** | 7.13, C1 §4, C3 R-1 | 180 | 0 | 0,0167 | ASYNC sem falha e F1 (10 execuções, 210 janelas); 30 janelas de "assinatura sem resposta" (6 por F1, suspeito sem resposta REST), fora do critério |
| **θ_lat_in** | **0,030769230769230774** | 7.7 (C-8), C1 A-2 | 99 | 0 | 0,0303 | o menor *double* acima de 4/130; 104 de 105 janelas determinadas; com cobertura (C3, C3a) |
| **θ_lat_out** | **0,0** | 7.8, C1 §2 | 105 | 0 | 0,0286 | maior trecho na faixa intermediária = 6 ≤ m |
| **θ_err_in** | **5 × 10⁻³²⁴** | 7.7 (C-8), C1 A-2 | 100 | 0 | 0,03 | zero erro nas janelas sem falha; qualquer erro em 2 janelas seguidas é alto |
| **θ_err_out** | **0,0** | 7.8, C1 §2 | 105 | 0 | 0,0286 | maior trecho na faixa = 0 |
| **`lag_in`** | **6,932448 ms** | 7.11 | 260 rodadas | 3 amostras ≥ | — (quantil, não critério de zero ocorrências) | q99 por posto mais próximo, ASYNC sem falha |
| **`lag_out`** | **3,424438 ms** | 7.12, C1 §2 | 105 | 0 | 0,0286 | menor candidato com trecho ≤ m; maior trecho na faixa = 7 |
| **m** | **7** | 7.8, C1 §3, C3 R-3 | 197 janelas | — | — | maior trecho sem alto seguido de alta = 6 (`exec-0017-ASYNC-F1-A`, janelas 12 a 17); 1 trecho cortado no `end`; 10 trechos seguidos de alta |
| **`T_cooldown`** | **84 s** | m·W | — | — | — | 7 × 12 |
| **`T_estavel`** | **2 s** | 7.14 | 18 execuções com falha | 0 recaídas | — | sem recaída, `T_estavel` = `P_sonda` |
| **`T_hold_viol`** | **12 s** | 7.15 | 26 execuções | — | — | nenhuma execução com duas violações do V; `T_hold_viol` = W |
| k (núcleo) | 2 | 7.6 | — | — | — | derivação binomial do plano de 22/10 |
| `n_lat`, `n_err` | 100, 100 | 7.9 | — | — | — | — |
| **teto da convergência** | **600 s** | 7.10, A-16 | 18 | 0 fora | — | maior Δ_conv 7,087 s (`exec-0017-ASYNC-F1-A`) < 400 s; 18 de 18 convergiram. **Ressalva P-48**, seção 3 |
| `convergencia_periodo_s` | 12 s | = W (fechamento da DT-020) | — | — | — | — |
| **`exec_teto_s`** | **1014 s** | 7.17, C1 A-11 | 26 | — | — | 300 + 21 + 60 + 600 + 12 + 1,5 × (1,021 + 12,545) = 1013,35, para cima; `t_pre` máximo em `exec-0015-ASYNC-F2-A`, `t_pos` em `exec-0009-SYNC-F2-A` |
| **`volume_exec_mb`** | **14** | seção 2 | — | — | — | — |
| cobertura da latência | com cobertura | C3 R-2, C3a | 105 janelas | 1 indeterminada (0,95 %) | — | não passa de 5 %; S_lat e S_err atuam |
| q̂ ASYNC F2 | 0/5 | 6, 7.18 | 5 | 0 | Clopper-Pearson 95 %: [0; 0,52] | nenhuma I1, I4 ou I2 real nas 26 aceitas |
| q̂ ASYNC F4 | 0/5 | 6, 7.18 | 5 | 0 | [0; 0,52] | idem |
| curva de poder | 0 em toda redução | 7.18 (C1 A-13) | — | — | — | taxas do braço A todas 0; `poder-ASYNC-F2.json` `992d8037…`, `poder-ASYNC-F4.json` `58d9fd74…` |
| número de blocos | 12 | B-3 (decisão do Gestor) | — | — | — | — |

**Referências de p99 (7.1, `referencia_p99.py`, n ≥ 1000):** ASYNC `c5ad8687e7f253656ed14350f5be738a3fd14f68dc75523c9e50cc406ff685cb`
(Validate 5 658 612 ns, n = 14 343; IncrementFailure 5 204 240 ns, n = 1 138; as outras cinco operações em `omitidas`);
SYNC `5ced3dc5ab15bbb0e9e67d5aacf6dd8d84025d33809149deb169126665c3dea9` (Validate 5 684 615 ns, n = 8 488; as outras seis
em `omitidas`). O sinal de latência conta só as operações da referência (plano de 22/10 §3.2).

## 2. Guarda de disco: `volume_exec_mb` = 14

A 7.17 dá 2 MiB, o maior das execuções da calibração (1,24 MiB, `exec-0020-ASYNC-F4-A`). Mas a calibração só tem braço
A, e a regra da 4.1 do pré-registro diz que vale o maior medido. O maior medido no projeto é o do I-3b `ativo-religar`
(braço B, stderr descomprimido): 13,6 MB, arredondado para **14**. O coordenador aceitou a ressalva 3 da conferência. O
parâmetro é operacional (limite de disco antes de cada execução, 1,5 × o volume) e não toca hipótese.

## 3. Ressalva do P-48 na regra 7.10

O *smoke* do P-48 (topologia velha do `rcm` principal no S3, usada pelo laço de convergência) não foi feito antes do X-18.
O Δ_conv medido pode estar enviesado para baixo. O teto de 600 s só mudaria se o maior Δ_conv real passasse de 400 s, 56
vezes o medido, e ele é limite de censura, não desfecho. **O *smoke* do P-48 roda antes da N4**, porque na avaliação o
Δ_conv é desfecho secundário.

## 4. Declarações obrigatórias (não mudam valores)

1. **A saída R4 praticamente não ocorre.** Com m = 7, a saída pede 7 janelas calmas seguidas, e calma exige latência com
   taxa 0 (θ_lat_out = 0), erro 0 (θ_err_out = 0) e lag ≤ 3,42 ms. Com esses valores, o maior trecho calmo foi de 2
   janelas nas ASYNC sem falha e de 3 depois do `end` nas F1, F2 e F4, e só 45 de 104 janelas sem falha têm latência com
   taxa 0. **Uma vez em CONSERVADOR, o controlador fica lá até o fim da execução.** O efeito medido pelo A/B não muda: o
   P1 exige CONSERVADOR, a cota é um AC-5 por execução e, com P2 fora, CONSERVADOR sem assinatura não leva a ação.
   Oscilação, reentrada, `T_cooldown` e `T_estavel` passam a ser **descritivos** e quase vazios. Os valores não mudam:
   mudar depois de ver os dados quebraria a D-12. O m = 7 vem de uma única recaída, alta só pela defasagem (janela 18 de
   `exec-0017-ASYNC-F1-A`).
2. **q̂ = 0/5 nas duas células de teste, com poder 0.** Na calibração não houve violação do V em ASYNC F2 nem em ASYNC F4
   (Clopper-Pearson de 95 % até 0,52). Se a avaliação repetir o padrão, a redução de V é **"não avaliável"** pela regra
   já registrada (B-40 §5.1: braço A sem violação em nenhum bloco da célula), e a contribuição do A/B fica descritiva
   (P1 previsto e aplicado, AC-5, custo). Não é defeito da derivação.

## 5. Matriz e semente

- **Semente de ordem:** **20261106**, a data deste commit (regra do B-40 §8; `git log -1 --format=%cd --date=format:%Y%m%d`).
- Matriz gerada pelo `campanha_s3_matriz.py` da integração com o W-6c (`494ccce`), com os argumentos do B-40 §8 e
  `--ordem-seed 20261106`: SHA-256 `813159a102e8a73a500e2be13da9a993d0a91d8a5c83785b3b2e5568db3706ab`, 96 execuções
  (núcleo: ASYNC F2 24, ASYNC F1 8, ASYNC sem falha 16, SYNC F2 8; extra: ASYNC F4 24, SYNC F4 8, ASYNC F3 8). É o mesmo
  SHA que o roteiro calculou.

## 6. Arquivos de entrada da avaliação

Em `docs/b40-parte2/` (com `.gitattributes` `* -text`, para o *checkout* não mudar o fim de linha nem os SHA):

| Arquivo | SHA-256 | Origem |
|---|---|---|
| `controlador-avaliacao-s3.json` | `2f0d46c836bff3dcde406ac38f4049c2dd42f6c7051f4d74a2a439d2e6cf9f63` | `gerar-entradas-avaliacao.py` do roteiro (`8286c4eb…`) sobre a derivação e o controlador da calibração (`c4e05c3c…`, `17f2bd4`); conferido pelo `campanha_s3_conferir.py controlador --exigir-politica --exigir-p2-desabilitada` do W-6c |
| `campanha-s3-avaliacao.json` | `1bb3879fe569c590d836980b5987e60a9332f08808a7c3763f43e106e7a7bc46` | o mesmo gerador sobre o arquivo final da calibração (`cfd5fa6a…`), com **`volume_exec_mb` trocado de 2 para 14** (seção 2; o gerador sozinho dá `faded116…`); conferido pelo `parametros --t-enc-pior-s 21` |
| `referencia-S3-async.json` | `c5ad8687e7f253656ed14350f5be738a3fd14f68dc75523c9e50cc406ff685cb` | cópia byte a byte da derivação |
| `referencia-S3-sync.json` | `5ced3dc5ab15bbb0e9e67d5aacf6dd8d84025d33809149deb169126665c3dea9` | idem |
| `matriz-avaliacao.tsv` | `813159a102e8a73a500e2be13da9a993d0a91d8a5c83785b3b2e5568db3706ab` | seção 5 |

O SHA-256 do jar e do *bundle* é o que o roteiro registra no passo 2.1 (ponta com o W-6c); ele não entra aqui.

## 7. O que fica de fora

- Contagem final de F3 do *rebaseline* (B-40 §4.1): sai no X-15 e não muda nenhum valor desta parte.
- Desfecho novo de "exposição à perda": em decisão do Gestor; se aprovado, vira complemento separado.
