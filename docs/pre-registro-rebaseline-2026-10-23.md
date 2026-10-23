# Pré-registro do *rebaseline* S1/S2 em DIST_SYNC e DIST_ASYNC: RASCUNHO para aprovação (2026-10-23)

> Senior, 2026-10-23. **Rascunho. Nada aqui vale como pré-registro até o Gestor aprovar.** Depois da
> aprovação, os valores fixados viram uma entrada **PRÉ-REGISTRO** em `docs/decisoes-tecnicas.md`
> (proposta: DT-009), commitada antes da primeira execução do piloto, e não podem ser ajustados depois
> de ver dados (regra do cabeçalho de `decisoes-tecnicas.md:3-7`). Sem push.
>
> Estado de partida: bloco B-43 fechado em B-43.1..B-43.4, 6, 7, 7b, 8 e 9 (este último validado com
> ressalva em `validacao-b43-9-2026-10-23.md`). Não feitos: B-43.5 (gerador da mistura semeado) e a
> parte ii do B-43.3a (guarda *online* e recusa no `percentis.py`).

## 1. O que é o *rebaseline*

1. **Substitui as Tabelas 5 e 6 da monografia** (`tcc-escrita/Desenvolvimento.tex:49-68`,
   `tab-baseline-f1-p99`, e `:73-90`, `tab-baseline-f3-p99`). Essas tabelas vieram da bateria de 23/06
   (`diagnostico-login-blocked-2026-10-22.md:11`), em que todo Login deu BLOCKED e todo Validate/Logout
   leu chave inexistente. Elas mediram recusa e leitura vazia, não sessões. O texto também afirma
   `DIST_ASYNC` (`Desenvolvimento.tex:43`), mas os *caches* usados eram SYNC
   (`plano-implementacao-controlador-2026-10-22.md:95-106`).
2. **Mede os dois modos.** DIST_SYNC (controle) e DIST_ASYNC (alvo), como manda
   `Metodologia.tex:168` (T1). O plano já previa o *rebaseline* ASYNC "ao lado da Tabela 5"
   (`plano-implementacao-controlador-2026-10-22.md:512`); aqui os dois modos são medidos na mesma
   campanha, com o mesmo jar, para que a comparação SYNC × ASYNC não misture versões.
3. **Mistura v3, não v2.** DT-005 manteve `v2` como padrão do `--mix` só porque os testes o fixam, e
   mandou toda execução de medição passar `--mix v3` (`decisoes-tecnicas.md:69-79`). A v2 não é
   estacionária: a fração INVALID chega a 69 % em S1 e 54 % em S2 em 60 s
   (`validacao-b43-2-2026-10-22.md:28`), e o p99 de Validate mediria a deriva da mistura. A v3 tem
   tolerâncias de estacionariedade pré-registradas (DT-004, DT-007, DT-008) e passou nelas em todos os
   *smokes* desde o B-43.7b, inclusive o Q6 de 600 s do B-43.9.
4. **S3 fica fora.** O S3 é a simulação de ciclo de vida da T-F, com carga baixa (ordem de 10² ops/s,
   `plano-fechamento-etapa-formal-2026-10-15.md:408`) e foco em semântica, não em latência. A própria
   T-F reexecuta as referências sem falha, F1 e F3 com S3 (mesma linha). Pôr S3 aqui duplicaria a T-F.
5. **Desfecho principal:** p99 de latência por operação, por execução. Secundários: p50, p95, taxa de
   erro por código, vazão, e as contagens do auditor (I1/I2/I4, I5/I6 da rodada final), reportadas e
   não usadas como critério de sanidade, salvo onde a seção 5 diz.

## 2. Matriz de execuções

| Fator | Níveis | Fonte |
|---|---|---|
| Cenário | S1 (50/50), S2 (95/5), mistura v3 | DT-005 |
| Modo | `--replication sync` (`sessions`, `counters`), `--replication async` (`sessions-async`, `counters-async`) | B-42.1; `cluster/infinispan-cluster.xml:22-46` (na ponta da integração) |
| Falha | nenhuma; F1 (`podman stop`/`start` de `isn2`, 60 s); F3 (`netem delay 20ms 13ms distribution normal` em `isn2`) | `scripts/run-baseline.sh:75-81`, `:151-170`; `Desenvolvimento.tex:40` |
| Repetições | **10 por célula** (recomendado; ver pergunta R2) | seção 6 |

12 células × 10 = **120 execuções** de campanha, mais o piloto (seção 7).

