# V-B: validacao do gate real B-29 (G-0 a G-4) — 2026-10-16

Senior. Entrega do Worker-2: `test/b29-gate-vm` (`dd06d7a` G-0, `aa900fd` G-1). Verificacao feita sobre os brutos de `runs/gate-b29/` e `runs/gate-b29/g1-rep1/`, com o protocolo permanente de autenticidade (plano, secao 4.1: P-1 origem, P-2 recontagem, P-10 zero/uma janela). **Nenhuma injecao foi executada**; na VM so leitura (estado atual abaixo).

## 1. Recontagem (P-2): todos os numeros do relatorio conferem

| Item | Recalculado dos brutos | Relatorio |
|---|---|---|
| marcador start | `1792127463784986725` = 05:11:03,785 UTC | igual |
| marcador end | `1792127559827315241` = 05:12:39,827 UTC | igual |
| duracao start->end | 96,0423 s | 96,04 s |
| t_split isn3 | ISPN000094 `[isn3-5889\|3] (1)` 05:11:43,370 - 05:11:03,785 = 39,585 s | 39,6 s |
| t_split isn2 / isn1 | 05:11:50,198 / 05:11:50,207 -> 46,413 / 46,422 s | 46,4 s |
| t_merge (vista) | ISPN000093 isn2 05:13:26,210, isn1 ,214, isn3 ,216 - end 05:12:39,827 = 46,383 / 46,387 / 46,389 s | 46,4 s |
| t_merge (polling) | `t_merge.txt`: `t_merge_s=48` | 48 s (passo de 2 s + ~1 s de `date`/curl; coerente com 46,4) |
| `saude-antes`/`saude-depois` | 3x `HEALTHY,number_of_nodes:3` em ambos | igual |
| Contadores por filtro (janela.log) | dst(offset 16): 15/22, 29/30, 37/48, 42/52, 48/53; src(offset 12): 5/5, 8/8, 9/9, 19/11, 20/11; monotonicos; final (`inj.log`) dst 53+53=106, src 21+12=33, total 139 | igual |
| Sondas | `isn3->isn1:7800` rc=124 x5; `isn1->isn2:7800` rc=0 x5 | igual |
| REST isn3 | http=200, 4,89 / 4,81 / 4,54 / 3,58 / 3,78 ms | "~4 ms" |
| Saude | +8 e +25 s: HEALTHY/3 nas tres; +45, +65, +82 s: 11222/11223 HEALTHY/2, 11224 DEGRADED/1 | igual |
| `ss` isn3 | 3 conexoes (+8 s, uma com Send-Q 178 para 10.89.1.2:7800), 2 (+25 s), 1 (depois) | igual |

Observacao nova (nao esta no relatorio): o trafego **nao e simetrico em volume**. O injetor imprime "ingress=139, egress=139", mas o real e **ingress (src) 33 pkt e egress (dst) 106 pkt**; os 139 sao a soma de todos os filtros listados duas vezes. Ambos > 0, portanto o criterio G-1 vale, mas o numero 139/139 do injetor nao deve ser citado.

## 2. Relogios e a "discrepancia" 05:13:26

Nao ha discrepancia. O `end` (05:12:39,827) e o **fim da particao** (apos `del clsact`); 05:13:26,2 e a **fusao** que ocorre 46,4 s depois (ciclo do MERGE3 apos a cura). Os 46,4 s sao exatamente a diferenca entre os dois.
- Relogios: marcadores (`date +%s%N`), `janela.log` e logs do Infinispan sao todos da **mesma VM**; um relogio so, sem alinhamento entre maquinas. Relogio da VM x host lido agora: 05:16:55 x 05:16:59 (diferenca de segundos incluindo latencia do ssh); irrelevante, pois nenhuma medida cruza relogios.
- Confirmacao cruzada independente: os `JGRP000006` em isn2 (05:11:14,5; :31,6; :51,7; 05:12:11,8; :28,9) caem 3 s depois de cada cabecalho de amostra (+8, +25, +45, +65, +82 s), que e o `timeout 3` da sonda `isn3->isn1`; isso prova que as amostras foram tomadas nos instantes declarados.
- A leitura de saude da amostra +45 s (cabecalho 05:11:48) so ocorre apos 2 sondas (~05:11:51,7), por isso mostra 2 nos, embora a vista das maiorias seja de 05:11:50,2. Consistente, mas folga de ~1,5 s: nao usar o cabecalho da amostra como instante da leitura.
- A janela de **96,04 s** e a duracao real do corte: o marcador `start` e escrito antes da verificacao TCP (2 pares x `timeout 3` ~ 6 s), depois vem `sleep 90`. Rotular a janela "90 s" e nominal; para T-F usar `end - start` dos marcadores.

## 3. Mensagens (RAST-G3-1): conferidas nos `log-completo-*` (P-1)

