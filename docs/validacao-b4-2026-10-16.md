# Validacao V-B4: execucao do gate v3 na VM (REP=3, G-5, G-6), 2026-10-16

Senior. Base: relatorio do Worker-2 (`docs/relatorio-tb-v3-execucao-2026-10-16.md`, commit 8cc1349), brutos em `runs/gate-b29/` da worktree `wt-gate-v3`, recalculados por mim (epoch ns dos marcadores contra logs de container, CSV da sonda, JSONs de saude). Nada foi executado na VM; nenhum script ou codigo foi alterado.

## Veredito: T-B APROVADA COM RESSALVAS

A execucao e fiel e os numeros do relatorio batem, com **tres correcoes menores** (secao 1) e **um criterio meu (t_merge pelo saude-log) que nao foi cumprido na letra** (secao 2). A REP=3 e valida e cai na faixa "<= 60 s". H2 e H3 continuam nao separadas.

## 1. REP=3: numeros reconferidos

| Item | Relatorio | Recalculado nos brutos | Estado |
|---|---|---|---|
| Janela | 96,046 s | end - start = 1792141095575688010 - 1792140999529654856 = 96,046 s | confere |
| t_split maioria / isn3 | +46,4 / +39,8 s | vista `[isn2\|3] (2)` 08:57:25,975-,982; isn3 08:57:19,340 e DEGRADED 19,352 contra start 08:56:39,530 | confere |
| MergeView | +46,41 s do end | 08:59:01,984-,989 menos 08:58:15,576 | confere |
| Finished rebalance | +46,6 s | ultimo (counters/sessions) 08:59:02,164-,173 = +46,59 s | confere |
| `t_merge.txt` | 46 | `t_merge_s=46` | confere |
| ISPN000524 / 100014 / 000638 | 0 | 0 nos 3 logs | confere |
| Filtros | ingress 33, egress 439 | 21+12 e 125+314 em `inj.log` | confere |
| Idade do cluster | 120,8 s | containers 08:54:38,76 ate start 08:56:39,53 | confere |
| Recriacao | HEALTHY/3 em +4 s, estavel 20 s | `rec1/validacao.txt` (total 24 s) | confere |
| REST de isn3 na janela | "5 amostras, 4-7 ms" | o g1b coletou **20** `/health` de isn3 na janela (a cada 5 s), todos http=200, **3,3 a 7,2 ms** | corrigido (e mais forte) |
| Excecoes Hot Rod em isn3 | 12 timeouts (+0,9 a +39,1 s) e 220 imediatos | **11 timeouts** (+0,9 a **+37,0 s**: 7 sessions, 4 counters; `TransportException` x8 de 4,0 s em put/get/remove, `RuntimeException` x3 de 2,0 s no entrySet) e **221 erros de disponibilidade**; total 232 (igual). O "12o timeout" e `sessions remove` iniciado em +39,05 s que esperou 0,79 s e terminou em +39,84 s com ISPN000306, no mesmo instante do DEGRADED_MODE (+39,82 s) | corrigido |
| OK | 50 a partir de +145,4 s | 50 (de +145,44 a +175,27 s; inclui 2 `remove-final`) | confere |

Leitura adicional (nova): a fronteira de `size=2` para `size=3` aparece tambem nos `/health` de 11222 coletados pelo g1b: `HEALTHY/2` em end+44,04 s e `HEALTHY/3` em end+49,06 s. Combinado com o instante do `MergeView` (46,41) e do ultimo `Finished rebalance` (46,59), com a ultima amostra do saude-log (HEALTHY/2 em end+46,19 s) e com `t_merge.txt` = 46 (floor de uma deteccao feita no laco `until` do proprio g1.sh), **t_merge de REP=3 fica em [46,6 ; 47,0) s**. O "~48 s" citado na tarefa e o valor da REP1; a REP=3 e ~47 s.

## 2. O defeito do saude-log: viola meu criterio?

Fatos: `g1.sh` executa `until health ... size=3; sleep 2` e so entao chama `parar_log_saude`. O log para no ciclo seguinte a deteccao. A ultima linha do `saude-log.csv` e `HEALTHY/2` em end+46,19 s, **anterior** ao MergeView (46,41 s). Logo existem 0 amostras HEALTHY/3 depois do end.

