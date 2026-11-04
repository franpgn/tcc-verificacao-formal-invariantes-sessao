# Roteiro do ensaio do F1 silencioso na VM, 2026-11-04

Preparado pelo Worker. **Não foi executado na VM.** Pedido: `wt-plano-ctrl/docs/prompt-f1-kill-2026-10-27.md` §4.2 (aprovado em 28/10, Q4). Especificação: `docs/prompt-worker-ensaio-f1-2026-11-04.md`. Critérios: DT-017, complemento 2 do pré-registro §2 a §4, adenda da DT-009 parte 2 (notas P1 e P2), DT-011 (P3) e §4.1 do prompt do F1. Posição no dia: depois da V-S7 e antes da recriação final do cluster do *rebaseline* (`docs/roteiro-vm-2026-11-04.md`, passo 8, Q-17).

O que o ensaio mede, e não decide: Δdet de isn1 e isn3 no modo silencioso (primeira `ISPN000094` sem isn2 menos a linha de isolamento do `injetor.log`), o ExitCode 137 do `podman kill` no Podman da VM (nota P1), a volta a 3 nós, e, nas duas com carga, o que o K3 e o C1..C3 do PR teriam decidido. Uma recusa sistemática de K3 sob F1 leva ao PR §8, não à troca do mecanismo.

## Antes de rodar

- **Cluster (E-2, pendente do Gestor).** O cluster do gate v4 (XML `8792dea3…`) não tem `sessions-async`/`counters-async`. O ensaio exige o XML do *rebaseline* (`6d52413b…`, de `20805b0`) nos 3 nós, os 4 caches existentes e vazios, e os 3 contêineres com `.Created` posterior ao fim do S7. Recriar o cluster com esse XML **antes** do ensaio. O instante do `--cluster-criado-apos` do piloto é anotado antes da **segunda** recriação, depois do ensaio, para que o piloto rode num cluster que nunca viu falha.
- ***Bundle* do *rebaseline* na VM** (`git archive 20805b0` + jar congelado `5eced136…`), passado em `--rb-dir`. O ensaio confere os 7 *hashes* da adenda e a lista `003814e9…33a4` antes de qualquer coisa, e lê dele as constantes do PR e o corpo de `montar_cmd` (`run-rebaseline.sh:48-51`, `:63-67`, `:273-283`). Nada é redigitado no `ensaio-f1.sh`.
- **Senha.** `read -rs RB_PASSWORD; export RB_PASSWORD` antes do `flock` (checklist 10.6, passo 8). O ensaio só confere que está presente; a senha vai ao `curl` e à linha de comando do workload (como no *runner*) e nunca é gravada: `execucao.txt` e o console têm a linha mascarada (`--password ***`).
- **Host em silêncio** (sem TLC, `mvn` nem carga pesada) do passo 0 ao fim. O `boot_id` é conferido no início e no fim; um intervalo do saude-log maior que 2 × 2 s invalida a execução.
- **Tempo.** Cada execução: portão K1 (≥ 60 s), F1 de 60 s, `podman start` (51 s no WSL), volta. As com carga duram 300 s de workload mais o verificador e o `gzip -1`. Total esperado: 1,5 a 2 h, com 4 GOs.

## Como rodar

```bash
cd ~/tcc-gate
sha256sum runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh   # esperado b83e82230b8c69e61777499047e81d3055ec35423378af8a87500cf5ffda8d8c
read -rs RB_PASSWORD; export RB_PASSWORD
flock /var/lock/tcc-vm.lock tmux new -s ensaio
# dentro do tmux (RB = diretorio do git archive 20805b0 com o jar congelado):
bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh --dry-run --rb-dir "$RB"
bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh --rb-dir "$RB" \
     --boot-esperado "$(cat /proc/sys/kernel/random/boot_id)" --bloco-anterior runs/vm-s7-2026-11-04/vm-<UTC>
# ao fim: exit (libera o lock)
```

O `--boot-esperado` tem de ser igual ao `boot_id fim` do S7 (`07-boot-fim.txt` do `--bloco-anterior`); se não for, houve *reboot* entre blocos e o ensaio não roda. O kernel tem de ser o do S7.

## Os GOs (E-1, pendente do Gestor: um por execução)

Cada execução pede, no terminal, o texto exato abaixo, em até 15 min. Qualquer outra coisa (inclusive só `GO`) aborta com rc 3, **sem injetar**. Cada GO aceito vai para `runs/ensaio-f1-silencioso-2026-11-04/go.log` (não versionado) e para `exec-<n>-i<i>/go.txt`.

