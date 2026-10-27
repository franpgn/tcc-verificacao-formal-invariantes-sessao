# Validação V-S1 do gate v4, 2026-10-27

Validador: Senior. Alvo: `runs/vm-s1-2026-10-27/` (`00` a `14`) e `docs/relatorio-s1-2026-10-27.md`, commit `6834311` da branch `test/b29-gate-v4`. Referência: `decisoes-senior-2026-10-16.md` seções 5.1 a 5.5 (em `wt-plano-ctrl/docs/`) e `pre-registro-rebaseline-aprovado-2026-10-23.md` seção 5. Li os brutos, não o resumo. Para fechar dois pontos que os brutos não resolviam, fiz leituras adicionais: o journal da VM por ssh (só leitura: `journalctl`, `ls`, `zgrep`, `podman inspect`) e o log de eventos do Windows do host (`Get-WinEvent`, só leitura). Não houve start, stop, `tc` nem escrita na VM.

**Veredito: S1 APROVADO COM RESSALVAS.** Nenhuma ressalva invalida o que o S1 mediu. O S2 fica **bloqueado** até o Gestor decidir como o cluster sobe (ponto 1) e o que é a S4 (ponto 2). O plano previa as duas coisas com o cluster de pé, e isso deixou de valer.

## 1. Medido contra alegado

| Alegação do relatório | Bruto | Conferência |
|---|---|---|
| sha256 do bundle `39e9f100…f6c0`, ref única `9eadbdb` | `02` | confere: `SHA256_OK`, `git bundle verify` ok, `list-heads` = `9eadbdbe…c615e147` |
| 1º checkout recusado, nada alterado | `02` | confere: rc=1, `Aborting`. Os comandos que vêm depois em `02` (CR, hashes) rodaram ainda na v3 e por isso acusam `runs/gate-netdegrade` ausente. Valem os de `04`, refeitos após o checkout |
| 156 conflitantes idênticos aos blobs, backup, checkout, 0 ausentes | `03` | confere: `comparados=156 diferentes=0`, tar com 376 = `find` 376, `mv` de 156, `HEAD_OK`, `cmp` 156/0, `ausentes_apos_checkout=0`. Os `novos_apos_checkout=4` não estão listados no bruto (ressalva R-4) |
| 9 hashes de script e modos git | `04` | confere um a um com a seção 2 de `relatorio-gate-v4-2026-10-16.md`. 0 CR |
| B0 | `05` | confere: 9 binários presentes, `iproute2-6.1.0`, `normal.dist`, `6.8.0-147-generic`, systemd 255, podman 4.9.3 (rootless e sudo). Os 3 módulos estavam ausentes antes e presentes depois do `modprobe`, com `.ko.zst` do 147 |
| netdegrade 306/0/0 com flock real | `06` | recontado: 306 linhas `PASS:`, 0 `FAIL`, 0 `SKIP`. `suite_rc=0`, 84 s. T-nd-21a..c presentes, HEAD `9eadbdb`, `G_TESTE` 0 |
| partition 127/0/1, SKIP estrutural | `07` | recontado: 127/0/1. O SKIP é o T-par-6 (`run-baseline.sh` sem F2, B-29.1). T-par-15 roda. `suite_rc=0`, 38 s |
| shims antes do PATH real | código das suítes | confere: `PATH_SEM_PODMAN="/usr/bin:/bin"`. O T-nd-21 põe `shims-semflock` (podman, sudo, nsenter, tc, ping, readlink) antes de `dirname $(command -v flock)` = `/usr/bin`. O `tc` real (`/usr/sbin`) fica fora do PATH em todos os casos |
| G_TESTE ausente, lock, timers, tmux, java | `00`, `14` | confere. Lock criado às 17:59 e livre no fim |
| cluster `created`, Pid 0, RestartPolicy vazia, StartedAt de 16/10 | `08`, `14` | confere. Os StartedAt batem com o `rec1.out` em `12` até o nanossegundo |
| `recriar.sh` criou os contêineres atuais | `10`, `12` | confere: em `podman events` aparecem `died`/`cleanup`/`remove` às 08:54:36–38 e `create`/`start` às 08:54:38–41. Depois disso só há o `system refresh` de 20/10 |
| desligamento abrupto logo após 2026-10-18 07:49:49 | `09`, `10` | confere: o boot -1 termina em 07:49:49, não há linha de desligamento ordenado nem `ISPN080002` depois de 16/10 08:54, e o último `health_status` é de 07:49:49. **Causa fechada nesta V-S1** (seção 3) |
| cluster/ idêntico v3 → v4 | `12` | confere: `git diff --stat` vazio, `podman-compose.yml` `46d5ff7c…a67cd` |
| "dois comandos levaram ~2 min" | `05`, `08` | **os brutos não sustentam a afirmação como está escrita** (seção 4) |

