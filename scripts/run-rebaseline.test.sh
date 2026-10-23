#!/usr/bin/env bash
#
# run-rebaseline.test.sh
#
# Testes R1..R12 do scripts/run-rebaseline.sh (prompt do runner de 2026-10-23, com a adenda da DT-011:
# injecao a +49 s da ancora, F3 de 251 s, amostras de netem em +70 s e +280 s).
#
# Auto-contido, no estilo de inject-jitter.test.sh: sem cluster, sem rede, sem VM, sem tc real. Shims em
# PATH para java, podman, sudo, nsenter, tc, curl, systemd-run, systemctl, flock, sleep, date e python3
# (este so intercepta analysis/rebaseline.py; o resto vai ao python real). Relogio acelerado por shim:
# o shim de sleep dorme s/RB_ESCALA de verdade e o de date devolve T0 + (agora - T0) * RB_ESCALA, de modo
# que os prazos do runner (60 s de K1, +49 s, 120 s de ancora, 180 s de F1, 251 s de F3) passam em
# segundos reais. Os injetores REAIS (inject-crash.sh, inject-jitter.sh) rodam contra os shims.
#
# Uso: bash scripts/run-rebaseline.test.sh [R1 R4 ...]   (sem argumentos: todos). Exit 0 se todos passarem.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "${DIR}/.." && pwd)"
RUNNER="${DIR}/run-rebaseline.sh"
REAL_PY="$(command -v python3)"
REAL_DATE="$(command -v date)"
REAL_SLEEP="$(command -v sleep)"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
SELECAO=" ${*:-R1 R2 R3 R4 R5 R6 R7 R8 R9 R10 R11 R12 R13 R14} "

PASS=0
FAIL=0
ok() {
    if [[ "$2" == "$3" ]]; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (esperado='$2' obtido='$3')"; FAIL=$((FAIL + 1)); fi
}
contem() {
    if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "PASS: $1"; PASS=$((PASS + 1))
    else echo "FAIL: $1 (nao encontrou '$2')"; FAIL=$((FAIL + 1)); fi
}
nao_contem() {
    if printf '%s\n' "$3" | grep -qF -e "$2"; then echo "FAIL: $1 (encontrou '$2')"; FAIL=$((FAIL + 1))
    else echo "PASS: $1"; PASS=$((PASS + 1)); fi
}
verdade() {  # verdade <descricao> <comando...>
    local d="$1"; shift
    if "$@"; then echo "PASS: ${d}"; PASS=$((PASS + 1)); else echo "FAIL: ${d}"; FAIL=$((FAIL + 1)); fi
}
quer() { [[ "${SELECAO}" == *" $1 "* ]]; }

agora_real_ms() { local r="${EPOCHREALTIME//[.,]/}"; echo "$((10#${r:0:13}))"; }

