# Conferência da derivação da recalibração S3 contra os brutos, e o tempo do P1 nas ASYNC-F2 (2026-11-06)

> Senior, 2026-11-06. É a mesma conferência da §7 que fiz na calibração
> (`docs/validacao-derivacao-calibracao-2026-11-06.md`).
>
> - Saída: `dados-vm/cs3-2026-11/derivacao-recal-20261106T145106Z/calibracao-s3-derivacao.json`, SHA-256
>   `b183527a…`. Script da tag `cc2b71c` (`2f9767e0…`), igual à ponta `6d154c6` do W-9 conferida na V-W5c §7.2.
> - Brutos: `dados-vm/cs3-2026-11/rc-20261106T144744Z/campanha-s3-recalibracao/` e `ENTREGA-W9.txt`.
> - Recálculo e reprodução do P1 com código meu, só biblioteca padrão, sem importar o W-9 nem o W-5c. O código ficou fora
>   do repositório, no *scratchpad* da sessão (`vderiv/recalc_rc.py`).

## 0. Veredito

1. **Os valores da derivação estão confirmados.** Tudo o que recalculei por amostragem bate com o JSON até a última
   casa. Valem as ressalvas da seção 3, que são as da calibração mais a R-4.
2. **Com estes parâmetros, o AC-5 chega tarde demais nas ASYNC-F2.** Na reprodução, o P1 dispara entre +9,9 s e
   +10,1 s depois do corte nas quatro execuções, e a parada efetiva fica em cerca de +23,3 s. Das 17 perdas (pares de
   V) dessas execuções, **só 1 aconteceu depois desse instante, ligada ao nó isolado**, o que dá cerca de 6 %. A curva
   de poder da própria derivação dá cerca de 0,03 para uma redução dessa ordem, contra 0,52 com 40 % e 0,98 com 60 %.
   **Não recomendo rodar a avaliação A/B de V com estes parâmetros como teste confirmatório** (seção 4.4).

## 1. Integridade

- O `MANIFEST.sha256` da pasta da derivação está todo OK.
- Entraram 25 execuções aceitas. Ficaram fora as quatro DESCARTADA-INFRA por h4a (i = 0, 10, 17 e 27). A ASYNC-F2 tem
  4 aceitas, por decisão do coordenador.
- A R-4 está aplicada: o JSON traz `W_s.dados.subestimacao_w_menor_que_5_s` com fator 1,25 e o caso "7.3".

## 2. Recálculo por amostragem

| Valor | JSON | Recálculo independente | Situação |
|---|---|---|---|
| p99 ASYNC (7.1) | Validate 4 369 513, Login 6 369 031, Logout 7 052 307, IncrementFailure 4 117 287 ns | os mesmos, por posto mais próximo sem `ReplayValidate` | confere |
| `ReplayValidate` fora de λ₅ e da referência | — | há 1 494 `ReplayValidate` nos CSV das ASYNC sem falha, e nenhum aparece em `p99_ns`, `n` ou `omitidas` da referência. Se entrassem, λ₅ iria a 31,0/s | confere |
| λ₅ e W (7.2, 7.3) | 29,8/s (N = 255); W = 4 s; W_min = 3 | 29,8/s; ⌈100/29,8⌉ = 4; atraso máximo 3,74 ms (`exec-0001-SYNC-F2-A`), W_min 3 | confere |
| θ_lat_in (7.7) | 0,05780346820809249 (N = 313) | maior mínimo de pares = 10/173 (`exec-0026-ASYNC-none-A`, janelas 3 e 4: 11/186 e 10/173), e o `nextafter` dá o valor do JSON | confere |
| `lag_in` (7.11) | 6,881289 ms (N = 260) | posto 258 de 260 = 6,881289 ms | confere |
| `lag_out` (7.12) | 0,939831 ms; trecho 35 | menor candidato com trecho ≤ m = 35 (o anterior dá 52); 60 janelas sem rodada puladas | confere |
| m (7.8) | 35; maior trecho 34 | trecho de 34 janelas com rodada em `exec-0019-ASYNC-F1-A` (posições 16 a 49), seguido da posição 50, alta só pela latência, 182 s depois do `end` | confere |
| `T_hold_viol` (7.15) | 69,500485632 s | `exec-0013-ASYNC-F2-A`: 5 violações *online* do V (sem a sonda), maior intervalo 69,500485632 s (de +45,8 s a +115,3 s do corte) | confere |
| Δ_conv máximo (7.10) | 11,086181803 s; 17 convergiram | 11,086181803 s (`exec-0007-ASYNC-F1-A`); 17 de 17 em `convergiu` | confere |
| assinatura sem resposta (7.13) | 60 janelas, 12 por F1 | 60 janelas: 12 em cada F1, persistência até 12, todas com o suspeito sem resposta REST; 0 no critério, então `kLag` = 1 | confere |
| q̂ por pares (seção 6, 7.18) | F2 4/4 (3, 6, 2 e 6 pares); F4 0/5 | recontei os pares (chave, classe) das três origens, com a fase pela detecção (online e imediata) ou pela invalidação (tardia e varredura). Pares por execução: 3, 6, 2 e 6. Por origem: 0004 sonda 3 / varredura 3; 0013 online 3 / sonda 4 / varredura 2; 0018 sonda 2; 0022 online 1 / sonda 6 / varredura 3. F4: nenhum par. Tudo igual ao JSON | confere |

