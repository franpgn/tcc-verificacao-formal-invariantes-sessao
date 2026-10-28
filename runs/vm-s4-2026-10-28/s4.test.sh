#!/usr/bin/env bash
# runs/vm-s4-2026-10-28/s4.test.sh -- teste SEM VM do involucro s4.sh, com shims (sudo/podman/flock/uname/tmux/systemctl/java)
# e stubs de recriar.sh, portao51.sh, g1.sh e g1b.sh numa arvore falsa $HOME/tcc-gate (git proprio).
# Prova: ordem pre -> copia -> RECRIAR -> recriacao -> portao pre -> g1b (janela tmux) -> g1 -> g1b rc -> portao pos;
# ambiente passado ao g1 (REP=4 G1_DUR=90 G1_LOG_SAUDE_S=2 G1_LOG_POS_S=30) e ao g1b (REP=4 COLETA=minima TCC_DIR);
# o GO chega ao g1 so pelo stdin do Gestor (o s4.sh nao o digita); cada pre-condicao que falha aborta ANTES do down;
# portao ruim aborta antes do g1; g1 sem GO encerra o g1b e nao roda o portao pos; t_merge > 60 s sai com rc 5;
# avaliar_portao aceita a saida real do portao do S3 (00-portao51-pre-console.txt) e recusa cada criterio violado;
# a tabela de hashes embutida e igual aos arquivos deste repositorio.
# NAO prova: podman, tmux, flock, Infinispan ou os scripts reais (estes tem testes proprios). NAO toca na VM nem em rede.
# Rodar:  bash runs/vm-s4-2026-10-28/s4.test.sh      Exit 0 se tudo passar. Leva ~1-2 min.
set -uo pipefail
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$AQUI/../.." && pwd)"
S4="$AQUI/s4.sh"
P=0; F=0
ok()     { if [ "$2" = "$3" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (esperado '$2', obtido '$3')"; F=$((F+1)); fi; }
contem() { if printf '%s' "$3" | grep -qF -- "$2"; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 (sem '$2')"; F=$((F+1)); fi; }
nao()    { if printf '%s' "$3" | grep -qF -- "$2"; then echo "FAIL: $1 (com '$2')"; F=$((F+1)); else echo "PASS: $1"; P=$((P+1)); fi; }
antes()  { # antes <desc> <arquivo> <a> <b>: primeira linha com a vem antes da primeira com b
  local la lb; la=$(grep -nF -m1 -- "$3" "$2" | cut -d: -f1); lb=$(grep -nF -m1 -- "$4" "$2" | cut -d: -f1)
  if [ -n "$la" ] && [ -n "$lb" ] && [ "$la" -lt "$lb" ]; then echo "PASS: $1"; P=$((P+1)); else echo "FAIL: $1 ('$3'@${la:-?} '$4'@${lb:-?})"; F=$((F+1)); fi; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
BIN="$T/bin"; mkdir -p "$BIN"
FIXTURE="$T/portao-ok.txt"
sed -n '/^amostras=/,/^fim /p' "$REPO/runs/vm-s3-2026-10-28/00-portao51-pre-console.txt" > "$FIXTURE"
BOOT=b7df4b03-6652-4808-9617-11096693e65b; echo "$BOOT" > "$T/boot_id"

# ---------------- shims
cat > "$BIN/sudo" <<'EOF'
#!/usr/bin/env bash
echo "sudo $*" >> "$SHIM_LOG"
[ "$1" = -n ] && { [ -z "${SHIM_SUDO_FALHA:-}" ]; exit $?; }
case "$1" in podman) shift; exec podman "$@";; nsenter) echo "qdisc noqueue 0: root refcnt 2"; exit 0;; esac
echo "shim sudo: inesperado: $*" >&2; exit 99
EOF
cat > "$BIN/podman" <<'EOF'
#!/usr/bin/env bash
echo "podman $*" >> "$SHIM_LOG"
case "$1" in
  ps) for n in isn1 isn2 isn3; do echo "$n abc Up 10 hours (healthy) criado=2026-10-27 18:48:5x"; done;;
  logs) echo "2026-10-27 18:49:06,181 INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn2-7049|2] (3) [isn2-7049, isn1-64005, isn3-27776]";;
  inspect) case "$*" in *StartedAt*) echo "2026-10-28 06:00:00.000000000 +0000 UTC";; *Pid*) echo 4003;; *Id*) echo 7a196f163609aaaa;;
                        *Created*) echo "2026-10-28 06:00:00.0 +0000 UTC";; *) echo '[{"Name":"x"}]';; esac;;
