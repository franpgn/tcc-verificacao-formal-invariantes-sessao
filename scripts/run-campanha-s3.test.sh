#!/usr/bin/env bash
#
# run-campanha-s3.test.sh
#
# Testes S1..S16 do scripts/run-campanha-s3.sh (W-6, B-46), no estilo do run-rebaseline.test.sh: sem cluster, sem
# rede, sem VM, sem tc real. Shims em PATH para java, podman, podman-compose, sudo, nsenter, tc, ip, iptables, curl,
# systemd-run, systemctl, flock, pgrep, df, sleep, date e timeout. Relogio acelerado: o shim de sleep dorme s/ESCALA de
# verdade, o de timeout divide os prazos por ESCALA e o de date devolve T0 + (agora - T0) * ESCALA. O injetor de F1 e
# o inject-crash.sh REAL; os de F2 e F4 sao dubles (CS_INJ_PART, CS_INJ_DEGR) que gravam os marcadores e deixam ou
# tiram a regra de rede no estado do shim de tc. O python e o real (campanha_s3_conferir.py, campanha_s3_h4.py sobre
# analysis/h4.py, campanha_s3_matriz.py e cria-caches.sh rodam de verdade).
#
# Uso: bash scripts/run-campanha-s3.test.sh [S1 S4 ...]   (sem argumentos: todos). Exit 0 se todos passarem.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "${DIR}/.." && pwd)"
ORQ="${DIR}/run-campanha-s3.sh"
MATRIZ_PY="${DIR}/campanha_s3_matriz.py"
REAL_PY="$(command -v python3)"
REAL_DATE="$(command -v date)"
REAL_SLEEP="$(command -v sleep)"
REAL_TIMEOUT="$(command -v timeout)"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
SELECAO=" ${*:-S1 S2 S3 S4 S5 S6 S7 S8 S9 S10 S11 S12 S13 S14 S15 S16 S17 S18} "

PASS=0
FAIL=0
ok() {
    if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1)); fi
}
# sem grep -q: com pipefail, o grep -q sai no primeiro acerto e o printf de um texto grande morre de SIGPIPE
contem() {
    if printf '%s\n' "$3" | grep -F -e "$2" > /dev/null; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1)); fi
}
nao_contem() {
    if printf '%s\n' "$3" | grep -F -e "$2" > /dev/null; then echo "FAIL: $1 (encontrou '$2')"; FAIL=$((FAIL + 1))
    else echo "PASS: $1"; PASS=$((PASS + 1)); fi
}
verdade() {
    local d="$1"; shift
    if "$@"; then echo "PASS: ${d}"; PASS=$((PASS + 1)); else echo "FAIL: ${d}"; FAIL=$((FAIL + 1)); fi
}
quer() { [[ "${SELECAO}" == *" $1 "* ]]; }
agora_real_ms() { local r="${EPOCHREALTIME//[.,]/}"; echo "$((10#${r:0:13}))"; }

# ----------------------------------------------------------------------------- shims
criar_shims() {  # criar_shims <dir>
    local SH="$1"
    mkdir -p "${SH}"
    : > "${SH}/.cs-shim"
    echo "0b0b0b0b-1111-2222-3333-444444444444" > "${SH}/boot_id"
    cat > "${SH}/_comum.sh" <<'EOF'
vms() { local r="${EPOCHREALTIME//[.,]/}"; r=$((10#${r:0:13})); echo $(( CS_T0_MS + (r - CS_T0_MS) * CS_ESCALA )); }
registrar() { local IFS='|'; echo "$(vms) $*" >> "${CS_ESTADO}/chamadas.log"; }
dormir() { sleep "$1" & wait $!; }
pid_no() { echo $(( 1000 + ${1#isn} )); }
no_porta() { case "$1" in 11222) echo isn1 ;; 11223) echo isn2 ;; *) echo isn3 ;; esac; }
EOF
    cat > "${SH}/sleep" <<EOF
#!/usr/bin/env bash
s="\$1"; int="\${s%%.*}"; frac="000"; [[ "\$s" == *.* ]] && frac="\${s#*.}000"
ms=\$(( 10#\${int:-0} * 1000 + 10#\${frac:0:3} ))
r=\$(( ms / CS_ESCALA ))
exec "${REAL_SLEEP}" "\$(( r / 1000 )).\$(printf '%03d' \$(( r % 1000 )))"
EOF
    cat > "${SH}/date" <<EOF
#!/usr/bin/env bash
if [[ "\$*" == "+%s%3N" ]]; then source "\${CS_SHIM_DIR}/_comum.sh"; vms; exit 0; fi
exec "${REAL_DATE}" "\$@"
EOF
    cat > "${SH}/timeout" <<EOF
#!/usr/bin/env bash
source "\${CS_SHIM_DIR}/_comum.sh"
registrar timeout "\$@"
esc() { local v="\${1%s}"; awk -v v="\$v" -v e="\${CS_ESCALA}" 'BEGIN { printf "%.3f", v / e }'; }
k=""; if [[ "\$1" == "-k" ]]; then k="\$(esc "\$2")"; shift 2; fi
t="\$(esc "\$1")"; shift
if [[ -n "\$k" ]]; then exec "${REAL_TIMEOUT}" -k "\$k" "\$t" "\$@"; fi
exec "${REAL_TIMEOUT}" "\$t" "\$@"
EOF
    cat > "${SH}/java" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar java "$@"
ST="${CS_ESTADO}"
case "$1" in -version) echo 'openjdk version "21" (shim)' >&2; exit 0 ;; esac
csv=""; seed=""; ctl=off; dry=0; dur=60; cfg=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --csv-dir) csv="$2" ;; --seed) seed="$2" ;; --controller) ctl="$2" ;; --dry-run) dry=1 ;;
        --duration) dur="$2" ;; --controller-config) cfg="$2" ;;
    esac; shift
done
if [[ "${dry}" -eq 1 ]]; then echo "dry-run, configuracao OK (shim ${ctl})"; exit "$(cat "${ST}/java-dry-rc" 2>/dev/null || echo 0)"; fi
rc="$(cat "${ST}/java-exit-${seed}" 2>/dev/null || echo 0)"
trap 'echo "morto $(vms)" >> "${ST}/java-morto-${seed}"; [[ -f "${ST}/java-ignora-term" ]] || exit 143' TERM
mkdir -p "${csv}"
sha="$(sha256sum "${cfg}" | cut -d' ' -f1)"
[[ -f "${ST}/meta-sha" ]] && sha="$(cat "${ST}/meta-sha")"
atu="por-tipo(AC-2=rest-infinispan,AC-5=rest-infinispan)"; [[ -f "${ST}/meta-atuador" ]] && atu="$(cat "${ST}/meta-atuador")"
pol="por-assinatura"; [[ -f "${ST}/meta-politica" ]] && pol="$(cat "${ST}/meta-politica")"
p2=false; [[ -f "${ST}/meta-p2" ]] && p2="$(cat "${ST}/meta-p2")"
printf '{"tipo":"meta","esquema":"decisoes-controlador/1.2","modo":"%s","param_sha256":"%s","atuador":"%s","politica":"%s","parametros_politica":{"k_lag":2,"p2_habilitada":%s}}\n' \
    "${ctl}" "${sha}" "${atu}" "${pol}" "${p2}" > "${csv}/decisoes-rep-001.jsonl"
echo "Configuracao: Cli{scenario=S3, seed=${seed}}"
dormir 1
if [[ ! -f "${ST}/java-sem-ancora" ]]; then
    echo "Warm-up ate +30s; medicao a partir de +44s; total ${dur}s"
    vms > "${ST}/ancora-${seed}"