| Codigo | Onde | Conferido |
|---|---|---|
| ISPN000094 | isn3 `[isn3-5889\|3] (1)` 05:11:43,370 (l.20); isn2/isn1 `[isn2-19096\|3] (2)` 05:11:50,198/,207 | sim |
| ISPN000319 (ERROR) | isn3, `sessions` e `counters`, 05:11:43,385 (l.26-27), "must stay in degraded mode ... Current members are [isn3-5889], lost members are [isn2, isn1]" | sim; **na divisao** |
| ISPN100011 | isn3 `DEGRADED_MODE, topology id 11` 05:11:43,389 (`counters`, `sessions`); isn2 `AVAILABLE, topology id 10` 05:11:50,206/,235 | sim. **Correcao:** so isn2 (coordenador) loga o AVAILABLE; isn1 nao tem a linha (o relatorio diz "isn1/isn2") |
| ISPN100007 "After merge" | isn3 na **divisao** (05:11:43,385 em diante, 7 contextos) e isn2 na fusao | sim; rotulo enganoso |
| ISPN000093 | isn2 05:13:26,210; isn1 ,214; isn3 ,216; `MergeView::[isn2-19096\|4] (3)` | sim |
| ISPN000517 | **so isn2** (coordenador), 05:13:26,444-,468, `id=11` de isn3 descartada | sim. **Correcao:** nao aparece em isn1 |
| JGRP000011 | isn3 05:13:26,212, descarte de isn2 como nao-membro | sim |
| JGRP000006 | artefato das sondas (isn2 x5) | sim |
| ISPN012026 | 101/101/111 ocorrencias (ruido) | sim |

**Limite essencial:** todas sao mensagens de **servidor**. O `ClassificadorDeErro` do workload classifica **excecoes do cliente Hot Rod**; a classe/mensagem/causas dessa excecao **nao foram capturadas**. O JSON de saude tambem so foi guardado como fragmento `"health_status":"X","number_of_nodes":N` (grep de `g-lib.sh`), nao o JSON completo; `cache_health[].cache_name/status`, que o `HealthRest` le, nao esta nos brutos. Logo C-2/RAST-G3-1 **nao esta fechado** por este gate.

### Tarefa para preencher o esqueleto C-2 (Worker-4, depois de captura)
1. **Captura (VM, exige go do Gestor, 1 janela curta ou a REP=2 de G-4):** durante a particao de isn3, com cliente Hot Rod apontado so a isn3 (`ClientIntelligence.BASIC`, sem failover para outros nos), executar `get`, `put`, `remove`, `entrySet`/`keySet` em `sessions` e `counters`; gravar classe, mensagem e cadeia de causas completas (stack) em `runs/gate-b29/g1-rep2/hotrod-excecao-*.txt`; e o JSON **integral** de `/rest/v2/cache-managers/default/health` em isn3 (DEGRADED) e em isn2.
2. **Marcas candidatas (so confirmar com a captura):** do servidor, a assinatura e `ISPN100011 ... DEGRADED_MODE` + `ISPN000319`; do cliente, esperar `AvailabilityException`/ISPN000306 como causa. Acrescentar a `MARCAS_DE_INDISPONIBILIDADE` apenas o que a captura mostrar; `ISPN000319` e `DEGRADED_MODE` sao de log e nao de excecao, nao servem como marcas no cliente.
3. **HealthRest:** deve tratar `number_of_nodes=1` + status de `sessions`/`counters` `DEGRADED` em isn3 como "no na minoria", e `number_of_nodes=2` + `HEALTHY` nas maiorias como esperado (a maioria nao muda de status). Teste: JSON real de isn3 (DEGRADED/1), de isn2 (HEALTHY/2) e de apos-fusao (HEALTHY/3), colados como fixtures.
4. **ClassificadorMensagemRealTest:** preencher `CLASSE_CAPTURADA`/`MENSAGEM_CAPTURADA` com a captura, construir a excecao com as mesmas causas, afirmar `ERROR_UNAVAILABLE`; manter o caso negativo (timeout/`TransportException` real nao vira UNAVAILABLE); remover os dois `@Disabled`. TDD: o teste falha com a lista de marcas atual se a captura trouxer texto fora dela.

## 4. Hot Rod em isn3 durante a particao: lacuna

O gate mediu **REST** (`:11224`, http=200 em ~4 ms), nao Hot Rod. REST 200 prova que a porta aceita e o servico de saude responde, nada sobre o Hot Rod. Com `DENY_READ_WRITES` e isn3 em `DEGRADED_MODE`, espera-se que `get/put` em chaves cujos donos nao estao na particao falhem com erro de disponibilidade (nao timeout), mas **isso nao foi medido**. O criterio G-1 do plano ("Hot Rod a isn3 devolve erro, nao timeout") fica **nao verificado**. Lacuna a cobrir antes do smoke do S3 (T-F item 0) ou na REP=2; sem isso H-L7 nao pode ser alegada.

## 5. O que o gate prova e nao prova; honestidade dos veredictos

