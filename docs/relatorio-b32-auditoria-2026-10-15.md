# Relatório de auditoria do B-32 (tarefa T-A)

> Worker-1 (frente formal), 2026-10-15. Branch `test/b32-auditoria-controle`, base `48d1571` (`feature/b32-tlc-propriedades-fortes`), worktree `wt-ta`.
> Plano: `docs/plano-fechamento-etapa-formal-2026-10-15.md` (seção 4 e T-A). **Este relatório não declara a tarefa aprovada**: entrega a
> evidência bruta e os comandos; a validação V-A é do Senior.
> `spec/SessionStore.tla` e `spec/MCSession.tla` **não foram alterados** (`git diff 48d1571 -- spec/SessionStore.tla spec/MCSession.tla` vazio;
> blob de `SessionStore.tla` `ab36f9d6e0176291684d7d3d2590bdf6940fe230`, igual ao de `48d1571`). Só `spec/run-tlc-propriedades.sh` foi estendido.

## 1. Resumo

1. **Reconciliação numérica (A.1):** 100 `.out` de `runs/tlc/prop-*` recalculados por script a partir dos brutos. **Nenhuma divergência** entre
   `.out` e `resumo-*.tsv` (resultado, gerados, distintos, tamanho do traço) e **nenhuma divergência** entre o relatório B-32 e os brutos nas
   80 alegações numéricas conferidas (`divergencias-relatorio.tsv`). Os números do B-32 estão corretos; o que muda é a **leitura** de alguns vereditos (seção 3).
2. **Controle sem `SYMMETRY`, 1 worker (A.3):** 30 execuções sem `SYMMETRY` (26 violações com 1 worker e 4 fechamentos exaustivos): **mesmo veredito em 30 de 30**, **mesmo tamanho de traço em 26 de 26** violações, e os 4 fechamentos continuam fechando (mais estados, razões 3,30 a 10,64). Nenhum veredito mudou.
3. **Vacuidade (A.4):** `Mesclar` tem contagem **0:0** de `-coverage` em sem falha (r2 e r3) e queda (r2 com as 4 propriedades; r3 com `I2Fa`, 19.229.901 distintos), em configuração que fecha; e
   **> 0** sob partição. `I2Fa`, `I1M`, `I4M`, `I4Mp` ganham `VACUA` nessas configurações; na cota do plano continuam `vácuo-estrutural`.
4. **`ERRO rc=150` (A.5):** reproduzido (5 de 80 e 4 de 80 execuções simultâneas) e eliminado (0 de 120) com `-Djava.io.tmpdir` próprio por execução.
5. **P-26 e P-27** registrados em `docs/registro-de-problemas.md`; **catálogo** criado em `docs/catalogo-operacao-falha-invariante.md`.
6. **Nenhum controle contradisse o relatório B-32.** Nenhum veredito foi `INVALIDADO`.

## 2. Ambiente e reprodutibilidade (P-11)

| Item | Valor |
|---|---|
| TLC | TLC2 2.19 (rev 5a47802, 08/08/2024), mesmo dos `.out` do B-32 |
| `tla2tools.jar` | fora do repositório (`C:\Users\Francesco\Development\tcc\_tools\tla2tools.jar`); SHA-256 `936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88` (o baixado de `github.com/tlaplus/tlaplus/releases/download/v1.7.4/tla2tools.jar`, indicado no relatório B-32; `spec/tla2tools.jar` não existe) |
| Java | `jdk-17.0.2` explícito via `JAVA=` (o `java` do PATH é 1.8) |
| Máquina | Windows 11, 20 núcleos, 31,8 GB (4 a 5 GB livres durante a auditoria, máquina compartilhada com outros Workers) |
| Shell | Git Bash; `python3` 3.12.10 só para os scripts de auditoria |
| Segredos | nenhum; sem IPs privados |

```bash
export TLA_TOOLS=<...>/tla2tools.jar JAVA="/c/Program Files/Java/jdk-17.0.2/bin/java.exe"
```

