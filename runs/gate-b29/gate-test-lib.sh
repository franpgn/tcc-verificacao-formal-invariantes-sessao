#!/usr/bin/env bash
# runs/gate-b29/gate-test-lib.sh -- infraestrutura dos testes SEM VM de g1.sh, g5.sh, g6.sh (so definicoes).
# Mesmo padrao de scripts/inject-partition.test.sh: shims de podman/sudo/nsenter/tc/readlink/flock/curl/
# systemd-run/systemctl no inicio do PATH, que so registram as chamadas em $SHIM_LOG e simulam o estado
# (clsact/netem por PID, timers armados). Os "injetores" tambem sao stubs (scripts/inject-*.sh falsos em
# $TCC_DIR), que fazem as MESMAS chamadas tc via nsenter dos injetores reais. Nada aqui prova o kernel.
# NAO toca na VM, em rede, nem em privilegio.

DIR_TESTE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATE_DIR="$DIR_TESTE"
PASS=0; FAIL=0

ok()  { if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS+1)); else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL+1)); fi; }
contem()     { if grep -qF -e "$2" <<<"$3"; then echo "PASS: $1"; PASS=$((PASS+1)); else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL+1)); fi; }
nao_contem() { if grep -qF -e "$2" <<<"$3"; then echo "FAIL: $1 (encontrou '$2' indevidamente)"; FAIL=$((FAIL+1)); else echo "PASS: $1"; PASS=$((PASS+1)); fi; }
# ordem <descricao> <arquivo> <padrao1> <padrao2> ... (subsequencia, substring fixa)
ordem() {
  local desc="$1" arq="$2"; shift 2; local ini=1 pad achou
  for pad in "$@"; do
    achou="$(tail -n +"$ini" "$arq" | grep -nF -m1 -e "$pad" | cut -d: -f1)"
    if [[ -z "$achou" ]]; then echo "FAIL: $desc (padrao '$pad' ausente apos a linha $ini)"; FAIL=$((FAIL+1)); return; fi
    ini=$((ini + achou))
  done
  echo "PASS: $desc"; PASS=$((PASS+1))
}
conta() { grep -cF -e "$1" "$2" 2>/dev/null || true; }   # conta <substring> <arquivo>

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SHIMDIR="$TMP/shims"; TCC="$TMP/tcc"
mkdir -p "$SHIMDIR" "$TCC/scripts" "$TCC/runs/gate-b29"
export SHIM_LOG="$TMP/shim.log" SHIM_STATE="$TMP/state" LOCK_DIR="$TMP/locks"
mkdir -p "$SHIM_STATE" "$LOCK_DIR"

# podman: PID = 4000 + ultimo digito do nome (isn1=4001...); IP 10.89.0.<d>; SHIM_MISSING simula inexistente
cat > "$SHIMDIR/podman" <<'EOF'
#!/usr/bin/env bash
echo "podman $*" >>"$SHIM_LOG"
case "$1" in
  logs) echo "log falso de $*"; exit 0;;
  inspect) ;;
  *) exit 0;;
esac
shift; fmt=""
if [[ "$1" == "-f" ]]; then fmt="$2"; shift 2; fi
nome="$1"
case " ${SHIM_MISSING:-} " in *" $nome "*) exit 1;; esac
d="${nome: -1}"
case "$fmt" in
  *State.Pid*) echo "$((4000 + d))";;
  *State.Status*) echo "running";;
  *IPAddress*) echo "10.89.0.$d ";;
