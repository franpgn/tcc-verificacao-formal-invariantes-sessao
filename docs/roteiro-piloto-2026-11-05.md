# Roteiro de comandos do X-09 (segunda recriação) e do X-10 (piloto) (Senior, 2026-11-05)

> Para o coordenador, que executa sob a autorização do Gestor (G-5 da X-08v). Os comandos rodam **na VM**
> (`server1@172.27.114.62`, boot `f9e98b74-…`), salvo os marcados como **host**. Fontes: checklist 10.6
> (`wt-ctrl-integ/docs/relatorio-rebaseline-runner-2026-10-23.md`), complemento 1 §1.3 e §2, complemento 3 §2, §5 e §7,
> DT-009 parte 2 (adenda, lista `003814e9…`), DT-018 (adenda, lista `e9c44819…`), DT-019 (contagem de episódios) e o
> código congelado: `scripts/run-rebaseline.sh` `d95305fe…`, `scripts/amostrador-infra.sh` `e4cc5c79…`,
> `scripts/vigia-h4.sh` `1e7462f4…` e `analysis/h4.py` `53d7e7cb…`, lidos nesta versão. As duas listas foram
> reproduzidas pelo Senior a partir dos *hashes* registrados.
>
> Nada aqui muda arquivo congelado. A única escrita nova fora dos brutos é a cópia do `recriar.sh` com o `cd` trocado,
> igual ao X-07b.

## 0. Pré-condições (conferir antes de começar; se faltar uma, parar e avisar)

| # | Item | Quem |
|---|---|---|
| P-1 | X-02 feito: `AutomaticStopAction=ShutDown`, suspensão e hibernação desligadas, Windows Update pausado (C1 §5) | Gestor (PowerShell elevado) |
| P-2 | C3-1..C3-4 do complemento 3 confirmados pelo Gestor (a DT-018 registra que precisam de confirmação antes do piloto) | Gestor |
| P-3 | DT-019 commitada antes do `go piloto` | Senior (feito, mesmo *commit* deste roteiro) |
| P-4 | `typeperf` ampliado rodando no host (G-3) e a decisão do Gestor sobre o log do TaskScheduler registrada | coordenador / Gestor |
| P-5 | Nenhum `ensaio-f1.sh`, `run-rebaseline.sh`, `goloop.sh` ou coleta *ad hoc* em `/run/tccdiag` ativa na VM (seção 1, passo 1.2) | coordenador |

Regime do host: **H2 até o `iniciar` do amostrador (passo 2.4), H0 estrito daí até o `parar` (passo 3.6)**, incluindo
cópia, `scp`, `git`, compressão e extração (C3 §7). O `typeperf` e o vigia do coordenador são as únicas atividades
permitidas no host.

## 1. X-09: segunda recriação do *cluster* e checklist 10.6, passos 3 a 6

### 1.1 Sessão e variáveis (VM)

```bash
tmux new -s rebaseline          # janela 0: runner. Nao por flock em volta do runner (ele mesmo pega o lock)
cd ~/rb/rb-20805b0
mkdir -p ~/rebaseline-infra
export RB=~/rb/rb-20805b0
export SAIDA=$RB/runs/rebaseline
export MATRIZ=$RB/runs/rebaseline/matriz.tsv
export MATRIZ_SHA=8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35
export JAR=$RB/workload/target/session-workload-0.1.0-SNAPSHOT.jar
export JAR_SHA=5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf
read -rs RB_PASSWORD && export RB_PASSWORD     # digitar a senha do cluster (nao vai para o historico)
```

### 1.2 Estado antes de recriar (VM, só leitura)

```bash
cat /proc/sys/kernel/random/boot_id            # esperado f9e98b74-bf1f-4192-9770-b40285877292
pgrep -af 'ensaio-f1|run-rebaseline|goloop|tccdiag|vmstat 1|session-workload' || echo "nenhum processo residual"
flock -n /var/lock/tcc-vm.lock true && echo "lock livre" || echo "LOCK OCUPADO: parar"
systemctl list-units --all --no-legend 'tcc-amostrador-*' | wc -l     # esperado 0
systemctl list-timers --all --no-legend 'tcc-heal*' | wc -c           # esperado 0
ls /run/tccdiag/ 2>/dev/null                   # so as coletas antigas (diag-20261105T034339Z e a ad hoc); nenhuma piloto-*
```

