# Roteiro de VM da recalibração do benchmark refeito (X-19): anexo ao roteiro X-17/X-18 (Senior, 2026-11-06)

> Para o coordenador. **Só executar depois do aviso dele**, com a tag `int/2026-11-06-bench` fechada e a DT-026
> registrada (já está: `wt-bench`, `docs/pre-registro-benchmark-refeito`, documento `37a1099`, DT `d3d2988`). Este
> anexo só descreve o que muda em relação ao `docs/roteiro-x17-calibracao-2026-11-05.md` (X-18), que continua valendo
> onde este não diz nada diferente: forma dos blocos, função `vm`, *lock*, *dead-man*, CR, `tmux`, regras do supervisor,
> perfil H0, cópias com `sha256sum -c` e a tabela de falhas da seção 8 de lá.
>
> Conferido no *host*, sem VM, com a árvore de `ade376e` (ponta de `feature/controlador-integracao` às 07:05 de 06/11,
> com W-6c, W-10, P-48, W-9 e W-5c/W-5d, a base mais próxima da tag):
>
> - a matriz da recalibração sai igual em duas gerações (`3f20219d…`);
> - o arquivo de parâmetros do 2.2, sem o `s3_replay_late_max`, passa no `campanha_s3_conferir.py parametros`
>   (24 números, os cinco `s3_*` com tipo e faixa conferidos);
> - o `--dry-run` da fase `calibracao` com `--sonda-replay --swap-limite-kb 1048576` sai com exit 0, e a linha do
>   programa traz `--s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9
>   --s3-admin-mean-sec 5 --s3-replay-probe --sweep`;
> - **a ponta `ade376e` não passa o `--s3-replay-late-max`**: o `campanha_s3_conferir.py` recusa o campo
>   `s3_replay_late_max` ("parametros desconhecidos"), e o `--sonda-replay` só acrescenta `--s3-replay-probe --sweep`.
>   A DT-026 fixa 268 (P-3). Isso depende da tag (seção 9, D-1);
> - os quatro auxiliares passaram em `bash -n` ou `py_compile`, e o `rc-param.py` recusou regravar a saída.

## 0. O que muda em relação ao X-18

| Item | X-18 | X-19 (recalibração) | Origem |
|---|---|---|---|
| Bundle | `cs3-1f65a02` (T = `1f65a02`) | ponta da tag `int/2026-11-06-bench` | coordenador |
| Carga S3 | padrão do programa | **L+** nos campos `s3_*` do arquivo de parâmetros, em todas as células: `s3_users` 40, `s3_think_ms` 500, `s3_session_median_sec` 10, `s3_logout_prob` 0,9, `s3_admin_mean_sec` 5 | DT-026, P-1: ponto de operação p2-d pela regra 6.5 do piloto (`runs/p2-d-java.log`) |
| Instrumentos | nenhum | `--sonda-replay` no orquestrador (`--s3-replay-probe --sweep` na linha do programa) e `--s3-replay-late-max 268` | DT-026 §2.1 e P-3 |
| Sonda, `min`/`max` | — | 1 s e 3 s; são os padrões do W-10 em `ade376e` (`S3Parametros.java`), então valem mesmo se a tag não os passar | DT-026 §2.1 |
| Células | 26 (ASYNC sem falha × 5, F1 × 5, F2 × 5, F4 × 5; SYNC F2 × 3; SYNC sem falha × 3) | as mesmas 26; pausa fora (P-2) | DT-026 §4 |
| Matriz | `--ordem-seed 20261105 --s0 300000` | **`--ordem-seed 20261106 --s0 700000`**, passo 100, `--i0 0` (sementes 700 000 a 702 500) | DT-026 §4 (data do commit que aprova o pré-registro) |
| Controlador e referências | `controlador-calibracao-s3.json` (`c4e05c3c…`), referências inertes | os mesmos | pré-registro da calibração §4.2; DT-026 §4 (nenhuma regra muda) |
| `exec_teto_s`, `volume_exec_mb` | 1010 s, 14 MB | **os mesmos** (seção 3) | C-6, C-7; medidas da p2-d |
| Swap | supervisor no modo `recriar` (sem W-6c) | `--swap-limite-kb 1048576` no orquestrador, supervisor no modo `registrar` (exit 11 se o *swap* continuar alto) | W-6c; C-10 e DT-023 |
| `--exigir-trilha-completa` | não | **não** (a DT-020 §5 não tem o critério; igual ao X-18) | G-4 de 05/11 |
| Infra na VM | `~/cs3-infra`, sessão `cs3` | `~/rc-infra`, sessão `rc`, voláteis em `/run/tccdiag/coord-rc` | separação dos marcos de fim |
| Saída | `runs/campanha-s3-calibracao` | `runs/campanha-s3-recalibracao` | — |

## 1. Pré-condições

| # | Item | Bloqueia |
|---|---|---|
| P-1 | Aviso do coordenador para executar; tag `int/2026-11-06-bench` fechada, com as suítes do integrador registradas | tudo |
| P-2 | **A tag passa `--s3-replay-late-max 268` ao programa** pelo arquivo de parâmetros (campo `s3_replay_late_max`, com origem; DT-026 §8: "gravadas no `execucao.json` e no arquivo de parâmetros com origem"). O 2.2 confere | tudo |
| P-3 | Nenhuma fase em curso na VM; *lock* livre; 0 *timers* `tcc-heal*`; 0 *units* `tcc-amostrador-*` (o piloto já fechou e recriou o *cluster* no desenho S3, roteiro do piloto §8.3) | 2.5 |
| P-4 | *Host* em H0 há ≥ 5 min antes do GO, como no X-18 | GO |
| P-5 | Disco da VM: livre ≥ 1,5 × 32 × 14 MiB = 672 MiB além do *bundle* | 2.5 |
| P-6 | W-9 da tag aceita o arquivo de parâmetros da recalibração (esquema `campanha-s3/1` com os `s3_*` e o `s3_replay_late_max`) e deixa o `ReplayValidate` fora de λ₅, da excedência e da referência (DT-026 §8) | só a derivação, depois |

## 2. Preparo

### 2.1 Host: bundle da tag e auxiliares (H2; ~10 min)

```bash
# host. Trocar SHA_DA_PONTA pelo SHA completo da tag int/2026-11-06-bench.
T=SHA_DA_PONTA
[[ "$T" =~ ^[0-9a-f]{40}$ ]] || echo "T invalido: trocar SHA_DA_PONTA"
TCC=/c/Users/Francesco/Development/tcc
for c in 494ccce 7c94cfb a97675f; do git -C $TCC/wt-ctrl-integ merge-base --is-ancestor $c "$T" && echo "T contem $c"; done   # W-6c, W-10, P-48
S7=$(git -C $TCC/wt-ctrl-integ rev-parse --short=7 "$T"); echo "S7=$S7"
git -C $TCC/wt-ctrl-integ worktree add --detach "$TCC/wt-cs3-$S7" "$T"
cd "$TCC/wt-cs3-$S7" && git status --short | wc -l                   # 0
grep -c -- '--sonda-replay)' scripts/run-campanha-s3.sh               # >= 1
H=$TCC/dados-vm/cs3-2026-11; D=$H/bundle-rc-$S7; mkdir -p "$D/aux"
git -c core.autocrlf=false archive --format=tar --prefix="cs3-$S7/" "$T" -o "$D/cs3-$S7.tar"
(cd workload && mvn -o -q -T1 -DskipTests package)                   # faltando plugin offline: o mesmo sem -o
cp workload/target/session-workload-0.1.0-SNAPSHOT.jar "$D/"
(cd "$D" && sha256sum cs3-$S7.tar session-workload-0.1.0-SNAPSHOT.jar | sed 's/ \*/  /' > bundle-$S7.sha256 && cat bundle-$S7.sha256)
R="docs/plano-implementacao-controlador:docs/roteiro-recalibracao-2026-11-06.md"
extrair() { git -C $TCC/wt-plano-ctrl show "$R" | awk -v m="<!-- arquivo: $1 -->" '$0 == m {f = 1; next} f == 1 && /^```/ {f = 2; next} f == 2 && /^```$/ {exit} f == 2 {print}'; }
for f in recriar-cs3.sh supervisor-cs3.sh av-conferir.py rc-param.py; do extrair "$f" > "$D/aux/$f"; done
(cd "$D/aux" && sha256sum * | sed 's/ \*/  /' > ../aux-$S7.sha256 && cat ../aux-$S7.sha256)    # igual a tabela da secao 10
```

### 2.2 Host: arquivo de parâmetros da recalibração (H2; ~3 min)

Base: o arquivo final da calibração X-18 (`cfd5fa6a…`, os 19 números da DT-020 §4.1; C-6 e C-7 conferidos na seção
3). O `rc-param.py` acrescenta os cinco `s3_*` da L+ com a origem "seleção do ponto de operação pela regra 6.5 do piloto
de 06/11" e o `s3_replay_late_max` = 268 com a origem da DT-026.

```bash
# host
PB=$H/x18-20261106T050100Z/entrada/campanha-s3-calibracao.json
sha256sum "$PB" | cut -c1-64                                         # cfd5fa6a0d5de696d52dac1ae1646b1ae87eb9303dc7533cedba8a5e24af9a98
mkdir -p "$D/entrada"
python -I "$D/aux/rc-param.py" --base "$PB" --saida "$D/entrada/campanha-s3-recalibracao.json" --late-max 268 \
  --late-max-origem "DT-026 (P-3): maior numero de logouts confirmados por execucao no piloto de 06/11, 267, mais 1"
#   esperado: 1ca5877f3b2ea01982477fd45e52befb9a794a71d5bf7d39f025560942d7222b
python -I "$TCC/wt-cs3-$S7/scripts/campanha_s3_conferir.py" parametros --arquivo "$D/entrada/campanha-s3-recalibracao.json" --t-enc-pior-s 21 | tail -8
#   esperado: os cinco s3_* e s3_replay_late_max 268 depois dos 19 numeros
#   "Erro: parametros desconhecidos: s3_replay_late_max": a tag nao tem a P-2; PARAR e avisar o coordenador (secao 9, D-1)
cp /c/Users/Francesco/Development/tcc/dados-vm/cs3-2026-11/bundle-1f65a02/aux/referencia-S3-*-inerte.json "$D/entrada/"
(cd "$D/entrada" && sha256sum * | sed 's/ \*/  /' > SHA256SUMS && cat SHA256SUMS)
#   referencia-S3-async-inerte.json 05342de4..., referencia-S3-sync-inerte.json 30dee87f... (as do X-18)
```