esac
exit 0
EOF
cat > "$SHIMDIR/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF
# nsenter -t PID -n CMD...: registra e passa ao tc com SHIM_PID; 'timeout' (sonda TCP) devolve SHIM_CONN_RC (124)
cat > "$SHIMDIR/nsenter" <<'EOF'
#!/usr/bin/env bash
echo "nsenter $*" >>"$SHIM_LOG"
pid="$2"; shift 3
if [[ "$1" == "timeout" ]]; then exit "${SHIM_CONN_RC:-124}"; fi
export SHIM_PID="$pid"
exec "$@"
EOF
# tc: estado por PID em $SHIM_STATE/clsact.<pid> e netem.<pid>
cat > "$SHIMDIR/tc" <<'EOF'
#!/usr/bin/env bash
cl="$SHIM_STATE/clsact.$SHIM_PID"; ne="$SHIM_STATE/netem.$SHIM_PID"; args="$*"
case "$args" in
  "qdisc add dev "*" clsact") : >"$cl";;
  "qdisc add dev "*" root netem "*) : >"$ne";;
  "qdisc del dev "*" clsact") [[ -f "$cl" ]] || { echo "Error: Cannot find specified qdisc on specified device." >&2; exit 2; }; rm -f "$cl";;
  "qdisc del dev "*" root") [[ -f "$ne" ]] || { echo "Error: Cannot delete qdisc with handle of zero." >&2; exit 2; }; rm -f "$ne";;
  "qdisc show dev "*)
     echo "qdisc noqueue 0: root refcnt 2"
     [[ -f "$ne" ]] && echo "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms  13ms"
     [[ -f "$cl" ]] && echo "qdisc clsact ffff: parent ffff:fff1"
     true;;
  "-s qdisc show dev "*) echo "qdisc netem Sent 100 bytes 1 pkt";;
  "-s filter show dev "*)
     if [[ -f "$cl" ]]; then
       for par in 2 3; do
         echo "  match 0a59010$par/ffffffff at 16"; echo "        Sent 120 bytes 3 pkt (dropped 3, overlimits 0 requeues 0) "
         echo "  match 0a59010$par/ffffffff at 12"; echo "        Sent 120 bytes 3 pkt (dropped 3, overlimits 0 requeues 0) "
       done
     fi;;
esac
exit 0
EOF
# readlink /proc/<pid>/ns/net: cada PID tem netns proprio; SHIM_HOSTNET (nos no netns do host), SHIM_SAMENS (nos que
# compartilham um mesmo netns), SHIM_NO_NS (leitura falha)
cat > "$SHIMDIR/readlink" <<'EOF'
#!/usr/bin/env bash
echo "readlink $*" >>"$SHIM_LOG"
case "$1" in
  /proc/1/ns/net) echo "net:[4026531840]";;
  /proc/*/ns/net)
     p="${1#/proc/}"; p="${p%%/*}"; d=$((p - 4000))
     case " ${SHIM_NO_NS:-} " in *" isn$d "*) exit 1;; esac
     case " ${SHIM_HOSTNET:-} " in *" isn$d "*) echo "net:[4026531840]"; exit 0;; esac
     case " ${SHIM_SAMENS:-} " in *" isn$d "*) echo "net:[4026599999]"; exit 0;; esac
     echo "net:[40265$p]";;
  *) exit 1;;
esac
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$SHIMDIR/flock"
# systemd-run: grava os argumentos (NUL) em state/sr.N e cria o "timer" state/timer.<unit>
cat > "$SHIMDIR/systemd-run" <<'EOF'
#!/usr/bin/env bash
echo "systemd-run $*" >>"$SHIM_LOG"
u=""; for a in "$@"; do case "$a" in --unit=*) u="${a#--unit=}";; esac; done
n=$(ls "$SHIM_STATE"/sr.* 2>/dev/null | wc -l)
printf '%s\0' "$@" > "$SHIM_STATE/sr.$((n+1))"
: > "$SHIM_STATE/timer.$u"
exit 0
EOF
cat > "$SHIMDIR/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "systemctl $*" >>"$SHIM_LOG"
case "$1" in
  stop) rm -f "$SHIM_STATE/timer.${2%.timer}";;
  list-timers) for f in "$SHIM_STATE"/timer.*; do [[ -e "$f" ]] && echo "${f##*/timer.}.timer  n/a"; done; true;;