Parâmetros fixos de cada execução (proposta):

| Parâmetro | Valor | Observação |
|---|---|---|
| `--duration` | 300 s | ≈ 10⁶ eventos em S1 por execução (T9, `Metodologia.tex:176`), estimado do Q6 local (1,89·10⁶ em 600 s num ambiente 2× mais lento) |
| `--warmup-min-sec` | 30 s | eventos do aquecimento descartados (`WarmupPolicy`) |
| `--threads` | 4 | é a configuração em que a v3 foi validada em todos os *smokes*; a margem de DT-007 é igual ao número de *threads* |
| `--mix v3 --reset-state on --ops 0 --rep 1` | | reset entre execuções (B-43.1) |
| F1 | início em +35 s (aquecimento + 5 s), 60 s parado | mesma regra do `run-baseline.sh:151`; 60 s > FD_ALL3 de 40 s (T16/T17) |
| F3 | início em +35 s, até o fim da janela (265 s) | `run-baseline.sh:161-163` |
| Jar | o da ponta integrada depois do B-43.9 (e, se aprovados, da correção F-1 e do B-43.5), SHA-256 no pré-registro | |
| *Cluster* | 3 nós `quay.io/infinispan/server:15.0.21.Final`, XML do B-42.1, criado do zero depois do S7 | `plano-implementacao...:481` |

**Ordem.** Blocos completos aleatorizados: a campanha tem 10 rodadas, e cada rodada executa as 12
células uma vez, numa ordem sorteada com semente registrada no pré-registro. Assim, deriva da VM ao
longo da noite (temperatura, *page cache*, fragmentação do *heap* do Infinispan) cai igualmente sobre
todas as células, e uma interrupção no meio deixa rodadas completas utilizáveis.

## 3. Sementes (R-S1..R-S4, `validacao-b43-7b-2026-10-22.md:114-128`)

- **R-S1/R-S2.** Execução de índice global i (0..N−1, na ordem sorteada do pré-registro) roda com
  `--seed = S0 + 100·i` e `--rep 1`. Proposta: **S0 = 50 000** (não colide com nenhum *smoke*, que
  usaram 42 e 43 900..44 400). O piloto usa o mesmo S0 com i a partir de 1 000, para nunca
  coincidir com a campanha.
- **R-S3.** A análise lê `seed=` e `rep=` da linha `Configuracao:` de cada stdout e recusa a matriz
  se dois pares coincidirem (como `runs/b43-9-smoke/analise.py`).
- **R-S4.** Uma reprovação isolada de C3 na matriz (N = 120 ≥ 20) é investigada e a execução é
  repetida com a próxima semente da sequência (i = 120, 121, ...), desde que o C3 agregado da matriz
  (soma de k e de n) passe no mesmo IC. Duas reprovações de C3, ou o agregado reprovado, **param** o
  *rebaseline*. Com N = 120, o número esperado de reprovações por acaso é 0,12.
- **Escopo da reprodutibilidade.** Sem o B-43.5, só a sequência de *tokens* antigos é reprodutível por
  semente; a ordem das operações de cada *thread* não é (`validacao-b43-7b-2026-10-22.md:151-167`). As
  repetições são independentes do mesmo jeito; o que muda é a frase da Metodologia.

## 4. Onde e quando roda

- **VM Hyper-V** (IP de 22/10: `172.18.251.140`; o IP já mudou uma vez, conferir pelo MAC
  `00-15-5D-03-AC-00` antes de cada sessão, `runbook-vm-hyperv.md:101-104`), VM exclusiva com
  `flock /var/lock/tcc-vm.lock`, código levado por *bundle* conferido por *hash*
  (`decisoes-senior-2026-10-16.md:363-368`).
- **Depois de S1..S7** (`decisoes-senior-2026-10-16.md:399`, sequência em `:372-397`). O S4 (REP=4B) é
  sensível ao histórico do *cluster*; qualquer carga S1/S2 antes dele contaminaria esse histórico. O
  Gestor precisa estar presente em S4..S7.
- **Recriação do *cluster*** com o XML do B-42.1 depois do S7 (`plano-implementacao...:481`, regra 3 em
  `:498`). O mesmo *cluster* serve os dois modos (os quatro *caches* coexistem).
- **T-D não é pré-requisito funcional** (o *rebaseline* só usa F1 e F3), mas edita o mesmo
  `run-baseline.sh` (`plano-implementacao...:463`). Ver pergunta R5.