fi
dormir "$(( dur / 2 ))"
alvo="-"
if [[ "${ctl}" == active && -f "${ST}/ac5-alvo" ]]; then
    alvo="$(cat "${ST}/ac5-alvo")"; no="$(no_porta "${alvo##*:}")"
    echo exited > "${ST}/status-${no}"; echo parou >> "${ST}/parou-${no}"
    printf '{"tipo":"acao","operacao":"APLICAR","executada":true,"acao":{"tipo":"AC-5","caches":["sessions-async"],"alvo":"%s","valor":null},"resultado":"APLICADA"}\n' "${alvo}" >> "${csv}/decisoes-rep-001.jsonl"
fi
[[ -f "${ST}/sinal-alvo" ]] && alvo="$(cat "${ST}/sinal-alvo")"
dormir "$(( dur - dur / 2 ))"
[[ -f "${ST}/java-pendura" ]] && dormir 100000
echo '{"tipo":"fim"}' >> "${csv}/decisoes-rep-001.jsonl"
if [[ "${ctl}" != off && ! -f "${ST}/java-sem-sinal" ]]; then
    echo "Fim da carga: rep=1 t_fim_carga_ns=$(vms)000000 ac5_alvo=${alvo}"
fi
relig=null
if [[ "${alvo}" != "-" ]]; then
    no="$(no_porta "${alvo##*:}")"
    for k in $(seq 1 60); do [[ "$(cat "${ST}/status-${no}")" == running ]] && { relig="$(vms)000000"; break; }; dormir 1; done
    echo "${relig}" > "${ST}/relig-visto-${seed}"
fi
dormir 2
echo "op_id,operation,start_ns,end_ns,replica,return_code,key" > "${csv}/rep-001.csv"
printf '{\n  "t_fim_carga_ns" : 1,\n  "t_religamento_ns" : %s\n}\n' "${relig}" > "${csv}/convergencia-resumo-rep-001.json"
echo "Total de operacoes: 1"
exit "${rc}"
EOF
    cat > "${SH}/podman" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar podman "$@"
ST="${CS_ESTADO}"
cmd="$1"; shift
case "${cmd}" in
    inspect)
        fmt=""; if [[ "$1" == "-f" ]]; then fmt="$2"; shift 2; fi
        no="$1"
        est="$(cat "${ST}/status-${no}" 2>/dev/null || echo running)"
        [[ "${est}" == ausente ]] && exit 125
        case "${fmt}" in
            *State.Status*) echo "${est}" ;;
            *State.Pid*) if [[ "${est}" == running ]]; then pid_no "${no}"; else echo 0; fi ;;
            *StartedAt.UnixMilli*) cat "${ST}/iniciado-ms-${no}" 2>/dev/null || echo 1000 ;;
            *FinishedAt.UnixMilli*) cat "${ST}/parado-ms-${no}" 2>/dev/null || echo 0 ;;
            *State.ExitCode*) cat "${ST}/exitcode-${no}" 2>/dev/null || echo 0 ;;
            *) echo '[{}]' ;;
        esac ;;
    kill) no="$1"; echo exited > "${ST}/status-${no}"; vms > "${ST}/parado-ms-${no}"; echo 137 > "${ST}/exitcode-${no}"; echo parou >> "${ST}/parou-${no}" ;;
    start)
        no="$1"
        [[ -f "${ST}/start-falha-${no}" ]] && { echo "Error: start (shim)" >&2; exit 125; }
        [[ -f "${ST}/start-nao-volta-${no}" ]] && exit 0
        if [[ "$(cat "${ST}/status-${no}" 2>/dev/null || echo running)" != running ]]; then vms > "${ST}/iniciado-ms-${no}"; fi
        echo running > "${ST}/status-${no}"; rm -f "${ST}/link-$(pid_no "${no}")"; echo "${no}" ;;
    version) echo "Version: 5.0.0 (shim)" ;;
    *) exit 0 ;;
esac
EOF
    cat > "${SH}/podman-compose" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar podman-compose "$@"
ST="${CS_ESTADO}"
acao="${@: -1}"; [[ "${acao}" == "-d" ]] && acao=up
case "${acao}" in
    down) for n in isn1 isn2 isn3; do echo ausente > "${ST}/status-${n}"; done
          rm -rf "${ST}/caches"; rm -f "${ST}"/netem-* "${ST}"/clsact-* "${ST}"/prio-* "${ST}"/link-* ;;
    up) [[ -f "${ST}/compose-up-falha" ]] && exit 1
        t="$(vms)"; [[ -f "${ST}/started-velho" ]] && t=1000
        for n in isn1 isn2 isn3; do echo running > "${ST}/status-${n}"; echo "${t}" > "${ST}/iniciado-ms-${n}"; done ;;
esac
exit 0
EOF
    cat > "${SH}/sudo" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar sudo "$@"
ST="${CS_ESTADO}"
[[ "$1" == "-n" ]] && shift
if [[ "$1" == "true" ]]; then exit 0; fi
if [[ "$1" == "readlink" ]]; then
    p="${2#/proc/}"; pid="${p%%/*}"
    if [[ "${pid}" == 1 || -f "${ST}/netns-host-${pid}" ]]; then echo "net:[4026531840]"; else echo "net:[40265${pid}]"; fi
    exit 0
fi
exec "$@"
EOF
    cat > "${SH}/readlink" <<'EOF'
#!/usr/bin/env bash
p="${1#/proc/}"; pid="${p%%/*}"
if [[ "${pid}" == 1 || -f "${CS_ESTADO}/netns-host-${pid}" ]]; then echo "net:[4026531840]"; else echo "net:[40265${pid}]"; fi
EOF
    cat > "${SH}/nsenter" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar nsenter "$@"
pid=""
while [[ $# -gt 0 ]]; do case "$1" in -t) pid="$2"; shift 2 ;; -n) shift ;; *) break ;; esac; done
export CS_NETNS_PID="${pid}"
exec "$@"
EOF
    cat > "${SH}/tc" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar tc "pid=${CS_NETNS_PID:-?}" "$@"
ST="${CS_ESTADO}"; p="${CS_NETNS_PID:-0}"
case "$*" in
    "qdisc show dev eth0")
        out="qdisc noqueue 0: root refcnt 2"
        [[ -f "${ST}/clsact-${p}" ]] && out+=$'\n'"qdisc clsact ffff: parent ffff:fff1"
        [[ -f "${ST}/prio-${p}" ]] && out=$'qdisc prio 1: root refcnt 2 bands 3\nqdisc netem 30: parent 1:3 limit 1000 loss 20%'
        echo "${out}" ;;
esac
EOF
    cat > "${SH}/ip" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar ip "pid=${CS_NETNS_PID:-?}" "$@"
f="${CS_ESTADO}/link-${CS_NETNS_PID:-0}"
mostrar() {
    if [[ -f "${f}" ]]; then echo "2: eth0@if7: <BROADCAST,MULTICAST> mtu 1500 qdisc noqueue state DOWN mode DEFAULT"
    else echo "2: eth0@if7: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT"; fi
}
case "$*" in
    "link show dev eth0"|"-o link show dev eth0") mostrar ;;
    "link set dev eth0 down") : > "${f}" ;;
    "link set dev eth0 up") rm -f "${f}" ;;
    *) exit 1 ;;
esac
EOF
    cat > "${SH}/iptables" <<'EOF'
#!/usr/bin/env bash
printf -- '-P INPUT ACCEPT\n-P FORWARD ACCEPT\n-P OUTPUT ACCEPT\n'
[[ -f "${CS_ESTADO}/iptables-residuo-${CS_NETNS_PID:-0}" ]] && echo "-A INPUT -s 10.89.0.3/32 -j DROP"
exit 0
EOF
    cat > "${SH}/curl" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
