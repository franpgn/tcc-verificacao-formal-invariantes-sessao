#!/usr/bin/env bash
#
# inject-crash.test.sh
#
# Suite de testes do inject-crash.sh (F1 como crash silencioso, opcao B do plano
# docs/prompt-f1-kill-2026-10-27.md da branch docs/plano-implementacao-controlador,
# casos C1..C15 do prompt da secao 7) que NAO exigem VM, podman, ip nem privilegio.
#
# Os casos com SHIMS colocam scripts falsos de podman, sudo, readlink, nsenter, ip e
# sleep no inicio do PATH. Os shims registram cada chamada em $SHIM_LOG e simulam o
# estado do container (running/exited, ExitCode) e o estado da interface (UP/DOWN)
# por PID. Assim a LOGICA do injetor (ordem dos comandos, guarda de netns, codigos de
# saida, sinais, carimbo por linha) e verificada sem kernel. Os shims NAO provam:
#   - que 'ip link set dev eth0 down' no netns real cala o JGroups sem FIN (isso e o
#     smoke local, secao 4.1 do plano, e o ensaio na VM, secao 4.2);
#   - que o 'podman kill --signal KILL' real da ExitCode 137 nem que o FD_ALL3 detecta
#     a queda em ~40 s;
#   - que o formato real de 'ip -o link show' e o que o shim imita (o parser le a
#     lista de flags entre '<' e '>', formato documentado do iproute2).
#
# Auto-contido (sem bats). Rodar:  bash scripts/inject-crash.test.sh
# CRASH_SCRIPT=<arquivo> roda a suite contra outro injetor (mutantes).
# Exit 0 se todos passarem; 1 caso contrario.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CRASH="${CRASH_SCRIPT:-${DIR}/inject-crash.sh}"

PASS=0
FAIL=0

ok() { # ok <descricao> <esperado> <obtido>
    if [[ "$2" == "$3" ]]; then
        echo "PASS: $1"; PASS=$((PASS + 1))
    else
        echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1))
    fi
}

contem() { # contem <descricao> <substring> <texto>
    if printf '%s\n' "$3" | grep -qF -e "$2"; then
        echo "PASS: $1"; PASS=$((PASS + 1))
    else
        echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1))
    fi
}

nao_contem() { # nao_contem <descricao> <substring> <texto>
    if printf '%s\n' "$3" | grep -qF -e "$2"; then
        echo "FAIL: $1 (encontrou '$2' indevidamente)"; FAIL=$((FAIL + 1))
    else
        echo "PASS: $1"; PASS=$((PASS + 1))
    fi
}

verdade() { # verdade <descricao> <comando...>
    local d="$1"; shift
    if "$@"; then echo "PASS: ${d}"; PASS=$((PASS + 1)); else echo "FAIL: ${d}"; FAIL=$((FAIL + 1)); fi
}

conta() { grep -cF -e "$1" "${SHIM_LOG}" 2>/dev/null; }

# ordem_exata <descricao> <padrao1> ...: as linhas do SHIM_LOG que casam ALGUM dos
# padroes, na ordem do log, tem de ser exatamente a lista dada (nem mais, nem menos).
ordem_exata() {
    local desc="$1"; shift
    local esperado obtido linha pad
    esperado="$(printf '%s\n' "$@")"
    obtido=""
    while IFS= read -r linha; do
        for pad in "$@"; do
            if [[ "${linha}" == *"${pad}"* ]]; then obtido+="${pad}"$'\n'; break; fi
        done
    done < "${SHIM_LOG}"
    obtido="${obtido%$'\n'}"
    if [[ "${esperado}" == "${obtido}" ]]; then
        echo "PASS: ${desc}"; PASS=$((PASS + 1))
    else
        echo "FAIL: ${desc}"
        echo "      esperado: $(printf '%s | ' "$@")"
        echo "      obtido:   $(printf '%s' "${obtido}" | tr '\n' '|' | sed 's/|/ | /g')"
        FAIL=$((FAIL + 1))
    fi
}

