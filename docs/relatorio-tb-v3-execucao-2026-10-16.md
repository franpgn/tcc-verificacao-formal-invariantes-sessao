# Execucao do gate v3 na VM: REP=3 do G-4, G-5, G-6 (2026-10-16)

Worker-2. Bundle `gate-v3.bundle` (sha256 `c44bb837...5175`), HEAD `507c21ee6812d4ea69788277907b92b56ff13311`, VM `192.168.137.150`. Autorizado pelo Gestor nesta conversa: REP=3, G-5, G-6, recriacao do cluster, REP=4 condicional. NAO executado: L-0/L-1, qualquer inject-netdegrade/perda de pacotes, REP=4 (ver secao 7). O Senior valida; este documento nao se autoaprova. Brutos em `runs/gate-b29/` (g1-rep3, g5, g6, recriacao, go.log, consoles; ignorados pelo `.gitignore`, ficam em disco; copia espelhada na VM em `~/tcc-gate/runs/gate-b29/`).

## 1. Preparo (passo 0 e 1)

| Item | Resultado |
|---|---|
| sha256 bundle / jar na VM | `c44bb837...5175` / `3594c690...7bee`, iguais ao host |
| HEAD | `507c21ee...7311` |
| 8 hashes da secao 5 do relatorio v3 | 8/8 conferem |
| CR em `runs/gate-b29/*.sh` e `scripts/*.sh` | 0 |
| Modos | `*.sh` executaveis (g0, g0b, g1, g1b, g5, g6, `*.test.sh`); libs `100644` |
| R1 backup | `~/backup-gate-b29-pre-v3` (copia de `runs/gate-b29` + `tracked-mods.diff`: a VM tinha g-lib/g0/g0b modificados na arvore, branch `tmp/vm-bundle-2`); `git checkout -f -B test/b29-gate-vm-v3 FETCH_HEAD`; `g1-rep1/` e `g1-rep2/` seguem intactos (untracked) |
| Suites na VM, uma por vez (R6) | partition 127/0/1; jitter 35/0; g1b falhas=0; g1 48/0; g5 50/0; g6 70/0 |
| Suite `inject-netdegrade.test.sh` | NAO rodada na VM: nao esta na lista do relatorio v3 (6.2) e o escopo exclui netdegrade; o classificador de permissao a bloqueou. Foi 298/0/1 (Git Bash) e 306/0/0 (WSL) em V-B3 |
| R3, dead-man inerte com `systemd-run` real | unit `tcc-dmtest`, container `nao-existe`: journal "dead-man netem: pid/netns invalido para nao-existe (pid=); NAO tocado", exit 1/FAILURE. Aspas e `$` do corpo chegaram intactos ao `bash -c`. Nenhum no tocado; unit residual limpa com `reset-failed` |

## 2. Recriacao do cluster (R4)

Antes: `podman ps -a` (isn1..3 Up 4 h, projeto `cluster`), imagem `quay.io/infinispan/server:15.0.21.Final` presente; dump de `podman logs` e `inspect` em `recriacao/pre-recriacao-*` (idade antiga: start 05:00:14-17). `podman-compose -f cluster/podman-compose.yml down` rc=0, imagem preservada, `up -d` rc=0.

Validacao (`recriacao/rec1/validacao.txt`, laco de 2 s): primeira leitura HEALTHY size=3 nas 3 portas +4 s apos o `up`; leituras consecutivas boas por 20 s; "ESTAVEL ... total 24 s" (limite 180 s). MergeView de 3 membros `[isn2-15987|2]` as 08:54:45; 0 timers tcc-heal; os 3 netns so `noqueue`.
Idade do cluster: containers iniciados 08:54:38.76-41.14; GO da REP=3 as 08:56:39 e injecao imediatamente depois: **idade do cluster no start da janela 120,8 s**, topologia baixa (ids 10-11 na janela).

Observacao: so houve UMA recriacao (REP=4 nao executada).

