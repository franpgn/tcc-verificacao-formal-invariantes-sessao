#!/usr/bin/env bash
# runs/gate-b29/gate-test-lib-v4.sh -- EXTENSAO de gate-test-lib.sh para os testes SEM VM de l0.sh/l1.sh/l0c.sh (L-gate) e g5b.sh.
# Carrega gate-test-lib.sh (ok/contem/ordem/roda, shims de podman/sudo/nsenter/readlink/flock/curl, stubs do inject-partition)
# e SUBSTITUI/ACRESCENTA, no mesmo $SHIMDIR:
#   - tc:          prio/netem na banda 1:3/filtro u32 (netdegrade) alem de clsact e netem raiz (F2/F3); SHIM_TC_FAIL=<trecho>
#                  faz o tc falhar (rc 2) quando os argumentos contem o trecho (o kernel recusando um parametro);
#   - ip:          `ip -4 addr show` fixo;
#   - systemd-run: dead-man que DISPARA de fato (apos --on-active=Ns executa o comando da unit, como o systemd), a menos que o
#                  timer seja parado antes. SHIM_DM_PRAZO=<s> troca o prazo; SHIM_DM_ATRASO=<s> soma atraso; SHIM_DM_NOFIRE=1 nunca
#                  dispara. A saida da unit vai para state/journal.<unit>;
#   - systemctl:   cat/show/is-failed/list-timers/stop coerentes com o estado acima (stop mata o timer);
#   - journalctl:  -u <unit>.service le o journal simulado;
#   - stub scripts/inject-netdegrade.sh (--heal, --dry-run, injecao com marcador). STUB_RC, STUB_DUR, STUB_NO_CLEAN,
#     STUB_HEAL_RC (rc APOS remover), STUB_HEAL_NOREMOVE (heal que nao remove).
# `bash` com BASH_ENV=$BASHENV_TTY faz `[ -t 0 ]` valer 0, para exercitar o caminho "com terminal" do exigir_go de l-lib.sh
# (que, verbatim do roteiro, nao tem o G_TESTE do g56-lib.sh). Sem BASH_ENV, o teste de "sem terminal" e real (stdin </dev/null).
# Nada aqui prova o kernel, o systemd real nem o AccuracySec real. NAO toca na VM, em rede nem em privilegio.

source "$(dirname "${BASH_SOURCE[0]}")/gate-test-lib.sh"

