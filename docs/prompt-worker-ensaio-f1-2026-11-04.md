# Prompt do Worker: `ensaio-f1.sh`, ensaio do F1 silencioso na VM, 2026-11-04

> Escrito pelo Senior em 04/11, com o Gestor dormindo. Só documento, nenhuma VM tocada, nenhum push. Resolve a Q-17 de
> `docs/roteiro-vm-2026-11-04.md`. As perguntas **E-1..E-7** (seção 8) estão pendentes do Gestor: o Worker implementa a
> recomendação do Senior em cada uma e marca a linha com `# E-n (pendente do Gestor)`.
> **Não mexer** nos arquivos de `runs/vm-s6-2026-11-04/` e `runs/vm-s7-2026-11-04/`, que outro Worker está escrevendo.

## 0. O que o ensaio é, e de onde vêm os critérios

- **Pedido:** `wt-plano-ctrl/docs/prompt-f1-kill-2026-10-27.md` §4.2, aprovado pelo Gestor em 28/10 (Q4 = Sim, 1 sessão):
  na VM, **depois da V-S7 e antes da recriação final do cluster do *rebaseline***, 2 F1 `silencioso` sem carga e 2 com
  carga (S1 sync e S1 async, 300 s, mesma invocação do *runner*, semente fora da faixa da matriz), com o *bundle* novo;
  medir o mesmo da §4.1 mais o veredito do `VerificadorSanidade` e as contagens de erro; brutos em
  `runs/ensaio-f1-silencioso-<data>/`. Uma recusa sistemática de K3 sob F1 leva ao PR §8 (entrada nova antes da noite 1),
  **não** à troca do mecanismo.
- **Mecanismo e K6:** DT-017 (`wt-plano-ctrl/docs/decisoes-tecnicas.md:513`) e complemento 2 do pré-registro §2 a §4:
  guarda de netns, `ip link set dev eth0 down` no netns de isn2, `podman kill isn2 --signal KILL` (ExitCode 137), 60 s,
  `podman start isn2`; o `injetor.log` tem `modo=silencioso`, `eth0 down`, `kill isn2`, `ExitCode=137` e `start isn2`;
  exit 0; disparo entre +48 e +51 s da âncora `Warm-up ate +` (DT-011, P3); `died` de isn2 com código ≠ 137 é recusa
  (nota P2 da adenda da DT-009 parte 2).
- **O que a §4.1 mede e as paradas:** Δdet = primeira `ISPN000094` sem isn2 em isn1 e isn3 menos o instante da injeção
  (carimbo por linha do `injetor.log`); código de saída (`podman inspect … .State.ExitCode` antes do `start` e
  `podman events --filter event=died`); ausência de `ISPN000390` e `ISPN080002` na vida antiga de isn2 (sem saída ordenada);
  na vida nova, `ISPN000693` ausente e `ISPN000389`/`ISPN080001` presentes; volta a 3 nós HEALTHY sem
  `HEALTHY_REBALANCING`, com o tempo desde o `start`, abaixo de 180 s (parada (e)); `ip -br link` no netns novo de isn2 com
  `eth0` UP; IP de isn2 igual ou diferente do antigo (só registrar). **Parar e reportar** se o `silencioso` der Δdet < 30 s.
  A faixa [38, 55] s era expectativa, não critério; o *smoke* deu 36,5 a 41,9 s e a explicação pelo FD_ALL3 dá cerca de
  [32, 49] s (`validacao-f1-silencioso-2026-10-28.md` item 6). Nada é ajustado para "fazer passar".
- **Re-medição §5 e adenda da DT-009 parte 2:** os *hashes* do *bundle* (`git archive 20805b0`): `inject-crash.sh`
  `5b4d5719…c0cf`, `run-rebaseline.sh` `d95305fe…7366`, `analysis/rebaseline.py` `3005f49a…c7cd`, jar `5eced136…efaf`, lista
  `003814e9…33a4`; o aviso do R4 (disparo do *runner* a ~+51,4 s no host) é esperado. O ensaio confirma na VM o ExitCode 137
  do `podman kill` (nota P1 da adenda).

## 1. Onde o *script* mora e como chega à VM

O *bundle* do *rebaseline* é um `git archive` de `20805b0` mais o jar congelado, e não se edita. O *script* do ensaio fica
na branch `test/b29-gate-v4` (worktree `wt-gate-v4`) e chega à VM no *bundle* v4h, em `~/tcc-gate`. Ele **usa** o
`inject-crash.sh` e o jar do diretório do *rebaseline* na VM, passado em `--rb-dir`, e confere os *hashes* de lá contra a
tabela da adenda antes de qualquer coisa.