- **Horário.** Campanhas à noite, desenvolvimento de dia (decisão do Gestor de 22/10). A regra de
  silêncio no *host* durante injeções (`decisoes-senior-2026-10-16.md:365`) vale a noite inteira,
  porque F1/F3 estão espalhados por todas as rodadas.
- **`go` do Gestor.** A regra de 16/10 exige o `go` com o Gestor presente antes de qualquer injeção que
  toque `tc` num nó (`decisoes-senior-2026-10-16.md:364`), e F3 toca. Uma campanha noturna sem o
  Gestor é incompatível com a letra da regra. Ver pergunta R6.

## 5. Critérios de sanidade e de recusa

Por execução. Uma execução recusada **fica nos brutos, rotulada**, e não entra na análise.

| # | Critério | Sem falha | F1 / F3 |
|---|---|---|---|
| K1 | portão antes da execução: 3 nós HEALTHY com `number_of_nodes=3` há ≥ 60 s, sem `HEALTHY_REBALANCING`; reset com `depois=0,0`; `tc qdisc` sem `netem` nos 3 netns | recusa | recusa |
| K2 | exit 0 do *workload* (o `run-baseline.sh:185` hoje engole o exit com `|| true`; o *runner* novo não pode) | recusa | recusa |
| K3 | verificador de sanidade (B-43.3a-i): exit 0; exit 2 (operação fora da mistura), 4 ou 5 | recusa | recusa |
| K4 | C1 (terços ≤ 1,0 p.p.), C2 ([4,5; 6,5] %), C3 (IC de DT-008, recusa se n·p·(1−p) < 100), C3/C5 com a margem de DT-007, C8 carga 200 000 | recusa | recusa |
| K5 | C4 (NOT_FOUND = 0; Login OK/Login ≥ 0,99) e C5 (`reposicao_erro = 0`, `logout_noop` isolado = 0) | recusa | **reportado, não critério** (DT-006, `decisoes-tecnicas.md:91-93`) |
| K6 | a falha aconteceu: F1, *log* do injetor com `stop` e `start` e o *cluster* de volta a 3 nós antes da próxima; F3, `netem` presente durante e ausente depois (`verificar_sem_netem`) | n/a | recusa |
| K7 | R-S3 (par semente/repetição único) | recusa da matriz | idem |
| K8 | `ISPN006010` no máximo 1 por nó por vida do *cluster* | parada | parada |
| K9 | SYNC sem falha: I1 = I4 = 0 e I2 = 0 (S1/S2 não têm `Block`), `Auditor I2:` toda zero, `sem_marco = 0` | **parada** para investigar (seria defeito do auditor, depois do B-43.6..9) | reportado |
| K10 | ASYNC: I1/I2/I4 | reportado (H-A0) | reportado |

Para C1/C2 com falha, o denominador é o número de Validate isolados com `return_code` VALID ou INVALID;
os erros são reportados à parte (sem isso, um F1 com erros reprovaria C2 por construção). Isso é uma
definição nova e precisa de aprovação (pergunta R7).

**Regras de parada da campanha.** (a) Duas recusas na mesma célula; (b) mais de 6 recusas no total
(5 %); (c) qualquer K8 ou K9; (d) duas reprovações de C3 (R-S4). Uma recusa isolada é reposta no fim
da campanha com a próxima semente da sequência, no máximo uma reposição por célula.

**Contagem de violações.** Nenhuma tabela usa `Violacoes detectadas:` do stdout, que desde o B-43.9
inclui `I2_JANELA_CASCATA` e `I2_CASCATA_INCOMPLETA` (`validacao-b43-9-2026-10-23.md`, ponto 2). As
contagens saem de `violacoes-rep-NNN.csv` por invariante, com I2 contada só quando `classificacao =
REAL`.

## 6. Unidade de análise e estatística

- **Unidade = execução.** Para cada execução e operação, p50, p95 e p99 das latências
  (`end_ns − start_ns`) dos eventos da janela de medição com desfecho de negócio válido para a
  operação (Login OK; Validate VALID ou INVALID; Logout; IncrementFailure COUNTED; ResetFailures).
  Erros ficam fora dos percentis e entram na taxa de erro.
- **Mínimo de amostras.** p99 só é calculado com n ≥ 1 000 eventos da operação na execução (≥ 10 na
  cauda). Abaixo disso a execução fica sem p99 naquela operação, e isso é reportado. O caso conhecido é
  IncrementFailure em S2 sob F3 (2 109 amostras em três janelas de 65 s na bateria antiga,
  `Desenvolvimento.tex:95`).
