# Pré-registro do *rebaseline* S1/S2 em DIST_SYNC e DIST_ASYNC: versão APROVADA (2026-10-23)

> Senior, 2026-10-23. **Aprovado pelo Gestor em 2026-10-23**, a partir do rascunho
> `docs/pre-registro-rebaseline-2026-10-23.md` (commit `d7df5dd`), que fica sem edição como histórico.
> As respostas do Gestor às perguntas R1..R10 do rascunho estão na seção 0. O registro formal é a
> entrada **DT-009 (PRÉ-REGISTRO)** de `docs/decisoes-tecnicas.md`; a regra de `sem_marco` é a
> **DT-010**. Pela regra do cabeçalho de `decisoes-tecnicas.md`, nada daqui pode ser ajustado depois de
> ver dados do piloto ou da campanha; uma mudança vira entrada nova que cita a DT-009. Sem push.
>
> Base de código citada: `feature/controlador-integracao` em `5e1084f` e a ponta do B-43.9 em
> `96c8045` (`fix/b43-9-i2-instantaneo`), lidas sem modificar.

## 0. Decisões do Gestor (23/10) aplicadas

| # | Pergunta do rascunho | Decisão |
|---|---|---|
| R1 | Escopo | S1 e S2 com mistura v3, em DIST_SYNC e DIST_ASYNC; S3 fora |
| R2 | Tamanho | Opção A: 10 repetições por célula, 300 s cada; 120 execuções em duas noites, mais o piloto |
| R3 | Parâmetros | 4 *threads*; F1 de 60 s e F3 de 20 ms ± 13 ms (normal), os dois em `isn2` a +35 s; aquecimento mínimo de 30 s |
| R4 | B-43.5 | Feito e integrado **antes** do *rebaseline* |
| R5 | *Runner* | Novo `scripts/run-rebaseline.sh` e novo `analysis/rebaseline.py`; `scripts/run-baseline.sh` não é tocado |
| R6 | `go` noturno | O Gestor dá o `go` presente no início de cada noite e acompanha a primeira execução F3; o resto da noite roda com paradas automáticas |
| R7 | C1/C2 com falha | Denominador = Validate isolados com `return_code` VALID ou INVALID; erros reportados à parte |
| R8 | Sementes | S0 = 50 000; piloto com i ≥ 1 000; R-S4 como na seção 4 |
| R9 | CPU | Sem coleta de CPU no *rebaseline* |
| R10 | Piloto | Piloto numa noite, validado de dia pelo Senior; campanha liberada a partir da noite seguinte |

Decisões do mesmo dia sobre a V-B43.9 que afetam este pré-registro: correção de F-1 antes da
integração do B-43.9 (o jar do *rebaseline* já a contém); `sem_marco = 0` como critério só em SYNC
(DT-010).

## 1. Objeto

1. **Substitui as Tabelas 5 e 6 da monografia** (`tcc-escrita/Desenvolvimento.tex`,
   `tab-baseline-f1-p99` e `tab-baseline-f3-p99`). A bateria de 23/06 que as gerou teve todo Login
   BLOCKED e todo Validate/Logout sobre chave inexistente (`diagnostico-login-blocked-2026-10-22.md`):
   mediu recusa e leitura vazia, não sessões. O texto também afirma `DIST_ASYNC`, mas os *caches*
   usados eram SYNC (`plano-implementacao-controlador-2026-10-22.md:95-106`). Os números antigos não
   são comparados com os novos.
2. **Mede os dois modos na mesma campanha e com o mesmo jar**: DIST_SYNC (controle) e DIST_ASYNC
   (alvo), como pede `Metodologia.tex:168` (T1).
3. **Mistura v3** (DT-005: toda execução de medição passa `--mix v3`). A v2 não é estacionária
   (`validacao-b43-2-2026-10-22.md:28`). A v3 tem tolerâncias pré-registradas (DT-004, DT-007, DT-008).
4. **S3 fica fora**: é a simulação de ciclo de vida da T-F, de carga baixa e foco semântico.
5. **Desfecho principal**: p99 de latência por operação, **por execução**. Secundários: p50, p95, taxa
   de erro por código, vazão e contagens do auditor (reportadas; critério só onde a seção 6 diz).