| Entrega | Caminho |
|---|---|
| *script*, teste, roteiro | `runs/ensaio-f1-silencioso-2026-11-04/{ensaio-f1.sh,ensaio-f1.test.sh,ROTEIRO-ENSAIO-F1.md}` |
| atributo `-text` dos brutos | uma linha `runs/ensaio-f1-*/** -text` no fim do `.gitattributes`, num `chore:` próprio (mesmo motivo da P-4 da V-S4) |
| *bundle* | `tcc/gate-v4h.bundle` + `.sha256`, a partir da ponta que a VM tiver (v4g se já levado; senão v4f), não levado à VM |

Commits: `chore:` (gitattributes), `test:` (vermelho), `feat:`, `docs:`. Sem push, sem VM.

## 2. Interface

```
bash runs/ensaio-f1-silencioso-2026-11-04/ensaio-f1.sh --rb-dir <dir do git archive 20805b0 na VM> \
     --boot-esperado <uuid> --bloco-anterior <dir vm-UTC do S7> [--so <1|2|3|4>]
     --dry-run | --hashes | --avaliar <dir vm-UTC>
```

`--boot-esperado` e `--bloco-anterior` como no `s6.sh`/`s7.sh` (seção 2.1 de `docs/prompt-worker-s6-s7-g5b-2026-11-04.md`):
nada fixo no código; `boot_id` atual = argumento = `boot_id fim` do bloco anterior; kernel igual ao do bloco anterior. Como o
cluster é recriado para o ensaio (E-2), aqui **não** se exige ID/`StartedAt` iguais ao bloco anterior; exige-se, sim, que
os 3 contêineres tenham `.Created` posterior ao fim do S7 e que nenhum tenha reiniciado desde a recriação
(`StartedAt` = o da recriação, registrado no passo 0). `--so n` roda só a execução n (para repetir uma, com GO novo). Saídas:
0 todas válidas; 1 pré-condição; 2 argumentos; 3 portão; 4 parada (Δdet < 30 s, cluster que não volta em 180 s, injetor com
exit ≠ 0); 6 avaliação com alguma execução inválida; 130 Ctrl-C. `ENSAIO_TESTE=1` só nos testes.

## 3. Passos

Saída `runs/ensaio-f1-silencioso-2026-11-04/vm-<UTC>/`, console em `ensaio-console.log`, `rc.txt`, rc lido na mesma
linha do pipeline em todo `tee "$OUT/` (R-3 da V-s5sh).

| Passo | Ação | Falha |
|---|---|---|
| 0 | Pré-condições, só leitura: `TCC_DIR`, tmux, terminal, `/var/lock/tcc-vm.lock` **OCUPADO** (roda dentro do `flock`), lock do `inject-crash.sh` livre, `sudo -n`, `boot_id`/kernel (seção 2), *hashes* do `--rb-dir` contra a adenda (os 7 arquivos e a lista `003814e9…`), sha256 do próprio *script*, ambiente sem `PODMAN`/`LOCK_DIR`/`RB_*` exceto `RB_PASSWORD`/`G_TESTE`/…, `RB_PASSWORD` presente (lido com `read -rs` antes, como na checklist 10.6 passo 8; nunca gravado), 0 timers `tcc-heal*`, 3 nós `running`, os 4 caches existem (`sessions`, `counters`, `sessions-async`, `counters-async`) e vazios, XML em uso com sha256 `6d52413b…` (o de `20805b0`), guarda de netns de isn2, `ip -br link` de isn2 com `eth0` UP, `java -version`, diretório de saída novo | exit 1 |
| 1 | `inject-crash.sh --dry-run --node isn2 --duration 60`, gravado | exit 1 |
| 2 | Portão: 3 nós HEALTHY com `number_of_nodes=3` há ≥ 60 s, sem `HEALTHY_REBALANCING`, sem `netem` nos 3 netns (o K1 do PR §6) | exit 3 |
| 3..6 | Uma execução por passo (seção 4), cada uma com o seu GO, o seu portão K1 antes e a sua coleta | ver 4 |
| 7 | Estado final: portão; 0 timers; `boot_id` do fim | (avaliação) |
| 8 | Avaliação (seção 5) e `MANIFEST.sha256` do `vm-<UTC>/` | exit 6 |

## 4. As 4 execuções

| n | Carga | Replicação | i (E-3) | Semente |
|---|---|---|---|---|
| 1 | sem | — | 2000 | — |
| 2 | sem | — | 2001 | — |
| 3 | S1, 300 s | `sync` | 2002 | 50 000 + 100·2002 = 250 200 |
| 4 | S1, 300 s | `async` | 2003 | 250 300 |