# ----------------------------------------------------------------------------- shims
criar_shims() {  # criar_shims <dir-shims>
    local SH="$1"
    mkdir -p "${SH}"
    : > "${SH}/.rb-shim"
    cat > "${SH}/_comum.sh" <<'EOF'
vms() { local r="${EPOCHREALTIME//[.,]/}"; r=$((10#${r:0:13})); echo $(( RB_T0_MS + (r - RB_T0_MS) * RB_ESCALA )); }
registrar() { local IFS='|'; echo "$(vms) $*" >> "${RB_ESTADO}/chamadas.log"; }
dormir() { sleep "$1" & wait $!; }
EOF
    cat > "${SH}/sleep" <<EOF
#!/usr/bin/env bash
s="\$1"; int="\${s%%.*}"; frac="000"; [[ "\$s" == *.* ]] && frac="\${s#*.}000"
ms=\$(( 10#\${int:-0} * 1000 + 10#\${frac:0:3} ))
r=\$(( ms / RB_ESCALA ))
exec "${REAL_SLEEP}" "\$(( r / 1000 )).\$(printf '%03d' \$(( r % 1000 )))"
EOF
    cat > "${SH}/date" <<EOF
#!/usr/bin/env bash
if [[ "\$*" == "+%s%3N" ]]; then source "\${RB_SHIM_DIR}/_comum.sh"; vms; exit 0; fi
exec "${REAL_DATE}" "\$@"
EOF
    cat > "${SH}/java" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar java "$@"
ST="${RB_ESTADO}"
case "$1" in
    -version) echo 'openjdk version "21" (shim)' >&2; exit 0 ;;
    -cp)
        saida=""; while [[ $# -gt 0 ]]; do [[ "$1" == "--saida" ]] && saida="$2"; shift; done
        echo "{\"veredito\": \"$(cat "${ST}/verif-veredito" 2>/dev/null || echo OK)\"}" > "${saida}"
        exit "$(cat "${ST}/verif-rc" 2>/dev/null || echo 0)" ;;
esac
csv=""; seed=""; cen=""
while [[ $# -gt 0 ]]; do
    case "$1" in --csv-dir) csv="$2" ;; --seed) seed="$2" ;; --scenario) cen="$2" ;; esac; shift
done
rc="$(cat "${ST}/java-exit-${seed}" 2>/dev/null || echo 0)"
trap 'echo morto >> "${ST}/java-morto-${seed}"; exit 143' TERM
echo "Configuracao: Cli{scenario=${cen}, duracao=300s, ops=0, rep=1, threads=4, seed=${seed}, csvDir=${csv}}"
if [[ "${rc}" == 2 || "${rc}" == 3 ]]; then echo "Erro: shim exit ${rc}" >&2; exit "${rc}"; fi
dormir "$(cat "${ST}/java-ancora-s" 2>/dev/null || echo 12)"
if [[ ! -f "${ST}/java-sem-ancora" ]]; then
    echo "Warm-up ate +30s; medicao a partir de +44s; total 300s"
    vms > "${ST}/ancora-${seed}"
fi
dormir "$(cat "${ST}/java-resto-s" 2>/dev/null || echo 305)"
mkdir -p "${csv}"
echo "op_id,operation,start_ns,end_ns,replica,return_code,key" > "${csv}/rep-001.csv"
echo "instante,invariante,chave,mensagem" > "${csv}/violacoes-rep-001.csv"
echo '{"tamanho_final":200000,"veredito":"OK"}' > "${csv}/carga-rep-001.json"
echo '{"veredito":"OK"}' > "${csv}/estado-rep-001.json"
echo '{"contrato_csv":2}' > "${csv}/meta-csv-rep-001.json"
echo "Total de operacoes: 1"
exit "${rc}"
EOF
    cat > "${SH}/podman" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar podman "$@"
ST="${RB_ESTADO}"
cmd="$1"; shift
case "${cmd}" in
    inspect)
        fmt=""; if [[ "$1" == "-f" ]]; then fmt="$2"; shift 2; fi
        no="$1"
        [[ -f "${ST}/ausente-${no}" ]] && exit 125
        [[ -z "${fmt}" && -f "${ST}/inspect-sem-f-falha-${no}" ]] && exit 125
        est="$(cat "${ST}/status-${no}" 2>/dev/null || echo running)"
        case "${fmt}" in
            *State.Status*) echo "${est}" ;;
            *State.Pid*) if [[ "${est}" == running ]]; then echo "$((1000 + ${no#isn}))"; else echo 0; fi ;;
            *Created.Unix*) cat "${ST}/criado-${no}" 2>/dev/null || echo 1792800000 ;;
            *StartedAt.UnixMilli*) echo "$(( $(cat "${ST}/iniciado-${no}" 2>/dev/null || echo 1792700000) * 1000 ))" ;;
            *StartedAt.Unix*) cat "${ST}/iniciado-${no}" 2>/dev/null || echo 1792700000 ;;
            *FinishedAt.UnixMilli*) cat "${ST}/parado-ms-${no}" 2>/dev/null || echo -62135596800000 ;;
            *) echo '[{}]' ;;
        esac ;;
    stop) echo stopped > "${ST}/status-$1"; echo parou >> "${ST}/parou-$1"; vms > "${ST}/parado-ms-$1"; echo "$1" ;;
    start)
        if [[ "$(cat "${ST}/status-$1" 2>/dev/null || echo running)" != running ]]; then
            # nova vida do processo: ISPN080001 na subida e o aviso ISPN006010 da JVM nova (DT-013)
            t="$(( $(vms) / 1000 ))"; echo "${t}" > "${ST}/iniciado-$1"
            printf '%s INFO ISPN080001: Infinispan Server started\n%s WARN ISPN006010: Conditional operation\n' "${t}" "${t}" >> "${ST}/logs-$1"
        fi
        echo running > "${ST}/status-$1"; echo "$1" ;;
    logs)
        no="${@: -1}"; desde=0
        if [[ "$1" == "--since" ]]; then
            [[ -f "${ST}/logs-since-falha" ]] && { echo "Error: since falhou (shim)" >&2; exit 125; }
            desde="$2"
        fi
        if [[ ! -f "${ST}/logs-${no}" ]]; then
            n="$(cat "${ST}/ispn-${no}" 2>/dev/null || echo 1)"
            echo "1792700000 INFO ISPN080001: Infinispan Server started" > "${ST}/logs-${no}"
            for ((k = 0; k < n; k++)); do echo "1792700000 WARN [o.i.s.h.HotRodHeader] ISPN006010: Conditional operation" >> "${ST}/logs-${no}"; done
            echo "1792700000 INFO outra linha" >> "${ST}/logs-${no}"
        fi
        while read -r t txt; do [[ "${t}" -ge "${desde}" ]] && echo "${txt}"; done < "${ST}/logs-${no}"
        exit 0 ;;
    version) echo "Version: 5.0.0 (shim)" ;;
    *) exit 0 ;;
esac
EOF
    cat > "${SH}/sudo" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar sudo "$@"
ST="${RB_ESTADO}"
[[ "$1" == "-n" ]] && shift
if [[ "$1" == "true" ]]; then [[ -f "${ST}/sudo-falha" ]] && exit 1; exit 0; fi
if [[ "$1" == "readlink" ]]; then
    p="${2#/proc/}"; pid="${p%%/*}"
    if [[ "${pid}" == 1 || -f "${ST}/netns-host-${pid}" ]]; then echo "net:[4026531840]"; else echo "net:[40265${pid}]"; fi
    exit 0
fi
exec "$@"
EOF
    cat > "${SH}/nsenter" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar nsenter "$@"
pid=""
while [[ $# -gt 0 ]]; do
    case "$1" in -t) pid="$2"; shift 2 ;; -n) shift ;; *) break ;; esac
done
export RB_NETNS_PID="${pid}"
exec "$@"
EOF
    cat > "${SH}/tc" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar tc "pid=${RB_NETNS_PID:-?}" "$@"
ST="${RB_ESTADO}"
f="${ST}/netem-${RB_NETNS_PID:-0}"
case "$*" in
    "qdisc add dev eth0 root netem"*)
        if [[ -f "${ST}/netem-rejeita" ]]; then echo "Error: Specified qdisc kind is unknown." >&2; exit 2; fi
        echo "$*" > "${f}" ;;
    "qdisc del dev eth0 root")
        if [[ -f "${ST}/del-falha" ]]; then exit 0; fi
        if [[ -f "${f}" ]]; then rm -f "${f}"; else echo "Error: Cannot delete qdisc with handle of zero." >&2; exit 2; fi ;;
    "qdisc show dev eth0")
        if [[ -f "${f}" ]]; then echo "qdisc netem 8001: root refcnt 2 limit 1000 delay 20ms  13ms"
        else echo "qdisc noqueue 0: root refcnt 2"; fi ;;
