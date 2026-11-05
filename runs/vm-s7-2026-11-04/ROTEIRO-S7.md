# Roteiro do S7 (G-5b-B: o dead-man disparando sozinho), 2026-11-04

> **Estado em 2026-11-05 (Senior): S7 VÁLIDO na terceira tentativa, bloco encerrado, não repetir.**
>
> | Tentativa | Diretório | GO (UTC) | Veredito automático | Validação | Causa |
> |---|---|---|---|---|---|
> | 1 | `vm-20261104T225329Z/` | 04/11 22:55:51 | `INVALIDO` (B1/A11, 1 amostra ruim no portão pós) | `V-S7.md` | pausa de ~10 s da VM inteira |
> | 2 | `vm-20261104T232939Z/` | 04/11 23:32:06 | `INVALIDO` (intervalo do saúde-log 20,1 s) | `V-S7r.md` | disco virtual travado por 176 s |
> | 3 | `vm-20261105T012458Z/` | 05/11 01:27:13 | **`VALIDO (19 criterios OK)`** | **`V-S7t.md`** | — |
>
> Antes da terceira tentativa, o coordenador aplicou ao host as mitigações da V-S7r §5 (ASPM desligado, `ReTrim`, exclusão de `C:\Hyper-V` no Defender), mediu 30 min de novo e fez a checagem de 10 min. **A condição de entrada da V-S7r §6, item 3, foi cumprida**: nos 10 min da checagem (01:14:21-01:24:22Z), a pior janela de 10 s de PSI io `full` foi de 1,17 s, abaixo de 1,5 s, com 0 amostras ruins. O `s7.sh`, o `g5b.sh` e o `portao51.sh` não mudaram em nenhuma tentativa. Os brutos das três estão no host, com `sha256sum -c` OK. Com o S7 válido, S1..S7 estão aprovados.
>
> Daqui em diante, este roteiro é histórico. O passo seguinte da VM está em `docs/roteiro-vm-2026-11-04.md` §0 e na fila do `plano-execucao-continua-2026-11-04.md` §3.0 (`wt-plano-ctrl`).

Preparado pelo Worker. Não foi executado na VM. Referências: `docs/prompt-worker-s6-s7-g5b-2026-11-04.md`, `decisoes-senior-2026-10-16.md` §5.1, §5.2, §5.7 e §5.8 (na `wt-plano-ctrl`), `runs/vm-s5-2026-10-28/V-s5sh.md` (R-1, R-2, R-3, R-6), `runs/vm-s4-2026-10-28/V-S4.md` (P-2), `docs/roteiro-vm-2026-11-04.md`, seção 9 (Q-11 a Q-16, pendentes do Gestor), e o `runs/vm-s6-2026-11-04/ROTEIRO-S6.md`, do qual este difere só no que é próprio do modo B.

## O que o S7 prova e o que não prova

Cobre a segunda lacuna da §5.7: **o dead-man disparando de fato, pelo caminho que toca `tc`**. O `runs/gate-b29/g5b.sh B` (v4c, `902654b`, não editado) arma o dead-man pela mesma função `armar` (corpo de produção, margem curta: 120 + 25 = **145 s**, `AccuracySec=1s`), injeta `--minority isn3 --duration 120`, espera o split confirmado, dá `kill -KILL` no injetor a partir de +70 s, confere a persistência por 15 s e então **não cura e não desarma**: espera o dead-man, que tem de disparar entre 145 e 155 s depois de armar.

O `s7.sh` não injeta, não cura, não desarma e não digita o GO.

Não prova (§5.7 e §5.8): a **precisão do timer de produção**. A unit do S7 tem `AccuracySec=1s` e a de produção não; o S7 prova o **corpo** do dead-man de produção (o que ele executa), não quando o timer de produção dispara, que pode atrasar até 60 s (R-3 da V-S2). Também não prova o tempo de fusão (dado de segurança, não é t_merge), a queda da VM nem a perda da sessão `tmux`. Estas partições não entram no conjunto do G-4.

## Antes do GO, quem roda o bloco precisa saber