Avaliacao:
- **Meu criterio da V-B3 (secao 6) dizia "primeiro HEALTHY size=3 no saude-log, conferir com `t_merge.txt`". A primeira metade nao e calculavel; a segunda foi cumprida.** Isto nao e falha de execucao do Worker-2: o desenho do g1.sh (que eu aprovei na V-B3) para o log exatamente na primeira deteccao, e eu nao notei que isso tornava o criterio inalcancavel. O erro e meu e do script, nao da VM.
- A classificacao **se sustenta**, por quatro fontes independentes do saude-log (secao 1): MergeView, Finished rebalance, `/health` de 11222/g1b (2 pontos que cercam o evento) e `t_merge.txt`. A faixa "<= 60 s" tem folga de 13 s sobre o pior limite; o erro da medida e < 2 s. Nao ha ambiguidade na classificacao.
- O que o log curto **impede**: provar que o cluster nao oscila (HEALTHY/3 -> degradado) logo depois da fusao, e medir t_merge por porta com resolucao de 2 s. Nao e relevante para esta REP, e relevante para a REP=4.

Exigencia (antes de qualquer REP futura): corrigir `g1.sh` para **continuar o log por X s depois de size=3** (sugestao: variavel `G1_LOG_POS_S`, padrao 30; `parar_log_saude` apos `sleep` de X; manter `G1_LOG_SAUDE_S=0` desligando tudo) e acrescentar teste T-g1-8 (shim que devolve size=3 e confere que ha linhas HEALTHY/3 apos o t_merge). `t_merge.txt` deve passar a registrar tambem o instante da primeira leitura HEALTHY/3 em ns, para dispensar o parse. Tarefa do Worker, depois V-B5 curta, depois novo bundle (go do Gestor).

## 3. G-5: PASS, com a delimitacao do que nao foi exercitado

Conferido em `g5/resultado.txt` (10 linhas PASS/size=3, sem FAIL/ABORTADO; `abort-heal.log` ausente): TERM rc=143 e KILL rc=137 (`rc-term.txt`/`rc-kill.txt`); `estado-kill.txt` mostra `qdisc clsact ffff:` presente com os filtros (22/13 pkt egress, 7/1 ingress) antes do KILL e `qdisc-pos-heal.txt` limpo; `heal.log` 09:02:39 "clsact removido"; size=3 HEALTHY "apos 0 s" nos dois. Criterio de PASS da V-B3 secao 6: **atendido integralmente**.

NAO exercitado (nao aproveitar G-5 para alegar):
- O **dead-man disparando de fato** (timer systemd chegando ao fim e removendo o clsact). Em G-5, G-6 e REP=3 ele foi armado e desarmado; em R3 so se provou o corpo inerte com container inexistente (nao o caminho que toca `tc`).
- Heal depois de uma particao com mudanca de vista: janela de 40 s < deteccao (~40 s), `log-vista-*` vazios. O KILL ocorreu sem split.
- KILL do `systemd-run` ou do shell pai; falha de `sudo`/`nsenter`.

Impacto: o dead-man continua uma defesa **nao medida no caminho de disparo**. Aceitavel porque cada execucao teve `--heal` manual e preflight final; nao aceitavel para alegar "o ambiente se autolimpa". Recomendacao: um G-5b opcional e curto (dead-man com TTL de 30 s e `kill -KILL` do injetor, esperar o disparo), a decidir pelo Gestor; nao bloqueia T-B.

## 4. G-6: PASS honesto, com limites

Recalculado: F2 start 09:03:48,190, end 09:05:24,234; maioria `[isn2|5] (2)` 09:04:29,984 = **+41,79 s**; isn3 09:04:31,199 = +43,01 s; MergeView 09:06:05,987 = **+41,75 s** do end; 3 amostras validas (+10, +45, +70 s), 0 PULADA; `estado-pos-f2.txt` mostra isn3 `noqueue` e isn2 `netem 8002: delay 20ms 13ms` logo apos o fim da F2 (a limpeza da F2 nao removeu o netem de isn2); F3 ligado ate 09:05:56,89 (a fusao ocorreu **durante** o jitter); size=3 HEALTHY "apos 45 s" do fim da F2; 0 ocorrencias de ISPN000524/100014/000638. Criterio da V-B3 atendido.