## 2. Matriz e parâmetros fixos

| Fator | Níveis |
|---|---|
| Cenário | S1 (50/50), S2 (95/5); `--mix v3` |
| Modo | `--replication sync` (`sessions`, `counters`) e `--replication async` (`sessions-async`, `counters-async`) |
| Falha | `none`; F1 (`podman stop`/`start` de `isn2`, 60 s parado); F3 (`netem delay 20ms 13ms distribution normal` no `eth0` de `isn2`) |
| Repetições | 10 por célula |

12 células × 10 = **120 execuções** de campanha. Rótulo canônico de célula: `<cenario>-<modo>-<falha>`
(por exemplo `S1-sync-F1`). Ordem canônica da lista de células, usada pelo sorteio da seção 3:
cenário (S1, S2) × modo (sync, async) × falha (none, F1, F3), nessa ordem de aninhamento.

Invocação do *workload* em toda execução (piloto, campanha e reposições):

```
java -jar <jar> --scenario <S1|S2> --mix v3 --replication <sync|async> --reset-state on \
     --duration 300 --warmup-min-sec 30 --threads 4 --ops 0 --rep 1 --seed <S0 + 100*i> \
     --servers 127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224 --username admin --password <...> \
     --csv-dir <dir>
```

Sem `--marker`, `--convergence`, `--sweep` nem `--stop-at-fault-end`. Sem controlador: o jar da
integração em `96c8045` não tem opção `--controller`; se a ponta usada tiver, o *runner* passa
`--controller off` e registra isso no complemento da seção 9.

**Linha do tempo de uma execução** (de `WarmupPolicy.padrao` com 300 s e mínimo de 30 s):
aquecimento de +0 a +30 s (`max(30, ⌈0,10·300⌉)`), descarte de +30 a +44 s (`⌈0,05·270⌉ = 14 s`),
**janela de medição de +44 a +300 s (256 s)**. Só a janela de medição vai para o CSV e para os
contadores com janela.

- **F1**: `inject-crash.sh --node isn2 --duration 60` disparado a **+35 s**; o nó fica parado de
  ≈ +35 a ≈ +95 s. Os primeiros ≈ 9 s da parada caem no descarte; a janela de medição contém ≈ 51 s
  com o nó parado, o retorno e o rebalanceamento. 60 s > FD_ALL3 de 40 s (T16/T17).
- **F3**: `inject-jitter.sh --node isn2 --iface eth0 --delay 20ms --jitter 13ms --distribution normal
  --duration 265` disparado a **+35 s**; a disciplina vale até +300 s, cobrindo a janela de medição
  inteira.
- O instante +35 s é o decidido pelo Gestor (R3). Ele foi proposto no rascunho como "aquecimento + 5
  s"; o descarte de 14 s, que o rascunho não considerou, faz a janela de medição começar a +44 s.
  O efeito está descrito acima e é declarado no relatório; não muda nenhuma decisão.

Outros fixos: *cluster* de 3 nós `quay.io/infinispan/server:15.0.21.Final` com o XML do B-42.1
(`cluster/infinispan-cluster.xml`, quatro *caches*, os dois modos no mesmo *cluster*), criado do zero
depois do S7; jar único, construído com a árvore limpa da ponta integrada que contém B-43.9 (com F-1)
e B-43.5, SHA-256 registrado antes do piloto (seção 9).

## 3. Ordem de execução

- **Blocos completos aleatorizados.** 10 rodadas; cada rodada executa as 12 células uma vez, numa
  ordem sorteada. Deriva da VM ao longo da noite cai igualmente sobre todas as células, e uma parada
  deixa rodadas completas utilizáveis.
- **Sorteio.** `ORDEM_SEED = 231023`. Para a rodada r (r = 1..10 na campanha, r = 0 no piloto), a
  ordem é `random.Random(ORDEM_SEED * 1000 + r).shuffle(lista)` (Python 3, `random` da biblioteca
  padrão) aplicado à lista canônica da seção 2. O gerador é `scripts/rebaseline_matriz.py`, que
  escreve a matriz completa (`i`, rodada, posição, célula, semente) em TSV.
