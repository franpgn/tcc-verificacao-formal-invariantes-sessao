# Roteiro do S6 (G-5b-A: cura manual depois de um split real), 2026-11-04

Preparado pelo Worker. Não foi executado na VM. Referências: `docs/prompt-worker-s6-s7-g5b-2026-11-04.md` (o pedido do Senior), `decisoes-senior-2026-10-16.md` §5.1, §5.2, §5.7 e §5.8 (na `wt-plano-ctrl`), `runs/vm-s5-2026-10-28/V-s5sh.md` (R-1, R-2, R-3 e R-6, que viraram requisito aqui), `runs/vm-s4-2026-10-28/V-S4.md` (P-2) e `docs/roteiro-vm-2026-11-04.md`, seção 9 (perguntas Q-11 a Q-16, pendentes do Gestor). O modelo é o `runs/vm-s5-2026-10-28/ROTEIRO-S5.md`.

## O que o S6 prova e o que não prova

O G-5 de 2026-10-16 usou uma janela de 40 s, menor que a detecção do JGroups (~40 a 46 s), e desarmou o dead-man em todas as fases. O S6 cobre a primeira lacuna da §5.7: **a cura (`--heal`) depois de um split real**. O `runs/gate-b29/g5b.sh A` (v4c, `902654b`, não editado) arma o dead-man com 120 + 90 s, injeta `--minority isn3 --duration 120`, espera o split confirmado nos logs, dá `kill -KILL` no injetor a partir de +70 s, confere que o corte persiste por 15 s, faz o `--heal` manual e só desarma depois de `HEALTHY` x3.

O `s6.sh` não injeta, não cura, não desarma e não digita o GO. Ele confere as pré-condições, roda o portão 5.1 antes e depois, coleta em volta (saúde a cada 2 s, `tc qdisc show` de isn3 a cada 2 s, journal da unit e do `sudo`, vistas dos 3 nós) e avalia o bloco a partir dos brutos.

Não prova (§5.7 e §5.8): o tempo de fusão (o `t_heal -> MergeView` é **dado de segurança, não é t_merge**); o dead-man disparando (isso é o S7); a queda da própria VM ou a perda da sessão `tmux`; nada sobre invariantes com carga (isso é a T-F). Estas partições **não entram no conjunto do G-4**.

## Antes do GO, o Gestor precisa saber

- **Dead-man.** O `g5b.sh` arma com 120 + 90 = **210 s** e `AccuracySec=1s`. Ele faz o `--heal` manual sozinho. Se abortar, o trap dele faz o `--heal` e o dead-man continua armado como rede de segurança. Cura manual, em `~/tcc-gate`: `PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3`. O `s6.sh` imprime este aviso logo antes do prompt.
- **O GO é digitado pelo Gestor** no prompt do próprio `g5b.sh` (`exigir_go`), que exige terminal. O `s6.sh` não digita nada.
- **GO em até 15 min** depois do prompt. O coletor espera a linha nova de GO no `go.log` por no máximo 900 s (R-5 da V-s5sh). Passado isso, responda qualquer outra coisa (o `g5b.sh` aborta sem armar) e recomece.
- **Presença.** Do GO até o fim do `g5b.sh` (cerca de 2,5 min), e até a cura se o `g5b.sh` falhar (até 210 + 60 s depois de armar, ver "Falha do `g5b.sh`").
- **Host.** Nada de TLC, `mvn` ou carga pesada no host do início ao fim do portão pós (§5.1). O `s6.sh` registra o `boot_id` no início e no fim, e um intervalo do saúde-log maior que 2 × 2 s invalida o bloco.
- **Ctrl-C.** Durante o `g5b.sh`, um Ctrl-C chega também ao `g5b.sh`: o trap dele faz o `--heal` e o dead-man segue armado. O `tee` do `04-g5b.log` ignora SIGINT de propósito, para o `g5b.sh` ter onde escrever enquanto cura (sem isso ele morreria por SIGPIPE antes do `--heal`). O `s6.sh` encerra o coletor, grava `interrompido` em `rc.txt`, imprime o estado do dead-man e a cura manual, e sai com 130.

## Como rodar

```bash
cd ~/tcc-gate
sha256sum runs/vm-s6-2026-11-04/s6.sh   # esperado c01d8c79888e6afa20a7766aac355f7aec12b3c53bddaf14cb2b5877aa0e2ef8
flock /var/lock/tcc-vm.lock tmux new -s s6
# dentro do tmux:
bash runs/vm-s6-2026-11-04/s6.sh --boot-esperado <uuid> --bloco-anterior runs/vm-s5-2026-10-28/vm-<UTC>
# ao fim: exit (libera o lock)
```

