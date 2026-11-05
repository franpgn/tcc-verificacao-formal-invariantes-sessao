# B-40: pré-registro da avaliação do controlador na campanha S3, braços A e B: PARTE 1 APROVADA (2026-11-05)

> Senior, 2026-11-05, com o *host* em silêncio (só leitura e escrita de documentos). Branch
> `docs/b40-pre-registro-avaliacao` (worktree `wt-b40`), sobre `docs/pre-registro-calibracao-s3` (agora com o DT-020),
> porque este documento usa as regras de derivação de lá. **Parte 1 aprovada** pelo coordenador em nome do Gestor em
> 2026-11-05 (decisões finais de ~22:30Z; demais itens pela regra permanente do Gestor, só os que têm origem
> rastreável, seção 15), registrada como **DT-021 (PRÉ-REGISTRO)** em `docs/decisoes-tecnicas.md`. Tem duas partes. A
> **parte 1** (este documento) fixa o desenho, os desfechos, a estatística, as regras de perda, o que se mede e os riscos conhecidos, e é
> aprovada antes da calibração. A **parte 2** (seção 11) recebe os valores que as regras da calibração produzirem e é
> commitada antes da primeira noite da avaliação (N4), sem olhar diferença entre braços, e vira entrada própria em
> `docs/decisoes-tecnicas.md`. Sem push.
>
> Pedido do coordenador em nome do Gestor (05/11): incluir uma célula ASYNC F3 descritiva, com a justificativa de por
> que F3 não tem teste de redução (seção 4).

## Siglas

| Sigla | Significado |
|---|---|
| braço A / B | controlador em `shadow` (decide e registra, não atua) / em `active` (atua no *cluster*) |
| bloco | par A e B com a mesma semente, ordem dos braços sorteada |
| V, L | desfechos co-principais: taxa de violações; p99 da ação S3 |
| IU | interseção-união (Berger, 1982): "rejeita" só se as duas hipóteses nulas forem rejeitadas |
| P0, P1, P2 | nenhuma ação; parar o nó suspeito (AC-5); suspender o rebalanceamento (AC-2, fora) |
| d_b | diferença por bloco, B menos A |
| k | número de blocos de uma célula com d_b ≠ 0 |
| parte 2 | valores calibrados, seção 11 |

## 0. Decisões aplicadas

| Decisão | Fonte |
|---|---|
| Co-principais: V = (I1 + I4 + I2 real) × 1000 / ações S3; L = p99 da ação S3 com espera | G-1, `plano-alinhamento-objetivos-2026-11-04.md` §3.1; `Metodologia.tex:349` |
| Regra de sucesso por célula: IU; Holm entre as células de teste sobre `p_IU` | D-1; `Metodologia.tex:351`; `berger_iut_1982`, `holm_sequential_1979` |
| Com zeros, V entra como diferença; razão com exclusão é sensibilidade | G3 de 28/10 |
| Calibração derivada; dados da calibração fora da avaliação | D-12; `docs/pre-registro-calibracao-s3-2026-11-05.md` |
| Braço único B com o controlador completo; P1 = AC-5; P2 fora (`p2Habilitada = false`) | coordenador em nome do Gestor, 05/11; D-B; `opcoes-arquitetura-configuracao-2026-11-05.md` §9.1 |
| `kLag` ≤ 5 pela viabilidade `kLag·W + 13,3 s < 39,8 s` | P-42; `V-S7r.md:45` |
| W ≥ teto da leitura + maior atraso de início | R4 da V-W4 |
| Energia com razão ociosa 0,3 central e 0,5 de sensibilidade; P_max 181 W | Gestor, 05/11; Barroso e Hölzle (2007), p. 36 e 34; ficha do i5-14600KF |
| Religamento depois do fim da carga; `convergencia_estado` | coordenador, 05/11; W-6 |
| h4 do C3 e regra de memória (a aprovar junto com a retomada do *rebaseline*) | C3, DT-018, DT-019; G-3 de `validacao-noite1-2026-11-05.md:647` |
| SYNC F2 com limitação declarada (P1 não dispara em SYNC: R3) | `wt-w4/docs/relatorio-w4-2026-11-05.md:203` |
| Vazão útil não inferior, δ = 10 % | Gestor, 22/10; `Metodologia.tex:353` |
| `comparar_bracos.py` roda com `python -E -P` | G2 de 28/10 |
| Regra dos números | R-N |

## 1. Objeto

