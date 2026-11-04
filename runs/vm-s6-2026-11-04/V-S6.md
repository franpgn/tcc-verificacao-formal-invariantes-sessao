# V-S6: validação do S6 (G-5b-A, split real de isn3, `kill -KILL` do injetor, `--heal` manual), 2026-11-04

Autor: Senior. Bloco rodado pelo coordenador em 04/11, 22:38:05-22:48:32Z, com GO digitado às 22:40:26Z sob a autorização do Gestor de 2026-11-04 (`go.log`, sha `f6483c5`). VM `172.27.114.62`, `boot_id` `f9e98b74-bf1f-4192-9770-b40285877292` no início e no fim (`07-boot-fim.txt`), mesmo cluster do S5 (isn1-15077, isn2-28862, isn3-9749). Host em silêncio (H0), segundo o coordenador.

Brutos em `vm-20261104T223805Z/`. Conferidos no host: `sha256sum -c MANIFEST.sha256`, `g5b-A.sha256` e `copia-host.sha256`, todos OK. Só li os brutos. Nada foi rodado na VM nem no host além do `sha256sum`, e não houve push.

Avaliação automática: `S6: VALIDO (16 criterios OK)` (`08-avaliacao.txt`). Abaixo, as três leituras humanas que ela pede (§5.7 de `decisoes-senior-2026-10-16.md`; Q-13 a Q-15 de `docs/roteiro-vm-2026-11-04.md`).

## 1. Instantes nos `log-completo-isn*.txt` e ordem split -> KILL -> heal -> merge

Referência: `t_start` = 22:40:27,350 (`cronologia.txt`). Os três contêineres usam o relógio do mesmo kernel, então os carimbos dos logs e os da cronologia são comparáveis.

| Instante (UTC) | Fonte | Evento | Relativo |
|---|---|---|---|
| 22:40:26,567-26,812 | journal do `sudo` | pré-limpeza, sonda e injeção (`clsact` + 4 filtros) | antes do `start` |
| 22:40:26,816 | `marcadores.txt` | `event=start` | - |
| 22:40:27,350 | cronologia | `t_start` | 0 |
| 22:41:08,630 | isn3 | `ISPN000094` `[isn3-9749\|3] (1)` | +41,28 s |
| 22:41:08,638 / 08,640 | isn3 | `DEGRADED_MODE` (`sessions`, `counters`), topology id 11 | +41,29 s |
| 22:41:09,269 / 09,276 | isn1 / isn2 | `ISPN000094` `[isn1-15077\|3] (2)` | +41,92 / +41,93 s |
| 22:41:11,176 | cronologia | `t_split_confirmado` (sondagem do `g5b.sh`) | +43,83 s |
| 22:41:37,877 | cronologia | `t_kill` (`rc_injetor=137`) | +70,53 s |
| 22:41:54,014 | cronologia | `t_persistencia_ok` | kill + 16,14 s |
| 22:41:54,016 | cronologia | `t_heal` | +86,67 s |
| 22:41:54,083 | journal do `sudo` | `tc qdisc del dev eth0 clsact` do `--heal` | heal + 0,067 s |
| 22:42:45,276 | isn3 | `ISPN000093` MergeView `[isn1-15077\|4] (3)` | heal + 51,26 s |
| 22:42:46,278 / 46,319 | isn1 / isn2 | `ISPN000093` MergeView `[isn1-15077\|4] (3)` | heal + 52,26 / 52,30 s |
| 22:42:46,457 / 46,460 | isn1 | `ISPN100013` fim da resolução de conflito (`counters`, `sessions`) | heal + 52,44 s |
| 22:42:46,998 | cronologia | `t_mergeview` (sondagem) | heal + 52,98 s |
| 22:42:47,016 / 47,023 | cronologia | `t_saudavel` / `t_desarmado` | heal + 53,00 / 53,01 s |

A ordem é a exigida. O split aparece nos três logs entre +41,3 e +41,9 s, antes da confirmação pela sondagem (+43,8 s) e 28,6 s antes do KILL. Entre o split e o MergeView não há nenhuma outra linha `ISPN000094`/`ISPN000093` em nenhum nó, então as vistas ficaram divididas durante toda a persistência (KILL até heal) e só se juntaram depois do `--heal`. `qdisc-apos-kill.txt` mostra `clsact` presente; `qdisc-pos-heal.txt`, ausente. A detecção (+41,3 a +41,9 s) cai dentro da faixa medida nas REPs (39,8 a 46,4 s).

Observação sem efeito no veredito: isn3 registra o MergeView 1,0 s antes de isn1, que é o coordenador da fusão. O relógio é o mesmo, então a diferença é real; o mais provável é a ordem de instalação da vista no JGroups (o líder instala a sua depois de coletar as confirmações), mas não verifiquei isso. A resolução de conflito terminou em 0,14 s e não houve `ISPN000524`/`ISPN100014`/`ISPN000638`.

## 2. As 2 linhas de `DEGRADED_MODE`

As duas são de isn3, às 22:41:08,638 e 08,640, uma por cache com tratamento de partição: `[Context=sessions]` e `[Context=counters]`, ambas `ISPN100011: Entering availability mode DEGRADED_MODE, topology id 11`. É o comportamento esperado do lado minoritário: isn3 ficou sozinho numa vista de 1 de 3 membros, sem maioria, e as duas caches de aplicação passam a modo degradado. As caches internas (`org.infinispan.CONFIG`, `___protobuf_metadata` etc.) não registram essa linha.