PATH_SEM_PODMAN="/usr/bin:/bin"
REAL_SLEEP="$(command -v sleep)"

# ------------------------------------------------------------------ shims
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
SHIMDIR="${TMP}/shims"
mkdir -p "${SHIMDIR}"
export SHIM_LOG="${TMP}/shim.log"
export SHIM_STATE="${TMP}/state"
export REAL_SLEEP
mkdir -p "${SHIM_STATE}"

# podman: inspect [-f FMT] NOME | kill [--signal S] NOME | stop NOME | start NOME.
# PID = 4000 + ultimo digito do nome (SHIM_PID sobrescreve, inclusive com vazio).
# Estado em $SHIM_STATE/status-<no> (padrao running). Variaveis:
#   SHIM_MISSING      lista de nos inexistentes (inspect sai 125)
#   SHIM_STATUS       estado inicial forcado (ex.: exited)
#   SHIM_EXITCODE     ExitCode depois do kill (padrao 137); o stop da 143
#   SHIM_KILL_NOOP=1  o kill nao derruba o container (fica running)
#   SHIM_START_FAIL=1 o start sai 125
cat > "${SHIMDIR}/podman" <<'EOF'
#!/usr/bin/env bash
echo "podman $*" >> "${SHIM_LOG}"
cmd="$1"; shift
no="${*: -1}"
st="${SHIM_STATE}/status-${no}"
case "${cmd}" in
    inspect)
        fmt=""; if [[ "$1" == "-f" ]]; then fmt="$2"; shift 2; fi
        case " ${SHIM_MISSING:-} " in *" ${no} "*) echo "Error: no such object: \"${no}\"" >&2; exit 125 ;; esac
        [[ -f "${st}" ]] || echo "${SHIM_STATUS:-running}" > "${st}"
        est="$(cat "${st}")"
        d="${no: -1}"
        case "${fmt}" in
            *State.Status*)   echo "${est}" ;;
            *State.Pid*)
                if [[ -n "${SHIM_PID+x}" ]]; then echo "${SHIM_PID}"
                elif [[ "${est}" == running ]]; then echo "$((4000 + d))"; else echo 0; fi ;;
            *State.ExitCode*) cat "${SHIM_STATE}/exitcode-${no}" 2>/dev/null || echo 0 ;;
            *) echo '[{}]' ;;
        esac ;;
    kill)
        [[ -n "${SHIM_KILL_NOOP:-}" ]] && { echo "${no}"; exit 0; }
        echo exited > "${st}"; echo "${SHIM_EXITCODE:-137}" > "${SHIM_STATE}/exitcode-${no}"; echo "${no}" ;;
    stop)
        echo exited > "${st}"; echo 143 > "${SHIM_STATE}/exitcode-${no}"; echo "${no}" ;;
    start)
        [[ -n "${SHIM_START_FAIL:-}" ]] && { echo "Error: unable to start container \"${no}\"" >&2; exit 125; }
        echo running > "${st}"; rm -f "${SHIM_STATE}/link-$((4000 + ${no: -1}))"; echo "${no}" ;;
    *) exit 0 ;;
esac
EOF

# sudo: nao registra; repassa (o registro e feito pelo comando de baixo).
cat > "${SHIMDIR}/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF

# readlink: so /proc/<pid>/ns/net. Cada PID tem netns proprio, exceto com
# SHIM_HOSTNET=1 (o alvo compartilha o netns do PID 1).
cat > "${SHIMDIR}/readlink" <<'EOF'
#!/usr/bin/env bash
echo "readlink $*" >> "${SHIM_LOG}"
case "$1" in
    /proc/1/ns/net) echo "net:[4026531840]" ;;
    /proc/*/ns/net)
        if [[ -n "${SHIM_HOSTNET:-}" ]]; then echo "net:[4026531840]"
        else p="${1#/proc/}"; echo "net:[40265${p%%/*}]"; fi ;;
    *) exit 1 ;;
esac
EOF

# nsenter -t PID -n CMD...: registra e repassa ao comando com o PID exportado.
cat > "${SHIMDIR}/nsenter" <<'EOF'
#!/usr/bin/env bash
echo "nsenter $*" >> "${SHIM_LOG}"
pid=""
while [[ $# -gt 0 ]]; do
    case "$1" in -t) pid="$2"; shift 2 ;; -n) shift ;; *) break ;; esac
done
export SHIM_NETNS_PID="${pid}"
exec "$@"
EOF

# ip: estado da interface por PID em $SHIM_STATE/link-<pid> (ausente = UP).
#   SHIM_NO_IFACE=1      a interface nao existe (exit 1, 'Device does not exist')
#   SHIM_LINK_STUCK=1    'link set ... down' sai 0 mas a interface continua UP
#   SHIM_BLOCK_VERIFY=<arq> o 'ip -o link show' bloqueia ate <arq> existir
cat > "${SHIMDIR}/ip" <<'EOF'
#!/usr/bin/env bash
echo "ip $*" >> "${SHIM_LOG}"
f="${SHIM_STATE}/link-${SHIM_NETNS_PID:-0}"
ifc="${*: -1}"
[[ "$*" == *" set "* ]] && ifc="$(echo "$*" | sed -n 's/.* dev \([^ ]*\) .*/\1/p')"
if [[ -n "${SHIM_NO_IFACE:-}" ]]; then echo "Device \"${ifc}\" does not exist." >&2; exit 1; fi
mostrar() {
    if [[ -f "${f}" ]]; then
        echo "2: ${ifc}@if7: <BROADCAST,MULTICAST> mtu 1500 qdisc noqueue state DOWN mode DEFAULT group default qlen 1000"
    else
        echo "2: ${ifc}@if7: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default qlen 1000"
    fi
}
case "$*" in
    "-o link show dev "*)
        if [[ -n "${SHIM_BLOCK_VERIFY:-}" ]]; then
            : > "${SHIM_BLOCK_VERIFY}.esperando"
            for ((k = 0; k < 300; k++)); do [[ -f "${SHIM_BLOCK_VERIFY}" ]] && break; "${REAL_SLEEP}" 0.1; done
        fi
        mostrar ;;
    "link show dev "*) mostrar; echo "    link/ether 0a:58:0a:59:00:02 brd ff:ff:ff:ff:ff:ff" ;;
    "link set dev "*" down") [[ -n "${SHIM_LINK_STUCK:-}" ]] || : > "${f}" ;;
    "link set dev "*" up") rm -f "${f}" ;;
    *) echo "shim ip: comando nao previsto: $*" >&2; exit 2 ;;
esac
EOF