## 3. A.1 e A.2: reconciliação e classificação

Comandos:

```bash
scripts/audit-tlc-runs.sh                      # -> runs/tlc/audit-b32/tabela-recalculada.tsv e divergencias-relatorio.tsv
ALEGACOES=/dev/null PADRAO='ctrl-*' scripts/audit-tlc-runs.sh runs/tlc/audit-b32 runs/tlc/audit-b32/controle
```

- `runs/tlc/audit-b32/tabela-recalculada.tsv` cobre **100 de 100** `.out` de `prop-*` (diretório, propriedade, `workers`, cardinalidade, cotas,
  restrição, `SYMMETRY`, resultado **derivado só do `.out`**: `FECHOU`/`VIOLADA`/`TIMEOUT`/`ABORTADA`/`SIMULACAO_SEM_ACHADO`, gerados, distintos, fila,
  duração, estados do traço, linha do `tsv`).
- Divergências `.out` × `tsv`: **0**. Alegações do relatório B-32 (`runs/tlc/audit-b32/alegacoes-relatorio.tsv`, transcritas de 3.1 a 3.5):
  80 conferidas, **0 divergentes** (`divergencias-relatorio.tsv`).
- Contagem de resultados derivados do `.out`: 77 `VIOLADA`, 6 `FECHOU`, 17 `TIMEOUT`, 0 `ABORTADA` (o `.out` do `rc=150` foi sobrescrito, ver abaixo).
- Configuração (P-8): `runs/tlc/audit-b32/verificacao-cfg.txt`: os 100 `.cfg` conferem com o nome do diretório (`c2r3` = `MaxCanal=2`, `MaxRelogio=3`;
  `ev1` = `MaxEventosFalha=1`; `lim6` = `LIMITE=6`; restrição = a da falha), `I2Fb` em `INVARIANTS`, as demais em `PROPERTY`, e **todos** com `SYMMETRY Simetria`.
- Hash (P-8): os `.out` foram gerados no worktree `wt-b32` no commit `48d1571`, e `MCSession.tla`/`SessionStore.tla` estão idênticos a ele. Não há como provar
  qual hash o TLC leu em 2026-10-09; os controles reproduzem os mesmos traços, o que sustenta que a especificação é a mesma.
- `SEM-VIOLACAO-ATE-AQUI` (A.2): os 17 `.out` classificados `TIMEOUT` não têm `No error has been found` nem `0 states left on queue`; o último `Progress` bate com o
  `tsv` (gerados e distintos idênticos). Nenhum timeout é tratado como "preservada" no catálogo.

Observações que **não** são erros:

1. **Traços de 4 workers × 1 worker.** O TLC com vários workers não devolve sempre o traço mínimo. Nos 32 pares com as duas rodadas
   (`runs/tlc/audit-b32/comparacao-4workers-vs-1worker.tsv`), o traço de 4 workers é igual ou maior em 0 a 2 estados, nunca menor (ex.: `prop-queda I4Fp`: 6 contra 5; `prop-sem I2Fb`: 5 contra 3; `prop-particao I2Fa`: 6 contra 4).
   O relatório B-32 cita corretamente os de 1 worker.
2. **`ERRO rc=150`:** o `.out` da execução com erro foi sobrescrito pela repetição; só resta a linha do `tsv` (`prop-queda/resumo-r3.tsv`, `I4M`, `rc=150`, `0s`). Ver seção 6.
3. **Execuções presentes nos brutos e não citadas no relatório B-32:** `prop-queda-c2r3/r3-I1F` (`TIMEOUT`, 13.297.388 distintos em 600 s, 4 workers; é a rodada curta que antecedeu a `ev2-longo`),
   `prop-queda-w1/r3-I4M` (`TIMEOUT`, 300 s, 1 worker), `prop-particao/r3-I1P` (`TIMEOUT`, 120 s). Nenhuma contradiz o relatório.