esac
EOF
    cat > "${SH}/curl" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar curl "$@"
ST="${RB_ESTADO}"
url="${@: -1}"; porta="${url#*://*:}"; porta="${porta%%/*}"
case "${porta}" in 11222) alvo=isn1 ;; 11223) alvo=isn2 ;; *) alvo=isn3 ;; esac
[[ "$(cat "${ST}/status-${alvo}" 2>/dev/null || echo running)" == running ]] || exit 7
n=0; for no in isn1 isn2 isn3; do [[ "$(cat "${ST}/status-${no}" 2>/dev/null || echo running)" == running ]] && n=$((n + 1)); done
if [[ -f "${ST}/saude-nunca-volta" && -f "${ST}/parou-isn2" ]]; then n=2; fi
st=HEALTHY; [[ "${n}" == 3 ]] || st=DEGRADED
echo "{\"cluster_health\":{\"cluster_name\":\"c\",\"health_status\":\"${st}\",\"number_of_nodes\":${n},\"node_names\":[]},\"cache_health\":[{\"status\":\"HEALTHY\",\"cache_name\":\"sessions\"}]}"
EOF
    cat > "${SH}/systemd-run" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar systemd-run "$@"
ST="${RB_ESTADO}"
rc="$(cat "${ST}/systemd-run-rc" 2>/dev/null || echo 0)"
[[ "${rc}" == 0 ]] || exit "${rc}"
for a in "$@"; do [[ "${a}" == --unit=* ]] && u="${a#--unit=}"; done
prev=""; for a in "$@"; do [[ "${prev}" == --unit ]] && u="${a}"; prev="${a}"; done
: > "${ST}/timer-${u}"
echo "Running timer as unit: ${u}.timer"
EOF
    cat > "${SH}/systemctl" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar systemctl "$@"
ST="${RB_ESTADO}"
case "$1" in
    stop) u="${2%.timer}"; rm -f "${ST}/timer-${u}" ;;
    list-timers)
        for f in "${ST}"/timer-*; do [[ -e "${f}" ]] && echo "n/a n/a n/a n/a ${f##*/timer-}.timer ${f##*/timer-}.service"; done ;;
esac
exit 0
EOF
    cat > "${SH}/flock" <<'EOF'
#!/usr/bin/env bash
source "${RB_SHIM_DIR}/_comum.sh"
registrar flock "$@"
conf=1
while [[ $# -gt 0 && "$1" == -* ]]; do
    case "$1" in -E) conf="$2"; shift 2 ;; *) shift ;; esac
done
lockf="$1"; shift
[[ -f "${RB_ESTADO}/lock-ocupado" ]] && exit "${conf}"
exec "$@"
EOF
    cat > "${SH}/python3" <<EOF
#!/usr/bin/env bash
source "\${RB_SHIM_DIR}/_comum.sh"
ST="\${RB_ESTADO}"
if [[ "\$*" == *rebaseline.py\ checar* ]]; then
    registrar python3 "\$@"
    d=""; prev=""; for a in "\$@"; do [[ "\${prev}" == --exec ]] && d="\${a}"; prev="\${a}"; done
    b="\${d##*/}"; i="\${b#exec-}"; i="\$((10#\${i%%-*}))"
    rc="\$(cat "\${ST}/checar-rc-\${i}" 2>/dev/null || echo 0)"
    case "\${rc}" in 0) v=ACEITA ;; 10) v=RECUSADA ;; *) v=PARADA ;; esac
    echo "{\"i\": \${i}, \"veredito\": \"\${v}\", \"motivos\": [\"shim\"]}" > "\${d}/checagem.json"
    exit "\${rc}"
fi
if [[ "\$*" == *rebaseline.py\ estado* ]]; then
    registrar python3 "\$@"
    if [[ "\$*" == *--listar-reposicoes* ]]; then cat "\${ST}/reposicoes-lista" 2>/dev/null; fi
    n="\$(cat "\${ST}/estado-n" 2>/dev/null || echo 0)"; n=\$((n + 1)); echo "\${n}" > "\${ST}/estado-n"
    exit "\$(cat "\${ST}/estado-rc-\${n}" 2>/dev/null || echo 0)"