# sleep: registra; dorme de verdade so com SHIM_SLEEP_REAL=1 (C10, C11).
cat > "${SHIMDIR}/sleep" <<'EOF'
#!/usr/bin/env bash
echo "sleep $*" >> "${SHIM_LOG}"
[[ -n "${SHIM_SLEEP_REAL:-}" ]] && exec "${REAL_SLEEP}" "$@"
exit 0
EOF
chmod +x "${SHIMDIR}"/*

# Shims "proibidos" (C12): qualquer chamada deixa rastro e falha.
PROIBIDO="${TMP}/proibido"
mkdir -p "${PROIBIDO}"
for c in podman sudo nsenter ip; do
    printf '#!/usr/bin/env bash\necho "%s $*" >> "%s/chamadas"\nexit 99\n' "${c}" "${PROIBIDO}" > "${PROIBIDO}/${c}"
done
chmod +x "${PROIBIDO}"/*

PATH_SHIMS="${SHIMDIR}:${PATH_SEM_PODMAN}"

reset_shims() {
    : > "${SHIM_LOG}"
    rm -f "${SHIM_STATE}"/* "${TMP}/saida" "${TMP}/libera" "${TMP}/libera.esperando" 2>/dev/null
    unset SHIM_MISSING SHIM_STATUS SHIM_EXITCODE SHIM_KILL_NOOP SHIM_START_FAIL SHIM_PID SHIM_HOSTNET \
          SHIM_NO_IFACE SHIM_LINK_STUCK SHIM_BLOCK_VERIFY SHIM_SLEEP_REAL
}

# roda_shim <args...>: injetor com os shims; saida em $OUT, rc em $RC.
roda_shim() {
    OUT="$(PATH="${PATH_SHIMS}" bash "${CRASH}" "$@" 2>&1)"; RC=$?
}

# esperar_arquivo <arq> <segundos>
esperar_arquivo() {
    local k; for ((k = 0; k < $2 * 10; k++)); do [[ -e "$1" ]] && return 0; "${REAL_SLEEP}" 0.1; done; return 1
}
esperar_log() { # esperar_log <padrao-fixo> <segundos>
    local k; for ((k = 0; k < $2 * 10; k++)); do grep -qF -e "$1" "${SHIM_LOG}" 2>/dev/null && return 0; "${REAL_SLEEP}" 0.1; done; return 1
}

# =============================================================== C1
echo "== C1: modo padrao e silencioso; ordem exata das chamadas =="
reset_shims
roda_shim --node isn2 --duration 60
ok "C1 exit 0" "0" "${RC}"
ordem_exata "C1 ordem: readlink alvo, readlink 1, ip link show, set down, -o show, kill, ExitCode, sleep, start" \
    "readlink /proc/4002/ns/net" \
    "readlink /proc/1/ns/net" \
    "ip link show dev eth0" \
    "ip link set dev eth0 down" \
    "ip -o link show dev eth0" \
    "podman kill --signal KILL isn2" \
    "podman inspect -f {{.State.ExitCode}} isn2" \
    "sleep 60" \
    "podman start isn2"
contem "C1 nsenter no PID de isn2" "nsenter -t 4002 -n ip link set dev eth0 down" "$(cat "${SHIM_LOG}")"
ok "C1 kill chamado uma vez" "1" "$(conta 'podman kill')"
ok "C1 start chamado uma vez" "1" "$(conta 'podman start')"
contem "C1 linha final OK com o modo" "OK: F1 (modo=silencioso)" "${OUT}"

# =============================================================== C2
echo "== C2: 'podman stop' nunca e chamado nos modos silencioso e kill =="
reset_shims
roda_shim --duration 60
ok "C2 silencioso exit 0" "0" "${RC}"
ok "C2 silencioso sem stop" "0" "$(conta 'podman stop')"
reset_shims
roda_shim --modo kill --duration 60
ok "C2 kill exit 0" "0" "${RC}"
ok "C2 modo kill sem stop" "0" "$(conta 'podman stop')"

# =============================================================== C3
echo "== C3: guarda de netns e PID invalido -> exit 4 sem ip nem kill =="
reset_shims
SHIM_HOSTNET=1 roda_shim --duration 60
ok "C3a netns do alvo = netns do PID 1 -> exit 4" "4" "${RC}"
contem "C3a diagnostico" "netns" "${OUT}"
ok "C3a nenhuma chamada de ip" "0" "$(conta 'ip ')"
ok "C3a nenhum nsenter" "0" "$(conta 'nsenter')"
ok "C3a nenhum kill" "0" "$(conta 'podman kill')"
for p in 0 "" abc 1; do
    reset_shims
    SHIM_PID="${p}" roda_shim --duration 60
    ok "C3b PID '${p}' -> exit 4" "4" "${RC}"
    ok "C3b PID '${p}': nenhum ip" "0" "$(conta 'ip ')"
    ok "C3b PID '${p}': nenhum kill" "0" "$(conta 'podman kill')"
done
reset_shims
SHIM_MISSING="isn2" roda_shim --duration 60
ok "C3c container inexistente -> exit 4" "4" "${RC}"
reset_shims
SHIM_STATUS="exited" roda_shim --duration 60
ok "C3d container nao running -> exit 4" "4" "${RC}"
ok "C3d nenhum kill" "0" "$(conta 'podman kill')"

# =============================================================== C4
echo "== C4: interface inexistente -> exit 4 sem kill =="
reset_shims
SHIM_NO_IFACE=1 roda_shim --duration 60 --iface eth9
ok "C4 exit 4" "4" "${RC}"
ok "C4 nenhum kill" "0" "$(conta 'podman kill')"
ok "C4 nenhum link set" "0" "$(conta 'link set')"

# =============================================================== C5
echo "== C5: 'link set down' nao pega -> tentativa de up, exit 1, sem kill =="
reset_shims
SHIM_LINK_STUCK=1 roda_shim --duration 60
ok "C5 exit 1" "1" "${RC}"
ok "C5 tentativa de up" "1" "$(conta 'ip link set dev eth0 up')"
ok "C5 nenhum kill" "0" "$(conta 'podman kill')"
ok "C5 nenhum start (nada foi derrubado)" "0" "$(conta 'podman start')"

# =============================================================== C6
echo "== C6: ExitCode 143 -> start feito, exit 1, linha ExitCode=143 =="
reset_shims
SHIM_EXITCODE=143 roda_shim --duration 60
ok "C6 exit 1" "1" "${RC}"
ok "C6 start feito" "1" "$(conta 'podman start isn2')"
contem "C6 linha ExitCode=143" "isn2 ExitCode=143" "${OUT}"
nao_contem "C6 sem linha OK" "OK: F1" "${OUT}"

# =============================================================== C7
echo "== C7: kill nao termina em 10 s -> exit 5 =="
reset_shims
SHIM_KILL_NOOP=1 roda_shim --duration 60
ok "C7 exit 5" "5" "${RC}"
ok "C7 nao esperou a duracao (sem sleep 60)" "0" "$(conta 'sleep 60')"
ok "C7 nao deu start" "0" "$(conta 'podman start')"
verdade "C7 consultou o estado varias vezes (poll)" test "$(conta 'podman inspect -f {{.State.Status}} isn2')" -ge 10

# =============================================================== C8
echo "== C8: podman start falha -> exit 5 =="
reset_shims
SHIM_START_FAIL=1 roda_shim --duration 60
ok "C8 exit 5" "5" "${RC}"
ok "C8 start tentado" "1" "$(conta 'podman start isn2')"
nao_contem "C8 sem linha OK" "OK: F1" "${OUT}"

# =============================================================== C9
echo "== C9: TERM depois do isolamento e antes do kill -> kill e start, exit 143 =="
reset_shims
export SHIM_BLOCK_VERIFY="${TMP}/libera"
PATH="${PATH_SHIMS}" bash "${CRASH}" --duration 60 > "${TMP}/saida" 2>&1 &
BGPID=$!
verdade "C9 chegou a verificacao do isolamento" esperar_arquivo "${TMP}/libera.esperando" 30
ok "C9 interface ja esta DOWN" "1" "$(ls "${SHIM_STATE}"/link-4002 2>/dev/null | wc -l | tr -d ' ')"
ok "C9 kill ainda nao chamado" "0" "$(conta 'podman kill')"
kill -TERM "${BGPID}" 2>/dev/null
"${REAL_SLEEP}" 0.3
: > "${TMP}/libera"
wait "${BGPID}" 2>/dev/null; RC=$?
unset SHIM_BLOCK_VERIFY
ok "C9 exit 143" "143" "${RC}"
verdade "C9 kill chamado pelo trap" test "$(conta 'podman kill --signal KILL isn2')" -ge 1
ok "C9 start chamado uma vez" "1" "$(conta 'podman start isn2')"
ok "C9 sem espera da duracao" "0" "$(conta 'sleep 60')"
ok "C9 isn2 de volta a running" "running" "$(cat "${SHIM_STATE}/status-isn2")"

# =============================================================== C10
echo "== C10: TERM durante a espera -> start uma vez, exit 143, sem esperar a duracao =="
reset_shims
export SHIM_SLEEP_REAL=1
PATH="${PATH_SHIMS}" bash "${CRASH}" --duration 30 > "${TMP}/saida" 2>&1 &
BGPID=$!
verdade "C10 chegou a espera" esperar_log "sleep 30" 30
"${REAL_SLEEP}" 0.3
T0=$SECONDS
kill -TERM "${BGPID}" 2>/dev/null
wait "${BGPID}" 2>/dev/null; RC=$?
unset SHIM_SLEEP_REAL
ok "C10 exit 143" "143" "${RC}"
ok "C10 saiu rapido (nao esperou os 30 s)" "1" "$(( (SECONDS - T0) < 10 ? 1 : 0 ))"
ok "C10 start chamado uma vez" "1" "$(conta 'podman start isn2')"
ok "C10 kill chamado uma vez (ja estava morto)" "1" "$(conta 'podman kill')"
ok "C10 isn2 de volta a running" "running" "$(cat "${SHIM_STATE}/status-isn2")"
contem "C10 trap registrou o que fez" "trap" "$(cat "${TMP}/saida")"

# =============================================================== C11
echo "== C11: carimbo por linha (AAAA-MM-DDTHH:MM:SS.mmmZ), recalculado a cada linha =="
reset_shims
export SHIM_SLEEP_REAL=1
OUT="$(PATH="${PATH_SHIMS}" bash "${CRASH}" --duration 2 2>&1)"; RC=$?
unset SHIM_SLEEP_REAL
ok "C11 exit 0" "0" "${RC}"
TS_ESPERA="$(printf '%s\n' "${OUT}" | grep -F 'aguardando 2s' | head -1 | sed -n 's/^\[\([^]]*\)\].*/\1/p')"
TS_START="$(printf '%s\n' "${OUT}" | grep -F 'start isn2' | head -1 | sed -n 's/^\[\([^]]*\)\].*/\1/p')"
FMT='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z$'
verdade "C11 carimbo da espera no formato com ms ('${TS_ESPERA}')" test -n "$(printf '%s' "${TS_ESPERA}" | grep -E "${FMT}")"
verdade "C11 carimbo do start no formato com ms ('${TS_START}')" test -n "$(printf '%s' "${TS_START}" | grep -E "${FMT}")"
verdade "C11 carimbos diferentes antes e depois da espera real de 2 s" test -n "${TS_ESPERA}" -a "${TS_ESPERA}" != "${TS_START}"
ok "C11 todas as linhas com carimbo no formato" "0" \
   "$(printf '%s\n' "${OUT}" | grep '^\[' | sed -n 's/^\[\([^]]*\)\].*/\1/p' | grep -vcE "${FMT}")"

