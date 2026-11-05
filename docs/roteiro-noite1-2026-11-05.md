# Roteiro de comandos do X-12: noite 1 do *rebaseline*, rodadas 1 a 5 (Senior, 2026-11-05)

> Para o coordenador. **Só vale depois da decisão do Gestor sobre o parecer do piloto**
> (`docs/validacao-piloto-2026-11-05.md`, G-1 a G-5), registrada em `decisoes-tecnicas.md`. O parecer saiu com
> ressalvas, então a A-7 não se aplica e o coordenador não libera sozinho. Os comandos rodam **na VM**
> (`server1@172.27.114.62`, boot `f9e98b74-…`), salvo os marcados como **host**. Mesmo formato do
> `docs/roteiro-piloto-2026-11-05.md`, que continua valendo onde este roteiro não diz nada diferente.
>
> Fontes: PR §3, §5, §6 e §8; complemento 1 §1.3 e §2; complemento 3 §4.3, §5 e §7; DT-013, DT-014, DT-017, DT-018 e
> DT-019; código congelado lido nesta versão (`run-rebaseline.sh` `d95305fe…`, `amostrador-infra.sh` `e4cc5c79…`,
> `vigia-h4.sh` `1e7462f4…`, `h4.py` `53d7e7cb…`).
>
> **A fase se chama `noite1`**, não "campanha". O *runner*, o vigia e o `h4.py` só aceitam `piloto`, `noite1`, `noite2` e
> `reposicoes` (`run-rebaseline.sh:21`, `vigia-h4.sh:55`, `h4.py:70`). O GO pedido é `go noite1 <data>`.
>
> O *cluster* **não** é recriado. A noite 1 roda no *cluster* do piloto, com o mesmo `--cluster-criado-apos`. Nada aqui
> muda arquivo congelado.

## 0. Pré-condições (conferir antes de começar; se faltar uma, parar e avisar)

| # | Item | Quem |
|---|---|---|
| P-1 | Decisão do Gestor sobre G-1 a G-5 do parecer do piloto registrada em `decisoes-tecnicas.md` | Gestor / Senior |
| P-2 | Host sem jogo nem aplicativo do usuário aberto; Workers parados (sem `mvn`, testes, Podman local, TLC); `HOST-PERFIL.txt` = H0 há ≥ 5 min antes do passo 2.2 | coordenador / Gestor |
| P-3 | Se a G-2(c) for aprovada: `Stop-Service WSearch` feito pelo Gestor em PowerShell elevado | Gestor |
| P-4 | `typeperf` por processo **reiniciado** pouco antes do passo 2.2, com saída nova (o `Process(*)` só enumera os processos que existem quando ele começa) | coordenador (H2) |
| P-5 | Brutos do piloto no host conferidos (pacote `e3955c27…`, manifesto 162/162): feito | coordenador |
| P-6 | Nenhum `run-rebaseline.sh`, `ensaio-f1.sh`, `goloop*`, vigia ou amostrador ativo na VM (passo 1.2) | coordenador |

Regime do host: **H2 até o `iniciar` do amostrador (passo 2.2), H0 estrito daí até o `parar` (passo 3.3)**, inclusive
cópia, `scp`, `git`, compressão, extração e qualquer aplicativo do usuário (C3 §7; R-1 do parecer). Só o `typeperf` e o
vigia do coordenador rodam no host.

## 1. Preparação (H2)

### 1.0 Host: `typeperf` novo e covariáveis de abertura

PowerShell do coordenador, sem elevação. O diretório `diag-io` é o do piloto, e `contadores-proc.txt` é a mesma lista.