fi
exec "${REAL_PY}" "\$@"
EOF
    chmod +x "${SH}"/*
}

# ----------------------------------------------------------------------------- ambiente de um caso
# novo_caso <nome> [escala]: cria shims, estado, jar falso e exporta as variaveis do runner.
novo_caso() {
    CASO="${TMP}/$1"
    SH="${CASO}/shims"; ST="${CASO}/estado"; OUT="${CASO}/saida"
    mkdir -p "${ST}" "${OUT}"
    criar_shims "${SH}"
    JAR="${CASO}/workload.jar"; echo "jar falso $1" > "${JAR}"
    JAR_SHA="$(sha256sum "${JAR}" | cut -d' ' -f1)"
    export RB_SHIM_DIR="${SH}" RB_ESTADO="${ST}" RB_ESCALA="${2:-50}" RB_T0_MS="$(agora_real_ms)"
    export RB_MODO_TESTE=1 RB_TTY_ARQUIVO="${ST}/tty" RB_PASSWORD="segredo" PODMAN="sudo podman" JAVA="java"
    export PATH_TESTE="${SH}:${PATH}"
    : > "${ST}/chamadas.log"
    HOJE="$("${REAL_DATE}" +%F)"
}

# matriz <arquivo> <fase> "<i> <celula>" ...   (sementes S0 + 100*i)
matriz() {
    local arq="$1" fase="$2"; shift 2
    printf 'i\tfase\tnoite\trodada\tposicao\tcelula\tcenario\tmodo\tfalha\tseed\n' > "${arq}"
    local pos=0 x i cel
    for x in "$@"; do
        i="${x%% *}"; cel="${x#* }"
        IFS=- read -r cen modo falha <<< "${cel}"
        local fs="campanha"; [[ "${fase}" == piloto ]] && fs="piloto"
        printf '%s\t%s\t%s\t1\t%s\t%s\t%s\t%s\t%s\t%s\n' "${i}" "${fs}" "${fase}" "${pos}" "${cel}" "${cen}" "${modo}" \
            "${falha}" "$((50000 + 100 * i))" >> "${arq}"
        pos=$((pos + 1))
    done
    MAT="${arq}"; MAT_SHA="$(sha256sum "${arq}" | cut -d' ' -f1)"
}

# rodar_runner <fase> [args extras...]: roda o runner com os shims; define RC e SAIDA_TXT
rodar_runner() {
    local fase="$1"; shift
    SAIDA_TXT="$(PATH="${PATH_TESTE}" bash "${RUNNER}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" \
        --jar "${JAR}" --jar-sha256 "${JAR_SHA}" --fase "${fase}" --saida "${OUT}" \
        --cluster-criado-apos 2026-10-23T00:00:00Z "$@" 2>&1)"; RC=$?
}

chamadas() { cat "${ST}/chamadas.log"; }
linha_de() {  # linha_de <padrao-fixo> [n-esima]: numero da linha da n-esima ocorrencia em chamadas.log (0 se nao ha)
    local n; n="$(grep -nF -e "$1" "${ST}/chamadas.log" | sed -n "${2:-1}p" | cut -d: -f1)"; echo "${n:-0}"
}
ultima_de() { local n; n="$(grep -nF -e "$1" "${ST}/chamadas.log" | tail -1 | cut -d: -f1)"; echo "${n:-0}"; }
json() { "${REAL_PY}" -c "import json,sys; d=json.load(open(sys.argv[1])); print(eval(sys.argv[2]))" "$1" "$2"; }
exec_dir() { ls -d "${OUT}"/exec-"$(printf '%04d' "$1")"-* 2>/dev/null | head -1; }

# ============================================================================= R1
if quer R1; then
echo "== R1: --dry-run lista o piloto na ordem da matriz, sementes 50000+100*i, sem java/podman/sudo =="
novo_caso r1
"${REAL_PY}" -I "${DIR}/rebaseline_matriz.py" gerar --ordem-seed 231023 --s0 50000 --saida "${CASO}/matriz.tsv" > /dev/null
MAT="${CASO}/matriz.tsv"; MAT_SHA="$(sha256sum "${MAT}" | cut -d' ' -f1)"
OUT1="$(PATH="${PATH_TESTE}" env -u RB_MODO_TESTE bash "${RUNNER}" --dry-run --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" \
        --fase piloto 2>&1)"; RC=$?
ok "R1 exit 0" "0" "${RC}"
ESPERADO="$(awk -F'\t' 'NR > 1 && $3 == "piloto" {print $1, $6, $10}' "${MAT}")"
OBTIDO="$(printf '%s\n' "${OUT1}" | sed -n 's/^PLANO i=\([0-9]*\) celula=\([^ ]*\) seed=\([0-9]*\).*/\1 \2 \3/p')"
ok "R1 plano = matriz (i, celula, semente) na ordem" "${ESPERADO}" "${OBTIDO}"
ok "R1 12 execucoes" "12" "$(printf '%s\n' "${OBTIDO}" | grep -c .)"
verdade "R1 sementes = 50000 + 100*i" bash -c "printf '%s\n' \"${OBTIDO}\" | awk '{ if (\$3 != 50000 + 100*\$1) exit 1 }'"
contem "R1 comando java do PR" "--scenario S2 --mix v3 --replication sync --reset-state on --duration 300 --warmup-min-sec 30 --threads 4 --ops 0 --rep 1 --seed 150000 --servers 127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224 --username admin --password *** --csv-dir" "${OUT1}"
contem "R1 injecao a +49 s" "+49 s" "${OUT1}"
ok "R1 nenhuma chamada a java/podman/sudo" "0" "$(grep -cE ' (java|podman|sudo|nsenter|tc|curl|systemd-run|flock)([|]|$)' "${ST}/chamadas.log")"
OUT2="$(PATH="${PATH_TESTE}" bash "${RUNNER}" --dry-run --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --fase noite1 2>&1)"
ok "R1 noite1 lista 60 execucoes" "60" "$(printf '%s\n' "${OUT2}" | grep -c '^PLANO i=')"
if [[ -n "${RB_TESTE_GUARDAR:-}" ]]; then
    printf '%s\n' "${OUT1}" > "${RB_TESTE_GUARDAR}/dry-run-piloto.txt"
    printf '%s\n' "${OUT2}" > "${RB_TESTE_GUARDAR}/dry-run-noite1.txt"
fi
fi

# ============================================================================= R2
if quer R2; then
echo "== R2: sha256 errado da matriz ou do jar: exit 4, nenhum java =="
novo_caso r2
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none"
MAT_SHA_OK="${MAT_SHA}"; MAT_SHA="$(printf '0%.0s' {1..64})"
rodar_runner piloto
ok "R2 matriz com sha errado -> exit 4" "4" "${RC}"
MAT_SHA="${MAT_SHA_OK}"; JAR_SHA="$(printf 'a%.0s' {1..64})"
rodar_runner piloto
ok "R2 jar com sha errado -> exit 4" "4" "${RC}"
ok "R2 nenhum java chamado" "0" "$(grep -c ' java' "${ST}/chamadas.log")"
fi

