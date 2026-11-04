# V-S7: validação do S7 (G-5b-B, split real de isn3, `kill -KILL` do injetor, dead-man disparando sozinho), 2026-11-04

Autor: Senior. Bloco rodado pelo coordenador em 04/11, 22:53:29-23:06:12Z, com GO digitado às 22:55:51Z sob a autorização do Gestor de 2026-11-04 (`go.log`, sha `f6483c5`). VM `172.27.114.62`, `boot_id` `f9e98b74-bf1f-4192-9770-b40285877292` no início (`00-pre.log`) e no fim (`07-boot-fim.txt`), mesmo cluster do S5 e do S6 (isn1-15077, isn2-28862, isn3-9749). Host em H0, segundo o coordenador. `s7.sh` com sha256 `2385609e…` (`00-pre.log`).

Brutos em `vm-20261104T225329Z/`. Conferidos no host: `sha256sum -c MANIFEST.sha256` (47 arquivos), `g5b-B.sha256` e `copia-host.sha256` (50 arquivos), todos OK.

**Acréscimo do coordenador.** Depois da saída do `s7.sh`, e portanto fora do `MANIFEST.sha256` dele, o coordenador gravou dois arquivos, ambos cobertos pelo `copia-host.sha256`:

- `isn1-log-23h03-23h04.txt`: `podman logs isn1` de 23:03:30 a 23:04:20Z;
- `journal-vm-23h03-23h04.txt`: journal da VM na mesma janela (56 linhas: isn1, isn2, isn3, `podman` e `systemd`; nenhuma linha de kernel).

O que rodei: só leitura dos brutos, `sha256sum` e, no host, uma consulta ao log de eventos do Windows (`Get-WinEvent`, `Get-PhysicalDisk`, a chave `Disk\TimeOutValue`). Nada na VM. Nenhum push.

Avaliação automática: `S7: INVALIDO (1 falha(s), 18 OK)` (`08-avaliacao.txt`). A falha é `B1/A11 portao 5.1 pos`, com `amostras_ruins=1`.

## 1. A amostra ruim do portão pós

`06-portao51-pos.csv` tem 201 amostras (67 rodadas × 3 portas). Só uma é ruim, na linha 173: `2026-11-04T23:03:55, 11222 (isn1), http 000`, sem corpo. A coluna `t_iso` é o `date -Is` gravado **depois** do `curl` de cada porta (`portao51.sh`), e o `curl` do portão usa `-m 3`.

Não há amostra às 23:03:50 nem às 23:04:00. As vizinhas são estas:

| `t_iso` (UTC) | Porta | Resultado | Intervalo desde a linha anterior |
|---|---|---|---|
| 23:03:47 | 11222, 11223, 11224 | 200 HEALTHY/3 nas três | ~5 s (normal) |
| 23:03:55 | 11222 | **000**, sem corpo | **8 s** |
| 23:04:02 | 11223, 11224 | 200 HEALTHY/3 | **7 s** |
| 23:04:08 | 11222, 11223, 11224 | 200 HEALTHY/3 nas três | ~6 s |

A rodada que começou por volta de 23:03:52 levou cerca de 10 s, quando as outras 66 levaram centésimos de segundo. Fora dela, nem o portão pré nem o pós têm intervalo acima de 6 s.

Cada requisição de saúde com `--digest` gera duas linhas `ISPN012026` no log do nó: a da primeira perna, que recebe o 401, e a da segunda, já autenticada. No uso normal as duas ficam a ~1 ms uma da outra. Nos logs do coordenador:

| Instante (UTC) | Nó | Evento |
|---|---|---|
| 23:03:31,753 → 32,139 | isn1 | duas pernas com **0,386 s** entre elas (as de isn2 e isn3 na mesma rodada: 1 ms) |
| 23:03:47,248 / 47,249 | isn1 | normal (1 ms) |
| 23:03:52,280 | isn1 | **só a primeira perna**. Não há segunda: o 401 não saiu em 3 s, e o `curl` desistiu às ~23:03:55 com `000` |
| 23:03:52,28 → 23:04:00,33 | todos | **8 s sem nenhuma linha no journal**, de nenhuma origem |
| 23:04:00,327 → 04:02,501 | isn2 | duas pernas com **2,17 s** entre elas |
| 23:04:02,509 → 02,983 | isn3 | duas pernas com **0,47 s** entre elas |
| 23:04:04,855-05,555 | isn2, isn3, isn1 | `GET /health/status` vindo de 127.0.0.1: o *healthcheck* do podman chegando aos contêineres |
| 23:04:05,03-05,71 | podman | `health_status` dos três contêineres com `m=+33,13`, `+33,45` e `+33,81` s. O de isn2 sai com `status=125` (`Failed with result 'exit-code'`) |
| 23:04:07,995 / 07,996 | isn1 | normal (1 ms), e daí em diante tudo normal |

