# B-44.3 — AC-6: exclusão forçada por `GMS.suspect` via *probe* do JGroups

Data: 2026-11-05. Branch `test/b44-3-opcoes-jgroups` (de `12c1786`). Especificação: seção 8 e seções 2.3 e 2.4 de
`docs/opcoes-arquitetura-configuracao-2026-11-05.md` (branch `docs/plano-implementacao-controlador`, `155aa95`).
Isto é verificação, não atuador: `controle/` e o atuador não foram tocados. O *cluster* local é Podman, projeto
`b443` (REST/Hot Rod nas portas 12444..12446, *probe* nas 12447..12449), imagem
`quay.io/infinispan/server:15.0.21.Final`. Sem VM, sem push.

A execução teve duas janelas. A primeira foi das 05:29 às 05:55 UTC e parou para a noite 1 da VM; a
`V3-async-acao2` ficou **inválida**, porque a máquina Podman parou entre o corte e o *probe*. A segunda foi a
janela H2, das 08:34 às 09:17 UTC. Os brutos estão em `runs/b44-3/` (`MANIFEST.sha256`). CSV, `.log` e `.txt`
ficam fora do Git pelo `.gitignore` (D-005), mas estão no disco e no manifesto. As tabelas derivadas estão em
`runs/b44-3/analise/` e os roteiros em `runs/b44-3/scripts/`.

## 1. Veredito

**A AC-6 passa nos critérios de entrada (a), (b), (c) e (e) e no limite de tempo.** Há duas ressalvas para o
Senior: o falso positivo não é inofensivo em ASYNC (V-5) e o resumo do *passcode* não protege contra repetição (V-6).

| Critério | Resultado | Números | Evidência |
|---|---|---|---|
| (a) AC-6 funciona nos dois lados sob corte | **sim**, 7 de 7 (4 SYNC, 3 ASYNC; `isn3` não coordenador) | vista de 2 na maioria: mediana 24 ms, máx. 321 ms; vista de 1 no `isn3`: mediana 18 ms, máx. 35 ms; `isn3` em `DEGRADED_MODE` nos dois *caches*: mediana 25 ms, máx. 80 ms; 1ª recusa Hot Rod no `isn3`: mediana 27 ms, máx. 90 ms | `analise/marcos.tsv`, `analise/agrega.tsv`, `servidores/V3-*`, `servidores/INV-*` |
| (b) suspeito é o coordenador | **sim** (V-4, n = 1; mais V-5b maioria e V-5 com `isn3` coordenador) | o próximo da fila (`isn1`) assume e instala a vista de 2 em 15 e 20 ms; o `isn3` fica sozinho só em **2,25 s** (vista intermediária `{isn3, isn1}` e espera de confirmação de 2 s) | `servidores/V4-async-acao/`, `probe/acao-V4-async-acao.tsv` |
| (c) o falso positivo funde sozinho | **sim**, 2 de 2, mas em **50,0 s** depois do envio, não em 10 a 30 s. **Em ASYNC, a fusão reverteu um logout** (4 × I1) e deixou réplicas divergentes até o fim (1 × I5); o controle teve 0 | recusas no `isn3` até a fusão: 122 (ASYNC) e 155 (SYNC), contra 0 nos controles | `carga/V5-async-acao/violacoes-rep-001.csv`, `analise/marcos.tsv` |
| (d) detector em execução sem efeito | **sim** | `FD_ALL3.timeout=10000` gravado e lido nos 3; `num_bits` continuou 5; T_det de 46,1 s (maioria) e 46,7 s (`isn3`), contra 42,8 a 48,2 s nos 8 controles com corte | `probe/V2-*.tsv`, `servidores/V2/` |
| (e) `diag` ligado não muda a carga sem falha | **sim** (n = 1 por condição) | 0 erros e 0 violações nos 4; p50 +0,26 a +0,35 ms, dentro da faixa de 1,86 a 2,70 ms do B-44.1/1b | `analise/resumo.tsv`, `carga/V1-*` |
| Limite `k·W + t_probe + t_vista < 39,8 s` | **passa** | com `isn3` não coordenador: 0,323 + 0,321 s ⇒ **`k·W < 39,16 s`**; com `isn3` coordenador: 2,247 + 2,245 s ⇒ **`k·W < 35,3 s`** (os dois tempos contam do mesmo instante; pelo maior, `k·W < 37,55 s`) | idem (a) e (b) |
| V-5b, um lado só | **sem piora** | violações: controle 15 (I2), só maioria 0, só `isn3` 0 | `analise/resumo.tsv` |
| V-6, *passcode* | **funciona como trava**, mas **aceita repetição** | sem resumo: recusado; resumo certo: aceito; *passcode* errado: recusado; `t1` de 01/01/2000: **aceito**; `GMS.suspect` sem resumo: recusado e vistas intactas | `probe/V6.tsv`, `servidores/V6/` |