esac
exit 0
EOF
cat > "$BIN/flock" <<'EOF'
#!/usr/bin/env bash
echo "flock $*" >> "$SHIM_LOG"
[ "${SHIM_LOCK:-ocupado}" = livre ]
EOF
cat > "$BIN/uname" <<'EOF'
#!/usr/bin/env bash
echo "${SHIM_KERNEL:-6.8.0-147-generic}"
EOF
cat > "$BIN/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SHIM_LOG"
[ -z "${SHIM_TIMERS:-}" ] || echo "Wed 2026-10-28 n/a tcc-heal-1-2.timer tcc-heal-1-2.service"
exit 0
EOF
cat > "$BIN/java" <<'EOF'
#!/usr/bin/env bash
echo 'openjdk version "17.0.16" 2025-07-15' >&2
EOF
# tmux new-window -d -P -F fmt -n nome "cmd": roda cmd em segundo plano (herda o ambiente) e imprime o id; kill-window marca e mata
cat > "$BIN/tmux" <<'EOF'
#!/usr/bin/env bash
echo "tmux $*" >> "$SHIM_LOG"
case "$1" in
  new-window) cmd="${@: -1}"; bash -c "$cmd" > /dev/null 2>&1 < /dev/null & echo "$!" > "$SHIM_STATE/tmux.pid"; echo "@7";;
  kill-window) : > "$SHIM_STATE/killed"; kill "$(cat "$SHIM_STATE/tmux.pid")" 2>/dev/null; true;;