Honestidade: PASS do **mecanismo de composicao** (F3 e F2 coexistem e cada limpeza toca so o seu no) e **nao** uma medida do G-4: ha jitter, nao ha sonda nem coleta. Um dado util e colateral: G-6 foi a particao seguinte na mesma instancia (REP=3 foi a anterior; topologia 23 na janela contra 10-11 da REP=3) e fundiu em 41,75 s sem sonda, com historico de 1 particao previa. Isso nao refuta H3 (REP2 foi a 3a particao, com 2 previas), apenas mostra que 1 particao previa nao basta.

## 5. REP=1, REP=2, REP=3: o que se afirma e o que nao

| Estrato | REP | t_merge | Condicoes |
|---|---|---|---|
| cluster novo, ocioso | REP=1 | 48 s | cluster recem-criado (topologia 10-11), ocioso; instrumentacao conforme o relatorio da REP1 (nao rechecada nesta validacao) |
| cluster novo, ocioso | REP=3 | ~47 s ([46,6 ; 47,0)) | cluster recriado (120,8 s), sonda Hot Rod minima, `/health` a 5 s, sem `/caches`/stats/availability |
| cluster com historico + coleta completa | REP=2 | 188 s | 3a particao da instancia (topologia 22-23), sonda + `/caches`, `?action=stats`, `?action=get-availability`, ISPN000524 a 60 s em counters, ISPN000638 x27 |

Hipoteses (V-B2/V-B3):
- **H1 (sonda em counters)**: a REP=3 manteve a sonda, inclusive em `counters`, e **nao reproduziu** nem timeout de 60 s na resolucao de conflitos (conflito resolvido em ~165 ms) nem ISPN000638. Em REP=3 `counters` devolveu, apos o DEGRADED, **erro imediato** ISPN000306/305 (221 ocorrencias, duracao ~0 s), enquanto em REP=2 `counters` ficou em timeout por toda a janela. Leitura: a operacao da sonda sobre `counters` e tratada normalmente por um cluster em estado limpo; o timeout continuo em REP=2 e **sintoma** de `counters` preso a topologia 22 (estado do cache), nao da operacao em si. **O que se pode afirmar**: a sonda minima em `counters` e **insuficiente** para causar o atraso em cluster novo. **O que NAO se pode**: que a sonda seja inocente em geral; uma interacao sonda x historico (comandos pendentes em estado ja degradado) continua possivel.
- **H2 (coleta REST completa)** e **H3 (historico)**: REP=3 mudou cluster **e** coleta ao mesmo tempo, logo **nao separa** as duas. Ambas seguem compativeis com os dados. Uma observacao a favor de H2: o REST de isn3 respondeu 200 em 3-7 ms durante toda a janela na REP=3 (em REP=2, 52/60 pendurados), o que indica que a coleta pesada, e nao o `/health`, foi o que pendurou o REST de isn3 na REP=2; mas isso diz de que o REST pendurado e consequencia, nao de que causou a lentidao.
- O que se afirma, sem exagero: **existe um estrato (cluster novo, ocioso, sonda minima) com t_merge de 46-48 s em 2 janelas (REP1, REP3)**; uma janela no estrato "historico + coleta completa" deu 188 s. Dispersao maior que 4x entre estratos, sem causa isolada. N=1 por estrato (REP1 e REP3 formam N=2 no estrato novo).

Classificacao atualizada (a usar em T-C2/T-F e no Cap. 4/5): REP=1 e REP=3 = estrato "cluster novo, ocioso"; REP=2 = estrato "cluster com historico + coleta completa", **preservada, nao descartada, nao agregada**.

## 6. REP=4: decisao e sequencia minima

Uma REP=4 "recriar + sem sonda + G1_LOG_SAUDE_S=0" **agrega pouco**: seria a terceira janela do estrato novo e confirmaria H1-fraca, mas nao separa H2 de H3 e ainda perde o log. Separar H2/H3 exige variar **um** fator por vez:

| Variante | Fatores | Se lenta (>= 150 s ou ISPN000524) | Se rapida (~47 s) |
|---|---|---|---|
| **B (recomendada, primeira)**: particao no cluster **atual**, sem recriar, `COLETA=minima`, sonda minima como na REP=3, log corrigido | historico (agora 2 particoes previas: REP=3 e G-6; topologia ja em 23+, nivel da REP=2) | H3 sustentada, H2 excluida como causa necessaria | H3 enfraquecida; H2 vira a explicacao mais provavel, ainda nao provada |
| C (so se B rapida e se quiser fechar H2): recriar + `COLETA=completa` + sonda, como na REP=2 | coleta | H2 sustentada | H3 favorecida |
| A (recriar, sem sonda, log corrigido) | sonda | (H1 reaberta) | terceira janela do estrato novo; so para completar "3 janelas comparaveis" do G-4 |