## 2. Configuração (passo 0)

- **Pilha JGroups.** `runs/b44-3/cluster/jgroups-tcp-diag.xml` é `cluster/jgroups-tcp.xml` com `diag.enabled="true"`,
  `diag.enable_tcp="true"`, `diag.enable_udp="false"`, `diag.port="7500"` e `diag.port_range="0"`. Não usei
  `diag.bind_addr`: o `ServerSocket` liga em todas as interfaces e o encaminhamento do Podman alcança. A
  `jgroups-tcp-diag-pass.xml` acrescenta `diag.passcode` (valor de laboratório).
- **Portas (desvio do prompt).** A porta 7500 de cada nó foi publicada em 12447..12449, não em 7500..7502, para
  não colidir com outro Worker.
- **Ordem de subida.** Os nós sobem um a um (`sobe` em `scripts/lib.sh`): `isn1`, `isn2`, `isn3`; no V-4, `isn3`,
  `isn1`, `isn2`. Nomes lógicos, coordenador, PID e IP de cada encarnação estão nas linhas `nomes` de `fases.tsv`.
- **A fusão pode trocar o coordenador.** O líder da fusão vira coordenador do *cluster* (`analise/fusao.tsv`,
  coluna `lider_da_fusao`). Por isso, a partir de `V3-async-ctl2`, o `isn3` era o coordenador nas execuções do
  lote 2 (V-5, V-5b). A coluna `coord_antes_t0(sonda)` de `marcos.tsv` registra o coordenador de cada execução,
  lido pela REST antes de t0. As execuções de investigação garantiram o `isn1` como coordenador
  (`garante_isn1` em `scripts/inv.sh`).
- **Relógio.** O contêiner fica alinhado ao host dentro de ±25 ms. Os avisos do GMS ("I'm not the coordinator")
  caem entre o envio e a resposta do *probe*, o que permite usar os tempos dos logs.

## 3. Resultados por passo

### V-1 (sem falha, 80 s, mesmas sementes)

| Execução | ops | p50 (ms) | p99 (ms) | erros | violações |
|---|---|---|---|---|---|
| `diag` desligado, SYNC | 735 | 2,49 | 7,57 | 0 | 0 |
| `diag` ligado, SYNC | 734 | 2,84 | 7,78 | 0 | 0 |
| `diag` desligado, ASYNC | 837 | 2,24 | 5,56 | 0 | 0 |
| `diag` ligado, ASYNC | 836 | 2,50 | 7,16 | 0 | 0 |

Os controles sem falha do V-5 (`diag` ligado) ficaram em p50 de 1,51 e 1,62 ms e p99 de 5,39 e 6,27 ms.

### V-1b (cliente sem processo)

- **Cliente.** `scripts/ProbeTcp.java` abre um `Socket` por pedido e manda os pedidos em paralelo.
- **Formato da resposta.** Primeiro um cabeçalho `<nome> [ip=..., N mbr(s), cluster=cluster-tcc, version=5.3.16.Final ...]\n`,
  depois uma linha `CHAVE=valor\r\n` por item. Exemplo: `GMS={view=[isn1-10211|2] (3) [...], view_ack_collection_timeout=2s, ...}`.
- **`op=GMS.suspect[...]`.** O método é `void`, então a resposta é só o cabeçalho (115 bytes). O cabeçalho mostra
  o número de membros **depois** da operação, porque a troca de vista roda na própria thread do pedido.
- **Tempos.** Leituras de 13 a 24 ms; a primeira conexão da JVM levou 307 ms.
- **Listar operações.** `ops=GMS` volta vazio; `ops=pbcast.GMS` lista `suspect(java.lang.String)`.
- **`probe.sh` (só como referência).** Levou 1,6 s e, com `-tcp true`, repete o pedido em **todos os membros** (3
  respostas a partir de um só alvo). Como atuador, isso seria perigoso; o `ProbeTcp` fala só com o nó endereçado.