Prova (uma janela, cluster ocioso, minoria de 1): `clsact+u32+drop` funcionam no netns de isn3 com kernel 6.8; corte nos dois sentidos (src 33 e dst 106 pkt); isn1<->isn2 intactos; host e REST de isn3 intactos; IPv4-only (G-0); limpeza e exit 0; dead-man armado e desarmado; vistas de 1 e 2, DEGRADED na minoria, fusao para 3 sem intervencao.
Nao prova: variancia de t_split/t_merge; dependencia da carga; perda/divergencia de dados no merge (ISPN000517 descarta a topologia da minoria; efeito nao medido); particao de outro no (coordenador isn2 ou isn1), 2x1 diferente ou sem maioria; Hot Rod; sinais (G-5); coexistencia com F3 (G-6). "SSH da VM responde" e verdadeiro por construcao (o u32 so existe no netns de isn3) mas nao foi registrado dentro da janela.

Veredictos por criterio: G-0 **PASS**; G-1 **PASS PARCIAL** (falta Hot Rod); G-2 **PASS**; G-3 **PASS** para o observado no servidor (uma janela) e **PARCIAL** para o mapa de mensagens do cliente; **G-4 PASS PARCIAL (1 de 3 janelas)**: o criterio do plano e >= 3 com minimo, mediana e maximo. Escrever "G-3/G-4 PASS" sem qualificar viola P-10; o relatorio do Worker deve ser lido como PARCIAL em G-4.

## 6. Achados e escopo minimo antes do experimento (TDD)

| # | Achado | Decisao | Branch / teste |
|---|---|---|---|
| A1 | `soma_pkts` cego a sentido (l.451-467): `show ingress` e `show egress` listam os dois filtros; "um so sentido" nunca avisa; log "139/139" enganoso | **Corrigir antes do experimento** (T-F usa o resumo do injetor como evidencia) | `fix/b29-contadores-por-sentido`; novo T-par com `tc` falso que lista os dois filtros em ambos os hooks, um com contador 0: hoje nao avisa, deve avisar; separar por `match ... at 12|16` como em `cont_filtros` |
| A2 | `inject-partition.sh` sem guarda de netns (so `inject-netdegrade.sh` tem) | **Corrigir antes de T-D/T-F** (T-D roda o injetor automatizado; protege o SSH da VM) | `fix/b29-guarda-netns-particao`; reaproveitar o shim de `readlink` de T-nd-21; T-par: pid cujo netns == o do PID 1 -> aborta com exit nao zero sem chamar `tc` |
| A3 | `TIMESTAMP` congelado (l.64, usado em l.206): `inj.log` inteiro marca 05:11:03Z | Corrigir junto com A1 (1 linha: `date` em `log()`); forense de T-F depende | mesma branch de A1, T-par: duas chamadas de `log` com `sleep 1` entre elas diferem |
| A4 | `g1.sh` extrai `log-vista-*` antes da fusao | Nao exige fix do injetor; **corrigir `g1.sh` antes de REP=2/3** (extrair apos `t_merge`, com `--since`; ja existem `log-completo-*` que cobrem) | commit em `test/b29-gate-vm`; ajuste de roteiro, nao de spec |
| A5 | `g1.sh` sem sonda Hot Rod | Acrescentar captura Hot Rod/JSON integral (secao 3) | idem, depende de um cliente Hot Rod pequeno (tarefa C-2) |
| A6 | Relatorio: ISPN000517/ISPN100011-AVAILABLE so em isn2 | Corrigir texto (nota na secao 3 deste documento) | docs |

A1-A3 sao pequenos, independentes de VM, e entram numa unica rodada do Worker-2 (duas branches, uma por responsabilidade), com os 101 testes T-par atuais intactos (+ novos).

## 7. Estado da VM (somente leitura, 05:16 UTC)

11222/11223/11224: `HEALTHY`, `number_of_nodes":3`; `qdisc noqueue 0: root` em isn1, isn2 e isn3 (sem clsact); 0 timers `tcc-heal-*`. Sem residuo.

## 8. Veredito V-B: **APROVADA COM RESSALVAS**

- Numeros, relogios e mensagens do relatorio conferem com os brutos; o metodo (netns + dead-man + tmux + flock + go) funcionou limpo. Autenticidade (P-1/P-2): ok.
- **T-C2 (gate L) pode ser liberada**: a VM, o metodo SSH, o `nsenter`, o dead-man e o clsact/u32 estao provados; o gate L usa `prio/netem` (nota: `/usr/lib/tc/normal.dist` nao existe, relevante para `distribution normal`) e exige go proprio do Gestor antes de L-1.
- **Nao liberar** T-D (integracao no `run-baseline.sh`) e T-F antes de: A1-A3 corrigidos e G-5 executado; T-F tambem exige a captura Hot Rod e C-2.
- **"Go" do Gestor ainda faltantes:** G-5 (`kill -TERM`/`-KILL`, deixa isn3 cortado ate o `--heal`; usar duracao de 30-40 s); G-6 (so apos G-5, ciente de F3); G-4 REP=2 e REP=3 (cada uma e particao real de ~96 s; a REP=2 deve incluir a captura Hot Rod/JSON integral e usar `g1.sh` corrigido); go separado para L-1 no T-C2. Sugestao opcional: uma das repeticoes com carga leve de escrita em `sessions`, para dar o primeiro dado sobre o efeito do merge nos dados (hoje nao medido).