- **Índice global i.** Campanha: i = 12·(r − 1) + posição, posição = 0..11, logo i = 0..119.
  Piloto: i = 1 000 + posição (1 000..1 011). Reposições: i = 120, 121, ..., na ordem em que forem
  necessárias.
- **Noites.** Noite do piloto (rodada 0). Campanha: noite 1 = rodadas 1..5 (i = 0..59); noite 2 =
  rodadas 6..10 (i = 60..119), seguida das reposições, se houver e se couberem; senão, uma terceira
  noite curta só de reposições.
- A matriz gerada (`runs/rebaseline/matriz.tsv`) é commitada, com o seu SHA-256, antes do piloto
  (seção 9). O *runner* recusa rodar uma matriz cujo SHA-256 não seja o registrado.

## 4. Sementes (R-S1..R-S4)

- **R-S1/R-S2.** A execução de índice i roda com `--seed = S0 + 100·i`, `--rep 1`, **S0 = 50 000**.
  Com o B-43.5, `sementeThread(seed, tid, 1)` alimenta o `KeyGenerator`, o `Random` do *token*
  antigo (DT-008) e o `Random` da mistura, cada um com a sua derivação (o da mistura com sal próprio).
  Nenhum par (semente, *thread*, repetição) se repete na campanha, no piloto ou nas reposições
  (R-S2 de `validacao-b43-7b-2026-10-22.md`).
- **R-S3.** A análise lê `seed=` e `rep=` da linha `Configuracao:` de cada stdout e recusa a matriz se
  dois pares coincidirem, ou se a semente lida não for `S0 + 100·i` do i registrado na matriz.
- **R-S4.** Uma reprovação isolada de C3 (IC de DT-008) recusa a execução, que é investigada e
  reposta com o próximo i livre, desde que o C3 agregado das execuções aceitas da campanha (soma de k
  e de n) passe no mesmo IC. Duas reprovações de C3, ou o agregado reprovado, **param** o
  *rebaseline*. Esperado por acaso: 0,12 reprovação em 120.
- **Escopo da reprodutibilidade** (com o B-43.5). Mesma semente ⇒ mesma sequência, em cada *thread*,
  de tipos de operação, chaves Zipf (*slots* da v3), decisões de *token* antigo e identidades de
  Login. Não são reprodutíveis: a intercalação entre *threads*, as latências, as respostas do *grid*
  (e o que delas depende: geração do *slot* lida, tríade só após Login OK, reposições), o número de
  operações por *thread* e a fronteira da janela de medição, que são definidos por tempo.

## 5. Onde e quando roda

- **VM Hyper-V** `tcc-f3`. IP de 22/10: `172.18.251.140`; o IP já mudou uma vez, então conferir antes
  de cada noite pelo MAC `00-15-5D-03-AC-00` (`Get-NetNeighbor -InterfaceAlias 'vEthernet (Default
  Switch)'` no *host*). O *runner* roda **dentro** da VM, em `tmux`, com o *cluster* *rootful*
  (`PODMAN="sudo podman"`), sob `flock /var/lock/tcc-vm.lock`. Código levado por *bundle* conferido
  por *hash* (`decisoes-senior-2026-10-16.md`, seção 5.1).
- **Depois de S1..S7** aprovados (sequência em `decisoes-senior-2026-10-16.md`, seção 5.2), com o
  *cluster* recriado com o XML do B-42.1 depois do S7. Nenhuma carga S1/S2 antes do S4.
- **Horário.** Piloto e campanha à noite; desenvolvimento de dia. Silêncio no *host* (sem TLC,
  `mvn test` nem carga) durante a noite inteira, porque F1 e F3 estão espalhados por todas as rodadas.
- **`go` do Gestor (R6).** No início de cada noite (piloto, noite 1, noite 2 e eventual noite de
  reposições), o Gestor, presente, digita o `go` no terminal da sessão `tmux`. Antes da **primeira
  execução F3 da noite**, o *runner* pede um segundo `go` e o Gestor acompanha a aplicação e a remoção
  do `netem` dessa execução. Daí em diante a noite segue sem ele, com as paradas automáticas da
  seção 6. Isso atende a regra de 16/10 (`go` com o Gestor presente antes de injeção que toque `tc`)
  na forma aprovada pelo Gestor para campanhas noturnas: um `go` por noite cobre as execuções F3
  daquela noite e daquela matriz, e só delas.