# =============================================================== C12
echo "== C12: --dry-run sem podman/sudo/nsenter/ip -> exit 0 e plano =="
rm -f "${PROIBIDO}/chamadas"
OUT="$(PATH="${PROIBIDO}:${PATH_SEM_PODMAN}" bash "${CRASH}" --dry-run 2>&1)"; RC=$?
ok "C12 exit 0" "0" "${RC}"
contem "C12 plano com isolamento" "nsenter -t <pid> -n ip link set dev eth0 down" "${OUT}"
contem "C12 plano com kill" "kill --signal KILL isn2" "${OUT}"
contem "C12 plano com start" "start isn2" "${OUT}"
contem "C12 plano com modo" "modo=silencioso" "${OUT}"
contem "C12 nada executado" "dry-run: nada executado" "${OUT}"
ok "C12 nenhum podman/sudo/nsenter/ip chamado" "0" "$(cat "${PROIBIDO}/chamadas" 2>/dev/null | wc -l | tr -d ' ')"
OUT="$(PATH="${PROIBIDO}:${PATH_SEM_PODMAN}" bash "${CRASH}" --dry-run --modo kill 2>&1)"; RC=$?
ok "C12b --modo kill --dry-run exit 0" "0" "${RC}"
nao_contem "C12b modo kill sem isolamento no plano" "link set" "${OUT}"
contem "C12b modo kill com kill" "kill --signal KILL isn2" "${OUT}"
OUT="$(PATH="${PATH_SEM_PODMAN}" bash "${CRASH}" --help 2>&1)"; RC=$?
ok "C12c --help exit 0" "0" "${RC}"
for f in --node --duration --modo --iface --dry-run --help PODMAN FD_SOCK2 FD_ALL3; do
    contem "C12c --help documenta ${f}" "${f}" "${OUT}"
