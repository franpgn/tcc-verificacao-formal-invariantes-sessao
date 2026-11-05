# V-W9: validação independente do script de derivação da calibração S3 (2026-11-05)

> Senior, 2026-11-05, antes do X-18. Alvo: `analysis/calibracao_s3.py` e `analysis/test_calibracao_s3.py` na branch
> `feature/w9-calibracao-s3`, ponta `63c976f` (relatório do Worker em `docs/relatorio-w9-2026-11-05.md`, §1 a §8).
> Regras: pré-registro da calibração §7 (`docs/pre-registro-calibracao-s3-2026-11-05.md`), complemento 1 (DT-022), que
> prevalece onde diverge, e complemento 2 (DT-023, *swap*, sem efeito no script), todos na branch
> `docs/pre-registro-calibracao-s3`, ponta `ddce38f`. Código do controlador conferido na integração (`wt-ctrl-integ`,
> branch `feature/controlador-integracao`, ponta `fdcf0ae`). Orquestrador conferido no `wt-w6` (`081e1c4`) e na
> integração. Só testes Python; nada de Maven nem Podman. O código do Worker não foi alterado.

## 0. Veredito

**Aprovado com ressalvas.** O script implementa as regras 7.1 a 7.18 como o complemento 1 as escreve, imita o
controlador nos pontos que decidem limiar (`>=`/`>`, janela indeterminada, mínimos de amostra, persistência e predicado
do P1) e lê o formato que o orquestrador grava. Os testes passam nos dois modos e pegam 16 de 18 mutações próprias; as
2 que sobrevivem são equivalentes.

**Nada no script bloqueia a calibração.** Os dois achados que mudam valores derivados estão no **texto das regras**,
não no código (R-1 e R-2, seção 6). Como mudam a derivação, precisam de emenda antes de alguém ler dado da
calibração. Recomendo fechá-los num complemento 3 antes do X-18. Se o Gestor preferir não segurar a VM, o complemento
pode ser commitado durante o X-18, desde que ninguém abra os brutos antes do commit, e isso fica declarado.

## 1. Regra a regra (7.1 a 7.18, com o complemento 1)