### 2.3 Transferência, conferência e variáveis (H2; ~5 min)

```bash
# host
VMIP=172.27.114.62     # VMIP, SSHO e vm() como na secao 0 do roteiro do X-17/X-18
SSHO=(-o BatchMode=yes -o ConnectTimeout=10 -o HostKeyAlias=172.18.251.140 -i "$HOME/.ssh/id_ed25519_tccf3")
vm() { sed 's/\r$//' | ssh "${SSHO[@]}" "server1@$VMIP" 'bash -s'; }
scp "${SSHO[@]}" "$D/cs3-$S7.tar" "$D/session-workload-0.1.0-SNAPSHOT.jar" "$D/bundle-$S7.sha256" "$D/aux-$S7.sha256" "server1@$VMIP:"
scp -r "${SSHO[@]}" "$D/aux" "server1@$VMIP:rc-aux-$S7"
scp -r "${SSHO[@]}" "$D/entrada" "server1@$VMIP:rc-entrada-$S7"
```

```bash
# VM (vm <<'FIMVM' ... FIMVM). Trocar XXXXXXX pelo S7 do 2.1.
S7=XXXXXXX
cd ~ && sha256sum -c bundle-$S7.sha256                               # 2 OK
(cd ~/rc-aux-$S7 && sha256sum -c ../aux-$S7.sha256)                  # 4 OK
(cd ~/rc-entrada-$S7 && sha256sum -c SHA256SUMS)                     # 3 OK
if [ -e ~/cs3/cs3-$S7 ]; then echo "cs3-$S7 ja existe: usar como esta"; else mkdir -p ~/cs3 && tar -xf ~/cs3-$S7.tar -C ~/cs3; fi
CS3=~/cs3/cs3-$S7; mkdir -p $CS3/workload/target $CS3/runs/entrada-rc ~/rc-infra
[ -f $CS3/workload/target/session-workload-0.1.0-SNAPSHOT.jar ] || cp ~/session-workload-0.1.0-SNAPSHOT.jar $CS3/workload/target/
cp ~/rc-aux-$S7/*.sh ~/rc-aux-$S7/*.py ~/rc-infra/
cp ~/rc-entrada-$S7/*.json $CS3/runs/entrada-rc/
grep -rlI $'\r' $CS3/scripts $CS3/cluster $CS3/runs/entrada-rc ~/rc-infra || echo "sem CR"
(cd $CS3 && sha256sum -c ~/cs3-infra/congelados.sha256)               # 18 OK (lista do X-17/X-18; nenhum mudou de 1f65a02 a ade376e)
grep -q -- '--sonda-replay)' $CS3/scripts/run-campanha-s3.sh && grep -q -- '--swap-limite-kb)' $CS3/scripts/run-campanha-s3.sh \
  && echo "W-10 e W-6c na ponta" || { echo "ponta sem W-10 ou W-6c: PARAR"; exit 1; }
cat > ~/rc-infra/vars.sh <<EOF2
# recalibracao X-19 (roteiro-recalibracao-2026-11-06.md). Todo bloco da VM comeca com: source ~/rc-infra/vars.sh
S7=$S7
CS3=$HOME/cs3/cs3-$S7
E=$HOME/cs3/cs3-$S7/runs/entrada-rc
SRC=$HOME/cs3/cs3-$S7/runs/campanha-s3-recalibracao
JAR=$HOME/cs3/cs3-$S7/workload/target/session-workload-0.1.0-SNAPSHOT.jar
JAR_SHA=$(sha256sum $CS3/workload/target/session-workload-0.1.0-SNAPSHOT.jar | cut -c1-64)
CTRL=$HOME/cs3/cs3-$S7/cluster/controlador/controlador-calibracao-s3.json
CTRL_SHA=c4e05c3cc40c1900378de7f709ac3f99fea896a1ba2d1f0addd9d78a53a574ff
REF_A=$HOME/cs3/cs3-$S7/runs/entrada-rc/referencia-S3-async-inerte.json
REF_A_SHA=05342de490d817208d4c26625a4718f4969f4d02244b4b24758bdc73a2016d15
REF_S=$HOME/cs3/cs3-$S7/runs/entrada-rc/referencia-S3-sync-inerte.json
REF_S_SHA=30dee87fb79cb69dbf3f9a61caa6a2a9354f20a8d10f2370af5f9925a532212f
PRC=$HOME/cs3/cs3-$S7/runs/entrada-rc/campanha-s3-recalibracao.json
PRC_SHA=1ca5877f3b2ea01982477fd45e52befb9a794a71d5bf7d39f025560942d7222b
MRC=$HOME/cs3/cs3-$S7/runs/entrada-rc/matriz-recalibracao.tsv
MRC_SHA=3f20219d2e8f01ded10fea80d51f2d7c87f4c7361abc45a51f8a122baa7c0351
ORC="--swap-limite-kb 1048576 --sonda-replay"
EOF2
cat >> ~/rc-infra/vars.sh <<'EOF2'
lancar_rc() {  # lancar_rc <fase> <tsv da fila> <cmd> <janela do supervisor>
  if pgrep -f 'run-campanha-s3[.]sh' > /dev/null; then echo "orquestrador ainda rodando: nao lancar"; return 1; fi
  if pgrep -f '^bash /home/[^ ]+/rc-infra/supervisor-cs3[.]sh' > /dev/null; then echo "supervisor anterior ainda rodando: esperar SUP_FIM"; return 1; fi
  local lim; lim=$(( $(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["parametros"]["exec_teto_s"]["valor"])' "$PRC") + 90 ))
  tmux has-session -t rc 2>/dev/null || tmux new-session -d -s rc -n orq -x 220 -y 50
  tmux send-keys -t rc:orq clear Enter; sleep 1; tmux clear-history -t rc:orq
  tmux new-window -d -t rc -n "$4" "SUP_JANELA=rc:orq SUP_INFRA=$HOME/rc-infra SUP_VOLATIL=/run/tccdiag/coord-rc bash $HOME/rc-infra/supervisor-cs3.sh $SRC $1 $2 $3 $lim registrar $CS3; echo SUP_FIM; sleep 86400"
  sleep 2; tmux send-keys -t rc:orq "bash $3" Enter
}
EOF2
cat ~/rc-infra/vars.sh; bash -n ~/rc-infra/vars.sh && echo "vars ok"
```

### 2.4 Matriz e `--dry-run` (H2; ~3 min)

```bash
# VM
source ~/rc-infra/vars.sh; cd $CS3
python3 -I scripts/campanha_s3_matriz.py gerar --modo calibracao \
  --celulas "ASYNC-none:calibracao:5,ASYNC-F1:calibracao:5,ASYNC-F2:calibracao:5,SYNC-F2:calibracao:3,SYNC-none:calibracao:3,ASYNC-F4:calibracao:5" \
  --ordem-seed 20261106 --s0 700000 --passo-semente 100 --i0 0 --saida $MRC
printf '%s  %s\n' $MRC_SHA $MRC | sha256sum -c                       # OK (3f20219d...)
python3 -I scripts/campanha_s3_conferir.py parametros --arquivo $PRC --t-enc-pior-s 21 | grep -E '^(exec_teto_s|volume_exec_mb|s3_)'
bash scripts/run-campanha-s3.sh --dry-run --matriz $MRC --matriz-sha256 $MRC_SHA --fase calibracao \
  --parametros $PRC --parametros-sha256 $PRC_SHA --controlador $CTRL --controlador-sha256 $CTRL_SHA \
  --referencia-async $REF_A --referencia-async-sha256 $REF_A_SHA --referencia-sync $REF_S --referencia-sync-sha256 $REF_S_SHA \
  --jar $JAR --jar-sha256 $JAR_SHA --saida $SRC $ORC 2>&1 | tee ~/rc-infra/dry-run.txt | grep -E '^(DRY-RUN|PLANO execucoes|Erro)'
grep -m1 '^      timeout' ~/rc-infra/dry-run.txt | grep -oE '\-\-s3-[a-z-]+ [0-9.]+|--s3-replay-probe|--sweep' | tr '\n' ' '; echo
ls -d $SRC 2>/dev/null || echo "saida ainda nao existe (certo)"
```

Esperado: `DRY-RUN … (SHA-256 confere) … (25 numeros, todos com origem)` (os 19, os cinco `s3_*` e o
`s3_replay_late_max`); `DRY-RUN opcoes: swap_limite_kb=1048576 exigir_trilha_completa=nao sonda_replay=sim`; a linha
`DRY-RUN carga S3:` com as cinco opções da L+ (e o `--s3-replay-late-max 268`, na forma que a tag usar); `PLANO
execucoes=26`, de `i=0 celula=ASYNC-none … seed=700000` a `i=25 celula=ASYNC-none … seed=702500`; na linha do programa,
`--s3-users 40 --s3-think-ms 500 --s3-session-median-sec 10 --s3-logout-prob 0.9 --s3-admin-mean-sec 5`,
`--s3-replay-late-max 268`, `--s3-replay-probe` e `--sweep`. Sem o `--s3-replay-late-max 268` na linha: PARAR (P-2).

Ordem das 26 (semente = 700 000 + 100·i): 0 ASYNC-none, 1 SYNC-F2, 2 ASYNC-F1, 3 ASYNC-F4, 4 ASYNC-F2, 5 SYNC-none,
6 ASYNC-none, 7 ASYNC-F1, 8 SYNC-F2, 9 ASYNC-F4, 10 ASYNC-F2, 11 SYNC-none, 12 ASYNC-F4, 13 ASYNC-F2, 14 ASYNC-F1,
15 SYNC-none, 16 SYNC-F2, 17 ASYNC-none, 18 ASYNC-F2, 19 ASYNC-F1, 20 ASYNC-none, 21 ASYNC-F4, 22 ASYNC-F2, 23 ASYNC-F4,
24 ASYNC-F1, 25 ASYNC-none. F2/F4 (pedem `go rede`): 1, 3, 4, 8, 9, 10, 12, 13, 16, 18, 21, 22, 23.

