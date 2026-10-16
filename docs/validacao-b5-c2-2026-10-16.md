# Validacao do Senior: V-B5 (g1fix) e fechamento de C-2 (2026-10-16)

Escopo: leitura, clones descartaveis em `%TEMP%` e execucao de suites sem VM. Nada foi executado na VM, nada foi empurrado, nenhum script ou codigo foi alterado.

## A. V-B5: correcao do g1.sh (`test/b29-gate-v3-g1fix`)

### A.1 Integridade do bundle

| Item | Esperado | Medido |
|---|---|---|
| sha256 de `gate-v3b.bundle` | `12704ac9...ef2e8c7` | igual (`12704ac97101681f4b8658e3f3b5a1cdc9f8bb04a1dd19c8bc98dbc89ef2e8c7`) |
| HEAD do clone do bundle | `55caf9f28b507ea9b00bb0ddc4c5345d4efb7f84` | igual |
| CR nos `*.sh` (19 com modo 100755 + 8 100644) | 0 | 0 CR em todos |
| Modos | gate/testes executaveis | g0, g0b, g1, g1b, g5, g6, `*.test.sh`, `gate-test-lib.sh`, scripts `inject-*` = 100755; `g-lib.sh` e `g56-lib.sh` = 100644 (so `source`, correto) |

Observacao: os brutos versionados em `runs/gate-b29/g1-rep3/` (`saude-log.csv` 217 CR, `marcadores.txt`, `t_merge.txt`) e `g5/`, `g6/`, `go.log`, `recriacao/` tem CRLF (vieram da copia VM para Windows). Nao afeta scripts; o awk do g1.sh e tolerante (68 linhas HEALTHY/3 contadas com CRLF). Na VM o arquivo e gerado em LF.

### A.2 Suites (clone do bundle)

| Suite | Git Bash | WSL (Ubuntu, kernel 5.15) |
|---|---|---|
| g1.test.sh | 61 PASS / 0 FAIL | 61 / 0 |
| g1b.test.sh | falhas=0 | falhas=0 |
| g5.test.sh | 50 / 0 | 50 / 0 |
| g6.test.sh (sem carga) | 70 / 0 | 70 / 0 |
| inject-partition.test.sh | 125 PASS / 0 / 2 SKIP | 127 / 0 / 1 SKIP |

A rodada 63/7 do g6 nao se repetiu sem carga (70/0 nos dois ambientes).

### A.3 Vermelho e mutacoes

- Vermelho `af94c8d` contra o g1.sh antigo: **57 PASS / 4 FAIL** reproduzidos (o relatorio diz 5 FAIL). Os 4 sao: log nao segue >= 3 s apos HEALTHY/3; `t_saude_primeira_ns` ausente; ISO ausente; `t_merge_saude_s` vazio (este ultimo com regex do teste ainda errada). Com o teste corrigido (`22c6fff`) e o g1.sh de `a568a39^` (fix revertido): tambem 57/4. A diferenca 4 vs 5 e discrepancia de contagem do relatorio, sem efeito sobre a conclusao (o teste e vermelho de fato).
- Mutacoes sobre o g1.sh de HEAD (so g1.test.sh rodado):
  - M1 `G1_LOG_SAUDE_S=0` ignorado: morta (4 FAIL, T-g1-7 e T-g1-9).
  - M2 `PRIM` sem `size==3`: morta (2 FAIL).
  - M3 `PRIM` sem `$1>end`: morta (2 FAIL).
  - M4 sem o `sleep "$POS_S"`: **sobreviveu em 1 rodada, morta em 2** (60/1). T-g1-8 so fixa o POS_S de forma estreita (POS=4 s contra limite de 3 s e polling de 2 s). Ressalva de robustez do teste, nao do script.

### A.4 Leitura do g1.sh (HEAD 55caf9f)

