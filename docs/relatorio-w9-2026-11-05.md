# Relatório do W-9: derivação da calibração S3 (`analysis/calibracao_s3.py`) (2026-11-05)

> Worker, 2026-11-05. Branch `feature/w9-calibracao-s3` (worktree `wt-w9`) a partir de `17f2bd4`
> (`feature/controlador-integracao`). Só Python: nenhum Maven, Podman ou VM. Sem push e sem integração.

## 1. Tarefa e fonte

Item W-9 da seção 9 do pré-registro da calibração (`docs/pre-registro-calibracao-s3-2026-11-05.md`, branch
`docs/pre-registro-calibracao-s3`, DT-020): o *script* determinístico que aplica as regras de derivação da seção 7,
escrito e testado com dados sintéticos antes do X-18. A seção 7 foi lida em `a98d597` e relida no fim na ponta
`2c8b7b8`: é a mesma nos dois (as mudanças de `2c8b7b8` são em 4.1, 5 e 10; a de 4.1, `convergencia_periodo_s` = W,
entra na 7.17, ver A-11). Decisões do Gestor aplicadas:

1. critério de falso acionamento C-8: zero ocorrências, com N e o limite de 95 % 3/N ao lado de cada limiar;
2. 7.13 conta só as janelas em que vale o predicado da política P1 (`PoliticaPorAssinatura.java:158`); a assinatura
   sem resposta REST do suspeito é contada à parte; `kLag·W + 13,3 < 39,8`, senão o AC-5 é inviável (7.4);
3. nenhuma regra olha diferença entre braços: o *script* recusa execução de braço B ou em `active`.

## 2. Commits

| Commit | Tipo | O quê |
|---|---|---|
| `d4c6f09` | test | 53 testes e o esqueleto com a interface (vermelho; caiu também por um defeito do *fixture*) |
| `7678318` | test | *fixture* corrigido; vermelho limpo: 17 falhas e 34 erros contra o esqueleto |
| `bd6446d` | feat | `analysis/calibracao_s3.py`, regras 7.1 a 7.18 |
| `8563ce2` | test | regra sem nenhuma execução das suas células não deriva (vermelho: 2 falhas) |
| `d329e21` | fix | m, T_estavel e T_hold_viol nulos com motivo nesse caso; texto da A-8 |
| (este) | docs | `docs/implementacao.md` (TD-016), `docs/testes.md`, este relatório |

## 3. O que o *script* faz

`python3 -I analysis/calibracao_s3.py --campanha <saída do orquestrador> --parametros <campanha-s3-1.json> --saida
<derivacao.json> [--referencias-dir <dir>]`. Lê os `exec-NNNN-<célula>-A` no formato do `run-campanha-s3.sh`
(`wt-w6`, `081e1c4`): `checagem.json` (veredito), `execucao.json` (célula, tempos), `rep-001.csv`,
`observaveis-rep-001.jsonl` (meta com `origem_ns` e `parametros_defasagem.periodo_ns`, linhas `topologia` e
`defasagem`), `decisoes-rep-001.jsonl` (meta e linhas `acao` PREVISTA, só conferência), `violacoes-rep-001.csv`,
`s3-acoes-rep-001.csv`, `convergencia-resumo-rep-001.json` e `marcadores.txt`. Só entram as execuções ACEITA; as
demais e as abortadas (DT-014) ficam listadas em `execucoes.excluidas`, e `execucoes.por_celula` diz quantas de cada
célula entraram contra as planejadas da seção 3.

Saída: JSON determinístico com, para cada parâmetro, `regra`, `valor`, `unidade`, `texto` da regra, `dados`
(células, execuções, N e os números intermediários), `incerteza` (N e 3/N no critério C-8, n, intervalo de
Clopper-Pearson), `motivo` (quando não deriva) e `ambiguidades` (as leituras que se aplicam). Ao lado: `regras`
(regra → parâmetros), `valores_b40_parte2` (os valores na forma do arquivo do controlador, pela leitura literal),
`constantes` (cada uma com a origem no código) e a lista `ambiguidades`. Com `--referencias-dir`, grava as duas
referências de p99 (`referencia-p99/1`) com o SHA-256 que está no JSON.