### 2.5 Recriação com o bundle novo (H2; ~3 min)

```bash
# VM
source ~/rc-infra/vars.sh
TS=$(date -u +%Y%m%dT%H%M%SZ); A=~/rc-infra/cluster-antes-$TS; mkdir -p $A
for n in isn1 isn2 isn3; do sudo podman inspect $n > $A/inspect-$n.json 2>&1; sudo podman logs $n > $A/logs-$n.txt 2>&1; done
sha256sum $A/* > $A.sha256
flock -n /var/lock/tcc-vm.lock bash ~/rc-infra/recriar-cs3.sh $CS3 ~/rc-infra/recriacao-$TS > /dev/null 2>&1; echo "recriar rc=$?"
grep -vE 'AVAILABLE$|rebalancing_enabled true|ISPN000094' ~/rc-infra/recriacao-$TS/conferencia.txt
free -m | tail -2; df -Pm $CS3/runs | tail -1                       # livre >= 672 MiB (P-5)
```

Esperado: `RECRIAR-CS3 OK` e *swap* perto de 40 MB. O piloto deixou os *caches* no desenho S3 (DENY +
`PREFERRED_NON_NULL`), e a recriação os refaz pelos JSON do bundle novo de qualquer modo.

## 3. Tetos: `exec_teto_s` e `volume_exec_mb`

A recalibração segue as regras da calibração (DT-026 §4), então o teto é o da DT-020 §4.1 com o período da calibração
(`convergencia_periodo_s` = 5 s, o W provisório do controlador da calibração) e o teto da convergência de 600 s:

`exec_teto_s = 300 + 21 + 60 + 600 + 5 + 1,5 × (t_pre + t_pos)`, com `t_pre` e `t_pos` = o maior entre os da R2 da
V-W6b (2,4 s e 13,5 s) e o medido (C-6; regra 7.17), nunca abaixo de 1010 s.

Medido na p2-d (`dados-vm/piloto-2026-11-06/p-20261106T081543Z/piloto/runs/p2-d/`, carga L+, `--sweep`,
`--s3-replay-probe` com o tardio truncado em 200) e na d (mesma carga, sem a sonda). `t_pre` = âncora − lançamento (`execucao.json`); `t_pos` = `fim_utc` − instante da
convergência (`t_convergencia_abs_ns` do `convergencia-resumo-rep-001.json`), que inclui a varredura e o *replay* tardio:

| Execução | `t_pre` (s) | `t_pos` (s) | processo, do lançamento à saída (s) | volume (MiB) |
|---|---|---|---|---|
| p2-d exec-0000 | 1,01 | 11,62 | 312,4 | 3 |
| p2-d exec-0001 | 1,02 | 11,57 | 312,3 | 3 |
| p2-d exec-0002 | 1,22 | 11,72 | 312,5 | 3 |
| d exec-0000 a 0002 | 1,01 a 1,22 | 11,63 a 11,71 | 312,4 a 312,5 | 3 |

O *replay* tardio roda depois da linha `Fim da carga:` (a linha veio a +301,0 s do lançamento nas três p2-d) e cabe,
com a convergência e a varredura, nos ~11,4 s até a saída. Com 268 candidatas no lugar de 200, são 68 leituras a mais,
da ordem de segundos. As medidas ficam **abaixo** das do I-3b, então a fórmula dá o mesmo valor do X-18: 300 + 21 +
60 + 600 + 5 + 1,5 × (2,4 + 13,5) = 1009,85 → **1010 s**. O pior caso real da recalibração (braço A, sem espera de
religamento, convergência até o teto) fica em ~936 s (V-W6b §5) mais esses segundos, abaixo de 1010 (V-W6b §5). O piloto só mediu ASYNC F2; o maior `t_pos` do X-18 foi 12,545 s
(`exec-0009-SYNC-F2-A`, DT-025), também abaixo de 13,5 s.

**1014 s (o `exec_teto_s` da parte 2, DT-025) também bastaria**, mas ele é o da avaliação: a mesma fórmula com o período
igual ao W calibrado (12 s) e os `t_pre`/`t_pos` do X-18. Na recalibração vale o da calibração, e não é preciso
recalcular. `volume_exec_mb` fica em 14 (C-7: o maior entre 14 e o medido, 3 MiB).

## 4. Execução (fase `calibracao`)

### 4.1 PowerShell: H0 e covariáveis

O bloco do 4.1 do roteiro do X-17/X-18, trocando `X-17` por `X-19` e `host-proc-io-x17.csv` por
`host-proc-io-x19.csv`. Esperar 5 min em H0 antes do 4.3.

### 4.2 VM: amostrador h4, bloco de abertura e comandos

```bash
# VM
source ~/rc-infra/vars.sh; cd $CS3
flock -n /var/lock/tcc-vm.lock true && echo "lock livre" || echo "LOCK OCUPADO: parar"
pgrep -af 'run-rebaseline|run-campanha-s3|supervisor-|session-workload|inject-' || echo "nenhum processo residual"
systemctl list-timers --all --no-legend 'tcc-heal*' | grep -c .       # 0
systemctl list-units --all --no-legend 'tcc-amostrador-*' | grep -c . # 0
H4RC=diag-$(date -u +%Y%m%dT%H%M%SZ)
printf 'H4RC=%s\nBOOTRC=%s\n' "$H4RC" "$(cat /proc/sys/kernel/random/boot_id)" >> ~/rc-infra/vars.sh
bash scripts/amostrador-infra.sh iniciar --id $H4RC --espelho-dir ~/rc-infra/$H4RC
bash scripts/amostrador-infra.sh estado --id $H4RC
sudo install -d -m 0755 -o "$(id -un)" -g "$(id -gn)" /run/tccdiag/coord-rc
date -Is > ~/rc-infra/abertura-iso.txt
source ~/rebaseline-infra/bloco.sh; bloco ~/rc-infra/inicio-$(date -u +%Y%m%dT%H%M%SZ).txt > /dev/null
source ~/rc-infra/vars.sh
for F in calibracao reposicoes; do
cat > ~/rc-infra/cmd-rc-$F.sh <<EOF
#!/bin/bash
# recalibracao X-19, fase $F (roteiro-recalibracao-2026-11-06.md, 4.2); gerado em $(date -u +%FT%TZ)
cd $CS3 || { echo "ORQ_RC=2 cd falhou"; exit 2; }
export CS_PASSWORD="\$(sed -n 's/^ *- PASS=//p' cluster/podman-compose-sem-caches.yml | head -1)"
bash scripts/run-campanha-s3.sh --matriz $MRC --matriz-sha256 $MRC_SHA --fase $F \\
  --parametros $PRC --parametros-sha256 $PRC_SHA --controlador $CTRL --controlador-sha256 $CTRL_SHA \\
  --referencia-async $REF_A --referencia-async-sha256 $REF_A_SHA --referencia-sync $REF_S --referencia-sync-sha256 $REF_S_SHA \\
  --jar $JAR --jar-sha256 $JAR_SHA --boot-esperado $BOOTRC --h4-id $H4RC --saida $SRC $ORC
echo "ORQ_RC=\$? \$(date -u +%FT%TZ)"
EOF
bash -n ~/rc-infra/cmd-rc-$F.sh || echo "ERRO em cmd-rc-$F.sh"
done
cat ~/rc-infra/cmd-rc-calibracao.sh
```

### 4.3 VM: lançamento e GO

```bash
# VM
source ~/rc-infra/vars.sh
test ! -e $SRC && echo "saida da recalibracao nova"
lancar_rc calibracao $MRC ~/rc-infra/cmd-rc-calibracao.sh sup1
sleep 30; cat ~/rc-infra/supervisor-calibracao-*.log
```

O supervisor (janela `rc:sup1`, modo `registrar`) dá o GO sob a autorização do Gestor: `go calibracao <AAAA-MM-DD>`
no início e `go rede 1` antes da i = 1 (SYNC-F2).

### 4.4 Conferência da primeira execução e do protocolo (sem parar)

Logo depois do lançamento da i = 0, a linha `lancando a carga (…)` do log tem de trazer a carga e os instrumentos:

```bash
# VM
source ~/rc-infra/vars.sh
grep -m1 'exec i=0: lancando a carga' $SRC/calibracao/orquestrador.log \
  | grep -oE '\-\-s3-[a-z-]+ [0-9.]+|--s3-replay-probe|--sweep' | tr '\n' ' '; echo
#   esperado: as cinco da L+, --s3-replay-late-max 268, --s3-replay-probe e --sweep
```

Faltando qualquer uma: `touch $SRC/PARAR` (a fase para antes da i = 1) e chamar o Senior. Depois do veredito da i = 2,
o checklist de protocolo das três primeiras, como no 5.2 do roteiro da avaliação:

```bash
# VM
source ~/rc-infra/vars.sh
python3 -I ~/rc-infra/av-conferir.py piloto $SRC --ate-i 2 | tee ~/rc-infra/protocolo-0-2.txt | tail -n 3
grep -h -oE 'tardias_candidatas=[0-9]+ tardias_amostra=[0-9]+' $SRC/exec-000[0-2]-*/stdout.log
```

`PROTOCOLO OK`: nada a fazer (a frase "os 3 blocos contam" do script vem da avaliação; aqui são as i = 0 a 2, que
contam). `VER COM O SENIOR` por item de protocolo (não por h4 ou recusa comum): `touch $SRC/PARAR` e chamar o
Senior. As trilhas fecharam completas nas 26 do X-18 (`resumo-calibracao.tsv`), então o item B-7 também vale aqui.
A i = 0 é sem falha e não tem *replay* tardio (sem marcador `start`, o W-10 não faz o tardio); a i = 1 é SYNC-F2 e a
i = 2, ASYNC-F1.