Os *healthchecks* começaram por volta de 23:03:32 e levaram ~33 s para chegar ao `curl` de dentro do contêiner. O de isn2 terminou com erro do próprio podman (125). Não é um *unhealthy*.

Na volta de 11223 há uma conta que não fecha só com isn2. A linha de 11222 foi gravada às 23:03:55. A primeira perna de isn2 só chegou às 04:00,327, e o `curl` de 11223 respondeu 200 com `-m 3`, então a transferência começou às 03:59,5 ou depois. Sobram pelo menos ~4 s, entre 23:03:55 e 23:03:59,5, gastos **fora** do `curl` e do JVM de isn2: na criação do processo `curl` ou no laço do *shell*.

### Causa provável

**Uma pausa da VM inteira, de ~23:03:52 a ~23:04:03Z (~10 s), e não um problema de isn1.** Grau de certeza: **alto** de que a pausa não foi só de isn1. **Médio** de que a origem é o SSD NVMe do host.

Por que não é pausa de GC de isn1, nem nada próprio de isn1:

- Na mesma janela ficaram lentos isn2 (2,17 s), isn3 (0,47 s), o laço do portão (~4 s fora do `curl`) e os três *healthchecks* do podman (~33 s, um com erro). Uma pausa do JVM de isn1 não explica nenhum desses.
- O journal inteiro fica 8 s sem nenhuma linha.
- isn1 só foi o primeiro a ser atingido porque é a primeira porta da rodada: a requisição dele chegou às 23:03:52,280, no começo da pausa.

O mecanismo mais provável, que **não verifiquei**, é este: a thread do Netty de isn1 gravou a linha `WARN` (saída padrão, que o journal recebeu) e travou numa escrita em disco. O Infinispan grava o log também em `server.log`, dentro do contêiner, e daí o 401 não saiu. O podman também depende de escrita em disco nos *healthchecks*.

**Evidência no host.** No log `System` do Windows há um único evento na janela de 20:02:30 a 20:05:30 locais (UTC−3): **`stornvme` 129, "Reset to device, \Device\RaidPort1, was issued", às 20:04:59 = 23:04:59Z**. O `Disk\TimeOutValue` do host é **65 s**. Se o `stornvme` usar esse tempo-limite, o pedido de E/S que provocou o *reset* saiu por volta de **23:03:54Z**, 2 s depois do começo da pausa na VM. Os relógios do host e da VM não foram comparados (a VM usa a sincronização do Hyper-V), e não sei se o `stornvme` usa exatamente esse valor. Por isso a ligação entre o *reset* e a pausa é **provável, não provada**.

**O evento não é isolado.** O mesmo `stornvme` 129 aparece no host desde 22/10, incluindo **quatro *resets* hoje entre 18:45:18 e 18:45:56 locais (21:45Z)**, logo antes de a VM ser religada às 21:49Z. Em 28/10, das 12:56:23 às 12:58:47 locais, são 15 *resets* seguidos, a cada ~10 s. A madrugada de 28/10 teve o BSOD 0x154 (`volmgr` 162, *dump* às 03:37:48 locais), cujo código é de exceção de armazenamento. Não houve nenhum 129 durante o S5 (22:19Z) nem durante o S6 (22:38-22:48Z), então este evento **não explica** as duas respostas de ~0,37 s de isn1 na base do S5 (R-7 da V-S5).

O que fica em aberto: isn1 teve pernas lentas pequenas também fora da pausa (0,162 s às 22:56:12 em 99 pares do `log-completo-isn1.txt`, 0,386 s às 23:03:31). No mesmo log, isn2 e isn3 não têm nenhuma acima de 0,1 s. Com esses números isso não sustenta uma conclusão, mas vai com a R-7 da V-S5 para a checagem curta da seção 6.

