# Conferência da derivação da calibração S3 contra os brutos (2026-11-06)

> Senior, 2026-11-06. Conferência pedida pela §7 do pré-registro da calibração ("o Senior confere contra os brutos e
> leva ao B-40 parte 2"), antes de os valores irem ao B-40 parte 2.
>
> - Saída: `dados-vm/cs3-2026-11/derivacao-20261106T050404Z/calibracao-s3-derivacao.json`, SHA-256 `e904b014…8eed`.
> - Brutos: `dados-vm/cs3-2026-11/x18-20261106T050100Z/campanha-s3-calibracao/` e `ENTREGA-W9.txt`.
> - Recálculo por amostragem com código meu, só biblioteca padrão, sem importar o W-9. O código ficou fora do
>   repositório, no *scratchpad* da sessão (`vderiv/recalc.py`).

## 0. Veredito

**Valores confirmados para o B-40 parte 2, com três ressalvas de texto e duas declarações obrigatórias** (seção 3).
Todos os valores conferidos por amostragem batem com o JSON até a última casa. Os demais seguem das regras com os dados
que o JSON mostra. Nenhum valor precisa ser mudado. As declarações da seção 3.2 não mudam valores: elas dizem o que os
valores fazem no controlador e precisam constar no B-40.

## 1. Integridade

- `MANIFEST.sha256` da pasta da derivação: todos os arquivos OK.
- Referências: `referencia-S3-async.json` = `c5ad8687…`, `referencia-S3-sync.json` = `5ced3dc5…`, iguais às de
  `valores_b40_parte2`.
- Script: o `gerado_por` traz `a056f8b1…`. É o SHA-256 do arquivo no disco do `wt-w9`, com CRLF; o *blob* da ponta
  `4e2c2c8` dá `e3a39fd9…` com LF. Sem diferença de conteúdo: `git diff 4e2c2c8 -- analysis/calibracao_s3.py` vazio.
  A ponta conferida pela V-W9 (§9) é a que rodou.
- Execuções: 27 diretórios, 26 aceitas (5, 5, 5, 5, 3 e 3 por célula, todas completas). A `exec-0001-SYNC-F2-A`
  (DESCARTADA-INFRA, h4a) ficou fora, e a reposição `exec-0026` entrou.

## 2. Recálculo por amostragem

| Valor | JSON | Recálculo independente | Situação |
|---|---|---|---|
| p99 ASYNC (7.1) | Validate 5 658 612 ns; IncrementFailure 5 204 240 ns | os mesmos, por posto mais próximo sobre todos os eventos de cada operação (n = 14 343 e 1 138; as outras 5 operações, com n < 1000, ficam fora) | confere |
| λ₅ (7.2) | 8,4/s, N = 255, posto 13 | 8,4/s em 255 janelas de 5 s | confere |
| W (7.3) | 12 s; termo da vazão 12; W_min 3 | ⌈100/8,4⌉ = ⌈11,90⌉ = 12; atraso máximo 2,817 ms (`exec-0008-ASYNC-F1-A`) dá W_min = ⌊2,0028⌋ + 1 = 3 | confere |
| viabilidade e `kLag` (7.4, 7.13, R-1) | `kLag` = 1, sem reserva | nenhuma janela com teto, vista completa e suspeito respondendo nas ASYNC-none e F1, então `kLag` = 1. `max(2, 1)·12 + 13,3` = 37,3 s < 39,8 s | confere |
| assinatura sem resposta (7.13) | 30 janelas, 6 por F1, persistência máxima 6 | 30 janelas: 6 em cada uma das cinco F1, todas com o suspeito sem resposta REST; 0 contadas no critério | confere |
| θ_lat_in (7.7) | 0,030769230769230774; N = 99 | maior mínimo de pares = 4/130 (janelas 1 e 2 de `exec-0012-ASYNC-none-A`: 4 excedências em 130 elegíveis e 5 em 148); `nextafter` dá o valor do JSON | confere |
| θ_err_in (7.7) | 5 × 10⁻³²⁴; N = 100 | zero erro nas janelas sem falha; maior mínimo 0,0 | confere |
| `lag_in` (7.11) | 6,932448 ms; N = 260; 3 amostras ≥ | posto 258 de 260 = 6,932448 ms; 3 amostras ≥ | confere |
| `lag_out` (7.12, C1 §2) | 3,424438 ms; maior trecho na banda 7 | menor candidato com trecho ≤ m = 7 é 3,424438 (trecho 7); o candidato anterior dá 8 | confere |
| m (7.8, C1 §3, R-3) | 7; maior trecho 6; 1 trecho cortado no `end` | as 15 sequências H/N iguais às do JSON; o trecho de 6 está em `exec-0017-ASYNC-F1-A`, janelas 12 a 17, e termina na janela 18, alta só pela defasagem (lag ≥ `lag_in`) | confere |
| Δ_conv máximo (7.10) | 7,087395895 s; 18 convergiram; 0 fora | 7,087395895 s (`exec-0017-ASYNC-F1-A`), 18 de 18 com `convergencia_estado` = `convergiu` | confere |
| q̂ (seção 6, 7.18) | 0/5 em ASYNC-F2 e em ASYNC-F4 | nenhuma linha I1, I4 ou I2 em nenhum `violacoes-rep-001.csv` das 26 aceitas. Há só `I2_CASCATA_INCOMPLETA` (F1 18, F2 71) e `I2_JANELA_CASCATA` (F1 11, F2 17, F4 1), com `classificacao` vazia e fora do V pela DT-002. No controlador elas são `FORA_DO_GATILHO` (`ClasseViolacao.de`) | confere |

Conferidos pela fórmula, sem recálculo dos brutos:
- `T_cooldown` = 7 × 12 = 84 s.
- `T_hold_viol` = W = 12 s, porque nenhuma execução tem duas violações do V (não há nenhuma).
- Teto da convergência = 600 s, porque 7,09 s < 400 s.
- `exec_teto` = 300 + 21 + 60 + 600 + 12 + 1,5 × (1,021 + 12,545) = 1013,35, que arredonda para 1014 s.
- `T_estavel` = P_sonda = 2 s, porque o JSON não registra recaída depois do `end`. Isso é coerente com as sequências
  do m: as janelas altas depois do `end` vêm da defasagem, não da topologia.
- Curva de poder = 0: as taxas por mil ações do braço A são todas 0 (`taxas-ASYNC-F2.txt` e `taxas-ASYNC-F4.txt`).

## 3. Ressalvas e declarações

### 3.1 Ressalvas

1. **Regra 7.10 e P-48.** O *smoke* do P-48 não foi feito antes do X-18, e o P-48 continua ABERTO em `1f65a02`. Ele
   trata da topologia velha do `rcm` principal no S3, que o laço de convergência usa no `min` com a REST e na escolha
   dos segmentos lidos de cada nó (`registro-de-problemas.md`, P-48). O Δ_conv medido pode estar enviesado na direção
   que o P-48 aponta: segmentos lidos do nó errado podem dar convergência cedo demais. O valor da 7.10 (600 s) só
   mudaria se o maior Δ_conv real passasse de 400 s, 56 vezes o medido, e 600 s é limite de censura, não decide
   desfecho. Fica **confirmado com a ressalva**, e o *smoke* do P-48 tem de vir antes da N4, porque na avaliação o
   Δ_conv é desfecho.
2. **Campo `leitura` de `valores_b40_parte2`.** O campo diz "regras da seção 7 com o complemento 1 (DT-022)". O
   `pre_registro` do mesmo JSON cita os complementos 1, 3 e 3a, que são os que valeram. É só texto. No B-40, citar os
   três; no script, corrigir a constante numa próxima mudança (não refazer a derivação por isso).
3. **`volume_exec_mb` = 2 MiB.** O valor confere com a 7.17 aplicada às execuções da calibração (maior 1,24 MiB,
   `exec-0020-ASYNC-F4-A`), mas a calibração só tem braço A. A 4.1 diz que vale o maior medido, e o I-3b
   `ativo-religar` (braço B, com o stderr descomprimido) deu 13,6 MB. Para a guarda de disco da avaliação, recomendo
   manter 14, o maior medido. O parâmetro é operacional e não toca hipótese.

### 3.2 Declarações obrigatórias no B-40 (não mudam valores)

1. **Com os limiares derivados, a saída R4 praticamente não ocorre.** O m = 7 exige 7 janelas calmas seguidas, e calma
   pede latência com taxa 0 (θ_lat_out = 0), erro 0 (θ_err_out = 0) e lag ≤ 3,42 ms. Com os valores derivados, o
   maior trecho de janelas calmas foi de **2 janelas** nas ASYNC sem falha e de **3** depois do `end` nas F1, F2 e
   F4. Só 45 de 104 janelas sem falha têm taxa de latência 0. Uma vez em CONSERVADOR, o controlador fica lá até o fim
   da execução. Isso decorre da regra do complemento 1 §2, que limita os trechos de janelas intermediárias mas não
   garante trechos calmos de m janelas. O efeito medido pelo A/B não muda: o P1 exige CONSERVADOR, a cota é 1 AC-5 por
   execução e, com P2 desligada, CONSERVADOR sem assinatura não leva a ação nenhuma. Mas oscilação, reentrada,
   `T_cooldown` e `T_estavel` passam a ser descritivos e quase vazios. **Não proponho mudar valores:** seria emenda
   depois de ver os dados (D-12). Se o Gestor quiser mudar mesmo assim, a mudança tem de ser declarada como
   pós-calibração.
2. **q̂ = 0 nas duas células de teste, com poder 0.** Na calibração não houve violação do V em ASYNC-F2 nem em
   ASYNC-F4 (0/5 cada, Clopper-Pearson de 95 % até 0,52). A 7.18 é só informativa, mas o Gestor precisa saber antes da
   N4: se a avaliação repetir o padrão, o teste de redução de violações não tem como rejeitar, e a contribuição do A/B
   passa a ser descritiva (P1 previsto, AC-5 aplicado, custo). Não é defeito da derivação.

Também vale registrar que o m = 7 vem de uma única recaída. A janela 18 da `exec-0017-ASYNC-F1-A` é alta só porque o
lag passou de `lag_in` (6,93 ms, o q99 das rodadas sem falha), um evento na escala do próprio falso alarme do limiar.
É a regra aplicada como escrita.

## 4. Pontos para o Gestor

1. Aceitar os valores para o B-40 parte 2 com as ressalvas 3.1 e as declarações 3.2 (recomendado).
2. Volume da guarda de disco: 14 (recomendado) ou os 2 MiB da calibração.
3. Agendar o *smoke* do P-48 antes da N4.