## 2. Ressalvas

- **R-1 (registro).** Seis blocos não têm a linha `$ comando`: `10` às 18:10:28, o primeiro de 18:12:34, 18:16:17 e 18:16:26; `11` às 18:14:56 e 18:15:06. Por isso não dá para refazer a partir dos brutos o "0 linhas depois de 09:07", o "0" antes da lista de `ISPN080002` nem o "50". As conclusões que dependiam deles estão corroboradas por outros blocos e pela seção 3, então nada cai. A partir do S2, todo bloco deve registrar o comando, o carimbo do cliente no início e no fim, e um `date -Is` dentro do comando remoto.
- **R-2 (registro).** O carimbo `###` é o do cliente no **início** do passo. O fim não é registrado, de modo que a diferença entre dois carimbos mistura a duração do comando com o tempo do cliente. O `13` omite o comando real (`ssh ... true`) e o `time` bruto.
- **R-3 (covariável nova).** O kernel mudou: `6.8.0-124` nas REP=1..3, G-5 e G-6, `6.8.0-147` agora. O S2 (L-0) existe para provar exatamente isso no `tc`. Para a S4 e para o complemento do pré-registro do rebaseline, `uname -r` passa a ser covariável registrada. O `unattended-upgrade` está ativo (`APT::Periodic::Unattended-Upgrade "1"`, timer `apt-daily-upgrade` habilitado) e foi ele que instalou o 147. Ver P-6.
- **R-4 (resíduos).** Ficam 4 caminhos não versionados na VM (`g1-rep3/hotrod/`, `recriacao/pre-recriacao-inspect-isn{1,2,3}.json`), que são brutos da REP=3/R4. O Worker deve trazê-los ao host e versioná-los na `test/b29-gate-v4`, ou declarar que ficam fora. Os `novos_apos_checkout=4` devem ser listados. Os backups em `~` (`.tgz`, `-conflito/`, lista) ficam até a V-S7.
- **R-5 (estado deixado).** `sch_prio`, `sch_netem` e `cls_u32` ficam carregados (refcount 0). Não muda nenhum critério. O L-0 deve registrar esse estado como ponto de partida.

## 3. O desligamento de 18/10: causa no host

Fiz a leitura no host Windows (só leitura, `Get-WinEvent`, log System):

- `Kernel-Power 41` em 2026-10-20 13:31:14 (local, UTC−3): "rebooted without cleanly shutting down first". `BugcheckCode=0`, `PowerButtonTimestamp=0`, `LongPowerButtonPressDetected=false`, `SleepInProgress=0`.
- `EventLog 6008`: "The previous system shutdown at 04:49:35 on 18/10/2026 was unexpected", ou seja, 07:49:35 UTC. A VM ainda registrou eventos até 07:49:49 UTC. O 6008 usa o último registro periódico do host e por isso pode ficar alguns segundos antes do instante real.
- O SO do host voltou em 2026-10-20T16:31:04Z e a VM deu boot às 16:31:46Z. A VM sobe sozinha com o host (AutomaticStartAction ativo).
- Não há `WER-SystemErrorReporting 1001` (bugcheck) no período. O último evento do host antes da queda é de 04:42:57 local.

Conclusão: **o host Windows caiu sem desligamento ordenado** (perda de energia ou travamento duro, sem tela azul nem botão de energia) por volta de 07:49:49 UTC de 18/10, e ficou desligado até 20/10 16:31 UTC. A VM não teve falha própria. `Get-VM` foi negado sem elevação, então AutomaticStopAction/StartAction não foram lidos (P-6).

## 4. As latências de ~2 min estão no cliente, não na VM

