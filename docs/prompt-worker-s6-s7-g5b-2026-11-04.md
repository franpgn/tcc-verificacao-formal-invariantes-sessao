# Prompt do Worker: invólucros do S6 (G-5b-A) e do S7 (G-5b-B), 2026-11-04

> Escrito pelo Senior na madrugada de 04/11 (UTC), com o Gestor dormindo. Só documento, nenhuma VM tocada, nenhum push.
> As decisões marcadas **Q-nn** estão pendentes do Gestor (lista em `docs/roteiro-vm-2026-11-04.md`, seção 9). O Worker
> implementa a **recomendação** do Senior em cada uma e marca a linha no código com `# Q-nn (pendente do Gestor)`, para que a
> troca, se o Gestor decidir diferente, seja uma linha mais o teste correspondente.

## 0. Contexto

- Plano: `wt-plano-ctrl/docs/decisoes-senior-2026-10-16.md` §5.1 (princípios e portão de estabilidade), §5.2 (ordem; "o
  Gestor precisa estar presente em S4, S5, S6 e S7"), §5.7 (G-5b-A e G-5b-B: o que fazer, validade, o que o Senior
  verifica) e §5.8 (o que não se prova).
- Executor já existente e **que não se edita**: `runs/gate-b29/g5b.sh` (modos `A` e `B`), com `g56-lib.sh`, `g-lib.sh` e
  `scripts/inject-partition.sh`. O `g5b.sh` em uso é o do v4c (`902654b`), sha256 `6f50e3e3…0378`, já conferido na VM no
  S3 (`runs/vm-s3-2026-10-28/02-pos-l0c-leitura.log:9`). Ele arma o dead-man com `AccuracySec=1s`, injeta
  `--minority isn3 --duration 120`, espera o split confirmado nos logs (até 100 s do `start`), dá `kill -KILL` no injetor a
  partir de +70 s, confere que o corte persiste por 15 s e então, no modo A, faz o `--heal` manual e desarma depois de
  `HEALTHY` x3; no modo B, não cura nem desarma e espera o dead-man (prazo 120 + 25 = 145 s, tolerância 10 s).
- Modelo a seguir: `runs/vm-s5-2026-10-28/` (`s5.sh`, `s5.test.sh`, `ROTEIRO-S5.md`) e o parecer `V-s5sh.md`, inclusive as
  ressalvas R-1, R-2, R-3 e R-6, que viram requisito aqui (seção 6). Também `runs/vm-s4-2026-10-28/s4.sh` (passo 1 com
  `journal -b -1`) e `V-S4.md` (P-2: `boot_id` no início e no fim; intervalo do saude-log ≤ 2× o nominal).
- Branch `test/b29-gate-v4`, worktree `wt-gate-v4`. Ponta atual: a do commit deste prompt.

## 1. O que entregar

| Diretório | Arquivos |
|---|---|
| `runs/vm-s6-2026-11-04/` | `s6.sh` (invólucro do G-5b-A), `s6.test.sh`, `ROTEIRO-S6.md` |
| `runs/vm-s7-2026-11-04/` | `s7.sh` (invólucro do G-5b-B), `s7.test.sh`, `ROTEIRO-S7.md` |
| `tcc/` (fora do repo) | `gate-v4g.bundle` + `gate-v4g.bundle.sha256`, do `9c18ec8` (ponta do v4f) até a nova ponta. **Não** levar à VM |

Commits, nesta ordem e no estilo dos que já existem: `test: s6.test.sh … (vermelho: s6.sh ainda nao existe)`,
`feat: s6.sh …`, `docs: roteiro do S6 …`, e o mesmo trio para o S7. Commits locais. **Sem push. Sem VM.** Nenhuma edição em
`g5b.sh`, `g56-lib.sh`, `g-lib.sh`, `inject-partition.sh`, `portao51.sh`, `s5.sh`, `s5.test.sh`, `s4.sh` ou em qualquer
arquivo de `runs/vm-s1..s5-*` (o `s5.test.sh` tem de continuar 356/0).

`s6.sh` e `s7.sh` são autocontidos (um hash por bloco, como o `s5.sh`). O que for igual nos dois e no `s5.sh` é copiado
**verbatim** (seção 4.5), e o teste prova a igualdade.

## 2. Interface