## 2. Classificação: a falha é do mecanismo ou do portão de estado final?

**É do portão de estado final, e não do mecanismo que o S7 prova.** O dead-man passou em todos os critérios: B2 a B7, D-1 a D-3, e as leituras humanas da seção 3. Quando a amostra ruim saiu, o bloco do dead-man tinha terminado havia 5 min (`g5b.sh` com rc 0 às 22:59:03), não havia `clsact` nem filtro em nenhum netns, não havia timer, e as vistas estavam estáveis desde 22:59:01. Isso é uma **leitura** do Senior e não muda o veredito.

**O pré-registro deixa o bloco INVÁLIDO.** O `ROTEIRO-S7.md`, na tabela "Invalidam o bloco (`FALHA`)", põe o A11 dentro do B1: "A1, A2, A3, A4, A5, A6 (com o limite de 14 s da R-1), **A11** e A12 do S6, com a mesma regra". O §5.7 manda conferir "o estado final pelo portão 5.1". O A11 é avaliado por `avaliar_portao`, que exige `amostras_ruins=0` (`s7.sh:82`) desde o `s4.sh` (`c0f12a1`, 28/10). Esse código foi copiado sem mudança para o `s5.sh`, o `s6.sh` e o `s7.sh`, revisado na V-s6s7sh (linha A11 "ok") e congelado pelo hash `2385609e…` antes do bloco.

**Nenhuma regra registrada antes do bloco permite reclassificar esta falha.** As regras de reclassificação que existem são estas, e nenhuma se aplica:

- R-1, persistência em [14, 15) s vira `RESULTADO`: não se aplica, a persistência foi de 16,135 s.
- Limite de 150 s do `g5b.sh` até MergeView/`HEALTHY`: não estourou. Mesmo que estourasse, a regra manda **refazer** o bloco.
- R-7 da V-s5sh, mudança de vista tardia no portão pós: vale só para o S5, porque a §5.6 trata a mudança de vista como resultado. Aqui não houve mudança de vista.

**Uma observação, sem efeito sobre o veredito.** O texto do §5.1 pede "3 portas `HEALTHY` com `number_of_nodes=3` por **>= 120 s**". O cabeçalho do `portao51.sh` diz que uma amostra ruim "zera a contagem". Por essa leitura, o portão pós passaria: 283 s de maior sequência boa, e os últimos 125 s seguidos (de 23:04:07 ao fim), com tudo o mais limpo. Mas a forma de aplicar o §5.1 aprovada para o S7 é a do `avaliar_portao`, mais estrita, com zero amostras ruins na janela inteira. Trocar uma pela outra agora seria mudar a regra depois de ver o dado, e isso não faço. Se o Gestor quiser a leitura do §5.1 para os blocos **futuros**, ela tem de ser registrada antes deles (seção 6, P-2).

**Conclusão: o S7 tem de ser repetido.** Os dados deste bloco ficam no relatório como observação do mecanismo, num bloco INVÁLIDO por estado final. Não podem ser citados como S7 válido.

## 3. Leituras humanas (as mesmas do S6)

### 3.1 Instantes e ordem split → KILL → disparo → merge

Referência: `t_start` = 22:55:53,017 (`cronologia.txt`). Todos os carimbos vêm do mesmo kernel.