**Com carga (3 e 4):** a linha de comando do *workload* é a de `montar_cmd` de `scripts/run-rebaseline.sh:273-278`, com as
constantes de `run-rebaseline.sh:48-51` (`DURACAO=300 WARMUP_MIN=30 THREADS=4 OPS=0 REP=1 MIX=v3 RESET_STATE=on`,
`INJ_OFFSET=49`, `F1_DUR=60`), lidas **do `--rb-dir` em tempo de execução** e não redigitadas: o *script* extrai as duas
linhas de constantes e o corpo de `montar_cmd` do `run-rebaseline.sh` do `--rb-dir` (cujo hash já foi conferido) e falha se
não as encontrar. O F1 é disparado a +49 s da linha `Warm-up ate +` do `stdout.log` (*polling* ≤ 200 ms, como o *runner*),
com `inject-crash.sh --node isn2 --duration 60`. Depois do *workload*: `VerificadorSanidade --csv rep-001.csv --scenario S1`
com o jar do `--rb-dir` (mesma classe que o *runner* usa, `run-rebaseline.sh:874`), saída e exit gravados.

**Sem carga (1 e 2):** o mesmo injetor, disparado depois do portão, sem *workload*. Sem âncora: o instante de referência é
o carimbo da linha de isolamento do `injetor.log`.

**Por execução, sempre:** GO (E-1) com o rótulo `F1 n=<n> i=<i> carga=<...>` conferido no prompt, gravado em `go.log`
próprio do ensaio (não versionado); `podman events --filter container=isn2 --format json` em segundo plano do GO até a volta;
`podman logs -t --since <GO>` dos 3 nós; `podman inspect isn2` antes do `start` (lido pelo injetor) e depois; `ip -br link`
e IP de isn2 depois da volta; saúde a cada 2 s com o `saude_loop` copiado **verbatim** do `s5.sh` (teste de igualdade, como
no prompt do S6/S7, 4.5); `injetor.log`; e, nas com carga, `stdout.log`, `rep-001.csv` (**comprimido com `gzip -1` depois do
verificador**, E-6), `violacoes-rep-001.csv`, saída do verificador. Uma execução só começa depois do portão K1 da seguinte,
nunca em paralelo.

**Paradas automáticas (param o ensaio inteiro, exit 4):** Δdet < 30 s (§4.1); cluster que não volta a 3 HEALTHY sem
`REBALANCING` em 180 s do `start` (parada (e)); injetor com exit ≠ 0 (o contrato C2: 1/4 = recusa K6, 2/3/5 = infraestrutura).
Numa parada, o *script* grava o estado (contêineres, `ip -br link` de isn2, timers) e **não** tenta consertar: com isn2
morto e sem `start`, a instrução impressa é `sudo podman start isn2`, para o Gestor decidir.

## 5. Avaliação automática, por execução

Formato do `s5.sh` (`OK:`/`FALHA:`/`RESULTADO:`/`LEITURA HUMANA:`; última linha `ENSAIO: n de 4 VALIDAS`).

| # | Critério (FALHA se não valer) | Origem |
|---|---|---|
| F-1 | `injetor.log` com `modo=silencioso`, `eth0 down`, `kill isn2`, `ExitCode=137`, `start isn2`; injetor exit 0 | DT-017, compl. 2 §3 (K6) |
| F-2 | todo `died` de isn2 durante a execução tem código 137 (`podman events`) e `.State.ExitCode` lido antes do `start` = 137 | DT-017, nota P2 da adenda |
| F-3 | (só 3 e 4) disparo entre +48 e +51 s da âncora | DT-011 P3 |
| F-4 | Δdet ≥ 30 s em isn1 **e** isn3 | §4.1 (parada) |
| F-5 | nenhuma `ISPN000390`/`ISPN080002` na vida antiga de isn2 | §4.1 (saída ordenada ausente) |
| F-6 | vida nova com `ISPN000389` e `ISPN080001`, sem `ISPN000693` | §4.1, compl. 2 §4 |
| F-7 | 3 nós HEALTHY sem `HEALTHY_REBALANCING` em < 180 s do `start` | §4.1, parada (e) |
| F-8 | `eth0` UP no netns novo de isn2 | §4.1 |
| F-9 | `boot_id` igual no início e no fim do ensaio; intervalo máximo do saude-log ≤ 2 × 2 s | V-S4 P-2 |
| F-10 | estado final: portão OK, 0 timers, caches vazios | §5.1 das decisões de 16/10 |

`RESULTADO` (não invalida o ensaio; é o que o ensaio veio medir): Δdet de cada nó (e se cai em [32, 49] s); tempo de volta;
IP de isn2 igual ou não; nas com carga, veredito e exit do `VerificadorSanidade`, contagens de `ERROR_*` e de cada desfecho,
latência máxima na janela da falha, e o que o K3 do PR teria decidido (E-5). `LEITURA HUMANA`: a ordem
isolamento → kill → vista sem isn2 → start → vista com isn2 nos logs; o tempo do `podman start` (no WSL deu 51 s).