### 1.3 Hashes do *bundle* e do h4 (VM)

```bash
cd $RB
printf '%s\n' \
 '3005f49ae5696dcd9ace324b08295187100fcde39c111115a89c517bc4e3c7cd *analysis/rebaseline.py' \
 '8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35 *runs/rebaseline/matriz.tsv' \
 '5b4d571986256d96c73405ac887eca55c783787ad0bc0a0c5dec558856ddc0cf *scripts/inject-crash.sh' \
 '7e68962b1b7f508ee84ca7561a20c0bba36c43ea4597d7390e7c8091cd24e529 *scripts/inject-jitter.sh' \
 '3531916a863832fd5aea050910111c69f0c9130c0bd325c3ad3f238e44a3533a *scripts/rebaseline_matriz.py' \
 'd95305fee402b1f882d387a40e81d4f83df72d7fe4b6daf810516e0627267366 *scripts/run-rebaseline.sh' \
 '5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf *workload/target/session-workload-0.1.0-SNAPSHOT.jar' \
 > ~/rebaseline-infra/lista-003814e9.sha256
printf '%s\n' \
 'e4cc5c794312afbfa7e4743ca556f37033924c51b0246f8f211718a6ee0a8780 *scripts/amostrador-infra.sh' \
 '1e7462f46923020abc82c2759b7d77937887c60b91a7af0f2dfc971b8cd4391c *scripts/vigia-h4.sh' \
 '53d7e7cb831856a58dc9df1a717df559437857aae5b57d159d42bbd84feb8b30 *analysis/h4.py' \
 > ~/rebaseline-infra/lista-e9c44819.sha256
sha256sum ~/rebaseline-infra/lista-003814e9.sha256 ~/rebaseline-infra/lista-e9c44819.sha256
#   esperado 003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4 e e9c4481989f147fc683d5923aef76213826ac2a69e15f5990336e560a57664a6
sha256sum -c ~/rebaseline-infra/lista-003814e9.sha256 && sha256sum -c ~/rebaseline-infra/lista-e9c44819.sha256
sha256sum cluster/infinispan-cluster.xml       # esperado 6d52413b1c3de47b63179430bfadb02dc29d74ba90b919c584c66ff25437778c
```

### 1.4 Instante UTC e recriação (checklist 10.6 passo 3; VM)

O `recriar.sh` faz `mkdir -p $D` **antes** do `cd`. Por isso o diretório de saída tem de ser absoluto.

```bash
# copia com o cd trocado (se a do X-07b ja existir, use-a e confira so o diff abaixo)
sed 's#cd ~/tcc-gate$#cd ~/rb/rb-20805b0#' ~/tcc-gate/runs/gate-b29/recriar.sh > ~/rebaseline-infra/recriar-rb-20805b0.sh
diff ~/tcc-gate/runs/gate-b29/recriar.sh ~/rebaseline-infra/recriar-rb-20805b0.sh   # so a linha 3 (cd) difere
sha256sum ~/tcc-gate/runs/gate-b29/recriar.sh ~/rebaseline-infra/recriar-rb-20805b0.sh

# instante UTC: ANTES do down/up. Vai em --cluster-criado-apos
export CRIADO_APOS=$(date -u +%FT%TZ); echo "cluster_criado_apos=$CRIADO_APOS" | tee ~/rebaseline-infra/cluster-criado-apos.txt
sleep 1
D=$RB/runs/recriacao-pre-piloto-2026-11-05/vm-$(date -u +%Y%m%dT%H%M%SZ)
bash ~/rebaseline-infra/recriar-rb-20805b0.sh "$D" 2>&1 | tee "$D.console.log"
#   esperado: "down rc=0", "up rc=0" e "ESTAVEL size=3 HEALTHY: ..." ; "FALHA: nao fechou em 180s" = parar e avisar
cp ~/rebaseline-infra/cluster-criado-apos.txt ~/rebaseline-infra/recriar-rb-20805b0.sh "$D/"
```

Conferência da recriação (VM):