### 4.5 Acompanhamento (só leitura, a cada 30 a 60 min; H0)

```bash
# VM
source ~/rc-infra/vars.sh
tail -n 3 $SRC/calibracao/orquestrador.log
tail -n 1 $(ls -t /run/tccdiag/coord-rc/progresso-calibracao-*.log | head -1)
ls ~/rc-infra/ | grep -E '^(ALERTA|FIM)-' || echo "sem alerta nem fim"
tmux list-windows -t rc
```

Duração: 26 × ~395 s (as três p2-d, da recriação ao veredito: 398, 390 e 396 s) ≈ 2,9 h. Pior caso: 26 × 1490 s
(W-6c com `--swap-limite-kb`, como no 7 do roteiro do X-17/X-18) ≈ 10,8 h.

## 5. Reposições (depois de `FIM-calibracao-OK`; uma vez)

Como no 5.7 do roteiro do X-17/X-18: perda (RECUSADA ou DESCARTADA-INFRA) reposta uma vez com o próximo i e semente
nova; teto h4 = 6; segunda perda da mesma reposição pede o Gestor.

```bash
# VM
source ~/rc-infra/vars.sh; cd $CS3
python3 -I ~/rc-infra/av-conferir.py execucoes $SRC --tsv ~/rc-infra/execucoes-antes-reposicoes.tsv | tail -n 3
python3 -I scripts/campanha_s3_matriz.py repor --matriz $MRC --saida $SRC --ordem-seed 20261106 --s0 700000 \
  --passo-semente 100 --teto-h4 6 --fase calibracao --dry-run
# se listar reposicoes (senao, ir ao 6):
python3 -I scripts/campanha_s3_matriz.py repor --matriz $MRC --saida $SRC --ordem-seed 20261106 --s0 700000 \
  --passo-semente 100 --teto-h4 6 --fase calibracao | tee ~/rc-infra/repor.txt
lancar_rc reposicoes $SRC/reposicoes/reposicoes.tsv ~/rc-infra/cmd-rc-reposicoes.sh supr1
```

As reposições começam em i = 26 (semente 702 600). GO: `go reposicoes <data>` e `go rede <i>` se houver F2/F4.

## 6. Fim

### 6.1 VM: conferência, teto do *replay* tardio e fim do H0

```bash
# VM
source ~/rc-infra/vars.sh; cd $SRC
ls ~/rc-infra/ | grep -E '^(ALERTA|FIM)-'
for F in calibracao reposicoes; do
  [ -f $F/MANIFEST.sha256 ] || continue
  echo "$F: linhas .tmp = $(grep -c " $F/MANIFEST.sha256.tmp\$" $F/MANIFEST.sha256)"
  grep -v " $F/MANIFEST.sha256.tmp\$" $F/MANIFEST.sha256 | sha256sum -c --quiet; echo "$F manifest rc=$?"
done
for d in exec-*/; do
  case $d in *.abortada-*) echo "abortada (DT-014): $d"; continue ;; esac
  if [ -f $d/MANIFEST.sha256 ]; then (cd $d && sha256sum -c --quiet MANIFEST.sha256) || echo "DIVERGE $d"; else echo "sem MANIFEST: $d"; fi
done
# DT-026 §2.1: execucao com tardias_candidatas >= 268 bateu no teto do replay tardio; e marcada e relatada
for d in exec-*/; do c=$(grep -oE 'tardias_candidatas=[0-9]+' $d/stdout.log 2>/dev/null | cut -d= -f2); [ -n "$c" ] && [ "$c" -ge 268 ] && echo "TETO DO TARDIO: $d ($c)"; done; echo "conferencia do teto do tardio feita"
python3 -I ~/rc-infra/av-conferir.py execucoes $SRC --tsv ~/rc-infra/execucoes-final.tsv | tail -n 3
systemctl list-timers --all --no-legend 'tcc-heal*' | grep -c .       # 0
bash $CS3/scripts/amostrador-infra.sh parar --id $H4RC --espelho-dir ~/rc-infra/$H4RC
source ~/rebaseline-infra/bloco.sh
bloco ~/rc-infra/fim-$(date -u +%Y%m%dT%H%M%SZ).txt "$(cat ~/rc-infra/abertura-iso.txt)" > /dev/null
cp -p /run/tccdiag/coord-rc/progresso-*.log ~/rc-infra/ 2>/dev/null
```

Aqui termina o H0.

### 6.2 Cópia ao *host* com `sha256sum -c` dos dois lados

Como no 6.2 do roteiro do X-17/X-18, trocando `x18` por `rc`, `campanha-s3-calibracao entrada` por
`campanha-s3-recalibracao entrada-rc` e `~/cs3-infra` por `~/rc-infra`:

```bash
# VM
source ~/rc-infra/vars.sh; TS=$(date -u +%Y%m%dT%H%M%SZ); echo "TS=$TS"
tar -C $CS3/runs -cf ~/rc-infra/rc-brutos-$TS.tar campanha-s3-recalibracao entrada-rc
tar -C ~ -cf ~/rc-infra/rc-infra-$TS.tar --exclude='*.tar' rc-infra
cd ~/rc-infra && sha256sum rc-brutos-$TS.tar rc-infra-$TS.tar | tee rc-$TS.sha256 && sha256sum -c rc-$TS.sha256
```

```bash
# host. Trocar TS_DA_VM pelo TS impresso na VM.
TS=TS_DA_VM; mkdir -p "$H/rc-$TS" && cd "$H/rc-$TS"
scp "${SSHO[@]}" "server1@$VMIP:rc-infra/rc-brutos-$TS.tar" "server1@$VMIP:rc-infra/rc-infra-$TS.tar" "server1@$VMIP:rc-infra/rc-$TS.sha256" .
sha256sum -c rc-$TS.sha256                                           # 2 OK
tar -xf rc-brutos-$TS.tar && tar -xf rc-infra-$TS.tar
cd campanha-s3-recalibracao
for F in calibracao reposicoes; do [ -f $F/MANIFEST.sha256 ] && { grep -v " $F/MANIFEST.sha256.tmp\$" $F/MANIFEST.sha256 | sha256sum -c --quiet && echo "$F OK no host"; }; done
n=0; for d in exec-*/; do [ -f "$d/MANIFEST.sha256" ] && { (cd "$d" && sha256sum -c --quiet MANIFEST.sha256) && n=$((n+1)) || echo "DIVERGE $d"; }; done; echo "MANIFEST por execucao OK: $n"
```

### 6.3 Entrega ao W-9

`"$H/rc-$TS/ENTREGA-W9.txt"`, como no 6.3 do roteiro do X-17/X-18, com estas diferenças: o pré-registro é a **DT-026**
(`37a1099`, `d3d2988`) sobre as regras da seção 7 do pré-registro da calibração, com os complementos 1, 3 e 3a, a regra
de memória do complemento 2 e o C-8 (DT-026 §4); o arquivo de parâmetros é o
`campanha-s3-recalibracao.json` (`1ca5877f…`, com os `s3_*` e o `s3_replay_late_max`); as execuções que bateram no
teto do *replay* tardio (6.1) vão listadas; e a frase "a derivação é a da recalibração e substitui, para o benchmark
refeito, os valores da DT-025; ReplayValidate fica fora de λ₅, da excedência e da referência (DT-026 §8)". A
derivação usa o `analysis/calibracao_s3.py` da tag (P-6).

## 7. Tempo estimado

| Passo | Tempo |
|---|---|
| 2.1 a 2.5 | 25-35 min |
| 4.1 a 4.3 (com 5 min de H0) | ~15 min |
| 26 execuções | ~2,9 h (pior caso ~10,8 h) |
| reposições | ~6,6 min cada |
| 6.1 a 6.3 | 25-35 min |
| **Total** | **~4 a 4,5 h** |

## 8. Falhas prováveis (além da seção 8 do roteiro do X-17/X-18)

| Sintoma | O que fazer |
|---|---|
| 2.2: `parametros desconhecidos: s3_replay_late_max` | a tag não tem a P-2; parar e avisar o coordenador (D-1) |
| 2.4 ou 4.4: linha do programa sem `--s3-replay-late-max 268`, sem `--sweep` ou sem a L+ | no 2.4, não lançar; no 4.4, `touch $SRC/PARAR` (a fase para antes da i = 1) e chamar o Senior. O supervisor não confere a linha |
| exit 11 (`parada swap … depois da recriacao`, P-59; o supervisor grava `ALERTA-FIM-calibracao`) | não retomar; diagnóstico de memória à mão (`free -m`, `swapon --show`, 15 maiores `VmSwap`) em `~/rc-infra/memoria-exit11-<UTC>.txt`; chamar o Senior (DT-023) |
| `varredura-rep-001.erro.txt` numa execução (leitura global com *cache* DEGRADED) | nada na hora; a execução segue e vai listada na entrega (DT-026 trata a varredura com erro na avaliação; na recalibração, o W-9 decide pela regra dele) |
| `tardias_candidatas` ≥ 268 | nada na hora; marcada e relatada (DT-026 §2.1) |
| a i = 0 sai sem `tardias_*` no `stdout.log` | esperado: célula sem falha, sem Logout na falha nem na recuperação |

## 9. O que depende da tag

| # | Dependência | Sem ela |
|---|---|---|
| D-1 | o orquestrador passar `--s3-replay-late-max` a partir do arquivo de parâmetros (`s3_replay_late_max`, com origem), como pede a DT-026 §8 | o *replay* tardio fica em 200 e trunca (na p2-d as candidatas foram 214, 238 e 216), contra a P-3: não rodar |
| D-2 | `--sonda-replay`, carga S3 nativa (`s3_*`), P-48 e W-6c na ponta | o 2.1 e o 2.3 param |
| D-3 | W-9 da tag com os `s3_*`/`s3_replay_late_max` no arquivo de parâmetros e o `ReplayValidate` fora das regras | só a derivação depois |
| D-4 | o SHA do jar e do bundle | entram no 2.1 e na entrega |