## 6. Critérios de sanidade e de recusa (por execução)

Uma execução recusada fica nos brutos, rotulada com o motivo, e não entra na análise.

| # | Critério | `none` | F1 / F3 |
|---|---|---|---|
| K1 | Portão antes da execução: 3 nós HEALTHY com `number_of_nodes=3` há ≥ 60 s, sem `HEALTHY_REBALANCING`; `tc qdisc` sem `netem` nos 3 netns; depois da execução, linha `Reset:` com os dois *caches* em 0 antes da carga | recusa | recusa |
| K2 | Exit 0 do *workload* | recusa | recusa |
| K3 | `VerificadorSanidade --csv rep-NNN.csv --scenario S1|S2` com exit 0 e veredito OK | recusa | recusa |
| K4 | C1 (terços ≤ 1,0 p.p.), C2 (fração INVALID em [4,5; 6,5] %), C3 (IC de DT-008, com a recusa por n·p·(1−p) < 100 e a fronteira stdout × CSV ≤ 4 de DT-007), C8 (carga com 200 000 entradas e veredito OK) | recusa | recusa |
| K5 | C4 (Validate NOT_FOUND = 0; Login OK/Login ≥ 0,99; eventos − operações = 2 × Logins OK) e C5 (`reposicao_erro = 0`, `logout_noop` isolado = 0, `logout_isolados = reposicoes`, fronteira ≤ 4, Logout isolado NOT_FOUND = 0); `invalidacao_perdida = conflito_esgotado = 0` | recusa | **reportado** (DT-006) |
| K6 | A falha aconteceu como previsto. F1: *log* do injetor com `stop` e `start`, exit 0, e *cluster* de volta a 3 nós HEALTHY antes da execução seguinte. F3: injetor com exit 0, `netem` presente durante a janela (amostrado pelo *runner*) e ausente depois | n/a | recusa |
| K7 | R-S3 | recusa da matriz | idem |
| K8 | `ISPN006010` no máximo 1 por nó por vida do *cluster* | **parada** | **parada** |
| K9 | Modo SYNC, `none`: I1 = I4 = 0 e nenhum registro I2 de qualquer classe no `violacoes-rep-NNN.csv`; `Auditor I2:` toda em zero, inclusive `sem_marco = 0` (DT-010); `Login: revertido_por_bloqueio=0` | **parada** para investigar | reportado |
| K10 | Modo ASYNC: I1, I2 (por classe), I4 e `sem_marco` | reportado (H-A0) | reportado |

- **C1/C2 com falha (R7).** O denominador é o número de Validate isolados (chave `sid-`) com
  `return_code` VALID ou INVALID na janela de medição; os demais desfechos de Validate isolado
  (`ERROR_TRANSPORT`, `ERROR_UNAVAILABLE`, NOT_FOUND) são contados e reportados à parte. Sem falha,
  essa definição coincide com a de DT-004 (não há erros), logo nada muda em `none`.
- **C1 e C2 continuam critério de recusa nas células com falha**: são propriedades da mistura, não do
  *grid*.
- **Paradas automáticas da campanha.** (a) Duas recusas na mesma célula; (b) mais de 6 recusas no
  total (5 %); (c) qualquer K8 ou K9; (d) duas reprovações de C3, ou o C3 agregado reprovado (R-S4);
  (e) *cluster* que não volta a 3 nós HEALTHY em 180 s depois de um F1, ou portão K1 que não passa em
  10 min; (f) `netem` residual que o *runner* não consegue remover; (g) erro de infraestrutura do
  próprio *runner* (exit diferente de 0 de um passo obrigatório). Uma recusa isolada é reposta no fim
  da campanha com o próximo i livre, no máximo uma reposição por célula.
- **Contagem de violações.** Nenhuma tabela usa `Violacoes detectadas:` do stdout (que desde o B-43.9
  inclui `I2_JANELA_CASCATA` e `I2_CASCATA_INCOMPLETA`). As contagens saem de
  `violacoes-rep-NNN.csv`, por invariante, com I2 contada como violação só quando `classificacao =
  REAL` e o rótulo é exatamente `I2`. Os rótulos de janela e de cascata incompleta e os
  `*_concorrentes` são reportados à parte (DT-001, DT-002).