| Instante (UTC) | Fonte | Evento | Relativo |
|---|---|---|---|
| 22:55:51,975 / 51,982 | cronologia | `t_antes_armar` / `t_depois_armar` | -1,04 s |
| 22:55:51,981 | journal da unit | `Started tcc-heal-61463-16632.timer` | |
| 22:55:52,233-52,271 | journal do `sudo` | pré-limpeza, sonda e injeção (`clsact` + 4 filtros) | antes do `start` |
| 22:55:52,275 | `marcadores.txt` | `event=start` | -0,74 s |
| 22:56:30,495 | isn3 | `ISPN000094` `[isn3-9749\|5] (1)` | +37,48 s |
| 22:56:30,498 (×2) | isn3 | `DEGRADED_MODE` (`sessions`, `counters`), topology id 23 | +37,48 s |
| 22:56:37,283 / 37,285 | isn1 / isn2 | `ISPN000094` `[isn1-15077\|5] (2)`; isn1 `AVAILABLE`, topology id 22 | +44,27 s |
| 22:56:39,205 | cronologia | `t_split_confirmado` | +46,19 s |
| 22:57:03,398 | cronologia | `t_kill` (`rc_injetor=137`) | +70,38 s |
| 22:57:19,533 | cronologia | `t_persistencia_ok` | kill + 16,135 s |
| 22:58:17,287 | journal da unit | `Started tcc-heal-61463-16632.service` | timer + 145,306 s |
| 22:58:17,290 | journal da unit | `Heal: removendo clsact em isn3` | |
| 22:58:17,403 | journal do `sudo` | `tc qdisc del dev eth0 clsact`, `_SYSTEMD_UNIT` = a unit | +144,39 s |
| 22:58:17,407 | journal da unit | `.service` e `.timer` `Deactivated successfully` | |
| 22:58:18,274 | cronologia | `t_fire` (sondagem de 1 s) | +145,26 s; `del` 0,870 s antes |
| 22:59:01,290 / 01,292 | isn1, isn3 / isn2 | `ISPN000093` MergeView `[isn1-15077\|6] (3)` | `del` + 43,89 s |
| 22:59:01,407 / 01,424 | isn1 | `ISPN100013` fim da resolução de conflito (`counters`, `sessions`) | |
| 22:59:03,297 / 03,318 | cronologia | `t_mergeview` / `t_saudavel` | `t_fire` + 45,02 / 45,04 s |

A ordem é a exigida. O split aparece nos três logs antes da confirmação pela sondagem e ~26 s antes do KILL. Entre o split e o MergeView não há nenhuma outra linha `ISPN000094`/`ISPN000093` em nenhum nó (`05-vistas.log`, `log-vista-*`), então as vistas ficaram divididas do KILL ao disparo. `qdisc-apos-kill.txt` mostra `clsact` presente; `qdisc-no-disparo.txt` só mostra `noqueue`. O disparo (146,298 / 146,292 ms, B3) e o início da unit 0,306 s depois do prazo de 145 s batem com o atraso do S3 (0,341 s, V-S3 item 10). A diferença de 0,987 s entre a unit e o `t_fire` vem da sondagem de 1 s.

Duas observações sem efeito no veredito. A primeira: isn3 detectou o corte em +37,5 s do `t_start` (+38,2 s do marcador), um pouco abaixo da faixa medida antes (39,8 a 46,4 s); a maioria detectou em +44,3 s, dentro da faixa. A segunda: `del → MergeView` = 43,9 s. É dado de segurança, não é t_merge (§5.7). Ficou dentro da faixa das REPs (44 a 48 s pela sondagem: aqui, 45,0 s) e abaixo do S6 (53 s).

### 3.2 As 2 linhas de `DEGRADED_MODE`

As duas são de isn3, às 22:56:30,498, uma por cache com tratamento de partição: `[Context=sessions]` e `[Context=counters]` `ISPN100011: Entering availability mode DEGRADED_MODE, topology id 23`. É o sinal de split do lado minoritário que o §5.7 pede, igual ao do S6. No lado majoritário, isn1 registra `AVAILABLE`, topology id 22, para as duas caches às 22:56:37,287/,291. A recuperação aparece em isn1 como `ISPN100007 ... recovered members [isn1-15077, isn2-28862, isn3-9749] with topology id 28` (22:59:01,295) para `sessions` e `counters`. Não houve `ISPN000524`/`ISPN100014`/`ISPN000638`.

### 3.3 Origem dos comandos `tc` de escrita no journal do `sudo`

Extraí do `05-sudo-journal.txt` (1107 entradas) todos os `COMMAND=` com `tc qdisc|filter add|del`. São 11.

| Hora (UTC) | PID do sudo | Origem | Comando (`nsenter -t 1711 -n tc ...`) | Papel (`inj.log`, journal da unit) |
|---|---|---|---|---|
| 22:55:52,233 | 62363 | `session-31.scope` | `qdisc del dev eth0 clsact` | pré-limpeza idempotente |
| 22:55:52,237 | 62368 | `session-31.scope` | `qdisc add dev eth0 clsact` | sonda |
| 22:55:52,241 / 52,246 | 62372 / 62376 | `session-31.scope` | `filter add` ingress/egress `192.0.2.1/32 action drop` | sonda (TEST-NET-1) |
| 22:55:52,250 | 62380 | `session-31.scope` | `qdisc del dev eth0 clsact` | fim da sonda |
| 22:55:52,254 | 62385 | `session-31.scope` | `qdisc add dev eth0 clsact` | injeção |
| 22:55:52,259-52,271 | 62390-62403 | `session-31.scope` | 4 `filter add`, src/dst 10.89.1.2 e 10.89.1.3, `action drop` | injeção (isn3 × isn1, isn3 × isn2) |
| 22:58:17,403 | 70190 | **`tcc-heal-61463-16632.service`** | `qdisc del dev eth0 clsact` | **disparo do dead-man** |

