# V-ensaio-execucao: leitura X-08v do ensaio do F1 silencioso (Senior, 2026-11-05)

> Brutos: `runs/ensaio-f1-silencioso-2026-11-04/vm-20261105T030044Z/` (100 arquivos, 75 MB; `copia-host.sha256`
> conferido de novo nesta leitura, `sha256sum -c` sem divergência). Roteiro `ROTEIRO-ENSAIO-F1.md`, validação
> `V-ensaio-f1.md`, especificação `wt-gate-v4/docs/prompt-worker-ensaio-f1-2026-11-04.md`, critérios DT-017,
> complemento 2 (C2) e complemento 3 (C3, critério h4) do pré-registro.
>
> Material desta leitura em `diag-x08v/` (com `SHA256SUMS`): instantâneo do `typeperf` do host
> (`host-io-ensaio-instantaneo.csv`, copiado do *scratchpad* do coordenador às 03:49Z, ainda em gravação; as linhas até
> 03:44Z são as lidas aqui), os dois *scripts* de conta (`psi_fases.py`, `host_fases.py`, Python 3, biblioteca padrão,
> rodam com `-I`), as saídas deles e os eventos SPP do log Application do Windows (`spp-eventos-local.txt`, hora local
> UTC−3). A janela de 10 s é a da C3 §3.2 (diferença do `total=` da linha `full` de `/proc/pressure/io`), calculada sobre
> `coleta-paralela-coordenador/psi.txt`. Esse arquivo não tem `/proc/uptime`, então o salto de relógio do h4b não é
> avaliável aqui. Só o intervalo entre amostras é.

## 1. Veredito

**O ensaio cumpriu o que devia medir, e as 4 execuções são informativas.** O resultado formal é `ENSAIO: 3 de 4 VALIDAS`.
A única FALHA, o F-9 do n=4, é **artefato do `saude_loop`**, não pausa da VM (seção 2.3). É o caso previsto na R-5 da
`V-ensaio-f1.md` ("antes de descartar uma execução por F-9, ler o `saude.csv`").

**A hipótese de que a escrita do próprio workload satura o disco está refutada pelos dados.** O travamento do n=3 é
externo ao workload e, pelo `typeperf`, nasce no disco físico do host (seção 3). O n=4, que gravou o maior CSV
(1.710.922 linhas), teve a janela h4 mais limpa já medida nesta VM (pior janela de 10 s = 0,075 s). **O h4a descartaria 1
das 2 execuções com carga**, e não "quase todas".

## 2. O que o ensaio informa ao rebaseline

### 2.1 Linha do tempo e números (instantes relativos à âncora nas execuções com carga)

| | n=1 sem carga | n=2 sem carga | n=3 S1 sync | n=4 S1 async |
|---|---|---|---|---|
| disparo | — | — | +49,009 s | +49,004 s |
| Δdet isn1 / isn3 | 41,644 / 41,640 s | 43,437 / 43,434 s | 46,155 / 46,150 s | 48,034 / 48,028 s |
| vista sem isn2 (isn1) | — | — | +95,3 s | +97,2 s |
| `podman start` (linha do injetor até o OK) | 0,127 s | 0,141 s | **8,448 s** | 0,193 s |
| `t_reinicio` (start) | — | — | +109,3 s | +109,6 s |
| volta (3 HEALTHY sem REBALANCING, desde o start) | 5,208 s | 5,358 s | 13,594 s | 10,454 s |
| ExitCode | 137 | 137 | 137 | 137 |
| ERROR_TRANSPORT | — | — | 24 | 8 |
| latência máxima na janela da falha | — | — | 11,148 s | 10,762 s |
| K3 (VerificadorSanidade) | — | — | ACEITARIA | ACEITARIA |
| C1..C3 | — | — | ok (dif. terços 0,19 p.p.; INVALID 5,24 %) | ok (0,01 p.p.; 5,19 %) |