Pela fórmula:
- `T_cooldown` = 35 × 4 = 140 s.
- `exec_teto` = 300 + 21 + 60 + 600 + 4 + 1,5 × (1,024 + 21,638) = 1018,99, que arredonda para 1019 s.
- Teto da convergência = 600 s, porque 11,09 s < 400 s.
- `T_estavel` = 2 s, sem recaída.
- Poder na F2, modelo independente: 0,517 com 40 % e 0,977 com 60 % (`poder-ASYNC-F2.json`).

## 3. Ressalvas (as da calibração, mais a R-4)

1. **R-4.** Com W = 4 s, as janelas sem rodada (1 em 5) foram puladas. `kLag`, m e o trecho do `lag_out` estão
   subestimados em cerca de 1,25 vez: o m de 35 corresponde a cerca de 44 janelas na grade da avaliação. Está declarado
   no JSON.
2. **A saída R4 continua praticamente sem ocorrer.** Com θ_lat_out = θ_err_out = 0 e m = 35 (140 s), uma vez em
   CONSERVADOR o controlador fica lá. Vale a mesma declaração 3.2.1 da conferência da calibração.
3. **Volume.** A derivação dá 3 MiB (maior 2,99 MiB, `exec-0021-ASYNC-F4-A`). Para a guarda de disco, mantenho a
   recomendação de 14, o maior já medido.
4. **Campo `leitura`.** `valores_b40_parte2.leitura` ainda cita só o complemento 1. As regras que valem são a DT-026
   com os complementos 1, 3 e 3a. É só texto.
5. **P-48.** O P-48 entrou na tag (`cc2b71c`). A ressalva da 7.10 da calibração cai, desde que o *smoke* dele tenha
   sido conferido na integração. Não conferi isso aqui.

## 4. Em quanto tempo o P1 dispara nas ASYNC-F2, e o que um AC-5 nesse instante evitaria

### 4.1 Reprodução das decisões

Refiz as decisões nas trilhas de observáveis das 4 ASYNC-F2, na grade de 4 s alinhada em `origem_ns`, com os parâmetros
calibrados: W = 4 s, k = 2, `kLag` = 1, θ_lat_in, θ_err_in, `lag_in`, teto de 2 000 ms e cota de 1 AC-5. Segui o código:
- **núcleo**: R1, e R2 com topologia, latência, erro e defasagem;
- **política**: CONSERVADOR, vista completa, mesmo suspeito no teto com persistência ≥ `kLag` e suspeito respondendo à
  REST.

Janela sem rodada (artefato do período de 5 s da sonda na recalibração) foi tratada de dois jeitos: pulada, ou como
indeterminada, que é o que o código faria. Os dois dão o mesmo instante.

| Execução | corte (da origem) | 1ª rodada no teto | entrada em CONSERVADOR | P1 dispara | vista completa até | AC-5 PREVISTA no braço A (W 5 s, `kLag` 2, provisórios) |
|---|---|---|---|---|---|---|
| exec-0004 | +6,05 s | +3,95 s | janela 3, R2 por latência, erro e defasagem | **+9,95 s** | +41,9 s | +13,95 s |
| exec-0013 | +5,94 s | +4,07 s | janela 3, R2 por latência, erro e defasagem | **+10,06 s** | +42,1 s | +14,06 s |
| exec-0018 | +6,05 s | +3,95 s | janela 3, R2 por defasagem | **+9,95 s** | +41,9 s | +13,95 s |
| exec-0022 | +6,01 s | +3,99 s | janela 3, R2 por defasagem | **+9,99 s** | +42,0 s | +13,99 s |

Os instantes são relativos ao corte (marcador `start`), salvo a primeira coluna. O mecanismo é o mesmo nas quatro:
1. A primeira rodada depois do corte já sai no teto (janela 2).
2. Na janela 3 a defasagem fica ALTA pela segunda vez seguida. É o k = 2 da R2.
3. O perfil vira CONSERVADOR nessa janela, e a política age no fim dela (`kLag` = 1 já está cumprido).