ST="${CS_ESTADO}"
out=""; fmt=""; metodo=GET; dados=""; url=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -o) out="$2"; shift 2 ;; -w) fmt="$2"; shift 2 ;; -X) metodo="$2"; shift 2 ;;
        --data-binary) dados="${2#@}"; shift 2 ;; -K) shift 2 ;; -H|--max-time) shift 2 ;;
        -*) shift ;; *) url="$1"; shift ;;
    esac
done
cat > /dev/null
registrar curl "${metodo}" "${url}"
porta="${url#*://*:}"; porta="${porta%%/*}"; alvo="$(no_porta "${porta}")"
codigo() { [[ -n "${fmt}" ]] && printf '%s' "$1"; }
corpo() { if [[ -n "${out}" ]]; then printf '%s' "$1" > "${out}"; else printf '%s' "$1"; fi; }
if [[ "$(cat "${ST}/status-${alvo}" 2>/dev/null || echo running)" != running ]]; then codigo 000; exit 7; fi
caminho="${url#*://*/}"
case "${caminho}" in
    rest/v2/cache-managers/default/health)
        n=0; for x in isn1 isn2 isn3; do [[ "$(cat "${ST}/status-${x}" 2>/dev/null || echo running)" == running ]] && n=$((n + 1)); done
        [[ -f "${ST}/saude-nunca-volta" && -f "${ST}/parou-isn2" ]] && n=2
        [[ -f "${ST}/saude-ruim" ]] && n=2
        st=HEALTHY; [[ "${n}" == 3 ]] || st=DEGRADED
        corpo "{\"cluster_health\":{\"cluster_name\":\"c\",\"health_status\":\"${st}\",\"number_of_nodes\":${n},\"node_names\":[]},\"cache_health\":[]}"
        codigo 200 ;;
    rest/v2/container)
        v=true; [[ -f "${ST}/rebal-off" ]] && v=false
        corpo "{\"version\":\"15.0.21.Final\",\"rebalancing_enabled\":${v}}"; codigo 200 ;;
    rest/v2/caches/*\?action=get-availability)
        corpo "$(cat "${ST}/disponibilidade" 2>/dev/null || echo AVAILABLE)"; codigo 200 ;;
    rest/v2/caches/*\?action=config)
        c="${caminho#rest/v2/caches/}"; c="${c%%\?*}"
        if [[ ! -f "${ST}/caches/${c}.json" ]]; then corpo "nao existe"; codigo 404; exit 0; fi
        b="$(cat "${ST}/caches/${c}.json")"
        [[ -f "${ST}/config-diverge-${c}" ]] && b="${b/17500/15000}"
        corpo "{\"${c}\":${b}}"; codigo 200 ;;
    rest/v2/caches/*)
        c="${caminho#rest/v2/caches/}"
        if [[ "${metodo}" == POST ]]; then
            mkdir -p "${ST}/caches"; tr -d '\r\n' < "${dados}" > "${ST}/caches/${c}.json"; corpo ""; codigo 200
        else corpo "{}"; codigo 200; fi ;;
    *) corpo "?"; codigo 404 ;;
esac
exit 0
EOF
    cat > "${SH}/systemd-run" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar systemd-run "$@"
ST="${CS_ESTADO}"
[[ -f "${ST}/systemd-run-falha" ]] && exit 1
u=""; for a in "$@"; do [[ "${a}" == --unit=* ]] && u="${a#--unit=}"; done
: > "${ST}/timer-${u}"
EOF
    cat > "${SH}/systemctl" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar systemctl "$@"
ST="${CS_ESTADO}"
case "$1" in
    stop) u="${2%.timer}"; [[ -f "${ST}/desarme-falha" ]] || rm -f "${ST}/timer-${u}" ;;
    list-timers) for f in "${ST}"/timer-*; do [[ -e "${f}" ]] && echo "n/a ${f##*/timer-}.timer ${f##*/timer-}.service"; done ;;
    is-active) [[ -f "${ST}/h4-inativa" ]] && exit 3; exit 0 ;;
esac
exit 0
EOF
    cat > "${SH}/flock" <<'EOF'
#!/usr/bin/env bash
conf=1
while [[ $# -gt 0 && "$1" == -* ]]; do case "$1" in -E) conf="$2"; shift 2 ;; *) shift ;; esac; done
shift
[[ -f "${CS_ESTADO}/lock-ocupado" ]] && exit "${conf}"
exec "$@"
EOF
    cat > "${SH}/pgrep" <<'EOF'
#!/usr/bin/env bash
echo 4242
EOF
    cat > "${SH}/df" <<'EOF'
#!/usr/bin/env bash
echo "Filesystem 1048576-blocks Used Available Capacity Mounted on"
echo "/dev/mapper/vg 28000 1000 $(cat "${CS_ESTADO}/df-livre" 2>/dev/null || echo 100000) 5% /"
EOF
    cat > "${SH}/coletor-falso.sh" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar coletor "$@"
saida=""; while [[ $# -gt 0 ]]; do [[ "$1" == --saida ]] && saida="$2"; shift; done
echo "t_epoch_ns,fonte,cpu_usec_acum,mem_bytes,steal_usec_acum,estado" > "${saida}/recursos-rep-001.csv"
trap 'echo "{\"fim\":true}" > "${saida}/recursos-meta-rep-001.json"; exit 0' TERM
while :; do dormir 1; done
EOF
    cat > "${SH}/inject-partition-falso.sh" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar inject-partition "$@"
ST="${CS_ESTADO}"
heal=0; no=""; dur=0; marker=""
while [[ $# -gt 0 ]]; do
    case "$1" in --heal) heal=1; shift ;; --minority) no="$2"; shift 2 ;; --duration) dur="$2"; shift 2 ;;
        --marker) marker="$2"; shift 2 ;; *) shift ;; esac
done
if [[ "${heal}" -eq 1 ]]; then [[ -f "${ST}/heal-falha" ]] && exit 1; rm -f "${ST}"/clsact-*; exit 0; fi
p="$(pid_no "${no}")"
echo "fault=F2 node=${no} event=start t_epoch_ns=$(vms)000000" >> "${marker}"
: > "${ST}/clsact-${p}"
trap 'rm -f "${ST}/clsact-${p}"; echo "fault=F2 node=${no} event=end t_epoch_ns=$(vms)000000" >> "${marker}"; exit 143' TERM
dormir "${dur}"
[[ -f "${ST}/del-falha" ]] || rm -f "${ST}/clsact-${p}"
echo "fault=F2 node=${no} event=end t_epoch_ns=$(vms)000000" >> "${marker}"
exit "$(cat "${ST}/inj-rc" 2>/dev/null || echo 0)"
EOF
    cat > "${SH}/inject-netdegrade-falso.sh" <<'EOF'
#!/usr/bin/env bash
source "${CS_SHIM_DIR}/_comum.sh"
registrar inject-netdegrade "$@"
ST="${CS_ESTADO}"
heal=0; no=""; dur=0; marker=""
while [[ $# -gt 0 ]]; do
    case "$1" in --heal) heal=1; shift ;; --target) no="$2"; shift 2 ;; --duration) dur="$2"; shift 2 ;;
        --marker) marker="$2"; shift 2 ;; *) shift ;; esac