## 7. Análise

- **Unidade = execução.** Para cada execução e operação, p50, p95 e p99 das latências
  (`end_ns − start_ns`, ns) dos eventos do CSV (só a janela de medição) com desfecho de negócio válido:

  | Operação (CSV) | Desfechos que entram nos percentis |
  |---|---|
  | Login (O1) | `OK` |
  | Validate (O2), isolado e da tríade juntos | `VALID`, `INVALID` |
  | Logout (O3), isolado e da tríade juntos | `NONE` |
  | IncrementFailure (O4) | `COUNTED` |
  | ResetFailures (O5), só S1 | `NONE` |

  Os outros desfechos ficam fora dos percentis e entram nas taxas por código. Percentil por posto
  mais próximo (`ordenado[⌈p·n⌉ − 1]`, igual ao `pct` de `runs/b43-9-smoke/analise.py`). As linhas
  isolado × tríade de Validate e Logout são reportadas como secundárias.
- **Mínimo de amostras.** p99 só com n ≥ 1 000 eventos válidos da operação na execução. Abaixo
  disso, a execução fica sem p99 naquela operação, e isso é reportado. p99,9 só onde n ≥ 10 000, sem
  contraste.
- **Resumo da célula.** Média e mediana dos p99 das execuções aceitas, mínimo, máximo e o número de
  execuções que entraram.
- **Contrastes.** (i) F1 − `none` e F3 − `none`, por cenário, modo e operação (as linhas das novas
  Tabelas 5 e 6); (ii) ASYNC − SYNC, por cenário, falha e operação. Estimativa: diferença das médias
  dos p99 por execução. IC 95 % por *bootstrap* percentil com 10 000 reamostragens, reamostrando
  **execuções** com reposição, independentemente em cada uma das duas células.
  `BOOT_SEED = 231024`; cada contraste usa `numpy.random.default_rng(SeedSequence([BOOT_SEED, k]))`,
  com k a posição do contraste na lista canônica ordenada de rótulos, para que o resultado não dependa
  da ordem de cálculo.
- **Contraste só é estimado** se cada uma das duas células tiver ≥ 8 execuções aceitas com p99 na
  operação; senão sai "não estimado (n insuficiente)".
- **Teste secundário.** Mann-Whitney bilateral sobre os p99 por execução (`scipy.stats.mannwhitneyu`,
  `alternative="two-sided"`, `method="auto"`), α = 0,05. O Mann-Whitney sobre eventos agrupados de
  várias execuções (`analysis/compare_scenarios.py`) **não** é usado.
- **Multiplicidade.** 9 linhas (S1: 5 operações; S2: 4, porque S2 não tem ResetFailures) × 2 modos ×
  2 contrastes de falha, mais os contrastes ASYNC − SYNC. IC reportados sem correção, com a contagem
  de comparações declarada; nenhuma conclusão depende de uma comparação isolada no limite.
- **Vazão.** Eventos válidos por segundo da janela de medição, por execução e operação.

## 8. Piloto e campanha

| Etapa | Conteúdo | Tempo estimado |
|---|---|---|
| Piloto (noite 0) | Rodada 0: 12 execuções, i = 1 000..1 011, mesmos critérios e paradas | ≈ 1,5 h |
| Validação do piloto (dia) | O Senior lê os brutos (K1..K10, n por operação, tempos) e emite parecer; o Gestor libera a campanha | de dia |
| Noite 1 | Rodadas 1..5 (i = 0..59) | ≈ 6,5 a 8 h |
| Noite 2 | Rodadas 6..10 (i = 60..119) e reposições (≤ 6) | ≈ 6,5 a 9 h |

- **Dados do piloto nunca entram em resultado.** Servem para verificar o *runner*, os critérios e os
  tempos.
- **Liberação da campanha.** Piloto com as 12 execuções aceitas, ou com recusa explicada por defeito
  do *runner* já corrigido. Se o piloto mostrar que um critério recusa sistematicamente uma célula
  com falha por razão que não é defeito (por exemplo, uma regra do `VerificadorSanidade` sob F1), a
  mudança vira entrada nova em `decisoes-tecnicas.md`, aprovada pelo Gestor **antes** da noite 1.
  O parecer do piloto reporta também, por célula, o n de Validate isolados por terço (para estimar a
  chance de falsa recusa de C1 sob F3) e as operações que ficariam sem p99 por n < 1 000 (o caso
  esperado é IncrementFailure em S2 sob F3).