4. **`I4Mp` sob partição** só existia com 4 workers; foi rodada com 1 worker, com e sem simetria (seção 4): 5 estados nas duas.

## 4. A.3: controle sem `SYMMETRY` (1 worker)

Implementação: `spec/run-tlc-propriedades.sh` ganhou `SEM_SIMETRIA=1` (omite a linha `SYMMETRY Simetria` do `.cfg`), `COVERAGE=<min>` (`-coverage`) e um
`-Djava.io.tmpdir` por execução. Driver reproduzível: `scripts/run-controle-sem-simetria-b32.sh {violacoes|extras|fechamentos|cobertura}`.

Revisão de `MCSession.tla` e `SessionStore.tla` quanto à simetria: nenhuma fórmula distingue réplicas, sids ou identidades. As propriedades quantificam sobre `REPLICAS`/`SIDS`;
`Particionar` explora todos os subconjuntos; `Vencedor`/`Mesclar` dependem só de cardinalidade e escolhem `rf` por `\E`; `LimiteCanal`/`LimiteRelogio` valem para toda réplica. O único `CHOOSE` é
`IdSemente == CHOOSE id \in IDS : TRUE`, inofensivo com `IDS = {u1}`. A rodada empírica abaixo confirma.

Tabela "com simetria × sem simetria" (gerada por `scripts/audit_comparacao_simetria.py`, arquivo `runs/tlc/audit-b32/comparacao-simetria.tsv`):