done
if [[ "${heal}" -eq 1 ]]; then [[ -f "${ST}/heal-falha" ]] && exit 1; rm -f "${ST}"/prio-*; exit 0; fi
echo "fault=F4 node=${no} event=start t_epoch_ns=$(vms)000000" >> "${marker}"
for n in isn1 isn2 isn3; do : > "${ST}/prio-$(pid_no "${n}")"; done
dormir "${dur}"
[[ -f "${ST}/del-falha" ]] || rm -f "${ST}"/prio-*
echo "fault=F4 node=${no} event=end t_epoch_ns=$(vms)000000" >> "${marker}"
exit "$(cat "${ST}/inj-rc" 2>/dev/null || echo 0)"
EOF
    chmod +x "${SH}"/*
}

# ----------------------------------------------------------------------------- caso
H4_ID="calibracao-20261107T120000Z"
novo_caso() {  # novo_caso <nome> [escala]
    CASO="${TMP}/$1"
    SH="${CASO}/shims"; ST="${CASO}/estado"; OUT="${CASO}/saida"; H4R="${CASO}/run"
    mkdir -p "${ST}" "${OUT}" "${H4R}/${H4_ID}"
    : > "${H4R}/.cs-shim"
    criar_shims "${SH}"
    JAR="${CASO}/workload.jar"; echo "jar falso $1" > "${JAR}"; JAR_SHA="$(sha256sum "${JAR}" | cut -d' ' -f1)"
    export CS_SHIM_DIR="${SH}" CS_ESTADO="${ST}" CS_ESCALA="${2:-20}" CS_T0_MS="$(agora_real_ms)"
    export CS_MODO_TESTE=1 CS_TTY_ARQUIVO="${ST}/tty" CS_PASSWORD="segredo" PODMAN="sudo podman" \
        PODMAN_COMPOSE="sudo podman-compose" JAVA="java" CS_H4_RUN="${H4R}" CS_COLETOR="${SH}/coletor-falso.sh" \
        CS_INJ_PART="${SH}/inject-partition-falso.sh" CS_INJ_DEGR="${SH}/inject-netdegrade-falso.sh"
    export PATH_TESTE="${SH}:${PATH}"
    : > "${ST}/chamadas.log"
    HOJE="$("${REAL_DATE}" +%F)"
    BOOT="$(cat "${SH}/boot_id")"
    parametros
    controlador
    amostras
}

parametros() {  # parametros [chave=valor ...]: arquivo campanha-s3/1 com origem "teste" em tudo
    "${REAL_PY}" -I - "${CASO}/parametros.json" "$@" <<'PY'
import json, sys
v = {"duracao_s": 60, "warmup_min_s": 30, "inj_offset_s": 10, "f1_no": "isn2", "f1_dur_s": 20, "f2_no": "isn3",
     "f2_dur_s": 20, "f4_no": "isn3", "f4_severidade": "N3", "f4_direcao": "both", "f4_dur_s": 20,
     "hotrod_timeout_ms": 2000, "convergencia_teto_s": 60, "convergencia_periodo_s": 5, "w_recovery_s": 30,
     "religamento_prazo_s": 30, "exec_teto_s": 200, "encerramento_folga_s": 2, "volume_exec_mb": 10}
for kv in sys.argv[2:]:
    k, x = kv.split("=", 1)
    v[k] = int(x) if x.lstrip("-").isdigit() else x
obj = {"esquema": "campanha-s3/1", "versao": "teste",
       "parametros": {k: {"valor": x, "origem": "teste"} for k, x in v.items()}}
open(sys.argv[1], "w", encoding="utf-8").write(json.dumps(obj))
PY
    PARAMS="${CASO}/parametros.json"; PARAMS_SHA="$(sha256sum "${PARAMS}" | cut -d' ' -f1)"
}

controlador() {  # controlador [sem-politica]
    if [[ "${1:-}" == sem-politica ]]; then
        echo '{"versao":"t","wNs":5000000000,"defasagem":{"lagInMs":2000,"lagOutMs":1000,"passoLeituraMs":20,"origem":{}}}' > "${CASO}/controlador.json"
    else
        echo '{"versao":"t","wNs":5000000000,"politica":{"kLag":2,"tetoLagMs":2000,"tRebNs":60000000000,"cotaAc5":1,"p2Habilitada":false,"origem":{}},"defasagem":{"lagInMs":2000,"lagOutMs":1000,"passoLeituraMs":20,"origem":{}}}' > "${CASO}/controlador.json"
    fi
    CTRL="${CASO}/controlador.json"; CTRL_SHA="$(sha256sum "${CTRL}" | cut -d' ' -f1)"
    echo '{"esquema":"referencia-p99/1"}' > "${CASO}/referencia.json"
    REF="${CASO}/referencia.json"; REF_SHA="$(sha256sum "${REF}" | cut -d' ' -f1)"
}

amostras() {  # amostras [io_full_por_amostra_s]: arquivo do amostrador cobrindo o tempo virtual do caso
    "${REAL_PY}" -I - "${H4R}/${H4_ID}/amostras.txt" "${CS_T0_MS}" "${1:-0}" <<'PY'
import sys
arq, t0_ms, degrau = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
linhas = ["# tcc-amostrador-infra v1 id=calibracao-20261107T120000Z inicio=2026-11-07T12:00:00+00:00 boot_id=b "
          "host=vm disco=sda periodo_s=1 pid=1 versao_script_sha256=%s" % ("0" * 64)]
full = 10 * 10**6
for k in range(-200, 30000):
    t = t0_ms * 1000 + k * 10**6
    full += int(degrau * 10**6)
    linhas.append("%d.%06d %d.000000 io 7 %d cpu 3 - mem 0 0 sda %s"
                  % (t // 10**6, t % 10**6, 1000 + k + 200, full, " ".join(["1"] * 17)))
open(arq, "w", encoding="ascii", newline="\n").write("\n".join(linhas) + "\n")
PY
}

matriz_calib() {  # matriz_calib "<CELULA:calibracao:N>,..."
    "${REAL_PY}" -I "${MATRIZ_PY}" gerar --modo calibracao --celulas "$1" --ordem-seed 7 --s0 60000 \
        --passo-semente 100 --i0 0 --saida "${CASO}/matriz.tsv" > /dev/null
    MAT="${CASO}/matriz.tsv"; MAT_SHA="$(sha256sum "${MAT}" | cut -d' ' -f1)"
}
matriz_aval() {  # matriz_aval "<CELULA:teste:N>,..."
    "${REAL_PY}" -I "${MATRIZ_PY}" gerar --modo avaliacao --nucleo "$1" --ordem-seed 7 --s0 70000 \
        --passo-semente 100 --i0 0 --b0 1 --saida "${CASO}/matriz.tsv" > /dev/null
    MAT="${CASO}/matriz.tsv"; MAT_SHA="$(sha256sum "${MAT}" | cut -d' ' -f1)"
}

rodar() {  # rodar <fase> [args extras]: RC e SAIDA_TXT
    local fase="$1"; shift
    SAIDA_TXT="$(PATH="${PATH_TESTE}" bash "${ORQ}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --fase "${fase}" \
        --parametros "${PARAMS}" --parametros-sha256 "${PARAMS_SHA}" --controlador "${CTRL}" --controlador-sha256 "${CTRL_SHA}" \
        --referencia "${REF}" --referencia-sha256 "${REF_SHA}" --jar "${JAR}" --jar-sha256 "${JAR_SHA}" --saida "${OUT}" \
        --boot-esperado "${BOOT}" --h4-id "${H4_ID}" "$@" 2>&1)"; RC=$?
}
rodar_fundo() {  # como rodar, em segundo plano; PID em BG
    local fase="$1"; shift
    PATH="${PATH_TESTE}" bash "${ORQ}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --fase "${fase}" \
        --parametros "${PARAMS}" --parametros-sha256 "${PARAMS_SHA}" --controlador "${CTRL}" --controlador-sha256 "${CTRL_SHA}" \
        --referencia "${REF}" --referencia-sha256 "${REF_SHA}" --jar "${JAR}" --jar-sha256 "${JAR_SHA}" --saida "${OUT}" \
        --boot-esperado "${BOOT}" --h4-id "${H4_ID}" "$@" > "${CASO}/out.txt" 2>&1 &
    BG=$!
}
chamadas() { cat "${ST}/chamadas.log"; }
linha_de() { local n; n="$(grep -nF -e "$1" "${ST}/chamadas.log" | sed -n "${2:-1}p" | cut -d: -f1)"; echo "${n:-0}"; }
conta() { grep -cF -e "$1" "${ST}/chamadas.log"; }
cargas() { grep -F " java|-jar|" "${ST}/chamadas.log" | grep -vcF -- "--dry-run"; }
json() { "${REAL_PY}" -c "import json,sys; d=json.load(open(sys.argv[1], encoding='utf-8')); print(eval(sys.argv[2]))" "$1" "$2"; }
exec_dir() { ls -d "${OUT}"/exec-"$(printf '%04d' "$1")"-* 2>/dev/null | grep -v abortada | head -1; }
log_fase() { cat "${OUT}/$1/orquestrador.log" 2>/dev/null; }
esperar_por() { local k; for ((k = 0; k < $2 * 10; k++)); do grep -qF -e "$1" "${ST}/chamadas.log" && return 0; "${REAL_SLEEP}" 0.1; done; return 1; }

# ============================================================================= S1
if quer S1; then
echo "== S1: --dry-run da calibracao e da avaliacao: plano, timeout -k 23s (21 + 2), nada executado =="
novo_caso s1
matriz_calib "ASYNC-none:calibracao:1,ASYNC-F1:calibracao:1,ASYNC-F2:calibracao:1"
OUT1="$(PATH="${PATH_TESTE}" bash "${ORQ}" --dry-run --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --fase calibracao \
    --parametros "${PARAMS}" --parametros-sha256 "${PARAMS_SHA}" --controlador "${CTRL}" --controlador-sha256 "${CTRL_SHA}" \
    --referencia "${REF}" --referencia-sha256 "${REF_SHA}" --saida "${OUT}" 2>&1)"; RC=$?
ok "S1 calibracao exit 0" "0" "${RC}"
ok "S1 calibracao: 3 linhas PLANO" "3" "$(grep -c '^PLANO i=' <<< "${OUT1}")"
contem "S1 timeout -k (T_enc pior caso 21 s + folga 2 s) e teto" "timeout -k 23s 200s java -jar" "${OUT1}"
contem "S1 comando S3 com controlador shadow, religamento e convergencia" "--controller shadow --controller-config ${CTRL} --controller-reference ${REF}" "${OUT1}"
contem "S1 --rejoin-timeout-sec do parametro" "--rejoin-timeout-sec 30" "${OUT1}"
contem "S1 F2 com dead-man de 20 + 90 s" "dead-man 110 s" "${OUT1}"
contem "S1 senha mascarada" "--password ***" "${OUT1}"
nao_contem "S1 senha nao aparece" "segredo" "${OUT1}"
ok "S1 nenhuma chamada a java/podman/sudo/curl" "0" "$(grep -cE ' (java|podman|podman-compose|sudo|curl|systemd-run)([|]|$)' "${ST}/chamadas.log")"
matriz_aval "ASYNC-F2:teste:2"
OUT2="$(PATH="${PATH_TESTE}" bash "${ORQ}" --dry-run --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --fase nucleo \
    --parametros "${PARAMS}" --parametros-sha256 "${PARAMS_SHA}" --controlador "${CTRL}" --controlador-sha256 "${CTRL_SHA}" \
    --referencia "${REF}" --referencia-sha256 "${REF_SHA}" --saida "${OUT}" 2>&1)"; RC=$?
ok "S1 avaliacao exit 0" "0" "${RC}"
ok "S1 avaliacao: 2 blocos x 2 bracos" "4" "$(grep -c '^PLANO i=' <<< "${OUT2}")"
ok "S1 avaliacao: 2 active e 2 shadow" "2 2" "$(grep -c 'controller=active' <<< "${OUT2}") $(grep -c 'controller=shadow' <<< "${OUT2}")"
OUT3="$(PATH="${PATH_TESTE}" bash "${ORQ}" --dry-run --matriz "${MAT}" --matriz-sha256 "$(printf '0%.0s' {1..64})" --fase nucleo \
    --parametros "${PARAMS}" --parametros-sha256 "${PARAMS_SHA}" --controlador "${CTRL}" --controlador-sha256 "${CTRL_SHA}" \
    --referencia "${REF}" --referencia-sha256 "${REF_SHA}" --saida "${OUT}" 2>&1)"; RC=$?
ok "S1 SHA-256 errado da matriz -> exit 4" "4" "${RC}"
if [[ -n "${CS_TESTE_GUARDAR:-}" ]]; then printf '%s\n' "${OUT1}" > "${CS_TESTE_GUARDAR}/dry-run-calibracao.txt"; printf '%s\n' "${OUT2}" > "${CS_TESTE_GUARDAR}/dry-run-avaliacao.txt"; fi
fi

# ============================================================================= S2
if quer S2; then
echo "== S2: parametro sem origem, boot diferente, lock ocupado e controlador sem politica no braco B -> exit 4 =="
novo_caso s2
matriz_calib "ASYNC-none:calibracao:1"
"${REAL_PY}" -I -c "import json,sys; p=sys.argv[1]; o=json.load(open(p)); o['parametros']['inj_offset_s']['origem']=''; open(p,'w').write(json.dumps(o))" "${PARAMS}"
PARAMS_SHA="$(sha256sum "${PARAMS}" | cut -d' ' -f1)"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S2 parametro sem origem -> exit 4" "4" "${RC}"
contem "S2 motivo cita o parametro" "inj_offset_s" "${SAIDA_TXT}"
ok "S2 nenhum java" "0" "$(cargas)"
parametros
rodar calibracao --boot-esperado "ffffffff-1111-2222-3333-444444444444"
ok "S2 boot diferente -> exit 4" "4" "${RC}"
: > "${ST}/lock-ocupado"; rodar calibracao; rm -f "${ST}/lock-ocupado"
ok "S2 lock ocupado -> exit 4" "4" "${RC}"
matriz_aval "ASYNC-F2:teste:1"
controlador sem-politica
rodar nucleo
ok "S2 braco B sem bloco politica -> exit 4" "4" "${RC}"
contem "S2 motivo politica" "politica" "${SAIDA_TXT}"
ok "S2 nenhum compose" "0" "$(conta 'podman-compose|')"
fi

# ============================================================================= S3
if quer S3; then
echo "== S3: GO da fase e GO de rede (texto exato por tty, como no runner do rebaseline) =="
novo_caso s3
matriz_calib "ASYNC-none:calibracao:1,ASYNC-F2:calibracao:1"
printf 'go calibracao 1999-01-01\n' > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S3 GO da fase errado -> exit 5" "5" "${RC}"
ok "S3 sem compose nem java" "0" "$(( $(conta 'podman-compose|') + $(cargas) ))"
verdade "S3 gates.json registra o recusado" grep -q '"aceito": false' "${OUT}/calibracao/gates.json"
novo_caso s3b
matriz_calib "ASYNC-none:calibracao:1,ASYNC-F2:calibracao:1"
PRIMEIRA_REDE="$(awk -F'\t' 'NR > 1 && $7 == "F2" {print $1; exit}' "${MAT}")"
printf 'go calibracao %s\ngo rede 9999\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S3 GO de rede errado -> exit 5" "5" "${RC}"
ok "S3 nenhuma particao sem o GO de rede" "0" "$(conta 'inject-partition|')"
verdade "S3 GO de rede errado nao deixa o diretorio da F2" test -z "$(ls -d "${OUT}"/exec-"$(printf '%04d' "${PRIMEIRA_REDE}")"-* 2>/dev/null)"
fi

# ============================================================================= S4
if quer S4; then
echo "== S4: calibracao none + F1 + F2: recriacao, seis caches, camada 3, K1, carga, falhas, marcadores, ACEITA =="
novo_caso s4
matriz_calib "ASYNC-none:calibracao:1,ASYNC-F1:calibracao:1,ASYNC-F2:calibracao:1"
printf 'go calibracao %s\ngo rede %s\n' "${HOJE}" "$(awk -F'\t' 'NR > 1 && $7 == "F2" {print $1; exit}' "${MAT}")" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S4 exit 0" "0" "${RC}"
contem "S4 ultima linha" "exit=0" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
ok "S4 tres ups" "3" "$(grep -c ' podman-compose|.*|up|-d' "${ST}/chamadas.log")"
ok "S4 tres downs" "3" "$(grep -c ' podman-compose|.*|down' "${ST}/chamadas.log")"
ok "S4 seis caches criados por execucao (POST)" "18" "$(grep -c 'curl|POST|.*rest/v2/caches/' "${ST}/chamadas.log")"
for i in 0 1 2; do
    D="$(exec_dir "${i}")"
    ok "S4 i=${i} ACEITA" "ACEITA" "$(json "${D}/checagem.json" 'd["veredito"]')"
    ok "S4 i=${i} K1 ok" "True" "$(json "${D}/portao.json" 'd["ok"]')"
    ok "S4 i=${i} janela_h4 da linha do programa" "[44, 60]" "$(json "${D}/execucao.json" '[d["janela_h4"]["ini_desde_ancora_s"], d["janela_h4"]["fim_desde_ancora_s"]]')"
    ok "S4 i=${i} encerramento K = 21 + 2" "23" "$(json "${D}/execucao.json" 'd["encerramento"]["kill_apos_s"]')"
    ok "S4 i=${i} camada 3 antes sem divergencia" "None" "$(json "${D}/execucao.json" 'd["camada3_pre"]["divergencias"]')"
    verdade "S4 i=${i} MANIFEST.sha256 da execucao" test -s "${D}/MANIFEST.sha256"
    verdade "S4 i=${i} stdout.log com a linha de fim da carga" grep -q '^Fim da carga: rep=1 ' "${D}/stdout.log"
    verdade "S4 i=${i} caches.tsv com seis POST 200" test "$(awk -F'\t' '$3 == "POST" && $5 == 200' "${D}/caches.tsv" | wc -l)" -eq 6
done
DF1="$(ls -d "${OUT}"/exec-*-ASYNC-F1-A)"; DF2="$(ls -d "${OUT}"/exec-*-ASYNC-F2-A)"
ok "S4 F1: marcadores start e end do inject-crash.sh" "start end" "$(sed -n 's/.*fault=F1 node=isn2 event=\([a-z]*\) .*/\1/p' "${DF1}/marcadores.txt" | paste -sd' ')"
ok "S4 F1: volta pela REST registrada (P-35)" "True" "$(json "${DF1}/execucao.json" 'd["volta_f1"]["ok"]')"
ok "S4 F2: marcadores start e end" "start end" "$(sed -n 's/.*fault=F2 node=isn3 event=\([a-z]*\) .*/\1/p' "${DF2}/marcadores.txt" | paste -sd' ')"
SR="$(linha_de 'systemd-run|')"; INJ="$(linha_de 'inject-partition|--minority')"
verdade "S4 F2: dead-man armado antes da particao" test "${SR}" -gt 0 -a "${SR}" -lt "${INJ}"
contem "S4 F2: dead-man de 20 + 90 s com AccuracySec=1s" "--on-active=110s|--timer-property=AccuracySec=1s" "$(chamadas)"
contem "S4 F2: dead-man cura com inject-partition.sh --heal" "--heal|--minority|isn3" "$(grep 'systemd-run|' "${ST}/chamadas.log")"
ok "S4 F2: nenhum timer sobrando" "0" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"
contem "S4 carga com timeout -k 23s e o teto" "timeout|-k|23s|200s|java|-jar" "$(chamadas)"
J="$(grep ' java|-jar|.*--controller|shadow' "${ST}/chamadas.log" | head -1)"
for a in "--scenario|S3" "--marker|" "--convergence|" "--rejoin-timeout-sec|30" "--hotrod-timeout-ms|2000" "--controller-admin|http://127.0.0.1:11222,http://127.0.0.1:11223,http://127.0.0.1:11224" "--w-recovery-sec|30"; do
    contem "S4 java recebe ${a}" "${a}" "${J}"