esac
EOF
chmod +x "$BIN"/*

# ---------------- arvore falsa (stubs nos caminhos reais) e tabela de hashes dos stubs
nova_arvore() {
  rm -rf "$T/home" "$T/state"; mkdir -p "$T/state"; : > "$T/shim.log"
  G="$T/home/tcc-gate"; mkdir -p "$G/scripts" "$G/runs/gate-b29" "$G/runs/vm-s2-2026-10-27" "$G/runs/vm-s4-2026-10-28" "$G/workload/target"
  cp "$S4" "$G/runs/vm-s4-2026-10-28/s4.sh"
  echo jar > "$G/workload/target/session-workload-1.0.jar"
  echo "# stub" > "$G/scripts/inject-partition.sh"; echo "# stub" > "$G/runs/gate-b29/g-lib.sh"; echo "# stub" > "$G/runs/gate-b29/g56-lib.sh"
  echo "go antigo" > "$G/runs/gate-b29/go.log"
  cat > "$G/runs/gate-b29/recriar.sh" <<'EOF'
D=$1; echo "RECRIAR $D cwd=$PWD" >> "$SHIM_LOG"; mkdir -p "$D"; echo "t0_up_epoch=$(date +%s)" > "$D/validacao.txt"
echo "down rc=0"; echo "up rc=0"
if [ -n "${S4T_REC_FALHA:-}" ]; then echo "FALHA: nao fechou em 180s"; else echo "ESTAVEL size=3 HEALTHY: primeira leitura boa +14s, estavel por 21s (total 35s)"; fi
EOF
  cat > "$G/runs/vm-s2-2026-10-27/portao51.sh" <<'EOF'
echo "PORTAO dur=$1 csv=$2" >> "$SHIM_LOG"; : > "$2"
if [ -n "${S4T_PORTAO_RUIM:-}" ]; then sed 's/amostras_ruins=0/amostras_ruins=1/' "$S4T_FIXTURE"; else cat "$S4T_FIXTURE"; fi
EOF
  cat > "$G/runs/gate-b29/g1.sh" <<'EOF'
echo "G1 REP=${REP:-} G1_DUR=${G1_DUR:-} G1_LOG_SAUDE_S=${G1_LOG_SAUDE_S:-} G1_LOG_POS_S=${G1_LOG_POS_S:-} COLETA=${COLETA:-} TCC_DIR=${TCC_DIR:-} cwd=$PWD" >> "$SHIM_LOG"
read -rp "G-1 (REP=$REP, janela de $G1_DUR s em isn3): go do Gestor registrado no relatorio? [digite GO] " r
echo "G1 resposta=$r" >> "$SHIM_LOG"
[ "$r" = GO ] || { echo "ABORTAR: sem go" >&2; exit 1; }
echo "go G-1 (REP=$REP, janela de $G1_DUR s em isn3) $(date -Is)" >> runs/gate-b29/go.log
O=runs/gate-b29/g1-rep$REP; mkdir -p "$O"
echo "fault=F2 node=isn3 event=start t_epoch_ns=$(date +%s)000000000" > "$O/marcadores.txt"; sleep 1
echo "fault=F2 node=isn3 event=end t_epoch_ns=$(date +%s)000000000" >> "$O/marcadores.txt"
echo "t_merge_s=${S4T_TMERGE:-46} (desde event=end)" > "$O/t_merge.txt"
echo "G-1 concluido (REP=$REP)"; exit "${S4T_G1_RC:-0}"
EOF
  cat > "$G/runs/gate-b29/g1b.sh" <<'EOF'
echo "G1B REP=${REP:-} COLETA=${COLETA:-} TCC_DIR=${TCC_DIR:-} cwd=$PWD" >> "$SHIM_LOG"
M=runs/gate-b29/g1-rep$REP/marcadores.txt
for i in $(seq 1 60); do [ -e "$SHIM_STATE/killed" ] && exit 9; grep -q event=end "$M" 2>/dev/null && break; sleep 0.5; done
grep -q event=end "$M" 2>/dev/null || exit 2
mkdir -p "runs/gate-b29/g1-rep$REP/hotrod"; echo "rep=$REP coleta=$COLETA" > "runs/gate-b29/g1-rep$REP/hotrod/resumo.txt"
echo "G1B FIM" >> "$SHIM_LOG"; exit 0
EOF
  ( cd "$G" && for f in scripts/inject-partition.sh runs/gate-b29/g-lib.sh runs/gate-b29/g56-lib.sh runs/gate-b29/g1.sh \
        runs/gate-b29/g1b.sh runs/gate-b29/recriar.sh runs/vm-s2-2026-10-27/portao51.sh; do sha256sum "$f" | sed 's/ \*/  /'; done ) > "$T/hashes"
  ( cd "$G" && git init -q && git config core.autocrlf false && git add -A && git -c user.name=t -c user.email=t@t commit -qm stub )
}
# roda o s4.sh na arvore falsa; $1 = stdin (respostas do "Gestor"); demais = VAR=valor extras
roda() {
  local entrada="$1"; shift
  SAIDA="$(printf '%s' "$entrada" | env -u TCC_DIR HOME="$T/home" PATH="$BIN:$PATH" TMUX="/tmp/tmux-1000/default,1,0" \
    SHIM_LOG="$T/shim.log" SHIM_STATE="$T/state" S4T_FIXTURE="$FIXTURE" \
    S4_TESTE=1 S4_HASHES="$T/hashes" S4_BOOT_ID_FILE="$T/boot_id" S4_PORTAO_MIN_S=1 S4_PORTAO_FOLGA_S=0 S4_G1B_ESPERA_RC_S=40 \
    "$@" bash "$T/home/tcc-gate/runs/vm-s4-2026-10-28/s4.sh" 2>&1)"; RC=$?
  OUT="$(ls -d "$T/home/tcc-gate/runs/vm-s4-2026-10-28"/vm-* 2>/dev/null | head -1)"
  sleep 0.3
}
nada_destrutivo() { # nada_destrutivo <rotulo>: nem recriacao nem g1/g1b
  ok "$1: recriar nao chamado" 0 "$(grep -c '^RECRIAR' "$T/shim.log")"
  ok "$1: g1 nao chamado" 0 "$(grep -c '^G1 ' "$T/shim.log")"
  ok "$1: g1b nao lancado" 0 "$(grep -c '^tmux new-window' "$T/shim.log")"
}