```powershell
$D = "$env:LOCALAPPDATA\Temp\claude\98c9abffb040f610\0786b06c-748d-4829-8e8b-152396a994ac\scratchpad\diag-io"
Get-Process typeperf -ErrorAction SilentlyContinue | Stop-Process          # para o do piloto
Start-Process typeperf -WindowStyle Hidden -ArgumentList @('-cf', "$D\contadores-proc.txt", '-si', '2', '-f', 'CSV', '-y', '-o', "$D\host-proc-io-noite1.csv")
$H = "C:\Users\Francesco\Development\tcc\dados-vm\rebaseline-2026-11\noite1-host-inicio-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')).txt"
& {
  Get-Date -Format o
  Get-PSDrive C | Select-Object Used,Free | Format-List
  powercfg /qh SCHEME_CURRENT SUB_PCIEXPRESS ASPM                         # indice AC = 0
  Get-Process typeperf | Select-Object Id,StartTime | Format-List
  wevtutil gl Microsoft-Windows-TaskScheduler/Operational | Select-String enabled
  Get-Service WSearch | Select-Object Status,StartType | Format-List
  'jogo/aplicativos (esperado vazio):'
  Get-Process -Name VALORANT*,RiotClient*,Discord*,steam* -ErrorAction SilentlyContinue | Select-Object Name,Id,StartTime
  Get-Process | Sort-Object CPU -Descending | Select-Object -First 15 Name,Id,CPU | Format-Table -AutoSize
} *>&1 | Tee-Object -FilePath $H
```

O Gestor declara, ou lê em PowerShell elevado, a exclusão de `C:\Hyper-V` no Defender e o *checkpoint* da VM
(`Get-MpPreference | Select -Expand ExclusionPath`; `Get-VMSnapshot tcc-f3`) e o tamanho do AVHDX
(`Get-ChildItem C:\Hyper-V\tcc-f3 -Recurse -Include *.avhdx | Select Name,Length`). A resposta vai no mesmo arquivo.

### 1.1 Sessão e variáveis (VM)

A sessão `tmux` do piloto, se ainda existir, é fechada antes, depois de conferir que nada roda nela (passo 1.2).

```bash
tmux kill-session -t rebaseline 2>/dev/null; tmux new -s rebaseline   # janela 0: runner
cd ~/rb/rb-20805b0
export RB=~/rb/rb-20805b0
export SAIDA=$RB/runs/rebaseline
export MATRIZ=$RB/runs/rebaseline/matriz.tsv
export MATRIZ_SHA=8654900cd23b9b46e65163a5d106bd6f86113632d2ce621ff962f637cd645c35
export JAR=$RB/workload/target/session-workload-0.1.0-SNAPSHOT.jar
export JAR_SHA=5eced1363f1b318f533174e967be6a81080bcc95961d0f72a118dc0184dcefaf
export CRIADO_APOS=$(sed -n 's/^cluster_criado_apos=//p' ~/rebaseline-infra/cluster-criado-apos.txt); echo "$CRIADO_APOS"
#   esperado 2026-11-05T04:01:07Z (o cluster NAO e recriado)
read -rs RB_PASSWORD && export RB_PASSWORD
```

### 1.2 Estado entre fases (VM, só leitura)

```bash
cat /proc/sys/kernel/random/boot_id            # esperado f9e98b74-bf1f-4192-9770-b40285877292 (h1 entre fases)
pgrep -af 'ensaio-f1|run-rebaseline|goloop|vigia-h4|session-workload|vmstat 1' || echo "nenhum processo residual"
flock -n /var/lock/tcc-vm.lock true && echo "lock livre" || echo "LOCK OCUPADO: parar"
systemctl list-units --all --no-legend 'tcc-amostrador-*' | wc -l     # esperado 0 (o do piloto foi parado)
systemctl list-timers --all --no-legend 'tcc-heal*' | wc -c           # esperado 0
for n in isn1 isn2 isn3; do sudo podman inspect -f '{{.Name}} {{.State.Status}} {{.State.StartedAt.Unix}} {{.State.StartedAt}}' $n; done
#   esperado: running; isn1 1793851284 (04:01:24.765810852), isn2 1793854627 (04:57:07.454886045),
#   isn3 1793851287 (04:01:27.745419488), iguais ao bloco de fim do piloto. Diferente: NAO comecar, chamar o Senior
for n in isn1 isn2 isn3; do echo "$n vidas=$(sudo podman logs $n 2>&1 | grep -c ISPN080001) ispn006010=$(sudo podman logs $n 2>&1 | grep -c ISPN006010)"; done
#   esperado isn1 1/1, isn2 5/5, isn3 1/1 (DT-013; so leitura)
```