```
bash runs/vm-s6-2026-11-04/s6.sh --boot-esperado <uuid> --bloco-anterior <dir vm-UTC do S5>
bash runs/vm-s7-2026-11-04/s7.sh --boot-esperado <uuid> --bloco-anterior <dir vm-UTC do S6>
        --dry-run                 imprime o plano (inclui `g5b.sh A|B --dry-run`) e sai 0 sem tocar em nada
        --hashes                  imprime a tabela de hashes embutida
        --avaliar-portao <log>    avalia um .log do portao51.sh
        --avaliar <dir vm-UTC>    refaz a avaliacao (tambem no host, Git Bash, sobre a copia)
```

Saídas: `0` VÁLIDO; `1` pré-condição; `2` argumentos; `3` portão pré; `4` `g5b.sh` com rc ≠ 0; `5` portão pós; `6`
avaliação INVÁLIDO; `130` Ctrl-C. `S6_TESTE=1`/`S7_TESTE=1` só nos testes, para trocar limites, tempos, arquivo do
`boot_id` e tabela de hashes (como `S5_TESTE`).

### 2.1 `boot_id` e kernel sem valor fixo (lição do `s4.sh`)

O `s4.sh` e o `s5.sh` têm `BOOT_ESPERADO` fixo no código; depois do reboot de 28/10 o `s4.sh` teve de ser editado, com
hash novo e nova validação. Aqui **nenhum** `boot_id` fica no código:

1. `--boot-esperado` é obrigatório e tem de ser um UUID (`^[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$`); ausente ou
   malformado: exit 2, nada lido além dos argumentos.
2. `--bloco-anterior` é obrigatório: o diretório `vm-<UTC>/` do bloco anterior (S5 para o S6; S6 para o S7). Dele o script
   lê o `boot_id` do fim (`07-boot-fim.txt`, linha `boot_id fim: <uuid> …`), o `uname -r` (`00-pre.log`) e, por nó, `id=` e
   `StartedAt=` (`00-pre.log`). Arquivo ausente ou ilegível: exit 1 ("bloco anterior sem brutos").
3. Pré-condição: `boot_id` atual = `--boot-esperado` = `boot_id fim` do bloco anterior. Qualquer diferença é **NÃO OK**
   ("reboot da VM entre blocos ou referência errada: parar e chamar o Senior"), nunca um aviso. **Q-11.**
4. Kernel atual = o do bloco anterior. IDs e `StartedAt` dos 3 contêineres = os do bloco anterior (um contêiner reiniciado
   entre blocos muda o histórico do cluster sem ninguém ver). **Q-13, D-4.**
5. Tudo isso vai para `parametros.txt` e `00-pre.log`. A avaliação confere `boot_id` igual no início e no fim
   (V-S4 P-2).

O `s6.test.sh`/`s7.test.sh` cobrem: argumento ausente, UUID malformado, bloco anterior sem `07-boot-fim.txt`, `boot_id`
diferente do esperado, esperado diferente do bloco anterior, kernel diferente, ID/StartedAt diferente, e reboot no meio
do bloco (o arquivo do `boot_id` muda antes do passo 6), que dá INVÁLIDO.

## 3. Passos do `s6.sh` (o `s7.sh` é igual, trocando `A` por `B` e os critérios da seção 5)

Diretório de saída `runs/vm-s6-2026-11-04/vm-<UTC>/`, console inteiro em `s6-console.log`, `rc.txt` com rc e fim de cada
passo. O rc de cada passo é lido **na mesma linha** do pipeline (`... | tee "$OUT/..."; RC=${PIPESTATUS[0]}`), inclusive
dentro de funções que usam `tee "$OUT/$1.log"` (R-3 da V-s5sh).