echo "== T-s4-0: arquivo e tabela de hashes =="
bash -n "$S4"; ok "T-s4-0 bash -n" 0 $?
ok "T-s4-0 nenhum CR" 0 "$(tr -cd '\r' < "$S4" | wc -c | tr -d ' ')"
ok "T-s4-0 tabela embutida = arquivos do repositorio (7 OK)" 7 "$(cd "$REPO" && bash "$S4" --hashes | sha256sum -c - 2>/dev/null | grep -c ': OK$')"
contem "T-s4-0 inject-partition = S0 (59dd2bee...94e1)" "59dd2beee5b003d85f96cae71866227d7bca1c87a9ceeab034180ce4254d94e1  scripts/inject-partition.sh" "$(bash "$S4" --hashes)"
contem "T-s4-0 g-lib = S0 (af3436d5...22dd)" "af3436d5ede54f5f2047ba48620130a803b4bbd8929e2cbc242fbbe0ebb922dd  runs/gate-b29/g-lib.sh" "$(bash "$S4" --hashes)"

echo "== T-s4-1: avaliar_portao com a saida real do portao do S3 e mutacoes =="
X=$(S4_TESTE=1 bash "$S4" --avaliar-portao "$FIXTURE"); ok "T-s4-1 fixture S3 aceita (rc)" 0 $?
contem "T-s4-1 fixture S3: OK" "PORTAO 5.1: OK (amostras_ruins=0, maior_sequencia_boa_s=131)" "$X"
mut() { # mut <rotulo> <sed>
  sed "$2" "$FIXTURE" > "$T/mut.txt"; X=$(bash "$S4" --avaliar-portao "$T/mut.txt"); local r=$?
  ok "T-s4-1 recusa: $1" 1 "$r"; contem "T-s4-1 motivo: $1" "FALHA portao" "$X"; }
mut "amostra ruim" 's/amostras_ruins=0/amostras_ruins=2/'
mut "sequencia boa < 120 s" 's/maior_sequencia_boa_s=131/maior_sequencia_boa_s=100/'
mut "cache com 1 entrada" 's/^size counters porta 11223: 0$/size counters porta 11223: 1/'
mut "timer tcc-heal" 's/timers tcc-heal: 0 units/timers tcc-heal: 1 units/'
mut "prio residual em isn3" 's/^isn3 proibidos(clsact|prio|netem|ingress)=0/isn3 proibidos(clsact|prio|netem|ingress)=1/'
mut "filtro em isn2" 's/^isn2 tc filter eth0: \[\]/isn2 tc filter eth0: [filter parent 1:]/'
mut "lock LIVRE" 's/^lock: OCUPADO/lock: LIVRE/'
mut "mudanca de vista em isn1" 's/^\(isn1 mudancas de vista nos ultimos 5 min .*\): 0$/\1): 2/'
mut "sem a linha fim" '/^fim /d'

echo "== T-s4-2: caminho completo (TCC_DIR nao definido; Gestor digita RECRIAR e GO) =="
nova_arvore; roda $'RECRIAR\nGO\n'
ok "T-s4-2 rc 0" 0 "$RC"
contem "T-s4-2 TCC_DIR exportado pelo s4.sh" "TCC_DIR=$T/home/tcc-gate (exportado pelo s4.sh)" "$SAIDA"
ok "T-s4-2 saida em runs/vm-s4-2026-10-28/vm-*" 1 "$([ -n "$OUT" ] && [ -d "$OUT" ] && echo 1 || echo 0)"
for f in 00-pre.log 01-pre-recriacao.log 02-recriacao.log 03-pos-recriacao.log 04-portao51-pre.log 04-portao51-pre.avaliacao \
         05-pre-g1.log 05-g1.log 05-g1b.log 06-pos-g1.log 07-portao51-pos.log 07-portao51-pos.avaliacao go.log g1-rep4.sha256 s4-console.log rc.txt \
         recriacao/validacao.txt pre-recriacao/inspect-isn1.json pre-recriacao/logs-isn3.txt; do
  ok "T-s4-2 gravado $f" 1 "$([ -s "$OUT/$f" ] && echo 1 || echo 0)"; done