Os sinais por janela são recalculados dos brutos com a W da 7.3, nunca tirados das decisões gravadas (seção 4.2 do
pré-registro): eventos pela janela do `end_ns` (`AgregadorDeJanela`), excedência pela referência da 7.1 do perfil
da execução, erro = `ERROR_TRANSPORT` + `ERROR_UNAVAILABLE`, mínimo de 100 eventos (7.9), topologia e S_lag pela
regra de consumo das sondas, violação = I1 + I4 + I2 REAL.

## 4. Testes

`analysis/test_calibracao_s3.py`, 55 testes em 5 classes (detalhe em `docs/testes.md`).

| Comando | Resultado na ponta `d329e21` |
|---|---|
| `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 55, 53 OK e 2 pulados (paridade, sem numpy em `-I`), 8,1 s |
| `python3 -E -P -m unittest discover -s analysis -p "test_calibracao_s3.py"` | **PASS**: 55 OK, 8,4 s |

Cobertura pedida: caso normal de cada regra (campanha base com as 6 células); bordas zero ocorrências (`kLag` = 1,
T_hold_viol = W, m e T_estavel sem recaída), nenhuma janela válida (`kLag` e θ_in nulos com N = 0), limite do `kLag`
estourado (persistência 5 com W = 5: `kLag` = 6, AC-5 inviável na 7.4) e no limite (`kLag` = 5, viável); separação
F1 sem resposta (12 janelas seguidas do isn2 morto no teto: não conta, `kLag` continua 2) e com resposta (o isn2
de volta no teto com vista completa: conta). Sete mutantes das regras sensíveis morrem; um é equivalente
(`docs/testes.md`). Um *smoke* com os brutos reais do I-3b (`controle-shadow`) confirmou a leitura dos formatos.

A suíte inteira de `analysis/` não foi rodada (VM possivelmente medindo; nenhum módulo existente mudou).

## 5. Ambiguidades das regras (não resolvidas por mim: a leitura adotada está no JSON, para decisão)

| Id | Regra | Ambiguidade | Leitura adotada |
|---|---|---|---|
| A-1 | 7.3, 7.12, 7.13 | Na calibração a sonda S_lag roda uma vez por W provisório (5 s); as regras agrupam pela W da 7.3. Com W ≠ 5 s, janelas com 2 rodadas ou sem rodada | agrupar pela W da 7.3 (a 7.13 manda); lag da janela = `SondaDefasagem.resumir`; sem rodada = indeterminado, persistência zerada. Com W > 5 s fica mais conservador (2 rodadas por janela), com W < 5 s menos (persistência quebrada) |
| A-2 | 7.7 | "menor valor" de θ_in não existe (conjunto aberto) | menor *double* acima do maior mínimo de k janelas seguidas (`math.nextafter`), o limiar exato da comparação `taxa >= θ` do código |
| **A-3** | **7.8, 7.12** | **"θ_out = maior valor tal que ..." é degenerado**: o maior θ_out < θ_in deixa a banda vazia e sempre satisfaz; a histerese some. Idem `lag_out` | `valor` = literal (maior *double* abaixo do limiar de entrada); `dados.alternativa_menor_valor` = menor valor que satisfaz a condição, a provável intenção. **Precisa de decisão** |
| A-4 | 7.8 | m depende de θ_out e θ_out depende de m | com a leitura literal não há ciclo; a alternativa da A-3 usa o m literal |
| A-5 | 7.8 | "seguido de nova janela alta": imediato? "alta" = sinal alto numa janela ou disparo de R1/R2 (R2 exige k janelas para latência, erro e defasagem)? | imediatamente seguido; alta = `algumAlto` do `Controlador`; janelas com início em ou depois do `end`; sem recaída, m = 1 |
| A-6 | 7.3, 7.8, 7.10, 7.14, 7.15 | A coluna Dados ("F1, F2, F4", "todas") e o último parágrafo da seção 7 ("W, θ, k, m, lag_in e kLag saem das execuções ASYNC") divergem | m só de ASYNC; 7.10 e 7.14 com a SYNC F2; atraso da 7.3 e 7.15 de todas as aceitas |
| A-7 | 7.7, 7.8, 7.12, 7.13 | "N = número de janelas usadas" | N = janelas em que o critério pode ocorrer (7.7: k janelas seguidas determinadas; 7.13: vista completa); o total vai ao lado |
| A-8 | 7.2, 7.3, 7.5, 7.7, 7.11, 7.13 | fim da medição e janela parcial | janelas completas de `origem_ns` até `t_fim_carga_ns`; D = 256 s só no alvo a |
| A-9 | 7.14 | duração do trecho saudável | da primeira à última amostra saudável depois do `end`, se a seguinte é anômala |
| A-10 | 7.15 | classe e fase das violações | I1 + I4 + I2 REAL, todas as do CSV da execução |
| A-11 | 7.17 | `convergencia_periodo_s`, `t_pre`, `t_pos`, unidade do volume | período = W da 7.3 (4.1 de `2c8b7b8`); `t_pre` = âncora − lançamento; `t_pos` = `fim_utc` − convergência (superestima); MiB arredondado para cima; teto para cima |
| A-12 | 7.16 | T_exec | `fim_utc` − `recriacao_ms` (sem pós-checagem e h4) |
| A-13 | 7.18 | o `poder.py` importa numpy e não roda em `-I`; semente e `n-sim` não estão fixados | q̂, Clopper-Pearson e taxas calculados aqui; comando gravado com `<S>` e `<N>` |
| A-14 | 7.2, 7.11 | método do quantil | posto mais próximo (`referencia_p99.percentil`) |
| A-15 | 7.8, 7.13, 7.14 | atribuição de amostras de topologia e rodadas às janelas | regra de consumo das sondas (a primeira janela leva o aquecimento da topologia) |
| A-16 | 7.10 | "execuções que convergiram" e Δ_conv | Δ_conv do `comparar_bracos` (desde `t_fim_carga_ns` no braço A); `convergencia_estado` ≠ `convergiu` fora |
| A-17 | 7.3 | a 7.3 pede W ≥ teto + atraso; o código exige prazo < W estrito | fórmula da 7.3; `dados.aceito_pelo_codigo` avisa |

Fora da seção 7: a seção 9 (W-9) diz "regras 7.1 a 7.17" e a tarefa diz "7.1 a 7.18"; a 7.18 foi feita até onde o
`-I` deixa (A-13).

## 6. Observações para o Senior (sem decidir nada)

1. **θ_err_in com zero erro.** Sem erro nas execuções sem falha, θ_err_in sai `5e-324` (o menor *double* positivo):
   qualquer erro em duas janelas seguidas dispara. É a regra aplicada, não defeito, mas vale ter em vista.
2. **W pode sair grande.** No *smoke* com o I-3b local (uma execução, só `Validate` com n ≥ 1000), λ₅ deu 5,4 eventos/s
   e W = 19 s; com W = 19 s, o limite da 7.13 só aceita `kLag` = 1. A vazão da VM com 5 execuções define isso; se λ₅
   ficar abaixo de ~10/s (W ≥ 10 s), `kLag` ≤ 2.
3. **A-3 muda os valores do B-40 parte 2.** Pela leitura literal, θ_out ≈ θ_in e `lag_out` ≈ `lag_in` (sem
   histerese). `valores_b40_parte2` usa a literal; a alternativa está em `dados.alternativa_menor_valor`.

## 7. Pontos que precisam de validação

- Decidir A-3 (literal × menor valor) e A-5 (definição de reentrada), as duas de maior efeito.
- Confirmar as demais leituras (A-1, A-2, A-6 a A-17) ou trocar por emenda ao pré-registro antes do X-18.
- O W-9 parte de `17f2bd4`; a integração já está em `944a36d` (W-2b). Na integração, `docs/implementacao.md` e
  `docs/testes.md` vão conflitar (seções acrescentadas no mesmo ponto); o TD deste item já usa o número 16.