De onde tirar os dois argumentos:
- `--bloco-anterior`: o diretório `vm-<UTC>/` do S5 que o Senior validou na V-S5, dentro de `~/tcc-gate/runs/vm-s5-2026-10-28/`. O `s6.sh` lê dele `07-boot-fim.txt` (linha `boot_id fim: <uuid> ...`), `00-pre.log` (`uname -r:` e, por nó, `id=` e `StartedAt=`) e, só para registro, a última linha de `08-avaliacao.txt`.
- `--boot-esperado`: o `<uuid>` da linha `boot_id fim:` desse mesmo `07-boot-fim.txt`, conferido pelo Gestor contra `cat /proc/sys/kernel/random/boot_id` antes de começar. Se os dois forem diferentes, houve reboot depois do S5: **não rodar** e chamar o Senior.

Nenhum `boot_id` fica no código (lição do `s4.sh`, que teve de ser editado depois do reboot de 28/10).

`--dry-run` imprime o plano (inclusive o `g5b.sh A --dry-run`) e sai 0 sem chamar `sudo`, `podman`, `tc`, `systemctl` nem `journalctl`.

## Pré-condição sobre o `go.log` (Q-12, pendente)

O `exigir_go` acrescenta uma linha em `runs/gate-b29/go.log`, que é versionado. Na VM ele já está modificado desde o S4 (6 linhas; o Gestor conferiu o sha256 `3de1fa71…20d8` em 04/11, igual ao da cópia do S4 no host, `runs/vm-s4-2026-10-28/vm-20261028T070600Z/go.log`). Se o Gestor restaurar o arquivo antes do S5 (Q-01), ele volta a 5 linhas. Nos dois casos o S6 passa: a regra aceita como única modificação versionada o `go.log` com o conteúdo do `HEAD` como prefixo exato e cada linha a mais começando com `go `. Qualquer outro arquivo modificado, uma linha antiga alterada, uma linha a mais sem `go ` ou o `go.log` no índice (`M ` em vez de ` M`) é NÃO OK.

## O que o `s6.sh` faz