- **O nó isn3 fica cortado até ~145 s depois de armar.** **Não curar à mão**: qualquer `--heal` manual invalida o bloco (§5.7), e o detector do journal do `sudo` (D-1) vai registrá-lo.
- **Se o dead-man não disparar** até 145 + 10 + 30 = **185 s** depois de armar, o `g5b.sh` falha e o trap dele cura (o bloco fica como falha do dead-man). Só se o próprio `g5b.sh` travar use a cura manual, em `~/tcc-gate`: `PODMAN='sudo podman' bash scripts/inject-partition.sh --heal --minority isn3`, sabendo que o bloco fica registrado como falha do dead-man (**Q-16**, pendente). O `s7.sh` imprime este aviso logo antes do prompt.
- **Quem dá o GO (autorizado pelo Gestor em 2026-11-04).** Em 04/11 o Gestor autorizou o coordenador a rodar sozinho os blocos de VM (S5, S6, S7 e as campanhas noturnas), sem a presença dele. O GO continua sendo dado bloco a bloco, **digitado pelo coordenador** num terminal `tmux` da VM, sob essa autorização. O mecanismo `exigir_go` e os scripts não mudam: o GO continua vindo de um terminal. O coordenador acompanha o bloco até a cura. As regras de parada e de cura manual são as mesmas.
- **O GO é digitado pelo coordenador** (autorizado pelo Gestor em 2026-11-04) no prompt do `g5b.sh`, em **até 15 min**; depois disso responda qualquer outra coisa (aborta sem armar) e recomece.
- **O S6 tem de ter rodado antes** (§5.7, "A antes de B"): o `s7.sh` exige `runs/gate-b29/g5b-A/resultado.txt` e um portão 5.1 limpo entre os dois (o portão pré do S7).
- **O coordenador acompanha o bloco até a cura**, do GO até o fim do `g5b.sh` (cerca de 3,5 min).
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
sha256sum runs/vm-s7-2026-11-04/s7.sh   # esperado 2385609e850c7813124ca4f98bae7c4e91aeef8cfec9c1ae763234c9f4cf415e
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
| B1 | A1, A2, A3, A4, A5, A6 (com o limite de 14 s da R-1), A11 e A12 do S6, com a mesma regra (linhas `B1/A1` ... `B1/A11` e o intervalo do saúde-log) | §5.7, V-S4 P-2 |
| B2 | `chamadas-injetor.txt` com 1 linha (a injeção) e 0 `--heal`; `PASS nenhum --heal manual` | §5.7 |
| B3 | `delta_desde_antes_de_armar_ms ≥ 145000` e `delta_desde_depois_de_armar_ms ≤ 155000` em `tempos.txt`, e os dois iguais aos recalculados de `t_fire`, `t_antes_armar` e `t_depois_armar` da cronologia | §5.7 |
| B4 | `journal-unit.txt` da cópia **e** `05-journal-unit.txt`: `Heal: removendo`, `Deactivated successfully` ou `Succeeded`, nenhum `Failed with result`; `estado-systemd.txt` sem `is-failed: failed` | §5.7 |
| B5 | `systemctl cat` da unit (em `unit-armada.txt`) igual ao `armar` de `g-lib.sh`: no `.service`, um só `ExecStart` = `bash scripts/inject-partition.sh --heal --minority isn3` (aspas tiradas e `/usr/bin/bash` lido como `bash`), `Environment=PODMAN=sudo podman`, `WorkingDirectory=` o `TCC_DIR`, e nenhuma outra diretiva além de `Description`; o `.timer` só com `Description` (igual à do `.service`), `OnActiveSec` = 145 s, `AccuracySec=1s` e o `RemainAfterElapse=no` que o `systemd-run` escreve | §5.7 ("corpo idêntico ao de produção") |
| B6 | `qdisc-no-disparo.txt` sem `clsact`; `MergeView` de 3 membros nos 3 `log-vista` e, em `05-vistas.log`, com instante ≥ `t_fire`; `HEALTHY` x3 em `saude-depois.txt`; 0 timers ao fim (`estado-systemd.txt` e `05-pos-g5b.log`) **sem** nenhum `systemctl stop` no journal do `sudo` desde o passo 3 | §5.7 |
| B7 | `t_start t_split_confirmado t_kill t_persistencia_ok t_fire t_mergeview t_saudavel` presentes e em ordem | §5.7 |
| D-1 | journal do `sudo`: **0** `nsenter … tc qdisc del dev eth0 clsact` vindos da sessão desde `t_kill` (FALHA se houver: foi cura manual) **e ≥ 1** vindo da unit do dead-man entre `t_kill` e `t_fire + 1 s` (1 s = resolução da sondagem que mede o `t_fire`, `docs/rastreabilidade-limiares-s5-s7-2026-11-04.md`, 4.5; FALHA se 0: o journal não mostra a cura pelo dead-man; dúvida 5 da V-s6s7sh, decidida pelo Gestor em 2026-11-04, com base no S3, que mostrou o `sudo` de root dentro da unit registrado com `_SYSTEMD_UNIT` = a unit) | **Q-13** |
| D-2 | `janela/tc-isn3.csv`: `clsact=1` de `t_start` (folga 0 s) até `t_fire − 2 s` (sondagem de 1 s mais a duração do `tc`) e `clsact=0` a partir de `t_fire + 1 s`, com pelo menos 1 amostra em cada intervalo. Folgas de `docs/rastreabilidade-limiares-s5-s7-2026-11-04.md`, 4.4 e item 45 | **Q-13** |
| D-3 | `AccuracyUSec=1s`, `prazo_s=145` e `OnActiveSec` = 145 s em `unit-armada.txt` | **Q-13** |

