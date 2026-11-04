# Roteiro do S7 (G-5b-B: o dead-man disparando sozinho), 2026-11-04

Preparado pelo Worker. Não foi executado na VM. Referências: `docs/prompt-worker-s6-s7-g5b-2026-11-04.md`, `decisoes-senior-2026-10-16.md` §5.1, §5.2, §5.7 e §5.8 (na `wt-plano-ctrl`), `runs/vm-s5-2026-10-28/V-s5sh.md` (R-1, R-2, R-3, R-6), `runs/vm-s4-2026-10-28/V-S4.md` (P-2), `docs/roteiro-vm-2026-11-04.md`, seção 9 (Q-11 a Q-16, pendentes do Gestor), e o `runs/vm-s6-2026-11-04/ROTEIRO-S6.md`, do qual este difere só no que é próprio do modo B.

## O que o S7 prova e o que não prova

Cobre a segunda lacuna da §5.7: **o dead-man disparando de fato, pelo caminho que toca `tc`**. O `runs/gate-b29/g5b.sh B` (v4c, `902654b`, não editado) arma o dead-man pela mesma função `armar` (corpo de produção, margem curta: 120 + 25 = **145 s**, `AccuracySec=1s`), injeta `--minority isn3 --duration 120`, espera o split confirmado, dá `kill -KILL` no injetor a partir de +70 s, confere a persistência por 15 s e então **não cura e não desarma**: espera o dead-man, que tem de disparar entre 145 e 155 s depois de armar.

O `s7.sh` não injeta, não cura, não desarma e não digita o GO.

Não prova (§5.7 e §5.8): a **precisão do timer de produção**. A unit do S7 tem `AccuracySec=1s` e a de produção não; o S7 prova o **corpo** do dead-man de produção (o que ele executa), não quando o timer de produção dispara, que pode atrasar até 60 s (R-3 da V-S2). Também não prova o tempo de fusão (dado de segurança, não é t_merge), a queda da VM nem a perda da sessão `tmux`. Estas partições não entram no conjunto do G-4.

## Antes do GO, o Gestor precisa saber

- **O nó isn3 fica cortado até ~145 s depois de armar.** **Não curar à mão**: qualquer `--heal` manual invalida o bloco (§5.7), e o detector do journal do `sudo` (D-1) vai registrá-lo.
- **Se o dead-man não disparar** até 145 + 10 + 30 = **185 s** depois de armar, o `g5b.sh` falha e o trap dele cura (o bloco fica como falha do dead-man). Só se o próprio `g5b.sh` travar use a cura manual, em `~/tcc-gate`: `PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3`, sabendo que o bloco fica registrado como falha do dead-man (**Q-16**, pendente). O `s7.sh` imprime este aviso logo antes do prompt.
- **O GO é digitado pelo Gestor** no prompt do `g5b.sh`, em **até 15 min**; depois disso responda qualquer outra coisa (aborta sem armar) e recomece.
- **O S6 tem de ter rodado antes** (§5.7, "A antes de B"): o `s7.sh` exige `runs/gate-b29/g5b-A/resultado.txt` e um portão 5.1 limpo entre os dois (o portão pré do S7).
- **Presença** do GO até o fim do `g5b.sh` (cerca de 3,5 min).
- **Host** sem TLC, `mvn` ou carga pesada do início ao fim do portão pós.
- **Ctrl-C** chega também ao `g5b.sh`, cujo trap faz o `--heal` (o que, no modo B, invalida o bloco). O `s7.sh` encerra o coletor, grava `interrompido` e sai com 130.

## Depois de um Ctrl-C: o que conferir (R-3 da V-s6s7sh)

No tmux o SIGINT vai a todo o grupo de primeiro plano, inclusive ao `tee` do console. O trap do `g5b.sh` tenta o `--heal`, mas uma escrita dele em stderr depois que os `tee` morreram pode matá-lo por SIGPIPE antes da cura, e o trap do invólucro pode morrer do mesmo jeito antes de gravar `interrompido`. A rede de segurança é o dead-man. Por isso, depois de qualquer Ctrl-C, ainda dentro do tmux e em `~/tcc-gate`:

1. `systemctl list-timers --all --no-legend | grep tcc-heal`: se houver timer, o dead-man ainda está armado e vai curar no prazo (S6: 210 s, S7: 145 s desde a armação); esperar ou fazer a cura manual.
2. `sudo nsenter -t $(sudo podman inspect -f '{{.State.Pid}}' isn3) -n tc qdisc show dev eth0`: só `noqueue`, sem `clsact`. Com `clsact` e sem timer: cura manual imediata (`PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3`).
3. `tail rc.txt` do `vm-<UTC>/`: deve terminar em `interrompido`. Sem essa linha, o trap do invólucro não chegou ao fim; anotar e seguir os itens 1, 2 e 4.
4. Processos órfãos: `ps -ef | grep -E 'g5b.sh|inject-partition|saude|curl' | grep -v grep` não deve mostrar nada depois de alguns segundos (o coletor é encerrado antes de qualquer escrita do trap). Se sobrar, `kill` no PID e anotar.
5. O bloco interrompido não tem `MANIFEST.sha256`; copiar os brutos assim mesmo e relatar ao Senior antes de qualquer nova tentativa, que pede portão 5.1 limpo e novo GO.

## Como rodar

```bash
cd ~/tcc-gate
sha256sum runs/vm-s7-2026-11-04/s7.sh   # esperado ac67a6fa0a2ee1d08c3b82c8b79a1bb3f0e40847c8c44c7181bb50f95fd15863
flock /var/lock/tcc-vm.lock tmux new -s s7
# dentro do tmux:
bash runs/vm-s7-2026-11-04/s7.sh --boot-esperado <uuid> --bloco-anterior runs/vm-s6-2026-11-04/vm-<UTC>
# ao fim: exit (libera o lock)
```

`--bloco-anterior` é o diretório `vm-<UTC>/` do **S6** validado pelo Senior (V-S6A); `--boot-esperado` é o `<uuid>` da linha `boot_id fim:` do `07-boot-fim.txt` desse diretório, conferido contra `cat /proc/sys/kernel/random/boot_id`. Diferença entre os dois: houve reboot depois do S6, **não rodar** e chamar o Senior (Q-11).

## Pré-condição sobre o `go.log` (Q-12, pendente)

Depois do S6, o `runs/gate-b29/go.log` versionado tem pelo menos a linha `go G-5b-A ...` a mais (e a do S4, se não foi restaurada). A regra é a mesma do S6: o `HEAD` tem de ser prefixo exato e cada linha a mais tem de começar com `go `.

## O que o `s7.sh` faz

Os passos, arquivos e rcs são os do S6 (ver `ROTEIRO-S6.md`), com estas diferenças:

| Passo | Diferença no S7 |
|---|---|
| 0 | `runs/gate-b29/g5b-B` inexistente **e** `runs/gate-b29/g5b-A/resultado.txt` existente; bloco anterior = S6 |
| 1 | `g5b.sh B --dry-run`: `duracao=120s`, `KILL em +70s`, `margem do dead-man=25s`, `120+25 = 145 s, AccuracySec=1s` e `Disparo entre 120+25=145 s e +10 s da armacao` |
| 3 | `g5b.sh B` neste terminal; o aviso da seção anterior |
| 4 | a cópia é `g5b-B-copia/` com `g5b-B.sha256` |
| falha | `05-pos-falha.log` e a espera até 0 timers ou 145 + 60 s desde a armação (Q-13, D-5) |

Arquivos em `vm-<UTC>/`: `00-pre.log`, `parametros.txt`, `01-g5b-dry-run.log`, `02-portao51-pre.{log,csv,avaliacao}`, `03-coletor.log`, `04-g5b.log`, `janela/saude.csv`, `janela/tc-isn3.csv`, `g5b-B-copia/`, `g5b-B.sha256`, `go.log`, `05-journal-unit.txt`, `05-sudo-journal.txt`, `05-pos-g5b.log`, `05-vistas.log`, `06-portao51-pos.{log,csv,avaliacao}`, `07-boot-fim.txt`, `08-avaliacao.txt`, `s7-console.log`, `rc.txt`, `MANIFEST.sha256` (e `05-pos-falha.log` se o `g5b.sh` falhar). Saídas: 0 VÁLIDO; 1 pré-condição; 2 argumentos; 3 portão pré; 4 `g5b.sh` rc ≠ 0; 5 portão pós; 6 avaliação INVÁLIDO; 130 Ctrl-C.