| Passo | Ação | Arquivos | Falha |
|---|---|---|---|
| 0 | Pré-condições, **só leitura**: `TCC_DIR=$HOME/tcc-gate` (exporta se vazio, aborta se diferente); script dentro de `$TCC_DIR/runs`; tmux; terminal (`[ -t 0 ]`, o GO do `g5b.sh` é interativo); `/var/lock/tcc-vm.lock` **OCUPADO** (o script roda dentro do `flock`); `/tmp/inject-partition-isn3.lock` **livre** (`flock -n` com `true`); `sudo -n`; seção 2.1; `HEAD` e "contém `902654b`"; árvore versionada limpa **exceto** a regra do `go.log` (abaixo); tabela de hashes (seção 4.4) com `sha256sum -c`; sha256 do próprio script; ambiente sem `LOCK_DIR`, `PODMAN`, `INJ`, `LOG`, `DMS`, `UNIT`, `G_TESTE`, `G5B_*`, `G1_*`, `REP`, `COLETA`, `SONDA_*`, `L0C_*`, `TCC_*` exceto `TCC_DIR`; `runs/gate-b29/g5b-A` (S6) ou `g5b-B` (S7) **inexistente**; no S7, `runs/gate-b29/g5b-A/resultado.txt` existe (o S6 rodou antes); 0 timers e 0 units `tcc-heal`; `podman ps -a`; IP, ID, `StartedAt` e última vista dos 3 nós; PID de isn3 e guarda de netns (diferente do host); `ss -tn` de isn3 na 7800 (IPv4/IPv6 só **registrados**: com IPv6 o split não acontece e o `g5b.sh` já falha sozinho); NIC da VM sem `prio`/`netem`/`clsact` | `00-pre.log`, `parametros.txt` | exit 1, nada tocado |
| 1 | `bash runs/gate-b29/g5b.sh A --dry-run`, gravado, e conferência dos parâmetros efetivos no texto: `duracao=120s`, `KILL em +70s`, `margem do dead-man=90s` (S6) ou `25s` (S7) | `01-g5b-dry-run.log` | exit 1 |
| 2 | Portão 5.1 pré (`portao51.sh 130`), com `.log`, `.csv` e avaliação automática **idêntica** à do `s5.sh` | `02-portao51-pre.{log,csv,avaliacao}` | exit 3: o bloco não roda |
| 3 | Coletor em segundo plano (3.1). Aviso impresso antes do prompt (3.2). **`g5b.sh A` neste terminal**; o GO é o do próprio `g5b.sh` (`exigir_go`), digitado pelo Gestor. O invólucro **não** digita nada | `03-coletor.log`, `04-g5b.log` | ver 3.3 |
| 4 | Depois do `g5b.sh`: cópia de `runs/gate-b29/g5b-A/` para `g5b-A-copia/` com `g5b-A.sha256`; cópia de `runs/gate-b29/go.log`; `journalctl -u <unit>.service` e `-u <unit>.timer` com `-o short-iso-precise` (o nome da unit sai de `unit=` em `unit-armada.txt`); journal do `sudo` (3.4); timers e units `tcc-heal`; `tc qdisc show dev eth0` dos 3 netns; NIC; linhas de vista e `DEGRADED_MODE` dos 3 nós desde o início do passo 3 | `g5b-A-copia/`, `g5b-A.sha256`, `go.log`, `05-journal-unit.txt`, `05-sudo-journal.txt`, `05-pos-g5b.log`, `05-vistas.log` | (registro) |
| 5 | Portão 5.1 pós, com fim ≥ fim do `g5b.sh` + 345 s (como no `s5.sh`) | `06-portao51-pos.{log,csv,avaliacao}` | exit 5 |
| 6 | `boot_id` do fim | `07-boot-fim.txt` | (entra na avaliação) |
| 7 | Avaliação automática (seção 5) e manifesto `MANIFEST.sha256` de todo o `vm-<UTC>/` (gerado por último, sem incluir a si mesmo) | `08-avaliacao.txt`, `MANIFEST.sha256` | exit 6 |

**Regra do `go.log` (Q-12).** O `exigir_go` acrescenta uma linha em `runs/gate-b29/go.log`, que é **versionado**. Depois do
S4 a VM já deve ter esse arquivo modificado (a cópia do S4, `runs/vm-s4-2026-10-28/vm-20261028T070600Z/go.log`, tem 6 linhas;
o arquivo versionado tem 5). A pré-condição "árvore limpa" fica assim: o único arquivo versionado que pode estar modificado
é `runs/gate-b29/go.log`, e só se `git show HEAD:runs/gate-b29/go.log` for **prefixo exato** do arquivo e cada linha a mais
começar com `go `. Qualquer outro arquivo modificado, ou uma linha antiga alterada, é NÃO OK. Testes: árvore limpa; só
`go.log` com linhas `go ` a mais (OK); linha antiga editada (NÃO OK); linha a mais sem `go ` (NÃO OK); outro arquivo
modificado (NÃO OK).

### 3.1 Coletor

Mesmo desenho do coletor do `s5.sh`, sem sonda Hot Rod e sem `ping` (o §5.7 não pede nenhum dos dois):

- espera a **linha nova** de GO do bloco em `runs/gate-b29/go.log` (contagem de linhas com `go G-5b-A` maior que a do passo
  0), por no máximo `ESPERA_GO_S=900` (o GO tem de vir em até 15 min do prompt, como a R-5 da V-s5sh); sem GO no prazo,
  registra e sai sem coletar;