| Passo | Ação | Arquivo em `vm-<UTC>/` | rc em `rc.txt` | Se falhar |
|---|---|---|---|---|
| — | Argumentos: `--boot-esperado` (UUID minúsculo) e `--bloco-anterior` com `07-boot-fim.txt` e `00-pre.log` legíveis | nada (nem o diretório é criado) | — | rc 2 (argumento) ou rc 1 ("bloco anterior sem brutos") |
| 0 | Pré-condições, só leitura: `TCC_DIR=$HOME/tcc-gate`; script dentro de `$TCC_DIR/runs`; tmux; terminal; `/var/lock/tcc-vm.lock` OCUPADO; `/tmp/inject-partition-isn3.lock` livre; `sudo -n`; `boot_id` atual = `--boot-esperado` = `boot_id fim` do S5 (Q-11); kernel, IDs e `StartedAt` dos 3 nós = os do S5 (Q-13, D-4); HEAD contém `902654b`; regra do `go.log` (Q-12); tabela de hashes; sha256 do `s6.sh`; ambiente sem `LOCK_DIR`/`PODMAN`/`INJ`/`LOG`/`DMS`/`UNIT`/`G_TESTE`/`G5B_*`/`G1_*`/`REP`/`COLETA`/`SONDA_*`/`L0C_*`/`TCC_*` (exceto `TCC_DIR`); `runs/gate-b29/g5b-A` inexistente; 0 timers e 0 units `tcc-heal`; `podman ps -a`; IP, ID, `StartedAt` e última vista dos 3 nós; PID de isn3 e guarda de netns; `ss -tn` de isn3 na 7800 (IPv4/IPv6 só registrados); NIC da VM sem `prio`/`netem`/`clsact` | `00-pre.log`, `parametros.txt` | `pre` | rc 1, nada tocado |
| 1 | `bash runs/gate-b29/g5b.sh A --dry-run` e conferência dos parâmetros efetivos no texto: `duracao=120s`, `KILL em +70s`, `margem do dead-man=90s`, `120+90 = 210 s, AccuracySec=1s` | `01-g5b-dry-run.log` | `g5b-dry-run` | rc 1 |
| 2 | Portão 5.1 pré (`portao51.sh 130`), com a mesma avaliação automática do `s5.sh` | `02-portao51-pre.{log,csv,avaliacao}` | `02-portao51-pre`, `02-portao51-pre-avaliacao` | rc 3: **S6 não roda** |
| 3 | Coletor em segundo plano; aviso; **`g5b.sh A` neste terminal, com o GO do Gestor** | `03-coletor.log`, `04-g5b.log`, `janela/saude.csv`, `janela/tc-isn3.csv` | `g5b`, `coletor` | rc 4: ver abaixo |
| 3a | Coletor: espera a linha nova `go G-5b-A` no `go.log` (até 900 s; sem GO, registra e sai sem coletar). A partir do GO: `/health` nas 3 portas e conexão ao `sshd` a cada 2 s (`saude_loop` do `s5.sh`) e `sudo nsenter -t <pid isn3> -n tc qdisc show dev eth0` a cada 2 s (`t_epoch_ns,clsact(0/1),linha`), só leitura. Para quando o `g5b.sh` termina (o invólucro espera no máximo 60 s e então manda TERM) ou em 900 + 900 s | `janela/` | `coletor` | (registro) |
| 4 | Cópia de `runs/gate-b29/g5b-A/` com sha256; cópia do `go.log`; `journalctl -u <unit>.service -u <unit>.timer -o short-iso-precise`; journal do `sudo` desde o início do passo 3 (`-o json`); timers e units; `tc qdisc show dev eth0` dos 3 netns; NIC; vistas e `DEGRADED_MODE` dos 3 nós desde o início do passo 3 | `g5b-A-copia/`, `g5b-A.sha256`, `go.log`, `05-journal-unit.txt`, `05-sudo-journal.txt`, `05-pos-g5b.log`, `05-vistas.log` | `pos-g5b` | (registro) |
| 5 | Portão 5.1 pós, com fim ≥ fim do `g5b.sh` + 345 s | `06-portao51-pos.{log,csv,avaliacao}` | `06-portao51-pos`, `06-portao51-pos-avaliacao` | rc 5 |
| 6 | `boot_id` do fim | `07-boot-fim.txt` | `boot-fim` | (entra na avaliação) |
| 7 | Avaliação automática (abaixo) e, ao sair, `MANIFEST.sha256` de todo o `vm-<UTC>/` (inclusive o console; sem ele mesmo) | `08-avaliacao.txt`, `MANIFEST.sha256` | `avaliacao` | rc 6: INVÁLIDO |

O console inteiro vai para `s6-console.log`. O rc de cada passo é lido na mesma linha do pipeline (`... | tee "$OUT/..."; RC=${PIPESTATUS[0]}`), inclusive dentro de `portao()` (R-3 da V-s5sh); o teste confere isso em **todo** `tee "$OUT/`. O invólucro só lê `tc`; quem escreve é o injetor, chamado pelo `g5b.sh`.

**Falha do `g5b.sh` (rc 4).** Sem GO, o `g5b.sh` sai antes de armar: o coletor é encerrado sem coletar e o `s6.sh` sai com 4, sem portão pós. Se o `g5b.sh` falhar **depois** de armar, o trap dele já chamou o `--heal` e o dead-man segue armado. O `s6.sh` grava `05-pos-falha.log` (timers, `tc qdisc` dos 3 netns, instrução de cura manual), copia o `g5b-A/` e **espera** até 0 timers `tcc-heal` ou até 210 + 60 s desde a armação, o que vier antes, registrando a cada 5 s a presença de `clsact` em isn3. Ao fim registra de quem veio a cura (dead-man, pelo journal da unit, ou trap do `g5b.sh`, pelo `abort-heal.log`). Só então sai com 4 (Q-13, D-5; R-6 da V-s5sh). Se o prazo esgotar com timer armado, a linha diz `use a cura manual`.

## Avaliação automática (`08-avaliacao.txt`)

Linhas `OK:`, `FALHA:`, `RESULTADO:` ou `LEITURA HUMANA:`; a última é `S6: VALIDO (...)` ou `S6: INVALIDO (...)`. A avaliação recalcula a partir dos brutos copiados (`g5b-A-copia/` e os do invólucro), não só dos `PASS` do `resultado.txt`, e cada linha cita a origem. Pode ser refeita no host, no Git Bash, sobre a cópia: `bash runs/vm-s6-2026-11-04/s6.sh --avaliar runs/vm-s6-2026-11-04/vm-<UTC>`; o resultado é idêntico ao `08-avaliacao.txt` exceto a linha `== avaliacao do S6 <data> sobre <dir>`.