Se a tag passar o `--s3-replay-late-max` por outro caminho (uma opção do orquestrador em vez do campo), o 2.2 e o
`ORC` do 2.3 mudam, e o Senior ajusta este anexo antes do GO.

## 10. Arquivos auxiliares

Extraídos no 2.1 do *blob* deste anexo. `recriar-cs3.sh`, `supervisor-cs3.sh` e `av-conferir.py` são os mesmos do
roteiro da avaliação (`02f4e5d`); o `rc-param.py` é novo.

| Arquivo | SHA-256 | Vai para |
|---|---|---|
| `recriar-cs3.sh` | `e8b96d6180b5d34f8abea9f3c084d5a662e9b353790abaaa2c21d3c9a900b03d` | `~/rc-infra/` |
| `supervisor-cs3.sh` | `f606b9e2e9d2a39d6171a77d1365a19947663fa47587fbd558a2a82cbe5f720f` | `~/rc-infra/` |
| `av-conferir.py` | `030fbf48953802b6c8083d3ec0e66151d54d7ba1b57d3b5d2c8a0e2e094e5e1a` | `~/rc-infra/` |
| `rc-param.py` | `d3c8ba8cb4e48e7b110d35d2187b7e5e7922aa504a53ca82ed3dc8de94ca7f8d` | *host* (2.2) |

### 10.1 `recriar-cs3.sh`

<!-- arquivo: recriar-cs3.sh -->
```bash
#!/bin/bash
# recriar-cs3.sh <bundle> <dir-saida>: recria o cluster com o XML SEM caches do bundle S3 (W-6, TD-015) e confere o
# desenho: ESTAVEL (3 nos HEALTHY com 3 membros por 20 s seguidos, ate 180 s; criterio do recriar-rb-20805b0.sh),
# nenhum dos seis caches antes do cria-caches.sh (lista GET /rest/v2/caches de cada no so com os internos ___*; o
# ?action=config de cache inexistente da HTTP 500 no 15.0.21, nao 404), os seis criados pela REST com leitura de volta
# byte a byte nos 3 nos, e a camada 3 (rebalancing_enabled true e os seis AVAILABLE nos 3 nos). Sem netem/timer.
# Roteiro X-17/X-18 (roteiro-x17-calibracao-2026-11-05.md), passo 2.2. Rodar sob flock /var/lock/tcc-vm.lock.
# Ultima linha: "RECRIAR-CS3 OK" ou "RECRIAR-CS3 FALHA (<n>)". Exit 0 ou 1.
set -u
B=$1; D=$2; mkdir -p "$D"; cd "$B" || exit 1
C=cluster/podman-compose-sem-caches.yml
S=$(sed -n 's/^ *- PASS=//p' "$C" | head -1)
CACHES="sessions counters sessions-async counters-async canario canario-async"
PORTAS="11222 11223 11224"
nf=0
ok() { echo "OK    $*" | tee -a "$D/conferencia.txt"; }
falha() { echo "FALHA $*" | tee -a "$D/conferencia.txt"; nf=$((nf + 1)); }
cod() { printf 'user = "admin:%s"\n' "$S" | curl -s -o "$3" -w '%{http_code}' -m 5 --digest -K - "http://127.0.0.1:$1$2"; }
hs() { for p in $PORTAS; do rm -f "$D/h.json"; cod "$p" /rest/v2/cache-managers/default/health "$D/h.json" > /dev/null
         tr -d ' \n' < "$D/h.json" 2>/dev/null | grep -o '"health_status":"[A-Z_]*","number_of_nodes":[0-9]*' || echo "$p:ERR"
       done | paste -sd' '; }
echo "inicio $(date -u +%FT%TZ) bundle=$B xml=$(sha256sum cluster/infinispan-sem-caches.xml | cut -c1-64)" > "$D/conferencia.txt"
sudo podman-compose -f "$C" down > "$D/down.log" 2>&1; r=$?; [ $r = 0 ] && ok "down rc=0" || falha "down rc=$r"
sudo podman-compose -f "$C" up -d > "$D/up.log" 2>&1; r=$?; [ $r = 0 ] && ok "up rc=0" || falha "up rc=$r"
T0=$(date +%s); OKS=; EST=
while [ $(( $(date +%s) - T0 )) -lt 180 ]; do
  r=$(hs); echo "$(date +%s.%N) $r" >> "$D/validacao.txt"
  if [ "$(echo "$r" | grep -o '"health_status":"HEALTHY","number_of_nodes":3' | wc -l)" = 3 ]; then
    [ -z "$OKS" ] && OKS=$(date +%s)
    [ $(( $(date +%s) - OKS )) -ge 20 ] && { EST=$(date +%s); break; }
  else OKS=; fi
  sleep 2
done
[ -n "$EST" ] && ok "ESTAVEL size=3 HEALTHY: primeira leitura boa +$((OKS - T0))s, total $((EST - T0))s" \
              || falha "ESTAVEL: nao fechou em 180 s"
for p in $PORTAS; do
  h=$(cod "$p" /rest/v2/caches "$D/lista-antes-$p.json")
  r=$(python3 -I -c 'import json,sys; l=json.load(open(sys.argv[1])); n=[x if isinstance(x, str) else x.get("name") for x in l]; u=sorted(set(sys.argv[2].split()) & set(n)); print(" ".join(u) if u else "nenhum")' \
      "$D/lista-antes-$p.json" "$CACHES" 2>/dev/null) || r="ilegivel"
  [ "$h" = 200 ] && [ "$r" = nenhum ] && ok "antes do cria-caches: nenhum dos seis em :$p ($(tr -d '\r\n' < "$D/lista-antes-$p.json"))" \
    || falha "antes do cria-caches: :$p HTTP $h, presentes: $r (o XML devia vir sem caches)"
done
CRIA_CACHES_SENHA="$S" bash scripts/cria-caches.sh --url http://127.0.0.1:11222 \
  --conferir http://127.0.0.1:11222,http://127.0.0.1:11223,http://127.0.0.1:11224 --usuario admin \
  --dir cluster/caches $CACHES > "$D/caches.tsv" 2> "$D/caches.err"; r=$?
[ $r = 0 ] && ok "cria-caches rc=0 ($(grep -c $'\tPOST\t' "$D/caches.tsv") POST, $(grep -c $'\tGET\t' "$D/caches.tsv") GET)" \
           || falha "cria-caches rc=$r: $(head -c 300 "$D/caches.err")"
for p in $PORTAS; do
  cod "$p" /rest/v2/caches "$D/lista-depois-$p.json" > /dev/null
  cod "$p" /rest/v2/container "$D/container-$p.json" > /dev/null
  grep -q '"rebalancing_enabled":true' "$D/container-$p.json" && ok ":$p rebalancing_enabled true" \
                                                               || falha ":$p rebalancing_enabled diferente de true"
  for c in $CACHES; do
    cod "$p" "/rest/v2/caches/$c?action=get-availability" "$D/x.txt" > /dev/null
    a=$(tr -d '\r\n' < "$D/x.txt"); [ "$a" = AVAILABLE ] && ok ":$p $c AVAILABLE" || falha ":$p $c disponibilidade '$a'"
  done
done
t=$(systemctl list-timers --all --no-legend 'tcc-heal*' | grep -c .)
[ "$t" = 0 ] && ok "nenhum timer tcc-heal*" || falha "timers tcc-heal* ativos: $t"
for n in isn1 isn2 isn3; do
  pid=$(sudo podman inspect -f '{{.State.Pid}}' "$n"); q=$(sudo nsenter -t "$pid" -n tc qdisc show dev eth0 | tr '\n' ';')
  echo "$n StartedAt=$(sudo podman inspect -f '{{.State.StartedAt}}' "$n") qdisc=$q" >> "$D/conferencia.txt"
  case "$q" in *netem*|*clsact*|*"prio 1:"*) falha "$n com regra de rede: $q" ;; *) ok "$n sem regra de rede" ;; esac
done
for n in isn1 isn2 isn3; do sudo podman logs "$n" 2>&1 | grep -E "ISPN000094" | tail -1 | cut -c1-200 >> "$D/conferencia.txt"; done
[ "$nf" = 0 ] && { echo "RECRIAR-CS3 OK" | tee -a "$D/conferencia.txt"; exit 0; }
echo "RECRIAR-CS3 FALHA ($nf)" | tee -a "$D/conferencia.txt"; exit 1
```

### 10.2 `supervisor-cs3.sh`