# ============================================================================= R3
if quer R3; then
echo "== R3: gate da noite e gate f3 =="
novo_caso r3a
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1" "1001 S1-sync-F3"
echo "go piloto 1999-01-01" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R3 gate da noite errado -> exit 5" "5" "${RC}"
ok "R3 sem java" "0" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
ok "R3 nenhum injetor (podman stop)" "0" "$(grep -c 'podman|stop' "${ST}/chamadas.log")"
ok "R3 nenhum netem" "0" "$(grep -c 'tc|.*add' "${ST}/chamadas.log")"

novo_caso r3b
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none" "1001 S2-async-F1" "1002 S1-sync-F3" "1003 S2-sync-F3"
printf 'go piloto %s\ngo f3 1002\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R3 gates certos -> exit 0" "0" "${RC}"
LOG="$(cat "${OUT}/piloto/runner.log")"
ok "R3 gate f3 pedido uma vez" "1" "$(grep -c 'GATE f3' "${OUT}/piloto/runner.log")"
G="$(grep -n 'GATE f3' "${OUT}/piloto/runner.log" | cut -d: -f1)"
W1001="$(grep -n 'lancando workload i=1001' "${OUT}/piloto/runner.log" | cut -d: -f1)"
W1002="$(grep -n 'lancando workload i=1002' "${OUT}/piloto/runner.log" | cut -d: -f1)"
verdade "R3 gate f3 depois do F1 (i=1001) e antes da primeira F3 (i=1002)" test "${W1001:-0}" -lt "${G:-0}" -a "${G:-0}" -lt "${W1002:-0}"
K1002="$(grep -n 'exec i=1002 .*portao K1' "${OUT}/piloto/runner.log" | cut -d: -f1)"
verdade "R3 (C2) gate f3 antes do portao K1 da primeira F3" test "${G:-0}" -gt 0 -a "${G:-0}" -lt "${K1002:-0}"
ok "R3 gates.json com 2 gates" "2" "$(json "${OUT}/piloto/gates.json" 'len(d["execucoes_do_runner"][-1]["gates"])')"
ok "R3 4 workloads" "4" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
for f in resumo.json resumo.md MANIFEST.sha256 ambiente.md; do
    verdade "R3 ${f} gravado" test -s "${OUT}/piloto/${f}"
done
verdade "R3 MANIFEST cobre stdout.log" grep -q 'exec-1002-S1-sync-F3/stdout.log' "${OUT}/piloto/MANIFEST.sha256"
contem "R3 ultima linha com exit e motivo" "exit=0" "$(tail -1 "${OUT}/piloto/runner.log")"

novo_caso r3c
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1" "1001 S1-sync-F3"
printf 'go piloto %s\ngo f3 9999\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R3 gate f3 errado -> exit 5" "5" "${RC}"
ok "R3 F1 anterior rodou (podman stop)" "1" "$(grep -c ' podman|stop|isn2' "${ST}/chamadas.log")"
ok "R3 nenhum netem sem o gate f3" "0" "$(grep -c 'tc|.*|qdisc|add' "${ST}/chamadas.log")"
ok "R3 workload da F3 nao lancado" "1" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
verdade "R3 (C2) go f3 errado nao deixa exec-1001" test -z "$(ls -d "${OUT}"/exec-1001-* 2>/dev/null)"
printf 'go piloto %s
go f3 1001
' "${HOJE}" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R3 (C2) nova tentativa com o go f3 certo -> exit 0" "0" "${RC}"
ok "R3 (C2) F1 ja checada nao roda de novo; F3 roda" "2" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
fi

# ============================================================================= R4
if quer R4; then
echo "== R4: ancora 'Warm-up ate' e disparo em N + 49 s =="
novo_caso r4 2
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
echo 12 > "${ST}/java-ancora-s"; echo 120 > "${ST}/java-resto-s"
rodar_runner piloto
ok "R4 exit 0" "0" "${RC}"
D="$(exec_dir 1000)"
ANC="$(cat "${ST}/ancora-150000")"
DISP="$(json "${D}/execucao.json" 'd["disparo_ms"]')"
STOP="$(grep 'podman|stop|isn2' "${ST}/chamadas.log" | head -1 | cut -d' ' -f1)"
echo "   (ancora do java ${ANC}; disparo do runner +$(( DISP - ANC )) ms; podman stop +$(( STOP - ANC )) ms)"
verdade "R4 disparo em N + 49 s (+-1 s)" test "$(( DISP - ANC ))" -ge 48000 -a "$(( DISP - ANC ))" -le 50000
verdade "R4 podman stop do injetor dentro da tolerancia de K6 (N+48..N+51 s)" test "$(( STOP - ANC ))" -ge 48000 -a "$(( STOP - ANC ))" -le 51000
OFF="$(json "${D}/execucao.json" 'd["inj_inicio_offset_s"]')"
verdade "R4 inj_inicio_offset_s em [48,50]" "${REAL_PY}" -c "import sys; sys.exit(0 if 48 <= ${OFF} <= 50 else 1)"
PAR="$(json "${D}/execucao.json" 'd["falha_efetiva"]["f1_parado_desde_ancora_s"]')"
REI="$(json "${D}/execucao.json" 'd["falha_efetiva"]["f1_reiniciado_desde_ancora_s"]')"
echo "   (C5: isn2 parado a +${PAR} s e reiniciado a +${REI} s da ancora)"
verdade "R4 (C5) instante efetivo do F1 registrado (parado >= +48 s, reinicio ~60 s depois)" "${REAL_PY}" -c     "import sys; p, r = ${PAR}, ${REI}; sys.exit(0 if p >= 48 and 55 <= r - p <= 70 else 1)"