contem "T-s4-2 hashes conferidos" "hashes: iguais a referencia" "$(cat "$OUT/00-pre.log")"
contem "T-s4-2 boot_id registrado" "boot_id: $BOOT" "$(cat "$OUT/00-pre.log")"
contem "T-s4-2 uname -r registrado" "uname -r: 6.8.0-147-generic" "$(cat "$OUT/00-pre.log")"
contem "T-s4-2 lock ocupado registrado" "OCUPADO (este flock)" "$(cat "$OUT/00-pre.log")"
contem "T-s4-2 vista antiga registrada" "isn3 ultima vista: 2026-10-27 18:49:06,181" "$(cat "$OUT/01-pre-recriacao.log")"
contem "T-s4-2 go.log antigo registrado" "go antigo" "$(cat "$OUT/00-pre.log")"
ok "T-s4-2 rc.txt na ordem (pre, copia, recriacao, portao pre, g1, g1b, portao pos)" \
  "pre copia-cluster-antigo recriacao pos-recriacao 04-portao51-pre 04-portao51-pre-avaliacao g1 g1b 07-portao51-pos 07-portao51-pos-avaliacao" \
  "$(grep -oE '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | sed 's/ rc=//' | paste -sd' ')"
ok "T-s4-2 todos os passos com rc=0" 0 "$(grep -E '^[a-z0-9-]+ rc=' "$OUT/rc.txt" | grep -vc ' rc=0 ')"
contem "T-s4-2 recriar com o diretorio de saida, a partir de ~/tcc-gate" "RECRIAR $OUT/recriacao cwd=$T/home/tcc-gate" "$(cat "$T/shim.log")"
ok "T-s4-2 recriar uma vez" 1 "$(grep -c '^RECRIAR' "$T/shim.log")"
ok "T-s4-2 portao duas vezes (pre e pos)" 2 "$(grep -c '^PORTAO' "$T/shim.log")"
contem "T-s4-2 portao pre grava o csv na saida" "csv=$OUT/04-portao51-pre.csv" "$(cat "$T/shim.log")"
antes "T-s4-2 recriar antes do portao pre" "$T/shim.log" "RECRIAR" "PORTAO dur="
antes "T-s4-2 portao pre antes do g1b" "$T/shim.log" "PORTAO dur=" "tmux new-window"
antes "T-s4-2 g1b lancado antes do g1" "$T/shim.log" "tmux new-window" "G1 REP="
contem "T-s4-2 g1 com o ambiente da REP=4R" "G1 REP=4 G1_DUR=90 G1_LOG_SAUDE_S=2 G1_LOG_POS_S=30 COLETA= TCC_DIR=$T/home/tcc-gate cwd=$T/home/tcc-gate" "$(cat "$T/shim.log")"
contem "T-s4-2 g1b com REP=4 COLETA=minima" "G1B REP=4 COLETA=minima TCC_DIR=$T/home/tcc-gate cwd=$T/home/tcc-gate" "$(cat "$T/shim.log")"
contem "T-s4-2 janela tmux propria do g1b" "tmux new-window -d -P -F #{window_id} -n s4-g1b" "$(cat "$T/shim.log")"
contem "T-s4-2 o GO que chegou ao g1 e o digitado" "G1 resposta=GO" "$(cat "$T/shim.log")"
contem "T-s4-2 g1b terminou" "G1B FIM" "$(cat "$T/shim.log")"
ok "T-s4-2 g1b.rc = 0" 0 "$(cat "$OUT/g1b.rc")"
contem "T-s4-2 t_merge registrado" "t_merge_s=46" "$(cat "$OUT/06-pos-g1.log")"
contem "T-s4-2 g1-rep4 com hashes" "marcadores.txt" "$(cat "$OUT/g1-rep4.sha256")"
contem "T-s4-2 go do G-1 copiado" "go G-1 (REP=4, janela de 90 s em isn3)" "$(cat "$OUT/go.log")"
nao "T-s4-2 sem PARAR" "PARAR" "$SAIDA"
ok "T-s4-2 nenhuma chamada inesperada" 0 "$(printf '%s' "$SAIDA" | grep -c inesperado)"