## Avaliação automática (`08-avaliacao.txt`)

Mesmo formato do S6; a última linha é `S7: VALIDO (...)` ou `S7: INVALIDO (...)`. Refazer no host: `bash runs/vm-s7-2026-11-04/s7.sh --avaliar runs/vm-s7-2026-11-04/vm-<UTC>`.

Invalidam o bloco (`FALHA`):

| # | Critério | Origem |
|---|---|---|
| B1 | A1, A2, A3, A4, A5, A6, A11 e A12 do S6, com a mesma regra (linhas `B1/A1` ... `B1/A11` e o intervalo do saúde-log) | §5.7, V-S4 P-2 |
| B2 | `chamadas-injetor.txt` com 1 linha (a injeção) e 0 `--heal`; `PASS nenhum --heal manual` | §5.7 |
| B3 | `delta_desde_antes_de_armar_ms ≥ 145000` e `delta_desde_depois_de_armar_ms ≤ 155000` em `tempos.txt`, e os dois iguais aos recalculados de `t_fire`, `t_antes_armar` e `t_depois_armar` da cronologia | §5.7 |
| B4 | `journal-unit.txt` da cópia **e** `05-journal-unit.txt`: `Heal: removendo`, `Deactivated successfully` ou `Succeeded`, nenhum `Failed with result`; `estado-systemd.txt` sem `is-failed: failed` | §5.7 |
| B5 | `systemctl cat` da unit (em `unit-armada.txt`) igual ao `armar` de `g-lib.sh`: no `.service`, um só `ExecStart` = `bash scripts/inject-partition.sh --heal --minority isn3` (aspas tiradas e `/usr/bin/bash` lido como `bash`), `Environment=PODMAN=sudo podman`, `WorkingDirectory=` o `TCC_DIR`, e nenhuma outra diretiva além de `Description`; o `.timer` só com `Description` (igual à do `.service`), `OnActiveSec` = 145 s, `AccuracySec=1s` e o `RemainAfterElapse=no` que o `systemd-run` escreve | §5.7 ("corpo idêntico ao de produção") |
| B6 | `qdisc-no-disparo.txt` sem `clsact`; `MergeView` de 3 membros nos 3 `log-vista` e, em `05-vistas.log`, com instante ≥ `t_fire`; `HEALTHY` x3 em `saude-depois.txt`; 0 timers ao fim (`estado-systemd.txt` e `05-pos-g5b.log`) **sem** nenhum `systemctl stop` no journal do `sudo` desde o passo 3 | §5.7 |
| B7 | `t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel` presentes e em ordem | §5.7 |
| D-1 | journal do `sudo`: **0** `nsenter … tc qdisc del dev eth0 clsact` vindos da sessão desde `t_kill` (FALHA se houver: foi cura manual). Os vindos da unit do dead-man vão para `LEITURA HUMANA` (esperado ≥ 1, o primeiro até `t_fire + 5 s`) | **Q-13** |
| D-2 | `janela/tc-isn3.csv`: `clsact=1` de `t_start + 5 s` até `t_fire − 2 s` e `clsact=0` a partir de `t_fire + 5 s`, com pelo menos 1 amostra em cada intervalo | **Q-13** |
| D-3 | `AccuracyUSec=1s`, `prazo_s=145` e `OnActiveSec` = 145 s em `unit-armada.txt` | **Q-13** |

**Limite conhecido do B1/A6 (pergunta ao Gestor no relatório do Worker).** O `g5b.sh` (não editado) mede a persistência em segundos inteiros: `T_P0=$(date +%s)` e `while [ $(( $(date +%s) - T_P0 )) -lt 15 ]`, com uma amostra a cada ~2 s. O laço pode sair quando o tempo real desde `T_P0` ainda é ~14,3 s (quando a fração de segundo de `T_P0` passa de ~0,65), e o `t_persistencia_ok` só é carimbado depois de três `podman logs`. Por isso `t_persistencia_ok − t_kill` pode sair entre ~14,3 e ~15 s numa execução correta, e o B1/A6 (≥ 15 s) dá FALHA. O `s7.sh` segue o prompt no B1/A6 (≥ 15 s, lado conservador). Se o B1/A6 cair só por isso (todas as outras condições do A6 OK e `t_persistencia_ok − t_kill ≥ 14 s`), o Senior reclassifica na V-S6B ou o Gestor decide outra regra antes do S7. No teste o mesmo efeito aparece com 3 s e o limite do teste é 2 s.