<!-- arquivo: supervisor-cs3.sh -->
```bash
#!/bin/bash
# supervisor-cs3.sh <SAIDA> <FASE> <TSV da fila> <arquivo de comando> <limiar de progresso, s> <registrar|recriar> <bundle>
#                   [<parar antes de i>]
# Supervisor do coordenador para UMA invocacao de fase do run-campanha-s3.sh (X-17 e X-18), na janela tmux cs3:sup,
# sob a autorizacao do Gestor de 05/11 (rodar o X-17 e a calibracao sem novo aval). Roteiro
# docs/roteiro-x17-calibracao-2026-11-05.md, secao 5.4. Modelo: supervisor-n1s2b.sh e goloop-gen.sh da noite 1.
#  1. GO: copia para cs3:orq so "go <FASE> AAAA-MM-DD" e "go rede <i>" com i de F2/F4 da fila, uma vez por texto e
#     por invocacao; outro pedido grava ALERTA-GO e fica sem resposta (o orquestrador sai com 5 em 1 h).
#  2. Swap (C-10 aprovada, nota de fechamento do DT-020, 2c8b7b8; decisao do coordenador de 05/11): com >= 1 GiB no
#     portao K1, o cluster e recriado antes da execucao, que roda com o mesmo i e a mesma semente, sem contar como
#     recusa. Le /proc/meminfo enquanto a ultima linha do log e o "portao K1" de uma execucao. Modo "registrar" (W-6c na
#     ponta, --swap-limite-kb 1048576): quem recria e o orquestrador; aqui so se registra a leitura. Modo "recriar"
#     (sem W-6c): Ctrl-C no K1 (DT-014 item 1: sem stdout.log, mesmo i e mesma semente), diagnostico, recriar-cs3.sh
#     sob o lock e relancamento, quantas vezes for preciso, sempre registrando; a terceira seguida na mesma execucao
#     grava ALERTA-SWAP-REPETIDO, so informativo. Nunca swapoff/swapon (risco de OOM: ~6,4 GB nas tres JVMs em 8 GB).
#  3. Progresso: orquestrador.log parado por mais de <limiar> s grava ALERTA-PROGRESSO (sem acao automatica).
#  4. Fim (ORQ_RC= no painel): "FIM exit=0 motivo=fase <FASE> concluida" grava FIM-<FASE>-OK; outro fim, ALERTA-FIM.
#  5. Piloto embutido (B-40 secao 2; avaliacao): com o 8o argumento N, cria <SAIDA>/PARAR assim que o veredito da
#     i = N-2 aparece no log; o orquestrador para antes da i = N (exit 0, motivo "parada pedida pelo arquivo PARAR"), e
#     o supervisor grava PILOTO-<FASE>-PARADO em vez de ALERTA-FIM.
# Janela, pasta de eventos e pasta volatil: SUP_JANELA, SUP_INFRA e SUP_VOLATIL (padrao cs3:orq, ~/cs3-infra e
# /run/tccdiag/coord-cs3, os do X-17/X-18). O pedido de rede vale para F2, F3 e F4 (W-6c: GO de rede antes da 1a F3).
# Nunca usa pkill; nao mata sessao tmux; progresso na pasta volatil (tmpfs); eventos na pasta de eventos (disco).
set -u
SAIDA=$1; FASE=$2; TSV=$3; CMD=$4; LIMIAR=$5; MODO=$6; BUNDLE=$7; PARAR_ANTES=${8:-}
case "$MODO" in registrar|recriar) ;; *) echo "uso: modo do swap deve ser registrar ou recriar" >&2; exit 2 ;; esac
J=${SUP_JANELA:-cs3:orq}; I=${SUP_INFRA:-$HOME/cs3-infra}; V=${SUP_VOLATIL:-/run/tccdiag/coord-cs3}; LIM=1048576
case "$PARAR_ANTES" in ''|[1-9]|[1-9][0-9]*) ;; *) echo "uso: <parar antes de i> deve ser inteiro >= 2" >&2; exit 2 ;; esac
L=$SAIDA/$FASE/orquestrador.log
TS=$(date -u +%Y%m%dT%H%M%SZ); EV=$I/supervisor-$FASE-$TS.log; PR=$V/progresso-$FASE-$TS.log
mkdir -p "$I" "$V" 2>/dev/null || true
ev() { echo "$(date -u +%FT%TZ) $*" | tee -a "$EV"; }
swap_kb() { awk '/^SwapTotal:/{t=$2} /^SwapFree:/{f=$2} END{print t-f}' /proc/meminfo; }
mem_kb() { awk '/^MemAvailable:/{print $2}' /proc/meminfo; }
REDE="|$(awk -F'\t' -v f="$FASE" 'NR>1 && ($7=="F2"||$7=="F3"||$7=="F4") && (f=="reposicoes"||$2==f) {printf "%s|", $1}' "$TSV")"
ev "inicio saida=$SAIDA fase=$FASE tsv=$TSV cmd=$CMD limiar=${LIMIAR}s swap_lim_kB=$LIM modo_swap=$MODO bundle=$BUNDLE janela=$J parar_antes=${PARAR_ANTES:--} rede=$REDE"
parar_feito=0
feito="|"; k1="|"; swap_i=""; ult_swap_i=""; seguidas=0; t0=$(date +%s); ult=0; alertado=0

relancar() {
  tmux send-keys -t "$J" clear Enter; sleep 1; tmux clear-history -t "$J"
  tmux send-keys -t "$J" "bash $CMD" Enter
  feito="|"; k1="|"; swap_i=""; t0=$(date +%s); alertado=0
  ev "orquestrador relancado: bash $CMD"
}

tratar_swap() {  # orquestrador ja saiu com 130 pelo Ctrl-C do swap: diagnostico, recriacao (C-10), relancamento
  local f d p q
  f=$I/memoria-swap-$FASE-i$swap_i-$(date -u +%Y%m%dT%H%M%SZ).txt
  { date -Is; free -m; swapon --show; cat /proc/pressure/memory
    for p in /proc/[0-9]*; do
      q=$(awk '/^VmSwap:/{print $2}' "$p/status" 2>/dev/null)
      [ -n "$q" ] && [ "$q" -gt 0 ] && echo "$q kB pid=${p#/proc/} $(tr '\0' ' ' < "$p/cmdline" 2>/dev/null | cut -c1-150)"
    done | sort -rn | head -15
  } > "$f" 2>&1
  if [ "$swap_i" = "$ult_swap_i" ]; then seguidas=$((seguidas + 1)); else seguidas=1; ult_swap_i=$swap_i; fi
  if [ "$seguidas" -ge 3 ]; then
    ev "parada por swap numero $seguidas seguida na i=$swap_i (informativo; o supervisor segue)"; touch "$I/ALERTA-SWAP-REPETIDO-$FASE-i$swap_i"
  fi
  d=$I/recriacao-swap-$FASE-i$swap_i-$(date -u +%Y%m%dT%H%M%SZ)
  flock -n /var/lock/tcc-vm.lock bash "$I/recriar-cs3.sh" "$BUNDLE" "$d" > /dev/null 2>&1
  if ! tail -n 1 "$d/conferencia.txt" 2>/dev/null | grep -q '^RECRIAR-CS3 OK$'; then
    ev "recriacao depois do swap da i=$swap_i falhou ($d): supervisor para"; touch "$I/ALERTA-RECRIACAO-$FASE"; return 1
  fi
  ev "cluster recriado depois do swap da i=$swap_i (C-10): swap agora $(swap_kb) kB; recriacao em $d; diagnostico em $f"
  relancar
  return 0
}

for t in $(seq 1 200000); do
  p=$(tmux capture-pane -p -S -40 -t "$J" 2>/dev/null) || { ev "janela $J sumiu: supervisor para"; touch "$I/ALERTA-SUPERVISOR-$FASE"; break; }
  # 1) GO
  g=$(printf '%s\n' "$p" | grep -oE '^Digite exatamente: go [a-z]+ [0-9-]+$' | tail -1 | sed 's/^Digite exatamente: //')
  if [ -n "$g" ] && ! printf '%s' "$feito" | grep -qF "|$g|"; then
    feito="$feito$g|"
    if printf '%s' "$g" | grep -qE "^go $FASE [0-9]{4}-[0-9]{2}-[0-9]{2}\$" \
       || { printf '%s' "$g" | grep -qE '^go rede [0-9]+$' && printf '%s' "$REDE" | grep -qF "|${g#go rede }|"; }; then
      tmux send-keys -t "$J" "$g" Enter; ev "GO enviado: $g"
    else
      ev "GO NAO previsto, nao enviado: $g"; touch "$I/ALERTA-GO-$FASE"
    fi
  fi
  linha=$(tail -n 1 "$L" 2>/dev/null)
  # 2) swap no portao K1
  k=$(printf '%s' "$linha" | sed -n 's/.*run-campanha-s3\.sh: exec i=\([0-9]*\): portao K1$/\1/p')
  if [ -n "$k" ] && [ -z "$swap_i" ]; then
    s=$(swap_kb)
    printf '%s' "$k1" | grep -qF "|$k|" || { k1="$k1$k|"; ev "K1 i=$k swap_usado_kB=$s"; }
    if [ "$s" -ge "$LIM" ] && [ "$MODO" = recriar ]; then
      tmux send-keys -t "$J" C-c; swap_i=$k
      ev "SWAP $s kB >= $LIM kB no K1 da i=$k: Ctrl-C para recriar o cluster (C-10; DT-014 item 1)"; touch "$I/SWAP-$FASE-i$k-$(date -u +%H%M%S)"
    elif [ "$s" -ge "$LIM" ] && ! printf '%s' "$k1" | grep -qF "|$k:alto|"; then
      k1="$k1$k:alto|"; ev "SWAP $s kB >= $LIM kB no K1 da i=$k: o orquestrador (W-6c, --swap-limite-kb) recria o cluster"
    fi
  fi
  # 2b) piloto embutido: PARAR quando o veredito da i = PARAR_ANTES-2 aparecer (a i seguinte roda; a fase para antes)
  if [ -n "$PARAR_ANTES" ] && [ "$parar_feito" = 0 ] && [ -f "$L" ] \
     && grep -qE "run-campanha-s3\.sh: exec i=$((PARAR_ANTES - 2)): (ACEITA|RECUSADA|DESCARTADA-INFRA|PULADA)" "$L"; then
    touch "$SAIDA/PARAR"; parar_feito=1
    ev "PARAR criado (piloto embutido, B-40 secao 2): a fase para antes da i=$PARAR_ANTES"
  fi
  # 3) fim do orquestrador
  if printf '%s\n' "$p" | grep -q '^ORQ_RC='; then
    rc=$(printf '%s\n' "$p" | grep '^ORQ_RC=' | tail -1)
    if [ -f "$L" ] && [ "$(stat -c %Y "$L")" -ge "$t0" ]; then fim=$(tail -n 1 "$L"); else fim="(sem linha FIM nova: pre-voo; ver o painel)"; fi
    ev "fim do orquestrador: $rc | $fim"
    if printf '%s' "$fim" | grep -qE "FIM exit=0 motivo=fase $FASE concluida\$"; then touch "$I/FIM-$FASE-OK"; break; fi
    if [ "$parar_feito" = 1 ] && printf '%s' "$fim" | grep -q 'FIM exit=0 motivo=parada pedida pelo arquivo PARAR'; then
      touch "$I/PILOTO-$FASE-PARADO"; ev "piloto embutido: fase parada pelo PARAR, como previsto"; break
    fi
    if [ -n "$swap_i" ] && printf '%s' "$fim" | grep -q 'FIM exit=130 '; then tratar_swap && continue; break; fi
    printf '%s\n%s\n' "$rc" "$fim" > "$I/ALERTA-FIM-$FASE"; break
  fi
  # 4) progresso, a cada 60 s
  agora=$(date +%s)
  if [ $((agora - ult)) -ge 60 ]; then
    ult=$agora
    # idade desde a ultima linha do log ou desde o (re)lancamento, o que for mais recente (retomada com log antigo)
    m=$t0; if [ -f "$L" ] && [ "$(stat -c %Y "$L")" -gt "$t0" ]; then m=$(stat -c %Y "$L"); fi; idade=$((agora - m))
    cont=$(cat "$SAIDA"/exec-*/checagem.json 2>/dev/null | grep -o '"veredito": "[A-Z-]*"' | sort | uniq -c | tr -s ' ' | tr '\n' ';')
    passo=$(grep -o '"passo": "[^"]*"' "$SAIDA/$FASE/progresso.json" 2>/dev/null)
    echo "$(date -u +%FT%TZ) idade_log=${idade}s ${passo} swap_kB=$(swap_kb) memdisp_kB=$(mem_kb) livre_MiB=$(df -Pm "$SAIDA" | awk 'NR==2{print $4}') timers=$(systemctl list-timers --all --no-legend 'tcc-heal*' | grep -c .) vereditos=[${cont}] ultima=$(printf '%s' "$linha" | cut -c1-160)" >> "$PR"
    if [ "$idade" -gt "$LIMIAR" ] && [ "$alertado" = 0 ]; then ev "PROGRESSO parado ha ${idade}s (> ${LIMIAR}s): $linha"; touch "$I/ALERTA-PROGRESSO-$FASE"; alertado=1; fi
    [ "$idade" -le "$LIMIAR" ] && alertado=0
  fi
  sleep 3
done
cp -p "$PR" "$I/" 2>/dev/null
ev "supervisor sai"
```