esac
exit 0
EOF
# curl: so o endpoint de saude. Modo em $SHIM_STATE/health.mode (ok|rebal|down); SHIM_DOWN_PORTS (portas sem resposta);
# SHIM_HEALTH_SLOW (s). Com -w, anexa o codigo HTTP como o curl real.
cat > "$SHIMDIR/curl" <<'EOF'
#!/usr/bin/env bash
w=""; url=""
while [ $# -gt 0 ]; do case "$1" in -w) w="$2"; shift;; http*) url="$1";; esac; shift; done
echo "curl $url" >>"$SHIM_LOG"
[ -z "${SHIM_HEALTH_SLOW:-}" ] || sleep "$SHIM_HEALTH_SLOW"
port="${url#*localhost:}"; port="${port%%/*}"
mode=$(cat "$SHIM_STATE/health.mode" 2>/dev/null || echo ok)
down="${SHIM_DOWN_PORTS:-$(cat "$SHIM_STATE/down.ports" 2>/dev/null)}"   # variavel ou arquivo (mudavel com o laco em curso)
case " $down " in *" $port "*) mode=down;; esac
case "$mode" in
  down) printf '%s' "${w//%\{http_code\}/000}"; exit 28;;
  rebal) st=HEALTHY_REBALANCING; n=3;;
  nodes2) st=HEALTHY; n=2;;
  *) st=HEALTHY; n=3;;