- a partir do GO: saúde a cada 2 s com o `saude_loop` do `s5.sh` (3 portas e `ssh22`), para `janela/saude.csv`; e uma série
  de `sudo nsenter -t <pid isn3> -n tc qdisc show dev eth0` a cada 2 s para `janela/tc-isn3.csv` (`t_epoch_ns,clsact(0|1),
  linha`), **só leitura**;
- para quando o `g5b.sh` terminar (arquivo de parada criado pelo invólucro) ou, no máximo, `ESPERA_GO_S + 900` s depois de
  começar; o invólucro espera o coletor no máximo 60 s e então manda TERM;
- trap de TERM/INT que encerra os filhos; nada dele toca em `tc` ou no cluster.

### 3.2 Aviso antes do prompt do GO

O invólucro imprime, logo antes de chamar o `g5b.sh`:

- S6: "o dead-man está armado com 120 + 90 = 210 s e `AccuracySec=1s`; o `g5b.sh` faz o `--heal` manual sozinho; se ele
  abortar, o próprio trap faz o `--heal` e o dead-man continua armado como rede de segurança. Cura manual, em `~/tcc-gate`:
  `PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3`";
- S7: "o nó isn3 fica cortado até ~145 s depois de armar; **não** curar à mão: qualquer `--heal` manual invalida o bloco
  (§5.7). Se o dead-man não disparar até 145 + 10 + 30 = 185 s, o `g5b.sh` falha e o trap dele cura; só se o próprio
  `g5b.sh` travar use a cura manual acima, sabendo que o bloco fica registrado como falha do dead-man";
- nos dois: "digite GO no prompt do `g5b.sh` em até 15 min; depois disso responda qualquer outra coisa (aborta sem armar) e
  recomece".

### 3.3 Falha do `g5b.sh` (rc ≠ 0)

Sem GO, o `g5b.sh` sai antes de armar: o invólucro encerra o coletor e sai com 4, sem portão pós. Se o `g5b.sh` falhou
**depois** de armar, o trap dele já chamou o `--heal` e o dead-man segue armado. Aqui o invólucro faz o que a R-6 da V-s5sh
pediu e o `s5.sh` não fazia: grava `05-pos-falha.log` (timers, `tc qdisc` dos 3 netns, a instrução de cura manual) e
**espera** até 0 timers `tcc-heal` ou até `prazo do dead-man + 60 s` desde a armação (o que vier antes), registrando a
cada 5 s a presença de `clsact` em isn3; ao fim registra se a cura veio e de quem (journal da unit). Só então sai com 4.
**Q-13, D-5.**

### 3.4 Journal do `sudo` (detector de cura manual)

O §5.7 pede para o S7 que "nenhum comando de cura apareceu em `history`/consoles". O `history` não é confiável (só é
gravado quando o shell sai). Todo `tc` do injetor passa por `sudo nsenter -t <pid> -n tc …`, e o `sudo` do Ubuntu registra
cada comando no journal. O invólucro grava, da armação até o fim do passo 4:

```
sudo journalctl _COMM=sudo --since @<epoch do inicio do passo 3> -o verbose --no-pager
```

e extrai, para cada entrada com `COMMAND=` contendo `nsenter` e `tc qdisc del dev eth0 clsact`, o instante
(`__REALTIME_TIMESTAMP`, µs) e a origem (`_SYSTEMD_UNIT`: a unit do dead-man ou a sessão `session-*.scope`). O formato exato
do `-o verbose` é conferido contra um *fixture* do teste; se o Worker preferir `-o json` com `sed`, tudo bem, desde que não
dependa de `jq` (não é garantido na VM).

Uso nos critérios (**Q-13, D-1**):
- S6 (controle positivo do detector): entre `t_kill` e `t_desarmado`, **exatamente 1** `tc qdisc del … clsact` vindo da
  sessão (o `--heal` manual do `g5b.sh`) e **0** vindos da unit. Se o detector não vir o heal do S6, ele não serve para o
  S7, e o S6 sai INVÁLIDO com essa razão.
- S7: entre `t_kill` e o fim do passo 4, **0** vindos da sessão (FALHA se houver). As entradas vindas da unit do dead-man
  são esperadas (≥ 1, a primeira entre `t_kill` e `t_fire` + 5 s), mas ficam como `LEITURA HUMANA`: o S6 só serve de
  controle positivo para a sessão, e nada até aqui provou que o `sudo` chamado por root dentro de uma unit é registrado.