### 1.3 *Hashes* (VM)

As listas já existem em `~/rebaseline-infra/` desde o piloto.

```bash
cd $RB
sha256sum ~/rebaseline-infra/lista-003814e9.sha256 ~/rebaseline-infra/lista-e9c44819.sha256
#   esperado 003814e93929ea7360f7eba35933907f954201ddf6faca2ce0ae9d3c8c2133a4 e e9c4481989f147fc683d5923aef76213826ac2a69e15f5990336e560a57664a6
sha256sum -c ~/rebaseline-infra/lista-003814e9.sha256 && sha256sum -c ~/rebaseline-infra/lista-e9c44819.sha256
sha256sum cluster/infinispan-cluster.xml ~/rebaseline-infra/bloco.sh
#   esperado 6d52413b1c3de47b63179430bfadb02dc29d74ba90b919c584c66ff25437778c e f205e0440d959307f10fa3b0b737a878041be66a050b9d634126b84c86439393
sha256sum $SAIDA/matriz.tsv                    # 8654900c...
```

### 1.4 Disco (VM)

Volume da noite medido no piloto: 5 × 1,563 GB = 7,82 GB. Regra do roteiro da VM §7.5: livre ≥ 1,5 × 7,82 GB.

```bash
df -B1 --output=avail / | tail -1              # GO so com >= 11730000000
df -h / /run
```

**Só se a G-3 tiver sido aprovada** (O-3 do piloto na VM), antes da leitura acima. A cópia de referência está no host:

```bash
cd $SAIDA
O3=~/rebaseline-infra/o3-piloto-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p "$O3"
grep -E '^[0-9a-f]{64}  exec-10(0[0-9]|1[01])-[A-Za-z0-9-]+/rep-001\.csv$' piloto/MANIFEST.sha256 > "$O3/csv-piloto.sha256"
wc -l < "$O3/csv-piloto.sha256"                # esperado 12
df -B1 --output=avail / | tee "$O3/df-antes.txt"
sha256sum -c "$O3/csv-piloto.sha256" 2>&1 | tee "$O3/conferencia-antes.txt"      # 12 OK; senao parar
while read -r h f; do
  gzip -1 -k -- "$f"
  g=$(gzip -dc -- "$f.gz" | sha256sum | cut -d' ' -f1)
  if [ "$g" = "$h" ]; then rm -- "$f"; echo "OK $f"; else rm -f -- "$f.gz"; echo "DIVERGE $f: original mantido"; fi
done < "$O3/csv-piloto.sha256" 2>&1 | tee "$O3/compressao.txt"
sha256sum exec-10*/rep-001.csv.gz > "$O3/gz.sha256"; wc -l < "$O3/gz.sha256"     # 12
sudo fstrim -v / 2>&1 | tee "$O3/fstrim.txt"
df -B1 --output=avail / | tee "$O3/df-depois.txt"                                 # ~1,2 GB a mais
```

O `rebaseline.py estado` lê só o `checagem.json` das execuções do piloto (inclusive para a R-S3). O `h4.py` lê só o
`execucao.json`. Nenhum dos dois abre o CSV do piloto. Se o `df` continuar abaixo de 11 730 000 000, **não dar o GO** e
chamar o Gestor.

### 1.5 *Dry-run* (VM)

Não rodar o pré-voo com "nao" apontando para `$SAIDA`. Ele criaria `$SAIDA/noite1/` antes da noite. O *dry-run* basta.

```bash
cd $RB
bash scripts/run-rebaseline.sh --dry-run --matriz "$MATRIZ" --matriz-sha256 "$MATRIZ_SHA" --fase noite1 \
  | tee ~/rebaseline-infra/noite1-dry-run-$(date -u +%Y%m%dT%H%M%SZ).txt
#   esperado: "DRY-RUN fase=noite1 ... (SHA-256 confere)", 60 execucoes i=0..59 na ordem da matriz,
#   "PLANO execucoes=60 primeira_F3=4 ..." e "DRY-RUN: nada executado"
ls -d $SAIDA/noite1 2>/dev/null || echo "noite1/ ainda nao existe (certo)"
```