novo_caso r4b
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/java-sem-ancora"
rodar_runner piloto
D="$(exec_dir 1000)"
ok "R4 sem ancora: segue (exit 0)" "0" "${RC}"
ok "R4 sem ancora: workload morto" "1" "$(cat "${ST}"/java-morto-150000 2>/dev/null | grep -c morto)"
ok "R4 sem ancora: recusa K6 registrada" "sem ancora" "$(json "${D}/execucao.json" 'd["recusa_k6"]')"
ok "R4 sem ancora: nenhum injetor" "0" "$(grep -c 'podman|stop' "${ST}/chamadas.log")"
fi

# ============================================================================= R5
if quer R5; then
echo "== R5: exits do workload chegam a execucao.json; sem '|| true' =="
novo_caso r5
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none" "1001 S2-async-none" "1002 S1-async-none" "1003 S2-sync-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
echo 2 > "${ST}/java-exit-150100"; echo 3 > "${ST}/java-exit-150200"; echo 5 > "${ST}/java-exit-150300"
rodar_runner piloto
ok "R5 exit 0" "0" "${RC}"
for par in "1000 0" "1001 2" "1002 3" "1003 5"; do
    set -- ${par}
    ok "R5 workload_exit de i=$1" "$2" "$(json "$(exec_dir "$1")/execucao.json" 'd["workload_exit"]')"
done
ok "R5 nenhum '|| true' no runner" "0" "$(grep -c '|| true' "${RUNNER}")"
ok "R5 nenhum '|| true' em linhas de java/verificador/injetor/analise" "0" \
   "$(grep -E 'java|JAVA|CMD|Verificador|inject-|INJ_|rebaseline.py|ANALISE' "${RUNNER}" | grep -c '|| *true')"
fi

# ============================================================================= R6
if quer R6; then
echo "== R6: F3 com dead-man antes do netem; residual -> exit 6 =="
novo_caso r6 10
matriz "${CASO}/m.tsv" piloto "1000 S2-sync-F3"
printf 'go piloto %s\ngo f3 1000\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R6 exit 0" "0" "${RC}"
SR="$(linha_de 'systemd-run|')"; ADD="$(linha_de 'qdisc|add')"
verdade "R6 systemd-run antes do primeiro tc add" test "${SR}" -gt 0 -a "${SR}" -lt "${ADD}"
contem "R6 dead-man de 341 s" "--on-active=341s" "$(chamadas)"
contem "R6 dead-man remove o netem de isn2" "tcc-heal-rb-1000" "$(chamadas)"
contem "R6 netem 20ms 13ms normal" "netem|delay|20ms|13ms|distribution|normal" "$(chamadas)"
contem "R6 injetor com 251 s" "DURACAO=251s" "$(cat "$(exec_dir 1000)/injetor.log")"
DEL="$(ultima_de 'qdisc|del')"; STP="$(linha_de 'systemctl|stop|tcc-heal-rb-1000.timer')"
verdade "R6 systemctl stop depois da ultima remocao" test "${STP}" -gt "${DEL}" -a "${DEL}" -gt 0
D="$(exec_dir 1000)"
ok "R6 amostras de netem (+70 e +280) presentes" "[True, True]" "$(json "${D}/execucao.json" '[a["netem"] for a in d["netem_amostras"]]')"
ok "R6 alvos das amostras" "[70, 280]" "$(json "${D}/execucao.json" '[a["alvo_s"] for a in d["netem_amostras"]]')"
ok "R6 (C5) amostra efetiva do netem a +51 s (so relatorio)" "51" "$(json "${D}/execucao.json" 'd["falha_efetiva"]["netem_efetiva"]["alvo_s"]')"
ok "R6 (C5) amostra efetiva com offset e resultado" "True" "$(json "${D}/execucao.json" 'd["falha_efetiva"]["netem_efetiva"]["offset_s"] >= 50 and isinstance(d["falha_efetiva"]["netem_efetiva"]["netem"], bool)')"
ok "R6 netem ausente depois" "False" "$(json "${D}/execucao.json" 'd["netem_pos"]["netem"]')"
ok "R6 nenhum timer sobrando" "0" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"

novo_caso r6b 10
matriz "${CASO}/m.tsv" piloto "1000 S2-sync-F3"
printf 'go piloto %s\ngo f3 1000\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/del-falha"
rodar_runner piloto
ok "R6 netem residual persistente -> exit 6" "6" "${RC}"
contem "R6 motivo netem residual" "netem residual" "$(tail -1 "${OUT}/piloto/runner.log")"
ok "R6 dead-man continua armado (netem nao saiu)" "1" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"

novo_caso r6c
matriz "${CASO}/m.tsv" piloto "1000 S2-sync-F3"
printf 'go piloto %s\ngo f3 1000\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
echo 1 > "${ST}/systemd-run-rc"
rodar_runner piloto
ok "R6 dead-man que nao arma -> exit 6" "6" "${RC}"
ok "R6 sem dead-man, nenhum netem" "0" "$(grep -c 'qdisc|add' "${ST}/chamadas.log")"
fi

# ============================================================================= R7
if quer R7; then
echo "== R7: F1 sem retorno do cluster em 180 s -> exit 10, parada (e) =="
novo_caso r7
matriz "${CASO}/m.tsv" piloto "1000 S1-async-F1" "1001 S1-sync-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/saude-nunca-volta"
rodar_runner piloto
ok "R7 exit 10" "10" "${RC}"
contem "R7 motivo (e)" "(e)" "$(tail -1 "${OUT}/piloto/runner.log")"
ok "R7 execucao seguinte nao lancada" "1" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
fi