## 6. Testes (TDD) e mutantes

`ensaio-f1.test.sh` com *shims* (`sudo`, `podman` com `events`/`inspect`/`logs -t`/`kill`/`start`, `nsenter`, `ip`, `flock`,
`systemctl`, `curl`, `java` com um *stub* de *workload* que escreve `Warm-up ate +` e um CSV mínimo, e um *stub* do
`VerificadorSanidade`), numa árvore falsa com um `--rb-dir` falso. No mínimo: caminho feliz das 4; cada pré-condição
falhando antes de qualquer injeção (inclusive *hash* do `--rb-dir`, cache ASYNC ausente, XML diferente, `RB_PASSWORD`
ausente, lock livre, contêiner reiniciado); GO ausente ou com rótulo errado (nenhuma injeção); cada F-n violado isoladamente;
cada parada (Δdet 29 s; volta em 181 s; injetor exit 1, 2 e 5); `--so 3`; Ctrl-C real (`set -m`, `kill -INT` ao grupo); a
senha nunca aparece em arquivo nenhum do `vm-<UTC>/` (grep da senha do teste); as constantes e o `montar_cmd` vêm do
`run-rebaseline.sh` do `--rb-dir` (um `--rb-dir` com `THREADS=8` muda a linha de comando, e o teste vê); nenhum `tc` e
nenhum `podman kill/start` sai do *script* fora do injetor (o *shim* registra a origem); `--avaliar` refaz a avaliação igual;
verificação estática do rc na mesma linha. Mutantes, relatados um a um: Δdet sem a parada de 30 s; F-2 aceitando 143; F-3
com tolerância de ±5 s; senha gravada no `parametros.txt`; constantes redigitadas em vez de lidas; duas execuções sem o
portão entre elas. Medir e informar a duração; rodar sozinho.

## 7. Restrições

Nada na VM, nenhum push. Não editar `inject-crash.sh`, `run-rebaseline.sh`, `rebaseline.py`, o jar, a matriz nem nada do
*bundle* congelado; não editar `runs/vm-s*/`. Se o `run-rebaseline.sh` não permitir extrair as constantes de forma robusta,
**parar e perguntar**, não redigitar. Critério que não estiver na seção 5 vira pergunta. Relatório no chat: commits, testes
(PASS/FAIL, duração), mutantes, sha256 do *script* e do *bundle* v4h, desvios, dúvidas.

## 8. Perguntas ao Gestor (com a recomendação do Senior)

| # | Pergunta | Recomendação |
|---|---|---|
| **E-1** | Um GO por execução (4 GOs, cada um com o rótulo da execução) ou um GO para o ensaio inteiro? | **Um por execução.** Cada uma mata isn2; o GO por injeção é a regra de todos os blocos de VM, e o ensaio é acompanhado |
| **E-2** | O cluster atual (XML `8792dea3…`, do gate v4) não tem caches ASYNC. Recriar com o XML do *rebaseline* (`6d52413b…`, de `20805b0`) só para o ensaio e recriar outra vez antes do piloto (Q-17 (a) do roteiro)? | **Sim.** O instante da checklist 10.6 passo 3 (`--cluster-criado-apos`) é anotado antes da **segunda** recriação, então o piloto continua num cluster que nunca viu falha |
| **E-3** | Índices e sementes do ensaio: i = 2000..2003, semente = 50 000 + 100·i (fora da campanha 0..119+reposições e do piloto 1000..1011, e sem colidir com nenhum par da R-S2) | **Aprovar** |
| **E-4** | As 2 sem carga: só o injetor, depois do portão, sem *workload* (a §4.2 não diz como) | **Aprovar**; é o que a §4.1 fez no *smoke* |
| **E-5** | Nas com carga, o K3 e o C1..C3 do PR são calculados só como `RESULTADO` (o ensaio não é dado de campanha); uma recusa nas duas vira o assunto do PR §8 antes da noite 1 | **Aprovar** |
| **E-6** | Comprimir o `rep-001.csv` das execuções com carga com `gzip -1` (cerca de 50 MB cada comprimidas, contra ~200 MB) por causa do disco (seção 7 do roteiro) | **Aprovar**; o sha256 do CSV é gravado antes de comprimir |
| **E-7** | Uma parada automática (Δdet < 30 s, volta > 180 s) encerra o ensaio inteiro, e a retomada é com `--so n` e GO novo depois de o Senior ler os brutos | **Aprovar** |