Primeiras execuções, na ordem: 0 S2-sync-F1, 1 S1-sync-F1, 2 S1-sync-none, 3 S2-sync-none, **4 S1-async-F3** (primeira F3),
5 S1-async-none, … (sementes 50 000 a 55 900, passo 100). F1 da noite: i = 0, 1, 9, 11, 13, 18, 20, 22, 24, 29, 30, 31,
38, 40, 45, 46, 50, 55, 57 e 59 (20). F3: i = 4, 7, 8, 10, 12, 19, 21, 23, 25, 27, 34, 35, 36, 41, 42, 47, 51, 53, 54 e 56 (20).

### 1.6 Bloco de abertura do C1 §1.3 (VM)

```bash
source ~/rebaseline-infra/bloco.sh
export ABERTURA_ISO=$(date -Is); echo "$ABERTURA_ISO" > ~/rebaseline-infra/abertura-noite1-iso.txt
bloco ~/rebaseline-infra/noite1-inicio-$(date -u +%Y%m%dT%H%M%SZ).txt
command -v ip nsenter tc                        # tres caminhos (adenda da DT-009 parte 2)
```

Conferir no bloco: `boot_id` `f9e98b74-…`; kernel `6.8.0-147-generic`; podman 4.9.3; os três contêineres `running` com os
`StartedAt` do passo 1.2; `df` coerente com o passo 1.4; nenhuma unit `tcc-amostrador-*`.

## 2. X-12: noite 1

### 2.1 Janelas tmux

| janela | conteúdo |
|---|---|
| 0 | runner (sessão `rebaseline`, com `RB_PASSWORD` exportada) |
| 1 | vigia h4 |
| 2 | conferências e leitura |

### 2.2 Amostrador h4: iniciar (início do H0 estrito; VM, janela 2)

```bash
tmux new-window -t rebaseline -n conf
cd ~/rb/rb-20805b0
export ID=noite1-$(date -u +%Y%m%dT%H%M%SZ); echo "$ID" | tee ~/rebaseline-infra/id-noite1.txt
bash scripts/amostrador-infra.sh iniciar --id "$ID"
#   esperado exit 0, as duas units, o arquivo em /run/tccdiag/<ID>/amostras.txt, o espelho e "primeira amostra: ..."
bash scripts/amostrador-infra.sh estado --id "$ID"
```

### 2.3 Vigia (VM, janela 1)

```bash
tmux new-window -t rebaseline -n vigia
cd ~/rb/rb-20805b0; ID=$(cat ~/rebaseline-infra/id-noite1.txt)
bash scripts/vigia-h4.sh --id "$ID" --saida ~/rb/rb-20805b0/runs/rebaseline --fase noite1
#   a cada 300 s: "[<UTC>] ciclo N: h4.py exit 0: h4: k de n execucoes; episodios/h=...; parada=nao"
```

### 2.4 Laço de GO do coordenador (VM, fora das janelas 0 a 2)

Cópia do `goloop-rb.sh` do piloto, mudando só o padrão (`go noite1` e **apenas** `go f3 4`) e o número de voltas. Ele
também detecta o fim, e a noite dura ~6,4 h.