Não invalidam (`RESULTADO`): `t_split_confirmado − t_start`; `t_mergeview − t_fire` e `t_saudavel − t_fire` (dado de segurança, não é t_merge); o tempo total de corte (`t_start` a `t_fire`); `ISPN000524`/`ISPN100014`/`ISPN000638` (**Q-14**); as linhas `DEGRADED_MODE`.

Leitura humana: os `tc qdisc del` da unit no journal do `sudo` (o S6 só serve de controle positivo para a sessão; o S3 mostrou o `sudo` de root dentro da unit do netdegrade no journal, mas nada provou isso para esta unit); a diferença entre o início da unit no journal (`Started <unit>.service`, `-o short-iso-precise`) e o `t_fire` medido por polling (espera-se ≤ 2 s); o limite do `AccuracySec` acima; a latência do REST de isn3 na janela; a origem dos outros `tc` de escrita no journal do `sudo`; os instantes de `ISPN000094`/`DEGRADED_MODE`/`ISPN000093` nos logs completos.

Q-11 a Q-14 e Q-16 estão **pendentes do Gestor**; o código implementa a recomendação do Senior e marca cada linha com `# Q-nn (pendente do Gestor)`.

## Duração estimada

Teste (Git Bash, shims, janela de 8 s e prazo de 14 s): o caminho completo leva cerca de 35 a 40 s; a suíte inteira deu 663 PASS, 0 FAIL em 1009 s (~17 min; sem outra suíte minha em paralelo, mas com outro Worker usando o host).

Na VM, calculada: o prompt do GO aparece por volta de **2,5 min** (pré-condições, dry-run e portão pré de 130 s). Depois do GO: split confirmado em ~40 a 46 s, KILL em +70 s, persistência até ~+86 s, disparo em 145 a 155 s depois de armar, fusão da ordem de ~46 s, `HEALTHY` x3, preflight e logs: **o `g5b.sh` termina ~3,5 a 4 min depois do GO**. Portão pós até o fim do `g5b.sh` + 345 s (~6 min). Total: **cerca de 12 a 13 min**, mais o tempo até o GO.

## Teste

`bash runs/vm-s7-2026-11-04/s7.test.sh`, sozinho. Mesmo desenho do `s6.test.sh`: o fixture vem do `runs/gate-b29/g5b.sh` **real** em modo B sobre os shims de `gate-test-lib-v4.sh` (~25 s), e o stub reencena o disparo da unit (o `sudo nsenter … tc qdisc del` sai com a `_SYSTEMD_UNIT` da unit) e o journal dela. O T-0 prova também que o que é comum ao `s6.sh` e ao `s7.sh` (utilitários, coletor, espera depois de falha, console) é igual byte a byte.

## Como copiar os brutos para o host

```bash
scp -r -o HostKeyAlias=172.18.251.140 -i ~/.ssh/id_ed25519_tccf3 server1@<ip>:tcc-gate/runs/vm-s7-2026-11-04/vm-<UTC> runs/vm-s7-2026-11-04/
cd runs/vm-s7-2026-11-04/vm-<UTC> && sha256sum -c MANIFEST.sha256 && sha256sum -c g5b-B.sha256
cd ../../.. && bash runs/vm-s7-2026-11-04/s7.sh --avaliar runs/vm-s7-2026-11-04/vm-<UTC>
```

## Como o `s7.sh` chega à VM

Só pelo bundle **v4g**, conferido por sha256, depois da validação do Senior (V-s6s7sh) e da autorização do Gestor (Q-02). Antes do S7, na VM: `sha256sum runs/vm-s7-2026-11-04/s7.sh` deve dar `ac67a6fa0a2ee1d08c3b82c8b79a1bb3f0e40847c8c44c7181bb50f95fd15863`.