BASHENV_TTY="$TMP/bashenv-tty.sh"
cat > "$BASHENV_TTY" <<'EOF'
function [ { if (( $# == 3 )) && [[ "$1" == -t && "$2" == 0 && "$3" == "]" ]]; then return 0; fi; builtin [ "$@"; }
EOF

cat > "$SHIMDIR/tc" <<'EOF'
#!/usr/bin/env bash
# estado por PID: clsact.<p> (F2), netem.<p> (F3, raiz), prio.<p> + nm.<p> (netem em 1:3) + flt.<p> (u32) (F4)
S="$SHIM_STATE"; p="$SHIM_PID"; args="$*"
cl="$S/clsact.$p"; ne="$S/netem.$p"; pr="$S/prio.$p"; nm="$S/nm.$p"; fl="$S/flt.$p"
if [[ -n "${SHIM_TC_FAIL:-}" && "$args" == *"$SHIM_TC_FAIL"* ]]; then echo "RTNETLINK answers: Invalid argument" >&2; exit 2; fi
case "$args" in
  "qdisc add dev "*" clsact") : >"$cl";;
  "qdisc add dev "*" root netem "*) : >"$ne";;
  "qdisc add dev "*" root handle 1: prio "*) : >"$pr";;
  "qdisc add dev "*" parent 1:3 handle 30: netem "*) [[ -f "$pr" ]] || { echo "Error: Parent Qdisc doesn't exists." >&2; exit 2; }; echo "$args" >"$nm";;
  "qdisc change dev "*" parent 1:3 handle 30: netem "*) [[ -f "$nm" ]] || { echo "Error: Cannot find specified qdisc on specified device." >&2; exit 2; }; echo "$args" >"$nm";;
  "filter add dev "*" parent 1: protocol ip prio 1 u32 "*) [[ -f "$pr" ]] || { echo "Error: Parent Qdisc doesn't exists." >&2; exit 2; }; echo "$args" >>"$fl";;
  "qdisc del dev "*" clsact") [[ -f "$cl" ]] || { echo "Error: Cannot find specified qdisc on specified device." >&2; exit 2; }; rm -f "$cl"; : >"$S/merged.flag";;
  "qdisc del dev "*" root")
     if [[ -f "$pr" ]]; then rm -f "$pr" "$nm" "$fl"
     elif [[ -f "$ne" ]]; then rm -f "$ne"
     else echo "Error: Cannot delete qdisc with handle of zero." >&2; exit 2; fi;;
  "qdisc show dev "*)
     if [[ -f "$pr" ]]; then echo "qdisc prio 1: root refcnt 2 bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
     else echo "qdisc noqueue 0: root refcnt 2"; fi
     [[ -f "$nm" ]] && echo "qdisc netem 30: parent 1:3 limit 1000 delay 1ms"
     [[ -f "$ne" ]] && echo "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms  13ms"
     [[ -f "$cl" ]] && echo "qdisc clsact ffff: parent ffff:fff1"
     true;;
  "-s qdisc show dev "*) echo "qdisc netem 30: parent 1:3 limit 1000"; echo " Sent 100 bytes 1 pkt (dropped 0, overlimits 0 requeues 0)";;
  "filter show dev "*) [[ -f "$fl" ]] && { echo "filter parent 1: protocol ip pref 1 u32 chain 0"; echo "  match c0000201/ffffffff at 16"; }; true;;
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
cat > "$SHIMDIR/ip" <<'EOF'
#!/usr/bin/env bash
echo "ip $*" >>"$SHIM_LOG"
echo "2: eth0    inet 10.89.0.3/24 brd 10.89.0.255 scope global eth0"
EOF
cat > "$SHIMDIR/systemd-run" <<'EOF'
#!/usr/bin/env bash
echo "systemd-run $*" >>"$SHIM_LOG"
S="$SHIM_STATE"; u=""; on=0; wd="$PWD"; envs=(); props=""
n=$(ls "$S"/sr.* 2>/dev/null | wc -l)
printf '%s\0' "$@" > "$S/sr.$((n+1))"
while [ $# -gt 0 ]; do
  case "$1" in
    --unit=*) u="${1#--unit=}";;
    --on-active=*) on="${1#--on-active=}"; on="${on%s}";;
    --working-directory=*) wd="${1#--working-directory=}";;
    --setenv=*) envs+=("${1#--setenv=}");;
    --quiet) ;;
    --timer-property=*) props+="${1#--timer-property=}";;
    *) break;;
  esac; shift
done
cmd=("$@")
{ echo "[Unit]"; echo "[Service]"; echo "Environment=${envs[*]}"; echo "WorkingDirectory=$wd"; echo "ExecStart=${cmd[*]}"; } > "$S/unit.$u"
{ echo "OnActiveSec=${on}s"; [ -z "$props" ] || echo "$props"; } > "$S/timerdef.$u"
: > "$S/timer.$u"
d="${SHIM_DM_PRAZO:-$on}"
(
  sleep "$d" & SP=$!
  trap 'kill "$SP" 2>/dev/null; exit 0' TERM
  wait "$SP"
  [ -z "${SHIM_DM_ATRASO:-}" ] || sleep "$SHIM_DM_ATRASO"
  [ -e "$S/timer.$u" ] || exit 0
  [ -z "${SHIM_DM_NOFIRE:-}" ] || exit 0
  rm -f "$S/timer.$u"
  { echo "$(date -Is) server1 systemd[1]: Started $u.service."
    ( cd "$wd" && env "${envs[@]}" "${cmd[@]}" ) 2>&1; rc=$?
    if [ "$rc" = 0 ]; then echo "$(date -Is) server1 systemd[1]: $u.service: Deactivated successfully."
    else echo "$(date -Is) server1 systemd[1]: $u.service: Failed with result 'exit-code'."; fi; } >> "$S/journal.$u" 2>&1
) >/dev/null 2>&1 </dev/null &
echo $! > "$S/timer.$u"
exit 0
EOF
cat > "$SHIMDIR/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "systemctl $*" >>"$SHIM_LOG"
S="$SHIM_STATE"
case "$1" in
  stop) u="${2%.timer}"; [ -s "$S/timer.$u" ] && kill "$(cat "$S/timer.$u")" 2>/dev/null; rm -f "$S/timer.$u";;
  list-timers) for f in "$S"/timer.*; do [[ -e "$f" ]] && echo "${f##*/timer.}.timer  n/a"; done; true;;
  cat) u="${2%.service}"; u="${u%.timer}"
       case "$2" in *.timer) echo "[Timer]"; cat "$S/timerdef.$u" 2>/dev/null;; *) cat "$S/unit.$u" 2>/dev/null;; esac;;
  show) u="${2%.timer}"; grep -q AccuracySec "$S/timerdef.$u" 2>/dev/null || echo "AccuracySec=1min"; cat "$S/timerdef.$u" 2>/dev/null; true;;
  is-failed) u="${2%.service}"
       if grep -q "Failed with result" "$S/journal.$u" 2>/dev/null; then echo failed; exit 0; else echo inactive; exit 1; fi;;