```bash
cat > ~/rebaseline-infra/goloop-n1.sh <<'EOF'
#!/bin/bash
# Envia ao tmux "rebaseline" (janela 0) o GO exato pedido pelo run-rebaseline.sh da noite 1, uma vez por texto.
# Coordenador, sob a autorizacao A-1. So 'go noite1 <data>' e 'go f3 4'. Uso: goloop-n1.sh <log>
L=${1:-/run/tccdiag/goloop-n1.log}
feito=""
for t in $(seq 1 10000); do
  p=$(tmux capture-pane -p -S -40 -t rebaseline:0 2>/dev/null) || { echo "$(date -u +%T) sessao sumiu" >> "$L"; break; }
  g=$(printf '%s\n' "$p" | grep -oE '^Digite exatamente: (go noite1 [0-9]{4}-[0-9]{2}-[0-9]{2}|go f3 4)$' | tail -1 | sed 's/^Digite exatamente: //')
  if [ -n "$g" ] && ! printf '%s' "$feito" | grep -qF "|$g|"; then
    tmux send-keys -t rebaseline:0 "$g" Enter
    feito="$feito|$g|"
    echo "$(date -u +%T) enviado: $g" >> "$L"
  fi
  if printf '%s\n' "$p" | grep -qE 'run-rebaseline\.sh: FIM exit=|^RUNNER_RC='; then
    echo "$(date -u +%T) FIM detectado: $(printf '%s\n' "$p" | grep -E 'FIM exit=|^RUNNER_RC=' | tail -2 | tr '\n' ' ')" >> "$L"
    break
  fi
  sleep 3
done
EOF
diff ~/rebaseline-infra/goloop-rb.sh ~/rebaseline-infra/goloop-n1.sh   # so cabecalho, L, seq e o padrao
sha256sum ~/rebaseline-infra/goloop-n1.sh
```

O laço é iniciado (em segundo plano, com `nohup`) logo antes do passo 2.5. Qualquer outro pedido de *gate* fica para o
Gestor.

### 2.5 Runner da noite 1 (VM, janela 0)

```bash
cd ~/rb/rb-20805b0
bash scripts/run-rebaseline.sh --matriz "$MATRIZ" --matriz-sha256 "$MATRIZ_SHA" \
  --jar "$JAR" --jar-sha256 "$JAR_SHA" --fase noite1 --cluster-criado-apos "$CRIADO_APOS" \
  --saida "$SAIDA"; echo "RUNNER_RC=$? $(date -u +%FT%TZ)"
```

A senha vai por `RB_PASSWORD`. **Não** usar `--password`.

**GO da noite.** O *runner* imprime em `/dev/tty`:

```
Digite exatamente: go noite1 AAAA-MM-DD
>
```

A data é `date +%F` da VM (UTC) no instante do *gate*. Se o *gate* chegar em 2026-11-05 UTC:

```
go noite1 2026-11-05
```

Se chegar depois da meia-noite UTC, a data impressa é 2026-11-06, e é ela que vale. O laço copia o texto impresso.

**GO da primeira F3** (i = 4, a quinta execução, ~25 min depois do GO pelo tempo medido no piloto; o plano impresso estima
~28 min). Antes, na janela 2:

```bash
systemctl list-timers --all --no-legend 'tcc-heal-rb-*'      # tem de estar vazio
```

O *runner* pede `go f3 4`, e o laço envia:

```
go f3 4
```

Durante a i = 4, na janela 2 (só leitura):

```bash
grep -m1 'exec i=4: F3 disparada a +49' ~/rb/rb-20805b0/runs/rebaseline/noite1/runner.log
systemctl list-timers --all --no-legend 'tcc-heal-rb-4*'                                      # timer presente
sudo nsenter -t $(sudo podman inspect -f '{{.State.Pid}}' isn2) -n tc qdisc show dev eth0     # mostra netem
#   por volta de +300 s: "injetor saiu com 0" no runner.log, netem some, timer some
```

**Acompanhamento** (só leitura, no máximo uma vez por hora, sem cópia para o host):

```bash
tail -n 4 ~/rb/rb-20805b0/runs/rebaseline/noite1/runner.log
ls ~/rebaseline-infra/$(cat ~/rebaseline-infra/id-noite1.txt)/ | grep -E 'PARADA-H4|ALERTA-H4C' || echo "sem aviso h4"
df -h / | tail -1
```

### 2.6 Parada h4 (C3 §4.3)

Se o vigia gravar `PARADA-H4` (exit 30 do `h4.py` ou unit do amostrador inativa), o coordenador interrompe o *runner*
**durante o portão K1 da execução seguinte**, antes do lançamento do *workload*:

```bash
# janela 2: esperar a linha do portao da proxima execucao
tail -n 0 -f ~/rb/rb-20805b0/runs/rebaseline/noite1/runner.log | grep -m1 ': portao K1'
# assim que ela aparecer (o portao leva >= 60 s): Ctrl-C na janela 0
```