Fiz a leitura do journal da VM com `-o short-precise`:

- **Caso A (passo `lsmod`, carimbo 17:55:48).** Entre 17:55:46.34 e 17:57:51.07 nenhuma conexão ssh foi aceita. A conexão desse passo é aceita às 17:57:51.07 e termina às 17:57:51.41. O tempo se perdeu **antes** da autenticação: processo ssh no Windows, NAT do Default Switch ou o próprio executor do Worker. Na VM o passo levou 0,34 s.
- **Caso B (`sudo podman ps -a --format ...`, carimbo 18:05:14).** A conexão é aceita às 18:05:22.04, o `sudo` abre e fecha entre 18:05:22.347 e 18:05:22.366 (19 ms), e o **cliente** só desconecta às 18:07:30.44 ("disconnected by user"). O podman não travou. O cliente segurou a sessão por 2 min depois de o comando terminar.
- **Caso C (não relatado).** O primeiro bloco de `14` tem carimbo 18:17:51, mas o `date -Is` remoto marca 18:18:03. São 12 s de atraso, de novo do lado do cliente.

## 5. Recomendações

### 5.1 Ponto 1: como subir o cluster para o S2

Recomendo **recriar com o XML atual da v4** (`cluster/`, idêntico ao da v3 e ao da REP=3), por `podman-compose down` + `up -d`. É o mesmo procedimento da R4 de 16/10.

Por que não `podman start` dos contêineres existentes:
1. Ele não preserva histórico nenhum, porque as JVMs são novas e a vista começa do zero. Ou seja, não traz o benefício que justificava "cluster atual, sem recriar".
2. Ele carrega um estado que a REP=3 não tinha. Li na VM que os três contêineres têm `___global.lck` e `___global.state` de 16/10 08:54:4x na camada gravável (`/opt/infinispan/server/data/`), resíduo do desligamento sem `ISPN080002`. Não verifiquei o que o Infinispan 15.0.21 faz com um lock de estado global pendente: pode recusar a subida, avisar ou ignorar. Qualquer dessas respostas é uma diferença não controlada em relação à REP=3, e a recriação a elimina.

Por que não recriar já com o XML do B-42.1: o pré-registro do rebaseline (seção 5) pede o cluster "criado do zero depois do S7" e registra o instante de criação no complemento. Quatro caches mudam o trabalho de rebalance no merge, e o t_merge da S4 deixaria de ser comparável com REP=1..3. A regra fica: **S2..S7 com o XML atual; XML do B-42.1 só depois da V-S7.**

Condições da recriação (preâmbulo do S2, sem `tc`):
- O `recriar.sh` não está versionado. O Worker o versiona verbatim (sha `5b1cca41…0766`) em `runs/gate-b29/`, com teste por `--dry-run` ou shim no host, ou então escreve os comandos no relatório. O `nsenter ... tc qdisc show` do script só lê, então é aceitável.
- Antes do `down`: `podman inspect` dos três para `runs/vm-s2-*/recriacao/`, e cópia dos `___global.*` (é um achado e vale guardar).
- Depois: portão 5.1 completo (≥ 120 s HEALTHY×3, caches vazios, `noqueue` nos 3 netns, nenhuma mudança de vista nos 5 min finais) **antes** do L-0. Registrar o instante de criação e o `topologyId`.
- Esse passo altera o estado da VM. Não é injeção, mas é uma escrita fora do roteiro aprovado, então precisa da aprovação explícita do Gestor (P-1).

### 5.2 Ponto 2: a REP=4B

A pergunta original (histórico do cluster contra coleta pesada) **não tem mais resposta com os meios atuais**. A célula "cluster com histórico" exigia os contêineres de 16/10 vivos, com uma partição e um merge acumulados. Isso se perdeu, e recriar um histórico artificial (uma partição de aquecimento antes da medição) não reproduz o envelhecimento da REP=2 e custaria mais um `go`.