Sequencia minima: **(1) corrigir g1.sh (secao 2) -> (2) V-B5 curta -> (3) REP=4 variante B (1 execucao, ~6 min, sem recriar) -> (4) A somente se B for lenta** (para ter a 3a janela do estrato novo) **ou** se o Gestor exigir 3 janelas puras para o G-4; C opcional, so se o Gestor quiser fechar H2. Para o TCC, B ja produz o resultado informativo; o restante e custo crescente por pouco retorno.

Go necessario: B exige (a) autorizar o novo bundle na VM e (b) `go G-1 (REP=4 ...)` digitado pelo Gestor (`exigir_go`). **Nao** exige "go" de recriacao. A e C exigem tambem go de recriar o cluster. L-0/L-1 seguem com go proprio.

## 7. Material para o C-2 (excecoes reais do cliente Hot Rod, REP=3, isn3, `sonda-isn3.csv`)

| Fase (desde o start) | Cache | Op | Classe do topo | Mensagem (real) | Causas | Qtd | Duracao |
|---|---|---|---|---|---|---|---|
| +0,9 a +33 s (pre-deteccao) | sessions | put/get/remove | `org.infinispan.client.hotrod.exceptions.TransportException` | `java.net.SocketTimeoutException: <Op>Operation{sessions, key=sonda-isn3, ..., connection=127.0.0.1/127.0.0.1:11224} timed out after 2000 ms` | `SocketTimeoutException` (mesma mensagem) | 5 | ~4,0 s |
| +15 a +25 s | counters | put/get/remove | `TransportException` | idem, `<Op>Operation{counters,...}` | idem | 3 | ~4,0 s |
| +8,9 e +37,0 s | sessions | entrySet | `java.lang.RuntimeException` | `java.net.SocketTimeoutException: IterationNextOperation{sessions,...} timed out after 2000 ms` | (ver CSV) | 2 | ~2,0 s |
| +23,0 s | counters | entrySet | `RuntimeException` | idem, counters | idem | 1 | ~2,0 s |
| +39,05 a +140,5 s (DEGRADED) | sessions e counters | put/get/remove | `org.infinispan.client.hotrod.exceptions.HotRodClientException` | `org.infinispan.partitionhandling.AvailabilityException: ISPN000306: Key 'WrappedByteArray[...]' is not available. Not all owners are in this partition` | vazia (`causas` = "") | 166 | 0,00-0,02 s (um de 0,79 s, o que atravessou a deteccao) |
| +39,8 a +140,5 s | sessions e counters | entrySet | `HotRodClientException` | `org.infinispan.partitionhandling.AvailabilityException: ISPN000305: Cluster is operating in degraded mode because of node failures.` | vazia | 55 | ~0 s |
| +145,4 a +175,3 s | ambos | todas | OK | | | 50 | |

Quantidade por classe: `HotRodClientException` 221, `TransportException` 8, `RuntimeException` 3 = 232. Isn1 (maioria): 0 erros em 282 linhas. A **classe de topo da indisponibilidade e `HotRodClientException`**, nao `AvailabilityException`: o nome `AvailabilityException` so existe **dentro da mensagem**. Nao ha cadeia de causas (coluna vazia) nesse caso.

O que `ClassificadorDeErro` deve reconhecer (e o que ja reconhece, lido no codigo): `HotRodClientException` (nao e `TransportException`, `HotRodTimeoutException`, `IOException`, `TimeoutException`) cuja **mensagem** contem `AvailabilityException`/`ISPN000306`/`degraded mode` -> `ERROR_UNAVAILABLE`; `TransportException` (SocketTimeout) -> `ERROR_TRANSPORT`; `RuntimeException` do `entrySet` cuja mensagem e `java.net.SocketTimeoutException: IterationNextOperation... timed out` **e nao tem marca** -> cai no padrao `ERROR_TRANSPORT`. Risco: depende de a mensagem nao conter "DEGRADED"/"not available" (a captura real nao contem; as chaves ficam em `WrappedByteArray[...]`). O `HealthRest` deve reconhecer o JSON real: `cluster_health.health_status` (`HEALTHY`/`DEGRADED`), `cluster_health.number_of_nodes`, `cache_health[].status` (`DEGRADED` em `sessions`/`counters`); `HEALTHY_REBALANCING` apareceu na REP=2 (2 amostras) e em 0 amostras na REP=3, entao precisa de fixture da REP=2.