O *runner* sai com 130. A execução sem `stdout.log` volta com o mesmo i e a mesma semente (DT-014 item 1). A retomada
pede decisão do Gestor registrada. Depois de interromper, seguir a seção 3 (bloco de fim, `parar`, `h4.py fase`).

### 2.7 Linha de fim

Última linha de `~/rb/rb-20805b0/runs/rebaseline/noite1/runner.log`, também impressa no terminal:

```
[AAAA-MM-DDTHH:MM:SS.mmmZ] run-rebaseline.sh: FIM exit=<rc> motivo=<texto>
```

Regex (ERE): `^\[[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z\] run-rebaseline\.sh: FIM exit=[0-9]+ motivo=.*$`

- Fim normal: `FIM exit=0 motivo=fase noite1 concluida`.
- Outros: `exit=5` (GO não dado), `exit=6` (infraestrutura), `exit=10` (parada automática do PR §6: duas recusas na
  mesma célula, mais de 6 recusas, K8, K9, C3), `exit=130` ou `143` (Ctrl-C ou TERM).
- O `MANIFEST.sha256` é gravado **depois** da linha FIM. Esperar `noite1/MANIFEST.sha256` existir antes de ler.
- Erros do pré-voo anteriores a `noite1/` (*hash*, dependência, *cluster*, *flock*, uso) não geram linha FIM. Saem em
  *stderr* e no `RUNNER_RC=` do painel. O vigia do coordenador aceita as duas marcas.

Duração esperada: **~6 h 23 min** depois do `go noite1` (60 × 383 s medidos no piloto), mais a espera do `go f3 4`. O
`fim_estimado` do plano impresso usa 420 s por execução (~7 h) e fica ~35 min acima do real.

## 3. Fim da fase (VM; H0 estrito até o passo 3.3)

### 3.1 Saída do runner

```bash
cd ~/rb/rb-20805b0/runs/rebaseline
tail -n 1 noite1/runner.log; cat noite1/resumo.md
grep -c ' noite1/MANIFEST.sha256.tmp$' noite1/MANIFEST.sha256           # esperado 1 (defeito conhecido, parecer §8)
grep -v ' noite1/MANIFEST.sha256.tmp$' noite1/MANIFEST.sha256 | sha256sum -c --quiet; echo "manifest rc=$?"   # esperado 0
systemctl list-timers --all --no-legend 'tcc-heal-rb-*'      # vazio; senao, checklist 10.6 passo 13 (netem antes do timer)
grep -E '"(execucoes_campanha|aceitas_campanha|total_recusas|parada)"' estado.json   # gravado pelo runner a cada execucao
```

Não rodar `rebaseline.py estado` à mão: ele regrava o `estado.json` da saída.

### 3.2 Bloco de fim do C1 §1.3 (antes de parar o amostrador)

```bash
source ~/rebaseline-infra/bloco.sh
bloco ~/rebaseline-infra/noite1-fim-$(date -u +%Y%m%dT%H%M%SZ).txt "$(cat ~/rebaseline-infra/abertura-noite1-iso.txt)"
# codigo de saida de cada 'died' de isn2 (DT-017: esperado 137 em todos; 20 died e 20 start se a noite foi inteira)
sudo podman events --since "$(cat ~/rebaseline-infra/abertura-noite1-iso.txt)" --until "$(date -Is)" \
  --filter container=isn2 --filter event=died --filter event=start --format json \
  > ~/rebaseline-infra/noite1-eventos-isn2.json
grep -o '"Status":"[a-z]*"\|"containerExitCode":[0-9]*\|"ContainerExitCode":[0-9]*' ~/rebaseline-infra/noite1-eventos-isn2.json | sort | uniq -c
```

### 3.3 Vigia e amostrador: parar