| n | Carga | GO a digitar | i (E-3) | Semente |
|---|---|---|---|---|
| 1 | sem (E-4) | `GO F1 n=1 i=2000 carga=sem` | 2000 | — |
| 2 | sem (E-4) | `GO F1 n=2 i=2001 carga=sem` | 2001 | — |
| 3 | S1, 300 s, `sync` | `GO F1 n=3 i=2002 carga=S1-sync` | 2002 | 250 200 |
| 4 | S1, 300 s, `async` | `GO F1 n=4 i=2003 carga=S1-async` | 2003 | 250 300 |

As sementes são `S0 + PASSO_SEMENTE · i`, com `S0` e `PASSO_SEMENTE` lidos do `run-rebaseline.sh` do `--rb-dir`.

## O que o `ensaio-f1.sh` faz

| Passo | Ação | Arquivo em `vm-<UTC>/` | Se falhar |
|---|---|---|---|
| 0 | Pré-condições, só leitura: `TCC_DIR`, tmux, terminal, lock da VM **ocupado**, nenhum `inject-crash.sh` em execução (o injetor não tem *lock* próprio), `sudo -n`, `boot_id` e kernel contra o S7, 7 *hashes* e a lista do `--rb-dir`, sha256 do próprio script, constantes e `montar_cmd` legíveis no `run-rebaseline.sh`, ambiente sem `PODMAN`/`JAVA`/`LOCK_DIR`/`RB_*` (exceto `RB_PASSWORD`)/`G_TESTE`/`ENSAIO_*`/..., `RB_PASSWORD` presente, 0 timers `tcc-heal`, java, 3 nós `running` criados depois do S7 e sem reinício desde a recriação (`StartedAt − Created ≤ 600 s`), XML montado = `6d52413b…` nos 3, os 4 caches com `size` 0 nas 3 portas, guarda de netns de isn2, `eth0` UP em isn2 | `00-pre.log`, `parametros.txt` | rc 1, nada tocado |
| 1 | `inject-crash.sh --dry-run --node isn2 --duration 60` | `01-injetor-dry-run.log` | rc 1 |
| 2 | Portão K1 (PR §6, constantes do `--rb-dir`): 3 portas com `cluster_health` HEALTHY, `number_of_nodes=3`, nada `REBALANCING` e sem `netem` nos 3 netns por 60 s, prazo 600 s | `02-portao.log` | rc 3 |
| 3..6 | Por execução: GO; `podman events` de isn2 e saude-log a cada 2 s em segundo plano; `StartedAt` dos 3 = o esperado; portão K1 próprio; `podman inspect isn2` antes; (com carga) workload com a linha de `montar_cmd`, âncora `Warm-up ate +` por *polling* de 200 ms e disparo a +49 s; `inject-crash.sh --node isn2 --duration 60`; volta (3 HEALTHY sem REBALANCING, a cada 5 s, prazo 180 s do `podman start`); `podman inspect` depois, `ip -br link` do netns novo, IP; `podman logs -t --since <GO>` dos 3; Δdet; (com carga) espera o workload, `VerificadorSanidade --csv rep-001.csv --scenario S1`, resumo do CSV, C1..C3, sha256 do CSV e `gzip -1` (E-6) | `exec-<n>-i<i>/` | ver paradas |
| 7 | Estado final: portão K1, timers, `size` dos 4 caches | `07-estado-final.log`, `07-portao-final.log` | (avaliação) |
| 8 | `boot_id` do fim, avaliação, `MANIFEST.sha256` (sem ele mesmo e sem o `ensaio-console.log`, que o `tee` ainda escreve) | `07-boot-fim.txt`, `08-avaliacao.txt`, `MANIFEST.sha256` | rc 6 |

Por execução, em `exec-<n>-i<i>/`: `go.txt`, `execucao.txt` (chaves `n`, `i`, `semente`, `comando` mascarado, `lanc_ms`, `anc_ms`, `disp_ms`, `injetor_exit`, `start_ms`, `volta_ok`, `volta_s`, `ip_antes`, `ip_depois`, `pid_depois`, `wexit`, `vexit`), `started.txt`, `portao.log`, `saude.csv`, `events-isn2.json`, `inspect-isn2-{antes,depois}.json`, `injetor.log`, `volta.log`, `ip-br-link-isn2.txt`, `logs-isn{1,2,3}.txt`; nas com carga também `stdout.log`, `rep-001.csv.gz`, `rep-001.csv.sha256`, `violacoes-rep-001.csv`, `sanidade.json`, `verificador.out`, `csv-resumo.txt`, `c1-c3.json`.

O console inteiro vai para `ensaio-console.log`; o rc de cada passo vai para `rc.txt`, lido na mesma linha do pipeline em todo `| tee` (R-3 da V-s5sh).

## Paradas automáticas (rc 4, E-7)