Recomendo **redefinir a S4 como REP=4R** (réplica da célula da REP=3), em vez de retirá-la:
- Configuração idêntica à da REP=3: cluster recém-criado, `COLETA=minima`, saúde a 2 s, `G1_LOG_POS_S=30`, janela de 90 s em isn3, `REP=4` explícito no `go`.
- Perguntas que a REP=4R responde: (a) o `inject-partition.sh` se comporta igual no kernel 6.8.0-147 (janela de 96,04 ± 0,2 s, split dos dois lados, `MergeView`, sem `clsact` residual)? Esta é a primeira partição real no kernel novo, e é melhor fazê-la num roteiro conhecido do que no G-5b, que mata o injetor. (b) Um segundo ponto na célula "novo + mínima": se der perto de 46 s, a REP=3 ganha uma réplica. Com n = 2 ainda não é estimativa de variância.
- O que **deixa de ser lido**: nenhum resultado da REP=4R separa histórico de coleta pesada. A REP=2 (188 s) segue sem explicação, como limitação declarada. A REP=5 (variante A) da seção 5.5 também perde sentido. Se o Gestor quiser essa pergunta de volta, ela passa a ser um experimento próprio com o histórico definido de antemão, fora desta sequência.
- Pré-condições: o portão 5.1, a idade do cluster e o `uname -r` são registrados. A idade vai passar de ~1 h (S2 e S3 antes), contra ~10 min na REP=3. Declaro isso como diferença e não recomendo recriar de novo antes da S4 só por causa da idade.
- Se a REP=4R der mais de 60 s, para-se como na 5.5. A leitura passa a ser "a célula novo + mínima não é estável", não "histórico".

A ordem **S4 → S5 continua**, mas a justificativa muda. Antes era preservar o histórico. Agora é que a REP=4R precisa de um cluster sem nenhuma injeção prévia que toque tráfego, como a REP=3 teve. L-0 e L-0c não tocam tráfego (filtro TEST-NET), mas L-1 pode mudar a vista. A cláusula da 5.2 item 2 ("se L-1 vier antes, ela não pode ter mudado a vista") continua valendo com o novo motivo.

### 5.3 Ponto 3: checkout no lugar, sem worktree

**Aceito.** O procedimento foi conservador e está provado no `03`: comparação por `hash-object` com o blob da v4 (156/0), tar integral conferido (376 = 376), `mv` (nada apagado), `cmp` após o checkout (156/0) e 0 ausentes. Há ainda um motivo técnico a favor: os rótulos do compose fixam `working_dir=/home/server1/tcc-gate/cluster`. Um `git worktree` em outro diretório faria o `podman-compose` operar com outro caminho de projeto, o que atrapalha `down`/`up` e a rastreabilidade. As ressalvas são a R-4 e o aviso do relatório: voltar `~/tcc-gate` para a v3 apaga os 156. Ninguém volta para a v3 sem novo backup.

### 5.4 Ponto 4: latência do ssh

**Não bloqueia o S2**, porque a causa está no lado do cliente (seção 4) e nada do caminho crítico depende de ssh novo. O injetor roda em `tmux` na VM, o dead-man é `systemd-run` local e o `flock` é local. O risco real é para a **intervenção manual** do operador: um `--heal` manual digitado em sessão ssh nova pode demorar 2 min para chegar. Mitigações, todas baratas e sem tocar a VM:
- Antes de cada `go`, deixar aberta e já autenticada a sessão interativa do `tmux` e uma segunda sessão de reserva com o comando de `--heal` pronto, e não abrir conexão nova na emergência.
- Fazer do registro de R-1/R-2 parte do S2: carimbo do cliente no início e no fim, e `date -Is` remoto. Assim, se acontecer de novo, a separação entre cliente e VM sai dos brutos.
- Pré-condição do `go` nas S4..S7: 3× `ssh true` em menos de 2 s cada, registrado.

A investigação da causa no Windows (OpenSSH, Default Switch ou o executor do Worker) fica como tarefa de fundo, fora da sequência.

### 5.5 Ponto 5: ressalva 1 da V-S0 (`AccuracySec=1s`) no S3

**De acordo.** O S3 (L-0c) é o primeiro uso do `armar` de `l0c.sh` contra systemd real. Ao critério da 5.4 da decisão de 16/10 (disparo entre 30 e 40 s) acrescento: `systemctl show <timer> -p AccuracyUSec` = `1s`, `systemctl cat` do timer e do service nos brutos, e o carimbo do disparo no journal. A mesma conferência se repete no S6/S7 para o `armar` de `g5b.sh`.