done
for f in resumo.json resumo.md MANIFEST.sha256 ambiente.md gates.json progresso.json bundle.sha256; do verdade "S4 ${f}" test -s "${OUT}/calibracao/${f}"; done
ok "S4 h4 sem execucoes h4" "0" "$(json "${OUT}/h4/h4-calibracao.json" 'd["contagem"]["h4"]')"
ok "S4 h4 avaliou as tres" "3" "$(json "${OUT}/h4/h4-calibracao.json" 'd["contagem"]["execucoes"]')"
verdade "S4 MANIFEST da fase cobre o stdout do F2" grep -q "${DF2##*/}/stdout.log" "${OUT}/calibracao/MANIFEST.sha256"
fi

# ============================================================================= S5
if quer S5; then
echo "== S5: bloco A/B com F2; AC-5 no braco B: podman start do no parado ao ler a linha de fim da carga =="
novo_caso s5
matriz_aval "ASYNC-F2:teste:1"
echo "127.0.0.1:11224" > "${ST}/ac5-alvo"
printf 'go nucleo %s\ngo rede 0\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar nucleo
ok "S5 exit 0" "0" "${RC}"
DA="$(ls -d "${OUT}"/exec-*-ASYNC-F2-A)"; DB="$(ls -d "${OUT}"/exec-*-ASYNC-F2-B)"
ok "S5 A ACEITA" "ACEITA" "$(json "${DA}/checagem.json" 'd["veredito"]')"
ok "S5 B ACEITA" "ACEITA" "$(json "${DB}/checagem.json" 'd["veredito"]')"
ok "S5 B: religamento de isn3" "isn3" "$(json "${DB}/execucao.json" 'd["religamento"]["no"]')"
ok "S5 A: sem religamento" "None" "$(json "${DA}/execucao.json" 'd["religamento"]')"
SEED_B="$(json "${DB}/execucao.json" 'd["seed"]')"
verdade "S5 o programa viu isn3 de volta (t_religamento_ns)" test "$(cat "${ST}/relig-visto-${SEED_B}" 2>/dev/null)" != null
START="$(grep -n 'podman|start|isn3' "${ST}/chamadas.log" | tail -1 | cut -d' ' -f1 | cut -d: -f2)"
FIMJ="$(json "${DB}/execucao.json" 'd["sinal_fim_carga"]["recebido_ms"]')"
verdade "S5 podman start isn3 depois da linha de fim da carga" test "${START:-0}" -ge "${FIMJ:-1}"
ok "S5 trilha e linha de fim concordam" $'trilha_ac5	127.0.0.1:11224' "$(head -1 "${DB}/trilha-ac5.txt")"
verdade "S5 manifesto-bracos.tsv para o comparar_bracos.py" test "$(wc -l < "${OUT}/manifesto-bracos.tsv")" -eq 3
ok "S5 cabecalho do manifesto" $'bloco	celula	braco	execucao' "$(head -1 "${OUT}/manifesto-bracos.tsv")"
fi

