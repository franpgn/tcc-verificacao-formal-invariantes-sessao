# V-S5: validação do S5 (L-1 e L-2, perda de 20 % em isn3 por 60 s), 2026-11-04

Autor: Senior. Bloco rodado pelo coordenador em 04/11, 22:19:57-22:30:22Z, com GO digitado às 22:23:29Z sob a autorização do Gestor de 2026-11-04. VM `172.27.114.62`, `boot_id` `f9e98b74-bf1f-4192-9770-b40285877292`, cluster recriado às 21:49:40Z (XML `8792dea3…`, vista `[isn1-15077|2] (3)`). Host em silêncio (perfil H0, CPU ~7 %) durante todo o bloco, segundo o coordenador.

Brutos em `vm-20261104T221957Z/` (33 arquivos). `sha256sum -c copia-host.sha256` confere os 33. Só li os brutos. Nada foi rodado na VM nem no host, e não houve push.

## Veredito

**S5 VÁLIDO, aceito com ressalvas menores (R-a a R-f), nenhuma bloqueante. O S6 pode começar já**, com
`--bloco-anterior runs/vm-s5-2026-10-28/vm-20261104T221957Z` e `--boot-esperado f9e98b74-bf1f-4192-9770-b40285877292`, conferido antes contra `cat /proc/sys/kernel/random/boot_id`.

A perda de 20 % de isn3 para isn1 e isn2 não mudou a vista e não gerou `DEGRADED_MODE`. Pela §5.6, a REP=4B (5.2, item 2) fica onde estava e o S6 não precisa de portão 5.1 extra além do que o próprio `s6.sh` roda. O portão pós deste bloco já deu OK (317 s de sequência boa, 0 amostras ruins).

## 1. Critérios automáticos (`08-avaliacao.txt`)

Os 23 `OK:` conferem com os brutos que recalculei: marcadores 1 start e 1 end, janela de 62,421 s (`l1/marcadores.txt`); `rc=0` do `l1.sh` e do injetor; `boot_id` igual no início (`00-pre.log`) e no fim (`07-boot-fim.txt`); L-2 limpo (`04-l1.log`: "ok sem prio em isn3"; `05-pos-l1.log`: NIC sem `prio`/`netem`/`clsact`, 0 timers e 0 units `tcc-heal`); portão 5.1 pré e pós OK. `FOLGA_LAT_S=0` (`parametros.txt`), como manda a rastreabilidade de 04/11 (item 11).

## 2. Leituras humanas

**P-1 / R-9 (sonda Hot Rod na janela).** Rodou. `janela/sonda-isn3.csv` tem 378 operações, todas `OK`, 0 `ERRO`, 64 `put` e 64 `get` dentro da janela, mais 30 de cada nos 30 s depois do `end`, e `remove-final` OK nos 2 caches (`sessions`, `counters`). Cliente `BASIC`, pinado em `127.0.0.1:11224`. Registro a diferença declarada da R-9: o cluster não ficou "ocioso" como diz a §5.6, porque a sonda acrescenta ~2 operações por segundo com replicação SYNC pelo enlace degradado. Não houve efeito sobre a vista. Como a V-s5sh já chamava, o critério automático "Hot Rod" é a conexão TCP ao endpoint único 11224 (REST e Hot Rod).

**P-2 (amostras da janela acima do p95 da base).** Contei só as amostras entre `start` e `end`.

| Métrica | p95 da base (n) | acima do p95 na janela | max durante | max da base |
|---|---|---|---|---|
| REST isn3 11224, `time_total` | 3,972 ms (30) | 1 de 32 (3 %) | 5,066 ms | 4,238 ms |
| conexão TCP a 11224 | 0,168 ms (30) | 1 de 32 (3 %) | 0,190 ms | 0,176 ms |
| REST de controle isn1 11222, `time_total` | 367,9 ms (30), ver R-7 abaixo | 0 de 32 (1 de 32 contra 3,943 ms) | 4,418 ms | 371,2 ms |
| conexão TCP ao ssh 22 | 0,103 ms (30) | 0 de 32 | 0,097 ms | 0,106 ms |
| RTT do ping do host a isn3 | 0,080 ms (300) | 1 de 250 (0,4 %) | 0,084 ms | 0,384 ms |

Todas ficam em torno dos ~5 % esperados ou abaixo, e nenhum `max durante` passa de 1,2 vez o máximo da base (o único acima do máximo da base é o 5,066 ms do REST de isn3, uma amostra isolada). A cauda não degradou. O mesmo vale para o REST de isn2 (11223), que não é critério: 1 de 32 acima. Se o filtro u32 pegasse o tráfego de isn3 para o host, ~20 % das respostas levariam um RTO de 200 ms. A chance de 32 amostras de REST de isn3 saírem todas abaixo de 6 ms seria 0,8^32 ≈ 0,0008, e a de 250 pings sem perda seria desprezível. Isso responde à parada obrigatória "filtro `u32` degradando Hot Rod/SSH": não degradou.