- `G1_LOG_POS_S=30` (padrao): depois de detectar HEALTHY/3 nas 3 portas e extrair os logs, `sleep "$POS_S"` e so entao `parar_log_saude`. Com `G1_LOG_SAUDE_S=0` o bloco inteiro e pulado (nem espera nem escreve campos; T-g1-9).
- `t_merge.txt`: `t_merge_s` (polling, `health`) preservado; acrescenta `t_saude_primeira_ns`, `t_saude_primeira_iso`, `t_merge_saude_s`, `t_saude_todas_ns`, `t_saude_todas_iso`, `t_merge_saude_todas_s`. Se nao ha amostra: `t_saude_primeira_ns=ausente (...)`.
- `trap parar_log_saude EXIT` continua; o log nunca sobrevive ao script.

### A.5 Ressalva do Worker (split ainda nao percebido no end)

Os brutos mostram que o split e percebido bem antes do end:

| Rodada | Start -> split percebido (isn3 ISPN000094, size 1) | Start -> end | Margem |
|---|---|---|---|
| REP=2 (`wt-tb/.../g1-rep2`) | +46 s (06:49:03) | +96 s | ~50 s |
| REP=3 (`wt-gate-v3/.../g1-rep3`) | +40 s (saude-log: isn3 DEGRADED/1 em +40,2 s; ports 11222/11223 HEALTHY/2 em +48,2 s) | +96 s | ~48 s |

Em REP=3, de +48,2 s a +142,2 s (fim do log antigo) as tres portas mostram HEALTHY/2, HEALTHY/2 e DEGRADED/1; o MergeView e as 08:59:01,989 (end + 47,3 s). Portanto, na janela de 90 s (DUR) a primeira amostra HEALTHY/3 depois do end e real. O risco existe so se `G1_DUR` for encurtado abaixo de ~50 s ou se a rodada tiver perda mais baixa.

Recomendacao: o criterio `t_merge_saude_s` deve exigir, alem de `ts > end`, **uma amostra anterior ao end com size < 3 em alguma porta** (HEALTHY/2 ou DEGRADED/1), isto e, "split percebido antes do end", e aceitar HEALTHY/3 somente depois disso. Esse e o criterio de `t_merge` valido; o do MergeView (log-vista) e o confirmador independente. Nao e bloqueio para a REP=4 na janela de 90 s; tratar como validacao a posteriori na analise do `saude-log.csv` (um `awk` de leitura), sem mudar o g1.sh agora.

Achado correlato: em REP=2 o `t_merge_s` foi 188 s enquanto o MergeView veio ~45 s apos o end, e a captura de saude em isn1 mostra `HEALTHY_REBALANCING` com 3 nos (fixture `rep2-isn1-healthy-rebalancing-3nos.json`). O `t_merge` por polling mede "cache HEALTHY nos 3 nos", nao "vista unificada"; os dois tempos devem ser reportados.

### A.6 Bundle na VM e REP=4

Veredito do item: o bundle `gate-v3b.bundle` pode ir para a VM (sha256 e HEAD conferem, 0 CR, modos corretos, suites verdes em dois ambientes).

A variante B da REP=4 (cluster recriado, sem g1b e sem sonda, com o log de saude padrao ligado, `G1_LOG_POS_S=30`) nao precisa de nenhuma outra mudanca de script. Precisa so de: go novo do Gestor para a REP=4; recriacao do cluster (size=3, HEALTHY nas 3 portas, caches vazios); `REP=4 bash runs/gate-b29/g1.sh` (sem `G1_LOG_SAUDE_S=0`, para ter `t_merge_saude_s`); conferir depois `MergeView` e `Finished rebalance` dos `log-vista-*`. Se o Gestor preferir a variante A (REP=4 sem log), `G1_LOG_SAUDE_S=0`, como no relatorio do gate v3, secao 6.7.

**Veredito A: APROVADA COM RESSALVAS** (R1: vermelho medido 4 FAIL, nao 5; R2: M4 sobrevive de forma intermitente por tolerancia de temporizacao de T-g1-8; R3: registrar o criterio "split percebido antes do end" na analise do saude-log).