**Limite do B1/A6 (R-1 da V-s6s7sh, decidido pelo Gestor em 2026-11-04).** O `g5b.sh` (não editado) mede a persistência em segundos inteiros: `T_P0=$(date +%s)` e `while [ $(( $(date +%s) - T_P0 )) -lt 15 ]`, com uma amostra a cada ~2 s. Por construção ele só garante `t_persistencia_ok − t_kill > 14 s`, e numa execução correta o valor pode sair entre ~14,3 e ~15 s. A regra do invólucro é a que o `g5b.sh` garante: **FALHA se `t_persistencia_ok − t_kill < 14 s`**; em [14, 15) s sai `RESULTADO` e `LEITURA HUMANA` com o valor exato (o Senior confere na V-S6B que a diferença vem só da granularidade); ≥ 15 s é OK sem ressalva. No código, `PERSISTE_MIN_S=$(( PERSISTE_S - 1 ))`; no teste `PERSISTE_S=2`, então os casos de fronteira são 0,999 s (FALHA), 1,000 s e 1,999 s (RESULTADO). É desvio explícito do texto "≥ 15 s" do §5.7, aprovado pelo Gestor.

**Limites do próprio `g5b.sh` (pré-registrado antes do bloco, decisão do Gestor de 2026-11-04; `docs/rastreabilidade-limiares-s5-s7-2026-11-04.md`, itens 38 a 40 e 4.3).** O `g5b.sh` não é editado. (1) **150 s** até `MergeView` de 3 membros e `HEALTHY` x3 depois do disparo do dead-man: se o bloco falhar **só** por esse limite e o `MergeView` de 3 e o `HEALTHY` x3 aparecerem depois nos brutos (`05-vistas.log`, portão pós), o bloco fica **INVÁLIDO para a ferramenta** e o tempo medido de recomposição vira `RESULTADO` (a REP=2 levou 188 s; nas REPs em cluster novo, 44 a 48 s). Refaz-se o bloco, com portão 5.1 limpo e novo GO. Isso não cria falso VÁLIDO: a cura e a fusão são conferidas à parte (B6 e portão pós). (2) **40 s** até o `event=start` e **100 s** até o split confirmado ficam como estão: o veredito não depende do valor dentro das faixas medidas (start em menos de 2 s no S4; split entre a maior detecção medida, 46,4 s, e o fim da janela, 120 s), conforme a análise de insensibilidade do documento do Senior.

Não invalidam (`RESULTADO`): `t_split_confirmado − t_start`; `t_mergeview − t_fire` e `t_saudavel − t_fire` (dado de segurança, não é t_merge); o tempo total de corte (`t_start` a `t_fire`); `ISPN000524`/`ISPN100014`/`ISPN000638` (**Q-14**); as linhas `DEGRADED_MODE`.

Leitura humana: o instante do primeiro `tc qdisc del` da unit no journal do `sudo` em relação ao `t_fire` (a contagem virou critério no D-1); a diferença entre o início da unit no journal (`Started <unit>.service`, `-o short-iso-precise`) e o `t_fire` medido por polling (espera-se ≤ 2 s); o limite do `AccuracySec` acima; a latência do REST de isn3 na janela; a origem dos outros `tc` de escrita no journal do `sudo`; os instantes de `ISPN000094`/`DEGRADED_MODE`/`ISPN000093` nos logs completos.

Q-11 a Q-14 e Q-16 estão **pendentes do Gestor**; o código implementa a recomendação do Senior e marca cada linha com `# Q-nn (pendente do Gestor)`.

## Duração estimada

Teste (Git Bash, shims, janela de 8 s e prazo de 14 s): o caminho completo leva cerca de 35 a 40 s; a suíte inteira, sozinha, deu **693 PASS, 0 FAIL em 982 s** (2026-11-04, com a R-1, o D-1 como FALHA, as folgas do D-2 e os casos de fronteira; antes, 663 PASS).

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

Só pelo bundle **v4h** (`C:\Users\Francesco\Development\tcc\gate-v4h.bundle`, incremental de `1266c12`, HEAD da VM, até a ponta desta branch, com `.sha256`), conferido por sha256, com a autorização do Gestor (Q-02). O v4g ficou superado. Antes do S7, na VM: `sha256sum runs/vm-s7-2026-11-04/s7.sh` deve dar `2385609e850c7813124ca4f98bae7c4e91aeef8cfec9c1ae763234c9f4cf415e`.
