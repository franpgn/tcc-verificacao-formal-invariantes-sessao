# B-40 parte 2 revisada: valores da recalibração para a avaliação fatorial (2026-11-06)

> Senior, 2026-11-06, depois da recalibração e **antes de qualquer dado da avaliação fatorial** (DT-027). Branch
> `docs/pre-registro-benchmark-refeito` (`wt-bench`). Registrada como **DT-028 (PRÉ-REGISTRO)**. **Para o benchmark
> refeito, substitui a DT-025** (valores da calibração cega). A DT-025 não é editada. Sem push.
>
> - **Derivação:** `dados-vm/cs3-2026-11/derivacao-recal-20261106T145106Z/calibracao-s3-derivacao.json`, SHA-256
>   `b183527a4c88476a1feda51bfd6e7fedb832949344d04ad59c80606b25126121`, da recalibração com a carga L+ e os
>   instrumentos da DT-026, pelas regras da DT-020 com os complementos 1, 3 e 3a.
> - **Conferência independente:** `wt-w9/docs/validacao-derivacao-recalibracao-2026-11-06.md` (`ddc7700`): valores
>   confirmados por recálculo amostral contra os brutos.
> - **Referências de p99:** ASYNC `dec07fb76352e85ea014def9f8c52f5b0b416663ddda4f3c60f4676c660fd396`, SYNC
>   `32bb1c60d13611eed70b4ffae3a276e0e318dbee9f3d2393a3545a653e6ad741` (7.1, sem `ReplayValidate`).
> - Nenhum valor da derivação foi mudado, salvo a guarda de disco (seção 2). Os parâmetros novos (seção 3) não vêm da
>   derivação, e cada um traz a origem.

## 1. Valores do controlador e da campanha

`a` = 0,05 × W/D = 0,05 × 4/256 = 0,00078125. Critério C-8: zero ocorrências, com 3/N reportado.

| Parâmetro | Valor | Regra | N | 3/N | Dados |
|---|---|---|---|---|---|
| λ₅ | 29,8 eventos/s | 7.2 | 255 janelas de 5 s | — | ASYNC sem falha × 5 (de 26,4 a 55,6) |
| **W** | **4 s** | 7.3 (C1 §4.2) | — | — | ⌈100/29,8⌉ = 4; W_min = 3 s (atraso máximo 3,74 ms, `exec-0001-SYNC-F2-A`); reserva **não** aplicada |
| **`kLag`** | **1** | 7.13 (C1 §4, C3 R-1) | 451 | 0,0067 | 0 ocorrências com a condição da política; 60 janelas de "assinatura sem resposta" (12 por F1), fora do critério |
| viabilidade da ação do P1 | viável | 7.4, C3 R-1 | — | — | `max(2, 1)·4 + 13,3` = 21,3 s < 39,8 s com a parada; com o AC-7 o termo de 13,3 s cai para dezenas de ms (relatório da A4: 16 a 70 ms) |
| **θ_lat_in** | **0,05780346820809249** | 7.7 | 313 | 0,0096 | o menor *double* acima de 10/173; 319 de 320 janelas determinadas; com cobertura |
| **θ_lat_out** | **0,0** | 7.8, C1 §2 | 320 | 0,0094 | maior trecho na faixa = 22 ≤ m |
| **θ_err_in** | **5 × 10⁻³²⁴** | 7.7 | 313 | 0,0096 | zero erro nas janelas sem falha |
| **θ_err_out** | **0,0** | 7.8, C1 §2 | 320 | 0,0094 | maior trecho na faixa = 0 |
| **`lag_in`** | **6,881289 ms** | 7.11 | 260 rodadas | — (quantil) | 3 amostras ≥ |
| **`lag_out`** | **0,939831 ms** | 7.12, C1 §2 | 260 | 0,0115 | maior trecho na faixa = 35 = m; 60 janelas sem rodada puladas (R-4) |
| **m** | **35** | 7.8, C1 §3, C3 R-3 | 474 janelas | — | maior trecho sem alto seguido de alta = 34 (`exec-0019-ASYNC-F1-A`) |
| **`T_cooldown`** | **140 s** | m·W | — | — | 35 × 4 |
| **`T_estavel`** | **2 s** | 7.14 | 17 | 0 recaídas | `P_sonda` |
| **`T_hold_viol`** | **69,500485632 s** | 7.15 | 25 | — | maior intervalo entre violações seguidas, `exec-0013-ASYNC-F2-A` |
| k (núcleo), `n_lat`, `n_err` | 2, 100, 100 | 7.6, 7.9 | — | — | — |
| **teto da convergência** | **600 s** | 7.10 | 17 | 0 fora | maior Δ_conv 11,09 s (`exec-0007-ASYNC-F1-A`) < 400 s |
| `convergencia_periodo_s` | 4 s | = W (fechamento da DT-020) | — | — | — |
| **`exec_teto_s`** | **1019 s** | 7.17 | 25 | — | 300 + 21 + 60 + 600 + 4 + 1,5 × (1,024 + 21,638) = 1018,99, para cima |
| cobertura da latência | com cobertura | C3 R-2, C3a | 320 janelas | 1 indeterminada (0,3 %) | S_lat e S_err atuam |
| q̂ ASYNC F2 | 4/4 | 6 | 4 | — | Clopper-Pearson 95 %: [0,40; 1]; V por pares 3, 6, 2 e 6 |
| q̂ ASYNC F4 | 0/5 | 6 | 5 | — | — |

