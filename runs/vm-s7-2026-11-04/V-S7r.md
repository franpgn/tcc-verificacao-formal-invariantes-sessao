# V-S7r: análise do S7 repetido (G-5b-B), 2026-11-04

Autor: Senior. O bloco rodou de 23:29:39 a 23:41:32Z, com GO às 23:32:06Z (`go.log`, sha `f6483c5`). VM `172.27.114.62`, `boot_id` `f9e98b74-…` no início e no fim, mesmo cluster do S5, do S6 e do S7 anterior. `s7.sh` com sha256 `2385609e…`, igual ao do S7 anterior. Host em H0, segundo o coordenador.

Brutos em `vm-20261104T232939Z/`. Conferi no host: `MANIFEST.sha256` (47 arquivos), `g5b-B.sha256` (24) e `copia-host.sha256` (58), todos OK. Os acréscimos do coordenador ficam fora do `MANIFEST` e dentro do `copia-host.sha256`: `checagem-pre-coordenador/`, `coleta-paralela-coordenador/` (`vmstat 1` e PSI a 1 s, de 23:29:37 a 23:41:53Z) e `journal-vm-23h32-23h36.txt`.

O que rodei: só leitura dos brutos e `sha256sum`. No host, sem administrador: `Get-WinEvent`, `Get-Counter`, `powercfg /q`, `Get-MpComputerStatus` e `ls` de `C:\Hyper-V\tcc-f3\`. Nada na VM. Nenhum push.

Veredito automático: `S7: INVALIDO (1 falha, 18 OK)`. A falha é `intervalo maximo do saude-log em janela/saude.csv: 20.118 s > 2 x 2 s`.

## Resposta curta

Desta vez **não houve pausa da VM inteira**. O que travou foi o **disco virtual da VM**. Das 23:32:02 às 23:34:58Z (176 s), a PSI de I/O (`full`) somou **145 s**: durante 82 % do tempo, todas as tarefas que não estavam ociosas ficaram esperando I/O. Nesse período houve até 8 processos em estado D, `wa` de até 99 % e quase nenhuma escrita (123 KB/s, menos que os 364 KB/s de antes). A CPU não faltou (PSI de CPU perto de 0, `st` = 0) e os JVMs responderam ao REST em ~2 ms o tempo todo.

Ficaram travados os processos que dependem do disco: o podman (*healthcheck*, `logs` e `inspect`), o journald, o coletor de saúde e os próprios coletores do coordenador. O JGroups detectou o split no tempo de sempre. O que atrasou foi a **sondagem** do `g5b.sh`, que lê os logs por `sudo podman logs`.

## 1. O que pausou e por que o split só foi confirmado em +123 s

Referência: `t_start` = 23:32:35,383Z (`cronologia.txt`).

| Instante (UTC) | Fonte | Evento | Relativo a `t_start` |
|---|---|---|---|
| 23:32:02,48 | journal da VM | começa uma rodada de *healthcheck* do podman nos 3 contêineres | −32,9 s |
| 23:32:02-03 | vmstat, PSI | `b` vai de 0 a 2, `wa` vai a 44 %, PSI io `full` vai a ~1 s por segundo | −33 s |
| 23:32:06 | `go.log` | GO | −29,4 s |
| 23:32:34,04-34,12 | journal | a rodada de 23:32:02 termina, `m=+31,6 s` nos 3 nós (isn2 com `status=125`) | −1,3 s |
| 23:32:34,34 | cronologia | `t_antes_armar`, **28 s depois do GO** (no S7 anterior foi 1 s) | −1,04 s |
| 23:32:34,6 | `inj.log`, journal do `sudo` | `clsact` + 4 filtros aplicados; `event=start` às 34,650 | −0,7 s |
| 23:32:39,9 | journal | nova rodada de *healthcheck* nos 3 nós | +4,5 s |
| 23:33:18,534 | log de isn3 | `ISPN000094` `[isn3-9749\|7] (1)`; `DEGRADED_MODE` às 33:19,729/,732 | **+43,2 s** |
| 23:33:20,722 / ,724 | isn1 / isn2 | `ISPN000094` `[isn1-15077\|7] (2)` | **+45,3 s** |
| 23:34:14,85 | journal | *healthcheck* de isn1 termina, `m=+94,9 s`, `status=125` | |
| 23:34:30,38 | journal (gravado às 23:34:38) | *healthcheck* de isn2 termina, `m=+110,5 s` | |
| **23:34:38,912** | journal | ***healthcheck* de isn3 termina, `m=+119,0 s`** | |
| **23:34:38,957** | cronologia | **`t_split_confirmado`**, 45 ms depois | **+123,6 s** |
| 23:34:38,992 | cronologia | `t_kill` (o +70 s já tinha passado, então o KILL sai em seguida) | +123,6 s |
| 23:34:58 | PSI | o travamento acaba (io `full` cai de ~0,8 para 0,14 s/s) | |
| 23:34:59,807 | journal da unit | dead-man dispara, `del clsact` às 23:34:59,870 | |
| 23:35:00,802 | cronologia | `t_fire` (sondagem de 1 s) | +145,4 s |
| 23:35:44,730-,734 | 3 nós | MergeView `[isn1-15077\|8] (3)` | `del` + 44,9 s; `t_mergeview` (sondagem) = `t_fire` + 45,5 s |

Respostas diretas:

- **O corte foi aplicado na hora?** Sim. O injetor aplicou `clsact` e os filtros às 23:32:34,6. A série `tc` de isn3 tem `clsact=1` desde `t_start` (D-2 OK).
- **A detecção atrasou?** Não. isn3 viu a vista de 1 membro em +43,2 s e isn1/isn2 viram a de 2 em +45,3 s. A faixa medida antes ia de 39,8 a 46,4 s, e o S7 anterior deu +37,5 e +44,3 s. Os carimbos são do próprio JVM, e o JGroups rodou normalmente durante o travamento: ele trabalha em memória e na rede, sem disco.
- **A sondagem de confirmação travou?** Sim, por ~78 s. A partir de 23:33:20,73, os três logs já tinham todos os sinais que `split_confirmado()` procura. A confirmação só saiu às 23:34:38,957, **45 ms depois** de terminar o *healthcheck* de isn3, que estava preso havia 119 s. `split_confirmado()` roda `sudo podman logs` em isn1, isn2 e isn3, nessa ordem. A última chamada é a de isn3, e ela só voltou quando o *healthcheck* de isn3 saiu.

O mecanismo provável é este, e **não está verificado**: o *healthcheck* do podman segura o *lock* do contêiner, ou o banco de estado do podman, enquanto espera uma escrita em disco que não termina. O `podman logs` do mesmo contêiner espera por esse *lock*. O mesmo padrão aparece antes do bloco. Entre o GO (23:32:06) e a armação (23:32:34,34), o `preflight` do `g5b.sh` (que usa `podman inspect`) ficou 28 s parado e só seguiu 0,2 s depois de terminar a rodada de *healthcheck* de 23:32:02. São duas coincidências de dezenas de milissegundos entre o fim de um *healthcheck* travado e a volta de um comando podman do `g5b.sh`. Por isso dou **grau alto** para a explicação "podman preso atrás do disco" e **grau médio** para o detalhe do *lock*.

**Margem que sobrou.** `ESPERA_SPLIT` = 100 s é conferido antes de cada chamada, e não limita uma chamada já em curso. A última chamada começou antes de +100 s e voltou em +123,6 s. O corte persistiu 18,8 s depois do KILL, e o mínimo é 14 s (A6). Com uns 5 s a mais de travamento, o A6 cairia. Se a confirmação viesse depois do disparo, o `tem_clsact` falharia e o `abortar` curaria. Nos dois casos o erro é para o lado seguro, e não proponho mudar o `g5b.sh`, que está congelado.

**O dead-man também passa pelo podman.** O `--heal` da unit roda `sudo podman inspect isn3` antes do `tc qdisc del` (journal, 23:34:59). Aqui o disparo veio 1 s depois do fim do travamento e levou 0,1 s. Se o disparo caísse no meio de um travamento destes, a cura atrasaria até o podman voltar, ~2 min neste caso. Isso não muda o veredito. Mas o prazo do dead-man não é um teto rígido quando o disco trava, e isso vai para o projeto do controlador (seção 4).

**Os outros 18 critérios.** Conferi contra os brutos e concordo com eles. B2 a B7 e D-1 a D-3 deram OK. O dead-man foi o único agente de cura: 3 `del clsact` no journal do `sudo`, sendo 2 da sessão antes de `t_kill` (pré-limpeza e fim da sonda) e 1 da unit. O disparo veio em 146,457/146,450 s. O A11 passou, sem nenhuma amostra ruim no portão pós.

## 2. A lacuna de 20,1 s no saúde-log

A lacuna de 20,118 s é de 11223, de 23:32:59 a 23:33:19. Ela é maior que as do PSI por três razões.

1. **O intervalo por porta é uma volta inteira do laço** (`saude_loop`, `s7.sh:375-392`). Cada volta, para cada porta, faz `date`, `curl -o corpo`, `tr < corpo`, a truncagem `: > corpo`, três *pipelines* de `grep|cut` e um `echo >> saude.csv`. Depois vêm o teste de ssh, um `awk` e o `sleep`. São mais de 20 processos e uns 10 acessos a arquivo por volta. O amostrador de PSI do coordenador faz uma leitura de `/proc` e um acréscimo por segundo.
2. **O tempo foi gasto fora do `curl`.** Na volta que contém a lacuna, as linhas saíram às 23:33:08 (11224), 23:33:10 (ssh e 11222), 23:33:19 (11223) e 23:33:24 (11224): de 5 a 9 s entre linhas seguidas. Mesmo assim, o `time_total` de cada `curl` ficou entre 1,7 e 3,0 ms em **todas** as 316 linhas. O JVM e a rede responderam na hora. O que travou foram as escritas e truncagens de arquivo do *shell*, que no ext4 precisam do *journal* do sistema de arquivos, e a criação de processos. Isso é inferência: não instrumentei as chamadas.
3. **A PSI conta o travamento, mas o amostrador só perde amostra quando ele mesmo trava.** Os contadores `total=` são cumulativos e não perdem nada. Entre as amostras de 1 s, a PSI io `full` crescia ~0,8-1,0 s por segundo durante os 176 s. O amostrador só ficou sem gravar em dois trechos, de 4,4 s (até 23:33:09) e de 5,7 s (até 23:33:20). Esses dois trechos caem dentro da volta de 20 s do coletor de saúde, que somou também os segundos de travamento entre eles.

O coletor de saúde é o mais exposto porque trunca e reescreve arquivo a cada porta. É por isso que o critério do intervalo pegou o problema, e é para isso que ele existe.

Dois contrapontos. As lacunas do coletor de saúde começam às 23:32:06 (7,7 a 16,4 s até 23:32:33), num trecho em que o amostrador de PSI não perdeu nenhuma amostra, mas a PSI io `full` já estava em ~1 s por segundo. O journal também ficou parado de 23:33:14 a 23:33:18/19, e a linha do *healthcheck* de isn2 carimbada às 23:34:30,38 só foi gravada às 23:34:38.

## 3. A hipótese: Hyper-V ou disco do host

### 3.1 Pausa da VM pelo Hyper-V: improvável como causa principal

Durante a maior parte dos 176 s, o `vmstat` e o amostrador de PSI rodaram a cada 1,00 s, com `b` de 2 a 8. Uma VM pausada não produz amostras. A CPU não faltou: PSI de CPU ≤ 0,02 s por amostra e `st` = 0. Os JVMs responderam REST em ~2 ms. Pausas curtas da VM inteira, de até ~6 s, no lugar dos dois buracos do amostrador, **não estão excluídas**: com os dados da VM, uma pausa da VM e um amostrador preso numa escrita parecem iguais. Elas são, no máximo, uma parte pequena dos 145 s.

### 3.2 Disco virtual lento: confirmado na VM, origem no host provável e não provada

O que os dados da VM mostram:

- O travamento é **de latência, não de volume**. Durante os 176 s a VM gravou 21,7 MB. Nos 144 s anteriores tinha gravado 52,5 MB sem travar. Leituras: 76 KB. Os pedidos de I/O saíam e demoravam segundos para voltar.
- Ele é **recorrente e independente do bloco**. Na checagem curta houve 29 s de PSI io `full` entre 23:18:50 e 23:22:30 (pior janela de 10 amostras: 2,5 s). Às 23:18:49 o `avg300` de io era 23 %, e a VM estava sem bloco nenhum desde o fim do S7 anterior (23:06Z). O total desde o *boot* (21:49:28Z) era de 1.130 s às 23:18:49, ou 21 % do tempo de vida da VM, sem separar o que foi *boot* e criação do cluster. Entre episódios, a PSI io `full` fica em ~0,7-0,8 s por minuto, com pior janela de 10 s ≤ 0,8 s (23:22:30-23:28:54, 23:29:38-23:32:02 e 23:35:00-23:41:53).
- O S7 anterior (23:03:52-23:04:03) teve a mesma assinatura: *healthchecks* com `m=+33 s`, um deles com `status=125`, e o journal parado.

O que eu li no host agora, sem administrador:

| Item | Valor | Leitura |
|---|---|---|
| Disco | um só, `XrayDisk 512GB SSD`, NVMe, `HealthStatus=Healthy` | não há outro disco físico para onde levar o VHDX |
| `C:` | 86,5 GB livres de 475,9 GB (**82 % ocupado**) | SSD de consumo cheio tende a ter picos de latência, porque sobra menos área livre para o controlador |
| VHDX | `C:\Hyper-V\tcc-f3\tcc-f3.vhdx` (15,0 GB, gravado pela última vez às 18:49 locais = 21:49Z) e **checkpoint ativo** `tcc-f3_04E5ECCF-….avhdx` (746 MB às 20:46 locais, crescendo) | é outro AVHDX, não o `8824F456` de 6,34 GB do plano de execução: houve mescla ou checkpoint novo às 21:49Z, quando a VM religou |
| Contadores `Hyper-V Virtual Storage Device` do AVHDX | **`Flush Count` = 97.122** para `Write Count` = 178.194; **`Error Count` = 1**; latência agora 0,28 ms | mais da metade das escritas da VM vem com *flush*. Houve 1 erro registrado pelo dispositivo virtual desde a partida da VM. Não sei quando: o contador é cumulativo |
| `PhysicalDisk` agora | 0,5 a 2,2 ms por transferência, fila 0 | normal fora do episódio. Os contadores só mostram o estado do momento, então valem como diagnóstico apenas se forem gravados durante o episódio |
| Energia | plano Balanced; **`Link State Power Management` (ASPM do PCIe) = 2, economia máxima, na tomada** | o ASPM agressivo é causa conhecida de latência alta e de *reset* `stornvme` 129 em NVMe de consumo |
| Memória do host | 12,1 GB disponíveis de 31,8 GB; paginação 12 % | sem pressão de memória |
| Defender | proteção em tempo real ligada, `MsMpEng` ativo | não sei se `C:\Hyper-V\` está excluído (ler isso pede administrador) |
| Log `System`, 20:15-20:40 locais | nenhum evento | sem 129 desta vez. O travamento não precisa de *reset*: o 129 é o caso extremo, em que um pedido passa do tempo-limite |

O *flush* pesa por esta razão. Os *healthchecks* (3 contêineres a cada ~5 s) e o journald gravam com `fsync`. Cada `fsync` da VM vira um *flush* no AVHDX, que vira um *flush* no NVMe. Num AVHDX novo, cada bloco gravado pela primeira vez ainda exige alocação e atualização de metadados. Um NVMe de consumo sem DRAM, 82 % cheio e com ASPM em economia máxima pode levar segundos num *flush*. É uma cadeia plausível, **não provada**. As três suspeitas no host, em ordem de probabilidade, são:

1. o próprio NVMe (ASPM ou APST, disco cheio, *firmware*), com o histórico dos 129;
2. a camada do AVHDX (alocação e metadados do checkpoint);
3. o filtro do Defender sobre o AVHDX.

A memória fica de fora.

### 3.3 Como confirmar no host sem administrador

Verifiquei agora que estes contadores podem ser lidos sem elevação. O coordenador grava a 1 s durante a próxima janela de VM (diagnóstico ou bloco), num arquivo fora do diretório do bloco:

```text
typeperf -cf contadores-io.txt -si 1 -f CSV -o host-io-<UTC>.csv
```

`contadores-io.txt`, um contador por linha:

```text
\Hyper-V Virtual Storage Device(*)\Latency
\Hyper-V Virtual Storage Device(*)\Queue Length
\Hyper-V Virtual Storage Device(*)\Flush Count
\Hyper-V Virtual Storage Device(*)\Write Count
\Hyper-V Virtual Storage Device(*)\Error Count
\PhysicalDisk(*)\Avg. Disk sec/Write
\PhysicalDisk(*)\Avg. Disk sec/Read
\PhysicalDisk(*)\Current Disk Queue Length
\PhysicalDisk(*)\% Idle Time
\Hyper-V Hypervisor Virtual Processor(*)\% Guest Run Time
\Memory\Available MBytes
\Process(MsMpEng)\% Processor Time
\Process(MsMpEng)\IO Data Bytes/sec
```

Como ler o resultado, cruzando com a PSI da VM pelo relógio (com ±1 s de folga, porque os relógios do host e da VM não foram comparados):

| Durante um episódio de PSI io `full` alta na VM | Conclusão |
|---|---|
| latência do AVHDX alta **e** `Avg. Disk sec/Write` do `PhysicalDisk` alto | o disco físico (NVMe) |
| latência do AVHDX alta **e** `PhysicalDisk` normal | a camada do VHDX/AVHDX ou um filtro (Defender); `MsMpEng` ajuda a separar |
| latência do AVHDX normal | o problema está dentro da VM (improvável, mas fica testado) |
| `% Guest Run Time` dos 4 VPs em 0 com a VM sem amostrar | pausa da VM inteira |

Do lado da VM, só leitura: o amostrador de PSI do coordenador passa a gravar em **tmpfs** (`/dev/shm`) e também `/proc/diskstats` (para `sda`: pedidos em curso e tempo ponderado de I/O), copiados para o disco ao fim. Gravando em tmpfs, o amostrador não trava em escrita, e um buraco nele passa a significar pausa da VM ou falta de CPU, não disco. Depois, `journalctl -k -b --no-pager | grep -Ei 'I/O error|blk_update|storvsc|hung_task|timed out'`, para procurar o `Error Count = 1`.

### 3.4 O que só o Gestor pode rodar (PowerShell elevado, só leitura)

```powershell
Get-VM tcc-f3 | Format-List Name,State,Status,Uptime,MemoryAssigned,DynamicMemoryEnabled,AutomaticStopAction,CheckpointType
Get-VMSnapshot -VMName tcc-f3 | Format-List Name,CreationTime,SnapshotType,ParentSnapshotName
Get-VMHardDiskDrive -VMName tcc-f3 | Get-VHD | Format-List Path,ParentPath,VhdType,FileSize,Size,BlockSize,FragmentationPercentage
Get-PhysicalDisk | Get-StorageReliabilityCounter | Format-List *
Get-WinEvent -LogName Microsoft-Windows-StorPort/Operational -Oldest | Where-Object TimeCreated -ge '2026-11-04 18:00' | Group-Object Id | Select Count,Name
Get-MpPreference | Select-Object -ExpandProperty ExclusionPath
```

O `StorPort/Operational` registra latência e *timeouts* por dispositivo. Ele deu "operação não autorizada" sem elevação.

## 4. O que isso significa para a medição de referência e para o controlador

### 4.1 O que fica exposto e o que não fica

- **Os JVMs não travaram neste episódio.** REST em ~2 ms, JGroups no tempo normal, MergeView 44,9 s depois do `del` (S7 anterior: 43,9 s; S6: 53 s). O caminho de uma operação de *cache*, que é memória e rede, não toca o disco.
- **O cliente do *workload* também não grava durante a janela.** O `EventCsvWriter` acumula os eventos em memória e grava tudo no fim da repetição. Uma operação medida (`end_ns − start_ns`) não inclui escrita do CSV.
- **Mesmo assim, a cauda fica exposta.** No S7 anterior, uma resposta de isn1 ficou presa: o 401 do *digest* não saiu em 3 s, provavelmente porque a *thread* gravava log. Um JVM trava se uma *thread* que está atendendo uma operação grava log e a escrita bloqueia, ou se uma escrita em arquivo mapeado (por exemplo `hsperfdata`) segura um *safepoint*. Esse segundo mecanismo é conhecido, mas não verifiquei se ocorre aqui. As duas coisas aparecem como segundos isolados na cauda, justamente no p99 e no máximo. Uma janela de 300 s com 2-3 min de travamento contamina o p99 da execução, mesmo que a mediana pouco mude.
- **Tudo o que o *harness* mede pelo relógio dele, passando por `podman`, `sudo` ou arquivo, fica exposto.** O caso deste bloco mostra isso: o JVM marcou o split em +43/+45 s, e o *harness* o viu em +123,6 s. Na campanha, isso afeta os instantes do F1 (`podman stop`/`start` a +49 s, janela do K6 em +48..+51 s), as amostras de `netem` do F3, o K1 e o K8 (`podman logs`).
- **No controlador, isso pesa ainda mais.** Se o sensor do controlador lê estado por `podman logs`/`inspect`/`exec`, ou se o atuador chama `podman`/`sudo`, um travamento destes atrasa a detecção e a atuação em até ~2 min, e a "latência de detecção do controlador" medida passa a medir o disco do host. Tempos de detecção tirados dos **carimbos do JVM** nos logs aguentam o travamento. Tempos tirados do **momento em que o *harness* observou** não aguentam. O mesmo vale para a cura do dead-man (seção 1).

### 4.2 O pré-registro cobre isso? Não.

- **K1 a K10** (PR §6) olham saúde antes da execução, exit, sanidade do CSV, mistura e IC, invariantes, falha como prevista, sementes e `ISPN006010`. Nenhum percebe um travamento de I/O numa célula `none`. O K6 pode recusar um F1/F3 cujos instantes saiam da janela, mas só por acaso, e com o rótulo errado ("falha não aconteceu como previsto").
- **A parada (h) do complemento 1** cobre *boot* e reinício de processo, não travamento.
- **A §5 do PR e a §5 do complemento 1** pedem silêncio no host. Este episódio aconteceu com o host em H0, então o silêncio não basta.

### 4.3 Proposta: critério h4, "travamento de I/O ou pausa da VM"

É um complemento 3 ao PR, no mesmo formato e com o mesmo efeito da parada (h) do complemento 1. Ele tem de ser registrado **antes do piloto** e não olha nenhum desfecho medido.

**Coleta, fora do *runner* congelado.** Uma janela `tmux` à parte, aberta antes do `go` da fase, com um amostrador de 1 s que grava em `/dev/shm/rebaseline-infra/<fase>-psi.txt` o `date +%s.%N`, as três linhas de `/proc/pressure/{io,cpu,memory}` e a linha de `sda` de `/proc/diskstats`. O conteúdo é copiado para `~/rebaseline-infra/` ao fim da fase e versionado com os brutos. No host, o `typeperf` da seção 3.3 é diagnóstico e não entra no critério.

**Regra.** Para cada execução, na janela de medição (do `Warm-up ate` ao fim da medição, pelos instantes do `runner.log`), calcular pelos contadores cumulativos `total=` de io `full`:

- **h4a:** o travamento de io `full` acumulado em qualquer janela de 10 s de relógio (pela diferença de `total=` entre amostras com 10 s de distância) é **≥ 1,5 s**;
- **h4b:** há um intervalo **≥ 3 s** entre duas amostras seguidas do amostrador em tmpfs, o que indica pausa da VM ou falta de CPU.

Qualquer um dos dois deixa a execução `descartada-infra (h4)`. Ela fica nos brutos, não entra na análise, não conta como recusa da célula e é reposta com o próximo i livre se tinha `stdout.log` (como o item 2 da seção 1.2 do complemento 1). **Parada da fase:** três execuções h4 na mesma fase, ou uma execução sem amostrador legível, param a fase e chamam o Senior.

**De onde vêm os limiares.** Medi, nos trechos limpos de hoje, pior janela de 10 s ≤ 0,8 s e amostrador sempre ≤ 1,02 s. No episódio leve da checagem curta, a pior janela foi de 2,5 s. No episódio deste bloco, foi de 17 s, com buracos de 5,7 s. O 1,5 s fica no meio, com folga de quase 2× sobre o ruído limpo. **O piloto (noite 0, que não entra em resultado) serve para conferir o ruído com a carga real.** Se a pior janela de alguma execução `none` sem episódio visível passar de 1,1 s (75 % do limiar), o limiar é revisto por DT antes da noite 1, nunca depois.

**Para os blocos de VM que faltam (S7, ensaio do F1).** A mesma coleta em tmpfs vai junto, só como diagnóstico. O veredito desses blocos continua sendo o do critério de intervalo do saúde-log que já existe, e não muda.

Isto é proposta. A decisão é do Gestor (P-3).

## 5. Próximo passo

**Não repetir o S7 agora, e não seguir para o *rebaseline* com o S7 pendente.** Foram três episódios em ~30 min de observação (23:03, 23:18-23:22, 23:32-23:35). Um S7 novo nas mesmas condições tem boa chance de sair inválido de novo. O PR §5 exige S1..S7 aprovados antes do piloto. Pular o S7 seria uma DT de desvio, e a campanha pegaria o mesmo problema, agora dentro das medições de cauda.

Sequência recomendada:

1. **Diagnóstico sem mudar nada (~30 min, H0).** Cluster ligado e ocioso. Na VM, o amostrador em tmpfs da seção 3.3. No host, o `typeperf` da seção 3.3. O Gestor roda as leituras elevadas da seção 3.4. O objetivo é saber de onde vem o travamento, e com essa frequência 30 min devem pegar ao menos um episódio.
2. **Mudanças no host de menor custo, reversíveis e sem reiniciar a VM** (o `boot_id` e a cadeia S5→S6→S7 se mantêm):
   - **M1. ASPM do PCIe desligado na tomada:** `powercfg /setacvalueindex SCHEME_CURRENT SUB_PCIEXPRESS ASPM 0`, depois `powercfg /setactive SCHEME_CURRENT`. Hoje o valor é 2. Para desfazer, voltar a 2.
   - **M2. APST do NVMe desligado.** Os ajustes "Primary NVMe Idle Timeout" e "Primary NVMe Power State Transition Latency Tolerance" ficam ocultos no `powercfg`. Expor com `powercfg -attributes SUB_DISK <GUID> -ATTRIB_HIDE`, conferir o GUID com `powercfg /q SCHEME_CURRENT SUB_DISK` e pôr 0 na tomada. Pede administrador.
   - **M3. Espaço e TRIM no host:** liberar espaço em `C:` (hoje 82 % ocupado; por exemplo, levar para fora os *bundles*, os `.zip` e os *worktrees* antigos, se o Gestor aprovar) e rodar `Optimize-Volume -DriveLetter C -ReTrim -Verbose` (administrador).
3. **Medir de novo por 30 min**, com a mesma coleta. Passa se não houver nenhuma janela de 10 s com io `full` ≥ 1,5 s, nenhum buraco ≥ 3 s no amostrador em tmpfs e nenhum 129. Se passar, **repetir o S7** (seção 6).
4. **Se não passar**, decidir pela leitura do `typeperf`:
   - latência alta só no AVHDX: **M4**, exclusão de `C:\Hyper-V\` no Defender (troca de segurança, decisão do Gestor), e depois **M5**, apagar o checkpoint. O Hyper-V mescla o AVHDX com a VM ligada, então não precisa reiniciar, mas se perde o ponto de volta, o que reabre a A-9. Antes, copiar o VHDX e o AVHDX para um disco externo.
   - latência alta no disco físico: atualizar o *firmware* e o driver do NVMe, se houver versão. Se nada resolver, a medição nesta máquina vira uma limitação declarada, e o h4 da seção 4.3 passa a ser o que separa as execuções boas das ruins.

O que **não recomendo**:

- **Mover o VHDX:** só há um disco físico, e um disco USB seria pior.
- **Limitar a memória da VM:** não há pressão de memória na VM (6,1 GB livres, PSI de memória 0) nem no host (12 GB disponíveis).
- **Mexer no `healthcheck` do `podman-compose.yml`**, que é fonte de `fsync`: o arquivo é fixo do PR §2.

**Calendário.** A sequência leva de 1,5 a 2 h antes de um novo S7. O piloto previsto para hoje à noite (Q-18) fica para depois do S7 válido e do complemento 3 commitado.

## 6. Se o S7 for repetido de novo

Sem mudança no `s7.sh`, no `g5b.sh` nem no `portao51.sh`.

1. Na VM, mover `runs/gate-b29/g5b-B` para `runs/gate-b29/g5b-B.s7-invalido-20261104T232939Z`, sem apagar. Os brutos já estão no host e conferem.
2. Mantêm-se `--bloco-anterior` = S6 (`vm-20261104T223805Z`) e `--boot-esperado` = `f9e98b74-…`, se não houver *reboot*. O `go.log` vai ter mais uma linha `go ` (Q-12).
3. Checagem curta (`portao51.sh 600`) com o amostrador em tmpfs. **Só dar o GO se, nos últimos 10 min da checagem, não houver janela de 10 s com io `full` ≥ 1,5 s.** Essa condição de entrada fica registrada aqui, antes do bloco. Ela não muda o veredito do `s7.sh`.
4. Coleta paralela em tmpfs e `typeperf` no host durante o bloco, só como diagnóstico.

## Veredito

**S7 repetido INVÁLIDO**, pelo critério pré-registrado do intervalo do saúde-log (20,118 s > 4 s). Concordo com a falha e com os 18 OK. A causa é um **travamento do disco virtual da VM** de 176 s (PSI io `full` de 145 s), e não uma pausa da VM nem do JGroups. O travamento segurou o podman, e com ele a sondagem de split do `g5b.sh` (+123,6 s, quando o JVM tinha visto o split em +43/+45 s), o coletor de saúde e o journal. O mecanismo do dead-man passou de novo em tudo o que o S7 exige.

O problema é de infraestrutura e se repete: dois S7 seguidos caíram por ele. Ele também ameaça as medições de cauda e de detecção da campanha e do controlador, e o pré-registro não tem critério que o pegue. Antes de outro S7, vêm o diagnóstico e as mudanças baratas no host, e o h4 entra antes do piloto.

## Pontos que precisam de validação do Gestor

- **P-1.** Autorizar o diagnóstico de 30 min (seção 5, item 1): amostrador em tmpfs na VM (só leitura de `/proc`, escrita em `/dev/shm`), `typeperf` no host, e as leituras elevadas da seção 3.4, feitas pelo próprio Gestor.
- **P-2.** Autorizar M1 (ASPM), M2 (APST) e M3 (espaço em `C:` e `ReTrim`). M4 (exclusão no Defender) e M5 (apagar o checkpoint, reabrindo a A-9) só se o item 3 da seção 5 falhar.
- **P-3.** Aprovar o critério h4 (seção 4.3) como complemento 3 do PR, com DT, antes do piloto, incluindo os limiares de 1,5 s e 3 s e a parada com 3 execuções h4.
- **P-4.** Aprovar a condição de entrada do próximo S7 (seção 6, item 3) e a nova repetição depois dos itens 1 a 3 da seção 5.
- **P-5.** Registrar, para o projeto do controlador, que sensor e atuador não devem depender de `podman`/`sudo`/disco no caminho crítico. Tempos de detecção vêm dos carimbos do JVM, e o prazo do dead-man não é teto rígido com disco travado.