```bash
for n in isn1 isn2 isn3; do sudo podman inspect -f '{{.Name}} {{.State.Status}} Created={{.Created.Unix}} {{.Created}}' $n; done
date -u -d "$CRIADO_APOS" +%s                  # os tres Created.Unix tem de ser >= este numero
for n in isn1 isn2 isn3; do sudo podman inspect -f '{{.Name}} {{range .Mounts}}{{.Source}} {{end}}' $n; done   # o XML de $RB/cluster/
for p in 11222 11223 11224; do for c in sessions counters sessions-async counters-async; do
  echo "$c $p: $(curl -s -m 5 --digest -u admin:"$RB_PASSWORD" "http://127.0.0.1:$p/rest/v2/caches/$c?action=size")"; done; done
#   esperado: 12 linhas com 0 (os 4 caches existem e estao vazios)
```

### 1.5 Checklist 10.6 passo 4 (VM, só leitura, sem injeção)

```bash
sudo -n true && echo "sudo ok"; python3 --version; tmux -V; podman --version; flock --version
sudo podman inspect -f '{{.Created.Unix}} {{.State.StartedAt.Unix}} {{.State.StartedAt.UnixMilli}} {{.State.FinishedAt.UnixMilli}}' isn1
#   quatro numeros; o ultimo e negativo (-62135596800000) em isn1
sudo podman logs --since $(sudo podman inspect -f '{{.State.StartedAt.Unix}}' isn1) isn1 2>&1 | grep -c ISPN080001   # esperado 1
systemctl list-timers --all --no-legend 'tcc-heal-rb-*' | wc -c                                                      # esperado 0
```

### 1.6 Checklist 10.6 passo 5: *dry-run* (VM)

```bash
cd $RB
bash scripts/run-rebaseline.sh --dry-run --matriz "$MATRIZ" --matriz-sha256 "$MATRIZ_SHA" --fase piloto \
  | tee ~/rebaseline-infra/piloto-dry-run-$(date -u +%Y%m%dT%H%M%SZ).txt
#   esperado: "DRY-RUN fase=piloto ... (SHA-256 confere)", 12 execucoes i=1000..1011 na ordem da matriz,
#   "PLANO execucoes=12 primeira_F3=1005 ..." e "DRY-RUN: nada executado"
```

As 12 linhas, na ordem: 1000 S2-sync-F1, 1001 S2-async-none, 1002 S1-sync-F1, 1003 S1-async-none, 1004 S2-async-F1,
1005 S1-async-F3, 1006 S2-async-F3, 1007 S2-sync-F3, 1008 S1-async-F1, 1009 S1-sync-F3, 1010 S2-sync-none,
1011 S1-sync-none (sementes 150000 a 151100, passo 100).

### 1.7 Checklist 10.6 passo 6: pré-voo sem injeção (VM)

Mesmo comando do piloto, com `--saida` de ensaio. No *gate*, digitar `nao` (qualquer coisa diferente do pedido):

```bash
cd $RB
bash scripts/run-rebaseline.sh --matriz "$MATRIZ" --matriz-sha256 "$MATRIZ_SHA" --jar "$JAR" --jar-sha256 "$JAR_SHA" \
  --fase piloto --cluster-criado-apos "$CRIADO_APOS" --saida "$RB/runs/rebaseline-ensaio"; echo "RUNNER_RC=$? $(date -u +%FT%TZ)"
#   no prompt "Digite exatamente: go piloto <data>" digitar:  nao
#   esperado: plano impresso, "go recusado (noite)", "FIM exit=5 motivo=go nao dado (noite)", RUNNER_RC=5
ls -d $RB/runs/rebaseline-ensaio/exec-* 2>/dev/null | wc -l       # esperado 0
tail -n 3 $RB/runs/rebaseline-ensaio/piloto/runner.log
```

Um aviso "MANIFEST.sha256 nao gravado" nesse ensaio é esperado (não há `matriz.tsv` em `runs/rebaseline-ensaio/`).

### 1.8 Bloco de abertura do C1 §1.3 e covariáveis (VM e host)

VM, gravado fora do diretório de saída do *runner*:

```bash
bloco() {  # bloco <arquivo> [desde]: C1 §1.3; com <desde> acrescenta o bloco de fim
  local f="$1" desde="${2:-}"
  {
    echo "== carimbo_inicio_cliente=$(date -u +%FT%T.%3NZ)"
    echo "\$ date -Is"; date -Is
    echo "\$ boot_id"; cat /proc/sys/kernel/random/boot_id
    echo "\$ uptime -s"; uptime -s
    echo "\$ uname -r"; uname -r
    echo "\$ podman --version"; podman --version
    echo "\$ sudo podman version"; sudo podman version
    echo "\$ apt-mark showhold"; apt-mark showhold
    echo "\$ systemctl is-enabled apt-daily-upgrade.timer unattended-upgrades"; systemctl is-enabled apt-daily-upgrade.timer unattended-upgrades
    echo "\$ systemctl is-active apt-daily-upgrade.timer unattended-upgrades"; systemctl is-active apt-daily-upgrade.timer unattended-upgrades
    for n in isn1 isn2 isn3; do
      sudo podman inspect -f '{{.Name}} {{.State.Status}} {{.State.StartedAt}} {{.State.StartedAt.Unix}} {{.Created}}' $n; done
    echo "\$ df -h / /run"; df -h / /run
    echo "\$ free -m"; free -m
    systemctl list-units --all --no-legend 'tcc-amostrador-*'
    if [ -n "$desde" ]; then
      echo "\$ journalctl --list-boots --no-pager | tail -n 3"; journalctl --list-boots --no-pager | tail -n 3
      echo "\$ podman events desde $desde"
      sudo podman events --since "$desde" --until "$(date -Is)" --filter event=start --filter event=died --filter event=restart
    fi
    echo "== carimbo_fim_cliente=$(date -u +%FT%T.%3NZ)"
  } > "$f" 2>&1
  cat "$f"
}
export ABERTURA_ISO=$(date -Is)
bloco ~/rebaseline-infra/piloto-inicio-$(date -u +%Y%m%dT%H%M%SZ).txt
```

Conferir no bloco: o `boot_id` é `f9e98b74-…`, igual ao do fim do ensaio (h1); kernel `6.8.0-147-generic` e podman
4.9.3 (C1 §2); os 3 contêineres estão `running` com `StartedAt` posterior ao `CRIADO_APOS`; o `df` de `/` tem **pelo
menos 5 GB livres**. O piloto sem compressão grava de 1,6 a 2,9 GB (roteiro da VM §7.2), e o ensaio mediu ~110 a
165 MB por execução S1 com carga. Abaixo de 5 GB, parar e avisar o Senior.

**Host** (PowerShell do coordenador, sem elevação; anotar a saída num arquivo
`dados-vm/rebaseline-infra/piloto-host-inicio-<UTC>.txt`):

```powershell
Get-Date -Format o
Get-PSDrive C | Select-Object Used,Free
powercfg /qh SCHEME_CURRENT SUB_PCIEXPRESS ASPM        # C3 §7: ASPM desligado na tomada (indice AC = 0)
Get-Process typeperf | Select-Object Id,StartTime       # typeperf ampliado ativo (G-3)
wevtutil gl Microsoft-Windows-TaskScheduler/Operational | Select-String enabled   # covariavel G-3 (true/false)
```

A exclusão de `C:\Hyper-V` no Defender e o *checkpoint* da VM (C3 §7) exigem PowerShell elevado. Ficam declarados como
"mantidos" pelo Gestor, ou lidos por ele (`Get-MpPreference | Select -Expand ExclusionPath`, `Get-VMSnapshot tcc-f3`).

## 2. X-10: piloto

### 2.1 Janelas tmux

| janela | conteúdo |
|---|---|
| 0 | runner (sessão `rebaseline`, já aberta no passo 1.1, com `RB_PASSWORD` exportada) |
| 1 | vigia h4 |
| 2 | conferências da primeira F3 e leitura |

### 2.2 Amostrador h4: iniciar (início do H0 estrito; VM, janela 2)

```bash
tmux new-window -t rebaseline -n conf
cd ~/rb/rb-20805b0
export ID=piloto-$(date -u +%Y%m%dT%H%M%SZ); echo "$ID" | tee ~/rebaseline-infra/id-piloto.txt
bash scripts/amostrador-infra.sh iniciar --id "$ID"
#   esperado exit 0 e as linhas "unit: tcc-amostrador-<ID>", "unit: <espelho>", "arquivo: /run/tccdiag/<ID>/amostras.txt",
#   "espelho: ~/rebaseline-infra/<ID>/amostras.txt" e "primeira amostra: ..."
bash scripts/amostrador-infra.sh estado --id "$ID"
```