## B. Fechamento de C-2 (`feature/b33-3-c2-fixtures`, HEAD 0de0476)

### B.1 Testes
- Clone limpo: `mvn -f workload/pom.xml test` = **275 run / 0 fail / 0 err / 0 skipped** (33 classes). Base `fb12c98`: 205 run / 0 / 0 / 3 skipped. Nenhuma classe fora de `ClassificadorMensagemRealTest` mudou de contagem (199 = 199); essa classe foi de 6 (3 `@Disabled`) para 76. Os 184 testes do fim de E2 estao contidos nos 199 (conferido por contagem por classe, nao por nome).
- Arvore integrada (I + g1fix + 3 commits de C-2 por cherry-pick, clone descartavel): mvn 275/0/0/0; g1 61/0, g5 50/0, g6 70/0, partition 125/0/2.

### B.2 Fixtures contra os CSV reais
Comparacao automatica (Python, `csv.reader`) linha a linha das 20 capturas de `CapturasReais.java` com `wt-gate-v3/runs/gate-b29/g1-rep3/hotrod/sonda-isn3.csv` (283 linhas) e `wt-tb/runs/gate-b29/g1-rep2/hotrod/sonda-isn3.csv` (99 linhas): **20/20 iguais** em classe, mensagem, causas, cache, operacao e offset (1 casa decimal). Distribuicao: REP3 pre-deteccao 8, REP3 DEGRADED 8, REP2 4.
Os 4 JSON de `captura-real/` batem byte a byte (sem CR/LF final) com brutos: `rep2-isn1-healthy-rebalancing-3nos` = `saude-11222/11223-...458/482...` de g1-rep2; `rep3-isn1-healthy-2nos` = saude-11222 de g1-rep3 (+48 s em diante); `rep3-isn3-degraded-1no` = saude-11224 (+40 s em diante); `rep3-isn3-healthy-3nos` = saude-11222 (linha de base).

### B.3 Classificador
- Regra confirmada no codigo: excecao de transporte (`TransportException`, `HotRodTimeoutException`, `IOException`, `UncheckedIOException`, `TimeoutException`) ou cuja cadeia abaixo contem transporte nao avalia a propria mensagem. Marcas: `AvailabilityException`, `ISPN000306`, `ISPN000305`, `degraded mode`.
- Mapeamento: timeout puro (TransportException e RuntimeException do entrySet com SocketTimeout) -> ERROR_TRANSPORT; AvailabilityException/ISPN000306/ISPN000305 (HotRodClientException sem causas) -> ERROR_UNAVAILABLE; `InterruptedException` -> ERROR_TRANSPORT. Confere com as 20 fixtures.
- Remocao de `is not available`: segura para os dados observados (as mensagens de captura que a contem ja casam por `AvailabilityException`/`ISPN000306`) e e a direcao conservadora (evita falso positivo de textos como cache/recurso nao disponivel, que nao sao particao). Custo: uma indisponibilidade futura que chegue so com esse texto, sem a classe, viraria TRANSPORT. Mutacao que a recoloca **sobrevive** (equivalente nos dados da captura), logo a seguranca e argumento, nao e fixada por teste.
- Mutacoes (suites Classificador*/SessionOps*): M-a recolocar `is not available`: sobrevive (equivalente); M-b sem o salto por causa de transporte: morta (2 falhas, entrySet); M-c sem `ISPN000305`: sobrevive (redundante com `AvailabilityException`/`degraded mode`); M-d recolocar `DEGRADED`: morta (2 falhas); M-e retirar todas as marcas: morta (25 falhas). 3 mutacoes mortas de 5.
- Os 3 testes antigos editados (ClassificadorDeErroTest, 2 em SessionOpsBlockTest) so trocaram texto fabricado ("Cache 'sessions' is DEGRADED") pelo real ("AvailabilityException: ISPN000305 ... degraded mode"); consequencia direta da remocao de `DEGRADED`. Aceitavel.