| Controle (`runs/tlc/audit-b32/<dir>`) | Prop. | Falha | Cotas (canal/relogio/ev/LIMITE) | Referência com simetria | Com sim.: resultado, traço ou distintos | Sem sim.: resultado, traço ou distintos | Razão | Mesmo veredito | Mesmo traço |
|---|---|---|---|---|---|---|---|---|---|
| `ctrl-nosym-lim6-particao` | I2Fb | SoParticao | 3/5/2/6 | `particao-lim6-w1/r3-I2Fb` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-lim6-queda` | I2Fb | SoQueda | 3/5/2/6 | `queda-lim6-w1/r3-I2Fb` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-lim6-sem` | I2Fb | SemFalha | 3/5/2/6 | `sem-lim6-w1/r3-I2Fb` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-livre-particao` | I1F | SoParticao | 3/5/2/2 | `livre-particao-w1/r3-I1F` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-livre-particao` | I1M | SoParticao | 3/5/2/2 | `livre-particao-w1/r3-I1M` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-livre-particao` | I1P | SoParticao | 3/5/2/2 | `livre-particao-w1/r3-I1P` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-livre-particao` | I2Fa | SoParticao | 3/5/2/2 | `livre-particao-w1/r3-I2Fa` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-livre-particao` | MesclarPerdeChave | SoParticao | 3/5/2/2 | `livre-particao-w1/r3-MesclarPerdeChave` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-livre-queda` | I1F | SoQueda | 3/5/2/2 | `livre-queda-w1/r3-I1F` (1 w) | VIOLADA, 7 estados | VIOLADA, 7 estados | - | SIM | SIM |
| `ctrl-nosym-livre-queda` | I1P | SoQueda | 3/5/2/2 | `livre-queda-w1/r3-I1P` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-livre-sem` | I1F | SemFalha | 3/5/2/2 | `livre-sem-w1/r3-I1F` (1 w) | VIOLADA, 7 estados | VIOLADA, 7 estados | - | SIM | SIM |
| `ctrl-nosym-livre-sem` | I1P | SemFalha | 3/5/2/2 | `livre-sem-w1/r3-I1P` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I1F | SoParticao | 3/5/2/2 | `particao-w1/r3-I1F` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I1M | SoParticao | 3/5/2/2 | `particao-w1/r3-I1M` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I2Fa | SoParticao | 3/5/2/2 | `particao-w1/r3-I2Fa` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I2Fb | SoParticao | 3/5/2/2 | `particao-w1/r3-I2Fb` (1 w) | VIOLADA, 3 estados | VIOLADA, 3 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I4F | SoParticao | 3/5/2/2 | `particao-w1/r3-I4F` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I4Fp | SoParticao | 3/5/2/2 | `particao-w1/r3-I4Fp` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I4M | SoParticao | 3/5/2/2 | `particao-w1/r3-I4M` (1 w) | VIOLADA, 4 estados | VIOLADA, 4 estados | - | SIM | SIM |
| `ctrl-nosym-particao` | I4Mp | SoParticao | 3/5/2/2 | `ctrl-sym-particao-w1/r3-I4Mp` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-queda` | I2Fb | SoQueda | 3/5/2/2 | `queda-w1/r3-I2Fb` (1 w) | VIOLADA, 3 estados | VIOLADA, 3 estados | - | SIM | SIM |
| `ctrl-nosym-queda` | I4F | SoQueda | 3/5/2/2 | `queda-w1/r3-I4F` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-queda` | I4Fp | SoQueda | 3/5/2/2 | `queda-w1/r3-I4Fp` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-queda-c2r3` | I1F | SoQueda | 2/3/2/2 | `queda-c2r3/r2-I1F` (4 w) | FECHOU, 15.690 | FECHOU, 51.840 | 3.30 | SIM | - |
| `ctrl-nosym-sem` | I2Fb | SemFalha | 3/5/2/2 | `sem-w1/r3-I2Fb` (1 w) | VIOLADA, 3 estados | VIOLADA, 3 estados | - | SIM | SIM |
| `ctrl-nosym-sem` | I4F | SemFalha | 3/5/2/2 | `sem-w1/r3-I4F` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-sem` | I4Fp | SemFalha | 3/5/2/2 | `sem-w1/r3-I4Fp` (1 w) | VIOLADA, 5 estados | VIOLADA, 5 estados | - | SIM | SIM |
| `ctrl-nosym-sem-c2r3-I1F` | I1F | SemFalha | 2/3/2/2 | `sem-c2r3/r3-I1F` (4 w) | FECHOU, 1.627.731 | FECHOU, 17.315.129 | 10.64 | SIM | - |
| `ctrl-nosym-sem-c2r3-I1P` | I1P | SemFalha | 2/3/2/2 | `sem-c2r3/r3-I1P` (6 w) | FECHOU, 1.627.731 | FECHOU, 17.315.129 | 10.64 | SIM | - |
| `ctrl-nosym-sem-r2` | I1F | SemFalha | 3/5/2/2 | `sem/r2-I1F` (4 w) | FECHOU, 2.968.799 | FECHOU, 11.115.255 | 3.74 | SIM | - |

Conclusão: a redução por simetria não alterou nenhum veredito nem o tamanho de nenhum traço mínimo nas 30 células controladas. Os 4 fechamentos sem simetria (`I1F` queda r2, `I1F` sem falha r2 e r3, `I1P` sem falha r3) estão confirmados sem simetria: 51.840; 11.115.255; 17.315.129; 17.315.129 estados distintos. As células de `I1F` queda r3 (`ev1`, `ev2-longo`) ficam sem controle.

Não rodados sem simetria (razão): `prop-queda-c2r3-ev1` e `prop-queda-c2r3-ev2-longo` de `I1F` (8.037.698 e 19.229.901 estados distintos com simetria; sem simetria seriam até 12× mais, fora do orçamento de memória
da máquina compartilhada); ficam `PRESERVADA-ATE-COTAS (sob simetria; sem controle sem simetria)`.

## 5. A.4: vacuidade por cobertura

Comando: `scripts/run-controle-sem-simetria-b32.sh cobertura` (r2, `MaxCanal=2`, `MaxRelogio=3`, 3 colunas) e, adicionalmente,
`OUTDIR=... MAXCANAL=2 MAXRELOGIO=3 COVERAGE=1 WORKERS=4 spec/run-tlc-propriedades.sh semeado sem r3 I2Fa I1M I4M I4Mp` (r3, sem falha) e `... queda r3 I2Fa` (8 workers).
Extração: `scripts/audit-cobertura.sh > runs/tlc/audit-b32/cobertura-mesclar.tsv`. Formato do TLC: `<Acao ...>: D:T`, com `D` = distintos novos gerados pela ação e `T` = vezes em que gerou sucessor.

| Diretório | Arquivo | Resultado | `Particionar` D:T | `Mesclar` D:T |
|---|---|---|---|---|
| `cov-particao-r2` | `r2-I1M.out` | VIOLADA:traco_de_4_estados | 21:26 | 3:6 |
| `cov-particao-r2` | `r2-I2Fa.out` | VIOLADA:traco_de_4_estados | 41:48 | 11:14 |
| `cov-particao-r2` | `r2-I4M.out` | VIOLADA:traco_de_4_estados | 21:26 | 3:6 |
| `cov-particao-r2` | `r2-I4Mp.out` | VIOLADA:traco_de_5_estados | 104:112 | 33:37 |
| `cov-queda-r2` | `r2-I1M.out` | SEM_VIOLACAO:exaustivo | 0:11928 | 0:0 |
| `cov-queda-r2` | `r2-I2Fa.out` | SEM_VIOLACAO:exaustivo | 0:11928 | 0:0 |
| `cov-queda-r2` | `r2-I4M.out` | SEM_VIOLACAO:exaustivo | 0:11928 | 0:0 |
| `cov-queda-r2` | `r2-I4Mp.out` | SEM_VIOLACAO:exaustivo | 0:11928 | 0:0 |
| `cov-queda-r3c2` | `r3-I2Fa.out` | SEM_VIOLACAO:exaustivo | 0:29299158 | 0:0 |
| `cov-sem-r2` | `r2-I1M.out` | SEM_VIOLACAO:exaustivo | 0:3976 | 0:0 |
| `cov-sem-r2` | `r2-I2Fa.out` | SEM_VIOLACAO:exaustivo | 0:3976 | 0:0 |
| `cov-sem-r2` | `r2-I4M.out` | SEM_VIOLACAO:exaustivo | 0:3976 | 0:0 |
| `cov-sem-r2` | `r2-I4Mp.out` | SEM_VIOLACAO:exaustivo | 0:3976 | 0:0 |
| `cov-sem-r3c2` | `r3-I1M.out` | SEM_VIOLACAO:exaustivo | 0:9766386 | 0:0 |
| `cov-sem-r3c2` | `r3-I2Fa.out` | SEM_VIOLACAO:exaustivo | 0:9766386 | 0:0 |
| `cov-sem-r3c2` | `r3-I4M.out` | SEM_VIOLACAO:exaustivo | 0:9766386 | 0:0 |
| `cov-sem-r3c2` | `r3-I4Mp.out` | SEM_VIOLACAO:exaustivo | 0:9766386 | 0:0 |

Leitura: em sem falha e queda `Particionar` é gerada (T grande) mas todos os sucessores caem fora do modelo (`D=0`, restrição `SemFalha`/`SoQueda`), e `Mesclar` nunca é habilitada (0:0). Sob partição `Mesclar` > 0.
Isso é a prova empírica pedida em P-5, restrita a essas configurações (**não** é a cota do plano).

## 6. A.5: `ERRO rc=150`

- **Diagnóstico.** O TLC extrai `TLC.tla`, `Naturals.tla`, `Sequences.tla`, `FiniteSets.tla` para `java.io.tmpdir` (`%TEMP%`), com nomes fixos. Execuções simultâneas colidem ali: um processo lê o arquivo
  enquanto outro o recria ou o remove, e o TLC falha com `Fatal errors while parsing ... NullPointerException ... Module-Table lookup failure for module name MCSession` e `rc=150`.
  O `-metadir` por execução (já existente) não cobre esse diretório.
- **Reprodução (antes, script do B-32 sem correção).** 10 rodadas de 8 execuções simultâneas (`I2Fb`, `semeado particao r3`, 1 worker, `OUTDIR` distinto cada):
  **5 de 80** com `ERRO rc=150` (uma segunda bateria idêntica, ainda com o script antigo: 4 de 80; não arquivada). Evidência: `runs/tlc/audit-b32/rc150/antes-80-resumos.tsv`, amostras em `rc150/antes-amostra-falha/`
  (`r2_1-r3-I2Fb.out` mostra o `NullPointerException` e `Module-Table lookup failure`).
- **Correção.** `spec/run-tlc-propriedades.sh`: `JTMP="$(mktemp -d ...)"` e `-Djava.io.tmpdir="$(winpath "$JTMP")"` por execução, removido ao final. O cabeçalho do `.out` passa a mostrar
  `Parsing file ...\Temp\tlc-jtmp-XXXXXX\TLC.tla` (`rc150/depois-amostra/`).
- **Depois.** 15 rodadas de 8 execuções simultâneas: **0 de 120** com erro (`rc150/depois-120-resumos.tsv`: 120 `VIOLADA traco_de_3_estados`). A exigência do plano era 6 simultâneas.
- **Célula `queda I4M` reexecutada** (parâmetros originais, 4 workers, 60 s): `SEM_VIOLACAO_ATE_AQUI limite_de_tempo_60s 581350 87893` (`runs/tlc/audit-b32/rc150-reexec-queda-I4M/`). O original repetido tinha 773.116 gerados e 114.802 distintos em 60 s;
  a diferença é a carga da máquina (outros processos em paralelo), não resultado.
- **Classificação dos `rc`:** `0` (sem erro), `12` (violação), `124` (timeout); qualquer outro vira `ERRO` (comentado no script). Nota: `OUTDIR` deve ser caminho absoluto (o script faz `cd` para `spec/` depois de criar o diretório).

## 7. A.6: traços e o artefato do relógio (P-26)

Comando: `python3 scripts/audit_tracos.py runs/tlc runs/tlc/audit-b32/tracos 'prop-*'` (e `... runs/tlc/audit-b32 runs/tlc/audit-b32/tracos-controle 'ctrl-*'`).
Para cada traço de violação, `traco-<dir>-<prop>.md` traz a sequência de ações, os estados compactos, a leitura do mecanismo e a coluna "depende do artefato D-4?".
Critério mecânico: algum passo local (`Login`/`Logout`/`Block`/`Unblock`/`IncrementFailure`/`ResetFailures`) que incrementa `relogio` e não altera o registro local (o gate `novoTS > ts` rejeitou por `relogio` defasado, assinatura de `I1P`).

- **77 traços de `prop-*`: 59 não dependem de D-4, 18 dependem** (`runs/tlc/audit-b32/tracos/classificacao-d4.tsv`). Os 18 são todos do **modelo livre** (`I1P`, `I1F`, `I4F` em sem falha e queda, mais `-simulate`).
  **Nenhum traço semeado depende de D-4.**
- Vereditos independentes de P-26 (todos os semeados e, no livre: `I1F`/`I4F`/`I1M`/`I4M`/`I2Fa` sob partição, `I2Fb`, `MesclarPerdeChave`): ver catálogo seções 3 e 5.
- Limite honesto: a assinatura mecânica detecta só passos rejeitados no próprio traço. Que o semeado não exiba a assinatura nos traços de **violação** não prova que o artefato seja inalcançável nas execuções que não violaram
  (`queda` e `partição` não fecharam para `I1P`).
- Para o Senior (P-7): ler `runs/tlc/audit-b32/tracos/traco-*.md`; o texto de "Leitura" é meu, a verificação passo a passo contra `Next` é do Senior.

## 8. A.7: decisões recomendadas ao Gestor

1. **Manter `I4Fp`/`I4Mp`** (recomendação do plano, confirmada pelos dados). `I4F` já é violada sem falha em 5 estados e, sob partição, seu traço mínimo (4) é a reversão do `I1F`; `I4Fp` isola a troca de motivo.
   Ressalva obrigatória: `I4Fp` é violada **sem falha** (`Logout`×`Block` por canal assíncrono, 5 estados) e entra como `VIOLADA-POR-CONSTRUCAO`; `I4Mp` só é "atribuível à fusão" porque o passo violador é `Mesclar`, mas a pré-condição existe sem falha.
2. **P-26 registrado** (`ABERTO-ACEITO`). Pergunta Q-5: corrigir `relogio' = max(relogio, ts recebido)` em variante e reverificar, ou manter?
3. **P-27 registrado** (`ACEITO`, decisão D1): mecanismos (a) `comportamento do código` e (b) `artefato do modelo`. Pergunta Q-3: o auto-bloqueio por limite sem cascata é intencional no domínio?

