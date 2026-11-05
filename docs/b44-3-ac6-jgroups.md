# B-44.3 — AC-6: exclusão forçada por `GMS.suspect` via *probe* do JGroups (PARCIAL)

Data: 2026-11-05. Branch `test/b44-3-opcoes-jgroups` (de `12c1786`). Especificação: seção 8 e seções 2.3/2.4 de
`docs/opcoes-arquitetura-configuracao-2026-11-05.md` (branch `docs/plano-implementacao-controlador`, `155aa95`).
Verificação, não atuador: `controle/` e o atuador não foram tocados. *Cluster* local Podman, projeto `b443`
(REST/Hot Rod 12444..12446, *probe* 12447..12449), imagem `quay.io/infinispan/server:15.0.21.Final`. Sem VM, sem push.

**Estado: PARCIAL.** A fase foi interrompida às 05:55 UTC pelo coordenador para a noite 1 da VM (H0). Medido:
V-1, V-1b, V-2, V-3 SYNC (2 ações + 2 controles), V-3 ASYNC (1 ação + 1 controle). Falta: V-3 ASYNC (2ª ação e
2º controle), V-4, V-5, V-5b, V-6. A execução `V3-async-acao2` é **inválida**: a máquina Podman parou
(parada externa) entre o corte (05:54:34,478) e os pedidos do *probe* (05:54:39,859, `Connection refused` nos 4).

Brutos em `runs/b44-3/` (`MANIFEST.sha256`). CSV, `.log` e `.txt` ficam fora do Git pelo `.gitignore` (D-005),
mas estão no disco e no manifesto.

## 1. Resposta curta (com o que já foi medido)

| Critério | Resultado até aqui | Evidência |
|---|---|---|
| (a) AC-6 funciona nos dois lados sob corte | **sim em 3 de 3 execuções válidas** (SYNC 2, ASYNC 1; falta a 2ª ASYNC) | `analise/marcos.tsv`; logs `servidores/V3-*/` |
| (b) suspeito = coordenador | **não medido** (V-4 pendente) | — |
| (c) falso positivo funde sozinho | **não medido** (V-5 pendente) | — |
| (d) detector em execução sem efeito | **sim** (T_det 46,1 s na maioria e 46,7 s no `isn3` com `timeout=10s` gravado; `num_bits` ficou 5) | `probe/V2-*.tsv`, `servidores/V2/` |
| (e) `diag` ligado não muda a carga sem falha | **sim, n = 1 por condição** (0 erros e 0 violações nos 4; p50 +0,26 a +0,35 ms, dentro da faixa 1,86..2,70 ms do B-44.1/1b) | `analise/resumo.tsv`, `carga/V1-*` |
| Viabilidade `k·W + t_probe + t_vista < 39,8 s` | com os máximos medidos (t_probe ≤ 31 ms, t_vista ≤ 25 ms na maioria e ≤ 22 ms no `isn3`): **`k·W < 39,74 s`** | idem (a) |

## 2. Configuração (passo 0)

`runs/b44-3/cluster/jgroups-tcp-diag.xml` = `cluster/jgroups-tcp.xml` com `diag.enabled="true"`,
`diag.enable_tcp="true"`, `diag.enable_udp="false"`, `diag.port="7500"`, `diag.port_range="0"` (sem
`diag.bind_addr`: o `ServerSocket` liga em todas as interfaces e o encaminhamento do Podman alcança).
Desvio do prompt: a porta 7500 de cada nó foi publicada em 12447/12448/12449, não em 7500..7502, para não colidir
com outro Worker. Nós subidos um a um (`isn1`, `isn2`, `isn3`); coordenador `isn1-10211`; nomes lógicos
`isn1-10211`, `isn2-60404`, `isn3-38280` (`fases.tsv`).

## 3. Resultados por passo

### V-1 (sem falha, 80 s, mesmas sementes com e sem `diag`)

| Execução | ops | p50 (ms) | p99 (ms) | erros | violações |
|---|---|---|---|---|---|
| `diag` desligado, SYNC | 735 | 2,49 | 7,57 | 0 | 0 |
| `diag` ligado, SYNC | 734 | 2,84 | 7,78 | 0 | 0 |
| `diag` desligado, ASYNC | 837 | 2,24 | 5,56 | 0 | 0 |
| `diag` ligado, ASYNC | 836 | 2,50 | 7,16 | 0 | 0 |

### V-1b (cliente sem processo)

`runs/b44-3/scripts/ProbeTcp.java` (um `Socket` por pedido, pedidos em paralelo). Formato da resposta: uma
linha de cabeçalho `<nome> [ip=..., N mbr(s), cluster=cluster-tcc, version=5.3.16.Final ...]\n` e, depois, uma
linha `CHAVE=valor\r\n` por item (ex.: `GMS={view=[isn1-10211|2] (3) [...], view_ack_collection_timeout=2s, ...}`).
`op=GMS.suspect[...]` (método `void`) responde só o cabeçalho (115 bytes); o cabeçalho já traz o número de
membros **depois** da operação. Leituras de 13 a 24 ms (a 1ª conexão da JVM, 307 ms). `ops=GMS` volta vazio
(a classe não é achada pelo nome curto); `ops=pbcast.GMS` lista `suspect(java.lang.String)`. Referência:
`probe.sh` no contêiner levou 1,6 s e, com `-tcp true`, **pergunta os endereços e repete o pedido em todos os
membros** (3 respostas a partir de um só alvo); o `ProbeTcp` fala só com o nó endereçado. Com o `diag`
desligado, a porta publicada aceita e fecha sem resposta (0 bytes, 13 ms).