## 4. Detalhes de implementação

### 4.1 Nada de `tc` no invólucro

O invólucro só **lê** `tc` (coletor, passo 4, falha). Quem escreve é o injetor, chamado pelo `g5b.sh`. O teste prova isso:
nenhuma chamada a `tc` com `add`/`del`/`change`/`replace` sai do invólucro (o *shim* do `sudo` registra a origem).

### 4.2 Ctrl-C

Trap de INT: TERM ao coletor, registro em `rc.txt`, mensagem com a cura manual e o estado do dead-man, exit 130. O teste
manda SIGINT de verdade ao grupo (`set -m`, `kill -INT -<pgid>`) durante a janela e confere `rc.txt`, que o coletor parou e
que nenhum filho ficou vivo (R-2 da V-s5sh).

### 4.3 Tempos no teste

Os tempos curtos do teste não podem deixar o teste frágil sob carga (R-1 da V-s5sh: 341/15 em paralelo, 356/0 sozinho).
Use folgas que aguentem o teste rodando junto com outro processo pesado; meça e informe a duração. O `s6.test.sh` e o
`s7.test.sh` rodam **sozinhos**, um de cada vez.

### 4.4 Tabela de hashes (referência v4c, `902654b`)

| Arquivo | sha256 | Conferido na VM em |
|---|---|---|
| `runs/gate-b29/g5b.sh` | `6f50e3e36b2d93aba59e13a7765265935c83d5ccd20bc4c636531d7b1b260378` | S3 (`runs/vm-s3-2026-10-28/02-pos-l0c-leitura.log:9`) |
| `runs/gate-b29/g56-lib.sh` | `4f750fe3cf59f299585c12f66b3020973976e0e979474fde6354bf6998a18e2a` | S0 (relatório do gate v4) |
| `runs/gate-b29/g-lib.sh` | `af3436d5ede54f5f2047ba48620130a803b4bbd8929e2cbc242fbbe0ebb922dd` | S3 (`V-S3.md`, item 3) |
| `scripts/inject-partition.sh` | `59dd2beee5b003d85f96cae71866227d7bca1c87a9ceeab034180ce4254d94e1` | S3 (`04-s4-pre-hashes-e-cluster.log:1`) |
| `runs/vm-s2-2026-10-27/portao51.sh` | `2598b37d4969b1bd717ddbe514110fb6a0077e1332e38c1b76c5caea61802338` | S2, S3, S4 |

O Senior recalculou os cinco sobre os *blobs* da ponta (`git show HEAD:<arq> | sha256sum`). O teste T-s6-0 confere que a
tabela embutida é igual aos arquivos do repositório e que `git diff --stat 902654b HEAD` é vazio para os cinco.

### 4.5 Código copiado do `s5.sh`

Copiar **verbatim** de `runs/vm-s5-2026-10-28/s5.sh`: `avaliar_portao`, `stats`, `tcp_s`, `saude_loop` e o bloco do critério
"intervalo do saude-log ≤ 2× o nominal". O teste extrai cada função dos dois arquivos (do nome até a chave de fecho na
coluna 0) e exige `diff` vazio, exceto as mensagens que citam `s5.sh` ou `S5`, que podem trocar o número do bloco. Se uma
função precisar mudar de verdade, ela ganha nome novo; não se edita a cópia.

### 4.6 O que o teste usa como `g5b.sh`

A avaliação do invólucro lê os arquivos que o `g5b.sh` real escreve (`resultado.txt`, `cronologia.txt`, `estado.txt`,
`tempos.txt`, `unit-armada.txt`, `qdisc-*.txt`, `chamadas-injetor.txt`, `marcadores.txt`, `journal-unit.txt`,
`estado-systemd.txt`, `log-vista-*.txt`). Um *stub* inventado pode divergir do formato real. Por isso o *stub* de
`g5b.sh` do teste **reproduz uma saída gerada pelo `g5b.sh` real**: o teste roda o `g5b.sh` verdadeiro (modo A e modo B),
sobre os *shims* de `runs/gate-b29/gate-test-lib-v4.sh` e com `G5B_*` curtos, uma vez no início, guarda os diretórios
`g5b-A`/`g5b-B` resultantes como *fixture* e o *stub* os copia (variando só o que cada caso precisa variar). Se isso custar
mais de ~5 min por execução do teste, alternativa aceitável: gerar o *fixture* uma vez, versioná-lo
(`git add -f`, porque `runs/**/*.txt` está no `.gitignore`) e ter um teste que regenera e compara as linhas-chave. Informar a
escolha no relatório.

