# Relatorio de preparo da REP=2 (Go-1, sem injecao) - 2026-10-16

Worker-2. Nada foi injetado: nenhum `inject-*` sem `--dry-run`, `tc add/change`, `kill`, `g1.sh` ou `g1b.sh` foi executado. Sem push.

## 1. Bundle (host)

- Branch `tmp/vm-bundle-2` (worktree `wt-bundle2`) a partir de `feature/etapa-formal-integracao` (e6e5985, intacta); `git cherry-pick 7ca5dfa..fb12c98` limpo, sem conflito (5 commits da sonda).
- **SHA da ponta: `544879150d70e146d4b3f5a4847c13d8f824f5b9`** (87 commits).
- JDK 22.0.2 (Maven); `java` do PATH e 1.8 e nao foi usado.

| Verificacao | Resultado |
|---|---|
| `mvn -f workload/pom.xml test` (soma do Surefire) | 205 testes, 0 falhas, 0 erros, 3 skipped |
| inject-partition (Git Bash) | 125 PASS, 0 FAIL, 2 SKIP |
| inject-netdegrade (Git Bash) | 298 PASS, 0 FAIL, 1 SKIP (T-nd-21, flock real ausente no host) |
| inject-jitter | 35 PASS, 0 FAIL |
| `g1b.test.sh` | falhas=0 |
| `python -m unittest discover -s analysis` | Ran 9 tests, OK |
| `jar tf` | `SondaHotrod.class`, `SondaHotrod$Args/$Abridor/$Alvo/$Sessao/$1`, `SondaCsv.class` presentes |