### 10.3 `av-conferir.py`

<!-- arquivo: av-conferir.py -->
```python
#!/usr/bin/env python3
"""Conferencias do coordenador na avaliacao (B-40), roteiro docs/roteiro-avaliacao-b40-2026-11-06.md. So biblioteca
padrao; roda com python3 -I. Nunca le V, L, vazao nem convergencia (o piloto embutido do B-40 secao 2 so olha o
protocolo); so leitura dos brutos, grava so o --tsv.

  execucoes <SAIDA> --tsv <arq>
      Uma linha por execucao terminada: i, bloco, celula, braco, veredito, motivos, avisos, tempos (t_pre, duracao,
      volume), swap no K1 e recriacao (C-10), injetor (exit, alvo parado, fim nominal e saida: N1), AC-5 da linha de
      fim e religamento, trilhas (fim, linhas_perdidas, trilha_incompleta: B-7), marcadores, h4 e MANIFEST. No fim, por
      celula, os blocos completos (A e B ACEITA), perdidos (algum braco nao ACEITA) e incompletos (falta braco).
  piloto <SAIDA> --ate-i N [--desde-i M]
      Checklist de protocolo das execucoes i = M..N (padrao M = 0; o piloto embutido sao os 3 primeiros blocos da
      fase nucleo: M = 0, N = 5; a conferencia do W-6c na fase extra: M = 56, N = 61). Uma linha OK/FALHA por item e
      "PILOTO: PROTOCOLO OK" ou "PILOTO: VER COM O SENIOR (<n> itens)". Exit 0 ou 1.
"""
import argparse
import json
import math
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path

LOG_RE = re.compile(r"^\[(\S+)\] run-campanha-s3\.sh: (.*)$")
FALHA_NO = {"F1": "isn2", "F2": "isn3", "F3": "isn2", "F4": "isn3"}
ALVO_NO = {"127.0.0.1:11222": "isn1", "127.0.0.1:11223": "isn2", "127.0.0.1:11224": "isn3"}


def ler(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def jsonl(p):
    p = Path(p)
    if not p.is_file():
        return []
    out = []
    for l in p.read_text(encoding="utf-8", errors="replace").splitlines():
        try:
            out.append(json.loads(l))
        except ValueError:
            out.append({"tipo": "ILEGIVEL"})
    return out


def fim_trilha(linhas):
    if not linhas or linhas[-1].get("tipo") != "fim":
        return "nao", "-"
    return "sim", str(linhas[-1].get("linhas_perdidas", "-"))


def iso_ms(s):
    return int(round((datetime.strptime(s, "%Y-%m-%dT%H:%M:%S.%fZ") - datetime(1970, 1, 1)).total_seconds() * 1000))


def tempos_log(saida):
    ini, fim = {}, {}
    for f in sorted(Path(saida).glob("*/orquestrador.log")):
        for l in f.read_text(encoding="utf-8", errors="replace").splitlines():
            m = LOG_RE.match(l)
            if not m:
                continue
            t, txt = iso_ms(m.group(1)), m.group(2)
            a = re.match(r"exec i=(\d+) \S+ braco=\S+ .*recriando o cluster", txt)
            if a:
                ini.setdefault(int(a.group(1)), t)
            b = re.match(r"exec i=(\d+): (ACEITA|RECUSADA|DESCARTADA-INFRA|PULADA)", txt)
            if b:
                fim[int(b.group(1))] = t
    return ini, fim


def volume_mib(d):
    tot = 0
    for raiz, _d, arqs in os.walk(d):
        for a in arqs:
            st = os.lstat(os.path.join(raiz, a))
            tot += max(st.st_size, getattr(st, "st_blocks", 0) * 512)
    return math.ceil(tot / 1048576)


def manifest_ok(d):
    if not (d / "MANIFEST.sha256").is_file():
        return "sem"
    r = subprocess.run(["sha256sum", "-c", "--quiet", "MANIFEST.sha256"], cwd=str(d), capture_output=True, text=True)
    return "ok" if r.returncode == 0 else "DIVERGE"


def uma(d, ini, fim):
    """Dados de protocolo de uma execucao terminada (sem V, L, vazao nem convergencia)."""
    ch = ler(d / "checagem.json")
    ex = ler(d / "execucao.json") if (d / "execucao.json").is_file() else {}
    f1, l1 = fim_trilha(jsonl(d / "decisoes-rep-001.jsonl"))
    f2, l2 = fim_trilha(jsonl(d / "observaveis-rep-001.jsonl"))
    mk = (d / "marcadores.txt").read_text(encoding="utf-8").splitlines() if (d / "marcadores.txt").is_file() else []
    h4 = ""
    if (d / "h4.txt").is_file():
        m = re.search(r"^VEREDITO \d+ (\S+)$", (d / "h4.txt").read_text(encoding="utf-8", errors="replace"), re.M)
        h4 = m.group(1) if m else "?"
    i = ch["i"]
    t_pre = None
    if ex.get("ancora_ms") and ex.get("lancamento_ms"):
        t_pre = (ex["ancora_ms"] - ex["lancamento_ms"]) / 1000.0
    dur = (fim[i] - ini[i]) / 1000.0 if i in ini and i in fim else None
    sp = ex.get("swap_portao") or {}
    rel = ex.get("religamento") or {}
    return {"i": i, "dir": d.name, "bloco": ch.get("bloco"), "celula": ch.get("celula"), "braco": ch.get("braco"),
            "veredito": ch["veredito"], "motivos": ch["motivos"], "avisos": ch["avisos"],
            "falha": ex.get("falha"), "t_pre_s": t_pre, "duracao_s": dur, "volume_mib": volume_mib(d),
            "swap_kb_k1": ex.get("swap_usado_kb_k1"), "swap_recriado": sp.get("recriado"),
            "inj_offset_s": ex.get("inj_inicio_offset_s"), "injetor_exit": ex.get("injetor_exit"),
            "injetor_alvo_parado": ex.get("injetor_alvo_parado"),
            "fim_nominal_s": ex.get("fim_nominal_desde_ancora_s"),
            "injetor_saida_s": ex.get("injetor_saida_desde_ancora_s"),
            "deadman_armado": (ex.get("deadman") or {}).get("armado"),
            "residuo": (ex.get("rede_pos") or {}).get("residuo"),
            "camada3_ok": (ex.get("camada3_pre") or {}).get("divergencias", 1) is None
            and (ex.get("camada3_pos") or {}).get("divergencias", 1) is None,
            "ac5_alvo": (ex.get("sinal_fim_carga") or {}).get("ac5_alvo"),
            "religamento_rc": rel.get("podman_start_rc"), "religamento_no": rel.get("no"),
            "trilha_incompleta": ex.get("trilha_incompleta"), "decisoes": (f1, l1), "observaveis": (f2, l2),
            "marc_start": sum(1 for l in mk if "event=start" in l), "marc_end": sum(1 for l in mk if "event=end" in l),
            "h4": h4, "manifest": manifest_ok(d), "workload_exit": ex.get("workload_exit"),
            "teto": ex.get("teto_estourado")}


def execucoes(saida):
    ini, fim = tempos_log(saida)
    out = []
    for d in sorted(Path(saida).glob("exec-*")):
        if ".abortada-" in d.name or not (d / "checagem.json").is_file():
            continue
        out.append(uma(d, ini, fim))
    return sorted(out, key=lambda x: x["i"])


def cmd_execucoes(a):
    xs = execucoes(a.saida)
    cols = ["i", "bloco", "celula", "braco", "veredito", "n_motivos", "n_avisos", "t_pre_s", "duracao_s", "volume_mib",
            "swap_kb_k1", "swap_recriado", "injetor_exit", "injetor_alvo_parado", "fim_nominal_s", "injetor_saida_s",
            "ac5_alvo", "religamento_rc", "trilha_incompleta", "decisoes", "observaveis", "marcadores", "h4",
            "manifest", "motivos_e_avisos"]
    linhas = ["\t".join(cols)]
    for x in xs:
        v = [x["i"], x["bloco"], x["celula"], x["braco"], x["veredito"], len(x["motivos"]), len(x["avisos"]),
             x["t_pre_s"], x["duracao_s"], x["volume_mib"], x["swap_kb_k1"], x["swap_recriado"], x["injetor_exit"],
             x["injetor_alvo_parado"], x["fim_nominal_s"], x["injetor_saida_s"], x["ac5_alvo"], x["religamento_rc"],
             x["trilha_incompleta"], "/".join(x["decisoes"]), "/".join(x["observaveis"]),
             "%d/%d" % (x["marc_start"], x["marc_end"]), x["h4"], x["manifest"],
             " | ".join(x["motivos"] + ["aviso: " + y for y in x["avisos"]]).replace("\t", " ")]
        linhas.append("\t".join("-" if y is None else str(y) for y in v))
    Path(a.tsv).write_text("\n".join(linhas) + "\n", encoding="utf-8")
    for l in linhas:
        print(l[:220])
    blocos = {}
    for x in xs:
        if x["bloco"] in (None, "-"):
            continue
        blocos.setdefault(x["celula"], {}).setdefault(str(x["bloco"]), {})[x["braco"]] = x["veredito"]
    for cel in sorted(blocos):
        comp = sorted(int(b) for b, br in blocos[cel].items() if br.get("A") == "ACEITA" and br.get("B") == "ACEITA")
        perd = sorted(int(b) for b, br in blocos[cel].items()
                      if any(v not in ("ACEITA",) for v in br.values()) and set(br) >= {"A", "B"})
        inc = sorted(int(b) for b, br in blocos[cel].items() if set(br) != {"A", "B"})
        print("BLOCOS %s completos=%d perdidos=%s incompletos=%s" % (cel, len(comp), perd or "-", inc or "-"))
    return 0


def cmd_piloto(a):
    xs = [x for x in execucoes(a.saida) if a.desde_i <= x["i"] <= a.ate_i]
    falhas = []

    def c(nome, ok, det=None):
        print("%s %s%s" % ("OK   " if ok else "FALHA", nome, "" if det is None else ": %s" % (det,)))
        if not ok:
            falhas.append(nome)

    c("execucoes terminadas de i = %d a %d" % (a.desde_i, a.ate_i),
      [x["i"] for x in xs] == list(range(a.desde_i, a.ate_i + 1)), [x["i"] for x in xs])
    for x in xs:
        p = "i=%d %s %s bloco %s: " % (x["i"], x["celula"], x["braco"], x["bloco"])
        c(p + "veredito ACEITA", x["veredito"] == "ACEITA", "%s %s" % (x["veredito"], "; ".join(x["motivos"])))
        if x["veredito"] == "PULADA":
            continue
        c(p + "carga com exit 0, sem teto, ancora ate 120 s", x["workload_exit"] == 0 and x["teto"] is False
          and x["t_pre_s"] is not None and x["t_pre_s"] <= 120, (x["workload_exit"], x["teto"], x["t_pre_s"]))
        c(p + "camada 3 sem divergencia", x["camada3_ok"])
        c(p + "trilhas de decisoes e observaveis com fim e linhas_perdidas 0 (B-7)",
          x["decisoes"] == ("sim", "0") and x["observaveis"] == ("sim", "0"), (x["decisoes"], x["observaveis"]))
        c(p + "h4 ok", x["h4"] == "ok", x["h4"])
        c(p + "MANIFEST da execucao confere", x["manifest"] == "ok", x["manifest"])
        if x["falha"] in ("F2", "F3", "F4"):
            c(p + "falha disparada em [49, 51) s, dead-man armado, sem resto de rede",
              x["inj_offset_s"] is not None and 49 <= float(x["inj_offset_s"]) < 51 and x["deadman_armado"] is True
              and x["residuo"] is False, (x["inj_offset_s"], x["deadman_armado"], x["residuo"]))
        if x["falha"] in ("F1", "F2", "F3", "F4"):
            c(p + "marcadores: um start e um end", (x["marc_start"], x["marc_end"]) == (1, 1),
              (x["marc_start"], x["marc_end"]))
        if x["ac5_alvo"] not in (None, "-"):
            no = ALVO_NO.get(x["ac5_alvo"])
            c(p + "AC-5 so no no da falha, com religamento rc 0", x["braco"] == "B" and no == FALHA_NO.get(x["falha"])
              and x["religamento_rc"] == 0, (x["ac5_alvo"], x["falha"], x["religamento_rc"]))
    if falhas:
        print("PILOTO: VER COM O SENIOR (%d itens)" % len(falhas))
        return 1
    print("PILOTO: PROTOCOLO OK (os 3 blocos contam; a fase segue)")
    return 0


def main(argv):
    ap = argparse.ArgumentParser(prog="av-conferir.py")
    sub = ap.add_subparsers(dest="cmd")
    e = sub.add_parser("execucoes")
    e.add_argument("saida")
    e.add_argument("--tsv", required=True)
    p = sub.add_parser("piloto")
    p.add_argument("saida")
    p.add_argument("--ate-i", type=int, required=True)
    p.add_argument("--desde-i", type=int, default=0)
    try:
        a = ap.parse_args(argv)
    except SystemExit as x:
        return 2 if x.code else 0
    if a.cmd == "execucoes":
        return cmd_execucoes(a)
    if a.cmd == "piloto":
        return cmd_piloto(a)
    ap.print_usage(sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
```