## 5. Avaliação automática (`08-avaliacao.txt`)

Mesmo formato do `s5.sh`: linhas `OK:`, `FALHA:`, `RESULTADO:` ou `LEITURA HUMANA:`, e a última `S6: VALIDO (...)` ou
`S6: INVALIDO (...)`. A avaliação **recalcula** a partir dos brutos copiados (`g5b-A-copia/` e os do invólucro); não confia
só nos `PASS` do `resultado.txt`. Cada linha cita a origem do critério.

### 5.1 S6, G-5b-A (§5.7, "Validade" e "O Senior verifica")

| # | Critério (FALHA se não valer) | Origem | Como |
|---|---|---|---|
| A1 | `boot_id` igual ao esperado no início e no fim | V-S4 P-2 | `00-pre.log`, `07-boot-fim.txt` |
| A2 | `g5b.sh` rc 0; `resultado.txt` sem `FAIL` e sem `ABORTADO` | §5.7 | `rc.txt`, cópia |
| A3 | split confirmado **antes** do KILL: `t_start < t_split_confirmado < t_kill` | §5.7 | `cronologia.txt` |
| A4 | KILL a partir de +70 s do `start` (`t_kill − t_start ≥ 70 s`) | `g5b.sh` (parâmetro do plano) | `cronologia.txt` |
| A5 | `rc_injetor=137`; marcadores com 1 `start` e 0 `end` | §5.7 | `estado.txt`, `marcadores.txt` |
| A6 | `clsact` presente antes do KILL (`estado.txt`) e depois do KILL (`qdisc-apos-kill.txt`); persistência ≥ 15 s (`t_persistencia_ok − t_kill ≥ 15 s`) sem MergeView no intervalo | §5.7 | cópia |
| A7 | `--heal` manual rc 0, uma vez só (`chamadas-injetor.txt`: 1 injeção e 1 `--heal`); `qdisc-pos-heal.txt` sem `clsact` | §5.7 | cópia |
| A8 | MergeView de 3 membros nos 3 logs depois do heal; `size=3 HEALTHY` x3 | §5.7 | `resultado.txt`, `log-vista-*.txt` |
| A9 | dead-man desarmado **só depois** de saudável (`t_saudavel ≤ t_desarmado`); armado durante (`unit-armada.txt` e `estado.txt` listam o timer); 0 timers e 0 units ao fim | §5.7 | cópia, `05-pos-g5b.log` |
| A10 | cronologia completa e em ordem: `t_start t_split_confirmado t_kill t_persistencia_ok t_heal t_mergeview t_saudavel t_desarmado` | §5.7 | `cronologia.txt` |
| A11 | portão 5.1 pós OK (size=3 HEALTHY, caches vazios, 3 netns limpos, sem filtros, 0 timers, sem mudança de vista em 5 min) | §5.1 | `06-portao51-pos.avaliacao` |
| A12 | intervalo máximo do saude-log da janela ≤ 2 × 2 s | V-S4 P-2 | `janela/saude.csv` |
| D-1 | detector do `sudo`: 1 `tc qdisc del … clsact` da sessão entre `t_kill` e `t_desarmado`, 0 da unit | **Q-13** (deriva do §5.7) | `05-sudo-journal.txt` |
| D-2 | série do coletor coerente: `clsact=1` em todas as amostras entre `t_start + 5 s` e `t_heal`, e `clsact=0` em todas a partir de `t_heal + 5 s` | **Q-13** (deriva do §5.7, "o corte persiste") | `janela/tc-isn3.csv` |
| D-3 | timer armado com `AccuracyUSec=1s` e prazo 210 s (`unit-armada.txt`) | **Q-13** (V-S0/V-S3: precisão do timer) | cópia |

`RESULTADO` (não invalida): `t_split_confirmado − t_start`; `t_mergeview − t_heal` e `t_saudavel − t_heal` (rotulados "dado
de segurança, **não** é t_merge", §5.7); `ISPN000524`/`ISPN100014`/`ISPN000638` nos logs (**Q-14**: recomendação = RESULTADO
com `LEITURA HUMANA`); disparo do dead-man depois do heal manual, se `t_desarmado` passar de 210 s da armação (**Q-15**:
recomendação = RESULTADO, válido desde que `qdisc-pos-heal.txt` sem `clsact` seja anterior ao disparo; o invólucro detecta
pelo `05-journal-unit.txt`).