- **Resumo da célula:** média e mediana dos p99 das 10 execuções, mínimo e máximo.
- **Contrastes:** (i) F1 − nenhuma e F3 − nenhuma, por cenário, modo e operação (as linhas das novas
  Tabelas 5 e 6); (ii) ASYNC − SYNC, por cenário, falha e operação. Estimativa: diferença das médias
  dos p99 por execução. IC 95 % por *bootstrap* percentil com 10 000 reamostragens, reamostrando
  **execuções** com reposição dentro de cada célula, semente do *bootstrap* fixada no pré-registro.
- **Teste secundário:** Mann-Whitney bilateral sobre os p99 por execução (10 contra 10), α = 0,05,
  para continuidade com T22 (`Metodologia.tex:189`). O Mann-Whitney sobre eventos agrupados de várias
  execuções, usado em `analysis/compare_scenarios.py`, **não** é usado: trata eventos dependentes como
  independentes.
- **Multiplicidade:** são cerca de 9 operações × 2 contrastes × 2 modos nas tabelas principais. Os IC
  são reportados sem correção, com a contagem de comparações declarada; nenhuma conclusão depende de
  uma comparação isolada no limite.
- **O que muda no texto acadêmico** (para os agentes acadêmicos, via `docs/resumos-para-tcc/` depois da
  aprovação): T10 passa de 30 para 10 repetições (desvio declarado, pergunta R2); T22 passa a dizer
  "por execução"; a frase de reprodutibilidade segue a seção 3.

## 7. Piloto, campanha e tempo de máquina

Tempos medidos no *smoke* B-43.9 (`runs/b43-9-smoke/tempos.txt`): o *overhead* fixo de uma execução
(JVM, reset, carga de 200 000 entradas em ≈ 1,7 s) foi de 13 a 23 s.

| Etapa | Conteúdo | Por execução | Total |
|---|---|---|---|
| Piloto | 1 rodada (12 células × 1), sementes i = 1 000..1 011 | 300 s + 20 s + portão 60 s, mais até 90 s de espera em F1 | ≈ 1,5 h |
| Validação do piloto | o Senior lê os brutos (K1..K10) e aprova | | de dia |
| Campanha | 10 rodadas × 12 = 120 | ≈ 6,5 a 8 min | **≈ 13 a 16 h** |
| Reposições | até 6 | | ≤ 1 h |

A VM pode rodar 24 h, mas 13 a 16 h invadem o dia. Recomendo **duas noites de 5 rodadas** (≈ 7 a 8 h
cada): com blocos completos, cada noite tem as 12 células cinco vezes, e a quebra não cria
desequilíbrio. O piloto pode ir na primeira noite, antes da rodada 1, se o Gestor aceitar validar o
piloto pelos brutos e liberar a campanha na noite seguinte (aí são três noites no total: piloto,
rodadas 1..5, rodadas 6..10).

Alternativas de tamanho (pergunta R2):

| Opção | Repetições | Execuções | Máquina |
|---|---|---|---|
| A (recomendada) | 10 × 300 s | 120 | ≈ 13 a 16 h, duas noites |
| B | 5 × 300 s | 60 | ≈ 7 a 8 h, uma noite; *bootstrap* com 5 execuções é frágil |
| C | 30 × 300 s (T10 literal) | 360 | ≈ 40 a 48 h, seis noites |
| D | 30 × 120 s | 360 | ≈ 22 a 26 h; T9 não é atendido (≈ 4·10⁵ eventos) e a janela pós-F1 fica curta |

## 8. Pré-requisitos