### 10.4 `rc-param.py`

<!-- arquivo: rc-param.py -->
```python
#!/usr/bin/env python3
"""Arquivo de parametros da recalibracao (X-19, benchmark refeito) a partir do arquivo final da calibracao X-18.

Roteiro docs/roteiro-recalibracao-2026-11-06.md, secao 2.2. So biblioteca padrao; roda com python3 -I.

Uso:
  python3 -I rc-param.py --base <campanha-s3-calibracao.json> --saida <json que ainda nao existe>
      [--late-max N --late-max-origem TEXTO]

Copia os 19 numeros da base (esquema campanha-s3/1, os do X-18: C-6 e C-7 conferidos no roteiro pelas medidas da
p2-d) e acrescenta os cinco campos s3_* da carga L+ com os valores da condicao d da passagem 2 do piloto de 06/11
(runs/p2-d-java.log): s3_users 40, s3_think_ms 500, s3_session_median_sec 10, s3_logout_prob 0.9, s3_admin_mean_sec 5.
Com --late-max, acrescenta s3_replay_late_max (pre-registro do benchmark refeito, P-3), que so entra se a ponta do
orquestrador aceitar o campo (o campanha_s3_conferir.py recusa campo desconhecido). Nenhum valor e escolhido aqui.
Exit 0 ok, 2 uso, 3 recusa. Nunca sobrescreve.
"""
import argparse
import json
import sys
from pathlib import Path

ORIGEM_L = ("selecao do ponto de operacao pela regra 6.5 do piloto de 06/11 (condicao d, passagem 2: "
            "runs/p2-d-java.log, %s)")
CARGA_L = (("s3_users", 40, "--s3-users 40"),
           ("s3_think_ms", 500, "--s3-think-ms 500"),
           ("s3_session_median_sec", 10, "--s3-session-median-sec 10"),
           ("s3_logout_prob", 0.9, "--s3-logout-prob 0.9"),
           ("s3_admin_mean_sec", 5, "--s3-admin-mean-sec 5"))


def main(argv):
    ap = argparse.ArgumentParser(prog="rc-param.py")
    ap.add_argument("--base", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--late-max", type=int)
    ap.add_argument("--late-max-origem")
    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return 2 if e.code else 0
    if (a.late_max is None) != (a.late_max_origem is None):
        print("Erro: --late-max e --late-max-origem vao juntos", file=sys.stderr)
        return 2
    saida = Path(a.saida)
    if saida.exists():
        print("Recusa: %s ja existe (nunca sobrescreve)" % saida, file=sys.stderr)
        return 3
    base = json.loads(Path(a.base).read_text(encoding="utf-8"))
    if base.get("esquema") != "campanha-s3/1" or not isinstance(base.get("parametros"), dict):
        print("Recusa: a base nao e campanha-s3/1", file=sys.stderr)
        return 3
    ps = base["parametros"]
    ja = [k for k, _v, _o in CARGA_L if k in ps] + (["s3_replay_late_max"] if "s3_replay_late_max" in ps else [])
    if ja:
        print("Recusa: a base ja tem %s" % ", ".join(ja), file=sys.stderr)
        return 3
    for nome, valor, opcao in CARGA_L:
        ps[nome] = {"valor": valor, "origem": ORIGEM_L % opcao}
    if a.late_max is not None:
        if a.late_max < 0 or not a.late_max_origem.strip():
            print("Erro: --late-max >= 0 e --late-max-origem nao vazio", file=sys.stderr)
            return 2
        ps["s3_replay_late_max"] = {"valor": a.late_max, "origem": a.late_max_origem}
    base["versao"] = "recalibracao X-19 (benchmark refeito): base X-18 com a carga L+ da p2-d%s" % (
        "" if a.late_max is None else " e s3_replay_late_max")
    dados = (json.dumps(base, indent=1, ensure_ascii=True) + "\n").encode("ascii")
    saida.write_bytes(dados)
    import hashlib
    print("%s  %s" % (hashlib.sha256(dados).hexdigest(), saida))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
```

---

## Análise do Senior (formato da frente prática)

### Contexto identificado no TCC
A calibração X-18 foi cega para violações (DT-026 §1). A recalibração repete as 26 células com a carga L+ do ponto de
operação do piloto e com os instrumentos que tornam as violações observáveis, para produzir a nova parte 2 do benchmark
refeito.

### Requisitos práticos identificados
Bundle da tag do benchmark; L+ por `s3_*`; `--sonda-replay` e late-max 268; sementes 700 000; tetos conferidos com a
p2-d; supervisor `registrar`; entrega ao W-9.

### Planejamento proposto
Seções 2 a 6.

### Branches sugeridas
Nenhuma de código. Anexo em `docs/plano-implementacao-controlador`.

### Critérios de aceite
4.4 (linha do programa e protocolo), 6.1 e 6.2.

### Pontos que precisam de validação do gestor
D-1 (seção 9): sem a passagem do late-max pela tag, a recalibração não roda.

### Próxima tarefa sugerida para o Worker
Integrador: passar `s3_replay_late_max` do arquivo de parâmetros ao programa (D-1), com teste, antes da tag.