### 5.6 Ponto 6: registro e mitigação para as noites do rebaseline

**Entra no `registro-de-problemas.md` como P-28** (o último é P-27), na branch de integração e não nesta, quando o Gestor liberar. Conteúdo: incidente de infraestrutura, causa no host (seção 3), efeitos (cluster em `created`, perda do histórico, pergunta da REP=4B extinta, kernel trocado, `___global.lck` pendente), decisão tomada sobre a S4 e mitigação.

Mitigação recomendada:
1. **Host (o que causou o incidente).** O Gestor confere a alimentação (no-break, ou o notebook ligado na tomada com a bateria saudável), desliga a suspensão e a hibernação nas noites, pausa o Windows Update na janela da campanha e lê, em PowerShell elevado, `Get-VM tcc-f3 | Select AutomaticStopAction,AutomaticStartAction`. Recomendo `AutomaticStopAction=ShutDown`, para que um reboot ordenado do host gere `ISPN080002` na VM.
2. **VM.** Suspender `unattended-upgrades` durante a campanha, ou pelo menos segurar o kernel com `apt-mark hold`. É escrita na VM, então vai para aprovação. Registrar `uname -r` por noite.
3. **Runner.** No início e no fim de cada noite, e antes de cada execução, registrar `/proc/sys/kernel/random/boot_id`, `uptime -s` e os `StartedAt` dos 3 contêineres. É parada automática nova (h) se o `boot_id` mudar, se o `StartedAt` de isn1/isn3 mudar, ou se o de isn2 mudar fora de uma execução F1. A execução em curso numa parada (h) é descartada como erro de infraestrutura, não como recusa da célula. Isso é uma mudança no pré-registro aprovado e precisa do Gestor (P-5).
4. **Sem restart policy.** Recomendo **não** pôr `restart: always`/`on-failure` no compose:
   - (a) Muda `podman-compose.yml`, que é um dos fixos do pré-registro.
   - (b) O F1 é `podman stop` + 60 s + `podman start`. A JVM sai com 143 no SIGTERM e, se o podman 4.9.3 reiniciasse um contêiner parado pelo usuário (não verificado aqui), a parada de 60 s encurtaria em silêncio e contaminaria a célula F1. Com `on-failure` esse risco é ainda mais direto.
   - (c) Depois de uma queda do host, o runner no `tmux` morre do mesmo jeito. Um cluster que volta sozinho não retoma a campanha e só **esconde** a queda, já que o portão K1 passaria. O comportamento atual (contêineres em `created` depois do boot) é a parada segura que se quer. Ela deve ser detectada pelo `boot_id` e não contornada.

## 6. Pontos que precisam do Gestor

- **P-1.** Autorizar a recriação do cluster com o XML atual da v4 como preâmbulo do S2 (5.1), com o versionamento do `recriar.sh`. A alternativa é `podman start`, que o Senior não recomenda.
- **P-2.** S4: redefinir como REP=4R (recomendado), manter a REP=4B com ressalva (não recomendado, porque não mede o que diz medir) ou retirar a S4. Se retirar, a primeira partição real no kernel 147 passa a ser o G-5b-A.
- **P-3.** Aceitar a REP=2 (188 s) como não explicada (limitação declarada) e dispensar a REP=5 da seção 5.5.
- **P-4.** Abrir o P-28 no `registro-de-problemas.md` da integração.
- **P-5.** Acrescentar ao pré-registro do rebaseline, como complemento antes do piloto, a parada (h) por `boot_id`/`StartedAt` e o descarte da execução interrompida por infraestrutura. Registrar `uname -r` e a versão do podman no complemento da seção 9.
- **P-6.** Medidas no host (energia, suspensão, Windows Update, `Get-VM` elevado) e, na VM, suspender o `unattended-upgrades` ou segurar o kernel.

## 7. Próximo passo do Worker (só depois de P-1 e P-2)

S2 = preâmbulo de recriação (5.1) → portão 5.1 → L-0, com o registro de R-1/R-2. Antes disso, sem VM: R-4 (trazer e versionar ou declarar os 4 resíduos e listar os 4 novos) e o versionamento do `recriar.sh`. Parar ao fim do S2 para a V-S2.