| Item | Exigido antes? | Recomendação |
|---|---|---|
| Integração do B-43.9 | **sim** | o Login medido tem de ter a dupla checagem (`prompt-b43-9-atualizado-2026-10-22.md:32-35`) |
| Correção F-1 da V-B43.9 | não para S1/S2 | fazer antes, para o *rebaseline* e a T-F usarem o mesmo jar |
| **B-43.5** (mistura semeada) | não | **fazer antes, se couber até o fim do S7.** Não muda a validade estatística (as repetições já são independentes), mas a fazer depois o jar do *rebaseline* ficaria diferente do da T-F e do B-41 (que precisa de "mesma semente de carga" nos blocos pareados, `plano-implementacao...:528`), e a Metodologia teria de descrever duas versões. É pequeno e só no *host*. Se atrasar o S7, rodar sem ele e declarar |
| **B-43.3a parte ii** (guarda *online* e recusa no `percentis.py`) | não | **não fazer antes.** A recusa é por execução e *offline* (seção 5), aplicada pelo *script* de análise novo. A guarda *online* abortaria a execução no meio, o que só economiza máquina |
| *Runner* do *rebaseline* | **sim** | o `run-baseline.sh` atual não passa `--mix`, `--replication` nem `--reset-state`, usa `SEED_BASE + rep` (`:183`, contra R-S2), engole o exit (`:185`) e não aleatoriza a ordem. Ver pergunta R5 |
| *Script* de análise `analysis/rebaseline.py` | **sim** | K1..K10, R-S3, p99 por execução, *bootstrap* entre execuções, com testes e *fixtures*; derivado de `runs/b43-9-smoke/analise.py` |
| S1..S7 aprovados e *cluster* recriado | **sim** | seção 4 |
| Pré-registro (DT-009) commitado com o SHA-256 do jar, a ordem sorteada, S0 e a semente do *bootstrap* | **sim** | antes do piloto |

## 9. O que fica fora

- S3 e as células F2 e F4 (T-D e T-F).
- O controlador (`--controller off` em todas; B-41 é outra campanha).
- Energia e CPU por contêiner (proxy do plano do controlador): não coletados aqui, para não acrescentar
  carga de observação que mude a latência. Pode entrar como coleta passiva se o Gestor pedir (R9).
- Percentis por fase (antes/durante/recuperação da falha): exigem os marcadores do T-D. As tabelas
  usam a janela inteira, como as Tabelas 5 e 6 antigas.
- p99,9 como desfecho (com ≈ 10⁶ eventos por execução há ≈ 10³ na cauda em S1, mas não em todas as
  operações). Reportado onde n ≥ 10 000, sem contraste.
- A bateria "plena" de 10⁶ operações e 30 repetições de T9/T10, salvo se o Gestor escolher a opção C.
- Os números antigos das Tabelas 5 e 6: não são comparados com os novos, porque mediram outra coisa.

## 10. Perguntas ao Gestor

1. **R1, escopo.** S1/S2, v3, SYNC e ASYNC, sem S3 (recomendado). Alternativa: só ASYNC, como no
   plano de 22/10, mantendo SYNC da bateria antiga, o que não é possível porque a antiga mediu BLOCKED.
2. **R2, tamanho.** Opção A, 10 repetições × 300 s, duas noites (recomendada); B, C ou D da seção 7.
3. **R3, parâmetros.** 4 *threads*, aquecimento 30 s, F1 60 s em `isn2` a +35 s, F3 20 ms ± 13 ms
   normal em `isn2` até o fim (recomendado). Alternativa: 8 *threads* como o `run-baseline.sh`, o que
   exige um *smoke* v3 com 8 *threads* antes.
4. **R4, B-43.5.** Fazer antes do *rebaseline*, se couber até o fim do S7 (recomendado); ou depois,
   declarando a diferença de jar.
5. **R5, *runner*.** (a, recomendado) *script* novo `scripts/run-rebaseline.sh`, que reaproveita
   `inject-crash.sh` e `inject-jitter.sh` e não toca o `run-baseline.sh` (sem conflito com o T-D e sem
   mudar o que gerou os brutos antigos), com teste de `--dry-run` e entrega própria de Worker no
   *host*; (b) estender o `run-baseline.sh` no B-42.3, depois do T-D.
6. **R6, `go` para a campanha noturna.** (a, recomendado) o Gestor dá o `go` presente no início de
   cada noite e acompanha a primeira execução F3 (aplicação e remoção do `netem`), e a noite segue sem
   ele, com `flock`, `trap` de limpeza do injetor e K6/K8 como paradas automáticas; (b) só com o Gestor
   presente o tempo todo, o que leva a campanha para o dia.
7. **R7, denominador de C1/C2 com falha.** Validate isolados com VALID ou INVALID, erros à parte
   (recomendado).
8. **R8, sementes.** S0 = 50 000, piloto em i ≥ 1 000, R-S4 como na seção 3 (recomendado).
9. **R9, CPU.** Não coletar no *rebaseline* (recomendado) ou coletar `podman stats` passivo a 1 Hz.
10. **R10, piloto.** Validar o piloto pelos brutos de dia e liberar a campanha na noite seguinte
    (recomendado), ou encadear piloto e campanha na mesma noite com parada automática se K1..K10
    falharem.