## 9. Pré-requisitos (todos antes do piloto)

| Item | Estado em 23/10 |
|---|---|
| B-43.9 com a correção F-1 (Z20, teste de rótulos do `ClasseViolacao`, K22, guarda D4 retirada), validado e integrado em `feature/controlador-integracao` | em correção (`wt-b43-9`) |
| B-43.5 (mistura semeada), validado e integrado | prompt em `docs/prompt-b43-5-seed-mistura-2026-10-23.md` |
| `scripts/run-rebaseline.sh`, `scripts/rebaseline_matriz.py` e `analysis/rebaseline.py`, com testes, validados e integrados | prompt em `docs/prompt-rebaseline-runner-analise-2026-10-23.md` |
| S1..S7 aprovados; *cluster* recriado com o XML do B-42.1 | pendente |
| **Complemento do pré-registro**: entrada nova em `decisoes-tecnicas.md` com o SHA da ponta integrada, o SHA-256 do jar, o SHA-256 do *bundle* da VM, o SHA-256 de `matriz.tsv` e o instante de criação do *cluster* | antes do piloto; são identificadores, não escolhas |

## 10. Fora do escopo

- S3 e as células F2 e F4 (T-D e T-F); o controlador (B-41 é outra campanha).
- CPU e energia por contêiner (R9): nada de `podman stats` ou outra coleta durante a campanha.
- Percentis por fase (antes/durante/recuperação): exigem os marcadores do T-D; as tabelas usam a
  janela de medição inteira, como as antigas.
- A bateria de T9/T10 literal (30 repetições de 10⁶ operações).
- A guarda *online* da parte ii do B-43.3a: a recusa aqui é *offline*, por execução.

## 11. Efeito no texto acadêmico (via `docs/resumos-para-tcc/`, depois dos resultados)

- T10 passa de 30 para 10 repetições: desvio declarado, com a justificativa de tempo de máquina e a
  unidade de análise por execução.
- T22 passa a dizer "*bootstrap* reamostrando execuções" e "Mann-Whitney sobre os p99 por execução".
- A frase de reprodutibilidade segue a seção 4 (escopo explícito do que é e do que não é
  reprodutível).
- O texto de F1/F3 descreve a linha do tempo da seção 2 (injeção a +35 s, medição de +44 a +300 s).
- O modo de replicação de cada tabela é dito explicitamente (SYNC e ASYNC).

## 12. Diferenças em relação ao rascunho

| Ponto | Rascunho | Aprovado |
|---|---|---|
| Perguntas R1..R10 | abertas | decididas (seção 0) |
| Linha do tempo | "+35 s = aquecimento + 5 s, dentro da janela" | janela de medição de +44 a +300 s, por causa do descarte de 14 s; +35 s mantido |
| Ordem | "semente registrada" | `ORDEM_SEED = 231023`, algoritmo e gerador fixados |
| *Bootstrap* | "semente fixada no pré-registro" | `BOOT_SEED = 231024`, derivação por contraste |
| Índices | i da campanha | i da campanha, do piloto e das reposições definidos |
| K3 | "exit 2, 4 ou 5" | exit 0 e veredito OK (o verificador sai com 0, 2 ou 4) |
| K5 | C4, C5 | inclui `invalidacao_perdida = conflito_esgotado = 0` (B-43.8) |
| K9 | `sem_marco = 0` | idem, ancorado na DT-010; inclui `revertido_por_bloqueio = 0` |
| Paradas | (a)..(d) | (a)..(g): *cluster* que não volta, `netem` residual, erro do *runner* |
| Percentil | não definido | posto mais próximo; desfechos válidos por operação em tabela |
| Contraste | sempre | só com ≥ 8 execuções aceitas por célula |
| Citação do MAC | `runbook-vm-hyperv.md:101-104` (não contém o MAC) | `Get-NetNeighbor` no *host*, conforme a memória de estado de 22/10 |