```bash
# janela 1: Ctrl-C no vigia
cd ~/rb/rb-20805b0; ID=$(cat ~/rebaseline-infra/id-noite1.txt)
bash scripts/amostrador-infra.sh parar --id "$ID"
#   esperado exit 0 e "copia final: /home/server1/rebaseline-infra/<ID>/amostras-final.txt sha256=<hex>"
cat ~/rebaseline-infra/$ID/amostras-final.txt.sha256
tail -n 2 ~/rebaseline-infra/$ID/amostras-final.txt       # "# fim t_real=... amostras=... atrasos_ge_1_5s=..."
```

Aqui termina o H0 estrito.

### 3.4 Leitura h4 final

```bash
cd ~/rb/rb-20805b0
python3 -I analysis/h4.py fase --amostras ~/rebaseline-infra/$ID/amostras-final.txt \
  --saida ~/rb/rb-20805b0/runs/rebaseline --fase noite1; echo "h4 rc=$?"
#   exit 0 (segue), 30 (parada h4), 4 (arquivo ilegivel: vale como h4c), 2 (uso)
sha256sum runs/rebaseline/h4/h4-noite1.tsv runs/rebaseline/h4/h4-noite1.json | tee ~/rebaseline-infra/h4-noite1.sha256
```

**Não** rodar `h4.py repor` agora. As reposições h4 entram no fim da noite 2 (C3 §5).

### 3.5 Host: covariáveis de fim (PowerShell, sem elevação)

```powershell
$H = "C:\Users\Francesco\Development\tcc\dados-vm\rebaseline-2026-11\noite1-host-fim-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')).txt"
& {
  Get-Date -Format o
  Get-PSDrive C | Select-Object Used,Free | Format-List
  powercfg /qh SCHEME_CURRENT SUB_PCIEXPRESS ASPM
  Get-Process typeperf | Select-Object Id,StartTime | Format-List
  wevtutil gl Microsoft-Windows-TaskScheduler/Operational | Select-String enabled
  Get-Service WSearch | Select-Object Status,StartType | Format-List
} *>&1 | Tee-Object -FilePath $H
```

Se o Gestor parou o `WSearch` (G-2(c)), ele decide aqui se o religa até a noite 2. A decisão vira covariável.

### 3.6 O que levar ao host para o X-13 (depois de sair do H0)

`~/rb/rb-20805b0/runs/rebaseline/` (`matriz.tsv`, `noite1/`, os `exec-0000-*` a `exec-0059-*`, `estado.json`,
`h4/h4-noite1.*`), `~/rebaseline-infra/` (blocos de abertura e de fim, dry-run, `id-noite1.txt`, `$ID/` com
`amostras-final.txt` e `.sha256`, `noite1-eventos-isn2.json`, `h4-noite1.sha256`, `goloop-n1.sh`
e o *log* dele, `o3-piloto-*/` se houver, `PARADA-H4`/`ALERTA-H4C` se existirem) e, do host, o `host-proc-io-noite1.csv`, o
log do TaskScheduler da janela da noite e os dois arquivos de covariáveis. Volume esperado sem compressão: ~7,8 GB. A
cópia segue a O-3 do X-13 (`gzip -1` na VM, `scp`, `sha256sum -c` do original e do `.gz`, só então apagar na VM, `fstrim`),
com roteiro próprio. Depois da noite 1, a VM fica com ~3,5 GiB livres, então a O-3 é obrigatória antes da noite 2.

## 4. O que o parecer da noite 1 (X-13) vai conferir

- h1: `boot_id` igual na abertura, no fim e no fim do piloto. h2: `StartedAt` de `isn1`/`isn3` sem mudança e sem evento.
  h3: os 20 pares `died`/`start` de `isn2` dentro das 20 execuções F1, `containerExitCode` 137 nos `died`, e o
  `StartedAt` final igual ao do último F1 executado (i = 59, se a noite foi inteira).
- K1..K10 por execução; paradas (a) a (g) pelo `estado`; C3 agregado da campanha.
- h4: tabela `h4-noite1.tsv`, regra de parada (5 em 12, 4 seguidas, h4c), execuções ACEITAS que são h4 (reposição no fim
  da noite 2).
- Covariáveis do host contra as da abertura (C3 §7), e o `typeperf` contra o amostrador em qualquer episódio.