Os 8 "outros" que a avaliação pediu para conferir são os `add`: 2 de `clsact` e 6 de filtro, da sonda e da injeção. Os 10 primeiros comandos caem em 38 ms, antes do `event=start` (52,275), e batem um a um com o `inj.log`. Entre `t_kill` e o fim do bloco, o único comando de escrita é o `del` da unit. Não há nenhum `systemctl stop` no journal do `sudo`, e `chamadas-injetor.txt` tem só a injeção. **O dead-man foi o único agente de cura.**

## 4. Os demais critérios

Conferi contra os brutos os 18 `OK`:

- **Cronologia e tempos.** A cronologia acima; `tempos.txt`.
- **Unit (B4, B5, D-3).** `unit-armada.txt`: `ExecStart` = `bash scripts/inject-partition.sh --heal --minority isn3`, `PODMAN=sudo podman`, `OnActiveSec=2min 25s`, `AccuracySec=1s`. Journal da unit sem `Failed with result`.
- **D-2.** `janela/tc-isn3.csv`: 72 amostras com `clsact=1` até `t_fire − 2 s` e 22 com `clsact=0` a partir de `t_fire + 1 s`.
- **Intervalo do saúde-log.** Máximo de 2,345 s. A janela do saúde-log termina às 22:59:04, antes da pausa, que não a atingiu.
- **REST de isn3 na janela.** Mediana de 1,9 ms.
- **`boot_id`.** Igual no início e no fim.

Concordo com a única `FALHA` (A11) e com o veredito automático.

## Veredito

**S7 INVÁLIDO**, pelo B1/A11 pré-registrado (portão 5.1 pós com `amostras_ruins=1`). A amostra ruim é um `http 000` de isn1 às 23:03:55Z, por causa de uma pausa da VM inteira de ~10 s, e não de isn1 sozinho. A causa provável, não provada, é o *reset* do NVMe do host às 23:04:59Z (`stornvme` 129, tempo-limite de 65 s, que leva a ~23:03:54Z). O mecanismo que o S7 prova se comportou como exigido em todos os critérios: o dead-man foi o único agente de cura, disparou em 146,3 s, rc 0, corpo idêntico ao de produção, `clsact` ausente em seguida, MergeView de 3 e `HEALTHY` x3. Mas não há regra registrada antes do bloco que permita reclassificar a falha do estado final. **O S7 se repete.**

## 5. Repetir o S7: o que conferir antes

O hash do `s7.sh` não muda, e nada no `s7.sh`, no `g5b.sh` nem no `portao51.sh` é editado.