echo "== T-s4-3: TCC_DIR definido igual a ~/tcc-gate e aceito; diferente aborta =="
nova_arvore; roda $'RECRIAR\nGO\n' TCC_DIR="$T/home/tcc-gate"
ok "T-s4-3 TCC_DIR igual: rc 0" 0 "$RC"; contem "T-s4-3 ja exportado" "(ja exportado)" "$SAIDA"
nova_arvore; roda $'RECRIAR\nGO\n' TCC_DIR="$T/home/tcc-desenvolvimento"
ok "T-s4-3 TCC_DIR diferente: rc 1" 1 "$RC"; contem "T-s4-3 mensagem" "precisa ser $T/home/tcc-gate" "$SAIDA"; nada_destrutivo "T-s4-3"

echo "== T-s4-4: pre-condicoes que falham abortam antes do down =="
precond() { # precond <rotulo> <trecho-esperado> VAR=valor...
  local rot="$1" tr="$2"; shift 2; nova_arvore; prep_extra; roda $'RECRIAR\nGO\n' "$@"
  ok "T-s4-4 $rot: rc 1" 1 "$RC"; contem "T-s4-4 $rot: motivo" "$tr" "$(cat "$OUT/00-pre.log" 2>/dev/null)"
  contem "T-s4-4 $rot: abortado" "S4 ABORTADO (rc=1): pre-condicoes" "$SAIDA"; nada_destrutivo "T-s4-4 $rot"; prep_extra() { :; }; }
prep_extra() { :; }
precond "fora do tmux" "fora do tmux" TMUX=
precond "lock livre" "LIVRE" SHIM_LOCK=livre
precond "sudo -n falha" "sudo -n true falhou" SHIM_SUDO_FALHA=1
precond "kernel diferente" "kernel 6.8.0-150-generic" SHIM_KERNEL=6.8.0-150-generic
precond "boot_id diferente" "houve reboot?" S4_BOOT_ESPERADO=00000000-0000-0000-0000-000000000000
precond "timer tcc-heal armado" "1 timers tcc-heal armados" SHIM_TIMERS=1
precond "G1_DUR herdado" "variaveis herdadas que mudariam o g1/g1b: G1_DUR" G1_DUR=30
precond "COLETA herdada" "variaveis herdadas que mudariam o g1/g1b: COLETA" COLETA=completa
prep_extra() { echo "# alterado" >> "$G/runs/gate-b29/g1.sh"; ( cd "$G" && git -c user.name=t -c user.email=t@t commit -qam alt ); }
precond "hash do g1.sh diferente" "hash diferente da referencia"
prep_extra() { echo "# sujo" >> "$G/runs/gate-b29/g-lib.sh"; }
precond "arquivo versionado modificado" "1 arquivos versionados modificados"
prep_extra() { mkdir -p "$G/runs/gate-b29/g1-rep4"; }
precond "g1-rep4 ja existe" "g1-rep4 ja existe"
prep_extra() { rm -f "$G/workload/target/"*.jar; ( cd "$G" && git -c user.name=t -c user.email=t@t commit -qam semjar ); }
precond "jar ausente" "jar workload/target/session-workload-*.jar ausente"