| Regra | Situação | O que foi conferido |
|---|---|---|
| Ordem (C1 §1) | confere | 7.1 antes do λ₅ (define as operações elegíveis), depois W, `kLag` e reserva, 7.5, θ_in e `lag_in`, m, θ_out e `lag_out`, o resto; todas as regras na grade do W final |
| 7.1 | confere | `referencia_p99.gerar_referencia(..., "S3", perfil, 1000)` sobre ASYNC-none e SYNC-none; bytes do arquivo iguais aos do `gravar_json`, SHA-256 no JSON |
| 7.2 | confere | janelas de 5 s alinhadas em `origem_ns`, completas até `t_fim_carga_ns`, evento pela janela do `end_ns`, elegível = operação da referência ASYNC; quantil por posto mais próximo (A-14), em `Fraction` |
| 7.3 + C1 §4.2 | confere | `⌈100/λ₅⌉` exato; `W_min = ⌊teto + atraso_max⌋ + 1` (estrito); atraso = `t_inicio_ns` − início da janela de 5 s da calibração, sobre todas as aceitas (A-6). Margem do texto: o `atraso_max` é censurado pela própria agenda (com W = 5 s, `cabeNaJanela` não roda rodada com atraso > 3 s, e ela não aparece no JSONL). O texto manda usar as linhas `defasagem`, e é o que o script faz; vale declarar |
| 7.4 + C1 §4.3 | confere com o texto | ver R-1: o texto conta `kLag·W`, e o controlador só age depois de `max(k, kLag)` janelas |
| 7.5 | confere | excedência estrita (`latência > p99`), referência do perfil da execução, `n_lat` = 100 |
| 7.6, 7.9 | confere | k = 2, `n_err` = 100 |
| 7.7 | confere | menor θ com zero ocorrências = `nextafter` do maior mínimo de pares (A-2); par com janela indeterminada não conta, como no código (`persistenciaLat` zera com taxa nula) |
| 7.8 m + C1 §3 | confere, com margem | "alta" = `algumAlto` (violação, topologia, lat ≥ θ_lat_in, erro ≥ θ_err_in, lag ≥ `lag_in`); "sem alto" não usa θ_out; "imediatamente seguido"; trecho que chega ao fim não conta; sem trecho, m = 1; sem janela depois do `end`, não derivado. A margem (trecho que atravessa o `end`) está na seção 3 (R-3) |
| 7.8 θ_out + C1 §2 | confere | **menor** c em {0} ∪ {observados < θ_in}; banda estrita `c < x < θ_in`; indeterminada quebra o trecho; igualdade com c é calma; ASYNC sem falha |
| 7.8 `T_cooldown` | confere | m·W |
| 7.10 | confere | 600 s se o maior Δ_conv < 400 s (comparação exata), senão 1,5 × o maior; só `convergiu`; `n_fora` ao lado |
| 7.11 | confere | q99 por posto mais próximo sobre o `lag_max_ms` de cada rodada das ASYNC sem falha |
| 7.12 + C1 §2 | confere | a mesma regra do θ_out sobre o `lagMaxMs` da janela, com `lag_in`; janela sem rodada fora da série (pulada, C1 §4.1) |
| 7.13 | confere | predicado igual ao de `PoliticaPorAssinatura.java:158`; persistência como `:198-214` (teto com `>=`, mesmo suspeito, zera abaixo do teto ou sem suspeito); `vistaCompleta` e `respondeuEmTodas` como `EstadoTopologia.java:118-129` e `:186-189`; assinatura sem resposta contada à parte, com motivo (suspeito sem resposta, outro nó sem resposta, vista incompleta), fora do critério; CONSERVADOR fora da contagem, como o texto manda; `kLag` = 1 + maior persistência contada |
| Reserva de W (C1 §4.3) | confere | viável no W da 7.3 fica; senão, o **maior** W' viável de W − 1 até W_min, com `kLag_min(W')` recalculado na grade W'; todas as regras seguintes usam W'; perda de cobertura da latência declarada com a fração; sem W' viável, AC-5 inviável com a lista de candidatos e W da 7.3 mantido |
| 7.14 | confere | trechos saudáveis depois do `end`, da primeira à última amostra saudável quando a seguinte é anômala (A-9), mais 2 s; sem recaída, 2 s; F1, F2 (ASYNC e SYNC) e F4 |
| 7.15 | confere | maior intervalo entre violações I1 + I4 + I2 REAL seguidas na mesma execução, todas as aceitas; sem duas, W |
| 7.16 | confere | n = 12; `T_exec` = `fim_utc` − `recriacao_ms`; ciclo completo ao lado |
| 7.17 | confere | fórmula de 4.1 com o período = W final, `T_enc` = 21 s, teto da 7.10, maior `t_pre` e `t_pos`; nunca abaixo de 1010 s; volume em MiB para cima |
| 7.18 | confere | q̂ com Clopper-Pearson de 95 %, fases `durante` + `recuperacao`; comando do `poder.py` com semente 20261105, 10 000 simulações, m = 2, 12 blocos; não roda em `-I` (numpy), como declarado |

## 2. Fidelidade ao código do controlador

| Ponto | Código | Script | Situação |
|---|---|---|---|
| janela do evento | `AgregadorDeJanela.acumuladorDe(fimNs)`: `floorDiv(fim − origem, W)`; antes da origem não conta | idem | confere |
| elegível e excedência | `referencia.contem(op)`; `latenciaNs() > p99` | `op in p99`; `en − st > p99[op]` | confere |
| erro | `ERROR_TRANSPORT` ou `ERROR_UNAVAILABLE` | idem | confere |
| taxa indeterminada | `den == 0 \|\| den < minimo` → nulo | idem, com `n_lat` = `n_err` = 100 | confere |
| alto | `taxa >= θ_in`; `lagMax >= lagIn` (ALTA) | idem | confere |
| calmo | `taxa <= θ_out` ou nula; `lagMax <= lagOut` | banda estrita no θ_out | confere |
| resumo da defasagem | `SondaDefasagem.resumir`: lag de cada nó = máximo entre rodadas; máximo a partir de 0; suspeito = máximo único, empate dá nulo | `resumo_lag`, mesma lógica (resultado não depende da ordem) | confere |
| consumo | rodada com `tInicioNs < fim` e amostra com `tNs < fim` ainda não consumidas; a 1ª janela leva o aquecimento da topologia | janela que contém `t_inicio_ns` / `t_ns`; antes da origem vai à 1ª | confere com o C1 §4.1 (aproximação abaixo) |
| topologia da janela | `resumirComNosAte`: TODAS se respostas ≥ amostras; `vistaCompleta`: todo nó TODAS e menor = maior = `porNo.size()` | idem | confere |
| persistência do P1 | atualizada em toda janela, antes do teste de perfil (`planejar`, `:114`); zera com `lagMax` nulo | idem, mas janela sem rodada é pulada (C1 §4.1 manda) | confere com o texto |

Aproximações que não mudam o veredito, todas no sentido conservador ou de efeito de milissegundos: (1) o código
consome rodadas e amostras pela hora em que chegam (as atrasadas vão à janela seguinte, `defasagem_rodadas_atrasadas`),
o script pelo instante de início; (2) evento que chega mais de 100 ms depois da marca d'água não entra na janela no
código (`atrasados_total`), e no script entra; (3) violação atrasada é promovida à janela seguinte no código; (4) na
janela em que R4 dispara, o laço retorna antes do `planejar` (`LacoDeControle.java:322-324`), e a janela seguinte zera
a persistência do P1 pelo salto de `fimNs`; o script não zera, o que só aumenta a persistência contada.

## 3. As duas leituras do Worker (relatório §8)

**(a) Trecho máximo no m (R-3, margem do texto).** O C1 §3 diz "trecho máximo de janelas sem sinal alto que começa em
ou depois do marcador `end`". O Worker toma o trecho máximo sobre a execução inteira, e o que começa antes do `end`
não conta. É uma leitura literal possível. A outra, igualmente literal, toma o trecho máximo dentro das janelas
depois do `end` (o trecho que atravessa o `end` conta a partir da primeira janela depois dele). A segunda dá m igual
ou maior, trata o `end` como a 7.14 trata (A-9 corta o trecho saudável no `end`) e cobre o caso que m existe para
cobrir: no código, as calmas começam a contar antes do `end`, a saída pode ocorrer depois dele e a janela alta
seguinte produz reentrada. Contar o trecho inteiro, desde antes do `end`, não serve, porque na F4 um trecho calmo
pode vir do começo da carga e inflar m. O efeito prático é pequeno: na F1 e na F2 as janelas da falha são altas
(topologia), e só a F4 deve produzir trecho calmo atravessando o `end`. **Recomendação:** adotar a leitura cortada no
`end`. A mudança tem 2 linhas em `m_reentrada` e 1 teste. Se o Gestor mantiver a literal do Worker, o JSON deveria pelo
menos listar os trechos que atravessam o `end` (quantos e o maior), como diagnóstico.

**(b) Reserva com perda total de cobertura (R-2, lacuna do texto).** A leitura do Worker ("não derivado") é a única
que o texto permite. Consequência no controlador real:

- **CONSERVADOR continua alcançável sem o sinal de latência.** Pelo `Controlador.java:121-125`, a entrada vem de
  violação (R1), de uma janela de topologia anômala ou de k = 2 janelas seguidas com lag ≥ `lag_in` (R2). No cenário
  do P1 (vista completa, S_lag no teto), o lag está ALTA desde a primeira janela com rodada no teto, porque `lag_in`
  ≤ teto. O perfil vira CONSERVADOR na segunda janela, na mesma avaliação em que a política é consultada
  (`LacoDeControle.java:314` e `:328`), e o P1 pode agir ali. A latência não é necessária para o P1.
- **O arquivo do controlador não fecha.** O carregamento exige θ finitos e não negativos com `θ_out < θ_in`, m ≥ 1 e
  `cooldownNs` (`ParametrosControlador.java:74-90`). Com θ_lat_in, m e `T_cooldown` "não derivados", o B-40 parte 2
  não tem como montar o JSON da avaliação, e escolher os valores depois de ver os dados quebraria o pré-registro.
- **Esse caso deixa de ser raro com a R-1.** Com o *smoke* do I-3b (λ₅ = 5,4/s, W = 19 s) e a viabilidade corrigida,
  a reserva vai a W' ≤ 13 s, onde a janela tem cerca de 70 eventos elegíveis, abaixo de `n_lat` = 100.
- **O que o B-40 precisa declarar** (proposta para o complemento 3): sem janela determinada nas ASYNC sem falha em W',
  o sinal de latência fica **sem cobertura**. θ_lat_in vai a um valor acima de 1 (inalcançável, porque a taxa é ≤ 1) e
  θ_lat_out = 1. m e `T_cooldown` saem da 7.8 com esse valor, que é o que o controlador fará. O texto diz que S_lat não
  atua na avaliação e que só a defasagem, a topologia e as violações levam ao CONSERVADOR. A mesma regra vale para
  θ_err se a taxa de erro também ficar sem cobertura. No script, a mudança é pequena: substituir o "não derivado" por
  esse valor quando o motivo for a cobertura nula.

## 4. Testes e mutações

- `python3 -I -m unittest discover -s analysis -p "test_calibracao_s3.py"`: **62 testes, OK, 2 pulados** (paridade com
  `comparar_bracos` e `scipy`, que exigem numpy).
- `python3 -E -P -m unittest discover -s analysis -p "test_calibracao_s3.py"`: **62 testes, OK.**
- Mutações próprias, cada uma numa cópia do diretório `analysis/` fora do repositório, com a suíte em `-I`:

| # | Regra | Mutação | Resultado |
|---|---|---|---|
| 0 | 7.7 | θ = maior mínimo, sem `nextafter` | morta (5 testes) |
| 1 | 7.7 | máximo do par no lugar do mínimo | morta |
| 2 | 7.7 | janela indeterminada vira 0 | morta |
| 3 | 7.8 m | trecho que chega ao fim conta | morta |
| 4 | 7.8 m | trecho que atravessa o `end` conta inteiro | morta |
| 5 | 7.8 m | m = L, sem o +1 | morta |
| 6 | 7.8 θ_out | maior valor (a literal antiga) | morta |
| 7 | 7.12 | banda com `<=` embaixo | morta |
| 8 | 7.12 | `t < m` no lugar de `t <= m` | morta |
| 9 | 7.13 | sem exigir vista completa | morta |
| 10 | 7.13 | sem exigir resposta do suspeito | **viva, equivalente**: com vista completa todo nó respondeu em todas, e o carregamento recusa rótulos fora de correspondência |
| 11 | 7.13 | teto estrito (`lag <= teto` zera) | morta (14 testes) |
| 12 | 7.13 | janela sem rodada zera a persistência | morta |
| 13 | reserva | menor W' viável no lugar do maior | morta |
| 14 | reserva | exclui W_min | morta |
| 15 | reserva | limite com `<=` | **viva, equivalente**: `kLag·W` é inteiro e nunca vale 26,5 |
| 16 | 7.3 | W_min pelo teto (não estrito) | morta |
| 17 | reserva | W' não passa às regras seguintes | morta |

## 5. Entrada real

| Item | Gravado por | Lido pelo script | Situação |
|---|---|---|---|
| diretório | `dir_exec`: `<--saida>/exec-%04d-<celula>-<braco>`; abortada vira `<dir>.abortada-<UTC>` (`run-campanha-s3.sh:267`, `:1347`) | `glob("exec-*")`; `.abortada-` vai às excluídas | confere |
| arquivos | gravados em `.scratch` e movidos para o diretório da execução (`:1388-1394`) | no diretório da execução | confere |
| `execucao.json` | `i`, `fase`, `celula` (`ASYNC-none` etc., `campanha_s3_matriz.py`), `braco`, `controller`, `modo` (minúsculo), `falha`, `seed`, `recriacao_ms`, `lancamento_ms`, `ancora_ms`, `fim_utc` com milissegundos (`iso_ms`, `:194`) | os mesmos; `fim_utc` com até 3 casas | confere |
| `checagem.json` | `i`, `fase`, `celula`, `bloco`, `braco`, `veredito`, `motivos`, `avisos` | `braco`, `veredito`, `celula` | confere |
| marcadores | `fault=F<n> node=<no> event=start\|end t_epoch_ns=<n>` (F1, F2, F4) | `fases.ler_marcadores` (1º `start`, último `end`) | confere |
| CSV de eventos | contrato v2 com `meta-csv-rep-001.json` (`MetaCsv.java:37`) | `energia_proxy.ler_eventos_inicio` | confere |
| violações | `violacoes-rep-001.csv` com a coluna `classificacao` (`WorkloadMain.java:514-516`) | I1, I4 e I2 REAL | confere |
| resumo de convergência | `convergencia-resumo-rep-001.json` (`WorkloadMain.java:717`) | `t_fim_carga_ns`, `t_convergencia_abs_ns`, `convergencia_estado` | confere; sem ele a derivação para com exit 2 |
| JSONL `observaveis` | meta 1.2 (`nos_esperados`, `origem_ns`, `parametros_defasagem.periodo_ns`), `topologia` (`t_ns`, `anomala`, `por_no` com `no`, `respondeu`, `numero_de_nos`), `defasagem` (`t_inicio_ns`, `teto_ms`, `lag_max_ms`, `lag_por_no` com `no`, `lag_ms`) (`FormatoJsonl.java:38-201`; contrato v1, linhas `topologia` e `defasagem`) | os mesmos campos; última linha truncada descartada | confere |
| JSONL `decisoes` | meta com `modo`, `cenario`, `replicacao`, `parametros_politica.teto_lag_ms`; `acao` com `operacao`, `acao.tipo` = `AC-5`, `resultado` = `PREVISTA` | os mesmos | confere |

O `run-campanha-s3.sh` do `wt-w6` e o da integração diferem só na releitura da meta da trilha (I-1 da V-W6), sem
mudança de formato.

## 6. Achados e o que bloqueia

| Id | Onde | Achado | Bloqueia? |
|---|---|---|---|
| **R-1** | texto (7.4, 7.13, C1 §4.3) | A viabilidade conta `kLag·W + 13,3 < 39,8`, mas o P1 exige CONSERVADOR (`PoliticaPorAssinatura.java:152`), e no cenário do P1 o perfil só chega lá depois de k = 2 janelas de lag ALTA. O AC-5 age, no mínimo, ao fim da janela `max(k, kLag)`. Com `kLag` = 1, a regra aceita W de 14 a 26 s, e o tempo real fica em 2W + 13,3 ≥ 41,3 s, acima dos 39,8 s. Depois da troca de vista, a vista deixa de ser completa e o P1 não age mais, então a célula de teste perde o efeito que mede. **Emenda proposta:** "viável em W'" = `max(k, kLag_min(W'))·W' + 13,3 < 39,8`, o que dá W' ≤ 13 s para `kLag` ≤ 2. O texto também não conta a fase do corte dentro da janela (até um W a mais); é convenção anterior, e basta declarar | precisa de emenda antes da derivação; recomendo antes do X-18 |
| **R-2** | texto (C1 §4.3) | perda total de cobertura da latência deixa θ_lat_in, m, `T_cooldown` e θ_lat_out sem valor, e o arquivo da avaliação não fecha; com a R-1 o caso fica provável. Proposta na seção 3(b) | idem, junto com a R-1 |
| R-3 | texto (C1 §3) | trecho do m que atravessa o `end`: a leitura do Worker é literal, mas a cortada no `end` é mais conservadora e coerente com a 7.14 | não; decisão do Gestor (recomendo a cortada) |
| R-4 | texto (C1 §4.1) | com W final < 5 s, as janelas sem rodada são puladas, e persistência e trechos ficam contados em rodadas de 5 s, não em janelas. Isso subestima `kLag`, m e o trecho do `lag_out` em cerca de 5/W, ao contrário do que o C1 §4.1 chama de conservador. Com a R-1 isso só acontece se `kLag_min` passar de 5 | não; declarar se W final < 5 s |
| R-5 | código | `viabilidade()` não é usada pela `derivar` (só pelos testes); o §6, item 3, do relatório do Worker ainda fala da leitura literal do θ_out, já superada pelo §8 | não |

Mudanças de código que a R-1 e a R-2 pedem, se aprovadas: em `reserva_w`, `_cabe(max(K_NUCLEO, k), w)`; em `derivar`,
θ_lat_in/θ_lat_out (e θ_err, se for o caso) com o valor de "sem cobertura" quando o motivo for a cobertura nula, para
m e `T_cooldown` derivarem. Isso dá alguns testes novos e uma conferência curta do diff pelo Senior, sem nova validação
completa.

## 7. Pontos para o Gestor

1. **R-1:** aprovar a viabilidade com `max(k, kLag)`. Sem ela, um `kLag` = 1 com W ≥ 14 s é declarado viável e não age
   a tempo.
2. **R-2:** aprovar a regra de "latência sem cobertura" (θ_lat_in acima de 1, θ_lat_out = 1, m e `T_cooldown` derivados
   com eles, e a declaração no B-40).
3. **R-3:** leitura do m cortada no `end` (recomendada) ou a literal do Worker, com diagnóstico.
4. **Quando:** complemento 3 antes do X-18 (recomendado) ou durante ele, sem abrir os brutos antes do commit.