- jar sombreado `session-workload-0.1.0-SNAPSHOT.jar` (18.719.440 B) sha256 `c58c8b5e87e3ed8ceb6dc12b86bbf93e2b928b9d319262d5e464c521596dd61c`.
- `vm-bundle-2.bundle` (905.376 B, em `C:\Users\Francesco\Development\tcc\`) sha256 `71800af0c83200a587497821b09550b1d520f7f4027731b0e7973053bf8e6565`; `git bundle verify`: ok (sha1).

## 2. Go-1 na VM (server1@192.168.137.150, so chave)

- `scp` de bundle e jar para `~`; `sha256sum` na VM identico aos do host (ambos).
- Clone `~/tcc-gate` estava em `4978e1c` (detached) com 4 arquivos "M" (`g-lib.sh`, `g0.sh`, `g0b.sh`, `g1.sh`): `git diff` mostra **so mudanca de modo 100644->100755**, nenhum conteudo. Isso bloqueou o `checkout` normal; usado `git -c core.fileMode=false checkout tmp/vm-bundle-2` (sem alterar config persistente). Apos o checkout: HEAD = `544879150d70e146d4b3f5a4847c13d8f824f5b9` (confere), `grep -rlI $'\r' runs/gate-b29/*.sh scripts/*.sh | wc -l` = **0**. Os 4 arquivos seguem com bit x local (inofensivo; `g1b.sh` vem 100644 e e chamado por `bash`).
- `java -version` na VM: openjdk 17.0.19 (>= 17, ok). `flock`, `tmux`, `git`, `mvn` 3.8.7, `podman` presentes. Sem `unzip` (nao necessario).
- Suites na VM, sem injecao:

| Suite | Resultado |
|---|---|
| inject-partition (flock real) | 127 PASS, 0 FAIL, 1 SKIP (T-par-6, esperado) |
| inject-netdegrade | 306 PASS, 0 FAIL, 0 SKIP |
| inject-jitter | 35 PASS, 0 FAIL |
| g1b.test.sh | falhas=0 |
| `jar tf` na VM | 6 entradas `SondaHotrod*` |
| `mvn -o test` na VM | BUILD SUCCESS mas **0 testes executados** (Maven 3.8.7 usa surefire 2.12.4 por defeito e nao ha provider JUnit 5 no `~/.m2` offline). Nao e falha de codigo; os 205 testes foram medidos no host. Nao ha como rodar o JUnit na VM sem baixar plugins (mudanca que exigiria go) |

- jar copiado para `~/tcc-gate/workload/target/` (diretorio ignorado pelo git), onde o glob padrao de `g1b.sh` o encontra; copia extra em `~/rep2-jar/`. Sem isso o `g1b.sh` abortaria ("jar nao encontrado").

## 3. Pre-voo somente leitura (VM)

```
podman ps: isn1/isn2/isn3 Up 2 hours (healthy)
health 11222/11223/11224: HEALTHY, number_of_nodes=3 (x3)
tc qdisc show dev eth0 (netns de isn1/isn2/isn3, pids 4111/4247/4656, distintos do netns do host): "qdisc noqueue 0: root refcnt 2" nos tres; sem clsact; tc filter ingress vazio
systemctl list-timers | grep -c tcc-heal: 0
tmux ls: no server running; /var/lock/tcc-vm.lock existe (0 B, livre); nenhum processo flock/inject-/g1/SondaHotrod
```

Endpoints REST de leitura (GET, `--digest`), portas 11222 e 11224, resultados iguais:

| Endpoint | HTTP | Corpo |
|---|---|---|
| `/rest/v2/cache-managers/default/caches` | 200 | JSON lista com `counters` e `sessions`: `status RUNNING`, `distributed-cache`, `health HEALTHY`, `rebalancing_enabled true` |
| `/rest/v2/caches/sessions?action=get-availability` | 200 | `AVAILABLE` (texto puro) |
| `/rest/v2/caches/counters?action=get-availability` | 200 | `AVAILABLE` |
| `/rest/v2/caches/sessions?action=stats` | 200 | JSON (`time_since_start` ~6080, `approximate_entries 0`, `stores 0`, ...) |
| `/rest/v2/caches/counters?action=stats` | 200 | JSON idem |

Os dois endpoints marcados "a verificar" na validacao existem (200), entao a coleta do `g1b.sh` nao deve registrar 404.

## 4. Documentacao

- `python -m unittest discover -s analysis` no lugar de `python -m unittest` em `analysis/`: `docs/integracao-etapa-formal-2026-10-16.md` (l.39) e `docs/testes.md` (l.7 e l.225). Nao ha ocorrencia de `python -m unittest analysis` literal fora do proprio texto de validacao do Senior.
- **Texto proposto para a secao 5.3 do plano (NAO editado)**: "ordem: eol, b29, gate, fix contadores, fix netns, b29.2, auditoria(+b32), b33; cherry-pick de faixas; conflitos em inject-partition.test.sh e docs/testes.md mantem ambas as partes".

## 5. Comando exato proposto para a REP=2 (so com o Go-2 do Gestor, Gestor presente)

Na VM, dois terminais tmux (`tmux new -s rep2`, `Ctrl-b %`), ambos com `cd ~/tcc-gate` e o lock da VM (um `flock` por terminal nao serviria, pois o lock deve cobrir a execucao inteira; usar o mesmo arquivo em cada processo e nao o segurar entre eles):

```
# terminal B (primeiro; espera event=start ate 600 s; nao injeta)
REP=2 flock /var/lock/tcc-vm.lock bash runs/gate-b29/g1b.sh 2>&1 | tee runs/gate-b29/g1b-rep2.log

# terminal A (pede "GO"; injeta F2 de 90 s em isn3; arma o dead-man antes)
REP=2 flock /var/lock/tcc-vm.lock bash runs/gate-b29/g1.sh
```

Atencao: os dois `flock` no mesmo arquivo se excluiriam mutuamente (o segundo bloquearia ate o primeiro terminar, e o `g1b.sh` espera o `g1.sh`). Portanto, **o `g1b.sh` NAO deve ficar sob o mesmo `flock` do `g1.sh`**: proposta definitiva = `flock` somente no terminal A (`g1.sh`) e `g1b.sh` sem `flock` (ele nao injeta nem altera estado, apenas le e grava em `runs/`). A frase da validacao ("ambos sob flock") precisa de decisao do Senior; ver pendencias.

## 6. O que falta

1. Go-2 explicito do Gestor (injecao F2 90 s em isn3) e decisao sobre o flock (secao 5).
2. Declarar divergencias da sonda no relatorio da REP=2: `maxRetries` 1 contra 3 do workload; chave unica; cliente BASIC pinado; ligacao da sonda e coleta.
3. Apos a REP=2: `tc` limpo, size=3 HEALTHY, remover `sonda-*` de `sessions` e `counters`, trazer `runs/gate-b29/g1-rep2/` inteiro ao host, classificar C-2.
4. Opcional: alinhar na VM os bits de modo dos 4 scripts (cosmetico) e, se quiser JUnit na VM, definir se pode baixar surefire (exige go).
5. Integracao de `docs/preparo-rep2-2026-10-16` (local) e da secao 5.3 do plano: aval do Gestor.