# ============================================================================= S6
if quer S6; then
echo "== S6: braco B que nao atua (atuador nulo / politica nenhuma / sha diferente) -> carga parada, exit 6 =="
for caso in atuador politica sha; do
    novo_caso "s6-${caso}"
    matriz_aval "ASYNC-none:descritiva:1"
    case "${caso}" in
        atuador) echo nulo > "${ST}/meta-atuador" ;;
        politica) echo nenhuma > "${ST}/meta-politica" ;;
        sha) printf 'b%.0s' {1..64} > "${ST}/meta-sha" ;;
    esac
    printf 'go nucleo %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
    rodar nucleo
    ok "S6 ${caso}: exit 6" "6" "${RC}"
    contem "S6 ${caso}: motivo meta" "meta" "$(tail -1 "${OUT}/nucleo/orquestrador.log")"
    ok "S6 ${caso}: uma execucao DESCARTADA-INFRA (a recusada)" "1" "$(grep -l '"veredito": "DESCARTADA-INFRA"' "${OUT}"/exec-*/checagem.json 2>/dev/null | wc -l | tr -d ' ')"
    verdade "S6 ${caso}: carga morta antes do fim" test -n "$(cat "${ST}"/java-morto-* 2>/dev/null)"
done
fi

# ============================================================================= S7
if quer S7; then
echo "== S7: encerramento: TERM no orquestrador -> SIGTERM a carga, SIGKILL so depois de K = 23 s (virtuais) =="
novo_caso s7 10
matriz_calib "ASYNC-none:calibracao:1"
: > "${ST}/java-ignora-term"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar_fundo calibracao
verdade "S7 carga lancada" esperar_por 'timeout|-k|23s' 120
"${REAL_SLEEP}" 2
T0="$(agora_real_ms)"
kill -TERM "${BG}"; wait "${BG}"; RC=$?
DT=$(( $(agora_real_ms) - T0 ))
ok "S7 orquestrador sai com 143" "143" "${RC}"
verdade "S7 a carga recebeu SIGTERM" test -n "$(cat "${ST}"/java-morto-* 2>/dev/null)"
echo "   (TERM -> fim em ${DT} ms reais; K = 23 s virtuais = $(( 23000 / 10 )) ms reais)"
verdade "S7 SIGKILL nao veio antes de K (o processo resistiu ao TERM ate o timeout matar)" test "${DT}" -ge 2000
verdade "S7 nenhum java vivo" test -z "$(ps -ef 2>/dev/null | grep -F "${CASO}" | grep -F java | grep -v grep)"
novo_caso s7b 20
parametros exec_teto_s=150
matriz_calib "ASYNC-none:calibracao:1"
: > "${ST}/java-pendura"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S7 teto estourado: fase segue (exit 0)" "0" "${RC}"
D="$(exec_dir 0)"
ok "S7 teto estourado: RECUSADA" "RECUSADA" "$(json "${D}/checagem.json" 'd["veredito"]')"
ok "S7 teto marcado" "True" "$(json "${D}/execucao.json" 'd["teto_estourado"]')"
fi