## 3. REP=3 do G-4 (g1.sh + g1b `COLETA=minima`, `REP=3` explicito, rotulo conferido no GO; GO digitado por `tmux send-keys` com autorizacao do Gestor; g1.sh sob `flock`, g1b em janela sem lock)

go.log: `go G-1 (REP=3, janela de 90 s em isn3) 2026-10-16T08:56:39+00:00 sha=507c21ee...`. `inject-partition.sh` com A2 (R5): diferente do da REP=2 no guarda de netns; rc/marcadores iguais.

Validade da REP: injetor rc=0, sem abort, dead-man desarmado, sem clsact, janela real **96,046 s** (start 08:56:39.530, end 08:58:15.576; REP1/2: 96,04). Valida.

| Criterio | Evidencia | PASS/FAIL |
|---|---|---|
| t_split maioria | vista `[isn2-15987\|3] (2)` em 08:57:25.975 (isn2) / .982 (isn1): **+46,4 s** do start | medida |
| t_split isn3 | vista `[isn3\|3] (1)` 08:57:19.340: **+39,8 s**; sessions/counters DEGRADED_MODE (topologia 11) 08:57:19.352 | medida |
| Vista fundida | MergeView `[isn2-15987\|4] (3)` 08:59:01.984 (isn2), .989 (isn1, isn3): **+46,41 s** do `event=end` | PASS |
| **t_merge** | `t_merge.txt` = **46 s**; ultima amostra nao saudavel do `saude-log.csv` em +46,19 s; Finished rebalance dos caches em 08:59:02.010-.173 (**+46,6 s**). Faixa **<= 60 s (~48 como REP1)** | ver ressalva |
| ISPN000524 / ISPN100014 / ISPN000638 | 0 ocorrencias nos 3 logs. Conflict resolution sessions/counters: Starting 08:59:01.997, Finished 08:59:02.160-.166 (~165 ms) | PASS |
| Cluster ao fim | `saude-depois`: HEALTHY size=3 x3; 0 timers; sem clsact | PASS |
| Chaves de teste da sonda | `GET /caches/{sessions,counters}/sonda-isn1|sonda-isn3` = 404 e `?action=size`=0 | PASS (limpo) |

Ressalva do saude-log: `g1.sh` encerra o log de saude ao detectar size=3, e o ultimo ciclo de 2 s ainda mostrava size=2/DEGRADED (216 linhas; 0 amostras HEALTHY size=3 depois do end). Logo **t_merge pelo saude-log nao e calculavel como o Senior definiu (primeira amostra HEALTHY/3)**; ha apenas o limite inferior 46,19 s. A medida exata vem dos instantes do `MergeView` e do `Finished rebalance` (46,4-46,6 s) e do `t_merge.txt` (46 s). A classificacao nao depende disso (46 << 60).

Desvios do saude-log (resolucao 2 s, por porta; `HEALTHY_REBALANCING` = 0 amostras): primeira amostra com size != 3: 11224 DEGRADED size=1 em +40,2 s; 11222/11223 HEALTHY size=2 em +48,2 s.

### Contadores por filtro (isn3, kernel: src/ingress offset 12, dst/egress offset 16)

Final (`inj.log`, 96 s instalados): egress (dst) isn1 (10.89.1.5): 125 pkt, 123 descartados; egress isn2 (10.89.1.6): 314 pkt, 312 descartados; ingress (src) isn1: 21 pkt/21; isn2: 12 pkt/12. Soma do injetor: **ingress=33, egress=439**. Amostra +82 s (`janela.log`): egress 120/268, ingress 20/11. Sondas: isn3->isn1:7800 rc=124 nas 5 amostras; isn1->isn2:7800 rc=0 nas 5.

### REST de isn3 e Hot Rod (g1b minimo)

`/health` e REST de isn3 (11224) responderam **http=200 em ~4-7 ms nas 5 amostras da janela** (na REP2 o REST de isn3 ficou pendurado com 52/60 000). Sonda isn1: 282 linhas, 0 erro. Sonda isn3: 282 linhas, 232 erros, 50 OK (de +145,4 a +175,3 s).