Param o ensaio inteiro: injetor com exit ≠ 0 (1/4 = recusa K6; 2/3/5 = infraestrutura), cluster que não volta em 180 s do `podman start`, Δdet < 30 s em isn1 ou isn3, e workload sem a âncora (nada injetado). Numa parada o script grava `parada.txt` (contêineres, estado e `ip -br link` de isn2, timers), faz a avaliação e o manifesto, e **não conserta nada**. Se isn2 ficar parado, a instrução impressa é `sudo podman start isn2`, para o Gestor decidir depois de o Senior ler os brutos. A retomada é com `--so <n>` e GO novo.

**Ctrl-C (rc 130).** Se o injetor estiver no meio, o script manda TERM a ele e espera até 150 s; o *trap* do `inject-crash.sh` devolve isn2 (kill + start, ou só start). Depois encerra o workload, o saude-log e o `podman events`, registra em `rc.txt` e, se isn2 não estiver `running`, imprime a instrução acima.

## Avaliação (`08-avaliacao.txt`)

Formato do `s5.sh`: `OK:`, `FALHA:`, `RESULTADO:`, `LEITURA HUMANA:`; uma linha `EXECUCAO n=<n>: VALIDA|INVALIDA` por execução e a última `ENSAIO: <k> de <N> VALIDAS`. Refazer no host, sobre a cópia: `bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh --avaliar runs/ensaio-f1-silencioso-2026-11-04/vm-<UTC>`.

| # | Critério (FALHA se não valer) |
|---|---|
| F-1 | `injetor.log` com `modo=silencioso`, `eth0 down`, `kill isn2`, `ExitCode=137`, `start isn2` (as regex do `k6` de `rebaseline.py`) e injetor exit 0 |
| F-2 | ao menos um `died` de isn2 no `podman events`, todos com `ContainerExitCode` 137 (ausente = 0, pelo `omitempty`), e `isn2 ExitCode=137` no `injetor.log` |
| F-3 | (3 e 4) disparo entre `INJ_OFFSET − 1` e `INJ_OFFSET + 2` s da âncora (48 a 51 s) |
| F-4 | Δdet ≥ 30 s em isn1 **e** isn3 (sem leitura = FALHA) |
| F-5 | nenhuma `ISPN000390`/`ISPN080002` na vida antiga de isn2 (linhas antes do `podman start`) |
| F-6 | vida nova de isn2 com `ISPN000389` e `ISPN080001`, sem `ISPN000693` |
| F-7 | volta a 3 HEALTHY sem REBALANCING em < 180 s do `podman start` |
| F-8 | `eth0` UP no `ip -br link` do netns novo de isn2 |
| F-9 | `boot_id` igual no início e no fim do ensaio (geral); intervalo máximo do saude-log da execução ≤ 2 × 2 s |
| F-10 | (geral) portão K1 final OK, 0 timers, e `size` 0 nos caches que nenhuma execução com carga desta invocação usou (D-1, abaixo) |

`RESULTADO`: Δdet de cada nó e se cai em [32, 49] s; tempo de volta; IP de isn2 igual ou não; nas com carga, exit e veredito do verificador e o que o K3 teria decidido (E-5), C1..C3 calculados com `rebaseline.criterios_mistura(..., "F1")` do `--rb-dir`, contagens de `ERROR_*` e de cada desfecho, latência máxima na janela disparo..volta, sha256 do CSV antes do `gzip`. `LEITURA HUMANA`: a ordem isolamento → kill → vista sem isn2 → start → vista com isn2, e a duração do `podman start`.

## Pendências e desvios declarados

- **E-1..E-7** implementadas pela recomendação do Senior e marcadas no código com `# E-n (pendente do Gestor)`.
- **D-1.** F-10 pede "caches vazios" no fim, mas o workload S1 deixa dados nos caches que usou (o reset do B-43.1 é no início da execução seguinte). O ensaio exige `size` 0 só nos caches não usados por carga na invocação (com as 4, nenhum) e registra os tamanhos como `RESULTADO`.
- **D-2.** O `inject-crash.sh` não tem *lock* próprio; a pré-condição "lock do injetor livre" virou "nenhum `inject-crash.sh` em execução" (`pgrep -f`).
- **D-3.** Com `--so 2..4`, isn2 já foi reiniciado pelo F1 das execuções anteriores do ensaio: o `StartedAt − Created` de isn2 só é registrado nesse caso.
- **D-4.** GO ausente ou errado sai com rc 3, o mesmo código do portão (a seção 2 do prompt não dá código próprio).
- **D-5.** Workload sem âncora é parada (rc 4, nada injetado), por não haver o que medir; a seção 4 do prompt não lista esse caso.
- **D-6.** Exige-se ao menos um `died` em F-2, para que um coletor de eventos quebrado não passe em branco.
- O saude-log usa o `saude_loop` verbatim do `s5.sh`, que tem `admin:infinispan` no código: se o `RB_PASSWORD` da VM não for `infinispan`, o saude-log grava HTTP 401 (não invalida nada; F-9 só mede o intervalo), mas o portão K1 e a volta usam o `RB_PASSWORD`.