# ============================================================================= S8
if quer S8; then
echo "== S8: P-35: inject-crash.sh sai com 0 mas isn2 nao volta -> conferido pela REST, exit 10 =="
novo_caso s8
matriz_calib "ASYNC-F1:calibracao:1,ASYNC-none:calibracao:1"
: > "${ST}/saude-nunca-volta"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S8 exit 10" "10" "${RC}"
D="$(ls -d "${OUT}"/exec-*-ASYNC-F1-A)"
ok "S8 injetor saiu com 0" "0" "$(json "${D}/execucao.json" 'd["injetor_exit"]')"
ok "S8 volta registrada como falha" "False" "$(json "${D}/execucao.json" 'd["volta_f1"]["ok"]')"
contem "S8 motivo P-35" "P-35" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
# a ordem da matriz e sorteada: cargas lancadas = posicao do F1 na fila (nenhuma depois dele)
POS_F1="$(awk -F'\t' 'NR > 1 && $7 == "F1" {print NR - 1; exit}' "${MAT}")"
ok "S8 nenhuma carga depois da execucao F1 (posicao ${POS_F1} na fila)" "${POS_F1}" "$(cargas)"
fi

# ============================================================================= S9
if quer S9; then
echo "== S9: resto de particao que o --heal nao remove -> exit 6, dead-man mantido; resto curado -> segue =="
novo_caso s9
matriz_calib "ASYNC-F2:calibracao:1"
: > "${ST}/del-falha"; : > "${ST}/heal-falha"
printf 'go calibracao %s\ngo rede 0\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S9 resto nao curado -> exit 6" "6" "${RC}"
contem "S9 motivo" "resto de rede" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
ok "S9 dead-man continua armado" "1" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"
novo_caso s9b
matriz_calib "ASYNC-F2:calibracao:1"
: > "${ST}/del-falha"
printf 'go calibracao %s\ngo rede 0\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S9 resto curado pelo --heal -> exit 0" "0" "${RC}"
ok "S9 heal extra registrado" "True" "$(json "$(exec_dir 0)/execucao.json" 'd["rede_pos"]["heal_extra"]')"
ok "S9 sem timer no fim" "0" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"
fi

# ============================================================================= S10
if quer S10; then
echo "== S10: DT-014: interrompida antes da carga (abortada, mesmo i) e depois (RECUSADA) =="
novo_caso s10
matriz_calib "ASYNC-none:calibracao:2"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
D0="${OUT}/exec-0000-ASYNC-none-A"; mkdir -p "${D0}"; echo '{}' > "${D0}/portao.json"
D1="${OUT}/exec-0001-ASYNC-none-A"; mkdir -p "${D1}"; echo "parcial" > "${D1}/stdout.log"
rodar calibracao
ok "S10 exit 0" "0" "${RC}"
verdade "S10 antes da carga: diretorio .abortada-<UTC> mantido" test -s "$(ls -d "${D0}".abortada-* | head -1)/portao.json"
ok "S10 o mesmo i=0 roda de novo (semente 60000)" "1" "$(grep -c ' java|-jar|.*|--seed|60000|' "${ST}/chamadas.log")"
ok "S10 depois da carga: RECUSADA sem relancar" "RECUSADA 0" "$(json "${D1}/checagem.json" 'd["veredito"]') $(grep -c ' java|-jar|.*|--seed|60100|' "${ST}/chamadas.log")"
contem "S10 motivo DT-014" "DT-014" "$(json "${D1}/checagem.json" 'd["motivos"][0]')"
verdade "S10 MANIFEST cobre a abortada" grep -q '.abortada-' "${OUT}/calibracao/MANIFEST.sha256"
fi

# ============================================================================= S11
if quer S11; then
echo "== S11: PARAR entre execucoes; retomada com o bundle alterado e recusada =="
novo_caso s11
matriz_calib "ASYNC-none:calibracao:2"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
: > "${OUT}/PARAR"
rodar calibracao
ok "S11 PARAR -> exit 0 sem carga" "0 0" "${RC} $(cargas)"
contem "S11 motivo PARAR" "PARAR" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
rm -f "${OUT}/PARAR"
printf '{"fase": "calibracao", "bundle_sha256": "%s"}\n' "$(printf 'c%.0s' {1..64})" > "${OUT}/calibracao/progresso.json"
rodar calibracao
ok "S11 bundle diferente do progresso -> exit 4" "4" "${RC}"
contem "S11 motivo bundle" "bundle mudou" "${SAIDA_TXT}"
fi

# ============================================================================= S12
if quer S12; then
echo "== S12: camada 3 (configuracao lida de volta diverge) e StartedAt anterior a recriacao -> exit 6 =="
novo_caso s12
matriz_calib "ASYNC-none:calibracao:1"
: > "${ST}/config-diverge-sessions"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S12 leitura de volta divergente na criacao -> exit 6" "6" "${RC}"
contem "S12 motivo cria-caches" "cria-caches" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
ok "S12 sem carga" "0" "$(cargas)"
novo_caso s12b
matriz_calib "ASYNC-none:calibracao:1"
echo DEGRADED_MODE > "${ST}/disponibilidade"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S12 disponibilidade != AVAILABLE antes da carga -> exit 6" "6" "${RC}"
contem "S12 motivo camada 3" "camada 3" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
novo_caso s12c
matriz_calib "ASYNC-none:calibracao:1"
: > "${ST}/started-velho"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S12 StartedAt anterior a recriacao -> exit 6" "6" "${RC}"
contem "S12 motivo StartedAt" "StartedAt" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
fi