esac
exit 0
EOF
cat > "$SHIMDIR/journalctl" <<'EOF'
#!/usr/bin/env bash
echo "journalctl $*" >>"$SHIM_LOG"
u=""; while [ $# -gt 0 ]; do case "$1" in -u) u="${2%.service}"; shift;; esac; shift; done
cat "$SHIM_STATE/journal.$u" 2>/dev/null
exit 0
EOF
# podman: o de gate-test-lib.sh + `logs` que simula as vistas do JGroups conforme o estado do shim de tc:
#   clsact em qualquer no => split (isn1/isn2 vista de 2; isn3 vista de 1 + DEGRADED_MODE); apos o clsact ser removido => MergeView de 3.
#   SHIM_LOGS_SEM_SPLIT=1 (nunca mostra o split), SHIM_LOGS_SEM_MERGE=1 (nunca mostra o MergeView), SHIM_MERGE_PREMATURE=1
#   (mostra MergeView ja durante o split: as vistas "se juntaram" sozinhas).
cat > "$SHIMDIR/podman" <<'EOF'
#!/usr/bin/env bash
echo "podman $*" >>"$SHIM_LOG"
case "$1" in
  logs)
    nome="${@: -1}"; S="$SHIM_STATE"; split=0; ls "$S"/clsact.* >/dev/null 2>&1 && split=1
    if [[ "$split" = 1 && -z "${SHIM_LOGS_SEM_SPLIT:-}" ]]; then
      if [[ "$nome" != isn3 && -n "${SHIM_SPLIT_SO_ISN3:-}" ]]; then :
      elif [[ "$nome" == isn3 ]]; then
        echo "2026-10-16 06:49:03,411 INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn3-5889|5] (1) [isn3-5889]"
        echo "2026-10-16 06:49:03,413 INFO  [o.i.CLUSTER] [Context=sessions]ISPN100011: Entering availability mode DEGRADED_MODE, topology id 23"
      else
        echo "2026-10-16 06:49:02,320 INFO  [o.i.CLUSTER] ISPN000094: Received new cluster view for channel cluster-tcc: [isn2-19096|5] (2) [isn2-19096, isn1-47759]"
      fi
    fi
    if [[ ( -f "$S/merged.flag" || -n "${SHIM_MERGE_PREMATURE:-}" ) && -z "${SHIM_LOGS_SEM_MERGE:-}" ]]; then
      echo "2026-10-16 06:50:38,322 INFO  [o.i.CLUSTER] ISPN000093: Received new, MERGED cluster view for channel cluster-tcc: MergeView::[isn2-19096|6] ($([[ -n "${SHIM_MERGE_PARCIAL:-}" ]] && echo 2 || echo 3)) [isn2-19096, isn1-47759, isn3-5889], 2 subgroups"
    fi
    exit 0;;
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
chmod +x "$SHIMDIR"/*

# o stub de inject-partition imprime a mesma mensagem de heal do injetor real (o journal da unit e evidencia do modo B)
awk '{print} /^  echo "INJ heal" >>"\$SHIM_LOG"/{print "  echo \"[inject-partition] Heal: removendo clsact em isn3 (iface eth0)\""}' \
  "$TCC/scripts/inject-partition.sh" > "$TCC/scripts/inject-partition.sh.novo" && mv "$TCC/scripts/inject-partition.sh.novo" "$TCC/scripts/inject-partition.sh"

cat > "$TCC/scripts/inject-netdegrade.sh" <<'EOF'
#!/usr/bin/env bash
# STUB de inject-netdegrade.sh (v4). STUB_RC, STUB_DUR, STUB_NO_CLEAN, STUB_HEAL_RC, STUB_HEAL_NOREMOVE.
echo "INJ netdegrade $*" >>"$SHIM_LOG"
if [[ "$1" == "--heal" ]]; then
  echo "INJ netdegrade-heal" >>"$SHIM_LOG"
  [[ -n "${STUB_HEAL_SILENT:-}" ]] || echo "[inject-netdegrade] Heal: removendo a qdisc prio 1: do injetor em isn1 isn2 isn3 (iface eth0); raiz alheia nao e tocada"
  for n in isn1 isn2 isn3; do
    pid=$(sudo podman inspect -f '{{.State.Pid}}' "$n") || continue
    if sudo nsenter -t "$pid" -n tc qdisc show dev eth0 | grep -q '^qdisc prio 1: '; then
      [[ -n "${STUB_HEAL_NOREMOVE:-}" ]] || sudo nsenter -t "$pid" -n tc qdisc del dev eth0 root
      echo "[inject-netdegrade]   $n (pid $pid): prio 1: removida"
    fi
  done
  exit "${STUB_HEAL_RC:-0}"
fi
dry=0; dur=60; marker=/dev/null
while [[ $# -gt 0 ]]; do case "$1" in --dry-run) dry=1;; --duration) dur="$2"; shift;; --marker) marker="$2"; shift;; esac; shift; done
if [[ "$dry" = 1 ]]; then echo "[inject-netdegrade] Plano (dry-run): nada executado"; exit 0; fi
pid=$(sudo podman inspect -f '{{.State.Pid}}' isn3)
sudo nsenter -t "$pid" -n tc qdisc add dev eth0 root handle 1: prio bands 3 priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
sudo nsenter -t "$pid" -n tc qdisc add dev eth0 parent 1:3 handle 30: netem loss 20%
echo "fault=F4 node=isn3 event=start t_epoch_ns=$(date +%s%N)" >> "$marker"
sleep "${STUB_DUR:-$dur}"
[[ -n "${STUB_NO_CLEAN:-}" ]] || sudo nsenter -t "$pid" -n tc qdisc del dev eth0 root
echo "fault=F4 node=isn3 event=end t_epoch_ns=$(date +%s%N)" >> "$marker"
exit "${STUB_RC:-0}"
EOF

# limpeza de estado entre cenarios (acrescenta as variaveis novas ao reset_shims original)
# mata os 'timers' de fundo do shim de systemd-run ainda dormindo (nao deixa processo solto entre cenarios/ao fim)
matar_timers() { local f; for f in "$SHIM_STATE"/timer.*; do [ -s "$f" ] && kill "$(cat "$f")" 2>/dev/null; done; return 0; }
reset_shims_v4() {
  matar_timers
  reset_shims
  rm -rf "$TCC/runs/gate-netdegrade" "$TCC/runs/gate-b29/g5b-A" "$TCC/runs/gate-b29/g5b-B" 2>/dev/null
  unset SHIM_TC_FAIL SHIM_DM_PRAZO SHIM_DM_ATRASO SHIM_DM_NOFIRE STUB_RC STUB_DUR STUB_NO_CLEAN STUB_HEAL_NOREMOVE STUB_HEAL_SILENT SHIM_SPLIT_SO_ISN3 SHIM_MERGE_PARCIAL \
        L0C_PRAZO L0C_MIN L0C_MAX L0C_LIMITE G5B_DUR G5B_T_KILL G5B_MARGEM_A G5B_MARGEM_B G5B_ESPERA_SPLIT G5B_PERSISTE_S \
        G5B_LIMITE_S G5B_ESPERA_START G5B_TOL_S G5B_FOLGA_S G5B_ACC SHIM_LOGS_SEM_SPLIT SHIM_LOGS_SEM_MERGE SHIM_MERGE_PREMATURE
}
# roda_tty <entrada-stdin> <script> [args...]: como `roda`, mas com [ -t 0 ] verdadeiro (BASH_ENV) e SEM G_TESTE
roda_tty() {
  local entrada="$1"; shift
  printf '%s\n' "$entrada" | BASH_ENV="$BASHENV_TTY" PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$@" > "$TMP/saida.txt" 2>&1; RC=${PIPESTATUS[1]}
  SAIDA="$(cat "$TMP/saida.txt")"
}
# roda_notty <script> [args...]: stdin </dev/null, sem BASH_ENV: o [ -t 0 ] e falso de verdade
roda_notty() {
  PATH="$PATH_SHIMS" TCC_DIR="$TCC" bash "$@" </dev/null > "$TMP/saida.txt" 2>&1; RC=$?
  SAIDA="$(cat "$TMP/saida.txt")"
}
# aguarda ate <s> segundos o processo de fundo (timers do shim) terminar de escrever
espera_arquivo() { local f="$1" t="${2:-15}" i=0; while [ ! -e "$f" ] && [ "$i" -lt $((t*10)) ]; do sleep 0.1; i=$((i+1)); done; }