Avaliar, em S3, se o controlador do *cluster* reduz a taxa de violações sem piorar o p99 da ação S3 em mais de 10 %,
comparando o braço B (atua) com o braço A (mesmo código, só observa). O braço A é o "modelo estático" do objetivo
específico (5) (`Metodologia.tex:347`). Mesmo jar, mesmo XML sem *caches*, mesmos JSON, mesmo arquivo de controlador
(com os valores da parte 2) e mesma referência de p99 por perfil nos dois braços.

## 2. Braços, blocos e piloto

- **Bloco:** uma execução A e uma B, mesma célula, mesma semente de carga, ordem dos braços sorteada
  (`campanha_s3_matriz.py`, `random.Random(ordem_seed·10⁶ + bloco)`); o *cluster* é recriado antes de cada execução
  (W-6). O orquestrador recusa matriz com pareamento quebrado.
- **Piloto embutido** (`Metodologia.tex:347`, "os três primeiros blocos funcionam como piloto e são aproveitados se o
  protocolo não mudar"). Operacionalização: depois dos 3 primeiros blocos completos da fase `nucleo`, na ordem da
  matriz, o coordenador cria o arquivo `PARAR` e o Senior lê **só** o protocolo (vereditos, tempos, trilhas íntegras,
  h4, religamento, marcadores), nunca V, L nem a vazão. Sem mudança de protocolo, os 3 blocos contam e a fase segue.
  Com mudança, a mudança é registrada antes de retomar, os 3 blocos ficam como dado de piloto (fora da análise) e são
  refeitos com os próximos índices de bloco.

## 3. Células

| Célula | Falha | Papel | Blocos | Fase | Origem |
|---|---|---|---|---|---|
| ASYNC F2 | `isn3` isolado 90 s, a +49 s | **teste** | **12** (seção 7) | núcleo | `Metodologia.tex:347`; plano de 04/11 §5.2 |
| ASYNC F1 | queda silenciosa de `isn2`, 60 s, a +49 s | descritiva (o controlador não faz mal na queda) | 4 | núcleo | **decisão do Gestor, 05/11** (seção 3.1) |
| ASYNC sem falha | — | descritiva (sobrecarga, acionamentos indevidos) | 8 | núcleo | `Metodologia.tex:347` (8 blocos); plano §5.2 dizia 4 |
| SYNC F2 | igual à ASYNC F2 | referência, sem teste (R3) | 4 | núcleo | `Metodologia.tex:347`; R3 |
| ASYNC F4 | perda de 20 % (N3) na saída de `isn3`, 90 s, a +49 s | **teste** | **12** | extra | `Metodologia.tex:347`; parâmetros: C-3 da calibração |
| SYNC F4 | igual à ASYNC F4 | referência, sem teste | 4 | extra | `Metodologia.tex:347` |
| **ASYNC F3** | ***jitter* 20 ± 13 ms (normal) na saída de `isn2`, de +49 a +300 s** | **descritiva (falso alarme)** | **4** | extra | **pedido do Gestor, 05/11; seção 4** |

A SYNC F1 do plano de 04/11 (OP-6) não entra: a `Metodologia.tex:347` não a tem, e o contraste SYNC × ASYNC sob F1
já vem do *rebaseline* em S1/S2. **Total: 2 × (2n + 24) execuções**, isto é, 2 células de teste × 2n, mais sem falha 16,
F1 8, SYNC F2 8, SYNC F4 8 e F3 8: com n = 12, **96 execuções** (seção 7).

Os parâmetros de cada falha (nó, duração, instante, severidade e direção do F4) são os do arquivo `campanha-s3/1` da
calibração (seção 4.1 de lá), os mesmos nos dois braços e nas duas campanhas.

### 3.1 ASYNC F1 é descritiva (decisão do Gestor, 05/11)

Nas palavras do Gestor: "se não temos ação nenhuma, okay retirá-lo". No braço B não há ação para a queda:

- o P1 exige que o suspeito tenha respondido à REST em todas as amostras da janela e que a vista esteja completa
  (`PoliticaPorAssinatura.java:158`, `EstadoTopologia.java:118-129` em `17f2bd4`), e o `isn2` morto não responde;
- o P2 está fora pelo D-B (`p2Habilitada = false`).

Sem ação, não há hipótese de redução a testar. A célula fica, com 4 blocos, para medir que o controlador **não faz mal
na queda**. Por execução, nos dois braços: número de AC-5 aplicadas no braço B (e previstas no A), contadas por alvo;
**nó saudável parado**, isto é, qualquer AC-5 aplicada nesta célula (o alvo, pela condição da política, respondia à
REST; enquanto o `isn2` está morto ela não pode disparar, e depois que ele volta não há partição); p99 da ação S3 e
vazão útil como diferença pareada (log da razão B/A) com IC 95 % *t*, sem teste; violações por braço com
Clopper-Pearson. Na calibração, a F1 × 5 continua como controle negativo do P1 (regra 7.13 de lá).

## 4. A célula ASYNC F3

### 4.1 Por que F3 não tem teste de redução

O plano de 04/11 tirou F3 da avaliação sem justificativa escrita. A justificativa é esta:

1. **Não há violação para reduzir, pelo que se mediu até agora.** No *rebaseline* em S1/S2, nos dois modos, até a
   i = 39 houve 13 execuções F3 aceitas, todas com I1 = I4 = 0 e nenhuma I2 (`runs/rebaseline/exec-*-F3/checagem.json`,
   campo `K9`). A única violação da campanha até aqui foi na i = 22, S1-async-F1, com I1 = 1. Esses números foram lidos
   pelo coordenador na VM durante o segmento 2. No *host* conferi as cinco execuções F3 de i ≤ 17
   (i = 4, 7, 8, 10, 12, `dados-vm/rebaseline-2026-11/noite1-20261105/rebaseline/exec-00{04,07,08,10,12}-*-F3/checagem.json`),
   todas ACEITA com `K9.valor` = {I1: 0, I4: 0, I2: 0}. As 4 F3 do piloto (i = 1005 a 1007 e 1009) também deram zero,
   mas o piloto não entra na campanha. As de i = 18 a 39 e a i = 22 serão conferidas na cópia do segmento 2. **O número
   final sai do fim da campanha do *rebaseline* (X-15)** e entra na parte 2.
2. **Sem violação no braço A, o teste é não avaliável.** Pela regra 3 do plano de alinhamento §3.3, célula em que o
   braço A não tem violação em nenhum bloco tem H_V "não avaliável". Mesmo usando o limite superior de 95 % da fração
   de execuções com violação, 3/13 ≈ 0,23 (`hanley_lippmanhand_zero_1983`), a chance de 7 ou mais blocos de 12 (o número das
   células de teste) terem violação no braço A, condição mínima para o teste rejeitar com Holm (seção 5), é
   P(Binomial(12; 0,23) ≥ 7) ≈ 9 × 10⁻³. Um teste com esse poder só gastaria α e VM.
3. **Não há mecanismo de violação mirado.** F3 não muda topologia nem corta a rede: 20 ± 13 ms de atraso ficam muito
   abaixo da detecção do JGroups (39,8 a 46,4 s) e do prazo Hot Rod de 2 s. Os mecanismos que o AC-5 mira (M1, M5)
   pedem corte (`catalogo-operacao-falha-invariante.md` §4; plano de 04/11 §4.3).
4. **Limite da extrapolação:** os 13 zeros são de S1/S2, não de S3. A própria célula, no braço A, dá a taxa em S3, que
   é relatada como descritiva.

### 4.2 O que a célula mede

A pergunta é de especificidade: o controlador age quando não deve? Por execução, nos dois braços:

| Medida | Definição | Fonte nos brutos |
|---|---|---|
| janelas com defasagem alta | janelas com S_lag ≥ `lag_in` (parte 2) | linhas `defasagem` e `janela` do `observaveis-rep-001.jsonl` |
| amostras no teto | leituras cruzadas que não viram o valor em `tetoLagMs` (2000 ms) | linhas `defasagem` |
| entradas em CONSERVADOR por defasagem | transições com motivo `DEFASAGEM` | `decisoes-rep-001.jsonl` |
| AC-5 desnecessário | braço B: `acao` AC-5 `APLICADA` (qualquer alvo; sem partição, toda parada é desnecessária). Braço A: AC-5 `PREVISTA` | trilha; linha `Fim da carga:` |
| p99 da ação S3, B frente a A | `ln(p99_B/p99_A)` por bloco, média e IC 95 % *t*, **sem teste** | `comparar_bracos.py`, célula `descritiva` |
| vazão útil, B frente a A | log da razão B/A por bloco, média e IC 95 % *t*, sem teste | idem |
| violações | taxa por braço, com Clopper-Pearson (REF-098) | idem |

Fases: `durante` (+49 a +300 s). F3 vai até o fim da carga, então não há fase de recuperação dentro da carga.

### 4.3 Como a F3 roda no S3 (depende do W-6c)

O orquestrador do W-6 não tem F3 (`campanha_s3_matriz.py` só aceita `none|F1|F2|F4`). O W-6c acrescenta, com TDD:

- injetor `scripts/inject-jitter.sh` **sem mudança** (está na lista `003814e9` do *rebaseline*, `7e68962b…`), com os
  parâmetros do *rebaseline*: `--node isn2 --iface eth0 --delay 20ms --jitter 13ms --distribution normal --duration 251`
  a +49 s da âncora (PR §2; DT-011);
- dead-man de 341 s (251 + 90, DT-011) com `tc qdisc del dev eth0 root` pelo `nsenter`, como `run-rebaseline.sh:789-790`,
  e `AccuracySec=1s`;
- marcadores `fault=F3 node=isn2 event=start|end` gravados **pelo orquestrador** (o injetor congelado não tem
  `--marker`): `start` no disparo, `end` na saída do injetor; e a conferência do `netem` a +2 s do disparo (o
  `NETEM_EFETIVA_S` = 51 da C5 do *rebaseline*), sem `netem` ⇒ recusa;
- esquema `campanha-s3/2` com `f3_no`, `f3_delay_ms`, `f3_jitter_ms`, `f3_distribuicao` e `f3_dur_s`, cada um com a
  origem acima; `go rede` também antes da primeira F3;
- a correção B1 da V-W6 vale também para o F3 (AC-5 que para o `isn2` durante o `netem` faz o injetor falhar na
  remoção).

## 5. Desfechos e estatística

### 5.1 Co-principais (células de teste)

| Desfecho | Por execução | Por bloco | Hipótese |
|---|---|---|---|
| V | (I1 + I4 + I2 real) × 1000 / ações S3, fases `durante` + `recuperacao` (contagem do `contar_violacoes`, DT-001, DT-002, H7) | d_b = r_B − r_A | H_V: média(d_b) < 0. Troca de sinal exata, bilateral, α = 0,05; p_V = 1 se a média de d_b ≥ 0 |
| L | `acao_s3_p99_com_espera`, mesmas fases, n ≥ 1000 | d_b = ln(p99_B/p99_A) | H_L (não inferioridade): limite superior do IC 95 % *t* de exp(média d_b) ≤ 1 + δ_L, δ_L = 0,10; p_L na escala bilateral (2·F_t) |

- `p_IU = max(p_V, p_L)` por célula.
- **Holm entre as células de teste, m = 2** (ASYNC F2 e F4), FWER 0,05. A F1 saiu do teste (seção 3.1). Uma célula
  de teste que não rodar (S5, calendário) ou que ficar não avaliável entra com p = 1. O m não muda depois de ver dados
  (ponto 3 da V-W5).
- **Célula "melhoria"**: IU rejeitada depois do Holm, **e** vazão útil não inferior (limite inferior do IC 95 % da
  razão B/A > 0,90), **e** rastreabilidade integral (cada ação do braço B com regra, sinais e leitura de volta, e trilha
  com `meta` e `fim`).
- **H_V não avaliável** quando o braço A não tem violação em nenhum bloco da célula; relatar "não observada em N ações",
  limite superior ≈ 3/N (`hanley_lippmanhand_zero_1983`).
- **Poder:** com Holm sobre 2, a menor `p_IU` precisa ficar abaixo de 0,025, o que exige k ≥ 7 (2/2⁷ = 0,0156; com
  k = 6, 2/2⁶ = 0,031 não basta); a segunda, abaixo de 0,05, exige k ≥ 6 (plano de alinhamento §3.3). O relatório dá
  k e o p mínimo atingível `2/2^k` por célula.

### 5.2 Sensibilidades (sem Holm, reportadas)

(a) razão de taxas com exclusão dos blocos com zero no braço A (G3); (b) teste exato condicional de duas taxas de
Poisson sobre as contagens somadas, rotulado anticonservador; (c) IC por *bootstrap* de blocos da diferença de taxas
(`efron_tibshirani_bootstrap_1993`); (d) energia com razão ociosa 0,5. Fonte: plano de alinhamento §3.3.

### 5.3 Secundários (Holm dentro da célula, família fixa)

Família, nesta ordem: Δ_rec; Δ_conv; divergência persistente (0/1); taxa de erro; falhas percebidas por mil ações;
CPU-s por mil ações úteis; energia estimada (razão 0,3). **7 hipóteses por célula**, Holm a 0,05; quem ficar sem dado
entra com p = 1 (A-5 (ii) da V-W5; B-6, aprovado). Escalas: diferença para taxas, Δ e divergência; log
da razão para CPU e energia (G3; relatório do B-39.4 §2). p99 por operação e cada invariante (I1, I4, I2 real) são
**descritivos**, só com IC, para a família não crescer. Se a família do `comparar_bracos.py` for outra, o código é
ajustado antes da N4 (W-5b).

- Δ_rec: última operação iniciada depois do marcador `end` que terminou em anomalia; censura nos últimos W_c antes de
  `t_fim_carga_ns` (plano de alinhamento §3.4; B-39.4). Sensibilidade: definição da `Metodologia.tex:223`.
- Δ_conv: `t_convergencia_abs_ns − t_religamento_ns` com AC-5 APLICADA; senão `− t_fim_carga_ns`. AC-5 aplicado sem
  `t_religamento_ns`, ou `convergencia_estado = sem_rodada_valida`: Δ_conv e divergência não avaliáveis na execução.

### 5.4 Descritivos do controlador

Transições, tempo em CONSERVADOR, ações executadas e previstas por tipo, tempo com AC-5 ativa (até o fim da carga),
atraso entre o marcador `start` e a primeira ação, acionamentos nas células sem falha, F1 e F3 (W-5, item 1).

## 6. Parâmetros da análise

| Parâmetro | Valor | Origem |
|---|---|---|
| α; IC | 0,05; 95 % | `Metodologia.tex:229, :326` |
| δ_L (`--delta-latencia`) | 0,10 | `Metodologia.tex:229, :351` |
| δ da vazão (`--delta`) | 0,10 | Gestor, 22/10 |
| W_c (`--w-analise-s`) | = W da parte 2 | plano de alinhamento §3.4-3.5 |
| W_recovery (`--w-recovery-s`) | = `w_recovery_s` do arquivo da campanha (C-4 da calibração; recomendação 191 s) | B-39.4 §3; a guarda G4 exige o mesmo valor do programa |
| m (`--m-celulas`) | 2 | seção 5.1 |
| fases | `durante` + `recuperacao`; célula sem falha com `--fases antes` em chamada própria | plano §3.1; B-39.4 §8 |
| P_max; razão ociosa | 181 W; 0,3 e 0,5 | Gestor, 05/11 |
| quantil da linha de base; confiança de p_ref⁺ (Δ_rec de sensibilidade) | 0,95; 0,95 | plano §3.4 |
| blocos e reduções da curva de poder | 12 (fixo); 0 a 1 em passos de 0,1 | seção 7; plano §3.3 |

## 7. Número de blocos das células de teste: 12 (decisão do Gestor, 05/11)

**n = 12** nas duas células de teste (ASYNC F2 e ASYNC F4), fixo, sem regra pelo tempo. As outras células têm o número
fixo da seção 3. Execuções = 2 × (2·12 + 24) = **96**.

- **Origem:** 12 é o teto do plano de 04/11 §4.6; o piso da `Metodologia.tex:347` é 8. O número não depende do efeito
  observado (ST-5).
- **Poder:** com Holm sobre 2, a célula de menor valor-p só rejeita com k ≥ 7 blocos de d_b ≠ 0 (seção 5.1), ou seja,
  7 dos 12 com violação em algum braço. A curva de poder da calibração (regra 7.18 de lá) é só informativa.
- **Custo estimado** (8 min por execução, pré-registro da calibração §8; o tempo real sai da calibração): 12,8 h de VM,
  16,0 h com a margem de 1,25 para reposições h4 (a fração do teto do C3 §4.4, 30 de 120), em 2 blocos de VM de ~10 h
  (fila X-20/X-21). Se o `T_exec` medido passar de ~15 min (96 × 1,25 × 15 min = 30 h), a avaliação passa de 2 blocos
  de VM: isso é calendário e não muda n.

## 8. Matriz e sementes

`campanha_s3_matriz.py gerar --modo avaliacao --nucleo "ASYNC-F2:teste:12,ASYNC-F1:descritiva:4,ASYNC-none:descritiva:8,SYNC-F2:referencia:4"
--extras "ASYNC-F4:teste:12,SYNC-F4:referencia:4,ASYNC-F3:descritiva:4" --ordem-seed <AAAAMMDD do commit da parte 2>
--s0 400000 --passo-semente 100 --i0 0 --b0 1`, saída `runs/campanha-s3-avaliacao/`.

| Número | Valor | Origem |
|---|---|---|
| `--ordem-seed` | data do commit da parte 2, AAAAMMDD | regra declarada antes de qualquer dado (como na calibração) |
| `--s0`, passo | 400 000, 100 | faixa disjunta do *rebaseline* (até 151 100) e da calibração (300 000 + 100·i, abaixo de 400 000 enquanto i < 1000) |
| `--i0`, `--b0` | 0, 1 | diretório de saída próprio |

O F3 na matriz depende do W-6c (seção 4.3).

## 9. Execução, perdas e reposições

- Vereditos do orquestrador: ACEITA, RECUSADA, DESCARTADA-INFRA, PULADA (como na calibração, seção 5 de lá). Só
  blocos com os dois braços ACEITA entram na análise (`manifesto-bracos.tsv`).
- **Bloco perdido é refeito inteiro**, com o próximo bloco livre e semente nova (`repor`; plano de 04/11 §5.2); o braço
  que sobrou vira PULADA. Uma segunda perda do mesmo bloco reposto pede decisão do Gestor.
- **F2/F4 (e F3) no braço B com AC-5 no nó da falha (B1 do W-6b, V-W6b §3.2).** A execução fica ACEITA com o aviso
  "falha efetiva até a parada do nó pelo AC-5" só se valem as quatro condições: (1) o injetor saiu com 1 ou 6; (2) o nó
  da falha estava fora de `running` quando o injetor saiu; (3) a linha `Fim da carga:` e a trilha dão o AC-5 APLICADA
  nesse nó; (4) na F2, o `injetor.log` tem `OK: <no> nao conecta a nenhum par`, e na F4, a linha
  `degradacao aplicada: alvo <no>` e o marcador `start` (R4, exigência do W-6c). Faltando qualquer uma, RECUSADA. Não é
  perda: recusar essas execuções descartaria o braço B justamente quando o controlador age.
- **Fim da falha na análise dessas execuções (N1 da V-W6b, aceita).** Nelas, o fim da falha usado no Δ_rec e nas
  fases é o **fim nominal** (`inj_inicio_offset_s` + `f*_dur_s`), e não o `end` que o injetor gravou ao sair. Motivo:
  no F4, o AC-5 durante o *ping* faz o injetor sair ~50 s antes do fim nominal, e o Δ_rec e os descritivos por fase
  ficariam assimétricos contra o braço B por construção (V e L não mudam, porque `durante` + `recuperacao` vai até o fim
  da carga nos dois braços). O W-6c grava no `execucao.json` o fim nominal e o instante de saída do injetor; o W-5b usa,
  nessas execuções, o maior dos dois como fim da falha. O `marcadores.txt` do injetor não é reescrito.
- **Limitação do F4 (R4 da V-W6b).** Quando o AC-5 corta o *ping* de verificação, o injetor não deixa prova da
  severidade. A evidência de que a degradação foi aplicada fica mais fraca: a linha `degradacao aplicada: alvo <no>`, o
  marcador `start` e o próprio AC-5 no nó degradado. Isso é declarado no texto como limitação das células F4.
- **AC-5 precoce no nó da falha, antes do disparo (O1 da V-W6b).** A guarda de *netns* do orquestrador não resolve o
  PID do nó parado e a **fase para com exit 6**. Vale até o X-17; depois dele, o Senior reavalia se a execução passa a
  ser só RECUSADA (W-6c).
- **Outros veredictos do orquestrador (W-6b):** nó parado sem AC-5 na linha de fim (O8): DESCARTADA-INFRA (P-53 do
  W-6b); carga sem a linha `Fim da carga:`: religamento e DESCARTADA-INFRA, sem parar a fase; *timer*
  `tcc-heal-cs-*` sobrando: o pré-voo recusa com 4.
- **Rastreabilidade como condição de protocolo:** trilha sem `fim`, ou com `linhas_perdidas` > 0, nos dois braços,
  torna a execução DESCARTADA-INFRA (o disco, não o efeito, decide). O orquestrador ainda não lê isso: item do W-6c.
- h4: C3 por execução; parada do C3 §4.3; **teto de reposições h4 = 25 % das execuções planejadas** (C3 §4.4): **24**.
- Regra de memória: a da calibração (seção 5 de lá), se o Gestor aprovar a G-3.
- DT-014 para execução interrompida.
- Célula que fechar com menos blocos que o planejado entra com o que tem; o relatório dá k e `2/2^k`.

## 10. O que se mede, como se reporta e riscos conhecidos antes dos dados

**Sem expectativa numérica de efeito** (decisão do Gestor, 05/11): "não há como saber se o controlador realmente será
responsável por uma piora de 25 % da vazão, assim como não há como saber quanto ele irá melhorar". Nenhuma razão,
nenhum "B ≈ A" e nenhum sentido previsto entram neste pré-registro.

**O que se mede**, em toda célula e nos dois braços: taxa de violações (V), p99 da ação S3 (L), vazão útil, energia
estimada, os secundários da seção 5.3 e o comportamento do controlador (seção 5.4).

**Como se reporta.** Por célula, a diferença pareada B − A (taxas, Δ_rec, Δ_conv, divergência) ou o logaritmo da razão
B/A (p99, vazão útil, CPU, energia), com a média e o IC 95 % *t* sobre os blocos, e o *bootstrap* de blocos como
sensibilidade, **qualquer que seja o sinal**. Ganhos e custos aparecem lado a lado, na mesma tabela da célula.

**Quando se diz "melhora".** Só numa célula de teste (ASYNC F2 ou F4) que passe a regra da seção 5.1: IU rejeitada
depois do Holm sobre 2, vazão útil não inferior e rastreabilidade integral. Uma célula de teste que não passa é relatada
com as mesmas medidas e os mesmos IC, sem "melhora". Nas células descritivas e de referência não há conclusão de
efeito, só as medidas com IC.

**Riscos conhecidos antes dos dados (registro, sem número):**

1. Parar um nó tira de serviço o servidor de aplicação fixado nele até o fim da execução, o que reduz a capacidade de
   atendimento. A vazão útil é medida e entra na regra (`Metodologia.tex:353`, MET-73).
2. O AC-5 não desfaz o que foi aceito antes da parada (`acoes-controlador-cluster-2026-11-05.md` §4).
3. O AC-5 pode parar um nó saudável sem partição real (falso positivo). As células sem falha, F1 e F3 contam isso.
4. Na saída do nó parado, escritas ASYNC ainda em voo podem se perder (risco 1 do AC-5, I-4 do W-2a). As violações que
   isso causar são contadas como as outras.
5. Em SYNC, o P1 pode não disparar (R3, limitação declarada da célula SYNC F2).
6. M2 e M3 (leitura defasada e atualização perdida em ASYNC) não têm ação de servidor (plano de 04/11 §4.3).
7. Os limiares saem de 5 execuções sem falha pelo critério de zero ocorrências (C-8 da calibração), com 3/N
   reportado; o acionamento indevido é medido de novo aqui, nas células sem falha, F1 e F3.

## 11. Parte 2: valores a preencher depois da calibração

Commit próprio, antes da N4, preenchido pelo Senior com a saída do `analysis/calibracao_s3.py` (W-9) conferida contra
os brutos, **sem nenhuma comparação entre braços**:

| Item | Regra (pré-registro da calibração) |
|---|---|
| W, λ₅, atraso máximo de início | 7.2, 7.3 |
| viabilidade do AC-5 (se inviável, este B-40 é revisto antes da N4) | 7.4 |
| θ_lat_in/out, θ_err_in/out, k = 2, m, `T_cooldown`, `n_err` | 7.6 a 7.9 |
| `lag_in`, `lag_out`, `kLag` | 7.11 a 7.13 |
| `T_estavel`, `T_hold_viol` | 7.14, 7.15 |
| teto da convergência | 7.10 |
| referências de p99 ASYNC e SYNC (SHA-256) | 7.1 |
| `T_exec` medido (n = 12 já fixado, seção 7) | 7.16 |
| `exec_teto_s`, `volume_exec_mb` | 7.17 |
| SHA-256 do arquivo do controlador, do `campanha-s3/2`, da matriz e do jar | — |
| contagem final de F3 do *rebaseline* (seção 4.1) | X-15 |

## 12. Fora do escopo

OP-11 (terceiro braço de ablação; a decisão é braço único B); OP-10 (campanha S3 completa de caracterização);
AC-6 (verificada e não adotada, `opcoes-arquitetura-configuracao-2026-11-05.md` §9.1); P2/AC-2 (exploratória, D-B);
AC-3 (`remote-timeout`); E-1 (`FORCE_SYNCHRONOUS`). Qualquer um deles na avaliação exige complemento antes de dados.

## 13. Pré-requisitos

1. Pré-registro da calibração aprovado e executado (X-18), com a parte 2 commitada.
2. W-6b **feito** (`bef758e`, com a correção B2: `--referencia-async`/`--referencia-sync`; V-W6b `081e1c4`) e a
   integração com o W-2b e o I-1; o W-6c: F3 (seção 4.3), leitura do *swap*, `linhas_perdidas` e `fim` da trilha, fim
   nominal e instante de saída do injetor no `execucao.json` (N1) e as linhas exigidas no F4 (R4) (seção 9).
3. W-9 validado; W-5 (`comparar-bracos/2`) integrado, com a família de secundários da seção 5.3.
4. S5 aprovado (já está) para as células F4.
5. `--dry-run` da avaliação com a matriz e os arquivos da parte 2.

## 14. Diferenças em relação ao texto (para o Escritor, pelo coordenador, se o Gestor aprovar)

| Onde | Texto hoje | Este B-40 |
|---|---|---|
| `Metodologia.tex:347` | quatro células ASYNC de 8 blocos (sem falha, F2, F4, F1) e duas SYNC de referência; "cerca de 80 execuções" | F2 e F4 de teste com 12 blocos; F1 descritiva com 4; sem falha descritiva com 8; F3 descritiva com 4; SYNC F2 e F4 de referência com 4; 96 execuções |
| `Metodologia.tex:347` | célula sem falha "para medir sobrecarga e acionamentos indevidos" | igual; explicitar que é descritiva |
| `Metodologia.tex:349` | "o menor valor-p bilateral … só fica abaixo de 0,05 a partir de k = 6" | acrescentar que, com Holm sobre duas células, a de menor valor-p precisa de k ≥ 7 (2/2⁷ < 0,025) |
| `Metodologia.tex:351` | Holm "das três células com falha" | **das duas células de teste, F2 e F4**. Razão: no braço B não há ação para a queda (o P1 exige resposta REST do suspeito, e o P2 está fora), então a F1 não tem hipótese de redução; ela vira descritiva, para mostrar que o controlador não faz mal na queda. Dizer também que a F3 é descritiva e por quê (seção 4.1) |
| `Metodologia.tex:353` (MET-73) | "o pré-registro declara essa troca entre violações e vazão como expectativa" | o pré-registro **registra o risco** (parar o nó reduz a capacidade de atendimento), sem expectativa; a vazão é medida e entra na regra (seção 10) |
| `Metodologia.tex:353` | "limiares calibrados com dados da campanha com o cenário S3" | acrescentar que a calibração não roda F3, para não ajustar o limiar olhando a célula em que ele é avaliado |

## 15. Decisões do Gestor e origem de cada item

Decisões finais do Gestor (05/11, ~22:30Z): n = 12 (B-3), critério de falso acionamento (B-10), F1 descritiva (B-4) e
nenhuma expectativa numérica de efeito (B-11). Correções da V-W6b aceitas pelo coordenador (seção 9): condições do B1,
N1, R4 e O1. Os demais itens ficam aprovados pela regra permanente do Gestor, **só os que têm origem rastreável**.

| # | Item | Valor aprovado | Origem | Situação |
|---|---|---|---|---|
| B-1 | Célula ASYNC F3 descritiva, 4 blocos, nas extras | sim | pedido do Gestor (05/11); 4 blocos como as células sem teste da `Metodologia.tex:347`; seção 4 | aprovado |
| B-2 | F3 fora da calibração | sim | C-1 da calibração (DT-020) | aprovado |
| B-3 | Número de blocos das células de teste (F2, F4) | **12** | decisão do Gestor; teto do plano de 04/11 §4.6 | **decidido** |
| B-4 | ASYNC F1 | descritiva com 4 blocos; Holm sobre 2 (F2, F4) | decisão do Gestor; sem ação para a queda (`PoliticaPorAssinatura.java:158`; D-B) | **decidido** |
| B-5 | SYNC F1 fora | sim | `Metodologia.tex:347`; contraste SYNC × ASYNC sob F1 no *rebaseline* | aprovado (regra permanente) |
| B-6 | Família dos secundários (7 hipóteses) e p = 1 para quem ficar sem dado | sim | lista do plano de alinhamento §3.1; exigência A-5 (ii) da V-W5 | aprovado (regra permanente) |
| B-7 | Rastreabilidade (`fim` e `linhas_perdidas` = 0) como condição de protocolo | sim | critério de rastreabilidade (`Metodologia.tex:229`); contrato JSONL (`linhas_perdidas`) | aprovado (regra permanente) |
| B-8 | Piloto embutido nos 3 primeiros blocos, lido só no protocolo | sim | `Metodologia.tex:347` | aprovado (regra permanente) |
| B-9 | Herdadas da calibração: `f4_*`, `w_recovery_s`, `encerramento_folga_s`, `exec_teto_s` (1010 s), `volume_exec_mb` | como lá | C-3 a C-7 (DT-020) | aprovado |
| B-10 | Critério de falso acionamento dos limiares | **zero ocorrências, 3/N reportado** | decisão do Gestor (C-8, DT-020) | **decidido** |
| B-11 | Sem expectativa numérica de efeito; ganhos e custos com IC lado a lado; "melhora" só pela regra | sim | decisão do Gestor | **decidido** |

Nenhum item da parte 1 ficou sem origem rastreável. A parte 2 (seção 11) é preenchida pelas regras da calibração.