1. **Saúde do armazenamento do host (o principal).** Os `stornvme` 129 são recorrentes desde 22/10 e vieram em rajada em 28/10. Antes de repetir, ler (só leitura) os contadores do SSD (`Get-PhysicalDisk | Get-StorageReliabilityCounter`, que pede administrador) e conferir que não houve 129 desde 23:04:59Z. Atualizar o *firmware*/driver do NVMe, desligar a economia de energia do PCIe/NVMe (ASPM/APST, causa comum de 129) e fazer o backup da VHDX são mudanças no host: **decisão do Gestor (P-1)**.
2. **Checagem curta, só leitura, na VM, antes do novo bloco.** Rodar `bash runs/vm-s2-2026-10-27/portao51.sh 600 <csv>` sob o lock (é o próprio portão, nada que toque `tc`) e, numa janela `tmux` à parte, `vmstat 1` e `cat /proc/pressure/{cpu,io,memory}` a cada 1 s. Ao fim: (a) contar as amostras ruins; (b) tirar do `podman logs --since` dos 3 nós o intervalo entre as duas pernas de cada requisição, como na seção 1; (c) consultar no host os 129 da janela. Se aparecer pausa de novo, com ou sem 129, **não repetir** o S7 e chamar o Senior. Se só isn1 tiver pernas lentas, aí sim vale instrumentar o GC: por exemplo, `jcmd <pid> VM.log what=gc` dentro do contêiner, se a imagem tiver `jcmd`. Isso é mudança no JVM sob teste e precisa do Gestor. Com a evidência atual, **não recomendo** instrumentar o GC, porque a pausa não foi de isn1.
3. **Coleta paralela no próprio S7 repetido.** Os mesmos `vmstat 1`/PSI numa janela `tmux` à parte, gravados fora do `vm-<UTC>/` e acrescentados depois pelo coordenador, como desta vez. A consulta aos 129 do host vai junto. Fica registrado **agora, antes do bloco**: esses dados servem só para diagnóstico. Uma amostra ruim no portão pós continua deixando o bloco INVÁLIDO, com ou sem 129.
4. **Pré-condições do `s7.sh`.** Ele exige `runs/gate-b29/g5b-B` inexistente. Na VM, mover o diretório deste bloco para `runs/gate-b29/g5b-B.s7-invalido-20261104T225329Z`, sem apagar (os brutos já estão no host e conferem). `--bloco-anterior` continua sendo o S6 (`vm-20261104T223805Z`), e `--boot-esperado` continua sendo `f9e98b74-…`, se não houver reboot. O `go.log` vai ter mais uma linha `go ` (aceita pela Q-12). Novo GO, portão pré limpo, host em H0.
5. **Mesmo cluster.** O repetido roda sobre o cluster do S5, do S6 e deste S7, com mais uma partição no histórico. Isso não afeta a REP=4B (já feita). O cluster é recriado antes do *rebaseline*, de qualquer modo.

## 6. O que isso implica para a campanha da medição de referência

**O portão K1 da campanha não tem a mesma sensibilidade.** O `portao_k1` de `scripts/run-rebaseline.sh` (`wt-rebaseline`, linhas 576-596) exige 60 s seguidos de amostras boas, e uma amostra ruim só **zera** a contagem (`inicio_bom=""`). A recusa só vem se o portão não passar em 10 min, pela parada (e) do PR §6. O `curl` usa `--max-time 5`. Uma pausa como esta atrasaria uma execução em ~60 s e não recusaria nada.

Onde a mesma sensibilidade aparece:

- **Ensaio do F1 silencioso** (`docs/prompt-worker-ensaio-f1-2026-11-04.md`, ainda não implementado). O F-10 diz "estado final: portão OK", com origem no §5.1. Se o Worker reaproveitar o `avaliar_portao`, a sensibilidade a uma única amostra ruim é a mesma. O F-9 (intervalo máximo do saúde-log ≤ 4 s) também cairia com uma pausa destas. **Decidir antes de implementar** qual regra o F-10 usa (P-2).
- **A janela de medição da campanha.** A sensibilidade real está nos dados, não no K1. Uma pausa de ~10 s no meio dos 300 s de uma execução gera latências altas e possíveis `ERROR_TRANSPORT`, e pode reprovar K3/K4/K5. Uma rajada como a de 28/10 (~2,5 min de *resets*) passa da detecção do FD_ALL3 (~40 s) e produziria mudança de vista no meio de uma execução `none`. Numa campanha de várias noites, o estado do NVMe do host é risco de infraestrutura e entra como pré-condição.

## Pontos que precisam de validação do Gestor

- **P-1.** Ação sobre o NVMe do host antes do S7 repetido e da campanha: ler os contadores de confiabilidade; atualizar *firmware*/driver; desligar ASPM/APST; fazer o backup da VHDX.
- **P-2.** Para blocos **futuros** (não para este S7): manter `amostras_ruins=0` no portão de estado final, ou passar à leitura do §5.1 (≥ 120 s seguidos, o "zera a contagem" do `portao51.sh`). Isso inclui o F-10 do ensaio do F1. Se mudar, registrar antes, com hash novo.
- **P-3.** Autorizar a checagem curta da seção 5, item 2, e a coleta paralela do item 3 no S7 repetido.
- **P-4.** Autorizar o coordenador a mover `runs/gate-b29/g5b-B` na VM e a rodar o S7 de novo, com novo GO.