# ============================================================================= R8
esperar_por() {  # esperar_por <padrao> <segundos-reais>
    local k; for ((k = 0; k < $2 * 10; k++)); do grep -qF -e "$1" "${ST}/chamadas.log" && return 0; "${REAL_SLEEP}" 0.1; done; return 1
}
if quer R8; then
echo "== R8: SIGTERM durante F1 (podman start) e durante F3 (tc del e depois systemctl stop) =="
novo_caso r8 10
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
PATH="${PATH_TESTE}" bash "${RUNNER}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --jar "${JAR}" --jar-sha256 "${JAR_SHA}" \
    --fase piloto --saida "${OUT}" --cluster-criado-apos 2026-10-23T00:00:00Z > "${CASO}/out.txt" 2>&1 &
PID=$!
verdade "R8 F1 em curso (podman stop isn2)" esperar_por 'podman|stop|isn2' 120
"${REAL_SLEEP}" 0.5
kill -TERM "${PID}"; wait "${PID}"; RC=$?
verdade "R8 runner terminou com erro" test "${RC}" -ne 0
STOP="$(linha_de 'podman|stop|isn2')"; START="$(linha_de 'podman|start|isn2')"
verdade "R8 podman start isn2 depois do stop" test "${START}" -gt "${STOP}"
ok "R8 isn2 running" "running" "$(cat "${ST}/status-isn2")"
verdade "R8 brutos preservados" test -s "$(exec_dir 1000)/stdout.log"

novo_caso r8b 10
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F3"
printf 'go piloto %s\ngo f3 1000\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
PATH="${PATH_TESTE}" bash "${RUNNER}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" --jar "${JAR}" --jar-sha256 "${JAR_SHA}" \
    --fase piloto --saida "${OUT}" --cluster-criado-apos 2026-10-23T00:00:00Z > "${CASO}/out.txt" 2>&1 &
PID=$!
verdade "R8 F3 em curso (netem aplicado)" esperar_por 'qdisc|add|dev|eth0|root|netem|delay|20ms' 120
"${REAL_SLEEP}" 0.5
N0="$(wc -l < "${ST}/chamadas.log")"
kill -TERM "${PID}"; wait "${PID}"; RC=$?
verdade "R8 runner terminou com erro" test "${RC}" -ne 0
DEPOIS="$(tail -n +"$((N0 + 1))" "${ST}/chamadas.log")"
D1="$(printf '%s\n' "${DEPOIS}" | grep -nF 'qdisc|del' | head -1 | cut -d: -f1)"
S1="$(printf '%s\n' "${DEPOIS}" | grep -nF 'systemctl|stop|tcc-heal-rb-1000.timer' | head -1 | cut -d: -f1)"
verdade "R8 tc del depois do TERM e antes do systemctl stop" test "${D1:-0}" -gt 0 -a "${S1:-0}" -gt "${D1:-0}"
ok "R8 sem netem no fim" "0" "$(ls "${ST}"/netem-* 2>/dev/null | grep -c .)"
ok "R8 sem timer no fim" "0" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"
fi

# ============================================================================= R9
if quer R9; then
echo "== R9: checar 10 segue; checar 20 ou estado 20 -> exit 10 =="
novo_caso r9
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none" "1001 S1-async-none" "1002 S2-sync-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
echo 10 > "${ST}/checar-rc-1000"; echo 20 > "${ST}/checar-rc-1001"
rodar_runner piloto
ok "R9 checar 20 -> exit 10" "10" "${RC}"
ok "R9 checar 10 seguiu para a segunda" "2" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
contem "R9 motivo" "checar" "$(tail -1 "${OUT}/piloto/runner.log")"

novo_caso r9b
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none" "1001 S1-async-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
echo 20 > "${ST}/estado-rc-1"
rodar_runner piloto
ok "R9 estado 20 -> exit 10" "10" "${RC}"
ok "R9 parou depois da primeira" "1" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"

novo_caso r9c
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
echo 2 > "${ST}/checar-rc-1000"
rodar_runner piloto
ok "R9 checar com exit 2 -> exit 6" "6" "${RC}"
fi

# ============================================================================= R10
if quer R10; then
echo "== R10: guarda de netns =="
novo_caso r10
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F3"
printf 'go piloto %s\ngo f3 1000\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/netns-host-1002"
rodar_runner piloto
ok "R10 isn2 no netns do PID 1 -> exit 4" "4" "${RC}"
ok "R10 nenhum java" "0" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
contem "R10 motivo netns" "netns" "${SAIDA_TXT}"
fi

# ============================================================================= R11
if quer R11; then
echo "== R11: /dev/tty fora do modo de teste; PODMAN real recusado no modo de teste =="
novo_caso r11
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
if ( : < /dev/tty ) 2>/dev/null; then
    echo "SKIP: R11a (ha terminal de controle; o runner leria /dev/tty de verdade)"
else
    SAIDA_TXT="$(PATH="${PATH_TESTE}" env -u RB_MODO_TESTE bash "${RUNNER}" --matriz "${MAT}" --matriz-sha256 "${MAT_SHA}" \
        --jar "${JAR}" --jar-sha256 "${JAR_SHA}" --fase piloto --saida "${OUT}" --cluster-criado-apos 2026-10-23T00:00:00Z \
        < /dev/null 2>&1)"; RC=$?
    ok "R11 sem RB_MODO_TESTE o arquivo e ignorado (sem tty: exit 5)" "5" "${RC}"
    contem "R11 leu /dev/tty" "/dev/tty" "${SAIDA_TXT}"
    ok "R11 sem java" "0" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