esac
printf '{"cluster_health":{"cluster_name":"x","health_status":"%s","number_of_nodes":%s,"node_names":["a","b"]},"cache_health":[{"cache_name":"sessions","status":"HEALTHY"}]}' "$st" "$n"
printf '%s' "${w//%\{http_code\}/200}"
exit 0
EOF
chmod +x "$SHIMDIR"/*

# stubs dos injetores (em $TCC/scripts): as mesmas chamadas tc via nsenter que os reais, marcadores com t_epoch_ns
cat > "$TCC/scripts/inject-partition.sh" <<'EOF'
#!/usr/bin/env bash
# STUB de inject-partition.sh. STUB_SEM_START, STUB_SEM_CLSACT, STUB_DIE_RC/STUB_DIE_CALL, STUB_HEAL_RC.
echo "INJ partition $*" >>"$SHIM_LOG"
pid=$(sudo podman inspect -f '{{.State.Pid}}' isn3)
if [[ "$1" == "--heal" ]]; then
  echo "INJ heal" >>"$SHIM_LOG"
  sudo nsenter -t "$pid" -n tc qdisc del dev eth0 clsact 2>/dev/null
  exit "${STUB_HEAL_RC:-0}"
fi
dur=90; marker=/dev/null
while [[ $# -gt 0 ]]; do case "$1" in --duration) dur="$2"; shift;; --marker) marker="$2"; shift;; esac; shift; done
n=$(( $(cat "$SHIM_STATE/inj.count" 2>/dev/null || echo 0) + 1 )); echo "$n" > "$SHIM_STATE/inj.count"
[[ -n "${STUB_SEM_CLSACT:-}" ]] || sudo nsenter -t "$pid" -n tc qdisc add dev eth0 clsact
[[ -n "${STUB_SEM_START:-}" ]] || echo "fault=F2 node=isn3 event=start t_epoch_ns=$(date +%s%N)" >> "$marker"
# STUB_DIE_RC: a partir da chamada STUB_DIE_CALL, o stub "morre" sozinho (deixa clsact e sem marcador end)
if [[ -n "${STUB_DIE_RC:-}" && "$n" -ge "${STUB_DIE_CALL:-1}" ]]; then exit "$STUB_DIE_RC"; fi
fim() {
  sudo nsenter -t "$pid" -n tc qdisc del dev eth0 clsact 2>/dev/null
  # STUB_POS_FUSAO=<s>: depois do fim da janela o cluster demora <s> s para voltar a size=3 (simula a fusao)
  if [[ -n "${STUB_POS_FUSAO:-}" ]]; then echo nodes2 > "$SHIM_STATE/health.mode"; ( sleep "$STUB_POS_FUSAO"; echo ok > "$SHIM_STATE/health.mode" ) & fi
  echo "fault=F2 node=isn3 event=end t_epoch_ns=$(date +%s%N)" >> "$marker"
}
trap 'kill "$SLP" 2>/dev/null; fim; exit 143' TERM
sleep "$dur" & SLP=$!; wait "$SLP"
fim; exit 0
EOF
cat > "$TCC/scripts/inject-jitter.sh" <<'EOF'
#!/usr/bin/env bash
# STUB de inject-jitter.sh. STUB_NO_NETEM: nao instala o netem.
echo "INJ jitter $*" >>"$SHIM_LOG"
node=isn2; dur=130
while [[ $# -gt 0 ]]; do case "$1" in --node) node="$2"; shift;; --duration) dur="$2"; shift;; esac; shift; done
pid=$(sudo podman inspect -f '{{.State.Pid}}' "$node")
[[ -n "${STUB_NO_NETEM:-}" ]] || sudo nsenter -t "$pid" -n tc qdisc add dev eth0 root netem delay 20ms 13ms distribution normal
fim() { sudo nsenter -t "$pid" -n tc qdisc del dev eth0 root 2>/dev/null; }
trap 'kill "$SLP" 2>/dev/null; fim; exit 143' TERM
sleep "$dur" & SLP=$!; wait "$SLP"
fim; exit 0
EOF

# reset_shims: zera log, estados e saidas; remove variaveis de cenario
reset_shims() {
  : > "$SHIM_LOG"
  rm -rf "$SHIM_STATE" "$TCC/runs/gate-b29/g1-rep"* "$TCC/runs/gate-b29/g5" "$TCC/runs/gate-b29/g6" "$TCC/runs/gate-b29/go.log" "$LOCK_DIR"/* 2>/dev/null
  mkdir -p "$SHIM_STATE" "$LOCK_DIR"
  unset SHIM_MISSING SHIM_HOSTNET SHIM_SAMENS SHIM_NO_NS SHIM_DOWN_PORTS SHIM_HEALTH_SLOW SHIM_CONN_RC \
        STUB_SEM_START STUB_SEM_CLSACT STUB_DIE_RC STUB_DIE_CALL STUB_HEAL_RC STUB_NO_NETEM STUB_POS_FUSAO \
        G5_DUR G5_T_KILL G5_LIMITE_S G5_ESPERA_START G6_F2 G6_F3 G6_AMOSTRAS G6_LIMITE_S G6_MARGEM G6_ESPERA_NETEM \
        G6_HEALTH_TIMEOUT G1_DUR G1_AMOSTRAS G1_MERGE_LIM G1_LOG_SAUDE_S REP
}
PATH_SHIMS="$SHIMDIR:$PATH"
# roda <entrada-stdin> <script> [args...]: executa com shims, TCC_DIR e G_TESTE=1; saida em $SAIDA, rc em $RC
roda() {
  local entrada="$1"; shift
  # saida em ARQUIVO (nao em $(...)): um processo orfao de segundo plano nao pode prender o teste num pipe aberto
  printf '%s\n' "$entrada" | PATH="$PATH_SHIMS" TCC_DIR="$TCC" G_TESTE=1 bash "$@" > "$TMP/saida.txt" 2>&1; RC=${PIPESTATUS[1]}
  SAIDA="$(cat "$TMP/saida.txt")"
}
estado_timers() { ls "$SHIM_STATE"/timer.* 2>/dev/null | wc -l | tr -d ' '; }
resumo_final() { echo "==== RESULTADO: $PASS PASS, $FAIL FAIL ===="; [[ "$FAIL" == 0 ]]; }