**R-7, item extra (p95 de 0,368 s no REST de isn1 na base).** É uma anomalia da base, com duas amostras isoladas: 0,371 s a +24,05 s e 0,368 s a +36,07 s do início da base (`base/saude.csv`), com `time_connect` normal (0,072 e 0,127 ms). O tempo foi gasto no servidor, depois de aceita a conexão. Nas 28 restantes o máximo é 4,2 ms. Não coincidem com nada em isn2, isn3, ssh ou na sonda (as operações de +24,04 a +24,06 s ficaram entre 1,4 e 3,4 ms). Fica como pausa local de isn1 (GC ou thread do REST), sem causa confirmada, porque o bloco não guarda log de GC. Com n = 30, o p95 do `stats()` é a 29.ª amostra ordenada, e por isso duas amostras bastam para inflar o limiar. **Não afeta nenhuma conclusão.** Tirando as duas da base, o p95 vira 3,943 ms, a mediana durante (2,808 ms) continua abaixo, e só 1 de 32 amostras da janela passa dele. O isn1 é só controle e não fica atrás do filtro. Sugestão para os próximos invólucros, sem mexer no S5: imprimir também o p95 sem o máximo, ou o número de amostras da base acima de 10× a mediana.

**P-3 (intervalo binomial).** O injetor guarda só o percentual. Com n = 300, 17 % e 20,3333 % dão k = 51 e k = 61 perdas, exatos.

| Par | k/n | p̂ | Wilson 95 % | z contra 0,20 |
|---|---|---|---|---|
| isn3 → isn1 | 51/300 | 0,170 | [0,132; 0,217] | -1,30 |
| isn3 → isn2 | 61/300 | 0,203 | [0,162; 0,253] | +0,14 |
| os dois juntos | 112/600 | 0,187 | [0,158; 0,220] | -0,82 |
| contador netem 30 (fim) | 228/1212 | 0,188 | [0,167; 0,211] | -1,03 |

0,20 cabe nos quatro intervalos. A faixa de 3σ é 60 ± 20,78 = [39,22; 80,78], ou seja, 40 a 80 inteiros. O §5.6 arredondou para fora (39 a 81), como a rastreabilidade (item 5) já registrava. Com 51 e 61 a diferença não importa.

**Filtros u32 e `success`.** Ressalva R-a: o `tc -s filter show` deste kernel não imprime contadores do u32 (`rule hit`/`success`), porque eles só existem com `CONFIG_CLS_U32_PERF`. A leitura literal pedida não é possível. Uso no lugar dela uma evidência equivalente. A `prio` tem `priomap` todo 0, então todo tráfego sem filtro vai para a banda 1:1, e a netem 30 (banda 1:3) só recebe o que os filtros casam. Os contadores da netem 30 crescem a cada amostra: Sent+dropped 193 → 601 → 957 (tc-10/30/50) → 1212 no fim (`04-l1.log`), dropped 31 → 111 → 172 → 228. As razões são 0,161, 0,185, 0,180 e 0,188. Nas três amostras os filtros listados são exatamente os dois esperados, `match 0a590102` (10.89.1.2, isn1) e `0a590103` (10.89.1.3, isn2), com `flowid 1:3`, e nenhum outro. A banda 1:1 levou o resto, 1542 − 785 = 757 pacotes até +50 s, entre eles as respostas ao host, sem perda.

**`TcpRetransSegs` e H-L.** isn3 foi de 0 a 94, isn1 de 0 a 5, isn2 ficou em 0. Isso confere com a H-L: a perda está na saída de isn3, e quem retransmite é isn3. Dos 228 descartes da netem, 112 são pings ICMP do próprio injetor (51 + 61), que não geram retransmissão TCP. Sobram ~116 descartes de TCP, e 94 retransmissões é compatível: descarte de ACK puro não se retransmite, e uma retransmissão pode cobrir mais de um segmento. Os +5 de isn1 são compatíveis com ACKs de isn3 perdidos, que levam isn1 a retransmitir por RTO segmentos que já tinham chegado (retransmissão espúria). isn2 sem retransmissão é plausível se ele mandou menos tráfego a isn3 na janela (isn1 é o coordenador). Os dois números de isn1 e isn2 ficam como `RESULTADO`, como o roteiro previa.

**Vistas e `DEGRADED_MODE`.** `05-vistas.log` mostra 0 linhas de vista ou `DEGRADED` nos 3 nós desde 22:23:11Z. As 188 amostras de saúde da janela estão todas em `HEALTHY`/3. A última vista é a da recriação, `[isn1-15077|2] (3)` (`00-pre.log`). O portão pós deu OK com 317 s de sequência boa. Não houve mudança de vista, então a reclassificação da R-7 da V-s5sh não se aplica.

## 3. Latência das operações Hot Rod na janela