fi
mkdir -p "${CASO}/real"; printf '#!/usr/bin/env bash\necho real\n' > "${CASO}/real/podman"; chmod +x "${CASO}/real/podman"
PODMAN="${CASO}/real/podman" rodar_runner piloto
ok "R11 PODMAN fora dos shims com RB_MODO_TESTE=1 -> exit 4" "4" "${RC}"
PODMAN="podman" RB_SHIM_DIR="${CASO}/real" rodar_runner piloto
ok "R11 diretorio de shims sem marca -> exit 4" "4" "${RC}"
fi

# ============================================================================= R12
if quer R12; then
echo "== R12: comando java argumento por argumento (PR secao 2) =="
novo_caso r12
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none" "1001 S2-async-none"
printf 'go piloto %s\n' "${HOJE}" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R12 exit 0" "0" "${RC}"
for par in "1000 S1 sync 150000" "1001 S2 async 150100"; do
    set -- ${par}
    D="$(exec_dir "$1")"
    ESP="java|-jar|${JAR}|--scenario|$2|--mix|v3|--replication|$3|--reset-state|on|--duration|300|--warmup-min-sec|30|--threads|4|--ops|0|--rep|1|--seed|$4|--servers|127.0.0.1:11222,127.0.0.1:11223,127.0.0.1:11224|--username|admin|--password|segredo|--csv-dir|${D}/.scratch"
    OBT="$(grep " java|-jar|.*|--seed|$4|" "${ST}/chamadas.log" | cut -d' ' -f2-)"
    ok "R12 argumentos de i=$1" "${ESP}" "${OBT}"
    ok "R12 execucao.json mascara a senha" "***" "$(json "${D}/execucao.json" 'd["comando"][d["comando"].index("--password")+1]')"
    VER="$(grep " java|-cp|${JAR}|br.unipampa.tcc.session.VerificadorSanidade|--csv|${D}/rep-001.csv|--scenario|$2|--saida|${D}/sanidade.json" "${ST}/chamadas.log" | grep -c .)"
    ok "R12 VerificadorSanidade de i=$1" "1" "${VER}"
done
fi

# ============================================================================= R13
if quer R13; then
echo "== R13 (C1/DT-013): ispn006010.json por vida do processo; contagem ilegivel -> exit 6 =="
novo_caso r13 20
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1"
printf 'go piloto %s
' "${HOJE}" > "${RB_TTY_ARQUIVO}"
rodar_runner piloto
ok "R13 exit 0" "0" "${RC}"
D="$(exec_dir 1000)"
ok "R13 total por no (isn2 com duas vidas)" "[1, 2, 1]" "$(json "${D}/ispn006010.json" '[d["por_no"][n] for n in ("isn1","isn2","isn3")]')"
ok "R13 vidas por no (ISPN080001)" "[1, 2, 1]" "$(json "${D}/ispn006010.json" '[d["vidas_por_no"][n] for n in ("isn1","isn2","isn3")]')"
ok "R13 vida atual (--since StartedAt)" "[1, 1, 1]" "$(json "${D}/ispn006010.json" '[d["por_no_desde_ultimo_start"][n] for n in ("isn1","isn2","isn3")]')"
contem "R13 podman logs --since StartedAt" "podman|logs|--since|" "$(chamadas)"

novo_caso r13b 20
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-none" "1001 S2-sync-none"
printf 'go piloto %s
' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/logs-since-falha"
rodar_runner piloto
ok "R13 contagem da vida atual ilegivel -> exit 6" "6" "${RC}"
ok "R13 checar nao chamado com contagem nula" "0" "$(grep -c 'rebaseline.py|checar' "${ST}/chamadas.log")"
ok "R13 segunda execucao nao lancada" "1" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
fi

# ============================================================================= R14
if quer R14; then
echo "== R14 (C3/P2): exit do injetor 1 e 4 = recusa K6 (segue); 2, 3, 5 e outros = exit 6 =="
novo_caso r14 20
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F1" "1001 S2-sync-none"
printf 'go piloto %s
' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/inspect-sem-f-falha-isn2"
rodar_runner piloto
ok "R14 injetor F1 com exit 4 -> fase segue (exit 0)" "0" "${RC}"
ok "R14 injetor_exit 4 registrado" "4" "$(json "$(exec_dir 1000)/execucao.json" 'd["injetor_exit"]')"
ok "R14 execucao seguinte lancada" "2" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"

novo_caso r14b 20
matriz "${CASO}/m.tsv" piloto "1000 S1-sync-F3" "1001 S2-sync-none"
printf 'go piloto %s
go f3 1000
' "${HOJE}" > "${RB_TTY_ARQUIVO}"
: > "${ST}/netem-rejeita"
rodar_runner piloto
ok "R14 injetor F3 com exit 5 (netem rejeitado) -> exit 6" "6" "${RC}"
ok "R14 injetor_exit 5 registrado" "5" "$(json "$(exec_dir 1000)/execucao.json" 'd["injetor_exit"]')"
contem "R14 motivo" "injetor" "$(tail -1 "${OUT}/piloto/runner.log")"
ok "R14 execucao seguinte nao lancada" "1" "$(grep -c ' java|-jar' "${ST}/chamadas.log")"
ok "R14 dead-man desarmado (sem netem)" "0" "$(ls "${ST}"/timer-* 2>/dev/null | grep -c .)"
fi

echo
echo "==== RESULTADO: ${PASS} PASS, ${FAIL} FAIL ===="
[[ "${FAIL}" -eq 0 ]]