- **`diag` desligado.** A porta publicada aceita e fecha sem resposta (0 bytes).

### V-2 (FD_ALL3 em execução)

- **Escrita.** `jmx=FD_ALL3.timeout=10000` nos 3 nós levou 14 ms. A leitura de volta deu
  `timeout=10s, num_bits=5, interval=8s`.
- **Corte.** O `isn3` foi cortado às 05:37:55,059. A maioria instalou a vista de 2 em +46,1 s e o `isn3` a
  vista de 1 em +46,7 s. Isso cai na faixa dos controles do V-3, V-4 e V-5b (42,8 a 48,2 s na maioria e 42,9 a
  47,7 s no `isn3`).
- **Depois da fusão.** `num_bits` continuava 5. Nulo confirmado, como em `FD_ALL3.java:56-77` e `ReflectUtils.java:214-222`.

### V-3 (corte em t0, pedidos em t0 + ~5 s, cura em t0 + 60 s; `isn3` não coordenador)

Os tempos estão em ms desde o envio do 1º pedido (`t_ref`), tirados dos logs dos servidores. Nos controles, o
detector age sozinho (T_det em s desde t0). Linhas INV são do passo de investigação (seção 4), com a mesma ação.

| Execução | t_probe máx. | vista 2 isn1/isn2 | vista 1 isn3 | DEGRADED isn3 | 1ª recusa isn3 | fim rebal. maioria | erros isn3 até a cura (UNAV/TRANSP) | TRANSPORT na maioria até a cura | viol. |
|---|---|---|---|---|---|---|---|---|---|
| SYNC ctl1 | — | T_det 43,6 s | 44,6 s | — | — | — | 68 / 31 | 26 | 0 |
| SYNC ação 1 | 24 | 17 / 20 | 16 | 22 | 23 | 55 | 119 / 0 | 0 | 0 |
| SYNC ação 2 | 31 | 21 / 25 | 22 | 28 | 29 | 65 | 114 / 11 | 35 (travamento do *host*, seção 4.2) | 0 |
| SYNC ctl2 | — | T_det 47,7 s | 47,7 s | — | — | — | 53 / 34 | 40 | 0 |
| INV SYNC ação 1 | 323 | 15 / 321 | 18 | 33 | 36 | 1655 | 105 / 0 | 0 | 0 |
| INV SYNC ctl | — | T_det 47,4 s | 43,0 s | — | — | — | 37 / 33 | 32 | 0 |
| INV SYNC ação 2 | 19 | 12 / 15 | 12 | 22 | 27 | 31 | 116 / 0 | 0 | 0 |
| ASYNC ctl1 | — | T_det 42,8 s | 43,8 s | — | — | — | 44 / 18 | 5 | 0 |
| ASYNC ação 1 | 28 | 21 / 24 | 21 | 25 | 26 | 37 | 124 / 0 | 0 | 0 |
| ASYNC ação 2 (refeita) | 48 | 25 / 33 | 35 | 80 | 90 | 62 | 66 / 1 | 0 | 1 (antes da ação) |
| ASYNC ctl2 (`isn3` coord.) | — | T_det 48,2 s | 45,8 s | — | — | — | 40 / 21 | 12 | 2 |
| INV ASYNC ação, cura em t0 + 40 s | 17 | 12 / 14 | 13 | 21 | 24 | 27 | 102 / 0 | 0 | 0 |