## 2. Guarda de disco: `volume_exec_mb` = 14

A 7.17 dá 3 MiB (maior 2,99 MiB, `exec-0021-ASYNC-F4-A`). Vale 14, o maior volume medido no projeto (I-3b, braço B,
13,6 MB), como na DT-025 e pela recomendação do validador (ressalva 3). O parâmetro é operacional.

## 3. Parâmetros novos da avaliação fatorial

| Parâmetro | Valor | Origem |
|---|---|---|
| **`s3_replay_late_max`** | **306** | regra da DT-026 §2.1 (acima do maior número de candidatas ao *replay* tardio por execução): o maior na recalibração foi **305** (`exec-0023-ASYNC-F4-A`; `tardio-candidatas.tsv` da derivação). **As 5 ASYNC-F4 da recalibração bateram no teto de 268** (275 a 305 candidatas) e tiveram o tardio amostrado |
| **`fusao_teto_s`** | **72 s** | 48 s (o maior tempo de fusão depois do `end` da F2 medido na VM, 47,8 s, em 18 F2 do X-18 e do X-19, pela sonda de topologia) × 1,5 (a folga do `exec_teto_s` sobre o medido, pré-registro da calibração §4.1). Fonte: `wt-fat/docs/relatorio-orquestrador-fatorial-2026-11-06.md`. Coerência conferida pelo orquestrador: 49 + 90 + 72 = 211 ≤ 300 |
| **`acaoP1`** | **`"AC-7"`**, explícito no arquivo do controlador da avaliação | decisão G-1 do coordenador sobre a A4 (trocar a parada pelo `ignored-caches/sessions-async`) e V-3 (`acaoP1` opcional com padrão AC-7, mas explícito no arquivo da avaliação; o orquestrador recusa o braço B sem ele) (`wt-p1ic-ca/docs/implementacao.md`, `docs/contrato-jsonl-controlador-v1.md`) |

## 4. Declarações obrigatórias (não mudam valores)

1. **R-4 aplicada.** Com W = 4 s < 5 s, as janelas sem rodada (1 em 5, artefato da sonda de 5 s da recalibração) foram
   puladas. `kLag`, m e o trecho do `lag_out` estão **subestimados em cerca de 1,25 vez** (complemento 3 §4): o m de 35
   corresponde a cerca de 44 janelas na grade da avaliação. Declarado no JSON (`subestimacao_w_menor_que_5_s`).
2. **A saída R4 praticamente não ocorre.** Com θ_lat_out = θ_err_out = 0 e m = 35 (140 s), uma vez em CONSERVADOR o
   controlador fica lá até o fim da execução. Oscilação, reentrada, `T_cooldown` e `T_estavel` são descritivos.
3. **Tempo do P1.** Na reprodução das decisões com estes valores nas 4 ASYNC-F2 da recalibração (validação §4.1), o P1
   dispara entre **+9,9 e +10,1 s** depois do corte. Com a parada (AC-5), o nó pararia em cerca de +23,3 s; com o AC-7, o
   efeito vem dezenas de milissegundos depois do P1. Isso é registro do mecanismo, não previsão de efeito.

## 5. Ambiente: log dos contêineres (P-64)

Desvio de ambiente em relação à recalibração, igual nos quatro braços e **sem mudança de semântica**: o log dos três
nós vai a `k8s-file` (teto de 512 MB), não ao journald, e os *loggers* `org.infinispan.server.hotrod.BaseRequestProcessor`
e `org.infinispan.interceptors.impl.InvocationContextInterceptor` vão a FATAL pela REST depois da criação dos *caches*
(conferidos pela camada 3 antes e depois de cada execução). Motivo: o alvo do AC-7 sob corte gravava ~268 MB de pilhas
por execução, só nos braços ativos, uma carga de disco assimétrica capaz de mexer no p99 e no h4. Medida: **268 MB →
0,09 MB** no alvo do AC-7. O servidor recusa e responde igual; só as linhas de pilha somem (`ISPN005003`, `ISPN000136`),
e a checagem de fusão continua lendo `ISPN000093`. Texto completo em `wt-p1ic-ca/docs/configuracao-infinispan.md`,
seção "Log dos contêineres na avaliação (P-64)" (`e672051`).

## 6. Arquivos de entrada

O controlador da avaliação é o da recalibração com os valores da seção 1, `acaoP1 = "AC-7"` e a política com `p2Habilitada
= false`; o `campanha-s3/2` leva os valores da seção 1, `volume_exec_mb` = 14, `s3_replay_late_max` = 306 e `fusao_teto_s`
= 72. Os SHA-256 dos arquivos gerados e da matriz fatorial são registrados no roteiro da avaliação antes da primeira
execução, e têm de corresponder a estes valores.