Excecoes do cliente Hot Rod em isn3 (nomes reais, para C-2, sem ligar `@Disabled`):

| Fase | Quantidade | Classe | Mensagem |
|---|---|---|---|
| +0,9 a +39,1 s (antes da deteccao) | 12 (sessions e counters) | `TransportException` (put 3, get 3, remove 2) e `RuntimeException` (entrySet 3, IterationNextOperation) | `java.net.SocketTimeoutException: <Put\|Get\|Remove\|IterationNext>Operation{...} timed out after 2000 ms`; duracao 0,79 a 4,05 s (2 tentativas) |
| +39,8 a +140,5 s (DEGRADED, sessions **e** counters) | 220, duracao ~0 s | `HotRodClientException` | put 55 / get 55 / remove 56: `org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[...]' is not available. Not all owners are in this partition`; entrySet 55: `AvailabilityException: ISPN000305: Cluster is operating in degraded mode because of node failures.` |
| +145,4 s em diante | 50 OK | | primeira operacao OK 3,0 s depois do MergeView (+142,5 s) |

Latencia ate o primeiro erro de aplicacao: o primeiro `HotRodClientException` (counters put, +39,8 s) coincide com DEGRADED_MODE de isn3 (+39,82 s); resolucao da sonda ~1-4 s por operacao. Diferente da REP2, **counters tambem devolve erro imediato** (sem timeout apos a deteccao): o atraso da REP2 nao reapareceu.

## 4. G-5 (g5.sh, `--dry-run` antes, GO real)

go.log: `go G-5 ... 2026-10-16T09:02:02`. Nenhuma linha FAIL/ABORTADO; `abort-heal.log` ausente; 8 PASS.

| Criterio | Evidencia | Resultado |
|---|---|---|
| TERM rc=143 | rc=143 | PASS |
| sem clsact apos o trap | `qdisc noqueue` | PASS |
| marcadores start e end | presentes (uma vez cada) | PASS |
| size=3 HEALTHY <= 150 s | "apos 0 s" | PASS |
| KILL rc=137 | rc=137 | PASS |
| clsact fica apos o KILL | "clsact FICOU" | PASS |
| sem `end` no KILL | sem marcador | PASS |
| `--heal` rc=0 e sem clsact | `heal.log` 09:02:39; `qdisc-pos-heal` limpo | PASS |
| size=3 HEALTHY apos o KILL | "apos 0 s" | PASS |
| preflight final (0 timers, sem clsact/netem) | todos "ok" | PASS |
| "G-5 concluido" | presente | PASS |

**G-5 PASS.** Nao houve mudanca de vista (janela 40 s < deteccao; `log-vista-*` vazios), como esperado. Dead-men `tcc-heal-173504-9720` e `-5287` armados e desarmados.

## 5. G-6 (g6.sh, `G6_LIMITE_S=300`, `--dry-run` antes, GO real)

go.log: `go G-6 ... 2026-10-16T09:03:46`. Nenhuma linha FAIL/ABORTADO; 0 timers; 7 linhas PASS.

| Criterio | Evidencia | Resultado |
|---|---|---|
| PASS durante a janela, N >= 2 | "3 amostras validas de: +10s +45s +70s", 0 PULADA | PASS |
| F2 rc=0 | rc_particao=0 | PASS |
| isn3 sem clsact apos F2 | `qdisc noqueue` | PASS |
| limpeza da F2 nao removeu o netem de isn2 | isn2 `netem 8002: delay 20ms 13ms` logo apos o fim da F2 | PASS |
| F3 rc=0 | rc_jitter=0 | PASS |
| isn2 sem netem; isn3 limpo | `noqueue` nos 3 apos F3 | PASS |
| size=3 HEALTHY dentro do limite | "size=3 HEALTHY apos 45 s desde o fim da F2" (limite 300 s) | PASS |
| preflight final / "G-6 concluido" | todos "ok", 0 timers | PASS |