Invalidam o bloco (`FALHA`):

| # | Critério | Origem |
|---|---|---|
| A1 | `boot_id` igual ao esperado no início e no fim | V-S4 P-2 |
| A2 | `g5b rc=0` em `rc.txt`; `resultado.txt` sem `FAIL` e sem `ABORTADO` | §5.7 |
| A3 | `t_start < t_split_confirmado < t_kill` (`cronologia.txt`) | §5.7 |
| A4 | `t_kill − t_start ≥ 70 s` | parâmetro do plano no `g5b.sh` |
| A5 | `rc_injetor=137`; marcadores com 1 `start` e 0 `end` | §5.7 |
| A6 | `clsact` antes do KILL (`estado.txt`) e depois (`qdisc-apos-kill.txt`); `t_persistencia_ok − t_kill ≥ 15 s`; amostras de `persistencia.txt` dentro do intervalo; `PASS corte PERSISTIU`; nenhum `MergeView` em `05-vistas.log` com instante (do próprio log do Infinispan, UTC) entre `t_kill` e `t_persistencia_ok`, e nenhum com instante ilegível | §5.7 |
| A7 | `chamadas-injetor.txt` com 2 linhas: 1 injeção e 1 `--heal`; `PASS --heal rc=0`; `qdisc-pos-heal.txt` sem `clsact` | §5.7 |
| A8 | `MergeView` de 3 membros em `log-vista-isn{1,2,3}.txt` e, em `05-vistas.log`, para cada nó, com instante ≥ `t_heal`; `saude-depois.txt` com 3 portas `HEALTHY`/3; `size=3 HEALTHY apos` | §5.7 |
| A9 | `t_saudavel ≤ t_desarmado`; o timer da unit listado em `unit-armada.txt` e em `estado.txt` (armado durante o corte); 0 timers e 0 units em `05-pos-g5b.log` | §5.7 |
| A10 | `t_start t_split_confirmado t_kill t_persistencia_ok t_heal t_mergeview t_saudavel t_desarmado` presentes e em ordem | §5.7 |
| A11 | portão 5.1 pós OK | §5.1 |
| A12 | intervalo máximo do saúde-log da janela ≤ 2 × 2 s (o corpo do critério é o do `s5.sh`, sem a base, que o S6 não tem) | V-S4 P-2 |
| D-1 | journal do `sudo` (`-o json`): entre `t_kill` e `t_desarmado`, exatamente 1 `nsenter … tc qdisc del dev eth0 clsact` vindo da sessão e 0 vindos da unit do dead-man. Origem = `_SYSTEMD_UNIT`: a unit do dead-man ou qualquer outra (conta como sessão). Se o detector não vir o `--heal` manual, ele não serve de controle para o S7 | **Q-13** (deriva do §5.7) |
| D-2 | `janela/tc-isn3.csv`: `clsact=1` em todas as amostras de `t_start + 5 s` a `t_heal` e `clsact=0` em todas a partir de `t_heal + 5 s`, com pelo menos 1 amostra em cada intervalo | **Q-13** |
| D-3 | `unit-armada.txt`: `AccuracyUSec=1s`, `prazo_s=210` e `OnActiveSec` do `.timer` = 210 s (aceita o formato do systemd, por exemplo `3min 30s`) | **Q-13** (V-S0/V-S3) |

**Limite conhecido do A6 (pergunta ao Gestor no relatório do Worker).** O `g5b.sh` (não editado) mede a persistência em segundos inteiros: `T_P0=$(date +%s)` e `while [ $(( $(date +%s) - T_P0 )) -lt 15 ]`, com uma amostra a cada ~2 s. O laço pode sair quando o tempo real desde `T_P0` ainda é ~14,3 s (quando a fração de segundo de `T_P0` passa de ~0,65), e o `t_persistencia_ok` só é carimbado depois de três `podman logs`. Por isso `t_persistencia_ok − t_kill` pode sair entre ~14,3 e ~15 s numa execução correta, e o A6 do prompt (≥ 15 s) dá FALHA. O `s6.sh` segue o prompt (≥ 15 s, lado conservador). Se o A6 cair só por isso (todas as outras condições do A6 OK e `t_persistencia_ok − t_kill ≥ 14 s`), o Senior reclassifica na V-S6A ou o Gestor decide outra regra antes do S6. No teste o mesmo efeito aparece com 3 s e o limite do teste é 2 s.