## 9. Discrepâncias e pontos de atenção

- **Discrepâncias numéricas entre o relatório B-32 e os brutos: nenhuma.**
- **Leituras que a auditoria ajusta (não são números errados):**
  1. `I4F` sob partição aparece no B-32 como "Confirmada, com ressalva". No catálogo é `VIOLADA-POR-CONSTRUCAO` nas três colunas, porque já é violada sem falha (5 estados).
  2. `I4Mp` e `I4Fp` não são provas de efeito da partição (seção 8).
  3. Os "vácuos" do B-32 eram argumento estrutural; agora são `vácuo-verificado` nas configurações reduzidas e continuam `vácuo-estrutural` na cota do plano.
  4. `I1F` sem falha e queda em 3 réplicas nas cotas do plano **não fechou**; só vale "preservada até" as cotas da seção 4.1 do catálogo.
- **Limitações:** o controle sem simetria cobre violações e fechamentos até ≈ 20 M de estados distintos; `ev1`/`ev2-longo` ficam sem controle sem simetria. A leitura P-7 dos traços ainda é do Senior.
  A máquina era compartilhada (outros Workers), então tempos de parede dos controles não são comparáveis aos do B-32.

## 10. Onde estão as evidências

| Evidência | Caminho |
|---|---|
| Tabela recalculada, divergências, alegações | `runs/tlc/audit-b32/{tabela-recalculada,divergencias-relatorio,alegacoes-relatorio}.tsv` |
| Verificação de `.cfg` | `runs/tlc/audit-b32/verificacao-cfg.txt` |
| 4 workers × 1 worker | `runs/tlc/audit-b32/comparacao-4workers-vs-1worker.tsv` |
| Controles sem simetria (brutos) | `runs/tlc/audit-b32/ctrl-*/` (`.cfg`, `.out`, `resumo-*.tsv`) e tabela `runs/tlc/audit-b32/controle/tabela-recalculada.tsv` |
| Com × sem simetria | `runs/tlc/audit-b32/comparacao-simetria.tsv` |
| Cobertura | `runs/tlc/audit-b32/cov-*/`, `runs/tlc/audit-b32/cobertura-mesclar.tsv` |
| `rc=150` | `runs/tlc/audit-b32/rc150/`, `runs/tlc/audit-b32/rc150-reexec-queda-I4M/` |
| Traços anotados (P-26) | `runs/tlc/audit-b32/tracos/`, `runs/tlc/audit-b32/tracos-controle/` |
| Scripts | `scripts/audit-tlc-runs.sh`, `scripts/audit_tlc_runs.py`, `scripts/audit_tracos.py`, `scripts/audit_comparacao_simetria.py`, `scripts/audit-cobertura.sh`, `scripts/run-controle-sem-simetria-b32.sh`, `spec/run-tlc-propriedades.sh` |
| P-26 e P-27 | `docs/registro-de-problemas.md` |
| Catálogo | `docs/catalogo-operacao-falha-invariante.md` |