echo "== T-s4-5: confirmacao da recriacao =="
nova_arvore; roda $'nao\nGO\n'
ok "T-s4-5 sem RECRIAR: rc 1" 1 "$RC"; contem "T-s4-5 mensagem" "recriacao nao confirmada" "$SAIDA"; nada_destrutivo "T-s4-5"
contem "T-s4-5 copia de leitura feita antes" "copia-cluster-antigo rc=0" "$(cat "$OUT/rc.txt")"

echo "== T-s4-6: recriacao sem ESTAVEL aborta antes do portao e do g1 =="
nova_arvore; roda $'RECRIAR\nGO\n' S4T_REC_FALHA=1
ok "T-s4-6 rc 2" 2 "$RC"; ok "T-s4-6 sem portao" 0 "$(grep -c '^PORTAO' "$T/shim.log")"
ok "T-s4-6 g1 nao chamado" 0 "$(grep -c '^G1 ' "$T/shim.log")"; ok "T-s4-6 g1b nao lancado" 0 "$(grep -c '^tmux new-window' "$T/shim.log")"

echo "== T-s4-7: portao pre ruim PARA (sem g1b, sem g1) =="
nova_arvore; roda $'RECRIAR\nGO\n' S4T_PORTAO_RUIM=1
ok "T-s4-7 rc 3" 3 "$RC"; contem "T-s4-7 mensagem" "portao 5.1 pre falhou: S4 NAO roda" "$SAIDA"
contem "T-s4-7 avaliacao gravada" "FALHA portao: amostras_ruins=1" "$(cat "$OUT/04-portao51-pre.avaliacao")"
ok "T-s4-7 g1 nao chamado" 0 "$(grep -c '^G1 ' "$T/shim.log")"; ok "T-s4-7 g1b nao lancado" 0 "$(grep -c '^tmux new-window' "$T/shim.log")"

echo "== T-s4-8: Gestor nao digita GO: g1 aborta, g1b encerrado, sem portao pos =="
nova_arvore; roda $'RECRIAR\nnao\n'
ok "T-s4-8 rc 4" 4 "$RC"; contem "T-s4-8 resposta que chegou ao g1" "G1 resposta=nao" "$(cat "$T/shim.log")"
contem "T-s4-8 g1b encerrado" "tmux kill-window -t @7" "$(cat "$T/shim.log")"
contem "T-s4-8 registro" "g1b encerrado (g1 sem event=start)" "$(cat "$OUT/rc.txt")"
ok "T-s4-8 portao so o pre" 1 "$(grep -c '^PORTAO' "$T/shim.log")"
ok "T-s4-8 sem go novo no go.log" 0 "$(grep -c 'REP=4' "$T/home/tcc-gate/runs/gate-b29/go.log")"
contem "T-s4-8 aviso do dead-man" "inject-partition.sh --heal --minority isn3" "$SAIDA"
sleep 1; ok "T-s4-8 g1b nao terminou como coleta" 0 "$(grep -c 'G1B FIM' "$T/shim.log")"

echo "== T-s4-9: g1 falha depois do start: g1b NAO e encerrado =="
nova_arvore; roda $'RECRIAR\nGO\n' S4T_G1_RC=1
ok "T-s4-9 rc 4" 4 "$RC"; nao "T-s4-9 sem kill-window" "kill-window" "$(cat "$T/shim.log")"
ok "T-s4-9 estado apos falha gravado" 1 "$([ -s "$OUT/06-pos-falha.log" ] && echo 1 || echo 0)"
sleep 3

echo "== T-s4-10: t_merge > 60 s: PARAR, rc 5, portao pos ainda roda =="
nova_arvore; roda $'RECRIAR\nGO\n' S4T_TMERGE=188
ok "T-s4-10 rc 5" 5 "$RC"; contem "T-s4-10 PARAR" "PARAR: t_merge_s=188" "$SAIDA"
ok "T-s4-10 portao pos rodou" 2 "$(grep -c '^PORTAO' "$T/shim.log")"

echo "RESUMO: $P PASS, $F FAIL"
[ "$F" = 0 ]