Não invalidam (`RESULTADO`): `t_split_confirmado − t_start`; `t_mergeview − t_heal` e `t_saudavel − t_heal` (rotulados "dado de segurança, NÃO é t_merge"); `ISPN000524`/`ISPN100014`/`ISPN000638` nos `log-completo-isn*.txt` (**Q-14**, com leitura humana); as linhas `DEGRADED_MODE`; o disparo do dead-man depois do `--heal` manual e antes do desarme (**Q-15**): vale desde que o `Started <unit>.service` do `05-journal-unit.txt` seja posterior a `t_heal` e `qdisc-pos-heal.txt` esteja sem `clsact`; o `tc qdisc del` da unit depois de `t_heal` não conta no D-1. Disparo antes do `--heal` manual é FALHA.

Ficam para leitura humana (`LEITURA HUMANA`): os instantes de `ISPN000094`/`DEGRADED_MODE`/`ISPN000093` nos `log-completo-isn*.txt`; a origem dos outros comandos `tc` de escrita no journal do `sudo` (injeção, pré-limpeza e sonda do injetor, todos antes de `t_start`); a latência do REST de isn3 na janela.

Q-11 a Q-15 estão **pendentes do Gestor** (`docs/roteiro-vm-2026-11-04.md`, seção 9). O código implementa a recomendação do Senior em cada uma, com a linha marcada `# Q-nn (pendente do Gestor)`.

## Duração estimada

Medida no teste (Git Bash, shims, `g5b.sh` reencenado com janela de 8 s): o caminho completo leva cerca de 30 s; a suíte inteira deu 588 PASS, 0 FAIL em 753 s (~12,5 min; sem outra suíte minha em paralelo, mas com outro Worker usando o host).

Na VM, calculada: pré-condições e dry-run em segundos; portão pré 130 s mais as leituras finais, então **o prompt do GO aparece por volta de 2,5 min**. Depois do GO: armação e injeção em ~2 s; split confirmado em ~40 a 46 s; KILL em +70 s; persistência até ~+86 s; `--heal`; fusão da ordem de ~46 s (`MergeView` por volta de +130 s); `HEALTHY` x3, desarme, preflight e logs, **o `g5b.sh` termina ~2,5 min depois do GO**. O portão pós vai até o fim do `g5b.sh` + 345 s (~6 min). Total: **cerca de 11 a 12 min**, mais o tempo até o GO.

## Teste

`bash runs/vm-s6-2026-11-04/s6.test.sh`, sozinho (R-1 da V-s5sh). O teste roda primeiro o `runs/gate-b29/g5b.sh` **real** (modo A) sobre os shims de `gate-test-lib-v4.sh` com `G5B_*` curtos (~20 s) e usa o `g5b-A/` resultante como fixture; o stub de `g5b.sh` reencena a cronologia desse fixture em tempo real e o copia com os instantes deslocados (prompt, seção 4.6, primeira opção; nada versionado). Cobre T-0 a T-12 do prompt, inclusive Ctrl-C real ao grupo (`set -m`, `kill -INT`), a espera do dead-man depois de falha e cada critério da avaliação violado isoladamente. Não prova podman, tc, systemd, journald, Infinispan nem o `g5b.sh` na VM (`g5b.test.sh` cobre o `g5b.sh` com shims).

## Como copiar os brutos para o host

```bash
# no host, Git Bash
scp -r -o HostKeyAlias=172.18.251.140 -i ~/.ssh/id_ed25519_tccf3 server1@<ip>:tcc-gate/runs/vm-s6-2026-11-04/vm-<UTC> runs/vm-s6-2026-11-04/
cd runs/vm-s6-2026-11-04/vm-<UTC> && sha256sum -c MANIFEST.sha256      # todas as linhas OK
sha256sum -c g5b-A.sha256                                              # a copia do g5b-A
cd ../../.. && bash runs/vm-s6-2026-11-04/s6.sh --avaliar runs/vm-s6-2026-11-04/vm-<UTC>
```

`runs/vm-s*/**` é `-text` no `.gitattributes`, então os bytes LF da VM ficam como estão no checkout e o `sha256sum -c` continua valendo.

## Como o `s6.sh` chega à VM

Só pelo bundle **v4g** (do `9c18ec8`, ponta do v4f, até a ponta desta branch), conferido por sha256, **depois** da validação do Senior (V-s6s7sh) e da autorização do Gestor (Q-02). Antes do S6, na VM: `sha256sum runs/vm-s6-2026-11-04/s6.sh` deve dar `c01d8c79888e6afa20a7766aac355f7aec12b3c53bddaf14cb2b5877aa0e2ef8`.