### 2.3 Vigia (VM, janela 1)

```bash
tmux new-window -t rebaseline -n vigia
cd ~/rb/rb-20805b0; ID=$(cat ~/rebaseline-infra/id-piloto.txt)
bash scripts/vigia-h4.sh --id "$ID" --saida ~/rb/rb-20805b0/runs/rebaseline --fase piloto
#   a cada 300 s: "[<UTC>] ciclo N: h4.py exit 0: h4: k de n execucoes; episodios/h=...; parada=nao"
#   aviso "PARADA H4" (exit 30 ou unit inativa) => C3 §4.3: Ctrl-C no runner DURANTE o portao K1 da execucao seguinte
```

### 2.4 Runner do piloto (VM, janela 0)

```bash
cd ~/rb/rb-20805b0
bash scripts/run-rebaseline.sh --matriz "$MATRIZ" --matriz-sha256 "$MATRIZ_SHA" \
  --jar "$JAR" --jar-sha256 "$JAR_SHA" --fase piloto --cluster-criado-apos "$CRIADO_APOS" \
  --saida "$SAIDA"; echo "RUNNER_RC=$? $(date -u +%FT%TZ)"
```

A senha vai pela variável `RB_PASSWORD` (exportada no passo 1.1). **Não** usar `--password` na linha de comando.

**GO da noite.** O *runner* imprime em `/dev/tty`:

```
Digite exatamente: go piloto AAAA-MM-DD
>
```

A data é `date +%F` da VM, no fuso da VM (UTC), no instante em que o *runner* chega ao *gate*. Hoje é `2026-11-05`.
Digitar exatamente a data impressa:

```
go piloto 2026-11-05
```

O *gate* espera até 3600 s. Texto diferente sai com exit 5 sem criar `exec-*`.

**GO da primeira F3** (i = 1005, a sexta execução, ~35 min depois do `go piloto`; o plano impresso traz
`hora_estimada_primeira_F3`). Antes de digitar, na janela 2:

```bash
systemctl list-timers --all --no-legend 'tcc-heal-rb-*'      # tem de estar vazio
```

O *runner* imprime:

```
Digite exatamente: go f3 1005
>
```

Digitar:

```
go f3 1005
```

Depois do `go f3` vem o portão K1 (60 s ou mais), e só então o workload. Na janela 2, durante a 1005:

```bash
grep -m1 'F3 disparada a +49' ~/rb/rb-20805b0/runs/rebaseline/piloto/runner.log
systemctl list-timers --all --no-legend 'tcc-heal-rb-1005*'                                   # timer presente
sudo nsenter -t $(sudo podman inspect -f '{{.State.Pid}}' isn2) -n tc qdisc show dev eth0     # mostra netem
#   por volta de +300 s: "injetor saiu com 0" no runner.log, netem some e o timer some
```

Padrão para o `goloop` do coordenador capturar o texto pedido no painel (as duas formas):

```
Digite exatamente: (go piloto [0-9]{4}-[0-9]{2}-[0-9]{2}|go f3 [0-9]+)$
```

Envie só os dois GOs esperados, `go piloto <data impressa>` e `go f3 1005`, cada um uma vez. Qualquer outro pedido de
*gate* fica para o Gestor.

### 2.5 Linha de fim (para o vigia do coordenador)

A última linha do `runner.log` da fase (`~/rb/rb-20805b0/runs/rebaseline/piloto/runner.log`), também impressa no
terminal, é:

```
[AAAA-MM-DDTHH:MM:SS.mmmZ] run-rebaseline.sh: FIM exit=<rc> motivo=<texto>
```

Regex (ERE): `^\[[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z\] run-rebaseline\.sh: FIM exit=[0-9]+ motivo=.*$`

- Fim normal: `FIM exit=0 motivo=fase piloto concluida`.
- Outros: `exit=5` (go não dado), `exit=6` (infraestrutura), `exit=10` (parada automática do PR §6), `exit=130` ou
  `143` (Ctrl-C ou TERM).