### V-2 (FD_ALL3 em execução)

`jmx=FD_ALL3.timeout=10000` nos 3 nós (14 ms); leitura de volta `timeout=10s, num_bits=5, interval=8s`. Corte do
`isn3` às 05:37:55,059: vista de 2 na maioria a +46,1 s e vista de 1 no `isn3` a +46,7 s, iguais aos controles do
V-3 (43,6 a 47,7 s). Depois da fusão, `num_bits` continuava 5. Nulo confirmado.

### V-3 (corte em t0, pedidos em t0 + ~5 s, cura em t0 + 60 s)

Tempos em ms depois do envio do 1º pedido (`t_ref`), pelos logs dos servidores; controles sem ação.

| Execução | t_probe (máx.) | vista 2 isn1/isn2 | vista 1 isn3 | DEGRADED isn3 (sessions / async) | 1ª recusa Hot Rod isn3 (`ISPN000306`) | fim do rebal. maioria | fusão após a cura (`ISPN000093`) | violações (total / janela 5 s) |
|---|---|---|---|---|---|---|---|---|
| SYNC ctl1 | — | T_det 43,6 s | T_det 44,6 s | — | — | — | 30,9 s | 0 / 0 |
| SYNC ação 1 | 24 | 17 / 20 | 16 | 20 / 22 | 23 | 55 | 40,4 s | 0 / 0 |
| SYNC ação 2 | 31 | 21 / 25 | 22 | 28 / 28 | 29 | 65 | 43,8 s | 0 / 0 |
| SYNC ctl2 | — | T_det 47,7 s | T_det 47,7 s | — | — | — | 35,0 s | 0 / 0 |
| ASYNC ctl1 | — | T_det 42,8 s | T_det 43,8 s | — | — | — | 30,2 s | 0 / 0 |
| ASYNC ação 1 | 28 | 21 / 24 | 21 | 25 / 25 | 26 | 37 | 40,7 s | 0 / 0 |

- Os dois avisos esperados aparecem (`isn2` e `isn3`: "I'm not the coordinator (or next-in-line); dropping LEAVE
  request by []"). Eles caem entre o envio e a resposta do *probe*: o relógio do contêiner está alinhado ao do
  host dentro de ±25 ms, o que permite usar os tempos dos logs.
- No `isn3`, `ISPN000319` ("must stay in degraded mode") e `DEGRADED_MODE` nos 4 *caches*; recusas
  `AvailabilityException ISPN000306` daí em diante. Erros no `isn3` entre t_ref e a cura: 114 a 124
  `ERROR_UNAVAILABLE` nas ações (mais 11 `TRANSPORT` na SYNC ação 2), contra 44 a 68 `UNAVAILABLE` e 18 a 34
  `TRANSPORT` nos controles (`analise/erros-fase.tsv`).
- Escritas confirmadas pelo `isn3` entre t0 e o pedido: 0, 1 e 0. Onde foram parar depois da fusão não foi medido
  (o S3 não expõe o valor por chave; fica pendente).
- Fusão automática depois da cura nas 3 ações, com resolução de conflitos (`ISPN100012`..`ISPN100013`, < 300 ms).
  **Ela veio 40,4 a 43,8 s depois da cura nas ações, contra 30,2 a 35,0 s nos controles**, acima dos 10 a 30 s
  esperados do MERGE3. Causa não investigada.
- Nenhuma violação em nenhuma execução, controles incluídos: esta carga curta local não separa ação de controle
  pelas violações. A diferença aparece nos erros (o `isn3` passa a recusar ~40 s antes).
- SYNC ação 2: 35 `ERROR_TRANSPORT` na maioria **depois** da exclusão (t_ref + 37,8 a 47,4 s), com
  `AuthMechListOperation ... timed out after 2000 ms` ao abrir conexão com `isn1`/`isn2` e nenhum log nos
  servidores nesse intervalo. Na ação 1 não houve. Os controles têm 33 e 22 erros desse tipo na maioria na mesma fase.
  Não piora em relação ao controle, mas a AC-6 não os elimina em SYNC. Ponto aberto.

## 4. Achados de código (V-6, ainda sem execução)

Lidos em `jgroups-5.3.16.Final-sources.jar` (Maven Central): `Util.createDigest` faz `md.update(bb)` sem
`bb.flip()`, então `t1` e `q1` **não entram** no resumo. O resumo efetivo é `SHA-1(passcode)`, fixo, e
`DiagnosticsHandler.authorizeProbeRequest` não confere `t1`: um pedido capturado pode ser repetido. Além disso,
`Util.readBytes` corta o pedido TCP no primeiro byte `0x0A`, então um prefixo binário com `0x0A` falha. O
`ProbeTcp` sorteia `(t1, q1)` de novo até o prefixo sair sem `0x0A`. Para o *passcode* de teste,
`SHA-1 = 05ff29c1...4437d4`, sem `0x0A`. Falta a confirmação empírica.

## 5. Pendências para a próxima janela

V-3 ASYNC (2ª ação + 2º controle), V-4 (coordenador `isn3`), V-5 (ASYNC e SYNC, com controles), V-5b (maioria só,
`isn3` só, controle), V-6 (`podman-compose.b443-pass.yml` pronto). Roteiros: `scripts/v3-async.sh` (refazer só as
duas últimas), `scripts/v5.sh`. Os contêineres `b443-*` ficaram na máquina Podman parada e serão recriados.