Na grade da avaliação, com uma rodada por janela de 4 s, o resultado é o mesmo: a rodada da janela 2 começa depois do
corte e sai no teto. **P1 em cerca de +10 s.** Somados os 13,3 s de parada efetiva (I-3), **o nó isolado para em cerca
de +23,3 s**, bem antes da troca de vista (+42 s), então a política não perde a vista completa.

### 4.2 Perdas contra o instante da parada

O "instante do dano" de cada par de V é:
- **forma 1:** o fim do `Logout` confirmado pelo isn3 (11224). Com o isn3 parado, esse `Logout` não seria confirmado;
- **forma 2:** a primeira releitura VALID servida pelo isn3 depois do `Logout` confirmado pela maioria;
- **demais:** a primeira detecção.

Um par é evitável se o dano vem depois da parada efetiva.

| Execução | pares | instantes do dano (s depois do corte) | depois de +23,3 s |
|---|---|---|---|
| exec-0004 | 3 | forma 1: +1,3, +13,9, +14,7 | 0 |
| exec-0013 | 6 | forma 2: +4,3; forma 1: +4,7, +22,6, **+32,6**; I2 + I1 `ADMIN_BLOCK` da mesma sid: +45,5 | 1 (o de +32,6 s) |
| exec-0018 | 2 | forma 2: +7,7, +9,9 | 0 |
| exec-0022 | 6 | forma 1: +0,6, +6,2, +6,7, +14,0; forma 2: +4,6, +5,3 | 0 |

- **O par de I2 e I1 `ADMIN_BLOCK` da exec-0013 (+45,5 s) não é evitável.** O `Block` da identidade `s3-u-025` foi
  confirmado pelo isn1 em +45,35 s, e as releituras VALID vieram do **isn2**, na maioria, até +45,8 s; em +46,0 s a
  leitura já dá INVALID. É janela de cascata do lado da maioria, logo depois da troca de vista, sem relação com o nó
  isolado.
- **Total: 1 de 17 pares evitável, cerca de 6 %.** Sem a forma 2 (sensibilidade), é 1 de 12. Nenhum de F4, onde q̂ = 0.
- A perda de +22,6 s fica 0,8 s antes da parada. Uma variação de cerca de 1 s no instante da parada levaria a contagem,
  no máximo, a 2 de 17.
- Contexto: entre o corte e +45 s, o isn3 confirmou 22 *logouts* nas 4 execuções, 10 deles depois de +23,3 s. Só 1
  desses virou perda. As perdas se concentram nos primeiros ~15 s depois do corte.

### 4.3 Limites da conta

- **É um teto do que o AC-5 evitaria.** Os dados são do braço A, sem ação. Conta como evitada toda perda posterior à
  parada, e o que a parada mudaria na dinâmica (clientes fixados no isn3 recebendo erro) não reduz perdas anteriores.
  Pela própria política, as escritas confirmadas pelo nó isolado entre o corte e a parada continuam perdidas.
- **A amostra é pequena** (4 execuções, 17 pares). A conclusão depende da forma da distribuição dos danos no tempo,
  concentrada no início, e não do número exato.
- **O tempo do P1 é estrutural:** pelo menos k janelas de W depois da primeira rodada no teto, mais a parada. Com
  W = 4 s e k = 2, nenhum `kLag` menor ajuda: `kLag` já é 1.

### 4.4 Decide se vale rodar a avaliação?

Com estes parâmetros, a redução de V que o AC-5 pode produzir nas ASYNC-F2 fica em cerca de 6 % no máximo. A curva de
poder da derivação (12 blocos, modelo independente) dá 0,045 com 10 % e 0,0098 com 0 %, então cerca de 0,03 para 6 %.
**A avaliação A/B de V, como está, não tem como mostrar efeito.** Ela mediria um nulo previsível, que já está
explicado aqui com os dados.

O gargalo está nos dois termos fixos: cerca de 10 s até o P1 (k = 2 janelas de 4 s depois da primeira rodada no teto)
e 13,3 s de parada sob corte. A maioria das perdas acontece antes de +15 s. Opções para o Gestor, nenhuma automática:
1. **Não rodar a avaliação de V como teste confirmatório.** Reportar esta análise como resultado: a detecção por S_lag
   com parada sob corte é lenta demais para a janela de perda medida. A avaliação pode ainda rodar como descritiva de
   custo e de acionamento.
2. **Mudar o desenho antes de rodar**, sempre como emenda declarada e antes dos dados da avaliação. Um alvo seria
   encurtar a parada (13,3 s), que é o maior termo; outro, dispensar o k = 2 do núcleo para a assinatura do P1. Qualquer
   mudança pede nova verificação de falso acionamento, porque a 7.13 contou só a política.
3. **Rodar como está**, sabendo que o poder para o efeito alcançável é de cerca de 3 %.

Recomendo a opção 1, ou a 2 se houver tempo para medir de novo o falso acionamento. A opção 3 gasta VM para confirmar
um nulo que já está previsto.