A sonda teve p95 de put de 0,306 s e de get de 0,282 s na janela, contra 6,7 ms e 2,9 ms na base. Explico pela replicação SYNC (DIST_SYNC, `owners=2`, transporte JGroups TCP) passando pelo enlace degradado de isn3:

- **A mediana não mudou** (put 2,5 ms contra 2,3 ms; get 1,7 ms contra 1,4 ms). A maioria das operações não perde nenhum segmento.
- **A fração lenta bate com 20 %.** Acima de 50 ms ficaram 14 de 64 puts (22 %) e 12 de 64 gets (19 %). Por cache, os gets lentos foram 9/32 em `counters` e 3/32 em `sessions`. As duas contagens são compatíveis com binomial(32; 0,2), média 6,4 e desvio 2,3.
- **Os tempos se agrupam no RTO mínimo do Linux.** Dezenove das 26 operações lentas ficam entre 0,19 e 0,33 s, perto de 200 ms do RTO mínimo mais a operação. Três ficam entre 0,38 e 0,70 s, o que corresponde a duas perdas ou a backoff. Quatro ficam entre 0,069 e 0,110 s, o que é compatível com retransmissão rápida ou tail loss probe, quando há tráfego posterior na mesma conexão.
- **A latência volta ao normal na hora do `end`.** As 30 operações de put e as 30 de get depois do `end` têm máximo de 6,8 ms e 3,0 ms.
- **O caminho cliente-isn3 está limpo.** O REST de isn3 e a conexão TCP a 11224 saem pela mesma interface que a sonda e não degradaram (seção 2).

O ponto que não se explica só pela replicação: um get de Hot Rod em isn3 só passa pelo enlace degradado se isn3 **não** for dono da chave `sonda-isn3` naquele cache. Se fosse dono, a leitura seria local. Os gets lentos nos dois caches indicam que isn3 não é dono da chave em nenhum dos dois. Isso é inferência: o bloco não guarda o consistent hash, e não conferi (ressalva R-b). A alternativa seria o get ter ido a um dono remoto por outro motivo, e ela não muda nada nesta validação. A latência de operação continua sendo leitura humana, não critério (P-1 da V-s5sh).

## 4. Ressalvas

- **R-a.** O u32 não tem contador `success` neste kernel. A leitura foi substituída pelos contadores da netem 30 (seção 2). Para os próximos invólucros, registrar a ausência do contador em vez de pedir a leitura.
- **R-b.** A posse da chave `sonda-isn3` foi inferida e não verificada (seção 3).
- **R-c.** A base do REST de controle isn1 tem duas pausas de ~0,37 s. O limiar ficou inflado, mas a conclusão não muda (R-7, seção 2).
- **R-d.** A sonda rodou durante a janela (R-9), uma diferença declarada em relação ao "ocioso" da §5.6, sem efeito sobre a vista.
- **R-e.** O injetor não guarda as linhas `transmitted/received`. As perdas vêm do percentual, que é exato para n = 300 (P-3 da V-s5sh). A faixa 39-81 está arredondada para fora, sem efeito.
- **R-f.** O GO foi dado pelo coordenador, sem o Gestor no terminal, então não há anotação do atraso percebido no ssh do terminal Windows. A medida de dentro da VM (conexão TCP ao sshd) não degradou. O host ficou em H0, e os intervalos máximos do saúde-log foram 2,725 s na base e 2,279 s na janela, abaixo de 4 s.

## 5. Para o S6

- `--bloco-anterior runs/vm-s5-2026-10-28/vm-20261104T221957Z`. O `s6.sh` lê dele `07-boot-fim.txt`, `00-pre.log` (kernel `6.8.0-147-generic`, IDs `d46a19727fdd`/`87816ddc1db0`/`ef69b337005e` e os `StartedAt` de 21:49:59, 21:50:14 e 21:50:51Z) e `08-avaliacao.txt`.
- `--boot-esperado f9e98b74-bf1f-4192-9770-b40285877292`. Se o `boot_id` atual for outro, houve reboot depois do S5: não rodar e chamar o Senior.
- O host continua em silêncio do início ao fim do S6.

## 6. Roteiros atualizados neste commit (só `.md`)

Cumpre a nota do V-diff-v4h. Em `runs/vm-s5-2026-10-28/ROTEIRO-S5.md` (cluster recriado; linha do `boot_id` do "Como rodar") e em `docs/roteiro-vm-2026-11-04.md` (linhas de VM, `boot_id` e Cluster da seção 1), troquei IP `172.27.127.98`, boot `b088606d…` e recriação das 19:14 pelos valores atuais: `172.27.114.62`, `f9e98b74-…`, recriação das 21:49:40Z e vista `[isn1-15077|2] (3)`. Os valores antigos ficam como histórico. Scripts e hashes não foram tocados, e o cabeçalho do `s5.sh` continua citando 19:14 de propósito.