done

# =============================================================== C13
echo "== C13: modo kill (sem readlink/ip) e modo stop (comportamento de junho) =="
reset_shims
roda_shim --modo kill --duration 60
ok "C13a modo kill exit 0" "0" "${RC}"
ok "C13a sem readlink" "0" "$(conta 'readlink')"
ok "C13a sem ip" "0" "$(conta 'ip ')"
ordem_exata "C13a ordem kill, ExitCode, sleep, start" \
    "podman kill --signal KILL isn2" "podman inspect -f {{.State.ExitCode}} isn2" "sleep 60" "podman start isn2"
contem "C13a linha modo=kill" "modo=kill" "${OUT}"
reset_shims
roda_shim --modo stop --duration 60
ok "C13b modo stop exit 0" "0" "${RC}"
ok "C13b sem readlink" "0" "$(conta 'readlink')"
ok "C13b sem ip" "0" "$(conta 'ip ')"
ok "C13b sem kill" "0" "$(conta 'podman kill')"
ordem_exata "C13b ordem stop, sleep, start" "podman stop isn2" "sleep 60" "podman start isn2"
contem "C13b linha modo=stop" "modo=stop" "${OUT}"

# =============================================================== C14
echo "== C14: argumentos invalidos -> exit 2 =="
bash "${CRASH}" --dry-run --modo x > /dev/null 2>&1; RC=$?
ok "C14a --modo x" "2" "${RC}"
bash "${CRASH}" --dry-run --duration 0 > /dev/null 2>&1; RC=$?
ok "C14b --duration 0" "2" "${RC}"
bash "${CRASH}" --dry-run --iface "" > /dev/null 2>&1; RC=$?
ok "C14c --iface vazio" "2" "${RC}"
bash "${CRASH}" --dry-run --iface 'eth0;rm' > /dev/null 2>&1; RC=$?
ok "C14d --iface com caractere invalido" "2" "${RC}"
bash "${CRASH}" --dry-run --modo > /dev/null 2>&1; RC=$?
ok "C14e --modo sem valor" "2" "${RC}"
bash "${CRASH}" --foo > /dev/null 2>&1; RC=$?
ok "C14f argumento desconhecido" "2" "${RC}"

# =============================================================== C15
echo "== C15: log com as linhas que K6 procura =="
reset_shims
OUT="$(PATH="${PATH_SHIMS}" PODMAN="sudo podman" bash "${CRASH}" --node isn2 --duration 60 2>&1)"; RC=$?
ok "C15 exit 0 com PODMAN='sudo podman'" "0" "${RC}"
for re in 'modo=silencioso' 'eth0 down' 'kill isn2' 'ExitCode=137' 'start isn2'; do
    verdade "C15 log casa \\b${re}\\b" test -n "$(printf '%s\n' "${OUT}" | grep -E "(^|[^[:alnum:]_])${re}([^[:alnum:]_]|$)")"
done
contem "C15 primeira linha e o plano" "Plano: NODE=isn2 MODO=silencioso DURACAO=60s IFACE=eth0 DRY_RUN=0" \
    "$(printf '%s\n' "${OUT}" | head -1)"
verdade "C15 linha 'modo=silencioso' isolada" test -n "$(printf '%s\n' "${OUT}" | grep -E ': modo=silencioso$')"

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