### B.4 HealthRest e laco de convergencia
`HealthRest.Estado.healthy(cache)` e `"HEALTHY".equals(status)`; `HEALTHY_REBALANCING` conta como nao saudavel (teste com a fixture real de rep2). No `WorkloadMain` a sonda entrega `min(number_of_nodes, topologia de sessions)`, `healthy("sessions")` e `healthy("counters")`; uma rodada so e valida com os dois caches HEALTHY e todos os nos presentes. Adequado, e confirmado pela captura real (REP=2 teve 3 nos com HEALTHY_REBALANCING). Limite aceito: a consulta e a um unico no REST; a trava por topologia mitiga.

### B.5 As 7 linhas de log do servidor no teste
Sao decisoes de teste (nenhuma foi observada como excecao de cliente), documentadas no proprio teste: so ISPN000319 ("must stay in degraded mode") e tratada como indisponibilidade; visoes, MergeView, ISPN100011 (`DEGRADED_MODE`), ISPN100007, ISPN000517 e JGRP000011 nao. Aceitavel com a ressalva de que ISPN100011 nao casa porque `DEGRADED` foi removida, o que e coerente com a captura.

### B.6 Dominio e spec
`git diff fb12c98..0de0476 --name-only`: unico arquivo de `main` e `ClassificadorDeErro.java` (+38/-9); nada em `spec/` nem em dominio; resto em testes, resources e docs.

**Veredito B: APROVADA COM RESSALVAS** (R1: remocao de `is not available` e `ISPN000305` nao e fixada por mutacao; R2: contagem "184 anteriores" conferida por classe, nao por nome; R3: fixtures dependem de brutos REP=2 que estao so em `test/b29-gate-vm` (8394843)).

## C. Integracao local em `feature/etapa-formal-integracao` (especificacao, nao executada)

Fatos medidos:
- A ponta de `feature/etapa-formal-integracao` (e6e5985) e ancestral de `test/b29-gate-vm-v3` -> `test/b29-gate-v3-execucao` -> `test/b29-gate-v3-g1fix` (cadeia linear de 19 commits). `git merge --ff-only test/b29-gate-v3-g1fix` funciona e preserva o historico linear.
- `test/b29-gate-vm-v3` ja traz a sonda por copias cherry-pickadas (8b3963c, 55da813, 48e23ac, 7acb9c3, 5448791). `feature/b33-3-c2-fixtures` carrega os originais (afd0371, 743f9db, 8b74551, 03646e6, fb12c98): **nao** merge nem cherry-pick da branch inteira.
- `git cherry` de `test/b29-gate-vm` contra I: so `8394843` falta (REP=2 + g5/g6). g5.sh, g56-lib.sh, g6.sh, go.log conflitam add/add com o que o v3 traz (d1d9d1a diz "sem alteracao").

Ordem proposta (historico linear, sem merge commits):
1. `git merge --ff-only test/b29-gate-v3-g1fix` (traz vm-v3, execucao e g1fix, e a sonda).
2. `git cherry-pick 5573310 4944a5d 0de0476` (so os 3 commits de C-2). Dry-run em clone descartavel: sem conflito; mvn 275/0/0/0.
3. `git cherry-pick 8394843` (brutos REP=2, relatorio) com resolucao: manter a versao de HEAD em g5.sh, g56-lib.sh, g6.sh e unir go.log (conflito add/add medido nos 4). Alternativa mais limpa: `git cherry-pick -n 8394843` e `git restore --staged --worktree` nos 4 arquivos antes do commit. Necessario porque as fixtures REP=2 citam esses brutos.
4. Revalidar: mvn 275/0/0/0; g1 61/0; g5 50/0; g6 70/0; g1b falhas=0; partition 125/0/2 (Git Bash) e 127/0/1 (WSL).
5. Depois da REP=4 (se feita) e do relatorio, integrar so os brutos novos. `test/b29-gate-vm` nao precisa ser integrada alem do passo 3.

Nenhum push. Esta validacao fica em `docs/validacao-b5-c2-2026-10-16`.