**G-6 PASS.** Dados (nao agregar ao G-4): F3 ligado 09:03:47 (`t_f3_on` 1792141427.8); F2 start 09:03:48.19, end 09:05:24.23, F3 fim 09:05:56.89. Vista maioria `[isn2\|5] (2)` em 09:04:29.98: **+41,8 s**; isn3 09:04:31.20 (DEGRADED_MODE topologia 23): **+43,0 s**; MergeView `[isn2\|6] (3)` 09:06:05.987 (isn2), 09:06:06.994 (isn1), .993 (isn3): **+41,75 s** do end da F2 (isn1 +42,76 s). Dead-men `tcc-heal-176004-31381` e `-netem-176004-10179` armados antes da injecao, desarmados ao fim.

## 6. Classificacao e o que NAO provam

REP=3: t_merge **~46 s**, faixa `<= 60 s` (como REP1 = 48). Leitura (do Senior, secao 6 de V-B3): sonda minima e `/health` a 2 s sao inocentes nesta medida; REP2 foi H2 (coleta completa) ou H3 (historico/3a particao da instancia) ou outra causa. **Isto e uma observacao, nao uma prova**: H2 e H3 continuam nao separadas (a REP=3 usou cluster novo E coleta minima ao mesmo tempo); uma unica REP no estrato; nao exclui um evento raro, que a REP2 teria sido. Os 188 s nao reapareceram; nao foi tentado reproduzir.

O que os resultados NAO provam:
- Nao provam que H1 (sonda) seja inocente em geral: a sonda minima aqui conviveu com cluster novo; o REST de isn3 respondeu normal, o que difere da REP2.
- G-5 cobre janela de 40 s sem split; nao testa o heal depois de uma particao real nem o `systemd-run` do dead-man disparando de fato (foi desarmado nas tres execucoes; so o corpo inerte foi provado em R3 com container inexistente, NAO o caminho que toca `tc`).
- G-6 e uma execucao so; o jitter de 20 ms +/- 13 ms em isn2 nao alterou o split (41,8 s) nem o merge (41,75 s), mas isso nao cobre jitter maior, nem F3 em outros nos; os tempos nao entram no conjunto do G-4.
- Nenhum teste de netdegrade/perda de pacotes (L-0/L-1), nem a suite `inject-netdegrade.test.sh`, foi feito na VM.
- t_merge pelo saude-log nao foi calculavel (secao 3); a medida vale pelos logs de container.
- Exatidao do instante do start: marcadores do injetor (epoch ns) vs relogio dos containers: mesma VM, sem skew.

## 7. REP=4

**Nao executada.** Regra: REP=3 em ~46 s torna a REP=4 opcional ("recomendada", por dar 3 janelas comparaveis REP1/REP3/REP4); a instrucao do Gestor/Senior e nao executar e pedir decisao. Se o Senior/Gestor quiser: recriar de novo o cluster e `REP=4 G1_LOG_SAUDE_S=0 bash runs/gate-b29/g1.sh` (sem g1b), usando `t_merge.txt` mais `MergeView`/`Finished rebalance` dos logs.

## 8. Estado final da VM

0 timers `tcc-heal`; `qdisc noqueue` nos 3 netns (sem clsact/netem); sem tmux/flock/injetor/sonda presos; cluster HEALTHY size=3 nas 3 portas; `sessions`/`counters` com size 0. Sem force-push, sem push; sem segredos.

Mensagens para C-2 (nomes reais, secao 3): `org.infinispan.client.hotrod.exceptions.TransportException` (timeout 2000 ms), `HotRodClientException` com `AvailabilityException ISPN000306` (put/get/remove) e `ISPN000305` (entrySet), `RuntimeException` (timeout no `entrySet`). O `@Disabled` de C-2 NAO foi ligado.