`LEITURA HUMANA`: o Senior refaz os instantes de `ISPN000094`/`DEGRADED_MODE`/`ISPN000093` nos `log-completo-isn*.txt`;
a origem dos `sudo` que não forem `tc qdisc del`.

### 5.2 S7, G-5b-B

| # | Critério (FALHA se não valer) | Origem |
|---|---|---|
| B1 | A1, A2, A3, A4, A5, A6, A11 e A12, com a mesma regra | §5.7, V-S4 P-2 |
| B2 | **nenhum** `--heal` manual: `chamadas-injetor.txt` com 1 linha (a injeção) e 0 `--heal`; `resultado.txt` com `PASS nenhum --heal manual` | §5.7 |
| B3 | disparo entre `dur + margem` e `dur + margem + 10 s` desde a armação: em `tempos.txt`, `delta_desde_antes_de_armar_ms ≥ 145000` e `delta_desde_depois_de_armar_ms ≤ 155000` | §5.7 |
| B4 | journal da unit: `Heal: removendo`, `Deactivated successfully` ou `Succeeded`, nenhum `Failed with result`; unit não `failed` | §5.7 |
| B5 | corpo da unit igual ao de produção (`armar` de `g-lib.sh`): no `.service`, `ExecStart` com `bash scripts/inject-partition.sh --heal --minority isn3`, `Environment=PODMAN=sudo podman` e `WorkingDirectory=$TCC_DIR`; o `.timer` difere só em `AccuracySec=1s` e no prazo (145 s) | §5.7 ("corpo idêntico ao de produção") |
| B6 | `qdisc-no-disparo.txt` sem `clsact`; MergeView de 3 membros nos 3 logs depois do disparo; `HEALTHY` x3; 0 timers ao fim **sem** `systemctl stop` | §5.7 |
| B7 | cronologia completa e em ordem: `t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel` | §5.7 |
| D-1 | detector do `sudo`: 0 `tc qdisc del … clsact` da sessão entre `t_kill` e o fim (as da unit vão para `LEITURA HUMANA`, 3.4) | **Q-13** |
| D-2 | série do coletor: `clsact=1` de `t_start + 5 s` até `t_fire − 2 s`; `clsact=0` de `t_fire + 5 s` em diante | **Q-13** |
| D-3 | `AccuracyUSec=1s` e prazo 145 s em `unit-armada.txt` | **Q-13** |

`RESULTADO`: `t_mergeview − t_fire`, `t_saudavel − t_fire`, tempo total de corte; `ISPN000524`… (**Q-14**). `LEITURA HUMANA`:
diferença entre o início da unit no journal (`-o short-iso-precise`) e o `t_fire` medido por *polling* (espera-se ≤ 2 s); o
limite de que a unit do S7 tem `AccuracySec=1s` e a de produção não (o S7 prova o **corpo** do dead-man de produção, não a
precisão do timer de produção, que pode atrasar até 60 s, R-3 da V-S2).

## 6. Testes exigidos (TDD, vermelho primeiro)

`s6.test.sh` e `s7.test.sh`, no padrão do `s5.test.sh` (árvore falsa `$HOME/tcc-gate` com git próprio, *shims* de `sudo`,
`podman`, `nsenter`, `flock`, `uname`, `systemctl`, `journalctl`, `curl`, `ip`, `tc`, `hostname`, *stub* de `portao51.sh`
e de `g5b.sh` conforme 4.6). No mínimo:

| Caso | Prova |
|---|---|
| T-0 | `bash -n`; modo git `100755`; 0 CR; tabela de hashes = arquivos do repo e diff vazio desde `902654b`; funções copiadas iguais às do `s5.sh` (4.5); verificação estática "rc na mesma linha" cobrindo **todo** `tee "$OUT/` (R-3) |
| T-1 | argumentos (2.1): ausente, malformado, bloco anterior sem brutos; nada além de leitura |
| T-2 | caminho feliz: ordem pre → dry-run → portão pré → coletor + `g5b.sh` (GO só pelo stdin) → pós → portão pós → `boot_id` fim → avaliação VÁLIDO; todos os arquivos da seção 3 existem; `MANIFEST.sha256` confere (`sha256sum -c`) |
| T-3 | cada pré-condição que falha aborta **antes** do portão e do `g5b.sh`, com a mensagem certa (lock livre, lock do injetor ocupado, `g5b-A` existente, timer `tcc-heal`, variável herdada, hash diferente, netns do host, regra do `go.log` nos 5 casos, `boot_id`/kernel/ID/StartedAt) |
| T-4 | portão pré reprovado: exit 3, `g5b.sh` não chamado |
| T-5 | `g5b.sh` sem GO: exit 4, coletor encerrado, sem portão pós |
| T-6 | `g5b.sh` falha depois de armar: `05-pos-falha.log`, espera até 0 timers (com *shim* que zera os timers depois de N s), exit 4 |
| T-7 | cada critério da seção 5 violado isoladamente dá `FALHA` e `INVALIDO` (um caso por linha da tabela, inclusive D-1, D-2, D-3); cada `RESULTADO` presente não invalida |
| T-8 | reboot no meio do bloco: INVÁLIDO |
| T-9 | Ctrl-C real durante a janela (4.2) |
| T-10 | nenhum `tc` de escrita sai do invólucro (4.1) |
| T-11 | `--avaliar` sobre a cópia reproduz o mesmo `08-avaliacao.txt` (byte a byte, exceto a linha de data) |
| T-12 | `--dry-run` não chama `sudo`, `podman`, `tc`, `systemctl` nem `journalctl` |

Mutantes (relatar cada um como morto/sobrevivente, em cópia `git archive`, nunca no worktree): (M1) retirar a checagem do
split antes do KILL; (M2) aceitar `rc_injetor` ≠ 137; (M3) B3 com tolerância de 20 s; (M4) D-1 sem filtrar pela origem; (M5)
regra do `go.log` aceitando linha antiga editada; (M6) `boot_id` aceitando o do arquivo sem comparar com `--boot-esperado`;
(M7) o coletor sem limite de espera do GO; (M8) A9 sem a ordem `t_saudavel ≤ t_desarmado`; (M9) trap INT sem matar o coletor.
Um mutante sobrevivente exige ou um teste novo ou a justificativa de equivalência.

Suítes que têm de continuar como estão, rodadas uma por vez: `s5.test.sh` 356/0, `s4.test.sh` 216/0 e `g5b.test.sh`
(medir antes de começar e no fim; o relatório do gate v4 dá 128/0 no Git Bash, mas é anterior ao `72c5e5b`, então vale a
contagem medida agora).

## 7. Roteiros (`ROTEIRO-S6.md`, `ROTEIRO-S7.md`)

No formato do `ROTEIRO-S5.md`: o que o bloco prova e não prova (§5.7 e §5.8); "antes do GO, o Gestor precisa saber" (aviso
da 3.2, risco do nó cortado no S7, GO em até 15 min); comando exato (`sha256sum` esperado do script; `flock
/var/lock/tcc-vm.lock tmux new -s s6`; a invocação com `--boot-esperado` e `--bloco-anterior`, dizendo de onde tirar os dois);
tabela de passos, arquivos, rc e o que fazer em cada falha; a avaliação (seção 5, citando Q-11..Q-15 como pendentes);
duração estimada (medida no teste e calculada para a VM); como copiar os brutos para o host (`scp -r -o
HostKeyAlias=172.18.251.140 -i ~/.ssh/id_ed25519_tccf3 server1@<ip>:...`) e conferir com `sha256sum -c MANIFEST.sha256` no
Git Bash; e que o script só chega à VM pelo *bundle* v4g, depois da validação do Senior e da autorização do Gestor.

## 8. Restrições

- Nada na VM, nenhum `ssh`, nenhum push, nenhum `git push` nem `git bundle` enviado a lugar nenhum.
- Nenhum TLC, `mvn test` nem suíte pesada em paralelo com os testes destes scripts.
- Não editar os executores (`g5b.sh` etc.) nem os blocos anteriores. Se achar defeito neles, **parar e relatar**; não
  contornar no invólucro sem decisão.
- Não inventar critério de aceite. O que não estiver na seção 5, no §5.7 ou numa Q-nn vira pergunta no relatório.

## 9. Relatório de volta (no chat, não em arquivo)

Commits (SHA e mensagem); contagens dos testes (PASS/FAIL e duração, cada suíte sozinha); mutantes; sha256 de `s6.sh` e
`s7.sh`; sha256 e intervalo do `gate-v4g.bundle`; desvios deste prompt e por quê; dúvidas para o Gestor.