No lado majoritário, isn1 registra `Entering availability mode AVAILABLE, topology id 10` para `sessions` (09,275) e `counters` (09,287), seguido de rebalance entre isn1 e isn2. Portanto as 2 linhas são exatamente o sinal de split que o §5.7 pede ("isn3 `DEGRADED_MODE`"), e não um problema. Não há linha de volta a `AVAILABLE` em isn3 depois da fusão: a recuperação aparece em isn1 como `ISPN100007 After merge ... recovered members [isn1, isn2, isn3]` (22:42:46,322), e as 3 portas dão `HEALTHY`, `number_of_nodes=3` (`saude-depois.txt`, portão pós).

## 3. Origem dos comandos `tc` de escrita no journal do `sudo`

Extraí do `05-sudo-journal.txt` (870 entradas desde `@1793832017`) todos os `COMMAND=` com `tc qdisc|filter add|del`. São 11: 3 `del clsact` (os que o D-1 lista) e os 8 que a avaliação pediu para conferir. Todos vêm de `session-25.scope` (a sessão `tmux` do bloco); nenhum de `tcc-heal-39356-21867`.

| Hora (UTC) | PID do sudo | Comando (`nsenter -t 1711 -n tc ...`) | Papel (`inj.log`, `heal.log`) |
|---|---|---|---|
| 22:40:26,567 | 40181 | `qdisc del dev eth0 clsact` | pré-limpeza idempotente |
| 22:40:26,572 | 40186 | `qdisc add dev eth0 clsact` | sonda |
| 22:40:26,778 | 40192 | `filter add ... ingress ... src 192.0.2.1/32 action drop` | sonda (TEST-NET-1) |
| 22:40:26,785 | 40197 | `filter add ... egress ... dst 192.0.2.1/32 action drop` | sonda |
| 22:40:26,789 | 40201 | `qdisc del dev eth0 clsact` | fim da sonda |
| 22:40:26,794 | 40206 | `qdisc add dev eth0 clsact` | injeção |
| 22:40:26,799-26,812 | 40211-40224 | 4 `filter add`, src/dst 10.89.1.2 e 10.89.1.3, `action drop` | injeção (isn3 x isn1, isn3 x isn2) |
| 22:41:54,083 | 45122 | `qdisc del dev eth0 clsact` | `--heal` manual (`heal.log` 22:41:54) |

Os 8 "outros" são os `add` (2 de `clsact` e 6 de filtro) da sonda e da injeção. Os 10 primeiros comandos caem em 0,25 s, dentro da execução do injetor (PID 40009, o que levou o KILL), antes do `event=start` e do `t_start`, e batem um a um com as linhas do `inj.log`. Entre `t_kill` e `t_desarmado` há um único comando de escrita, o `del` do `--heal`, 67 ms depois de `t_heal`. A unit do dead-man não disparou: o `05-journal-unit.txt` tem só `Started` do `.timer` (22:40:26) e `Stopped` (22:42:47,02, que é o `t_desarmado`), sem `.service`. A Q-15 não se aplicou. Não houve nenhuma cura vinda da sessão além do `--heal` manual.

## 4. heal -> MergeView: 53 s contra ~46 s das REPs e contra o limite de 150 s

`t_mergeview − t_heal` = 52,98 s pela sondagem. Nos logs, 51,26 s (isn3) a 52,30 s (isn2). As REPs em cluster novo deram 44 a 48 s (~46 s, citado no §5.7 e na linha 40 de `docs/rastreabilidade-limiares-s5-s7-2026-11-04.md`). O S6 ficou de 3 a 6 s acima dessa faixa e muito abaixo do limite pré-registrado de 150 s do `g5b.sh` (folga de ~97 s), longe dos 188 s da REP=2. Pelo §5.7 e pelo ROTEIRO-S6, esse valor é **dado de segurança, não é t_merge**, e com n = 1 não sustenta conclusão sobre a diferença em relação às REPs: o início da contagem aqui é o `--heal` depois de 86 s de isolamento, não o fim de uma janela do injetor. Vai para o relatório como `RESULTADO`.

## Veredito

**S6 VÁLIDO.** Concordo com os 16 critérios automáticos e as três leituras humanas fecham. Split real observado nos três logs antes do KILL; `rc_injetor=137` sem marcador `end`; corte persistente por 16,14 s (≥ 15 s, sem a ressalva da R-1); único agente de cura o `--heal` manual, rc 0, `clsact` ausente em seguida; MergeView de 3 nos 3 nós; `HEALTHY` x3; dead-man armado durante o corte, desarmado 0,007 s depois de saudável, sem disparo; portão 5.1 pós limpo; `boot_id` igual no início e no fim.

## S7

Pode começar já, sem reboot e com o host ainda em silêncio. Antes, na VM: `cat /proc/sys/kernel/random/boot_id` tem de dar `f9e98b74-bf1f-4192-9770-b40285877292` (se der outro, não rodar e chamar o Senior, Q-11), e `sha256sum runs/vm-s7-2026-11-04/s7.sh` tem de dar `2385609e850c7813124ca4f98bae7c4e91aeef8cfec9c1ae763234c9f4cf415e`. Em `~/tcc-gate`:

```bash
bash runs/vm-s7-2026-11-04/s7.sh --boot-esperado f9e98b74-bf1f-4192-9770-b40285877292 --bloco-anterior runs/vm-s6-2026-11-04/vm-20261104T223805Z
```

O `s7.sh` vai exigir `runs/gate-b29/g5b-A/resultado.txt` (presente) e o portão 5.1 pré limpo. O D-4 compara os IDs e o `StartedAt` dos contêineres com os deste bloco.