- **Δdet** ficou em 41,6 a 48,0 s, dentro da faixa [32, 49] s explicada pelo FD_ALL3 (timeout 40 s, verificação a cada
  8 s, VERIFY_SUSPECT2 1 s). A vista sem isn2 caiu em +95 a +97 s, dentro dos "≈ +89 a ≈ +98 s" esperados na C2 §2. O
  n=4 (48,03 s) ficou a 1 s do topo da faixa. A faixa é explicação, não critério (o F-4 exige ≥ 30 s). A subida
  monótona 41,6 → 48,0 nas 4 execuções é compatível com a fase aleatória da verificação do FD_ALL3. Com 4 pontos, não há
  como afirmar mais que isso.
- **Volta** de 5 a 14 s depois do start, isn2 de volta a ≈ +109 s como previsto. O start lento do n=3 (8,4 s contra
  0,13 a 0,19 s) e a volta mais longa dele caem no fim do travamento do host (seção 3.3).
- **K3 sob o F1 silencioso: 2 de 2 aceitariam.** A ressalva da C2 §5 ("K3 sob o F1 novo pode recusar por razões que
  não são defeito") não se materializou no ensaio.
- **ERROR_TRANSPORT**: todos caem entre +45 e +95 s (um no n=4 a +115 s), isto é, entre o isolamento e a vista nova.
  São `SocketTimeoutException` de 2.000 ms com 3 tentativas, contra isn2 calado.
- **Efeito do F1 silencioso na vazão** (contagem de operações por intervalo de 5 s, lida do CSV por `start_ns`):

  | intervalo | n=3 sync | n=4 async |
  |---|---|---|
  | +40 a +45 s | 5.614 | 8.271 |
  | +50 a +95 s (9 intervalos) | 1 a 18 por intervalo | 6 a 94 por intervalo |
  | +100 a +140 s | 16.154 a 33.750 | 13.946 a 44.589 |

  Nos ~46 s entre o isolamento e a vista nova, as 4 *threads* ficam presas em operações contra chaves de isn2, nos dois
  modos. Esse é o "intervalo de ≈ 40 s em que o *cluster* ainda conta com `isn2`" que a C2 §5 anunciou sem prever o
  efeito. É desfecho medido e é registrado aqui só para o planejamento. Nenhum critério muda por causa dele.
- **n=4: 2 violações I5** (divergência entre réplicas) na auditoria final, às 03:19:25Z. São desfecho, ficam em
  `violacoes-rep-001.csv` e não são lidas aqui.

### 2.2 Memória e disco da VM (observação de planejamento)

- O workload guarda os eventos em memória até o fim da repetição (`EventCsvWriter`, seção 3.1). No n=4, a memória livre
  da VM caiu de 5,94 GB (03:00:40Z) a **0,61 GB** (03:19:26Z, logo antes do *flush*), sem swap configurado. Ao sair, o
  JVM do workload devolveu ~1,2 GB. A matriz do rebaseline só tem execuções de 300 s, então isso não muda nada, mas a
  margem é pequena. Vale olhar o `free` no fim das células S2 do piloto.
- O `run-rebaseline.sh` não comprime o CSV. O `gzip -1` foi só do ensaio (E-6). O *flush* do n=4 escreveu ~163 MB
  (`vmstat`). Na campanha inteira, com ~150 execuções, isso dá na ordem de 15 a 25 GB no disco da VM.

### 2.3 O F-9 do n=4 é artefato do `saude_loop`

No `exec-4-i2003/saude.csv`, a sonda `/health` da porta 11223 (isn2) começou às **03:15:00,345Z**, ~280 ms depois do
`eth0 down` (03:15:00,062) e antes de o injetor confirmar o ExitCode 137 (03:15:00,478). Ela voltou com `http=000` e
`t_total=3,002 s`, isto é, bateu no `-m 3`. Com o `eth0` de isn2 caído e o encaminhamento de porta ainda de pé, o SYN
some sem resposta. A sonda seguinte, às 03:15:03,394, foi recusada em 85 µs, porque o encaminhamento já tinha sido
desfeito. Como o laço faz as sondas em série, o intervalo da porta 11224 virou 2 s + 3 s = **4,992 s**.

Não houve pausa. O amostrador do coordenador (PSI a 1 s em `/run/tccdiag`) teve intervalo máximo de 1,016 s no ensaio
inteiro. O `vmstat` é contínuo, e o `typeperf` do host não tem lacuna anormal perto das 03:15Z.

Consequência: **o F-9, copiado do `s5.sh`, tem um falso positivo próprio do F1 silencioso.** Basta que uma sonda à porta
de isn2 caia nos poucos centésimos de segundo entre o isolamento e a desmontagem da rede do contêiner. O *runner* do
rebaseline não usa esse F-9. O h4b usa o amostrador em tmpfs, que não depende de `curl`, então o rebaseline não é afetado.
O F-9 do n=3 (3,770 s, abaixo do limite) tem outra causa. As sondas ficaram ~1 s mais lentas cada, sem `curl` lento,
entre 03:09:49 e 03:09:53Z, no fim do travamento do host (criação de processo sob I/O travado).

## 3. Atribuição do io `full` às fases

### 3.1 O workload não escreve o CSV durante a medição

`workload/src/main/java/br/unipampa/tcc/session/EventCsvWriter.java` em `20805b0`: "Os eventos são acumulados em memória
durante a repetição e gravados de uma só vez em `flush(Path)` ao final". O `stdout.log` é a única escrita contínua do
workload, e é pequena (56 e 87 kB). No `ensaio-f1.sh`, o `gzip -1` roda depois do `wait` do workload, do verificador, do
`awk` e do C1..C3 (linhas 584 a 604). Os dois ficam, portanto, **fora da janela h4** por construção.

Rajadas de escrita da VM acima de 10 MB/s no ensaio (`vmstat`, coluna `bo`):

| instante | volume | origem | io `full` em volta | host C: |
|---|---|---|---|---|
| 03:12:57Z | 49 MB | *flush* do CSV do n=3 | 0,10 s na fase | ocioso ≥ 87 % |
| 03:13:32Z | 30 MB | *writeback* do `.gz` do n=3 (~30 s depois) | 0 | normal |
| 03:19:27Z e 03:19:32Z | 12 + 151 MB | *flush* do CSV do n=4 | 0,17 s na fase | ocioso ≥ 60 % |
| 03:20:08Z | 44 MB | *writeback* do `.gz` do n=4 (44,7 MB) | episódio de 3,42 s | **já com fila 33 às 03:20:07** |

A VM grava 151 MB num segundo sem travar quando o host está quieto. O único caso com travamento coincidiu com atividade
prévia no disco do host e veio depois do fim das execuções. Isso repete a conclusão da V-S7r §2: **o travamento é de
latência, não de volume.**

### 3.2 Fases das execuções com carga (io `full` em segundos; janelas de 10 s ≥ 1,5 s; pior janela)

**n=3 (S1 sync)**, âncora 03:07:40,811Z, janela h4 de 03:08:24,8 a 03:12:40,8Z:

| fase | intervalo UTC | io `full` | janelas ≥ 1,5 | pior |
|---|---|---|---|---|
| GO → lançamento (portão K1) | 03:06:33-03:07:36 | 0,39 | 0 | 0,09 |
| lançamento → âncora (reset, carga) | 03:07:36-03:07:40 | 0,00 | 0 | 0,07 |
| âncora → +44 s (aquecimento) | 03:07:40-03:08:24 | 0,10 | 0 | 0,04 |
| +44 s → disparo | 03:08:24-03:08:29 | 0,02 | 0 | 0,04 |
| **disparo → start** (isn2 calado e morto) | 03:08:29-03:09:30 | **12,23** | **28** | **5,38 (03:09:08)** |
| start → volta | 03:09:30-03:09:43 | 1,33 | 0 | 1,17 |
| volta → +300 s | 03:09:43-03:12:40 | 4,59 | 12 | 1,80 (03:09:57) |
| +300 s → fim (*flush*, verificador, `awk`, C1..C3, `gzip`) | 03:12:40-03:13:00 | 0,10 | 0 | 0,07 |

Na janela h4: **18,30 s de io `full`, 40 janelas ≥ 1,5 s, pior 5,38 s**. O travamento vai de 03:08:59 a 03:09:21Z na
VM (+78 a +100 s da âncora). Nesse trecho, a VM gravava de 50 a 570 kB/s, com `wa` de 24 a 42 % e `b = 1`, e o workload
estava praticamente parado (`us` de 1 a 4 %, seção 2.1). **O workload não podia ser a fonte: estava bloqueado e não
escrevia.** Um resíduo vai de 03:09:53 a 03:10:05Z (pico 1,80 s), dentro do segmento do F1.

**n=4 (S1 async)**, âncora 03:14:10,889Z, janela h4 de 03:14:54,9 a 03:19:10,9Z:

| fase | intervalo UTC | io `full` | janelas ≥ 1,5 | pior |
|---|---|---|---|---|
| GO → lançamento | 03:13:04-03:14:07 | 0,51 | 0 | 0,12 |
| âncora → +44 s | 03:14:10-03:14:54 | 0,09 | 0 | 0,04 |
| disparo → start | 03:14:59-03:16:00 | 0,23 | 0 | 0,07 |
| start → volta | 03:16:00-03:16:10 | 0,00 | 0 | 0,04 |
| volta → +300 s | 03:16:10-03:19:10 | 0,59 | 0 | 0,08 |
| +300 s → fim (auditoria, *flush* de 163 MB, verificador, `gzip`) | 03:19:10-03:19:36 | 0,17 | 0 | 0,09 |

Na janela h4: **0,88 s de io `full`, 0 janelas ≥ 1,5 s, pior 0,075 s**, com o maior CSV do ensaio.

**Sem carga (n=1, n=2)**: pior janela de 0,09 e 0,21 s. O `podman start` com volta produziu 0,00 s de io `full`.

**Resposta à pergunta da C3 §4.2** (quanto io o `podman start` produz nesta VM): em condição limpa, praticamente nada
(pior janela no segmento do F1 de 0,06 s no n=4 e 0,05 s sem carga). Como manda a C3, isso não muda a regra do segmento.

### 3.3 Distribuição das 60 janelas ≥ 1,5 s e das 180 > 0,8 s (03:00:40 a 03:39:51Z)

| trecho | > 0,8 s | ≥ 1,5 s | pior | io `full` |
|---|---|---|---|---|
| antes do ensaio (até 03:00:44) | 0 | 0 | 0,05 | 0,05 |
| ensaio até o disparo do n=3 (n=1, n=2, portão e carga do n=3) | 0 | 0 | 0,21 | 2,86 |
| n=3, disparo → fim | 61 | **40** | 5,38 | 18,39 |
| n=4, GO → fim | 0 | 0 | 0,12 | 1,72 |
| estado final e avaliação (03:19:36-03:20:43) | 33 | **18** | 3,42 | 8,56 |
| depois do ensaio (atividade do coordenador, cópia) | 86 | 2 | 1,56 | 28,13 |

Episódios (trechos contínuos de janelas ≥ 1,5 s): 03:09:01-03:09:28 (pico 5,38), 03:09:53-03:10:03 (1,80), 03:10:05
(1,52), 03:20:10-03:20:19 (3,42), 03:20:24-03:20:26 (2,10), 03:20:28-03:20:32 (2,19) e 03:21:18-03:21:19 (1,56). Os três
primeiros são um único evento no host. Os quatro últimos ficam fora de qualquer janela de medição.

### 3.4 Cruzamento com o `typeperf` do host

| trecho | latência AVHDX (máx., unidade do contador) | C: s/escrita (máx.) | C: ocioso (mín.) |
|---|---|---|---|
| n=1 e n=2 (03:02-03:06Z) | 0,033 | 6,8 ms | 30 % |
| n=3 antes do episódio (03:07:40-03:08:58Z) | 0,040 | 29,5 ms | 42 % |
| **n=3 episódio (03:08:59-03:09:21Z)** | 0,341 | **78 ms** (leitura até 210 ms) | **0 %** |
| n=3 resto da janela | 0,491 | 126 ms | 0 % (até 03:10:05Z) |
| n=4 âncora → +300 s | 0,343 | 72 ms (pontual) | **61,5 %** |
| n=4 *flush* + `gzip` | 0,053 | 19 ms | 60 % |

O disco físico C: do host ficou **0 % ocioso de 03:08:59 a ~03:10:05Z** (~66 s), com fila de 4 a 16 e escritas de 40 a
78 ms. Enquanto isso, o AVHDX da VM recebia só 5 a 30 escritas por segundo. O próprio `typeperf`, que grava no C:, ficou
**29,3 s sem amostra** (03:09:36 a 03:10:05Z), e foi a única lacuna acima de 4 s em 42 min. A memória livre do host ficou
em ~10 GB, sem paginação. Conclusão: o disco do host estava ocupado por algo fora da VM, e a VM travou porque tinha
escritas pequenas (logs dos contêineres, journald) na fila nesse intervalo. O `typeperf` atual não tem bytes por
processo, então a fonte não é identificável com estes dados.

**Indícios sobre a fonte (não conclusivos):**
- O serviço *Software Protection* (`sppsvc`) reiniciou às 03:08:36Z (evento 16394) e se reagendou às 03:09:07Z (16384).
  Nas ~112 min de `typeperf` do host em 04-05/11, 2 dos 5 reinícios do SPP foram seguidos de C: a 0 % ocioso (01:12Z e
  03:08Z), e 3 não. Os episódios de 23:58Z, 00:25Z e 00:52Z não tiveram SPP. O SPP não é necessário nem suficiente.
- Os episódios do host de 23:58, 00:25 e 00:52Z têm espaçamento de ~27 min. Somando 5 períodos a 00:52Z chega-se a
  ~03:07Z, perto do de 03:08:59Z. Isso sugere uma tarefa periódica do host, mas é uma hipótese, sem prova.
- O log `Microsoft-Windows-TaskScheduler/Operational` está desabilitado, por isso não houve como cruzar as tarefas
  agendadas.

### 3.5 O que h4a e h4b teriam decidido (C3 §3)

| execução | h4a (janela inteira) | h4a com o segmento do F1 excluído (C3 §4.2) | h4b (maior intervalo) | veredito h4 |
|---|---|---|---|---|
| n=3 | 40 janelas ≥ 1,5; pior 5,38 | **27 janelas fora do segmento [03:09:28, 03:10:30]; pior 5,38 (03:09:08)**; dentro: 13, pior 1,81 | 1,013 s | **descartada-infra (h4a)** |
| n=4 | 0; pior 0,075 | 0; segmento com pior 0,061 | 1,016 s | ok |

O segmento do F1 não salva o n=3, porque o travamento começou ~30 s antes do start. A conferência da C3 §3.4 (2 ou mais
execuções `none` com pior janela entre 1,1 e 1,5 s) não se aplica ao ensaio, que só tem F1. O n=4 ficou em 0,075 s, longe
dessa faixa.

## 4. Saídas avaliadas

A causa não é a escrita do workload. As saídas voltadas ao workload, portanto, **não atacam o problema**. A tabela fica
para registro.

| saída | efeito esperado aqui | exige mudar | recomendação |
|---|---|---|---|
| CSV em tmpfs na VM e cópia no fim | nenhum: o CSV já é gravado fora da janela | *runner* (`--csv-dir` no `montar_cmd`, hash `d95305fe…`) e passo de cópia; risco de memória (~165 MB em RAM com 0,6 GB livres no fim do n=4) | não fazer |
| *buffer* maior | nenhum: o escritor já guarda tudo em memória e grava no fim | jar (`5eced136…`) | não se aplica |
| `gzip` depois da medição | já é assim no ensaio; o *runner* do rebaseline nem comprime | — | nada a fazer |
| `sync` no fim de cada execução, antes do portão seguinte | tira o *writeback* do CSV do caminho da execução seguinte de forma determinística; hoje ele já termina em ~30 s, durante o K1 (60 s) da seguinte | *runner* (congelado) | não necessário; só para *scripts* novos fora do congelado |
| reduzir o journald / logs dos contêineres | marginal: menos escritas pequenas expostas ao travamento do host; não remove a latência do host | configuração da VM (covariável C1 §2), não *runner*/jar/PR | não prioritário |
| separar o disco | nenhum: o host tem um só disco físico (`PhysicalDisk(0 C:)` = `_Total`); outro VHDX no mesmo SSD não isola a latência | hardware | não disponível |
| **identificar a fonte no host** | ataca a causa | nada congelado; covariável do host (C3 §7), com registro | **fazer antes do piloto, só com coleta** (seção 5) |

**O h4 deve distinguir travamento autoinduzido de externo?** Recomendo **não mudar o veredito**. Um travamento na janela
contamina a medição seja qual for a origem, e o desenho já põe o I/O próprio do workload fora da janela. O que pode ser
pré-registrado, sem olhar desfecho, é **uma coluna diagnóstica** no `h4.py fase`, porque o amostrador já grava a linha
`sda` do `/proc/diskstats`. Para a pior janela de cada execução, registrar os MB escritos pela VM nos mesmos 10 s e
classificar como "volume" (≥ 50 MB escritos na janela) ou "latência" (< 5 MB), com "indeterminado" no meio. A coluna só
informa: se um h4 do piloto cair em "volume", o caso vai ao Gestor antes da noite 1. Como o `h4.py` entra na lista SHA-256
da adenda da DT-018, a coluna exige DT nova e lista nova **antes** do piloto.

## 5. O piloto pode rodar?

**Pelo lado do workload, sim, como está.** Nada no jar, no *runner* ou no pré-registro precisa mudar por causa deste
ensaio. A taxa de h4 é o que preocupa. Juntando o ensaio às ~112 min de `typeperf`, o host teve ~2 eventos de disco por
hora sem atribuição, e nem todos chegam à PSI da VM. Com exposição de ~5 min por execução (256 s de janela mais a duração
do evento), isso dá **8 a 15 % de execuções h4**, acima dos 7 % centrais da C3 §6 e abaixo do limite de viabilidade de
~25 %. Com 15 %, são ~20 reposições h4 na campanha, dentro do teto de 30 (C3 §4.4) mas sem folga grande.

Antes do piloto, sem mudar nada congelado:

1. **Instrumentar o host** (diagnóstico, fora do critério). Acrescentar ao `typeperf` os contadores
   `\PhysicalDisk(0 C:)\Disk Read Bytes/sec`, `Disk Write Bytes/sec` e `Disk Transfers/sec`, mais um amostrador de
   `\Process(*)\IO Read Bytes/sec` e `IO Write Bytes/sec` (os 5 maiores a cada 1 s, em arquivo à parte). Habilitar o log
   `Microsoft-Windows-TaskScheduler/Operational` (`wevtutil sl … /e:true`). Assim o próximo evento fica atribuível.
2. **Definir agora como se contam os episódios da C3 §6** (condição dos 3,5 episódios por hora). Pela letra
   ("trechos contínuos"), o evento único de 03:09Z conta como **3** episódios, e o ensaio sozinho já daria 9 por hora.
   Contado como evento do host, dá 1 em 20 min, ou 3 por hora. A regra precisa ser fixada antes de qualquer dado do piloto.
   Proponho fundir em um episódio os trechos separados por menos de 60 s.

## 6. Pontos para o Gestor

- **G-1.** Aceitar a leitura do F-9 do n=4 como artefato do `saude_loop` (seção 2.3), registrando o resultado formal de
  3 de 4 e a leitura de 4 de 4 informativas. Recomendação: aceitar, sem repetir o ensaio, que "mede, não decide".
- **G-2.** Regra de contagem de episódios da C3 §6: fundir trechos a menos de 60 s (recomendado) ou manter a letra.
  Decidir antes do piloto, por DT.
- **G-3.** Instrumentação do host da seção 5, item 1, antes do piloto. É diagnóstica e reversível. O log do
  TaskScheduler é mudança de configuração do host e deve ser registrado como covariável (C3 §7).
- **G-4.** Coluna diagnóstica volume/latência no `h4.py` (seção 4). É opcional e, se aprovada, exige DT e lista SHA-256
  nova antes do piloto.
- **G-5.** Decidir se o piloto espera a atribuição da fonte no host (mais um ou dois ciclos de observação de ~30 min com
  a instrumentação nova) ou se roda já, aceitando 8 a 15 % de h4 como estimativa de trabalho. Recomendação: rodar com a
  instrumentação nova ligada. O piloto em si serve de observação, e a condição dos 3,5 episódios por hora continua valendo.