Tarefa C-2 (para o Worker, apos aprovacao do Gestor): (1) copiar para `ClassificadorMensagemRealTest` tres fixtures reais: timeout `TransportException`+SocketTimeout (put), `RuntimeException`+SocketTimeout (entrySet/IterationNext), `HotRodClientException` com ISPN000306 (put) e com ISPN000305 (entrySet); (2) preencher `CLASSE_CAPTURADA`/`MENSAGEM_CAPTURADA`/`OPERACAO_CAPTURADA` e converter o teste unico em `@ParameterizedTest` com os 4 casos; remover `@Disabled` de `excecaoRealDeNoDegradedViraErrorUnavailable`; (3) decidir `esperadoIndisponivel` das 7 linhas de log conhecidas e remover o 2o `@Disabled`; (4) fixtures JSON reais de `saude-11224-1792141044160696554.json` (DEGRADED/1), `saude-11222-1792141139612494120.json` ou o `.../...144638...` (HEALTHY/2, HEALTHY/3) e o JSON HEALTHY_REBALANCING da REP=2 para `HealthRest`; remover o 3o `@Disabled`; (5) caso "timeout puro -> ERROR_TRANSPORT" (exige que o texto "timed out after 2000 ms" nao case com marcas) **e** "AvailabilityException -> ERROR_UNAVAILABLE" lado a lado, com um caso negativo adicional em que a mensagem do timeout inclui "not available" (continua TRANSPORT quando a classe e `TransportException`; documentar o buraco da `RuntimeException` do `entrySet`, que nao e protegida por `ehTransporte` e seria promovida). Esse buraco e o unico ponto onde eu recomendo ajuste de codigo (testar primeiro, TDD).

## 8. O que o gate B-29 prova no total (G-0 a G-6)

| G | Prova | NAO prova |
|---|---|---|
| G-0 | pre-voo da VM e do cluster (nao rechecado aqui; ver relatorio do Worker anterior) | desempenho |
| G-1 / G-4 (REP1, REP3) | particao F2 em isn3 e reforma: split ~40-46 s, merge ~47-48 s, sem ISPN000524, resolucao de conflito ~165 ms, em cluster novo e ocioso; contadores de filtro > 0 | que o merge seja <= 60 s **sempre**: a REP2 (188 s) mostra o contrario no estrato com historico + coleta completa |
| G-4 (REP2) | existe cauda lenta, com ISPN000524 e ISPN000638, cuja causa nao foi isolada | a causa (H1/H2/H3) |
| G-3 (Hot Rod real) | o cliente distingue timeout (`TransportException`/`RuntimeException`, 2-4 s) de recusa (`HotRodClientException` + ISPN000306/305, ~0 s) em sessions e counters | cobertura de outras operacoes (`putIfAbsent`, `replace`, `compute`, transacoes) nem outros clientes |
| G-5 | trap/heal do injetor: TERM e KILL deixam o ambiente recuperavel | o disparo do dead-man; heal apos split real |
| G-6 | F3 (jitter em isn2) e F2 (particao em isn3) compoem sem se atropelar; split +41,8 s, merge +41,75 s | efeito de jitter alto, outros nos, perda de pacotes (L-0/L-1) |

## 9. T-B: APROVADA COM RESSALVAS; T-C2 e T-D liberadas com condicoes

Ressalvas: (a) corrigir g1.sh (log pos-size=3) antes de qualquer REP futura; (b) classificar por estrato como na secao 5; (c) H2/H3 declaradas **nao separadas** em qualquer texto academico; (d) dead-man disparando de fato nao provado (G-5b opcional); (e) correcao de 3 numeros no relatorio do Worker (secao 1: 11 timeouts ate +37,0 s, 221 erros imediatos, 20 amostras de REST).

Libera: **T-C2** (ligar o `ClassificadorMensagemRealTest` com as fixtures da secao 7, sem VM, nao exige go); **L-0/L-1 exigem go explicito do Gestor** e, antes, a decisao sobre o G-5b. **T-D** pode comecar em paralelo (nao depende de nova medida). A REP=4-B e opcional para o gate B-29 e recomendada para a narrativa do TCC; nao bloqueia T-C2 nem T-D.