- O `MANIFEST.sha256` é gravado **depois** da linha FIM. Esperar `piloto/MANIFEST.sha256` existir (alguns segundos)
  antes de qualquer leitura.
- Erros do pré-voo anteriores à criação de `piloto/` (hash, dependência, cluster, flock, uso) **não** geram linha FIM.
  Saem como `Erro: ...` em *stderr*. Por isso o comando do passo 2.4 termina com `echo "RUNNER_RC=..."`. O vigia deve
  aceitar as duas marcas: a linha FIM no `runner.log` ou `^RUNNER_RC=[0-9]+ ` no painel da janela 0.

Duração esperada: ~12 × 7 min ≈ 1,4 h depois do `go piloto`.

## 3. Fim da fase (VM)

### 3.1 Saída do runner

```bash
cd ~/rb/rb-20805b0/runs/rebaseline
tail -n 1 piloto/runner.log; cat piloto/resumo.md
sha256sum -c piloto/MANIFEST.sha256 | grep -v ': OK$' ; echo "manifest rc=${PIPESTATUS[0]}"
systemctl list-timers --all --no-legend 'tcc-heal-rb-*'      # vazio; se nao, checklist 10.6 passo 13 (netem antes do timer)
```

### 3.2 Bloco de fim do C1 §1.3 (antes de parar o amostrador)

```bash
bloco ~/rebaseline-infra/piloto-fim-$(date -u +%Y%m%dT%H%M%SZ).txt "$ABERTURA_ISO"
```

Se a janela 0 foi fechada, redefinir a função `bloco` do passo 1.8 e usar o `date -Is` da abertura, que está na
segunda linha do arquivo `piloto-inicio-*.txt`.

### 3.3 Vigia e amostrador: parar

```bash
# janela 1: Ctrl-C no vigia (ele nao sai sozinho com --ciclos 0)
cd ~/rb/rb-20805b0; ID=$(cat ~/rebaseline-infra/id-piloto.txt)
bash scripts/amostrador-infra.sh parar --id "$ID"
#   esperado exit 0 e "copia final: /home/server1/rebaseline-infra/<ID>/amostras-final.txt sha256=<hex>"
cat ~/rebaseline-infra/$ID/amostras-final.txt.sha256
tail -n 2 ~/rebaseline-infra/$ID/amostras-final.txt       # linha "# fim t_real=... amostras=... atrasos_ge_1_5s=..."
```

Aqui termina o H0 estrito.

### 3.4 Leitura h4 final

```bash
cd ~/rb/rb-20805b0
python3 -I analysis/h4.py fase --amostras ~/rebaseline-infra/$ID/amostras-final.txt \
  --saida ~/rb/rb-20805b0/runs/rebaseline --fase piloto; echo "h4 rc=$?"
#   exit 0 (segue), 30 (parada h4), 4 (arquivo ilegivel: vale como h4c, R3 da W-h4v), 2 (uso)
ls -l runs/rebaseline/h4/h4-piloto.tsv runs/rebaseline/h4/h4-piloto.json
```

Se a cópia final faltar, usar o espelho `~/rebaseline-infra/$ID/amostras.txt` e registrar isso no parecer. A contagem de
episódios da DT-019 (fusão a < 60 s, exclusão R1, base só do piloto) é do Senior no X-11, a partir do JSON e do TSV.

### 3.5 O que levar ao host para o X-11 (depois de sair do H0)

`~/rb/rb-20805b0/runs/rebaseline/` (matriz, `piloto/`, `exec-1000-*` a `exec-1011-*`, `h4/`), `~/rebaseline-infra/`
(listas, `cluster-criado-apos.txt`, `recriar-rb-20805b0.sh`, dry-run, blocos de abertura e de fim, `id-piloto.txt`,
`$ID/` com `amostras-final.txt` e `.sha256`, `PARADA-H4`/`ALERTA-H4C` se existirem),
`runs/recriacao-pre-piloto-2026-11-05/`, `runs/rebaseline-ensaio/` e, do host, o `typeperf` ampliado e o arquivo de
covariáveis do passo 1.8. Copiar com `sha256sum` conferido dos dois lados.