# ============================================================================= S13
if quer S13; then
echo "== S13: h4 (C3): execucao h4 vira DESCARTADA-INFRA; 4 seguidas param a fase; unit inativa para antes =="
novo_caso s13
matriz_calib "ASYNC-none:calibracao:4"
amostras 2
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S13 4 h4 seguidas -> parada (exit 10)" "10" "${RC}"
contem "S13 motivo parada h4" "parada h4" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
ok "S13 primeira execucao h4a" "DESCARTADA-INFRA" "$(json "$(exec_dir 0)/checagem.json" 'd["veredito"]')"
contem "S13 motivo h4a" "h4: h4a" "$(json "$(exec_dir 0)/checagem.json" 'd["motivos"]')"
novo_caso s13b
matriz_calib "ASYNC-none:calibracao:1"
: > "${ST}/h4-inativa"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S13 unit do amostrador inativa -> exit 4 no pre-voo" "4" "${RC}"
fi

# ============================================================================= S14
if quer S14; then
echo "== S14: bloco perdido no primeiro braco -> o segundo braco e PULADA (o bloco inteiro e refeito) =="
novo_caso s14
matriz_aval "ASYNC-none:descritiva:1"
PRIMEIRO="$(awk -F'\t' 'NR == 2 {print $12}' "${MAT}")"
echo 3 > "${ST}/java-exit-${PRIMEIRO}"
printf 'go nucleo %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar nucleo
ok "S14 exit 0" "0" "${RC}"
ok "S14 primeiro braco RECUSADA (carga saiu com 3)" "RECUSADA" "$(json "$(exec_dir 0)/checagem.json" 'd["veredito"]')"
ok "S14 segundo braco PULADA" "PULADA" "$(json "$(exec_dir 1)/checagem.json" 'd["veredito"]')"
ok "S14 so uma carga lancada" "1" "$(cargas)"
ok "S14 repor refaz o bloco inteiro" "2" "$("${REAL_PY}" -I "${MATRIZ_PY}" repor --matriz "${MAT}" --saida "${OUT}" --ordem-seed 7 --s0 70000 --passo-semente 100 --teto-h4 5 > /dev/null && awk 'NR > 1' "${OUT}/reposicoes/reposicoes.tsv" | wc -l | tr -d ' ')"
fi

# ============================================================================= S15
if quer S15; then
echo "== S15: limite de disco (livre < 1,5 x volume de uma execucao) -> exit 10 antes da carga =="
novo_caso s15
matriz_calib "ASYNC-none:calibracao:1"
echo 14 > "${ST}/df-livre"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S15 exit 10" "10" "${RC}"
contem "S15 motivo disco" "disco" "$(tail -1 "${OUT}/calibracao/orquestrador.log")"
ok "S15 sem carga" "0" "$(cargas)"
echo 15 > "${ST}/df-livre"; rm -f "${OUT}/calibracao/progresso.json"
rodar calibracao
ok "S15 com 1,5 x exatos segue" "0" "${RC}"
fi

# ============================================================================= S16
if quer S16; then
echo "== S16: F4 com dead-man e marcadores; nenhum '|| true' em linha de carga, injetor ou analise =="
novo_caso s16
matriz_calib "ASYNC-F4:calibracao:1"
printf 'go calibracao %s\ngo rede 0\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S16 exit 0" "0" "${RC}"
D="$(exec_dir 0)"
ok "S16 ACEITA" "ACEITA" "$(json "${D}/checagem.json" 'd["veredito"]')"
ok "S16 marcadores F4" "start end" "$(sed -n 's/.*fault=F4 node=isn3 event=\([a-z]*\) .*/\1/p' "${D}/marcadores.txt" | paste -sd' ')"
contem "S16 dead-man F4 cura com inject-netdegrade --heal" "--heal" "$(grep 'systemd-run|' "${ST}/chamadas.log")"
contem "S16 injetor F4 com severidade e direcao" "inject-netdegrade|--target|isn3|--severity|N3|--direction|both|--duration|20" "$(chamadas)"
ok "S16 nenhum '|| true' nas linhas que lancam carga, injetor, conferencia, h4 ou caches" "0" \
   "$(grep -E '"[$][{]CMD|bash "[$][{]INJ_|(-I|py) "[$][{](CONFERIR|H4S3)|bash "[$][{]CRIA_CACHES' "${ORQ}" | grep -c '|| *true')"
fi

# ============================================================================= S17
if quer S17; then
echo "== S17: carga que termina sem a ancora antes dos 120 s -> RECUSADA 'sem ancora', sem injecao =="
novo_caso s17
matriz_calib "ASYNC-F1:calibracao:1"
: > "${ST}/java-sem-ancora"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S17 exit 0" "0" "${RC}"
D="$(exec_dir 0)"
ok "S17 RECUSADA" "RECUSADA" "$(json "${D}/checagem.json" 'd["veredito"]')"
contem "S17 motivo sem ancora" "sem ancora" "$(json "${D}/checagem.json" 'd["motivos"]')"
ok "S17 nenhuma injecao" "0" "$(conta 'podman|kill')"
ok "S17 janela_h4 nula" "None" "$(json "${D}/execucao.json" 'd["janela_h4"]')"
fi

# ============================================================================= S18
if quer S18; then
echo "== S18: D-B: p2Habilitada != false no controlador recusa no pre-voo; p2_habilitada != false na meta para a carga =="
novo_caso s18
matriz_calib "ASYNC-none:calibracao:1"
"${REAL_PY}" -I -c "import json,sys; p=sys.argv[1]; o=json.load(open(p)); o['politica']['p2Habilitada']=True; open(p,'w').write(json.dumps(o))" "${CTRL}"
CTRL_SHA="$(sha256sum "${CTRL}" | cut -d' ' -f1)"
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S18 p2Habilitada true -> exit 4 no pre-voo" "4" "${RC}"
contem "S18 motivo p2Habilitada" "p2Habilitada" "${SAIDA_TXT}"
ok "S18 sem compose nem carga" "0" "$(( $(conta 'podman-compose|') + $(cargas) ))"
novo_caso s18c
matriz_calib "ASYNC-none:calibracao:1"
controlador sem-politica
printf 'go calibracao %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar calibracao
ok "S18 sem bloco politica -> exit 4 (calibracao tambem exige p2Habilitada = false)" "4" "${RC}"
contem "S18 motivo do sem bloco" "p2Habilitada" "${SAIDA_TXT}"
novo_caso s18b
matriz_aval "ASYNC-none:descritiva:1"
echo true > "${ST}/meta-p2"
printf 'go nucleo %s\n' "${HOJE}" > "${CS_TTY_ARQUIVO}"
rodar nucleo
ok "S18 meta com p2_habilitada true -> exit 6" "6" "${RC}"
contem "S18 motivo p2_habilitada" "p2_habilitada" "$(tail -1 "${OUT}/nucleo/orquestrador.log")"
verdade "S18 carga parada" test -n "$(cat "${ST}"/java-morto-* 2>/dev/null)"
ok "S18 a primeira execucao (qualquer braco) e DESCARTADA-INFRA" "1" "$(grep -l '"veredito": "DESCARTADA-INFRA"' "${OUT}"/exec-*/checagem.json 2>/dev/null | wc -l | tr -d ' ')"
fi

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