- **Logs esperados.** Os avisos aparecem no `isn2` e no `isn3` ("I'm not the coordinator (or next-in-line);
  dropping LEAVE request by []"). No `isn3`, saem `ISPN000319` ("must stay in degraded mode") e
  `ISPN100011 ... DEGRADED_MODE` nos 4 *caches*. Depois disso, cada operação recebe
  `AvailabilityException ISPN000306`.
- **Maioria sem esperar o `isn3`.** As escritas da maioria deixam de esperar pelo `isn3`: zero `ERROR_TRANSPORT` na
  maioria entre a ação e a cura em 7 de 7 ações, descontado o travamento do *host*. Nos controles do mesmo trecho
  foram 4 a 40. Essa é a metade (b) do M1, que o AC-5 não alcança.
- **Escritas confirmadas pelo `isn3` entre t0 e o pedido.** Foram 0 ou 1 por execução (`esc_ok_isn3_t0_tref` em
  `analise/resumo.tsv`). Onde elas foram parar depois da fusão não foi medido, porque o S3 não expõe o valor
  por chave.
- **Violações não separam ação de controle.** Ficaram em 0 a 2 nas duas, nesta carga curta local. A diferença
  aparece nos erros.
- **Fusão depois da cura.** Veio sozinha em todas, com resolução de conflitos (`ISPN100012`..`ISPN100013`) em
  menos de 300 ms. Levou 40,4 a 43,8 s nas ações e 30,2 a 35,0 s nos controles; a explicação está na seção 4.1.

### V-4 (o suspeito `isn3` é o coordenador; ASYNC)

O *cluster* foi recriado na ordem `isn3`, `isn1`, `isn2`, e o coordenador era `isn3-35090`.

- **Maioria.** `isn1` e `isn2` receberam `GMS.suspect[isn3]`. O `isn1`, próximo da fila, assumiu e instalou
  `[isn1|3] (2)` em 15 ms; o `isn2` recebeu a vista em 20 ms. Isso confere com `ParticipantGmsImpl.java:90-100`.
  A maioria recuperou a topologia dos *caches* e terminou o rebalanceamento em 5,5 s, sem erros.
- **`isn3`, coordenador isolado.** Processou um suspeito por vez. Primeiro instalou `{isn3, isn1}` em 14 ms e
  esperou a confirmação do `isn1`, que estava cortado, por `view_ack_collection_timeout` = 2 s. Só então
  instalou `{isn3}`, em 2.245 ms. A resposta do *probe* ao `isn3` veio em 2.247 ms, porque a troca de vista
  roda na thread do pedido. `DEGRADED_MODE` e a primeira recusa vieram em 2,25 s.
- **Escritas aceitas na janela.** Nesses 2,25 s o `isn3` ainda aceitou escritas: 2 `IncrementFailure`
  (em 184 e 1.189 ms) e 1 `Login` (em 2.198 ms), registradas em `carga/V4-async-acao/rep-001.csv`.
- **Fusão.** Ocorreu sozinha 40,5 s depois da cura, com o `isn1` como líder, e não houve violações.
- **Controles.** `V4-async-ctl` rodou com o `isn1` coordenador, porque a fusão da ação anterior passou o papel a
  ele, e por isso não serve de controle do V-4. `V4-async-ctl2` foi refeito com o `isn3` coordenador: T_det de
  42,9 s nos dois lados e 0 violações. `V3-async-ctl2` também teve o `isn3` coordenador.

### V-5 (falso positivo: os 4 pedidos sem corte; `isn3` coordenador nas duas)

| Execução | vista 2 maioria | vista 1 isn3 | fusão desde a ação | recusas isn3 até a fusão | violações |
|---|---|---|---|---|---|
| ASYNC controle | — | — | — | 0 | 0 |
| ASYNC ação | 12 / 15 ms | 2.014 ms | 50,0 s | 122 | **5: I1 × 4, I5 × 1** |
| SYNC ação | 15 / 17 ms | 2.018 ms | 50,0 s | 155 | 0 |
| SYNC controle | — | — | — | 0 | 0 |

- **Fusão automática nas duas.** Veio 50,0 s depois do envio, ou seja, 48,0 s depois da vista de 1 do `isn3`, o
  primeiro tique do MERGE3 (seção 4.1), e não em 10 a 30 s.
- **O que a fusão desfez em ASYNC.** A chave `d022af32-...` (usuário `s3-u-009`) recebeu `Logout` no `isn1`
  (maioria) às 08:45:27,356, 1,6 s antes da fusão de 08:45:28,995. Um `Validate` às 08:45:28,498 viu INVALID.
  Depois da fusão, os `Validate` voltaram a ver VALID: 4 × I1, "invalidação confirmada revertida". O auditor
  final registrou réplicas divergentes, INVALID contra VALID (I5).
- **Leitura.** É o M5 que a seção 2.4 previa ("o falso positivo **cria** exposição ao M5"). Com n = 2, não há
  base para uma taxa.

### V-5b (um lado só, ASYNC sob corte; `isn3` coordenador)

| Execução | vista maioria | vista isn3 | violações |
|---|---|---|---|
| controle | T_det 43,1 s | 44,3 s | 15 (I2 × 15) |
| pedido só na maioria | 11 / 13 ms (o `isn1` assume a coordenação) | 39,97 s (FD_ALL3 do próprio `isn3`) | 0 |
| pedido só no `isn3` | 43,3 s (FD_ALL3 da maioria) | 5.516 ms | 0 |

Nenhuma das duas versões piorou as violações em relação ao controle. No caso "só no `isn3`", com o `isn3`
coordenador e os outros dois ainda achando que ele é membro, a vista de 1 levou 5,5 s: primeiro `{isn3, isn2}`
em 4,0 s, depois `{isn3}` em 5,5 s. A causa dos primeiros 4 s não foi investigada.

### V-6 (*passcode*)

| Pedido (`ProbeTcp`, alvo `isn1`) | Resultado | Log do servidor |
|---|---|---|
| sem resumo | recusado, 0 bytes | `EOFException` em `authorizeProbeRequest` (`DiagnosticsHandler.java:365`) |
| resumo certo | aceito, 266 bytes | — |
| *passcode* errado | recusado | `Authorization failed!` (`:372`) |
| resumo certo com `t1` = 01/01/2000 | **aceito** | — |
| leitura `FD_ALL3.timeout` nos 3 com resumo | aceita nos 3 | — |
| `op=GMS.suspect` sem resumo nos 3 | recusado nos 3 | `EOFException`; `cluster_size` continuou 3 nos 3 |

**Lido no código e confirmado.** `Util.createDigest` faz `md.update(bb)` sem `bb.flip()`, então `t1` e `q1` não
entram no resumo. O resumo efetivo é `SHA-1(passcode)`, fixo, e o servidor não confere `t1`: um pedido capturado
pode ser repetido. Além disso, `Util.readBytes` corta o pedido TCP no primeiro `0x0A`, o que faz falhar um prefixo
binário que contenha esse byte. O `ProbeTcp` sorteia `(t1, q1)` de novo até o prefixo sair sem `0x0A`. Se
`SHA-1(passcode)` tiver `0x0A`, nenhum pedido passa; escolher o *passcode* fora desse caso.

## 4. Investigação dos dois achados da primeira janela

### 4.1 Fusão cerca de 10 s mais lenta depois da AC-6: explicada pelo MERGE3, não é efeito da ação

**Mecanismo, pelo código** (`protocols/MERGE3.java`, JGroups 5.3.16):

- O verificador de vistas (`ViewConsistencyChecker`, `:437-510`) roda no coordenador de cada subgrupo a cada
  `check_interval`. Com `max_interval=30000`, isso dá `computeCheckInterval()` = 1,6 × 30 s = **48 s** (`:196`).
- O verificador é **reiniciado a cada troca de vista** (`:246-257`), e a 1ª rodada vem 48 s depois.
- Cada rodada só funde se recebeu, desde a rodada anterior, um INFO de outro subgrupo; as INFO são apagadas a
  cada rodada (`clearViews()`).
- As INFO saem a cada `max(min_interval, random(max_interval) + max_interval/2)`, isto é, de 15 a 45 s (`:427-429`).

**Prova empírica** (`scripts/fusao.py`, `analise/fusao.tsv`):

- Em **20 das 21 fusões**, o instante da fusão ficou a **≤ 12 ms de 48 ou 96 s depois da última troca de vista do
  líder da fusão**.
- A exceção é `V3-sync-acao2`, com +3,94 s. É compatível com a espera de respostas de vista do verificador
  (`check_interval/10` = 4,8 s, `:487`).
- **Nas ações**, a exclusão em t0 + 5 s põe o primeiro tique em t0 + 53 s, antes da cura em t0 + 60 s. A fusão
  fica para o segundo tique, em t0 + 101 s, ≈ 41 s depois da cura.
- **Nos controles**, a detecção em t0 + 43..48 s põe o primeiro tique em t0 + 91..96 s, 31 a 35 s depois da cura.
- **Teste de previsão.** Com a cura em t0 + 40 s (`INV-async-cura40`), o primeiro tique (t0 + 53 s) ainda caiu
  depois da cura. Mesmo assim, a fusão só veio no segundo tique, 60,7 s depois da cura: entre a cura e o primeiro
  tique, nenhuma INFO tinha chegado. A regra de tique mais INFO explica isso.
- **No falso positivo**, a fusão veio 48,0 s depois da vista de 1 do `isn3`, no primeiro tique.

**Consequência.** O tempo de fusão depois da cura depende da fase do tique de 48 s em relação à cura e do
sorteio das INFO. A AC-6 não deixa a fusão lenta: ela muda a fase. O que é preciso declarar é o intervalo
possível, **0 a ~96 s depois da cura**, e não 10 a 30 s. Para o falso positivo, o custo é de 48 a 50 s de `isn3`
recusando (≈ 120 a 155 recusas nesta carga), não 10 a 30 s.

### 4.2 `ERROR_TRANSPORT` na maioria em SYNC: travamento do *host*, não do JGroups

- **Não se repetiu.** Houve duas execuções SYNC a mais com a mesma ação (`INV-sync-rede1`, `INV-sync-rede2`).
  Foram **0** `ERROR_TRANSPORT` na maioria depois da exclusão, em 2 de 2.
- **Hipótese do *buffer* descartada.** O *buffer* de envio TCP para o `isn3` não encheu: o Send-Q dos `isn1`/`isn2`
  para o `isn3`, amostrado a cada 1 s com `ss` no netns, ficou parado em ~8,8 KB depois da exclusão
  (`analise/rede-INV-sync-rede2.tsv`). Sem ação, ele cresceu até ~126 KB e parou na detecção (`rede-INV-sync-rede-ctl.tsv`).
  A hipótese de envio bloqueado por *buffer* cheio para o par cortado não se sustenta.
- **Causa provável: o ambiente.** Na `V3-sync-acao2`, a sonda REST dos **três** nós travou ao mesmo tempo, entre
  t_ref + 37 s e t_ref + 50 s, com amostras de até 9,1 s. Isso inclui o `isn3` isolado, que não
  troca nada com a maioria. Os servidores não registraram nada nesse intervalo.
- **É o único caso.** `scripts/travamentos.py` varreu todas as execuções e esse é o único travamento simultâneo
  dos três nós (`analise/travamentos.tsv`). Os 35 erros coincidem com ele. A causa é um travamento do *host*
  (máquina WSL ou encaminhamento de portas), e o `AuthMechListOperation timed out` do cliente é sintoma dele.
- **Perturbação declarada.** Os INV-sync tiraram dumps de threads (`kill -3`) em t_ref + 15, 30 e 45 s. Eles não
  foram usados, porque o travamento não se repetiu, e ficam nos logs do contêiner.

## 5. Riscos e limites

- **n pequeno.** n = 7 para (a); n = 1 para V-4, V-1 e V-6; n = 2 para V-5. Tudo no *cluster* local, não na VM.
- **Falso positivo em ASYNC.** Pode reverter escrita confirmada na fusão, como no V-5. A guarda de entrada da AC-6
  (S_lag no teto em k janelas, vista completa) é o que limita isso; a acurácia do sinal vira requisito de
  segurança, não só de vazão.
- **Suspeito coordenador.** O lado isolado leva ~2,25 s (2 s de espera de confirmação). Se só o pedido ao
  isolado chegar, leva até 5,5 s. Nesse intervalo ele ainda aceita escritas.
- **Coordenador muda com a fusão.** Depois de uma fusão, o coordenador pode ser qualquer nó. O atuador não pode
  supor que o `isn1` seja coordenador.
- **Segurança da porta.** Sem *passcode*, qualquer um que alcance a porta chama qualquer operação. Com
  *passcode*, o resumo é fixo e repetível. Os dois casos são risco de laboratório a declarar; a porta deve ficar
  só na rede do experimento.

## 6. Arquivos

| Caminho (`runs/b44-3/`) | Conteúdo |
|---|---|
| `cluster/` | pilhas JGroups (sem `diag`, com `diag`, com *passcode*) e os três `podman-compose.b443-*.yml` |
| `scripts/lib.sh` | subida ordenada, corte por nftables (uma chamada `ssh`), execução com controle, ação, coleta |
| `scripts/ProbeTcp.java` | cliente do *probe* TCP, com *passcode* compatível com o JGroups 5.3.16 |
| `scripts/sonda.py`, `amostra-rede.sh` | sonda REST de vista e disponibilidade a ~5 Hz; `ss` por netns e dumps de threads |
| `scripts/analisa.py`, `marcos.py`, `erros_fase.py`, `agrega.py`, `fusao.py`, `rede.py`, `travamentos.py` | tabelas de `analise/` |
| `scripts/v3-*.sh`, `lote2.sh`, `inv.sh`, `v4-v6.sh`, `v4-ctl2.sh`, `v5.sh` | roteiros de cada lote |
| `fases.tsv` | todos os instantes (UTC e ms do host), incluindo a interrupção e a execução inválida |
| `probe/`, `sonda/`, `rede/`, `carga/`, `servidores/` | brutos |
